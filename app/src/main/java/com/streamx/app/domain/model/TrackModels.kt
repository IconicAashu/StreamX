package com.streamx.app.domain.model

data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean = false,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1
)

data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val language: String,
    val isOff: Boolean = false,
    val isSelected: Boolean = false,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val uri: String? = null
) {
    companion object {
        val OFF = SubtitleTrackOption(
            id = "off",
            label = "Off",
            language = "",
            isOff = true,
            isSelected = true
        )
    }
}

data class VideoQualityOption(
    val label: String,
    val heightPixels: Int,
    val bitrate: Int = 0,
    val isAuto: Boolean = false,
    val isSelected: Boolean = false,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val streamUrl: String? = null
) {
    companion object {
        val AUTO = VideoQualityOption(
            label = "Auto",
            heightPixels = 0,
            isAuto = true,
            isSelected = true
        )
    }
}
