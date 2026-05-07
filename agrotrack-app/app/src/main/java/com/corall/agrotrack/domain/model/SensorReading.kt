package com.corall.agrotrack.domain.model

data class SensorReading(
    val id: Long = 0,
    val sensorId: Int,
    val gatewayId: Int,
    val sensorName: String = "",
    val unit: String = "°C",
    val temperature: Double?,
    val voltage: Double?,
    val battery: Double?,
    val receivedAt: Long,       // epoch ms
    val status: SensorStatus = SensorStatus.Normal,
)
