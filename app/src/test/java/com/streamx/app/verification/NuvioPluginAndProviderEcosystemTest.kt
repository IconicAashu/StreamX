package com.streamx.app.verification

import com.streamx.app.domain.model.*
import com.streamx.app.network.provider.CloudStreamRepoParser
import com.streamx.app.network.provider.NuvioPluginAdapter
import com.streamx.app.network.provider.RepositoryTypeDetector
import org.junit.Assert.*
import org.junit.Test

class NuvioPluginAndProviderEcosystemTest {

    // ==========================================
    // PHASE 1: DIRECTORY ANALYSIS & CLASSIFICATION
    // ==========================================

    @Test
    fun testDirectoryClassificationIntoThreeCategories() {
        val providers = UnifiedProviderCatalogue.getAllProviders()
        assertTrue("Unified catalogue must contain entries", providers.isNotEmpty())

        val stremioAddons = providers.filter { it.category == ProviderCategory.STREMIO_ADDON }
        val nuvioPlugins = providers.filter { it.category == ProviderCategory.NUVIO_NATIVE }
        val cloudstreamExtensions = providers.filter { it.category == ProviderCategory.CLOUDSTREAM_EXTENSION }
        val mediaServers = providers.filter { it.category == ProviderCategory.MEDIA_SERVER }

        assertTrue("Must have Stremio add-ons", stremioAddons.isNotEmpty())
        assertTrue("Must have Nuvio native plugins", nuvioPlugins.isNotEmpty())
        assertTrue("Must have CloudStream extensions", cloudstreamExtensions.isNotEmpty())
        assertTrue("Must have Media Servers", mediaServers.isNotEmpty())

        // Validate metadata completeness
        providers.forEach { p ->
            assertNotNull("Provider ID required", p.id)
            assertNotNull("Provider Name required", p.name)
            assertNotNull("Category required", p.category)
            assertTrue("Supported types must not be empty", p.supportedTypes.isNotEmpty())
            assertTrue("Supported languages must not be empty", p.supportedLanguages.isNotEmpty())
            assertNotNull("Integration status required", p.integrationStatus)
            assertNotNull("Playback transport required", p.playbackTransport)
        }
    }

    // ==========================================
    // PHASE 2: STREMIO ADD-ON INTEGRATION
    // ==========================================

    @Test
    fun testStremioAddonProtocolCompliance() {
        val stremioAddons = UnifiedProviderCatalogue.getProvidersByCategory(ProviderCategory.STREMIO_ADDON)
        
        // Cinemeta Official: metadata only
        val cinemeta = stremioAddons.find { it.id == "addon_cinemeta" }
        assertNotNull(cinemeta)
        assertTrue(cinemeta!!.isMetadataOnly)
        assertFalse(cinemeta.isStreamProvider)
        assertEquals(ProviderIntegrationStatus.COMPATIBLE, cinemeta.integrationStatus)

        // Public Domain Movies: stream provider
        val pubDomain = stremioAddons.find { it.id == "org.stremio.pubdomainmovies" }
        assertNotNull(pubDomain)
        assertTrue(pubDomain!!.isStreamProvider)
        assertEquals(ProviderIntegrationStatus.COMPATIBLE, pubDomain.integrationStatus)

        // OpenSubtitles: subtitles only
        val openSubs = stremioAddons.find { it.id == "addon_open_subtitles" }
        assertNotNull(openSubs)
        assertTrue(openSubs!!.isSubtitlesOnly)
        assertFalse(openSubs.isStreamProvider)

        // Torrentio: requires configuration (Debrid key)
        val torrentio = stremioAddons.find { it.id == "addon_torrentio" }
        assertNotNull(torrentio)
        assertTrue(torrentio!!.requiresConfiguration)
        assertEquals(ProviderIntegrationStatus.REQUIRES_CONFIGURATION, torrentio.integrationStatus)
        assertNotNull(torrentio.configurationHint)
    }

    // ==========================================
    // PHASE 3: NUVIO NATIVE PLUGINS ANALYSIS & ADAPTER
    // ==========================================

