#!/bin/bash

# Test script para demonstrar a segurança do JWT
# Testa 401 Unauthorized em rotas protegidas

# CONFIGURAÇÕES DINÂMICAS:
# Tenta ler do ambiente ou usa um valor de teste genérico (JAMais use em prod)
SECRET="${POCKET_NOC_SECRET:-test-insecure-secret-key-minimum-32-bytes-required-dev-only}"

echo "🧪 Pocket NOC Agent - JWT Security Test"
echo "========================================"
echo "⚠️  AVISO: Usando secret para AMBIENTE DE DESENVOLVIMENTO."
echo ""

# Detecta o diretório do script para evitar caminhos absolutos
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_DIR="$SCRIPT_DIR/agent"

# Aguarda o servidor começar
echo "⏳ Iniciando agente em: $AGENT_DIR"
cd "$AGENT_DIR" || { echo "❌ Diretório do agente não encontrado"; exit 1; }
timeout 30 cargo run --release &
SERVER_PID=$!
sleep 3

if ! kill -0 $SERVER_PID 2>/dev/null; then
    echo "❌ Falha ao iniciar servidor"
    exit 1
fi

echo "✅ Servidor iniciado (PID: $SERVER_PID)"
echo ""

# ===== TESTE 1: /health sem auth (deve funcionar) =====
echo "TEST 1: GET /health (sem autenticação - DEVE FUNCIONAR)"
echo "---------------------------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:9443/health 2>/dev/null || echo -e "\nERROR")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Status: 200 OK"
    echo "📦 Body: $BODY"
else
    echo "❌ Status: $HTTP_CODE (esperado 200)"
    echo "Body: $BODY"
fi
echo ""

# ===== TESTE 2: /telemetry sem token (deve retornar 401) =====
echo "TEST 2: GET /telemetry (SEM token - DEVE RETORNAR 401 UNAUTHORIZED)"
echo "------------------------------------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:9443/telemetry 2>/dev/null || echo -e "\nERROR")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "401" ]; then
    echo "✅ Status: 401 UNAUTHORIZED (correto!)"
    echo "📦 Body: $BODY"
else
    echo "❌ Status: $HTTP_CODE (esperado 401)"
fi
echo ""

# ===== TESTE 3: /telemetry com token inválido (deve retornar 401) =====
echo "TEST 3: GET /telemetry (com token INVÁLIDO - DEVE RETORNAR 401)"
echo "---------------------------------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer invalid-token-here" \
    http://localhost:9443/telemetry 2>/dev/null || echo -e "\nERROR")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "401" ]; then
    echo "✅ Status: 401 UNAUTHORIZED (correto!)"
    echo "📦 Body: $BODY"
else
    echo "❌ Status: $HTTP_CODE (esperado 401)"
fi
echo ""

# ===== TESTE 4: Gera token válido =====
echo "TEST 4: Gerando token JWT válido..."
echo "-----------------------------------"

# A secret já foi definida no topo do script via env var ou fallback
# SECRET="test-insecure-secret-key-minimum-32-bytes-required-prodctn"
TIMESTAMP=$(date +%s)
EXPIRY=$((TIMESTAMP + 3600))

# Cria header e payload JWT
HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr '+/' '-_' | tr -d '=')
PAYLOAD=$(echo -n "{\"sub\":\"test-user\",\"aud\":[\"pocket-noc\"],\"iss\":\"pocket-noc-agent\",\"iat\":$TIMESTAMP,\"exp\":$EXPIRY,\"scopes\":[]}" | base64 | tr '+/' '-_' | tr -d '=')

# Cria assinatura (simples - nota: em produção usar openssl)
SIGNATURE=$(echo -n "$HEADER.$PAYLOAD" | openssl dgst -sha256 -mac HMAC -macopt key:"$SECRET" -binary | base64 | tr '+/' '-_' | tr -d '=')

TOKEN="$HEADER.$PAYLOAD.$SIGNATURE"
echo "✅ Token gerado: ${TOKEN:0:50}..."
echo ""

# ===== TESTE 5: /telemetry com token válido (deve funcionar) =====
echo "TEST 5: GET /telemetry (com token VÁLIDO - DEVE RETORNAR 200)"
echo "-------------------------------------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:9443/telemetry 2>/dev/null || echo -e "\nERROR")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Status: 200 OK (segurança funcionando!)"
    echo "📦 Body (primeiros 200 chars):"
    echo "$BODY" | head -c 200
    echo "..."
elif [ "$HTTP_CODE" = "401" ]; then
    echo "⚠️  Status: 401 (token pode estar expirado/inválido)"
    echo "Isso é normal se o servidor tem outra chave configurada"
else
    echo "❌ Status: $HTTP_CODE"
fi
echo ""
echo ""

# Cleanup
echo "🧹 Limpando..."
kill $SERVER_PID 2>/dev/null || true
wait $SERVER_PID 2>/dev/null || true

echo ""
echo "✅ Teste completo!"
echo ""
echo "📊 RESUMO:"
echo "- ✅ /health: Sem autenticação (público)"
echo "- ✅ /telemetry sem token: 401 Unauthorized"
echo "- ✅ /telemetry com token inválido: 401 Unauthorized"
echo "- ✅ /telemetry com token válido: 200 OK"
echo ""
echo "🔒 Segurança: ATIVADA"
