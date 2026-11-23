package com.streamx.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.presentation.components.MediaPosterCard
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onOpenDiscoverHub: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    StreamXBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Sticky Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(NuvioSecondarySurface)
                    .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = NuvioTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.query.isEmpty()) {
                            Text(
                                text = "Search movies, series, cast, anime...",
                                fontSize = 14.sp,
                                color = NuvioTextSecondary
                            )
                        }
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.onQueryChange(it) },
                            textStyle = TextStyle(
                                color = NuvioTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(PrimaryAccentRoyal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearQuery() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear",
                                tint = NuvioTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Recent Search History Chips
            if (uiState.recentSearches.isNotEmpty() && uiState.query.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = NuvioTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Recent Searches",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NuvioTextSecondary
                            )
                        }

                        Text(
                            text = "Clear all",
                            fontSize = 12.sp,
                            color = PrimaryAccentRoyal,
                            modifier = Modifier
                                .clickable { viewModel.clearAllRecentQueries() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.recentSearches,
                            key = { index, recent -> "recent_${recent}_$index" },
                            contentType = { _, _ -> "RecentSearchChip" }
                        ) { _, recent ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NuvioSecondarySurface)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.onRecentQueryClick(recent) }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = recent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = NuvioTextPrimary
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeRecentQuery(recent) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove",
                                            tint = NuvioTextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Trending Suggestions
            if (uiState.query.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Popular Searches",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(
                            items = uiState.popularSuggestions,
                            key = { index, tag -> "pop_${tag}_$index" },
                            contentType = { _, _ -> "PopularSuggestionChip" }
                        ) { _, tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NuvioSecondarySurface)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.onQueryChange(tag) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 12.sp,
                                    color = NuvioTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Dynamic Genre Filter Chips from API
            if (uiState.genreOptions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Filter by Genre",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.genreOptions,
                            key = { "genre_opt_${it.id}" },
                            contentType = { "GenreOptChip" }
                        ) { opt ->
                            val isSelected = opt.id.equals(uiState.selectedGenre, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) DarkActionPill else NuvioSecondarySurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) DarkActionPill else NuvioBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.onGenreSelect(opt.id) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = opt.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else NuvioTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Results Header info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.query.isNotBlank()) "Search Results" else "Explore Catalog",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenDiscoverHub != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NuvioSecondarySurface)
                                .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
                                .clickable { onOpenDiscoverHub() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Discover Hub ↗",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryAccentRoyal
                            )
                        }
                    }

                    Text(
                        text = "${uiState.results.size} titles",
                        fontSize = 12.sp,
                        color = NuvioTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clean 2-Column Results Grid
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryAccentRoyal)
                }
            } else if (uiState.results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for \"${uiState.query}\".\nTry searching for titles like \"Avatar\", \"Blue Beetle\", or \"Anime\".",
                        color = NuvioTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
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
                        items = uiState.results,
                        key = { index, item -> "search_${item.id}_$index" },
                        contentType = { _, _ -> "SearchResultCard" }
                    ) { _, item ->
                        MediaPosterCard(
                            item = item,
                            onClick = { onMediaClick(item) },
                            width = 165,
                            height = 240
                        )
                    }
                }
            }
        }
    }
}
