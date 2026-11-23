package com.streamx.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.streamx.app.R
import com.streamx.app.presentation.MainActivity
import com.streamx.app.utils.StreamXLog

object NotificationHelper {

    const val CHANNEL_GENERAL = "streamx_general"
    const val CHANNEL_WATCHLIST = "streamx_watchlist"
    private const val TEST_NOTIFICATION_ID = 1001
    private const val WATCHLIST_NOTIFICATION_ID = 1002
    private const val UPDATE_NOTIFICATION_ID = 1003

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Channel 1: General & Playback
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General & Playback",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General STREAMX updates, playback alerts, and system notices"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#5E5CE6")
            }

            // Channel 2: Watchlist & New Episodes
            val watchlistChannel = NotificationChannel(
                CHANNEL_WATCHLIST,
                "Watchlist & Releases",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New episode releases and watchlist availability alerts"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#5E5CE6")
            }

            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(watchlistChannel)
            StreamXLog.d("NotificationHelper", "Created STREAMX notification channels")
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendTestNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) {
            StreamXLog.w("NotificationHelper", "Cannot send test notification: permission not granted")
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("STREAMX Playback Ready")
            .setContentText("Notifications and playback engine are active and working.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            StreamXLog.d("NotificationHelper", "Posted test notification successfully")
            true
        } catch (e: SecurityException) {
            StreamXLog.e("NotificationHelper", "SecurityException posting notification: ${e.message}")
            false
        } catch (e: Exception) {
            StreamXLog.e("NotificationHelper", "Failed to post notification: ${e.message}")
            false
        }
    }

    fun sendWatchlistNotification(context: Context, title: String, message: String): Boolean {
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WATCHLIST)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(WATCHLIST_NOTIFICATION_ID, notification)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendUpdateNotification(context: Context, newVersionName: String, notesSnippet: String): Boolean {
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "about")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("STREAMX Update Available: v$newVersionName")
            .setContentText(notesSnippet.ifBlank { "A new version of STREAMX is available. Tap to install." })
            .setStyle(NotificationCompat.BigTextStyle().bigText("STREAMX v$newVersionName is available. Tap to review release notes and install."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
            true
        } catch (_: Exception) {
            false
        }
    }
}
