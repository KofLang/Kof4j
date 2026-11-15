[English](AUDIT-STABILITY.md) | [Português](AUDIT-STABILITY.md)

# AUDIT-STABILITY — tests / sanitizers / static analysis / skips / gates (living)

## The current suite verdict (executed at `e38619713`; see AUDIT-BASELINE)

8216 tests / 0 failures / 0 errors / 1015 named skips / BUILD SUCCESS —
STABILITY: GREEN + CUT: GREEN (the extract: suite-verdict-0.8.0-e38619713.md).

## The P0/P1 cycle results (15/11)

- The cross toolchains INSTALLED on this host (the cross-aarch64/riscv64-gcc15
  + the binutils via zypper; the `*-linux-gnu-*` symlinks to the suse names) —
  the previously-skipped cross faces NOW RUN under qemu (the named skip's
  condition satisfied).
- The lr.d/sc.d aarch64 translations LANDED (the Buffer borrow's LR/SC
  atomics had no translation — the `mov Wd, w16` idiom for the sc.d status;
  the widths measured by the assembler). The buffer cross faces: BufferFfi
  6/0F (the memset cross + the JS parity), BufferBorrow 7/8.
- The REMAINING 1F: the CONCURRENT borrow race on riscv64 under qemu TCG —
  the barrier waits for 3 attempt marks and times out at 30s: under qemu's
  cooperative scheduler the two workers' fenced calls may serialize (worker1
  completes+releases before worker2 starts → no MEM020 → no loser mark). The
  race timing needs the REAL hardware or the CI's cross job (the test design
  itself says "exactly one MEM020 always fires" — on real hardware). NOT a
  test weakening: the test stays as-is; the limitation is recorded HERE.
- The JS copy-out 'P' face LANDED (the KofJsFfiMarshal packArrayOut +
  the element-typed copyBack + the countParams/bridge 'P' dispatch + the
  borrows skipped for the arrays — the MEM020 protocol is Buffer-only).

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
