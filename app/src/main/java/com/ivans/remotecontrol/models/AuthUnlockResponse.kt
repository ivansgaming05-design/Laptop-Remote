package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class AuthUnlockResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("session_token")
    val sessionToken: String? = null,

    @SerializedName("expires_in")
    val expiresIn: Int = 0,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("retry_after")
    val retryAfter: Int? = null
)