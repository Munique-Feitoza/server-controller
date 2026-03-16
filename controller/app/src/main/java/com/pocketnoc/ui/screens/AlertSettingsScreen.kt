package com.pocketnoc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnoc.data.local.AlertThresholdConfig
import com.pocketnoc.ui.theme.*

@Composable
fun AlertSettingsScreen(
    currentConfig: AlertThresholdConfig,
    onSaveSettings: (AlertThresholdConfig) -> Unit,
    onBack: () -> Unit
) {
    var cpuThreshold by remember { mutableStateOf(currentConfig.cpuThresholdPercent) }
    var memoryThreshold by remember { mutableStateOf(currentConfig.memoryThresholdPercent) }
    var diskThreshold by remember { mutableStateOf(currentConfig.diskThresholdPercent) }
    var temperatureThreshold by remember { mutableStateOf(currentConfig.temperatureThresholdCelsius) }
    var rebootThreshold by remember { mutableStateOf(currentConfig.rebootThresholdMinutes.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("← Voltar", color = NeonCyan)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "⚙️ Configurar Alertas",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Bold
            )
        }

        // Section: CPU
        AlertThresholdSlider(
            title = "⚡ Limite de CPU",
            description = "Dispara alerta quando CPU ultrapassa o limite",
            emoji = "⚡",
            value = cpuThreshold,
            onValueChange = { cpuThreshold = it },
            unit = "%",
            range = 30f..100f
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Memory
        AlertThresholdSlider(
            title = "🧠 Limite de Memória",
            description = "Dispara alerta quando memória ultrapassa o limite",
            emoji = "🧠",
            value = memoryThreshold,
            onValueChange = { memoryThreshold = it },
            unit = "%",
            range = 30f..100f
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Disk
        AlertThresholdSlider(
            title = "💾 Limite de Disco",
            description = "Dispara alerta quando disco ultrapassa o limite",
            emoji = "💾",
            value = diskThreshold,
            onValueChange = { diskThreshold = it },
            unit = "%",
            range = 50f..100f
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Temperature
        AlertThresholdSlider(
            title = "🌡️ Limite de Temperatura",
            description = "Dispara alerta quando temperatura ultrapassa o limite",
            emoji = "🌡️",
            value = temperatureThreshold,
            onValueChange = { temperatureThreshold = it },
            unit = "°C",
            range = 40f..100f
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Reboot
        AlertThresholdSlider(
            title = "🔄 Limite de Reboot",
            description = "Alerta para reinicializações recentes",
            emoji = "🔄",
            value = rebootThreshold,
            onValueChange = { rebootThreshold = it },
            unit = "min",
            range = 1f..60f
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                shape = RoundedCornerShape(8.dp),
                onClick = { onBack() }
            ) {
                Text("Cancelar", color = Color.White)
            }

            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(8.dp),
                onClick = {
                    val newConfig = AlertThresholdConfig(
                        cpuThresholdPercent = cpuThreshold,
                        memoryThresholdPercent = memoryThreshold,
                        diskThresholdPercent = diskThreshold,
                        temperatureThresholdCelsius = temperatureThreshold,
                        rebootThresholdMinutes = rebootThreshold.toLong()
                    )
                    onSaveSettings(newConfig)
                }
            ) {
                Text("Salvar", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AlertThresholdSlider(
    title: String,
    description: String,
    emoji: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    unit: String,
    range: ClosedFloatingPointRange<Float>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1A2551).copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = String.format("%.0f", value) + unit,
                style = MaterialTheme.typography.titleMedium,
                color = NeonGreen,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = NeonPurple.copy(alpha = 0.3f)
            )
        )
    }
}
