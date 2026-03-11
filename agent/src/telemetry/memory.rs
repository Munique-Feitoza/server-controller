use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{System, SystemExt};

/// Métricas de memória e swap
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryMetrics {
    /// Uso de RAM em percentual
    pub usage_percent: f32,
    /// RAM usada em MB
    pub used_mb: u64,
    /// RAM total em MB
    pub total_mb: u64,
    /// Swap usada em MB
    pub swap_used_mb: u64,
    /// Swap total em MB
    pub swap_total_mb: u64,
}

impl MemoryMetrics {
    pub fn collect(system: &System) -> Result<Self> {
        let total_memory = system.total_memory();
        let used_memory = system.used_memory();

        if total_memory == 0 {
            return Err(crate::error::AgentError::TelemetryError(
                "Cannot determine total memory".to_string(),
            ));
        }

        let usage_percent = (used_memory as f32 / total_memory as f32) * 100.0;

        Ok(Self {
            usage_percent,
            used_mb: used_memory / 1024,
            total_mb: total_memory / 1024,
            swap_used_mb: system.used_swap() / 1024,
            swap_total_mb: system.total_swap() / 1024,
        })
    }
}
