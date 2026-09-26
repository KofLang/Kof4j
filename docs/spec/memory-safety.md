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
| **FFI** | Explicit via `kof_ffi_release` / arena close | `Arena.ofConfined()` on JVM; copied buffers on Native |

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
| **B-06** Closure capture | By-value snapshot (immutable) or boxed (mutated) | Capturing mutable local without box when escaping | `MEM023` | Compile-time |

> **Note**: No exclusive borrow means no data-race freedom guarantee at the
> type level. Data races are prevented by discipline + `join_all` + explicit
> synchronization primitives (channels, futures). The compiler emits
> `MEM020`/`MEM021` at aliasing-sensitive stdlib/FFI/spawn boundaries.

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

## 7. FFI Ownership Boundaries

| Boundary | JVM | Native |
|---|---|---|
| **String in** | Copy to JVM `String` | Copy to Kof String |
| **String out** | Copy from JVM String | Copy from C string (owned by Kof) |
| **Buffer in** | `Arena.ofConfined()` copy-in | Copy-in to Kof buffer |
| **Buffer out (INOUT)** | Copy-in + copy-back on arena close | **Discarded on native** (divergence, `MEM005`) |
| **Array writes** | Copy-in + copy-back | **Discarded on native** (documented divergence) |
| **Owned pointer return** | Copied before arena close | Copied to Kof String/Buffer |
| **Ownership transfer** | Explicit `Arena.ofConfined()` lifetime | Explicit `kof_ffi_release` |

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
| `FFI` buffer | Explicit arena / `kof_ffi_release` | Yes | `MEM005` |

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
| Mutable aliasing data race | Programmer discipline + `MEM020/021` | `MEM020/021` (compile + runtime) | All |
| Null deref | Nullability + narrowing | `SEM049` (compile-time) | All |
| Resource leak | Explicit close | `MEM014` (compile-time) | All |
| FFI ownership confusion | Explicit arena/release | `MEM005` (compile + runtime) | JVM + Native |
| Resource leak (DB/Web) | Explicit close | `MEM014` (compile-time) | All |

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
| **3** First guarantees | Use-after-move, dangling, escape, mutable aliasing | 🔓 UNLOCKED 26/09 (`D-COMPLETE-FIRST`) |
| **4** Closures & async | Capture semantics, async boundaries | ⏳ WAITING |
| **5** Native & FFI | Pointer/alloc/free, C ABI ownership table | ⏳ WAITING |
| **6** Cross-target | JVM/JS/WASM parity matrix | ⏳ WAITING |

---

## Appendix: Cross-Target Parity

| Rule | JVM | Native | JS | Script |
|---|---|---|---|---|
| GC reachability | Precise | Conservative | Browser GC | Interpreter |
| `val`/`var` mutability | Compile-time | Compile-time | Compile-time | Compile-time |
| Nullability narrowing | Yes | Yes | Yes | Yes |
| FFI string return | Copy (arena) | Copy (Kof-owned) | Copy (JS string) | Copy |
| FFI Buffer INOUT | Copy-in + copy-back | **Discarded** (divergence) | Copy-back | Copy-back |
| Worker stack roots | Yes (virtual threads) | **Never** (disabled after spawn) | N/A (event loop) | Interpreter stack |

---

*End of Fase 1 Specification. Gate: maintainer review of this document against
`docs/spec/memory-safety-investigation.md` and the real Kof surface.*