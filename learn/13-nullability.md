[English](13-nullability.md) | [Português](13-nullability.pt_BR.md)

# 13 — Nullability

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta — examples verified in the compiler**
>
> `Tipo?` (e.g.: `String?`) declares that a value **can** be `null`. The
> compiler requires a check (`if (x != null)`) before using it — and narrowing
> was fixed on the JVM on 02/09 (previously `s.length` with narrowing emitted
> invalid bytecode).

## The problem

`NullPointerException` is the most common cause of errors in Java:

```java
String nome = null;
System.out.println(nome.length());  // NullPointerException!
```

## The solution: `?`

```kf
String nome = "Mel"           // cannot be null
String? apelido = findNickname()  // can be null — null arrives via API,
var outro: String? = "Kof"    // annotated form (also valid)
                              // NEVER via `= null` (SEM048 since 10/09)
```

## Narrowing: `if (x != null)`

```kf
String? nome = obterNome()    // may come null
if (nome != null) {
    println(nome.length)      // safe — the check unlocks the access
} else {
    println("sem nome")
}
```

Accessing **without** the check is a compilation error:

```kf
var nome: String? = obterNome()
println(nome.length)   // ERROR: nome may be null — requires if (nome != null)
```

## The stdlib returns `?` (02/09)

The stdlib read functions are honestly typed — absence is
`null`, not a sentinel:

```kf
main() {
    var conteudo = readFile("config.json")     // String?
    if (conteudo != null) {
        println(conteudo.length)
    } else {
        println("arquivo não existe")
    }

    var linha = readLine()                     // String? — null at EOF
    if (linha != null) {
        println("linha: " + linha)
    }

    var m = mapOf("nome", "Mel")
    var v = m.get("nome")                      // V? — reference values
    if (v != null) {
        println(v.length)                      // 3
    }
}
```

> `Map.get` returns `V?` for **reference** values (`Map<String, String>`).
> For primitive values (`Map<String, Int>`) the type stays `V` — the current
> model does not represent absence in that case; check with `contains`/`containsKey`.

## Nullable in functions and returns

```kf
String? find(Int id) {
    if (id == 1) { return "mel" }
    return null
}

main() {
    var s = find(1)
    if (s != null) {
        println(s.length)    // 3
    }
}
```

## Golden rule

- **Absence as a value** (the data may not exist) → `String?`/`Tipo?` +
  `if (x != null)`.
- **Real error** (the absence is a defect) → `throw "mensagem"` + `catch`.

```kf
String findOrThrow(Int id) {
    if (id == 1) { return "mel" }
    throw "not found: " + id
}
```

## Where we are (0.5.0-beta)

- ✅ `String?`, `Int?`, `Tipo?` in the parser and type system (`NullableType`).
- ✅ Narrowing `if (x != null)` on the 3 targets — **JVM fixed 02/09**
  (previously `s.length`/`s.substring(...)` with narrowing emitted
  `getfield "?".length`/`"".substring` → launcher error/`ClassFormatError`).
- ✅ `Map.get` → `V?`, `readFile`/`readText`/`readLine` → `String?`.
- ✅ Elvis `a ?: b` (§821): `val name = user.nick ?: "anonymous"`.
- 🚧 Deeper flow analysis and the `?.` operator still planned.

## Exercises

1. Write `String? saudacao(String? nome)` that returns `"oi, X"` when
   `nome != null` and `"oi"` otherwise — use narrowing.
2. Read a file that may not exist and handle both cases with
   `readFile`.
3. Why does `Map.get` of a `Map<String, Int>` **not** return `Int?`? (hint:
   how `Int?` is stored at runtime).

## Next step

[Exceptions →](14-exceptions.md)
