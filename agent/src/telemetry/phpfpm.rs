use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Metricas de um pool PHP-FPM individual (mapeado a um site)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhpFpmPool {
    /// Nome do pool (ex: "us-curriculogratis")
    pub pool_name: String,
    /// Percentual total de CPU consumido por todos os workers deste pool
    pub cpu_percent: f32,
    /// RAM total em MB consumida por todos os workers
    pub memory_mb: f32,
    /// Quantidade de workers (processos) ativos
    pub worker_count: u32,
}

/// Metricas agregadas de todos os pools PHP-FPM do servidor
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhpFpmMetrics {
    /// Lista de pools ordenados por CPU (decrescente)
    pub pools: Vec<PhpFpmPool>,
    /// Total de workers PHP-FPM ativos
    pub total_workers: u32,
    /// CPU total consumida por todos os pools
    pub total_cpu_percent: f32,
    /// RAM total em MB consumida por todos os pools
    pub total_memory_mb: f32,
}

/// Coleta metricas de todos os pools PHP-FPM ativos no servidor.
/// Funciona com RunCloud (php*rc-fpm) e instalacoes padrão.
pub fn collect_phpfpm_metrics() -> PhpFpmMetrics {
    let output = std::process::Command::new("ps")
        .args(["aux"])
        .output();

    let stdout = match output {
        Ok(o) if o.status.success() => String::from_utf8_lossy(&o.stdout).to_string(),
        _ => return PhpFpmMetrics { pools: vec![], total_workers: 0, total_cpu_percent: 0.0, total_memory_mb: 0.0 },
    };

    // Agrupa por pool name
    let mut pool_data: HashMap<String, (f32, f32, u32)> = HashMap::new(); // (cpu, rss_kb, count)

    for line in stdout.lines() {
        if !line.contains("php-fpm") || !line.contains("pool") || line.contains("grep") {
            continue;
        }

        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 11 {
            continue;
        }

        let cpu: f32 = parts[2].parse().unwrap_or(0.0);
        let rss_kb: f32 = parts[5].parse().unwrap_or(0.0);

        // O nome do pool eh a ultima palavra da linha (ex: "pool us-curriculogratis")
        let pool_name = parts.last().unwrap_or(&"unknown").to_string();

        let entry = pool_data.entry(pool_name).or_insert((0.0, 0.0, 0));
        entry.0 += cpu;
        entry.1 += rss_kb;
        entry.2 += 1;
    }

    let mut pools: Vec<PhpFpmPool> = pool_data.into_iter()
        .map(|(name, (cpu, rss_kb, count))| PhpFpmPool {
            pool_name: name,
            cpu_percent: cpu,
            memory_mb: rss_kb / 1024.0,
            worker_count: count,
        })
        .collect();

    // Ordena por CPU decrescente
    pools.sort_by(|a, b| b.cpu_percent.partial_cmp(&a.cpu_percent).unwrap_or(std::cmp::Ordering::Equal));

    let total_workers = pools.iter().map(|p| p.worker_count).sum();
    let total_cpu = pools.iter().map(|p| p.cpu_percent).sum();
    let total_mem = pools.iter().map(|p| p.memory_mb).sum();

    PhpFpmMetrics {
        pools,
        total_workers,
        total_cpu_percent: total_cpu,
        total_memory_mb: total_mem,
    }
}
