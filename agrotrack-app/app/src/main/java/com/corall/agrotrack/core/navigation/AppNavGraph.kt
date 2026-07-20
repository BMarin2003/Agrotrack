package com.corall.agrotrack.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.corall.agrotrack.core.security.SessionManager
import com.corall.agrotrack.core.security.UserRole
import com.corall.agrotrack.presentation.alerts.AlertsScreen
import com.corall.agrotrack.presentation.auth.LoginScreen
import com.corall.agrotrack.presentation.calibration.CalibrationScreen
import com.corall.agrotrack.presentation.dashboard.DashboardScreen
import com.corall.agrotrack.presentation.gateways.GatewayDetailScreen
import com.corall.agrotrack.presentation.gateways.GatewaysScreen
import com.corall.agrotrack.presentation.home.HomeScreen
import com.corall.agrotrack.presentation.installer.InstallerScreen
import com.corall.agrotrack.presentation.reports.ReportsScreen
import com.corall.agrotrack.presentation.sensors.SensorDetailScreen
import com.corall.agrotrack.presentation.session.SessionViewModel
import com.corall.agrotrack.presentation.settings.SettingsScreen
import com.corall.agrotrack.presentation.support.HelpDeskScreen
import com.corall.agrotrack.presentation.support.MaintenanceScreen
import com.corall.agrotrack.presentation.support.SupportScreen
import com.corall.agrotrack.presentation.thresholds.ThresholdsScreen
import com.corall.agrotrack.presentation.users.UsersScreen

@Composable
fun AppNavGraph(
    navController:    NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel   = hiltViewModel(),
) {
    val startDestination = if (SessionManager.isSessionActive()) Screen.Home.route
                           else Screen.Login.route

    val sessionResult by sessionViewModel.result.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        sessionViewModel.verifySession()
    }

    LaunchedEffect(sessionResult) {
        if (sessionResult == SessionViewModel.VerifyResult.INVALID) {
            sessionViewModel.disconnectLiveUpdates()
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    sessionViewModel.connectLiveUpdates()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Home (hub de módulos por rol) ─────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDashboard   = { navController.navigate(Screen.Dashboard.route) },
                onNavigateToGateways    = { navController.navigate(Screen.Gateways.route) },
                onNavigateToAlerts      = { navController.navigate(Screen.Alerts.route) },
                onNavigateToReports     = { navController.navigate(Screen.Reports.route) },
                onNavigateToInstaller   = {
                    if (SessionManager.currentRole() != UserRole.OPERATOR)
                        navController.navigate(Screen.Installer.route)
                },
                onNavigateToSupport     = {
                    if (SessionManager.currentRole() != UserRole.OPERATOR)
                        navController.navigate(Screen.Support.route)
                },
                onNavigateToCalibration = {
                    if (SessionManager.currentRole() != UserRole.OPERATOR)
                        navController.navigate(Screen.Calibration.createRoute(0))
                },
                onNavigateToUsers       = {
                    if (SessionManager.currentRole() == UserRole.ADMIN)
                        navController.navigate(Screen.Users.route)
                },
                // LogoutUseCase ya limpió la sesión; aquí solo navegamos
                onLogout = {
                    sessionViewModel.disconnectLiveUpdates()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Monitoreo ─────────────────────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onBack                = { navController.popBackStack() },
                onNavigateToAlerts    = { navController.navigate(Screen.Alerts.route) },
                onNavigateToReports   = { navController.navigate(Screen.Reports.route) },
                onNavigateToSettings  = {
                    val role = SessionManager.currentRole()
                    if (role == UserRole.TECHNICIAN || role == UserRole.ADMIN)
                        navController.navigate(Screen.Settings.route)
                },
                onNavigateToUsers     = {
                    if (SessionManager.currentRole() == UserRole.ADMIN)
                        navController.navigate(Screen.Users.route)
                },
                onLogout = {
                    SessionManager.clearSession()
                    sessionViewModel.disconnectLiveUpdates()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Gateways ──────────────────────────────────────────────────────────
        composable(Screen.Gateways.route) {
            GatewaysScreen(
                onBack         = { navController.popBackStack() },
                onGatewayClick = { id -> navController.navigate(Screen.GatewayDetail.createRoute(id)) },
            )
        }

        composable(
            route     = Screen.GatewayDetail.route,
            arguments = listOf(navArgument("gatewayId") { type = NavType.IntType }),
        ) { back ->
            val gatewayId = back.arguments?.getInt("gatewayId") ?: return@composable
            GatewayDetailScreen(
                gatewayId     = gatewayId,
                onBack        = { navController.popBackStack() },
                onSensorClick = { id -> navController.navigate(Screen.SensorDetail.createRoute(id)) },
            )
        }

        composable(
            route     = Screen.SensorDetail.route,
            arguments = listOf(navArgument("sensorId") { type = NavType.IntType }),
        ) { back ->
            val sensorId = back.arguments?.getInt("sensorId") ?: return@composable
            SensorDetailScreen(
                sensorId           = sensorId,
                onBack             = { navController.popBackStack() },
                onConfigThresholds = { id -> navController.navigate(Screen.Thresholds.createRoute(id)) },
                onCalibrate        = { id ->
                    if (SessionManager.currentRole() != UserRole.OPERATOR)
                        navController.navigate(Screen.Calibration.createRoute(id))
                },
            )
        }

        // ── Alertas y umbrales ────────────────────────────────────────────────
        composable(Screen.Alerts.route) {
            AlertsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route     = Screen.Thresholds.route,
            arguments = listOf(navArgument("sensorId") { type = NavType.IntType }),
        ) { back ->
            val sensorId = back.arguments?.getInt("sensorId") ?: return@composable
            ThresholdsScreen(
                sensorId = sensorId,
                onBack   = { navController.popBackStack() },
            )
        }

        // ── Reportes ──────────────────────────────────────────────────────────
        composable(Screen.Reports.route) {
            ReportsScreen(onBack = { navController.popBackStack() })
        }

        // ── Técnico instalador ────────────────────────────────────────────────
        composable(Screen.Installer.route) {
            InstallerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        // ── Técnico de soporte ────────────────────────────────────────────────
        composable(Screen.Support.route) {
            SupportScreen(
                onBack               = { navController.popBackStack() },
                onGatewayClick       = { id -> navController.navigate(Screen.Maintenance.createRoute(id)) },
                onNavigateToHelpDesk = { navController.navigate(Screen.HelpDesk.route) },
            )
        }

        composable(
            route     = Screen.Maintenance.route,
            arguments = listOf(navArgument("gatewayId") { type = NavType.IntType }),
        ) { back ->
            val gatewayId = back.arguments?.getInt("gatewayId") ?: return@composable
            MaintenanceScreen(
                gatewayId = gatewayId,
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Screen.HelpDesk.route) {
            HelpDeskScreen(onBack = { navController.popBackStack() })
        }

        // ── Calibración ───────────────────────────────────────────────────────
        composable(
            route     = Screen.Calibration.route,
            arguments = listOf(navArgument("sensorId") { type = NavType.IntType }),
        ) { back ->
            val sensorId = back.arguments?.getInt("sensorId") ?: return@composable
            CalibrationScreen(
                sensorId = sensorId,
                onBack   = { navController.popBackStack() },
            )
        }

        // ── Superadmin ────────────────────────────────────────────────────────
        composable(Screen.Users.route) {
            UsersScreen(onBack = { navController.popBackStack() })
        }
    }
}
