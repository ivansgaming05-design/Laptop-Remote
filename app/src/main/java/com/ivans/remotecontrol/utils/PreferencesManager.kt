package com.ivans.remotecontrol.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("remote_control_prefs", Context.MODE_PRIVATE)

    companion object {
        // Server settings
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://100.64.0.1:5792/"

        // App settings
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"

        // Security settings
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_EXPIRES = "session_expires"
        private const val KEY_LAST_AUTH_TIME = "last_auth_time"
        private const val KEY_AUTH_ATTEMPTS = "auth_attempts"

        // Biometric settings
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LAST_PASSWORD_AUTH_DATE = "last_password_auth_date"
    }

    // ================================
    // SERVER SETTINGS
    // ================================

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    // ================================
    // APP SETTINGS
    // ================================

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun getVibrationEnabled(): Boolean {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    // ================================
    // SESSION MANAGEMENT
    // ================================

    fun setSessionToken(token: String) {
        prefs.edit().putString(KEY_SESSION_TOKEN, token).apply()
        val expiryTime = System.currentTimeMillis() + (10 * 60 * 1000)
        prefs.edit().putLong(KEY_SESSION_EXPIRES, expiryTime).apply()
    }

    fun getSessionToken(): String {
        val token = prefs.getString(KEY_SESSION_TOKEN, "") ?: ""
        if (token.isNotEmpty() && isSessionExpired()) {
            clearSessionToken()
            return ""
        }
        return token
    }

    fun clearSessionToken() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SESSION_EXPIRES)
            .apply()
    }

    fun isSessionExpired(): Boolean {
        val expiryTime = prefs.getLong(KEY_SESSION_EXPIRES, 0)
        return expiryTime > 0 && System.currentTimeMillis() > expiryTime
    }

    fun getSessionTimeRemaining(): Long {
        val expiryTime = prefs.getLong(KEY_SESSION_EXPIRES, 0)
        val remaining = expiryTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    // ================================
    // BIOMETRIC SETTINGS
    // ================================

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setLastPasswordAuthDate(date: String = getCurrentDate()) {
        prefs.edit().putString(KEY_LAST_PASSWORD_AUTH_DATE, date).apply()
    }

    fun getLastPasswordAuthDate(): String {
        return prefs.getString(KEY_LAST_PASSWORD_AUTH_DATE, "") ?: ""
    }

    fun hasPasswordAuthToday(): Boolean {
        val lastAuthDate = getLastPasswordAuthDate()
        val today = getCurrentDate()
        return lastAuthDate == today
    }

    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.MONTH)}-${calendar.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    // ================================
    // AUTHENTICATION TRACKING
    // ================================

    fun setLastAuthTime(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_AUTH_TIME, timestamp).apply()
    }

    fun getLastAuthTime(): Long {
        return prefs.getLong(KEY_LAST_AUTH_TIME, 0)
    }

    fun getTimeSinceLastAuth(): Long {
        val lastAuth = getLastAuthTime()
        return if (lastAuth > 0) {
            System.currentTimeMillis() - lastAuth
        } else {
            Long.MAX_VALUE
        }
    }

    fun shouldRequireAuth(hourlyInterval: Long = 3600000): Boolean {
        return getTimeSinceLastAuth() > hourlyInterval
    }

    // ================================
    // FAILED ATTEMPTS TRACKING
    // ================================

    fun incrementFailedAttempts(): Int {
        val currentAttempts = getFailedAttempts()
        val newAttempts = currentAttempts + 1
        prefs.edit().putInt(KEY_AUTH_ATTEMPTS, newAttempts).apply()
        return newAttempts
    }

    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_AUTH_ATTEMPTS, 0)
    }

    fun clearFailedAttempts() {
        prefs.edit().remove(KEY_AUTH_ATTEMPTS).apply()
    }

    fun isTemporarilyLocked(): Boolean {
        val attempts = getFailedAttempts()
        return attempts >= 3
    }

    fun getLockoutTimeRemaining(): Long {
        val attempts = getFailedAttempts()
        if (attempts < 3) return 0

        val lockoutMinutes = when {
            attempts >= 5 -> 30
            attempts >= 3 -> 5
            else -> 0
        }

        val lastAuth = getLastAuthTime()
        val lockoutDuration = lockoutMinutes * 60 * 1000L
        val unlockTime = lastAuth + lockoutDuration
        val remaining = unlockTime - System.currentTimeMillis()

        return if (remaining > 0) remaining else 0
    }

    // ================================
    // SECURITY UTILITIES
    // ================================

    fun generateSecureToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    // ================================
    // GENERAL UTILITIES
    // ================================

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun clearSecurityData() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SESSION_EXPIRES)
            .remove(KEY_LAST_AUTH_TIME)
            .remove(KEY_AUTH_ATTEMPTS)
            .apply()
    }
}