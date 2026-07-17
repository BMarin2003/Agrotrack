package com.corall.agrotrack.presentation.installer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    onBack:    () -> Unit,
    viewModel: InstallerViewModel = hiltViewModel(),
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    var gatewayExpanded by remember { mutableStateOf(false) }

    AgroGradient {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                text     = "Configura la red WiFi, el PIN de acceso y el tópico MQTT del gateway.",
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

            // Gateway selector
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
                        containerColor   = CardBg,
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

            // ── WiFi ─────────────────────────────────────────────────
            Text("Red WiFi", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text     = "El gateway se conectará a esta red para enviar datos al servidor.",
                color    = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

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

            uiState.error?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            when {
                uiState.verifying -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Guardado. Verificando conexión del gateway…", color = Muted, fontSize = 12.sp)
                }
                uiState.confirmed -> Text("Conectado correctamente", color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                uiState.success   -> Text(
                    "Guardado. No se detectó conexión del gateway aún — verifica manualmente.",
                    color    = Color(0xFFF59E0B),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick  = viewModel::submitWifi,
                enabled  = !uiState.isSaving && !uiState.verifying && uiState.selectedGatewayId != null,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF0D1B2A), strokeWidth = 2.dp)
                else Text("Guardar WiFi", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
            }

            // ── PIN ──────────────────────────────────────────────────
            HorizontalDivider(color = Muted.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 20.dp))

            Text("PIN de acceso", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text     = "PIN de 4 dígitos para acceder a configuración y calibración en el dispositivo.",
                color    = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value           = uiState.pin,
                onValueChange   = { if (it.length <= 4 && it.all { c -> c.isDigit() }) viewModel.onPinChange(it) },
                label           = { Text("PIN (4 dígitos)") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors          = fieldColors,
                modifier        = Modifier.fillMaxWidth(),
            )

            if (uiState.gateways.size > 1) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Aplicar a todos los gateways", color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked         = uiState.pinAllGateways,
                        onCheckedChange = viewModel::onPinAllGatewaysToggle,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0D1B2A),
                            checkedTrackColor = Cyan,
                        ),
                    )
                }
            }

            uiState.pinError?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (uiState.pinSuccess) Text("PIN configurado correctamente", color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(12.dp))

            Button(
                onClick  = viewModel::submitPin,
                enabled  = !uiState.isPinSaving && uiState.pin.length == 4,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                if (uiState.isPinSaving) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF0D1B2A), strokeWidth = 2.dp)
                else Text("Guardar PIN", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
            }

            // ── Tópico MQTT ──────────────────────────────────────────
            HorizontalDivider(color = Muted.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 20.dp))

            Text("Tópico MQTT", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text     = "Define a qué tópico publica este gateway. Sin aprovisionamiento físico todavía, la confirmación puede tardar o no llegar en un gateway real.",
                color    = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value         = uiState.mqttTopic,
                onValueChange = viewModel::onMqttTopicChange,
                label         = { Text("Tópico (ej: twarm/prueba)") },
                singleLine    = true,
                colors        = fieldColors,
                modifier      = Modifier.fillMaxWidth(),
            )

            uiState.mqttError?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            when (uiState.mqttState) {
                MqttTopicState.PENDING -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Esperando confirmación del gateway…", color = Muted, fontSize = 12.sp)
                }
                MqttTopicState.APPLIED -> Text("✅ Tópico aplicado en el gateway", color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                MqttTopicState.ERROR   -> {} // el mensaje ya sale arriba vía mqttError
                MqttTopicState.NONE    -> {}
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick  = viewModel::submitMqttTopic,
                enabled  = !uiState.isMqttSaving && uiState.mqttState != MqttTopicState.PENDING && uiState.mqttTopic.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                if (uiState.isMqttSaving) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF0D1B2A), strokeWidth = 2.dp)
                else Text("Guardar tópico MQTT", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
