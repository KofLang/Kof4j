[English](memory-safety.md) | [Português](memory-safety.pt_BR.md)

# Memory Safety Specification — Ownership, Lifetime, Borrowing, Aliasing (D-MEMORY-SAFETY)

> **Status: Fase 2 CLOSED 26/09** (slices 1–4, `dev.kof.compiler.memory`, model test 8/8,
> BT success `9bcddfe90`) — **Fase 3 UNLOCKED 26/09**: the O-02×N-02 decision
> request was resolved by `D-COMPLETE-FIRST` (re-express O-02 without a null literal —
> analysis pass + emission across the 4 targets as one complete package). Fase 1 CLOSED/accepted 25/09 (option A); Fase 2
> unlocked by the maintainer 26/09 (chat: "fase 2 destravada"). `DECISIONS.md` §`D-MEMORY-SAFETY`.
> Based on `docs/spec/memory-safety-investigation.md` (Fase 0, CLOSED 25/09).
> This document formalizes the memory model against the REAL Kof surface.

---

## 1. Foundational Model

### 1.1 The Memory Contract

Kof programs execute on managed runtimes (JVM, JS, Script) or a freestanding
conservative GC runtime (Native x86-64, RV32I, Cortex-M3). The memory contract
is:

| Property | Guarantee |
|---|---|
| **Allocation** | Every object/record/closure/array/box is allocated by the runtime (`new` on JVM/JS, `kof_alloc` on Native). |
| **Deallocation** | Automatic via GC (conservative mark-sweep on Native; generational on JVM; browser GC on JS). No user-facing `free`/`drop`. |
| **Reachability** | An object is live if reachable from GC roots (stack, static data, GPRs on Native; GC roots on JVM/JS). |
| **Move/Copy** | Primitive types (`bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`) are **copied** by value. All other types (ClassType, FunctionType, Nullable, Array, List, Map, Set, Buffer, Handle, String) are **reference-semantic** — assignment copies the pointer only. |
| **Mutability** | Enforced at the binding (`val` vs `var`) by semantic analysis (`SEM037`, `SEM038`, `SEM054`). The binding is frozen, not the object: `val l = listOf(1); l.add(2)` is legal. |
| **Nullability** | `T?` is a distinct type. `val x: T? = null` legal. Deref without narrowing → `SEM049`. `x = null` literal → `SEM048` (forbidden). Flow-sensitive narrowing on `x != null` only. |

> **Non-goal**: no deep immutability, no transitive const, no linear/affine types,
> no borrow-checker à la Rust. The model delivers guarantees through existing
> surface + opt-in boundaries.

---

## 2. Ownership

### 2.1 Definition

Every object has exactly one **owner** at any time. The owner is the scope
or entity responsible for the object's lifetime end (deallocation).

