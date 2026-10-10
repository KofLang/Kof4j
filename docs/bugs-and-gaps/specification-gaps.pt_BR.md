[English](specification-gaps.md) | [Português](specification-gaps.pt_BR.md)

# Specification Gaps e Divergências

> **CONSOLIDADO — movido de `docs/development/` p/ `docs/language-reference/` (09/09) e p/ `docs/bugs-and-gaps/` (13/09, refactor de clareza da mantenedora) em
> 12/09** (regra dos 3 estados): as 23 entradas SG-001–020 + E1–E3 estão todas
> resolvidas (APLICADOS 06–12/09, maioria por decisão explícita da mantenedora —
> fila da 2ª rodada COMPLETA, ver §Resumo). O doc vira **referência da spec**
> (o que cada SG exige e onde está travado); novos gaps de spec entram aqui com
> status próprio. Bugs abertos ficam em `docs/bugs-and-gaps/known-bugs.md`.

**Versão:** 0.5.0-beta (pom `revision`; era 0.3.0-beta na auditoria) · **Data:** 06/09/2026 · **Fonte:** auditoria completa do
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
  R6); em qualquer posição de nome (função, variável, parâmetro, método, campo,
  classe, record, enum) o `ParseContext.expectId` emite o mesmo `PARSE085`
  (medido 17/09, #330; antes o genérico `PARSE037` variável / `PARSE023`
  parâmetro). Alinhado ao corpus (regra 4). KofScript
  (`.ks`) mantém `fn` como sintaxe própria e traduz na fronteira
  KofScript (`.ks`) **não** é exceção: é Kof puro (sem `fn`/`let`/`async`).
  Testes: `FunctionSyntaxTest` (15: fun/fn/func
  rejeitados como prefixo, `fn calc(): Int` rejeitado, `fn()`/`var fun`/
  `param fn` rejeitados, membro de classe, `Int calc():Int` idiomático; o
  #330 adicionou `var fun`/`var fn`/`var func` como nome → `PARSE085`).
  `let`/`const`/`async` são inexistentes em `.kf` **e** `.ks` (KofScript não
  é JavaScript — sugar removido 06/09).

### SG-002 — Tokens e keywords que a gramática não usa

- **APLICADO (opção 1 da recomendação — tokens REMOVIDOS do lexer; provado
  12/09):** `~`, `::`, `...`, `=>`, `|>`, `_` isolado e as keywords
  `sealed`/`permits` não existem mais como tokens — grep 0 em
  `TokenType.java`/`Token.java`/`parser/Lexer.java` (a lista acima deste
  parágrafo descrevia o estado PRÉ-fix). `~5` agora é **LEX005** ("Unexpected
  character", `Lexer.java:467`); `a => b`/`xs |> f`/`A::b` caem no parse com
  `PARSE041`; `sealed class S {}` vê `sealed` como IDENTIFIER comum →
  `PARSE010` (declaração sem tipo). Sem diagnóstico de "feature reservada":
  a gramática simplesmente nunca os usou, e agora o lexer também não.
- **Prova:** `CompilerDriverTest.deadTokensGiveCleanLexerError` (5 casos com
  código exato esperado, 1/1 verde 12/09) — o teste que TRAVA a remoção
  (regressão de qualquer token morto que ressurgir).

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
  **fortemente tipada e estaticamente tipada**"; `docs/architecture/architecture.md`
  "fortemente tipada".
- **Implementação (histórico)**: o type checker **não** garante subtipagem
  (§SG-009), **não** checa elemento de coleção, **não** impõe
  `private`/`abstract` em compile-time, **não** impede reatribuição de `val`.
- **Problema**: "strongly typed" é vago e, lido como "o compilador impede
  operações mal tipadas", é **falso** para Kof hoje.
- **Recomendação**: substituir por propriedades concretas (já feitas em
  [language-reference/type-system.md](../language-reference/type-system.md)).
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
  4. **Narrowing intra-expressão** (`SemNarrowing.narrowedScope`, chamado de
     `SemExpressionTyper`; o REFACTOR-500 de 17/09 o moveu): em
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
  [expressions.md](../language-reference/expressions.md) §5) **e** abrir gap de
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
  (só `type`/`name`/`initializer` — `VarDeclStmt.java:4`).
- **Documentação**: `AGENTS.md` "val y = 20 // imutável".
- **Problema**: `val` é decorativo. A distinção `val`/`var` não tem efeito
  observável.
- **Recomendação**: ou implementar rejeição de atribuição a `val` (SEM novo), ou
  documentar que `val` é convenção (não-garantido). Decisão de design.
- **CORRIGIDO 09/09 (DD-02, bug 62a):** `val` agora é imutável — reatribuir
  (incl. compound `+=`) emite **SEM037** ("cannot assign to immutable 'val'"). O
  parser carrega `type="val"` (antes sempre "var"); `LocalVariableSymbol` ganhou
  `isVal`; `analyzeAssignmentStatement` checa. Ver `docs/decisions/planning-mutability.md`.

### SG-011 — Função aninhada e sobrecarga top-level

- **APLICADO (09/09, decisão do maintainer, SEM048-lane spec-gaps):** função
  aninhada funciona — parser captura `Type name(params) { ... }` em statement
  (`lookaheadNestedFunction`, checado ANTES do typed-var-decl) e o desugar faz
  hoisting para top-level `outer__inner` inserida ANTES da outer ("inner
  primeiro"); chamadas `inner(...)` reescritas para `outer__inner(...)`.
  Semântica: inner definida antes do corpo executar; outer chama e aguarda o
  retorno. Prova: `JvmE2ETest.execNestedFunction` (42) +
  `execNestedFunctionWithCondition`.
- **APLICADO (11/09, parte B — sobrecarga top-level, oracle JVM):** funções
  homônimas com ASSINATURAS diferentes coexistem e o call site resolve o
  candidato aplicável mais específico (`TopLevelOverload.pick` — igualdade
  exata > subtipagem; a JVM é o oráculo). O que continua ERRO: duplicata
  EXATA de assinatura (SEM047) e colisão só-de-retorno (retorno não é
  assinatura, como na JVM); chamada ambígua entre candidatos aplicáveis →
  SEM057 com hint do cast (R6: nunca escolha silenciosa). Paridade por
  construção: a seleção acontece no frontend e cada backend referencia o
  candidato pela assinatura — JVM = descritor do `invokestatic` (já levava os
  `argTypes` do escolhido), Native = símbolo sufixado por tag de assinatura
  (`Default_Main_g_I` vs `_I_I`; x86/riscv, aarch traduz; wrappers de
  default-arg param o sufixo próprio — de-duplica colisão latente no `as`),
  JS = nome sufixado quando há ≥2 assinaturas sob o nome (chave async por
  assinatura), interpretador = `findKofMethod` casa `KofCall.parameterTypes`
  com fallback nome+aridade. Programa com um único candidato por nome é
  byte-idêntico ao antes em todos os targets (invariante de não-regressão).
  Prova: `TopLevelOverloadE2ETest` (saída idêntica nos 6: JVM/Script/JS/x86/
  riscv64/aarch64 sob qemu — `5 11 abab 42` + defaults `7 11`),
  `CompilerDriverTest` (assinaturas distintas compilam; duplicata e
  só-retorno SEM047).
- **Implementação (histórico)**: função dentro de função não era parseada como
  declaração (SG-011); duas funções top-level homônimas colidem sem
  diagnóstico claro (o `define` sobrescreve).
- **Relacionado (MÉTODO, não top-level):** sobrecarga de método por ARIDADE na
  mesma classe era o **bug 131 de `known-bugs.md` — ✅ CORRIGIDO 13/09**
  (DECIDIDO 13/09, opção 10a: implementar; `18a64d45`, 4 backends, `MethodCallTyper`
  escolhe por aridade+compatibilidade; métodos de classe de mesmo nome com
  assinaturas diferentes coexistem). Esta SG-011 cobre só a sobrecarga de FUNÇÃO
  top-level (✅); o §131 cobre a face MÉTODO de classe (também ✅ desde 13/09).

### SG-012 — Inferência de tipo de parâmetro de lambda

- **APLICADO (09/09, decisão do maintainer):** inferência contextual — param
  de lambda sem anotação em `map`/`filter`/`reduce` de `List<T>` herda o tipo
  do ELEMENTO (`MemberCallTyper.contextualLambda` reescreve o param no AST;
  padrão SSE já usado p/ KofWeb). `nums.map((x) -> x * 2)` compila sem
  `(x: Int)`. Aritmética sobre param untyped SEM contexto continua SEM001
  (nunca Object silencioso). Prova: `lambdaParamInferredFromListContext` +
  regressão `untypedLambdaParamArithmeticIsDiagnosedNotEmitted`.

### SG-013 — `private`/`protected` sem checagem em compile-time — ✅ FIXED (métodos 09/09; campos 17/09)

- **APLICADO (09/09, decisão do maintainer, SEM046):** causa raiz era
  `defineMethodSymbol` com accessFlags=1 (PUBLIC) hardcoded — modifiers
  descartados. Agora o símbolo carrega PRIVATE/PROTECTED reais e
  `MemberCallTyper.checkMemberAccess` rejeita: private fora da classe
  declarante, protected fora da hierarquia (transitiva), ambos de contexto
  top-level. Prova: 4 testes `CompilerDriverTest` (private/protected,
  dentro/fora).
- **ESTENDIDO a CAMPOS (17/09, #331/#327, lane compiler):** o mesmo contrato
  agora cobre acesso a campo — `private` só na declarante, `protected` na
  declarante/subclasses; `this.x`/`x` nu na classe declarante passa. Raiz:
  `FieldSymbol` perdia os modificadores no `SymbolTableBuilder`; agora
  `MemberCallTyper.checkFieldAccess` rejeita (`SEM046`). Escrita em campo
  `final` fora do construtor declarante é rejeitada com `SEM065` (JVMS 4.4 —
  antes: `IllegalAccessError` silencioso). Prova: `FieldAccessControlTest` 7/7.

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
- **Refinado 17/09 (#322, `ebf59ca4`):** uma `abstract class` pode ADIAR os
  métodos da interface (`abstract class A implements I {}` compila, JLS 8.4.8.1);
  a obrigação é TRANSITIVA — um super abstrato cobra a subclasse concreta, então
  `class C extends A {}` sem `f()` falha com `SEM043` nomeando classe + método +
  "inherited via" (sem `AbstractMethodError` silencioso). Prova:
  `AbstractClassPartialInterfaceE2ETest` 3/3.

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
  `docs/language-reference/concurrency-memory-model.md` — SC em todos os targets,
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

### SG-021 — `json.encode` sem forma indentada (pretty-print) — PEDIDO, sem decisão

- **Origem:** Issue #126 (reporter procurava um `JSON.stringify`-style 3º
  argumento de indentação; o check aceitou `json.encode(x, 4)` — lacuna de
  aridade corrigida 13/09 em `MemberCallNamespaces` com SEM025).
- **Estado da spec:** `json.encode(x)` é o contrato flat (v1) nos 4 targets;
  não existe forma indentada e NUNCA foi prometida.
- **O que é pedido:** face `json.encode(x, n)`/`json.encodePretty(x)` com
  indentação determinística (golden por target). Decisão de design (regra 6):
  cabe à mantenedora; até lá a aridade errada é SEM025 com dica da forma
  correta — nunca fallback silencioso (R6).

### SG-022 — value records / tipos-valor de primeira classe (sem identidade observável) — PEDIDO, sem decisão

- **Origem:** Issue #275 (pedido de feature, 16/09). Proposta: uma forma
  `value record Vec2(Float x, Float y)` com semântica de valor e **sem
  identidade de objeto observável**, deixando cada backend escolher a
  representação física (local, ABI registro/pilha, campo inline, array
  achatado, boxed sob demanda). O reporter explicitamente **não** quer uma
  sintaxe de "alocação na pilha" nem uma estratégia de armazenamento
  garantida — só a propriedade semântica (ausência de identidade).
- **Estado da spec:** `record` hoje é um agregado imutável que ainda tem
  identidade de referência (`==` é `==` de conteúdo, `getClass()` é a classe do
  record, pode ser boxeado e guardado em coleção). **Não** existe sintaxe para
  declarar semântica de valor sem identidade; o corpus nunca prometeu uma.
- **Por que não é edição de lane:** é **sintaxe nova + contrato semântico novo**
  (observabilidade de identidade) → regra 6 (contrato congelado). É decisão de
  design da mantenedora e interage com o freeze de `==`, boxe e coleções. Até
  ser decidido: nenhuma otimização silenciosa de record comum (escape analysis
  segue detalhe de backend, nunca promessa observável).
- **Nota cross-target:** na JVM um value record ainda poderia ser uma classe
  normal (a JVM não tem value types até o Project Valhalla); a garantia seria
  "identidade não observável", imposta pelo compilador (proibir operações de
  identidade) — não "sem alocação". Native x86/riscv poderia achatar/embutir;
  JS boxearia. Qualquer implementação deve declarar o comportamento honesto
  por alvo (R6/R7), nunca prometer alocação na pilha.

### SG-023 — Runner de property-based testing + fixtures de suíte no `kof test` — ✅ DECIDIDO 21/09 (sem superfície nova: opção C + iii)

- **Origem:** restante da fatia 3 do tracker **X8** (`ecosystem-coverage.md` G6
  "next"): depois do `rng` (fatias 1–2 ✅ 18/09, `KofRngTest` 11/11) e da fatia 3
  do `kof test` (`--timeout` ✅ 19/09; **suítes nomeadas por diretório ✅ 21/09**,
  `CmdTestSuiteTest` 2/2), restam duas faces e ambas precisam de superfície.
- **Pedido (a) runner de property:** rodar o corpo de um `test` sobre entradas
  geradas por `kof.rng` (`forAll`), com seed/replay determinístico e minimização
  no FAIL.
- **Pedido (b) fixtures:** setup/teardown por suíte, compartilhados por todos os
  arquivos de um diretório.
- **Estado da spec:** `rng` existe (xorshift128+splitmix32 seedável, JVM/JS/Native-x86)
  e o `kof test` roda `test "name" { }` com timeouts + suítes nomeadas — mas **não**
  há runner de property nem contrato de fixtures; o corpus nunca prometeu nenhum.
- **Superfícies candidatas** (Lei da Simplicidade, regra 11 — a menor que declara a
  intenção): **property** → (A) `property "name" { forAll((gen) -> …) }`, espelhando
  `test "name" { }`; (B) um modo `kof test --props` reinterpretando um `test`
  existente (implícito, mais pesado); (C) nada novo — documentar o idioma
  `rng` + loop e manter o runner fora do v1. **fixtures** → (i) blocos
  `setup { }`/`teardown { }`; (ii) arquivo de convenção por diretório (`_suite.kf`);
  (iii) nada novo.
- **Por que não é edição do lane:** ambas adicionam **superfície de linguagem
  voltada ao usuário** (uma palavra-chave ou contrato de CLI) → regra 6; valem o
  portão da simplicidade (regra 11) e o `D-KOF-FIRST` (o contrato Kof precede
  qualquer empréstimo de QuickCheck/Hypothesis).
- **Todo de implementação (quando decidido):** 1. fixar a superfície (uma opção de
  cada); 2. parser/typer + runner no `kof-cli`/`kof-script`; 3. seed + replay
  determinístico; 4. paridade por alvo JVM/Native-x86/JS/Android (`RNG001` honesto
  só em cross — Android real desde #777); 5. E2E por alvo + corpus (`training/`, `learn/23-testing`);
  6. implementação completa ou gap diagnosticado — sem stub (Q7/R6).
- **Decidido (21/09, delegado pela mantenedora, `D-PROPERTY`):** opção **C** + opção
  **iii** — **sem sintaxe nova**; o runner de property é o idioma existente
  `test "name" { }` + `kof.rng` + `assert`, e as fixtures são o padrão
  `close()` + `try/finally` (`D5-B`). Prova: `PropertyTestIdiomE2ETest` **7/7**
  (reuso semeado, `checksum` idêntico byte a byte JVM==JS e JVM==Native-x86,
  property falsificável FALHA com exit 1, property de zero iterações PASSA
  vacuousamente). Documentado em `training/idioms/stdlib.md` + `learn/23-testing.md`.
  Nenhum parser/typer/codegen tocado.

---

## Categoria C — Divergências entre targets (paridade) — atualizada 10/09

| # | Divergência | JVM | Native | JS | Gap |
|---|---|---|---|---|---|
| SG-C1 | Short-circuit `&&`/`\|\|` | ✅ | ✅ | ✅ CORRIGIDO 09/09 | SG-006 ✅ |
| SG-C2 | Exceção (representação) | RuntimeException | kof_panic | throw string | Stable efeito |
| SG-C3 | GC | JVM | free-list/mark-sweep (x86); bump (riscv) | engine | Target-specific |
| SG-C4 | FP extremo | IEEE | IEEE (cross ✅ 15/09) | IEEE | FLT001 FECHADO (FP→string cross) |
| SG-C5 | Interop tipos host | ✅ | ❌ | ❌ | Target-specific |
| SG-C6 | `println(null)` | "null" | ✅ (R6) | "null" | — |
| SG-C7 | Map/Set type-arg classe | ✅ CORRIGIDO 06/09 (era bug#33 — causa real: nullable inferido) | ✅ | ✅ | — |
| SG-C8 | `spawn{lambda}` handle | ✅ CORRIGIDO 06/09 (bug#29) | ✅ | ✅ | — |

---

## Categoria D — Bugs conhecidos (referência cruzada) — atualizada 10/09

Não duplicados aqui — ver [known-bugs.md](known-bugs.md):
- **#29** spawn{lambda}-com-handle — ✅ CORRIGIDO 06/09
- **#30** decode<Bool> x86_64 — ✅ CORRIGIDO
- **#31** process.<inexistente> — ✅ CORRIGIDO 06/09
- **#32** type-arg genérico via import — ✅ CORRIGIDO (`qualifyDeep`)
- **#33** "Map/Set com type-arg de classe" — ✅ CORRIGIDO 06/09 (causa real: member call em receiver nullable **inferido**; o emit de Map/Set nunca foi o problema)

---

## Categoria E — Documentação desatualizada (docs ≠ código)

### SG-E1 — `docs/architecture/architecture.md` chama riscv64/aarch64 de "placeholder x86_64" — ✅ CORRIGIDO 10/09 (residual)

- **Doc** (`architecture.md:40-46,97-98`): "codegen ainda x86_64 (placeholder)".
- **Código**: `NativeBackend.emitRiscv` é lowering riscv64 **real**;
  aarch64 via `translateRiscvToAarch64`.
- **CORRIGIDO 10/09:** o cabeçalho do doc já tinha a nota de correção de
  06/09; os residuais ("codegen x86_64 placeholder via qemu" no enum Target
  e a seção de targets 0.2.6) foram atualizados para lowering real.
  Verificação: grep "placeholder" em `docs/architecture/architecture.md` agora só
  aparece na nota histórica de correção (que explica o porquê).

### SG-E2 — `docs/history/language-state.md` data 02/09, versão 0.2.6-beta — ✅ CORRIGIDO 10/09

- Contava 810 testes; hoje são **1270** (kof-compiler só). Versão 0.2.6;
  hoje 0.3.0.
- **CORRIGIDO 10/09:** marcado como **SNAPSHOT HISTÓRICO** (nota no topo
  apontando para `docs/status.md`, `docs/language-reference/` e
  `specification-gaps.md` como fontes correntes). Regenerar o doc seria
  duplicar o status.md — snapshot honesto é melhor que cópia derivada que
  apodrece.

### SG-E3 — `docs/architecture/architecture.md` lista "KofC Backend" como backend da IR — ✅ CORRIGIDO (06/09) / verificado 10/09

- **Doc antigo**: mostrava `KofC Backend` no pipeline consumindo a IR.
- **Código**: `KofCCompiler` **não** implementa `Backend` nem consome
  `IRModule` — é um compilador C-subset separado (`kof-c-compiler`).
- **Verificado 10/09:** o diagrama do pipeline em `docs/architecture/architecture.md`
  mostra os 3 backends da IR (JvmRuntime/NativeRuntime/JsBackend) e
  `KofCcompiler` está seção própria, sem relação com a IR;
  `docs/architecture/compiler-architecture.md` tabela "É / Não é" já diz explicitamente
  "KofC **não é** backend da IR Kof". Fechado sem código novo.

---

## Resumo

- **23 gaps SG-00x** (A: contradições doc/código; B: comportamento não
  especificado; SG-021 json pretty-print e SG-022 value records = pedidos sem
  decisão; **SG-023 property runner + fixtures ✅ DECIDIDO 21/09 — sem superfície
  nova, `D-PROPERTY`**). **Fila da mantenedora (2ª rodada, 10/09) COMPLETA:**
  SG-008 ✅, SG-005 ✅, SG-009 ✅, SG-020 ✅ — ver histórico em cada seção.
- **8 divergências de target** (C).
- **3 docs desatualizados** (E) — **todos ✅** (E1 residual 10/09, E2
  snapshot 10/09, E3 verificado).
- **5 bugs** (D, já em known-bugs) — 29/30/31/32/33 ✅ corrigidos.

**Estado 10/09:** a auditoria original foi de documentação, mas a fila
subsequente de decisões do maintainer corrigiu a linguagem com testes
(SEM041–SEM049, SG-009 subtipagem nominal, SG-020 spec de memória). Cada
item B/C que envolve mudança de semântica SEM decisão do maintainer segue
regra 6: vira gap/plano em `planning-*`, nunca edição silenciosa.
