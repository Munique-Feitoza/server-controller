package com.pocketnoc.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pocketnoc.ui.screens.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import kotlin.reflect.typeOf

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = AppRoute.Login.route
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val servers by dashboardViewModel.allServers.collectAsState()

    // Auto-navegação para o dashboard quando os servidores forem sincronizados
    LaunchedEffect(servers) {
        if (servers.isNotEmpty() && navController.currentDestination?.route == AppRoute.Login.route) {
            navController.navigate(AppRoute.Dashboard.route) {
                popUpTo(AppRoute.Login.route) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
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
                },
                onNavigateToServerList = {
                    navController.navigate(AppRoute.ServerList.route)
                },
                onNavigateToServerDetails = { serverId ->
                    navController.navigate(AppRoute.ServerDetails.createRoute(serverId))
                },
                onNavigateToActionCenter = { serverId ->
                    navController.navigate(AppRoute.ActionCenter.createRoute(serverId))
                },
                onNavigateToAlertSettings = {
                    navController.navigate(AppRoute.AlertSettings.route)
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
                onNavigateToActionCenter = { serverId ->
                    navController.navigate(AppRoute.ActionCenter.createRoute(serverId))
                },
                onNavigateToProcessExplorer = { serverId ->
                    navController.navigate(AppRoute.ProcessExplorer.createRoute(serverId))
                },
                onNavigateToLogs = { serverId, service ->
                    navController.navigate(AppRoute.LogViewer.createRoute(serverId, service))
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
    }
}
