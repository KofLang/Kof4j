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

### SG-005 — Deref de `T?` sem narrowing não é erro ✅ CORRIGIDO 10/09 (SEM049)

- **Implementação anterior**: `var s: String? = "x"; s.length` **compila e roda**.
  O lowering desembrulha o receiver (`ExpressionTyper.java:143`). Null-safety era
  **advisory**: o compilador não impede NPE.
- **Correção (10/09, breaking — regra 6 suspensa, decisão do maintainer no
  SG-005/008 "o próprio nome já diz")**: deref de `T?` sem narrowing → erro
  **SEM049** ("receiver is nullable (T?); narrow first").
  1. **Method call** (`SemMethodCallTyper`, logo após inferir `recv`): receiver
     `NullableType` → SEM049, antes dos branches de coleções/process/channel.
  2. **Field/property** (`SemExpressionTyper` case `FieldAccessExpr`): idem —
     `s.length` em `String?` era o furo (o `Type.isString` desembrulha Nullable).
  3. **Narrowing estendido** (`StatementAnalyzer.collectNarrowing`): além do
     `if (x != null)` → THEN (que já existia), agora `if (x == null)` → **ELSE**,
     e conjunção `x != null && Y` narrowa o THEN inteiro. Disjunção (`||`) NÃO
     narrowa (o ramo roda se UM valer) — honesto.
  4. **Narrowing intra-expressão** (`SemExpressionTyper.narrowedScope`): em
     `if (s != null && s.length > 0)`, o lado DIREITO da `&&` vê `s` narrowed
     (short-circuit: o lado só é avaliado se o esquerdo passou) — sem isso a
     PRÓPRIA condição daria SEM049 no `s.length`.
- **Aritmética sobre `T?`** (`a + 1` com `a: Int?`) **continua verde** — não é
  deref; o guard-unbox do bug 87 cobre.
- **Testes migrados**: `KofMapSetTest.memberCallOnNullableInferredFromMapJVM`
  (deref direto → narrowing `if (v != null)`).
- **Provas**: `CompilerDriverTest.nullableDerefWithoutNarrowingFails` /
  `nullableDerefPropertyWithoutNarrowingFails` (SEM049) +
  `nullableNarrowedIfStaysGreen` / `nullableNarrowedAndStaysGreen` /
  `nullableNarrowedElseStaysGreen` (246/246). Suíte compiler 1263 run /
  0 falhas de código (15 errors ambientais: node/javac/javap).

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

### SG-008 — Null safety: `T?` nunca NPE e literal `null` não é fabricável ✅ CORRIGIDO 10/09 (bug 87 + SEM048)

- **Implementação anterior**: `Int? a = null; a == null` → **NPE em runtime**
  (*probe*: o unbox do `Integer` null lança). `String? s = null; s == null` →
  `true` corretamente. Inconsistência entre nullable de primitivo e de referência.
- **Decisão do maintainer (09/09)**: "o próprio nome já diz" — **NENHUM literal
  `null` é atribuível** (nem a `T?`): `Int? x = null` → erro; `x = null` → erro.
  `null` só chega a `T?` via **API** (ex.: `mapOf("k", v).get("missing")`), e
  `T? == null` é comparação de **referência** (nunca NPE por unbox).
- **Implementação (10/09):**
  1. **SEM048** — `StatementAnalyzer` rejeita literal `null` em `VarDeclStmt`
     (`T? x = null`) e em atribuição (`x = null`); o idioma correto é obter `null`
     de API. Prova: `CompilerDriverTest.nullInVarDeclFails` /
     `nullInAssignmentFails` / `nullFromApiStaysGreen` (241/241).
  2. **`Map.get()` devolve `V?` para TODO `V`** — `CollectionCallLowerer`,
     `CollectionMethodTyper`, `SemMethodCallTyper`, `MemberCallTyper` deixam de
     devolver `V` para primitivo e passam a devolver `NullableType(valueType)`
     sempre (ausência = null comparável, nunca exceção/unbox). O `put()` em
     `mapOf()` vazio pina os tipos `K,V` no símbolo do local (`SymbolTable.
     updateLocalType`) para o cache semântico não divergir do emit.
  3. **Comparação `T? == x` sem NPE** — `CompilerComparisons` desembrulha
     `NullableType` no tipo de operando; quando um lado é `Unknown`/`Nullable(Unknown)`
     (get de `mapOf()` sem pin) contra um primitivo, a comparação vira referência
     (primitivo boxado, `Objects.equals`) espelhando o interpretador, em vez de
     `if_icmp*` sobre null → VerifyError. `ExpressionBinaryLowerer` faz o box do
     lado primitivo na ordem correta. O interpretador ganha `eqAllowsNull` /
     unbox com guard (`KofInterpreterCollections`/`KofInterpreterOps`/
     `KofInterpreterValues`).
