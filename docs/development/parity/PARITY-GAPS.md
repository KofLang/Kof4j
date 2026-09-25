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
| 1 | `process.run`/`spawn`/`exit` | ✅ | ✅ x86 `run`/`exit` (`spawn` = slice B, `PROC001`; whole-record `println(r)`/`"x"+r` = `PROC001`, access `.stdout`/`.stderr`/`.exitCode`) | ✅ cross `run` 26/09 (`spawn` = slice B, `PROC001`) | ✅ (KofJsRunner) | `PROC001` (spawn + whole-record print) | native-cross lane (run x86 ✅ 25/09, cross ✅ 26/09; `spawn` = slice B) |
| 2 | `shell.cmd`/`run`/`runWith`/`pipeline`/`ok` | ✅ | ✅ x86 `run`/`cmd`/`ok` (`runWith`/`pipeline` = slice B) | ✅ cross `run`/`cmd`/`ok`/`runWith` 26/09 (`pipeline` = slice B, `PROC001`; runWith cwd/env não-vazio = Result honesto) | ✅ (host runner) | `PROC001` (pipeline + x86 runWith) | native-cross lane (x86 ✅ 25/09, cross ✅ 26/09) |
| 3 | `ssh.cmd`/`run`/`ok` | ✅ | ✅ x86 + riscv64/aarch64 26/09 | ❌ | ❌ | `PROC001` (MCU/riscv32; JS sem dispatch) | native-cross lane (nativo ✅ 26/09) |
| 4 | media: `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record`/`list` | ✅ | ❌ | ❌ | ❌ | `MEDIA001`/`MEDIA003` | media front |
| 5 | `mq.*` | ✅ | partial (`MQ001` faces) | ⏳ golden | ⏳ | `MQ001` | infra lane |
| 6 | `gpu.*` (JS face) + cross golden | ✅ | ✅ | ⏳ golden | ❌ `GPU001` | `GPU001` | gpu/native lanes |
| 7 | `observability.*` cross golden + `OBS003` | ✅ | ✅ x86 | ⏳ golden | ⏳ (spans ✅, OBS003 pinned) | `OBS003` | obs lane |
| 9 | `cache.*`/`config.*`/`log.*` cross golden; `log` interpreter | ✅ (log ⏳ interp) | ✅ x86 | `cache.*` ✅ 26/09 (`KofCacheCrossTest`, riscv64+aarch64 — `cache.ttl` −1 parity fixed, §501); `log` ✅ timestamp+nível+rótulo JVM 26/09 (`NativeLogCrossTest` 7/7); `config` ❌ `CONF001` | ✅ | `CONF001` | lane parity (cache ✅ 26/09) / stdlib lane |
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
