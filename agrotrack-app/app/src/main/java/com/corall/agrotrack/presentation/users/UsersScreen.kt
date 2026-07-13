package com.corall.agrotrack.presentation.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.data.remote.dto.UserItemDto
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.EmptyState
import com.corall.agrotrack.presentation.common.components.ErrorState
import com.corall.agrotrack.presentation.common.components.LoadingState
import com.corall.agrotrack.presentation.common.components.StatusPill
import com.corall.agrotrack.presentation.common.theme.AccentCyan
import com.corall.agrotrack.presentation.common.theme.AccentGreen
import com.corall.agrotrack.presentation.common.theme.AccentRed
import com.corall.agrotrack.presentation.common.theme.GradientTop
import com.corall.agrotrack.presentation.common.theme.InfoBlue
import com.corall.agrotrack.presentation.common.theme.SurfaceCard
import com.corall.agrotrack.presentation.common.theme.TextMutedOnDark
import com.corall.agrotrack.presentation.common.theme.TextOnDark

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
                    color      = TextOnDark,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = "Administra los usuarios del sistema.",
                    color    = TextMutedOnDark,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                uiState.actionError?.let { err ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, color = AccentRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::dismissActionError, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMutedOnDark, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> ErrorState(message = uiState.error ?: "Error", onRetry = viewModel::load)
                    uiState.users.isEmpty() -> EmptyState("No hay usuarios registrados")
                    else -> {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.users, key = { it.id }) { user ->
                                UserCard(
                                    user            = user,
                                    processing      = user.id in uiState.processingUserIds,
                                    onEdit          = { viewModel.openEdit(user) },
                                    onToggle        = { viewModel.toggleEnable(user) },
                                    onRequestDelete = { viewModel.requestDelete(user) },
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick        = viewModel::openCreate,
                modifier       = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = AccentCyan,
                contentColor   = GradientTop,
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

    uiState.confirmDeleteUser?.let { user ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor   = GradientTop,
            title = { Text("Eliminar usuario", color = TextOnDark, fontWeight = FontWeight.Bold) },
            text  = { Text("Se eliminará a ${user.names} permanentemente. ¿Continuar?", color = TextMutedOnDark) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Eliminar", color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancelar", color = TextMutedOnDark) }
            },
        )
    }
}

@Composable
private fun UserCard(
    user:            UserItemDto,
    processing:      Boolean,
    onEdit:          () -> Unit,
    onToggle:        () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val enabled  = user.enable ?: true
    val roleName = ROLES.firstOrNull { it.first == user.roleId }?.second ?: user.roleName ?: "—"

    Surface(color = SurfaceCard, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.names, color = TextOnDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(user.email, color = TextMutedOnDark, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleChip(roleName)
                    StatusChip(enabled = enabled, onToggle = onToggle, disabled = processing)
                }
            }

            if (processing) {
                CircularProgressIndicator(Modifier.size(20.dp), color = AccentCyan, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = AccentCyan, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AccentRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleChip(label: String) {
    StatusPill(text = label, color = InfoBlue)
}

@Composable
private fun StatusChip(enabled: Boolean, onToggle: () -> Unit, disabled: Boolean) {
    StatusPill(
        text    = if (enabled) "Activo" else "Inactivo",
        color   = if (enabled) AccentGreen else AccentRed,
        onClick = onToggle,
        enabled = !disabled,
    )
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
        containerColor   = GradientTop,
        title = {
            Text(
                if (isEdit) "Editar Usuario" else "Nuevo Usuario",
                color = TextOnDark, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentCyan,
                    unfocusedBorderColor = TextMutedOnDark.copy(alpha = 0.4f),
                    focusedTextColor     = TextOnDark,
                    unfocusedTextColor   = TextOnDark,
                    focusedLabelColor    = AccentCyan,
                    unfocusedLabelColor  = TextMutedOnDark,
                    cursorColor          = AccentCyan,
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
                        containerColor   = SurfaceCard,
                    ) {
                        ROLES.forEach { (id, name, _) ->
                            DropdownMenuItem(
                                text    = { Text(name, color = TextOnDark) },
                                onClick = { onRole(id); roleExpanded = false },
                            )
                        }
                    }
                }
                uiState.dialogError?.let { err ->
                    Text(err, color = AccentRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !uiState.isSaving) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                else Text(if (isEdit) "Guardar" else "Crear", color = AccentCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMutedOnDark) }
        },
    )
}
