package com.streamx.app.domain.repository

import com.streamx.app.domain.model.PlaybackSource

/**
 * Playback repository.
 * Dedicated strictly to authorized streaming URLs, DRM manifests, and HLS/DASH/MP4 assets.
 * Kept strictly isolated from metadata APIs.
 */
interface PlaybackRepository {
    suspend fun getPlaybackSource(mediaId: String, forceRefresh: Boolean = false): PlaybackSource
    suspend fun getAvailablePlaybackSources(mediaId: String, forceRefresh: Boolean = false): List<PlaybackSource>
    suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long)
    suspend fun getPlaybackPosition(mediaId: String): Long

    fun setCurrentSelectedSource(source: PlaybackSource?)
    fun getCurrentSelectedSource(mediaId: String? = null, streamUrl: String? = null): PlaybackSource?
}
