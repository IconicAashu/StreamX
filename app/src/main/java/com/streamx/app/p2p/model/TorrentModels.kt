package com.streamx.app.p2p.model

/**
 * P2P BitTorrent domain models, piece scheduling priorities, and streaming buffer status.
 */

enum class PiecePriority(val value: Int) {
    DONT_DOWNLOAD(0),
    LOW(1),
    NORMAL(4),
    HIGH(6),
    CRITICAL(7)
}

enum class TorrentState {
    CHECKING,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    PAUSED,
    SEEDING,
    ERROR
}

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val size: Long,
    val isVideo: Boolean,
    val isSelected: Boolean = false,
    val priority: PiecePriority = PiecePriority.NORMAL
) {
    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val value = size / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
        }
}

data class TorrentMetadata(
    val infoHash: String,
    val name: String,
    val totalSize: Long,
    val pieceLength: Int,
    val pieceCount: Int,
    val files: List<TorrentFileEntry>,
    val trackers: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val creationDate: Long? = null
) {
    val formattedTotalSize: String
        get() {
            if (totalSize <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(totalSize.toDouble()) / Math.log10(1024.0)).toInt()
            val value = totalSize / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.2f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
        }

    val primaryVideoFile: TorrentFileEntry?
        get() = files.filter { it.isVideo }.maxByOrNull { it.size } ?: files.firstOrNull()
}

data class TorrentStatus(
    val infoHash: String,
    val name: String,
    val state: TorrentState = TorrentState.DOWNLOADING,
    val progress: Float = 0.0f,
    val downloadRate: Long = 0L,   // bytes/sec
    val uploadRate: Long = 0L,     // bytes/sec
    val peersCount: Int = 0,
    val seedsCount: Int = 0,
    val piecesDownloaded: Int = 0,
    val totalPieces: Int = 0,
    val bufferedBytes: Long = 0L,
    val selectedFileIndex: Int = -1,
    val errorMessage: String? = null
) {
    val formattedDownloadRate: String
        get() = formatSpeed(downloadRate)

    val formattedUploadRate: String
        get() = formatSpeed(uploadRate)

    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024.0) {
            String.format("%.2f MB/s", kb / 1024.0)
        } else {
            String.format("%.1f KB/s", kb)
        }
    }
}

data class StreamingBufferState(
    val playbackByteOffset: Long,
    val totalFileBytes: Long,
    val bufferAheadBytes: Long,
    val bufferedPercentage: Float,
    val isReadyToPlay: Boolean
)
