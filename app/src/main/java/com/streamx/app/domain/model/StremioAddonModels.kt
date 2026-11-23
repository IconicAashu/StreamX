package com.streamx.app.domain.model

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

enum class AddonStatus {
    READY,
    LOADING,
    ERROR,
    DISABLED,
    NEEDS_CONFIGURATION
}

data class StremioBehaviorHints(
    @SerializedName("configurable") val configurable: Boolean = false,
    @SerializedName("configurationRequired") val configurationRequired: Boolean = false
)

data class StremioCatalogExtra(
    @SerializedName("name") val name: String,
    @SerializedName("isRequired") val isRequired: Boolean = false,
    @SerializedName("options") val options: List<String>? = null
)

data class StremioCatalogDefinition(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("pageSize") val pageSize: Int? = null,
    @SerializedName("extra") val extra: List<StremioCatalogExtra>? = null
)

@JsonAdapter(StremioResourceDefinitionDeserializer::class)
data class StremioResourceDefinition(
    @SerializedName("name") val name: String,
    @SerializedName("types") val types: List<String>? = null,
    @SerializedName("idPrefixes") val idPrefixes: List<String>? = null
)

/**
 * Custom deserializer supporting Stremio manifest v3 resources:
 * Handles array of strings (e.g. ["catalog", "stream"]) and array of objects (e.g. [{"name":"catalog", "types":["movie"]}]).
 */
class StremioResourceDefinitionDeserializer : JsonDeserializer<StremioResourceDefinition> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): StremioResourceDefinition {
        return when {
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                StremioResourceDefinition(name = json.asString)
            }
            json.isJsonObject -> {
                val obj = json.asJsonObject
                val name = obj.get("name")?.asString ?: ""
                val types = if (obj.has("types") && obj.get("types").isJsonArray) {
                    obj.getAsJsonArray("types").mapNotNull { it.asString }
                } else null
                val idPrefixes = if (obj.has("idPrefixes") && obj.get("idPrefixes").isJsonArray) {
                    obj.getAsJsonArray("idPrefixes").mapNotNull { it.asString }
                } else null
                StremioResourceDefinition(name = name, types = types, idPrefixes = idPrefixes)
            }
            else -> StremioResourceDefinition(name = "")
        }
    }
}

data class StremioManifest(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String = "1.0.0",
    @SerializedName("description") val description: String? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("background") val background: String? = null,
    @SerializedName("resources") val resources: List<StremioResourceDefinition> = emptyList(),
    @SerializedName("types") val types: List<String> = emptyList(),
    @SerializedName("idPrefixes") val idPrefixes: List<String>? = null,
    @SerializedName("catalogs") val catalogs: List<StremioCatalogDefinition> = emptyList(),
    @SerializedName("behaviorHints") val behaviorHints: StremioBehaviorHints? = null,
    @SerializedName("contactEmail") val contactEmail: String? = null
) {
    fun supportsResource(resourceName: String, type: String? = null, id: String? = null): Boolean {
        val matchingResource = resources.firstOrNull { it.name.equals(resourceName, ignoreCase = true) }
            ?: return false

        val effectiveTypes = matchingResource.types ?: types
        if (type != null && effectiveTypes.isNotEmpty()) {
            val directMatch = effectiveTypes.any { it.equals(type, ignoreCase = true) }
            val animeFallback = type.equals("anime", ignoreCase = true) &&
                    effectiveTypes.any { it.equals("series", ignoreCase = true) || it.equals("movie", ignoreCase = true) }
            val seriesFallback = (type.equals("series", ignoreCase = true) || type.equals("movie", ignoreCase = true)) &&
                    effectiveTypes.any { it.equals("anime", ignoreCase = true) }
            if (!directMatch && !animeFallback && !seriesFallback) {
                return false
            }
        }

        if (id != null) {
            val allowedPrefixes = matchingResource.idPrefixes ?: idPrefixes
            if (allowedPrefixes != null && allowedPrefixes.isNotEmpty()) {
                val hasPrefix = allowedPrefixes.any { prefix ->
                    id.startsWith(prefix, ignoreCase = true) ||
                    id.startsWith(prefix.trimEnd(':') + ":", ignoreCase = true)
                }
                if (!hasPrefix) return false
            }
        }

        return true
    }
}


