# 📚 Índice Completo - Pocket NOC

Guia rápido de navegação para toda a documentação e código do projeto.

## 🎯 Início Rápido

**Novo no projeto?** Comece por aqui:

1. [README.md](./README.md) - Visão geral e arquitetura
2. [PROJECT_SUMMARY.md](./PROJECT_SUMMARY.md) - O que foi entregue
3. [docs/SETUP.md](./docs/SETUP.md) - Instale em 5 minutos

---

## 📖 Documentação

### Essencial
| Documento | Propósito | Tempo de leitura |
|-----------|-----------|-----------------|
| [README.md](./README.md) | Visão geral, estrutura do projeto | 5 min |
| [PROJECT_SUMMARY.md](./PROJECT_SUMMARY.md) | O que foi entregue, status final | 10 min |
| [docs/SETUP.md](./docs/SETUP.md) | Instalação e configuração | 15 min |

### Referência Técnica
| Documento | Propósito | Público |
|-----------|-----------|---------|
| [docs/API.md](./docs/API.md) | Especificação de todos os endpoints | Desenvolvedores |
| [docs/SECURITY.md](./docs/SECURITY.md) | Autenticação, TLS, protecção contra ataques | Devops / Security |
| [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) | Arquitetura detalhada, fluxos de dados | Arquitetos / Engenheiros |

### Versioning e Changelog
| Arquivo | Conteúdo |
|---------|----------|
| [CHANGELOG.md](./CHANGELOG.md) | Histórico de features e roadmap |

---

## 🗂️ Estrutura do Código

### Agent (Rust) - `/agent`

```
agent/
├── README.md                      # Documentação do agent
├── Cargo.toml                     # Dependências e configuração
├── src/
│   ├── main.rs                    # Entry point
│   ├── lib.rs                     # Module declarations
│   ├── error.rs                   # Error types
│   ├── telemetry/mod.rs           # Telemetry collector
│   ├── services/mod.rs            # Service monitoring
│   ├── commands/mod.rs            # Command execution
│   ├── api/handlers.rs            # REST endpoints
│   ├── api/middleware.rs          # Auth + logging
│   └── auth/mod.rs                # JWT + API Key
└── systemd/
    └── pocket-noc-agent.service   # Systemd service file
```

**Arquivos-chave**:
- [agent/src/main.rs](./agent/src/main.rs) - Configuração do servidor
- [agent/src/api/handlers.rs](./agent/src/api/handlers.rs) - 6 endpoints
- [agent/src/auth/mod.rs](./agent/src/auth/mod.rs) - JWT implementation
- [agent/README.md](./agent/README.md) - Guia do agent

### Controller (Kotlin) - `/controller`

```
controller/
├── README.md                      # Quick guide
├── settings.gradle.kts            # Gradle settings
├── build.gradle.kts               # Root build config
└── app/
    ├── README.md                  # Documentação do app
    ├── build.gradle.kts           # App dependencies
    └── src/main/java/com/pocketnoc/
        ├── MainActivity.kt         # Main activity
        ├── data/
        │   ├── models/Models.kt    # All data classes
        │   ├── api/
        │   │   ├── AgentApiService.kt    # Retrofit interface
        │   │   └── RetrofitClient.kt     # HTTP client config
        │   └── repository/ServerRepository.kt  # Data layer
        └── ui/
            ├── screens/DashboardScreen.kt      # Dashboard UI
            ├── components/Components.kt         # Reusable components
            ├── theme/Theme.kt                  # Dark theme
            └── viewmodels/DashboardViewModel.kt # State management
```

**Arquivos-chave**:
- [controller/app/src/main/java/com/pocketnoc/MainActivity.kt](./controller/app/src/main/java/com/pocketnoc/MainActivity.kt)
- [controller/app/src/main/java/com/pocketnoc/ui/screens/DashboardScreen.kt](./controller/app/src/main/java/com/pocketnoc/ui/screens/DashboardScreen.kt)
- [controller/app/README.md](./controller/app/README.md)

---

## 🔍 Buscar por Tópico

