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
            sensorCount   = 6,
            status        = GatewayStatus.Online,
            lastReadingAt = System.currentTimeMillis() - 15_000,
            battery       = 73.0,
        )
    )

    // Sensor 1 (S-001) es el único con sensor físico conectado.
    // Los demás existen en el sistema pero no envían datos todavía.
    val sensors: List<Sensor> = listOf(
        Sensor(id = 1, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp A1",    identifier = "S-001", type = "temperature", unit = "°C", location = "Zona A – Sector 1", enable = true),
        Sensor(id = 2, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp A2",    identifier = "S-002", type = "temperature", unit = "°C", location = "Zona A – Sector 2", enable = true),
        Sensor(id = 3, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp B1",    identifier = "S-003", type = "temperature", unit = "°C", location = "Zona B – Sector 1", enable = true),
        Sensor(id = 4, gatewayId = 1, gatewayName = "Gateway Principal", name = "Temp B2",    identifier = "S-004", type = "temperature", unit = "°C", location = "Zona B – Sector 2", enable = true),
        Sensor(id = 5, gatewayId = 1, gatewayName = "Gateway Principal", name = "Humedad C1", identifier = "S-005", type = "humidity",    unit = "%",  location = "Zona C – Sector 1", enable = true),
        Sensor(id = 6, gatewayId = 1, gatewayName = "Gateway Principal", name = "Voltaje",    identifier = "S-006", type = "voltage",     unit = "V",  location = "Panel Principal",   enable = true),
    )

    fun latestReadings(gatewayId: Int): List<SensorReading> {
        if (gatewayId != 1) return emptyList()
        val now = System.currentTimeMillis()
        return listOf(
            SensorReading(
                id          = 1L,
                sensorId    = 1,
                gatewayId   = 1,
                sensorName  = "Temp A1",
                unit        = "°C",
                temperature = 24.5,
                voltage     = 3.82,
                battery     = 85.0,
                receivedAt  = now - 15_000,
                status      = SensorStatus.Normal,
            )
        )
    }

    fun lastReadingForSensor(sensorId: Int): SensorReading? {
        return latestReadings(gatewayId = 1).firstOrNull { it.sensorId == sensorId }
    }

    private val thresholdStore: MutableMap<Int, ThresholdConfig> = mutableMapOf(
        1 to ThresholdConfig(sensorId = 1, minThreshold = 10.0, maxThreshold = 30.0, alertsEnabled = true),
        2 to ThresholdConfig(sensorId = 2, minThreshold = 10.0, maxThreshold = 30.0, alertsEnabled = true),
        3 to ThresholdConfig(sensorId = 3, minThreshold =  8.0, maxThreshold = 28.0, alertsEnabled = true),
        4 to ThresholdConfig(sensorId = 4, minThreshold =  8.0, maxThreshold = 28.0, alertsEnabled = false),
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
