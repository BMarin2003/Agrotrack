package com.corall.agrotrack.presentation.gateways

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.corall.agrotrack.presentation.gateways.components.AgroGradient
import com.corall.agrotrack.presentation.gateways.components.AgroHeader
import com.corall.agrotrack.presentation.gateways.components.EmptyState
import com.corall.agrotrack.presentation.gateways.components.LoadingState
import com.corall.agrotrack.presentation.gateways.components.SensorCard
import com.corall.agrotrack.domain.model.SensorStatus

private val Cyan     = Color(0xFF62C9FF)
private val SoftCyan = Color(0xFF93DDFF)
private val Muted    = Color(0xFF94A3B8)
private val White    = Color(0xFFF8FAFC)
private val DarkBg   = Color(0xFF0D1B2A)

private val SECURITY_OPTIONS = listOf("WPA2", "WPA3", "open")

private fun batteryColor(battery: Double): Color = when {
    battery >= 50 -> Color(0xFF22C55E)
    battery >= 20 -> Color(0xFFF59E0B)
    else          -> Color(0xFFEF4444)
}

private enum class SensorStatusFilter(val label: String) {
    ALL("Todos"),
    ACTIVE("Activos"),
    INACTIVE("Inactivos"),
    SUSPICIOUS("Sospechosos"),
}

private fun matchesFilter(status: SensorStatus, filter: SensorStatusFilter): Boolean = when (filter) {
    SensorStatusFilter.ALL        -> true
    SensorStatusFilter.ACTIVE     -> status == SensorStatus.Normal
    SensorStatusFilter.INACTIVE   -> status == SensorStatus.Offline
    SensorStatusFilter.SUSPICIOUS -> status == SensorStatus.Warning || status == SensorStatus.Critical
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayDetailScreen(
    gatewayId:     Int,
    onBack:        () -> Unit,
    onSensorClick: (Int) -> Unit,
    viewModel:     GatewayDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(gatewayId) {
        viewModel.start(gatewayId)
    }

    AgroGradient {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            AgroHeader(onBack = onBack)

            Text(
                text     = "Bienvenido, Operador",
                color    = Color(0xFFB6C2D1),
                fontSize = 14.sp,
            )

            // Sección sensores con botón WiFi al lado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            ) {
                Icon(Icons.Default.Sensors, null, tint = SoftCyan, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = "Sensores del gateway",
                    color      = White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.openWifiDialog() }) {
                    Icon(
                        imageVector        = Icons.Default.Wifi,
                        contentDescription = "Configurar WiFi",
                        tint               = SoftCyan,
                        modifier           = Modifier.size(22.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text     = uiState.gatewayName.ifBlank { "Gateway #$gatewayId" },
                    color    = Color(0xFFB6C2D1),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                uiState.gatewayBattery?.let { bat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF132238), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint     = batteryColor(bat),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = "${bat.toInt()} %",
                            color    = batteryColor(bat),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            var selectedFilter by remember { mutableStateOf(SensorStatusFilter.ALL) }
            val filteredSensors = uiState.sensors.filter { matchesFilter(it.status, selectedFilter) }

            if (uiState.sensors.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    SensorStatusFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick  = { selectedFilter = filter },
                            label    = { Text(filter.label, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan,
                                selectedLabelColor      = DarkBg,
                            ),
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> LoadingState()

                uiState.error != null -> EmptyState(
                    text = uiState.error ?: "No se pudieron cargar los sensores",
                )

                uiState.sensors.isEmpty() -> EmptyState(
                    text = "No hay sensores disponibles",
                )

                filteredSensors.isEmpty() -> EmptyState(
                    text = "Ningún sensor coincide con \"${selectedFilter.label}\"",
                )

                else -> {
                    LazyColumn(
                        modifier        = Modifier
                            .fillMaxSize()
                            .padding(top = 18.dp),
                        contentPadding  = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = filteredSensors,
                            key   = { _, sensor -> sensor.id },
                        ) { index, sensor ->
                            SensorCard(
                                sensor      = sensor,
                                highlighted = index == 0,
                                onClick     = { onSensorClick(sensor.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo WiFi
    if (uiState.wifiDialogOpen) {
        WifiConfigDialog(
            isSaving  = uiState.wifiSaving,
            error     = uiState.wifiError,
            onDismiss = { viewModel.closeWifiDialog() },
            onConfirm = { ssid, password, security ->
                viewModel.saveWifi(ssid, password.ifBlank { null }, security)
            },
        )
    }

    // Snackbar de éxito (simple Toast alternativo con LaunchedEffect)
    if (uiState.wifiSuccess) {
        LaunchedEffect(Unit) {
            // El estado se resetea al abrir el diálogo de nuevo; no se necesita acción extra.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiConfigDialog(
    isSaving:  Boolean,
    error:     String?,
    onDismiss: () -> Unit,
    onConfirm: (ssid: String, password: String, security: String) -> Unit,
) {
    var ssid     by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("WPA2") }
    var expanded by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = Cyan,
        unfocusedBorderColor = Muted.copy(alpha = 0.4f),
        focusedLabelColor    = Cyan,
        unfocusedLabelColor  = Muted,
        focusedTextColor     = White,
        unfocusedTextColor   = White,
        cursorColor          = Cyan,
    )

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor   = DarkBg,
        titleContentColor = White,
        textContentColor  = Muted,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, tint = SoftCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Configurar WiFi del gateway", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = ssid,
                    onValueChange = { ssid = it },
                    label         = { Text("SSID (nombre de red)") },
                    singleLine    = true,
                    colors        = fieldColors,
                    modifier      = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value               = password,
                    onValueChange       = { password = it },
                    label               = { Text("Contraseña (opcional)") },
                    singleLine          = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors              = fieldColors,
                    modifier            = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded        = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value         = security,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Seguridad") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors        = fieldColors,
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded        = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        SECURITY_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text    = { Text(option) },
                                onClick = { security = option; expanded = false },
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(error, color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(ssid, password, security) },
                enabled  = ssid.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                } else {
                    Text("Guardar", color = Cyan, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar", color = Muted)
            }
        },
    )
}
