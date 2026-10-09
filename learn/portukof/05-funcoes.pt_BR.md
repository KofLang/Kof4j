[English](05-funcoes.md) | [Português](05-funcoes.pt_BR.md)

# 05 — Funções

> **PortuKof — tudo executado de verdade.**

## O que é uma função?

Uma função é uma **máquina pequena** com nome: você dá dados, ela processa e
devolve um resultado. Quando o programa cresce, funções evitam copiar e colar.

## O modelo do Kof: tipo antes do nome

```ptkf
Int dobrar(Int n) {
    retorna n * 2
}

principal() {
    escrevaln(dobrar(21))
}
```

Saída **medida**: `42`

Entendendo:

- `Int` (no começo) → o tipo do resultado que ela devolve
- `dobrar` → o nome da função
- `(Int n)` → ela recebe um número inteiro e dá o apelido `n`
- `retorna` → devolve o resultado para quem chamou

> **Atenção:** Em Kof **não existe** a palavra `fun`, `func` ou `def` (são de
> outras linguagens). A regra é simples: o tipo do retorno vem antes do nome,
> exatamente como `principal()` (que não devolve nada).

## Jeito curto: corpo-expressão com `=`

Quando a função cabe em uma continha só:

```ptkf
Int triplicar(Int n) = n * 3

principal() {
    escrevaln(triplicar(7))
}
```

Saída **medida**: `21`

Sem chaves, sem `retorna` — direto ao ponto.

## Função sem retorno (efeito na tela)

```ptkf
void saudar(texto nome) {
    diga("Oi, " + nome + "!")
}

principal() {
    saudar("Ana")
    saudar("Beto")
}
```

`void` significa "não devolve nenhum valor — só faz algo" (aqui, mostra na tela
usando o açúcar `diga`). Saída **medida**:

```text
Oi, Ana!
Oi, Beto!
```

## Desafio

1. Faça uma função `Int quadrado(Int n)` que devolve `n * n`.
2. Faça uma função `Bool ehPar(Int n)` que devolve se o número é par.
3. Crie uma função `texto saudarComIdade(texto nome, Int idade)`.

<details>
<summary>Respostas dos desafios 1 e 2 (medidas)</summary>

```ptkf
Int quadrado(Int n) { retorna n * n }
```

`quadrado(5)` → saída medida: `25`

```ptkf
Bool ehPar(Int n) = n % 2 == 0
```

`ehPar(4)`, `ehPar(7)` → saída medida: `true`, `false`
</details>
