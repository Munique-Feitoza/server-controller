use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{PidExt, ProcessExt, System, SystemExt};

/// Métricas de processos
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProcessMetrics {
    pub top_processes: Vec<ProcessInfo>,
    /// Quantidade de containers rodando (se docker estiver disponível)
    pub docker_containers_running: Option<usize>,
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
        processes.sort_by(|a, b| {
            b.cpu_usage
                .partial_cmp(&a.cpu_usage)
                .unwrap_or(std::cmp::Ordering::Equal)
        });
        let top_processes = processes.into_iter().take(10).collect();

        // Conta os containers ativos via CLI do Docker (com timeout para evitar hangs do daemon)
        // O timeout nativo do Linux blinda a thread do Tokio de ficar travada infinitamente
        let docker_output = std::process::Command::new("timeout")
            .args(["2s", "docker", "ps", "-q"])
            .output()
            .ok();

        let docker_containers_running = docker_output.and_then(|output| {
            if output.status.success() {
                let out_str = String::from_utf8_lossy(&output.stdout);
                Some(out_str.lines().count())
            } else {
                None
            }
        });

        Ok(Self {
            top_processes,
            docker_containers_running,
        })
    }
}
