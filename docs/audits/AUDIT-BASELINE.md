[English](AUDIT-BASELINE.md) | [Português](AUDIT-BASELINE.md)

# AUDIT-BASELINE — the stabilization operation's starting state (15/11/2026)

**Mandate:** the stabilization/reconciliation/gap-closing operation (the
maintainer's P0→P3 priority; evidence-first; no weakened assertions; no
reclassification-for-gates; no force push; no main merges; no releases
without authorization).

## Branch / SHA / divergence

- **Active branch:** `lab` @ `20f601010` (clean tree).
- **`main`** @ `fd02d3523` (the 0.7.3 bump) — `lab` is **50 commits ahead**;
  the promotion `lab → main` is maintainer-gated (never by an agent).
- **Open PRs:** `koflang.github.io#1` (the site-sync FFI/0.8.0/conformance
  correction — the other repo, awaiting the maintainer's review).
- **Releases:** 0.7.3 published on the 4 platforms (Latest).

## The suite baseline AT THIS SHA (executed, not inherited)

- `scripts/safe-suite.sh -o test -Dmaven.test.failure.ignore=true
  '-Dsurefire.argLine=-Djava.io.tmpdir=/var/tmp/kof-surefire'`
- Log: `/var/tmp/kof-suite-080f-refresh2.log` (disk-backed; the extract:
  `docs/audits/suite-verdict-0.8.0-e38619713.md` +PT).
- **SUITE-SHA `e38619713` · SUITE-DIRTY 0 · TOTAL: tests=8216 failures=0
  errors=0 skipped=1015 · BUILD SUCCESS.**
- The verdict: `stability-report.sh` → **STABILITY: GREEN**;
  `check_lab_stability.sh` → **CUT: GREEN** (rc-gate rc=0, 15 structural
  gates incl. `check_conformance_1_0`).
- NOTE (honest): the working tip is `20f601010` — 2 docs-only commits past
  the stamp (the fixed point of any verdict record; the code byte-identical,
  measured). The mandate's rule "no prior-commit results as proof of the
  current commit" is honored by naming this delta explicitly: the delta is
  docs-only; the next full-suite stamp belongs to the first fix cycle.

## The 1015 skips — the accounting

The named-skip families (each names its reason in the test): the webkit
browser faces (the environment cannot launch it — the system libs), the
cross-riscv64/aarch64 faces missing the cross-gcc on this host (the CI
`cross-native` job runs them for real), the ESP8266 faces without the
toolchain provisioned in a given environment (named when absent; provisioned
HERE), the pcap/openblas/cypress binaries present here (the faces RUN), the
JVM-only FFM faces, the Android faces needing the SDK. The per-skip
inventory belongs to AUDIT-STABILITY (the first P2 cycle).

## The open issues (13) — the P0–P3 map

| Issue | Theme | The state at this baseline |
|---|---|---|
| #1134 | aarch64 SIGBUS on real hw | measured statically (31 misaligned SP sites in the kept asm); the fix needs arm64 hw; classified `1.0-outside` by the maintainer's route (b) — the mandate asks a re-evaluation of the support-contract coherence |
| #1155 | WASM/WASI residuals | the named faces (T-3 typed catch, sin-cos, GC-4, the 15.4 flip) |
| #1156 | FFI residuals | 2 of 4 faces LANDED this week (callback-String, HFA-cross) + copy-out LANDED (JVM+native, the borrowed opt-in, consumed by linalg); variadics = the taken decision; the remainders: pcap capture, the cross copy-out |
| #1157 | the debugger beyond the JVM MVP | the named residuals (native vars/expr/breakpoints, the VS Code ext) |
| #1158 | KofJS web residuals + the site claim | ws/sse/TLS/path params; the site DB/ORM-on-JS claim already corrected |
| #1159 | kof.web on Native | WEB002 residuals; NAT007 on the cross |
| #1160 | the libs follow-ups | the copy-out PART landed (linalg consumes); pcap capture, SVG-engine, BAM, key-store, soname remain |
| #1161 | the formal conformance suite | the MEASUREMENT LAYER LANDED (`check_conformance_1_0.sh`, wired into the cut verdict); the item-by-item ledger resolution stays the ladder |
| #1162 | the package registry beyond the MVP | the named scope |
| #1163 | the full web platform / OTel / self-hosting | the planned scope |
| #1135 | native on macOS | the plan-and-gates scope |
| #1108 | the elvis operator | `post-1.0` (the frozen-surface decision) |
| #856 | `kof test` module root | the named scope |

## The infrastructure limits (this host)

- No cross-gcc riscv64/aarch64 (the cross faces skip named; the CI runs
  them); no arm64 hardware (the #1134 fix unprovable here); webkit does not
  launch (the system libs); no node/npm (the site build/lint is the PR's CI);
  no sudo without the maintainer (granted once for the dialout).
- The /tmp tmpfs 7.8G ceiling: the durable artifacts live on /var/tmp and
  docs/audits/ (the run-5 lesson).
