package com.streamx.app.presentation.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.BuildConfig
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.notification.NotificationHelper
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onNavigateToAddons: () -> Unit = {},
    onNavigateToJellyfin: () -> Unit = {},
    onNavigateToP2P: () -> Unit = {},
    onNavigateToContentDiscovery: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToTestPlayer: (streamUrl: String, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val groupedSettings by viewModel.filteredItems.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize(context)
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    // Permission launcher for Android 13+ POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
            Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setNotificationsEnabled(false)
            Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    StreamXBackground(modifier = modifier) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NuvioCardSurface)
                            .border(1.dp, NuvioBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NuvioTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                }

                // Search Filter Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NuvioCardSurface)
                        .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = NuvioTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search settings...",
                                    fontSize = 14.sp,
                                    color = NuvioTextSecondary
                                )
                            }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = NuvioTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(NuvioAccentViolet),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChanged("") },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = NuvioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Settings Group List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    groupedSettings.forEach { (group, itemsList) ->
                        item(key = "group_header_${group.name}") {
                            Text(
                                text = group.header.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }

                        item(key = "group_card_${group.name}") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                                border = BorderStroke(1.dp, NuvioBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    itemsList.forEachIndexed { index, item ->
                                        SettingItemRow(
                                            item = item,
                                            uiState = uiState,
                                            onClick = {
                                                when (item.id) {
                                                    "theme_mode" -> viewModel.toggleThemeDialog(true)
                                                    "app_language" -> viewModel.toggleLanguageDialog(true)
                                                    "notifications" -> {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasNotificationPermission(context)) {
                                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        } else {
                                                            viewModel.setNotificationsEnabled(!uiState.notificationsEnabled)
                                                        }
                                                    }
                                                    "playback_defaults" -> viewModel.togglePlaybackDialog(true)
                                                    "audio_subtitles" -> viewModel.toggleAudioSubtitleDialog(true)
                                                    "addons" -> onNavigateToAddons()
                                                    "jellyfin" -> onNavigateToJellyfin()
                                                    "p2p" -> onNavigateToP2P()
                                                    "cache_storage" -> viewModel.toggleClearCacheDialog(true)
                                                    "search_history" -> {
                                                        viewModel.setRecentSearchesEnabled(!uiState.recentSearchesEnabled)
                                                    }
                                                    "integrations" -> onNavigateToIntegrations()
                                                    "about_app" -> onNavigateToAbout()
                                                    "debug_player_test" -> viewModel.toggleDebugPlayerDialog(true)
                                                }
                                            },
                                            onToggleSwitch = { enabled ->
                                                when (item.id) {
                                                    "notifications" -> {
                                                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasNotificationPermission(context)) {
                                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        } else {
                                                            viewModel.setNotificationsEnabled(enabled)
                                                        }
                                                    }
                                                    "search_history" -> viewModel.setRecentSearchesEnabled(enabled)
                                                }
                                            },
                                            onSendTestNotification = { viewModel.sendTestNotification(context) },
                                            onClearSearchHistory = { viewModel.clearSearchHistory() }
                                        )

                                        if (index < itemsList.size - 1) {
                                            HorizontalDivider(
                                                color = NuvioBorder.copy(alpha = 0.6f),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(start = 58.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Theme Selection Dialog (Light, Dark, System Default)
        if (uiState.showThemeDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleThemeDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Choose Theme", fontWeight = FontWeight.Bold, color = NuvioTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "SYSTEM" to "System Default",
                            "LIGHT" to "Light (Liquid Glass)",
                            "DARK" to "Dark (Midnight Glass)"
                        ).forEach { (mode, label) ->
                            val isSelected = uiState.themeMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) NuvioSecondarySurface else Color.Transparent)
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NuvioAccentViolet else NuvioTextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = NuvioAccentViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.toggleThemeDialog(false) }) {
                        Text("Done", color = NuvioAccentViolet, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 2. Language Selection Dialog
        if (uiState.showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleLanguageDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Select App Language", fontWeight = FontWeight.Bold, color = NuvioTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("English (US)", "Español", "हिन्दी", "Français", "日本語").forEach { lang ->
                            val isSelected = uiState.appLanguage == lang
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) NuvioSecondarySurface else Color.Transparent)
                                    .clickable { viewModel.setAppLanguage(lang) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NuvioAccentViolet else NuvioTextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = NuvioAccentViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.toggleLanguageDialog(false) }) {
                        Text("Close", color = NuvioAccentViolet)
                    }
                }
            )
        }

        // 3. Playback Defaults Dialog (Autoplay & Stream Quality)
        if (uiState.showPlaybackDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.togglePlaybackDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Playback Defaults", fontWeight = FontWeight.Bold, color = NuvioTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Autoplay Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Autoplay Next Title", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NuvioTextPrimary)
                                Text("Automatically start next episode or movie", fontSize = 11.sp, color = NuvioTextSecondary)
                            }
                            Switch(
                                checked = uiState.autoplay,
                                onCheckedChange = { viewModel.setAutoplay(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NuvioAccentViolet,
                                    uncheckedThumbColor = NuvioTextSecondary,
                                    uncheckedTrackColor = NuvioSecondarySurface
                                )
                            )
                        }

                        HorizontalDivider(color = NuvioBorder, thickness = 1.dp)

                        Text("Default Video Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NuvioTextPrimary)
                        VideoQuality.values().forEach { quality ->
                            val isSelected = quality == uiState.defaultQuality
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NuvioSecondarySurface else Color.Transparent)
                                    .clickable { viewModel.setDefaultQuality(quality) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NuvioAccentViolet else NuvioTextPrimary
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = NuvioAccentViolet, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.togglePlaybackDialog(false) }) {
                        Text("Done", color = NuvioAccentViolet, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 4. Audio & Subtitles Dialog
        if (uiState.showAudioSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleAudioSubtitleDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Audio & Subtitle Preferences", fontWeight = FontWeight.Bold, color = NuvioTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Subtitles Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable Subtitles", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NuvioTextPrimary)
                                Text("Show captions by default", fontSize = 11.sp, color = NuvioTextSecondary)
                            }
                            Switch(
                                checked = uiState.subtitlesEnabled,
                                onCheckedChange = { viewModel.setSubtitlesEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NuvioAccentViolet,
                                    uncheckedThumbColor = NuvioTextSecondary,
                                    uncheckedTrackColor = NuvioSecondarySurface
                                )
                            )
                        }

                        HorizontalDivider(color = NuvioBorder, thickness = 1.dp)

                        Text("Preferred Audio Language", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NuvioTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Original", "English", "Hindi", "Spanish", "Japanese").take(3).forEach { lang ->
                                val isSelected = uiState.preferredAudioLanguage == lang
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NuvioAccentViolet else NuvioSecondarySurface)
                                        .clickable { viewModel.setPreferredAudioLanguage(lang) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(lang, fontSize = 12.sp, color = if (isSelected) Color.White else NuvioTextPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text("Default Subtitle Language", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NuvioTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("English", "Spanish", "Hindi", "French").forEach { sub ->
                                val isSelected = uiState.preferredSubtitleLanguage == sub
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NuvioAccentViolet else NuvioSecondarySurface)
                                        .clickable { viewModel.setPreferredSubtitleLanguage(sub) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(sub, fontSize = 12.sp, color = if (isSelected) Color.White else NuvioTextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.toggleAudioSubtitleDialog(false) }) {
                        Text("Apply", color = NuvioAccentViolet, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 5. Clear Cache Dialog
        if (uiState.showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleClearCacheDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Storage & Cache", fontWeight = FontWeight.Bold, color = NuvioTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Current cached data size: ${uiState.cacheSizeFormatted}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioAccentViolet
                        )
                        Text(
                            text = "Clearing cache will remove downloaded posters, thumbnails, and temporary stream buffers. Your watchlist and preferences will be preserved.",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearAppCache(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear Cache", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleClearCacheDialog(false) }) {
                        Text("Cancel", color = NuvioTextSecondary)
                    }
                }
            )
        }

        // 6. Debug Player Test Dialog (DEBUG ONLY)
        if (uiState.showDebugPlayerDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleDebugPlayerDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Debug Video Player Test", fontWeight = FontWeight.Bold, color = NuvioTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Launch ExoPlayer directly with verified public test streams:",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary
                        )

                        Button(
                            onClick = {
                                viewModel.toggleDebugPlayerDialog(false)
                                onNavigateToTestPlayer(
                                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                    "[DEBUG] Big Buck Bunny MP4"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NuvioSecondarySurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Play Big Buck Bunny (Direct MP4)", color = NuvioTextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.toggleDebugPlayerDialog(false)
                                onNavigateToTestPlayer(
                                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                    "[DEBUG] Adaptive HLS Stream"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NuvioSecondarySurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Play Adaptive HLS Stream (.m3u8)", color = NuvioTextPrimary, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.toggleDebugPlayerDialog(false) }) {
                        Text("Close", color = NuvioTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingItemRow(
    item: SettingItem,
    uiState: SettingsUiState,
    onClick: () -> Unit,
    onToggleSwitch: (Boolean) -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    onClearSearchHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val subtitleText = when (item.id) {
        "theme_mode" -> when (uiState.themeMode) {
            "LIGHT" -> "Light (Liquid Glass)"
            "DARK" -> "Dark (Midnight Glass)"
            else -> "System Default"
        }
        "app_language" -> uiState.appLanguage
        "notifications" -> if (uiState.notificationsEnabled) "Active • Alerts enabled" else "Disabled"
        "playback_defaults" -> "Autoplay ${if (uiState.autoplay) "On" else "Off"} • ${uiState.defaultQuality.label}"
        "audio_subtitles" -> "Audio: ${uiState.preferredAudioLanguage} • Subs: ${if (uiState.subtitlesEnabled) uiState.preferredSubtitleLanguage else "Off"}"
        "cache_storage" -> "Size: ${uiState.cacheSizeFormatted}"
        "search_history" -> if (uiState.recentSearchesEnabled) "Recording search queries" else "Disabled"
        else -> item.subtitle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NuvioSecondarySurface)
                .border(1.dp, NuvioBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getSettingIcon(item.iconName),
                contentDescription = item.title,
                tint = NuvioAccentViolet,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary
                )
                if (item.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NuvioAccentViolet.copy(alpha = 0.15f))
                            .border(1.dp, NuvioAccentViolet.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioAccentViolet
                        )
                    }
                }
            }
            Text(
                text = subtitleText,
                fontSize = 12.sp,
                color = NuvioTextSecondary,
                lineHeight = 16.sp
            )
        }

        // Tail control: Switches or Chevron
        when (item.id) {
            "notifications" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSendTestNotification,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Test Notification",
                            tint = NuvioAccentViolet,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = onToggleSwitch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NuvioAccentViolet,
                            uncheckedThumbColor = NuvioTextSecondary,
                            uncheckedTrackColor = NuvioSecondarySurface
                        )
                    )
                }
            }
            "search_history" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClearSearchHistory,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = NuvioTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Switch(
                        checked = uiState.recentSearchesEnabled,
                        onCheckedChange = onToggleSwitch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NuvioAccentViolet,
                            uncheckedThumbColor = NuvioTextSecondary,
                            uncheckedTrackColor = NuvioSecondarySurface
                        )
                    )
                }
            }
            else -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = NuvioTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private fun getSettingIcon(name: String): ImageVector {
    return when (name) {
        "palette" -> Icons.Filled.Palette
        "language" -> Icons.Filled.Language
        "notifications" -> Icons.Filled.Notifications
        "play_circle" -> Icons.Filled.PlayCircle
        "subtitles" -> Icons.Filled.Subtitles
        "extension" -> Icons.Filled.Extension
        "dns" -> Icons.Filled.Dns
        "cloud_download" -> Icons.Filled.CloudDownload
        "storage" -> Icons.Filled.Storage
        "history" -> Icons.Filled.History
        "hub" -> Icons.Filled.Hub
        "info" -> Icons.Filled.Info
        "bug_report" -> Icons.Filled.BugReport
        else -> Icons.Filled.Settings
    }
}
