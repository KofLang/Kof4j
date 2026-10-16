[English](test-architecture-plan.md) | [Português](test-architecture-plan.pt_BR.md)

# 🧪 Refactoring Plan — Kof Test Architecture and Modularization

**Status:** `CONCLUDED` 11/10/2026 — promoted from `future/` 28/09/2026 (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`); moved to `docs/` per the 3-state rule (every finite phase landed)
**Owner:** `192.168.15.30:9092` (lane compiler/JVM — hygiene split + R6 parity-matrix claims; ONE plan, ONE owner per `D-PLAN-ONE-OWNER`)
**Decision:** `D-TEST-ARCHITECTURE-GO` (`DECISIONS.md`) — promotion authorized "profiling → integration".

**Close-out (11/10/2026):** the refactoring is COMPLETE — every phase with a finite scope landed and its ratchet is exhausted. Phase 1 profiling (`scripts/test-suite-profile.sh` + `docs/testing/TEST-PERFORMANCE.md`), Phase 2 discovery + ratchet (`scripts/test-suite-audit.sh` + `scripts/check_test_hygiene.sh`), Phase 3 modularization (oversized 43→18; pure extraction exhausted), Phase 4 harness (identical-pair consolidation 146→117; **zero identical pairs remain**), Phase 5 targets (fifteen slices; `dupname` ratchet 131→117, **EXHAUSTED** — the `main` cluster is a confirmed false lead: Kof `main()` inside test-source text blocks, not a Java helper), Phase 7 integration (`mvn verify -Pintegration`; golden + CLI integration in the `verify` phase). **Phase 6 (Conformance) is not a finite task but the plan's standing outcome** — the official equivalence suite (`tests/run-golden.sh`, four targets: jvm/native/js/script) is a permanent regression net that grows **one case per slice**; at close-out it was **55 cases / 220 checks** (slices 1–30), and further cases are ordinary additive maintenance, not plan work (slice 31, `passwords-namespace`, brought it to **56 / 224**; slice 32, `jwt-namespace`, to **57 / 228**; slice 33, `string-methods-extended`, to **58 / 232**; slice 34, `config-namespace`, to **59 / 236**; slice 35, `json-records`, to **60 / 240**; slice 36, `json-maps`, to **61 / 244**; slice 37, `json-escaping`, to **62 / 248**; slice 38, `list-predicates`, to **63 / 252**; slice 39, `abstract-records`, to **64 / 256**; slice 40, `enum-methods`, to **65 / 260**; slice 41, `string-switch-defaults`, to **66 / 264**; slice 42, `type-check-casts`, to **67 / 268**). Since close-out the suite has continued to grow as ordinary additive maintenance (latest: `interface-static-default`, an interface with `static` + `default` members and an inherited `static` field, → **75 cases / 300 checks**, pinning the §721/§724/§725 cross-target equivalences). No open decision, no queued slice. Prior slices were purely test infrastructure — the compiler was never touched (plan golden rule); slice 35 is the one exception, pairing the golden case with the low-risk single-unit compiler fix §659a it surfaced (see the slice block below).
**Real state (updated 30/09/2026):** the suite is thousands of
`*Test.java` files with no layers/harness; the plan is now under way. **Landed:**
Phase 1 profiling (`scripts/test-suite-profile.sh` + permanent
`docs/testing/TEST-PERFORMANCE.md`), Phase 2 discovery audit
(`scripts/test-suite-audit.sh`) and Phase 2 **ratchet** (`scripts/check_test_hygiene.sh`
over the frozen `scripts/test-hygiene-baseline.txt`, **117 keys, rc=0** — 132 at the
30/09 measurement, tightened by the 02/10 Phase-3 extraction and the 03/10–08/10 Phase-5 slices (`jvmOracle` 131→130, `stopServer` 130→129, `assertRuns` 129→128, `runScript` 128→127, `runKof` 127→126, `assertBoth` 126→125, `copyLibrary` 125→124, `runBoth` 124→123, `runAll3` 123→122, `assumeToolchain` 122→121, `assumeAarch64` 121→120, `assumeCross` 120→119, `runQemu` 119→118, `runQemuE` 118→117); the 0-citation Phase-3 head is exhausted, next candidate has 10 doc
citations, and the remaining `dupname` cluster needs the Phase-5 harness). **Quick-win slice 1
(28/09):** removed the false-positive `Thread.sleep` key (comment-only mention in
`AsyncSleepJsE2ETest`) and the redundant post-`startServer` settle in
`KofWebHardeningTest` (the port-readiness probe already guarantees the bind).
**Quick-win slice 2 (28/09):** the duplicated JVM web readiness probe
(`while (attempt < 40)` + `Thread.sleep(100)`, copy-pasted in `KofWebE2ETest`,
`KofHttpE2ETest`, `KofHttpPoliciesE2ETest`, `KofWebStreamE2ETest`) now lives once
in `TestServerFixture.awaitListening(Process, int)` → baseline 185→182 keys (4
class keys removed, 1 helper key added). **Quick-win slice 3 (28/09):** the same
fixture absorbed the readiness loops of `KofMediaE2ETest`,
`KofOAuthResourceServerTest`, `KofWebHardeningTest`, `KofWebSseE2ETest` and
`KofWebWsE2ETest`; the redundant SSE reconnect settle was dropped; and the
`maxConnections` 503 race test was freed from its fixed sleep in favour of a
bounded poll (it was flaky: 1/3 green) → baseline 182→179 keys. **Honest
correction:** slice 1's "redundant" settle in `KofWebHardeningTest` was part of
that race's timing — the test now waits for the 503 instead of guessing. The visible cost is
feedback latency, not correctness (the reactor suite is green).
**Honest correction 04/10 (`known-bugs` §590):** the slice-3/5 bounded poll did NOT de-flake
`connection_cap_returns_503_when_exceeded` — measured **5/12 RED** isolated at tip `bb43f73a6`
and the only non-environmental red in the full suite. The server increments `activeConnections`
when the HANDLER starts (`JvmRuntimeWebServer:185`), not at `accept()`, so `startServer()`'s
readiness probe could still occupy the single slot when `held` connected: `held` was then the
rejected connection and every later probe saw 200 (the 3s poll can never recover). Fixed at the
root in the test: a bounded retry re-opens `held` until a probe observes the 503 while `held`
is open (fixed **20/20**). Test-only.
**Quick-win slice 4 (28/09):** `TestServerFixture` gained a TCP-only
`awaitPort(port, attempts, interval)` and an explicit-budget
`awaitListening(process, port, attempts, interval)`; the remaining pure readiness
loops in `KofWebNativeE2ETest` (4), `KofWebJsE2ETest` (3) and `KofBlogE2ETest` (1)
now call them instead of hand-rolled probes → baseline 179→176 keys. Fail-fast on
child death and kill-on-timeout stay inside the fixture.
**Quick-win slice 5 (29/09):** `TestServerFixture` gained
`awaitTrue(attempts, interval, condition)` — a bounded poll for counters/response
codes that treats a throwing probe as "not ready yet". `KofWebHardeningTest`
replaced its four fixed settles (`awaitStats` 20 ms, the 503 poll 50 ms, and the
SSE/WS counter decrements 1600/100 ms) with bounded polls; `KofWebWsE2ETest`
replaced its 300 ms "socket stays open" settle with a `setSoTimeout(300)` read
that must time out → baseline 176→174 keys. Both de-flake: the old settles were
guessing the app's `time.sleep(1500)` margin.
**Quick-win slice 6 (29/09):** `TestServerFixture` gained
`await(process, port, attempts, interval, probe)` — a custom readiness probe where
an `IOException` means "not ready yet" and any other exception aborts, so an
`AssertionError` inside the probe still fails the test; `awaitListening` now
delegates to it. `KofWebTlsTest` replaced its two SSL-handshake readiness loops
with `await`; `KofLogE2ETest` replaced its two readiness loops with `awaitListening`
and its two fixed `Thread.sleep` settles (300/400 ms) with bounded `awaitTrue` polls
over a now thread-safe `StringBuffer` stdout — zeroing the last two `sleep` keys of
the web/log E2E. The baseline count stays 174: the 2 removed `sleep` keys are offset
by 2 `dupname` **leads** (`assertManagedTargets`, `runCross`) that entered the frozen
set with the kof-file/multiparadigma lanes in the same window — recorded, not hidden.
**Quick-win slice 7 (29/09):** the CLI E2E readiness/teardown sleeps moved into a new
`CliAwaitFixture` (`awaitTrue`, `awaitExit`, `pause`, `kof-cli` test infra):
`ServePortTest` (2 readiness loops + the §390 orphan wait), `ServeManifestPortE2ETest`
and `FullStackE2ETest` (readiness) and `CliDebugProcessLeakTest` (§438 orphan wait) now
poll a deadline or block on `ProcessHandle.onExit()` instead of a fixed `Thread.sleep`
→ baseline 174→171 keys (4 test keys removed, 1 fixture key added).
`KofDebugJvmExceptionTest` keeps its 500 ms — an intentional "let the loop run before
pause" in the DAP flow, not a readiness settle.
**Quick-win slice 8 (29/09):** `KofTimeE2ETest#durationSchedulerAtFiresJvm` was re-measured and
reclassified — its two `Thread.sleep(150/80)` were NOT load-bearing boot timing but a scheduler
poll: both became bounded `TestServerFixture.awaitTrue` polls (wait for ≥3 fires on a 20 ms
interval; then assert no fire in the 80 ms after `cancel`), and `TickCounter.n` is now `volatile`
(read across the scheduler thread). Baseline 171→170 keys (1 file leaves the sleep set);
`KofTimeE2ETest` 44/44 (0 skip), focused test 4/4 runs.
**Phase 3 extraction slice (02/10):** `DepsRegistryTest` (509 lines, an `oversized` ratchet key)
gave its shared harness to a new `DepsRegistrySupport` (fake GitHub-Releases server, D2-A package
builder, CLI subprocess runner) — the established Phase 3 pattern (`abstract class …Support`, the
test class `extends` it). `DepsRegistryTest` 509→303, `DepsRegistrySupport` 230; the 4 neighbor
classes that `import static dev.kof.cli.DepsRegistryTest.*` keep resolving via inheritance (zero
citation drift). Proof: `DepsRegistryTest` 13/13 + `DepsRegistryTrustTest` 5/5 +
`DepsSourceModuleTest` 8/8 + `CmdDeploySourcesTest` 5/5 = **31/31 green**; ratchet baseline
132→**131** keys (oversized 19→18). (Orphan `DepsRegistryTest$*.class` from the old nested layout
had to be purged from `target/test-classes` before the focused run — maven's incremental compiler
does not delete them.)
**Phase 3 cost found (29/09):** the giant-test split is NOT a cheap increment — test class names
are cited as proof across `docs/` (e.g. `TranslateTest` in `known-bugs`, `audits/`,
`future/TRANSLATOR`), so splitting or renaming a class requires a reference sweep and risks doc
drift. This is now **measured, not guessed**: `scripts/test-suite-audit.sh --citations` counts, per
oversized class, how many files under `docs/` mention its name (`0` = split with no citation
sweep). Measured cheapest-first head: `ArrayBoundsStressTest` (2), `KofSetEqualityTest` (2),
`SemanticResolutionTest` (4), `CmdDeployTest`/`BiosBootE2ETest`/`KofInterpreterParityTest`/
`NullablePrimitiveContractE2ETest` (8) … `ConformanceMatrixTest` (42). Two `docs/bugs-and-gaps`
citations of `KofSetEqualityTest`/`ArrayBoundsStressTest` are class-level counts ("whole
`KofSetEqualityTest` 21/21"), which drift even when the cited method stays in place — so the
cheap Phase 3 rule is: **move only uncited tests out, keep cited methods and class name in the
original file, update the counts**. **First split landed (29/09):** the reusable support of
`KofSetEqualitySupport` (the four Kof sources + the JVM/JS runners) was extracted out of
`KofSetEqualityTest` — all 21 cases and the cited method stayed, so **zero citation drift** — with
oversized 43→42 and baseline 170→169. **Second split landed (29/09):** `KofMathSupport` extracted
the JVM/Native/JS runners + cross-arch qemu golden + toolchain guard out of `KofMathTest` (all 29
cases and the `conformance-matrix`/parity-cited class name stayed) → oversized 42→41, baseline
169→168. **Third split landed (29/09):** `ArrayBoundsStressSupport` extracted the JVM/JS/Native
runners, the Kof program generators and the invariant oracles out of `ArrayBoundsStressTest` (all
15 cases and the cited class name stayed) → oversized 41→40, baseline 168→167. **Fourth split
landed (29/09):** `KofMediaSupport` extracted the pure WAV/MP4 byte builders (`makeWav`/`mp4Box`/
`makeMp4`/`mp4Box64`/`makeMp4WithExtendedSizeBoxBeforeMoov`) out of `KofMediaE2ETest` (all 17 cases
and the cited class name stayed) → oversized 40→39, baseline 167→166. **Fifth split landed
(29/09):** `NullablePrimitiveContractSupport` extracted the JVM/SCRIPT/JS runners + target oracle
out of `NullablePrimitiveContractE2ETest` (all 26 cases and the cited class name stayed) →
oversized 39→38, baseline 166→165. **Sixth split landed (29/09):** `LambdaSupport` extracted the
JVM/Native/SCRIPT/JS runners out of `LambdaE2ETest` (all 36 cases and the cited class name
stayed) → oversized 38→37, baseline 165→164. **Seventh split landed (29/09):** `BiosBootSupport`
extracted the qemu/serial/build helpers out of `BiosBootE2ETest` (all 10 cases and the cited class
name stayed) → oversized 37→36, baseline 164→163. **Eighth split landed (29/09):**
`FfiStructSupport` extracted the C shim source, the host-`.so` compiler, the toolchain lookup
and the JVM/Native/JS runners out of `FfiStructE2ETest` (all 12 cases and the cited class name
stayed) → oversized 36→35, baseline 163→162. **Ninth split landed (29/09):** `ShellSupport`
extracted the JVM/JS capture harness and the parity/refusal oracles (with the inherited `@TempDir`
field) out of `ShellE2ETest` (all 21 cases and the cited class name stayed) → oversized 35→34,
baseline 162→161. **Tenth split landed (29/09):** `KofStringsSupport` extracted the JVM/Native/JS/
qemu runners, the toolchain guard and the two largest inline Kof programs (as `ALL_JVM`/
`ALL_NATIVE` constants) out of `KofStringsTest` (all 18 cases and the cited class name stayed) →
oversized 34→33, baseline 161→160. **Eleventh split landed (29/09):** `KofSwitchExprSupport`
extracted the JVM/Native/JS runners and hoisted all 32 inline Kof programs out of
`KofSwitchExprE2ETest` into named constants (all 32 cases and the cited class name stayed) →
oversized 33→32, baseline 160→159. **Twelfth split landed (29/09):** `KofInterpreterParitySupport`
(harness) + `KofInterpreterParityPrograms` (the 8 largest inline Kof programs, hoisted) out of
`KofInterpreterParityTest` (all 26 cases and the cited class name stayed) → oversized 32→31,
baseline 159→158. **Thirteenth split landed (29/09):** `UiSupport` (runners) + `UiPrograms` (11
largest inline Kof programs, hoisted) out of `UiE2ETest` (all 29 cases and the cited class name
stayed) → oversized 31→30, baseline 158→157. **Fourteenth split landed (29/09):** `JvmSupport`
(runner) + `JvmPrograms` (12 largest inline Kof programs, hoisted) out of `JvmE2ETest` (all 35
cases and the cited class name stayed) → oversized 30→29, baseline 157→156. **Fifteenth split
landed (29/09):** `KofValidationSupport` (runners) + `KofValidationPrograms` (6 largest inline Kof
programs, hoisted) out of `KofValidationTest` (all 34 cases and the cited class name stayed) →
oversized 29→28, baseline 156→155. **Sixteenth split landed (29/09):** `KofJsSupport`
(runners) + `KofJsPrograms` (24 inline Kof programs, hoisted) out of `KofJsE2ETest` (all 40 cases
and the cited class name stayed) → oversized 28→27, baseline 155→154. **Seventeenth split landed (29/09):** `KofWebPrograms`
(19 inline Kof programs + the shared `WEB_APP`, hoisted) out of `KofWebE2ETest` (all 28 cases and
the cited class name stayed) → oversized 27→26, baseline 154→153. **Eighteenth split landed (29/09):** `KofScriptPrograms`
(8 inline Kof programs, hoisted) out of `KofScriptTest` (all 25 cases and the cited class name
stayed) → oversized 26→25, baseline 153→152. **Nineteenth split landed (29/09):** `WorkflowPrograms`
(7 inline Kof programs, hoisted) out of `WorkflowE2ETest` (all 24 cases and the cited class name
stayed) → oversized 25→24, baseline 152→151. **Twentieth split landed (29/09):** `DomainGapPrograms`
(10 inline Kof programs, hoisted) out of `DomainGapCodesTest` (all 29 cases and the cited class
name stayed) → oversized 24→23, baseline 151→150. **Twenty-first split landed (29/09):**
`BackendParityPrograms` (5 inline Kof programs, hoisted) out of `BackendParityTest` (all 19 cases
and the cited class name stayed) → oversized 23→22, baseline 150→149. **Twenty-second split landed (29/09):**
`ComponentCoreSupport` (runners) + `ComponentCorePrograms` (28 hoisted programs) out of
`ComponentCoreE2ETest` (all 29 cases and the cited class name stayed) → oversized 22→21,
baseline 149→148. **Twenty-third split landed (29/09):** `SemanticResolutionSupport` (driver +
SEM025/SEM050 oracles) + `SemanticResolutionPrograms` (19 hoisted programs) out of
`SemanticResolutionTest` (all 30 cases and the cited class name stayed) → oversized 21→20,
baseline 148→147. **Twenty-fourth split landed (29/09):** `CmdDeploySupport` (16 helper
methods/records, extracted) out of `CmdDeployTest` (all 16 cases and the cited class name stayed)
→ oversized 20→19, baseline 147→146. **Twenty-fifth split landed (30/09):** `DomainGapParityMatrixTest`
(the R6 ledger test + `repoRoot`/`GAP_CODE`, extracted) out of `DomainGapCodesTest` (all 31 behavior
cases and the cited class name stayed; 6 doc citations of the ledger moved) — the file had regrown
past 500 with the 30/09 `zip`/`NAT008` pins → oversized 492, baseline 146 (unchanged; the class was
not in the frozen baseline). The metric is a
guide, not an oracle: naming candidates in this queue (and in `README`)
itself adds citations to a class, so **re-measure `--citations` before choosing the next split**.
That rule + ordering is the traced Phase 3 todo.
**How to finish:** Phase 1/2 discovery done — then **Phase 3 modularization** (re-measure
`--citations`; extract support and move only uncited tests, keeping cited methods and class names)
and remaining **Phase 2 quick-win removals** interleaved (shrink the baseline: sleeps / duplication
/ oversized) → 4 (harness) → 5 (targets) → 6 (conformance) → 7 (`mvn verify`). **Pure test
infrastructure — the compiler is never touched** (golden rule below). One slice per commit,
RED-first + `check_500`.

## 📌 Overview

The repository currently has **thousands of tests**, but they are not
organized as a test architecture. They grew along with the compiler.

The problem is not the quantity.

The problem is that, over time, these emerged:

- repeated tests;
- equivalent scenarios written in different ways;
- giant tests trying to validate many things;
- test classes heavily coupled to the implementation;
- syntax tests mixed with lowering tests;
- lowering tests mixed with execution;
- E2E execution mixed with conformance;
- stress tests living next to fast tests;
- suites whose feedback is slow.

This compromises three things:

1. development speed;
2. compiler reliability;
3. the project's engineering quality.

## 🎯 Objective

Turn the tests into an organized, modular, fast and deterministic system.

The suite must stop being just a large volume of `*Test.java` files and
acquire clear validation layers.

## 🧠 Kof's Testing Philosophy

Proposed as the project's official philosophy:

> A test does not exist to prove the code works.
>
> A test exists to prevent an engineering decision from being lost in the
> future.

Consequently:

- every fixed bug stays protected;
- every design decision stays documented;
- every observable behavior stays validated;
- no test exists merely to inflate a number.

## 🏗️ Layered Architecture

Proposed formal architecture:

```
L0 - Unit Tests
    Parser
    Lexer
    AST
    Typer
    Semantic Analysis

L1 - Component Tests
    Lowering
    Codegen
    IR
    Optimizer
    Backend
    ABI

L2 - Target Execution
    JVM
    Native
    JavaScript
    Script
    Android

L3 - E2E
    Compile
    Run
    Compare stdout
    Check exit code

L4 - Conformance
    syntax
    semantics
    stdlib
    operators
    runtime

L5 - Stress
    concurrency
    memory
    fuzzing
    load
    stability
```

## 🔥 Main Identified Problems

### 1. Massive structural repetition

Currently many tests:

- create a compiler;
- load code;
- compile;
- execute;
- check a string.

This repeats across practically the whole suite.

Proposed: create an official **Kof Test Harness**, centralizing:

```
compile()
run()
expect()
expectOutput()
expectDiagnostic()
```

## 2. Lack of isolation between targets

Today the tests:

```
JVM
Native
JS
Script
```

end up coexisting in the same test repository without explicit boundaries.

Proposed formal separation:

```
compiler/
native/
jvm/
js/
script/
shared/
conformance/
```

## 3. Giant tests

There are files with hundreds of scenarios.

This makes it hard to:

- debug;
- run in isolation;
- measure time;
- discover regressions.

Proposed: split by responsibility.

## 4. Absence of execution profiles

Currently the developer practically runs everything.

There should be profiles:

### Fast

```
mvn test -Pfast
```

Goal: feedback under ~30 seconds.

Contains:

- Parser
- Lexer
- Typer
- Lowering
- Unit
- Component

### Integration

```
mvn test -Pintegration
```

Contains:

- targets
- execution
- golden
- ABI

### Full

```
mvn test
```

Everything.

### Stress

```
mvn test -Pstress
```

Contains:

- concurrency
- memory
- fuzzing
- stability

This separation prevents 10-minute tests from dictating the daily pace.

## 5. Lack of traceability

Today there is no easy map between:

- feature
- test
- bug
- decision

Proposed: each test family declares:

```
Feature:
Records
Pattern Matching
Generics
FFI
```

and:

```
Coverage:
Parser
Typer
Lowering
JVM
Native
JS
```

## 6. Golden tests

Proposed: an official **Golden Suite**.

It should contain:

- real examples;
- compiled code;
- expected output;
- exit code;
- hash.

Goal:

```
same code
↓
same output
↓
on every target
```

## 7. Regression tests

Today many bugs become a single testcase.

Proposed official policy:

> Every fixed bug must generate:
>
> 1. Minimal reproduction
> 2. Regression test
> 3. Permanent reference

The test must never be removed.

## 8. Compiler-crash tests

Today many tests try to reproduce errors.

What's missing is a dedicated Stability suite.

It should validate:

- the parser never hangs;
- lowering never throws an unexpected exception;
- the typer never loops;
- invalid code always produces a diagnostic;
- the AST never ends up inconsistent.

## 9. Deterministic tests

No test may depend on:

- the current time;
- the network;
- a specific operating system;
- an external tool's availability without an explicit guard.

Every external dependency must be protected by honest environment guards
(`assumeTrue` + documented gap — R6, never a silent skip).

## 10. Feedback cost

Proposed: continuous measurement.

Generate a report:

```
test
time
failures
stability
```

The slowest tests must be permanently monitored.

## 🧪 Refactoring Strategy

The refactoring must NOT touch the compiler.

It changes only the test infrastructure.

### Phase 1 — Profiling

Instrument the whole suite.

Discover:

- time per class
- time per target
- duplicated tests
- redundant tests
- unstable tests

### Phase 2 — Quick Wins

**State (28/09):** discovery + guard LANDED — `scripts/test-suite-audit.sh`
measures the leads (sleeps / oversized / duplicate names); `scripts/check_test_hygiene.sh`
is the ratchet over the frozen `scripts/test-hygiene-baseline.txt`
(`--write-baseline` only after improving). The removals below are the open work
(shrink the baseline, then re-freeze).

Remove:

- repetition;
- sleeps;
- unnecessary loops;
- redundant setup.

### Phase 3 — Modularization

Separate layers:

```
compiler tests
backend tests
target tests
conformance tests
stress tests
```

### Phase 4 — Harness

Build the official infrastructure. **First slice landed (29/09):** the duplicated media byte-layout
helpers (`be32`/`type4`/`le16`/`le32`/`clipMp4`/`clipZeroSizeMp4`/`wav`) of `MediaCrossE2ETest` and
`MediaNativeE2ETest` were consolidated into a shared `MediaByteSupport` base — eliminating 3
`dupname` ratchet keys (both E2E classes stay green, zero citation drift). **Second Phase 4 slice (29/09):** `KofCSupport` consolidated the duplicated C-compiler harness
(`has`/`requireTools`/`run`/`assertAllPrograms`/`compile` + the `Prog` record) across
`KofCParamsCompilerTest`, `KofCStructCompilerTest`, `KofCCrossCompilerTest` and `KofCObjectCompilerTest`
— removing 2 `dupname` keys (`assertAllPrograms`, `requireTools`; `has`/`run`/`compile` persist
declared in other modules), all 4 classes green. **Third Phase 4 slice (29/09):** the 3 golden-loop
`@Test` methods (`x86OracleMatchesEveryGolden`/`riscv64MatchesEveryGolden`/`aarch64MatchesEveryGolden`)
shared by `KofCParamsCompilerTest`/`KofCStructCompilerTest` moved to an intermediate
`KofCGoldenSupport` base (only those two extend it, so no leak to Cross/Object) — 3 more `dupname`
keys removed; both classes keep 7/9 tests green. Harness baseline 146→**138**
(8 `dupname` keys eliminated across the three slices). **Fourth Phase 4 slice (29/09):** the 3
identical log-level `@Test` methods (`errorLevelSuppressesInfo`/`offSuppressesEverything`/
`warnGoesToStderr`) shared by `KofLogE2ETest`/`NativeLogE2ETest` moved to a `LogLevelSupport` base
(each subclass supplies its `run`); 3 more `dupname` keys removed, counts preserved (11/7). Harness
baseline 146→**135** (11 keys eliminated across the four slices). **Fifth Phase 4 slice (29/09):**
the identical WebSocket frame helpers (`writeMaskedFrame`/`readFully`, with `MASK`) shared by
`KofWebHardeningTest`/`KofWebWsE2ETest` moved to a `WsFrameSupport` base; 2 more `dupname` keys
removed. Harness baseline 146→**133** (13 keys eliminated across the five slices). **Sixth Phase 4 slice
(29/09):** the identical `javac` helper shared by `CompareTest`/`MigrateTest` (kof-cli) moved to a
`CliJavacSupport` base — 1 more `dupname` key. Harness baseline 146→**132** (14 keys eliminated
across the six slices).

**Phase 4 identical-pair harness EXHAUSTED (29/09):** six slices reduced the ratchet 146→132 by
consolidating methods whose bodies are **byte-identical** across exactly two classes (media bytes,
C harness, C golden loop, log level, WebSocket frames, `javac`). The audit confirms **zero
identical pairs remain**; every remaining `dupname` is a test/harness shared by design across two
targets (e.g. `KofCParamsCompilerTest`×`KofCStructCompilerTest`'s golden tests, `execArithmetic`
in `JvmE2ETest`×`KofJsE2ETest`, `native*MatchesJvmGolden` in `NativeAarch64`×`NativeRiscv64`).
Consolidating those needs a **target-parameterized cross-target harness** (Phase 5) — a design
increment, not a zero-risk refactor; forcing it would hide the target under abstraction hooks and
risk the cited per-target counts. Tool to scope it: `scripts/test-suite-audit.sh --dups` (read-only)
lists each duplicated name with its declaring classes; the current heads are `main` (32 classes),
`assumeToolchain` (26), `copyLibrary` (16), `stopServer` (12), `jvmOracle` (12).

Phase 3 note (29/09): pure extraction is now exhausted — **43 oversized classes down to 19** over 24
splits, all with zero citation drift; the remaining oversized classes either belong to an active
lane or require moving test methods (citation sweep), which is Phase 4/5 scope. **Re-opened once for
a regrowth (30/09):** `DomainGapCodesTest` crossed 500 again with the `zip`/`NAT008` pins — the
hygiene `oversized` key count 19→20 (one NEW key, RED) — a twenty-fifth pure extraction
(`DomainGapParityMatrixTest`, the R6 ledger) returned it to 492 → the key count back to **19**.

### Phase 5 — Targets

Separate execution:

```
JVM
Native
JS
Script
```

**First Phase 5 slice LANDED (03/10, `D-TEST-ARCHITECTURE-PHASES`):** the
byte-identical cross-target harness of the `NativeIo*CrossTest` family
(15 classes: bytes/dir-delete/dir-list/fs/mkdirs/copy/move/normalize/path/
read-range/resolve/size/stat/text/to-absolute) was consolidated into a
target-parameterized `NativeCrossSupport` base — `has` (toolchain guard),
`capture`, `runJvm` (JVM oracle) and `runCross` (compile + qemu, parameterized
by `Target`), plus the instance conveniences `runJvm`/`runCross`/`runNative`
and `base` used by copy/move. ~716 duplicated helper lines removed (family
1995→1172, plus the 107-line base; `NativeIoCopyCrossTest` 147→75). **Each target stays a real,
separately-named `@Test`** (JVM / riscv64 / aarch64): only the execution
mechanism is shared, the per-target counts are preserved. Proof: **54/54**
`NativeIo*CrossTest` + `NativeCrossWideArgsE2ETest` green (cross riscv64/aarch64
actually executed, not skipped); harness ratchet stays at **131** keys with zero
new debt. Remaining Phase 5 work: the shared JVM-oracle `@Test` (`jvmOracle`, 13
declarations) and the other cross-target families (`main`, `assumeToolchain`,
`copyLibrary`, `stopServer`) — a design increment, deferred to keep this slice
zero-risk.

**Second Phase 5 slice LANDED (03/10):** the 13 byte-identical `jvmOracle`
`@Test` methods (12 `NativeIo*CrossTest` + `NativeCrossWideArgsE2ETest`) moved
to a new `NativeIoJvmOracleSupport` base — the subclass supplies
`jvmOracleSource(tempDir)` + `jvmOracleExpected()`, the base owns the `@Test`.
Each subclass keeps its own source/golden, so no per-target or per-face
assertion is hidden. Proof: **54/54** `NativeIo*CrossTest` +
`NativeCrossWideArgsE2ETest` green (cross riscv64/aarch64 executed); the
`dupname jvmOracle` ratchet key is **eliminated** (baseline 131→130). The 4
classes with a non-standard JVM shape (`copy`/`move`/`text`/`metadata`) keep
their own `@Test` on `NativeCrossSupport`; `NativeIoMetadataE2ETest` (same
family) was also moved onto `NativeCrossSupport` (4/4 green). Remaining: `main` (mostly Kof source in
text blocks, a false lead), `assumeToolchain` (26 divergent signatures) and
`copyLibrary` (35 classes, two shapes) — each needs its own bounded increment.

**Phase 5 slice 3 LANDED (03/10):** the duplicate `targetGapRefusal` `@Test`
(`CryptoSignE2ETest` × `KeyExchangeE2ETest`, same name/different named code —
introduced by the D-KOF-SIGN lane's `9d2f5c81e`, which had left the hygiene gate
RED) was consolidated into a new `TargetGapRefusalSupport` base: the subclass
supplies `gapProgram()`/`gapCode()`/`gapLabel()`, the base owns the `@Test`.
`CryptoSignE2ETest` 3/3 + `KeyExchangeE2ETest` 6/6 green; `check_test_hygiene`
back to rc=0 (130 keys, 0 new debt).

**Phase 5 slice 4 LANDED (03/10):** the duplicated child-process teardown
(`private Process serverProcess;` + `@AfterEach stopServer()` — destroy → wait 5s
→ destroyForcibly) copied across 11 server-spawning E2E classes
(`KofHttp*`/`KofWeb*`/`PaginationPageRequestE2ETest`/`KofMediaE2ETest`) was
consolidated into a new `ServerProcessSupport` base that owns the field and the
single `@AfterEach`. The 7 classes with no other base extend it directly; the 4
that already extend `WsFrameSupport`/`KofWebPrograms`/`KofMediaSupport` now reach
it through those supports (each re-based on `ServerProcessSupport`) — so the
`stopServer` method name lives in exactly ONE class. `KofHttpServerTest`'s
in-process `@AfterEach` (it closes a `KofHttpServer`, not a child `Process`) was
renamed `closeServer` to keep it distinct; its behaviour is unchanged. No test
body, target count or assertion moved. Proof: the 12 affected batteries
**118/118** green (web/http/media/pagination + `KofHttpServerTest`); the
`dupname stopServer` ratchet key is **eliminated** — baseline re-frozen
130→**129**. `KofOAuthResourceServerTest`'s `cleanup()` (same process stop plus a
`jwksServer` stop) was deliberately left as-is: it is not a `stopServer` duplicate
and folding it would need a second teardown hook.

**Phase 5 slice 5 LANDED (03/10):** the JVM-run helper `assertRuns` was
byte-identical in 7 core E2E classes (`FnTypeInGenericDeclaredTypeTest`,
`HeterogeneousListInferTest`, `ReduceStringCastTest`, `NestedFnTypeArityTest`,
`LambdaFieldCaptureTest`, `FnTypeFieldCallTest`, `PrimitiveStringEqTest`) — both
the 3-arg and the 2-arg overload. It now lives once in a new `JvmRunSupport` base
(which owns `runJvmMain` + the two `assertRuns` overloads); each of the 7 classes
extends it. `KofCacheE2ETest`'s same-named helper is a different shape
(`(Path, String, String, Target, String)` compiling and running the chosen
target) and was renamed `assertTargetRuns` so the `assertRuns` name is not
overloaded across classes by accident. No test body, target or assertion moved.
Proof: the 8 affected batteries **38/38** green; the `dupname assertRuns` ratchet
key is **eliminated** — baseline re-frozen 129→**128**.

**Phase 5 slice 6 LANDED (03/10):** the multi-source run helpers were
byte-identical across 4 core E2E classes — `runScript(Path root, List<Path> sources, String expected)`
in `SealedTypeE2ETest`/`TypeVarianceE2ETest`/`UseSiteVarianceE2ETest`/`InteropSchemaE2ETest` and
`runJs(Path root, List<Path> sources, String expected)` in 3 of them. They now live once in a new
`MultiSourceRunSupport` base (which also owns the shared `driver`), which the 4 classes extend.
No test body, target or assertion moved. Proof: the 4 affected batteries **47/47** green; the
`dupname runScript` ratchet key is **eliminated** — baseline re-frozen 128→**127**. (`runJs` stays a
key: `KofRandomTest`/`KofStringsIndentDedentTest` define different-shape `runJs` helpers, left as-is.)

**Phase 5 slice 7 LANDED (03/10):** the library-install runner `runKof` (sets `kof.install.dir`, compiles to
JVM, loads `Default.Main` by reflection) was byte-identical in 6 Kofmd E2E classes
(`KofmdE2ETest`/`KofmdVocabE2ETest`/`KofmdFormatE2ETest`/`KofmdCorpusE2ETest`/`KofmdRoundTripE2ETest`/
`KofmdLspSupportE2ETest`) and `PdfLibraryE2ETest` (only the temp-dir prefix differed). It now lives once in a
new `KofmdRunSupport` base (which also owns the shared `driver`/`tmp`), with the subclass supplying
`copyLibrary` and an overridable `outPrefix()`. No test body, target or assertion moved. Proof: the 7
affected batteries **19/19** green; the `dupname runKof` ratchet key is **eliminated** — baseline re-frozen
127→**126**.

**Phase 5 slice 8 LANDED (03/10):** the JVM+JS output-parity helpers `driver`/`runJvm`/`runJs`/`assertBoth`
were byte-identical across `JsIfFoldStatementE2ETest`/`JsLoopIfTailE2ETest` (identical) and
`NullablePrimitiveRelationalConditionTest` (whitespace-only signature wrapping) — the helper body compiles a
program to JVM and JS, runs both, asserts the JVM output against the expected value and the JS output
against the JVM output. They now live once in a new `JsParityRunSupport` base, which the 3 classes extend.
No test body, target or assertion moved. Proof: the 3 affected batteries **18/18** green (JsIfFold 8,
JsLoopIfTail 7, NullableRelational 3); the `dupname assertBoth` ratchet key is **eliminated** — baseline
re-frozen 126→**125**.

**Phase 5 slice 9 LANDED (04/10):** the library-install pair `copyLibrary` + `findLibraryRoot` was
byte-identical (modulo the library name and its marker file) across 36 E2E classes spanning `libs/file`,
`libs/interop`, `libs/image`, `libs/kofmd` and `libs/pdf`. It now lives once in a new
`LibraryInstallSupport` interface (default `copyLibrary`/`findLibraryRoot`/`findLibsRoot`), which the 36
classes implement — each supplying only `libraryName()` + `libraryMarkers()`; the four multi-library
connectors additionally override `libraryNames()` (`file`+`interop`). The audit counts only `void`
methods, so the non-void providers add no key. `KofmdRunSupport` also implements the interface and its
abstract `copyLibrary` was dropped. No test body, target or assertion moved. Proof: the 36 affected
batteries **268/268** green (1 honest toolchain skip); the `dupname copyLibrary` ratchet key is
**eliminated** — baseline re-frozen 125→**124**.
Follow-up **#750** caught the ratchet still RED after the concurrent TIFF landing: `TiffDecodeE2ETest`
was the 37th class with its own `void copyLibrary`. It now implements the same interface (`image` +
`Tiff.kf`) and its local install helpers were deleted; `check_test_hygiene` measures **124 keys, rc=0**.

**Phase 5 slice 10 LANDED (04/10):** the JVM+JS helper suite `runJvm`/`runJs`/`runBoth` was identical
across `ArrayBoundsSafetyE2ETest`, `CoreRegressionE2ETest` and `WrapperStaticCallsE2ETest` (the
`runJvm` body differed only by two-space indentation). It now lives once in a new `JvmJsRunSupport` base
(which also owns the shared `driver`), which the 3 classes extend. `WrapperStaticCallsE2ETest` keeps its
own `runNativeX86`. The new `TiffDecodeE2ETest` (image lane) was migrated onto `LibraryInstallSupport` so
the `copyLibrary` key stays eliminated. No test body, target or assertion moved. Proof: the 4 affected
batteries **126/126** green; the `dupname runBoth` ratchet key is **eliminated** — baseline re-frozen
124→**123**.

**Phase 5 slice 11 LANDED (04/10):** the 3-target `runAll3(Path, String, String)` helper was byte-identical
(modulo `private`/`protected` and comments) between `NullableBoolTruthinessE2ETest`/`TrooleanLawE2ETest`
and the existing `NullablePrimitiveContractSupport` base — the two classes now extend the base and their
duplicated `runJvm`/`runScript`/`runJs`/`runAll3`/`assertTarget` copies are gone, so `runAll3` lives in
exactly one class. The two genuinely different shapes were renamed: `NullablePrimitiveFieldWriterE2ETest`'s
4-target helper (it adds `runNativeX86`) → `runAll4Targets`, and `AsCastPrecedenceE2ETest`'s inline
name-based helper → `runAllThree`. No test body, target or assertion moved. Proof: the 4 affected batteries
**43/43** green (NullableBoolTruthiness 15, TrooleanLaw 13, NullablePrimitiveFieldWriter 9,
AsCastPrecedence 6); the `dupname runAll3` ratchet key is **eliminated** — baseline re-frozen 123→**122**.

**Phase 5 slice 12 LANDED (05/10):** the `assumeToolchain` family — the last
remaining `dupname` cluster (26 classes, each with its own private copy of the
same "does this binary exist?" guard) — is consolidated behind a new
`NativeToolchainAssumptions` interface (prefix-aware `hasTool` §591 + the named
guards `assumeNativeRiscv64`/`assumeNativeRiscv64WithSysroot`/`assumeNativeAarch64`/
`assumeNativeX86_64`/`assumeMcuRiscvAsm`/`assumeMcuArmAsm`, plus the generic
`assumeToolchain(String...)` the abstract supports forward to). The 26 classes
implement the interface and delete their local declarations; every no-arg call
site now names the guard it needs (`assumeToolchain()` → the explicit guard), so
the tool set each test requires is visible at the call site instead of buried in
a per-class body. `KofHttpNativeResilienceCrossTest`'s distinct
`assumeToolchain(String arch)` was renamed `assumeArchToolchain` so the name is
not accidentally overloaded. No test body, target or assertion moved. Proof: the
25 affected batteries **342 run / 0F / 0E / 15 skipped** (the skips are the
honest absent-cross/qemu guards); `check_test_hygiene` rc=0 with the
`dupname assumeToolchain` key **eliminated** — baseline re-frozen 122→**121**
(the 3 reported keys are the PDF lane's untracked `PdfTextE2ETest` +
`startServer`, pre-existing external).

**Phase 5 slice 13 LANDED (05/10):** the last two toolchain `dupname` clusters —
`assumeAarch64` (8 classes) and `assumeCross` (2 classes) — are consolidated onto
the same `NativeToolchainAssumptions` interface. `assumeNativeAarch64WithSysroot()`
was added (mirror of the riscv64 variant) for `NativeRiscvDtoaTest`, whose local
`assumeAarch64` also required the libc cross sysroot; the other 7 classes map to
`assumeNativeAarch64()`. `NativeRiscvDbWireTest` and `PlatformSeamSabotageTest`
(which still kept a full local `has`) now implement the interface too, and
`KofConfigCrossTest` dropped its local `has`/`assumeCross` pair (its local `has`
was used only by the guard). No test body, target or assertion moved. Proof: the
10 affected batteries **74 run / 0F / 0E / 17 skipped** (the skips are the honest
absent aarch64/qemu guards); `check_test_hygiene` rc=0 with both `dupname` keys
**eliminated** — baseline re-frozen 121→**119**. The earlier `dupname startServer`
NEW key (from the #756 `KofHttpErrorContractE2ETest` helper) was also cleared by
renaming it `startContractServer`.

**Phase 5 slice 14 LANDED (08/10, lane compiler/JVM/native `192.168.15.30:9092`):**
the `runQemu` `dupname` cluster — six classes (`KofUuidTest`, `KofStringsSupport`,
`KofValidationSupport`, `KofStringsIndentDedentTest`, `KofTimeE2ETest`,
`KofRandomTest`) each declared a byte-equivalent helper that compiles a Kof source
for a cross target and runs the binary under QEMU, asserting exit 0. The helper
now lives once in a new `QemuRunSupport` interface (a `default` method over an
abstract `driver()` accessor), which extends `NativeToolchainAssumptions`; the six
classes implement it and their local copies are deleted. `KofRandomTest`'s three
call sites pass the qemu arch name explicitly (`qemu-riscv64`/`qemu-aarch64`),
matching the other five. No test body, target or assertion moved. Proof: the six
affected batteries **151 run / 0F / 0E**; `check_test_hygiene` rc=0 with
`dupname runQemu` **eliminated** — baseline re-frozen 119→**118**.

**Phase 5 slice 15 LANDED (08/10, lane compiler/JVM/native `192.168.15.30:9092`):**
the `runQemuE` `dupname` cluster — `KofNetTest` and `KofEncodingTest` each
declared a byte-equivalent helper that compiles a Kof source for a cross target,
runs the binary under QEMU and asserts the stdout equals the JVM oracle (a
superset of `runQemu`, which only asserts exit 0). The helper now lives once as a
second `default` method on the existing `QemuRunSupport` interface; both classes
implement it (adding the `driver()` accessor over their existing field) and their
local copies are deleted. No test body, target or assertion moved. Proof: the two
affected batteries **18 run / 0F / 0E / 0 skipped** (the cross riscv64+aarch64
legs actually executed); `check_test_hygiene` rc=0 with `dupname runQemuE`
**eliminated** — baseline re-frozen 118→**117**.

**Phase 5 ratchet repair LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):**
the `dupname runQemu` key reappeared as NEW debt after the M1/native-cross landings
stacked local helpers again instead of the shared one. The block that was
byte-duplicated as a private `runQemu(Path dir, String arch, Path bin)` — run the
cross binary under QEMU with `LD_LIBRARY_PATH=dir`, require exit 0 within 60 s,
return the normalised stdout — now lives once as `NativeRiscv64E2ETest.runQemuWithLibPath`
(the class that already owns `qemu()`/`runBounded`). `BufferRuntimeBorrowE2ETest`,
`FfiNativeArrayE2ETest` and `FfiNativeStringArrayE2ETest` call it and drop their local
copies; `FfiCrossHfaReturnE2ETest`'s `void runQemu(...)` (a compile-and-assert wrapper
with a distinct signature) is renamed `runQemuCrossFixture` and delegates its run tail
to the shared helper. No test body, target or assertion moved. Proof (executed):
`check_test_hygiene` rc=0 (`dupname runQemu` eliminated, 117 keys, 0 new debt);
`mvn -o -pl kof-compiler -am test-compile` rc=0; the affected batteries
`BufferRuntimeBorrowE2ETest` 8/0F, `FfiNativeArrayE2ETest` 3/0F,
`FfiNativeStringArrayE2ETest` 2/0F, `FfiCrossHfaReturnE2ETest` 5/0F/0 skipped
(the cross riscv64+aarch64 legs executed), `NativeRiscv64E2ETest` 58/0F.

