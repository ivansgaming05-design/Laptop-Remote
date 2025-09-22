package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class TeamViewerInfo(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("password")
    val password: String = ""
)