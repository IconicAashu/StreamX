package com.streamx.app.network.model

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<TmdbMovieDto>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

data class TmdbMovieDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("genre_ids") val genreIds: List<Int>?
)

data class JikanSearchResponse(
    @SerializedName("data") val data: List<JikanAnimeDto>
)

data class JikanAnimeDto(
    @SerializedName("mal_id") val malId: Long,
    @SerializedName("title") val title: String,
    @SerializedName("synopsis") val synopsis: String?,
    @SerializedName("score") val score: Double?,
    @SerializedName("scored_by") val scoredBy: Int?,
    @SerializedName("images") val images: JikanImagesDto?,
    @SerializedName("year") val year: Int?,
    @SerializedName("genres") val genres: List<JikanGenreDto>?
)

data class JikanImagesDto(
    @SerializedName("jpg") val jpg: JikanImageFormatDto?,
    @SerializedName("webp") val webp: JikanImageFormatDto?
)

data class JikanImageFormatDto(
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("large_image_url") val largeImageUrl: String?
)

data class JikanGenreDto(
    @SerializedName("mal_id") val malId: Int,
    @SerializedName("name") val name: String
)
