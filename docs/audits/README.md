[English](README.md) | [Português](README.pt_BR.md)

# docs/audits/ — audits (snapshot of state vs. reality)

> **Rule of this folder** (created 09/13, maintainer's decision): a document whose
> work is to **compare what the plans/docs claim against the real code, tests and
> releases** — an implemented-vs-planned matrix — lives here, not in
> `development/` (which is the queue of pending technical work) nor in `bugs-and-gaps/`
> (queue by target). The audit **does not close by itself**: it points to work, which is
> claimed in the plans/records of the other folders.

## Current state

| Doc | What it audits | Liveliness |
|---|---|---|
| `roadmap-audit.md` | roadmap × code (matrix + P0→P5 queue) | **alive** — re-audit when something closes |
| `complexity-audit.md` | line/class count (09/02) | **snapshot** — live gate = `scripts/check_500.sh` (CI ratchet) |
| `PLANNING-FUTURE-AUDIT.md` | branch `planning-future` × beta (09/07–09/08) | **closed** — R2 lives in `DECISIONS.md` §D-APP/§D-PLATFORM (ratified 09/13), R5 in the migration cluster |
| `planning-future-reconcile.md` | branch merge (09/05) | **closed** — checklist fulfilled (tiers port) |
| `registry-live-roundtrip-2026-09-20.md` | Registry 1.5.3 live GitHub round-trip smoke (20/09) | **RED** — publish OK, pull blocked by #564; re-run phases C–E after the fix |
| `supply-chain-trust-boundary-2026-09-20.md` | KOF supply-chain trust boundary v1 — read-only baseline (release workflow, tested×published SHA, actions/permissions, source governance, provenance) | **alive** — lab results (Onda 2) and the maintainer's answers to §5 land here |
| `size-baseline.md` | D-SIZE-BUDGET Phase 1 — distribution weight baseline (module/shaded/thin/packed jars, top-package attribution, hello-world per target) | **alive** — regenerate with `scripts/size/measure-size.sh` at each size-relevant change; diff with `compare-size.sh` |

## How to use

1. **Never** attack work directly from here — whatever an audit marks as
   pending must have a home: bug → `docs/bugs-and-gaps/known-bugs.md`; plan
   with code to write → the plan doc in `docs/development/`; decision →
   `docs/development/DECISIONS.md` (the `decision-pending/` folder was extinguished 09/13).
2. When closing what an audit pointed out, **update the audit's line in the
   SAME commit** (it is a record, not a frozen opinion).
3. A closed audit (nothing alive pointing to unmatched work) stays
   here as a dated historical record — it does not go back to `development/`.
