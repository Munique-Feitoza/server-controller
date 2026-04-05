use serde::{Deserialize, Serialize};
use std::collections::VecDeque;

/// Entrada individual do log de auditoria.
/// Registra cada requisição HTTP processada pelo agente.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuditEntry {
    pub id: String,
    pub timestamp: String,
    pub action: String,
    pub source_ip: String,
    pub endpoint: String,
    pub method: String,
    pub status_code: u16,
    pub details: Option<String>,
}

/// Ring buffer de entradas de auditoria com capacidade fixa.
/// Similar ao WatchdogEventStore mas para ações da API.
pub struct AuditLog {
    entries: VecDeque<AuditEntry>,
    capacity: usize,
}

impl AuditLog {
    pub fn new(capacity: usize) -> Self {
        Self {
            entries: VecDeque::with_capacity(capacity),
            capacity,
        }
    }

    pub fn with_defaults() -> Self {
        Self::new(1000)
    }

    /// Registra uma nova entrada de auditoria.
    /// Se o buffer estiver cheio, remove a entrada mais antiga (FIFO).
    pub fn log(&mut self, entry: AuditEntry) {
        if self.entries.len() >= self.capacity {
            self.entries.pop_front();
        }
        self.entries.push_back(entry);
    }

    /// Cria e registra uma entrada a partir dos dados da requisição.
    pub fn record(
        &mut self,
        method: &str,
        endpoint: &str,
        source_ip: &str,
        status_code: u16,
        details: Option<String>,
    ) {
        let entry = AuditEntry {
            id: uuid::Uuid::new_v4().to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            action: format!("{} {}", method, endpoint),
            source_ip: source_ip.to_string(),
            endpoint: endpoint.to_string(),
            method: method.to_string(),
            status_code,
            details,
        };
        self.log(entry);
    }

    /// Retorna as N entradas mais recentes.
    pub fn recent(&self, limit: usize) -> Vec<&AuditEntry> {
        self.entries.iter().rev().take(limit).collect()
    }

    /// Filtra entradas por ação (substring match).
    pub fn by_action(&self, action: &str) -> Vec<&AuditEntry> {
        self.entries
            .iter()
            .filter(|e| e.action.contains(action) || e.endpoint.contains(action))
            .collect()
    }

    /// Limpa todo o histórico de auditoria.
    pub fn clear(&mut self) {
        self.entries.clear();
    }

    /// Número total de entradas no log.
    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
}
