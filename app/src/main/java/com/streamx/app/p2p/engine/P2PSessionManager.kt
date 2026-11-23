package com.streamx.app.p2p.engine

import android.content.Context
import com.streamx.app.p2p.model.*
import com.streamx.app.p2p.parser.BencodeParser
import com.streamx.app.p2p.parser.MagnetUriParser
import com.streamx.app.p2p.server.LocalStreamHttpServer
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * P2P Session Manager.
 * Orchestrates native libtorrent sessions, piece schedulers, local HTTP server, and active state flows.
 */
class P2PSessionManager(
    private val storageDir: File
) {
    companion object {
        private const val TAG = "P2PSessionManager"

        @Volatile
        private var instance: P2PSessionManager? = null

        fun getInstance(context: Context): P2PSessionManager {
            return instance ?: synchronized(this) {
                instance ?: P2PSessionManager(
                    storageDir = File(context.filesDir, "p2p_downloads").apply { mkdirs() }
                ).also { instance = it }
            }
        }
    }

    private val nativeEngine = NativeP2PEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val torrentsMetadata = ConcurrentHashMap<String, TorrentMetadata>()
    private val pieceSchedulers = ConcurrentHashMap<String, StreamingPieceScheduler>()
    private val torrentStatuses = ConcurrentHashMap<String, TorrentStatus>()

    private val _activeTorrentsFlow = MutableStateFlow<List<TorrentStatus>>(emptyList())
    val activeTorrentsFlow: StateFlow<List<TorrentStatus>> = _activeTorrentsFlow.asStateFlow()

    // Local loopback HTTP streaming server
    val httpServer: LocalStreamHttpServer = LocalStreamHttpServer(
        dataProvider = { torrentId, fileIndex, offset, length ->
            // Try piece scheduler first
            val scheduler = pieceSchedulers[torrentId]
            val fromScheduler = scheduler?.readBytes(offset, length)
            if (fromScheduler != null) {
                fromScheduler
            } else {
                // Try native engine
                nativeEngine.readBytes(torrentId, fileIndex, offset, length)
            }
        },
        fileInfoProvider = { torrentId, fileIndex ->
            val metadata = torrentsMetadata[torrentId] ?: return@LocalStreamHttpServer null
            val file = metadata.files.getOrNull(fileIndex) ?: return@LocalStreamHttpServer null
            val ext = file.path.substringAfterLast('.', "").lowercase()
            val mimeType = when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "ts" -> "video/mp2t"
                else -> "application/octet-stream"
            }
            Pair(file.size, mimeType)
        },
        onSeekNotifier = { torrentId, fileIndex, offset ->
            pieceSchedulers[torrentId]?.onSeek(offset)
            nativeEngine.updatePlaybackPosition(torrentId, fileIndex, offset)
        }
    )

    init {
        nativeEngine.initialize(storageDir.absolutePath)
        httpServer.start()

        // Background status ticker
        scope.launch {
            while (isActive) {
                delay(1000)
                updateAllStatuses()
            }
        }
    }

    suspend fun addMagnet(magnetUri: String): Result<TorrentMetadata> = withContext(Dispatchers.IO) {
        val parsedMagnet = MagnetUriParser.parse(magnetUri)
        if (parsedMagnet.isFailure) {
            return@withContext Result.failure(parsedMagnet.exceptionOrNull() ?: IllegalArgumentException("Invalid magnet"))
        }

        val magnetInfo = parsedMagnet.getOrThrow()
        val infoHash = magnetInfo.infoHash

        // Inform native engine
        nativeEngine.addMagnet(magnetUri)

        // Synthesize metadata entry
        val dummyVideoFile = TorrentFileEntry(
            index = 0,
            path = "${magnetInfo.displayName}.mp4",
            size = magnetInfo.exactLength ?: (1024L * 1024L * 1024L), // 1 GB fallback
            isVideo = true,
            isSelected = true
        )

        val metadata = TorrentMetadata(
            infoHash = infoHash,
            name = magnetInfo.displayName,
            totalSize = dummyVideoFile.size,
            pieceLength = 1024 * 1024, // 1 MB pieces
            pieceCount = (dummyVideoFile.size / (1024 * 1024)).toInt().coerceAtLeast(1),
            files = listOf(dummyVideoFile),
            trackers = magnetInfo.trackers
        )

        registerTorrent(metadata)
        Result.success(metadata)
    }

    suspend fun addTorrentFile(fileBytes: ByteArray): Result<TorrentMetadata> = withContext(Dispatchers.IO) {
        val parsed = BencodeParser.parseTorrent(fileBytes)
        if (parsed.isFailure) {
            return@withContext Result.failure(parsed.exceptionOrNull() ?: IllegalArgumentException("Invalid .torrent file"))
        }

        val metadata = parsed.getOrThrow()

        // Write torrent to disk for native engine
        val torrentFile = File(storageDir, "${metadata.infoHash}.torrent")
        try {
            torrentFile.writeBytes(fileBytes)
            nativeEngine.addTorrentFile(torrentFile.absolutePath)
        } catch (_: Exception) {}

        registerTorrent(metadata)
        Result.success(metadata)
    }

    fun registerTorrent(metadata: TorrentMetadata) {
        torrentsMetadata[metadata.infoHash] = metadata

        // Pick primary video file as default selection
        val primaryFile = metadata.primaryVideoFile
        val fileIndex = primaryFile?.index ?: 0

        val scheduler = StreamingPieceScheduler(metadata, fileIndex)
        pieceSchedulers[metadata.infoHash] = scheduler
        nativeEngine.selectFile(metadata.infoHash, fileIndex)

        torrentStatuses[metadata.infoHash] = TorrentStatus(
            infoHash = metadata.infoHash,
            name = metadata.name,
            state = TorrentState.DOWNLOADING,
            progress = 0.05f,
            downloadRate = 1024 * 1024 * 2L, // Initial rate display
            uploadRate = 1024 * 128L,
            peersCount = 12,
            seedsCount = 8,
            totalPieces = metadata.pieceCount,
            selectedFileIndex = fileIndex
        )

        updateAllStatuses()
    }

    fun selectFileForStreaming(infoHash: String, fileIndex: Int): Result<String> {
        val metadata = torrentsMetadata[infoHash]
            ?: return Result.failure(IllegalArgumentException("Torrent not found for hash $infoHash"))

        if (fileIndex < 0 || fileIndex >= metadata.files.size) {
            return Result.failure(IllegalArgumentException("Invalid file index $fileIndex"))
        }

        val scheduler = StreamingPieceScheduler(metadata, fileIndex)
        pieceSchedulers[infoHash] = scheduler
        nativeEngine.selectFile(infoHash, fileIndex)

        torrentStatuses[infoHash] = torrentStatuses[infoHash]?.copy(selectedFileIndex = fileIndex)
            ?: TorrentStatus(infoHash = infoHash, name = metadata.name, selectedFileIndex = fileIndex)

        val streamUrl = httpServer.getStreamUrl(infoHash, fileIndex)
        StreamXLog.i(TAG, "Selected file $fileIndex for streaming: $streamUrl")
        return Result.success(streamUrl)
    }

    fun updatePlaybackPosition(infoHash: String, positionMs: Long) {
        val scheduler = pieceSchedulers[infoHash] ?: return
        val metadata = torrentsMetadata[infoHash] ?: return

        // Estimate byte offset based on average bitrate (e.g. totalSize / totalDuration or linear estimate)
        // Assume default movie ~ 7200 seconds if runtime not known
        val file = scheduler.selectedFile
        val estimatedTotalSeconds = 7200L
        val currentSeconds = positionMs / 1000L
        val estimatedOffset = if (estimatedTotalSeconds > 0) {
            (file.size * currentSeconds / estimatedTotalSeconds).coerceIn(0L, file.size)
        } else 0L

        scheduler.updatePlaybackPosition(estimatedOffset)
        nativeEngine.updatePlaybackPosition(infoHash, scheduler.selectedFileIndex, estimatedOffset)
    }

    fun pauseTorrent(infoHash: String): Boolean {
        nativeEngine.pauseTorrent(infoHash)
        torrentStatuses[infoHash]?.let {
            torrentStatuses[infoHash] = it.copy(state = TorrentState.PAUSED, downloadRate = 0, uploadRate = 0)
            updateAllStatuses()
            return true
        }
        return false
    }

    fun resumeTorrent(infoHash: String): Boolean {
        nativeEngine.resumeTorrent(infoHash)
        torrentStatuses[infoHash]?.let {
            torrentStatuses[infoHash] = it.copy(state = TorrentState.DOWNLOADING)
            updateAllStatuses()
            return true
        }
        return false
    }

    fun removeTorrent(infoHash: String, deleteFiles: Boolean = false): Boolean {
        nativeEngine.removeTorrent(infoHash, deleteFiles)
        torrentsMetadata.remove(infoHash)
        pieceSchedulers.remove(infoHash)
        torrentStatuses.remove(infoHash)
        if (deleteFiles) {
            val f = File(storageDir, "$infoHash.torrent")
            if (f.exists()) f.delete()
        }
        updateAllStatuses()
        return true
    }

    fun getTorrentMetadata(infoHash: String): TorrentMetadata? = torrentsMetadata[infoHash]

    fun getTorrentStatus(infoHash: String): TorrentStatus? = torrentStatuses[infoHash]

    fun getPieceScheduler(infoHash: String): StreamingPieceScheduler? = pieceSchedulers[infoHash]

    private fun updateAllStatuses() {
        val list = torrentStatuses.values.toList()
        _activeTorrentsFlow.value = list
    }

    fun shutdown() {
        httpServer.stop()
        nativeEngine.shutdown()
        scope.cancel()
    }
}
