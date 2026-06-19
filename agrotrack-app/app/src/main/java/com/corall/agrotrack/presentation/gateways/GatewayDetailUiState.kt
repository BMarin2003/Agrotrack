package com.corall.agrotrack.presentation.gateways

import com.corall.agrotrack.domain.model.SensorSummary

data class GatewayDetailUiState(
    val isLoading:        Boolean          = true,
    val gatewayId:        Int              = 0,
    val gatewayName:      String           = "",
    val gatewayBattery:   Double?          = null,
    val sensors:          List<SensorSummary> = emptyList(),
    val error:            String?          = null,
    val wifiDialogOpen:   Boolean          = false,
    val wifiSaving:       Boolean          = false,
    val wifiSuccess:      Boolean          = false,
    val wifiError:        String?          = null,
)