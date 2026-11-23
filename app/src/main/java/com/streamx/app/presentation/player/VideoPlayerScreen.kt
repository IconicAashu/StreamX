@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.streamx.app.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamx.app.domain.model.AudioTrackOption
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.SubtitleTrackOption
import com.streamx.app.domain.model.VideoQualityOption
import androidx.compose.material.icons.filled.Layers
import com.streamx.app.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * STREAMX Premium Cinematic Video Player
 *
 * Architecture & Features:
 * 1. Automatic Landscape & Immersive Edge-to-Edge:
 *    - Automatically forces SCREEN_ORIENTATION_SENSOR_LANDSCAPE on entry.
 *    - Hides status bar, navigation bar, clock & battery via WindowInsetsControllerCompat.
 *    - Restores previous orientation and system bars on exit.
 *    - Keeps screen awake (FLAG_KEEP_SCREEN_ON) during active playback.
 * 2. Real Media3 ExoPlayer Binding:
 *    - Direct play/pause, seeking, buffering detection, error observation.
 *    - Playback speed control (0.5x to 2.0x).
 *    - Aspect ratio modes (Fit, Zoom/Crop, Stretch).
 * 3. Animated Cinematic Controls:
 *    - Smooth 3-second auto-hide fade animation.
 *    - Animated Play/Pause scale & icon transition.
 *    - Double-tap left/right 10-second seek gesture with animated ripple feedback.
 * 4. High-Precision Custom Seek Bar:
 *    - Buffered progress track + played progress track + scrubbing thumb.
 * 5. Track Selection & Settings in Landscape:
 *    - Sleek side-dialog for Audio, Subtitles, Quality, and Speed options.
 */
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val isBuffering by viewModel.playerManager.isBuffering.collectAsState()
    val currentPosition by viewModel.playerManager.currentPosition.collectAsState()
    val bufferedPosition by viewModel.bufferedPosition.collectAsState()
    val duration by viewModel.playerManager.duration.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    val selectedQualityOption by viewModel.selectedVideoQuality.collectAsState()
    val availableQualities by viewModel.availableVideoQualities.collectAsState()
    val selectedAudioOption by viewModel.selectedAudioTrack.collectAsState()
    val availableAudios by viewModel.availableAudioTracks.collectAsState()
    val selectedSubtitleOption by viewModel.selectedSubtitleTrack.collectAsState()
    val availableSubtitles by viewModel.availableSubtitleTracks.collectAsState()
    val availableSources by viewModel.availableSources.collectAsState()
    val errorMessage by viewModel.playerManager.errorMessage.collectAsState()
    val streamNotice by viewModel.streamNotice.collectAsState()

    // Double-tap seek visual feedback states: Pair(isForward: Boolean, triggerTime: Long)
    var doubleTapSeekFeedback by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }

    // User dragging seekbar state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }

    // Intercept hardware / system back button to safely restore state
    BackHandler {
        viewModel.savePlaybackPosition()
        onBackClick()
    }

    // 1. Force landscape orientation and immersive fullscreen on enter, restore on exit
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        // Switch to landscape immediately
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Immersive edge-to-edge: hide status & navigation bars
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            viewModel.savePlaybackPosition()
            viewModel.playerManager.pause()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Restore system bars and orientation
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 2. Keep screen on during active playback
    DisposableEffect(isPlaying) {
        val window = (context as? Activity)?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 3. Activity Lifecycle Observer: pause and save position on background/pause
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.savePlaybackPosition()
                viewModel.playerManager.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 4. Auto-hide controls timer (3 seconds of inactivity while playing)
    LaunchedEffect(uiState.showControls, isPlaying, isDraggingSlider, doubleTapSeekFeedback) {
        if (uiState.showControls && isPlaying && !isDraggingSlider && uiState.activeBottomSheet == null) {
            delay(3500)
            viewModel.setControlsVisible(false)
        }
    }

    // 5. Dismiss double-tap feedback after 650ms
    LaunchedEffect(doubleTapSeekFeedback) {
        if (doubleTapSeekFeedback != null) {
            delay(650)
            doubleTapSeekFeedback = null
        }
    }

    val displayError = uiState.errorMessage ?: errorMessage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        val isRightHalf = offset.x > (screenWidth / 2)
                        if (isRightHalf) {
                            viewModel.playerManager.seekForward(10000L)
                            doubleTapSeekFeedback = Pair(true, System.currentTimeMillis())
                        } else {
                            viewModel.playerManager.seekBack(10000L)
                            doubleTapSeekFeedback = Pair(false, System.currentTimeMillis())
                        }
                        viewModel.setControlsVisible(true)
                    },
                    onTap = {
                        viewModel.toggleControls()
                    }
                )
            }
    ) {
        // Media3 PlayerView with dynamic AspectRatio resizeMode
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    val p = if (!viewModel.playerManager.isPlayerReleased) viewModel.playerManager.exoPlayer else null
                    player = p
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = when (uiState.resizeMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                val p = if (!viewModel.playerManager.isPlayerReleased) viewModel.playerManager.exoPlayer else null
                if (playerView.player != p) {
                    playerView.player = p
                }
                playerView.resizeMode = when (uiState.resizeMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Double-Tap Seek Visual Feedback Animation
        doubleTapSeekFeedback?.let { (isForward, _) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isForward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                            contentDescription = if (isForward) "+10s" else "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isForward) "+10 sec" else "-10 sec",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD8B4FE)
                        )
                    }
                }
            }
        }

        // Buffering Indicator (Centered Pulsing Loader)
        if (isBuffering && displayError == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFA855F7),
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.5.dp
                )
            }
        }

        // Overlay Controls (Top bar, Center Play/Pause, Bottom Seekbar & Controls)
        AnimatedVisibility(
            visible = uiState.showControls || !isPlaying || displayError != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.85f),
                            0.25f to Color.Black.copy(alpha = 0.35f),
                            0.5f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.45f),
                            1.0f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
            ) {
                // TOP BAR (Left: Back, Title, Year; Right: Audio, Subtitle, Quality, Settings)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Group: Back Button & Metadata
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.savePlaybackPosition()
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = uiState.source?.title ?: "STREAMX Video Player",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = streamNotice ?: uiState.source?.licenseNotice ?: "STREAMX Master Stream",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                                if (selectedQualityOption.heightPixels > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E2A42))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = selectedQualityOption.label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD8B4FE)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Group: Source, Audio, Subtitle, Quality, Speed
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Multi-Source Selection Pill
                        val sourceLabel = uiState.source?.title?.substringBefore(":") ?: "Source"
                        CinematicPillButton(
                            label = sourceLabel.take(14),
                            icon = Icons.Filled.Layers,
                            onClick = { viewModel.openBottomSheet(PlayerBottomSheet.SOURCES) }
                        )

                        // Audio Selection Pill
                        CinematicPillButton(
                            label = selectedAudioOption?.label ?: "Audio",
                            icon = Icons.Filled.Audiotrack,
                            onClick = { viewModel.openBottomSheet(PlayerBottomSheet.AUDIO) }
                        )

                        // Subtitle Selection Pill
                        CinematicPillButton(
                            label = if (selectedSubtitleOption.isOff) "Sub: Off" else "Sub: ${selectedSubtitleOption.label}",
                            icon = Icons.Filled.Subtitles,
                            onClick = { viewModel.openBottomSheet(PlayerBottomSheet.SUBTITLES) }
                        )

                        // Video Quality Pill
                        CinematicPillButton(
                            label = selectedQualityOption.label,
                            icon = Icons.Filled.HighQuality,
                            onClick = { viewModel.openBottomSheet(PlayerBottomSheet.QUALITY) }
                        )

                        // Settings / Speed Button
                        CinematicPillButton(
                            label = "${playbackSpeed}x",
                            icon = Icons.Filled.Speed,
                            onClick = { viewModel.openBottomSheet(PlayerBottomSheet.SPEED) }
                        )
                    }
                }

                // CENTER CONTROLS (Rewind 10s, Animated Play/Pause, Forward 10s)
                if (displayError == null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.55f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                viewModel.playerManager.seekBack(10000L)
                                doubleTapSeekFeedback = Pair(false, System.currentTimeMillis())
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Large Animated Play/Pause Button
                        val playButtonScale by animateFloatAsState(
                            targetValue = if (isPlaying) 1.0f else 1.05f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "playScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .scale(playButtonScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFA855F7),
                                            Color(0xFF7C3AED)
                                        )
                                    )
                                )
                                .border(1.5.dp, Color(0xFFD8B4FE).copy(alpha = 0.8f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    viewModel.playerManager.togglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    scaleIn(tween(180)) + fadeIn(tween(180)) togetherWith
                                            scaleOut(tween(180)) + fadeOut(tween(180))
                                },
                                label = "playPauseIcon"
                            ) { targetPlaying ->
                                Icon(
                                    imageVector = if (targetPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (targetPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                viewModel.playerManager.seekForward(10000L)
                                doubleTapSeekFeedback = Pair(true, System.currentTimeMillis())
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // BOTTOM CONTROLS (Seek Bar with Buffered Track, Timestamps, Speed & Resize)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    val totalDuration = duration.coerceAtLeast(1L)
                    val displayPosition = if (isDraggingSlider) dragPositionMs else currentPosition

                    // High-Precision Seek Bar with Buffered Progress
                    CinematicSeekBar(
                        currentPositionMs = displayPosition,
                        bufferedPositionMs = bufferedPosition,
                        totalDurationMs = totalDuration,
                        onSeekStarted = {
                            isDraggingSlider = true
                            dragPositionMs = currentPosition
                        },
                        onSeekProgress = { newMs ->
                            dragPositionMs = newMs
                        },
                        onSeekFinished = { finalMs ->
                            viewModel.playerManager.seekTo(finalMs)
                            isDraggingSlider = false
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Timestamps & Aspect Ratio / Fullscreen controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current / Total Duration
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = formatMsToTime(displayPosition),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "/",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = formatMsToTime(totalDuration),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF9CA3AF)
                            )
                        }

                        // Right Utility Controls: Aspect Ratio Resize Mode & Fullscreen
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Resize Mode Button (Fit -> Zoom -> Fill)
                            val resizeLabel = when (uiState.resizeMode) {
                                1 -> "Zoom"
                                2 -> "Stretch"
                                else -> "Fit"
                            }
                            val resizeIcon = when (uiState.resizeMode) {
                                1 -> Icons.Filled.CropFree
                                2 -> Icons.Filled.AspectRatio
                                else -> Icons.Filled.FitScreen
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.cycleResizeMode() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = resizeIcon,
                                    contentDescription = resizeLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = resizeLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD1D5DB)
                                )
                            }

                            // Fullscreen Toggle (Maintains landscape but resets immersive bars if needed)
                            IconButton(
                                onClick = { viewModel.toggleFullscreen() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error & Fallback Stream Dialog Overlay
        if (displayError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18181C)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2A42)),
                    modifier = Modifier.fillMaxWidth(0.65f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE50914).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Playback Notice",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = displayError,
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBackClick,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2A42)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back", color = Color.White, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.retryPlayback() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2A42)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry", color = Color.White, fontSize = 12.sp)
                            }

                            if (availableSources.size > 1) {
                                Button(
                                    onClick = { viewModel.openBottomSheet(PlayerBottomSheet.SOURCES) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Switch Source", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleek Landscape Track Selection & Speed Modal Dialog
        val currentSheet = uiState.activeBottomSheet
        if (currentSheet != null && currentSheet != PlayerBottomSheet.NONE) {
            CinematicTrackSelectionDialog(
                sheetType = currentSheet,
                availableSources = availableSources,
                selectedSource = uiState.source,
                availableQualities = availableQualities,
                selectedQuality = selectedQualityOption,
                availableAudios = availableAudios,
                selectedAudio = selectedAudioOption,
                availableSubtitles = availableSubtitles,
                selectedSubtitle = selectedSubtitleOption,
                currentSpeed = playbackSpeed,
                onSourceSelect = { viewModel.selectSource(it) },
                onQualitySelect = { viewModel.selectQuality(it) },
                onAudioSelect = { viewModel.selectAudio(it) },
                onSubtitleSelect = { viewModel.selectSubtitle(it) },
                onSpeedSelect = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { viewModel.closeBottomSheet() }
            )
        }
    }
}

/**
 * High-Precision Custom Seek Bar:
 * - Inactive Track: Dark semi-transparent background
 * - Buffered Track: Soft white semi-transparent progress
 * - Active Track: Glowing purple progress bar
 * - Interactive Scrubbing Thumb with instant touch detection
 */
@Composable
private fun CinematicSeekBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    totalDurationMs: Long,
    onSeekStarted: () -> Unit,
    onSeekProgress: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSafe = totalDurationMs.coerceAtLeast(1L)
    val playedFraction = (currentPositionMs.toFloat() / durationSafe.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / durationSafe.toFloat()).coerceIn(0f, 1f)

    var isTouching by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(totalDurationMs) {
                detectTapGestures(
                    onPress = { offset ->
                        isTouching = true
                        onSeekStarted()
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val newMs = (newFraction * durationSafe).toLong()
                        onSeekProgress(newMs)
                        tryAwaitRelease()
                        isTouching = false
                        onSeekFinished(newMs)
                    }
                )
            }
            .pointerInput(totalDurationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        onSeekStarted()
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress((newFraction * durationSafe).toLong())
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress((newFraction * durationSafe).toLong())
                    },
                    onDragEnd = {
                        isTouching = false
                        val currentMs = (playedFraction * durationSafe).toLong()
                        onSeekFinished(currentMs)
                    },
                    onDragCancel = {
                        isTouching = false
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val playedWidthDp = with(LocalDensity.current) { (widthPx * playedFraction).toDp() }
        val bufferedWidthDp = with(LocalDensity.current) { (widthPx * bufferedFraction).toDp() }

        // Track Height (Expands slightly when touched)
        val trackHeight = if (isTouching) 6.dp else 4.dp
        val thumbRadius = if (isTouching) 16.dp else 12.dp

        // 1. Inactive Background Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.22f))
        )

        // 2. Buffered Progress Track
        Box(
            modifier = Modifier
                .width(bufferedWidthDp)
                .height(trackHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.45f))
        )

        // 3. Active Played Progress Track
        Box(
            modifier = Modifier
                .width(playedWidthDp)
                .height(trackHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF7C3AED),
                            Color(0xFFA855F7)
                        )
                    )
                )
        )

        // 4. Scrubbing Thumb
        Box(
            modifier = Modifier
                .offset(x = (playedWidthDp - (thumbRadius / 2)).coerceAtLeast(0.dp))
                .size(thumbRadius)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0xFFA855F7), CircleShape)
        )
    }
}

