package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.GeneralReport

data class GeneralReportUiState(
    val range:           String        = "24h",
    val customFrom:       Long?         = null,
    val customTo:         Long?         = null,
    val isLoadingReport:  Boolean       = false,
    val hasGenerated:     Boolean       = false,
    val report:           GeneralReport? = null,
    val error:            String?       = null,
    val downloadFormat:   DownloadFormat = DownloadFormat.PDF,
    val downloadError:    String?       = null,
) {
    val isCustomRange: Boolean get() = customFrom != null && customTo != null
}