### Phase 6 — Conformance

Build the official equivalence suite.

**Phase 6 slice 1 LANDED (05/10):** the official equivalence suite (`tests/golden/`)
covered only JVM + native; the plan's Golden Suite defines the goal as
"same code → same output on every target" with the **exit code** checked, and
the target list is JVM/Native/JS/Script. `tests/run-golden.sh` now runs every
case on all four targets — `jvm`/`native`/`js` (build + run the artifact) and
`script` (`kof run --target script`, direct IR interpretation) — asserting the
captured stdout equals `expected.txt` AND the exit code is `0`. The optional
`--target` selector and positional case filter were added; the default (what
CI/release call) runs all four. External tools are guarded honestly (R6): a
target whose runtime is absent (`as`/`ld` for native, `node` for js) is
**SKIPPED with the reason**, never silently passed. No compiler, test class or
assertion changed — test infrastructure only. Proof (executed):
`tests/run-golden.sh` **48/48** (12 cases × 4 targets: jvm, native, js, script),
exit 0.

**Phase 6 slice 2 LANDED (05/10):** the equivalence suite's *coverage* grew from
12 to **16 cases** — four new language-surface cases chosen to exercise
contracts the old set did not: `null-safety` (nullable narrowing + `if (x !=
null)`), `map-set` (`mapOf`/`put`/`getOrDefault`/`containsKey` + `setOf`/`add`/
`contains`/`size`), `pipelines` (`sorted`/`distinct`/`any`/`all`/`count`/`find`/
`map`/`filter`), and `switch-expr` (switch as an expression `case -> ...` +
switch statement, `break` optional). Every case is validated on all four targets
by the same runner, so the "same code → same output on every target" contract is
now pinned for these four surfaces too. One expected value was corrected during
RED-first authoring (`sorted()` on `[3,1,2,1]` is `[1,1,2,3]`, not `[1,2,3,3]`),
confirming the runner catches a wrong golden. No compiler change — test
infrastructure only. Proof (executed): `tests/run-golden.sh` **64/64**
(16 cases × 4 targets), exit 0.

