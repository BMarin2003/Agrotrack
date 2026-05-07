package com.corall.agrotrack.domain.repository

import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

interface TelemetryRepository {
    suspend fun getLatestReadings(gatewayId: Int): Result<List<SensorReading>>
    fun getCachedReadings(gatewayId: Int): Flow<List<SensorReading>>
    suspend fun cacheReading(reading: SensorReading)
    suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>>
    fun getCachedAlerts(): Flow<List<Alert>>
    suspend fun cacheAlert(alert: Alert)
    suspend fun resolveAlert(alertId: Long): Result<Unit>
}
