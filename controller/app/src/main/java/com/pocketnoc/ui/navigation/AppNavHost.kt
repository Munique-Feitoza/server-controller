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
        // Tela de Splash
        composable(AppRoute.Splash.route) {
            SplashScreen(
                navController = navController,
                servers = servers
            )
        }

        // Tela de Login
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

        // Tela do Dashboard
        composable(AppRoute.Dashboard.route) {
            DashboardScreen(
                navController = navController,
                viewModel = dashboardViewModel,
                onNavigateToAddServer = {
                    navController.navigate(AppRoute.Login.route)
                }
            )
        }

        // Tela de Lista de Servidores
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

        // Tela de Detalhes do Servidor
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

        // Tela do Centro de Acoes
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

        // Tela de Configuracao de Alertas
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

        // Tela do Explorador de Processos
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

        // Tela do Visualizador de Logs
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

        // Tela de Historico de Alertas
        composable(AppRoute.AlertHistory.route) {
            AlertHistoryScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Tela do Watchdog (rota independente)
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

        // Tela de Exportacao
        composable(AppRoute.Export.route) {
            ExportScreen(
                viewModel = dashboardViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Tela de Log de Auditoria
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

        // Tela de Configuracao do Agente
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

        // Seguranca do Dashboard ERP
        composable(AppRoute.SecurityDashboard.route) {
            val securityViewModel: com.pocketnoc.ui.viewmodels.SecurityViewModel = hiltViewModel()
            val incidents by securityViewModel.incidents.collectAsState()
            val stats by securityViewModel.stats.collectAsState()
            val isLoading by securityViewModel.isLoading.collectAsState()

            LaunchedEffect(Unit) {
                securityViewModel.loadSecurityData(hours = 168)
            }

            SecurityDashboardScreen(
                incidents = incidents,
                stats = stats,
                isLoading = isLoading,
                onRefresh = { securityViewModel.loadSecurityData(hours = 168) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // PHP-FPM Pools por servidor
        composable(
            route = AppRoute.PhpFpm.route,
            arguments = listOf(
                androidx.navigation.navArgument("serverId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { backStackEntry ->
            val serverId: Int = backStackEntry.arguments?.getInt("serverId") ?: 0
            val server = servers.find { it.id == serverId }

            PhpFpmScreen(
                pools = emptyList(),
                totalWorkers = 0,
                totalCpu = 0f,
                totalMemory = 0f,
                serverName = server?.name ?: "Servidor",
                isLoading = false,
                onRefresh = {},
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
