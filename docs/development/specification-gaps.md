# Specification Gaps e Divergências

**Versão:** 0.3.0-beta · **Data:** 06/09/2026 · **Fonte:** auditoria completa do
`kof-compiler` + probes de execução + revisão de `docs/`, `training/`, `AGENTS.md`

Este é o relatório de inconsistências encontradas na auditoria. Cada item
distingue **o que o código faz**, **o que a documentação diz** e **o que os
testes provam**. **Nenhum item aqui foi "corrigido" na linguagem** — são
recomendações futuras (regra 14 da tarefa: não alterar comportamento).

---

## Categoria A — Documentação contradiz código

### SG-001 — `fun`/`fn`/`func` existiam no compilador mas o corpus diz que não ✅ RESOLVIDO (06/09)

- **Implementação (antes)**: `fn` era prefixo opcional aceito. `fun`/`func`
  compilavam porque o parser lia a palavra como *tipo de retorno* e o nome da
  função vinha depois (`fun main()` → função `main`, retorno implícito `void`).
  `JsonE2ETest.java:223` usava `fun main()` e passava.
- **Documentação**: `AGENTS.md` ("não existe `fun` nem `func`"),
  `training/anti-patterns/fake-idioms.md` (lista `fun`/`func`/`let` como fake).
- **Problema (antes)**: a regra "não existe" era falsa para o compilador real —
  um agente que escrevesse `fun` não recebia erro.
- **Resolução (06/09, 2º passo)**: `fun`/`fn`/`func` viraram **palavras
  reservadas** no lexer (tokens `FUN`/`FN`/`FUNC`, mesmo mecanismo de
  `sealed`/`permits`) — **não existem** no Kof em nenhuma posição: nem como
  keyword de declaração, nem como nome de função, variável, parâmetro ou
  campo. Em posição de declaração o parser dá `PARSE085` (diagnóstico claro —
  R6); em outra posição, o `expectId` de cada parser já falha (`PARSE037`
  variável, `PARSE023` parâmetro, …). Alinhado ao corpus (regra 4). KofScript
  (`.ks`) mantém `fn` como sintaxe própria e traduz na fronteira
  KofScript (`.ks`) **não** é exceção: é Kof puro (sem `fn`/`let`/`async`).
  Testes: `FunctionSyntaxTest` (12: fun/fn/func
  rejeitados como prefixo, `fn calc(): Int` rejeitado, `fn()`/`var fun`/
  `param fn` rejeitados, membro de classe, `Int calc():Int` idiomático).
  `let`/`const`/`async` são inexistentes em `.kf` **e** `.ks` (KofScript não
  é JavaScript — sugar removido 06/09).

### SG-002 — Tokens e keywords que a gramática não usa

- **Implementação**: o lexer produz `TILDE` (`~`), `COLON_COLON` (`::`),
  `ELLIPSIS` (`...`), `DOUBLE_ARROW` (`=>`), `PIPE_LINE` (`|>`),
  `UNDERSCORE` (`_` isolado) e as keywords `sealed`/`permits`, mas **nenhum**
  aparece em produção do parser (grep: 0 usos em `Parser.java`).
- **Consequência observável**: `~5`→`PARSE041`, `a => b`→`PARSE041`,
  `sealed class X{}`→`PARSE007` (todos *probe*).
- **Problema**: tokens mortos dão a impressão de feature planejada que não
  existe. `sealed`/`permits` sugerem hierarquia selada (Java 17) que **não é
  implementada**.
- **Recomendação**: remover os tokens do lexer **ou** implementar as features
  **ou** documentar explicitamente como "reservado, não implementado". Hoje é
  **Unspecified**.

### SG-003 — Termos de marketing vs definição técnica

- **APLICADO (09/09, decisão do maintainer — "review and apply" com as
  checagens novas):** com SEM041–SEM046 aplicados, as garantias de compilação
  cobrem instancição de abstract, tipo aninhado, cobertura de interface,
  assinatura de main, throw-clause e visibilidade — o que a README/overview
  podem afirmar como propriedades concretas. `docs/language-reference/
  type-system.md` §1 atualizado com a nota de 09/09 e §13 com os 6 códigos
  novos na tabela SEM0xx. "Fortemente tipada" continua FORA do vocabulário
  oficial (vago por definição) — o que vale é a lista de checagens, agora
  completa e testada.
