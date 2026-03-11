# API Specification - Pocket NOC Agent

Documentação completa dos endpoints da API REST do Agente Rust.

## 🌐 Informações Gerais

- **Protocolo**: HTTPS/TLS
- **Porta Padrão**: 9443
- **Content-Type**: `application/json`
- **Autenticação**: JWT Bearer Token (Bearer <token>)
- **Rate Limiting**: Não implementado ainda (TODO)

## 📊 Status Codes

| Código | Significado | Exemplo |
|--------|-------------|---------|
| 200 | OK - Requisição bem-sucedida | GET /health |
| 201 | Created - Recurso criado | POST /commands |
| 400 | Bad Request - Parametrização inválida | POST /commands/invalid_id |
| 401 | Unauthorized - Token ausente/inválido | Falta header Authorization |
| 404 | Not Found - Recurso não existe | GET /services/nonexistent |
| 500 | Internal Server Error | Erro no processamento |

## 📡 Endpoints

### 1. Health Check

Verifica se o agente está rodando. **Não requer autenticação**.

```
GET /health
```

**Response (200 OK)**:
```json
{
  "status": "healthy",
  "service": "pocket-noc-agent",
  "timestamp": "2024-03-11T12:34:56Z"
}
```

**Exemplo cURL**:
```bash
curl -k https://localhost:9443/health
```

---

### 2. Telemetria Completa

Retorna todos os dados de telemetria do sistema em tempo real.

```
GET /telemetry
Authorization: Bearer <token>
```

**Response (200 OK)**:
```json
{
  "cpu": {
    "usage_percent": 42.5,
    "core_count": 8,
    "cores": [
      { "index": 0, "usage_percent": 45.2 },
      { "index": 1, "usage_percent": 40.1 },
      ...
    ],
    "frequency_mhz": 3400
  },
  "memory": {
    "usage_percent": 58.3,
    "used_mb": 9563,
    "total_mb": 16384,
    "swap_used_mb": 0,
    "swap_total_mb": 4096
  },
  "disk": {
    "disks": [
      {
        "mount_point": "/",
        "used_gb": 45.2,
        "total_gb": 100.0,
        "usage_percent": 45.2,
        "filesystem": "ext4"
      },
      {
        "mount_point": "/home",
        "used_gb": 120.5,
        "total_gb": 500.0,
        "usage_percent": 24.1,
        "filesystem": "ext4"
      }
    ]
  },
  "temperature": {
    "sensors": [
      { "name": "coretemp", "celsius": 65.2 },
      { "name": "acpitz", "celsius": 29.8 }
    ]
  },
  "uptime": {
    "uptime_seconds": 864000,
    "load_average": [0.45, 0.52, 0.48]
  },
  "timestamp": 1710152096
}
```

**Exemplo cURL**:
```bash
curl -k -H "Authorization: Bearer <token>" \
  https://localhost:9443/telemetry
```

**Exemplo Kotlin**:
```kotlin
val token = "seu-jwt-token"
val authHeader = "Bearer $token"
val telemetry = apiService.getTelemetry(authHeader)
```

---

### 3. Status de um Serviço

Verifica o status de um serviço específico (systemd).

```
GET /services/{service_name}
Authorization: Bearer <token>
```

**Parameters**:
- `service_name` (string): Nome do serviço, ex: `nginx`, `docker`, `postgresql`

**Response (200 OK)**:
```json
{
  "name": "nginx",
  "status": "active",
  "description": "A free, open-source, high-performance HTTP server",
  "pid": 1234
}
```

**Respostas Possíveis**:
- `status`: `"active"`, `"inactive"`, `"unknown"`
- `pid`: `null` se inativo

**Exemplo cURL**:
```bash
curl -k -H "Authorization: Bearer <token>" \
  https://localhost:9443/services/nginx
```

**Serviços Comuns**:
- `nginx` - Web server
- `docker` - Container runtime
- `postgresql` - Banco de dados
- `redis` - Cache
- `systemd-journald` - Log service
- `openssh-server` - SSH server

---

### 4. Listar Comandos Disponíveis

Retorna lista de todos os comandos de emergência permitidos.

```
GET /commands
Authorization: Bearer <token>
```

**Response (200 OK)**:
```json
{
  "commands": [
    {
      "id": "restart_nginx",
      "description": "Restart Nginx web server",
      "command": "systemctl",
      "args": ["restart", "nginx"]
    },
    {
      "id": "stop_nginx",
      "description": "Stop Nginx web server",
      "command": "systemctl",
      "args": ["stop", "nginx"]
    },
    {
      "id": "restart_docker",
      "description": "Restart Docker daemon",
      "command": "systemctl",
      "args": ["restart", "docker"]
    },
    {
      "id": "clear_logs",
      "description": "Clear systemd journal logs",
      "command": "journalctl",
      "args": ["--rotate"]
    },
    {
      "id": "check_disk_space",
      "description": "Check disk space usage",
      "command": "df",
      "args": ["-h"]
    }
  ]
}
```

**Exemplo cURL**:
```bash
curl -k -H "Authorization: Bearer <token>" \
  https://localhost:9443/commands
```

---

### 5. Executar Comando de Emergência

Executa um comando pré-aprovado. **Requer confirmação do usuário**.

```
POST /commands/{command_id}
Authorization: Bearer <token>
```

