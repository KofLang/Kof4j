# 16 — Lambdas

> **Status: implementado (JVM / Native / JS) — 0.3.22-beta — exemplos verificados no compilador**
>
> Lambdas `(x: Int) -> expr` com capturas funcionam nos três targets;
> `map/filter/reduce` em `List<T>` usam lambdas.

## O que são lambdas

Lambdas são funções anônimas — blocos de código que podem ser passados como
argumentos ou guardados em variáveis.

## Sintaxe

```kf
main() {
    var dobro = (x: Int) -> x * 2
    var resultado = dobro(5)
    println(resultado)     // 10

    var soma = (a: Int, b: Int) -> a + b
    println(soma(3, 4))    // 7

    var constante = () -> 99
    println(constante())   // 99
}
```

## Com collections

```kf
var nomes = listOf("Ana", "Bob", "Carlos")

var maiusculos = nomes.map((nome: String) -> nome.toUpperCase())
// ["ANA", "BOB", "CARLOS"]

var longos = nomes.filter((nome: String) -> nome.length > 3)
// ["Carlos"]
```

## Captura (closures)

Uma lambda captura variáveis do escopo onde foi criada:

```kf
var fator = 2
var dobro = (x: Int) -> x * fator
println(dobro(5))    // 10
```

## Captura mutável (02/09 — JVM verificado)

Uma variável capturada que é **mutada** (dentro ou fora da lambda) é
**boxada** — a lambda vê o valor atualizado:

```kf
main() {
    var offset = 10
    var f2 = (x: Int) -> x + offset
    println(f2(5))        // 15
    offset = 20           // mutação FORA da lambda
    println(f2(5))        // 25 — a lambda enxerga o novo valor

    var counter = 0
    var inc = () -> { counter = counter + 1 }   // lambda ESCREVE na externa
    inc()
    inc()
    println(counter)      // 2
}
```

> **Histórico (02/09):** antes a mutação fora da lambda não era detectada — a
> variável era capturada **por valor** e a leitura ficava desatualizada
> (retornava 15 em vez de 25). Corrigido no `CompilerDriver`
> (`collectMutatedCaptures`). **Native:** a direção "lambda escreve na
> variável externa" funciona; a direção "lê a variável externa após ela ser
> mutada fora da lambda" ainda é um bug conhecido (produz valor errado) —
> usar com cautela no target nativo.

## Regra prática

- Lambda que **só lê** uma variável: captura por valor, sem surpresas.
- Variável **mutada** + lambda: o compilador boxa — funciona no JVM; no
  Native, prefira que a mutação aconteça **dentro** da lambda.

## Referência a método — planejado

`::nome` não é suportado ainda. Use um lambda explícito:

```kf
for (var nome in listOf("Ana", "Bob")) {
    println(nome)
}
```

## Exercícios

1. Escreva um lambda `(x: Int) -> x * x` e use com `listOf(1,2,3,4).map`.
2. Capture uma variável, chame a lambda, mude a variável e chame de novo —
   verifique que o JVM reflete a mudança.
3. Use `filter` para extrair só os pares de `listOf(1,2,3,4,5,6,7,8,9,10)` e
   `reduce` para somá-los.

## Próximo passo

[Programação Funcional →](17-functional-programming.md)