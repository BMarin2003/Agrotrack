package com.corall.agrotrack.presentation.thresholds

data class ThresholdsUiState(
    val isLoading:     Boolean = true,
    val sensorId:      Int     = 0,
    val minThreshold:  String  = "",
    val maxThreshold:  String  = "",
    val alertsEnabled: Boolean = false,
    val isSaving:      Boolean = false,
    val error:         String? = null,
    val saveSuccess:   Boolean = false,
)
