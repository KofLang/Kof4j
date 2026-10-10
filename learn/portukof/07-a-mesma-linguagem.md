[English](07-a-mesma-linguagem.md) | [Português](07-a-mesma-linguagem.pt_BR.md)

# 07 — The Same Language

> **PortuKof — everything claimed here is measured by tests or executed code.**

After six lessons you have been writing a lot of PortuKof. The good news: you
already know Kof. These two programs are **the same program** — only the words
swap; names and texts stay identical (identifiers and strings are **never**
translated):

```ptkf
Int dobrar(Int n) { retorna n * 2 }

principal() {
    var total = 0
    para (var i em listaDe(1, 2, 3)) {
        total = total + dobrar(i)
    }
    se (total > 5) {
        diga("grande: " + total)
    } senao {
        diga("pequeno: " + total)
    }
}
```

```kf
Int dobrar(Int n) { return n * 2 }

main() {
    var total = 0
    for (var i in listOf(1, 2, 3)) {
        total = total + dobrar(i)
    }
    if (total > 5) {
        println("grande: " + total)
    } else {
        println("pequeno: " + total)
    }
}
```

Measured output of BOTH: `grande: 12`. And a compiler test
(`PortuKofSurfaceE2ETest`) proves the two become the **same AST** after surface
normalization — not "similar": identical.

## Extension decides, content never guesses

| file | surface |
|---|---|
| `game.ptkf` | PortuKof |
| `game.kf` / `game.kof` | Kof |

A `.kf` file full of `se/enquanto/diga` does not become PortuKof. A `.ptkf`
file with `if/while/println` still runs — it's canonical Kof mistakenly written
in Portuguese.

## `kof fmt`: the formatter speaks Portuguese

```bash
kof fmt game.ptkf
```

The formatter parses `.ptkf` through the profile pipeline and re-prints it
**in Portuguese from the AST** — never regex translation, never touching your
identifiers, never translating strings or comments.

## Portuguese diagnostics

`.ptkf` files receive localized error messages with code, line and column —
you learn to read errors in your language.

## What you now know

Variables, decisions, loops, functions, collections, records, input and output.
That knowledge **does not expire** — it is the foundation of Python, JavaScript,
Rust, Java, and Kof itself.

## Next steps

1. Rewrite a program you already made in another language — in PortuKof.
2. Read the full reference:
   [`docs/languages/portukof.md`](../../docs/languages/portukof.md).
3. Run the **same** `.ptkf` on JVM, native, JS, RISC-V and ARM — because it is
   Kof.
