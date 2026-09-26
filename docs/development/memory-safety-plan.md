[English](memory-safety-plan.md) | [Português](memory-safety-plan.pt_BR.md)

# Memory safety — ownership, lifetime, borrowing, aliasing (D-MEMORY-SAFETY)

> **Status: ACTIVE front, owned by the parity lane (maintainer 25/09,
> `DECISIONS.md` §`D-MEMORY-SAFETY`).** Fase 0 (investigation) **CLOSED 25/09** —
> `docs/spec/memory-safety-investigation.md` accepted. Fase 1 (specification)
> **CLOSED 25/09 — maintainer chose "review and close" (option A of the
> decision list)**: `docs/spec/memory-safety.md`(+PT) is the accepted spec.
> Compiler/core edits (Phases 2+) wait for the current development queue to
> close (brief's final constraint; option J — ZERO premature core edits).

**Goal:** define a serious memory semantics for Kof so that entire classes of
memory bugs are impossible — or live behind an explicit boundary the
programmer must name. NOT a feature called `ownership`: the success criterion
is the compiler being able to say *"this program cannot produce this class of
error"* (use-after-free, double-free, dangling reference, invalid lifetime
escape, unsafe mutable aliasing, unexpected null, accidental data race).

## The brief's hard constraints (locked in `D-MEMORY-SAFETY`)

1. **Kof already has null safety** — never reinvent/replace/duplicate it;
   only study nullability × ownership × lifetime × borrowing.
2. **Kof-first investigation** — no assumption that Kof works like Rust,
   C++, Java, Kotlin, Swift or Zig (rule 10; rule 8: a foreign-language
   feature request is not a Kof bug).
3. **Architecture before code** — Phase 0 investigation and Phase 1 spec
   precede ANY compiler edit.
4. **Implementation waits for the current queue** (brief, final line).
5. **Simplicity Law** (rule 11): strong guarantees without turning Kof code
   into an endless chain of lifetime annotations.
6. **Cross-target by construction** — JVM, Native, JS and the planned WASM
   express the same Kof semantics; GC on JVM/JS never excuses semantic
   divergence; FFI must define the owner per crossing.
7. **Diagnostics and tests are part of the feature** — every rule ships with
   valid/invalid/expected-diagnostic/regression/per-backend cases; small
   suites per domain, adapted to the real test tree.
8. **Forbidden:** copying Rust's borrow checker, inventing syntax (`let`,
   `const`, foreign move markers), a null-safety rewrite, big-bang compiler
   refactors, single-backend ownership, hiding ownership problems in the
   runtime.

## Phases (each gates the next; a phase closes only with proof)

| Phase | Deliverable | Gate |
|---|---|---|
| **0 — Investigation** ✅ | `docs/spec/memory-safety-investigation.md` (EN+PT): current state (parser/AST/semantics/types/IR/symbol resolution/mutability/closures/scope/implicit lifetime per backend: JVM/Native/JS/WASM-infrastructure/FFI/pointers/collections/async), risks found, existing related bugs (§ ledger sweep), fragile points, proposal, alternatives considered, compatibility impact, incremental plan | investigation doc accepted (maintainer review); 20 questions of §1 answered with file:line evidence — **CLOSED 25/09** |
| **1 — Specification** 🔄 | `docs/spec/memory-safety.md` (EN+PT): Ownership, Lifetime, Borrowing, Aliasing, Mutability, Move, Copy, Clone, Drop/Destruction, Escape, Closure Capture, Concurrency, FFI, Unsafe Boundaries — each with: allowed / forbidden / sync-required / compile-time / runtime / type-dependent | spec accepted; the safety matrix (§22 of the brief) written against REAL Kof syntax |
| ~~**2 — Compiler infrastructure**~~ | internal representations for ownership/lifetime/borrow/alias/mutability/escape/resource-state — **✅ CLOSED 26/09** (slices 1–4: `OwnerKind`/`MemRule`/`ManagedResource`/`CaptureMode`/`MoveDetector`+`MoveTransfer` in `dev.kof.compiler.memory`; `MemoryModelTest` 8/8; `Build + Tests` success `9bcddfe90` — zero behavior change) | ALCANCADO 26/09 (structures compile; suite byte-green no runner) |
| **3 — First guarantees** | use-after-move; dangling references; invalid escapes; mutable aliasing; double ownership/destruction | per-rule: valid case compiles, invalid case gets the NAMED diagnostic, regression test, per-backend proof |
| **4 — Closures & async** | closure capture semantics; callbacks; async/futures; iterators/generators | same proof shape |
| **5 — Native & FFI** | pointers/allocation/destruction/C ABI/other connectors; the Kof↔C↔Rust↔JVM↔Python ownership table | every boundary kind has an owner/free-writer/guardian decision + test |
| **6 — JVM / JS / WASM** | the same semantics on every backend | cross-target proof matrix green |

## Immediate next step (this lane)

**Fase 1 CLOSED 25/09** — `docs/spec/memory-safety.md` (EN+PT) written and
accepted by the maintainer (option A). It formalizes
Ownership, Lifetime, Borrowing, Aliasing, Mutability, Move, Copy, Clone,
Drop/Destruction, Escape, Closure Capture, Concurrency, FFI, Unsafe
Boundaries against the real Kof surface documented in
`docs/spec/memory-safety-investigation.md`. Each rule with
allowed/forbidden/sync-required/compile-time/runtime/type-dependent
classification. Safety matrix (§22 of the brief) against REAL Kof syntax.
**No compiler edits.**

**Fase 2 UNLOCKED 26/09 by the maintainer (chat: "fase 2 destravada").**
The lane now builds the compiler-internal representations — package
`dev.kof.compiler.memory` with ownership/lifetime/borrowing/aliasing/
mutability/escape/resource-state models + the `MEMxxx` diagnostic-code enum
from the spec (§1–§10). Structures + tests only; **zero behavior change**
(suite byte-green). Emission/wiring of the diagnostics is Fase 3.

**Fase 2 LANDED 26/09 (slices 1–4, tip `9bcddfe90`):** `OwnerKind` (§2.1),
`MemRule` (as 31 regras O/L/B/M/E/C/N com diagnostico ou permissivas),
`ManagedResource` (§9), `CaptureMode` (§6.1), `MoveDetector`+`MoveTransfer`
(O-02, read-only, sem emissao) — `MemoryModelTest` 8/8, zero mudanca de
comportamento. A fila de representacao esta EXAUSTA.

> **DECISION REQUEST — RESOLVIDO 26/09 por D-COMPLETE-FIRST (DECISIONS.md):**
> the move pattern `var a = b; b = null` collided with N-02/SEM048 (null
> literals forbidden). The maintainer's rule (26/09, chat) makes the complete
> idiomatic form the decision: **re-express O-02 without a null literal (c)** —
> the ownership/lifetime analysis pass in the compiler pipeline, with
> MEM001/MEM002/MEM005 emission and the interaction cases across the 4
> targets, landed as one complete package (pass + emission + per-target proof;
> no lone diagnostic, no stub, no gap accepted). **Fase 3 UNLOCKED.**

## Definition of done (whole front)

The 12 questions of §27 answered in the spec, the impossible-bug-classes list
explicit, and the implementation matching the spec with the §22 safety matrix
green per backend — "some structures named `Ownership`/`Borrow`/`Lifetime`"
is NOT done.

**Relationships:** `DECISIONS.md` §`D-MEMORY-SAFETY` (the decision record);
`PARITY-GAPS.md` (the blocker this front queues behind); `rule 6` (frozen
semantics — any ownership semantics that changes evaluation order or operator
contracts is a maintainer decision, never an agent edit).
