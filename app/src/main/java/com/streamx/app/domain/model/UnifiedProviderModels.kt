package com.streamx.app.domain.model

import com.google.gson.annotations.SerializedName

/**
 * High-level category of streaming integration.
 */
enum class ProviderCategory(val displayName: String) {
    @SerializedName("stremio")
    STREMIO_ADDON("Stremio Add-on"),

    @SerializedName("nuvio")
    NUVIO_NATIVE("Nuvio Native Plugin"),

    @SerializedName("cloudstream")
    CLOUDSTREAM_EXTENSION("CloudStream Extension"),

    @SerializedName("media_server")
    MEDIA_SERVER("Media Server")
}

/**
 * Compatibility and readiness state of a provider within STREAMX.
 */
enum class ProviderIntegrationStatus(val label: String) {
    COMPATIBLE("Fully Compatible"),
    REQUIRES_CONFIGURATION("Requires Configuration"),
    REQUIRES_RUNTIME_ADAPTER("Requires Runtime Sandbox"),
    UNSUPPORTED("Unsupported Architecture"),
    OFFLINE_UNAVAILABLE("Server Offline (Host Unreachable)"),
    BLOCKED_SECURITY("Blocked (Security/Legal Policy)")
}

/**
 * Underlying media transport protocol used by the provider.
 */
enum class PlaybackTransportType(val label: String) {
    DIRECT_HTTP("Direct HTTP/HTTPS"),
    HLS("Apple HLS (.m3u8)"),
    DASH("MPEG-DASH (.mpd)"),
    P2P_TORRENT("BitTorrent / P2P Engine"),
    MEDIA_SERVER_STREAM("Jellyfin / Emby Stream"),
    UNRESOLVED("Host-Resolved")
}

/**
 * Unified representation of any provider across Stremio, Nuvio, CloudStream, and Media Servers.
 */
data class UnifiedProvider(
    val id: String,
    val name: String,
    val category: ProviderCategory,
    val description: String,
    val author: String,
    val version: String = "1.0.0",
    val manifestUrl: String,
    val repositoryUrl: String? = null,
    val supportedTypes: List<String> = listOf("movie", "series"),
    val supportedLanguages: List<String> = listOf("en"),
    val iconUrl: String? = null,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val requiresConfiguration: Boolean = false,
    val configurationHint: String? = null,
    val integrationStatus: ProviderIntegrationStatus = ProviderIntegrationStatus.COMPATIBLE,
    val playbackTransport: PlaybackTransportType = PlaybackTransportType.DIRECT_HTTP,
    val healthStatus: String = "Online",
    val lastSuccessfulCheck: Long = System.currentTimeMillis(),
    val declaredResources: List<String> = emptyList(),
    val protocolDescription: String = "Stremio JSON/REST Protocol",
    val isLegitimate: Boolean = true,
    val securityNotice: String? = null
) {
    val isStreamProvider: Boolean
        get() = declaredResources.any { it.equals("stream", ignoreCase = true) } ||
                category == ProviderCategory.MEDIA_SERVER

    val isMetadataOnly: Boolean
        get() = !isStreamProvider && declaredResources.any { it.equals("meta", ignoreCase = true) || it.equals("catalog", ignoreCase = true) }

    val isSubtitlesOnly: Boolean
        get() = !isStreamProvider && declaredResources.all { it.equals("subtitles", ignoreCase = true) }

    val isExecutablePlugin: Boolean
        get() = category == ProviderCategory.NUVIO_NATIVE || category == ProviderCategory.CLOUDSTREAM_EXTENSION
}

/**
 * Nuvio Native Plugin Repository Manifest schema (nuvioplugin.com / GitHub).
 */
data class NuvioRepoManifest(
    @SerializedName("name") val name: String = "",
    @SerializedName("author") val author: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("plugins") val plugins: List<NuvioPluginDefinition> = emptyList()
)

data class NuvioPluginDefinition(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String = "1.0.0",
    @SerializedName("description") val description: String = "",
    @SerializedName("author") val author: String = "Nuvio Community",
    @SerializedName("scriptUrl") val scriptUrl: String? = null,
    @SerializedName("types") val types: List<String> = listOf("movie", "series"),
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("languages") val languages: List<String> = listOf("en")
)

/**
 * CloudStream Extension Repository Manifest schema (cloudstreamrepo.com / GitHub).
 */
data class CloudStreamRepoManifest(
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("manifestVersion") val manifestVersion: Int = 1,
    @SerializedName("pluginLists") val pluginLists: List<String> = emptyList()
)

data class CloudStreamPluginItem(
    @SerializedName("name") val name: String,
    @SerializedName("internalName") val internalName: String? = null,
    @SerializedName("pluginClassName") val pluginClassName: String? = null,
    @SerializedName("version") val version: Int = 1,
    @SerializedName("url") val url: String? = null, // Direct .cs3 bytecode URL
    @SerializedName("types") val types: List<String> = emptyList(),
    @SerializedName("language") val language: String = "en",
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("authors") val authors: List<String> = emptyList(),
    @SerializedName("description") val description: String? = null,
    @SerializedName("fileSize") val fileSize: Long = 0L,
    @SerializedName("status") val status: Int = 1
)
