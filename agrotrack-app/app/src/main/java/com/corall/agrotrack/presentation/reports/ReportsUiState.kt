package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading

data class ReportsUiState(
    val isLoadingSensors: Boolean          = true,
    val sensors:          List<Sensor>     = emptyList(),
    val selectedSensors:  Set<Sensor>      = emptySet(),
    val range:            String           = "24h",
    val customFrom:       Long?            = null,
    val customTo:         Long?            = null,
    val isLoadingReport:  Boolean          = false,
    val hasGenerated:     Boolean          = false,
    // sensorId -> lecturas de ese sensor en el rango consultado
    val reports:          Map<Int, List<SensorReading>> = emptyMap(),
    val error:            String?          = null,
    // Descarga (HU15/16/18) — formato elegido; la exportación misma se arma
    // en el momento a partir de `reports` (ya está en memoria), así que no
    // hay llamada de red que "perder conexión" — no hace falta reintentar
    // nada por separado, siempre se puede volver a tocar "Descargar".
    val downloadFormat:   DownloadFormat   = DownloadFormat.PDF,
    val downloadError:    String?          = null,
) {
    val isCustomRange: Boolean get() = customFrom != null && customTo != null
}

enum class DownloadFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    CSV("CSV", "csv", "text/csv"),
    JSON("JSON", "json", "application/json"),
}
