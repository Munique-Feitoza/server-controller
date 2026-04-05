package com.pocketnoc.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pocketnoc.ui.screens.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.WatchdogViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = AppRoute.Splash.route
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val servers by dashboardViewModel.allServers.collectAsState()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Splash Screen
        composable(AppRoute.Splash.route) {
            SplashScreen(
                navController = navController,
                servers = servers
            )
        }

        // Login Screen
        composable(AppRoute.Login.route) {
            LoginScreen(
                onLoginSuccess = { name, url, secret ->
                    dashboardViewModel.addServer(name, url, secret)
                    navController.navigate(AppRoute.Dashboard.route) {
                        popUpTo(AppRoute.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Dashboard Screen
        composable(AppRoute.Dashboard.route) {
            DashboardScreen(
                navController = navController,
                viewModel = dashboardViewModel,
                onNavigateToAddServer = {
                    navController.navigate(AppRoute.Login.route)
                }
            )
        }

        // Server List Screen
        composable(AppRoute.ServerList.route) {
            ServerListScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetails = { serverId ->
                    navController.navigate(AppRoute.ServerDetails.createRoute(serverId))
                }
            )
        }

        // Server Details Screen
        composable(
            route = AppRoute.ServerDetails.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0

            ServerDetailsScreen(
                viewModel = dashboardViewModel,
                serverId = serverId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToActionCenter = { id ->
                    navController.navigate(AppRoute.ActionCenter.createRoute(id))
                },
                onNavigateToProcessExplorer = { id ->
                    navController.navigate(AppRoute.ProcessExplorer.createRoute(id))
                },
                onNavigateToLogs = { id, service ->
                    navController.navigate(AppRoute.LogViewer.createRoute(id, service))
                },
                onNavigateToWatchdog = { id ->
                    navController.navigate(AppRoute.Watchdog.createRoute(id))
                },
                onNavigateToAuditLog = { id ->
                    navController.navigate(AppRoute.AuditLog.createRoute(id))
                },
                onNavigateToAgentConfig = { id ->
                    navController.navigate(AppRoute.AgentConfig.createRoute(id))
                }
            )
        }

        // Action Center Screen
        composable(
            route = AppRoute.ActionCenter.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0

            ActionCenterScreen(
                viewModel = dashboardViewModel,
                serverId = serverId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Alert Settings Screen
        composable(AppRoute.AlertSettings.route) {
            val currentConfig by dashboardViewModel.alertThresholds.collectAsState()

            AlertSettingsScreen(
                currentConfig = currentConfig,
                onSaveSettings = { newConfig ->
                    dashboardViewModel.updateAlertSettings(newConfig)
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Process Explorer Screen
        composable(
            route = AppRoute.ProcessExplorer.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0

            ProcessExplorerScreen(
                viewModel = dashboardViewModel,
                serverId = serverId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Log Viewer Screen
        composable(
            route = AppRoute.LogViewer.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                },
                androidx.navigation.navArgument("service") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = "pocket-noc-agent"
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0
            val serviceName: String = backStackEntry.arguments?.getString("service") ?: "pocket-noc-agent"

            LogViewerScreen(
                viewModel = dashboardViewModel,
                serverId = serverId,
                serviceName = serviceName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Alert History Screen
        composable(AppRoute.AlertHistory.route) {
            AlertHistoryScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Watchdog Screen (standalone route)
        composable(
            route = AppRoute.Watchdog.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0
            val server = servers.find { it.id == serverId }
            val watchdogViewModel: WatchdogViewModel = hiltViewModel()

            server?.let {
                WatchdogScreen(
                    server = it,
                    viewModel = watchdogViewModel
                )
            }
        }

        // Export Screen
        composable(AppRoute.Export.route) {
            ExportScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Audit Log Screen
        composable(
            route = AppRoute.AuditLog.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0

            AuditLogScreen(
                viewModel = dashboardViewModel,
                serverId = serverId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Agent Config Screen
        composable(
            route = AppRoute.AgentConfig.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0

            AgentConfigScreen(
                viewModel = dashboardViewModel,
                serverId = serverId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
