use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{System, SystemExt};
use crate::services::{ServiceInfo, ServiceMonitor};

/// POR QUE RUST? (Decisão de Engenharia)
/// Escolhi Rust para o Agente por ser uma linguagem de sistemas que oferece "Zero-Cost Abstractions".
/// Isso significa que o monitoramento consome o mínimo possível de CPU e RAM (footprint < 15MB),
/// garantindo que o próprio monitor não interfira na performance do servidor que está vigiando.
/// Além disso, o sistema de tipos e o 'borrow checker' eliminam bugs de memória e race conditions
/// em tempo de compilação, o que é crítico para um serviço que roda como root.

mod cpu;
mod memory;
mod disk;
mod temperature;
mod network;
mod security;
mod processes;
pub mod alerts;

pub use cpu::CpuMetrics;
pub use memory::MemoryMetrics;
pub use disk::DiskMetrics;
pub use temperature::TemperatureMetrics;
pub use network::NetworkMetrics;
pub use security::SecurityMetrics;
pub use processes::ProcessMetrics;
pub use alerts::{Alert, AlertManager, AlertThresholds, AlertType};

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
    pub services: Vec<ServiceInfo>,
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
/// 
/// Aqui exploramos a eficiência do Rust ao interagir diretamente com o subsistema /proc do Linux.
/// Diferente de soluções em linguagens de alto nível, o Rust nos permite gerenciar buffers de forma
/// extremamente granulada, minimizando syscalls e alocações desnecessárias.
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
        let services = ServiceMonitor::check_multiple_services(&["nginx", "docker", "mysql", "pocket-noc-agent"]);

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
            services,
            timestamp,
        })
    }

    /// Encerra um processo pelo PID
    pub fn kill_process(&mut self, pid: u32) -> Result<bool> {
        use sysinfo::{Pid, PidExt, ProcessExt};
        let sys_pid = Pid::from_u32(pid);
        if let Some(process) = self.system.process(sys_pid) {
            Ok(process.kill())
        } else {
            Err(crate::error::AgentError::CommandError(format!("Process with PID {} not found", pid)))
        }
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
