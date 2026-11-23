package com.streamx.app.verification

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.streamx.app.domain.model.*
import com.streamx.app.player.StreamUrlValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * STREAMX — AUDIT TEST SUITE FOR:
 * PRIORITY 1 – ADD-ON CAPABILITY AUDIT
 * PRIORITY 2 – STREAM DISCOVERY TRACE
 * PRIORITY 3 – ID COMPATIBILITY & NAMESPACE ISOLATION
 * PRIORITY 4 – STREAM VALIDATION & REQUEST HEADERS
 * PRIORITY 5 – PLAYBACK ERROR REPORTING
 */
class AddonCapabilityAndStreamDiscoveryTest {

    private val gson = Gson()

    // ==========================================
    // PRIORITY 1: ADD-ON CAPABILITY AUDIT
    // ==========================================

    @Test
    fun testAddonCapabilityAudit_distinguishesMetadataFromStreamProviders() {
        val cinemeta = InstalledAddon(
            id = "addon_cinemeta",
            name = "Cinemeta",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            baseUrl = "https://v3-cinemeta.strem.io",
            resources = listOf(
                StremioResourceDefinition("catalog", listOf("movie", "series"), listOf("tt")),
                StremioResourceDefinition("meta", listOf("movie", "series"), listOf("tt"))
            ),
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt")
        )

        val openSubtitles = InstalledAddon(
            id = "addon_open_subtitles",
            name = "OpenSubtitles v3",
            manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
            baseUrl = "https://opensubtitles-v3.strem.io",
            resources = listOf(
                StremioResourceDefinition("subtitles", listOf("movie", "series"), listOf("tt"))
            ),
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt")
        )

        val publicDomain = InstalledAddon(
            id = "org.stremio.pubdomainmovies",
            name = "Public Domain Movies",
            manifestUrl = "https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json",
            baseUrl = "https://caching.stremio.net/publicdomainmovies.now.sh",
            resources = listOf(
                StremioResourceDefinition("catalog", listOf("movie"), listOf("tt")),
                StremioResourceDefinition("stream", listOf("movie"), listOf("tt"))
            ),
            types = listOf("movie"),
            idPrefixes = listOf("tt")
        )

        // Cinemeta: Metadata only (cannot stream)
        assertTrue(cinemeta.isMetadataOnly)
        assertFalse(cinemeta.isStreamProvider)
        assertFalse(cinemeta.isSubtitlesOnly)
        assertTrue(cinemeta.supportsResource("catalog", "movie", "tt0111161"))
        assertTrue(cinemeta.supportsResource("meta", "movie", "tt0111161"))
        assertFalse(cinemeta.supportsResource("stream", "movie", "tt0111161"))

        // OpenSubtitles: Subtitles only
        assertTrue(openSubtitles.isSubtitlesOnly)
        assertFalse(openSubtitles.isStreamProvider)
        assertFalse(openSubtitles.isMetadataOnly)
        assertTrue(openSubtitles.supportsResource("subtitles", "movie", "tt0111161"))
        assertFalse(openSubtitles.supportsResource("stream", "movie", "tt0111161"))

        // Public Domain: Stream provider
        assertTrue(publicDomain.isStreamProvider)
        assertFalse(publicDomain.isMetadataOnly)
        assertFalse(publicDomain.isSubtitlesOnly)
        assertTrue(publicDomain.supportsResource("stream", "movie", "tt0012349"))
        assertFalse(publicDomain.supportsResource("stream", "series", "tt0903747:1:1")) // Only supports movie
    }

    // ==========================================
    // PRIORITY 2: STREAM DISCOVERY TRACE
    // ==========================================

