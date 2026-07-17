package com.corall.agrotrack.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.model.GeneralReportRow
import com.corall.agrotrack.domain.repository.TelemetryRepository
import com.google.gson.GsonBuilder
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

fun buildGeneralCsv(rows: List<GeneralReportRow>): String {
    val sb = StringBuilder("gateway,estado,temp_min,temp_avg,temp_max,sensores,alertas,alertas_sin_resolver\n")
    for (r in rows) {
        sb.append(csvEscapeGeneral(r.name)).append(',')
            .append(r.status.name).append(',')
            .append(r.tempMin?.toString().orEmpty()).append(',')
            .append(r.tempAvg?.toString().orEmpty()).append(',')
            .append(r.tempMax?.toString().orEmpty()).append(',')
            .append(r.sensorCount).append(',')
            .append(r.alertsTotal).append(',')
            .append(r.alertsUnresolved).append('\n')
    }
    return sb.toString()
}

fun buildGeneralJson(rows: List<GeneralReportRow>): String {
    val exportGson = GsonBuilder().setPrettyPrinting().create()
    val list = rows.map {
        mapOf(
            "gateway" to it.name, "estado" to it.status.name,
            "temp_min" to it.tempMin, "temp_avg" to it.tempAvg, "temp_max" to it.tempMax,
            "sensores" to it.sensorCount, "alertas" to it.alertsTotal, "alertas_sin_resolver" to it.alertsUnresolved,
        )
    }
    return exportGson.toJson(list)
}

private fun csvEscapeGeneral(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n')) "\"${value.replace("\"", "\"\"")}\"" else value

@HiltViewModel
class GeneralReportViewModel @Inject constructor(
    private val telemetryRepo: TelemetryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneralReportUiState())
    val uiState: StateFlow<GeneralReportUiState> = _uiState.asStateFlow()

    fun selectRange(range: String) = _uiState.update { it.copy(range = range, customFrom = null, customTo = null) }
    fun setCustomRange(fromMs: Long, toMs: Long) = _uiState.update { it.copy(customFrom = fromMs, customTo = toMs) }
    fun selectDownloadFormat(format: DownloadFormat) = _uiState.update { it.copy(downloadFormat = format, downloadError = null) }
    fun onDownloadError(message: String?) = _uiState.update { it.copy(downloadError = message) }

    fun generateReport() {
        val state = _uiState.value
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
            telemetryRepo.getGeneralReport(from, to)
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

    fun buildExportContent(): String {
        val rows = _uiState.value.report?.gateways ?: emptyList()
        return when (_uiState.value.downloadFormat) {
            DownloadFormat.CSV  -> buildGeneralCsv(rows)
            DownloadFormat.JSON -> buildGeneralJson(rows)
            DownloadFormat.PDF  -> "" // el PDF se arma aparte con ReportPdfBuilder.buildGeneralLevel
        }
    }
}
