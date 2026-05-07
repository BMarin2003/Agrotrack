package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SensorReadingDto(
    @SerializedName("id")           val id: Long?,
    @SerializedName("sensor_id")    val sensorId: Int,
    @SerializedName("gateway_id")   val gatewayId: Int,
    @SerializedName("sensor_name")  val sensorName: String?,
    @SerializedName("unit")         val unit: String?,
    @SerializedName("temperature")  val temperature: Double?,
    @SerializedName("voltage")      val voltage: Double?,
    @SerializedName("battery")      val battery: Double?,
    @SerializedName("received_at")  val receivedAt: String?,
)

data class AlertDto(
    @SerializedName("id")          val id: Long,
    @SerializedName("sensor_id")   val sensorId: Int,
    @SerializedName("gateway_id")  val gatewayId: Int,
    @SerializedName("type")        val type: String,
    @SerializedName("metric")      val metric: String?,
    @SerializedName("value")       val value: Double?,
    @SerializedName("threshold")   val threshold: Double?,
    @SerializedName("message")     val message: String,
    @SerializedName("resolved")    val resolved: Boolean,
    @SerializedName("created_at")  val createdAt: String?,
)
