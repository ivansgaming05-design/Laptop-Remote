package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class SystemStatus(
    @SerializedName("nightlight")
    val nightlight: Boolean = false,

    @SerializedName("brightness")
    val brightness: Int = 5,

    @SerializedName("display_mode")
    val displayMode: String = "external",

    @SerializedName("last_screenshot")
    val lastScreenshot: String = "",

    @SerializedName("teamviewer_info")
    val teamViewerInfo: TeamViewerInfo = TeamViewerInfo()
)