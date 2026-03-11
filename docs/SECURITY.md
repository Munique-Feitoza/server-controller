# Segurança e Autenticação - Pocket NOC

Este documento detalha as estratégias de segurança implementadas no projeto Pocket NOC.

## 🔐 Autenticação

### JWT (JSON Web Tokens) - Recomendado

**Implementação no Agent (Rust)**:

```rust
let jwt_config = JwtConfig::new("super-secret-key".to_string(), 3600);

// Gerar token
let token = jwt_config.generate_token("client-1", vec!["read".to_string()])?;
// Token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...

// Validar token
let claims = jwt_config.validate_token(token)?;
assert_eq!(claims.sub, "client-1");
```

**Implementação no Controller (Kotlin)**:

```kotlin
// Armazenar token após login
val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
PreferenceManager.getDefaultSharedPreferences(context)
    .edit()
    .putString("jwt_token", token)
    .apply()

// Usar em requests
val authHeader = "Bearer $token"
apiService.getTelemetry(authHeader)
```

### API Key - Fallback

Para casos onde JWT não é necessário:

```rust
let api_key_auth = ApiKeyAuth::new("my-secret-key".to_string());
api_key_auth.validate(provided_key)?; // OK ou erro
```

**Header no Request**:
```
X-API-Key: my-secret-key
```

## 🔒 HTTPS/TLS

### Certificado Self-Signed (Desenvolvimento)

```bash
# Gerar certificado válido por 365 dias
openssl req -x509 -newkey rsa:4096 -nodes -out cert.pem -keyout key.pem -days 365

# Configurar no Rust
use tokio_rustls::TlsAcceptor;
let certs = load_certs("cert.pem")?;
let key = load_key("key.pem")?;
let config = ServerConfig::builder().build()?;
let acceptor = TlsAcceptor::from(Arc::new(config));
```

### Certificate Válido (Produção)

Use um provedor confiável:
- **Let's Encrypt**: Certificados gratuitos automatizados
- **AWS ACM**: Para servidores na AWS
- **Cloudflare**: Proteção e proxy reverso

**Nginx como Proxy Reverso**:

```nginx
server {
    listen 443 ssl;
    server_name api.pocketnoc.com;

    ssl_certificate /etc/letsencrypt/live/api.pocketnoc.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.pocketnoc.com/privkey.pem;

    location / {
        proxy_pass http://localhost:9443;
        proxy_set_header Authorization $http_authorization;
    }
}
```

## 🔑 Gerenciamento de Secrets

### Variáveis de Ambiente

```bash
# .env (NÃO comitir!)
JWT_SECRET=super-secret-key-generate-randomically
JWT_EXPIRATION=3600
AGENT_PORT=9443

# Carregar no código
let secret = std::env::var("JWT_SECRET")
    .expect("JWT_SECRET not set");
```

### Arquivo de Configuração

```toml
# config.toml (NÃO comitir!)
[auth]
jwt_secret = "super-secret-key"
jwt_expiration = 3600

[server]
port = 9443
host = "0.0.0.0"

[ssl]
cert_path = "/etc/pocket-noc/cert.pem"
key_path = "/etc/pocket-noc/key.pem"
```

```rust
use toml::Value;

let config: Value = toml::from_str(&std::fs::read_to_string("config.toml")?)?;
let secret = config["auth"]["jwt_secret"].as_str().unwrap();
```

## 🛡️ Whitelist de Comandos

Apenas comandos explicitamente permitidos podem ser executados:

```rust
pub fn default_emergency_commands() -> Vec<EmergencyCommand> {
    vec![
        EmergencyCommand {
            id: "restart_nginx",
            description: "Restart Nginx",
            command: "systemctl",
            args: vec!["restart", "nginx"],
        },
        // Não permite: rm -rf /, curl http://malicious.com, etc
    ]
}
```

**Validação**:
- ✅ Apenas comandos na whitelist são executados
- ✅ Sem interpretação de shell (`/bin/bash -c`)
- ✅ Argumentos são strings pré-definidas, não input do usuário
- ✅ Sem pipes, redirects ou wildcards

## 🚫 Proteção contra Vulnerabilidades

### 1. Injection (Shell, SQL)
- ✅ Sem `shell=True` em Python
- ✅ Sem `eval()` ou `exec()`
- ✅ Sem interpolação de strings em comandos
- ✅ Uso de whitelists

### 2. Replay Attack
- ✅ JWT com `exp` (expiração)
- ✅ Renovação de tokens regularmente
- ✅ Validação de timestamp `iat`

### 3. Man-in-the-Middle (MITM)
- ✅ HTTPS/TLS obrigatório
- ✅ Validação de certificado no cliente

### 4. Acesso Não Autorizado
- ✅ Autenticação em todos os endpoints (exceto `/health`)
- ✅ Validação de escopos (permissões)

### 5. DoS (Denial of Service)
- ✅ Rate limiting (implementar com tower-http)
- ✅ Timeouts em requests
- ✅ Limite de tamanho de payload

```rust
use tower_http::limit::RequestBodyLimitLayer;

let app = Router::new()
    .layer(RequestBodyLimitLayer::max(10.megabytes()));
```

## 🌐 Rede Privada (Recomendado)

### Tailscale (Fácil)

```bash
# No servidor
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up

# No celular
# Baixar app do Google Play e conectar na mesma VPN
```

**Vantagens**:
- Zero-trust security
- Acesso apenas entre nós autorizado
- Sem exposição para internet pública
- Funciona através de NAT/firewalls

### WireGuard (Mais controle)

```bash
# Gerar chaves
wg genkey | tee privatekey | wg pubkey > publickey

# Configuração
[Interface]
Address = 10.0.0.1/24
PrivateKey = <server-private-key>

[Peer]
PublicKey = <client-public-key>
AllowedIPs = 10.0.0.2/32
```

## 📋 Checklist de Segurança

- [ ] JWT secret é forte (mínimo 32 caracteres aleatórios)
- [ ] Secrets não são commitados no Git
- [ ] HTTPS/TLS configurado e válido
- [ ] Whitelist de comandos é restrita
- [ ] Logs de acesso são auditados
- [ ] Tokens expirando regularmente (1-24h)
- [ ] Rede privada (Tailscale/WireGuard) está ativa
- [ ] Certificados não expirados
- [ ] Rate limiting implementado
- [ ] Validação de input em endpoints

## 🔍 Auditoria e Logs

### Log de Requisições

```rust
use tracing::info;

info!(
    method = %method,
    uri = %uri,
    status = response.status().as_u16(),
    user = claims.sub,
    "Request handled"
);
```

### Análise de Logs

```bash
# Ver logs do agent
sudo journalctl -u pocket-noc-agent -n 100 -f

# Filtrar por usuário
sudo journalctl -u pocket-noc-agent | grep "user=\"client-1\""

# Filtrar por erro
sudo journalctl -u pocket-noc-agent | grep "error"
```

## 📚 Referências

- [OWASP Top 10](https://owasp.org/Top10/)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8949)
- [TLS 1.3](https://tools.ietf.org/html/rfc8446)
- [Tailscale Docs](https://tailscale.com/kb/)
- [WireGuard Docs](https://www.wireguard.com/quickstart/)

---

**Status**: ✅ Produção-ready com implementações recomendadas
