# 🚀 LEI-ME PRIMEIRO - Pocket NOC

## ⚠️ Antes de começar

Este é um projeto de **infraestrutura sensível** com **tokens e chaves secretas**. Siga as instruções abaixo para configurar de forma **SEGURA**.

## 🔐 Configuração Inicial (CRÍTICO)

### Passo 1: Clonar o repositório

```bash
git clone https://github.com/Munique-Feitoza/server-controller.git
cd server-controller
```

### Passo 2: Criar arquivos de configuração local (NÃO versionados)

```bash
# Agente Rust
cat > .env << EOF
# Gere com: openssl rand -base64 32
POCKET_NOC_SECRET=seu-secret-aqui-minimo-32-bytes

# Opcional: API Key
# POCKET_NOC_API_KEY=sua-chave-aqui
EOF

# Controller Android
mkdir -p controller
cp .env.example controller/local.properties
# Edite controller/local.properties com seus valores
nano controller/local.properties
```

### Passo 3: Verificar .gitignore

Estes arquivos **NUNCA** devem ser commitados:

```bash
# Verificar
cat .gitignore | grep -E "\.env|local\.properties|\.pem|\.key"

# Deve mostrar:
# .env
# local.properties
# *.pem
# *.key
```

## 📦 Arquitetura do Projeto

```
Pocket NOC = Agente Rust + Controller Android

┌─────────────────────────────────────┐
│   Servidor Linux (Rust Agent)       │
│   Porta: 9443 (HTTP/HTTPS)          │
│   ├─ /health (público)              │
│   ├─ /telemetry (JWT obrigatório)   │
│   ├─ /services (JWT obrigatório)    │
│   ├─ /commands (JWT obrigatório)    │
│   └─ /metrics (JWT obrigatório)     │
└─────────────────────────────────────┘
              ↑
              │ HTTPS + JWT
              ↓
┌─────────────────────────────────────┐
│   Smartphone Android                │
│   ├─ Dashboard em tempo real         │
│   ├─ Monitoramento de serviços      │
│   ├─ Execução de comandos           │
│   └─ Visualização de métricas       │
└─────────────────────────────────────┘
```

## 🔑 Gerenciar Secrets Seguramente

### Gerar Secret para JWT

```bash
# Gere uma chave aleatória de 32+ bytes
openssl rand -base64 32

# Resultado: algo como:
# a3fH2kL9mQ0pR4sT7uV2wX5yZ8cB1dE4fG7

# Coloque em .env
POCKET_NOC_SECRET=a3fH2kL9mQ0pR4sT7uV2wX5yZ8cB1dE4fG7
```

### Gerar Token JWT (para testes)

Após iniciar o agente, gere um token:

```bash
# Exemplo simples (para desenvolvimento)
echo "Use um JWT decoder online para criar um token com:"
# sub: seu-usuario
# exp: $(date +%s) + 3600  (uma hora no futuro)
# scopes: ["read", "write"]
```

### Android: Configurar BuildConfig

Edite `controller/local.properties`:

```properties
POCKET_NOC_SERVER_URL=http://seu-servidor:9443/
POCKET_NOC_JWT_TOKEN=seu-token-jwt-aqui
USE_HTTPS=false  # true em produção com HTTPS
```

## 🚀 Compilar e Executar

### Agente Rust

```bash
cd agent/

# Debug
cargo run

# Release (otimizado)
cargo build --release

# Testes
cargo test --lib
```

### Controller Android

```bash
cd controller/

# Build APK de debug
./gradlew assembleDebug

# Build APK release
./gradlew assembleRelease

# Instalar em device
./gradlew installDebug
```

## ✅ Validar Segurança

### Teste 1: Rota pública (sem auth)

```bash
curl http://localhost:9443/health
# Deve retornar 200 OK
```

### Teste 2: Rota protegida SEM token

```bash
curl http://localhost:9443/telemetry
# Deve retornar 401 Unauthorized
```

### Teste 3: Rota protegida COM token

```bash
TOKEN="seu-jwt-aqui"
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:9443/telemetry
# Deve retornar 200 OK + JSON
```

## 📚 Documentação

Leia os arquivos nesta ordem:

1. **Este arquivo** (README.FIRST.md) ← Você está aqui
2. **README.md** - Visão geral do projeto
3. **docs/SETUP.md** - Guia detalhado de setup
4. **docs/SECURITY.md** - Política de segurança
5. **docs/API.md** - Documentação dos endpoints
6. **docs/ARCHITECTURE.md** - Design da arquitetura
7. **DEPLOY.md** - Guia de deploy para produção

## 🔄 Commits Temáticos

Este projeto está organizado em commits específicos (ler com `git log`):

- **chore**: Configuração de segurança (.env, .gitignore)
- **docs**: Documentação e guias
- **feat(agent)**: Agente Rust
- **feat(controller)**: App Android
- **security**: Autenticação JWT
- **test**: Testes de segurança

## ⚠️ NÃO FAÇA ISTO

❌ **NUNCA commite:**
- `.env` com secrets reais
- `local.properties` com tokens
- Certificados `.pem` ou `.key`
- Chaves privadas de qualquer tipo

❌ **NUNCA coloque em código:**
- Senhas hardcoded
- URLs de produção em commits de teste
- Tokens JWT no source code
- IPs privados em código (use variáveis!)

✅ **SEMPRE:**
- Use `.env.example` como template
- Armazene secrets em variáveis de ambiente
- Use GitHub Secrets para CI/CD
- Rotacione secrets regularmente

## 🐛 Troubleshooting

### Agente não inicia

```bash
# Error: "Secret too short"
# Solução: POCKET_NOC_SECRET deve ter mínimo 32 bytes

# Error: "Failed to bind port 9443"
# Solução: Mude porta em main.rs ou libere a porta:
sudo ufw allow 9443
```

### Erro 401 no Controller

```bash
# Verificar se:
1. Token está correto em local.properties
2. Token não expirou (máx 1 hora de validade)
3. Mesma chave secreta em ambos os lados (agent e controller)
4. Header está "Authorization: Bearer TOKEN"
```

### App Android não conecta

```bash
# Verificar:
1. Agente rodando: curl http://seu-ip:9443/health
2. URL correta em local.properties
3. Firewall permite tráfego na porta 9443
4. Mesmo Wi-Fi (development) ou Tailscale (produção)
```

## 🆘 Suporte

Se tiver problemas:

1. Cheque **docs/SETUP.md** para instruções detalhadas
2. Veja **DEPLOY.md** para troubleshooting
3. Rode **test_jwt_security.sh** para validar JWT
4. Consulte **SECURITY_AUDIT_RESULTS.md** para specs

## 📋 Checklist: Antes de Produção

- [ ] Secret gerado com `openssl rand -base64 32`
- [ ] `.env` preenchido e **não versionado**
- [ ] `local.properties` preenchido e **não versionado**
- [ ] HTTPS configurado com certificado válido
- [ ] Testes passando: `cargo test --lib`
- [ ] APK compilado e assinado
- [ ] Firewall configurado
- [ ] Logs auditáveis habilitados
- [ ] Backup da chave secreta em local seguro

## 🎯 Próximos Passos

1. ✅ Ler este arquivo
2. ✅ Executar `./gradlew build` no controller
3. ✅ Executar `cargo build` no agent
4. ✅ Configurar `.env` e `local.properties`
5. ✅ Testar com `curl` / Postman
6. ✅ Deploy para produção

---

**Status**: 🟢 Produção Pronta  
**Última atualização**: 11/03/2026  
**Mantido por**: Munique Feitoza
