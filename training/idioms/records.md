# Idioms — Records

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.3.22-beta (Sep 2026)

## What it is

Record é a forma de declarar **dados imutáveis com zero cerimônia**.

```kof
record Point(Int x, Int y)
```

O compilador gera: construtor canônico, accessors (`p.x()`), e no JVM também
`toString`/`equals`/`hashCode`.

> **`class Point(Int x, Int y)` é idêntico** — o parser trata `class X(...)`
> como record body (imutável). Use `record` (a forma canônica) para deixar a
> intenção explícita; `class X(...)` é retrocompatível mas não traz classe
> mutável (verificado 02/09). Para estado mutável, veja `classes.md`.

## When to use

- Dados imutáveis (DTO, valor, chave, resultado).
- Agrupamento de valores sem comportamento.
- Quando em Java você escreveria `class` + construtor + getters + equals + hashCode.

## When not to use

- Estado mutável → classe.
- Comportamento sobre o estado → classe.

## BAD — classe com cerimônia para dados

```kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    public getName(): String {
        return name
    }
    public getAge(): Int {
        return age
    }
}
```

## GOOD — record

```kof
record User(String name, Int age)
```

Uso:

```kof
var u = User("Mel", 30)
println(u.name())
println(u)
```

No JVM, `println(u)` imprime `User[name=Mel, age=30]` (toString gerado).

## WHY

Record elimina a cerimônia que Java exige para dados. Se o problema é
"representar um objeto de dados", record é a resposta padrão.

## Criação

```kof
var a = Point(10, 20)      // construtor canônico
var b = new Point(3, 4)    // também aceito
```

> **Record é imutável.** Escrever em componente (`p.x = 9`) ou em `this.x` dentro
> de um record é erro de compilação **SEM038** ("record is immutable"). Para
> estado mutável use `class` com campos + `constructor(...)`.

## Pattern matching — record destructuring (0.3.22-beta)

```kof
record Point(Int x, Int y)
record User(String name, Int age)

main() {
    var p = Point(10, 20)
    switch (p) {
        case Point(var x, var y):
            println(x + "," + y)   // 10,20
            break
        default:
            println("outro")
    }

    // com tipos
    var obj: Object = User("Mel", 30)
    switch (obj) {
        case String s:
            println(s)
            break
        case User(var n, var a):
            println(n + " " + a)
            break
        default:
            println("unknown")
    }

    // instanceof + destructuring
    if (p instanceof Point) {
        var q = p as Point
        println(q.x() + "," + q.y())
    }
}
```

O compilador gera `if-chain` com `getfield` nos 3 targets (JVM `INVOKEVIRTUAL`, Native `rcx/r15`, JS `typeof/instanceof`).

## Acesso: record `p.x()` vs classe `u.name` (02/09 — documentado)

Um record expõe os componentes por **accessors** (`p.x()`), uma classe por
**campo direto** (`u.name`). É a diferença de contrato entre dados imutáveis
(record — leitura via método) e estado mutável (classe — campo). Não é
acidente: o record é um valor; a classe é estado.

```kof
record Point(Int x, Int y)
var p = Point(10, 20)
p.x()                          // accessor (método)

class User {
    String name
    public constructor(String name) { this.name = name }
}
var u = User("Mel")
u.name                         // campo direto
```

## JSON

Records são suportados por `json.encode`/`json.decode<T>` no JVM e JS:

```kof
var p = Point(3, 4)
var j = json.encode(p)                 // {"x":3,"y":4}
var d = json.decode<Point>("{\"x\": 10, \"y\": 20}")
```

**Native:** JSN002/JSN001/JSN003 fechados 31/08 — `json.encode`/`json.decode<T>` de
objetos/records/arrays funciona também no Native (composição compile-time; FP em XMM).

## Null safety com records (0.3.22-beta)

```kof
Point? maybe = null
if (maybe != null) {
    println(maybe.x())
}
```

## Anti-patterns relacionados

- `java-like-code.md` — record + getters
- `unnecessary-abstraction.md` — wrapper em volta de record sem motivo
- `fake-idioms.md` — conferir status de pattern matching
