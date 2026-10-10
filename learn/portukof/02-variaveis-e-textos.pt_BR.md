[English](02-variaveis-e-textos.md) | [Português](02-variaveis-e-textos.pt_BR.md)

# 02 — Variáveis e Textos

> **PortuKof — tudo executado de verdade.**

## Caixas com nomes

Uma variável é uma caixa onde você guarda um valor:

```ptkf
principal() {
    var nome = "Ana"
    val idade = 12
    escrevaln("Oi, " + nome)
    escrevaln(idade)
}
```

Saída **medida**:

```text
Oi, Ana
12
```

- `var` → caixa que pode mudar depois
- `val` → caixa que **não** muda (o Kof prefere `val`; mudar é exceção, não rotina)
- `"Oi, " + nome` → textos se juntam com `+` (chama *concatenação*)

## Descobrindo coisas sobre um texto

```ptkf
principal() {
    var nome = "Ana"
    escrevaln(nome.tamanho())
}
```

Saída **medida**: `3`

Repare: `nome.tamanho()` — "o tamanho de nome". Métodos em Kof sempre se lêem
assim: `coisa.fazAlgo()`.

## Entrada: pergunte e escute

```ptkf
principal() {
    escreva("Qual é o seu nome? ")
    val nome = leia()
    escrevaln("Oi, " + nome + "!")
}
```

Saída **medida** (digitamos `Ana`):

```text
Qual é o seu nome? Oi, Ana!
```

`leia()` espera você digitar e aperta ENTER. É a primeira vez que o programa
responde ao mundo — isso já é interatividade.

## Números

```ptkf
principal() {
    var a = 10
    var b = 3
    escrevaln(a + b)
    escrevaln(a - b)
    escrevaln(a * b)
    escrevaln(a / b)
    escrevaln(a % b)
}
```

Saída **medida**: `13`, `7`, `30`, `3`, `1` — e `%` é o **resto** da divisão
(10 dividido por 3 dá 3 e sobra 1).

## Desafio

1. Pergunte o nome e a idade, depois mostre "Ana tem 12 anos".
2. Descubra o tamanho do nome digitado.
3. Guarde o resultado de `2 + 2 * 3` em `val` e mostre. Qual foi? Por quê?

<details>
<summary>Dica do desafio 3</summary>

A multiplicação acontece antes da soma (a matemática de escola vale): o resultado é `8`.
</details>
