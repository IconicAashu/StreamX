package com.streamx.app.network.provider

import com.google.gson.Gson
import com.streamx.app.domain.model.*
import com.streamx.app.network.addon.AddonSecurityValidator
import com.streamx.app.utils.StreamXLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Adapter and security validator for Nuvio Native Plugins (nuvioplugin.com / GitHub).
 *
 * Architecture Analysis (Phase 3):
 * 1. Nuvio plugins are client-side JavaScript scrapers designed for React Native + Hermes JS Engine.
 * 2. They do NOT follow the Stremio HTTP/JSON protocol and cannot be queried via Stremio REST calls.
 * 3. Executing arbitrary third-party JavaScript directly in the main Android process without an
 *    isolated sandbox poses severe security risks (arbitrary code execution, SSRF, memory leaks).
 * 4. This adapter securely parses Nuvio repository manifests, inspects plugin metadata, and
 *    classifies each entry according to legal authorization and sandbox requirements.
 */
class NuvioPluginAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "NuvioPluginAdapter"

        // Known unverified/pirate scraper keywords that violate Phase 6 security & legal policy
        private val UNVERIFIED_SCRAPER_KEYWORDS = listOf(
            "pirate", "crack", "torrentio-scrap", "showbox", "doodstream", "streamtape", "vidcloud"
        )
    }

    /**
     * Parses a Nuvio repository manifest URL (e.g. from nuvioplugin.com or GitHub raw).
     */
    fun parseRepositoryManifest(manifestJson: String, repoUrl: String): Result<List<UnifiedProvider>> {
        return try {
            val manifest = gson.fromJson(manifestJson, NuvioRepoManifest::class.java)
            if (manifest == null || manifest.plugins.isEmpty()) {
                // If single plugin manifest
                val singlePlugin = gson.fromJson(manifestJson, NuvioPluginDefinition::class.java)
                if (singlePlugin != null && singlePlugin.id.isNotBlank()) {
                    return Result.success(listOf(mapToUnifiedProvider(singlePlugin, repoUrl)))
                }
                return Result.failure(IllegalArgumentException("No valid Nuvio plugins found in manifest"))
            }

            val providers = manifest.plugins.map { plugin ->
                mapToUnifiedProvider(plugin, repoUrl)
            }
            Result.success(providers)
        } catch (e: Exception) {
            StreamXLog.e(TAG, "Failed to parse Nuvio manifest: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches and parses a remote Nuvio repository manifest.
     */
    fun fetchRepository(url: String): Result<List<UnifiedProvider>> {
        val validatedUrl = AddonSecurityValidator.validateUrl(url).getOrElse {
            return Result.failure(it)
        }

        return try {
            val request = Request.Builder()
                .url(validatedUrl)
                .header("User-Agent", "STREAMX/2.0 NuvioAdapter")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response body"))
            parseRepositoryManifest(body, validatedUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToUnifiedProvider(plugin: NuvioPluginDefinition, repoUrl: String): UnifiedProvider {
        val isBlocked = UNVERIFIED_SCRAPER_KEYWORDS.any { keyword ->
            plugin.id.contains(keyword, ignoreCase = true) ||
            plugin.name.contains(keyword, ignoreCase = true) ||
            plugin.description.contains(keyword, ignoreCase = true)
        }

        val status = if (isBlocked) {
            ProviderIntegrationStatus.BLOCKED_SECURITY
        } else {
            ProviderIntegrationStatus.REQUIRES_RUNTIME_ADAPTER
        }

        val securityNotice = if (isBlocked) {
            "Blocked per STREAMX Security & Legal Policy: Unlicensed scraper / access bypass."
        } else {
            "Nuvio Native Plugin: Requires Hermes/QuickJS Sandboxed Engine. Dynamic in-process script execution is disabled."
        }

        return UnifiedProvider(
            id = "nuvio_${plugin.id}",
            name = plugin.name,
            category = ProviderCategory.NUVIO_NATIVE,
            description = plugin.description.ifBlank { "Nuvio Native Provider module for ${plugin.name}" },
            author = plugin.author.ifBlank { "Nuvio Community" },
            version = plugin.version,
            manifestUrl = plugin.scriptUrl ?: repoUrl,
            repositoryUrl = repoUrl,
            supportedTypes = plugin.types,
            supportedLanguages = plugin.languages,
            iconUrl = plugin.icon,
            isInstalled = false,
            isEnabled = false,
            requiresConfiguration = false,
            integrationStatus = status,
            playbackTransport = PlaybackTransportType.DIRECT_HTTP,
            healthStatus = if (isBlocked) "Blocked" else "Sandbox Required",
            protocolDescription = "Nuvio Hermes JS Provider Module (Requires JS Sandbox)",
            isLegitimate = !isBlocked,
            securityNotice = securityNotice
        )
    }
}
