package com.corall.agrotrack.presentation.installer

import com.corall.agrotrack.domain.model.Gateway

/** Sin canal real de aprovisionamiento a un gateway físico todavía (ver
 *  migración 011) — PENDING puede quedarse así para siempre en un gateway
 *  real; solo la flota simulada llega a APPLIED/ERROR. */
enum class MqttTopicState { NONE, PENDING, APPLIED, ERROR }

data class InstallerUiState(
    val isLoading:         Boolean       = true,
    val gateways:          List<Gateway> = emptyList(),
    val selectedGatewayId: Int?          = null,
    // WiFi
    val ssid:              String        = "",
    val password:          String        = "",
    val security:          String        = "WPA2",
    val isSaving:          Boolean       = false,
    val success:           Boolean       = false,
    // Verificación post-guardado: ¿el gateway realmente volvió a reportar datos?
    val verifying:         Boolean       = false,
    val confirmed:         Boolean       = false,
    val error:             String?       = null,
    // PIN
    val pin:               String        = "",
    val pinAllGateways:    Boolean       = false,
    val isPinSaving:       Boolean       = false,
    val pinSuccess:        Boolean       = false,
    val pinError:          String?       = null,
    // Tópico MQTT
    val mqttTopic:         String        = "",
    val isMqttSaving:      Boolean       = false,
    val mqttState:         MqttTopicState = MqttTopicState.NONE,
    val mqttError:         String?       = null,
)
