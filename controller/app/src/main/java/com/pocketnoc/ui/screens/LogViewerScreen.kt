package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.LogsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    serviceName: String,
    onNavigateBack: () -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    val selectedServer = servers.find { it.id == serverId }
    val logsState by viewModel.logsState.collectAsState()

    LaunchedEffect(selectedServer) {
        selectedServer?.let { viewModel.fetchLogs(it, serviceName) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("📟 LOG VIEWER", style = MaterialTheme.typography.titleMedium, color = NeonGreen)
                        Text(serviceName, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedServer?.let { viewModel.fetchLogs(it, serviceName) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            when (logsState) {
                is LogsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonGreen)
                    }
                }
                is LogsUiState.Success -> {
                    val logs = (logsState as LogsUiState.Success).logs
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (logs.isEmpty()) "No logs found for this service." else logs,
                            color = NeonGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is LogsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = (logsState as LogsUiState.Error).message,
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
