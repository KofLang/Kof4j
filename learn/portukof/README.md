# Learn Programming with PortuKof 🚀

**PortuKof is Kof in Portuguese.** The same language, the same errors, the same
power — except the words are yours: `principal`, `se`, `enquanto`, `escreva`.

> [English](README.md) | [Português](README.pt_BR.md)

## Who this track is for

- **Kids and teenagers** (10+) who want to learn programming logic without
  fighting English on day one.
- **Teachers** looking for a first language that never becomes a toy dialect:
  what you learn here is real programming.
- **Kof users** who want to see the pt-BR surface working end to end.

## The core idea (no trick)

PortuKof is **not a separate language**. It is the human surface of Kof.
Underneath everything is the same:

```text
.ptkf program → same lexer/parser → SAME AST → SAME IR → SAME backends
```

A kid who finishes this track knows: variables, decisions, loops, functions,
lists, records — and can run the SAME code on JVM, native, RISC-V, ARM and JS,
exactly like any Kof program.

## How to run (3 ways)

```bash
kof script ola_mundo.ptkf   # fast (interpreted, great for play)
kof run ola_mundo.ptkf      # compile and run on the JVM
kof check ola_mundo.ptkf    # just check for errors, no run
```

Every program in this track was **actually executed**; the shown outputs are
real. Official examples live in `examples/portukof/`.

## The track

| Lesson | What you learn |
|---|---|
| [01 — First program](01-primeiro-programa.md) | `principal`, `escreva`, `diga`, `escrevaln` |
| [02 — Variables and texts](02-variaveis-e-textos.md) | `var`, `val`, `+`, `tamanho()` |
| [03 — Decisions](03-decisoes.md) | `se/senao`, `escolha`, `conjuntoDe().contem()` |
| [04 — Loops](04-repeticoes.md) | `para…em`, `enquanto`, `sair`, `segue` |
| [05 — Functions](05-funcoes.md) | type-before-name, `retorna`, expression body |
| [06 — Lists, maps and records](06-listas-e-jogos.md) | `listaDe`, `mapaDe`, `registro` |
| [07 — The same language](07-a-mesma-linguagem.md) | twin `.ptkf`/`.kf` programs, `kof fmt`, next steps |

## Syntax questions

The full reference (vocabulary tables, PT diagnostics, tooling) is in
[`docs/languages/portukof.md`](../../docs/languages/portukof.md).
The compiler is the authority: when in doubt, `kof check`.
