package com.corall.agrotrack.presentation.thresholds

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// HUs: configurar umbral mínimo, umbral máximo, activar/desactivar alertas por sensor,
//      umbrales independientes por operador
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdsScreen(
    sensorId: Int,
    onBack:   () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Umbrales — Sensor #$sensorId") },
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
                text  = "Configuración de umbrales y alertas\n(próximamente)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
