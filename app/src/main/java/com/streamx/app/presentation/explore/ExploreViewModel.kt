package com.streamx.app.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.*
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ChannelFilterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val channels: List<ChannelType> = emptyList(),
    val selectedChannelId: Int = 1,
    val selectedChannelName: String = "Movie",
    val activeFilters: ActiveFilterState = ActiveFilterState(),
    val genres: List<FilterOption> = emptyList(),
    val countries: List<FilterOption> = emptyList(),
    val years: List<FilterOption> = emptyList(),
    val sortOptions: List<FilterOption> = emptyList(),
    val ratingRange: FilterRange = FilterRange(0f, 10f),
    val languageOptions: List<FilterOption> = emptyList(),
    val colNum: Int = 3,
    val shape: String = "Vertical",
    val isAdvancedFilterSheetOpen: Boolean = false,
    val trendingSpotlight: List<MediaItem> = emptyList(),
    val awardWinners: List<MediaItem> = emptyList(),
    val bingeWorthySeries: List<MediaItem> = emptyList(),
    val filteredGridItems: List<MediaItem> = emptyList(),
    val filteredGridRows: List<List<MediaItem>> = emptyList(),
    val isLoading: Boolean = false
)

class ExploreViewModel(
    private val catalogRepository: CatalogRepository,
    private val channelFilterRepository: ChannelFilterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        loadDiscoveryHub()
    }

    private fun loadDiscoveryHub() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Load Channels & Filters from API
            val channels = channelFilterRepository.loadChannelFilters()
            val movieChannel = channels.find { it.channelId == 1 } ?: channels.firstOrNull()

            val genres = movieChannel?.filters
                ?.find { it.type == ChannelFilterType.GENRE }?.options ?: emptyList()

            val countries = movieChannel?.filters
                ?.find { it.type == ChannelFilterType.COUNTRY }?.options ?: emptyList()

            val years = movieChannel?.filters
                ?.find { it.type == ChannelFilterType.YEAR }?.options ?: emptyList()

            val sortOptions = movieChannel?.filters
                ?.find { it.type == ChannelFilterType.SORT }?.options ?: emptyList()

            val ratingRange = movieChannel?.filters
                ?.find { it.type == ChannelFilterType.RATE }?.range ?: FilterRange(0f, 10f)

            val languages = movieChannel?.filters
                ?.find { it.type == ChannelFilterType.CLASSIFY_LANGUAGE }?.options ?: emptyList()

            val colNum = movieChannel?.style?.colNum ?: 3
            val shape = movieChannel?.style?.shape ?: "Vertical"

            // 2. Load Curated collections
            val trending = catalogRepository.getHeroItems()
            val awards = catalogRepository.getAwardWinners()
            val series = catalogRepository.getBingeWorthySeries()

            // 3. Load initial filtered grid items based on default filters
            val initialFilters = ActiveFilterState(
                channelId = movieChannel?.channelId ?: 1,
                channelName = movieChannel?.channelName ?: "Movie"
            )
            val filteredItems = channelFilterRepository.discoverMediaWithFilters(initialFilters)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    channels = channels,
                    selectedChannelId = movieChannel?.channelId ?: 1,
                    selectedChannelName = movieChannel?.channelName ?: "Movie",
                    activeFilters = initialFilters,
                    genres = genres,
                    countries = countries,
                    years = years,
                    sortOptions = sortOptions,
                    ratingRange = ratingRange,
                    languageOptions = languages,
                    colNum = colNum,
                    shape = shape,
                    trendingSpotlight = trending,
                    awardWinners = awards,
                    bingeWorthySeries = series,
                    filteredGridItems = filteredItems,
                    filteredGridRows = filteredItems.chunked(colNum)
                )
            }
        }
    }

    fun selectChannel(channelId: Int) {
        val channel = _uiState.value.channels.find { it.channelId == channelId } ?: return
        val genres = channel.filters.find { it.type == ChannelFilterType.GENRE }?.options ?: emptyList()
        val countries = channel.filters.find { it.type == ChannelFilterType.COUNTRY }?.options ?: emptyList()
        val years = channel.filters.find { it.type == ChannelFilterType.YEAR }?.options ?: emptyList()
        val sortOptions = channel.filters.find { it.type == ChannelFilterType.SORT }?.options ?: emptyList()
        val ratingRange = channel.filters.find { it.type == ChannelFilterType.RATE }?.range ?: FilterRange(0f, 10f)
        val languages = channel.filters.find { it.type == ChannelFilterType.CLASSIFY_LANGUAGE }?.options ?: emptyList()

        val updatedFilters = _uiState.value.activeFilters.copy(
            channelId = channel.channelId,
            channelName = channel.channelName
        )

        _uiState.update {
            it.copy(
                selectedChannelId = channel.channelId,
                selectedChannelName = channel.channelName,
                activeFilters = updatedFilters,
                genres = genres,
                countries = countries,
                years = years,
                sortOptions = sortOptions,
                ratingRange = ratingRange,
                languageOptions = languages,
                colNum = channel.style.colNum,
                shape = channel.style.shape
            )
        }
        applyFilters(updatedFilters)
    }

    fun selectGenre(genre: String) {
        val newGenre = if (_uiState.value.activeFilters.selectedGenre == genre) "All" else genre
        val updated = _uiState.value.activeFilters.copy(selectedGenre = newGenre)
        _uiState.update { it.copy(activeFilters = updated) }
        applyFilters(updated)
    }

    fun selectCountry(country: String) {
        val newCountry = if (_uiState.value.activeFilters.selectedCountry == country) "All" else country
        val updated = _uiState.value.activeFilters.copy(selectedCountry = newCountry)
        _uiState.update { it.copy(activeFilters = updated) }
        applyFilters(updated)
    }

    fun selectYear(year: String) {
        val newYear = if (_uiState.value.activeFilters.selectedYear == year) "All" else year
        val updated = _uiState.value.activeFilters.copy(selectedYear = newYear)
        _uiState.update { it.copy(activeFilters = updated) }
        applyFilters(updated)
    }

    fun selectSort(sort: String) {
        val updated = _uiState.value.activeFilters.copy(selectedSort = sort)
        _uiState.update { it.copy(activeFilters = updated) }
        applyFilters(updated)
    }

    fun selectMinRating(rating: Float) {
        val updated = _uiState.value.activeFilters.copy(selectedMinRating = rating)
        _uiState.update { it.copy(activeFilters = updated) }
        applyFilters(updated)
    }

    fun selectLanguage(language: String) {
        val newLang = if (_uiState.value.activeFilters.selectedLanguage == language) "All" else language
        val updated = _uiState.value.activeFilters.copy(selectedLanguage = newLang)
        _uiState.update { it.copy(activeFilters = updated) }
        applyFilters(updated)
    }

    fun resetFilters() {
        val resetState = ActiveFilterState(
            channelId = _uiState.value.selectedChannelId,
            channelName = _uiState.value.selectedChannelName
        )
        _uiState.update { it.copy(activeFilters = resetState) }
        applyFilters(resetState)
    }

    fun toggleAdvancedFilterSheet(open: Boolean) {
        _uiState.update { it.copy(isAdvancedFilterSheetOpen = open) }
    }

    private fun applyFilters(filters: ActiveFilterState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val filtered = channelFilterRepository.discoverMediaWithFilters(filters)
            _uiState.update {
                it.copy(
                    filteredGridItems = filtered,
                    filteredGridRows = filtered.chunked(it.colNum),
                    isLoading = false
                )
            }
        }
    }
}
