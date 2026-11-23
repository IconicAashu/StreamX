package com.streamx.app.verification

import com.streamx.app.domain.model.DeveloperInfo
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.UserProfile
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import com.streamx.app.presentation.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalProfileSystemAndDeveloperCreditsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==========================================
    // 1. USER PROFILE MODEL & INITIALS MONOGRAM TESTS
    // ==========================================

    @Test
    fun testDefaultUserProfileIsNotCreatedAndDoesNotDefaultToDeveloper() {
        val defaultProfile = UserProfile()
        assertFalse("New profile must not be marked created by default", defaultProfile.isCreated)
        assertEquals("New profile name must be empty by default", "", defaultProfile.displayName)
        assertNull("New profile avatar URI must be null by default", defaultProfile.avatarUri)
        assertNotEquals("Default profile must NEVER be pre-populated with developer's name", "Aashu Kumar", defaultProfile.displayName)
        assertEquals("Initials fallback for empty name must be 'U'", "U", defaultProfile.initials)
    }

    @Test
    fun testInitialsGenerationForSingleAndMultipleWords() {
        assertEquals("R", UserProfile(displayName = "Rahul").initials)
        assertEquals("RS", UserProfile(displayName = "Rahul Sharma").initials)
        assertEquals("AK", UserProfile(displayName = "Aashu Kumar").initials)
        assertEquals("VD", UserProfile(displayName = "Vikram Aditya Deshmukh").initials)
        assertEquals("P", UserProfile(displayName = "  Priya  ").initials)
        assertEquals("U", UserProfile(displayName = "   ").initials)
    }

    // ==========================================
    // 2. DEVELOPER CREDITS & IMMUTABILITY TESTS
    // ==========================================

    @Test
    fun testDeveloperCreditsExactAttributes() {
        assertEquals("Aashu Kumar", DeveloperInfo.NAME)
        assertEquals("Designed & Developed by", DeveloperInfo.ROLE)
        assertEquals("IconicAashu", DeveloperInfo.GITHUB_USERNAME)
        assertEquals("https://github.com/IconicAashu", DeveloperInfo.GITHUB_URL)
        assertEquals("aashu@hilmail.co", DeveloperInfo.FEEDBACK_EMAIL)

        assertTrue("GitHub URL must use HTTPS", DeveloperInfo.GITHUB_URL.startsWith("https://"))
        assertTrue("GitHub URL must point to user's handle", DeveloperInfo.GITHUB_URL.endsWith(DeveloperInfo.GITHUB_USERNAME))
        assertTrue("Email address must contain @", DeveloperInfo.FEEDBACK_EMAIL.contains("@"))
        assertTrue("Email address must end with domain", DeveloperInfo.FEEDBACK_EMAIL.endsWith(".co") || DeveloperInfo.FEEDBACK_EMAIL.endsWith(".com"))
    }

    // ==========================================
    // 3. PROFILE CREATION & VALIDATION TESTS
    // ==========================================

    @Test
    fun testProfileCreationFailsWithBlankOrEmptyName() = runTest {
        val fakeRepo = FakeUserPreferencesRepository()
        val fakeCatalog = FakeCatalogRepository()
        val viewModel = ProfileViewModel(fakeRepo, fakeCatalog)
        testScheduler.advanceUntilIdle()

        // 1. Attempt creation with empty name
        viewModel.onNameInputChange("")
        val resultEmpty = viewModel.createProfile()
        assertFalse("Creation must fail for empty name", resultEmpty)
        assertEquals("Please enter your name to create your profile.", viewModel.uiState.value.nameError)
        assertFalse(fakeRepo.userProfileFlow.value.isCreated)

        // 2. Attempt creation with whitespace-only name
        viewModel.onNameInputChange("   ")
        val resultWhitespace = viewModel.createProfile()
        assertFalse("Creation must fail for whitespace name", resultWhitespace)
        assertEquals("Please enter your name to create your profile.", viewModel.uiState.value.nameError)
        assertFalse(fakeRepo.userProfileFlow.value.isCreated)
    }

    @Test
    fun testProfileCreationSucceedsWithValidCustomName() = runTest {
        val fakeRepo = FakeUserPreferencesRepository()
        val fakeCatalog = FakeCatalogRepository()
        val viewModel = ProfileViewModel(fakeRepo, fakeCatalog)
        testScheduler.advanceUntilIdle()

        viewModel.onNameInputChange("Rahul")
        viewModel.onAvatarSelected("content://media/picker/avatar123")
        val success = viewModel.createProfile()

        assertTrue("Creation must succeed with valid name", success)
        testScheduler.advanceUntilIdle()

        val savedProfile = fakeRepo.userProfileFlow.value
        assertTrue("Profile must now be marked created", savedProfile.isCreated)
        assertEquals("Rahul", savedProfile.displayName)
        assertEquals("content://media/picker/avatar123", savedProfile.avatarUri)
        assertEquals("R", savedProfile.initials)
        assertEquals("Rahul", viewModel.uiState.value.userName)
        assertNull("Name error must be cleared", viewModel.uiState.value.nameError)
    }

    // ==========================================
    // 4. PROFILE EDITING & UPDATES
    // ==========================================

    @Test
    fun testProfileEditingFlow() = runTest {
        val fakeRepo = FakeUserPreferencesRepository()
        fakeRepo.saveUserProfile("Rahul", null)

        val fakeCatalog = FakeCatalogRepository()
        val viewModel = ProfileViewModel(fakeRepo, fakeCatalog)
        testScheduler.advanceUntilIdle()

        assertEquals("Rahul", viewModel.uiState.value.userProfile.displayName)

        // Open edit dialog
        viewModel.openEditProfileDialog()
        assertTrue(viewModel.uiState.value.isEditDialogOpen)
        assertEquals("Rahul", viewModel.uiState.value.inputName)

        // Change display name and add avatar
        viewModel.onNameInputChange("Rahul Verma")
        viewModel.onAvatarSelected("content://media/picker/avatar456")

        val saveSuccess = viewModel.saveProfileChanges()
        assertTrue(saveSuccess)
        testScheduler.advanceUntilIdle()

        assertFalse("Dialog must close after saving", viewModel.uiState.value.isEditDialogOpen)
        assertEquals("Rahul Verma", fakeRepo.userProfileFlow.value.displayName)
        assertEquals("content://media/picker/avatar456", fakeRepo.userProfileFlow.value.avatarUri)
        assertEquals("RV", fakeRepo.userProfileFlow.value.initials)
    }

    @Test
    fun testResetProfileToUncreated() = runTest {
        val fakeRepo = FakeUserPreferencesRepository()
        fakeRepo.saveUserProfile("Alex", "avatar.jpg")

        val fakeCatalog = FakeCatalogRepository()
        val viewModel = ProfileViewModel(fakeRepo, fakeCatalog)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.userProfile.isCreated)

        viewModel.resetProfileToUncreated()
        testScheduler.advanceUntilIdle()

        assertFalse("Profile must revert to uncreated", viewModel.uiState.value.userProfile.isCreated)
        assertEquals("", viewModel.uiState.value.userProfile.displayName)
        assertNull(viewModel.uiState.value.userProfile.avatarUri)
    }

    // ==========================================
    // FAKE TEST REPOSITORIES
    // ==========================================

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val _profile = MutableStateFlow(UserProfile())
        override val userProfileFlow: StateFlow<UserProfile> = _profile.asStateFlow()

        override suspend fun saveUserProfile(name: String, avatarUri: String?) {
            _profile.value = UserProfile(
                displayName = name.trim(),
                avatarUri = avatarUri,
                isCreated = true
            )
        }

        override suspend fun resetUserProfile() {
            _profile.value = UserProfile()
        }

        override val isOnboardingCompletedFlow: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}

        override val watchlistIdsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val watchlistFlow: StateFlow<List<MediaItem>> = MutableStateFlow(emptyList())
        override suspend fun toggleWatchlist(mediaItem: MediaItem): Boolean = false
        override suspend fun isSaved(mediaId: String): Boolean = false

        override val playbackPositionsFlow: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
        override suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {}
        override suspend fun getPlaybackPosition(mediaId: String): Long = 0L

        override val recentSearchesFlow: Flow<List<String>> = flowOf(emptyList())
        override suspend fun addSearchQuery(query: String) {}
        override suspend fun removeSearchQuery(query: String) {}
        override suspend fun clearSearchHistory() {}

        override val preferredAudioLanguageFlow: StateFlow<String> = MutableStateFlow("English")
        override suspend fun setPreferredAudioLanguage(language: String) {}

        override val subtitlesEnabledFlow: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun setSubtitlesEnabled(enabled: Boolean) {}

        override val defaultQualityFlow: StateFlow<VideoQuality> = MutableStateFlow(VideoQuality.AUTO)
        override suspend fun setDefaultQuality(quality: VideoQuality) {}

        override val recentSearchesEnabledFlow: Flow<Boolean> = flowOf(true)
        override suspend fun setRecentSearchesEnabled(enabled: Boolean) {}

        override val autoplayEnabledFlow: Flow<Boolean> = flowOf(true)
        override suspend fun setAutoplayEnabled(enabled: Boolean) {}

        override val tmdbEnrichmentEnabledFlow: Flow<Boolean> = flowOf(true)
        override suspend fun setTmdbEnrichmentEnabled(enabled: Boolean) {}

        override val jikanMetadataEnabledFlow: Flow<Boolean> = flowOf(true)
        override suspend fun setJikanMetadataEnabled(enabled: Boolean) {}

        override val externalRatingsEnabledFlow: Flow<Boolean> = flowOf(true)
        override suspend fun setExternalRatingsEnabled(enabled: Boolean) {}

        override val themeModeFlow: StateFlow<String> = MutableStateFlow("SYSTEM")
        override suspend fun setThemeMode(mode: String) {}

        override val notificationsEnabledFlow: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun setNotificationsEnabled(enabled: Boolean) {}

        override val preferredSubtitleLanguageFlow: StateFlow<String> = MutableStateFlow("English")
        override suspend fun setPreferredSubtitleLanguage(lang: String) {}

        override val appLanguageFlow: StateFlow<String> = MutableStateFlow("English (US)")
        override suspend fun setAppLanguage(lang: String) {}

        override val autoUpdateCheckEnabledFlow: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {}

        override val lastUpdateCheckTimeFlow: StateFlow<Long> = MutableStateFlow(0L)
        override suspend fun setLastUpdateCheckTime(timestampMs: Long) {}
    }

    private class FakeCatalogRepository : CatalogRepository {
        override suspend fun getHeroItems(): List<MediaItem> = emptyList()
        override suspend fun getItemsByCategory(category: com.streamx.app.domain.model.MediaCategory): List<MediaItem> = emptyList()
        override suspend fun getForYouItems(): List<MediaItem> = emptyList()
        override suspend fun getBollywoodAndSouthIndian(): List<MediaItem> = emptyList()
        override suspend fun getContinueWatching(): List<MediaItem> = emptyList()
        override suspend fun getAwardWinners(): List<MediaItem> = emptyList()
        override suspend fun getBingeWorthySeries(): List<MediaItem> = emptyList()
        override suspend fun getMediaDetails(id: String): MediaItem? = null
        override suspend fun getSimilarTitles(id: String): List<MediaItem> = emptyList()
        override suspend fun search(query: String, selectedGenre: String?): List<MediaItem> = emptyList()
        override suspend fun discoverByGenre(genre: String): List<MediaItem> = emptyList()
        override suspend fun getPopularGenres(): List<String> = emptyList()
        override suspend fun getAllPostersForWelcome(): List<String> = emptyList()
    }
}
