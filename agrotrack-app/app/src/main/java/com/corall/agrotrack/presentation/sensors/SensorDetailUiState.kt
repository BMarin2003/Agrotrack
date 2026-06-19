package com.corall.agrotrack.presentation.sensors

import com.corall.agrotrack.domain.model.SensorStatus

data class SensorDetailUiState(
    val isLoading:   Boolean      = true,
    val sensorName:  String       = "",
    val sensorType:  String       = "temperature",
    val location:    String       = "",
    val unit:        String       = "°C",
    val temperature: Double?      = null,
    val voltage:     Double?      = null,
    val battery:     Double?      = null,
    val receivedAt:  Long?        = null,
    val status:      SensorStatus = SensorStatus.Offline,
    val error:       String?      = null,
)
