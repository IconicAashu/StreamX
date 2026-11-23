package com.streamx.app.data.repository

import com.streamx.app.data.local.UserPreferencesDataStore
import com.streamx.app.data.playback.stremio.StremioPlaybackProvider
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.playback.PlaybackEventType
import com.streamx.app.domain.playback.PlaybackProvider
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.PlaybackRepository
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal, provider-independent Playback Repository.
 * Dispatches stream queries and progress reports to registered production PlaybackProviders
 * (JellyfinPlaybackProvider, StremioPlaybackProvider, P2PPlaybackProvider).
 */
class PlaybackRepositoryImpl(
    private val dataStore: UserPreferencesDataStore? = null,
    private val manifestAddonRepository: ManifestAddonRepository,
    private val providers: List<PlaybackProvider> = listOf(
        StremioPlaybackProvider(manifestAddonRepository)
    )
) : PlaybackRepository {

    private val sourcesCache = java.util.concurrent.ConcurrentHashMap<String, List<PlaybackSource>>()
    private var activeSelectedSource: PlaybackSource? = null

    override fun setCurrentSelectedSource(source: PlaybackSource?) {
        activeSelectedSource = source
    }

    override fun getCurrentSelectedSource(mediaId: String?, streamUrl: String?): PlaybackSource? {
        val selected = activeSelectedSource
        if (selected != null) {
            val matchesMedia = mediaId.isNullOrBlank() || selected.mediaId == mediaId
            val matchesUrl = streamUrl.isNullOrBlank() || selected.streamUrl == streamUrl
            if (matchesMedia && matchesUrl) {
                return selected
            }
        }
        if (!mediaId.isNullOrBlank() && !streamUrl.isNullOrBlank()) {
            val cachedList = sourcesCache[mediaId]
            val cachedSource = cachedList?.firstOrNull { it.streamUrl == streamUrl }
            if (cachedSource != null) return cachedSource
        }
        return if (streamUrl.isNullOrBlank()) selected else null
    }

    override suspend fun getAvailablePlaybackSources(mediaId: String, forceRefresh: Boolean): List<PlaybackSource> = withContext(Dispatchers.IO) {
        val aggregatedSources = mutableListOf<PlaybackSource>()

        // Dispatch to all providers that can handle this media ID
        val matchingProviders = providers.filter { it.canHandle(mediaId) }
        StreamXLog.d(
            "PlaybackRepositoryImpl",
            "getAvailablePlaybackSources: mediaId=$mediaId, matchingProviders=${matchingProviders.map { it.providerId }}"
        )

        for (provider in matchingProviders) {
            try {
                val streams = provider.getStreams(mediaId)
                StreamXLog.d("PlaybackRepositoryImpl", "Provider ${provider.providerId} returned ${streams.size} streams for $mediaId")
                aggregatedSources.addAll(streams)
            } catch (e: Exception) {
                StreamXLog.w("PlaybackRepositoryImpl", "Provider ${provider.providerId} failed for $mediaId: ${e.message}")
                // If it's a dedicated provider (like Jellyfin) and it failed, rethrow if no other providers match
                if (matchingProviders.size == 1) {
                    throw e
                }
            }
        }

        // Deduplicate streams by streamUrl
        val deduplicated = aggregatedSources.distinctBy { it.streamUrl }
        StreamXLog.d("PlaybackRepositoryImpl", "Discovered ${deduplicated.size} deduplicated sources for $mediaId")

        if (deduplicated.isEmpty()) {
            val installed = manifestAddonRepository.installedAddonsFlow.value
            val streamAddons = installed.filter { it.isStreamProvider }
            val metadataAddons = installed.filter { it.isMetadataOnly }
            val failureDetail = buildString {
                append("No playback authorized source available for this title ($mediaId).\n\n")
                append("Add-on Capability Status:\n")
                if (streamAddons.isEmpty()) {
                    append("• No stream provider add-on installed or enabled.\n")
                } else {
                    streamAddons.forEach { addon ->
                        val status = if (addon.isEnabled) "Enabled" else "Disabled"
                        append("• ${addon.name} ($status): 0 playable HTTP streams returned\n")
                    }
                }
                if (metadataAddons.isNotEmpty()) {
                    append("• Metadata-only (${metadataAddons.joinToString { it.name }}): Provides catalog & info only\n")
                }
                append("\nPlease install an authorized Stremio stream provider add-on in Settings > Add-ons, or connect a Jellyfin server.")
            }
            throw IllegalStateException(failureDetail)
        }

        sourcesCache[mediaId] = deduplicated
        deduplicated
    }

    override suspend fun getPlaybackSource(mediaId: String, forceRefresh: Boolean): PlaybackSource = withContext(Dispatchers.IO) {
        val sources = getAvailablePlaybackSources(mediaId, forceRefresh)
        sources.first()
    }

    override suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {
        withContext(Dispatchers.IO) {
            try {
                dataStore?.savePlaybackPosition(mediaId, positionMs, durationMs)
                // Report progress to matching providers (e.g. Jellyfin server sync)
                providers.filter { it.canHandle(mediaId) }.forEach { provider ->
                    provider.reportProgress(mediaId, positionMs, durationMs, isPaused = false, PlaybackEventType.PROGRESS)
                }
            } catch (_: Exception) {}
        }
    }

    override suspend fun getPlaybackPosition(mediaId: String): Long = withContext(Dispatchers.IO) {
        dataStore?.getPlaybackPosition(mediaId) ?: 0L
    }
}
