# 🚀 Pocket NOC - Guia de Setup e Deployment

> **🔒 SEGURANÇA EM PRIMEIRO LUGAR**

Este documento descreve como configurar o Pocket NOC com as melhores práticas de segurança.

## ⚡ Quick Start (Desenvolvimento)

### 1. Clonar o repositório
```bash
git clone https://github.com/seu-usuario/server-controller.git
cd server-controller
```

### 2. Configurar variáveis de ambiente (CRÍTICO)
```bash
# Copiar template
cp .env.example .env

# Editar .env com suas credenciais (NUNCA commitar .env)
# Mínimo 32 bytes para segurança OWASP
openssl rand -base64 32  # Gera secret aleatório
```

**IMPORTANTE**: O arquivo `.env` está no `.gitignore` - ninguém pode ver seus secrets!

### 3. Build do Agente Rust
```bash
cd agent
POCKET_NOC_SECRET="seu-secret-aqui-com-32-bytes" cargo run --release
```

O agente vai iniciar em **http://localhost:9443** (HTTP para teste)

### 4. Testar as rotas

#### ✅ Rota pública (SEM autenticação)
```bash
curl http://localhost:9443/health
# Resposta: 200 OK
```

#### 🔒 Rota protegida (SEM token - deve retornar 401)
```bash
curl http://localhost:9443/telemetry
# Resposta: 401 Unauthorized
```

#### 🔐 Rota protegida (COM token válido - deve retornar 200)
```bash
# Gerar um token JWT válido e usar:
curl -H "Authorization: Bearer seu-token-jwt-aqui" \
  http://localhost:9443/telemetry
# Resposta: 200 OK com dados JSON
```

---

## 🏭 Deploy em Produção

### 1. Gerar Secrets Seguros

```bash
# JWT Secret (mínimo 32 bytes)
POCKET_NOC_SECRET=$(openssl rand -base64 32)
echo "POCKET_NOC_SECRET=$POCKET_NOC_SECRET"

# API Key (opcional, também 32 bytes)
POCKET_NOC_API_KEY=$(openssl rand -base64 32)
echo "POCKET_NOC_API_KEY=$POCKET_NOC_API_KEY"
```

### 2. Configurar no servidor

```bash
# Via arquivo .env (recomendado)
cat > /opt/pocket-noc/.env << EOF
POCKET_NOC_SECRET=$POCKET_NOC_SECRET
POCKET_NOC_API_KEY=$POCKET_NOC_API_KEY
RUST_LOG=warn
EOF

# OU via variáveis de ambiente do systemd (ver seção abaixo)
```

### 3. Certificados TLS/HTTPS (Let's Encrypt)

```bash
# Instalar certbot
sudo apt install certbot python3-certbot-nginx

# Gerar certificado para seu domínio
sudo certbot certonly --standalone -d seu-dominio.com

# Os certificados estarão em:
# /etc/letsencrypt/live/seu-dominio.com/fullchain.pem
# /etc/letsencrypt/live/seu-dominio.com/privkey.pem
```

### 4. Serviço systemd

Criar `/etc/systemd/system/pocket-noc.service`:

```ini
[Unit]
Description=Pocket NOC Agent - Infrastructure Monitoring
After=network.target

[Service]
Type=simple
User=pocket-noc
WorkingDirectory=/opt/pocket-noc
Environment="POCKET_NOC_SECRET=seu-secret-aqui"
Environment="RUST_LOG=warn"
ExecStart=/opt/pocket-noc/agent --port 9443 --https
Restart=on-failure
RestartSec=10

# Segurança
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/opt/pocket-noc

[Install]
WantedBy=multi-user.target
```

Iniciar:
```bash
sudo systemctl daemon-reload
sudo systemctl enable pocket-noc
sudo systemctl start pocket-noc
sudo systemctl status pocket-noc
```

### 5. Reverse Proxy (Nginx com TLS)

```nginx
server {
    listen 443 ssl http2;
    server_name seu-dominio.com;

    ssl_certificate /etc/letsencrypt/live/seu-dominio.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/seu-dominio.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://127.0.0.1:9443;
        proxy_set_header Authorization $http_authorization;
        proxy_pass_header Authorization;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
    }
}

# Redirecionar HTTP → HTTPS
server {
    listen 80;
    server_name seu-dominio.com;
    return 301 https://$server_name$request_uri;
}
```

---

## 🛡️ Segurança - Checklist

### Antes de rodar em produção:

- [ ] **Secrets não estão no repositório**
  - Verificar: `git ls-files | grep -E "\.env|\.pem|\.key"`
  - Deve retornar vazio

