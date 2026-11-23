#include "p2p_streaming_engine.h"
#include <android/log.h>
#include <algorithm>
#include <fstream>
#include <cmath>

#define LOG_TAG "StreamX-NativeP2P"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace streamx::p2p {

// -----------------------------------------------------------------------------
// TorrentSession Implementation
// -----------------------------------------------------------------------------

TorrentSession::TorrentSession(const std::string& infoHash, const std::string& name, const std::string& storageDir)
    : infoHash_(infoHash), name_(name), storageDir_(storageDir) {
    stats_.downloadRate = 0;
    stats_.uploadRate = 0;
    stats_.progress = 0.0f;
    stats_.peersCount = 0;
    stats_.seedsCount = 0;
    stats_.piecesCompleted = 0;
    stats_.totalPieces = 0;
    stats_.bufferedBytes = 0;
    stats_.state = TorrentState::DOWNLOADING_METADATA;
}

TorrentSession::~TorrentSession() {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    piecePriorities_.clear();
    piecesCompleted_.clear();
}

TorrentInfo TorrentSession::getInfo() const {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    return info_;
}

StreamingStats TorrentSession::getStats() const {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    return stats_;
}

void TorrentSession::setTorrentInfo(const TorrentInfo& info) {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    info_ = info;
    info_.storagePath = storageDir_;
    stats_.totalPieces = info.numPieces;
    stats_.state = TorrentState::DOWNLOADING;

    piecePriorities_.assign(info.numPieces, static_cast<uint8_t>(PiecePriority::NORMAL));
    piecesCompleted_.assign(info.numPieces, false);
}

bool TorrentSession::selectFile(int fileIndex) {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    if (fileIndex < 0 || fileIndex >= static_cast<int>(info_.files.size())) {
        LOGE("selectFile: invalid index %d", fileIndex);
        return false;
    }

    selectedFileIndex_ = fileIndex;
    for (size_t i = 0; i < info_.files.size(); ++i) {
        info_.files[i].selected = (static_cast<int>(i) == fileIndex);
    }

    // Default buffer ahead: 15 MB
    const int64_t defaultBufferAhead = 15 * 1024 * 1024;
    calculatePiecePriorities(fileIndex, 0, defaultBufferAhead);
    return true;
}

void TorrentSession::updatePlaybackPosition(int fileIndex, int64_t byteOffset, int64_t bufferAheadBytes) {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    selectedFileIndex_ = fileIndex;
    currentPlaybackOffset_ = byteOffset;
    calculatePiecePriorities(fileIndex, byteOffset, bufferAheadBytes);
}

