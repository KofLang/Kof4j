# 11 — Generics

> **Status: implementado (JVM / Native / JS) — 0.3.22-beta — erasure + `Box<T>` com `T` primitivo**
>
> Generics por erasure funcionam nos três targets; `Box<Int>` com `substituteTypeVariable` + `kof_int_to_string` nativo já está em 0.2.0.

## O problema

Sem generics, você precisa de casts:

```java
List lista = new ArrayList();
lista.add("texto");
String texto = (String) lista.get(0);  // cast manual
```

Com generics, o compilador sabe o tipo:

```java
List<String> lista = new ArrayList<String>();
lista.add("texto");
String texto = lista.get(0);  // sem cast
```

## Generics em Kof

### Classes genéricas

```kf
class Box<T> {
    T value

    set(T v) {
        value = v
    }

    get(): T {
        return value
    }
}
```

Uso:

```kf
var caixaTexto = new Box<String>()
caixaTexto.set("olá")
var caixaNumero = new Box<Int>()
caixaNumero.set(42)
println(caixaNumero.get())   // 42 — Box<T> com T primitivo
```

`Box<T>` com `T` primitivo (`Box<Int>`) funciona nos três targets — no Native
o `get()` que devolve `T` tem o tipo substituído em compile-time
(`substituteTypeVariable`), então `println(b.get())` imprime o valor e não
vira segfault.

### Métodos genéricos

Os parâmetros de tipo vêm **depois** do nome da função:

```kf
identity<T>(T x): T {
    return x
}

main() {
    println(identity(42))     // 42
    println(identity("hi"))   // hi
}
```

### Bounds (planejado)

`extends` em parâmetros de tipo ainda não é resolvido em compile-time.

## Variância (planejado)

```kf
void copiar(List<? extends Animal> origem, List<? super Animal> destino) {
    for (var animal : origem) {
        destino.add(animal);
    }
}
```

## Interoperabilidade com generics Java

```kf
// Kof usando generics Java
var lista = new java.util.ArrayList<String>();
lista.add("hello");
String item = lista.get(0);
```

## Próximo passo

[Collections →](12-collections.md)
