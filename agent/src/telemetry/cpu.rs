use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{CpuExt, System, SystemExt};

/// Métricas de CPU
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CpuMetrics {
    /// Uso global de CPU em percentual
    pub usage_percent: f32,
    /// Número de núcleos
    pub core_count: usize,
    /// Uso por núcleo
    pub cores: Vec<CoreMetrics>,
    /// Frequência média em MHz
    pub frequency_mhz: u64,
}

/// Métricas por núcleo de CPU
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CoreMetrics {
    /// Índice do núcleo
    pub index: usize,
    /// Uso em percentual
    pub usage_percent: f32,
}

impl CpuMetrics {
    pub fn collect(system: &System) -> Result<Self> {
        let cpus = system.cpus();

        if cpus.is_empty() {
            return Err(crate::error::AgentError::TelemetryError(
                "No processors found".to_string(),
            ));
        }

        let usage_percent = cpus
            .iter()
            .map(|p: &sysinfo::Cpu| p.cpu_usage())
            .sum::<f32>()
            / cpus.len() as f32;

        let cores = cpus
            .iter()
            .enumerate()
            .map(|(idx, processor): (usize, &sysinfo::Cpu)| CoreMetrics {
                index: idx,
                usage_percent: processor.cpu_usage(),
            })
            .collect();

        let frequency_mhz = cpus
            .first()
            .map(|p: &sysinfo::Cpu| p.frequency())
            .unwrap_or(0);

        Ok(Self {
            usage_percent,
            core_count: cpus.len(),
            cores,
            frequency_mhz,
        })
    }
}