- **Documentação (histórico)**: `README.md:60` "Kof é uma linguagem
  **fortemente tipada e estaticamente tipada**"; `docs/architecture.md`
  "fortemente tipada".
- **Implementação (histórico)**: o type checker **não** garante subtipagem
  (§SG-009), **não** checa elemento de coleção, **não** impõe
  `private`/`abstract` em compile-time, **não** impede reatribuição de `val`.
- **Problema**: "strongly typed" é vago e, lido como "o compilador impede
  operações mal tipadas", é **falso** para Kof hoje.
- **Recomendação**: substituir por propriedades concretas (já feitas em
  [language-reference/type-system.md](language-reference/type-system.md)).
  Manter "estaticamente tipada" (verdadeiro: tipos resolvidos em compile-time).

---

## Categoria B — Comportamento não especificado (Unspecified)

### SG-004 — (resolvido na auditoria) `bool→numérico`

- **Implementação**: `bool` é armazenado como `int` 1/0; `var i: Int = true`
  → `1` (*probe*). `isAssignable` aceita por `primitiveWidth(bool)=0`.
- **Problema**: a coerção funciona por **acidente de representação**, não por
  regra. Não há teste dedicado.
- **Recomendação**: decidir se é regra da linguagem (documentar + testar) ou
  deve ser rejeitada (SEM002 já pega aritmética, mas não atribuição).

### SG-005 — Deref de `T?` sem narrowing não é erro

- **Implementação**: `var s: String? = "x"; s.length` **compila e roda**
  (*probe* → 1). O lowering desembrulha o receiver (`ExpressionTyper.java:143`).
- **Problema**: null-safety é **advisory**: o compilador não impede NPE. Se
  `s` fosse `null`, NPE em runtime.
- **Recomendação**: ou documentar que null-safety é parcial (só `if (x!=null)`
  estreita, deref direto é permitido), ou tornar deref de `T?` sem narrowing um
  erro (breaking change).

### SG-006 — Short-circuit de `&&`/`||` desligado no JS — ✅ CORRIGIDO 09/09 (paridade OK + teste)

- **Implementação**: `ExpressionBinaryLowerer.java:56-57` — o short-circuit por
  labels é emitido só quando `target != JS`. No JS, ambos os lados são
  avaliados.
- **Problema**: `if (x != null && x.length > 0)` pode NPE no JS mas não no
  JVM/Native. **Divergência de paridade** (regra 5 de congelamento).
- **Recomendação**: documentar como Target-specific (feito em
  [expressions.md](language-reference/expressions.md) §5) **e** abrir gap de
  paridade para corrigir o JS.
- **NOTA 09/09 (análise de código):** o lowering por labels é `target != JS`,
  mas para `&&`/`||` de bool o JS emite os operadores nativos (`a && b`,
  `a || b` — `JsCallEmitter.binaryExpr` linhas 274-277), que **já fazem
  short-circuit** nativamente. Logo `x != null && x.length > 0` NÃO deve NPE no
  JS (o `x.length > 0` não é avaliado se `x != null` é false). Paridade plausível
  por leitura de código, mas **sem teste de runtime que trave** — recomenda-se um
  caso em `BackendParityTest` (`if (x != null && x.length > 0)`) nos 4 targets
  antes de fechar o gap.
- **✅ CORRIGIDO 09/09:** `BackendParityTest.parityShortCircuitAndOr` adicionado
  (`String? s = null` → `vazio` via short-circuit; `String? t = "abc"` →
  `nao-vazio`). Suíte verde (a única falha da suíte 1163+25+5+109 é o bug 46,
  pré-existente) → o short-circuit de `&&` no JS/JVM está travado por teste.

### SG-007 — Wildcard de genérico (`? extends T`) compila mas quebra — ✅ CORRIGIDO 06/09 (PARSE086)

- **Implementação (antes)**: `List<? extends Int>` é parseado (o `?` vira sufixo
  nullable, `extends Int` entra no nome) e **roda com
  `NoClassDefFoundError: ?extendsInt`** (*probe*).
