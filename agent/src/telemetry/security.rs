use crate::error::Result;
use serde::{Deserialize, Serialize};
use std::process::Command;

/// Métricas de segurança
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityMetrics {
    pub active_ssh_sessions: usize,
    pub failed_login_attempts: usize,
    pub failed_logins: Vec<FailedLogin>,
    pub suspicious_activities: Vec<String>,
}

/// Detalhes de uma tentativa falha de login
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FailedLogin {
    pub ip: String,
    pub count: usize,
    pub last_attempt: String,
}

impl SecurityMetrics {
    pub fn collect() -> Result<Self> {
        let active_ssh_sessions = Self::count_ssh_sessions().unwrap_or(0);
        let mut failed_logins = Self::parse_failed_logins().unwrap_or_default();
        
        // Calcula o total de tentativas de invasão considerando TODOS os IPs
        let total_failed = failed_logins.iter().map(|f| f.count).sum();
        
        // Lista apenas os agressores insistentes (reduz ruído de IPs que já levaram block)
        failed_logins.retain(|f| f.count > 10);
        
        Ok(Self {
            active_ssh_sessions,
            failed_login_attempts: total_failed,
            failed_logins,
            suspicious_activities: Vec::new(),
        })
    }

    fn count_ssh_sessions() -> Option<usize> {
        let output = Command::new("who").output().ok()?;
        let stdout = String::from_utf8_lossy(&output.stdout);
        Some(stdout.lines().filter(|line| line.contains("pts/")).count())
    }

    fn parse_failed_logins() -> Option<Vec<FailedLogin>> {
        use std::collections::HashMap;
        
        // lastb -s "-15 minutes" -a mostra as tentativas recentes com IP e tempo
        let output = Command::new("lastb").arg("-s").arg("-15 minutes").arg("-a").output().ok()?;
        let stdout = String::from_utf8_lossy(&output.stdout);
        
        let mut ip_counts: HashMap<String, (usize, String)> = HashMap::new();
        
        for line in stdout.lines() {
            if line.trim().is_empty() || line.contains("btmp begins") || line.starts_with("USERNAME") {
                continue;
            }

            // O lastb -a coloca o hostname/IP no final da linha
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 3 {
                let ip = parts.last().unwrap_or(&"unknown").to_string();
                // Ignora localhost e IPs inválidos simples
                if ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "localhost" {
                    continue;
                }

                let entry = ip_counts.entry(ip).or_insert((0, parts.get(4..8).map(|s| s.join(" ")).unwrap_or_default()));
                entry.0 += 1;
            }
        }

        let mut result: Vec<FailedLogin> = ip_counts
            .into_iter()
            .map(|(ip, (count, last_attempt))| FailedLogin { ip, count, last_attempt })
            .collect();
            
        // Ordena por quantidade de ataques (decrescente)
        result.sort_by(|a, b| b.count.cmp(&a.count));
        
        Some(result)
    }

    /// Bloqueia um IP usando iptables (requer root)
    pub fn block_ip(ip: &str) -> Result<bool> {
        // Validação básica de IP para evitar command injection (embora usemos Command::new)
        if ip.contains(' ') || ip.contains(';') || ip.contains('&') {
            return Err(crate::error::AgentError::CommandError("Invalid IP format".to_string()));
        }

        let status = Command::new("iptables")
            .arg("-A")
            .arg("INPUT")
            .arg("-s")
            .arg(ip)
            .arg("-j")
            .arg("DROP")
            .status()
            .map_err(|e| crate::error::AgentError::CommandError(format!("Iptables failed: {}", e)))?;

        Ok(status.success())
    }
}
