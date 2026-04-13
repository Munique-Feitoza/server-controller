# Estratégia de Testes — Pocket NOC

> Documentação da estratégia de testes e qualidade do código.  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Pirâmide de Testes](#pirâmide-de-testes)
3. [Testes do Agente (Rust)](#testes-do-agente-rust)
4. [Testes do Controller (Android)](#testes-do-controller-android)
5. [Análise Estática](#análise-estática)
6. [CI — Execução Automatizada](#ci--execução-automatizada)
7. [Como Executar](#como-executar)

---

## Visão Geral

O Pocket NOC adota uma estratégia de testes focada em **confiabilidade de componentes críticos**: autenticação JWT, lógica de cálculo de saúde, modelos de dados e watchdog. Os testes são executados automaticamente nas pipelines de CI/CD (GitHub Actions) em cada push e pull request.

---

## Pirâmide de Testes

```mermaid
graph TB
    subgraph "Pirâmide de Testes"
        E2E["🔝 E2E<br/>(Manual / Futuro)"]
        INT["🔷 Integração<br/>(API + SSH Tunnel)"]
        UNIT["🟢 Unitários<br/>(JWT, Models, Watchdog, Health)"]
    end

    subgraph "Ferramentas"
        RUST_TEST["cargo test"]
        CLIPPY["cargo clippy"]
        FMT["cargo fmt"]
        GRADLE["./gradlew test"]
        LINT["./gradlew lint"]
    end

    UNIT --> RUST_TEST
    UNIT --> GRADLE
    INT --> RUST_TEST
    E2E -.->|"futuro"| INT

    style UNIT fill:#2a9d8f,color:#fff
    style INT fill:#457b9d,color:#fff
    style E2E fill:#e9c46a,color:#000
```

---

## Testes do Agente (Rust)

### Suítes de Teste

| Arquivo | Cobertura | Descrição |
|:---|:---|:---|
| `tests/api_tests.rs` | Autenticação JWT | Geração de token, validação de claims, expiração, secret inválido |
| `tests/watchdog_tests.rs` | WatchdogEngine | Circuit breaker (Closed/Open/HalfOpen), probes, remediação |

### Testes de JWT (`api_tests.rs`)

```mermaid
flowchart TD
    T1["test_jwt_generation<br/>Gera token com claims válidos"]
    T2["test_jwt_validation<br/>Valida assinatura e expiração"]
    T3["test_jwt_expired<br/>Rejeita token expirado"]
    T4["test_jwt_invalid_secret<br/>Rejeita secret < 32 bytes"]
    T5["test_jwt_claims<br/>Verifica sub, iss, exp, iat"]

    T1 --> PASS["✓ Todos devem passar"]
    T2 --> PASS
    T3 --> PASS
    T4 --> PASS
    T5 --> PASS
```

**Cenários cobertos:**
- Geração de token com claims válidos
- Validação de assinatura HMAC-SHA256
- Rejeição de token expirado
- Rejeição de secret menor que 32 bytes
- Verificação de claims `sub`, `iss`, `exp`, `iat`

### Testes do Watchdog (`watchdog_tests.rs`)

**Cenários cobertos:**
- Transição de estados do Circuit Breaker: Closed → Open → HalfOpen → Closed
- Contagem de falhas e reset após sucesso
- Cooldown timer entre estados Open e HalfOpen
- Probes HTTP, TCP e Systemctl (mocks)
- Ring buffer de eventos (inserção e limite de 500)

### Execução

```bash
cd agent

# Todos os testes
cargo test

# Testes com output detalhado
cargo test -- --nocapture

# Teste específico
cargo test test_jwt_generation

# Testes de um arquivo
cargo test --test api_tests
cargo test --test watchdog_tests
```

---

## Testes do Controller (Android)

### Suítes de Teste

| Arquivo | Cobertura | Descrição |
|:---|:---|:---|
| `ModelsTest.kt` | Modelos de dados | Serialização/deserialização JSON, valores default, nullability |
| `HealthStatusCalculatorTest.kt` | Cálculo de saúde | Classificação HEALTHY/WARNING/ALERT/CRITICAL por thresholds |

### Testes de Modelos (`ModelsTest.kt`)

**Cenários cobertos:**
- Criação de `SystemTelemetry` com todos os campos
- Serialização para JSON e deserialização de volta
- Campos opcionais (nullable) com valor nulo
- Valores default dos data classes
- Estrutura de `Alert`, `WatchdogEvent`, `DockerContainer`, etc.

### Testes do Health Calculator (`HealthStatusCalculatorTest.kt`)

```mermaid
flowchart LR
    subgraph "Inputs"
        CPU["CPU %"]
        RAM["RAM %"]
        DISK["Disk %"]
        TEMP["Temp °C"]
    end

    subgraph "HealthStatusCalculator"
        CALC["calculate()"]
    end

    subgraph "Outputs"
        H["HEALTHY<br/>(todos < warning)"]
        W["WARNING<br/>(algum acima de 70%)"]
        A["ALERT<br/>(algum acima de 85%)"]
        C["CRITICAL<br/>(algum acima de 95%)"]
    end

    CPU --> CALC
    RAM --> CALC
    DISK --> CALC
    TEMP --> CALC

    CALC --> H
    CALC --> W
    CALC --> A
    CALC --> C
```

**Cenários cobertos:**
- Servidor saudável (todos os valores baixos)
- Warning (CPU entre 70-85%)
- Alert (memória entre 85-95%)
- Critical (disco acima de 95%)
- Múltiplos alertas simultâneos (prioridade do mais grave)
- Valores nulos/ausentes (default para UNKNOWN)

### Execução

```bash
cd controller

# Todos os testes unitários
./gradlew test

# Testes com relatório HTML
./gradlew testDebugUnitTest

# Teste de uma classe específica
./gradlew test --tests "com.pocketnoc.ModelsTest"
./gradlew test --tests "com.pocketnoc.HealthStatusCalculatorTest"
```

---

## Análise Estática

### Rust — Clippy + Fmt

| Ferramenta | Propósito | Configuração |
|:---|:---|:---|
| `cargo clippy` | Linting avançado (anti-patterns, performance, correctness) | `-- -D warnings` (zero warnings) |
| `cargo fmt` | Formatação consistente | `--check` (falha se não formatado) |

### Android — Lint

| Ferramenta | Propósito |
|:---|:---|
| `./gradlew lint` | Análise estática Android (deprecated APIs, accessibility, security) |

---

## CI — Execução Automatizada

Os testes são executados automaticamente pelo GitHub Actions em cada push e pull request.

```mermaid
flowchart TD
    subgraph "agent-ci.yml"
        A1["cargo fmt --check"] --> A2["cargo clippy -- -D warnings"]
        A2 --> A3["cargo test"]
        A3 --> A4["cargo build --release"]
    end

    subgraph "android-ci.yml"
        B1["./gradlew assembleDebug"] --> B2["./gradlew lint"]
        B2 --> B3["./gradlew test"]
    end

    PUSH["Push / PR"] --> A1
    PUSH --> B1
```

### Critérios de Aprovação

| Critério | Agente (Rust) | Controller (Android) |
|:---|:---|:---|
| Formatação | `cargo fmt --check` | — |
| Lint | `cargo clippy -D warnings` | `./gradlew lint` |
| Testes | `cargo test` | `./gradlew test` |
| Build | `cargo build --release` | `./gradlew assembleDebug` |

---

## Como Executar

### Agente (Rust) — Completo

```bash
cd agent

# 1. Formatação
cargo fmt --check

# 2. Linting (zero warnings)
cargo clippy -- -D warnings

# 3. Testes
cargo test

# 4. Build de produção
cargo build --release --target x86_64-unknown-linux-musl
```

### Controller (Android) — Completo

```bash
cd controller

# 1. Build
./gradlew assembleDebug

# 2. Lint
./gradlew lint

# 3. Testes unitários
./gradlew test
```

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
