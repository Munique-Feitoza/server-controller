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

# Dados dos servidores (de local.properties)
declare -A SERVERS
SERVERS[1]="158.69.126.90"
SERVERS[2]="66.70.178.190"
SERVERS[3]="142.44.137.130"
SERVERS[4]="144.217.255.109"

declare -A NAMES
NAMES[1]="Host Genix"
NAMES[2]="HOST Viral"
NAMES[3]="Host WINUP"
NAMES[4]="WINUP"

SSH_USER="runcloud"

# Portas SSH por servidor (Host WINUP usa 2222)
declare -A PORTS
PORTS[1]="22"
PORTS[2]="22"
PORTS[3]="2222"
PORTS[4]="22"

# Deploy em cada servidor
for i in 1 2 3 4; do
    IP=${SERVERS[$i]}
    NAME=${NAMES[$i]}
    PORT=${PORTS[$i]}

    echo "🚀 Deployando em $i: $NAME ($IP:$PORT)..."

    # Mata processo antigo
    ssh -p "$PORT" -o ConnectTimeout=5 "$SSH_USER@$IP" "sudo pkill -9 pocket-noc-agent 2>/dev/null || true" || true
    sleep 1

    # Copia binario
    scp -P "$PORT" -o ConnectTimeout=5 "$BINARY" "$SSH_USER@$IP:/tmp/pocket-noc-agent" || {
        echo "❌ Falha ao copiar arquivo para $NAME"
        continue
    }

    # Instala
    ssh -p "$PORT" -o ConnectTimeout=5 "$SSH_USER@$IP" "sudo mv /tmp/pocket-noc-agent /usr/local/bin/pocket-noc-agent && sudo chmod +x /usr/local/bin/pocket-noc-agent" || {
        echo "❌ Falha ao instalar em $NAME"
        continue
    }

    # Reinicia servico
    ssh -p "$PORT" -o ConnectTimeout=5 "$SSH_USER@$IP" "sudo systemctl daemon-reload && sudo systemctl restart pocket-noc-agent" || {
        echo "❌ Falha ao reiniciar servico em $NAME"
        continue
    }

    # Verifica status
    sleep 2
    STATUS=$(ssh -p "$PORT" -o ConnectTimeout=5 "$SSH_USER@$IP" "systemctl is-active pocket-noc-agent" 2>/dev/null || echo "unknown")

    if [ "$STATUS" = "active" ]; then
        echo "✅ $NAME ($IP) - Servico ativo!"
    else
        echo "⚠️  $NAME ($IP) - Status: $STATUS (verifica logs com: journalctl -u pocket-noc-agent -n 20)"
    fi
    echo ""
done

echo "✨ Deploy concluído!"
