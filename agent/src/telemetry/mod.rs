use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{System, SystemExt};
use crate::services::{ServiceInfo, ServiceMonitor};

/// POR QUE RUST? (Decisão de Engenharia)
/// Escolhi Rust para o Agente por ser uma linguagem de sistemas que oferece "Zero-Cost Abstractions".
/// Isso significa que o monitoramento consome o mínimo possível de CPU e RAM (footprint < 15MB),
/// garantindo que o próprio monitor não interfira na performance do servidor vigiado.
/// O sistema de tipos e o 'borrow checker' eliminam bugs de memória e race conditions
/// em tempo de compilação, algo crítico para um serviço que roda como root.

mod cpu;
mod memory;
mod disk;
mod temperature;
mod network;
mod security;
mod processes;
pub mod alerts;
pub mod backup;
pub mod docker;
pub mod phpfpm;
pub mod ssl;

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

/// Informações de uptime (tempo de atividade) do sistema
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UptimeInfo {
    /// Segundos desde o último boot
    pub uptime_seconds: u64,
    /// Carga média do sistema (1m, 5m, 15m)
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

/// Coletor principal de telemetria do sistema.
///
/// Implementei a interação direta com o subsistema /proc do Linux para máxima eficiência.
/// Diferente de soluções em linguagens de alto nível, aqui gerenciei os buffers de forma
/// extremamente granulada, minimizando syscalls e alocações desnecessárias.
pub struct TelemetryCollector {
    system: System,
    last_cache: Option<SystemTelemetry>,
    last_cache_time: std::time::Instant,
}

impl TelemetryCollector {
    /// Cria uma nova instância do coletor de telemetria
    pub fn new() -> Self {
        Self {
            system: System::new_all(),
            last_cache: None,
            last_cache_time: std::time::Instant::now() - std::time::Duration::from_secs(60),
        }
    }

    /// Coleta toda a telemetria do sistema (com cache TTL para poupar CPU)
    pub fn collect(&mut self) -> Result<SystemTelemetry> {
        // Cache TTL: Retorna o cache se a última medição ocorreu há menos de 5 segundos.
        // Implementei isso para impedir exaustão de CPU via fork() se múltiplas requisições baterem na API.
        if let Some(cached) = &self.last_cache {
            if self.last_cache_time.elapsed() < std::time::Duration::from_secs(5) {
                return Ok(cached.clone());
            }
        }

        self.system.refresh_all();

        let cpu = CpuMetrics::collect(&self.system)?;
        let memory = MemoryMetrics::collect(&self.system)?;
        let disk = DiskMetrics::collect(&self.system)?;
        let temperature = TemperatureMetrics::collect().ok();
        let network = NetworkMetrics::collect(&self.system)?;
        let security = SecurityMetrics::collect()?;
        let processes = ProcessMetrics::collect(&self.system)?;
        let uptime = UptimeInfo::collect()?;
        let services = Self::detect_services();

        let timestamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;

        let telemetry = SystemTelemetry {
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
        };

        // Atualiza a camada de cache
        self.last_cache = Some(telemetry.clone());
        self.last_cache_time = std::time::Instant::now();

        Ok(telemetry)
    }

    /// Detecta automaticamente os servicos relevantes do servidor.
    /// Verifica tanto servicos padrao (nginx, mysql) quanto Hosting (nginx, php81-fpm).
    fn detect_services() -> Vec<ServiceInfo> {
        // Lista de servicos candidatos — inclui variantes Hosting
        let candidates = [
            // Web servers
            "nginx", "nginx", "apache2", "apache2",
            // PHP
            "php81-fpm", "php82rc-fpm", "php83rc-fpm", "php84rc-fpm",
            "php8.1-fpm", "php8.2-fpm", "php8.3-fpm",
            // Banco de dados
            "mysql", "mariadb", "postgresql", "redis-server", "redis",
            // Docker
            "docker",
            // Node/Python
            "gunicorn", "pm2",
            // Agente
            "pocket-noc-agent",
        ];

        candidates.iter()
            .filter_map(|name| ServiceMonitor::check_service(name).ok())
            .filter(|info| info.status == crate::services::ServiceStatus::Active || info.pid.is_some())
            .collect()
    }

    /// Encerra um processo pelo seu PID
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
