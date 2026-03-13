package com.pocketnoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketnoc.ui.theme.PocketNOCTheme
import com.pocketnoc.ui.screens.DashboardScreen
import com.pocketnoc.ui.screens.LoginScreen
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketNOCTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: DashboardViewModel = hiltViewModel()
    val servers by viewModel.allServers.collectAsState()

    // Auto-navegação para o dashboard quando os servidores forem sincronizados
    LaunchedEffect(servers) {
        if (servers.isNotEmpty() && navController.currentDestination?.route == "login") {
            navController.navigate("dashboard") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (servers.isEmpty()) "login" else "dashboard"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { name, url, secret ->
                    viewModel.addServer(name, url, secret)
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToAddServer = {
                    navController.navigate("login")
                }
            )
        }
    }
}
