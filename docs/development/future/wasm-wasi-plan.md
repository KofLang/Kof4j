[English](wasm-wasi-plan.md) | [Português](wasm-wasi-plan.pt_BR.md)

# Kof WASM & WASI — future implementation specification

> **State (19/09): FUTURE — specification only, zero code.** No WASM/WASI
> implementation was performed or is implied by this document. Registered at
> the maintainer's request so that a future agent/developer can implement the
> feature from this spec alone. Not an execution queue (three-states rule +
> R12). Every statement is marked **CURRENT** (measured in the code),
> **PLANNED** (proposed here) or **TBD / DECISION REQUIRED** (not decided —
> never presented as decided).
>
> **Decision update (28/09/2026):** the technical questions this spec left
> TBD/DECISION REQUIRED were **resolved** by the maintainer in `DECISIONS.md`
> §`D-WASM-GO` (`D-WASM-01..09`: direct backend, `Int`=i64, thrown-string
> unwinding, handles+handle table, native mark-sweep GC design, explicit
> closure env, WASI preview1, wasmtime first, cooperative concurrency). The
> spec is still **future/zero-code**; promotion is one-at-a-time per
> `D-FUTURE-PROMOTION`. Do not read the inline TBD markers as still-open
> decisions.
>
> **Sibling docs:** `qrcode-wasm-plan.md` PART 2 (the frontend strategy: JS ×
> WASM coexistence — this spec is its implementation detail for the compiler
> side); `DECOMPILER.md` / `TRANSLATOR.md` (reverse direction: WASM→Kof — a
> future differential oracle for this backend, §26).

## 1. Objective

**PLANNED:** add `WASM` and `WASI` as official Kof targets:

```text
Kof source → Kof compiler → Kof IR → WASM backend → .wasm
```

A Kof developer writes Kof and selects WebAssembly as target — no
JavaScript, no C, no intermediate language. WASM is a **first-class
backend**, not a transpilation pipeline. **WASI is the same backend plus a
system-interface layer** (§2).

## 2. WASM × WASI — two layers, one backend

```text
WASM ─ WebAssembly module; execution controlled by a host (browser,
       wasmtime, embedder). No ambient rights.
WASI ─ WASM + system interface: filesystem, stdin/stdout/stderr, clocks,
       random, args/env — capabilities granted by the WASI host.
```

Two execution shapes (PLANNED):

```text
Kof → WASM module → host                (browser: Web APIs via bridge)
Kof → WASM module → WASI interface → WASI runtime   (server/CLI/edge)
```

WASI is **not** "WASM for servers": it is a different capability layer over
the same module format. The backend must treat the difference as *which
imports the module declares*, not two compilers.

## 3. Current architecture (measured 19/09 — the ground this spec stands on)

**CURRENT** facts this plan must respect (verified in the tree):

| Component | Real state (19/09) |
|---|---|
| Target enum | `dev.kof.compiler.Target` = `JVM, NATIVE, NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID, SCRIPT` — **no WASM/WASI value exists** |
| Honest rejection | `TargetMatrix` already maps the strings `wasm`/`kofwebasm` → gap **`WASM001`** ("planejado Fase 6 — rejeitado com gap honesto, nunca silencioso"): asking for wasm today yields a diagnostic, never a silent fallback |
| Pipeline | `CompilerPipeline.compile(driver, sourceFile, outputDir, Target)` — one frontend (Lexer→Parser→AST→semantic analysis) into per-target lowering/codegen; `SCRIPT` interprets the **shared IR** (`CompilerPipeline.interpret`) — proof the IR is backend-agnostic enough to host a new backend |
| JS backend | `dev.kof.compiler.js` (`JsBackend`, `JsArtifactWriter`, `JsCallEmitter`, `JsClassEmitter`, …) — text-emitting reference for a new `wasm` package |
| Native backend | `dev.kof.compiler.nativex8664` (+ `riscv/`, `aarch64/` siblings) — the **closest relative of a WASM backend**: own allocator, own object layout, hand-rolled runtime; reuse analysis in §7 |
| GC | Native mark-sweep is **in development** (roadmap: G-0 riscv ✅ `356f33b9`, G-1..G-5 decomposition) — WASM GC depends on it (§9) |
| Capability system | stdlib classes gate per target via `supportedOn` + `XXX00x` gap codes (`DomainGapCodesTest` pins) + the matrix in `docs/backend-parity.md` — WASM joins as a new column, not a new mechanism (§14) |
| KofUI | D-UI-SCOPE (ratified 19/09, `backend-parity.md`): kof.ui rule-updates are authored for **KofJS today** and **Kof WebASM when `Target.WASM` exists** — never JVM/Native (§17) |
| CLI | targets parsed in `kof-cli` (`BenchDiscovery`/`CmdBuild` map `jvm|native|js`…); `--target` is the selection point (§23) |
| Dev host | **no** `wasmtime`/`wasmer`/`wat2wasm`/`wasm-opt` on the dev host (measured 19/09) — future tests need toolchain guards (`assumeTrue`, never silent skip) |

