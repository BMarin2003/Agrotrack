package com.corall.agrotrack.domain.usecase.auth

import com.corall.agrotrack.core.network.WebSocketManager
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val wsManager: WebSocketManager,
) {
    suspend operator fun invoke() {
        wsManager.disconnect()
        authRepository.logout()
        sessionManager.clearSession()
    }
}
