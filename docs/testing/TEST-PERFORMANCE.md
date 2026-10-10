[English](TEST-PERFORMANCE.md) | [Português](TEST-PERFORMANCE.pt_BR.md)

# Test suite performance — measured record

last: phase-1-profiling-tooling
doing: test-architecture-phase-1
next: phase-2-quick-wins
location: docs/testing
state: active
intent: measured-suite-performance
constraint:
  - read-only-profiler
  - no-compiler-change
  - measured-not-remembered
decision: D-TEST-ARCHITECTURE-GO

Phase 1 (profiling) of [`docs/development/test-architecture-plan.md`](../development/test-architecture-plan.md).
The numbers below are **measured**, never remembered: they come from the Surefire
reports already present in the working tree, parsed by
`scripts/test-suite-profile.sh` (read-only — it never re-runs the suite).

## How to regenerate

```
scripts/test-suite-profile.sh --top 20
scripts/test-suite-profile.sh --top 20 --md /tmp/suite-tables.md
```

The profiler totals tests/failures/errors/skipped/time and ranks the slowest
test cases, classes and modules. No report found = `rc 3` (it never invents a
number).

## Snapshot

**Measured:** `tests=4368 failures=2 errors=0 skipped=125 time=2451.201s` over
4 module(s) / 502 class(es) / 502 Surefire report(s).

> **Honest basis:** an aggregate of the `TEST-*.xml` files present in the tree at
> collection time (mixed run timestamps — not a single clean run). The 2 failures
> belong to records from other lanes' in-flight runs; this front is pure
> infrastructure and runs no suite. A clean single-run snapshot is produced
> whenever a full suite is executed.

### 20 slowest test cases

| # | Time (s) | Test | Module |
|---|---------:|------|--------|
| 1 | 243.181 | `dev.kof.compiler.ArrayBoundsDeepStressTest#stress020_reducedSoakKofJsOnly` | `kof-compiler` |
| 2 | 90.62 | `dev.kof.compiler.ArrayBoundsDeepStressTest#deepStress003_oneMillionMixedIndexSeveralSeeds` | `kof-compiler` |
| 3 | 66.099 | `dev.kof.compiler.RingPrivilegeE2ETest#ring1BuiltinRunsKofFunctionAtCpl1` | `kof-compiler` |
| 4 | 56.989 | `dev.kof.compiler.ConformanceMatrixTest#conformanceCoreArithmetic` | `kof-compiler` |
| 5 | 23.584 | `dev.kof.cli.KofDebugJvmStepTest#stepThenImmediateStackTraceNeverLosesTheStoppedThread` | `kof-cli` |
| 6 | 21.75 | `dev.kof.cli.CmdWorkflowTest#realCiPipelineExampleRunsEndToEnd` | `kof-cli` |
| 7 | 17.728 | `dev.kof.cli.DepsRegistryTest#tarballSelectionKeepsExactThenJvmThenFirstTarGz` | `kof-cli` |
| 8 | 17.34 | `dev.kof.cli.CmdMakealiveTest#jsTargetFacesJvmBytes` | `kof-cli` |
| 9 | 17.194 | `dev.kof.cli.CmdMakealiveTest#destroyRemovesAllReverseTopoAndStateRebirthsOnNextPlan` | `kof-cli` |
| 10 | 17.143 | `dev.kof.cli.CmdWorkflowTest#runJsDryRunAndJobFacesJvm` | `kof-cli` |
| 11 | 15.112 | `dev.kof.compiler.ConformanceMatrixTest#conformanceJson` | `kof-compiler` |
| 12 | 14.808 | `dev.kof.compiler.ArrayBoundsDeepStressTest#deepStress011_millionElementArrayHeavyRejectionLoop` | `kof-compiler` |
| 13 | 13.42 | `dev.kof.compiler.InteropTimeoutE2ETest#cancelIdleIsNoopOnCross` | `kof-compiler` |
| 14 | 12.544 | `dev.kof.cli.CmdMakealiveTest#applyPersistsGenerationAndPlanBecomesEmpty` | `kof-cli` |
| 15 | 11.314 | `dev.kof.cli.CmdWorkflowTest#listShowsJobsAndDeps` | `kof-cli` |
| 16 | 10.97 | `dev.kof.script.KofScriptTest#interpreterParitySweep` | `kof-script` |
| 17 | 10.625 | `dev.kof.cli.CmdWorkflowTest#runFailureExitsOne` | `kof-cli` |
| 18 | 10.548 | `dev.kof.cli.DepsSourceModuleTest#libraryPackageWithoutJarInstallsVerifiedSourcesAndIsIdempotent` | `kof-cli` |
| 19 | 9.821 | `dev.kof.cli.DepsRegistryTest#pullResolvesKofReleaseClasspathSeesTheJarAndIsIdempotent` | `kof-cli` |
| 20 | 9.616 | `dev.kof.compiler.PdfLibraryReachProbeTest#probeReach` | `kof-compiler` |

