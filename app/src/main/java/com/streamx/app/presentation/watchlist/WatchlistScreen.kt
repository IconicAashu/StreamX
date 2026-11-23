package com.streamx.app.presentation.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.presentation.components.MediaPosterCard
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchlist by viewModel.watchlist.collectAsState()

    StreamXBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saved Library",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                    Text(
                        text = "Your saved movies, series and anime",
                        fontSize = 13.sp,
                        color = NuvioTextSecondary
                    )
                }

                Text(
                    text = "${watchlist.size} saved",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryAccentRoyal
                )
            }

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
                        Text(
                            text = "Your Watchlist is Empty",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                        Text(
                            text = "Explore the catalog and tap the bookmark icon on any title to save it for later.",
                            fontSize = 14.sp,
                            color = NuvioTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = watchlist,
                        key = { index, item -> "watchlist_${item.id}_$index" },
                        contentType = { _, _ -> "WatchlistPosterCard" }
                    ) { _, item ->
                        Box {
                            MediaPosterCard(
                                item = item,
                                onClick = { onMediaClick(item) },
                                width = 165,
                                height = 240
                            )

                            // Quick remove button
                            IconButton(
                                onClick = { viewModel.removeFromWatchlist(item) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.9f))
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkRemove,
                                    contentDescription = "Remove",
                                    tint = NuvioTextPrimary,
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
