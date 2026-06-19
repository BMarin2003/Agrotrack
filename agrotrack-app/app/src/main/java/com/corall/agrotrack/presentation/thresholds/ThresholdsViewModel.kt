package com.corall.agrotrack.presentation.thresholds

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.model.ThresholdConfig
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThresholdsViewModel @Inject constructor(
    savedStateHandle:           SavedStateHandle,
    private val telemetryRepo:  TelemetryRepository,
) : ViewModel() {

    private val sensorId: Int = checkNotNull(savedStateHandle["sensorId"])

    private val _uiState = MutableStateFlow(ThresholdsUiState(sensorId = sensorId))
    val uiState: StateFlow<ThresholdsUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            telemetryRepo.getThresholdConfig(sensorId)
                .onSuccess { config ->
                    _uiState.update { it.copy(
                        isLoading    = false,
                        minThreshold = config?.minThreshold?.toString() ?: "",
                        maxThreshold = config?.maxThreshold?.toString() ?: "",
                        alertsEnabled = config?.alertsEnabled ?: false,
                    ) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onMinChange(value: String)  { _uiState.update { it.copy(minThreshold = value,  saveSuccess = false) } }
    fun onMaxChange(value: String)  { _uiState.update { it.copy(maxThreshold = value,  saveSuccess = false) } }

    fun onToggleAlerts(enabled: Boolean) {
        _uiState.update { it.copy(alertsEnabled = enabled, saveSuccess = false) }
    }

    fun save() {
        val state = _uiState.value
        val min = state.minThreshold.toDoubleOrNull()
        val max = state.maxThreshold.toDoubleOrNull()

        if (state.minThreshold.isNotBlank() && min == null) {
            _uiState.update { it.copy(error = "Umbral mínimo inválido") }
            return
        }
        if (state.maxThreshold.isNotBlank() && max == null) {
            _uiState.update { it.copy(error = "Umbral máximo inválido") }
            return
        }
        if (min != null && max != null && min >= max) {
            _uiState.update { it.copy(error = "El umbral mínimo debe ser menor que el máximo") }
            return
        }

        // Validación HU-AL4: los umbrales siempre se envían junto con alertsEnabled,
        // preservando la configuración independientemente del estado del toggle.
        val config = ThresholdConfig(
            sensorId      = sensorId,
            minThreshold  = min,
            maxThreshold  = max,
            alertsEnabled = state.alertsEnabled,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            telemetryRepo.updateThresholdConfig(config)
                .onSuccess { _uiState.update { it.copy(isSaving = false, saveSuccess = true) } }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
