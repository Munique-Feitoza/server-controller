# Modelos de Dados — Pocket NOC

> Documentação dos modelos de dados utilizados no agente Rust e no Controller Android.  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Diagrama de Classes — Agente (Rust)](#diagrama-de-classes--agente-rust)
3. [Diagrama de Classes — Controller (Android)](#diagrama-de-classes--controller-android)
4. [Diagrama ER — Persistência Local (Room)](#diagrama-er--persistência-local-room)
5. [Mapeamento Rust ↔ Kotlin](#mapeamento-rust--kotlin)
6. [Detalhamento dos Modelos](#detalhamento-dos-modelos)

---

## Visão Geral

O Pocket NOC utiliza modelos de dados serializados em JSON para comunicação entre o agente Rust e o Controller Android. O agente produz as structs em Rust (serializadas via `serde`), e o Android as consome como `data class` Kotlin (deserializadas via `Gson/Moshi`).

---

## Diagrama de Classes — Agente (Rust)

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#475569", "lineColor": "#64748b" } }}%%
classDiagram
    class SystemTelemetry {
        +CpuMetrics cpu
        +MemoryMetrics memory
        +DiskMetrics disk
        +TemperatureMetrics temperature
        +NetworkMetrics network
        +SecurityMetrics security
        +ProcessMetrics processes
        +UptimeInfo uptime
        +Vec~ServiceInfo~ services
        +i64 timestamp
    }

    class CpuMetrics {
        +f32 usage_percent
        +Vec~CoreMetrics~ cores
    }

    class CoreMetrics {
        +u32 core_id
        +f32 usage_percent
        +u64 frequency_mhz
    }

    class MemoryMetrics {
        +u64 total_mb
        +u64 used_mb
        +f32 usage_percent
        +u64 swap_total_mb
        +u64 swap_used_mb
    }

    class DiskMetrics {
        +Vec~DiskInfo~ disks
    }

    class DiskInfo {
        +String mount_point
        +f64 total_gb
        +f64 used_gb
        +f32 usage_percent
        +String filesystem
    }

    class TemperatureMetrics {
        +Vec~SensorInfo~ sensors
    }

    class SensorInfo {
        +String label
        +f32 celsius
    }

    class NetworkMetrics {
        +Vec~InterfaceMetrics~ interfaces
    }

    class InterfaceMetrics {
        +String name
        +u64 rx_bytes
        +u64 tx_bytes
        +u64 rx_packets
        +u64 tx_packets
        +u64 rx_errors
        +u64 tx_errors
    }

    class SecurityMetrics {
        +u32 active_ssh_sessions
        +u32 failed_login_attempts
        +Vec~FailedLogin~ failed_logins
    }

    class FailedLogin {
        +String ip
        +String user
        +u32 attempts
        +String last_attempt
    }

    class ProcessMetrics {
        +Vec~ProcessInfo~ top_processes
        +u32 docker_containers_running
    }

    class ProcessInfo {
        +u32 pid
        +String name
        +f32 cpu_usage
        +u64 memory_mb
    }

    class UptimeInfo {
        +u64 uptime_seconds
        +Vec~f64~ load_average
    }

    class ServiceInfo {
        +String name
        +String status
        +String description
        +Option~u32~ pid
    }

    SystemTelemetry --> CpuMetrics
    SystemTelemetry --> MemoryMetrics
    SystemTelemetry --> DiskMetrics
    SystemTelemetry --> TemperatureMetrics
    SystemTelemetry --> NetworkMetrics
    SystemTelemetry --> SecurityMetrics
    SystemTelemetry --> ProcessMetrics
    SystemTelemetry --> UptimeInfo
    SystemTelemetry --> ServiceInfo

    CpuMetrics --> CoreMetrics
    DiskMetrics --> DiskInfo
    TemperatureMetrics --> SensorInfo
    NetworkMetrics --> InterfaceMetrics
    SecurityMetrics --> FailedLogin
    ProcessMetrics --> ProcessInfo
```

### Modelos de Alerta

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#475569", "lineColor": "#64748b" } }}%%
classDiagram
    class Alert {
        +AlertType alert_type
        +String message
        +f32 current_value
        +f32 threshold
        +i64 timestamp
        +Option~String~ component
    }

    class AlertType {
        <<enumeration>>
        HighCpu
        HighMemory
        HighDisk
        HighTemperature
        SecurityThreat
        RecentReboot
    }

    class AlertThresholdConfig {
        +f32 cpu_threshold_percent
        +f32 memory_threshold_percent
        +f32 disk_threshold_percent
        +f32 temperature_threshold_celsius
        +u32 reboot_threshold_minutes
        +u32 security_threat_threshold
    }

    Alert --> AlertType
```

### Modelos do Watchdog

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#475569", "lineColor": "#64748b" } }}%%
classDiagram
    class WatchdogEvent {
        +String id
        +String service
        +String status
        +String message
        +i64 timestamp
        +bool remediation_attempted
    }

    class CircuitBreaker {
        +CircuitState state
        +u32 failure_count
        +u32 max_failures
        +u64 cooldown_secs
        +Option~Instant~ last_failure
    }

    class CircuitState {
        <<enumeration>>
        Closed
        Open
        HalfOpen
    }

    CircuitBreaker --> CircuitState
```

### Modelos de Segurança

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#475569", "lineColor": "#64748b" } }}%%
classDiagram
    class ThreatTracker {
        +HashMap~IpAddr, ThreatEntry~ entries
        +u32 ban_threshold
        +Duration window
        +is_honeypot_path(path) bool
        +track_access(ip) ThreatAction
    }

    class ThreatEntry {
        +u32 count
        +Instant first_access
        +bool banned
    }

    class ThreatAction {
        <<enumeration>>
        Monitor
        Ban
        AlreadyBanned
        SafeIp
    }

    class SecurityIncident {
        +String ip
        +String country
        +String city
        +String isp
        +String attack_type
        +String severity
        +String machine_signature
        +String timestamp
    }

    ThreatTracker --> ThreatEntry
    ThreatTracker --> ThreatAction
```

---

## Diagrama de Classes — Controller (Android)

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#475569", "lineColor": "#64748b" } }}%%
classDiagram
    class SystemTelemetry {
        +CpuMetrics cpu
        +MemoryMetrics memory
        +DiskMetrics disk
        +TemperatureMetrics? temperature
        +NetworkMetrics network
        +SecurityMetrics security
        +ProcessMetrics processes
        +UptimeInfo uptime
        +List~ServiceInfo~ services
        +Long timestamp
    }

    class Alert {
        +String alertType
        +String message
        +Float currentValue
        +Float threshold
        +Long timestamp
        +String? component
    }

    class EmergencyCommand {
        +String id
        +String description
        +String command
        +List~String~ args
    }

    class CommandListResponse {
        +List~EmergencyCommand~ commands
    }

    class CommandInfo {
        +String id
        +String description
        +String command
        +List~String~ args
        +Int timeout
    }

    class CommandResult {
        +String commandId
        +Int exitCode
        +String stdout
        +String stderr
        +Long timestamp
    }

    class WatchdogEvent {
        +String id
        +String service
        +String status
        +String message
        +Long timestamp
        +Boolean remediationAttempted
    }

    class DockerContainer {
        +String id
        +String name
        +String image
        +String status
        +String? ports
    }

    class SslCertificate {
        +String domain
        +String issuer
        +String expiresAt
        +Int daysRemaining
        +String status
    }

    class PhpFpmPool {
        +String site
        +String poolName
        +Int activeProcesses
        +Int idleProcesses
        +Int totalProcesses
        +Double memoryMb
        +Double cpuPercent
    }

    class BackupInfo {
        +String name
        +String lastRun
        +String status
        +Double sizeMb
        +Double ageHours
    }

    class HealthStatus {
        <<enumeration>>
        HEALTHY
        WARNING
        ALERT
        CRITICAL
        UNKNOWN
    }

    class ServerHealth {
        +String serverId
        +HealthStatus status
        +SystemTelemetry? telemetry
        +List~Alert~ alerts
    }

    ServerHealth --> HealthStatus
    ServerHealth --> SystemTelemetry
    ServerHealth --> Alert
```

---

## Diagrama ER — Persistência Local (Room)

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
erDiagram
    SERVER_ENTITY {
        string id PK
        string name
        string host
        int port
        string sshUser
        string status
        long lastSeen
    }

    ALERT_ENTITY {
        long id PK
        string serverId FK
        string alertType
        string message
        float currentValue
        float threshold
        long timestamp
        boolean acknowledged
    }

    TELEMETRY_HISTORY_ENTITY {
        long id PK
        string serverId FK
        float cpuPercent
        float memoryPercent
        float diskPercent
        float temperature
        long timestamp
    }

    SERVER_ENTITY ||--o{ ALERT_ENTITY : "gera"
    SERVER_ENTITY ||--o{ TELEMETRY_HISTORY_ENTITY : "registra"
```

### DAOs (Data Access Objects)

```mermaid
%%{init: { "theme": "base", "themeVariables": { "fontFamily": "Inter, sans-serif", "primaryColor": "#1e293b", "primaryTextColor": "#f8fafc", "primaryBorderColor": "#475569", "lineColor": "#64748b" } }}%%
classDiagram
    class ServerDao {
        +getAll() Flow~List~ServerEntity~~
        +getById(id) ServerEntity?
        +insert(server)
        +update(server)
        +delete(server)
    }

    class AlertDao {
        +getByServerId(serverId) Flow~List~AlertEntity~~
        +getRecent(limit) Flow~List~AlertEntity~~
        +insert(alert)
        +deleteOlderThan(timestamp)
    }

    class TelemetryHistoryDao {
        +getByServerId(serverId, limit) Flow~List~TelemetryHistoryEntity~~
        +insert(entry)
        +deleteOlderThan(timestamp)
    }
```

---

## Mapeamento Rust ↔ Kotlin

| Rust (serde) | Kotlin (data class) | JSON Field |
|:---|:---|:---|
| `SystemTelemetry` | `SystemTelemetry` | — |
| `CpuMetrics` | `CpuMetrics` | `cpu` |
| `MemoryMetrics` | `MemoryMetrics` | `memory` |
| `DiskMetrics` | `DiskMetrics` | `disk` |
| `TemperatureMetrics` | `TemperatureMetrics` | `temperature` |
| `NetworkMetrics` | `NetworkMetrics` | `network` |
| `SecurityMetrics` | `SecurityMetrics` | `security` |
| `ProcessMetrics` | `ProcessMetrics` | `processes` |
| `UptimeInfo` | `UptimeInfo` | `uptime` |
| `Vec<ServiceInfo>` | `List<ServiceInfo>` | `services` |
| `Alert` | `Alert` | — |
| `WatchdogEvent` | `WatchdogEvent` | — |
| `String` | `String` | — |
| `f32` / `f64` | `Float` / `Double` | — |
| `u32` / `u64` | `Int` / `Long` | — |
| `Option<T>` | `T?` (nullable) | — |
| `Vec<T>` | `List<T>` | — |
| `HashMap<K,V>` | `Map<K,V>` | — |

---

## Detalhamento dos Modelos

### Ciclo de Vida dos Dados

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
flowchart LR
    subgraph rust["🦀 Agente Rust"]
        KERNEL["🐧 /proc + /sys"] --> COLLECT["📈 TelemetryCollector"]
        COLLECT --> CACHE[("💾 Cache L1<br/><i>5s</i>")]
        CACHE --> SERIALIZE["⚙️ serde_json::to_string()"]
    end

    SERIALIZE ==>|JSON via HTTP| DESERIALIZE

    subgraph android["📱 Controller Android"]
        DESERIALIZE["🌐 Gson.fromJson()"] --> MODEL["🧩 data class"]
        MODEL --> VM["🧩 ViewModel<br/><i>StateFlow</i>"]
        VM --> COMPOSE["🎨 Jetpack Compose"]
        MODEL --> ROOM[("💾 Room Database")]
    end

    classDef rustNode fill:#c2410c,stroke:#fb923c,stroke-width:2px,color:#fff7ed
    classDef androidNode fill:#7c3aed,stroke:#a78bfa,stroke-width:2px,color:#f5f3ff
    classDef kernelNode fill:#334155,stroke:#94a3b8,stroke-width:2px,color:#f1f5f9
    classDef storeNode fill:#0f766e,stroke:#5eead4,stroke-width:2px,color:#f0fdfa
    class KERNEL kernelNode
    class COLLECT,SERIALIZE rustNode
    class DESERIALIZE,MODEL,VM,COMPOSE androidNode
    class CACHE,ROOM storeNode
```

### Enums de Status

| Enum | Valores | Uso |
|:---|:---|:---|
| `AlertType` | `highcpu`, `highmemory`, `highdisk`, `hightemperature`, `securitythreat`, `recentreboot` | Classificação de alertas |
| `CircuitState` | `closed`, `open`, `halfopen` | Estado do circuit breaker |
| `ServiceStatus` | `active`, `inactive`, `failed`, `unknown` | Status de serviço systemd |
| `HealthStatus` | `HEALTHY`, `WARNING`, `ALERT`, `CRITICAL`, `UNKNOWN` | Saúde geral do servidor |
| `SslStatus` | `valid`, `expiring`, `expired` | Status de certificado SSL |

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
