# 📡 Referência da API — Pocket NOC Agent

Esta API é interna e protegida. Todas as rotas (exceto `/health`) exigem o cabeçalho `Authorization: Bearer <JWT_TOKEN>`.

## 📍 Base URL

O agente ouve em: `http://127.0.0.1:9443` (Acesso via túnel SSH).

---

## 🚀 Endpoints de Telemetria

### `GET /telemetry`

Retorna o estado completo da máquina.

- **Retorno**: JSON contendo CPU, RAM, Disk, Load Average e Network.

### `GET /processes`

Lista os processos que mais consomem recursos.

- **Retorno**: Top 10 processos formatados para o dashboard.

### `DELETE /processes/:pid`

Encerra um processo específico.

- **Segurança**: Requer confirmação no mobile antes do disparo.

---

## ⚙️ Gestão e Ações

### `POST /alerts/config`

Atualiza os thresholds de alerta em tempo real.

- **Payload**:

```json
{
  "cpu_threshold_percent": 85.0,
  "memory_threshold_percent": 90.0,
  "disk_threshold_percent": 95.0,
  "security_threat_threshold": 15
}
```

### `GET /logs?service=<name>&lines=100`

Acessa o buffer do `journalctl`.

- **Default service**: `pocket-noc-agent`.

### `POST /commands/:id`

Executa um comando da Whitelist.

- **Comandos Válidos**: `restart_nginx`, `stop_nginx`, `restart_docker`, `clear_logs`.

---

## 💓 Health Check

### `GET /health`

Verifica se o agente está operante.

- **Auth**: Não requerido.
- **Resposta**: `{"status": "healthy"}`

---
**Documentação Funcional — Protocolo OMNI-DEV.**