### 20 slowest classes

| # | Time (s) | Class | Module |
|---|---------:|-------|--------|
| 1 | 366.279 | `dev.kof.compiler.ArrayBoundsDeepStressTest` | `kof-compiler` |
| 2 | 113.055 | `dev.kof.compiler.ConformanceMatrixTest` | `kof-compiler` |
| 3 | 102.585 | `dev.kof.cli.CmdWorkflowTest` | `kof-cli` |
| 4 | 76.124 | `dev.kof.compiler.RingPrivilegeE2ETest` | `kof-compiler` |
| 5 | 65.871 | `dev.kof.cli.CmdMakealiveTest` | `kof-cli` |
| 6 | 64.733 | `dev.kof.cli.DepsRegistryTest` | `kof-cli` |
| 7 | 51.059 | `dev.kof.compiler.KofWebE2ETest` | `kof-compiler` |
| 8 | 48.789 | `dev.kof.cli.DecompileTest` | `kof-cli` |
| 9 | 43.956 | `dev.kof.compiler.InteropTimeoutE2ETest` | `kof-compiler` |
| 10 | 41.824 | `dev.kof.compiler.KofSecurityTest` | `kof-compiler` |
| 11 | 40.225 | `dev.kof.compiler.KofOrmE2ETest` | `kof-compiler` |
| 12 | 38.348 | `dev.kof.cli.DepsSourceModuleTest` | `kof-cli` |
| 13 | 30.869 | `dev.kof.compiler.NullablePrimitiveContractE2ETest` | `kof-compiler` |
| 14 | 28.883 | `dev.kof.cli.KofDebugJvmStepTest` | `kof-cli` |
| 15 | 27.271 | `dev.kof.compiler.KofTimeE2ETest` | `kof-compiler` |
| 16 | 26.684 | `dev.kof.compiler.KofConcurrency2Test` | `kof-compiler` |
| 17 | 26.53 | `dev.kof.compiler.IoE2ETest` | `kof-compiler` |
| 18 | 25.504 | `dev.kof.compiler.ComponentCoreE2ETest` | `kof-compiler` |
| 19 | 24.99 | `dev.kof.compiler.UiE2ETest` | `kof-compiler` |
| 20 | 24.268 | `dev.kof.script.KofScriptTest` | `kof-script` |

### Time by module

| Time (s) | Module |
|---------:|--------|
| 1883.836 | `kof-compiler` |
| 512.715 | `kof-cli` |
| 53.301 | `kof-script` |
| 1.349 | `kof-c-compiler` |

## Phase 2 discovery (source audit)

`scripts/test-suite-audit.sh` scans the test **sources** (read-only; never runs
the suite) for the quick-win targets the plan names.

**Measured:** `sleeps=56 oversized(>=500)=43 duplicate-across-classes=117` over
502 test sources.

- **`Thread.sleep`** — 56 sites (one is a prose mention in the
  `AsyncSleepJsE2ETest` javadoc), concentrated in server/debug/boot waits
  (`ServePortTest`, `BiosBootE2ETest`, `KofDebugJvmExceptionTest`). Leads for
  Phase 2 (deterministic waits), not automatic removals: several guard real
  external processes.
- **Oversized test classes** — 43 classes ≥ 500 lines; the tail is
  `CompilerDriverTest` (5298), `KofOrmE2ETest` (3932), `NativeRiscvDbWireTest`
  (2871). Prime targets for Phase 3 modularization.
- **Duplicated test-method names** — 117 names span ≥ 2 classes. This is a
  **lead, not a defect count**: cross-target parity clusters (same face on
  JVM/Native/JS) and shared helpers (`main`, `assumeToolchain`, `jvmOracle`) are
  expected and intentional.

