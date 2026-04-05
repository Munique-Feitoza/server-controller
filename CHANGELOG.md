# Changelog — Pocket NOC Ultra

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo. O projeto segue o padrão **Semantic Versioning**.

---

## [0.3.0] - 02 de Abril de 2026

### Performance

- **`services/mod.rs`**: Consolidadas as 3 chamadas separadas ao `systemctl show` (ActiveState, Description, MainPID) em uma única invocação por serviço. Reduz de 12 subprocessos para 4 a cada ciclo de telemetria dos 4 serviços monitorados.
- **`security.rs`**: Adicionado `timeout 3s` ao comando `lastb` para blindar o agente contra travamento em servidores com `/var/log/btmp` volumoso.

### Segurança

- **`security.rs`**: Validação de IP no endpoint `block-ip` migrada para o parser nativo do Rust (`std::net::IpAddr`). CIDRs e ranges (ex: `0.0.0.0/0`) agora são corretamente rejeitados com erro `422`, prevenindo bloqueio acidental de todo o tráfego de entrada.

### Documentação

- **`docs/API.md`**: Reescrito com documentação completa de todos os 16 endpoints (telemetria, alertas, processos, serviços, logs, comandos, segurança, métricas, watchdog).
- **`docs/ARCHITECTURE.md`**: Corrigida informação incorreta ("polling a cada segundo" → cache de 5s + loop de 60s). Adicionadas seções de WatchdogEngine, Circuit Breaker e modelo de concorrência.
- **`docs/SECURITY.md`**: Documentados o enforcement do segredo JWT (mínimo 32 bytes), o timeout defensivo do `lastb`, e a nova validação de IP para `block-ip`.
- **`docs/SETUP.md`**: Adicionada tabela completa de variáveis de ambiente, instruções de instalação do serviço systemd hardened (usuário dedicado, limites de CPU/RAM), e seção de troubleshooting expandida.
- **`README.md`**: Adicionados WatchdogEngine, alertas ntfy e métricas Prometheus nos diferenciais técnicos. Adicionada tabela completa de endpoints da API.

---

## [0.2.0] - 20 de Março de 2026

### Agente (Rust) — WatchdogEngine & Alertas

- **WatchdogEngine**: Motor de auto-remediação com probes HTTP, systemctl e TCP. Loop independente com intervalo configurável.
- **Circuit Breaker**: Máquina de estados (Closed/Open/HalfOpen) por serviço monitorado. Evita loops infinitos de remediação.
- **RemediationEngine**: Executa ações corretivas (restart de serviço) após falhas confirmadas.
- **Ring Buffer de Eventos**: Armazena os últimos 500 eventos do Watchdog em memória via `VecDeque`.
- **Roles de Servidor**: Seleção automática de probes via `SERVER_ROLE` (wordpress, erp, database, generic, etc).
- **AlertManager com Deduplicação**: Sistema de alertas com cooldown de 30 minutos por `(AlertType, componente)`. Evita spam de notificações.
- **ntfy.sh Integration**: Notificações push para alertas de CPU, memória, disco, temperatura, segurança e reboot recente. Retry automático com backoff linear em caso de rate limit.
- **Prometheus Metrics**: Endpoint `GET /metrics` com formato texto compatível com Prometheus.
- **Novos endpoints**: `/watchdog/events`, `/watchdog/reset`, `/watchdog/breakers`, `/alerts`, `/alerts/config`, `/security/block-ip`, `/metrics`, `/services/:name`.

### Segurança

- Segredo JWT com enforcement de mínimo 32 bytes no startup (rejeita configuração insegura).
- Mascaramento do secret nos logs (apenas 4 primeiros chars + `****`).

---

## [0.1.0] - 11 de Março de 2026

### Agente (Rust) — Core Engine

- **Coleta de Telemetria**: Leitura direta do `/proc` e `/sys` para métricas de CPU (por core), RAM/swap, disco (uso + I/O bruto via `procfs::diskstats`), temperatura via `/sys/class/hwmon`, e rede (por interface).
- **Cache L1 (5s)**: `TelemetryCollector` com cache em memória para evitar `system.refresh_all()` em cada requisição.
- **Gestão de Serviços**: Integração com `systemd` para monitoramento de serviços críticos.
- **Action Center**: Execução de comandos via whitelist com proteção contra shell injection.
- **Segurança Munux Security**: Autenticação JWT (HMAC-SHA256) e bind exclusivo em `127.0.0.1`.
- **Observabilidade**: Logs estruturados via `tracing`.
- **Build musl**: Binário estático com `opt-level=3`, LTO e strip — ~4 MB, zero dependências de runtime.

### Controller (Android) — Mobile Command

- **Arquitetura MVVM**: Implementação com Hilt (DI), Retrofit (Networking) e Compose (UI).
- **ServerHealthWidget**: Visão consolidada da saúde da infraestrutura.
- **Segurança Zero-Leak**: Armazenamento de segredos e chaves SSH via `EncryptedSharedPreferences`.
- **Terminal Log Viewer**: Visualizador de logs em tempo real integrado ao dashboard.
- **Auditoria Local**: Histórico de alertas persistido em banco de dados Room.

### Documentação & Setup

- Ecossistema `/docs`: Guias de API, Arquitetura, Segurança e Instalação.
- Script de deployment e configuração de serviço systemd.

---

## Roadmap

### Fase 3 — Recursos Avançados

- [ ] **WebSockets**: Telemetria push em tempo real (infraestrutura `tokio-tungstenite` já incluída).
- [ ] **Persistência SQLite**: Histórico de eventos e alertas persistidos entre reinicializações.
- [ ] **AppWidget Android**: Status visual direto na tela inicial.
- [ ] **Gráficos históricos**: Dashboards de CPU/RAM com série temporal.
- [ ] **TLS nativo**: Ativar TLS no Axum (infraestrutura rustls já incluída), para acesso sem necessidade de SSH quando em rede privada confiável.

---

## Notas de Desenvolvimento

### Rodando Testes

**Agente (Rust)**:
```bash
cd agent
cargo test
cargo clippy -- -D warnings
cargo fmt --check
```

**Controller (Android)**:
```bash
cd controller
./gradlew test
./gradlew lint
```

### Build para Produção

**Agente (binário musl estático)**:
```bash
cd agent
cargo build --release --target x86_64-unknown-linux-musl
```

**Android**:
```bash
cd controller
./gradlew assembleRelease
```

---

**Mantido por**: Munique Alves Pacheco Feitoza  
**Última atualização**: 02 de Abril de 2026
