use crate::error::Result;
use serde::{Deserialize, Serialize};

use super::SystemTelemetry;

/// Tipos de alertas possíveis
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
#[serde(rename_all = "lowercase")]
pub enum AlertType {
    /// CPU acima do threshold configurado
    HighCpu,
    /// Memória acima do threshold configurado
    HighMemory,
    /// Disco acima do threshold configurado
    HighDisk,
    /// Temperatura acima do threshold configurado
    HighTemperature,
    /// Taxa de fail2ban alto (múltiplas tentativas de acesso não autorizado)
    SecurityThreat,
    /// Uptime muito baixo (servidor reiniciou recentemente)
    RecentReboot,
}

impl AlertType {
    pub fn label(&self) -> &str {
        match self {
            Self::HighCpu => "Alta Carga de CPU",
            Self::HighMemory => "Alta Utilização de Memória",
            Self::HighDisk => "Espaço em Disco Crítico",
            Self::HighTemperature => "Temperatura Elevada",
            Self::SecurityThreat => "Ameaça de Segurança",
            Self::RecentReboot => "Servidor Reiniciado Recentemente",
        }
    }

    pub fn emoji(&self) -> &str {
        match self {
            Self::HighCpu => "⚡",
            Self::HighMemory => "🧠",
            Self::HighDisk => "💾",
            Self::HighTemperature => "🌡️",
            Self::SecurityThreat => "🚨",
            Self::RecentReboot => "🔄",
        }
    }

    pub fn color(&self) -> &str {
        match self {
            Self::HighCpu | Self::HighDisk => "#FF9800",      // Orange
            Self::HighMemory => "#FF5722",                     // Red-Orange
            Self::HighTemperature | Self::SecurityThreat => "#F44336", // Red
            Self::RecentReboot => "#2196F3",                   // Blue
        }
    }

    /// Retorna a prioridade para o ntfy (1-5)
    pub fn ntfy_priority(&self) -> u8 {
        match self {
            Self::HighCpu | 
            Self::HighDisk |
            Self::HighTemperature |
            Self::SecurityThreat => 4, // High
            _ => 3, // Default
        }
    }

    /// Retorna as tags para o ntfy
    pub fn ntfy_tags(&self) -> &str {
        match self {
            Self::HighCpu => "cpu,warning",
            Self::SecurityThreat => "lock,skull,danger",
            Self::RecentReboot => "reboot,info",
            Self::HighDisk => "floppy_disk,warning",
            Self::HighTemperature => "thermometer,warning",
            _ => "warning",
        }
    }
}

/// Representa um alerta individual
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Alert {
    /// Tipo do alerta
    pub alert_type: AlertType,
    /// Mensagem descritiva do alerta
    pub message: String,
    /// Valor atual da métrica
    pub current_value: f32,
    /// Threshold que foi ultrapassado
    pub threshold: f32,
    /// Timestamp em Unix quando o alerta foi gerado
    pub timestamp: i64,
    /// Componente que gerou o alerta (ex: "disk /")
    pub component: Option<String>,
}

/// Configuração de thresholds para alertas
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct AlertThresholds {
    /// CPU acima de X% (padrão: 80%)
    pub cpu_threshold_percent: f32,
    /// Memória acima de X% (padrão: 85%)
    pub memory_threshold_percent: f32,
    /// Disco acima de X% (padrão: 90%)
    pub disk_threshold_percent: f32,
    /// Temperatura acima de X°C (padrão: 80)
    pub temperature_threshold_celsius: f32,
    /// Minutos desde o boot para alertar de reboot (padrão: 5)
    pub reboot_threshold_minutes: u64,
    /// Mínimo de tentativas de login falhas por IP (padrão: 10)
    pub security_threat_threshold: usize,
}

impl Default for AlertThresholds {
    fn default() -> Self {
        Self {
            cpu_threshold_percent: 80.0,
            memory_threshold_percent: 85.0,
            disk_threshold_percent: 90.0,
            temperature_threshold_celsius: 80.0,
            reboot_threshold_minutes: 5,
            security_threat_threshold: 10,
        }
    }
}

/// Gerenciador de alertas
pub struct AlertManager {
    thresholds: AlertThresholds,
}

impl AlertManager {
    pub fn new(thresholds: AlertThresholds) -> Self {
        Self { thresholds }
    }

    pub fn with_defaults() -> Self {
        Self::new(AlertThresholds::default())
    }

    pub fn update_thresholds(&mut self, thresholds: AlertThresholds) {
        self.thresholds = thresholds;
    }

    pub fn get_thresholds(&self) -> &AlertThresholds {
        &self.thresholds
    }

