package com.streamx.app.presentation.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.streamx.app.domain.model.MediaCategory
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.presentation.components.NavigationTab
import com.streamx.app.presentation.components.StreamXBottomBar
import com.streamx.app.presentation.explore.ExploreScreen
import com.streamx.app.presentation.explore.ExploreViewModel
import com.streamx.app.presentation.home.HomeScreen
import com.streamx.app.presentation.home.HomeViewModel
import com.streamx.app.presentation.profile.ProfileScreen
import com.streamx.app.presentation.profile.ProfileViewModel
import com.streamx.app.presentation.search.SearchScreen
import com.streamx.app.presentation.search.SearchViewModel
import com.streamx.app.presentation.theme.rememberStreamXGradientBrush
import com.streamx.app.presentation.watchlist.WatchlistScreen
import com.streamx.app.presentation.watchlist.WatchlistViewModel

@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    watchlistViewModel: WatchlistViewModel,
    profileViewModel: ProfileViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit = onMediaClick,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    exploreViewModel: ExploreViewModel? = null,
    initialTab: NavigationTab = NavigationTab.HOME
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val streamxGradientBrush = rememberStreamXGradientBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(streamxGradientBrush)
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = 380f)) + slideInHorizontally(
                    animationSpec = spring(stiffness = 380f), initialOffsetX = { it / 12 }
                )).togetherWith(fadeOut(animationSpec = spring(stiffness = 500f)) + slideOutHorizontally(
                    animationSpec = spring(stiffness = 500f), targetOffsetX = { -it / 24 }
                ))
            },
            label = "tabContentTransition"
        ) { targetTab ->
            when (targetTab) {
                NavigationTab.HOME, NavigationTab.MOVIES, NavigationTab.SERIES -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onMediaClick = onMediaClick,
                        onPlayClick = onPlayClick,
                        onSearchClick = { selectedTab = NavigationTab.SEARCH },
                        onSeeAllClick = { 
                            if (exploreViewModel != null) {
                                selectedTab = NavigationTab.DISCOVER
                            } else {
                                onNavigateToDiscover()
                            }
                        },
                        onGenreClick = {
                            if (exploreViewModel != null) {
                                selectedTab = NavigationTab.DISCOVER
                            } else {
                                onNavigateToDiscover()
                            }
                        }
                    )
                }
                NavigationTab.DISCOVER -> {
                    if (exploreViewModel != null) {
                        ExploreScreen(
                            viewModel = exploreViewModel,
                            onMediaClick = onMediaClick
                        )
                    } else {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onMediaClick = onMediaClick,
                            onPlayClick = onPlayClick,
                            onSearchClick = { selectedTab = NavigationTab.SEARCH },
                            onSeeAllClick = { onNavigateToDiscover() },
                            onGenreClick = { onNavigateToDiscover() }
                        )
                    }
                }
                NavigationTab.SEARCH -> {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onMediaClick = onMediaClick,
                        onOpenDiscoverHub = {
                            if (exploreViewModel != null) {
                                selectedTab = NavigationTab.DISCOVER
                            } else {
                                onNavigateToDiscover()
                            }
                        }
                    )
                }
                NavigationTab.WATCHLIST, NavigationTab.LIBRARY -> {
                    WatchlistScreen(
                        viewModel = watchlistViewModel,
                        onMediaClick = onMediaClick
                    )
                }
                NavigationTab.PROFILE -> {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onMediaClick = onMediaClick,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToWatchlist = { selectedTab = NavigationTab.WATCHLIST }
                    )
                }
            }
        }

        // STREAMX Floating pill bottom navigation bar (Home, Discover, Search, Watchlist, Profile)
        StreamXBottomBar(
            currentTab = selectedTab,
            onTabSelected = { newTab ->
                selectedTab = newTab
                if (newTab == NavigationTab.MOVIES) {
                    homeViewModel.selectCategory(MediaCategory.MOVIES)
                } else if (newTab == NavigationTab.SERIES) {
                    homeViewModel.selectCategory(MediaCategory.SERIES)
                } else if (newTab == NavigationTab.HOME) {
                    homeViewModel.selectCategory(MediaCategory.TRENDING)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
