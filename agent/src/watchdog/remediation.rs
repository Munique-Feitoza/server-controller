use std::collections::HashMap;
use std::process::Command;
use serde::{Deserialize, Serialize};
use crate::watchdog::circuit_breaker::CircuitBreaker;

// ─────────────────────────────────────────────────────────────────────────────
// AÇÕES DE REMEDIAÇÃO DISPONÍVEIS
// Cada variante representa um comando concreto executado no sistema operacional.
// ─────────────────────────────────────────────────────────────────────────────

/// Ações que o Watchdog pode executar autonomamente
///
/// # Design Pattern: Command
/// Encapsulamos cada operação de sistema como uma variante tipada de enum.
/// Isso permite que o Kotlin mostre a ação de forma legível sem parsear strings brutas.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "PascalCase")]
pub enum RemediationAction {
    /// `systemctl restart <service>` — reinicia o daemon
    RestartService(String),
    /// `systemctl reload <service>` — recarrega config sem downtime
    ReloadConfig(String),
    /// Mata o processo pelo PID (para processos zumbi travados)
    KillProcess(u32),
    /// Limpa arquivos temporários do agente
    ClearTmpFiles,
    /// Nenhuma ação automática disponível — escala para humano
    EscalateToHuman,
}

impl std::fmt::Display for RemediationAction {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            RemediationAction::RestartService(s)  => write!(f, "RestartService({})", s),
            RemediationAction::ReloadConfig(s)    => write!(f, "ReloadConfig({})", s),
            RemediationAction::KillProcess(pid)   => write!(f, "KillProcess({})", pid),
            RemediationAction::ClearTmpFiles      => write!(f, "ClearTmpFiles"),
            RemediationAction::EscalateToHuman    => write!(f, "EscalateToHuman"),
        }
    }
}

/// Status final após a tentativa de remediação
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "PascalCase")]
pub enum RemediationStatus {
    /// Ação executada e serviço voltou ao normal
    Success,
    /// Ação executada, mas serviço ainda não recuperou
    Failed,
    /// Circuit Breaker bloqueou — limite de tentativas atingido
    CircuitOpen,
    /// Nenhuma ação foi necessária (probe voltou a ser saudável)
    NotNeeded,
}

impl std::fmt::Display for RemediationStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            RemediationStatus::Success      => write!(f, "Success"),
            RemediationStatus::Failed       => write!(f, "Failed"),
            RemediationStatus::CircuitOpen  => write!(f, "CircuitOpen"),
            RemediationStatus::NotNeeded    => write!(f, "NotNeeded"),
        }
    }
}

/// Resultado completo de uma tentativa de remediação
#[derive(Debug, Clone)]
pub struct RemediationResult {
    pub action:    RemediationAction,
    pub status:    RemediationStatus,
    pub attempts:  u32,
    pub circuit_open: bool,
    pub message:   String,
}

// ─────────────────────────────────────────────────────────────────────────────
// REMEDIATION ENGINE
// Motor central que mapeia serviços → ações e mantém o Circuit Breaker de cada um.
// ─────────────────────────────────────────────────────────────────────────────

/// Motor de remediação — um Circuit Breaker por serviço vigiado
///
/// # Bounded Context (Clean Architecture)
/// O RemediationEngine encapsula toda a lógica de "o que fazer quando algo falha".
/// O WatchdogEngine apenas chama `execute(service, action)` sem saber dos detalhes.
pub struct RemediationEngine {
    /// HashMap<service_name, CircuitBreaker>
    breakers: HashMap<String, CircuitBreaker>,
    /// Máximo de falhas antes de abrir o circuito (configurável via .env)
    max_failures: u32,
    /// Cooldown em segundos após abrir o circuito (configurável via .env)
    cooldown_secs: u64,
}

impl RemediationEngine {
    /// Cria um novo engine com os parâmetros do Circuit Breaker
    pub fn new(max_failures: u32, cooldown_secs: u64) -> Self {
        Self {
            breakers: HashMap::new(),
            max_failures,
            cooldown_secs,
        }
    }

    /// Cria com os valores padrão (3 tentativas, 5 min cooldown)
    pub fn with_defaults() -> Self {
        Self::new(3, 300)
    }

    /// Retorna (criando se necessário) o Circuit Breaker de um serviço
    fn breaker_for(&mut self, service: &str) -> &mut CircuitBreaker {
        self.breakers
            .entry(service.to_string())
            .or_insert_with(|| CircuitBreaker::new(self.max_failures, self.cooldown_secs))
    }

