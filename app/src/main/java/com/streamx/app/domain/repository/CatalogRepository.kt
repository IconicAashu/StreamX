package com.streamx.app.domain.repository

import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem

/**
 * Metadata & Catalog repository.
 * Dedicated strictly to content discovery, metadata aggregation, search, and details.
 * Connects to live TMDB API with graceful offline/fallback capabilities.
 */
interface CatalogRepository {
    suspend fun getHeroItems(): List<MediaItem>
    suspend fun getItemsByCategory(category: MediaCategory): List<MediaItem>
    suspend fun getForYouItems(): List<MediaItem>
    suspend fun getBollywoodAndSouthIndian(): List<MediaItem>
    suspend fun getContinueWatching(): List<MediaItem>
    suspend fun getAwardWinners(): List<MediaItem>
    suspend fun getBingeWorthySeries(): List<MediaItem>
    suspend fun getMediaDetails(id: String): MediaItem?
    suspend fun getSimilarTitles(id: String): List<MediaItem>
    suspend fun search(query: String, selectedGenre: String?): List<MediaItem>
    suspend fun discoverByGenre(genre: String): List<MediaItem>
    suspend fun getPopularGenres(): List<String>
    suspend fun getAllPostersForWelcome(): List<String>
}
