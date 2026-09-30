[English](size-budget-plan.md) | [Português](size-budget-plan.pt_BR.md)

# Size Budget — plan

last: #704 opened (motivation, options, scope)
doing: none — the front opens when the maintainer merges the PR that adds this plan
next: slice 1.1 — `scripts/size/measure-toolchain.sh` + selftest
location: docs/development/size-budget-plan.md
state: UNDER DEVELOPMENT (on merge)

decision: `D-SIZE-BUDGET` (`DECISIONS.md`) · tracker: #704

constraint:

* phase-1-measures-only: no dependency, packaging, target or behavior change
* no-absolute-limit-before-baseline
* read-only scripts, offline, deterministic output
* unknown-over-guess: a face that cannot be measured prints `UNKNOWN` + reason, never 0

---

## 1. Phase 1 slices

| # | Deliverable | Proof |
|---|---|---|
| 1.1 | `scripts/size/measure-toolchain.sh` — bytes of each module jar (`kof-cli`, `kof-compiler`, `kof-runtime`, `kof-script`, `kof-c-compiler`) + distribution archive + installed tree | `--selftest` with planted files; TSV output stable across two runs |
| 1.2 | `scripts/size/measure-dependencies.sh` — direct/transitive count and bytes per artifact, shaded `kof-cli` split by origin | `--selftest`; sum of attributed bytes == shaded jar entries (unattributed shown, never hidden) |
| 1.3 | `scripts/size/measure-generated.sh` — `hello-world` per target (JVM, Native x86-64/riscv64/aarch64, JS, Script) | `--selftest`; missing toolchain (as/ld/qemu) = `UNKNOWN` row, not failure |
| 1.4 | `scripts/size/compare-size.sh A B` — size diff between two measurements, grouped core / optional / generated | `--selftest` with two planted TSVs (growth, shrink, new, removed) |
| 1.5 | new `size-baseline` audit (EN+PT) in `docs/audits/` — first baseline: commit, environment, the three tables, top 20 consumers | produced by 1.1–1.3 at a named SHA; re-run reproduces the numbers |

## 2. After Phase 1

Each later phase needs its own maintainer decision, chosen from the baseline:
attribution → low-risk reductions (before/after/delta + suite) → packaging spikes → CI gate `scripts/check_size_budget.sh`.

## 3. Done (Phase 1)

* 1.1–1.5 landed, each with selftest wired into `scripts/tests/run-agent-tests.sh`
* baseline published and reproducible
* zero change outside `scripts/size/` and `docs/audits/`
