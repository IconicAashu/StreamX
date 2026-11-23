package com.streamx.app.presentation.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.repository.PlaybackRepository
import com.streamx.app.player.Media3PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val source: PlaybackSource? = null,
    val showControls: Boolean = true,
    val isFullscreen: Boolean = true,
    val resizeMode: Int = 0, // 0 = FIT, 1 = ZOOM, 2 = FILL
    val activeBottomSheet: PlayerBottomSheet? = null,
    val errorMessage: String? = null
)

enum class PlayerBottomSheet {
    NONE,
    QUALITY,
    AUDIO,
    SUBTITLES,
    SPEED,
    SOURCES
}

class VideoPlayerViewModel(
    val mediaId: String,
    private val playbackRepository: PlaybackRepository,
    context: Context,
    private val initialStreamUrl: String? = null,
    private val initialTitle: String? = null,
    private val initialProvider: String? = null
) : ViewModel() {

    class Factory(
        private val mediaId: String,
        private val playbackRepository: PlaybackRepository,
        private val context: Context,
        private val initialStreamUrl: String? = null,
        private val initialTitle: String? = null,
        private val initialProvider: String? = null
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VideoPlayerViewModel(
                mediaId = mediaId,
                playbackRepository = playbackRepository,
                context = context,
                initialStreamUrl = initialStreamUrl,
                initialTitle = initialTitle,
                initialProvider = initialProvider
            ) as T
        }
    }

    val playerManager = Media3PlayerManager(context.applicationContext)

    private val _availableSources = MutableStateFlow<List<PlaybackSource>>(emptyList())
    val availableSources: StateFlow<List<PlaybackSource>> = _availableSources.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadPlaybackSource(forceRefresh = false)
    }

    private fun loadPlaybackSource(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                com.streamx.app.utils.StreamXLog.d(
                    "VideoPlayerViewModel",
                    "loadPlaybackSource: mediaId=$mediaId, hasInitialUrl=${!initialStreamUrl.isNullOrBlank()}"
                )

                // 1. Check if a high-fidelity PlaybackSource with headers was selected in repository
                val cachedSelectedSource = playbackRepository.getCurrentSelectedSource(mediaId, initialStreamUrl)

                val effectiveSource = cachedSelectedSource ?: if (!initialStreamUrl.isNullOrBlank() && !forceRefresh) {
                    PlaybackSource(
                        mediaId = mediaId,
                        title = initialTitle ?: "Selected Source",
                        streamUrl = initialStreamUrl,
                        isLiveOrHls = initialStreamUrl.contains(".m3u8", ignoreCase = true),
                        licenseNotice = "${initialProvider ?: "Direct Stream"} • Source discovered",
                        availableQualities = listOf(VideoQuality.AUTO),
                        availableAudioTracks = listOf("Original Audio"),
                        availableSubtitles = listOf("Off"),
                        providerName = initialProvider ?: "Direct Stream"
                    )
                } else null

                if (effectiveSource != null) {
                    _availableSources.value = listOf(effectiveSource)
                    val savedPosition = try {
                        playbackRepository.getPlaybackPosition(mediaId)
                    } catch (_: Exception) {
                        0L
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            source = effectiveSource
                        )
                    }
                    com.streamx.app.utils.StreamXLog.d(
                        "VideoPlayerViewModel",
                        "Playing effective source '${effectiveSource.title}' (provider: ${effectiveSource.providerName}, headers count: ${effectiveSource.requestHeaders.size})"
                    )
                    playerManager.prepareAndPlay(effectiveSource)
                    if (savedPosition > 0L) {
                        playerManager.seekTo(savedPosition)
                    }

                    // Background query to discover alternative sources if available
                    try {
                        val backgroundSources = playbackRepository.getAvailablePlaybackSources(mediaId, forceRefresh = false)
                        val combined = (listOf(effectiveSource) + backgroundSources).distinctBy { it.streamUrl }
                        _availableSources.value = combined
                    } catch (_: Exception) {}
                    return@launch
                }

                // 2. Query registered playback providers (TestMedia, Jellyfin, P2P, Stremio)
                val sources = playbackRepository.getAvailablePlaybackSources(mediaId, forceRefresh = forceRefresh)
                _availableSources.value = sources
                val primarySource = sources.first()
                val savedPosition = try {
                    playbackRepository.getPlaybackPosition(mediaId)
                } catch (_: Exception) {
                    0L
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        source = primarySource
                    )
                }
                com.streamx.app.utils.StreamXLog.d(
                    "VideoPlayerViewModel",
                    "Resolved primary source: ${primarySource.title} (${primarySource.providerName})"
                )
                playerManager.prepareAndPlay(primarySource)
                if (savedPosition > 0L) {
                    playerManager.seekTo(savedPosition)
                }
            } catch (e: Exception) {
                com.streamx.app.utils.StreamXLog.e(
                    "VideoPlayerViewModel",
                    "Failed to load stream for $mediaId: ${e.message}"
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load stream: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun selectSource(source: PlaybackSource) {
        val currentPos = playerManager.currentPosition.value
        _uiState.update { it.copy(source = source, isLoading = true, errorMessage = null) }
        playbackRepository.setCurrentSelectedSource(source)
        playerManager.prepareAndPlay(source)
        if (currentPos > 0L) {
            playerManager.seekTo(currentPos)
        }
        closeBottomSheet()
    }

    fun savePlaybackPosition() {
        val pos = playerManager.currentPosition.value
        val dur = playerManager.duration.value.takeIf { it > 0L }
            ?: ((_uiState.value.source?.durationSeconds ?: 0L) * 1000L)
        if (pos > 0L) {
            viewModelScope.launch {
                playbackRepository.savePlaybackPosition(mediaId, pos, dur)
            }
        }
    }

    fun retryPlayback() {
        loadPlaybackSource(forceRefresh = true)
    }

    val playbackStatus = playerManager.playbackStatus
    val streamNotice = playerManager.streamNotice

    fun playFallback() {
        playerManager.playFallback()
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(showControls = visible) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun cycleResizeMode() {
        _uiState.update { it.copy(resizeMode = (it.resizeMode + 1) % 3) }
    }

    val playbackSpeed = playerManager.playbackSpeed
    val bufferedPosition = playerManager.bufferedPosition

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        closeBottomSheet()
    }

    fun openBottomSheet(sheet: PlayerBottomSheet) {
        _uiState.update { it.copy(activeBottomSheet = sheet) }
    }

    fun closeBottomSheet() {
        _uiState.update { it.copy(activeBottomSheet = PlayerBottomSheet.NONE) }
    }

    val availableAudioTracks = playerManager.availableAudioTracks
    val selectedAudioTrack = playerManager.selectedAudioTrack

    val availableSubtitleTracks = playerManager.availableSubtitleTracks
    val selectedSubtitleTrack = playerManager.selectedSubtitleTrack

    val availableVideoQualities = playerManager.availableVideoQualities
    val selectedVideoQuality = playerManager.selectedVideoQuality

    fun selectQuality(option: com.streamx.app.domain.model.VideoQualityOption) {
        playerManager.selectVideoQuality(option)
        closeBottomSheet()
    }

    fun selectQuality(quality: VideoQuality) {
        val matchingOption = availableVideoQualities.value.find { it.heightPixels == quality.heightPixels }
            ?: com.streamx.app.domain.model.VideoQualityOption.AUTO
        selectQuality(matchingOption)
    }

    fun selectAudio(option: com.streamx.app.domain.model.AudioTrackOption) {
        playerManager.selectAudioTrack(option)
        closeBottomSheet()
    }

    fun selectAudio(track: String) {
        val matchingOption = availableAudioTracks.value.find { it.label == track }
        if (matchingOption != null) {
            selectAudio(matchingOption)
        } else {
            closeBottomSheet()
        }
    }

    fun selectSubtitle(option: com.streamx.app.domain.model.SubtitleTrackOption) {
        playerManager.selectSubtitleTrack(option)
        closeBottomSheet()
    }

    fun selectSubtitle(track: String) {
        val matchingOption = availableSubtitleTracks.value.find { it.label == track }
            ?: com.streamx.app.domain.model.SubtitleTrackOption.OFF
        selectSubtitle(matchingOption)
    }

    fun release() {
        savePlaybackPosition()
        playerManager.release()
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }
}
