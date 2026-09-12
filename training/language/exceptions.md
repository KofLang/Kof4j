# Kof Exceptions Reference

**Exceptions são Strings** — `throw "mensagem"` / `catch (String e)`. Não há
objeto de exceção nem `throw 42`/`catch (Int e)` (geram bytecode inválido no
JVM — verificado 02/09). Para ausência como valor, use `String?` (não erro).

## Throw

```kof
throw "error message"
throw "user not found: " + id
```

## Try/Catch

```kof
try {
    riskyOperation()
} catch (String e) {
    println("Error: " + e)
}
```

## Try/Finally

```kof
try {
    riskyOperation()
} finally {
    println("Cleanup")
}
```

## Try/Catch/Finally

```kof
try {
    riskyOperation()
} catch (String e) {
    println("Error: " + e)
} finally {
    println("Cleanup")
}
```

## Ausência vs erro

- **Ausência** (o dado pode não existir) → `String?` + `if (x != null)`.
- **Erro real** (a ausência é um defeito) → `throw "not found: " + id`.

```kof
String? find(String key) { if (found) return value; return null }
String findOrThrow(String key) { if (found) return value; throw "not found: " + key }
```

## Runtime Errors

| Error | Trigger | Message |
|-------|---------|---------|
| Null pointer | Access null object | "Runtime error: null pointer access" |
| Array bounds | Index out of range | "Runtime error: array index out of bounds" |
| Panic | `kof_panic()` | Custom message |

## Behavior

- **JVM**: Exceptions propagate via the JVM exception table; the thrown String
  is wrapped in a `RuntimeException` and unwrapped back in the catch.
- **Native**: real unwinding via an exception frame chain (`kof_throw_string`):
  frames restore `rsp`/`rbp` and jump to the handler; `finally` runs and the
  exception is rethrown; propagation across function frames works.
- **try/catch**: works on both targets. In Native, the FIRST catch of a try
  captures (no type dispatch between multiple catches).
- **finally**: always executed (normal path, caught path, propagation).

## Limitations (0.3.22-beta)

- No stack traces in Native
- Exceptions are **Strings** — `throw 42`/`catch (Int e)` geram bytecode
  inválido no JVM (02/09); use `String?` para ausência como valor
- Native exceptions propagate via unwinding (not fatal); `finally` always runs