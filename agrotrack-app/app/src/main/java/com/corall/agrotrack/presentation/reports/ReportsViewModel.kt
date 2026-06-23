package com.corall.agrotrack.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.data.remote.api.SensorsApiService
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
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
                    sensors.firstOrNull()?.let { selectSensor(it) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingSensors = false, error = e.message) }
                }
        }
    }

    fun selectSensor(sensor: Sensor) {
        _uiState.update { it.copy(selectedSensor = sensor) }
        loadReport()
    }

    fun selectRange(range: String) {
        _uiState.update { it.copy(range = range) }
        loadReport()
    }

    fun loadReport() {
        val state = _uiState.value
        val sensorId = state.selectedSensor?.id ?: return
        val nowMs = System.currentTimeMillis()
        val fromMs = when (state.range) {
            "7d"  -> nowMs - 7 * 86_400_000L
            "30d" -> nowMs - 30 * 86_400_000L
            else  -> nowMs - 86_400_000L
        }
        val from = Instant.ofEpochMilli(fromMs).toString()
        val to   = Instant.ofEpochMilli(nowMs).toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingReport = true, error = null) }
            telemetryRepo.getReportHistory(sensorId, from, to)
                .onSuccess { readings ->
                    _uiState.update { it.copy(isLoadingReport = false, readings = readings) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingReport = false, error = e.message) }
                }
        }
    }
}
