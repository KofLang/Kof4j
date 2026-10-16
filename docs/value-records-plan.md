[English](value-records-plan.md) | [Português](value-records-plan.pt_BR.md)

# Value Records — first-class value types (PROMOTED to `docs/` 14/10 · TIER 2.7)

state:
last: PROMOTED to `docs/` 14/10 (from `docs/development/`) — ALL FIVE STEPS LANDED with executed proof: 2.7.1 the `value` front-end keyword (contextual, like `sealed`; PortuKof `valor registro`); 2.7.2 the JVM ABI (the common record class + `SymbolTable.ClassSymbol.isValueRecord`); 2.7.3 the Native ABI (the current heap representation — value semantics already delivered by immutability + structural equality; pass-by-value unobservable, explicitly deferred); 2.7.4 the JS ABI (`Object.freeze(this)` in the value-record constructor); 2.7.5 the conformance cell `valuerecord` (4 targets DONE) + the matrix row + the `training/idioms/records` idiom + the `learn/07` section. ALSO fixed en route: the `Optimizer`/`JsBackend` IRClass rebuilds dropped `isRecord` (latent), and the PortuKof contextual modifier is now canonicalized.
next: reference contract — no pending slice. Future work (a separate decision if ever wanted): the deferred Native pass-by-value/stack allocation optimization; the compile-time identity error (no Kof construct requires identity today).
location: docs/value-records-plan.md
owner: 192.168.100.14:0, flatt CLI, no opencode server on this host; port n/a
state: promoted

**Status:** PROMOTED to `docs/` 14/10 — **ALL FIVE STEPS LANDED with executed proof** (2.7.1 the `value` front-end keyword; 2.7.2 the JVM ABI + `ClassSymbol.isValueRecord`; 2.7.3 the Native ABI — current heap repr, pass-by-value deferred; 2.7.4 the JS ABI — `Object.freeze`; 2.7.5 the conformance cell `valuerecord` DONE on 4 targets + the matrix row + the idiom + the learn section). The front is authorized (`D-VALUE-RECORD` 16/09; `D-VALUE-RECORDS-GO` 28/09: `value` applies ONLY to `record`, collections hold them boxed, identity-required use is a compile-time error).
**Source:** `DECISIONS.md` §D-VALUE-RECORD (16/09, accepted) · issue #275 ·
`roadmap.md` §23 (docs/development/) TIER 2.7 (step queue 2.7.1–2.7.5)

## 1. Objective

Let a programmer declare that a user-defined aggregate has **value semantics and
no observable object identity** — for small data-oriented types (vectors,
coordinates, colors, ranges, parser tokens, iterator state) where a separate
object allocation adds allocation pressure, GC work, indirection and worse cache
locality. Relying on JVM escape analysis does not express intent and does not
hold across the Native/JS backends.

## 2. Proposal (candidate syntax)

```kof
value record Vec2(Float x, Float y)
value record Color(Int r, Int g, Int b, Int a)
```

A `value record` has the **same concise immutable data-model** as an existing
`record`; the `value` modifier adds "no identity". Ordinary `record` keeps its
semantics untouched.

## 3. Contract (from `DECISIONS.md` §D-VALUE-RECORD)

- Equality/hash by fields (already the record contract).
- **Additive and backward compatible** (freeze rule 2): existing code keeps
  compiling and running.
- The language **intentionally does not guarantee "stack allocation"** — the
  guarantee is value semantics + absence of identity; physical storage is a
  backend decision.
- Boundary: **core stdlib**, not an official package (R1).

## 4. Per-target ABI (scope decision before code — R7 honest scope)

| Target | Candidate representation |
|--------|--------------------------|
| JVM | value/inline class (`invokevirtual` value semantics, no `Object` identity) |
| Native | pass-by-value (struct by value / registers) |
| JS | plain frozen object (no identity) |

The per-target ABI is an **explicit scope decision** before any code lands; the
JS target may legitimately land last (R7).

## 5. Step queue

The executable order lives in `roadmap.md` §23 (docs/development/) **TIER 2.7** (2.7.1 front-end
`value` keyword → 2.7.2 JVM ABI → 2.7.3 Native ABI → 2.7.4 JS ABI → 2.7.5
parity + docs). This document is the design rationale; it does not duplicate the
queue.

## 6. Open questions (maintainer decisions)

- Does `value` also apply to `class`, or only to `record`?
- Interaction with generics/collections (e.g. `List<Vec2>` — flattened or boxed?)
- Exact diagnostics when a `value record` is used where identity is required.
- Whether the JS representation is frozen/sealed or a plain object.

## 7. What NOT to do

- Do **not** promise stack allocation or an ownership/borrowing model (permanent
  non-goal).
- Do **not** change ordinary `record` semantics.
- Do **not** open the front without authorization (rule 6 / R12): new fronts do
  not open before the SYSTEMS stage closes.
