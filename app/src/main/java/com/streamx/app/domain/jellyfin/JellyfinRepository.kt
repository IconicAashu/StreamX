package com.streamx.app.domain.jellyfin

import com.streamx.app.domain.model.MediaServerConfig
import com.streamx.app.network.jellyfin.model.*
import kotlinx.coroutines.flow.StateFlow

interface JellyfinRepository {
    val activeServerFlow: StateFlow<MediaServerConfig?>

    suspend fun connectServer(url: String): Result<JellyfinPublicSystemInfo>
    suspend fun login(serverUrl: String, username: String, password: String): Result<JellyfinAuthenticationResult>
    suspend fun initiateQuickConnect(serverUrl: String): Result<JellyfinQuickConnectInitiate>
    suspend fun checkQuickConnect(serverUrl: String, secret: String): Result<Boolean>
    suspend fun completeQuickConnect(serverUrl: String, secret: String): Result<JellyfinAuthenticationResult>
    suspend fun logout()
    suspend fun setActiveServer(server: MediaServerConfig)

    suspend fun getUserViews(): Result<List<JellyfinItemDto>>
    suspend fun getItems(
        parentId: String? = null,
        includeItemTypes: String? = null,
        limit: Int = 50,
        startIndex: Int = 0
    ): Result<List<JellyfinItemDto>>
    suspend fun getContinueWatching(limit: Int = 20): Result<List<JellyfinItemDto>>
    suspend fun getLatestItems(parentId: String? = null, limit: Int = 20): Result<List<JellyfinItemDto>>
    suspend fun getSeasons(seriesId: String): Result<List<JellyfinItemDto>>
    suspend fun getEpisodes(seriesId: String, seasonId: String? = null): Result<List<JellyfinItemDto>>
    suspend fun getItemDetails(itemId: String): Result<JellyfinItemDto>

    suspend fun getPlaybackInfo(itemId: String): Result<JellyfinPlaybackInfoDto>
    suspend fun reportPlaybackStart(itemId: String, mediaSourceId: String?, playSessionId: String?, positionTicks: Long): Result<Unit>
    suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String?, playSessionId: String?, positionTicks: Long, isPaused: Boolean): Result<Unit>
    suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String?, playSessionId: String?, positionTicks: Long): Result<Unit>

    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null, width: Int? = null): String
}
