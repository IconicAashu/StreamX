#ifndef STREAMX_P2P_TYPES_H
#define STREAMX_P2P_TYPES_H

#include <string>
#include <vector>
#include <cstdint>

namespace streamx::p2p {

enum class PiecePriority : int {
    DONT_DOWNLOAD = 0,
    LOW = 1,
    NORMAL = 4,
    HIGH = 6,
    CRITICAL = 7
};

enum class TorrentState : int {
    CHECKING = 0,
    DOWNLOADING_METADATA = 1,
    DOWNLOADING = 2,
    PAUSED = 3,
    SEEDING = 4,
    ERROR = 5
};

struct FileEntry {
    int index;
    std::string path;
    int64_t size;
    bool isVideo;
    bool selected;
};

struct TorrentInfo {
    std::string infoHash;
    std::string name;
    int64_t totalSize;
    int pieceLength;
    int numPieces;
    std::vector<FileEntry> files;
    std::string storagePath;
};

struct StreamingStats {
    int64_t downloadRate;      // bytes/sec
    int64_t uploadRate;        // bytes/sec
    float progress;            // 0.0f - 1.0f
    int peersCount;
    int seedsCount;
    int piecesCompleted;
    int totalPieces;
    int64_t bufferedBytes;
    TorrentState state;
};

struct PieceWindow {
    int startPiece;
    int endPiece;
    PiecePriority priority;
};

} // namespace streamx::p2p

#endif // STREAMX_P2P_TYPES_H
