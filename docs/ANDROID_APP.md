# Arquitetura do Controller Android — Pocket NOC

> Documentação técnica do app Android nativo (Kotlin + Jetpack Compose).  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Stack Tecnológica](#stack-tecnológica)
3. [Arquitetura MVVM](#arquitetura-mvvm)
4. [Injeção de Dependência (Hilt)](#injeção-de-dependência-hilt)
5. [Camada de Dados](#camada-de-dados)
6. [Camada de UI](#camada-de-ui)
7. [Navegação](#navegação)
8. [Design System](#design-system)
9. [SSH Tunneling](#ssh-tunneling)
10. [Segurança do App](#segurança-do-app)
11. [Background Workers](#background-workers)
12. [Estrutura de Diretórios](#estrutura-de-diretórios)

---

## Visão Geral

O Controller é um app Android nativo construído com Kotlin e Jetpack Compose, seguindo a arquitetura **MVVM (Model-View-ViewModel)**. Ele se conecta aos agentes Rust em cada servidor via túnel SSH, agrega telemetria, permite ações remotas e oferece uma experiência de monitoramento profissional em dispositivos móveis e tablets.

```mermaid
graph TB
    subgraph "Apresentação"
        COMPOSE["Jetpack Compose<br/>(17 telas)"]
        THEME["Design System<br/>(Material 3)"]
        NAV["Navigation Compose"]
    end

    subgraph "Lógica"
        VM["ViewModels<br/>(MVVM)"]
        HILT["Hilt DI"]
    end

    subgraph "Dados"
        REPO["ServerRepository"]
        RETROFIT["Retrofit 2<br/>(REST Client)"]
        ROOM["Room DB<br/>(SQLite)"]
        PREFS["EncryptedSharedPrefs"]
        SSH["JSch<br/>(SSH Tunnel)"]
    end

    COMPOSE --> VM
    VM --> REPO
    REPO --> RETROFIT
    REPO --> ROOM
    REPO --> PREFS
    RETROFIT --> SSH
```

---

## Stack Tecnológica

| Componente | Tecnologia | Versão |
|:---|:---|:---|
| **Linguagem** | Kotlin | 1.9+ |
| **UI Framework** | Jetpack Compose + Material 3 | Compose BOM |
| **Arquitetura** | MVVM | — |
| **Injeção de Dependência** | Hilt (Dagger) | 2.50 |
| **Networking** | Retrofit 2 + OkHttp 3 | — |
| **Banco de Dados** | Room (SQLite) | — |
| **Preferences** | DataStore + EncryptedSharedPrefs | — |
| **SSH** | JSch | 0.2.20 |
| **Biometria** | AndroidX Biometric | — |
| **Background** | WorkManager | — |
| **Min SDK** | 24 (Android 7.0) | — |
| **Target SDK** | 34 (Android 14) | — |

---

## Arquitetura MVVM

```mermaid
graph LR
    subgraph "View"
        S1["DashboardScreen"]
        S2["ServerDetailsScreen"]
        S3["...17 telas"]
    end

    subgraph "ViewModel"
        VM1["DashboardViewModel"]
        VM2["SecurityViewModel"]
        VM3["WatchdogViewModel"]
    end

    subgraph "Model"
        R["ServerRepository"]
        A["AgentApiService"]
        D["DashboardApiService"]
        DB["AppDatabase"]
    end

    S1 -->|"observa StateFlow"| VM1
    S2 -->|"observa StateFlow"| VM1
    S3 -->|"observa StateFlow"| VM2
    S3 -->|"observa StateFlow"| VM3

    VM1 -->|"chama suspend fun"| R
    VM2 -->|"chama suspend fun"| R
    VM3 -->|"chama suspend fun"| R

    R --> A
    R --> D
    R --> DB
```

### DashboardViewModel — Estado Principal

```mermaid
classDiagram
    class DashboardViewModel {
        -ServerRepository repository
        -SshTunnelManager sshManager
        +telemetryState: StateFlow~TelemetryUiState~
        +commandsState: StateFlow~CommandsUiState~
        +processesState: StateFlow~ProcessesUiState~
        +logsState: StateFlow~LogsUiState~
        +telemetryHistory: StateFlow~List~TelemetryHistoryEntity~~
        +alertHistory: StateFlow~List~AlertEntity~~
        +allServers: StateFlow~List~ServerEntity~~
        +alertThresholds: StateFlow~AlertThresholdConfig~
        +networkStatus: StateFlow~ConnectivityStatus~
        +fetchTelemetry(server: ServerEntity)
        +killProcess(pid: Int)
        +getServiceStatus(name: String)
        +executeCommand(id: String)
        +updateAlertConfig(config: AlertThresholdConfig)
        +blockIp(ip: String)
    }

    class TelemetryUiState {
        <<sealed class>>
        +Loading
        +Success(data: SystemTelemetry)
        +Error(message: String)
    }

    DashboardViewModel --> TelemetryUiState
```

---

## Injeção de Dependência (Hilt)

```mermaid
graph TB
    subgraph "Módulos Hilt"
        NM["NetworkModule<br/>@Provides Retrofit, OkHttp"]
        DM["DatabaseModule<br/>@Provides Room, DAOs"]
        SM["SecurityModule<br/>@Provides JwtUtils, BiometricAuth"]
    end

    subgraph "Componentes Injetados"
        API["AgentApiService"]
        DASH["DashboardApiService"]
        DB["AppDatabase"]
        JWT["JwtUtils"]
        BIO["BiometricAuthManager"]
        REPO["ServerRepository"]
    end

    NM --> API
    NM --> DASH
    DM --> DB
    SM --> JWT
    SM --> BIO

    API --> REPO
    DASH --> REPO
    DB --> REPO
```

---

## Camada de Dados

### ServerRepository

O `ServerRepository` é o ponto central de acesso a dados. Ele abstrai as fontes (API remota, banco local, preferências criptografadas) e expõe operações assíncronas via coroutines.

```mermaid
classDiagram
    class ServerRepository {
        -AgentApiService agentApi
        -DashboardApiService dashboardApi
        -AppDatabase database
        -SecureTokenManager tokenManager
        +getTelemetry(serverId) Result~SystemTelemetry~
        +getAlerts(serverId) Result~List~Alert~~
        +getProcesses(serverId) Result~List~ProcessInfo~~
        +killProcess(serverId, pid) Result~Boolean~
        +executeCommand(serverId, commandId) Result~CommandResult~
        +getWatchdogEvents(serverId) Result~List~WatchdogEvent~~
        +getAuditLogs(serverId) Result~List~AuditEntry~~
        +getDockerContainers(serverId) Result~List~DockerContainer~~
        +getSslCertificates(serverId) Result~List~SslCertificate~~
        +getPhpFpmPools(serverId) Result~List~PhpFpmPool~~
        +blockIp(serverId, ip) Result~Boolean~
        +getSecurityIncidents() Result~List~SecurityIncident~~
    }

    class AgentApiService {
        <<interface>>
        +getTelemetry() Response~SystemTelemetry~
        +getAlerts() Response~AlertsResponse~
        +getTopProcesses() Response~List~ProcessInfo~~
        +killProcess(pid) Response~Unit~
        +getServiceStatus(name) Response~ServiceInfo~
        +executeCommand(id) Response~CommandResult~
        +getWatchdogEvents() Response~WatchdogEventsResponse~
        +getAuditLogs() Response~List~AuditEntry~~
        +blockIp(request) Response~Unit~
    }

    class DashboardApiService {
        <<interface>>
        +getIncidents(token) Response~DashboardIncidentsResponse~
        +getIncidentStats(token) Response~DashboardStatsResponse~
    }

    ServerRepository --> AgentApiService
    ServerRepository --> DashboardApiService
```

### Persistência Local

```mermaid
graph LR
    subgraph "Room Database"
        DB["AppDatabase"]
        SD["ServerDao"]
        AD["AlertDao"]
        TD["TelemetryHistoryDao"]
    end

    subgraph "Encrypted Storage"
        STM["SecureTokenManager"]
        ESP["EncryptedSharedPreferences"]
        AKS["Android KeyStore"]
    end

    subgraph "DataStore"
        ADS["AlertThresholdDataStore"]
        SMS["SessionManager"]
    end

    DB --> SD
    DB --> AD
    DB --> TD
    STM --> ESP --> AKS
```

---

## Camada de UI

### Telas do App

| Tela | Arquivo | Descrição |
|:---|:---|:---|
| `SplashScreen` | `SplashScreen.kt` | Inicialização e branding |
| `LoginScreen` | `LoginScreen.kt` | Autenticação de usuário |
| `BiometricGateScreen` | `BiometricGateScreen.kt` | Proteção por biometria |
| `DashboardScreen` | `DashboardScreen.kt` | Visão geral com menu hamburger |
| `ServerListScreen` | `ServerListScreen.kt` | Lista de servidores configurados |
| `ServerDetailsScreen` | `ServerDetailsScreen.kt` | Deep dive por servidor |
| `ActionCenterScreen` | `ActionCenterScreen.kt` | Execução de comandos whitelist |
| `ProcessExplorerScreen` | `ProcessExplorerScreen.kt` | Top processos + kill by PID |
| `LogViewerScreen` | `LogViewerScreen.kt` | Visualizador journalctl em tempo real |
| `AlertSettingsScreen` | `AlertSettingsScreen.kt` | Configuração de thresholds |
| `AlertHistoryScreen` | `AlertHistoryScreen.kt` | Histórico de alertas |
| `WatchdogScreen` | `WatchdogScreen.kt` | Eventos e circuit breakers |
| `SecurityDashboardScreen` | `SecurityDashboardScreen.kt` | Incidentes do Dashboard ERP |
| `SslCheckScreen` | `SslCheckScreen.kt` | Status de certificados SSL |
| `PhpFpmScreen` | `PhpFpmScreen.kt` | PHP-FPM por site |
| `AuditLogScreen` | `AuditLogScreen.kt` | Log de auditoria da API |
| `AgentConfigScreen` | `AgentConfigScreen.kt` | Configuração remota do agente |
| `ExportScreen` | `ExportScreen.kt` | Exportação de dados (CSV/JSON) |

### ServerDetailsScreen — Análise Completa

Tela de "deep dive" por servidor, acessível via menu hamburger da Dashboard (**Análise Completa (Gráficos)**). Renderiza, na ordem:

1. **LiveStatusHeader** — status com pulso animado, último update
2. **ArcGauges** — CPU / RAM / DISCO
3. **TelemetryLineChart** — histórico de **até 24h**:
   - Fonte: `TelemetryHistoryEntity` via Room, `limit=2880` (24h × 30s)
   - Downsample para 200 buckets preservando o **MAX** de cada bucket
   - Eixo X com 4 ticks `HH:MM` distribuídos sobre o `firstTs..lastTs`
   - **Tap-to-inspect**: toque em qualquer ponto → linha vertical branca + marker magenta + label `DD/MM HH:MM · CPU X.X%` substituindo o título
4. **CpuPeaksCard** — top 3 picos de CPU das últimas 24h (supressão de adjacentes)
5. **TopSitesCpuCard** — top 5 pools PHP-FPM por CPU (via `/phpfpm/pools`)
6. SystemLoadCard (uptime + load avg)
7. **DiskUsageCard** — formata `< 1 GB` como MB, mostra filesystem, % com 1 casa decimal
8. NetworkCard
9. TemperatureCard (se houver sensor)
10. TopProcessesCard (top 5 processos com link pro ProcessExplorer)

**Limitação conhecida:** o histórico só cresce enquanto o app está ativo fazendo `fetchTelemetry()`. Para capturar picos que acontecem com o app fechado sem depender do worker periódico (que corre em frequência menor), seria necessário adicionar um ring buffer server-side no agente — feature planejada.

### Componentes Reutilizáveis

| Componente | Descrição |
|:---|:---|
| `Components.kt` | Cards, dialogs, status indicators, botões estilizados |
| `TelemetryLineChart` (em `Components.kt`) | Gráfico de linha CPU/RAM com histórico de 24h. Recebe `cpuSamples: List<Pair<Long, Float>>` e `ramSamples`. Faz downsample pra 200 buckets preservando o MAX (não perde picos). Eixo X com 4 ticks HH:MM. Suporta **tap-to-inspect**: tocar no gráfico mostra linha vertical + marker + label `DD/MM HH:MM · CPU X.X%`. |
| `CpuPeaksCard` (em `Components.kt`) | Top 3 picos de CPU nas últimas 24h com horário relativo (`2h atrás`) + clock (`03:47`). Suprime picos adjacentes (mínimo 10% da janela entre eles) pra não listar 3 amostras do mesmo evento. |
| `TopSitesCpuCard` (em `Components.kt`) | Top 5 sites (pools PHP-FPM) por CPU. Usa `/phpfpm/pools` do agente. Barra de progresso normalizada ao maior pool. |
| `DiskUsageCard` (em `ServerDetailsScreen.kt`) | Formata `< 1 GB` como MB, mostra filesystem (ext4/vfat) e % com 1 casa decimal. Corrigido erro anterior onde discos pequenos apareciam como `0/0GB`. |
| `AdaptiveLayout.kt` | Layout adaptivo — single-pane (phone) ↔ multi-pane (tablet) |
| `PocketNocTopBar.kt` | Top bar customizada com seletor de servidor |

---

## Navegação

### Diagrama de Rotas

A partir da v1.x, o `DashboardScreen` expõe as telas principais via menu hamburger — não há mais um "buraco" onde `ServerDetailsScreen` e suas filhas ficavam inalcançáveis. `WatchdogScreen` continua como aba embedded na própria Dashboard (não é rota separada do menu).

```mermaid
graph TD
    SPLASH["splash"] --> LOGIN["login"]
    LOGIN --> DASH["dashboard"]

    DASH -->|"menu: Historico"| ALERT_HIST["alert_history"]
    DASH -->|"menu: Exportar"| EXPORT["export"]
    DASH -->|"menu: Seguranca"| SECURITY["security_dashboard"]
    DASH -->|"menu: PHP-FPM"| PHPFPM["phpfpm/{serverId}"]
    DASH -->|"menu: SSL"| SSL["ssl_check/{serverId}"]
    DASH -->|"menu: Analise Completa"| DETAILS["server_details/{serverId}"]
    DASH -->|"menu: Processos"| PROCESSES["process_explorer/{serverId}"]
    DASH -->|"menu: Acoes"| ACTIONS["action_center/{serverId}"]
    DASH -->|"menu: Logs"| LOGS["log_viewer/{serverId}"]
    DASH -->|"menu: Auditoria"| AUDIT["audit_log/{serverId}"]
    DASH -->|"menu: Config Agente"| CONFIG["agent_config/{serverId}"]
    DASH -->|"menu: Config Alertas"| ALERT_SET["alert_settings"]
    DASH -.->|"aba embedded"| WATCHDOG_TAB["WATCHDOG (sub-tab)"]

    DETAILS --> ACTIONS
    DETAILS --> PROCESSES
    DETAILS --> LOGS
    DETAILS --> AUDIT
    DETAILS --> CONFIG
```

**Notas:**
- `ServerListScreen` e `BiometricGateScreen` existem no código mas são órfãos — sem entry point atual.
- `WatchdogScreen` está disponível apenas como aba dentro da Dashboard (removido do menu hamburger pra não duplicar).

### AppRoutes

Todas as rotas são definidas como `sealed class` em `AppRoutes.kt`, garantindo type-safety na navegação:

```kotlin
sealed class AppRoutes(val route: String) {
    object Splash : AppRoutes("splash")
    object BiometricGate : AppRoutes("biometric_gate")
    object Login : AppRoutes("login")
    object Dashboard : AppRoutes("dashboard")
    object ServerList : AppRoutes("server_list")
    object SecurityDashboard : AppRoutes("security_dashboard")
    object PhpFpm : AppRoutes("php_fpm")
    object SslCheck : AppRoutes("ssl_check")
    object Export : AppRoutes("export")
    // Rotas parametrizadas recebem {serverId}
    object ServerDetails : AppRoutes("server_details/{serverId}")
    object ActionCenter : AppRoutes("action_center/{serverId}")
    // ...
}
```

---

## Design System

### Tokens

```mermaid
graph LR
    subgraph "AppColors.kt"
        PRIMARY["Primary<br/>#1A1A2E"]
        SECONDARY["Secondary<br/>#16213E"]
        ACCENT["Accent<br/>#0F3460"]
        SUCCESS["Success<br/>#2A9D8F"]
        WARNING["Warning<br/>#E9C46A"]
        ERROR["Error<br/>#E63946"]
    end

    subgraph "Dimens.kt"
        XS["xs: 4dp"]
        SM["sm: 8dp"]
        MD["md: 16dp"]
        LG["lg: 24dp"]
        XL["xl: 32dp"]
    end

    subgraph "Shapes.kt"
        CARD["Card: 12dp"]
        BUTTON["Button: 8dp"]
        DIALOG["Dialog: 16dp"]
    end

    subgraph "Theme.kt"
        LIGHT["Light Theme"]
        DARK["Dark Theme"]
        ADAPTIVE["Adaptive Typography"]
    end
```

### Dark / Light Mode

O app suporta toggle manual de tema via `DashboardScreen`. O estado do tema persiste entre sessões via DataStore.

```mermaid
flowchart LR
    TOGGLE["Toggle button"] --> STATE["isDarkTheme: MutableState"]
    STATE --> THEME["PocketNOCTheme(darkTheme)"]
    THEME --> COLORS["AppColors.dark / AppColors.light"]
    COLORS --> COMPOSE["MaterialTheme(colorScheme)"]
```

### Layout Adaptivo

```mermaid
graph TB
    SCREEN{"Largura da tela"}
    SCREEN -->|"< 600dp"| PHONE["Single-pane<br/>(NavigationBar)"]
    SCREEN -->|"600-840dp"| TABLET["Multi-pane<br/>(NavigationRail)"]
    SCREEN -->|"> 840dp"| DESKTOP["Expanded<br/>(NavigationDrawer)"]
```

---

## SSH Tunneling

### Fluxo de Conexão

```mermaid
sequenceDiagram
    participant App as Controller
    participant STM as SecureTokenManager
    participant SSH as SshTunnelManager (JSch)
    participant Server as Servidor Remoto
    participant Agent as Agente Rust

    App->>STM: Recupera chave SSH privada
    STM-->>App: Ed25519/RSA key (decrypted)

    App->>SSH: connect(serverId, host, port, user, key)
    SSH->>SSH: JSch session config
    Note over SSH: Algorithms: ssh-ed25519, ssh-rsa,<br/>rsa-sha2-256, rsa-sha2-512
    SSH->>Server: Establish SSH connection
    Server-->>SSH: Session established
    SSH->>SSH: setPortForwardingL(localPort, "127.0.0.1", remotePort)

    App->>Agent: GET /telemetry (via localhost:localPort)
    Agent-->>App: SystemTelemetry JSON

    loop Keep-alive
        SSH->>Server: ServerAliveInterval (30s)
    end
```

### Gerenciamento de Sessões

```mermaid
classDiagram
    class SshTunnelManager {
        -Map~String, Session~ sessions
        -SecureTokenManager tokenManager
        +connect(serverId, host, port, user) Result~Unit~
        +disconnect(serverId)
        +disconnectAll()
        +isConnected(serverId) Boolean
        +getLocalPort(serverId) Int?
    }
```

**Características:**
- Sessions cacheadas por `serverId` para reuso
- Keep-alive a cada 30 segundos
- Suporte a Ed25519 e RSA
- Max 3 auth failures antes de bloquear
- Emergency mode (skip host key checking) configurável

---

## Segurança do App

```mermaid
graph TB
    subgraph "Proteções"
        BIO["Biometria<br/>(Fingerprint/Face)"]
        ENC["EncryptedSharedPrefs<br/>(Android KeyStore)"]
        JWT["JWT local<br/>(geração no device)"]
        CERT["Certificate Pinning<br/>(OkHttp)"]
        LOG["Zero log leak<br/>(sem secrets no Logcat)"]
    end

    subgraph "Fluxo de Acesso"
        START["App abre"] --> BIO_CHECK{"Biometria OK?"}
        BIO_CHECK -->|Sim| TOKEN["Carrega tokens<br/>(EncryptedPrefs)"]
        BIO_CHECK -->|Não| BLOCK["Acesso negado"]
        TOKEN --> SSH_CONN["Estabelece SSH"]
        SSH_CONN --> API_CALL["API calls + JWT"]
    end
```

---

## Background Workers

### AlertMonitoringWorker

Worker periódico via WorkManager que faz polling de alertas em background, dispara notificação por alerta e **grava os top 3 alertas recentes em `SharedPreferences("widget_data")`** para o widget de home screen consumir.

```mermaid
sequenceDiagram
    participant WM as WorkManager
    participant Worker as AlertMonitoringWorker
    participant Repo as ServerRepository
    participant Notif as SecurityNotificationManager
    participant Prefs as SharedPreferences
    participant Widget as ServerStatusWidget

    WM->>Worker: doWork() [periodic]
    loop Para cada servidor
        Worker->>Repo: getAlerts(server)
        Repo-->>Worker: AlertsResponse
        loop Para cada alerta
            Worker->>Notif: sendAlert(serverName, alert)
            Worker-->>Worker: recentAlerts += (timestamp, "SERVER: message")
        end
    end
    Worker-->>Worker: top3 = recentAlerts.sortedByDesc(ts).take(3)
    Worker->>Prefs: putString("recent_alerts", top3.join("\n"))
    Worker->>Widget: notifyAppWidgetManager(update)
    Worker-->>WM: Result.success()
```

### ServerStatusWidget

Widget de home screen mostrando saúde dos servidores **e os 3 alertas mais recentes**. Lê os valores do `SharedPreferences("widget_data")` escritos pelo `AlertMonitoringWorker`.

**Layout:** `widget_server_status.xml`
- Header: título `POCKET NOC` + timestamp da última atualização
- Contadores: SERVERS / ONLINE / ALERTAS
- **Seção de alertas recentes** (TextView com `maxLines=3`): mostra até 3 alertas no formato `SERVER: mensagem`. Se vazio: `sem alertas ativos`.
- `minHeight`: `130dp` (antes era `80dp` — bumpado para caber a nova seção)

```mermaid
flowchart LR
    WORKER["AlertMonitoringWorker"] -->|"writes"| PREFS[("SharedPreferences\nwidget_data")]
    PREFS -->|"reads"| WIDGET["ServerStatusWidget.updateWidget()"]
    WIDGET --> REMOTE["RemoteViews"]
    REMOTE --> LAYOUT["widget_server_status.xml"]
    LAYOUT --> TV_COUNT["SERVERS/ONLINE/ALERTAS"]
    LAYOUT --> TV_ALERTS["widget_recent_alerts (TextView)"]
```

**Nota:** ao atualizar o APK após mudar o `minHeight`, o widget **precisa ser removido e re-adicionado** na home screen para o Android renderizar o novo tamanho.

---

## Estrutura de Diretórios

```
controller/app/src/main/java/com/pocketnoc/
├── MainActivity.kt                    # Single Activity
├── PocketNOCApplication.kt            # Application + Hilt
├── config/
│   └── PocketNOCConfig.kt             # Build config (local.properties)
├── data/
│   ├── api/
│   │   ├── AgentApiService.kt         # Retrofit (20+ endpoints)
│   │   ├── DashboardApiService.kt     # Dashboard ERP
│   │   └── RetrofitClient.kt          # OkHttp setup
│   ├── models/
│   │   └── Models.kt                  # 30+ data classes
│   ├── local/
│   │   ├── AppDatabase.kt             # Room database
│   │   ├── SecureTokenManager.kt      # EncryptedSharedPrefs
│   │   ├── SessionManager.kt          # Session state
│   │   ├── AlertThresholdDataStore.kt # DataStore prefs
│   │   ├── dao/                        # Room DAOs
│   │   └── entities/                   # Room entities
│   ├── repository/
│   │   └── ServerRepository.kt        # Data layer central
│   └── websocket/
│       ├── RealtimeTelemetryManager.kt
│       └── WebSocketTelemetryManager.kt
├── di/
│   ├── DatabaseModule.kt              # Room DI
│   ├── NetworkModule.kt               # Retrofit DI
│   └── SecurityModule.kt              # JWT + Biometric DI
├── notifications/
│   └── PocketNOCFirebaseService.kt    # FCM handler
├── ssh/
│   └── SshTunnelManager.kt            # JSch tunneling
├── ui/
│   ├── screens/                        # 17 Compose screens
│   ├── components/                     # Reusable components
│   ├── navigation/                     # AppNavHost + AppRoutes
│   ├── viewmodels/                     # 3 ViewModels
│   └── theme/                          # Design system tokens
├── utils/                              # Utilities (JWT, Health, etc.)
├── widget/                             # Home screen widget
└── workers/                            # WorkManager tasks
```

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
