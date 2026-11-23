#ifndef STREAMX_P2P_STREAMING_ENGINE_H
#define STREAMX_P2P_STREAMING_ENGINE_H

#include "p2p_types.h"
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <atomic>

namespace streamx::p2p {

class TorrentSession {
public:
    TorrentSession(const std::string& infoHash, const std::string& name, const std::string& storageDir);
    ~TorrentSession();

    std::string getInfoHash() const { return infoHash_; }
    TorrentInfo getInfo() const;
    StreamingStats getStats() const;

    void setTorrentInfo(const TorrentInfo& info);
    bool selectFile(int fileIndex);
    void updatePlaybackPosition(int fileIndex, int64_t byteOffset, int64_t bufferAheadBytes);
    bool readBytes(int fileIndex, int64_t byteOffset, int length, std::vector<uint8_t>& outBuffer);

    void pause();
    void resume();
    bool isPaused() const;

    // Piece scheduling
    void calculatePiecePriorities(int selectedFileIndex, int64_t playbackOffset, int64_t bufferAhead);

private:
    std::string infoHash_;
    std::string name_;
    std::string storageDir_;
    mutable std::mutex sessionMutex_;

    TorrentInfo info_;
    StreamingStats stats_;
    int selectedFileIndex_ = -1;
    int64_t currentPlaybackOffset_ = 0;
    std::atomic<bool> paused_{false};

    std::vector<uint8_t> piecePriorities_;
    std::vector<bool> piecesCompleted_;
};

class StreamingEngine {
public:
    static StreamingEngine& getInstance();

    bool initialize(const std::string& downloadDir);
    void shutdown();
    bool isInitialized() const;

    std::string addMagnet(const std::string& magnetUri);
    std::string addTorrentFile(const std::string& filePath);
    bool removeTorrent(const std::string& infoHash, bool deleteFiles);

    bool pauseTorrent(const std::string& infoHash);
    bool resumeTorrent(const std::string& infoHash);

    bool getTorrentInfo(const std::string& infoHash, TorrentInfo& outInfo);
    bool getStreamingStats(const std::string& infoHash, StreamingStats& outStats);

    bool selectFileForStreaming(const std::string& infoHash, int fileIndex);
    void updatePlaybackPosition(const std::string& infoHash, int fileIndex, int64_t byteOffset, int64_t bufferAheadBytes);
    bool readStreamBytes(const std::string& infoHash, int fileIndex, int64_t byteOffset, int length, std::vector<uint8_t>& outBuffer);

private:
    StreamingEngine();
    ~StreamingEngine();
    StreamingEngine(const StreamingEngine&) = delete;
    StreamingEngine& operator=(const StreamingEngine&) = delete;

    std::string downloadDir_;
    std::atomic<bool> initialized_{false};
    mutable std::mutex engineMutex_;
    std::unordered_map<std::string, std::shared_ptr<TorrentSession>> sessions_;
};

} // namespace streamx::p2p

#endif // STREAMX_P2P_STREAMING_ENGINE_H
