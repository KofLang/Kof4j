[English](01-primeiro-programa.md) | [Português](01-primeiro-programa.pt_BR.md)

# 01 — First Program

> **PortuKof is the pt-BR surface of Kof — everything here was actually executed.**

## Hello, world!

Every programmer starts here. In PortuKof:

```ptkf
principal() {
    escrevaln("Olá, mundo!")
}
```

Save as `ola_mundo.ptkf` and run:

```bash
kof script ola_mundo.ptkf
```

**Measured** output:

```text
Olá, mundo!
```

## Reading each part

```
principal  → the entry point (the function that runs first)
( )        → parentheses: inputs would go here
{ }        → braces: the body, instructions in order
escrevaln  → prints a line to the screen ("ln" = newline at the end)
"Olá..."   → text: always inside double quotes
```

## `escreva` vs `escrevaln`

`escreva` prints without a newline; `escrevaln` prints and jumps to the next line.

## Talking style: `diga` and `diz`

Since programming is talking to the machine, PortuKof also accepts everyday
words for "say" — the same builtins under different spellings (speech sugar):

| you write | equals | canonical Kof |
|---|---|---|
| `diga("Oi!")` | `escrevaln("Oi!")` | `println` |
| `diz("Oi!")` | `escreva("Oi!")` | `print` |

There is no "better" spelling: `diga`, `escrevaln` and `println` compile to the
exact same program. `escrevaln` stays the primary surface name (docs, hover);
`diga`/`diz` are child-friendly aliases (`D-PORTUKOF-SUGAR`).

## Errors are learning

If you typo `escreval("oi")`, the compiler **does not guess** — it points at the
problem with code, line and column, localized in Portuguese for `.ptkf` files.

```bash
kof check ola_mundo.ptkf
```

## Did you know?

The SAME program in canonical Kof is `main() { println("Hello, world!") }`.
PortuKof and Kof become the **exact same AST** underneath — a different human
surface, an identical machine. Lesson 07 shows this in detail.
