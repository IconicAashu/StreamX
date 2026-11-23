package com.streamx.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamx.app.data.local.UserPreferencesDataStore
import com.streamx.app.domain.model.*
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.network.addon.AddonSecurityValidator
import com.streamx.app.network.addon.StremioAddonClient
import com.streamx.app.utils.ContentIdResolver
import com.streamx.app.utils.StreamMetadataParser
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ManifestAddonRepositoryImpl(
    private val dataStore: UserPreferencesDataStore,
    private val addonClient: StremioAddonClient = StremioAddonClient()
) : ManifestAddonRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson: Gson = addonClient.gson

    private val defaultInstalledAddons = BuiltInAddonCatalogue.getDefaultInstalledAddons()

    private val defaultServers: List<MediaServerConfig> = emptyList()

    private val defaultProviders = listOf(
        ProviderModule(
            id = "prov_trailer_resolver",
            name = "Official Trailer & Clip Resolver",
            providerId = "com.streamx.provider.trailers",
            isEnabled = true,
            description = "Resolves high-definition 4K official trailers from studio CDNs."
        ),
        ProviderModule(
            id = "prov_drm_clearlead",
            name = "ClearKey Media3 DRM Slot",
            providerId = "com.streamx.provider.clearkey",
            isEnabled = false,
            description = "Modular ClearKey DRM decrypter slot compliant with AndroidX Media3."
        )
    )

    private val _installedAddonsFlow = MutableStateFlow<List<InstalledAddon>>(defaultInstalledAddons)
    override val installedAddonsFlow: StateFlow<List<InstalledAddon>> = _installedAddonsFlow.asStateFlow()

    private val _addonsFlow = MutableStateFlow<List<AddonManifest>>(
        defaultInstalledAddons.map { it.toAddonManifest() }
    )
    override val addonsFlow: StateFlow<List<AddonManifest>> = _addonsFlow.asStateFlow()

    private val _mediaServersFlow = MutableStateFlow<List<MediaServerConfig>>(defaultServers)
    override val mediaServersFlow: StateFlow<List<MediaServerConfig>> = _mediaServersFlow.asStateFlow()

    private val _customProvidersFlow = MutableStateFlow<List<ProviderModule>>(defaultProviders)
    override val customProvidersFlow: StateFlow<List<ProviderModule>> = _customProvidersFlow.asStateFlow()

    private val _addonCatalogsFlow = MutableStateFlow<List<AddonCatalogSection>>(emptyList())
    override val addonCatalogsFlow: StateFlow<List<AddonCatalogSection>> = _addonCatalogsFlow.asStateFlow()

    // Thread-safe in-memory cache of discovered media items across addons
    private val cachedMediaItems = ConcurrentHashMap<String, MediaItem>()

    init {
        scope.launch {
            loadAddonsFromDataStore()
            loadServersAndProviders()
            // Fetch initial addon catalogs asynchronously
            fetchAddonCatalogs()
        }
    }

    private suspend fun loadAddonsFromDataStore() {
        val rawJson = dataStore.rawAddonsData.firstOrNull()
        if (!rawJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<InstalledAddon>>() {}.type
                val list: List<InstalledAddon> = gson.fromJson(rawJson, type)
                if (list.isNotEmpty()) {
                    val sorted = list.sortedBy { it.orderIndex }
                    _installedAddonsFlow.value = sorted
                    _addonsFlow.value = sorted.map { it.toAddonManifest() }
                    return
                }
            } catch (e: Exception) {
                // Fall back to defaults if parsing fails
            }
        }
        // First run or empty DataStore: persist default installed addons
        persistInstalledAddons(defaultInstalledAddons)
    }

    private suspend fun persistInstalledAddons(addons: List<InstalledAddon>) {
        val sorted = addons.sortedBy { it.orderIndex }
        _installedAddonsFlow.value = sorted
        _addonsFlow.value = sorted.map { it.toAddonManifest() }
        try {
            val json = gson.toJson(sorted)
            dataStore.saveRawAddonsData(json)
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }

    private suspend fun loadServersAndProviders() {
        dataStore.rawServersData.firstOrNull()?.let { raw ->
            if (raw.isNotBlank()) {
                try {
                    val type = object : TypeToken<List<MediaServerConfig>>() {}.type
                    val list: List<MediaServerConfig> = gson.fromJson(raw, type)
                    if (list.isNotEmpty()) _mediaServersFlow.value = list
                } catch (_: Exception) {}
            }
        }
        dataStore.rawProvidersData.firstOrNull()?.let { raw ->
            if (raw.isNotBlank()) {
                try {
                    val type = object : TypeToken<List<ProviderModule>>() {}.type
                    val list: List<ProviderModule> = gson.fromJson(raw, type)
                    if (list.isNotEmpty()) _customProvidersFlow.value = list
                } catch (_: Exception) {}
            }
        }
    }

    override suspend fun installAddonFromUrl(manifestUrl: String): Result<InstalledAddon> = withContext(Dispatchers.IO) {
        val validation = AddonSecurityValidator.validateUrl(manifestUrl)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: SecurityException("Invalid URL"))
        }

        val validatedUrl = validation.getOrThrow()
        val manifestResult = addonClient.fetchManifest(validatedUrl)
        if (manifestResult.isFailure) {
            return@withContext Result.failure(manifestResult.exceptionOrNull() ?: Exception("Failed to fetch manifest"))
        }

        val manifest = manifestResult.getOrThrow()
        val currentList = _installedAddonsFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == manifest.id }

        val newAddon = InstalledAddon.fromManifest(
            manifest = manifest,
            manifestUrl = validatedUrl,
            isEnabled = true,
            orderIndex = if (existingIndex >= 0) currentList[existingIndex].orderIndex else currentList.size
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = newAddon
        } else {
            currentList.add(newAddon)
        }

        persistInstalledAddons(currentList)
        fetchAddonCatalogs()
        Result.success(newAddon)
    }

    override suspend fun addAddon(name: String, manifestUrl: String, description: String) {
        val result = installAddonFromUrl(manifestUrl)
        if (result.isFailure) {
            // If fetching failed, add a placeholder with ERROR status
            val newAddon = InstalledAddon(
                id = "addon_${UUID.randomUUID().toString().take(8)}",
                name = name.ifBlank { "Custom Addon" },
                manifestUrl = manifestUrl,
                baseUrl = manifestUrl.substringBeforeLast("/manifest.json"),
                isEnabled = true,
                description = description,
                status = AddonStatus.ERROR,
                lastErrorMessage = result.exceptionOrNull()?.message ?: "Failed to connect",
                orderIndex = _installedAddonsFlow.value.size
            )
            val updated = _installedAddonsFlow.value + newAddon
            persistInstalledAddons(updated)
        }
    }

    override suspend fun toggleAddon(id: String): Unit = withContext(Dispatchers.IO) {
        val currentList = _installedAddonsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = currentList[index]
            val newEnabled = !item.isEnabled
            currentList[index] = item.copy(
                isEnabled = newEnabled,
                status = if (newEnabled) AddonStatus.READY else AddonStatus.DISABLED
            )
            persistInstalledAddons(currentList)
            fetchAddonCatalogs()
        }
    }

    override suspend fun removeAddon(id: String): Unit = withContext(Dispatchers.IO) {
        val currentList = _installedAddonsFlow.value.filter { it.id != id }
        persistInstalledAddons(currentList)
        fetchAddonCatalogs()
        Unit
    }

    override suspend fun reorderAddons(fromIndex: Int, toIndex: Int): Unit = withContext(Dispatchers.IO) {
        val currentList = _installedAddonsFlow.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            val reindexed = currentList.mapIndexed { idx, addon -> addon.copy(orderIndex = idx) }
            persistInstalledAddons(reindexed)
        }
    }

    override suspend fun refreshAddon(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentList = _installedAddonsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index < 0) return@withContext Result.failure(IllegalArgumentException("Addon not found"))

        val target = currentList[index]
        currentList[index] = target.copy(status = AddonStatus.LOADING)
        _installedAddonsFlow.value = currentList

        val result = addonClient.fetchManifest(target.manifestUrl)
        if (result.isSuccess) {
            val manifest = result.getOrThrow()
            currentList[index] = InstalledAddon.fromManifest(
                manifest = manifest,
                manifestUrl = target.manifestUrl,
                isEnabled = target.isEnabled,
                orderIndex = target.orderIndex
            )
            persistInstalledAddons(currentList)
            fetchAddonCatalogs()
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Refresh failed"
            currentList[index] = target.copy(status = AddonStatus.ERROR, lastErrorMessage = err)
            persistInstalledAddons(currentList)
            Result.failure(Exception(err))
        }
    }

    override suspend fun refreshAddons(): Unit = withContext(Dispatchers.IO) {
        val currentList = _installedAddonsFlow.value
        for (addon in currentList) {
            if (addon.isEnabled) {
                try {
                    refreshAddon(addon.id)
                } catch (_: Exception) {}
            }
        }
        fetchAddonCatalogs()
        Unit
    }

    override suspend fun saveMediaServer(server: MediaServerConfig): Unit = withContext(Dispatchers.IO) {
        val current = _mediaServersFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == server.id }
        if (index >= 0) current[index] = server else current.add(server)
        _mediaServersFlow.value = current
        try {
            dataStore.saveRawServersData(gson.toJson(current))
        } catch (_: Exception) {}
    }

    override suspend fun removeMediaServer(id: String): Unit = withContext(Dispatchers.IO) {
        val updated = _mediaServersFlow.value.filter { it.id != id }
        _mediaServersFlow.value = updated
        try {
            dataStore.saveRawServersData(gson.toJson(updated))
        } catch (_: Exception) {}
    }

    override suspend fun testConnection(server: MediaServerConfig): Boolean = withContext(Dispatchers.IO) {
        val validation = AddonSecurityValidator.validateUrl(server.baseUrl)
        validation.isSuccess
    }

    override suspend fun toggleProvider(id: String): Unit = withContext(Dispatchers.IO) {
        val current = _customProvidersFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val prov = current[index]
            current[index] = prov.copy(isEnabled = !prov.isEnabled)
            _customProvidersFlow.value = current
            try {
                dataStore.saveRawProvidersData(gson.toJson(current))
            } catch (_: Exception) {}
        }
    }

    private fun extractOriginalId(id: String): String {
        val cached = cachedMediaItems[id]
        if (cached?.originalId?.isNotBlank() == true) {
            return cached.originalId!!
        }
        val matchingAddon = _installedAddonsFlow.value.find { id.startsWith("${it.id}:") }
        if (matchingAddon != null) {
            val stripped = id.removePrefix("${matchingAddon.id}:")
            return ContentIdResolver.resolveCanonicalId(stripped)
        }
        return ContentIdResolver.resolveCanonicalId(id)
    }

    override suspend fun getMediaItemById(id: String): MediaItem? = withContext(Dispatchers.IO) {
        cachedMediaItems[id] ?: cachedMediaItems[extractOriginalId(id)]
    }

    override suspend fun fetchAddonCatalogs(): List<AddonCatalogSection> = withContext(Dispatchers.IO) {
        val enabledAddons = _installedAddonsFlow.value.filter { it.isEnabled }
        val sections = mutableListOf<AddonCatalogSection>()

        for (addon in enabledAddons) {
            for (catalogDef in addon.catalogs) {
                try {
                    val catalogResult = addonClient.fetchCatalog(
                        baseUrl = addon.baseUrl,
                        type = catalogDef.type,
                        id = catalogDef.id
                    )

                    if (catalogResult.isSuccess) {
                        val metas = catalogResult.getOrThrow()
                        val mediaItems = metas.map { meta ->
                            val uniqueId = "${addon.id}:${meta.id}"
                            val isAnime = meta.type.equals("anime", ignoreCase = true) ||
                                    catalogDef.type.equals("anime", ignoreCase = true) ||
                                    meta.genres?.any { it.contains("anime", ignoreCase = true) } == true

                            val format = when {
                                isAnime -> ContentFormat.ANIME
                                meta.type.equals("series", ignoreCase = true) || meta.type.equals("tv", ignoreCase = true) -> ContentFormat.SERIES
                                else -> ContentFormat.MOVIE
                            }
                            val category = when (format) {
                                ContentFormat.ANIME -> MediaCategory.ANIME
                                ContentFormat.SERIES -> MediaCategory.SERIES
                                else -> MediaCategory.MOVIES
                            }
                            val primaryGenre = if (isAnime) {
                                meta.genres?.firstOrNull { !it.equals("anime", ignoreCase = true) } ?: "Anime"
                            } else {
                                meta.genres?.firstOrNull() ?: meta.type.replaceFirstChar { it.uppercase() }
                            }

                            val item = MediaItem(
                                id = uniqueId,
                                title = meta.name,
                                originalTitle = meta.name,
                                originalId = meta.id,
                                posterUrl = meta.poster.orEmpty(),
                                backdropUrl = meta.background ?: meta.poster.orEmpty(),
                                releaseYear = meta.year ?: meta.releaseInfo ?: "2024",
                                releaseDateFormatted = meta.releaseInfo ?: meta.year ?: "2024",
                                format = format,
                                primaryGenre = primaryGenre,
                                genres = meta.genres ?: listOf(primaryGenre),
                                runtimeFormatted = com.streamx.app.utils.DurationParser.parseToFormatted(meta.runtime, fallback = "120 min"),
                                maturityRating = "13+",
                                rating = meta.imdbRating?.toDoubleOrNull() ?: 7.5,
                                ratingVotesCount = "Stremio Addon",
                                likesCount = "95%",
                                synopsis = meta.description ?: "Discovered via ${addon.name}",
                                cast = emptyList(),
                                audioLanguages = listOf("Original Audio"),
                                subtitleLanguages = listOf("English"),
                                categories = listOf(category, MediaCategory.TRENDING),
                                streamUrl = "" // Will be resolved dynamically on playback request
                            )
                            // Cache under both namespaced ID and raw ID
                            cachedMediaItems[uniqueId] = item
                            cachedMediaItems[meta.id] = item
                            item
                        }

                        if (mediaItems.isNotEmpty()) {
                            sections.add(
                                AddonCatalogSection(
                                    addonId = addon.id,
                                    addonName = addon.name,
                                    title = "${addon.name} • ${catalogDef.name ?: catalogDef.id.replaceFirstChar { it.uppercase() }}",
                                    items = mediaItems,
                                    catalogId = catalogDef.id,
                                    type = catalogDef.type,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Gracefully isolate per-addon network or parsing failure
                }
            }
        }

        _addonCatalogsFlow.value = sections
        sections
    }

    override suspend fun searchAddonCatalogs(query: String, type: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val enabledAddons = _installedAddonsFlow.value.filter { it.isEnabled }
        val results = mutableListOf<MediaItem>()
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())

        for (addon in enabledAddons) {
            val matchingCatalogs = addon.catalogs.filter {
                it.type.equals(type, ignoreCase = true) || type.isBlank()
            }

            for (catalogDef in matchingCatalogs) {
                try {
                    val hasSearchExtra = catalogDef.extra?.any { it.name.equals("search", ignoreCase = true) } ?: true
                    if (!hasSearchExtra) continue

                    val searchResult = addonClient.fetchCatalog(
                        baseUrl = addon.baseUrl,
                        type = catalogDef.type,
                        id = catalogDef.id,
                        extraParams = "search=$encodedQuery"
                    )

                    if (searchResult.isSuccess) {
                        val metas = searchResult.getOrThrow()
                        for (meta in metas) {
                            val uniqueId = "${addon.id}:${meta.id}"
                            val isAnime = meta.type.equals("anime", ignoreCase = true) ||
                                    catalogDef.type.equals("anime", ignoreCase = true) ||
                                    meta.genres?.any { it.contains("anime", ignoreCase = true) } == true

                            val format = when {
                                isAnime -> ContentFormat.ANIME
                                meta.type.equals("series", ignoreCase = true) || meta.type.equals("tv", ignoreCase = true) -> ContentFormat.SERIES
                                else -> ContentFormat.MOVIE
                            }
                            val category = when (format) {
                                ContentFormat.ANIME -> MediaCategory.ANIME
                                ContentFormat.SERIES -> MediaCategory.SERIES
                                else -> MediaCategory.MOVIES
                            }
                            val primaryGenre = if (isAnime) {
                                meta.genres?.firstOrNull { !it.equals("anime", ignoreCase = true) } ?: "Anime"
                            } else {
                                meta.genres?.firstOrNull() ?: meta.type.replaceFirstChar { it.uppercase() }
                            }

                            val item = MediaItem(
                                id = uniqueId,
                                title = meta.name,
                                originalTitle = meta.name,
                                originalId = meta.id,
                                posterUrl = meta.poster.orEmpty(),
                                backdropUrl = meta.background ?: meta.poster.orEmpty(),
                                releaseYear = meta.year ?: meta.releaseInfo ?: "2024",
                                releaseDateFormatted = meta.releaseInfo ?: meta.year ?: "2024",
                                format = format,
                                primaryGenre = primaryGenre,
                                genres = meta.genres ?: emptyList(),
                                runtimeFormatted = com.streamx.app.utils.DurationParser.parseToFormatted(meta.runtime, fallback = "120 min"),
                                maturityRating = "13+",
                                rating = meta.imdbRating?.toDoubleOrNull() ?: 7.0,
                                ratingVotesCount = "Stremio",
                                likesCount = "90%",
                                synopsis = meta.description ?: "",
                                cast = emptyList(),
                                audioLanguages = emptyList(),
                                subtitleLanguages = emptyList(),
                                categories = listOf(category),
                                streamUrl = ""
                            )
                            cachedMediaItems[uniqueId] = item
                            cachedMediaItems[meta.id] = item
                            results.add(item)
                        }
                    }
                } catch (e: Exception) {
                    // Isolate addon search failure
                }
            }
        }

        results.distinctBy { it.id }
    }

    override suspend fun getMediaMeta(type: String, id: String): StremioMetaDetail? = withContext(Dispatchers.IO) {
        val originalId = extractOriginalId(id)
        val cleanType = if (type.isNotBlank()) type else {
            val cached = cachedMediaItems[id] ?: cachedMediaItems[originalId]
            ContentIdResolver.inferContentType(cached?.format, originalId)
        }

        val enabledAddons = _installedAddonsFlow.value.filter {
            it.supportsResource("meta", type = cleanType, id = originalId)
        }

        for (addon in enabledAddons) {
            try {
                val requestType = if (cleanType.equals("anime", ignoreCase = true) && !addon.types.contains("anime")) {
                    "series"
                } else {
                    cleanType
                }
                val res = addonClient.fetchMeta(addon.baseUrl, type = requestType, id = originalId)
                if (res.isSuccess) {
                    return@withContext res.getOrThrow()
                }
            } catch (e: Exception) {
                StreamXLog.e("ManifestAddonRepositoryImpl", "Failed to fetch meta from ${addon.name}: ${e.message}")
            }
        }
        null
    }

    override suspend fun getStreamsForMedia(type: String, id: String): List<PlaybackSource> = withContext(Dispatchers.IO) {
        val originalId = extractOriginalId(id)
        val cleanType = if (type.isNotBlank()) type else {
            val cached = cachedMediaItems[id] ?: cachedMediaItems[originalId]
            ContentIdResolver.inferContentType(cached?.format, originalId)
        }

        val enabledAddons = _installedAddonsFlow.value.filter {
            it.supportsResource("stream", type = cleanType, id = originalId)
        }
        StreamXLog.d(
            "ManifestAddonRepositoryImpl",
            "getStreamsForMedia: type=$cleanType, originalId=$originalId across ${enabledAddons.size} enabled addons (${enabledAddons.joinToString { it.name }})"
        )

        coroutineScope {
            val deferredList = enabledAddons.map { addon ->
                async {
                    withTimeoutOrNull(10000L) {
                        try {
                            val requestType = if (cleanType.equals("anime", ignoreCase = true) && !addon.types.contains("anime")) {
                                "series"
                            } else {
                                cleanType
                            }

                            val result = addonClient.fetchStreams(addon.baseUrl, type = requestType, id = originalId)
                            if (result.isSuccess) {
                                val streams = result.getOrThrow()
                                val sources = mutableListOf<PlaybackSource>()
                                for (stream in streams) {
                                    if (stream.isDirectHttp) {
                                        val streamUrl = stream.url!!
                                        val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
                                        val parsed = StreamMetadataParser.parse(stream.title, stream.name, stream.description)
                                        val headers = stream.getRequestHeaders()

                                        // Extract in-stream subtitles if provided directly by stream response
                                        val inStreamSubs = stream.subtitles?.mapNotNull { sub ->
                                            if (sub.url.isNotBlank()) {
                                                SubtitleTrackOption(
                                                    id = "${addon.id}_stream_${sub.id ?: sub.lang}",
                                                    label = "${sub.label ?: sub.lang.uppercase()} • ${addon.name}",
                                                    language = sub.lang,
                                                    isOff = false,
                                                    isSelected = false,
                                                    uri = sub.url
                                                )
                                            } else null
                                        } ?: emptyList()

                                        sources.add(
                                            PlaybackSource(
                                                mediaId = id,
                                                title = "${addon.name}: ${parsed.cleanTitle}",
                                                streamUrl = streamUrl,
                                                isLiveOrHls = isHls,
                                                licenseNotice = "Source: ${addon.name} • Source discovered",
                                                requestHeaders = headers,
                                                availableQualities = listOf(VideoQuality.AUTO),
                                                availableAudioTracks = listOf("Original Audio"),
                                                availableSubtitles = listOf("Off"),
                                                externalSubtitles = inStreamSubs,
                                                providerName = addon.name,
                                                resolution = parsed.resolution,
                                                codec = parsed.codec,
                                                fileSize = parsed.fileSize,
                                                seeders = parsed.seeders,
                                                qualityBadge = parsed.qualityBadge
                                            )
                                        )
                                    } else if (stream.isTorrent && !stream.infoHash.isNullOrBlank()) {
                                        StreamXLog.d("ManifestAddonRepositoryImpl", "Addon ${addon.name} returned BitTorrent stream (infoHash: ${stream.infoHash}). Managed independently via P2P session.")
                                    } else if (stream.isExternalOrYouTube) {
                                        StreamXLog.d("ManifestAddonRepositoryImpl", "Addon ${addon.name} returned external/YouTube stream (ytId: ${stream.ytId}, externalUrl: ${stream.externalUrl}). Direct player playback not supported.")
                                    }
                                }
                                sources
                            } else {
                                emptyList()
                            }
                        } catch (e: Exception) {
                            StreamXLog.e("ManifestAddonRepositoryImpl", "Addon ${addon.name} stream query failed: ${e.message}")
                            emptyList()
                        }
                    } ?: emptyList()
                }
            }

            deferredList.awaitAll().flatten().distinctBy { it.streamUrl }
        }
    }

    override suspend fun getSubtitlesForMedia(type: String, id: String): List<SubtitleTrackOption> = withContext(Dispatchers.IO) {
        val originalId = extractOriginalId(id)
        val cleanType = if (type.isNotBlank()) type else {
            val cached = cachedMediaItems[id] ?: cachedMediaItems[originalId]
            ContentIdResolver.inferContentType(cached?.format, originalId)
        }

        val enabledAddons = _installedAddonsFlow.value.filter {
            it.supportsResource("subtitles", type = cleanType, id = originalId)
        }

        coroutineScope {
            val deferredList = enabledAddons.map { addon ->
                async {
                    withTimeoutOrNull(8000L) {
                        try {
                            val requestType = if (cleanType.equals("anime", ignoreCase = true) && !addon.types.contains("anime")) {
                                "series"
                            } else {
                                cleanType
                            }
                            val result = addonClient.fetchSubtitles(addon.baseUrl, type = requestType, id = originalId)
                            if (result.isSuccess) {
                                val subs = result.getOrThrow()
                                val options = mutableListOf<SubtitleTrackOption>()
                                for (sub in subs) {
                                    if (sub.url.isNotBlank()) {
                                        options.add(
                                            SubtitleTrackOption(
                                                id = "${addon.id}_${sub.id ?: sub.lang}",
                                                label = "${sub.label ?: sub.lang.uppercase()} • ${addon.name}",
                                                language = sub.lang,
                                                isOff = false,
                                                isSelected = false,
                                                uri = sub.url
                                            )
                                        )
                                    }
                                }
                                options
                            } else {
                                emptyList()
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } ?: emptyList()
                }
            }

            deferredList.awaitAll().flatten()
        }
    }
}
