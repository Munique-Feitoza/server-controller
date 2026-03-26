use std::collections::VecDeque;
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use chrono::Utc;
use crate::watchdog::probes::ProbeStatus;
use crate::watchdog::remediation::{RemediationAction, RemediationStatus};

/// Evento estruturado gerado pelo Watchdog para cada probe falha + ação tomada.
///
/// Este struct é o "contrato de dados" entre o Rust e o Kotlin.
/// Todo o payload é serializável como JSON para ser enviado via REST ou WebSocket.
///
/// # Campo `server_id`
/// CRÍTICO para multi-servidor: identifica univocamente qual instância do agente
/// gerou o evento. Lido da env var `SERVER_ID` na inicialização.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WatchdogEvent {
    /// UUID v4 único por evento
    pub id: String,

    /// Timestamp Unix em segundos (UTC)
    pub timestamp: i64,

    /// ISO 8601 para facilitar parsing no Kotlin (ex: "2026-03-21T15:00:00Z")
    pub timestamp_iso: String,

    // ─── Identidade do servidor ───────────────────────────────────────────────
    /// Identificador curto do servidor (ex: "vps-deploy-01", "erp-server")
    /// Lido da env var `SERVER_ID`
    pub server_id: String,

    /// Função/papel do servidor (ex: "wordpress", "erp", "database", "generic")
    /// Lido da env var `SERVER_ROLE`
    pub server_role: String,

    /// Hostname real da máquina (resultado de `hostname`)
    pub server_hostname: String,

    // ─── Diagnóstico do probe ─────────────────────────────────────────────────
    /// Nome do serviço que falhou (ex: "nginx", "mysql", "nextjs-frontend")
    pub service: String,

    /// Status retornado pelo probe (Healthy / Degraded / Down)
    pub probe_result: String,

    /// Latência medida pelo probe em ms (None para probes sem latência)
    pub probe_latency_ms: Option<u64>,

    // ─── Remediação ───────────────────────────────────────────────────────────
    /// Ação executada (ex: "RestartService(nginx)")
    pub action_taken: String,

    /// Status final da remediação
    pub final_status: String,

    /// Quantas tentativas foram feitas até este evento
    pub attempts: u32,

    /// True se o Circuit Breaker está aberto (requer intervenção humana)
    pub circuit_open: bool,

    // ─── Mensagem legível ────────────────────────────────────────────────────
    /// Mensagem humana completa para exibição no app
    /// Ex: "[erp-server] gunicorn Down às 03:00, reiniciado com sucesso"
    pub message: String,
}

