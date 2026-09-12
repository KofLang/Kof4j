# Kof Classes

**Version:** 0.3.22-beta (Sep 2026)

## Basic Class — dois modelos (verificado 02/09)

> `class User(String name, Int age)` é **alias de `record`** (imutável,
> `extends java.lang.Record` no JVM; accessors `u.name()`; escrita `u.name =
> "x"` NÃO). Para **estado mutável**, use campos + `constructor(...)` (campos
> públicos, escrita direta).

```kof
// Imutável (record-style): class X(...) == record X(...)
class User(String name, Int age) { }
var u = User("Mel", 30)
println(u.name)      // leitura ok (accessor)
// u.age = 31         // ERRO de compilação SEM038: record é imutável

// Mutável — forma de classe real
class User2 {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
}
var u2 = User2("Mel", 30)
u2.age = 31           // ok — campo público mutável
```

## Records (Immutable Data)

```kof
record Point(Int x, Int y)
// Auto-generates: constructor, accessors x(), y(), toString()
switch (p) {
    case Point(var x, var y): println(x + "," + y) // destructuring
}
```

## Inheritance

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
}
```

## Interfaces

```kof
interface Speaker {
    speak(): String
}
class Dog implements Speaker {
    public speak(): String {
        return "woof"
    }
}
```

## Virtual Dispatch

```kof
class Animal {
    public speak(): String { return "animal" }
}
class Dog extends Animal {
    public speak(): String { return "dog" }
}
main() {
    Animal a = new Dog()
    println(a.speak())  // prints "dog" (virtual dispatch)
}
```

## Field Initialization

```kof
class Config {
    String host = "localhost"
    Int port = 8080
    public constructor() {
    }
}
```

## Access Modifiers

- `public` — accessible everywhere
- `private` — accessible within class
- `protected` — accessible in subclass
- `static` — class-level
- `final` — immutable
