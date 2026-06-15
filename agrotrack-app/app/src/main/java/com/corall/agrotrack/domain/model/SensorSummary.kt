package com.corall.agrotrack.domain.model

data class SensorSummary(
    val id: Int,
    val gatewayId: Int,
    val gatewayName: String,
    val name: String,
    val identifier: String,
    val type: String,
    val unit: String,
    val location: String,
    val value: Double?,
    val voltage: Double?,
    val battery: Double?,
    val receivedAt: Long?,
    val status: SensorStatus,
)