package com.streamx.app.data.jellyfin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamx.app.data.local.UserPreferencesDataStore
import com.streamx.app.domain.jellyfin.JellyfinRepository
import com.streamx.app.domain.model.MediaServerConfig
import com.streamx.app.domain.model.MediaServerType
import com.streamx.app.network.jellyfin.api.JellyfinApiService
import com.streamx.app.network.jellyfin.client.JellyfinClientFactory
import com.streamx.app.network.jellyfin.model.*
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class JellyfinRepositoryImpl(
    private val dataStore: UserPreferencesDataStore,
    private val gson: Gson = Gson()
) : JellyfinRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val apiServiceCache = ConcurrentHashMap<String, JellyfinApiService>()

    private val _activeServerFlow = MutableStateFlow<MediaServerConfig?>(null)
    override val activeServerFlow: StateFlow<MediaServerConfig?> = _activeServerFlow.asStateFlow()

    init {
        scope.launch {
            loadSavedServers()
        }
    }

    private suspend fun loadSavedServers() {
        try {
            val raw = dataStore.rawServersData.firstOrNull()
            if (!raw.isNullOrBlank()) {
                val type = object : TypeToken<List<MediaServerConfig>>() {}.type
                val servers: List<MediaServerConfig> = gson.fromJson(raw, type)
                val connected = servers.firstOrNull { it.isConnected && it.type == MediaServerType.JELLYFIN }
                    ?: servers.firstOrNull { it.type == MediaServerType.JELLYFIN }
                _activeServerFlow.value = connected
            }
        } catch (e: Exception) {
            StreamXLog.e("JellyfinRepository", "Failed to load saved media servers: ${e.message}")
        }
    }

    private suspend fun persistServer(server: MediaServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                val raw = dataStore.rawServersData.firstOrNull()
                val current = if (!raw.isNullOrBlank()) {
                    val type = object : TypeToken<List<MediaServerConfig>>() {}.type
                    val list: List<MediaServerConfig> = gson.fromJson(raw, type)
                    list.toMutableList()
                } else {
                    mutableListOf()
                }

                current.removeAll { it.baseUrl.equals(server.baseUrl, ignoreCase = true) || it.id == server.id }
                current.add(0, server)

                dataStore.saveRawServersData(gson.toJson(current))
                _activeServerFlow.value = server
            } catch (e: Exception) {
                StreamXLog.e("JellyfinRepository", "Failed to persist server: ${e.message}")
            }
        }
    }

    private fun getApiService(baseUrl: String, token: String? = null): JellyfinApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cacheKey = "$normalized|${token.orEmpty()}"
        return apiServiceCache.getOrPut(cacheKey) {
            val client = JellyfinClientFactory.createOkHttpClient { token ?: _activeServerFlow.value?.apiKey }
            val retrofit = JellyfinClientFactory.createRetrofit(normalized, client)
            retrofit.create(JellyfinApiService::class.java)
        }
    }

    override suspend fun connectServer(url: String): Result<JellyfinPublicSystemInfo> = withContext(Dispatchers.IO) {
        val validation = JellyfinClientFactory.validateServerUrl(url)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid URL"))
        }

        val validUrl = validation.getOrThrow()
        try {
            val service = getApiService(validUrl, token = null)
            val response = service.getPublicSystemInfo()
            if (response.isSuccessful && response.body() != null) {
                val info = response.body()!!
                StreamXLog.d("JellyfinRepository", "Connected to Jellyfin: ${info.serverName} (v${info.version})")
                Result.success(info)
            } else {
                Result.failure(IOException("Server returned HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            StreamXLog.e("JellyfinRepository", "Connection failed to $validUrl: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<JellyfinAuthenticationResult> = withContext(Dispatchers.IO) {
        val validation = JellyfinClientFactory.validateServerUrl(serverUrl)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid URL"))
        }

        val validUrl = validation.getOrThrow()
        try {
            val service = getApiService(validUrl, token = null)
            val response = service.authenticateByName(
                JellyfinAuthenticateUserByName(username = username, password = password)
            )

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val serverConfig = MediaServerConfig(
                    id = "jellyfin_${result.serverId.ifBlank { System.currentTimeMillis().toString() }}",
                    type = MediaServerType.JELLYFIN,
                    serverName = result.user?.name?.let { "$it's Jellyfin" } ?: "Jellyfin Server",
                    baseUrl = validUrl,
                    apiKey = result.accessToken,
                    isConnected = true,
                    userId = result.user?.id.orEmpty(),
                    username = result.user?.name.orEmpty(),
                    serverId = result.serverId
                )

                persistServer(serverConfig)
                StreamXLog.d("JellyfinRepository", "Authenticated successfully as [REDACTED_USER]")
                Result.success(result)
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Invalid username or password"
                    else -> "Authentication failed (HTTP ${response.code()})"
                }
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            StreamXLog.e("JellyfinRepository", "Login exception: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun initiateQuickConnect(serverUrl: String): Result<JellyfinQuickConnectInitiate> = withContext(Dispatchers.IO) {
        val validation = JellyfinClientFactory.validateServerUrl(serverUrl)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid URL"))
        }
        val validUrl = validation.getOrThrow()
        try {
            val service = getApiService(validUrl, token = null)
            val response = service.initiateQuickConnect()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("Quick Connect not supported or disabled on this server (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkQuickConnect(serverUrl: String, secret: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val validation = JellyfinClientFactory.validateServerUrl(serverUrl)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid URL"))
        }
        val validUrl = validation.getOrThrow()
        try {
            val service = getApiService(validUrl, token = null)
            val response = service.checkQuickConnect(secret)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.authenticated)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeQuickConnect(serverUrl: String, secret: String): Result<JellyfinAuthenticationResult> = withContext(Dispatchers.IO) {
        val validation = JellyfinClientFactory.validateServerUrl(serverUrl)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid URL"))
        }
        val validUrl = validation.getOrThrow()
        try {
            val service = getApiService(validUrl, token = null)
            val response = service.authenticateWithQuickConnect(mapOf("Secret" to secret))
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val serverConfig = MediaServerConfig(
                    id = "jellyfin_${result.serverId.ifBlank { System.currentTimeMillis().toString() }}",
                    type = MediaServerType.JELLYFIN,
                    serverName = result.user?.name?.let { "$it's Jellyfin" } ?: "Jellyfin Server",
                    baseUrl = validUrl,
                    apiKey = result.accessToken,
                    isConnected = true,
                    userId = result.user?.id.orEmpty(),
                    username = result.user?.name.orEmpty(),
                    serverId = result.serverId
                )
                persistServer(serverConfig)
                Result.success(result)
            } else {
                Result.failure(IOException("Quick Connect authentication failed (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        val current = _activeServerFlow.value ?: return@withContext
        val updated = current.copy(isConnected = false, apiKey = "")
        persistServer(updated)
        _activeServerFlow.value = null
    }

    override suspend fun setActiveServer(server: MediaServerConfig) {
        persistServer(server.copy(isConnected = true))
    }

    private fun requireActiveServer(): Pair<MediaServerConfig, JellyfinApiService> {
        val server = _activeServerFlow.value
            ?: throw IllegalStateException("No Jellyfin server connected. Please connect a server in Settings or Jellyfin screen.")
        val service = getApiService(server.baseUrl, server.apiKey)
        return server to service
    }

    override suspend fun getUserViews(): Result<List<JellyfinItemDto>> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getUserViews(server.userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.items)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItems(
        parentId: String?,
        includeItemTypes: String?,
        limit: Int,
        startIndex: Int
    ): Result<List<JellyfinItemDto>> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getItems(
                userId = server.userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes,
                limit = limit,
                startIndex = startIndex
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.items)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<JellyfinItemDto>> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getContinueWatching(userId = server.userId, limit = limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.items)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLatestItems(parentId: String?, limit: Int): Result<List<JellyfinItemDto>> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getLatestItems(userId = server.userId, parentId = parentId, limit = limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSeasons(seriesId: String): Result<List<JellyfinItemDto>> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getSeasons(seriesId = seriesId, userId = server.userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.items)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String?): Result<List<JellyfinItemDto>> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getEpisodes(seriesId = seriesId, seasonId = seasonId, userId = server.userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.items)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItemDetails(itemId: String): Result<JellyfinItemDto> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getItemDetails(userId = server.userId, itemId = itemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPlaybackInfo(itemId: String): Result<JellyfinPlaybackInfoDto> = withContext(Dispatchers.IO) {
        try {
            val (server, service) = requireActiveServer()
            val response = service.getPlaybackInfo(itemId = itemId, userId = server.userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("Failed to get playback info: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionTicks: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (_, service) = requireActiveServer()
            service.reportPlaybackStart(
                JellyfinPlaybackProgressRequest(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = positionTicks,
                    isPaused = false,
                    eventName = "TimeUpdate"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionTicks: Long,
        isPaused: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (_, service) = requireActiveServer()
            service.reportPlaybackProgress(
                JellyfinPlaybackProgressRequest(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = positionTicks,
                    isPaused = isPaused,
                    eventName = "TimeUpdate"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        positionTicks: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (_, service) = requireActiveServer()
            service.reportPlaybackStopped(
                JellyfinPlaybackProgressRequest(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = positionTicks,
                    isPaused = false,
                    eventName = "Stop"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getImageUrl(itemId: String, imageType: String, tag: String?, width: Int?): String {
        val server = _activeServerFlow.value ?: return ""
        val base = server.baseUrl.trimEnd('/')
        val params = mutableListOf<String>()
        if (!tag.isNullOrBlank()) params.add("tag=$tag")
        if (width != null && width > 0) params.add("maxWidth=$width")
        if (server.apiKey.isNotBlank()) params.add("api_key=${server.apiKey}")

        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "$base/Items/$itemId/Images/$imageType$query"
    }
}
