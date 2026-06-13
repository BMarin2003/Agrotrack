package com.corall.agrotrack.domain.model

data class Alert(
    val id: Long,
    val sensorId: Int,
    val gatewayId: Int,
    val type: String,
    val metric: String?,
    val value: Double?,
    val threshold: Double?,
    val message: String,
    val resolved: Boolean,
    val createdAt: Long,
)