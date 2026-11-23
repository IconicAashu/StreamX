package com.streamx.app.navigation

import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamx.app.R
import com.streamx.app.StreamXApp
import com.streamx.app.presentation.about.AboutScreen
import com.streamx.app.presentation.about.AboutViewModel
import com.streamx.app.presentation.details.DetailsScreen
import com.streamx.app.presentation.details.DetailsViewModel
import com.streamx.app.presentation.explore.ExploreScreen
import com.streamx.app.presentation.explore.ExploreViewModel
import com.streamx.app.presentation.home.HomeViewModel
import com.streamx.app.presentation.jellyfin.JellyfinConnectScreen
import com.streamx.app.presentation.jellyfin.JellyfinLibraryScreen
import com.streamx.app.presentation.jellyfin.JellyfinLoginScreen
import com.streamx.app.presentation.jellyfin.JellyfinMediaDetailScreen
import com.streamx.app.presentation.jellyfin.JellyfinViewModel
import com.streamx.app.presentation.main.MainScreen
import com.streamx.app.presentation.p2p.P2PDashboardScreen
import com.streamx.app.presentation.p2p.P2PViewModel
import com.streamx.app.presentation.player.VideoPlayerScreen
import com.streamx.app.presentation.player.VideoPlayerViewModel
import com.streamx.app.presentation.profile.ProfileViewModel
import com.streamx.app.presentation.search.SearchViewModel
import com.streamx.app.presentation.settings.SettingsScreen
import com.streamx.app.presentation.settings.SettingsViewModel
import com.streamx.app.presentation.settings.addons.AddonsScreen
import com.streamx.app.presentation.settings.addons.AddonsViewModel
import com.streamx.app.presentation.settings.content_discovery.ContentDiscoveryScreen
import com.streamx.app.presentation.settings.content_discovery.ContentDiscoveryViewModel
import com.streamx.app.presentation.settings.integrations.IntegrationsScreen
import com.streamx.app.presentation.settings.integrations.IntegrationsViewModel
import com.streamx.app.presentation.sources.SourceSelectionScreen
import com.streamx.app.presentation.sources.SourceSelectionViewModel
import com.streamx.app.presentation.theme.*
import com.streamx.app.presentation.watchlist.WatchlistViewModel
import com.streamx.app.presentation.welcome.WelcomeScreen
import com.streamx.app.presentation.welcome.WelcomeViewModel

