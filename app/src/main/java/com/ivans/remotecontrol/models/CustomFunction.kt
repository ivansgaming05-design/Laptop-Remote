package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class CustomFunction(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("color")
    val color: String = "#6200EA",

    @SerializedName("script_path")
    val scriptPath: String = "",

    @SerializedName("created_at")
    val createdAt: String = "",

    @SerializedName("icon_name")
    val iconName: String? = null // Change from iconRes to iconName
)