[English](triage-playbook.md) | [Português](triage-playbook.pt_BR.md)

# Portão KOF-first — triagem de issues/PRs NOT-VALID, contrárias ao contrato e fora de escopo

**Repositório:** `KofLang/Kof4j`
**Branch de referência:** `lab` (`D-BRANCH-PIPELINE`: `lab` → `testing` → `prerelease` → `stable` → `release/x.y.z`; `beta-*` está congelada e não existe mais no remoto)
**Objetivo:** revisar issues e pull requests que tratam sintaxe ou semântica estrangeira como bug, contradizem contrato KOF já documentado ou tentam introduzir extensão de linguagem sob o rótulo de correção.

> Este documento é um **playbook de análise e ação**. Ele não autoriza fechamento automático por nenhuma lista abaixo. Antes de comentar, fechar issue ou fechar PR, o agente deve reler o estado atual da branch, a issue, a PR, os comentários mais recentes e o contrato aplicável.

---

## 1. Regra principal

Aplicar o fluxo do `D-KOF-FIRST` (regra 10 do `AGENTS.md`):

```text
reproducer
    ↓
KOF VALIDITY
    ↓
CONTRACT SOURCE
    ↓
KOF IDIOM SEARCH
    ↓
MEASUREMENT
    ↓
CLASSIFICATION
    ↓
┌─────────────────────────────┐
│ BUG REAL / TARGET DIVERGENCE│ → pode haver correção
└─────────────────────────────┘

NOT-VALID
    ↓
explicar a forma KOF existente
    ↓
fechar a issue / fechar a PR sem merge

DESIGN REQUEST / GAP / CONTRACT AMBIGUITY
    ↓
NÃO implementar como bugfix
    ↓
orientar discussão / decisão de linguagem
```

**Princípio:** comportamento de Java, Kotlin, C#, JavaScript, JVM ou de qualquer outra plataforma **não é oráculo da linguagem KOF**. O oráculo é o contrato interno do KOF.

Ordem de evidência:

1. `docs/development/DECISIONS.md`;
2. documentação normativa em `docs/language-reference/`;
3. testes de conformidade/golden;
4. `training/`, `learn/` e `training/anti-patterns/fake-idioms.md`;
5. matriz de paridade;
6. implementação atual;
7. somente depois, referências externas.

---

## 2. O que deve ser considerado NOT-VALID

Classificar como **NOT-VALID** quando:

- o reproducer usa sintaxe que **não pertence ao KOF**;
- o KOF já possui forma documentada de expressar a mesma intenção;
- a rejeição do compilador é coerente com o contrato atual;
- a issue chama a rejeição de "bug" apenas porque a construção funciona em Java/Kotlin/C#/etc.;
- a PR "corrige" o parser aceitando uma segunda sintaxe não autorizada.

Nesses casos, **não alterar o compilador** e **não melhorar o diagnóstico como pretexto para manter a issue aberta**, salvo se decisão ou documentação específica exigir esse diagnóstico.

A ação normal é:

```text
1. provar a sintaxe KOF correta;
2. citar o contrato;
3. mostrar o exemplo correto;
4. explicar que a necessidade já é atendida;
5. fechar a PR sem merge, se houver;
6. fechar a issue como not planned / not a bug sob o contrato atual;
7. se a outra forma tiver mérito, indicar o caminho de design separado.
```

---

## 3. O que deve ser considerado CONTRACT CONFLICT

Classificar como **CONTRACT CONFLICT** quando a PR ou a issue pede comportamento explicitamente proibido ou já decidido em sentido contrário.

Exemplos:

- permitir `T? = null` quando o contrato vigente determina `SEM048` para literal `null` atribuído diretamente;
- implementar comportamento já rejeitado por decisão de linguagem;
- alterar semântica congelada sem decisão da mantenedora;
- reabrir, por parser ou backend, superfície que o contrato diz não existir.

A ação é mais forte que num NOT-VALID simples:

```text
não mergear
    ↓
citar a decisão normativa que seria violada
    ↓
mostrar o comportamento KOF atual
    ↓
explicar que mudar isso exige decisão SUPERSEDING / novo registro
    ↓
fechar o bugfix atual
```

Nunca escrever que a proposta é "ruim" ou "proibida para sempre". A redação correta é:

> A alteração pode ser discutida, mas não pode entrar como correção de bug enquanto o contrato vigente disser o contrário.

---

## 4. DESIGN REQUEST / GAP legítimo fora do escopo de bugfix

Algumas issues podem revelar necessidade real e ainda assim **não serem bugs**.

Exemplos:

