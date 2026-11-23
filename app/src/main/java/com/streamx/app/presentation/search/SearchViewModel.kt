package com.streamx.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.ActiveFilterState
import com.streamx.app.domain.model.ChannelFilterType
import com.streamx.app.domain.model.FilterOption
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ChannelFilterRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedGenre: String? = null,
    val selectedCountry: String? = null,
    val genreOptions: List<FilterOption> = emptyList(),
    val countryOptions: List<FilterOption> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val popularSuggestions: List<String> = listOf("Avatar", "Blue Beetle", "Kalki", "Jujutsu Kaisen", "Anime", "Sci-Fi"),
    val isLoading: Boolean = false
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val catalogRepository: CatalogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val channelFilterRepository: ChannelFilterRepository? = null
) : ViewModel() {

    private val _queryFlow = MutableStateFlow("")
    private val _selectedGenreFlow = MutableStateFlow<String?>(null)
    private val _selectedCountryFlow = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Load dynamic filter chips from ChannelFilterRepository
        viewModelScope.launch {
            val channels = channelFilterRepository?.loadChannelFilters() ?: emptyList()
            val movieChannel = channels.find { it.channelId == 1 } ?: channels.firstOrNull()
            val genres = movieChannel?.filters?.find { it.type == ChannelFilterType.GENRE }?.options ?: emptyList()
            val countries = movieChannel?.filters?.find { it.type == ChannelFilterType.COUNTRY }?.options ?: emptyList()

            _uiState.update {
                it.copy(
                    genreOptions = genres,
                    countryOptions = countries
                )
            }
        }

        // Collect persistent recent searches from DataStore
        viewModelScope.launch {
            userPreferencesRepository.recentSearchesFlow.collect { recents ->
                _uiState.update { it.copy(recentSearches = recents) }
            }
        }

        // Initial default results
        viewModelScope.launch {
            val initial = catalogRepository.search("", null)
            _uiState.update { it.copy(results = initial) }
        }

        // StateFlow debounced search query + multi-filter combining
        viewModelScope.launch {
            combine(
                _queryFlow.debounce(250),
                _selectedGenreFlow,
                _selectedCountryFlow
            ) { query, genre, country ->
                Triple(query, genre, country)
            }.collectLatest { (query, genre, country) ->
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        query = query,
                        selectedGenre = genre,
                        selectedCountry = country
                    )
                }

                val searchBase = catalogRepository.search(query, genre)
                val finalResults = if (country.isNullOrBlank() || country == "All") {
                    searchBase
                } else if (channelFilterRepository != null) {
                    val filterState = ActiveFilterState(
                        selectedGenre = genre ?: "All",
                        selectedCountry = country
                    )
                    channelFilterRepository.filterMediaItems(searchBase, filterState)
                } else {
                    searchBase.filter { it.country.equals(country, ignoreCase = true) }
                }

                _uiState.update { it.copy(isLoading = false, results = finalResults) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _queryFlow.value = newQuery
        _uiState.update { it.copy(query = newQuery) }
        if (newQuery.length >= 3) {
            viewModelScope.launch {
                userPreferencesRepository.addSearchQuery(newQuery)
            }
        }
    }

    fun onGenreSelect(genre: String) {
        val newGenre = if (_selectedGenreFlow.value == genre || genre == "All") null else genre
        _selectedGenreFlow.value = newGenre
        _uiState.update { it.copy(selectedGenre = newGenre) }
    }

    fun onCountrySelect(country: String) {
        val newCountry = if (_selectedCountryFlow.value == country || country == "All") null else country
        _selectedCountryFlow.value = newCountry
        _uiState.update { it.copy(selectedCountry = newCountry) }
    }

    fun onRecentQueryClick(query: String) {
        onQueryChange(query)
    }

    fun removeRecentQuery(query: String) {
        viewModelScope.launch {
            userPreferencesRepository.removeSearchQuery(query)
        }
    }

    fun clearAllRecentQueries() {
        viewModelScope.launch {
            userPreferencesRepository.clearSearchHistory()
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }
}
