package com.corall.agrotrack.data.repository

import com.corall.agrotrack.data.local.dao.AlertDao
import com.corall.agrotrack.data.local.dao.SensorReadingDao
import com.corall.agrotrack.data.local.entity.AlertEntity
import com.corall.agrotrack.data.local.entity.SensorReadingEntity
import com.corall.agrotrack.data.mock.MockConfig
import com.corall.agrotrack.data.mock.MockData
import com.corall.agrotrack.data.remote.api.TelemetryApiService
import com.corall.agrotrack.data.remote.dto.AlertDto
import com.corall.agrotrack.data.remote.dto.GatewayReportDto
import com.corall.agrotrack.data.remote.dto.GatewayReportSensorDto
import com.corall.agrotrack.data.remote.dto.GeneralReportDto
import com.corall.agrotrack.data.remote.dto.GeneralReportGatewayRowDto
import com.corall.agrotrack.data.remote.dto.SensorReadingDto
import com.corall.agrotrack.data.remote.dto.ThresholdUpsertDto
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.GatewayConnectivityMode
import com.corall.agrotrack.domain.model.GatewayReport
import com.corall.agrotrack.domain.model.GatewayReportSensor
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.GeneralReport
import com.corall.agrotrack.domain.model.GeneralReportRow
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
            readings
                .sortedByDescending { it.receivedAt }
                .distinctBy { it.sensorId }
                .map { it.toDomain() }
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

    override suspend fun clearAllAlerts(): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) { alertDao.deleteAll(); return@runCatching }

        val response = api.clearAllAlerts()

        if (!response.isSuccessful) {
            error("No se pudieron eliminar las alertas")
        }

        alertDao.deleteAll()
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
            alertsEnabled = temp.enable,
        )
    }

    override suspend fun getReportHistory(sensorId: Int, from: String?, to: String?): Result<List<SensorReading>> = runCatching {
        val response = api.getReportHistory(sensorId, from, to)
        if (!response.isSuccessful) error("No se pudo cargar el historial")
        response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun getAlertHistory(gatewayId: Int, from: String?, to: String?): Result<List<Alert>> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.mockAlerts.toList()

        val response = api.getAlertHistory(gatewayId, from, to)
        if (!response.isSuccessful) error("No se pudo cargar el historial de alertas")
        response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun getGatewayReport(gatewayId: Int, from: String, to: String): Result<GatewayReport> = runCatching {
        val response = api.getGatewayReport(gatewayId, from, to)
        if (!response.isSuccessful) error("No se pudo generar el reporte del gateway")
        val dto = response.body() ?: error("Respuesta vacía del servidor")

        GatewayReport(
            gatewayId = dto.gateway.id,
            gatewayName = dto.gateway.name,
            gatewayLocation = dto.gateway.location.orEmpty(),
            connectivityMode = GatewayConnectivityMode.from(dto.gateway.connectivityMode),
            pendingSyncCount = dto.gateway.pendingSyncCount ?: 0,
            gatewayBattery = dto.gateway.battery,
            tempMin = dto.summary.tempMin,
            tempMax = dto.summary.tempMax,
            tempAvg = dto.summary.tempAvg,
            sensorCount = dto.summary.sensorCount,
            readingCount = dto.summary.readingCount,
            alertsTotal = dto.alerts.total,
            alertsResolved = dto.alerts.resolved,
            alertsUnresolved = dto.alerts.unresolved,
            sensors = dto.sensors.map { it.toDomain(gatewayId) },
        )
    }

    override suspend fun getGeneralReport(from: String, to: String): Result<GeneralReport> = runCatching {
        val response = api.getGeneralReport(from, to)
        if (!response.isSuccessful) error("No se pudo generar el reporte general")
        val dto = response.body() ?: error("Respuesta vacía del servidor")

        GeneralReport(
            gateways = dto.gateways.map { it.toDomain() },
            totalGatewayCount = dto.totals.gatewayCount,
            totalSensorCount = dto.totals.sensorCount,
            totalAlertCount = dto.totals.alertCount,
        )
    }

    override suspend fun updateThresholdConfig(config: ThresholdConfig): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) {
            MockData.saveThresholdConfig(config)
            return@runCatching
        }

        val response = api.upsertThreshold(
            ThresholdUpsertDto(
                sensorId = config.sensorId,
                metric   = "temperature",
                minValue = config.minThreshold,
                maxValue = config.maxThreshold,
                enable   = config.alertsEnabled,
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

    private fun GatewayReportSensorDto.toDomain(gatewayId: Int): GatewayReportSensor {
        return GatewayReportSensor(
            sensorId = id,
            name = name,
            unit = unit ?: "°C",
            tempMin = tempMin,
            tempMax = tempMax,
            tempAvg = tempAvg,
            readings = readings.map { r ->
                val receivedAtMillis = r.receivedAt.toEpochMillisOrZero()
                SensorReading(
                    id = r.id,
                    sensorId = id,
                    gatewayId = gatewayId,
                    sensorName = name,
                    unit = unit ?: "°C",
                    temperature = r.temperature,
                    voltage = null,
                    battery = null,
                    receivedAt = receivedAtMillis,
                    status = statusFromReceivedAt(receivedAtMillis),
                )
            },
        )
    }

    private fun GeneralReportGatewayRowDto.toDomain(): GeneralReportRow {
        return GeneralReportRow(
            gatewayId = id,
            name = name,
            location = location.orEmpty(),
            status = GatewayStatus.from(status, enable = true),
            connectivityMode = GatewayConnectivityMode.from(connectivityMode),
            pendingSyncCount = pendingSyncCount ?: 0,
            battery = battery,
            sensorCount = sensorCount,
            tempMin = tempMin,
            tempMax = tempMax,
            tempAvg = tempAvg,
            alertsTotal = alertsTotal,
            alertsUnresolved = alertsUnresolved,
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

        return if (System.currentTimeMillis() - receivedAt > 90_000) {
            SensorStatus.Offline
        } else {
            SensorStatus.Normal
        }
    }
}