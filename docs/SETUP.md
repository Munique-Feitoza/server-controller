# Setup e Instalação - Pocket NOC

Guia completo para instalação e configuração do Pocket NOC em ambiente de produção.

## 📋 Pré-requisitos

### Agent (Servidor)
- Linux (Ubuntu 20.04+, Debian 11+, CentOS 8+, AlmaLinux 9+)
- Rust 1.70+
- sudo acesso
- 500MB livre em disco
- 128MB RAM disponível

### Controller (Celular)
- Android 8.0+ (API 24+)
- 100MB espaço em disco
- Conexão de rede estável

## 🚀 Instalação Rápida (5 minutos)

### 1. Preparar o Servidor

```bash
# Atualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar Rust (se não tiver)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env

# Clonar repositório
git clone https://github.com/seu-usuario/server-controller.git
cd server-controller
```

### 2. Compilar Agent

```bash
cd agent
cargo build --release

# O binário estará em: target/release/pocket-noc-agent
ls -lh target/release/pocket-noc-agent
```

### 3. Instalar como Serviço

```bash
# Copiar binário
sudo cp target/release/pocket-noc-agent /usr/local/bin/

# Criar usuário de serviço
sudo useradd -r -s /bin/false pocketnoc

# Criar diretórios
sudo mkdir -p /var/lib/pocket-noc /var/log/pocket-noc
sudo chown pocketnoc:pocketnoc /var/lib/pocket-noc /var/log/pocket-noc

# Copiar arquivo de serviço
sudo cp systemd/pocket-noc-agent.service /etc/systemd/system/

# Ativar e iniciar
sudo systemctl daemon-reload
sudo systemctl enable pocket-noc-agent
sudo systemctl start pocket-noc-agent

# Verificar status
sudo systemctl status pocket-noc-agent
```

### 4. Testar Agent

```bash
# Health check (sem autenticação)
curl -k https://localhost:9443/health

# Gerar JWT (exemplo)
# Token temporário para testes
TOKEN="seu-token-aqui"

# Obter telemetria
curl -k -H "Authorization: Bearer $TOKEN" \
  https://localhost:9443/telemetry
```

### 5. Compilar e Instalar Controller

```bash
cd controller
./gradlew assembleDebug

# Instalar no Android via adb
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Ou build release (assinado)
./gradlew assembleRelease
```

## 🔐 Configuração de Segurança

### Gerar Certificado TLS (Desenvolvimento)

```bash
# Self-signed certificate válido por 365 dias
openssl req -x509 -newkey rsa:4096 -nodes \
  -out /etc/pocket-noc/cert.pem \
  -keyout /etc/pocket-noc/key.pem \
  -days 365

# Permissões corretas
sudo chmod 600 /etc/pocket-noc/key.pem
sudo chmod 644 /etc/pocket-noc/cert.pem
```

### Configuração com Nginx (Recomendado)

```bash
# Instalar Nginx
sudo apt install nginx certbot python3-certbot-nginx -y

# Criar config
sudo tee /etc/nginx/sites-available/pocket-noc > /dev/null <<EOF
upstream pocket_noc_agent {
    server 127.0.0.1:9443;
}

server {
    listen 80;
    server_name api.pocketnoc.local;

    location / {
        proxy_pass https://pocket_noc_agent;
        proxy_set_header Authorization \$http_authorization;
        proxy_ssl_verify off;  # Para self-signed em dev
    }
}
EOF

# Ativar
sudo ln -s /etc/nginx/sites-available/pocket-noc \
  /etc/nginx/sites-enabled/

sudo nginx -t
sudo systemctl reload nginx
```

### Gerar JWT Secret

```bash
# Gerar string aleatória segura (32+ caracteres)
openssl rand -base64 32

# Exemplo output:
# rZ7k+2jL9mQ4vX1bP8nH3wY6aT5cD0eF7gJ2kL4sM9oP1qR3tU5vW7xY9zA1bC3dE5fG7hJ9iK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3hJ5

# Salvar em arquivo (protegido)
echo "JWT_SECRET=rZ7k+2jL9mQ4vX1bP8nH3wY6aT5cD0eF7gJ2kL4sM9oP1qR3tU5vW7xY9zA1bC3dE5fG7hJ9iK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3hJ5" \
  > ~/.pocket-noc-env

chmod 600 ~/.pocket-noc-env
```

