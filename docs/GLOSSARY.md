# Glossário Técnico — Pocket NOC

> Definições dos termos técnicos utilizados na documentação e no código-fonte.  
> Autora: **Munique Alves Pacheco Feitoza**  
> Última atualização: Abril de 2026

---

## A

**Action Center**  
Módulo do app Android que permite executar comandos remotos nos servidores via whitelist. Sem shell intermediário — seguro contra injeção.

**AlertManager**  
Componente do agente responsável por avaliar thresholds de telemetria e disparar notificações via ntfy.sh. Implementa deduplicação com cooldown de 30 minutos por `(AlertType, componente)`.

**Arc\<Mutex\<T\>\>**  
Padrão de concorrência em Rust. `Arc` (Atomic Reference Counting) permite compartilhamento entre threads, `Mutex` garante exclusão mútua no acesso ao dado. Usado para proteger estado mutável entre o servidor HTTP e as tasks background.

**AuditLog**  
Ring buffer de 1000 entradas que registra todas as requisições HTTP processadas pelo agente (endpoint, método, IP, status code, timestamp).

**Axum**  
Framework web assíncrono para Rust, construído sobre Tokio e Tower. Utilizado como servidor HTTP do agente.

---

## B

**Biometria**  
Autenticação por impressão digital (fingerprint) ou reconhecimento facial no app Android, via AndroidX Biometric API. Obrigatória para acessar o app.

---

## C

**Cache L1**  
Cache em memória do `TelemetryCollector` com TTL de 5 segundos. Evita leitura repetida de `/proc` e subprocessos a cada requisição.

**CAP_KILL / CAP_NET_ADMIN**  
Linux capabilities concedidas ao processo do agente via systemd. `CAP_KILL` permite enviar sinais a processos (kill). `CAP_NET_ADMIN` permite manipular regras de firewall (iptables).

**Circuit Breaker**  
Padrão de resiliência implementado no WatchdogEngine. Três estados: **Closed** (normal), **Open** (serviço indisponível, remediações bloqueadas) e **HalfOpen** (testando recuperação). Previne loops infinitos de restart.

**Compose (Jetpack Compose)**  
Toolkit declarativo de UI do Android. As telas do Pocket NOC são funções `@Composable` que reagem automaticamente a mudanças de estado via `StateFlow`.

**Controller**  
Nome do app Android que serve como interface de controle e monitoramento. Conecta-se aos agentes via SSH tunnel.

---

## D

**DataStore**  
API do Android para persistência de preferências tipadas. Utilizado para thresholds de alerta e configurações de sessão.

**Defesa em Profundidade**  
Estratégia de segurança com múltiplas camadas independentes (perímetro, transporte, aplicação, defesa ativa, dados). A falha de uma camada não compromete as demais.

**Design System**  
Conjunto de tokens de design (cores, dimensões, shapes, tipografia) definidos em `AppColors.kt`, `Dimens.kt`, `Shapes.kt` e `Theme.kt`. Garante consistência visual em todas as telas.

---

## E

**EncryptedSharedPreferences**  
API do Android para armazenamento criptografado em repouso. Utiliza o Android KeyStore para proteger a master key. Guarda tokens JWT, chaves SSH e credenciais de servidor.

---

## H

**Hilt**  
Framework de injeção de dependência para Android baseado no Dagger. Configura automaticamente `Retrofit`, `Room`, `JwtUtils` e outros componentes.

**Honeypot**  
Paths falsos (`/wp-admin`, `/.env`, `/.git`, etc.) monitorados pelo middleware do agente. Acessos a esses paths indicam atividade de scanner/bot e são rastreados pelo `ThreatTracker`.

**HS256 (HMAC-SHA256)**  
Algoritmo de assinatura usado nos tokens JWT do Pocket NOC. Utiliza um segredo compartilhado (mínimo 32 bytes) para assinar e verificar tokens.

---

## J

**JSch**  
Biblioteca Java para SSH2. O Controller Android usa JSch para estabelecer túneis SSH (Local Port Forwarding) com os servidores.

**JWT (JSON Web Token)**  
Padrão de autenticação stateless. Cada requisição ao agente inclui um token JWT no header `Authorization: Bearer <token>`, que é validado pelo middleware.

---

## L

