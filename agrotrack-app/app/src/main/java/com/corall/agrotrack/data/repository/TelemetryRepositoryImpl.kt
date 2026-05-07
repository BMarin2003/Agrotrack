package com.corall.agrotrack.data.repository

import com.corall.agrotrack.data.local.dao.AlertDao
import com.corall.agrotrack.data.local.dao.SensorReadingDao
import com.corall.agrotrack.data.local.entity.AlertEntity
import com.corall.agrotrack.data.local.entity.SensorReadingEntity
import com.corall.agrotrack.data.remote.api.TelemetryApiService
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.repository.TelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TelemetryRepositoryImpl @Inject constructor(
    private val api: TelemetryApiService,
    private val readingDao: SensorReadingDao,
    private val alertDao: AlertDao,
) : TelemetryRepository {

    override suspend fun getLatestReadings(gatewayId: Int): Result<List<SensorReading>> = runCatching {
        val response = api.getLatestReadings(gatewayId)
        val body     = response.body() ?: error("Sin datos del servidor")
        body.map { dto ->
            SensorReading(
                sensorId    = dto.sensorId,
                gatewayId   = dto.gatewayId,
                sensorName  = dto.sensorName ?: "",
                unit        = dto.unit ?: "°C",
                temperature = dto.temperature,
                voltage     = dto.voltage,
                battery     = dto.battery,
                receivedAt  = System.currentTimeMillis(),
            )
        }
    }

    override fun getCachedReadings(gatewayId: Int): Flow<List<SensorReading>> =
        readingDao.observeLatestByGateway(gatewayId).map { entities ->
            entities.map { e ->
                val isOffline = System.currentTimeMillis() - e.receivedAt > 30_000
                SensorReading(
                    id          = e.id,
                    sensorId    = e.sensorId,
                    gatewayId   = e.gatewayId,
                    sensorName  = e.sensorName,
                    unit        = e.unit,
                    temperature = e.temperature,
                    voltage     = e.voltage,
                    battery     = e.battery,
                    receivedAt  = e.receivedAt,
                    status      = if (isOffline) SensorStatus.Offline else SensorStatus.Normal,
                )
            }
        }

    override suspend fun cacheReading(reading: SensorReading) {
        readingDao.insert(reading.toEntity())
    }

    override suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>> = runCatching {
        val response = api.getActiveAlerts(gatewayId)
        response.body()?.map { dto ->
            Alert(
                id = dto.id, sensorId = dto.sensorId, gatewayId = dto.gatewayId,
                type = dto.type, metric = dto.metric, value = dto.value,
                threshold = dto.threshold, message = dto.message,
                resolved = dto.resolved, createdAt = System.currentTimeMillis(),
            )
        } ?: emptyList()
    }

    override fun getCachedAlerts(): Flow<List<Alert>> =
        alertDao.observeActive().map { entities ->
            entities.map { e ->
                Alert(id = e.id, sensorId = e.sensorId, gatewayId = e.gatewayId,
                    type = e.type, metric = e.metric, value = e.value,
                    threshold = e.threshold, message = e.message,
                    resolved = e.resolved, createdAt = e.createdAt)
            }
        }

    override suspend fun cacheAlert(alert: Alert) {
        alertDao.insert(alert.toEntity())
    }

    override suspend fun resolveAlert(alertId: Long): Result<Unit> = runCatching {
        api.resolveAlert(alertId)
        alertDao.resolve(alertId)
    }

    private fun SensorReading.toEntity() = SensorReadingEntity(
        sensorId = sensorId, gatewayId = gatewayId, sensorName = sensorName,
        unit = unit, temperature = temperature, voltage = voltage,
        battery = battery, receivedAt = receivedAt,
    )

    private fun Alert.toEntity() = AlertEntity(
        id = id, sensorId = sensorId, gatewayId = gatewayId, type = type,
        metric = metric, value = value, threshold = threshold, message = message,
        resolved = resolved, createdAt = createdAt,
    )
}