impl WatchdogEvent {
    /// Cria um novo evento com todos os campos obrigatórios
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        server_id:       &str,
        server_role:     &str,
        server_hostname: &str,
        service:         &str,
        probe_status:    &ProbeStatus,
        probe_latency:   Option<u64>,
        action:          &RemediationAction,
        rem_status:      &RemediationStatus,
        attempts:        u32,
        circuit_open:    bool,
        message:         String,
    ) -> Self {
        let now = Utc::now();
        Self {
            id:              Uuid::new_v4().to_string(),
            timestamp:       now.timestamp(),
            timestamp_iso:   now.format("%Y-%m-%dT%H:%M:%SZ").to_string(),
            server_id:       server_id.to_string(),
            server_role:     server_role.to_string(),
            server_hostname: server_hostname.to_string(),
            service:         service.to_string(),
            probe_result:    probe_status.to_string(),
            probe_latency_ms: probe_latency,
            action_taken:    action.to_string(),
            final_status:    rem_status.to_string(),
            attempts,
            circuit_open,
            message,
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WATCHDOG EVENT STORE
// Ring buffer em memória: mantém os últimos N eventos sem crescer indefinidamente.
//
// # Pattern: Bounded Buffer
// VecDeque + capacidade máxima: O(1) insert, O(n) busca.
// Para >500 eventos, usaríamos SQLite. Para o escopo atual, é perfeito.
// ─────────────────────────────────────────────────────────────────────────────

/// Store em memória para os eventos do Watchdog (ring buffer)
pub struct WatchdogEventStore {
    events:   VecDeque<WatchdogEvent>,
    capacity: usize,
}

impl WatchdogEventStore {
    /// Cria um store com capacidade máxima de `capacity` eventos
    pub fn new(capacity: usize) -> Self {
        Self {
            events:   VecDeque::with_capacity(capacity),
            capacity,
        }
    }

    /// Cria com capacidade padrão de 500 eventos
    pub fn with_defaults() -> Self {
        Self::new(500)
    }

    /// Insere um evento. Se o buffer estiver cheio, descarta o mais antigo.
    pub fn push(&mut self, event: WatchdogEvent) {
        if self.events.len() >= self.capacity {
            self.events.pop_front(); // Remove o evento mais antigo (FIFO)
        }
        self.events.push_back(event);
    }

    /// Retorna os N eventos mais recentes (para o endpoint `/watchdog/events`)
    pub fn recent(&self, limit: usize) -> Vec<&WatchdogEvent> {
        self.events.iter().rev().take(limit).collect()
    }

    /// Retorna todos os eventos como Vec clonado (para serialização JSON)
    pub fn all_as_vec(&self) -> Vec<WatchdogEvent> {
        self.events.iter().rev().cloned().collect()
    }

    /// Filtra eventos por `server_id` (multi-servidor: mostra só o servidor selecionado)
    pub fn by_server(&self, server_id: &str) -> Vec<&WatchdogEvent> {
        self.events
            .iter()
            .rev()
            .filter(|e| e.server_id == server_id)
            .collect()
    }

    /// Filtra eventos por status (ex: "CircuitOpen" para mostrar só alertas críticos)
    pub fn by_status(&self, status: &str) -> Vec<&WatchdogEvent> {
        self.events
            .iter()
            .rev()
            .filter(|e| e.final_status == status)
            .collect()
    }

    /// Retorna contagem de eventos por servidor (para o dashboard do app)
    pub fn count_by_server(&self) -> std::collections::HashMap<String, usize> {
        let mut counts = std::collections::HashMap::new();
        for event in &self.events {
            *counts.entry(event.server_id.clone()).or_insert(0) += 1;
        }
        counts
    }

    pub fn len(&self) -> usize {
        self.events.len()
    }

    /// Limpa todos os eventos do store
    pub fn clear(&mut self) {
        self.events.clear();
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_event(server_id: &str, status: &str) -> WatchdogEvent {
        WatchdogEvent {
            id:               Uuid::new_v4().to_string(),
            timestamp:        0,
            timestamp_iso:    "2026-03-21T00:00:00Z".to_string(),
            server_id:        server_id.to_string(),
            server_role:      "wordpress".to_string(),
            server_hostname:  "test-host".to_string(),
            service:          "nginx".to_string(),
            probe_result:     "Down".to_string(),
            probe_latency_ms: None,
            action_taken:     "RestartService(nginx)".to_string(),
            final_status:     status.to_string(),
            attempts:         1,
            circuit_open:     false,
            message:          format!("[{}] test event", server_id),
        }
    }

    #[test]
    fn test_store_respects_capacity() {
        let mut store = WatchdogEventStore::new(3);
        store.push(make_event("server-a", "Success"));
        store.push(make_event("server-a", "Success"));
        store.push(make_event("server-a", "Success"));
        store.push(make_event("server-a", "Failed")); // Deve descartar o mais antigo
        assert_eq!(store.len(), 3);
    }

    #[test]
    fn test_filter_by_server() {
        let mut store = WatchdogEventStore::new(100);
        store.push(make_event("server-a", "Success"));
        store.push(make_event("server-b", "Failed"));
        store.push(make_event("server-a", "CircuitOpen"));

        let server_a = store.by_server("server-a");
        assert_eq!(server_a.len(), 2);
        let server_b = store.by_server("server-b");
        assert_eq!(server_b.len(), 1);
    }

    #[test]
    fn test_recent_returns_most_recent_first() {
        let mut store = WatchdogEventStore::new(100);
        store.push(make_event("server-a", "Success"));
        store.push(make_event("server-a", "Failed")); // Mais recente

        let recent = store.recent(1);
        assert_eq!(recent[0].final_status, "Failed");
    }
}
