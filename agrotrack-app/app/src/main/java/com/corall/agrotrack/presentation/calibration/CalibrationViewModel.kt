package com.corall.agrotrack.presentation.calibration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.data.remote.api.SensorsApiService
import com.corall.agrotrack.data.remote.dto.CalibrationSaveDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: SensorsApiService,
) : ViewModel() {

    private val sensorId: Int = checkNotNull(savedStateHandle["sensorId"])

    private val _uiState = MutableStateFlow(CalibrationUiState(sensorId = sensorId))
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { api.getCalibration(sensorId) }
                .onSuccess { resp ->
                    val cal = resp.body()
                    _uiState.update { it.copy(
                        isLoading   = false,
                        gain        = cal?.gain?.toString()      ?: "1.0",
                        intercept   = cal?.intercept?.toString() ?: "0.0",
                        lastApplied = cal?.appliedAt,
                    ) }
                }
                .onFailure { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun onGainChange(v: String)      = _uiState.update { it.copy(gain      = v, error = null, success = false) }
    fun onInterceptChange(v: String) = _uiState.update { it.copy(intercept = v, error = null, success = false) }
    fun onNotesChange(v: String)     = _uiState.update { it.copy(notes     = v) }

    fun save() {
        val s = _uiState.value
        val g = s.gain.toDoubleOrNull()      ?: run { _uiState.update { it.copy(error = "Ganancia inválida") };      return }
        val i = s.intercept.toDoubleOrNull() ?: run { _uiState.update { it.copy(error = "Intercepto inválido") };    return }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, success = false) }
            runCatching { api.saveCalibration(sensorId, CalibrationSaveDto(g, i, s.notes.ifBlank { null })) }
                .onSuccess { resp ->
                    if (resp.isSuccessful)
                        _uiState.update { it.copy(isSaving = false, success = true, lastApplied = "Aplicado ahora") }
                    else
                        _uiState.update { it.copy(isSaving = false, error = "Error ${resp.code()}") }
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
