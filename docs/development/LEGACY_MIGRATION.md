[English](LEGACY_MIGRATION.md) | [Português](LEGACY_MIGRATION.pt_BR.md)

# LEGACY_MIGRATION.md — Legacy Software Migration Platform

**Status:** **UNDER DEVELOPMENT — promoted to current work 14/10 (`D-FUTURE-PROMOTION`, lane nativa/GC `192.168.15.117:0`).** Phases A–F have working code and tests (re-measured 22/10 by the lane that took this orphan: Decompile **228**, Translate **77**, Compare **15**, Migrate 11); **Phase F COMPLETE — the §3.3 progressive-support list reaches 19/19**, anonymous SAM → lambda); the remaining structured-shape faces need the diamond-law decision or the interop lane, both outside this plan's autonomous scope. **Current slice: Phase H — full JAR migration per §8.1** (`kof migrate app.jar --output dir/`: per-class decompilation to `.kf` files + the aggregate report with per-unit recovered/not-recovered detected from the actual stubs). **LANDED 14/10 (`39825a291`): the §8.1 behavioral-test wiring** — `kof migrate app.jar --output dir/ --compare` runs the differential test per class with a static `main` (legacy bytecode vs the compiled Kof, `Compare.runKof(kof, entryClass, args, stdin)`), feeding the report's `Behavioral tests: N passed / M divergent` section; main-less/packaged classes count as not-comparable (honest); `MigrationReport` gained `behavioralPassed`/`behavioralDivergent`/`behavioralNotComparable`; proof `MigrateTest` 10/10. **LANDED 14/10 (`9205629bd`): the Phase G typed-exception comparability face** — `kof compare` extracts the first exception/error TYPE from both stderr streams (JVM trace format) as a dedicated `exceptions` channel that GATES the verdict (closes a measured false green: two sides failing exit 1 under DIFFERENT types compared equivalent); proof `CompareTest` 13/13. **How to finish:** remaining Phase G faces = return values (program-level via the exit-code channel; per-function is the decompiler's diamond-law ceiling, outside this plan's autonomous scope), DB mutations/protocols/side effects (external-observation infrastructure), and JS/native trace formats for the typed channel — each behind its own measurement. The former wording follows. — **central and single doc of the migration
platform** (dropped from `future/` on 12/09; MERGED `LEGACY_IR.md` into §4 and
`DIFFERENTIAL_TESTING.md` into §8 on 13/09 — duplicated concepts, zero
unique content; technical work-logs live in `DECOMPILER.md`/`TRANSLATOR.md`).
The platform exists: `kof inspect/decompile/translate/compare/migrate`
registered in `Main.java` (test count = single source in
`roadmap.md` §23 TIER 3–5; `inspect` is the IR stats command, with no test
class of its own). Method body recovery is still partial — the traceable
report exposes it honestly)

**Owner:** `192.168.15.117:0` (lane nativa/GC — ONE plan, ONE owner per `D-PLAN-ONE-OWNER`).
**Scope:** migration platform (implementation started in 0.3.x)
**Created:** August 22, 2026 · **Last consolidation:** 13/09/2026

---

## 1. Vision

Kof is not just a language for creating new software.

The long-term vision is a platform capable of **analyzing, recovering,
translating and modernizing legacy systems to Kof**:

> Preserve the functionality of legacy software while we modernize its
> implementation to Kof.

The platform must work both with available source code and with systems where
the original code was lost — using bytecode, binaries, metadata, build
artifacts and observable behavior as sources of information.

**This documentation is architecture + implementation state (§3 has the real
table: commands ✅ in the CLI, honest partial coverage).**

---

## 2. Fundamental Principle

The platform does not assume `Legacy → Java → Kof`.

When possible, it uses the direct path:

```text
Legacy
   ↓
Legacy Semantic IR
   ↓
Kof AST
   ↓
Kof IR
```

For JVM:

```text
JVM Bytecode
     ↓
Bytecode Analysis
     ↓
Legacy Semantic IR
     ↓
Kof AST
```

Java can be a **supported origin** (via translator), but never a
**mandatory intermediate representation**. This avoids an artificial Java
generation step between the legacy artifact and Kof.

---

## 3. Planned Components

