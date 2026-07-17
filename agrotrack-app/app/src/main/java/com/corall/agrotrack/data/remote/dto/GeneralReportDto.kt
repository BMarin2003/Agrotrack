package com.corall.agrotrack.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GeneralReportDto(
    @SerializedName("gateways") val gateways: List<GeneralReportGatewayRowDto>,
    @SerializedName("totals") val totals: GeneralReportTotalsDto,
)

data class GeneralReportGatewayRowDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: String?,
    @SerializedName("connectivity_mode") val connectivityMode: String?,
    @SerializedName("pending_sync_count") val pendingSyncCount: Int?,
    @SerializedName("battery") val battery: Double?,
    @SerializedName("status") val status: String?,
    @SerializedName("sensor_count") val sensorCount: Int,
    @SerializedName("temp_min") val tempMin: Double?,
    @SerializedName("temp_max") val tempMax: Double?,
    @SerializedName("temp_avg") val tempAvg: Double?,
    @SerializedName("alerts_total") val alertsTotal: Int,
    @SerializedName("alerts_unresolved") val alertsUnresolved: Int,
)

data class GeneralReportTotalsDto(
    @SerializedName("gateway_count") val gatewayCount: Int,
    @SerializedName("sensor_count") val sensorCount: Int,
    @SerializedName("alert_count") val alertCount: Int,
)
