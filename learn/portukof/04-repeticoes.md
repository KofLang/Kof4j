[English](04-repeticoes.md) | [Português](04-repeticoes.pt_BR.md)

# 04 — Loops

> **PortuKof — every output below was measured by running the program.**

## For each item: `para … em` ("for … in")

```ptkf
principal() {
    para (var i em listaDe(1, 2, 3)) {
        escrevaln(i)
    }
}
```

Measured output: `1`, `2`, `3`.

## While it's true: `enquanto`

Rocket countdown:

```ptkf
principal() {
    var cont = 3
    enquanto (cont > 0) {
        escrevaln(cont)
        cont = cont - 1
    }
    escrevaln("foguete!")
}
```

Measured output:

```text
3
2
1
foguete!
```

Here `var` (a box that changes) makes sense: `cont` decreases **inside** the
loop until the condition turns false. Every loop needs a way to **end**.

## Summing with a loop

```ptkf
principal() {
    var soma = 0
    para (var n em listaDe(1, 2, 3, 4, 5)) {
        soma = soma + n
    }
    escrevaln(soma)
}
```

Measured output: `15`

## Flow control

- `sair` → ends the loop now (`break` in Kof)
- `segue` → skips to the next round (`continue` in Kof)

## Challenges

1. 7-times table over a list of 1..10.
2. Find the biggest number in `listaDe(3, 9, 2, 7)`.
3. A horn: `enquanto` loop that ends only when `leia()` receives "pare".
