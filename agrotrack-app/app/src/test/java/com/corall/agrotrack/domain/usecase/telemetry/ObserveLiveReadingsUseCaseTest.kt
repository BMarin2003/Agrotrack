package com.corall.agrotrack.domain.usecase.telemetry

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveLiveReadingsUseCaseTest {

    private val gson = Gson()

    @Test
    fun `evento sensor_recovered se parsea con sus datos completos`() {
        val raw = """
            {"type":"alert","data":{"id":42,"sensor_id":7,"gateway_id":1,"type":"sensor_recovered","message":"Sensor 7 volvió a transmitir"}}
        """.trimIndent()

        val event = parseLiveEvent(gson, raw)

        assertTrue(event is LiveEvent.NewAlert)
        val alert = (event as LiveEvent.NewAlert).data
        assertEquals("sensor_recovered", alert.type)
        assertEquals(7, alert.sensorId)
        assertEquals(1, alert.gatewayId)
        assertEquals("Sensor 7 volvió a transmitir", alert.message)
    }

    @Test
    fun `evento de lectura no se confunde con un evento de alerta`() {
        val raw = """{"type":"reading","data":{"sensor_id":7,"gateway_id":1,"temperature":21.5}}"""

        assertTrue(parseLiveEvent(gson, raw) is LiveEvent.Reading)
    }

    @Test
    fun `payload sin tipo reconocido se descarta`() {
        assertNull(parseLiveEvent(gson, """{"foo":"bar"}"""))
    }

    @Test
    fun `JSON malformado no lanza excepcion y se descarta`() {
        assertNull(parseLiveEvent(gson, "esto no es json"))
    }
}
