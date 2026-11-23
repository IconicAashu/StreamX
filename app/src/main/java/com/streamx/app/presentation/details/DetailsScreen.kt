@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.streamx.app.presentation.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamx.app.domain.model.CastMember
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.PlaybackSource
import com.streamx.app.domain.model.StremioVideo
import com.streamx.app.presentation.components.ContentRow
import com.streamx.app.presentation.components.PillChip
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBackClick: () -> Unit,
    onOpenSourceSelection: (mediaItem: MediaItem, targetId: String?, episodeTitle: String?) -> Unit,
    onSimilarMediaClick: (MediaItem) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val heroHeight = screenHeight * 0.45f

    StreamXBackground {
        val item = uiState.mediaItem
        if (item == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = PrimaryAccentRoyal)
                } else {
                    Text(
                        text = uiState.errorMessage ?: "Details not available",
                        color = TextLavender
                    )
                }
            }
            return@StreamXBackground
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Top Hero Artwork container (~45% screen height) with rounded bottom corners (28.dp)
                item(key = "details_hero_artwork", contentType = "HeroArtwork") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight + 30.dp)
                    ) {
                        // Artwork with rounded bottom corners
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.backdropUrl.takeIf { it.isNotBlank() } ?: item.posterUrl.takeIf { it.isNotBlank() } ?: com.streamx.app.R.drawable.streamx_logo)
                                    .crossfade(150)
                                    .size(width = 1280, height = 720)
                                    .placeholder(com.streamx.app.R.drawable.streamx_logo)
                                    .error(com.streamx.app.R.drawable.streamx_logo)
                                    .fallback(com.streamx.app.R.drawable.streamx_logo)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Gradient overlays for controls readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to Color.Black.copy(alpha = 0.55f),
                                            0.3f to Color.Transparent,
                                            0.7f to Color.Transparent,
                                            1.0f to MidnightDark.copy(alpha = 0.85f)
                                        )
                                    )
                            )

                            // Top overlay controls: back button on left, menu/share on right
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.85f))
                                        .border(1.dp, Color(0x22000000), CircleShape)
                                        .clickable { onBackClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = NuvioTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.85f))
                                            .border(1.dp, Color(0x22000000), CircleShape)
                                            .clickable { viewModel.toggleWatchlist() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isSaved) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Save",
                                            tint = if (uiState.isSaved) PrimaryAccentRoyal else NuvioTextPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Overlapping circular Play button
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(56.dp)
                                .shadow(16.dp, CircleShape, spotColor = Color.Black)
                                .clip(CircleShape)
                                .background(DarkActionPill)
                                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                                .clickable {
                                    val defaultEp = uiState.displayedEpisodes.firstOrNull()
                                    val targetId = if (uiState.seasons.isNotEmpty() && defaultEp != null) defaultEp.id else item.id
                                    val epTitle = if (uiState.seasons.isNotEmpty() && defaultEp != null) {
                                        "${item.title} • S${defaultEp.season}E${defaultEp.episode}: ${defaultEp.title ?: "Episode ${defaultEp.episode}"}"
                                    } else null
                                    onOpenSourceSelection(item, targetId, epTitle)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Content Body Below Hero Artwork
                item(key = "details_content_body", contentType = "ContentBody") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${item.releaseDateFormatted} • ${item.runtimeFormatted}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = NuvioTextSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row of rounded pill badges: [Runtime] [Genre] [Format] [Maturity]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PillChip(text = item.runtimeFormatted)
                            PillChip(text = item.primaryGenre)
                            PillChip(text = item.format.displayName)
                            PillChip(text = item.maturityRating)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Rating & Engagement Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NuvioSecondarySurface)
                                .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = StarRatingGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "%.1f/10".format(item.rating),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NuvioTextPrimary
                                )
                                Text(
                                    text = "(${item.ratingVotesCount})",
                                    fontSize = 12.sp,
                                    color = NuvioTextSecondary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ThumbUp,
                                        contentDescription = "Likes",
                                        tint = NuvioTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = item.likesCount,
                                        fontSize = 12.sp,
                                        color = NuvioTextSecondary
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.toggleWatchlist() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Save to Watchlist",
                                        tint = if (uiState.isSaved) Color(0xFFFF4D6D) else NuvioTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Primary Action Buttons: Play + Sources + Watchlist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val defaultEp = uiState.displayedEpisodes.firstOrNull()
                                    val targetId = if (uiState.seasons.isNotEmpty() && defaultEp != null) defaultEp.id else item.id
                                    val epTitle = if (uiState.seasons.isNotEmpty() && defaultEp != null) {
                                        "${item.title} • S${defaultEp.season}E${defaultEp.episode}: ${defaultEp.title ?: "Episode ${defaultEp.episode}"}"
                                    } else null
                                    onOpenSourceSelection(item, targetId, epTitle)
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkActionPill,
                                    contentColor = Color.White
                                )
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
                                        text = "Watch Now",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Sources Selector button
                            OutlinedButton(
                                onClick = {
                                    val defaultEp = uiState.displayedEpisodes.firstOrNull()
                                    val targetId = if (uiState.seasons.isNotEmpty() && defaultEp != null) defaultEp.id else item.id
                                    val epTitle = if (uiState.seasons.isNotEmpty() && defaultEp != null) {
                                        "${item.title} • S${defaultEp.season}E${defaultEp.episode}: ${defaultEp.title ?: "Episode ${defaultEp.episode}"}"
                                    } else null
                                    onOpenSourceSelection(item, targetId, epTitle)
                                },
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = NuvioSecondarySurface,
                                    contentColor = NuvioTextPrimary
                                ),
                                border = BorderStroke(1.dp, NuvioBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = PrimaryAccentRoyal
                                    )
                                    Text(
                                        text = "Sources",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SEASONS & EPISODES SECTION (For Series & Anime)
                        if (uiState.seasons.isNotEmpty() && uiState.displayedEpisodes.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Episodes",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Text(
                                    text = "${uiState.displayedEpisodes.size} Episodes",
                                    fontSize = 12.sp,
                                    color = TextLavender
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Season selector chips row
                            if (uiState.seasons.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(uiState.seasons) { seasonNum ->
                                        val isSelected = seasonNum == uiState.selectedSeason
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) PrimaryAccentRoyal else SurfacePurpleSlate)
                                                .border(1.dp, if (isSelected) PrimaryAccentLight else SurfaceBorderLight, RoundedCornerShape(12.dp))
                                                .clickable { viewModel.selectSeason(seasonNum) }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Season $seasonNum",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) TextWhite else TextLavender
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Episode Cards Horizontal Row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(uiState.displayedEpisodes, key = { it.id }) { ep ->
                                    EpisodeCard(
                                        episode = ep,
                                        onClick = {
                                            onOpenSourceSelection(
                                                item,
                                                ep.id,
                                                "${item.title} • S${ep.season}E${ep.episode}: ${ep.title ?: "Episode ${ep.episode}"}"
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Cast Section Header with "See all"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cast",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "See all",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryAccentLight,
                                modifier = Modifier.clickable { /* See all cast */ }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cast Members Horizontal Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(
                                items = item.cast,
                                key = { index, actor -> "cast_${actor.id}_$index" },
                                contentType = { _, _ -> "CastPortraitCard" }
                            ) { _, actor ->
                                CastPortraitCard(actor = actor)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Synopsis Section
                        Text(
                            text = "Synopsis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.synopsis,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = TextLavender
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Audio & Subtitles Badges
                        Text(
                            text = "Audio & Subtitles",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item.audioLanguages.take(3).forEach { audio ->
                                PillChip(text = audio)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Similar Titles Row
                if (uiState.similarTitles.isNotEmpty()) {
                    item(key = "details_similar_titles", contentType = "ContentRow") {
                        ContentRow(
                            title = "Similar Titles",
                            items = uiState.similarTitles,
                            onItemClick = onSimilarMediaClick,
                            onSeeAllClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: StremioVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePurpleSlate),
        border = BorderStroke(1.dp, SurfaceBorderLight),
        modifier = modifier
            .width(220.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1C29)),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.thumbnail,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, PrimaryAccentLight.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play Episode",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Episode ${episode.episode}: ${episode.title ?: ""}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.overview,
                    fontSize = 11.sp,
                    color = TextLavender,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CastPortraitCard(
    actor: CastMember,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(90.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 110.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, SurfaceBorderLight.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(actor.profileUrl)
                    .crossfade(150)
                    .size(width = 180, height = 220)
                    .build(),
                contentDescription = actor.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = actor.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = actor.characterName,
            fontSize = 10.sp,
            color = TextLavender,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
