package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// HUs: listado de todos los gateways gestionados, estado actual de cada gateway,
//      gateways con mantenimiento vencido resaltados, próximo mantenimiento, mesa de ayuda
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack:              () -> Unit,
    onGatewayClick:      (gatewayId: Int) -> Unit,
    onNavigateToHelpDesk:() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soporte Técnico") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHelpDesk) {
                        Icon(Icons.Default.SupportAgent, contentDescription = "Mesa de ayuda")
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
                text  = "Vista global de gateways y mantenimientos\n(próximamente)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
