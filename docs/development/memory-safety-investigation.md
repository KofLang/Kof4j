[English](memory-safety-investigation.md) | [Português](memory-safety-investigation.pt_BR.md)

# Memory safety — Fase 0: investigation of Kof's implicit memory model (D-MEMORY-SAFETY)

> **Status: Fase 0 deliverable** of `memory-safety-plan.md` (maintainer
> 25/09, `DECISIONS.md` §`D-MEMORY-SAFETY`). Research only — zero compiler
> edits. Every claim below carries file:line evidence (paths relative to
> `kof-compiler/src/main/java/dev/kof/compiler/`). Investigated 25/09 on tip
> `9dc5922fc` (beta-0.5.0).

## 1. Current state — what the compiler actually does today

### 1.1 Variable representation (per stage)
AST: `VarDeclStmt(type, name, init)` — `val`/`var` carried as **strings** in
the type field (`parser/StatementParser.java:409-432`). Semantic symbol:
`LocalVariableSymbol(name, type, index, isVal)` (`SymbolTable.java:147`,
built at `StatementAnalyzer.java:100,148`). IR: `KofStoreLocal/KofLoadLocal`
+ `IRLocalVariable`; sequential slot allocation (+1, +2 for long/double)
(`StatementLowererLocalBoxing.java:135-137`). Backends: JVM = the IR index IS
the local slot (`JvmOpEmitter.java:70-75`; `Nullable(primitive)` → ASTORE,
1 slot); native x86-64 = **one qword stack slot per local, everything** —
long/double included (`nat/NativeMethodEmitter.java:232-239`), with DWARF
mapping the same slots (`NativeMethodEmitter.java:158-174`); JS = `let/const`
predecl (`js/JsEmitter.java:107-110`).

### 1.2 Value vs reference; copy vs share
Decided by the type model, not per backend: `Type.PrimitiveType`
(bool/byte/short/int/long/float/double/char) copies (`Type.java:7-17`);
everything else erases to reference (`CompilerTypeSupport.java:89-92`).
`String` is a ClassType (reference; immutable values, shared refs — concat
allocates a new KofString, `runtime/RuntimeStringBase.java:116-156`).
**There is no deep copy on assignment anywhere**
(`ExpressionAssignmentLowerer.java:38,68,124,168,178` — emit-value + store).
The only mechanical copies: FFI copy-in/copy-back, native list-grow buffer
copy (`RuntimeList.java:44-66`), JVM `Buffer.bytes()` defensive clone
(`jvm/JvmBufferRuntime.java:25-26`).

### 1.3 Escape analysis: NONE, on any backend
The only IR optimizer does constant folding / dead stack-effect /
reachability / jump threading (`backend/Optimizer.java:66-110`). Every
object/record/closure/box/Handle is `new` on JVM, `kof_alloc` on native
(`nat/NativeOpHelpers.java:36-53`).

### 1.4 Mutability (val/var)
Enforced at semantic analysis, not parsing: `SEM037` rejects `val`
re-assignment (`SemAssignmentAnalyzer.java:39-46`; for-update via
`StatementAnalyzer.java:239-242`). Family: `SEM038` record-component write,
`SEM054` `l[i]=v` on List, final-field write guard
(`MemberCallTyper.java:487-495`). **`val` freezes the BINDING, not the
object**: `val l = listOf(1); l.add(2)` is legal today (no deep-immutability
concept).

### 1.5 Closure capture: by value, boxed only when mutated
Synthetic `Lambda<N>` class; each capture = private final field set once by
the constructor (`CompilerLambdaClass.java:53-57,95-101,172-186`); creation
site pushes the capture's **current value** (`CompilerCaptures.java:21-30`).
A pre-pass marks locals mutated inside the lambda and **re-boxes them** into
a shared heap `Box` (`CompilerCaptureScanner.java:454-467,510-524`;
`CapturedVarBox.java:15-56`; gate `StatementLowererLocalBoxing.java:37-39`).
Consequence: un-boxed captures are **snapshots**; mutation is visible only
through the box path. JS transpiles the same shape (`js/MethodCtx.java:56-98`)
— capture semantics are uniform across backends.

### 1.6 GC model (native x86-64): conservative mark-sweep
Roots = (a) main thread's whole stack, qword scan from collector `rsp` down
to `kof_main_stack_bottom` (64 MB cap, 4 KB fallback —
`runtime/RuntimeGc.java:28-57`); (b) `.data`+`.bss` statics from
`kof_heap_root_start` to `_end` (`RuntimeGc.java:59-75`); (c) all 15 GPRs,
because `kof_gc_collect_now` blanket-spills them (`RuntimeGc.java:262-311`,
§260 fix). Mark accepts any 8-aligned value in `[kof_heap_low,
kof_heap_high)` hitting a live gc-list block, then scans every payload qword
conservatively (`RuntimeGc.java:86-131,143-219`). **Worker-thread stacks are
NEVER roots** — auto-collect is permanently disabled after the first `spawn`
(`RuntimeMemory.java:127-138`, gate also in `RuntimeConcurrency.java:202-212`).

