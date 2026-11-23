package com.streamx.app.presentation.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.ContentFormat
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.PlaybackRepository
import com.streamx.app.utils.ContentIdResolver
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceSelectionUiState(
    val isLoading: Boolean = true,
    val mediaItem: MediaItem? = null,
    val targetId: String = "",
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val sources: List<PlaybackSource> = emptyList(),
    val selectedSource: PlaybackSource? = null,
    val errorMessage: String? = null,
    val emptyDetail: String? = null,
    val unsupportedMessage: String? = null
)

class SourceSelectionViewModel(
    val mediaId: String,
    val targetId: String,
    val initialEpisodeTitle: String?,
    private val catalogRepository: CatalogRepository,
    private val playbackRepository: PlaybackRepository,
    private val manifestAddonRepository: ManifestAddonRepository
) : ViewModel() {

    class Factory(
        private val mediaId: String,
        private val targetId: String,
        private val initialEpisodeTitle: String?,
        private val catalogRepository: CatalogRepository,
        private val playbackRepository: PlaybackRepository,
        private val manifestAddonRepository: ManifestAddonRepository
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SourceSelectionViewModel(
                mediaId = mediaId,
                targetId = targetId,
                initialEpisodeTitle = initialEpisodeTitle,
                catalogRepository = catalogRepository,
                playbackRepository = playbackRepository,
                manifestAddonRepository = manifestAddonRepository
            ) as T
        }
    }

    private val _uiState = MutableStateFlow(
        SourceSelectionUiState(
            targetId = targetId,
            episodeTitle = initialEpisodeTitle
        )
    )
    val uiState: StateFlow<SourceSelectionUiState> = _uiState.asStateFlow()

    init {
        parseSeasonAndEpisode()
        loadSources()
    }

    private fun parseSeasonAndEpisode() {
        val epRegex = Regex("(?::|season:|s)(\\d+)(?::|episode:|e)(\\d+)$", RegexOption.IGNORE_CASE)
        val match = epRegex.find(targetId)
        if (match != null) {
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            _uiState.update {
                it.copy(
                    seasonNumber = season,
                    episodeNumber = episode
                )
            }
        }
    }

    fun loadSources() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    emptyDetail = null,
                    unsupportedMessage = null
                )
            }

            try {
                // 1. Fetch MediaItem metadata for hero display
                val item = catalogRepository.getMediaDetails(mediaId)
                if (item != null) {
                    _uiState.update { it.copy(mediaItem = item) }
                }

                // 2. Discover sources via PlaybackRepository across all compatible add-ons
                StreamXLog.d(
                    "SourceSelectionViewModel",
                    "Querying compatible stream providers for targetId='$targetId' (mediaId='$mediaId')"
                )
                val discoveredSources = playbackRepository.getAvailablePlaybackSources(targetId)

                // 3. Pre-select first direct playable stream (HTTP/HLS/DASH)
                val autoSelected = discoveredSources.firstOrNull { isDirectPlayable(it) }
                    ?: discoveredSources.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sources = discoveredSources,
                        selectedSource = autoSelected,
                        errorMessage = null,
                        emptyDetail = null
                    )
                }
            } catch (e: Exception) {
                val failureMessage = e.message ?: "Failed to discover stream sources."
                StreamXLog.e("SourceSelectionViewModel", "Source discovery error: $failureMessage")

                val installed = manifestAddonRepository.installedAddonsFlow.value
                val streamAddons = installed.filter { it.isStreamProvider }
                val metadataOnly = installed.filter { it.isMetadataOnly }

                val detail = buildString {
                    if (streamAddons.none { it.isEnabled }) {
                        append("No stream provider add-ons are installed or enabled.\n")
                    } else {
                        streamAddons.filter { it.isEnabled }.forEach { addon ->
                            append("• ${addon.name}: 0 supported streams returned\n")
                        }
                    }
                    if (metadataOnly.isNotEmpty()) {
                        append("\nNote: Add-ons like ${metadataOnly.joinToString { it.name }} are metadata-only providers and do not host playback streams.")
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sources = emptyList(),
                        selectedSource = null,
                        errorMessage = failureMessage,
                        emptyDetail = detail
                    )
                }
            }
        }
    }

    fun selectSource(source: PlaybackSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun retry() {
        loadSources()
    }

    fun showUnsupportedActionNotice(message: String) {
        _uiState.update { it.copy(unsupportedMessage = message) }
    }

    fun dismissUnsupportedNotice() {
        _uiState.update { it.copy(unsupportedMessage = null) }
    }

    fun isDirectPlayable(source: PlaybackSource): Boolean {
        val url = source.streamUrl.trim()
        if (url.isBlank()) return false
        if (url.startsWith("magnet:", ignoreCase = true) || url.startsWith("torrent:", ignoreCase = true)) {
            return false
        }
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    fun getTransportType(source: PlaybackSource): String {
        val url = source.streamUrl.trim().lowercase()
        return when {
            url.contains(".m3u8") || source.isLiveOrHls -> "HLS Stream"
            url.contains(".mpd") -> "DASH Stream"
            url.startsWith("magnet:") || url.contains("info_hash") -> "BitTorrent (P2P)"
            url.contains(".mp4") -> "Direct MP4"
            url.contains(".mkv") -> "Direct MKV"
            url.startsWith("http://") || url.startsWith("https://") -> "Direct HTTP"
            else -> "Stream"
        }
    }
}
