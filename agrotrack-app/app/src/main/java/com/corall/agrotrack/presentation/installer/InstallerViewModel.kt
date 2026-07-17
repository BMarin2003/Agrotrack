package com.corall.agrotrack.presentation.installer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corall.agrotrack.domain.repository.GatewayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallerViewModel @Inject constructor(
    private val gatewayRepo: GatewayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            gatewayRepo.getGateways()
                .onSuccess { gateways ->
                    _uiState.update { it.copy(
                        isLoading         = false,
                        gateways          = gateways,
                        selectedGatewayId = gateways.firstOrNull()?.id,
                    ) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun selectGateway(id: Int) = _uiState.update { it.copy(selectedGatewayId = id, success = false, error = null) }
    fun onSsidChange(v: String)     = _uiState.update { it.copy(ssid     = v, success  = false, error    = null) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }

    fun submitWifi() {
        val s = _uiState.value
        val gatewayId = s.selectedGatewayId ?: run { _uiState.update { it.copy(error = "Selecciona un gateway") }; return }
        if (s.ssid.isBlank()) { _uiState.update { it.copy(error = "El SSID no puede estar vacío") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, success = false, confirmed = false) }
            val beforeReadingAt = gatewayRepo.getGatewayById(gatewayId).getOrNull()?.lastReadingAt
            gatewayRepo.updateGatewayWifi(gatewayId, s.ssid.trim(), s.password.ifBlank { null }, "WPA2")
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, success = true, verifying = true) }
                    verifyConnection(gatewayId, beforeReadingAt)
                }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    /**
     * No hay handshake real con el gateway físico tras guardar el WiFi — la única señal
     * disponible es si vuelve a llegar telemetría. Pollea unas cuantas veces con timeout
     * en vez de asumir éxito solo porque el guardado en BD funcionó.
     */
    private fun verifyConnection(gatewayId: Int, beforeReadingAt: Long?) {
        viewModelScope.launch {
            repeat(3) {
                delay(5_000)
                val gateway = gatewayRepo.getGatewayById(gatewayId).getOrNull()
                val reconnected = gateway?.lastReadingAt != null && gateway.lastReadingAt != beforeReadingAt
                if (reconnected) {
                    _uiState.update { it.copy(verifying = false, confirmed = true) }
                    return@launch
                }
            }
            _uiState.update { it.copy(verifying = false, confirmed = false) }
        }
    }

    fun onPinChange(v: String)            = _uiState.update { it.copy(pin = v, pinError = null, pinSuccess = false) }
    fun onPinAllGatewaysToggle(v: Boolean) = _uiState.update { it.copy(pinAllGateways = v, pinError = null) }

    fun submitPin() {
        val s   = _uiState.value
        val pin = s.pin.trim()
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _uiState.update { it.copy(pinError = "El PIN debe ser exactamente 4 dígitos numéricos") }
            return
        }
        val ids = if (s.pinAllGateways) s.gateways.map { it.id }
                  else listOfNotNull(s.selectedGatewayId)
        if (ids.isEmpty()) { _uiState.update { it.copy(pinError = "Selecciona un gateway") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isPinSaving = true, pinError = null, pinSuccess = false) }
            gatewayRepo.updateGatewayPin(ids, pin)
                .onSuccess { _uiState.update { it.copy(isPinSaving = false, pinSuccess = true) } }
                .onFailure { e -> _uiState.update { it.copy(isPinSaving = false, pinError = e.message) } }
        }
    }

    fun onMqttTopicChange(v: String) = _uiState.update { it.copy(mqttTopic = v, mqttState = MqttTopicState.NONE, mqttError = null) }

    fun submitMqttTopic() {
        val s = _uiState.value
        val gatewayId = s.selectedGatewayId ?: run { _uiState.update { it.copy(mqttError = "Selecciona un gateway") }; return }
        if (s.mqttTopic.isBlank()) { _uiState.update { it.copy(mqttError = "El tópico no puede estar vacío") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isMqttSaving = true, mqttError = null, mqttState = MqttTopicState.NONE) }
            gatewayRepo.updateGatewayMqttTopic(gatewayId, s.mqttTopic.trim())
                .onSuccess {
                    _uiState.update { it.copy(isMqttSaving = false, mqttState = MqttTopicState.PENDING) }
                    awaitMqttTopicAck(gatewayId)
                }
                .onFailure { e -> _uiState.update { it.copy(isMqttSaving = false, mqttError = e.message) } }
        }
    }

    /**
     * No hay canal real de aprovisionamiento a un gateway físico (ver
     * migración 011) — se pollea el estado igual que la calibración
     * (HU32-35): la flota simulada confirma sola en unos segundos, un
     * gateway real se queda "pendiente" hasta que exista ese mecanismo.
     */
    private fun awaitMqttTopicAck(gatewayId: Int) {
        viewModelScope.launch {
            repeat(6) {
                delay(3_000)
                val gateway = gatewayRepo.getGatewayById(gatewayId).getOrNull()
                when (gateway?.mqttTopicStatus) {
                    "applied" -> {
                        _uiState.update { it.copy(mqttState = MqttTopicState.APPLIED) }
                        return@launch
                    }
                    "error" -> {
                        _uiState.update { it.copy(mqttState = MqttTopicState.ERROR, mqttError = "El gateway rechazó el nuevo tópico") }
                        return@launch
                    }
                    else -> { /* pending, seguir esperando */ }
                }
            }
            _uiState.update { it.copy(
                mqttState = MqttTopicState.ERROR,
                mqttError = "Sin confirmación del gateway — verifica que esté encendido y en rango",
            ) }
        }
    }
}
