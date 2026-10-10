[English](fake-idioms.md) | [Português](fake-idioms.pt_BR.md)

# Anti-pattern — Fake Idioms

## Name

Teaching or using as idiomatic something that does not exist in the language.

## Problem

The model can invent `users.map(...)`, `Option<T>`, `async/await`,
`for user in users` (without `var`), primary constructors, pattern matching —
because they exist in other languages. Code like that **does not compile** or
**compiles by accident** with the wrong semantics.

## Real status (verified in the compiler — 0.5.0-beta, Sep 2026)

| Feature | Status |
|---|---|
| `List<T>` (add/get/set/size/contains/isEmpty/remove/clear/listOf) | ✅ Implemented (3 targets, free-list GC in Native) |
| `for (var x in coll)` | ✅ Implemented |
| `Map<K,V>` / `Set<T>` + `mapOf`/`setOf` | ✅ Implemented (JVM HashMap, Native asm, JS Map/Set since 0.1.0) |
| Higher-order `list.map/filter/reduce` | ✅ Implemented (since 0.2.6-beta, 3 targets) |
| `Box<T>` generics with primitive `T` (e.g.: `Box<Int>`) | ✅ Implemented (substituteTypeVariable fix 25/08) |
| Lambdas `(x: Int) -> expr` with mutable capture (via synthetic box Box0) | ✅ Implemented |
| If-expr `if (c) a else b` | ✅ Implemented |
| `json.encode` / `json.decode<T>` | ✅ Implemented (3 targets; JSN001/002/003 closed 31/08 — objects/records/arrays, FP XMM in Native) |
| `throw "msg"` / `try/catch/finally` | ✅ Implemented (JVM + Native unwinding) |
| `String?` / `Int?` null safety + `if (x != null)` narrowing | ✅ Implemented (since 0.2.6-beta, NullableType + isAssignable; `= null` literal rejected SEM048 since 10/09). ⚠️ nullable of `Int?` **folds `null`→`0` silently** (open bug #259/D-NULL-INTENT, §125) — the "✅" is the String/class path; a null **record** `T?` vs `null` also threw NPE (§262), fixed 17/09 |
| Pattern matching `switch (x) { case String s: ... }` + `instanceof`/`as` | ✅ Implemented |
| Pattern record destructuring `case Point(x, y):` | ✅ Implemented (Parser PatternExpr fieldVars, since 0.2.6-beta) |
| Switch as expression `var r = switch (x) { case A -> b; default -> c }` | ✅ Implemented (SYN001, 03/09 — 3 targets + riscv64/aarch64; `default` required or enum exhaustiveness, otherwise `SEM032`) |
| `spawn` / `await` with `Handle<T>` and unboxing | ✅ 3 targets (JVM virtual threads; Native pthread — CONC001 closed 31/08; JS event-loop — CONC003 closed 03/09) |
| Primary constructor `class X(...)` / `record` | ✅ Implemented (record-style since 0.0.5) |
| `Thread` / `Executor` (platform APIs) | ❌ Unavailable — never use (`spawn` is the intent) |
| Generic `Option<T>` | ❌ Planned — use `String?` for nullability |
| `Bool?` / `Boolean?` (boolo nulável por sintaxe) | ❌ REJECTED since 19/09 — **D-TROOL**: `Bool` has exactly two values (`SEM095`). For true/false/unknown the language has **`Troolean`** (Kleene `&&`/`||`/`!`, `println` = `true`/`false`/`null`, DECISIONS.md §D-TROOL; proof `TrooleanLawE2ETest`) |
| `Int.MAX_VALUE` / `Long.MIN_VALUE` / `Int.SIZE` / `Int.<campo>` | ❌ Unavailable (bug 99, 10/09) — primitive types **have no static fields/constants**. `SEM050`: rejected in the typer (it was accepted silently and generated `NoClassDefFoundError "?"`/SIGSEGV, and `var x = Int.MAX_VALUE` **crashed the compiler**). Use the **literal** (`2147483647`, `9223372036854775807`, `-2147483648`) or `as`. (`String.valueOf(42)`/`String.format(...)` are the opposite path: **methods** with parentheses, implemented — the exemption applies only to *type* position, `x: Int`/`x as Int`, not to *field access*.) |
| `l.remove(elemento)` by VALUE (Java `List.remove(Object)`) | ❌ Unavailable — List `remove/get/set` take an **Int index** and `remove` returns the element (learn/12). By value use `contains(x)` / a loop with `get(i)`. `SEM055` rejects non-Int in the index (bug 122: it was accepted → JVM VerifyError, Native pointer-as-index) |
| `l.add(i, v)` (Java positional **insert**) | ❌ Unavailable — List `add`/`push`/`append` take exactly **one** element and append it; there is **no positional insert** (learn/12). `SEM072` rejects the 2-arg form in the shared typer (#336: it was accepted → JVM VerifyError, JS/Script silent wrong-append). To place a value at an index, `set(i, v)` replaces an existing element |
| `listOf("a").add(5)` / `setOf("a").add(5)` / `mapOf("k",1).put(5,"v")` (HETEROGENEOUS collection) | ❌ Unavailable — Kof collections are **HOMOGENEOUS** (bug 126, maintainer's decision 11/09): after the type PINS (by the `listOf("a")` literal or by the first add/put), writing a type ≠ is rejected at compile-time with `SEM056`. It is not only Native that breaks (String tag scan over raw Int → SIGSEGV): on the JVM a heterogeneous `List.add` already gives a **VerifyError** on load and a heterogeneous-value `Map.put` gives a **ClassCastException** on get. The rejection is universal (a type error is an error on every target). **What is NOT rejected:** query-side (`get(k)`/`contains(x)` with a type ≠ → safe miss: null/false, never crash); numeric widening (`Int` in `List<Long>`); the **first** add/put in an `Unknown` container (it pins, does not pollute); and `Unknown`/nullable from a function (SG-008). Use collections of the same type — if you need "different types", model **records/unions**, not a `List<Object>` |
| `for user in users` (without var) | ❌ Unavailable |
| Array literals `{1, 2, 3}` / `[1,2,3]` | ❌ Unavailable — use `new Int[n]` + `listOf` |
| `async`/`await` (JS-style), `let`/`const` | ❌ Unavailable — use `spawn`/`await` and `var`/`val` (KofScript **is not** JavaScript) |
| `fn` / `fun` / `func` (any position) | ❌ Unavailable — **reserved** words (06/09, SG-001): they do not exist in Kof, neither as a keyword nor as an identifier (function name, variable, parameter, field). In declaration position: `PARSE085`; elsewhere: `PARSE037`/`PARSE023`/… Use `Tipo nome(...) { }` or `nome(...): Tipo { }`. **Not even in KofScript** — `.ks` is pure Kof, not JavaScript |
| `extern` callback that is NOT scalar/synchronous/non-escaping | ❌ Unavailable — the R3 callback (C2 ✅ 18/09) binds a function-typed parameter with a **primitive** callback ABI, synchronous and non-escaping only. A `String`/struct/pointer in the callback signature or a callback **as return** → `FFI001` on the JVM (measured, `JvmFfiCallbackE2ETest`); any callback on JS → `FFI002` (parity = slice C3). Storing the pointer to call LATER (`atexit`/`signal`/async) is not bindable either — lifetime/GC-rooting would be R12. Keep the callback in the call: `f(20, 22, (x: Int, y: Int) -> x + y)` |
| hand-rolled native binding (JNI glue / generated `.java`/`.h` wrappers around `extern`) | ❌ Not the idiom — `extern "lib" sym(T): R` IS the binding (JVM FFM, JS host bridge; measured 18/09). Writing JNI/FFI scaffolding around it duplicates the compiler's job and bypasses the gap codes (`FFI001`/`FFI002`) that make missing capabilities HONEST |
| `x as Char` (primitive cast to char) | ✅ Implemented (real I2C, 01/09) |
| `longVal as Int` (narrowing Long→Int) | ✅ Implemented (real L2I, 01/09) |
| `new Long[n]` (64-bit array) | ✅ Implemented (01/09) |
| `String.valueOf(x)` builtin static receiver | ✅ Implemented (01/09) |
| `Set<T>` as a declared type (field/return/param) | ✅ Implemented (02/09 — JVM descriptor `kof.Set` → `java/util/HashSet`) |
| Return/method with a generic type in a class (`List<String> foo()`) | ✅ Implemented (02/09 — parser parse-then-decide) |
| Prefixed nullable form `String? s` (type before name) and return `String? f()` | ✅ Implemented (02/09 — statements, functions and classes). NOTE: initializing with `= null` is SEM048 since 10/09 — null reaches `T?` only via API |
| `Map.get` returning `V?` for reference values | ✅ Implemented (02/09 — absence = null, narrowing) |

## Bad example (still does not compile)

```kof
// DOES NOT COMPILE — array literal does not exist
var nums = [1, 2, 3]

// DOES NOT COMPILE — generic Option does not exist
var maybe = Option.of(x)

// DOES NOT COMPILE — for without var
for (user in users) { }

// DOES NOT COMPILE — the `permits` clause does NOT exist (the Kotlin habit).
// `sealed` itself IS valid now (contextual modifier, 0.5.0-beta X5.1): the
// closed set is "subtypes in the SAME file", not a `permits` list.
sealed class Resultado permits Sucesso, Erro { }   // `permits` → PARSE error
```

## Good example — what exists today

```kof
// map/filter/reduce — implemented
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var soma = nums.reduce((a: Int, b: Int) -> a + b, 0)

// Null safety String?
String? maybe = mapOf("k", "x").get("k")   // null via API (no `= null` — SEM048)
if (maybe != null) {
    println(maybe.length)
}
var s: String = maybe   // SEM021 error — not assignable without a check

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
// ...or as an EXPRESSION (SYN001) when the switch produces a value:
var desc = switch (obj) {
    case String s -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}
if (p instanceof Point) {
    var q = p as Point
}

// Box<T> with primitive
var b = Box<Int>(42)
println(b.get())

// Mutable capture
var offset = 10
var f = (x: Int) -> x + offset   // OK — synthetic box

// Primary constructor
class User(String name, Int age) { }
var u = User("Mel", 30)
```

## Why it is bad

A model that "learns" nonexistent features produces code the compiler
rejects — or worse, code that compiles with another semantics. The corpus must
teach the exact boundary of what exists.

## Rule

Before using a feature, check the status table.
When the feature does not exist: use the real alternative OR mark `WORKAROUND`.

## Exceptions

- None — fake idioms are never acceptable in the corpus.

> **Lexer gotcha (verified 09/09):** the Kof lexer pre-processes `\uXXXX` in
> strings **before** forming the token (Java-style). `\u0027` inside a string
> becomes a literal `'` and can blow up the parse (LEX004 "unterminated char") at
> token boundaries. For quotes in a test-expected string, prefer **avoiding the
> quote** in the assert (e.g.: test `&amp;quot;` → `&quot;` instead of embedding `"`/
> `'` in the expected literal).

## "Kof is not Java/Kotlin/C#/JS" — an issue asking to become another language is NOT a bug

**Rule (ABSOLUTE, maintainer 18/09 — `AGENTS.md` §8, `DECISIONS.md`
D-NOT-JAVA).** Kof has its own unique syntax. When a request (issue/PR) asks
for a construct that only exists because it is **translated** Java, Kotlin,
C# or JavaScript, the compiler's rejection is **correct and expected** — the
issue is **not-valid**: answer once with the Kof idiom that replaces it and
close it. Never implement the foreign feature; never "fix the diagnostic" of a
correct rejection. It is only a real bug if Kof *promises* the construct in
this corpus/docs and the compiler *disagrees with its own docs*.

| ❌ Foreign (Java/Kotlin/C#/JS) | ✅ Kof idiom that replaces it |
|---|---|
| `StringBuilder` | `+` / `+=` (concat is already efficient) |
| top-level `val`/`var`/`let` | inside a function, or a `class` field |
| `fun name()` / `val` keyword | `String name() { }` (type before the name) |
| `v is Car` (Kotlin type-check) | `if (v instanceof Car) { var c = v as Car … }` or `case Car c:` in `switch` |
| `"""triple quotes"""` | normal `"…"` strings (no raw/block literal). Rejected with `LEX008` (#364: it used to fold to an **empty** string and compile silently) |
| `Pair(a, b)` / `Triple` | a `record` with named fields |
| `xs.any { it > 3 }` / `all`/`none`/`count { }` / `it` | `xs.filter((x: Int) -> x > 3)` then check `.size()`; the lambda param is **always explicit** |
| `mutableListOf()` / `setOf(...)` variadic is fine | `listOf(...)` (Kof lists are already mutable) |
| `object` (Kotlin singleton) | a `class` with fields + constructor, or top-level functions |
| `a ?: b` (Elvis), `x?.y` (safe call), `x!!` | `if (x != null) …` (nullability by narrowing) |
| `0..n` / `1 until n` ranges | `for (var i = 0; i < n; i++) { … }` |
| `f(p = v)` named args | positional args in declaration order |
| `class Box(size: Int) { body }` primary+body | `record Box(Int size)` (no body) **or** a `class` with explicit `constructor(Int size)` |
| `catch (e: Exception)` typed catch | `catch (String e)` (Kof exceptions **are** Strings; `throw "msg"`) |
| `open` / `override` | plain methods — Kof dispatches by signature, no modifiers |
| `let` / `const` / `async fn` | `var`/`val`; `spawn`/`await` |
| `Map.Entry` destructured `for ((k, v) in map)` | `map.keys()` then `map.get(k)` |
| `s[0]` indexing a `String` | `s.charAt(0)` |
| `Char c = "x"` / `obj.charField = "x"` (String into `Char`) | char literal `Char c = 'x'` — from 20/09 SEM012 rejects the String store everywhere (local and field, §368 `5cd078c1`) |
| `"x${n}"` string interpolation (Kotlin/GString) | `"x" + n` — `${…}` inside a Kof string is **literal text** (no diagnostic, by contract — `lexical-structure.md` §4.1, probe) |
| `class Foo: A, B` (Kotlin interface list via `:`) | `class Foo implements A, B { }` |
| `val name: String` property in an `interface` | a method accessor: `interface I { String name() }` |
| `n.abs()` / `n.equals(o)` / `n.toChar()` (methods on a **primitive**) | `math.abs(n)`, `a == b`, `n as Char` — primitives only have `toString()` and the `toInt()`/`toLong()`/`toFloat()`/`toDouble()` conversions. Rejected with `SEM074` (#362 ✅ FIXED 18/09: the unlisted call used to pass `check` and die at class load — `ClassFormatError`, empty Methodref owner) |
| `::twice` / bare named-function reference (`val f = twice`, `listOf(twice)`) | lambda wrapper: `val f = (x: Int) -> twice(x)`; `listOf((x: Int) -> twice(x))` — measured 18/09: bare ref = SEM011, wrapper = `42` |
| `l.sort()` / `l.indexOf(x)` on a `List` (Java API) | Kof `List` API is `add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce`; find position with a `for` + `get(i)`; order by sorting outside the list (interop) — no `sort`/`indexOf` promise |
| `m.containsValue(v)` on a `Map` (Java API) | `m.values()` + `contains` — Kof `Map` API is `put/get/remove/containsKey/size/keys/values` (**`getOrDefault(k, d)` was fake until 0.4.0 and became REAL on 18/09 (`62bd455e`) — use it**)
| `this(args)` constructor self-delegation (Java/C#) | Kof promises **`super(args)`** only (base class, first statement — `learn/07`); share init via a helper method both constructors call (measured working) |
| Static `File.exists(p)` / `File.readText(p)` / `File.readRange(p, o, n)` (the `kof.io` "static forms") | ❌ **Not implemented** — measured 28/09 on all targets: the typer rejects the receiver with `SEM011 Undefined variable or type: 'File'`. Use the **instance form**: `File(p).exists()`, `File(p).readText()`, `File(p).readRange(0, 4)`. (`docs/stdlib/IO.md` used to list "static forms" as if they existed — corrected 28/09; the `KofIo.staticMethod` table in the compiler is unreachable because the typer never resolves the type as a static receiver) |

> Cross-check: if the reproducer would compile in **Kotlin/Java** because it is
> *translated*, it is this rule — reject it. The bug family is only about code
> that **is** valid Kof and the compiler mishandles (e.g. #403 `log` field
> hijack, #313 unqualified `throw Exception`, #336 `l.add(i,v)`).
