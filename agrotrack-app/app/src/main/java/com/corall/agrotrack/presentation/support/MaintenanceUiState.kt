package com.corall.agrotrack.presentation.support

import com.corall.agrotrack.data.remote.dto.MaintenanceRecordDto

data class MaintenanceUiState(
    val isLoading:  Boolean                    = true,
    val gatewayId:  Int                        = 0,
    val records:    List<MaintenanceRecordDto> = emptyList(),
    val notes:      String                     = "",
    val isSaving:   Boolean                    = false,
    val success:    Boolean                    = false,
    val error:      String?                    = null,
)
