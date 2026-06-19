package com.corall.agrotrack.domain.usecase.gateway

import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.model.SensorSummary
import com.corall.agrotrack.domain.repository.GatewayRepository
import com.corall.agrotrack.domain.repository.TelemetryRepository
import javax.inject.Inject

class GetGatewaySensorsUseCase @Inject constructor(
    private val gatewayRepository: GatewayRepository,
    private val telemetryRepository: TelemetryRepository,
) {
    suspend operator fun invoke(gatewayId: Int): Result<List<SensorSummary>> = runCatching {
        val sensors = gatewayRepository.getSensorsByGateway(gatewayId).getOrThrow()
        val readings = telemetryRepository.getLatestReadings(gatewayId).getOrDefault(emptyList())
        val readingsBySensor = readings.associateBy { it.sensorId }
        val now = System.currentTimeMillis()

        sensors.map { sensor ->
            val reading = readingsBySensor[sensor.id]
            val receivedAt = reading?.receivedAt

            val status = when {
                !sensor.enable -> SensorStatus.Offline
                receivedAt == null -> SensorStatus.Offline
                now - receivedAt > 90_000 -> SensorStatus.Offline
                else -> reading.status
            }

            SensorSummary(
                id = sensor.id,
                gatewayId = sensor.gatewayId,
                gatewayName = sensor.gatewayName,
                name = sensor.name,
                identifier = sensor.identifier,
                type = sensor.type,
                unit = sensor.unit,
                location = sensor.location,
                value = reading?.temperature,
                voltage = reading?.voltage,
                battery = reading?.battery,
                receivedAt = receivedAt,
                status = status,
            )
        }
    }
}