[English](14-exceptions.md) | [Português](14-exceptions.pt_BR.md)

# 14 — Exceptions

> **Status: implementado (JVM / Native / JS) — 0.5.0-beta — exemplos verificados no compilador**
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

## `using` (recursos gerenciados)

Quando um valor precisa de cleanup, o `using` garante que o closer roda — no
sucesso E na exceção — sem `try/finally` escrito à mão:

```kf
main() {
    using (conn = db.connect("jdbc:h2:mem:demo"), db.close(conn)) {
        db.execute(conn, "CREATE TABLE t (id INT PRIMARY KEY)")
        println("table ready")
    } // db.close(conn) roda aqui, mesmo se o corpo lançar
}
```

Regras: o closer é explícito — escreva o idioma de close real do tipo
(`db.close(conn)`, `conn.close()`, `sse.close()`); o vínculo vive só dentro do
bloco; aninhe `using` para múltiplos recursos (o interno fecha primeiro).
(A URL H2 acima é JVM-hermética; outros alvos usam seu próprio DSN `db`.)

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