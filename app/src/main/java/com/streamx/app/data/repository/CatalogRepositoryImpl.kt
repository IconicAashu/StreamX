package com.streamx.app.data.repository

import com.streamx.app.data.local.MockCatalogDataSource
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.TmdbRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import com.streamx.app.utils.DurationParser
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider-independent Catalog Repository.
 * Powered by Stremio Add-on Catalogs, TMDB metadata discovery, and resilient local fallback items.
 * Completely independent of any proprietary third-party APIs.
 */
class CatalogRepositoryImpl(
    private val tmdbRepository: TmdbRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val manifestAddonRepository: ManifestAddonRepository? = null
) : CatalogRepository {

    private fun applyUserPreferences(items: List<MediaItem>): List<MediaItem> {
        val savedIds = userPreferencesRepository?.watchlistIdsFlow?.value ?: emptySet()
        val positions = userPreferencesRepository?.playbackPositionsFlow?.value ?: emptyMap()
        return items.map { item ->
            val isSaved = savedIds.contains(item.id)
            val savedPos = positions[item.id]
            if (savedPos != null && savedPos > 0L) {
                val totalMs = DurationParser.parseToMs(item.runtimeFormatted).takeIf { it > 0L } ?: 7200000L
                val progress = (savedPos.toFloat() / totalMs.toFloat()).coerceIn(0.05f, 0.95f)
                val remainingMinutes = ((totalMs - savedPos) / 60000L).coerceAtLeast(1L)
                item.copy(
                    isSaved = isSaved,
                    continueWatchingProgress = progress,
                    continueWatchingRemaining = "${remainingMinutes}m left"
                )
            } else {
                item.copy(isSaved = isSaved)
            }
        }
    }

    override suspend fun getHeroItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val addonHeroes = manifestAddonRepository?.addonCatalogsFlow?.value
            ?.flatMap { it.items }
            ?.take(5) ?: emptyList()
        val tmdbTrending = if (addonHeroes.isEmpty()) tmdbRepository.getTrendingToday().take(5) else emptyList()
        val localHeroes = if (addonHeroes.isEmpty() && tmdbTrending.isEmpty()) MockCatalogDataSource.items.take(3) else emptyList()
        val combined = (addonHeroes + tmdbTrending + localHeroes).distinctBy { it.title }.take(5)
        applyUserPreferences(combined)
    }

    override suspend fun getItemsByCategory(category: MediaCategory): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = when (category) {
            MediaCategory.MOVIES -> {
                val addonMovies = manifestAddonRepository?.addonCatalogsFlow?.value
                    ?.filter { it.type.equals("movie", ignoreCase = true) }
                    ?.flatMap { it.items } ?: emptyList()
                val tmdbMovies = tmdbRepository.getPopularMovies()
                val localMovies = MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.MOVIES) }
                (addonMovies + tmdbMovies + localMovies).distinctBy { it.title }
            }
            MediaCategory.SERIES, MediaCategory.TV_SHOW -> {
                val addonSeries = manifestAddonRepository?.addonCatalogsFlow?.value
                    ?.filter { it.type.equals("series", ignoreCase = true) }
                    ?.flatMap { it.items } ?: emptyList()
                val tmdbTv = tmdbRepository.getPopularTvShows()
                val localTv = MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.SERIES) }
                (addonSeries + tmdbTv + localTv).distinctBy { it.title }
            }
            MediaCategory.NEW, MediaCategory.TRENDING -> {
                val addonTrending = manifestAddonRepository?.addonCatalogsFlow?.value
                    ?.flatMap { it.items }?.take(15) ?: emptyList()
                val tmdbTrending = tmdbRepository.getTrendingToday()
                val localTrending = MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.TRENDING) }
                (addonTrending + tmdbTrending + localTrending).distinctBy { it.title }
            }
            MediaCategory.ANIME -> {
                val addonAnime = manifestAddonRepository?.addonCatalogsFlow?.value
                    ?.filter { it.type.equals("anime", ignoreCase = true) || it.title.contains("anime", ignoreCase = true) }
                    ?.flatMap { it.items } ?: emptyList()
                val localAnime = MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.ANIME) }
                (addonAnime + localAnime).distinctBy { it.title }
            }
            MediaCategory.BOLLYWOOD_SOUTH_INDIAN -> {
                MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.BOLLYWOOD_SOUTH_INDIAN) }
            }
            MediaCategory.FOR_YOU -> {
                val tmdbTop = tmdbRepository.getTopRatedMovies().take(4)
                val localForYou = MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.FOR_YOU) }
                (tmdbTop + localForYou).distinctBy { it.title }
            }
            MediaCategory.CONTINUE_WATCHING -> {
                getContinueWatching()
            }
        }
        applyUserPreferences(items)
    }

    override suspend fun getForYouItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        getItemsByCategory(MediaCategory.FOR_YOU)
    }

    override suspend fun getBollywoodAndSouthIndian(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.BOLLYWOOD_SOUTH_INDIAN) }
        applyUserPreferences(items)
    }

    override suspend fun getContinueWatching(): List<MediaItem> = withContext(Dispatchers.IO) {
        val positions = userPreferencesRepository?.playbackPositionsFlow?.value ?: emptyMap()
        val playedItems = positions.keys.mapNotNull { id ->
            MockCatalogDataSource.items.find { it.id == id }
                ?: manifestAddonRepository?.getMediaItemById(id)
        }
        val defaultList = MockCatalogDataSource.items.filter { it.continueWatchingProgress != null }
        val combined = (playedItems + defaultList).distinctBy { it.id }
        applyUserPreferences(combined)
    }

    override suspend fun getAwardWinners(): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbAward = tmdbRepository.getTopRatedMovies().take(8)
        val localAward = MockCatalogDataSource.items.filter { it.rating >= 8.5 }
        val combined = (tmdbAward + localAward).distinctBy { it.title }
        applyUserPreferences(combined)
    }

    override suspend fun getBingeWorthySeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbSeries = tmdbRepository.getTopRatedTvShows().take(8)
        val localSeries = MockCatalogDataSource.items.filter { it.format == com.streamx.app.domain.model.ContentFormat.SERIES }
        val combined = (tmdbSeries + localSeries).distinctBy { it.title }
        applyUserPreferences(combined)
    }

    override suspend fun getMediaDetails(id: String): MediaItem? = withContext(Dispatchers.IO) {
        StreamXLog.logMovieSelected(id, "getMediaDetails")

        val addonItem = manifestAddonRepository?.getMediaItemById(id)
        if (addonItem != null) {
            return@withContext applyUserPreferences(listOf(addonItem)).firstOrNull()
        }

        val item = if (id.startsWith("tmdb_")) {
            val numericId = id.removePrefix("tmdb_").toLongOrNull()
            if (numericId != null) {
                val live = tmdbRepository.getDetails(numericId, isTv = false)
                    ?: tmdbRepository.getDetails(numericId, isTv = true)
                if (live != null) live else MockCatalogDataSource.items.find { it.id == id }
            } else {
                MockCatalogDataSource.items.find { it.id == id }
            }
        } else {
            MockCatalogDataSource.items.find { it.id == id }
        }
        item?.let { applyUserPreferences(listOf(it)).firstOrNull() }
    }

    override suspend fun getSimilarTitles(id: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val current = getMediaDetails(id)
        val similar = MockCatalogDataSource.items.filter { it.id != id && (current == null || it.primaryGenre == current.primaryGenre || it.format == current.format) }
        applyUserPreferences(similar)
    }

    override suspend fun search(query: String, selectedGenre: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbResults = tmdbRepository.searchMulti(query)
        val addonMatches = manifestAddonRepository?.addonCatalogsFlow?.value
            ?.flatMap { it.items }
            ?.filter { it.title.contains(query, ignoreCase = true) || it.primaryGenre.contains(query, ignoreCase = true) }
            ?: emptyList()

        val localMatches = MockCatalogDataSource.items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                (item.originalTitle?.contains(query, ignoreCase = true) == true) ||
                item.genres.any { it.contains(query, ignoreCase = true) } ||
                item.cast.any { it.name.contains(query, ignoreCase = true) }
            val matchesGenre = selectedGenre.isNullOrBlank() || selectedGenre == "All" || item.genres.contains(selectedGenre)
            matchesQuery && matchesGenre
        }
        val combined = (addonMatches + tmdbResults + localMatches).distinctBy { it.id }
        val filtered = if (selectedGenre.isNullOrBlank() || selectedGenre == "All") {
            combined
        } else {
            combined.filter { it.genres.any { g -> g.contains(selectedGenre, ignoreCase = true) } }
        }
        applyUserPreferences(filtered)
    }

    override suspend fun discoverByGenre(genre: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val addonMatches = manifestAddonRepository?.addonCatalogsFlow?.value
            ?.flatMap { it.items }
            ?.filter { it.genres.any { g -> g.contains(genre, ignoreCase = true) } }
            ?: emptyList()

        if (addonMatches.isNotEmpty()) {
            return@withContext applyUserPreferences(addonMatches)
        }

        val items = tmdbRepository.discoverByGenre(genre)
        applyUserPreferences(items)
    }

    override suspend fun getPopularGenres(): List<String> = withContext(Dispatchers.IO) {
        MockCatalogDataSource.genres
    }

    override suspend fun getAllPostersForWelcome(): List<String> = withContext(Dispatchers.IO) {
        val trending = tmdbRepository.getTrendingToday().map { it.posterUrl }
        if (trending.isNotEmpty()) {
            trending
        } else {
            MockCatalogDataSource.items.map { it.posterUrl }
        }
    }
}
