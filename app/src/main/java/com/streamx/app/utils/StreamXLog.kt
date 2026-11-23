package com.streamx.app.utils

import android.util.Log

/**
 * Structured diagnostics logger for STREAMX API & Media3 Playback.
 * Follows strict security policies: NEVER logs API keys, cookies, or authorization tokens.
 * Safe across both Android runtime and JVM unit test runners.
 */
object StreamXLog {
    private const val TAG = "STREAMX_DIAG"

    private fun log(level: String, msg: String) {
        try {
            when (level) {
                "D" -> Log.d(TAG, msg)
                "I" -> Log.i(TAG, msg)
                "W" -> Log.w(TAG, msg)
                "E" -> Log.e(TAG, msg)
                else -> Log.d(TAG, msg)
            }
        } catch (_: Throwable) {
            println("[$TAG][$level] $msg")
        }
    }

    fun logApiRequest(endpoint: String, page: Int?, category: String?, filters: String? = null) {
        log("D", "API_REQ >> Endpoint: $endpoint | Page: ${page ?: 1} | Category: ${category ?: "default"} | Filters: ${filters ?: "none"}")
    }

    fun logApiResponse(
        endpoint: String,
        statusCode: Int,
        page: Int?,
        itemsCount: Int,
        hasMore: Boolean? = null
    ) {
        log("I", "API_RES << Endpoint: $endpoint | HTTP: $statusCode | Page: ${page ?: 1} | Items: $itemsCount | HasMore: $hasMore")
    }

    fun logMovieSelected(movieId: String, title: String) {
        log("D", "MOVIE_CLICK >> ID: $movieId | Title: $title")
    }

    fun logPlaybackSourceResolved(
        movieId: String,
        isTrailer: Boolean,
        availableQualities: List<String>,
        hasStreamUrl: Boolean,
        streamUrlDomain: String?
    ) {
        log(
            "I",
            "PLAYBACK_SOURCE >> ID: $movieId | IsTrailer: $isTrailer | HasUrl: $hasStreamUrl | Domain: ${streamUrlDomain ?: "none"} | Qualities: $availableQualities"
        )
    }

    fun logPlayerError(errorCode: Int, errorName: String, message: String?) {
        log("E", "PLAYER_ERR !! Code: $errorCode | Name: $errorName | Message: $message")
    }

    fun logPlayerDiagnosticError(
        errorCode: Int,
        errorCodeName: String,
        message: String?,
        causeChain: String,
        httpStatusCode: Int?,
        httpStatusMessage: String?,
        redactedUrl: String,
        streamType: String,
        providerName: String,
        playbackState: String
    ) {
        log(
            "E",
            """
            PLAYER_DIAGNOSTIC_FAILURE !!
            • Error Code: $errorCode ($errorCodeName)
            • Message: $message
            • HTTP Status: ${httpStatusCode?.let { "$it ($httpStatusMessage)" } ?: "N/A"}
            • Provider: $providerName
            • Stream Type: $streamType
            • Redacted URL: $redactedUrl
            • Playback State: $playbackState
            • Cause Chain: $causeChain
            """.trimIndent()
        )
    }

    fun logNetworkError(endpoint: String, throwable: Throwable) {
        log("E", "NET_ERR !! Endpoint: $endpoint | Error: ${throwable.localizedMessage ?: throwable.javaClass.simpleName}")
    }

    fun logAudioTrackSelected(label: String, lang: String) {
        log("I", "AUDIO_TRACK_SELECT >> Label: $label | Lang: $lang")
    }

    fun logSubtitleTrackSelected(label: String, lang: String) {
        log("I", "SUBTITLE_TRACK_SELECT >> Label: $label | Lang: $lang")
    }

    fun logQualitySelected(label: String, height: Int) {
        log("I", "QUALITY_SELECT >> Label: $label | Height: ${height}p")
    }

    fun d(tag: String, msg: String) {
        log("D", "[$tag] $msg")
    }

    fun i(tag: String, msg: String) {
        log("I", "[$tag] $msg")
    }

    fun w(tag: String, msg: String) {
        log("W", "[$tag] $msg")
    }

    fun e(tag: String, msg: String) {
        log("E", "[$tag] $msg")
    }
}
