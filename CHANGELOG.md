# 📝 Changelog — Pocket NOC Ultra

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo. O projeto segue o padrão **Semantic Versioning**.

---

## [0.1.0] - 11 de Março de 2026

### 🦀 Agente (Rust) - Core Engine

- ✅ **Coleta de Telemetria**: Implementação de leitura direta do `/proc` para métricas de CPU, RAM, Disco e Temperatura.
- ✅ **Gestão de Serviços**: Integração com `systemd` para monitoramento e reinicialização de serviços críticos (Nginx, Docker, etc).
- ✅ **Action Center**: Execução de comandos via Whitelist com proteção contra Shell Injection.
- ✅ **Segurança HackerSec**: Autenticação via JWT (HMAC-SHA256) e bind exclusivo em `127.0.0.1`.
- ✅ **Observabilidade**: Logs estruturados via `tracing` e endpoint de métricas compatível com Prometheus.

### 📱 Controller (Android) - Mobile Command

- ✅ **Arquitetura MVVM**: Implementação com Hilt (DI), Retrofit (Networking) e Compose (UI).
- ✅ **Widgets Internos**: Implementação do `ServerHealthWidget` para visão consolidada da saúde da infra.
- ✅ **Segurança Zero-Leak**: Armazenamento de segredos e chaves SSH via `EncryptedSharedPreferences`.
- ✅ **Terminal Log Viewer**: Visualizador de logs em tempo real integrado ao dashboard.
- ✅ **Auditoria Local**: Histórico de alertas persistido em banco de dados Room.

### 📚 Documentação & Setup

- ✅ **Ecossistema /docs**: Guias detalhados de API, Arquitetura, Segurança e Instalação.
- ✅ **Setup Automatizado**: Script de deployment e configuração de serviço systemd.

---

## 🚀 Próximos Passos (Roadmap)

### Fase 2 - Expansão de Recursos

- [x] Suporte a múltiplos servidores.
- [x] Histórico persistente de alertas locais.
- [ ] Dashboards com gráficos históricos (CPU/RAM).
- [ ] Implementação de WebSockets para telemetria push.

### Fase 3 - Recursos Avançados (Coming Soon)

- [ ] **AppWidget**: Status visual direto na tela inicial do Android.
- [ ] **Push Notifications**: Alertas remotos via ntfy.sh (em progresso).
- [ ] **Alert Rules Engine**: Regras de alerta customizáveis por servidor.

---

## 🛠️ Notas de Desenvolvimento

### Executando Testes

**Agente (Rust)**:

```bash
cd agent
cargo test
```

**Controller (Android)**:

```bash
cd controller
./gradlew test
```

### Build para Produção

**Agente**:

```bash
cd agent
cargo build --release
```

**Android**:

```bash
cd controller
./gradlew assembleRelease
```

---

Acompanhe as atualizações conforme o **Protocolo OMNI-DEV**.

**Mantido por**: Munique Alves Pacheco Feitoza  
**Última atualização**: 16 de Março de 2026
