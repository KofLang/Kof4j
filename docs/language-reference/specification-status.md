# Status da Especificação

**Versão:** 0.3.0-beta · **Data:** 06/09/2026

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
| `sealed`/`permits` | **Unspecified** (tokens mortos) | nenhum (SG-002) |
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
| Deref `T?` sem narrowing | **Unspecified** (compila) | probe (SG-005) |
| Subtipagem por herança | **Unspecified** (não checada) | probe (SG-009) |
| Generics (erasure) | Stable | `KofMapSetTest`, `PackagesE2ETest` |
| Variance (`? extends`) | **Unspecified** (quebra runtime) | probe (SG-007) |
| Bounds de type-var | **Planned/ausente** | nenhum |
| Inferência de type-args de ctor | **Ausente** | nenhum |
| Checagem de elem em `list.add` | **Unspecified** (não checa) | probe (SG-009) |
| `==` por tipo (conteúdo/identidade) | Stable | probes + `CoreRegressionE2ETest` |
| Overload de construtor (aridade) | Stable | `SymbolTable.java:47` |
| Overload de método | **Ausente** | nenhum |
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
| Inferência de param de lambda | **Unspecified** (exige anotação) | probe SEM001 |
| Função aninhada | **Unspecified** | nenhum (SG-011) |
| Trailing lambda | Stable | `LambdaE2ETest` |

### Classes e tipos de dados
| Feature | Status | Teste-evidência |
|---|---|---|
| Class mutável + constructor | Stable | `ClassFileE2ETest` |
| `class X(...)` = record | Stable | `AGENTS.md`, probes |
| Record (equals/hashCode/toString) | Stable | `KofPatternMatchingTest` |
| Enum (só constantes, valor=String) | Stable | `KofEnumTest`, `KofEnumSwitchTest` |
| Interface (default methods) | Stable | probe |
| Interface sem checagem de cobertura | **Unspecified** | probe (SG-015) |
| Herança + override virtual | Stable | probes |
| `private` em compile-time | **Unspecified** (só runtime) | probe (SG-013) |
| `abstract` não-instanciável | **Unspecified** (só runtime) | probe (SG-017) |
| Pattern matching (binding+destruturing) | Stable | `KofPatternMatchingTest` |
| Pattern com guarda/aninhado | **Ausente** | nenhum (SG-014) |
| Entity (ORM) | **Experimental** | `KofOrmE2ETest` |
| Classes aninhadas | **Unspecified** | nenhum (SG-016) |
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

### Concorrência
| Feature | Status | Teste-evidência |
|---|---|---|
| spawn (statement) | Stable | `KofConcurrency2Test` |
| spawn (expressão → Handle) | Stable | probe |
| await | Stable | `KofAwaitTest` |
| awaitTimeout | Stable | probe |
| Channel | Stable | `KofConcurrency2Test` |
| Modelo de memória | **Unspecified** | nenhum (SG-020) |
| `spawn { lambda }` com handle | **Bug #29** | `known-bugs.md` |

### Exceções
| Feature | Status | Teste-evidência |
|---|---|---|
| throw String | Stable | `ExceptionsE2ETest` |
| try/catch/finally | Stable | `ExceptionsE2ETest` |
| `throws` validado | **Ausente** | probe (SG-019) |
| Representação por target | **Target-specific** | `ExceptionsE2ETest` |

### Stdlib (`kof.*`)
| Feature | Status | Teste-evidência |
|---|---|---|
| json | Stable (3 targets) | `JsonCompleteE2ETest` |
| collections (List/Map/Set) | Stable | `KofMapSetTest` |
| string methods | Stable | `StringMethodRegistry` |
| http / web / db / orm / cache / mq / time / scheduler / log / config / security / validation / observability / ui / media / process | **Experimental** | E2E por área |
| Map/Set com type-arg de classe | **Bug #33** | `known-bugs.md` |

---

## 2. Conformance — o que impede uma definição rigorosa hoje

Uma definição de conformidade (aceitar válidos, rejeitar inválidos, preservar
significado) **não pode ser rigorosa** enquanto existirem:

1. **Regras Unspecified** listadas acima (subtipagem, null-deref, modelo de
   memória, classes aninhadas, sobrecarga top-level, exit code de main).
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
- **Unspecified**: ~20 pontos (SG-002 a SG-020).
- **Planned/Ausente**: bounds de type-var, sobrecarga de operador, labeled
  break, `~`, ternário, range, macros, traits, type alias.
