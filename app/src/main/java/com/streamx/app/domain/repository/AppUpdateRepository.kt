package com.streamx.app.domain.repository

import com.streamx.app.domain.model.AppUpdateInfo
import com.streamx.app.domain.model.DownloadProgressState
import com.streamx.app.domain.model.UpdateCheckResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Repository governing remote update discovery, SHA-256 cryptographic package integrity,
 * APK streaming downloads, and package validation.
 */
interface AppUpdateRepository {
    val autoUpdateCheckEnabledFlow: StateFlow<Boolean>
    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean)

    val lastUpdateCheckTimeFlow: StateFlow<Long>

    suspend fun checkForUpdates(
        customEndpointUrl: String? = null,
        currentVersionCode: Int
    ): UpdateCheckResult

    fun downloadApk(
        updateInfo: AppUpdateInfo,
        targetFile: File
    ): Flow<DownloadProgressState>

    fun cancelDownload()

    fun verifyDownloadedApk(
        apkFile: File,
        expectedSha256: String?,
        expectedPackageName: String = "com.streamx.app"
    ): Result<Boolean>

    fun calculateSha256(file: File): String
}
