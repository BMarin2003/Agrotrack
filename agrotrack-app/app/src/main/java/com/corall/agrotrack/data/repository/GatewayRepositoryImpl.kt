package com.corall.agrotrack.data.repository

import com.corall.agrotrack.data.mock.MockConfig
import com.corall.agrotrack.data.mock.MockData
import com.corall.agrotrack.data.remote.api.SensorsApiService
import com.corall.agrotrack.data.remote.dto.GatewayDto
import com.corall.agrotrack.data.remote.dto.SensorAliasSaveDto
import com.corall.agrotrack.data.remote.dto.SensorDto
import com.corall.agrotrack.data.remote.dto.PinConfigDto
import com.corall.agrotrack.data.remote.dto.WifiConfigDto
import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.GatewayConnectivityMode
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.repository.GatewayRepository
import java.time.OffsetDateTime
import javax.inject.Inject

class GatewayRepositoryImpl @Inject constructor(
    private val api: SensorsApiService,
) : GatewayRepository {

    override suspend fun getGateways(): Result<List<Gateway>> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.gateways

        val response = api.getGateways()
        if (!response.isSuccessful) error("No se pudieron cargar los gateways")
        response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun getGatewayById(gatewayId: Int): Result<Gateway?> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.gateways.firstOrNull { it.id == gatewayId }

        val response = api.getGateways()
        if (!response.isSuccessful) error("No se pudo cargar el gateway")
        response.body().orEmpty().map { it.toDomain() }.firstOrNull { it.id == gatewayId }
    }

    override suspend fun getSensorsByGateway(gatewayId: Int): Result<List<Sensor>> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.sensors.filter { it.gatewayId == gatewayId }

        val response = api.getSensorsByGateway(gatewayId)
        if (!response.isSuccessful) error("No se pudieron cargar los sensores")
        response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun getSensorById(sensorId: Int): Result<Sensor> = runCatching {
        if (MockConfig.ENABLED) return@runCatching MockData.sensors.first { it.id == sensorId }

        val response = api.getSensorById(sensorId)
        if (!response.isSuccessful) error("No se pudo cargar el sensor")
        response.body()?.toDomain() ?: error("Sensor no encontrado")
    }

    override suspend fun updateGatewayWifi(gatewayId: Int, ssid: String, password: String?, security: String): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) return@runCatching

        val response = api.updateGatewayWifi(gatewayId, WifiConfigDto(ssid, password, security))
        if (!response.isSuccessful) error("No se pudo actualizar la configuración WiFi")
    }

    override suspend fun updateGatewayPin(gatewayIds: List<Int>, pin: String): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) return@runCatching

        val response = api.setGatewayPin(PinConfigDto(gatewayIds, pin))
        if (!response.isSuccessful) error("No se pudo actualizar el PIN")
    }

    override suspend fun getSensorAlias(sensorId: Int): Result<String?> = runCatching {
        if (MockConfig.ENABLED) return@runCatching null

        val response = api.getSensorAlias(sensorId)
        if (!response.isSuccessful) error("No se pudo obtener el alias del sensor")
        response.body()?.alias
    }

    override suspend fun saveSensorAlias(sensorId: Int, alias: String): Result<Unit> = runCatching {
        if (MockConfig.ENABLED) return@runCatching

        val response = api.saveSensorAlias(sensorId, SensorAliasSaveDto(alias))
        if (!response.isSuccessful) error("No se pudo guardar el alias del sensor")
    }

    private fun GatewayDto.toDomain(): Gateway {
        val enabled = enable ?: true

        return Gateway(
            id = id,
            name = name,
            identifier = identifier.orEmpty(),
            location = location.orEmpty(),
            enable = enabled,
            sensorCount = sensorCount ?: 0,
            status = GatewayStatus.from(status, enabled),
            lastReadingAt = lastReadingAt.toEpochMillisOrNull(),
            battery = battery,
            connectivityMode = GatewayConnectivityMode.from(connectivityMode),
            pendingSyncCount = pendingSyncCount ?: 0,
        )
    }

    private fun SensorDto.toDomain(): Sensor {
        return Sensor(
            id = id,
            gatewayId = gatewayId,
            gatewayName = gateway.orEmpty(),
            name = name,
            identifier = identifier.orEmpty(),
            type = type ?: "temperature",
            unit = unit ?: "°C",
            location = location.orEmpty(),
            enable = enable ?: true,
        )
    }

    private fun String?.toEpochMillisOrNull(): Long? {
        return runCatching {
            if (this.isNullOrBlank()) null else OffsetDateTime.parse(this).toInstant().toEpochMilli()
        }.getOrNull()
    }
}