| Command | Purpose | Status |
|---------|-----------|--------|
| `kof inspect <input>` | Structural analysis of `.class`/`.jar`/binaries | ✅ `Inspect.java` (Main.java:25) |
| `kof decompile <input>` | Recovery of Kof code from compiled artifacts | ✅ `Decompile.java` (Main.java:26; partial body → honest stub) |
| `kof translate <input>` | Source-code migration (first target: Java → Kof) | ✅ `Translate.java` (Main.java:27; Java subset) |
| `kof migrate <input>` | Full migration with report | ✅ `Migrate.java` (Main.java:29; traceable report) |
| `kof compare <legacy> <kof>` | Differential testing between systems | ✅ `Compare.java` (Main.java:28; stdout/exit/stderr) |

**All commands exist in the CLI** (verified 12/09 — `Main.java:25-29`;
test count = single source in `roadmap.md` §23 TIER 3–5). What remains
in development is the
**coverage** of recovery (complex method bodies → honest UNKNOWN stub;
translator's Java subset).

### 3.1 `kof inspect` (implemented)

Analysis of existing systems. Responsibilities:
identify format, platform, version; analyze dependencies; identify
classes, methods, interfaces, fields, types; identify external calls,
reflection, dynamic loading, JNI/FFM/native calls; identify metadata,
debug information, serialization, resources; estimate recoverability.

Conceptual output (illustrative values — no metric is real without defined
implementation and methodology):

```text
Classes:              1842
Methods:              17391
Reflection:           detected
Native calls:         detected
Debug metadata:       partial

Recoverability:
Types                  HIGH
Control Flow           HIGH
Method Signatures      HIGH
Local Names            LOW
Comments               NONE
```

### 3.2 `kof decompiler` (implemented)

Intended for recovering Kof code from `.class`/`.jar`/`.war`.

Pipeline:

```text
JVM Class File
       ↓
Class File Parser
       ↓
Bytecode IR
       ↓
Control Flow Graph
       ↓
Type Recovery
       ↓
Data Flow Analysis
       ↓
Semantic Recovery
       ↓
Kof AST
       ↓
Kof Source
```

The decompiler prioritizes: semantic equivalence, readability, structure, types,
control flow, calls, inheritance, interfaces, recoverable generics,
exceptions, annotations, metadata.

**It does not try to artificially reconstruct the original Java.** The goal is
to produce **equivalent idiomatic Kof**, not to pretend the original source
was recovered.

### 3.3 `kof translate` (implemented)

Source-code migration (first target: Java → Kof).

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

The translator **does not work by textual substitution** (`public → ...`).
It understands the program's semantic structure.

Planned progressive support: classes, interfaces, inheritance, generics,
overloads, constructors, exceptions, annotations, records, enums, lambdas,
nested classes, anonymous classes, static initialization, access modifiers,
Java standard library, external library calls.

> **Measured 14/10 (lane nativa/GC `192.168.15.117:0`) — the list against the
> translator: COMPLETE — 19/19 faces land (the maintainer DECIDED the two open shapes on
> 14/10: hoisted name = `Outer_Inner`, static-only scope; anonymous = SAM →
> lambda, rest refused).** **Nested static classes HOIST since this round** —
> `static class Inner` inside `T` is emitted top-level as `T_Inner` and every
> reference is renamed (type positions via `kofType`, expression accesses like
> `T.Inner.X`/`Inner.X` via a guarded textual post-pass); EXECUTED proof: the
> translated program compiles and runs (`T_Inner(21).twice()` → `42`, and the
> qualified static `T.Inner.X` → `5`); non-static inner (captures the
> enclosing instance) and name collisions (`T_Inner` already top-level; the
> same `Inner` under two outers) refuse with NAMED reasons. **The `continue`-join shape (contFor) recovers with CALCULATED conditions — and a WRONG ORACLE was found and corrected** — instrumenting the `struct` refusals (a permanent `refusalSink`, the `blockerSink` analog: cond-null 425, emitLinear-null 372, struct-generic 241 on the own-classes corpus) showed the dominant refusal is calculated test conditions. Reopening arithmetic in the closed-set evaluator emitted the `for+continue` WRONG — per the guard face's oracle — but EXECUTING THE REAL JAVA ORACLE proved the old recorded list (`0 0 1 3 3 7 12`) mathematically WRONG for that body (contFor(4) = 1+3 = 4, not 3; the correct sequence is `0 0 1 1 4 4 9`, verified n=0..20): the emitted Kof was RIGHT and the recorded oracle was the error. The condition path now applies the branch inverse on arity-1 (`ifne` → `== 0`) and emits the continue as an empty-then with the common increment outside (semantic-equivalent); the guard face was CORRECTED to lock the zero-drift against the executed Java (n=0..20 identical). `&&`/`?:` (real shared joins) still honestly degrade. **String casts recover since this round** — the checkcast handler refused String targets ("redundant/ambiguous"); MEASURED that `as String` is FAITHFUL (compiles; the correct cast returns, the wrong one THROWS the CCE — the exact runtime check of Java's checkcast), so the refusal didn't survive execution: `cast(Object o) { return (String) o; }` now recovers (`arg0 as String`), zero-drift proven (the decompiled tree RUNS printing `ok`/`4`). **Array conditions recover since this round** — `for (i = 0; i < a.length; i++)` stubbed because the test-condition path only accepted BARE loads (the exact-arity guard); a CLOSED-SET mini stack evaluator (loads + constants + `arraylength` ONLY — arithmetic deliberately EXCLUDED: the old exact refusal also gated the un-handled `continue`-join shape, and reopening arithmetic emitted a WRONG compilable `for`, the exact R6 regression the guard face locks — measured by reopening it, then narrowed back) now evaluates the test expression exactly; zero-drift proven end-to-end (Java prints 10, the decompiled-then-compiled Kof prints 10). **Static fields + the `<clinit>` FOLD in the DECOMPILER since this round** — a MEASURED false OK of the same shape as the translator's closed one: a decompiled `static int W = 7` class emitted NO field at all and the `<clinit>` block disappeared silently (the emitted Kof referenced `St.W` that did not exist — non-compiling). Now static fields are EMITTED and the clinit in the literal-putstatic shape folds into the field initializers (`static Int V = 42`, `static String S = "hi"`); constants `static final` (JVMS 4.7.2 — they do NOT pass through the clinit) fold via the newly-read `ConstantValue` attribute (additive parser read); non-literal clinit bodies leave the fields without initializer (reported, never fabricated). Executed proof: the decompiled tree compiles and RUNS printing `42`/`hi` (`DecompileTest.staticFieldsAndClinitFoldNotSilentlyLost`). **Anonymous classes LOWER since this round (the last face)** — an anonymous of a single-abstract-method functional interface lowers to a Kof lambda (typed params, `(x: Int) -> { return x * 2 }`); the JDK `Runnable` lowers the same; multi-method and stateful (a lambda has no fields) refuse with NAMED reasons. EXECUTED proof: the translated programs compile and run (the SAM probe printed `42`, the multi-statement one `9`). classes/interfaces/inheritance/generics/
> overloads/constructors/exceptions/annotations/records/enums/lambdas/access
> modifiers/Java stdlib/switch/loops/arrays/casts/instanceof all translate
> (probed per-item); **static initialization FOLDS since this round** —
> `static { V = 42; }` with ONLY literal assignments to declared static
> fields folds into the field initializers (the block's value WINS over a
> field initializer — Java runs the block after the field inits; executed
> proof: the translated program compiles and prints `42`), non-literal
> bodies keep the NAMED refusal; **instance init `{ }` folds** — the body is
> prefixed into every constructor (a class without one gains the default
> constructor carrying it; a block AFTER the first constructor refuses —
> textual order is not recreable in this one-pass parser). The remaining refusals
> (all NAMED): **non-static inner** (captures the enclosing instance — the synthetic
> `this$Outer` field = Option B is the unlock if wanted), **name collision** (`Outer_Inner`
> already top-level, or the same `Inner` under two outers), and **multi-method / stateful
> anonymous** (a lambda has no fields; multi-method is not a SAM). The hoisted-name format
> (`Outer_Inner`) and the anonymous shape (SAM → lambda) were CONFIRMED by the maintainer
> 14/10 — the §3.3 list is COMPLETE (19/19).

