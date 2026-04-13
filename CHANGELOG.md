# Changelog — Pocket NOC

Todas as mudanças notáveis neste projeto são documentadas neste arquivo.  
O projeto segue o padrão **[Semantic Versioning](https://semver.org/)**.

---

## [0.4.0] — 10 de Abril de 2026

### Agente (Rust) — Defesa Ativa e Monitoramento Expandido

- **Sistema de defesa ativa**: honeypot paths (30+), ThreatTracker com rastreamento por IP, ZIP bomb (50MB gzip) para bots recorrentes, auto-ban via iptables após 5 tentativas.
- **Inteligência de ameaças**: módulo `security/intel.rs` com GeoIP, ISP, classificação bot/humano.
- **Registro de incidentes**: `security/incidents.rs` para armazenar incidentes recebidos do Dashboard ERP.
- **Monitoramento PHP-FPM**: `telemetry/phpfpm.rs` com detecção automática de pools Hosting e consumo por site.
- **Monitoramento Docker**: `telemetry/docker.rs` com containers, status e portas.
- **Verificação de backups**: `telemetry/backup.rs` com status e idade.
- **Verificação SSL**: `telemetry/ssl.rs` com checagem periódica (6h) e alertas em 30/7/0 dias.
- **Endpoint webhook**: `POST /webhook/security` para integração com Dashboard ERP.
- **Rate limiting**: middleware de limitação por IP (60 req/min padrão).
- **WebSocket**: infraestrutura para telemetria em tempo real.
- **Audit log**: ring buffer de 1000 entradas para auditoria de requisições HTTP.

### Controller (Android) — Redesign Completo

- **Design system**: tokens de cor (`AppColors`), dimensão (`Dimens`), shape (`Shapes`) e tema (`Theme`) com Material 3.
- **Migração completa**: todas as 14 telas existentes migradas para o novo design system.
- **Novas telas**: SecurityDashboardScreen, PhpFpmScreen, SslCheckScreen, AuditLogScreen, AgentConfigScreen, ExportScreen, BiometricGateScreen.
- **Dark/Light mode**: toggle manual com persistência.
- **Layout adaptivo**: suporte a phone, tablet e foldable via `AdaptiveLayout`.
- **Widget de home screen**: `ServerStatusWidget` com status em tempo real.
- **Push notifications**: Firebase Cloud Messaging (FCM) via `PocketNOCFirebaseService`.
- **Autenticação biométrica**: fingerprint/face via `BiometricAuthManager`.
- **Persistência de telemetria**: Room DB com `TelemetryHistoryDao`.
- **Exportação de dados**: CSV/JSON via `ExportScreen`.
- **Dashboard reescrito**: menu hamburger, fundo sólido, botões de tema e exportação.
- **Ícone redesenhado**: novo ícone adaptivo para o launcher.

### Correções

- Fix: crash do `EncryptedSharedPreferences` corrompido — recriação automática.
- Fix: widget conectado com dados reais (antes era mock).
- Fix: tipografia responsiva e dark/light mode.

### API ERP (FastAPI)

- Novo: painel de inteligência de segurança para admin (`pocketnoc.py`).
- Novo: endpoint `/clusters` para agrupar IPs por identidade.
- Novo: autenticação dual (API key + JWT) nos endpoints de segurança.
- Fix: filtro de falsos positivos em todas as queries do painel.

### DevOps

- **CI/CD**: GitHub Actions para Rust (fmt, clippy, test, build multi-target), Android (build, lint, test) e Release (tag-triggered).
- **Documentação completa**: 9 documentos com diagramas UML Mermaid — Arquitetura, API, Segurança, Setup, Modelos de Dados, App Android, Testes, CI/CD, Glossário.
- **Testes**: unitários para JWT, commands, watchdog (Rust) e models, health calculator (Android).
- Deploy script corrigido.
- Comentários traduzidos para pt-BR.

---

## [0.3.0] — 02 de Abril de 2026

### Performance

- **`services/mod.rs`**: Consolidadas as 3 chamadas separadas ao `systemctl show` em uma única invocação por serviço. Reduz de 12 subprocessos para 4 a cada ciclo de telemetria.
- **`security.rs`**: Adicionado `timeout 3s` ao comando `lastb` para blindar contra travamento em servidores com `/var/log/btmp` volumoso.

### Segurança

- **`security.rs`**: Validação de IP migrada para `std::net::IpAddr`. CIDRs rejeitados com `422`.

### Documentação

- Reescrita completa de API.md, ARCHITECTURE.md, SECURITY.md e SETUP.md.

---

## [0.2.0] — 20 de Março de 2026

### Agente (Rust) — WatchdogEngine e Alertas

- **WatchdogEngine**: auto-remediação com probes HTTP, systemctl e TCP. Loop independente (30s).
- **Circuit Breaker**: máquina de estados (Closed/Open/HalfOpen) por serviço.
- **RemediationEngine**: ações corretivas após falhas confirmadas.
- **Ring Buffer**: 500 eventos do Watchdog em memória (VecDeque).
- **Roles de Servidor**: seleção automática de probes via `SERVER_ROLE`.
- **AlertManager**: deduplicação com cooldown de 30 minutos.
- **ntfy.sh**: notificações push com retry e backoff linear.
- **Prometheus**: endpoint `GET /metrics`.
- **Novos endpoints**: watchdog, alerts, block-ip, metrics, services.

### Segurança

- Enforcement de secret JWT ≥ 32 bytes no startup.
- Mascaramento do secret nos logs.

---

## [0.1.0] — 11 de Março de 2026

### Agente (Rust) — Core Engine

- **Telemetria**: CPU, RAM/swap, disco, temperatura, rede, processos, serviços.
- **Cache L1 (5s)**: evita leitura repetida de `/proc` a cada requisição.
- **Action Center**: whitelist de comandos sem shell injection.
- **JWT Auth**: HMAC-SHA256 com bind exclusivo em `127.0.0.1`.
- **Build musl**: binário estático ~4 MB com LTO e strip.

### Controller (Android) — MVP

- **MVVM**: Hilt + Retrofit + Compose.
- **ServerHealthWidget**: visão consolidada de saúde.
- **EncryptedSharedPreferences**: segredos criptografados.
- **Log Viewer**: visualizador de logs em tempo real.
- **Room**: histórico de alertas persistido localmente.

### Infraestrutura

- Script de deployment (`deploy.sh`).
- Serviço systemd com hardening.
- Documentação inicial (`/docs`).

---

## Roadmap

### Fase 5 — Recursos Avançados

- [ ] **WebSocket real-time**: telemetria push via WebSocket (infraestrutura `tokio-tungstenite` pronta)
- [ ] **Gráficos históricos**: dashboards de CPU/RAM com série temporal
- [ ] **TLS nativo**: ativar TLS no Axum para acesso em rede privada sem SSH
- [ ] **Multi-tenancy**: suporte a equipes com RBAC
- [ ] **Testes de integração**: cobertura end-to-end com containers Docker

---

**Mantido por**: Munique Alves Pacheco Feitoza  
**Última atualização**: 10 de Abril de 2026
