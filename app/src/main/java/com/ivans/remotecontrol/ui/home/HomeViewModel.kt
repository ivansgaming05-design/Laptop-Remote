package com.ivans.remotecontrol.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivans.remotecontrol.network.ApiClient
import com.ivans.remotecontrol.models.CustomFunction
import com.ivans.remotecontrol.models.SystemStatus
import com.ivans.remotecontrol.models.TeamViewerInfo
import kotlinx.coroutines.launch
import android.app.Application
import com.ivans.remotecontrol.R
import com.ivans.remotecontrol.utils.ScreenshotManager

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _systemStatus = MutableLiveData<SystemStatus>()
    val systemStatus: LiveData<SystemStatus> = _systemStatus

    private val _teamViewerInfo = MutableLiveData<TeamViewerInfo>()
    val teamViewerInfo: LiveData<TeamViewerInfo> = _teamViewerInfo

    private val _customFunctions = MutableLiveData<List<CustomFunction>>()
    val customFunctions: LiveData<List<CustomFunction>> = _customFunctions

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadSystemStatus()
        loadCustomFunctions()
    }

    fun checkConnection(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                println("HomeViewModel: Testing connection to ${ApiClient.getCurrentUrl()}")
                val response = ApiClient.apiService.ping()
                println("HomeViewModel: Connection test result: ${response.isSuccessful}")
                callback(response.isSuccessful)
            } catch (e: Exception) {
                println("HomeViewModel: Connection test failed: ${e.message}")
                callback(false)
            }
        }
    }

    // System Controls
    fun toggleNightlight() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.toggleNightlight()
                if (response.isSuccessful) {
                    loadSystemStatus() // Refresh status after action
                } else {
                    _error.value = "Failed to toggle night light"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleDisplay() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.toggleDisplay()
                if (response.isSuccessful) {
                    loadSystemStatus()
                } else {
                    _error.value = "Failed to toggle display"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private val _screenshotTaken = MutableLiveData<String?>()
    val screenshotTaken: LiveData<String?> = _screenshotTaken

    fun takeScreenshot() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.takeScreenshot()
                if (response.isSuccessful) {
                    response.body()?.let { responseBody ->
                        val screenshotId = responseBody["screenshot_id"] as? String
                        if (screenshotId != null) {
                            // Save to gallery instead of showing dialog
                            val serverUrl = ApiClient.getCurrentUrl()
                            val screenshotUrl = "${serverUrl}api/screenshots/$screenshotId"

                            val screenshotManager = ScreenshotManager(getApplication())
                            screenshotManager.downloadAndSaveScreenshot(screenshotUrl)

                        } else {
                            _error.value = "Screenshot taken but no ID received"
                        }
                    } ?: run {
                        _error.value = "Screenshot response body is null"
                    }
                } else {
                    _error.value = "Failed to take screenshot"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }


    fun getTeamViewer() {
        android.util.Log.d("HomeViewModel", "getTeamViewer() called")
        viewModelScope.launch {
            try {
                _loading.value = true
                android.util.Log.d("HomeViewModel", "Making TeamViewer API call")
                val response = ApiClient.apiService.getTeamViewer()
                android.util.Log.d("HomeViewModel", "TeamViewer API response: success=${response.isSuccessful}, code=${response.code()}")

                if (response.isSuccessful) {
                    response.body()?.let { teamViewerInfo ->
                        android.util.Log.d("HomeViewModel", "TeamViewer response body - ID: '${teamViewerInfo.id}', Password: '${teamViewerInfo.password}'")

                        // Always post the info (even if empty) so the UI can decide what to show
                        _teamViewerInfo.value = teamViewerInfo

                        // Set error message if info is incomplete (this won't show in UI, just for logging)
                        if (teamViewerInfo.id.isEmpty() || teamViewerInfo.password.isEmpty()) {
                            android.util.Log.d("HomeViewModel", "TeamViewer info incomplete - triggering retry button")
                            _error.value = "TeamViewer info incomplete"
                        } else {
                            android.util.Log.d("HomeViewModel", "TeamViewer info complete - showing ID/password buttons")
                            _error.value = null
                        }
                    } ?: run {
                        // No response body
                        android.util.Log.d("HomeViewModel", "No TeamViewer response body - showing retry button")
                        _teamViewerInfo.value = TeamViewerInfo("", "")
                        _error.value = "No TeamViewer response"
                    }
                } else {
                    android.util.Log.d("HomeViewModel", "TeamViewer API failed - showing retry button")
                    _teamViewerInfo.value = TeamViewerInfo("", "")
                    _error.value = "Failed to get TeamViewer info"
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "TeamViewer API exception: ${e.message}", e)
                _teamViewerInfo.value = TeamViewerInfo("", "")
                _error.value = "Error getting TeamViewer info: ${e.message}"
            } finally {
                _loading.value = false
                android.util.Log.d("HomeViewModel", "getTeamViewer() completed")
            }
        }
    }

    fun openSelector() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.openSelector()
                if (!response.isSuccessful) {
                    _error.value = "Failed to open selector"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleHandTracking() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.toggleHandTracking()
                if (!response.isSuccessful) {
                    _error.value = "Failed to toggle hand tracking"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun lockSystem() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.lockSystem()
                if (!response.isSuccessful) {
                    _error.value = "Failed to lock system"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun panicShutdown() {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.panicShutdown()
                if (!response.isSuccessful) {
                    _error.value = "Failed to shutdown system"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    // System Status
    fun loadSystemStatus() {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getSystemStatus()
                if (response.isSuccessful) {
                    response.body()?.let {
                        _systemStatus.value = it
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error loading system status: ${e.message}"
            }
        }
    }

    fun setBrightness(level: Int) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.setBrightness(level)
                if (response.isSuccessful) {
                    loadSystemStatus()
                } else {
                    _error.value = "Failed to set brightness"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            }
        }
    }

    // Custom Functions - Modified
    fun loadCustomFunctions() {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getCustomFunctions()
                if (response.isSuccessful) {
                    response.body()?.let {
                        _customFunctions.value = it
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error loading custom functions: ${e.message}"
            }
        }
    }

    // Modified to accept script filename instead of script content
    fun addCustomFunction(name: String, color: String, scriptFile: String, iconRes: Int) {
        viewModelScope.launch {
            try {
                _loading.value = true

                // Convert iconRes to icon name
                val iconName = getIconNameFromResource(iconRes)

                val request = com.ivans.remotecontrol.network.CreateCustomFunctionRequest(
                    name, color, scriptFile, iconName
                )
                val response = ApiClient.apiService.createCustomFunction(request)
                if (response.isSuccessful) {
                    loadCustomFunctions() // Reload the list
                } else {
                    _error.value = "Failed to create custom function"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun getIconNameFromResource(iconRes: Int): String {
        return when (iconRes) {
            R.drawable.ic_add -> "ic_add"
            R.drawable.ic_home -> "ic_home"
            R.drawable.ic_settings -> "ic_settings"
            R.drawable.ic_screenshot -> "ic_screenshot"
            R.drawable.ic_display -> "ic_display"
            R.drawable.ic_lock -> "ic_lock"
            R.drawable.ic_hand -> "ic_hand"
            R.drawable.ic_moon -> "ic_moon"
            R.drawable.ic_sun -> "ic_sun"
            R.drawable.ic_alarm -> "ic_alarm"
            R.drawable.ic_teamviewer -> "ic_teamviewer"
            R.drawable.ic_selector -> "ic_selector"
            R.drawable.ic_clean -> "ic_clean"
            R.drawable.ic_quit -> "ic_quit"
            R.drawable.ic_panic -> "ic_panic"
            else -> "ic_add" // default
        }
    }

    fun executeCustomFunction(functionId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.executeCustomFunction(functionId)
                if (!response.isSuccessful) {
                    _error.value = "Failed to execute custom function"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteCustomFunction(functionId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                val response = ApiClient.apiService.deleteCustomFunction(functionId)
                if (response.isSuccessful) {
                    loadCustomFunctions() // Reload the list
                } else {
                    _error.value = "Failed to delete custom function"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeCustomFunction(functionId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.removeCustomFunction(functionId)
                if (response.isSuccessful) {
                    loadCustomFunctions() // Reload the list to update UI
                } else {
                    _error.value = "Failed to remove function"
                }
            } catch (e: Exception) {
                _error.value = "Failed to remove function: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}