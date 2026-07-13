package com.corall.agrotrack.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.data.remote.api.UsersApiService
import com.corall.agrotrack.data.remote.dto.UserCreateDto
import com.corall.agrotrack.data.remote.dto.UserItemDto
import com.corall.agrotrack.data.remote.dto.UserUpdateDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val api: UsersApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listUsers() }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        _uiState.update { it.copy(isLoading = false, users = resp.body().orEmpty()) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Error al cargar usuarios") }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun openCreate() {
        _uiState.update { it.copy(showDialog = true, editUser = null, dialogName = "", dialogEmail = "", dialogPassword = "", dialogRoleId = 1, dialogError = null) }
    }

    fun openEdit(user: UserItemDto) {
        _uiState.update { it.copy(showDialog = true, editUser = user, dialogName = user.names, dialogEmail = user.email, dialogPassword = "", dialogRoleId = user.roleId ?: 1, dialogError = null) }
    }

    fun dismiss() = _uiState.update { it.copy(showDialog = false, editUser = null, dialogError = null) }

    fun onNameChange(v: String)     = _uiState.update { it.copy(dialogName = v) }
    fun onEmailChange(v: String)    = _uiState.update { it.copy(dialogEmail = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(dialogPassword = v) }
    fun onRoleChange(v: Int)        = _uiState.update { it.copy(dialogRoleId = v) }

    fun save() {
        val state = _uiState.value
        if (state.dialogName.isBlank())  { _uiState.update { it.copy(dialogError = "El nombre es requerido") }; return }
        if (state.dialogEmail.isBlank()) { _uiState.update { it.copy(dialogError = "El correo es requerido") }; return }
        if (state.editUser == null && state.dialogPassword.length < 8) {
            _uiState.update { it.copy(dialogError = "La contraseña debe tener al menos 8 caracteres") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, dialogError = null) }
            val resp = runCatching {
                if (state.editUser == null) {
                    api.createUser(UserCreateDto(state.dialogName.trim(), state.dialogEmail.trim(), state.dialogPassword, state.dialogRoleId))
                } else {
                    api.updateUser(state.editUser.id, UserUpdateDto(state.dialogName.trim(), state.dialogEmail.trim(), state.dialogRoleId))
                }
            }
            resp.onSuccess { r ->
                if (r.isSuccessful) { _uiState.update { it.copy(isSaving = false, showDialog = false) }; load() }
                else _uiState.update { it.copy(isSaving = false, dialogError = "No se pudo guardar") }
            }.onFailure { e -> _uiState.update { it.copy(isSaving = false, dialogError = e.message) } }
        }
    }

    fun toggleEnable(user: UserItemDto) {
        _uiState.update { it.copy(processingUserIds = it.processingUserIds + user.id) }
        viewModelScope.launch {
            val resp = runCatching { api.updateUser(user.id, UserUpdateDto(enable = !(user.enable ?: true))) }
            resp.onSuccess { r ->
                _uiState.update { it.copy(processingUserIds = it.processingUserIds - user.id) }
                if (r.isSuccessful) load()
                else _uiState.update { it.copy(actionError = "No se pudo actualizar el estado del usuario") }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        processingUserIds = it.processingUserIds - user.id,
                        actionError       = e.message ?: "No se pudo actualizar el estado del usuario",
                    )
                }
            }
        }
    }

    fun requestDelete(user: UserItemDto) = _uiState.update { it.copy(confirmDeleteUser = user) }

    fun cancelDelete() = _uiState.update { it.copy(confirmDeleteUser = null) }

    fun confirmDelete() {
        val user = _uiState.value.confirmDeleteUser ?: return
        _uiState.update { it.copy(confirmDeleteUser = null, processingUserIds = it.processingUserIds + user.id) }
        viewModelScope.launch {
            val resp = runCatching { api.deleteUser(user.id) }
            resp.onSuccess { r ->
                _uiState.update { it.copy(processingUserIds = it.processingUserIds - user.id) }
                if (r.isSuccessful) load()
                else _uiState.update { it.copy(actionError = "No se pudo eliminar el usuario") }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        processingUserIds = it.processingUserIds - user.id,
                        actionError       = e.message ?: "No se pudo eliminar el usuario",
                    )
                }
            }
        }
    }

    fun dismissActionError() = _uiState.update { it.copy(actionError = null) }
}
