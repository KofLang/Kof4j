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

## The skips (the §10 per-skip inventory — MEASURED, not asserted)

**The mechanical gate (16/11): `scripts/check_skip_inventory.sh`** — scans
ALL the test sources of the 5 modules for the skip sites
(`assumeTrue`/`assumeFalse`/`abort`/`@Disabled`), requires a NAMED
justification on every site (the literal, the concatenation, the in-file
`static final String` constant, or the dynamic message-builder call —
listed by family), fails rc=1 on any SILENT skip with the file:line
identification, and emits the family table. Planted-fault selftest
(the 1-arg assumeTrue + the bare @Disabled go RED; the named ones pass).
Structural test: `scripts/tests/skip-inventory-test.sh`; wired into
`check_lab_stability` STRUCTURAL_GATES + the agent test harness.

**The measured verdict at this tip: 722 sites / 722 named / 0 silent /
124 families.** The execution-side count (the skips that FIRE on a given
host) is the `TOTAL: ... skipped=N` line of the stamped suite log — a
different number per host by design (a site is named once; it fires only
where its condition holds).

The six §10 fields per family (the mandate's exact questions):

| Family (the sites) | Identification | Justification | Environment required | Target affected | Execution condition | Owner/plan |
|---|---|---|---|---|---|---|
| native toolchain absent (141) | `has("as","ld")` guards, the x86 test bases | the native backend needs the Linux assembler/linker | the binutils (as/ld) | NATIVE x86-64 | the tools absent on the host | the CI's native job runs them for real; the JVM face proves the semantics elsewhere |
| cross riscv64 + qemu absent (~160 across the message variants) | `hasToolchain("riscv64")` guards (NATIVE002) | the cross faces need the riscv64 assembler/ld + qemu | cross-riscv64-gcc + qemu-riscv64 | NATIVE_RISCV64 | absent on the host | SATISFIED HERE 15/11 (the toolchains installed — the faces now RUN under qemu); the sites stay for hosts without; the CI owns the canonical run |
| cross aarch64 + qemu absent (~130) | `hasToolchain("aarch64")` guards | the same, aarch64 side | cross-aarch64-gcc + qemu-aarch64 | NATIVE_AARCH64 | the same | the same (running HERE since 15/11; #1134's real-hardware caveat stays documented) |
| wasm hosts absent (37) | wasmtime guard | the WASI emission faces need the runtime | `scripts/provision-wasmtime.sh` | WASM/WASI | wasmtime absent | the provision script (named in the message) |
| MySQL/MariaDB not reachable (23) | the DB probe | the db faces need a live server on 127.0.0.1 | MySQL/MariaDB service | JVM (db) | the DB down | environmental; the faces keep the honest refusal diagnostics |
| known-bugs §602 GC guards (10) | `BLOCKED_BY_602` + `CROSS_BLOCKED_REASON` | the conservative-GC reclaim of live caller arrays (the tile/plane buffers) | the §602 fix | NATIVE (the av1 faces) | the GC defect open | the native/GC lane owns §602; the sites unblock with it |
| node absent (4) | the node probe | the JS tooling faces need node | node | JS tooling | absent (this host has no node/npm — the documented environmental limit) | the CI |
| the JVM-only FFM faces | the target checks | the FFM downcall is JVM-only | — | JVM | non-JVM target | the contract (never a silent skip) |
| @DisabledOnOs(WINDOWS) | the annotation arg | the OS-conditional guards | — | per-face | the OS matches | the contract |
| the browser system absent (1, `PlaywrightLibProviderTest:128`) | the Playwright deps probe | the browser SPI needs the system libs (`install-deps` requires sudo) | the playwright deps | the webkit/chromium faces | the deps absent (webkit does NOT launch on THIS host — the named environmental limit) | environmental; the CI's browser job |
| [dynamic] skipMessage(face, missing) (1) | `NativeLibraryAvailability.assumePresent` | the missing-soname list built at runtime | the named sonames | per-face | a lib missing | the message NAMES the face + the missing libs |

The gate's teeth (proven): a NEW skip without a justification fails the
stability gate at the next run — silent skips can never accumulate again.

## The gates (structural vs behavioral — the mandate's question)

The structural gates check REAL state (the file existence + the pattern
grims + the counts), never the behavior; the behavior lives in the suite
(the 8216). The conformance gate (`check_conformance_1_0`) crosses the
matrix rows against the locking TESTS — the structural/behavioral bridge.
The known gap: the gates that grep could pass on a rename — the P2 cycle
audits each gate for the behavioral anchor (the test class it cites).
