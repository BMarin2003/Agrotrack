package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ThresholdItemDto(
    @SerializedName("id")            val id:           Int?,
    @SerializedName("sensor_id")     val sensorId:     Int,
    @SerializedName("metric")        val metric:       String,
    @SerializedName("min_value")     val minValue:     Double?,
    @SerializedName("max_value")     val maxValue:     Double?,
    @SerializedName("alert_message") val alertMessage: String?,
    @SerializedName("enable")        val enable:       Boolean = true,
)

data class ThresholdUpsertDto(
    @SerializedName("sensor_id")     val sensorId:     Int,
    @SerializedName("metric")        val metric:       String,
    @SerializedName("min_value")     val minValue:     Double?,
    @SerializedName("max_value")     val maxValue:     Double?,
    @SerializedName("alert_message") val alertMessage: String? = null,
    @SerializedName("enable")        val enable:       Boolean,
)
