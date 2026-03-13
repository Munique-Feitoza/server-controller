# 🚀 Guia de Setup e Instalação - Pocket NOC

Este guia orienta a instalação completa do **Pocket NOC Agent** nos seus servidores Linux e do **Pocket NOC Controller** no seu dispositivo Android.

---

## 📋 Pré-requisitos

### Agente (Servidor)

- **Linux**: Ubuntu 22.04+, Debian 12+, Arch Linux ou Manjaro.
- **Rust**: Versão 1.75+ (Stable).
- **Acesso**: Usuário com privilégios de `sudo`.
- **Recursos**: Mínimo de 100MB de RAM disponível.

### Controller (Celular)

- **Android**: Versão 8.0 (Oreo) ou superior.
- **Desenvolvedor**: Opções de desenvolvedor e Depuração USB ativadas.

---

## 🛠️ Passo 1: Configuração do Servidor (Agente)

O Agente é o serviço que coleta as métricas. Ele deve ser compilado e rodar como um serviço do sistema.

### 1. Compilação

```bash
# Clone o repositório se ainda não o fez
git clone https://github.com/seu-usuario/server-controller.git
cd server-controller/agent

# Compile em modo release para máxima performance
cargo build --release
```

### 2. Instalação como Serviço (Systemd)

O binário será gerado em `target/release/pocket-noc-agent`.

```bash
# Copie o binário para o diretório de binários do sistema
sudo cp target/release/pocket-noc-agent /usr/local/bin/

# Configure o arquivo de serviço
sudo cp systemd/pocket-noc-agent.service /etc/systemd/system/

# Recarregue os daemons e inicie o serviço
sudo systemctl daemon-reload
sudo systemctl enable --now pocket-noc-agent
```

---

## 📱 Passo 2: Configuração do Android (Controller)

### 1. Gerar o APK

```bash
cd ../controller
./gradlew assembleDebug
```

### 2. Instalação no Celular

Conecte seu celular via USB e execute:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔍 Solução de Problemas (Troubleshooting)

### Erro: `INSTALL_FAILED_USER_RESTRICTED`

Se ao tentar instalar o app no celular você receber este erro:
> *The application could not be installed: INSTALL_FAILED_USER_RESTRICTED Installation via USB is disabled.*

**Como resolver:**

1. Vá em **Configurações** no seu celular Android.
2. Acesse **Opções do Desenvolvedor**.
3. Procure pela seção de **Depuração**.
4. Ative a chave **Instalar via USB** (ou *Install via USB*).
   - *Dica:* Em aparelhos Xiaomi, isso pode exigir que você esteja logado na sua Mi Account.
5. Tente instalar novamente.

### Falha na Conexão (Timeout)

1. Verifique se a porta **9443** está liberada no firewall do servidor:

   ```bash
   sudo ufw allow 9443/tcp
   ```

2. Certifique-se de que o Agente está rodando:

   ```bash
   sudo systemctl status pocket-noc-agent
   ```

---

## 🔐 Configuração do JWT Secret

Para que o App consiga falar com o Agente, você precisa configurar um Secret compartilhado:

1. Crie um arquivo `.env` na raiz do Agente no servidor.
2. Adicione a chave:

   ```bash
   JWT_SECRET=sua_chave_secreta_aqui_muito_longa
   ```

3. Reinicie o agente:

   ```bash
   sudo systemctl restart pocket-noc-agent
   ```

---

**Dúvidas?** Consulte a [Documentação de Segurança](./SECURITY.md) ou abra uma issue no repositório.
