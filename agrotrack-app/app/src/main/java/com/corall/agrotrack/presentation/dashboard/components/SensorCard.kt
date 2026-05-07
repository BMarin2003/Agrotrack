package com.corall.agrotrack.presentation.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.presentation.common.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorCard(
    reading: SensorReading,
    modifier: Modifier = Modifier,
) {
    val statusColor by animateColorAsState(
        targetValue = when (reading.status) {
            SensorStatus.Normal   -> StatusNormal
            SensorStatus.Warning  -> StatusWarning
            SensorStatus.Critical -> StatusCritical
            SensorStatus.Offline  -> StatusOffline
        },
        animationSpec = tween(500),
        label = "statusColor",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(2.dp, statusColor),
        colors   = CardDefaults.cardColors(containerColor = SurfaceMid),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = reading.sensorName.ifBlank { "Sensor #${reading.sensorId}" },
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurfaceLight,
                )
                StatusBadge(status = reading.status, color = statusColor)
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text  = if (reading.temperature != null) "%.1f".format(reading.temperature) else "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = statusColor,
                )
                Text(
                    text     = reading.unit,
                    fontSize = 20.sp,
                    color    = statusColor,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                reading.battery?.let { bat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (bat < 20) {
                            Icon(Icons.Default.BatteryAlert, null,
                                tint = StatusWarning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text  = "Batería ${"%.0f".format(bat)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bat < 20) StatusWarning else OnSurfaceLight.copy(alpha = 0.6f),
                        )
                    }
                }
                Text(
                    text  = formatTimestamp(reading.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceLight.copy(alpha = 0.5f),
                )
            }

            if (reading.status == SensorStatus.Offline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SignalWifiOff, null,
                        tint = StatusOffline, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sin señal", color = StatusOffline,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SensorStatus, color: Color) {
    Surface(
        color  = color.copy(alpha = 0.15f),
        shape  = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text     = status.name.uppercase(),
            color    = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
