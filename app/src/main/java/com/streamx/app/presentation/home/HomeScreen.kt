package com.streamx.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.presentation.components.*
import com.streamx.app.presentation.theme.PrimaryAccentRoyal

/**
 * STREAMX HomeScreen matching the visual reference design pixel-for-pixel:
 * 1. STREAMX Header: "STREAM" (white) + "X" (purple), "Movies • Series • More", Search & Notification with badge
 * 2. FEATURED Hero Card: "★ FEATURED" badge, Title, Genres, "▶ Play" pill button, Artwork & Dots
 * 3. Trending Row: Header "Trending" + "See All >", horizontal list of poster cards with bottom-aligned titles
 * 4. Genres Grid: Header "Genres" + "See All >", 3-column colorful cards (Action, Comedy, Drama, Adventure, Thriller, Horror)
 * 5. Dynamic Pagination & Continuous Catalog Rows (Movies, Series, Anime, For You)
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit = onMediaClick,
    onSearchClick: () -> Unit = {},
    onSeeAllClick: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val trendingRowState = rememberLazyListState()

    val genreCards = remember {
        listOf(
            GenreCard("Action", "Action", Icons.Default.Movie, listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))),
            GenreCard("Comedy", "Comedy", Icons.Default.SentimentSatisfiedAlt, listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
            GenreCard("Drama", "Drama", Icons.Default.Theaters, listOf(Color(0xFFEA580C), Color(0xFFC2410C))),
            GenreCard("Adventure", "Adventure", Icons.Default.SportsEsports, listOf(Color(0xFF059669), Color(0xFF047857))),
            GenreCard("Thriller", "Thriller", Icons.Default.RocketLaunch, listOf(Color(0xFFE11D48), Color(0xFFBE123C))),
            GenreCard("Horror", "Horror", Icons.Default.NightlightRound, listOf(Color(0xFF6366F1), Color(0xFF4338CA)))
        )
    }

    // Observe vertical scroll near bottom for endless pagination
    val shouldLoadMoreVertical = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMoreVertical.value) {
        if (shouldLoadMoreVertical.value && !uiState.isLoadingMore && uiState.hasMore) {
            viewModel.loadNextPage()
        }
    }

    val featuredItem = uiState.heroItems.firstOrNull() ?: uiState.trendingItems.firstOrNull()

    StreamXBackground(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // 1. STREAMX Top Bar Header & Search Input
            item(key = "home_top_bar", contentType = "TopBar") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StreamXTopBar(
                        onSearchClick = onSearchClick,
                        onNotificationClick = { /* Notification sheet / center */ }
                    )
                    
                    // Search Input Bar with Filter Button matching reference
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        StreamXSearchInputBar(
                            query = "",
                            onQueryChange = { onSearchClick() },
                            onFilterClick = { onSeeAllClick("Filter") },
                            placeholder = "Movies, series, shows...",
                            modifier = Modifier.clickable { onSearchClick() }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Filter Pills Row (All, Adventure, Comedy, Fantasy, Action...)
                    val homeCategories = remember {
                        listOf(
                            MediaCategory.TRENDING to "All",
                            MediaCategory.MOVIES to "Movies",
                            MediaCategory.SERIES to "Series",
                            MediaCategory.ANIME to "Anime",
                            MediaCategory.FOR_YOU to "For You"
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(homeCategories) { (cat, label) ->
                            val isSelected = uiState.selectedCategory == cat
                            GlassPill(
                                text = label,
                                isSelected = isSelected,
                                onClick = { viewModel.selectCategory(cat) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. FEATURED Hero Card
            if (featuredItem != null) {
                item(key = "home_featured_card", contentType = "FeaturedCard") {
                    StreamXFeaturedCard(
                        item = featuredItem,
                        onPlayClick = onPlayClick,
                        onItemClick = onMediaClick,
                        pageCount = 4,
                        currentPage = 0
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Category Filter Spotlight (if active)
            if (uiState.selectedCategory != MediaCategory.TRENDING && uiState.categoryFilteredItems.isNotEmpty()) {
                item(key = "home_category_spotlight", contentType = "CategorySpotlight") {
                    ContentRow(
                        title = "${uiState.selectedCategory.title} Catalog",
                        items = uiState.categoryFilteredItems,
                        onItemClick = onMediaClick,
                        onSeeAllClick = { onSeeAllClick(uiState.selectedCategory.title) }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // 3. "Trending" Section Header & Carousel
            item(key = "home_trending_header", contentType = "SectionHeader") {
                StreamXSectionHeader(
                    title = "Trending",
                    onSeeAllClick = { onSeeAllClick("Trending") }
                )
            }

            item(key = "home_trending_list", contentType = "TrendingList") {
                val trendingList = uiState.trendingItems.ifEmpty { uiState.heroItems }
                LazyRow(
                    state = trendingRowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = trendingList,
                        key = { index, item -> "trending_${item.id}_$index" }
                    ) { _, item ->
                        StreamXTrendingCard(
                            item = item,
                            onClick = { onMediaClick(item) }
                        )
                    }

                    if (uiState.isLoadingMore && uiState.selectedCategory == MediaCategory.TRENDING) {
                        item(key = "trending_loading_more") {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(176.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryAccentRoyal,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 4. "Genres" Section Header & 3-Column Grid
            item(key = "home_genres_header", contentType = "SectionHeader") {
                StreamXSectionHeader(
                    title = "Genres",
                    onSeeAllClick = { onSeeAllClick("Genres") }
                )
            }

            item(key = "home_genres_grid", contentType = "GenreGrid") {
                StreamXGenreGrid(
                    genres = genreCards,
                    onGenreClick = { genreId ->
                        viewModel.filterByGenre(genreId)
                        onGenreClick(genreId)
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 5. Popular Movies Row
            if (uiState.moviesItems.isNotEmpty()) {
                item(key = "home_movies_row", contentType = "ContentRow") {
                    ContentRow(
                        title = "Popular Movies",
                        items = uiState.moviesItems,
                        onItemClick = onMediaClick,
                        onSeeAllClick = { onSeeAllClick("Movies") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 6. TV Series Row
            if (uiState.seriesItems.isNotEmpty()) {
                item(key = "home_series_row", contentType = "ContentRow") {
                    ContentRow(
                        title = "TV Series",
                        items = uiState.seriesItems,
                        onItemClick = onMediaClick,
                        onSeeAllClick = { onSeeAllClick("Series") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 7. Anime Row
            if (uiState.animeItems.isNotEmpty()) {
                item(key = "home_anime_row", contentType = "ContentRow") {
                    ContentRow(
                        title = "Anime",
                        items = uiState.animeItems,
                        onItemClick = onMediaClick,
                        onSeeAllClick = { onSeeAllClick("Anime") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 8. For You Row
            if (uiState.forYouItems.isNotEmpty()) {
                item(key = "home_for_you_row", contentType = "ContentRow") {
                    ContentRow(
                        title = "For You",
                        items = uiState.forYouItems,
                        onItemClick = onMediaClick,
                        onSeeAllClick = { onSeeAllClick("For You") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Dynamic Addon Catalog Rows with guaranteed unique keys
            uiState.addonSections.forEachIndexed { sIndex, section ->
                if (section.items.isNotEmpty()) {
                    item(key = "home_addon_${section.addonId}_${section.catalogId}_${section.type}_$sIndex", contentType = "AddonContentRow") {
                        ContentRow(
                            title = section.title,
                            items = section.items,
                            onItemClick = onMediaClick,
                            onSeeAllClick = { onSeeAllClick(section.title) }
                        )
                    }
                }
            }

            // Vertical Pagination Loading Indicator
            if (uiState.isLoadingMore) {
                item(key = "home_loading_more_footer", contentType = "LoadingFooter") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryAccentRoyal,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}
