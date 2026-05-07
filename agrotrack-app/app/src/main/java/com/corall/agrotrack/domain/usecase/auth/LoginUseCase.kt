package com.corall.agrotrack.domain.usecase.auth

import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole
import com.corall.agrotrack.domain.model.User
import com.corall.agrotrack.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val result = authRepository.login(email, password)
        result.onSuccess { user ->
            sessionManager.saveSession(user.token, user.id, user.names, user.role)
        }
        return result
    }
}
