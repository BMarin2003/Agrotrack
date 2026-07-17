package com.corall.agrotrack.domain.model

data class GatewayReport(
    val gatewayId: Int,
    val gatewayName: String,
    val gatewayLocation: String,
    val connectivityMode: GatewayConnectivityMode,
    val pendingSyncCount: Int,
    val gatewayBattery: Double?,
    val tempMin: Double?,
    val tempMax: Double?,
    val tempAvg: Double?,
    val sensorCount: Int,
    val readingCount: Int,
    val alertsTotal: Int,
    val alertsResolved: Int,
    val alertsUnresolved: Int,
    val sensors: List<GatewayReportSensor>,
)

data class GatewayReportSensor(
    val sensorId: Int,
    val name: String,
    val unit: String,
    val tempMin: Double?,
    val tempMax: Double?,
    val tempAvg: Double?,
    val readings: List<SensorReading>,
)