/**
 * Top Bar Pill Action Button (Audio, Subtitles, Quality, Speed)
 */
@Composable
private fun CinematicPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1C29).copy(alpha = 0.85f))
            .border(1.dp, Color(0xFF3B3556), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFD8B4FE),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * Landscape Track Selection & Speed Dialog
 */
@Composable
private fun CinematicTrackSelectionDialog(
    sheetType: PlayerBottomSheet,
    availableSources: List<PlaybackSource>,
    selectedSource: PlaybackSource?,
    availableQualities: List<VideoQualityOption>,
    selectedQuality: VideoQualityOption,
    availableAudios: List<AudioTrackOption>,
    selectedAudio: AudioTrackOption?,
    availableSubtitles: List<SubtitleTrackOption>,
    selectedSubtitle: SubtitleTrackOption,
    currentSpeed: Float,
    onSourceSelect: (PlaybackSource) -> Unit,
    onQualitySelect: (VideoQualityOption) -> Unit,
    onAudioSelect: (AudioTrackOption) -> Unit,
    onSubtitleSelect: (SubtitleTrackOption) -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22000000)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .width(380.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent click-through */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    val title = when (sheetType) {
                        PlayerBottomSheet.SOURCES -> "Select Playback Source"
                        PlayerBottomSheet.QUALITY -> "Video Quality"
                        PlayerBottomSheet.AUDIO -> "Audio Language"
                        PlayerBottomSheet.SUBTITLES -> "Subtitles"
                        PlayerBottomSheet.SPEED -> "Playback Speed"
                        else -> ""
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = NuvioTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (sheetType) {
                        PlayerBottomSheet.SOURCES -> {
                            if (availableSources.isEmpty()) {
                                CinematicOptionRow(
                                    label = selectedSource?.title ?: "Primary Authorized Stream",
                                    isSelected = true,
                                    onClick = { onDismiss() }
                                )
                            } else {
                                availableSources.forEach { source ->
                                    val isCurrent = source.streamUrl == selectedSource?.streamUrl
                                    CinematicOptionRow(
                                        label = source.title,
                                        isSelected = isCurrent,
                                        onClick = { onSourceSelect(source) }
                                    )
                                }
                            }
                        }
                        PlayerBottomSheet.QUALITY -> {
                            availableQualities.forEach { quality ->
                                CinematicOptionRow(
                                    label = quality.label,
                                    isSelected = quality.label == selectedQuality.label,
                                    onClick = { onQualitySelect(quality) }
                                )
                            }
                        }
                        PlayerBottomSheet.AUDIO -> {
                            if (availableAudios.isEmpty()) {
                                CinematicOptionRow(
                                    label = "Default Audio (Stereo)",
                                    isSelected = true,
                                    onClick = { onDismiss() }
                                )
                            } else {
                                availableAudios.forEach { audio ->
                                    val label = if (availableAudios.size == 1) {
                                        "${audio.label} (Only track available)"
                                    } else {
                                        audio.label
                                    }
                                    CinematicOptionRow(
                                        label = label,
                                        isSelected = audio.id == selectedAudio?.id,
                                        onClick = { onAudioSelect(audio) }
                                    )
                                }
                            }
                        }
                        PlayerBottomSheet.SUBTITLES -> {
                            availableSubtitles.forEach { sub ->
                                val label = if (sub.isOff && availableSubtitles.size <= 1) {
                                    "Off (No subtitles available)"
                                } else {
                                    sub.label
                                }
                                CinematicOptionRow(
                                    label = label,
                                    isSelected = sub.id == selectedSubtitle.id,
                                    onClick = { onSubtitleSelect(sub) }
                                )
                            }
                        }
                        PlayerBottomSheet.SPEED -> {
                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                val label = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"
                                CinematicOptionRow(
                                    label = label,
                                    isSelected = (currentSpeed == speed),
                                    onClick = { onSpeedSelect(speed) }
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun CinematicOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) DarkActionPill else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else NuvioTextPrimary
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatMsToTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
