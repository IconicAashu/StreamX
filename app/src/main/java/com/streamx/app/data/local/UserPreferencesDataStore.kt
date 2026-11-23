package com.streamx.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.streamx.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "streamx_preferences")

class UserPreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val RECENT_SEARCHES_ENABLED = booleanPreferencesKey("recent_searches_enabled")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val DEFAULT_STREAM_QUALITY = stringPreferencesKey("default_stream_quality")
        val AUTOPLAY_ENABLED = booleanPreferencesKey("autoplay_enabled")
        val TMDB_ENRICHMENT_ENABLED = booleanPreferencesKey("tmdb_enrichment_enabled")
        val JIKAN_METADATA_ENABLED = booleanPreferencesKey("jikan_metadata_enabled")
        val EXTERNAL_RATINGS_ENABLED = booleanPreferencesKey("external_ratings_enabled")
        val WATCHLIST_IDS = stringSetPreferencesKey("watchlist_ids")
        val PLAYBACK_POSITIONS = stringPreferencesKey("playback_positions")
        val ADDONS_DATA = stringPreferencesKey("addons_data")
        val SERVERS_DATA = stringPreferencesKey("servers_data")
        val PROVIDERS_DATA = stringPreferencesKey("providers_data")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val USER_PROFILE_NAME = stringPreferencesKey("user_profile_name")
        val USER_PROFILE_AVATAR_URI = stringPreferencesKey("user_profile_avatar_uri")
        val IS_PROFILE_CREATED = booleanPreferencesKey("is_profile_created")
        val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
    }

    val watchlistIds: Flow<Set<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.WATCHLIST_IDS] ?: setOf("m1", "m2")
        }

    suspend fun toggleWatchlistId(mediaId: String): Boolean {
        var isNowSaved = false
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.WATCHLIST_IDS] ?: setOf("m1", "m2")
            if (mediaId in current) {
                preferences[PreferencesKeys.WATCHLIST_IDS] = current - mediaId
                isNowSaved = false
            } else {
                preferences[PreferencesKeys.WATCHLIST_IDS] = current + mediaId
                isNowSaved = true
            }
        }
        return isNowSaved
    }

    suspend fun setWatchlistIds(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WATCHLIST_IDS] = ids
        }
    }

    val playbackPositions: Flow<Map<String, Long>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val raw = preferences[PreferencesKeys.PLAYBACK_POSITIONS] ?: ""
            if (raw.isBlank()) emptyMap()
            else {
                raw.split("|||").mapNotNull { entry ->
                    val lastColon = entry.lastIndexOf(':')
                    if (lastColon > 0) {
                        val key = entry.substring(0, lastColon)
                        val valueStr = entry.substring(lastColon + 1)
                        val posMs = com.streamx.app.utils.DurationParser.parseToMs(valueStr, -1L)
                        if (posMs >= 0L) key to posMs else null
                    } else null
                }.toMap()
            }
        }

    suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.PLAYBACK_POSITIONS] ?: ""
            val currentMap = if (raw.isBlank()) mutableMapOf()
            else {
                raw.split("|||").mapNotNull { entry ->
                    val lastColon = entry.lastIndexOf(':')
                    if (lastColon > 0) {
                        val key = entry.substring(0, lastColon)
                        val valueStr = entry.substring(lastColon + 1)
                        val posMs = com.streamx.app.utils.DurationParser.parseToMs(valueStr, -1L)
                        if (posMs >= 0L) key to posMs else null
                    } else null
                }.toMap().toMutableMap()
            }
            currentMap[mediaId] = positionMs
            preferences[PreferencesKeys.PLAYBACK_POSITIONS] = currentMap.entries.joinToString("|||") { "${it.key}:${it.value}" }
        }
    }

    suspend fun getPlaybackPosition(mediaId: String): Long {
        return try {
            playbackPositions.first()[mediaId] ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] = completed
        }
    }

    val recentSearches: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val serialized = preferences[PreferencesKeys.RECENT_SEARCHES] ?: ""
            if (serialized.isBlank()) {
                listOf("Blue Beetle", "Avatar", "Kalki 2898 AD", "Anime", "Sci-Fi")
            } else {
                serialized.split("|||").filter { it.isNotBlank() }
            }
        }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { preferences ->
            val isEnabled = preferences[PreferencesKeys.RECENT_SEARCHES_ENABLED] ?: true
            if (!isEnabled) return@edit
            val currentList = (preferences[PreferencesKeys.RECENT_SEARCHES] ?: "")
                .split("|||")
                .filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
                .toMutableList()
            // Add to top, max 10
            currentList.add(0, trimmed)
            val updated = currentList.take(10).joinToString("|||")
            preferences[PreferencesKeys.RECENT_SEARCHES] = updated
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.dataStore.edit { preferences ->
            val currentList = (preferences[PreferencesKeys.RECENT_SEARCHES] ?: "")
                .split("|||")
                .filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            preferences[PreferencesKeys.RECENT_SEARCHES] = currentList.joinToString("|||")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_SEARCHES] = ""
        }
    }

    val preferredAudioLanguage: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.PREFERRED_AUDIO_LANG] ?: "Original"
        }

    suspend fun setPreferredAudioLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_AUDIO_LANG] = language
        }
    }

    val subtitlesEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.SUBTITLES_ENABLED] ?: true
        }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SUBTITLES_ENABLED] = enabled
        }
    }

    val defaultQuality: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_STREAM_QUALITY] ?: "1080p"
        }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_STREAM_QUALITY] = quality
        }
    }

    val recentSearchesEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.RECENT_SEARCHES_ENABLED] ?: true
        }

    suspend fun setRecentSearchesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_SEARCHES_ENABLED] = enabled
        }
    }

    val autoplayEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTOPLAY_ENABLED] ?: true
        }

    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOPLAY_ENABLED] = enabled
        }
    }

    val tmdbEnrichmentEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.TMDB_ENRICHMENT_ENABLED] ?: true
        }

    suspend fun setTmdbEnrichmentEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TMDB_ENRICHMENT_ENABLED] = enabled
        }
    }

    val jikanMetadataEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.JIKAN_METADATA_ENABLED] ?: true
        }

    suspend fun setJikanMetadataEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.JIKAN_METADATA_ENABLED] = enabled
        }
    }

    val externalRatingsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.EXTERNAL_RATINGS_ENABLED] ?: true
        }

    suspend fun setExternalRatingsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_RATINGS_ENABLED] = enabled
        }
    }

    val rawAddonsData: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.ADDONS_DATA]
        }

    suspend fun saveRawAddonsData(data: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADDONS_DATA] = data
        }
    }

    val rawServersData: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.SERVERS_DATA]
        }

    suspend fun saveRawServersData(data: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVERS_DATA] = data
        }
    }

    val rawProvidersData: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.PROVIDERS_DATA]
        }

    suspend fun saveRawProvidersData(data: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROVIDERS_DATA] = data
        }
    }

    val themeMode: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM"
        }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    val preferredSubtitleLanguage: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG] ?: "English"
        }

    suspend fun setPreferredSubtitleLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG] = lang
        }
    }

    val appLanguage: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] ?: "English (US)"
        }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = lang
        }
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val name = preferences[PreferencesKeys.USER_PROFILE_NAME] ?: ""
            val avatarUri = preferences[PreferencesKeys.USER_PROFILE_AVATAR_URI]
            val isCreated = preferences[PreferencesKeys.IS_PROFILE_CREATED] ?: false
            UserProfile(
                displayName = name,
                avatarUri = avatarUri,
                isCreated = isCreated
            )
        }

    suspend fun saveUserProfile(name: String, avatarUri: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_PROFILE_NAME] = name.trim()
            if (avatarUri != null) {
                preferences[PreferencesKeys.USER_PROFILE_AVATAR_URI] = avatarUri
            } else {
                preferences.remove(PreferencesKeys.USER_PROFILE_AVATAR_URI)
            }
            preferences[PreferencesKeys.IS_PROFILE_CREATED] = true
        }
    }

    suspend fun resetUserProfile() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_PROFILE_NAME)
            preferences.remove(PreferencesKeys.USER_PROFILE_AVATAR_URI)
            preferences[PreferencesKeys.IS_PROFILE_CREATED] = false
        }
    }

    val autoUpdateCheckEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] ?: true
        }

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] = enabled
        }
    }

    val lastUpdateCheckTime: Flow<Long> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] ?: 0L
        }

    suspend fun setLastUpdateCheckTime(timestampMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] = timestampMs
        }
    }
}
