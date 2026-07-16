package com.corall.agrotrack.presentation.support

import com.corall.agrotrack.data.remote.dto.TicketDto
import com.corall.agrotrack.domain.model.Gateway

data class HelpDeskUiState(
    val isLoading:           Boolean         = true,
    val tickets:              List<TicketDto> = emptyList(),
    val gateways:             List<Gateway>   = emptyList(),
    val error:                String?         = null,
    // Formulario "nuevo ticket"
    val showNewTicketDialog:  Boolean         = false,
    val newSubject:           String          = "",
    val newDescription:       String          = "",
    val newGatewayId:         Int?            = null,
    val isSaving:             Boolean         = false,
    val saveError:            String?         = null,
)
