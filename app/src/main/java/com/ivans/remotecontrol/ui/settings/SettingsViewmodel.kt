package com.ivans.remotecontrol.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun testConnection(url: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                // For now, just simulate a connection test
                // Replace this with actual API call when needed
                _connectionStatus.value = url.isNotEmpty() &&
                        (url.startsWith("http://") || url.startsWith("https://"))

            } catch (e: Exception) {
                _connectionStatus.value = false
                _error.value = "Connection test failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}