**Phase 6 slice 3 LANDED (05/10):** four more cases — **20 total** — covering the
object model and generics: `classes` (explicit constructor + mutable fields +
`extends` + implicit override + `super(name)`, field write through `this`),
`interfaces` (`implements` + a `List<Speaker>` dispatched virtually),
`generics-box` (`class Box<T>(T value)` erasure + `substituteTypeVariable` on
JVM and Native), and `enum` (`enum Color { … }` + `name()` + `values().size`).
These are the surfaces where JVM/Native/JS erasure most often diverges, so
pinning them on all four targets is the highest-value coverage increment left in
Phase 6. Proof (executed): `tests/run-golden.sh` **80/80** (20 cases × 4
targets), exit 0.

**Phase 6 slice 4 LANDED (05/10):** three more cases — **23 total** — closing the
remaining high-value surfaces: `strings-methods` (`trim`/`substring`/`startsWith`/
`endsWith`/`indexOf`/`toUpperCase`/`toLowerCase`/`charAt` + content `==`),
`closures` (mutable capture via the synthetic `BoxN` + capture inside `map`/
`filter`), and `sealed-switch` (`sealed class` + exhaustive switch expression
without `default` — the §X5.1/§X5.2 contract, erased in codegen). Proof
(executed): `tests/run-golden.sh` **92/92** (23 cases × 4 targets), exit 0.

