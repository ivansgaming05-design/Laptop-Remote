package com.ivans.remotecontrol.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ivans.remotecontrol.MainActivity
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.receivers.NotificationReceiver

class CustomNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferencesManager = PreferencesManager(context)

    companion object {
        private const val CHANNEL_ID = "remote_control_channel"
        private const val SCREENSHOT_NOTIFICATION_ID = 1001
        private const val TEAMVIEWER_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Control Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for remote control actions"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showScreenshotNotification(screenshotId: String) {
        val serverUrl = preferencesManager.getServerUrl()
        val screenshotUrl = "$serverUrl/screenshots/$screenshotId"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("show_screenshot", screenshotId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.ivans.remotecontrol.COPY_ACTION"
            putExtra("text", screenshotUrl)
            putExtra("label", "Screenshot URL")
        }

        val copyPendingIntent = PendingIntent.getBroadcast(
            context, 1, copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screenshot taken")
            .setContentText("Tap to view or copy URL")
            .setSmallIcon(R.drawable.ic_screenshot)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_screenshot, "View", openPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Copy URL", copyPendingIntent)
            .build()

        notificationManager.notify(SCREENSHOT_NOTIFICATION_ID, notification)
    }

    fun showTeamViewerNotification(id: String, password: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("teamviewer_id", id)
            putExtra("teamviewer_password", password)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIdIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.ivans.remotecontrol.COPY_ACTION"
            putExtra("text", id)
            putExtra("label", "TeamViewer ID")
        }

        val copyIdPendingIntent = PendingIntent.getBroadcast(
            context, 2, copyIdIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyPasswordIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.ivans.remotecontrol.COPY_ACTION"
            putExtra("text", password)
            putExtra("label", "TeamViewer Password")
        }

        val copyPasswordPendingIntent = PendingIntent.getBroadcast(
            context, 3, copyPasswordIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("TeamViewer Info")
            .setContentText("ID: $id | Password: $password")
            .setSmallIcon(R.drawable.ic_teamviewer)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "Copy ID", copyIdPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Copy Password", copyPasswordPendingIntent)
            .build()

        notificationManager.notify(TEAMVIEWER_NOTIFICATION_ID, notification)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    fun cancelScreenshotNotification() {
        notificationManager.cancel(SCREENSHOT_NOTIFICATION_ID)
    }

    fun cancelTeamViewerNotification() {
        notificationManager.cancel(TEAMVIEWER_NOTIFICATION_ID)
    }
}