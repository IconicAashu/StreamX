package com.streamx.app.presentation.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamx.app.BuildConfig
import com.streamx.app.domain.model.DeveloperInfo
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.UserProfile
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.presentation.components.ContentRow
import com.streamx.app.presentation.components.MediaPosterCard
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val audioLang by viewModel.preferredAudioLanguage.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()

    // Photo picker for profile avatar
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onAvatarSelected(uri.toString())
        }
    }

    // Feedback toast observation
    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedbackMessage()
        }
    }

    StreamXBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Profile",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                    if (uiState.userProfile.isCreated) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NuvioAccentViolet.copy(alpha = 0.12f))
                                .border(1.dp, NuvioAccentViolet.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Local",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NuvioAccentViolet
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = NuvioTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = uiState.userProfile.isCreated,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = 400f)) + expandVertically())
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = 400f)) + shrinkVertically())
                },
                label = "profileCreatedStateTransition"
            ) { isCreated ->
                if (!isCreated) {
                    // ==========================================
                    // 1. FIRST-TIME LOCAL ACCOUNT SETUP SCREEN
                    // ==========================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item(key = "setup_card") {
                            ProfileSetupCard(
                                inputName = uiState.inputName,
                                inputAvatarUri = uiState.inputAvatarUri,
                                nameError = uiState.nameError,
                                onNameChange = { viewModel.onNameInputChange(it) },
                                onPickAvatar = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onRemoveAvatar = { viewModel.onAvatarSelected(null) },
                                onCreateProfile = { viewModel.createProfile() }
                            )
                        }

                        // Developer Attribution Section at bottom
                        item(key = "developer_credits_setup") {
                            DeveloperCreditsSection()
                        }
                    }
                } else {
                    // ==========================================
                    // 2. ACTIVE USER PROFILE & DASHBOARD
                    // ==========================================
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Segmented Tab Selector: [Overview] [My Watchlist (N)]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(NuvioSecondarySurface)
                                .border(1.dp, NuvioBorder, RoundedCornerShape(20.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ProfileSectionTab.values().forEach { tab ->
                                val isSelected = tab == uiState.selectedTab
                                val label = if (tab == ProfileSectionTab.WATCHLIST) "My Watchlist (${watchlist.size})" else tab.label

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) NuvioAccentViolet else Color.Transparent)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { viewModel.selectTab(tab) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else NuvioTextSecondary
                                    )
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = uiState.selectedTab,
                            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                            label = "profileTabTransition"
                        ) { selectedTab ->
                            when (selectedTab) {
                                ProfileSectionTab.OVERVIEW -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // User Profile Header Card
                                        item(key = "user_header_card") {
                                            UserProfileHeaderCard(
                                                profile = uiState.userProfile,
                                                onEditClick = { viewModel.openEditProfileDialog() }
                                            )
                                        }

                                        // Continue Watching Carousel
                                        if (uiState.continueWatchingList.isNotEmpty()) {
                                            item(key = "profile_resume_watching") {
                                                ContentRow(
                                                    title = "Continue Watching",
                                                    items = uiState.continueWatchingList,
                                                    onItemClick = onMediaClick,
                                                    onSeeAllClick = {}
                                                )
                                            }
                                        }

                                        // Playback & Audio Preferences Section
                                        item(key = "profile_playback_prefs") {
                                            PlaybackPreferencesCard(
                                                audioLang = audioLang,
                                                subtitlesEnabled = subtitlesEnabled,
                                                defaultQuality = defaultQuality,
                                                onLanguageClick = { viewModel.setLanguageDialogVisible(true) },
                                                onSubtitlesToggle = { viewModel.toggleSubtitles(it) },
                                                onQualityClick = { viewModel.setQualityDialogVisible(true) }
                                            )
                                        }

                                        // Quick Navigation Shortcuts
                                        item(key = "profile_shortcuts") {
                                            ProfileShortcutsCard(
                                                watchlistCount = watchlist.size,
                                                onWatchlistClick = { viewModel.selectTab(ProfileSectionTab.WATCHLIST) },
                                                onSettingsClick = onNavigateToSettings
                                            )
                                        }

                                        // About STREAMX Card
                                        item(key = "about_streamx_card") {
                                            AboutStreamXCard()
                                        }

                                        // Dedicated Developer Credits at the absolute bottom
                                        item(key = "developer_credits_active") {
                                            DeveloperCreditsSection()
                                        }
                                    }
                                }

                                ProfileSectionTab.WATCHLIST -> {
                                    if (watchlist.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.BookmarkBorder,
                                                    contentDescription = null,
                                                    tint = NuvioTextSecondary,
                                                    modifier = Modifier.size(54.dp)
                                                )
                                                Text(
                                                    text = "Your Watchlist is Empty",
                                                    fontSize = 19.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NuvioTextPrimary
                                                )
                                                Text(
                                                    text = "Tap the bookmark icon on any movie or series to save it here for fast access.",
                                                    fontSize = 13.sp,
                                                    color = NuvioTextSecondary,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(18.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(
                                                items = watchlist,
                                                key = { "profile_watchlist_${it.id}" },
                                                contentType = { "WatchlistPosterCard" }
                                            ) { item ->
                                                Box {
                                                    MediaPosterCard(
                                                        item = item,
                                                        onClick = { onMediaClick(item) },
                                                        width = 165,
                                                        height = 240
                                                    )

                                                    IconButton(
                                                        onClick = { viewModel.removeFromWatchlist(item) },
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(6.dp)
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xD9000000))
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.BookmarkRemove,
                                                            contentDescription = "Remove",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
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
                }
            }
        }

        // ==========================================
        // 3. EDIT PROFILE DIALOG
        // ==========================================
        if (uiState.isEditDialogOpen) {
            EditProfileDialog(
                inputName = uiState.inputName,
                inputAvatarUri = uiState.inputAvatarUri,
                initials = uiState.userProfile.initials,
                nameError = uiState.nameError,
                onNameChange = { viewModel.onNameInputChange(it) },
                onPickAvatar = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveAvatar = { viewModel.onAvatarSelected(null) },
                onDismiss = { viewModel.dismissEditProfileDialog() },
                onSave = { viewModel.saveProfileChanges() }
            )
        }

        // Preferred Audio Language Dialog
        if (uiState.isLanguageDialogVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.setLanguageDialogVisible(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = {
                    Text(
                        text = "Preferred Audio Language",
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Original", "Hindi", "English", "Spanish", "French", "Japanese").forEach { lang ->
                            val isSelected = (lang == audioLang)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NuvioSecondarySurface else Color.Transparent)
                                    .clickable { viewModel.selectPreferredLanguage(lang) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang,
                                    color = if (isSelected) NuvioAccentViolet else NuvioTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                    TextButton(onClick = { viewModel.setLanguageDialogVisible(false) }) {
                        Text("Close", color = NuvioAccentViolet)
                    }
                }
            )
        }

        // Default Video Quality Dialog
        if (uiState.isQualityDialogVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.setQualityDialogVisible(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = {
                    Text(
                        text = "Default Video Quality",
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            VideoQuality.AUTO,
                            VideoQuality.QUALITY_480P,
                            VideoQuality.QUALITY_720P,
                            VideoQuality.QUALITY_1080P,
                            VideoQuality.QUALITY_4K
                        ).forEach { quality ->
                            val isSelected = (quality == defaultQuality)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NuvioSecondarySurface else Color.Transparent)
                                    .clickable { viewModel.selectDefaultQuality(quality) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.label,
                                    color = if (isSelected) NuvioAccentViolet else NuvioTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                    TextButton(onClick = { viewModel.setQualityDialogVisible(false) }) {
                        Text("Close", color = NuvioAccentViolet)
                    }
                }
            )
        }
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

/**
 * Welcoming card shown when a user opens Profile for the first time.
 */
@Composable
private fun ProfileSetupCard(
    inputName: String,
    inputAvatarUri: String?,
    nameError: String?,
    onNameChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onCreateProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome Header
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(NuvioAccentViolet.copy(alpha = 0.12f))
                .border(1.dp, NuvioAccentViolet.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = NuvioAccentViolet,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Welcome to STREAMX",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NuvioTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Create your private local profile. No email, cloud login or password required.",
            fontSize = 13.sp,
            color = NuvioTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Avatar Preview & Picker
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B4EE6),
                            Color(0xFF5E5CE6),
                            Color(0xFF3B82F6)
                        )
                    )
                )
                .clickable { onPickAvatar() },
            contentAlignment = Alignment.Center
        ) {
            if (inputAvatarUri != null) {
                AsyncImage(
                    model = inputAvatarUri,
                    contentDescription = "Avatar Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val monogram = if (inputName.isNotBlank()) {
                    inputName.trim().take(1).uppercase()
                } else {
                    "?"
                }
                Text(
                    text = monogram,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Camera Badge Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(NuvioCardSurface)
                    .border(1.5.dp, NuvioBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Choose Photo",
                    tint = NuvioAccentViolet,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPickAvatar) {
                Text(
                    text = if (inputAvatarUri != null) "Change Photo" else "Add Photo (Optional)",
                    fontSize = 12.sp,
                    color = NuvioAccentViolet,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (inputAvatarUri != null) {
                TextButton(onClick = onRemoveAvatar) {
                    Text(
                        text = "Remove",
                        fontSize = 12.sp,
                        color = Color(0xFFFF453A),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Display Name Input
        OutlinedTextField(
            value = inputName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display Name") },
            placeholder = { Text("e.g. Rahul") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = NuvioAccentViolet
                )
            },
            singleLine = true,
            isError = (nameError != null),
            supportingText = {
                if (nameError != null) {
                    Text(text = nameError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                } else {
                    Text(text = "This name is displayed on your personal dashboard", fontSize = 11.sp, color = NuvioTextSecondary)
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NuvioAccentViolet,
                unfocusedBorderColor = NuvioBorder,
                focusedLabelColor = NuvioAccentViolet,
                unfocusedLabelColor = NuvioTextSecondary,
                focusedTextColor = NuvioTextPrimary,
                unfocusedTextColor = NuvioTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Create Profile Action Button
        Button(
            onClick = onCreateProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NuvioAccentViolet,
                contentColor = Color.White
            )
        ) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Create Profile", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Top active user profile card with avatar, name, and edit button.
 */
@Composable
private fun UserProfileHeaderCard(
    profile: UserProfile,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with edit action
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B4EE6),
                            Color(0xFF5E5CE6),
                            Color(0xFF3B82F6)
                        )
                    )
                )
                .border(2.dp, NuvioAccentViolet.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!profile.avatarUri.isNullOrBlank()) {
                AsyncImage(
                    model = profile.avatarUri,
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = profile.initials,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Display Name (e.g. "Rahul")
        Text(
            text = profile.displayName,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = NuvioTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Local Profile Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioSecondarySurface)
                .border(1.dp, NuvioBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF34C759),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Local Profile • Private On-Device",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = NuvioTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Profile Button
        OutlinedButton(
            onClick = onEditClick,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NuvioAccentViolet.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = NuvioAccentViolet
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Edit Profile",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Playback preferences card with audio language, subtitles, quality.
 */
@Composable
private fun PlaybackPreferencesCard(
    audioLang: String,
    subtitlesEnabled: Boolean,
    defaultQuality: VideoQuality,
    onLanguageClick: () -> Unit,
    onSubtitlesToggle: (Boolean) -> Unit,
    onQualityClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "Playback & Audio Preferences",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = NuvioTextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Preferred Audio Language
        PreferenceClickRow(
            title = "Preferred Audio Language",
            subtitle = audioLang,
            icon = Icons.Filled.Language,
            onClick = onLanguageClick
        )

        HorizontalDivider(
            color = NuvioBorder.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Subtitles Enabled Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = null,
                        tint = NuvioAccentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Subtitles",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextPrimary
                    )
                    Text(
                        text = if (subtitlesEnabled) "Always enabled" else "Disabled by default",
                        fontSize = 12.sp,
                        color = NuvioTextSecondary
                    )
                }
            }

            Switch(
                checked = subtitlesEnabled,
                onCheckedChange = onSubtitlesToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NuvioAccentViolet,
                    uncheckedThumbColor = NuvioTextSecondary,
                    uncheckedTrackColor = NuvioSecondarySurface
                )
            )
        }

        HorizontalDivider(
            color = NuvioBorder.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Default Video Quality
        PreferenceClickRow(
            title = "Default Stream Quality",
            subtitle = defaultQuality.label,
            icon = Icons.Filled.HighQuality,
            onClick = onQualityClick
        )
    }
}

/**
 * Shortcuts to Watchlist and App Settings.
 */
@Composable
private fun ProfileShortcutsCard(
    watchlistCount: Int,
    onWatchlistClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "Quick Navigation",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = NuvioTextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        PreferenceClickRow(
            title = "My Watchlist",
            subtitle = "$watchlistCount saved titles",
            icon = Icons.Filled.Bookmark,
            onClick = onWatchlistClick
        )

        HorizontalDivider(
            color = NuvioBorder.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        PreferenceClickRow(
            title = "App Settings & Sources",
            subtitle = "Themes, add-ons, notifications, cache and Jellyfin",
            icon = Icons.Filled.Settings,
            onClick = onSettingsClick
        )
    }
}

/**
 * About STREAMX architecture & local data assurance.
 */
@Composable
private fun AboutStreamXCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(NuvioAccentViolet.copy(alpha = 0.12f))
                    .border(1.dp, NuvioAccentViolet.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = NuvioAccentViolet,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "About STREAMX",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NuvioTextPrimary
            )
        }

        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Liquid Glass Edition",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NuvioAccentViolet
        )

        Text(
            text = "STREAMX is designed for native streaming discovery with zero cloud tracking. Your profiles, preferences, and watchlist remain completely on your device.",
            fontSize = 12.sp,
            color = NuvioTextSecondary,
            lineHeight = 17.sp
        )
    }
}

/**
 * Dedicated Developer Attribution Section at the absolute bottom of the Profile page.
 * Prominently presents developer Aashu Kumar, GitHub profile, and feedback email.
 */
@Composable
private fun DeveloperCreditsSection() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Minimal Developer Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF5E5CE6),
                                Color(0xFF7D7AFF),
                                Color(0xFF4A90E2)
                            )
                        )
                    )
                    .border(1.5.dp, NuvioAccentViolet.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AK",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column {
                Text(
                    text = DeveloperInfo.ROLE,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = NuvioTextSecondary
                )
                Text(
                    text = DeveloperInfo.NAME,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(12.dp))

        // Clickable GitHub Link Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DeveloperInfo.GITHUB_URL))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = "GitHub",
                        tint = NuvioAccentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "GitHub: ${DeveloperInfo.GITHUB_USERNAME}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioTextPrimary
                        )
                    }
                    Text(
                        text = DeveloperInfo.GITHUB_URL,
                        fontSize = 11.5.sp,
                        color = NuvioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open GitHub",
                tint = NuvioAccentViolet,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Clickable Feedback Email Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${DeveloperInfo.FEEDBACK_EMAIL}")
                            putExtra(Intent.EXTRA_SUBJECT, "STREAMX User Feedback")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = "Email",
                        tint = NuvioAccentViolet,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = "Feedback & Inquiries",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextPrimary
                    )
                    Text(
                        text = DeveloperInfo.FEEDBACK_EMAIL,
                        fontSize = 11.5.sp,
                        color = NuvioTextSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Send Email",
                tint = NuvioAccentViolet,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Dialog to edit local profile name and avatar.
 */
@Composable
private fun EditProfileDialog(
    inputName: String,
    inputAvatarUri: String?,
    initials: String,
    nameError: String?,
    onNameChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NuvioCardSurface,
        titleContentColor = NuvioTextPrimary,
        textContentColor = NuvioTextSecondary,
        title = {
            Text(
                text = "Edit Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = NuvioTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar preview & edit
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6B4EE6),
                                    Color(0xFF5E5CE6),
                                    Color(0xFF3B82F6)
                                )
                            )
                        )
                        .clickable { onPickAvatar() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!inputAvatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = inputAvatarUri,
                            contentDescription = "Avatar Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = if (inputName.isNotBlank()) inputName.trim().take(1).uppercase() else initials,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(NuvioCardSurface)
                            .border(1.dp, NuvioBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = NuvioAccentViolet,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TextButton(onClick = onPickAvatar) {
                        Text(text = "Change Photo", fontSize = 12.sp, color = NuvioAccentViolet)
                    }
                    if (!inputAvatarUri.isNullOrBlank()) {
                        TextButton(onClick = onRemoveAvatar) {
                            Text(text = "Remove", fontSize = 12.sp, color = Color(0xFFFF453A))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputName,
                    onValueChange = onNameChange,
                    label = { Text("Display Name") },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    isError = (nameError != null),
                    supportingText = {
                        if (nameError != null) {
                            Text(text = nameError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NuvioAccentViolet,
                        unfocusedBorderColor = NuvioBorder,
                        focusedLabelColor = NuvioAccentViolet,
                        unfocusedLabelColor = NuvioTextSecondary,
                        focusedTextColor = NuvioTextPrimary,
                        unfocusedTextColor = NuvioTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NuvioAccentViolet,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NuvioTextSecondary, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun PreferenceClickRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NuvioSecondarySurface)
                    .border(1.dp, NuvioBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NuvioAccentViolet,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = NuvioTextSecondary
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = NuvioTextSecondary,
            modifier = Modifier.size(13.dp)
        )
    }
}
