package com.corall.agrotrack.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.model.GatewayReport
import com.corall.agrotrack.domain.repository.GatewayRepository
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GatewayReportViewModel @Inject constructor(
    private val gatewayRepo:   GatewayRepository,
    private val telemetryRepo: TelemetryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayReportUiState())
    val uiState: StateFlow<GatewayReportUiState> = _uiState.asStateFlow()

    init { loadGateways() }

    private fun loadGateways() {
        viewModelScope.launch {
            gatewayRepo.getGateways()
                .onSuccess { gateways ->
                    _uiState.update { it.copy(
                        isLoadingGateways = false,
                        gateways = gateways,
                        selectedGatewayId = gateways.firstOrNull()?.id,
                    ) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingGateways = false, error = e.message) } }
        }
    }

    fun selectGateway(id: Int) = _uiState.update { it.copy(selectedGatewayId = id, hasGenerated = false, report = null) }
    fun selectRange(range: String) = _uiState.update { it.copy(range = range, customFrom = null, customTo = null) }
    fun setCustomRange(fromMs: Long, toMs: Long) = _uiState.update { it.copy(customFrom = fromMs, customTo = toMs) }
    fun selectDownloadFormat(format: DownloadFormat) = _uiState.update { it.copy(downloadFormat = format, downloadError = null) }
    fun onDownloadError(message: String?) = _uiState.update { it.copy(downloadError = message) }

    fun generateReport() {
        val state = _uiState.value
        val gatewayId = state.selectedGatewayId ?: return

        val nowMs = System.currentTimeMillis()
        val fromMs = state.customFrom ?: when (state.range) {
            "7d"  -> nowMs - 7 * 86_400_000L
            "30d" -> nowMs - 30 * 86_400_000L
            else  -> nowMs - 86_400_000L
        }
        val toMs = state.customTo ?: nowMs
        val from = Instant.ofEpochMilli(fromMs).toString()
        val to   = Instant.ofEpochMilli(toMs).toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingReport = true, error = null, hasGenerated = true) }
            telemetryRepo.getGatewayReport(gatewayId, from, to)
                .onSuccess { report -> _uiState.update { it.copy(isLoadingReport = false, report = report) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingReport = false, error = e.message) } }
        }
    }

    fun periodLabel(): String {
        val state = _uiState.value
        return if (state.isCustomRange) {
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            "${fmt.format(Date(state.customFrom!!))} — ${fmt.format(Date(state.customTo!!))}"
        } else when (state.range) {
            "7d"  -> "Últimos 7 días"
            "30d" -> "Últimos 30 días"
            else  -> "Últimas 24 horas"
        }
    }

    /** sensor + sus lecturas, insumo para el PDF (Task 9) y para exportSensorRows(). */
    fun exportSensorRows(): List<Pair<String, com.corall.agrotrack.domain.model.SensorReading>> {
        val report = _uiState.value.report ?: return emptyList()
        return report.sensors.flatMap { sensor -> sensor.readings.map { sensor.name to it } }
    }

    fun buildExportContent(): String {
        val rows = exportSensorRows()
        return when (_uiState.value.downloadFormat) {
            DownloadFormat.CSV  -> ReportExportUtil.buildCsv(rows)
            DownloadFormat.JSON -> ReportExportUtil.buildJson(rows)
            DownloadFormat.PDF  -> "" // el PDF se arma aparte con ReportPdfBuilder (Task 9)
        }
    }
}