- **KofScript** migrado para o mesmo idioma (não fabrica null, usa `mapOf().get()`).
- **Nota (regra 6)**: o programa `m.get(k) == 1` continua compilando — o `1` é
  boxado e comparado por `if_acmpeq` (paridade cross-target). Provas de paridade em
  `BackendParityTest`/`ConformanceMatrixTest`/`KofScriptTest`.
- **Registrado em `known-bugs.md` §87.**

### SG-009 — Subtipagem não é checada pelo type checker — ✅ CORRIGIDO 10/09 (SEM021 nominal)

- **Implementação anterior**: `isAssignable` retornava `true` para **qualquer**
  par `ClassType→ClassType` (`TypeChecker.isAssignable`). A segurança vinha
  do `checkcast` do lowering/runtime.
- **Problema**: `A a = <objeto de classe não-relacionada>` passava na checagem
  de tipos; falhava só em runtime. `implements` sem cobrir métodos compila
  (SG-015 — já corrigido). Abstract pode ser instanciado (SG-017 — já
  corrigido).
- **CORRIGIDO 10/09 (subtipagem nominal em `isAssignable`):** novo overload
  `TypeChecker.isAssignable(sa, from, to)` — para referência→referência de
  classes de domínio, caminha `superClass`/`interfaces` via BFS (mesmo padrão
  de `MemberResolver.resolveInHierarchy`); não-relacionado → erro compile-time
  **SEM021** (var-decl tipado; assignment/return mantêm SEM012/SEM010 já
  existentes). Conservador (true) quando a hierarquia é desconhecida — tipo
  externo (imports Android/JDK), builtin (String/List/Map, relações próprias
  do BuiltinTypes) ou classe não declarada no módulo — restringir isso
  quebraria interop legítima (regra 6: nunca quebrar o que funciona).
  **Subtipos legítimos continuam verdes**: `Dog extends Animal` → `Animal a =
  Dog()` compila (superclass BFS); `Cat implements Speaker` → `Speaker s =
  Cat()` compila (interfaces BFS); `Object` raiz aceita qualquer referência.
  **Call sites migrados**: `SemExpressionTyper:225` (assignment-expr),
  `StatementAnalyzer:48` (assignment-stmt), `:147` (var-decl tipado),
  `:164` (return). **Provas:** 4 testes novos em `CompilerDriverTest`
  (`unrelatedClassAssignmentFails` = SEM021 no repro `Cat c = Dog()`;
  `subclassAssignmentStaysGreen`; `interfaceAssignmentStaysGreen`;
  `externalTypeAssignmentStaysConservative`) — CompilerDriverTest 250/250;
  suíte compiler **1270 run / 0 falhas de código** (16 errors ambientais =
  node/javac/javap ausentes); zero falso-positivo no corpus (todos os
  programas legítimos existentes continuam compilando).

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

### SG-020 — Modelo de memória concorrente ausente — ✅ CORRIGIDO 09/09 (spec) / validado 10/09

- **Implementação anterior**: `spawn`/`await`/`Channel` funcionavam, mas não
  havia definição de happens-before/visibilidade/atomicidade.
