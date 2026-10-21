[English](debug-adapter.md) | [Português](debug-adapter.pt_BR.md)

# DEBUG-ADAPTER.md — kof-debug (DAP Debug Adapter)

**Status:** JVM (raw JDWP, no jdk.jdi) + NATIVE (console gdb + DAP↔GDB/MI, X7-3/X7-4) implemented and validated — console gdb (X7-3, `--break`/`--output`) and `kof debug --dap --target native` bridging DAP to the real gdb/MI2 (X7-4 `bda631a7`: `KofGdbMi` + `KofDebugNativeDap`, `KofDebugNativeDapTest`); sources in `stackTrace`/breakpoints are always the `.kf`; no gdb = honest DAP error naming the tool (R6); JS = honest refusal (the target runs on the EMBEDDED engine — there is no node/inspector to attach to). **20/09 (X7-5):** attach is REAL on JVM (`--dap --attach <pid>`, raw JDWP into a live VM) and Native (`--dap --attach <pid>`, gdb `-p`); the JVM client was rebuilt against the JDK 25 wire (`known-bugs.md §376`) and the full launch/attach conversations now have E2E tests (`KofDebugJvmTest`, `KofDebugAttachTest`)`. **20/09 (Phase 7):** JVM `next`/`stepIn`/`stepOut` + `evaluate` (`KofDebugJvmStepTest`) and `pause` + `setExceptionBreakpoints` (`KofDebugJvmExceptionTest`); Native `pause` + `setExceptionBreakpoints` (`KofDebugNativeDapTest`)
**Date:** August 27, 2026 (updated 20/09 with the Native faces)
**Version:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Objective

The `kof-debug` component exposes Kof execution via **DAP** (Debug Adapter
Protocol) — the same protocol used by modern editors (VS Code, Neovim,
IntelliJ, Kof Editor).

Do not create a proprietary protocol.

## 2. Responsibilities

- launch programs (`launch`);
- attach to processes (`attach` — ✅ 20/09: JVM `--dap --attach <pid>` and Native `--dap --attach <pid>`; JS = honest gap);
- execution control: continue, pause, step over/into/out, terminate (`restart` is **not** implemented — see §3.3);
- breakpoints (source; later conditional, hit count, exception);
- stack traces, scopes, locals, arguments, fields;
- exception events;
- variable inspection with Kof types;
- expression evaluation (future — with the type system, never raw Java/JS).

## 3. Interface

```text
kof-debug (DAP over stdio — Content-Length framing)
    ↓
JVM: launch java -agentlib:jdwp + JDWP client (raw wire protocol)
Native: build ELF with DWARF + gdb (console: `--target native` with `--break`/`--output`;
        editor: `--dap --target native` translates DAP -> GDB/MI 2, `KofGdbMi`/`KofDebugNativeDap`)  [✅ 20/09]
JS: honest refusal — the JS target runs on the embedded Graal engine; there is no
    node/inspector to launch or attach (roadmap §19.5 face 7 stays open for a future
    engine inspector, never a fake bridge)
```

The CLI (`kof debug`) is only an interface — the logic lives in the adapter.
The JDWP client is implemented over the **raw wire protocol** (without
depending on the `jdk.jdi` module) to keep the tooling self-contained.

## 3.1 Implemented DAP flow (JVM)

```text
initialize            → capabilities (configurationDone, terminate)
launch                → compiles (JVM + debug info), free port,
                        java -agentlib:jdwp=transport=dt_socket,server=y,
                        suspend=y,address=<port>, connects and registers the
                        ClassPrepare of Default.Main (suspend ALL)
setBreakpoints        → registers the Kof lines (applied on ClassPrepare)
setExceptionBreakpoints → EventRequest.Set kind 4 + ExceptionOnly modifier 8
                        (refType 0 = all; caught/uncaught faces)
configurationDone     → VM.Resume
pause                 → ThreadReference.Suspend (11,2) on every USER thread —
                        the JDWP agent's own threads (name starts with "JDWP")
                        are skipped: suspending the transport thread freezes the
                        protocol itself (measured). `stopped` with reason `pause`
[event] stopped       → breakpoint hit (thread + reason)
stackTrace            → Kof frames: function name, file, line
continue              → VM.Resume
disconnect/terminate  → VM.Dispose + process kill + cleanup
```

## 3.1b Implemented DAP flow (Native, via GDB/MI — `--dap --target native`)

```text
initialize            → capabilities (configurationDone, terminate)
launch                → compiles NATIVE with debug info (DWARF: line table +
                        DIEs — X7-1/X7-2), starts `gdb -q --interp mi2` with
                        `directory <source dir>`; missing gdb = success:false
                        naming the tool (never a stack mid-stream)
