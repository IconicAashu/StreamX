package com.streamx.app.p2p.engine

import com.streamx.app.p2p.model.PiecePriority
import com.streamx.app.p2p.model.StreamingBufferState
import com.streamx.app.p2p.model.TorrentFileEntry
import com.streamx.app.p2p.model.TorrentMetadata
import com.streamx.app.utils.StreamXLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Streaming-Aware Piece Scheduler for BitTorrent video streaming.
 * Implements non-sequential prioritization:
 * 1. Container Header pieces (moov atom / Matroska EBML) -> CRITICAL
 * 2. Container Index / Cues pieces (tail of file) -> CRITICAL
 * 3. Sliding Playback Window (read head + buffer ahead) -> CRITICAL / HIGH
 * 4. Background sequential pieces -> NORMAL
 * 5. Unselected files -> DONT_DOWNLOAD
 */
class StreamingPieceScheduler(
    val metadata: TorrentMetadata,
    val selectedFileIndex: Int
) {
    companion object {
        private const val TAG = "PieceScheduler"
        const val DEFAULT_BUFFER_AHEAD_BYTES = 15 * 1024 * 1024L // 15 MB buffer ahead
    }

    private val lock = ReentrantLock()
    val selectedFile: TorrentFileEntry = metadata.files.getOrNull(selectedFileIndex)
        ?: throw IllegalArgumentException("Invalid file index $selectedFileIndex for torrent with ${metadata.files.size} files")

    val fileStartOffset: Long
    val fileEndOffset: Long
    val fileStartPiece: Int
    val fileEndPiece: Int

    private val piecePriorities = ConcurrentHashMap<Int, PiecePriority>()
    private val completedPieces = ConcurrentHashMap<Int, ByteArray>()
    private var currentPlaybackOffset: Long = 0L

    init {
        var offset = 0L
        for (i in 0 until selectedFileIndex) {
            offset += metadata.files[i].size
        }
        fileStartOffset = offset
        fileEndOffset = offset + selectedFile.size

        fileStartPiece = (fileStartOffset / metadata.pieceLength).toInt()
        fileEndPiece = ((fileEndOffset - 1) / metadata.pieceLength).toInt().coerceAtMost(metadata.pieceCount - 1)

        // Initialize piece priorities
        recalculatePriorities(playbackOffset = 0L, bufferAheadBytes = DEFAULT_BUFFER_AHEAD_BYTES)
    }

    fun onSeek(newByteOffset: Long, bufferAheadBytes: Long = DEFAULT_BUFFER_AHEAD_BYTES) {
        lock.withLock {
            val clamped = newByteOffset.coerceIn(0L, selectedFile.size)
            currentPlaybackOffset = clamped
            recalculatePriorities(clamped, bufferAheadBytes)
            StreamXLog.i(TAG, "Seeked to byte $clamped. Reprioritized pieces for file '${selectedFile.path}'.")
        }
    }

    fun updatePlaybackPosition(playbackOffset: Long, bufferAheadBytes: Long = DEFAULT_BUFFER_AHEAD_BYTES) {
        lock.withLock {
            val clamped = playbackOffset.coerceIn(0L, selectedFile.size)
            currentPlaybackOffset = clamped
            recalculatePriorities(clamped, bufferAheadBytes)
        }
    }

    private fun recalculatePriorities(playbackOffset: Long, bufferAheadBytes: Long) {
        // 1. Reset all pieces to DONT_DOWNLOAD
        for (p in 0 until metadata.pieceCount) {
            piecePriorities[p] = PiecePriority.DONT_DOWNLOAD
        }

        // 2. Mark selected file pieces as NORMAL
        for (p in fileStartPiece..fileEndPiece) {
            piecePriorities[p] = PiecePriority.NORMAL
        }

        // 3. Container Header pieces (moov/EBML) -> CRITICAL
        val headerEnd = min(fileStartPiece + 2, fileEndPiece)
        for (p in fileStartPiece..headerEnd) {
            piecePriorities[p] = PiecePriority.CRITICAL
        }

        // 4. Container Tail pieces (Index/Cues/Moov at end) -> CRITICAL
        val tailStart = max(fileStartPiece, fileEndPiece - 2)
        for (p in tailStart..fileEndPiece) {
            piecePriorities[p] = PiecePriority.CRITICAL
        }

        // 5. Sliding Playback Window around current read head
        val targetAbsoluteOffset = fileStartOffset + playbackOffset
        val currentPiece = (targetAbsoluteOffset / metadata.pieceLength).toInt().coerceIn(fileStartPiece, fileEndPiece)
        val bufferEndPiece = ((targetAbsoluteOffset + bufferAheadBytes) / metadata.pieceLength).toInt().coerceIn(currentPiece, fileEndPiece)

        // Immediate pieces (current + next 3) -> CRITICAL
        val immediateEnd = min(currentPiece + 3, bufferEndPiece)
        for (p in currentPiece..immediateEnd) {
            piecePriorities[p] = PiecePriority.CRITICAL
        }

        // Buffer ahead pieces -> HIGH
        for (p in (immediateEnd + 1)..bufferEndPiece) {
            piecePriorities[p] = PiecePriority.HIGH
        }
    }

    fun getPiecePriority(pieceIndex: Int): PiecePriority {
        return piecePriorities[pieceIndex] ?: PiecePriority.DONT_DOWNLOAD
    }

    fun getAllPriorities(): Map<Int, PiecePriority> {
        return HashMap(piecePriorities)
    }

    fun markPieceCompleted(pieceIndex: Int, data: ByteArray) {
        completedPieces[pieceIndex] = data
    }

    fun isPieceCompleted(pieceIndex: Int): Boolean {
        return completedPieces.containsKey(pieceIndex)
    }

    fun getBufferState(bufferAheadBytes: Long = DEFAULT_BUFFER_AHEAD_BYTES): StreamingBufferState {
        lock.withLock {
            val targetAbsoluteOffset = fileStartOffset + currentPlaybackOffset
            val currentPiece = (targetAbsoluteOffset / metadata.pieceLength).toInt().coerceIn(fileStartPiece, fileEndPiece)
            val bufferEndPiece = ((targetAbsoluteOffset + bufferAheadBytes) / metadata.pieceLength).toInt().coerceIn(currentPiece, fileEndPiece)

            val totalWindowPieces = (bufferEndPiece - currentPiece + 1).coerceAtLeast(1)
            var readyPieces = 0
            for (p in currentPiece..bufferEndPiece) {
                if (completedPieces.containsKey(p)) {
                    readyPieces++
                }
            }

            val bufferPercent = (readyPieces.toFloat() / totalWindowPieces.toFloat()).coerceIn(0.0f, 1.0f)
            // Ready to play if header pieces and current piece are completed
            val hasHeader = completedPieces.containsKey(fileStartPiece)
            val hasCurrent = completedPieces.containsKey(currentPiece)
            val isReady = hasHeader && hasCurrent

            val bufferedBytes = completedPieces.values.sumOf { it.size.toLong() }

            return StreamingBufferState(
                playbackByteOffset = currentPlaybackOffset,
                totalFileBytes = selectedFile.size,
                bufferAheadBytes = bufferAheadBytes,
                bufferedPercentage = bufferPercent,
                isReadyToPlay = isReady
            )
        }
    }

    fun readBytes(byteOffset: Long, length: Int): ByteArray? {
        lock.withLock {
            if (byteOffset < 0 || byteOffset >= selectedFile.size || length <= 0) {
                return null
            }

            val bytesToRead = min(length.toLong(), selectedFile.size - byteOffset).toInt()
            val result = ByteArray(bytesToRead)

            var written = 0
            var currentOffset = fileStartOffset + byteOffset

            while (written < bytesToRead) {
                val pieceIndex = (currentOffset / metadata.pieceLength).toInt()
                val offsetInPiece = (currentOffset % metadata.pieceLength).toInt()
                val pieceData = completedPieces[pieceIndex]

                if (pieceData == null) {
                    // Piece not yet available in buffer
                    return null
                }

                val availableInPiece = pieceData.size - offsetInPiece
                val toCopy = min(availableInPiece, bytesToRead - written)
                System.arraycopy(pieceData, offsetInPiece, result, written, toCopy)

                written += toCopy
                currentOffset += toCopy
            }

            return result
        }
    }
}
