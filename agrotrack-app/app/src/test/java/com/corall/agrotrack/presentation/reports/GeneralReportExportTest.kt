package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.GatewayConnectivityMode
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.GeneralReportRow
import org.junit.Assert.*
import org.junit.Test

class GeneralReportExportTest {

    private fun row(name: String, tempAvg: Double?) = GeneralReportRow(
        gatewayId = 1, name = name, location = "Ica", status = GatewayStatus.Online,
        connectivityMode = GatewayConnectivityMode.Wifi, pendingSyncCount = 0, battery = 80.0,
        sensorCount = 15, tempMin = 1.0, tempMax = 8.0, tempAvg = tempAvg,
        alertsTotal = 2, alertsUnresolved = 1,
    )

    @Test
    fun `CSV general tiene encabezado y una fila por gateway`() {
        val csv = buildGeneralCsv(listOf(row("Cámara Fría 1", 4.5), row("Cámara Fría 2", 5.1)))
        val lines = csv.trim().lines()
        assertEquals("gateway,estado,temp_min,temp_avg,temp_max,sensores,alertas,alertas_sin_resolver", lines[0])
        assertEquals(3, lines.size) // encabezado + 2 filas
    }

    @Test
    fun `CSV general maneja gateway sin lecturas en el periodo`() {
        val csv = buildGeneralCsv(listOf(row("Sin datos", null)))
        assertTrue(csv.contains("Sin datos,Online,1.0,,8.0,15,2,1"))
    }
}
