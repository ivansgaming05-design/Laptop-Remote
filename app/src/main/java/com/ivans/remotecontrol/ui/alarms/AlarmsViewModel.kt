package com.ivans.remotecontrol.ui.alarms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivans.remotecontrol.models.Alarm
import com.ivans.remotecontrol.network.ApiClient
import kotlinx.coroutines.launch

class AlarmsViewModel : ViewModel() {

    private val _alarms = MutableLiveData<List<Alarm>>()
    val alarms: LiveData<List<Alarm>> = _alarms

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadAlarms() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.getAlarms()
                if (response.isSuccessful) {
                    _alarms.value = response.body() ?: emptyList()
                } else {
                    _error.value = "Failed to load alarms"
                }
            } catch (e: Exception) {
                _error.value = "Error loading alarms: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.addAlarm(alarm)
                if (response.isSuccessful) {
                    loadAlarms() // Reload the list
                } else {
                    _error.value = "Failed to add alarm"
                }
            } catch (e: Exception) {
                _error.value = "Error adding alarm: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateAlarm(id: String, alarm: Alarm) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.updateAlarm(id, alarm)
                if (response.isSuccessful) {
                    loadAlarms() // Reload the list
                } else {
                    _error.value = "Failed to update alarm"
                }
            } catch (e: Exception) {
                _error.value = "Error updating alarm: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteAlarm(id: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.deleteAlarm(id)
                if (response.isSuccessful) {
                    loadAlarms() // Reload the list
                } else {
                    _error.value = "Failed to delete alarm"
                }
            } catch (e: Exception) {
                _error.value = "Error deleting alarm: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}