[English](04-repeticoes.md) | [Português](04-repeticoes.pt_BR.md)

# 04 — Repetições

> **PortuKof — tudo executado de verdade.**

## Para cada item: `para … em`

```ptkf
principal() {
    para (var i em listaDe(1, 2, 3)) {
        escrevaln(i)
    }
}
```

Saída **medida**: `1`, `2`, `3` (uma linha cada).

## Enquanto for verdade: `enquanto`

Contagem regressiva de foguete:

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

Saída **medida**:

```text
3
2
1
foguetel!
```

Aqui `var` (caixa que muda) faz sentido: `cont` diminui **dentro** do loop até a
condição virar falsa. Todo loop precisa de um jeito de **terminar** — senão roda
para sempre.

## Somando com loop

```ptkf
principal() {
    var soma = 0
    para (var n em listaDe(1, 2, 3, 4, 5)) {
        soma = soma + n
    }
    escrevaln(soma)
}
```

Saída **medida**: `15`

## Par ou ímpar, sem enrolação

```ptkf
principal() {
    para (var n em listaDe(1, 2, 3, 4, 5, 6)) {
        se (n % 2 == 0) {
            escrevaln(n + " é par")
        } senao {
            escrevaln(n + " é ímpar")
        }
    }
}
```

Saída **medida**:

```text
1 é ímpar
2 é par
3 é ímpar
4 é par
5 é ímpar
6 é par
```

## Controlando o fluxo

- `sair` → encerra o loop agora
- `segue` → pula para a próxima volta

```ptkf
principal() {
    para (var n em listaDe(1, 2, 3, 4)) {
        se (n == 2) { segue }
        se (n == 4) { sair }
        escrevaln(n)
    }
}
```

Saída esperada: `1`, `3` (o 2 pulou, o 4 encerrou).

## Desafio

1. Tabuada do 7: `para` sobre `listaDe(1..10)` mostrando `7 * n`.
2. O maior número: percorra `listaDe(3, 9, 2, 7)` guardando o maior em `val var`.
3. Buzina: `enquanto` que só termina quando `leia()` receber "pare".
