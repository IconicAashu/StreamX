package com.streamx.app.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

object DurationParser {

    private val HOUR_MIN_REGEX = Regex(
        """^(?:(\d+)\s*(?:hours?|hrs?|h))?\s*(?:(\d+)\s*(?:minutes?|mins?|m))?\s*(?:(\d+)\s*(?:seconds?|secs?|s))?$""",
        RegexOption.IGNORE_CASE
    )

    private val TIME_COLON_REGEX = Regex(
        """^(?:(?:(\d{1,2}):)?(\d{1,2}):)?(\d{1,2})$"""
    )

    /**
     * Parses a duration string or number representation into milliseconds.
     * Supports formats: "2h 3m", "1h 45m", "45m", "120 min", "120", "02:15:00", etc.
     * Never throws NumberFormatException or any runtime exception.
     */
    fun parseToMs(raw: String?, defaultMs: Long = 0L): Long {
        if (raw.isNullOrBlank()) return defaultMs
        val seconds = parseToSeconds(raw, -1L)
        return if (seconds >= 0L) seconds * 1000L else defaultMs
    }

    /**
     * Parses a duration string or number representation into seconds.
     * Never throws NumberFormatException or any runtime exception.
     */
    fun parseToSeconds(raw: String?, defaultSeconds: Long = 0L): Long {
        if (raw.isNullOrBlank()) return defaultSeconds
        val trimmed = raw.trim()

        try {
            // Case 1: HH:mm:ss or mm:ss
            if (trimmed.contains(":")) {
                val match = TIME_COLON_REGEX.matchEntire(trimmed)
                if (match != null) {
                    val p1 = match.groupValues[1].toLongOrNull()
                    val p2 = match.groupValues[2].toLongOrNull()
                    val p3 = match.groupValues[3].toLongOrNull() ?: 0L

                    return when {
                        p1 != null && p2 != null -> (p1 * 3600L) + (p2 * 60L) + p3
                        p2 != null -> (p2 * 60L) + p3
                        else -> p3
                    }
                }
            }

            // Case 2: Formats with units like "2h 3m", "1h 45min", "45m", "2h", "90 min"
            val hourMinMatch = HOUR_MIN_REGEX.matchEntire(trimmed)
            if (hourMinMatch != null) {
                val hoursStr = hourMinMatch.groupValues[1]
                val minsStr = hourMinMatch.groupValues[2]
                val secsStr = hourMinMatch.groupValues[3]

                if (hoursStr.isNotEmpty() || minsStr.isNotEmpty() || secsStr.isNotEmpty()) {
                    val hours = hoursStr.toLongOrNull() ?: 0L
                    val mins = minsStr.toLongOrNull() ?: 0L
                    val secs = secsStr.toLongOrNull() ?: 0L
                    return (hours * 3600L) + (mins * 60L) + secs
                }
            }

            // Case 3: Pure numeric string or numeric with 'min' / 'm' / 's'
            val cleanNumeric = trimmed.replace(Regex("""[^\d]"""), "")
            if (cleanNumeric.isNotEmpty()) {
                val numericVal = cleanNumeric.toLongOrNull() ?: return defaultSeconds

                // If explicitly has 's' or 'sec' and no 'h' or 'm'
                if (trimmed.endsWith("s", ignoreCase = true) || trimmed.endsWith("sec", ignoreCase = true)) {
                    return numericVal
                }

                // If it's a typical runtime number without units (e.g., "120" -> 120 minutes)
                // Runtime in minutes is typically <= 600 (10 hours). If >= 600, it's already in seconds.
                return if (numericVal in 1..600) {
                    numericVal * 60L
                } else {
                    numericVal
                }
            }
        } catch (_: Exception) {
            // Safe fallback
        }

        return defaultSeconds
    }

    /**
     * Converts seconds into standard human-readable format e.g. "2h 3m" or "45m".
     */
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "0m"
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return if (hours > 0L) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    /**
     * Formats duration milliseconds into "2h 3m" or "45m".
     */
    fun formatMs(ms: Long): String {
        return formatDuration(ms / 1000L)
    }

    /**
     * Safely formats an arbitrary raw value (String, Number, etc.) into "Xh Ym" or "Xm".
     */
    fun parseToFormatted(raw: Any?, fallback: String = "2h 15m"): String {
        if (raw == null) return fallback
        return when (raw) {
            is Number -> formatDuration(raw.toLong())
            is String -> {
                val secs = parseToSeconds(raw, -1L)
                if (secs > 0L) formatDuration(secs) else raw.ifBlank { fallback }
            }
            else -> fallback
        }
    }
}

/**
 * Custom Gson JsonDeserializer to safely parse duration / durationSeconds fields
 * without ever throwing java.lang.NumberFormatException on strings like "2h 3m".
 */
class SafeDurationLongDeserializer : JsonDeserializer<Long?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Long? {
        if (json == null || json.isJsonNull) return null
        return try {
            if (json.isJsonPrimitive) {
                val primitive = json.asJsonPrimitive
                if (primitive.isNumber) {
                    primitive.asLong
                } else if (primitive.isString) {
                    val str = primitive.asString
                    DurationParser.parseToSeconds(str, 0L)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
