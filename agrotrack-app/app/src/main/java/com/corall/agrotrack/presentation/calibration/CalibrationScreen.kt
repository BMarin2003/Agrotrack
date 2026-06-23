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
import com.corall.agrotrack.presentation.gateways.components.AgroGradient
import com.corall.agrotrack.presentation.gateways.components.AgroHeader
import kotlinx.coroutines.launch

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Orange = Color(0xFFF59E0B)

// HUs: enviar ganancia, enviar intercepto, confirmación de parámetros aplicados
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    sensorId: Int,
    onBack:   () -> Unit,
) {
    var gain      by remember { mutableStateOf("1.0") }
    var intercept by remember { mutableStateOf("0.0") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
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

                // Info card
                Surface(color = Color(0xFF1A2A1A), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text("ⓘ", color = Orange, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text     = "Fórmula aplicada: T_corregida = T_raw × ganancia + intercepto",
                            color    = Orange.copy(alpha = 0.9f),
                            fontSize = 12.sp,
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

                OutlinedTextField(
                    value           = gain,
                    onValueChange   = { gain = it },
                    label           = { Text("Ganancia") },
                    supportingText  = { Text("Factor de escala (ej: 1.02 para +2%)", color = Muted, fontSize = 11.sp) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors          = fieldColors,
                    modifier        = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value           = intercept,
                    onValueChange   = { intercept = it },
                    label           = { Text("Intercepto (°C)") },
                    supportingText  = { Text("Offset fijo sumado al valor corregido", color = Muted, fontSize = 11.sp) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors          = fieldColors,
                    modifier        = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.weight(1f))

                // Endpoint pendiente — formulario listo, se conecta cuando el backend implemente calibración
                Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Endpoint pendiente en el servidor", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("La UI está lista. Se activará al implementar POST /sensors/:id/calibration", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Endpoint de calibración pendiente en el servidor")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
                ) {
                    Text("Enviar calibración", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
