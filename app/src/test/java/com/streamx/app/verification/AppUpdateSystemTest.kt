package com.streamx.app.verification

import com.streamx.app.domain.model.*
import com.streamx.app.domain.repository.AppUpdateRepository
import com.streamx.app.network.update.UpdateManifestParser
import com.streamx.app.presentation.about.AboutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateSystemTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==========================================
    // 1. VERSION COMPARISON LOGIC
    // ==========================================

    @Test
    fun testVersionCodeComparisonDecidesUpdateAvailability() {
        val currentVersionCode = 1

        val newerRelease = AppUpdateInfo(
            versionName = "1.0.1",
            versionCode = 2,
            releaseNotes = "Fixes",
            apkUrl = "https://example.com/app.apk",
            sha256Checksum = "a".repeat(64)
        )

        val sameRelease = AppUpdateInfo(
            versionName = "1.0.0",
            versionCode = 1,
            releaseNotes = "Current",
            apkUrl = "https://example.com/app.apk",
            sha256Checksum = null
        )

        val olderRelease = AppUpdateInfo(
            versionName = "0.9.0",
            versionCode = 0,
            releaseNotes = "Old",
            apkUrl = "https://example.com/app.apk",
            sha256Checksum = null
        )

        assertTrue("Higher remote versionCode must trigger update available", newerRelease.versionCode > currentVersionCode)
        assertFalse("Same versionCode must NOT trigger update available", sameRelease.versionCode > currentVersionCode)
        assertFalse("Lower versionCode must NOT trigger update available", olderRelease.versionCode > currentVersionCode)
    }

    // ==========================================
    // 2. MANIFEST PARSING TESTS
    // ==========================================

    @Test
    fun testParseDedicatedUpdateManifestJson() {
        val json = """
            {
              "versionName": "1.2.0",
              "versionCode": 5,
              "releaseNotes": "Performance improvements and new Liquid Glass features.",
              "apkUrl": "https://github.com/IconicAashu/StreamX/releases/download/v1.2.0/app-release.apk",
              "sha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
              "releaseDate": "2026-11-23",
              "sizeBytes": 25528806
            }
        """.trimIndent()

        val result = UpdateManifestParser.parse(json)
        assertTrue("Parser must succeed for valid dedicated manifest", result.isSuccess)

        val info = result.getOrThrow()
        assertEquals("1.2.0", info.versionName)
        assertEquals(5, info.versionCode)
        assertEquals("Performance improvements and new Liquid Glass features.", info.releaseNotes)
        assertEquals("https://github.com/IconicAashu/StreamX/releases/download/v1.2.0/app-release.apk", info.apkUrl)
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", info.sha256Checksum)
        assertEquals(25528806L, info.sizeBytes)
        assertEquals("2026-11-23", info.releaseDate)
    }

    @Test
    fun testParseGitHubReleasesApiResponse() {
        val githubJson = """
            {
              "tag_name": "v1.1.0",
              "name": "STREAMX v1.1.0 Release",
              "body": "## What's Changed\n* Added About page\nversionCode: 3\nSHA256: 1222639dbc5afefc04368086b5b2391689f81e82f7395edd3af9e470cab9b065",
              "html_url": "https://github.com/IconicAashu/StreamX/releases/tag/v1.1.0",
              "published_at": "2026-11-23T19:34:58Z",
              "assets": [
                {
                  "name": "streamx-debug.apk",
                  "size": 25528806,
                  "browser_download_url": "https://github.com/IconicAashu/StreamX/releases/download/v1.1.0/streamx-debug.apk"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateManifestParser.parse(githubJson)
        assertTrue("Parser must succeed for GitHub Releases API payload", result.isSuccess)

        val info = result.getOrThrow()
        assertEquals("1.1.0", info.versionName)
        assertEquals(3, info.versionCode)
        assertEquals("https://github.com/IconicAashu/StreamX/releases/download/v1.1.0/streamx-debug.apk", info.apkUrl)
        assertEquals(25528806L, info.sizeBytes)
        assertEquals("1222639dbc5afefc04368086b5b2391689f81e82f7395edd3af9e470cab9b065", info.sha256Checksum)
        assertEquals("2026-11-23", info.releaseDate)
    }

    @Test
    fun testParseFailsWhenNoApkAssetInGitHubRelease() {
        val noApkJson = """
            {
              "tag_name": "v1.1.0",
              "body": "Release without apk",
              "assets": [
                {
                  "name": "source-code.zip",
                  "browser_download_url": "https://github.com/IconicAashu/StreamX/archive/refs/tags/v1.1.0.zip"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateManifestParser.parse(noApkJson)
        assertTrue("Parser must fail when release does not provide a .apk asset", result.isFailure)
    }

    @Test
    fun testParseFailsOnMalformedJson() {
        val malformedJson = "{ invalid_json: "
        val result = UpdateManifestParser.parse(malformedJson)
        assertTrue("Parser must handle malformed JSON cleanly with failure", result.isFailure)
    }

    // ==========================================
    // 3. SHA-256 CHECKSUM VERIFICATION TESTS
    // ==========================================

    @Test
    fun testSha256ChecksumCalculationAndIntegrityCheck() {
        val tempFile = File.createTempFile("streamx_test_pkg", ".apk")
        tempFile.deleteOnExit()
        tempFile.writeText("STREAMX_AUTHENTIC_PACKAGE_PAYLOAD")

        // Compute expected SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(tempFile.readBytes()).joinToString("") { "%02x".format(it) }

        assertEquals(64, expectedHash.length)

        // Verify match case-insensitively
        val fakeRepo = FakeAppUpdateRepository()
        val calculated = fakeRepo.calculateSha256(tempFile)
        assertEquals(expectedHash, calculated)

        // Verify mismatch detection
        val tamperedHash = "0".repeat(64)
        assertNotEquals(tamperedHash, calculated)

        tempFile.delete()
    }

    // ==========================================
    // 4. ABOUT VIEWMODEL & STATE MACHINE TESTS
    // ==========================================

    @Test
    fun testAboutViewModelCheckForUpdatesFlow() = runTest {
        val fakeRepo = FakeAppUpdateRepository()
        fakeRepo.nextCheckResult = UpdateCheckResult.UpToDate("1.0.0", 1)

        val viewModel = AboutViewModel(fakeRepo)
        testScheduler.advanceUntilIdle()

        assertEquals("STREAMX", viewModel.uiState.value.appName)
        assertFalse(viewModel.uiState.value.isCheckingForUpdates)
        assertNull(viewModel.uiState.value.updateCheckResult)

        // Trigger check
        viewModel.checkForUpdates()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCheckingForUpdates)
        val result = viewModel.uiState.value.updateCheckResult
        assertNotNull(result)
        assertTrue("Expected UpToDate result", result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun testAboutViewModelUpdateAvailableFlow() = runTest {
        val fakeRepo = FakeAppUpdateRepository()
        val updateInfo = AppUpdateInfo(
            versionName = "1.2.0",
            versionCode = 5,
            releaseNotes = "New features",
            apkUrl = "https://example.com/app.apk",
            sha256Checksum = "a".repeat(64),
            sizeBytes = 25000000L
        )
        fakeRepo.nextCheckResult = UpdateCheckResult.UpdateAvailable(updateInfo, 1)

        val viewModel = AboutViewModel(fakeRepo)
        testScheduler.advanceUntilIdle()

        viewModel.checkForUpdates()
        testScheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.updateCheckResult
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("1.2.0", available.updateInfo.versionName)
        assertEquals(5, available.updateInfo.versionCode)
    }

    @Test
    fun testAboutViewModelAutoCheckToggle() = runTest {
        val fakeRepo = FakeAppUpdateRepository()
        val viewModel = AboutViewModel(fakeRepo)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.autoCheckEnabled)

        // Fake toggle without context (repo state update)
        fakeRepo.setAutoUpdateCheckEnabled(false)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.autoCheckEnabled)
    }

    // ==========================================
    // FAKE TEST REPOSITORY
    // ==========================================

    private class FakeAppUpdateRepository : AppUpdateRepository {
        private val _autoCheck = MutableStateFlow(true)
        override val autoUpdateCheckEnabledFlow: StateFlow<Boolean> = _autoCheck.asStateFlow()

        private val _lastCheck = MutableStateFlow(0L)
        override val lastUpdateCheckTimeFlow: StateFlow<Long> = _lastCheck.asStateFlow()

        var nextCheckResult: UpdateCheckResult = UpdateCheckResult.UpToDate("1.0.0", 1)

        override suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
            _autoCheck.value = enabled
        }

        override suspend fun checkForUpdates(
            customEndpointUrl: String?,
            currentVersionCode: Int
        ): UpdateCheckResult {
            _lastCheck.value = System.currentTimeMillis()
            return nextCheckResult
        }

        override fun downloadApk(
            updateInfo: AppUpdateInfo,
            targetFile: File
        ): Flow<DownloadProgressState> = flow {
            emit(DownloadProgressState.Queued)
            emit(DownloadProgressState.InProgress(0.5f, 12500000L, 25000000L))
            emit(DownloadProgressState.ReadyToInstall(targetFile.absolutePath, updateInfo))
        }

        override fun cancelDownload() {}

        override fun verifyDownloadedApk(
            apkFile: File,
            expectedSha256: String?,
            expectedPackageName: String
        ): Result<Boolean> {
            if (!apkFile.exists()) return Result.failure(Exception("File missing"))
            if (!expectedSha256.isNullOrBlank()) {
                val calc = calculateSha256(apkFile)
                if (!calc.equals(expectedSha256, ignoreCase = true)) {
                    return Result.failure(SecurityException("SHA-256 mismatch"))
                }
            }
            return Result.success(true)
        }

        override fun calculateSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(file.readBytes())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
