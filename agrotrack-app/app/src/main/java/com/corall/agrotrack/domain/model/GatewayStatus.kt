package com.corall.agrotrack.domain.model

enum class GatewayStatus {
    Online,
    Offline,
    Maintenance;

    companion object {
        fun from(value: String?, enable: Boolean): GatewayStatus {
            if (!enable) return Maintenance

            return when (value?.lowercase()) {
                "online" -> Online
                "maintenance" -> Maintenance
                else -> Offline
            }
        }
    }
}