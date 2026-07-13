package com.corall.agrotrack.presentation.calibration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Orange = Color(0xFFF59E0B)

@Composable
fun CalibrationScreen(
    sensorId: Int,
    onBack:   () -> Unit,
    vm:       CalibrationViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.success) {
        if (uiState.success) snackbarHost.showSnackbar("Calibración aplicada correctamente")
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHost.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { innerPadding ->
        AgroGradient {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
            ) {
                AgroHeader(onBack = onBack)

                Text(
                    text       = "Calibración — Sensor #$sensorId",
                    color      = White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = "Ajusta los parámetros del sensor de temperatura.",
                    color    = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                Surface(color = Color(0xFF1A2A1A), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text("ⓘ", color = Orange, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text     = "Fórmula: T_corregida = T_raw × ganancia + intercepto",
                            color    = Orange.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                        )
                    }
                }

                uiState.lastApplied?.let { date ->
                    Spacer(Modifier.height(8.dp))
                    Surface(color = CardBg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text     = "Última calibración: $date",
                            color    = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Cyan,
                    unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                    focusedTextColor     = White,
                    unfocusedTextColor   = White,
                    focusedLabelColor    = Cyan,
                    unfocusedLabelColor  = Muted,
                    cursorColor          = Cyan,
                )

                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.padding(top = 32.dp))
                } else {
                    OutlinedTextField(
                        value           = uiState.gain,
                        onValueChange   = vm::onGainChange,
                        label           = { Text("Ganancia") },
                        supportingText  = { Text("Factor de escala (ej: 1.02 para +2%)", color = Muted, fontSize = 11.sp) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = fieldColors,
                        modifier        = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value           = uiState.intercept,
                        onValueChange   = vm::onInterceptChange,
                        label           = { Text("Intercepto (°C)") },
                        supportingText  = { Text("Offset fijo sumado al valor corregido", color = Muted, fontSize = 11.sp) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = fieldColors,
                        modifier        = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value         = uiState.notes,
                        onValueChange = vm::onNotesChange,
                        label         = { Text("Notas (opcional)") },
                        minLines      = 2,
                        colors        = fieldColors,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick  = vm::save,
                    enabled  = !uiState.isSaving && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(color = Color(0xFF0D1B2A), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Enviar calibración", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
