[English](records.md) | [Português](records.pt_BR.md)

# Idioms — Records

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:**  0.5.0-beta (Sep 2026)

## What it is

Record is the way to declare **immutable data with zero ceremony**.

```kof
record Point(Int x, Int y)
```

The compiler generates: canonical constructor, accessors (`p.x()`), and on the JVM also
`toString`/`equals`/`hashCode`.

> **`class Point(Int x, Int y)` is identical** — the parser treats `class X(...)`
> as a record body (immutable). Use `record` (the canonical form) to make the
> intent explicit; `class X(...)` is backward-compatible but does not bring a
> mutable class (verified 02/09). For mutable state, see `classes.md`.

## `value record` — value semantics, no identity (TIER 2.7, 14/10)

```kof
value record Vec2(Float x, Float y)
```

A `value record` has the **same immutable data-model and the same observable
behavior** as an ordinary `record` — fields, structural `==`, `toString` — but
declares **no object identity** (D-VALUE-RECORD; `D-VALUE-RECORDS-GO`: `value`
applies only to `record`, collections hold them boxed, identity-required use is
a compile-time error). Additive: an ordinary `record` keeps its semantics.

Per target: JVM = the common record class; Native = the current heap
representation (pass-by-value deferred); JS = the object is **frozen**
(`Object.freeze`). Use `value record` for small data-oriented types
(vectors, colors, ranges, tokens) to state the intent; use plain `record`
when the distinction does not matter.

```kof
// `value` is a CONTEXTUAL keyword: only before `record`. Anywhere else it
// stays an ordinary identifier — this still works:
var value = 42
record Amount(Int value, String unit)
```

## When to use

- Immutable data (DTO, value, key, result).
- Grouping values without behavior.
- When in Java you would write `class` + constructor + getters + equals + hashCode.

## When not to use

- Mutable state → class.
- Behavior over the state → class.

## BAD — class with ceremony for data

```kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    public getName(): String {
        return name
    }
    public getAge(): Int {
        return age
    }
}
```

## GOOD — record

```kof
record User(String name, Int age)
```

Usage:

```kof
var u = User("Mel", 30)
println(u.name())
println(u)
```

On the JVM, `println(u)` prints `User[name=Mel, age=30]` (generated toString).

## WHY

Record eliminates the ceremony that Java requires for data. If the problem is
"represent a data object", record is the default answer.

## Creation

```kof
var a = Point(10, 20)      // canonical constructor
var b = new Point(3, 4)    // also accepted
```

> **Record is immutable.** Writing to a component (`p.x = 9`) or to `this.x` inside
> a record is a compilation error **SEM038** ("record is immutable"). For
> mutable state use `class` with fields + `constructor(...)`.

## Pattern matching — record destructuring (0.5.0-beta)

```kof
record Point(Int x, Int y)
record User(String name, Int age)

main() {
    var p = Point(10, 20)
    switch (p) {
        case Point(var x, var y):
            println(x + "," + y)   // 10,20
            break
        default:
            println("outro")
    }

    // with types
    var obj: Object = User("Mel", 30)
    switch (obj) {
        case String s:
            println(s)
            break
        case User(var n, var a):
            println(n + " " + a)
            break
        default:
            println("unknown")
    }

    // instanceof + destructuring
    if (p instanceof Point) {
        var q = p as Point
        println(q.x() + "," + q.y())
    }
}
```

The compiler generates an `if-chain` with `getfield` on the 3 targets (JVM `INVOKEVIRTUAL`, Native `rcx/r15`, JS `typeof/instanceof`).

## Access: record `p.x()` vs class `u.name` (02/09 — documented)

A record exposes its components through **accessors** (`p.x()`), a class through
a **direct field** (`u.name`). It is the contract difference between immutable
data (record — read via method) and mutable state (class — field). It is not an
accident: the record is a value; the class is state.

```kof
record Point(Int x, Int y)
var p = Point(10, 20)
p.x()                          // accessor (method)

class User {
    String name
    public constructor(String name) { this.name = name }
}
var u = User("Mel")
u.name                         // direct field
```

## JSON

Records are supported by `json.encode`/`json.decode<T>` on the JVM and JS:

```kof
var p = Point(3, 4)
var j = json.encode(p)                 // {"x":3,"y":4}
var d = json.decode<Point>("{\"x\": 10, \"y\": 20}")
```

**Native:** JSN002/JSN001/JSN003 closed 31/08 — `json.encode`/`json.decode<T>` of
objects/records/arrays also works on Native (compile-time composition; FP in XMM).

## Null safety with records (0.5.0-beta)

```kof
record Point(Int x, Int y)
var m: Map<String, Point> = mapOf("k", Point(7, 8))
var maybe: Point? = m.get("k")    // null reaches T? via API (= null literal is SEM048)
if (maybe != null) {
    println(maybe.x())
}
```

> ✅ The narrowing above is safe even for a **missing** key (`m.get("z")`,
> so `maybe` is really `null`): `== null`/`!= null` on a record is a reference
> comparison (`if_acmp`), never a `.equals()` call — fixed 17/09 (`07a51565`,
> bug `§262` face (a)).
>
> ✅ `==` between **two** nullable records is also safe (fixed 17/09,
> `§262` face (b)): `miss == hit` where one is `null` yields the
> `Objects.equals` result (`false` if only one is `null`, `true` if both are,
> content equality otherwise) on all four targets — no NPE, no narrowing
> needed. (`RecordNullableNullEqE2ETest` locks JVM=JS=SCRIPT=Native.)

## Related anti-patterns

- `java-like-code.md` — record + getters
- `unnecessary-abstraction.md` — wrapper around a record for no reason
- `fake-idioms.md` — check the status of pattern matching
