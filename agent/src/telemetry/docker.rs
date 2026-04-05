use serde::{Deserialize, Serialize};

/// Informações de um container Docker.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DockerContainer {
    pub id: String,
    pub name: String,
    pub image: String,
    pub status: String,
    pub state: String,
    pub created: String,
    pub ports: Vec<String>,
}

/// Métricas agregadas dos containers Docker.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DockerMetrics {
    pub containers: Vec<DockerContainer>,
    pub running_count: u32,
    pub total_count: u32,
}

/// Coleta informações dos containers Docker via `docker ps`.
/// Retorna None se o Docker não estiver instalado ou acessível.
pub fn collect_docker_metrics() -> Option<DockerMetrics> {
    let output = std::process::Command::new("docker")
        .args(["ps", "-a", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.State}}|{{.CreatedAt}}|{{.Ports}}"])
        .output()
        .ok()?;

    if !output.status.success() {
        return None;
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut containers = Vec::new();
    let mut running_count = 0u32;

    for line in stdout.lines() {
        let parts: Vec<&str> = line.splitn(7, '|').collect();
        if parts.len() >= 5 {
            let state = parts.get(4).unwrap_or(&"unknown").to_string();
            if state == "running" {
                running_count += 1;
            }

            let ports_str = parts.get(6).unwrap_or(&"");
            let ports: Vec<String> = if ports_str.is_empty() {
                vec![]
            } else {
                ports_str.split(',').map(|s| s.trim().to_string()).collect()
            };

            containers.push(DockerContainer {
                id: parts[0].to_string(),
                name: parts.get(1).unwrap_or(&"").to_string(),
                image: parts.get(2).unwrap_or(&"").to_string(),
                status: parts.get(3).unwrap_or(&"").to_string(),
                state,
                created: parts.get(5).unwrap_or(&"").to_string(),
                ports,
            });
        }
    }

    let total_count = containers.len() as u32;

    Some(DockerMetrics {
        containers,
        running_count,
        total_count,
    })
}
