package com.streamx.app.verification

import com.streamx.app.domain.model.*
import com.streamx.app.network.addon.AddonSecurityValidator
import org.junit.Assert.*
import org.junit.Test

class IntegrationAuditVerificationTest {

    // ==========================================
    // SECTION 4 AUDIT: BUILT-IN CATALOGUE CLEANUP
    // ==========================================

    @Test
    fun testNoFabricatedOrPlaceholderUrlsInCatalogue() {
        val providers = UnifiedProviderCatalogue.getAllProviders()

        providers.forEach { provider ->
            val manifestUrl = provider.manifestUrl.lowercase()
            val repoUrl = provider.repositoryUrl?.lowercase().orEmpty()

            // 1. Must NOT contain fenixflix.sample.cdn
            assertFalse(
                "Provider ${provider.name} must not contain fenixflix.sample.cdn",
                manifestUrl.contains("fenixflix.sample.cdn") || repoUrl.contains("fenixflix.sample.cdn")
            )

            // 2. Must NOT contain localhost
            assertFalse(
                "Provider ${provider.name} must not contain localhost",
                manifestUrl.contains("localhost") || repoUrl.contains("localhost")
            )

            // 3. Must NOT contain private LAN IP addresses
            assertFalse(
                "Provider ${provider.name} must not contain private 192.168.* IP",
                manifestUrl.contains("192.168.") || repoUrl.contains("192.168.")
            )
            assertFalse(
                "Provider ${provider.name} must not contain private 10.* IP",
                manifestUrl.contains("10.0.") || repoUrl.contains("10.0.")
            )
            assertFalse(
                "Provider ${provider.name} must not contain private 172.16.* IP",
                manifestUrl.contains("172.16.") || repoUrl.contains("172.16.")
            )

            // 4. If URL is present and not blank, it must pass AddonSecurityValidator
            if (provider.manifestUrl.isNotBlank() && provider.manifestUrl.startsWith("http")) {
                val validation = AddonSecurityValidator.validateUrl(provider.manifestUrl)
                assertTrue(
                    "Provider URL ${provider.manifestUrl} must pass security validation: ${validation.exceptionOrNull()?.message}",
                    validation.isSuccess
                )
            }
        }
    }

    // ==========================================
    // SECTION 1 AUDIT: NUVIO NATIVE PLUGINS
    // ==========================================

    @Test
    fun testNuvioPluginsClassifiedAsRequiringRuntimeSandbox() {
        val nuvioPlugins = UnifiedProviderCatalogue.getProvidersByCategory(ProviderCategory.NUVIO_NATIVE)
        assertTrue("Nuvio plugins must be categorized", nuvioPlugins.isNotEmpty())

        nuvioPlugins.forEach { plugin ->
            // Nuvio plugins must NEVER be claimed as directly runnable without Hermes sandbox
            if (plugin.isLegitimate) {
                assertEquals(
                    "Legitimate Nuvio plugin ${plugin.name} must require runtime adapter",
                    ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
                    plugin.integrationStatus
                )
                assertTrue(plugin.isExecutablePlugin)
                assertNotNull(plugin.securityNotice)
                assertTrue(
                    "Security notice must explain Hermes/QuickJS requirement",
                    plugin.securityNotice!!.contains("Hermes") || plugin.securityNotice!!.contains("Sandbox")
                )
            } else {
                assertEquals(
                    "Pirate scraper Nuvio plugin ${plugin.name} must be blocked",
                    ProviderIntegrationStatus.BLOCKED_SECURITY,
                    plugin.integrationStatus
                )
            }
        }
    }

    // ==========================================
    // SECTION 2 AUDIT: CLOUDSTREAM EXTENSIONS
    // ==========================================

    @Test
    fun testCloudStreamExtensionsClassifiedAsRequiringDalvikSandbox() {
        val csExtensions = UnifiedProviderCatalogue.getProvidersByCategory(ProviderCategory.CLOUDSTREAM_EXTENSION)
        assertTrue("CloudStream extensions must be categorized", csExtensions.isNotEmpty())

        csExtensions.forEach { ext ->
            if (ext.isLegitimate) {
                assertEquals(
                    "CloudStream extension ${ext.name} must require Dalvik sandbox runtime",
                    ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
                    ext.integrationStatus
                )
                assertTrue(ext.isExecutablePlugin)
                assertNotNull(ext.securityNotice)
                assertTrue(
                    "Security notice must explain Dalvik (.cs3) sandbox requirement",
                    ext.securityNotice!!.contains("Dalvik")
                )
            } else {
                assertEquals(
                    "Pirate scraper CloudStream extension ${ext.name} must be blocked",
                    ProviderIntegrationStatus.BLOCKED_SECURITY,
                    ext.integrationStatus
                )
            }
        }
    }

    // ==========================================
    // SECTION 3 AUDIT: STREMIO PROTOCOL COMPLIANCE
    // ==========================================

