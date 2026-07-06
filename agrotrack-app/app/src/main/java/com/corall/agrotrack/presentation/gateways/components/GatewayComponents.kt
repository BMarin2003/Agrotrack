package com.corall.agrotrack.presentation.gateways.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corall.agrotrack.domain.model.Gateway
import com.corall.agrotrack.domain.model.GatewayConnectivityMode
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.domain.model.SensorSummary
import java.util.Locale
import kotlin.math.roundToInt

private val BackgroundTop = Color(0xFF020B17)
private val BackgroundBottom = Color(0xFF061C31)
private val CardColor = Color(0xFF132238)
private val CardColorAlt = Color(0xFF101D30)
private val Cyan = Color(0xFF62C9FF)
private val SoftCyan = Color(0xFF93DDFF)
private val Green = Color(0xFF22C55E)
private val Blue = Color(0xFF38BDF8)
private val Muted = Color(0xFF94A3B8)
private val White = Color(0xFFF8FAFC)

@Composable
fun AgroGradient(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BackgroundTop, BackgroundBottom),
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun AgroHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AgroTrack",
            color = White,
            fontSize = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = White,
            )
        }
    }
}

@Composable
fun SectionTitle(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SoftCyan,
            modifier = Modifier.size(23.dp),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }

    if (!subtitle.isNullOrBlank()) {
        Text(
            text = subtitle,
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
fun GatewayCard(
    gateway: Gateway,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(10.dp, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Default.Router)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = gateway.name,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )

                Text(
                    text = "${gateway.sensorCount} sensores",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 5.dp),
                ) {
                    GatewayStatusPill(status = gateway.status)
                    ConnectivityIcon(mode = gateway.connectivityMode, modifier = Modifier.padding(start = 6.dp))
                    SyncingPill(pendingCount = gateway.pendingSyncCount, modifier = Modifier.padding(start = 6.dp))
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun SensorCard(
    sensor: SensorSummary,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .then(
                if (highlighted) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(Cyan, Color(0xFF2563EB))),
                        shape = RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFF0E2A4A) else CardColorAlt,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) Cyan.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.05f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(sensorIcon(sensor.type))

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = sensor.name,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )

                Text(
                    text = "${sensorMetricName(sensor.type)}  •  ${sensor.valueText()}",
                    color = Color(0xFFC9D4E3),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(sensorStatusColor(sensor.status), CircleShape),
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Última lectura: ${sensor.lastReadingText()}",
                        color = Muted,
                        fontSize = 10.sp,
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Cyan)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF0B3558), Color(0xFF10243B)),
                ),
                shape = RoundedCornerShape(9.dp),
            )
            .border(
                width = 1.dp,
                color = Color(0xFF1E5F8F),
                shape = RoundedCornerShape(9.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SoftCyan,
            modifier = Modifier.size(31.dp),
        )
    }
}

@Composable
private fun GatewayStatusPill(
    status: GatewayStatus,
    modifier: Modifier = Modifier,
) {
    val text = when (status) {
        GatewayStatus.Online -> "En línea"
        GatewayStatus.Offline -> "Sin conexión"
        GatewayStatus.Maintenance -> "En mantenimiento"
    }

    val color = when (status) {
        GatewayStatus.Online -> Green
        GatewayStatus.Offline -> Color(0xFFEF4444)
        GatewayStatus.Maintenance -> Blue
    }

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )

        Spacer(modifier = Modifier.width(5.dp))

        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ConnectivityIcon(
    mode: GatewayConnectivityMode,
    modifier: Modifier = Modifier,
) {
    if (mode == GatewayConnectivityMode.Unknown) return

    Icon(
        imageVector = if (mode == GatewayConnectivityMode.Wifi) Icons.Default.Wifi else Icons.Default.SimCard,
        contentDescription = if (mode == GatewayConnectivityMode.Wifi) "Conectado por WiFi" else "Conectado por SIM",
        tint = Muted,
        modifier = modifier.size(12.dp),
    )
}

@Composable
private fun SyncingPill(
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pendingCount <= 0) return

    Row(
        modifier = modifier
            .background(Blue.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(8.dp),
            color = Blue,
            strokeWidth = 1.dp,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "Sincronizando ($pendingCount)",
            color = Blue,
            fontSize = 10.sp,
        )
    }
}

private fun sensorIcon(type: String): ImageVector {
    return when (type.lowercase()) {
        "temperature" -> Icons.Default.Thermostat
        "humidity" -> Icons.Default.Opacity
        "voltage" -> Icons.Default.Bolt
        "co2", "pressure" -> Icons.Default.Air
        "cold", "freezer" -> Icons.Default.AcUnit
        else -> Icons.Default.Sensors
    }
}

private fun sensorMetricName(type: String): String {
    return when (type.lowercase()) {
        "temperature" -> "Temperatura"
        "humidity" -> "Humedad"
        "voltage" -> "Consumo energético"
        "co2" -> "Calidad del aire"
        "pressure" -> "Presión"
        else -> "Lectura"
    }
}

private fun sensorStatusColor(status: SensorStatus): Color {
    return when (status) {
        SensorStatus.Normal -> Green
        SensorStatus.Warning -> Color(0xFFF59E0B)
        SensorStatus.Critical -> Color(0xFFEF4444)
        SensorStatus.Offline -> Color(0xFF64748B)
    }
}

private fun SensorSummary.valueText(): String {
    val currentValue = value ?: return "Sin datos"
    val formatted = if (currentValue % 1.0 == 0.0) {
        currentValue.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", currentValue)
    }

    return "$formatted $unit"
}

private fun SensorSummary.lastReadingText(): String {
    val timestamp = receivedAt ?: return "Sin lectura"
    val diff = System.currentTimeMillis() - timestamp

    return when {
        diff < 60_000 -> "Hace menos de 1 min"
        diff < 3_600_000 -> "Hace ${diff / 60_000} min"
        diff < 86_400_000 -> "Hace ${diff / 3_600_000} h"
        else -> "Hace ${diff / 86_400_000} d"
    }
}