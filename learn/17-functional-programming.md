# 17 — Programação Funcional

> **Status: implementado — `map/filter/reduce` em `List<T>` (0.2.6-beta) — JVM/Native/JS — exemplos verificados no compilador**
>
> Kof não é uma linguagem funcional, mas `List<T>` oferece
> `map/filter/reduce` idiomáticos — a transformação é uma expressão, não um
> loop manual.

## map — transformar cada elemento

```kf
var nums = listOf(1, 2, 3, 4, 5)
var dobrados = nums.map((x: Int) -> x * 2)
println(dobrados.get(0))   // 2
println(dobrados.size)     // 5
```

## filter — selecionar elementos

```kf
var pares = nums.filter((x: Int) -> x % 2 == 0)
println(pares.size)        // 2 — [2, 4]
```

## reduce — acumular

```kf
var soma = nums.reduce((acc: Int, x: Int) -> acc + x, 0)
println(soma)              // 15
```

## Combinando

```kf
record User(String nome, Int idade)

main() {
    var usuarios = listOf(
        User("Mel", 26),
        User("Ana", 34),
        User("Bob", 17)
    )

    var adultos = usuarios.filter((u: User) -> u.idade() >= 18)
        .map((u: User) -> u.nome().toUpperCase())
    println(adultos.size)          // 2
    println(adultos.get(0))        // MEL
}
```

## Por que não loop manual

```kf
// ❌ Loop manual — o "o quê" (mapear) fica escondido no "como" (iterar)
var nomes = listOf()
for (var u in usuarios) { nomes.add(u.nome()) }

// ✅ map — expressa a intenção
var nomes2 = usuarios.map((u: User) -> u.nome())
```

## Imutabilidade e `val`

`val` impede a reatribuição da variável, mas `List` continua mutável por
métodos (`add`, `set`):

```kf
val lista = listOf(1, 2, 3)
lista.add(4)              // funciona — a lista é mutável
// lista = listOf(9)      // erro — val não pode ser reatribuído
```

Para dados imutáveis de verdade, use `record` + `json.encode`/`json.decode`
(ver cap. 12 e `docs/stdlib/stdlib.md`).

## Funções puras

Uma função pura não tem efeitos colaterais — mesmo input, mesmo output:

```kf
Int dobro(Int x) = x * 2
```

Prefira funções puras em `map/filter/reduce` (sem mutar estado externo).

## Exercícios

1. Dado `listOf(1,2,3,4,5,6)`, calcule a soma dos quadrados dos pares com uma
   cadeia `filter(...).map(...).reduce(...)`.
2. Ordene mentalmente a saída de `usuarios.filter((u) -> u.idade() < 30)
   .map((u) -> u.nome())` — confirme com `kof run`.
3. Reescreva um `for` que monta uma lista de nomes usando `map` (exercício do
   cap. 12).

## Próximo passo

[Concorrência →](18-concurrency.md)