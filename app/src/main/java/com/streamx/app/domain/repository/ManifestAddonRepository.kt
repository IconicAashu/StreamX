package com.streamx.app.domain.repository

import com.streamx.app.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface ManifestAddonRepository {
    val addonsFlow: StateFlow<List<AddonManifest>>
    val installedAddonsFlow: StateFlow<List<InstalledAddon>>
    val mediaServersFlow: StateFlow<List<MediaServerConfig>>
    val customProvidersFlow: StateFlow<List<ProviderModule>>
    val addonCatalogsFlow: StateFlow<List<AddonCatalogSection>>

    suspend fun addAddon(name: String, manifestUrl: String, description: String = "")
    suspend fun installAddonFromUrl(manifestUrl: String): Result<InstalledAddon>
    suspend fun toggleAddon(id: String)
    suspend fun removeAddon(id: String)
    suspend fun reorderAddons(fromIndex: Int, toIndex: Int)
    suspend fun refreshAddon(id: String): Result<Unit>
    suspend fun refreshAddons()

    suspend fun saveMediaServer(server: MediaServerConfig)
    suspend fun removeMediaServer(id: String)
    suspend fun testConnection(server: MediaServerConfig): Boolean

    suspend fun toggleProvider(id: String)
    suspend fun getMediaItemById(id: String): MediaItem?
    suspend fun fetchAddonCatalogs(): List<AddonCatalogSection>
    suspend fun searchAddonCatalogs(query: String, type: String = "movie"): List<MediaItem>
    suspend fun getMediaMeta(type: String, id: String): StremioMetaDetail?
    suspend fun getStreamsForMedia(type: String, id: String): List<PlaybackSource>
    suspend fun getSubtitlesForMedia(type: String, id: String): List<SubtitleTrackOption>
}
