use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{NetworkExt, NetworksExt, System, SystemExt};

/// Métricas de rede
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkMetrics {
    pub interfaces: Vec<InterfaceMetrics>,
    /// Latência do ping pro gateway/internet em ms
    pub ping_latency_ms: Option<f64>,
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

        // Extraindo Network Latency/Jitter com ping para a cloudflare
        // Envolto em 'timeout' do GNU coreutils para evitar travamento da thread caso a rede morra
        let ping_output = std::process::Command::new("timeout")
            .args(["2s", "ping", "-c", "1", "-W", "1", "1.1.1.1"])
            .output()
            .ok();

        let ping_latency_ms = ping_output.and_then(|output| {
            let out_str = String::from_utf8_lossy(&output.stdout);
            if let Some(time_idx) = out_str.find("time=") {
                let rest = &out_str[time_idx + 5..];
                if let Some(space_idx) = rest.find(" ms") {
                    rest[..space_idx].parse::<f64>().ok()
                } else {
                    None
                }
            } else {
                None
            }
        });

        Ok(Self {
            interfaces,
            ping_latency_ms,
        })
    }
}
