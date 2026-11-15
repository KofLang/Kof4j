[English](suite-verdict-0.8.0-3dad1181.md) | [Português](suite-verdict-0.8.0-3dad1181.pt_BR.md)

# Stamped suite verdict — the 0.8.0 candidate at `3dad1181`

**Run:** `scripts/safe-suite.sh` (full reactors, `-Dmaven.test.failure.ignore=true`), completed 15/11/2026 ~11:00 local, log `/tmp/opencode/safe-suite-0.8.0-tip5.log` (the tmpfs copy since cleaned; THIS file is the durable extract).

**Stamp (as printed by safe-suite.sh):**
```
SUITE-SHA: 3dad118116eefae390e28a42b89f049074fc3b3b
SUITE-DIRTY: 0
TOTAL: tests=8210 failures=0 errors=0 skipped=1014
[INFO] BUILD SUCCESS
```

**Verdict:** `stability-report.sh` → **STABILITY: GREEN sha=3dad1181 tests=8210 failures=0 errors=0 skipped=1014**; `check_lab_stability.sh` → **CUT: GREEN** (rc-gate rc=0 — 0 open `1.0-blocks`; structural gates ALL rc=0). The 0.8.0 cut is the maintainer's merge.

**Run history (the honest ledger, EN+PT in DOING.md 15/11 12:02):** run-1 7267/0F/7E (the /tmp tmpfs quota burst — the browser per-test profiles + the standing consumers hit the 7.8G ceiling); run-2 8210/0F/2E (the Cypress pinned 16.1.1 binary absent — `cypress install` restored it); run-3 8210/0F/1E (a transient tmpfs-pressure read failure, passed isolated); run-4 4635/1067F — INVALID by the lane's own hand (`_JAVA_OPTIONS` contaminates every child JVM's stderr: never set it on a byte-exact suite); run-5 GREEN with the pom's documented `-Dsurefire.argLine=-Djava.io.tmpdir=/var/tmp/kof-surefire` (forks only).

**Re-measure rule (the standing one):** a cut re-measures on the final clean tip at cut time — this extract certifies the 15/11 verdict, not the tip forever.
