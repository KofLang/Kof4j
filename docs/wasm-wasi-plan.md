[English](wasm-wasi-plan.md) | [Português](wasm-wasi-plan.pt_BR.md)

**Owner:** `192.168.15.101:9092` (TIER 15 lane; 15.1+15.2+15.3-slice1+15.3b+15.3c-sliceA+15.3c-sliceB+15.3d-inc1+15.3d-inc2-A+15.3d-inc2-B+15.3d-inc2-C1+C2 landed; nested-record/`Double.toString` + collections next) — promoted by `D-WEB-WASI-DEFAULT-0710` (0.6.0 GATE, #776); any free lane claims it in DOING first (`D-PLAN-ONE-OWNER`).

# Kof WASM & WASI — future implementation specification

last: 15.3d-increment2-SLICE-E LANDED 09/10 (explicit `p.equals(q)` on a known-record owner routes to the SAME inline content fold as `==` — no virtual dispatch needed, contract §262 explicit-equals == content-equality; `WasmWasiE2ETest` 13/13 with `explicitRecordEqualsMatchesTheJvmOracle` byte-equal JVM oracle 5 faces, battery 31/31, zero record refusals left; repaired external damage committed on the tip: conflict markers in DOING EN+PT + known-bugs.md, and the cross-GC entry renumbered §640->§643 in PT/CHANGELOG where it collided with the gpu §640); before it 15.3d-increment2-SLICE-D LANDED 09/10 (nested-record fields = i32 handles on the bump heap; the alloc saves/restores the in-progress constructor receiver via `nestR[depth]` — objIdx corruption between alloc and stores was the measured root cause; `toString`/`==`/`!=` recurse with one scratch slot per depth; `WasmRecordCtor` split keeps `check_500` rc=0 (`WasmLowering` 644→545); E2E `nestedRecordFieldsMatchTheJvmOracle` byte-equal to the JVM oracle (8 faces), battery 31/31, full suite skipped by maintainer order; explicit `.equals()` keeps `WASM002` honest) — plan PROMOTED `docs/development/` → `docs/` at the lane cut by maintainer order 09/10 (slices E/F/G + 15.4 stay open inside the doc); before it 15.3d-increment2-SLICE-C2 LANDED 09/10 (record `==`/`!=`/`== null` CONTENT equality = opaque `kofRecordEq` + inline single-block field fold, byte-equal JVM oracle 12/12 faces, battery 30/30, `WasmPrintCode` split keeps `check_500` rc=0);
doing: 15.3 continues — slice E LANDED 09/10 (explicit `p.equals(q)` routed to the inline fold; `explicitRecordEqualsMatchesTheJvmOracle` green, the LAST record WASM002 refusal removed); slice F: `Double` fields in `toString` + `Double.toString` parity; slice G: collections (`kof.list/map/set` on the bump heap); then 15.4 flip LAST.
next: GC-handle runtime (records/collections) → 15.4 frontend-default flip LAST (only at full parity; `D-LAB-STABILITY` keeps the cut gated by #776).
location: docs/wasm-wasi-plan.md
state: UNDER DEVELOPMENT

> **State (07/10): UNDER DEVELOPMENT — promoted by the maintainer's own order**
> (`D-WEB-WASI-DEFAULT-0710`); **zero code at promotion day**. The plan is a
> **GATE for the 0.6.0 cut** (issue #776): Kof's web target becomes **WASI by
> default**, desktop frontend likewise; the JS/`kofjs` target **continues to
> exist** when explicitly specified; the **language surface does not change**;
> **total behavior parity** and **zero regression** are mandatory. The plan is
> OPEN and unowned — any free lane claims it in DOING first (`D-PLAN-ONE-OWNER`).
> Every statement below stays marked **CURRENT** (measured in the code),
> **PLANNED** (proposed here) or **TBD / DECISION REQUIRED** (legacy markers —
> the 28/09 `D-WASM-01..09` block already resolved the technical ones).
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

## 6. Target registry (15.1+15.2 LANDED 07/10 — enum + matrix + CLI parse + SCALAR emission)

```bash
kof build --target=wasm   # 15.2: emits the scalar subset; outside it: WASM002 honest rejection
kof build --target=wasi   # PLANNED
kof check --target=wasm   # PLANNED (diagnostic parity with other targets)
kof run --target=wasi     # PLANNED (requires a WASI host, §19)
```

**Implementation order honesty:** each command appears in the CLI only with
its phase (§31); until then `WASM001` remains the answer. Registry touch
points (measured): `Target.java` enum + `TargetMatrix` + `kof-cli` target
parsing — **enum change = frozen-surface adjacent → rule 6 approval**.

**Slice 15.1 plumbing LANDED (06/10, lane compiler/JVM/native `192.168.15.30:9092`):**
the honest gap now covers the WASI spellings too — `TargetMatrix.frontendGapFor`
recognizes `wasm32`, `wasm32-wasi`, `wasi`, `wasi-preview1`, `wasip1`, `kofwasi`
(besides the existing `wasm`/`kofwasm`/`kofwebasm`/`kofwebassembly`/`webassembly`)
and both CLI paths (`--frontend=wasi`/`kof.toml` via `TargetMatrix.parse`, legacy
`--target=wasi` via `KofCliSupport.parseTarget`) refuse with `WASM001`, never the
generic "unknown target". The message now names the promoted plan + issue #776 +
`D-WEB-WASI-DEFAULT-0710`. Additive plumbing only — **no `Target` enum value
yet** (that is the frozen-surface-adjacent step, rule 6, deferred to the codegen
phase). Proof: `TargetMatrixTest.wasiSpellingsAreHonestGap` +
`SelectTargetsTest.legacyWasiTargetFlagIsHonestGap` (both RED pre-slice).

**Slice 15.2 LANDED (07/10, lane `192.168.15.101:9092`):** first emitting
backend for `wasm` — `dev/kof/compiler/wasm/WasmBackend` writes a direct
binary WebAssembly module (`WasmBinary` sections, `WasmInstr` encodings)
exporting every top-level static SCALAR function (`Int/Long/Double/Bool/
Char`; `Int=i64` per D-WASM-02) of `Default/Main`; control flow lowers
through the IR block graph into a `loop $dispatch` + `$pc` dispatcher
(explicit `br` depth — forward-only fallthrough was measured and rejected);
direct calls resolve by name at serialization. The subset boundary is a
HONEST refusal, never a silent skip: `CompilerPipeline` catches
`WasmUnsupportedException` → `WASM002` naming this plan + unit + #776 and
writes NO artifacts (Q7). `main`/IO/strings/collections/records = 15.3+
(D-WASM-03..06). WAT emission stays PLANNED (this section's `--emit=wat`
row predates the slice; the binary is the 15.2 product). Proof:
`WasmScalarE2ETest` 3/3 — emission+validation via `wasm-tools` 1.261.0,
execution via `wasmtime` v49.0.2 (`scripts/provision-wasmtime.sh`, pinned
checksums, host-gated by `assumeTrue`), JVM oracle parity for `add`,
`collatz(27)=111`, `fib(10)=55`; refusal case proves no partial module.
`WasmTargetGateE2ETest` re-pinned to the new truth (wasm IS backend; WASI
still `WASM001`; out-of-subset program → `WASM002`).

**Slice 15.3-stdout LANDED (07/10, lane `192.168.15.101:9092`):**
`Target.WASI` is now an EMITTING WASI-preview1 backend: `main` compiles to
the exported `_start` (command mode), every scalar `println` writes through
the imported `wasi_snapshot_preview1.fd_write`; `Int`/`Long` print via the
emitted `kof.writeInt` itoa helper, `Bool` via `kof.writeBool`. Out-of-slice
programs (string literals, `args`, records/collections) refuse `WASM002`
naming this plan + #776, writing NO artifacts (Q7; proven by
`WasmWasiE2ETest` + `WasmTargetGateE2ETest`). Proof: `WasmWasiE2ETest` 3/3 —
module carries the preview1 imports + `_start` export, validates with
`wasm-tools` 1.261.0, executes under `wasmtime` v49.0.2 with stdout EXACTLY
equal to the JVM oracle of the same source (negative/zero/call-result ints,
bools), exit 0; the `WasmScalarE2ETest` 3/3 (15.2) and re-pinned
`WasmTargetGateE2ETest` 5/5 (WASI IS backend; frontend gap stays `WASM001`
until 15.4) stay green; `kof deploy --target wasi` honestly refuses `WASM001`
(module emits, deploy archive/runtime host not yet — `SelectTargetsTest`/
`CmdDeployTest` unchanged-green). `println(String)`, records, collections,
`args` and the GC-handle runtime land in the NEXT 15.3 slices (GC-handle rule of the plan).

**Slice 15.3b LANDED (07/10, lane `192.168.15.101:9092`):** `println(String)` of a
string LITERAL now emits: the bytes go to a wasm DATA segment (pool based at 1024,
`+1` byte reserved per string for the newline, 4-aligned), the println intercept
pushes `(addr,len)` and the emitted `kof.writeString` helper writes them through
`fd_write` with the trailing `\n`. Proof: `WasmWasiE2ETest` 3/3 — mixed scalar+string
main prints EXACTLY the JVM-oracle stdout (`oi`, `hello kof` added) under wasmtime
v49.0.2, module validates with `wasm-tools`; out-of-slice strings (concat
`var s = "a" + "b"`, `println(args)`) refuse `WASM002` naming plan + #776 with NO
artifacts (Q7); `WasmTargetGateE2ETest` 5/5 re-pinned (its refuse case moved from
string-literal — now EMITTED — to `args`); `WasmScalarE2ETest` 3/3 stays green. String
VARIABLES/concat/records/collections need the heap (D-WASM handle-table shape of the
plan §8/§9) — 15.3c+ (`args`) then GC slices. NOTE 07/10: the 4-module suite is
`4877 run / 3 F` — JavaFX env (documented) + `Av1CoeffsE2ETest` aarch64/riscv64 =
EXTERNAL regression catalogued as `known-bugs` **§625** (bisect lands on `a2f69d2f7`
§620 cross-arg shift; reproduced at remote tip `45d839322` WITHOUT any WASI-lane code;
 owner lane `192.168.15.30:9092`; NOT touched by this lane — collision rule) — **FIXED
 08/10** by that lane (cross/x86 prologue zeroes slots above `paramSlotMax`, `PrologueSlotInitTest`).


**Slice 15.3c-sliceA LANDED (08/10, lane `192.168.15.101:9092`):** String VARIABLES
and CONCATENATION now emit — a `String` value becomes an i32 HANDLE into a bump
heap (`global 0`, mutable i32 initialized `16384`; `[len][bytes]\n` layout, the
`+1` newline reserved at emit time so `println` never re-allocates). Three emitted
helpers drive it: `kof.strLit(addr,len)` copies a DATA-segment literal into a fresh
heap block and returns its handle; `kof.strConcat(a,b)` bump-allocates `la+lb`,
copies both payloads (`copyLoop` — a `block`+`loop` with `br_if`/`br`, `i32.load8_u`
align 0, byte store `addr` pushed before `value`) and returns the joined handle;
`kof.writeStr(handle)` writes `[h]+1` bytes through `fd_write` (reused for every
`println` of a non-literal string). The println intercept pushes `(addr,len)` for
a literal and calls `kof.strLit` for `kof_string_concat`/`String` variables, so
`var s = "a" + "b"; println(s)` and `println("x" + "y" + "z")` print `ab`/`xyz`.
Proof: `WasmWasiE2ETest` 3/3 — the mixed scalar+string+concat main prints EXACTLY
the JVM-oracle stdout (`3/-42/0/55/true/false/14/oi/hello kof/ab/xyz`) under
wasmtime v49.0.2, module validates with `wasm-tools`, exit 0; `WasmTargetGateE2ETest`
5/5 + `WasmScalarE2ETest` 3/3 + `TargetMatrixTest` 10/10 green. `println(args)`/
`args[0]`/records/collections still refuse `WASM002` naming plan + #776 with NO
artifacts (Q7). At sliceA time `args` still refused; it landed the SAME DAY as
15.3c-sliceB below (records/collections remain, then 15.4 flip LAST). MEASURE 08/10: the full 4-module suite at tip `898bc50ab` + this slice = 8F + 2 flakes, ALL external/environmental and stash-proven independent of the WASI lane: §625 `Av1CoeffsE2ETest` (2F, FIXED 08/10 by lane `.30:9092` — zero stale prologue slots) + §627 `KofTestingE2ETest` float-assert (2F, FIXED 08/10 by lane `.30:9092` — interpreter `EQ/NE` + cross `feq.s`) + §628 `JvmLauncherDiagnosticE2ETest` (3F deterministic at the CLEAN tip — the §554 `ExternalArgTighten` breaks the pipe fixtures, owner compiler/interop) + JavaFX env (1F) + `InteropTimeoutE2ETest` load flakes (2F, GREEN isolated). Live queue 3->5 (5->3 after the §625+§627 fixes).


**Slice 15.3c-sliceB LANDED (08/10, lane `192.168.15.101:9092`):** `args` — a
program that touches `args` gets a `kof.readArgs` prologue in `_start`: WASI-preview1
`args_sizes_get(&argc@48,&argv_buf_size@52)` then bump-allocates the pointer TABLE
(`argc*4`) and the NUL byte BUFFER (`sz`) on `global 0`, calls `args_get`, and converts
every `argv[i+1]` (argv[0] = program name, DROPPED for JVM parity) into a heap
KofString handle `[len][bytes]\n` (strlen + `copyInto`), returning an array handle
`[count][handle...]`. `args.length` lowers to `i32.load` of `[arr]` + `i64.extend_i32_s`
(Int = i64, D-WASM-02); `args[i]` lowers to `i32.wrap_i64` of the index plus an
EXPLICIT bounds check (`count <= idx` via `i32.le_u` -> `unreachable`) — out-of-range
is a deterministic trap, NEVER a garbage read (the exception model is D-WASM-03, still
TBD); `println(args[i])` and `args[i] + "x"` ride the 15.3c-sliceA String runtime
(`kof.writeStr`/`kof.strConcat`). Proof: `WasmWasiE2ETest` 5/5 under wasmtime v49.0.2 —
`argsLengthIndexAndConcatMatchTheJvmOracle` compiles the SAME source for JVM and WASI,
runs both with `alpha beta` and asserts byte-equal stdout (`2/alpha/beta/alpha-beta`);
`emptyArgsLengthMatchesJvmAndOobIndexTraps` pins `args.length == 0` with no argv and
asserts a NON-ZERO wasmtime exit for `args[7]` (explicit `unreachable`, measured exit
134); the refusal face re-pinned: `println(args)` (array format) and `for (a in args)`
still `WASM002` naming plan + #776 with NO artifacts. Gate hygiene: the split forced by
`check_500` landed WITH this slice — `WasmBackend` 629 -> 204 (module assembly only),
lowering/dispatcher/Ctx extracted to `WasmLowering` (447), `kof.readArgs` + `copyInto`
live in `WasmArgsRuntime` (192), `WasmStdoutRuntime` 503 -> 329; pure code motion, WASI
battery green after. MEASURE 08/10 (post-split + post-§625-fix tree): full 4-module suite = kof-compiler 4898 run/5F + kof-cli 593/3F — §627 2F + §628 3F = 5F deterministic EXTERNAL (other lanes) + JavaFX-env 1F + InteropTimeout load flakes 2F = 3F environmental; §625 GREEN post the `.30` prologue fix (`6572e6367`, Av1Coeffs 6/6 re-measured here); RingPrivilege GREEN this pass; ZERO WASI failures, battery 23/23 + PrologueSlotInit 2/2.

**Slice 15.3d-increment1 LANDED (08/10, lane `192.168.15.101:9092`):** RECORD ALLOCATION + INT/LONG FIELD ACCESS — a record value is a bump-heap block whose layout is `ClassLayout.build(IRClass)` (the SAME source Native uses: header + 8-byte slots), the receiver is an `i32` handle in the local slot (`lowerMethod` maps a record-typed local/param to `i32`); `WasmLowering` gains `KofNewObject` (bump `global 0` by `totalSize`, stash the handle in a scratch `obj` local, push it), `KofDup` (no-op — the handle the constructor left on the wasm value stack is reused), the record `<init>` (`CONSTRUCTOR` call: pops the argument values in reverse field order into a scratch `v` i64 local and `i64.store`s each at `obj + fieldOffset`), and `KofLoadField` (receiver `i32` handle + `fieldOffset` → `i64.load`). `WasmInstr.Mem` gains the real i64 memory opcodes `LOAD64=0x29`/`STORE64=0x37` (align 3) — the smallest mechanism, not a per-feature hack. **Scope honesty (Q7/R6):** only `Int`/`Long` fields (i64 on the stack) are built; a record with a `String`/`Bool`/`Char`/`Double`/nested-record field, `println(record)` (`toString`), `record == record` (`equals`) and record-in-`+` all require INSTANCE-method lowering (not yet present) and keep refusing `WASM002` naming plan + #776 with NO artifacts — never a silently-invalid module. Proof: `WasmWasiE2ETest` 7/7 under wasmtime v49.0.2 — `recordAllocationAndIntFieldAccessMatchTheJvmOracle` compiles `record Point(Int x, Int y) { var p = Point(1,2); println(p.x); println(p.y) }` for JVM + WASI and asserts byte-equal stdout (`1/2`); `recordToStringAndEqualityStillRefuseHonestly` pins the three still-open faces (toString / equality / a String-field record) to `WASM002` + no-artifacts. NEXT (increment 2): the `toString`/`equals`/record-concat faces land with instance-method lowering; then collections, then 15.4 flip LAST.
**Slice 15.3d-increment2-SLICE-A LANDED (09/10, lane `192.168.15.101:9092`):** `String.valueOf(Int|Long)` AS A VALUE (variable / inside `+`) — `WasmStdoutRuntime.kofIntToStr` formats digits in the SAME scratch window the proven-green `kof.writeInt` uses, then bump-allocates a `[len][digits]` heap handle with the `kof.strLit` pattern (`copyLoop`, +5 block) and returns the handle; `WasmLowering` routes valueOf-by-owner `String` that is NOT immediately `println` to `kof.intToStr` (Int|Long), treats a String receiver as IDENTITY (the JVM `+` desugar re-applies `valueOf` on an existing handle — measured in the emitted `_start`), and refuses everything else `WASM002` with NO artifacts (Q7); `WasmBackend` registers the helper with the string runtime. Proof: `WasmWasiE2ETest` 8/8 under wasmtime v49.0.2 — `stringValuesOfVariablesAndConcatMatchTheJvmOracle` (JVM+WASI same source, byte-equal `3 / -42 / 7! / x100`); WASI battery 26/26. NEXT: slice B = record field widths (Bool/Char/String-handle = i32 slots, Double = f64), slice C = inline `toString`/`equals`/record-concat via `kof.strEq`.
**Slice 15.3d-increment2-SLICE-B LANDED (09/10, lane `192.168.15.101:9092`):** RECORD FIELD WIDTHS + char/bool print parity — `WasmStdoutRuntime.kofWriteChar` now receives the i64 the 15.2 `emitLiteral` actually pushes for `Char` and wraps to an i32 local (the pre-existing `println(char)` module was INVALID — a latent false-green now pinned by `charAndBoolPrintMatchTheJvmOracle`); `WasmLowering` dispatches record `<init>` stores and `KofLoadField` loads by width: Int/Long/Char = i64 (`Mem.STORE64/LOAD64`), Bool/String-handle = i32 (`Mem.STORE/LOAD`), Double = f64 (`Mem.STORE_F64/LOAD_F64` = 0x39/0x2b align 3, new in `WasmInstr.Mem`) with per-function scratch `v32`/`vf64`; `operandTypeBefore` answers the field type so `println(recordField)` registers the right writer; `isStringField` recognizes record `String` fields; `usesStringOps` registers the string runtime for programs holding string literals/handles without any printed string (`Pair("ab", 7)` alone used to encode an unresolved `kof.strLit`); nested-record fields still refuse `WASM002` NO artifacts (`record-string-field` refusal flipped green; `record-nested-field` pinned). Proof: `WasmWasiE2ETest` 10/10 (`charAndBoolPrintMatchTheJvmOracle`, `recordFieldWidthsMatchTheJvmOracle` byte-equal JVM oracle under wasmtime v49.0.2); WASI battery 28/28. NEXT: slice C = inline `toString`/`equals`/record-concat via `kof.strEq`.
**Slice 15.3d-increment2-SLICE-C2 LANDED (09/10, lane `192.168.15.101:9092`):** RECORD `==`/`!=` CONTENT EQUALITY — the frontend `RecordEqualityLowerer` WASI arm desugars to the SAME opaque `kofRecordEq(L,R)` call the JS arm already uses (the plan's own precedent: "one single function call is an opaque expression for all backends"; the loop/`$pc` linearization never gets the cross-block merge it could not model, so it never HAS to). `WasmRecordCode.emitEquals` folds the fields INLINE in one straight-line block — accumulator locals `eqAcc`(handle L)/`eqEq`(handle R)/`eqAnd`(AND-fold) + `eqCmp`/`eqDbl` scratch, every `if` void-bodied so no value ever crosses a label; null semantics = `java.util.Objects.equals` (both null→1, one null→0, else field-wise content); widths: Int/Long/Char `i64.eq`, Bool `i32.eq`, String fields via NEW `kof.strEq` (length check + byte-scan loop over the bump-heap blocks), Double via `i64.reinterpret_f64`+`i64.eq` = JVM `Double.equals` bits (+0.0/-0.0/NaN parity); `!=` (EQ-vs-0 desugar) and the null-0 literal are intercepted as `i32` when the producer is the fold; `record == null` (reference branch of the desugar) = handle-vs-0 `i32.eq/ne`; the explicit `p.equals(q)` still refuses `WASM002` — instance-method dispatch is a separate slice. Bugs the byte-parity caught (Q0/Q5, fixed same commit): wasm `br` to a LOOP label is a CONTINUE — the strEq scan exits at depth 2 (the enclosing `if`), not 1 (infinite loop measured); the fold accumulator doubling as the L handle got clobbered by the seed — roles split `eqAcc`/`eqAnd`; `i32.and` opcode (0x71, an earlier 0x7c was `i64.add`). Gate hygiene: `WasmPrintCode` extracted from `WasmLowering` (622->593, `check_500` rc=0). Proof: `WasmWasiE2ETest` **12/12** under wasmtime v49.0.2 — `recordEqualityMatchesTheJvmOracle` same source JVM+WASI byte-equal across `Point`/`Pair`(String content-not-identity)/`Flag`(Bool/Char)/`Dbl`(bits)/`== null`/`!= null`/`!=`/`!`; `record-equality` refusal FLIPPED GREEN; refusal test renamed+re-pinned to `record-explicit-equals` + `record-nested-field`. WASI battery **30/30**. NEXT: nested-record fields + `Double`/`Double.toString` in record `toString`; then collections; then 15.4 flip LAST.

**Slice 15.3d-increment2-SLICE-C1 LANDED (09/10, lane `192.168.15.101:9092`):** RECORD `toString`/PRINT/CONCAT INLINE — new `wasm/WasmRecordCode.emitToString` expands the synthesized instance method AT THE CALL SITE (the backend has no virtual dispatch yet — the same shape decision as `CompilerRecordSupport`/`RecordEqualityLowerer`, the JVM oracle): `Name[f1=v1, f2=v2]` built from bump-heap handles (`kof.strLit`/`kof.strConcat`), Int/Long via `kof.intToStr`, Bool via new `kof.strBool`, Char via new `kof.strChar`, a String field reuses its own handle; `WasmLowering` routes `println(record)`, `record.toString()`, `String.valueOf(record)` and record-inside-`+` through it (operand type via `operandTypeBefore` over `ctx.flat`), `KofCheckCast` becomes a no-op and wide values stored into `Object` box-slots wrap `i64->i32`; `WasmBackend` closure-skips `toString`/`equals` on record owners, admits `recOk` println types and registers the string runtime + heap when `usesRecords`. **Scope honesty (Q7/R6):** `record == record` keeps refusing `WASM002` with NO artifacts — measured: the JVM `==` desugar merges the field-compare through CONTROL FLOW across labels (ternary + `i64` literals into an `i32` merge), which the loop/`$pc` linearization does not model; the never-wired `emitEquals`/`kof.strEq` scaffolding was DELETED (dead code is a stub, Q7 — it returns in slice C2 WITH the merge model). Gate hygiene: `WasmTypeOracle` extracted from `WasmLowering` (726 -> 569 tolerated; oracle/inference responsibility), `WasmStdoutRuntime` 477 under target. Proof: `WasmWasiE2ETest` **11/11** under wasmtime v49.0.2 — `recordToStringAndConcatMatchTheJvmOracle` (same source JVM+WASI byte-equal `Point[x=1, y=2]` / `v=Point[x=1, y=2]` / `Pair[a=ab, n=7]` / `Flag[c=k, ok=true]`), `recordToStringAndEqualityStillRefuseHonestly` re-pinned to the two still-open faces (equality + nested fields), `charAndBoolPrintMatchTheJvmOracle` stays green (a wrong println wrap added during work was caught by it). WASI battery **29/29**; `check_500` rc=0. NEXT: slice C2 = record `==` with the cross-block merge model; then nested-record fields + Double; then collections; then 15.4 flip LAST.

**Amendment (07/10, lane `192.168.15.101:9092`, 15.1-COMPLETE):** the enum
step was NOT deferred — roadmap TIER 15 defines 15.1 ITSELF as enum+plumbing
(maintainer order `D-WEB-WASI-DEFAULT-0710`, the order that promoted this
plan). `Target.WASM`/`Target.WASI` now EXIST: the canonical bare `wasm`/`wasi`
PARSE as real targets end-to-end, and the honest emitting refusal (WASM001,
naming this plan + unit 15.2 + #776, writing NO artifacts, never a JVM
fallback) lives in `TargetMatrix.validate`/`CompilerPipeline.lowerAndEmit`
(`WasmTargetGateE2ETest` 5/5). The long solecisms keep `.30`'s string gap
verbatim; both `.30` tests were re-pinned to this truth (solecism coverage
preserved).

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
