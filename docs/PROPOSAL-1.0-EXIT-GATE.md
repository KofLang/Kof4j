[English](PROPOSAL-1.0-EXIT-GATE.md) | [Português](PROPOSAL-1.0-EXIT-GATE.pt_BR.md)

# KOF 1.0 — Exit Gate Contract Proposal

**Status:** RATIFIED by the maintainer on 09/20/2026 — recorded as `D-RELEASE-1.0 — KOF 1.0 EXIT GATE` in `docs/development/DECISIONS.md` (chat: "decisão [...] ratificada. concordo com o planejamento [...] kof RC 1.0.0 e kof release 1.0.0 só existem QUANDO todos os pontos estiverem correspondentes e não houver nenhuma aresta aberta")  
**Tracking issue:** [#560](https://github.com/KofLang/Kof4j/issues/560) (DESIGN REQUEST to the maintainer)  
**Location:** `docs/development/` — promoted from `future/` by the ratification: normative plan with its §23 queue now binding development meta.  
**Revision:** v3.1 — v3 corrected after the 09/20/2026 revalidation (section 24); aligned with the repository state, the maintainer's decisions/publications and a weighted (non-normative) external benchmark  
**Repository:** `KofLang/Kof4j`  
**Current active branch:** `lab` (`D-BRANCH-PIPELINE`; `beta-0.5.0`/`D-BRANCH-0.5.0` `SUPERSEDED` by the 28/09 cutover)  
**Previous branch:** `beta-0.4.0` — only in-flight landings + release prep, per the maintainer's decision  
**Reviewer / required decision authority:** **Mel (`melmonfre`)**  
**Nature:** exit contract for a future KOF 1.0; it is NOT authorization to cut 1.0 now.

> ## MEL'S REVIEW AND APPROVAL — RATIFIED 09/20/2026
>
> The maintainer approved the contract and the plan as written (see §22 for the
> verbatim evidence and §23 for the queue it opens). From this record on, the
> EXIT GATE (§8) is the binding development meta: **no Kof RC 1.0 and no Kof
> release 1.0 exist while any mandatory item is unmet or any edge is open.**
>
> The original safeguard stands for the future: no agent may ratify anything on
> Mel's behalf; only she fills approval blocks and answers open sub-questions.
> The KofC/Android surface, the declaration that opens the first RC and the
> reinforcement candidates were all answered by Mel on 09/20/2026
> (`D-1.0-EDGES`); they are no longer open edges.
>
> After Mel's explicit approval this contract:
>
> 1. became a normative decision in `docs/development/DECISIONS.md` (`D-RELEASE-1.0`);
> 2. must be synchronized with the release/versioning documents (queue);
> 3. must be turned into mandatory automatic gates (queue — REDs first);
> 4. guides the cut of the first 1.0 Release Candidate (last queue step).

> ## What changed from v3 to v3.1 (section 24 revalidation, 09/20/2026)
>
> Factual corrections only; **no decision was taken and no requirement was added or removed.**
>
> 1. **Section 6** — the cited tip (`47a7b8f9`) is now an ancestor of the current one; the condition "no residual landing only in `beta-0.4.0`" was **measured and is NOT satisfied today** (3 commits, all by Mel herself).
> 2. **Sections 4/5** — the code-scanning API returns **38 open alerts** on `refs/heads/beta-0.5.0`, a different number from the **21** in the original text of #555.
> 3. **Section 13** — the claim that the release/versioning docs cite `kofc` was **not confirmed** (0 mentions in `docs/distribution/`); `kofc` appears in the README and in the architecture docs.
> 4. **Sections 13/16/21** — the public site (`koflang.github.io`) diverges from the checklist's target list and from `VERSION`: v0.4.1-beta, KofC "Disponível" (Available), KofJS "Em desenvolvimento" (In development).
> 5. **Section 28.3** — Rahman's thesis numbers **could not be verified** and were removed; the principle stays, marked as unconfirmed.
>
> Full record of what was / was not verified: **section 37**.

---

## 0. Read first

This is the **ratified 1.0 exit-gate contract** (`D-RELEASE-1.0`). Read:

- **§25 Executive summary** — the two-minute version.
- **§8 EXIT GATE** — the contract; **§13** the target checklist (the 8-target
  Stable 1.0, KofC + Android inside, per `D-1.0-EDGES`).
- **§21 Q1–Q8** — the maintainer's decisions (all resolved; only **EG-8** — the
  maintainer's own declaration — is still open).
- **§23 After Mel's approval** — the **execution order** (the queue), tracked in
  `roadmap.md` §24.
- **§35** — the EXIT GATE v3 candidate block; **§37** the 09/20 revalidation
  record (v3.1).
- **§24** — the rule for updating this document.
- **`D-1.0-STABILITY-100`** (`DECISIONS.md`, 20/09) — the closure refinement:
  no 1.0.0 while ANY item is open in `docs/development/`,
  `docs/development/future/` or `docs/bugs-and-gaps/`, with the full
  cross-target parity MEASURED (proven, never claimed).

Live status is measured mechanically by `scripts/check_release_050_gate.sh`
(the 0.5.0 conditions) and `scripts/check_release_blockers.sh` (the EG queue /
`--rc-gate`), never by eye.

---

# 1. Real state that governs this review

This revision replaces the earlier reading that still treated `beta-0.4.0` as the active line.

The decision in force is:

```text
D-BRANCH-0.5.0
```

Recorded in `docs/development/DECISIONS.md` on 09/20/2026.

The maintainer's order, recorded in the document itself, was:

> "avise os outros agentes, vamos mover todo trabalho pra branch beta-0.5.0 e começar a preparar a nova release"

Normative consequences already published:

```text
beta-0.5.0 = active branch
beta-0.4.0 = only in-flight landings + release prep
```

All new code and documentation work must go to `beta-0.5.0`.

When something already started in `beta-0.4.0` still lands, the docs lane must absorb that landing into `beta-0.5.0`.

The final number of the new release, the version bump, the CHANGELOG and the tag are **the maintainer's decision at the moment of the cut**.

Therefore:

> **This document does NOT presume that the next release is 1.0.**

The project is preparing the `beta-0.5.0` line. The contract below defines what must be true when Mel decides to actually start preparing KOF 1.0.

---

# 2. What already exists in KOF's release contract

The current documentation already defines:

```text
Beta → Release Candidate → Stable
```

And establishes, in essence:

- **Beta:** development still open, with features evolving;
- **RC:** target parity and a stabilization phase;
- **Stable:** 1.0 with a compatibility guarantee.

This means the `1.0 EXIT GATE` does not create the idea of RC or Stable from scratch.

It tries to make the passage between those stages **measurable**.

---

# 3. Important correction relative to the earlier proposal

The earlier version could be read as if the project should freeze features now.

That is not correct.

The correct wording is:

```text
BETA
│
├── features may keep landing
├── bugs are fixed
├── gaps are closed or made explicit
├── targets evolve
├── contracts are decided
└── the 1.0 surface is prepared
        │
        │  only when Mel authorizes the RC cut
        ▼
   FIRST 1.0 RC
        │
        └── freeze of the 1.0 public surface
```

Therefore:

> **There is no automatic feature freeze on `beta-0.5.0`.**

The proposed freeze starts **only after the cut of the first 1.0 RC**, and only if Mel approves that rule as part of the contract.

---

# 4. KOF-first evidence block

```text
KOF VALIDITY:
N/A — this document is about release/governance, not KOF syntax.

CONTRACT SOURCE:
- docs/development/DECISIONS.md — especially D-BRANCH-0.5.0;
- docs/distribution/release-naming.md;
- docs/distribution/VERSIONING.md;
- AGENTS.md — Quality Gate / no bug ships / zero regression / suite as gate;
- docs/distribution/release-beta-0.5.0.md (acceptance record, CLOSED 28/09).

CURRENT KOF IDIOM:
Beta → RC → Stable is already the official progression.
The active branch is now beta-0.5.0 by Mel's explicit decision.
The bump/tag/release number remain the maintainer's decision.

MEASUREMENT:
Snapshot consulted on 09/20/2026:
- beta-0.5.0 tip observed in v3: 47a7b8f9af12cc10f471b811f461d7e778053c02;
- beta-0.5.0 tip at the v3.1 revalidation (09/20/2026): 9ee038f7 — the tip changes with every landing, always revalidate;
- beta-0.4.0 = 4ee3a5c9 and still carries 3 commits that beta-0.5.0 has not absorbed (section 6);
- open CodeQL alerts in the API for refs/heads/beta-0.5.0: 38 (the original #555 text said 21);
- #550 CLOSED;
- #553 CLOSED;
- #554 CLOSED;
- #555 OPEN;
- Code Quality Analysis on beta-0.5.0 was GREEN;
- CodeQL Gate (alerts API) was RED;
- kof-security-bot was GREEN;
- VERSION on beta-0.5.0 was still 0.4.7-beta;
- D-BRANCH-0.5.0 forbids a unilateral bump.

CLASSIFICATION:
DESIGN REQUEST — release/governance contract for 1.0.

DUPLICATE / PRECEDENT CHECK:
- D-RELEASE covers patch/release evaluation;
- D-BRANCH-0.5.0 governs the current active line;
- release-beta-0.5.0-prep.md governs the preparation of the next beta;
- no already-ratified D-RELEASE-1.0 was found.

ACTION:
Submit this contract to Mel.
Only after explicit approval:
- record the normative decision;
- synchronize docs;
- automate gates;
- define the first 1.0 RC candidate.
```

---

# 5. Current state of the new release's preparation

The official `beta-0.5.0` queue was created by the `D-BRANCH-0.5.0` decision itself.

Since the first version of this document:

```text
#550 — CLOSED
#553 — CLOSED
#554 — CLOSED
#555 — OPEN
```

The three bugs/tools that were in the way of preparing the new beta have already been closed with recorded proof.

The largest gate still explicitly open is:

```text
#555 — Quality Gate / CodeQL debt + gate reliability
```

The last completed run inspected before the new tip showed:

```text
Code Quality Analysis  = GREEN
CodeQL Gate            = RED
Security bot           = GREEN
```

At tip `47a7b8f9...`, the workflows were still queued/running during this review. Issue `#555` remained OPEN. Therefore the previous state must not be promoted to the new SHA without a new measurement.

**v3.1 revalidation (09/20/2026):** in the last completed `kof-quality-bot` run (SHA `7b2dd963`) the `Code Quality Analysis` job was `success` and the `CodeQL Gate (alerts API)` job was `failure`; `kof-security-bot` was `success`. The code-scanning API returned **38 open alerts** on `refs/heads/beta-0.5.0`, against the **21** cited in the text of #555 (measured, at the time, on tip `817c27f2` of `beta-0.4.0`). The difference may be new debt, a different per-ref count, or both — **always measure with the gate itself on the candidate SHA, never from the issue text.**

Hence the 1.0 requirement:

```text
Quality/Security gates green and trustworthy
```

**still CANNOT be marked as done.**

---

# 6. The branch transition must also be part of the preparation

Mel's decision determines that `beta-0.4.0` receives only in-flight landings and that these landings are absorbed by `beta-0.5.0`.

In the v3 snapshot:

```text
beta-0.5.0 = 47a7b8f9...
beta-0.4.0 = 4ee3a5c9...
```

**Measurement at the v3.1 revalidation (09/20/2026):**

```text
beta-0.5.0 = 9ee038f7   (the tip cited in v3, 47a7b8f9, is now an ancestor)
beta-0.4.0 = 4ee3a5c9
beta-0.5.0 relative to beta-0.4.0: 16 ahead / 3 behind
```

The 3 commits that exist **only** in `beta-0.4.0` (not yet absorbed by `beta-0.5.0`) are all landings by Mel herself (MakeAlive/GAPS-DB): `4ee3a5c9`, `ce5e8e66` and `d3f79e7a`.

Therefore the condition "no residual landing exclusive to `beta-0.4.0`", listed below, **is NOT satisfied today** — and absorbing those landings is the docs lane's job, per the decision.

That does not by itself violate the decision — because the decision explicitly allows in-flight landings —, but it creates an objective condition that must be resolved before any future RC line:

```text
[ ] no residual landing exclusive to beta-0.4.0
[ ] everything that must survive was absorbed by the active branch
[ ] beta-0.5.0 (or the successor Mel indicates) is the only active source
```

---

# 7. Proposed normative decision

Suggested identifier, **only after Mel's approval**:

```text
D-RELEASE-1.0 — KOF 1.0 EXIT GATE
```

Proposed contract:

> A build may only be declared **Release Candidate of KOF 1.0** when every mandatory item of the EXIT GATE is satisfied with reproducible evidence on the same candidate.
>
> An RC may only be promoted to **Stable 1.0** when the EXIT GATE remains green and the RC → Stable round introduces no regression.
>
> The public surface of 1.0 is frozen **from the first RC approved by Mel**, and not during the Beta.

---

# 8. KOF 1.0 EXIT GATE

```text
[ ] main CI green
[ ] Quality/Security gates green and trustworthy
[ ] 0 OPEN bugs classified as release-blocker
[ ] real package tested outside the repo
[ ] JVM green
[ ] x86-64 green
[ ] riscv64 green
[ ] aarch64 green
[ ] JS green
[ ] Script green
[ ] KofC green (own gate — `D-1.0-EDGES`)
[ ] Android green (own gate — `D-1.0-EDGES`)
[ ] golden byte parity where the contract requires it
[ ] all remaining gaps explicitly outside the 1.0 surface
[ ] VERSION / docs / release metadata synchronized
[ ] RC with no new feature
[ ] only fixes during the RC
[ ] RC → Stable without regression
[ ] 100% of the items in docs/development/, docs/development/future/ and
    docs/bugs-and-gaps/ resolved, with full cross-target parity PROVEN
    (D-1.0-STABILITY-100)
```

### Complement aligned with decision D-BRANCH-0.5.0

Before the first RC:

```text
[ ] the active branch defined by Mel has converged
[ ] no relevant residual landing stayed only in beta-0.4.0
[ ] the previous release-prep was closed correctly
[ ] Mel explicitly declared that the 1.0 line/candidate has started
```

---

# 9. Main CI green

To mark:

```text
[ ] main CI green
```

it is necessary to have:

- a candidate identified by SHA;
- CI on the same SHA;
- build and tests executed;
- structural gates executed;
- mandatory jobs not skipped because of an earlier failure;
- golden/integration executed when they are part of the workflow;
- cross tests executed where the official toolchain exists;
- no justification based only on a local test.

A previous green SHA does not validate a later SHA.

---

# 10. Quality/Security gates green AND trustworthy

The requirement is not merely visual.

Minimum criteria:

```text
[ ] the scan and the verdict belong to the same SHA
[ ] empty API != unavailable API
[ ] a stale analysis cannot decide a new commit
[ ] an old alert from another lane cannot be attributed to the current change
[ ] the debt inventory and the merge gate have clear semantics
[ ] there is no routine use of CODEQL_GATE_SKIP=1
[ ] there is no known false-green
[ ] the gate's own logic has automated regression
```

The current state of `#555` shows this area still needs closing.

**Status (EG-2, closed 20/09/2026):** the security lane's `scripts/codeql-gate.sh`
+ `scripts/codeql-baseline.txt` (`0d2a019d`) closed six of the eight criteria
(API failure is INCONCLUSIVO; no routine `CODEQL_GATE_SKIP`; `state:null`
handled; baseline carries owner/date/reason; automated regression). The
stability lane closed the remaining two in the same file (coordinated,
maintainer-approved), RED-first in `scripts/tests/codeql-gate-test.sh` (now 10
scenarios, registered in `run-agent-tests.sh`):
1. **empty ≠ unavailable** — an API failure is tracked in `api_ok`, never
   inferred from an empty list; a legitimately empty alert list is GREEN, not
   INCONCLUSIVO.
2. **SHA binding** — the latest analysis SHA (code-scanning analyses API) is
   compared with the branch tip (branches API); a stale or absent analysis is
   INCONCLUSIVO (rc=2, non-blocking), never green. A new alert still wins as RED.
Live gate 20/09 measured the mechanism working (main/beta-0.4.0 stale → rc=2;
beta-0.5.0 flagged a new alert #945, already fixed at the source by `9c88d590`,
awaiting re-scan).

The final technical solution for the Quality Gate is its own front and must not be self-ratified by this document.

---

# 11. Zero OPEN release-blockers

The requirement is:

```text
OPEN release-blocker = 0
```

But the mere absence of a label called `release-blocker` does not prove that.

Before the RC, Mel must approve the classification mechanism.

Every open issue must be explicitly in one of these categories:

```text
BLOCKS 1.0
OUTSIDE 1.0 SURFACE
POST-1.0
NOT A BUG / CLOSE
TRACKING (release-process / umbrella)
```

No ambiguous issue may be ignored merely because it received no label.

**Decided (`D-1.0-EDGES`, 20/09/2026):** the categories are **five** — a fifth,
`tracking/contract`, was added for release-process/umbrella issues (the #560
tracking thread); it is valid during stabilization but must still close before
the RC. The mechanism is landed (EG-1): labels + `scripts/release-blockers.tsv`
+ `scripts/check_release_blockers.sh` (RED when any OPEN issue has zero or 2+
categories; `--rc-gate` fails while any `1.0-blocks` is open). Current state:
#561/#563/#564/#565 = `1.0-blocks`; #560 = `tracking/contract`; #555 CLOSED.

---

# 12. Real package tested outside the repo

This condition gained important practical evidence with the closing of #550.

#550 showed exactly why the gate is necessary: something could work inside the tree and fail in the distributed CLI by depending on repo paths.

The 1.0 proof must use the real package:

```text
build/package
      ↓
copy/extract to a clean directory
      ↓
no access to the source tree
      ↓
kof version
kof info
build/run
      ↓
per-target smoke
```

The artifact the user installs is the object of the validation.
**Mechanism (landed 20/09, EG-3 — `D-RELEASE-1.0` queue §23 item 9):**
`scripts/test-package-outside-repo.sh` automates exactly the flow above — it
builds the dist, extracts the **real tar.gz** into a clean directory under
`$HOME` (never `/tmp`, repo rule 9) with the repo's env unexported, then runs
`kof version → kof info → kof new → per-target run of the template → pure-Kof
lib resolution` (`kof.pdf` from `lib/kof-libs` — the #550 class of bug), with
honest preflights (missing JDK ≥ 25 / node / cross toolchain fail loud, never
fake-green; cross targets are build-only here — exec lives in the final matrix,
§23 item 10). Measured PASS on 20/09 with `kof-0.4.7-beta-linux-x86_64`
(jvm+script+js+native). Offline RED-first proof for the agent suite:
`scripts/tests/test-package-outside-repo-test.sh`. The **RC-day re-run on the
same candidate** remains what satisfies this checklist item — the mechanism
only makes that re-run one command.


---

# 13. Targets proposed in the checklist

The requested checklist contains:

```text
JVM
Native x86-64
Native riscv64
Native aarch64
JS
Script
```

This list must be reviewed by Mel before ratification.

There are two current surfaces that must not be silently forgotten:

**v3.1 revalidation note:** the public site (`koflang.github.io`), read on 09/20/2026, presents the targets as follows — JVM, Native x86_64, Native RISC-V/ARM64, KofScript and **KofC** as "Disponível" (Available), and **KofJS** as "Em desenvolvimento" (In development) — at version **v0.4.1-beta**. That diverges from the checklist list above (which includes JS and does not include KofC) and from the repository's `VERSION` (`0.4.7-beta`). Whatever Mel decides for Q2/Q7, the site must say the same thing as the contract.

## KofC

**v3.1 correction:** v3 stated that the release/versioning documents cite `kofc`. That was **not confirmed**: on 09/20/2026 there is no mention of `kofc` in `docs/distribution/` (release-naming, VERSIONING, RELEASES, PACKAGING, INSTALL…). `kofc` appears in `README.md` and in architecture documents (`docs/architecture/`), and the public site marks it "Disponível". The question below remains valid, but its grounds are now the README, the architecture and the site — not the release docs.

Mandatory question:

```text
Is KofC part of the Stable 1.0 Surface?
```

If yes, it needs its own gate.

If not, it must be explicitly outside the 1.0 surface.

**Decided (`D-1.0-EDGES`, 20/09/2026): yes — KofC is part of the Stable 1.0
Surface and needs its own gate.** The site's "Disponível" is now consistent
with the contract.

**Gate mechanism (landed 20/09, EG-9):** `scripts/test-kofc-gate.sh` — it
preflights the native toolchain (`as` + `ld`/`gcc`) and a JDK ≥ 25 (missing =
loud failure naming the tool, never a silent skip), compiles **and executes**
the supported corpus (5 cases, real ELF run with stdout asserted) and rejects
malformed input without emitting a binary (the #485 class — R6/Q7). The verdict
is bound to the SHA (`KOFC-GATE: PASS sha=…`). RED-first offline proof:
`scripts/tests/test-kofc-gate-test.sh` (5 scenarios, registered in
`run-agent-tests.sh`). Measured PASS on `8fa39ff9`.

## Android

The repository has Android under evolution and recent decisions say, on specific faces, that "Android is JVM" and that it must share behavior where that parity was decided (verified: `docs/development/DECISIONS.md`, record of the DB faces; Android also appears in `docs/distribution/INSTALL.md`).

That does not automatically mean all of Android already belongs to the Stable 1.0 Surface.

Mel must decide:

```text
Android = Stable 1.0
or
Android = experimental / post-1.0
```

**Decided (`D-1.0-EDGES`, 20/09/2026): Android = Stable 1.0, with its own
gate** (the full option, not the partial one; CI already runs the APK).

**Gate mechanism (landed 20/09, EG-10):** `scripts/test-android-gate.sh` —
honest preflight (JDK ≥ 25 + `jar`; `ANDROID_HOME` with a complete build-tools
≥ 35 and a platform `android-N/android.jar`); an absent SDK is an honest SKIP
(exit 3) **naming what is missing** — never a silent green (R6) — and the CI
`android.yml` runs the SAME gate. With the SDK present it runs
`kof build --target android --apk` (the standalone
aapt2→d8→zip→zipalign→apksigner pipeline) and proves the artifact is a real
zip carrying `AndroidManifest.xml` + `classes.dex`; the verdict is bound to the
SHA (`ANDROID-GATE: PASS sha=…`). RED-first offline proof:
`scripts/tests/test-android-gate-test.sh` (6 scenarios, registered in
`run-agent-tests.sh`).

---

# 14. Golden byte parity

Where KOF's contract requires identical observable behavior:

```text
same KOF program
        ↓
JVM / Native / JS / Script
        ↓
stdout + exit + other observable bytes
        ↓
byte parity
```

there must be proof on the candidate.

Where a target difference was explicitly decided, an artificial parity is not invented.

**Mechanism (landed 20/09, EG-5 — `D-RELEASE-1.0` queue §23 item 10):**
`scripts/target-matrix.sh` runs the SAME Kof program across the core surface and
proves byte parity against the JVM oracle in one command: `jvm`, `native`
(x86-64), `native.riscv64`, `native.aarch64`, `js`, `script` — cross builds with
the cross toolchain and EXECUTES under `qemu-<arch>` when present. Honest
preflights (R6/R7): a missing build toolchain on a core target is `FAIL`; a
missing qemu for cross is an honest `SKIP` that yields `INCOMPLETE` (rc=2, never
a fake green); `kofc` (EG-9) and `android` (EG-10) are reported as `DELEGATED`
rows, each with its own gate. Measured PASS on 20/09 on the six core targets
(identical stdout, JVM oracle). Offline RED-first proof for the agent suite:
`scripts/tests/target-matrix-test.sh` (comparator rejects a divergent target and
accepts equality; a coherent matrix PASSes; a divergent core target FAILs naming
it; preflight without a JDK exits high) — registered in `run-agent-tests.sh`.
The **RC-day run on the same candidate** remains what satisfies this checklist
item; the harness only makes that run one command. The **0.5.0 release gate**
(`scripts/check_release_050_gate.sh`, condition 1) auto-runs this harness and
reads its `PARITY: 100%` line, so parity is MEASURED, never by eye.

---

# 15. Gaps outside the Stable 1.0 Surface

1.0 does not necessarily need to implement every idea in the roadmap.

But every remaining gap must be in one of two states:

```text
INSIDE 1.0
→ must be closed

OUTSIDE 1.0
→ must be explicitly documented
```

For a gap to stay outside 1.0 without blocking:

- the affected target is known;
- the behavior/diagnostic is known;
- no silent fallback;
- docs updated;
- no document may call the face Stable by mistake;
- the scope decision is recorded.

The authority for that decision is Mel.

---

# 16. VERSION / docs / metadata

**Before the bump** the repository showed, on `beta-0.5.0`:

```text
VERSION = 0.4.7-beta
```

This is consistent with `D-BRANCH-0.5.0`, because the decision itself says the version bump is a release-prep item and the number is confirmed by the maintainer at the cut; no agent bumps unilaterally.

**Updated (`D-VERSION-BUMP-0.5.0`, 20/09/2026):** the maintainer ordered the bump, so on `beta-0.5.0`:

```text
VERSION = 0.5.0-beta
```

`scripts/bump-version.sh` synced `VERSION` + `pom.xml` + `version.properties` + the current-version doc stamps (EN+PT); history was left intact.

**Metadata drift (measured 09/20/2026):** the in-repo release docs were synced by
the bump (`release-naming.md` and `INSTALL.md` now say "Current version:
0.5.0-beta"). The only remaining drift is the **public site**
(`koflang.github.io` still shows `v0.4.1-beta`, and KofJS "Em desenvolvimento"
while JS is in the 1.0 surface) — **maintainer-owned and deferred**: she will
update the site shortly (chat 20/09). The repository side of EG-7 is done.

For the future 1.0 candidate, synchronize at least:

```text
VERSION
pom.xml
packaged version.properties
CHANGELOG
AGENTS header
release docs
public site (koflang.github.io)
target/support matrix
release notes
tag
artifacts
```

---

# 17. Feature freeze — corrected wording

The checklist keeps:

```text
[ ] RC with no new feature
[ ] only fixes during the RC
```

But the proposed normative interpretation is:

> **After the cut of the first 1.0 RC, the public surface approved for KOF 1.0 is frozen.**

During the RC the following remain allowed:

- bug fixes;
- security fixes;
- parity fixes;
- tests needed to prove fixes;
- documentation;
- packaging fixes;
- CI/release-engineering fixes;
- refactors strictly necessary for stability, without expanding the contract.

Not allowed without a new decision by Mel:

- new syntax;
- new semantics;
- new Stable public API;
- new stdlib feature outside the already approved surface;
- target/domain expansion;
- a "small" feature added just because it looks cheap.

If a blocker requires changing the public surface:

```text
Mel decides:
A) change the contract and restart the RC validation
or
B) defer to post-1.0
```

---

# 18. Beta-0.5.0 remains open to development

This point must stay explicit to adhere to the maintainer's current direction.

`D-BRANCH-0.5.0` says all new work goes to the new active branch.

Therefore this document must not treat `beta-0.5.0` as a disguised RC.

The sequence is:

```text
beta-0.5.0
    ↓
new beta releases if Mel decides so
    ↓
1.0 surface explicitly approved
    ↓
first 1.0 RC
    ↓
surface freeze
    ↓
fixes / stabilization
    ↓
Stable 1.0
```

This contract does not try to decide how many Betas will exist before the RC.

---

# 19. RC → Stable

Promotion only happens if:

```text
EXIT_GATE(final_rc_sha) = GREEN
AND
release_blockers = 0
AND
new_public_features_since_rc_cut = 0
AND
regressions_since_rc_cut = 0
```

And the Stable artifact must be validated again.

Never use "the previous RC was green" as proof for a different SHA.

---

# 20. Observed situation versus the future EXIT GATE

| Item | Situation observed in this review | Can it be checked? |
|---|---|---|
| main CI green | no 1.0 candidate declared | NO |
| Quality/Security trustworthy | Security green; Quality Gate red (`CodeQL Gate (alerts API)` = failure on 09/20); #555 open; API shows 38 open alerts on beta-0.5.0 | NO |
| 0 release-blockers | 1.0 triage not yet ratified | NO |
| package outside the repo | #550 proved the pattern and was closed; it is not yet proof for a 1.0 candidate | NO |
| JVM green | no 1.0 candidate | NO |
| x86-64 green | no 1.0 candidate | NO |
| riscv64 green | no 1.0 candidate | NO |
| aarch64 green | no 1.0 candidate | NO |
| JS green | no 1.0 candidate | NO |
| Script green | no 1.0 candidate | NO |
| byte parity | no final round of the 1.0 candidate | NO |
| gaps outside the surface | 1.0 surface not yet approved | NO |
| metadata synchronized | VERSION stays 0.4.7-beta by correct release-prep decision; drift already present: public site at v0.4.1-beta and `release-naming.md`/`INSTALL.md` at "Current version 0.4.0-beta" | NO |
| active branch converged (section 6) | 3 commits only in `beta-0.4.0` (all by Mel) not yet absorbed by `beta-0.5.0` | NO |
| RC with no new feature | no 1.0 RC exists yet | N/A |
| fixes-only during RC | no 1.0 RC exists yet | N/A |
| RC → Stable without regression | no 1.0 RC exists yet | N/A |

The checkboxes must not be pre-greened before a real 1.0 candidate exists.

---

# 21. Points Mel needs to decide before ratification

**Answered (`D-1.0-EDGES`, 20/09/2026):** Q1, Q2 and Q7 below are now decided
(see the note on each). Q3–Q6 and Q8 were already ratified in `D-RELEASE-1.0`.

## Q1 — When does the 1.0 line formally start?

The current preparation is for `beta-0.5.0`.

```text
Which event/decision ends the Beta sequence and opens the preparation of the first 1.0 RC?
```

**Answered:** the 1.0 line opens **after the 0.5.0 release is cut and EG-1..EG-7
are closed**; then Mel declares it and the first RC candidate is cut (EG-8).

## Q2 — 1.0 target surface

Confirm explicitly:

```text
[ ] JVM
[ ] Native x86-64
[ ] Native riscv64
[ ] Native aarch64
[ ] JS
[ ] Script
[ ] KofC ?
[ ] Android ?
```

Data for the decision (measured 09/20/2026): the public site marks KofC as "Disponível" and KofJS as "Em desenvolvimento"; the v3 checklist has the opposite (JS in, KofC out, no decision).

**Answered:** the surface is the six above **plus KofC and Android** — eight
targets, each with its own gate. KofC and Android are Stable 1.0 targets.

## Q3 — Release blocker

Which mechanism is normative: GitHub label, milestone, ledger, release-prep, or a combination?

## Q4 — Freeze

Approve or change:

> the freeze starts only after the first 1.0 RC and freezes the public surface, not the stabilization work.

## Q5 — Gaps

Approve or change:

> a gap may remain in 1.0 only if it is explicitly outside the Stable Surface and has honest/documented behavior.

## Q6 — Quality Gate

Define the level of rigor:

> green is not enough; the gate must prove it is analyzing the correct SHA and does not produce a known false-green/false-red.

## Q7 — KofC and Android

Decide whether they are Stable 1.0 targets or separate/experimental surfaces.

If KofC or Android stay outside the Stable 1.0 Surface, the public site, the README and the architecture docs must make that explicit (today the site marks KofC as "Disponível").

**Answered:** both are **Stable 1.0 targets with their own gates**. No
site/README "outside" note is needed; the site's KofC "Disponível" is now
consistent with the contract.

## Q8 — Ratification

If approved, record as:

```text
D-RELEASE-1.0 — KOF 1.0 EXIT GATE
```

or use the identifier Mel prefers.

---

# 22. Approval block

```text
MAINTAINER REVIEW — MEL

[x] APPROVED AS WRITTEN
[ ] APPROVED WITH CHANGES
[ ] REQUEST CHANGES
[ ] REJECTED / SUPERSEDED

Reviewer: Mel Santos (maintainer) — via chat, recorded by the docs lane as
          instructed ("decisão ... ratificada. concordo com o planejamento")
Date: 09/20/2026
Decision reference / commit: D-RELEASE-1.0 — KOF 1.0 EXIT GATE (docs/development/DECISIONS.md)
Notes: Maintainer's words (verbatim, PT): "setar como meta de desenvolvimento a
  estabilização dos contratos seguindo o planejamento existente nessa issue.
  kof RC 1.0.0 e kof release 1.0.0 só existem QUANDO todos os pontos estiverem
  correspondentes e não houver nenhuma aresta aberta". The EXIT GATE becomes
  binding development meta; the §23 queue opens on beta-0.5.0. Ratification
  covers the contract and the plan — the still-open sub-questions of §21
  (KofC and Android inside the Stable 1.0 surface; the exact declaration that
  opens the first RC) are themselves "arestas abertas": each must be closed by
  the maintainer BEFORE the first RC candidate, per the no-open-edge rule.
```

No agent fills this block on the maintainer's behalf. *(The block above was
filled by the agent ONLY as the mechanical record of the maintainer's explicit
chat ratification of 09/20/2026, quoting her words as evidence — not on her
behalf.)*

---

# 23. After Mel's approval

Only after approval:

1. update the active branch indicated by Mel;
2. re-read `DECISIONS.md`, `AGENTS.md` and the current release-prep;
3. record the normative decision;
4. synchronize EN/PT;
5. define `release-blocker` mechanically;
6. implement the machine gate;
7. write REDs for the gate before changing logic;
8. validate BEFORE/AFTER;
9. test the real package;
10. run the final matrix;
11. only then create the first 1.0 RC candidate.

---

# 24. Rule for updating this document

KOF is changing fast.

Before any ratification:

```text
git fetch origin
git checkout beta-0.5.0
git pull --rebase origin beta-0.5.0
git log -1 --oneline
```

and revalidate:

```text
DECISIONS.md
AGENTS.md
current release-prep
open issues
open PRs
CI
CodeQL
backend-parity
known-bugs
gaps
VERSION
CHANGELOG
```

If Mel changes again the active branch, the release number or the intended surface, **the maintainer's newest decision prevails** and this document must be updated before it becomes a norm.

---

# 25. Executive summary

```text
TODAY
beta-0.5.0
development continues
the new release is being prepared
#550/#553/#554 closed
#555 still open
VERSION must not be bumped unilaterally
        │
        ▼
FUTURE — when Mel decides to prepare 1.0
        │
        ├── define the Stable surface
        ├── zero release-blockers
        ├── close/confine gaps
        ├── trustworthy gates
        ├── real package
        └── final matrix
                │
                ▼
            1.0 RC
                │
                ├── public-surface freeze
                ├── fixes/security/parity/docs/release only
                └── zero regression
                        │
                        ▼
                    Stable 1.0
```

> **The KOF 1.0 EXIT GATE must respect the release line Mel is conducting right now. It does not force 1.0, does not freeze the Beta and does not anticipate decisions that belong to the maintainer. It only defines the evidence the project will have to demand when Mel decides the time for 1.0 has come.**

---

# 26. External benchmark rule — updated by the evidence criterion

The external research of this review follows an explicit rule:

> **What comes from outside serves as comparative inspiration. It does not define
> KOF's direction and never overrides Mel's decisions, `DECISIONS.md`, the tests,
> the normative documentation or the project's measured implementation.**

The order remains KOF-first:

```text
KOF
DECISIONS
contract/documentation
tests/goldens
parity/gaps
measured implementation
        ↓
proven gap/problem
        ↓
external research
        ↓
translating the principle back to KOF
        ↓
Mel's review/approval when it changes the contract
```

## 26.1 Weights used in this review

The weighting was adjusted so that **empirical academic research, real tests and
real cases dominate the analysis**.

| Weight | Source | How it enters the analysis |
|---:|---|---|
| **5** | **Quality academic/empirical research + real test + real case with observed data** | dominant external evidence; used to look for mechanisms that really worked or failed |
| **3** | Mature, widely used repository with a release process executed repeatedly | strong operational precedent |
| **2** | Official documentation/process of other languages/projects | design/process reference, not sufficient proof alone |
| **1** | Forums/discussions/community comments | weak signal; helps discover problems and experiences, never to decide the contract |

### Additional rule

Duplicated sources are not artificially summed.

A paper and a page that merely reproduces the same paper count as **one
evidence item**, not two.

Likewise:

```text
popularity ≠ proof
documentation ≠ measured result
forum ≠ contract
```

---

# 27. Mel's specific evidence outside Git

Mel's public posts do not replace `DECISIONS.md`, but they help interpret the
project's intent when they are consistent with what later appears in the
repository.

Two public patterns are especially adherent to the EXIT GATE.

## 27.1 "Real numbers, or no numbers"

KOF's official site declares:

> **"Números reais, ou nenhum número."** ("Real numbers, or no numbers.")

And it also publicly assumes that the project shows what exists, what is being
built and where it is going, without turning the roadmap into a promise.

**Translation to the EXIT GATE:**

```text
checkbox without measurement = checkbox not done
```

No target becomes GREEN by documentation, expectation or memory.

## 27.2 Real software as validation

In the post about the Kof Editor, Mel explicitly describes building real
software in KOF as one of the best ways to validate the language.

This already has a concrete counterpart in Git:

- `KofLang/Kof-Editor`;
- its own CI;
- a history of constraints found by using KOF for real;
- bugs discovered by the editor and turned into repros;
- a historical `669/669` suite record;
- repros on three targets;
- byte-for-byte parity restored in one of the documented cycles.

**Proposed consequence for 1.0:**

the gate must not stop at `hello.kf`.

The 1.0 candidate must prove at least one reference real application using the
**candidate's packaged artifact**, outside the `Kof4j` tree.

Mel must decide which application/corpus will be official for this gate.

Natural candidate for discussion:

```text
Kof Editor
```

but this document does NOT make it mandatory without approval.

KOF-specific sources:

- https://koflang.github.io/
- https://pt.linkedin.com/posts/aminadojava_github-koflangkof-editor-activity-7497346539044970497-R3oP
- https://github.com/KofLang/Kof-Editor

---

# 28. Academic research and real cases — weight 5

## 28.1 Ericsson — short feature freeze supported by automation

**Study:** Eero Laukkanen, Maria Paasivaara, Juha Itkonen, Casper Lassenius and
Teemu Arvonen, *Towards Continuous Delivery by Reducing the Feature Freeze
Period: A Case Study*, ICSE-SEIP 2017.

A real case in an Ericsson R&D organization.

Reported results:

- **56%** reduction in the feature freeze period;
- afterwards, **63% fewer changes during the freeze**;
- **59% fewer changes close to the release date**;
- test automation was a central enabler.

Source:
https://research.aalto.fi/en/publications/towards-continuous-delivery-by-reducing-the-feature-freeze-period/

### What this inspires in KOF

Do not freeze the Beta early.

Use a **short, deliberate and highly automated RC**, when the surface is
already chosen.

This reinforces the correction already made in the document:

```text
beta-0.5.0 ≠ freeze
first approved RC = start of the surface freeze
```

### What NOT to copy

The paper does not define how many days KOF's RC should last.

Any fixed duration would be invented.

---

## 28.2 Linux + Chrome — stabilization is a phase of its own

**Study:** Md Tajmilur Rahman and Peter C. Rigby,
*Release Stabilization on Linux and Chrome*, IEEE Software 2015.

The empirical study found a real stabilization phase even in fast cycles and
showed that a small group controls a good part of that work.

In the Linux case analyzed, stabilization proceeds through RCs until important
regressions are no longer pending.

Sources:
- https://users.encs.concordia.ca/~pcr/paper/Rahman2015IEEES-preprint.pdf
- DOI 10.1109/MS.2015.31

### Inspiration for KOF

Add to the RC process:

```text
RELEASE OWNER / VERIFIER MATRIX
```

Each 1.0 target/gate must have:

- an owner of the evidence;
- SHA;
- command/job;
- result;
- possible independent verifier;
- explicit pending item.

This fits the already existing pattern of lanes, ownership and risk-based
verifier in KOF.

---

## 28.3 Builds with failing tests and post-release crashes

> **⚠ NOT VERIFIED (v3.1 correction).** v3 cited crash-report medians
> (447 / 247 / 2) attributed to the thesis below. In the 09/20/2026 revalidation
> the PDF was downloaded, but the text could not be extracted and an independent
> search did not find those values. **The numbers were removed from this
> document** until someone confirms the exact page. What the search did confirm is
> only the existence of related work by Rahman and Rigby (2018) on the impact of
> failing/flaky/high-failure tests on the number of crash reports of Firefox builds.

Rahman's line of research on release engineering associates builds with failing
tests with more post-release crashes. **The direction of the association is the
only point used here, and it still depends on that confirmation; no numeric value
from it supports any requirement of the EXIT GATE.**

Source (numbers not re-verified):
https://spectrum.library.concordia.ca/id/eprint/983513/1/Rahman_PhD_S2018.pdf

### Inspiration for KOF

The EXIT GATE must distinguish:

```text
explained RED
flake RED
infrastructure RED
regression RED
```

but **must not normalize an ignored RED**.

This reinforces:

```text
[ ] no mandatory failure ignored
[ ] no flake used as an excuse without evidence
[ ] exception/reclassification recorded
```

---

## 28.4 8.8 billion executions — hidden flakiness remains a risk

**2026 study, IEEE TSE:** Leinen, Gruber, Erdogan, Stahlbauer and Pretschner,
*An Empirical Study of Detected and Undetected Flaky Test Failures in Real-World
CI Pipelines*.

**8.8 billion test executions** were analyzed in four industrial-scale projects.

Among the results:

- undetected flaky failures accounted for **9.8%–16.3%** of failed pipelines;
- the environment had a relevant impact, with flakiness varying by roughly
  **3x** between environments.

Source:
https://portal.fis.tum.de/en/publications/an-empirical-study-of-detected-and-undetected-flaky-test-failures/

### Inspiration for KOF

A green rerun must not automatically turn a release failure into "infra".

For the 1.0 candidate:

```text
failure
  ↓
reproduce in a clean environment
  ↓
classify
  ├── regression → fix
  ├── known flake → issue + owner + evidence
  └── infra → environment evidence
```

If the failure cannot be classified, the gate stays inconclusive/RED.

---

## 28.5 Release readiness is not a single metric

The study *Monitoring and Controlling Release Readiness by Learning Across
Projects* analyzed readiness attributes in dozens of projects.

The recurring bottlenecks included:

- CI rate;
- feature completion rate;
- bug fix rate.

Source:
https://doi.org/10.1007/978-3-319-31545-4_14

### Inspiration for KOF

The `EXIT GATE` must remain an **evidence matrix**, not become a single numeric score.

Do not create:

```text
"KOF readiness = 87%"
```

as a release criterion.

Better:

```text
each condition is observable
each condition has proof
any mandatory blocker remains a blocker
```

---

# 29. Mature repositories — weight 3

## 29.1 LLVM — RC against baseline, regressions and real packages

LLVM's real release process has particularly relevant elements:

- official testers per target/environment;
- RCs built and tested as artifacts;
- comparison of the RC with the previous release/RC;
- regressions are identified and recorded;
- regressions must be closed before the next stages;
- artifacts receive verification/attestation in the current pipeline;
- release blockers live in an explicit milestone/process.

Sources:
- https://github.com/llvm/llvm-project/blob/main/llvm/docs/ReleaseProcess.md
- https://github.com/llvm/llvm-project/blob/main/llvm/docs/HowToReleaseLLVM.rst
- https://github.com/llvm/llvm-project/blob/main/llvm/RELEASE_TESTERS.TXT

### KOF inspiration

Add an **explicit baseline**:

```text
candidate
   vs
last accepted baseline
```

It is not enough that:

```text
the candidate passed
```

It must also answer:

```text
did the candidate introduce a regression?
```

---

## 29.2 Rust — Crater as real-world compatibility proof

Rust runs Crater to compare Beta against Stable on a wide corpus of crates,
including in `build-and-test` mode.

Source:
https://forge.rust-lang.org/release/crater.html

### KOF inspiration

KOF does not yet have Rust's ecosystem size, so copying Crater would be overkill.

But the principle is strong:

> compatibility must be measured against real consumers, not inferred only from
> the compiler's suite.

Proposal for Mel:

```text
KOF 1.0 COMPATIBILITY CORPUS
```

Possible components:

- official examples;
- executable normative snippets;
- training/learn programs that are complete;
- official KOF projects;
- Kof Editor;
- other real consumers approved by the maintainer.

The corpus must use the packaged candidate, not internal repository classes.

---

## 29.3 Kubernetes — the release stops when the signal is red

The Kubernetes release handbook determines that errors in the test snapshot stop
the process until they are:

```text
FIXED
or
explicitly marked NON-RELEASE-BLOCKING
```

It also requires that later RCs be measured on the release branch itself.

Source:
https://github.com/kubernetes/sig-release/blob/master/release-engineering/handbooks/release-cuts.md

### KOF inspiration

Add a waiver policy:

```text
a release blocker only stops blocking with:
- reason;
- evidence;
- owner;
- explicit approval from Mel/the release authority;
- destination of the pending item.
```

There is no implicit waiver.

---

# 30. Official documentation of other languages — weight 2

## 30.1 Python — the freeze has a clear phase

PEP 602 clearly separates development, Beta and RC.

In Python's policy the freeze begins already at the first Beta; this **must not
be copied automatically by KOF**, since KOF's current direction keeps the Beta
open.

The reusable principle is another one:

> each phase needs explicit change rules.

Source:
https://peps.python.org/pep-0602/

### Correct translation to KOF

```text
KOF Beta   = development allowed under the current contract
KOF RC     = public surface freeze + stabilization
KOF Stable = approved contract + compatibility
```

---

## 30.2 Rust — stability requires experience before Stable

The rustc-dev-guide documents that new features do not simply enter Stable: they
go through channels and experience before stabilization, because of the strong
compatibility guarantees.

Source:
https://github.com/rust-lang/rust/blob/master/src/doc/rustc-dev-guide/src/implementing-new-features.md

### KOF inspiration

At the first RC, generate a **Stable Surface snapshot**:

```text
decided grammar/semantics
public stdlib
public CLI
declared targets
allowed gap codes
contractual diagnostics where applicable
```

After the snapshot:

```text
surface diff != empty
    ↓
does not pass silently
    ↓
Mel decides change + restart of validation
or
deferral to post-1.0
```

This turns "no new feature" into a verifiable rule.

---

# 31. Forums — weight 1

Python discussions about real RCs reiterate that, in this phase, only reviewed
changes that are clear fixes enter, with the goal of minimizing changes before
the final.

Example:
https://discuss.python.org/t/python-3-15-0-release-candidate-1-is-here/108395

Rust discussions also treat Beta as an opportunity to detect regressions before
Stable:
https://internals.rust-lang.org/t/the-case-for-a-new-relese-channel-testing/14412

### Use in the benchmark

These sources only corroborate patterns already found in stronger sources.

They **add no requirement to KOF**.

---

# 32. Proposed improvements to the KOF 1.0 EXIT GATE after the benchmark

The lines below are **candidates**, not contract.

Each one needs Mel's approval before entering the normative block.

**RATIFIED 09/20/2026 (`D-1.0-EDGES`):** all nine reinforcement candidates
become **mandatory gates** (the markers below read `[RATIFIED]`). The §32.x
proposals are now normative gates of the exit contract, not candidates.

## 32.1 Real-application gate

Add:

```text
[RATIFIED] reference real application validated with the candidate package
```

Proposed form:

```text
release artifact
      ↓
environment outside Kof4j
      ↓
real KOF application
      ↓
build/check/run
      ↓
functional proof
```

**KOF adherence:** VERY HIGH.

It is directly consistent with how Mel herself describes validating the
Kof Editor.

---

## 32.2 Baseline/regression gate

Add:

```text
[RATIFIED] candidate compared with the last accepted baseline, with no new regression
```

This complements:

```text
RC → Stable without regression
```

because it defines "no regression" as a comparison and not a feeling.

Possible baseline:

```text
last accepted RC
or
the last Beta declared a reference by Mel
```

---

## 32.3 Explicit flaky-test policy

Add to the CI/release evidence:

```text
[RATIFIED] 0 mandatory failures without classification
[RATIFIED] known flakes have issue + owner + evidence
[RATIFIED] an isolated rerun does not automatically erase the first failure
```

For 1.0, an unresolved flake may be:

- a release-blocker;
- explicitly non-blocking;
- or an infrastructure gap;

but never "disappear" without classification.

---

## 32.4 Stable Surface snapshot at RC1

Add:

```text
[RATIFIED] Stable Surface snapshot created at RC1
[RATIFIED] no surface drift without a new decision
```

This snapshot makes the feature freeze measurable.

---

## 32.5 Compatibility corpus

Add:

```text
[RATIFIED] corpus of real consumers compiles/runs with the candidate
```

It does not need Rust/Crater scale.

It starts small and honest.

---

## 32.6 Artifact identity gate

Strengthen:

```text
[ ] real package tested outside the repo
```

to:

```text
[RATIFIED] the tested package has the SAME digest as the artifact that will be published
```

MANDATORY record (ratified 20/09 by `D-ARTIFACT-TRUST` — no longer "ideally"):

```text
SHA256
builder/run
commit SHA
target
timestamp
```

The ratified contract goes further than the record: **integrity +
exact-artifact + neutral build provenance attested by the official workflow**;
the release gate **BLOCKS** without valid evidence, and `kof deps resolve`
**HARD-BLOCKS** official packages without it (community packages = honest
warning, never silent). Enforcement queue: (a) rc-gate digest check +
standalone jars in `SHA256SUMS`, (b) attest+verify in the release workflow,
(c) evidence check on the resolve side — `DECISIONS.md` §D-ARTIFACT-TRUST.

KOF's own roadmap/decisions already use `SHA256SUMS` in the registry direction,
so this principle has additional internal adherence.

---

## 32.7 Release evidence per target

Proposal:

```text
target         SHA        proof/job        result     verifier
JVM            ...        ...              GREEN      ...
x86-64         ...        ...              GREEN      ...
riscv64        ...        ...              GREEN      ...
aarch64        ...        ...              GREEN      ...
JS             ...        ...              GREEN      ...
Script         ...        ...              GREEN      ...
```

For HIGH-risk gates, leverage KOF's already existing direction of an independent
verifier.

---

## 32.8 Formal release waiver

If something red is considered non-blocking:

```text
waiver id
finding/test
cause
why it does not block
residual risk
owner
destination release
Mel's approval
```

Without that:

```text
RED remains RED
```

---

# 33. What I do NOT recommend importing

Even with strong sources, some mechanisms from other projects are not good
candidates for direct copying.

## 33.1 Do not copy Python's calendar

There is no internal evidence that KOF needs Beta/RC of a fixed duration.

## 33.2 Do not copy the whole Crater

KOF still has a different ecosystem size.

Copying the infrastructure would be cost without proportional benefit.

Reuse only the principle of the **consumer corpus**.

## 33.3 Do not create Kubernetes' bureaucracy

Kubernetes needs a large Release Team because the project is enormous.

KOF can have the same rigor with:

```text
small manifest
clear owner
automatic evidence
Mel's decision
```

## 33.4 Do not turn a score into a release decision

The weights of this research order **external sources**.

They do NOT produce:

```text
score >= 80 → ship 1.0
```

The decision remains binary by contract:

```text
mandatory gates satisfied?
yes/no
```

---

# 34. Ranking of the improvements by the weighted benchmark

The points below are **external evidence points**, not product scoring.

| Proposal | Academic/case evidence (5) | Mature repo (3) | Docs (2) | Forum (1) | Reading |
|---|---:|---:|---:|---:|---|
| do not normalize RED/flake; classify failures | 15 | 3 | 0 | 0 | **very strong** |
| short freeze, only in the stabilization phase | 10 | 6 | 2 | 1 | **very strong** |
| compare candidate vs previous baseline | 5 | 6 | 0 | 0 | **strong** |
| test real application/consumer | 5 + direct KOF evidence | 3 | 0 | 0 | **very strong for KOF** |
| release owner/verifier per target | 5 | 6 | 0 | 0 | **strong** |
| compatibility corpus | 0 | 3 | 2 | 1 | **moderate externally, high practical adherence** |
| Stable Surface snapshot | 0 | 3 | 4 | 1 | **moderate/strong** |
| artifact digest/provenance | 0 | 6 | 2 | 0 | **strong + internal adherence to SHA256SUMS** |
| formal waiver for non-blocking | 0 | 6 | 0 | 0 | **strong operationally** |

> The numbers serve only to order the external research.
> **No row beats an existing KOF decision.**

---

# 35. EXIT GATE v3 — candidate block for Mel's review

The original checklist remains intact:

```text
[ ] main CI green
[ ] Quality/Security gates green and trustworthy
[ ] 0 OPEN bugs classified as release-blocker
[ ] real package tested outside the repo
[ ] JVM green
[ ] x86-64 green
[ ] riscv64 green
[ ] aarch64 green
[ ] JS green
[ ] Script green
[ ] KofC green (own gate — `D-1.0-EDGES`)
[ ] Android green (own gate — `D-1.0-EDGES`)
[ ] golden byte parity where the contract requires it
[ ] all remaining gaps explicitly outside the 1.0 surface
[ ] VERSION / docs / release metadata synchronized
[ ] RC with no new feature
[ ] only fixes during the RC
[ ] RC → Stable without regression
```

### Reinforcement candidates — RATIFIED (all nine, `D-1.0-EDGES` 20/09/2026)

```text
[x] Stable Surface snapshot frozen at RC1
[x] 0 mandatory failures without classification
[x] flaky-test policy applied to the candidate
[x] candidate compared with the last accepted baseline
[x] compatibility corpus executed
[x] real KOF application validated with the candidate package
[x] digest of the tested package == digest of the published package
[x] evidence manifest per target
[x] release waiver only explicit, documented and approved
```

**Decided:** all nine become mandatory gates — not only the four recommended.
The recommendation below is kept as historical context and is **overridden**.

My recommendation for the discussion with Mel is **not to turn all of them into
new gates at once**.

The four with the best ratio of evidence, adherence and cost are:

```text
1. real application with the candidate package
2. comparison against baseline / no new regression
3. failure/flake policy without false-green
4. Stable Surface snapshot at RC1
```

Afterwards:

```text
5. artifact identity (SHA256/provenance)
6. evidence manifest per target
7. growing compatibility corpus
8. formal waiver
```

---

# 36. Research synthesis

The external research does not change KOF's direction.

It reinforces something that already appears both in the repository and in
Mel's public communication:

```text
do not prove a feature by a list
prove it by real use

do not call an expectation a metric
measure

do not hide a gap
make it explicit

do not confuse "it compiled" with "it works"
execute

do not confuse a green CI with a trustworthy gate
validate the gate itself

do not call it RC while the surface keeps changing
cut the RC when real stabilization begins
```

The goal of the EXIT GATE is not to make KOF look like Rust, Python, LLVM,
Kubernetes, Chrome or any other project.

The goal is to take advantage of what those projects and studies have learned in
order to ask KOF itself a better question:

> **"What evidence do we need to produce to state, within Mel's contract,
> that this exact artifact is really KOF 1.0?"**

The final answer remains with KOF and the maintainer.

---

# 37. Verification record — revalidation of 09/20/2026 (v3.1)

Read-only: no repository file, issue, label or decision was changed.
Measured tips: `beta-0.5.0 = 9ee038f7`, `beta-0.4.0 = 4ee3a5c9` (both change with every landing — revalidate).

## 37.1 Internal claims

| v3 claim | Result |
|---|---|
| `D-BRANCH-0.5.0` exists in `DECISIONS.md`, with Mel's order quoted | ✅ confirmed (identical text) |
| `AGENTS.md` on `beta-0.5.0` declares the active branch | ✅ confirmed |
| `VERSION` and `pom.xml` = `0.4.7-beta` on `beta-0.5.0` | ✅ confirmed |
| `release-beta-0.5.0-prep.md`, `release-naming.md`, `VERSIONING.md` exist | ✅ confirmed |
| no ratified `D-RELEASE-1.0` exists | ✅ confirmed (no occurrence) |
| #550, #553, #554 CLOSED; #555 OPEN | ✅ confirmed (#555 is the only open issue) |
| `Code Quality Analysis` green / `CodeQL Gate (alerts API)` red / security-bot green | ✅ confirmed (last completed `kof-quality-bot`, SHA `7b2dd963`) |
| `beta-0.4.0 = 4ee3a5c9` | ✅ confirmed |
| tip `47a7b8f9` of `beta-0.5.0` | ⚠ exists and is an ancestor of the current one (`9ee038f7`) |
| "no residual landing only in `beta-0.4.0`" (section 6 condition) | ❌ **not satisfied today**: 3 commits (`4ee3a5c9`, `ce5e8e66`, `d3f79e7a`, all by Mel) |
| #555 = 21 alerts | ⚠ the code-scanning API returns **38** open on `refs/heads/beta-0.5.0` |
| release/versioning docs cite `kofc` | ❌ **not confirmed** (0 mentions in `docs/distribution/`; present in the README and in `docs/architecture/`) |
| Android: "Android is JVM" in recent decisions | ✅ confirmed (`DECISIONS.md`, DB faces; and `INSTALL.md` mentions Android) |
| Kof Editor: own CI | ✅ exists (`CI (build + check)`); last 2 runs `action_required`, 1 `success` |
| Kof Editor: `669/669` and byte-for-byte parity restored | ✅ confirmed in `docs/08-restricoes-kof.md` (`mvn clean test` 669/669 = 456 compiler + 8 script + 5 C-compiler; **a local-build result**) |
| `SHA256SUMS` in the registry direction | ✅ confirmed (`DECISIONS.md`, D2 — registry MVP) |
| site: "Números reais, ou nenhum número." | ✅ confirmed ("Métricas" section) |

## 37.2 New findings (not in v3)

- The public site (`koflang.github.io`) is at **v0.4.1-beta**, marks **KofC "Disponível"** and **KofJS "Em desenvolvimento"**. It diverges from the target checklist and from `VERSION`.
- `docs/distribution/release-naming.md` and `INSTALL.md` still say "Current version: 0.4.0-beta".

## 37.3 External sources

| Source | Result |
|---|---|
| Ericsson (Laukkanen et al., ICSE-SEIP 2017): freeze −56%, changes during freeze −63%, near release −59%, via test automation | ✅ confirmed in the abstract |
| Leinen et al., IEEE TSE 2026: 8.8 billion executions, 4 projects, 9.8%–16.3%, up to 3× per environment | ✅ confirmed (TUM portal; publication marked "in press") |
| Kubernetes release-cuts: stop the process if the test snapshot has an error until fixed or marked non-blocking; later RCs measured on the release branch | ✅ confirmed |
| Rust Crater: beta × stable on a corpus of crates, `build-and-test` mode | ✅ confirmed |
| Rahman (thesis, Concordia): medians 447 / 247 / 2 crash reports | ❌ **not verified — numbers removed** (section 28.3) |
| Rahman & Rigby, *Release Stabilization on Linux and Chrome* (IEEE Software 2015) | ⚪ not re-verified |
| LLVM (`ReleaseProcess.md`, `HowToReleaseLLVM.rst`, `RELEASE_TESTERS.TXT`) | ⚪ not re-verified |
| Python PEP 602; Rust `implementing-new-features.md`; forum discussions (weight 1) | ⚪ not re-verified |
| Mel's LinkedIn post about the Kof Editor (section 27.2) | ⚪ not re-verified (the equivalent content was confirmed in the Kof Editor repository) |

The weights (section 26) and the ranking (section 34) **were not changed**: the corrections above do not change any recommendation, only what is verified fact and what is not yet.

**RATIFIED 09/20/2026** — the approval block (§22) was filled in as the
mechanical record of the maintainer's chat approval (her words quoted there);
the normative decision is `D-RELEASE-1.0` in `docs/development/DECISIONS.md`.
The `[? MEL]` reinforcement candidates of §35 are **closed** by `D-1.0-EDGES`
(09/20/2026): all nine became mandatory gates — the §32 markers read
`[RATIFIED]`; they are no longer open edges.
