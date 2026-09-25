[English](shell.md) | [Português](shell.pt_BR.md)

# kof.shell — argv dinâmico e pipelines

> **Status: JVM + JS ✅ (paridade byte a byte via `runWith`) · Native x86-64 ✅
> superfície completa (`run`/`cmd`/`ok`/`runWith`/`pipeline` — D-FULL-PARITY-050
> linha 2 fechada; runWith = split do argv + chdir + `setenv` aditivo; pipeline
> = encadeamento de pipes do kernel, captura do último estágio, goldens em
> paridade com o JVM) · cross riscv64/aarch64 ✅ superfície completa (runWith:
> cwd/env herdados byte a byte; cwd/env não-vazio = `Result` honesto).**

| Função | Forma |
|--------|-------|
| `cmd` | `cmd(String program, List<String> args) -> List<String>` |
| `run` | `run(String program) -> Result` · `run(String program, List<String> args) -> Result` |
| `runWith` | `runWith(List<String> argv, String cwd, Map<String,String> env) -> Result` |
| `pipeline` | `pipeline(List<List<String>> stages) -> Result` |
| `ok` | `ok(result) -> Bool` |

```kf
// argv montado em runtime — a forma do shell (process.run rejeita List: SEM025)
val parts = listOf("commit", "-m", msg)
val r = shell.run("git", parts)
if (!shell.ok(r)) { throw "git falhou: " + r.stderr }

// pipeline — estágios ligados com pipes reais, sem string de shell
val out = shell.pipeline(listOf(listOf("echo", "a"), listOf("wc", "-c")))

// controle total: cwd + env
val r2 = shell.runWith(listOf("make"), "/work", mapOf("CC", "clang"))
```

- `shell` é o idioma DINÂMICO (argv montado em runtime); para chamadas
  estáticas prefira [kof.process](process.pt_BR.md).
- Nenhuma string `sh -c "..."` — classe de injeção; a plataforma liga os
  pipes.

**Veja também:** [kof.process](process.pt_BR.md) — one-shot com varargs.
