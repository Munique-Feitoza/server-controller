# Pocket NOC Controller - Android App

Aplicativo nativo Android em Kotlin que monitora infraestrutura em tempo real via comunicação com agentes Rust.

## 🚀 Quick Start

```bash
cd controller
./gradlew assembleDebug
./gradlew installDebug
```

## 📱 Arquitetura

- **UI**: Jetpack Compose (MVVM)
- **Networking**: Retrofit 2 + OkHttp
- **Async**: Kotlin Coroutines
- **DI**: Hilt (planejado)

## 📚 Estrutura

```
app/src/main/java/com/pocketnoc/
├── data/
│   ├── models/      # Data classes
│   ├── api/         # Retrofit services
│   └── repository/  # Data repositories
├── ui/
│   ├── screens/     # Telas
│   ├── components/  # Componentes reusáveis
│   ├── viewmodels/  # ViewModels
│   └── theme/       # Design system
└── utils/           # Utilitários
```

## 📖 Documentação Completa

Veja [app/README.md](./app/README.md) para detalhes completos.
