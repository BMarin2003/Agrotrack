package com.corall.agrotrack.presentation.sensors

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HU3: Cambiar el formato del timestamp de la última lectura (relativo ↔ absoluto)
 */
class TimestampFormatTest {

    private fun absoluteReadingText(receivedAt: Long?): String {
        if (receivedAt == null || receivedAt <= 0L) return "Sin lectura"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(receivedAt))
    }

    @Test
    fun `timestamp nulo muestra Sin lectura`() {
        assertEquals("Sin lectura", absoluteReadingText(null))
    }

    @Test
    fun `timestamp en 0 muestra Sin lectura`() {
        assertEquals("Sin lectura", absoluteReadingText(0L))
    }

    @Test
    fun `timestamp valido se formatea como fecha y hora absoluta`() {
        // 2026-01-15 10:30:00 UTC
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.set(2026, 0, 15, 10, 30, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val expected = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(calendar.time)
        assertEquals(expected, absoluteReadingText(calendar.timeInMillis))
    }
}
