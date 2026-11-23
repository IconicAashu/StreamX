package com.streamx.app.network.api

import com.streamx.app.network.model.JikanSearchResponse
import com.streamx.app.network.model.TmdbSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CatalogApiService {

    @GET("3/trending/all/day")
    suspend fun getTmdbTrending(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/search/multi")
    suspend fun searchTmdb(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
}

interface JikanAnimeService {

    @GET("v4/top/anime")
    suspend fun getTopAnime(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): JikanSearchResponse

    @GET("v4/anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): JikanSearchResponse
}
