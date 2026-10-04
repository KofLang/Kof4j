[English](shell.md) | [Português](shell.pt_BR.md)

# `kof.shell` — idiomatic shell over `kof.process`

**Status:** v1 implemented (18/09, `34e4344f`, universal plan Stage 2 row 2.2) ·
**Source:** `KofShell.java` (dispatch) + `ExpressionShellCallLowerer` (gates/lowering) ·
**Tests:** `ShellE2ETest` (21) + `ShellCrossE2ETest` (7) · **Design record:** `docs/shell-plan.md`

## What it is

One typed, composable idiom for driving OS commands — run, capture, gate on exit
code, chain pipelines — **without ever stringifying a command into `sh -c`**. It is
sugar over `kof.process`: the `Result` type IS `kof.process`'s `Result` (one shape,
never a fork), and `run` lowers verbatim onto `kof_process_run`. Kof has no backtick,
no `$()`, no `|`/`&&` shell-infix operators (adding them is a grammar change — rule 6);
the function form is the signed-off answer (Q1–Q3, maintainer poll 18/09).

## Real API (measured in the compiler — 0.5.0-beta)

```kof
import kof.shell

var r = shell.run("git", listOf("status", "--short"))   // kof.process.Result
if (shell.ok(r)) {
    println(r.stdout)
}

var argv = shell.cmd("wc", listOf("-l"))
var n = shell.run(argv.get(0), listOf("-l")).stdout.trim()

var out = shell.pipeline(listOf(listOf("echo", "one two three"),
                                               listOf("wc", "-w"))).stdout  // all targets

var build = shell.runWith(shell.cmd("make", listOf("-j4")), "/src",
                          mapOf("CC", "clang"))                              // all targets
```

| Call | What it does |
|---|---|
| `shell.cmd(program, args)` | builds the argv `List<String>` `[program] + args` — always a **list**, never a string; feed it to `run` via `argv.get(0)` + the rest |
| `shell.run(program)` / `shell.run(program, args)` | runs the command, returns `kof.process.Result` (`exitCode`/`stdout`/`stderr`) |
| `shell.ok(result)` | `exitCode == 0` as a `Bool` (pure field/compare IR — `Result` carries no methods) |
| `shell.pipeline(listOf(argv, ...))` | chains stdout→stdin between stages, returns the last stage's `Result` (JVM: `kof_shell_pipeline` pump threads; JS: `KofJsProcessBridge` chain + pump threads; Native x86-64/riscv64/aarch64: kernel pipe-chaining, `kof_shell_pipeline`/`NativeRiscvAsmPipeline` — same contract, byte-parity) |
| `shell.runWith(argv, cwd, env)` | runs argv **in `cwd`** with an **additive** env (`""` cwd inherits the process dir; the map's keys override inherited ones — never a silent env wipe). Spawn errors and empty argv return an **honest** `Result` (`stderr` set, `exitCode == -1`) on every target |

## The security property (pinned by golden)

`argvIsNeverConcatenatedIntoShellString`: an argument containing shell metacharacters
(`"a b|c && d"`) survives as **ONE argv element** — the injection class of "build a
command string, hand it to `sh -c`" is structurally impossible in this API. The repo's
own scripts that concatenate command strings re-clean for nothing; `shell` is the
good path.

## Honest scope per target (R6 — never silent)

| Face | JVM | JS | Native |
|---|---|---|---|
| `cmd` / `run` / `runWith` / `ok` | ✅ real (`kof_process_run`; `runWith` via `kof_shell_runwith` — cwd + additive env, honest `-1` Results) | ✅ real (byte-parity with JVM — 5 pinned cases + `runWith` cwd/env/failures) | ✅ real on x86-64 and riscv64/aarch64 (`kof_shell_runwith` / `NativeRiscvAsmShell`, 26/09 — byte-parity with JVM); only the freestanding MCU/riscv32 keeps `PROC001` |
| `pipeline` | ✅ real (pump-thread chain, golden `echo|wc`) | ✅ real (host chain + pump threads, 20/09 — byte-parity pinned) | ✅ real on x86-64 (`kof_shell_pipeline`, kernel pipe-chaining) and riscv64/aarch64 (`NativeRiscvAsmPipeline`, 26/09 — byte-parity); only the freestanding MCU/riscv32 keeps `PROC001` |
| unknown member (`shell.foo`) | ✅ `SEM025` | — | — |

A non-zero exit code is **not** an exception: `failingCommandPropagatesExitCodeNotException`
pins `Result.exitCode` as data.

## Residual faces (not debt of v1 — signed-off scope ends here)

- Native `PROC001` is now only the freestanding MCU/riscv32 target; the host
  targets x86-64/riscv64/aarch64 run `cmd`/`run`/`runWith`/`ok`/`pipeline` for
  real (26/09, `ShellE2ETest` 21 + `ShellCrossE2ETest` 7).
- v2 addons excluded by the Q3 poll: glob, `~` expansion, `>` redirection —
  **not** in v1, design-only in the plan.

## BAD → GOOD

| ❌ BAD | ✅ GOOD | Why |
|---|---|---|
| `process.run("sh -c \"echo a | wc\"")` | `shell.pipeline([["echo","a"],["wc"]])` | argv-as-list kills the injection class |
| `if (r.exitCode == 0) ...` scattered | `if (shell.ok(r)) ...` | intention, not mechanism |
| manual `Result` re-plumbing in JS | shared `kof.process.Result` | one shape, never a fork |

## See also

- `docs/shell-plan.md` (design decisions Q1–Q3, wiring map, slices 2.2.0–2.2.4)
- `docs/backend-parity.md` — `kof.shell` rows in the namespace table + gap table
- `kof.process` face on Native: real on x86-64/riscv64/aarch64, `PROC001` only on the freestanding MCU/riscv32 (backend-parity, Known Gaps)
