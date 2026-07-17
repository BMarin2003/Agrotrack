package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GatewayReportDto(
    @SerializedName("gateway") val gateway: GatewayReportGatewayDto,
    @SerializedName("summary") val summary: GatewayReportSummaryDto,
    @SerializedName("sensors") val sensors: List<GatewayReportSensorDto>,
    @SerializedName("alerts") val alerts: GatewayReportAlertsDto,
)

data class GatewayReportGatewayDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: String?,
    @SerializedName("connectivity_mode") val connectivityMode: String?,
    @SerializedName("pending_sync_count") val pendingSyncCount: Int?,
    @SerializedName("battery") val battery: Double?,
)

data class GatewayReportSummaryDto(
    @SerializedName("temp_min") val tempMin: Double?,
    @SerializedName("temp_max") val tempMax: Double?,
    @SerializedName("temp_avg") val tempAvg: Double?,
    @SerializedName("sensor_count") val sensorCount: Int,
    @SerializedName("reading_count") val readingCount: Int,
)

data class GatewayReportSensorDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("unit") val unit: String?,
    @SerializedName("temp_min") val tempMin: Double?,
    @SerializedName("temp_max") val tempMax: Double?,
    @SerializedName("temp_avg") val tempAvg: Double?,
    @SerializedName("readings") val readings: List<GatewayReportReadingDto>,
)

data class GatewayReportReadingDto(
    @SerializedName("id") val id: Long,
    @SerializedName("temperature") val temperature: Double?,
    @SerializedName("received_at") val receivedAt: String?,
)

data class GatewayReportAlertsDto(
    @SerializedName("total") val total: Int,
    @SerializedName("resolved") val resolved: Int,
    @SerializedName("unresolved") val unresolved: Int,
)
