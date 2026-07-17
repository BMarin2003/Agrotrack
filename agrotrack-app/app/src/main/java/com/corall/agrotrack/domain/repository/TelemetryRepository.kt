package com.corall.agrotrack.domain.repository

import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.GatewayReport
import com.corall.agrotrack.domain.model.GeneralReport
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.ThresholdConfig
import kotlinx.coroutines.flow.Flow

interface TelemetryRepository {
    suspend fun getLatestReadings(gatewayId: Int): Result<List<SensorReading>>
    suspend fun getLastReading(sensorId: Int): Result<SensorReading?>
    fun getCachedReadings(gatewayId: Int): Flow<List<SensorReading>>
    suspend fun cacheReading(reading: SensorReading)
    suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>>
    fun getCachedAlerts(): Flow<List<Alert>>
    suspend fun cacheAlert(alert: Alert)
    suspend fun resolveAlert(alertId: Long): Result<Unit>
    suspend fun clearAllAlerts(): Result<Unit>
    suspend fun getThresholdConfig(sensorId: Int): Result<ThresholdConfig?>
    suspend fun updateThresholdConfig(config: ThresholdConfig): Result<Unit>
    suspend fun getReportHistory(sensorId: Int, from: String?, to: String?): Result<List<SensorReading>>
    suspend fun getAlertHistory(gatewayId: Int, from: String? = null, to: String? = null): Result<List<Alert>>
    suspend fun getGatewayReport(gatewayId: Int, from: String, to: String): Result<GatewayReport>
    suspend fun getGeneralReport(from: String, to: String): Result<GeneralReport>
}