### 1.7 Native allocation/free
Header 32 B before payload: size / free-next / gc-next / flags (mark bit0,
free-list bit1) (`RuntimeMemory.java` alloc prologue; `kof_init_object`
`RuntimeMemory.java:12-21`). Alloc: futex-locked first-fit (1M-node scan
cap) → on exhaustion ONE `kof_gc_collect_now` (unless any spawn happened) →
`mmap` grow (`RuntimeMemory.java:47-203`). `kof_free` exists but is called
only by 4-5 runtime internals (log ring, observability tables, channel
nodes) — **no RAII, no deterministic user-object free**; post-frame garbage
is reclaimed only by a GC cycle — and after the first spawn, effectively
**never** (leaks by design, documented in the runtime).

### 1.8 FFI ownership
JVM: confined `Arena.ofConfined()` per call, closed in `finally`; arrays
copy-in; `Buffer(U8)` INOUT copy-in+copy-back; `char*` returns copied to
String before the arena dies (`jvm/JvmFfiRuntime.java:126-191,320-363`).
The written rule: "a vida do buffer é da linguagem, nunca malloc/free do
programador" (`JvmFfiRuntime.java:135-138`). Native: same policy stated at
`nat/NativeFfiCall.java:33-36` (string return copied, C buffer never
free'd), but **array writes are discarded on native** (`NativeFfiCall.java:422-470`)
— a declared divergence from the JVM Buffer copy-back face.

### 1.9 Nullability model
`Type.NullableType(inner)` (`Type.java:56-57,103-106`). JVM: reference-T? =
plain null; primitive-T? **boxed** (ASTORE, #278/D-NULL-INTENT —
`JvmLiteralEmitter.java:295-300`, `StatementLowererLocalBoxing.java:26-36`).
Native: null = 0 sentinel (`NativeOpHelpers.java:83`); primitive-T? keeps the
**unwrapped representation** with 0-default (declared fase-2 divergence,
`StatementLowererLocalBoxing.java:21-24`). JS: null/undefined coalesced in
runtime helpers (`js/JsRuntimeCore.java:243-246,288`). Narrowing: flow-
sensitive only for `x != null` (redefines the symbol in a child scope,
`SemNarrowing.java:20-47`; `||` does not narrow, `:39`). Misuse codes:
SEM049 (deref without narrowing), SEM048 (`= null` literal).

### 1.10 Aliasing: pervasive and untracked
Every non-primitive assignment/param/return copies the pointer only →
`var b = a` (List/Map/record/Buffer/array) aliases on ALL backends; mutation
through one is visible through the other (JVM ArrayList identity
`jvm/JvmOpCollections.java:95-146`; native shared list header
`RuntimeList.java:15-66`; `Buffer.data` shared `jvm/JvmBufferRuntime.java:14-23`).
No alias/points-to analysis exists anywhere; the only identity logic is the
`==` dispatcher (content-equals or pointer-compare fallback,
`JvmOpEmitter.java:503-522`; `NativeClassMeta.java:231-255`).

### 1.11 Resource lifecycle: explicit close only
Web: `kof_web_close` closes the ServerSocket; apps live in a static registry
forever otherwise (`jvm/JvmWebCoreRuntime.java:496-507`). DB: static map,
closed only by `kof_db_close` (`jvm/JvmConfigRuntime.java:215,260-296`;
native `RuntimeDb2.java:293`, `RuntimeDb4.java:34`). Files: per-call whole-
file IO, handles inside helpers (`jvm/JvmRuntimeIo.java:26-130`). No
finalizers/Cleaners anywhere — forgetting `close()` leaks the underlying
resource for the process lifetime (the GC collects only the wrapper).

### 1.12 Concurrency (spawn/await)
JVM: virtual thread + `CompletableFuture`; `await` returns the SAME
reference the worker produced — no copy at the boundary
(`jvm/JvmRuntimeCore.java:76-170`). Native: 56-B handle + trampoline;
`handle->result` is a raw qword — the pointer is shared, not copied
(`RuntimeConcurrency.java:190-279,297-329`); captures cross by box or
snapshot (§1.5); `kof_spawn_join_all` guarantees no orphan task.

## 2. Known-bug ledger — the memory-adjacent history (§ sweep)

Closed family "conservative GC missed a root": **§503** (unaligned `.quad`
static root invisible → sweep freed live buffers; the `.balign 8` law),
**§260** (collect with temporaries live in registers → blanket GPR spill),
**G-6b/#113** (static-root range + stack-scan range originally too narrow).
Corruption family: **§292** (overflow onto the next block's 32-B header —
allocator corruption under free pressure), **§252** (stack-slot aliasing of
a cached size vs callee return address), **§117** (TID-hash collision →
cross-worker cancel leak), **§286** (lost-cancel race → Dekker protocol),
**§129** (throw in worker longjmp'd into the main thread's frame → per-TID
exception chains). Leak family: **§300** (JS re-render DOM leak). Races:
**§291/§256(b)/§364**. No OPEN entry names use-after-free/double-free/
dangling — the language-level class is prevented by design (GC never hands
freed memory back while a conservative root can see it), but §503/§260 prove
the safety rests on **hand-maintained emitter discipline**, not on a checked
invariant.

## 3. Fragile points (where the implicit model can bite)

1. **Worker stacks are never GC roots; collect is off forever after the
   first spawn** → concurrent native programs grow monotonically (leak by
   design). Any future "re-enable collect with workers alive" reopens the
   §260/§291 family.
2. **Static-root safety = hand `.balign 8` discipline** — nothing in the
   emitter enforces it; the §503 class is structural (any new runtime file
   can silently un-root itself).
3. **Conservative scan over-retains garbage** (stale words of popped frames)
   — leak-safe, never corruption-safe against future precision changes.
4. **Box-only mutable capture**: a capture the scanner fails to classify
   (new construct, shadowing) silently becomes a stale snapshot (the §253
   face-B native SIGSEGV family).
5. **Untracked aliasing + FFI copy-back into a shared `Buffer.data`**: two
   variables aliasing one Buffer while one is handed to C = in-place
   mutation with zero compiler awareness.
6. **`Nullable(primitive)` has two representations** (boxed JVM/JS vs
   unwrapped-with-0-default native) — a standing generator of null-vs-0
   parity bugs (§267/§279/§365/§368 family).
7. **`kof_free` is nearly unused and not lifecycle-integrated** — blocks
   stay gc-listed after free; correctness rides solely on flag bit1.
8. **No resource finalizers** — `db`/`web`/ffi resources leak on a missed
   close; nothing ties resource lifetime to scope.

## 4. Answers to the front's core questions (preliminary, for Fase 1)

- **Can use-after-free happen in Kof today?** At the language level, no
  (GC-managed heap; freed blocks stay unrooted-but-valid until reuse). At
  the boundaries: FFI returns copy (safe), but the native array-writes-
  discarded face means data silently vanishing, not dangling.
- **Can dangling references happen?** No — there are no user references to
  interior memory; the interior-pointer family is runtime-internal only
  (§252 class).
- **Can double-free happen?** Only inside the runtime (none open).
- **Can accidental data races happen?** Yes at the model level: `spawn`
  shares references (JVM) / qwords (native); two workers aliasing one List
  is unsynchronized on native (no locks in RuntimeList) — today the only
  guard is discipline + `join_all`.
- **What is the lifetime model?** Implicit: GC-managed, conservatively
  rooted; scopes have no memory meaning beyond slot reuse; closures extend
  lifetime by owning captures; resources ignore it entirely.
- **Is escape analysis needed?** Not for safety — for the native leak-by-
  design cost and for future stack allocation of non-escaping closures/boxes.

## 5. Compatibility and constraints for the spec (Fase 1 inputs)

Every guarantee must be **additive** (freeze rule 2): today's programs keep
compiling and running. The null safety system is untouchable (brief §1);
`val`/`var`, `==`, evaluation order, exceptions-as-String are frozen
(rule 6). The Simplicity Law (rule 11) forbids lifetime-annotation
ceremony: the model must deliver guarantees through **existing surface**
(ownership inference, diagnostics at the boundary constructs: FFI calls,
spawn captures, aliasing-sensitive stdlib calls, resource close) or through
**opt-in named boundaries**, not through new obligatory syntax. Cross-target
(D-FULL-PARITY-050): any semantic decided here must land with the parity
ledger row filled (JVM/Native/JS + golden).

## 6. Next steps

1. **Maintainer review of this document** (Fase 0 gate per the plan).
2. Fase 1: `docs/spec/memory-safety.md` — formalize Ownership/Lifetime/
   Borrowing/Aliasing/Mutability/Move/Copy/Clone/Drop/Escape/Closure
   Capture/Concurrency/FFI/Unsafe Boundaries against the real surface
   documented here, each rule with allowed/forbidden/sync-required/
   compile-time/runtime classification.
3. Compiler-infrastructure phase only after the current queue closes
   (brief constraint) — candidates surfaced by this investigation:
   an emitter-side alignment invariant test (kills the §503 class
   mechanically), a capture-scanner exhaustiveness guard, a resource-
   lifetime study for `use`-style scoped close.
