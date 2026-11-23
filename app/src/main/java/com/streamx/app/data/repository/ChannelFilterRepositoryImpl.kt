package com.streamx.app.data.repository

import com.google.gson.Gson
import com.streamx.app.data.local.DefaultChannelFilterData
import com.streamx.app.data.local.MockCatalogDataSource
import com.streamx.app.data.mapper.ChannelFilterMapper
import com.streamx.app.domain.model.ActiveFilterState
import com.streamx.app.domain.model.ChannelType
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.repository.ChannelFilterRepository
import com.streamx.app.domain.repository.TmdbRepository
import com.streamx.app.network.api.ChannelApiService
import com.streamx.app.network.model.ChannelFilterResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

class ChannelFilterRepositoryImpl(
    private val channelApiService: ChannelApiService? = null,
    private val tmdbRepository: TmdbRepository? = null,
    private val gson: Gson = Gson()
) : ChannelFilterRepository {

    private val defaultChannels: List<ChannelType> by lazy {
        try {
            val responseDto = gson.fromJson(
                DefaultChannelFilterData.RAW_CHANNEL_FILTER_JSON,
                ChannelFilterResponseDto::class.java
            )
            ChannelFilterMapper.toDomainList(responseDto)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val _channelFiltersFlow = MutableStateFlow<List<ChannelType>>(defaultChannels)
    override val channelFiltersFlow: StateFlow<List<ChannelType>> = _channelFiltersFlow.asStateFlow()

    override suspend fun loadChannelFilters(remoteUrl: String?): List<ChannelType> = withContext(Dispatchers.IO) {
        if (remoteUrl.isNullOrBlank() || channelApiService == null) {
            return@withContext _channelFiltersFlow.value
        }

        try {
            val response = channelApiService.getChannelFilters(remoteUrl)
            val domainList = ChannelFilterMapper.toDomainList(response)
            if (domainList.isNotEmpty()) {
                _channelFiltersFlow.value = domainList
                domainList
            } else {
                _channelFiltersFlow.value
            }
        } catch (e: Exception) {
            _channelFiltersFlow.value
        }
    }

    override fun filterMediaItems(items: List<MediaItem>, filterState: ActiveFilterState): List<MediaItem> {
        var filtered = items

        // 1. Filter by Genre
        if (filterState.selectedGenre != "All") {
            filtered = filtered.filter { item ->
                item.genres.any { it.equals(filterState.selectedGenre, ignoreCase = true) } ||
                item.primaryGenre.equals(filterState.selectedGenre, ignoreCase = true)
            }
        }

        // 2. Filter by Country
        if (filterState.selectedCountry != "All") {
            filtered = filtered.filter { item ->
                item.country.equals(filterState.selectedCountry, ignoreCase = true) ||
                item.synopsis.contains(filterState.selectedCountry, ignoreCase = true) ||
                item.title.contains(filterState.selectedCountry, ignoreCase = true)
            }
        }

        // 3. Filter by Year
        if (filterState.selectedYear != "All") {
            val selectedYear = filterState.selectedYear
            filtered = if (selectedYear.endsWith("s", ignoreCase = true)) {
                // Decade filter: "2010s", "2000s", "1990s", "1980s"
                val decadeStart = selectedYear.dropLast(1).toIntOrNull() ?: 2020
                val decadeEnd = decadeStart + 9
                filtered.filter { item ->
                    val itemYear = item.releaseYear.toIntOrNull() ?: 0
                    itemYear in decadeStart..decadeEnd
                }
            } else {
                // Specific year: "2026", "2025", "2024", etc.
                filtered.filter { item ->
                    item.releaseYear == selectedYear || item.releaseDateFormatted.contains(selectedYear)
                }
            }
        }

        // 4. Filter by Rating
        if (filterState.selectedMinRating > 0f) {
            filtered = filtered.filter { item ->
                item.rating >= filterState.selectedMinRating
            }
        }

        // 5. Filter by Language / Classification
        if (filterState.selectedLanguage != "All") {
            val cleanLang = filterState.selectedLanguage
                .replace(" dub", "", ignoreCase = true)
                .replace(" sub", "", ignoreCase = true)
                .trim()

            filtered = filtered.filter { item ->
                item.audioLanguages.any { it.contains(cleanLang, ignoreCase = true) } ||
                item.subtitleLanguages.any { it.contains(cleanLang, ignoreCase = true) } ||
                (filterState.selectedLanguage.equals("Original Audio", ignoreCase = true))
            }
        }

        // 6. Sort
        return when (filterState.selectedSort) {
            "Hottest" -> filtered.sortedByDescending { it.rating }
            "Latest" -> filtered.sortedByDescending { it.releaseYear }
            else -> filtered // "ForYou" preserves catalog curation
        }
    }

    override suspend fun discoverMediaWithFilters(filterState: ActiveFilterState): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbItems = if (tmdbRepository != null) {
            if (filterState.selectedGenre != "All") {
                tmdbRepository.discoverByGenre(filterState.selectedGenre)
            } else {
                tmdbRepository.getPopularMovies()
            }
        } else {
            emptyList()
        }

        val localItems = MockCatalogDataSource.items
        val combined = (tmdbItems + localItems).distinctBy { it.id }

        filterMediaItems(combined, filterState)
    }
}
