[English](specification-status.md) | [Português](specification-status.pt_BR.md)

# Status da Especificação

**Versão:** 0.5.0-beta · **Data:** 06/09/2026 · **Re-sincronizado 17/09/2026** contra os SG-00x aplicados (`docs/bugs-and-gaps/specification-gaps.md`) e os fixes #322/#330.

Classificação de cada feature da linguagem. **Nada aqui é "estável" por
cortesia** — Stable exige semântica congelada (regra 0.2.6-beta) **e** teste
que a prova. Categorias: **Stable · Experimental · Implementation-defined ·
Target-specific · Unspecified · Planned**.

---

## 1. Classificação por feature

### Núcleo sintático
| Feature | Status | Teste-evidência |
|---|---|---|
| Lexer (tokens, literais, comentários) | Stable | `Lexer` exercitado por toda a suíte |
| Parser recursive descent | Stable | `FunctionSyntaxTest`, `Parser` via E2E |
| Semicolon opcional | Stable | probes + suíte |
| Keywords (lista) | Stable | `Lexer.java:13-74` |
| `sealed`/`permits` | **Ausente** (tokens mortos removidos do lexer; `sealed class S {}` → `PARSE010`) | `deadTokensGiveCleanLexerError` (SG-002) |
| `fn`/`fun`/`func` prefixo | **Stable** (rejeitado com `PARSE085`, SG-001 resolvido 06/09) | `FunctionSyntaxTest` (5 casos) |

### Sistema de tipos
| Feature | Status | Teste-evidência |
|---|---|---|
| 9 primitivos | Stable | `Type.java`, suíte |
| `string` como referência | Stable | `BuiltinTypes.java:11` |
| Widening numérico implícito | Stable | probes + `emitWideningIfNeeded` |
| Narrowing só via `as` | Stable | probe SEM021 |
| `bool→numérico` (=1/0) | **Implementation-defined** | probe (representação vazou) |
| Nullability `T?` | Stable | `KofPatternMatchingTest`, probes |
| Narrowing `if (x != null)` | Stable | probes |
| Deref `T?` sem narrowing | Stable — deref sem narrowing → `SEM049` | probe (SG-005) |
| Subtipagem por herança | Stable — nominal; classe não relacionada → `SEM021` | probe (SG-009) |
| Generics (erasure) | Stable | `KofMapSetTest`, `PackagesE2ETest` |
| Variância (`? extends`) | **Ausente** — wildcard → `PARSE086` | probe (SG-007) |
| Bounds de type-var | **Planned/ausente** | nenhum |
| Inferência de type-args de ctor | **Ausente** | nenhum |
| Checagem de elem em `list.add`/`set`/`map.put` | Stable — tipo errado → `SEM056` | probe |
| `==` por tipo (conteúdo/identidade) | Stable | probes + `CoreRegressionE2ETest` |
| Overload de construtor (aridade) | Stable | `SymbolTable.java:47` |
| Overload de método (aridade/tipos, mesma classe) | Stable (desde 13/09, §131) | `MethodCallTyper` (SG-011) |
| Default parameters | Stable | `lowerFunctionDefaults` |

### Funções e closures
| Feature | Status | Teste-evidência |
|---|---|---|
| 3 formas de retorno | Stable | `FunctionSyntaxTest` |
| Expression body (`= expr`) | Stable | `FunctionSyntaxTest` |
| `main` (formas) | Stable | probes + `JvmE2ETest` |
| Recursão direta | Stable | probe `fact(5)` |
| TCO | **Ausente** (não garantido) | nenhum |
| Função genérica | Stable | probe `idf<Int>` |
| Lambda (formas) | Stable | `LambdaE2ETest` |
| Captura snapshot | Stable | probe |
| Captura mutável (Box) | Stable | probe `n=2` |
| Function types 1ª classe | Stable | `KofHigherOrderTest` |
| Inferência de param de lambda | Stable com **contexto** (`List` `map`/`filter`/`reduce`); sem contexto → `SEM001` | `LambdaE2ETest`, probe (SG-012) |
| Função aninhada | Stable (hoisted para `outer__inner`) | `JvmE2ETest.execNestedFunction` (SG-011) |
| Trailing lambda | Stable | `LambdaE2ETest` |

