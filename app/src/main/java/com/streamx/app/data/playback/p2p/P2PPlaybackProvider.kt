package com.streamx.app.data.playback.p2p

import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.playback.PlaybackEventType
import com.streamx.app.domain.playback.PlaybackProvider
import com.streamx.app.p2p.engine.P2PSessionManager
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P2P Playback Provider implementing STREAMX universal PlaybackProvider.
 * Streams decentralized torrents and magnet links locally via loopback HTTP server (127.0.0.1)
 * directly into Android Media3 ExoPlayer.
 */
class P2PPlaybackProvider(
    private val sessionManager: P2PSessionManager
) : PlaybackProvider {

    companion object {
        private const val TAG = "P2PPlaybackProvider"
    }

    override val providerId: String = "p2p"
    override val displayName: String = "P2P BitTorrent Engine"

    override suspend fun canHandle(mediaId: String): Boolean {
        val trimmed = mediaId.trim()
        return trimmed.startsWith("p2p_", ignoreCase = true) ||
                trimmed.startsWith("magnet:?", ignoreCase = true) ||
                trimmed.endsWith(".torrent", ignoreCase = true)
    }

    override suspend fun getStreams(mediaId: String): List<PlaybackSource> = withContext(Dispatchers.IO) {
        val trimmed = mediaId.trim()
        val sources = mutableListOf<PlaybackSource>()

        try {
            val (infoHash, fileIndex) = when {
                trimmed.startsWith("magnet:?", ignoreCase = true) -> {
                    val addResult = sessionManager.addMagnet(trimmed)
                    val metadata = addResult.getOrThrow()
                    Pair(metadata.infoHash, metadata.primaryVideoFile?.index ?: 0)
                }
                trimmed.startsWith("p2p_", ignoreCase = true) -> {
                    val raw = trimmed.removePrefix("p2p_")
                    if (raw.contains("_file_")) {
                        val parts = raw.split("_file_")
                        Pair(parts[0], parts[1].toIntOrNull() ?: 0)
                    } else {
                        val meta = sessionManager.getTorrentMetadata(raw)
                        Pair(raw, meta?.primaryVideoFile?.index ?: 0)
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unsupported P2P media identifier: $mediaId")
                }
            }

            // Select file and retrieve local loopback stream URL
            val streamUrlResult = sessionManager.selectFileForStreaming(infoHash, fileIndex)
            val streamUrl = streamUrlResult.getOrThrow()

            val metadata = sessionManager.getTorrentMetadata(infoHash)
            val fileEntry = metadata?.files?.getOrNull(fileIndex)
            val fileName = fileEntry?.path ?: "Video Stream"

            sources.add(
                PlaybackSource(
                    mediaId = mediaId,
                    title = "P2P: $fileName (Direct Stream)",
                    streamUrl = streamUrl,
                    isLiveOrHls = false,
                    licenseNotice = "Decentralized BitTorrent Stream • Local Loopback (127.0.0.1)",
                    availableQualities = listOf(VideoQuality.AUTO, VideoQuality.QUALITY_1080P),
                    availableAudioTracks = listOf("Default Audio Track"),
                    availableSubtitles = listOf("Off")
                )
            )

            StreamXLog.i(TAG, "Generated P2P stream source: $streamUrl for torrent $infoHash (file $fileIndex)")
        } catch (e: Exception) {
            StreamXLog.e(TAG, "Failed to resolve P2P stream for $mediaId: ${e.message}")
            throw e
        }

        sources
    }

    override suspend fun reportProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean,
        event: PlaybackEventType
    ) {
        if (!mediaId.startsWith("p2p_", ignoreCase = true) && !mediaId.startsWith("magnet:?", ignoreCase = true)) {
            return
        }

        val infoHash = if (mediaId.startsWith("p2p_")) {
            val raw = mediaId.removePrefix("p2p_")
            if (raw.contains("_file_")) raw.substringBefore("_file_") else raw
        } else {
            mediaId
        }

        when (event) {
            PlaybackEventType.START,
            PlaybackEventType.PROGRESS -> {
                sessionManager.updatePlaybackPosition(infoHash, positionMs)
            }
            PlaybackEventType.STOP -> {
                StreamXLog.i(TAG, "P2P playback stopped for $infoHash at $positionMs ms")
            }
        }
    }
}
