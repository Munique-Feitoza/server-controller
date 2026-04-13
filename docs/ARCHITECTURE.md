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
    subgraph android["📱 App Android — Controller"]
        direction TB
        UI["🎨 UI Layer<br/><i>Jetpack Compose</i>"]
        VM["🧩 ViewModels<br/><i>MVVM</i>"]
        REPO["🗄️ ServerRepository"]
        API_SVC["🌐 AgentApiService<br/><i>Retrofit</i>"]
        DASH_SVC["📊 DashboardApiService"]
        SSH["🔐 SshTunnelManager<br/><i>JSch</i>"]
        DB[("💾 Room Database")]
        SEC_STORE[("🔑 SecureTokenManager<br/><i>EncryptedSharedPrefs</i>")]
        BIO["👆 BiometricAuthManager"]

        UI --> VM --> REPO
        REPO --> API_SVC & DASH_SVC & DB
        API_SVC --> SSH
        SEC_STORE --> SSH
        BIO --> UI
    end

    subgraph agent["🦀 Agente Rust — Por Servidor"]
        direction TB
        MAIN["⚙️ main.rs<br/><i>Axum + Tokio</i>"]
        MW["🛡️ Middleware Stack<br/><i>JWT · Rate Limit · Security</i>"]
        HANDLERS["🔌 API Handlers<br/><i>25+ endpoints</i>"]
        TELEM["📈 TelemetryCollector<br/><i>Cache L1 · 5s</i>"]
        WATCHDOG["🐕 WatchdogEngine<br/><i>Loop · 30s</i>"]
        ALERT["🚨 AlertManager<br/><i>Dedup · 30min</i>"]
        SECURITY["🕵️ ThreatTracker<br/><i>Honeypot · ZipBomb</i>"]
        AUDIT["📜 AuditLog<br/><i>Ring Buffer · 1000</i>"]
        CMD["⚡ CommandExecutor<br/><i>Whitelist</i>"]
        NOTIF["📣 ntfy.sh Client<br/><i>Retry · Backoff</i>"]

        MAIN --> MW --> HANDLERS
        HANDLERS --> TELEM & WATCHDOG & SECURITY & AUDIT & CMD
        TELEM --> ALERT --> NOTIF
        WATCHDOG --> NOTIF
    end

    subgraph kernel["🐧 Linux Kernel / Sistema"]
        direction LR
        PROC["/proc/stat<br/>/proc/meminfo"]
        SYS["/sys/class/hwmon"]
        SYSTEMD["systemctl"]
        IPTABLES["iptables"]
        JOURNAL["journalctl"]
    end

    SSH ==>|"localhost:9443<br/>TLS + JWT"| MW

    TELEM --> PROC & SYS
    WATCHDOG --> SYSTEMD
    SECURITY --> IPTABLES
    HANDLERS --> JOURNAL

    classDef androidNode fill:#7c3aed,stroke:#a78bfa,stroke-width:2px,color:#f5f3ff
    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef kernelNode fill:#334155,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9
    classDef storeNode fill:#0f766e,stroke:#5eead4,stroke-width:2px,color:#f0fdfa

    class UI,VM,REPO,API_SVC,DASH_SVC,SSH,BIO androidNode
    class DB,SEC_STORE storeNode
    class MAIN,MW,HANDLERS,TELEM,WATCHDOG,ALERT,SECURITY,AUDIT,CMD,NOTIF rustNode
    class PROC,SYS,SYSTEMD,IPTABLES,JOURNAL kernelNode
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
graph LR
    subgraph src["🦀 agent/src/"]
        main["⚙️ main.rs<br/><i>Entry point + server init</i>"]
        lib["📦 lib.rs<br/><i>Module declarations</i>"]

        subgraph apiPkg["🔌 api/"]
            handlers["handlers.rs<br/><i>25+ REST endpoints</i>"]
            middleware["🛡️ middleware.rs<br/><i>JWT + Security</i>"]
            rate_limit["rate_limit.rs<br/><i>Rate limiting por IP</i>"]
            websocket["websocket.rs<br/><i>WebSocket support</i>"]
        end

        subgraph telemPkg["📈 telemetry/"]
            telem_mod["mod.rs<br/><i>TelemetryCollector</i>"]
            cpu["cpu.rs"]
            memory["memory.rs"]
            disk["disk.rs"]
            temperature["temperature.rs"]
            network["network.rs"]
            security_tel["security.rs"]
            processes["processes.rs"]
            alerts["🚨 alerts.rs<br/><i>AlertManager</i>"]
            phpfpm["phpfpm.rs"]
            docker["docker.rs"]
            backup["backup.rs"]
            ssl["ssl.rs"]
        end

        subgraph wdPkg["🐕 watchdog/"]
            wd_mod["mod.rs<br/><i>WatchdogEngine</i>"]
            probes["probes.rs<br/><i>HTTP/TCP/Systemctl</i>"]
            cb["circuit_breaker.rs"]
            remediation["remediation.rs"]
            event["event.rs<br/><i>Ring Buffer 500</i>"]
        end

        subgraph secPkg["🕵️ security/"]
            sec_mod["mod.rs<br/><i>ThreatTracker</i>"]
            incidents["incidents.rs<br/><i>IncidentStore</i>"]
            intel["intel.rs<br/><i>GeoIP + Bot detect</i>"]
        end

        auth["🔑 auth/mod.rs<br/><i>JWT HS256</i>"]
        commands["⚡ commands/mod.rs<br/><i>Whitelist executor</i>"]
        audit["📜 audit.rs<br/><i>Ring Buffer 1000</i>"]
        services["services/mod.rs<br/><i>Systemd monitor</i>"]
        notifications["📣 notifications/<br/><i>ntfy.sh client</i>"]
    end

    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef storeNode fill:#0f766e,stroke:#5eead4,stroke-width:2px,color:#f0fdfa
    class main,lib,handlers,middleware,rate_limit,websocket,telem_mod,cpu,memory,disk,temperature,network,security_tel,processes,alerts,phpfpm,docker,backup,ssl,wd_mod,probes,cb,remediation,event,sec_mod,incidents,intel,auth,commands,audit,services,notifications rustNode
