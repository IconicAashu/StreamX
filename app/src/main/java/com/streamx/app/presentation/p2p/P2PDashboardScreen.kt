package com.streamx.app.presentation.p2p

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.p2p.model.TorrentState
import com.streamx.app.p2p.model.TorrentStatus
import com.streamx.app.p2p.safety.P2PSafetyManager
import com.streamx.app.presentation.theme.*

@Composable
fun P2PDashboardScreen(
    viewModel: P2PViewModel,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeTorrents by viewModel.activeTorrents.collectAsState()

    val totalDownloadRate = activeTorrents.sumOf { it.downloadRate }
    val totalUploadRate = activeTorrents.sumOf { it.uploadRate }
    val totalPeers = activeTorrents.sumOf { it.peersCount }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NuvioBackground,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "P2P Streaming Engine",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF29B6F6).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LIBTORRENT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF29B6F6)
                                )
                            }
                        }
                        Text(
                            text = "Native C++ / JNI • Loopback HTTP (127.0.0.1)",
                            fontSize = 11.sp,
                            color = NuvioTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.openAddDialog() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NuvioAccentViolet)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Torrent",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Swarm Performance Metrics Card
                item(key = "p2p_metrics") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetricItem(
                                label = "DOWNLOAD",
                                value = formatSpeed(totalDownloadRate),
                                color = Color(0xFF00E676),
                                icon = Icons.Filled.ArrowDownward
                            )
                            MetricDivider()
                            MetricItem(
                                label = "UPLOAD",
                                value = formatSpeed(totalUploadRate),
                                color = Color(0xFF29B6F6),
                                icon = Icons.Filled.ArrowUpward
                            )
                            MetricDivider()
                            MetricItem(
                                label = "PEERS",
                                value = "$totalPeers",
                                color = Color(0xFFFFC107),
                                icon = Icons.Filled.People
                            )
                        }
                    }
                }

                // Quick Self-Test Action Banner
                item(key = "developer_test_banner") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioAccentViolet.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioAccentViolet.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Developer Self-Test Stream",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Generates synthetic BMFF MP4 & tests loopback Range playback instantly.",
                                    fontSize = 11.sp,
                                    color = NuvioTextSecondary,
                                    lineHeight = 15.sp
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.addSampleDeveloperTorrent { mediaId ->
                                        onPlayClick(mediaId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("▶ Run Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Active Transfers Header
                item(key = "active_transfers_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE TRANSFERS (${activeTorrents.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Active Transfers List
                if (activeTorrents.isEmpty()) {
                    item(key = "empty_torrents") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    tint = NuvioTextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No active P2P streams",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NuvioTextPrimary
                                )
                                Text(
                                    text = "Add a magnet URI or run the developer test above.",
                                    fontSize = 12.sp,
                                    color = NuvioTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(activeTorrents, key = { it.infoHash }) { torrent ->
                        TorrentTransferCard(
                            torrent = torrent,
                            onPlayClick = {
                                val fileIndex = if (torrent.selectedFileIndex >= 0) torrent.selectedFileIndex else 0
                                onPlayClick("p2p_${torrent.infoHash}_file_$fileIndex")
                            },
                            onPauseClick = { viewModel.pauseTorrent(torrent.infoHash) },
                            onResumeClick = { viewModel.resumeTorrent(torrent.infoHash) },
                            onRemoveClick = { viewModel.removeTorrent(torrent.infoHash, deleteFiles = false) }
                        )
                    }
                }
            }
        }
    }

    // Add Torrent Dialog
    if (uiState.showAddDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeAddDialog() },
            title = {
                Text(
                    text = "Add P2P Torrent",
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a magnet link to resolve metadata and stream directly:",
                        fontSize = 13.sp,
                        color = NuvioTextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NuvioPillBackground)
                            .border(1.dp, NuvioBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        if (uiState.magnetInput.isEmpty()) {
                            Text("magnet:?xt=urn:btih:...", color = NuvioTextSecondary, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = uiState.magnetInput,
                            onValueChange = { viewModel.setMagnetInput(it) },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(NuvioAccentViolet),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitMagnet { mediaId ->
                            onPlayClick(mediaId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Magnet", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAddDialog() }) {
                    Text("Cancel", color = NuvioTextSecondary)
                }
            },
            containerColor = NuvioCardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Safety Warning & User Confirmation Dialog
    if (uiState.showSafetyWarningDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSafetyDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unverified P2P Source",
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = P2PSafetyManager.LEGAL_DISCLAIMER,
                        fontSize = 12.sp,
                        color = NuvioTextSecondary,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = "Do you want to proceed and connect to this peer-to-peer swarm?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmAddMagnet { mediaId ->
                            onPlayClick(mediaId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm & Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSafetyDialog() }) {
                    Text("Cancel", color = NuvioTextSecondary)
                }
            },
            containerColor = NuvioCardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Multi-File Selection Dialog
    if (uiState.showFileSelectDialog && uiState.selectedTorrentMetadata != null) {
        val meta = uiState.selectedTorrentMetadata!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissFileSelectDialog() },
            title = {
                Text(
                    text = "Select Video to Stream",
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${meta.name} contains multiple files. Select the file for streaming:",
                        fontSize = 12.sp,
                        color = NuvioTextSecondary
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(meta.files) { file ->
                            val isSelected = file.index == uiState.selectedFileIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NuvioAccentViolet.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { viewModel.selectFileIndex(file.index) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectFileIndex(file.index) },
                                    colors = RadioButtonDefaults.colors(selectedColor = NuvioAccentViolet)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = file.path,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = NuvioTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (file.isVideo) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(NuvioAccentViolet.copy(alpha = 0.3f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("VIDEO", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        text = file.formattedSize,
                                        fontSize = 11.sp,
                                        color = NuvioTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmFileSelection { mediaId ->
                            onPlayClick(mediaId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Start Stream", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFileSelectDialog() }) {
                    Text("Cancel", color = NuvioTextSecondary)
                }
            },
            containerColor = NuvioCardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun TorrentTransferCard(
    torrent: TorrentStatus,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & State Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = torrent.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val stateBadgeColor = when (torrent.state) {
                    TorrentState.DOWNLOADING -> Color(0xFF00E676)
                    TorrentState.PAUSED -> Color(0xFFFFC107)
                    TorrentState.SEEDING -> Color(0xFF29B6F6)
                    else -> NuvioTextSecondary
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(stateBadgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = torrent.state.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateBadgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { torrent.progress.coerceIn(0.0f, 1.0f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NuvioAccentViolet,
                trackColor = NuvioPillBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Speed and Peer Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "↓ ${torrent.formattedDownloadRate}",
                        fontSize = 11.sp,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "↑ ${torrent.formattedUploadRate}",
                        fontSize = 11.sp,
                        color = Color(0xFF29B6F6),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Peers: ${torrent.peersCount}",
                        fontSize = 11.sp,
                        color = NuvioTextSecondary
                    )
                }

                Text(
                    text = "${torrent.progressPercent}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Control Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (torrent.state == TorrentState.PAUSED) onResumeClick() else onPauseClick()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (torrent.state == TorrentState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = "Pause/Resume",
                        tint = NuvioTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove Torrent",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NuvioTextSecondary)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(NuvioBorder.copy(alpha = 0.5f))
    )
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024.0) {
        String.format("%.1f MB/s", kb / 1024.0)
    } else {
        String.format("%.0f KB/s", kb)
    }
}
