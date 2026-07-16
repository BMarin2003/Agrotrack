package com.corall.agrotrack.presentation.installer

import com.corall.agrotrack.domain.model.Gateway

data class InstallerUiState(
    val isLoading:         Boolean       = true,
    val gateways:          List<Gateway> = emptyList(),
    val selectedGatewayId: Int?          = null,
    // WiFi
    val ssid:              String        = "",
    val password:          String        = "",
    val isSaving:          Boolean       = false,
    val success:           Boolean       = false,
    // Verificación post-guardado: ¿el gateway realmente volvió a reportar datos?
    val verifying:         Boolean       = false,
    val confirmed:         Boolean       = false,
    val error:             String?       = null,
    // PIN
    val pin:               String        = "",
    val pinAllGateways:    Boolean       = false,
    val isPinSaving:       Boolean       = false,
    val pinSuccess:        Boolean       = false,
    val pinError:          String?       = null,
)
