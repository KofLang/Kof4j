[English](AUDIT-DOCUMENTATION.md) | [Português](AUDIT-DOCUMENTATION.md)

# AUDIT-DOCUMENTATION — the doc divergences found/fixed + the site sync (living)

## Found + fixed (this operation + the immediate run-up)

| Divergence | Fixed by |
|---|---|
| The promoted ESP8266 doc: the Kofmd location stale + the body still "plan, zero code" + the H-stage statuses unrecorded | `b734f74c9` + `2c86a7914` (the location/state, the Rule-6 block SATISFIED AND CLOSED, the H-stages realized, §9 ALL SEVEN MET) |
| The future/README PT link to the old path | `b734f74c9` |
| `D-ESP8266-DEFERRED` stale (said NOT promoted) | `b734f74c9` (marked SUPERSEDED — the decision kept as the ownership record) |
| THE SITE (`koflang.github.io`): the emDesenvolvimento FFI line stale (the landed faces listed as in-development; variadics listed as a gap = the taken decision) + missing the linalg level-3 / the 0.8.0 readiness / the conformance gate + the stale planejado conformance line | `koflang.github.io#1` (branch sync/lab-15-11 — awaiting the maintainer's review; NOT published without authorization) |

## The doc-truth rules honored

- The history is never rewritten (the old CHANGELOG/status rows keep the old
  names by design).
- The decisions are superseded, never deleted (D-ESP8266-DEFERRED).
- The Kofmd compression in the same commit (D-KOFMD-ON-EDIT).

## The remaining audit (the P2 cycle)

- The site's routes beyond roadmap (the web.tsx, the standard-library.tsx):
  the spot-check of the availability badges against the 0.7.3 published
  surface (the lab features must not appear as stable-available).
- **The site badge sync EXECUTED (16/11): the three stale MATH-D mentions in
  kof-roadmap.ts** (the stdlib card, the emDesenvolvimento entry, the
  1.0-blocks ledger) **synced via koflang.github.io#2** (the branch
  sync/lab-16-11-mathd, NOT merged — the publish is the maintainer's): the
  published-state descriptions stay honest (0.7.3 still refuses MATH001 —
  the fix is on lab, untagged), and the lab-tracking entries record the
  #1169 closure by implementation. The build validated upstream (node v24 on
  the clone host); the deploy happens on the maintainer's merge.
- **The site sync FOLLOW-UP (16/11, the same PR branch): FOUR more stale
  mentions synced** — the 0.8.0 entry (the #1134 prototype note → the
  LANDED redesign with the checker numbers), the release-ladder entry (the
  4-blocks list → THREE closed by implementation, ONE remains #1135
  decision-gated), the FFI entry (#1167 → FIXED on lab), and the ledger
  entry (three open → ONE, the rc-gate count). The PR accumulates the full
  sync for the maintainer's review-and-merge.

## The doc-truth spot audit EXECUTED (16/11, the P2 cycle — the mandate §2/§11)

- **The README's kof blocks COMPILED** (all four extracted and compiled JVM):
  the UI block, the web block, and the TWO `Path`/`Directory` blocks — the
  latter two were TOP-LEVEL STATEMENTS (`var path = ...` bare) which Kof's
  grammar refuses (top-level = functions/classes only). **FIXED EN+PT**: both
  wrapped in `main() { }` — all four blocks now compile green.
- **The install/build docs VERIFIED**: the README's install wildcard
  (`kof-*-linux-x86_64.tar.gz`) matches the REAL published assets — the four
  per-platform 0.7.3 releases exist (linux-x86_64, macos-arm64, macos-x86_64,
  windows-x86_64), and INSTALL.md's platform table ("where it is on the
  page") is accurate. The workflow `release.yml` matrix = the doc's platform
  list. NO divergence.
- **backend-parity.md + .pt_BR.md: the MATH-D row was STALE** (claimed
  "x86-64 only; the cross answers MATH001; the issue stays open") — FIXED
  EN+PT the same cycle: the row now records the landed cross (the B32 riscv
  asm + the translator), the edge proofs, and the #1169 closure.
- **The language-reference's kof fences**: FRAGMENTS by convention (the prose
  snippets — the concurrency/type-system docs) — not compilable-program
  claims; the audit records the convention, no fix needed.
- The MATH-D freshness: the conformance-matrix rows EN+PT were flipped in the
  landing cycle; no stale math claim remains in README/lang-ref. The
  status.md `last:` ledger is historical (the landed-state entries) — the
  MATH-D entry rides the landing commit's DOING (the convention).
