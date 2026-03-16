# 🤝 Contribuindo com o Pocket NOC Ultra

Seja bem-vindo ao guia de colaboração do **Pocket NOC Ultra**. Fico feliz em ver seu interesse em elevar o nível desta ferramenta. Para manter a excelência técnica e o rigor de engenharia do projeto, siga as diretrizes abaixo.

---

## 🏛️ Filosofia de Desenvolvimento

- **Performance First**: O agente em Rust deve ser mantido com o mínimo de overhead possível.
- **Segurança Nativa**: Zero Trust e HackerSec Core são inegociáveis.
- **Código Limpo**: Siga os princípios SOLID e as convenções da linguagem.

---

## 🚀 Fluxo de Trabalho

### 1. Preparação

Antes de codar, garanta que seu ambiente está configurado:

- **Agente**: Rust 1.70+
- **Controller**: Android Studio + SDK 34

### 2. Padrões de Commit

Utilizamos **Conventional Commits** para manter o histórico semântico:

- `feat`: Nova funcionalidade.
- `fix`: Correção de bug.
- `perf`: Melhoria de performance.
- `docs`: Alterações na documentação.
- `security`: Melhorias ou correções de segurança.

### 3. Processo de Pull Request (PR)

1. Faça o **Fork** do repositório.
2. Crie uma branch descritiva: `git checkout -b feat/monitoramento-disco`.
3. Adicione testes para sua mudança.
4. Rode as verificações locais:
   - **Rust**: `cargo fmt`, `cargo clippy`, `cargo test`.
   - **Kotlin**: `./gradlew test`, `./gradlew lint`.
5. Abra o PR com uma descrição técnica detalhada do impacto da alteração.

---

## 📋 Checklist de Qualidade

Antes de submeter, verifique:

- [ ] O código segue as convenções de estilo (Rust fmt / Kotlin coding conventions).
- [ ] Não há `unwrap()` ou `lateinit` perigosos sem tratamento.
- [ ] Documentação em `/docs` foi atualizada se necessário.
- [ ] O footprint de memória do Agente não foi afetado negativamente.

---

## 🐛 Relatando Problemas

Encontrou um bug? Abra uma **Issue** detalhada incluindo:

- Passos para reprodução.
- Contexto do ambiente (Versão do Kernel, Android API).
- Logs (formatados como blocos de código).

---

Agradeço por ajudar a tornar o Pocket NOC uma ferramenta cada vez mais robusta.

**Munique Alves Pacheco Feitoza**  
*Engenharia de Software & Alta Performance*
