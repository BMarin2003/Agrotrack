package com.corall.agrotrack.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole

/**
 * Pantalla exclusiva para TECHNICIAN.
 * La navegación a esta pantalla ya está bloqueada en AppNavGraph cuando el rol es OPERATOR.
 * Este check es una segunda línea de defensa en la UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val role = SessionManager.currentRole()

    if (role != UserRole.TECHNICIAN) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Acceso restringido", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración Técnica") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Umbrales de temperatura", style = MaterialTheme.typography.titleLarge)
            // TODO: ThresholdEditor composable
            HorizontalDivider()
            Text("Gestión de sensores", style = MaterialTheme.typography.titleLarge)
            // TODO: SensorManager composable
            HorizontalDivider()
            Text("Diagnóstico de conexión", style = MaterialTheme.typography.titleLarge)
            // TODO: DiagnosticsPanel composable
        }
    }
}
