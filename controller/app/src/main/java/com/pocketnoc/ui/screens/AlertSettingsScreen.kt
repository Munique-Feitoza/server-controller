package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketnoc.data.local.AlertThresholdConfig
import com.pocketnoc.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsScreen(
    currentConfig: AlertThresholdConfig,
    onSaveSettings: (AlertThresholdConfig) -> Unit,
    onBack: () -> Unit
) {
    var cpuThreshold         by remember { mutableStateOf(currentConfig.cpuThresholdPercent) }
    var memoryThreshold      by remember { mutableStateOf(currentConfig.memoryThresholdPercent) }
    var diskThreshold        by remember { mutableStateOf(currentConfig.diskThresholdPercent) }
    var temperatureThreshold by remember { mutableStateOf(currentConfig.temperatureThresholdCelsius) }
    var rebootThreshold      by remember { mutableStateOf(currentConfig.rebootThresholdMinutes.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "ALERT CONFIG",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonMagenta,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Limiares de disparo de alertas",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NeonMagenta)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = NeonMagenta.copy(alpha = 0.25f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(listOf(DarkBackground, Color(0xFF140F1F), DarkBackground))
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabeçalho informativo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonMagenta.copy(alpha = 0.05f))
                        .border(1.dp, NeonMagenta.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonMagenta.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Text(
                        "Alertas são disparados quando o valor medido excede o limiar configurado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                ThresholdSliderCard(
                    icon        = Icons.Default.Speed,
                    label       = "CPU",
                    description = "Uso do processador",
                    value       = cpuThreshold,
                    onValueChange = { cpuThreshold = it },
                    unit        = "%",
                    range       = 30f..100f,
                    accentColor = NeonMagenta
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Memory,
                    label       = "MEMÓRIA",
                    description = "Uso de RAM",
                    value       = memoryThreshold,
                    onValueChange = { memoryThreshold = it },
                    unit        = "%",
                    range       = 30f..100f,
                    accentColor = NeonBlue
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Storage,
                    label       = "DISCO",
                    description = "Uso de armazenamento",
                    value       = diskThreshold,
                    onValueChange = { diskThreshold = it },
                    unit        = "%",
                    range       = 50f..100f,
                    accentColor = NeonCyan
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Thermostat,
                    label       = "TEMPERATURA",
                    description = "Temperatura do hardware",
                    value       = temperatureThreshold,
                    onValueChange = { temperatureThreshold = it },
                    unit        = "°C",
                    range       = 40f..100f,
                    accentColor = WarningOrange
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Refresh,
                    label       = "REBOOT",
                    description = "Alerta para reinicializações recentes",
                    value       = rebootThreshold,
                    onValueChange = { rebootThreshold = it },
                    unit        = "min",
                    range       = 1f..60f,
                    accentColor = NeonGreen
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick  = onBack,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(TextMuted.copy(alpha = 0.4f), TextMuted.copy(alpha = 0.2f)))
                        )
                    ) {
                        Text("Cancelar", color = TextSecondary, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            onSaveSettings(
                                AlertThresholdConfig(
                                    cpuThresholdPercent        = cpuThreshold,
                                    memoryThresholdPercent     = memoryThreshold,
                                    diskThresholdPercent       = diskThreshold,
                                    temperatureThresholdCelsius = temperatureThreshold,
                                    rebootThresholdMinutes     = rebootThreshold.toLong()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = NeonMagenta.copy(alpha = 0.15f)),
                        border   = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(NeonMagenta.copy(alpha = 0.8f), NeonMagenta.copy(alpha = 0.4f)))
                        ),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SALVAR", color = NeonMagenta, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ThresholdSliderCard(
    icon: ImageVector,
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color
) {
    val pct = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val valueColor = when {
        pct > 0.85f -> CriticalRedHealth
        pct > 0.65f -> AlertYellow
        else        -> accentColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = accentColor.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.07f), DarkCard)))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Valor atual
            Box(
                modifier = Modifier
                    .background(valueColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, valueColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = "${String.format("%.0f", value)}$unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = valueColor,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = range,
            modifier      = Modifier.fillMaxWidth(),
            colors        = SliderDefaults.colors(
                thumbColor          = accentColor,
                activeTrackColor    = accentColor,
                inactiveTrackColor  = accentColor.copy(alpha = 0.15f)
            )
        )

        // Labels de range
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${range.start.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("${range.endInclusive.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
