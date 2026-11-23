package com.streamx.app.network.api

import com.streamx.app.network.model.TmdbGenreListResponse
import com.streamx.app.network.model.TmdbMediaDto
import com.streamx.app.network.model.TmdbPaginatedResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("trending/{media_type}/{time_window}")
    suspend fun getTrending(
        @Path("media_type") mediaType: String = "all",
        @Path("time_window") timeWindow: String = "day",
        @Query("api_key") apiKey: String = "",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String = "",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String = "",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("api_key") apiKey: String = "",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("api_key") apiKey: String = "",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String = "",
        @Query("with_genres") genreId: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("search/multi")
    suspend fun multiSearch(
        @Query("api_key") apiKey: String = "",
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPaginatedResponse<TmdbMediaDto>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String = "",
        @Query("append_to_response") appendToResponse: String = "credits,similar,external_ids"
    ): TmdbMediaDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String = "",
        @Query("append_to_response") appendToResponse: String = "credits,similar,external_ids"
    ): TmdbMediaDto

    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("api_key") apiKey: String = ""
    ): TmdbGenreListResponse
}
