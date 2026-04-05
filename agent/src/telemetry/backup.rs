use serde::{Deserialize, Serialize};
use std::path::Path;

/// Informações sobre um arquivo/diretório de backup.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupInfo {
    pub path: String,
    pub last_modified: String,
    pub age_hours: f64,
    pub size_bytes: u64,
    pub is_stale: bool,
}

/// Status geral dos backups do servidor.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupStatus {
    pub backups: Vec<BackupInfo>,
    pub any_stale: bool,
}

/// Caminhos comuns onde backups podem estar armazenados.
const BACKUP_PATHS: &[&str] = &[
    "/var/backups",
    "/backup",
    "/home/backup",
    "/root/backups",
    "/opt/backups",
];

/// Extensões de arquivo que indicam backup.
const BACKUP_EXTENSIONS: &[&str] = &[
    ".sql.gz", ".sql.bz2", ".sql.xz", ".sql",
    ".tar.gz", ".tar.bz2", ".tar.xz", ".tgz",
    ".zip", ".bak", ".dump",
];

/// Limiar de horas para considerar um backup como "stale" (obsoleto).
const STALE_THRESHOLD_HOURS: f64 = 25.0;

/// Coleta informações sobre os backups do servidor.
/// Varre diretórios conhecidos procurando por arquivos de backup.
pub fn collect_backup_status() -> BackupStatus {
    let mut backups = Vec::new();
    let now = std::time::SystemTime::now();

    for backup_dir in BACKUP_PATHS {
        let path = Path::new(backup_dir);
        if !path.exists() || !path.is_dir() {
            continue;
        }

        // Procura arquivos de backup no diretório (1 nível)
        if let Ok(entries) = std::fs::read_dir(path) {
            for entry in entries.flatten() {
                let file_path = entry.path();
                let file_name = file_path.to_string_lossy().to_string();

                // Verifica se o arquivo tem extensão de backup
                let is_backup = BACKUP_EXTENSIONS.iter().any(|ext| file_name.ends_with(ext));
                if !is_backup {
                    continue;
                }

                if let Ok(metadata) = entry.metadata() {
                    let modified = metadata.modified().unwrap_or(std::time::UNIX_EPOCH);
                    let age = now.duration_since(modified).unwrap_or_default();
                    let age_hours = age.as_secs_f64() / 3600.0;

                    let modified_str = chrono::DateTime::<chrono::Utc>::from(modified).to_rfc3339();

                    backups.push(BackupInfo {
                        path: file_name,
                        last_modified: modified_str,
                        age_hours,
                        size_bytes: metadata.len(),
                        is_stale: age_hours > STALE_THRESHOLD_HOURS,
                    });
                }
            }
        }
    }

    // Ordena por mais recente primeiro
    backups.sort_by(|a, b| a.age_hours.partial_cmp(&b.age_hours).unwrap_or(std::cmp::Ordering::Equal));

    let any_stale = backups.iter().any(|b| b.is_stale);

    BackupStatus {
        backups,
        any_stale,
    }
}
