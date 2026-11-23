package com.streamx.app.data.repository

import com.streamx.app.data.local.MockCatalogDataSource
import com.streamx.app.data.local.UserPreferencesDataStore
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.UserProfile
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class UserPreferencesRepositoryImpl(
    private val dataStore: UserPreferencesDataStore
) : UserPreferencesRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override val isOnboardingCompletedFlow: Flow<Boolean> = dataStore.isOnboardingCompleted

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.setOnboardingCompleted(completed)
    }

    private val _watchlistIdsFlow = MutableStateFlow<Set<String>>(setOf("m1", "m2"))
    override val watchlistIdsFlow: StateFlow<Set<String>> = _watchlistIdsFlow.asStateFlow()

    private val _watchlistFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    override val watchlistFlow: StateFlow<List<MediaItem>> = _watchlistFlow.asStateFlow()

    private val _playbackPositionsFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val playbackPositionsFlow: StateFlow<Map<String, Long>> = _playbackPositionsFlow.asStateFlow()

    override val recentSearchesFlow: Flow<List<String>> = dataStore.recentSearches

    override suspend fun addSearchQuery(query: String) {
        dataStore.addSearchQuery(query)
    }

    override suspend fun removeSearchQuery(query: String) {
        dataStore.removeSearchQuery(query)
    }

    override suspend fun clearSearchHistory() {
        dataStore.clearSearchHistory()
    }

    private val _preferredAudioLanguageFlow = MutableStateFlow("Original")
    override val preferredAudioLanguageFlow: StateFlow<String> = _preferredAudioLanguageFlow.asStateFlow()

    private val _subtitlesEnabledFlow = MutableStateFlow(true)
    override val subtitlesEnabledFlow: StateFlow<Boolean> = _subtitlesEnabledFlow.asStateFlow()

    private val _defaultQualityFlow = MutableStateFlow(VideoQuality.QUALITY_1080P)
    override val defaultQualityFlow: StateFlow<VideoQuality> = _defaultQualityFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow("SYSTEM")
    override val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _notificationsEnabledFlow = MutableStateFlow(true)
    override val notificationsEnabledFlow: StateFlow<Boolean> = _notificationsEnabledFlow.asStateFlow()

    private val _preferredSubtitleLanguageFlow = MutableStateFlow("English")
    override val preferredSubtitleLanguageFlow: StateFlow<String> = _preferredSubtitleLanguageFlow.asStateFlow()

    private val _appLanguageFlow = MutableStateFlow("English (US)")
    override val appLanguageFlow: StateFlow<String> = _appLanguageFlow.asStateFlow()

    private val _userProfileFlow = MutableStateFlow(UserProfile())
    override val userProfileFlow: StateFlow<UserProfile> = _userProfileFlow.asStateFlow()

    private val _autoUpdateCheckEnabledFlow = MutableStateFlow(true)
    override val autoUpdateCheckEnabledFlow: StateFlow<Boolean> = _autoUpdateCheckEnabledFlow.asStateFlow()

    private val _lastUpdateCheckTimeFlow = MutableStateFlow(0L)
    override val lastUpdateCheckTimeFlow: StateFlow<Long> = _lastUpdateCheckTimeFlow.asStateFlow()

    override val recentSearchesEnabledFlow: Flow<Boolean> = dataStore.recentSearchesEnabled

    override suspend fun setRecentSearchesEnabled(enabled: Boolean) {
        dataStore.setRecentSearchesEnabled(enabled)
    }

    override val autoplayEnabledFlow: Flow<Boolean> = dataStore.autoplayEnabled

    override suspend fun setAutoplayEnabled(enabled: Boolean) {
        dataStore.setAutoplayEnabled(enabled)
    }

    override val tmdbEnrichmentEnabledFlow: Flow<Boolean> = dataStore.tmdbEnrichmentEnabled

    override suspend fun setTmdbEnrichmentEnabled(enabled: Boolean) {
        dataStore.setTmdbEnrichmentEnabled(enabled)
    }

    override val jikanMetadataEnabledFlow: Flow<Boolean> = dataStore.jikanMetadataEnabled

    override suspend fun setJikanMetadataEnabled(enabled: Boolean) {
        dataStore.setJikanMetadataEnabled(enabled)
    }

    override val externalRatingsEnabledFlow: Flow<Boolean> = dataStore.externalRatingsEnabled

    override suspend fun setExternalRatingsEnabled(enabled: Boolean) {
        dataStore.setExternalRatingsEnabled(enabled)
    }

    init {
        repositoryScope.launch {
            dataStore.watchlistIds.collect { ids ->
                _watchlistIdsFlow.value = ids
                val resolvedItems = ids.mapNotNull { id ->
                    MockCatalogDataSource.items.find { it.id == id }?.copy(isSaved = true)
                }
                _watchlistFlow.value = resolvedItems
            }
        }
        repositoryScope.launch {
            dataStore.playbackPositions.collect { positions ->
                _playbackPositionsFlow.value = positions
            }
        }
        repositoryScope.launch {
            dataStore.preferredAudioLanguage.collect { _preferredAudioLanguageFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.subtitlesEnabled.collect { _subtitlesEnabledFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.defaultQuality.collect { qualityStr ->
                _defaultQualityFlow.value = VideoQuality.values().find { it.label.contains(qualityStr, ignoreCase = true) }
                    ?: VideoQuality.QUALITY_1080P
            }
        }
        repositoryScope.launch {
            dataStore.themeMode.collect { _themeModeFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.notificationsEnabled.collect { _notificationsEnabledFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.preferredSubtitleLanguage.collect { _preferredSubtitleLanguageFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.appLanguage.collect { _appLanguageFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.userProfile.collect { _userProfileFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.autoUpdateCheckEnabled.collect { _autoUpdateCheckEnabledFlow.value = it }
        }
        repositoryScope.launch {
            dataStore.lastUpdateCheckTime.collect { _lastUpdateCheckTimeFlow.value = it }
        }
    }

    override suspend fun toggleWatchlist(mediaItem: MediaItem): Boolean {
        return dataStore.toggleWatchlistId(mediaItem.id)
    }

    override suspend fun isSaved(mediaId: String): Boolean {
        return _watchlistIdsFlow.value.contains(mediaId)
    }

    override suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {
        dataStore.savePlaybackPosition(mediaId, positionMs, durationMs)
    }

    override suspend fun getPlaybackPosition(mediaId: String): Long {
        return _playbackPositionsFlow.value[mediaId] ?: 0L
    }

    override suspend fun setPreferredAudioLanguage(language: String) {
        _preferredAudioLanguageFlow.value = language
        dataStore.setPreferredAudioLanguage(language)
    }

    override suspend fun setSubtitlesEnabled(enabled: Boolean) {
        _subtitlesEnabledFlow.value = enabled
        dataStore.setSubtitlesEnabled(enabled)
    }

    override suspend fun setDefaultQuality(quality: VideoQuality) {
        _defaultQualityFlow.value = quality
        dataStore.setDefaultQuality(quality.label)
    }

    override suspend fun setThemeMode(mode: String) {
        _themeModeFlow.value = mode
        dataStore.setThemeMode(mode)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabledFlow.value = enabled
        dataStore.setNotificationsEnabled(enabled)
    }

    override suspend fun setPreferredSubtitleLanguage(lang: String) {
        _preferredSubtitleLanguageFlow.value = lang
        dataStore.setPreferredSubtitleLanguage(lang)
    }

    override suspend fun setAppLanguage(lang: String) {
        _appLanguageFlow.value = lang
        dataStore.setAppLanguage(lang)
    }

    override suspend fun saveUserProfile(name: String, avatarUri: String?) {
        _userProfileFlow.value = UserProfile(
            displayName = name.trim(),
            avatarUri = avatarUri,
            isCreated = true
        )
        dataStore.saveUserProfile(name, avatarUri)
    }

    override suspend fun resetUserProfile() {
        _userProfileFlow.value = UserProfile()
        dataStore.resetUserProfile()
    }

    override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        _autoUpdateCheckEnabledFlow.value = enabled
        dataStore.setAutoUpdateCheckEnabled(enabled)
    }

    override suspend fun setLastUpdateCheckTime(timestampMs: Long) {
        _lastUpdateCheckTimeFlow.value = timestampMs
        dataStore.setLastUpdateCheckTime(timestampMs)
    }
}
