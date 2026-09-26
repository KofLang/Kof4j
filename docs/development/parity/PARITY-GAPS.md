[English](PARITY-GAPS.md) | [Português](PARITY-GAPS.pt_BR.md)

# Full-parity ledger — 0.5.0 blocker (maintainer 24/09)

> **MANDATE (maintainer, 24/09): full platform parity is INDISPENSABLE for
> the 0.5.0 release.** An honest gap code (`PROC001`, `MEDIA001`, ...) is the
> sanctioned WAY to track a missing face — never the sanctioned END state.
> Every row below is a feature that today works on SOME targets and is a
> named/diagnosed gap on others. **`0.5.0` does not cut while this ledger
> has ANY open row.**
>
> Rule of the ledger (three states, machine-checked by
> `check_release_050_gate.sh` → `full_parity`):
> - a row leaves ONLY when the feature compiles AND runs with byte/golden
>   parity on ALL targets (proof test named, runner recorded);
> - partial closes move the row's target cells (never mark a row DONE
>   partially);
> - the ledger EMPTY (no rows) is the GREEN state — `docs/development/parity/`
>   itself STAYS (the contract + history), the file body shrinks to "0 open rows".
>
> "⏳ golden" = the face exists but the cross golden (riscv64/aarch64 byte
> diff vs JVM oracle) was never measured — unmeasured is NOT green (Q5: no
> false green).

## Open rows (measured 24/09 from `DomainGapCodesTest`, `Kof*.java` gap codes, `training/idioms/stdlib.md` parity table, `docs/bugs-and-gaps/known-bugs.md`)

