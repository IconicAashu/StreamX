package com.streamx.app.presentation.settings.content_discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.AddonManifest
import com.streamx.app.domain.model.MediaServerConfig
import com.streamx.app.domain.model.MediaServerType
import com.streamx.app.domain.model.ProviderModule
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ContentDiscoveryUiState(
    val recentSearchesEnabled: Boolean = true,
    val showAddAddonDialog: Boolean = false,
    val showAddServerDialog: Boolean = false,
    val isTestingConnection: Boolean = false,
    val testingServerId: String? = null,
    val testResultMessage: String? = null
)

class ContentDiscoveryViewModel(
    private val manifestAddonRepository: ManifestAddonRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentDiscoveryUiState())
    val uiState: StateFlow<ContentDiscoveryUiState> = _uiState.asStateFlow()

    val addons: StateFlow<List<AddonManifest>> = manifestAddonRepository.addonsFlow
    val mediaServers: StateFlow<List<MediaServerConfig>> = manifestAddonRepository.mediaServersFlow
    val customProviders: StateFlow<List<ProviderModule>> = manifestAddonRepository.customProvidersFlow

    init {
        viewModelScope.launch {
            userPreferencesRepository.recentSearchesEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(recentSearchesEnabled = enabled) }
            }
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
        }
    }

    fun toggleAddAddonDialog(show: Boolean) {
        _uiState.update { it.copy(showAddAddonDialog = show) }
    }

    fun toggleAddServerDialog(show: Boolean) {
        _uiState.update { it.copy(showAddServerDialog = show) }
    }

    fun addAddon(name: String, manifestUrl: String, description: String = "") {
        viewModelScope.launch {
            manifestAddonRepository.addAddon(name, manifestUrl, description)
            _uiState.update { it.copy(showAddAddonDialog = false) }
        }
    }

    fun toggleAddon(id: String) {
        viewModelScope.launch {
            manifestAddonRepository.toggleAddon(id)
        }
    }

    fun removeAddon(id: String) {
        viewModelScope.launch {
            manifestAddonRepository.removeAddon(id)
        }
    }

    fun refreshAddons() {
        viewModelScope.launch {
            manifestAddonRepository.refreshAddons()
        }
    }

    fun addMediaServer(type: MediaServerType, name: String, url: String, apiKey: String) {
        viewModelScope.launch {
            val newServer = MediaServerConfig(
                id = UUID.randomUUID().toString(),
                type = type,
                serverName = name.ifBlank { "${type.displayName} Server" },
                baseUrl = url,
                apiKey = apiKey,
                isConnected = true
            )
            manifestAddonRepository.saveMediaServer(newServer)
            _uiState.update { it.copy(showAddServerDialog = false) }
        }
    }

    fun removeMediaServer(id: String) {
        viewModelScope.launch {
            manifestAddonRepository.removeMediaServer(id)
        }
    }

    fun testServerConnection(server: MediaServerConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, testingServerId = server.id, testResultMessage = null) }
            delay(1200) // Realistic probe latency
            val success = manifestAddonRepository.testConnection(server)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    testingServerId = null,
                    testResultMessage = if (success) "Connected successfully to ${server.serverName}!" else "Connection failed. Check host and token."
                )
            }
        }
    }

    fun toggleProvider(id: String) {
        viewModelScope.launch {
            manifestAddonRepository.toggleProvider(id)
        }
    }
}
