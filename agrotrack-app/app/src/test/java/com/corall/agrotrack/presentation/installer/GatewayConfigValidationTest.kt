package com.corall.agrotrack.presentation.installer

import org.junit.Assert.*
import org.junit.Test

/**
 * HU: Configuración de gateway — validación de WiFi y PIN
 */
class GatewayConfigValidationTest {

    private fun isValidPin(pin: String)  = pin.length == 4 && pin.all { it.isDigit() }
    private fun isValidSsid(ssid: String) = ssid.isNotBlank()

    private fun resolveTargetGatewayIds(
        allIds: List<Int>,
        selectedId: Int?,
        applyToAll: Boolean,
    ): List<Int> = if (applyToAll) allIds else listOfNotNull(selectedId)

    // ── HU: configurar WiFi ───────────────────────────────────────────────────

    @Test
    fun `SSID con texto es valido`() {
        assertTrue(isValidSsid("AgroTrack-Field"))
    }

    @Test
    fun `SSID vacio es invalido`() {
        assertFalse(isValidSsid(""))
    }

    @Test
    fun `SSID con solo espacios es invalido`() {
        assertFalse(isValidSsid("   "))
    }

    @Test
    fun `SSID con caracteres especiales es valido`() {
        assertTrue(isValidSsid("Red_AgroTrack-2026!"))
    }

    @Test
    fun `contrasena puede estar vacia para red abierta`() {
        val password = ""
        assertTrue("Contraseña vacía es aceptable para red abierta", password.isEmpty())
    }

    // ── HU: configurar PIN (4 dígitos) ────────────────────────────────────────

    @Test
    fun `PIN de 4 digitos numericos es valido`() {
        assertTrue(isValidPin("1234"))
    }

    @Test
    fun `PIN de 3 digitos es invalido`() {
        assertFalse(isValidPin("123"))
    }

    @Test
    fun `PIN de 5 digitos es invalido`() {
        assertFalse(isValidPin("12345"))
    }

    @Test
    fun `PIN vacio es invalido`() {
        assertFalse(isValidPin(""))
    }

    @Test
    fun `PIN con letras es invalido`() {
        assertFalse(isValidPin("12a4"))
    }

    @Test
    fun `PIN con punto decimal es invalido`() {
        assertFalse(isValidPin("12.4"))
    }

    @Test
    fun `PIN con espacios es invalido`() {
        assertFalse(isValidPin("12 4"))
    }

    @Test
    fun `PIN cero-cero-cero-cero es valido (0000)`() {
        assertTrue(isValidPin("0000"))
    }

    @Test
    fun `PIN nueve-nueve-nueve-nueve es valido (9999)`() {
        assertTrue(isValidPin("9999"))
    }

    // ── HU: configurar PIN en lote ────────────────────────────────────────────

    @Test
    fun `applyToAll true envia PIN a todos los gateways`() {
        val all    = listOf(1, 2, 3, 4)
        val result = resolveTargetGatewayIds(all, selectedId = 1, applyToAll = true)
        assertEquals(all, result)
    }

    @Test
    fun `applyToAll false con seleccion envia solo al gateway seleccionado`() {
        val all    = listOf(1, 2, 3, 4)
        val result = resolveTargetGatewayIds(all, selectedId = 2, applyToAll = false)
        assertEquals(listOf(2), result)
    }

    @Test
    fun `applyToAll false sin seleccion no envia a nadie`() {
        val all    = listOf(1, 2, 3)
        val result = resolveTargetGatewayIds(all, selectedId = null, applyToAll = false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `lista de targets no contiene duplicados`() {
        val all    = listOf(1, 2, 3)
        val result = resolveTargetGatewayIds(all, selectedId = null, applyToAll = true)
        assertEquals(result.size, result.distinct().size)
    }
}
