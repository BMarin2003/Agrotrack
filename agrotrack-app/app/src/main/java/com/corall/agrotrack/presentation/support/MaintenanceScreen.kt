package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// HUs: ver historial de mantenimientos, registrar mantenimiento realizado,
//      próxima fecha, notificación 2 semanas antes, marcar como vencido
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    gatewayId: Int,
    onBack:    () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mantenimientos — Gateway #$gatewayId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier          = Modifier.fillMaxSize().padding(padding),
            contentAlignment  = Alignment.Center,
        ) {
            Text(
                text  = "Historial y registro de mantenimientos\n(próximamente)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
