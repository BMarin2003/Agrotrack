package com.corall.agrotrack.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.data.remote.api.SensorsApiService
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
class ReportsViewModel @Inject constructor(
    private val sensorsApi:    SensorsApiService,
    private val telemetryRepo: TelemetryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init { loadSensors() }

    private fun loadSensors() {
        viewModelScope.launch {
            runCatching { sensorsApi.getAllSensors() }
                .onSuccess { resp ->
                    val sensors = resp.body().orEmpty()
                        .filter { it.enable != false }
                        .map { dto ->
                            Sensor(
                                id          = dto.id,
                                gatewayId   = dto.gatewayId,
                                gatewayName = dto.gateway.orEmpty(),
                                name        = dto.name,
                                identifier  = dto.identifier.orEmpty(),
                                type        = dto.type ?: "temperature",
                                unit        = dto.unit ?: "°C",
                                location    = dto.location.orEmpty(),
                                enable      = dto.enable ?: true,
                            )
                        }
                    _uiState.update { it.copy(isLoadingSensors = false, sensors = sensors) }
                    sensors.firstOrNull()?.let {
                        _uiState.update { s -> s.copy(selectedSensors = setOf(it)) }
                        loadReport()
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingSensors = false, error = e.message) }
                }
        }
    }

    /** Alterna la selección de un sensor para el reporte (multi-selección). */
    fun toggleSensor(sensor: Sensor) {
        _uiState.update { state ->
            val current = state.selectedSensors
            val next = if (current.contains(sensor)) current - sensor else current + sensor
            state.copy(selectedSensors = next)
        }
    }

    fun selectRange(range: String) {
        _uiState.update { it.copy(range = range, customFrom = null, customTo = null) }
    }

    fun setCustomRange(fromMs: Long, toMs: Long) {
        _uiState.update { it.copy(customFrom = fromMs, customTo = toMs) }
    }

    fun loadReport() {
        val state = _uiState.value
        val sensorIds = state.selectedSensors.map { it.id }
        if (sensorIds.isEmpty()) return

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
            runCatching {
                coroutineScope {
                    sensorIds.map { id ->
                        async { id to telemetryRepo.getReportHistory(id, from, to) }
                    }.map { it.await() }
                }
            }.onSuccess { results ->
                val firstError = results.firstNotNullOfOrNull { (_, r) -> r.exceptionOrNull() }
                val reports = results.associate { (id, r) -> id to (r.getOrNull() ?: emptyList()) }
                _uiState.update { it.copy(isLoadingReport = false, reports = reports, error = firstError?.message) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoadingReport = false, error = e.message) }
            }
        }
    }

    fun selectDownloadFormat(format: DownloadFormat) = _uiState.update { it.copy(downloadFormat = format, downloadError = null) }
    fun onDownloadError(message: String?) = _uiState.update { it.copy(downloadError = message) }

    /** Sensor + sus lecturas, en el orden mostrado — insumo para el PDF. */
    fun exportSensorRows(): List<Pair<Sensor, List<SensorReading>>> {
        val state = _uiState.value
        return state.selectedSensors.toList().map { it to state.reports[it.id].orEmpty() }
    }

    fun periodLabel(): String {
        val state = _uiState.value
        return if (state.isCustomRange) {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            "${fmt.format(Date(state.customFrom!!))} — ${fmt.format(Date(state.customTo!!))}"
        } else when (state.range) {
            "7d"  -> "Últimos 7 días"
            "30d" -> "Últimos 30 días"
            else  -> "Últimas 24 horas"
        }
    }

    /**
     * Arma el contenido a exportar a partir de lo que YA está en memoria
     * (`reports`, cargado por loadReport()) — no hace ninguna llamada de
     * red. Por eso HU18 ("reintentar si pierde conexión") queda resuelto
     * de raíz: no hay conexión que perder al exportar, siempre se puede
     * volver a tocar "Descargar" al instante.
     */
    fun buildExportContent(): String {
        val state = _uiState.value
        val sensorNames = state.selectedSensors.associate { it.id to it.name }
        val rows = state.reports.entries
            .sortedBy { it.key }
            .flatMap { (sensorId, readings) -> readings.map { sensorNames[sensorId].orEmpty() to it } }
            .sortedBy { it.second.receivedAt }

        return when (state.downloadFormat) {
            DownloadFormat.CSV  -> ReportExportUtil.buildCsv(rows)
            DownloadFormat.JSON -> ReportExportUtil.buildJson(rows)
        }
    }
}
