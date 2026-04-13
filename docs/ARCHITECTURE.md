# Arquitetura do Sistema — Pocket NOC

> Documentação técnica da arquitetura distribuída do Pocket NOC.  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Diagrama de Componentes](#diagrama-de-componentes)
3. [Stacks Tecnológicas](#stacks-tecnológicas)
4. [Arquitetura do Agente (Rust)](#arquitetura-do-agente-rust)
5. [Arquitetura do Controller (Android)](#arquitetura-do-controller-android)
6. [Fluxo de Dados de Telemetria](#fluxo-de-dados-de-telemetria)
7. [WatchdogEngine — Auto-Remediação](#watchdogengine--auto-remediação)
8. [Sistema de Segurança Ativa](#sistema-de-segurança-ativa)
9. [Modelo de Concorrência](#modelo-de-concorrência)
10. [Integrações Externas](#integrações-externas)
11. [Decisões de Engenharia](#decisões-de-engenharia)

---

## Visão Geral

O Pocket NOC segue um modelo de **Agente Distribuído** com comunicação direta entre o dispositivo móvel e os servidores monitorados. Diferente de soluções tradicionais que dependem de um servidor central (SaaS), a arquitetura elimina pontos únicos de falha e dependências externas desnecessárias.

Cada servidor de produção executa uma instância independente do agente Rust, que expõe uma API REST acessível exclusivamente via túnel SSH. O app Android atua como Controller — conecta-se a cada agente individualmente, agrega dados e permite ações remotas.

```mermaid
C4Context
    title Diagrama de Contexto — Pocket NOC

    Person(admin, "Administradora", "Engenheira de infraestrutura")

    System(android, "Pocket NOC Controller", "App Android nativo (Kotlin/Compose)")

    System_Boundary(servers, "Servidores de Produção") {
        System(agent1, "Agente Rust #1", "server-1")
        System(agent2, "Agente Rust #2", "server-2")
        System(agent3, "Agente Rust #3", "server-3")
        System(agent4, "Agente Rust #4", "server-4")
    }

    System_Ext(dashboard, "Dashboard ERP", "FastAPI + Next.js")
    System_Ext(ntfy, "ntfy.sh", "Push notifications")
    System_Ext(cloudflare, "Cloudflare API", "WAF e DNS")

    Rel(admin, android, "Opera via", "Biometria + UI")
    Rel(android, agent1, "SSH Tunnel + JWT", "REST API")
    Rel(android, agent2, "SSH Tunnel + JWT", "REST API")
    Rel(android, agent3, "SSH Tunnel + JWT", "REST API")
    Rel(android, agent4, "SSH Tunnel + JWT", "REST API")
    Rel(dashboard, agent1, "Webhook", "POST /webhook/security")
    Rel(agent1, ntfy, "Push alerts", "HTTPS")
    Rel(agent1, cloudflare, "Block IP", "API")
```

---

## Diagrama de Componentes

```mermaid
graph TB
    subgraph "App Android — Controller"
        UI["UI Layer<br/>(Jetpack Compose)"]
        VM["ViewModels<br/>(MVVM)"]
        REPO["ServerRepository"]
        API_SVC["AgentApiService<br/>(Retrofit)"]
        DASH_SVC["DashboardApiService"]
        SSH["SshTunnelManager<br/>(JSch)"]
        DB["Room Database"]
        SEC_STORE["SecureTokenManager<br/>(EncryptedSharedPrefs)"]
        BIO["BiometricAuthManager"]

        UI --> VM
        VM --> REPO
        REPO --> API_SVC
        REPO --> DASH_SVC
        REPO --> DB
        API_SVC --> SSH
        SEC_STORE --> SSH
        BIO --> UI
    end

    subgraph "Agente Rust — Por Servidor"
        MAIN["main.rs<br/>(Axum + Tokio)"]
        MW["Middleware Stack<br/>(JWT + Rate Limit + Security)"]
        HANDLERS["API Handlers<br/>(25+ endpoints)"]
        TELEM["TelemetryCollector<br/>(Cache L1 5s)"]
        WATCHDOG["WatchdogEngine<br/>(Loop 30s)"]
        ALERT["AlertManager<br/>(Dedup 30min)"]
        SECURITY["ThreatTracker<br/>(Honeypot + ZipBomb)"]
        AUDIT["AuditLog<br/>(Ring Buffer 1000)"]
        CMD["CommandExecutor<br/>(Whitelist)"]
        NOTIF["ntfy.sh Client<br/>(Retry + Backoff)"]

        MAIN --> MW --> HANDLERS
        HANDLERS --> TELEM
        HANDLERS --> WATCHDOG
        HANDLERS --> SECURITY
        HANDLERS --> AUDIT
        HANDLERS --> CMD
        TELEM --> ALERT
        ALERT --> NOTIF
        WATCHDOG --> NOTIF
    end

    SSH -->|"localhost:9443"| MW

    subgraph "Linux Kernel / Sistema"
        PROC["/proc/stat<br/>/proc/meminfo"]
        SYS["/sys/class/hwmon"]
        SYSTEMD["systemctl"]
        IPTABLES["iptables"]
        JOURNAL["journalctl"]
    end

    TELEM --> PROC
    TELEM --> SYS
    WATCHDOG --> SYSTEMD
    SECURITY --> IPTABLES
    HANDLERS --> JOURNAL
```

---

## Stacks Tecnológicas

| Camada | Tecnologia | Motivação |
|:---|:---|:---|
| **Agente** | Rust + Axum + Tokio | Zero-cost abstractions, sem GC, binário estático ~4 MB, footprint < 15 MB RAM |
| **Mobile** | Kotlin + Jetpack Compose + Hilt | UI reativa moderna, DI nativa, performance nativa Android |
| **Persistência Mobile** | Room + DataStore + EncryptedSharedPrefs | Gestão local de estado com credenciais criptografadas via Android KeyStore |
| **Comunicação** | SSH Tunneling (JSch) + JWT HS256 | Bicamada de segurança — criptografia de transporte + autenticação de aplicação |
| **Notificações** | ntfy.sh | Push notifications sem necessidade de FCM ou servidor proprietário |
| **CI/CD** | GitHub Actions | Pipelines automatizadas para Rust (clippy, test, build) e Android (lint, test, APK) |
| **Dashboard ERP** | FastAPI + PostgreSQL | Integração com sistema de gestão existente para inteligência de segurança |

---

## Arquitetura do Agente (Rust)

### Diagrama de Pacotes

```mermaid
graph LR
    subgraph "agent/src/"
        main["main.rs<br/><i>Entry point + server init</i>"]
        lib["lib.rs<br/><i>Module declarations</i>"]

        subgraph "api/"
            handlers["handlers.rs<br/><i>25+ REST endpoints</i>"]
            middleware["middleware.rs<br/><i>JWT + Security</i>"]
            rate_limit["rate_limit.rs<br/><i>Rate limiting por IP</i>"]
            websocket["websocket.rs<br/><i>WebSocket support</i>"]
        end

        subgraph "telemetry/"
            telem_mod["mod.rs<br/><i>TelemetryCollector</i>"]
            cpu["cpu.rs"]
            memory["memory.rs"]
            disk["disk.rs"]
            temperature["temperature.rs"]
            network["network.rs"]
            security_tel["security.rs"]
            processes["processes.rs"]
            alerts["alerts.rs<br/><i>AlertManager</i>"]
            phpfpm["phpfpm.rs"]
            docker["docker.rs"]
            backup["backup.rs"]
            ssl["ssl.rs"]
        end

        subgraph "watchdog/"
            wd_mod["mod.rs<br/><i>WatchdogEngine</i>"]
            probes["probes.rs<br/><i>HTTP/TCP/Systemctl</i>"]
            cb["circuit_breaker.rs"]
            remediation["remediation.rs"]
            event["event.rs<br/><i>Ring Buffer 500</i>"]
        end

        subgraph "security/"
            sec_mod["mod.rs<br/><i>ThreatTracker</i>"]
            incidents["incidents.rs<br/><i>IncidentStore</i>"]
            intel["intel.rs<br/><i>GeoIP + Bot detect</i>"]
        end

        auth["auth/mod.rs<br/><i>JWT HS256</i>"]
        commands["commands/mod.rs<br/><i>Whitelist executor</i>"]
        audit["audit.rs<br/><i>Ring Buffer 1000</i>"]
        services["services/mod.rs<br/><i>Systemd monitor</i>"]
        notifications["notifications/<br/><i>ntfy.sh client</i>"]
    end
```

### Ciclo de Vida do Agente

```mermaid
sequenceDiagram
    participant Startup as main.rs
    participant JWT as JwtConfig
    participant Server as Axum Server
    participant Telem as TelemetryCollector
    participant Alert as AlertManager
    participant WD as WatchdogEngine
    participant SSL as SSL Checker

    Startup->>JWT: Carrega POCKET_NOC_SECRET (≥32 bytes)
    JWT-->>Startup: Config validada

    Startup->>Telem: Arc<Mutex<TelemetryCollector>>::new()
    Startup->>Alert: Arc<Mutex<AlertManager>>::new()
    Startup->>WD: WatchdogEngine::new(SERVER_ROLE)

    par Background Tasks
        Startup->>Alert: tokio::spawn(alert_loop - 60s)
        Startup->>WD: tokio::spawn(watchdog_loop - 30s)
        Startup->>SSL: tokio::spawn(ssl_check_loop - 6h)
    end

    Startup->>Server: axum::serve(127.0.0.1:9443)
    Note over Server: Middleware: RateLimit → Security → JWT → Logging → CORS

    loop Requisições
        Server->>Server: Processa via handlers
    end
```

---

## Arquitetura do Controller (Android)

### Padrão MVVM

```mermaid
graph TB
    subgraph "View Layer — Jetpack Compose"
        Dashboard["DashboardScreen"]
        ServerList["ServerListScreen"]
        ServerDetails["ServerDetailsScreen"]
        Security["SecurityDashboardScreen"]
        Watchdog["WatchdogScreen"]
        PhpFpm["PhpFpmScreen"]
        SSL["SslCheckScreen"]
        Actions["ActionCenterScreen"]
        Processes["ProcessExplorerScreen"]
        Logs["LogViewerScreen"]
        Alerts["AlertSettingsScreen"]
        History["AlertHistoryScreen"]
        AuditLog["AuditLogScreen"]
        Config["AgentConfigScreen"]
        Export["ExportScreen"]
        Login["LoginScreen"]
        Biometric["BiometricGateScreen"]
        Splash["SplashScreen"]
    end

    subgraph "ViewModel Layer"
        DashVM["DashboardViewModel"]
        SecVM["SecurityViewModel"]
        WdVM["WatchdogViewModel"]
    end

    subgraph "Data Layer"
        Repo["ServerRepository"]
        AgentAPI["AgentApiService<br/>(Retrofit)"]
        DashAPI["DashboardApiService"]
        RoomDB["AppDatabase<br/>(Room)"]
        Prefs["SecureTokenManager"]
        SSHTunnel["SshTunnelManager"]
    end

    Dashboard --> DashVM
    ServerDetails --> DashVM
    Security --> SecVM
    Watchdog --> WdVM

    DashVM --> Repo
    SecVM --> Repo
    WdVM --> Repo

    Repo --> AgentAPI
    Repo --> DashAPI
    Repo --> RoomDB
    AgentAPI --> SSHTunnel
    SSHTunnel --> Prefs
```

### Diagrama de Navegação

A Dashboard expõe **todas** as telas de deep-dive diretamente pelo menu hamburger (antes elas só eram acessíveis passando por `ServerListScreen → ServerDetailsScreen`, o que deixava várias telas órfãs). `WatchdogScreen` está embedded como aba na Dashboard e **não** aparece no menu (evita duplicação).

```mermaid
stateDiagram-v2
    [*] --> Splash
    Splash --> Login: App inicializado
    Login --> Dashboard: JWT válido

    Dashboard --> AlertHistory: Menu — Histórico
    Dashboard --> Export: Menu — Exportar
    Dashboard --> SecurityDashboard: Menu — Segurança
    Dashboard --> PhpFpm: Menu — PHP-FPM (servidor ativo)
    Dashboard --> SslCheck: Menu — SSL Monitor
    Dashboard --> ServerDetails: Menu — Análise Completa
    Dashboard --> ProcessExplorer: Menu — Processos
    Dashboard --> ActionCenter: Menu — Ações
    Dashboard --> LogViewer: Menu — Logs
    Dashboard --> AuditLog: Menu — Auditoria
    Dashboard --> AgentConfig: Menu — Config Agente
    Dashboard --> AlertSettings: Menu — Config Alertas
    Dashboard --> WatchdogTab: Aba embedded (não é rota)

    ServerDetails --> ActionCenter: Ações
    ServerDetails --> ProcessExplorer: Processos
    ServerDetails --> LogViewer: Logs
    ServerDetails --> AuditLog: Auditoria
    ServerDetails --> AgentConfig: Config
```

---

## Fluxo de Dados de Telemetria

```mermaid
sequenceDiagram
    participant User as App Android
    participant SSH as SSH Tunnel (JSch)
    participant MW as Middleware Stack
    participant Handler as GET /telemetry
    participant Cache as TelemetryCollector
    participant Kernel as Linux Kernel
    participant Ntfy as ntfy.sh

    User->>SSH: GET /telemetry (JWT Bearer)
    SSH->>MW: Forward → localhost:9443
    MW->>MW: Rate limit check (60/min)
    MW->>MW: JWT validation (HS256)
    MW->>MW: Honeypot check (path seguro)
    MW->>Handler: Request autorizada

    Handler->>Cache: get_telemetry()
    alt Cache válido (TTL < 5s)
        Cache-->>Handler: JSON do cache L1
    else Cache expirado
        Cache->>Kernel: /proc/stat (CPU por core)
        Cache->>Kernel: /proc/meminfo (RAM/Swap)
        Cache->>Kernel: /sys/class/hwmon (Temperatura)
        Cache->>Kernel: /proc/diskstats (Disco I/O)
        Cache->>Kernel: systemctl show (Serviços)
        Kernel-->>Cache: Raw metrics
        Cache->>Cache: Atualiza cache L1
        Cache-->>Handler: JSON atualizado
    end

    Handler-->>MW: 200 OK + SystemTelemetry
    MW->>MW: Registra no AuditLog
    MW-->>SSH: Response cifrada
    SSH-->>User: JSON (StateFlow → Compose)

    Note over Cache,Ntfy: Loop Background (60s) — independente das requisições
    loop A cada 60 segundos
        Cache->>Kernel: Coleta telemetria
        Cache->>Cache: Analisa thresholds
        alt Alerta novo (cooldown 30min expirado)
            Cache->>Ntfy: POST /pocket_noc_XXXX (prioridade 4-5)
        end
    end
```

---

## WatchdogEngine — Auto-Remediação

### Diagrama de Estados do Circuit Breaker

```mermaid
stateDiagram-v2
    [*] --> Closed: Estado inicial

    Closed --> Closed: Probe OK → reset contador
    Closed --> Open: failures ≥ max_failures (3)

    Open --> Open: Dentro do cooldown (300s)
    Open --> HalfOpen: Cooldown expirado

    HalfOpen --> Closed: Probe OK → serviço recuperado
    HalfOpen --> Open: Probe falhou → volta ao cooldown

    note right of Closed
        Estado normal.
        Remediações permitidas.
    end note

    note right of Open
        Serviço considerado indisponível.
        Remediações bloqueadas.
        Notificação ntfy enviada (1x).
    end note

    note right of HalfOpen
        Testando recuperação.
        Uma probe de verificação.
    end note
```

### Fluxo Completo do Watchdog

```mermaid
flowchart TD
    A["⏱ Loop 30s"] --> B["Seleciona probes por SERVER_ROLE"]

    B --> C{"Tipo de Probe"}
    C -->|HTTP| D["GET endpoint + timeout"]
    C -->|TCP| E["Socket connect + timeout"]
    C -->|Systemctl| F["systemctl show → ActiveState"]

    D --> G{"Resultado"}
    E --> G
    F --> G

    G -->|"✓ Sucesso"| H["CircuitBreaker.record_success()"]
    G -->|"✗ Falha"| I["CircuitBreaker.record_failure()"]

    H --> J{"Estado anterior era Open?"}
    J -->|Sim| K["Transição → HalfOpen → Closed"]
    K --> L["📱 Notifica: serviço recuperado"]
    J -->|Não| M["Nenhuma ação"]

    I --> N{"failures ≥ max_failures?"}
    N -->|Não| O["Evento: Failing"]
    N -->|Sim| P{"CircuitBreaker já Open?"}

    P -->|Não| Q["Transição → Open"]
    Q --> R["📱 Notifica ntfy (1x)"]
    Q --> S["Bloqueia remediações por cooldown_secs"]
    P -->|Sim| T["Aguarda cooldown expirar"]

    O --> U["RemediationEngine"]
    U --> V["systemctl restart <service>"]
    V --> W["Registra WatchdogEvent no ring buffer"]

    style A fill:#1a1a2e,color:#fff
    style R fill:#e63946,color:#fff
    style L fill:#2a9d8f,color:#fff
```

### Roles de Servidor e Probes

| Role | Serviços Monitorados | Tipo de Probe |
|:---|:---|:---|
| `wordpress` | nginx, apache2, mariadb, php81-fpm + TCP 3306/80 | Systemctl + TCP |
| `wordpress-python` | wordpress + python-api HTTP /health | Systemctl + TCP + HTTP |
| `erp` | gunicorn, postgresql, nginx + python-api + nextjs | Systemctl + TCP + HTTP |
| `python-nextjs` | nginx, apache2, mariadb, postgresql@14-main, TCP 8000/3000/80 | Systemctl + TCP |
| `database` | mysql, postgresql, TCP 3306, TCP 5432 | Systemctl + TCP |
| `generic` (padrão) | **autodetecção**: veja abaixo | Systemctl + TCP |

**Role `generic` — autodetecção dinâmica:**

A partir da v1.x, o role `generic` descobre sozinho o que monitorar ao invés de receber uma lista fixa. Cada boot do agente executa:

1. `systemctl is-active nginx` ou `nginx` — adiciona probe + TCP 80 + TCP 443
2. `systemctl list-units --state=running` filtrando `php*fpm` — adiciona probe para **cada versão ativa** (ex: `php81-fpm`, `php83rc-fpm`, `php84rc-fpm`)
3. `mariadb`/`mysql` (mariadb vence se ambos) + TCP 3306 se ativo
4. `postgresql` e todas variantes `postgresql@*-main` + TCP 5432 se ativo
5. `docker`, `redis-server` — se ativos

No deploy atual (4 servidores), o role `generic` gera 9-13 probes por servidor ao invés dos 3-4 antigos. O probe de auto-monitoramento `pocket-noc-agent` foi removido (era código morto — se o agente morrer, ele não pode se reviver).

**Probes extras por env var:**

Independente do role escolhido, o agente aceita probes ad-hoc configurados no `.env`:

```bash
# Lista de servicos systemctl — ';'-separada
EXTRA_SERVICE_PROBES=postfix;dovecot;clamav-daemon

# Probes TCP — formato: nome|host|porta
EXTRA_TCP_PROBES=memcached-tcp|127.0.0.1|11211;elasticsearch|127.0.0.1|9200

# Probes HTTP — formato: nome|url|status_esperado|latencia_degraded_ms
EXTRA_HTTP_PROBES=erp-api|http://127.0.0.1:8000/health|200|2000
```

Parsing em [agent/src/watchdog/probes.rs](../agent/src/watchdog/probes.rs) (`load_extra_probes`).

---

## Sistema de Segurança Ativa

### Fluxo de Detecção e Resposta

```mermaid
sequenceDiagram
    participant Attacker as Atacante
    participant MW as Middleware
    participant Tracker as ThreatTracker
    participant IPTables as iptables
    participant Store as IncidentStore

    Attacker->>MW: GET /wp-admin (sem JWT)
    MW->>Tracker: is_honeypot_path("/wp-admin") → true
    Tracker->>Tracker: incrementa contador (IP)

    alt Tentativas < 5
        Tracker-->>MW: Monitora (não bloqueia)
        MW-->>Attacker: 404 Not Found
    else Tentativas ≥ 5
        Tracker->>Tracker: IP marcado como banido
        Tracker->>IPTables: iptables -I INPUT -s <IP> -j DROP
        MW->>MW: Gera zip bomb (50MB gzip)
        MW-->>Attacker: 200 OK + zip bomb
        Tracker->>Store: Registra incidente
    end

    Note over Tracker: IPs seguros (localhost, 10.*, 192.168.*, 172.16-17.*) nunca são banidos
    Note over Tracker: Janela de reset: 1 hora por IP
```

---

## Modelo de Concorrência

O agente utiliza o runtime assíncrono **Tokio** com compartilhamento de estado via `Arc<Mutex<T>>`.

```mermaid
graph LR
    subgraph "Tokio Runtime (multi-thread)"
        HTTP["HTTP Server<br/>(Axum)"]
        AlertLoop["Alert Loop<br/>(60s spawn)"]
        WDLoop["Watchdog Loop<br/>(30s spawn)"]
        SSLLoop["SSL Check Loop<br/>(6h spawn)"]
    end

    subgraph "Estado Compartilhado (Arc<Mutex<T>>)"
        TC["TelemetryCollector<br/><i>Cache L1 + sysinfo</i>"]
        AM["AlertManager<br/><i>Thresholds + cooldowns</i>"]
        WES["WatchdogEventStore<br/><i>Ring buffer 500</i>"]
        AL["AuditLog<br/><i>Ring buffer 1000</i>"]
        TT["ThreatTracker<br/><i>Contadores por IP</i>"]
    end

    HTTP --> TC
    HTTP --> AM
    HTTP --> WES
    HTTP --> AL
    HTTP --> TT

    AlertLoop --> TC
    AlertLoop --> AM

    WDLoop --> WES

    style TC fill:#264653,color:#fff
    style AM fill:#2a9d8f,color:#fff
    style WES fill:#e9c46a,color:#000
    style AL fill:#f4a261,color:#000
    style TT fill:#e76f51,color:#fff
```

**Decisões de concorrência:**

- `Arc<Mutex<TelemetryCollector>>` — protege cache + `sysinfo::System` entre o servidor HTTP e o loop de alertas
- `Arc<Mutex<AlertManager>>` — permite atualização dinâmica de thresholds via `POST /alerts/config` sem restart
- `Arc<Mutex<WatchdogEventStore>>` — ring buffer thread-safe para eventos (VecDeque, max 500)
- `Arc<Mutex<AuditLog>>` — ring buffer para registros de auditoria (max 1000)
- `tokio::task::spawn_blocking` — usado em probes do Watchdog (systemctl, TCP) para não bloquear o executor async

---

## Integrações Externas

```mermaid
graph TB
    subgraph "Pocket NOC Agent"
        Agent[Agente Rust]
    end

    subgraph "Dashboard ERP (FastAPI)"
        Webhook["POST /webhook/security"]
        Incidents["GET /security/incidents"]
    end

    subgraph "ntfy.sh"
        Push["Push Notifications"]
    end

    subgraph "Cloudflare"
        WAF["WAF Rules"]
        DNS["DNS Management"]
    end

    subgraph "Linux"
        IPT["iptables"]
        Systemd["systemctl"]
        Journal["journalctl"]
    end

    Dashboard_ERP["Dashboard ERP"] -->|"Incidentes de segurança"| Webhook
    Agent -->|"Alertas (CPU, RAM, Disco)"| Push
    Agent -->|"Bloqueio de IP"| IPT
    Agent -->|"Restart de serviços"| Systemd
    Agent -->|"Leitura de logs"| Journal
    Agent -.->|"Futuro: ban via API"| WAF
```

---

## Decisões de Engenharia

### Por que Rust no Agente?

Rust permite criar um binário estático (musl) que não exige runtime ou garbage collector, garantindo que o agente nunca entre em concorrência por recursos com os serviços monitorados. O sistema de tipos e o borrow checker eliminam bugs de memória e race conditions em tempo de compilação — fundamental para um serviço que roda com privilégios elevados (`CAP_KILL`, `CAP_NET_ADMIN`).

**Perfil de release otimizado:**
```toml
[profile.release]
opt-level = 3          # Otimização máxima
lto = true             # Link-time optimization
codegen-units = 1      # Compilação lenta, binário rápido
strip = true           # Remove símbolos de debug
```

Resultado: **~4 MB** binário estático, **< 15 MB** RAM, **< 0.5%** CPU.

### Por que Kotlin + Compose no Controller?

Jetpack Compose oferece UI declarativa e reativa que se integra nativamente com `StateFlow` do ViewModel — ideal para dashboards que atualizam a cada 30 segundos. Hilt (Dagger) fornece injeção de dependência com zero boilerplate, e a tipagem forte do Kotlin previne null pointer exceptions comuns em Java.

### Por que SSH Tunnel em vez de HTTPS direto?

A escolha de SSH tunneling em vez de expor o agente via HTTPS na internet foi deliberada:

1. **Zero superfície de ataque** — o agente não aparece em nenhum scan de porta
2. **Autenticação dupla** — chave SSH + JWT token
3. **Sem certificados TLS** — elimina complexidade de renovação de certificados
4. **Infraestrutura existente** — todos os servidores já têm SSH configurado

### Por que ntfy.sh em vez de FCM?

O ntfy.sh permite push notifications sem depender do Google Firebase, sem necessidade de conta Google, e funciona com qualquer cliente HTTP. O tópico é derivado automaticamente do secret JWT, eliminando configuração adicional.

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
