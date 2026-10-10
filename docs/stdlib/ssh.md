[English](ssh.md) | [Português](ssh.pt_BR.md)

# `kof.ssh` — remote exec over `kof.process`

**Status:** v1 implemented (21/09, `2cd4257a`, universal plan Stage 2 row 2.3) ·
**Source:** `KofSsh.java` (dispatch) + `ExpressionSshCallLowerer` (gates/lowering) ·
**Tests:** `SshE2ETest` (12) + `SshCrossE2ETest` (2) · **Design record:**
`docs/architecture/UNIVERSAL-PLATFORM-VISION.md` §6.1 (option **B** — "over
`kof.process`/FFI"; this is the process path, `libssh` not needed for the MVP)

## What it is

One typed idiom for running a command on a remote host — build the argv, run it,
gate on exit code — **without ever stringifying a command into `sh -c`**. It is
sugar over `kof.process`: the `Result` type IS `kof.process`'s `Result` (one
shape, never a fork), and `run` lowers onto the same process layer as
`shell.run` (`kof_shell_runwith`). Kof has no backtick, no `$()` — the function
form is the answer.

`host` is any ssh target (`"user@host"`, `"host"`, an alias). The call is
**non-interactive and bounded**: BatchMode + a 5 s connect timeout, so it can
neither block on a password prompt nor hang forever.

## Real API (measured in the compiler — 0.5.0-beta)

```kof
var r = ssh.run("user@host", "uname -a")     // kof.process.Result
if (ssh.ok(r)) {
    println(r.stdout)
}

var argv = ssh.cmd("user@host", "df -h")     // ["ssh","-o","BatchMode=yes",
                                             //  "-o","ConnectTimeout=5",host,command]
println(argv.get(6))                         // the command, ONE element
```

| Call | What it does |
|---|---|
| `ssh.cmd(host, command)` | builds the argv `List<String>` `["ssh","-o","BatchMode=yes","-o","ConnectTimeout=5", host, command]` — always a **list**, never a string |
| `ssh.run(host, command)` | runs it, returns `kof.process.Result` (`exitCode`/`stdout`/`stderr`) |
| `ssh.ok(result)` | `exitCode == 0` as a `Bool` (pure field/compare IR — `Result` carries no methods) |

A connection or spawn failure is **not** an exception: `run` returns an honest
`Result` (`exitCode == 255` from ssh, or `-1` when the process cannot be
spawned), never a throw and never a hang.

## The security property (pinned by golden)

`cmdNeverConcatenatesIntoShellString`: the **host and the command stay ONE argv
element each**, so a value with spaces or metacharacters
(`ssh.cmd("h; rm -rf /", "echo a b|c && d")`) reaches `ssh` literally — the
`sh -c` injection class is structurally impossible in this API. `cmd` never
concatenates into a shell string.

## Honest scope per target (R6 — never silent)

| Face | JVM | JS | Native |
|---|---|---|---|
| `cmd` / `run` / `ok` | ✅ real (lowers onto `kof_ssh_argv`/`kof_ssh_run` → `kof_process_run`) | ✅ real (byte-parity with JVM) | ✅ real on x86-64 (`RuntimeSsh`) and riscv64/aarch64 (`NativeRiscvAsmSsh`), 26/09 — byte-parity with JVM; only the freestanding MCU/riscv32 keeps `PROC001` |
| unknown member (`ssh.foo`) | ✅ `SEM025` | — | — |

Native is real on the host targets x86-64/riscv64/aarch64 (26/09, `SshE2ETest` 12 +
`SshCrossE2ETest` 2); `PROC001` is now only the freestanding MCU/riscv32 — the same
cell as `kof.process`/`kof.shell` (R7 honest scope), never a silent stub (R6).

## BAD → GOOD

| ❌ BAD | ✅ GOOD | Why |
|---|---|---|
| `shell.run("ssh", listOf(host, "sh -c '" + cmd + "'"))` | `ssh.run(host, cmd)` | argv-as-list kills the injection class |
| `if (r.exitCode == 0) ...` scattered | `if (ssh.ok(r)) ...` | intention, not mechanism |
| a one-shot manual `ssh` argv each time | `ssh.cmd(host, command)` | one shape, tested |
| a hanging/interactive ssh call | `ssh.run` (BatchMode + ConnectTimeout) | bounded by construction |

## See also

- `docs/stdlib/shell.md` — the sibling idiom over `kof.process` (same `Result`)
- `docs/architecture/UNIVERSAL-PLATFORM-VISION.md` §6.1 (SSH classification)
- `docs/backend-parity.md` — `kof.ssh` rows in the namespace table + gap table
