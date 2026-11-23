package com.streamx.app.network.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.streamx.app.domain.model.AppUpdateInfo

/**
 * Robust parser capable of extracting [AppUpdateInfo] from:
 * 1. Standard GitHub Releases API response (`/releases/latest`).
 * 2. Dedicated STREAMX version manifest JSON.
 */
object UpdateManifestParser {

    private val SHA256_REGEX = Regex("([a-fA-F0-9]{64})")
    private val VERSION_CODE_REGEX = Regex("(?i)version\\s*code\\s*[:=]\\s*(\\d+)")

    /**
     * Parses raw JSON string into [AppUpdateInfo].
     * Handles both GitHub release payloads and direct manifests.
     */
    fun parse(jsonString: String): Result<AppUpdateInfo> {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            // 1. Check if payload is a dedicated update manifest
            if (json.has("versionCode") && json.has("apkUrl")) {
                val versionName = json.get("versionName")?.asString ?: "1.0.0"
                val versionCode = json.get("versionCode").asInt
                val releaseNotes = json.get("releaseNotes")?.asString ?: "Bug fixes and improvements."
                val apkUrl = json.get("apkUrl").asString
                val sha256 = json.get("sha256")?.asString
                val releaseDate = json.get("releaseDate")?.asString
                val sizeBytes = if (json.has("sizeBytes")) json.get("sizeBytes").asLong else null

                return Result.success(
                    AppUpdateInfo(
                        versionName = versionName,
                        versionCode = versionCode,
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl,
                        sha256Checksum = sha256,
                        releaseDate = releaseDate,
                        sizeBytes = sizeBytes
                    )
                )
            }

            // 2. Check if payload is a GitHub Releases API response
            if (json.has("tag_name") && json.has("assets")) {
                val tag = json.get("tag_name").asString
                val versionName = tag.removePrefix("v").removePrefix("V")
                val releaseNotes = json.get("body")?.asString ?: "Release $tag"
                val htmlUrl = json.get("html_url")?.asString
                val releaseDate = json.get("published_at")?.asString?.take(10)

                // Locate .apk asset
                val assets = json.getAsJsonArray("assets")
                var apkUrl: String? = null
                var sizeBytes: Long? = null

                for (element in assets) {
                    val asset = element.asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.get("browser_download_url")?.asString
                        sizeBytes = asset.get("size")?.asLong
                        break
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    return Result.failure(IllegalStateException("No APK asset found in release '$tag'."))
                }

                // Extract versionCode from release notes or tag
                val parsedVersionCode = extractVersionCode(releaseNotes, versionName)

                // Extract SHA-256 hash from body if specified
                val sha256 = SHA256_REGEX.find(releaseNotes)?.value

                return Result.success(
                    AppUpdateInfo(
                        versionName = versionName,
                        versionCode = parsedVersionCode,
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl,
                        sha256Checksum = sha256,
                        releaseDate = releaseDate,
                        sizeBytes = sizeBytes,
                        htmlUrl = htmlUrl
                    )
                )
            }

            Result.failure(IllegalArgumentException("Unsupported update manifest structure."))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Failed to parse update manifest: ${e.message}", e))
        }
    }

    /**
     * Extracts or derives integer version code from release notes or version name.
     * Looks for pattern "versionCode: 2" or computes sensible monotonic fallback from "X.Y.Z".
     */
    fun extractVersionCode(body: String, versionName: String): Int {
        val codeMatch = VERSION_CODE_REGEX.find(body)
        if (codeMatch != null) {
            val num = codeMatch.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        // Semantic fallback calculation: 1.2.3 -> 1 * 10000 + 2 * 100 + 3 = 10203
        return try {
            val parts = versionName.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            when (parts.size) {
                1 -> parts[0]
                2 -> parts[0] * 100 + parts[1]
                3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
                else -> 1
            }
        } catch (_: Exception) {
            1
        }
    }
}
