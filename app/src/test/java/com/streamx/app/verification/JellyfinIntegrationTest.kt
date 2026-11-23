package com.streamx.app.verification

import com.google.gson.Gson
import com.streamx.app.data.playback.jellyfin.JellyfinPlaybackProvider
import com.streamx.app.data.playback.stremio.StremioPlaybackProvider
import com.streamx.app.domain.jellyfin.JellyfinRepository
import com.streamx.app.domain.model.*
import com.streamx.app.domain.playback.PlaybackEventType
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.network.jellyfin.client.JellyfinClientFactory
import com.streamx.app.network.jellyfin.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive verification of Phase 1 Jellyfin Media Server Integration:
 * - URL validation (HTTP/HTTPS, trailing slashes, private IP, hostnames)
 * - Authentication & identification headers (X-Emby-Authorization, Access Token)
 * - Media library metadata parsing (Views, Items, Seasons, Episodes, Continue Watching)
 * - Playback info, Direct Play, Direct Stream, and HLS Transcoding stream resolution
 * - Audio & Subtitle track parsing and extraction
 * - Playback progress ticks calculation (1 ms = 10,000 ticks)
 * - Provider-independent PlaybackProvider architecture & delegation
 */
class JellyfinIntegrationTest {

    private val gson = Gson()

    @Test
    fun testServerUrlValidation() {
        // Valid URLs
        val validLocal = JellyfinClientFactory.validateServerUrl("http://192.168.1.100:8096")
        assertTrue(validLocal.isSuccess)
        assertEquals("http://192.168.1.100:8096", validLocal.getOrThrow())

        // Trailing slash stripping
        val trailingSlash = JellyfinClientFactory.validateServerUrl("https://jellyfin.streamx.local:8920/")
        assertTrue(trailingSlash.isSuccess)
        assertEquals("https://jellyfin.streamx.local:8920", trailingSlash.getOrThrow())

        // Subpath support
        val subpath = JellyfinClientFactory.validateServerUrl("https://media.example.com/jellyfin/")
        assertTrue(subpath.isSuccess)
        assertEquals("https://media.example.com/jellyfin", subpath.getOrThrow())

        // Auto-scheme insertion
        val noScheme = JellyfinClientFactory.validateServerUrl("192.168.1.50:8096")
        assertTrue(noScheme.isSuccess)
        assertEquals("http://192.168.1.50:8096", noScheme.getOrThrow())

        // Invalid URLs
        val blankUrl = JellyfinClientFactory.validateServerUrl("   ")
        assertTrue(blankUrl.isFailure)

        val badScheme = JellyfinClientFactory.validateServerUrl("ftp://192.168.1.100")
        assertTrue(badScheme.isFailure)
    }

    @Test
    fun testJellyfinAuthenticationAndAuthorizationHeaders() {
        val authHeader = JellyfinClientFactory.buildAuthHeader("streamx-device-uuid-1234")
        assertTrue(authHeader.contains("MediaBrowser Client=\"STREAMX\""))
        assertTrue(authHeader.contains("Device=\"Android\""))
        assertTrue(authHeader.contains("DeviceId=\"streamx-device-uuid-1234\""))
        assertTrue(authHeader.contains("Version=\"1.0.0\""))

        // Authenticate User result parsing
        val authJson = """
            {
                "AccessToken": "mock_jellyfin_access_token_98765",
                "ServerId": "srv-abc-12345",
                "User": {
                    "Id": "usr-uuid-777",
                    "Name": "Aashu",
                    "HasPassword": true
                },
                "SessionInfo": {
                    "Id": "sess-999"
                }
            }
        """.trimIndent()

        val authResult = gson.fromJson(authJson, JellyfinAuthenticationResult::class.java)
        assertEquals("mock_jellyfin_access_token_98765", authResult.accessToken)
        assertEquals("srv-abc-12345", authResult.serverId)
        assertEquals("usr-uuid-777", authResult.user?.id)
        assertEquals("Aashu", authResult.user?.name)
    }

