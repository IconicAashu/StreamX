package com.streamx.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.presentation.theme.*
import kotlin.math.absoluteValue

private val HeroPosterScrimBrush = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.65f to Color.Transparent,
    1.0f to Color(0xCC110D24)
)

@Composable
fun StreamXBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gradientBrush = rememberStreamXGradientBrush()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush),
        content = content
    )
}

@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    cornerRadius: RoundedCornerShape = RoundedCornerShape(16.dp),
    backgroundColor: Color = SurfacePurpleSlate,
    borderColor: Color = SurfaceBorderLight,
    borderWidth: Int = 1,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(cornerRadius)
            .background(backgroundColor)
            .border(borderWidth.dp, borderColor, cornerRadius),
        content = content
    )
}

/**
 * Clean plain-text category tabs (Trending, New, Movies, Series, TV Show, Anime):
 * Borderless, pill-less.
 * Active tab: 20.sp, FontWeight.SemiBold, #FFFFFF
 * Inactive tabs: 16.sp, FontWeight.Normal, #A89FD1
 */
@Composable
fun CategoryTabsRow(
    categories: List<MediaCategory>,
    selectedCategory: MediaCategory,
    onCategorySelected: (MediaCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            items = categories,
            key = { it.name },
            contentType = { "CategoryTab" }
        ) { category ->
            val isSelected = category == selectedCategory
            val textColor by animateColorAsState(
                targetValue = if (isSelected) TextWhite else TextLavender,
                label = "tabTextColor"
            )

            Text(
                text = category.title,
                fontSize = if (isSelected) 20.sp else 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCategorySelected(category) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * Center-focused Hero Carousel matching the reference layout:
 * - contentPadding = PaddingValues(horizontal = 54.dp), pageSpacing = 16.dp
 * - Starts at index 1 so BOTH left and right neighboring cards peek equally from both screen edges
 * - Scale interpolation: 0.90f on side cards to 1.0f on center card
 * - 24.dp rounded corners
 * - Centered metadata below:
 *   * Year (13.sp, #B3ABD6)
 *   * Title (22.sp, FontWeight.Bold, #FFFFFF)
 *   * 3 pill chips (#2B2354 at 85% alpha, 1dp border #43387A, 20.dp corner radius): [Genre] [Runtime] [★ Rating]
 *   * Pager indicator dots: active dot 18.dp pill in #FFFFFF, inactive dots 6.dp circles in #4C437A
 */
@Composable
fun HeroCarousel(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val initialIndex = if (items.size > 1) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 54.dp),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 2,
            key = { index -> items[index].id },
            modifier = Modifier
                .fillMaxWidth()
                .height(370.dp)
        ) { page ->
            val item = items[page]

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // Calculate scale directly inside graphicsLayer to avoid recompositions during pager swipe
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue
                        val targetScale = (1f - (pageOffset * 0.10f)).coerceIn(0.90f, 1.0f)
                        scaleX = targetScale
                        scaleY = targetScale
                    }
                    .fillMaxSize()
                    .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = Color(0x666E56F8))
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, SurfaceBorderLight.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .clickable { onItemClick(item) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.posterUrl.takeIf { it.isNotBlank() } ?: com.streamx.app.R.drawable.streamx_logo)
                        .crossfade(150)
                        .size(width = 600, height = 900)
                        .placeholder(com.streamx.app.R.drawable.streamx_logo)
                        .error(com.streamx.app.R.drawable.streamx_logo)
                        .fallback(com.streamx.app.R.drawable.streamx_logo)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Cached bottom gradient for depth without per-page allocations
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HeroPosterScrimBrush)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hero Metadata directly beneath the carousel
        val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

        Text(
            text = currentItem.releaseYear,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = TextYearMuted
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = currentItem.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Row of 3 dark translucent pill chips: [Genre] | [Runtime] | [★ Rating]
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroPillChip(text = currentItem.primaryGenre)
            HeroPillChip(text = currentItem.runtimeFormatted)
            HeroPillChip(
                text = "${currentItem.rating}",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Rating",
                        tint = StarRatingGold,
                        modifier = Modifier.size(13.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pager indicator dots: active dot 18.dp pill in #FFFFFF, inactive dots 6.dp in #4C437A
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    label = "indicatorWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) PagerDotActive else PagerDotInactive,
                    label = "indicatorColor"
                )

                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

/**
 * 20.dp corner radius pill chip with #2B2354 background at 85% alpha and #43387A 1dp border
 */
@Composable
fun HeroPillChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HeroChipBg)
            .border(1.dp, HeroChipBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextWhite
            )
        }
    }
}

@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    HeroPillChip(text = text, modifier = modifier, leadingIcon = leadingIcon)
}

