# Pocket NOC - Server Controller

**Um painel de controle de infraestrutura e segurança on-the-go para celular Android.**

Monitore seus servidores em tempo real, receba alertas e execute ações de emergência direto do seu bolso, sem precisar de um notebook.

---

## 📋 Arquitetura Geral

```
┌─────────────────────────────────────────────────────────┐
│  CONTROLLER (Android / Kotlin)                          │
│  ├─ Dashboard em tempo real                             │
│  ├─ Alertas visuais (Verde/Amarelo/Vermelho)           │
│  ├─ Botões de ação rápida (restart, logs, etc)         │
│  └─ Widget na tela inicial                              │
└──────────────────────────┬──────────────────────────────┘
                           │
                    HTTPS/WebSocket
                   (Autenticação JWT)
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
┌───────▼─────────┐              ┌───────────▼─────────┐
│ AGENT 1 (Rust)  │              │ AGENT N (Rust)      │
│ Servidor 1      │              │ Servidor N          │
│                 │              │                     │
│ ├─ Telemetria   │              │ ├─ Telemetria       │
│ ├─ Serviços     │              │ ├─ Serviços         │
│ └─ Emergência   │              │ └─ Emergência       │
└─────────────────┘              └─────────────────────┘
```

---

## 🗂️ Estrutura do Repositório

```
server-controller/
├── agent/                       # Agente Rust (servidores)
│   ├── Cargo.toml              # Dependências e configuração
│   ├── src/
│   │   ├── main.rs             # Entry point
│   │   ├── lib.rs              # Módulos principais
│   │   ├── telemetry/
│   │   │   ├── mod.rs
│   │   │   ├── cpu.rs
│   │   │   ├── memory.rs
│   │   │   ├── disk.rs
│   │   │   └── temperature.rs
│   │   ├── services/
│   │   │   ├── mod.rs
│   │   │   └── monitor.rs
│   │   ├── commands/
│   │   │   ├── mod.rs
│   │   │   └── executor.rs
│   │   ├── api/
│   │   │   ├── mod.rs
│   │   │   ├── handlers.rs
│   │   │   ├── middleware.rs
│   │   │   └── websocket.rs
│   │   ├── auth/
│   │   │   ├── mod.rs
│   │   │   └── jwt.rs
│   │   └── error.rs
│   ├── systemd/
│   │   └── pocket-noc-agent.service
│   └── README.md
│
├── controller/                  # Controller Android (Kotlin)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── app/
│   │   ├── build.gradle.kts
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── AndroidManifest.xml
│   │   │       ├── java/com/pocketnoc/
│   │   │       │   ├── MainActivity.kt
│   │   │       │   ├── data/
│   │   │       │   │   ├── models/
│   │   │       │   │   ├── api/
│   │   │       │   │   └── repository/
│   │   │       │   ├── ui/
│   │   │       │   │   ├── screens/
│   │   │       │   │   ├── components/
│   │   │       │   │   ├── theme/
│   │   │       │   │   └── viewmodels/
│   │   │       │   └── utils/
│   │   │       └─ res/
│   │   └── README.md
│   └── gradle/
│
├── docs/                        # Documentação
│   ├── SETUP.md                # Guia de instalação
│   ├── SECURITY.md             # Políticas de segurança
│   ├── API.md                  # Especificação da API
│   └── ARCHITECTURE.md         # Detalhes da arquitetura
│
└── README.md                   # Este arquivo
```

---

## 🚀 Início Rápido

### Pré-requisitos

- **Agent**: Linux com Rust 1.70+
- **Controller**: Android 8.0+ com Kotlin 1.9+
- **Segurança**: HTTPS com certificado TLS

### Agent (Servidor)

```bash
cd agent
cargo build --release
sudo systemctl enable --now pocket-noc-agent.service
```

### Controller (Celular)

```bash
cd controller
./gradlew assembleRelease
# Instalar no Android via adb ou Google Play (futuro)
```

---

## 📡 Comunicação

| Componente | Protocolo | Porta | Autenticação |
|-----------|-----------|-------|--------------|
| Agent HTTP REST | HTTPS | 9443 | JWT / API Key |
| Agent WebSocket | WSS | 9443 | JWT / API Key |
| Controller | HTTPS Client | N/A | Token Bearer |

---

## 📚 Documentação Completa

- [Setup e Instalação](./docs/SETUP.md)
- [Segurança e Autenticação](./docs/SECURITY.md)
- [Especificação da API](./docs/API.md)
- [Arquitetura Detalhada](./docs/ARCHITECTURE.md)

---

## 🔒 Segurança

- ✅ Autenticação JWT em todas as requisições
- ✅ HTTPS/TLS obrigatório
- ✅ Validação de comandos predefinidos (whitelist)
- ✅ Integração com VPN (Tailscale/WireGuard recomendado)

---

## 📝 Licença

MIT - Veja LICENSE.md

---

## 👨‍💻 Desenvolvimento

Desenvolvido seguindo princípios de engenharia sênior em infraestrutura e mobile:

- **Rust**: Idiomático, sem `unwrap()` indiscriminado, tratamento de erros com `Result` e `Option`
- **Kotlin**: MVVM, Jetpack Compose, Coroutines, injeção de dependência
- **Resilência**: Preparado para o pior cenário (CPU 100%, conexões intermitentes)

---

**Pronto para monitorar sua infraestrutura? Comece pelo [SETUP.md](./docs/SETUP.md)!**