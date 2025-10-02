package com.ivans.remotecontrol.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferencesManager(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create EncryptedSharedPreferences", e)
        // Fallback to regular SharedPreferences if encryption fails
        context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "SecurePreferencesManager"
        private const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        private const val KEY_BIOMETRIC_PASSWORD = "biometric_password"
    }

    // Remember Password (for autofill convenience)
    fun setRememberedPassword(password: String) {
        securePrefs.edit().putString(KEY_REMEMBERED_PASSWORD, password).apply()
    }

    fun getRememberedPassword(): String {
        return securePrefs.getString(KEY_REMEMBERED_PASSWORD, "") ?: ""
    }

    fun clearRememberedPassword() {
        securePrefs.edit().remove(KEY_REMEMBERED_PASSWORD).apply()
    }

    fun hasRememberedPassword(): Boolean {
        return getRememberedPassword().isNotEmpty()
    }

    // Biometric Password (separate storage for biometric auth)
    fun setBiometricPassword(password: String) {
        securePrefs.edit().putString(KEY_BIOMETRIC_PASSWORD, password).apply()
    }

    fun getBiometricPassword(): String {
        return securePrefs.getString(KEY_BIOMETRIC_PASSWORD, "") ?: ""
    }

    fun clearBiometricPassword() {
        securePrefs.edit().remove(KEY_BIOMETRIC_PASSWORD).apply()
    }

    fun hasBiometricPassword(): Boolean {
        return getBiometricPassword().isNotEmpty()
    }

    // Clear all secure data
    fun clearAll() {
        securePrefs.edit().clear().apply()
    }
}