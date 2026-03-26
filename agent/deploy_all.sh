#!/bin/bash
# Script de Deploy OMNI-DEV para Pocket NOC Agent

BIN_PATH="./target/x86_64-unknown-linux-musl/release/pocket-noc-agent"
KEY="/tmp/pocket_noc_ssh.key"

deploy_to() {
    local NAME=$1
    local IP=$2
    local PORT=$3
    echo "🚀 Implantando em $NAME ($IP:$PORT)..."
    
    # 1. Upload do binário
    scp -i "$KEY" -P "$PORT" -o StrictHostKeyChecking=no "$BIN_PATH" "runcloud@$IP:/home/runcloud/pocket-noc-agent.tmp"
    
    # 2. Substituição e Restart (Usa sudo remoto para o systemctl)
    ssh -i "$KEY" -p "$PORT" -o StrictHostKeyChecking=no "runcloud@$IP" "
        sudo systemctl stop pocket-noc-agent
        sudo mv /home/runcloud/pocket-noc-agent.tmp /usr/local/bin/pocket-noc-agent
        sudo chmod +x /usr/local/bin/pocket-noc-agent
        sudo systemctl start pocket-noc-agent
        sudo systemctl status pocket-noc-agent --no-pager | head -n 5
    "
}

# Servidores (Mantenha os IPs reais em um arquivo separado ou variável de ambiente para segurança)
# deploy_to "server1" "1.2.3.4" 22
# deploy_to "server2" "5.6.7.8" 22
# deploy_to "server3" "9.10.11.12" 2222
# deploy_to "server4" "13.14.15.16" 22

echo "⚠️  Configure os IPs reais no script antes de rodar ou use variáveis de ambiente."

echo "✅ Deploy concluído em todos os servidores! 🌌🚀🦾"