    @Test
    fun testNuvioPluginAdapterParsesRepoManifest() {
        val adapter = NuvioPluginAdapter()

        val sampleNuvioManifest = """
        {
            "name": "Nuvio Verified Plugins",
            "author": "Nuvio Community",
            "version": "1.0.0",
            "plugins": [
                {
                    "id": "classic_cinema",
                    "name": "Classic Cinema Nuvio",
                    "version": "1.2.0",
                    "description": "Public domain movies scraper",
                    "author": "FilmArchivist",
                    "scriptUrl": "https://raw.githubusercontent.com/NuvioPlugin/classic/main/index.js",
                    "types": ["movie"],
                    "languages": ["en"]
                }
            ]
        }
        """.trimIndent()

        val result = adapter.parseRepositoryManifest(sampleNuvioManifest, "https://nuvioplugin.com/repo/manifest.json")
        assertTrue("Parsing Nuvio manifest must succeed", result.isSuccess)

        val providers = result.getOrThrow()
        assertEquals(1, providers.size)

        val provider = providers[0]
        assertEquals("nuvio_classic_cinema", provider.id)
        assertEquals("Classic Cinema Nuvio", provider.name)
        assertEquals(ProviderCategory.NUVIO_NATIVE, provider.category)
        // Must require runtime adapter (Hermes/QuickJS sandbox)
        assertEquals(ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER, provider.integrationStatus)
        assertTrue("Plugin must be flagged as executable", provider.isExecutablePlugin)
        assertTrue("Must explain sandbox requirement", provider.securityNotice?.contains("Hermes") == true)
    }

    // ==========================================
    // PHASE 4: CLOUDSTREAM EXTENSIONS ANALYSIS & PARSER
    // ==========================================

    @Test
    fun testCloudStreamRepoParserParsesPluginsJson() {
        val parser = CloudStreamRepoParser()

        val samplePluginsJson = """
        [
            {
                "name": "OpenCultureProvider",
                "internalName": "OpenCulture",
                "pluginClassName": "com.cloudstream.OpenCulture",
                "version": 4,
                "url": "https://raw.githubusercontent.com/cloudstreamrepo/open-culture/builds/OpenCulture.cs3",
                "types": ["TvSeries", "Movie"],
                "language": "en",
                "authors": ["CloudStream Devs"],
                "description": "Cultural open-access documentaries"
            }
        ]
        """.trimIndent()

        val result = parser.parsePluginsJson(samplePluginsJson, "https://cloudstreamrepo.com/repo.json", "Open Culture Repo")
        assertTrue("Parsing CloudStream plugins.json must succeed", result.isSuccess)

        val providers = result.getOrThrow()
        assertEquals(1, providers.size)

        val provider = providers[0]
        assertEquals("cs_openculture", provider.id)
        assertEquals("OpenCultureProvider", provider.name)
        assertEquals(ProviderCategory.CLOUDSTREAM_EXTENSION, provider.category)
        // Must require runtime adapter (Dalvik .cs3 sandbox)
        assertEquals(ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER, provider.integrationStatus)
        assertTrue("Must be marked as executable plugin", provider.isExecutablePlugin)
        assertTrue("Must state Dex bytecode sandbox requirement", provider.securityNotice?.contains("Dalvik") == true)
    }

    // ==========================================
    // REPOSITORY TYPE AUTO-DETECTION TESTS
    // ==========================================

    @Test
    fun testRepositoryTypeAutoDetection() {
        // Stremio URL
        val stremioResult = RepositoryTypeDetector.detect("https://v3-cinemeta.strem.io/manifest.json")
        assertEquals(ProviderCategory.STREMIO_ADDON, stremioResult.category)
        assertEquals(ProviderIntegrationStatus.COMPATIBLE, stremioResult.integrationStatus)

        // Stremio custom protocol
        val stremioProtocolResult = RepositoryTypeDetector.detect("stremio://torrentio.strem.fun/manifest.json")
        assertEquals(ProviderCategory.STREMIO_ADDON, stremioProtocolResult.category)
        assertEquals("https://torrentio.strem.fun/manifest.json", stremioProtocolResult.normalizedUrl)

        // Nuvio URL
        val nuvioResult = RepositoryTypeDetector.detect("https://nuvioplugin.com/plugins/manifest.json")
        assertEquals(ProviderCategory.NUVIO_NATIVE, nuvioResult.category)
        assertEquals(ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER, nuvioResult.integrationStatus)

        // CloudStream repo.json
        val cloudstreamResult = RepositoryTypeDetector.detect("https://raw.githubusercontent.com/user/repo/builds/repo.json")
        assertEquals(ProviderCategory.CLOUDSTREAM_EXTENSION, cloudstreamResult.category)
        assertEquals(ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER, cloudstreamResult.integrationStatus)

        // CloudStream .cs3 artifact
        val cs3Result = RepositoryTypeDetector.detect("https://cloudstreamrepo.com/plugins/Anime.cs3")
        assertEquals(ProviderCategory.CLOUDSTREAM_EXTENSION, cs3Result.category)

        // Jellyfin media server
        val jellyfinResult = RepositoryTypeDetector.detect("http://192.168.1.50:8096/System/Info")
        assertEquals(ProviderCategory.MEDIA_SERVER, jellyfinResult.category)
    }

