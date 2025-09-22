package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class AddAlarmRequest(
    @SerializedName("time")
    val time: String,

    @SerializedName("days")
    val days: List<Int> = emptyList()
)

data class AddCustomFunctionRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("color")
    val color: String,

    @SerializedName("script_content")
    val scriptContent: String
)