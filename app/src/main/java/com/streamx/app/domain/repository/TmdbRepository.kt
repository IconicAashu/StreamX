package com.streamx.app.domain.repository

import com.streamx.app.domain.model.MediaItem

interface TmdbRepository {
    suspend fun getTrendingToday(): List<MediaItem>
    suspend fun getPopularMovies(): List<MediaItem>
    suspend fun getTopRatedMovies(): List<MediaItem>
    suspend fun getPopularTvShows(): List<MediaItem>
    suspend fun getTopRatedTvShows(): List<MediaItem>
    suspend fun discoverByGenre(genreName: String): List<MediaItem>
    suspend fun searchMulti(query: String): List<MediaItem>
    suspend fun getDetails(id: Long, isTv: Boolean): MediaItem?
}
