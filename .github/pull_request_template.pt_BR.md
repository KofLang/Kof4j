[English](pull_request_template.md) | [Português](pull_request_template.pt_BR.md)

<!--
  Esteira de branches (autoritativa: `AGENTS.md` §Autoridade/D-BRANCH-PIPELINE e
  `docs/development/DECISIONS.pt_BR.md` §D-QUALITY-PIPELINE-2609):
  lab → testing → prerelease → stable → release/x.y.z → tag.
  Desenvolvimento e correções entram SEMPRE por `lab`; os demais estágios são
  protegidos e a guarda do repositório fecha qualquer PR mirando `main`.
-->

## 🎯 Branch Base Alvo
- [ ] Confirmo que este PR aponta para o estágio ativo **`lab`** e **NÃO** para um estágio protegido (`main`/`testing`/`prerelease`/`stable`).

---

## 📝 Descrição da Mudança
<!-- Descreva de forma clara e concisa o que foi adicionado, corrigido ou refatorado. -->

---

## 🔗 Issue Relacionada
<!-- Todo PR deve referenciar uma issue aberta (ex: Fixes #123, Closes #456). Regra 6 do AGENTS.md. -->
Fixes #

---

## 📐 Contrato KOF-primeiro (`D-KOF-FIRST`)
<!-- Toda mudança responde aos quatro portões abaixo. "A linguagem X faz assim" nunca é fonte de contrato. -->
**Reproducer Kof válido** (o trecho que exercita a mudança):

```kof

```

- **Fonte do contrato** (entrada do DECISIONS.md, doc normativo, teste de conformidade/golden, ou matriz de paridade) que define o comportamento esperado:
- **RED antes da mudança de produção** — alvo(s), esperado pelo contrato Kof, obtido:
- **Causa raiz** (não o sintoma):
- **Classificação** (`Bug real` / `Divergência de alvo` / `Gap real` / `Design request` / `Not-valid` / `Ambiguidade de contrato`):
- **Isto altera a superfície do Kof?** Se sim, a decisão da mantenedora que autoriza (regra 6) — uma forma nova aceita pela gramática é feature de linguagem, não conserto de parser:
- **Referências externas usadas** (só de implementação/teoria; nenhuma delas define a superfície do Kof):

---

## 🧪 Como Foi Testado (Portão de Qualidade)
<!-- O teste PROVA que o código funciona. Liste os comandos rodados e os testes adicionados. -->
- [ ] `mvn -o -pl kof-compiler -am compile -q` rodou sem erros
- [ ] Testes adicionados/alterados cobrindo o caminho feliz e bordas (Q3)
- [ ] Suíte rodada e verde (`mvn test ...`)

---

## 📋 Checklist de Pré-Submissão
- [ ] Nenhuma alteração contém stubs ou TODOs de fachada (Q7)
- [ ] Regras do `AGENTS.md` respeitadas (≤500 linhas/classe, zero regressão)
- [ ] Documentação ou `DOING.md` atualizados se aplicável