| Owner Kind | Lifetime End | Examples |
|---|---|---|
| **Scope** | End of the block/function where the owner binding was declared | `val x = Object()` — owned by the block |
| **GC Root** | When the root becomes unreachable (conservative on Native; precise on JVM/JS) | Static fields, stack slots, GPRs |
| **Container** | When the container is collected / cleared | `List<T>` owns its elements; `Buffer` owns its backing array |
| **Closure** | When the closure is collected | Captured variables (boxed if mutated) |
| **FFI** | JVM/JS: confined arena closes at call end; Native: no release surface today (`FFI001` refusal, #651 route) | `Arena.ofConfined()` on JVM; host bridge per call on JS |

### 2.2 Ownership Rules

| Rule | Allowed | Forbidden | Diagnostic | Classification |
|---|---|---|---|---|
| **O-01** Single owner | Each object has exactly one owning scope/container | Two scopes claiming ownership of same object without transfer | `MEM001` | Compile-time |
| **O-02** Transfer | Explicit move: `var a = b; b = null` (source nulled) | Implicit transfer without explicit nulling of source | `MEM002` | Compile-time |
| **O-03** Container ownership | `list.add(x)` → list owns `x`; `list.clear()` releases | Container holding reference after clear without nulling elements | `MEM003` | Compile-time |
| **O-04** Closure ownership | Closure owns its captures; boxed captures shared | Capturing un-boxed variable that escapes closure | `MEM004` | Compile-time |
| **O-05** FFI ownership | Arena/buffer lifetime explicit; no implicit ownership transfer | FFI function returning owned pointer without explicit contract | `MEM005` | Compile-time + Runtime |

> **Note**: Ownership is not a type qualifier in Kof. It is a semantic property
> inferred from scope structure and explicit transfers. The compiler tracks
> ownership to emit diagnostics at boundaries (FFI, spawn, container mutation,
> resource close).

---

## 3. Lifetime

### 3.1 Definition

An object's lifetime is the interval from allocation to deallocation. In Kof,
lifetimes are **managed** by the GC, not by scope brackets. The key distinction
from Rust/C++: lifetimes are not part of the type system; they are a runtime
property made visible through diagnostics at escape points.

### 3.2 Lifetime Rules

| Rule | Allowed | Forbidden | Diagnostic | Classification |
|---|---|---|---|---|
| **L-01** No dangling | GC ensures no dangling pointers (conservative on Native) | User code creating interior pointers that outlive object | `MEM010` | Runtime (conservative) |
| **L-02** No use-after-free | GC never reclaims reachable objects | Explicit `kof_free` + continued use (runtime only) | `MEM011` | Runtime |
| **L-03** No double-free | `kof_free` called at most once per allocation | `kof_free` called twice on same pointer | `MEM012` | Runtime |
| **L-04** Escape awareness | Closure captures extend lifetime; scope exit does not end lifetime of heap objects | Returning interior pointer from FFI without lifetime annotation | `MEM013` | Compile-time |
| **L-05** Resource lifetime | Resource handles (DB, Web, File, FFI) must be explicitly closed | Implicit close on scope exit | `MEM014` | Compile-time |

> **Critical**: Kof does **not** have destructor semantics (`Drop`/`__del__`).
> Resource cleanup is explicit (`close()`, `release()`, `close()`). The GC
> only reclaims memory.

---

## 3. Borrowing & Aliasing

### 3.1 Borrowing Model

Kof uses **shared immutable aliasing** by default. There are no exclusive
borrows (`&mut`), no borrow checker, no lifetime parameters. The model:

- **Shared aliasing is pervasive and always allowed**: `var a = listOf(1); var b = a` — both alias the same list.
- **Mutation through one alias is visible through all aliases**: `b.add(2)` → `a` sees it.
- **No exclusive access guarantees**: No `&mut`, no borrow checker.
- **Mutation safety** is the programmer's responsibility at aliasing-sensitive boundaries:
  - FFI calls that write through a buffer (`MEM020`)
  - Spawn with shared mutable state (`MEM021`)
  - `List`/`Buffer` mutation through shared alias (`MEM022`)

### 3.2 Borrowing Rules

| Rule | Allowed | Forbidden | Diagnostic | Classification |
|---|---|---|---|---|
| **B-01** Shared aliasing | `var a = listOf(1); var b = a` | — | — | — |
| **B-02** Mutation through alias | `b.add(2)` visible in `a` | — | — | — |
| **B-03** FFI borrow | `kof_ffi_call(ptr)` where C may write | Passing same `Buffer` to two concurrent FFI calls without sync | `MEM020` | Compile-time + Runtime |
| **B-04** Spawn alias | `spawn { a.add(1) }` + `a.add(2)` in main | Unsynchronized concurrent mutation without `join_all` | `MEM021` | Compile-time + Runtime |
| **B-05** Stdlib aliasing | `list.add(x)`; `buffer.write(bytes)` | Mutation during iteration without explicit copy | `MEM022` | Compile-time + Runtime |
| **B-06** Closure capture | By-value snapshot (immutable) or boxed (mutated) | Capturing mutable local without box when escaping | `MEM023` | Compile-time (unconstructible — see note 28/09) |

> **Note**: No exclusive borrow means no data-race freedom guarantee at the
> type level. Data races are prevented by discipline + `join_all` + explicit
> synchronization primitives (channels, futures). The compiler emits
> `MEM020`/`MEM021` at aliasing-sensitive stdlib/FFI/spawn boundaries.

> **B-04 scalar (28/09, #660/`D-MEM021-SCALAR`):** `MEM021` covers the ESCALAR
> capture too — the parent re-assigning/incrementing a captured local after
> `spawn`, with no `await`/`join_all` between, is a compile-time ERROR (the
> worker write forces the representation box, so parent and worker share the
> slot; measured silent race `202`/`101` before the fix). Read-only capture
> stays silent (by-value, no box). `MemorySafetyE2ETest` 46/46.

> **B-06 verification (28/09, #658/#659):** the lowering boxes every mutated
> capture by construction (`mutatedCapturedNames` → `CapturedVarBox`), so the
> forbidden shape (unboxed mutating capture escaping) is unconstructible —
> `MEM023` has NO compile face today (precedent O-03/`D-MEMORY-CLEAR`: the
> guarantee is proven by test, no diagnostic invented). Pinned by the 4-target
> batteries: `LambdaE2ETest` (closure faces) + `SpawnE2ETest` (async/return faces,
> including MUTATED capture (`spawn { n = n + 1; return n * 2 }` → `44`) and
> child→parent visibility through join (`println(await h); println(n)` →
> `44/22`), added 28/09 after the independent verifier measured that read-only
> capture never exercises the box: JVM lowers it as `LambdaTask0.<init>(I)`,
> by value; `44` alone would also pass a by-value snapshot).

---

## 4. Mutability

### 4.1 Binding Mutability (val/var)

| Rule | Allowed | Forbidden | Diagnostic |
|---|---|---|---|
| **M-01** `val` binding freeze | `val x = 1; x = 2` → `SEM037` | — | `SEM037` |
| **M-02** Record component write | `val r = R(1); r.f = 2` → `SEM038` | — | `SEM038` |
| **M-03** List mutation through `val` | `val l = listOf(1); l.add(2)` | — | — |
| **M-04** List mutation through `val` element | `val l = listOf(R(1)); l[0].f = 2` → `SEM054` | — | `SEM054` |

> **No deep immutability**: `val` freezes the binding, not the object graph.
> This is a deliberate design choice (Simplicity Law).

---

## 5. Move, Copy, Clone, Drop

| Concept | Kof Semantics |
|---|---|
| **Move** | `var a = b; b = null` — explicit transfer, source nulled. Not a language operator. |
| **Copy** | Primitive types: always copy. References: pointer copy only. No deep copy operator. |
| **Clone** | `x.clone()` where `x` implements `Clone` (stdlib protocol). Not automatic. |
| **Drop/Destructor** | **Does not exist**. No destructors, no finalizers. Resources: explicit `close()`. |

---

## 6. Escape & Capture

### 6.1 Closure Capture

| Rule | Behavior |
|---|---|
| **E-01** Capture by value | Un-mutated locals: captured by value (snapshot) |
| **E-02** Capture by box | Mutated locals: re-boxed into shared heap `Box` |
| **E-03** Escape | Closure owning captures extends their lifetime until closure collected |
| **E-03** Capture scanner | Pre-pass marks mutated locals (`CompilerCaptureScanner.java`) — exhaustiveness required |

---

## 7. FFI Ownership Boundaries (phase 5 ownership table, `D-MEMORY-SAFETY`; EN×PT in par)

Everything that crosses a language boundary is a VALUE COPY with a confined lifetime — Kof never transfers ownership of its heap to an external runtime, and external memory is never retained past the call unless the Kof side copies it. Cells are MEASURED against the tree and pinned by the behaviour matrix (`docs/backend-parity.md` §C-FFI row, 165), not remembered. The two-branch table this replaces carried two stale claims — "Buffer INOUT discarded on native" (the shipped FFI face was a decl-line `FFI001` refusal; #651 fatia A1 landed the x86-64 `Buffer(U8)` namespace/print surface and fatia A2 landed the x86-64 `extern` `B` face — cross stays `FFI001`) and "Native: explicit `kof_ffi_release`" (the symbol exists only as a model concept, `OwnerKind.java:29-30`, with no surface in the tree — measured 28/09) — corrected here per the #665 documentation-truth practice and issue #670.

| Boundary | JVM | Native | JS host runner | Python (`kof.interop`) |
|---|---|---|---|---|
| **Scalar (Int/Long/Float/Double/Bool)** | by value across FFM (`FfiE2ETest`) | by value, direct `call sym@PLT`, link-by-use — no dlopen, no ownership (#431, §369) | by value via host bridge, byte-for-byte with JVM (`KofJsFfiBridge`, `FfiE2ETest` JVM↔JS) | fresh typed-JSON value per call (`interop-py-host.kf`, `json.decode` by face type) |
| **String** | copy at the boundary, both directions | copy at the boundary; return = boundary copy (`kof_ffi_from_cstr`, payload off 24, NULL→NULL) | copy | JSON string value |
| **Buffer(U8) in/INOUT** | copy-in + copy-back on confined `Arena.ofConfined()` close (`BufferFfiE2ETest` 5/5) | **x86-64 `extern` `B` binds since #651 fatia A2** — payload pointer `obj+24`, the C write is the copy-back (`BufferFfiE2ETest#bufferInoutCopyInCopyBackNativeParity`); the namespace/print surface binds via fatia A1 (`BufferE2ETest#allocBytesAndPrintlnNativeParity`); cross riscv64/aarch64 **also binds since #651 fatia B** (`BufferFfiE2ETest#bufferParamCrossBindsAndMatchesJvm`, JVM==riscv64==aarch64) | copy-in + copy-back parity (`BufferFfiE2ETest#bufferInoutCopyInCopyBackJsParity`, `JsRuntimeBuffer`) | no shared buffer — values only |
| **record / scalar array** | by value: `record` arg + return, `T[]`→ptr copy-in, out-buffer (`FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, 3.8b) | x86-64: struct param/return by value incl. sret (> 16 B), `T[]` copy-in with NO write-back (`FfiNativeArrayE2ETest`); cross: `T[]` copy-in (30/09, face 1, `kof_ffi_pack_array`), `String[]`→`char**` copy-in (face 2, `kof_ffi_pack_str_array`) memory-path struct RETURN by value (face 3, sret; arch-aware result pointer `a0` riscv64 / `x8` aarch64) and the >16 B by-value struct **param** (face 3, BYREF pointer `a0`/`x0`) — all JVM==x86-64==riscv64==aarch64 (`FfiNativeArrayE2ETest`/`FfiNativeStringArrayE2ETest`/`FfiNativeStructReturnE2ETest`/`FfiCrossStructParamE2ETest`); callbacks on cross → `FFI001` decl-line | non-scalar → `FFI002` (JS struct bridge pending, `CompilerFfiBinding`) | n/a (typed JSON face only) |
| **Callback (upcall)** | real C function pointer, synchronous/non-escaping, confined-arena contract (R3.4, `JvmFfiCallbackE2ETest`) | `FFI001` — honest remainder of §369 | host bridge builds the same upcall, byte-for-byte (`upcallStub`, R3.4-C3) | n/a |
| **Owned pointer returned by C / opaque handle** | copied before arena close; not retained | `FFI001` at the decl line (opaque handles out of scope, §61 remainder) | copied (host bridge) | n/a |
| **Explicit release** | none needed — confined `Arena.ofConfined()` closes at call end | **no release surface exists**: `kof_ffi_release` is a model concept (`OwnerKind.java:29-30`) and a D6-5 proposal (`docs/ffi-abi-structs.md`), never implemented — measured 28/09, declared here instead of claimed | none — host bridge allocates per call (R3.4) | none — each call is a fresh child session; Python-side globals do not survive across calls |

- **Kof↔Python is NOT shared memory.** The engine is pure Kof over `process.spawn` + typed `json.decode` (3-line protocol, `interop-py-host.kf` header): every value crosses as a fresh JSON payload, so there is nothing to own or release; failures are named `INTEROP004/006/007/008`, never silence; a live-session handle is rule-6 territory, undecided.
- **Kof↔Rust has NO surface today — an ABSENCE, not a gap.** `kof-c-compiler` is a fixture/subset compiler (`KofCCompiler` header: "native-only C subset compiler... no JVM target"), not an exporter; Kof emits no consumable library ABI and no doc claims one. A Rust column joins this table only when a real need lands — inventing support would be a hallucinated contract.
- **LANDED 29/09 (phase-5 unit 2, `#667`/`#668` decisions A).** The `MEM020` (B-03) **compile** face is now shaped: an `extern` whose parameter is `Buffer(U8)` INOUT writes that buffer, so two unsynchronized writes (worker×parent or worker×worker) are reported as a compile-time `MEM020` ERROR over the existing `OwnershipPass` (`FfiCaptureSpawnE2ETest#concurrentFfiBufferWriteParentAndSpawnIsMem020` + `#concurrentFfiBufferWriteTwoSpawnsIsMem020`; the awaited single write stays clean — `#singleAwaitedFfiBufferWriteIsNotMem020`). Script×`extern` no longer dies raw at runtime with `KofRuntime.kof_ffi/4`: it is refused at the declaration line reusing `FFI001` (`ScriptTargetTest#externIsRefusedOnScriptFfi001`, integration `FfiCaptureSpawnE2ETest#ffiRefusedOnScriptEvenInsideSpawn`). Because the refusal precedes any lowering, the `MEM020` face is **unreachable on Script by construction** (no `extern` ⇒ no FFI write) — the correct Script pin is `FFI001`, never `MEM020`.
- **LANDED 30/09 (phase-5 unit 4, `D-MEM030-BORROW-RUNTIME`).** The `MEM020` (B-03) **runtime** half is now shaped: the `Buffer(U8)` runtime object carries an exclusive **writable-borrow** flag; an `extern` INOUT write acquires it and releases it after the downcall, so a **second concurrent writable borrow** raises a runtime `MEM020` while a single/awaited writer stays clean. The primitive is on **all six** faces (JVM, JS, Native x86-64, riscv64, aarch64, Script). Per the maintainer's follow-up decision, the **negative** case is proven by execution where preemption exists (JVM virtual threads; native pthreads x86-64/riscv64/aarch64) and by documented **structural unreachability** on JS (its `spawn` is `async`/`await` — cooperative, single-threaded) and Script (no `Buffer`/`extern` surface: `FFI001`). Proof: `BufferRuntimeBorrowE2ETest` (positive controls on all faces; x86-64 negative raises exactly one `MEM020`; the cross negative race is temporarily blocked by `known-bugs §545`, a pre-existing native/cross `spawn × extern` SIGSEGV independent of this front).

---

## 8. Concurrency & Memory

| Rule | Description |
|---|---|
| **C-01** Spawn shares references | `spawn { use(a) }` — `a` shared between main and worker |
| **C-02** `join_all` | Guarantees no orphan tasks; no copy at boundary |
| **C-03** Data races | Possible if two workers alias same mutable object without sync |
| **C-03** Guards | `MEM021` at `spawn` with shared mutable state |
| **C-04** Worker stacks | **Never GC roots** on Native — collect disabled after first `spawn` |

---

## 9. Resource Lifecycle

| Resource | Lifetime | Close Required | Diagnostic |
|---|---|---|---|
| `Web` server | Process or explicit `kof_web_close` | Yes | `MEM014` |
| `DB` connection | Process or explicit `kof_db_close` | Yes | `MEM014` |
| `File` handle | Per-call (whole file) | Auto per-call | — |
| `FFI` buffer | Confined arena (JVM/JS); Native has no buffer face yet (`FFI001`, §7) | Yes | `MEM005` |

> No finalizers, no `Cleaner`, no finalizers. Leaking `close()` leaks the
> underlying OS resource for the process lifetime.

---

## 10. Nullability × Ownership

| Rule | Description |
|---|---|
| **N-01** `T?` nullable | Deref without narrowing → `SEM049` |
| **N-02** `= null` literal | Forbidden → `SEM048` |
| **N-03** Narrowing | Only `x != null` narrows; `||` does not narrow |
| **N-04** Primitive nullable | JVM/JS: boxed; Native: unwrapped with 0 default (divergence) |

---

## 11. Safety Matrix (Fase 1 Gate)

The following matrix maps each bug class to its prevention mechanism:

| Bug Class | Prevention | Diagnostic | Backend |
|---|---|---|---|
| Use-after-free | GC (conservative on Native) | `MEM011` (runtime) | All |
| Double-free | GC / single `kof_free` | `MEM012` (runtime) | All |
| Dangling reference | GC (conservative) | `MEM010` (runtime) | All |
| Invalid lifetime escape | Escape analysis at boundaries | `MEM013` (compile-time) | All |
| Use-after-move | Explicit nulling on transfer | `MEM002` (compile-time) | All |
| Mutable aliasing data race | Programmer discipline + `MEM020/021` | `MEM021` compile (D-MEM021-SCALAR) + `MEM020` compile (#668) **and runtime** (#668 + `D-MEM030-BORROW-RUNTIME`, 30/09) | All reachable (Script: `extern`/`Buffer` refused `FFI001`; JS: cooperative `spawn` ⇒ negative is structural N/A) |
| Null deref | Nullability + narrowing | `SEM049` (compile-time) | All |
| Resource leak | Explicit close | `MEM014` (compile-time; WARNING) | All (Script surfaces it via `Result.warnings()`, `D-SCRIPT-WARN-SURFACE`) |
| FFI ownership confusion | Confined arena per call (no release surface exists — `kof_ffi_release` is a model concept, spec §7, measured 28/09) | `MEM005` — model rule, no emission surface today | JVM + Native |
| Resource leak (DB/Web) | Explicit close | `MEM014` (compile-time; WARNING) | All (Script surfaces it via `Result.warnings()`, `D-SCRIPT-WARN-SURFACE`) |

> **WARNING-class diagnostics on Script:** `MEM014`/`MEM022` fire in the shared frontend on all targets. On Script, `interpret()` used to discard WARNINGs (only ERRORS escaped) — measured 29/09, decision request #678; **RESOLVED 29/09 (`D-SCRIPT-WARN-SURFACE`, option A): the interpreter now exposes them via `KofInterpreter.Result.warnings()` and the CLI/`KofScript` print them to stderr like the compile path.**

> **Simplicity Law check**: No lifetime annotations in user code. All rules fire
> at existing surface boundaries (FFI, spawn, stdlib mutation, resource close,
> assignment with explicit null).

---

## 12. Implementation Roadmap (Post-Spec)

| Phase | Work | Status |
|---|---|---|
| **0** Investigation | `docs/spec/memory-safety-investigation.md` | ✅ CLOSED 25/09 |
| **1** Specification | `docs/spec/memory-safety.md` (this doc) | ✅ CLOSED 25/09 (accepted, option A) |
| **2** Compiler infrastructure | Ownership/Lifetime/Borrow/Escape internal representations (`dev.kof.compiler.memory`) | ✅ CLOSED 26/09 (slices 1–4; BT success `9bcddfe90`; emission = Fase 3, unlocked) |
| **3** First guarantees | Use-after-move, dangling, escape, mutable aliasing | ✅ CLOSED — slices 3.1→4 landed (MEM001/002/013/014/021/022); see `memory-safety-plan.md` |
| **4** Closures & async | Capture semantics, async boundaries | ✅ CLOSED 28/09 — 4.1 capture (#658), 4.2 async/futures (#659), 4.3 callbacks (#662); iterators/generators = measured absence |
| **5** Native & FFI | Pointer/alloc/free, C ABI ownership table | 🔓 IN PROGRESS — ownership table landed (#670); unit 1 pinned (#666); unit 2 landed (#667/#668); unit 3 pinned (`Buffer(U8)` INOUT × spawn/await); `#651` B cross pending |
| **6** Cross-target | same memory-safety semantics on the **four backends that exist** — JVM, Native (x86-64 + cross riscv64/aarch64), JS, Script. WASM is not a gap of this front: it re-enters the contract only when a real WASM backend lands (`D-MEM-PHASE6-4BACKENDS`, 30/09) | ⏳ WAITING |

> **Phase-6 scope (corrected 30/09, `D-MEM-PHASE6-4BACKENDS`):** the original roadmap named "JVM / JS / WASM", but the tree has **no WASM backend** (`docs/backend-parity.md` = JVM × Native × KofJS; absence measured 28/09, #671). Phase-6 parity is therefore defined over the backends that exist (the four above). WASM leaves the contract until a real backend lands — it is **not** an accepted gap of this front.

---

## Appendix: Cross-Target Parity

| Rule | JVM | Native | JS | Script |
|---|---|---|---|---|
| GC reachability | Precise | Conservative | Browser GC | Interpreter |
| `val`/`var` mutability | Compile-time | Compile-time | Compile-time | Compile-time |
| Nullability narrowing | Yes | Yes | Yes | Yes |
| FFI string return | Copy (arena) | Copy (Kof-owned) | Copy (JS string) | Copy |
| FFI Buffer INOUT | Copy-in + copy-back (`JvmFfiRuntime`) | **Binds on x86-64 and riscv64/aarch64** (`#651` A2/B: the emitter passes the payload pointer `obj+24`; the C write is the copy-back — cross via `NativeFfiCallRiscv`) | Copy-back (host bridge) | **Refused at the declaration line `FFI001`** (`#667` — Script has no FFI runtime) |
| Worker stack roots | Yes (virtual threads) | **Never** (disabled after spawn) | N/A (event loop) | Interpreter stack |

---

*End of Fase 1 Specification. Gate: maintainer review of this document against
`docs/spec/memory-safety-investigation.md` and the real Kof surface.*