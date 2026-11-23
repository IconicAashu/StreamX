package com.streamx.app.presentation.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.jellyfin.JellyfinRepository
import com.streamx.app.domain.model.MediaServerConfig
import com.streamx.app.network.jellyfin.client.JellyfinClientFactory
import com.streamx.app.network.jellyfin.model.JellyfinItemDto
import com.streamx.app.network.jellyfin.model.JellyfinPublicSystemInfo
import com.streamx.app.network.jellyfin.model.JellyfinQuickConnectInitiate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JellyfinUiState(
    val serverUrlInput: String = "http://",
    val usernameInput: String = "",
    val passwordInput: String = "",
    val isConnecting: Boolean = false,
    val isLoggingIn: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val serverInfo: JellyfinPublicSystemInfo? = null,
    val quickConnectInitiate: JellyfinQuickConnectInitiate? = null,
    val isQuickConnectPolling: Boolean = false,
    // Library state
    val views: List<JellyfinItemDto> = emptyList(),
    val selectedView: JellyfinItemDto? = null,
    val continueWatching: List<JellyfinItemDto> = emptyList(),
    val latestItems: List<JellyfinItemDto> = emptyList(),
    val libraryItems: List<JellyfinItemDto> = emptyList(),
    val isLoadingLibrary: Boolean = false,
    // Detail state
    val selectedItemDetail: JellyfinItemDto? = null,
    val seasons: List<JellyfinItemDto> = emptyList(),
    val selectedSeason: JellyfinItemDto? = null,
    val episodes: List<JellyfinItemDto> = emptyList(),
    val isLoadingDetail: Boolean = false
)

