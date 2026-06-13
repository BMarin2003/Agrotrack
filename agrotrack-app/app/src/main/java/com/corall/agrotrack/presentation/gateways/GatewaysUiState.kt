package com.corall.agrotrack.presentation.gateways

import com.corall.agrotrack.domain.model.Gateway

data class GatewaysUiState(
    val isLoading: Boolean = true,
    val gateways: List<Gateway> = emptyList(),
    val error: String? = null,
)