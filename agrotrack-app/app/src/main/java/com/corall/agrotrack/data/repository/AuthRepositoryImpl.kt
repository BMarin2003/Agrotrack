package com.corall.agrotrack.data.repository

import com.corall.agrotrack.core.security.UserRole
import com.corall.agrotrack.data.remote.api.AuthApiService
import com.corall.agrotrack.data.remote.dto.LoginRequestDto
import com.corall.agrotrack.domain.model.User
import com.corall.agrotrack.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val response = api.login(LoginRequestDto(email, password))
        val body     = response.body() ?: error(response.errorBody()?.string() ?: "Error de autenticación")
        val role = mapRole(body.user.roles.map { it.name })
        User(
            id       = body.user.id,
            names    = body.user.names,
            email    = body.user.email,
            role     = role,
            token    = body.token,
            expiresIn = body.expiresIn,
        )
    }

    override suspend fun logout() = runCatching { api.logout() }.let { }

    private fun mapRole(roleNames: List<String>): UserRole = when {
        roleNames.any { it == "Administrador" } -> UserRole.ADMIN
        roleNames.any { it == "Técnico" }       -> UserRole.TECHNICIAN
        else                                    -> UserRole.OPERATOR
    }

    override suspend fun verifyToken(): Result<User> = runCatching {
        val response = api.verifyToken()
        val body     = response.body() ?: error("Token inválido")
        val role = mapRole(body.user.roles.map { it.name })
        User(id = body.user.id, names = body.user.names, email = body.user.email,
             role = role, token = body.token, expiresIn = body.expiresIn)
    }
}
