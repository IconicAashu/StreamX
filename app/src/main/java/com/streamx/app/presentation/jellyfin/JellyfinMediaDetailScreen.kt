package com.streamx.app.presentation.jellyfin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamx.app.network.jellyfin.model.JellyfinItemDto
import com.streamx.app.presentation.theme.*

@Composable
fun JellyfinMediaDetailScreen(
    itemId: String,
    viewModel: JellyfinViewModel,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(itemId) {
        viewModel.loadItemDetail(itemId)
    }

    val item = uiState.selectedItemDetail

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioBackground)
    ) {
        if (uiState.isLoadingDetail && item == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NuvioAccentViolet,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (item != null) {
            val backdropUrl = viewModel.getImageUrl(item.id, "Backdrop")
            val posterUrl = viewModel.getImageUrl(item.id, "Primary")

            val durationMinutes = item.runTimeTicks?.let { (it / 10_000_000L / 60L).toInt() } ?: 0
            val formattedDuration = if (durationMinutes > 0) {
                val hours = durationMinutes / 60
                val mins = durationMinutes % 60
                if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            } else null

            val hasResume = (item.userData?.playbackPositionTicks ?: 0L) > 0L
            val resumePercent = if (hasResume && (item.runTimeTicks ?: 0L) > 0L) {
                ((item.userData!!.playbackPositionTicks.toFloat() / item.runTimeTicks!!.toFloat()) * 100).toInt()
            } else 0

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // Hero Backdrop Header
                item(key = "detail_hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(backdropUrl.ifBlank { posterUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Vertical and horizontal gradients
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            NuvioBackground
                                        )
                                    )
                                )
                        )

                        // Top back button
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, NuvioBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Poster, Title & Metadata Info Row
                item(key = "detail_header_info") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .offset(y = (-40).dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Poster Card
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(0.67f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NuvioCardSurface)
                                .border(1.5.dp, NuvioBorder, RoundedCornerShape(12.dp))
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
                        }

                        // Title, Year, Duration, Rating
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 44.dp)
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextPrimary,
                                lineHeight = 26.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item.productionYear?.let { year ->
                                    Text(
                                        text = "$year",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = NuvioTextSecondary
                                    )
                                }

                                formattedDuration?.let { dur ->
                                    Text("•", color = NuvioTextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = dur,
                                        fontSize = 13.sp,
                                        color = NuvioTextSecondary
                                    )
                                }

                                item.officialRating?.let { rating ->
                                    Text("•", color = NuvioTextSecondary, fontSize = 12.sp)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(1.dp, NuvioBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = rating,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NuvioTextSecondary
                                        )
                                    }
                                }
                            }

                            // Community Rating
                            item.communityRating?.let { rating ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "★",
                                        color = Color(0xFFFFC107),
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", rating),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NuvioTextPrimary
                                    )
                                    Text(
                                        text = " / 10",
                                        fontSize = 11.sp,
                                        color = NuvioTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons (Play / Resume)
                item(key = "detail_actions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .offset(y = (-20).dp)
                    ) {
                        if (item.type.equals("Movie", ignoreCase = true) || item.type.equals("Episode", ignoreCase = true)) {
                            Button(
                                onClick = { onPlayClick("jellyfin_${item.id}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasResume) "Resume ($resumePercent%)" else "Play",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Genres
                        if (!item.genres.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(item.genres) { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(NuvioCardSurface)
                                            .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = genre,
                                            fontSize = 12.sp,
                                            color = NuvioTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Overview Synopsis
                item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    item(key = "detail_overview") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "OVERVIEW",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = overview,
                                fontSize = 14.sp,
                                color = NuvioTextPrimary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                // TV Series Seasons & Episodes Section
                if (item.type.equals("Series", ignoreCase = true)) {
                    // Seasons Selector
                    if (uiState.seasons.isNotEmpty()) {
                        item(key = "detail_seasons") {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "SEASONS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuvioTextSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    items(uiState.seasons, key = { it.id }) { season ->
                                        val isSelected = uiState.selectedSeason?.id == season.id
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSelected) NuvioAccentViolet else NuvioCardSurface)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) NuvioAccentViolet else NuvioBorder,
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .clickable { viewModel.selectSeason(season) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = season.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else NuvioTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Episodes List
                    item(key = "detail_episodes_header") {
                        Text(
                            text = "EPISODES (${uiState.episodes.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextSecondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }

                    if (uiState.episodes.isEmpty()) {
                        item(key = "detail_episodes_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No episodes found for this season.",
                                    color = NuvioTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(uiState.episodes, key = { it.id }) { episode ->
                            JellyfinEpisodeRow(
                                episode = episode,
                                getImageUrl = { id -> viewModel.getImageUrl(id, "Primary") },
                                onPlayClick = { onPlayClick("jellyfin_${episode.id}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JellyfinEpisodeRow(
    episode: JellyfinItemDto,
    getImageUrl: (String) -> String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailUrl = getImageUrl(episode.id)

    val epMinutes = episode.runTimeTicks?.let { (it / 10_000_000L / 60L).toInt() } ?: 0
    val durationText = if (epMinutes > 0) "${epMinutes}m" else ""

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onPlayClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Play overlay
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val epNumber = episode.indexNumber?.let { "Episode $it • " } ?: ""
                Text(
                    text = "$epNumber${episode.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (durationText.isNotBlank()) {
                    Text(
                        text = durationText,
                        fontSize = 11.sp,
                        color = NuvioTextSecondary
                    )
                }

                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = overview,
                        fontSize = 11.sp,
                        color = NuvioTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
