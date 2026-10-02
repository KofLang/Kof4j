# Quality pipeline — executable branch policy

last: 14.3-promotion-tooling (suite gate + scripted counts, #657)
doing: automation-enforcement
next: 14.4-rulesets+dispatch-on-main (mantenedora)
location: docs/development
state: active

intent: executable-branch-quality-policy

constraint:

* lab is the only development entry point
* every promotion is explicit, unidirectional and auditable
* no direct push to a later stage
* any failure returns to lab
* fail closed when the state cannot be determined
* deterministic and idempotent automation

## Pipeline

`lab → testing → prerelease → stable → release/x.y.z → tag`

| Stage | Role | Gate to leave it | Protected |
|---|---|---|---|
| `lab` | development + fixes | all required checks PASS | no |
| `testing` | integration/QA | all required checks PASS, 0 blocking issues | yes |
| `prerelease` | public candidate | 7-day window, 0 related issues, checks PASS | yes |
| `stable` | closed contract | all required checks PASS | yes |
| `release/x.y.z` | packaging only | all required checks PASS, version not tagged | yes |
| tag `kof-*` | public contract | — | immutable |

The `≥80%` denominator was **dropped** (`D-QUALITY-PIPELINE-2609`, maintainer
28/09/2026): every promotion is 100%.

## States

`LAB → TESTING → PRERELEASE → PRERELEASE_OBSERVATION → STABLE → RELEASE → TAGGED`

Failure never opens a parallel flow: `TESTING|PRERELEASE|STABLE|RELEASE → BLOCKED → LAB`.

The machine is code, not convention: `scripts/pipeline/pipeline_state.py`. A
transition not in `ALLOWED`, an unknown branch, or a no-op is **refused** (fail
closed). `lab→prerelease`, `lab→stable`, `testing→stable`, `prerelease→release`,
`lab→release` and `stable→prerelease` are blocked by test.

## Promotion Gate

`scripts/pipeline/promotion_gate.py` evaluates one step: it reuses the state
machine, then requires 100% of the fixed mandatory checks (Build + Tests, Native
cross, kof.io multiplatform, Structural quality gates, CodeQL Gate, bots), the
complete observation window with 0 related issues (`prerelease→stable`), and
idempotent tag creation. It is a pure function of its inputs — deterministic, no
writes, no network — so it is safe to re-run.

Report fields: from/to stage and state, status, per-check results, commit,
version, timestamp, blocking/related issues, reasons, and the action
(`return_to: lab`) on failure.

## Enforcement

* Rulesets (server side): `pipeline-stages` blocks direct push, force-push and
  deletion on `main`/`testing`/`prerelease`/`stable`/`release/*`, and requires a
  PR + status checks; `release-tags` makes `kof-*` tags immutable. `lab` is open.
* Workflow `.github/workflows/promote.yml` (manual, idempotent): validates the
  transition, runs the FULL SUITE on the exact dispatched tip as the first formal
  gate (`lab` has no per-push CI by design — the suite is the measurement, never a
  stale check-run), merges that verdict with the tip's check-runs and MEASURED
  issue counts (`promotion_evidence.py`: blocking = open `bug` issues; related =
  issues updated since `promoted-at`), runs the gate, and opens an auditable
  promotion PR only when it PASSES. It never pushes to a stage, and the counts
  are never defaulted — omitting them is refused (opinion is forbidden).
* Concurrency is grouped per target stage; duplicate runs are no-ops.

## Issue linkage

Blocking is deterministic: a promotion is refused when the count of blocking
issues (`testing→prerelease`) or of related issues in the observation window
(`prerelease→stable`) is non-zero. Association is by version/commit/tag, never by
free-text heuristics.

## Correction policy

> Found a problem? It returns to `lab`.

A fix is never made in `testing`, `prerelease`, `stable` or `release/x.y.z` and
then merged sideways. It is born in `lab` and re-traverses the whole pipeline; a
new pre-release starts its own observation window.

## Contract

`D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE` (`docs/development/DECISIONS.md`).
Cutover announcement and migration instructions: issue #647.
