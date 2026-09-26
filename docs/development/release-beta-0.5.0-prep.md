[Português](release-beta-0.5.0-prep.pt_BR.md) | [English](release-beta-0.5.0-prep.md)

# Release 0.5.0 — preparation (branch `beta-0.5.0`)

Decision: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09, maintainer order). Active
branch is `beta-0.5.0`; `beta-0.4.0` only receives in-flight landings and
release prep. This doc is the queue — it stays in `docs/development/` until
the release is cut (three-states rule).

## Checklist (ordered — version number and tag are the maintainer's call, rule 6)

1. [x] Land what is in flight: §374/#553 (`.22` — WIP in
       `JvmOpCollections`), §371/#550 (CLI cross build), §378/#554 (docs
       gate). **ALL THREE LANDED 20/09 — ✅ FIXED** (`§374` box-if-primitive at
       class load, `BareCollectionFieldE2ETest` 8/8; `§371` shipped-CLI cross,
       `ShippedCliCrossSmokeTest` 2/2 + `RuntimeSourceLoaderTest` 6/6; `§378`
       EN×PT open-set cross-check — proofs in `known-bugs.md`). Docs lane ff
       `beta-0.5.0` after every landing on `beta-0.4.0`.
2. [ ] CodeQL debt (#555): **TRIAGE CLOSED 20/09 (unit I, §385)** — the 40
       in the window: 13 fixed in code with targeted tests, 26 dismissed with
       a real reason (25 test-harness `used in tests` + FP JEP 443 #876),
       #938 fixed by the tooling lane awaiting `main` re-scan. The gate is now
       BASELINE-driven (`scripts/codeql-baseline.txt`: only NEW alerts block;
       `CODEQL_GATE_SKIP` requires a reason, prints a banner, logs to
       `.git/codeql-gate-skips.log`; ignored in CI) — `scripts/codeql-gate.sh
       --fast` already measures GREEN with no skip (rc=0). Still needed to
       tick [x]: ff of `q555` + first re-scan closing the 14 ids tolerated in
       the baseline (prune those lines then); #563 (src/test family in CI)
       follows its own queue.
3. [ ] Version bump: **DECIDED 09/20/2026 — the release ships as
       `0.5.0-beta`** (beta suffix kept, no codename; `D-RELEASE-0.5.0-GATE`
       addendum). `VERSION`/`pom.xml` are already at `0.5.0-beta`; only the
       CHANGELOG/tag remain. Check hardcoded version refs (tests/javadoc
       mention the artifact version) BEFORE bumping; never a unilateral edit.
       **Audited 21/09 (9093): clean** — the remaining `0.4.0` hits are
       provenance comments (when a port landed) and `beta-0.4.0` used as a
       *branch name* by `codeql-gate.sh` (monitors both) and by test fixtures;
       none hardcode the artifact version.
4. [~] CHANGELOG cut (EN+PT): a `0.5.0` section gathering the unreleased
       bullets; `AGENTS.md`(+PT) header `Version:` updated in the same
       commit. (Lanes may draft it now; the cut still waits on the seven
       conditions.) **DRAFT LANDED 22/09 (sessão 9092):** the unreleased
       header is now `## [0.5.0-beta] - unreleased (branch beta-0.5.0)`
       (EN+PT — `VERSION` is already `0.5.0-beta`; `AGENTS.md`(+PT) header
       already bumped by `D-VERSION-BUMP-0.5.0`). The CUT itself (date the
       section + tag) still waits on the seven conditions.
5. [ ] Tally: `'Current build: **N**'` in `docs/backend-parity.md`(+PT) from
       the first GREEN hosted CI Build+Tests run on the release tip
       (measured from the job log, never memory).
6. [ ] Stability proof: full suite 0F/0E + 5/5 conformance matrix MEASURED
       on the tag candidate (AGENTS §Stability — tag only after green).
7. [ ] Tag + release notes (EN+PT); declare `beta-0.4.0` closed except for
       the residual-fix list. **`main` stays frozen until this release**
       (maintainer 09/20/2026): the 12 pre-fix CodeQL alerts on `main` are
       ported on release day, not before; the gate measures `beta-0.5.0`.

## Open issues that travel to `beta-0.5.0`

#555 (CodeQL umbrella — the only one still open; **#550/#553/#554 landed
20/09** with their sections ✅ FIXED). Announced on each issue and via the
`DOING.md`(+PT) banner.

## Release gate (`D-RELEASE-0.5.0-GATE`, 09/20/2026, maintainer directive)

The 0.5.0 release is cut only when **all seven conditions** hold, each one
**measured** (never by eye). This gate refines the checklist above: the
checklist is the tactical queue, these seven are the acceptance. The
maintainer's directive is the priority for "releasing the 0.5.0 gate to all
agents".

| # | Condition | How it is measured | State 21/09 (measured — never by eye) |
|---|---|---|---|
| 1 | 100% parity between targets | per-target matrix + golden byte parity where the contract requires; divergence = bug or diagnosed `XXX00x`. **Auto-measured** by `check_release_050_gate.sh` (runs `scripts/target-matrix.sh`, EG-5, and reads its `PARITY: 100%` line) | GREEN (measured 21/09: `PARITY: 100%` on jvm/x86-64/riscv64/aarch64/JS/Script vs the JVM oracle; the tree jar was rebuilt with `scripts/build-kof-jar.sh`, which also stamps it so a rebase no longer fakes "stale"; on a rootless host the cross toolchain (binutils/qemu/libc) is set up by `scripts/setup-cross-toolchain.sh`, which extracts the `.deb`s to a local prefix and points `KOF_CROSS_SYSROOT` at it). **Re-measured 21/09 at `c8d62388` (tip): BACK TO GREEN** — `scripts/build-kof-jar.sh` (rebuilds + stamps `lib/kof.jar` by source content) then `scripts/target-matrix.sh` under the `scripts/setup-cross-toolchain.sh --export` env (`PATH`/`LD_LIBRARY_PATH`/`KOF_CROSS_SYSROOT`) → `PARITY: 100%` (jvm/x86_64/riscv64/aarch64/js/script byte-identical vs the JVM oracle; kofc=EG-9, android=EG-10 delegated). The earlier same-day NEEDS-MEASURE (landings makealive 3.2 `966c86a4`, §422 `9d97b268`, …, re-stale `lib/kof.jar`; `chain-check` names the newest source) is the condition's own rule, re-instated at cut by condition 6 — **not a parity regression**. Re-measured 21/09 at `29198ea8` (after `KofBuffer.gapCode` changed, jar rebuilt): `PARITY: 100%` again |
| 2 | No pending decision | `DECISIONS.md` has no open question changing the surface | NEEDS-REVIEW (**not RED**) — **2** approved `State: OPEN` fronts in flight (`D-TYPE-VARIANCE`/X5, `D-INTEROP-REFLECT`/X6), synced 22/09 with the gate (`decisions` names exactly these two); `D-SECRETS` is now `DECIDED` (all faces landed 21/09, `04473bbe`) and left this row. Per `D-RELEASE-0.5.0-GATE` condition 2, an approved OPEN front does not block the cut and is never "nothing waits" |
| 3 | All loose `docs/development/*.md` concluded and moved out | three-states rule; only work with pending implementation stays | **GREEN (21/09, `D-RELEASE-0.5.0-SCOPE`)** — the in-flight OWNED plans still loose are allowlisted and do not gate the 0.5.0 cut: `ffi-abi-structs` [jonas], `db-parity-plan` [gaps-db lane]; each keeps its owner/queue (README sec.1). `makealive-plan`, `secrets-plan` and `IMPLEMENTATION-UNIVERSAL-PLATFORM` MOVED 21/09 — concluded; `type-system-extensions-plan` (X5+X6) MOVED 22/09 — concluded. `loose_docs GREEN` measured |
| 4 | Total stability | full suite 0F/0E + 5/5 conformance matrix on the candidate. **Auto-measured** from a real suite log via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`); GREEN requires the `TOTAL` to be 0F/0E **and** the log **stamped** (`SUITE-SHA` == tip, `SUITE-DIRTY=0`) — a log from another commit or from a dirty tree is `unknown`, never a false GREEN | GREEN (measured 21/09 at `67b087f9`): fresh full `safe-suite.sh` run — `TOTAL: tests=3440 failures=0 errors=0 skipped=225`, `SUITE-SHA`==tip, `SUITE-DIRTY=0`, `stability-report.sh` returned GREEN from the stamped log. The earlier RED at `96af9b63` (`tests=3413 failures=1`) was the **§422 stale test** `CompilerDriverTest#externProducesHonestGapNotSilentDrop`, which still demanded `FFI001` for an `Int[]` extern that `7c6413d4` (D6-2) intentionally made bindable on the JVM — **RESOLVED 21/09 by the FFI lane**: `7c89f531` repointed the assertion to genuinely-unbound signatures (`String[]`/`List<Int>`→`FFI001`, `Buffer(Int)`→`SEM096`) and `9d97b268` closed §422 (`CompilerDriverTest` 259/0F/0E), so the stale red is gone. The `8f459b8e` baseline (`tests=3355`, first self-certifying log — the old `safe-suite.sh` printed `TOTAL` to the console only and never appended it, so NO stamped run could be certified until that mechanism fix) still stands as history. **Re-measured 21/09 at `29198ea8` (tip): `TOTAL: tests=3479 failures=0 errors=0 skipped=13`, `SUITE-SHA==tip`, `stability-report.sh` GREEN** — the 3 failures seen at the §431 candidate (`3472/3F`) were stale JS-FFI ratchets now reconciled (`KofBuffer.gapCode` JS gap, `InteropIdiomsCompileTest` JS bridge, `ArtifactSizeTest` `HELLO_JS_BYTES` 13.007→13.834 KB), so that red is gone. The candidate must still be re-measured on the final clean tip at cut time — condition 6's own rule, not this row's doubt |
| 5 | 0 open issues that are a bug | GitHub OPEN issues with a `bug` label = 0 | GREEN (0 open bug issues; the condition reads the **query's exit code** — an API failure is `UNKNOWN`, never GREEN; on a host without `gh`, `scripts/fetch-open-issues.sh` supplies `R050_OPEN_ISSUES_TSV` from the public API — measured 21/09: 0 open bug issues, #580 is documentation/enhancement) |
| 6 | All edges closed | EG-1..EG-7 closed + open `1.0-blocks` = 0; **EG-8 decoupled** (`D-RELEASE-0.5.0-SCOPE`) — it is the 1.0-line RC cut + the maintainer's declaration, only after EG-1..EG-7 | **GREEN (21/09, `R050_OPEN_BLOCKS=0` measured via authenticated `gh`: the only 2 open items are dependabot PRs, 0 issues)** — an unreadable EG table is `UNKNOWN`, never GREEN; a failed `1.0-blocks` enumeration leaves that part `UNKNOWN`, never an implied 0 |
| 7 | Nothing pending in bugs-and-gaps | `check_known_bugs_status.sh` live set empty + `specification-gaps.md` 0 open | RED (2 live at the tip — **26/09: lane compiler 9092 CLOSED §500 (fatia B — static fields via real `KofGetStatic`; RED→GREEN `ExternalStaticFieldE2ETest` 8/8, goldens measured on the bare JVM; the write face is `SEM025`, not COMP002; reactor 4099 0F/0E)** — 3→2; **26/09: §508 FIXED same day by its owner (baremetal lane)**; heartbeat had catalogued §508 (PR-mode-only CodeQL ERROR at `RuntimeDtoaSchubfach.java:90` — provably false-positive: `gTable()` length 1234 is even, step-2 never touches `length`; the branch gates read push-mode alerts and are unaffected; dismiss-or-guard action recorded for the owner, baremetal lane) — 3→4; **26/09: lane compiler 9092 catalogued §500** (static method/field on an imported external class name that does not resolve emits empty-owner `invokevirtual "".bogus` — even the valid varargs `Arrays.asList` is affected, `ExternalClasspath` lacks `ACC_VARARGS`; needs a dedicated unit) — 2→3; **26/09: lane compiler 9092 FIXED §499** (unknown static method on a builtin type name — now `SEM074`; RED→GREEN `BuiltinUnknownMethodGuardTest` 14/14; suite 4016 0F/0E) — 3→2; **26/09: lane compiler 9092 catalogued §499** (unknown static method on a builtin type name — `String.bogus()`/`Int.bogus()` compile clean and emit `invokestatic <Owner>.bogus` → `NoSuchMethodError`; needs a curated interop whitelist shared by typer+lowerer, rule 6/11) — 2→3; **26/09: lane native-cross FIXED §497** (recursive `delete`, `modifiedTime`, `isSymlink`, `moveTo`, `copyTo` on x86-64 + riscv64/aarch64; `NAT006` closed) — 4→3; **26/09: lane native-cross catalogued §497** (native `kof.io` gaps: non-recursive `Directory.delete` on x86-64 + no native `copyTo`/`moveTo`/`modifiedTime`/`isSymlink`) — 3→4; **25/09: lane compiler 9092 FIXED §496** (unknown FIELD on any builtin namespace now SEM102) — 3→2; (**24/09: lane gaps-db catalogued §493** (JVM vs Native diverge on the `orm.delete`/`deleteAll` MySQL error path — JVM throws a SQLException string, Native x86-64/cross return `true`; contract decision) — 0→1; **24/09: lane compiler 9092 FIXED §488** (x86 `RuntimeDb5`: NULL → literal `null`, `len==0` → `kof_json_encode_string`; E2E `nativeMysqlNullAndEmptyStringJson` JVM-oracle byte-parity, RED pre-fix `{"n":,}`) — 1→0; **24/09: lane gaps-db catalogued §488** (x86 MySQL text path emits NULL as a raw empty string → invalid JSON `{"n":,`, and an empty string cell as a raw number; `RuntimeDb5 .Ldb_mysql_null`; root-cause fix pending) — 0→1; **24/09: lane compiler 9092 FIXED §278 face gpu** (Android compiles `kof.gpu` like the JVM — byte-identical `Main.class`; runtime = `JvmVkStubRuntime`, FFM-free on ART) — 1→0; **23/09: lane 9092 FIXED §485** (channel drain-then-send NULL deref — stale `tail`; `channelDrainThenSendNative` x86+riscv64+aarch64) — 2→1; **23/09: lane compiler 9092 FIXED §486 face (b) `43f2833a`** (return-suffixed bridge mangling — bridge×concrete no longer collide; `NativeGenericIfaceBridgeE2ETest` now proves BOTH faces 3/3 x86+riscv64+aarch64) **and §487 `374b2b4bb`** (issue #610 — default-method diamond now `SEM101` at compile; overload by arity across interfaces fixed) — 3→2; **23/09: lane compiler 9092 FIXED §486 face (a)** (reference-return covariant bridge on Native skipped — register pass-through; `NativeGenericIfaceBridgeE2ETest` 3/3 x86+riscv64+aarch64) **and catalogued §486 face (b)** (primitive-return bridge collides on one asm symbol; needs return-type-aware native mangling; files owned by the baremetal lane) — 2→3; **23/09: lane compiler 9092 FIXED §205** (Object-typed print on Native — record/class reference now dispatches `toString` via `kof_tostring_table[type_id]`; §205 slice 2 = N2/tagged-box ABI; `NativeObjectBoxPrintE2ETest` 3/3 x86+riscv64+aarch64) — 2→1; **23/09: session 9092 FIXED §483 + §271 + §248** (generic-interface dispatch on Native + interface default methods on Native/JS; `GenericInterfaceAssignabilityTest` 10/10, `InterfaceDefaultMethodE2ETest` 7/7 on 4 targets) — 5→2; **23/09: lane 9093 FIXED §476 + §478 (#587 break-in-case) — 5→4**; **23/09: lane 9092 baremetal catalogued §476** (mixed pattern+value `case` list + empty `default:` → JVM `VerifyError`) — 4→5; **23/09: lane 9092 baremetal FIXED §423** (cross channels ported — `kof_channel_*` in slice `RtB61`; NAT005 gate removed; qemu parity on both arches) — 5→4; **23/09: lane 9092 baremetal FIXED §448** (Schubfach dtoa cross riscv64/aarch64 `Double`+`Float`) — 6→5; **23/09: lane 9093 nat FIXED §444** (TypeVariable branch in the x86 `valueOf` dispatcher) — 7→6; **22/09: session 9093 (typer lane) FIXED §280** (53/91 sites com posição real; `DiagnosticSourceLocationTest` 5/5) — 8→7; **22/09: lane 9093 FIXED §442** (split `NewExpr` → `SemNewExprTyper`; `check_500` rc=0, suite 3590 0F/0E @ `fd5119f69`) — 9→8; 22/09: lane 9093 FIXED **§268** (`D-RULE6-BATCH` (A): cached `java.lang` probe + SEM087) + **§288** (`D-RULE6-BATCH` (b): parse-time `TypeVariable` single source + interim SEM085 for owner-T function types, `FnTypeVarSignatureE2ETest` 9/9) and catalogued **§444** (pre-existing native gap — set moved §288 out / §444 in, net 9); merged tip 11→10 covers **§302** closed (CLOSEALL) + **§442** catalogued; 21/09: (14→12 after the maintainer's CLOSEALL batch closed §334/§400) after §421 S0 fix + §423–§431 catalogued, §431 closed by lane `.18`, §429 fixed (LSP `-32601`), §435 closed by lane `.18` (check_500 `LspServer` 601→582, split into `LspJsonRpc`), §432 fixed (JVM `getOrDefault` VerifyError), §428 fixed (DAP unimplemented requests now honest-fail), §425 fixed (riscv64/aarch64 `kof.config` now honest `CONF001`), §426 fixed (`time.collect()` on JS now `TIME004`), §427 fixed (riscv64/aarch64 `kof.io`/web-T1 now `NAT006`/`NAT007`) and §424 fixed (five `String` methods on JS/Native now honest `STR003`), §437 closed (check_500 red already gone — `JvmOpCollections` 594 < 600 after the §432 dead-code cleanup, no split) and §438 fixed (`KofDebugJvmSession` shutdown hook + tree-kill test teardown, so no debuggee/`/tmp/kof-debug-*` outlives a session); the script is the authority; an unreadable ledger is `UNKNOWN`, never GREEN) |

| 8 | **Full platform parity (BLOCKER, `D-FULL-PARITY-050` 24/09)** | `docs/development/parity/PARITY-GAPS.md`(+PT) has **0 open rows** — machine-checked: `check_release_050_gate.sh` → `full_parity` (ledger missing/unparsable = UNKNOWN, never GREEN); every namespace face compiles AND runs byte/golden vs the JVM oracle on JVM/Script, Native x86-64, Native riscv64/aarch64 and JS | **RED (24/09, created measured)** — 16 open rows: process/shell `PROC001`; ssh (code to catalog); media `MEDIA001/003`; mq `MQ001`; gpu JS+golden `GPU001`; observability golden `OBS003`; time cross `TIME002/004`; cache/config/log cross golden + interp log `CONF001`; `math.pow` cross `MATH001`; strings `NAT-STR01`/`STR003`; web T1 native `WEB00x`; kof.io cross `NAT006/007`; security cross `SECN001/003/004/005`; orm native `ORM001`; db native query/prepared `DB001`. Condition 1 (conformance matrix) stays; this is the long tail the matrix never covered — unmeasured golden = OPEN (Q5) |

Mechanized by `scripts/check_release_050_gate.sh` (reports each condition as
GREEN / RED / NEEDS-MEASURE / UNKNOWN; RED-first test
`scripts/tests/check-release-050-gate-test.sh`). Every data-driven condition
**refuses GREEN when its source is unreadable** — stale jar, a suite log from
another commit or from a dirty tree, a failed GitHub query, an unparsable EG
table, an unreadable bug ledger, a missing decision source or loose-doc list:
all seven conditions are inconclusive, never falsely green.
RED is expected until the queue closes — the gate is the driver, not a blocker
to work around.

### Recovery — clearing the auto-measured conditions

```bash
eval "$(scripts/setup-cross-toolchain.sh --export)"     # cond. 1: cross binutils/qemu/libc (rootless host; once)
scripts/build-kof-jar.sh                                # cond. 1: rebuild + stamp the tree jar (after the last compiler commit)
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 core targets)
scripts/fetch-open-issues.sh > /tmp/open-issues.tsv     # cond. 5: when `gh` is unavailable (public API)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: run on a CLEAN tree
R050_OPEN_ISSUES_TSV=/tmp/open-issues.tsv \
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
