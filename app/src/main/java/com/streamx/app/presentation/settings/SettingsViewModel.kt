package com.streamx.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.streamx.app.BuildConfig
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.repository.UserPreferencesRepository
import com.streamx.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingItem(
    val id: String,
    val group: SettingGroup,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val badge: String? = null
)

enum class SettingGroup(val header: String) {
    APPEARANCE("Look & Feel"),
    NOTIFICATIONS("Notifications"),
    PLAYBACK_SOURCES("Playback & Sources"),
    STORAGE_PRIVACY("Data & Privacy"),
    INTEGRATIONS("External Integrations"),
    ABOUT("About STREAMX"),
    DEVELOPER("Developer & Diagnostics")
}

data class SettingsUiState(
    val searchQuery: String = "",
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val appLanguage: String = "English (US)",
    val notificationsEnabled: Boolean = true,
    val autoplay: Boolean = true,
    val defaultQuality: VideoQuality = VideoQuality.QUALITY_1080P,
    val preferredAudioLanguage: String = "Original",
    val subtitlesEnabled: Boolean = true,
    val preferredSubtitleLanguage: String = "English",
    val recentSearchesEnabled: Boolean = true,
    val cacheSizeFormatted: String = "Calculating...",
    val feedbackMessage: String? = null,
    val showThemeDialog: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val showPlaybackDialog: Boolean = false,
    val showAudioSubtitleDialog: Boolean = false,
    val showClearCacheDialog: Boolean = false,
    val showDebugPlayerDialog: Boolean = false
)

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val allSettings = buildList {
        // Group 1: Appearance
        add(SettingItem("theme_mode", SettingGroup.APPEARANCE, "Theme", "Light, Dark, or System Default", "palette"))
        add(SettingItem("app_language", SettingGroup.APPEARANCE, "App Language", "English (US), Español, हिन्दी, etc.", "language"))

        // Group 2: Notifications
        add(SettingItem("notifications", SettingGroup.NOTIFICATIONS, "Push & Local Notifications", "Watchlist alerts and test notifications", "notifications"))

        // Group 3: Playback & Sources
        add(SettingItem("playback_defaults", SettingGroup.PLAYBACK_SOURCES, "Playback Defaults", "Autoplay & default stream quality", "play_circle"))
        add(SettingItem("audio_subtitles", SettingGroup.PLAYBACK_SOURCES, "Audio & Subtitles", "Default audio track and subtitle language", "subtitles"))
        add(SettingItem("addons", SettingGroup.PLAYBACK_SOURCES, "Add-ons Catalogue", "Manage Stremio add-ons and providers", "extension", "STREMIO"))
        add(SettingItem("jellyfin", SettingGroup.PLAYBACK_SOURCES, "Jellyfin Media Server", "Connect personal home server", "dns", "JELLYFIN"))
        add(SettingItem("p2p", SettingGroup.PLAYBACK_SOURCES, "P2P Streaming Engine", "BitTorrent engine settings", "cloud_download", "NATIVE P2P"))

        // Group 4: Data & Privacy
        add(SettingItem("cache_storage", SettingGroup.STORAGE_PRIVACY, "Storage & Cache", "Clear image and stream cache", "storage"))
        add(SettingItem("search_history", SettingGroup.STORAGE_PRIVACY, "Search History", "Toggle and clear search query history", "history"))

        // Group 5: Integrations
        add(SettingItem("integrations", SettingGroup.INTEGRATIONS, "Metadata Integrations", "TMDB API, Jikan Anime, external ratings", "hub"))

        // Group 6: About
        add(SettingItem("about_app", SettingGroup.ABOUT, "About STREAMX", "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", "info"))

        // Group 7: Developer (Debug only)
        if (BuildConfig.DEBUG) {
            add(
                SettingItem(
                    id = "debug_player_test",
                    group = SettingGroup.DEVELOPER,
                    title = "Debug Video Player Test",
                    subtitle = "Verify ExoPlayer pipeline with controlled Big Buck Bunny samples",
                    iconName = "bug_report",
                    badge = "DEBUG ONLY"
                )
            )
        }
    }

    val filteredItems: StateFlow<Map<SettingGroup, List<SettingItem>>> = _uiState
        .map { state ->
            val query = state.searchQuery.trim().lowercase()
            val filtered = if (query.isEmpty()) {
                allSettings
            } else {
                allSettings.filter {
                    it.title.lowercase().contains(query) ||
                    it.subtitle.lowercase().contains(query) ||
                    it.group.header.lowercase().contains(query)
                }
            }
            filtered.groupBy { it.group }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = allSettings.groupBy { it.group }
        )

    init {
        viewModelScope.launch {
            userPreferencesRepository.themeModeFlow.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoplayEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(autoplay = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.defaultQualityFlow.collect { quality ->
                _uiState.update { it.copy(defaultQuality = quality) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferredAudioLanguageFlow.collect { lang ->
                _uiState.update { it.copy(preferredAudioLanguage = lang) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.subtitlesEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(subtitlesEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferredSubtitleLanguageFlow.collect { lang ->
                _uiState.update { it.copy(preferredSubtitleLanguage = lang) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.notificationsEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.recentSearchesEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(recentSearchesEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.appLanguageFlow.collect { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode, showThemeDialog = false) }
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppLanguage(lang)
            _uiState.update { it.copy(appLanguage = lang, showLanguageDialog = false) }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNotificationsEnabled(enabled)
            _uiState.update { it.copy(notificationsEnabled = enabled) }
        }
    }

    fun sendTestNotification(context: Context) {
        val success = NotificationHelper.sendTestNotification(context)
        if (success) {
            _uiState.update { it.copy(feedbackMessage = "Test notification sent successfully!") }
        } else {
            _uiState.update { it.copy(feedbackMessage = "Notification permission not granted. Please allow notifications in Android settings.") }
        }
    }

    fun setAutoplay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoplayEnabled(enabled)
            _uiState.update { it.copy(autoplay = enabled) }
        }
    }

    fun setDefaultQuality(quality: VideoQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultQuality(quality)
            _uiState.update { it.copy(defaultQuality = quality) }
        }
    }

    fun setPreferredAudioLanguage(lang: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredAudioLanguage(lang)
            _uiState.update { it.copy(preferredAudioLanguage = lang) }
        }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSubtitlesEnabled(enabled)
            _uiState.update { it.copy(subtitlesEnabled = enabled) }
        }
    }

    fun setPreferredSubtitleLanguage(lang: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredSubtitleLanguage(lang)
            _uiState.update { it.copy(preferredSubtitleLanguage = lang) }
        }
    }

    fun setRecentSearchesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setRecentSearchesEnabled(enabled)
            _uiState.update { it.copy(recentSearchesEnabled = enabled) }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            userPreferencesRepository.clearSearchHistory()
            _uiState.update { it.copy(feedbackMessage = "Search history cleared.") }
        }
    }

    fun refreshCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            var totalBytes = 0L
            try {
                context.cacheDir?.walkTopDown()?.filter { it.isFile }?.forEach {
                    totalBytes += it.length()
                }
                context.externalCacheDir?.walkTopDown()?.filter { it.isFile }?.forEach {
                    totalBytes += it.length()
                }
            } catch (_: Exception) {}

            val formatted = when {
                totalBytes >= 1024 * 1024 -> "%.1f MB".format(totalBytes / (1024.0 * 1024.0))
                totalBytes >= 1024 -> "%.1f KB".format(totalBytes / 1024.0)
                else -> "$totalBytes bytes"
            }
            _uiState.update { it.copy(cacheSizeFormatted = formatted) }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearAppCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear Coil memory and disk caches
                Coil.imageLoader(context).memoryCache?.clear()
                Coil.imageLoader(context).diskCache?.clear()

                // Clear cache directory files
                context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        cacheSizeFormatted = "0.0 KB",
                        showClearCacheDialog = false,
                        feedbackMessage = "Cache cleared successfully."
                    )
                }
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun toggleThemeDialog(show: Boolean) = _uiState.update { it.copy(showThemeDialog = show) }
    fun toggleLanguageDialog(show: Boolean) = _uiState.update { it.copy(showLanguageDialog = show) }
    fun togglePlaybackDialog(show: Boolean) = _uiState.update { it.copy(showPlaybackDialog = show) }
    fun toggleAudioSubtitleDialog(show: Boolean) = _uiState.update { it.copy(showAudioSubtitleDialog = show) }
    fun toggleClearCacheDialog(show: Boolean) = _uiState.update { it.copy(showClearCacheDialog = show) }
    fun toggleDebugPlayerDialog(show: Boolean) = _uiState.update { it.copy(showDebugPlayerDialog = show) }
}
