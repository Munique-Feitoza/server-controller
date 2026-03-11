# Contribuindo para Pocket NOC

Obrigado por considerar contribuir para o Pocket NOC! Este documento oferece diretrizes e instruções para colaboradores.

## 🤝 Tipos de Contribuições

### 🐛 Relatando Bugs

Se encontrar um bug:

1. Verifique se já não foi reportado em [Issues](https://github.com/seu-usuario/server-controller/issues)
2. Abra um novo issue com:
   - **Título**: Descrição clara do bug
   - **Reprodução**: Passos para reproduzir
   - **Comportamento esperado**: O que deveria acontecer
   - **Comportamento real**: O que está acontecendo
   - **Ambiente**: OS, versão Rust/Android, etc

**Template**:
```markdown
## Bug Report

### Descrição
[Descreva o bug]

### Como reproduzir
1. [Passo 1]
2. [Passo 2]
3. [Bug ocorre]

### Esperado
[O que deveria acontecer]

### Atual
[O que está acontecendo]

### Ambiente
- OS: [Linux/Android]
- Versão Rust: [1.70+]
- Android API: [24+]

### Logs
[Incluir logs relevantes]
```

### 💡 Sugerindo Melhorias

Tem uma ideia? Excelente!

1. Abra um issue com `[FEATURE]` no título
2. Descreva o problema que resolve
3. Explique a solução proposta
4. Liste exemplos de uso

**Template**:
```markdown
## Feature Request

### Problema
[Qual problema isso resolve?]

### Solução
[Descrição clara da feature]

### Exemplos de uso
```rust
// Exemplo de como seria usado
```

### Alternativas consideradas
[Outras soluções exploradas]
```

### 🔧 Contribuindo Código

#### Preparar Ambiente

**Para Agent (Rust)**:
```bash
git clone https://github.com/seu-usuario/server-controller.git
cd server-controller/agent

# Testar compilação
cargo check

# Executar testes
cargo test

# Executar linter
cargo clippy

# Formatar código
cargo fmt
```

**Para Controller (Kotlin)**:
```bash
cd server-controller/controller

# Build
./gradlew build

# Tests
./gradlew test

# Lint
./gradlew lint
```

#### Processo de Contribuição

1. **Fork o repositório**
   ```bash
   git clone https://github.com/seu-usuario/server-controller.git
   cd server-controller
   ```

2. **Criar branch para sua feature**
   ```bash
   git checkout -b feature/minha-feature
   ```

3. **Fazer mudanças**
   - Seguir guia de estilo (veja abaixo)
   - Adicionar testes
   - Atualizar documentação

4. **Testar localmente**
   ```bash
   # Rust
   cargo test
   cargo clippy
   cargo fmt --check

   # Kotlin
   ./gradlew test
   ./gradlew lint
   ```

5. **Commit com mensagem clara**
   ```bash
   git commit -m "feat: adiciona novo endpoint /status"
   ```

6. **Push para seu fork**
   ```bash
   git push origin feature/minha-feature
   ```

7. **Abrir Pull Request**
   - Descreva mudanças
   - Referencie issues relacionadas
   - Explique testes adicionados

---

## 📋 Guias de Estilo

### Rust

Seguir [Rust Style Guide](https://doc.rust-lang.org/1.0.0/style/):

```rust
// ✅ Bom
pub fn calculate_cpu_usage(metrics: &CpuMetrics) -> Result<f32> {
    if metrics.cores.is_empty() {
        return Err(AgentError::TelemetryError("No cores found".to_string()));
    }
    Ok(metrics.usage_percent)
}

// ❌ Evitar
pub fn calculate_cpu_usage(metrics: &CpuMetrics) -> f32 {
    metrics.cores.iter().map(|c| c.usage_percent).sum::<f32>() / metrics.cores.len() as f32
}

pub fn calculate_cpu_usage(m: &CpuMetrics) -> f32 {
    m.usage_percent.abs()
}
```

**Checklist Rust**:
- [ ] Sem `unwrap()` sem tratamento de erro
- [ ] Usar `Result<T, E>` para operações que podem falhar
- [ ] Usar `Option<T>` para valores opcionais
- [ ] Documentar funções públicas
- [ ] Testes unitários para lógica crítica

### Kotlin

Seguir [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Bom
@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // ...
    }
}

// ❌ Evitar
@Composable
fun MetricCard(title: String, value: String, m: Modifier = Modifier) {
    Column(modifier = m.fillMaxWidth()) { }
}
```

**Checklist Kotlin**:
- [ ] Nomes em camelCase
- [ ] Lambdas finais para funções de extensão
- [ ] Usar `?.let {}` para null safety
- [ ] Documentar funções públicas com KDoc
- [ ] Testes com JUnit e AndroidTest

---

## ✅ Pull Request Checklist

Antes de submeter PR:

- [ ] Branch criada a partir de `main`
- [ ] Código formatado (`cargo fmt` ou `./gradlew spotlessApply`)
- [ ] Sem warnings (`cargo clippy` ou `./gradlew lint`)
- [ ] Testes adicionados
- [ ] Testes passando localmente
- [ ] Documentação atualizada
- [ ] Commit messages claros
- [ ] Sem commits merge desnecessários
- [ ] Descrição clara do PR

**Template de PR**:
```markdown
## Descrição
[Breve descrição do que foi mudado]

## Tipo de Mudança
- [ ] 🐛 Bug fix
- [ ] ✨ Feature nova
- [ ] 📚 Documentação
- [ ] 🔄 Refactoring

## Issues Relacionadas
Fixes #123

## Como Testar
1. [Passo 1]
2. [Passo 2]
3. Verificar resultado esperado

## Screenshots (se aplicável)
[Imagens de UI changes]

## Checklist
- [ ] Código segue guia de estilo
- [ ] Testes adicionados
- [ ] Documentação atualizada
- [ ] Sem warnings/errors
```

---

## 🏗️ Estrutura de Features

### Feature Pequena (Bug fix, pequena melhoria)

```
┌─ Feature branch
   ├─ 1 commit
   ├─ 1 test
   └─ PR simples
```

### Feature Grande (Nova tela, novo endpoint)

```
┌─ Feature branch
   ├─ Múltiplos commits organizados
   ├─ Testes completos
   ├─ Documentação atualizada
   ├─ Changelog atualizado
   └─ PR com descrição detalhada
```

---

## 📖 Documentação

Ao adicionar features, atualize:

1. **README.md** - Se muda o comportamento principal
2. **docs/API.md** - Se adiciona endpoints
3. **docs/ARCHITECTURE.md** - Se muda fluxos
4. **CHANGELOG.md** - Nova entrada em "Upcoming" ou versão atual
5. **Comentários no código** - Documentar funções públicas

---

## 🧪 Testes

### Rust

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cpu_metrics_collection() {
        let metrics = CpuMetrics {
            usage_percent: 42.5,
            core_count: 4,
            cores: vec![...],
            frequency_mhz: 3400,
        };

        assert!(metrics.usage_percent > 0.0);
        assert_eq!(metrics.core_count, 4);
    }
}
```

### Kotlin

```kotlin
class DashboardViewModelTest {
    
    @Test
    fun testTelemetryFetching() {
        val viewModel = DashboardViewModel(mockRepository)
        viewModel.fetchTelemetry("url", "token")
        
        // Assert estado mudou para Success
        // Assert dados carregados corretamente
    }
}
```

---

## 🚀 Release Process

Features são mergidas em `main` quando prontas. Releases seguem [Semantic Versioning](https://semver.org/):

- **MAJOR** (0.1.0 → 1.0.0): Breaking changes
- **MINOR** (0.1.0 → 0.2.0): Nova feature
- **PATCH** (0.1.0 → 0.1.1): Bug fix

---

## 📞 Comunicação

- **Issues**: Para bugs e feature requests
- **Discussions**: Para dúvidas e debates
- **PRs**: Para código

---

## 📜 Licença

Ao contribuir, você concorda que suas contribuições serão licenciadas sob a licença MIT.

---

## 🙏 Obrigado!

Toda contribuição, não importa o tamanho, ajuda a melhorar o Pocket NOC!

---

**Mantido por**: Pocket NOC Team  
**Última atualização**: 11 de março de 2026
