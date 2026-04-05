# Guia de Setup e Deployment — Pocket NOC Ultra

Siga estes passos para colocar sua infraestrutura sob monitoramento.

---

## 1. Configurando o Agente no Servidor

### Requisitos

- Rust 1.70+ instalado (ou use o deploy via binário musl pré-compilado)
- Linux com systemd (Ubuntu 22.04+ recomendado)
- `iptables` disponível (para a funcionalidade de block-ip)
- Acesso sudo/root para criar o usuário `pocketnoc` e instalar o serviço

### Opção A — Deploy Automatizado (Recomendado)

O script `deploy.sh` na raiz do repositório compila o binário musl estático localmente e faz deploy em todos os servidores configurados via SSH:

```bash
# Na sua máquina local (não no servidor)
chmod +x deploy.sh
./deploy.sh
```

O script irá:
1. Compilar o agente como binário estático `x86_64-unknown-linux-musl` (zero dependências)
2. Parar o processo antigo via systemd
3. Copiar o novo binário via `scp`
4. Reiniciar o serviço e verificar o status

### Opção B — Compilação Manual

```bash
# Clone o repositório no seu servidor
git clone https://github.com/Munique-Feitoza/pocket-noc.git
cd pocket-noc/agent

# Instala o target musl (apenas primeira vez)
rustup target add x86_64-unknown-linux-musl

# Compila binário estático otimizado
cargo build --release --target x86_64-unknown-linux-musl

# Binário gerado:
# target/x86_64-unknown-linux-musl/release/pocket-noc-agent
```

---

## 2. Variáveis de Ambiente

Crie o arquivo `/etc/pocket-noc/.env` no servidor (ou configure via systemd `Environment=`):

| Variável | Obrigatória | Padrão | Descrição |
|----------|-------------|--------|-----------|
| `POCKET_NOC_SECRET` | **Sim** | — | Segredo JWT. Mínimo 32 caracteres. Use `openssl rand -hex 32` para gerar. |
| `POCKET_NOC_PORT` | Não | `9443` | Porta de bind (sempre em 127.0.0.1). |
| `SERVER_ROLE` | Não | `generic` | Define os probes do Watchdog. Opções: `wordpress`, `wordpress-python`, `erp`, `python-nextjs`, `database`, `generic`. |
| `NTFY_TOPIC` | Não | Derivado do secret | Tópico do ntfy.sh para notificações push. |
| `WATCHDOG_ENABLED` | Não | `true` | Liga/desliga o WatchdogEngine. |
| `WATCHDOG_INTERVAL_SECS` | Não | `30` | Intervalo entre verificações do Watchdog. |
| `WATCHDOG_MAX_FAILURES` | Não | `3` | Falhas consecutivas para abrir o Circuit Breaker. |
| `WATCHDOG_COOLDOWN_SECS` | Não | `300` | Tempo (segundos) que o Circuit Breaker fica aberto antes de tentar HalfOpen. |
| `WATCHDOG_WEBHOOK_URL` | Não | — | URL opcional para receber eventos do Watchdog via HTTP POST. |

**Exemplo de geração do secret:**
```bash
openssl rand -hex 32
# Saída: a3f8d2c1e4b5f67890abcdef1234567890abcdef1234567890abcdef12345678
```

---

## 3. Serviço Systemd (Produção)

O arquivo de unidade em `agent/systemd/pocket-noc-agent.service` já está configurado com hardening de segurança. Para instalar:

```bash
# Cria usuário dedicado (não root)
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pocketnoc

# Instala o binário
sudo cp target/x86_64-unknown-linux-musl/release/pocket-noc-agent /usr/local/bin/
sudo chmod +x /usr/local/bin/pocket-noc-agent

# Instala o serviço
sudo cp agent/systemd/pocket-noc-agent.service /etc/systemd/system/

# Ativa e inicia
sudo systemctl daemon-reload
sudo systemctl enable --now pocket-noc-agent

# Verifica status
sudo systemctl status pocket-noc-agent
```

**Conteúdo do arquivo de serviço (`pocket-noc-agent.service`):**

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

# Limites de recursos (evita que o monitor impacte o servidor monitorado)
MemoryMax=128M
CPUQuota=5%

# Capabilities mínimas necessárias (em vez de root)
AmbientCapabilities=CAP_KILL CAP_NET_ADMIN
CapabilityBoundingSet=CAP_KILL CAP_NET_ADMIN

[Install]
WantedBy=multi-user.target
```

---

## 4. Configurando o Controller (Android)

### Arquivo local.properties

No diretório `controller/`, crie ou edite o arquivo `local.properties`:

```properties
# Servidor 1
POCKET_NOC_SERVER_1=ip.do.seu.servidor
POCKET_NOC_SERVER_NAME_1=Meu_Prod_Server
SSH_USER_1=root
SSH_HOST_1=ip.do.seu.servidor

# Chave SSH privada (Ed25519 recomendado)
SSH_KEY_CONTENT_GLOBAL=-----BEGIN OPENSSH PRIVATE KEY-----\nsua-chave-aqui\n-----END OPENSSH PRIVATE KEY-----

# Segredo JWT (deve ser IDÊNTICO ao POCKET_NOC_SECRET do servidor)
POCKET_NOC_SECRET=seu-segredo-de-no-minimo-32-caracteres
```

### Build e Instalação

```bash
cd controller

# Debug (desenvolvimento)
./gradlew assembleDebug

# Release (produção)
./gradlew assembleRelease
```

---

## 5. Troubleshooting

**"Erro de Conexão (SSH Tunnel Failure)"**
- Verifique se o IP do servidor está correto
- Confirme que a porta 22 (SSH) está aberta no firewall do provedor de cloud
- Teste a conexão SSH manualmente: `ssh usuario@ip`

**"401 Unauthorized"**
- O `POCKET_NOC_SECRET` no Android não bate com o definido no servidor
- O token pode ter expirado (padrão: 1 hora) — gere um novo no app

**"Logs não aparecem"**
- O usuário `pocketnoc` precisa ter permissão para ler o journal: `sudo usermod -aG systemd-journal pocketnoc`

**"Watchdog não está remediando"**
- Verifique se o Circuit Breaker está aberto: `GET /watchdog/breakers`
- Se estiver aberto, aguarde o cooldown ou resete manualmente: `POST /watchdog/reset`
- Confirme que `WATCHDOG_ENABLED=true` no ambiente do serviço

**Verificar logs do agente:**
```bash
journalctl -u pocket-noc-agent -f
journalctl -u pocket-noc-agent -n 50 --no-pager
```

---

*Escrito com rigor técnico para o projeto de Engenharia de Software.*
