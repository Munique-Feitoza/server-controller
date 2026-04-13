# Guia de Instalação e Deployment — Pocket NOC

> Guia completo para colocar a infraestrutura sob monitoramento.  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## Sumário

1. [Pré-requisitos](#pré-requisitos)
2. [Instalação do Agente](#instalação-do-agente)
3. [Variáveis de Ambiente](#variáveis-de-ambiente)
4. [Serviço Systemd](#serviço-systemd)
5. [Configuração do Controller (Android)](#configuração-do-controller-android)
6. [Verificação da Instalação](#verificação-da-instalação)
7. [Troubleshooting](#troubleshooting)

---

## Pré-requisitos

### Servidor (Agente Rust)

| Requisito | Versão | Observação |
|:---|:---|:---|
| Linux com systemd | Ubuntu 22.04+ / Debian 12+ | Outros distros compatíveis com systemd também funcionam |
| Rust | 1.70+ | Apenas para compilação (o binário musl é estático) |
| `iptables` | Qualquer | Para funcionalidade de block-ip |
| Acesso SSH | OpenSSH | Com autenticação por chave (Ed25519 recomendado) |
| Acesso sudo/root | — | Para criar usuário `pocketnoc` e instalar o serviço |

### Máquina de Desenvolvimento

| Requisito | Versão | Observação |
|:---|:---|:---|
| Rust + musl target | 1.70+ | `rustup target add x86_64-unknown-linux-musl` |
| Android Studio | Hedgehog+ | Com Kotlin 1.9+ e Compose Compiler |
| JDK | 17+ | Para build do Android |

---

## Instalação do Agente

### Opção A — Deploy Automatizado (Recomendado)

O script `deploy.sh` compila o binário musl estático na sua máquina local e faz deploy em todos os servidores configurados:

```bash
chmod +x deploy.sh
./deploy.sh
```

**O script executa automaticamente:**

```mermaid
flowchart LR
    A["Compila musl<br/>(x86_64-static)"] --> B["Para cada servidor"]
    B --> C["pkill agent"]
    C --> D["scp binário"]
    D --> E["mv → /usr/local/bin/"]
    E --> F["systemctl restart"]
    F --> G["Verifica status"]
```

### Opção B — Compilação Manual

```bash
# Clone o repositório
git clone https://github.com/Munique-Feitoza/pocket-noc.git
cd pocket-noc/agent

# Instala o target musl (apenas primeira vez)
rustup target add x86_64-unknown-linux-musl

# Compila binário estático otimizado
cargo build --release --target x86_64-unknown-linux-musl

# Binário gerado em:
# target/x86_64-unknown-linux-musl/release/pocket-noc-agent
```

**Para ARM (aarch64):**
```bash
rustup target add aarch64-unknown-linux-gnu
cargo build --release --target aarch64-unknown-linux-gnu
```

---

## Variáveis de Ambiente

Crie o arquivo `/etc/pocket-noc/.env` no servidor:

```bash
sudo mkdir -p /etc/pocket-noc
sudo nano /etc/pocket-noc/.env
```

### Variáveis Obrigatórias

| Variável | Descrição |
|:---|:---|
| `POCKET_NOC_SECRET` | Segredo JWT. **Mínimo 32 caracteres**. Gere com `openssl rand -hex 32` |

### Variáveis Opcionais

| Variável | Padrão | Descrição |
|:---|:---|:---|
| `POCKET_NOC_PORT` | `9443` | Porta de bind (sempre em 127.0.0.1) |
| `SERVER_ROLE` | `generic` | Define probes do Watchdog. Opções: `wordpress`, `wordpress-python`, `erp`, `python-nextjs`, `database`, `generic`. O role `generic` faz **autodetecção** — descobre sozinho todos os `php*-fpm`, `mariadb`/`mysql`, `postgresql@*` e adiciona probes TCP 80/443/3306/5432 conforme o que estiver ativo. |
| `SERVER_ID` | hostname | Identificador único do servidor |
| `NTFY_TOPIC` | Derivado do secret | Tópico ntfy.sh para notificações push |
| `WATCHDOG_ENABLED` | `true` | Liga/desliga o WatchdogEngine |
| `WATCHDOG_INTERVAL_SECS` | `30` | Intervalo entre verificações |
| `WATCHDOG_MAX_FAILURES` | `3` | Falhas consecutivas para abrir o Circuit Breaker |
| `WATCHDOG_COOLDOWN_SECS` | `300` | Tempo (segundos) que o breaker fica aberto |
| `WATCHDOG_WEBHOOK_URL` | — | URL para receber eventos do Watchdog via HTTP POST |
| `RATE_LIMIT_PER_MINUTE` | `60` | Requisições por minuto por IP |
| `SSL_SKIP_DOMAINS` | — | Lista separada por vírgula de domínios a **não verificar** no monitor SSL. Útil para domínios internos que não precisam de certificado público. Ex: `api.example.com,app.example.com` |
| `EXTRA_SERVICE_PROBES` | — | Probes extras do Watchdog via `systemctl is-active`. Formato: `servico1;servico2;servico3`. Ex: `postfix;dovecot;clamav-daemon` |
| `EXTRA_TCP_PROBES` | — | Probes TCP extras. Formato: `nome\|host\|porta;...`. Ex: `memcached-tcp\|127.0.0.1\|11211` |
| `EXTRA_HTTP_PROBES` | — | Probes HTTP extras. Formato: `nome\|url\|status_esperado\|latencia_degraded_ms;...`. Ex: `erp-api\|http://127.0.0.1:8000/health\|200\|2000` |

### Exemplo Completo

```bash
# /etc/pocket-noc-agent.env
POCKET_NOC_SECRET=a3f8d2c1e4b5f67890abcdef1234567890abcdef1234567890abcdef12345678
POCKET_NOC_PORT=9443
SERVER_ROLE=generic
SERVER_ID=vps-server-01
NTFY_TOPIC=pocket_noc_REDACTED
WATCHDOG_ENABLED=true
WATCHDOG_INTERVAL_SECS=30
WATCHDOG_MAX_FAILURES=3
WATCHDOG_COOLDOWN_SECS=300
RATE_LIMIT_PER_MINUTE=60

# Opcional: pular SSL check em dominios internos
SSL_SKIP_DOMAINS=api.example.com,app.example.com

# Opcional: probes customizados do Watchdog
EXTRA_HTTP_PROBES=erp-api|http://127.0.0.1:8000/health|200|2000
```

---

## Serviço Systemd

### Criação do Usuário Dedicado

```bash
# Cria usuário de sistema sem shell e sem home
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pocketnoc

# Adiciona ao grupo systemd-journal (para leitura de logs)
sudo usermod -aG systemd-journal pocketnoc
```

### Instalação do Binário

```bash
sudo cp target/x86_64-unknown-linux-musl/release/pocket-noc-agent /usr/local/bin/
sudo chmod +x /usr/local/bin/pocket-noc-agent
```

### Instalação do Serviço

```bash
sudo cp agent/systemd/pocket-noc-agent.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now pocket-noc-agent
```

### Verificação

```bash
sudo systemctl status pocket-noc-agent
```

### Arquivo de Serviço de Referência

```ini
[Unit]
Description=Pocket NOC Agent — Server Monitoring & Auto-Remediation
Documentation=https://github.com/Munique-Feitoza/pocket-noc
After=network.target

[Service]
Type=simple
User=pocketnoc
Group=pocketnoc

# Secret via EnvironmentFile (não exposto em 'ps aux')
EnvironmentFile=-/etc/pocket-noc/.env

ExecStart=/usr/local/bin/pocket-noc-agent
Restart=always
RestartSec=10

# Limites de recursos
MemoryMax=128M
CPUQuota=5%

# Capabilities mínimas (em vez de root)
AmbientCapabilities=CAP_KILL CAP_NET_ADMIN
CapabilityBoundingSet=CAP_KILL CAP_NET_ADMIN

[Install]
WantedBy=multi-user.target
```

---

## Configuração do Controller (Android)

### Arquivo `local.properties`

No diretório `controller/`, crie ou edite `local.properties`:

```properties
# === Servidor 1 ===
POCKET_NOC_SERVER_1=192.0.2.10
POCKET_NOC_SERVER_NAME_1=server-1
SSH_USER_1=deploy
SSH_HOST_1=192.0.2.10
SSH_SERVICE_PORT_1=22
LOCAL_FORWARD_PORT_1=9443
REMOTE_AGENT_PORT_1=9443

# === Servidor 2 ===
POCKET_NOC_SERVER_2=192.0.2.20
POCKET_NOC_SERVER_NAME_2=server-2
SSH_USER_2=deploy
SSH_HOST_2=192.0.2.20
SSH_SERVICE_PORT_2=22
LOCAL_FORWARD_PORT_2=9444
REMOTE_AGENT_PORT_2=9443

# === Chave SSH Global (Ed25519 recomendado) ===
SSH_KEY_CONTENT_GLOBAL=-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----

# === Segredo JWT (IDÊNTICO ao POCKET_NOC_SECRET do servidor) ===
POCKET_NOC_SECRET=a3f8d2c1e4b5f67890abcdef1234567890abcdef1234567890abcdef12345678

# === Dashboard ERP (opcional) ===
DASHBOARD_NOC_TOKEN=token-do-dashboard
DASHBOARD_API_URL=https://api.example.com/api/v1/pocketnoc/

# === Feature Flags ===
USE_HTTPS=false
EMERGENCY_MODE=false
SSH_STRICT_HOST_CHECKING=true
MAX_AUTH_FAILURES=3
CPU_ALERT_THRESHOLD=80
```

### Build

```bash
cd controller

# Debug (desenvolvimento)
./gradlew assembleDebug

# Release (produção)
./gradlew assembleRelease

# Instalar no dispositivo conectado
./gradlew installDebug
```

---

## Verificação da Instalação

### 1. Verificar o Agente

```bash
# Status do serviço
sudo systemctl status pocket-noc-agent

# Logs em tempo real
journalctl -u pocket-noc-agent -f

# Teste direto (no próprio servidor)
curl http://127.0.0.1:9443/health
# Esperado: {"status":"healthy"}
```

### 2. Testar via Túnel SSH

```bash
# Na sua máquina local
ssh -L 9443:127.0.0.1:9443 deploy@192.0.2.10

# Em outro terminal
curl http://127.0.0.1:9443/health
# Esperado: {"status":"healthy"}

# Teste com JWT
TOKEN=$(./test_jwt_security.sh)
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:9443/telemetry
```

### 3. Verificar o Watchdog

```bash
TOKEN=$(./test_jwt_security.sh)
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:9443/watchdog/breakers
```

---

## Troubleshooting

### Problemas Comuns

| Problema | Causa | Solução |
|:---|:---|:---|
| `Connection refused` | Agente não está rodando | `sudo systemctl start pocket-noc-agent` |
| `401 Unauthorized` | Secret JWT diferente no app e servidor | Conferir se `POCKET_NOC_SECRET` é idêntico nos dois |
| Token expirado | JWT passou de 1 hora | Gerar novo token no app |
| SSH Tunnel Failure | IP errado ou porta SSH fechada | Testar `ssh usuario@ip` manualmente |
| Logs não aparecem | Permissão de journal | `sudo usermod -aG systemd-journal pocketnoc` |
| Watchdog não remedia | Circuit Breaker aberto | `GET /watchdog/breakers` + `POST /watchdog/reset` |
| `429 Too Many Requests` | Rate limit excedido | Aguardar 1 minuto ou aumentar `RATE_LIMIT_PER_MINUTE` |
| Crash EncryptedPrefs | Arquivo corrompido | App recria automaticamente (SecureTokenManager) |

### Comandos de Diagnóstico

```bash
# Logs do agente (últimas 50 linhas)
journalctl -u pocket-noc-agent -n 50 --no-pager

# Verificar se a porta está em uso
ss -tlnp | grep 9443

# Verificar capabilities do processo
cat /proc/$(pidof pocket-noc-agent)/status | grep Cap

# Uso de recursos do agente
ps aux | grep pocket-noc-agent

# Verificar regras iptables (IPs banidos)
sudo iptables -L INPUT -n | grep DROP
```

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
