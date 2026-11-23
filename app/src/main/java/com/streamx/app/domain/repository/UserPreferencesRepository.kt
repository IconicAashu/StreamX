package com.streamx.app.domain.repository

import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.UserProfile
import com.streamx.app.domain.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UserPreferencesRepository {
    val isOnboardingCompletedFlow: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    val watchlistIdsFlow: StateFlow<Set<String>>
    val watchlistFlow: StateFlow<List<MediaItem>>
    suspend fun toggleWatchlist(mediaItem: MediaItem): Boolean
    suspend fun isSaved(mediaId: String): Boolean

    val playbackPositionsFlow: StateFlow<Map<String, Long>>
    suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long)
    suspend fun getPlaybackPosition(mediaId: String): Long

    val recentSearchesFlow: Flow<List<String>>
    suspend fun addSearchQuery(query: String)
    suspend fun removeSearchQuery(query: String)
    suspend fun clearSearchHistory()

    val preferredAudioLanguageFlow: StateFlow<String>
    suspend fun setPreferredAudioLanguage(language: String)

    val subtitlesEnabledFlow: StateFlow<Boolean>
    suspend fun setSubtitlesEnabled(enabled: Boolean)

    val defaultQualityFlow: StateFlow<VideoQuality>
    suspend fun setDefaultQuality(quality: VideoQuality)

    val recentSearchesEnabledFlow: Flow<Boolean>
    suspend fun setRecentSearchesEnabled(enabled: Boolean)

    val autoplayEnabledFlow: Flow<Boolean>
    suspend fun setAutoplayEnabled(enabled: Boolean)

    val tmdbEnrichmentEnabledFlow: Flow<Boolean>
    suspend fun setTmdbEnrichmentEnabled(enabled: Boolean)

    val jikanMetadataEnabledFlow: Flow<Boolean>
    suspend fun setJikanMetadataEnabled(enabled: Boolean)

    val externalRatingsEnabledFlow: Flow<Boolean>
    suspend fun setExternalRatingsEnabled(enabled: Boolean)

    val themeModeFlow: StateFlow<String>
    suspend fun setThemeMode(mode: String)

    val notificationsEnabledFlow: StateFlow<Boolean>
    suspend fun setNotificationsEnabled(enabled: Boolean)

    val preferredSubtitleLanguageFlow: StateFlow<String>
    suspend fun setPreferredSubtitleLanguage(lang: String)

    val appLanguageFlow: StateFlow<String>
    suspend fun setAppLanguage(lang: String)

    val userProfileFlow: StateFlow<UserProfile>
    suspend fun saveUserProfile(name: String, avatarUri: String?)
    suspend fun resetUserProfile()

    val autoUpdateCheckEnabledFlow: StateFlow<Boolean>
    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean)

    val lastUpdateCheckTimeFlow: StateFlow<Long>
    suspend fun setLastUpdateCheckTime(timestampMs: Long)
}
