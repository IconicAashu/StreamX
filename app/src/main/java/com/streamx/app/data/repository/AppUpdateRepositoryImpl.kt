package com.streamx.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.streamx.app.domain.model.AppUpdateInfo
import com.streamx.app.domain.model.DownloadProgressState
import com.streamx.app.domain.model.UpdateCheckResult
import com.streamx.app.domain.model.UpdateConfig
import com.streamx.app.domain.repository.AppUpdateRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import com.streamx.app.network.update.UpdateManifestParser
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppUpdateRepositoryImpl(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : AppUpdateRepository {

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val BUFFER_SIZE = 8192
    }

    private val isDownloadCancelled = AtomicBoolean(false)

    override val autoUpdateCheckEnabledFlow: StateFlow<Boolean> =
        userPreferencesRepository.autoUpdateCheckEnabledFlow

    override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        userPreferencesRepository.setAutoUpdateCheckEnabled(enabled)
    }

    override val lastUpdateCheckTimeFlow: StateFlow<Long> =
        userPreferencesRepository.lastUpdateCheckTimeFlow

    override suspend fun checkForUpdates(
        customEndpointUrl: String?,
        currentVersionCode: Int
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val primaryUrl = customEndpointUrl ?: UpdateConfig.GITHUB_RELEASES_API_URL
        userPreferencesRepository.setLastUpdateCheckTime(System.currentTimeMillis())

        try {
            StreamXLog.d(TAG, "Checking for updates at: $primaryUrl")
            val request = Request.Builder()
                .url(primaryUrl)
                .header("Accept", "application/vnd.github.v3+json, application/json")
                .header("User-Agent", "STREAMX-Updater/1.0")
                .build()

            val response = httpClient.newCall(request).execute()

            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                val parseResult = UpdateManifestParser.parse(body)
                if (parseResult.isSuccess) {
                    val updateInfo = parseResult.getOrThrow()
                    return@withContext evaluateVersion(updateInfo, currentVersionCode)
                }
            }

            // Fallback to secondary manifest URL if GitHub Release is 404 or unconfigured
            if (customEndpointUrl == null && (response.code == 404 || response.code == 403)) {
                StreamXLog.w(TAG, "GitHub API returned ${response.code}. Attempting fallback manifest...")
                val fallbackRequest = Request.Builder()
                    .url(UpdateConfig.DEFAULT_FALLBACK_MANIFEST_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", "STREAMX-Updater/1.0")
                    .build()

                try {
                    val fallbackResponse = httpClient.newCall(fallbackRequest).execute()
                    val fallbackBody = fallbackResponse.body?.string()
                    if (fallbackResponse.isSuccessful && !fallbackBody.isNullOrBlank()) {
                        val fallbackParsed = UpdateManifestParser.parse(fallbackBody)
                        if (fallbackParsed.isSuccess) {
                            return@withContext evaluateVersion(fallbackParsed.getOrThrow(), currentVersionCode)
                        }
                    }
                } catch (_: Exception) {}
            }

            if (response.code == 404) {
                return@withContext UpdateCheckResult.Error("No releases published yet on GitHub repository (${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPO}).")
            }
            if (response.code == 403) {
                return@withContext UpdateCheckResult.Error("GitHub API rate limit exceeded. Please try again later.")
            }

            UpdateCheckResult.Error("Failed to fetch release information (HTTP ${response.code}).")
        } catch (e: IOException) {
            StreamXLog.e(TAG, "Network failure checking update: ${e.message}")
            UpdateCheckResult.Error("Unable to connect to update server. Please check your internet connection.", e)
        } catch (e: Exception) {
            StreamXLog.e(TAG, "Unexpected error checking update: ${e.message}")
            UpdateCheckResult.Error("Update check failed: ${e.localizedMessage ?: "Unknown error"}", e)
        }
    }

    private fun evaluateVersion(updateInfo: AppUpdateInfo, currentVersionCode: Int): UpdateCheckResult {
        return if (updateInfo.versionCode > currentVersionCode) {
            StreamXLog.i(TAG, "Update available: v${updateInfo.versionName} (code=${updateInfo.versionCode} > current=$currentVersionCode)")
            UpdateCheckResult.UpdateAvailable(updateInfo, currentVersionCode)
        } else {
            StreamXLog.i(TAG, "Application is up-to-date (current=$currentVersionCode, remote=${updateInfo.versionCode})")
            UpdateCheckResult.UpToDate(updateInfo.versionName, currentVersionCode)
        }
    }

    override fun downloadApk(
        updateInfo: AppUpdateInfo,
        targetFile: File
    ): Flow<DownloadProgressState> = flow {
        isDownloadCancelled.set(false)
        emit(DownloadProgressState.Queued)

        try {
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.apkUrl)
                .header("User-Agent", "STREAMX-Downloader/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadProgressState.Failed("Download failed with HTTP ${response.code}: ${response.message}"))
                return@flow
            }

            val responseBody = response.body
                ?: run {
                    emit(DownloadProgressState.Failed("Response body was empty."))
                    return@flow
                }

            val contentLength = responseBody.contentLength().takeIf { it > 0 } ?: (updateInfo.sizeBytes ?: -1L)
            var bytesReadTotal = 0L

            responseBody.byteStream().use { inputStream: InputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    var lastEmittedPercent = -1

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        if (isDownloadCancelled.get()) {
                            targetFile.delete()
                            emit(DownloadProgressState.Failed("Download was cancelled."))
                            return@flow
                        }

                        outputStream.write(buffer, 0, read)
                        bytesReadTotal += read

                        if (contentLength > 0) {
                            val percent = ((bytesReadTotal * 100) / contentLength).toInt()
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(
                                    DownloadProgressState.InProgress(
                                        progress = bytesReadTotal.toFloat() / contentLength.toFloat(),
                                        bytesDownloaded = bytesReadTotal,
                                        totalBytes = contentLength
                                    )
                                )
                            }
                        } else {
                            emit(
                                DownloadProgressState.InProgress(
                                    progress = -1f,
                                    bytesDownloaded = bytesReadTotal,
                                    totalBytes = -1L
                                )
                            )
                        }
                    }
                    outputStream.flush()
                }
            }

            // Step 2: Verify SHA-256 and package signature
            emit(DownloadProgressState.VerifyingChecksum())
            val verifyResult = verifyDownloadedApk(targetFile, updateInfo.sha256Checksum)
            if (verifyResult.isFailure) {
                targetFile.delete()
                val errorMsg = verifyResult.exceptionOrNull()?.message ?: "Package verification failed."
                emit(DownloadProgressState.Failed(errorMsg, verifyResult.exceptionOrNull()))
                return@flow
            }

            emit(DownloadProgressState.ReadyToInstall(targetFile.absolutePath, updateInfo))
        } catch (e: CancellationException) {
            targetFile.delete()
            emit(DownloadProgressState.Failed("Download cancelled."))
        } catch (e: Exception) {
            targetFile.delete()
            StreamXLog.e(TAG, "Download failed: ${e.message}")
            emit(DownloadProgressState.Failed("Download error: ${e.localizedMessage ?: "Network failed"}", e))
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelDownload() {
        isDownloadCancelled.set(true)
    }

    override fun verifyDownloadedApk(
        apkFile: File,
        expectedSha256: String?,
        expectedPackageName: String
    ): Result<Boolean> {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            return Result.failure(IOException("Downloaded APK file is empty or does not exist."))
        }

        // 1. SHA-256 Checksum Verification
        if (!expectedSha256.isNullOrBlank()) {
            val calculatedHash = calculateSha256(apkFile)
            if (!calculatedHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                StreamXLog.e(TAG, "Checksum mismatch! Expected: $expectedSha256, got: $calculatedHash")
                return Result.failure(
                    SecurityException("SHA-256 checksum mismatch! The downloaded APK may be corrupted or tampered with.")
                )
            }
            StreamXLog.i(TAG, "SHA-256 checksum verified successfully: $calculatedHash")
        } else {
            StreamXLog.w(TAG, "Release manifest did not declare a SHA-256 hash. Skipping cryptographic digest check.")
        }

        // 2. Android Package Archive Verification
        val packageArchive = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_META_DATA
        ) ?: return Result.failure(
            IllegalArgumentException("Downloaded file is not a valid Android package archive (.apk).")
        )

        if (packageArchive.packageName != expectedPackageName) {
            return Result.failure(
                SecurityException("Package name mismatch! Expected '$expectedPackageName' but APK declared '${packageArchive.packageName}'.")
            )
        }

        return Result.success(true)
    }

    override fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val bytes = digest.digest()
        val hex = StringBuilder()
        for (b in bytes) {
            hex.append(String.format("%02x", b))
        }
        return hex.toString()
    }
}
