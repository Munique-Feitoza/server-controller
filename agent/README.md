# Pocket NOC Agent - Agente Rust para Monitoramento de Infraestrutura

Um agente ultra-leve, escrito em Rust, que coleta telemetria de sistema em tempo real, monitora serviços críticos e executa comandos de emergência via API segura.

## 🎯 Características

- ✅ **Ultra-leve**: ~10-15 MB de memória, <1% CPU em idle
- ✅ **Telemetria em tempo real**: CPU, RAM, Disco, Temperatura, Uptime, Load Average
- ✅ **Monitoramento de Serviços**: Verifica status de serviços systemd
- ✅ **Comandos de Emergência**: Execute ações pré-aprovadas (restart nginx, docker, etc)
- ✅ **API REST**: Endpoints simples e eficientes
- ✅ **Autenticação**: JWT e API Key
- ✅ **Logging estruturado**: Integrado com journald

## 📦 Dependências Principais

```toml
tokio      = Async runtime
axum       = Web framework ultra-rápido
sysinfo    = Coleta de telemetria
procfs     = Interface /proc
jsonwebtoken = Autenticação JWT
```

## 🚀 Compilação e Instalação

### Pré-requisitos
- Rust 1.70+
- Linux (compatível com Ubuntu, Debian, CentOS, etc)

### Build

```bash
cd agent
cargo build --release
```

O binário compilado estará em `target/release/pocket-noc-agent`.

### Instalação como Serviço

```bash
# 1. Copiar binário
sudo cp target/release/pocket-noc-agent /usr/local/bin/

# 2. Criar usuário de serviço
sudo useradd -r -s /bin/false pocketnoc

# 3. Copiar arquivo de serviço
sudo cp systemd/pocket-noc-agent.service /etc/systemd/system/

# 4. Ativar e iniciar
sudo systemctl daemon-reload
sudo systemctl enable pocket-noc-agent
sudo systemctl start pocket-noc-agent

# 5. Verificar status
sudo systemctl status pocket-noc-agent
```

## 📡 Endpoints da API

Todos os endpoints (exceto `/health`) requerem autenticação JWT via header `Authorization: Bearer <token>`.

### Saúde

```bash
GET /health
# Resposta: { "status": "healthy", "service": "pocket-noc-agent" }
```

### Telemetria Completa

```bash
curl -H "Authorization: Bearer <token>" https://localhost:9443/telemetry
# Resposta: JSON completo com CPU, RAM, Disco, Temperatura, Uptime, Load Average
```

### Status de um Serviço

```bash
curl -H "Authorization: Bearer <token>" https://localhost:9443/services/nginx
# Resposta: { "name": "nginx", "status": "active", "pid": 1234 }
```

### Listar Comandos Disponíveis

```bash
curl -H "Authorization: Bearer <token>" https://localhost:9443/commands
# Resposta: Lista de comandos pré-aprovados
```

### Executar Comando de Emergência

```bash
curl -X POST -H "Authorization: Bearer <token>" https://localhost:9443/commands/restart_nginx
# Resposta: { "command_id": "restart_nginx", "exit_code": 0, "stdout": "...", "stderr": "..." }
```

### Métricas Prometheus

```bash
curl -H "Authorization: Bearer <token>" https://localhost:9443/metrics
# Resposta: Formato texto compatível com Prometheus
```

## 🔐 Segurança

### Autenticação JWT

O agente valida tokens JWT em todos os endpoints (exceto `/health`). Configure a chave secreta antes de iniciar:

```rust
let jwt_config = JwtConfig::new("seu-secret-super-seguro".to_string(), 3600);
let token = jwt_config.generate_token("client-1", vec!["read".to_string()])?;
```

### HTTPS/TLS

Por enquanto, o agente roda em HTTP na porta 9443. Para produção, use um proxy reverso (Nginx/Caddy) com TLS.

**Recomendação**: Coloque o agente em uma rede privada (Tailscale, WireGuard) e acesse via VPN.

### Whitelist de Comandos

Apenas os comandos definidos em `default_emergency_commands()` podem ser executados. Não há interpretação de shell - cada comando é executado isoladamente com seus argumentos validados.

## 🛠️ Estrutura do Código

```
src/
├── main.rs             # Entrypoint e configuração do servidor
├── lib.rs              # Declaração de módulos
├── error.rs            # Tipos de erro customizados
├── telemetry/
│   ├── mod.rs          # Coletor principal
│   ├── cpu.rs          # Métricas de CPU
│   ├── memory.rs       # Métricas de RAM/Swap
│   ├── disk.rs         # Métricas de disco
│   └── temperature.rs  # Métricas de temperatura
├── services/
│   └── mod.rs          # Monitor de serviços systemd
├── commands/
│   └── mod.rs          # Executor de comandos pré-aprovados
├── api/
│   ├── mod.rs
│   ├── handlers.rs     # Endpoints REST
│   └── middleware.rs   # Autenticação e logging
└── auth/
    └── mod.rs          # JWT e API Key
```

## 📊 Exemplo de Resposta de Telemetria

```json
{
  "cpu": {
    "usage_percent": 25.5,
    "core_count": 8,
    "cores": [
      { "index": 0, "usage_percent": 30.2 },
      { "index": 1, "usage_percent": 20.1 },
      ...
    ],
    "frequency_mhz": 3400
  },
  "memory": {
    "usage_percent": 45.3,
    "used_mb": 7654,
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
      }
    ]
  },
  "temperature": {
    "sensors": [
      { "name": "coretemp", "celsius": 65.2 }
    ]
  },
  "uptime": {
    "uptime_seconds": 864000,
    "load_average": [0.45, 0.52, 0.48]
  },
  "timestamp": 1234567890
}
```

## 🐛 Troubleshooting

### Agent não inicia
```bash
journalctl -u pocket-noc-agent -n 50
```

### Verificar compilação
```bash
cargo check
cargo clippy
```

### Testar conexão
```bash
curl -v https://localhost:9443/health
```

## 📝 Licença

MIT

---

**Próximo passo**: Configure a autenticação e implemente o [Controller Android](../controller/README.md).
