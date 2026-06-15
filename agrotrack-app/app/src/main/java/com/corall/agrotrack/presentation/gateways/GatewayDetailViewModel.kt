package com.corall.agrotrack.presentation.gateways

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayDetailUiState())
    val uiState: StateFlow<GatewayDetailUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var currentGatewayId: Int? = null

    fun start(gatewayId: Int) {
        if (currentGatewayId == gatewayId) return

        currentGatewayId = gatewayId
        pollingJob?.cancel()

        pollingJob = viewModelScope.launch {
            load(gatewayId, isInitialLoad = true)

            while (isActive) {
                delay(10_000)
                load(gatewayId, isInitialLoad = false)
            }
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

            getGatewaySensors(gatewayId)
                .onSuccess { sensors ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            gatewayName = sensors.firstOrNull()?.gatewayName.orEmpty(),
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