data class StremioTrailer(
    @SerializedName("source") val source: String? = null,
    @SerializedName("type") val type: String? = null
)

data class StremioVideo(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("released") val released: String? = null,
    @SerializedName("season") val season: Int = 1,
    @SerializedName("episode") val episode: Int = 1,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("overview") val overview: String? = null
)

data class StremioMetaItem(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("background") val background: String? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("genres") val genres: List<String>? = null,
    @SerializedName("runtime") val runtime: String? = null
)

data class StremioMetaDetail(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("background") val background: String? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("genres") val genres: List<String>? = null,
    @SerializedName("runtime") val runtime: String? = null,
    @SerializedName("cast") val cast: List<String>? = null,
    @SerializedName("director") val director: List<String>? = null,
    @SerializedName("trailers") val trailers: List<StremioTrailer>? = null,
    @SerializedName("videos") val videos: List<StremioVideo>? = null
)

data class StremioStreamBehaviorHints(
    @SerializedName("notWebReady") val notWebReady: Boolean? = null,
    @SerializedName("bingeGroup") val bingeGroup: String? = null,
    @SerializedName("proxyHeaders") val proxyHeaders: JsonElement? = null,
    @SerializedName("headers") val headers: Map<String, String>? = null,
    @SerializedName("requestHeaders") val requestHeaders: Map<String, String>? = null
) {
    fun getEffectiveHeaders(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        requestHeaders?.let { result.putAll(it) }
        headers?.let { result.putAll(it) }
        if (proxyHeaders != null && proxyHeaders.isJsonObject) {
            val obj = proxyHeaders.asJsonObject
            if (obj.has("request") && obj.get("request").isJsonObject) {
                val req = obj.getAsJsonObject("request")
                for ((k, v) in req.entrySet()) {
                    if (v.isJsonPrimitive) result[k] = v.asString
                }
            } else if (obj.has("headers") && obj.get("headers").isJsonObject) {
                val req = obj.getAsJsonObject("headers")
                for ((k, v) in req.entrySet()) {
                    if (v.isJsonPrimitive) result[k] = v.asString
                }
            } else {
                for ((k, v) in obj.entrySet()) {
                    if (!k.equals("response", ignoreCase = true) && v.isJsonPrimitive) {
                        result[k] = v.asString
                    }
                }
            }
        }
        return result
    }

    fun getResponseHeaders(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (proxyHeaders != null && proxyHeaders.isJsonObject) {
            val obj = proxyHeaders.asJsonObject
            if (obj.has("response") && obj.get("response").isJsonObject) {
                val resp = obj.getAsJsonObject("response")
                for ((k, v) in resp.entrySet()) {
                    if (v.isJsonPrimitive) result[k] = v.asString
                }
            }
        }
        return result
    }
}

data class StremioStream(
    @SerializedName("url") val url: String? = null,
    @SerializedName("ytId") val ytId: String? = null,
    @SerializedName("externalUrl") val externalUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int? = null,
    @SerializedName("subtitles") val subtitles: List<StremioSubtitle>? = null,
    @SerializedName("headers") val directHeaders: Map<String, String>? = null,
    @SerializedName("behaviorHints") val behaviorHints: StremioStreamBehaviorHints? = null
) {
    val isTorrent: Boolean get() = !infoHash.isNullOrBlank()
    val isDirectHttp: Boolean get() = !url.isNullOrBlank() && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
    val isExternalOrYouTube: Boolean get() = !ytId.isNullOrBlank() || !externalUrl.isNullOrBlank()

    fun getRequestHeaders(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        directHeaders?.let { result.putAll(it) }
        behaviorHints?.getEffectiveHeaders()?.let { result.putAll(it) }
        return result
    }
}

data class StremioSubtitle(
    @SerializedName("id") val id: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("lang") val lang: String,
    @SerializedName("label") val label: String? = null
)

data class StremioCatalogResponse(
    @SerializedName("metas") val metas: List<StremioMetaItem>? = null
)

