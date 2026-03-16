# 🛠️ Guia de Setup e Deployment

Bem-vinda ao guia de instalação do Pocket NOC Ultra. Siga estes passos para colocar sua infraestrutura sob monitoramento em poucos minutos.

## 🦀 1. Configurando o Agente no Servidor

### Requisitos

- Rust 1.70+ instalado.
- Sistema Linux (Preferencialmente Ubuntu 22.04+).
- Privilégios de Root (necessário para logs e gestão de processos).

### Compilação e Execução

```bash
# Clone o repositório no seu servidor
git clone https://github.com/Munique-Feitoza/pocket-noc.git
cd pocket-noc/agent

# Defina seu segredo de segurança (CRÍTICO)
export POCKET_NOC_SECRET="seu-segredo-de-no-minimo-32-caracteres"

# Compile com otimizações
cargo build --release

# Rode o agente
# Dica: rode via tmux ou screen, ou configure um serviço systemd (recomendado)
./target/release/pocket-noc-agent
```

### Criando um Serviço Systemd (Produção)

Para garantir que o agente inicie com o servidor:
`sudo nano /etc/systemd/system/pocket-noc-agent.service`

```ini
[Unit]
Description=Pocket NOC Agent
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/caminho/do/agente
Environment=POCKET_NOC_SECRET=seu-segredo-aqui
ExecStart=/caminho/do/agente/target/release/pocket-noc-agent
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

`sudo systemctl enable --now pocket-noc-agent`

---

## 📱 2. Configurando o Controller (Android)

### Arquivo local.properties

No diretório `controller/`, crie ou edite o arquivo `local.properties`:

```properties
# Credenciais do Servidor 1
POCKET_NOC_SERVER_1=ip.do.seu.servidor
POCKET_NOC_SERVER_NAME_1=Meu_Prod_Server
SSH_USER_1=root
SSH_HOST_1=ip.do.seu.servidor
SSH_KEY_CONTENT_GLOBAL=-----BEGIN OPENSSH PRIVATE KEY-----\nsua-chave-aqui\n-----END OPENSSH PRIVATE KEY-----

# Segredo JWT (Deve ser IGUAL ao do servidor)
POCKET_NOC_SECRET=seu-segredo-de-no-minimo-32-caracteres
```

### Instalação

Gere o APK e instale no seu smartphone:
`./gradlew assembleDebug`

---

## ❓ Troubleshooting

1. **"Erro de Conexão (SSH Tunnel Failure)"**: Verifique se o IP do servidor está correto e se a porta 22 (SSH) está aberta no seu provedor de cloud (AWS, DigitalOcean, etc).
2. **"401 Unauthorized"**: O segredo JWT no Android não bate com o segredo definido no enviroment do Rust.
3. **Logs não aparecem**: Certifique-se de que o Agente está rodando como Root para poder acessar o `journalctl`.

---
*Escrito com rigor técnico para o projeto de Engenharia de Software.*
