# Pocket NOC — Centro de Operações de Rede no Bolso

[![Rust](https://img.shields.io/badge/Rust-1.70%2B-orange?style=for-the-badge&logo=rust)](https://www.rust-lang.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green?style=for-the-badge&logo=android)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-GPL_v2-blue?style=for-the-badge&logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![CI Agent](https://img.shields.io/badge/CI-Rust-success?style=for-the-badge&logo=githubactions)](https://github.com/Munique-Feitoza/pocket-noc/actions)
[![CI Android](https://img.shields.io/badge/CI-Android-success?style=for-the-badge&logo=githubactions)](https://github.com/Munique-Feitoza/pocket-noc/actions)

Solução completa de **monitoramento**, **segurança** e **gestão de infraestrutura**. Agente em **Rust** rodando nos servidores (< 15 MB RAM, < 0.5% CPU) + app **Android** nativo em Kotlin/Jetpack Compose para controle remoto em tempo real.

---

## Arquitetura

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "fontFamily": "Inter, -apple-system, Segoe UI, sans-serif",
    "fontSize": "14px",
    "primaryColor": "#1e293b",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#334155",
    "lineColor": "#64748b",
    "clusterBkg": "#0f172a",
    "clusterBorder": "#334155"
  },
  "flowchart": { "curve": "basis", "padding": 20 }
}}%%
graph TB
    subgraph controller["📱 Controller Android"]
        APP["🎨 Jetpack Compose + MVVM<br/><i>17 telas · Dark/Light · Biometria · Widget</i>"]
    end

    subgraph secLayer["🛡️ Camada de Segurança"]
        SSH["🔐 SSH Tunnel<br/><i>AES-256 / ChaCha20</i>"]
        JWT["🔑 JWT Auth<br/><i>HS256, ≥32 bytes</i>"]
    end

    subgraph agents["🦀 Agentes Rust — por servidor"]
        AGENT["⚙️ Axum + Tokio<br/><i>25+ endpoints REST · ~4 MB binary</i>"]
        TELEM["📈 Telemetria<br/><i>CPU, RAM, Disco, Rede, Temp</i>"]
        WD["🐕 WatchdogEngine<br/><i>Auto-remediação</i>"]
        SEC["🕵️ Defesa Ativa<br/><i>Honeypot + ZIP bomb</i>"]
    end

    subgraph integrations["🌐 Integrações"]
        ERP["📊 Dashboard ERP<br/><i>FastAPI</i>"]
        NTFY["📣 ntfy.sh<br/><i>Push alerts</i>"]
        LINUX["🐧 Linux Kernel<br/><i>/proc, systemctl, iptables</i>"]
    end

    APP ==> SSH ==> JWT ==> AGENT
    AGENT --> TELEM --> LINUX
    AGENT --> WD --> LINUX
    AGENT --> SEC --> LINUX
    ERP -->|webhook| AGENT
    AGENT -->|alertas| NTFY

    classDef androidNode fill:#7c3aed,stroke:#a78bfa,stroke-width:2px,color:#f5f3ff
    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef kernelNode fill:#334155,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9
    classDef externalNode fill:#475569,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9
    class APP androidNode
    class SSH,JWT,AGENT,TELEM,WD,SEC rustNode
    class LINUX kernelNode
    class ERP,NTFY externalNode
```

---

## Funcionalidades

### Agente Rust

- **Telemetria completa**: CPU (por core), RAM/Swap, disco, rede (por interface), temperatura, processos, serviços systemd
- **WatchdogEngine**: detecta serviços caídos e reinicia automaticamente com circuit breaker (Closed/Open/HalfOpen)
- **Defesa ativa**: 30+ honeypot paths + ZIP bomb + auto-ban via iptables após 5 tentativas
- **Inteligência de ameaças**: geolocalização, ISP e classificação bot/humano de atacantes
- **PHP-FPM por site**: consumo de CPU/RAM por site WordPress (detecção automática Hosting)
- **Monitoramento Docker**: containers, status, portas
- **Certificados SSL**: verificação periódica (6h) com alertas de expiração
- **Backups**: status e idade dos backups
- **Alertas push**: notificações via ntfy.sh com deduplicação inteligente (cooldown 30min)
- **Métricas Prometheus**: endpoint `/metrics` para scraping
- **Audit log**: ring buffer de 1000 entradas com detalhes de cada requisição

### Controller Android

- **17 telas** com design system profissional (Material 3)
- **Dark/Light mode** com toggle manual
- **Biometria**: fingerprint/face para proteger acesso
- **Multi-servidor**: gerencia 4+ servidores simultaneamente
- **Layout adaptivo**: phone, tablet e foldable
- **SSH Tunnel**: gerenciamento automático de túneis (JSch)
- **Widget de home screen**: status do servidor na tela inicial
- **Exportação**: dados em CSV/JSON
- **Push notifications**: Firebase Cloud Messaging
- **Persistência local**: Room DB + EncryptedSharedPreferences

---

## API do Agente

Todas as rotas (exceto `/health`) requerem `Authorization: Bearer <JWT_TOKEN>`.

| Método | Rota | Descrição |
|:---|:---|:---|
| `GET` | `/health` | Health check (sem auth) |
| `GET` | `/telemetry` | Snapshot completo do sistema |
| `GET` | `/alerts` | Alertas ativos |
| `POST` | `/alerts/config` | Atualiza thresholds |
| `GET` | `/processes` | Top 10 processos |
| `DELETE` | `/processes/:pid` | Encerra processo |
| `GET` | `/logs` | Logs do journalctl |
| `GET` | `/services/:name` | Status de serviço |
| `GET` | `/commands` | Lista comandos whitelist |
| `POST` | `/commands/:id` | Executa comando |
| `POST` | `/security/block-ip` | Bloqueia IP via iptables |
| `GET` | `/security/incidents` | Incidentes de segurança |
| `POST` | `/webhook/security` | Recebe alertas do dashboard |
| `GET` | `/metrics` | Formato Prometheus |
| `GET` | `/phpfpm/pools` | PHP-FPM pools por site |
| `GET` | `/docker/containers` | Containers Docker |
| `GET` | `/ssl/check` | Certificados SSL |
| `GET` | `/backups/status` | Status de backups |
| `GET` | `/audit/logs` | Log de auditoria |
| `GET` | `/config` | Configuração do agente |
| `POST` | `/config` | Atualiza configuração |
| `GET` | `/watchdog/events` | Eventos do Watchdog |
| `GET` | `/watchdog/breakers` | Circuit Breakers |
| `POST` | `/watchdog/reset` | Reset dos breakers |

---

## Segurança

O sistema implementa **5 camadas de defesa independentes**:

| Camada | Mecanismo | Descrição |
|:---|:---|:---|
| **Perímetro** | Stealth Bind | Agente ouve apenas em `127.0.0.1` — invisível na internet |
| **Transporte** | SSH Tunnel | Criptografia AES-256/ChaCha20 via túnel SSH |
| **Aplicação** | JWT HS256 | Token com secret ≥ 32 bytes, expiração 1h |
| **Defesa Ativa** | Honeypot + ZIP bomb | 30+ paths falsos, auto-ban após 5 acessos |
| **Dados** | EncryptedSharedPrefs | Segredos criptografados via Android KeyStore |

---

## Performance

| Métrica | Valor |
|:---|:---|
| RAM do agente | 8-15 MB |
| CPU do agente (idle) | < 0.5% |
| Binário | ~4 MB (musl estático) |
| Ciclo Watchdog | 30 segundos |
| Ciclo Alertas | 60 segundos |
| Cache Telemetria | 5 segundos (L1) |
| Verificação SSL | 6 horas |

---

## Quick Start

### Deploy do Agente

```bash
# Deploy automatizado em todos os servidores
chmod +x deploy.sh
./deploy.sh
```

### Build do Controller

```bash
cd controller
./gradlew assembleDebug
./gradlew installDebug
```

---

## Documentação

| Documento | Descrição |
|:---|:---|
| [Arquitetura](./docs/ARCHITECTURE.md) | Design do sistema, diagramas UML, decisões de engenharia |
| [API Completa](./docs/API.md) | Referência de todos os 25+ endpoints com exemplos |
| [Segurança](./docs/SECURITY.md) | Modelo de ameaças, defesa em profundidade, matriz de controles |
| [Instalação](./docs/SETUP.md) | Guia completo de setup e deployment |
| [Modelos de Dados](./docs/DATA_MODELS.md) | Diagramas de classe UML (Rust + Kotlin) |
| [App Android](./docs/ANDROID_APP.md) | Arquitetura MVVM, navegação, design system |
| [Testes](./docs/TESTING.md) | Estratégia de testes e execução |
| [CI/CD e Deploy](./docs/DEPLOYMENT.md) | Pipelines GitHub Actions e processo de deploy |
| [Glossário](./docs/GLOSSARY.md) | Definições dos termos técnicos |
| [Changelog](./CHANGELOG.md) | Histórico de versões |

---

## Stack Tecnológica

| Camada | Tecnologia |
|:---|:---|
| Agente | Rust + Axum + Tokio + serde + sysinfo + procfs |
| Mobile | Kotlin + Jetpack Compose + Material 3 + Hilt |
| Networking | Retrofit 2 + OkHttp + JSch (SSH) |
| Persistência | Room + DataStore + EncryptedSharedPreferences |
| Notificações | ntfy.sh (agente) + Firebase Cloud Messaging (mobile) |
| CI/CD | GitHub Actions (Rust + Android + Release) |

---

## Licença

Este projeto é licenciado sob a [GNU General Public License v2.0](./LICENSE).

---

**Desenvolvido por [Munique Alves Pacheco Feitoza](https://github.com/Munique-Feitoza)**  
*Engenharia de Software | Análise e Desenvolvimento de Sistemas*
