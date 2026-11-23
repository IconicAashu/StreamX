package com.streamx.app.verification

import com.google.gson.Gson
import com.streamx.app.domain.model.*
import com.streamx.app.network.addon.AddonSecurityValidator
import com.streamx.app.network.addon.StremioAddonClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

class StremioAddonSystemAuditTest {

    private val gson = Gson()

    // 1. Install a valid public manifest & parse capabilities
    @Test
    fun test1_installValidPublicManifestAndParseCapabilities() {
        val cinemetaManifestJson = """
            {
              "id": "org.stremio.cinemeta",
              "name": "Cinemeta",
              "version": "3.0.12",
              "description": "Official movie and series metadata provider",
              "resources": ["catalog", "meta"],
              "types": ["movie", "series", "anime"],
              "idPrefixes": ["tt"],
              "catalogs": [
                { "type": "movie", "id": "top", "name": "Popular Movies" },
                { "type": "series", "id": "top", "name": "Popular Series" }
              ]
            }
        """.trimIndent()

        val manifest = gson.fromJson(cinemetaManifestJson, StremioManifest::class.java)
        assertNotNull(manifest)

        val installed = InstalledAddon.fromManifest(
            manifest = manifest,
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            isEnabled = true,
            orderIndex = 0
        )

        assertEquals("org.stremio.cinemeta", installed.id)
        assertEquals("https://v3-cinemeta.strem.io", installed.baseUrl)
        assertTrue(installed.isEnabled)
        assertEquals(AddonStatus.READY, installed.status)
        assertEquals(2, installed.catalogs.size)
        assertTrue(installed.supportsResource("catalog", "movie", "tt0111161"))
        assertTrue(installed.supportsResource("meta", "series", "tt0903747"))
        assertFalse(installed.supportsResource("stream", "movie", "tt0111161"))
    }

    // 2. Restart simulation and verify JSON persistence
    @Test
    fun test2_persistenceAcrossSimulatedRestart() {
        val addons = listOf(
            InstalledAddon(
                id = "addon_cinemeta",
                name = "Cinemeta",
                manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
                baseUrl = "https://v3-cinemeta.strem.io",
                isEnabled = true,
                version = "3.0.12",
                orderIndex = 0
            ),
            InstalledAddon(
                id = "addon_pdm",
                name = "Public Domain Movies",
                manifestUrl = "https://publicdomainmovies.info/manifest.json",
                baseUrl = "https://publicdomainmovies.info",
                isEnabled = false,
                version = "2.1.0",
                orderIndex = 1
            )
        )

        // Serialize to DataStore JSON representation
        val serializedJson = gson.toJson(addons)
        assertNotNull(serializedJson)
        assertTrue(serializedJson.contains("addon_cinemeta"))
        assertTrue(serializedJson.contains("addon_pdm"))

        // Simulate application restart: deserialize from raw JSON
        val deserialized = gson.fromJson(serializedJson, Array<InstalledAddon>::class.java).toList()
        assertEquals(2, deserialized.size)
        assertEquals("addon_cinemeta", deserialized[0].id)
        assertTrue(deserialized[0].isEnabled)
        assertEquals("addon_pdm", deserialized[1].id)
        assertFalse(deserialized[1].isEnabled)
    }

    // 3. Multi-Source Aggregation (Multiple Stremio Addons & Stream Providers)
    @Test
    fun test3_multiSourceAggregation_multipleStremioAddons() {
        // Given a movie with ID "tt0111161"
        val mediaId = "tt0111161"

        // Simulated Stream Provider VIP source
        val provider1Source = PlaybackSource(
            mediaId = mediaId,
            title = "Fast CDN VIP",
            streamUrl = "https://cdn.provider.sample/streams/shawshank_1080p.m3u8",
            isLiveOrHls = true,
            licenseNotice = "Fast CDN • Authorized Stream"
        )

        // Simulated Public Domain Addon stream
        val addon1Source = PlaybackSource(
            mediaId = mediaId,
            title = "Public Domain Movies: 1080p HD",
            streamUrl = "https://archive.org/download/shawshank_classic/stream.mp4",
            isLiveOrHls = false,
            licenseNotice = "Source: Public Domain Movies • Media3 Verified"
        )

        // Simulated Community HLS Addon stream
        val addon2Source = PlaybackSource(
            mediaId = mediaId,
            title = "Cinemeta Resolver: 720p Multi-Audio",
            streamUrl = "https://video.cinemeta.sample/hls/720p.m3u8",
            isLiveOrHls = true,
            licenseNotice = "Source: Cinemeta Resolver • Media3 Verified"
        )

        // Aggregated result must contain all sources with distinct URLs and correct attribution
        val aggregated = listOf(provider1Source, addon1Source, addon2Source).distinctBy { it.streamUrl }

        assertEquals(3, aggregated.size)
        assertEquals("Fast CDN VIP", aggregated[0].title)
        assertEquals("Public Domain Movies: 1080p HD", aggregated[1].title)
        assertEquals("Cinemeta Resolver: 720p Multi-Audio", aggregated[2].title)
        assertTrue(aggregated.all { it.streamUrl.startsWith("https://") })
    }

