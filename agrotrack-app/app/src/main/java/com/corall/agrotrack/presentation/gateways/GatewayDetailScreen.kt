package com.corall.agrotrack.presentation.gateways

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// HUs: ver listado de sensores de un gateway, temperatura actual, batería, timestamp,
//      estado del sensor, voltaje, nombre personalizado
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayDetailScreen(
    gatewayId:      Int,
    onBack:         () -> Unit,
    onSensorClick:  (sensorId: Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gateway #$gatewayId") },
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
                text  = "Detalle de gateway y sensores\n(próximamente)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
