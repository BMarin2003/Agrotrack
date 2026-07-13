package com.corall.agrotrack.presentation.thresholds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.LoadingState

private val Cyan     = Color(0xFF62C9FF)
private val SoftCyan = Color(0xFF93DDFF)
private val Muted    = Color(0xFF94A3B8)
private val White    = Color(0xFFF8FAFC)
private val CardBg   = Color(0xFF132238)
private val Green    = Color(0xFF22C55E)
private val Red      = Color(0xFFEF4444)

@Composable
fun ThresholdsScreen(
    sensorId:  Int,
    onBack:    () -> Unit,
    viewModel: ThresholdsViewModel = hiltViewModel(),
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

            Text(
                text       = "Umbrales — Sensor #$sensorId",
                color      = White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Configura los límites de temperatura y las alertas.",
                color    = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(28.dp))

            // Toggle alertas
            Surface(
                color  = CardBg,
                shape  = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (uiState.alertsEnabled) Icons.Default.NotificationsActive
                                      else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint     = if (uiState.alertsEnabled) Green else Muted,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "Alertas activas",
                            color      = White,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text     = if (uiState.alertsEnabled) "Se notifica al superar los umbrales"
                                       else "Las alertas están desactivadas",
                            color    = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked         = uiState.alertsEnabled,
                        onCheckedChange = viewModel::onToggleAlerts,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor       = White,
                            checkedTrackColor       = Green,
                            uncheckedThumbColor     = Muted,
                            uncheckedTrackColor     = Muted.copy(alpha = 0.3f),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Cyan,
                unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                focusedLabelColor    = Cyan,
                unfocusedLabelColor  = Muted,
                focusedTextColor     = White,
                unfocusedTextColor   = White,
                cursorColor          = Cyan,
            )

            OutlinedTextField(
                value         = uiState.minThreshold,
                onValueChange = viewModel::onMinChange,
                label         = { Text("Umbral mínimo (°C)") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors        = fieldColors,
                modifier      = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value         = uiState.maxThreshold,
                onValueChange = viewModel::onMaxChange,
                label         = { Text("Umbral máximo (°C)") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors        = fieldColors,
                modifier      = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = Red, fontSize = 12.sp)
            }

            if (uiState.saveSuccess) {
                Spacer(Modifier.height(8.dp))
                Text("Configuración guardada", color = Green, fontSize = 12.sp)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick  = viewModel::save,
                enabled  = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = White, strokeWidth = 2.dp)
                } else {
                    Text("Guardar configuración", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
