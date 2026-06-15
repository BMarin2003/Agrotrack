package com.corall.agrotrack.domain.model

data class Sensor(
    val id: Int,
    val gatewayId: Int,
    val gatewayName: String,
    val name: String,
    val identifier: String,
    val type: String,
    val unit: String,
    val location: String,
    val enable: Boolean,
)