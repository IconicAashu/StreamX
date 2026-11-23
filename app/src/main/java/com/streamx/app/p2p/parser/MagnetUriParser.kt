package com.streamx.app.p2p.parser

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class MagnetInfo(
    val infoHash: String,        // Normalized 40-char lowercase hex
    val displayName: String,
    val trackers: List<String> = emptyList(),
    val exactLength: Long? = null
)

object MagnetUriParser {

    private val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun parse(rawUri: String): Result<MagnetInfo> {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("magnet:?", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Invalid magnet scheme: URI must start with 'magnet:?'"))
        }

        val query = trimmed.substring("magnet:?".length)
        val params = query.split('&')

        var rawHash: String? = null
        var name: String? = null
        val trackers = mutableListOf<String>()
        var length: Long? = null

        for (param in params) {
            val keyValue = param.split('=', limit = 2)
            if (keyValue.size != 2) continue
            val key = keyValue[0].lowercase()
            val value = try {
                URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                keyValue[1]
            }

            when (key) {
                "xt" -> {
                    val lower = value.lowercase()
                    if (lower.startsWith("urn:btih:")) {
                        rawHash = value.substring("urn:btih:".length)
                    }
                }
                "dn" -> name = value
                "tr" -> trackers.add(value)
                "xl" -> length = value.toLongOrNull()
            }
        }

        if (rawHash.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Magnet URI missing required 'xt=urn:btih:' info hash"))
        }

        val normalizedHash = normalizeInfoHash(rawHash)
            ?: return Result.failure(IllegalArgumentException("Invalid BTIH hash format: $rawHash (must be 40 hex or 32 base32)"))

        val finalName = name?.takeIf { it.isNotBlank() } ?: "Magnet ($normalizedHash)"

        return Result.success(
            MagnetInfo(
                infoHash = normalizedHash,
                displayName = finalName,
                trackers = trackers,
                exactLength = length
            )
        )
    }

    fun normalizeInfoHash(raw: String): String? {
        val clean = raw.trim().lowercase()
        return when (clean.length) {
            40 -> {
                // Verify hex chars
                if (clean.all { it in "0123456789abcdef" }) clean else null
            }
            32 -> {
                // Base32 decode to 20 bytes, then hex encode
                base32ToHex(raw.uppercase())
            }
            else -> null
        }
    }

    private fun base32ToHex(base32: String): String? {
        try {
            var buffer = 0
            var bitsLeft = 0
            val bytes = mutableListOf<Byte>()

            for (c in base32) {
                val value = BASE32_ALPHABET.indexOf(c)
                if (value < 0) return null
                buffer = (buffer shl 5) or value
                bitsLeft += 5
                if (bitsLeft >= 8) {
                    bytes.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                    bitsLeft -= 8
                }
            }

            val hexChars = "0123456789abcdef"
            val sb = java.lang.StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val i = b.toInt() and 0xFF
                sb.append(hexChars[i shr 4])
                sb.append(hexChars[i and 0x0F])
            }
            val hex = sb.toString()
            return if (hex.length == 40) hex else null
        } catch (_: Exception) {
            return null
        }
    }
}
