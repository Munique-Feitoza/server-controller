package com.pocketnoc.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.ui.viewmodels.AdminAccessUiState
import com.pocketnoc.ui.viewmodels.AdminAccessViewModel
import com.pocketnoc.ui.viewmodels.AdminEvent

/**
 * Tela "Acessos Admin" — lista os administradores WordPress criados (reportados
 * pelo mu-plugin WinUp Security Guard ao agente) e permite revogar cada um
 * direto do celular.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAccessScreen(
    server: ServerEntity,
    viewModel: AdminAccessViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val actionMsg by viewModel.actionMsg.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(server.id) { viewModel.fetch(server) }
    LaunchedEffect(actionMsg) {
        actionMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearActionMsg()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acessos Admin") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetch(server) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is AdminAccessUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is AdminAccessUiState.Empty ->
                    Text(
                        text = "Nenhum admin criado registrado.\n\nQuando um administrador for criado " +
                            "em qualquer site deste servidor, o evento aparece aqui para revisao.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                is AdminAccessUiState.Error ->
                    Text(
                        text = "Erro ao carregar: ${s.message}",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.error
                    )

                is AdminAccessUiState.Success ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(s.events, key = { it.incidentId }) { ev ->
                            AdminEventCard(event = ev, onRevoke = { viewModel.revoke(server, ev) })
                        }
                    }
            }
        }
    }
}

@Composable
private fun AdminEventCard(event: AdminEvent, onRevoke: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "🚨 ${event.site}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            InfoLine("Conta", event.login)
            InfoLine("Email", event.email.ifBlank { "—" })
            InfoLine("Criado por", event.createdBy)
            InfoLine("IP", event.ip)
            InfoLine("Quando", event.timestamp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { confirm = true },
                modifier = Modifier.align(Alignment.End),
                enabled = event.userId > 0 && event.path.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apagar este admin")
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar administrador?") },
            text = {
                Text(
                    "Remover a conta '${event.login}' de ${event.site}?\n\n" +
                        "O conteudo dela e reatribuido a outro administrador. Acao irreversivel."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRevoke()
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}
