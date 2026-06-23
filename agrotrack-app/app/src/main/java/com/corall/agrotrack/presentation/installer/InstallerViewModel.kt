package com.corall.agrotrack.presentation.installer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.repository.GatewayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallerViewModel @Inject constructor(
    private val gatewayRepo: GatewayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            gatewayRepo.getGateways()
                .onSuccess { gateways ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        gateways  = gateways,
                        selectedGatewayId = gateways.firstOrNull()?.id,
                    ) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectGateway(id: Int) = _uiState.update { it.copy(selectedGatewayId = id, success = false, error = null) }
    fun onSsidChange(v: String) = _uiState.update { it.copy(ssid = v, success = false) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }

    fun submit() {
        val state = _uiState.value
        val gatewayId = state.selectedGatewayId ?: run {
            _uiState.update { it.copy(error = "Selecciona un gateway") }
            return
        }
        if (state.ssid.isBlank()) {
            _uiState.update { it.copy(error = "El SSID no puede estar vacío") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, success = false) }
            gatewayRepo.updateGatewayWifi(gatewayId, state.ssid.trim(), state.password.ifBlank { null }, "WPA2")
                .onSuccess { _uiState.update { it.copy(isSaving = false, success = true) } }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
