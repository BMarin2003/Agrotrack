package com.corall.agrotrack.data.mock

import com.corall.agrotrack.domain.model.ThresholdConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockTemperatureSimulatorTest {

    private lateinit var sim: MockTemperatureSimulator

    @Before
    fun setUp() {
        sim = MockTemperatureSimulator()
        // Estado conocido: umbrales que no rompe la temp inicial (22.0°C)
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = 10.0, maxThreshold = 30.0, alertsEnabled = true)
        )
    }

    @Test
    fun `tick devuelve null cuando no hay evento`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        // temp inicial ~22.0, umbrales 10–30 → ningún evento
        val result = sim.tick(now = 1_000_000L)
        assertNull("No debería devolver alerta con probabilidades en 0 y temp dentro de umbrales", result)
    }

    @Test
    fun `tick devuelve anomalous_reading cuando spike forzado`() {
        sim.SPIKE_PROBABILITY   = 1.0
        sim.SILENCE_PROBABILITY = 0.0
        val alert = sim.tick(now = 1_000_000L)
        assertNotNull("Spike forzado debe generar alerta", alert)
        assertEquals("Tipo de alerta debe ser anomalous_reading", "anomalous_reading", alert!!.type)
        assertEquals("Métrica debe ser temperature", "temperature", alert.metric)
        assertEquals("sensorId debe ser 1", 1, alert.sensorId)
        assertEquals("gatewayId debe ser 1", 1, alert.gatewayId)
        assertFalse("Alert debe empezar no resuelta", alert.resolved)
    }

    @Test
    fun `tick devuelve threshold_exceeded bajo umbral minimo`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        // minThreshold muy alto → temp inicial siempre por debajo
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = 100.0, maxThreshold = 200.0, alertsEnabled = true)
        )
        val alert = sim.tick(now = 1_000_000L)
        assertNotNull("Temp bajo mínimo debe generar alerta", alert)
        assertEquals("Tipo de alerta debe ser threshold_exceeded", "threshold_exceeded", alert!!.type)
        assertTrue("Mensaje debe mencionar 'mínimo'", alert.message.contains("mínimo", ignoreCase = true))
    }

    @Test
    fun `tick devuelve threshold_exceeded sobre umbral maximo`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        // maxThreshold muy bajo → temp inicial siempre por encima
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = -100.0, maxThreshold = -50.0, alertsEnabled = true)
        )
        val alert = sim.tick(now = 1_000_000L)
        assertNotNull("Temp sobre máximo debe generar alerta", alert)
        assertEquals("Tipo de alerta debe ser threshold_exceeded", "threshold_exceeded", alert!!.type)
        assertTrue("Mensaje debe mencionar 'máximo'", alert.message.contains("máximo", ignoreCase = true))
    }

    @Test
    fun `tick no genera threshold cuando alertsEnabled es false`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 0.0
        MockData.saveThresholdConfig(
            ThresholdConfig(sensorId = 1, minThreshold = 100.0, maxThreshold = 200.0, alertsEnabled = false)
        )
        val alert = sim.tick(now = 1_000_000L)
        assertNull("No debe generar alerta cuando alertsEnabled es false", alert)
    }

    @Test
    fun `tick devuelve null en modo silencio con menos de 30s`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 1.0
        val t0 = 1_000_000L
        sim.tick(now = t0)                    // inicia silencio
        val result = sim.tick(now = t0 + 10_000L)  // solo han pasado 10s
        assertNull("Silencio < 30s debe devolver null", result)
    }

    @Test
    fun `tick devuelve sensor_offline en modo silencio con mas de 30s`() {
        sim.SPIKE_PROBABILITY   = 0.0
        sim.SILENCE_PROBABILITY = 1.0
        val t0 = 1_000_000L
        sim.tick(now = t0)                     // inicia silencio
        val alert = sim.tick(now = t0 + 31_000L)  // 31s después
        assertNotNull("Silencio > 30s debe generar sensor_offline", alert)
        assertEquals("Tipo de alerta debe ser sensor_offline", "sensor_offline", alert!!.type)
        assertEquals("sensorId debe ser 1", 1, alert.sensorId)
        assertFalse("Alert debe empezar no resuelta", alert.resolved)
    }

    @Test
    fun `currentTemp es legible desde fuera`() {
        val tempAntes = sim.currentTemp
        sim.tick(now = 1_000_000L)
        // currentTemp puede haber cambiado por drift — solo verificamos que es Double
        assertTrue("currentTemp debe ser un número válido (finito)", sim.currentTemp.isFinite())
        // y que efectivamente cambió (drift + noise siempre != 0)
        assertNotEquals("currentTemp debe cambiar después de tick por drift y noise", tempAntes, sim.currentTemp)
    }
}
