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
import com.ivans.remotecontrol.utils.PreferencesManager
import android.util.Log
import retrofit2.Response

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    // System Status
    private val _systemStatus = MutableLiveData<SystemStatus>()
    val systemStatus: LiveData<SystemStatus> = _systemStatus

    private val _teamViewerInfo = MutableLiveData<TeamViewerInfo>()
    val teamViewerInfo: LiveData<TeamViewerInfo> = _teamViewerInfo

    private val _customFunctions = MutableLiveData<List<CustomFunction>>()
    val customFunctions: LiveData<List<CustomFunction>> = _customFunctions

    // UI State
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Authentication State
    private val _authenticationRequired = MutableLiveData<Boolean>()
    val authenticationRequired: LiveData<Boolean> = _authenticationRequired

    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus

    // Screenshot
    private val _screenshotTaken = MutableLiveData<String?>()
    val screenshotTaken: LiveData<String?> = _screenshotTaken

    private var lastAuthCheck = 0L
    private val AUTH_CHECK_DEBOUNCE = 2000L

    init {
        // Set up ApiClient with preferences manager
        ApiClient.setPreferencesManager(preferencesManager)

        // Load initial data
        checkConnectionAndLoadData()
    }

    fun checkConnection(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "Testing connection to ${ApiClient.getCurrentUrl()}")
                val response = ApiClient.apiService.ping()
                val isConnected = response.isSuccessful
                Log.d("HomeViewModel", "Connection test result: $isConnected")

                _connectionStatus.value = if (isConnected) {
                    ConnectionStatus.CONNECTED
                } else {
                    ConnectionStatus.DISCONNECTED
                }

                callback(isConnected)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Connection test failed", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                callback(false)
            }
        }
    }

    fun checkConnectionAndLoadData() {

        val now = System.currentTimeMillis()
        if (now - lastAuthCheck < AUTH_CHECK_DEBOUNCE) {
            Log.d("HomeViewModel", "Auth check debounced")
            return
        }
        lastAuthCheck = now

        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.CONNECTING

            try {
                // First, test basic connectivity
                val connectionOk = ApiClient.testConnection()
                if (!connectionOk) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    _error.value = "Cannot connect to server"
                    return@launch
                }

                // Check authentication status
                val authResult = ApiClient.checkAuthStatus()
                when (authResult) {
                    ApiClient.AuthResult.UNLOCKED -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        loadSystemStatus()
                        loadCustomFunctions()
                    }
                    ApiClient.AuthResult.LOCKED,
                    ApiClient.AuthResult.TEMPORARILY_LOCKED -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _authenticationRequired.value = true
                    }
                    ApiClient.AuthResult.ERROR -> {
                        _connectionStatus.value = ConnectionStatus.ERROR
                        _error.value = "Failed to check authentication status"
                    }
                    else -> {
                        _connectionStatus.value = ConnectionStatus.ERROR
                        _error.value = "Unknown authentication state"
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error checking connection and auth", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                _error.value = "Connection error: ${e.message}"
            }
        }
    }

    fun performAuthentication(password: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val authResult = ApiClient.performAuthChallenge(password)

                when (authResult) {
                    ApiClient.AuthResult.SUCCESS -> {
                        _authenticationRequired.value = false
                        _connectionStatus.value = ConnectionStatus.CONNECTED

                        // Load data after successful auth
                        loadSystemStatus()
                        loadCustomFunctions()

                        callback(true, null)
                    }
                    ApiClient.AuthResult.INVALID_PASSWORD -> {
                        callback(false, "Incorrect password")
                    }
                    ApiClient.AuthResult.TEMPORARILY_LOCKED -> {
                        val lockoutTime = preferencesManager.getLockoutTimeRemaining()
                        val minutes = (lockoutTime / 60000).toInt()
                        callback(false, "Too many failed attempts. Try again in $minutes minutes.")
                    }
                    ApiClient.AuthResult.ERROR -> {
                        callback(false, "Authentication failed. Please try again.")
                    }
                    else -> {
                        callback(false, "Unknown authentication error")
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Authentication error", e)
                callback(false, "Connection error: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun <T> executeWithAuthCheck(
        apiCall: suspend () -> Response<T>,
        onSuccess: (T) -> Unit = {},
        onError: (String) -> Unit = { _error.value = it }
    ): Boolean {
        return try {
            val response = apiCall()

            when {
                response.isSuccessful -> {
                    response.body()?.let { onSuccess(it) }
                    true
                }
                response.code() == 401 || response.code() == 423 -> {
                    // Authentication required
                    _authenticationRequired.value = true
                    false
                }
                else -> {
                    onError("Request failed: ${response.code()}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "API call failed", e)
            onError("Network error: ${e.message}")
            false
        }
    }

    // ================================
    // SYSTEM CONTROLS
    // ================================

    fun toggleNightlight() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.toggleNightlight() },
                    onSuccess = {
                        loadSystemStatus() // Refresh status after action
                    },
                    onError = { _error.value = "Failed to toggle night light" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleDisplay() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.toggleDisplay() },
                    onSuccess = {
                        loadSystemStatus()
                    },
                    onError = { _error.value = "Failed to toggle display" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun takeScreenshot() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.takeScreenshot() },
                    onSuccess = { responseBody ->
                        responseBody["screenshot_id"]?.let { screenshotId ->
                            if (screenshotId is String) {
                                // Save to gallery
                                val serverUrl = ApiClient.getCurrentUrl()
                                val screenshotUrl = "${serverUrl}api/screenshots/$screenshotId"

                                val screenshotManager = ScreenshotManager(getApplication())
                                viewModelScope.launch {
                                    screenshotManager.downloadAndSaveScreenshot(screenshotUrl)
                                }

                                _screenshotTaken.value = screenshotId
                            }
                        }
                    },
                    onError = { _error.value = "Failed to take screenshot" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun getTeamViewer() {
        Log.d("HomeViewModel", "getTeamViewer() called")
        viewModelScope.launch {
            try {
                _loading.value = true
                Log.d("HomeViewModel", "Making TeamViewer API call")

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.getTeamViewer() },
                    onSuccess = { teamViewerInfo ->
                        Log.d("HomeViewModel", "TeamViewer response - ID: '${teamViewerInfo.id}', Password: '${teamViewerInfo.password}'")
                        _teamViewerInfo.value = teamViewerInfo

                        if (teamViewerInfo.id.isEmpty() || teamViewerInfo.password.isEmpty()) {
                            Log.d("HomeViewModel", "TeamViewer info incomplete - triggering retry button")
                            _error.value = "TeamViewer info incomplete"
                        } else {
                            Log.d("HomeViewModel", "TeamViewer info complete - showing ID/password buttons")
                            _error.value = null
                        }
                    },
                    onError = {
                        Log.d("HomeViewModel", "TeamViewer API failed - showing retry button")
                        _teamViewerInfo.value = TeamViewerInfo("", "")
                        _error.value = "Failed to get TeamViewer info"
                    }
                )
            } finally {
                _loading.value = false
                Log.d("HomeViewModel", "getTeamViewer() completed")
            }
        }
    }

    fun openSelector() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.openSelector() },
                    onError = { _error.value = "Failed to open selector" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleHandTracking() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.toggleHandTracking() },
                    onError = { _error.value = "Failed to toggle hand tracking" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun lockSystem() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.lockSystem() },
                    onError = { _error.value = "Failed to lock system" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun panicShutdown() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.panicShutdown() },
                    onError = { _error.value = "Failed to shutdown system" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun quitServer() {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.quitServer() },
                    onError = { _error.value = "Failed to quit server" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    // ================================
    // SYSTEM STATUS
    // ================================

    fun loadSystemStatus() {
        viewModelScope.launch {
            try {
                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.getSystemStatus() },
                    onSuccess = { status ->
                        _systemStatus.value = status
                    },
                    onError = { _error.value = "Error loading system status: $it" }
                )
            } catch (e: Exception) {
                _error.value = "Error loading system status: ${e.message}"
            }
        }
    }

    fun setBrightness(level: Int) {
        viewModelScope.launch {
            try {
                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.setBrightness(level) },
                    onSuccess = {
                        loadSystemStatus()
                    },
                    onError = { _error.value = "Failed to set brightness" }
                )
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            }
        }
    }

    // ================================
    // CUSTOM FUNCTIONS
    // ================================

    fun loadCustomFunctions() {
        viewModelScope.launch {
            try {
                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.getCustomFunctions() },
                    onSuccess = { functions ->
                        _customFunctions.value = functions
                    },
                    onError = { _error.value = "Error loading custom functions: $it" }
                )
            } catch (e: Exception) {
                _error.value = "Error loading custom functions: ${e.message}"
            }
        }
    }

    fun addCustomFunction(name: String, color: String, scriptFile: String, iconRes: Int) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val iconName = getIconNameFromResource(iconRes)
                val request = com.ivans.remotecontrol.network.CreateCustomFunctionRequest(
                    name, color, scriptFile, iconName
                )

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.createCustomFunction(request) },
                    onSuccess = {
                        loadCustomFunctions() // Reload the list
                    },
                    onError = { _error.value = "Failed to create custom function" }
                )
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

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.executeCustomFunction(functionId) },
                    onError = { _error.value = "Failed to execute custom function" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteCustomFunction(functionId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true

                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.deleteCustomFunction(functionId) },
                    onSuccess = {
                        loadCustomFunctions() // Reload the list
                    },
                    onError = { _error.value = "Failed to delete custom function" }
                )
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeCustomFunction(functionId: Int) {
        viewModelScope.launch {
            try {
                executeWithAuthCheck(
                    apiCall = { ApiClient.apiService.removeCustomFunction(functionId) },
                    onSuccess = {
                        loadCustomFunctions() // Reload the list to update UI
                    },
                    onError = { _error.value = "Failed to remove function" }
                )
            } catch (e: Exception) {
                _error.value = "Failed to remove function: ${e.message}"
            }
        }
    }

    // ================================
    // UTILITY METHODS
    // ================================

    fun clearError() {
        _error.value = null
    }

    fun clearAuthenticationRequired() {
        _authenticationRequired.value = false
    }

    fun refreshData() {
        checkConnectionAndLoadData()
    }

    fun getSessionTimeRemaining(): Long {
        return preferencesManager.getSessionTimeRemaining()
    }

    fun hasValidSession(): Boolean {
        return ApiClient.hasValidSession()
    }

    enum class ConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }
}