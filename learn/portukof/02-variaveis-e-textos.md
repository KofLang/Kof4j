[English](02-variaveis-e-textos.md) | [Português](02-variaveis-e-textos.pt_BR.md)

# 02 — Variables and Texts

> **PortuKof — every output below was measured by running the program.**

A variable is a named box for a value:

```ptkf
principal() {
    var nome = "Ana"
    val idade = 12
    escrevaln("Oi, " + nome)
    escrevaln(idade)
}
```

Measured output:

```text
Oi, Ana
12
```

- `var` → box that can change later
- `val` → box that **cannot** change (Kof prefers `val`; mutation is the exception)
- `"Oi, " + nome` → texts join with `+` (concatenation)

## Ask and listen

```ptkf
principal() {
    escreva("Qual é o seu nome? ")
    val nome = leia()
    escrevaln("Oi, " + nome + "!")
}
```

Measured (we typed `Ana`):

```text
Qual é o seu nome? Oi, Ana!
```

`nome.tamanho()` gives the text length (measured: `3`). Numbers work with
`+ - * /` and `%` (remainder). The same identifiers, methods and types as
canonical Kof — only the surface words are Portuguese.
