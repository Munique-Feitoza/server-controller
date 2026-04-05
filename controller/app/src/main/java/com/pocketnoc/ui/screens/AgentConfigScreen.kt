package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    val servers by viewModel.allServers.collectAsState()
    val server = servers.find { it.id == serverId }

    var config by remember { mutableStateOf<AgentRuntimeConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Campos editaveis
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
                    .background(colors.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.RadiusLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(AppShapes.medium)
                            .background(colors.primary.copy(alpha = 0.10f))
                            .border(Dimens.BorderThin, colors.primary.copy(alpha = 0.35f), AppShapes.medium)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = colors.primary, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(Dimens.SpaceLg))
                    Column {
                        Text(
                            "AGENT CONFIG",
                            color = colors.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(server?.name ?: "Servidor", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(Dimens.BorderThin)
                        .background(colors.primary.copy(alpha = 0.3f))
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            Text("ERR", color = StatusColors.critical, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                            Text(errorMsg ?: "", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                config != null -> {
                    val cfg = config!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.RadiusCard),
                        contentPadding = PaddingValues(vertical = Dimens.ScreenPadding)
                    ) {
                        // Informacoes somente leitura
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

                        // Campos editaveis
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(8.dp, spotColor = colors.tertiary.copy(alpha = 0.2f)),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                                shape = AppShapes.card
                            ) {
                                Column(modifier = Modifier.padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
                                    Text("WATCHDOG CONFIG", style = MaterialTheme.typography.labelSmall, color = colors.tertiary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                                    ConfigField("Cycle Interval (seconds)", watchdogInterval, colors.tertiary) { watchdogInterval = it }
                                    ConfigField("Max Failures (circuit breaker)", maxFailures, StatusColors.warning) { maxFailures = it }
                                    ConfigField("Cooldown (seconds)", cooldownSecs, colors.primary) { cooldownSecs = it }
                                    ConfigField("Rate Limit (req/min)", rateLimitPerMin, ext.magenta) { rateLimitPerMin = it }
                                }
                            }
                        }

                        // Botao de salvar
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
                                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary),
                                shape = AppShapes.xl
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(Dimens.IconSm), color = colors.background)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = colors.background, modifier = Modifier.size(Dimens.IconSm))
                                    Spacer(Modifier.width(Dimens.SpaceMd))
                                    Text("SAVE & APPLY", color = colors.background, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, spotColor = colors.primary.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = AppShapes.card
    ) {
        Column(modifier = Modifier.padding(Dimens.ScreenPadding)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(Dimens.SpaceLg))
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
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
    val colors = MaterialTheme.colorScheme

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
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            cursorColor = accentColor,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}