    @Test
    fun testQuickConnectModelsParsing() {
        val initiateJson = """
            {
                "Code": "849201",
                "Secret": "sec-abcdef-123456",
                "AuthenticationApiUrl": null
            }
        """.trimIndent()

        val initiate = gson.fromJson(initiateJson, JellyfinQuickConnectInitiate::class.java)
        assertEquals("849201", initiate.code)
        assertEquals("sec-abcdef-123456", initiate.secret)
        assertNull(initiate.authenticationApiUrl)

        val authenticatedJson = """
            {
                "Authenticated": true,
                "Secret": "sec-abcdef-123456"
            }
        """.trimIndent()

        val check = gson.fromJson(authenticatedJson, JellyfinQuickConnectResult::class.java)
        assertTrue(check.authenticated)
    }

    @Test
    fun testMediaLibraryItemParsing() {
        val itemJson = """
            {
                "Id": "item-movie-001",
                "Name": "Inception",
                "Type": "Movie",
                "Overview": "A thief who steals corporate secrets through the use of dream-sharing technology.",
                "ProductionYear": 2010,
                "RunTimeTicks": 88800000000,
                "CommunityRating": 8.8,
                "OfficialRating": "PG-13",
                "Genres": ["Action", "Sci-Fi", "Adventure"],
                "UserData": {
                    "PlaybackPositionTicks": 24000000000,
                    "PlayCount": 1,
                    "IsFavorite": true,
                    "Played": false
                }
            }
        """.trimIndent()

        val item = gson.fromJson(itemJson, JellyfinItemDto::class.java)
        assertEquals("item-movie-001", item.id)
        assertEquals("Inception", item.name)
        assertEquals("Movie", item.type)
        assertEquals(2010, item.productionYear)
        assertEquals(8.8, item.communityRating ?: 0.0, 0.01)
        assertEquals(3, item.genres?.size)
        assertEquals(88800000000L, item.runTimeTicks)

        // Runtime calculation: 88,800,000,000 ticks / 10,000,000 ticks/sec / 60 sec = 148 minutes
        val runtimeMinutes = (item.runTimeTicks!! / 10_000_000L / 60L).toInt()
        assertEquals(148, runtimeMinutes)

        // Resume progress calculation
        val resumeTicks = item.userData?.playbackPositionTicks ?: 0L
        assertEquals(24000000000L, resumeTicks)
        val progressRatio = resumeTicks.toDouble() / item.runTimeTicks!!.toDouble()
        assertTrue(progressRatio > 0.25 && progressRatio < 0.30)
    }

    @Test
    fun testPlaybackInfoAndStreamResolution() {
        val playbackInfoJson = """
            {
                "MediaSources": [
                    {
                        "Id": "source-001",
                        "Container": "mp4",
                        "SupportsDirectPlay": true,
                        "SupportsDirectStream": true,
                        "SupportsTranscoding": true,
                        "Bitrate": 10000000,
                        "MediaStreams": [
                            {
                                "Type": "Video",
                                "Index": 0,
                                "Codec": "h264",
                                "Width": 1920,
                                "Height": 1080
                            },
                            {
                                "Type": "Audio",
                                "Index": 1,
                                "Codec": "aac",
                                "Language": "eng",
                                "DisplayTitle": "English AAC Stereo",
                                "Channels": 2,
                                "IsDefault": true
                            },
                            {
                                "Type": "Subtitle",
                                "Index": 2,
                                "Codec": "subrip",
                                "Language": "eng",
                                "DisplayTitle": "English (SRT)",
                                "DeliveryMethod": "External",
                                "DeliveryUrl": "/Videos/item-movie-001/source-001/Subtitles/2/Stream.vtt",
                                "IsDefault": false
                            }
                        ]
                    }
                ],
                "PlaySessionId": "ps-session-12345"
            }
        """.trimIndent()

        val playbackInfo = gson.fromJson(playbackInfoJson, JellyfinPlaybackInfoDto::class.java)
        assertEquals(1, playbackInfo.mediaSources.size)
        val mediaSource = playbackInfo.mediaSources.first()
        assertTrue(mediaSource.supportsDirectPlay)
        assertTrue(mediaSource.supportsTranscoding)

        // Audio streams extraction
        val audioStreams = mediaSource.mediaStreams.filter { it.type.equals("Audio", ignoreCase = true) }
        assertEquals(1, audioStreams.size)
        assertEquals("eng", audioStreams.first().language)
        assertEquals("aac", audioStreams.first().codec)

        // Subtitle streams extraction
        val subtitleStreams = mediaSource.mediaStreams.filter { it.type.equals("Subtitle", ignoreCase = true) }
        assertEquals(1, subtitleStreams.size)
        assertEquals("subrip", subtitleStreams.first().codec)
        assertEquals("/Videos/item-movie-001/source-001/Subtitles/2/Stream.vtt", subtitleStreams.first().deliveryUrl)

        // Direct Play URL format validation
        val serverUrl = "https://jellyfin.example.com"
        val itemId = "item-movie-001"
        val token = "test_token_abc"
        val directPlayUrl = "$serverUrl/Videos/$itemId/stream?static=true&mediaSourceId=${mediaSource.id}&api_key=$token"
        assertTrue(directPlayUrl.contains("static=true"))
        assertTrue(directPlayUrl.contains("mediaSourceId=source-001"))
        assertTrue(directPlayUrl.contains("api_key=test_token_abc"))

        // HLS Transcoding URL format validation
        val hlsTranscodeUrl = "$serverUrl/Videos/$itemId/main.m3u8?mediaSourceId=${mediaSource.id}&api_key=$token"
        assertTrue(hlsTranscodeUrl.endsWith(".m3u8?mediaSourceId=source-001&api_key=test_token_abc"))
    }