- **Oversized classes by doc-citation exposure** — `--citations` counts, per
  oversized class, how many files under `docs/` mention its name (a split that
  renames/moves cited tests forces a citation sweep; `0` is the cheapest).
  Measured cheapest-first head: `ArrayBoundsStressTest` (2),
  `KofSetEqualityTest` (2), `SemanticResolutionTest` (4), `CmdDeployTest` /
  `BiosBootE2ETest` / `KofInterpreterParityTest` /
  `NullablePrimitiveContractE2ETest` (8) … `ConformanceMatrixTest` (42). Cost is
  prose too: a class-count citation ("whole `KofSetEqualityTest` 21/21") drifts
  even when the cited method stays — move only uncited tests, keep cited
  methods and the class name in place, update the counts. **First split
  (29/09):** `KofSetEqualitySupport` extracted the shared sources + JVM/JS
  runners out of `KofSetEqualityTest` (all 21 cases kept, zero citation drift)
  → oversized 43→42, baseline 170→169; and `KofMathSupport` out of `KofMathTest`
  (all 29 cases kept) → oversized 42→41, baseline 169→168; and `ArrayBoundsStressSupport`
  out of `ArrayBoundsStressTest` (all 15 cases kept) → oversized 41→40, baseline
  168→167; and `KofMediaSupport` out of `KofMediaE2ETest` (all 17 cases kept, the WAV/MP4
  byte builders) → oversized 40→39, baseline 166; and `NullablePrimitiveContractSupport` out
  of `NullablePrimitiveContractE2ETest` (all 26 cases kept) → oversized 39→38, baseline 165;
  and `LambdaSupport` out of `LambdaE2ETest` (all 36 cases kept) → oversized 38→37, baseline 164;
  and `BiosBootSupport` out of `BiosBootE2ETest` (all 10 cases kept) → oversized 37→36, baseline
  163; and `FfiStructSupport` out of `FfiStructE2ETest` (all 12 cases kept) → oversized 36→35,
  baseline 162; and `ShellSupport` out of `ShellE2ETest` (all 21 cases kept) → oversized 35→34,
  baseline 161; and `KofStringsSupport` out of `KofStringsTest` (all 18 cases kept, two inline
  programs hoisted to constants) → oversized 34→33, baseline 160; and `KofSwitchExprSupport`
  out of `KofSwitchExprE2ETest` (all 32 cases kept, all 32 inline programs hoisted) → oversized
  33→32, baseline 159; and `KofInterpreterParitySupport`/`KofInterpreterParityPrograms` out of
  `KofInterpreterParityTest` (all 26 cases kept, 8 largest programs hoisted) → oversized 32→31,
  baseline 158; and `UiSupport`/`UiPrograms` out of `UiE2ETest` (all 29 cases kept, 11 largest
  programs hoisted) → oversized 31→30, baseline 157; and `JvmSupport`/`JvmPrograms` out of
  `JvmE2ETest` (all 35 cases kept, 12 largest programs hoisted) → oversized 30→29, baseline 156;
  and `KofValidationSupport`/`KofValidationPrograms` out of `KofValidationTest` (all 34 cases
  kept, 6 largest programs hoisted) → oversized 29→28, baseline 155; and `KofJsSupport`/
  `KofJsPrograms` out of `KofJsE2ETest` (all 40 cases kept, 24 programs hoisted) → oversized
  28→27, baseline 154; and `KofWebPrograms` out of `KofWebE2ETest` (all 28 cases kept, 19 programs
  + `WEB_APP` hoisted) → oversized 27→26, baseline 153; and `KofScriptPrograms` out of
  `KofScriptTest` (all 25 cases kept, 8 programs hoisted) → oversized 26→25, baseline 152; and
  `WorkflowPrograms` out of `WorkflowE2ETest` (all 24 cases kept, 7 programs hoisted) → oversized
  25→24, baseline 151; and `DomainGapPrograms` out of `DomainGapCodesTest` (all 29 cases kept, 10
  programs hoisted) → oversized 24→23, baseline 150; and `BackendParityPrograms` out of
  `BackendParityTest` (all 19 cases kept, 5 programs hoisted) → oversized 23→22, baseline 149; and
  `ComponentCoreSupport`/`ComponentCorePrograms` out of `ComponentCoreE2ETest` (all 29 cases kept,
  28 programs hoisted) → oversized 22→21, baseline 148; and `SemanticResolutionSupport`/
  `SemanticResolutionPrograms` out of `SemanticResolutionTest` (all 30 cases kept, 19 programs
  hoisted) → oversized 21→20, baseline 147; and `CmdDeploySupport` out of `CmdDeployTest` (all 16
  cases kept, 16 helpers/records extracted, kof-cli module) → oversized 20→19, baseline 146.
  **Phase 4/harness (29/09):** `MediaByteSupport` consolidated 7 duplicated media byte-layout
  helpers from `MediaCrossE2ETest`/`MediaNativeE2ETest` → **3 fewer `dupname` keys**; and
  `KofCSupport` consolidated the C-compiler harness across 4 `KofC*CompilerTest` → **2 more**
  (baseline **132**, 14 total across the six slices). Re-measure `--citations`
  before the next pick: naming candidates in the queue itself adds citations to them. (The
  third split also repaired an inherited ratchet regression: `IniReaderE2ETest`'s
  `parsesOnScript`/`parsesOnNativeX86`/`parsesOnNativeRiscv64` duplicated
  `XmlReaderE2ETest`'s names, so they were prefixed `ini` — the ratchet stays honest.)

The audit modifies nothing; acting on a lead is a separate, scoped unit.

## Reading

- The slow tail is dominated by **stress tests** (`ArrayBoundsDeepStressTest`,
  ≈366s) and **cross-process E2E** (`ConformanceMatrixTest`, `CmdWorkflowTest`,
  `CmdMakealiveTest`, `DepsRegistryTest`) — the natural targets of Phase 2.
- Phase 2 (quick wins: repetition/sleeps/redundant setup) and Phase 3
  (modularization into layers) act on this measured base, never on memory.