```

### Ciclo de Vida do Agente

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#334155", "actorBkg": "#1e293b", "actorBorder": "#475569", "actorTextColor": "#f8fafc", "signalColor": "#94a3b8", "signalTextColor": "#e2e8f0", "noteBkgColor": "#334155", "noteTextColor": "#f1f5f9", "noteBorderColor": "#64748b" } }}%%
sequenceDiagram
    participant Startup as ⚙️ main.rs
    participant JWT as 🔑 JwtConfig
    participant Server as 🔌 Axum Server
    participant Telem as 📈 TelemetryCollector
    participant Alert as 🚨 AlertManager
    participant WD as 🐕 WatchdogEngine
    participant SSL as 🔐 SSL Checker

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
    subgraph viewLayer["🎨 View Layer — Jetpack Compose"]
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
        Biometric["👆 BiometricGateScreen"]
        Splash["SplashScreen"]
    end

    subgraph vmLayer["🧩 ViewModel Layer"]
        DashVM["DashboardViewModel"]
        SecVM["SecurityViewModel"]
        WdVM["WatchdogViewModel"]
    end

    subgraph dataLayer["🗄️ Data Layer"]
        Repo["ServerRepository"]
        AgentAPI["🌐 AgentApiService<br/><i>Retrofit</i>"]
        DashAPI["📊 DashboardApiService"]
        RoomDB[("💾 AppDatabase<br/><i>Room</i>")]
        Prefs[("🔑 SecureTokenManager")]
        SSHTunnel["🔐 SshTunnelManager"]
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
    AgentAPI ==> SSHTunnel
    SSHTunnel --> Prefs

    classDef androidNode fill:#7c3aed,stroke:#a78bfa,stroke-width:2px,color:#f5f3ff
    classDef storeNode fill:#0f766e,stroke:#5eead4,stroke-width:2px,color:#f0fdfa
    class Dashboard,ServerList,ServerDetails,Security,Watchdog,PhpFpm,SSL,Actions,Processes,Logs,Alerts,History,AuditLog,Config,Export,Login,Biometric,Splash,DashVM,SecVM,WdVM,Repo,AgentAPI,DashAPI,SSHTunnel androidNode
    class RoomDB,Prefs storeNode
```

### Diagrama de Navegação

