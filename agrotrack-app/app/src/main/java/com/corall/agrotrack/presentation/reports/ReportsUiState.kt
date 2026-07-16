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
) {
    val isCustomRange: Boolean get() = customFrom != null && customTo != null
}
