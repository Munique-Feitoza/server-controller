# 📡 Especificação da API - Pocket NOC Agent

Este documento descreve os endpoints da API REST fornecida pelo Agente Rust para o Controller Android.

---

## 🌐 Informações Gerais

- **Protocolo**: HTTPS/TLS (Obrigatório)
- **Porta Padrão**: `9443`
- **Formato**: `application/json`
- **Autenticação**: Bearer Token (JWT)

---

## 📊 Endpoints Disponíveis

### 1. Health Check

Verifica se o agente está ativo e operacional.

- **Método**: `GET`
- **Path**: `/health`
- **Auth**: Não necessária.

**Exemplo de Resposta**:

```json
{
  "status": "healthy",
  "timestamp": "2026-03-13T12:00:00Z"
}
```

---

### 2. Telemetria Completa

Retorna o estado atual do servidor (CPU, RAM, Disco, etc.).

- **Método**: `GET`
- **Path**: `/telemetry`
- **Auth**: Bearer Token.

**Campos principais**:

- `cpu`: Uso por core e total.
- `memory`: RAM usada, total e swap.
- `disk`: Lista de discos montados e espaço livre.
- `uptime`: Tempo de atividade do sistema.

---

### 3. Listar Comandos (Action Center)

Retorna a lista de comandos de emergência configurados na whitelist.

- **Método**: `GET`
- **Path**: `/commands`
- **Auth**: Bearer Token.

---

### 4. Executar Comando

Dispara um comando da whitelist.

- **Método**: `POST`
- **Path**: `/commands/{id}`
- **Auth**: Bearer Token.

**Exemplo de Resposta**:

```json
{
  "exit_code": 0,
  "stdout": "Service restarted successfully",
  "stderr": ""
}
```

---

## 🔑 Autenticação

Todas as rotas (exceto `/health`) exigem o header:
`Authorization: Bearer <seu_token_jwt>`

> [!TIP]
> Use o script de testes na raiz do projeto para validar sua conexão e token: `test_jwt_security.sh`.

---

**Versão da API**: 0.1.0
**Última Atualização**: Março de 2026