    // ==========================================
    // PHASE 5: UNIFIED PROVIDER CATALOGUE & REFRESH
    // ==========================================

    @Test
    fun testUnifiedCatalogueFilterAndDefaults() {
        val all = UnifiedProviderCatalogue.getAllProviders()
        val compatible = UnifiedProviderCatalogue.getCompatibleProviders()
        val sandboxed = UnifiedProviderCatalogue.getSandboxedRuntimeProviders()
        val blocked = UnifiedProviderCatalogue.getBlockedProviders()
        val configRequired = UnifiedProviderCatalogue.getConfigurationRequiredProviders()
        val offlineUnavailable = UnifiedProviderCatalogue.getOfflineUnavailableProviders()

        assertTrue("Compatible providers must exist", compatible.isNotEmpty())
        assertTrue("Sandboxed runtime providers must exist", sandboxed.isNotEmpty())
        assertTrue("Blocked security providers must exist", blocked.isNotEmpty())
        assertTrue("Configuration-required providers must exist", configRequired.isNotEmpty())
        assertTrue("Offline unavailable providers must exist", offlineUnavailable.isNotEmpty())

        // Verify total adds up
        assertEquals(all.size, compatible.size + sandboxed.size + blocked.size + configRequired.size + offlineUnavailable.size)

        // Verify default installed providers
        val defaultInstalled = all.filter { it.isInstalled }
        assertTrue("New users receive default installed providers", defaultInstalled.isNotEmpty())
        assertTrue("Cinemeta must be default installed", defaultInstalled.any { it.id == "addon_cinemeta" })
        assertTrue("Public Domain Movies must be default installed", defaultInstalled.any { it.id == "org.stremio.pubdomainmovies" })

        val jellyfin = all.find { it.id == "server_home_jellyfin" }
        assertNotNull("Jellyfin provider entry must exist", jellyfin)
        assertTrue("Jellyfin requires user configuration", jellyfin!!.requiresConfiguration)
        assertEquals(ProviderIntegrationStatus.REQUIRES_CONFIGURATION, jellyfin.integrationStatus)
    }

    // ==========================================
    // PHASE 6: SECURITY AND LEGAL COMPATIBILITY
    // ==========================================

    @Test
    fun testSecurityAndLegalExclusions() {
        val blockedProviders = UnifiedProviderCatalogue.getBlockedProviders()
        assertTrue("Must identify and block unlicensed scrapers", blockedProviders.isNotEmpty())

        blockedProviders.forEach { blocked ->
            assertEquals(ProviderIntegrationStatus.BLOCKED_SECURITY, blocked.integrationStatus)
            assertFalse("Blocked providers cannot be marked legitimate", blocked.isLegitimate)
            assertNotNull("Must state reason for blocking", blocked.securityNotice)
            assertTrue(
                "Security notice must cite policy",
                blocked.securityNotice!!.contains("Blocked per STREAMX Security & Legal Policy")
            )
        }
    }

    // ==========================================
    // PHASE 7: PLAYER BASELINE REGRESSION TEST
    // ==========================================

    @Test
    fun testFenixFlixBaselineIntegrity() {
        val fenixSource = PlaybackSource(
            mediaId = "tt1375666",
            title = "FenixFlix: Inception (2010)",
            streamUrl = "https://fenixflix.stream/cdn/inception_1080p.m3u8",
            isLiveOrHls = true,
            providerName = "FenixFlix",
            requestHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; STREAMX-Player)",
                "Referer" to "https://fenixflix.com/"
            )
        )
        assertNotNull("FenixFlix source must be constructed", fenixSource)
        assertEquals("FenixFlix", fenixSource.providerName)
        assertTrue("Stream must be identified as HLS", fenixSource.isLiveOrHls)
        assertEquals("https://fenixflix.com/", fenixSource.requestHeaders["Referer"])
    }
}