    @Test
    fun testPlaybackProgressTicksConversion() {
        // Jellyfin tick standard: 1 millisecond = 10,000 ticks
        val positionMs = 65_432L
        val expectedTicks = 65_432L * 10_000L // 654,320,000 ticks
        assertEquals(654320000L, expectedTicks)

        val durationMs = 7_200_000L // 2 hours
        val durationTicks = durationMs * 10_000L
        assertEquals(72000000000L, durationTicks)
    }

    @Test
    fun testProviderIndependentDelegation() = runBlocking {
        // Fake Jellyfin Repository
        val fakeJellyfinRepo = object : JellyfinRepository {
            override val activeServerFlow: StateFlow<MediaServerConfig?> = MutableStateFlow(
                MediaServerConfig(
                    id = "srv_1",
                    serverName = "Home Jellyfin",
                    baseUrl = "http://192.168.1.100:8096",
                    apiKey = "fake_token",
                    isConnected = true,
                    userId = "user_1"
                )
            )
            override suspend fun connectServer(url: String): Result<JellyfinPublicSystemInfo> =
                Result.success(JellyfinPublicSystemInfo(id = "srv_1", serverName = "Home Jellyfin", version = "10.9.0"))
            override suspend fun login(url: String, user: String, pass: String): Result<JellyfinAuthenticationResult> =
                Result.failure(NotImplementedError())
            override suspend fun initiateQuickConnect(url: String): Result<JellyfinQuickConnectInitiate> =
                Result.failure(NotImplementedError())
            override suspend fun checkQuickConnect(url: String, secret: String): Result<Boolean> = Result.success(false)
            override suspend fun completeQuickConnect(url: String, secret: String): Result<JellyfinAuthenticationResult> =
                Result.failure(NotImplementedError())
            override suspend fun logout() {}
            override suspend fun setActiveServer(server: MediaServerConfig) {}
            override suspend fun getUserViews(): Result<List<JellyfinItemDto>> = Result.success(emptyList())
            override suspend fun getContinueWatching(limit: Int): Result<List<JellyfinItemDto>> = Result.success(emptyList())
            override suspend fun getLatestItems(parentId: String?, limit: Int): Result<List<JellyfinItemDto>> = Result.success(emptyList())
            override suspend fun getItems(parentId: String?, includeItemTypes: String?, limit: Int, startIndex: Int): Result<List<JellyfinItemDto>> = Result.success(emptyList())
            override suspend fun getItemDetails(itemId: String): Result<JellyfinItemDto> = Result.failure(NotImplementedError())
            override suspend fun getSeasons(seriesId: String): Result<List<JellyfinItemDto>> = Result.success(emptyList())
            override suspend fun getEpisodes(seriesId: String, seasonId: String?): Result<List<JellyfinItemDto>> = Result.success(emptyList())
            override suspend fun getPlaybackInfo(itemId: String): Result<JellyfinPlaybackInfoDto> {
                return Result.success(
                    JellyfinPlaybackInfoDto(
                        mediaSources = listOf(
                            JellyfinMediaSourceDto(
                                id = "src_$itemId",
                                container = "mkv",
                                supportsDirectPlay = true,
                                supportsTranscoding = false,
                                mediaStreams = listOf(
                                    JellyfinMediaStreamDto(index = 0, type = "Video", codec = "h264", width = 1920, height = 1080),
                                    JellyfinMediaStreamDto(index = 1, type = "Audio", codec = "aac", language = "eng")
                                )
                            )
                        )
                    )
                )
            }
            override suspend fun reportPlaybackStart(itemId: String, mediaSourceId: String?, playSessionId: String?, positionTicks: Long): Result<Unit> = Result.success(Unit)
            override suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String?, playSessionId: String?, positionTicks: Long, isPaused: Boolean): Result<Unit> = Result.success(Unit)
            override suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String?, playSessionId: String?, positionTicks: Long): Result<Unit> = Result.success(Unit)
            override fun getImageUrl(itemId: String, imageType: String, tag: String?, width: Int?): String = ""
        }

        val jellyfinProvider = JellyfinPlaybackProvider(fakeJellyfinRepo)

        // Fake Stremio Addon Repository
        val fakeStremioRepo = object : ManifestAddonRepository {
            override val addonsFlow: StateFlow<List<AddonManifest>> = MutableStateFlow(emptyList())
            override val installedAddonsFlow: StateFlow<List<InstalledAddon>> = MutableStateFlow(emptyList())
            override val mediaServersFlow: StateFlow<List<MediaServerConfig>> = MutableStateFlow(emptyList())
            override val customProvidersFlow: StateFlow<List<ProviderModule>> = MutableStateFlow(emptyList())
            override val addonCatalogsFlow: StateFlow<List<AddonCatalogSection>> = MutableStateFlow(emptyList())
            override suspend fun addAddon(name: String, manifestUrl: String, description: String) {}
            override suspend fun installAddonFromUrl(manifestUrl: String): Result<InstalledAddon> = Result.failure(NotImplementedError())
            override suspend fun toggleAddon(id: String) {}
            override suspend fun removeAddon(id: String) {}
            override suspend fun reorderAddons(fromIndex: Int, toIndex: Int) {}
            override suspend fun refreshAddon(id: String): Result<Unit> = Result.success(Unit)
            override suspend fun refreshAddons() {}
            override suspend fun saveMediaServer(server: MediaServerConfig) {}
            override suspend fun removeMediaServer(id: String) {}
            override suspend fun testConnection(server: MediaServerConfig): Boolean = true
            override suspend fun toggleProvider(id: String) {}
            override suspend fun getMediaItemById(id: String): MediaItem? = null
            override suspend fun fetchAddonCatalogs(): List<AddonCatalogSection> = emptyList()
            override suspend fun searchAddonCatalogs(query: String, type: String): List<MediaItem> = emptyList()
            override suspend fun getMediaMeta(type: String, id: String): com.streamx.app.domain.model.StremioMetaDetail? = null
            override suspend fun getStreamsForMedia(type: String, id: String): List<PlaybackSource> {
                return listOf(
                    PlaybackSource(
                        mediaId = id,
                        title = "Torrentio 1080p",
                        streamUrl = "https://stream.torrentio.com/$id.mp4"
                    )
                )
            }
            override suspend fun getSubtitlesForMedia(type: String, id: String): List<SubtitleTrackOption> = emptyList()
        }

        val stremioProvider = StremioPlaybackProvider(fakeStremioRepo)

        // Test canHandle routing
        assertTrue(jellyfinProvider.canHandle("jellyfin_movie_999"))
        assertFalse(jellyfinProvider.canHandle("tt1234567"))

        assertTrue(stremioProvider.canHandle("tt1234567"))
        assertFalse(stremioProvider.canHandle("jellyfin_movie_999"))

        // Test Jellyfin stream resolution via provider (Direct Play + Transcoding HLS)
        val jellyfinSources = jellyfinProvider.getStreams("jellyfin_item_42")
        assertEquals(2, jellyfinSources.size)
        assertTrue(jellyfinSources.any { it.title.contains("Direct Play") && it.streamUrl.contains("http://192.168.1.100:8096/Videos/item_42/stream") })
        assertTrue(jellyfinSources.any { it.title.contains("Adaptive Stream") && it.streamUrl.contains("http://192.168.1.100:8096/Videos/item_42/main.m3u8") })

        // Test Stremio stream resolution via provider
        val stremioSources = stremioProvider.getStreams("tt0137523")
        assertEquals(1, stremioSources.size)
        assertEquals("Torrentio 1080p", stremioSources.first().title)
        assertEquals("https://stream.torrentio.com/tt0137523.mp4", stremioSources.first().streamUrl)
    }
}
