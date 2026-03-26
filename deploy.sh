#!/bin/bash

# Script de Deploy do Pocket NOC Agent com Compilação MUSL
# Compila em Linux musl-compatible e faz deploy em todos os 4 servidores

set -e

echo "🔨 Compilando Pocket NOC Agent para musl..."
cd "$(dirname "$0")/agent"

# Instala o target musl se não estiver
rustup target add x86_64-unknown-linux-musl 2>/dev/null || true

# Compila
cargo build --release --target x86_64-unknown-linux-musl

BINARY="target/x86_64-unknown-linux-musl/release/pocket-noc-agent"

if [ ! -f "$BINARY" ]; then
    echo "❌ Compilação falhou! Binário não encontrado."
    exit 1
fi

echo "✅ Binário compilado: $BINARY"
echo ""

# Dados dos servidores
declare -A SERVERS
SERVERS[1]="1.2.3.4"
SERVERS[2]="5.6.7.8"
SERVERS[3]="9.10.11.12"
SERVERS[4]="13.14.15.16"

declare -A NAMES
NAMES[1]="server1"
NAMES[2]="server2"
NAMES[3]="server3"
NAMES[4]="server4"

SSH_USER="deploy"

# Deploy em cada servidor
for i in 1 2 3 4; do
    IP=${SERVERS[$i]}
    NAME=${NAMES[$i]}
    
    echo "🚀 Deployando em $i: $NAME ($IP)..."
    
    # Mata processo antigo
    ssh -o ConnectTimeout=5 "$SSH_USER@$IP" "sudo pkill -9 pocket-noc-agent 2>/dev/null || true" || true
    sleep 1
    
    # Copia binário
    scp -o ConnectTimeout=5 "$BINARY" "$SSH_USER@$IP:/tmp/pocket-noc-agent" || {
        echo "❌ Falha ao copiar arquivo para $NAME"
        continue
    }
    
    # Instala
    ssh -o ConnectTimeout=5 "$SSH_USER@$IP" "sudo mv /tmp/pocket-noc-agent /usr/local/bin/pocket-noc-agent && sudo chmod +x /usr/local/bin/pocket-noc-agent" || {
        echo "❌ Falha ao instalar em $NAME"
        continue
    }
    
    # Reinicia serviço
    ssh -o ConnectTimeout=5 "$SSH_USER@$IP" "sudo systemctl daemon-reload && sudo systemctl restart pocket-noc-agent" || {
        echo "❌ Falha ao reiniciar serviço em $NAME"
        continue
    }
    
    # Verifica status
    sleep 2
    STATUS=$(ssh -o ConnectTimeout=5 "$SSH_USER@$IP" "systemctl is-active pocket-noc-agent" 2>/dev/null || echo "unknown")
    
    if [ "$STATUS" = "active" ]; then
        echo "✅ $NAME ($IP) - Serviço ativo!"
    else
        echo "⚠️  $NAME ($IP) - Status: $STATUS (verifica logs com: journalctl -u pocket-noc-agent -n 20)"
    fi
    echo ""
done

echo "✨ Deploy concluído!"
