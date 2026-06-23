package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading

data class ReportsUiState(
    val isLoadingSensors: Boolean          = true,
    val sensors:          List<Sensor>     = emptyList(),
    val selectedSensor:   Sensor?          = null,
    val range:            String           = "24h",
    val isLoadingReport:  Boolean          = false,
    val readings:         List<SensorReading> = emptyList(),
    val error:            String?          = null,
)
