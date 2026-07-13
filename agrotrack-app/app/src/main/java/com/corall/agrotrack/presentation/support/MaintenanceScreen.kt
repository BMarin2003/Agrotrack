package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.data.remote.dto.MaintenanceRecordDto
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)

@Composable
fun MaintenanceScreen(
    gatewayId: Int,
    onBack:    () -> Unit,
    vm:        MaintenanceViewModel = hiltViewModel(),
) {
    val uiState  by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.success) {
        if (uiState.success) snackbar.showSnackbar("Mantenimiento registrado")
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
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

                val nextScheduled = uiState.records.firstOrNull()?.nextScheduled
                Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Próximo mantenimiento", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text     = nextScheduled ?: "No programado",
                            color    = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Historial", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cyan)
                    }
                } else {
                    LazyColumn(
                        modifier            = Modifier.weight(1f),
                        contentPadding      = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.records.isEmpty()) {
                            item {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Sin registros de mantenimiento", color = Muted, fontSize = 13.sp)
                                }
                            }
                        } else {
                            items(uiState.records) { record ->
                                MaintenanceRecordCard(record)
                            }
                        }
                    }
                }

                Text("Registrar mantenimiento", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value         = uiState.notes,
                    onValueChange = vm::onNotesChange,
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

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick  = vm::register,
                    enabled  = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(color = Color(0xFF0D1B2A), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Registrar mantenimiento", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceRecordCard(record: MaintenanceRecordDto) {
    Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text      = record.performedAt.take(10),
                color     = Cyan,
                fontSize  = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            record.notes?.let { notes ->
                Spacer(Modifier.height(4.dp))
                Text(text = notes, color = Color(0xFFCBD5E1), fontSize = 13.sp)
            }
            record.nextScheduled?.let { next ->
                Spacer(Modifier.height(6.dp))
                Text(text = "Próximo: $next", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
