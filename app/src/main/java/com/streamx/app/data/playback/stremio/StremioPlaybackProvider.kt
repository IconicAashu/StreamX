package com.streamx.app.data.playback.stremio

import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.playback.PlaybackEventType
import com.streamx.app.domain.playback.PlaybackProvider
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.utils.DurationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StremioPlaybackProvider(
    private val manifestAddonRepository: ManifestAddonRepository
) : PlaybackProvider {

    override val providerId: String = "stremio"
    override val displayName: String = "Stremio Community Add-ons"

    override suspend fun canHandle(mediaId: String): Boolean {
        // Handles standard IMDb (tt...), Kitsu, Cinemeta, and TMDB media items
        return !mediaId.startsWith("jellyfin_", ignoreCase = true)
    }

    override suspend fun getStreams(mediaId: String): List<PlaybackSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<PlaybackSource>()

        // 1. Direct verified streamUrl from cached addon item
        val addonItem = manifestAddonRepository.getMediaItemById(mediaId)
        if (addonItem != null && addonItem.streamUrl.isNotBlank()) {
            sources.add(
                PlaybackSource(
                    mediaId = mediaId,
                    title = "Add-on: ${addonItem.title}",
                    streamUrl = addonItem.streamUrl,
                    isLiveOrHls = addonItem.streamUrl.contains(".m3u8", ignoreCase = true),
                    durationSeconds = DurationParser.parseToSeconds(addonItem.runtimeFormatted),
                    licenseNotice = "Direct Add-on Stream • Source discovered",
                    availableQualities = listOf(VideoQuality.AUTO),
                    availableAudioTracks = addonItem.audioLanguages.ifEmpty { listOf("Original Audio") },
                    availableSubtitles = addonItem.subtitleLanguages.ifEmpty { listOf("Off") },
                    providerName = "Direct Add-on"
                )
            )
        }

        // 2. Discover external subtitles from enabled subtitle add-ons
        val externalSubs = try {
            manifestAddonRepository.getSubtitlesForMedia(type = "", id = mediaId)
        } catch (_: Exception) {
            emptyList()
        }

        // 3. Query all enabled compatible Stremio stream provider add-ons
        try {
            val addonStreams = manifestAddonRepository.getStreamsForMedia(type = "", id = mediaId)
            for (stream in addonStreams) {
                if (stream.streamUrl.isNotBlank()) {
                    sources.add(stream.copy(externalSubtitles = externalSubs))
                }
            }
        } catch (_: Exception) {
            // Isolate provider failure
        }

        sources.distinctBy { it.streamUrl }
    }

    override suspend fun reportProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean,
        event: PlaybackEventType
    ) {
        // Stremio add-ons are stateless; progress is persisted locally by user preferences
    }
}
