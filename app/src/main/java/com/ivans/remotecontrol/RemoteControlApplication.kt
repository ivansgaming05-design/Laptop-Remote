package com.ivans.remotecontrol

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class RemoteControlApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channels for Android 8.0+
        createNotificationChannels()

        // Initialize any other app-wide components here
        // Note: We don't initialize PreferencesManager here since we create instances as needed
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Remote Control notifications channel
            val remoteControlChannel = NotificationChannel(
                "remote_control_channel",
                "Remote Control Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for remote control actions like screenshots and TeamViewer info"
                enableLights(true)
                enableVibration(true)
            }

            // Screenshot notifications channel
            val screenshotChannel = NotificationChannel(
                "screenshot_channel",
                "Screenshot Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when screenshots are taken"
                enableLights(true)
                enableVibration(true)
            }

            // TeamViewer notifications channel
            val teamViewerChannel = NotificationChannel(
                "teamviewer_channel",
                "TeamViewer Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for TeamViewer connection information"
                enableLights(true)
                enableVibration(true)
            }

            // Register channels
            notificationManager.createNotificationChannel(remoteControlChannel)
            notificationManager.createNotificationChannel(screenshotChannel)
            notificationManager.createNotificationChannel(teamViewerChannel)
        }
    }
}