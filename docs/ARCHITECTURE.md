# 📐 Arquitetura Detalhada - Pocket NOC

Este documento fornece uma visão técnica profunda da arquitetura do Pocket NOC, detalhando as decisões de design, fluxos de dados e a integração entre os componentes.

---

## 🏗️ Visão Geral do Sistema

O Pocket NOC utiliza uma arquitetura descentralizada de Agente-Controller, garantindo escalabilidade e baixo acoplamento.

```mermaid
graph LR
    subgraph "Mobile Device (Kotlin)"
        A[UI Layer] --> B[ViewModel]
        B --> C[Repository]
        C --> D[Retrofit Client]
    end

    D -- "HTTPS + JWT" --> E[Pocket NOC Agent (Rust)]

    subgraph "Server Environment"
        E --> F[Telemetry Module]
        E --> G[Service Manager]
        E --> H[Command Executor]
        F --> I["/proc & sysfs"]
        G --> J[Systemd]
    end
```

---

## 🏎️ Componentes Principais

### 1. Agent (O Coração no Servidor)

Desenvolvido em **Rust** para garantir a máxima performance com o menor uso de recursos possível.

- **Modularidade**: O código é dividido em módulos especializados (telemetria, serviços, comandos).
- **Zero-Unwrap Policy**: Tratamento de erros robusto usando `Result` e `Option` para evitar crashes.
- **Async I/O**: Baseado no runtime `Tokio` para lidar com múltiplas conexões sem bloquear.

### 2. Controller (A Interface no Bolso)

Desenvolvido em **Kotlin** com **Jetpack Compose**, seguindo as melhores práticas da plataforma Android.

- **MVVM (Model-View-ViewModel)**: Separação clara entre a lógica de negócio e a interface.
- **Coroutines & Flow**: Gerenciamento eficiente de chamadas assíncronas e estados da UI.
- **Material Design 3**: UI moderna com foco em usabilidade e estética futurista.

---

## 📊 Fluxos de Dados

### Coleta de Telemetria

1. O **Controller** solicita um `GET /telemetry` enviando o `JWT`.
2. O **Agente** valida a assinatura e expiração do token através de um **Middleware**.
3. O módulo de telemetria lê diretamente do sistema de arquivos virtual do Linux (`/proc` e `/sys`).
4. Os dados são serializados em JSON e enviados de volta.
5. A UI do Android reage à atualização do estado através de `StateFlow`.

### Execução de Comandos (Action Center)
>
> [!NOTE]
> O Action Center está temporariamente oculto na UI principal para ajustes, mas a infraestrutura da API continua operacional.

1. O Controller envia um `POST /commands/{id}`.
2. O Agente verifica se o comando solicitado está presente na **Whitelist**.
3. Se autorizado, o comando é executado via `std::process::Command` sem passagem direta para o shell (evitando `Shell Injection`).
4. O resultado (exit code e output) é retornado ao Controller.

---

## 🛡️ Decisões Técnicas e Segurança

- **Porta 9443**: Escolhida para evitar conflitos, permitindo o uso de proxies reversos.
- **Segurança por Whitelist**: Ao contrário de ferramentas de terminal remoto, o Pocket NOC só permite executar o que foi explicitamente configurado.
- **Minimalismo**: O agente não possui banco de dados próprio, consumindo menos de 20MB de RAM.

---

## 📈 Escalabilidade e Performance

O sistema foi testado para responder em menos de **200ms** para métricas de telemetria, mesmo sob carga moderada de sistema. Para ambientes com centenas de servidores, recomenda-se o uso de um agregador ou proxy centralizado, embora o Agente suporte conexões diretas via IP ou VPN.

---

**Última atualização**: Março de 2026
**Status**: Arquitetura validada e estável.
