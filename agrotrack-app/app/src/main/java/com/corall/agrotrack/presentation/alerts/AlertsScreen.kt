package com.corall.agrotrack.presentation.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.domain.model.Alert
import com.corall.agrotrack.presentation.common.theme.StatusCritical
import com.corall.agrotrack.presentation.common.theme.StatusNormal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title   = { Text("Eliminar todas las alertas") },
            text    = { Text("Se eliminarán ${uiState.alerts.size} alertas permanentemente. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showConfirm = false }) {
                    Text("Eliminar", color = StatusCritical)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (uiState.selectedTab == AlertsTab.ACTIVE && uiState.alerts.isNotEmpty() && uiState.canResolve) {
                        IconButton(onClick = { showConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Eliminar todas",
                                tint = StatusCritical,
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == AlertsTab.ACTIVE,
                    onClick  = { viewModel.selectTab(AlertsTab.ACTIVE) },
                    text     = { Text("Activas") },
                )
                Tab(
                    selected = uiState.selectedTab == AlertsTab.HISTORY,
                    onClick  = { viewModel.selectTab(AlertsTab.HISTORY) },
                    text     = { Text("Historial") },
                )
            }

            when (uiState.selectedTab) {
                AlertsTab.ACTIVE -> AlertsList(
                    alerts     = uiState.alerts,
                    canResolve = uiState.canResolve,
                    onResolve  = viewModel::resolve,
                    emptyText  = "Sin alertas activas",
                )
                AlertsTab.HISTORY -> when {
                    uiState.isLoadingHistory -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    uiState.historyError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.historyError ?: "Error al cargar el historial", color = StatusCritical)
                    }
                    else -> AlertsList(
                        alerts     = uiState.historyAlerts,
                        canResolve = false,
                        onResolve  = {},
                        emptyText  = "Sin alertas registradas",
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertsList(
    alerts:     List<Alert>,
    canResolve: Boolean,
    onResolve:  (Long) -> Unit,
    emptyText:  String,
) {
    if (alerts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(alerts, key = { it.id }) { alert ->
            AlertRow(alert = alert, canResolve = canResolve, onResolve = onResolve)
        }
    }
}

@Composable
private fun AlertRow(
    alert:      Alert,
    canResolve: Boolean,
    onResolve:  (Long) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (alert.resolved) StatusNormal.copy(0.1f)
                             else StatusCritical.copy(0.1f)
        )
    ) {
        Row(
            modifier              = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Sensor ${alert.sensorId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (!alert.resolved && canResolve) {
                IconButton(onClick = { onResolve(alert.id) }) {
                    Icon(Icons.Default.CheckCircle, null, tint = StatusNormal)
                }
            }
        }
    }
}
