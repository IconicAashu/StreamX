package com.streamx.app.domain.model

enum class VideoQuality(val label: String, val heightPixels: Int) {
    AUTO("Auto", 0),
    QUALITY_360P("360p", 360),
    QUALITY_480P("480p", 480),
    QUALITY_720P("720p", 720),
    QUALITY_1080P("1080p", 1080),
    QUALITY_4K("4K Ultra HD", 2160)
}

data class PlaybackSource(
    val mediaId: String,
    val title: String,
    val streamUrl: String,
    val isLiveOrHls: Boolean = false,
    val isTrailer: Boolean = false,
    val durationSeconds: Long = 0L,
    val isFullFeature: Boolean = false,
    val licenseNotice: String = "Authorized Public Stream • Source discovered",
    val requestHeaders: Map<String, String> = emptyMap(),
    val availableQualities: List<VideoQuality> = listOf(
        VideoQuality.AUTO,
        VideoQuality.QUALITY_480P,
        VideoQuality.QUALITY_720P,
        VideoQuality.QUALITY_1080P,
        VideoQuality.QUALITY_4K
    ),
    val availableAudioTracks: List<String> = listOf("Original (Atmos)", "Hindi Dub", "English (Stereo)"),
    val availableSubtitles: List<String> = listOf("Off", "English [CC]", "Hindi", "Spanish", "Japanese"),
    val externalSubtitles: List<SubtitleTrackOption> = emptyList(),
    val resolutionStreams: Map<Int, String> = emptyMap(),
    // Nuvio-style Stream Attributes & Attribution
    val providerName: String = "STREAMX",
    val resolution: String? = null,
    val codec: String? = null,
    val fileSize: String? = null,
    val seeders: Int? = null,
    val qualityBadge: String = "HD"
)
