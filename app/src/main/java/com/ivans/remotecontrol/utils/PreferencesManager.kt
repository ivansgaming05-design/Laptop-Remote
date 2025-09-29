package com.ivans.remotecontrol.utils

import android.content.Context
import android.content.SharedPreferences
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
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
        private const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        private const val KEY_ENCRYPTION_KEY = "encryption_key"
        private const val KEY_LAST_AUTH_TIME = "last_auth_time"
        private const val KEY_AUTH_ATTEMPTS = "auth_attempts"

        // Security constants
        private const val ENCRYPTION_ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/ECB/PKCS1Padding"
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
        // Set session expiry time (10 minutes from now)
        val expiryTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        prefs.edit().putLong(KEY_SESSION_EXPIRES, expiryTime).apply()
    }

    fun getSessionToken(): String {
        val token = prefs.getString(KEY_SESSION_TOKEN, "") ?: ""

        // Check if session has expired
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
    // PASSWORD MANAGEMENT (ENCRYPTED)
    // ================================

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyString = prefs.getString(KEY_ENCRYPTION_KEY, null)

        return if (keyString != null) {
            // Decode existing key
            val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
            SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM)
        } else {
            // Generate new key
            val keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM)
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()

            // Save key for future use
            val encodedKey = Base64.encodeToString(secretKey.encoded, Base64.DEFAULT)
            prefs.edit().putString(KEY_ENCRYPTION_KEY, encodedKey).apply()

            secretKey
        }
    }

    private fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            android.util.Log.e("PreferencesManager", "Encryption failed", e)
            "" // Return empty string on failure
        }
    }

    private fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey())
            val encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("PreferencesManager", "Decryption failed", e)
            "" // Return empty string on failure
        }
    }

    fun setRememberedPassword(password: String) {
        if (password.isNotEmpty()) {
            val encryptedPassword = encrypt(password)
            prefs.edit().putString(KEY_REMEMBERED_PASSWORD, encryptedPassword).apply()
        } else {
            clearRememberedPassword()
        }
    }

    fun getRememberedPassword(): String {
        val encryptedPassword = prefs.getString(KEY_REMEMBERED_PASSWORD, "") ?: ""
        return if (encryptedPassword.isNotEmpty()) {
            decrypt(encryptedPassword)
        } else {
            ""
        }
    }

    fun clearRememberedPassword() {
        prefs.edit().remove(KEY_REMEMBERED_PASSWORD).apply()
    }

    fun hasRememberedPassword(): Boolean {
        return prefs.contains(KEY_REMEMBERED_PASSWORD) &&
                getRememberedPassword().isNotEmpty()
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
            Long.MAX_VALUE // Never authenticated
        }
    }

    fun shouldRequireAuth(hourlyInterval: Long = 3600000): Boolean { // 1 hour default
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
        return attempts >= 3 // Lock after 3 failed attempts
    }

    fun getLockoutTimeRemaining(): Long {
        val attempts = getFailedAttempts()
        if (attempts < 3) return 0

        // Progressive lockout: 5 min after 3 attempts, 30 min after 5 attempts
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

    fun isSecuritySetupComplete(): Boolean {
        return hasRememberedPassword() || getSessionToken().isNotEmpty()
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
            .remove(KEY_REMEMBERED_PASSWORD)
            .remove(KEY_ENCRYPTION_KEY)
            .remove(KEY_LAST_AUTH_TIME)
            .remove(KEY_AUTH_ATTEMPTS)
            .apply()
    }

    // ================================
    // DEBUG/LOGGING (Remove in production)
    // ================================

    fun getSecurityStatus(): Map<String, Any> {
        return mapOf(
            "hasSessionToken" to getSessionToken().isNotEmpty(),
            "sessionExpired" to isSessionExpired(),
            "sessionTimeRemaining" to getSessionTimeRemaining(),
            "hasRememberedPassword" to hasRememberedPassword(),
            "timeSinceLastAuth" to getTimeSinceLastAuth(),
            "failedAttempts" to getFailedAttempts(),
            "isTemporarilyLocked" to isTemporarilyLocked(),
            "lockoutTimeRemaining" to getLockoutTimeRemaining()
        )
    }

    fun printSecurityStatus() {
        val status = getSecurityStatus()
        android.util.Log.d("PreferencesManager", "Security Status: $status")
    }
}