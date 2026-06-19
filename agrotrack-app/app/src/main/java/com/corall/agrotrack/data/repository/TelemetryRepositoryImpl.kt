package com.corall.agrotrack.data.repository

import com.corall.agrotrack.data.local.dao.AlertDao
import com.corall.agrotrack.data.local.dao.SensorReadingDao
import com.corall.agrotrack.data.local.entity.AlertEntity
import com.corall.agrotrack.data.local.entity.SensorReadingEntity
import com.corall.agrotrack.data.mock.MockConfig
import com.corall.agrotrack.data.mock.MockData
import com.corall.agrotrack.data.remote.api.TelemetryApiService
import com.corall.agrotrack.data.remote.dto.AlertDto
import com.corall.agrotrack.data.remote.dto.SensorReadingDto
import com.corall.agrotrack.data.remote.dto.ThresholdUpsertDto
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.model.ThresholdConfig
import com.corall.agrotrack.domain.repository.TelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.OffsetDateTime
import javax.inject.Inject

class TelemetryRepositoryImpl @Inject constructor(
    private val api: TelemetryApiService,
    private val readingDao: SensorReadingDao,
    private val alertDao: AlertDao,
) : TelemetryRepository {

    override suspend fun getLatestReadings(gatewayId: Int): Result<List<SensorReading>> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.latestReadings(gatewayId)

        val response = api.getLatestReadings(gatewayId)

        if (!response.isSuccessful) {
            error("No se pudieron cargar las lecturas")
        }

        val readings = response.body().orEmpty().map { it.toDomain() }

        if (readings.isNotEmpty()) {
            readingDao.insertAll(readings.map { it.toEntity() })
        }

        readings
    }

    override suspend fun getLastReading(sensorId: Int): Result<SensorReading?> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.lastReadingForSensor(sensorId)

        val response = api.getLastReading(sensorId)
        if (response.code() == 404) return@runCatching null
        if (!response.isSuccessful) error("No se pudo obtener la última lectura")
        response.body()?.toDomain()
    }

    override fun getCachedReadings(gatewayId: Int): Flow<List<SensorReading>> {
        return readingDao.observeLatestByGateway(gatewayId).map { readings ->
            readings.map { it.toDomain() }
        }
    }

    override suspend fun cacheReading(reading: SensorReading) {
        readingDao.insert(reading.toEntity())
    }

    override suspend fun getActiveAlerts(gatewayId: Int): Result<List<Alert>> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.mockAlerts.toList()

        val response = api.getActiveAlerts(gatewayId)

        if (!response.isSuccessful) {
            error("No se pudieron cargar las alertas")
        }

        val alerts = response.body().orEmpty().map { it.toDomain() }

        if (alerts.isNotEmpty()) {
            alertDao.insertAll(alerts.map { it.toEntity() })
        }

        alerts
    }

    override fun getCachedAlerts(): Flow<List<Alert>> {
        return alertDao.observeActive().map { alerts ->
            alerts.map { it.toDomain() }
        }
    }

    override suspend fun cacheAlert(alert: Alert) {
        alertDao.insert(alert.toEntity())
    }

    override suspend fun resolveAlert(alertId: Long): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) { alertDao.resolve(alertId); return@runCatching }

        val response = api.resolveAlert(alertId)

        if (!response.isSuccessful) {
            error("No se pudo resolver la alerta")
        }

        alertDao.resolve(alertId)
    }

    override suspend fun getThresholdConfig(sensorId: Int): Result<ThresholdConfig?> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.getThresholdConfig(sensorId)

        val response = api.getThresholds(sensorId)
        if (!response.isSuccessful) error("No se pudo obtener la configuración de umbrales")
        val items = response.body().orEmpty()
        val temp  = items.firstOrNull { it.metric == "temperature" } ?: return@runCatching null
        ThresholdConfig(
            sensorId      = sensorId,
            minThreshold  = temp.minValue,
            maxThreshold  = temp.maxValue,
            alertsEnabled = temp.minValue != null || temp.maxValue != null,
        )
    }

    override suspend fun updateThresholdConfig(config: ThresholdConfig): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) {
            MockData.saveThresholdConfig(config)
            return@runCatching
        }

        // Validación HU-AL4: si alertsEnabled=false enviamos null en los valores para desactivar
        // el disparo de alertas, pero el cliente conserva la configuración localmente.
        val (minVal, maxVal) = if (config.alertsEnabled) {
            config.minThreshold to config.maxThreshold
        } else {
            null to null
        }

        val response = api.upsertThreshold(
            ThresholdUpsertDto(
                sensorId = config.sensorId,
                metric   = "temperature",
                minValue = minVal,
                maxValue = maxVal,
            )
        )
        if (!response.isSuccessful) error("No se pudo actualizar la configuración de umbrales")
    }

    private fun SensorReadingDto.toDomain(): SensorReading {
        val receivedAtMillis = receivedAt.toEpochMillisOrZero()

        return SensorReading(
            id = id ?: 0L,
            sensorId = sensorId,
            gatewayId = gatewayId,
            sensorName = sensorName.orEmpty(),
            unit = unit ?: "°C",
            temperature = temperature,
            voltage = voltage,
            battery = battery,
            receivedAt = receivedAtMillis,
            status = statusFromReceivedAt(receivedAtMillis),
        )
    }

    private fun SensorReading.toEntity(): SensorReadingEntity {
        return SensorReadingEntity(
            id = id,
            sensorId = sensorId,
            gatewayId = gatewayId,
            sensorName = sensorName,
            unit = unit,
            temperature = temperature,
            voltage = voltage,
            battery = battery,
            receivedAt = receivedAt,
            status = status.name,
        )
    }

    private fun SensorReadingEntity.toDomain(): SensorReading {
        return SensorReading(
            id = id,
            sensorId = sensorId,
            gatewayId = gatewayId,
            sensorName = sensorName,
            unit = unit,
            temperature = temperature,
            voltage = voltage,
            battery = battery,
            receivedAt = receivedAt,
            status = runCatching {
                SensorStatus.valueOf(status)
            }.getOrDefault(SensorStatus.Normal),
        )
    }

    private fun AlertDto.toDomain(): Alert {
        return Alert(
            id = id,
            sensorId = sensorId,
            gatewayId = gatewayId,
            type = type,
            metric = metric,
            value = value,
            threshold = threshold,
            message = message,
            resolved = resolved,
            createdAt = createdAt.toEpochMillisOrZero(),
        )
    }

    private fun Alert.toEntity(): AlertEntity {
        return AlertEntity(
            id = id,
            sensorId = sensorId,
            gatewayId = gatewayId,
            type = type,
            metric = metric,
            value = value,
            threshold = threshold,
            message = message,
            resolved = resolved,
            createdAt = createdAt,
        )
    }

    private fun AlertEntity.toDomain(): Alert {
        return Alert(
            id = id,
            sensorId = sensorId,
            gatewayId = gatewayId,
            type = type,
            metric = metric,
            value = value,
            threshold = threshold,
            message = message,
            resolved = resolved,
            createdAt = createdAt,
        )
    }

    private fun String?.toEpochMillisOrZero(): Long {
        return runCatching {
            if (this.isNullOrBlank()) {
                0L
            } else {
                OffsetDateTime.parse(this).toInstant().toEpochMilli()
            }
        }.getOrDefault(0L)
    }

    private fun statusFromReceivedAt(receivedAt: Long): SensorStatus {
        if (receivedAt <= 0L) return SensorStatus.Offline

        return if (System.currentTimeMillis() - receivedAt > 30_000) {
            SensorStatus.Offline
        } else {
            SensorStatus.Normal
        }
    }
}