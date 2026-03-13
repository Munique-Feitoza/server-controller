use crate::error::Result;
use serde::{Deserialize, Serialize};
use std::process::Command;

/// Métricas de segurança
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityMetrics {
    pub active_ssh_sessions: usize,
    pub suspicious_activities: Vec<String>,
}

impl SecurityMetrics {
    pub fn collect() -> Result<Self> {
        let active_ssh_sessions = Self::count_ssh_sessions().unwrap_or(0);
        
        Ok(Self {
            active_ssh_sessions,
            suspicious_activities: Vec::new(), // To be implemented with deeper log analysis
        })
    }

    fn count_ssh_sessions() -> Option<usize> {
        let output = Command::new("who").output().ok()?;
        let stdout = String::from_utf8_lossy(&output.stdout);
        Some(stdout.lines().filter(|line| line.contains("pts/")).count())
    }
}
