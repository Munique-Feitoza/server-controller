package com.pocketnoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.pocketnoc.ui.navigation.AppNavHost
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.theme.PocketNOCTheme
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import androidx.hilt.navigation.compose.hiltViewModel
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

    val startDestination = if (servers.isEmpty()) AppRoute.Login.route else AppRoute.Dashboard.route

    AppNavHost(
        navController = navController,
        startDestination = startDestination
    )
}
