use crate::error::{AgentError, Result};
use serde::{Deserialize, Serialize};
use std::process::Command;

/// Comandos de emergência pré-aprovados (whitelist)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EmergencyCommand {
    /// Identificador único
    pub id: String,
    /// Descrição do comando
    pub description: String,
    /// Comando a executar (apenas binários pré-aprovados)
    pub command: String,
    /// Argumentos (lista de strings, não permite shell injection)
    pub args: Vec<String>,
}

/// Resultado da execução de um comando
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResult {
    /// ID do comando executado
    pub command_id: String,
    /// Código de saída
    pub exit_code: i32,
    /// Saída padrão
    pub stdout: String,
    /// Saída de erro
    pub stderr: String,
    /// Timestamp de execução
    pub timestamp: i64,
}

/// Executor de comandos de emergência
pub struct CommandExecutor {
    /// Comandos pré-aprovados
    allowed_commands: Vec<EmergencyCommand>,
}

impl CommandExecutor {
    /// Cria um novo executor com comandos pré-aprovados
    pub fn new(allowed_commands: Vec<EmergencyCommand>) -> Self {
        Self { allowed_commands }
    }

    /// Lista todos os comandos disponíveis
    pub fn list_commands(&self) -> Vec<EmergencyCommand> {
        self.allowed_commands.clone()
    }

    /// Executa um comando de emergência por ID
    pub fn execute_command(&self, command_id: &str) -> Result<CommandResult> {
        let command = self
            .allowed_commands
            .iter()
            .find(|c| c.id == command_id)
            .ok_or_else(|| AgentError::CommandError(format!("Command '{}' not found", command_id)))?;

        let mut cmd = Command::new(&command.command);
        cmd.args(&command.args);

        let output = cmd
            .output()
            .map_err(|e| AgentError::CommandError(format!("Failed to execute command: {}", e)))?;

        let timestamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;

        Ok(CommandResult {
            command_id: command_id.to_string(),
            exit_code: output.status.code().unwrap_or(-1),
            stdout: String::from_utf8_lossy(&output.stdout).to_string(),
            stderr: String::from_utf8_lossy(&output.stderr).to_string(),
            timestamp,
        })
    }
}

/// Preset de comandos comuns de emergência
pub fn default_emergency_commands() -> Vec<EmergencyCommand> {
    vec![
        EmergencyCommand {
            id: "restart_nginx".to_string(),
            description: "Restart Nginx web server".to_string(),
            command: "systemctl".to_string(),
            args: vec!["restart".to_string(), "nginx".to_string()],
        },
        EmergencyCommand {
            id: "stop_nginx".to_string(),
            description: "Stop Nginx web server".to_string(),
            command: "systemctl".to_string(),
            args: vec!["stop".to_string(), "nginx".to_string()],
        },
        EmergencyCommand {
            id: "restart_docker".to_string(),
            description: "Restart Docker daemon".to_string(),
            command: "systemctl".to_string(),
            args: vec!["restart".to_string(), "docker".to_string()],
        },
        EmergencyCommand {
            id: "clear_logs".to_string(),
            description: "Clear systemd journal logs".to_string(),
            command: "journalctl".to_string(),
            args: vec!["--rotate".to_string()],
        },
        EmergencyCommand {
            id: "check_disk_space".to_string(),
            description: "Check disk space usage".to_string(),
            command: "df".to_string(),
            args: vec!["-h".to_string()],
        },
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_commands() {
        let commands = default_emergency_commands();
        assert!(!commands.is_empty());
        assert!(commands.iter().any(|c| c.id == "restart_nginx"));
    }

    #[test]
    fn test_command_executor_creation() {
        let commands = default_emergency_commands();
        let executor = CommandExecutor::new(commands.clone());
        assert_eq!(executor.list_commands().len(), commands.len());
    }

    #[test]
    fn test_command_not_found() {
        let executor = CommandExecutor::new(default_emergency_commands());
        let result = executor.execute_command("nonexistent_command");
        assert!(result.is_err());
    }
}
