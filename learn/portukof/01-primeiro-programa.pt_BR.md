[English](01-primeiro-programa.md) | [Português](01-primeiro-programa.pt_BR.md)

# 01 — Primeiro Programa

> **PortuKof — superfície pt-BR do Kof — tudo nesta aula foi executado de verdade.**

## Olá, mundo!

Todo programador começa aqui. Em PortuKof:

```ptkf
principal() {
    escrevaln("Olá, mundo!")
}
```

Salve como `ola_mundo.ptkf` e rode:

```bash
kof script ola_mundo.ptkf
```

Saída **medida**:

```text
Olá, mundo!
```

## Entendendo cada parte

```
principal  → o ponto de partida do programa (a função que roda primeiro)
( )        → parênteses: aqui entrariam os dados de entrada
{ }        → chaves: o corpo, as instruções na ordem
escrevaln  → escreve uma linha na tela (o "ln" = pula linha no fim)
"Olá..."   → texto: sempre entre aspas duplas
```

## `escreva` vs `escrevaln`

```ptkf
principal() {
    escreva("oi")
    escreva(" oi")
    escrevaln(" fim")
    escrevaln("linha nova")
}
```

Saída **medida** (repare que `escreva` não pula linha, `escrevaln` pula):

```text
oi oi fim
linha nova
```

## Jeito de conversar: `diga` e `diz`

Programar é conversar com a máquina. Para "falar" na tela, o PortuKof aceita
também as palavras do dia a dia (mesma coisa, mesmo builtin):

| você escreve | equivale a | é o mesmo que |
|---|---|---|
| `diga("Oi!")` | `escrevaln("Oi!")` | `println` |
| `diz("Oi!")` | `escreva("Oi!")` | `print` |

```ptkf
principal() {
    diga("falei uma linha")
    diga("outra linha")
}
```

Não existe "melhor" ou "pior": `diga`, `escrevaln` e `println` são o MESMO
programa por baixo. O Kof escolheu `escrevaln` como nome primário (aparece na
documentação e no hover); `diga` é o açúcar que aproxima da fala da criança.

## Erro é aprendizado

Se você escrever `escreval("oi")` (sem o `n`), o compilador **não adivinha** —
ele aponta o problema com código, linha e coluna, em português quando o arquivo
é `.ptkf`:

```bash
kof check ola_mundo.ptkf
```

Errar faz parte: o `check` é seu amigo, não seu juiz.

## Desafio

1. Mostre seu nome na tela.
2. Mostre três frases, cada uma em uma linha.
3. Troque `escrevaln` por `escreva` e veja a diferença.

<details>
<summary>Resposta do desafio 1</summary>

```ptkf
principal() {
    escrevaln("Meu nome é Ana")
}
```
</details>

## Você sabia?

O MESMO programa em Kof canônico (inglês) é:

```kf
main() {
    println("Hello, world!")
}
```

PortuKof e Kof viram **exatamente o mesmo AST** por baixo — uma língua humana
diferente, uma máquina idêntica. A aula 07 mostra isso com detalhes.
