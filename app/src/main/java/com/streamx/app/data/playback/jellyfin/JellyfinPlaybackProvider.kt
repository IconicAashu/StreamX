package com.streamx.app.data.playback.jellyfin

import com.streamx.app.domain.jellyfin.JellyfinRepository
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.SubtitleTrackOption
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.playback.PlaybackEventType
import com.streamx.app.domain.playback.PlaybackProvider
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JellyfinPlaybackProvider(
    private val jellyfinRepository: JellyfinRepository
) : PlaybackProvider {

    override val providerId: String = "jellyfin"
    override val displayName: String = "Jellyfin Media Server"

    // Cache active playback session info for progress reporting
    private var lastPlaySessionId: String? = null
    private var lastMediaSourceId: String? = null

    override suspend fun canHandle(mediaId: String): Boolean {
        return mediaId.startsWith("jellyfin_", ignoreCase = true)
    }

    override suspend fun getStreams(mediaId: String): List<PlaybackSource> = withContext(Dispatchers.IO) {
        val server = jellyfinRepository.activeServerFlow.value
            ?: throw IllegalStateException("No active Jellyfin server connected. Please connect in Settings or Jellyfin screen.")

        val cleanItemId = mediaId.removePrefix("jellyfin_")
        val playbackInfoResult = jellyfinRepository.getPlaybackInfo(cleanItemId)
        if (playbackInfoResult.isFailure) {
            val error = playbackInfoResult.exceptionOrNull()
            throw IllegalStateException("Jellyfin failed to provide playback info: ${error?.message ?: "Unknown error"}")
        }

        val playbackInfo = playbackInfoResult.getOrThrow()
        lastPlaySessionId = playbackInfo.playSessionId

        val base = server.baseUrl.trimEnd('/')
        val apiKey = server.apiKey
        val sources = mutableListOf<PlaybackSource>()

        for (mediaSource in playbackInfo.mediaSources) {
            lastMediaSourceId = mediaSource.id

            // Extract Audio Streams
            val audioStreams = mediaSource.mediaStreams.filter { it.type.equals("Audio", ignoreCase = true) }
            val audioTrackNames = if (audioStreams.isNotEmpty()) {
                audioStreams.map { it.displayTitle ?: it.language ?: "Audio ${it.index}" }
            } else {
                listOf("Original Audio")
            }

            // Extract Subtitle Streams
            val subtitleStreams = mediaSource.mediaStreams.filter { it.type.equals("Subtitle", ignoreCase = true) }
            val subtitleTrackNames = mutableListOf("Off")
            val externalSubtitles = mutableListOf<SubtitleTrackOption>()

            subtitleStreams.forEach { sub ->
                val name = sub.displayTitle ?: sub.language ?: "Sub ${sub.index}"
                subtitleTrackNames.add(name)

                // If external or delivery URL provided, add as SubtitleTrackOption
                val deliveryPath = sub.deliveryUrl ?: "/Videos/$cleanItemId/${mediaSource.id}/Subtitles/${sub.index}/Stream.vtt"
                val subUrl = if (deliveryPath.startsWith("http://", ignoreCase = true) || deliveryPath.startsWith("https://", ignoreCase = true)) {
                    deliveryPath
                } else {
                    val connector = if (deliveryPath.contains("?")) "&" else "?"
                    "$base$deliveryPath${connector}api_key=$apiKey"
                }

                externalSubtitles.add(
                    SubtitleTrackOption(
                        id = "jellyfin_sub_${sub.index}",
                        label = name,
                        language = sub.language ?: "und",
                        uri = subUrl,
                        isSelected = sub.isDefault
                    )
                )
            }

            val authHeaders = mapOf(
                "X-Emby-Token" to apiKey,
                "Accept" to "*/*"
            )

            // 1. Direct Play Stream (static stream url)
            val directPlayUrl = "$base/Videos/$cleanItemId/stream?static=true&mediaSourceId=${mediaSource.id}&api_key=$apiKey"
            sources.add(
                PlaybackSource(
                    mediaId = mediaId,
                    title = "Jellyfin: Direct Play (${mediaSource.container?.uppercase() ?: "Original"})",
                    streamUrl = directPlayUrl,
                    isLiveOrHls = false,
                    licenseNotice = "Direct Play • Jellyfin Authorized",
                    requestHeaders = authHeaders,
                    availableQualities = listOf(VideoQuality.AUTO, VideoQuality.QUALITY_1080P, VideoQuality.QUALITY_720P),
                    availableAudioTracks = audioTrackNames,
                    availableSubtitles = subtitleTrackNames,
                    externalSubtitles = externalSubtitles
                )
            )

            // 2. Transcoding / Direct Stream (HLS master playlist)
            val transcodingPath = mediaSource.transcodingUrl ?: "/Videos/$cleanItemId/main.m3u8?mediaSourceId=${mediaSource.id}&api_key=$apiKey"
            val transcodingUrl = if (transcodingPath.startsWith("http://", ignoreCase = true) || transcodingPath.startsWith("https://", ignoreCase = true)) {
                transcodingPath
            } else {
                "$base$transcodingPath"
            }

            sources.add(
                PlaybackSource(
                    mediaId = mediaId,
                    title = "Jellyfin: Adaptive Stream (HLS)",
                    streamUrl = transcodingUrl,
                    isLiveOrHls = true,
                    licenseNotice = "Adaptive Transcode • Jellyfin Authorized",
                    requestHeaders = authHeaders,
                    availableQualities = listOf(VideoQuality.AUTO, VideoQuality.QUALITY_1080P, VideoQuality.QUALITY_720P, VideoQuality.QUALITY_480P),
                    availableAudioTracks = audioTrackNames,
                    availableSubtitles = subtitleTrackNames,
                    externalSubtitles = externalSubtitles
                )
            )
        }

        if (sources.isEmpty()) {
            throw IllegalStateException("Jellyfin server reported 0 available media sources for item $cleanItemId")
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
        withContext(Dispatchers.IO) {
            val cleanItemId = mediaId.removePrefix("jellyfin_")
            val positionTicks = positionMs * 10000L // 1ms = 10,000 ticks in Jellyfin

            try {
                when (event) {
                    PlaybackEventType.START -> {
                        jellyfinRepository.reportPlaybackStart(
                            itemId = cleanItemId,
                            mediaSourceId = lastMediaSourceId,
                            playSessionId = lastPlaySessionId,
                            positionTicks = positionTicks
                        )
                    }
                    PlaybackEventType.PROGRESS -> {
                        jellyfinRepository.reportPlaybackProgress(
                            itemId = cleanItemId,
                            mediaSourceId = lastMediaSourceId,
                            playSessionId = lastPlaySessionId,
                            positionTicks = positionTicks,
                            isPaused = isPaused
                        )
                    }
                    PlaybackEventType.STOP -> {
                        jellyfinRepository.reportPlaybackStopped(
                            itemId = cleanItemId,
                            mediaSourceId = lastMediaSourceId,
                            playSessionId = lastPlaySessionId,
                            positionTicks = positionTicks
                        )
                    }
                }
            } catch (e: Exception) {
                StreamXLog.e("JellyfinPlayback", "Failed to report progress ($event) to Jellyfin: ${e.message}")
            }
        }
    }
}