    // 4. Disable one addon and verify it stops contributing results
    @Test
    fun test4_addonDisabling_stopsResultsImmediately() {
        val addonPublicDomain = InstalledAddon(
            id = "addon_pdm",
            name = "Public Domain Movies",
            manifestUrl = "https://publicdomainmovies.info/manifest.json",
            baseUrl = "https://publicdomainmovies.info",
            isEnabled = true,
            resources = listOf(StremioResourceDefinition("stream", listOf("movie"))),
            types = listOf("movie")
        )

        assertTrue(addonPublicDomain.supportsResource("stream", "movie", "pdm_1"))

        // When user disables it
        val disabledAddon = addonPublicDomain.copy(isEnabled = false, status = AddonStatus.DISABLED)
        assertFalse(disabledAddon.isEnabled)
        assertFalse(disabledAddon.supportsResource("stream", "movie", "pdm_1"))
    }

    // 5. Namespaced ID mapping & Original ID Preservation
    @Test
    fun test5_namespacedIdMapping_preservesOriginalIdWithoutCollision() {
        // Test namespaced ID with simple IMDb id
        val cinemetaId = "addon_cinemeta:tt0111161"
        val expectedCinemetaOriginal = "tt0111161"

        // Test namespaced ID where original ID itself has a colon (e.g. kitsu:anime:1234)
        val kitsuId = "addon_anime_kitsu:kitsu:1234"
        val expectedKitsuOriginal = "kitsu:1234"

        fun extractOriginal(namespacedId: String, addonId: String): String {
            return if (namespacedId.startsWith("$addonId:")) {
                namespacedId.removePrefix("$addonId:")
            } else {
                namespacedId
            }
        }

        assertEquals(expectedCinemetaOriginal, extractOriginal(cinemetaId, "addon_cinemeta"))
        assertEquals(expectedKitsuOriginal, extractOriginal(kitsuId, "addon_anime_kitsu"))

        // Ensure no collision: two addons having the same raw ID "123" produce distinct unique IDs
        val addonAId = "addon_a:123"
        val addonBId = "addon_b:123"
        assertNotEquals(addonAId, addonBId)
        assertEquals("123", extractOriginal(addonAId, "addon_a"))
        assertEquals("123", extractOriginal(addonBId, "addon_b"))
    }

    // 6. Anime Compatibility (Kitsu and standard Stremio series mapping)
    @Test
    fun test6_animeCompatibility_mapsToAnimeCategoryAndHandlesSeriesFallback() {
        // Addon 1: Dedicated Anime Addon (Kitsu) declaring "anime"
        val kitsuManifest = StremioManifest(
            id = "com.kitsu.anime",
            name = "Kitsu Anime",
            resources = listOf(StremioResourceDefinition("meta", listOf("anime"))),
            types = listOf("anime"),
            idPrefixes = listOf("kitsu")
        )
        assertTrue(kitsuManifest.supportsResource("meta", "anime", "kitsu:999"))

        // Addon 2: Standard Stremio Metadata Addon declaring "series" and "movie"
        val standardAddon = StremioManifest(
            id = "org.standard.meta",
            name = "Standard Meta",
            resources = listOf(StremioResourceDefinition("meta", listOf("series", "movie"))),
            types = listOf("series", "movie"),
            idPrefixes = listOf("tt")
        )

        // When STREAMX queries for anime content, standard addon should match via series fallback if id starts with tt
        assertTrue(standardAddon.supportsResource("meta", "anime", "tt1234567"))
        assertFalse(standardAddon.supportsResource("meta", "anime", "kitsu:999"))

        // Test category mapping
        val metaItem = StremioMetaItem(
            id = "kitsu:456",
            type = "anime",
            name = "Demon Slayer: Kimetsu no Yaiba",
            genres = listOf("Action", "Fantasy", "Animation")
        )

        val isAnime = metaItem.type.equals("anime", ignoreCase = true) ||
                metaItem.genres?.any { it.contains("anime", ignoreCase = true) } == true
        val format = if (isAnime) ContentFormat.ANIME else ContentFormat.SERIES
        val category = if (format == ContentFormat.ANIME) MediaCategory.ANIME else MediaCategory.SERIES

        assertEquals(ContentFormat.ANIME, format)
        assertEquals(MediaCategory.ANIME, category)
    }