### Gerar Token JWT

```rust
// main.rs
use pocket_noc_agent::auth::JwtConfig;

fn main() {
    let secret = std::env::var("JWT_SECRET")
        .expect("JWT_SECRET env var required");
    
    let jwt_config = JwtConfig::new(secret, 24 * 3600); // 24 horas
    
    let token = jwt_config
        .generate_token("mobile-client", vec!["read".to_string()])
        .expect("Failed to generate token");
    
    println!("JWT Token: {}", token);
}
```

## 📱 Configurar Controller Android

### 1. Adicionar Servidor

No app:
1. Abrir "Settings" ou "Add Server"
2. Preencher:
   - **Name**: Ex: "Production Server"
   - **Host**: IP ou domain (ex: api.pocketnoc.local)
   - **Port**: 9443
   - **Token**: JWT token gerado acima

### 2. Testar Conexão

1. Abrir servidor na lista
2. Verificar se Dashboard carrega
3. Se erro: Ver logs no celular (Logcat)

### 3. Permitir Self-Signed Certs (Dev Only)

Em `RetrofitClient.kt`:

```kotlin
val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
})

val sslContext = SSLContext.getInstance("SSL")
sslContext.init(null, trustAllCerts, SecureRandom())

val httpClient = OkHttpClient.Builder()
    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
    .hostnameVerifier { _, _ -> true }
    .build()
```

## 🔄 Rede Privada (Tailscale)

### Setup Servidor

```bash
# Instalar Tailscale
curl -fsSL https://tailscale.com/install.sh | sh

# Conectar
sudo tailscale up

# Obter IP Tailscale
tailscale ip -4
# Output: 100.x.x.x
```

### Setup Android

1. Baixar "Tailscale" do Google Play
2. Abrir app e fazer login
3. Conectar à VPN
4. No Controller, usar IP Tailscale:
   - Host: `100.x.x.x` (do servidor)
   - Port: 9443

## 📊 Verificação de Instalação

```bash
# Status do serviço
sudo systemctl status pocket-noc-agent

# Logs em tempo real
sudo journalctl -u pocket-noc-agent -f

# Verificar porta aberta
sudo ss -tlnp | grep 9443

# Testar API
curl -k https://localhost:9443/health

# Uso de recursos
ps aux | grep pocket-noc-agent
```

## 🐛 Troubleshooting

### Agent não inicia

```bash
# Ver erro completo
sudo journalctl -u pocket-noc-agent -n 50

# Tentar rodar manualmente
/usr/local/bin/pocket-noc-agent
```

### Erro "Address already in use"

```bash
# Verificar o que está usando porta 9443
sudo lsof -i :9443

# Matar processo
sudo kill -9 <PID>
```

### Erro SSL/TLS no Android

```bash
# Verificar certificado
openssl s_client -connect localhost:9443

# Para development, aceitar self-signed no código
```

### Controller não conecta

1. Verificar conectividade de rede
2. Testar `ping` ao host
3. Aumentar timeout em `RetrofitClient`:
   ```kotlin
   .connectTimeout(30, TimeUnit.SECONDS)
   ```

## 🔄 Atualizações

### Agent

```bash
cd server-controller
git pull origin main
cd agent

cargo build --release
sudo cp target/release/pocket-noc-agent /usr/local/bin/

sudo systemctl restart pocket-noc-agent
```

### Controller

```bash
cd server-controller/controller

./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 📚 Próximas Etapas

1. [ ] Configurar backup de logs
2. [ ] Implementar alertas (Slack, email)
3. [ ] Configurar Prometheus + Grafana
4. [ ] Documentar runbooks de incident
5. [ ] Testar failover e disaster recovery

## 🆘 Suporte

- Documentação: [README.md](../README.md)
- API: [docs/API.md](./API.md)
- Segurança: [docs/SECURITY.md](./SECURITY.md)
- Issues: [GitHub](https://github.com/seu-usuario/server-controller/issues)

---

**Status**: ✅ Production-ready