### Classes e tipos de dados
| Feature | Status | Teste-evidência |
|---|---|---|
| Class mutável + constructor | Stable | `ClassFileE2ETest` |
| `class X(...)` = record | Stable | `AGENTS.md`, probes |
| Record (equals/hashCode/toString) | Stable | `KofPatternMatchingTest` |
| Enum (só constantes, valor=String) | Stable | `KofEnumTest`, `KofEnumSwitchTest` |
| Interface (default methods) | Stable | probe |
| Cobertura de interface (classe concreta deve implementar) | Stable | `ImplementationChecker.checkInterfaceImplementation` `SEM043` (SG-015); `abstract` pode adiar, obrigação transitiva via supers abstratos (#322) |
| Herança + override virtual | Stable | probes |
| `private`/`protected` em compile-time — **métodos + campos**; escrita `final` | Stable | `SEM046`/`SEM065` (SG-013) |
| `private`/`protected` em compile-time — **campos** | **Unspecified** (só runtime) | probe (SG-013) |
| `abstract` não-instanciável | Stable | `SEM041` (SG-017) |
| Pattern matching (binding+destruturing) | Stable | `KofPatternMatchingTest` |
| Pattern com guarda/aninhado | **Ausente** | nenhum (SG-014) |
| Entity (ORM) | **Experimental** | `KofOrmE2ETest` |
| Classes aninhadas | **Ausente** (erro de parse `SEM042`) | `nestedClassGivesCleanDiagnostic` (SG-016) |
| Sobrecarga de operador | **Ausente** | nenhum |

### Controle de fluxo
| Feature | Status | Teste-evidência |
|---|---|---|
| if/else (stmt + expr) | Stable | suíte |
| while/do-while/for/for-in | Stable | suíte |
| switch statement (sem fallthrough) | Stable | `KofEnumSwitchTest` |
| switch expression (SYN001) | Stable | `KofSwitchExprE2ETest` 23/23 |
| break/continue (sem label) | Stable | probes |
| Labeled break | **Ausente** | probe (SG-002) |

### Módulos e nomes
| Feature | Status | Teste-evidência |
|---|---|---|
| package | Stable | `PackagesE2ETest` |
| import (classe) | Stable | `PackagesE2ETest` |
| import wildcard (traz decls) | Stable | `CompilerImports` |
| import wildcard (qualifica nome) | **Ausente** (não qualifica) | `:57` |
| qualifyDeep (type-args) | Stable | `PackagesE2ETest` (bug 32) |
| Import ambíguo (não chuta) | Stable | `CompilerTypes:102` |
| PKG002 (1 main) | Stable | probe |
| Interop JVM (tipos Java) | **Target-specific** | `AndroidInteropE2ETest` |
| FFI C (`extern "<lib>"`) | **Parcial** — JVM qualquer assinatura escalar, aridade livre, retornos `void`/`String` (18/09, `.18`); não-escalar = `FFI001` (**exceto a fatia JVM 3.8b: `record` por valor arg/retorno + `T[]` escalar→`ptr` ✅ 20–21/09**, `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5); runner host JS = MESMA ABI escalar (3.6.F2/F3 ✅ 18/09, `FfiE2ETest` 16/16 byte-for-byte JVM↔JS), não-escalar = `FFI002`, browser = runtime R7; **Native = ABI escalar DIRETA em x86-64/riscv64/aarch64** (#431 fatias 1–2 ✅ 20/09, §369: link-by-use + `call sym@PLT`, sem `dlopen`; `FfiNativeE2ETest` 16/16 + `FfiNativeCrossE2ETest` 6/6 qemu), não-escalar/callback/`library()` ausente = `FFI001` (R6, nunca silencioso); args numéricos são convertidos ao slot pela regra comum (§370/#549 ✅ 20/09, `FfiExternTypeConversionTest` 11/11 JVM/Native/host JS) |

### Concorrência
| Feature | Status | Teste-evidência |
|---|---|---|
| spawn (statement) | Stable | `KofConcurrency2Test` |
| spawn (expressão → Handle) | Stable | probe |
| await | Stable | `KofAwaitTest` |
| awaitTimeout | Stable | probe |
| Channel | Stable | `KofConcurrency2Test` |
| Modelo de memória | Definido — SC + 6 regras happens-before | `concurrency-memory-model.md` (SG-020) |
| `spawn { lambda }` com handle | **Bug #29** | `known-bugs.md` |

### Exceções
| Feature | Status | Teste-evidência |
|---|---|---|
| throw String | Stable | `ExceptionsE2ETest` |
| try/catch/finally | Stable | `ExceptionsE2ETest` |
| Nomes da cláusula `throws` checados por tipo (`SEM045`) | Stable (desde 09/09, SG-019) | `throwsUnknownTypeGivesCleanDiagnostic` |
| Representação por target | **Target-specific** | `ExceptionsE2ETest` |

### Stdlib (`kof.*`)
| Feature | Status | Teste-evidência |
|---|---|---|
| json | Stable (3 targets) | `JsonCompleteE2ETest` |
| collections (List/Map/Set) | Stable | `KofMapSetTest` |
| string methods | Stable | `StringMethodRegistry` |
| http / web / db / orm / cache / mq / time / scheduler / log / config / security / validation / observability / ui / media / process | **Experimental** | E2E por área |
| `rng` (namespace embutido: `seed`/`int`/`boolean`/`double`/`string`) | **Experimental** — JVM+JS+NATIVE x86_64+ANDROID (ANDROID reusa o runtime JVM, issue #777); cross riscv64/aarch64 gap `RNG001` (honesto, R6); mesma seed ⇒ mesma sequência em todo backend | `KofRngTest` (12): `deterministicJvm/JsMatchesOracle`, `jvmJsParity`, `reseedRestartsSequence{Jvm,Js}`, `contractJvm/Js`, `jvmNativeParityFullFace`, `nativeMatchesOracle`, `contractNative`, `crossStaysHonestGap`, `androidMatchesOracle` |
| Map/Set com type-arg de classe | **Bug #33** | `known-bugs.md` |

---

## 2. Conformance — o que impede uma definição rigorosa hoje

Uma definição de conformidade (aceitar válidos, rejeitar inválidos, preservar
significado) **não pode ser rigorosa** enquanto existirem:

1. **Regras Unspecified** ainda listadas acima (ex.: `private`/`protected` em
   **campos**, ordem de `Map`/`Set`) — a fila SG-00x fora isso está resolvida ou
   aplicada (subtipagem, null-deref, modelo de memória, classes aninhadas,
   sobrecarga top-level, exit code de `main`, …).
2. **Regras Implementation-defined** que vazam para comportamento observável
   (`bool→int`=1/0, `val` não-imutável, ordem de avaliação de `x++` em
   expressão, layout de slots).
3. **Divergências Target-specific** não formalizadas (short-circuit JS,
   representação de exceção, GC, FP extremo, ordem de Map).
4. **Bugs abertos** que fazem o comportamento real divergir do previsto
   (#29 spawn-handle, #33 Map/Set emit).
5. **Ausência de um oráculo de "programa válido"**: sem a gramática formal
   *normativa* (a daqui é *extrativa*), não há como dizer se um programa que o
   parser aceita *deveria* ser aceito.

**Caminho para conformance** (recomendação, não implementada):
- Fechar os SG-00x (decidir cada Unspecified).
- Congelar a gramática EBNF como normativa (não só extrativa).
- Extrair os testes E2E por target num *conformance suite* com expected
  outputs por regra (não por arquivo).
- Definir um perfil de conformidade mínimo (núcleo estável) vs experimental.

---

## 3. Resumo de contagem

- **Stable**: núcleo (sintaxe, tipos primitivos, widening, nullability básica,
  generics erasure, `==`, funções, closures, classes/records/enums/interfaces,
  controle de fluxo, pacotes/imports, concorrência básica, exceções, json,
  collections).
- **Experimental**: stdlib de domínio (http/web/db/orm/ui/media/…).
- **Implementation-defined**: `bool→numérico`, `val`, layout de frames,
  mecanismo de spawn.
- **Target-specific**: GC, FP extremo, exceção (representação), short-circuit
  JS, interop (só JVM), ordem de Map.
- **Unspecified**: 1 ponto — `private`/`protected` em **campos** (SG-013); o
  resto de SG-002–SG-020 está resolvido/aplicado.
- **Planned/Ausente**: bounds de type-var, sobrecarga de operador, labeled
  break, `~`, ternário, range, macros, traits, type alias.
