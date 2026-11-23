package com.streamx.app.verification

import com.google.gson.Gson
import com.streamx.app.domain.model.*
import com.streamx.app.network.addon.AddonSecurityValidator
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class StremioAddonProtocolTest {

    private val gson = Gson()

    @Test
    fun manifestDeserialization_stringResourcesArray() {
        val json = """
            {
              "id": "org.stremio.cinemeta",
              "name": "Cinemeta",
              "version": "3.0.12",
              "description": "The official IMDb movie/series metadata addon",
              "resources": ["catalog", "meta", "subtitles"],
              "types": ["movie", "series"],
              "idPrefixes": ["tt"]
            }
        """.trimIndent()

        val manifest = gson.fromJson(json, StremioManifest::class.java)

        assertNotNull(manifest)
        assertEquals("org.stremio.cinemeta", manifest.id)
        assertEquals("Cinemeta", manifest.name)
        assertEquals("3.0.12", manifest.version)
        assertEquals(3, manifest.resources.size)
        assertEquals("catalog", manifest.resources[0].name)
        assertEquals("meta", manifest.resources[1].name)
        assertEquals("subtitles", manifest.resources[2].name)
        assertEquals(listOf("movie", "series"), manifest.types)
        assertEquals(listOf("tt"), manifest.idPrefixes)

        // Test supportsResource
        assertTrue(manifest.supportsResource("catalog", "movie", "tt1234567"))
        assertTrue(manifest.supportsResource("meta", "series", "tt9876543"))
        assertFalse(manifest.supportsResource("stream", "movie", "tt1234567"))
        assertFalse(manifest.supportsResource("catalog", "movie", "kitsu:123"))
    }

    @Test
    fun manifestDeserialization_objectResourcesArray() {
        val json = """
            {
              "id": "com.example.streams",
              "name": "Custom Stream Provider",
              "version": "1.0.0",
              "description": "Provides streaming sources",
              "resources": [
                {
                  "name": "stream",
                  "types": ["movie"],
                  "idPrefixes": ["tt", "pdm:"]
                },
                {
                  "name": "subtitles",
                  "types": ["movie", "series"]
                }
              ],
              "types": ["movie", "series"]
            }
        """.trimIndent()

        val manifest = gson.fromJson(json, StremioManifest::class.java)

        assertNotNull(manifest)
        assertEquals("com.example.streams", manifest.id)
        assertEquals(2, manifest.resources.size)
        assertEquals("stream", manifest.resources[0].name)
        assertEquals(listOf("movie"), manifest.resources[0].types)
        assertEquals(listOf("tt", "pdm:"), manifest.resources[0].idPrefixes)

        // Supports stream for movies with tt prefix
        assertTrue(manifest.supportsResource("stream", "movie", "tt0111161"))
        assertTrue(manifest.supportsResource("stream", "movie", "pdm:classic_1"))
        // Does not support stream for series
        assertFalse(manifest.supportsResource("stream", "series", "tt0111161"))
        // Does not support stream for unlisted idPrefix
        assertFalse(manifest.supportsResource("stream", "movie", "other:123"))
        // Subtitles supported for both movie and series
        assertTrue(manifest.supportsResource("subtitles", "series", "tt0111161"))
    }

    @Test
    fun securityValidator_blocksDisallowedSchemes() {
        val ftp = AddonSecurityValidator.validateUrl("ftp://files.example.com/manifest.json")
        assertTrue(ftp.isFailure)
        assertTrue(ftp.exceptionOrNull() is SecurityException)

        val file = AddonSecurityValidator.validateUrl("file:///sdcard/Download/manifest.json")
        assertTrue(file.isFailure)

        val js = AddonSecurityValidator.validateUrl("javascript:alert(1)")
        assertTrue(js.isFailure)
    }

    @Test
    fun securityValidator_blocksLoopbackAndLocalNetworks() {
        val localhost = AddonSecurityValidator.validateUrl("http://localhost:8080/manifest.json")
        assertTrue(localhost.isFailure)

        val loopbackIp = AddonSecurityValidator.validateUrl("http://127.0.0.1:3000/manifest.json")
        assertTrue(loopbackIp.isFailure)

        val zeroIp = AddonSecurityValidator.validateUrl("http://0.0.0.0:8000/manifest.json")
        assertTrue(zeroIp.isFailure)
    }

    @Test
    fun securityValidator_detectsPrivateIpRanges() {
        val privateIps = listOf(
            "10.0.0.1",
            "10.255.255.255",
            "172.16.0.1",
            "172.24.1.1",
            "172.31.255.255",
            "192.168.0.1",
            "192.168.1.100",
            "169.254.169.254", // Cloud metadata link-local
            "127.0.0.1",
            "0.0.0.0"
        )

        for (ipStr in privateIps) {
            val addr = InetAddress.getByName(ipStr)
            assertTrue("Expected $ipStr to be blocked as private/local", AddonSecurityValidator.isPrivateOrLocalAddress(addr))
        }

        // Public IPs should not be detected as private
        val publicIp = InetAddress.getByName("8.8.8.8")
        assertFalse(AddonSecurityValidator.isPrivateOrLocalAddress(publicIp))
    }

    @Test
    fun securityValidator_maxPayloadLimitIs5MB() {
        assertEquals(5 * 1024 * 1024L, AddonSecurityValidator.MAX_RESPONSE_SIZE_BYTES)
    }

    @Test
    fun streamResponse_parsingAndValidation() {
        val json = """
            {
              "streams": [
                {
                  "name": "Public Domain 1080p",
                  "title": "Night of the Living Dead • Full Feature",
                  "url": "https://archive.org/download/night_of_the_living_dead/night.mp4"
                },
                {
                  "name": "HLS Adaptive",
                  "title": "Multi-bitrate Stream",
                  "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/master.m3u8"
                },
                {
                  "name": "Torrent Stream",
                  "title": "P2P Stream",
                  "infoHash": "0123456789abcdef0123456789abcdef01234567"
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, StremioStreamResponse::class.java)
        assertNotNull(response)
        val streams = response.streams.orEmpty()
        assertEquals(3, streams.size)

        // Check direct HTTP/HLS streams
        val httpStreams = streams.filter { !it.url.isNullOrBlank() && it.url!!.startsWith("http") }
        assertEquals(2, httpStreams.size)
        assertEquals("https://archive.org/download/night_of_the_living_dead/night.mp4", httpStreams[0].url)
        assertTrue(httpStreams[1].url!!.endsWith(".m3u8"))

        // Check torrent stream has infoHash but no direct HTTP URL
        val torrentStream = streams.first { it.infoHash != null }
        assertNull(torrentStream.url)
        assertEquals("0123456789abcdef0123456789abcdef01234567", torrentStream.infoHash)
    }

    @Test
    fun subtitleResponse_parsing() {
        val json = """
            {
              "subtitles": [
                {
                  "id": "sub_en_1",
                  "url": "https://opensubtitles.strem.io/sub_en.vtt",
                  "lang": "eng",
                  "label": "English SDH"
                },
                {
                  "id": "sub_hi_1",
                  "url": "https://opensubtitles.strem.io/sub_hi.srt",
                  "lang": "hin",
                  "label": "Hindi"
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, StremioSubtitleResponse::class.java)
        assertNotNull(response)
        val subs = response.subtitles.orEmpty()
        assertEquals(2, subs.size)
        assertEquals("eng", subs[0].lang)
        assertEquals("English SDH", subs[0].label)
        assertEquals("https://opensubtitles.strem.io/sub_en.vtt", subs[0].url)
        assertEquals("hin", subs[1].lang)
    }

    @Test
    fun installedAddon_fromManifest_andStatusToggling() {
        val manifest = StremioManifest(
            id = "test.addon",
            name = "Test Addon",
            version = "2.0.0",
            description = "A test addon for verification",
            resources = listOf(StremioResourceDefinition("stream")),
            types = listOf("movie")
        )

        val installed = InstalledAddon.fromManifest(
            manifest = manifest,
            manifestUrl = "https://example.com/addon/manifest.json",
            isEnabled = true,
            orderIndex = 0
        )

        assertEquals("test.addon", installed.id)
        assertEquals("Test Addon", installed.name)
        assertEquals("https://example.com/addon", installed.baseUrl)
        assertEquals(AddonStatus.READY, installed.status)
        assertTrue(installed.isEnabled)
        assertTrue(installed.supportsResource("stream", "movie"))

        // Test backwards compatibility conversion to AddonManifest
        val legacy = installed.toAddonManifest()
        assertEquals("test.addon", legacy.id)
        assertEquals("Test Addon", legacy.name)
        assertEquals("2.0.0", legacy.version)
        assertTrue(legacy.isEnabled)

        // Test disabling
        val disabled = installed.copy(isEnabled = false, status = AddonStatus.DISABLED)
        assertFalse(disabled.isEnabled)
        assertFalse(disabled.supportsResource("stream", "movie"))
    }
}
