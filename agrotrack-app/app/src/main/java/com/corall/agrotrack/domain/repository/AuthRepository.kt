package com.corall.agrotrack.domain.repository

import com.corall.agrotrack.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout()
    suspend fun verifyToken(): Result<User>
}
