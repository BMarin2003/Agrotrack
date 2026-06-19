package com.corall.agrotrack.data.mock

import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.model.ThresholdConfig

object MockData {

    val gateways: List<Gateway> = listOf(
        Gateway(
            id            = 1,
            name          = "Gateway Principal",
            identifier    = "GW-TWARM-001",
            location      = "Almacén Central",
            enable        = true,
            sensorCount   = 1,
            status        = GatewayStatus.Online,
            lastReadingAt = System.currentTimeMillis() - 15_000,
            battery       = 73.0,
        )
    )

    // Solo sensor 1 (S-001) tiene hardware físico conectado.
    val sensors: List<Sensor> = listOf(
        Sensor(id = 1, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp A1", identifier = "S-001", type = "temperature", unit = "°C", location = "Zona A – Sector 1", enable = true),
    )

    // Temperatura actual del simulador — actualizada por MockAlertForegroundService en cada tick.
    @Volatile var currentSensorTemp: Double = 22.0
    @Volatile var lastReadingTimestamp: Long = System.currentTimeMillis() - 15_000

    fun latestReadings(gatewayId: Int): List<SensorReading> {
        if (gatewayId != 1) return emptyList()
        return listOf(
            SensorReading(
                id          = 1L,
                sensorId    = 1,
                gatewayId   = 1,
                sensorName  = "Temp A1",
                unit        = "°C",
                temperature = currentSensorTemp,
                voltage     = 3.82,
                battery     = 85.0,
                receivedAt  = lastReadingTimestamp,
                status      = SensorStatus.Normal,
            )
        )
    }

    fun lastReadingForSensor(sensorId: Int): SensorReading? {
        return latestReadings(gatewayId = 1).firstOrNull { it.sensorId == sensorId }
    }

    private val thresholdStore: MutableMap<Int, ThresholdConfig> = mutableMapOf(
        1 to ThresholdConfig(sensorId = 1, minThreshold = 20.5, maxThreshold = 25.0, alertsEnabled = true),
    )

    fun getThresholdConfig(sensorId: Int): ThresholdConfig =
        thresholdStore.getOrDefault(sensorId, ThresholdConfig(sensorId, null, null, false))

    fun saveThresholdConfig(config: ThresholdConfig) {
        thresholdStore[config.sensorId] = config
    }

    val mockAlerts: MutableList<Alert> = java.util.concurrent.CopyOnWriteArrayList()

    fun addMockAlert(alert: Alert) {
        mockAlerts.add(0, alert)
    }
}
