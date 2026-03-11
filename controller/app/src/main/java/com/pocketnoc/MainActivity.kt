package com.pocketnoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.pocketnoc.ui.theme.PocketNOCTheme
import com.pocketnoc.ui.screens.DashboardScreen
import com.pocketnoc.data.api.RetrofitClient
import com.pocketnoc.data.repository.ServerRepository
import com.pocketnoc.config.PocketNOCConfig

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
    // Carrega configuração segura (do BuildConfig / local.properties)
    val serverUrl = PocketNOCConfig.serverUrl
    val jwtToken = PocketNOCConfig.jwtToken
    
    // Criar repositório com API service
    val apiService = RetrofitClient.getInstance(serverUrl)
        .create(com.pocketnoc.data.api.AgentApiService::class.java)
    val repository = ServerRepository(apiService)

    // Criar ViewModel
    val viewModel = com.pocketnoc.ui.viewmodels.DashboardViewModel(repository)

    DashboardScreen(
        viewModel = viewModel,
        serverUrl = serverUrl,
        token = jwtToken,
        onNavigateToServerDetails = { /* TODO */ }
    )
}