**Parameters**:
- `command_id` (string): ID do comando a executar

**Response (200 OK)**:
```json
{
  "command_id": "restart_nginx",
  "exit_code": 0,
  "stdout": "",
  "stderr": "",
  "timestamp": 1710152146
}
```

**Response (400 Bad Request)**:
```json
{
  "error": "Command 'invalid_command' not found",
  "status": 400
}
```

**Exemplo cURL**:
```bash
curl -k -X POST -H "Authorization: Bearer <token>" \
  https://localhost:9443/commands/restart_nginx
```

**Exemplo Kotlin**:
```kotlin
val result = repository.executeCommand("restart_nginx", token)
result.onSuccess { commandResult ->
    println("Exit code: ${commandResult.exitCode}")
    println("Output: ${commandResult.stdout}")
}
result.onFailure { error ->
    println("Error: ${error.message}")
}
```

---

### 6. Métricas (Prometheus)

Retorna métricas em formato compatível com Prometheus para scraping.

```
GET /metrics
Authorization: Bearer <token>
```

**Response (200 OK)** - Formato Prometheus:
```
# HELP cpu_usage_percent CPU usage percentage
# TYPE cpu_usage_percent gauge
cpu_usage_percent 42.5
cpu_usage_percent{core="0"} 45.2
cpu_usage_percent{core="1"} 40.1

# HELP memory_usage_percent Memory usage percentage
# TYPE memory_usage_percent gauge
memory_usage_percent 58.3
memory_used_mb 9563
memory_total_mb 16384

# HELP disk_usage_percent Disk usage percentage
# TYPE disk_usage_percent gauge
disk_usage_percent{mount="/",fs="ext4"} 45.2
disk_used_gb{mount="/"} 45.2
disk_total_gb{mount="/"} 100.0

# HELP system_uptime_seconds System uptime in seconds
# TYPE system_uptime_seconds gauge
system_uptime_seconds 864000
system_load_average{period="1m"} 0.45
```

**Integração com Prometheus**:

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'pocket-noc-agents'
    scheme: https
    tls_config:
      insecure_skip_verify: true # Para self-signed certs
    bearer_token: 'seu-jwt-token'
    static_configs:
      - targets: ['localhost:9443']
```

**Exemplo cURL**:
```bash
curl -k -H "Authorization: Bearer <token>" \
  https://localhost:9443/metrics
```

---

## 🔑 Autenticação

### Header Obrigatório

Todos os endpoints (exceto `/health`) requerem:

```
Authorization: Bearer <JWT_TOKEN>
```

### Gerando um Token

No servidor:

```rust
let jwt_config = JwtConfig::new("secret-key".to_string(), 3600);
let token = jwt_config.generate_token("client-1", vec!["read".to_string()])?;
println!("Token: {}", token);
```

### Estrutura do JWT

```
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "client-1",
  "exp": 1710155696,
  "iat": 1710152096,
  "scopes": ["read"]
}

Signature: HMACSHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  "secret-key"
)
```

---

## 🧪 Exemplos de Workflow

### Workflow Completo: Monitorar e Reiniciar Nginx

```bash
#!/bin/bash
TOKEN="seu-jwt-token"
SERVER="https://localhost:9443"

# 1. Verificar saúde do agente
echo "=== Health Check ==="
curl -k $SERVER/health

# 2. Obter telemetria
echo "=== Telemetry ==="
curl -k -H "Authorization: Bearer $TOKEN" \
  $SERVER/telemetry | jq .

# 3. Verificar status do nginx
echo "=== Nginx Status ==="
curl -k -H "Authorization: Bearer $TOKEN" \
  $SERVER/services/nginx | jq .

# 4. Se estiver inativo, reiniciar
echo "=== Restart Nginx ==="
curl -k -X POST -H "Authorization: Bearer $TOKEN" \
  $SERVER/commands/restart_nginx | jq .
```

---

## ⚠️ Erros Comuns

### 401 Unauthorized
```json
{
  "error": "Missing Authorization header",
  "status": 401
}
```

**Solução**: Adicionar `Authorization: Bearer <token>` ao header.

### 404 Not Found
```json
{
  "error": "Command 'typo_command' not found",
  "status": 404
}
```

**Solução**: Listar comandos com `GET /commands` e usar um ID válido.

### 500 Internal Server Error
```json
{
  "error": "Failed to read CPU metrics: ...",
  "status": 500
}
```

**Solução**: Verificar logs do agent (`journalctl -u pocket-noc-agent`).

---

## 📊 Performance

### Response Times (Esperado)

| Endpoint | Tempo |
|----------|-------|
| `/health` | < 10ms |
| `/telemetry` | 50-200ms |
| `/services/{name}` | 100-300ms |
| `/commands/{id}` | 1-5s (depende do comando) |
| `/metrics` | 100-500ms |

### Limites

- Max payload: 10MB
- Timeout de conexão: 15s
- Timeout de read: 15s
- Max concurrent connections: Limitado pelo OS

---

## 📚 Links Relacionados

- [Security.md](./SECURITY.md) - Detalhes de autenticação e segurança
- [Agent README](../agent/README.md) - Informações do agent Rust
- [Controller README](../controller/README.md) - Informações do controller Android

---

**Versão API**: 0.1.0  
**Última atualização**: 2024-03-11
