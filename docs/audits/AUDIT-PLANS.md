[English](AUDIT-PLANS.md) | [Português](AUDIT-PLANS.md)

# AUDIT-PLANS — the plans inventory and reconciliation (living; updated per cycle)

The rule: a plan is CONCLUDED only with every mandatory requirement
IMPLEMENTED_AND_VERIFIED (executed proof). The classification per the
mandate's vocabulary. The source of truth: `docs/` (promoted = concluded),
`docs/development/` (in progress), the git history.

## `docs/` — the PROMOTED plans (concluded; the spot audits re-verify)

| Plan | Classification | The spot audit |
|---|---|---|
| testing-platform (phases 1–8) | IMPLEMENTED_AND_VERIFIED | the 60/0F family in the suite; the phase-8 row fully landed |
| kof-expr (spike + slices 1–9) | IMPLEMENTED_AND_VERIFIED | KofExprSearchE2ETest 9/9; the per-executor budgets |
| kofscript-embedding | IMPLEMENTED_AND_VERIFIED | KofEmbedSessionTest 8/8; shipped in 0.7.3 |
| kof-esp8266 | IMPLEMENTED_AND_VERIFIED (gates 0–5; gate 5 ON THE REAL BOARD) | FfiNativeEsp8266E2ETest 6/6 + the console bytes measured |
| universal-platform | IMPLEMENTED_AND_VERIFIED (the faces; the follow-ups tracked in #1160) | the Stage E2E families |
| linalg (the BLAS levels 1–3) | IMPLEMENTED_AND_VERIFIED | Level1/2/3E2ETest 6/0F (dscal/axpy/gemm via the copy-out opt-in) |
| the others (secrets, kof-file, pagination, http-policies, connectors, kofbol, financial, cobol-translator, entity-history, shell, workflow, value-records, type-system-extensions, assembly-optimization, scoped-resources, image-vision, graphics-gaming, qrcode-wasm, wasm-wasi, test-architecture, multi-paradigma) | IMPLEMENTED_AND_VERIFIED per their own promotion records | spot audits: ON DEMAND (the P2/P3 cycles re-verify the highest-traffic ones first) |

## `docs/development/` — the open plans

| Plan | Classification |
|---|---|
| quality-pipeline (the maintainer's, the 14.4 rulesets) | BLOCKED on the maintainer (owned) |
| (nothing else — the promotion queue is EMPTY; future/ holds only PLAN-BOOTSTRAP, gated post-1.0 by D-BOOTSTRAP) | — |

## The gap records (the issues) — the requirement-level reconciliation

| Issue | The faces | Classification per face |
|---|---|---|
| #1156 | callback-String | IMPLEMENTED_AND_VERIFIED (FfiNativeCallbackStrE2ETest) |
| #1156 | HFA-cross | IMPLEMENTED_AND_VERIFIED (FfiCrossHfaE2ETest 10) |
| #1156 | variadics | SUPERSEDED_BY_DECISION (D-R3-3.5: none) |
| #1156 | array copy-out | IMPLEMENTED_AND_VERIFIED on JVM+native x86-64 (the borrowed opt-in); PARTIALLY_IMPLEMENTED on cross (FFI001 honest, the named follow-up) |
| #1156 | pcap capture pointer-heavy | NOT_IMPLEMENTED (FFI001 honest; the pcap version face binds) |
| #1160 | BLAS copy-out | IMPLEMENTED_AND_VERIFIED (linalg consumes) |
| #1160 | pcap capture / SVG-engine / BAM / key-store / soname | NOT_IMPLEMENTED (each named) |
| #1161 | the conformance measurement layer | IMPLEMENTED_AND_VERIFIED (check_conformance_1_0.sh, 105/105 locked, wired into the cut verdict) |
| #1161 | the item-by-item ledger resolution (D-1.0-STABILITY-100) | NOT_IMPLEMENTED (the 1.0-scale ladder itself) |
| #1155 | T-3 typed catch / sin-cos / GC-4 / the 15.4 flip | NOT_IMPLEMENTED (the named WASI faces) |
| #1157 | the debugger beyond the JVM MVP | PARTIALLY_IMPLEMENTED (the DAP-on-JVM MVP + the JS source maps + the DWARF lines partially; the vars/expr/breakpoints native + the VS Code ext NOT) |
| #1158 | ws/sse/TLS/path params on JS | NOT_IMPLEMENTED (the named residuals; the DB/ORM-on-JS claim corrected on the site) |
| #1159 | kof.web on Native | PARTIALLY_IMPLEMENTED (the WEB002 residuals named; the whole cross server = NAT007) |
| #1162 | the registry beyond the MVP | NOT_IMPLEMENTED |
| #1163 | the full web platform / OTel / self-hosting | NOT_IMPLEMENTED (the planned scope) |
| #1134 | aarch64 SIGBUS | BLOCKED on arm64 hardware (the defect measured statically: 31 misaligned SP sites; the fix = the push/pop redesign, unprovable here) — the mandate's re-evaluation of the support-contract coherence: PENDING (the P0-cycle question) |
| #1135 | native on macOS | NOT_IMPLEMENTED (the plan-and-gates scope) |
| #1108 | the elvis operator | SUPERSEDED_BY_DECISION (post-1.0; PARSE041) |
| #856 | `kof test` module root | NOT_IMPLEMENTED |

## The contradictory docs — found and fixed so far

- The promoted ESP8266 doc's Kofmd `location` + the future/README PT link +
  the D-ESP8266-DEFERRED staleness (fixed 15/11, `b734f74c9`).
- The promoted doc's plan-only body (fixed 15/11, `2c86a7914`).
- The SITE's FFI line (fixed via `koflang.github.io#1`, 15/11).
- The remaining known-stale: the historical CHANGELOG rows keep the old
  names by design (history is not rewritten); status.md's older `last:`
  rows are history, the topmost row is current.
