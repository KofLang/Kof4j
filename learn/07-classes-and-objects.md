[English](07-classes-and-objects.md) | [Português](07-classes-and-objects.pt_BR.md)

# 07 — Classes and Objects

> **Kof 0.5.0-beta — examples verified in the compiler (02/09)**
>
> Kof has **two** models of "data with parameters": `record`/`class X(...)`
> (immutable, accessors) and a class with fields + `constructor(...)` (mutable,
> direct fields). **No getters/setters** — the field is the data.

## 1. Immutable data → record

The canonical form for immutable data:

```kf
record User(String name, String email)

main() {
    var u = User("Mel", "mel@kof.dev")
    println(u.name())          // accessor
    println(u.email())         // mel@kof.dev
}
```

The compiler generates: canonical constructor, accessors (`name()`), and
`toString`/`equals`/`hashCode` on every target. Equality and hashing are BY
CONTENT everywhere (§104b-ii/§114, 24/09): two records with the same field
values are `==`, hash the same, dedup in `Set`, match as `Map` keys and
`containsValue` values — on JVM, Native (x86-64, riscv64, aarch64) and JS
alike. The synthesized `equals` recurses into nested record/class fields;
`hashCode` is content-based for String, Double and nested records.

### `value record` — same data model, no identity (TIER 2.7, 14/10)

```kf
value record Vec2(Float x, Float y)
```

A `value record` behaves **exactly like `record`** (fields, structural `==`,
`toString` — verified on all four targets) but declares **no object identity**
(`D-VALUE-RECORD`; `value` applies only to `record`, collections hold them
boxed). Per target: JVM = the common record class, Native = the current heap
representation, JS = the object is **frozen**. `value` is a *contextual*
keyword: only before `record` — anywhere else `value` stays an ordinary
identifier.

Records can have methods:

```kf
record Token(String kind, String text) {
    label(): String {
        return kind + "(" + text + ")"
    }
}
```

## 2. `class X(...)` = record (the same thing — verified 02/09)

`class User(String name, String email)` is an **alias of `record`** — the parser
treats it as a record body (immutable, `extends java.lang.Record` on the JVM):

```kf
class User(String name, String email) {
    greeting(): String {
        return "Hello " + name
    }
}

main() {
    var u = User("Mel", "mel@kof.dev")
    println(u.greeting())      // Hello Mel
    println(u.name)            // read ok (becomes the accessor)
    // u.name = "Ana"          // COMPILATION ERROR SEM038: record is immutable
}
```

> Prefer `record` (the intention is explicit). `class X(...)` is backward-compatible.

## 3. Mutable state → class with fields + `constructor(...)`

To **mutate**, use explicit public fields:

```kf
class Conta {
    String titular
    Double saldo

    public constructor(String titular, Double saldo) {
        this.titular = titular
        this.saldo = saldo
    }

    depositar(Double valor) {
        saldo = saldo + valor     // direct field access
    }
}

main() {
    var c = Conta("Mel", 100.0)
    c.saldo = 50.0                // direct write — no setter
    c.depositar(25.0)
    println(c.saldo)              // 75.0 — direct read, no getter
}
```

**No getters/setters**: `c.saldo` reads, `c.saldo = x` writes. `getSaldo()`/
`setSaldo()` are Java ceremony with no reason in Kof (see ch. 08).

## Default constructor

Without `constructor(...)`, an empty constructor is generated:

```kf
class Config {
    String host
    Int porta
}

main() {
    var config = Config()
    config.host = "localhost"
    println(config.host)
}
```

`new Config()` is also accepted (backward-compatible).

## Fields with initializer

```kf
class User {
    String name
    Bool active = true
}
```

Initializers run in all constructors (JVM, Native, JS).

## Access modifiers

`private` exists for real encapsulation — but **do not create a getter to
expose**; either the field is public, or the method has semantics:

```kf
class Conta {
    private Double saldo

    public constructor(Double saldo) { this.saldo = saldo }

    // method with SEMANTICS, not a getter
    Double totalComJuros(Double taxa) {
        return saldo * (1 + taxa)
    }
}
```

## Utility functions → top-level (not a static class)

```kf
// ❌ utility class with static (Java)
class StringUtils {
    static String repetir(String texto, Int vezes) { ... }
}

// ✅ top-level function (Kof)
String repetir(String texto, Int vezes) {
    var resultado = ""
    for (var i = 0; i < vezes; i++) {
        resultado += texto
    }
    return resultado
}
```

## this and super

```kf
class Animal {
    String nome

    public constructor(String nome) {
        this.nome = nome
    }
}

class Cachorro extends Animal {
    String raca

    public constructor(String nome, String raca) {
        super(nome)          // super(args) is the 1st statement
        this.raca = raca
    }
}
```

Override is implicit (same method name); dispatch is virtual.

## Method overloading (0.5.0-beta, §131)

Methods with the same name coexist in a class when their signatures differ:

```kf
class Calc {
    Int add(Int a, Int b) { return a + b }
    Int add(Int a, Int b, Int c) { return a + b + c }
}
main() {
    var c = Calc()
    println(c.add(1, 2))       // 3
    println(c.add(1, 2, 3))    // 6
}
```

The rules are the functions' rules (SEM047 exact duplicate, SEM057
ambiguity) — see ch. 06 and `training/idioms/classes.md`.

## Current status

- ✅ `record` / `class X(...)` — immutable data, accessors (3 targets)
- ✅ Mutable class — public fields + `constructor(...)`
- ✅ Fields with initializer (JVM, Native, JS)
- ✅ Inheritance, virtual dispatch, interfaces
- ✅ No getters/setters — direct field

## Exercise 1

Create `class ContaBancaria` with fields `titular` and `saldo`, a constructor and
methods `depositar`/`sacar` — **without** `getSaldo()`, access `c.saldo`
directly. Validate with `kof run`.

## Exercise 2

Create `record Retangulo(Double largura, Double altura)` with a method
`area()`. Test it. Then try `r.largura = 5.0` — what happens and why?

## Next step

[Fields and Data Access →](08-properties.md)
