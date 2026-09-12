# Anti-pattern — Sentinel Values

## Name

Usar um valor de dados para representar ausência/erro.

## Problem

`""`, `-1`, `0`, `"not found"` retornados para significar "não existe".
O consumidor precisa conhecer a convenção; valores legítimos podem colidir
com a sentinela; o erro não carrega informação.

## Bad example

```kof
Int findIndex(String key) {
    for (var i = 0; i < keys.size; i = i + 1) {
        if (keys.get(i) == key) {
            return i
        }
    }
    return -1
}
```

`-1` é a sentinela. O chamador precisa lembrar: `if (findIndex(k) >= 0)`.

## Why it is bad

- Convenção invisível (o tipo `Int` não diz que `-1` é especial).
- Erro e dado são indistinguíveis.
- Não há mensagem de erro.

## Preferred approach (erro real)

```kof
Int findIndex(String key) {
    for (var i = 0; i < keys.size; i = i + 1) {
        if (keys.get(i) == key) {
            return i
        }
    }
    throw "key not found: " + key
}
```

O consumidor trata com `try/catch` e recebe a informação do erro.

## Preferred approach (ausência como dado)

```kof
// ✅ String? / Int? com narrowing é o idiom
String? find(String key) {
    for (var e in entries) {
        if (e.key == key) return e.value
    }
    return null
}
var r = find("x")
if (r != null) {
    println(r.length)
}

// Alternativa quando erro e dado não se misturam: exception
String findOrThrow(String key) {
    for (var e in entries) { if (e.key == key) return e.value }
    throw "not found: " + key
}
```

> **Nota:** `Option<T>` genérico ainda é `planned` — para casos simples use `String?`/`Int?`. Sentinela (`""`/`-1`) só é `WORKAROUND` se `null` não modela o domínio e deve ser marcada explicitamente.

## Exceptions

- Convenções de APIs externas (ex.: índices retornam -1 em certos protocolos).