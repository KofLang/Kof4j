# Anti-pattern — Fake Idioms

## Name

Ensinar ou usar como idiomático algo que não existe na linguagem.

## Problem

O modelo pode inventar `users.map(...)`, `Option<T>`, `async/await`,
`for user in users` (sem `var`), primary constructors, pattern matching —
porque existem em outras linguagens. Código assim **não compila** ou
**compila por acidente** com semântica errada.

## Status real (verificado no compilador — 0.3.22-beta, Sep 2026)

| Feature | Status |
|---|---|
| `List<T>` (add/get/set/size/contains/isEmpty/remove/clear/listOf) | ✅ Implemented (3 targets, free-list GC no Native) |
| `for (var x in coll)` | ✅ Implemented |
| `Map<K,V>` / `Set<T>` + `mapOf`/`setOf` | ✅ Implemented (JVM HashMap, Native asm, JS Map/Set desde 0.1.0) |
| Higher-order `list.map/filter/reduce` | ✅ Implemented (desde 0.2.6-beta, 3 targets) |
| `Box<T>` generics com `T` primitivo (ex.: `Box<Int>`) | ✅ Implemented (fix substituteTypeVariable 25/08) |
| Lambdas `(x: Int) -> expr` com captura mutável (via box sintético Box0) | ✅ Implemented |
| If-expr `if (c) a else b` | ✅ Implemented |
| `json.encode` / `json.decode<T>` | ✅ Implemented (3 targets; JSN001/002/003 fechados 31/08 — objetos/records/arrays, FP XMM no Native) |
| `throw "msg"` / `try/catch/finally` | ✅ Implemented (JVM + Native unwinding) |
| `String?` / `Int?` null safety + `if (x != null)` narrowing | ✅ Implemented (desde 0.2.6-beta, NullableType + isAssignable) |
| Pattern matching `switch (x) { case String s: ... }` + `instanceof`/`as` | ✅ Implemented |
| Pattern record destructuring `case Point(x, y):` | ✅ Implemented (Parser PatternExpr fieldVars, desde 0.2.6-beta) |
| Switch como expressão `var r = switch (x) { case A -> b; default -> c }` | ✅ Implemented (SYN001, 03/09 — 3 targets + riscv64/aarch64; `default` obrigatório ou exaustividade de enum, senão `SEM032`) |
| `spawn` / `await` com `Handle<T>` e unboxing | ✅ 3 targets (JVM virtual threads; Native pthread — CONC001 fechado 31/08; JS sequencial — CONC003 parcial) |
| Primary constructor `class X(...)` / `record` | ✅ Implemented (record-style desde 0.0.5) |
| `Thread` / `Executor` (APIs de plataforma) | ❌ Unavailable — nunca use (`spawn` é a intenção) |
| `Option<T>` genérico | ❌ Planned — use `String?` para nulabilidade |
| `Int.MAX_VALUE` / `Long.MIN_VALUE` / `Int.SIZE` / `Int.<campo>` | ❌ Unavailable (bug 99, 10/09) — tipos primitivos **não têm campos/constantes estáticas**. `SEM050`: rejeitado no typer (era aceito em silêncio e gerava `NoClassDefFoundError "?"`/SIGSEGV, e `var x = Int.MAX_VALUE` **crashava o compilador**). Use o **literal** (`2147483647`, `9223372036854775807`, `-2147483648`) ou `as`. (`String.valueOf(42)`/`String.format(...)` são o caminho oposto: **métodos** com parênteses, implementados — a isenção vale só p/ posição de *tipo*, `x: Int`/`x as Int`, não p/ *field access*.) |
| `l.remove(elemento)` por VALOR (Java `List.remove(Object)`) | ❌ Unavailable — `remove/get/set` de List pegam **índice Int** e `remove` devolve o elemento (learn/12). Por valor use `contains(x)` / loop com `get(i)`. `SEM055` rejeita não-Int no índice (bug 122: era aceito → JVM VerifyError, Native pointer-as-index) |
| `listOf("a").add(5)` / `setOf("a").add(5)` / `mapOf("k",1).put(5,"v")` (coleta HETEROGÊNEA) | ❌ Unavailable — coleções Kof são **HOMOGÊNEAS** (bug 126 decisão da mantenedora 11/09): depois que o tipo PINA (pelo literal `listOf("a")` ou pelo primeiro add/put), escrever tipo ≠ é rejeitado em compile-time com `SEM056`. Não é só o Native que quebra (scan tag String sobre Int cru → SIGSEGV): no JVM `List.add` hetero já dá **VerifyError** na carga e `Map.put` valor-hetero dá **ClassCastException** no get. A rejeição é universal (erro de tipo é erro em todo alvo). **O que NÃO é rejeitado:** query-side (`get(k)`/`contains(x)` com tipo ≠ → miss seguro: null/false, nunca crash); widening numérico (`Int` em `List<Long>`); o **primeiro** add/put num container `Unknown` (pina, não polui); e `Unknown`/nullable de função (SG-008). Use coleções do mesmo tipo — se precisa de "tipos diferentes", modelem **records/unions**, não um `List<Object>` |
| `for user in users` (sem var) | ❌ Unavailable |
| Array literals `{1, 2, 3}` / `[1,2,3]` | ❌ Unavailable — use `new Int[n]` + `listOf` |
| `async`/`await` (JS-style), `let`/`const` | ❌ Unavailable — use `spawn`/`await` e `var`/`val` (KofScript **não** é JavaScript) |
| `fn` / `fun` / `func` (qualquer posição) | ❌ Unavailable — palavras **reservadas** (06/09, SG-001): não existem no Kof, nem como keyword nem como identificador (nome de função, variável, parâmetro, campo). Em posição de declaração: `PARSE085`; em outra: `PARSE037`/`PARSE023`/… Use `Tipo nome(...) { }` ou `nome(...): Tipo { }`. **Nem em KofScript** — `.ks` é Kof puro, não JavaScript |
| `x as Char` (cast primitivo p/ char) | ✅ Implemented (I2C real, 01/09) |
| `longVal as Int` (narrowing Long→Int) | ✅ Implemented (L2I real, 01/09) |
| `new Long[n]` (array de 64 bits) | ✅ Implemented (01/09) |
| `String.valueOf(x)` receiver estático builtin | ✅ Implemented (01/09) |
| `Set<T>` como tipo declarado (campo/retorno/param) | ✅ Implemented (02/09 — descriptor JVM `kof.Set` → `java/util/HashSet`) |
| Retorno/método com tipo genérico em classe (`List<String> foo()`) | ✅ Implemented (02/09 — parser parse-then-decide) |
| Forma prefixada nullable `String? s = null` e retorno `String? f()` | ✅ Implemented (02/09 — statements, funções e classes) |
| `Map.get` devolvendo `V?` para valores de referência | ✅ Implemented (02/09 — ausência = null, narrowing) |

