package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.models.SystemTelemetry
import com.pocketnoc.ui.components.MetricCard
import com.pocketnoc.ui.components.PercentageBar
import com.pocketnoc.ui.components.StatusTrafficLight
import com.pocketnoc.ui.components.TrafficLightStatus
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.TelemetryUiState

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    serverUrl: String,
    token: String,
    onNavigateToServerDetails: () -> Unit
) {
    val telemetryState = viewModel.telemetryState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchTelemetry(serverUrl, token)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("Pocket NOC") },
            actions = {
                IconButton(onClick = { viewModel.refreshTelemetry(token) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1E1E1E)
            )
        )

        // Content
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = telemetryState.value) {
                is TelemetryUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is TelemetryUiState.Success -> {
                    DashboardContent(
                        telemetry = state.telemetry,
                        onNavigateToServerDetails = onNavigateToServerDetails
                    )
                }

                is TelemetryUiState.Error -> {
                    ErrorContent(message = state.message)
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    telemetry: SystemTelemetry,
    onNavigateToServerDetails: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // Status Traffic Light
        val status = when {
            telemetry.cpu.usagePercent > 90 -> TrafficLightStatus.CRITICAL
            telemetry.memory.usagePercent > 85 -> TrafficLightStatus.CRITICAL
            telemetry.cpu.usagePercent > 70 || telemetry.memory.usagePercent > 70 ->
                TrafficLightStatus.WARNING

            else -> TrafficLightStatus.HEALTHY
        }

        StatusTrafficLight(
            status = status,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // CPU Metrics
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                Text(
                    "CPU",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF00D084)
                )

                PercentageBar(
                    "Global",
                    telemetry.cpu.usagePercent,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Cores: ${telemetry.cpu.coreCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Memory Metrics
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                Text(
                    "Memory",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF03DAC6)
                )

                PercentageBar(
                    "RAM",
                    telemetry.memory.usagePercent,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "${telemetry.memory.usedMb}MB / ${telemetry.memory.totalMb}MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Disk Metrics
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                Text(
                    "Disk",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFFB74D)
                )

                telemetry.disk.disks.forEach { disk ->
                    PercentageBar(
                        disk.mountPoint,
                        disk.usagePercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // More Details Button
        Button(
            onClick = onNavigateToServerDetails,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("View Full Details")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ErrorContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Error Loading Data",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFFEF5350)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
