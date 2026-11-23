@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.streamx.app.presentation.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.presentation.components.PillChip
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

/**
 * Nuvio-style Source Selection Screen for STREAMX.
 *
 * Allows users to review all available streams discovered across enabled Stremio add-ons,
 * inspect resolution/codec/file-size/transport metadata, and explicitly select a source
 * before initiating playback in the Media3 ExoPlayer.
 */
@Composable
fun SourceSelectionScreen(
    viewModel: SourceSelectionViewModel,
    onBackClick: () -> Unit,
    onOpenAddonsSettings: () -> Unit,
    onPlaySource: (PlaybackSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    StreamXBackground {
        Box(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (uiState.selectedSource != null && !uiState.isLoading) 88.dp else 0.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SurfacePurpleSlate)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Select Source",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }

                    IconButton(
                        onClick = onOpenAddonsSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfacePurpleSlate)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = "Manage Add-ons",
                            tint = PrimaryAccentLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Media Metadata Header Card
                val item = uiState.mediaItem
                if (item != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfacePurpleSlate.copy(alpha = 0.5f))
                            .border(1.dp, SurfaceBorderLight.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Poster Thumbnail
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 88.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, SurfaceBorderLight, RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.posterUrl.ifBlank { item.backdropUrl })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Title & Episode / Details Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Show Season & Episode details if applicable
                            val epDisplay = when {
                                !uiState.episodeTitle.isNullOrBlank() -> uiState.episodeTitle!!
                                uiState.seasonNumber != null && uiState.episodeNumber != null ->
                                    "Season ${uiState.seasonNumber} • Episode ${uiState.episodeNumber}"
                                else -> null
                            }

                            if (epDisplay != null) {
                                Text(
                                    text = epDisplay,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryAccentLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PillChip(text = item.format.displayName)
                                if (item.primaryGenre.isNotBlank()) {
                                    PillChip(text = item.primaryGenre)
                                }
                                if (item.releaseYear.isNotBlank()) {
                                    PillChip(text = item.releaseYear)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content View: Loading vs Empty vs Sources List
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryAccentRoyal,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Discovering streams across enabled add-ons...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextLavender
                                )
                                Text(
                                    text = "Querying Stremio protocol stream providers",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }

                    uiState.sources.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF191624)),
                                border = BorderStroke(1.dp, Color(0xFF2E2842)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E2842)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    Text(
                                        text = "No sources available",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        textAlign = TextAlign.Center
                                    )

                                    val explanation = uiState.emptyDetail
                                        ?: uiState.errorMessage
                                        ?: "Enabled add-ons did not return any authorized stream for this title."

                                    Text(
                                        text = explanation,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = TextLavender,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = onOpenAddonsSettings,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFF3E3658))
                                        ) {
                                            Text(
                                                text = "Add-ons Settings",
                                                color = Color(0xFFD8B4FE),
                                                fontSize = 12.sp
                                            )
                                        }

                                        Button(
                                            onClick = { viewModel.retry() },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentRoyal)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Retry",
                                                color = TextWhite,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Sources Available List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AVAILABLE SOURCES (${uiState.sources.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                                Text(
                                    text = "Select & Play",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA855F7)
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.sources, key = { it.streamUrl }) { source ->
                                    val isSelected = uiState.selectedSource?.streamUrl == source.streamUrl
                                    val transport = viewModel.getTransportType(source)
                                    val isPlayable = viewModel.isDirectPlayable(source)

                                    NuvioSourceSelectCard(
                                        source = source,
                                        transport = transport,
                                        isSelected = isSelected,
                                        isPlayable = isPlayable,
                                        onClick = {
                                            viewModel.selectSource(source)
                                        },
                                        onDoubleTapOrPlay = {
                                            viewModel.selectSource(source)
                                            if (isPlayable) {
                                                onPlaySource(source)
                                            } else {
                                                viewModel.showUnsupportedActionNotice(
                                                    "This source is a BitTorrent/P2P link. Media3 plays direct HTTP and HLS streams. Please select a Direct HTTP or HLS stream source."
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fixed Bottom Action Bar (When a source is selected)
            val selected = uiState.selectedSource
            if (selected != null && !uiState.isLoading && uiState.sources.isNotEmpty()) {
                val transport = viewModel.getTransportType(selected)
                val isPlayable = viewModel.isDirectPlayable(selected)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1E1A2E))
                            .border(1.dp, Color(0xFF382F52), RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected Source",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9CA3AF),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${selected.providerName} • ${selected.resolution ?: "HD"} ($transport)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                if (isPlayable) {
                                    onPlaySource(selected)
                                } else {
                                    viewModel.showUnsupportedActionNotice(
                                        "This source is a BitTorrent/P2P link. Media3 plays direct HTTP and HLS streams. Please select a Direct HTTP or HLS stream source."
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayable) PrimaryAccentRoyal else Color(0xFF4B5563),
                                contentColor = TextWhite
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isPlayable) "Play Source" else "Unsupported",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Unsupported Notice Dialog
            val unsupportedNotice = uiState.unsupportedMessage
            if (unsupportedNotice != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissUnsupportedNotice() },
                    containerColor = Color(0xFF1E1A2E),
                    titleContentColor = TextWhite,
                    textContentColor = TextLavender,
                    title = {
                        Text(
                            text = "Transport Notice",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = unsupportedNotice,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissUnsupportedNotice() }) {
                            Text("OK", color = PrimaryAccentLight, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NuvioSourceSelectCard(
    source: PlaybackSource,
    transport: String,
    isSelected: Boolean,
    isPlayable: Boolean,
    onClick: () -> Unit,
    onDoubleTapOrPlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkActionPill else NuvioCardSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) DarkActionPill else NuvioBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Provider Badge + Badges + Radio Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Provider Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) PrimaryAccentRoyal else PrimaryAccentRoyal.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = source.providerName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else PrimaryAccentRoyal
                        )
                    }

                    // Transport Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else NuvioSecondarySurface)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = transport,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPlayable) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                }

                // Selection Radio Indicator
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) PrimaryAccentRoyal else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (isSelected) PrimaryAccentRoyal else NuvioBorder,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            // Title / Release string
            Text(
                text = source.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else NuvioTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Metadata Row: Resolution, Codec, File Size, Seeders
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!source.resolution.isNullOrBlank()) {
                    SourceBadge(text = source.resolution, isAccent = true, isSelected = isSelected)
                }
                if (!source.codec.isNullOrBlank()) {
                    SourceBadge(text = source.codec, isSelected = isSelected)
                }
                if (!source.fileSize.isNullOrBlank()) {
                    SourceBadge(text = source.fileSize, isSelected = isSelected)
                }
                if (source.seeders != null && source.seeders > 0) {
                    SourceBadge(text = "👤 ${source.seeders}", isSelected = isSelected)
                }
                if (source.availableAudioTracks.isNotEmpty() && source.availableAudioTracks.first() != "Original Audio") {
                    SourceBadge(text = "🔊 ${source.availableAudioTracks.first()}", isSelected = isSelected)
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(
    text: String,
    isAccent: Boolean = false,
    isSelected: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.15f)
                else if (isAccent) PrimaryAccentRoyal.copy(alpha = 0.1f)
                else NuvioSecondarySurface
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White
            else if (isAccent) PrimaryAccentRoyal
            else NuvioTextSecondary
        )
    }
}
