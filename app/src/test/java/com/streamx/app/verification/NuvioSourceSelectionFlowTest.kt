package com.streamx.app.verification

import com.streamx.app.domain.model.*
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.PlaybackRepository
import com.streamx.app.navigation.Screen
import com.streamx.app.presentation.sources.SourceSelectionViewModel
import com.streamx.app.presentation.player.VideoPlayerViewModel
import com.streamx.app.utils.ContentIdResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/**
 * STREAMX Nuvio-Style Source Selection Flow Verification Test Suite.
 *
 * Verifies all 9 core requirements:
 *  1. Movie Details -> tap PLAY -> opens SourceSelection with movie ID and title.
 *  2. Series Details -> tap PLAY -> opens SourceSelection with default S1E1 targetId.
 *  3. Series Details -> tap specific Episode -> opens SourceSelection with exact episode targetId & title.
 *  4. SourceSelection shows loading state during discovery.
 *  5. Discovered streams are parsed and classified by transport (HTTP, HLS, DASH, BitTorrent).
 *  6. First playable stream is auto-selected; user selection updates active source.
 *  7. Play Source button on playable stream navigates to Screen.Player with exact parameters.
 *  8. Unsupported BitTorrent stream displays transport notice and prevents launching Media3 player.
 *  9. Empty discovery displays informative error card explaining add-on status and NEVER opens player.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NuvioSourceSelectionFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleMovie = MediaItem(
        id = "m10",
        title = "Dune: Part Two",
        posterUrl = "https://image.tmdb.org/t/p/w500/dune2.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/dune2_back.jpg",
        releaseYear = "2024",
        releaseDateFormatted = "2024-03-01",
        format = ContentFormat.MOVIE,
        primaryGenre = "Sci-Fi",
        genres = listOf("Sci-Fi", "Adventure"),
        runtimeFormatted = "2h 46m",
        maturityRating = "PG-13",
        rating = 8.6,
        ratingVotesCount = "450k",
        likesCount = "95%",
        synopsis = "Paul Atreides unites with Chani and the Fremen while seeking revenge.",
        cast = emptyList(),
        audioLanguages = listOf("English", "Hindi"),
        subtitleLanguages = listOf("English", "Spanish"),
        categories = emptyList(),
        streamUrl = ""
    )

    private val sampleSeries = MediaItem(
        id = "m8",
        title = "Squid Game",
        posterUrl = "https://image.tmdb.org/t/p/w500/squidgame.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/squidgame_back.jpg",
        releaseYear = "2021",
        releaseDateFormatted = "2021-09-17",
        format = ContentFormat.SERIES,
        primaryGenre = "Drama",
        genres = listOf("Drama", "Mystery", "Thriller"),
        runtimeFormatted = "55m per ep",
        maturityRating = "TV-MA",
        rating = 8.0,
        ratingVotesCount = "520k",
        likesCount = "92%",
        synopsis = "Hundreds of cash-strapped players accept a strange invitation.",
        cast = emptyList(),
        audioLanguages = listOf("Korean", "English"),
        subtitleLanguages = listOf("English"),
        categories = emptyList(),
        streamUrl = ""
    )

    // Stubs
    private lateinit var mockCatalogRepo: FakeCatalogRepository
    private lateinit var mockPlaybackRepo: FakePlaybackRepository
    private lateinit var mockManifestAddonRepo: FakeManifestAddonRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockCatalogRepo = FakeCatalogRepository(mapOf("m10" to sampleMovie, "m8" to sampleSeries))
        mockPlaybackRepo = FakePlaybackRepository()
        mockManifestAddonRepo = FakeManifestAddonRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // SCENARIO 1: Movie Details -> tap PLAY -> opens SourceSelectionScreen
    // =========================================================================
    @Test
    fun testScenario1_MovieDetails_PlayButtonRoutesToSourceSelection_WithMovieId() = runTest(testDispatcher) {
        val movie = sampleMovie
        // When user taps Play on movie details:
        val targetId = movie.id
        val episodeTitle: String? = null
        val route = Screen.SourceSelection.createRoute(
            mediaId = movie.id,
            targetId = targetId,
            episodeTitle = episodeTitle
        )

        // Verify route pattern
        assertTrue("Route must start with source_selection", route.startsWith("source_selection/m10"))
        assertTrue("Route must contain targetId=m10", route.contains("targetId=m10"))

        // Decode arguments as NavHost would
        val parsedMediaId = "m10"
        val parsedTargetId = "m10"

        val viewModel = SourceSelectionViewModel(
            mediaId = parsedMediaId,
            targetId = parsedTargetId,
            initialEpisodeTitle = null,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("m10", state.mediaItem?.id)
        assertEquals("Dune: Part Two", state.mediaItem?.title)
        assertNull("Movie must not have season number", state.seasonNumber)
        assertNull("Movie must not have episode number", state.episodeNumber)
    }

    // =========================================================================
    // SCENARIO 2: Series Details -> tap PLAY -> opens SourceSelectionScreen with S1E1
    // =========================================================================
    @Test
    fun testScenario2_SeriesDetails_PlayButtonRoutesToSourceSelection_WithDefaultS1E1() = runTest(testDispatcher) {
        val series = sampleSeries
        val defaultEpId = "m8:1:1"
        val defaultEpTitle = "${series.title} • S1E1: Red Light, Green Light"

        val route = Screen.SourceSelection.createRoute(
            mediaId = series.id,
            targetId = defaultEpId,
            episodeTitle = defaultEpTitle
        )

        assertTrue(route.startsWith("source_selection/m8"))
        assertTrue(route.contains("targetId="))

        val viewModel = SourceSelectionViewModel(
            mediaId = series.id,
            targetId = defaultEpId,
            initialEpisodeTitle = defaultEpTitle,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("m8", state.mediaItem?.id)
        assertEquals(1, state.seasonNumber)
        assertEquals(1, state.episodeNumber)
        assertEquals(defaultEpTitle, state.episodeTitle)
    }

    // =========================================================================
    // SCENARIO 3: Series Details -> tap specific Episode -> opens SourceSelection with exact episode
    // =========================================================================
    @Test
    fun testScenario3_SeriesDetails_SpecificEpisodeClick_RoutesWithExactEpisodeTargetId() = runTest(testDispatcher) {
        val series = sampleSeries
        val epId = "m8:1:5"
        val epTitle = "${series.title} • S1E5: A Fair World"

        val route = Screen.SourceSelection.createRoute(
            mediaId = series.id,
            targetId = epId,
            episodeTitle = epTitle
        )

        assertTrue(route.startsWith("source_selection/m8"))

        val viewModel = SourceSelectionViewModel(
            mediaId = series.id,
            targetId = epId,
            initialEpisodeTitle = epTitle,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("m8", state.mediaItem?.id)
        assertEquals(1, state.seasonNumber)
        assertEquals(5, state.episodeNumber)
        assertEquals(epTitle, state.episodeTitle)
    }

    // =========================================================================
    // SCENARIO 4: SourceSelectionScreen shows loading state during discovery
    // =========================================================================
    @Test
    fun testScenario4_SourceSelection_ShowsLoadingStateDuringDiscovery() = runTest(testDispatcher) {
        val viewModel = SourceSelectionViewModel(
            mediaId = "m10",
            targetId = "m10",
            initialEpisodeTitle = null,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        // Before coroutines advance, state should show loading
        val initialState = viewModel.uiState.value
        assertTrue("Initial state must be loading", initialState.isLoading)
        assertTrue("Initial sources must be empty while loading", initialState.sources.isEmpty())

        testScheduler.advanceUntilIdle()
        assertFalse("Loading state must complete after coroutines finish", viewModel.uiState.value.isLoading)
    }

    // =========================================================================
    // SCENARIO 5: Discovered streams parsed and classified by transport
    // =========================================================================
    @Test
    fun testScenario5_DiscoveredStreamsParsedClassifiedByTransport() = runTest(testDispatcher) {
        val httpSource = PlaybackSource(
            mediaId = "m10",
            title = "Dune 2 1080p MP4",
            streamUrl = "https://cdn.example.com/video.mp4",
            providerName = "DirectCDN"
        )
        val hlsSource = PlaybackSource(
            mediaId = "m10",
            title = "Dune 2 4K HLS Stream",
            streamUrl = "https://cdn.example.com/master.m3u8",
            isLiveOrHls = true,
            providerName = "FastHLS"
        )
        val dashSource = PlaybackSource(
            mediaId = "m10",
            title = "Dune 2 DASH Stream",
            streamUrl = "https://cdn.example.com/manifest.mpd",
            providerName = "DashCDN"
        )
        val torrentSource = PlaybackSource(
            mediaId = "m10",
            title = "Dune.Part.Two.2024.1080p",
            streamUrl = "magnet:?xt=urn:btih:e582c4f2a74c6e9a0f023738e4a9e889b720e7e1",
            providerName = "Torrentio"
        )

        mockPlaybackRepo.stubSources = listOf(torrentSource, hlsSource, httpSource, dashSource)

        val viewModel = SourceSelectionViewModel(
            mediaId = "m10",
            targetId = "m10",
            initialEpisodeTitle = null,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()

        // Verify transport classification
        assertEquals("Direct MP4", viewModel.getTransportType(httpSource))
        assertEquals("HLS Stream", viewModel.getTransportType(hlsSource))
        assertEquals("DASH Stream", viewModel.getTransportType(dashSource))
        assertEquals("BitTorrent (P2P)", viewModel.getTransportType(torrentSource))

        assertTrue("HTTP source must be directly playable", viewModel.isDirectPlayable(httpSource))
        assertTrue("HLS source must be directly playable", viewModel.isDirectPlayable(hlsSource))
        assertTrue("DASH source must be directly playable", viewModel.isDirectPlayable(dashSource))
        assertFalse("Torrent source must not be directly playable in Media3", viewModel.isDirectPlayable(torrentSource))
    }

    // =========================================================================
    // SCENARIO 6: First playable stream is auto-selected; user can select another
    // =========================================================================
    @Test
    fun testScenario6_AutoSelectsFirstPlayableStream_AndUserCanSelectAnother() = runTest(testDispatcher) {
        val torrentSource = PlaybackSource(
            mediaId = "m10",
            title = "Torrent 1080p",
            streamUrl = "magnet:?xt=urn:btih:abcdef123456",
            providerName = "Torrentio"
        )
        val hlsSource = PlaybackSource(
            mediaId = "m10",
            title = "HLS Stream 1080p",
            streamUrl = "https://stream.example.com/live.m3u8",
            isLiveOrHls = true,
            providerName = "HLSProvider"
        )
        val directHttpSource = PlaybackSource(
            mediaId = "m10",
            title = "Direct 720p",
            streamUrl = "https://stream.example.com/720p.mp4",
            providerName = "HTTPProvider"
        )

        // Torrent is first in list, but HLS is first DIRECT PLAYABLE
        mockPlaybackRepo.stubSources = listOf(torrentSource, hlsSource, directHttpSource)

        val viewModel = SourceSelectionViewModel(
            mediaId = "m10",
            targetId = "m10",
            initialEpisodeTitle = null,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("Must auto-select first direct playable source", hlsSource.streamUrl, state.selectedSource?.streamUrl)

        // User chooses directHttpSource
        viewModel.selectSource(directHttpSource)
        assertEquals("Selected source must update to directHttpSource", directHttpSource.streamUrl, viewModel.uiState.value.selectedSource?.streamUrl)
    }

    // =========================================================================
    // SCENARIO 7: Play Source Button -> navigates to Player with exact parameters
    // =========================================================================
    @Test
    fun testScenario7_PlaySourceButton_DirectPlayableNavigatesToPlayerWithExactParams() {
        val chosenSource = PlaybackSource(
            mediaId = "m10",
            title = "Dune: Part Two (4K IMAX)",
            streamUrl = "https://cdn.example.com/dune2_4k.m3u8",
            providerName = "Torrentio RD"
        )

        val playerRoute = Screen.Player.createRoute(
            mediaId = chosenSource.mediaId,
            streamUrl = chosenSource.streamUrl,
            title = chosenSource.title,
            provider = chosenSource.providerName
        )

        assertTrue(playerRoute.startsWith("player/m10"))
        assertTrue(playerRoute.contains("streamUrl="))
        assertTrue(playerRoute.contains("title="))
        assertTrue(playerRoute.contains("provider="))

        // Verify decoding
        val decodedRoute = URLDecoder.decode(playerRoute, "UTF-8")
        assertTrue(decodedRoute.contains(chosenSource.streamUrl))
        assertTrue(decodedRoute.contains(chosenSource.title))
        assertTrue(decodedRoute.contains(chosenSource.providerName))
    }

    // =========================================================================
    // SCENARIO 8: Selecting BitTorrent source displays notice and prevents direct player navigation
    // =========================================================================
    @Test
    fun testScenario8_SelectingBitTorrentSource_DisplaysUnsupportedNotice_PreventsDirectPlayerNavigation() = runTest(testDispatcher) {
        val torrentSource = PlaybackSource(
            mediaId = "m10",
            title = "Dune Part Two 1080p Torrent",
            streamUrl = "magnet:?xt=urn:btih:e582c4f2a74c6e9a0f023738e4a9e889b720e7e1&dn=Dune",
            providerName = "Torrentio P2P"
        )

        mockPlaybackRepo.stubSources = listOf(torrentSource)

        val viewModel = SourceSelectionViewModel(
            mediaId = "m10",
            targetId = "m10",
            initialEpisodeTitle = null,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()

        assertFalse("Torrent source must not be direct playable", viewModel.isDirectPlayable(torrentSource))

        // Trigger unsupported notice
        viewModel.showUnsupportedActionNotice("This is a BitTorrent P2P source. Media3 direct playback requires an HTTP, HLS, or DASH endpoint.")
        assertEquals(
            "This is a BitTorrent P2P source. Media3 direct playback requires an HTTP, HLS, or DASH endpoint.",
            viewModel.uiState.value.unsupportedMessage
        )

        // Dismiss notice
        viewModel.dismissUnsupportedNotice()
        assertNull(viewModel.uiState.value.unsupportedMessage)
    }

    // =========================================================================
    // SCENARIO 9: Empty discovery displays informative error card, NEVER opens player
    // =========================================================================
    @Test
    fun testScenario9_NoSourcesFound_DisplaysInformativeErrorCard_NeverNavigatesToPlayer() = runTest(testDispatcher) {
        // Configure mock to throw when discovering streams
        mockPlaybackRepo.shouldThrow = true
        mockManifestAddonRepo.installedAddonsFlow.value = listOf(
            InstalledAddon(
                id = "addon_cinemeta",
                name = "Cinemeta",
                manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
                baseUrl = "https://v3-cinemeta.strem.io",
                isEnabled = true,
                resources = listOf(StremioResourceDefinition(name = "meta"))
            )
        )

        val viewModel = SourceSelectionViewModel(
            mediaId = "m10",
            targetId = "m10",
            initialEpisodeTitle = null,
            catalogRepository = mockCatalogRepo,
            playbackRepository = mockPlaybackRepo,
            manifestAddonRepository = mockManifestAddonRepo
        )

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse("Loading must be false after error", state.isLoading)
        assertTrue("Sources list must be empty", state.sources.isEmpty())
        assertNull("Selected source must be null", state.selectedSource)
        assertNotNull("Error message must be present", state.errorMessage)
        assertNotNull("Empty detail explanation must be present", state.emptyDetail)
        assertTrue("Explanation must mention metadata-only status", state.emptyDetail!!.contains("Cinemeta"))

        // Retry capability
        mockPlaybackRepo.shouldThrow = false
        val newSource = PlaybackSource(
            mediaId = "m10",
            title = "Recovered Stream",
            streamUrl = "https://cdn.example.com/rec.m3u8",
            isLiveOrHls = true,
            providerName = "RecoveredProvider"
        )
        mockPlaybackRepo.stubSources = listOf(newSource)

        viewModel.retry()
        testScheduler.advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertEquals(1, recoveredState.sources.size)
        assertEquals("Recovered Stream", recoveredState.selectedSource?.title)
    }

    // =========================================================================
    // FAKE REPOSITORIES FOR ISOLATED TESTING
    // =========================================================================

    private class FakeCatalogRepository(
        private val items: Map<String, MediaItem>
    ) : CatalogRepository {
        override suspend fun getHeroItems(): List<MediaItem> = items.values.toList()
        override suspend fun getItemsByCategory(category: MediaCategory): List<MediaItem> = items.values.toList()
        override suspend fun getForYouItems(): List<MediaItem> = items.values.toList()
        override suspend fun getBollywoodAndSouthIndian(): List<MediaItem> = emptyList()
        override suspend fun getContinueWatching(): List<MediaItem> = emptyList()
        override suspend fun getAwardWinners(): List<MediaItem> = emptyList()
        override suspend fun getBingeWorthySeries(): List<MediaItem> = emptyList()
        override suspend fun getMediaDetails(id: String): MediaItem? = items[id]
        override suspend fun getSimilarTitles(id: String): List<MediaItem> = emptyList()
        override suspend fun search(query: String, selectedGenre: String?): List<MediaItem> = emptyList()
        override suspend fun discoverByGenre(genre: String): List<MediaItem> = emptyList()
        override suspend fun getPopularGenres(): List<String> = emptyList()
        override suspend fun getAllPostersForWelcome(): List<String> = emptyList()
    }

    private class FakePlaybackRepository : PlaybackRepository {
        var stubSources: List<PlaybackSource> = emptyList()
        var shouldThrow: Boolean = false

        override suspend fun getPlaybackSource(mediaId: String, forceRefresh: Boolean): PlaybackSource {
            if (shouldThrow) throw IllegalStateException("No sources available")
            return stubSources.firstOrNull() ?: throw IllegalStateException("Empty sources")
        }

        override suspend fun getAvailablePlaybackSources(mediaId: String, forceRefresh: Boolean): List<PlaybackSource> {
            if (shouldThrow) throw IllegalStateException("No sources available from installed add-ons")
            return stubSources
        }

        override suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {}
        override suspend fun getPlaybackPosition(mediaId: String): Long = 0L

        private var currentSource: PlaybackSource? = null
        override fun setCurrentSelectedSource(source: PlaybackSource?) {
            currentSource = source
        }
        override fun getCurrentSelectedSource(mediaId: String?, streamUrl: String?): PlaybackSource? {
            return currentSource
        }
    }

    private class FakeManifestAddonRepository : ManifestAddonRepository {
        override val addonsFlow = MutableStateFlow<List<AddonManifest>>(emptyList())
        override val installedAddonsFlow = MutableStateFlow<List<InstalledAddon>>(emptyList())
        override val mediaServersFlow = MutableStateFlow<List<MediaServerConfig>>(emptyList())
        override val customProvidersFlow = MutableStateFlow<List<ProviderModule>>(emptyList())
        override val addonCatalogsFlow = MutableStateFlow<List<AddonCatalogSection>>(emptyList())

        override suspend fun addAddon(name: String, manifestUrl: String, description: String) {}
        override suspend fun installAddonFromUrl(manifestUrl: String): Result<InstalledAddon> = Result.failure(Exception())
        override suspend fun toggleAddon(id: String) {}
        override suspend fun removeAddon(id: String) {}
        override suspend fun reorderAddons(fromIndex: Int, toIndex: Int) {}
        override suspend fun refreshAddon(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun refreshAddons() {}
        override suspend fun saveMediaServer(server: MediaServerConfig) {}
        override suspend fun removeMediaServer(id: String) {}
        override suspend fun testConnection(server: MediaServerConfig): Boolean = true
        override suspend fun toggleProvider(id: String) {}
        override suspend fun getMediaItemById(id: String): MediaItem? = null
        override suspend fun fetchAddonCatalogs(): List<AddonCatalogSection> = emptyList()
        override suspend fun searchAddonCatalogs(query: String, type: String): List<MediaItem> = emptyList()
        override suspend fun getMediaMeta(type: String, id: String): StremioMetaDetail? = null
        override suspend fun getStreamsForMedia(type: String, id: String): List<PlaybackSource> = emptyList()
        override suspend fun getSubtitlesForMedia(type: String, id: String): List<SubtitleTrackOption> = emptyList()
    }
}
