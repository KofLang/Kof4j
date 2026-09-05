# Referência de Exceções em Kof

**Exceções são Strings** — `throw "mensagem"` / `catch (String e)`. Não há
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

## Erros de Runtime

| Erro | Gatilho | Mensagem |
|-------|---------|---------|
| Null pointer | Acessar objeto null | "Runtime error: null pointer access" |
| Array bounds | Índice fora do intervalo | "Runtime error: array index out of bounds" |
| Panic | `kof_panic()` | Mensagem customizada |

## Comportamento

- **JVM**: exceções propagam pela exception table da JVM; a String lançada é
  embrulhada em um `RuntimeException` e desembrulhada de volta no catch.
- **Native**: unwinding real via uma cadeia de frames de exceção
  (`kof_throw_string`): os frames restauram `rsp`/`rbp` e saltam para o handler;
  o `finally` executa e a exceção é relançada; a propagação entre frames de
  função funciona.
- **try/catch**: funciona nos dois targets. No Native, o PRIMEIRO catch de um try
  captura (sem dispatch de tipo entre múltiplos catches).
- **finally**: sempre executado (caminho normal, caminho capturado, propagação).

## Limitações (0.2.6-beta)

- Sem stack traces no Native
- Exceções são **Strings** — `throw 42`/`catch (Int e)` geram bytecode inválido
  no JVM (02/09); use `String?` para ausência como valor
- Exceções no Native propagam via unwinding (não fatal); o `finally` sempre roda