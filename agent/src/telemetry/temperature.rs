use crate::error::Result;
use serde::{Deserialize, Serialize};
use std::fs;

/// Métricas de temperatura
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TemperatureMetrics {
    /// Lista de sensores de temperatura
    pub sensors: Vec<TemperatureSensor>,
}

/// Informações de um sensor de temperatura
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TemperatureSensor {
    /// Nome do sensor
    pub name: String,
    /// Temperatura em Celsius
    pub celsius: f32,
}

impl TemperatureMetrics {
    pub fn collect() -> Result<Self> {
        let mut sensors = Vec::new();

        // Tenta ler de /sys/class/hwmon (interface padrão Linux)
        if let Ok(entries) = fs::read_dir("/sys/class/hwmon") {
            for entry in entries.flatten() {
                let path = entry.path();
                let name = entry.file_name();
                let name_str = name.to_string_lossy().to_string();

                // Tenta ler temperatura de input
                let temp_path = path.join("temp1_input");
                if let Ok(temp_data) = fs::read_to_string(&temp_path) {
                    if let Ok(temp_millidegrees) = temp_data.trim().parse::<f32>() {
                        sensors.push(TemperatureSensor {
                            name: name_str,
                            celsius: temp_millidegrees / 1000.0,
                        });
                    }
                }
            }
        }

        // Se nenhum sensor foi encontrado, retorna uma lista vazia (não é erro crítico)
        Ok(Self { sensors })
    }
}