@Composable
fun StreamXNavHost(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as StreamXApp).container

    // Collect onboarding persistence state from Jetpack DataStore
    val isOnboardingCompleted by appContainer.userPreferencesRepository
        .isOnboardingCompletedFlow
        .collectAsState(initial = null)

    // Splash screen while reading preference from DataStore
    if (isOnboardingCompleted == null) {
        val splashGradient = rememberStreamXGradientBrush()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(splashGradient),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = PrimaryAccentRoyal)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.5.dp, PrimaryAccentRoyal, RoundedCornerShape(24.dp))
                        .background(SurfacePurpleSlate),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.streamx_logo),
                        contentDescription = "STREAMX Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = "STREAMX",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    letterSpacing = 2.sp
                )

                CircularProgressIndicator(
                    color = PrimaryAccentRoyal,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
        return
    }

    val startDestination = if (isOnboardingCompleted == true) {
        Screen.Main.route
    } else {
        Screen.Welcome.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome / Onboarding Screen
        composable(
            route = Screen.Welcome.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(400)
                ) + fadeOut(tween(400))
            }
        ) {
            val welcomeViewModel = remember {
                WelcomeViewModel(
                    catalogRepository = appContainer.catalogRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository
                )
            }
            WelcomeScreen(
                viewModel = welcomeViewModel,
                onGetStartedClick = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        // Main Screen (Hosts Floating Pill Bottom Navigation: Home, Search, Library, Profile)
        composable(
            route = Screen.Main.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(400)
                ) + fadeIn(tween(400))
            },
            exitTransition = { fadeOut(tween(300)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(300)) }
        ) {
            val homeViewModel = remember {
                HomeViewModel(
                    catalogRepository = appContainer.catalogRepository,
                    manifestAddonRepository = appContainer.manifestAddonRepository
                )
            }
            val searchViewModel = remember {
                SearchViewModel(
                    catalogRepository = appContainer.catalogRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    channelFilterRepository = appContainer.channelFilterRepository
                )
            }
            val watchlistViewModel = remember {
                WatchlistViewModel(appContainer.userPreferencesRepository)
            }
            val profileViewModel = remember {
                ProfileViewModel(
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    catalogRepository = appContainer.catalogRepository
                )
            }
            val exploreViewModel = remember {
                ExploreViewModel(
                    catalogRepository = appContainer.catalogRepository,
                    channelFilterRepository = appContainer.channelFilterRepository
                )
            }
            MainScreen(
                homeViewModel = homeViewModel,
                searchViewModel = searchViewModel,
                watchlistViewModel = watchlistViewModel,
                profileViewModel = profileViewModel,
                exploreViewModel = exploreViewModel,
                onMediaClick = { mediaItem ->
                    navController.navigate(Screen.Details.createRoute(mediaItem.id))
                },
                onPlayClick = { mediaItem ->
                    navController.navigate(Screen.SourceSelection.createRoute(mediaItem.id))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToDiscover = {
                    navController.navigate(Screen.Discover.route)
                }
            )
        }

        // Discover Hub (Channel & Dynamic Filters: Genre, Country, Year, Dubs, Rating)
        composable(
            route = Screen.Discover.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) {
            val exploreViewModel = remember {
                ExploreViewModel(
                    catalogRepository = appContainer.catalogRepository,
                    channelFilterRepository = appContainer.channelFilterRepository
                )
            }
            ExploreScreen(
                viewModel = exploreViewModel,
                onMediaClick = { mediaItem ->
                    navController.navigate(Screen.Details.createRoute(mediaItem.id))
                }
            )
        }

        // Details Screen
        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = {
                fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaId = Uri.decode(rawId) ?: rawId
            val detailsViewModel: DetailsViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "details_$mediaId",
                factory = DetailsViewModel.Factory(
                    mediaId = mediaId,
                    catalogRepository = appContainer.catalogRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    manifestAddonRepository = appContainer.manifestAddonRepository,
                    playbackRepository = appContainer.playbackRepository
                )
            )

            DetailsScreen(
                viewModel = detailsViewModel,
                onBackClick = { navController.popBackStack() },
                onOpenSourceSelection = { item, targetId, epTitle ->
                    navController.navigate(
                        Screen.SourceSelection.createRoute(
                            mediaId = item.id,
                            targetId = targetId ?: item.id,
                            episodeTitle = epTitle
                        )
                    )
                },
                onSimilarMediaClick = { item ->
                    navController.navigate(Screen.Details.createRoute(item.id))
                }
            )
        }

        // Dedicated Nuvio-style Source Selection Screen
        composable(
            route = Screen.SourceSelection.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("targetId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episodeTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val rawMediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaId = Uri.decode(rawMediaId) ?: rawMediaId
            val rawTargetId = backStackEntry.arguments?.getString("targetId")
            val targetId = rawTargetId?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() } ?: mediaId
            val rawEpisodeTitle = backStackEntry.arguments?.getString("episodeTitle")
            val episodeTitle = rawEpisodeTitle?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }

            val sourceSelectionViewModel: SourceSelectionViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "source_selection_${mediaId}_${targetId}",
                factory = SourceSelectionViewModel.Factory(
                    mediaId = mediaId,
                    targetId = targetId,
                    initialEpisodeTitle = episodeTitle,
                    catalogRepository = appContainer.catalogRepository,
                    playbackRepository = appContainer.playbackRepository,
                    manifestAddonRepository = appContainer.manifestAddonRepository
                )
            )

            SourceSelectionScreen(
                viewModel = sourceSelectionViewModel,
                onBackClick = { navController.popBackStack() },
                onOpenAddonsSettings = { navController.navigate(Screen.Addons.route) },
                onPlaySource = { source ->
                    appContainer.playbackRepository.setCurrentSelectedSource(source)
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaId = source.mediaId,
                            streamUrl = source.streamUrl,
                            title = source.title,
                            provider = source.providerName
                        )
                    )
                }
            )
        }

        // Media3 Video Player Screen
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("streamUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("provider") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            enterTransition = {
                scaleIn(initialScale = 0.92f, animationSpec = tween(350)) + fadeIn(tween(350))
            },
            exitTransition = {
                scaleOut(targetScale = 0.92f, animationSpec = tween(350)) + fadeOut(tween(350))
            },
            popExitTransition = {
                scaleOut(targetScale = 0.92f, animationSpec = tween(350)) + fadeOut(tween(350))
            }
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaId = Uri.decode(rawId) ?: rawId
            val rawStreamUrl = backStackEntry.arguments?.getString("streamUrl")
            val initialStreamUrl = rawStreamUrl?.takeIf { it.isNotBlank() }
            val rawTitle = backStackEntry.arguments?.getString("title")
            val initialTitle = rawTitle?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
            val rawProvider = backStackEntry.arguments?.getString("provider")
            val initialProvider = rawProvider?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
            val playerViewModel: VideoPlayerViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "player_${mediaId}_${initialStreamUrl.hashCode()}",
                factory = VideoPlayerViewModel.Factory(
                    mediaId = mediaId,
                    playbackRepository = appContainer.playbackRepository,
                    context = context.applicationContext,
                    initialStreamUrl = initialStreamUrl,
                    initialTitle = initialTitle,
                    initialProvider = initialProvider
                )
            )

            VideoPlayerScreen(
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Settings Screen
        composable(
            route = Screen.Settings.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) {
            val settingsViewModel = remember {
                SettingsViewModel(appContainer.userPreferencesRepository)
            }
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToAddons = {
                    navController.navigate(Screen.Addons.route)
                },
                onNavigateToJellyfin = {
                    navController.navigate(Screen.JellyfinConnect.route)
                },
                onNavigateToP2P = {
                    navController.navigate(Screen.P2PDashboard.route)
                },
                onNavigateToContentDiscovery = {
                    navController.navigate(Screen.ContentDiscovery.route)
                },
                onNavigateToIntegrations = {
                    navController.navigate(Screen.Integrations.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                },
                onNavigateToTestPlayer = { streamUrl, title ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaId = "debug_sample_video",
                            streamUrl = streamUrl,
                            title = title
                        )
                    )
                }
            )
        }

        // Dedicated About Screen with APK Update System
        composable(
            route = Screen.About.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) {
            val aboutViewModel = remember {
                AboutViewModel(appContainer.appUpdateRepository)
            }
            AboutScreen(
                viewModel = aboutViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Add-ons Screen (Stremio Protocol Engine)
        composable(
            route = Screen.Addons.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) {
            val addonsViewModel = remember {
                AddonsViewModel(
                    manifestAddonRepository = appContainer.manifestAddonRepository
                )
            }
            AddonsScreen(
                viewModel = addonsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Content & Discovery Screen
        composable(
            route = Screen.ContentDiscovery.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) {
            val contentDiscoveryViewModel = remember {
                ContentDiscoveryViewModel(
                    manifestAddonRepository = appContainer.manifestAddonRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository
                )
            }
            ContentDiscoveryScreen(
                viewModel = contentDiscoveryViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToAddons = {
                    navController.navigate(Screen.Addons.route)
                },
                onNavigateToJellyfin = {
                    navController.navigate(Screen.JellyfinConnect.route)
                }
            )
        }

        // Integrations Screen
        composable(
            route = Screen.Integrations.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) {
            val integrationsViewModel = remember {
                IntegrationsViewModel(appContainer.userPreferencesRepository)
            }
            IntegrationsScreen(
                viewModel = integrationsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Jellyfin Connect Screen
        composable(
            route = Screen.JellyfinConnect.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val jellyfinViewModel: JellyfinViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "jellyfin_connect",
                factory = JellyfinViewModel.Factory(appContainer.jellyfinRepository)
            )
            JellyfinConnectScreen(
                viewModel = jellyfinViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToLogin = { serverUrl ->
                    navController.navigate(Screen.JellyfinLogin.createRoute(serverUrl))
                },
                onNavigateToLibrary = {
                    navController.navigate(Screen.JellyfinLibrary.route)
                }
            )
        }

        // Jellyfin Login Screen
        composable(
            route = Screen.JellyfinLogin.route,
            arguments = listOf(
                navArgument("serverUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val rawUrl = backStackEntry.arguments?.getString("serverUrl") ?: ""
            val serverUrl = Uri.decode(rawUrl) ?: rawUrl
            val jellyfinViewModel: JellyfinViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "jellyfin_login_$serverUrl",
                factory = JellyfinViewModel.Factory(appContainer.jellyfinRepository)
            )
            JellyfinLoginScreen(
                serverUrl = serverUrl,
                viewModel = jellyfinViewModel,
                onBackClick = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Screen.JellyfinLibrary.route) {
                        popUpTo(Screen.JellyfinConnect.route) { inclusive = true }
                    }
                }
            )
        }

        // Jellyfin Library Browsing Screen
        composable(
            route = Screen.JellyfinLibrary.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val jellyfinViewModel: JellyfinViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "jellyfin_library",
                factory = JellyfinViewModel.Factory(appContainer.jellyfinRepository)
            )
            JellyfinLibraryScreen(
                viewModel = jellyfinViewModel,
                onBackClick = { navController.popBackStack() },
                onItemClick = { itemId ->
                    navController.navigate(Screen.JellyfinDetail.createRoute(itemId))
                },
                onPlayClick = { mediaId ->
                    navController.navigate(Screen.Player.createRoute(mediaId))
                },
                onDisconnect = {
                    navController.popBackStack()
                }
            )
        }

        // Jellyfin Media Detail Screen
        composable(
            route = Screen.JellyfinDetail.route,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("itemId") ?: ""
            val itemId = Uri.decode(rawId) ?: rawId
            val jellyfinViewModel: JellyfinViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "jellyfin_detail_$itemId",
                factory = JellyfinViewModel.Factory(appContainer.jellyfinRepository)
            )
            JellyfinMediaDetailScreen(
                itemId = itemId,
                viewModel = jellyfinViewModel,
                onBackClick = { navController.popBackStack() },
                onPlayClick = { mediaId ->
                    navController.navigate(Screen.Player.createRoute(mediaId))
                }
            )
        }

        // P2P BitTorrent Streaming Dashboard
        composable(
            route = Screen.P2PDashboard.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(380)
                ) + fadeIn(tween(380))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(380)
                ) + fadeOut(tween(380))
            }
        ) { backStackEntry ->
            val p2pViewModel: P2PViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "p2p_dashboard",
                factory = P2PViewModel.Factory(appContainer.p2pSessionManager)
            )
            P2PDashboardScreen(
                viewModel = p2pViewModel,
                onBackClick = { navController.popBackStack() },
                onPlayClick = { mediaId ->
                    navController.navigate(Screen.Player.createRoute(mediaId))
                }
            )
        }
    }
}
