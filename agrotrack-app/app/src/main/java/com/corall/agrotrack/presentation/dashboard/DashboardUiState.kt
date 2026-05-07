package com.corall.agrotrack.presentation.dashboard

import com.corall.agrotrack.core.network.WsConnectionState
import com.corall.agrotrack.core.util.NetworkStatus
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.domain.model.SensorReading

data class DashboardUiState(
    val readings:         List<SensorReading>  = emptyList(),
    val activeAlerts:     List<Alert>           = emptyList(),
    val wsState:          WsConnectionState     = WsConnectionState.Connecting,
    val networkStatus:    NetworkStatus          = NetworkStatus.Unavailable,
    val isLoading:        Boolean               = true,
    val error:            String?               = null,
    val isTechnician:     Boolean               = false,
) {
    val isOnline: Boolean get() = networkStatus == NetworkStatus.Available
    val isWsLive: Boolean get() = wsState == WsConnectionState.Connected
    val criticalAlertCount: Int get() = activeAlerts.count { !it.resolved }
}