- nova sintaxe alternativa;
- nova inferência contextual;
- novo açúcar de fluxo;
- novo pattern de matching;
- nova API de coleção;
- nova forma de field;
- ampliar conversão SAM;
- nova regra de nullability.

Nesses casos:

1. não fechar a necessidade como "sem valor";
2. não mergear a PR de produção;
3. fechar ou reclassificar a issue de bug, quando o bug não existe;
4. orientar uma **discussão de design** separada;
5. exigir impacto em gramática, semântica, documentação, testes e todos os backends relevantes;
6. aguardar a decisão da mantenedora antes de código de produção.

### Forma correta da solicitação

Título sugerido:

```text
[Design] Should KOF support <nova forma/comportamento> in addition to <forma atual>?
```

Corpo mínimo: necessidade · contrato KOF atual · idioma KOF atual · mudança proposta · por que o idioma atual pode ser insuficiente · impacto em gramática e semântica · impacto entre alvos · compatibilidade e migração · alternativas · a decisão objetiva pedida à mantenedora.

**Não implementar antes da decisão.** Não criar a issue substituta automaticamente — forneça o texto e deixe a mantenedora ou o contribuidor decidir se a abre.

---

## 5. Ordem operacional para o agente

Executar **um par issue→PR por vez**.

```text
A. ler a issue completa + todos os comentários
B. ler a PR completa + patch + reviews + checks
C. conferir o tip atual da branch
D. localizar o contrato normativo
E. validar a sintaxe do reproducer
F. procurar o idioma KOF existente
G. medir quando necessário
H. classificar
I. escrever comentário individual e fundamentado
J. se NOT-VALID / CONTRACT CONFLICT:
     - fechar a PR sem merge
     - fechar a issue quando apropriado
K. se DESIGN REQUEST:
     - não mergear produção
     - orientar discussão separada
L. só então seguir para o próximo par
```

**Nunca fazer fechamento em massa com mensagem genérica.** Cada item recebe a sua própria prova — inclusive a sua própria medição no tip atual.

---

## 6. Critério para fechar PR sem merge

Fechar a PR quando qualquer um for verdadeiro:

- implementa sintaxe que não existe no contrato;
- adiciona alias ou açúcar sem decisão;
- contradiz decisão vigente;
- trata comportamento JVM/Java como requisito KOF;
- "corrige" o reproducer mudando a linguagem em vez de usar a sintaxe KOF correta;
- depende de issue cuja classificação correta é NOT-VALID;
- muda semântica de inferência de tipos, narrowing ou patterns sem decisão;
- não possui prova RED→GREEN exigida pelo Portão de Qualidade.

Antes de fechar, verificar se a PR contém algum **fix separável e legítimo** — por exemplo, diagnóstico emitido na posição `0:0` pode ser defeito real mesmo quando o pedido de nova API não é. Havendo, pedir ou produzir PR isolada apenas para o defeito legítimo.

---

## 7. Portão de qualidade para toda PR

Mesmo PR semanticamente correta não deve ser mergeada sem prova.

```text
reproducer KOF válido
      ↓
RED no código anterior
      ↓
correção da causa raiz
      ↓
teste de regressão na mesma PR/commit
      ↓
GREEN
      ↓
paridade relevante
      ↓
suíte / gates do repositório
```

**Teste não substitui decisão de linguagem.** PR de sintaxe nova com testes continua incorreta se não houver decisão que autorize a sintaxe nova.

---

## 8. Templates de comentário

Os templates de fechamento e de design request ficam em [`templates.pt_BR.md`](templates.pt_BR.md). Adapte cada um individualmente; nunca copie sem substituir as evidências.

---

## 9. Precedentes executados (worklog — não é fila)

Estes casos estão encerrados. Ficam registrados como **precedentes de raciocínio**; não são fila aberta. Revalide qualquer item novo contra a branch atual antes de agir.

### 9.1 Parâmetros de lambda tipados — `#492` / PR `#496`, `#509` / PR `#520`

```text
reproducer usa (Int x) -> ...
        ↓
o contrato documenta (x: Int) ->
        ↓
a intenção "lambda tipada" JÁ é suportada
        ↓
portanto não é bug
        ↓
PR que aceite `Type name` adicionaria uma segunda sintaxe
        ↓
fechar sem merge
        ↓
havendo interesse, abrir a discussão:
"Should lambda parameters accept both name: Type and Type name?"
```

A PR #520 afirmava que a #492 teria "corrigido parâmetros tipados de lambda em argumentos de chamada" — não corrigiu: a #492 foi fechada como não-bug e a #496 encerrada sem merge. Premissa falsa na cadeia merece correção explícita.

### 9.2 Bitwise NOT `~` — `#506` / PR `#516`

