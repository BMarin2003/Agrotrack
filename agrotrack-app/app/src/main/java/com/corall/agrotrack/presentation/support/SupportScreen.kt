package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.presentation.gateways.components.AgroGradient
import com.corall.agrotrack.presentation.gateways.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)
private val Blue   = Color(0xFF38BDF8)

// HUs: listado de gateways gestionados, estado, mantenimiento
@Composable
fun SupportScreen(
    onBack:               () -> Unit,
    onGatewayClick:       (gatewayId: Int) -> Unit,
    onNavigateToHelpDesk: () -> Unit,
    viewModel:            SupportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AgroGradient {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(top = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "AgroTrack",
                    color      = White,
                    fontSize   = 29.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier   = Modifier.weight(1f),
                )
                IconButton(onClick = onNavigateToHelpDesk) {
                    Icon(Icons.Default.HeadsetMic, contentDescription = "Mesa de ayuda", tint = Cyan)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = White)
                }
            }

            Text(
                text       = "Soporte Técnico",
                color      = White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Vista global de gateways y mantenimientos.",
                color    = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error ?: "Error", color = Red)
                    }
                }
                uiState.gateways.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay gateways registrados", color = Muted)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.gateways, key = { it.id }) { gateway ->
                            GatewayMaintenanceCard(
                                gateway = gateway,
                                onClick = { onGatewayClick(gateway.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayMaintenanceCard(gateway: Gateway, onClick: () -> Unit) {
    val statusColor = when (gateway.status) {
        GatewayStatus.Online      -> Green
        GatewayStatus.Offline     -> Red
        GatewayStatus.Maintenance -> Blue
    }
    val statusLabel = when (gateway.status) {
        GatewayStatus.Online      -> "En línea"
        GatewayStatus.Offline     -> "Sin conexión"
        GatewayStatus.Maintenance -> "Mantenimiento"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        shape    = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Router, contentDescription = null, tint = Cyan, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(gateway.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (gateway.location.isNotBlank()) {
                    Text(gateway.location, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text     = statusLabel,
                            color    = statusColor,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text("${gateway.sensorCount} sensores", color = Muted, fontSize = 11.sp)
                }
            }
            Icon(
                Icons.Default.Build,
                contentDescription = "Ver mantenimientos",
                tint               = Muted.copy(alpha = 0.6f),
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}
