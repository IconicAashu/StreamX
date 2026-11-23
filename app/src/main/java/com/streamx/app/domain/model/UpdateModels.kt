package com.streamx.app.domain.model

/**
 * Metadata and details of a remote application update.
 */
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256Checksum: String?,
    val releaseDate: String? = null,
    val sizeBytes: Long? = null,
    val htmlUrl: String? = null
)

/**
 * Result of checking for available updates against a remote endpoint.
 */
sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val updateInfo: AppUpdateInfo,
        val currentVersionCode: Int
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : UpdateCheckResult

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : UpdateCheckResult
}

/**
 * Progress states during an in-flight APK download and verification.
 */
sealed interface DownloadProgressState {
    object Idle : DownloadProgressState
    object Queued : DownloadProgressState
    data class InProgress(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadProgressState
    data class VerifyingChecksum(val message: String = "Verifying package SHA-256 integrity...") : DownloadProgressState
    data class ReadyToInstall(
        val apkPath: String,
        val updateInfo: AppUpdateInfo
    ) : DownloadProgressState
    data class Failed(
        val reason: String,
        val cause: Throwable? = null
    ) : DownloadProgressState
}

/**
 * Central update endpoint configuration for official STREAMX GitHub Releases and manifests.
 */
object UpdateConfig {
    const val GITHUB_OWNER = "IconicAashu"
    const val GITHUB_REPO = "StreamX"
    const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val DEFAULT_FALLBACK_MANIFEST_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/releases/latest-manifest.json"
}
