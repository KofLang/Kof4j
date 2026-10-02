[English](value-records-plan.md) | [Português](value-records-plan.pt_BR.md)

# Value Records — first-class value types (design plan · TIER 2.7)

**Status:** Plan (design only) — **zero code**; the front is **authorized** (`D-VALUE-RECORD` 16/09 accepted; `D-VALUE-RECORDS-GO` 28/09 opens it, design resolved in `D-FUTURE-BATCH-2809B`), gated by R12 (the SYSTEMS stage closes first) + one-at-a-time promotion per `D-FUTURE-PROMOTION`
**Source:** `DECISIONS.md` §D-VALUE-RECORD (16/09, accepted) · issue #275 ·
`../roadmap.md` §23 TIER 2.7 (step queue 2.7.1–2.7.5)

> **Rule of this document:** this is a **plan for the future** — it changes no
> behavior and opens no front. It stays in `future/` until the first increment
> ships (see `README.md` §"When to move from `future/` to `docs/`"). No lane may
> attack it without a new authorization (rule 6 / R12).

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

The executable order lives in `../roadmap.md` §23 **TIER 2.7** (2.7.1 front-end
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
