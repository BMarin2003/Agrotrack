package com.corall.agrotrack.presentation.sensors

import org.junit.Assert.*
import org.junit.Test

/**
 * HU1: Aviso de voltaje bajo — umbrales 3.5V (bajo) / 3.3V (crítico)
 */
class BatteryWarningLevelTest {

    private enum class Level { NORMAL, LOW, CRITICAL }

    private fun batteryWarningLevel(voltage: Double): Level = when {
        voltage < 3.3 -> Level.CRITICAL
        voltage < 3.5 -> Level.LOW
        else          -> Level.NORMAL
    }

    @Test
    fun `voltaje 3_8V es normal`() {
        assertEquals(Level.NORMAL, batteryWarningLevel(3.8))
    }

    @Test
    fun `voltaje exactamente 3_5V es normal (limite no inclusivo hacia abajo)`() {
        assertEquals(Level.NORMAL, batteryWarningLevel(3.5))
    }

    @Test
    fun `voltaje 3_4V es bajo`() {
        assertEquals(Level.LOW, batteryWarningLevel(3.4))
    }

    @Test
    fun `voltaje exactamente 3_3V es critico (limite no inclusivo hacia abajo)`() {
        assertEquals(Level.CRITICAL, batteryWarningLevel(3.3))
    }

    @Test
    fun `voltaje 3_2V es critico`() {
        assertEquals(Level.CRITICAL, batteryWarningLevel(3.2))
    }

    @Test
    fun `voltaje muy alto (4_2V, full charge) es normal`() {
        assertEquals(Level.NORMAL, batteryWarningLevel(4.2))
    }
}
