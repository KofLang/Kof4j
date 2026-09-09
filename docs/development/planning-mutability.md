# Planning — Validadores de mutabilidade: `val` e record (DD-02, GitHub #42)

> **Status:** `PROPOSED` (aguarda decisão da mantenedora) · **Issues:** #42 ·
> **Bump:** 0.3.1 → 0.3.2-beta · **Gate:** suíte completa + corpus sincronizado ·
> **Criado:** 09/09/2026 (análise da sessão da lane migração/issues)

## O conflito (regra 6: isto é decisão de design, não edição)

O corpus **promete** mutabilidade controlada:

- `learn/04-variables-and-types.md:22` — `// PI = 2.0  // ERRO: não pode reatribuir`
- `learn/07-classes-and-objects.md:52` — `// u.name = "Ana"  // ERRO de runtime: record é imutável`
- AGENTS.md — `record` = "dados **imutáveis**"; `class X(...)` = record (imutável)

O compilador **não aplica** nada disso (bug #42):

| programa | corpus diz | JVM hoje | KofJS hoje | interp hoje |
|---|---|---|---|---|
| `val x = 1; x = 2` | ERRO | `2` (muta) | `2` (muta) | `2` (muta) |
| `record P(Int x); p.x = 9` | ERRO | `IllegalAccessError` | `TypeError` | `9` (**muta!**) |

Três defeitos separados, uma raiz: a informação de mutabilidade é **destruída
no parser**. `StatementParser.parseVarDecl` (kof-compiler/.../parser/
StatementParser.java:354-357) aceita `VAL` mas grava `type="var"` — o AST não
diferencia `val`/`var`. O `SymbolTable.LocalVariableSymbol` (register
`name, type, index`) não tem flag de mutabilidade, então o
`StatementAnalyzer.analyzeAssignmentStatement` nem *poderia* checar hoje.

**Por que exige decisão:** hoje `val x = 1; x = 2` **compila**. Adicionar o
guard quebra código que roda — retrocompatibilidade é lei (regra 2), exceto por
bump deliberado. Este DD propõe o **caminho aditivo de dois estágios**.

## O design — "funciona em 100% dos casos" = 100% do que é DECIDÍVEL

A primeira proposta ingênua ("um guard SEM cobre tudo") **não fecha o problema
em 100%**. O problema tem uma fronteira formal: reatribuição em loop de
control-flow arbitrário é **indecidível** em geral (halting — a atribuição pode
nunca executar). Todo design honesto precisa de uma aproximação estática. O
padrão consolidado na indústria (Java `final`/effectively-final, Kotlin
comp-time, Rust `mut` no binding, JS `const` no parser) resolve assim:

> **Regra decidível:** a atribuição é ilegal **se o sintaxe do programa aponta
> para o binding imutável** (nome resolvido lexicalmente a um `val`/parâmetro
> largo/componente de record). Não importa se o ramo "nunca roda": código que
> *pode* reatribuir um imutável não é código válido — é o contrato de
> `val` ("valor constante", learn/04).

Sob essa regra, 100% dos programas Kof são classificados corretamente (não há
falso negativo: toda atribuição tem um alvo sintático que resolve a um symbol;
e não há falso positivo: atribuir a `val` é sempre inválido pelo corpus). A
aproximação é EXATA para a linguagem — o "100%" pedido não tem custo.

### Escopo do que é imutável (tabela normativa)

| binding | mutável? | reatribuir o nome | escrever componente |
|---|---|---|---|
| `var x` / tipo explícito (`Int x`) | sim | ok | ok (se campo de class) |
| `val x` | **não** | **SEM037** | n/a (referência aponta p/ objeto; estado interno do objeto segue as regras dele) |
| parâmetro de função/método | **decisão DD-02a** | hoje muta ok | n/a |
| `record R(...)` / `class R(campos)` | componentes **não** | n/a | **SEM038** |
| campo de `class` (com `constructor`) | sim | ok | ok |
| receiver `this` | não | **SEM039** (bônus) | ok |

Decisão pendente **DD-02a** (parâmetros): o corpus não fala. Java permite
reatribuir parâmetro; JS também; Kotlin proíbe. Recomendação: **permitir**
(paridade com o que os 4 backends já fazem hoje, zero código quebrado;
imutabilidade de parâmetro é estilo, não semântica — pode virar lint depois).

### Implementação (uma fase só, três guardas no analyzer — não no parser)

O local correto é **compilação** (estático), não runtime: o erro aparece em
`kof check`, nos 4 alvos com uma única implementação (single source of truth
no analyzer — regra 3 de plataforma; nada de checar no lowering de cada
backend, o que criaria divergência de novo).

1. **Carregar a informação (aditivo ao AST, retrocompatível):**
   `VarDeclStmt` ganha `boolean mutable` (parser: `ctx.check(TokenType.VAL)` →
   `mutable=false`; demais → `true`). Record é Java: o construtor canônico com
   `mutable=true` preserva todos os ~40 call-sites existentes (inclusive
   desugars — que são código gerado e sempre mutável). Alternativa recusada:
   token `type="val"` (frágil, stringly-typed — regra "intenção, não
   mecanismo" vale p/ nós internos também).

2. **Persistir no symbol:** `LocalVariableSymbol(name, type, index, mutable)`
   e `FieldSymbol(..., immutable)` — p/ record/componentes e `this` o analyzer
   marca `immutable=true` na definição.

3. **Os três guardas em `analyzeAssignmentStatement`**
   (kof-compiler/.../StatementAnalyzer.java:24 — o checkpoint ÚNICO de toda
   atribuição-statement dos 4 caminhos: JVM/JS/Native/interpretador passam
   todos pelo `CompilerPipeline.java:301-304`, que aborta antes do lowering):
   - alvo `IdentifierExpr` → symbol com `mutable=false` → **SEM037**
     `"cannot assign to 'x': declared val"`;
   - alvo `MemberExpr` cujo receiver resolve a record/componente — reusar o
     predicado EXISTENTE `CompilerTypes.isRecordType(Type, unit, analyzer)`
     (kof-compiler/.../CompilerTypes.java:188; já distingue record canônico de
     `class X(...)`-record) → **SEM038** `"cannot assign to 'p.x': record is
     immutable"`;
   - alvo `this` (nome `this` em contexto que não construtor) → **SEM039**.
   - `+=`/`-=`/etc. passam pelo MESMO caminho (AssignmentExpr com operator
     composto — StatementParser reusa o nó) → cobertos sem código extra.

   Códigos **SEM037/038/039** já reservados nesta análise: os maiores usados
   hoje são SEM033-SEM036 (livres a partir de 037).

### Porque fecha o caso do interpretador e o cross-target (regras 4 e 5)

- **Interpretador**: roda `analyze()` antes (mesmo pipeline) → a divergência
  "`interp muta em silêncio`" morre na origem: o programa nem chega a
  interpretar (erro SEM, não runtime).
- **JVM/JS hoje lançam erro de runtime** (`IllegalAccessError`/`TypeError`):
  com o guard, esses programas passam a falhar em compile — o runtime-err
  deixa de ser alcançável por código novo; não há mudança de output de código
  que roda hoje (só código inválido pelo corpus passa a não compilar).
- **Paridade**: um único guard no frontend → os 4 backends herdam
  byte-identical (mesmo diagnóstico).

### Migração em dois estágios (retrocompatibilidade, regra 2)

- **0.3.2-beta (este DD):** SEM037/038/039 como **WARNING** (novo código:
  `kof check` avisa; `--strict` promove p/ erro). Código existente NÃO quebra
  (só ganha aviso) — a linguagem fica honesta sem trair a promessa "roda hoje,
  roda amanhã". `training/` + `learn/` atualizados no mesmo release
  (regra corpus).
- **0.4.0:** warn → **error** (bump maior = quebra deliberada documentada,
  migration note com regex p/ `sed` nos casos que realmente precisam de `var`).

### Casos de teste DoD (100% das classes de decisão)

1. `val x=1; x=2` → SEM037; `var`/tipo explícito → ok (não regredir).
2. `val x=1; x+=2` → SEM037 (composto passa pelo mesmo nó).
3. `record P(Int x); p.x=9` → SEM038 nos 4 caminhos (pipeline único; teste
   interpreta o mesmo source e exige a MESMA falha SEM antes de run).
4. `class X(...)`-record → SEM038 (parser já classifica como record — reusar).
5. `val l = listOf(1); l.add(2)` → **ok** (imutável é o binding, não o objeto
   — learn/17:64 é literal sobre isso; o teste trava essa fronteira).
6. `this` em método de record (`bump(){ this.x=9 }`) → SEM038/039.
7. Parâmetro reatribuído → ok (DD-02a) + lint futuro opcional.
8. Sombra lexical: `val x=1; { var x=2; x=3 }` → ok (resolve p/ symbol do
   escopo interno — `scope.resolve` já faz isso; teste prova que o guard não é
   por nome, é por symbol).
9. Campos de class mutável direta `u.age=27` → ok (não confundir com #4).
10. Golden cross-target: programa inválido → os 4 backends emitindo a mesma
    lista de diagnósticos SEM (paridade).

## O que este design NÃO resolve (honestidade R6)

- Mutabilidade profunda (`val` de container): fora — o corpus define o binding,
  não o objeto (learn/17).
- Fluxo de dados (definite assignment de `val` sem inicializador): `val x; x=1;`
  hoje cria symbol com init null; o guard SEM037 proíbe o segundo passo,
  deixando `val x;` vazio — aceitável (mesmo resultado Kotlin); "val deve ter
  inicializador" é lint (SEM0xx reservado, fase 2).
- `let`/`const` do KofScript (aliases) → herdam: `const` → `val`, `let` → `var`
  (parser do .ks só precisa mapear p/ o mesmo booleano).

## Arquivos tocados (quando aprovado)

`parser/StatementParser.java` (flag `VAL`), `VarDeclStmt.java`,
`SymbolTable.java` (flags), `StatementAnalyzer.java` (3 guardas ~40 linhas),
reservas em `docs/diagnostics*`, `training/idioms/classes|records.md`
+ `learn/04|07` (nota do dois-estágios), `KofSemanticTest`/E2E cross-target.
