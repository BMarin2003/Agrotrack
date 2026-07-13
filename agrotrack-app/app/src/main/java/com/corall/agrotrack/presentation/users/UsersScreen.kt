package com.corall.agrotrack.presentation.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.data.remote.dto.UserItemDto
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)

private val ROLES = listOf(
    Triple(1, "Administrador", "ADMIN"),
    Triple(2, "Operador",      "OPERATOR"),
    Triple(3, "Técnico",       "TECHNICIAN"),
    Triple(4, "Auditor",       "AUDITOR"),
)

@Composable
fun UsersScreen(
    onBack:    () -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AgroGradient {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                AgroHeader(onBack = onBack)

                Text(
                    text       = "Gestión de Usuarios",
                    color      = White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = "Administra los usuarios del sistema.",
                    color    = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(uiState.error ?: "Error", color = Red)
                        }
                    }
                    uiState.users.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay usuarios registrados", color = Muted)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.users, key = { it.id }) { user ->
                                UserCard(
                                    user     = user,
                                    onEdit   = { viewModel.openEdit(user) },
                                    onToggle = { viewModel.toggleEnable(user) },
                                    onDelete = { viewModel.requestDelete(user) },
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick        = viewModel::openCreate,
                modifier       = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = Cyan,
                contentColor   = Color(0xFF0D1B2A),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar usuario")
            }
        }
    }

    if (uiState.showDialog) {
        UserDialog(
            uiState    = uiState,
            onDismiss  = viewModel::dismiss,
            onSave     = viewModel::save,
            onName     = viewModel::onNameChange,
            onEmail    = viewModel::onEmailChange,
            onPassword = viewModel::onPasswordChange,
            onRole     = viewModel::onRoleChange,
        )
    }
}

@Composable
private fun UserCard(
    user:     UserItemDto,
    onEdit:   () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled  = user.enable ?: true
    val roleName = ROLES.firstOrNull { it.first == user.roleId }?.second ?: user.roleName ?: "—"

    Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.names, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(user.email, color = Muted,  fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleChip(roleName)
                    StatusChip(enabled)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit,   contentDescription = "Editar",   tint = Cyan, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RoleChip(label: String) {
    Surface(color = Color(0xFF1E3A5F), shape = MaterialTheme.shapes.extraSmall) {
        Text(label, color = Color(0xFF93C5FD), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun StatusChip(enabled: Boolean) {
    Surface(
        color = if (enabled) Color(0xFF14532D) else Color(0xFF450A0A),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = if (enabled) "Activo" else "Inactivo",
            color    = if (enabled) Green else Red,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDialog(
    uiState:    UsersUiState,
    onDismiss:  () -> Unit,
    onSave:     () -> Unit,
    onName:     (String) -> Unit,
    onEmail:    (String) -> Unit,
    onPassword: (String) -> Unit,
    onRole:     (Int)    -> Unit,
) {
    val isEdit = uiState.editUser != null
    var roleExpanded by remember { mutableStateOf(false) }
    val selectedRoleName = ROLES.firstOrNull { it.first == uiState.dialogRoleId }?.second ?: "Operador"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF0D1B2A),
        title = {
            Text(
                if (isEdit) "Editar Usuario" else "Nuevo Usuario",
                color = White, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    value         = uiState.dialogName,
                    onValueChange = onName,
                    label         = { Text("Nombre completo") },
                    singleLine    = true,
                    colors        = fieldColors,
                    modifier      = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = uiState.dialogEmail,
                    onValueChange = onEmail,
                    label         = { Text("Correo electrónico") },
                    singleLine    = true,
                    colors        = fieldColors,
                    modifier      = Modifier.fillMaxWidth(),
                )
                if (!isEdit) {
                    OutlinedTextField(
                        value                = uiState.dialogPassword,
                        onValueChange        = onPassword,
                        label                = { Text("Contraseña") },
                        singleLine           = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors               = fieldColors,
                        modifier             = Modifier.fillMaxWidth(),
                    )
                }
                ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = it }) {
                    OutlinedTextField(
                        value         = selectedRoleName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Rol") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        colors        = fieldColors,
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded         = roleExpanded,
                        onDismissRequest = { roleExpanded = false },
                        containerColor   = Color(0xFF132238),
                    ) {
                        ROLES.forEach { (id, name, _) ->
                            DropdownMenuItem(
                                text    = { Text(name, color = White) },
                                onClick = { onRole(id); roleExpanded = false },
                            )
                        }
                    }
                }
                uiState.dialogError?.let { err ->
                    Text(err, color = Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !uiState.isSaving) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                else Text(if (isEdit) "Guardar" else "Crear", color = Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) }
        },
    )
}