- [ ] **JWT Secret configurado com 32+ bytes**
  - Verificar: `echo $POCKET_NOC_SECRET | wc -c` (deve ser > 33)

- [ ] **HTTPS/TLS ativado**
  - [ ] Certificado válido (não self-signed em produção)
  - [ ] Certificado não expirado: `openssl x509 -in cert.pem -noout -dates`

- [ ] **Firewall configurado**
  - [ ] Port 9443 aceita apenas requisições legítimas
  - [ ] Rate limiting ativado (ver Nginx config)

- [ ] **Logs e monitoramento**
  - [ ] Logs de auth em `/var/log/pocket-noc/auth.log`
  - [ ] Alertas para failed auth attempts

- [ ] **Backup de secrets**
  - [ ] .env armazenado com segurança (password manager, Vault, etc)
  - [ ] Chaves rotacionadas a cada 90 dias

### Testes de segurança:

```bash
# 1. Testar 401 sem token
curl -i https://seu-dominio.com/telemetry
# Deve retornar: 401 Unauthorized

# 2. Testar 401 com token inválido
curl -i -H "Authorization: Bearer invalid" \
  https://seu-dominio.com/telemetry
# Deve retornar: 401 Unauthorized

# 3. Testar 200 com token válido
curl -i -H "Authorization: Bearer $SEU_TOKEN_JWT" \
  https://seu-dominio.com/telemetry
# Deve retornar: 200 OK + JSON

# 4. Testar SSL/TLS
openssl s_client -connect seu-dominio.com:443 -showcerts
# Verificar: certificate chain, protocol TLSv1.3, ciphers strong
```

---

## 📱 Setup do Controller (Android)

### 1. Configurar URL do servidor no Kotlin

Editar `controller/app/src/main/java/com/pocketnoc/config/ApiConfig.kt`:

```kotlin
object ApiConfig {
    const val BASE_URL = "https://seu-dominio.com"  // HTTPS obrigatório
    const val JWT_TOKEN = "seu-token-jwt-aqui"  // Token gerado no servidor
}
```

### 2. Build e deploy
```bash
cd controller
./gradlew assembleRelease
# Gera: app/build/outputs/apk/release/app-release.apk
```

---

## 🔄 Renovação de Certificados (Let's Encrypt)

Automático com certbot:

```bash
# Criar cron job (systemd timer é melhor)
sudo systemctl enable certbot-renew.timer
sudo systemctl start certbot-renew.timer

# Ou cron manual
0 0 * * * /usr/bin/certbot renew --quiet
```

---

## 📊 Monitoramento

### Ver logs de autenticação:

```bash
# Logs do systemd
sudo journalctl -u pocket-noc -f  # Follow mode

# Filtrar apenas erros de auth
sudo journalctl -u pocket-noc | grep "401\|Unauthorized"
```

### Alertas recomendados:

- [x] Múltiplas falhas de auth (> 5 em 1 minuto) = possível ataque
- [x] Token expirado frequentemente = ajustar TTL ou cliente outdated
- [x] Certificado TLS expirando (< 7 dias) = renovar agora

---

## 🚨 Troubleshooting

### "401 Unauthorized" sempre

```bash
# 1. Verificar se token está no header corretamente
curl -v -H "Authorization: Bearer $TOKEN" https://seu-dominio.com/telemetry

# 2. Verificar se secret está correto
echo $POCKET_NOC_SECRET | wc -c  # Deve ser >= 33 bytes

# 3. Verificar se token não expirou
# Tokens expiram em 3600 segundos (1 hora) por padrão
```

### SSL_ERROR_HANDSHAKE_FAILURE

```bash
# Verificar certificado
openssl x509 -in /etc/letsencrypt/live/seu-dominio.com/fullchain.pem -text -noout

# Verificar se Nginx está servindo o certificado correto
sudo openssl s_client -connect seu-dominio.com:443 -showcerts
```

### Agente não inicia

```bash
# Verificar logs
sudo journalctl -u pocket-noc -n 50

# Verificar se porta 9443 está livre
sudo lsof -i :9443

# Verificar permissões do .env
ls -la /opt/pocket-noc/.env
```

---

## 📚 Referências

- [OWASP JWT Best Practices](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [Let's Encrypt Docs](https://letsencrypt.org/docs/)
- [Nginx Security Headers](https://www.nginx.com/blog/http-security-headers/)
- [Rust async-await patterns](https://tokio.rs/)

---

**Última atualização**: 11/03/2026  
**Versão**: 1.0.0  
**Autor**: Pocket NOC Team  
**Status**: ✅ Production Ready