Not assumed: that the diagram in §4 already exists in exactly that shape —
AST, semantic analysis and the per-target dispatch DO exist (above); the
"IR" is today the lowering input shared by JVM/native/script paths, whose
formality is a **Phase 0** subject (§31), not a given.

## 4. Future architecture (PLANNED)

```text
                          Kof
                           │
                   Frontend + IR (CURRENT: parse/typer/lowering pipeline)
                           │
      ┌──────────┬──────────┼──────────┬──────────┬──────────┐
      ▼          ▼          ▼          ▼          ▼          ▼
    JVM       Native        JS       WASM       WASI      Script
                                    │           │
                                 Browser      WASI
                                  host        runtime
```

Shared: frontend, typer, capability registry, gap codes, conformance
harness. Specific: the `wasm` emitter, a wasm runtime prelude, the host
bridge (browser) or WASI imports (wasi). WASM and WASI **share the backend
and differ only in declared imports + runtime bindings**.

## 5. Architectural decision (main requirement)

**Decision:** `Kof → WASM` is direct compilation through the compiler's own
infrastructure.

**Context:** WASM can be reached via several pipelines; the choice fixes
ownership of correctness.

**Options:** (a) direct backend from Kof IR; (b) Kof→JS→WASM (transpile
chain); (c) Kof→C→WASM (third-party toolchain in the middle).

**Chosen direction (PLANNED):** (a).

**Reason:** (b)/(c) make an external tool the semantic oracle — every bug in
it becomes a Kof bug, the output is uncontrollable, and the frozen
semantics (`==` content, String-exceptions, spawn/await, GC) end up defined
by someone else. (a) reuses the real components: `CompilerPipeline` dispatch,
the typer/lowering split already proven target-agnostic by `SCRIPT`, the
`js/` package as the shape-reference for a backend package, `TargetMatrix`
for the capability column.

**Consequences:** Kof owns the WASM correctness surface (validation +
fuzzing obligations, §26–27); a wasm runtime must exist (§7); **DECISION
REQUIRED** from the maintainer to freeze this as official (rule 6).

## 6. Target registry (PLANNED)

```bash
kof build --target=wasm   # PLANNED — today: WASM001 honest rejection
kof build --target=wasi   # PLANNED
kof check --target=wasm   # PLANNED (diagnostic parity with other targets)
kof run --target=wasi     # PLANNED (requires a WASI host, §19)
```

**Implementation order honesty:** each command appears in the CLI only with
its phase (§31); until then `WASM001` remains the answer. Registry touch
points (measured): `Target.java` enum + `TargetMatrix` + `kof-cli` target
parsing — **enum change = frozen-surface adjacent → rule 6 approval**.

## 7. WASM backend spec (PLANNED)

Module sections to emit: `Type, Import, Function, Table, Memory, Global,
Export, Start, Element, Code, Data`.

Mapping (Kof concept → IR → WASM):

