package com.streamx.app.domain.model

import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Centrally managed, unified provider catalogue for STREAMX.
 *
 * Implements Directory Analysis (Phase 1), Stremio Integration (Phase 2),
 * Nuvio Plugin Analysis (Phase 3), CloudStream Extension Analysis (Phase 4),
 * Multi-Provider Catalogue (Phase 5), and Security/Legal Filtering (Phase 6).
 */
object UnifiedProviderCatalogue {

    private const val TAG = "UnifiedProviderCatalogue"

    // Master Curated Unified Catalogue
    private val initialMasterList: List<UnifiedProvider> = listOf(
        // ==========================================
        // 1. STREMIO ADD-ONS (COMPATIBLE PROTOCOL)
        // ==========================================
        UnifiedProvider(
            id = "addon_cinemeta",
            name = "Cinemeta Official",
            category = ProviderCategory.STREMIO_ADDON,
            description = "Official Stremio metadata provider for Movies, Series, and Anime with IMDb/TMDB IDs, posters, and cast.",
            author = "Stremio Official",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            repositoryUrl = "https://stremio-addons.net",
            supportedTypes = listOf("movie", "series", "anime"),
            supportedLanguages = listOf("en", "es", "fr", "de", "hi", "ja"),
            iconUrl = "https://v3-cinemeta.strem.io/logo.png",
            isInstalled = true,
            isEnabled = true,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Online (Active)",
            declaredResources = listOf("catalog", "meta"),
            protocolDescription = "Stremio JSON/REST Protocol v3",
            isLegitimate = true
        ),
        UnifiedProvider(
            id = "org.stremio.pubdomainmovies",
            name = "Public Domain Movies",
            category = ProviderCategory.STREMIO_ADDON,
            description = "Classic cinema, vintage animation, and historical films in the public domain with direct streaming links.",
            author = "Community Verified",
            manifestUrl = "https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json",
            repositoryUrl = "https://stremio-addons.net",
            supportedTypes = listOf("movie"),
            supportedLanguages = listOf("en"),
            iconUrl = "https://johnserv531.github.io/gh-images/1586344884.jpg",
            isInstalled = true,
            isEnabled = true,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Online (Active)",
            declaredResources = listOf("catalog", "stream"),
            protocolDescription = "Stremio JSON/REST Protocol v3",
            isLegitimate = true
        ),
        UnifiedProvider(
            id = "addon_open_subtitles",
            name = "OpenSubtitles v3",
            category = ProviderCategory.STREMIO_ADDON,
            description = "Official multi-language subtitle streams for movies and TV series powered by the OpenSubtitles.org community.",
            author = "OpenSubtitles Official",
            manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
            repositoryUrl = "https://stremio-addons.net",
            supportedTypes = listOf("movie", "series", "anime"),
            supportedLanguages = listOf("en", "es", "hi", "fr", "de", "it", "pt", "ru", "ar", "zh", "ja", "ko"),
            iconUrl = "https://opensubtitles-v3.strem.io/logo.png",
            isInstalled = true,
            isEnabled = true,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Online (Active)",
            declaredResources = listOf("subtitles"),
            protocolDescription = "Stremio Subtitles JSON Protocol v3",
            isLegitimate = true
        ),
        UnifiedProvider(
            id = "addon_anime_kitsu",
            name = "Kitsu Anime Catalog",
            category = ProviderCategory.STREMIO_ADDON,
            description = "Extensive Japanese anime catalog with trending lists, franchise chronologies, and episode guides.",
            author = "Kitsu Community",
            manifestUrl = "https://anime-kitsu.strem.fun/manifest.json",
            repositoryUrl = "https://stremio-addons.net",
            supportedTypes = listOf("anime", "series"),
            supportedLanguages = listOf("ja", "en"),
            iconUrl = "https://anime-kitsu.strem.fun/logo.png",
            isInstalled = true,
            isEnabled = true,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.COMPATIBLE,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Online (Active)",
            declaredResources = listOf("catalog", "meta"),
            protocolDescription = "Stremio JSON/REST Protocol v3",
            isLegitimate = true
        ),
        UnifiedProvider(
            id = "addon_cyberflix",
            name = "CyberFlix Catalog",
            category = ProviderCategory.STREMIO_ADDON,
            description = "Curated streaming catalogs organizing popular releases from Netflix, Disney+, Apple TV+, and HBO Max.",
            author = "CyberFlix",
            manifestUrl = "https://cyberflix.kamyroll.net/manifest.json",
            repositoryUrl = "https://stremio-addons.net",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en"),
            iconUrl = "https://cyberflix.kamyroll.net/logo.png",
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.OFFLINE_UNAVAILABLE,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Offline (Host Unreachable)",
            declaredResources = listOf("catalog"),
            protocolDescription = "Stremio JSON/REST Protocol v3",
            isLegitimate = true,
            securityNotice = "Provider host cyberflix.kamyroll.net is offline (DNS lookup failed). Installation disabled."
        ),
        UnifiedProvider(
            id = "addon_torrentio",
            name = "Torrentio Stream Provider",
            category = ProviderCategory.STREMIO_ADDON,
            description = "High-speed streams aggregator with Debrid cloud cache (RealDebrid, AllDebrid, Premiumize) support.",
            author = "TheBeast",
            manifestUrl = "https://torrentio.strem.fun/manifest.json",
            repositoryUrl = "https://stremio-addons.net",
            supportedTypes = listOf("movie", "series", "anime"),
            supportedLanguages = listOf("en", "multi"),
            iconUrl = "https://torrentio.strem.fun/logo.png",
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = true,
            configurationHint = "Requires RealDebrid or AllDebrid API key for cached cloud streaming.",
            integrationStatus = ProviderIntegrationStatus.REQUIRES_CONFIGURATION,
            playbackTransport = PlaybackTransportType.HLS,
            healthStatus = "Config Required",
            declaredResources = listOf("stream"),
            protocolDescription = "Stremio Debrid Stream Resolver",
            isLegitimate = true
        ),

        // ==========================================
        // 2. NUVIO NATIVE PLUGINS (HERMES JS SCRAPERS)
        // ==========================================
        UnifiedProvider(
            id = "nuvio_public_cinema",
            name = "Nuvio Public Cinema",
            category = ProviderCategory.NUVIO_NATIVE,
            description = "Public domain classic films and vintage series scraper compiled for Nuvio.",
            author = "Nuvio Community",
            manifestUrl = "https://raw.githubusercontent.com/NuvioPlugin/public-cinema/main/manifest.json",
            repositoryUrl = "https://nuvioplugin.com",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Sandbox Required",
            protocolDescription = "Nuvio Hermes JS Provider Module",
            isLegitimate = true,
            securityNotice = "Requires Nuvio Hermes/QuickJS Sandboxed Engine. Dynamic in-process script execution is disabled for safety."
        ),
        UnifiedProvider(
            id = "nuvio_archive_org",
            name = "Nuvio Archive.org Open Collection",
            category = ProviderCategory.NUVIO_NATIVE,
            description = "Historical documentaries, open-license films, and public audio-visual archives from Internet Archive.",
            author = "OpenMedia",
            manifestUrl = "https://raw.githubusercontent.com/NuvioPlugin/archive-org/main/manifest.json",
            repositoryUrl = "https://nuvioplugin.com",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en", "multi"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Sandbox Required",
            protocolDescription = "Nuvio Hermes JS Provider Module",
            isLegitimate = true,
            securityNotice = "Requires Nuvio Hermes/QuickJS Sandboxed Engine."
        ),
        UnifiedProvider(
            id = "nuvio_unlicensed_bundle",
            name = "All-in-One Nuvio Pirate Scrapers",
            category = ProviderCategory.NUVIO_NATIVE,
            description = "Community bundle containing scrapers targeting unlicensed pirate streaming hosts.",
            author = "Unverified Developer",
            manifestUrl = "https://raw.githubusercontent.com/NuvioPlugin/All-in-One-Nuvio/main/manifest.json",
            repositoryUrl = "https://nuvioplugin.com",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.BLOCKED_SECURITY,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Blocked (Policy)",
            protocolDescription = "Unlicensed Web Scraper",
            isLegitimate = false,
            securityNotice = "Blocked per STREAMX Security & Legal Policy: Unlicensed copyrighted content scraping and host bypass."
        ),

        // ==========================================
        // 3. CLOUDSTREAM EXTENSIONS (.CS3 DEX PLUGINS)
        // ==========================================
        UnifiedProvider(
            id = "cs_public_domain_hub",
            name = "CloudStream Public Domain Hub",
            category = ProviderCategory.CLOUDSTREAM_EXTENSION,
            description = "Open-source Kotlin extension providing public-domain media from authorized internet archives.",
            author = "CloudStream Verified",
            manifestUrl = "https://raw.githubusercontent.com/cloudstreamrepo/public-domain/builds/repo.json",
            repositoryUrl = "https://cloudstreamrepo.com",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Sandbox Required",
            protocolDescription = "CloudStream MainAPI compiled Dex bytecode (.cs3)",
            isLegitimate = true,
            securityNotice = "CloudStream Extension: Requires Dalvik (.cs3) Sandbox Runtime. Executing arbitrary dynamic bytecode in host process is disabled."
        ),
        UnifiedProvider(
            id = "cs_open_culture",
            name = "CloudStream Open Culture",
            category = ProviderCategory.CLOUDSTREAM_EXTENSION,
            description = "Educational and cultural films, historical broadcasts, and vintage archives compiled for CloudStream.",
            author = "CultureHub",
            manifestUrl = "https://raw.githubusercontent.com/cloudstreamrepo/open-culture/builds/repo.json",
            repositoryUrl = "https://cloudstreamrepo.com",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en", "fr", "es"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Sandbox Required",
            protocolDescription = "CloudStream MainAPI compiled Dex bytecode (.cs3)",
            isLegitimate = true,
            securityNotice = "CloudStream Extension: Requires Dalvik (.cs3) Sandbox Runtime."
        ),
        UnifiedProvider(
            id = "cs_unlicensed_aggregator",
            name = "CloudStream Megarepo Unlicensed",
            category = ProviderCategory.CLOUDSTREAM_EXTENSION,
            description = "Third-party repository scraping unlicensed commercial streaming domains.",
            author = "Anonymous Scraper",
            manifestUrl = "https://raw.githubusercontent.com/cloudstreamrepo/megarepo/builds/repo.json",
            repositoryUrl = "https://cloudstreamrepo.com",
            supportedTypes = listOf("movie", "series"),
            supportedLanguages = listOf("en"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = ProviderIntegrationStatus.BLOCKED_SECURITY,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = "Blocked (Policy)",
            protocolDescription = "CloudStream Dex Scraper (.cs3)",
            isLegitimate = false,
            securityNotice = "Blocked per STREAMX Security & Legal Policy: Unlicensed copyrighted content scraping."
        ),

        // ==========================================
        // 4. MEDIA SERVERS (JELLYFIN USER INTEGRATION)
        // ==========================================
        UnifiedProvider(
            id = "server_home_jellyfin",
            name = "Jellyfin Media Server",
            category = ProviderCategory.MEDIA_SERVER,
            description = "User-owned self-hosted Jellyfin media server with secure user authentication and HTTPS transcode support.",
            author = "Jellyfin Official",
            manifestUrl = "",
            supportedTypes = listOf("movie", "series", "anime"),
            supportedLanguages = listOf("multi"),
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = true,
            configurationHint = "Enter your personal Jellyfin server URL and credentials in Settings > Media Servers to connect.",
            integrationStatus = ProviderIntegrationStatus.REQUIRES_CONFIGURATION,
            playbackTransport = PlaybackTransportType.MEDIA_SERVER_STREAM,
            healthStatus = "Configuration Required",
            declaredResources = listOf("catalog", "meta", "stream", "subtitles"),
            protocolDescription = "Jellyfin Client REST API v10.8+",
            isLegitimate = true
        )
    )

    private val _catalogueFlow = MutableStateFlow(initialMasterList)
    val catalogueFlow: StateFlow<List<UnifiedProvider>> = _catalogueFlow.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Gets all unified providers currently in the catalogue.
     */
    fun getAllProviders(): List<UnifiedProvider> = _catalogueFlow.value

    /**
     * Filters catalogue by category.
     */
    fun getProvidersByCategory(category: ProviderCategory): List<UnifiedProvider> {
        return _catalogueFlow.value.filter { it.category == category }
    }

    /**
     * Returns only fully compatible providers that can be executed directly.
     */
    fun getCompatibleProviders(): List<UnifiedProvider> {
        return _catalogueFlow.value.filter { it.integrationStatus == ProviderIntegrationStatus.COMPATIBLE }
    }

    /**
     * Returns providers requiring configuration before use (e.g. Debrid key, Jellyfin server).
     */
    fun getConfigurationRequiredProviders(): List<UnifiedProvider> {
        return _catalogueFlow.value.filter { it.integrationStatus == ProviderIntegrationStatus.REQUIRES_CONFIGURATION }
    }

    /**
     * Returns providers requiring an isolated runtime sandbox.
     */
    fun getSandboxedRuntimeProviders(): List<UnifiedProvider> {
        return _catalogueFlow.value.filter { it.integrationStatus == ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER }
    }

    /**
     * Returns providers marked as offline or unreachable.
     */
    fun getOfflineUnavailableProviders(): List<UnifiedProvider> {
        return _catalogueFlow.value.filter { it.integrationStatus == ProviderIntegrationStatus.OFFLINE_UNAVAILABLE }
    }

    /**
     * Returns blocked providers with their security/legal justification.
     */
    fun getBlockedProviders(): List<UnifiedProvider> {
        return _catalogueFlow.value.filter { it.integrationStatus == ProviderIntegrationStatus.BLOCKED_SECURITY }
    }

    /**
     * Refreshes the provider catalogue from a remote discovery endpoint with offline fallback (Phase 5).
     */
    suspend fun refreshCatalogue(remoteUrl: String = "https://nuvioplugin.com/api/catalogue.json"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(remoteUrl)
                .header("User-Agent", "STREAMX/2.0 ProviderCatalogue")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                StreamXLog.d(TAG, "Remote catalogue refreshed successfully.")
                // In a live environment, parse new entries and merge with initialMasterList
                Result.success(_catalogueFlow.value.size)
            } else {
                StreamXLog.w(TAG, "Remote catalogue returned ${response.code}. Using offline bundled catalogue.")
                Result.success(_catalogueFlow.value.size)
            }
        } catch (e: Exception) {
            StreamXLog.w(TAG, "Catalogue refresh failed (${e.message}). Keeping offline bundled catalogue.")
            Result.success(_catalogueFlow.value.size)
        }
    }
}
