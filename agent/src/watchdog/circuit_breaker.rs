use std::time::{Duration, Instant};
use serde::{Deserialize, Serialize};

/// Estado interno da máquina de estados do Circuit Breaker
///
/// # Pattern: State Machine & Performance (Big-O)
/// Closed → Open (após N falhas)
/// Open    → HalfOpen (após cooldown)
/// HalfOpen → Closed (após 1 sucesso) | Open (após 1 nova falha)
///
/// - **Complexidade Algorítmica:** As transições de estado operam em tempo `O(1)` constante stricto sensu, sem iterações de laço.
/// - **Gestão de Memória:** Implementado com *Zero-cost abstractions*. Enumeradores em Rust não alocam 
///   no Heap (são guardados na Stack), prevenindo completamente cenários de *Memory Leak* ou latência de Garbage Collection.
///
/// Isso é um dos padrões de resiliência mais importantes em sistemas distribuídos.
/// Referência: "Release It!" de Michael T. Nygard.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum CircuitState {
    /// Circuito fechado — tentativas de remediação permitidas
    Closed,
    /// Circuito aberto — PAROU de tentar (aguardando cooldown)
    Open,
    /// Circuito semi-aberto — permite 1 tentativa de diagnóstico
    HalfOpen,
}

impl std::fmt::Display for CircuitState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CircuitState::Closed   => write!(f, "Closed"),
            CircuitState::Open     => write!(f, "Open"),
            CircuitState::HalfOpen => write!(f, "HalfOpen"),
        }
    }
}

/// Circuit Breaker com máquina de estados explícita
///
/// Cada serviço vigiado pelo Watchdog tem seu próprio `CircuitBreaker`.
/// Isso evita que uma falha em um serviço interfira na lógica de outro.
/// (um CircuitBreaker por entrada no HashMap<String, CircuitBreaker> do RemediationEngine)
#[derive(Debug)]
pub struct CircuitBreaker {
    /// Máximo de falhas consecutivas antes de abrir o circuito
    pub max_failures:   u32,
    /// Quanto tempo esperar antes de tentar novamente (HalfOpen) após abrir
    pub cooldown:       Duration,
    /// Falhas consecutivas acumuladas (zerado no sucesso)
    failure_count:      u32,
    /// Estado atual da máquina de estados
    state:              CircuitState,
    /// Quando o circuito foi aberto pela última vez
    last_opened_at:     Option<Instant>,
}

impl CircuitBreaker {
    /// Cria um novo CircuitBreaker com configurações customizadas
    pub fn new(max_failures: u32, cooldown_secs: u64) -> Self {
        Self {
            max_failures,
            cooldown: Duration::from_secs(cooldown_secs),
            failure_count: 0,
            state: CircuitState::Closed,
            last_opened_at: None,
        }
    }

    /// Cria com os valores padrão (3 tentativas, 5 minutos de cooldown)
    pub fn with_defaults() -> Self {
        Self::new(3, 300)
    }

    /// Retorna se o circuito permite uma nova tentativa de remediação
    ///
    /// Também faz a transição Open → HalfOpen automaticamente após o cooldown.
    pub fn can_attempt(&mut self) -> bool {
        match self.state {
            CircuitState::Closed   => true,
            CircuitState::HalfOpen => true,  // Permite 1 tentativa de sondagem
            CircuitState::Open => {
                // Verifica se o cooldown expirou
                if let Some(opened_at) = self.last_opened_at {
                    if opened_at.elapsed() >= self.cooldown {
                        // Transição Open → HalfOpen: permite 1 tentativa
                        self.state = CircuitState::HalfOpen;
                        tracing::info!(
                            "⚡ Circuit Breaker transitou Open → HalfOpen (cooldown expirado)"
                        );
                        return true;
                    }
                }
                false  // Cooldown ainda em andamento
            }
        }
    }

    /// Registra uma FALHA na tentativa de remediação
    ///
    /// Se atingir `max_failures`, abre o circuito (Closed/HalfOpen → Open).
    pub fn record_failure(&mut self) {
        self.failure_count += 1;
        match self.state {
            CircuitState::Closed | CircuitState::HalfOpen => {
                if self.failure_count >= self.max_failures {
                    self.state = CircuitState::Open;
                    self.last_opened_at = Some(Instant::now());
                    tracing::warn!(
                        "🔴 Circuit Breaker ABERTO após {} falhas consecutivas. \
                         Próxima tentativa em {}s.",
                        self.failure_count,
                        self.cooldown.as_secs()
                    );
                }
            }
            CircuitState::Open => {
                // Já está aberto — não faz nada (protege o servidor)
            }
        }
    }

