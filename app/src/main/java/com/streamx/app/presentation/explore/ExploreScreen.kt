package com.streamx.app.presentation.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.domain.model.FilterOption
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.presentation.components.ContentRow
import com.streamx.app.presentation.components.MediaPosterCard
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val moods = remember {
        listOf(
            MoodItem("Feel good", "Comedy", Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Default.SentimentSatisfiedAlt),
            MoodItem("Emotional", "Drama", Color(0xFFDBEAFE), Color(0xFF2563EB), Icons.Default.Favorite),
            MoodItem("Thought-Provoking", "Sci-Fi", Color(0xFFD1FAE5), Color(0xFF059669), Icons.Default.Psychology),
            MoodItem("Suspenseful", "Thriller", Color(0xFFFFE4E6), Color(0xFFE11D48), Icons.Default.SecurityUpdateWarning),
            MoodItem("Mysterious", "Mystery", Color(0xFFEDE9FE), Color(0xFF7C3AED), Icons.Default.Visibility),
            MoodItem("Romantic", "Romance", Color(0xFFFCE7F3), Color(0xFFDB2777), Icons.Default.FavoriteBorder),
            MoodItem("Action Packed", "Action", Color(0xFFFFEDD5), Color(0xFFEA580C), Icons.Default.Bolt),
            MoodItem("Mind Bending", "Fantasy", Color(0xFFE0E7FF), Color(0xFF4F46E5), Icons.Default.AutoAwesome)
        )
    }

    StreamXBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Screen Header: What mood are you looking for?
            item(key = "discover_header", contentType = "Header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "What mood are you",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextPrimary
                            )
                            Text(
                                text = "looking for?",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextPrimary
                            )
                        }

                        // Filter Sheet button with active count badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (uiState.activeFilters.hasActiveFilters) DarkActionPill else NuvioSecondarySurface)
                                .border(1.dp, if (uiState.activeFilters.hasActiveFilters) DarkActionPill else NuvioBorder, RoundedCornerShape(20.dp))
                                .clickable { viewModel.toggleAdvancedFilterSheet(true) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = "Filters",
                                    tint = if (uiState.activeFilters.hasActiveFilters) Color.White else NuvioTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (uiState.activeFilters.activeFilterCount > 0)
                                        "Filters (${uiState.activeFilters.activeFilterCount})"
                                    else
                                        "Filters",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.activeFilters.hasActiveFilters) Color.White else NuvioTextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2-Column Mood Cards Grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        moods.chunked(2).forEach { rowMoods ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowMoods.forEach { mood ->
                                    val isSelected = uiState.activeFilters.selectedGenre.equals(mood.genreQuery, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) mood.accentColor.copy(alpha = 0.2f) else mood.bgColor)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) mood.accentColor else Color(0x1F000000),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                if (isSelected) {
                                                    viewModel.selectGenre("All")
                                                } else {
                                                    viewModel.selectGenre(mood.genreQuery)
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .border(1.dp, mood.accentColor.copy(alpha = 0.3f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = mood.icon,
                                                    contentDescription = null,
                                                    tint = mood.accentColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = mood.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NuvioTextPrimary
                                                )
                                                Text(
                                                    text = mood.genreQuery,
                                                    fontSize = 11.sp,
                                                    color = NuvioTextSecondary
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

            // Channel Selector (e.g. "Movie")
            if (uiState.channels.size > 1) {
                item(key = "discover_channels", contentType = "Channels") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.channels, key = { "channel_${it.channelId}" }) { channel ->
                            val isSelected = channel.channelId == uiState.selectedChannelId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isSelected) PrimaryAccentRoyal else SurfacePurpleSlate)
                                    .border(1.dp, if (isSelected) PrimaryAccentLight else SurfaceBorderLight, RoundedCornerShape(18.dp))
                                    .clickable { viewModel.selectChannel(channel.channelId) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = channel.channelName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextWhite else TextLavender
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Prominent Filter 1: Genre Filter Chips (showOut = true)
            if (uiState.genres.isNotEmpty()) {
                item(key = "discover_genres_row", contentType = "FilterRow") {
                    FilterChipRow(
                        title = "Genre",
                        options = uiState.genres,
                        selectedId = uiState.activeFilters.selectedGenre,
                        onSelect = { viewModel.selectGenre(it) }
                    )
                }
            }

            // Prominent Filter 2: Country Filter Chips (showOut = true)
            if (uiState.countries.isNotEmpty()) {
                item(key = "discover_countries_row", contentType = "FilterRow") {
                    FilterChipRow(
                        title = "Country",
                        options = uiState.countries,
                        selectedId = uiState.activeFilters.selectedCountry,
                        onSelect = { viewModel.selectCountry(it) }
                    )
                }
            }

            // Prominent Filter 3: Year Filter Chips (showOut = true)
            if (uiState.years.isNotEmpty()) {
                item(key = "discover_years_row", contentType = "FilterRow") {
                    FilterChipRow(
                        title = "Release Year",
                        options = uiState.years,
                        selectedId = uiState.activeFilters.selectedYear,
                        onSelect = { viewModel.selectYear(it) }
                    )
                }
            }

            // Active Filter summary pills (if any applied)
            if (uiState.activeFilters.hasActiveFilters) {
                item(key = "active_filter_chips", contentType = "ActiveChips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Active Filters",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryAccentLight
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.resetFilters() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = "Reset",
                                tint = TextLavender,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset all",
                                fontSize = 12.sp,
                                color = TextLavender
                            )
                        }
                    }
                }
            }

            // Curated Collection 1: Trending Spotlight Carousel (when no strict filters)
            if (!uiState.activeFilters.hasActiveFilters && uiState.trendingSpotlight.isNotEmpty()) {
                item(key = "discover_spotlight", contentType = "ContentRow") {
                    ContentRow(
                        title = "🔥 Popular Spotlight",
                        items = uiState.trendingSpotlight,
                        onItemClick = onMediaClick,
                        onSeeAllClick = {}
                    )
                }
            }

            // Curated Collection 2: Award Winners & Masterpieces
            if (!uiState.activeFilters.hasActiveFilters && uiState.awardWinners.isNotEmpty()) {
                item(key = "discover_award_winners", contentType = "ContentRow") {
                    ContentRow(
                        title = "🏆 Award Winners & Masterpieces",
                        items = uiState.awardWinners,
                        onItemClick = onMediaClick,
                        onSeeAllClick = {}
                    )
                }
            }

            // Section Header: Catalog Results Grid
            item(key = "discover_grid_header", contentType = "SectionHeader") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val titleText = buildString {
                        if (uiState.activeFilters.selectedGenre != "All") append("${uiState.activeFilters.selectedGenre} ")
                        if (uiState.activeFilters.selectedCountry != "All") append("${uiState.activeFilters.selectedCountry} ")
                        if (uiState.activeFilters.selectedYear != "All") append("(${uiState.activeFilters.selectedYear}) ")
                        if (isEmpty()) append("All ${uiState.selectedChannelName}s")
                    }

                    Text(
                        text = titleText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Text(
                        text = "${uiState.filteredGridItems.size} titles",
                        fontSize = 12.sp,
                        color = PrimaryAccentLight,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3-Column Poster Grid matching API style: { colNum: 3, shape: "Vertical" }
            if (uiState.isLoading) {
                item(key = "discover_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryAccentRoyal)
                    }
                }
            } else if (uiState.filteredGridItems.isEmpty()) {
                item(key = "discover_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No titles match the selected filters.\nTry resetting filters to explore more movies.",
                            color = TextLavender,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.filteredGridRows,
                    key = { index, row -> "grid_row_${index}_${row.joinToString("_") { it.id }}" },
                    contentType = { _, _ -> "GridRow" }
                ) { _, rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (item in rowItems) {
                            Box(modifier = Modifier.weight(1f)) {
                                MediaPosterCard(
                                    item = item,
                                    onClick = { onMediaClick(item) },
                                    width = 110,
                                    height = 165
                                )
                            }
                        }
                        // Fill empty slots if last row has fewer than colNum items
                        val emptySlots = uiState.colNum - rowItems.size
                        if (emptySlots > 0) {
                            repeat(emptySlots) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Advanced Filter Modal Bottom Sheet matching reference image
        if (uiState.isAdvancedFilterSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.toggleAdvancedFilterSheet(false) },
                containerColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    // Header: Filters | Clear all | ✕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filters",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Clear all",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = NuvioTextSecondary,
                                modifier = Modifier.clickable { viewModel.resetFilters() }
                            )

                            IconButton(
                                onClick = { viewModel.toggleAdvancedFilterSheet(false) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = NuvioTextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. Content Type pills
                    if (uiState.channels.isNotEmpty()) {
                        Text(
                            text = "Content",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.channels, key = { "modal_ch_${it.channelId}" }) { ch ->
                                val isSelected = ch.channelId == uiState.selectedChannelId
                                com.streamx.app.presentation.components.GlassPill(
                                    text = ch.channelName,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectChannel(ch.channelId) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // 2. Genres section
                    if (uiState.genres.isNotEmpty()) {
                        Text(
                            text = "Genres",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.genres.take(8), key = { "modal_g_${it.id}" }) { g ->
                                val isSelected = g.id.equals(uiState.activeFilters.selectedGenre, ignoreCase = true)
                                com.streamx.app.presentation.components.GlassPill(
                                    text = g.name,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectGenre(g.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // 3. Sort by section
                    if (uiState.sortOptions.isNotEmpty()) {
                        Text(
                            text = "Sort by",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            uiState.sortOptions.forEach { opt ->
                                val isSelected = uiState.activeFilters.selectedSort == opt.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) DarkActionPill else NuvioSecondarySurface)
                                        .border(1.dp, if (isSelected) DarkActionPill else NuvioBorder, RoundedCornerShape(16.dp))
                                        .clickable { viewModel.selectSort(opt.id) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else NuvioTextPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // 4. Rating section
                    Text(
                        text = "Rating (${if (uiState.activeFilters.selectedMinRating == 0f) "Any" else String.format("%.1f+", uiState.activeFilters.selectedMinRating)})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NuvioTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0f to "Any", 6.0f to "6+", 7.0f to "7+", 8.0f to "8+", 9.0f to "9+").forEach { (valFloat, label) ->
                            val isSelected = uiState.activeFilters.selectedMinRating == valFloat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) DarkActionPill else NuvioSecondarySurface)
                                    .border(1.dp, if (isSelected) DarkActionPill else NuvioBorder, RoundedCornerShape(14.dp))
                                    .clickable { viewModel.selectMinRating(valFloat) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (valFloat > 0f) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = StarRatingGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else NuvioTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Large full-width button: Show X results
                    Button(
                        onClick = { viewModel.toggleAdvancedFilterSheet(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkActionPill,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Show ${uiState.filteredGridItems.size} results",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

private data class MoodItem(
    val title: String,
    val genreQuery: String,
    val bgColor: Color,
    val accentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun FilterChipRow(
    title: String,
    options: List<FilterOption>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = options,
                key = { "chip_${title}_${it.id}" },
                contentType = { "FilterChip" }
            ) { option ->
                val isSelected = option.id.equals(selectedId, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) PrimaryAccentRoyal else SurfacePurpleSlate)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) PrimaryAccentLight else SurfaceBorderLight,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(option.id) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = option.name,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextWhite else TextLavender
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
