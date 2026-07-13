package com.corall.agrotrack.presentation.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.domain.model.SensorStatus
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.LoadingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Cyan    = Color(0xFF62C9FF)
private val SoftCyan = Color(0xFF93DDFF)
private val Green   = Color(0xFF22C55E)
private val Muted   = Color(0xFF94A3B8)
private val White   = Color(0xFFF8FAFC)
private val CardBg  = Color(0xFF132238)

@Composable
fun SensorDetailScreen(
    sensorId:           Int,
    onBack:             () -> Unit,
    onConfigThresholds: (Int) -> Unit,
    onCalibrate:        (Int) -> Unit,
    viewModel:          SensorDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AgroGradient {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            AgroHeader(onBack = onBack)

            if (uiState.isLoading) {
                LoadingState()
                return@Column
            }

            Spacer(Modifier.height(8.dp))

            // Nombre y ubicación
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = uiState.sensorName,
                        color      = White,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (uiState.location.isNotBlank()) {
                        Text(
                            text     = uiState.location,
                            color    = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                IconButton(onClick = { viewModel.openAliasDialog() }) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar alias", tint = SoftCyan)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Tarjeta principal de lectura
            ReadingCard(uiState = uiState)

            Spacer(Modifier.height(16.dp))

            // Timestamp última lectura — tocar para alternar entre formato relativo y absoluto
            var showAbsoluteTimestamp by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { showAbsoluteTimestamp = !showAbsoluteTimestamp },
            ) {
                Text(
                    text     = "Última lectura: " + if (showAbsoluteTimestamp)
                        absoluteReadingText(uiState.receivedAt)
                    else
                        lastReadingText(uiState.receivedAt),
                    color    = Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Cambiar formato de fecha",
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Métricas secundarias
            if (uiState.voltage != null || uiState.battery != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.battery?.let { bat ->
                        MetricChip(
                            icon  = Icons.Default.BatteryFull,
                            label = "Batería",
                            value = "${bat.roundToInt()} %",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    uiState.voltage?.let { v ->
                        MetricChip(
                            icon  = Icons.Default.Bolt,
                            label = "Voltaje",
                            value = String.format(Locale.US, "%.2f V", v),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                uiState.voltage?.let { v ->
                    val level = batteryWarningLevel(v)
                    if (level != BatteryWarningLevel.NORMAL) {
                        Spacer(Modifier.height(8.dp))
                        val (color, text) = if (level == BatteryWarningLevel.CRITICAL)
                            Color(0xFFEF4444) to "Batería crítica — reemplazar de inmediato"
                        else
                            Color(0xFFF59E0B) to "Voltaje bajo — reemplazar batería pronto"

                        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Spacer(Modifier.weight(1f))

            // Botones de acción
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp),
            ) {
                OutlinedButton(
                    onClick  = { onConfigThresholds(sensorId) },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = SoftCyan),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, SoftCyan.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Default.Tune, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Umbrales", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick  = { onCalibrate(sensorId) },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Muted.copy(alpha = 0.3f)),
                ) {
                    Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Calibrar", fontSize = 13.sp)
                }
            }
        }
    }

    if (uiState.aliasDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeAliasDialog() },
            title = { Text("Editar alias del sensor") },
            text = {
                Column {
                    OutlinedTextField(
                        value         = uiState.aliasInput,
                        onValueChange = viewModel::onAliasInputChange,
                        singleLine    = true,
                        label         = { Text("Nombre personalizado") },
                    )
                    uiState.aliasError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveAlias, enabled = !uiState.aliasSaving) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeAliasDialog() }) { Text("Cancelar") }
            },
        )
    }
}

private const val LOW_VOLTAGE_THRESHOLD      = 3.5
private const val CRITICAL_VOLTAGE_THRESHOLD = 3.3

private enum class BatteryWarningLevel { NORMAL, LOW, CRITICAL }

private fun batteryWarningLevel(voltage: Double): BatteryWarningLevel = when {
    voltage < CRITICAL_VOLTAGE_THRESHOLD -> BatteryWarningLevel.CRITICAL
    voltage < LOW_VOLTAGE_THRESHOLD      -> BatteryWarningLevel.LOW
    else                                 -> BatteryWarningLevel.NORMAL
}

@Composable
private fun ReadingCard(uiState: SensorDetailUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B3558), Color(0xFF0E2A4A))),
                RoundedCornerShape(16.dp),
            )
            .border(1.dp, Cyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = null,
                tint    = SoftCyan,
                modifier = Modifier.size(36.dp),
            )

            Spacer(Modifier.height(12.dp))

            if (uiState.temperature != null) {
                Text(
                    text       = String.format(Locale.US, "%.1f %s", uiState.temperature, uiState.unit),
                    color      = White,
                    fontSize   = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text     = "Sin datos",
                    color    = Muted,
                    fontSize = 28.sp,
                )
            }

            Spacer(Modifier.height(10.dp))

            StatusPill(uiState.status)
        }
    }
}

@Composable
private fun StatusPill(status: SensorStatus) {
    val (text, color) = when (status) {
        SensorStatus.Normal   -> "En línea"     to Green
        SensorStatus.Warning  -> "Advertencia"  to Color(0xFFF59E0B)
        SensorStatus.Critical -> "Crítico"      to Color(0xFFEF4444)
        SensorStatus.Offline  -> "Desconectado" to Color(0xFF64748B)
    }

    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricChip(
    icon:     ImageVector,
    label:    String,
    value:    String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(CardBg, RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SoftCyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Muted,  fontSize = 10.sp)
            Text(value, color = White,  fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun lastReadingText(receivedAt: Long?): String {
    if (receivedAt == null || receivedAt <= 0L) return "Sin lectura"
    val diff = System.currentTimeMillis() - receivedAt
    return when {
        diff < 60_000     -> "Hace menos de 1 min"
        diff < 3_600_000  -> "Hace ${diff / 60_000} min"
        diff < 86_400_000 -> "Hace ${diff / 3_600_000} h"
        else              -> "Hace ${diff / 86_400_000} d"
    }
}

private fun absoluteReadingText(receivedAt: Long?): String {
    if (receivedAt == null || receivedAt <= 0L) return "Sin lectura"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(receivedAt))
}