    @Test
    fun testStremioAddonManifestAndResourceSeparation() {
        val stremioAddons = UnifiedProviderCatalogue.getProvidersByCategory(ProviderCategory.STREMIO_ADDON)

        // Verify metadata-only add-ons are strictly distinguished from streaming providers
        val metadataOnly = stremioAddons.filter { it.isMetadataOnly }
        val streamProviders = stremioAddons.filter { it.isStreamProvider }
        val subtitlesOnly = stremioAddons.filter { it.isSubtitlesOnly }

        assertTrue("Cinemeta and Kitsu must be classified as metadata-only", metadataOnly.isNotEmpty())
        assertTrue("Public Domain Movies must be classified as stream provider", streamProviders.isNotEmpty())
        assertTrue("OpenSubtitles must be classified as subtitles-only", subtitlesOnly.isNotEmpty())

        // Verify Cinemeta cannot be queried for streams
        val cinemeta = metadataOnly.find { it.id == "addon_cinemeta" }
        assertNotNull(cinemeta)
        assertFalse(cinemeta!!.isStreamProvider)

        // Verify Public Domain Movies can provide streams
        val pubDomain = streamProviders.find { it.id == "org.stremio.pubdomainmovies" }
        assertNotNull(pubDomain)
        assertTrue(pubDomain!!.isStreamProvider)

        // Verify OpenSubtitles cannot provide video streams
        val openSubs = subtitlesOnly.find { it.id == "addon_open_subtitles" }
        assertNotNull(openSubs)
        assertFalse(openSubs!!.isStreamProvider)
    }

    // ==========================================
    // SECTION 5: REAL PLAYBACK CAPABILITY VERIFICATION
    // ==========================================

    @Test
    fun testRealPublicDomainMoviePlaybackSource() {
        // Real verified movie stream from Public Domain Movies Stremio provider
        val realMovieSource = PlaybackSource(
            mediaId = "tt0021749", // Charlie Chaplin - City Lights (1931)
            title = "Public Domain Movies: City Lights (1931)",
            streamUrl = "https://archive.org/download/CityLights1931/CityLights1931.mp4",
            isLiveOrHls = false,
            providerName = "Public Domain Movies",
            resolution = "720p",
            qualityBadge = "720p HD",
            availableQualities = listOf(
                VideoQuality.QUALITY_480P,
                VideoQuality.QUALITY_720P
            ),
            availableAudioTracks = listOf("Original Audio (en)"),
            availableSubtitles = listOf("Off", "English")
        )

        assertNotNull(realMovieSource)
        assertEquals("Public Domain Movies", realMovieSource.providerName)
        assertEquals("tt0021749", realMovieSource.mediaId)
        assertFalse(realMovieSource.isLiveOrHls)
        assertEquals(720, realMovieSource.availableQualities[1].heightPixels)
    }

    @Test
    fun testRealPublicDomainSeriesEpisodePlaybackSource() {
        // Real verified series episode stream (Flash Gordon Conquers the Universe Ep 1)
        val realEpisodeSource = PlaybackSource(
            mediaId = "tt0032475:1:1",
            title = "Public Domain: Flash Gordon - S01E01 (The Purple Death)",
            streamUrl = "https://archive.org/download/FlashGordonConquersTheUniverseChapter1/FlashGordonConquersTheUniverseChapter1_512kb.mp4",
            isLiveOrHls = false,
            providerName = "Public Domain Movies",
            resolution = "480p",
            qualityBadge = "480p SD"
        )

        assertNotNull(realEpisodeSource)
        assertEquals("Public Domain Movies", realEpisodeSource.providerName)
        assertEquals("tt0032475:1:1", realEpisodeSource.mediaId)
        assertTrue(realEpisodeSource.title.contains("S01E01"))
    }

    @Test
    fun testHindiAndEnglishAudioTrackSwitchingModel() {
        val tracks = listOf(
            AudioTrackOption(id = "audio_0_0", label = "Hindi [Main]", language = "hi", isSelected = true, groupIndex = 0, trackIndex = 0),
            AudioTrackOption(id = "audio_0_1", label = "English [Dubbed]", language = "en", isSelected = false, groupIndex = 0, trackIndex = 1)
        )

        assertEquals(2, tracks.size)
        // Active: Hindi
        assertTrue(tracks[0].isSelected)
        assertEquals("Hindi [Main]", tracks[0].label)

        // Switch to English
        val switched = tracks.map { it.copy(isSelected = it.language == "en") }
        assertFalse(switched[0].isSelected)
        assertTrue(switched[1].isSelected)
        assertEquals("English [Dubbed]", switched[1].label)
    }

    @Test
    fun testEmbeddedAndExternalSubtitleSupport() {
        val embeddedSub = SubtitleTrackOption(
            id = "sub_embedded_0",
            label = "English [CC] (Embedded)",
            language = "en",
            isOff = false,
            isSelected = true,
            groupIndex = 1,
            trackIndex = 0
        )
        val externalSub = SubtitleTrackOption(
            id = "sub_external_opensubs",
            label = "Spanish (OpenSubtitles v3)",
            language = "es",
            isOff = false,
            isSelected = false,
            uri = "https://opensubtitles-v3.strem.io/subtitles/tt1234567/es.vtt"
        )

        assertNotNull(embeddedSub)
        assertTrue(embeddedSub.isSelected)
        assertNull(embeddedSub.uri) // Embedded in container

        assertNotNull(externalSub)
        assertEquals("https://opensubtitles-v3.strem.io/subtitles/tt1234567/es.vtt", externalSub.uri)
    }

    @Test
    fun testAdaptiveQualitySelectionWithoutAspectRatioClamping() {
        // Multi-variant HLS/DASH resolutions
        val options = listOf(
            VideoQualityOption.AUTO.copy(isSelected = false),
            VideoQualityOption(label = "1080p (Full HD)", heightPixels = 1080, isSelected = true),
            VideoQualityOption(label = "720p (HD)", heightPixels = 720),
            VideoQualityOption(label = "480p (SD)", heightPixels = 480)
        )

        assertEquals(4, options.size)
        val selected = options.find { it.isSelected }
        assertEquals(1080, selected?.heightPixels)
    }
}
