# 13 — Nullability

> **Status: implementado (JVM / Native / JS) — 0.3.22-beta — exemplos verificados no compilador**
>
> `Tipo?` (ex.: `String?`) declara que um valor **pode** ser `null`. O
> compilador exige um check (`if (x != null)`) antes de usar — e o narrowing
> foi corrigido no JVM em 02/09 (antes `s.length` com narrowing emitia
> bytecode inválido).

## O problema

`NullPointerException` é a causa mais comum de erros em Java:

```java
String nome = null;
System.out.println(nome.length());  // NullPointerException!
```

## A solução: `?`

```kf
String nome = "Mel"           // não pode ser null
String? apelido = null        // pode ser null
var outro: String? = "Kof"    // forma anotada (também válida)
```

## Narrowing: `if (x != null)`

```kf
String? nome = obterNome()    // pode vir null
if (nome != null) {
    println(nome.length)      // seguro — o check libera o acesso
} else {
    println("sem nome")
}
```

Acessar **sem** o check é erro de compilação:

```kf
var nome: String? = obterNome()
println(nome.length)   // ERRO: nome pode ser null — exige if (nome != null)
```

## A stdlib devolve `?` (02/09)

As funções de leitura da stdlib são tipadas de forma honesta — ausência é
`null`, não sentinela:

```kf
main() {
    var conteudo = readFile("config.json")     // String?
    if (conteudo != null) {
        println(conteudo.length)
    } else {
        println("arquivo não existe")
    }

    var linha = readLine()                     // String? — null no EOF
    if (linha != null) {
        println("linha: " + linha)
    }

    var m = mapOf("nome", "Mel")
    var v = m.get("nome")                      // V? — valores de referência
    if (v != null) {
        println(v.length)                      // 3
    }
}
```

> `Map.get` devolve `V?` para valores de **referência** (`Map<String, String>`).
> Para valores primitivos (`Map<String, Int>`) o tipo fica `V` — o modelo
> atual não representa ausência nesse caso; cheque com `contains`/`containsKey`.

## Nullable em funções e retornos

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

## Regra de ouro

- **Ausência como valor** (o dado pode não existir) → `String?`/`Tipo?` +
  `if (x != null)`.
- **Erro real** (a ausência é um defeito) → `throw "mensagem"` + `catch`.

```kf
String findOrThrow(Int id) {
    if (id == 1) { return "mel" }
    throw "not found: " + id
}
```

## Onde estamos (0.3.22-beta)

- ✅ `String?`, `Int?`, `Tipo?` no parser e type system (`NullableType`).
- ✅ Narrowing `if (x != null)` nos 3 targets — **JVM corrigido 02/09**
  (antes `s.length`/`s.substring(...)` com narrowing emitiam
  `getfield "?".length`/`"".substring` → erro de launcher/`ClassFormatError`).
- ✅ `Map.get` → `V?`, `readFile`/`readText`/`readLine` → `String?`.
- 🚧 Flow analysis mais profundo e operadores `?.` / `?:` ainda planejados.

## Exercícios

1. Escreva `String? saudacao(String? nome)` que devolve `"oi, X"` quando
   `nome != null` e `"oi"` caso contrário — use narrowing.
2. Leia um arquivo que pode não existir e trate os dois casos com
   `readFile`.
3. Por que `Map.get` de `Map<String, Int>` **não** devolve `Int?`? (dica:
   como `Int?` é armazenado no runtime).

## Próximo passo

[Exceptions →](14-exceptions.md)