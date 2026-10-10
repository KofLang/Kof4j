[English](collections.md) | [Português](collections.pt_BR.md)

# Idioms — Collections

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:**  0.5.0-beta (Sep 2026) (02 Sep 2026)

## What it is

`List<T>` is the language's ordered collection. Creation: `listOf(...)` or `new List<T>()`.
Available on JVM (ArrayList), Native (its own implementation with free-list GC) and JS (Array) with the same API.
`Map<K,V>` and `Set<T>` have existed since 0.1.0 on the 3 targets (JVM HashMap/HashSet, Native own asm, JS Map/Set).

## Real API (verified in the compiler — 0.5.0-beta)

```kof
var l = listOf(1, 2, 3, 4)
l.add(5)
var x = l.get(0)        // native bounds check via kof_list_get — no manual workaround
l.set(0, 9)
l.size                  // property, not a method
l.contains(3)
l.isEmpty()
var r = l.remove(1)       // remove by INDEX (Int), returns the element
// NEVER l.remove("x") (Java's by-value): SEM055 (bug 122) — to find by
// value use contains(x); to find the POSITION, indexOf(x) (0.4.0, #382).
l.clear()
var vazio = listOf<Int>()

// Higher-order (3 targets)
var dobrados = l.map((x: Int) -> x * 2)
var pares = l.filter((x: Int) -> x % 2 == 0)
var soma = l.reduce((a: Int, b: Int) -> a + b, 0)   // order: (lambda, init)
// `reduce(0, (a, b) -> ...)` (init, lambda) is also accepted

// Map / Set
var m = mapOf("a", 1)
m.put("b", 2)
var v = m.get("a")
var n = m.getOrDefault("b", 0)   // default when key absent (0.4.0, 4 targets)
var s = setOf(1, 2, 3)
s.add(4)
s.contains(2)
// Kof collections are HOMOGENEOUS: after the type PINS, add/put/set with a type ≠
// is rejected at compile-time (SEM056, bug 126 — it was not only Native that broke:
// on the JVM the heterogeneous add already gave a VerifyError). Numeric widening
// (Int in List<Long>) and the FIRST add (which pins a listOf()) pass. Querying by a
// type ≠ (m.get(5) on a Map<String,Int>, s.contains("x") on a Set<Int>) is a SAFE
// MISS (null/false), never an error — only WRITING is checked.

// As a class field, constructor param and method return (3 targets — 01/09)
class Bag(Set<Int> tags) {
    Set<Int> all() {
        return tags
    }
}
var b = Bag(setOf(1, 2, 3))
println(b.all().size())
```

Fix 01/09: `Set<T>`/`Map<K,V>` as a class field/return on the JVM — the mapper mapped only `List`→`ArrayList` (so `Set`/`Map` became `Lkof/Set;` → `NoClassDefFoundError`); now `HashSet`/`HashMap`. Parser: a class method with a generic return (`Set<Int> all(`) now parses (before it fell into the field branch). `KofMapSetTest.setMapAsFieldAndReturn`.

## Search, cut and order (0.4.0 — #382/#386, 4 targets)

```kof
val l: List<Int> = listOf(3, 1, 2, 1)
var i = l.indexOf(1)          // first occurrence, -1 when absent
var j = l.lastIndexOf(1)      // last occurrence, -1 when absent
val mid = l.subList(1, 3)     // [begin, end) — copy; begin==end yields empty
val all = l.subList(0, l.size)
val head = l.take(2)          // first 2 — clamped: take(9) = the whole list
val tail = l.drop(1)          // everything after the first 1 — drop(9) = empty
val win = l.slice(1, 2)       // 2 items from offset 1 — clamped; offset>size = empty
var grew = mid.addAll(l)      // true when the list changed (false: empty source)
val ordered: List<Int> = listOf(5, 4, 3)
ordered.sort()                // NATURAL order, in-place (no Comparator in Kof)

val m: Map<String, Int> = mapOf("a", 1)
var has = m.containsValue(1)             // scan by VALUE (containsKey is by key)
var prev = m.putIfAbsent("a", 9)         // V? — returns the previous and does NOT
prev = m.putIfAbsent("z", 9)             // overwrite; null when the key is new
```

- `sort()` accepts naturally ordered elements (Int/Long/Double/String…);
  a record or other without order → `SEM097` (shared compile-time gate).
  `Float` on Native → `NAT001` with an honest diagnostic (§349) — never a
  silent wrong order.
- `indexOf`/`lastIndexOf` search by value with the pinned type; searching a
  type ≠ is a safe MISS (`-1`), same rule as `contains` (SEM056 only checks
  the WRITE).
- `subList` out-of-range dies with the bounds check (measured:
  `IndexOutOfBoundsException: toIndex = N` on the JVM; `kof_bounds_error`
  on Native) — same family as `get(i)`.
