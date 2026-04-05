package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

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
                        Text("LOG VIEWER", style = MaterialTheme.typography.titleLarge, color = colors.tertiary, fontFamily = FontFamily.Monospace)
                        Text(currentService, style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant, fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = colors.tertiary)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedServer?.let { viewModel.fetchLogs(it, currentService) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = colors.tertiary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background.copy(alpha = 0.95f))
            )
        },
        containerColor = colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            // Seletor de serviço horizontal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.5f))
                    .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceSm)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
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
                                fontSize = 13.sp,
                                color = if (selected) colors.background else colors.tertiary.copy(alpha = 0.7f)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tertiary,
                            containerColor = Color.Transparent,
                            selectedLabelColor = colors.background
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = colors.tertiary.copy(alpha = 0.3f),
                            selectedBorderColor = colors.tertiary
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            HorizontalDivider(color = colors.tertiary.copy(alpha = 0.1f))

            Box(modifier = Modifier.weight(1f)) {
                when (logsState) {
                    is LogsUiState.Loading -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(Dimens.SpaceLg),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
                        ) {
                            items(12) { i ->
                                val width = if (i % 3 == 0) 0.9f else if (i % 3 == 1) 0.7f else 0.55f
                                ShimmerBox(
                                    modifier = Modifier.fillMaxWidth(width).height(12.dp),
                                    shape = AppShapes.small
                                )
                            }
                        }
                    }

                    is LogsUiState.Success -> {
                        if (logLines.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Nenhum log encontrado para $currentService",
                                    color = colors.tertiary.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.SpaceMd),
                                contentPadding = PaddingValues(vertical = Dimens.SpaceMd)
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
                                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
                            ) {
                                Text("ERR", color = StatusColors.critical, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                                Text(
                                    (logsState as LogsUiState.Error).message,
                                    color = StatusColors.critical.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
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
                            .padding(Dimens.SpaceLg)
                            .size(Dimens.TopBarButton),
                        containerColor = colors.tertiary.copy(alpha = 0.2f),
                        contentColor = colors.tertiary
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
                        .background(colors.surface.copy(alpha = 0.5f))
                        .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceXs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${logLines.size} linhas", color = colors.tertiary.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(currentService, color = colors.tertiary.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LogLine(index: Int, line: String) {
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

    // Coloração semântica de linhas de log
    val (lineColor, _) = when {
        line.contains("ERROR", ignoreCase = true) || line.contains("CRIT", ignoreCase = true) ->
            Pair(StatusColors.critical, "ERR")
        line.contains("WARN", ignoreCase = true) ->
            Pair(StatusColors.warning, "WRN")
        line.contains("INFO", ignoreCase = true) ->
            Pair(colors.tertiary.copy(alpha = 0.8f), "INF")
        line.contains("DEBUG", ignoreCase = true) ->
            Pair(colors.primary.copy(alpha = 0.6f), "DBG")
        else -> Pair(colors.tertiary.copy(alpha = 0.55f), "   ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        // Número da linha
        Text(
            "${index + 1}".padStart(4),
            color = colors.tertiary.copy(alpha = 0.18f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.width(Dimens.Space4xl)
        )
        // Conteúdo
        Text(
            text = line,
            color = lineColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 15.sp
        )
    }
}
