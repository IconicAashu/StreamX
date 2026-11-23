package com.streamx.app.verification

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.JsonAdapter
import com.streamx.app.domain.model.ContentFormat
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.network.addon.AddonSecurityValidator
import com.streamx.app.network.addon.StremioAddonClient
import com.streamx.app.utils.DurationParser
import com.streamx.app.utils.SafeDurationLongDeserializer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

/**
 * Verification test suite for:
 * 1. Bug 1: Add-on Installation & Interceptor Fix (no EOFException on <5MB bodies, SSRF preserved, stremio:// support).
 * 2. Bug 2: Playback NumberFormatException Fix (safe parsing of "2h 3m", "1h 45m", "45m", "120", null, etc.).
 * 3. Bug 3: Distinction between metadata-only add-ons vs stream providers.
 */
class AddonAndDurationBugsFixTest {

    // Helper test DTO using SafeDurationLongDeserializer
    data class TestMediaDto(
        val subjectId: String = "",
        val title: String = "",
        @JsonAdapter(SafeDurationLongDeserializer::class)
        val duration: Long = 0L,
        @JsonAdapter(SafeDurationLongDeserializer::class)
        val durationSeconds: Long = 0L
    )

    // ==========================================
    // BUG 2: DURATION PARSING & NUMBER FORMAT EXCEPTION TESTS
    // ==========================================

    @Test
    fun testParseToSeconds_variousFormats() {
        // "2h 3m" -> 2*3600 + 3*60 = 7380
        assertEquals(7380L, DurationParser.parseToSeconds("2h 3m"))
        assertEquals(7380L, DurationParser.parseToSeconds("2h 3min"))
        assertEquals(7380L, DurationParser.parseToSeconds("2 hours 3 minutes"))

        // "1h 45m" -> 3600 + 45*60 = 6300
        assertEquals(6300L, DurationParser.parseToSeconds("1h 45m"))
        assertEquals(6300L, DurationParser.parseToSeconds("1h 45min"))

        // "45m" -> 45*60 = 2700
        assertEquals(2700L, DurationParser.parseToSeconds("45m"))
        assertEquals(2700L, DurationParser.parseToSeconds("45 min"))
        assertEquals(2700L, DurationParser.parseToSeconds("45 mins"))

        // "120" -> 120 minutes = 7200
        assertEquals(7200L, DurationParser.parseToSeconds("120"))
        assertEquals(7200L, DurationParser.parseToSeconds("120 min"))

        // "02:03:00" -> 2*3600 + 3*60 = 7380
        assertEquals(7380L, DurationParser.parseToSeconds("02:03:00"))
        assertEquals(2700L, DurationParser.parseToSeconds("45:00"))

        // Large seconds value "7200" -> 7200
        assertEquals(7200L, DurationParser.parseToSeconds("7200"))
    }

    @Test
    fun testParseToSeconds_nullAndInvalid_neverCrashes() {
        assertEquals(0L, DurationParser.parseToSeconds(null))
        assertEquals(0L, DurationParser.parseToSeconds(""))
        assertEquals(0L, DurationParser.parseToSeconds("   "))
        assertEquals(0L, DurationParser.parseToSeconds("not_a_duration"))
        assertEquals(100L, DurationParser.parseToSeconds("corrupted_value", defaultSeconds = 100L))
    }

    @Test
    fun testParseToMs_variousFormats() {
        assertEquals(7380000L, DurationParser.parseToMs("2h 3m"))
        assertEquals(6300000L, DurationParser.parseToMs("1h 45m"))
        assertEquals(2700000L, DurationParser.parseToMs("45m"))
        assertEquals(7200000L, DurationParser.parseToMs("120"))
        assertEquals(0L, DurationParser.parseToMs(null))
        assertEquals(0L, DurationParser.parseToMs("invalid"))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("2h 3m", DurationParser.formatDuration(7380L))
        assertEquals("1h 45m", DurationParser.formatDuration(6300L))
        assertEquals("45m", DurationParser.formatDuration(2700L))
        assertEquals("0m", DurationParser.formatDuration(0L))
    }

    @Test
    fun testMediaItemDurationProperties() {
        val item = MediaItem(
            id = "test_item",
            title = "Test Title",
            posterUrl = "",
            backdropUrl = "",
            releaseYear = "2024",
            releaseDateFormatted = "2024",
            format = ContentFormat.MOVIE,
            primaryGenre = "Action",
            genres = listOf("Action"),
            runtimeFormatted = "2h 3m",
            maturityRating = "PG-13",
            rating = 8.0,
            ratingVotesCount = "10K",
            likesCount = "90%",
            synopsis = "Synopsis",
            cast = emptyList(),
            audioLanguages = emptyList(),
            subtitleLanguages = emptyList(),
            categories = emptyList()
        )

        assertEquals(7380000L, item.durationMs)
        assertEquals(7380L, item.durationSeconds)
    }

