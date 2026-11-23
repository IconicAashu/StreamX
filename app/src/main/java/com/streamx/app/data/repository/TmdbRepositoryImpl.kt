package com.streamx.app.data.repository

import com.streamx.app.data.local.MockCatalogDataSource
import com.streamx.app.data.mapper.TmdbMapper
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.repository.TmdbRepository
import com.streamx.app.network.api.TmdbApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmdbRepositoryImpl(
    private val tmdbApiService: TmdbApiService,
    private val apiKey: String = "" // Optional TMDB v3 API Key; fallbacks protect against missing/invalid keys
) : TmdbRepository {

    override suspend fun getTrendingToday(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNotBlank()) {
                val response = tmdbApiService.getTrending("all", "day", apiKey)
                if (response.results.isNotEmpty()) {
                    return@withContext response.results.map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to rich local Phase 1 catalog
        }
        MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.TRENDING) }
    }

    override suspend fun getPopularMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNotBlank()) {
                val response = tmdbApiService.getPopularMovies(apiKey)
                if (response.results.isNotEmpty()) {
                    return@withContext response.results.map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
        MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.MOVIES) }
    }

    override suspend fun getTopRatedMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNotBlank()) {
                val response = tmdbApiService.getTopRatedMovies(apiKey)
                if (response.results.isNotEmpty()) {
                    return@withContext response.results.map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
        MockCatalogDataSource.items.sortedByDescending { it.rating }
    }

    override suspend fun getPopularTvShows(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNotBlank()) {
                val response = tmdbApiService.getPopularTv(apiKey)
                if (response.results.isNotEmpty()) {
                    return@withContext response.results.map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
        MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.SERIES) }
    }

    override suspend fun getTopRatedTvShows(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNotBlank()) {
                val response = tmdbApiService.getTopRatedTv(apiKey)
                if (response.results.isNotEmpty()) {
                    return@withContext response.results.map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
        MockCatalogDataSource.items.filter { it.categories.contains(MediaCategory.SERIES) }
    }

    override suspend fun discoverByGenre(genreName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val genreId = TmdbMapper.genreNameToId(genreName)
            if (apiKey.isNotBlank() && genreId != null) {
                val response = tmdbApiService.discoverMovies(
                    apiKey = apiKey,
                    genreId = genreId.toString()
                )
                if (response.results.isNotEmpty()) {
                    return@withContext response.results.map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
        if (genreName.equals("All", ignoreCase = true)) {
            MockCatalogDataSource.items
        } else {
            MockCatalogDataSource.items.filter { it.genres.any { g -> g.contains(genreName, ignoreCase = true) } }
        }
    }

    override suspend fun searchMulti(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            if (apiKey.isNotBlank()) {
                val response = tmdbApiService.multiSearch(apiKey = apiKey, query = query)
                if (response.results.isNotEmpty()) {
                    return@withContext response.results
                        .filter { it.posterPath != null || it.backdropPath != null }
                        .map { TmdbMapper.toDomain(it) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
        MockCatalogDataSource.items.filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                (item.originalTitle?.contains(query, ignoreCase = true) == true) ||
                item.genres.any { it.contains(query, ignoreCase = true) } ||
                item.cast.any { it.name.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun getDetails(id: Long, isTv: Boolean): MediaItem? = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNotBlank()) {
                val dto = if (isTv) {
                    tmdbApiService.getTvDetails(id, apiKey)
                } else {
                    tmdbApiService.getMovieDetails(id, apiKey)
                }
                return@withContext TmdbMapper.toDomain(dto)
            }
        } catch (_: Exception) {
            // Fallback
        }
        MockCatalogDataSource.items.find { it.id == "tmdb_$id" || it.id == "m$id" }
    }
}
