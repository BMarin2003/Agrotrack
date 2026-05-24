package com.corall.agrotrack.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole

data class HomeSection(
    val title:       String,
    val icon:        ImageVector,
    val description: String,
    val onClick:     () -> Unit,
    val roles:       Set<UserRole> = setOf(UserRole.OPERATOR, UserRole.TECHNICIAN, UserRole.ADMIN),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDashboard:  () -> Unit,
    onNavigateToGateways:   () -> Unit,
    onNavigateToAlerts:     () -> Unit,
    onNavigateToReports:    () -> Unit,
    onNavigateToInstaller:  () -> Unit,
    onNavigateToSupport:    () -> Unit,
    onNavigateToCalibration:() -> Unit,
    onNavigateToUsers:      () -> Unit,
    onLogout:               () -> Unit,
) {
    val role = SessionManager.currentRole()

    val sections = listOf(
        HomeSection("Monitoreo",    Icons.Default.Sensors,      "Lecturas en tiempo real",           onNavigateToDashboard),
        HomeSection("Gateways",     Icons.Default.Router,       "Estado y sensores por gateway",      onNavigateToGateways),
        HomeSection("Alertas",      Icons.Default.Notifications,"Alertas activas e historial",        onNavigateToAlerts),
        HomeSection("Reportes",     Icons.Default.BarChart,     "Histórico y descarga de datos",      onNavigateToReports),
        HomeSection("Instalación",  Icons.Default.Settings,     "WiFi, PIN y tópicos MQTT",           onNavigateToInstaller,
            roles = setOf(UserRole.TECHNICIAN, UserRole.ADMIN)),
        HomeSection("Soporte",      Icons.Default.Build,        "Mantenimientos y mesa de ayuda",     onNavigateToSupport,
            roles = setOf(UserRole.TECHNICIAN, UserRole.ADMIN)),
        HomeSection("Calibración",  Icons.Default.Tune,         "Parámetros remotos de sensores",    onNavigateToCalibration,
            roles = setOf(UserRole.TECHNICIAN, UserRole.ADMIN)),
        HomeSection("Usuarios",     Icons.Default.People,       "Gestión de cuentas y perfiles",      onNavigateToUsers,
            roles = setOf(UserRole.ADMIN)),
    ).filter { role in it.roles }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AgroTrack") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text  = "Bienvenido, ${SessionManager.getUserName()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sections) { section ->
                    SectionCard(section)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: HomeSection) {
    Card(
        onClick  = section.onClick,
        modifier = Modifier.fillMaxWidth().height(110.dp),
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(section.title,       style = MaterialTheme.typography.titleSmall)
                Text(section.description, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1)
            }
        }
    }
}
