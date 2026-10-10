[English](process.md) | [Português](process.pt_BR.md)

# kof.process — external commands, one-shot

> **Status: JVM + JS ✅ · Native x86-64/riscv64/aarch64 ✅ (`run`, `spawn` +
> handle ops, whole-`Result` printing) · Native MCU/riscv32 = `PROC001`
> (compile-time honest gap, pinned in `DomainGapCodesTest`).**

| Function | Form |
|----------|------|
| `run` | `run(String program, String... args) -> Result` |
| `spawn` | `spawn(String program, String... args) -> Handle` |
| `exit` | `exit(Int code) -> void` |

```kf
val r = process.run("git", "status", "--short")   // argv as varargs — no shell
if (r.exitCode == 0) { println(r.stdout) }        // non-zero exit is DATA
```

- `kof.process` = one-shot command with args you ALREADY have as values; the
  list-shaped/dynamic idiom is [kof.shell](shell.md) (passing a `List` here is
  SEM025 — that is shell's shape).
- `Result` carries `stdout`/`stderr`/`exitCode` in both namespaces.
- `spawn` returns a Handle: `readLine`/`write`/`exitCode`/`kill`/`alive` —
  the same handle ops as [18 — Concurrency](../18-concurrency.md).
- Under `D-FULL-PARITY-050`, subprocesses do not inherit ambient parent file
  descriptors beyond stdin/stdout/stderr. Native marks every descriptor `>2`
  close-on-exec before `run`/`spawn` (`#762`), matching the JVM oracle.
- NEVER build a command string (injection class) — args stay separate values.

**See also:** [kof.shell](shell.md) — pipelines and dynamic argv;
`training/idioms/stdlib.md` — the BAD/GOOD table.
