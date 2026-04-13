use serde::{Deserialize, Serialize};
use std::collections::VecDeque;

/// Incidente de seguranca recebido do Dashboard ERP ou detectado localmente.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityIncident {
    pub id: String,
    pub timestamp: String,
    pub source: String,
    pub severity: String,
    pub event_type: String,
    pub attacker_ip: String,
    pub country: Option<String>,
    pub city: Option<String>,
    pub isp: Option<String>,
    pub details: Option<String>,
    /// true = veio do dashboard ERP, false = detectado localmente pelo agente
    pub from_webhook: bool,
}

/// Ring buffer de incidentes de seguranca (maximo 200 entradas)
pub struct IncidentStore {
    incidents: VecDeque<SecurityIncident>,
    capacity: usize,
}

impl Default for IncidentStore {
    fn default() -> Self {
        Self::new()
    }
}

impl IncidentStore {
    pub fn new() -> Self {
        Self {
            incidents: VecDeque::with_capacity(200),
            capacity: 200,
        }
    }

    pub fn push(&mut self, incident: SecurityIncident) {
        if self.incidents.len() >= self.capacity {
            self.incidents.pop_front();
        }
        self.incidents.push_back(incident);
    }

    pub fn recent(&self, limit: usize) -> Vec<&SecurityIncident> {
        self.incidents.iter().rev().take(limit).collect()
    }

    pub fn by_severity(&self, severity: &str) -> Vec<&SecurityIncident> {
        self.incidents
            .iter()
            .filter(|i| i.severity == severity)
            .collect()
    }

    pub fn count_critical(&self) -> usize {
        self.incidents
            .iter()
            .filter(|i| i.severity == "CRITICAL" || i.severity == "critical")
            .count()
    }

    pub fn len(&self) -> usize {
        self.incidents.len()
    }

    pub fn is_empty(&self) -> bool {
        self.incidents.is_empty()
    }

    pub fn clear(&mut self) {
        self.incidents.clear();
    }
}
