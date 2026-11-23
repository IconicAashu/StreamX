package com.streamx.app.utils

data class ParsedStreamMetadata(
    val resolution: String? = null,
    val codec: String? = null,
    val fileSize: String? = null,
    val seeders: Int? = null,
    val qualityBadge: String = "HD",
    val cleanTitle: String
)

/**
 * Intelligent parser for Stremio stream titles, names, and descriptions.
 * Extracts resolution, codec, file size, and peer counts for Nuvio-style stream cards.
 */
object StreamMetadataParser {

    private val resolutionRegex = Regex("(?i)\\b(4K|2160p|1440p|2K|1080p|720p|480p|360p)\\b")
    private val codecRegex = Regex("(?i)\\b(HEVC|x265|H\\.?265|x264|H\\.?264|AV1|AVC|10bit)\\b")
    private val sizeRegex = Regex("(?i)\\b(\\d+(?:\\.\\d+)?\\s*(?:GB|MB|GiB|MiB))\\b")
    private val seedersRegex = Regex("(?i)(?:👤|seeds?:?)\\s*(\\d+)")

    fun parse(rawTitle: String?, rawName: String?, description: String?): ParsedStreamMetadata {
        val combined = listOfNotNull(rawName, rawTitle, description).joinToString(" ")

        val resolution = resolutionRegex.find(combined)?.value?.uppercase()
        val codec = codecRegex.find(combined)?.value?.uppercase()
        val fileSize = sizeRegex.find(combined)?.value
        val seeders = seedersRegex.find(combined)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val qualityBadge = when {
            resolution?.contains("4K") == true || resolution?.contains("2160") == true -> "4K"
            resolution?.contains("1080") == true -> "1080p"
            resolution?.contains("720") == true -> "720p"
            resolution?.contains("480") == true -> "480p"
            else -> "HD"
        }

        val firstLine = (rawTitle ?: rawName ?: description ?: "Play Stream")
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim() ?: "Play Stream"

        return ParsedStreamMetadata(
            resolution = resolution,
            codec = codec,
            fileSize = fileSize,
            seeders = seeders,
            qualityBadge = qualityBadge,
            cleanTitle = firstLine
        )
    }
}
