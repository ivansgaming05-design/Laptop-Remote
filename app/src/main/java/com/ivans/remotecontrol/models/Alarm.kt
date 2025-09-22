package com.ivans.remotecontrol.models

import com.google.gson.annotations.SerializedName

data class Alarm(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("hour")
    val hour: Int = 0,

    @SerializedName("minute")
    val minute: Int = 0,

    @SerializedName("days")
    val days: List<Int> = emptyList(),

    @SerializedName("enabled")
    val enabled: Boolean = true,

    @SerializedName("time")
    val time: String = ""
) {
    fun getFormattedTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun getDaysString(): String {
        if (days.isEmpty()) return "Once"
        if (days.size == 7) return "Everyday"

        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return days.sorted().joinToString(", ") { dayNames.getOrNull(it) ?: "" }
    }
}