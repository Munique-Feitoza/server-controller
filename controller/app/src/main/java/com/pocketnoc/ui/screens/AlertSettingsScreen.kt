package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val colors = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current

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
                            color = ext.magenta,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Limiares de disparo de alertas",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.outlineVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = ext.magenta)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface.copy(alpha = 0.9f)),
                modifier = Modifier.shadow(8.dp, spotColor = ext.magenta.copy(alpha = 0.25f))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
            ) {
                // Cabeçalho informativo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.large)
                        .background(ext.magenta.copy(alpha = 0.05f))
                        .border(Dimens.BorderThin, ext.magenta.copy(alpha = 0.2f), AppShapes.large)
                        .padding(Dimens.SpaceLg),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.RadiusLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ext.magenta.copy(alpha = 0.7f), modifier = Modifier.size(Dimens.IconSm))
                    Text(
                        "Alertas são disparados quando o valor medido excede o limiar configurado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
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
                    accentColor = ext.magenta
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Memory,
                    label       = "MEM\u00D3RIA",
                    description = "Uso de RAM",
                    value       = memoryThreshold,
                    onValueChange = { memoryThreshold = it },
                    unit        = "%",
                    range       = 30f..100f,
                    accentColor = ext.blue
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Storage,
                    label       = "DISCO",
                    description = "Uso de armazenamento",
                    value       = diskThreshold,
                    onValueChange = { diskThreshold = it },
                    unit        = "%",
                    range       = 50f..100f,
                    accentColor = colors.primary
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Thermostat,
                    label       = "TEMPERATURA",
                    description = "Temperatura do hardware",
                    value       = temperatureThreshold,
                    onValueChange = { temperatureThreshold = it },
                    unit        = "\u00B0C",
                    range       = 40f..100f,
                    accentColor = StatusColors.warning
                )

                ThresholdSliderCard(
                    icon        = Icons.Default.Refresh,
                    label       = "REBOOT",
                    description = "Alerta para reinicializações recentes",
                    value       = rebootThreshold,
                    onValueChange = { rebootThreshold = it },
                    unit        = "min",
                    range       = 1f..60f,
                    accentColor = colors.tertiary
                )

                Spacer(modifier = Modifier.height(Dimens.SpaceMd))

                // Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
                ) {
                    OutlinedButton(
                        onClick  = onBack,
                        modifier = Modifier.weight(1f).height(Dimens.ButtonHeight),
                        shape    = AppShapes.large,
                        border   = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(colors.outlineVariant.copy(alpha = 0.4f), colors.outlineVariant.copy(alpha = 0.2f)))
                        )
                    ) {
                        Text("Cancelar", color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
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
                        modifier = Modifier.weight(1f).height(Dimens.ButtonHeight),
                        colors   = ButtonDefaults.buttonColors(containerColor = ext.magenta.copy(alpha = 0.15f)),
                        border   = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(ext.magenta.copy(alpha = 0.8f), ext.magenta.copy(alpha = 0.4f)))
                        ),
                        shape    = AppShapes.large
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = ext.magenta, modifier = Modifier.size(Dimens.IconSm))
                        Spacer(modifier = Modifier.width(Dimens.SpaceSm))
                        Text("SALVAR", color = ext.magenta, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.ScreenPadding))
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
    val colors = MaterialTheme.colorScheme

    val pct = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val valueColor = when {
        pct > 0.85f -> StatusColors.critical
        pct > 0.65f -> StatusColors.warning
        else        -> accentColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, AppShapes.xl, spotColor = accentColor.copy(alpha = 0.2f))
            .clip(AppShapes.xl)
            .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.07f), colors.surfaceVariant)))
            .border(Dimens.BorderThin, accentColor.copy(alpha = 0.3f), AppShapes.xl)
            .padding(Dimens.ScreenPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // \u00CDcone
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accentColor.copy(alpha = 0.12f), AppShapes.large)
                    .border(Dimens.BorderThin, accentColor.copy(alpha = 0.4f), AppShapes.large),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(Dimens.SpaceLg))

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
                    color = colors.onSurfaceVariant
                )
            }

            // Valor atual
            Box(
                modifier = Modifier
                    .background(valueColor.copy(alpha = 0.12f), AppShapes.medium)
                    .border(Dimens.BorderThin, valueColor.copy(alpha = 0.4f), AppShapes.medium)
                    .padding(horizontal = Dimens.RadiusLg, vertical = Dimens.SpaceXs)
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

        Spacer(modifier = Modifier.height(Dimens.SpaceLg))

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${range.start.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
            Text("${range.endInclusive.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = colors.outlineVariant)
        }
    }
}
