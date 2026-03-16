package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.local.entities.AlertEntity
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val alertHistory by viewModel.alertHistory.collectAsState()
    
    val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("🛡️ ALERT AUDIT", style = MaterialTheme.typography.displaySmall, color = NeonMagenta)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonMagenta)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearAlertHistory() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.8f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color(0xFF1F0F0F), DarkBackground)
                    )
                )
        ) {
            if (alertHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No alerts recorded yet.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alertHistory) { alert ->
                        AlertHistoryCard(alert = alert, dateFormatter = dateFormatter)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertHistoryCard(alert: AlertEntity, dateFormatter: SimpleDateFormat) {
    val type = alert.type.uppercase()
    
    // Mapeamento de cor baseado no tipo e severidade (HackerSec Core: Segurança é sempre Crítico)
    val typeColor = when {
        type.contains("SECURITY") || type.contains("THREAT") -> CriticalRedHealth
        type.contains("CPU") || type.contains("DISK") || type.contains("TEMPERATURE") -> AlertYellow
        type.contains("MEMORY") -> WarningGreen
        else -> NeonCyan
    }

    // Unidade baseada no tipo de alerta
    val unit = when {
        type.contains("CPU") || type.contains("MEMORY") || type.contains("DISK") -> "%"
        type.contains("REBOOT") -> " min"
        else -> "" // Segurança e outros são contadores absolutos
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, spotColor = typeColor.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, typeColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(typeColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, typeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.serverName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = dateFormatter.format(Date(alert.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.1f", alert.value.toDouble())}$unit",
                        style = MaterialTheme.typography.titleMedium,
                        color = typeColor,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val limitLabel = if (unit == "%") "${alert.threshold.toInt()}%" else "${alert.threshold.toInt()}$unit"
                    Text(
                        text = "(Limit: $limitLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
