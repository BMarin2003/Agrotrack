package com.corall.agrotrack.presentation.gateways

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.repository.GatewayRepository
import com.corall.agrotrack.domain.usecase.gateway.GetGatewaySensorsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GatewayDetailViewModel @Inject constructor(
    private val getGatewaySensors: GetGatewaySensorsUseCase,
    private val gatewayRepository: GatewayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayDetailUiState())
    val uiState: StateFlow<GatewayDetailUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var currentGatewayId: Int? = null

    fun start(gatewayId: Int) {
        if (currentGatewayId == gatewayId) return

        currentGatewayId = gatewayId
        pollingJob?.cancel()
        _uiState.update { it.copy(gatewayId = gatewayId) }

        pollingJob = viewModelScope.launch {
            load(gatewayId, isInitialLoad = true)

            while (isActive) {
                delay(10_000)
                load(gatewayId, isInitialLoad = false)
            }
        }
    }

    fun openWifiDialog()  { _uiState.update { it.copy(wifiDialogOpen = true,  wifiSuccess = false, wifiError = null) } }
    fun closeWifiDialog() { _uiState.update { it.copy(wifiDialogOpen = false, wifiError = null) } }

    fun saveWifi(ssid: String, password: String?, security: String) {
        val gatewayId = currentGatewayId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(wifiSaving = true, wifiError = null) }
            gatewayRepository.updateGatewayWifi(gatewayId, ssid, password, security)
                .onSuccess { _uiState.update { it.copy(wifiSaving = false, wifiSuccess = true, wifiDialogOpen = false) } }
                .onFailure { e -> _uiState.update { it.copy(wifiSaving = false, wifiError = e.message) } }
        }
    }

    fun refresh() {
        val gatewayId = currentGatewayId ?: return
        load(gatewayId, isInitialLoad = false)
    }

    private fun load(gatewayId: Int, isInitialLoad: Boolean) {
        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.update { state ->
                    state.copy(isLoading = true, error = null)
                }
            }

            val gateway = gatewayRepository.getGatewayById(gatewayId).getOrNull()

            getGatewaySensors(gatewayId)
                .onSuccess { sensors ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            gatewayName = gateway?.name ?: sensors.firstOrNull()?.gatewayName.orEmpty(),
                            gatewayBattery = gateway?.battery,
                            sensors = sensors,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "No se pudieron cargar los sensores",
                        )
                    }
                }
        }
    }
}