A Dashboard expõe **todas** as telas de deep-dive diretamente pelo menu hamburger (antes elas só eram acessíveis passando por `ServerListScreen → ServerDetailsScreen`, o que deixava várias telas órfãs). `WatchdogScreen` está embedded como aba na Dashboard e **não** aparece no menu (evita duplicação).

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
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#334155", "actorBkg": "#1e293b", "actorBorder": "#475569", "actorTextColor": "#f8fafc", "signalColor": "#94a3b8", "signalTextColor": "#e2e8f0", "noteBkgColor": "#334155", "noteTextColor": "#f1f5f9", "noteBorderColor": "#64748b" } }}%%
sequenceDiagram
    participant User as 📱 App Android
    participant SSH as 🔐 SSH Tunnel (JSch)
    participant MW as 🛡️ Middleware Stack
    participant Handler as 🔌 GET /telemetry
    participant Cache as 📈 TelemetryCollector
    participant Kernel as 🐧 Linux Kernel
    participant Ntfy as 📣 ntfy.sh

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
flowchart TD
    A["⏱ Loop 30s"] --> B["🐕 Seleciona probes por SERVER_ROLE"]

    B --> C{"Tipo de Probe"}
    C -->|HTTP| D["🌐 GET endpoint + timeout"]
    C -->|TCP| E["🔌 Socket connect + timeout"]
    C -->|Systemctl| F["⚙️ systemctl show → ActiveState"]

    D --> G{"Resultado"}
    E --> G
    F --> G

    G -->|"✅ Sucesso"| H["CircuitBreaker.record_success()"]
    G -->|"❌ Falha"| I["CircuitBreaker.record_failure()"]

    H --> J{"Estado anterior era Open?"}
    J -->|Sim| K["Transição → HalfOpen → Closed"]
    K --> L["🔔 Notifica: serviço recuperado"]
    J -->|Não| M["Nenhuma ação"]

    I --> N{"failures ≥ max_failures?"}
    N -->|Não| O["⚠️ Evento: Failing"]
    N -->|Sim| P{"CircuitBreaker já Open?"}

    P -->|Não| Q["Transição → Open"]
    Q ==> R["🚨 Notifica ntfy (1x)"]
    Q --> S["Bloqueia remediações por cooldown_secs"]
    P -->|Sim| T["Aguarda cooldown expirar"]

    O --> U["⚡ RemediationEngine"]
    U --> V["systemctl restart <service>"]
    V --> W["📜 Registra WatchdogEvent no ring buffer"]

    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef alertNode fill:#991b1b,stroke:#f87171,stroke-width:2px,color:#fef2f2
    classDef kernelNode fill:#334155,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9
    classDef externalNode fill:#475569,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9

    class A,B,H,I,J,K,L,M,N,O,P,Q,S,T,U,W rustNode
    class R alertNode
    class D,E,F,V kernelNode
    class C,G externalNode
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
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#334155", "actorBkg": "#1e293b", "actorBorder": "#475569", "actorTextColor": "#f8fafc", "signalColor": "#94a3b8", "signalTextColor": "#e2e8f0", "noteBkgColor": "#334155", "noteTextColor": "#f1f5f9", "noteBorderColor": "#64748b" } }}%%
sequenceDiagram
    participant Attacker as 🤖 Atacante
    participant MW as 🛡️ Middleware
    participant Tracker as 🕵️ ThreatTracker
    participant IPTables as 🔥 iptables
    participant Store as 📜 IncidentStore

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
graph LR
    subgraph runtime["🦀 Tokio Runtime (multi-thread)"]
        HTTP["🔌 HTTP Server<br/><i>Axum</i>"]
        AlertLoop["🚨 Alert Loop<br/><i>60s spawn</i>"]
        WDLoop["🐕 Watchdog Loop<br/><i>30s spawn</i>"]
        SSLLoop["🔐 SSL Check Loop<br/><i>6h spawn</i>"]
    end

    subgraph shared["💾 Estado Compartilhado — Arc&lt;Mutex&lt;T&gt;&gt;"]
        TC[("📈 TelemetryCollector<br/><i>Cache L1 + sysinfo</i>")]
        AM[("🚨 AlertManager<br/><i>Thresholds + cooldowns</i>")]
        WES[("🐕 WatchdogEventStore<br/><i>Ring buffer 500</i>")]
        AL[("📜 AuditLog<br/><i>Ring buffer 1000</i>")]
        TT[("🕵️ ThreatTracker<br/><i>Contadores por IP</i>")]
    end

    HTTP --> TC
    HTTP --> AM
    HTTP --> WES
    HTTP --> AL
    HTTP --> TT

    AlertLoop --> TC
    AlertLoop --> AM

    WDLoop --> WES

    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef storeNode fill:#0f766e,stroke:#5eead4,stroke-width:2px,color:#f0fdfa
    class HTTP,AlertLoop,WDLoop,SSLLoop rustNode
    class TC,AM,WES,AL,TT storeNode
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
    subgraph pocket["🦀 Pocket NOC Agent"]
        Agent["⚙️ Agente Rust"]
    end

    subgraph erp["📊 Dashboard ERP — FastAPI"]
        Webhook["POST /webhook/security"]
        Incidents["GET /security/incidents"]
    end

    subgraph ntfySg["📣 ntfy.sh"]
        Push["🔔 Push Notifications"]
    end

    subgraph cf["☁️ Cloudflare"]
        WAF["🛡️ WAF Rules"]
        DNS["DNS Management"]
    end

    subgraph linux["🐧 Linux"]
        IPT["🔥 iptables"]
        Systemd["⚙️ systemctl"]
        Journal["📜 journalctl"]
    end

    Dashboard_ERP["📊 Dashboard ERP"] -->|"Incidentes de segurança"| Webhook
    Agent ==>|"Alertas (CPU, RAM, Disco)"| Push
    Agent -->|"Bloqueio de IP"| IPT
    Agent -->|"Restart de serviços"| Systemd
    Agent -->|"Leitura de logs"| Journal
    Agent -.->|"Futuro: ban via API"| WAF

    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef kernelNode fill:#334155,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9
    classDef externalNode fill:#475569,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9

    class Agent rustNode
    class Webhook,Incidents,Push,WAF,DNS,Dashboard_ERP externalNode
    class IPT,Systemd,Journal kernelNode
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
