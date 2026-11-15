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

- The README (the root), the language-reference, the install/build docs, the
  examples: the spot audit against the current surface (the P2 cycle).
- The site's routes beyond roadmap (the web.tsx, the standard-library.tsx):
  the spot-check of the availability badges against the 0.7.3 published
  surface (the lab features must not appear as stable-available).
