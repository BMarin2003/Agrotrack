package com.corall.agrotrack.domain.model

data class ThresholdConfig(
    val sensorId:      Int,
    val minThreshold:  Double?,
    val maxThreshold:  Double?,
    val alertsEnabled: Boolean,
)
