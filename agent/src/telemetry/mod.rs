use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{System, SystemExt};

mod cpu;
mod memory;
mod disk;
mod temperature;
mod network;
mod security;
mod processes;

pub use cpu::CpuMetrics;
pub use memory::MemoryMetrics;
pub use disk::DiskMetrics;
pub use temperature::TemperatureMetrics;
pub use network::NetworkMetrics;
pub use security::SecurityMetrics;
pub use processes::ProcessMetrics;

/// Estrutura completa de telemetria do sistema
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemTelemetry {
    pub cpu: CpuMetrics,
    pub memory: MemoryMetrics,
    pub disk: DiskMetrics,
    pub temperature: Option<TemperatureMetrics>,
    pub network: NetworkMetrics,
    pub security: SecurityMetrics,
    pub processes: ProcessMetrics,
    pub uptime: UptimeInfo,
    pub timestamp: i64,
}

/// Informações de tempo de atividade
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UptimeInfo {
    /// Segundos desde o boot
    pub uptime_seconds: u64,
    /// Load average (1m, 5m, 15m)
    pub load_average: [f64; 3],
}

impl UptimeInfo {
    fn collect() -> Result<Self> {
        let uptime = procfs::Uptime::new()
            .map_err(|e| crate::error::AgentError::TelemetryError(format!("Failed to read uptime: {}", e)))?;

        let load_avg = procfs::LoadAverage::new()
            .map_err(|e| crate::error::AgentError::TelemetryError(format!("Failed to read load average: {}", e)))?;

        Ok(Self {
            uptime_seconds: uptime.uptime as u64,
            load_average: [load_avg.one as f64, load_avg.five as f64, load_avg.fifteen as f64],
        })
    }
}

/// Coletor principal de telemetria
pub struct TelemetryCollector {
    system: System,
}

impl TelemetryCollector {
    /// Cria um novo coletor de telemetria
    pub fn new() -> Self {
        Self {
            system: System::new_all(),
        }
    }

    /// Coleta toda a telemetria do sistema
    pub fn collect(&mut self) -> Result<SystemTelemetry> {
        self.system.refresh_all();

        let cpu = CpuMetrics::collect(&self.system)?;
        let memory = MemoryMetrics::collect(&self.system)?;
        let disk = DiskMetrics::collect(&self.system)?;
        let temperature = TemperatureMetrics::collect().ok();
        let network = NetworkMetrics::collect(&self.system)?;
        let security = SecurityMetrics::collect()?;
        let processes = ProcessMetrics::collect(&self.system)?;
        let uptime = UptimeInfo::collect()?;

        let timestamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;

        Ok(SystemTelemetry {
            cpu,
            memory,
            disk,
            temperature,
            network,
            security,
            processes,
            uptime,
            timestamp,
        })
    }
}

impl Default for TelemetryCollector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_telemetry_collection() {
        let mut collector = TelemetryCollector::new();
        let telemetry = collector.collect().unwrap();

        assert!(telemetry.cpu.usage_percent >= 0.0 && telemetry.cpu.usage_percent <= 100.0);
        assert!(telemetry.memory.usage_percent >= 0.0 && telemetry.memory.usage_percent <= 100.0);
        assert!(telemetry.uptime.uptime_seconds > 0);
    }
}
