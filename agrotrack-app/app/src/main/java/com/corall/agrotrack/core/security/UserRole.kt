package com.corall.agrotrack.core.security

enum class UserRole {
    OPERATOR,
    TECHNICIAN,
    ADMIN;

    companion object {
        fun from(value: String?): UserRole {
            return when (value?.trim()?.uppercase()) {
                "ADMIN", "ADMINISTRADOR" -> ADMIN
                "TECHNICIAN", "TECNICO", "TÉCNICO", "SOPORTE", "SUPPORT" -> TECHNICIAN
                else -> OPERATOR
            }
        }
    }
}