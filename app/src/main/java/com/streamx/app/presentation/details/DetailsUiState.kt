package com.streamx.app.presentation.details

import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.StremioVideo

data class DetailsUiState(
    val isLoading: Boolean = true,
    val mediaItem: MediaItem? = null,
    val isSaved: Boolean = false,
    val similarTitles: List<MediaItem> = emptyList(),
    val errorMessage: String? = null,
    // Nuvio-Style Episodes & Multi-Source Selection
    val allEpisodes: List<StremioVideo> = emptyList(),
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val displayedEpisodes: List<StremioVideo> = emptyList(),
    val showSourcesSheet: Boolean = false,
    val isSourcesLoading: Boolean = false,
    val availableSources: List<PlaybackSource> = emptyList(),
    val sourcesErrorMessage: String? = null,
    val activeTargetId: String? = null,
    val activeEpisodeTitle: String? = null
)
