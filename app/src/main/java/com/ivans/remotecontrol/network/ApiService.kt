package com.ivans.remotecontrol.network

import com.ivans.remotecontrol.models.Alarm
import com.ivans.remotecontrol.models.AuthChallenge
import com.ivans.remotecontrol.models.AuthStatus
import com.ivans.remotecontrol.models.AuthUnlockResponse
import com.ivans.remotecontrol.models.CustomFunction
import com.ivans.remotecontrol.models.SystemStatus
import com.ivans.remotecontrol.models.TeamViewerInfo
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // System Controls
    @POST("api/nightlight")
    suspend fun toggleNightlight(): Response<Map<String, Any>>

    @POST("api/display")
    suspend fun toggleDisplay(): Response<Map<String, Any>>

    @POST("api/screenshot")
    suspend fun takeScreenshot(): Response<Map<String, Any>>

    @POST("api/teamviewer")
    suspend fun getTeamViewer(): Response<TeamViewerInfo>

    @POST("api/selector")
    suspend fun openSelector(): Response<Map<String, Any>>

    @POST("api/handtracking")
    suspend fun toggleHandTracking(): Response<Map<String, Any>>

    @POST("api/lock")
    suspend fun lockSystem(): Response<Map<String, Any>>

    @POST("api/panic")
    suspend fun panicShutdown(): Response<Map<String, Any>>

    // System Status
    @GET("api/status")
    suspend fun getSystemStatus(): Response<SystemStatus>

    @POST("api/brightness/{level}")
    suspend fun setBrightness(@Path("level") level: Int): Response<Map<String, Any>>

    // Alarm Management
    @GET("api/alarms")
    suspend fun getAlarms(): Response<List<Alarm>>

    @POST("api/alarms")
    suspend fun addAlarm(@Body alarm: Alarm): Response<Map<String, Any>>

    @PUT("api/alarms/{id}")
    suspend fun updateAlarm(@Path("id") id: String, @Body alarm: Alarm): Response<Map<String, Any>>

    @DELETE("api/alarms/{id}")
    suspend fun deleteAlarm(@Path("id") id: String): Response<Map<String, Any>>

    // Custom Functions - Modified
    @GET("api/scripts")
    suspend fun getAvailableScripts(): Response<List<String>>

    @GET("api/custom-functions")
    suspend fun getCustomFunctions(): Response<List<CustomFunction>>

    @POST("api/song-request")
    suspend fun requestSong(@Body request: Map<String, String>): Response<Map<String, Any>>

    @POST("api/custom-functions")
    suspend fun createCustomFunction(@Body request: CreateCustomFunctionRequest): Response<Map<String, Any>>

    @POST("api/custom-functions/{id}/execute")
    suspend fun executeCustomFunction(@Path("id") id: String): Response<Map<String, Any>>

    @DELETE("api/custom-functions/{id}")
    suspend fun deleteCustomFunction(@Path("id") id: String): Response<Map<String, Any>>

    @DELETE("api/custom-functions/{id}")
    suspend fun removeCustomFunction(@Path("id") functionId: Int): Response<Unit>

    @POST("api/quit")
    suspend fun quitServer(): Response<Map<String, Any>>

    @GET("api/auth/status")
    suspend fun getAuthStatus(): Response<AuthStatus>

    @GET("api/auth/challenge")
    suspend fun getAuthChallenge(): Response<AuthChallenge>

    @POST("api/auth/unlock")
    suspend fun unlockServer(@Body request: Map<String, String>): Response<AuthUnlockResponse>

    // Connection Test
    @GET("api/ping")
    suspend fun ping(): Response<Map<String, String>>

}
data class CreateCustomFunctionRequest(
    val name: String,
    val color: String,
    val scriptFile: String,
    val iconName: String // Add this field
)