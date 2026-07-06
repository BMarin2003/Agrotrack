package com.corall.agrotrack.domain.model

import org.junit.Assert.*
import org.junit.Test

class GatewayConnectivityModeTest {

    @Test
    fun `wifi en minuscula se mapea a Wifi`() {
        assertEquals(GatewayConnectivityMode.Wifi, GatewayConnectivityMode.from("wifi"))
    }

    @Test
    fun `WIFI en mayuscula tambien se mapea a Wifi (case-insensitive)`() {
        assertEquals(GatewayConnectivityMode.Wifi, GatewayConnectivityMode.from("WIFI"))
    }

    @Test
    fun `sim se mapea a Sim`() {
        assertEquals(GatewayConnectivityMode.Sim, GatewayConnectivityMode.from("sim"))
    }

    @Test
    fun `valor nulo se mapea a Unknown`() {
        assertEquals(GatewayConnectivityMode.Unknown, GatewayConnectivityMode.from(null))
    }

    @Test
    fun `valor desconocido se mapea a Unknown`() {
        assertEquals(GatewayConnectivityMode.Unknown, GatewayConnectivityMode.from("bluetooth"))
    }
}
