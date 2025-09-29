package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class AuthStatus(
    @SerializedName("locked")
    val locked: Boolean = false,

    @SerializedName("requires_auth")
    val requiresAuth: Boolean = false,

    @SerializedName("temporarily_locked")
    val temporarilyLocked: Boolean = false,

    @SerializedName("retry_after")
    val retryAfter: Int? = null,

    @SerializedName("failed_attempts")
    val failedAttempts: Int = 0
)