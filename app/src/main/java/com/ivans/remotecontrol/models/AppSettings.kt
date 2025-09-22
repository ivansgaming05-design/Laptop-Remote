package com.ivans.remotecontrol.models

data class AppSettings(
    val serverUrl: String = "",
    val autoConnect: Boolean = true,
    val darkTheme: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true
)