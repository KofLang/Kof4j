[English](memory-safety-plan.md) | [Português](memory-safety-plan.pt_BR.md)

# Memory safety — ownership, lifetime, borrowing, aliasing (D-MEMORY-SAFETY)

> **Status: ACTIVE front, owned by the parity lane (maintainer 25/09,
> `DECISIONS.md` §`D-MEMORY-SAFETY`).** The semantic work (Phases 0–1) is
> current work; compiler/core edits (Phases 2+) wait for the current
> development queue to close (the brief's final constraint). Zero premature
> core edits in the meantime.

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
| **0 — Investigation** | `docs/development/memory-safety-investigation.md` (EN+PT): current state (parser/AST/semantics/types/IR/symbol resolution/mutability/closures/scope/implicit lifetime per backend: JVM/Native/JS/WASM-infrastructure/FFI/pointers/collections/async), risks found, existing related bugs (§ ledger sweep), fragile points, proposal, alternatives considered, compatibility impact, incremental plan | investigation doc accepted (maintainer review); 20 questions of §1 answered with file:line evidence |
| **1 — Specification** | `docs/spec/memory-safety.md` (EN+PT): Ownership, Lifetime, Borrowing, Aliasing, Mutability, Move, Copy, Clone, Drop/Destruction, Escape, Closure Capture, Concurrency, FFI, Unsafe Boundaries — each with: allowed / forbidden / sync-required / compile-time / runtime / type-dependent | spec accepted; the safety matrix (§22 of the brief) written against REAL Kof syntax |
| **2 — Compiler infrastructure** | internal representations for ownership/lifetime/borrow/alias/mutability/escape/resource-state | structures compile; NO behavior change yet (suite byte-green) |
| **3 — First guarantees** | use-after-move; dangling references; invalid escapes; mutable aliasing; double ownership/destruction | per-rule: valid case compiles, invalid case gets the NAMED diagnostic, regression test, per-backend proof |
| **4 — Closures & async** | closure capture semantics; callbacks; async/futures; iterators/generators | same proof shape |
| **5 — Native & FFI** | pointers/allocation/destruction/C ABI/other connectors; the Kof↔C↔Rust↔JVM↔Python ownership table | every boundary kind has an owner/free-writer/guardian decision + test |
| **6 — JVM / JS / WASM** | the same semantics on every backend | cross-target proof matrix green |

## Immediate next step (this lane)

**Fase 0** — sweep the compiler for the 20 answers of §1 (variable
representation, value vs reference, copy vs share, escape awareness,
mutability in the type system, closure capture, FFI ownership, Native
freeing, per-backend representation) and sweep `known-bugs.md` for existing
reference/aliasing/lifetime/resource/pattern bugs (§503's GC-root class is
already one entry). Produce the investigation doc. **No compiler edits.**

## Definition of done (whole front)

The 12 questions of §27 answered in the spec, the impossible-bug-classes list
explicit, and the implementation matching the spec with the §22 safety matrix
green per backend — "some structures named `Ownership`/`Borrow`/`Lifetime`"
is NOT done.

**Relationships:** `DECISIONS.md` §`D-MEMORY-SAFETY` (the decision record);
`PARITY-GAPS.md` (the blocker this front queues behind); `rule 6` (frozen
semantics — any ownership semantics that changes evaluation order or operator
contracts is a maintainer decision, never an agent edit).
