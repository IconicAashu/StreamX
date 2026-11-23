package com.streamx.app.presentation.home

import com.streamx.app.domain.model.AddonCatalogSection
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem

data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedCategory: MediaCategory = MediaCategory.TRENDING,
    val availableCategories: List<MediaCategory> = listOf(
        MediaCategory.TRENDING,
        MediaCategory.NEW,
        MediaCategory.MOVIES,
        MediaCategory.SERIES,
        MediaCategory.TV_SHOW,
        MediaCategory.ANIME
    ),
    val heroItems: List<MediaItem> = emptyList(),
    val forYouItems: List<MediaItem> = emptyList(),
    val trendingItems: List<MediaItem> = emptyList(),
    val moviesItems: List<MediaItem> = emptyList(),
    val seriesItems: List<MediaItem> = emptyList(),
    val animeItems: List<MediaItem> = emptyList(),
    val bollywoodSouthIndianItems: List<MediaItem> = emptyList(),
    val continueWatchingItems: List<MediaItem> = emptyList(),
    val categoryFilteredItems: List<MediaItem> = emptyList(),
    val addonSections: List<AddonCatalogSection> = emptyList()
)
