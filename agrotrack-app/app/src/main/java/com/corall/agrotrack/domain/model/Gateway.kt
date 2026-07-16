package com.corall.agrotrack.domain.model

data class Gateway(
    val id: Int,
    val name: String,
    val identifier: String,
    val location: String,
    val enable: Boolean,
    val sensorCount: Int,
    val status: GatewayStatus,
    val lastReadingAt: Long?,
    val battery: Double? = null,
    val connectivityMode: GatewayConnectivityMode = GatewayConnectivityMode.Unknown,
    val pendingSyncCount: Int = 0,
    val nextMaintenanceDate: String? = null,
)