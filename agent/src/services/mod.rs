use crate::error::{AgentError, Result};
use serde::{Deserialize, Serialize};
use std::process::Command;

/// Status de um serviço
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum ServiceStatus {
    /// Serviço está rodando
    Active,
    /// Serviço está inativo
    Inactive,
    /// Status desconhecido
    Unknown,
}

/// Informações de um serviço
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServiceInfo {
    /// Nome do serviço
    pub name: String,
    /// Status atual
    pub status: ServiceStatus,
    /// Descrição (se disponível)
    pub description: Option<String>,
    /// PID do processo (se ativo)
    pub pid: Option<u32>,
}

/// Monitor de serviços systemd
pub struct ServiceMonitor;

impl ServiceMonitor {
    /// Verifica o status de um serviço com uma única chamada ao systemctl
    /// (ActiveState + Description + MainPID em um único subprocesso)
    pub fn check_service(service_name: &str) -> Result<ServiceInfo> {
        let output = Command::new("systemctl")
            .args(["show", "-p", "ActiveState", "-p", "Description", "-p", "MainPID", service_name])
            .output()
            .map_err(|e| AgentError::ServiceError(format!("Failed to check service: {}", e)))?;

        let output_str = String::from_utf8_lossy(&output.stdout);

        let mut active_state = "unknown".to_string();
        let mut description: Option<String> = None;
        let mut pid: Option<u32> = None;

        for line in output_str.lines() {
            if let Some(val) = line.strip_prefix("ActiveState=") {
                active_state = val.trim().to_lowercase();
            } else if let Some(val) = line.strip_prefix("Description=") {
                let v = val.trim().to_string();
                if !v.is_empty() {
                    description = Some(v);
                }
            } else if let Some(val) = line.strip_prefix("MainPID=") {
                pid = val.trim().parse::<u32>().ok().filter(|&p| p > 0);
            }
        }

        let status = match active_state.as_str() {
            "active" | "activating" | "reloading" => ServiceStatus::Active,
            "inactive" | "deactivating" | "failed" => {
                // Fallback: painéis como RunCloud iniciam processos fora do controle do systemd.
                // Se o systemd diz "inactive" mas o processo existe no sistema, reportamos como Active.
                let process_exists = Command::new("pgrep")
                    .args(["-x", service_name])
                    .status()
                    .map(|s| s.success())
                    .unwrap_or(false);
                if process_exists { ServiceStatus::Active } else { ServiceStatus::Inactive }
            }
            _ => ServiceStatus::Unknown,
        };

        Ok(ServiceInfo {
            name: service_name.to_string(),
            status,
            description,
            pid,
        })
    }

    /// Verifica múltiplos serviços
    pub fn check_multiple_services(service_names: &[&str]) -> Vec<ServiceInfo> {
        service_names
            .iter()
            .filter_map(|name| Self::check_service(name).ok())
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_service_status_parsing() {
        // Este teste precisaria de um ambiente com systemd rodando
        // Deixado como exemplo de como testar
        let result = ServiceMonitor::check_service("systemd-journald");
        if let Ok(info) = result {
            assert!(!info.name.is_empty());
            println!("Service {} is {:?}", info.name, info.status);
        }
    }
}
