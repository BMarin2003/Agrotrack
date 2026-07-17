package com.corall.agrotrack.domain.model

data class GeneralReport(
    val gateways: List<GeneralReportRow>,
    val totalGatewayCount: Int,
    val totalSensorCount: Int,
    val totalAlertCount: Int,
)

data class GeneralReportRow(
    val gatewayId: Int,
    val name: String,
    val location: String,
    val status: GatewayStatus,
    val connectivityMode: GatewayConnectivityMode,
    val pendingSyncCount: Int,
    val battery: Double?,
    val sensorCount: Int,
    val tempMin: Double?,
    val tempMax: Double?,
    val tempAvg: Double?,
    val alertsTotal: Int,
    val alertsUnresolved: Int,
)
