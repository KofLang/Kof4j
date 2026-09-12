# 06 — Funções

> **Status: implementado (JVM / Native / JS) — 0.3.22-beta — exemplos verificados no compilador**
>
> Funções de nível superior, métodos, expression bodies, default parameters,
> recursão e funções como valores (lambdas) funcionam nos targets JVM, Native
> e KofJS.

## Funções de nível superior

Não existe `fun`/`func` — a função é declarada pelo nome, com o tipo de
retorno **antes** do nome ou **depois** dos parâmetros:

```kf
Int soma(Int a, Int b) {
    return a + b
}

main() {
    println(soma(2, 3))   // 5
}
```

## As formas válidas

```kf
main() { println("entry point") }             // única sem tipo explícito
String saudacao() { return "oi" }             // retorno antes do nome
despedida(): String { return "tchau" }        // retorno depois dos parâmetros
void fazIsso() { println("x") }               // void explícito
```

## Expression body

Para funções de uma expressão só:

```kf
Bool positivo(Int x) = x > 0

main() {
    println(positivo(5))    // true
    println(positivo(-1))   // false
}
```

## Parâmetros

```kf
void imprimir(String mensagem, Int vezes) {
    var i = 0
    while (i < vezes) {
        print(mensagem)
        i = i + 1
    }
}
```

## Default parameters

```kf
void greet(String name = "world") {
    println("hello " + name)
}

main() {
    greet("Mel")
    greet()          // usa o default — "hello world"
}
```

`Server(8080)` / `Server()` para classes seguem a mesma semântica, resolvida
em compile-time.

## Retorno

Em funções `void`, `return` sozinho encerra o fluxo:

```kf
void maybe(Bool condition) {
    if (condition) {
        return
    }
    println("not-returned")
}
```

`return;` também é aceito por compatibilidade.

## Recursão

```kf
Int fatorial(Int n) {
    if (n <= 1) { return 1 }
    return n * fatorial(n - 1)
}

main() {
    println(fatorial(5))   // 120
}
```

## Funções como valores

Lambdas são valores de primeira classe — guarda-se em variável e passa-se
como argumento (é assim que `map/filter/reduce` funcionam, ver cap. 12 e 16):

```kf
main() {
    var dobro = (x: Int) -> x * 2
    println(dobro(5))                                  // 10

    var nums = listOf(1, 2, 3)
    var dobrados = nums.map((x: Int) -> x * 2)         // [2, 4, 6]
    println(dobrados.get(0))                           // 2
}
```

> **Nota:** não existe tipo de função **anotado** como parâmetro declarado
> (`(Int) -> Int f` não compila). A função chega como lambda anônimo no ponto
> de chamada.

## Exercícios

1. Escreva `Int maximo(Int a, Int b)` em expression body e use-a.
2. Escreva `Int fib(Int n)` recursivo e imprima `fib(10)`.
3. Crie `listOf(1,2,3,4).map(...)` que devolva os quadrados. Compare com um
   loop manual — qual expressa melhor a intenção?
4. Escreva uma função com default parameter que gere uma saudação
   personalizada.

## Próximo passo

[Classes e Objetos →](07-classes-and-objects.md)