- `take(n)`/`drop(n)`/`slice(offset, limit)` return a materialized copy (never
  a live view) and clamp honestly: `n`/`offset` past the end give the whole
  list / empty, never an error. A negative `n`/`offset`/`limit` is a named
  runtime error — `"PAGINATION: count must be >= 0"` (`take`/`drop`) or
  `"PAGINATION: limit/offset must be >= 0"` (`slice`). Available on all
  targets (D-PAGINATION, pagination P1).
- `putIfAbsent` returns `V?` (D-NULL-INTENT): narrow with `if (prev != null)`.
- The Java idiom `Collections.sort(l, comparator)` does not exist in Kof:
  `sort()` is natural order, period. To search a position, `indexOf(x)`
  (not a manual `get(i)` loop with `||`).

## Windowing `Window<T>` (D-PAGINATION P2 — `import kof.pagination`)

```kof
import kof.pagination

val l: List<Int> = listOf(10, 20, 30, 40, 50)
val w = window(l, 2, 1)          // 2 items from offset 1 -> Window<Int>
val w2 = window(l, 2, 1, true)   // same, total computed locally (opt-in)
w.items()        // List<Int> — the materialized window (possibly empty)
w.offset()       // Int
w.limit()        // Int
w.hasPrevious()  // Bool — offset > 0
w.hasNext()      // Bool — exact with total; optimistic (items.size == limit) without it
w.total()        // Long? — null unless requested
```

- `Window<T>` and `window(items, limit, offset[, withTotal])` are written in Kof
  (`D-KOF-FIRST-IMPL`) and injected flat on an explicit `import kof.pagination` —
  no per-backend runtime; on all targets (JVM/Native/JS/Script). A user-declared
  `Window`/`window` collides and skips the injection.
- `limit`/`offset` are `Int >= 0`; a negative value is a named error
  (`"PAGINATION: limit/offset must be >= 0"`), never a silent clamp.
- `offset > size` → empty window with `hasPrevious = true` (no error);
  `limit == 0` → empty window. `hasNext` is exact when `total` is present,
  optimistic otherwise. `total` is opt-in and never triggers a `COUNT`.
- The core knows nothing about SQL or HTTP (`orm.window`/`pageRequest` are
  platform faces of later slices).

## Quantifiers `any`/`all`/`none` (D-MULTIPARADIGMA-PHASE1A slice 1a, all targets)

```kof
var xs = listOf(1, 2, 3)
var hasBig = xs.any((x) -> x > 2)     // true — stops at the first match
var allPos = xs.all((x) -> x > 0)     // true — vacuous on empty
var noBig = xs.none((x) -> x > 9)     // true — vacuous on empty
```

Eager short-circuit loops: the predicate runs until the answer is known, then
stops (a `throw` past the decision point never fires). Vacuous: `all`/`none`
are true on empty, `any` is false (`none` ≡ ¬`any`, maintainer decision 28/09).
Predicates use the `filter` truthiness rule. Bare `(x)` params inherit the
element type (SG-012, generalized); no-`take`/`drop`/`slice` here — those are
pagination P1.

## `find` + `count(pred)` (D-MULTIPARADIGMA-PHASE1A slice 1b, all targets)

```kof
var xs = listOf(1, 2, 3)
var f = xs.find((x) -> x > 1)   // 2 — first match or null (like Map.get-missing)
var m = xs.find((x) -> x > 9)   // null — test with `== null` (the V? idiom)
var n = xs.count((x) -> x > 1)  // 2 — bare count() keeps meaning size
```

On Native, a found primitive is boxed behind the scenes (raw slots vs
boxed `T?` consumers); miss stays null/0 per target. Block lambdas yield only via
explicit `return` (SEM033).

## `forEach` (D-MULTIPARADIGMA-PHASE1A slice 1c, all targets)

```kof
var xs = listOf(1, 2, 3)
xs.forEach((x) -> println(x * 10))   // 10, 20, 30 — effect only, no allocation
listOf().forEach((x) -> println(x))  // vacuous: prints nothing
```

## `flatMap` (D-MULTIPARADIGMA-PHASE1A slice 1d, all targets)

```kof
var xs = listOf(1, 2, 3)
var ys = xs.flatMap((x) -> listOf(x, x * 10))   // [1, 10, 2, 20, 3, 30]
var e = listOf().flatMap((x) -> listOf(x))      // empty in, empty out
```

## `distinct` (D-MULTIPARADIGMA-PHASE1A slice 1e, all targets)

```kof
var xs = listOf(3, 1, 2, 1, 3)
var d = xs.distinct()   // [3, 1, 2] — first occurrences in order
```
Equality is the `contains` rule per target (String content, rest like
`contains`); empty in, empty out.

## `sorted` (D-MULTIPARADIGMA-PHASE1A slice 1g, all targets)

```kof
var xs = listOf(3, 1, 2)
var s = xs.sorted()   // [1, 2, 3] — fresh copy, xs unchanged
var d = xs.sorted((a: Int, b: Int) -> b - a)   // [3, 2, 1] — comparator: negative/zero/positive
```
Natural order needs a natural domain (Int/Long/Double/Float/Bool/Char/String — SEM097 otherwise); with a comparator the lambda defines the order (records welcome). Stable for pure comparators; empty/single in, same out.