- **CORRIGIDO 09/09:** spec completa em
  `docs/development/concurrency-memory-model.md` — SC em todos os targets,
  6 regras de happens-before (spawn/await/channel/cancel/locais/race),
  mapeamento por target (JMM virtual threads / x86-TSO futex / riscv-aarch
  fence), non-goals (sem volatile/synchronized na superfície — Channel é a
  abstração), DoD com provas. Interpretador: mapa de statics concorrente
  (HB por campo).
- **Validado 10/09 (varredura doc↔código):** as provas §4 do doc — (1)(2)(5)
  cobertas por `SpawnE2ETest`/`KofConcurrency2Test` (spawn/await/channel
  cross-target); (3) `staticsAreSequentiallyConsistent` (1998000) e
  (4) `noWordTearingOnLong` (leitor nunca vê valor inválido) **já
  implementados** em `KofConcurrency2Test:699/:737` (o doc §4 dizia
  "(3)(4) a implementar" — desatualizado; corrigido no doc). Gate:
  `KofConcurrency2Test` 29/0/1-skip (qemu) na suíte 1270/0-código.

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

### SG-E1 — `docs/architecture.md` chama riscv64/aarch64 de "placeholder x86_64" — ✅ CORRIGIDO 10/09 (residual)

- **Doc** (`architecture.md:40-46,97-98`): "codegen ainda x86_64 (placeholder)".
- **Código**: `NativeBackend.emitRiscv` é lowering riscv64 **real**;
  aarch64 via `translateRiscvToAarch64`.
- **CORRIGIDO 10/09:** o cabeçalho do doc já tinha a nota de correção de
  06/09; os residuais ("codegen x86_64 placeholder via qemu" no enum Target
  e a seção de targets 0.2.6) foram atualizados para lowering real.
  Verificação: grep "placeholder" em `docs/architecture.md` agora só
  aparece na nota histórica de correção (que explica o porquê).

### SG-E2 — `docs/language-state.md` data 02/09, versão 0.2.6-beta — ✅ CORRIGIDO 10/09

- Contava 810 testes; hoje são **1270** (kof-compiler só). Versão 0.2.6;
  hoje 0.3.0.
- **CORRIGIDO 10/09:** marcado como **SNAPSHOT HISTÓRICO** (nota no topo
  apontando para `docs/status.md`, `docs/language-reference/` e
  `specification-gaps.md` como fontes correntes). Regenerar o doc seria
  duplicar o status.md — snapshot honesto é melhor que cópia derivada que
  apodrece.

### SG-E3 — `docs/architecture.md` lista "KofC Backend" como backend da IR — ✅ CORRIGIDO (06/09) / verificado 10/09

- **Doc antigo**: mostrava `KofC Backend` no pipeline consumindo a IR.
- **Código**: `KofCCompiler` **não** implementa `Backend` nem consome
  `IRModule` — é um compilador C-subset separado (`kof-c-compiler`).
- **Verificado 10/09:** o diagrama do pipeline em `docs/architecture.md`
  mostra os 3 backends da IR (JvmRuntime/NativeRuntime/JsBackend) e
  `KofCcompiler` está seção própria, sem relação com a IR;
  `docs/compiler-architecture.md` tabela "É / Não é" já diz explicitamente
  "KofC **não é** backend da IR Kof". Fechado sem código novo.

---

## Resumo

- **20 gaps SG-00x** (A: contradições doc/código; B: comportamento não
  especificado). **Fila do maintainer (2ª rodada, 10/09) COMPLETA:**
  SG-008 ✅, SG-005 ✅, SG-009 ✅, SG-020 ✅ — ver histórico em cada seção.
- **8 divergências de target** (C).
- **3 docs desatualizados** (E) — **todos ✅** (E1 residual 10/09, E2
  snapshot 10/09, E3 verificado).
- **5 bugs** (D, já em known-bugs) — 29/30/31/32/33 ✅ corrigidos.

**Nenhum foi corrigido na linguagem** — esta tarefa é de documentação. Cada
item B/C que envolve mudança de semântica é **decisão de design** (regra 6:
 ) e deve virar gap/plano em `planning-*`, nunca edição
silenciosa.
