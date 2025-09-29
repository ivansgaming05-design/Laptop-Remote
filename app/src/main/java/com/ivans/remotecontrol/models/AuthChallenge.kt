package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class AuthChallenge(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("challenge")
    val challenge: String? = null,

    @SerializedName("expires_in")
    val expiresIn: Int = 0,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("retry_after")
    val retryAfter: Int? = null
)