package com.streamx.app.domain.model

enum class ContentFormat(val displayName: String) {
    MOVIE("Movie"),
    SERIES("Series"),
    ANIME("Anime")
}

enum class MediaCategory(val title: String) {
    TRENDING("Trending"),
    NEW("New"),
    MOVIES("Movies"),
    SERIES("Series"),
    TV_SHOW("TV Show"),
    ANIME("Anime"),
    FOR_YOU("For You"),
    BOLLYWOOD_SOUTH_INDIAN("Bollywood & South Indian"),
    CONTINUE_WATCHING("Continue Watching")
}

data class CastMember(
    val id: String,
    val name: String,
    val characterName: String,
    val profileUrl: String
)

data class MediaItem(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String,
    val backdropUrl: String,
    val releaseYear: String,
    val releaseDateFormatted: String,
    val format: ContentFormat,
    val primaryGenre: String,
    val genres: List<String>,
    val country: String = "United States",
    val runtimeFormatted: String,
    val maturityRating: String,
    val rating: Double,
    val ratingVotesCount: String,
    val likesCount: String,
    val synopsis: String,
    val cast: List<CastMember>,
    val audioLanguages: List<String>,
    val subtitleLanguages: List<String>,
    val categories: List<MediaCategory>,
    val isSaved: Boolean = false,
    val streamUrl: String = "",
    val originalId: String? = null,
    val continueWatchingProgress: Float? = null,
    val continueWatchingRemaining: String? = null
) {
    val durationMs: Long
        get() = com.streamx.app.utils.DurationParser.parseToMs(runtimeFormatted)

    val durationSeconds: Long
        get() = com.streamx.app.utils.DurationParser.parseToSeconds(runtimeFormatted)
}
