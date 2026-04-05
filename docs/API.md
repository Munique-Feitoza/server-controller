# Referência da API — Pocket NOC Agent

Esta API é interna e protegida. Todas as rotas (exceto `/health`) exigem o cabeçalho:

```
Authorization: Bearer <JWT_TOKEN>
```

## Base URL

O agente ouve exclusivamente em `http://127.0.0.1:9443` (acesso obrigatoriamente via túnel SSH).

---

## Autenticação

### Obter Token JWT

O token é gerado com a ferramenta `test_jwt_security.sh` ou via qualquer biblioteca JWT com HMAC-SHA256.

- **Algoritmo**: HS256
- **Expiração padrão**: 3600 segundos (1 hora), máximo 30 dias
- **Segredo**: deve ter no mínimo 32 caracteres (`POCKET_NOC_SECRET`)

---

## Health Check

### `GET /health`

Verifica se o agente está operante. Não requer autenticação.

**Resposta:**
```json
{ "status": "healthy" }
```

---

## Telemetria

### `GET /telemetry`

Retorna o estado completo da máquina. Os dados são cacheados por 5 segundos no agente para evitar sobrecarga de CPU.

**Resposta (resumida):**
```json
{
  "cpu": { "usage_percent": 12.5, "cores": [...] },
  "memory": { "total_mb": 8192, "used_mb": 3200, "usage_percent": 39.0 },
  "disk": { "disks": [{ "mount_point": "/", "total_gb": 80.0, "used_gb": 35.0, "usage_percent": 43.7 }] },
  "temperature": { "sensors": [{ "label": "Package id 0", "celsius": 52.0 }] },
  "network": { "interfaces": [...], "ping_latency_ms": 14.2 },
  "security": { "active_ssh_sessions": 1, "failed_login_attempts": 0, "failed_logins": [] },
  "processes": { "top_processes": [...], "docker_containers_running": 3 },
  "uptime": { "uptime_seconds": 345600, "load_average": [0.45, 0.60, 0.55] },
  "services": [{ "name": "nginx", "status": "active", "pid": 1234 }],
  "timestamp": 1743600000
}
```

### `GET /processes`

Lista os 10 processos que mais consomem CPU no momento.

**Resposta:**
```json
[
  { "pid": 1234, "name": "nginx", "cpu_usage": 3.5, "memory_mb": 45 }
]
```

### `DELETE /processes/:pid`

Encerra um processo específico via sinal SIGKILL.

**Parâmetros de rota:**
- `pid` — PID do processo a ser encerrado

**Resposta:**
```json
{ "killed": true }
```

---

## Alertas

### `GET /alerts`

Retorna a lista de alertas ativos com base nos thresholds configurados.

**Resposta:**
```json
[
  {
    "alert_type": "highcpu",
    "message": "CPU em alta carga: 87.3% (máx: 80.0%)",
    "current_value": 87.3,
    "threshold": 80.0,
    "timestamp": 1743600000,
    "component": null
  }
]
```

**Tipos de alerta:** `highcpu`, `highmemory`, `highdisk`, `hightemperature`, `securitythreat`, `recentreboot`

### `POST /alerts/config`

Atualiza os thresholds de alerta em tempo real (sem reiniciar o agente).

**Payload:**
```json
{
  "cpu_threshold_percent": 85.0,
  "memory_threshold_percent": 90.0,
  "disk_threshold_percent": 95.0,
  "temperature_threshold_celsius": 85.0,
  "reboot_threshold_minutes": 5,
  "security_threat_threshold": 10
}
```

Todos os campos são opcionais; campos omitidos mantêm o valor atual.

---

## Serviços e Logs

### `GET /services/:service_name`

Consulta o status de um serviço systemd específico.

**Parâmetros de rota:**
- `service_name` — Nome do serviço (ex: `nginx`, `docker`, `mysql`)

**Resposta:**
```json
{
  "name": "nginx",
  "status": "active",
  "description": "A high performance web server and a reverse proxy server",
  "pid": 12345
}
```

**Status possíveis:** `active`, `inactive`, `unknown`

### `GET /logs`

Acessa o buffer do `journalctl` para um serviço.