**Phase 6 slice 5 LANDED (05/10):** three more cases — **26 total** — pinning the
loop-control and numeric/string surfaces the old set did not exercise:
`loops-control` (`do-while` runs its body once then loops on the condition,
`break` exits a `for`, `continue` skips an iteration in both a `for` and a
`for-in`), `numeric-casts` (`3.9 as Int` truncates to `3`, integer division `7/2`
= `3` and modulo `7%3` = `1`, arithmetic precedence `2 + 3 * 4` = `14` vs
`(2 + 3) * 4` = `20`, `Int`→`Double` promotion `5 + 2.5` = `7.5`, `5 as Double / 2`
= `2.5`) and `string-parts-valueof` (`split(",")` + indexed `String[]`,
`toCharArray()` + `chars[0] as Int` = the code unit, content-insensitive
`equalsIgnoreCase`, and `String.valueOf` for `Int` and `Double`). Every case is
validated on all four targets by the same runner. Each value was measured on the
Script target first and cross-checked against the Kof contract before the golden
was frozen. No compiler change — test infrastructure only. Proof (executed):
`tests/run-golden.sh` **104/104** (26 cases × 4 targets), exit 0.

**Phase 6 slice 6 LANDED (05/10):** three more cases — **29 total** — pinning the
operator, collection-mutation and enum-exhaustiveness surfaces:
`bitwise-ops` (`&`/`|`/`^`/`<<`/`>>` — the operators most likely to diverge
because JS bitwise is 32-bit while the JVM/Native paths are 64-bit, so the
cross-target equality is a real guard), `list-map-mutation` (`list.add`/`get`/
`set` and `map.put`/`get`/`keys().size` after construction, distinct from the
read-only `collections`/`map-set` cases) and `enum-switch-expr` (an enum-typed
switch expression with no `default` — the exhaustiveness contract for enums,
distinct from the `sealed class` form in `sealed-switch`). Every case is
validated on all four targets. Proof (executed): `tests/run-golden.sh`
**116/116** (29 cases × 4 targets), exit 0.

**Phase 6 slice 7 LANDED (06/10):** two more cases — **31 total** — pinning the two
surfaces the #770/#772 family just exercised and the wide-integer contract:
`std-math-nullable` (a narrowed `Int?`/`String?` fed to a primitive-arg std call —
`math.abs`/`math.min`/`math.max`/`math.parseInt` through null guards, the exact
shape that regressed on native in `known-bugs` §612) and `long-arithmetic` (64-bit
`Long` add/sub/mul/div/mod, unary minus, relational and the round-trip identity —
the surface most likely to diverge because JS uses `BigInt` while JVM/Native are
64-bit, so cross-target equality is a real guard). Both are validated on all four
targets, plus riscv64/aarch64 under qemu during authoring. Proof (executed):
`tests/run-golden.sh` **124/124** (31 cases × 4 targets), exit 0.

**Phase 6 slice 8 LANDED (06/10):** two more cases — **33 total** — pinning the
floating-point formatting contract and the concurrency surface:
`double-formatting` (`Double` literals and arithmetic — `1.0`, `2.5`, `1.0/3.0`
= `0.3333333333333333`, the IEEE-754 artifact `0.1 + 0.2` =
`0.30000000000000004`, `1.0/0.0` = `Infinity`, `1e3` = `1000.0`, `7.5 % 2.0` =
`1.5`; JS `Number`/`BigInt` vs JVM/Native `double` formatting is a real
divergence guard) and `concurrency-spawn-await` (`val h = spawn f(n)` with typed
`Handle<T>` + `await h` unboxing, two tasks joined and combined; the frozen
`spawn`/`await` contract on all four targets). Both are validated on all four
targets, plus riscv64/aarch64 under qemu during authoring. Proof (executed):
`tests/run-golden.sh` **132/132** (33 cases × 4 targets), exit 0.

**Phase 6 slice 9 LANDED (06/10):** one more case — **34 total** — pinning the
`return`-through-`finally` contract the §613 native SIGSEGV exposed:
`finally-return` (`return` inside the `try` AND inside the `catch` of a
`try/catch/finally`, with the `finally` running on both paths — the exact shape
of `known-bugs` §613). Validated on all four targets. Proof (executed):
`tests/run-golden.sh` **136/136** (34 cases × 4 targets), exit 0.

**Phase 6 slice 10 LANDED (06/10):** one more case — **35 total** — pinning the
two `try/finally` abrupt-completion faces the Phase-6 slice-9 sweep catalogued
as `known-bugs` §617, now FIXED: `finally-control-flow` combines a
`break`/`continue` leaving a `try` (the finally MUST run before the jump) with a
nested `try/finally` whose inner try `return`s (the outer finally runs, the inner
value survives). Validated on all four targets; additionally run on
riscv64/aarch64 under qemu during authoring. Proof (executed):
`tests/run-golden.sh` **140/140** (35 cases × 4 targets), exit 0.

