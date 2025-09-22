package com.ivans.remotecontrol.network

import com.ivans.remotecontrol.models.*
import retrofit2.Response

class NetworkRepository {

    private val apiService = ApiClient.apiService

    // System Status
    suspend fun getSystemStatus(): Response<SystemStatus> {
        return apiService.getSystemStatus()
    }

    suspend fun setBrightness(level: Int): Response<Map<String, Any>> {
        return apiService.setBrightness(level)
    }

    // System Controls
    suspend fun toggleNightlight(): Response<Map<String, Any>> {
        return apiService.toggleNightlight()
    }

    suspend fun toggleDisplay(): Response<Map<String, Any>> {
        return apiService.toggleDisplay()
    }

    suspend fun takeScreenshot(): Response<Map<String, Any>> {
        return apiService.takeScreenshot()
    }

    suspend fun getTeamViewer(): Response<TeamViewerInfo> {
        return apiService.getTeamViewer()
    }

    suspend fun openSelector(): Response<Map<String, Any>> {
        return apiService.openSelector()
    }

    suspend fun toggleHandTracking(): Response<Map<String, Any>> {
        return apiService.toggleHandTracking()
    }

    suspend fun lockSystem(): Response<Map<String, Any>> {
        return apiService.lockSystem()
    }

    suspend fun panicShutdown(): Response<Map<String, Any>> {
        return apiService.panicShutdown()
    }

    // Alarms
    suspend fun getAlarms(): Response<List<Alarm>> {
        return apiService.getAlarms()
    }

    suspend fun addAlarm(alarm: Alarm): Response<Map<String, Any>> {
        return apiService.addAlarm(alarm)
    }

    suspend fun updateAlarm(id: String, alarm: Alarm): Response<Map<String, Any>> {
        return apiService.updateAlarm(id, alarm)
    }

    suspend fun deleteAlarm(id: String): Response<Map<String, Any>> {
        return apiService.deleteAlarm(id)
    }

    // Custom Functions
    suspend fun getAvailableScripts(): Response<List<String>> {
        return apiService.getAvailableScripts()
    }

    suspend fun getCustomFunctions(): Response<List<CustomFunction>> {
        return apiService.getCustomFunctions()
    }

    suspend fun createCustomFunction(request: CreateCustomFunctionRequest): Response<Map<String, Any>> {
        return apiService.createCustomFunction(request)
    }

    suspend fun executeCustomFunction(id: String): Response<Map<String, Any>> {
        return apiService.executeCustomFunction(id)
    }

    suspend fun deleteCustomFunction(id: String): Response<Map<String, Any>> {
        return apiService.deleteCustomFunction(id)
    }

    // Connection Test
    suspend fun ping(): Response<Map<String, String>> {
        return apiService.ping()
    }
}