package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SensorDto(
    @SerializedName("id") val id: Int,
    @SerializedName("gateway_id") val gatewayId: Int,
    @SerializedName("gateway") val gateway: String?,
    @SerializedName("name") val name: String,
    @SerializedName("identifier") val identifier: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("unit") val unit: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("enable") val enable: Boolean?,
)