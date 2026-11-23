package com.streamx.app.data.mapper

import com.streamx.app.domain.model.CastMember
import com.streamx.app.domain.model.ContentFormat
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.network.model.TmdbCastDto
import com.streamx.app.network.model.TmdbMediaDto
import java.util.Locale

object TmdbMapper {

    private val genreIdToName = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10765 to "Sci-Fi & Fantasy"
    )

    fun genreNameToId(name: String): Int? {
        return when (name.lowercase(Locale.ROOT)) {
            "action" -> 28
            "adventure" -> 12
            "animation", "anime" -> 16
            "comedy" -> 35
            "crime" -> 80
            "documentary" -> 99
            "drama" -> 18
            "fantasy" -> 14
            "horror" -> 27
            "romance" -> 10749
            "sci-fi" -> 878
            "thriller" -> 53
            else -> null
        }
    }

    fun toDomain(dto: TmdbMediaDto): MediaItem {
        val title = dto.title ?: dto.name ?: dto.originalTitle ?: dto.originalName ?: "STREAMX Title"
        val isTv = dto.mediaType == "tv" || dto.firstAirDate != null
        val format = if (isTv) ContentFormat.SERIES else ContentFormat.MOVIE
        val dateString = dto.releaseDate ?: dto.firstAirDate ?: "2024-01-01"
        val releaseYear = if (dateString.length >= 4) dateString.substring(0, 4) else "2024"

        val mappedGenres = dto.genreIds?.mapNotNull { genreIdToName[it] }?.ifEmpty {
            listOf(if (isTv) "Series" else "Feature")
        } ?: listOf("Action", "Drama")

        val primaryGenre = mappedGenres.firstOrNull() ?: "Drama"

        val posterUrl = if (!dto.posterPath.isNullOrBlank()) {
            "https://image.tmdb.org/t/p/w500${dto.posterPath}"
        } else {
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=800&auto=format&fit=crop"
        }

        val backdropUrl = if (!dto.backdropPath.isNullOrBlank()) {
            "https://image.tmdb.org/t/p/w1280${dto.backdropPath}"
        } else {
            posterUrl
        }

        val rawRating = dto.voteAverage ?: 7.5
        val rating = String.format(Locale.US, "%.1f", rawRating).toDoubleOrNull() ?: 7.5
        val voteCount = dto.voteCount ?: 1240
        val ratingVotesCount = if (voteCount >= 1000) "${voteCount / 1000}k votes" else "$voteCount votes"
        val likesCount = if (voteCount > 500) "${(voteCount * 0.75).toInt()}" else "1,240"

        val castList = dto.credits?.cast?.take(6)?.map { castDto ->
            CastMember(
                id = castDto.id.toString(),
                name = castDto.name,
                characterName = castDto.character ?: "Cast",
                profileUrl = if (!castDto.profilePath.isNullOrBlank()) {
                    "https://image.tmdb.org/t/p/w185${castDto.profilePath}"
                } else {
                    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=300&auto=format&fit=crop"
                }
            )
        } ?: listOf(
            CastMember("c_def_1", "Main Lead", "Protagonist", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=300&auto=format&fit=crop"),
            CastMember("c_def_2", "Supporting Lead", "Deuteragonist", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=300&auto=format&fit=crop")
        )

        val originCode = dto.originCountry?.firstOrNull()?.uppercase(Locale.ROOT)
        val country = when (originCode) {
            "US" -> "United States"
            "GB", "UK" -> "United Kingdom"
            "KR" -> "Korea"
            "JP" -> "Japan"
            "IN" -> "India"
            "FR" -> "France"
            "DE" -> "Germany"
            "ES" -> "Spain"
            "CN" -> "China"
            "IT" -> "Italy"
            "MX" -> "Mexico"
            "RU" -> "Russia"
            "TR" -> "Turkey"
            "TH" -> "Thailand"
            "ID" -> "Indonesia"
            else -> when (dto.originalLanguage?.lowercase(Locale.ROOT)) {
                "en" -> "United States"
                "ko" -> "Korea"
                "ja" -> "Japan"
                "hi", "te", "ta", "ml" -> "India"
                "fr" -> "France"
                "es" -> "Spain"
                "de" -> "Germany"
                "zh" -> "China"
                "it" -> "Italy"
                "ru" -> "Russia"
                else -> "United States"
            }
        }

        return MediaItem(
            id = "tmdb_${dto.id}",
            title = title,
            originalTitle = dto.originalTitle ?: dto.originalName,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            releaseDateFormatted = dateString,
            format = format,
            primaryGenre = primaryGenre,
            genres = mappedGenres,
            country = country,
            runtimeFormatted = if (isTv) "Season 1" else "2h 14min",
            maturityRating = if (rawRating > 8.0) "16+" else "13+",
            rating = rating,
            ratingVotesCount = ratingVotesCount,
            likesCount = likesCount,
            synopsis = dto.overview?.ifBlank { "An epic cinematic journey premiering on STREAMX." }
                ?: "An extraordinary story of adventure, destiny, and survival on STREAMX.",
            cast = castList,
            audioLanguages = listOf("Original (Atmos)", "Hindi Dub", "English (5.1)", "Spanish Dub"),
            subtitleLanguages = listOf("English [CC]", "Hindi", "Spanish", "Japanese"),
            categories = listOf(MediaCategory.TRENDING, MediaCategory.FOR_YOU),
            streamUrl = "",
            originalId = dto.imdbId ?: dto.externalIds?.imdbId
        )
    }
}