### Autenticação & Segurança
- JWT Implementation: [agent/src/auth/mod.rs](./agent/src/auth/mod.rs)
- Security Guide: [docs/SECURITY.md](./docs/SECURITY.md)
- TLS Setup: [docs/SETUP.md](./docs/SETUP.md#-configuração-de-segurança)

### Telemetria & Monitoramento
- Telemetry Collector: [agent/src/telemetry/mod.rs](./agent/src/telemetry/mod.rs)
- CPU Metrics: [agent/src/telemetry/cpu.rs](./agent/src/telemetry/cpu.rs)
- Memory Metrics: [agent/src/telemetry/memory.rs](./agent/src/telemetry/memory.rs)
- Disk Metrics: [agent/src/telemetry/disk.rs](./agent/src/telemetry/disk.rs)
- Service Monitor: [agent/src/services/mod.rs](./agent/src/services/mod.rs)

### API REST
- API Specification: [docs/API.md](./docs/API.md)
- Handlers: [agent/src/api/handlers.rs](./agent/src/api/handlers.rs)
- Middleware: [agent/src/api/middleware.rs](./agent/src/api/middleware.rs)

### Android UI
- Dashboard: [controller/app/src/main/java/com/pocketnoc/ui/screens/DashboardScreen.kt](./controller/app/src/main/java/com/pocketnoc/ui/screens/DashboardScreen.kt)
- Components: [controller/app/src/main/java/com/pocketnoc/ui/components/Components.kt](./controller/app/src/main/java/com/pocketnoc/ui/components/Components.kt)
- ViewModel: [controller/app/src/main/java/com/pocketnoc/ui/viewmodels/DashboardViewModel.kt](./controller/app/src/main/java/com/pocketnoc/ui/viewmodels/DashboardViewModel.kt)
- Models: [controller/app/src/main/java/com/pocketnoc/data/models/Models.kt](./controller/app/src/main/java/com/pocketnoc/data/models/Models.kt)

### Instalação & Configuração
- Quick Setup: [docs/SETUP.md](./docs/SETUP.md)
- Nginx Proxy: [docs/SETUP.md#configuração-com-nginx-recomendado](./docs/SETUP.md#configuração-com-nginx-recomendado)
- Tailscale VPN: [docs/SETUP.md#-rede-privada-tailscale](./docs/SETUP.md#-rede-privada-tailscale)

---

## 📋 Tarefas Implementadas

- ✅ Agente Rust completo com telemetria, serviços e comandos
- ✅ Controller Android com MVVM, Compose e Retrofit
- ✅ Autenticação JWT e API Key
- ✅ 6 endpoints REST (health, telemetry, services, commands, metrics)
- ✅ Documentação completa (4 arquivos)
- ✅ Setup guide para produção
- ✅ Segurança (whitelist, TLS, JWT)

---

## 🚀 Quick Commands

### Build Agent
```bash
cd agent
cargo build --release
```

### Build Controller
```bash
cd controller
./gradlew assembleDebug
```

### Test API
```bash
curl -k https://localhost:9443/health
```

### View Agent Logs
```bash
sudo journalctl -u pocket-noc-agent -f
```

### Install Service
```bash
sudo cp agent/systemd/pocket-noc-agent.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable pocket-noc-agent
sudo systemctl start pocket-noc-agent
```

---

## 🎯 Por Onde Começar?

**Se você é:**

- **DevOps/SRE** → [docs/SETUP.md](./docs/SETUP.md) + [docs/SECURITY.md](./docs/SECURITY.md)
- **Backend Engineer (Rust)** → [agent/README.md](./agent/README.md) + [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)
- **Mobile Engineer (Kotlin)** → [controller/app/README.md](./controller/app/README.md)
- **Product Manager** → [PROJECT_SUMMARY.md](./PROJECT_SUMMARY.md) + [README.md](./README.md)
- **Security Analyst** → [docs/SECURITY.md](./docs/SECURITY.md) + [docs/API.md](./docs/API.md)

---

## 📞 Referência Rápida

| Precisa de... | Vá para... |
|----------------|-----------|
| Instalar o projeto | [docs/SETUP.md](./docs/SETUP.md) |
| Usar a API | [docs/API.md](./docs/API.md) |
| Entender segurança | [docs/SECURITY.md](./docs/SECURITY.md) |
| Mergulhar na arquitetura | [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) |
| Ver roadmap | [CHANGELOG.md](./CHANGELOG.md) |
| Revisar código do Agent | [agent/src](./agent/src) |
| Revisar código do Controller | [controller/app/src](./controller/app/src/main/java/com/pocketnoc) |

---

## 🔗 Links Internos

- [Projeto principal](./README.md)
- [Agente Rust](./agent/README.md)
- [Controller Android](./controller/app/README.md)
- [Setup & Instalação](./docs/SETUP.md)
- [API Specification](./docs/API.md)
- [Segurança](./docs/SECURITY.md)
- [Arquitetura](./docs/ARCHITECTURE.md)
- [Changelog](./CHANGELOG.md)
- [Este índice](./INDEX.md)

---

**Última atualização**: 11 de março de 2026  
**Versão**: 0.1.0  
**Mantido por**: Pocket NOC Team
