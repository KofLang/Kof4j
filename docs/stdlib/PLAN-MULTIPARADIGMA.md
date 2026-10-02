[Português](PLAN-MULTIPARADIGMA.pt_BR.md) | [English](PLAN-MULTIPARADIGMA.md)

# PLAN-MULTIPARADIGMA — Multiparadigm, Functional Pipelines and Declarative Queries

**Status:** CONCLUDED 30/09 — promoted 28/09/2026 from `future/` to `docs/development/` (`D-MULTIPARADIGMA-PHASE1A`, `D-FUTURE-PROMOTION`), Phase 1 (slices 1a–1i) + Phase 2 (eager-cost yardstick) landed, **moved to `docs/stdlib/`** (3-state rule). Phases 5–7 remain design-only (§13), gated by R12 + a maintainer decision; the deferred native erasure front is tracked by the native lane (`NAT008`).
**Owner:** issues lane (closed 30/09).
**Decision:** `D-MULTIPARADIGMA-GO` (Tier 2.x) + `D-MULTIPARADIGMA-PHASE1A` (`DECISIONS.md`) — Phase-1a scope locked during implementation (the plan owns it).
**Real state (measured 28/09):** `map`/`filter`/`reduce` exist eager on `List` (all targets); `any`/`all`/`none` do not exist anywhere (zero corpus usage, no keywords); the `kof_list_*` per-target pattern is established; Phase-1a spec (§4 table) fully defines short-circuit + vacuous semantics.
**Slice 1a (landed 28/09):** `any`/`all`/`none` on `List` — eager short-circuit quantifiers reusing the `kof_list_*` pattern, zero typer/lowerer machinery beyond the map/filter path. BOUNDARY (pagination lane's ride, NOT touched): `take`/`drop`/`slice`; later: `find`/`forEach`/`flatMap`/`count(pred)`/`distinct`/rest. Proof: `ListQuantifiersE2ETest` 5/5 (JVM/Script/JS + Native-x86 + cross riscv/aarch64 run) + `KofScriptStdlibParityTest#quantifiersParity`. Vacuous `none` = true (maintainer decision 28/09, `none` ≡ ¬`any`).
**Slice 1b (landed 28/09):** `find` + `count(pred)` — nullable-element surface mirroring `Map.get`-missing per target (Native slots hold RAW primitives but `T?` consumers expect boxed: static box-tag from the lowerer, hit-path boxes via `kof_box_*`, miss stays 0; JVM pops the tag, JS/Script ignore it) + `ListFindCountE2ETest` 3/3 (JVM/Script/JS + Native-x86 + cross riscv/aarch64 run). Pre-existing holes met (not opened): Float relational in lambda bodies = JVM VerifyError for ALL higher-orders incl. untouched `filter`; Int-2 truthiness edge inherited from the `filter` family.
**Slice 1c (landed 29/09):** `forEach` — side-effect iteration without allocation + `ListForEachE2ETest` 3/3 (JVM/Script/JS + Native-x86 + cross run). Naming traps measured twice: kof_ names are snake_case (`kof_list_foreach`, never verbatim camelCase — misses every registry) and JS prelude follows the generic `runtimeJsName` mapping (`kofListForeach`, not `kofListForEach`).
**Slice 1d (landed 29/09):** `flatMap` — each element maps to a List, concatenated in order (x86/cross reuse `kof_list_add_all`; result type = the lambda's `List<R>` itself, non-List is UNKNOWN honest) + `ListFlatMapE2ETest` 3/3 (JVM/Script/JS + Native-x86 + cross riscv/aarch64 run). Same camelCase trap a third time (`flatMap` → explicit `kof_list_flatmap` mapping in the lowerer).
**Slice 1e (landed 29/09):** `distinct` — dedup preserving first-occurrence order + `ListDistinctE2ETest` 3/3. Equality reuses `contains` per target (static tag via `stringTag`, same taxonomy; x86/cross call `kof_list_contains` per candidate). Lesson: an end-anchored asm insertion ate flatMap's epilogue (SIGILL on cross, caught by flatMapCross) — fixed by reconstructing from the known-good commit; verify asm junctions by reading, not by anchor-matching.
**Slice 2 (Phase 2, landed 30/09):** the eager-cost yardstick ships as **Kof benchmark programs** under `benchmarks/pipelines/` (the repo's `kof bench` discovery already covers any `<category>/<name>/Main.kf` + `expected.txt`, so this is the natural form instead of the predicted Java `bench/ListPipelineBenchTest.java`). Coverage matches §3 Phase 2 — map / filter / combined / with-take — plus `distinct` (O(n²) equality) and `sorted` (D-MULTIPARADIGMA-SORTED) as the first eager-cost data points; 6/6 validate byte-for-byte on JVM + JS (`--quick`; measured medians JVM 614–824 ms, JS 101–4451 ms). Native measurement rides the CI `Benchmarks (native)` job (host lacks the cross toolchain). No `Sequence`/lazy code — **numbers only**, per the phase gate. Reference points once baselines exist: JS `sorted`/`distinct` are quadratic (≈4.1 s/4.5 s at n=4000) vs the eager O(n) ops at ≈0.1–0.4 s.

**How to finish (next slices):** 1e+1f+1g (`ListSortedE2ETest` 6/6)+1h (`ListGroupByE2ETest` 3/3) LANDED 29/09; `take`/`drop`/`slice` stay with pagination; `zip` DECIDED 29/09 (`D-MULTIPARADIGMA-ZIP` record `Pair` truncating at min — supersedes the rule-6 `D-NOT-JAVA` stop) — **1i LANDED 30/09:** library-first (`pairs.kf` record `Pair<A,B>` + `zipPairs<A,B>` via `CompilerPairs`, rewrite in `CollectionCallLowerer`/`CollectionZipLowerer`, `ListZipE2ETest` JVM/Script/JS GREEN incl. truncation/empty-hetero) + **native DONE via the honest gate `NAT008`** (`D-MULTIPARADIGMA-ZIP-NATIVE`, 30/09): a native list of a concrete primitive element stores the value RAW, so viewing it through the bare type-variable `zipPairs<A,B>` emitted `kof_unbox_*` over the raw int → SIGSEGV (rc=139; proven by `firstOf<T>(List<T>): T { return xs.get(0) }`, zero zip/injection). `CollectionZipLowerer` now refuses `zip` of a primitive-element list on every native target with `NAT008` (recorded in `backend-parity.md` Documented Gaps); reference-element `zip` runs natively and `zipNativeX86`/`zipCross` are GREEN against JVM (byte-identical), `zipPrimitiveElementOnNativeIsNat008` pins the refusal. Fixes en route: missing `emit` of the arg (stack corruption), type-before-name fns don't parse `<A,B>` (helper uses type-after-params), record accessors are `p.x()` (idioms/records). **Deferred (backend front):** box at the generic erasure boundary → delete `NAT008`, re-enable native `zip` for every element type.

> **State:** promoted `future/` → `docs/development/` (28/09, `D-MULTIPARADIGMA-PHASE1A`, `D-FUTURE-PROMOTION`); Phase 1 list ops 1a–1i are LANDED and Phase 2 (eager-cost benchmark yardstick, `benchmarks/pipelines/`) shipped 30/09 (see the header + `README.md` §1 row 0d). Phases 5–7 (DATA/INFRA: query/SQL/stream) remain **design-only**, gated by R12 (SYSTEMS closure) and a maintainer decision — this doc does not open them as work.

---

## 0. Question

> How to let the programmer write the **intention** of a data transformation (`users.filter{...}.map{...}`) keeping Kof simple, efficient, multiparadigm and able to execute that intention in the adequate backend (memory, stream, DB) — without turning Kof into a copy of Scala/Kotlin/LINQ/Haskell?

The answer is not "add 15 methods to List". It is: **keep the language surface as it is, extend the semantic layer so the compiler recognises a transformation expression, and let each backend decide the execution — preserving eager semantics today, enabling lazy/query tomorrow with no silent behaviour change.**

---

## 1. Diagnosis — where Kof really is (HEAD 2026-09-16)

### 1.1 How functions and lambdas are represented

- **Type:** `Type.FunctionType(List<Type> paramTypes, Type returnType, String className)` — `Type.java:29-33`. `className` is the synthetic class that implements the call (used for emit dispatch, bug 20).
- **Syntax:** `(x: Int) -> x*2`, `(a: Int,b: Int)->{return a+b}`, `() -> println("oi")`, `{ println("bg") }` block-lambda. Parsed by `parser/LambdaParser.java:106-143`. `looksLikeLambdaParams` / `looksLikeLambdaBlockParams` are `Implementation-defined`.
- **Lowering:** each lambda becomes a synthetic class `Lambda<N>` (or `LambdaTask<N>` for `spawn`) implementing synthetic interface `kof/Function<N>_<mangled>` — `CompilerLambdaClass.java:52`, `CompilerDriver.lambdaClass`. Captures become `private final` fields; mutated captures via `Box<N>` (`BoxClassFactory`). Call site: `new Lambda<N>(captures)` + `invoke(args)`.
- **Call representation:** `KofCall` with `KofCallKind {INSTANCE,STATIC,CONSTRUCTOR,FUNCTION,INTERFACE,SUPER}` — `IRNodes.java:30-ops` + `KofCallKind.java`. A lambda call is `FUNCTION`; dispatch `INSTANCE→INVOKEVIRTUAL`, etc. in `JvmBackend`.

### 1.2 Are lambdas first-class? (partially)

**Yes for:** storing in variable `var f: (Int)->Int = (x:Int)->x*2` — `closures.md:51-54`; passing as argument `list.map((x:Int)->x*2)`; returning ` (x:Int)->(y:Int)->x+y` — bug 19 fixed `6dad633`; storing in collection and invoking `listOf((x:Int)->x*2).get(0)(4)` — bug 20 fixed; `spawn` body.

**Limitations (blocking expressiveness):**
- Param without annotation defaults to `Object` (`LambdaParser.parseLambdaParameter:127` = `"Object"`, `closures.md:37` SG-012). Using it in arithmetic → `SEM001` hint. The only **contextual inference** is for `map/filter/reduce` on `List` (`MemberCallTyper.java:135-141` rewrites `Object` param to `elemType` via `contextualLambda`). No inference for any other higher-order, no `it`/`$0` implicit param (by design — `closures.md:148`).
- No generic lambdas, no default params in lambdas, no overload inside lambda, no named fun-type alias. No composition operator (`andThen`/`compose`/`|>`/`>>`). No currying sugar beyond `->` nesting.
- `FunctionType → ClassType` SAM conversion always passes `isAssignable` (`TypeChecker.java: SAM case`) but real check deferred to emit — `Unspecified`.

### 1.3 How closures capture

- **Scan:** `CompilerCaptureScanner.java:14-85` + `CompilerCaptures.collectCaptures`. Shadowed handling for params/inner decls.
- **Read-only capture:** snapshot at creation time, `private final` field — `closures.md:70-84` (`CompilerDriver` comment 897-898).
- **Mutable capture:** if captured var is assigned inside lambda **or** the outer mutates a var that is captured, `mutatedCapturedNames` triggers `Box<N>` (`CapturedVarBox.java:24`). Reads/writes become `KofLoadField/KofStoreField "value"`. Observable: mutation inside lambda is visible outside — `Stable`, tested (`SpawnE2ETest.spawnLambdaCapturesOuterLocal`). Historical bugs: mutable capture after external mutation gave garbage in Native (bug 9 FIXED), nested lambda capture `LambdaTask` missing (bug 19), etc.

### 1.4 How the compiler represents calls

- **Frontend:** `AstNodes.MethodCallExpr(receiver, methodName, arguments, typeArgs)` — receiver nullable (`f()` vs `obj.m()`). Generic call `f<T>(args)` via `< >` postfix - `grammar.md:218`.
- **Typing (two layers):** `SemanticAnalyzer` (phase 3) via `SemExpressionTyper` + `MemberCallTyper` (hard-coded dispatch tables for `List/Map/Set/String/Channel`, `Kof*` namespaces). `CompilerDriver` re-infers via `ExpressionTyper`/`MethodCallTyper`/`CollectionMethodTyper` — `compiler-architecture.md:8-4` "lowering re-infers everything" (deliberate, not a bug). Diagnostics `SEM013/014/025`.
- **Lowering:** `ExpressionMethodCallLowerer.lower` → `CollectionCallLowerer.lower` for collections → else `ExpressionInstanceCallLowerer` / `ExpressionStaticCallLowerer`. `CollectionCallLowerer` pushes `kof_list_map/filter/reduce` as `KofCall(FUNCTION)` to `KofRuntime` (descriptor `JvmRuntimeCallDescriptors:429`).

### 1.5 How the type system treats functions

- `Type.of("(Int)->Int")` parses function type string — `Type.java:51-59`. `FunctionType` is a normal `Type` variant with `isAssignable` SAM pass-through. No variance/bounds (`TypeVariable` only for class type-params, erasure in `JvmTypeMapper:16`). Erased at emit `TypeVariable→Object`. No function subtyping check (biggest gap SG-009: `ClassType→ClassType` always assignable). Call on value with `FunctionType` checks `ft.parameterTypes` via `TypeChecker.checkArgTypes`.

### 1.6 Expressions vs statements

- **Expression forms:** literal, identifier, binary, call, array access, lambda, `if (c) a else b` **mandatory else** (`PARSE044`), `switch (o){case->}` **mandatory default** (`SEM032`) with no fallthrough, `spawn f()` → `Handle<T>` (expr) vs `spawn f()` statement. `throw/return/break/continue` are **statements only** — `expressions.md:16-17`. Assignment is **statement** not value-expression (`SEM027`) — `var c = a=b` rejected.

### 1.7 Where optimisations happen

- `Optimizer.java:68-74` — **always on** (`optimizeEnabled=true`), 4 passes: `constantFold`, `deadEffects` (push+pop), `reachability`, `removeJumpToNext`. **There is no** inlining, fusion, pushdown, LICM, register allocation, escape analysis, devirtualisation. `Kof IR` is a flat stack-machine op stream with labels (`compiler-architecture.md:4.1`), nominal single `IRBasicBlock` per method. Heavy lifting delegated to target JIT (`JVM`) or `as/ld` (`Native`). No collection fusion, no intermediate-allocation elimination.

### 1.8 Is there an intermediate suitable for semantic operations?

- **Today: no.** `Kof IR` (30 ops, linear `KofLoadLiteral/Call/Binary/Label/Jump`) has **no dependency graph, no value domain, no basic-block CFG** (`compiler-architecture.md:152-165`). `QueryDslExpr.java:4` (`QueryDslExpr(entityType, whereClauses, orderBy, limit)`) exists only for `entity.query(db){...}` ORM DSL — lowers to `db.query<Entity>(...)` with SQL assembled at compile-time and binds (parametrised, no concat). It is **not** a generic transformation IR.
- **Per-target parity:** the same op stream is consumed by 4 backends (`JvmBackend`, `NativeBackend` x86/riscv/aarch64-translator, `JsBackend` tree `JsIr`, `KofInterpreter`). Parity proved by `ConformanceMatrixTest` (5 targets) + `KofInterpreterParityTest`. Differences are explicit gaps (`OTP001/002`, `FFI001`).
- **Conclusion:** to distinguish `constant / property / binop / lambda / map / filter / groupBy / distinct / sort / projection / materialisation` as **semantic expressions** optimisable per backend, a **new layer** is required — either as an **annotated AST/Transformation IR** before lowering, or as **metadata on the op stream**. The IR itself should stay flat; the new layer lives in the middle-end.

### 1.9 Collections

- **Implementation:** `List<T>` → `kof.List` → `java.util.ArrayList` (JVM) / own asm with free-list `kof_list_*` (`runtime/RuntimeList.java:13-362` + `nat/NativeRiscvAsmMapset1.java:196-353`) / `Array` (JS). `Map<K,V>`, `Set<T>` analogous (JVM `HashMap/HashSet`, Native asm, JS `Map/Set`). Method set hard-coded in 3 mirrored layers: `MemberCallTyper` (typing), `CollectionMethodTyper`/`MethodCallTyper` (type infer), `CollectionCallLowerer` (emit). Unknown allow-list → `SEM025`/`SEM055`/`SEM056` (homogeneity, index-type).
- **Current higher-order:** **only 3 on List**: `map`, `filter`, `reduce` (`training/idioms/collections.md:30-33`, `CollectionCallLowerer:17`). `map((x:Int)->expr): List<R>`, `filter((x:Int)->Bool): List<T>`, `reduce((a:T,b:T)->T, init): T` (both arg orders `(lambda,init)` and `(init,lambda)` accepted — `collections.md:32`). `reduce` generic? `Scheme`: `list.reduce((a:Int,b:Int)->a+b, 0)` (`JVM` reflects).
- **Semantics of current ops:** entirely **eager, allocating** (`kof_list_map` allocates `new ArrayList`, loops eager — `RuntimeList:247-281`). No lazy, no fusion, no effect tracking, no pushdown. `filter` predicate `Boolean.TRUE` or `Integer 1` → keep (`JvmStringMiscRuntime:93`, `KofInterpreterConcurrency:47`). Order preserved.

### 1.10 How native and JVM share semantics

- **One frontend + backend-agnostic IR + pluggable backends** (`README.md:70`). `CompilerDriver.compileSources` steps 1-15 (`compiler-architecture.md:32`). `LabelId.reset()` → `lowerToIR` → `applySuperBridges` → `Optimizer` → `selectBackend`. Same `Kof IR` consumed in order by each backend (flat op list with labels). Parity is **proven by execution**: JVM oracle is golden; Native via `qemu-riscv64/aarch64` + x86 host; JS via GraalJS embedded (`KofJsRunner`). `KofInterpreter` runs same IR without emitting bytecode (direct execution target, not a disguise).

### 1.11 How stdlib is resolved and compiled

- **Not via SymbolTable.** `kof.*` namespaces are **compile-time dispatch tables** (`KofStd`, `KofDb`, `KofCache`, `KofSecurity`, `KofValidation`, `KofUi`, `KofTime`, `KofHttp`, `KofMq`, `KofProcess`, `KofOrm`). Each has `staticMethod/instanceMethod` + `supportedOn/gapCode`. Call typer checks allow-list; lowerer emits `KofCall` to `KofRuntime` generated Java file (`JvmBackend` generates `KofRuntime.java` in output dir and compiles with `javac` — not `kof-runtime/` module, `compiler-architecture.md:5.1` "link-by-use"). `Native` emits asm symbols `kof_*`; `JS` emits via `JsCallEmitter`/`JsRuntimeOps`. **Never silent**: absent target → diagnosed gap (`TIME003`, `SECN002` etc. per `DECISIONS.md` G-05).

### 1.12 How new features are documented and tested

- **Docs:** `docs/language-reference/*.md` (grammar, types, semantics with `Status: Stable`, `Evidence:` line per section + probe), `docs/architecture/compiler-architecture.md` (normative about implementation), `training/idioms/<area>.md` + `training/anti-patterns/`, `learn/` step-by-step. New idiom/anti-pattern → update `training/` mandatory (`AGENTS.md` §"Updating the corpus").
- **Tests:** `FunctionSyntaxTest` (SEM vs `fun`), `LambdaE2ETest`, `SpawnE2ETest`, `CoreRegressionE2ETest`, `KofMapSetTest`, `KofInterpreterParityTest`, `ConformanceMatrixTest` (cell `increment`, `cast`, `ifexpr-heterogeneous`), `BackendParityTest`, `CompilerDriverTest` diagnostic cases (`SEM0xx`). **Quality gate Q0-Q7** (`AGENTS.md`): reproduce → fix root → prove with test in same commit → suite green → bench when plausible. `mvn test -o -pl kof-compiler -am -Dtest=...`, full suite `mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true` (1636 tests today, `grep -rl FAILURE */target/surefire-reports/*.txt`).

### 1.13 Limitations blocking a more expressive functional

| # | Limitation | Evidence | Impact on this plan |
|---|------------|----------|---------------------|
| L1 | Only 3 collection higher-orders (List.map/filter/reduce) | `CollectionCallLowerer:17` allow-list | Must extend without cloning Java `Stream` — each new op needs 3-layer table + 4 backends + parity |
| L2 | Lambda param inference only for those 3, otherwise `Object`→SEM001 | `MemberCallTyper.contextualLambda` gated on map/filter/reduce | Adding new higher-order must generalise `contextualLambda` or users keep writing `(x:Int)` boilerplate |
| L3 | IR has no transformation graph | `IRNodes.java` linear stack ops | Cannot do fusion/pushdown without a semantic layer; the plan proposes a **Transformation Expression IR** before lowering |
| L4 | All current ops are eager allocators | `RuntimeList.kof_list_map/filter` | Pipeline `filter.map.take(10)` always materialises 2 intermediate lists; needs `Sequence` lazy or fusion |
| L5 | No sequence abstraction | grep `Sequence` → 0 in domain | `Sequence<T>` is the natural lazy spine; must be `experimental` first |
| L6 | No effect / purity analysis | none | Cannot distinguish `filter{it.active}` (translatable) vs `filter{external.isValid(it.email)}` vs `filter{log(it);...}` — Phase 5 of the prompt |
| L7 | `TYPEOF` string (`Type.of` uses `"Object"` fallback) | `LambdaParser:127` | Weakens Typer; boxed nullable work `D-NULL-INTENT` (N1→N4) still in flight — do not overlap (`DECISIONS.md:860`) |
| L8 | `isAssignable: ClassType→ClassType` always true | `TypeChecker` final case, SG-009 | Safety delegated to runtime `checkcast`; functional generics rely on erasure + `substituteTypeVariable` positional |
| L9 | ≤500 lines gate, `NativeBackend 8834L`, `CollectionCallLowerer 443L` | `check_500`, `complexity-audit.md` | New ops must keep files under gate or split by responsibility, named by domain (never `*2`) |

**No second architecture exists** — there is only the dispatch-table + `KofRuntime` path. The plan reuses it.

---

## 2. Architectural proposal — compatible with today's Kof

### 2.1 Guiding principles (non-negotiable)

1. **Intention over mechanism** — `spawn`, `setOf().contains()` today; `filter`/`map` tomorrow. The language already favours this (AGENTS iron rule 1).
2. **Zero-bloat core** — the core language stays small; depth grows as stdlib dispatch, never as new targets or syntax (`../../architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` R12: "Universal capability ≠ bloated language").
3. **Additive, backward compatible** (`AGENTS.md` freeze rule 2): new collection methods are additive; existing `map/filter/reduce` semantics **do not change** (eager today stays eager unless the name or a distinct type says otherwise).
4. **Honest multi-target** (R6): a method that only exists on one target gives a diagnosed gap `XXX00x`, never a silent fallback.
5. **Not a copy** — no Scala collection hierarchy, no Kotlin `Sequence` transplant, no LINQ expression trees verbatim, no Rust iterator trait. The solution must read as **Kof**: `listOf`, `map`, `filter`, direct field access, `==` content where defined.
6. **Compile-time > runtime** — type info over reflection (the JVM `kof_ho_invoke` reflection over `invoke` methods is an existing debt; the Native path already uses function-pointer dispatch — keep that direction).
7. **Small units** — the ≤500 rule is part of the architecture; splitting is by responsibility (`RuntimeListMapFilter` vs `RuntimeListTakeDrop` vs `JsCollectionOpsOrdered`), never `List2`.

### 2.2 The central split: intention vs execution (your §3)

A transformation written by the programmer **is** an intention; how it executes is a backend concern — but the two must not be conflated into "a vague generic abstraction that hides everything" (your §3 final caution).

**Kof already does this once:** `db.query<User>(db, "SELECT ... WHERE active = ?", binds)` is the intention (`orm.query` DSL → `OrmCall` → `kof_orm_*`); execution is JDBC on JVM, SQLite `.so` on Native, Honest gap on JS (`DB001`). The functional side should reuse the same separation:

```
Kof source:    users.filter{ it.active }.map{ it.name }
       ↓ (a)   stays EAGER on List today — 2 allocators, no new type, no surprise
       ↓ (b)   same spelling can lower to Sequence (lazy) when the receiver is
               Sequence — explicit type, no implicit magic
       ↓ (c)   the compiler recognises the *shape* as TransformationExpr,
               optimisable per backend (fusion/pushdown) when profitable
       ↓ (d)   only when the shape is pure + known by the DB backend,
               it translates to parametrised SQL (no concat)
```

(a) ships first, fully tested. (b)(c)(d) are gated behind explicit types/diagnostics — not heuristics.

### 2.3 Representation — where to put it

**Keep `Kof IR` flat.** Introduce a **middle-end semantic layer** `TransformationExpr` (AST-level or lowering-level) that **annotates** a chain of calls, not a new backend-agnostic opcode universe.

```
Source
 ↓ Lexer/Parser  (LambdaParser, ExpressionParser)
 ↓ AST           (AstNodes + new TransformPipelineNode optional)
 ↓ Semantic      (ExpressionTyper annotates receiver element type, lambda FT)
 ↓ Transform IR  (new package, e.g. `transform/TransformExpr.java` sealed:
 │               Constant, Var, Property, BinOp, Logical, Call, Lambda,
 │               Map, Filter, FlatMap, Fold, GroupBy, Distinct, Sort,
 │               Projection, Materialize, ForEach, Find, Any/All/None, ...)
 ↓ Optimizer     (fusion rule: filter→filter, map→map; pushdown is future;
 │               all rules gated on purity — see §5)
 ↓ Lowering      (eager → `kof_list_*` today; Sequence → lazy iterators later;
 └               DB → SQL binds)
 ↓ Kof IR (unchanged op stream)
```

- The `TransformExpr` lives **before** lowering and is lowered to the existing `KofCall`/`KofDup`/`KofJump` patterns. Nothing loads at runtime.
- The existing `Optimizer.constantFold` stays; new `TransformOptimizer` handles only `TransformExpr` → never touches `Kof IR`.

### 2.4 Syntax — minimal, Kof-native (§5 of the prompt)

**Do not invent syntax.** The current syntax already solves the problem; extend only by adding **methods**, not by changing the grammar.

```kof
var doubled = values.map((value: Int) -> value * 2)
var big = values.filter((x: Int) -> x > 10).map((x: Int) -> x * 2)
```

- **Keep** `(x: Int) -> expr` (`grammar.md:231-233`). Implicit `it` is rejected (would be a contract change, rule 6).
- **Keep** `listOf(1,2,3)` / `mapOf` / `setOf`. No `[1,2,3]` literal (fake idiom).
- **Approved future sugar later:** `xs.filter{ it.active }` shorthand is already **rejected** (`parseLambdaBlockParams` looks for `x ->` / `x: T ->` with typed params; `{cond}` without `->` is a 0-param block-lambda, `closures.md:133`). Teaching it would be a decision `D-LAMBDA-SUGAR` with a bump, not a silent addition.
- **Pipeline operator `|>` / `..` / `in` does not exist** (`grammar.md:5.3`) — not introduced now.

### 2.5 Semantics — explicit, testable (§6 of the prompt)

| Property | Decision for Phase 1 (eager `List`) |
|----------|--------------------------------------|
| **Order** | Preserved (iteration in insertion order, as today — `kof_list_map` loops `0..size-1`). `sorted` is stable when comparator is pure. |
| **Evaluation** | **Eager** per operation (allocating). Documented: `filter.map.take(10)` allocates 2 intermediates today; `Sequence` will be lazy later. No silent mixing. |
| **Materialisation** | Explicit — every `map/filter` materialises a `List`. `Sequence` pipeline materialises only on terminal (`toList`, `forEach`, `reduce`, `count`, `find`). |
| **Mutability** | Source list is never mutated by `map/filter` (new list). `forEach` has no new collection (side-effects allowed but purity analysis still flags them). |
| **Equality** | Per-type `==` frozen (`expressions.md:4`): `String`/`record` content, `enum` identity, primitive value, else identity — applies inside lambdas too. |
| **Return types** | `map: List<A> → (A->B) → List<B>`; `filter: List<A> → (A->Bool) → List<A>`; `flatMap: List<A> → (A->List<B>) → List<B>`; `forEach: (A->Void)`; `find: List<A> → (A->Bool) → A?`; `any/all/none: List<A> → (A->Bool) → Bool`; `count` with/without predicate → `Int`; `take/drop: (Int) → List<A>`; `distinct: → List<A>`; `sorted: → List<A>` (natural order for comparable; with comparator ` (A,A)->Int` later); `groupBy: (A->K) → Map<K,List<A>>`; `zip: List<B> → List<(A,B)>`. |
| **Composition** | Associative: `xs.map(f).map(g) == xs.map(a->g(f(a)))` when `f,g` pure (test to lock). |
| **Failure** | `take(-1)`/`drop(-1)` → `0`/`size` (mirrors `slice` convention) or diagnostic `SEM055` — TBD, explicit in docs before code. |
| **Cross-target** | Same output on JVM/Native/JS (golden per target); `NaN` in sort not specified, JVM oracle decides. |

---

## 3. Incremental plan — do not do everything at once (your §4)

| Phase | Scope | Gate | What ships | What stays future |
|-------|-------|------|------------|--------------------|
| **0** | Diagnosis + proposal (this doc) | this commit | this `PLAN-MULTIPARADIGMA.md` pair | — |
| **1** | Functional foundations (your §5) — close the gaps **L1-L2** without new IR | `beta-0.4.0` additive | `flatMap`, `forEach`, `find`/`firstOrNull`, `any`/`all`/`none`, `count` (+ `take`/`drop`), `distinct`, minimal `groupBy`/`zip` later — all eager on `List`; generalise `contextualLambda` to new higher-orders; diagnostics `SEM05x` reuse | No Sequence, no IR change, no purity analysis |
| **2** | Evaluation & benchmarks (yardstick for §7) | after Phase 1 | **LANDED 30/09** — 6 eager pipeline benchmarks under `benchmarks/pipelines/` (map/filter/combined/with-take/distinct/sorted), validated JVM+JS; harness `kof bench` comparisons eager vs lazy per area (map/filter/combined/with-take, small/large, with capture) — no perf promise before numbers exist | No Sequence code yet, only numbers |
| **3** | Sequence / pipelines (your §7) — explicit `Sequence<T>` lazy spine | `experimental` tier (`backend-parity.md` R5) | `Sequence<T>` (`sequenceOf`, `asSequence`, `toList`, terminals) with lazy iterator semantics (JVM `Iterator`, Native state machine, JS `generator`), compatibility `List.asSequence()` / `Sequence.toList()`, materialisation explicit; parity documented | No optimiser fusion yet — laziness is via iterators, not rewriting the op stream |
| **4** | Transformation Expression IR (your §8) | middle-end, non-runtime | `transform/TransformExpr` sealed + `TransformAnalyzer` + `TransformOptimizer` (filter-filter fusion, map-map fusion only when purity proven) | No backend rewrite, no DB integration |
| **5** | Effect & translatability analysis (your §9) | static checker | predicate `isPure(expr)`, `isTranslatable(expr, backend)`, `hasObservableEffect(expr)` — progressive (pure Bool/Int/String property/binop/logic/call of known pure fn vs unknown/external/log) | No claim "half translates, half local" — decision deferred until analyser is accurate |
| **6** | Declarative query abstraction (your §10) | neutral representation | `queryOf(list)` / `q.filter{}.map{}` over `TransformExpr` source-agnostic; collection/memory backend first, stream/file stubs with honest gaps | No SQL, no JDBC |
| **7** | SQL backend (your §11) | parametrised only | `users.filter{it.active && it.age>=18}.map{it.name}` → `SELECT name FROM users WHERE active=? AND age>=?` when pure & knowable; never concat; parameterisation mandatory; joins/aggregations/pagination/transactions/errors catalogued as gaps | No ORM disfarçado, no query-builder that just renames SQL |

**Order is normative:** no `Sequence` before the extra `List` ops are solid; no `TransformExpr` before benchmarks prove the eager cost; no SQL before purity is testable. Each phase carries its own tests + parity + docs, and the previous phase's doc moves `future/`→`development/`→`docs/` as it closes.

**R12 guard:** Phases 0-4 are **core language hardening** (allowed before SYSTEMS closes — they do not introduce a new domain, package or target). Phases 5-7 are **DATA/INFRA** — gated by SYSTEMS closure (`roadmap.md` TIER 1: GC mark-sweep, parity gaps, package manager); this doc does not open them as work, only as design.

---

## 4. New operations — spec for Phase 1 (your §6)

**Priority eager on `List<T>`** (native dispatch via reuse of `kof_list_*` pattern; JS `Array` polyfill; interpreter parity).

| Op | Signature (Kof) | Behaviour | Native sketch |
|----|-----------------|-----------|---------------|
| `forEach` | `List<T>.forEach((T)->Void): Void` | Iteration without allocation; consumed per iteration; receiver not mutated | loop calling `invoke` per element, no `kof_list_new` |
| `flatMap` | `List<T>.flatMap((T)->List<R>): List<R>` | `T→List<R>` per element, concatenated in order | allocate `out`, iterate src, `invoke` → `List<R>` tmp, splice tmp `size` elements via `get` loop + `add` |
| `find` / `firstOrNull` | `List<T>.find((T)->Bool): T?` | First matching element or `null`; alias `find` for Kotlin familiarity but Kof name is `find` (not `first`) | loop `invoke` per elem, `test` → return elem, else `null` sentinel + `NullableType` return |
| `any` / `all` / `none` | `List<T>.any((T)->Bool): Bool` etc. | Quantifiers short-circuit (vacuous: `all`/`none` true, `any` false on empty — `none` ≡ ¬`any`, maintainer decision 28/09) | loop with early `return 1/0` on predicate result |
| `count` | `List<T>.count(): Int` and `count((T)->Bool): Int` | Underlying is `size` without predicate; with predicate counts matches | `size` or loop `if(pred) cnt++` |
| `take` / `drop` | `List<T>.take(Int): List<T>`, `drop(Int): List<T>` | `take(n)` → prefix `min(n,size)`; `drop(n)` → suffix `max(0,size-n)`; `n<=0` → empty or self | `take`: allocate + copy `0..n-1`; `drop`: allocate + copy `n..size-1` |
| `distinct` | `List<T>.distinct(): List<T>` | Dedup preserving first occurrence order; String content, rest identity/pointer (like `contains`) | `contains`-aware loop: `out.add(o)` only if `o` not already in `out` |
| `sorted` | `List<T>.sorted(): List<T>` (natural) + `sorted((T,T)->Int): List<T>` later | Copies and sorts; natural order via `compareTo` for String/numbers; naive for others = gap to define | `copy` + `qsort` (x86 `qsort_r` shim) or insertion for small; JS `Array.sort` |
| `groupBy` | `List<T>.groupBy((T)->K): Map<K,List<T>>` | Insertion-order map of groups | build `HashMap<K,ArrayList<T>>` mirroring `mapOf` path; keys boxed equality via `kof_map_put` |
| `zip` | `List<T>.zip(List<U>): List<Pair<T,U>>` — record `Pair` decided 29/09 (`D-MULTIPARADIGMA-ZIP`) | Up to `min(sizeA,sizeB)`; truncates silently (not an error) | pairwise `add`; **1i LANDED 30/09** (managed GREEN; native reference-elements GREEN byte-identical, primitive elements refused with `NAT008`) |

**Type inference for `find/any/etc.` must generalise `contextualLambda`** — extend `MemberCallTyper.contextualLambda` from `map/filter/reduce` to this new set; otherwise the user must write `(x:Int)` forever (the point of L2).

**Diagnostics reused:** `SEM025` unknown method, `SEM055` wrong index type, `SEM056` heterogeneous write; new codes only if a genuinely new failure (e.g. `sorted` comparator arity mismatch) — never silently drop (`DECISIONS.md` G-05).

---

## 5. Effects and translatability (your §9) — design before code

```kof
users.filter{ it.active && it.age >= 18 }          // pure → translatable
users.filter{ external.isValid(it.email) }         // unknown → not translatable
users.filter{ log(it); it.active }                 // effect → not translatable / no reorder
```

Future checker classifies a `TransformExpr` as:

- **Pure** — only `constant | var | property | compareTo | binop | logic | callToKnownPure`; no `kof.io`/`kof.log`/`kof.time.now()` nondet, no `spawn`, no assignment to outer.
- **Known** — predicate uses only properties/ops whose column mapping is known to the backend (declaration `entity User { name:String }` already carries the schema via `EntityDeclarationNode`).
- **Translatable** — pure ∧ known ∧ shape in `where|orderBy|limit|groupBy|distinct|sort|projection`. Everything else → "execute locally" / "diagnosed gap" — never partial silently.
- **No system of effects shipped in Phase 1** — document the evolution as a progressive accuracy table (Phase 5 upgrades it), not as a type-system (`TypeChecker` stays as is per ).

---

## 6. Query abstraction (your §10) & SQL backend (your §11) — intent, not an ORM

```kof
// same intention, different origins (future, not today):
users.filter{ it.active }.map{ it.name }           // List< User>
db.query<User>(db).filter{ it.active }             // DB query (when translatable)
file.lines().map{ parseUser(it) }.filter{...}      // stream (via FFI interop, not reimplemented)
```

**Invariants for SQL (quando chegar):**
- Modelo via `entity User { name:String; age:Int; active:Bool }` compilado para `record+schema` (já existe, `DATABASE_VISION.md` level 2).
- Propriedade → coluna por nome (mapping explícito futuro; default = nome da propriedade).
- Tipos Kof→SQL: `Bool→INTEGER 0/1`/`TEXT` per dialect, já em `KofOrm`.
- Parâmetros **sempre** preparados (`?`/`$1` + binds, nunca concat) — `KofOrm` já faz.
- `join`/agg/`page`/`transaction`/erros → gaps com código `DB001/ORM001` até implementação, nunca concatenação nem materialização ociosa.
- Não traduz toda lambda — apenas a sub-expressão `whereClauses: List<ExpressionNode>` cuja AST é `compare(field, literal/param)` — o resto executa local (a promessa "parte no backend, parte local" existe **só** depois que o analisador de efeitos provar a partição sem perda de semântica).

---

## 7. Optimisation by intention (your §12)

| Optimisation | When safe |
|--------------|-----------|
| `map(f).map(g)` fusion → `map(a->g(f(a)))` | only when `TransformExpr` proves `f`/`g` pure |
| successive `filter` fusion | always (conjunction) — no allocation |
| filter pushdown (`filter` before `map` when filter does not depend on mapped value) | only when analyser proves mapping-independent |
| projection pruning (only `name` column) | only when translation is pure |
| intermediate elimination on `Sequence` | via iterator, not via `kof_list_*` rewrite |

**No** reordering that drops an effectful call; **no** parallel execution without explicit safety claim; **no** assumption of purity where not proven.

---

## 8. File inventory — what changes and what is new (estimate per file:line)

### Will be edited (reuse existing seams)

| File (`kof-compiler/src/main/java/dev/kof/compiler/...`) | Role in the plan |
|---|---|
| `parser/LambdaParser.java` | keep, but note inference gate for new ops |
| `SemanticAnalyzer.java` / `SemExpressionTyper.java` / `MemberCallTyper.java:127-163` | widen `contextualLambda` from `map/filter/reduce` → full Phase-1 op set |
| `BuiltinTypes.java:156` + `Type.java` | no change for types, but `FunctionType` use widens |
| `ExpressionTyper.java` + `MethodCallTyper.java:240-254` + `CollectionMethodTyper.java:14-43` | mirror the `MemberCallTyper` change for lowering-side inference (`map` branch) |
| `CollectionCallLowerer.java:14-56,101-227,334-398` | **core edit** — add `flatMap/forEach/find/any/all/none/count/take/drop/distinct/sorted/groupBy/zip` branches (eager `kof_list_*` calls); generalise `emitArgsCoercingValue` usage |
| `ExpressionInstanceCallLowerer.java:270` | entry point that calls `CollectionCallLowerer.lower` — no change |
| `jvm/JvmStringMiscRuntime.java:81-103` | add `kof_list_flatMap`, `kof_list_forEach`, `kof_list_find`, `kof_list_any/all/none`, `kof_list_count`, `kof_list_take/drop`, `kof_list_distinct`, `kof_list_sorted`, `kof_list_groupBy`, `kof_list_zip` (JVM bodies) |
| `jvm/JvmRuntimeCallDescriptors.java:429` + `jvm/JvmRuntimeReturnDescriptors.java:191` | add descriptors for the new `kof_list_*` |
| `jvm/JvmOpCollections.java:63` | primitive-type box/unbox for new reduce-like returns |
| `runtime/RuntimeList.java:245-361` + `nat/NativeRiscvAsmMapset1.java:196-353` | real x86 & riscv asm for new list ops (each `globl` like existing); aarch64 via `NativeArchEmitter` translator |
| `js/JsRuntimeOps.java:48` + `js/JsBackend` + `js/JsCallEmitter` | add dispatch for new `kof_list_*` + `Array.*` polyfills |
| `KofInterpreterConcurrency.java:34-56` | add interpreter cases mirroring `RuntimeList` (fall back to `invokeLambda` loop) |
| `docs/language-reference/closures.md`, `expressions.md:10`, `type-system.md:12` | extend relevant type rows once shipped |
| `training/idioms/collections.md` | extend real-API section once shipped |

### New files (only when the phase needs them)

| File | Phase | Size target |
|------|-------|-------------|
| `transform/TransformExpr.java` (sealed: `Constant|Var|Property|BinOp|Logic|Call|Lambda|Map|Filter|...`) | 4 | ≤200 |
| `transform/TransformAnalyzer.java` (`isPure`, `isTranslatable`) | 5 | ≤200 |
| `transform/TransformOptimizer.java` (fusion only) | 4 | ≤200 |
| `collection/Sequence.java` (sealed interface, optional: or stdlib `.kf` wrapper)  + `collection/SequenceRuntime.java`  (lazy iterators: `MapView|FilterView|...`) | 3 | ≤400 each |
| `collection/SequenceOpsLowerer.java` (Sequence lowering, sibling of `CollectionCallLowerer`) | 3 | ≤250 |
| `bench/ListPipelineBenchTest.java` | 2 | **shipped 30/09 as Kof programs under `benchmarks/pipelines/`** (map/filter/combined/with-take/distinct/sorted) — the `kof bench` discovery form, not a Java harness |
| `E2ET: List*E2ETest` (Phase 1: `ListQuantifiersE2ETest`/`ListFindCountE2ETest`/`ListForEachE2ETest`/`ListFlatMapE2ETest`/`ListDistinctE2ETest`/`ListSortedE2ETest`/`ListGroupByE2ETest`/`ListZipE2ETest`), `SequenceE2ETest` | 1 / 3 | Phase 1 landed; `SequenceE2ETest` is Phase 3 (not built) |

**Delete/none:** no deletion of existing runtime files, no renaming of `kof_list_map`.

### Not touched (deliberate)

- `IRNodes.java` (linear op stream stays linear), `Parser.java` (grammar unchanged), `Lexer.java` (no new token), `CompilerPipeline` order, `NativeBackend.java` dispatch (only the runtime slice `.s` grows).

---

## 9. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **M1** Growing `CollectionCallLowerer` past 500L (already 443L) + `NativeBackend 8834L` | High | Gate fails CI | Split by responsibility when approaching 500: `CollectionMethodTyperFlatMap`, `RuntimeListTakeDrop`, `JsCollectionOpsRange` etc., named by domain; `check_500 --update-baseline` removal |
| **M2** JVM reflection (`kof_ho_invoke` iterates `getMethods`) becomes bottleneck on large lists | Medium | Perf claim | Native already uses function-pointer; JVM path kept simple but benchmarked (Phase 2). If slow, cache `Method` per lambda class |
| **M3** Reintroducing `SEM001` friction via non-annotated lambdas in new ops | High | UX loss | Widen `contextualLambda` together with each new op (one commit = one op + its inference, Q1) |
| **M4** Silent divergence on `contains` String tag (already `stringTag` logic) for `distinct`/`groupBy` keys | Medium | Paridade bug | Reuse `CollectionWrites.stringTag` conjunction `elemTag & argTag`, gated on both sides (like `contains`) |
| **M5** `Sequence` ambiguity vs `List` lazy/eager surprise | Medium | Break semantics | `Sequence` is **experimental** (R5), explicit type — `list.asSequence()` opt-in, never implicit; docs state eager vs lazy in one table |
| **M6** SQL over-promise (translating arbitrary lambda) | Medium | Injection / wrong semantics | Phase 5 explicitly says **do not invent**: parametrised only, purity gate must pass; otherwise the op stays local with diagnosed gap `DB001` |
| **M7** Overlap with compiler lane `D-NULL-INTENT` boxed `T?` (handle `find`→`T?`) | Medium | Rebase conflict | Coordinate on `NullableType` return for `find`/`firstOrNull`; reuse `SG-008` decision (`map.get→V?`) — same shape; do not alter box representation, only the return type wrapper |
| **M8** Aarch64 translator fragility (regex over riscv asm strings) | Low | Silent Native bug | New ops emitted to riscv first (`NativeRiscvAsmMapset1` style) and translator follows; covered by `NativeRiscv64E2ETest` + `NativeAarch64E2ETest` + `qemu` |
| **M9** Premature universal-stage opening (R12) if query/SQL lands before SYSTEMS closes | Medium | Process violation | This doc marks Phases 5-7 as **deferred** (design only) until Tier 1 gap table empties; promotion needs a decision in `DECISIONS.md` |

---

## 10. Acceptance criteria (must hold before the Phase merges)

### Language

- Kof is still multiparadigm: POO, procedural and functional coexist, no paradigm removed or weakened (your §2).
- `users.map((u:User)->u.name)` compiles without annotation boilerplate for the new ops (contextual inference widened, SG-012).
- No new keyword, no new operator, no grammar change beyond methods (prove: `grep TokenType FUN` zero beyond existing — `specification-gaps.md` SG-002 precedent).

### Compiler

- 3-layer tables mirrored (`MemberCallTyper` + `MethodCallTyper`/`CollectionMethodTyper` + `CollectionCallLowerer`) — adding a method in one layer without the others is a bug.
- `TransformationExpr` lives in the middle-end, not in `Kof IR` or at runtime.
- Diagnostics reuse `SEM025/055/056`; any new code has a gap-coded honest diagnostic per `DECISIONS.md` G-05.

### Runtime / stdlib

- Each new `List` op has defined behaviour for: order (stable), evaluation (eager Phase 1, lazy only when `Sequence`), materialisation (explicit), mutability (source untouched), equality (`contains` semantics), composition, failure (empty / out-of-range), parity (5 targets or diagnosed gap).
- No intermediate allocation avoided silently in Phase 1 — allocation count documented (e.g. `filter.map.take(10)` = 3 lists).

### Performance

- `kof bench` entries for `map`, `filter`, combined, `withTake`, small/large sizes, with capture — measured, not claimed (`bench/` harness, Phase 2).
- Native bench when target allows; cost of closures/captures/boxing accounted.

### Documentation

- `docs/language-reference/*`, `training/idioms/collections.md`, `learn/39-stdlib.md` updated in the same commit as the code (the meta-vivos sync rule, `DOING.md` i18n note).
- Limitations documented (eager vs lazy table, pure vs effect, translatable vs not). Future phases keep living in `future/` until promoted.

### Tests

- One commit = one op + its parity + its edges (Q1-Q3). Matrix: happy + empty/null (`listOf<Int>()`, `null` element via `Set` never silent), limit (first/last/out-of-range), expected error (`SEM025/056`), cross-target (JVM×Native×Script×JS same golden or gap), idempotency, concurrency (`spawn` over `map` result), resource (`kof_list_bounds_error` path).

---

## 11. Complexity estimate per phase (calibrated on closed work)

| Phase | Work shape | Reference (closed, similar) | Estimate | Why |
|-------|------------|-----------------------------|----------|-----|
| 1 — List ops eager (`flatMap`..`zip`) | ~10 methods × 3 layers × 4 backends | `§179` builtin UI/media shadowing `69bc0f73` (field+ctor+accessor, 2 backends) | **M** (~1 commit per 1-2 ops; total 6-8 commits) | Each op is "known dispatch + KofRuntime body + Native asm + parity E2E" — repeated pattern, but the Native slice and the `stringTag` conjunction need care per op |
| 2 — Benchmarks | harness wiring | `kof bench/profile` harness | **E-M** | Only wiring + numbers; no codegen change |
| 3 — Sequence lazy | new collection kind | `Channel<T>` (`schedule/channel`) | **M-H** | Lazy view chain (not trivial), but no GC/translator novelty; behaviour proven by `ChannelE2ETest` analogue |
| 4 — Transform IR + fusion | new middle-end package | `LambdaCapture Scanner` (Box) | **M** | Tree/sealed + 2 fusion rules, purity-gated; optimisation is minimal on purpose |
| 5 — Effect analysis | checker only | `§205` branch-by-branch lowering | **M** | Progressive table, not a type system |
| 6 — Query abstraction | neutral API | `kof.mq` / `kof.validation` dispatch | **M** | Thin API (`queryOf`) on top of TransformExpr, one backend (memory) |
| 7 — SQL backend | codegen over TransformExpr | `KofOrm` `where/orderBy/limit` wire | **H** | Parametrisation, joins/agg/pagination gaps, injection-free — research heavy |

**First viable increment (Phase 1a, doable now):** `flatMap` + `forEach` + `find`/`any`/`all`/`none`/`count` (6 ops) — no new IR, no Sequence, fully eager, reusing the `kof_list_*` pattern. One commit per op-pair with its E2E parity. After that, `take`/`drop`/`distinct` (1 commit), then `sorted`/`groupBy`/`zip` (gated — `sorted` and `groupBy` need comparator/boxing decisions).

---

## 12. What is NOT done (your §15) — explicit guards

No Scala/Kotlin/Haskell/Rust/LINQ copy; no gigantic functional API without clear semantics; no ORM disguised as language; no query builder that just renames SQL; no complex macro system; no full effect system before Phase 5; no total compiler refactor; no abstraction that only works on the JVM; no reflection-mandatory path; no naive translation of arbitrary lambda to SQL; no optimisation promise before numbers; no breaking change to existing code; no change without test + doc.

If a frontend feature seems implementable "because other languages have it" (pipe, spread, range `..`, Elvis `?:`, `it` sugar) — the answer is **no**, unless it fills a gap in the transformation intention within Kof's existing grammar and carries its own decision `D-PIPE` with bump + corpus sync.

---

## 13. Status / next step

**Phase 1 (list ops) and Phase 2 (eager-cost benchmark yardstick, `benchmarks/pipelines/`) are CONCLUDED** — slices 1a–1i LANDED (see §14 and the header), then the promoted scope closed and this document was **moved to `docs/stdlib/`** (3-state rule). The only remainder is the **backend erasure front** (`Deferred` in §14): boxing at the generic erasure boundary would delete `NAT008` and re-enable native `zip` for primitive elements; that touches the generic erasure/ABI and needs the backend lane (rule 6-adjacent), so it is NOT this plan's unit.

`NEXT STEP (none for this plan): the promoted scope (Phase 1 + Phase 2) is complete; Phases 5–7 stay design-only until SYSTEMS closes (R12) + a maintainer decision. Backend erasure front tracked by the native lane; gap code NAT008 in docs/backend-parity.md`

---

## References

- `AGENTS.md` (Quality gate Q0-Q7, gate `≤500`, `DOING.md` coordination, `future/` three-states rule)
- `docs/architecture/compiler-architecture.md` §§4.1-5.3 (real pipeline, IR flatness, backend selection)
- `docs/language-reference/{grammar,functions,closures,expressions,type-system}.md`
- `docs/bugs-and-gaps/specification-gaps.md` (SG-009, SG-012, SG-002)
- `docs/bugs-and-gaps/known-bugs.md` (bugs 19/20, §170 parity, §181 cast)
- `docs/development/roadmap.md` §23 Tiers 0–12, `docs/development/DECISIONS.md` (D-NULL-INTENT N1→N4)
- `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (invariants R1–R12, interop-first)
- `training/idioms/collections.md` (real API), `learn/16-lambdas.md`, `training/anti-patterns/fake-idioms.md`
