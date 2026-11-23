package com.streamx.app.domain.model

data class AddonManifest(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val isEnabled: Boolean = true,
    val version: String = "1.0.0",
    val description: String = ""
)

enum class MediaServerType(val displayName: String) {
    JELLYFIN("Jellyfin"),
    EMBY("Emby")
}

data class MediaServerConfig(
    val id: String,
    val type: MediaServerType = MediaServerType.JELLYFIN,
    val serverName: String,
    val baseUrl: String,
    val apiKey: String,
    val isConnected: Boolean = false,
    val userId: String = "",
    val username: String = "",
    val serverId: String = ""
)

data class ProviderModule(
    val id: String,
    val name: String,
    val providerId: String,
    val isEnabled: Boolean = true,
    val description: String = "",
    val isAuthorized: Boolean = true
)

data class AddonCatalogSection(
    val addonId: String,
    val addonName: String,
    val title: String,
    val items: List<MediaItem>,
    val catalogId: String = "",
    val type: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
