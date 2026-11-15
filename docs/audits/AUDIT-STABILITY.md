[English](AUDIT-STABILITY.md) | [Português](AUDIT-STABILITY.md)

# AUDIT-STABILITY — tests / sanitizers / static analysis / skips / gates (living)

## The current suite verdict (executed at `e38619713`; see AUDIT-BASELINE)

8216 tests / 0 failures / 0 errors / 1015 named skips / BUILD SUCCESS —
STABILITY: GREEN + CUT: GREEN (the extract: suite-verdict-0.8.0-e38619713.md).

## The static analysis / CodeQL

- `scripts/codeql-gate.sh --fast`: the GitHub API was rate-limited at the
  last probe (NAO-AVALIADO per branch; the CI `codeql.yml` remains the real
  gate). The CI has been GREEN through the 0.7.3 release. Re-probe: the P2
  cycle (the API window permitting).

## The sanitizers / Valgrind / fuzzing

NOT in the mandate's sense yet: the native faces run under the harness
assertions (the MEM020 borrow races, the payload-untouched goldens), but no
ASan/Valgrind pass exists for the native binaries. The P2 cycle: evaluate
`-fsanitize=address` on the native test binaries (the toolchain cc supports
it; the runtime is the Kof heap + the arena, so the FFI arena faces are the
valuable target).

## The skips (the families; the per-skip inventory the P2 cycle deepens)

| Family | The reason named | The condition to run | The owner/plan |
|---|---|---|---|
| webkit browser faces | the environment cannot launch webkit (the system libs) | the host with the webkit deps | environmental (documented) |
| cross riscv64/aarch64 faces | no cross-gcc here | the CI `cross-native` job runs them for real | the CI owns |
| ESP8266 faces without the toolchain | the named skip when absent | `scripts/provision-esp8266.sh` (provisioned HERE) | done here |
| the cypress/Cypress binary | the version-pinned binary absent | `cypress install` (restored 15/11) | done here |
| the JVM-only FFM faces | the FFM is JVM-only | — | the contract |

## The gates (structural vs behavioral — the mandate's question)

The structural gates check REAL state (the file existence + the pattern
grims + the counts), never the behavior; the behavior lives in the suite
(the 8216). The conformance gate (`check_conformance_1_0`) crosses the
matrix rows against the locking TESTS — the structural/behavioral bridge.
The known gap: the gates that grep could pass on a rename — the P2 cycle
audits each gate for the behavioral anchor (the test class it cites).