    /// Analisa a telemetria e retorna uma lista de alertas ativos
    pub fn analyze(&self, telemetry: &SystemTelemetry) -> Result<Vec<Alert>> {
        let mut alerts = Vec::new();
        let now = chrono::Utc::now().timestamp();

        // Verificar CPU
        if telemetry.cpu.usage_percent > self.thresholds.cpu_threshold_percent {
            alerts.push(Alert {
                alert_type: AlertType::HighCpu,
                message: format!(
                    "CPU em alta carga: {:.1}% (máx: {:.1}%)",
                    telemetry.cpu.usage_percent, self.thresholds.cpu_threshold_percent
                ),
                current_value: telemetry.cpu.usage_percent,
                threshold: self.thresholds.cpu_threshold_percent,
                timestamp: now,
                component: None,
            });
        }

        // Verificar Memória
        if telemetry.memory.usage_percent > self.thresholds.memory_threshold_percent {
            alerts.push(Alert {
                alert_type: AlertType::HighMemory,
                message: format!(
                    "Memória em alta utilização: {:.1}% ({} MB / {} MB)",
                    telemetry.memory.usage_percent, telemetry.memory.used_mb, telemetry.memory.total_mb
                ),
                current_value: telemetry.memory.usage_percent,
                threshold: self.thresholds.memory_threshold_percent,
                timestamp: now,
                component: None,
            });
        }

        // Verificar Discos
        for disk in &telemetry.disk.disks {
            if disk.usage_percent > self.thresholds.disk_threshold_percent {
                alerts.push(Alert {
                    alert_type: AlertType::HighDisk,
                    message: format!(
                        "Disco {} em uso crítico: {:.1}% ({:.1} GB / {:.1} GB)",
                        disk.mount_point, disk.usage_percent, disk.used_gb, disk.total_gb
                    ),
                    current_value: disk.usage_percent,
                    threshold: self.thresholds.disk_threshold_percent,
                    timestamp: now,
                    component: Some(disk.mount_point.clone()),
                });
            }
        }

        // Verificar Temperatura
        if let Some(temp_metrics) = &telemetry.temperature {
            if let Some(max_temp) = temp_metrics.sensors.iter().map(|r| r.celsius).max_by(|a: &f32, b: &f32| a.partial_cmp(b).unwrap()) {
                if max_temp > self.thresholds.temperature_threshold_celsius {
                    alerts.push(Alert {
                        alert_type: AlertType::HighTemperature,
                        message: format!(
                            "Temperatura do sistema elevada: {:.1}°C (máx: {:.1}°C)",
                            max_temp, self.thresholds.temperature_threshold_celsius
                        ),
                        current_value: max_temp,
                        threshold: self.thresholds.temperature_threshold_celsius,
                        timestamp: now,
                        component: None,
                    });
                }
            }
        }

        // Verificar reboot recente
        let uptime_minutes = telemetry.uptime.uptime_seconds / 60;
        if uptime_minutes < self.thresholds.reboot_threshold_minutes {
            alerts.push(Alert {
                alert_type: AlertType::RecentReboot,
                message: format!(
                    "Servidor foi reiniciado há menos de {} minutos (uptime: {} min)",
                    self.thresholds.reboot_threshold_minutes, uptime_minutes
                ),
                current_value: uptime_minutes as f32,
                threshold: self.thresholds.reboot_threshold_minutes as f32,
                timestamp: now,
                component: None,
            });
        }

        // Verificar ameaças de segurança — Filtrado por IP individual (HackerSec Core)
        // Só dispara se um ÚNICO IP atingir o threshold configurado
        if let Some(offender) = telemetry.security.failed_logins.iter().find(|f| f.count >= self.thresholds.security_threat_threshold) {
            alerts.push(Alert {
                alert_type: AlertType::SecurityThreat,
                message: format!(
                    "Detectado ataque massivo: IP {} com {} tentativas de acesso",
                    offender.ip, offender.count
                ),
                current_value: offender.count as f32,
                threshold: self.thresholds.security_threat_threshold as f32,
                timestamp: now,
                component: Some(offender.ip.clone()),
            });
        }

        Ok(alerts)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_alert_type_properties() {
        let alert_type = AlertType::HighCpu;
        assert_eq!(alert_type.label(), "Alta Carga de CPU");
        assert_eq!(alert_type.emoji(), "⚡");
        assert!(!alert_type.color().is_empty());
    }

    #[test]
    fn test_default_thresholds() {
        let thresholds = AlertThresholds::default();
        assert_eq!(thresholds.cpu_threshold_percent, 80.0);
        assert_eq!(thresholds.memory_threshold_percent, 85.0);
        assert_eq!(thresholds.disk_threshold_percent, 90.0);
    }
}
