package com.corall.agrotrack.presentation.support

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.data.remote.api.SensorsApiService
import com.corall.agrotrack.data.remote.dto.MaintenanceSaveDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: SensorsApiService,
) : ViewModel() {

    private val gatewayId: Int = checkNotNull(savedStateHandle["gatewayId"])

    private val _uiState = MutableStateFlow(MaintenanceUiState(gatewayId = gatewayId))
    val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { api.listMaintenance(gatewayId) }
                .onSuccess { resp ->
                    _uiState.update { it.copy(isLoading = false, records = resp.body().orEmpty()) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onNotesChange(v: String) = _uiState.update { it.copy(notes = v, success = false, error = null) }

    fun register() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, success = false) }
            runCatching { api.registerMaintenance(gatewayId, MaintenanceSaveDto(s.notes.ifBlank { null })) }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        _uiState.update { it.copy(isSaving = false, success = true, notes = "") }
                        load()
                    } else {
                        _uiState.update { it.copy(isSaving = false, error = "Error ${resp.code()}") }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
