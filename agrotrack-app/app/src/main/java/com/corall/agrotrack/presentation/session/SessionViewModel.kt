package com.corall.agrotrack.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    enum class VerifyResult { SKIPPED, VALID, INVALID }

    private val _result = MutableStateFlow<VerifyResult?>(null)
    val result: StateFlow<VerifyResult?> = _result.asStateFlow()

    fun verifySession() {
        if (_result.value != null) return // ya se verificó en esta sesión de proceso

        if (!sessionManager.isSessionActive()) {
            _result.value = VerifyResult.SKIPPED
            return
        }

        viewModelScope.launch {
            authRepository.verifyToken()
                .onSuccess { user ->
                    sessionManager.saveSession(user.token, user.id, user.names, user.role)
                    _result.value = VerifyResult.VALID
                }
                .onFailure {
                    sessionManager.clearSession()
                    _result.value = VerifyResult.INVALID
                }
        }
    }
}
