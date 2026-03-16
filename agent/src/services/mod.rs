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
    /// Verifica o status de um serviço
    pub fn check_service(service_name: &str) -> Result<ServiceInfo> {
        let status = Self::get_service_status(service_name)?;

        Ok(ServiceInfo {
            name: service_name.to_string(),
            status,
            description: Self::get_service_description(service_name).ok(),
            pid: Self::get_service_pid(service_name).ok(),
        })
    }

    /// Obtém o status de um serviço via systemctl
    fn get_service_status(service_name: &str) -> Result<ServiceStatus> {
        // Usa 'systemctl show' que é mais robusto que 'is-active'
        let output = Command::new("systemctl")
            .arg("show")
            .arg("-p")
            .arg("ActiveState")
            .arg("--value")
            .arg(service_name)
            .output()
            .map_err(|e| {
                AgentError::ServiceError(format!("Failed to check service status: {}", e))
            })?;

        let status_str = String::from_utf8_lossy(&output.stdout).trim().to_lowercase();

        Ok(match status_str.as_str() {
            // Estados que indicam que o serviço está realmente ativo
            "active" | "activating" | "reloading" => ServiceStatus::Active,
            // Estados que indicam inatividade
            "inactive" | "deactivating" | "failed" => ServiceStatus::Inactive,
            _ => ServiceStatus::Unknown,
        })
    }

    /// Obtém a descrição de um serviço
    fn get_service_description(service_name: &str) -> Result<String> {
        let output = Command::new("systemctl")
            .arg("show")
            .arg("-p")
            .arg("Description")
            .arg("--value")
            .arg(service_name)
            .output()
            .map_err(|e| {
                AgentError::ServiceError(format!("Failed to get service description: {}", e))
            })?;

        let description = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if description.is_empty() {
            Err(AgentError::ServiceError("No description available".to_string()))
        } else {
            Ok(description)
        }
    }

    /// Obtém o PID de um serviço ativo
    fn get_service_pid(service_name: &str) -> Result<u32> {
        let output = Command::new("systemctl")
            .arg("show")
            .arg("-p")
            .arg("MainPID")
            .arg("--value")
            .arg(service_name)
            .output()
            .map_err(|e| {
                AgentError::ServiceError(format!("Failed to get service PID: {}", e))
            })?;

        let pid_str_owned = String::from_utf8_lossy(&output.stdout).to_string();
        let pid_str = pid_str_owned.trim();
        pid_str.parse::<u32>()
            .ok()
            .and_then(|pid| if pid > 0 { Some(pid) } else { None })
            .ok_or_else(|| AgentError::ServiceError("No PID available".to_string()))
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
