[English](pull_request_template.md) | [Português](pull_request_template.pt_BR.md)

<!--
  Branch pipeline (authoritative: `AGENTS.md` §Authority/D-BRANCH-PIPELINE and
  `docs/development/DECISIONS.md` §D-QUALITY-PIPELINE-2609):
  lab → testing → prerelease → stable → release/x.y.z → tag.
  Development and fixes ALWAYS enter through `lab`; every other stage is
  protected and the guard action closes any PR aimed at `main`.
-->

## 🎯 Target Base Branch
- [ ] I confirm that this PR targets the active **`lab`** stage and **NOT** a protected stage (`main`/`testing`/`prerelease`/`stable`).

---

## 📝 Description of the Change
<!-- Describe clearly and concisely what was added, fixed or refactored. -->

---

## 🔗 Related Issue
<!-- Every PR must reference an open issue (e.g.: Fixes #123, Closes #456). Rule 6 of AGENTS.md. -->
Fixes #

---

## 📐 KOF-First Contract (`D-KOF-FIRST`)
<!-- Gates 1–5 live in `AGENTS.md` §Kof-first and `DECISIONS.md` §D-KOF-FIRST — answer the fields below; do not restate the rules. -->
**Valid Kof reproducer** (the snippet that exercises the change):

```kof

```

- **Contract source** (DECISIONS.md entry, normative doc, conformance/golden test, or parity matrix) that defines the expected behavior:
- **RED before the production change** — target(s), expected by the Kof contract, actual:
- **Root cause** (not the symptom):
- **Classification** (`Real bug` / `Target divergence` / `Real gap` / `Design request` / `Not-valid` / `Contract ambiguity`):
- **Does this change the Kof surface?** If yes, the maintainer's decision authorizing it (rule 6) — a new accepted grammar form is a language feature, not a parser fix:
- **External references used** (implementation/theory only; none of them defines the Kof surface):

---

## 🧪 How It Was Tested (Quality Gate)
<!-- The test PROVES that the code works. List the commands run and the tests added. -->
- [ ] `mvn -o -pl kof-compiler -am compile -q` ran without errors
- [ ] Tests added/changed covering the happy path and edge cases (Q3)
- [ ] Suite run and green (`mvn test ...`)

---

## 📋 Pre-Submission Checklist
- [ ] No change contains stubs or facade TODOs (Q7)
- [ ] `AGENTS.md` rules respected (≤500 lines/class, zero regression)
- [ ] Documentation or `DOING.md` updated if applicable
