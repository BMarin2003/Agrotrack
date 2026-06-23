package com.corall.agrotrack.presentation.calibration

data class CalibrationUiState(
    val isLoading:   Boolean = true,
    val sensorId:    Int     = 0,
    val gain:        String  = "1.0",
    val intercept:   String  = "0.0",
    val notes:       String  = "",
    val lastApplied: String? = null,
    val isSaving:    Boolean = false,
    val success:     Boolean = false,
    val error:       String? = null,
)
