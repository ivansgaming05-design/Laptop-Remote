package com.ivans.remotecontrol.utils

object Constants {
    const val DEFAULT_SERVER_URL = "http://100.64.0.1:5792/"
    const val CONNECTION_TIMEOUT = 30000L
    const val READ_TIMEOUT = 30000L
    const val WRITE_TIMEOUT = 30000L

    // Request codes
    const val REQUEST_CODE_SETTINGS = 1001

    // Intent extras
    const val EXTRA_SERVER_URL = "extra_server_url"
    const val EXTRA_ALARM_ID = "extra_alarm_id"

    // Notification channels
    const val NOTIFICATION_CHANNEL_ID = "remote_control_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Remote Control"
    const val NOTIFICATION_CHANNEL_DESCRIPTION = "Notifications for remote control actions"
}