    /// Executa a ação de remediação para um serviço, respeitando o Circuit Breaker
    ///
    /// # Segurança
    /// Todos os comandos são executados via `Command::new` com argumentos separados.
    /// NUNCA concatenamos strings e passamos para o shell (`sh -c`).
    /// Isso previne command injection mesmo que o nome do serviço venha de uma config.
    pub fn execute(&mut self, service: &str, action: RemediationAction) -> RemediationResult {
        let breaker = self.breaker_for(service);

        // 1. Circuit Breaker check — não tenta se o circuito estiver aberto
        if !breaker.can_attempt() {
            let remaining = breaker.remaining_cooldown_secs().unwrap_or(0);
            tracing::warn!(
                "🔴 [{}] Circuit Breaker ABERTO — remediação bloqueada. \
                 Cooldown restante: {}s",
                service, remaining
            );
            return RemediationResult {
                action:       action.clone(),
                status:       RemediationStatus::CircuitOpen,
                attempts:     breaker.failure_count(),
                circuit_open: true,
                message:      format!(
                    "[{}] Remediação bloqueada pelo Circuit Breaker. \
                     Intervenção humana necessária. Cooldown: {}s",
                    service, remaining
                ),
            };
        }

        let attempts_before = breaker.failure_count();

        // 2. Executa a ação no sistema operacional
        let success = self.execute_action(&action);

        // 3. Atualiza o Circuit Breaker
        if success {
            self.breaker_for(service).record_success();
        } else {
            self.breaker_for(service).record_failure();
        }

        let breaker = self.breaker_for(service);
        let circuit_open = breaker.is_open();
        let attempts = attempts_before + 1;

        let (status, message) = if success {
            (
                RemediationStatus::Success,
                format!("[{}] {} executado com sucesso", service, action),
            )
        } else if circuit_open {
            (
                RemediationStatus::CircuitOpen,
                format!(
                    "[{}] {} falhou ({} tentativas). Circuit Breaker ABERTO — \
                     REQUER INTERVENÇÃO HUMANA.",
                    service, action, attempts
                ),
            )
        } else {
            (
                RemediationStatus::Failed,
                format!(
                    "[{}] {} falhou (tentativa {}/{})",
                    service, action, attempts, self.max_failures
                ),
            )
        };

        tracing::info!("[Remediation] {}", message);

        RemediationResult {
            action,
            status,
            attempts,
            circuit_open,
            message,
        }
    }

    /// Despacha a execução concreta baseado no enum de ação
    fn execute_action(&self, action: &RemediationAction) -> bool {
        match action {
            RemediationAction::RestartService(service) => {
                tracing::info!("🔧 Reiniciando serviço: {}", service);
                Command::new("systemctl")
                    .arg("restart")
                    .arg(service)
                    .status()
                    .map(|s| s.success())
                    .unwrap_or(false)
            }

            RemediationAction::ReloadConfig(service) => {
                tracing::info!("🔄 Recarregando config de: {}", service);
                Command::new("systemctl")
                    .arg("reload")
                    .arg(service)
                    .status()
                    .map(|s| s.success())
                    .unwrap_or(false)
            }

            RemediationAction::KillProcess(pid) => {
                tracing::info!("💀 Matando processo PID: {}", pid);
                Command::new("kill")
                    .arg("-9")
                    .arg(pid.to_string())
                    .status()
                    .map(|s| s.success())
                    .unwrap_or(false)
            }

            RemediationAction::ClearTmpFiles => {
                tracing::info!("🗑️  Limpando arquivos temporários do agente");
                Command::new("find")
                    .args(["/tmp", "-name", "pocket-noc-*", "-delete"])
                    .status()
                    .map(|s| s.success())
                    .unwrap_or(false)
            }

            RemediationAction::EscalateToHuman => {
                // Não executa nada — apenas sinaliza no resultado
                tracing::warn!("🆘 Escalonando para intervenção humana!");
                false
            }
        }
    }

    /// Retorna o mapa de action recomendada dado um nome de serviço
    /// (usado pelo WatchdogEngine para decidir o que fazer quando um probe falha)
    pub fn recommended_action(service: &str) -> RemediationAction {
        match service {
            // Serviços web — restart direto
            s if s.contains("nginx")       => RemediationAction::RestartService(s.to_string()),
            s if s.contains("apache")      => RemediationAction::RestartService(s.to_string()),
            s if s.contains("php-fpm")     => RemediationAction::RestartService(s.to_string()),
            s if s.contains("php8")        => RemediationAction::RestartService(s.to_string()),
            // ERP/backends — restart
            s if s.contains("gunicorn")    => RemediationAction::RestartService(s.to_string()),
            s if s.contains("uvicorn")     => RemediationAction::RestartService(s.to_string()),
            // Bancos de dados — CUIDADO: restart de BD pode corromper dados
            // Usamos reload primeiro, escalamos se falhar
            s if s.contains("mysql")       => RemediationAction::ReloadConfig(s.to_string()),
            s if s.contains("postgresql")  => RemediationAction::ReloadConfig(s.to_string()),
            s if s.contains("postgres")    => RemediationAction::ReloadConfig(s.to_string()),
            // Probes TCP/HTTP — não têm ação direta de systemctl
            s if s.ends_with("-tcp")       => RemediationAction::EscalateToHuman,
            s if s.ends_with("-http")      => RemediationAction::EscalateToHuman,
            // Fallback genérico
            s                              => RemediationAction::RestartService(s.to_string()),
        }
    }

    /// Retorna o estado atual de todos os circuit breakers (para debug e dashboard)
    pub fn circuit_states(&self) -> HashMap<String, String> {
        self.breakers
            .iter()
            .map(|(k, v)| (k.clone(), v.state().to_string()))
            .collect()
    }
}
