[English](shell.md) | [Português](shell.pt_BR.md)

# kof.shell — dynamic argv and pipelines

> **Status: JVM + JS ✅ (byte parity through `runWith`) · Native x86-64:
> `run`/`cmd`/`ok`/`runWith` ✅ — `runWith` = full contract (argv split + chdir
> + additive `setenv` in the child hook; D-FULL-PARITY-050 row 2 slice B,
> JVM-parity goldens), `pipeline` = `PROC001` · cross riscv64/aarch64:
> `run`/`cmd`/`ok`/`runWith`/`pipeline` ✅ (runWith: inherited cwd/env
> byte-parity; a non-empty cwd/env is an honest `Result` failure).**

| Function | Form |
|----------|------|
| `cmd` | `cmd(String program, List<String> args) -> List<String>` |
| `run` | `run(String program) -> Result` · `run(String program, List<String> args) -> Result` |
| `runWith` | `runWith(List<String> argv, String cwd, Map<String,String> env) -> Result` |
| `pipeline` | `pipeline(List<List<String>> stages) -> Result` |
| `ok` | `ok(result) -> Bool` |

```kf
// argv built at runtime — the shell's shape (process.run rejects a List: SEM025)
val parts = listOf("commit", "-m", msg)
val r = shell.run("git", parts)
if (!shell.ok(r)) { throw "git failed: " + r.stderr }

// pipeline — stages wired with real pipes, no shell string
val out = shell.pipeline(listOf(listOf("echo", "a"), listOf("wc", "-c")))

// full control: cwd + env
val r2 = shell.runWith(listOf("make"), "/work", mapOf("CC", "clang"))
```

- `shell` is the DYNAMIC idiom (argv assembled at runtime); for static calls
  prefer [kof.process](process.md).
- No `sh -c "..."` strings anywhere — injection class; the platform wires
  the pipes.

**See also:** [kof.process](process.md) — one-shot with varargs.
