package com.corall.agrotrack.domain.model

import com.corall.agrotrack.core.security.UserRole

data class User(
    val id: Int,
    val names: String,
    val email: String,
    val role: UserRole,
    val token: String,
    val expiresIn: Int,
)
