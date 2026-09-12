# Idioms — Classes

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.3.22-beta (Sep 2026)

## What it is

Classe com campos, métodos, construtor, herança e interfaces.
Campos são declarados **sem** `var`/`val` e sem `;` obrigatório.

```kof
class User {
    String name
    Int age
}
```

## Construtores

### `class X(...)` é RECORD — dados imutáveis (verificado 02/09)

```kof
class User(String name, Int age) {
    greeting(): String {
        return "Hello " + name
    }
}
```

> **Atenção (02/09):** `class X(...)` é **alias de `record X(...)`** — o parser
> o trata como record body (imutável, `extends java.lang.Record` no JVM). Os
> "parâmetros" viram componentes com accessors: leitura `user.name` funciona
> (vira `name()`), mas **escrita `user.name = "x"` NÃO** (campo final →
> `IllegalAccessError`). Para dados imutáveis use `record` (a forma canônica);
> para **estado mutável** use campos explícitos + `constructor(...)`.

```kof
var user = User("Mel", 26)   // record — leitura ok, escrita não
```

### Construtor explícito — estado mutável (a forma de classe real)

```kof
class User {
    String name
    Int age

    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
}
```

Aqui os campos são **públicos e mutáveis**: `user.name = "Mel"` / `user.age = 30`.

`new User("Mel", 30)` continua válido, mas `User("Mel", 30)` é a forma
recomendada — o compilador trata ambas como construção de instância.

## When to use

- Entidades com comportamento (métodos que operam sobre o estado).
- Estado mutável.
- Herança e polimorfismo.

## When not to use

- Dados imutáveis sem comportamento → **record** (veja `records.md`).
- Apenas agrupamento de valores → record.

## BAD — cerimônia de getter

```kof
class User {
    private String name
    public getName(): String {
        return name
    }
    public setName(String name) {
        this.name = name
    }
}
```

## GOOD — campo direto

```kof
class User {
    String name
}
```

Uso: `u.name = "Mel"` e `println(u.name)`.

## WHY

Getter/setter de Java existe por convenções de encapsulamento (JavaBeans, frameworks).
Kof não possui essas convenções. Campo público é a forma idiomática até que exista
uma razão real para encapsulamento. Não reproduza ceremony sem semântica.

## BAD — factory trivial

```kof
createUser(String name): User {
    return User(name)
}
```

## GOOD

```kof
User(name)
```

## WHY

Uma factory que apenas delega ao construtor não adiciona informação. Chame o construtor.

## Herança

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    speak(): String = "animal"
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
    speak(): String = "dog"
}
```

- `super(args)` é a primeira instrução do construtor da subclasse.
- Override é implícito (mesmo nome de método).
- Dispatch é virtual em ambos os targets.

## Generics Box<T> (0.3.22-beta)

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b: Box<Int> = Box(42)
println(b.get())   // erasure + substituteTypeVariable — Native OK
```

## Anti-patterns relacionados

- Utility class de métodos estáticos → funções top-level (`functions.md`)
- Service layer sem estado → funções top-level
- Factory trivial → chamar o construtor
- `Box<T>` manual → usar generics nativo