# 14 — Exceptions

> **Status: implementado (JVM / Native / JS) — 0.3.22-beta — exemplos verificados no compilador**
>
> `throw`/`try`/`catch`/`finally` com unwinding real em JVM, Native e KofJS.
> Kof lança **Strings** (`throw "mensagem"` / `catch (String e)`), não
> instâncias de classe de exceção.

## throw

Kof lança um valor (a mensagem vai direto para o `catch`):

```kf
throw "valor inválido"
```

> **Importante (verificado 02/09):** a exceção é **String**.
> `throw 42` / `catch (Int e)` geram bytecode inválido no JVM — não use.
> Para ausência como valor, use `String?` (cap. 13).

## try/catch/finally

```kf
main() {
    try {
        var conexao = abrirConexao()
    } catch (String e) {
        println("erro: " + e)
    } finally {
        println("cleanup")    // roda sempre
    }
}
```

## Lançando valores contextualizados

A "identidade" da falha vem da própria mensagem:

```kf
User findUser(Int id) {
    if (id < 0) {
        throw "user not found: " + id
    }
    return User("u" + id)
}

class User(String name) { }

main() {
    try {
        var u = findUser(-1)
        println(u.name)
    } catch (String e) {
        println("caught: " + e)    // caught: user not found: -1
    } finally {
        println("cleanup")         // cleanup
    }
}
```

## Ausência vs erro

```kf
// Ausência (dado pode não existir) → String?
String? find(Int id) {
    if (id == 1) { return "mel" }
    return null
}

// Erro real (ausência é defeito) → throw
String findOrThrow(Int id) {
    if (id == 1) { return "mel" }
    throw "not found: " + id
}
```

## Limitações (02/09, verificadas)

- Exceções são **Strings** apenas — sem objeto de exceção.
- No Native, o primeiro `catch` de um `try` captura (sem despacho por tipo
  entre múltiplos catches).
- Sem stack trace no Native.

## Exercícios

1. Escreva `Double divide(Int a, Int b)` que lança `"division by zero"` quando
   `b == 0`; trate com `try/catch` no `main`.
2. Converta uma função que retorna `""` como "não encontrado" para `String?`
   (cap. 13) e depois para `throw` — explique quando usar cada um.
3. Verifique que `finally` roda no caminho normal, no capturado e no
   propagado.

## Próximo passo

[Pattern Matching →](15-pattern-matching.md)