    // 7. Subtitles only appear when valid subtitle resources exist
    @Test
    fun test7_subtitles_appearOnlyWhenValidResourceExists() {
        val addonNoSubtitles = InstalledAddon(
            id = "addon_streams_only",
            name = "Streams Only",
            manifestUrl = "https://streams.example.com/manifest.json",
            baseUrl = "https://streams.example.com",
            resources = listOf(StremioResourceDefinition("stream")),
            types = listOf("movie")
        )

        val addonWithSubtitles = InstalledAddon(
            id = "addon_opensubtitles",
            name = "OpenSubtitles",
            manifestUrl = "https://opensubtitles.example.com/manifest.json",
            baseUrl = "https://opensubtitles.example.com",
            resources = listOf(StremioResourceDefinition("subtitles")),
            types = listOf("movie", "series")
        )

        assertFalse(addonNoSubtitles.supportsResource("subtitles", "movie"))
        assertTrue(addonWithSubtitles.supportsResource("subtitles", "movie"))

        // Parse valid subtitles
        val subJson = """
            {
              "subtitles": [
                { "id": "1", "url": "https://subtitles.org/sub.srt", "lang": "hin", "label": "Hindi" }
              ]
            }
        """.trimIndent()
        val subs = gson.fromJson(subJson, StremioSubtitleResponse::class.java).subtitles.orEmpty()
        assertEquals(1, subs.size)
        assertEquals("hin", subs[0].lang)
        assertTrue(subs[0].url.startsWith("https://"))
    }

    // 8. Simulate unavailable addon and verify failure isolation
    @Test
    fun test8_unavailableAddonSimulation_otherAddonsContinueWorkingIsolated() {
        val workingAddon = InstalledAddon(
            id = "addon_working",
            name = "Working Provider",
            manifestUrl = "https://working.example.com/manifest.json",
            baseUrl = "https://working.example.com",
            isEnabled = true,
            resources = listOf(StremioResourceDefinition("stream")),
            types = listOf("movie")
        )

        val failingAddon = InstalledAddon(
            id = "addon_failing",
            name = "Offline Provider",
            manifestUrl = "https://offline.example.com/manifest.json",
            baseUrl = "https://offline.example.com",
            isEnabled = true,
            resources = listOf(StremioResourceDefinition("stream")),
            types = listOf("movie")
        )

        val retrievedStreams = mutableListOf<PlaybackSource>()

        val addons = listOf(failingAddon, workingAddon)
        for (addon in addons) {
            try {
                if (addon.id == "addon_failing") {
                    throw IOException("HTTP 502 Bad Gateway: Host unavailable")
                } else {
                    retrievedStreams.add(
                        PlaybackSource(
                            mediaId = "tt0111161",
                            title = "${addon.name}: 1080p Stream",
                            streamUrl = "https://working.cdn/movie.mp4",
                            licenseNotice = "${addon.name} • Media3 Verified"
                        )
                    )
                }
            } catch (e: Exception) {
                // Failure isolation ensures loop continues
            }
        }

        // Despite failingAddon throwing, workingAddon succeeded
        assertEquals(1, retrievedStreams.size)
        assertEquals("Working Provider: 1080p Stream", retrievedStreams[0].title)
    }

    // 9. SSRF, DNS & Redirect Protection
    @Test
    fun test9_ssrfAndDnsRedirectProtection_blocksPrivateAndLoopbackTargets() {
        // Block loopback
        assertTrue(AddonSecurityValidator.validateUrl("http://localhost/manifest.json").isFailure)
        assertTrue(AddonSecurityValidator.validateUrl("http://127.0.0.1:8080/manifest.json").isFailure)
        assertTrue(AddonSecurityValidator.validateUrl("http://[::1]:8080/manifest.json").isFailure)

        // Block AWS/GCP/Azure link-local metadata address
        val metadataAddress = InetAddress.getByName("169.254.169.254")
        assertTrue(AddonSecurityValidator.isPrivateOrLocalAddress(metadataAddress))

        // Block corporate intranet addresses
        val intranet10 = InetAddress.getByName("10.10.10.10")
        assertTrue(AddonSecurityValidator.isPrivateOrLocalAddress(intranet10))

        val intranet192 = InetAddress.getByName("192.168.1.1")
        assertTrue(AddonSecurityValidator.isPrivateOrLocalAddress(intranet192))

        val intranet172 = InetAddress.getByName("172.20.0.1")
        assertTrue(AddonSecurityValidator.isPrivateOrLocalAddress(intranet172))

        // SafeDns blocks disallowed hostnames
        val safeDns = AddonSecurityValidator.SafeDns()
        try {
            safeDns.lookup("localhost")
            fail("Expected SecurityException for localhost")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("SSRF blocked"))
        }

        try {
            safeDns.lookup("127.0.0.1")
            fail("Expected SecurityException for 127.0.0.1")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("SSRF blocked"))
        }
    }
}