---

## 4. Legacy Semantic IR (was `LEGACY_IR.md` — MERGED here 13/09)

The Legacy Semantic IR is the intermediate representation between the origin
format and the Kof AST. It exists so that each adapter (JVM bytecode, Java,
COBOL, etc.) produces the SAME semantic representation — allowing the
rest of the pipeline (decompiler, translator, differential testing) to be
independent of the origin.

Concepts represented: types, functions, methods, fields, inheritance,
interfaces, calls, control flow, exceptions, memory/external operations,
constants, data flow, metadata, dynamic behavior and **unknown operations** —
unknown information stays `UnknownType`/`UnknownCall`/`UnknownField`/
`UnknownBehavior`, never fabricated code "that looks valid".

### 4.1 Confidence Model (implemented — `Confidence.java`, 5 levels)

```text
Recovered exactly        — observed directly in the artifact
Recovered with metadata  — observed + metadata (debug info, signatures)
Inferred                 — derived from analysis (data flow, types)
Heuristic                — plausible, based on heuristics
Unknown                  — unrecoverable (becomes an honest stub in the .kf)
```

Each recovered element carries the level; the tool always distinguishes
**observed** from **inferred**.

### 4.2 Source Mapping

The IR preserves the relations `Legacy Source ↕ Legacy Semantic IR ↕ Kof AST ↕
Kof Source ↕ Kof IR` — the basis of diagnostics, auditing, comparison and the
traceable report of `kof migrate` (§8.1).

