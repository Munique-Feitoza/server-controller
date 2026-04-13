use crate::error::Result;
use serde::{Deserialize, Serialize};
use sysinfo::{DiskExt, System, SystemExt};

/// Métricas de disco
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiskMetrics {
    /// Lista de discos
    pub disks: Vec<DiskInfo>,
    /// Total bytes lidos do disco (acumulado)
    pub total_read_bytes: u64,
    /// Total bytes escritos no disco (acumulado)
    pub total_write_bytes: u64,
}

/// Informações de um disco
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiskInfo {
    /// Nome do ponto de montagem
    pub mount_point: String,
    /// Espaço usado em GB
    pub used_gb: f64,
    /// Espaço total em GB
    pub total_gb: f64,
    /// Percentual de uso
    pub usage_percent: f32,
    /// Tipo de arquivo (ext4, ntfs, etc)
    pub filesystem: String,
}

impl DiskMetrics {
    pub fn collect(system: &System) -> Result<Self> {
        let mut total_read_bytes = 0;
        let mut total_write_bytes = 0;

        // Coletando I/O bruto via Linux procfs
        if let Ok(stats) = procfs::diskstats() {
            for stat in stats {
                // Sectors geralmente tem 512 bytes
                total_read_bytes += stat.sectors_read * 512;
                total_write_bytes += stat.sectors_written * 512;
            }
        }

        let disks = system
            .disks()
            .iter()
            .map(|disk: &sysinfo::Disk| {
                let total = disk.total_space() as f64 / (1024.0 * 1024.0 * 1024.0);
                let used = (disk.total_space() - disk.available_space()) as f64
                    / (1024.0 * 1024.0 * 1024.0);
                let usage_percent = if total > 0.0 {
                    (used / total * 100.0) as f32
                } else {
                    0.0
                };

                DiskInfo {
                    mount_point: disk.mount_point().to_string_lossy().to_string(),
                    used_gb: used,
                    total_gb: total,
                    usage_percent,
                    filesystem: String::from_utf8_lossy(disk.file_system()).to_string(),
                }
            })
            .collect();

        Ok(Self {
            disks,
            total_read_bytes,
            total_write_bytes,
        })
    }
}
