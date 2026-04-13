use crate::watchdog::circuit_breaker::CircuitBreaker;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::process::Command;

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
            RemediationAction::RestartService(s) => write!(f, "RestartService({})", s),
            RemediationAction::ReloadConfig(s) => write!(f, "ReloadConfig({})", s),
            RemediationAction::KillProcess(pid) => write!(f, "KillProcess({})", pid),
            RemediationAction::ClearTmpFiles => write!(f, "ClearTmpFiles"),
            RemediationAction::EscalateToHuman => write!(f, "EscalateToHuman"),
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
            RemediationStatus::Success => write!(f, "Success"),
            RemediationStatus::Failed => write!(f, "Failed"),
            RemediationStatus::CircuitOpen => write!(f, "CircuitOpen"),
            RemediationStatus::NotNeeded => write!(f, "NotNeeded"),
        }
    }
}

/// Resultado completo de uma tentativa de remediação
#[derive(Debug, Clone)]
pub struct RemediationResult {
    pub action: RemediationAction,
    pub status: RemediationStatus,
    pub attempts: u32,
    pub circuit_open: bool,
    pub just_opened: bool, // Novo: indica se o circuito ACABOU de abrir
    pub message: String,
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
                service,
                remaining
            );
            return RemediationResult {
                action: action.clone(),
                status: RemediationStatus::CircuitOpen,
                attempts: breaker.failure_count(),
                circuit_open: true,
                just_opened: false, // Já estava aberto em ciclos anteriores
                message: format!(
                    "[{}] Ação '{}' BLOQUEADA pelo Circuit Breaker (Aberto). Cooldown: {}s",
                    service, action, remaining
                ),
            };
        }

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
        let attempts = breaker.failure_count();

        // Determina se o circuito se abriu NESTA tentativa
        let just_opened = circuit_open && (attempts == self.max_failures);

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
            just_opened,
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
        // 1. Normalização inteligente: extrai o nome base do serviço
        // Remove sufixos comuns de probes (ex: "nginx-http-80" -> "nginx")
        let base_name = service
            .split("-http")
            .next()
            .unwrap()
            .split("-tcp")
            .next()
            .unwrap()
            .to_string();

        match base_name.as_str() {
            s if s.contains("nginx") => RemediationAction::RestartService("nginx".to_string()),
            s if s.contains("apache") => RemediationAction::RestartService("apache2".to_string()),
            "mariadb" | "mysql" => RemediationAction::RestartService("mariadb".to_string()),
            "postgresql" | "postgres" => {
                if service.contains("@") {
                    RemediationAction::RestartService(service.to_string())
                } else {
                    RemediationAction::RestartService("postgresql".to_string())
                }
            }

            // PHP dinâmico (phpX.Y-fpm)
            s if s.starts_with("php") => {
                let version = s.chars().filter(|c| c.is_ascii_digit()).collect::<String>();
                if !version.is_empty() {
                    RemediationAction::RestartService(format!("php{}-fpm", version))
                } else {
                    RemediationAction::RestartService("php81-fpm".to_string())
                }
            }

            // ERP / Python Stacks
            "gunicorn" | "uvicorn" | "python" | "python-api" => {
                RemediationAction::RestartService("gunicorn".to_string())
            }

            // Nodes & Frontends
            "nextjs" | "node" | "npm" => RemediationAction::RestartService("nextjs".to_string()),

            // Fallback genérico: se o nome base parecer um serviço systemd, tenta ele.
            // Probes TCP/HTTP puras que não casarem com os nomes acima caem aqui.
            s if s.len() > 1 && !s.contains('.') => {
                RemediationAction::RestartService(s.to_string())
            }

            _ => RemediationAction::EscalateToHuman,
        }
    }

    /// Registra sucesso para um serviço (fecha o Circuit Breaker)
    /// Chamado pelo WatchdogEngine quando o probe volta a ser Healthy
    pub fn record_success(&mut self, service: &str) {
        self.breaker_for(service).record_success();
    }

    /// Reseta todos os circuit breakers (Reset Manual)
    pub fn reset_all(&mut self) {
        for breaker in self.breakers.values_mut() {
            breaker.record_success();
        }
        tracing::info!("♻️ Todos os Circuit Breakers foram resetados manualmente.");
    }

    /// Retorna o estado atual de todos os circuit breakers (para debug e dashboard)
    pub fn circuit_states(&self) -> Vec<serde_json::Value> {
        self.breakers
            .iter()
            .map(|(k, v)| {
                serde_json::json!({
                    "service": k,
                    "state": v.state().to_string(),
                    "failure_count": v.failure_count(),
                    "is_open": v.is_open(),
                    "remaining_cooldown": v.remaining_cooldown_secs()
                })
            })
            .collect()
    }
}
