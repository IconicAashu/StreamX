package com.streamx.app.utils

import com.streamx.app.data.local.MockCatalogDataSource
import com.streamx.app.domain.model.ContentFormat

/**
 * Universal Content Identifier and Format Resolver.
 * Maps internal, namespaced, TMDB, and episode IDs to canonical Stremio IDs (IMDb tt..., Kitsu kitsu:..., etc.)
 * and infers the correct Stremio resource type (movie, series, anime).
 */
object ContentIdResolver {

    private val internalToCanonicalMap = mapOf(
        "m1" to "tt9362930",    // Blue Beetle
        "m2" to "tt1757678",    // Avatar: Fire and Ash
        "m3" to "tt11315808",   // Joker: Folie à Deux
        "m4" to "tt12343534",   // Jujutsu Kaisen (IMDb ID)
        "m5" to "tt31024314",   // Demon Slayer: Infinity Castle
        "m6" to "tt27961726",   // Kalki 2898 AD
        "m7" to "tt8178634",    // RRR
        "m8" to "tt10919420",   // Squid Game
        "m9" to "tt9362722",    // Spider-Man: Across the Spider-Verse
        "m10" to "tt15239678",  // Dune: Part Two
        "m11" to "tt15398776"   // Oppenheimer
    )

    /**
     * Resolves any media identifier (internal id, namespaced addon id, or episode id)
     * to its canonical Stremio-compatible ID (IMDb tt..., Kitsu kitsu:..., etc.).
     */
    fun resolveCanonicalId(rawId: String): String {
        val trimmed = rawId.trim()

        // 1. Check if it has episode suffix like :1:1 or :2 (e.g. m8:1:1 or addon_cinemeta:tt12345:1:1)
        val episodeMatch = Regex("(?::\\d+:\\d+|:\\d+)$").find(trimmed)
        val episodeSuffix = episodeMatch?.value ?: ""
        val baseId = if (episodeSuffix.isNotEmpty()) {
            trimmed.substring(0, trimmed.length - episodeSuffix.length)
        } else {
            trimmed
        }

        // 2. Strip addon namespace prefixes (e.g. addon_cinemeta:tt12345 -> tt12345)
        val strippedId = if (baseId.contains(":")) {
            val prefix = baseId.substringBefore(":")
            if (prefix.startsWith("addon_") || prefix.startsWith("org.") || prefix.startsWith("com.")) {
                baseId.substringAfter(":")
            } else {
                baseId
            }
        } else {
            baseId
        }

        // 3. Resolve canonical base ID
        val canonicalBase = internalToCanonicalMap[strippedId]
            ?: MockCatalogDataSource.items.find { it.id == strippedId }?.originalId
            ?: strippedId

        val resolved = canonicalBase + episodeSuffix
        StreamXLog.d("ContentIdResolver", "resolveCanonicalId: rawId='$rawId' -> canonical='$resolved'")
        return resolved
    }

    /**
     * Infers Stremio content type ("movie", "series", "anime") from format and ID structure.
     */
    fun inferContentType(format: ContentFormat?, id: String): String {
        val inferred = when {
            format == ContentFormat.ANIME || id.startsWith("kitsu:", ignoreCase = true) -> "anime"
            id.matches(Regex(".*:\\d+:\\d+$")) || id.contains(":season:", ignoreCase = true) -> "series"
            format == ContentFormat.SERIES -> "series"
            else -> "movie"
        }
        StreamXLog.d("ContentIdResolver", "inferContentType: format=$format, id='$id' -> type='$inferred'")
        return inferred
    }
}
