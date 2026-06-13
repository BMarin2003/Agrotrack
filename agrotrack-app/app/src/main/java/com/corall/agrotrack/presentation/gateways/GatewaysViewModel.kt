package com.corall.agrotrack.presentation.gateways

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.usecase.gateway.GetGatewaysUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GatewaysViewModel @Inject constructor(
    private val getGateways: GetGatewaysUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewaysUiState())
    val uiState: StateFlow<GatewaysUiState> = _uiState.asStateFlow()

    init {
        startPolling()
    }

    fun refresh() {
        load(isInitialLoad = false)
    }

    private fun startPolling() {
        viewModelScope.launch {
            load(isInitialLoad = true)

            while (isActive) {
                delay(10_000)
                load(isInitialLoad = false)
            }
        }
    }

    private fun load(isInitialLoad: Boolean) {
        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.update { state ->
                    state.copy(isLoading = true, error = null)
                }
            }

            getGateways()
                .onSuccess { gateways ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            gateways = gateways,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "No se pudieron cargar los gateways",
                        )
                    }
                }
        }
    }
}