class JellyfinViewModel(
    val repository: JellyfinRepository
) : ViewModel() {

    class Factory(private val repository: JellyfinRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JellyfinViewModel(repository) as T
        }
    }

    private val _uiState = MutableStateFlow(JellyfinUiState())
    val uiState: StateFlow<JellyfinUiState> = _uiState.asStateFlow()

    val activeServer: StateFlow<MediaServerConfig?> = repository.activeServerFlow

    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            repository.activeServerFlow.collect { server ->
                if (server != null && server.isConnected) {
                    loadLibraryData()
                }
            }
        }
    }

    fun setServerUrlInput(url: String) {
        _uiState.update { it.copy(serverUrlInput = url, errorMessage = null) }
    }

    fun setUsernameInput(username: String) {
        _uiState.update { it.copy(usernameInput = username, errorMessage = null) }
    }

    fun setPasswordInput(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun connectServer(onSuccess: (String) -> Unit) {
        val rawUrl = _uiState.value.serverUrlInput
        val validation = JellyfinClientFactory.validateServerUrl(rawUrl)
        if (validation.isFailure) {
            _uiState.update { it.copy(errorMessage = validation.exceptionOrNull()?.message ?: "Invalid URL") }
            return
        }

        val validUrl = validation.getOrThrow()
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            val result = repository.connectServer(validUrl)
            if (result.isSuccess) {
                val info = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        serverInfo = info,
                        serverUrlInput = validUrl,
                        errorMessage = null
                    )
                }
                onSuccess(validUrl)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to connect to server"
                _uiState.update { it.copy(isConnecting = false, errorMessage = err) }
            }
        }
    }

    fun login(serverUrl: String, onSuccess: () -> Unit) {
        val username = _uiState.value.usernameInput.trim()
        val password = _uiState.value.passwordInput
        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your Jellyfin username") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, errorMessage = null) }
            val result = repository.login(serverUrl, username, password)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoggingIn = false,
                        passwordInput = "",
                        errorMessage = null,
                        successMessage = "Connected successfully!"
                    )
                }
                loadLibraryData()
                onSuccess()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Authentication failed"
                _uiState.update { it.copy(isLoggingIn = false, errorMessage = err) }
            }
        }
    }

    fun initiateQuickConnect(serverUrl: String) {
        viewModelScope.launch {
            val result = repository.initiateQuickConnect(serverUrl)
            if (result.isSuccess) {
                val qc = result.getOrThrow()
                _uiState.update { it.copy(quickConnectInitiate = qc, isQuickConnectPolling = true) }
                startQuickConnectPolling(serverUrl, qc.secret)
            } else {
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Quick Connect unavailable")
                }
            }
        }
    }

    private fun startQuickConnectPolling(serverUrl: String, secret: String) {
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            // Poll every 3 seconds up to 5 minutes
            for (i in 0..100) {
                delay(3000)
                val check = repository.checkQuickConnect(serverUrl, secret)
                if (check.isSuccess && check.getOrThrow()) {
                    // Authenticated! Complete the login
                    val complete = repository.completeQuickConnect(serverUrl, secret)
                    if (complete.isSuccess) {
                        _uiState.update {
                            it.copy(
                                isQuickConnectPolling = false,
                                quickConnectInitiate = null,
                                successMessage = "Connected via Quick Connect!"
                            )
                        }
                        loadLibraryData()
                        break
                    }
                }
            }
            _uiState.update { it.copy(isQuickConnectPolling = false) }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    views = emptyList(),
                    continueWatching = emptyList(),
                    latestItems = emptyList(),
                    libraryItems = emptyList(),
                    selectedItemDetail = null
                )
            }
            onComplete()
        }
    }

    fun loadLibraryData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLibrary = true) }

            // 1. Load Views
            val viewsResult = repository.getUserViews()
            val views = viewsResult.getOrDefault(emptyList())

            // 2. Load Continue Watching
            val cwResult = repository.getContinueWatching(20)
            val continueWatching = cwResult.getOrDefault(emptyList())

            // 3. Load Latest Items
            val latestResult = repository.getLatestItems(null, 20)
            val latest = latestResult.getOrDefault(emptyList())

            // 4. Default to first view items if available
            val firstView = views.firstOrNull()
            val items = if (firstView != null) {
                repository.getItems(parentId = firstView.id, limit = 60).getOrDefault(emptyList())
            } else {
                repository.getItems(limit = 60).getOrDefault(emptyList())
            }

            _uiState.update {
                it.copy(
                    views = views,
                    selectedView = firstView,
                    continueWatching = continueWatching,
                    latestItems = latest,
                    libraryItems = items,
                    isLoadingLibrary = false
                )
            }
        }
    }

    fun selectView(view: JellyfinItemDto) {
        _uiState.update { it.copy(selectedView = view, isLoadingLibrary = true) }
        viewModelScope.launch {
            val itemsResult = repository.getItems(parentId = view.id, limit = 60)
            _uiState.update {
                it.copy(
                    libraryItems = itemsResult.getOrDefault(emptyList()),
                    isLoadingLibrary = false
                )
            }
        }
    }

    fun loadItemDetail(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, selectedItemDetail = null) }
            val detailResult = repository.getItemDetails(itemId)
            if (detailResult.isSuccess) {
                val item = detailResult.getOrThrow()
                var seasons = emptyList<JellyfinItemDto>()
                var episodes = emptyList<JellyfinItemDto>()

                if (item.type.equals("Series", ignoreCase = true)) {
                    seasons = repository.getSeasons(item.id).getOrDefault(emptyList())
                    val firstSeason = seasons.firstOrNull()
                    if (firstSeason != null) {
                        episodes = repository.getEpisodes(item.id, firstSeason.id).getOrDefault(emptyList())
                    }
                }

                _uiState.update {
                    it.copy(
                        selectedItemDetail = item,
                        seasons = seasons,
                        selectedSeason = seasons.firstOrNull(),
                        episodes = episodes,
                        isLoadingDetail = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "Failed to load details"
                    )
                }
            }
        }
    }

    fun selectSeason(season: JellyfinItemDto) {
        val currentSeries = _uiState.value.selectedItemDetail ?: return
        _uiState.update { it.copy(selectedSeason = season) }
        viewModelScope.launch {
            val eps = repository.getEpisodes(currentSeries.id, season.id).getOrDefault(emptyList())
            _uiState.update { it.copy(episodes = eps) }
        }
    }

    fun getImageUrl(itemId: String, type: String = "Primary", tag: String? = null, width: Int? = null): String {
        return repository.getImageUrl(itemId, type, tag, width)
    }

    override fun onCleared() {
        super.onCleared()
        quickConnectJob?.cancel()
    }
}
