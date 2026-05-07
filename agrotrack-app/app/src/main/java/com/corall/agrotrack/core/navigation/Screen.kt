package com.corall.agrotrack.core.navigation

sealed class Screen(val route: String) {
    data object Login     : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Alerts    : Screen("alerts")
    data object Reports   : Screen("reports/{sensorId}") {
        fun createRoute(sensorId: Int) = "reports/$sensorId"
    }
    data object Settings  : Screen("settings")  // Solo TECHNICIAN
}
