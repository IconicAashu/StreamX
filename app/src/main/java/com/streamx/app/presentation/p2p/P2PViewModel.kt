package com.streamx.app.presentation.p2p

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streamx.app.p2p.engine.P2PSessionManager
import com.streamx.app.p2p.model.TorrentMetadata
import com.streamx.app.p2p.model.TorrentStatus
import com.streamx.app.p2p.safety.P2PSafetyManager
import com.streamx.app.p2p.safety.SafetyAuditResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class P2PUiState(
    val magnetInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedTorrentMetadata: TorrentMetadata? = null,
    val selectedFileIndex: Int = 0,
    val showAddDialog: Boolean = false,
    val showFileSelectDialog: Boolean = false,
    val showSafetyWarningDialog: Boolean = false,
    val safetyAudit: SafetyAuditResult? = null,
    val pendingMagnetUri: String? = null
)

class P2PViewModel(
    private val sessionManager: P2PSessionManager
) : ViewModel() {

    class Factory(private val sessionManager: P2PSessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return P2PViewModel(sessionManager) as T
        }
    }

    private val _uiState = MutableStateFlow(P2PUiState())
    val uiState: StateFlow<P2PUiState> = _uiState.asStateFlow()

    val activeTorrents: StateFlow<List<TorrentStatus>> = sessionManager.activeTorrentsFlow

    fun setMagnetInput(input: String) {
        _uiState.update { it.copy(magnetInput = input, errorMessage = null) }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, errorMessage = null) }
    }

    fun closeAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, magnetInput = "") }
    }

    fun submitMagnet(onReadyToStream: (String) -> Unit) {
        val uri = _uiState.value.magnetInput.trim()
        if (uri.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid magnet URI") }
            return
        }

        // Trigger safety audit & confirmation requirement
        _uiState.update {
            it.copy(
                showAddDialog = false,
                pendingMagnetUri = uri,
                showSafetyWarningDialog = true
            )
        }
    }

    fun confirmAddMagnet(onReadyToStream: (String) -> Unit) {
        val uri = _uiState.value.pendingMagnetUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showSafetyWarningDialog = false) }
            val result = sessionManager.addMagnet(uri)
            if (result.isSuccess) {
                val metadata = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedTorrentMetadata = metadata,
                        selectedFileIndex = metadata.primaryVideoFile?.index ?: 0,
                        showFileSelectDialog = metadata.files.size > 1,
                        successMessage = "Added torrent: ${metadata.name}",
                        pendingMagnetUri = null
                    )
                }
                if (metadata.files.size <= 1) {
                    onReadyToStream("p2p_${metadata.infoHash}_file_0")
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to add magnet",
                        pendingMagnetUri = null
                    )
                }
            }
        }
    }

    fun dismissSafetyDialog() {
        _uiState.update { it.copy(showSafetyWarningDialog = false, pendingMagnetUri = null) }
    }

    fun addSampleDeveloperTorrent(onReadyToStream: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Generate synthetic developer-owned ISO BMFF MP4 test video & torrent
            val videoBytes = P2PSafetyManager.generateSyntheticTestVideo(durationSeconds = 15)
            val (torrentBytes, metadata) = P2PSafetyManager.generateSyntheticTestTorrent(videoBytes, "developer_synthetic_test.mp4")

            // Prime the piece scheduler with the synthetic video bytes
            sessionManager.registerTorrent(metadata)
            val scheduler = sessionManager.getPieceScheduler(metadata.infoHash)
            if (scheduler != null) {
                val pieceLen = metadata.pieceLength
                for (p in 0 until metadata.pieceCount) {
                    val start = p * pieceLen
                    val end = (start + pieceLen).coerceAtMost(videoBytes.size)
                    val chunk = videoBytes.copyOfRange(start, end)
                    scheduler.markPieceCompleted(p, chunk)
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    selectedTorrentMetadata = metadata,
                    selectedFileIndex = 0,
                    successMessage = "Loaded Developer Synthetic Test Torrent (${metadata.name})",
                    showAddDialog = false
                )
            }
            onReadyToStream("p2p_${metadata.infoHash}_file_0")
        }
    }

    fun addTorrentBytes(bytes: ByteArray, onReadyToStream: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = sessionManager.addTorrentFile(bytes)
            if (result.isSuccess) {
                val metadata = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedTorrentMetadata = metadata,
                        selectedFileIndex = metadata.primaryVideoFile?.index ?: 0,
                        showFileSelectDialog = metadata.files.size > 1,
                        successMessage = "Loaded torrent file: ${metadata.name}",
                        showAddDialog = false
                    )
                }
                if (metadata.files.size <= 1) {
                    onReadyToStream("p2p_${metadata.infoHash}_file_0")
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to parse .torrent file"
                    )
                }
            }
        }
    }

    fun selectFileIndex(index: Int) {
        _uiState.update { it.copy(selectedFileIndex = index) }
    }

    fun confirmFileSelection(onReadyToStream: (String) -> Unit) {
        val meta = _uiState.value.selectedTorrentMetadata ?: return
        val fileIndex = _uiState.value.selectedFileIndex
        sessionManager.selectFileForStreaming(meta.infoHash, fileIndex)
        _uiState.update { it.copy(showFileSelectDialog = false) }
        onReadyToStream("p2p_${meta.infoHash}_file_$fileIndex")
    }

    fun dismissFileSelectDialog() {
        _uiState.update { it.copy(showFileSelectDialog = false) }
    }

    fun pauseTorrent(infoHash: String) {
        sessionManager.pauseTorrent(infoHash)
    }

    fun resumeTorrent(infoHash: String) {
        sessionManager.resumeTorrent(infoHash)
    }

    fun removeTorrent(infoHash: String, deleteFiles: Boolean = false) {
        sessionManager.removeTorrent(infoHash, deleteFiles)
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
