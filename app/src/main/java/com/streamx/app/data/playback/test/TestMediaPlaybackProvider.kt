package com.streamx.app.data.playback.test

import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.playback.PlaybackEventType
import com.streamx.app.domain.playback.PlaybackProvider
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Controlled Media3 Test Playback Provider.
 * Provides verified, publicly accessible sample videos independent of third-party add-ons
 * for testing, verification, and end-to-end player diagnostics.
 */
class TestMediaPlaybackProvider : PlaybackProvider {

    override val providerId: String = "test_media"
    override val displayName: String = "Controlled Media3 Test Provider"

    override suspend fun canHandle(mediaId: String): Boolean {
        return mediaId.startsWith("test_", ignoreCase = true) ||
                mediaId.startsWith("sample_", ignoreCase = true) ||
                mediaId.equals("big_buck_bunny", ignoreCase = true) ||
                mediaId.equals("media3_test_stream", ignoreCase = true)
    }

    override suspend fun getStreams(mediaId: String): List<PlaybackSource> = withContext(Dispatchers.IO) {
        StreamXLog.d("TestMediaPlaybackProvider", "Resolving controlled sample streams for test mediaId: $mediaId")

        val isHls = mediaId.contains("hls", ignoreCase = true)
        val streamUrl = if (isHls) {
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
        } else {
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
        }

        listOf(
            PlaybackSource(
                mediaId = mediaId,
                title = "Controlled Media3 Test Stream (${if (isHls) "HLS" else "MP4"})",
                streamUrl = streamUrl,
                isLiveOrHls = isHls,
                durationSeconds = 600L,
                licenseNotice = "Controlled Public Domain Sample • Independent Test Source",
                availableQualities = listOf(VideoQuality.QUALITY_1080P, VideoQuality.QUALITY_720P, VideoQuality.AUTO),
                availableAudioTracks = listOf("English (Stereo)"),
                availableSubtitles = listOf("Off", "English"),
                providerName = "Controlled Test Source",
                resolution = if (isHls) "1080p" else "180p",
                codec = if (isHls) "H.264/AAC" else "H.264",
                fileSize = if (isHls) "Adaptive HLS" else "6.4 MB"
            )
        )
    }

    override suspend fun reportProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean,
        event: PlaybackEventType
    ) {
        StreamXLog.d("TestMediaPlaybackProvider", "reportProgress: $mediaId, pos=$positionMs, dur=$durationMs, event=$event")
    }
}
