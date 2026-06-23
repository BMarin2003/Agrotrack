package com.corall.agrotrack.presentation.installer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.gateways.components.AgroGradient
import com.corall.agrotrack.presentation.gateways.components.AgroHeader
import com.corall.agrotrack.presentation.gateways.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)

// HUs: configurar red WiFi, confirmación de conexión exitosa, error WiFi específico
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    onBack:    () -> Unit,
    viewModel: InstallerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var gatewayExpanded by remember { mutableStateOf(false) }

    AgroGradient {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            AgroHeader(onBack = onBack)

            Text(
                text       = "Configuración de Instalación",
                color      = White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Configura la red WiFi del gateway seleccionado.",
                color    = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            if (uiState.isLoading) { LoadingState(); return@Column }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Cyan,
                unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                focusedTextColor     = White,
                unfocusedTextColor   = White,
                focusedLabelColor    = Cyan,
                unfocusedLabelColor  = Muted,
                cursorColor          = Cyan,
            )

            // Gateway selector (solo si hay más de uno)
            if (uiState.gateways.size > 1) {
                val selectedName = uiState.gateways.firstOrNull { it.id == uiState.selectedGatewayId }?.name ?: "Seleccionar"
                ExposedDropdownMenuBox(expanded = gatewayExpanded, onExpandedChange = { gatewayExpanded = it }) {
                    OutlinedTextField(
                        value         = selectedName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Gateway") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gatewayExpanded) },
                        colors        = fieldColors,
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded         = gatewayExpanded,
                        onDismissRequest = { gatewayExpanded = false },
                        containerColor   = Color(0xFF132238),
                    ) {
                        uiState.gateways.forEach { gw ->
                            DropdownMenuItem(
                                text    = { Text(gw.name, color = White) },
                                onClick = { viewModel.selectGateway(gw.id); gatewayExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (uiState.gateways.size == 1) {
                Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Gateway", color = Muted, fontSize = 11.sp)
                        Text(uiState.gateways.first().name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // WiFi config
            OutlinedTextField(
                value         = uiState.ssid,
                onValueChange = viewModel::onSsidChange,
                label         = { Text("SSID (nombre de la red)") },
                singleLine    = true,
                colors        = fieldColors,
                modifier      = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value                = uiState.password,
                onValueChange        = viewModel::onPasswordChange,
                label                = { Text("Contraseña WiFi") },
                placeholder          = { Text("Dejar en blanco si red abierta", color = Muted) },
                singleLine           = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors               = fieldColors,
                modifier             = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = Red, fontSize = 12.sp)
            }

            if (uiState.success) {
                Spacer(Modifier.height(8.dp))
                Text("Configuración WiFi guardada correctamente", color = Green, fontSize = 12.sp)
            }

            Spacer(Modifier.weight(1f))

            // PIN pendiente
            Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Configuración de PIN", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Pendiente en el servidor", color = Muted.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick  = viewModel::submit,
                enabled  = !uiState.isSaving && uiState.selectedGatewayId != null,
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = White, strokeWidth = 2.dp)
                } else {
                    Text("Guardar configuración WiFi", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
