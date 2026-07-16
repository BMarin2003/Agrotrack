package com.corall.agrotrack.presentation.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.data.remote.api.HelpDeskApiService
import com.corall.agrotrack.data.remote.dto.TicketCreateDto
import com.corall.agrotrack.data.remote.dto.TicketStatusUpdateDto
import com.corall.agrotrack.domain.repository.GatewayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HelpDeskViewModel @Inject constructor(
    private val api:              HelpDeskApiService,
    private val gatewayRepository: GatewayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpDeskUiState())
    val uiState: StateFlow<HelpDeskUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listTickets() }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        _uiState.update { it.copy(isLoading = false, tickets = resp.body().orEmpty()) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Error ${resp.code()}") }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }

            gatewayRepository.getGateways().onSuccess { gws -> _uiState.update { it.copy(gateways = gws) } }
        }
    }

    fun openNewTicketDialog() = _uiState.update {
        it.copy(showNewTicketDialog = true, newSubject = "", newDescription = "", newGatewayId = null, saveError = null)
    }
    fun closeNewTicketDialog()         = _uiState.update { it.copy(showNewTicketDialog = false) }
    fun onSubjectChange(v: String)     = _uiState.update { it.copy(newSubject = v, saveError = null) }
    fun onDescriptionChange(v: String) = _uiState.update { it.copy(newDescription = v) }
    fun onGatewaySelected(id: Int?)    = _uiState.update { it.copy(newGatewayId = id) }

    fun createTicket() {
        val s = _uiState.value
        if (s.newSubject.isBlank()) {
            _uiState.update { it.copy(saveError = "El asunto no puede estar vacío") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            runCatching {
                api.createTicket(TicketCreateDto(s.newGatewayId, s.newSubject.trim(), s.newDescription.ifBlank { null }))
            }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        _uiState.update { it.copy(isSaving = false, showNewTicketDialog = false) }
                        load()
                    } else {
                        _uiState.update { it.copy(isSaving = false, saveError = "Error ${resp.code()}") }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, saveError = e.message) } }
        }
    }

    fun updateStatus(ticketId: Long, newStatus: String) {
        viewModelScope.launch {
            runCatching { api.updateTicketStatus(ticketId, TicketStatusUpdateDto(newStatus)) }
                .onSuccess { resp ->
                    if (resp.isSuccessful) load()
                    else _uiState.update { it.copy(error = "Error ${resp.code()}") }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