void TorrentSession::calculatePiecePriorities(int selectedFileIndex, int64_t playbackOffset, int64_t bufferAhead) {
    if (info_.numPieces <= 0 || info_.pieceLength <= 0 || selectedFileIndex < 0 ||
        selectedFileIndex >= static_cast<int>(info_.files.size())) {
        return;
    }

    // Calculate start byte offset of selected file within torrent
    int64_t fileStartOffset = 0;
    for (int i = 0; i < selectedFileIndex; ++i) {
        fileStartOffset += info_.files[i].size;
    }
    int64_t fileEndOffset = fileStartOffset + info_.files[selectedFileIndex].size;

    int fileStartPiece = static_cast<int>(fileStartOffset / info_.pieceLength);
    int fileEndPiece = static_cast<int>((fileEndOffset - 1) / info_.pieceLength);
    fileEndPiece = std::min(fileEndPiece, info_.numPieces - 1);

    // 1. Reset all pieces outside this file to DONT_DOWNLOAD (0)
    std::fill(piecePriorities_.begin(), piecePriorities_.end(), static_cast<uint8_t>(PiecePriority::DONT_DOWNLOAD));

    // 2. Set all pieces inside the selected file to NORMAL priority (4)
    for (int p = fileStartPiece; p <= fileEndPiece; ++p) {
        piecePriorities_[p] = static_cast<uint8_t>(PiecePriority::NORMAL);
    }

    // 3. Prioritize Container Header (Moov atom / Matroska EBML)
    // First 2 pieces of the file are critical to begin demuxing in Media3 ExoPlayer
    int headerEndPiece = std::min(fileStartPiece + 2, fileEndPiece);
    for (int p = fileStartPiece; p <= headerEndPiece; ++p) {
        piecePriorities_[p] = static_cast<uint8_t>(PiecePriority::CRITICAL);
    }

    // 4. Prioritize Container Cues / Index / Tail
    // Last 2 pieces of the file often contain MP4 moov atom if placed at the end, or MKV cues
    int tailStartPiece = std::max(fileStartPiece, fileEndPiece - 2);
    for (int p = tailStartPiece; p <= fileEndPiece; ++p) {
        piecePriorities_[p] = static_cast<uint8_t>(PiecePriority::CRITICAL);
    }

    // 5. Sliding Playback Window (Current read head + immediate buffer ahead)
    int64_t targetOffset = fileStartOffset + playbackOffset;
    int currentPiece = static_cast<int>(targetOffset / info_.pieceLength);
    currentPiece = std::clamp(currentPiece, fileStartPiece, fileEndPiece);

    int bufferEndPiece = static_cast<int>((targetOffset + bufferAhead) / info_.pieceLength);
    bufferEndPiece = std::clamp(bufferEndPiece, currentPiece, fileEndPiece);

    // Immediate playback window (current and next 3 pieces) gets CRITICAL priority
    int immediateWindowEnd = std::min(currentPiece + 3, bufferEndPiece);
    for (int p = currentPiece; p <= immediateWindowEnd; ++p) {
        piecePriorities_[p] = static_cast<uint8_t>(PiecePriority::CRITICAL);
    }

    // Buffer ahead window gets HIGH priority
    for (int p = immediateWindowEnd + 1; p <= bufferEndPiece; ++p) {
        piecePriorities_[p] = static_cast<uint8_t>(PiecePriority::HIGH);
    }

    LOGI("calculatePiecePriorities: file=%d, curPiece=%d, bufEnd=%d, totalPieces=%d",
         selectedFileIndex, currentPiece, bufferEndPiece, info_.numPieces);
}

bool TorrentSession::readBytes(int fileIndex, int64_t byteOffset, int length, std::vector<uint8_t>& outBuffer) {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    if (fileIndex < 0 || fileIndex >= static_cast<int>(info_.files.size())) {
        return false;
    }

    const auto& file = info_.files[fileIndex];
    if (byteOffset >= file.size || length <= 0) {
        outBuffer.clear();
        return false;
    }

    int bytesToRead = static_cast<int>(std::min(static_cast<int64_t>(length), file.size - byteOffset));
    outBuffer.resize(bytesToRead);

    // Path traversal check
    if (file.path.find("..") != std::string::npos || file.path.front() == '/') {
        LOGE("readBytes: path traversal attempt rejected: %s", file.path.c_str());
        return false;
    }

    std::string fullPath = storageDir_ + "/" + file.path;
    std::ifstream stream(fullPath, std::ios::binary);
    if (!stream.is_open()) {
        // File may be downloading or synthetic test buffer
        LOGW("readBytes: file not yet on disk: %s", fullPath.c_str());
        return false;
    }

    stream.seekg(byteOffset);
    stream.read(reinterpret_cast<char*>(outBuffer.data()), bytesToRead);
    std::streamsize readCount = stream.gcount();
    if (readCount < bytesToRead) {
        outBuffer.resize(readCount);
    }
    return true;
}

void TorrentSession::pause() {
    paused_.store(true);
    stats_.state = TorrentState::PAUSED;
}

void TorrentSession::resume() {
    paused_.store(false);
    stats_.state = TorrentState::DOWNLOADING;
}

bool TorrentSession::isPaused() const {
    return paused_.load();
}

// -----------------------------------------------------------------------------
// StreamingEngine Singleton Implementation
// -----------------------------------------------------------------------------

StreamingEngine::StreamingEngine() = default;
StreamingEngine::~StreamingEngine() {
    shutdown();
}

StreamingEngine& StreamingEngine::getInstance() {
    static StreamingEngine instance;
    return instance;
}

bool StreamingEngine::initialize(const std::string& downloadDir) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    if (initialized_.load()) {
        return true;
    }

    downloadDir_ = downloadDir;
    initialized_.store(true);
    LOGI("StreamingEngine initialized with storage directory: %s", downloadDir_.c_str());
    return true;
}

