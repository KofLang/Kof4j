[English](03-decisoes.md) | [Português](03-decisoes.pt_BR.md)

# 03 — Decisions

> **PortuKof — every output below was measured by running the program.**

## if…else

Programs must choose. In PortuKof the word is literally `se` ("if"):

```ptkf
principal() {
    val nota = 8
    se (nota >= 7) {
        escrevaln("aprovado")
    } senao {
        escrevaln("tente de novo")
    }
}
```

Measured output: `aprovado`

Comparisons: `==`, `!=`, `>`, `<`, `>=`, `<=`.

## Many options: `escolha` (switch)

```ptkf
principal() {
    var dia = 3
    escolha (dia) {
        caso 1: escrevaln("segunda")
        caso 2: escrevaln("terca")
        caso 3: escrevaln("quarta")
        padrao: escrevaln("outro dia")
    }
}
```

Measured output: `quarta`

## Membership: `conjuntoDe(...).contem(x)`

```ptkf
principal() {
    val respostas = conjuntoDe("sim", "não")
    se (respostas.contem("sim")) {
        escrevaln("contem sim")
    }
}
```

Measured output: `contem sim`

## Mini-game: password loop (input + decision + repetition)

```ptkf
principal() {
    val senhaSecreta = "abacaxi"
    escreva("Qual é a senha? ")
    var tentativa = leia()
    enquanto (tentativa != senhaSecreta) {
        escrevaln("Errada! Tente de novo.")
        tentativa = leia()
    }
    escrevaln("Bem-vindo!")
}
```

Measured (we typed `melancia`, then `abacaxi`):

```text
Qual é a senha? Errada! Tente de novo.
Bem-vindo!
```

A **loop with an exit condition** — the heart of every game and app.