### 4.3 Implemented state (measured, never from memory)

- Phase B/C/D (JVM): `BytecodeReader/Decoder/Statements/Frame` +
  `Type.fromJvmDescriptor`/`fromJvmSignature` (`Signature` attribute, JVMS
  4.7.9.1 — generics/wildcards/type-variables, `367d6c4`); proof
  `ClassFileE2ETest.genericSignatureRecovery` + `DecompileTest` (live count
  in `roadmap.md` §23 TIER 3–5).
- Recovery of **other platforms** (Native/JS/binaries): not started —
  it is Phase I (§9), gated by R12.

### 4.4 Unrecoverable Information (was §5/`LEGACY_IR.md` §3)

Compilation is a transformation with loss. After `Source → Compiler → Bytecode`
the following disappear: comments, local names (without debug info), original
syntactic structure, formatting, generic information (erasure), programmer
intent and eliminated abstractions. **Decompilation is not recovery of the
original source** — it is recovery of behavior and structure; the
unrecoverable is EXPLICIT (UNKNOWN stub), never invented.

---

## 5. Relationship with the Kof Compiler

The platform does not duplicate existing components. It reuses:
Kof Lexer, Kof Parser, Kof AST, Kof Type System, Kof Semantic Model,
Kof IR, Kof Backend.

```text
              ┌───────────────┐
              │ Kof Source    │
              └───────┬───────┘
                      ↓
                 Kof Frontend
                      │
Legacy ───────────────┤
                      │
Java ─────────────────┤
                      │
JVM Bytecode ─────────┤
                      ↓
              Legacy Semantic IR
                      ↓
                   Kof AST
                      ↓
                  Kof Compiler
                      ↓
                  Kof IR
                      ↓
               JVM / Native
```

The migration tool is a **natural extension of the compiler**,
not a second independent compiler.

## 6. Security and Legality

The platform is a **software engineering tool**. The user must
have adequate authorization and rights over the software analyzed.

The platform does not promise to circumvent: DRM, copy protection, access
controls, security mechanisms, licensing.

Focus: preservation, interoperability and authorized modernization.

---

## 7. Future Formats

The architecture prepares format adapters beyond the JVM:

```text
Source / Binary
       ↓
Legacy Adapter
       ↓
Legacy Semantic IR
       ↓
Kof
```

Potential examples (do NOT implement at the beginning): COBOL, PL/I, Assembly,
legacy binaries, proprietary bytecode, custom VMs.

The goal is to prevent the project from becoming conceptually stuck to the JVM.

---

## 8. Differential Testing (was `DIFFERENTIAL_TESTING.md` — MERGED here 13/09)

Validates that a migration preserves behavior: same input vector in the
original program and in Kof, comparing **observable outputs**.