## Bad example (ainda não compila)

```kof
// NÃO COMPILA — array literal não existe
var nums = [1, 2, 3]

// NÃO COMPILA — Option genérico não existe
var maybe = Option.of(x)

// NÃO COMPILA — for sem var
for (user in users) { }
```

## Good example — o que existe hoje

```kof
// map/filter/reduce — implementado
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var soma = nums.reduce((a: Int, b: Int) -> a + b, 0)

// Null safety String?
String? maybe = null
if (maybe != null) {
    println(maybe.length)
}
var s: String = maybe   // erro SEM014 — não atribuível sem check

// Pattern matching + record destructuring
switch (obj) {
    case String s:
        println(s)
        break
    case Point(var x, var y):
        println(x + "," + y)
        break
    default:
        println("outro")
}
// ...ou como EXPRESSÃO (SYN001) quando o switch produz valor:
var desc = switch (obj) {
    case String s -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}
if (p instanceof Point) {
    var q = p as Point
}

// Box<T> com primitivo
var b = Box<Int>(42)
println(b.get())

// Captura mutável
var offset = 10
var f = (x: Int) -> x + offset   // OK — box sintético

// Primary constructor
class User(String name, Int age) { }
var u = User("Mel", 30)
```

## Why it is bad

Um modelo que "aprende" features inexistentes produz código que o compilador
rejeita — ou pior, código que compila com outra semântica. O corpus deve
ensinar a fronteira exata do que existe.

## Regra

Antes de usar uma feature, verifique a tabela de status.
Quando a feature não existe: use a alternativa real OU marque `WORKAROUND`.

## Exceptions

- Nenhuma — fake idioms nunca são aceitáveis no corpus.

> **Lexer gotcha (verificado 09/09):** o lexer do Kof pré-processa `\uXXXX` nas
> strings **antes** de formar o token (Java-style). `\u0027` dentro de string
> vira `'` literal e pode estourar o parse (LEX004 "unterminated char") em
> bordas de token. Para aspas em string-esperada de test, prefira **evitar a
> aspa** no assert (ex.: testar `&amp;quot;` → `&quot;` em vez de embutir `"`/
> `'` no literal esperado).