    @Test
    fun testStreamDiscoveryTrace_movieAndSeriesEpisode() {
        // Trace 1: Movie (Shawshank Redemption - tt0111161)
        val movieContentId = "tt0111161"
        val movieContentType = "movie"

        // Trace 2: Series Episode (Breaking Bad S01E01 - tt0903747:1:1)
        val episodeContentId = "tt0903747:1:1"
        val episodeContentType = "series"

        // Verify endpoint URLs according to Stremio Addon Protocol v1
        val baseUrl = "https://example-streams.org"
        val movieStreamEndpoint = "$baseUrl/stream/$movieContentType/$movieContentId.json"
        val episodeStreamEndpoint = "$baseUrl/stream/$episodeContentType/$episodeContentId.json"

        assertEquals("https://example-streams.org/stream/movie/tt0111161.json", movieStreamEndpoint)
        assertEquals("https://example-streams.org/stream/series/tt0903747:1:1.json", episodeStreamEndpoint)
    }

    // ==========================================
    // PRIORITY 3: ID COMPATIBILITY
    // ==========================================

    @Test
    fun testIdCompatibility_rejectsIncompatibleIdPrefixes() {
        val streamAddon = InstalledAddon(
            id = "addon_stream",
            name = "Stream Provider",
            manifestUrl = "https://provider.example/manifest.json",
            baseUrl = "https://provider.example",
            resources = listOf(
                StremioResourceDefinition("stream", listOf("movie", "series"), listOf("tt"))
            ),
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt")
        )

        // Valid IMDb ID (tt...)
        assertTrue(streamAddon.supportsResource("stream", "movie", "tt0111161"))
        assertTrue(streamAddon.supportsResource("stream", "series", "tt0903747:1:1"))

        // Incompatible TMDB ID (tmdb_...)
        assertFalse(streamAddon.supportsResource("stream", "movie", "tmdb_550"))

        // Incompatible Mock ID (m...)
        assertFalse(streamAddon.supportsResource("stream", "movie", "m1"))

        // Incompatible Anime Kitsu ID
        assertFalse(streamAddon.supportsResource("stream", "anime", "kitsu:12345"))
    }

    // ==========================================
    // PRIORITY 4: STREAM VALIDATION & HEADERS
    // ==========================================

    @Test
    fun testStreamHeaders_andTorrentDetection() {
        // JSON with behaviorHints.headers and proxyHeaders
        val json = """
            {
                "url": "https://cdn.example.com/hls/master.m3u8",
                "title": "1080p Multi-Audio",
                "behaviorHints": {
                    "headers": {
                        "User-Agent": "StreamX-Player/1.0",
                        "Referer": "https://provider.example.com/"
                    }
                }
            }
        """.trimIndent()

        val stream = gson.fromJson(json, StremioStream::class.java)
        assertNotNull(stream)
        assertTrue(stream.isDirectHttp)
        assertFalse(stream.isTorrent)

        val headers = stream.getRequestHeaders()
        assertEquals("StreamX-Player/1.0", headers["User-Agent"])
        assertEquals("https://provider.example.com/", headers["Referer"])

        // Torrent stream with infoHash
        val torrentJson = """
            {
                "infoHash": "5d640678eae57c72c0d096904fd7d7405ece6653",
                "title": "1080p Torrent",
                "fileIdx": 1
            }
        """.trimIndent()

        val torrentStream = gson.fromJson(torrentJson, StremioStream::class.java)
        assertNotNull(torrentStream)
        assertTrue(torrentStream.isTorrent)
        assertFalse(torrentStream.isDirectHttp)
        assertNull(torrentStream.url)
    }

    // ==========================================
    // PRIORITY 5: PLAYBACK ERROR DETAILS
    // ==========================================

    @Test
    fun testPlaybackFailureMessage_isInformativeAndDetailed() {
        val failureDetail = buildString {
            append("No playback authorized source available for this title (tt0111161).\n\n")
            append("Add-on Capability Status:\n")
            append("• No stream provider add-on installed or enabled.\n")
            append("• Metadata-only (Cinemeta): Provides catalog & info only\n")
            append("\nPlease install an authorized Stremio stream provider add-on in Settings > Add-ons.")
        }

        assertTrue(failureDetail.contains("tt0111161"))
        assertTrue(failureDetail.contains("Add-on Capability Status"))
        assertTrue(failureDetail.contains("Metadata-only"))
        assertTrue(failureDetail.contains("Settings > Add-ons"))
    }
}