@Composable
fun ContentRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NuvioTextPrimary
            )

            Text(
                text = "See all",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = NuvioTextSecondary,
                modifier = Modifier
                    .clickable { onSeeAllClick() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${title}_${item.id}_$index" },
                contentType = { _, _ -> "MediaPosterCard" }
            ) { _, item ->
                MediaPosterCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun MediaPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 125,
    height: Int = 180
) {
    Column(
        modifier = modifier
            .width(width.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GlassBorderLight, RoundedCornerShape(16.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.posterUrl.takeIf { it.isNotBlank() } ?: com.streamx.app.R.drawable.streamx_logo)
                    .crossfade(150)
                    .size(width = width * 2, height = height * 2)
                    .placeholder(com.streamx.app.R.drawable.streamx_logo)
                    .error(com.streamx.app.R.drawable.streamx_logo)
                    .fallback(com.streamx.app.R.drawable.streamx_logo)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle bottom gradient inside poster image for depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.65f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.35f)
                        )
                    )
            )

            // Watchlist saved badge on top left
            if (item.isSaved) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryAccentRoyal.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Saved in Watchlist",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Rating badge on top right
            if (item.rating > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = StarRatingGold,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "%.1f".format(item.rating),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Continue watching progress bar if available
            if (item.continueWatchingProgress != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { item.continueWatchingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = PrimaryAccentRoyal,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    if (item.continueWatchingRemaining != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.continueWatchingRemaining,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = NuvioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${item.releaseYear}",
                fontSize = 11.5.sp,
                color = NuvioTextSecondary
            )
            if (item.rating > 0) {
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = NuvioTextSecondary
                )
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = StarRatingGold,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "%.1f".format(item.rating),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            } else if (item.primaryGenre.isNotBlank()) {
                Text(
                    text = "• ${item.primaryGenre}",
                    fontSize = 11.sp,
                    color = NuvioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * STREAMX 5-Destination Navigation Bar (iOS Liquid Glass design):
 * - 5 destinations: Home, Discover, Search, Watchlist, Profile
 * - Active tab: Spring-animated pill background indicator with violet-blue tint
 * - Inactive tabs: Soft iOS gray icons & labels (#86868B)
 */
enum class NavigationTab(val label: String, val iconFilled: ImageVector, val iconOutlined: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER("Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    WATCHLIST("Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person),
    MOVIES("Movies", Icons.Filled.Movie, Icons.Outlined.Movie),
    SERIES("Series", Icons.Filled.Tv, Icons.Outlined.Tv),
    LIBRARY("Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: RoundedCornerShape = RoundedCornerShape(20.dp),
    backgroundColor: Color = GlassSurfaceLight,
    borderColor: Color = GlassBorderLight,
    borderWidth: Int = 1,
    elevation: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = cornerRadius,
        color = backgroundColor,
        border = BorderStroke(borderWidth.dp, borderColor),
        shadowElevation = elevation,
        content = { Box(content = content) }
    )
}

@Composable
fun GlassPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val pillBg by animateColorAsState(
        targetValue = if (isSelected) DarkActionPill else NuvioSecondarySurface,
        label = "glassPillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else NuvioTextSecondary,
        label = "glassPillText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillBg)
            .border(
                width = 1.dp,
                color = if (isSelected) DarkActionPill else NuvioBorder.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
fun StreamXTopBar(
    onSearchClick: (() -> Unit)? = null,
    onNotificationClick: () -> Unit = {},
    showSearchAction: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "STREAM",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = NuvioTextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "X",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryAccentRoyal,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Movies • Series • Anime",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NuvioTextSecondary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSearchAction && onSearchClick != null) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NuvioTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NuvioSecondarySurface)
                    .border(1.dp, NuvioBorder.copy(alpha = 0.7f), CircleShape)
                    .clickable { onNotificationClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = NuvioTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                // Red Notification Badge Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }
    }
}

/**
 * Clean search bar with filter button matching the iOS reference UI (media_1795436838549_6b7be35b.webp).
 */
@Composable
fun StreamXSearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    placeholder: String = "Movies, series, shows...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NuvioSecondarySurface)
            .border(1.dp, NuvioBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = NuvioTextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = NuvioTextSecondary
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = NuvioTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(PrimaryAccentRoyal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, NuvioBorder.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter",
                    tint = NuvioTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StreamXFeaturedCard(
    item: MediaItem,
    onPlayClick: (MediaItem) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    pageCount: Int = 4,
    currentPage: Int = 0
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassBorderLight, RoundedCornerShape(24.dp))
                .clickable { onItemClick(item) }
        ) {
            // Full backdrop image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.backdropUrl.ifBlank { item.posterUrl })
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Cinematic gradient overlay from bottom to top
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.20f),
                            0.75f to Color.Black.copy(alpha = 0.70f),
                            1.0f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
            )

            // Content positioned at bottom left & right
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Genres / Year / Rating tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "★", fontSize = 11.sp, color = StarRatingGold)
                            Text(
                                text = "%.1f".format(item.rating),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = "${item.releaseYear} • ${item.genres.take(2).joinToString(", ").ifBlank { item.primaryGenre }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Play Button
                    Button(
                        onClick = { onPlayClick(item) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Watch",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Watch",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Pager indicator dots below banner
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isActive = index == currentPage
                Box(
                    modifier = Modifier
                        .height(5.dp)
                        .width(if (isActive) 18.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isActive) NuvioTextPrimary else PagerDotInactive)
                )
            }
        }
    }
}

@Composable
fun StreamXSectionHeader(
    title: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = NuvioTextPrimary
        )
        Text(
            text = "See All >",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryAccentRoyal,
            modifier = Modifier.clickable { onSeeAllClick() }
        )
    }
}

@Composable
fun StreamXTrendingCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaPosterCard(
        item = item,
        onClick = onClick,
        modifier = modifier,
        width = 130,
        height = 190
    )
}

data class GenreCard(
    val id: String,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradient: List<Color>
)

@Composable
fun StreamXGenreCard(
    genre: GenreCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NuvioSecondarySurface)
            .border(1.dp, GlassBorderLight, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = genre.icon,
                contentDescription = genre.name,
                tint = PrimaryAccentRoyal,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = genre.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTextPrimary
            )
        }
    }
}

