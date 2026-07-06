package com.corall.agrotrack.presentation.sensors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.repository.GatewayRepository
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorDetailViewModel @Inject constructor(
    savedStateHandle:           SavedStateHandle,
    private val gatewayRepo:    GatewayRepository,
    private val telemetryRepo:  TelemetryRepository,
) : ViewModel() {

    private val sensorId: Int = checkNotNull(savedStateHandle["sensorId"])

    private val _uiState = MutableStateFlow(SensorDetailUiState())
    val uiState: StateFlow<SensorDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val sensor  = gatewayRepo.getSensorById(sensorId).getOrNull()
            val reading = telemetryRepo.getLastReading(sensorId).getOrNull()

            if (sensor == null && reading == null) {
                _uiState.update { it.copy(isLoading = false, error = "No se pudo cargar el sensor") }
                return@launch
            }

            val receivedAt = reading?.receivedAt
            val status = when {
                receivedAt == null -> SensorStatus.Offline
                System.currentTimeMillis() - receivedAt > 30_000 -> SensorStatus.Offline
                else -> reading.status
            }

            _uiState.update { it.copy(
                isLoading   = false,
                sensorName  = sensor?.name ?: reading?.sensorName ?: "Sensor #$sensorId",
                sensorType  = sensor?.type ?: "temperature",
                location    = sensor?.location ?: "",
                unit        = sensor?.unit ?: reading?.unit ?: "°C",
                temperature = reading?.temperature,
                voltage     = reading?.voltage,
                battery     = reading?.battery,
                receivedAt  = receivedAt,
                status      = status,
                error       = null,
            ) }
        }
    }

    fun openAliasDialog() {
        _uiState.update { it.copy(aliasDialogOpen = true, aliasInput = it.sensorName, aliasError = null) }
    }

    fun closeAliasDialog() {
        _uiState.update { it.copy(aliasDialogOpen = false, aliasError = null) }
    }

    fun onAliasInputChange(value: String) {
        _uiState.update { it.copy(aliasInput = value, aliasError = null) }
    }

    fun saveAlias() {
        val alias = _uiState.value.aliasInput.trim()
        if (alias.isBlank()) {
            _uiState.update { it.copy(aliasError = "El alias no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(aliasSaving = true, aliasError = null) }
            gatewayRepo.saveSensorAlias(sensorId, alias)
                .onSuccess {
                    _uiState.update { it.copy(aliasSaving = false, aliasDialogOpen = false, sensorName = alias) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(aliasSaving = false, aliasError = e.message) }
                }
        }
    }
}
