package com.streamx.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ManifestAddonRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val manifestAddonRepository: ManifestAddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var paginationJob: Job? = null

    init {
        loadHomeData()
        observeAddonCatalogs()
    }

    private fun observeAddonCatalogs() {
        viewModelScope.launch {
            manifestAddonRepository.addonCatalogsFlow.collect { sections ->
                _uiState.update { it.copy(addonSections = sections) }
            }
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Load Hero Items
            val hero = catalogRepository.getHeroItems()

            // 2. Load Core Content Categories
            val trending = catalogRepository.getItemsByCategory(MediaCategory.TRENDING)
            val movies = catalogRepository.getItemsByCategory(MediaCategory.MOVIES)
            val series = catalogRepository.getItemsByCategory(MediaCategory.SERIES)
            val anime = catalogRepository.getItemsByCategory(MediaCategory.ANIME)
            val forYou = catalogRepository.getForYouItems()
            val bollywood = catalogRepository.getBollywoodAndSouthIndian()
            val continueWatching = catalogRepository.getContinueWatching()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPage = 1,
                    hasMore = false,
                    heroItems = hero,
                    forYouItems = forYou,
                    trendingItems = trending,
                    moviesItems = movies,
                    seriesItems = series,
                    animeItems = anime,
                    bollywoodSouthIndianItems = bollywood,
                    continueWatchingItems = continueWatching,
                    categoryFilteredItems = trending
                )
            }
        }
    }

    fun selectCategory(category: MediaCategory) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                currentPage = 1,
                hasMore = false,
                isLoading = true
            )
        }
        viewModelScope.launch {
            val items = catalogRepository.getItemsByCategory(category)
            val updatedHero = if (category == MediaCategory.TRENDING) {
                catalogRepository.getHeroItems()
            } else if (items.isNotEmpty()) {
                items.take(5)
            } else {
                _uiState.value.heroItems
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPage = 1,
                    hasMore = false,
                    heroItems = updatedHero,
                    categoryFilteredItems = items,
                    trendingItems = if (category == MediaCategory.TRENDING) items else it.trendingItems
                )
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore) return

        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            // All catalogs are aggregated from installed add-ons and TMDB
            _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
        }
    }

    fun filterByGenre(genre: String) {
        _uiState.update { it.copy(isLoading = true, currentPage = 1, hasMore = false) }
        viewModelScope.launch {
            val items = catalogRepository.discoverByGenre(genre)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPage = 1,
                    hasMore = false,
                    categoryFilteredItems = items
                )
            }
        }
    }
}