## `groupBy` (D-MULTIPARADIGMA-PHASE1A slice 1h, all targets)

```kof
var xs = listOf(1, 2, 3, 4)
var g = xs.groupBy((n: Int) -> n % 2)   // {1=[1, 3], 0=[2, 4]} — Map<K,List<E>>
```
Keys use the boxed map equality (same taxonomy as `mapOf`); values are fresh lists in encounter order; missing keys read `null` (use `getOrDefault` or narrow with `if`).

## `listOf` with related subtypes infers the common ancestor (0.5.0-beta, §285)

```kof
interface Animal { String sound() }
class Dog implements Animal { String sound() { return "woof" } }
class Cat implements Animal { String sound() { return "meow" } }
var animals = listOf(new Dog(), new Cat())   // inferred List<Animal>, not List<Dog>
animals.get(1).sound()                       // "meow" — no ClassCastException
```

Elements that SHARE a supertype (class or interface) are homogeneous at the
ancestor level: the inference widens to the common supertype. Unrelated
elements (`listOf(new Dog(), 42)`) keep the SEM056 homogeneity rejection.
Before 0.4.0 the element type came from the FIRST argument only — the fix
walks superclasses AND interfaces (family of §156). Measured 18/09 on the
tip: `woof`/`meow`.

## `Map.get` returns `V?` for reference values (02/09)

`m.get(chave)` returns `V?` when the value is a reference type
(`Map<String, String>`, `Map<String, User>`): absence = `null`, use
`if (v != null)` to narrow. For **primitive** values (`Map<String, Int>`)
the type is now `V?` as well (since the D-NULL-INTENT N1 merge, #438 `250f6207`,
18/09: `Int z = m.get("a")` fails with a type-mismatch on `NullableType[int]` —
absence is representable). **Caution while §294 is open:** on the JVM a
present-key `if (v != null)` check over a primitive-valued map still dies at
runtime (`NoSuchMethodError Object.valueOf(boxed)`); until §294 closes, prefer
`m.getOrDefault(key, fallback)` (landed `62bd455e`, measured working) or
`contains`/`containsKey` checks for primitive-valued maps. Reference values are
unaffected.

Fix 27/08: `listOf(...).get(n)` and `size` in large projects with `import a.b.C` now resolve correctly (CompilerDriver file-specific imports). A manual index workaround is not necessary.

## Bare `List`/`Set`/`Map` in a declared position (0.4.0 — §373/#443)

A collection name WITHOUT type arguments in a field, parameter or return type is the
**builtin collection** — exactly what the local form has always meant (#139/#150/#214):

```kof
class Box {
    List items                      // bare = builtin List (before §373: crashed at class load)
    public constructor() { items = listOf(1, 2) }
}
count(xs: List): Int { return xs.size }     // bare in a parameter
```

Prefer the explicit element type (`List<Int> items`) when it is known — the compiler
checks more with it. A user class with the same name (`class List { ... }` in your own
package) keeps winning over the builtin (the §243 shadow guard, proven by controls in
`BareCollectionFieldE2ETest` 8/8, the same print on the 4 targets).

## When to use

Any problem that requires a sequence of elements:
collections, records, simple queues, groupings, accumulators.
`Map`/`Set` for associations and sets. `map`/`filter`/`reduce` for transformation without a manual loop.

## When not to use

- Do not reimplement `map`/`filter`/`reduce` with a loop when the higher-order expresses the intent.
- Do not use `List<record>` with a linear search when `Map<K,V>` solves it (when there is a key).

## BAD — manual structure

```kof
class Node {
    Node next
    Int value
}
class Registry {
    Node root
    Int count
}
```

## GOOD — the language's collection

```kof
class Registry {
    List<LanguageEntry> entries

    constructor() {
        entries = listOf(
            LanguageEntry("Kof", "kf", "kof"),
            LanguageEntry("JSON", "json", "json")
        )
    }
}
```

## GOOD — declarative transformation (0.5.0-beta)

```kof
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
```

## GOOD — Box<T> with primitives (0.1.0 fix)

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b = Box<Int>(42)
println(b.get())   // 42 — substituteTypeVariable fixes T → Int on Native
```

## WHY

`Node`/`next`/`count` is accidental implementation. The domain is "a sequence of entries".
Kof has the abstraction. Represent the domain, not the implementation.
Higher-orders and `Box<T>` eliminate manual loops and wrappers.

## Iteration

```kof
var items = listOf("a", "b", "c")
for (var item in items) {
    println(item)
}
```

`for-in` works over `List<T>` and arrays (`new Int[5]`).

## Element types

```kof
var ids = listOf<Int>()          // empty list of Int
var nomes = listOf("Ana", "Mel")
var users = listOf<User>()       // list of objects (erasure)
var boxed: Box<Int> = Box(5)
```

## Related anti-patterns

- Manual linked list → `training/anti-patterns/manual-data-structures.md`
- Array as a substitute for a dynamic collection → use `List<T>`
- Manual loop for map/filter → use higher-order