| # | Surface | JVM/Script | Native x86-64 | Native riscv64/aarch64 | JS | Gap code | Tracker / owner lane |
|---|---------|------------|----------------|--------------------------|----|----------|----------------------|
| 1 | `process.run`/`spawn`/`exit` | ✅ | ✅ x86 `run`/`exit`/`spawn` + whole-record `println(r)`/`"x"+r` 26/09 (`RuntimeProcess`/`RuntimeProcessSpawn`/`RuntimeProcessResult`; JVM §367 content with trailing CR/LF trimmed; handle: `readLine`/`write`/`exitCode`/`kill`/`alive`) | ✅ cross `run`/`spawn`+handles + whole-record print 26/09 (`NativeRiscvAsmProcess`/`NativeRiscvAsmProcessSpawn`/`NativeRiscvAsmProcessResult`; golden `ProcessResultWholePrintE2ETest` JVM≡riscv64≡aarch64) | ✅ (KofJsRunner) | `PROC001` (MCU/riscv32: `run`/`spawn`/whole-record print) | native-cross lane (run x86 ✅ 25/09, cross ✅ 26/09; spawn x86 ✅ 26/09, cross ✅ 26/09; whole-record x86+cross ✅ 26/09) |
| 3 | `ssh.cmd`/`run`/`ok` | ✅ | ✅ x86 + riscv64/aarch64 26/09 | ❌ | ❌ | `PROC001` (MCU/riscv32; JS sem dispatch) | native-cross lane (nativo ✅ 26/09) |
| 4 | media: `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record`/`list` | ✅ | ⚠️ x86-64 `Video`+`Audio` ✅ 26/09 (`RuntimeMedia`/`RuntimeMediaMp4`/`RuntimeMediaWav` — MP4 moov/mvhd incl. extended size, WAV PCM-16 parse+save com mkdirs recursivo; `MediaNativeE2ETest` byte-for-byte vs JVM + cap-64 honesto; `Image`/`Mic` ficam `MEDIA001` — decoder/encoder = regra 6) | ⚠️ riscv64/aarch64 `Video` ✅ 26/09 fatia 2A (`NativeRiscvAsmMedia`/`NativeRiscvAsmMediaMp4` — newfstatat S_IFREG + read, cap-64, MP4 be-scan c/ size=1/0, mvhd v0/v1, `bytes()` signed; `MediaCrossE2ETest` byte-for-byte vs JVM nos 2 arcos sob qemu); `Audio` = fatia 2B pendente | ❌ | `MEDIA001`/`MEDIA003` (Image/Mic all; Audio cross+JS) | media front (x86 fatia 1 + cross Video fatia 2A ✅ 26/09) |
| 10 | `math.pow` cross (static, no libc) | ✅ | ✅ (libm `-lm`) | ❌ `MATH001` | ✅ | `MATH001` | native cross lane |
| 11 | `strings.reverse` non-ASCII (UTF-16 vs byte) + `String.matches`/`replaceAll`/`replaceFirst`/`compareToIgnoreCase` | ✅ | ❌ `NAT-STR01`/`STR003` | ❌ `STR003` | ❌ `STR003` | `NAT-STR01`/`STR003` | native/js lanes |
| 12 | web T1 (`kof.http.server` faces) on native/cross | ✅ | ⏳ | ❌ `WEB002`–`WEB006` | ✅ | `WEB00x` | web lane |
| 13 | `kof.io` file faces on cross | ✅ | ✅ x86 | ✅ full — stat+text+fs (`exists`/`isFile`/`isDirectory`/`readText`/`writeText`/`appendText`/`delete`/`create`/`createDirectories`/`mkdirs`/`size`/`readBytes`/`writeBytes`/`appendBytes`/`list`/`readRange`/`name`/`path_fileName`/`path_parent`/`path_extension`/`path_isAbsolute`/`path_resolve`/`path_normalize`/`path_toAbsolute`) ✅ (`NativeRiscvAsmIoStat`/`IoText`/`IoFs`/`IoMkdirs`/`IoSize`/`IoBytes`/`IoDirList`/`IoReadRange`/`IoPath`/`IoResolve`/`IoNormalize`/`IoToAbsolute`/`IoDirDelete` — recursive `delete` ✅ cross **and x86-64** 26/09; `modifiedTime`+`isSymlink` ✅ cross+x86-64 via `NativeRiscvAsmIoMeta`/`RuntimeIoMeta`; `moveTo` ✅ all native via `RuntimeIoMove`/`NativeRiscvAsmIoMove` (`renameat2`); `copyTo` ✅ all native via `RuntimeIoCopy`/`NativeRiscvAsmIoCopy` → **row 13 COMPLETE, no `NAT006` face left** (§497 FIXED); `size()` msg JVM×x86 diverge (§494) | ✅ | `NAT006`/`NAT007` | native cross lane |
| 14 | security family on cross/native (bcrypt/argon2/keystore faces) | ✅ | partial | ❌ `SECN001`/`003`/`004`/`005` | ⏳ | `SECN00x` | security lane |

> **Rows 15 (`orm.*` native) and 16 (`db.*` native) CLOSED 24/09 by the
> gaps-db lane (S5.5)** — the cross (riscv64/aarch64) was the last open cells;
> see the Closed section for the proofs.

## Already at full parity (verified — the review of what is DONE, 24/09)

Measured 4/4 (JVM/Script ≡ Native x86-64 ≡ Native riscv64/aarch64 ≡ JS),
proof = `ConformanceMatrixTest` std* cases + the E2E named per row:

- **Language core:** operators/precedence, content `==`, exceptions as
  String, null-safety narrowing, `if`/`switch` expressions, pattern matching,
  closures, `spawn`/`await`/`poll`/`done`/`cancel`/`selectAny`/`awaitTimeout`,
  channels incl. riscv64/aarch64 (§423, §485), interface default methods
  (§248), generic interfaces + covariant/primitive bridges (§355–357, §486),
  record equality/hashCode by content everywhere (§104b-ii/§114), SEM diagnostics.
- **stdlib core (rows of the stdlib parity table with ✅ on 4 columns):**
  `math` (abs/sign/clamp/min/max/is*/sqrt/lerp/percentage/roundTo/parse*) —
  EXCEPT `pow` (row 10); `strings` (is*, count, capitalize/uncapitalize,
  reverse ASCII, toCamel/Pascal/Snake/Kebab, slugify, escapeHtml/Json,
  whitespace family, dedent, repeat, truncate, indent, pad*) — EXCEPT
  non-ASCII `reverse` (row 11); `encoding` (hex, base64, base64Url, url);
  `net` (all 8); `uuid` (v4, v7, isUuid); `random` (all faces);
  `time` civil-calendar core (isLeapYear, daysInMonth, dayOfWeek,
  daysBetween, isWeekend, isToday) — EXCEPT the new faces' cross golden
  (rows 8/9); `validation` (BR docs, network, card — all faces).
- **Collections** (`List`/`Set`/`Map` full API incl. `getOrDefault`,
  `putIfAbsent`, sort/indexOf/subList) — 4 targets.
- **`json.encode`/`decode<T>`** — 4 targets (Native composes at compile time).
- **`kof.ui`** — colors/widgets/windows on the 4 targets (rule: platform renders).
- **`String.toCharArray`** (row 11, ported 24/09) — 4 targets; array of UTF-16
  code units (astral = high/low surrogate), byte-parity JVM/x86/cross. Proof:
  `KofStringsTest#toCharArrayJvmJsNative` (JVM/JS/x86) +
  `NativeStringToCharArrayCrossTest` (riscv64/aarch64 under qemu).

An entry here only MOVES when its proof is named; the `ConformanceMatrixTest`
runner is re-run at every release-gate execution (condition 1), so a
regression re-opens the row (zero regression, freeze rule 1).

## Closed (proof recorded here when a row empties)

- **Row 2 — `shell.cmd`/`run`/`runWith`/`pipeline`/`ok` (all five faces, all four
  targets)** — closed 25/09 (parity lane + native-cross lane). x86-64: slice A
  landed `run`/`cmd`/`ok` (25/09), slice B landed `runWith` (full contract:
  argv split + chdir + additive `setenv` in the child hook; ctx on the wrapper
  stack — fork copies it, the GC stack scan roots it) and `pipeline`
  (`kof_shell_pipeline`: kernel pipe-chaining, stage-0 stdin `/dev/null`,
  capture = LAST stage only, exitCode = last; JVM oracle `JvmRuntimeCore:446`).
  Cross: `run` (row 1 slice C), `cmd`/`ok` (B2), `runWith` (B1: inherited
  cwd/env byte-parity, non-empty = honest `Result`), `pipeline` (B2 kernel
  chain) — all by the native-cross lane 26/09. Declared micro-divergences
  (R7, in the runtime header + pinned here): native `pipeline` caps at 64
  stages with an honest `Result` (the JVM has no cap) and intermediate-stderr
  goes to `/dev/null` (the JVM pipes-and-never-reads, which deadlocks past
  64 KiB — untestable by contract). The x86 runWith slice also fixed §503
  (misaligned `.data` `.quad` = GC-invisible static roots → sweep freed the
  live 1 MiB drain buffer → OUT==ERR==CHUNK; `.balign 8` is now mandatory on
  every runtime global holding a heap pointer). Proof: `ShellE2ETest`
  `pipelineOnNativeMatchesJvmGolden`/`pipelineHonestFailuresOnNative`/
  `runWithOnNativeMatchesJvmGolden`/`runWithHonestFailuresOnNative` (JVM ==
  native byte-for-byte), `ShellCrossE2ETest` 7/7, `ProcessRun*E2ETest` 6/6+6/6,
  T1–T14 scratch battery; no shell face is gated on any ledger target.
- **Row 6 — `gpu.*` (JS face) + cross golden** — closed 26/09 (lane parity).
  The row hid a REAL gap, not a stale golden: `KofGpu.supportedOn` already
  returned true for the native targets, but the riscv64/aarch64 emit path never
  emitted any `kof_vk_*`/`kof_mv64_*` runtime, so `gpu.available()` failed to
  **link** (`ld: undefined reference to 'kof_vk_available'`). New
  `NativeRiscvAsmGpu` defines the 13 entry points with the honest fallback
  contract of `JvmVkStubRuntime` (available=false, dispatch −1, the `32`/`sp`
  mv faces −6, `failReason` a real KofString), and JS/Script are no longer
  gated by `GPU001` (a declared gap is a tracking state, never an acceptance
  state, `D-FULL-PARITY-050`): `KofGpu.supportedOn` now returns true everywhere,
  JS gets `JsRuntimeGpuSupport` (camelCase fallback exports wired into the
  runtime slicer), and Script degrades through the interpreter's real FFM
  runtime (available=false/dispatch −1 without `libvkchain.so`). Proof:
  `KofGpuCrossTest` **4/4** (riscv64 + aarch64 under qemu, JS embedded runner,
  Script interpreter) + `ConformanceMatrixTest` 14/14 + `KofJsE2ETest` 40/40;
  `StdParityGapAuditTest#gpuUngatedOnAllTargets` pins the removal of `GPU001`.
- **Row 5 — `mq.*` cross + JS golden (riscv64/aarch64)** — closed 26/09 (lane
  parity). The row was STALE: `KofMq.supportedOn` returns `true` for every
  target (the `MQ001` code is a retained record, not a live gate —
  `StdParityGapAuditTest` asserts the unsupported set is empty) and the cross
  golden already executed (`KofMqE2ETest#crossNativeMqQueueAndPubsub`:
  queue/push/pop/queueSize + pub/sub + unsubscribe on riscv64 **and** aarch64
  under qemu, byte-parity with x86-64). The one face with no named proof was
  the **JS queue** (the existing test covered only JS pub/sub), now added:
  `KofMqE2ETest#jsQueuePushPopAndSize` asserts `2\njob-1\njob-2\nnull` on the
  embedded GraalJS runner. Proof: `KofMqE2ETest` **6/6, 0 skipped**
  (JVM/x86/riscv64/aarch64/JS).
- **Row 7 — `observability.*` cross golden + `OBS003`** — closed 26/09 (lane
  parity). The cross cells were STALE/partially measured: the span/trace/
  request-id golden already executed under qemu
  (`KofObservabilityTest#spansCrossArchRiscv64`/`#spansCrossArchAarch64`),
  and the last open cell was the **metrics/health golden on cross**.
  Extended `runCross` (`KofObservabilityTest`) with the four-target contract:
  `health()=="UP"`, `readiness()`/`liveness()`, `counter`/`increment`
  (`1`/`2`/`7`), `gauge 99`, `histogram` (`_count 2`/`_sum 25`) — measured
  byte-for-byte on riscv64 **and** aarch64 under qemu (2/2, `x-ok`). `OBS003`
  (`observability.exportSpans` on native) remains a **declared, intentional**
  R7 JVM-first gap — pinned by
  `StdParityGapAuditTest#observabilityExportSpansGatedOnNative` +
  `DomainGapCodesTest` (OBS003), never a silent stub. Proof:
  `KofObservabilityTest` (JVM/x86/JS/cross golden) + `KofJsE2ETest`.
- **Row 9 — `cache.*`/`config.*`/`log.*` cross (riscv64/aarch64)** — closed
  26/09 (lane parity). Three cross faces were open: **`cache`** — `cache.ttl`
  returned 0 for a missing/no-TTL/expired key where the JVM/x86 oracle returns
  -1 (`NativeRiscvAsmRtB2` `.Lct_miss`; §501), fixed with `KofCacheCrossTest`
  (4/4); **`log`** — the cross lacked the level interpreter and the JVM line
  contract (fatia 2a: `.Llog_parse_level` reads `KOF_LOG_LEVEL`, lazy
  threshold, JVM labels `DEBUG`/`INFO`/`WARN`/`ERROR`) and the UTC timestamp
  `yyyy-MM-dd HH:mm:ss.SSS` (fatia 2b: `.Llog_format_ts` ports the Hinnant civil
  conversion + `kof_time_now()`), with `NativeLogCrossTest` (7/7); **`config`**
  — the last face, compile-refused `CONF001`: now a real cross runtime
  (`NativeRiscvAsmConfig1/2/3` — `kof_env_getc`/`/proc/self/environ`, file
  `key=value` find, lookup `KOF_CONFIG` → env `KOF_<KEY>` → profile
  `kof.<KOF_PROFILE>.config`/`kof.config`, `${key}` interpolation, typed
  `get`/`env`/`has`/`str`/`int`/`long`/`bool`/`required`), with
  `KofConfigCrossTest` (3/3, env+file+profile+interpolation+panic) and the
  `CONF001` gate removed (`KofConfig.supportedOn` true; `DomainGapCodesTest`
  `configOnCrossHasNoGap`). Proof: the three cross tests 14/14 + the four-target
  E2E suites (`NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 110/110, 2 env
  skips) + `KofCacheE2ETest`/`KofLogE2ETest`/`KofConfigE2ETest`/`NativeConfigE2ETest`.
- **Row 8 — `time.*` (new faces + `addDays`/`diffDays` + `collect`)** — closed
  25/09 (lane parity). The cross cell was STALE: `addDays`/`diffDays` were
  already ported to riscv64/aarch64 on 11/09 (`NativeRiscvAsmRtB33`, fatia B33,
  reusing `kdv_valid`/`kdv_epoch`; `aarch64` via the translator), and the "new
  faces" (`todayIso`/`formatDateIso`/`isToday`/`hoursBetween`/`parseDateIso`)
  too. **Measured at the tip:** `KofTimeE2ETest` **44/44 with 0 skipped** under
  the cross toolchain (the existing `...CrossArch` goldens execute, not skip) +
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` stdtime2. The only real open
  cell was JS `time.collect` (`TIME004`) — and §426's gate was a fallback, not
  the end state (`D-FULL-PARITY-050`: an honest gap is never the sanctioned END
  state). It is now a **real** face: `kof_gc_collect_now` is recognized by
  `JsRuntimeOps.isRuntimeOp` (the true root of the old `ReferenceError` — the
  emitter produced a raw call because the mapping was never reached),
  `JsRuntimeTime` exports `kofGcCollectNow`, and `KofJsRunner` exposes
  `kof_platform.gcCollect` → `System.gc()` (exact JVM/SCRIPT semantics).
  Proof: `KofTimeE2ETest#collectJsRunsOnHost` (compiles AND runs) +
  `DomainGapCodesTest.collectOnJsHasNoGap`/`collectOnJvmAndX86HasNoGap` (cross
  arches included) 25/25 + `KofJsE2ETest` 40/40.
- **Row 15 — `orm.*` native (`kof_orm_*`)** — closed 24/09 (gaps-db lane,
  S5.5). All 13 faces (`create`/`migrate`/`count`/`count_where`/`save`/
  `saveAll`/`find`/`all`/`where`/`where_op`/`page`/`delete`/`deleteAll`) run
  byte-identical on JVM + Native x86-64 + riscv64 + aarch64 over **both**
  SQLite and the MySQL wire (the last cross gaps — MySQL `save`/`saveAll`/
  `find`/`all`/`where`/`where_op`/`page` — landed in S5.5, `RtB76`–`RtB81`,
  with helpers `RtB78Helpers`/`RtB80Helpers`/`RtB81Helpers`); JS closed 18/09
  (`KofJsOrmBridge`, same SQL as `JvmOrmRuntime`). Proof: `KofOrmE2ETest`
  82/0F — incl. `crossNativeMariadb{Save,SaveAll,Find,All,Where,Page}MatchesOracles`
  (JVM == x86-64 == riscv64 == aarch64 byte goldens) — + `NativeRiscvDbWireTest`
  41/0F + `NativeRiscvRuntimeSliceRegistryTest` 9/9.
- **Row 16 — `db.*` native query/prepared parity** — closed 24/09 (gaps-db
  lane). The cross now carries the full untyped wire (connect/handshake/
  COM_QUERY/execute/query/scalar, SQLite + MySQL, prepared binds — `RtB62`–
  `RtB73` + `RtB47b`); x86-64 real since F1–F2; JS closed 16/09 (DB001,
  `KofJsDbBridge`). Proof: `KofDbE2ETest` 40/0F + `NativeRiscvDbWireTest` 41/0F.

## Definition of done for EVERY row

1. The face compiles on the target (no gap code emitted).
2. Golden/E2E proof: byte-identical output vs the JVM oracle (cross under
   qemu where applicable), test named in the commit.
3. Parity table in `learn/39-stdlib.md`, `training/idioms/stdlib.md` and the
   namespace chapter under `learn/stdlib/` updated in the SAME commit.
4. Row removed from this ledger in the SAME commit + `full_parity` gate re-run.
