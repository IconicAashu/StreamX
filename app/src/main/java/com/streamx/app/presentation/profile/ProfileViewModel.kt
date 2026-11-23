package com.streamx.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.model.UserProfile
import com.streamx.app.domain.model.VideoQuality
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileSectionTab(val label: String) {
    OVERVIEW("Overview"),
    WATCHLIST("My Watchlist")
}

data class ProfileUiState(
    val userProfile: UserProfile = UserProfile(),
    val selectedTab: ProfileSectionTab = ProfileSectionTab.OVERVIEW,
    val continueWatchingList: List<MediaItem> = emptyList(),
    val isEditDialogOpen: Boolean = false,
    val inputName: String = "",
    val inputAvatarUri: String? = null,
    val nameError: String? = null,
    val isLanguageDialogVisible: Boolean = false,
    val isQualityDialogVisible: Boolean = false,
    val showResetDialog: Boolean = false,
    val feedbackMessage: String? = null
) {
    val userName: String
        get() = userProfile.displayName.ifBlank { "User" }

    val userEmail: String
        get() = "Local Account • Private On-Device"

    val membershipTier: String
        get() = "STREAMX Local • Pure Edition"
}

class ProfileViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    val watchlist: StateFlow<List<MediaItem>> = userPreferencesRepository.watchlistFlow
    val preferredAudioLanguage: StateFlow<String> = userPreferencesRepository.preferredAudioLanguageFlow
    val subtitlesEnabled: StateFlow<Boolean> = userPreferencesRepository.subtitlesEnabledFlow
    val defaultQuality: StateFlow<VideoQuality> = userPreferencesRepository.defaultQualityFlow
    val userProfile: StateFlow<UserProfile> = userPreferencesRepository.userProfileFlow

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userProfileFlow.collect { profile ->
                _uiState.update { current ->
                    current.copy(
                        userProfile = profile,
                        inputName = if (!profile.isCreated && current.inputName.isBlank()) "" else current.inputName
                    )
                }
            }
        }
        viewModelScope.launch {
            val list = catalogRepository.getContinueWatching()
            _uiState.update { it.copy(continueWatchingList = list) }
        }
    }

    fun onNameInputChange(name: String) {
        _uiState.update {
            it.copy(
                inputName = name,
                nameError = null
            )
        }
    }

    fun onAvatarSelected(uriStr: String?) {
        _uiState.update { it.copy(inputAvatarUri = uriStr) }
    }

    fun createProfile(): Boolean {
        val name = _uiState.value.inputName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(nameError = "Please enter your name to create your profile.") }
            return false
        }
        if (name.length > 40) {
            _uiState.update { it.copy(nameError = "Display name cannot exceed 40 characters.") }
            return false
        }
        viewModelScope.launch {
            userPreferencesRepository.saveUserProfile(name, _uiState.value.inputAvatarUri)
            _uiState.update {
                it.copy(
                    nameError = null,
                    feedbackMessage = "Welcome, $name!"
                )
            }
        }
        return true
    }

    fun openEditProfileDialog() {
        val current = _uiState.value.userProfile
        _uiState.update {
            it.copy(
                isEditDialogOpen = true,
                inputName = current.displayName,
                inputAvatarUri = current.avatarUri,
                nameError = null
            )
        }
    }

    fun dismissEditProfileDialog() {
        _uiState.update {
            it.copy(
                isEditDialogOpen = false,
                nameError = null
            )
        }
    }

    fun saveProfileChanges(): Boolean {
        val name = _uiState.value.inputName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(nameError = "Display name cannot be empty.") }
            return false
        }
        if (name.length > 40) {
            _uiState.update { it.copy(nameError = "Display name cannot exceed 40 characters.") }
            return false
        }
        viewModelScope.launch {
            userPreferencesRepository.saveUserProfile(name, _uiState.value.inputAvatarUri)
            _uiState.update {
                it.copy(
                    isEditDialogOpen = false,
                    nameError = null,
                    feedbackMessage = "Profile updated."
                )
            }
        }
        return true
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun selectTab(tab: ProfileSectionTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun removeFromWatchlist(item: MediaItem) {
        viewModelScope.launch {
            userPreferencesRepository.toggleWatchlist(item)
        }
    }

    fun setLanguageDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isLanguageDialogVisible = visible) }
    }

    fun setQualityDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isQualityDialogVisible = visible) }
    }

    fun selectPreferredLanguage(language: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredAudioLanguage(language)
            setLanguageDialogVisible(false)
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSubtitlesEnabled(enabled)
        }
    }

    fun selectDefaultQuality(quality: VideoQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultQuality(quality)
            setQualityDialogVisible(false)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(false)
        }
    }

    fun resetProfileToUncreated() {
        viewModelScope.launch {
            userPreferencesRepository.resetUserProfile()
            _uiState.update {
                it.copy(
                    inputName = "",
                    inputAvatarUri = null,
                    nameError = null,
                    isEditDialogOpen = false
                )
            }
        }
    }
}
