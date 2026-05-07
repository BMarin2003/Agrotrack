package com.corall.agrotrack.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.dashboard.components.AlertBanner
import com.corall.agrotrack.presentation.dashboard.components.ConnectionStatusBar
import com.corall.agrotrack.presentation.dashboard.components.SensorCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAlerts:   () -> Unit,
    onNavigateToReports:  (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout:             () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AgroTrack") },
                actions = {
                    // Ícono de alertas — visible para todos
                    BadgedBox(
                        badge = {
                            if (uiState.criticalAlertCount > 0) {
                                Badge { Text(uiState.criticalAlertCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToAlerts) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alertas")
                        }
                    }

                    // Configuración — SOLO TÉCNICO
                    if (uiState.isTechnician) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Banner de estado de conexión (offline / reconnecting)
            ConnectionStatusBar(
                networkStatus = uiState.networkStatus,
                wsState       = uiState.wsState,
            )

            // Banner de alertas críticas
            AlertBanner(
                alertCount = uiState.criticalAlertCount,
                onClick    = onNavigateToAlerts,
            )

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.readings.isEmpty() && !uiState.isOnline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text  = "Sin datos disponibles\nVerifica la conexión",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding     = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.readings, key = { it.sensorId }) { reading ->
                            SensorCard(
                                reading  = reading,
                                modifier = Modifier.clickableWithAnimation {
                                    onNavigateToReports(reading.sensorId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extensión local para que la Card sea clickable sin importar un modifier externo
private fun Modifier.clickableWithAnimation(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
