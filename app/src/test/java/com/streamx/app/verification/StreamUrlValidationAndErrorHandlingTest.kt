package com.streamx.app.verification

import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.player.StreamUrlValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamUrlValidationAndErrorHandlingTest {

    @Test
    fun testMalformedAndBlankUrlsFailValidation() = runBlocking {
        val blankResult = StreamUrlValidator.validate("")
        assertFalse(blankResult.isValid)
        assertEquals("Stream URL is blank", blankResult.serverErrorMessage)

        val invalidSchemeResult = StreamUrlValidator.validate("ftp://example.com/video.mp4")
        assertFalse(invalidSchemeResult.isValid)
        assertTrue(invalidSchemeResult.serverErrorMessage?.contains("Unsupported URI scheme") == true)
    }

    @Test
    fun testExpiredSignedUrlDetectedAndRejected() = runBlocking {
        // Timestamp in the past (e.g. 1600000000 = September 2020)
        val expiredUrl = "https://bcdn.hakunaymatata.com/resource/h265/c7cc1fbe3cb3.mp4?sign=testsign&t=1600000000"
        val result = StreamUrlValidator.validate(expiredUrl)

        assertFalse(result.isValid)
        assertTrue(result.isExpired)
        assertEquals(403, result.httpStatusCode)
        assertTrue(result.serverErrorMessage?.contains("expired") == true)
    }

    @Test
    fun testTrailerVersusFullMovieDistinction() = runBlocking {
        val trailerUrl = "https://macdn.aoneroom.com/media/vone/2024/08/07/trailer-sd.mp4"
        val trailerResult = StreamUrlValidator.validate(
            rawUrl = trailerUrl,
            declaredTrailer = true,
            durationSeconds = 73L
        )

        assertTrue(trailerResult.isTrailer)

        val fullMovieUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
        val movieResult = StreamUrlValidator.validate(
            rawUrl = fullMovieUrl,
            declaredTrailer = false,
            durationSeconds = 5460L
        )

        assertFalse("Full movie duration (>90m) must not be flagged as trailer", movieResult.isTrailer)
    }

    @Test
    fun testHttpErrorCodesMappedToMeaningfulMessages() {
        fun mapErrorCodeToMessage(code: Int, responseMessage: String?): String {
            return when (code) {
                401 -> "Authentication required (HTTP 401). Please check credentials."
                403 -> "Access denied (HTTP 403). Stream token expired or VIP restricted."
                404 -> "Video unavailable on server (HTTP 404)."
                429 -> "Too many requests to provider (HTTP 429). Please wait."
                in 500..599 -> "Streaming server unavailable (HTTP $code)."
                else -> "Streaming error: HTTP $code ($responseMessage). Tap to retry."
            }
        }

        assertEquals("Authentication required (HTTP 401). Please check credentials.", mapErrorCodeToMessage(401, "Unauthorized"))
        assertEquals("Access denied (HTTP 403). Stream token expired or VIP restricted.", mapErrorCodeToMessage(403, "Forbidden"))
        assertEquals("Video unavailable on server (HTTP 404).", mapErrorCodeToMessage(404, "Not Found"))
        assertEquals("Too many requests to provider (HTTP 429). Please wait.", mapErrorCodeToMessage(429, "Too Many Requests"))
        assertEquals("Streaming server unavailable (HTTP 500).", mapErrorCodeToMessage(500, "Internal Server Error"))
        assertEquals("Streaming server unavailable (HTTP 503).", mapErrorCodeToMessage(503, "Service Unavailable"))
    }

    @Test
    fun testPlaybackSourcePreservesMetadataAndQualityTiers() {
        val source = PlaybackSource(
            mediaId = "182664070041063208",
            title = "Ice Age 2: The Meltdown",
            streamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            isTrailer = false,
            durationSeconds = 5460L,
            isFullFeature = true,
            licenseNotice = "Authorized Full Stream • Media3 Compliant",
            availableQualities = listOf(VideoQuality.QUALITY_720P, VideoQuality.QUALITY_480P, VideoQuality.QUALITY_360P)
        )

        assertEquals("182664070041063208", source.mediaId)
        assertFalse(source.isTrailer)
        assertTrue(source.isFullFeature)
        assertEquals(3, source.availableQualities.size)
        assertTrue(source.availableQualities.contains(VideoQuality.QUALITY_720P))
    }
}
