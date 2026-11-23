@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamx.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streamx.app.domain.model.AudioTrackOption
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.SubtitleTrackOption
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.model.VideoQualityOption
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class Media3PlayerManager(
    private val context: Context
) {
    private val trackSelector = DefaultTrackSelector(context)

    private val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 14; STREAMX-Player) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setDefaultRequestProperties(
            mapOf(
                "Accept" to "*/*",
                "Accept-Encoding" to "identity"
            )
        )

    private val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

    private val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
        .setDataSourceFactory(defaultDataSourceFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .setSeekForwardIncrementMs(10000)
        .setSeekBackIncrementMs(10000)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var isReleased = false
    val isPlayerReleased: Boolean get() = isReleased

    private var currentSource: PlaybackSource? = null
    private var activeResolutionStreamUrl: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Dynamic Track Options detected from live media stream
    private val _availableAudioTracks = MutableStateFlow<List<AudioTrackOption>>(emptyList())
    val availableAudioTracks: StateFlow<List<AudioTrackOption>> = _availableAudioTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow<AudioTrackOption?>(null)
    val selectedAudioTrack: StateFlow<AudioTrackOption?> = _selectedAudioTrack.asStateFlow()

    private val _availableSubtitleTracks = MutableStateFlow<List<SubtitleTrackOption>>(listOf(SubtitleTrackOption.OFF))
    val availableSubtitleTracks: StateFlow<List<SubtitleTrackOption>> = _availableSubtitleTracks.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow(SubtitleTrackOption.OFF)
    val selectedSubtitleTrack: StateFlow<SubtitleTrackOption> = _selectedSubtitleTrack.asStateFlow()

    private val _availableVideoQualities = MutableStateFlow<List<VideoQualityOption>>(listOf(VideoQualityOption.AUTO))
    val availableVideoQualities: StateFlow<List<VideoQualityOption>> = _availableVideoQualities.asStateFlow()

    private val _selectedVideoQuality = MutableStateFlow(VideoQualityOption.AUTO)
    val selectedVideoQuality: StateFlow<VideoQualityOption> = _selectedVideoQuality.asStateFlow()

    // Backward-compatibility string representations for UI
    private val _legacySelectedQuality = MutableStateFlow(VideoQuality.AUTO)
    val legacySelectedQuality: StateFlow<VideoQuality> = _legacySelectedQuality.asStateFlow()

    private val _legacySelectedAudio = MutableStateFlow("Original Audio")
    val legacySelectedAudio: StateFlow<String> = _legacySelectedAudio.asStateFlow()

    private val _legacySelectedSubtitle = MutableStateFlow("Off")
    val legacySelectedSubtitle: StateFlow<String> = _legacySelectedSubtitle.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _streamNotice = MutableStateFlow<String?>(null)
    val streamNotice: StateFlow<String?> = _streamNotice.asStateFlow()

    private val _playbackStatus = MutableStateFlow(com.streamx.app.domain.model.PlaybackStatus.SOURCE_DISCOVERED)
    val playbackStatus: StateFlow<com.streamx.app.domain.model.PlaybackStatus> = _playbackStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            StreamXLog.d("Media3PlayerManager", "onIsPlayingChanged: isPlaying=$isPlaying | playbackState=${exoPlayer.playbackState}")
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            StreamXLog.d("Media3PlayerManager", "onPlaybackStateChanged: $stateName (code=$playbackState), playWhenReady=${exoPlayer.playWhenReady}")

            _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) {
                _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                _bufferedPosition.value = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                _errorMessage.value = null
                if (exoPlayer.playWhenReady) {
                    _playbackStatus.value = com.streamx.app.domain.model.PlaybackStatus.PLAYBACK_VERIFIED
                    _streamNotice.value = "${currentSource?.providerName ?: "STREAMX"} • ${com.streamx.app.domain.model.PlaybackStatus.PLAYBACK_VERIFIED.label}"
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableTracks(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Isolate subtitle parsing/loading errors so they do not crash or abort video playback
            val isSubtitleError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED &&
                    _availableSubtitleTracks.value.any { !it.isOff && it.isSelected } ||
                    (error.cause?.message?.contains("subtitle", ignoreCase = true) == true) ||
                    (error.cause?.message?.contains("vtt", ignoreCase = true) == true) ||
                    (error.cause?.message?.contains("srt", ignoreCase = true) == true)

            if (isSubtitleError && !_selectedSubtitleTrack.value.isOff) {
                StreamXLog.w("Media3PlayerManager", "Subtitle decoding/loading error encountered (${error.message}). Disabling subtitles to keep video playing.")
                try {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    _selectedSubtitleTrack.value = SubtitleTrackOption.OFF
                    _availableSubtitleTracks.value = _availableSubtitleTracks.value.map {
                        it.copy(isSelected = it.isOff)
                    }
                    _streamNotice.value = "Subtitle unavailable. Video playback continuing."
                    return
                } catch (_: Exception) {}
            }

            _isBuffering.value = false
            _isPlaying.value = false

            var invalidResponse: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException? = null
            val causeChainList = mutableListOf<String>()
            var cause: Throwable? = error
            var depth = 0
            while (cause != null) {
                if (depth > 0) {
                    causeChainList.add("[$depth] ${cause.javaClass.name}: ${cause.message}")
                }
                if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && invalidResponse == null) {
                    invalidResponse = cause
                }
                cause = cause.cause
                depth++
            }
            val causeChain = if (causeChainList.isEmpty()) "None (root error)" else causeChainList.joinToString(" -> ")

            val httpCode = invalidResponse?.responseCode
            val httpMsg = invalidResponse?.responseMessage
            val errorBody = try {
                invalidResponse?.responseBody?.let { String(it, Charsets.UTF_8).take(256) }
            } catch (_: Exception) { null }

            val rawStreamUrl = currentSource?.streamUrl ?: activeResolutionStreamUrl ?: ""
            val redactedUrl = redactStreamUrl(rawStreamUrl)
            val streamType = when {
                rawStreamUrl.contains(".m3u8", ignoreCase = true) -> "HLS (.m3u8)"
                rawStreamUrl.contains(".mpd", ignoreCase = true) -> "DASH (.mpd)"
                rawStreamUrl.contains(".mp4", ignoreCase = true) -> "MP4"
                rawStreamUrl.contains(".mkv", ignoreCase = true) -> "MKV"
                rawStreamUrl.contains(".webm", ignoreCase = true) -> "WebM"
                else -> "Auto-detected"
            }
            val providerName = currentSource?.providerName ?: "Unknown Provider"
            val stateName = when (exoPlayer.playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "STATE_${exoPlayer.playbackState}"
            }

            StreamXLog.logPlayerDiagnosticError(
                errorCode = error.errorCode,
                errorCodeName = error.errorCodeName,
                message = error.message,
                causeChain = causeChain,
                httpStatusCode = httpCode,
                httpStatusMessage = httpMsg ?: errorBody,
                redactedUrl = redactedUrl,
                streamType = streamType,
                providerName = providerName,
                playbackState = stateName
            )

            if (httpCode != null) {
                val userMsg = when (httpCode) {
                    401 -> "Authentication required (HTTP 401). Please check credentials."
                    403 -> "Access denied (HTTP 403). Stream token expired or VIP restricted."
                    404 -> "Video unavailable on server (HTTP 404)."
                    429 -> "Too many requests to provider (HTTP 429). Please wait."
                    in 500..599 -> "Streaming server unavailable (HTTP $httpCode)."
                    else -> "Streaming error: HTTP $httpCode ($httpMsg). Tap to retry."
                }
                _errorMessage.value = userMsg
            } else {
                _errorMessage.value = "Playback error (${error.errorCodeName}). Tap to retry."
            }
        }
    }

    init {
        try {
            if (java.net.CookieHandler.getDefault() == null) {
                java.net.CookieHandler.setDefault(
                    java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER)
                )
            }
        } catch (_: Throwable) {}
        exoPlayer.addListener(playerListener)
        try {
            exoPlayer.addAnalyticsListener(
                androidx.media3.exoplayer.util.EventLogger(trackSelector, "StreamXPlayer")
            )
        } catch (e: Throwable) {
            StreamXLog.w("Media3PlayerManager", "Could not attach EventLogger: ${e.message}")
        }
    }

    private fun redactStreamUrl(url: String?): String {
        if (url.isNullOrBlank()) return "[EMPTY]"
        return try {
            url.replace(
                Regex("([?&](?:token|key|sig|auth|t|expires|pass|apiKey|secret)=)[^&]+", RegexOption.IGNORE_CASE),
                "$1[REDACTED]"
            )
        } catch (_: Exception) {
            url
        }
    }

    fun prepareAndPlay(source: PlaybackSource) {
        currentSource = source
        activeResolutionStreamUrl = null
        prepareAndPlayInternal(source, startPositionMs = 0L)
    }

    private fun prepareAndPlayInternal(source: PlaybackSource, startPositionMs: Long = 0L) {
        if (isReleased) return
        _errorMessage.value = null
        _playbackStatus.value = com.streamx.app.domain.model.PlaybackStatus.SOURCE_DISCOVERED
        _streamNotice.value = "${source.providerName} • ${com.streamx.app.domain.model.PlaybackStatus.SOURCE_DISCOVERED.label}"

        val rawUrl = source.streamUrl.trim()
        if (rawUrl.isBlank()) {
            _errorMessage.value = "Authorized video stream is currently restricted by the provider."
            return
        }

        // Diagnostic trace of selected stream
        val redactedUrl = redactStreamUrl(rawUrl)
        val safeHeaderSummary = source.requestHeaders.entries.joinToString(", ") { (k, v) ->
            val safeV = if (k.equals("Authorization", ignoreCase = true) || k.equals("Cookie", ignoreCase = true) || k.contains("token", ignoreCase = true)) "[REDACTED]" else v
            "$k: $safeV"
        }
        StreamXLog.d(
            "Media3PlayerManager",
            "Trace Source >> Provider: ${source.providerName} | Title: ${source.title} | URL: $redactedUrl | Headers: [${safeHeaderSummary.ifBlank { "None" }}]"
        )

        scope.launch {
            // Validate stream URL range probe & signature before setting into ExoPlayer
            val validation = StreamUrlValidator.validate(rawUrl, source.isTrailer, source.durationSeconds, source.requestHeaders)
            if (validation.isExpired) {
                StreamXLog.w("Media3PlayerManager", "Stream token has expired for $redactedUrl")
                _errorMessage.value = "Stream token has expired. A fresh URL is required."
                return@launch
            }
            if (validation.httpStatusCode in listOf(401, 403, 404)) {
                val errorMsg = when (validation.httpStatusCode) {
                    401 -> "Authentication required (HTTP 401). Please check credentials."
                    403 -> "Access denied (HTTP 403). Stream token expired or VIP restricted."
                    404 -> "Video unavailable on server (HTTP 404)."
                    else -> validation.serverErrorMessage ?: "Playback stream cannot be loaded."
                }
                _errorMessage.value = errorMsg
                return@launch
            }
            if (validation.httpStatusCode == 400) {
                val errorMsg = "Provider request rejected (HTTP 400 Bad Request): ${validation.serverErrorMessage ?: "Invalid stream parameters or missing headers."}"
                _errorMessage.value = errorMsg
                StreamXLog.logPlayerDiagnosticError(
                    errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS",
                    message = "Pre-flight validation failed with HTTP 400",
                    causeChain = "StreamUrlValidator probe HTTP 400",
                    httpStatusCode = 400,
                    httpStatusMessage = validation.serverErrorMessage,
                    redactedUrl = redactedUrl,
                    streamType = if (rawUrl.contains(".m3u8", ignoreCase = true)) "HLS" else "Direct",
                    providerName = source.providerName,
                    playbackState = "PRE_FLIGHT_PROBE"
                )
                return@launch
            }
            if (validation.contentType?.contains("text/html", ignoreCase = true) == true) {
                val errorMsg = "Stream URL returned a webpage (${validation.contentType}), not a playable video or manifest."
                StreamXLog.w("Media3PlayerManager", "$errorMsg for URL: $redactedUrl")
                _errorMessage.value = errorMsg
                return@launch
            }
            if (!validation.isValid && !validation.isPlayableVideo) {
                StreamXLog.w("Media3PlayerManager", "Pre-flight validation warning: ${validation.serverErrorMessage}. Proceeding with ExoPlayer pipeline.")
            }

            _playbackStatus.value = com.streamx.app.domain.model.PlaybackStatus.URL_VALIDATED
            _streamNotice.value = "${source.providerName} • ${com.streamx.app.domain.model.PlaybackStatus.URL_VALIDATED.label}"

            try {
                val mediaItemBuilder = Media3Item.Builder()
                    .setUri(Uri.parse(rawUrl))

                when {
                    rawUrl.contains(".m3u8", ignoreCase = true) -> {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                    rawUrl.contains(".mpd", ignoreCase = true) -> {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                    }
                    rawUrl.contains(".mp4", ignoreCase = true) -> {
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
                    }
                    rawUrl.contains(".mkv", ignoreCase = true) -> {
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                    }
                    rawUrl.contains(".webm", ignoreCase = true) -> {
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_WEBM)
                    }
                }

                // Attach external subtitles if legitimately provided (SRT / WebVTT / TTML / SSA)
                if (source.externalSubtitles.isNotEmpty()) {
                    val subtitleConfigs = source.externalSubtitles.mapNotNull { sub ->
                        val uriStr = sub.uri ?: return@mapNotNull null
                        val mime = when {
                            uriStr.endsWith(".vtt", ignoreCase = true) || uriStr.contains(".vtt?", ignoreCase = true) -> MimeTypes.TEXT_VTT
                            uriStr.endsWith(".ttml", ignoreCase = true) || uriStr.contains(".ttml?", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
                            uriStr.endsWith(".ssa", ignoreCase = true) || uriStr.endsWith(".ass", ignoreCase = true) || uriStr.contains(".ass?", ignoreCase = true) -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.APPLICATION_SUBRIP
                        }
                        Media3Item.SubtitleConfiguration.Builder(Uri.parse(uriStr))
                            .setMimeType(mime)
                            .setLanguage(sub.language.ifBlank { "en" })
                            .setLabel(sub.label)
                            .setSelectionFlags(if (sub.isSelected) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }
                    mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
                }

                val mediaItem = mediaItemBuilder.build()

                // Case-insensitively extract custom User-Agent from source headers
                val customUserAgent = source.requestHeaders.entries
                    .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                    ?.value
                    ?: "Mozilla/5.0 (Linux; Android 14; STREAMX-Player) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                val requestProps = mutableMapOf(
                    "Accept" to "*/*"
                )
                // Filter out User-Agent from defaultRequestProperties so it doesn't conflict with setUserAgent()
                for ((key, value) in source.requestHeaders) {
                    if (!key.equals("User-Agent", ignoreCase = true)) {
                        requestProps[key] = value
                    }
                }

                val customHttpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent(customUserAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setDefaultRequestProperties(requestProps)

                val customDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, customHttpFactory)
                val customMediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(customDataSourceFactory)
                val mediaSource = customMediaSourceFactory.createMediaSource(mediaItem)
                exoPlayer.setMediaSource(mediaSource)

                exoPlayer.prepare()
                if (startPositionMs > 0L) {
                    exoPlayer.seekTo(startPositionMs)
                }
                exoPlayer.playWhenReady = true
                _playbackStatus.value = com.streamx.app.domain.model.PlaybackStatus.PLAYBACK_STARTED
                _streamNotice.value = "${source.providerName} • ${com.streamx.app.domain.model.PlaybackStatus.PLAYBACK_STARTED.label}"
                _duration.value = 0L
                _currentPosition.value = startPositionMs
            } catch (e: Exception) {
                StreamXLog.logPlayerError(-1, "PREPARE_FAIL", e.localizedMessage)
                _errorMessage.value = "Failed to open stream: ${e.localizedMessage}"
            }
        }
    }

    private fun updateAvailableTracks(tracks: Tracks) {
        val groups = tracks.groups

        // 1. Audio Track Extraction
        val audioList = mutableListOf<AudioTrackOption>()
        val currentAudioOverride = exoPlayer.trackSelectionParameters.overrides.values.firstOrNull {
            it.type == C.TRACK_TYPE_AUDIO
        }

        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            if (group.type == C.TRACK_TYPE_AUDIO && group.isSupported) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSupported(trackIndex)) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = if (currentAudioOverride != null) {
                            currentAudioOverride.mediaTrackGroup == group.mediaTrackGroup &&
                                    currentAudioOverride.trackIndices.contains(trackIndex)
                        } else {
                            group.isTrackSelected(trackIndex)
                        }
                        val lang = format.language ?: ""
                        val displayLang = if (lang.isNotBlank()) {
                            try {
                                val loc = Locale.forLanguageTag(lang)
                                loc.displayLanguage.takeIf { it.isNotBlank() } ?: lang
                            } catch (_: Exception) { lang }
                        } else ""
                        val label = format.label?.takeIf { it.isNotBlank() }
                            ?: displayLang.takeIf { it.isNotBlank() }
                            ?: "Audio Track ${audioList.size + 1}"

                        audioList.add(
                            AudioTrackOption(
                                id = "audio_${groupIndex}_$trackIndex",
                                label = label,
                                language = lang,
                                isSelected = isSelected,
                                groupIndex = groupIndex,
                                trackIndex = trackIndex
                            )
                        )
                    }
                }
            }
        }
        _availableAudioTracks.value = audioList
        val selectedAudio = audioList.find { it.isSelected } ?: audioList.firstOrNull()
        _selectedAudioTrack.value = selectedAudio
        selectedAudio?.let { _legacySelectedAudio.value = it.label }

        // 2. Subtitle Track Extraction
        val subList = mutableListOf<SubtitleTrackOption>()
        val isTextDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val currentTextOverride = exoPlayer.trackSelectionParameters.overrides.values.firstOrNull {
            it.type == C.TRACK_TYPE_TEXT
        }
        var hasSelectedSub = false

        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            if (group.type == C.TRACK_TYPE_TEXT && group.isSupported) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSupported(trackIndex)) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = !isTextDisabled && if (currentTextOverride != null) {
                            currentTextOverride.mediaTrackGroup == group.mediaTrackGroup &&
                                    currentTextOverride.trackIndices.contains(trackIndex)
                        } else {
                            group.isTrackSelected(trackIndex)
                        }
                        if (isSelected) hasSelectedSub = true
                        val lang = format.language ?: ""
                        val displayLang = if (lang.isNotBlank()) {
                            try {
                                val loc = Locale.forLanguageTag(lang)
                                loc.displayLanguage.takeIf { it.isNotBlank() } ?: lang
                            } catch (_: Exception) { lang }
                        } else ""
                        val label = format.label?.takeIf { it.isNotBlank() }
                            ?: displayLang.takeIf { it.isNotBlank() }
                            ?: "Subtitle ${subList.size + 1}"

                        subList.add(
                            SubtitleTrackOption(
                                id = "sub_${groupIndex}_$trackIndex",
                                label = label,
                                language = lang,
                                isSelected = isSelected,
                                groupIndex = groupIndex,
                                trackIndex = trackIndex
                            )
                        )
                    }
                }
            }
        }

        val allSubs = mutableListOf<SubtitleTrackOption>()
        val offOption = SubtitleTrackOption(
            id = "off",
            label = "Off",
            language = "",
            isOff = true,
            isSelected = isTextDisabled || !hasSelectedSub
        )
        allSubs.add(offOption)
        allSubs.addAll(subList)

        _availableSubtitleTracks.value = allSubs
        val activeSub = if (isTextDisabled || !hasSelectedSub) offOption else subList.find { it.isSelected } ?: offOption
        _selectedSubtitleTrack.value = activeSub
        _legacySelectedSubtitle.value = activeSub.label

        // 3. Video Quality Extraction (Adaptive HLS/DASH + Multi-URL streams + Single MP4)
        val qualityList = mutableListOf<VideoQualityOption>()
        val detectedResolutions = mutableListOf<Pair<Tracks.Group, Int>>()

        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            if (group.type == C.TRACK_TYPE_VIDEO && group.isSupported) {
                for (trackIndex in 0 until group.length) {
                    if (group.isTrackSupported(trackIndex)) {
                        val format = group.getTrackFormat(trackIndex)
                        if (format.height > 0) {
                            detectedResolutions.add(group to trackIndex)
                        }
                    }
                }
            }
        }

        val currentVideoOverride = exoPlayer.trackSelectionParameters.overrides.values.firstOrNull {
            it.type == C.TRACK_TYPE_VIDEO
        }
        val isAuto = (currentVideoOverride == null && activeResolutionStreamUrl == null)

        val isMultiVariantAdaptive = detectedResolutions.distinctBy { it.first.getTrackFormat(it.second).height }.size > 1 ||
                !currentSource?.resolutionStreams.isNullOrEmpty()

        if (isMultiVariantAdaptive) {
            // Adaptive multi-variant streams (HLS/DASH/multi-URL): Include Auto option
            qualityList.add(
                VideoQualityOption(
                    label = "Auto",
                    heightPixels = 0,
                    isAuto = true,
                    isSelected = isAuto
                )
            )

            if (detectedResolutions.isNotEmpty()) {
                val uniqueHeights = detectedResolutions
                    .distinctBy { it.first.getTrackFormat(it.second).height }
                    .sortedBy { it.first.getTrackFormat(it.second).height }

                uniqueHeights.forEach { (group, trackIndex) ->
                    val format = group.getTrackFormat(trackIndex)
                    val h = format.height
                    val isSelected = !isAuto && (currentVideoOverride?.mediaTrackGroup == group.mediaTrackGroup &&
                            currentVideoOverride?.trackIndices?.contains(trackIndex) == true)
                    qualityList.add(
                        VideoQualityOption(
                            label = formatQualityLabel(h),
                            heightPixels = h,
                            bitrate = format.bitrate,
                            isSelected = isSelected,
                            groupIndex = groups.indexOf(group),
                            trackIndex = trackIndex
                        )
                    )
                }
            } else if (!currentSource?.resolutionStreams.isNullOrEmpty()) {
                val streams = currentSource!!.resolutionStreams.toSortedMap()
                streams.forEach { (height, url) ->
                    val isSelected = (!isAuto && activeResolutionStreamUrl == url)
                    qualityList.add(
                        VideoQualityOption(
                            label = formatQualityLabel(height),
                            heightPixels = height,
                            streamUrl = url,
                            isSelected = isSelected
                        )
                    )
                }
            }
        } else if (detectedResolutions.isNotEmpty()) {
            // Single video track / non-adaptive stream (e.g. single MP4/MKV):
            // Show only the actual detected quality without redundant Auto
            val (group, trackIndex) = detectedResolutions.first()
            val format = group.getTrackFormat(trackIndex)
            val h = format.height
            qualityList.add(
                VideoQualityOption(
                    label = formatQualityLabel(h),
                    heightPixels = h,
                    bitrate = format.bitrate,
                    isSelected = true,
                    groupIndex = groups.indexOf(group),
                    trackIndex = trackIndex
                )
            )
        } else {
            // Video dimensions unknown / unavailable:
            qualityList.add(
                VideoQualityOption(
                    label = "Auto (Standard)",
                    heightPixels = 0,
                    isAuto = true,
                    isSelected = true
                )
            )
        }

        _availableVideoQualities.value = qualityList
        val activeQuality = qualityList.find { it.isSelected } ?: qualityList.first()
        _selectedVideoQuality.value = activeQuality
    }

    private fun formatQualityLabel(height: Int): String {
        return when (height) {
            2160 -> "2160p (4K)"
            1440 -> "1440p (2K)"
            1080 -> "1080p (Full HD)"
            720 -> "720p (HD)"
            480 -> "480p (SD)"
            360 -> "360p"
            240 -> "240p"
            else -> "${height}p"
        }
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        if (isReleased) return
        try {
            val tracks = exoPlayer.currentTracks
            if (option.groupIndex in 0 until tracks.groups.size) {
                val group = tracks.groups[option.groupIndex]
                if (option.trackIndex in 0 until group.length) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setOverrideForType(override)
                        .setPreferredAudioLanguage(option.language.takeIf { it.isNotBlank() })
                        .build()

                    _selectedAudioTrack.value = option.copy(isSelected = true)
                    _legacySelectedAudio.value = option.label
                    _availableAudioTracks.value = _availableAudioTracks.value.map {
                        it.copy(isSelected = (it.id == option.id))
                    }
                    StreamXLog.logAudioTrackSelected(option.label, option.language)
                }
            }
        } catch (e: Exception) {
            StreamXLog.logPlayerError(-1, "SELECT_AUDIO_ERR", e.message)
        }
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption) {
        if (isReleased) return
        try {
            if (option.isOff) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()

                _selectedSubtitleTrack.value = option.copy(isSelected = true)
                _legacySelectedSubtitle.value = "Off"
                _availableSubtitleTracks.value = _availableSubtitleTracks.value.map {
                    it.copy(isSelected = it.id == option.id)
                }
                StreamXLog.logSubtitleTrackSelected("Off", "")
            } else {
                val tracks = exoPlayer.currentTracks
                if (option.groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[option.groupIndex]
                    if (option.trackIndex in 0 until group.length) {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setOverrideForType(override)
                            .setPreferredTextLanguage(option.language.takeIf { it.isNotBlank() })
                            .build()

                        _selectedSubtitleTrack.value = option.copy(isSelected = true)
                        _legacySelectedSubtitle.value = option.label
                        _availableSubtitleTracks.value = _availableSubtitleTracks.value.map {
                            it.copy(isSelected = it.id == option.id)
                        }
                        StreamXLog.logSubtitleTrackSelected(option.label, option.language)
                    }
                }
            }
        } catch (e: Exception) {
            StreamXLog.logPlayerError(-1, "SELECT_SUBTITLE_ERR", e.message)
        }
    }

    fun selectVideoQuality(option: VideoQualityOption) {
        if (isReleased) return
        try {
            if (option.isAuto) {
                activeResolutionStreamUrl = null
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .clearVideoSizeConstraints()
                    .build()

                _selectedVideoQuality.value = option.copy(isSelected = true)
                _availableVideoQualities.value = _availableVideoQualities.value.map {
                    it.copy(isSelected = it.isAuto)
                }
                StreamXLog.logQualitySelected("Auto", 0)
            } else if (option.streamUrl != null && option.streamUrl != currentSource?.streamUrl) {
                // Multi-URL stream: preserve current position across URL switch!
                val currentPos = exoPlayer.currentPosition
                activeResolutionStreamUrl = option.streamUrl
                val newSource = currentSource?.copy(streamUrl = option.streamUrl) ?: return
                prepareAndPlayInternal(newSource, startPositionMs = currentPos)
                _selectedVideoQuality.value = option.copy(isSelected = true)
                _availableVideoQualities.value = _availableVideoQualities.value.map {
                    it.copy(isSelected = it.heightPixels == option.heightPixels)
                }
                StreamXLog.logQualitySelected(option.label, option.heightPixels)
            } else {
                // Adaptive track in HLS/DASH manifest
                val tracks = exoPlayer.currentTracks
                if (option.groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[option.groupIndex]
                    if (option.trackIndex in 0 until group.length) {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .setOverrideForType(override)
                            .clearVideoSizeConstraints()
                            .build()

                        _selectedVideoQuality.value = option.copy(isSelected = true)
                        _availableVideoQualities.value = _availableVideoQualities.value.map {
                            it.copy(isSelected = it.heightPixels == option.heightPixels)
                        }
                        StreamXLog.logQualitySelected(option.label, option.heightPixels)
                    }
                }
            }
        } catch (e: Exception) {
            StreamXLog.logPlayerError(-1, "SELECT_QUALITY_ERR", e.message)
        }
    }

    fun retry() {
        currentSource?.let { prepareAndPlay(it) }
    }

    fun play() {
        if (isReleased) return
        try {
            exoPlayer.play()
        } catch (_: Exception) {}
    }

    fun pause() {
        if (isReleased) return
        try {
            exoPlayer.pause()
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeed(speed: Float) {
        if (isReleased) return
        try {
            val clamped = speed.coerceIn(0.25f, 3.0f)
            exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(clamped)
            _playbackSpeed.value = clamped
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (isReleased) return
        try {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Long) {
        if (isReleased) return
        try {
            exoPlayer.seekTo(positionMs.coerceIn(0L, _duration.value.coerceAtLeast(1L)))
            _currentPosition.value = exoPlayer.currentPosition
        } catch (_: Exception) {}
    }

    fun seekForward(offsetMs: Long = 10000L) {
        if (isReleased) return
        try {
            val target = (exoPlayer.currentPosition + offsetMs).coerceAtMost(exoPlayer.duration)
            exoPlayer.seekTo(target)
        } catch (_: Exception) {}
    }

    fun seekBack(offsetMs: Long = 10000L) {
        if (isReleased) return
        try {
            val target = (exoPlayer.currentPosition - offsetMs).coerceAtLeast(0L)
            exoPlayer.seekTo(target)
        } catch (_: Exception) {}
    }

    fun playFallback() {
        // No hardcoded mock videos or fake stream fallbacks
        _errorMessage.value = "Playback unavailable: No authorized stream is currently configured."
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = scope.launch {
            while (isActive) {
                try {
                    if (!isReleased) {
                        _currentPosition.value = exoPlayer.currentPosition
                        _bufferedPosition.value = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                        _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    }
                } catch (_: Exception) {}
                delay(300)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        stopProgressUpdates()
        scope.cancel()
        try {
            exoPlayer.removeListener(playerListener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        } catch (_: Exception) {}
    }
}
