# 🔐 Protocolos de Segurança — HackerSec Core

A segurança no Pocket NOC não é um "adicional", ela é o alicerce. Como lidamos com acesso root a servidores de produção, implementei múltiplas camadas de proteção baseadas no princípio **Zero Trust**.

## 🛡️ Camadas de Defesa

O sistema utiliza uma abordagem de **Defesa em Profundidade**:

### 1. Perímetro: Stealth Bind (localhost)

O Agente Rust está configurado para fazer bind apenas em `127.0.0.1`. Isso significa que, mesmo que o servidor tenha portas abertas no firewall (UFW/IPTables), ninguém consegue falar com o agente diretamente da internet. O acesso é fisicamente impossível sem a nossa chave SSH.

### 2. Transporte: SSH Tunneling

Toda a comunicação REST passa por um túnel SSH (Local Port Forwarding).

- **Criptografia**: AES-256 de nível militar.
- **Autenticação**: Baseada em par de chaves (Ed25519 preferencialmente).
- **Proteção contra Intrusão (Sentinel Security)**: O Controller monitora falhas de login do sistema (`lastb`) com inteligência de filtragem.
  - **Filtro de Ruído**: A telemetria ignora tentativas isoladas e reporta apenas IPs que atingiram o threshold de segurança (padrão: 10 tentativas/hora), focando apenas em ameaças reais.
  - **Intervenção Manual (BAN)**: Bloqueio instantâneo via IPTables diretamente pelo Controller. O agente usa `iptables -I` para garantir que o bloqueio fique no topo da cadeia `INPUT`.

### 3. Aplicação: JWT Auth (HMAC-SHA256)

Mesmo dentro do túnel SSH, cada requisição HTTP precisa de um token JWT válido.

- **Segredo Dinâmico**: O segredo é carregado via `POCKET_NOC_SECRET` no servidor.
- **Expiration**: Tokens têm tempo de vida curto, minimizando o risco de replay attacks.

---

## 🧤 Gestão de Segredos no Mobile

Implementei um sistema de **Zero Leak** no Android para proteger suas credenciais:

- **EncryptedSharedPreferences**: Segredos e Chaves SSH não são salvos em texto puro. Eles são criptografados em repouso usando a KeyStore do Android (hardware-backed se disponível).
- **Obfuscação de Logs**: Removi qualquer log que pudesse expor tokens ou metadados sensíveis no Logcat.

## 🚦 Whitelist de Comandos

O **Action Center** não permite a execução de comandos arbitrários (shell remoto).

- Apenas binários pré-definidos no código Rust (ex: `systemctl restart nginx`) podem ser disparados.
- Os argumentos são sanitizados para evitar Shell Injection.

---
> [!WARNING]
> Nunca compartilhe seu `POCKET_NOC_SECRET` ou sua chave privada SSH. O vazamento de um desses dados compromete a integridade do seu servidor.