void StreamingEngine::shutdown() {
    std::lock_guard<std::mutex> lock(engineMutex_);
    if (!initialized_.load()) {
        return;
    }

    sessions_.clear();
    initialized_.store(false);
    LOGI("StreamingEngine shutdown successfully");
}

bool StreamingEngine::isInitialized() const {
    return initialized_.load();
}

std::string StreamingEngine::addMagnet(const std::string& magnetUri) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    if (!initialized_.load()) {
        LOGE("addMagnet called before engine initialized");
        return "";
    }

    // Extract xt=urn:btih:
    const std::string prefix = "xt=urn:btih:";
    auto pos = magnetUri.find(prefix);
    if (pos == std::string::npos) {
        LOGE("addMagnet: invalid magnet URI, no xt=urn:btih: found");
        return "";
    }

    size_t hashStart = pos + prefix.length();
    size_t hashEnd = magnetUri.find('&', hashStart);
    std::string infoHash = (hashEnd == std::string::npos)
        ? magnetUri.substr(hashStart)
        : magnetUri.substr(hashStart, hashEnd - hashStart);

    // Extract display name dn= if present
    std::string name = "Magnet Stream";
    const std::string dnPrefix = "dn=";
    auto dnPos = magnetUri.find(dnPrefix);
    if (dnPos != std::string::npos) {
        size_t nameStart = dnPos + dnPrefix.length();
        size_t nameEnd = magnetUri.find('&', nameStart);
        name = (nameEnd == std::string::npos)
            ? magnetUri.substr(nameStart)
            : magnetUri.substr(nameStart, nameEnd - nameStart);
    }

    auto session = std::make_shared<TorrentSession>(infoHash, name, downloadDir_);
    sessions_[infoHash] = session;
    LOGI("addMagnet: added session %s (%s)", infoHash.c_str(), name.c_str());
    return infoHash;
}

std::string StreamingEngine::addTorrentFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    if (!initialized_.load()) {
        return "";
    }

    // Derive session from file path hash
    std::string fakeHash = "torrent_" + std::to_string(std::hash<std::string>{}(filePath));
    std::string name = filePath.substr(filePath.find_last_of("/\\") + 1);

    auto session = std::make_shared<TorrentSession>(fakeHash, name, downloadDir_);
    sessions_[fakeHash] = session;
    LOGI("addTorrentFile: added session %s for %s", fakeHash.c_str(), filePath.c_str());
    return fakeHash;
}

bool StreamingEngine::removeTorrent(const std::string& infoHash, bool deleteFiles) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it == sessions_.end()) {
        return false;
    }
    sessions_.erase(it);
    LOGI("removeTorrent: removed %s (deleteFiles=%d)", infoHash.c_str(), deleteFiles);
    return true;
}

bool StreamingEngine::pauseTorrent(const std::string& infoHash) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        it->second->pause();
        return true;
    }
    return false;
}

bool StreamingEngine::resumeTorrent(const std::string& infoHash) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        it->second->resume();
        return true;
    }
    return false;
}

bool StreamingEngine::getTorrentInfo(const std::string& infoHash, TorrentInfo& outInfo) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        outInfo = it->second->getInfo();
        return true;
    }
    return false;
}

bool StreamingEngine::getStreamingStats(const std::string& infoHash, StreamingStats& outStats) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        outStats = it->second->getStats();
        return true;
    }
    return false;
}

bool StreamingEngine::selectFileForStreaming(const std::string& infoHash, int fileIndex) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        return it->second->selectFile(fileIndex);
    }
    return false;
}

void StreamingEngine::updatePlaybackPosition(const std::string& infoHash, int fileIndex, int64_t byteOffset, int64_t bufferAheadBytes) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        it->second->updatePlaybackPosition(fileIndex, byteOffset, bufferAheadBytes);
    }
}

bool StreamingEngine::readStreamBytes(const std::string& infoHash, int fileIndex, int64_t byteOffset, int length, std::vector<uint8_t>& outBuffer) {
    std::lock_guard<std::mutex> lock(engineMutex_);
    auto it = sessions_.find(infoHash);
    if (it != sessions_.end()) {
        return it->second->readBytes(fileIndex, byteOffset, length, outBuffer);
    }
    return false;
}

} // namespace streamx::p2p
