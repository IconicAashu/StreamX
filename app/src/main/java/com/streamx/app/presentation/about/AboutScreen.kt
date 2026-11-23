package com.streamx.app.presentation.about

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.BuildConfig
import com.streamx.app.domain.model.*
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.userNoticeMessage) {
        uiState.userNoticeMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearNoticeMessage()
        }
    }

    StreamXBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NuvioTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = "About StreamX",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. App Identity Card
                item(key = "app_identity") {
                    AppIdentityCard(
                        versionName = uiState.installedVersionName,
                        versionCode = uiState.installedVersionCode
                    )
                }

                // 2. APK Update System Card
                item(key = "update_system") {
                    AppUpdateSystemCard(
                        uiState = uiState,
                        onCheckForUpdates = { viewModel.checkForUpdates() },
                        onUpdateNow = { updateInfo -> viewModel.startDownload(context, updateInfo) },
                        onCancelDownload = { viewModel.cancelDownload() },
                        onToggleAutoCheck = { viewModel.toggleAutoCheck(it, context) }
                    )
                }

                // 3. Dedicated Developer Credits
                item(key = "developer_credits") {
                    DeveloperCreditsCard()
                }

                // 4. Open Source Acknowledgements
                item(key = "open_source") {
                    OpenSourceLibrariesCard()
                }
            }
        }

        // Unknown Sources Permission Dialog
        if (uiState.requiresInstallPermission) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissInstallPermissionDialog() },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = {
                    Text(
                        text = "Installation Permission Needed",
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Android requires permission to install APK updates from STREAMX. Please enable 'Allow from this source' in System Settings, then return to complete installation.",
                        fontSize = 13.sp,
                        color = NuvioTextSecondary,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissInstallPermissionDialog()
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                context.startActivity(fallbackIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NuvioAccentViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissInstallPermissionDialog() }) {
                        Text("Cancel", color = NuvioTextSecondary, fontSize = 13.sp)
                    }
                }
            )
        }
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

@Composable
private fun AppIdentityCard(
    versionName: String,
    versionCode: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon Emblem
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B4EE6),
                            Color(0xFF5E5CE6),
                            Color(0xFF3B82F6)
                        )
                    )
                )
                .border(1.5.dp, NuvioAccentViolet.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "STREAMX",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NuvioTextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Modern iOS Liquid Glass Streaming Discovery",
            fontSize = 12.5.sp,
            color = NuvioTextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Version Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioSecondarySurface)
                .border(1.dp, NuvioBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Version $versionName (Build $versionCode) • Debug",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = NuvioAccentViolet
            )
        }
    }
}

