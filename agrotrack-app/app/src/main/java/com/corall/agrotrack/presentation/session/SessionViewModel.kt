package com.corall.agrotrack.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.core.network.WebSocketManager
import com.corall.agrotrack.core.notification.AlertNotificationHelper
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.domain.repository.AuthRepository
import com.corall.agrotrack.domain.repository.TelemetryRepository
import com.corall.agrotrack.domain.usecase.telemetry.LiveEvent
import com.corall.agrotrack.domain.usecase.telemetry.ObserveLiveReadingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_GATEWAY_ID = 1

/**
 * Vive mientras dure la Activity (se instancia una sola vez en la raíz de
 * AppNavGraph, fuera de cualquier destino) — por eso es el lugar correcto
 * para mantener la conexión WS y las notificaciones de alertas activas
 * mientras el usuario navega entre pantallas, no solo mientras está en el
 * Dashboard. No sobrevive a que la app sea cerrada/matada por el SO — eso
 * requeriría push real (FCM), fuera de alcance por ahora.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository:      AuthRepository,
    private val sessionManager:      SessionManager,
    private val wsManager:           WebSocketManager,
    private val observeLive:         ObserveLiveReadingsUseCase,
    private val notificationHelper:  AlertNotificationHelper,
    private val telemetryRepository: TelemetryRepository,
) : ViewModel() {

    enum class VerifyResult { SKIPPED, VALID, INVALID }

    private val _result = MutableStateFlow<VerifyResult?>(null)
    val result: StateFlow<VerifyResult?> = _result.asStateFlow()

    private var liveUpdatesStarted = false

    fun verifySession() {
        if (_result.value != null) return // ya se verificó en esta sesión de proceso

        if (!sessionManager.isSessionActive()) {
            _result.value = VerifyResult.SKIPPED
            return
        }

        viewModelScope.launch {
            authRepository.verifyToken()
                .onSuccess { user ->
                    sessionManager.saveSession(user.token, user.id, user.names, user.role)
                    _result.value = VerifyResult.VALID
                    connectLiveUpdates()
                }
                .onFailure {
                    sessionManager.clearSession()
                    _result.value = VerifyResult.INVALID
                }
        }
    }

    /** Llamar tras un login exitoso (verifySession ya corrió una vez al arrancar
     *  y no se repite, así que un login fresco no dispara connectLiveUpdates solo). */
    fun connectLiveUpdates() {
        if (liveUpdatesStarted) return
        liveUpdatesStarted = true

        wsManager.connect(DEFAULT_GATEWAY_ID)
        observeLive()
            .onEach { event ->
                if (event is LiveEvent.NewAlert) {
                    telemetryRepository.cacheAlert(event.data)
                    notificationHelper.showAlert(event.data)
                }
            }
            .launchIn(viewModelScope)
    }

    fun disconnectLiveUpdates() {
        liveUpdatesStarted = false
        wsManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        wsManager.disconnect()
    }
}
