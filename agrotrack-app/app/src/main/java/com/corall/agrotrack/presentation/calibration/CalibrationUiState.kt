package com.corall.agrotrack.presentation.calibration

/** El backend publica el comando por MQTT pero no hay firmware real que lo
 *  confirme todavía — WAITING siempre termina en TIMEOUT hasta que el
 *  equipo de firmware implemente el ack (ver migración 009). */
enum class CalibrationAckState { NONE, WAITING, CONFIRMED, TIMEOUT }

data class CalibrationUiState(
    val isLoading:   Boolean             = true,
    val sensorId:    Int                 = 0,
    val gain:        String              = "1.0",
    val intercept:   String              = "0.0",
    val notes:       String              = "",
    val lastApplied: String?             = null,
    val isSaving:    Boolean             = false,
    val ackState:    CalibrationAckState = CalibrationAckState.NONE,
    val error:       String?             = null,
)