@Composable
fun StreamXGenreGrid(
    genres: List<GenreCard>,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1 (first 3 genres)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            genres.take(3).forEach { genre ->
                Box(modifier = Modifier.weight(1f)) {
                    StreamXGenreCard(
                        genre = genre,
                        onClick = { onGenreClick(genre.id) }
                    )
                }
            }
        }

        // Row 2 (next 3 genres)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            genres.drop(3).take(3).forEach { genre ->
                Box(modifier = Modifier.weight(1f)) {
                    StreamXGenreCard(
                        genre = genre,
                        onClick = { onGenreClick(genre.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun StreamXBottomBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleTabs = listOf(
        NavigationTab.HOME,
        NavigationTab.DISCOVER,
        NavigationTab.SEARCH,
        NavigationTab.WATCHLIST,
        NavigationTab.PROFILE
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xF2FFFFFF),
            shape = RoundedCornerShape(36.dp),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.95f),
                    1.0f to Color.Black.copy(alpha = 0.08f)
                )
            ),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleTabs.forEach { tab ->
                    val isSelected = tab == currentTab || 
                        (tab == NavigationTab.HOME && (currentTab == NavigationTab.MOVIES || currentTab == NavigationTab.SERIES)) ||
                        (tab == NavigationTab.WATCHLIST && currentTab == NavigationTab.LIBRARY)

                    val activeBgColor by animateColorAsState(
                        targetValue = if (isSelected) DarkActionPill else Color.Transparent,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.75f,
                            stiffness = 380f
                        ),
                        label = "bottomBarPillBg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else NuvioTextSecondary,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.75f,
                            stiffness = 380f
                        ),
                        label = "bottomBarIconTint"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.72f,
                            stiffness = 380f
                        ),
                        label = "bottomBarPillScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(activeBgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.iconFilled else tab.iconOutlined,
                            contentDescription = tab.label,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
