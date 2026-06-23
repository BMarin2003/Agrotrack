package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

// HUs: ver historial, registrar mantenimiento, próxima fecha, notificación 2 semanas antes
@Composable
fun MaintenanceScreen(
    gatewayId: Int,
    onBack:    () -> Unit,
) {
    var notes by remember { mutableStateOf("") }

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
                    text       = "Mantenimientos — Gateway #$gatewayId",
                    color      = White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = "Registra y consulta el historial de mantenimientos.",
                    color    = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                LazyColumn(
                    modifier        = Modifier.weight(1f),
                    contentPadding  = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Próximo mantenimiento placeholder
                    item {
                        Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Próximo mantenimiento", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("No programado", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    // Historial placeholder
                    item {
                        Text("Historial", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Sin registros de mantenimiento", color = Muted, fontSize = 13.sp)
                        }
                    }
                }

                // Registro de mantenimiento
                Text("Registrar mantenimiento", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text("Observaciones") },
                    minLines      = 3,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Cyan,
                        unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                        focusedTextColor     = White,
                        unfocusedTextColor   = White,
                        focusedLabelColor    = Cyan,
                        unfocusedLabelColor  = Muted,
                        cursorColor          = Cyan,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Surface(color = Color(0xFF1A1A00), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text     = "Endpoint pendiente: POST /sensors/gateways/$gatewayId/maintenance",
                        color    = Orange.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Endpoint de mantenimiento pendiente en el servidor")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
                ) {
                    Text("Registrar mantenimiento", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
