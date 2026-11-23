package com.streamx.app.work

import android.content.Context
import androidx.work.*
import com.streamx.app.BuildConfig
import com.streamx.app.StreamXApp
import com.streamx.app.domain.model.UpdateCheckResult
import com.streamx.app.notification.NotificationHelper
import com.streamx.app.utils.StreamXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that inspects remote releases in the background.
 * Respects Android background execution limits and never downloads/installs automatically.
 */
class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "streamx_auto_update_check"
        private const val TAG = "UpdateCheckWorker"

        /**
         * Schedules recurring daily background update checks with network constraint.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            StreamXLog.d(TAG, "Scheduled periodic background update check worker")
        }

        /**
         * Cancels background update checks when disabled by user preference.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            StreamXLog.d(TAG, "Cancelled periodic background update check worker")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        StreamXLog.d(TAG, "Executing background update check...")

        val app = applicationContext as? StreamXApp
        val appUpdateRepo = app?.appContainer?.appUpdateRepository

        if (appUpdateRepo == null) {
            StreamXLog.w(TAG, "AppUpdateRepository unavailable in worker context")
            return@withContext Result.failure()
        }

        // Check if user disabled auto-checks
        if (!appUpdateRepo.autoUpdateCheckEnabledFlow.value) {
            StreamXLog.d(TAG, "Auto update check is disabled by user. Skipping.")
            return@withContext Result.success()
        }

        val checkResult = appUpdateRepo.checkForUpdates(
            currentVersionCode = BuildConfig.VERSION_CODE
        )

        when (checkResult) {
            is UpdateCheckResult.UpdateAvailable -> {
                StreamXLog.i(TAG, "Background check found new version: v${checkResult.updateInfo.versionName}")
                NotificationHelper.sendUpdateNotification(
                    context = applicationContext,
                    newVersionName = checkResult.updateInfo.versionName,
                    notesSnippet = checkResult.updateInfo.releaseNotes.take(120)
                )
                Result.success()
            }
            is UpdateCheckResult.UpToDate -> {
                StreamXLog.d(TAG, "Background check: up-to-date")
                Result.success()
            }
            is UpdateCheckResult.Error -> {
                StreamXLog.w(TAG, "Background check error: ${checkResult.message}")
                Result.retry()
            }
        }
    }
}