data class StremioMetaDetailResponse(
    @SerializedName("meta") val meta: StremioMetaDetail? = null
)

data class StremioStreamResponse(
    @SerializedName("streams") val streams: List<StremioStream>? = null
)

data class StremioSubtitleResponse(
    @SerializedName("subtitles") val subtitles: List<StremioSubtitle>? = null
)

/**
 * Persistent representation of an installed add-on in STREAMX.
 */
data class InstalledAddon(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val baseUrl: String,
    val isEnabled: Boolean = true,
    val version: String = "1.0.0",
    val description: String = "",
    val logo: String? = null,
    val resources: List<StremioResourceDefinition> = emptyList(),
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
    val catalogs: List<StremioCatalogDefinition> = emptyList(),
    val orderIndex: Int = 0,
    val status: AddonStatus = AddonStatus.READY,
    val lastErrorMessage: String? = null
) {
    val isStreamProvider: Boolean
        get() = resources.any { it.name.equals("stream", ignoreCase = true) }

    val isMetadataOnly: Boolean
        get() = !isStreamProvider && resources.any { it.name.equals("meta", ignoreCase = true) || it.name.equals("catalog", ignoreCase = true) }

    val isSubtitlesOnly: Boolean
        get() = !isStreamProvider && resources.all { it.name.equals("subtitles", ignoreCase = true) }

    fun toAddonManifest(): AddonManifest = AddonManifest(
        id = id,
        name = name,
        manifestUrl = manifestUrl,
        isEnabled = isEnabled,
        version = version,
        description = description
    )

    fun supportsResource(resourceName: String, type: String? = null, id: String? = null): Boolean {
        if (!isEnabled) return false
        val matching = resources.firstOrNull { it.name.equals(resourceName, ignoreCase = true) }
            ?: return false

        val effectiveTypes = matching.types ?: types
        if (type != null && effectiveTypes.isNotEmpty()) {
            val directMatch = effectiveTypes.any { it.equals(type, ignoreCase = true) }
            val animeFallback = type.equals("anime", ignoreCase = true) &&
                    effectiveTypes.any { it.equals("series", ignoreCase = true) || it.equals("movie", ignoreCase = true) }
            val seriesFallback = (type.equals("series", ignoreCase = true) || type.equals("movie", ignoreCase = true)) &&
                    effectiveTypes.any { it.equals("anime", ignoreCase = true) }
            if (!directMatch && !animeFallback && !seriesFallback) {
                return false
            }
        }

        if (id != null) {
            val allowedPrefixes = matching.idPrefixes ?: idPrefixes
            if (allowedPrefixes != null && allowedPrefixes.isNotEmpty()) {
                val hasPrefix = allowedPrefixes.any { prefix ->
                    id.startsWith(prefix, ignoreCase = true) ||
                    id.startsWith(prefix.trimEnd(':') + ":", ignoreCase = true)
                }
                if (!hasPrefix) return false
            }
        }

        return true
    }

    companion object {
        fun fromManifest(manifest: StremioManifest, manifestUrl: String, isEnabled: Boolean = true, orderIndex: Int = 0): InstalledAddon {
            val normalizedUrl = manifestUrl.trim()
            val base = if (normalizedUrl.endsWith("/manifest.json", ignoreCase = true)) {
                normalizedUrl.substringBeforeLast("/manifest.json")
            } else if (normalizedUrl.endsWith("/")) {
                normalizedUrl.removeSuffix("/")
            } else {
                normalizedUrl
            }

            return InstalledAddon(
                id = manifest.id,
                name = manifest.name,
                manifestUrl = normalizedUrl,
                baseUrl = base,
                isEnabled = isEnabled,
                version = manifest.version,
                description = manifest.description.orEmpty(),
                logo = manifest.logo,
                resources = manifest.resources,
                types = manifest.types,
                idPrefixes = manifest.idPrefixes,
                catalogs = manifest.catalogs,
                orderIndex = orderIndex,
                status = if (isEnabled) AddonStatus.READY else AddonStatus.DISABLED
            )
        }
    }
}
