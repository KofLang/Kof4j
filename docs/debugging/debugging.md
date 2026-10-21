[English](debugging.md) | [Português](debugging.pt_BR.md)

# DEBUGGING.md — Kof Debugging (usage view)

**Status:** DAP debugging on **JVM and Native** (`kof debug`,
`kof debug --dap --target native`), batch `--break` on Native, `--attach` on
both; JS = honest gap (embedded engine — see `debugging-js.md`)
**Date:** September 20, 2026
**Version:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Experience

Debugging Kof is debugging Kof — on any target:

```text
  40 | User find(Int id) {
  41 |     var user = repository.find(id)
● 42 |     return user
  43 | }
```

When it stops:

```text
CALL STACK

UserService.find       UserService.kf:42
UserController.get     UserController.kf:18
main                    Application.kf:7
```

```text
VARIABLES

id      Int        42
user    User
  name             "Mel"
  active           true
```

The user never needs to know JVM bytecode, assembly or JavaScript.

## 2. Commands

```bash
kof debug app.kf                 # ✅ JVM (DAP server over stdio)
kof debug --dap app.kf           # JVM DAP explicit (default is the same server)
kof debug --dap --target native app.kf # ✅ X7-4 (`bda631a7`): DAP bridged to real gdb/MI2
kof debug --target native app.kf # ✅ X7-3 (`cfa67238`): builds the ELF with Kof DWARF and\                                 #    delegates to the target's gdb (`-x` command file, `-iex set
                                 #    directories` to the Kof source dir) — breakpoints on
                                 #    `Main.kf:2`, never on the mangle
kof debug --target native --break 4 --output out app.kf # ✅ X7-3 slice 2 (`64114449`): scriptable
                                 #    batch session (gdb `-batch`, `break Main.kf:N`, `run`, `bt`);
                                 #    `--output <dir>` keeps the ELF; both refuse on the JVM DAP
kof debug --attach <pid>         # ✅ X7-5 (`bda631a7`): JVM = raw JDWP into a live VM (the
                                 #    debuggee survives disconnect); Native = gdb `-p`
kof debug --target js app.kf     # honest gap: the JS target runs on the EMBEDDED engine
                                 #    (no devtools protocol yet) — diagnostic, not silence
kof build app.kf --debug         # extra metadata (default: debug info on)
kof build app.kf --release
```

The session compiles with debug metadata, launches the JVM with
`-agentlib:jdwp` (suspend=y) and responds to the DAP protocol.

## 3. Capabilities

**Implemented MVP (JVM target — Phase 3):**

- launch (compiles with debug metadata + launches the JVM with JDWP)
- breakpoints by Kof line (`UserService.kf:42`)
- `stopped` event when a breakpoint is hit
- stack traces with Kof names and lines (via LineNumberTable)
- `continue` and `disconnect`

**Implemented beyond the JVM MVP (measured in the DAP handlers 20/09):**

- `next` / `stepIn` / `stepOut`, `scopes` / `variables` and `evaluate` — ✅ on
  Native (DAP↔gdb/MI) and, since 20/09, also on the **JVM** DAP (JDWP
  `SingleStep`; `evaluate` resolves a local **name** — JDWP has no expression
  evaluator, so anything else is an honest refusal)
- `pause` and `setExceptionBreakpoints` — ✅ JVM + Native 20/09 (JVM: JDWP
  `ThreadReference.Suspend` over the user threads — never the agent's own — and
  the Exception event; Native: `-exec-interrupt --all` and a breakpoint on
  `kof_throw_string`, with the caught/uncaught refinement an honest
  `verified:false`)
- attach (`--attach <pid>`) on JVM and Native — ✅ X7-5
- ~~Native (DWARF — Phase 5)~~ ✅ **X7-3 landed 20/09** (`cfa67238`, `KofDebugNativeTest`);
  JS (source maps — Phase 6) = honest diagnostic today (embedded engine)

**Still planned (Phase 4 — see `debugger-architecture.md`):**

- Phase 4: Kof Editor UI (the DAP server — JVM + Native — is ready)

## 4. Integration

```text
Kof Editor
    ├── LSP ────► Kof Language Server (diagnostics, symbols, hover)
    └── DAP ────► kof-debug
                      ├── JVM (JDWP)
                      ├── Native (DWARF ↔ gdb/MI)
                      └── JS (honest gap — embedded engine)
```

LSP and DAP do not mix: LSP = code; DAP = execution.

## 5. State

- Phase 1 (DebugInfo in the IR) — ✅
- Phase 2 (JVM: SourceFile, LineNumberTable, LocalVariableTable) — ✅
- Phase 3 (`kof-debug` MVP: raw DAP + JDWP) — ✅
  - DAP requests: `initialize`, `launch`, `attach`, `setBreakpoints`,
    `setExceptionBreakpoints`, `configurationDone`, `continue`, `pause`,
    `next`, `stepIn`, `stepOut`, `threads`,
    `stackTrace`, `scopes`, `variables`, `evaluate`, `disconnect`
  - `stopped` event when a Kof breakpoint is hit
  - call stack with Kof functions, file and line (via LineNumberTable)
- Phase 5 (Native) — ✅ shipped as the DAP↔gdb/MI2 bridge
  (`kof debug --dap --target native`, `bda631a7`; guarantees measured in
  `debug-adapter.md`), including `next`/`stepIn`/`stepOut`, `scopes`,
  `variables` and `evaluate`. Phase 6 (JS) — honest refusal (embedded engine,
  no node/inspector — never a fake bridge; see `debugging-js.md`). Phase 4
  (Kof Editor UI) and the Phase 7 refinements — see
  `debugger-architecture.md`.

## 6. Measuring a landed fix — the stale-jar trap (lesson 20/09)

When you verify a compiler fix through the CLI jar, the jar must be **provably
current**: an incremental `mvn package -pl kof-cli -am` can leave the shaded
`dev/kof/compiler/*` entries pointing at an older build, so you measure the
**old compiler and believe the fix is absent** (this happened with §368 — the
`FieldAssignabilityPhantomE2ETest` 8/8 was right, the jar was a ghost). Rule:
rebuild with `mvn clean package -DskipTests`, and when in doubt compare the
class inside the jar with the module output
(`unzip -p <cli.jar> dev/kof/compiler/Foo.class | md5sum` vs
`md5sum kof-compiler/target/classes/.../Foo.class`) — **identical bytes or you
are not measuring the tip.**
