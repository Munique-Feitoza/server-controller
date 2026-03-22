# Pocket NOC Controller - App Android para Monitoramento

Um aplicativo nativo Android em Kotlin que conecta com os agentes Rust para monitorar infraestrutura em tempo real, receber alertas e executar ações de emergência.

## 🎯 Características

- ✅ **Dashboard em tempo real**: Telemetria atualizada constantemente
- ✅ **Semáforo de status**: Verde/Amarelo/Vermelho visual intuitivo
- ✅ **Componentes reusáveis**: Cards de métrica, barras de progresso, botões de ação
- ✅ **Autenticação JWT**: Suporte a tokens para comunicação segura
- ✅ **Arquitetura MVVM**: Clean architecture com ViewModel e StateFlow
- ✅ **Jetpack Compose**: UI moderna e reativa
- ✅ **Coroutines**: Operações assíncronas não-bloqueantes
- ✅ **Error handling**: Tratamento robusto de erros de rede

## 📦 Dependências Principais

```gradle
Jetpack Compose      = UI moderna
Retrofit 2           = Cliente HTTP
Coroutines           = Async/await
Hilt                 = Injeção de dependência
Material 3           = Design system
```

## 🚀 Compilação e Instalação

### Pré-requisitos

- Android SDK 24+ (API Level 24)
- Android Studio Flamingo ou superior
- Kotlin 1.9+

### Build

```bash
cd controller
./gradlew assembleDebug
```

### Instalar no Dispositivo

```bash
./gradlew installDebug
# ou via adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build Release (assinado)

```bash
./gradlew assembleRelease
# Assinar com sua keystore
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 🏗️ Arquitetura

### MVVM (Model-View-ViewModel)

```
UI (Composables)
    ↓
ViewModel (DashboardViewModel)
    ↓
Repository (ServerRepository)
    ↓
API Service (AgentApiService via Retrofit)
```

### Camadas

1. **Data Layer**
   - `models/`: Data classes (SystemTelemetry, ServiceInfo, etc)
   - `api/`: Retrofit service e client HTTP
   - `repository/`: Abstração de dados

2. **UI Layer**
   - `screens/`: Telas completas (DashboardScreen)
   - `components/`: Componentes reusáveis
   - `viewmodels/`: Lógica de apresentação
   - `theme/`: Design system

3. **Utils**
   - Extensões e utilitários

## 📱 Telas Principais

### 1. Dashboard (Principal)

Mostra:

- Semáforo de status (verde/amarelo/vermelho)
- Uso de CPU (global + por core)
- Uso de RAM e Swap
- Espaço em disco
- Temperatura (se disponível)
- Uptime e Load Average
- Botão "View Full Details"

**Estado**: Em desenvolvimento

```kotlin
@Composable
fun DashboardScreen(viewModel, serverUrl, token, onNavigate)
```

### 2. Server Details (Detalhes Completo)

Será implementado para:

- Gráficos de histórico (CPU, RAM, Disco)
- Lista de serviços e seus status
- Botões de ação rápida (restart nginx, docker, etc)
- Logs de comandos executados

**Estado**: Planejado

### 3. Server List (Lista de Servidores)

Será implementado para:

- Lista de todos os servidores configurados
- Status resumido de cada um
- Adição/remoção de servidores
- Seleção de servidor ativo

**Estado**: Planejado

## 🔐 Segurança

### Autenticação JWT

Todos os requests (exceto `/health`) incluem o header:

```
Authorization: Bearer <JWT_TOKEN>
```

O token é armazenado localmente:

```kotlin
// TODO: Usar EncryptedSharedPreferences
PreferenceManager.getDefaultSharedPreferences(context)
    .edit()
    .putString("jwt_token", token)
    .apply()
```

### HTTPS/TLS

```kotlin
val httpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .build()
```

**Recomendação**: Use certificado self-signed em desenvolvimento, certificates válidos em produção.

### API Key Alternative

Se JWT não estiver disponível, implementar fallback com API Key:

```kotlin
@GET("/telemetry")
suspend fun getTelemetry(
    @Header("X-API-Key") apiKey: String
): SystemTelemetry
```

## 📊 Exemplos de Uso

### Fetch Telemetria

```kotlin
val viewModel = DashboardViewModel(repository)
viewModel.fetchTelemetry(serverUrl, token)

// Observar estado
viewModel.telemetryState.collect { state ->
    when (state) {
        is TelemetryUiState.Loading -> showLoader()
        is TelemetryUiState.Success -> showData(state.telemetry)
        is TelemetryUiState.Error -> showError(state.message)
    }
}
```

### Executar Comando de Emergência

```kotlin
val result = repository.executeCommand("restart_nginx", token)
result.onSuccess { commandResult ->
    showToast("Nginx reiniciado: ${commandResult.exitCode}")
}
result.onFailure { error ->
    showToast("Erro: ${error.message}")
}
```

## 🐛 Troubleshooting

### Erro de conexão

```
javax.net.ssl.SSLHandshakeException
```

Solução: Adicionar certificado self-signed ao projeto ou validar certificate chain.

### Retrofit timeout

```
java.net.SocketTimeoutException
```

Solução: Aumentar timeout em RetrofitClient:

```kotlin
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)
```

### ViewModel null

Solução: Implementar Hilt para injeção de dependência ou usar `ViewModelProvider.Factory`.

## 📝 TODOs

- [ ] Implementar navegação com Compose Navigation
- [ ] Tela de detalhes do servidor com gráficos
- [ ] Tela de lista de servidores
- [ ] Widget na tela inicial (semáforo)
- [ ] Hilt para injeção de dependência
- [ ] Armazenamento seguro de tokens (EncryptedSharedPreferences)
- [ ] Refresh automático (polling ou WebSocket)
- [ ] Notificações push para alertas
- [ ] Modo offline com cache local
- [ ] Dark/Light theme toggle

## 🔄 Próximos Passos

1. Implementar navegação entre telas
2. Criar tela de autenticação/configuração de servidores
3. Adicionar gráficos em tempo real
4. Implementar widget de status na tela inicial
5. Integrar notificações de alerta

## 📝 Licença

GPL-2.0

---

**Relacionado**: [Agent Rust](../agent/README.md)
