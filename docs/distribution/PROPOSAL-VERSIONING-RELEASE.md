[English](PROPOSAL-VERSIONING-RELEASE.md) | [Português](PROPOSAL-VERSIONING-RELEASE.pt_BR.md)

# KOF — Proposal to consolidate the versioning and release-cut policy

**Status:** `RATIFIED — approved by the maintainer (PR #582 merged 22/09/2026); MATERIALIZED as D-VERSIONING-RELEASE (22/09/2026). **24/09: promoted out of `docs/development/` — the OPERATIVE rule is [`VERSIONING.md`](VERSIONING.md) (+PT) in this directory; this document is the ratified decision/rationale record.**

**Status:** `RATIFIED — approved by the maintainer (PR #582 merged 22/09/2026); MATERIALIZED as D-VERSIONING-RELEASE (22/09/2026)`
**Base branch:** `beta-0.5.0`
**Tip revalidated during drafting:** `6c9aeb847f167e97ed48c21a9e4453028198da63`
**VERSION measured at the tip:** `0.5.0-beta`
**Last published release measured:** `kof-0.4.9-beta-*` (Linux/Windows/macOS tag family)
**DOING.md — lane collision:** no active claim found over `VERSIONING.md`, `release-naming.md`, `D-RELEASE`, `D-RELEASE-0.5.0-GATE`, `D-RELEASE-1.0`, `D-1.0-EDGES`, or "versioning"/"versionamento" in this unit's measurement.
**Proposed decision:** `D-VERSIONING-RELEASE`
**Approval authority:** **Mel / `melmonfre`**
**Merge rule:** **MERGE FORBIDDEN WITHOUT MEL'S EXPLICIT APPROVAL OF THE FINAL DIFF**

---

## 0. Objective

Consolidate into a single normative policy the KOF criteria to:

1. classify a change as `PATCH`, `MINOR`, or `MAJOR`;
2. decide **when to evaluate** a new release;
3. decide **when a candidate is eligible for cut**;
4. preserve `D-RELEASE-1.0` as the integral gate for the first stable release;
5. remove the current ambiguity between `VERSIONING.md`, `D-RELEASE`, release gates, and the project's recent practice;
6. prevent a new public surface from being published as a plain patch by subjective judgment that the change is "small" or "materially small".

This work **does not by itself authorize any release, version bump, tag, or contract change**.

---

# 1. KOF-first — mandatory evidence

Revalidated at tip `6c9aeb847f167e97ed48c21a9e4453028198da63` (branch `beta-0.5.0`, in sync with `origin`) while drafting this proposal:

1. `AGENTS.md` and `AGENTS.pt_BR.md` — read; rule 6 ("a decision made in chat lives here [DECISIONS.md]... never attack a front without a decision locked here") is the normative basis this proposal invokes on itself.
2. `docs/development/DECISIONS.md` and `.pt_BR.md` — read; `D-RELEASE` (2026-09-14), `D-BRANCH-0.5.0`, `D-VERSION-BUMP-0.5.0`, `D-RELEASE-0.5.0-GATE` (+ condition 2 + `D-RELEASE-0.5.0-SCOPE`), `D-RELEASE-1.0`, `D-1.0-EDGES` confirmed present and `DECIDED`/`RATIFIED` as cited below.
3. `docs/distribution/VERSIONING.md` and `.pt_BR.md` — read; confirms the drift described in §2.1: the "Current stage" section still states `0.0.x` / Alpha and "every commit on main generates the next Alpha version (PATCH increment)", which does not describe the measured state (`VERSION` = `0.5.0-beta`, dedicated branch `beta-0.5.0`).
4. `docs/distribution/release-naming.md` and `.pt_BR.md` — read; already uses `MAJOR.MINOR.PATCH-<stage>` and the codename table; no normative contradiction with this proposal.
5. `docs/distribution/release-beta-0.5.0.md` and `.pt_BR.md` — the 0.5.0 cut acceptance record (CLOSED 28/09); orthogonal to this proposal.
6. `docs/PROPOSAL-1.0-EXIT-GATE.md` and `.pt_BR.md` — read; `D-RELEASE-1.0`/EXIT GATE confirmed `RATIFIED`, treated as untouchable by this proposal (§6).
7. `DOING.md` — no active claim (`IN PROGRESS`) over this front's files in this measurement.
8. `VERSION` — `0.5.0-beta` at the tip above.
9. Active branch `beta-0.5.0`; last published release measured by tag: `kof-0.4.9-beta-linux-x86_64` (and its windows/macos pairs).

This snapshot does not substitute a fresh revalidation if the tip advances before Mel's review.

### Evidence block

```text
KOF VALIDITY:
N/A — governance/versioning.

CONTRACT SOURCE:
VERSIONING.md
release-naming.md
D-RELEASE
D-BRANCH-0.5.0
D-VERSION-BUMP-0.5.0
D-RELEASE-0.5.0-GATE
D-RELEASE-0.5.0-SCOPE
D-RELEASE-1.0
D-1.0-EDGES

CURRENT KOF IDIOM:
MAJOR.MINOR.PATCH-<stage>

MEASUREMENT:
tip = 6c9aeb847f167e97ed48c21a9e4453028198da63 (origin/beta-0.5.0)
VERSION = 0.5.0-beta
last release = kof-0.4.9-beta-* (linux/windows/macos)
DOING.md = no lane collision on this front

CLASSIFICATION:
CONTRACT AMBIGUITY / governance DESIGN REQUEST.

DUPLICATE / PRECEDENT CHECK:
D-RELEASE already differentiates patch from minor.
D-RELEASE-1.0 already governs the first stable major.
There is today no single source unifying classification + trigger + gate + cut.

ACTION:
propose D-VERSIONING-RELEASE; wait for Mel's approval before making it normative.
```

---

# 2. Problem observed in the repository

Today KOF has the pieces, but they are scattered.

## 2.1 `VERSIONING.md`

Already defines the conceptual hierarchy:

```text
MAJOR.MINOR.PATCH
```

with:

- `PATCH`: bugfix, regression, small adjustments;
- `MINOR`: significant evolution;
- `MAJOR`: major release / architectural or compatibility change.

However, the file still carries historical text stating that:

```text
Current stage = 0.0.x / Alpha
```

and that every commit on `main` generates the next Alpha/PATCH.

This no longer describes the project's current state, today at `0.5.0-beta` with its own active branch — confirmed by this revalidation (§1.3).

## 2.2 `D-RELEASE`

Already decided (confirmed at the tip, §1.2):

- between `100–150` commits of advance, a new release **should be evaluated**;
- this does not freeze features;
- the version is only updated after the evaluation;
- if there is a new material capability that changes the contract, operator, or API surface, the evaluated version stops being a patch and becomes minor.

The idea is good, but has two limitations:

1. the normative command is hardcoded to `beta-0.4.0`;
2. the term **"material capability"** still requires subjective judgment.

## 2.3 `D-RELEASE-0.5.0-GATE`

Created a strong eligibility-to-cut model:

1. parity;
2. relevant decisions resolved/reviewed;
3. development-docs hygiene;
4. full stability;
5. zero open bugs;
6. edges/blockers closed;
7. bugs-and-gaps with no applicable pending item.

This gate is specific to `0.5.0`; this proposal **must not silently turn it into a universal gate**. It serves as precedent and model.

## 2.4 `D-RELEASE-1.0`

Already defines that `1.0.0` is neither a counter nor a marketing decision.

The first stable release can only exist after:

```text
EXIT GATE fully GREEN
+
same candidate
+
no open edge
+
RC without regression
```

`D-1.0-EDGES` also decided that the 1.0 line only opens after the `0.5.0` cut, EG-1..EG-7 closure, and an explicit maintainer declaration.

This proposal **does not change or weaken that contract**.

---

# 3. External evidence — grounding function only

External references **are not the KOF contract**. They serve to test whether the proposed policy is technically reasonable.

## Weight 5 — empirical research

### Maven / SemVer and consumers

A large-scale study on Maven library upgrades found broad SemVer adoption, but also breaking changes and real impact on consumers even when versioning suggested compatibility.

Reference:

- `Semantic Versioning and Impact of Breaking Changes in the Maven Ecosystem` — arXiv:2110.07889

Implication for KOF:

> The declared number is not sufficient proof of compatibility. Classification must be accompanied by a gate and evidence.

### Go / breaking changes outside major

A study on the Go ecosystem found breaking changes in upgrades that did not change the major version, with potential impact on downstream consumers.

Reference:

- empirical study on SemVer and breaking changes in the Go ecosystem — arXiv:2309.02894

Implication for KOF:

> `PATCH` or `MINOR` needs to be validated against the observable surface, not just manually chosen.

### Release cadence

A study across hundreds of open source projects found no evidence that simply shortening or lengthening the release cycle by itself improves bug handling.

Reference:

- study on rapid releases in open source projects — arXiv:2103.08648

Implication for KOF:

> a number of days or commits should be an **evaluation** trigger, never publication authorization.

### Security releases

Research on thousands of advisories shows that security fixes frequently require a much shorter publication cycle.

Reference:

- empirical study on time between security fix and release — arXiv:2112.06804

Implication for KOF:

> a security fix / critical regression should be able to trigger immediate evaluation without waiting for 100–150 commits.

---

## Weight 3 — mature projects

### Rust

Rust uses its own release cadence and treated `1.0` as a language-stability commitment, not a plain numeric increment.

Evolution continues after 1.0, preserving compatibility and using explicit mechanisms for larger changes.

### Go

Go ties the 1.x line to a strong compatibility commitment, but keeps adding relevant capabilities in a compatible way.

Useful conclusion:

```text
1.0 != frozen language
1.x = evolution + compatibility
```

### Python

Python separates the feature-development phase, beta/feature freeze, RC, and maintenance releases.

This reinforces the separation:

```text
classifying a version != deciding the moment of the cut
```

---

## Weight 2 — official documentation

### Semantic Versioning 2.0

SemVer distinguishes:

- PATCH: compatible fix;
- MINOR: compatible public functionality;
- MAJOR: public API incompatibility.

SemVer also treats `0.y.z` as initial development.

For KOF, the proposal deliberately adopts a discipline **stricter than SemVer's 0.x minimum**, because the project already has frozen contracts, normative decisions, and multi-target parity before 1.0.

---

# 4. Normative proposal — `D-VERSIONING-RELEASE`

> **WARNING:** the text below is a PROPOSAL. Do not record it as `DECIDED` before Mel's explicit approval.

## 4.1 Central principle

Versioning and cutting are two distinct decisions:

```text
CHANGE
  ↓
VERSION CLASSIFICATION
  ↓
PATCH / MINOR / MAJOR
  ↓
RELEASE EVALUATION
  ↓
CANDIDATE
  ↓
RELEASE GATE
  ↓
CUT
```

A change being classifiable as PATCH does not mean a PATCH release must be published immediately.

---

## 4.2 Pre-1.0 rule — PATCH

During `0.x`, `PATCH` is reserved for changes that **neither add nor alter the contracted public surface**.

Allowed examples:

- bugfix;
- security fix;
- regression fix;
- target-parity fix to satisfy an existing contract;
- performance without a change in contracted behavior;
- internal refactor;
- CI/tooling;
- release engineering;
- packaging;
- documentation;
- diagnostic improvement that does not change the contract;
- internal implementation of an already-approved decision, when it creates no new public surface.

Proposed rule:

```text
PUBLIC_CONTRACT_SURFACE_DIFF = 0
→ PATCH admissible
```

---

## 4.3 Pre-1.0 rule — MINOR

During `0.x`, any **new contracted public surface** or deliberate alteration of that surface requires `MINOR`.

Includes, when applicable:

- new syntax;
- new operator;
- new observable semantics;
- new relevant public API;
- new public namespace;
- new public command/flag that is part of the contract;
- new public stdlib capability;
- new package/registry/interop contract;
- promoting a new target to Supported/Stable surface;
- deliberate, approved pre-1.0 breaking change.

Proposed rule:

```text
PUBLIC_CONTRACT_SURFACE_DIFF > 0
→ PATCH forbidden
→ MINOR minimum
→ Decision ID mandatory
```

### Pre-1.0 breaking change

Can only happen with:

```text
MINOR
+
recorded DECISION
+
impact/migration note
+
corresponding proof
```

No deliberate breaking change should be hidden in a patch just because the project is still below 1.0.

---

# 5. Post-1.0 rule

After the first Stable:

```text
1.2.3 → 1.2.4
PATCH = backward-compatible fixes

1.2.3 → 1.3.0
MINOR = new backward-compatible public functionality

1.2.3 → 2.0.0
MAJOR = incompatible change to the public contract
```

In KOF, compatibility must be evaluated along four dimensions:

1. **source compatibility** — prior KOF code remains valid wherever the promise applies;
2. **artifact/binary compatibility** — package/interop/artifacts keep the declared contract;
3. **behavioral compatibility** — contracted observable behavior does not change improperly;
4. **cross-target compatibility/parity** — every target of the surface follows the contract or the documented gap.

---

# 6. Rule for the first `1.0.0`

This proposal must **incorporate by reference, without rewriting or relaxing**, `D-RELEASE-1.0` and `D-1.0-EDGES`.

`1.0.0` is not chosen by:

- number of commits;
- number of features;
- project age;
- numerically reaching `0.9.9`;
- subjective perception of maturity.

The first `1.0.0` only exists when the ratified EXIT GATE authorizes it.

Non-normative summary:

```text
0.5.0 cut
+
EG-1..EG-7 closed
+
maintainer declaration
+
first RC
+
Stable Surface closed
+
EXIT GATE fully GREEN on the same candidate
+
RC → Stable without regression
→ 1.0.0
```

---

# 7. Release-evaluation triggers

The policy must differentiate **trigger** from **cut**.

## 7.1 Ordinary trigger

Generalize the current `D-RELEASE` from:

```text
git rev-list --count origin/main..origin/beta-0.4.0
```

into a concept independent of a historical branch:

```text
LAST_RELEASE..ACTIVE_BRANCH
```

Current range proposed to preserve:

```text
100–150 commits
→ open RELEASE EVALUATION
```

This **does not authorize publication**.

## 7.2 Extraordinary trigger

Immediate evaluation may be opened by:

- a relevant security fix;
- a critical regression;
- an urgent distribution/package fix;
- an explicit maintainer decision.

Again:

```text
trigger != cut
```

---

# 8. Common eligibility-to-cut gate

The consolidated policy must define a common baseline, without silently turning the `0.5.0`-specific gate into a universal rule.

Proposed baseline:

```text
[ ] candidate SHA identified
[ ] VERSION / pom / version resource consistent
[ ] CHANGELOG/release metadata coherent
[ ] required suite GREEN on the candidate
[ ] PATCH/MINOR/MAJOR classification proven
[ ] necessary decisions recorded
[ ] applicable blockers resolved
[ ] real package validated when applicable
[ ] artifact trust/provenance per the current contract
[ ] EN/PT documentation synced where required
```

Line-specific gates continue to prevail:

- `D-RELEASE-0.5.0-GATE` for 0.5.0;
- `D-RELEASE-1.0` for RC/Stable 1.0;
- future line-specific gates once decided.

---

# 9. Future mechanical surface gate

The decision may authorize as backlog, **not as a requirement to approve this PR**, the future creation of:

```text
release-surface-gate
```

Goal:

compare the last release against the candidate across contractual surfaces such as:

- grammar;
- language-reference;
- operators;
- typing rules;
- Stable stdlib catalog;
- contractual CLI commands/flags;
- package/registry contract;
- Stable Target Surface;
- other project-generated manifests.

Desired result:

```text
PUBLIC_SURFACE_DIFF = 0
→ PATCH may remain in evaluation

PUBLIC_SURFACE_DIFF > 0
→ PATCH rejected
→ requires MINOR/MAJOR + Decision ID
```

Do not implement this gate within this contract PR, except by later explicit decision.

---

# 10. Documentation drift to fix if the proposal is approved

After Mel's approval, update EN+PT in a coordinated way.

## 10.1 `VERSIONING.md`

Remove/replace as current-state the obsolete statements:

```text
Current stage = 0.0.x
Alpha current
every commit on main generates next PATCH
```

Preserve history where necessary, but make the document describe the current state and point to `D-VERSIONING-RELEASE`.

## 10.2 `D-RELEASE`

Do not erase the historical decision.

Add a partial relation/supersession clarifying that:

- `100–150 commits` = ordinary evaluation trigger;
- the branch must resolve as `LAST_RELEASE..ACTIVE_BRANCH`, not hardcoded to `beta-0.4.0`;
- classification now follows `D-VERSIONING-RELEASE`.

## 10.3 `release-naming.md`

Keep `MAJOR.MINOR.PATCH-stage` and the Beta/RC/Stable staging; only align any phrase that contradicts the new decision.

## 10.4 `D-RELEASE-0.5.0-GATE` and `D-RELEASE-1.0`

Do not rewrite the gates.

Only add a relationship to the new decision if necessary.

---

# 11. Mandatory PR flow — Mel's approval

## STEP A — proposal PR

Create a branch from the up-to-date remote tip of `beta-0.5.0`.

Name used in this unit:

```text
docs/versioning-release-contract
```

Suggested title:

```text
[Design/Contract]: consolidate KOF versioning and release-cut policy
```

Base:

```text
beta-0.5.0
```

In the first version of the PR:

- add the dossier/proposal under `docs/development/` EN+PT (this document);
- **DO NOT mark `D-VERSIONING-RELEASE` as DECIDED**;
- **DO NOT change VERSION**;
- **DO NOT cut a release**;
- **DO NOT create a tag**;
- **DO NOT touch production code**;
- **DO NOT implement release-surface-gate**;
- request review from `melmonfre`.

### Decision question for Mel

The PR must ask for an explicit decision on:

> Do you approve consolidating KOF versioning under `D-VERSIONING-RELEASE`, with pre-1.0 PATCH reserved for changes with no contracted public-surface diff; MINOR mandatory whenever there is a new/altered contracted public surface; 100–150 commits acting only as an evaluation trigger; security/critical regressions able to trigger immediate evaluation; strict SemVer after 1.0; and `D-RELEASE-1.0` remaining integral and sovereign for the first Stable?

## HARD STOP A

Without Mel's explicit answer:

```text
STOP
NO DECISIONS.md normative edit
NO MERGE
```

A reaction, absence of objection, green CI, a bot comment, or another contributor's approval **does not substitute** the maintainer's approval.

---

# 12. STEP B — normative materialization after approval

Only if Mel approves the contract:

1. update `docs/development/DECISIONS.md` + `.pt_BR.md`;
2. record `D-VERSIONING-RELEASE` with the approved wording;
3. update `docs/distribution/VERSIONING.md` + `.pt_BR.md`;
4. align `release-naming.md` + `.pt_BR.md` if needed;
5. add a supersession/refinement relation on `D-RELEASE` without erasing history;
6. update the roadmap/tracker only if the decision creates mechanical backlog;
7. keep `D-RELEASE-0.5.0-GATE` and `D-RELEASE-1.0` intact except for necessary links/relationships;
8. run the docs gates required by the repo.

### Suggested decision state

If Mel approves only the documentary policy:

```text
State: DECIDED
```

If mechanical guards are also implemented in the same cycle — which **is not recommended in this PR** — only then evaluate `PARTIAL`/`IMPLEMENTED` per evidence.

---

# 13. HARD STOP B — final diff approval

Since STEP B modifies the diff after Mel's first decision, **the initial approval is not enough to merge**.

After materializing the normative text:

1. push the final diff;
2. request review from Mel again;
3. summarize exactly what changed after the first approval;
4. require explicit approval of the **final diff**.

Rule:

```text
MEL_FINAL_APPROVAL != TRUE
→ PR MUST NOT MERGE
```

Acceptable as final approval:

- a GitHub Review `APPROVED` from `melmonfre`; or
- an unambiguous maintainer comment authorizing the merge of the final diff, if the repo's review workflow does not allow a formal `APPROVED`.

Preference: formal `APPROVED` on GitHub.

Not acceptable:

- self-approval;
- the author's own approval;
- a bot's approval;
- another contributor's "LGTM";
- green CI without review;
- an approval predating subsequent normative commits;
- inference from silence.

---

# 14. PR checklist

## Baseline

```text
[x] fetch/rebase of beta-0.5.0
[x] tip recorded in the PR (6c9aeb847f167e97ed48c21a9e4453028198da63)
[x] VERSION measured (0.5.0-beta)
[x] last release measured (kof-0.4.9-beta-*)
[x] DOING/lane collision checked (none)
[x] current DECISIONS read
[x] VERSIONING EN/PT read
```

## Proposal

```text
[x] KOF-first evidence block included
[x] drift problem documented
[x] PATCH proposed for absence of a public-surface diff
[x] MINOR proposed for presence of a public-surface diff
[x] post-1.0 MAJOR aligned with incompatibility
[x] trigger separated from cut
[x] 100–150 commits = evaluation, never auto-release
[x] security/critical = extraordinary trigger
[x] D-RELEASE-1.0 fully preserved
[x] research references included as grounding, not contract
```

## Mel's approval — step A

```text
[x] review requested from melmonfre
[x] explicit decision recorded (PR #582 merged 22/09/2026)
[x] no normative change before the decision
```

## Materialization

```text
[x] DECISIONS EN/PT updated after approval (D-VERSIONING-RELEASE, 22/09/2026)
[x] VERSIONING EN/PT updated (current state + policy pointer)
[x] release-naming EN/PT aligned if needed (already consistent — no change)
[x] D-RELEASE refined without erasing history
[x] no VERSION/tag/release change
[x] no production code
```

## Quality

Commands executed in this unit and their real result (always record the command + real output, never claim an execution that did not happen):

```text
scripts/docs-lang.sh check   -> see result recorded in this unit's commit/PR
```

plus any docs/links/live-records gate required by the current `AGENTS.md`.

## Mel's approval — final diff

```text
[ ] new review requested after the last normative commit
[ ] Mel explicitly approved the final diff
[ ] no later commit invalidated the approval
[ ] applicable CI/gates green or pre-existing failures clearly demonstrated
```

## Merge

```text
[ ] MEL_FINAL_APPROVAL == TRUE
[ ] branch updated/rebased per repo policy
[ ] no lane conflict
[ ] merge allowed by current governance
```

If any critical item above fails:

```text
DO NOT MERGE
```

---

# 15. Suggested PR description text

```markdown
## Objective

Consolidate KOF's versioning and release-cut policy without changing the language or publishing a release.

Today the contract is spread across `VERSIONING.md`, `D-RELEASE`, the `0.5.0`-specific gate, and the `1.0` EXIT GATE.

The proposal separates two decisions:

1. **change classification** — PATCH / MINOR / MAJOR;
2. **eligibility to cut** — trigger → candidate → gate → release.

### Pre-1.0 proposal

- PATCH: no new/altered contracted public surface;
- MINOR: new/altered contracted public surface, or an approved pre-1.0 breaking change;
- 100–150 commits: an evaluation trigger, not release authorization;
- security/critical regression: an extraordinary evaluation trigger.

### Post-1.0

Strict SemVer, with compatibility analyzed across source, artifact/binary, behavioral, and cross-target parity.

### 1.0

`D-RELEASE-1.0` and `D-1.0-EDGES` remain fully in force and are not relaxed by this PR.

## Requested decision

@melmonfre — do you approve this consolidation as `D-VERSIONING-RELEASE`?

**This PR must not be merged without your explicit approval.**
After any approval of the proposal and materialization of the normative text, a new review of the final diff will be requested before merging.
```

---

# 16. Acceptance criteria

The work is only considered complete when:

```text
A. the current ambiguity is explicitly documented;
B. the proposed policy is grounded in the KOF contract + external evidence;
C. Mel made an explicit decision;
D. if approved, the decision was recorded EN+PT without erasing history;
E. VERSIONING stopped describing 0.0.x/Alpha as the current state;
F. the release trigger is separated from classification and from the cut;
G. D-RELEASE-1.0 remained intact;
H. the final diff received Mel's explicit approval;
I. only then may the PR be merged.
```

---

# 17. Do not

```text
DO NOT touch production code.
DO NOT implement parser/typer/runtime changes.
DO NOT change VERSION.
DO NOT create a tag.
DO NOT publish a release.
DO NOT self-ratify D-VERSIONING-RELEASE.
DO NOT mark it DECIDED before Mel.
DO NOT turn 100–150 commits into auto-release.
DO NOT weaken the 1.0 EXIT GATE.
DO NOT use external SemVer as an authority above the KOF contract.
DO NOT merge with approval only from another contributor/bot.
DO NOT consider an old approval valid after a later normative change without re-review.
```

---

# 18. Expected outcome

At the end, if approved:

```text
CHANGE
   ↓
PUBLIC CONTRACT SURFACE DIFF?
   ├── NO  → PATCH candidate
   └── YES → MINOR candidate (+ Decision ID)

AFTER 1.0:
   compatible fix        → PATCH
   compatible capability → MINOR
   incompatible contract → MAJOR

VERSION CLASSIFICATION
   ↓
RELEASE TRIGGER
   ↓
RELEASE EVALUATION
   ↓
IMMUTABLE CANDIDATE
   ↓
LINE-SPECIFIC GATE
   ↓
MAINTAINER CUT
```

And for the first Stable:

```text
D-RELEASE-1.0 EXIT GATE
        ↓
100% GREEN
        ↓
1.0.0
```

---

## External grounding references

- Semantic Versioning 2.0.0 — https://semver.org/
- Rust stability / release trains — https://blog.rust-lang.org/2014/10/30/Stability/
- Rust Editions — https://doc.rust-lang.org/edition-guide/editions/
- Go 1 Compatibility Promise — https://go.dev/doc/go1compat
- Python release cycle / annual release model — https://peps.python.org/pep-0602/
- Maven/SemVer empirical study — https://arxiv.org/abs/2110.07889
- Go/SemVer empirical study — https://arxiv.org/abs/2309.02894
- Rapid release empirical study — https://arxiv.org/abs/2103.08648
- Security fix/release empirical study — https://arxiv.org/abs/2112.06804

**Final rule:** external references ground; `DECISIONS.md` governs.
