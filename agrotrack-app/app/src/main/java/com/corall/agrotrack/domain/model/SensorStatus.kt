package com.corall.agrotrack.domain.model

enum class SensorStatus {
    Normal,    // Dentro de umbrales
    Warning,   // Cerca del umbral (±10 % del límite)
    Critical,  // Fuera de umbral
    Offline,   // Sin transmisión > 30 s
}
