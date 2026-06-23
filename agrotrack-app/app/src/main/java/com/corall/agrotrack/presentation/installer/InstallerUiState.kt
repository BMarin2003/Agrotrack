package com.corall.agrotrack.presentation.installer

import com.corall.agrotrack.domain.model.Gateway

data class InstallerUiState(
    val isLoading:         Boolean       = true,
    val gateways:          List<Gateway> = emptyList(),
    val selectedGatewayId: Int?          = null,
    val ssid:              String        = "",
    val password:          String        = "",
    val isSaving:          Boolean       = false,
    val success:           Boolean       = false,
    val error:             String?       = null,
)
