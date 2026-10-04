[English](process.md) | [Português](process.pt_BR.md)

# kof.process — comandos externos, one-shot

> **Status: JVM + JS ✅ · Native x86-64/riscv64/aarch64 ✅ (`run`, `spawn` +
> ops de handle, impressão do `Result` inteiro) · Native MCU/riscv32 = `PROC001`
> (gap honesto em compile-time, travado no `DomainGapCodesTest`).**

| Função | Forma |
|--------|-------|
| `run` | `run(String program, String... args) -> Result` |
| `spawn` | `spawn(String program, String... args) -> Handle` |
| `exit` | `exit(Int code) -> void` |

```kf
val r = process.run("git", "status", "--short")   // argv como varargs — sem shell
if (r.exitCode == 0) { println(r.stdout) }        // exit não-zero é DADO
```

- `kof.process` = comando one-shot com args que você JÁ tem como valores; o
  idioma de lista/dinâmico é [kof.shell](shell.pt_BR.md) (passar `List` aqui é
  SEM025 — essa é a forma do shell).
- `Result` carrega `stdout`/`stderr`/`exitCode` nos dois namespaces.
- `spawn` devolve um Handle: `readLine`/`write`/`exitCode`/`kill`/`alive` —
  as mesmas ops de handle do [18 — Concorrência](../18-concurrency.pt_BR.md).
- NUNCA monte uma string de comando (classe de injeção) — args ficam valores
  separados.

**Veja também:** [kof.shell](shell.pt_BR.md) — pipelines e argv dinâmico;
`training/idioms/stdlib.md` — a tabela BAD/GOOD.
