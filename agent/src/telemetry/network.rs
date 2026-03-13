use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{NetworkExt, NetworksExt, System, SystemExt};

/// Métricas de rede
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkMetrics {
    pub interfaces: Vec<InterfaceMetrics>,
}

/// Métricas de uma interface de rede
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InterfaceMetrics {
    pub name: String,
    pub rx_bytes: u64,
    pub tx_bytes: u64,
    pub rx_packets: u64,
    pub tx_packets: u64,
    pub rx_errors: u64,
    pub tx_errors: u64,
}

impl NetworkMetrics {
    pub fn collect(system: &System) -> Result<Self> {
        let interfaces = system
            .networks()
            .iter()
            .map(|(name, data)| InterfaceMetrics {
                name: name.clone(),
                rx_bytes: data.received(),
                tx_bytes: data.transmitted(),
                rx_packets: data.packets_received(),
                tx_packets: data.packets_transmitted(),
                rx_errors: data.errors_on_received(),
                tx_errors: data.errors_on_transmitted(),
            })
            .collect();

        Ok(Self { interfaces })
    }
}
