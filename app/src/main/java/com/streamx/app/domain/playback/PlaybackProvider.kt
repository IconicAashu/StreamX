package com.streamx.app.domain.playback

import com.streamx.app.domain.model.PlaybackSource

enum class PlaybackEventType {
    START,
    PROGRESS,
    STOP
}

/**
 * Universal, provider-independent streaming playback interface.
 * Isolates provider-specific authentication, headers, format resolution, and progress reporting.
 */
interface PlaybackProvider {
    val providerId: String
    val displayName: String

    suspend fun canHandle(mediaId: String): Boolean
    suspend fun getStreams(mediaId: String): List<PlaybackSource>
    suspend fun reportProgress(mediaId: String, positionMs: Long, durationMs: Long, isPaused: Boolean, event: PlaybackEventType) {}
}
