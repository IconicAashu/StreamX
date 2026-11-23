package com.streamx.app.domain.model

/**
 * Centrally managed, built-in catalogue of verified Stremio-compatible add-ons.
 * Guarantees that new users do not need to manually discover or enter manifest URLs,
 * while ensuring only authorized and verified providers are packaged with STREAMX.
 */
object BuiltInAddonCatalogue {

    val curatedCatalogue: List<CuratedProvider> = listOf(
        CuratedProvider(
            id = "addon_cinemeta",
            name = "Cinemeta Official",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            description = "Official Stremio metadata provider for Movies, Series, and Anime with IMDb/TMDB IDs, posters, and cast.",
            author = "Stremio Official",
            supportedTypes = listOf("movie", "series", "anime"),
            declaredResources = listOf("catalog", "meta"),
            iconUrl = "https://v3-cinemeta.strem.io/logo.png",
            requiresConfiguration = false,
            defaultInstalled = true,
            defaultEnabled = true
        ),
        CuratedProvider(
            id = "org.stremio.pubdomainmovies",
            name = "Public Domain Movies",
            manifestUrl = "https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json",
            description = "Classic cinema, vintage animation, and historical films in the public domain with direct streaming links.",
            author = "Community Verified",
            supportedTypes = listOf("movie"),
            declaredResources = listOf("catalog", "stream"),
            iconUrl = "https://johnserv531.github.io/gh-images/1586344884.jpg",
            requiresConfiguration = false,
            defaultInstalled = true,
            defaultEnabled = true
        ),
        CuratedProvider(
            id = "addon_open_subtitles",
            name = "OpenSubtitles v3",
            manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
            description = "Official multi-language subtitle streams for movies and TV series powered by the OpenSubtitles.org community.",
            author = "OpenSubtitles Official",
            supportedTypes = listOf("movie", "series", "anime"),
            declaredResources = listOf("subtitles"),
            iconUrl = "https://opensubtitles-v3.strem.io/logo.png",
            requiresConfiguration = false,
            defaultInstalled = true,
            defaultEnabled = true
        ),
        CuratedProvider(
            id = "addon_anime_kitsu",
            name = "Kitsu Anime Catalog",
            manifestUrl = "https://anime-kitsu.strem.fun/manifest.json",
            description = "Extensive Japanese anime catalog with trending lists, franchise chronologies, and episode guides.",
            author = "Kitsu Community",
            supportedTypes = listOf("anime", "series"),
            declaredResources = listOf("catalog", "meta"),
            iconUrl = "https://anime-kitsu.strem.fun/logo.png",
            requiresConfiguration = false,
            defaultInstalled = true,
            defaultEnabled = true
        ),
        CuratedProvider(
            id = "addon_torrentio",
            name = "Torrentio Stream Provider",
            manifestUrl = "https://torrentio.strem.fun/manifest.json",
            description = "High-speed streams aggregator with Debrid cloud cache (RealDebrid, AllDebrid, Premiumize) support.",
            author = "TheBeast",
            supportedTypes = listOf("movie", "series", "anime"),
            declaredResources = listOf("stream"),
            iconUrl = "https://torrentio.strem.fun/logo.png",
            requiresConfiguration = true,
            configurationHint = "Requires RealDebrid or AllDebrid API key for cached cloud streaming.",
            defaultInstalled = false,
            defaultEnabled = false
        ),
        CuratedProvider(
            id = "addon_cyberflix",
            name = "CyberFlix Catalog",
            manifestUrl = "https://cyberflix.kamyroll.net/manifest.json",
            description = "Curated streaming catalogs from Netflix, Disney+, Apple TV+, and HBO Max.",
            author = "CyberFlix",
            supportedTypes = listOf("movie", "series"),
            declaredResources = listOf("catalog"),
            iconUrl = "https://cyberflix.kamyroll.net/logo.png",
            requiresConfiguration = false,
            defaultInstalled = false,
            defaultEnabled = false,
            isAvailable = false,
            availabilityNotice = "Host server offline: cyberflix.kamyroll.net is unreachable."
        )
    )

    fun getDefaultInstalledAddons(): List<InstalledAddon> {
        return curatedCatalogue
            .filter { it.defaultInstalled }
            .mapIndexed { index, provider ->
                InstalledAddon(
                    id = provider.id,
                    name = provider.name,
                    manifestUrl = provider.manifestUrl,
                    baseUrl = provider.manifestUrl.substringBeforeLast("/manifest.json"),
                    isEnabled = provider.defaultEnabled,
                    version = "1.0.0",
                    description = provider.description,
                    logo = provider.iconUrl,
                    resources = provider.declaredResources.map { StremioResourceDefinition(name = it) },
                    types = provider.supportedTypes,
                    idPrefixes = if (provider.supportedTypes.contains("anime")) listOf("tt", "kitsu") else listOf("tt"),
                    orderIndex = index,
                    status = if (provider.requiresConfiguration) AddonStatus.NEEDS_CONFIGURATION else AddonStatus.READY
                )
            }
    }
}

data class CuratedProvider(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val description: String,
    val author: String,
    val supportedTypes: List<String>,
    val declaredResources: List<String>,
    val iconUrl: String? = null,
    val requiresConfiguration: Boolean = false,
    val configurationHint: String? = null,
    val defaultInstalled: Boolean = false,
    val defaultEnabled: Boolean = false,
    val isAvailable: Boolean = true,
    val availabilityNotice: String? = null
) {
    val isStreamProvider: Boolean
        get() = declaredResources.any { it.equals("stream", ignoreCase = true) }

    val isMetadataOnly: Boolean
        get() = !isStreamProvider && declaredResources.any { it.equals("meta", ignoreCase = true) || it.equals("catalog", ignoreCase = true) }

    val isSubtitlesOnly: Boolean
        get() = !isStreamProvider && declaredResources.all { it.equals("subtitles", ignoreCase = true) }
}
