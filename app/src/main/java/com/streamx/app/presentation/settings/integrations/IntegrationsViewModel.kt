package com.streamx.app.presentation.settings.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IntegrationsUiState(
    val tmdbEnrichmentEnabled: Boolean = true,
    val jikanMetadataEnabled: Boolean = true,
    val externalRatingsEnabled: Boolean = true,
    val cacheStatus: String = "4.2 MB cached",
    val isCacheCleared: Boolean = false
)

class IntegrationsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntegrationsUiState())
    val uiState: StateFlow<IntegrationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.tmdbEnrichmentEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(tmdbEnrichmentEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.jikanMetadataEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(jikanMetadataEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.externalRatingsEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(externalRatingsEnabled = enabled) }
            }
        }
    }

    fun setTmdbEnrichment(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTmdbEnrichmentEnabled(enabled)
            _uiState.update { it.copy(tmdbEnrichmentEnabled = enabled) }
        }
    }

    fun setJikanMetadata(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setJikanMetadataEnabled(enabled)
            _uiState.update { it.copy(jikanMetadataEnabled = enabled) }
        }
    }

    fun setExternalRatings(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExternalRatingsEnabled(enabled)
            _uiState.update { it.copy(externalRatingsEnabled = enabled) }
        }
    }

    fun clearMetadataCache() {
        _uiState.update { it.copy(cacheStatus = "0 KB cached", isCacheCleared = true) }
    }
}
