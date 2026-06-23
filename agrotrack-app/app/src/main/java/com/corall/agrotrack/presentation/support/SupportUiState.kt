package com.corall.agrotrack.presentation.support

import com.corall.agrotrack.domain.model.Gateway

data class SupportUiState(
    val isLoading: Boolean       = true,
    val gateways:  List<Gateway> = emptyList(),
    val error:     String?       = null,
)
