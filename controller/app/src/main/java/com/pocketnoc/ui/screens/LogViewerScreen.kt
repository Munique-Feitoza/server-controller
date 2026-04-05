package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.ui.components.ShimmerBox
import com.pocketnoc.ui.theme.*
import com.pocketnoc.ui.viewmodels.DashboardViewModel
import com.pocketnoc.ui.viewmodels.LogsUiState
import kotlinx.coroutines.launch

// Serviços disponíveis para visualização de logs
private val logServices = listOf("pocket-noc-agent", "nginx", "docker", "mysql", "sshd")

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

    var currentService by remember { mutableStateOf(serviceName) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedServer, currentService) {
        selectedServer?.let { viewModel.fetchLogs(it, currentService) }
    }

    // Auto-scroll para o final quando novos logs chegam
    val logs = (logsState as? LogsUiState.Success)?.logs
    val logLines = remember(logs) { logs?.lines() ?: emptyList() }
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LOG VIEWER", style = MaterialTheme.typography.titleLarge, color = NeonGreen, fontFamily = FontFamily.Monospace)
                        Text(currentService, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonGreen)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedServer?.let { viewModel.fetchLogs(it, currentService) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF050A0A))
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF060A06))
        ) {
            // Seletor de serviço horizontal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF080E08))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                logServices.forEach { service ->
                    val selected = service == currentService
                    FilterChip(
                        selected = selected,
                        onClick = { currentService = service },
                        label = {
                            Text(
                                service,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = if (selected) Color.Black else NeonGreen.copy(alpha = 0.7f)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen,
                            containerColor = Color.Transparent,
                            selectedLabelColor = Color.Black
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = NeonGreen.copy(alpha = 0.3f),
                            selectedBorderColor = NeonGreen
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            HorizontalDivider(color = NeonGreen.copy(alpha = 0.1f))

            Box(modifier = Modifier.weight(1f)) {
                when (logsState) {
                    is LogsUiState.Loading -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(12) { i ->
                                val width = if (i % 3 == 0) 0.9f else if (i % 3 == 1) 0.7f else 0.55f
                                ShimmerBox(
                                    modifier = Modifier.fillMaxWidth(width).height(12.dp),
                                    shape = RoundedCornerShape(2.dp)
                                )
                            }
                        }
                    }

                    is LogsUiState.Success -> {
                        if (logLines.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Nenhum log encontrado para $currentService",
                                    color = NeonGreen.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                itemsIndexed(logLines) { index, line ->
                                    LogLine(index = index, line = line)
                                }
                            }
                        }
                    }

                    is LogsUiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("ERR", color = CriticalRedHealth, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                                Text(
                                    (logsState as LogsUiState.Error).message,
                                    color = CriticalRedHealth.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Botão scroll para o final
                if (logLines.size > 5) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(logLines.size - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(40.dp),
                        containerColor = NeonGreen.copy(alpha = 0.2f),
                        contentColor = NeonGreen
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Ir ao final", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Rodapé: contagem de linhas
            if (logLines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF080E08))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${logLines.size} linhas", color = NeonGreen.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text(currentService, color = NeonGreen.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun LogLine(index: Int, line: String) {
    // Coloração semântica de linhas de log
    val (lineColor, _) = when {
        line.contains("ERROR", ignoreCase = true) || line.contains("CRIT", ignoreCase = true) ->
            Pair(CriticalRedHealth, "ERR")
        line.contains("WARN", ignoreCase = true) ->
            Pair(AlertYellow, "WRN")
        line.contains("INFO", ignoreCase = true) ->
            Pair(NeonGreen.copy(alpha = 0.8f), "INF")
        line.contains("DEBUG", ignoreCase = true) ->
            Pair(NeonCyan.copy(alpha = 0.6f), "DBG")
        else -> Pair(NeonGreen.copy(alpha = 0.55f), "   ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Número da linha
        Text(
            "${index + 1}".padStart(4),
            color = NeonGreen.copy(alpha = 0.18f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(32.dp)
        )
        // Conteúdo
        Text(
            text = line,
            color = lineColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}
