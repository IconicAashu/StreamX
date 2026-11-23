package com.streamx.app.player

import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class StreamValidationResult(
    val isValid: Boolean,
    val httpStatusCode: Int = 0,
    val contentType: String? = null,
    val contentLength: Long = -1L,
    val isExpired: Boolean = false,
    val isTrailer: Boolean = false,
    val isPlayableVideo: Boolean = false,
    val serverErrorMessage: String? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val finalRedirectUrl: String? = null,
    val diagnosticSummary: String = ""
)

object StreamUrlValidator {

    /**
     * Validates a stream URL before passing it to ExoPlayer:
     * 1. URL syntax and protocol verification (using standard java.net.URI).
     * 2. Signature and expiration check (parsing timestamp 't', 'exp', etc.).
     * 3. Small Range GET request probe (Range: bytes=0-1024) to confirm 200/206 status.
     * 4. Content-Type and video format verification.
     * 5. Extraction of server error message on 4xx/5xx responses.
     */
    suspend fun validate(
        rawUrl: String,
        declaredTrailer: Boolean = false,
        durationSeconds: Long = 0L,
        requestHeaders: Map<String, String> = emptyMap()
    ): StreamValidationResult = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext StreamValidationResult(
                isValid = false,
                serverErrorMessage = "Stream URL is blank"
            )
        }

        // 1. Syntax check via standard JVM URI
        val javaUri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return@withContext StreamValidationResult(
                isValid = false,
                serverErrorMessage = "Malformed stream URL: ${e.message}"
            )
        }

        val scheme = javaUri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return@withContext StreamValidationResult(
                isValid = false,
                serverErrorMessage = "Unsupported URI scheme: $scheme"
            )
        }

        val host = javaUri.host
        if (host.isNullOrBlank()) {
            return@withContext StreamValidationResult(
                isValid = false,
                serverErrorMessage = "Stream URL has empty host"
            )
        }

        // 2. Token Expiration Check
        val timestampParam = getQueryParam(trimmed, "t")
            ?: getQueryParam(trimmed, "exp")
            ?: getQueryParam(trimmed, "expires")
            ?: getQueryParam(trimmed, "expire")

        var isExpired = false
        if (timestampParam != null) {
            val tokenTimeSeconds = timestampParam.toLongOrNull()
            if (tokenTimeSeconds != null) {
                val currentTimeSeconds = System.currentTimeMillis() / 1000
                if (tokenTimeSeconds < currentTimeSeconds) {
                    isExpired = true
                }
            }
        }

        if (isExpired) {
            StreamXLog.logPlayerError(
                403,
                "STREAM_TOKEN_EXPIRED",
                "Stream URL signature token expired. Host: $host"
            )
            return@withContext StreamValidationResult(
                isValid = false,
                httpStatusCode = 403,
                isExpired = true,
                serverErrorMessage = "Stream token has expired. A fresh URL is required."
            )
        }

        // 3. Proactive HTTP GET probe (Range bytes=0-1024 for non-manifest videos)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(trimmed)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                val isManifest = trimmed.contains(".m3u8", ignoreCase = true) || trimmed.contains(".mpd", ignoreCase = true)
                if (!isManifest) {
                    setRequestProperty("Range", "bytes=0-1024")
                }
                setRequestProperty("Accept", "*/*")

                val customUserAgent = requestHeaders.entries
                    .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                    ?.value
                    ?: "Mozilla/5.0 (Linux; Android 14; STREAMX-Player) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                setRequestProperty("User-Agent", customUserAgent)

                for ((key, value) in requestHeaders) {
                    if (!key.equals("User-Agent", ignoreCase = true)) {
                        setRequestProperty(key, value)
                    }
                }
            }

            val statusCode = connection.responseCode
            val contentType = connection.contentType
            val contentLength = connection.contentLengthLong
            val finalRedirectUrl = connection.url?.toString() ?: trimmed

            val headers = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (k, v) ->
                if (k != null && v.isNotEmpty()) {
                    headers[k] = v.joinToString(", ")
                }
            }

            val isSuccess = statusCode in 200..299 || statusCode == 416 || statusCode in 300..399
            val isPlayable = isSuccess && isVideoContentType(contentType, trimmed)

            val isTrailer = declaredTrailer || (durationSeconds in 1..299) || trimmed.contains("trailer", ignoreCase = true)

            var serverErrorMessage: String? = null
            if (!isSuccess) {
                serverErrorMessage = try {
                    val stream = connection.errorStream ?: connection.inputStream
                    stream?.bufferedReader()?.use { it.readText() }?.take(512)?.trim()
                } catch (_: Exception) {
                    null
                }
            }

            StreamXLog.logPlaybackSourceResolved(
                movieId = host,
                isTrailer = isTrailer,
                availableQualities = listOf(contentType ?: "unknown"),
                hasStreamUrl = isPlayable,
                streamUrlDomain = host
            )

            StreamValidationResult(
                isValid = isPlayable,
                httpStatusCode = statusCode,
                contentType = contentType,
                contentLength = contentLength,
                isExpired = isExpired,
                isTrailer = isTrailer,
                isPlayableVideo = isPlayable,
                serverErrorMessage = serverErrorMessage,
                responseHeaders = headers,
                finalRedirectUrl = finalRedirectUrl,
                diagnosticSummary = "HTTP $statusCode | Content-Type: $contentType | Length: $contentLength | Host: $host"
            )
        } catch (e: Exception) {
            val isTrailer = declaredTrailer || (durationSeconds in 1..299) || trimmed.contains("trailer", ignoreCase = true)
            StreamXLog.logNetworkError("validate-stream", e)
            StreamValidationResult(
                isValid = false,
                httpStatusCode = -1,
                isTrailer = isTrailer,
                serverErrorMessage = "Connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun getQueryParam(rawUrl: String, paramName: String): String? {
        val queryIndex = rawUrl.indexOf('?')
        if (queryIndex < 0) return null
        val query = rawUrl.substring(queryIndex + 1)
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0].equals(paramName, ignoreCase = true)) {
                return parts[1]
            }
        }
        return null
    }

    private fun isVideoContentType(contentType: String?, url: String): Boolean {
        if (contentType == null) {
            return url.contains(".mp4", ignoreCase = true) ||
                    url.contains(".m3u8", ignoreCase = true) ||
                    url.contains(".mpd", ignoreCase = true) ||
                    url.contains(".mkv", ignoreCase = true) ||
                    url.contains(".webm", ignoreCase = true)
        }
        val lower = contentType.lowercase()
        return lower.startsWith("video/") ||
                lower.contains("audio/mpegurl") ||
                lower.contains("application/x-mpegurl") ||
                lower.contains("application/vnd.apple.mpegurl") ||
                lower.contains("application/dash+xml") ||
                lower.contains("application/octet-stream") ||
                (lower.contains("text/plain") && (url.contains(".m3u8", ignoreCase = true) || url.contains(".mpd", ignoreCase = true))) ||
                url.contains(".mpd", ignoreCase = true) ||
                url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".mp4", ignoreCase = true) ||
                url.contains(".mkv", ignoreCase = true) ||
                url.contains(".webm", ignoreCase = true)
    }
}
