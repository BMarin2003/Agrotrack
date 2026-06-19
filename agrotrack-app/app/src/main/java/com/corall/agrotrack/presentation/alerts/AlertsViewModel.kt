package com.corall.agrotrack.presentation.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val alerts:     List<Alert> = emptyList(),
    val canResolve: Boolean     = false,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository:     TelemetryRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        val role = sessionManager.getRole()
        _uiState.update { it.copy(canResolve = role == UserRole.TECHNICIAN || role == UserRole.ADMIN) }
        repository.getCachedAlerts()
            .onEach { alerts -> _uiState.update { it.copy(alerts = alerts) } }
            .launchIn(viewModelScope)
    }

    fun resolve(alertId: Long) {
        viewModelScope.launch { repository.resolveAlert(alertId) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAllAlerts() }
    }
}
