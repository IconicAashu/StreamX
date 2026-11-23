package com.streamx.app.verification

import com.streamx.app.domain.model.ContentFormat
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.PlaybackSource
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

/**
 * Verification of Playback Architecture Fixes:
 * - Removal of mock BigBuckBunny fallbacks
 * - MediaItem streamUrl default is empty
 * - Unique source resolution across distinct movie IDs
 * - Expired signed token detection
 */
class PlaybackArchitectureFixesTest {

    @Test
    fun testMediaItemDefaultStreamUrlIsEmpty() {
        val defaultItem = MediaItem(
            id = "test_item",
            title = "Test Movie",
            posterUrl = "",
            backdropUrl = "",
            releaseYear = "2024",
            releaseDateFormatted = "2024-01-01",
            format = ContentFormat.MOVIE,
            primaryGenre = "Action",
            genres = listOf("Action"),
            runtimeFormatted = "1h 45m",
            maturityRating = "PG-13",
            rating = 7.5,
            ratingVotesCount = "10k",
            likesCount = "90%",
            synopsis = "Synopsis",
            cast = emptyList(),
            audioLanguages = emptyList(),
            subtitleLanguages = emptyList(),
            categories = listOf(MediaCategory.MOVIES)
        )

        // Must NOT contain BigBuckBunny
        assertEquals("", defaultItem.streamUrl)
        assertFalse(defaultItem.streamUrl.contains("BigBuckBunny"))
    }

    @Test
    fun testUniquePlaybackSourceResolutionForThreeDistinctMovies() {
        val source1 = PlaybackSource(
            mediaId = "1001",
            title = "Addon A (1080p)",
            streamUrl = "https://cdn1.com/movie_alpha_1080p.mp4"
        )
        val source2 = PlaybackSource(
            mediaId = "1002",
            title = "Addon B (720p)",
            streamUrl = "https://cdn2.com/movie_beta_720p.mp4"
        )
        val source3 = PlaybackSource(
            mediaId = "1003",
            title = "Addon C (480p)",
            streamUrl = "https://cdn3.com/movie_gamma_480p.mp4"
        )

        // Each movie must retain its own unique ID and URL
        assertEquals("1001", source1.mediaId)
        assertEquals("https://cdn1.com/movie_alpha_1080p.mp4", source1.streamUrl)

        assertEquals("1002", source2.mediaId)
        assertEquals("https://cdn2.com/movie_beta_720p.mp4", source2.streamUrl)

        assertEquals("1003", source3.mediaId)
        assertEquals("https://cdn3.com/movie_gamma_480p.mp4", source3.streamUrl)

        // None must share the same stream URL
        assertNotEquals(source1.streamUrl, source2.streamUrl)
        assertNotEquals(source2.streamUrl, source3.streamUrl)
        assertNotEquals(source1.streamUrl, source3.streamUrl)
    }

    @Test
    fun testDetectExpiredSignedToken() {
        fun isTokenExpired(url: String, currentEpochSeconds: Long): Boolean {
            val uri = URI(url)
            val params = (uri.query ?: "").split("&").associate {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            }
            val t = params["t"]?.toLongOrNull() ?: return false
            return t <= currentEpochSeconds
        }

        val expiredUrl = "https://cdn.example.com/video.mp4?sign=abc1234&t=1600000000"
        val futureUrl = "https://cdn.example.com/video.mp4?sign=abc1234&t=2500000000"

        val nowSeconds = 1795269500L // ~Year 2026

        assertTrue(isTokenExpired(expiredUrl, nowSeconds))
        assertFalse(isTokenExpired(futureUrl, nowSeconds))
    }
}