@Composable
private fun AppUpdateSystemCard(
    uiState: AboutUiState,
    onCheckForUpdates: () -> Unit,
    onUpdateNow: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        // Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NuvioAccentViolet.copy(alpha = 0.12f))
                        .border(1.dp, NuvioAccentViolet.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = NuvioAccentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "APK Update System",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                    Text(
                        text = "Official GitHub Releases & package verification",
                        fontSize = 11.5.sp,
                        color = NuvioTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Check Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-check for Updates",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary
                )
                Text(
                    text = "Periodically checks for new releases in background via WorkManager",
                    fontSize = 11.sp,
                    color = NuvioTextSecondary
                )
            }

            Switch(
                checked = uiState.autoCheckEnabled,
                onCheckedChange = onToggleAutoCheck,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NuvioAccentViolet,
                    uncheckedThumbColor = NuvioTextSecondary,
                    uncheckedTrackColor = NuvioSecondarySurface
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(14.dp))

        // Last checked time
        if (uiState.lastCheckTimeMs > 0) {
            val dateStr = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(uiState.lastCheckTimeMs))
            Text(
                text = "Last checked: $dateStr",
                fontSize = 11.5.sp,
                color = NuvioTextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Dynamic State UI (Up-To-Date, Update Available, Downloading, Error)
        when (val result = uiState.updateCheckResult) {
            is UpdateCheckResult.UpToDate -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF34C759).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFF34C759).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "You're using the latest version of STREAMX (Build ${result.currentVersionCode}).",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            is UpdateCheckResult.UpdateAvailable -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioAccentViolet.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Update Available: v${result.updateInfo.versionName}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioAccentViolet
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NuvioAccentViolet.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Build ${result.updateInfo.versionCode}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioAccentViolet
                            )
                        }
                    }

                    if (result.updateInfo.sizeBytes != null && result.updateInfo.sizeBytes > 0) {
                        val mb = result.updateInfo.sizeBytes / (1024.0 * 1024.0)
                        Text(
                            text = "Download size: %.1f MB".format(mb),
                            fontSize = 12.sp,
                            color = NuvioTextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Release Notes:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextPrimary
                    )
                    Text(
                        text = result.updateInfo.releaseNotes,
                        fontSize = 12.sp,
                        color = NuvioTextSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Download Progress / Update Now Button
                    when (val dlState = uiState.downloadProgressState) {
                        is DownloadProgressState.Idle -> {
                            Button(
                                onClick = { onUpdateNow(result.updateInfo) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NuvioAccentViolet,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Now", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }

                        is DownloadProgressState.Queued,
                        is DownloadProgressState.InProgress -> {
                            val progress = if (dlState is DownloadProgressState.InProgress) dlState.progress else 0f
                            val bytesStr = if (dlState is DownloadProgressState.InProgress && dlState.totalBytes > 0) {
                                "%.1f MB / %.1f MB".format(
                                    dlState.bytesDownloaded / (1024.0 * 1024.0),
                                    dlState.totalBytes / (1024.0 * 1024.0)
                                )
                            } else {
                                "Downloading package..."
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = bytesStr, fontSize = 11.5.sp, color = NuvioTextSecondary)
                                    if (progress >= 0f) {
                                        Text(text = "${(progress * 100).toInt()}%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NuvioAccentViolet)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (progress >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = NuvioAccentViolet,
                                        trackColor = NuvioCardSurface
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = NuvioAccentViolet,
                                        trackColor = NuvioCardSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = onCancelDownload,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Cancel Download", color = Color(0xFFFF453A), fontSize = 12.sp)
                                }
                            }
                        }

                        is DownloadProgressState.VerifyingChecksum -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = NuvioAccentViolet
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = dlState.message,
                                    fontSize = 12.sp,
                                    color = NuvioTextSecondary
                                )
                            }
                        }

                        is DownloadProgressState.ReadyToInstall -> {
                            Button(
                                onClick = {
                                    val file = File(dlState.apkPath)
                                    onUpdateNow(dlState.updateInfo)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34C759),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Downloaded APK", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }

                        is DownloadProgressState.Failed -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Error: ${dlState.reason}",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF453A)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onUpdateNow(result.updateInfo) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet)
                                ) {
                                    Text("Retry Download", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            is UpdateCheckResult.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFF9500).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Update Check Notice",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                    }
                    Text(
                        text = result.message,
                        fontSize = 12.sp,
                        color = NuvioTextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            null -> {}
        }

        // Check for Updates Action Button
        Button(
            onClick = onCheckForUpdates,
            enabled = !uiState.isCheckingForUpdates,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NuvioAccentViolet,
                contentColor = Color.White
            )
        ) {
            if (uiState.isCheckingForUpdates) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Checking GitHub Releases...", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Check for Updates", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DeveloperCreditsCard() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        // Developer Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
        Spacer(modifier = Modifier.height(10.dp))

        // Clickable GitHub Link
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
                    Text(
                        text = "GitHub: ${DeveloperInfo.GITHUB_USERNAME}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextPrimary
                    )
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

        Spacer(modifier = Modifier.height(4.dp))

        // Clickable Feedback Email
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${DeveloperInfo.FEEDBACK_EMAIL}")
                            putExtra(Intent.EXTRA_SUBJECT, "STREAMX User Feedback & Inquiries")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
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

@Composable
private fun OpenSourceLibrariesCard() {
    var expanded by remember { mutableStateOf(false) }

    val libraries = listOf(
        "AndroidX Media3 (ExoPlayer)" to "High-performance video and audio streaming engine with adaptive HLS/DASH.",
        "Jetpack Compose & Material 3" to "Modern declarative UI toolkit and Material Design system.",
        "AndroidX WorkManager" to "Guaranteed background execution for periodic update checks.",
        "Jetpack DataStore" to "Asynchronous transactional key-value persistence for app preferences.",
        "Square Retrofit & OkHttp" to "Type-safe HTTP client and streaming network engine.",
        "Coil (Coroutine Image Loader)" to "Fast, lightweight image loading for Android powered by Kotlin Coroutines.",
        "Kotlin Coroutines & Flow" to "Asynchronous concurrency and reactive state streams."
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NuvioCardSurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = NuvioAccentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "Open Source Acknowledgements",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = NuvioTextSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                HorizontalDivider(color = NuvioBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                libraries.forEach { (name, desc) ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NuvioTextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NuvioSecondarySurface)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Apache 2.0",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuvioAccentViolet
                                )
                            }
                        }
                        Text(
                            text = desc,
                            fontSize = 11.5.sp,
                            color = NuvioTextSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
