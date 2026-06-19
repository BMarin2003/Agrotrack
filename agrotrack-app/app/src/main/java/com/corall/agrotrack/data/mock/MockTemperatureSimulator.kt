package com.corall.agrotrack.data.mock

import com.corall.agrotrack.domain.model.Alert
import kotlin.random.Random

class MockTemperatureSimulator {

    var SPIKE_PROBABILITY   = 0.02
    var SILENCE_PROBABILITY = 0.05

    private val sensorId  = 1
    private val gatewayId = 1

    var currentTemp: Double = 22.0
        private set

    private var drift       = 0.3
    private var silentSince: Long? = null
    private var alertIdSeq  = 10_000L

    fun tick(now: Long = System.currentTimeMillis()): Alert? {
        // 1. MODO SILENCIO
        silentSince?.let { since ->
            return if (now - since > 30_000L) {
                if (Random.nextDouble() < 0.20) silentSince = null
                buildAlert(
                    type      = "sensor_offline",
                    metric    = null,
                    value     = null,
                    threshold = null,
                    message   = "Sensor $sensorId offline",
                )
            } else {
                null
            }
        }

        // 2. DRIFT + RUIDO
        currentTemp += drift + Random.nextDouble(-0.4, 0.4)
        if (currentTemp < -5.0 || currentTemp > 45.0) drift = -drift

        // 3. SPIKE
        if (Random.nextDouble() < SPIKE_PROBABILITY) {
            val sign  = if (Random.nextBoolean()) 1.0 else -1.0
            val delta = Random.nextDouble(8.0, 12.0) * sign
            currentTemp += delta
            return buildAlert(
                type      = "anomalous_reading",
                metric    = "temperature",
                value     = currentTemp,
                threshold = null,
                message   = "Lectura anómala: ${"%.1f".format(currentTemp)}°C",
            )
        }

        // 4. UMBRAL
        val config = MockData.getThresholdConfig(sensorId)
        if (config.alertsEnabled) {
            config.minThreshold?.let { min ->
                if (currentTemp < min) {
                    return buildAlert(
                        type      = "threshold_exceeded",
                        metric    = "temperature",
                        value     = currentTemp,
                        threshold = min,
                        message   = "temperature bajo umbral mínimo: ${"%.1f".format(currentTemp)} < $min",
                    )
                }
            }
            config.maxThreshold?.let { max ->
                if (currentTemp > max) {
                    return buildAlert(
                        type      = "threshold_exceeded",
                        metric    = "temperature",
                        value     = currentTemp,
                        threshold = max,
                        message   = "temperature sobre umbral máximo: ${"%.1f".format(currentTemp)} > $max",
                    )
                }
            }
        }

        // 5. INICIO DE SILENCIO
        if (Random.nextDouble() < SILENCE_PROBABILITY) {
            silentSince = now
        }

        return null
    }

    private fun buildAlert(
        type: String,
        metric: String?,
        value: Double?,
        threshold: Double?,
        message: String,
    ): Alert = Alert(
        id        = alertIdSeq++,
        sensorId  = sensorId,
        gatewayId = gatewayId,
        type      = type,
        metric    = metric,
        value     = value,
        threshold = threshold,
        message   = message,
        resolved  = false,
        createdAt = System.currentTimeMillis(),
    )
}
