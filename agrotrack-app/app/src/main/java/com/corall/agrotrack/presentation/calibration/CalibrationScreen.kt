package com.corall.agrotrack.presentation.calibration

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// HUs: enviar ganancia remotamente, enviar intercepto, confirmación de parámetros aplicados,
//      error si no pudo recibir calibración, ver parámetros vigentes
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    sensorId: Int,
    onBack:   () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibración — Sensor #$sensorId") },
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
                text  = "Ganancia · Intercepto · Confirmación\n(próximamente)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