`docs/language-reference/grammar.md` §5.3 lista `~` **nominalmente** em "Operators that do NOT exist (SG-002, verified by probe)", e a produção unária não o inclui. O `LEX005` é, portanto, a rejeição esperada de sintaxe não suportada. A motivação da issue ("present in Java, C, and most languages targeting the JVM") é exatamente a inferência que o `D-NOT-JAVA` exclui. A alternativa KOF existente é o operador XOR: `x ^ -1` (medido `-6` para `x = 5`).

### 9.3 O lote de 19/09 — treze PRs fechadas sem merge

Todas medidas no tip do dia, cada uma com a sua citação de contrato:

| Classe | Item | Contrato / medição |
|---|---|---|
| NOT-VALID | `#536`/PR `#540` — `for (a in items)` sem `var` | `for-in = ( "var" \| "val" ) , identifier , "in" , expression ;` (`grammar.md` §6) + `fake-idioms.md` (❌ Unavailable); medido `1 2 3` com `var`, `SEM011` sem |
| NOT-VALID | `#537`/PR `#541` — multi-label `case A, B` | `case-expr` é um pattern-ou-expressão; medido `PARSE076`+`PARSE041`+`PARSE078` vs `vowel` ✓ com cases separados |
| NOT-VALID | `#535`/PR `#539` — enum `.name`/`.ordinal` como propriedades | o contrato é a forma de **método** (`classes.md`); medido `SEM030` na propriedade, `Red`/`0` no método |
| NOT-VALID | `#527`/PR `#531` — field `count: Int` | field é `Type name` (`classes.md` §1); medido `0` ✓ em `Int count = 0`, `PARSE018`+`PARSE016` na forma dois-pontos |
| NOT-VALID | `#510`/PR `#522` — `static count: Int` | mesma superfície do `#527`; uma discussão de sintaxe de field, não uma por modificador |
| NOT-VALID | `#528`/PR `#532` — `var count` no corpo da classe | `classes.md` §1 (`val x = 1` → `PARSE016`); medido `PARSE016`+`PARSE018` |
| NOT-VALID | `#508`/PR `#519` — "`class Box<T>` rejeitado" | **causa raiz falsa**: `class Box<T>` mede `7`; o `PARSE016` vem do field `val: Int = 42`, e o patch adicionou ramo `VAL` em vez de corrigir generics |
| CONTRACT CONFLICT | `#507`/PR `#517` — `String? s = null` | `SEM048` / SG-008 + `D-NULL-INTENT` (decisão da mantenedora, 09/09); o patch mexia no `StatementAnalyzer`, componente que implementa a regra; medido `SEM048` no literal e `0` ✓ via `map.get` |
| DESIGN REQUEST | `#513`/`#529`/`#530` + PRs `#525`/`#533`/`#534` | os HOF de coleção **já** inferem (`map` mede `3`, `reduce` mede `6`); `var f: (Int) -> Int = (x) -> …` dá `SEM001` — uma thread consolidada, cross-referenciando `#501` e a família SAM (`#298`/`#310`) |
| DESIGN REQUEST | `#514`/PR `#526` — narrowing após guard que termina | o narrowing documentado é a forma de then-branch (`type-system.md` §5); mantida aberta como item de rastreio da necessidade |
| DESIGN REQUEST | `#538`/PR `#542` — pattern de tipo primitivo | `SEM035` é rejeição **deliberada** (07/09), não acidente; a discussão precisa definir semântica em todos os alvos |

Duas lições deste lote, válidas para o próximo:

1. **Isolar o reproducer (Gate 0).** O `#508` relatava bug de generics cuja causa real era um field inválido sem relação. Quando o reproducer mistura construtos, teste cada um separadamente antes de aceitar a causa relatada.
2. **O rótulo `fix:` não é classificação.** Vários patches eram tecnicamente plausíveis e ainda assim não podiam entrar: o que faltava era decisão autorizando a superfície nova, não qualidade de implementação.

---

## 10. Resultado esperado

Ao final de uma varredura de triagem:

- nenhum comportamento estrangeiro deve ter virado KOF por acidente;
- nenhum contrato vigente deve ter sido modificado por um `fix:`;
- cada issue NOT-VALID deve mostrar **a forma KOF correta**;
- cada necessidade legítima fora do contrato deve ter **um caminho claro de design**;
- PRs incompatíveis devem estar fechadas sem merge;
- bugs reais e separáveis devem permanecer rastreáveis nas suas próprias issues/PRs;
- o backlog deve refletir problemas do **KOF**, e não diferenças entre o KOF e outras linguagens.

---

## Regra final

> **Primeiro provar o que o KOF é. Depois perguntar se o KOF deve mudar. Nunca inverter essa ordem.**
