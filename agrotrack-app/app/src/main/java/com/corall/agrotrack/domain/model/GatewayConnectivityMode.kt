package com.corall.agrotrack.domain.model

enum class GatewayConnectivityMode {
    Wifi,
    Sim,
    Unknown;

    companion object {
        fun from(value: String?): GatewayConnectivityMode = when (value?.lowercase()) {
            "wifi" -> Wifi
            "sim"  -> Sim
            else   -> Unknown
        }
    }
}
