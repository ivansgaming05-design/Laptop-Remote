package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName
import com.ivans.remotecontrol.models.CustomFunction
import com.ivans.remotecontrol.models.SystemStatus

data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("data")
    val data: T? = null,

    @SerializedName("output")
    val output: String? = null,

    @SerializedName("status")
    val status: SystemStatus? = null,

    @SerializedName("id")
    val id: String? = null,

    @SerializedName("password")
    val password: String? = null,

    @SerializedName("screenshot_id")
    val screenshotId: String? = null,

    @SerializedName("mode")
    val mode: String? = null,

    @SerializedName("functions")
    val functions: List<CustomFunction>? = null
)