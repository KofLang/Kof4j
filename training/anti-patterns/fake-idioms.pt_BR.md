[English](fake-idioms.md) | [Português](fake-idioms.pt_BR.md)

# Anti-pattern — Fake Idioms

## Name

Ensinar ou usar como idiomático algo que não existe na linguagem.

## Problem

O modelo pode inventar `users.map(...)`, `Option<T>`, `async/await`,
`for user in users` (sem `var`), primary constructors, pattern matching —
porque existem em outras linguagens. Código assim **não compila** ou
**compila por acidente** com semântica errada.

## Status real (verificado no compilador — 0.5.0-beta, Sep 2026)

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
| `String?` / `Int?` null safety + `if (x != null)` narrowing | ✅ Implemented (desde 0.2.6-beta, NullableType + isAssignable; literal `= null` rejeitado SEM048 desde 10/09). ⚠️ `Int?` nullable **dobra `null`→`0` silencioso** (bug aberto #259/D-NULL-INTENT, §125) — o "✅" é o caminho String/class; um **record** `T?` null comparado com `null` também dava NPE (§262), consertado 17/09 |
| Pattern matching `switch (x) { case String s: ... }` + `instanceof`/`as` | ✅ Implemented |
| Pattern record destructuring `case Point(x, y):` | ✅ Implemented (Parser PatternExpr fieldVars, desde 0.2.6-beta) |
| Switch como expressão `var r = switch (x) { case A -> b; default -> c }` | ✅ Implemented (SYN001, 03/09 — 3 targets + riscv64/aarch64; `default` obrigatório ou exaustividade de enum, senão `SEM032`) |
| `spawn` / `await` com `Handle<T>` e unboxing | ✅ 3 targets (JVM virtual threads; Native pthread — CONC001 fechado 31/08; JS event-loop — CONC003 fechado 03/09) |
| Primary constructor `class X(...)` / `record` | ✅ Implemented (record-style desde 0.0.5) |
| `Thread` / `Executor` (APIs de plataforma) | ❌ Unavailable — nunca use (`spawn` é a intenção) |
| `Option<T>` genérico | ❌ Planned — use `String?` para nulabilidade |
| `Bool?` / `Boolean?` (bool nulável por sintaxe) | ❌ REJEITADO desde 19/09 — **D-TROOL**: `Bool` tem exatamente dois valores (`SEM095`). Para true/false/desconhecido a lingua tem **`Troolean`** (`&&`/`||`/`!` de Kleene, `println` = `true`/`false`/`null`, DECISIONS.md §D-TROOL; prova `TrooleanLawE2ETest`) |
| `Int.MAX_VALUE` / `Long.MIN_VALUE` / `Int.SIZE` / `Int.<campo>` | ❌ Unavailable (bug 99, 10/09) — tipos primitivos **não têm campos/constantes estáticas**. `SEM050`: rejeitado no typer (era aceito em silêncio e gerava `NoClassDefFoundError "?"`/SIGSEGV, e `var x = Int.MAX_VALUE` **crashava o compilador**). Use o **literal** (`2147483647`, `9223372036854775807`, `-2147483648`) ou `as`. (`String.valueOf(42)`/`String.format(...)` são o caminho oposto: **métodos** com parênteses, implementados — a isenção vale só p/ posição de *tipo*, `x: Int`/`x as Int`, não p/ *field access*.) |
| `l.remove(elemento)` por VALOR (Java `List.remove(Object)`) | ❌ Unavailable — `remove/get/set` de List pegam **índice Int** e `remove` devolve o elemento (learn/12). Por valor use `contains(x)` / loop com `get(i)`. `SEM055` rejeita não-Int no índice (bug 122: era aceito → JVM VerifyError, Native pointer-as-index) |
| `l.add(i, v)` (**inserção** posicional do Java) | ❌ Unavailable — `add`/`push`/`append` de List pegam exatamente **um** elemento e o adicionam ao fim; **não existe inserção posicional** (learn/12). `SEM072` rejeita a forma de 2 args no typer compartilhado (#336: era aceito → JVM VerifyError, JS/Script append silencioso errado). Para colocar um valor num índice, `set(i, v)` SUBSTITUI um elemento existente |
| `listOf("a").add(5)` / `setOf("a").add(5)` / `mapOf("k",1).put(5,"v")` (coleta HETEROGÊNEA) | ❌ Unavailable — coleções Kof são **HOMOGÊNEAS** (bug 126 decisão da mantenedora 11/09): depois que o tipo PINA (pelo literal `listOf("a")` ou pelo primeiro add/put), escrever tipo ≠ é rejeitado em compile-time com `SEM056`. Não é só o Native que quebra (scan tag String sobre Int cru → SIGSEGV): no JVM `List.add` hetero já dá **VerifyError** na carga e `Map.put` valor-hetero dá **ClassCastException** no get. A rejeição é universal (erro de tipo é erro em todo alvo). **O que NÃO é rejeitado:** query-side (`get(k)`/`contains(x)` com tipo ≠ → miss seguro: null/false, nunca crash); widening numérico (`Int` em `List<Long>`); o **primeiro** add/put num container `Unknown` (pina, não polui); e `Unknown`/nullable de função (SG-008). Use coleções do mesmo tipo — se precisa de "tipos diferentes", modelem **records/unions**, não um `List<Object>` |
| `for user in users` (sem var) | ❌ Unavailable |
| Array literals `{1, 2, 3}` / `[1,2,3]` | ❌ Unavailable — use `new Int[n]` + `listOf` |
| `async`/`await` (JS-style), `let`/`const` | ❌ Unavailable — use `spawn`/`await` e `var`/`val` (KofScript **não** é JavaScript) |
| `fn` / `fun` / `func` (qualquer posição) | ❌ Unavailable — palavras **reservadas** (06/09, SG-001): não existem no Kof, nem como keyword nem como identificador (nome de função, variável, parâmetro, campo). Em posição de declaração: `PARSE085`; em outra: `PARSE037`/`PARSE023`/… Use `Tipo nome(...) { }` ou `nome(...): Tipo { }`. **Nem em KofScript** — `.ks` é Kof puro, não JavaScript |
| `extern` com callback que NÃO é escalar/síncrono/não-escapante | ❌ Unavailable — o callback do R3 (C2 ✅ 18/09) liga parâmetro tipo-função com ABI de callback **primitiva**, só síncrono e não-escapante. `String`/struct/ponteiro na assinatura do callback ou callback **como retorno** → `FFI001` na JVM (medido, `JvmFfiCallbackE2ETest`); qualquer callback no JS → `FFI002` (paridade = fatia C3). Guardar o ponteiro para chamar DEPOIS (`atexit`/`signal`/async) também não liga — vida/GC-rooting seria R12. Mantenha o callback na chamada: `f(20, 22, (x: Int, y: Int) -> x + y)` |
| binding nativo feito à mão (cola JNI / wrappers `.java`/`.h` gerados em volta do `extern`) | ❌ Não é o idiom — `extern "lib" sim(T): R` É o binding (FFM na JVM, ponte host no JS; medido 18/09). Escrever scaffolding JNI/FFI em volta duplica o trabalho do compilador e fura os códigos de gap (`FFI001`/`FFI002`) que tornam as capacidades ausentes HONESTAS |
| `x as Char` (cast primitivo p/ char) | ✅ Implemented (I2C real, 01/09) |
| `longVal as Int` (narrowing Long→Int) | ✅ Implemented (L2I real, 01/09) |
| `new Long[n]` (array de 64 bits) | ✅ Implemented (01/09) |
| `String.valueOf(x)` receiver estático builtin | ✅ Implemented (01/09) |
| `Set<T>` como tipo declarado (campo/retorno/param) | ✅ Implemented (02/09 — descriptor JVM `kof.Set` → `java/util/HashSet`) |
| Retorno/método com tipo genérico em classe (`List<String> foo()`) | ✅ Implemented (02/09 — parser parse-then-decide) |
| Forma prefixada nullable `String? s` (tipo antes do nome) e retorno `String? f()` | ✅ Implemented (02/09 — statements, funções e classes). NOTA: inicializar com `= null` é SEM048 desde 10/09 — null só chega ao `T?` via API |
| `Map.get` devolvendo `V?` para valores de referência | ✅ Implemented (02/09 — ausência = null, narrowing) |
| `::twice` / referencia nua de funcao nomeada (`val f = twice`, `listOf(twice)`) | envoltorio lambda: `val f = (x: Int) -> twice(x)`; `listOf((x: Int) -> twice(x))` — medido 18/09: ref nua = SEM011, envoltorio = `42` |

## Bad example (ainda não compila)

```kof
// NÃO COMPILA — array literal não existe
var nums = [1, 2, 3]

// NÃO COMPILA — Option genérico não existe
var maybe = Option.of(x)

// NÃO COMPILA — for sem var
for (user in users) { }

// NÃO COMPILA — a cláusula `permits` NÃO existe (hábito Kotlin).
// `sealed` em si É válido agora (modificador contextual, 0.5.0-beta X5.1): o
// conjunto fechado é "subtipos no MESMO arquivo", não uma lista `permits`.
sealed class Resultado permits Sucesso, Erro { }   // `permits` → erro de PARSE
```

## Good example — o que existe hoje

```kof
// map/filter/reduce — implementado
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var soma = nums.reduce((a: Int, b: Int) -> a + b, 0)

// Null safety String?
String? maybe = mapOf("k", "x").get("k")   // null via API (sem `= null` — SEM048)
if (maybe != null) {
    println(maybe.length)
}
var s: String = maybe   // erro SEM021 — não atribuível sem check

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

## "Kof não é Java/Kotlin/C#/JS" — issue pedindo para virar outra língua NÃO é bug

**Regra (ABSOLUTA, mantenedora 18/09 — `AGENTS.md` §8, `DECISIONS.md`
D-NOT-JAVA).** O Kof tem sintaxe própria e única. Quando um pedido
(issue/PR) quer um construto que só existe porque é Java, Kotlin, C# ou
JavaScript **traduzido**, a rejeição do compilador é **correta e esperada** —
a issue é **não-procedente**: responda uma vez com o idiom do Kof que
substitui e feche. Nunca implemente a feature estrangeira; nunca "conserte o
diagnóstico" de uma rejeição correta. Só vira bug real se o Kof *promete* o
construto neste corpus/docs e o compilador *discorda da própria doc*.

| ❌ Estrangeiro (Java/Kotlin/C#/JS) | ✅ Idiom do Kof que substitui |
|---|---|
| `StringBuilder` | `+` / `+=` (concatenação já é eficiente) |
| `val`/`var`/`let` top-level | dentro de função, ou campo de `class` |
| keyword `fun name()` / `val` | `String name() { }` (tipo antes do nome) |
| `v is Car` (type-check Kotlin) | `if (v instanceof Car) { var c = v as Car … }` ou `case Car c:` no `switch` |
| `"""três aspas"""` | strings normais `"…"` (sem literal raw/bloco). Rejeitado com `LEX008` (#364: antes dobrava para string **vazia** e compilava em silêncio) |
| `Pair(a, b)` / `Triple` | um `record` com campos nomeados |
| `xs.any { it > 3 }` / `all`/`none`/`count { }` / `it` | `xs.filter((x: Int) -> x > 3)` e checar `.size()`; o parâmetro do lambda é **sempre explícito** |
| `mutableListOf()` | `listOf(...)` (a lista do Kof já é mutável) |
| `object` (singleton Kotlin) | uma `class` com campos + construtor, ou funções top-level |
| Elvis `a ?: b`, safe-call `x?.y`, `x!!` | `if (x != null) …` (nulabilidade por narrowing) |
| intervalos `0..n` / `1 until n` | `for (var i = 0; i < n; i++) { … }` |
| argumento nomeado `f(p = v)` | argumentos posicionais na ordem da declaração |
| `class Box(size: Int) { corpo }` primário+corpo | `record Box(Int size)` (sem corpo) **ou** `class` com `constructor(Int size)` explícito |
| `catch (e: Exception)` tipado | `catch (String e)` (exceção no Kof **é** String; `throw "msg"`) |
| `open` / `override` | métodos simples — o Kof faz dispatch por assinatura, sem modificador |
| `let` / `const` / `async fn` | `var`/`val`; `spawn`/`await` |
| destructuring `for ((k, v) in map)` | `map.keys()` e depois `map.get(k)` |
| indexar String com `s[0]` | `s.charAt(0)` |
| `Char c = "x"` / `obj.campoChar = "x"` (String em `Char`) | literal de char `Char c = 'x'` — desde 20/09 SEM012 rejeita o store String em char em todo lugar (local e campo, §368 `5cd078c1`) |
| interpolação `"x${n}"` (Kotlin/GString) | `"x" + n` — `${…}` dentro de string Kof é **texto literal** (sem diagnóstico, por contrato — `lexical-structure.md` §4.1, sonda) |
| lista de interfaces com `:` (`class Foo: A, B`) | `class Foo implements A, B { }` |
| propriedade `val name: String` num `interface` | acessor-método: `interface I { String name() }` |
| `n.abs()` / `n.equals(o)` / `n.toChar()` (métodos em **primitivo**) | `math.abs(n)`, `a == b`, `n as Char` — primitivos só têm `toString()` e as conversões `toInt()`/`toLong()`/`toFloat()`/`toDouble()`. Rejeitado com `SEM074` (#362 ✅ CORRIGIDA 18/09: a chamada fora da lista passava no `check` e morria no load — `ClassFormatError`, dono do Methodref vazio) |
| `l.sort()` / `l.indexOf(x)` num `List` (API Java) | a API de `List` do Kof é `add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce`; ache a posição com `for` + `get(i)` (medido: `idx=2`); ordene fora da lista (interop) — não há promessa de `sort`/`indexOf` |
| `m.containsValue(v)` num `Map` (API Java) | `m.values()` + `contains` — a API de `Map` do Kof e `put/get/remove/containsKey/size/keys/values` (**`getOrDefault(k, d)` era fake ate 0.4.0 e virou REAL em 18/09 (`62bd455e`) — use-a**)
| `this(args)` auto-delegação de construtor (Java/C#) | o Kof promete só **`super(args)`** (classe-base, primeira instrução — `learn/07`); compartilhe o init via um método auxiliar que os dois construtores chamam (workaround medido `0/3`) |
| `File.exists(p)` / `File.readText(p)` / `File.readRange(p, o, n)` estáticos (as "formas estáticas" do `kof.io`) | ❌ **Não implementadas** — medido 28/09 em todos os alvos: o typer rejeita o receptor com `SEM011 Undefined variable or type: 'File'`. Use a **forma de instância**: `File(p).exists()`, `File(p).readText()`, `File(p).readRange(0, 4)`. (o `docs/stdlib/IO.md` listava "formas estáticas" como se existissem — corrigido 28/09; a tabela `KofIo.staticMethod` do compilador é inalcançável porque o typer nunca resolve o tipo como receptor estático) |

> Cruzamento: se o reproducer compilaria em **Kotlin/Java** por ser
> *traduzido*, é esta regra — rejeite. A família de bugs é só sobre código que
> **é** Kof válido e o compilador trata mal (ex.: #403 hijack do campo `log`,
> #313 `throw Exception` sem qualificar, #336 `l.add(i,v)`).