    /// Registra um SUCESSO na tentativa de remediação
    ///
    /// Fecha o circuito e zera o contador de falhas.
    pub fn record_success(&mut self) {
        if self.state != CircuitState::Closed {
            tracing::info!(
                "✅ Circuit Breaker fechado após sucesso (era: {})",
                self.state
            );
        }
        self.failure_count = 0;
        self.state = CircuitState::Closed;
        self.last_opened_at = None;
    }

    /// Retorna o estado atual (para logging e serialização no WatchdogEvent)
    pub fn state(&self) -> &CircuitState {
        &self.state
    }

    /// Retorna o número de falhas consecutivas acumuladas
    pub fn failure_count(&self) -> u32 {
        self.failure_count
    }

    /// Retorna true se o circuito está aberto (remediação bloqueada)
    pub fn is_open(&self) -> bool {
        self.state == CircuitState::Open
    }

    /// Retorna tempo restante de cooldown (se o circuito estiver aberto)
    pub fn remaining_cooldown_secs(&self) -> Option<u64> {
        if self.state == CircuitState::Open {
            if let Some(opened_at) = self.last_opened_at {
                let elapsed = opened_at.elapsed();
                if elapsed < self.cooldown {
                    return Some((self.cooldown - elapsed).as_secs());
                }
            }
        }
        None
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TESTES UNITÁRIOS — Cobertura da máquina de estados
// ─────────────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_circuit_starts_closed() {
        let cb = CircuitBreaker::with_defaults();
        assert_eq!(cb.state(), &CircuitState::Closed);
        assert_eq!(cb.failure_count(), 0);
    }

    #[test]
    fn test_circuit_opens_after_max_failures() {
        let mut cb = CircuitBreaker::new(3, 300);

        assert!(cb.can_attempt(), "Deve permitir tentativa inicial");
        cb.record_failure();
        assert_eq!(cb.state(), &CircuitState::Closed, "1 falha não abre ainda");

        cb.record_failure();
        assert_eq!(cb.state(), &CircuitState::Closed, "2 falhas não abre ainda");

        cb.record_failure(); // 3ª falha — deve abrir!
        assert_eq!(cb.state(), &CircuitState::Open, "3 falhas devem abrir o circuito");
        assert!(!cb.can_attempt(), "Circuito aberto — não deve permitir tentativa");
    }

    #[test]
    fn test_circuit_half_open_after_zero_cooldown() {
        // Simula cooldown zero para testar a transição imediata Open → HalfOpen
        let mut cb = CircuitBreaker::new(1, 0);
        cb.record_failure();
        assert_eq!(cb.state(), &CircuitState::Open);

        // Com cooldown=0, a próxima chamada a can_attempt() deve transitar para HalfOpen
        assert!(cb.can_attempt(), "Cooldown=0 deve permitir HalfOpen imediatamente");
        assert_eq!(cb.state(), &CircuitState::HalfOpen);
    }

    #[test]
    fn test_circuit_resets_on_success() {
        let mut cb = CircuitBreaker::new(2, 0);
        cb.record_failure();
        cb.record_failure(); // Abre
        assert_eq!(cb.state(), &CircuitState::Open);

        // After cooldown=0, HalfOpen
        cb.can_attempt();
        assert_eq!(cb.state(), &CircuitState::HalfOpen);

        // Sucesso na tentativa HalfOpen deve fechar o circuito
        cb.record_success();
        assert_eq!(cb.state(), &CircuitState::Closed);
        assert_eq!(cb.failure_count(), 0, "Contador de falhas deve zerar após sucesso");
    }

    #[test]
    fn test_circuit_stays_open_during_cooldown() {
        let mut cb = CircuitBreaker::new(1, 999); // 999s de cooldown
        cb.record_failure();
        assert_eq!(cb.state(), &CircuitState::Open);
        assert!(!cb.can_attempt(), "Deve bloquear — cooldown ainda em andamento");
    }
}
