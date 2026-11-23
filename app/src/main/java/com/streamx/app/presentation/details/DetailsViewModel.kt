package com.streamx.app.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.ContentFormat
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.StremioVideo
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.PlaybackRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import com.streamx.app.utils.ContentIdResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val mediaId: String,
    private val catalogRepository: CatalogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val manifestAddonRepository: ManifestAddonRepository? = null,
    private val playbackRepository: PlaybackRepository? = null
) : ViewModel() {

    class Factory(
        private val mediaId: String,
        private val catalogRepository: CatalogRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val manifestAddonRepository: ManifestAddonRepository? = null,
        private val playbackRepository: PlaybackRepository? = null
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailsViewModel(
                mediaId,
                catalogRepository,
                userPreferencesRepository,
                manifestAddonRepository,
                playbackRepository
            ) as T
        }
    }

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
        observeWatchlist()
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            userPreferencesRepository.watchlistIdsFlow.collect { ids ->
                _uiState.update { it.copy(isSaved = ids.contains(mediaId)) }
            }
        }
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val item = catalogRepository.getMediaDetails(mediaId)
            if (item != null) {
                val isSaved = userPreferencesRepository.isSaved(mediaId)
                val similar = catalogRepository.getSimilarTitles(mediaId)

                // Check if item is series / anime to fetch episodes from Cinemeta / Stremio meta add-on
                val canonicalId = ContentIdResolver.resolveCanonicalId(item.id)
                val isSeriesOrAnime = item.format == ContentFormat.SERIES || item.format == ContentFormat.ANIME

                var episodesList = emptyList<StremioVideo>()
                var seasonsList = emptyList<Int>()
                var defaultSeason = 1

                if (isSeriesOrAnime && manifestAddonRepository != null) {
                    try {
                        val meta = manifestAddonRepository.getMediaMeta(
                            type = ContentIdResolver.inferContentType(item.format, canonicalId),
                            id = canonicalId
                        )
                        if (!meta?.videos.isNullOrEmpty()) {
                            episodesList = meta!!.videos!!
                            seasonsList = episodesList.map { it.season }.distinct().sorted()
                            defaultSeason = seasonsList.firstOrNull() ?: 1
                        }
                    } catch (_: Exception) {}
                }

                // If no remote episodes were returned for a Series/Anime in mock catalog, provide default episodes
                if (isSeriesOrAnime && episodesList.isEmpty()) {
                    episodesList = (1..8).map { epNum ->
                        StremioVideo(
                            id = "$canonicalId:1:$epNum",
                            title = "Episode $epNum",
                            season = 1,
                            episode = epNum,
                            overview = "Official episode stream available via configured Stremio add-ons."
                        )
                    }
                    seasonsList = listOf(1)
                    defaultSeason = 1
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        mediaItem = item,
                        isSaved = isSaved,
                        similarTitles = similar,
                        allEpisodes = episodesList,
                        seasons = seasonsList,
                        selectedSeason = defaultSeason,
                        displayedEpisodes = episodesList.filter { ep -> ep.season == defaultSeason }
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Title details not found."
                    )
                }
            }
        }
    }

    fun selectSeason(season: Int) {
        val currentEpisodes = _uiState.value.allEpisodes
        _uiState.update {
            it.copy(
                selectedSeason = season,
                displayedEpisodes = currentEpisodes.filter { ep -> ep.season == season }
            )
        }
    }

    fun openSourcesSheet(targetId: String = mediaId, episodeTitle: String? = null) {
        _uiState.update {
            it.copy(
                showSourcesSheet = true,
                isSourcesLoading = true,
                availableSources = emptyList(),
                sourcesErrorMessage = null,
                activeTargetId = targetId,
                activeEpisodeTitle = episodeTitle
            )
        }

        viewModelScope.launch {
            com.streamx.app.utils.StreamXLog.d("DetailsViewModel", "openSourcesSheet: targetId=$targetId, title=$episodeTitle")
            if (playbackRepository != null) {
                try {
                    val sources = playbackRepository.getAvailablePlaybackSources(targetId)
                    com.streamx.app.utils.StreamXLog.d("DetailsViewModel", "openSourcesSheet discovered ${sources.size} sources for $targetId")
                    _uiState.update {
                        it.copy(
                            isSourcesLoading = false,
                            availableSources = sources,
                            sourcesErrorMessage = if (sources.isEmpty()) "No playback sources found for this title." else null
                        )
                    }
                } catch (e: Exception) {
                    com.streamx.app.utils.StreamXLog.e("DetailsViewModel", "openSourcesSheet error for $targetId: ${e.message}")
                    _uiState.update {
                        it.copy(
                            isSourcesLoading = false,
                            sourcesErrorMessage = e.message ?: "Failed to discover sources."
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSourcesLoading = false,
                        sourcesErrorMessage = "Playback engine unavailable."
                    )
                }
            }
        }
    }

    fun closeSourcesSheet() {
        _uiState.update {
            it.copy(showSourcesSheet = false)
        }
    }

    fun toggleWatchlist() {
        val currentItem = _uiState.value.mediaItem ?: return
        viewModelScope.launch {
            userPreferencesRepository.toggleWatchlist(currentItem)
            val newSavedState = userPreferencesRepository.isSaved(mediaId)
            _uiState.update { it.copy(isSaved = newSavedState) }
        }
    }
}
