package com.corall.agrotrack.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.core.network.WebSocketManager
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole
import com.corall.agrotrack.core.util.ConnectivityObserver
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.repository.TelemetryRepository
import com.corall.agrotrack.domain.usecase.telemetry.GetLatestReadingsUseCase
import com.corall.agrotrack.domain.usecase.telemetry.LiveEvent
import com.corall.agrotrack.domain.usecase.telemetry.ObserveLiveReadingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Hardcodeado para la demo; en producción viene de SessionManager o de la config del usuario
private const val DEFAULT_GATEWAY_ID = 1

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val wsManager:          WebSocketManager,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionManager:     SessionManager,
    private val getLatestReadings:  GetLatestReadingsUseCase,
    private val observeLive:        ObserveLiveReadingsUseCase,
    private val telemetryRepository: TelemetryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isTechnician = sessionManager.getRole() == UserRole.TECHNICIAN) }
        observeNetwork()
        observeWsState()
        observeLiveEvents()
        observeCachedData()
        connectAndFetch()
    }

    private fun connectAndFetch() {
        wsManager.connect(DEFAULT_GATEWAY_ID)
        viewModelScope.launch {
            getLatestReadings(DEFAULT_GATEWAY_ID)
                .onSuccess { readings ->
                    readings.forEach { telemetryRepository.cacheReading(it) }
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }

            telemetryRepository.getActiveAlerts(DEFAULT_GATEWAY_ID)
                .onSuccess { alerts ->
                    alerts.forEach { telemetryRepository.cacheAlert(it) }
                }
        }
    }

    private fun observeNetwork() {
        connectivityObserver.networkStatus
            .onEach { status -> _uiState.update { it.copy(networkStatus = status) } }
            .launchIn(viewModelScope)
    }

    private fun observeWsState() {
        wsManager.state
            .onEach { wsState -> _uiState.update { it.copy(wsState = wsState) } }
            .launchIn(viewModelScope)
    }

    private fun observeLiveEvents() {
        observeLive()
            .onEach { event ->
                when (event) {
                    is LiveEvent.Reading -> {
                        viewModelScope.launch { telemetryRepository.cacheReading(event.data) }
                    }
                    is LiveEvent.NewAlert -> {
                        viewModelScope.launch { telemetryRepository.cacheAlert(event.data) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCachedData() {
        telemetryRepository.getCachedReadings(DEFAULT_GATEWAY_ID)
            .onEach { readings ->
                val now = System.currentTimeMillis()
                val withStatus = readings.map { r ->
                    val silentMs = now - r.receivedAt
                    val status = when {
                        silentMs > 30_000 -> SensorStatus.Offline
                        else              -> r.status
                    }
                    r.copy(status = status)
                }
                _uiState.update { it.copy(readings = withStatus) }
            }
            .launchIn(viewModelScope)

        telemetryRepository.getCachedAlerts()
            .onEach { alerts -> _uiState.update { it.copy(activeAlerts = alerts) } }
            .launchIn(viewModelScope)
    }

    fun resolveAlert(alertId: Long) {
        viewModelScope.launch {
            telemetryRepository.resolveAlert(alertId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsManager.disconnect()
    }
}
