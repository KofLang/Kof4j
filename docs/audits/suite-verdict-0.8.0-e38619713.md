[English](suite-verdict-0.8.0-e38619713.md) | [Português](suite-verdict-0.8.0-e38619713.pt_BR.md)

# Stamped suite verdict — the 0.8.0 candidate REFRESHED at `e38619713`

**Run:** `scripts/safe-suite.sh -o test -Dmaven.test.failure.ignore=true '-Dsurefire.argLine=-Djava.io.tmpdir=/var/tmp/kof-surefire'` (the forks-only tmpdir override — the run-4 lesson), completed 15/11/2026 ~17:50 local, log `/var/tmp/kof-suite-080f-refresh2.log` (the durable disk path — no tmpfs cleanup risk).

**Stamp (as printed by safe-suite.sh):**
```
SUITE-SHA: e3861971319280522952de9cc1364fd622ef4089
SUITE-DIRTY: 0
TOTAL: tests=8216 failures=0 errors=0 skipped=1015
[INFO] BUILD SUCCESS
```

**Verdict:** `stability-report.sh` → **STABILITY: GREEN sha=e38619713 tests=8216 failures=0 errors=0 skipped=1015**; `check_lab_stability.sh` → **CUT: GREEN** (rc-gate rc=0 — 0 open `1.0-blocks`; structural gates ALL rc=0 — 15 gates incl. the conformance gate `check_conformance_1_0`). **THE STAMP IS HEAD — zero drift** (the bare default-HEAD face is GREEN without the EXPECT_SHA treatment: the run happened on the final tip).

**WHAT THIS VERDICT COVERS (the delta since `3dad1181`, the previous GREEN):** the FFI array copy-out fatias (JVM `kof_ffi_copy_out` + the native direct payload with the `ownership borrowed` opt-in — the by-value default RESTORED after the issues/tooling lane's regression report), the linalg level-2 (dscal/axpy) and level-3 (gemm via cblas_dgemm, the CblasRowMajor=101 enum measured) faces, the conformance gate + the 15-gate stability list, the copy_in_strings/%1$s split fix (JvmFfiCopyRuntime), the FfiNativeCrossGateTest 6-arg constructions (caught by the FIRST refresh attempt's compile — the suite itself caught the missed construction).

**Run history:** the first refresh attempt (`/tmp/opencode` wiped + `FfiNativeCrossGateTest` stale constructions) failed at compile — fixed in `e38619713` and this run went straight GREEN. The earlier 5-run story (the tmpfs quota burst, the Cypress binary, the transient read, the _JAVA_OPTIONS lesson) lives in the `3dad1181` extract (`suite-verdict-0.8.0-3dad1181.md`).

**Re-measure rule (the standing one):** a cut re-measures on the final clean tip at cut time. If the tip has not moved since `e38619713`, THIS verdict is the cut verdict.