**Phase 6 slice 11 LANDED (08/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **36 total** — pinning the `List` higher-order/query surfaces the
`pipelines` case left uncovered: `list-higher-order` exercises `reduce(lambda,
seed)` (seed form, `SEM073` if omitted), `indexOf`/`lastIndexOf` (`-1` when
absent), `isEmpty`, `none(pred)`, `find(pred)` (the first match), `slice(off,
len)`/`take(n)`/`drop(n)` (materialized copies, clamped), `groupBy` (`Map<K,
List<E>>`), `flatMap` (flattened list), `sort()` in place, `addAll`, `subList`,
`remove` and `sorted(comparator)` with a descending comparator. The `zip` face
is deliberately NOT included: it is the documented `NAT008` native gap (a
primitive element crosses a bare type parameter), so a golden case requiring all
four targets cannot pin it — the refusal is the contract. Validated on all four
targets. Proof (executed): `tests/run-golden.sh` **144/144** (36 cases × 4
targets), exit 0.

**Phase 6 slice 12 LANDED (08/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **37 total** — pinning the `Map`/`Set` method surface the `map-set`
case left uncovered (that case only exercised `mapOf`/`put`/`get`/
`getOrDefault`/`containsKey` and `setOf`/`add`/`contains`/`size`):
`map-methods` exercises `size`, `containsValue`, `putIfAbsent` (returns the
previous value and does NOT overwrite; `null` on a new key), `remove(key)`
(returns the removed value), `isEmpty`/`clear`/`size` on both `Map` and `Set`,
and `Set.add` of a duplicate leaving the size unchanged. Validated on all four
targets. Proof (executed): `tests/run-golden.sh` **148/148** (37 cases × 4
targets), exit 0.

**Phase 6 slice 13 LANDED (08/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **38 total** — pinning the compound-assignment surface, which no
earlier case exercised: `compound-assign` applies `+=`/`-=`/`*=`/`/=`/`%=` to an
`Int` accumulator (`10 → 15 → 12 → 24 → 6 → 1`), `+=` to a `String` (`"a"` →
`"abc"`) and `+=` to a `Double` (`1.5` → `4.0`, keeping the floating type).
Validated on all four targets. Proof (executed): `tests/run-golden.sh`
**152/152** (38 cases × 4 targets), exit 0.

**Phase 6 slice 14 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **39 total** — pinning the `Byte`/`Short`/`Char` arithmetic-promotion
contract (`D-KOF-BYTE-ARITH` / `known-bugs` §561, fixed 02/10), which no earlier
case exercised: `byte-arith` pins that `Byte`/`Short`/`Char` operands PROMOTE to
`Int` (`Byte * 256` = `16640`, `Byte + Byte` = `240` — the §561 repro that used to
crash `Byte.valueOf` on the JVM while JS/Native returned the un-truncated Int;
`Short + Short` = `2000`/`60000`, `Short * 3` = `90000`, `Char + Char` = `194`,
mixed `Byte + Int` = `165`, `Char - Char` = `25`), plus the explicit narrowing
`(bb + 1) as Byte` = `66` and the `as Byte` wrap `300 as Byte` = `44` (the `#471`
`i2b` face). The result is the un-truncated `Int`, so the JVM/JS/Native/Script
divergence §561 recorded is a real guard here. Validated on all four targets.
Proof (executed): `tests/run-golden.sh` **156/156** (39 cases × 4 targets), exit 0.

**Phase 6 slice 15 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **40 total** — pinning the `kof.strings` namespace, a large pure-Kof
stdlib surface (the parity matrix marks its predicates/case-conversions/
whitespace/escape faces ✅ on all four targets) that NO equivalence case exercised:
`strings-namespace` covers the ASCII predicates (`isAlpha`, `isNumeric`,
`isAlphaNumeric`, `isAscii`, `isUpperCase`, `isLowerCase` — including the
documented ASCII-boundary negatives `isAlpha("abc123")`=false,
`isUpperCase("123")`=false), `count` (NON-overlapping: `count("aabaabaa","ab")`=2,
`count("aaa","aa")`=1, empty sub = 0), `capitalize`/`uncapitalize` (exact mirror),
`reverse` (code-point, the NAT-STR01 face), `repeat` (`n<=0` = ""),
`truncate` (`n>=len` = original), `padLeft` (the pad is a String, 1st char used),
the case conversions `toCamelCase`/`toPascalCase`/`toSnakeCase` (the
uppercase+lowercase boundary — `HTTPServer`→`http_server`)/`toKebabCase`
(`XMLParser`→`xml-parser`), `slugify` (non-ASCII becomes a separator),
`removeWhitespace`/`normalizeWhitespace` (edge spaces collapsed) and `escapeHtml`
(the `&lt;`/`&amp;`/`&gt;` entities). This is a namespace-arity surface and a
genuine cross-target guard (native walks UTF-8 by code point). Validated on all
four targets. Proof (executed): `tests/run-golden.sh` **160/160** (40 cases × 4
targets), exit 0.

**Phase 6 slice 16 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **41 total** — pinning the `kof.math` namespace functions themselves:
the `std-math-nullable` case only exercised the nullable-narrowing SHAPE (`math.abs`/
`min`/`max`/`parseInt` behind null guards), never the namespace functions directly.
`math-namespace` covers the integer faces (`abs(-7)`=7, `sign(-9)`/`sign(0)`/`sign(9)`
= -1/0/1, `min`/`max`, `clamp(15,0,10)`=10 and `clamp(-5,0,10)`=0, the predicates
`isEven`/`isOdd`/`isPositive`/`isNegative`/`isZero`) and the Double faces (`sqrt(16.0)`
= `4.0`, `lerp(0.0,10.0,0.5)` = `5.0`, `percentage(3.0,4.0)` = `75.0`,
`isInteger(4.0)`=true/`isInteger(4.5)`=false, `isDecimal(4.5)`=true, `roundTo(3.14159,2)`
= `3.14` and the negative-decimals face `roundTo(1234.0,-2)` = `1200.0`). The Double
faces are the real cross-target guard: the native path computes `sqrt`/`lerp`/
`percentage`/`roundTo` in pure SSE2 (no libm) and JS uses `Number`, so the formatting
agreement is a genuine divergence check. Validated on all four targets. Proof
(executed): `tests/run-golden.sh` **164/164** (41 cases × 4 targets), exit 0.

**Phase 6 slice 17 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **42 total** — pinning the `kof.encoding` namespace, a pure-Kof stdlib
surface (parity matrix ✅ on all four targets) that NO equivalence case exercised:
`encoding-namespace` covers `hexEncode` (UTF-8 by bytes, lowercase — `"café"` →
`63 61 66 c3 a9`, so the é's two UTF-8 bytes `c3 a9` are the real byte-level guard),
`hexDecode` (`"4869"` → `"Hi"`, `"6869"` → `"hi"`), `base64Encode`
(`"Man"` → `"TWFu"`, `"hello"` → `"aGVsbG8="` with padding), `base64Decode`
(`"TWFu"` → `"Man"`, `"aGVsbG8="` → `"hello"`) and `urlEncode`/`urlDecode` — the
documented `%20`-NOT-`+` space rule and the reserved-char escaping
(`"a+b/c"` → `"a%2Bb%2Fc"`), with `urlDecode("caf%C3%A9")` → `"café"`. This is a
byte-level cross-target guard: the native path walks UTF-8 by byte and JS uses
`Number`/`Uint8Array`, so the agreement is a genuine divergence check. Validated
on all four targets. Proof (executed): `tests/run-golden.sh` **168/168** (42 cases
× 4 targets), exit 0.

**Phase 6 slice 18 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **43 total** — pinning the `kof.validation` namespace, a pure-Kof
validator surface (parity matrix ✅ on all four targets) that NO equivalence case
exercised: `validation-namespace` covers the BR documents
(`isCpf("52998224725")`=true but the all-equal `isCpf("11111111111")`=false — the
check-digit rule, `isCnpj("11222333000181")`=true, `isCep("01310-100")`=true), the
network faces (`isIpv4("192.168.0.1")`=true/`isIpv4("256.1.1.1")`=false,
`isIpv6("::1")`=true, `isMac("00:1A:2B:3C:4D:5E")`=true, the Int-arg
`isPort(8080)`=true/`isPort(99999)`=false, `isDomain("example.com")`=true), the Luhn
`isCreditCard("4242424242424242")`=true/`isCreditCard("1234567890123456")`=false, and
the LENIENT formatters that punctuate without validating
(`formatCpf("52998224725")`=`529.982.247-25`, `formatCep("01310100")`=`01310-100`,
`formatCnpj("11222333000181")`=`11.222.333/0001-81`). Validated on all four targets.
Proof (executed): `tests/run-golden.sh` **172/172** (43 cases × 4 targets), exit 0.

**Phase 6 slice 19 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **44 total** — pinning the `kof.time` calendar namespace, a pure-Kof
stdlib surface (parity ✅ on all four targets) that NO equivalence case exercised:
`time-namespace` covers the leap-year rule (`isLeapYear(2024)`=true, `2023`=false,
and the century rule `2000`=true/`1900`=false/`2100`=false), `daysInMonth(2024,2)`=29
vs `daysInMonth(2023,2)`=28, `dayOfWeek` (ISO 1=Mon..7=Sun: `2026-10-10`=6,
`2026-10-09`=5, `2000-01-01`=6), `isWeekend` (`2026-10-10`=true, `2026-10-09`=false —
the `dayOfWeek >= 6` wrapper), the serial `daysBetween` (`2026-01-01`→`2026-12-31`=364,
`2024-01-01`→`2024-03-01`=60, `2026`→`2027`=365), the ISO-string `diffDays`
(`"2026-01-01"`→`"2026-12-31"`=364), and the end-of-month / leap clamps of
`addDays("2026-01-31",1)`=`2026-02-01` / `addDays("2024-02-28",1)`=`2024-02-29`,
`addMonths("2026-01-31",1)`=`2026-02-28`, `addYears("2024-02-29",1)`=`2025-02-28`,
plus `formatDateIso(2026,10,10)`=`2026-10-10`. Test-infrastructure only, no compiler
change. Proof (executed): every value measured on the Script target first,
cross-checked against `docs/stdlib/README.md`/`KofTime`, then frozen;
`tests/run-golden.sh` **176/176** (44 cases × 4 targets: jvm/native/js/script), exit 0.

**Phase 6 slice 20 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **45 total** — pinning the `kof.uuid` namespace's deterministic surface,
`uuid.isUuid` (the shape validator; parity ✅ on all four targets), which NO equivalence
case exercised: `uuid-namespace` covers a canonical lowercase v4-shape
(`123e4567-e89b-12d3-a456-426614174000`=true) AND a v1-shape
(`6ba7b810-9dad-11d1-80b4-00c04fd430c8`=true — `isUuid` checks the SHAPE, not
version/variant), the all-zero UUID (`00000000-0000-0000-0000-000000000000`=true),
**uppercase hex (`123E4567-E89B-12D3-A456-426614174000`=true — measured on all four
targets before freezing)**, and the malformed set all false: 35 chars (one short),
37 chars (one long), no dashes (32 hex), a non-hex char (`g`), a misplaced dash,
dashes in the wrong positions (`1234-5678-1234-1234-1234567890ab`), the empty string,
and a trailing dash. **Scope note (measured):** `uuid.v4()`/`v7()` are NON-deterministic
(RFC 4122/9562) so they cannot be pinned by an equivalence case; `v4()` additionally does
NOT run under the golden `js` harness — `kof build --target js` + plain `node` throws
`kof_platform.randomBytesHex: not available outside the Kof JS host` (the JS uuid entropy
needs the Kof JS host, exercised by `KofUuidTest` not by golden). Test-infrastructure
only, no compiler change. Proof (executed): every value measured on the Script target
first, cross-checked on jvm/native/js (all four agree), then frozen; `tests/run-golden.sh`
**180/180** (45 cases × 4 targets), exit 0.

**Phase 6 slice 21 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **46 total** — pinning the REST of the `kof.time` calendar surface that slice
19 did not cover: `time-calendar-namespace` exercises `parseDateIso` (`"1970-01-01"`=0,
`"2026-01-01"`=20454, `"2026-10-10"`=20736, invalid/`"2026-13-01"`/`"2026-02-30"`=0),
`startOf`/`endOf` for `day`/`week`/`month`/`year` (`startOf("2026-10-10","month")`=`2026-10-01`,
`endOf(...)`=`2026-10-31`, week = Mon..Sun `startOf("2026-10-10","week")`=`2026-10-05`),
`age` (`1990-06-15`→`2026-10-10`=36, `1990-12-31`→=35, same-day=0) and `hoursBetween`
(`2026-01-01 00h`→`2026-01-02 12h`=36, reversed=-36). **This case is also a regression guard
for a real cross-target bug found and fixed in the same unit:** an UNKNOWN 5-character
`unit` (e.g. `"bogus"`) returned the start/end of the MONTH on the native targets instead of
the contract's `""` (jvm/script/js were correct). Root cause: the x86 asm
(`RuntimeTimeMonthIso.emitStartEndOf`) and the riscv64 asm (`NativeRiscvAsmRtB83`, shared
with aarch64 via the translator) dispatched on `len == 5` straight to the `month` branch
without checking the first byte was `'m'`; the len-4 branch already disambiguated
`week`/`year` by first byte, so only the 5-char face was unguarded (and the existing test
only used the 6-char `"decade"`, which fell into the unknown branch by luck). Fix = the
len-5 branch requires first byte `'m'` (`cmpb $109` x86 / `li t2,109; bne` riscv), else `""`.
Proof RED-first: `KofTimeE2ETest#timeStartEndOfNative` FAILED on the pre-fix tree with the
exact `2026-10-01` vs expected `""`; after the fix `timeStartEndOf{Native,CrossArch}` (x86-64
+ riscv64 + aarch64 under qemu) are 5/5, 0 skipped. Native asm only — no Kof semantics
change. Proof (executed): `tests/run-golden.sh` **184/184** (46 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; `check_500` rc=0.

**Phase 6 slice 22 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **47 total** — pinning the `math` numeric-parse family (`parseInt`/`parseLong`/
`parseDouble` + the `…OrDefault` fallbacks, S13a/b/c), a pure-Kof namespace surface (parity
✅ on all four targets) that slice 16 did NOT cover (it stopped at `roundTo`/`sqrt`/`lerp`):
`math-parse-namespace` exercises the JDK-with-trim contract — `parseInt("42")`=42, the TRIM
`parseInt(" 42 ")`=42, sign `parseInt("-7")`=-7 / `parseInt("+7")`=7, `parseLong("9000000000")`
=9000000000 (beyond Int), `parseDouble("3.14")`=3.14 / `parseDouble("1e3")`=1000.0
(scientific) / `parseDouble("-0.5")`=-0.5 — and the never-throw fallbacks:
`parseIntOrDefault("bad",-1)`=-1, `("42",-1)`=42, `("",-1)`=-1, `("  ",-1)`=-1, and the
OVERFLOW `("99999999999999",-1)`=-1 (falls back to the default, no wrap);
`parseLongOrDefault("bad",-9)`=-9 / `("9000000000",-9)`=9000000000;
`parseDoubleOrDefault("bad",-2.5)`=-2.5 / `("2.5",-2.5)`=2.5. **Scope note (measured,
honest):** `math.pi()`/`math.e()`/`math.tau()` and `toRadians`/`toDegrees` are the §621
family — `toRadians`/`toDegrees` refuse honestly on native (`MATH001`) and `pi()`/`e()` died
at native link (`COMP001`, no `kof_math_pi` symbol), so at this slice's cut they could not be
pinned by a 4-target equivalence case; the golden pins only the `parse*` family (4-target clean).
**Corrected 11/10:** `known-bugs` §651 (lane compiler/JVM/native) fixed the three constants —
the lowering now folds `pi()`/`e()`/`tau()` to a `KofLoadLiteral.ofDouble`, so they bind on
every target (no `kof_math_pi` symbol needed) and CAN be pinned by a 4-target case;
`toRadians`/`toDegrees` still refuse honestly on native (`MATH001`).
Test-infrastructure only, no compiler change. Proof (executed): every value measured on the
Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then
frozen; `tests/run-golden.sh` **188/188** (47 cases × 4 targets), exit 0.

**Phase 6 slice 23 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **48 total** — pinning the rest of the `kof.strings` escaping+indentation
family (S3.1/S3.3) that slice 15 did NOT cover (it stopped at `escapeHtml`):
`strings-escape-indent` pins `escapeJson` (`a\"b\\c`→`a\"b\\c`, a real newline→`\n`, a
tab→`\t`, `plain`→`plain`, `""`→``), `unescapeHtml` (named `&lt;a&gt;&amp;`→`<a>&`,
numeric `&#65;&#66;`→`AB`, no-op `plain`), `indent` (`"a\nb",2`→`  a\n  b`; `"a\n\nb",2`
→`  a\n\n  b` — the EMPTY line is NOT indented; `n=0` = identity), `dedent`
(`"  a\n  b"`→`a\nb`; `"a\n  b"` unchanged — min common indent 0), and `padRight`
(`"7",3,"0"`→`700`). All four targets agree byte-for-byte. **Scope note (measured,
honest):** `kof.strings` `capitalize`/`reverse`/`pad*` are Unicode/byte-divergent on
native (the NAT-STR01 gap, ASCII-only pinned) — this case uses only ASCII inputs, so it
stays 4-target clean. Test-infrastructure only, no compiler change. Proof (executed):
every value measured on the Script target first, cross-checked byte-for-byte on
jvm/native/js (all four agree), then frozen; `tests/run-golden.sh` **192/192** (48 cases
× 4 targets), exit 0.

**Phase 6 slice 24 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **49 total** — pinning `kof.rng`, the seedable xorshift128 PRNG (X8) that was
pinned by NO equivalence case. It is the STRONGEST cross-target determinism pin in the
stdlib: the contract is "same seed → same sequence on ANY backend" (only xor/shift/mul
mod 2^32 + a splitmix32 seed), so jvm/native-x86_64/js/script MUST agree byte-for-byte.
`rng-namespace` pins `rng.seed(42)` then `rng.int(100)`=67/90/54, `rng.boolean()`
=true/false, `rng.double()`=0.07631878219634403 (52-bit mantissa, exact IEEE-754 in both
backends), `rng.string(8,"abc")`=acbaccab, and the lenient edges `rng.int(0)`=0,
`rng.int(-5)`=0 (bound<=0 => 0), `rng.string(0,"abc")`="" and `rng.string(5,"")`="" (n<=0
or empty alphabet => ""). **Scope note (measured, honest):** `kof.rng` is `RNG001` on the
CROSS arches riscv64/aarch64 (`KofRng.supportedOn`) — the golden `native` target runs on
the x86_64 host, so the case is 4-target clean here; the cross arches stay owned by the
RNG001 gap. Test-infrastructure only, no compiler change. Proof (executed): the sequence
measured TWICE on the Script target (byte-identical → determinism confirmed),
cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **196/196** (49 cases × 4 targets), exit 0.

**Phase 6 slice 25 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **50 total** — pinning the remaining deterministic `kof.validation`
predicates: `validation-predicates` pins `isEmail` (`a@b.com`→true, `bad`→false), `isUrl`
(`https://kof.dev`/`http://a.b`/`https://example.com/path?q=1`→true, `ftp://x`→false),
`isInt` (`42`→true, `4.2`→false), `isLong("9000000000")`→true, `inRange` (5 in 1..10→true,
11→false), `required` (`x`→true, `""`→false), `notBlank` (`x`→true, `"   "`→false),
`minLength`/`maxLength`/`lengthBetween`, `creditCardBrand` (`4242…`→Visa, `5555…`→
Mastercard), `last4`→`4242`, and `max`(5,10)→true / `min`(5,10)→false. **This case found a
REAL x86-only native parity bug:** the x86 asm `kof_validation_isUrl` tested `byte4==':'`
with `jne .Lv_url_false` BEFORE the https branch was reachable, so `https://…` (byte4='s')
and every URL with a path/query fell straight to false; the riscv asm was already correct.
Fixed in `RuntimeValidation.java` (the second `jne` retargeted to `.Lv_url_check_https`,
the https byte4 test to `.Lv_url_false`), guarded RED-first by the new
`KofValidationUrlParityTest` (RED pre-fix → GREEN 35/35 across both validation classes).
**Scope note (measured, honest):** `validation.matches` is deliberately EXCLUDED — its
JVM/JS/Script regex vs Native literal-substring divergence is a known, documented issue
frozen in `development/future/kof-expr-plan.md` Q8 awaiting the `D-KOF-EXPR` maintainer decision
(`future/` is now UNFROZEN, `D-FUTURE-UNFREEZE` 11/10, but the `D-KOF-EXPR` decision is still pending). Proof (executed): every value measured on the Script target first,
cross-checked byte-for-byte on jvm/native/js, then frozen; `tests/run-golden.sh`
**200/200** (50 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am compile` rc=0;
gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang`
rc=0.

**Phase 6 slice 26 LANDED (10/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **51 total** — pinning the URL-safe base64 face of `kof.encoding`.
`encoding.base64UrlEncode`/`base64UrlDecode` (RFC 4648 §5, the `-_` alphabet, NO padding on
encode) are in the public API but were pinned by NO equivalence case — slice 17's
`encoding-namespace` covers `hexEncode`/`hexDecode`/`base64Encode`/`base64Decode`/
`urlEncode`/`urlDecode` but not the `base64Url*` pair, leaving the only uncovered
`kof.encoding` surface. The new case `tests/golden/encoding-base64url` pins
`base64UrlEncode` (`"Hi"`→`SGk`, `"fb&O->f"`→`ZmImTy0-Zg`, `"zÿ"`→`esO_` — the `_` is the
URL-safe stand-in for the standard `/+` alphabet, `"café"`→`Y2Fmw6k`, `""`→`` empty) and
`base64UrlDecode` (`"SGk"`→`Hi`, `"ZmImTy0-Zg"`→`fb&O->f`, `"esO_"`→`zÿ`, the TOLERANT
`"SGk="`→`Hi` padded form, `"Y2Fmw6k"`→`café` — non-ASCII round-trip through UTF-8 bytes).
Test-infrastructure only, no compiler change. Proof (executed): every value measured on the
Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then
frozen; `tests/run-golden.sh` **204/204** (51 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates `check_test_hygiene`/
`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 27 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **52 total** — pinning the deterministic core of `kof.observability`, a
namespace pinned by NO equivalence case until now. The new case
`tests/golden/observability-namespace` pins `health()`→`UP`, `readiness()`→true,
`liveness()`→true, the metrics store `counter("hits")`→1 then (after `increment("hits",3)`)
→5, `gauge("temp",42)` + `histogram("latency",7)`/`histogram("latency",13)`, and the
Prometheus `metrics()` render (the `# TYPE … counter`/`gauge` lines with `hits 5`,
`temp 42`, `latency_count 2`, `latency_sum 20`). All four targets agree byte-for-byte.
**Scope note (measured, honest):** two faces are deliberately EXCLUDED because they cannot
be a 4-target pin — (a) `exportSpans` is a documented native gap (`OBS003`: the OTLP/JSON
span serialization exists on JVM/JS only, a clean refusal on native, never a stub), and
(b) `spanStart`/`spanEnd` plus the id generators `requestId`/`correlationId`/`traceId`/
`spanId` are NON-deterministic (a fresh random value per run, and `spanEnd` needs the
`spanStart` handle). Test-infrastructure only, no compiler change. Proof (executed): every
value measured on the Script target first, cross-checked byte-for-byte on jvm/native/js
(all four agree), then frozen; `tests/run-golden.sh` **208/208** (52 cases × 4 targets),
exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates `check_test_hygiene`/
`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 28 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **53 total** — pinning the deterministic digest and constant-time faces of the
security namespaces (`kof.crypto` + `kof.security`), pinned by NO equivalence case until
now. The new case `tests/golden/security-crypto-namespace` pins `crypto.sha256("abc")`→
`ba7816bf…20015ad` (the NIST FIPS 180-4 vector), `crypto.sha256("")`→`e3b0c442…b855` (the
empty-string SHA-256), `crypto.sha512("abc")`→`ddaf35a1…ca49f` (NIST), `crypto.hmacSha256
("key","msg")`→`2d93cbc1…b8c628` (RFC 4231-style HMAC-SHA256), `security.constantTimeEquals`
(`"abc"`/`"abc"`→true, `"abc"`/`"abd"`→false, `""`/`""`→true), and `security.redact`
(`"a"`→`********` all-masked, `"verylongsecret"`→`very********cret` — first 4 + last 4
kept). All four targets agree byte-for-byte. **Scope note (measured, honest):** the
`security.*Header` family (`cspHeader`/`hstsHeader`/`contentTypeOptionsHeader`/`frameHeader`/
`referrerHeader`) is a documented JVM-only gap (`SECN000`: it refuses on native/JS) so it
is EXCLUDED — it cannot be a 4-target pin; `security.csrfToken`/`apiKeyGenerate`/`randomHex`
are NON-deterministic. Test-infrastructure only, no compiler change. Proof (executed): every
value measured on the Script target first, cross-checked byte-for-byte on jvm/native/js
(all four agree), then frozen; `tests/run-golden.sh` **212/212** (53 cases × 4 targets),
exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates `check_test_hygiene`/
`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 29 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **54 total** — pinning the last uncovered deterministic members of `kof.math`:
the `pow` face (S1b.2) and the three constants `pi()`/`e()`/`tau()`. The new case
`tests/golden/math-pow-constants` pins `math.pow(2.0,10.0)`→1024.0, `math.pow(3.0,2.0)`→9.0,
`math.pow(9.0,0.5)`→3.0, `math.pow(4.0,0.5)`→2.0, `math.pow(2.0,20.0)`→1048576.0,
`math.pow(10.0,3.0)`→1000.0, `math.pow(5.0,1.0)`→5.0, the identity `math.pow(2.0,0.0)`→1.0,
`math.pow(0.0,0.0)`→1.0 (libm `pow` convention, matching `Math.pow`), `math.pow(2.0,-1.0)`→0.5,
the negative base with an odd exponent `math.pow(-2.0,3.0)`→-8.0, and the constants
`math.pi()`→3.141592653589793, `math.e()`→2.718281828459045, `math.tau()`→6.283185307179586,
plus the identities `math.tau() == 2.0 * math.pi()`→true, `math.pi() > math.e()`→true,
`math.e() > math.tau()`→false. All four targets agree byte-for-byte. **Scope note (measured,
honest):** the pinned `pow` values are EXACTLY representable (small integer powers of 2/3/10
and the exact `sqrt` of 4/9), so they are identical across any correct libm — the x86-64
native links `pow@PLT` by use while JVM/JS/Script use `Math.pow`/`**`; a non-exact `pow`
(e.g. `2.0^0.5`) was deliberately NOT frozen because libm and `Math.pow` may differ by 1 ulp.
The three constants are 4-target clean since `known-bugs` §651 folds them to a
`KofLoadLiteral.ofDouble` (this lane) — before that fix `pi()`/`e()` died at the native link
(`COMP001`) and could not be pinned. `toRadians`/`toDegrees` + the nine trig functions stay
EXCLUDED (they refuse honestly on native, `MATH001`, so cannot be a 4-target pin).
Test-infrastructure only, no compiler change. Proof (executed): every value measured on the
Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then
frozen; `tests/run-golden.sh` **216/216** (54 cases × 4 targets), exit 0; `mvn -o -pl
kof-compiler -am compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/
`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 30 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **55 total** — pinning the deterministic core of `kof.cache`, a namespace pinned
by NO equivalence case until now. The new case `tests/golden/cache-namespace` pins the
round-trip `cache.set("name","Mel")`/`cache.get("name")`→`Mel`, the miss `cache.get("missing")`
→`null`, overwrite `cache.set("k","v1")`/`cache.set("k","v2")`→`v2`, `cache.delete` (the
deleted key reads `null`, the sibling survives), `cache.clear` (the whole store reads `null`),
the no-TTL boundary `cache.ttl("noexp")`→`-1`, the missing-key `cache.ttl("missing")`→`-1`,
and the idempotence of `cache.delete`/`cache.clear` on absent keys (no error, no change). All
four targets agree byte-for-byte. **Scope note (measured, honest):** the TTL-EXPIRY face
(`cache.set("t","x",1)` + `time.sleep(1300)` → `null`) is deliberately EXCLUDED — it depends
on wall-clock timing and cannot be a deterministic 4-target pin; the existing unit test
`KofCacheE2ETest.ttlValueAndExpiry` already covers it. `kof.cache` is 4-target clean
(`KofCache.supportedOn` = true on every target). Test-infrastructure only, no compiler
change. Proof (executed): every value measured on the Script target first, cross-checked
byte-for-byte on jvm/native/js (all four agree), then frozen; `tests/run-golden.sh` **220/220**
(55 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 31 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **56 total** — pinning the deterministic core of `kof.passwords`, a namespace
pinned by NO equivalence case until now. The new case `tests/golden/passwords-namespace`
pins the `verify` face (`passwords.verify("hunter2", h1)`→`true`, the wrong password
`passwords.verify("wrong", h1)`→`false`, the 600 000-iteration hash
`passwords.verify("hunter2", h6)`→`true`, and the malformed `passwords.verify("hunter2",
"not-a-hash")`→`false`) and the `needsRehash` face (`passwords.needsRehash(h1)`→`true` for a
1000-iteration hash, `passwords.needsRehash(h6)`→`false` for a 600 000-iteration hash, and
`passwords.needsRehash("not-a-hash")`→`true`). The hashes are FIXED
`pbkdf2$sha256$<iters>$<salt>$<dk>` strings (salt `00112233445566778899aabbccddeeff`, the
`"hunter2"` derived key frozen at each iteration count), so the pin is deterministic. All
four targets agree byte-for-byte. **Scope note (measured, honest):** `passwords.hash` is
deliberately EXCLUDED — it draws a fresh random salt per call and cannot be a deterministic
4-target pin; `passwords.verify`/`needsRehash` are 4-target clean (`KofSecurity.supportedOn`
accepts `jvmLike || JS || isNative`), and this slice also guards the §653 JS `verify` fix
end-to-end. Test-infrastructure only, no compiler change. Proof (executed): every value
measured on the Script target first, cross-checked byte-for-byte on jvm/native/js (all four
agree), then frozen; `tests/run-golden.sh` **224/224** (56 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 32 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **57 total** — pinning the deterministic core of `kof.jwt`, a namespace pinned
by NO equivalence case until now. The new case `tests/golden/jwt-namespace` pins `jwt.verify`
against FIXED HS256 tokens (built offline with the pinned secret `s3cret`): the valid token
returns its payload JSON (`jwt.verify(token, "s3cret")`→`{"sub":"u1","iss":"kof","aud":"api",
"exp":4102444800}`), a WRONG secret is rejected, the `iss`/`aud` bound verify returns the
payload, a wrong `iss` and a wrong `aud` are rejected, a malformed token (`"not-a-token"`) is
rejected, an EXPIRED token is rejected, and an `alg=none` algorithm-confusion token is
rejected. All four targets agree byte-for-byte. **Scope note (measured, honest):**
`jwt.create` is deliberately EXCLUDED — it injects `iat`/`exp` from the wall clock, so the
token string is NOT a deterministic 4-target pin; `jwt.verify` of a FIXED token is fully
deterministic (the expiry check reads the token's own `exp`, `4102444800` = year 2100).
`jwt.verify` is 4-target clean (`KofSecurity.supportedOn` accepts `jvmLike || JS || isNative`;
the cross arches riscv64/aarch64 are the `SECN004`/`SECN000` gap, so the golden `native`
target runs on x86_64 here). This slice also guards the algorithm-confusion rejection
end-to-end. Test-infrastructure only, no compiler change. Proof (executed): every value
measured on the Script target first, cross-checked byte-for-byte on jvm/native/js (all four
agree), then frozen; `tests/run-golden.sh` **228/228** (57 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`docs-lang` rc=0.

**Phase 6 slice 33 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **58 total** — pinning the core `String` member-method surface that had almost
NO golden coverage. The existing `strings-methods`/`string-parts-valueof` cases cover only
trim/length/substring(1-arg)/startsWith(1-arg)/endsWith/indexOf(1-arg)/toUpperCase/
toLowerCase/charAt/equals/split(1-arg)/toCharArray/equalsIgnoreCase/valueOf. The new case
`tests/golden/string-methods-extended` pins `isEmpty` (""→true, "x"→false), `contains`
(hit/miss), `lastIndexOf` (1-arg hit/miss + the 2-arg `fromIndex` clamp `lastIndexOf("l",0)`
→-1), `concat`, `replace(String,String)` + `replace(Char,Char)`, `substring(1,3)`,
`startsWith(str, fromIndex)` (hit + miss), `indexOf(str, fromIndex)`, `compareTo` (−1/0),
`compareToIgnoreCase` (0), and the numeric conversions `toInt`/`toLong`/`toDouble`. All 23
faces agree byte-for-byte on all four targets. **Scope note (measured, honest):** `getBytes()`
is EXCLUDED — a JVM-only `STR003` gap (refused on native AND js at compile time);
`matches`/`replaceAll`/`replaceFirst` are EXCLUDED (the regex JVM/JS/Script vs Native-literal
divergence frozen in `development/future/kof-expr-plan.md` Q8). **BUG FOUND + CATALOGUED (Q4):** the
2-arg `String.split(String, Int)` — accepted by `StringMethodRegistry`, correct on JVM/Script
— is BROKEN on two targets: **JS ignores the limit** (`"a,b,c".split(",", 2)` → 3 parts
instead of 2) and **native x86-64 SIGSEGVs** (exit 139; `NativeX86StringCalls` pops only one
arg for split, so the `limit` operand corrupts the stack). Recorded as `known-bugs` **§655**
EN+PT with the measured 4-target repro; NOT fixed here (the fix spans native x86 asm +
riscv/aarch + the JS `kofSplit` runtime — a dedicated cross-backend unit) — ✅ FIXED 11/10
across all four targets by lane flatt `192.168.100.14:0` (`StringSplitLimitCrossTest`
6 run / 0F / 2 named NATIVE002 skips, byte-for-byte on JVM + Script + JS + x86-64). Test-infrastructure
only, no compiler change. Proof (executed): every value measured on the Script target first,
cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **232/232** (58 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/
`docs-lang`/`check_known_bugs_status`/`check_ledger_anchors` rc=0.

**Phase 6 slice 34 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **59 total** — pinning the deterministic core of `kof.config`, a whole stdlib
namespace with ZERO golden coverage until now. The new case `tests/golden/config-namespace`
pins `config.get` on a missing key (→`null`), `config.has` (false), `config.str(key, "demo")`
→`demo` + the empty-string default, `config.int(key, 8080)`/`(key, 2147483647)`/`(key, -1)`,
`config.long(key, 9000000000)`/`(key, 9223372036854775807)`, `config.bool(key, false)`/
`(key, true)`, and `config.get("k") == null`→true. All 14 faces agree byte-for-byte on all four
targets. **Scope note (measured, honest):** `config.required` on a missing key THROWS, but with
a host-specific unhandled-exception rendering (JVM stack trace / JS `Error` / native `CONF001`),
so it is EXCLUDED — it cannot be a 4-target pin. **BUG FOUND + CATALOGUED (Q4):** authoring the
case with the decimal `Int.MIN_VALUE` default `-2147483648` made JVM emit `VerifyError` (masked
by the JDK launcher as the never-benign "JavaFX runtime components not found") and JS `TypeError:
Cannot mix BigInt and other types`, while Script/native truncate to `-2147483648`. Root: a `Long`
value is ACCEPTED where a builtin signature declares an `Int` parameter (no `SEM014`), unlike a
user function which correctly refuses it (`f(5000000000)` → `expected 'Int' but got 'Long'
[SEM014]`); `strings.repeat("ab", 5000000000)` is likewise accepted. Recorded as `known-bugs`
**§657** EN+PT; NOT repaired here (it touches the builtin call-lowering shared by every
namespace — a dedicated unit). The golden case pins only the 4-target-clean `Int` faces.
Test-infrastructure only, no compiler change. Proof (executed): every value measured on the
Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **236/236** (59 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/
`docs-lang`/`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records` rc=0.

**Phase 6 slice 35 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **60 total** — pinning the record faces of `json.encode`/`json.decode`, a
core-language area the existing `json` case covered only trivially (3 faces: `encode(List<Int>)`,
`decode<List<Int>>`, `encode(true)`; NO record encode/decode at all). The new case
`tests/golden/json-records` pins `json.encode(Point(3,4))`→`{"x":3,"y":4}`, a `String`+`Int`
record (`User("ada",36)`→`{"name":"ada","age":36}`), `json.decode<Point>` round-trip (→15),
`json.decode<User>` round-trip (→`grace/45`), `json.encode(List<Point>)`, `json.encode(List<String>)`,
`json.encode(Flag(true))`→`{"ok":true}`, `json.encode(List<Flag>)`, a record with an escaped-string
field (`"a\"b\nc"`), whitespace-tolerant decode (`"  {  \"x\" : 5 , \"y\" : 6 }  "`→11), and
extra-key-tolerant decode (unknown `z` ignored→3). All 11 faces agree byte-for-byte on all four
targets. **Scope note (measured, honest):** `Long`/`Double`/`Float` record fields are EXCLUDED —
they cannot be a 4-target pin until §659b/§659c are repaired (see below). **BUG FOUND + FIXED
(Q4/Q0):** authoring the case surfaced **§659** — `json.encode` of a record diverges across
targets by field type: (§659a) a `Bool` field emitted `1`/`0` on Script vs `true`/`false` on
JVM/JS/native, (§659b) a `Long` field crashes JS (`TypeError: Do not know how to serialize a
BigInt`), (§659c) a `Double`/`Float` field emits garbage on native (`{"d":2.0546950501119882E-81}`
— `CompilerTypeSupport.fieldOk` accepts every primitive while `nativeRecordWalkerOk` correctly
refuses `float`/`double` with `JSN002`, so the same record is a compile error via `encode(List<T>)`
but silent garbage via `encode(obj)`). **§659a is FIXED here** — the single-unit, low-risk face:
`KofInterpreterRuntime.encodeKof` now consults the declared field type and routes a `bool` field
through `kof_json_encode_bool` (the interpreter stores `Bool` as `Integer` 0/1, §108, and the raw
slot reached the encoder unnormalized). The native face **§659c was FIXED the same day** (`ExpressionJsonCallLowerer` now uses the scalar path's ABI-registered `kof_json_encode_double`/`_float`; native `json.encode(R(2.5,1.5))` → `{"d":2.5,"f":1.5}` ≡ JVM). §659b (JS `Long` field → `TypeError`) and §659d (JS drops a whole `Double`'s `.0`, scalar and field alike) stay OPEN (JS-backend units).
Proof (executed): every value measured on the Script target first, cross-checked byte-for-byte on
jvm/native/js (all four agree), then frozen; the §659a divergence measured pre-fix on all four
targets (Script `{"ok":1}` vs the other three `{"ok":true}`) and GREEN post-fix; regression
`JsonE2ETest`/`JsonCompleteE2ETest`/`JsonNativeRecordListE2ETest`/`JsonMissingPrimitiveE2ETest`/
`KofInterpreterParityTest` 65 run / 0F, plus the new `jsonEncodeRecordBoolField` parity test;
`tests/run-golden.sh` **240/240** (60 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/
`docs-lang`/`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records` rc=0.

**Phase 6 slice 36 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **61 total** — pinning `json.encode(Map<K,V>)`, the §106 "chaves SORTED"
decision (determinism > insertion order) that no golden case covered. The new case
`tests/golden/json-maps` pins `json.encode(mapOf("b",2,"a",1,"c",3))`→`{"a":1,"b":2,"c":3}`
(the sorted-key face), a `Map<String,String>` (`{"y":"lo","z":"hi"}`), a
`Map<String,Record>` (`{"k":{"x":3,"y":4}}` — nested record via the map walker), a
single-entry map, a `Map<String,Bool>`, and the empty-key face (`{"":0}`). All 6 faces agree
byte-for-byte on all four targets. **Scope note (measured, honest):** `json.encode` of a
`Map<String,Long>` is EXCLUDED — it hits the OPEN §659b (JS crashes on `BigInt`);
`Map<String,Double>` is excluded by the §659d JS whole-number divergence (the same reason the
`json-records` slice excluded its FP faces); `List<Map<…>>` and `decode<Map<String,T>>` are
EXCLUDED — both are refused on native (`JSN002` nested-collection-element, `JSN004` Map
decode). Test-infrastructure only, no compiler change. Proof (executed): every value measured
on the Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then
frozen; `tests/run-golden.sh` **244/244** (61 cases × 4 targets), exit 0; `mvn -o -pl
kof-compiler -am compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/
`check_500`/`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records`/
`check_changelog_ledger` rc=0.

**Phase 6 slice 37 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **62 total** — pinning the JSON string-ESCAPING faces of `json.encode(String)`
and the non-escaped decode faces. The new case `tests/golden/json-escaping` pins
`json.encode("tab\there")`, `"new\nline"`, `"cr\rhere"`, `"quote\"and\\back"`,
`"slash/forward"`, `"café"` (non-ASCII passthrough), `""` (empty), plus `json.decode<List<String>>`
and `json.decode<String>("\"hello\"")` (no escapes). All 10 faces agree byte-for-byte on all four
targets. **BUG FOUND + CATALOGUED (Q4):** authoring the case surfaced **§667** — `json.decode<String>`
does NOT honour JSON string escapes consistently: **JVM/Script** return `x\ty` VERBATIM (length 4,
no unescaping — `JvmRuntimeJson.kof_json_decode_string` only strips the outer quotes, and the
interpreter delegates to that same method), **native** unescapes `\t`/`\n`/`\r`/`\"`/`\\` but
NOT `\uXXXX` (`json.decode<String>("\"\\u0041\"")` → `u0041`), and **JS** is correct
(`JSON.parse`). The correct unescape already exists in the tree (`JsonParser.parseString`), so the
fix is to route the top-level entry point through it + add the `\u` arm to the native asm — a
cross-backend unit recorded OPEN, NOT landed here. The golden case pins only the 4-target-clean
faces. Test-infrastructure only, no compiler change. Proof (executed): every value measured on the
Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **248/248** (62 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/
`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger` rc=0.

**Phase 6 slice 38 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **63 total** — pinning the `List` higher-order PREDICATE/TRANSFORM methods that the
existing `list-higher-order` case left uncovered (it covers `reduce`/`indexOf`/`lastIndexOf`/
`isEmpty`/`none`/`find`/`slice`/`take`/`drop`/`groupBy`/`flatMap`/`sort`/`addAll`/`subList`/
`remove`/`sorted`/`forEach`). The new case `tests/golden/list-predicates` pins `any` (hit→true,
miss→false), `all` (hit→true, miss→false), `distinct` (size + element), `map` (transformed element
+ size), `filter` (size + element), and the empty-list vacuous faces (`empty.any`→false,
`empty.all`→true). All 12 faces agree byte-for-byte on all four targets. **Scope note (measured,
honest):** `List.zip` is EXCLUDED — it is refused on native with `NAT008` (a primitive element
would cross a bare type parameter and be unboxed as a pointer), so it cannot be a 4-target pin.
Test-infrastructure only, no compiler change. Proof (executed): every value measured on the Script
target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **252/252** (63 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/
`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger` rc=0.

**Phase 6 slice 39 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **64 total** — pinning ABSTRACT classes and RECORD member methods, two core-language
areas with ZERO golden coverage (the `classes` case covers plain inheritance+override, the
`records` case covers record fields, but neither an `abstract` declaration nor a method declared
INSIDE a `record`). The new case `tests/golden/abstract-records` pins an `abstract class Shape`
with an abstract method `area()` and a concrete method `label()`, two subclasses (`Sq` overriding
both, `Rect` overriding only `area()`), polymorphic dispatch through `List<Shape>` (`16`/`15`),
the concrete-vs-overridden `label()` face (`square`/`shape`), a `record Pt` with TWO member
methods (`sum()`, `scaled(Int)` returning a new record), and record structural equality
(`Pt(3,4) == Pt(3,4)`→true, `== Pt(3,5)`→false). All 9 faces agree byte-for-byte on all four
targets. Test-infrastructure only, no compiler change. Proof (executed): every value measured on
the Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then
frozen; `tests/run-golden.sh` **256/256** (64 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler
-am compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/
`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger` rc=0.

**Phase 6 slice 40 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **65 total** — pinning the `enum` member-method surface the existing `enum` case left
uncovered (it covers only `name()` and `values()`). The new case `tests/golden/enum-methods` pins
`ordinal()` (`RED`→0, `BLUE`→2), `name()`, `values()` (size + element), identity `==`/`!=` between
constants, and `compareTo` (negative/positive/zero). All 12 faces agree byte-for-byte on all four
targets. **BUGS FOUND + CATALOGUED (Q4):** authoring the case surfaced TWO real defects, both
recorded OPEN: **§674** — the native `enum.valueOf(String)` returns a value that SIGSEGVs (139) on
ANY use (`println(Color.valueOf("GREEN"))` crashes; a program that only calls `valueOf` prints
nothing), while JVM/Script/JS return the constant; **§675** — the `enum` ordering operators
(`<` `>` `<=` `>=`) are ACCEPTED but diverge (JVM/JS `false`, native `true`, Script
`not comparable`), and the contract is silent on enum ordering (it lists `compareTo`), so the fix
needs a maintainer decision (reject with a diagnostic vs define ordinal semantics). Both are
EXCLUDED from the golden case (it pins only the 4-target-clean faces). Test-infrastructure only,
no compiler change. Proof (executed): every value measured on the Script target first,
cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen; `tests/run-golden.sh`
**260/260** (65 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger` rc=0.

**Phase 6 slice 41 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **66 total** — pinning `switch` on a **`String`** subject and **default parameter
values**, two core-language surfaces with no golden coverage (the `switch-expr` case uses an `Int`
subject; `sealed-switch` uses sealed-class subjects; NO case exercised a default parameter). The new
case `tests/golden/string-switch-defaults` pins a `switch` EXPRESSION on a String (three literal
cases + a `default` that reads a parameter, `mon`/`wed`/`zzz`→`Monday`/`Wednesday`/`unknown`), the
explicit-fallback face (`day("tue", "N/A")`→`Tuesday`), a `switch` STATEMENT on a String (with a
non-matching arm falling to `default`), and default parameters (`greet("Ada")`→`Hi Ada!`,
`greet("Bob", "Hello")`→`Hello Bob!`, `greet("Cy", "Yo", "?")`→`Yo Cy?` — one, two and three
arguments). All 8 faces agree byte-for-byte on all four targets. Test-infrastructure only, no
compiler change. Proof (executed): every value measured on the Script target first, cross-checked
byte-for-byte on jvm/native/js (all four agree), then frozen; `tests/run-golden.sh` **264/264**
(66 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger` rc=0.

**Phase 6 slice 42 LANDED (11/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **67 total** — pinning reference `instanceof` type-tests and reference `as` casts, a
core-language area with no golden coverage (the four `as` uses in the suite are all NUMERIC casts:
`d as Int`, `x as Double`, `charAt(1) as Int`, `300 as Byte`). The new case
`tests/golden/type-check-casts` pins `instanceof` on a superclass/subclass pair (`a instanceof Dog`/
`Cat`/`Animal`), `instanceof String` on an `Object` slot, a successful `as String` cast (then
`.length`), a successful `as Dog` downcast (then a call), and an `instanceof`-guarded loop over a
`List<Animal>` dispatching per runtime kind. All 8 faces agree byte-for-byte on all four targets.
**BUGS FOUND + CATALOGUED (Q4):** authoring the case surfaced TWO divergences, both OPEN:
**§677** — `instanceof` on a PRIMITIVE target (`o instanceof Int` on a boxed `Object`) is `false` on
Script/native but `true` on JVM/JS; **§678** — a FAILING downcast (`a as Dog` where `a` is an
`Animal`) throws `ClassCastException` on JVM but evaluates to `null` on Script/native/JS. Both are
contract-SILENT (the language reference defines `instanceof` only for class/record types and does
not state the failing-cast behaviour), so each needs a maintainer decision; both are EXCLUDED from
the golden case (it pins only the 4-target-clean faces). Test-infrastructure only, no compiler
change. Proof (executed): every value measured on the Script target first, cross-checked
byte-for-byte on jvm/native/js (all four agree), then frozen; `tests/run-golden.sh` **268/268**
(67 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger` rc=0.

**Phase 6 slice 43 LANDED (12/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **68 total** — pinning the deterministic lenient edges of the `kof.random`
namespace, which had no golden coverage. The new case `tests/golden/random-namespace` pins
`random.randomInt(0)`/`(-5)`→`0` and `(1)`→`0`, `random.randomString(0,"abc")`/`(-3,"ab")`→`""`
and `(5,"")`→`""`, `random.hex(0)`/`(-1)`/`(-100)`→`null` and the `hex(4)`/`(1)`/`(16)` lengths
`8`/`2`/`32`, plus PREDICATES for the genuinely random draws (`randomInt(1000)` in range,
`randomString(6,"ab")` length) — never a value, so the case carries no entropy/clock byte. All
16 faces agree byte-for-byte on the four targets. **BUG FOUND + FIXED (Q0/Q4):** authoring the
case surfaced **§681** — `random.hex(n)` with `n <= 0` returned ~2 GiB of binary garbage on
native x86-64 instead of the contract's `null` (`kof_random_hex` was a bare
`jmp kof_sec_random_hex`, whose only callers pass a literal 16/32; `n = 0` allocated the header
and the SIGNED hex loop index `n-1 = -1` scanned the address space downward). FIXED the same
commit at the `random.hex` face wrapper, with a RED-first test
(`RandomHexNonPositiveNativeTest` **1/7 FAIL pre-fix** → **7 run / 0F** post-fix on JVM + Script
+ JS + native x86-64 + riscv64 + aarch64). This is the THIRD slice that carries a compiler fix
(the others were test-only). Proof (executed): every value measured on the Script target first,
cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **272/272** (68 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/`check_golden_count` rc=0.

**Phase 6 slice 44 LANDED (12/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **69 total** — pinning SIGNED integer arithmetic, a core-language area with
ZERO golden coverage. The new case `tests/golden/signed-division-overflow` pins the JVM
contract's truncation TOWARD ZERO for `Int`/`Long` division and remainder (`-7/2`→`-3`,
`-7%2`→`-1`, `7/-2`→`-3`, `7%-2`→`1`, `-1/2`→`0`, `-1%2`→`-1`, `-8/2`, `-8%3`, `8%-3`,
`-9/4`, `-9%4`), the `Int` 32-bit wraparound on overflow (`2147483647+1`→`-2147483648`,
`big*2`→`-2`, `big+big`→`-2`), and the Long signed bitwise/shift faces (`a>>1` arithmetic
on a negative → `-4500000000`, `a<<2`, `a&255`, `a|1`, `a^3`, `-1>>1`→`-1`). Every
EXISTING arithmetic golden uses only POSITIVE operands (`long-arithmetic` `a/b`/`a%b`/
`d/2`; `bitwise-ops` `16>>2`/`1<<4`), so the truncation-toward-zero rule, the negative-shift
sign extension and the overflow wrap had no pin. All 24 faces agree byte-for-byte on the
four targets. Test-infrastructure only, no compiler change. Proof (executed): every value
measured on the Script target first, cross-checked byte-for-byte on jvm/native/js (all four
agree), then frozen; `tests/run-golden.sh` **276/276** (69 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/`check_golden_count` rc=0.

**Phase 6 slice 45 LANDED (12/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **70 total** — pinning `String` concatenation of a collection, which had no
golden coverage. The new case `tests/golden/collection-concat` pins `"L=" + listOf(1,2,3)`
→ `L=[1, 2, 3]`, a `Set` → `[a, b]`, a `Map` → `{k=1}`, a nested list → `[[1], [2]]`, an
EMPTY list → `[]`, the bare `"" + list` form, a record concat, and a mixed
`"mix=" + l.size + "/" + s.size`. All 9 faces agree byte-for-byte on the four targets.
**BUG FOUND + FIXED (Q0/Q4):** authoring the case surfaced **§685** — on the JS target the
concat used the HOST's `Array.prototype.toString`/`[object Set]`/`[object Map]`
(`L=1,2,3`) instead of the Kof container form: the `+` desugar boxes the operand to
`Object` (the JVM `valueOf(Object)` signature) and the JS `valueOf` fell to a bare
`String(x)`, its container branches never matching the erased `Object` type. FIXED the same
commit by routing the erased-`Object` case through `kofFormat` (the Kof container
formatter, identical to `String(x)` for scalars/String/records), with a RED-first test
(`CollectionConcatJsE2ETest` **1/2 FAIL pre-fix** → **2 run / 0F** post-fix). A SEPARATE
face — the DIRECT `String.valueOf(collection)` on the NATIVE target, whose box-to-Object
path writes a raw pointer — was catalogued OPEN as **§686** and is deliberately NOT pinned.
This is the FOURTH slice that carries a compiler fix. Proof (executed): every value measured
on the Script target first, cross-checked byte-for-byte on jvm/native/js (all four agree),
then frozen; `tests/run-golden.sh` **280/280** (70 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_500`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/`check_golden_count` rc=0.

**Phase 6 slice 46 LANDED (13/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **71 total** — pinning the NULLABLE reference cast `x as T?` (the always-null
soft cast, the semantics fixed by `D-SEM-REJECTS-1210`), which had no golden coverage. The
new case `tests/golden/nullable-soft-cast` pins `a as Dog?` (an `Animal()` value) → `null`,
the same value `as Animal?` → kept, a real `Dog()` `as Dog?` → kept + `instanceof Dog`, and
`o: Object = 42` `as String?` → `null`. All six printed values agree byte-for-byte on the
four targets. **BUG FOUND + FIXED (Q0/Q4):** authoring the case surfaced the FACE A half of
**§678** — the nullable reference cast lowered to a bare `KofCheckCast`, which the
value-typed backends lower to a NO-OP, so JS and the three native targets handed back the
object of the WRONG type instead of `null` (the JVM+Script faces were already green via the
peer's 12/10 landing; the JS whole-program compare was still RED). FIXED the same commit by
desugaring the nullable REFERENCE cast in the SHARED `ExpressionBinaryInstanceOfLowerer` to
`if (x instanceof T) x else null` (the value routed through a `#softcast` temp local, so `x`
is evaluated once and each branch carries ops — the JS if-expr parser requires non-empty
branches), making the four targets agree BY CONSTRUCTION with no new runtime; the nullable
PRIMITIVE target keeps its own unbox path below and is not touched. RED-first
(`SoftCastNullableE2ETest` 6 faces: **4 FAIL pre-fix** → **6 run / 0F** post-fix); §678 stayed
live for its FACE B on the native target at this slice (plain `as` must CHECK and THROW there)
[*superseded 13/10: the NATIVE faces of FACE B landed, `§678` is CLOSED*]. This is the
FIFTH slice that carries a compiler fix. Proof (executed): every value measured on the Script
target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **284/284** (71 cases × 4 targets), exit 0;
`mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/`check_golden_count` rc=0 (the
pre-existing `check_500` RED — ExpressionTyper, KofInterpreter, NativeRiscvAsmRt0 plus the peers'
Translate/MemberResolver/StatementLowerer — is other lanes' debt and is not touched here).

**Phase 6 slice 47 LANDED (13/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **72 total** — pinning `Char` VALUES: the read-back of a `Char[]` compared against a
char literal, against another `Char[]` read, against `charAt` (the two spellings that must agree),
plus `!=`, the ordered pair, `as Int` and a case-folded `charAt`. The new case
`tests/golden/char-values` pins all eleven printed values, byte-identical on the four targets.
**BUG FOUND + FIXED (Q0/Q4):** authoring the case surfaced **§690** — the interpreter's `char[]`
holds real `Character` boxes (§185 needs them for `Array.set`) and `KofInterpreterValues.numEq`
only understood `Number`, so `"abc".toCharArray()[0] == 'a'` was FALSE on the Script while the
JVM/native/JS said true; the ORDERED faces agreed already because they go through
`KofInterpreter.unboxInt`, which knows `Character`/`Boolean`. FIXED the same commit by normalizing
the boxed canons in `numEq` (`KofInterpreterValues.numericCanon`), so equality enters the same
numeric space as the ordered path. RED-first (`CharBoxedCanonEqualityE2ETest`, Script against the
JVM oracle over ten faces: **1 of its 6 target faces FAIL pre-fix** — the only one that reads a
boxed canon — and all 6 pass after). This is the SIXTH slice that carries a compiler fix. Proof
(executed): every value measured on the Script target first, cross-checked byte-for-byte on
jvm/native/js (all four agree), then frozen; `tests/run-golden.sh` **288/288** (72 cases × 4
targets), exit 0; `mvn -o -pl kof-compiler -am compile` rc=0; gates
`check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/`check_known_bugs_status`/
`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/`check_golden_count` rc=0.

**Phase 6 slice 48 LANDED (13/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **73 total** — pinning `List.zip`, which had ZERO golden coverage (the operator was
only exercised by `ListZipE2ETest`). The new case `tests/golden/zip-pairs` pins the DECIDED
contract (`D-MULTIPARADIGMA-ZIP`: the record `Pair<A,B>`, truncating at the SHORTER list) — the
truncated size, both `Pair` fields at each kept index, the equal-length case, the empty-on-either-
side case (size 0) and a `first + "=" + second` concatenation. All twelve printed values agree
byte-for-byte on the four targets.
**Boundary measured while authoring:** the element type is `String` on BOTH sides on purpose. A
PRIMITIVE element makes the native target refuse the call with `NAT008`
(`D-MULTIPARADIGMA-ZIP-NATIVE` — a raw primitive slot seen through the bare type variable
`zipPairs<A,B>` would be unboxed as a pointer), so a primitive-element case could not run on all
four targets; the reference-element path is the one that runs everywhere. Also measured: the
EMPTY side has to be typed (`listOf("solo").drop(1)`, not a bare `listOf()`), because the bare
literal infers an unknown element and trips the same `NAT008` gate — the golden pins the typed
form, which is what a Kof program writes. Proof (executed): every value measured on the Script
target first, cross-checked byte-for-byte on jvm/native/js (all four agree), then frozen;
`tests/run-golden.sh` **292/292** (73 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/
`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/
`check_golden_count` rc=0.

**Phase 6 slice 49 LANDED (13/10, lane compiler/JVM/native `192.168.15.30:9092`):** one
more case — **74 total** — pinning the record's own VALUE surface, which had NO golden: the
existing `records` case pins only the COMPONENT access and a component-wise comparison
(`p.x() + p.y()`, `p.x() == 3`). The new case `tests/golden/record-values` pins the `toString`
form of a record (`Ponto[x=1, y=2]`, and a second record with a `String` + `Int` shape), the
whole-record `==` in BOTH directions (equal and unequal, plus a directly-constructed comparison),
a record INSIDE a list (its printed form, the size, and `contains` hitting and missing), and a
record as a Map VALUE (read back, compared, and `containsKey`). All eighteen printed values agree
byte-for-byte on the four targets.
**Why the gap mattered:** the record value semantics are exactly the surface a golden exists to
protect — `==` on a record is content equality (not identity) on all four targets, and the
`toString` form is the contract the container printer composes from, so a regression there would
silently change every printed collection of records. Probed on the four targets BEFORE freezing
(Script first, then jvm/native/js), per the plan's golden rule. Proof (executed):
`tests/run-golden.sh` **296/296** (74 cases × 4 targets), exit 0; `mvn -o -pl kof-compiler -am
compile` rc=0; gates `check_test_hygiene`/`check_owner_identity`/`check_doc_refs`/
`check_known_bugs_status`/`check_ledger_anchors`/`check_live_records`/`check_changelog_ledger`/
`check_golden_count` rc=0.

### Phase 7 — Integration

Deploy:

```
mvn verify
```

or equivalent.

**Phase 7 slice LANDED (05/10):** the plan's integration deploy is now a real
Maven profile — `mvn verify -Pintegration` runs the official golden suite
(jvm+native+js+script) and the CLI integration suite (`kof build`/`run`/`check`/
`serve`/`test`) in the `verify` phase against the freshly shaded CLI jar, via
`exec-maven-plugin` in `kof-cli` (`workingDirectory` =
`${maven.multiModuleProjectDirectory}`, so the root scripts run regardless of
the module). No CI YAML change was needed; the existing workflow steps keep
calling the scripts directly, and the profile gives a single local/CI command.
Proof (executed): `mvn -o -pl kof-cli -Pintegration exec:exec@golden-tests`
→ **48/48**; `...@integration-tests` → **9/9**; and the full reactor
`mvn -o -pl kof-cli -am -Pintegration verify` → BUILD SUCCESS, both suites green
at the `verify` phase.

**Phase 7 slice 2 LANDED (05/10):** the CLI integration suite gained the two
targets it was missing — `kof build --target js` + `node Default.mjs` (guarded
on `node`, exactly like the golden runner, so a host without Node reports SKIP
instead of a false red) and `kof run --target script` (direct IR interpretation).
`tests/run-integration.sh` now exercises **12** checks (was 9) across all four
targets plus the CLI surfaces (`check`/`serve`/`test`). Proof (executed):
`tests/run-integration.sh` **12/12**, exit 0; the `integration-tests` execution
of the `mvn verify -Pintegration` profile rides the same script.

**Phase 7 slice 3 LANDED (05/10):** the integration suite now pins the `kof run`
dispatch itself on the two targets it was missing — `kof run --target native`
(the CLI compiles, assembles and executes, distinct from the `build`+execute leg
already covered) and `kof run --target js` (the embedded JS engine, no external
`node`). `tests/run-integration.sh` now exercises **14** checks (was 12) and
every target has both a `build` and a `run` leg. Proof (executed):
`tests/run-integration.sh` **14/14**, exit 0.

## 📊 Goal

After the refactoring:

- fast feedback;
- less redundancy;
- a testable architecture;
- greater release confidence;
- regressions easier to investigate;
- tests that explain decisions;
- a suite that keeps up with Kof's growth.

## Architecture Diagram

```
Kof Test Suite
        │
        ├── Unit
        │
        ├── Component
        │
        ├── Backend
        │
        ├── Target
        │      │
        │      ├── JVM
        │      ├── Native
        │      ├── JavaScript
        │      ├── Script
        │      └── Android
        │
        ├── E2E
        │
        ├── Conformance
        │
        ├── Golden
        │
        ├── Fuzzing
        │
        └── Stress
```

## Golden rule

> "The compiler may change architecture.
>
> The test suite does not."

That phrase exactly captures the direction we are following.

## Conclusion

Kof's test suite must not be treated as secondary code.

It is one of the project's main engineering assets.

After consolidating the compiler, this is one of the biggest opportunities to
evolve Kof's quality.

Additional proposal: at the end of the refactoring, generate a permanent
document:

```
docs/testing/TEST-PERFORMANCE.md
```

tracking metrics such as:

- total time;
- time per layer;
- slowest tests;
- most unstable tests;
- suite runtime evolution.

## Next Step

Before any deep refactoring, the path is:

1. measure the whole suite (`scripts/test-suite-profile.sh`, Phase 1 — LANDED
   tooling; results in `docs/testing/TEST-PERFORMANCE.md`);
2. identify the 20 slowest tests (the profiler ranks them);
3. look for duplication (Phase 2 — discovery + ratchet LANDED:
   `scripts/test-suite-audit.sh` + `scripts/check_test_hygiene.sh`; work =
   shrink `scripts/test-hygiene-baseline.txt` via quick-win removals — current
   authority = **117** non-comment keys, per `scripts/test-hygiene-baseline.txt`);
4. propose the modularization (Phase 3 — started: `--citations` measures the split cost per
   oversized class and the drift rule is fixed; four splits landed = `KofSetEqualitySupport`
   out of `KofSetEqualityTest` (21/21 kept), `KofMathSupport` out of `KofMathTest` (29/29 kept),
   `ArrayBoundsStressSupport` out of `ArrayBoundsStressTest` (15/15 kept), `KofMediaSupport`
   out of `KofMediaE2ETest` (17/17 kept), `NullablePrimitiveContractSupport` out of
   `NullablePrimitiveContractE2ETest` (26/26 kept), `LambdaSupport` out of `LambdaE2ETest`
   (36/36 kept), `BiosBootSupport` out of `BiosBootE2ETest` (10/10 kept) and `FfiStructSupport`
   out of `FfiStructE2ETest` (12/12 kept), `ShellSupport` out of `ShellE2ETest` (21/21 kept)
   `KofStringsSupport` out of `KofStringsTest` (18/18 kept) and `KofSwitchExprSupport` out of
   `KofSwitchExprE2ETest` (32/32 kept) and `KofInterpreterParitySupport`/`...Programs` out of
   `KofInterpreterParityTest` (26/26 kept) and `UiSupport`/`UiPrograms` out of `UiE2ETest` (29/29
   kept), `UiSupport`/`UiPrograms` out of `UiE2ETest` (29/29 kept) and `JvmSupport`/`JvmPrograms`
   out of `JvmE2ETest` (35/35 kept) and `KofValidationSupport`/`...Programs` out of
   `KofValidationTest` (34/34 kept) and `KofJsSupport`/`KofJsPrograms` out of `KofJsE2ETest` (40/40
   kept) and `KofWebPrograms` out of `KofWebE2ETest` (28/28 kept) and `KofScriptPrograms` out of
   `KofScriptTest` (25/25 kept) and `WorkflowPrograms` out of `WorkflowE2ETest` (24/24 kept) and `DomainGapPrograms` out of
   `DomainGapCodesTest` (29/29 kept) and `BackendParityPrograms` out of `BackendParityTest` (19/19
   kept) and `ComponentCoreSupport`/`ComponentCorePrograms` out of `ComponentCoreE2ETest` (29/29
   kept) and `SemanticResolutionSupport`/`SemanticResolutionPrograms` out of `SemanticResolutionTest`
   (30/30 kept) and `CmdDeploySupport` out of `CmdDeployTest` (16/16 kept) → oversized 43→19,
   baseline 170→146 at the Phase-3 stage; the harness ratchet continued **146→132** in Phase 4
   and is EXHAUSTED — no further split is queued).

**Important:** this refactoring must not interfere with anything in the
compiler. It is purely test infrastructure (golden rule). The front is open
(`D-TEST-ARCHITECTURE-GO`); Phases 1–4 are CONCLUDED (oversized 43→18; harness ratchet 146→119, zero identical pairs remain). **Phase 5 is now AUTHORIZED and fifteen slices LANDED** (`D-TEST-ARCHITECTURE-PHASES`, maintainer 03/10 — `NativeCrossSupport` 54/54, `NativeIoJvmOracleSupport` (jvmOracle key eliminated), `TargetGapRefusalSupport`, `ServerProcessSupport` (stopServer key eliminated, 118/118), `JvmRunSupport` (assertRuns key eliminated, 38/38), `MultiSourceRunSupport` (runScript key eliminated, 47/47), `KofmdRunSupport` (runKof key eliminated, 19/19), `JsParityRunSupport` (assertBoth key eliminated, 18/18), `LibraryInstallSupport` (copyLibrary key eliminated, 268/268), `JvmJsRunSupport` (runBoth key eliminated, 126/126), the `NullablePrimitiveContractSupport` `runAll3` consolidation (43/43), `NativeToolchainAssumptions` (assumeToolchain key eliminated, 342/342), the `assumeAarch64`+`assumeCross` cleanup (121→119, 74/74), `QemuRunSupport` (runQemu key eliminated, 151/151, 119→118), and the `runQemuE` consolidation onto `QemuRunSupport` (18/18, 118→117)); Phases 5–7 remain open work, with the `main` cluster confirmed a false lead (Kof `main()` inside test-source text blocks, not a Java helper), and the Phase 6 first slice already LANDED.