- **What to compare (`kof compare`, implemented):** stdout, stderr, exit code,
  **files** (sandbox snapshot SHA-256) and — **LANDED 14/10, Phase G slice
  (lane nativa/GC `192.168.15.117:0`)** — **typed exceptions**: the first
  exception/error TYPE is extracted from both stderr streams (JVM trace
  format; the compare runs the JVM target on both sides) and compared as a
  dedicated `exceptions` channel that GATES the overall verdict. This closes
  a measured FALSE GREEN: both sides failing with exit 1 under DIFFERENT
  exception types (e.g. legacy NPE vs migrated ArrayIndexOO) compared
  equivalent before (stdout ""=="" , exit 1==1, files {}=={}; the raw stderr
  channel does not gate) — the typed channel flips it to divergent. The TYPE
  is the observable; the raw stack text (file names, line numbers) is
  environment noise (measured: legacy `Div.java:3` vs migrated `Div.kf:11`
  for the same `ArithmeticException`). Proof: `CompareTest` 13/13 — 5 new
  faces (the false-green closure, same-type-equivalent-despite-noise,
  throw-vs-clean-exit divergence, the E2E migrated `ArithmeticException`
  preservation, clean-run null==null). Remaining Phase G faces: **return
  values** are covered at the program level by the exit-code channel (main
  has no return value in either language; per-function return-value faces
  belong to the decompiler's structured-shape ceiling — diamond-law decision,
  outside this plan's autonomous scope); **DB mutations — LANDED 14/10,
  Phase G slice 2 (lane nativa/GC `192.168.15.117:0`)**: MEASURED first — an
  H2 FILE database run twice with the identical program produces DIFFERENT
  `.mv.db` bytes (the store header carries creation), so the DB observable is
  the QUERY DIGEST in stdout, not the store bytes; `captureFiles` excludes the
  driver-internal stores (`.mv.db`/`.trace.db`/`.lock.db`, named reason —
  without the exclusion equivalent DB programs compare files-divergent), and
  `Compare.runLegacy`/`runKof` gained extra-classpath overloads (the SAME
  provisioned JDBC driver on both sides — H2 via the zero-driver `DbDrivers`
  seam). Proof (executed): `CompareTest` **15/15** — the migrated Kof idiom
  (`db.connect`/`db.execute` with `?` binds/`db.query` → `json.decode<Row>`
  digest) vs the legacy JDBC program over their own file DBs: SAME rows →
  equivalent, ONE different row → divergent; full migration battery
  **154/154**. The slice also caught + FIXED a compiler regression from the
  #821 fix (`ec0dd0c1b`): the new inheritance-cycle walk fed
  `cls.superClass()` into `simpleName` unguarded and EVERY class without an
  explicit `extends` crashed COMP002 (internal error, measured RED by the
  battery faces) — null-tolerant guard + the
  `classWithoutExtendsStillCompiles` regression face (`CircularInheritanceTest`
  **4/4**; the sealed/inheritance neighborhood 29/29);
  **protocols, side effects** still need external-system observation
  infrastructure (future slices, each behind its own measurement); **JS/native trace formats** for the typed
  channel are a future face.
- **Divergence classification:** equivalent · divergent within scope
  · divergent outside scope · undefined behavior.
- **Acceptance criterion for a critical system:**
  `compile + static analysis + behavioral testing + differential testing +
  manual review + migration report` — "it compiled" is not enough.
- **Systems without source code:** when the source was lost, the observable
  behavior of the original binary is the source of truth (binary + metadata +
  dependencies + configuration + database + observed behavior). This is
  **software archaeology**, not trivial conversion.

> **Landed 14/10 — Phase H slice 3 (lane nativa/GC `192.168.15.117:0`): input vectors in the behavioral wiring.** §8's differential test is defined over the SAME INPUT VECTOR — `kof migrate app.jar --output dir/ --compare` now accepts `--arg <v>` (repeatable) and `--stdin <text>` and delivers the identical vector to BOTH sides of every per-main comparison (the wiring previously compared with hardcoded empty inputs). Proof (executed): `MigrateTest` **11/11** — the new face migrates a main that prints `args.length + args[0]` and runs the compare with `["x","y"]`: both sides print `2x`, verdict passed=1 (a one-sided plumbing flips it to divergent); the full migration battery **152/152**. Honest scope: the stdin leg of the plumbing is proven at the Compare level (CompareTest's `--stdin` faces); a decompiled body that READS stdin stubs honestly (the structured-shape ceiling), so the end-to-end stdin face waits for that ceiling (diamond-law decision).

