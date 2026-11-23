package com.streamx.app.presentation.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.BuildConfig
import com.streamx.app.domain.model.AppUpdateInfo
import com.streamx.app.domain.model.DownloadProgressState
import com.streamx.app.domain.model.UpdateCheckResult
import com.streamx.app.domain.repository.AppUpdateRepository
import com.streamx.app.utils.StreamXLog
import com.streamx.app.work.UpdateCheckWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AboutUiState(
    val appName: String = "STREAMX",
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    val isCheckingForUpdates: Boolean = false,
    val updateCheckResult: UpdateCheckResult? = null,
    val downloadProgressState: DownloadProgressState = DownloadProgressState.Idle,
    val autoCheckEnabled: Boolean = true,
    val lastCheckTimeMs: Long = 0L,
    val requiresInstallPermission: Boolean = false,
    val pendingApkFile: File? = null,
    val userNoticeMessage: String? = null
)

class AboutViewModel(
    private val appUpdateRepository: AppUpdateRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AboutViewModel"
    }

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            appUpdateRepository.autoUpdateCheckEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(autoCheckEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appUpdateRepository.lastUpdateCheckTimeFlow.collect { timeMs ->
                _uiState.update { it.copy(lastCheckTimeMs = timeMs) }
            }
        }
    }

    /**
     * Checks for updates by querying GitHub Releases API or configured manifest.
     */
    fun checkForUpdates(customUrl: String? = null) {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    downloadProgressState = DownloadProgressState.Idle,
                    userNoticeMessage = null
                )
            }

            val result = appUpdateRepository.checkForUpdates(
                customEndpointUrl = customUrl,
                currentVersionCode = BuildConfig.VERSION_CODE
            )

            _uiState.update {
                it.copy(
                    isCheckingForUpdates = false,
                    updateCheckResult = result
                )
            }
        }
    }

    /**
     * Initiates downloading of the new release APK with real-time byte tracking.
     */
    fun startDownload(context: Context, updateInfo: AppUpdateInfo) {
        downloadJob?.cancel()

        val updatesDir = File(context.cacheDir, "updates")
        val apkFile = File(updatesDir, "streamx-v${updateInfo.versionName}.apk")

        downloadJob = viewModelScope.launch {
            appUpdateRepository.downloadApk(updateInfo, apkFile).collect { progressState ->
                _uiState.update { it.copy(downloadProgressState = progressState) }

                if (progressState is DownloadProgressState.ReadyToInstall) {
                    _uiState.update { it.copy(pendingApkFile = apkFile) }
                    launchInstaller(context, apkFile)
                }
            }
        }
    }

    /**
     * Cancels an ongoing download and cleans up intermediate file.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        appUpdateRepository.cancelDownload()
        _uiState.update { it.copy(downloadProgressState = DownloadProgressState.Idle) }
    }

    /**
     * Prompts Android's standard package installer using FileProvider.
     */
    fun launchInstaller(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            _uiState.update {
                it.copy(userNoticeMessage = "Installer error: APK file missing or corrupted.")
            }
            return
        }

        // Check Unknown App Installation Permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                StreamXLog.w(TAG, "Install permission not granted for unknown sources.")
                _uiState.update {
                    it.copy(
                        requiresInstallPermission = true,
                        pendingApkFile = apkFile
                    )
                }
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            _uiState.update {
                it.copy(requiresInstallPermission = false)
            }
        } catch (e: Exception) {
            StreamXLog.e(TAG, "Failed to launch package installer: ${e.message}")
            _uiState.update {
                it.copy(userNoticeMessage = "Could not launch package installer: ${e.localizedMessage}")
            }
        }
    }

    fun dismissInstallPermissionDialog() {
        _uiState.update { it.copy(requiresInstallPermission = false) }
    }

    fun toggleAutoCheck(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            appUpdateRepository.setAutoUpdateCheckEnabled(enabled)
            if (enabled) {
                UpdateCheckWorker.schedule(context)
            } else {
                UpdateCheckWorker.cancel(context)
            }
        }
    }

    fun clearNoticeMessage() {
        _uiState.update { it.copy(userNoticeMessage = null) }
    }
}