| Kof concept | WASM representation | Status |
|---|---|---|
| function | `func` + type signature | PLANNED |
| local | `local` declaration | PLANNED |
| `Int` | `i32`/`i64` | **TBD** — which width matches Kof's `Int` overflow semantics (must equal JVM/Native golden, never JS double) |
| `Double` | `f64` | PLANNED |
| `Bool` | `i32` 0/1 | PLANNED |
| control flow | `block`/`loop`/`if`/`br`/`br_if`/`br_table` | PLANNED |
| exception (Kof's String-exception model) | no native mechanism → runtime convention (thrown-string global + `br` unwinding or host `throw`) | **DECISION REQUIRED** — frozen semantics, must map to observable behavior identical to other targets |
| call | `call` / `call_indirect` | PLANNED |

**TBD:** multi-value returns, tail-call, reference types vs i32 handles,
import/export naming ABI. Do not fix these before Phase 0.

## 8. Runtime WASM

Evaluation against the Native runtime (its code is the reuse candidate):

| Area | Reuse path | Status |
|---|---|---|
| allocator | native bump/mark-sweep design → wasm linear-memory allocator | needs adaptation (§9) |
| strings | `KofString` layout + content `==` | PLANNED reuse of layout, new memory substrate |
| arrays/objects | header (type/klass/size/fields) | PLANNED — layout must fit wasm addressing |
| runtime errors / panic (String-exception) | `kf_throw` model → wasm convention | DECISION REQUIRED (§7) |
| GC | see §9 | dependency |
| ABI | calling convention between Kof runtime fns and wasm functions | **DECISION REQUIRED** — Phase 0 deliverable |

Object representation in wasm (i32 indices into a handle table vs raw
linear-memory offsets) — **TBD**, Phase 0.

## 9. Linear memory

Model: `Kof object → Kof runtime → WASM linear memory (one per module)`.

Investigation points (no choices made): allocator strategy; alignment;
object layout; pointer representation (`i32` offset — wasm has no 64-bit
linear pointers pre-Memory64); growth (`memory.grow` + relocation problem —
growing invalidates offsets, ties directly to the GC/handle decision);
free; root tracking. **DECISION REQUIRED:** GC strategy vs handle-table —
the repo has not chosen; this spec does not choose for it.

**WASM must not create a second memory-management semantics independent of
Kof's** (freeze rule 5): the same program must observe the same
allocation/reuse behavior class proven by the Native GC golden tests.

## 10. GC

**CURRENT:** Native mark-sweep is in development (G-0 riscv ✅, G-1..G-5,
roadmap). **PLANNED dependency:** WASM consumes the SAME design — safe
points, root maps, object references, allocation/collection metadata —
re-expressed over linear memory. Explicit: **WASM is blocked on the GC
work reaching x86 stable** (mark-sweep currently disabled on the main native
path per roadmap). If a phase ships before that, it ships **without GC**
(arena/bump only) and says so in the capability matrix — no hidden
"eventually collected" claim.

## 11. Strings

`String` in WASM follows the **existing Kof spec** (content `==` is frozen;
UTF-8/UTF-16 choice is governed by `docs/stdlib` strings spec + JVM/Native
golds — wasm follows, never diverges). To cover in Phase 0: length model,
allocation, concatenation (compile-time constant folding + runtime
`kf_str_concat`), comparison (content, byte-identical results across
targets), indexing/slicing (frozen semantics — see JVM `charAt`/`[]`),
host interop (JS strings are UTF-16, WASI bytes are UTF-8 — conversion lives
in the bridge, not in the String layout).

## 12. Records, objects, classes, dispatch

Records (immutable), mutable classes (fields + constructor), inheritance
and virtual dispatch map to **Kof semantics first**: accessor calls, vtable
or `call_indirect` + function-table — representation TBD, **not** borrowed
from JS prototypes or class syntax. The wasm module may use tables for
dispatch; that is an implementation choice that must preserve observable
behavior (golden cross-target).

## 13. Closures

Capture model (value vs reference) is frozen behavior (CURRENT: JVM/Native
differ-none by the `collectCaptures` fix era; `spawn`-lambda capture is
tested). WASM needs: environment object (heap-allocated in linear memory),
captured-variable access, lifetime tied to the closure object (GC
dependency!), function reference = (code-index, env) pair — `call_indirect`
cannot carry env alone: **curried `invoke(f, env)` convention, DECISION
REQUIRED**. Nested closures share the design.

**Risk section:** closures are the highest-risk wasm item precisely because
they couple function references × environment allocation × GC (§9–10).
Phase 3 lands them only after GC + function-table are green.

## 14. WASI spec

Capabilities to specify (per chosen WASI version): `stdin/stdout/stderr`,
`args`, `environ`, `clock`, `random`, `filesystem (preopened dirs)`.
**WASI version: TBD** — the spec evolves (preview1 vs preview2/component
model); the implementation decision must re-check the state of the WASI
project at its time, recorded here as open, not guessed. **DECISION
REQUIRED.**

Rule unchanged from WASM §7: WASI = which imports the module declares; same
backend, different import set (`wasi:cli`, `wasi:filesystem`, … names TBD
with the version).

## 15. Capabilities (join the CURRENT system, not a new one)

Mechanism that exists today: per-stdlib-call `supportedOn` + gap code +
`docs/backend-parity.md` matrix + `DomainGapCodesTest` pins. **PLANNED:**
add `WASM`/`WASI` columns when `Target.WASM` exists; until then the column
is the whole-target `WASM001` (already honest).

Illustrative matrix (**`?` = nothing invented; most cells are TBD until
Phase 1–4 measure them**):

```text
                JVM   Native   JS   WASM   WASI
filesystem       ✓      ✓      -    ?      ?     (JS - = measured current; WASM/WASI = TBD)
stdout/println   ✓      ✓      ✓    ?      ?
clock/random     ✓      ✓      ✓    ?      ?
networking       ✓      ✓      ~    -?     ?     (browser fetch vs wasi-sockets = different, §21)
browser/DOM      -      -      ✓    ?      -
```

**Architectural rule (CURRENT, freeze 5/R6):** an API unavailable on a
target must produce an **explicit compile-time diagnostic** (`XXX001`, like
`WASM001`/`PROC001`/`FFI001` do today). **No silent stubs, ever.**

## 16. Browser WASM

```text
Kof → WASM → Browser host → Web APIs
```

Distinct from KofJS: **KofJS = JavaScript target** (CURRENT: text emitter +
browser E2E with Chrome headless); **KofWASM = WebAssembly target**
(PLANNED: compiled module + JS glue host). The bridge (generated glue, PLANNED)
covers DOM, events, `fetch`, storage, canvas, console — **the wasm module
cannot touch DOM directly**; everything crosses the host boundary. The glue
is *host integration*, not part of the language.

## 17. JavaScript interop

`Kof WASM → JS host` needs: string crossing (UTF-16↔module bytes), numeric
boundaries, callbacks, object handles. Principle: **JS is a capability/host
integration when needed — not a mandatory dependency of running Kof WASM**
(a WASI program must run with zero JS). WASM core and browser-specific
host integration stay separated (different artifact sets, §31 phases).

## 18. KofUI

**CURRENT decision (D-UI-SCOPE):** kof.ui rules are authored for KofJS
today and for **Kof WebASM when `Target.WASM` exists**; never JVM/Native.
**PLANNED shape:**

```text
KofUI
 ├── shared abstraction (CURRENT: the kof.ui API surface — intent, not HTML)
 ├── KofJS implementation (CURRENT)
 └── KofWASM host implementation (PLANNED)
```

Same conceptual API, different backend/host implementation — **no API
duplication, no second UI language** (rule 9/philosophy: intent declared in
Kof, rendering belongs to the platform).

## 19. WASI for applications

Future use cases — **documented, none promised for the first release**:
CLI tools, servers, edge functions, plugins/sandboxes, embedded apps. WASI
lets a Kof program compiled to `.wasm` run outside the browser on
compatible hosts, under capability grants — see security §29.

## 20. Host and runtime

`.wasm` is a module — execution **always** needs a host. Decisions
registered, not invented (all **TBD**): official development runtime;
supported WASI runtime(s); runtime discovery (`kof run --target=wasi`
behavior when none is installed → honest diagnostic, never a silent
fallback); no permanent coupling of Kof to one external runtime without an
architectural decision. Current host reality (measured): no WASI runtime
installed → CI guards must `assumeTrue` until this lands.

## 21. Concurrency

Frozen semantics: `spawn`/`await`/`Handle<T>`/channels (freeze 6). WASM
reality to analyze (no assumptions): browser/main wasm has **no preemption**
— native threads are a feature (wasi-threads is deprecated; the modern
direction is shared-everything proposals — **TBD**). Possible honest
outcomes include: cooperative scheduler on the module; spawn mapped to
Web Workers (browser) with channel-over-postMessage bridge (DECISION
REQUIRED); WASI threading deferred with `SCHED00x`-style diagnostic. **The
Native/JVM pthread model must not be assumed portable.** Until each choice
is made, the capability cell is `?`/`-` with diagnostic — R6.

## 22. Networking

Four different worlds — document separately, relate to the CURRENT
`kof.http`/`kof.db` per-target states (`backend-parity.md`): browser-WASM
(`fetch` via host glue), WASI networking (`wasi-sockets`, version-TBD),
Native (CURRENT), JVM (CURRENT). Gaps recorded explicitly; **no fake API
covering an absent capability** — a call that cannot be served gets
`NET001`-family diagnostic on that target.

## 23. Modules / imports

`import foo` (Kof's own import system, CURRENT) stays Kof regardless of
target — **no wasm-specific module system**. Open questions for Phase 0
(linking, module boundaries, exports, dead-code elimination, dependency
packaging, runtime dependencies): how a multi-file Kof program becomes one
module vs several with `import`s between them; the packaging answer should
converge with the official package system (roadmap §23, CURRENT gap
`PKG001`).

## 24. CLI & build system

**PLANNED** (proposal only — until Phase N ships, `WASM001` stands):
target selection (`--target=wasm|wasi`), output (`.wasm` artifact + glue),
runtime flags, linking, host configuration, capabilities. Touch points
measured: `kof-cli` target parsing + `CompilerPipeline.compile` dispatch.
Do not invent flags beyond the existing `--target` pattern.

## 25. WAT / debugging

**PLANNED:** `kof build --target=wasm --emit=wat` as backend dev/debug tool
(the CURRENT precedent is `--emit-asm` on Native — same spirit). Future:
debug info (source-level mapping; the KofJS **source map V3** work is the
cross-reference for what a wasm DWARF/name-section story would need). No
implementation now.

## 26. Conformance

Principle: **semantic equivalence across targets when capabilities are
equivalent; never identical binaries.** **CURRENT** harness reality:
per-target E2E/golden classes + `docs/bugs-and-gaps/conformance-matrix.md`
(not a `tests/conformance/` tree — do not invent one; extend the existing
mechanism). **PLANNED** addition: wasm/wasi target suites following the same
golden style, plus the **differential oracle opportunity**: the WASM→Kof
`DECOMPILER.md` plan, if both exist, closes a round-trip loop (wasm emitted →
decompiled → recompiled → same behavior).

## 27. Test strategy

- **Compiler:** AST→IR→WASM structural tests (golden `.wat` snapshots).
- **Validation:** every generated module passes a wasm validator (binary
  well-formedness ≠ correctness — validation is the floor).
- **Runtime:** generated module executes under the chosen host (§20) →
  stdout/exit-code goldens.
- **WASI:** program behavior under a WASI host with preopened dirs.
- **Cross-target:** same program → JVM/Native/JS/WASM(/WASI) where the
  capability cell allows; divergence = bug or `XXX001` diagnostic, never
  silence (freeze 5).
- **Guards:** missing host/toolchain → `assumeTrue` skip + recorded gap
  (the 19/09 host has no wasm tooling — this is the honest-skip CURRENT
  pattern, R6).

## 28. Fuzzing

Property-based/fuzz focus on the emitter's correctness surface: control
flow, **stack typing** (wasm is stack-typed — the deepest new bug class for
this compiler), function signatures, locals, closures, memory ops, strings,
object layout. Relation to the CURRENT backend-correctness problem (§25 of
the test-architecture plan; fuzzing is already in the roadmap queue):
**generating a `.wasm` proves nothing — generating a *semantically valid,
deterministically executing* module is the bar.**

## 29. Performance

Future benchmarks (no promises): startup, function calls, integer/float
arithmetic, loops, arrays, strings, allocation, JSON — then compare
JVM/Native/JS/WASM/WASI. **CURRENT precedent:** the repo's benchmark
harness (roadmap 1.4/1.5, native benchmark matrix) shows the measurement
style; wasm joins as a column, with the same honesty rules (never compare
against a stub).

## 30. Security

Sandbox is wasm's default posture, but the spec locks: capability-based
host boundaries; memory isolation inside the linear memory (bounds);
filesystem access **only** through granted WASI preopens; network **only**
through host capability. **A Kof→WASI program must never obtain arbitrary OS
access without passing through the host's capability gate** — mirrors
CURRENT `FFI001` (native dynamic FFI rejected) posture: heavy footguns stay
gated with diagnostics.

## 31. Dependencies / prerequisites

Justified by code analysis (not padded):

| Dependency | Why | State |
|---|---|---|
| IR formality | a new backend needs a stable lowering input (today shared with native/script) | Phase 0 subject |
| Native GC (mark-sweep G-1..G-5) | §9–10: wasm memory story reuses it | in development |
| Runtime ABI (wasm) | closures/objects/errors all sit on it | DECISION REQUIRED |
| Capability system (supportedOn + gap codes) | §15: the join point | CURRENT |
| KofUI D-UI-SCOPE | §18: the frontend contract exists, wasm side waits | CURRENT decision |
| WASI runtime decision | §20 | TBD |
| Conformance harness style | §26 | CURRENT (extend) |

Module system & packaging: related to `PKG001` but **not** a hard blocker
(single-module output sidesteps it in Phases 1–4) — listed as soft.

## 32. Future phases

| Phase | Objective | Depends on | Components touched | Tests | Done when |
|---|---|---|---|---|---|
| 0 | Architecture/ABI spec lock | decisions §5,8,13 | TargetMatrix docs, IR notes | none | maintainer signs DECISIONS.md entries |
| 1 | Minimal backend | Phase 0 | `dev.kof.compiler.wasm` (new pkg), `Target.WASM` enum (rule 6), CLI | compile+validate `.wat` goldens | hello-world module runs in chosen host |
| 2 | Runtime + memory | GC direction | prelude (alloc/strings/objects) | unit + validation | strings/ints correct |
| 3 | Language parity | 1–2 | control flow, records, classes, closures (§13) | cross-target goldens | language suite green where capabilities allow |
| 4 | WASI | runtime decision | import set, `Target.WASI` (rule 6) | wasi-host behavior tests | files/clock/random/args goldens |
| 5 | Browser host | 3 | glue generator | browser E2E (Chrome-headless precedent) | same-source JS-vs-WASM parity demo |
| 6 | KofUI integration | 5 + D-UI-SCOPE | shared abstraction × wasm host impl | UI E2E | one kof.ui app → both backends |
| 7 | Conformance | 1–6 | matrix columns, fuzzing rig | §26–28 suites | matrix green-or-diagnosed; §32 gates below |

Each phase = a full vertical cut (Q7): ships complete-with-diagnostic,
never facade-complete.

## 33. Release gates (moving this doc `future/` → `docs/`)

Only when implemented + validated — "generates .wasm" is **not** enough:

- [ ] real backend (not glue to another compiler)
- [ ] every emitted module passes a validator
- [ ] runtime: memory + strings work
- [ ] language parity (records/objects/closures/control flow) on green goldens
- [ ] WASI at the chosen version with capability gates
- [ ] capabilities matrix columns WASM/WASI filled with ✓/~/-/diagnosed (no `?` left unexplained)
- [ ] execution tests + conformance + fuzzing rig green (toolchain guards honest)
- [ ] docs + training synchronized (EN/PT)
- [ ] CodeQL/CI not degraded by the new surface

## 34. Roadmap dependencies

Real relations (see §31 table): **roadmap §23** package system (`PKG001`,
soft), **Native GC** G-1..G-5 (hard for Phase 2+), **D-UI-SCOPE** (Phase 6
contract), **IMPLEMENTATION-UNIVERSAL-PLATFORM** R6/R7 (honest gaps +
per-target scope — governs the whole matrix), R12 (this stays future until
the maintainer promotes it), **rule 6** (enum/ABI/frozen-semantics touches
need her decision), the **test-architecture plan** (fuzzing/property
testing infrastructure feeds §28), and `qrcode-wasm-plan` PART 2 (frontend
coexistence strategy this spec implements compiler-side).

## 35. Decision registry (all open — none invented)

```text
D-WASM-01  WASM as first-class direct backend (vs transpile chains)   DECISION REQUIRED (§5)
D-WASM-02  Int width mapping i32/i64 + overflow parity               TBD (§7)
D-WASM-03  Exception model on stack-typed wasm                       DECISION REQUIRED (§7)
D-WASM-04  object handles vs raw offsets; growth/relocation           TBD (§9)
D-WASM-05  GC strategy (reuse native design?)                         TBD (§10)
D-WASM-06  closure env + curried invoke convention                   DECISION REQUIRED (§13)
D-WASM-07  WASI version                                               TBD (§14)
D-WASM-08  dev runtime / supported WASI runtimes / discovery          TBD (§20)
D-WASM-09  concurrency model (workers? cooperative? diagnostic-only)  DECISION REQUIRED (§21)
D-WASM-10  Target enum addition (frozen-surface adjacent)             rule 6 (§6)
```

## 36. Validation performed for this document (19/09)

- files live in `docs/development/future/` ✓ (EN + PT pair, repo convention —
  a one-pair set was chosen as the minimum instead of the 8-file folder
  sketch in the request, per "criar a menor quantidade de documentos
  necessária / seguir o padrão existente")
- all CURRENT claims measured in code (`Target.java`, `TargetMatrix.java`,
  `CompilerPipeline.java`, `js/` package, `backend-parity.md` D-UI-SCOPE,
  roadmap GC lines, host toolchain check) ✓
- no future feature described as existing (the doc states repeatedly that
  wasm today = `WASM001` rejection) ✓
- no code was changed ✓; internal links resolve ✓; TBDs reviewed ✓.

**No implementation performed. Documentation only.**
