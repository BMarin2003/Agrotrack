package com.corall.agrotrack.core.navigation

sealed class Screen(val route: String) {
    data object Login         : Screen("login")
    data object Home          : Screen("home")

    // Monitoreo
    data object Dashboard     : Screen("dashboard")
    data object Gateways      : Screen("gateways")
    data object GatewayDetail : Screen("gateway/{gatewayId}") {
        fun createRoute(id: Int) = "gateway/$id"
    }
    data object SensorDetail  : Screen("sensor/{sensorId}") {
        fun createRoute(id: Int) = "sensor/$id"
    }

    // Alertas y umbrales
    data object Alerts        : Screen("alerts")
    data object Thresholds    : Screen("thresholds/{sensorId}") {
        fun createRoute(id: Int) = "thresholds/$id"
    }

    // Reportes
    data object Reports       : Screen("reports")

    // Técnico instalador
    data object Installer     : Screen("installer")

    // Técnico de soporte
    data object Support       : Screen("support")
    data object Maintenance   : Screen("maintenance/{gatewayId}") {
        fun createRoute(id: Int) = "maintenance/$id"
    }
    data object HelpDesk      : Screen("helpdesk")

    // Técnico calibrador
    data object Calibration   : Screen("calibration/{sensorId}") {
        fun createRoute(id: Int) = "calibration/$id"
    }

    // Configuración general (Técnico+)
    data object Settings      : Screen("settings")

    // Superadmin
    data object Users         : Screen("users")
}
