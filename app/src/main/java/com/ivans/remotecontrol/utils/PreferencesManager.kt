package com.ivans.remotecontrol.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("remote_control_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://192.168.1.100:5792"
    }

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}