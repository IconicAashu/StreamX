package com.streamx.app.network.provider

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamx.app.domain.model.*
import com.streamx.app.network.addon.AddonSecurityValidator
import com.streamx.app.utils.StreamXLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Parser and security inspector for CloudStream Extensions (cloudstreamrepo.com / GitHub).
 *
 * Architecture Analysis (Phase 4):
 * 1. CloudStream uses a two-level repository model: `repo.json` -> `plugins.json` -> `.cs3` files.
 * 2. `.cs3` files are compiled Android Dex / JVM bytecode packages implementing CloudStream's `MainAPI`.
 * 3. Loading unverified dynamic `.cs3` Dex bytecode at runtime via `DexClassLoader` inside the main
 *    application is blocked by modern Android security guidelines (arbitrary bytecode execution).
 * 4. This parser securely reads repository metadata, inspects available extensions, and classifies
 *    them as requiring an isolated out-of-process Dalvik Sandbox Runtime.
 */
class CloudStreamRepoParser(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "CloudStreamRepoParser"

        private val UNVERIFIED_SCRAPER_KEYWORDS = listOf(
            "pirate", "crack", "torrentio-scrap", "showbox", "doodstream", "streamtape", "vidcloud"
        )
    }

    /**
     * Parses a CloudStream `repo.json` string and returns the list of plugin list URLs.
     */
    fun parseRepoManifest(repoJson: String): Result<CloudStreamRepoManifest> {
        return try {
            val manifest = gson.fromJson(repoJson, CloudStreamRepoManifest::class.java)
            if (manifest == null || manifest.name.isBlank()) {
                Result.failure(IllegalArgumentException("Invalid CloudStream repo.json structure"))
            } else {
                Result.success(manifest)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses a CloudStream `plugins.json` string into a list of UnifiedProvider items.
     */
    fun parsePluginsJson(pluginsJson: String, repoUrl: String, repoName: String): Result<List<UnifiedProvider>> {
        return try {
            val listType = object : TypeToken<List<CloudStreamPluginItem>>() {}.type
            val plugins: List<CloudStreamPluginItem> = gson.fromJson(pluginsJson, listType)
                ?: return Result.failure(IllegalArgumentException("Empty plugins list"))

            val providers = plugins.map { item ->
                mapToUnifiedProvider(item, repoUrl, repoName)
            }
            Result.success(providers)
        } catch (e: Exception) {
            StreamXLog.e(TAG, "Failed to parse CloudStream plugins.json: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches and parses a remote CloudStream repository (`repo.json` or `plugins.json`).
     */
    fun fetchRepository(url: String): Result<List<UnifiedProvider>> {
        val validatedUrl = AddonSecurityValidator.validateUrl(url).getOrElse {
            return Result.failure(it)
        }

        return try {
            val request = Request.Builder()
                .url(validatedUrl)
                .header("User-Agent", "STREAMX/2.0 CloudStreamAdapter")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response body"))

            // Check if URL directly points to plugins.json
            if (validatedUrl.endsWith("plugins.json", ignoreCase = true) || body.trimStart().startsWith("[")) {
                return parsePluginsJson(body, validatedUrl, "CloudStream Repository")
            }

            // Otherwise parse repo.json and follow pluginLists
            val repoManifest = parseRepoManifest(body).getOrThrow()
            val allProviders = mutableListOf<UnifiedProvider>()

            for (pluginListUrl in repoManifest.pluginLists) {
                val listResult = fetchPluginsList(pluginListUrl, repoManifest.name)
                listResult.getOrNull()?.let { allProviders.addAll(it) }
            }

            Result.success(allProviders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchPluginsList(url: String, repoName: String): Result<List<UnifiedProvider>> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "STREAMX/2.0 CloudStreamAdapter")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(Exception("Empty plugins.json"))
            parsePluginsJson(body, url, repoName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToUnifiedProvider(item: CloudStreamPluginItem, repoUrl: String, repoName: String): UnifiedProvider {
        val isBlocked = UNVERIFIED_SCRAPER_KEYWORDS.any { keyword ->
            item.name.contains(keyword, ignoreCase = true) ||
            (item.internalName?.contains(keyword, ignoreCase = true) == true) ||
            (item.description?.contains(keyword, ignoreCase = true) == true)
        }

        val status = if (isBlocked) {
            ProviderIntegrationStatus.BLOCKED_SECURITY
        } else {
            ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER
        }

        val securityNotice = if (isBlocked) {
            "Blocked per STREAMX Security & Legal Policy: Unlicensed scraper / access bypass."
        } else {
            "CloudStream Extension: Requires Dalvik (.cs3) Sandbox Runtime. Executing arbitrary dynamic bytecode in host process is disabled."
        }

        val mediaTypes = item.types.map { typ ->
            when (typ.lowercase()) {
                "tvseries", "series" -> "series"
                "anime" -> "anime"
                "cartoon" -> "animation"
                else -> "movie"
            }
        }.ifEmpty { listOf("movie", "series") }

        return UnifiedProvider(
            id = "cs_${(item.internalName ?: item.name.replace(" ", "_")).lowercase()}",
            name = item.name,
            category = ProviderCategory.CLOUDSTREAM_EXTENSION,
            description = item.description ?: "CloudStream extension module compiled from Kotlin ($repoName)",
            author = item.authors.joinToString(", ").ifBlank { "CloudStream Community" },
            version = "v${item.version}",
            manifestUrl = item.url ?: repoUrl,
            repositoryUrl = repoUrl,
            supportedTypes = mediaTypes,
            supportedLanguages = listOf(item.language.ifBlank { "en" }),
            iconUrl = item.iconUrl,
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = status,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = if (isBlocked) "Blocked" else "Sandbox Required",
            protocolDescription = "CloudStream MainAPI compiled Dex bytecode (.cs3)",
            isLegitimate = !isBlocked,
            securityNotice = securityNotice
        )
    }
}
