package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.SensorReading
import org.junit.Assert.*
import org.junit.Test

class ReportExportUtilTest {

    private fun reading(temp: Double?, receivedAt: Long) = SensorReading(
        id = 1L, sensorId = 1, gatewayId = 1, sensorName = "S01", unit = "°C",
        temperature = temp, voltage = null, battery = null, receivedAt = receivedAt,
    )

    @Test
    fun `CSV incluye encabezado y una fila por lectura`() {
        val rows = listOf("Sensor A" to reading(4.5, 1_700_000_000_000L))
        val csv = ReportExportUtil.buildCsv(rows)
        assertTrue(csv.startsWith("sensor,fecha,temperatura_c\n"))
        assertTrue(csv.contains("Sensor A"))
        assertTrue(csv.contains("4.5"))
    }

    @Test
    fun `CSV escapa comas en el nombre del sensor`() {
        val rows = listOf("Sensor, con coma" to reading(1.0, 1_700_000_000_000L))
        val csv = ReportExportUtil.buildCsv(rows)
        assertTrue(csv.contains("\"Sensor, con coma\""))
    }

    @Test
    fun `JSON produce un array con la cantidad de filas esperada`() {
        val rows = listOf(
            "Sensor A" to reading(4.5, 1_700_000_000_000L),
            "Sensor B" to reading(5.1, 1_700_000_100_000L),
        )
        val json = ReportExportUtil.buildJson(rows)
        assertTrue(json.contains("Sensor A"))
        assertTrue(json.contains("Sensor B"))
        assertEquals(2, json.split("\"sensor\"").size - 1)
    }
}
