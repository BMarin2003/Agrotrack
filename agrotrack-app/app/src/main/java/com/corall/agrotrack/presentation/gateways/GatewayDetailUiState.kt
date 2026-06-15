package com.corall.agrotrack.presentation.gateways

import com.corall.agrotrack.domain.model.SensorSummary

data class GatewayDetailUiState(
    val isLoading: Boolean = true,
    val gatewayName: String = "",
    val sensors: List<SensorSummary> = emptyList(),
    val error: String? = null,
)