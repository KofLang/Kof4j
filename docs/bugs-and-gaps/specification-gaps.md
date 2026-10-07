[English](specification-gaps.md) | [Português](specification-gaps.pt_BR.md)

# Specification Gaps and Divergences

> **CONSOLIDATED — moved from `docs/development/` to `docs/language-reference/` (09/09) and to `docs/bugs-and-gaps/` (13/09, maintainer's clarity refactor) on
> 12/09** (3-state rule): the 23 entries SG-001–020 + E1–E3 are all
> resolved (APPLIED 06–12/09, most by explicit maintainer decision —
> 2nd round queue COMPLETE, see §Summary). The doc becomes the **spec reference**
> (what each SG requires and where it is locked); new spec gaps enter here with
> their own status. Open bugs stay in `docs/bugs-and-gaps/known-bugs.md`.

**Version:** 0.5.0-beta (pom `revision`; was 0.3.0-beta in the audit) · **Date:** 06/09/2026 · **Source:** complete audit of
`kof-compiler` + execution probes + review of `docs/`, `training/`, `AGENTS.md`

This is the report of inconsistencies found in the audit. Each item
distinguishes **what the code does**, **what the documentation says** and **what the
tests prove**. **No item here was "fixed" in the language** — they are
future recommendations (rule 14 of the task: do not change behavior).

---

## Category A — Documentation contradicts code

### SG-001 — `fun`/`fn`/`func` existed in the compiler but the corpus says they don't ✅ RESOLVED (06/09)

- **Implementation (before)**: `fn` was an accepted optional prefix. `fun`/`func`
  compiled because the parser read the word as a *return type* and the function
  name came after (`fun main()` → function `main`, implicit `void` return).
  `JsonE2ETest.java:223` used `fun main()` and passed.
- **Documentation**: `AGENTS.md` ("there is no `fun` nor `func`"),
  `training/anti-patterns/fake-idioms.md` (lists `fun`/`func`/`let` as fake).
- **Problem (before)**: the "does not exist" rule was false for the real compiler —
  an agent that wrote `fun` received no error.
- **Resolution (06/09, 2nd step)**: `fun`/`fn`/`func` became **reserved
  words** in the lexer (tokens `FUN`/`FN`/`FUNC`, same mechanism as
  `sealed`/`permits`) — they **do not exist** in Kof in any position: neither as
  a declaration keyword, nor as a function, variable, parameter or
  field name. In declaration position the parser gives `PARSE085` (clear diagnostic —
  R6); in any name position (function, variable, parameter, method, field,
  class, record, enum) `ParseContext.expectId` emits the same `PARSE085`
  (measured 17/09, #330; previously the generic `PARSE037` variable /
  `PARSE023` parameter). Aligned with the corpus (rule 4). KofScript
  (`.ks`) keeps `fn` as its own syntax and translates at the boundary
  KofScript (`.ks`) is **not** an exception: it is pure Kof (without `fn`/`let`/`async`).
  Tests: `FunctionSyntaxTest` (15: fun/fn/func
  rejected as a prefix, `fn calc(): Int` rejected, `fn()`/`var fun`/
  `param fn` rejected, class member, idiomatic `Int calc():Int`; #330 added
  `var fun`/`var fn`/`var func` as name → `PARSE085`).
  `let`/`const`/`async` do not exist in `.kf` **and** `.ks` (KofScript is not
  JavaScript — sugar removed 06/09).

### SG-002 — Tokens and keywords the grammar does not use

- **APPLIED (option 1 of the recommendation — tokens REMOVED from the lexer; proven
  12/09):** `~`, `::`, `...`, `=>`, `|>`, isolated `_` and the keywords
  `sealed`/`permits` no longer exist as tokens — grep 0 in
  `TokenType.java`/`Token.java`/`parser/Lexer.java` (the list above this
  paragraph described the PRE-fix state). `~5` is now **LEX005** ("Unexpected
  character", `Lexer.java:467`); `a => b`/`xs |> f`/`A::b` fall into parse with
  `PARSE041`; `sealed class S {}` sees `sealed` as a common IDENTIFIER →
  `PARSE010` (declaration without type). No "reserved feature" diagnostic:
  the grammar simply never used them, and now the lexer doesn't either.
- **Proof:** `CompilerDriverTest.deadTokensGiveCleanLexerError` (5 cases with
  exact expected code, 1/1 green 12/09) — the test that LOCKS the removal
  (regression of any dead token that resurfaces).

### SG-003 — Marketing terms vs technical definition

- **APPLIED (09/09, maintainer decision — "review and apply" with the
  new checks):** with SEM041–SEM046 applied, the compilation guarantees
  cover abstract instantiation, nested type, interface coverage,
  main signature, throw-clause and visibility — which the README/overview
  can state as concrete properties. `docs/language-reference/
  type-system.md` §1 updated with the 09/09 note and §13 with the 6 new
  codes in the SEM0xx table. "Strongly typed" remains OUTSIDE the official
  vocabulary (vague by definition) — what counts is the list of checks, now
  complete and tested.
- **Documentation (historical)**: `README.md:60` "Kof is a
  **strongly typed and statically typed** language"; `docs/architecture/architecture.md`
  "strongly typed".
- **Implementation (historical)**: the type checker does **not** guarantee subtyping
  (§SG-009), does **not** check collection element, does **not** enforce
  `private`/`abstract` at compile-time, does **not** prevent reassignment of `val`.
- **Problem**: "strongly typed" is vague and, read as "the compiler prevents
  badly typed operations", is **false** for Kof today.
- **Recommendation**: replace it with concrete properties (already done in
  [language-reference/type-system.md](../language-reference/type-system.md)).
  Keep "statically typed" (true: types resolved at compile-time).

---

## Category B — Unspecified behavior (Unspecified)

### SG-004 — (resolved in the audit) `bool→numeric`

- **Implementation**: `bool` is stored as `int` 1/0; `var i: Int = true`
  → `1` (*probe*). `isAssignable` accepts it via `primitiveWidth(bool)=0`.
- **Problem**: the coercion works by **representation accident**, not by
  rule. There is no dedicated test.
- **Recommendation**: decide whether it is a language rule (document + test) or
  should be rejected (SEM002 already catches arithmetic, but not assignment).

### SG-005 — Deref of `T?` without narrowing is not an error ✅ FIXED 10/09 (SEM049)

- **Previous implementation**: `var s: String? = "x"; s.length` **compiles and runs**.
  The lowering unwraps the receiver (`ExpressionTyper.java:143`). Null-safety was
  **advisory**: the compiler does not prevent NPE.
- **Fix (10/09, breaking — rule 6 suspended, maintainer decision on
  SG-005/008 "the name itself says it")**: deref of `T?` without narrowing → error
  **SEM049** ("receiver is nullable (T?); narrow first").
  1. **Method call** (`SemMethodCallTyper`, right after inferring `recv`): receiver
     `NullableType` → SEM049, before the collections/process/channel branches.
  2. **Field/property** (`SemExpressionTyper` case `FieldAccessExpr`): same —
     `s.length` on `String?` was the hole (`Type.isString` unwraps Nullable).
  3. **Extended narrowing** (`StatementAnalyzer.collectNarrowing`): beyond
     `if (x != null)` → THEN (which already existed), now `if (x == null)` → **ELSE**,
     and the conjunction `x != null && Y` narrows the whole THEN. Disjunction (`||`) does NOT
     narrow (the branch runs if ONE holds) — honest.
  4. **Intra-expression narrowing** (`SemNarrowing.narrowedScope`, called from
     `SemExpressionTyper`; the 17/09 REFACTOR-500 moved it out): in
     `if (s != null && s.length > 0)`, the RIGHT side of `&&` sees `s` narrowed
     (short-circuit: the side is only evaluated if the left one passed) — without this
     the condition ITSELF would give SEM049 on `s.length`.
- **Arithmetic on `T?`** (`a + 1` with `a: Int?`) **stays green** — it is not
  deref; the bug 87 guard-unbox covers it.
- **Migrated tests**: `KofMapSetTest.memberCallOnNullableInferredFromMapJVM`
  (direct deref → narrowing `if (v != null)`).
- **Proofs**: `CompilerDriverTest.nullableDerefWithoutNarrowingFails` /
  `nullableDerefPropertyWithoutNarrowingFails` (SEM049) +
  `nullableNarrowedIfStaysGreen` / `nullableNarrowedAndStaysGreen` /
  `nullableNarrowedElseStaysGreen` (246/246). Compiler suite 1263 run /
  0 code failures (15 environmental errors: node/javac/javap).

### SG-006 — `&&`/`||` short-circuit disabled in JS — ✅ FIXED 09/09 (parity OK + test)

- **Implementation**: `ExpressionBinaryLowerer.java:56-57` — the short-circuit by
  labels is emitted only when `target != JS`. In JS, both sides are
  evaluated.
- **Problem**: `if (x != null && x.length > 0)` can NPE in JS but not in
  JVM/Native. **Parity divergence** (freezing rule 5).
- **Recommendation**: document as Target-specific (done in
  [expressions.md](../language-reference/expressions.md) §5) **and** open a parity
  gap to fix JS.
- **NOTE 09/09 (code analysis):** the lowering by labels is `target != JS`,
  but for bool `&&`/`||` JS emits the native operators (`a && b`,
  `a || b` — `JsCallEmitter.binaryExpr` lines 274-277), which **already
  short-circuit** natively. So `x != null && x.length > 0` should NOT NPE in
  JS (`x.length > 0` is not evaluated if `x != null` is false). Parity plausible
  by code reading, but **without a runtime test that locks it** — a
  case in `BackendParityTest` (`if (x != null && x.length > 0)`) on the 4 targets
  is recommended before closing the gap.
- **✅ FIXED 09/09:** `BackendParityTest.parityShortCircuitAndOr` added
  (`String? s = null` → `vazio` via short-circuit; `String? t = "abc"` →
  `nao-vazio`). Suite green (the only failure in the 1163+25+5+109 suite is bug 46,
  pre-existing) → the `&&` short-circuit in JS/JVM is locked by test.

### SG-007 — Generic wildcard (`? extends T`) compiles but breaks — ✅ FIXED 06/09 (PARSE086)

- **Implementation (before)**: `List<? extends Int>` is parsed (the `?` becomes a
  nullable suffix, `extends Int` enters the name) and **runs with
  `NoClassDefFoundError: ?extendsInt`** (*probe*).
- **Fix (06/09, bug-fix lane)**: `TypeParser.parseTypeRef` rejects the `?` wildcard inside `<>` with `PARSE086` ("Wildcard types '? extends/super' are not supported; use concrete type or nullable 'T?'"). `List<String?>` (nullable) remains valid. Proof: `TestRepro2` wildcard → `PARSE086`, `TestWild` `String?` → ok.
- **Problem (before)**: syntax accepted without meaning — worse than a clear error (violates R6
  "never silent").

### SG-008 — Null safety: `T?` never NPEs and the `null` literal is not fabricable ✅ FIXED 10/09 (bug 87 + SEM048)

- **Previous implementation**: `Int? a = null; a == null` → **NPE at runtime**
  (*probe*: unboxing the null `Integer` throws). `String? s = null; s == null` →
  `true` correctly. Inconsistency between primitive and reference nullable.
- **Maintainer decision (09/09)**: "the name itself says it" — **NO `null` literal
  is assignable** (not even to `T?`): `Int? x = null` → error; `x = null` → error.
  `null` only reaches `T?` via **API** (e.g. `mapOf("k", v).get("missing")`), and
  `T? == null` is a **reference** comparison (never NPE by unbox).
- **Implementation (10/09):**
  1. **SEM048** — `StatementAnalyzer` rejects the `null` literal in `VarDeclStmt`
     (`T? x = null`) and in assignment (`x = null`); the correct idiom is to get `null`
     from an API. Proof: `CompilerDriverTest.nullInVarDeclFails` /
     `nullInAssignmentFails` / `nullFromApiStaysGreen` (241/241).
  2. **`Map.get()` returns `V?` for EVERY `V`** — `CollectionCallLowerer`,
     `CollectionMethodTyper`, `SemMethodCallTyper`, `MemberCallTyper` stop
     returning `V` for a primitive and now always return `NullableType(valueType)`
     (absence = comparable null, never exception/unbox). The `put()` on an empty
     `mapOf()` pins the `K,V` types in the local symbol (`SymbolTable.
     updateLocalType`) so the semantic cache does not diverge from the emit.
  3. **Comparison `T? == x` without NPE** — `CompilerComparisons` unwraps
     `NullableType` in the operand type; when one side is `Unknown`/`Nullable(Unknown)`
     (get of `mapOf()` without pin) against a primitive, the comparison becomes a reference
     comparison (boxed primitive, `Objects.equals`) mirroring the interpreter, instead of
     `if_icmp*` on null → VerifyError. `ExpressionBinaryLowerer` boxes the
     primitive side in the correct order. The interpreter gains `eqAllowsNull` /
     unbox with guard (`KofInterpreterCollections`/`KofInterpreterOps`/
     `KofInterpreterValues`).
- **KofScript** migrated to the same idiom (does not fabricate null, uses `mapOf().get()`).
- **Note (rule 6)**: the program `m.get(k) == 1` keeps compiling — the `1` is
  boxed and compared by `if_acmpeq` (cross-target parity). Parity proofs in
  `BackendParityTest`/`ConformanceMatrixTest`/`KofScriptTest`.
- **Recorded in `known-bugs.md` §87.**

### SG-009 — Subtyping is not checked by the type checker — ✅ FIXED 10/09 (nominal SEM021)

- **Previous implementation**: `isAssignable` returned `true` for **any**
  `ClassType→ClassType` pair (`TypeChecker.isAssignable`). The safety came
  from the lowering/runtime `checkcast`.
- **Problem**: `A a = <object of an unrelated class>` passed the type
  check; it failed only at runtime. `implements` without covering methods compiles
  (SG-015 — already fixed). Abstract can be instantiated (SG-017 — already
  fixed).
- **FIXED 10/09 (nominal subtyping in `isAssignable`):** new overload
  `TypeChecker.isAssignable(sa, from, to)` — for reference→reference of
  domain classes, it walks `superClass`/`interfaces` via BFS (same pattern
  as `MemberResolver.resolveInHierarchy`); unrelated → compile-time error
  **SEM021** (typed var-decl; assignment/return keep the already
  existing SEM012/SEM010). Conservative (true) when the hierarchy is unknown — external
  type (Android/JDK imports), builtin (String/List/Map, relations of
  BuiltinTypes) or class not declared in the module — restricting this
  would break legitimate interop (rule 6: never break what works).
  **Legitimate subtypes stay green**: `Dog extends Animal` → `Animal a =
  Dog()` compiles (superclass BFS); `Cat implements Speaker` → `Speaker s =
  Cat()` compiles (interfaces BFS); root `Object` accepts any reference.
  **Migrated call sites**: `SemExpressionTyper:225` (assignment-expr),
  `StatementAnalyzer:48` (assignment-stmt), `:147` (typed var-decl),
  `:164` (return). **Proofs:** 4 new tests in `CompilerDriverTest`
  (`unrelatedClassAssignmentFails` = SEM021 on the repro `Cat c = Dog()`;
  `subclassAssignmentStaysGreen`; `interfaceAssignmentStaysGreen`;
  `externalTypeAssignmentStaysConservative`) — CompilerDriverTest 250/250;
  compiler suite **1270 run / 0 code failures** (16 environmental errors =
  missing node/javac/javap); zero false positives in the corpus (all
  existing legitimate programs keep compiling).

### SG-010 — `val` does not prevent reassignment — ✅ FIXED 09/09 (SEM037)

- **Implementation**: `val x = 1; x = 2` **compiles and runs** (prints 2, *probe*
  confirmed in isolation). There is no immutability flag in `VarDeclStmt`
  (only `type`/`name`/`initializer` — `VarDeclStmt.java:4`).
- **Documentation**: `AGENTS.md` "val y = 20 // immutable".
- **Problem**: `val` is decorative. The `val`/`var` distinction has no
  observable effect.
- **Recommendation**: either implement rejection of assignment to `val` (new SEM), or
  document that `val` is convention (not guaranteed). Design decision.
- **FIXED 09/09 (DD-02, bug 62a):** `val` is now immutable — reassigning
  (incl. compound `+=`) emits **SEM037** ("cannot assign to immutable 'val'"). The
  parser carries `type="val"` (before always "var"); `LocalVariableSymbol` gained
  `isVal`; `analyzeAssignmentStatement` checks. See `docs/decisions/planning-mutability.md`.

### SG-011 — Nested function and top-level overloading

- **APPLIED (09/09, maintainer decision, SEM048-lane spec-gaps):** nested
  function works — parser captures `Type name(params) { ... }` in a statement
  (`lookaheadNestedFunction`, checked BEFORE the typed-var-decl) and the desugar does
  hoisting to top-level `outer__inner` inserted BEFORE the outer ("inner
  first"); calls `inner(...)` rewritten to `outer__inner(...)`.
  Semantics: inner defined before the body executes; outer calls and awaits the
  return. Proof: `JvmE2ETest.execNestedFunction` (42) +
  `execNestedFunctionWithCondition`.
- **APPLIED (11/09, part B — top-level overloading, JVM oracle):** homonymous
  functions with DIFFERENT SIGNATURES coexist and the call site resolves the most
  specific applicable candidate (`TopLevelOverload.pick` — exact
  equality > subtyping; the JVM is the oracle). What remains an ERROR: EXACT
  duplicate signature (SEM047) and return-only collision (return is not a
  signature, as in the JVM); ambiguous call between applicable candidates →
  SEM057 with a cast hint (R6: never a silent choice). Parity by
  construction: the selection happens in the frontend and each backend references the
  candidate by signature — JVM = `invokestatic` descriptor (it already carried the
  chosen `argTypes`), Native = symbol suffixed by signature tag
  (`Default_Main_g_I` vs `_I_I`; x86/riscv, aarch translates; default-arg
  wrappers carry their own suffix — de-duplicates latent collision in `as`),
  JS = suffixed name when there are ≥2 signatures under the name (async key per
  signature), interpreter = `findKofMethod` matches `KofCall.parameterTypes`
  with name+arity fallback. A program with a single candidate per name is
  byte-identical to before on all targets (non-regression invariant).
  Proof: `TopLevelOverloadE2ETest` (identical output on the 6: JVM/Script/JS/x86/
  riscv64/aarch64 under qemu — `5 11 abab 42` + defaults `7 11`),
  `CompilerDriverTest` (distinct signatures compile; duplicate and
  return-only SEM047).
- **Implementation (historical)**: a function inside a function was not parsed as a
  declaration (SG-011); two homonymous top-level functions collide without a
  clear diagnostic (the `define` overwrites).
- **Related (METHOD, not top-level):** method overloading by ARITY in the
  same class was **bug 131 of `known-bugs.md` — ✅ FIXED 13/09** (DECIDED
  13/09 option 10a: implement; `18a64d45`, 4 backends, `MethodCallTyper` picks
  by arity+compatibility; class methods of the same name with different
  signatures coexist). This SG-011 covers top-level FUNCTION overloading (✅);
  §131 covers the class-METHOD face (also ✅ since 13/09).

### SG-012 — Lambda parameter type inference

- **APPLIED (09/09, maintainer decision):** contextual inference — lambda
  param without annotation in `map`/`filter`/`reduce` of `List<T>` inherits the
  ELEMENT type (`MemberCallTyper.contextualLambda` rewrites the param in the AST;
  SSE pattern already used for KofWeb). `nums.map((x) -> x * 2)` compiles without
  `(x: Int)`. Arithmetic on an untyped param WITHOUT context remains SEM001
  (never silent Object). Proof: `lambdaParamInferredFromListContext` +
  regression `untypedLambdaParamArithmeticIsDiagnosedNotEmitted`.

### SG-013 — `private`/`protected` unchecked at compile-time — ✅ FIXED (09/09 methods; 17/09 fields)

- **APPLIED (09/09, maintainer decision, SEM046):** the root cause was
  `defineMethodSymbol` with accessFlags=1 (PUBLIC) hardcoded — modifiers
  discarded. Now the symbol carries real PRIVATE/PROTECTED and
  `MemberCallTyper.checkMemberAccess` rejects: private outside the declaring
  class, protected outside the hierarchy (transitive), both from a top-level
  context. Proof: 4 `CompilerDriverTest` tests (private/protected,
  inside/outside).
- **EXTENDED to FIELDS (17/09, #331/#327, compiler lane):** the same contract
  now covers field access — `private` field only in the declarer, `protected`
  in the declarer/subclasses; `this.x`/bare `x` in the declaring class passes.
  Root: `FieldSymbol` lost the modifiers in `SymbolTableBuilder`; now
  `MemberCallTyper.checkFieldAccess` rejects (`SEM046`). `final` field writes
  outside the declaring constructor are rejected with `SEM065` (JVMS 4.4 —
  before: silent `IllegalAccessError`). Proof: `FieldAccessControlTest` 7/7.

### SG-014 — Pattern matching without guards/nesting

- **APPLIED (09/09, maintainer decision, guards part):** `case T v if
  (cond):` / `case T(a,b) if (cond) ->` — PatternExpr gains the `guard` field
  (old ctors preserved), the parser consumes `if` + expression, SEM analyzes
  the guard with the bound var, lowering emits in the 2 switches (statement: guard in the
  test with a temporary cast; expression: post-binding guard). False → next
  case/arm. Proof: `switchCaseGuardFalseFallsThrough` +
  `switchCaseGuardTrueRunsGuardedArm`. Nesting (`case T(Inner(a,b))`)
  remains planned (part B of the gap).
- **Implementation (historical)**: only `case Type var` and `case Type(a,b)`
  (top-level).

### SG-015 — `implements` does not require covering abstract methods

- **APPLIED (09/09, maintainer decision, SEM043):** `checkInterfaceImplementation`
  at the end of `analyzeClass` — missing method → SEM043 naming the method;
  divergent arity → SEM043 with expected/found (exact type parity
  awaits virtual dispatch). Proof: 3 `CompilerDriverTest` tests
  (missing/wrongArity/complete-green).
- **Refined 17/09 (#322, `ebf59ca4`):** an `abstract class` may DEFER the
  interface methods (`abstract class A implements I {}` compiles, JLS 8.4.8.1);
  the obligation is TRANSITIVE — an abstract super charges the concrete subclass,
  so `class C extends A {}` without `f()` fails with `SEM043` naming the class +
  method + "inherited via" (no silent `AbstractMethodError`). Proof:
  `AbstractClassPartialInterfaceE2ETest` 3/3.

### SG-016 — Semantics of nested classes

- **APPLIED (09/09, maintainer decision, SEM042):** nested type does not
  exist in Kof — `class A { class B {} }` is an immediate parse error SEM042
  ("declare at top level") in `ClassMemberParser.parseClassMember`;
  nested interface/record/entity likewise (same branch). Proof:
  `nestedClassGivesCleanDiagnostic` + `topLevelClassStaysGreen`.

### SG-017 — `abstract class` instantiable at compile-time

- **APPLIED (09/09, maintainer decision, SEM041):** `new A()` and `A()`
  (implicit construction) of an abstract class → compile-time error SEM041.
  `abstractClasses` record in `SymbolTableBuilder.preDeclareType`; check
  on the 2 instantiation paths (SemExpressionTyper NewExpr +
  BuiltinCallTyper receiver-null — `Shape()` is MethodCallExpr, not NewExpr).
  Proof: `abstractClassInstantiationFails` +
  `abstractClassSubclassInstantiationStaysGreen`.

### SG-018 — Exit code of `Int main()`

- **APPLIED (09/09, maintainer decision, SEM044):** the `Int main()`
  form was REMOVED — the entry point is ONLY `main()` (no return type, no
  modifiers); `Int main()` → error SEM044. Modifiers on main do not even reach SEM
  (the parser always passes empty mods for a top-level function; SEM044 protects the
  contract in the semantic layer). The IR already emits public static void. Proof:
  3 `CompilerDriverTest` tests (typedMain/modifiedMain/plainMain-green).

### SG-019 — `throws` clause is decorative

- **APPLIED (09/09, maintainer decision, SEM045):** discovery — a top-level
  function did NOT EVEN CAPTURE `throw` (only `parseClassMember` called `parseThrows`;
  the gap said "decorative", in fact it was doubly dead). Fix:
  `Parser.parseFunctionDeclaration` captures + `SemanticAnalyzer.checkThrowsClause`
  validates that each name is a known type (class/interface of the module, builtin,
  or external via import) → SEM045. Proof: `throwsUnknownTypeGivesCleanDiagnostic`
  + `throwsKnownTypeStaysGreen`.

### SG-020 — Concurrent memory model missing — ✅ FIXED 09/09 (spec) / validated 10/09

- **Previous implementation**: `spawn`/`await`/`Channel` worked, but there
  was no definition of happens-before/visibility/atomicity.
- **FIXED 09/09:** complete spec in
  `docs/language-reference/concurrency-memory-model.md` — SC on all targets,
  6 happens-before rules (spawn/await/channel/cancel/locals/race),
  per-target mapping (JMM virtual threads / x86-TSO futex / riscv-aarch
  fence), non-goals (no volatile/synchronized on the surface — Channel is the
  abstraction), DoD with proofs. Interpreter: concurrent statics map
  (HB per field).
- **Validated 10/09 (doc↔code sweep):** the doc's §4 proofs — (1)(2)(5)
  covered by `SpawnE2ETest`/`KofConcurrency2Test` (spawn/await/channel
  cross-target); (3) `staticsAreSequentiallyConsistent` (1998000) and
  (4) `noWordTearingOnLong` (reader never sees an invalid value) **already
  implemented** in `KofConcurrency2Test:699/:737` (the doc §4 said
  "(3)(4) to implement" — outdated; fixed in the doc). Gate:
  `KofConcurrency2Test` 29/0/1-skip (qemu) in the 1270/0-code suite.

### SG-021 — `json.encode` without an indented form (pretty-print) — REQUESTED, no decision

- **Origin:** Issue #126 (the reporter was looking for a `JSON.stringify`-style 3rd
  indentation argument; the check accepted `json.encode(x, 4)` — an arity gap
  fixed 13/09 in `MemberCallNamespaces` with SEM025).
- **Spec state:** `json.encode(x)` is the flat contract (v1) on the 4 targets;
  there is no indented form and it was NEVER promised.
- **What is requested:** a `json.encode(x, n)`/`json.encodePretty(x)` face with
  deterministic indentation (golden per target). Design decision (rule 6):
  it is up to the maintainer; until then the wrong arity is SEM025 with a hint of the
  correct form — never a silent fallback (R6).

### SG-022 — value records / first-class value types (no observable identity) — REQUESTED, no decision

- **Origin:** Issue #275 (feature request, 16/09). Proposal: a `value record
  Vec2(Float x, Float y)` form with value semantics and **no observable object
  identity**, letting each backend choose the physical representation (local,
  ABI register/stack, inline field, flattened array, boxed on demand). The
  reporter explicitly does **not** want a "stack allocation" syntax nor a
  guaranteed storage strategy — only the semantic property (no identity).
- **Spec state:** `record` today is an immutable data aggregate that still has
  reference identity (`==` is content `==`, `getClass()` is the record class,
  it can be boxed and stored in collections). There is **no** syntax to declare
  identity-free value semantics; the corpus never promised one.
- **Why it is not a lane edit:** it is **new syntax + a new semantic contract**
  (identity observability) → rule 6 (frozen contract). It is the maintainer's
  design decision, and it interacts with the freeze on `==`, boxing and
  collections. Until decided: no silent optimization of ordinary records
  (escape analysis stays a backend detail, never an observable promise).
- **Cross-target note:** on the JVM a value record could still be a normal class
  (the JVM has no value types until Project Valhalla); the guarantee would be
  "identity not observable", enforceable by the compiler (forbid identity
  operations) — not "no allocation". Native x86/riscv could flatten/embed;
  JS would box. Any implementation must state the honest per-target behavior
  (R6/R7), never promise stack allocation.

### SG-023 — Property-based testing runner + suite fixtures in `kof test` — ✅ DECIDED 21/09 (no new surface: option C + iii)

- **Origin:** tracker **X8** slice 3 remainder (`ecosystem-coverage.md` G6 "next"):
  after `rng` (slices 1–2 ✅ 18/09, `KofRngTest` 11/11) and `kof test` fatia 3
  (`--timeout` ✅ 19/09; **named suites by directory ✅ 21/09**, `CmdTestSuiteTest` 2/2),
  two faces remain and both need a surface.
- **Requested (a) property runner:** run a `test` body over generated inputs from
  `kof.rng` (`forAll`), with a deterministic seed/replay and minimizing on failure.
- **Requested (b) fixtures:** per-suite setup/teardown shared by every file of a
  directory.
- **Spec state:** `rng` exists (seedable xorshift128+splitmix32, JVM/JS/Native-x86)
  and `kof test` runs `test "name" { }` with timeouts + named suites — but there is
  **no** property runner and **no** fixture contract; the corpus never promised one.
- **Candidate surfaces** (Simplicity Law, rule 11 — pick the shortest that states the
  intent): **property** → (A) `property "name" { forAll((gen) -> …) }`, mirroring
  `test "name" { }`; (B) a `kof test --props` mode reinterpreting an existing `test`
  (implicit, heavier); (C) nothing new — document the `rng` + loop idiom and keep the
  runner out of v1. **fixtures** → (i) `setup { }`/`teardown { }` blocks; (ii) a
  per-directory convention file (`_suite.kf`); (iii) nothing new.
- **Why it is not a lane edit:** both add **user-facing language surface** (a keyword
  or a CLI contract) → rule 6; the simplicity gate (rule 11) and `D-KOF-FIRST`
  (the Kof contract precedes any QuickCheck/Hypothesis borrowing) apply.
- **Implementation todo (when decided):** 1. fix the surface (one option each);
  2. parser/typer + runner in `kof-cli`/`kof-script`; 3. deterministic seed + replay;
  4. per-target parity JVM/Native-x86/JS/Android (`RNG001` honest on cross only — Android real since #777);
  5. E2E per target + corpus (`training/`, `learn/23-testing`); 6. complete or
  diagnosed gap — no stub (Q7/R6).
- **Decided (21/09, maintainer-delegated, `D-PROPERTY`):** option **C** + option **iii** —
  **no new syntax**; the property runner is the existing `test "name" { }` +
  `kof.rng` + `assert` idiom, and fixtures are the `close()` + `try/finally` pattern
  (`D5-B`). Proof: `PropertyTestIdiomE2ETest` **7/7** (seeded reuse, `checksum`
  bit-identical JVM==JS and JVM==Native-x86, falsifiable property FAILs with exit 1,
  zero-iteration property PASSes vacuously). Documented in `training/idioms/stdlib.md`
  + `learn/23-testing.md`. No parser/typer/codegen touched.

---

## Category C — Divergences between targets (parity) — updated 10/09

| # | Divergence | JVM | Native | JS | Gap |
|---|---|---|---|---|---|
| SG-C1 | Short-circuit `&&`/`\|\|` | ✅ | ✅ | ✅ FIXED 09/09 | SG-006 ✅ |
| SG-C2 | Exception (representation) | RuntimeException | kof_panic | throw string | Stable effect |
| SG-C3 | GC | JVM | free-list/mark-sweep (x86); bump (riscv) | engine | Target-specific |
| SG-C4 | Extreme FP | IEEE | IEEE (cross ✅ 15/09) | IEEE | FLT001 CLOSED (cross FP→string) |
| SG-C5 | Host type interop | ✅ | ❌ | ❌ | Target-specific |
| SG-C6 | `println(null)` | "null" | ✅ (R6) | "null" | — |
| SG-C7 | Map/Set class type-arg | ✅ FIXED 06/09 (was bug#33 — real cause: inferred nullable) | ✅ | ✅ | — |
| SG-C8 | `spawn{lambda}` handle | ✅ FIXED 06/09 (bug#29) | ✅ | ✅ | — |

---

## Category D — Known bugs (cross-reference) — updated 10/09

Not duplicated here — see [known-bugs.md](known-bugs.md):
- **#29** spawn{lambda}-with-handle — ✅ FIXED 06/09
- **#30** decode<Bool> x86_64 — ✅ FIXED
- **#31** process.<nonexistent> — ✅ FIXED 06/09
- **#32** generic type-arg via import — ✅ FIXED (`qualifyDeep`)
- **#33** "Map/Set with class type-arg" — ✅ FIXED 06/09 (real cause: member call on an **inferred** nullable receiver; the Map/Set emit was never the problem)

---

## Category E — Outdated documentation (docs ≠ code)

### SG-E1 — `docs/architecture/architecture.md` calls riscv64/aarch64 an "x86_64 placeholder" — ✅ FIXED 10/09 (residual)

- **Doc** (`architecture.md:40-46,97-98`): "codegen still x86_64 (placeholder)".
- **Code**: `NativeBackend.emitRiscv` is **real** riscv64 lowering;
  aarch64 via `translateRiscvToAarch64`.
- **FIXED 10/09:** the doc header already had the 06/09 correction note;
  the residuals ("codegen x86_64 placeholder via qemu" in the Target enum
  and the 0.2.6 targets section) were updated to real lowering.
  Verification: grep "placeholder" in `docs/architecture/architecture.md` now only
  appears in the historical correction note (which explains why).

### SG-E2 — `docs/history/language-state.md` dated 02/09, version 0.2.6-beta — ✅ FIXED 10/09

- It counted 810 tests; today there are **1270** (kof-compiler only). Version 0.2.6;
  today 0.3.0.
- **FIXED 10/09:** marked as a **HISTORICAL SNAPSHOT** (note at the top
  pointing to `docs/status.md`, `docs/language-reference/` and
  `specification-gaps.md` as current sources). Regenerating the doc would
  duplicate status.md — an honest snapshot is better than a derived copy that
  rots.

### SG-E3 — `docs/architecture/architecture.md` lists "KofC Backend" as an IR backend — ✅ FIXED (06/09) / verified 10/09

- **Old doc**: showed `KofC Backend` in the pipeline consuming the IR.
- **Code**: `KofCCompiler` does **not** implement `Backend` nor consume
  `IRModule` — it is a separate C-subset compiler (`kof-c-compiler`).
- **Verified 10/09:** the pipeline diagram in `docs/architecture/architecture.md`
  shows the 3 IR backends (JvmRuntime/NativeRuntime/JsBackend) and
  `KofCcompiler` is in its own section, unrelated to the IR;
  `docs/architecture/compiler-architecture.md` "Is / Is not" table already explicitly says
  "KofC **is not** a backend of the Kof IR". Closed with no new code.

---

## Summary

- **23 SG-00x gaps** (A: doc/code contradictions; B: unspecified
  behavior; SG-021 json pretty-print and SG-022 value records = requests with no
  decision; **SG-023 property runner + fixtures ✅ DECIDED 21/09 — no new surface,
  `D-PROPERTY`**). **Maintainer queue (2nd round, 10/09) COMPLETE:**
  SG-008 ✅, SG-005 ✅, SG-009 ✅, SG-020 ✅ — see the history in each section.
- **8 target divergences** (C).
- **3 outdated docs** (E) — **all ✅** (E1 residual 10/09, E2
  snapshot 10/09, E3 verified).
- **5 bugs** (D, already in known-bugs) — 29/30/31/32/33 ✅ fixed.

**State 10/09:** the original audit was about documentation, but the
subsequent queue of maintainer decisions fixed the language with tests
(SEM041–SEM049, SG-009 nominal subtyping, SG-020 memory spec). Every
B/C item that involves a semantics change WITHOUT a maintainer decision follows
rule 6: it becomes a gap/plan in `planning-*`, never a silent edit.
