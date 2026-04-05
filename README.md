# Pocket NOC — Centro de Operacoes de Rede no Bolso

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Rust](https://img.shields.io/badge/Rust-1.70%2B-orange?style=for-the-badge&logo=rust)](https://www.rust-lang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green?style=for-the-badge&logo=android)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-GPL_v2-blue?style=for-the-badge&logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

Solucao completa de monitoramento, seguranca e gestao de infraestrutura. Agente em **Rust** rodando nos servidores (< 10MB RAM) + app **Android** nativo em Kotlin/Compose para controle remoto em tempo real.

---

## O que faz

- **Monitoramento em tempo real**: CPU, RAM, disco, rede, temperatura, processos, servicos
- **WatchdogEngine**: detecta servicos caidos e reinicia automaticamente com circuit breaker
- **Defesa ativa**: honeypot paths + zip bomb + auto-ban via iptables apos 5 tentativas
- **Inteligencia de ameacas**: coleta geolocalizacao, ISP e classificacao bot/humano de atacantes
- **Integracao com Dashboard ERP**: recebe incidentes de seguranca do dashboard Acme via webhook
- **PHP-FPM por site**: mostra qual site WordPress esta consumindo CPU/RAM em cada servidor
- **Acoes remotas**: reiniciar servicos, matar processos, bloquear IPs, executar comandos
- **Alertas push**: notificacoes via ntfy.sh com deduplicacao inteligente
- **Dark/Light mode**: design system com tokens de cor, dimensao e shape

---

## Arquitetura

```
App Android (Kotlin/Compose)
    |
    |-- SSH Tunnel (JSch)
    |
    v
Agente Rust (Axum + Tokio)  ←──  Dashboard ERP (FastAPI)
    |                                   |
    |-- Telemetria (/proc, sysinfo)     |-- security_incidents (PostgreSQL)
    |-- WatchdogEngine (probes)         |-- notify_pocket_noc() webhook
    |-- Defesa (honeypot, zip bomb)     |
    |-- PHP-FPM monitoring             |
    v                                   v
iptables, systemctl, ntfy.sh      Cloudflare API (ban)
```

---

## API do Agente

| Metodo | Rota | Descricao |
|--------|------|-----------|
| `GET` | `/health` | Health check (sem auth) |
| `GET` | `/telemetry` | Snapshot completo do sistema |
| `GET` | `/alerts` | Alertas ativos |
| `POST` | `/alerts/config` | Atualiza thresholds |
| `GET` | `/processes` | Top 10 processos |
| `DELETE` | `/processes/:pid` | Encerra processo |
| `GET` | `/logs` | Logs do journalctl |
| `GET` | `/services/:name` | Status de servico |
| `GET` | `/commands` | Lista comandos whitelist |
| `POST` | `/commands/:id` | Executa comando |
| `POST` | `/security/block-ip` | Bloqueia IP via iptables |
| `GET` | `/security/incidents` | Incidentes de seguranca |
| `GET` | `/metrics` | Formato Prometheus |
| `GET` | `/phpfpm/pools` | PHP-FPM pools por site |
| `GET` | `/docker/containers` | Containers Docker |
| `GET` | `/backups/status` | Status de backups |
| `GET` | `/audit/logs` | Log de auditoria |
| `GET` | `/config` | Configuracao do agente |
| `GET` | `/watchdog/events` | Eventos do Watchdog |
| `GET` | `/watchdog/breakers` | Circuit Breakers |
| `POST` | `/watchdog/reset` | Reset dos breakers |
| `POST` | `/webhook/security` | Recebe alertas do dashboard |

> Todas as rotas (exceto `/health`) requerem JWT Bearer token.

---

## Seguranca

### Defesa ativa do agente

1. **Honeypot paths** (30+): `/wp-admin`, `/.env`, `/.git`, `/phpmyadmin`, etc
2. **Rastreamento por IP**: conta acessos a honeypots por atacante
3. **Na 5a tentativa**: serve zip bomb (50MB expandido) + ban automatico via iptables
4. **IPs seguros**: localhost, 192.168.*, 10.*, 172.16-17.* nunca sao banidos
5. **Erros de JWT**: retornam 401 normal, nunca contam para ban (protege devs)
6. **Inteligencia**: coleta pais, cidade, ISP, proxy/VPN, classifica bot vs humano

### Integracao com Dashboard ERP

O dashboard ERP (FastAPI/Next.js) detecta ataques via middleware e envia para o PocketNOC:
- `POST /webhook/security` recebe incidentes em tempo real
- `GET /security/incidents` permite consulta pelo app Android
- Dados: IP, pais, ISP, tipo de ataque, severidade, machine_signature

---

## Deploy

```bash
# Compila e faz deploy em todos os servidores
./deploy.sh
```

Servidores configurados:
| Servidor | IP | Porta SSH |
|----------|-----|-----------|
| server-1 | 192.0.2.10 | 22 |
| server-2 | 192.0.2.20 | 22 |
| server-3 | 192.0.2.30 | 2222 |
| server-4 | 192.0.2.40 | 22 |

---

## Performance do agente

| Metrica | Valor |
|---------|-------|
| RAM | 8-11 MB |
| CPU | 0.3-0.5% |
| Binario | ~4 MB (musl estático) |
| Ciclo watchdog | 30 segundos |
| Ciclo alertas | 60 segundos |

---

## Documentacao

- [Guia de Instalacao](./docs/SETUP.md)
- [Arquitetura](./docs/ARCHITECTURE.md)
- [Seguranca](./docs/SECURITY.md)
- [API Completa](./docs/API.md)

---

**Desenvolvido por [Munique Alves Pacheco Feitoza](https://github.com/Munique-Feitoza)**
*Engenharia de Software | ADS | Manjaro User*
