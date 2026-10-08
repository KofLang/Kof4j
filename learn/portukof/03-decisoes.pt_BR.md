[English](03-decisoes.md) | [Português](03-decisoes.pt_BR.md)

# 03 — Decisões

> **PortuKof — tudo executado de verdade.**

## Se… senão…

Programas precisam escolher. A palavra é literalmente `se`:

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

Saída **medida**: `aprovado`

Comparações: `==` (igual), `!=` (diferente), `>`, `<`, `>=`, `<=`.

## Muitas opções: `escolha`

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

Saída **medida**: `quarta`

## Pertence ou não pertence?

Um jeito limpo de perguntar "está na lista?":

```ptkf
principal() {
    val respostas = conjuntoDe("sim", "não")
    se (respostas.contem("sim")) {
        escrevaln("contem sim")
    }
}
```

Saída **medida**: `contem sim`

## Mini-jogo: adivinhe a senha

Junte input + decisão + repetição — um programa de verdade:

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

Saída **medida** (erramos `melancia` de propósito, depois acertamos):

```text
Qual é a senha? Errada! Tente de novo.
Bem-vindo!
```

Isso é um **loop com condição de saída** — o coração de jogos e apps.

## Desafio

1. Pergunte a idade; mostre "criança" (<12), "adolescente" (12–17) ou "adulto".
2. Um dado mágico: `escolha` com casos 1–6 mostrando o nome de cada face.
3. No mini-jogo, limite a 3 tentativas e use `sair` para quebrar o loop.