- **Correção (06/09, lane bug-fix)**: `TypeParser.parseTypeRef` rejeita `?` wildcard dentro de `<>` com `PARSE086` ("Wildcard types '? extends/super' are not supported; use concrete type or nullable 'T?'"). `List<String?>` (nullable) continua válido. Prova: `TestRepro2` wildcard → `PARSE086`, `TestWild` `String?` → ok.
- **Problema (antes)**: sintaxe aceita sem significado — pior que erro claro (viola R6
  "nunca silencioso").

### SG-008 — Comparação de nullable de primitivo com `null`

- **Implementação**: `Int? a = null; a == null` → **NPE em runtime** (*probe*:
  o unbox do `Integer` null lança). `String? s = null; s == null` → `true`
  corretamente.
- **Problema**: inconsistência entre nullable de primitivo e de referência.
- **Recomendação**: tratar `T? == null` para `T` primitivo como comparação de
  referência (sem unbox). Bug — registrar em `known-bugs.md`.

### SG-009 — Subtipagem não é checada pelo type checker

- **Implementação**: `isAssignable` retorna `true` para **qualquer** par
  `ClassType→ClassType` (`TypeChecker.isAssignable`). A segurança vem
  do `checkcast` do lowering/runtime.
- **Problema**: `A a = <objeto de classe não-relacionada>` passa na checagem de
  tipos; falha só em runtime. `implements` sem cobrir métodos compila (SG-015).
  Abstract pode ser instanciado (SG-017).
- **Recomendação**: implementar checagem de subtipagem nominal em
  `isAssignable` (caminhando `superClass`/`interfaces` via
  `resolveInHierarchy`). É a maior lacuna de segurança de tipos. **Não
  implementado aqui** (mudança de comportamento — exige suíte + possibly bump).

### SG-010 — `val` não impede reatribuição — ✅ CORRIGIDO 09/09 (SEM037)

- **Implementação**: `val x = 1; x = 2` **compila e roda** (imprime 2, *probe*
  confirmado isoladamente). Não há flag de imutabilidade no `VarDeclStmt`
  (só `type`/`name`/`initializer` — `AstNodes.java:351`).
- **Documentação**: `AGENTS.md` "val y = 20 // imutável".
- **Problema**: `val` é decorativo. A distinção `val`/`var` não tem efeito
  observável.
- **Recomendação**: ou implementar rejeição de atribuição a `val` (SEM novo), ou
  documentar que `val` é convenção (não-garantido). Decisão de design.
- **CORRIGIDO 09/09 (DD-02, bug 62a):** `val` agora é imutável — reatribuir
  (incl. compound `+=`) emite **SEM037** ("cannot assign to immutable 'val'"). O
  parser carrega `type="val"` (antes sempre "var"); `LocalVariableSymbol` ganhou
  `isVal`; `analyzeAssignmentStatement` checa. Ver `planning-mutability.md`.

### SG-011 — Função aninhada e sobrecarga top-level

