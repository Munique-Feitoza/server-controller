use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{PidExt, ProcessExt, System, SystemExt};

/// Métricas de processos
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProcessMetrics {
    pub top_processes: Vec<ProcessInfo>,
}

/// Informações de um processo
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProcessInfo {
    pub pid: u32,
    pub name: String,
    pub cpu_usage: f32,
    pub memory_mb: u64,
}

impl ProcessMetrics {
    pub fn collect(system: &System) -> Result<Self> {
        let mut processes: Vec<ProcessInfo> = system
            .processes()
            .iter()
            .map(|(pid, process)| ProcessInfo {
                pid: pid.as_u32(),
                name: process.name().to_string(),
                cpu_usage: process.cpu_usage(),
                memory_mb: process.memory() / 1024 / 1024,
            })
            .collect();

        // Ordena por uso de CPU (decrescente) e pega os top 10
        processes.sort_by(|a, b| b.cpu_usage.partial_cmp(&a.cpu_usage).unwrap_or(std::cmp::Ordering::Equal));
        let top_processes = processes.into_iter().take(10).collect();

        Ok(Self { top_processes })
    }
}
