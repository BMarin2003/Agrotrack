package com.corall.agrotrack.presentation.calibration

import org.junit.Assert.*
import org.junit.Test

/**
 * HU: Calibración de sensor — fórmula T_corregida = T_raw × ganancia + intercepto
 */
class CalibrationLogicTest {

    private fun applyCalibration(raw: Double, gain: Double, intercept: Double) =
        raw * gain + intercept

    // ── Fórmula de calibración ────────────────────────────────────────────────

    @Test
    fun `calibracion identidad retorna el valor raw sin cambios`() {
        assertEquals(22.5, applyCalibration(22.5, gain = 1.0, intercept = 0.0), 0.001)
    }

    @Test
    fun `ganancia mayor a 1 escala el valor hacia arriba`() {
        // +2% de ganancia sobre 22.5°C → 22.95°C
        assertEquals(22.95, applyCalibration(22.5, gain = 1.02, intercept = 0.0), 0.001)
    }

    @Test
    fun `ganancia menor a 1 escala el valor hacia abajo`() {
        // -5% de ganancia sobre 20°C → 19°C
        assertEquals(19.0, applyCalibration(20.0, gain = 0.95, intercept = 0.0), 0.001)
    }

    @Test
    fun `intercepto positivo suma offset al valor corregido`() {
        assertEquals(24.5, applyCalibration(22.5, gain = 1.0, intercept = 2.0), 0.001)
    }

    @Test
    fun `intercepto negativo resta offset al valor corregido`() {
        assertEquals(21.3, applyCalibration(22.5, gain = 1.0, intercept = -1.2), 0.001)
    }

    @Test
    fun `ganancia e intercepto combinados se aplican correctamente`() {
        // (20.0 × 1.1) + 2.0 = 24.0
        assertEquals(24.0, applyCalibration(20.0, gain = 1.1, intercept = 2.0), 0.001)
    }

    @Test
    fun `calibracion de temperatura negativa funciona correctamente`() {
        // (-5.0 × 1.0) + (-0.5) = -5.5
        assertEquals(-5.5, applyCalibration(-5.0, gain = 1.0, intercept = -0.5), 0.001)
    }

    @Test
    fun `calibracion en cero grados con ganancia y offset`() {
        // (0.0 × 1.05) + 0.3 = 0.3
        assertEquals(0.3, applyCalibration(0.0, gain = 1.05, intercept = 0.3), 0.001)
    }

    // ── Validación de entrada del formulario ──────────────────────────────────

    @Test
    fun `ganancia como string numerico valido se parsea correctamente`() {
        val gain = "1.02".toDoubleOrNull()
        assertNotNull("Ganancia válida debe parsear", gain)
        assertEquals(1.02, gain!!, 0.0001)
    }

    @Test
    fun `ganancia vacia es invalida`() {
        assertNull("String vacío debe retornar null", "".toDoubleOrNull())
    }

    @Test
    fun `ganancia con letras es invalida`() {
        assertNull("String con letras debe retornar null", "1.x2".toDoubleOrNull())
    }

    @Test
    fun `intercepto negativo como string se parsea correctamente`() {
        val intercept = "-1.5".toDoubleOrNull()
        assertNotNull("Intercepto negativo válido debe parsear", intercept)
        assertEquals(-1.5, intercept!!, 0.0001)
    }

    @Test
    fun `intercepto cero como string es valido`() {
        val intercept = "0.0".toDoubleOrNull()
        assertNotNull(intercept)
        assertEquals(0.0, intercept!!, 0.0001)
    }

    @Test
    fun `ganancia cero produce temperatura cero mas intercepto`() {
        // Caso degenerado: ganancia=0 borra toda la lectura
        assertEquals(5.0, applyCalibration(22.5, gain = 0.0, intercept = 5.0), 0.001)
    }
}