**Query params:**
- `service` — Nome do serviço (padrão: `pocket-noc-agent`)
- `lines` — Quantidade de linhas a retornar (padrão: 100)

**Exemplo:** `GET /logs?service=nginx&lines=50`

**Resposta:**
```json
{ "service": "nginx", "lines": ["Apr 01 12:00:00 nginx[1234]: ..."] }
```

---

## Action Center (Comandos)

### `GET /commands`

Lista todos os comandos disponíveis na Whitelist.

**Resposta:**
```json
[
  { "id": "restart_nginx", "description": "Restart Nginx web server" },
  { "id": "stop_nginx", "description": "Stop Nginx web server" },
  { "id": "restart_docker", "description": "Restart Docker daemon" },
  { "id": "restart_mysql", "description": "Restart MySQL/MariaDB" },
  { "id": "restart_agent", "description": "Restart Pocket NOC Agent" },
  { "id": "clear_logs", "description": "Rotate system logs" },
  { "id": "disk_usage", "description": "Show disk usage" }
]
```

### `POST /commands/:id`

Executa um comando da Whitelist. Não há shell intermediário — os argumentos são passados diretamente ao binário.

**Parâmetros de rota:**
- `id` — ID do comando (ver lista acima)

**Resposta:**
```json
{ "success": true, "output": "..." }
```

---

## Segurança

### `POST /security/block-ip`

Bloqueia um endereço IP via `iptables -I INPUT -s <ip> -j DROP`. Requer que o agente rode com permissão para executar `iptables` (root ou capacidade `CAP_NET_ADMIN`).

**Payload:**
```json
{ "ip": "192.168.1.100" }
```

Apenas endereços IPv4 ou IPv6 individuais válidos são aceitos. CIDRs e ranges são rejeitados.

**Resposta:**
```json
{ "blocked": true }
```

---

## Métricas (Prometheus)

### `GET /metrics`

Retorna métricas no formato texto do Prometheus para scraping.

**Resposta (Content-Type: text/plain):**
```
# HELP pocket_noc_cpu_usage_percent Current CPU usage percentage
# TYPE pocket_noc_cpu_usage_percent gauge
pocket_noc_cpu_usage_percent 12.5
# HELP pocket_noc_memory_usage_percent Current memory usage percentage
# TYPE pocket_noc_memory_usage_percent gauge
pocket_noc_memory_usage_percent 39.0
...
```

---

## Watchdog

### `GET /watchdog/events`

Retorna os eventos recentes do WatchdogEngine (ring buffer de 500 eventos).

**Query params (opcionais):**
- `server` — Filtra por nome de serviço
- `status` — Filtra por status (`healthy`, `failing`, `recovering`)

**Resposta:**
```json
[
  {
    "id": "uuid-...",
    "service": "nginx",
    "status": "failing",
    "message": "HTTP probe falhou: status 502",
    "timestamp": 1743600000,
    "remediation_attempted": true
  }
]
```

### `DELETE /watchdog/events`

Limpa o histórico de eventos do Watchdog na memória.

**Resposta:** `204 No Content`

### `POST /watchdog/reset`

Reseta todos os Circuit Breakers para o estado `Closed` (operação normal). Use após corrigir uma falha e querer que o Watchdog volte a tentar remediações.

**Resposta:**
```json
{ "reset": true }
```

### `GET /watchdog/breakers`

Inspeciona o estado atual de cada Circuit Breaker por serviço.

**Resposta:**
```json
{
  "nginx": { "state": "open", "failures": 3, "last_failure": 1743600000 },
  "docker": { "state": "closed", "failures": 0, "last_failure": null }
}
```

**Estados do Circuit Breaker:** `closed` (normal), `open` (bloqueado após falhas), `halfopen` (testando recuperação)

---

## Códigos de Erro

| Código | Significado |
|--------|-------------|
| `401`  | Token JWT ausente, expirado ou inválido |
| `403`  | Operação não permitida (ex: comando fora da whitelist) |
| `404`  | Recurso não encontrado (ex: PID ou serviço inexistente) |
| `422`  | Payload inválido (ex: IP malformado) |
| `500`  | Erro interno do agente |
