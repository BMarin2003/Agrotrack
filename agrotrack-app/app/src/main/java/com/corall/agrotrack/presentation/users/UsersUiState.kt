package com.corall.agrotrack.presentation.users

import com.corall.agrotrack.data.remote.dto.UserItemDto

data class UsersUiState(
    val isLoading:    Boolean          = true,
    val users:        List<UserItemDto> = emptyList(),
    val error:        String?          = null,
    val showDialog:   Boolean          = false,
    val editUser:     UserItemDto?     = null,
    val dialogName:   String           = "",
    val dialogEmail:  String           = "",
    val dialogPassword: String         = "",
    val dialogRoleId: Int              = 1,
    val isSaving:     Boolean          = false,
    val dialogError:  String?          = null,
)