- **APLICADO (09/09, decisão do maintainer, SEM048-lane spec-gaps):** função
  aninhada funciona — parser captura `Type name(params) { ... }` em statement
  (`lookaheadNestedFunction`, checado ANTES do typed-var-decl) e o desugar faz
  hoisting para top-level `outer__inner` inserida ANTES da outer ("inner
  primeiro"); chamadas `inner(...)` reescritas para `outer__inner(...)`.
  Semântica: inner definida antes do corpo executar; outer chama e aguarda o
  retorno. Prova: `JvmE2ETest.execNestedFunction` (42) +
  `execNestedFunctionWithCondition`. Sobrecarga top-level homônima segue
  aberta (parte B do gap).
- **Implementação (histórico)**: função dentro de função não era parseada como
  declaração (SG-011); duas funções top-level homônimas colidem sem
  diagnóstico claro (o `define` sobrescreve).

### SG-012 — Inferência de tipo de parâmetro de lambda

- **APLICADO (09/09, decisão do maintainer):** inferência contextual — param
  de lambda sem anotação em `map`/`filter`/`reduce` de `List<T>` herda o tipo
  do ELEMENTO (`MemberCallTyper.contextualLambda` reescreve o param no AST;
  padrão SSE já usado p/ KofWeb). `nums.map((x) -> x * 2)` compila sem
  `(x: Int)`. Aritmética sobre param untyped SEM contexto continua SEM001
  (nunca Object silencioso). Prova: `lambdaParamInferredFromListContext` +
  regressão `untypedLambdaParamArithmeticIsDiagnosedNotEmitted`.

### SG-013 — `private`/`protected` não são checados em compile-time

- **APLICADO (09/09, decisão do maintainer, SEM046):** causa raiz era
  `defineMethodSymbol` com accessFlags=1 (PUBLIC) hardcoded — modifiers
  descartados. Agora o símbolo carrega PRIVATE/PROTECTED reais e
  `MemberCallTyper.checkMemberAccess` rejeita: private fora da classe
  declarante, protected fora da hierarquia (transitiva), ambos de contexto
  top-level. Prova: 4 testes `CompilerDriverTest` (private/protected,
  dentro/fora).

### SG-014 — Pattern matching sem guardas/aninhamento

- **APLICADO (09/09, decisão do maintainer, parte guardas):** `case T v if
  (cond):` / `case T(a,b) if (cond) ->` — PatternExpr ganha campo `guard`
  (ctors antigos preservados), parser consome `if` + expressão, SEM analisa
  a guard com a var bound, lowering emite nos 2 switch (statement: guard no
  teste com cast temporário; expressão: guard pós-binding). False → próximo
  case/braço. Prova: `switchCaseGuardFalseFallsThrough` +
  `switchCaseGuardTrueRunsGuardedArm`. Aninhamento (`case T(Inner(a,b))`)
  segue planned (parte B do gap).
- **Implementação (histórico)**: só `case Type var` e `case Type(a,b)`
  (top-level).

### SG-015 — `implements` não exige cobrir métodos abstratos

- **APLICADO (09/09, decisão do maintainer, SEM043):** `checkInterfaceImplementation`
  no fim de `analyzeClass` — método ausente → SEM043 nomeando o método;
  aridade divergente → SEM043 com esperado/encontrado (paridade de tipo exata
  aguarda dispatch virtual). Prova: 3 testes `CompilerDriverTest`
  (missing/wrongArity/complete-green).

### SG-016 — Semântica de classes aninhadas

- **APLICADO (09/09, decisão do maintainer, SEM042):** tipo aninhado não
  existe em Kof — `class A { class B {} }` é erro de parse imediato SEM042
  ("declare at top level") em `ClassMemberParser.parseClassMember`;
  interface/record/entity aninhados idem (mesmo branch). Prova:
  `nestedClassGivesCleanDiagnostic` + `topLevelClassStaysGreen`.

### SG-017 — `abstract class` instanciável em compile-time

- **APLICADO (09/09, decisão do maintainer, SEM041):** `new A()` e `A()`
  (construção implícita) de classe abstrata → erro SEM041 compile-time.
  Registro `abstractClasses` em `SymbolTableBuilder.preDeclareType`; checagem
  nos 2 caminhos de instanciação (SemExpressionTyper NewExpr +
  BuiltinCallTyper receiver-null — `Shape()` é MethodCallExpr, não NewExpr).
  Prova: `abstractClassInstantiationFails` +
  `abstractClassSubclassInstantiationStaysGreen`.

### SG-018 — Exit code de `Int main()`

- **APLICADO (09/09, decisão do maintainer, SEM044):** a forma `Int main()`
  foi REMOVIDA — o entry point é SÓ `main()` (sem tipo de retorno, sem
  modifiers); `Int main()` → erro SEM044. Modifiers em main nem chegam ao SEM
  (parser sempre passa mods vazios p/ top-level function; SEM044 protege o
  contrato na camada semântica). O IR já emite public static void. Prova:
  3 testes `CompilerDriverTest` (typedMain/modifiedMain/plainMain-green).

### SG-019 — Cláusula `throws` é decorativa

- **APLICADO (09/09, decisão do maintainer, SEM045):** descoberta — top-level
  function NEM CAPTURAVA `throw` (só `parseClassMember` chamava `parseThrows`;
  o gap dizia "decorativa", na verdade era duplamente morta). Fix:
  `Parser.parseFunctionDeclaration` captura + `SemanticAnalyzer.checkThrowsClause`
  valida que cada nome é tipo conhecido (classe/interface do módulo, builtin,
  ou externa via import) → SEM045. Prova: `throwsUnknownTypeGivesCleanDiagnostic`
  + `throwsKnownTypeStaysGreen`.

### SG-020 — Modelo de memória concorrente ausente

- **Implementação**: `spawn`/`await`/`Channel` funcionam, mas não há definição
  de happens-before/visibilidade/atomicidade.
- **Recomendação**: para uma spec de conformidade, adotar um modelo (mesmo que
  "sequentially consistent por target"). Hoje Unspecified.

---

## Categoria C — Divergências entre targets (paridade)

| # | Divergência | JVM | Native | JS | Gap |
|---|---|---|---|---|---|
| SG-C1 | Short-circuit `&&`/`||` | ✅ | ✅ | ❌ | SG-006 |
| SG-C2 | Exceção (representação) | RuntimeException | kof_panic | throw string | Stable efeito |
| SG-C3 | GC | JVM | free-list/mark-sweep (x86); bump (riscv) | engine | Target-specific |
| SG-C4 | FP extremo | IEEE | IEEE (FLT001) | IEEE | FLT001 |
| SG-C5 | Interop tipos host | ✅ | ❌ | ❌ | Target-specific |
| SG-C6 | `println(null)` | "null" | (corrigido R6) | "null" | — |
| SG-C7 | Map/Set type-arg classe | ❌ bug#33 | ❌ bug#33 | ❌ bug#33 | #33 |
| SG-C8 | `spawn{lambda}` handle | ❌ bug#29 | ❌ bug#29 | ❌ bug#29 | #29 |

---

## Categoria D — Bugs conhecidos (referência cruzada)

Não duplicados aqui — ver [known-bugs.md](known-bugs.md):
- **#29** spawn{lambda}-com-handle (todos os targets)
- **#30** decode<Bool> x86_64 (corrigido)
- **#31** process.<inexistente>
- **#32** type-arg genérico via import (corrigido — `qualifyDeep`)
- **#33** Map/Set com type-arg de classe (emit) — **aberto**

---

## Categoria E — Documentação desatualizada (docs ≠ código)

### SG-E1 — `docs/architecture.md` chama riscv64/aarch64 de "placeholder x86_64"

- **Doc** (`architecture.md:40-46,97-98`): "codegen ainda x86_64 (placeholder)".
- **Código**: `NativeBackend.emitRiscv` (`:1947`) é lowering riscv64 **real**;
  aarch64 via `translateRiscvToAarch64` (`:8200`). `docs/status.md:668,737`
  confirma "core completo".
- **Problema**: a arquitetura está **1 versão desatualizada** (0.2.6 → 0.3.0).
- **Recomendação**: atualizar `architecture.md` (feito parcialmente em
  [compiler-architecture.md](compiler-architecture.md); o arquivo antigo deve
  apontar para o novo).

### SG-E2 — `docs/language-state.md` data 02/09, versão 0.2.6-beta

- Conta 810 testes; hoje são **969**. Versão 0.2.6; hoje 0.3.0.
- **Recomendação**: regenerar ou marcar como snapshot histórico.

### SG-E3 — `docs/architecture.md` lista "KofC Backend" como backend da IR

- **Doc**: mostra `KofC Backend` no pipeline consumindo a IR.
- **Código**: `KofCCompiler` **não** implementa `Backend` nem consome
  `IRModule` — é um compilador C-subset separado (`kof-c-compiler`).
- **Recomendação**: corrigir o diagrama (o pipeline de IR tem 3 backends:
  JVM/Native/JS; Android é JVM+empacotamento).

---

## Resumo

- **20 gaps SG-00x** (A: contradições doc/código; B: comportamento não
  especificado).
- **8 divergências de target** (C).
- **3 docs desatualizados** (E).
- **5 bugs** (D, já em known-bugs).

**Nenhum foi corrigido na linguagem** — esta tarefa é de documentação. Cada
item B/C que envolve mudança de semântica é **decisão de design** (regra 6:
 ) e deve virar gap/plano em `planning-*`, nunca edição
silenciosa.