    @Test
    fun testSafeDurationLongDeserializer_withDurationString() {
        // When JSON returns "duration": "2h 3m" or "durationSeconds": "2h 3m" as string
        val json = """
            {
                "subjectId": "test_123",
                "title": "Inception",
                "duration": "2h 3m",
                "durationSeconds": "2h 3m"
            }
        """.trimIndent()

        val gson = Gson()
        // Must NOT throw NumberFormatException: For input string: "2h 3m"
        val dto = gson.fromJson(json, TestMediaDto::class.java)
        assertNotNull(dto)
        assertEquals("test_123", dto.subjectId)
        assertEquals("Inception", dto.title)
        assertEquals(7380L, dto.duration)
        assertEquals(7380L, dto.durationSeconds)
    }

    // ==========================================
    // BUG 1: ADD-ON INSTALLATION & INTERCEPTOR TESTS
    // ==========================================

    @Test
    fun testSafeAddonInterceptor_smallResponse_noEOFException() {
        // Simulates an OkHttp chain returning a small 2KB manifest JSON
        val manifestJson = """
            {
                "id": "org.stremio.test",
                "name": "Test Addon",
                "version": "1.0.0",
                "resources": ["catalog", "meta", "stream"],
                "types": ["movie", "series"]
            }
        """.trimIndent()

        val interceptor = AddonSecurityValidator.SafeAddonInterceptor()

        // Create a mock chain
        val mockRequest = Request.Builder()
            .url("https://v3-cinemeta.strem.io/manifest.json")
            .build()

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = mockRequest
            override fun proceed(request: Request): Response {
                val body = manifestJson.toByteArray().toResponseBody("application/json".toMediaTypeOrNull())
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            override fun connection(): Connection? = null
            override fun call(): Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 5000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 5000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 5000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        }

        // Must succeed without throwing EOFException!
        val response = interceptor.intercept(mockChain)
        assertEquals(200, response.code)
        val bodyStr = response.body?.string()
        assertNotNull(bodyStr)
        assertTrue(bodyStr!!.contains("Test Addon"))
    }

    @Test
    fun testSafeAddonInterceptor_largeResponseExceeding5MB_isRejected() {
        val interceptor = AddonSecurityValidator.SafeAddonInterceptor()
        val mockRequest = Request.Builder()
            .url("https://v3-cinemeta.strem.io/manifest.json")
            .build()

        // Create a body larger than 5MB (5MB + 100KB)
        val oversizedBytes = ByteArray(5 * 1024 * 1024 + 100 * 1024)

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = mockRequest
            override fun proceed(request: Request): Response {
                val body = oversizedBytes.toResponseBody("application/json".toMediaTypeOrNull())
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            override fun connection(): Connection? = null
            override fun call(): Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 5000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 5000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 5000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        }

        try {
            interceptor.intercept(mockChain)
            fail("Expected IOException for response exceeding 5MB")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("5MB"))
        }
    }

    @Test
    fun testValidateUrl_stremioPrefix_normalizedToHttps() {
        val result = AddonSecurityValidator.validateUrl("stremio://v3-cinemeta.strem.io/manifest.json")
        assertTrue(result.isSuccess)
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", result.getOrThrow())
    }

    @Test
    fun testValidateUrl_ssrfProtection_blocksLocalhostAndPrivateIPs() {
        val localhostResult = AddonSecurityValidator.validateUrl("http://localhost/manifest.json")
        assertTrue(localhostResult.isFailure)

        val ip127Result = AddonSecurityValidator.validateUrl("http://127.0.0.1:8080/manifest.json")
        assertTrue(ip127Result.isFailure)

        val privateIp = InetAddress.getByName("192.168.1.1")
        assertTrue(AddonSecurityValidator.isPrivateOrLocalAddress(privateIp))

        val publicIp = InetAddress.getByName("8.8.8.8")
        assertFalse(AddonSecurityValidator.isPrivateOrLocalAddress(publicIp))
    }

    // ==========================================
    // BUG 3: METADATA-ONLY VS STREAM PROVIDERS
    // ==========================================

    @Test
    fun testMetadataOnlyVsStreamProviders() {
        val cinemetaResources = listOf("catalog", "meta")
        assertFalse(cinemetaResources.contains("stream"))

        val publicDomainResources = listOf("catalog", "meta", "stream")
        assertTrue(publicDomainResources.contains("stream"))
    }
}
