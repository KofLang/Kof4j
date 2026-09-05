# 15 — Pattern Matching

> **Status: implementado (JVM / Native / JS) — 0.2.6-beta**
>
> `switch case String s` (type pattern) e destructuring de records `case Point(x, y):` funcionam nos três targets. Parser + Semantic + CompilerDriver com `Native rbx→rcx` fix e `JS typeof`.
>
> **Atenção:** o padrão com variável (`case String s`) existe **só dentro de `switch`**. A forma `if (obj instanceof String s)` **não é suportada** — `instanceof` é um operador booleano simples e não faz *binding* de variável; para capturar, use `switch` com `case` ou `as` (cast).

## `instanceof` (checagem de tipo)

`instanceof` verifica o tipo (booleano) — ele **não** declara uma variável:

```kf
main() {
    Object obj = "kof"
    if (obj instanceof String) {
        var s = obj as String
        println("é uma string: " + s)
    }
}
```

Runnable — `kof run --target=jvm|native|js`:

```kf
main() {
    Object o = "kof"
    if (o instanceof String) {
        var s = o as String
        assert(s == "kof")
        println(s)
    }
}
```

Para capturar a variável tipada num único passo, use o pattern de `switch`
(seção seguinte).

## switch com padrões — type pattern

```kf
record Circulo(Double raio)
record Retangulo(Double largura, Double altura)

String descreve(Object forma) {
    switch (forma) {
        case Circulo c: { return "círculo com raio " + c.raio() }
        case Retangulo r: { return "retângulo " + r.largura() + "x" + r.altura() }
        default: { return "forma desconhecida" }
    }
}

main() {
    println(descreve(Circulo(2.0)))
    println(descreve(Retangulo(3.0, 4.0)))
}
```

> **Duas formas (0.2.6-beta):**
> - **Statement** — `case Tipo var:` com corpo de statements (efeitos colaterais).
> - **Expressão (SYN001, 03/09)** — `case Tipo var ->` produzindo **valor**:
>   `var desc = switch (forma) { case Circulo c -> "raio " + c.raio(); default -> "?" }`.
>   Cada caso é uma única expressão; `default` é obrigatório (ou exaustividade de
>   enum, senão `SEM032`); sem `break`, sem escopo de bloco. Funciona nos 3
>   targets (JVM/Native/JS) + riscv64/aarch64.

## Record destructuring — `Point(x, y)`

0.2.0 suporta desestruturação direta do record no `case`:

```kf
record Ponto(Int x, Int y)
record Pessoa(String nome, Int idade)

main() {
    Object o = Ponto(3, 7)
    switch (o) {
        case Ponto(x, y): {
            println("ponto " + x + "," + y)   // 3,7
        }
        case Pessoa(nome, idade): {
            println(nome + " " + idade)
        }
        case String s: {
            println("texto " + s)
        }
        default: {
            println("outro")
        }
    }

    // destructuring com var explícito também vale:
    switch (Ponto(1, 2)) {
        case Ponto(var a, var b): { println(a + b) }  // 3
        default: {}
    }
}
```

Compile e rode nos três targets — a cadeia `intention->Kof->frontend->IR->backend->runtime` mantém a semântica: o frontend normaliza `Ponto(x, y)` para `PatternExpr`, o IR emite `instanceof`+`checkcast`+`getfield` (JVM) / loads diretos (Native) / `typeof`+field access (JS).

## Padrões em sealed hierarchies (planejado)

`sealed ... permits` ainda não é consumido pelo parser (ver cap. 10). O
exemplo ilustra como o pattern de `switch` cobriria a hierarquia quando sealed
for implementado:

```kf
sealed class Resultado<T> permits Sucesso<T>, Erro<T> {}

String mensagem(Resultado<String> r) {
    switch (r) {
        case Sucesso s: { return "ok: " + s.valor() }
        case Erro e: { return "falha: " + e.mensagem() }
        default: { return "?" }
    }
}
```

O compilador verifica se todos os casos foram cobertos quando houver sealed (exhaustiveness check em evolução).

## Padrões com guards (planejado)

```kf
switch (nota) {
    case Int n when n >= 9: { println("excelente") }
    case Int n when n >= 7: { println("bom") }
    case Int n when n >= 5: { println("regular") }
    default: { println("reprovado") }
}
```

> `when` ainda é desugar futuro — hoje use `if` dentro do `case`.

## Próximo passo

[Lambdas →](16-lambdas.md)
