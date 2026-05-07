package com.corall.agrotrack.core.navigation

import androidx.compose.runtime.Composable
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
import com.corall.agrotrack.presentation.dashboard.DashboardScreen
import com.corall.agrotrack.presentation.settings.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    sessionManager: SessionManager = androidx.hilt.navigation.compose.hiltViewModel<com.corall.agrotrack.presentation.auth.LoginViewModel>()
        .run { SessionManager.provideForNav() }
) {
    val startDestination = if (SessionManager.isSessionActive()) Screen.Dashboard.route
                           else Screen.Login.route

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToAlerts   = { navController.navigate(Screen.Alerts.route) },
                onNavigateToReports  = { id -> navController.navigate(Screen.Reports.createRoute(id)) },
                onNavigateToSettings = {
                    if (SessionManager.currentRole() == UserRole.TECHNICIAN) {
                        navController.navigate(Screen.Settings.route)
                    }
                },
                onLogout = {
                    SessionManager.clearSession()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Alerts.route) {
            AlertsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route     = Screen.Reports.route,
            arguments = listOf(navArgument("sensorId") { type = NavType.IntType })
        ) { backStackEntry ->
            val sensorId = backStackEntry.arguments?.getInt("sensorId") ?: return@composable
            // ReportsScreen(sensorId = sensorId, onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
