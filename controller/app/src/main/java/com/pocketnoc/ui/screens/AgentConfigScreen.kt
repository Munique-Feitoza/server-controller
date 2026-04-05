package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.models.AgentRuntimeConfig
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigScreen(
    viewModel: DashboardViewModel,
    serverId: Int,
    onNavigateBack: () -> Unit
) {
    val servers by viewModel.allServers.collectAsState()
    val server = servers.find { it.id == serverId }

    var config by remember { mutableStateOf<AgentRuntimeConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Editable fields
    var watchdogInterval by remember { mutableStateOf("") }
    var maxFailures by remember { mutableStateOf("") }
    var cooldownSecs by remember { mutableStateOf("") }
    var rateLimitPerMin by remember { mutableStateOf("") }

    LaunchedEffect(server) {
        server?.let {
            try {
                val cfg = viewModel.fetchAgentConfig(it)
                config = cfg
                watchdogInterval = cfg.watchdogIntervalSecs.toString()
                maxFailures = cfg.watchdogMaxFailures.toString()
                cooldownSecs = cfg.watchdogCooldownSecs.toString()
                rateLimitPerMin = cfg.rateLimitPerMinute.toString()
                errorMsg = null
            } catch (e: Exception) {
                errorMsg = e.message
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A0F1E), DarkSurface.copy(alpha = 0.95f))))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(NeonCyan.copy(alpha = 0.10f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonCyan, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "AGENT CONFIG",
                            color = NeonCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(server?.name ?: "Servidor", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(alpha = 0.6f), Color.Transparent)))
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Brush.verticalGradient(listOf(DarkBackground, Color(0xFF0A1428), DarkBackground)))
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ERR", color = CriticalRedHealth, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                            Text(errorMsg ?: "", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                config != null -> {
                    val cfg = config!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Read-only info
                        item {
                            InfoCard(
                                title = "SERVER IDENTITY",
                                items = listOf(
                                    "Server ID" to cfg.serverId,
                                    "Role" to cfg.serverRole,
                                    "TLS" to if (cfg.tlsEnabled) "Enabled" else "Disabled",
                                    "Watchdog" to if (cfg.watchdogEnabled) "Active" else "Disabled"
                                )
                            )
                        }

                        // Editable fields
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonGreen.copy(alpha = 0.2f)),
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("WATCHDOG CONFIG", style = MaterialTheme.typography.labelSmall, color = NeonGreen, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                                    ConfigField("Cycle Interval (seconds)", watchdogInterval, NeonGreen) { watchdogInterval = it }
                                    ConfigField("Max Failures (circuit breaker)", maxFailures, AlertYellow) { maxFailures = it }
                                    ConfigField("Cooldown (seconds)", cooldownSecs, NeonCyan) { cooldownSecs = it }
                                    ConfigField("Rate Limit (req/min)", rateLimitPerMin, NeonMagenta) { rateLimitPerMin = it }
                                }
                            }
                        }

                        // Save button
                        item {
                            Button(
                                onClick = {
                                    server?.let { srv ->
                                        isSaving = true
                                        val updates = mutableMapOf<String, Any>()
                                        watchdogInterval.toLongOrNull()?.let { updates["watchdog_interval_secs"] = it }
                                        maxFailures.toIntOrNull()?.let { updates["watchdog_max_failures"] = it }
                                        cooldownSecs.toLongOrNull()?.let { updates["watchdog_cooldown_secs"] = it }
                                        rateLimitPerMin.toIntOrNull()?.let { updates["rate_limit_per_minute"] = it }
                                        viewModel.updateAgentConfig(srv, updates)
                                        isSaving = false
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("SAVE & APPLY", color = DarkBackground, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, items: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, spotColor = NeonCyan.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    accentColor: Color,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = accentColor.copy(alpha = 0.3f),
            focusedLabelColor = accentColor,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accentColor,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}
