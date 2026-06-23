package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CalibrationDto(
    @SerializedName("id")         val id:        Int?,
    @SerializedName("sensor_id")  val sensorId:  Int,
    @SerializedName("gain")       val gain:      Double,
    @SerializedName("intercept")  val intercept: Double,
    @SerializedName("notes")      val notes:     String?,
    @SerializedName("applied_at") val appliedAt: String?,
)

data class CalibrationSaveDto(
    @SerializedName("gain")      val gain:      Double,
    @SerializedName("intercept") val intercept: Double,
    @SerializedName("notes")     val notes:     String? = null,
)
