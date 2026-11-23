package com.streamx.app.presentation.settings.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.AddonStatus
import com.streamx.app.domain.model.InstalledAddon
import com.streamx.app.domain.model.StremioManifest
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.network.addon.AddonSecurityValidator
import com.streamx.app.network.addon.StremioAddonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AddonTab {
    INSTALLED,
    BROWSE
}

data class CuratedAddon(
    val name: String,
    val manifestUrl: String,
    val description: String,
    val author: String,
    val types: List<String>,
    val resources: List<String>,
    val iconUrl: String? = null,
    val requiresConfiguration: Boolean = false,
    val configurationHint: String? = null,
    val category: String = "Stremio Add-on",
    val integrationStatus: String = "Fully Compatible",
    val securityNotice: String? = null,
    val isExecutablePlugin: Boolean = false,
    val isAvailable: Boolean = true,
    val healthStatus: String = "Online"
)

data class AddonsUiState(
    val selectedTab: AddonTab = AddonTab.INSTALLED,
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val showInstallDialog: Boolean = false,
    val installUrlInput: String = "",
    val isVerifyingManifest: Boolean = false,
    val manifestPreview: StremioManifest? = null,
    val installErrorMessage: String? = null,
    val selectedAddonForDetails: InstalledAddon? = null,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

class AddonsViewModel(
    private val manifestAddonRepository: ManifestAddonRepository,
    private val addonClient: StremioAddonClient = StremioAddonClient()
) : ViewModel() {

    val installedAddons: StateFlow<List<InstalledAddon>> = manifestAddonRepository.installedAddonsFlow

    private val _uiState = MutableStateFlow(AddonsUiState())
    val uiState: StateFlow<AddonsUiState> = _uiState.asStateFlow()

    val curatedAddons = com.streamx.app.domain.model.UnifiedProviderCatalogue.getAllProviders().map { provider ->
        CuratedAddon(
            name = provider.name,
            manifestUrl = provider.manifestUrl,
            description = provider.description,
            author = provider.author,
            types = provider.supportedTypes.map { it.replaceFirstChar(Char::titlecase) },
            resources = provider.declaredResources.map { it.replaceFirstChar(Char::titlecase) },
            iconUrl = provider.iconUrl,
            requiresConfiguration = provider.requiresConfiguration,
            configurationHint = provider.configurationHint,
            category = provider.category.displayName,
            integrationStatus = provider.integrationStatus.label,
            securityNotice = provider.securityNotice,
            isExecutablePlugin = provider.isExecutablePlugin,
            isAvailable = provider.integrationStatus != com.streamx.app.domain.model.ProviderIntegrationStatus.OFFLINE_UNAVAILABLE &&
                          !provider.healthStatus.contains("Offline", ignoreCase = true),
            healthStatus = provider.healthStatus
        )
    }

    fun setCategoryFilter(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setTab(tab: AddonTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openInstallDialog(initialUrl: String = "") {
        _uiState.update {
            it.copy(
                showInstallDialog = true,
                installUrlInput = initialUrl,
                manifestPreview = null,
                installErrorMessage = null,
                isVerifyingManifest = false
            )
        }
        if (initialUrl.isNotBlank()) {
            verifyManifest(initialUrl)
        }
    }

    fun closeInstallDialog() {
        _uiState.update {
            it.copy(
                showInstallDialog = false,
                installUrlInput = "",
                manifestPreview = null,
                installErrorMessage = null,
                isVerifyingManifest = false
            )
        }
    }

    fun onUrlInputChanged(url: String) {
        _uiState.update {
            it.copy(
                installUrlInput = url,
                installErrorMessage = null,
                manifestPreview = null
            )
        }
    }

    fun verifyManifest(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(installErrorMessage = "Please enter a manifest or repository URL.") }
            return
        }

        val detection = com.streamx.app.network.provider.RepositoryTypeDetector.detect(trimmed)
        if (detection.category == com.streamx.app.domain.model.ProviderCategory.NUVIO_NATIVE) {
            _uiState.update {
                it.copy(
                    isVerifyingManifest = false,
                    installErrorMessage = "Nuvio Native Plugin detected: ${detection.explanation}"
                )
            }
            return
        } else if (detection.category == com.streamx.app.domain.model.ProviderCategory.CLOUDSTREAM_EXTENSION) {
            _uiState.update {
                it.copy(
                    isVerifyingManifest = false,
                    installErrorMessage = "CloudStream Extension detected: ${detection.explanation}"
                )
            }
            return
        } else if (detection.category == com.streamx.app.domain.model.ProviderCategory.MEDIA_SERVER) {
            _uiState.update {
                it.copy(
                    isVerifyingManifest = false,
                    installErrorMessage = "Media Server detected. Please configure Jellyfin in Settings > Media Servers."
                )
            }
            return
        }

        val effectiveUrl = detection.normalizedUrl
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingManifest = true, installErrorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                addonClient.fetchManifest(effectiveUrl)
            }
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isVerifyingManifest = false,
                        manifestPreview = result.getOrNull(),
                        installErrorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isVerifyingManifest = false,
                        installErrorMessage = result.exceptionOrNull()?.message ?: "Failed to load manifest."
                    )
                }
            }
        }
    }

    fun confirmInstall() {
        val url = _uiState.value.installUrlInput.trim()
        if (url.isBlank()) return

        val detection = com.streamx.app.network.provider.RepositoryTypeDetector.detect(url)
        val effectiveUrl = detection.normalizedUrl

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingManifest = true) }
            val result = withContext(Dispatchers.IO) {
                manifestAddonRepository.installAddonFromUrl(effectiveUrl)
            }
            if (result.isSuccess) {
                val installed = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        showInstallDialog = false,
                        isVerifyingManifest = false,
                        feedbackMessage = "Successfully installed ${installed.name}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isVerifyingManifest = false,
                        installErrorMessage = result.exceptionOrNull()?.message ?: "Installation failed."
                    )
                }
            }
        }
    }

    fun installCuratedAddon(curated: CuratedAddon) {
        if (!curated.isAvailable) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "${curated.name} is currently offline (${curated.securityNotice ?: "Host unreachable"}). Installation disabled."
                )
            }
            return
        }

        if (curated.isExecutablePlugin) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "${curated.name} (${curated.category}): ${curated.securityNotice ?: "Requires an isolated runtime sandbox."}"
                )
            }
            return
        }

        if (curated.category == "Media Server") {
            _uiState.update {
                it.copy(
                    feedbackMessage = "Connect your ${curated.name} under Settings > Media Servers."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = manifestAddonRepository.installAddonFromUrl(curated.manifestUrl)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        feedbackMessage = "Installed ${curated.name}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        feedbackMessage = "Failed to install ${curated.name}: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun refreshCatalogue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            com.streamx.app.domain.model.UnifiedProviderCatalogue.refreshCatalogue()
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    feedbackMessage = "Provider catalogue updated"
                )
            }
        }
    }

    fun toggleAddon(id: String) {
        viewModelScope.launch {
            manifestAddonRepository.toggleAddon(id)
        }
    }

    fun removeAddon(id: String) {
        viewModelScope.launch {
            val addon = installedAddons.value.find { it.id == id }
            manifestAddonRepository.removeAddon(id)
            _uiState.update {
                it.copy(
                    selectedAddonForDetails = null,
                    feedbackMessage = "Removed ${addon?.name ?: "Add-on"}"
                )
            }
        }
    }

    fun refreshAddon(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = manifestAddonRepository.refreshAddon(id)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    feedbackMessage = if (result.isSuccess) "Add-on manifest updated" else "Refresh failed"
                )
            }
        }
    }

    fun refreshAllAddons() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            manifestAddonRepository.refreshAddons()
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    feedbackMessage = "All add-on manifests refreshed"
                )
            }
        }
    }

    fun moveAddonUp(index: Int) {
        if (index > 0) {
            viewModelScope.launch {
                manifestAddonRepository.reorderAddons(index, index - 1)
            }
        }
    }

    fun moveAddonDown(index: Int) {
        val list = installedAddons.value
        if (index < list.size - 1) {
            viewModelScope.launch {
                manifestAddonRepository.reorderAddons(index, index + 1)
            }
        }
    }

    fun selectAddonDetails(addon: InstalledAddon?) {
        _uiState.update { it.copy(selectedAddonForDetails = addon) }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