setBreakpoints        → -break-insert -f -- <file.kf>:<line> (verified from
                        the real bkpt line the DWARF resolved)
setExceptionBreakpoints → -break-insert -f -- kof_throw_string (the Kof
                        runtime's own throw chain, NOT C++ exceptions, so gdb's
                        catch-throw does not apply); the caught/uncaught
                        refinement is JVM-only → honest verified:false (R6)
configurationDone     → -exec-run --all
pause                 → -exec-interrupt --all → `stopped` with reason `pause`
[event] *stopped      → DAP stopped (breakpoint-hit/entry/end-stepping mapped);
                        exit-code = exited + terminated
stackTrace            → -stack-list-frames; source.path is ALWAYS the .kf —
                        the whole point of the front (the editor never sees asm)
variables             → -stack-list-variables --simple-values over the real
                        DW_TAG_variable entries (name + DW_OP_fbreg +
                        DW_AT_type) the compiler already emits on the 3 native
                        arches — measured 20/09 with objdump on the ELF/.s;
                        lowered temporaries (tmp/cap/lambda$) are filtered out
                        of the DIEs, so the editor shows Kof names only
evaluate              → -data-evaluate-expression; unknown symbol = the gdb
                        error passed through, never an invented value (R6)
disconnect/terminate  → -gdb-exit + kill + build dir cleanup
```

MI transport detail that cost a debugging session (20/09): records are
token-PREFIXED (`2^done,...`) — the record TYPE is the first NON-digit char;
classifying on char 0 silently drops every synchronous reply (they only
surface as 5s timeouts).

## 3.2 JDWP (JDK 25) particularities discovered during implementation

- JDK 25 event kinds: `VMStart=90`, `VMDeath=99`, `ClassPrepare=8`
  (the classic spec values — 0, 15, 6 — are not used by HotSpot);
- `ClassMatch` is modifier **5** (modifier 1 is `Count` — a mistake here
  makes the request be accepted but the event never fires);
- `LocationOnly` is modifier **7**, with the location `tag(1) + typeID +
  methodID + codeIndex` (the tag is mandatory — without it the JVM responds
  `INVALID_OBJECT`);
- `Method.LineTable` returns `[codeIndex(long), lineCode(int)]` per
  entry (order long/line, not line/codeIndex);
- `ReferenceType.Methods` returns `methodID + name + signature +
  modifiers` (4 fields);
- `ThreadReference` is command set **11** (`Frames`=6, `FrameCount`=7);
  `StackFrame` is **16** (`GetValues`=1); `StringReference` is **10** (`Value`=1)
  (measured against `jdk.jdi/.../JDWP.java` from the JDK's own `src.zip`);
- HotSpot rejects `maxFrames` LARGER THAN THE REAL stack size in
  `ThreadReference.Frames` with `INVALID_LENGTH` (504) — ask `FrameCount`
  first and clamp (there is no "5 frames" limit);
- the event handler runs outside the event loop (dispatch on a thread) —
  JDWP commands emitted by the handler need the loop to receive
  replies (without it: timeout deadlock);
- `Composite` events have `suspendPolicy + eventCount` before the kinds, and
  each event is **`[kind (byte)][requestID (int)]`** — kind FIRST (JDWP.java
  7827; reading requestID first shifts the whole body);
- `IDSizes` answers **5 sizes, not 6** on JDK 25+ (`argIDSize` was removed);
  reading the 6th int steals 4 bytes of the next packet and desyncs the
  entire stream from the handshake on (measured byte-by-byte, §376);
- `VM.ClassesBySignature (1,2)` is BROKEN on JDK 25.0.4 (answers `count=0`
  plus garbage, then stalls; `jdb` never uses it) — resolve classes via
  `VM.Classes (1,3)` ([tag][ref][signature][status]);
- `Method.VariableTable (6,2)` replies `{argWords, slotCount,
  slots[start(long), name, sig, length, slot]}` — there is NO argument list;
  calling it on a native frame returns `NATIVE_METHOD` (511) — handle it,
  never swallow others.
- `Method.LineTable (6,1)` / `Method.VariableTable (6,2)` on a native or
  abstract frame returns `NATIVE_METHOD` (511, measured on the top frame
  `Thread.sleep` of a paused thread). A stack trace must report that ONE frame
  as unknown (name `?`, line `-1`) and keep the Kof frames — aborting the whole
  `stackTrace` because a JDK frame has no debug info killed the session.

## 3.3 Current limits (post-X7-5)

- `stackTrace` returns the requested depth (clamped by `FrameCount`) with
  real method name and Kof line per frame;
- `scopes`/`variables` return **real per-frame locals** (VariableTable +
  `StackFrame.GetValues`, formatted by Kof type);
- `verified: false` only until the class loads — at `ClassPrepare` the
  breakpoint is placed through LineTable and hits fire `stopped`;
- `next`/`stepIn`/`stepOut` — ✅ JVM 20/09 (JDWP `SingleStep` request kind 1,
  Step modifier kind 10: size LINE, depth over/into/out; the landing fires
  `stopped` with reason `step`) and ✅ Native since X7-4;
- `evaluate` — ✅ JVM 20/09 (a local-variable **name** of the frame, via the
  same VariableTable/GetValues path; JDWP has no expression evaluator, so
  anything else is an honest `success:false` naming the limitation) and ✅
  Native since X7-4 (gdb evaluates full expressions);
- `pause` — ✅ JVM 20/09 (ThreadReference.Suspend 11,2 on every user thread,
  never the JDWP agent's own) and ✅ Native 20/09 (`-exec-interrupt --all`);
- `setExceptionBreakpoints` — ✅ JVM 20/09 (Exception event kind 4 + ExceptionOnly
  modifier 8; `caught`/`uncaught` faces) and ✅ Native 20/09 (breakpoint on
  `kof_throw_string`, the runtime's own throw chain; the caught/uncaught
  refinement is JVM-only and is an honest `verified:false`, never a filter that
  silently over-breaks — R6);
- `restart` (and any other unimplemented request) — ✅ 21/09 (§428): the
  session answers an **honest `success:false`** naming the command
  (`unsupported request: <command>`), never `success:true` with an empty body
  (Q7). JVM (`KofDebugJvmSession`) and Native (`KofDebugNativeDap`). `restart`
  is a declared limit; `exceptionInfo`/`setVariable`/`completions`/
  `disassemble`/`readMemory` fall in the same honest bucket until implemented;
- remaining: JS stays an honest gap (embedded engine, no inspector).

> A `LocalVariableTable` wart affects local reads on a line that declares a
> variable: the JVM backend emits every local with `Start=0`/method-length, so a
> just-declared local is "visible" but unassigned and JDWP answers
> `INVALID_SLOT` (35) for the whole batch — the adapter retries per slot and
> omits only the unreadable one (never fakes a value); root cause catalogued
> §385 (JVM backend lane).

## 4. Runtime types

The adapter translates backend representations to Kof types:

| Kof | JVM | Native | JS |
|-----|-----|--------|-----|
| `List<User>` | ArrayList | kof list | Array |
| `User` | User.class | struct | object |
| `String` | java.lang.String | KofString | string |

The user always sees the Kof type.

## 5. Phases

- Phase 3 (MVP): JVM launch + breakpoints by Kof line + stack — ✅
- Phase 7: per-frame locals (`StackFrame.GetValues`), stepping, verified
  breakpoints, exception breakpoints, evaluation with the type system
  (Native: locals/scopes/stepping/evaluate landed X7-4/X7-5; JVM: locals +
  verified breakpoints landed X7-5, **stepping (`next`/`stepIn`/`stepOut`) +
  `evaluate` landed 20/09**, and **`pause` + `setExceptionBreakpoints` landed
  20/09** — the JVM and Native faces of Phase 7 are closed; JS remains an
  honest gap)
- ✅ 20/09: Native (DWARF) — console + DAP<->GDB/MI (X7-3/X7-4)
- ✅ 20/09 (X7-5): attach on JVM + Native; per-frame locals and multi-frame
  stack landed with it (ahead of Phase 7); JS stays an honest gap
  (embedded engine, no inspector)
- ✅ locals in DWARF: DW_TAG_variable + DW_OP_fbreg + DW_AT_type on the 3
  native arches (pre-existing since the fatia-2 work; RE-MEASURED 20/09 with
  objdump after this doc briefly claimed the opposite — shapes measured,
  never assumed, including by this lane)
