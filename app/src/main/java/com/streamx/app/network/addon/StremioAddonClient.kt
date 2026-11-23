package com.streamx.app.network.addon

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.streamx.app.domain.model.*
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class StremioAddonClient(
    private val okHttpClient: OkHttpClient = AddonSecurityValidator.createSafeAddonOkHttpClient()
) {
    val gson: Gson = Gson()

    suspend fun fetchManifest(manifestUrl: String): Result<StremioManifest> = withContext(Dispatchers.IO) {
        val trimmed = manifestUrl.trim()
        val cleanUrl = if (trimmed.startsWith("stremio://", ignoreCase = true)) {
            "https://" + trimmed.substring(10)
        } else {
            trimmed
        }

        val validation = AddonSecurityValidator.validateUrl(cleanUrl)
        if (validation.isFailure) {
            val err = validation.exceptionOrNull() ?: SecurityException("Invalid URL")
            StreamXLog.e("StremioAddonClient", "Manifest URL validation failed: ${err.message}")
            return@withContext Result.failure(err)
        }

        val validatedUrl = validation.getOrThrow()
        val normalizedUrl = if (validatedUrl.endsWith("/manifest.json", ignoreCase = true)) {
            validatedUrl
        } else if (validatedUrl.endsWith("/")) {
            "${validatedUrl}manifest.json"
        } else {
            "$validatedUrl/manifest.json"
        }

        StreamXLog.d("StremioAddonClient", "Connecting to addon manifest: $normalizedUrl")

        try {
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "STREAMX-Stremio-Client/1.0.0")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                StreamXLog.d("StremioAddonClient", "HTTP response code: ${response.code} for $normalizedUrl")
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    StreamXLog.e("StremioAddonClient", "Failed HTTP call: $errorMsg")
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrBlank()) {
                    StreamXLog.e("StremioAddonClient", "Manifest response body is empty from $normalizedUrl")
                    return@withContext Result.failure(IOException("Empty response body"))
                }

                val manifest = gson.fromJson(bodyString, StremioManifest::class.java)
                if (manifest == null || manifest.id.isBlank() || manifest.name.isBlank()) {
                    StreamXLog.e("StremioAddonClient", "Invalid manifest JSON: missing required id or name")
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid manifest: Missing required 'id' or 'name'")
                    )
                }

                StreamXLog.d("StremioAddonClient", "Successfully parsed manifest: ${manifest.name} (${manifest.id})")
                Result.success(manifest)
            }
        } catch (e: java.net.UnknownHostException) {
            val host = e.message ?: "server"
            val msg = "Unable to resolve host \"$host\". The add-on server is offline or unreachable."
            StreamXLog.e("StremioAddonClient", msg)
            Result.failure(IOException(msg, e))
        } catch (e: java.net.SocketTimeoutException) {
            val msg = "Connection timed out while contacting add-on server."
            StreamXLog.e("StremioAddonClient", msg)
            Result.failure(IOException(msg, e))
        } catch (e: java.net.ConnectException) {
            val msg = "Connection refused by add-on server. Server may be down."
            StreamXLog.e("StremioAddonClient", msg)
            Result.failure(IOException(msg, e))
        } catch (e: Exception) {
            StreamXLog.e("StremioAddonClient", "Exception during manifest fetch: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchCatalog(
        baseUrl: String,
        type: String,
        id: String,
        extraParams: String? = null
    ): Result<List<StremioMetaItem>> = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trimEnd('/')
        val cleanExtra = if (!extraParams.isNullOrBlank()) {
            extraParams.replace(" ", "%20")
        } else null

        val url = if (cleanExtra.isNullOrBlank()) {
            "$normalizedBase/catalog/$type/$id.json"
        } else {
            "$normalizedBase/catalog/$type/$id/$cleanExtra.json"
        }

        val validation = AddonSecurityValidator.validateUrl(url)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: SecurityException("Invalid URL"))
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "STREAMX-Stremio-Client/1.0.0")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val catalogResponse = gson.fromJson(bodyString, StremioCatalogResponse::class.java)
                val items = catalogResponse?.metas ?: emptyList()
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMeta(
        baseUrl: String,
        type: String,
        id: String
    ): Result<StremioMetaDetail> = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trimEnd('/')
        val url = "$normalizedBase/meta/$type/$id.json"

        val validation = AddonSecurityValidator.validateUrl(url)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: SecurityException("Invalid URL"))
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "STREAMX-Stremio-Client/1.0.0")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val metaResponse = gson.fromJson(bodyString, StremioMetaDetailResponse::class.java)
                val meta = metaResponse?.meta
                    ?: return@withContext Result.failure(IOException("No meta found in response"))

                Result.success(meta)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchStreams(
        baseUrl: String,
        type: String,
        id: String
    ): Result<List<StremioStream>> = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trimEnd('/')
        val url = "$normalizedBase/stream/$type/$id.json"

        val validation = AddonSecurityValidator.validateUrl(url)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: SecurityException("Invalid URL"))
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "STREAMX-Stremio-Client/1.0.0")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val streamResponse = gson.fromJson(bodyString, StremioStreamResponse::class.java)
                val streams = streamResponse?.streams ?: emptyList()
                Result.success(streams)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSubtitles(
        baseUrl: String,
        type: String,
        id: String
    ): Result<List<StremioSubtitle>> = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trimEnd('/')
        val url = "$normalizedBase/subtitles/$type/$id.json"

        val validation = AddonSecurityValidator.validateUrl(url)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull() ?: SecurityException("Invalid URL"))
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "STREAMX-Stremio-Client/1.0.0")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val subResponse = gson.fromJson(bodyString, StremioSubtitleResponse::class.java)
                val subtitles = subResponse?.subtitles ?: emptyList()
                Result.success(subtitles)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
