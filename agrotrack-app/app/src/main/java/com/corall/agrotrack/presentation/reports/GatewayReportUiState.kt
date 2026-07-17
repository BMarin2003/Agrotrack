package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.GatewayReport

data class GatewayReportUiState(
    val isLoadingGateways: Boolean       = true,
    val gateways:          List<Gateway> = emptyList(),
    val selectedGatewayId: Int?          = null,
    val range:             String        = "24h",
    val customFrom:        Long?         = null,
    val customTo:          Long?         = null,
    val isLoadingReport:   Boolean       = false,
    val hasGenerated:      Boolean       = false,
    val report:            GatewayReport? = null,
    val error:             String?       = null,
    val downloadFormat:    DownloadFormat = DownloadFormat.PDF,
    val downloadError:     String?       = null,
) {
    val isCustomRange: Boolean get() = customFrom != null && customTo != null
}
