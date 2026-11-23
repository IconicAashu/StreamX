package com.streamx.app.presentation.jellyfin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamx.app.network.jellyfin.model.JellyfinItemDto
import com.streamx.app.presentation.theme.*

@Composable
fun JellyfinLibraryScreen(
    viewModel: JellyfinViewModel,
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()

    var showDisconnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadLibraryData()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioBackground)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeServer?.serverName?.ifBlank { "Jellyfin" } ?: "Jellyfin Server",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                        }
                        Text(
                            text = activeServer?.username?.let { "Logged in as $it" } ?: activeServer?.baseUrl ?: "Connected",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.loadLibraryData() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NuvioCardSurface)
                            .border(1.dp, NuvioBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = NuvioTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { showDisconnectDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NuvioCardSurface)
                            .border(1.dp, NuvioBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Disconnect Server",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Views / Libraries Tabs
            if (uiState.views.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.views, key = { it.id }) { viewItem ->
                        val isSelected = uiState.selectedView?.id == viewItem.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NuvioAccentViolet else NuvioCardSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) NuvioAccentViolet else NuvioBorder,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { viewModel.selectView(viewItem) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = viewItem.name,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else NuvioTextSecondary
                            )
                        }
                    }
                }
            }

            // Main Content Area
            if (uiState.isLoadingLibrary && uiState.libraryItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = NuvioAccentViolet,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Continue Watching Row (if any)
                    if (uiState.continueWatching.isNotEmpty()) {
                        item(key = "section_continue_watching") {
                            Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleFilled,
                                        contentDescription = null,
                                        tint = NuvioAccentViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CONTINUE WATCHING",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NuvioTextPrimary,
                                        letterSpacing = 0.8.sp
                                    )
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    items(uiState.continueWatching, key = { "cw_${it.id}" }) { item ->
                                        JellyfinContinueWatchingCard(
                                            item = item,
                                            getImageUrl = { id, type -> viewModel.getImageUrl(id, type) },
                                            onClick = { onItemClick(item.id) },
                                            onPlayClick = { onPlayClick("jellyfin_${item.id}") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Latest / Recently Added (if any)
                    if (uiState.latestItems.isNotEmpty()) {
                        item(key = "section_latest") {
                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                Text(
                                    text = "RECENTLY ADDED",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuvioTextPrimary,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    items(uiState.latestItems, key = { "latest_${it.id}" }) { item ->
                                        JellyfinPosterCard(
                                            item = item,
                                            getImageUrl = { id -> viewModel.getImageUrl(id, "Primary") },
                                            onClick = { onItemClick(item.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Current View Items Header
                    item(key = "section_library_title") {
                        val title = uiState.selectedView?.name ?: "Library Items"
                        Text(
                            text = title.uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Library Grid Items
                    if (uiState.libraryItems.isEmpty()) {
                        item(key = "empty_library") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No items found in this library.",
                                    color = NuvioTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // Render in chunks of 3 for vertical grid inside LazyColumn
                        val chunked = uiState.libraryItems.chunked(3)
                        items(chunked) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (item in rowItems) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        JellyfinPosterCard(
                                            item = item,
                                            getImageUrl = { id -> viewModel.getImageUrl(id, "Primary") },
                                            onClick = { onItemClick(item.id) }
                                        )
                                    }
                                }
                                // Fill empty slots in last row to maintain grid alignment
                                for (i in 0 until (3 - rowItems.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = {
                Text(
                    text = "Disconnect Jellyfin Server",
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to disconnect from ${activeServer?.serverName ?: "this server"}? You can reconnect at any time.",
                    color = NuvioTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.logout {
                            onDisconnect()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Disconnect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel", color = NuvioTextSecondary)
                }
            },
            containerColor = NuvioCardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun JellyfinPosterCard(
    item: JellyfinItemDto,
    getImageUrl: (String) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val posterUrl = getImageUrl(item.id)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(12.dp))
                .background(NuvioCardSurface)
                .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Rating Badge (top right)
            item.communityRating?.let { rating ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "★",
                            color = Color(0xFFFFC107),
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Type Badge (top left)
            val typeLabel = when (item.type.lowercase()) {
                "series" -> "TV"
                "episode" -> "EP"
                else -> null
            }
            if (typeLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NuvioAccentViolet.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = typeLabel,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NuvioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        item.productionYear?.let { year ->
            Text(
                text = "$year",
                fontSize = 11.sp,
                color = NuvioTextSecondary
            )
        }
    }
}

@Composable
fun JellyfinContinueWatchingCard(
    item: JellyfinItemDto,
    getImageUrl: (String, String) -> String,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Use backdrop if available, fallback to primary
    val imageUrl = getImageUrl(item.id, "Backdrop")

    val playbackTicks = item.userData?.playbackPositionTicks ?: 0L
    val runtimeTicks = item.runTimeTicks ?: 1L
    val progress = if (runtimeTicks > 0) (playbackTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
        modifier = modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // Quick Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.5.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Progress bar at the bottom of the thumbnail
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = NuvioAccentViolet,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.seriesName ?: item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subText = if (item.seriesName != null && item.indexNumber != null) {
                    "S${item.parentIndexNumber ?: 1}:E${item.indexNumber} • ${item.name}"
                } else {
                    "${(progress * 100).toInt()}% watched"
                }

                Text(
                    text = subText,
                    fontSize = 11.sp,
                    color = NuvioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
