package com.streamx.app.network.model

import com.google.gson.annotations.SerializedName

data class TmdbPaginatedResponse<T>(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<T>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

data class TmdbMediaDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
    @SerializedName("origin_country") val originCountry: List<String>? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("credits") val credits: TmdbCreditsDto?,
    @SerializedName("similar") val similar: TmdbPaginatedResponse<TmdbMediaDto>?,
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("external_ids") val externalIds: TmdbExternalIdsDto? = null
)

data class TmdbExternalIdsDto(
    @SerializedName("imdb_id") val imdbId: String? = null
)

data class TmdbCreditsDto(
    @SerializedName("cast") val cast: List<TmdbCastDto>?
)

data class TmdbCastDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("character") val character: String?,
    @SerializedName("profile_path") val profilePath: String?
)

data class TmdbGenreListResponse(
    @SerializedName("genres") val genres: List<TmdbGenreDto>
)

data class TmdbGenreDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)
