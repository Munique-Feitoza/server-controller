# Protocolos de Segurança — Munux Security

A segurança no Pocket NOC não é um "adicional", ela é o alicerce. Como lidamos com acesso a servidores de produção, o sistema implementa múltiplas camadas de proteção baseadas no princípio **Zero Trust**.

## Camadas de Defesa

O sistema utiliza uma abordagem de **Defesa em Profundidade**:

### 1. Perímetro: Stealth Bind (localhost-only)

O agente Rust faz bind exclusivamente em `127.0.0.1:9443`. Isso significa que, mesmo que o servidor tenha portas abertas no firewall, ninguém consegue falar com o agente diretamente da internet. O acesso é fisicamente impossível sem a chave SSH.

### 2. Transporte: SSH Tunneling

Toda a comunicação REST passa por um túnel SSH (Local Port Forwarding):

```bash
ssh -L 9443:127.0.0.1:9443 usuario@servidor
```

- **Criptografia**: AES-256/ChaCha20 (negociado pelo cliente SSH)
- **Autenticação**: Par de chaves Ed25519 (recomendado) ou RSA-4096
- **Sem exposição de porta**: O agente não aparece em nenhum scan externo

### 3. Aplicação: JWT Auth (HMAC-SHA256)

Mesmo dentro do túnel SSH, cada requisição HTTP precisa de um token JWT válido.

- **Algoritmo**: HS256 (HMAC-SHA256)
- **Segredo mínimo**: 32 bytes — o agente rejeita segredos menores em tempo de startup (padrão OWASP)
- **Expiração padrão**: 3600 segundos (1 hora), configurável até 30 dias
- **Claims validados**: `exp`, `iat`, `nbf`
- **Único ponto de exceção**: `GET /health` (verificação de disponibilidade sem auth)

---

## Gestão de Segredos no Mobile

Sistema de **Zero Leak** no Android para proteção de credenciais:

- **EncryptedSharedPreferences**: Segredos e chaves SSH são criptografados em repouso usando a KeyStore do Android (hardware-backed se disponível no dispositivo)
- **Obfuscação de Logs**: Nenhum token, secret ou metadado sensível é exposto no Logcat
- **Mascaramento no Agente**: O secret é logado apenas com os 4 primeiros caracteres mascarados (`ABCD****`)

---

## Whitelist de Comandos (Action Center)

O Action Center não permite execução de comandos arbitrários.

- Apenas binários pré-definidos em tempo de compilação podem ser disparados
- Os argumentos são passados diretamente ao binário via `Command::new` — sem shell intermediário, sem interpolação, sem shell injection possível
- Comandos disponíveis: `restart_nginx`, `stop_nginx`, `start_nginx`, `restart_docker`, `start_docker`, `stop_docker`, `restart_mysql`, `restart_agent`, `clear_logs`, `disk_usage`

---

## Proteção contra Bloqueio Acidental (block-ip)

O endpoint `POST /security/block-ip` valida o endereço IP usando o parser nativo do Rust (`std::net::IpAddr`). Apenas IPs individuais válidos (IPv4 ou IPv6) são aceitos. CIDRs como `0.0.0.0/0` são rejeitados com erro `422` — prevenindo um bloqueio acidental de todo o tráfego de entrada.

---

## Inteligência de Ameaças (Sentinel Security)

O Controller monitora falhas de login via `lastb` com inteligência de filtragem:

- **Filtro de Ruído**: Apenas IPs que atingiram o threshold configurado (padrão: 10 tentativas/hora) são reportados como ameaça, focando em ataques reais
- **Timeout Defensivo**: A leitura do `lastb` tem timeout de 3 segundos para evitar travamento em servidores com `/var/log/btmp` muito grande
- **Intervenção Manual (BAN)**: Bloqueio instantâneo via `iptables -I INPUT` (topo da cadeia) diretamente pelo Controller

---

## Variáveis de Ambiente Sensíveis

| Variável | Obrigatória | Descrição |
|----------|-------------|-----------|
| `POCKET_NOC_SECRET` | Sim | Segredo JWT (mínimo 32 caracteres). Nunca use o padrão de desenvolvimento em produção. |
| `NTFY_TOPIC` | Não | Tópico ntfy personalizado. Se omitido, deriva dos primeiros 8 chars do secret. |
| `POCKET_NOC_PORT` | Não | Porta de bind (padrão: 9443). |

---

> [!WARNING]
> Nunca compartilhe seu `POCKET_NOC_SECRET` ou sua chave privada SSH. O vazamento de qualquer um desses dados compromete integralmente a segurança do seu servidor.

> [!NOTE]
> O agente deve ser executado como o usuário dedicado `pocketnoc` (criado pelo script de deploy), não como `root`. O systemd unit inclui `AmbientCapabilities=CAP_KILL CAP_NET_ADMIN` para as operações necessárias.