> **Landed 14/10 — Phase F/G slices (lane nativa/GC `192.168.15.117:0`): the approved JDK→stdlib table + the diamond `super.m()` face.**
> **JDK→stdlib table (maintainer-approved 14/10)** — it CLOSES a measured FALSE OK: the translator emitted `ArrayList()`/`HashMap()`/`StringBuilder()`/`System.*`/`Collections.*` as-is, and the emitted Kof DID NOT COMPILE (SEM015/SEM011). Now: `new ArrayList<>()`→`listOf()`, type `ArrayList<T>`→`List<T>`, `new HashMap<>()`→`mapOf()`, type `HashMap<K,V>`→`Map<K,V>`, `System.currentTimeMillis()`→`time.now()`, `Collections.sort(xs)`→`xs.sort()`, `new StringBuilder().append(a).append(b).toString()`→`a + b` (single append: `"" + e` — String.valueOf semantics, measured); JDK types OUTSIDE the table (`HashSet`, `LinkedList`, …) refuse with a NAMED diagnostic — never emit non-compiling Kof. Proof (executed): each mapping translates, COMPILES and RUNS (`TranslateTest` `assertCompiles` faces: ArrayList+sort → `2`/`a`; HashMap → `1`; time → `true`; StringBuilder → `n=42`; HashSet → named refusal).
> **Diamond `super.m()` (maintainer 14/10)** — `invokespecial` of the current superclass emitted a STUB; measured that Kof supports `super.m()` (compiles and runs: prints 10), so the decompiler now RECOVERS it (`return super.base()`; proof: `DecompileTest.superCallRecoveredNotStubbed` — decompiled tree compiles with ZERO drift, no stub in the file). The interface default refinement `A.super.m()` is ALSO recovered since this round: the default's body is read from the SIBLING classfile and INLINED at the call site by the same pipeline (measured: `viaInterface() = 1` — zero stub in the file; the decompiled tree compiles zero-drift; `DecompileTest.interfaceSuperDefaultInlinedNotStubbed`). Inline scope: the no-argument shape AND the argument shape (the call site's argument expressions are bound to the default's parameters — measured `via(Int arg0) = (arg0 * 2)` and the wide `viaWide(Long arg0) = (arg0 + 1L)`); a non-recoverable default body or a non-simple argument load leaves the method non-recovered by the normal flow (reported, never fabricated). Stubs must never be a design option (Q7/AGENTS).

### 8.1 Migration Report (`kof migrate`, Phase H)

Report with migration traceability. Conceptual structure (the numbers
below are ILLUSTRATIVE — no metric is real without defined implementation and
methodology):

```text
Kof Migration Report

Input:     legacy-application.jar
Output:    kof-application/

Recovered:     94.2%        ← % of units WITHOUT an UNKNOWN stub
Warnings:      17
Unrecoverable: 3
Manual review: 12 locations

Behavioral tests:
    183 passed
    2 divergent
```

---

## 9. Implementation Order

Do not start by trying to support all legacy systems.

```text
Phase A  JVM Inspection          (.class/.jar + structural analysis)
Phase B  JVM Bytecode IR         (Class File → Bytecode IR)
Phase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Phase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Phase E  Kof Decompiler          (generate Kof source)
Phase F  Java Translator         (Java Source → Kof)
Phase G  Differential Testing    (Legacy vs Kof)
Phase H  Migration Reports       (complete reports)
Phase I  Additional Frontends    (COBOL, PL/I, Assembly, proprietary formats)
```

Before implementing: define the architecture, validate with small prototypes,
and only then turn the prototypes into official components.

---

## 10. Success Criterion

The initiative is successful when a real legacy system produces:

```text
Legacy System
      ↓
Analysis
      ↓
Recoverable Semantics
      ↓
Kof Implementation
      ↓
Behavioral Verification
      ↓
Modern Deployment
```

with: traceability, diagnostics, limitations report, differential tests,
human review, readable Kof code, native/JVM compilation, compatible behavior
within the defined scope.

**Central question:**

> Are we recovering real behavior or merely fabricating code that
> looks plausible?

If the tool cannot distinguish these two things, the migration is not
reliable.

---

## 11. Related Documentation

- `DECOMPILER.md` — technical work-log of body recovery (Phase E/§7:
  pipeline, structural joins, records, drift-check on the corpus)
- `TRANSLATOR.md` — Java → Kof subset (Phase F; active owner of the translator lane)
- ~~`LEGACY_IR.md`~~ → §4 of this doc (MERGED 13/09 — concepts+status; there
  was no unique content beyond the decompiler work-log)
- ~~`DIFFERENTIAL_TESTING.md`~~ → §8 of this doc (MERGED 13/09)
- `roadmap.md` §23 (TIER 3–5) — order/priorities with measured status
- `docs/audits/PLANNING-FUTURE-AUDIT.md` — planned×actual audit
