[English](specification-status.md) | [Português](specification-status.pt_BR.md)

# Specification Status

**Version:** 0.5.0-beta · **Date:** 06/09/2026 · **Re-synced 17/09/2026** against the applied SG-00x (`docs/bugs-and-gaps/specification-gaps.md`) and the #322/#330 fixes.

Classification of each language feature. **Nothing here is "stable" out of
courtesy** — Stable requires frozen semantics (rule 0.2.6-beta) **and** a test
that proves it. Categories: **Stable · Experimental · Implementation-defined ·
Target-specific · Unspecified · Planned**.

---

## 1. Classification by feature

### Syntactic core
| Feature | Status | Test evidence |
|---|---|---|
| Lexer (tokens, literals, comments) | Stable | `Lexer` exercised by the whole suite |
| Recursive descent parser | Stable | `FunctionSyntaxTest`, `Parser` via E2E |
| Optional semicolon | Stable | probes + suite |
| Keywords (list) | Stable | `Lexer.java:13-74` |
| `sealed`/`permits` | **Absent** (dead tokens removed from the lexer; `sealed class S {}` → `PARSE010`) | `deadTokensGiveCleanLexerError` (SG-002) |
| `fn`/`fun`/`func` (prefix **and any name position**) | **Stable** (rejected with `PARSE085`, SG-001 resolved 06/09; #330 extended to names) | `FunctionSyntaxTest` (15) |

### Type system
| Feature | Status | Test evidence |
|---|---|---|
| 9 primitives | Stable | `Type.java`, suite |
| `string` as reference | Stable | `BuiltinTypes.java:11` |
| Implicit numeric widening | Stable | probes + `emitWideningIfNeeded` |
| Narrowing only via `as` | Stable | probe SEM021 |
| `bool→numeric` (=1/0) | **Implementation-defined** | probe (representation leaked) |
| Nullability `T?` | Stable | `KofPatternMatchingTest`, probes |
| Narrowing `if (x != null)` | Stable | probes |
| Deref `T?` without narrowing | Stable — deref without narrowing → `SEM049` | probe (SG-005) |
| Subtyping by inheritance | Stable — nominal; unrelated class → `SEM021` | probe (SG-009) |
| Generics (erasure) | Stable | `KofMapSetTest`, `PackagesE2ETest` |
| Variance (`? extends`) | **Absent** — wildcard → `PARSE086` | probe (SG-007) |
| Type-var bounds | **Planned/absent** | none |
| Ctor type-args inference | **Absent** | none |
| Elem check in `list.add`/`set`/`map.put` | Stable — wrong element type → `SEM056` | probe |
| `==` by type (content/identity) | Stable | probes + `CoreRegressionE2ETest` |
| Constructor overload (arity) | Stable | `SymbolTable.java:47` |
| Method overload (arity/types, same class) | Stable (since 13/09, §131) | `MethodCallTyper` (SG-011) |
| Default parameters | Stable | `lowerFunctionDefaults` |

### Functions and closures
| Feature | Status | Test evidence |
|---|---|---|
| 3 return forms | Stable | `FunctionSyntaxTest` |
| Expression body (`= expr`) | Stable | `FunctionSyntaxTest` |
| `main` (forms) | Stable | probes + `JvmE2ETest` |
| Direct recursion | Stable | probe `fact(5)` |
| TCO | **Absent** (not guaranteed) | none |
| Generic function | Stable | probe `idf<Int>` |
| Lambda (forms) | Stable | `LambdaE2ETest` |
| Snapshot capture | Stable | probe |
| Mutable capture (Box) | Stable | probe `n=2` |
| First-class function types | Stable | `KofHigherOrderTest` |
| Lambda param inference | Stable with **context** (`List` `map`/`filter`/`reduce`); without context → `SEM001` | `LambdaE2ETest`, probe (SG-012) |
| Nested function | Stable (hoisted to `outer__inner`) | `JvmE2ETest.execNestedFunction` (SG-011) |
| Trailing lambda | Stable | `LambdaE2ETest` |

### Classes and data types
| Feature | Status | Test evidence |
|---|---|---|
| Mutable class + constructor | Stable | `ClassFileE2ETest` |
| `class X(...)` = record | Stable | `AGENTS.md`, probes |
| Record (equals/hashCode/toString) | Stable | `KofPatternMatchingTest` |
| Enum (constants only, value=String) | Stable | `KofEnumTest`, `KofEnumSwitchTest` |
| Interface (default methods) | Stable | probe |
| Interface coverage (concrete class must implement) | Stable | `ImplementationChecker.checkInterfaceImplementation` `SEM043` (SG-015); `abstract` may defer, obligation transitive via abstract supers (#322) |
| Inheritance + virtual override | Stable | probes |
| `private`/`protected` at compile-time — **methods + fields**; `final` write | Stable | `SEM046`/`SEM065` (SG-013) |
| `private`/`protected` at compile-time — **fields** | **Unspecified** (runtime only) | probe (SG-013) |
| `abstract` non-instantiable | Stable | `SEM041` (SG-017) |
| Pattern matching (binding+destructuring) | Stable | `KofPatternMatchingTest` |
| Pattern with guard/nested | **Absent** | none (SG-014) |
| Entity (ORM) | **Experimental** | `KofOrmE2ETest` |
| Nested classes | **Absent** (parse error `SEM042`) | `nestedClassGivesCleanDiagnostic` (SG-016) |
| Operator overload | **Absent** | none |

### Control flow
| Feature | Status | Test evidence |
|---|---|---|
| if/else (stmt + expr) | Stable | suite |
| while/do-while/for/for-in | Stable | suite |
| switch statement (no fallthrough) | Stable | `KofEnumSwitchTest` |
| switch expression (SYN001) | Stable | `KofSwitchExprE2ETest` 23/23 |
| break/continue (no label) | Stable | probes |
| Labeled break | **Absent** | probe (SG-002) |

### Modules and names
| Feature | Status | Test evidence |
|---|---|---|
| package | Stable | `PackagesE2ETest` |
| import (class) | Stable | `PackagesE2ETest` |
| import wildcard (brings decls) | Stable | `CompilerImports` |
| import wildcard (qualifies name) | **Absent** (does not qualify) | `:57` |
| qualifyDeep (type-args) | Stable | `PackagesE2ETest` (bug 32) |
| Ambiguous import (does not guess) | Stable | `CompilerTypes:102` |
| PKG002 (1 main) | Stable | probe |
| JVM interop (Java types) | **Target-specific** | `AndroidInteropE2ETest` |
| C FFI (`extern "<lib>"`) | **Partial** — JVM any scalar signature, free arity, `void`/`String` returns (18/09, `.18`); non-scalar = `FFI001` (**except the JVM 3.8b slice: `record` by value arg/return + scalar `T[]`→`ptr` ✅ 20–21/09**, `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5); JS host runner = SAME scalar ABI (3.6.F2/F3 ✅ 18/09, `FfiE2ETest` 16/16 JVM↔JS byte-for-byte), non-scalar = `FFI002`, browser = runtime R7; **Native = scalar ABI DIRECT on x86-64/riscv64/aarch64** (#431 slices 1–2 ✅ 20/09, §369: link-by-use + `call sym@PLT`, no `dlopen`; `FfiNativeE2ETest` 16/16 + `FfiNativeCrossE2ETest` 6/6 qemu), non-scalar/callback/missing `library()` = `FFI001` (R6, never silent); numeric args are converted to the slot by the ordinary rule (§370/#549 ✅ 20/09, `FfiExternTypeConversionTest` 11/11 JVM/Native/JS-host) | measured 18/09 (`modules.md` §6; fmod/ldexp/strncmp/puts/getenv verbatims in `syntax.md`) (`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`, #431) |

### Concurrency
| Feature | Status | Test evidence |
|---|---|---|
| spawn (statement) | Stable | `KofConcurrency2Test` |
| spawn (expression → Handle) | Stable | probe |
| await | Stable | `KofAwaitTest` |
| awaitTimeout | Stable | probe |
| Channel | Stable | `KofConcurrency2Test` |
| Memory model | Defined — SC + 6 happens-before rules | `concurrency-memory-model.md` (SG-020) |
| `spawn { lambda }` with handle | **Bug #29** | `known-bugs.md` |

### Exceptions
| Feature | Status | Test evidence |
|---|---|---|
| throw String | Stable | `ExceptionsE2ETest` |
| try/catch/finally | Stable | `ExceptionsE2ETest` |
| `throws` clause names type-checked (`SEM045`) | Stable (since 09/09, SG-019) | `throwsUnknownTypeGivesCleanDiagnostic` |
| Per-target representation | **Target-specific** | `ExceptionsE2ETest` |

### Stdlib (`kof.*`)
| Feature | Status | Test evidence |
|---|---|---|
| json | Stable (3 targets) | `JsonCompleteE2ETest` |
| collections (List/Map/Set) | Stable | `KofMapSetTest` |
| string methods | Stable | `StringMethodRegistry` |
| http / web / db / orm / cache / mq / time / scheduler / log / config / security / validation / observability / ui / media / process | **Experimental** | E2E per area |
| `rng` (builtin namespace: `seed`/`int`/`boolean`/`double`/`string`) | **Experimental** — JVM+JS+NATIVE x86_64+ANDROID (ANDROID reuses the JVM runtime, issue #777); cross riscv64/aarch64 gap `RNG001` (honest, R6, X8 slice 3); same seed ⇒ same sequence every backend | `KofRngTest` (12): `deterministicJvm/JsMatchesOracle`, `jvmJsParity`, `reseedRestartsSequence{Jvm,Js}`, `contractJvm/Js`, `jvmNativeParityFullFace`, `nativeMatchesOracle`, `contractNative`, `crossStaysHonestGap`, `androidMatchesOracle` |
| Map/Set with class type-arg | **Bug #33** | `known-bugs.md` |

---

## 2. Conformance — what prevents a rigorous definition today

A conformance definition (accept valid, reject invalid, preserve
meaning) **cannot be rigorous** as long as the following exist:

1. **Unspecified rules** still listed above (e.g. `private`/`protected` on
   **fields**, `Map`/`Set` order) — the SG-00x queue is otherwise resolved or
   applied (subtyping, null-deref, memory model, nested classes, top-level
   overloading, `main` exit code, …).
2. **Implementation-defined rules** that leak into observable behavior
   (`bool→int`=1/0, non-immutable `val`, evaluation order of `x++` in an
   expression, slot layout).
3. **Target-specific divergences** not formalized (JS short-circuit,
   exception representation, GC, extreme FP, Map order).
4. **Open bugs** that make the real behavior diverge from what is expected
   (#29 spawn-handle, #33 Map/Set emit).
5. **Absence of a "valid program" oracle**: without the *normative* formal
   grammar (the one here is *extractive*), there is no way to say whether a program that the
   parser accepts *should* be accepted.

**Path to conformance** (recommendation, not implemented):
- Close the SG-00x (decide each Unspecified).
- Freeze the EBNF grammar as normative (not just extractive).
- Extract the E2E tests per target into a *conformance suite* with expected
  outputs per rule (not per file).
- Define a minimal conformance profile (stable core) vs experimental.

---

## 3. Count summary

- **Stable**: core (syntax, primitive types, widening, basic nullability,
  generics erasure, `==`, functions, closures, classes/records/enums/interfaces,
  control flow, packages/imports, basic concurrency, exceptions, json,
  collections).
- **Experimental**: domain stdlib (http/web/db/orm/ui/media/…).
- **Implementation-defined**: `bool→numeric`, `val`, frame layout,
  spawn mechanism.
- **Target-specific**: GC, extreme FP, exception (representation), JS
  short-circuit, interop (JVM only), Map order.
- **Unspecified**: 1 point — `private`/`protected` on **fields** (SG-013); the
  rest of SG-002–SG-020 is resolved/applied.
- **Planned/Absent**: type-var bounds, operator overloading, labeled
  break, `~`, ternary, range, macros, traits, type alias.
