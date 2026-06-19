package com.corall.agrotrack.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.core.network.WebSocketManager
import com.corall.agrotrack.core.notification.AlertNotificationHelper
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole
import com.corall.agrotrack.core.util.ConnectivityObserver
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.repository.TelemetryRepository
import com.corall.agrotrack.domain.usecase.telemetry.GetLatestReadingsUseCase
import com.corall.agrotrack.domain.usecase.telemetry.LiveEvent
import com.corall.agrotrack.domain.usecase.telemetry.ObserveLiveReadingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_GATEWAY_ID = 1
private const val POLL_INTERVAL_MS   = 10_000L
private const val WS_DEBOUNCE_MS     = 600L

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val wsManager:            WebSocketManager,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionManager:       SessionManager,
    private val getLatestReadings:    GetLatestReadingsUseCase,
    private val observeLive:          ObserveLiveReadingsUseCase,
    private val telemetryRepository:  TelemetryRepository,
    private val notificationHelper:   AlertNotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Job de debounce para no lanzar un HTTP por cada mensaje WS del burst
    private var wsRefreshJob: Job? = null

    init {
        val role = sessionManager.getRole()
        _uiState.update { it.copy(
            isTechnician = role == UserRole.TECHNICIAN || role == UserRole.ADMIN,
            isAdmin      = role == UserRole.ADMIN,
        ) }
        observeNetwork()
        observeWsState()
        observeLiveEvents()
        observeCachedData()
        startPollingAndConnect()
    }

    private fun startPollingAndConnect() {
        wsManager.connect(DEFAULT_GATEWAY_ID)

        viewModelScope.launch {
            // carga inicial
            refreshReadings(isInitial = true)
            telemetryRepository.getActiveAlerts(DEFAULT_GATEWAY_ID)
                .onSuccess { alerts -> alerts.forEach { telemetryRepository.cacheAlert(it) } }

            // polling periódico
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refreshReadings()
            }
        }
    }

    private suspend fun refreshReadings(isInitial: Boolean = false) {
        if (isInitial) _uiState.update { it.copy(isLoading = true) }

        getLatestReadings(DEFAULT_GATEWAY_ID)
            .onSuccess { readings ->
                // insertAll con ids reales (no 0L), nombre y unidad incluidos
                if (readings.isNotEmpty()) {
                    readings.forEach { telemetryRepository.cacheReading(it) }
                }
                _uiState.update { it.copy(isLoading = false, error = null) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
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
                        // El burst TWARM envía 3 mensajes seguidos — debounce 600ms
                        // para hacer 1 solo HTTP en vez de 3 simultáneos.
                        wsRefreshJob?.cancel()
                        wsRefreshJob = viewModelScope.launch {
                            delay(WS_DEBOUNCE_MS)
                            refreshReadings()
                        }
                    }
                    is LiveEvent.NewAlert -> {
                        viewModelScope.launch { telemetryRepository.cacheAlert(event.data) }
                        notificationHelper.showAlert(event.data)
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
                        silentMs > 90_000 -> SensorStatus.Offline
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
