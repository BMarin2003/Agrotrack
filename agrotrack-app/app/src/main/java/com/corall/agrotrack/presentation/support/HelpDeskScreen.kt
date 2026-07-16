package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.corall.agrotrack.data.remote.dto.TicketDto
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Red    = Color(0xFFEF4444)

private val STATUSES = listOf("abierto", "en_progreso", "resuelto", "cerrado")

private fun statusLabel(status: String) = when (status) {
    "abierto"     -> "Abierto"
    "en_progreso" -> "En progreso"
    "resuelto"    -> "Resuelto"
    "cerrado"     -> "Cerrado"
    else          -> status
}

private fun statusColor(status: String) = when (status) {
    "abierto"     -> Color(0xFFF59E0B)
    "en_progreso" -> Cyan
    "resuelto"    -> Color(0xFF22C55E)
    "cerrado"     -> Muted
    else          -> Muted
}

// HU26: mesa de ayuda — tickets de soporte al cliente
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDeskScreen(
    onBack: () -> Unit,
    vm:     HelpDeskViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    if (uiState.showNewTicketDialog) {
        NewTicketDialog(uiState = uiState, vm = vm)
    }

    AgroGradient {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            AgroHeader(onBack = onBack)

            Text(
                text       = "Mesa de Ayuda",
                color      = White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Gestiona solicitudes y consultas de los clientes.",
                color    = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Button(
                onClick  = vm::openNewTicketDialog,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0D1B2A))
                Spacer(Modifier.width(6.dp))
                Text("Nuevo ticket", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error ?: "Error", color = Red)
                    }
                }
                uiState.tickets.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin tickets registrados", color = Muted)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.tickets, key = { it.id }) { ticket ->
                            TicketCard(ticket = ticket, onStatusChange = { status -> vm.updateStatus(ticket.id, status) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: TicketDto, onStatusChange: (String) -> Unit) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ticket.subject, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

                Box {
                    Surface(
                        color    = statusColor(ticket.status).copy(alpha = 0.15f),
                        shape    = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.clickable { statusMenuExpanded = true },
                    ) {
                        Text(
                            text     = statusLabel(ticket.status),
                            color    = statusColor(ticket.status),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    DropdownMenu(
                        expanded         = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false },
                        containerColor   = CardBg,
                    ) {
                        STATUSES.forEach { status ->
                            DropdownMenuItem(
                                text    = { Text(statusLabel(status), color = statusColor(status)) },
                                onClick = { statusMenuExpanded = false; onStatusChange(status) },
                            )
                        }
                    }
                }
            }

            ticket.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(6.dp))
                Text(desc, color = Color(0xFFCBD5E1), fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row {
                ticket.gatewayName?.let { Text("Gateway: $it", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp)) }
                Text("Por: ${ticket.createdByName ?: "—"}", color = Muted, fontSize = 11.sp)
            }
            Text(ticket.createdAt.take(16).replace('T', ' '), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTicketDialog(uiState: HelpDeskUiState, vm: HelpDeskViewModel) {
    var gatewayExpanded by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = Cyan,
        unfocusedBorderColor = Muted.copy(alpha = 0.4f),
        focusedTextColor     = White,
        unfocusedTextColor   = White,
        focusedLabelColor    = Cyan,
        unfocusedLabelColor  = Muted,
        cursorColor          = Cyan,
    )

    AlertDialog(
        onDismissRequest = vm::closeNewTicketDialog,
        containerColor   = CardBg,
        title            = { Text("Nuevo ticket", color = White) },
        text = {
            Column {
                OutlinedTextField(
                    value         = uiState.newSubject,
                    onValueChange = vm::onSubjectChange,
                    label         = { Text("Asunto") },
                    singleLine    = true,
                    colors        = fieldColors,
                    modifier      = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = uiState.newDescription,
                    onValueChange = vm::onDescriptionChange,
                    label         = { Text("Descripción (opcional)") },
                    minLines      = 3,
                    colors        = fieldColors,
                    modifier      = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                val selectedGatewayName = uiState.gateways.firstOrNull { it.id == uiState.newGatewayId }?.name ?: "Ninguno"
                ExposedDropdownMenuBox(expanded = gatewayExpanded, onExpandedChange = { gatewayExpanded = it }) {
                    OutlinedTextField(
                        value         = selectedGatewayName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Gateway (opcional)") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gatewayExpanded) },
                        colors        = fieldColors,
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded         = gatewayExpanded,
                        onDismissRequest = { gatewayExpanded = false },
                        containerColor   = CardBg,
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Ninguno", color = White) },
                            onClick = { vm.onGatewaySelected(null); gatewayExpanded = false },
                        )
                        uiState.gateways.forEach { gw ->
                            DropdownMenuItem(
                                text    = { Text(gw.name, color = White) },
                                onClick = { vm.onGatewaySelected(gw.id); gatewayExpanded = false },
                            )
                        }
                    }
                }

                uiState.saveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::createTicket, enabled = !uiState.isSaving) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                else Text("Crear", color = Cyan)
            }
        },
        dismissButton = { TextButton(onClick = vm::closeNewTicketDialog) { Text("Cancelar", color = Muted) } },
    )
}
