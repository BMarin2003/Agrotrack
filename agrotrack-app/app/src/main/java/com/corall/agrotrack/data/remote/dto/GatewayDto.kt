package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GatewayDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("identifier") val identifier: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("enable") val enable: Boolean?,
    @SerializedName("sensor_count") val sensorCount: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("last_reading_at") val lastReadingAt: String?,
    @SerializedName("battery") val battery: Double? = null,
    @SerializedName("connectivity_mode") val connectivityMode: String? = null,
)