**Local Port Forwarding**  
Técnica SSH que mapeia uma porta local para uma porta remota. O app Android mapeia `localhost:9443` (no celular) para `127.0.0.1:9443` (no servidor) via túnel SSH.

---

## M

**Material 3**  
Design system do Google para Android. O Pocket NOC utiliza Material 3 com tema customizado (cores, shapes, tipografia).

**musl**  
Implementação leve da C standard library. O agente é compilado como binário musl estático (`x86_64-unknown-linux-musl`), resultando em um executável sem dependências de runtime (~4 MB).

**MVVM (Model-View-ViewModel)**  
Padrão arquitetural utilizado no Controller Android. **Model** (dados/rede), **View** (Compose screens), **ViewModel** (lógica de negócio + StateFlow).

---

## N

**ntfy.sh**  
Serviço de push notifications via HTTP. O agente envia alertas para um tópico ntfy, que são recebidos pelo app ntfy no celular. Sem dependência de Google Firebase.

**NOC (Network Operations Center)**  
Centro de Operações de Rede. O Pocket NOC coloca essa funcionalidade no bolso — monitoramento e controle de infraestrutura pelo celular.

---

## P

**PHP-FPM**  
FastCGI Process Manager para PHP. O agente monitora pools PHP-FPM por site (detecção automática Hosting), mostrando consumo de CPU e memória por site WordPress.

**Probe**  
Verificação de saúde executada pelo WatchdogEngine. Três tipos: **HTTP** (GET com timeout), **TCP** (socket connect), **Systemctl** (status do serviço).

---

## R

**Rate Limiting**  
Controle de taxa que limita requisições por IP (padrão: 60/min). Previne abuso e ataques de força bruta. Implementado como middleware Axum.

**Ring Buffer**  
Estrutura de dados circular (FIFO) com tamanho fixo. Usada no AuditLog (1000 entradas) e no WatchdogEventStore (500 eventos). Quando cheio, as entradas mais antigas são descartadas automaticamente.

**Room**  
Biblioteca de persistência Android que abstrai SQLite. O Pocket NOC usa Room para armazenar servidores, alertas e histórico de telemetria localmente.

**Retrofit**  
Biblioteca de cliente HTTP type-safe para Android. Define endpoints da API como interface Kotlin, gerando implementação automaticamente.

---

## S

**SERVER_ROLE**  
Variável de ambiente que define quais serviços o WatchdogEngine deve monitorar. Valores: `wordpress`, `wordpress-python`, `erp`, `python-nextjs`, `database`, `generic`.

**StateFlow**  
Fluxo de dados reativo do Kotlin coroutines. Os ViewModels expõem dados como `StateFlow`, que o Jetpack Compose observa para atualizar a UI automaticamente.

**Stealth Bind**  
Técnica de bind do agente exclusivamente em `127.0.0.1`. O agente é invisível a qualquer scan de porta externo — só acessível via túnel SSH.

---

## T

**TelemetryCollector**  
Componente central do agente que coleta métricas do sistema (CPU, RAM, disco, rede, temperatura, processos, serviços). Mantém cache L1 de 5 segundos.

**ThreatTracker**  
Componente de segurança que rastreia acessos a honeypot paths por IP. Após 5 acessos em 1 hora, o IP é banido via iptables e recebe um zip bomb.

**Tokio**  
Runtime assíncrono para Rust. O agente executa o servidor HTTP e todas as tasks background (alertas, watchdog, SSL check) no runtime Tokio multi-thread.

---

## W

**WatchdogEngine**  
Motor de auto-remediação que roda em loop independente (padrão: 30s). Executa probes nos serviços, gerencia circuit breakers e aciona ações corretivas (restart de serviço).

**WorkManager**  
API do Android para tarefas em background persistentes. O `AlertMonitoringWorker` usa WorkManager para polling periódico de alertas mesmo com o app fechado.

---

## Z

**Zero Trust**  
Modelo de segurança onde nenhuma requisição é confiável por padrão, independente da origem. Toda requisição ao agente é autenticada via JWT, mesmo dentro do túnel SSH.

**ZIP Bomb**  
Arquivo comprimido que se expande para 50 MB quando descomprimido. Servido como resposta a bots que acessam honeypot paths 5+ vezes. Neutraliza scanners automatizados.

---

> **Documentação escrita por Munique Alves Pacheco Feitoza**  
> Engenharia de Software — Análise e Desenvolvimento de Sistemas
