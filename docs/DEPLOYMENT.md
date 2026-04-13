# CI/CD e Deployment — Pocket NOC

> Documentação das pipelines de integração contínua, entrega contínua e processo de deploy.  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Arquitetura de CI/CD](#arquitetura-de-cicd)
3. [Pipeline do Agente (Rust)](#pipeline-do-agente-rust)
4. [Pipeline do Controller (Android)](#pipeline-do-controller-android)
5. [Pipeline de Release](#pipeline-de-release)
6. [Deploy em Produção](#deploy-em-produção)
7. [Infraestrutura de Produção](#infraestrutura-de-produção)
8. [Rollback](#rollback)

---

## Visão Geral

O Pocket NOC utiliza **GitHub Actions** para automação de CI/CD. Existem 3 pipelines independentes que garantem qualidade em cada camada do sistema.

```mermaid
graph LR
    subgraph "Triggers"
        PUSH["Push / PR"]
        TAG["Tag v*"]
    end

    subgraph "Pipelines"
        AGENT["agent-ci.yml<br/>(Rust)"]
        ANDROID["android-ci.yml<br/>(Kotlin)"]
        RELEASE["release.yml<br/>(Multi-artifact)"]
    end

    subgraph "Artifacts"
        BIN_x86["pocket-noc-agent<br/>(x86_64)"]
        BIN_arm["pocket-noc-agent<br/>(aarch64)"]
        APK["PocketNOC.apk"]
        GH_RELEASE["GitHub Release"]
    end

    PUSH -->|"agent/**"| AGENT
    PUSH -->|"controller/**"| ANDROID
    TAG --> RELEASE

    AGENT --> BIN_x86
    AGENT --> BIN_arm
    ANDROID --> APK
    RELEASE --> GH_RELEASE
```

---

## Arquitetura de CI/CD

```mermaid
flowchart TD
    DEV["Desenvolvimento Local"] -->|"git push"| GH["GitHub"]

    GH --> CI_AGENT["agent-ci.yml"]
    GH --> CI_ANDROID["android-ci.yml"]

    CI_AGENT --> FMT["cargo fmt --check"]
    FMT --> CLIPPY["cargo clippy -D warnings"]
    CLIPPY --> TEST_RUST["cargo test"]
    TEST_RUST --> BUILD_x86["Build x86_64"]
    TEST_RUST --> BUILD_arm["Build aarch64"]

    CI_ANDROID --> BUILD_APK["./gradlew assembleDebug"]
    BUILD_APK --> LINT["./gradlew lint"]
    LINT --> TEST_ANDROID["./gradlew test"]

    BUILD_x86 --> UPLOAD_AGENT["Upload Artifacts"]
    BUILD_arm --> UPLOAD_AGENT
    TEST_ANDROID --> UPLOAD_APK["Upload APK (main only)"]

    GH -->|"tag v*"| RELEASE["release.yml"]
    RELEASE --> MULTI_BUILD["Build All Targets"]
    MULTI_BUILD --> GH_RELEASE["Create GitHub Release"]

    style FMT fill:#2a9d8f,color:#fff
    style CLIPPY fill:#2a9d8f,color:#fff
    style TEST_RUST fill:#2a9d8f,color:#fff
    style TEST_ANDROID fill:#457b9d,color:#fff
```

---

## Pipeline do Agente (Rust)

**Arquivo:** `.github/workflows/agent-ci.yml`  
**Trigger:** Push/PR em `agent/**`

### Etapas

```mermaid
flowchart LR
    A["checkout"] --> B["Install Rust toolchain"]
    B --> C["cargo fmt --check"]
    C --> D["cargo clippy -- -D warnings"]
    D --> E["cargo test"]
    E --> F["Build x86_64-linux-gnu"]
    E --> G["Build aarch64-linux-gnu"]
    F --> H["Upload x86_64 artifact"]
    G --> I["Upload aarch64 artifact"]
```

| Etapa | Comando | Critério |
|:---|:---|:---|
| Formatação | `cargo fmt --check` | Zero desvios |
| Linting | `cargo clippy -- -D warnings` | Zero warnings |
| Testes | `cargo test` | Todos passando |
| Build (x86_64) | `cargo build --release` | Sucesso |
| Build (aarch64) | `cargo build --release --target aarch64-unknown-linux-gnu` | Sucesso |

---

## Pipeline do Controller (Android)

**Arquivo:** `.github/workflows/android-ci.yml`  
**Trigger:** Push/PR em `controller/**`

### Etapas

```mermaid
flowchart LR
    A["checkout"] --> B["Setup JDK 17"]
    B --> C["./gradlew assembleDebug"]
    C --> D["./gradlew lint"]
    D --> E["./gradlew test"]
    E --> F{"Branch é main?"}
    F -->|Sim| G["Upload APK artifact"]
    F -->|Não| H["Fim"]
```

| Etapa | Comando | Critério |
|:---|:---|:---|
| Build | `./gradlew assembleDebug` | Compilação sem erros |
| Lint | `./gradlew lint` | Sem erros críticos |
| Testes | `./gradlew test` | Todos passando |
| APK | Upload artifact | Apenas na branch `main` |

---

## Pipeline de Release

**Arquivo:** `.github/workflows/release.yml`  
**Trigger:** Push de tag `v*` (ex: `v0.4.0`)

### Fluxo de Release

```mermaid
sequenceDiagram
    participant Dev as Desenvolvedora
    participant Git as GitHub
    participant CI as GitHub Actions
    participant Release as GitHub Releases

    Dev->>Git: git tag v0.4.0 && git push --tags
    Git->>CI: Trigger release.yml

    par Build paralelo
        CI->>CI: Build Rust (x86_64-linux-musl)
        CI->>CI: Build Rust (aarch64-linux-gnu)
        CI->>CI: Build Android (release APK)
    end

    CI->>Release: Cria GitHub Release com artifacts
    Note over Release: pocket-noc-agent-x86_64<br/>pocket-noc-agent-aarch64<br/>PocketNOC-release.apk

    Dev->>Dev: Download artifacts ou deploy
```

### Artifacts da Release

| Artifact | Target | Descrição |
|:---|:---|:---|
| `pocket-noc-agent-x86_64` | `x86_64-unknown-linux-gnu` | Binário para servidores Intel/AMD |
| `pocket-noc-agent-aarch64` | `aarch64-unknown-linux-gnu` | Binário para servidores ARM |
| `PocketNOC-release.apk` | Android 7.0+ | App Android assinado |

---

## Deploy em Produção

### Deploy Automatizado (deploy.sh)

O script `deploy.sh` realiza o deploy completo em todos os servidores configurados.

```mermaid
sequenceDiagram
    participant Local as Máquina Local
    participant Build as Cargo Build
    participant S1 as server-1
    participant S2 as server-2
    participant S3 as server-3
    participant S4 as server-4

    Local->>Build: cargo build --release --target x86_64-unknown-linux-musl
    Build-->>Local: pocket-noc-agent (static binary, ~4MB)

    par Deploy paralelo
        Local->>S1: pkill → scp → mv → systemctl restart
        Local->>S2: pkill → scp → mv → systemctl restart
        Local->>S3: pkill → scp → mv → systemctl restart
        Local->>S4: pkill → scp → mv → systemctl restart
    end

    S1-->>Local: ✓ Status: active
    S2-->>Local: ✓ Status: active
    S3-->>Local: ✓ Status: active
    S4-->>Local: ✓ Status: active
```

### Passos do deploy.sh

```bash
# 1. Compilação local (musl estático)
cargo build --release --target x86_64-unknown-linux-musl

# 2. Para cada servidor:
pkill -9 pocket-noc-agent          # Para o processo
scp pocket-noc-agent user@host:~/  # Copia binário
ssh user@host "sudo mv ~/pocket-noc-agent /usr/local/bin/"
ssh user@host "sudo systemctl daemon-reload"
ssh user@host "sudo systemctl restart pocket-noc-agent"
ssh user@host "sudo systemctl status pocket-noc-agent"  # Verifica
```

### Execução

```bash
chmod +x deploy.sh
./deploy.sh
```

---

## Infraestrutura de Produção

### Servidores

```mermaid
graph TB
    subgraph "Produção"
        S1["server-1<br/>192.0.2.10<br/>SSH: 22"]
        S2["server-2<br/>192.0.2.20<br/>SSH: 22"]
        S3["server-3<br/>192.0.2.30<br/>SSH: 2222"]
        S4["server-4<br/>192.0.2.40<br/>SSH: 22"]
    end

    subgraph "Gerenciamento"
        RC["Hosting<br/>(Server Management)"]
        USER["SSH User: deploy"]
    end

    subgraph "Agente"
        BIN["/usr/local/bin/pocket-noc-agent"]
        SVC["systemd: pocket-noc-agent.service"]
        ENV["/etc/pocket-noc/.env"]
    end

    RC --> S1
    RC --> S2
    RC --> S3
    RC --> S4

    S1 --> BIN
    S1 --> SVC
    S1 --> ENV
```

| Servidor | IP | Porta SSH | Role |
|:---|:---|:---|:---|
| server-1 | `192.0.2.10` | 22 | wordpress |
| server-2 | `192.0.2.20` | 22 | wordpress |
| server-3 | `192.0.2.30` | 2222 | erp |
| server-4 | `192.0.2.40` | 22 | wordpress |

---

## Rollback

### Procedimento de Rollback

Em caso de problemas após deploy, o rollback é manual:

```bash
# 1. No servidor afetado, para o agente
ssh deploy@host "sudo systemctl stop pocket-noc-agent"

# 2. Restaura binário anterior (se houver backup)
ssh deploy@host "sudo cp /usr/local/bin/pocket-noc-agent.bak /usr/local/bin/pocket-noc-agent"

# 3. Reinicia
ssh deploy@host "sudo systemctl start pocket-noc-agent"

# 4. Verifica
ssh deploy@host "sudo systemctl status pocket-noc-agent"
```

### Estratégia de Zero Downtime

O agente reinicia em **< 1 segundo** (binário estático, sem warmup). O systemd garante `Restart=always` com `RestartSec=10`, minimizando o impacto de qualquer falha durante o deploy.

```mermaid
timeline
    title Timeline de Deploy
    0s : pkill agent
    0.5s : scp novo binário
    2s : mv para /usr/local/bin
    2.5s : systemctl restart
    3s : Agente operacional
```

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
