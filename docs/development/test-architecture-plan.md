[English](test-architecture-plan.md) | [Português](test-architecture-plan.pt_BR.md)

# 🧪 Refactoring Plan — Kof Test Architecture and Modularization

**Status:** `UNDER DEVELOPMENT` — promoted from `future/` 28/09/2026 (`D-TEST-ARCHITECTURE-GO`, `D-FUTURE-BATCH-2809`, `D-FUTURE-PROMOTION`)
**Owner:** issues/tooling lane (this session)
**Decision:** `D-TEST-ARCHITECTURE-GO` (`DECISIONS.md`) — promotion authorized "profiling → integration".
**Real state (updated 30/09/2026):** the suite is thousands of
`*Test.java` files with no layers/harness; the plan is now under way. **Landed:**
Phase 1 profiling (`scripts/test-suite-profile.sh` + permanent
`docs/testing/TEST-PERFORMANCE.md`), Phase 2 discovery audit
(`scripts/test-suite-audit.sh`) and Phase 2 **ratchet** (`scripts/check_test_hygiene.sh`
over the frozen `scripts/test-hygiene-baseline.txt`, **132 keys, rc=0** — the 30/09
measurement; the 0-citation Phase-3 head is exhausted, next candidate has 10 doc
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

### Phase 6 — Conformance

Build the official equivalence suite.

### Phase 7 — Integration

Deploy:

```
mvn verify
```

or equivalent.

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
   authority = **132** non-comment keys, per `scripts/test-hygiene-baseline.txt`);
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
(`D-TEST-ARCHITECTURE-GO`); Phases 1–4 are CONCLUDED (oversized 43→19; harness ratchet 146→132, zero identical pairs remain) — the only open work is Phase 5 (target-parameterized cross-target harness), which needs a decision.
