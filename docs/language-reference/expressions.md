[English](expressions.md) | [Português](expressions.pt_BR.md)

# Expressions

**Status:** Stable (except where labeled) · **Evidence:** ExpressionParser, `ExpressionLowerer.java`, `TypeChecker.inferBinaryResultType`

An expression produces a value. The full precedence and associativity are
in [grammar.md](grammar.md) §5. Here is the **semantics** of each form.

---

## 1. Literals

`int`/`long`/`float`/`double`/`string`/`char`/`bool`/`null` — see
[types.md](types.md) §4. `null` has type "null" that adapts to the assignment
context.

---

## 2. Identifiers and member access

- `x` — local variable, parameter, field (via implicit `this`), or unqualified
  enum constant.
- `this` — receiver of the current method/constructor.
- `super` — superclass receiver (for `super.method()` / `super(...)`).
- `a.b` — field or method (`FieldAccessExpr`/`MethodCallExpr`). Type keywords
  are valid after `.` (`config.int`).
- `a[i]` — array access (`ArrayAccessExpr`). `.get()/.set()` on an array →
  `SEM028`.

---

## 3. Arithmetic operators: `+ - * / %`

- Applicable to numerics. Result = `commonNumericType` of the operands
  (double > float > long > int).
- **`/` between integers is truncating integer division**: `7 / 2` → `3` (*probe*).
- **`%` follows the sign of the dividend** (JVM `irem` semantics): `-7 % 3` → `-1`
  (*probe*).
- **Constant division by zero** (`7 / 0`) → `ARITH001` at compile-time.
  **Non-constant** (`7 / z`, `z=0`) → `ArithmeticException` at runtime
  (*probe*). **Target-specific** (Native: trap/SIGFPE; JS: `Infinity`).
- **`+` with `string`** is concatenation: `"x" + 1` → `"x1"`, `1 + "x"` → `"1x"`,
  `"x" + true` → `"xtrue"` (*probe*). Any string operand → concat.
- **`+ - * / %` on `bool`** → `SEM002`. On a non-numeric reference →
  `SEM001`.

---

## 4. Relational and equality operators: `== != < <= > >=`

- Result always `bool`.
- **`==` has per-type semantics** (decided in the lowering, not in the type checker):
  - `string`, `record` → **content**; `enum` → **identity** (singleton instances)
  - primitive → **value**
  - reference (others) → **identity**
  - an enum value is **not** a String: `Dir.N == "N"` → `SEM062` (D-ENUM207)
  - see [type-system.md](type-system.md) §10.
- `< <= > >=` only make sense on numerics/char. On **String it is rejected at
  compile time (SEM053)** — lexicographic order was **Unspecified** and
  diverged per target (JVM always-false, Native by pointer, Script
  lexicographic). The idiom for order is `a.compareTo(b) < 0` (lexicographic,
  absolute parity on the 5 backends). `==`/`!=` on String are **content**
  (above) and remain valid.

---

## 5. Logical operators: `&& || !`

- `&&`/`||` require `bool` (or integer primitive — `1 && 2` compiles and is
  **true**, *probe*: treated as non-zero). Result `bool`.
- **Short-circuit**: `a && b` does not evaluate `b` if `a` is false — on **all**
  targets, JS included (SG-006 ✅ FIXED 09/09; `BackendParityTest.parityShortCircuitAndOr`).
- `!` is logical negation. `!5` → `0` (*probe*: applied to an integer as XOR with
  -1 / JVM `lnot` which gives 0/1). **Unspecified** for non-bool.

---

## 6. Bitwise operators: `& | ^ << >> >>>`

- `& | ^` between integers → bitwise.
- `<<` left shift; `>>` arithmetic shift (sign); `>>>` logical shift (zero).
- **There is no `~`** (complement) — `~5` → `PARSE041` (*probe*). To negate
  bits, use `x ^ -1`. **Unspecified** (SG-002).

---

## 7. Assignment as an expression: `=` and compounds

- `x = v`, `x += v`, `x -= v`, `x *= v`, `x /= v`, `x %= v`, `x &= v`,
  `x |= v`, `x ^= v`, `x <<= v`, `x >>= v`, `x >>>= v`.
- **Right-associative**: `a = b = c` assigns `c` to `b` and `b` to `a`.
- **Assignment used as a value** (`var c = (a = b)`) → `SEM027` (*probe* —
  assignment is a statement, not an expression, except in the assignment form itself).
- Assigning to a never-declared variable → `SEM020`.

---

## 8. Increment/decrement: `++ --`

- Prefix (`++x`) and postfix (`x++`) are both valid (ExpressionParser.parseUnary (inc/dec),
  `1457-1466`).
- Applicable to local, field, array element.
- `i++` as a statement increments; as an expression it produces the old value
  (standard semantics). **Implementation-defined** for the return value in
  expression position (not tested by a dedicated probe).

---

## 9. `instanceof` and `as`

- `x instanceof T` → `bool`. The target `T` must be a class/record/String
  reference — `instanceof` over a primitive (`o instanceof Int`) is refused
  with `SEM106` (`§677`).
- `x as T` → converted/cast value. `as T?` is the always-null soft cast on
  **all four targets** (LANDED 13/10); the plain `as` THROWS on all four too (`§678` CLOSED). See
  [type-system.md](type-system.md) §4.

---

## 10. Calls

- `f(args)` — function/method. `f<T>(args)` — with explicit type-args.
- `f { … }` — trailing lambda (the block is the last argument).
- `f { x -> … }` / `f { x: Int -> … }` — trailing lambda with parameters.
- `obj.m(args)` — instance method. `Klass.m(args)` — static method.
- `new T(args)` — constructor. `new T[n]` — array.
- Wrong arity → `SEM013`; wrong argument type → `SEM014`.

---

## 11. `if` as an expression

`kof
var status = if (ativo) "online" else "offline"
`

- **`else` is mandatory** in the expression form: `if (c) x` without `else` in
  expression position → `PARSE044` (*probe*).
- The two branches must have a common type (otherwise `SEM012`/`SEM021`).
- There is no ternary operator `c ? a : b` — `?` is only a type suffix (*probe*).

---

## 12. `switch` as an expression

`kof
var desc = switch (o) {
    case String s -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}
`

- Each case uses `->` and produces **one expression** (the value).
- **`default` mandatory** (or exhaustive enum) → otherwise `SEM032`.
- **No fallthrough** — each branch is a value.
- Supports **pattern matching**: `case Type var` (binding) and `case Type(a, b)`
  (record destructuring). See [classes.md](classes.md) §6.

---

## 13. Lambdas (expression)

`kof
(x: Int) -> x * 2
(a: Int, b: Int) -> { return a + b }
() -> println("oi")
{ println("bloco") }
`

- Parameters **require a type annotation** for arithmetic use: `(x) -> x + 1` →
  `SEM001` with the hint "declare the parameter type" (*probe*).
- Body: single expression (implicit return) or block `{ … }`.
- See [closures.md](closures.md).

---

## 14. `spawn` and `await` in expression position

- `spawn f()` in an expression → `Handle<T>` (lowered as `__kof_spawn_expr`).
- `await h` → value `T` (lowered as `__kof_await`).
- `awaitTimeout(h, ms)` → value `T` or throws an exception (function, **not** the
  syntax `await h withTimeout` — that gives `PARSE043`, *probe*).
- See [../concurrency.md](concurrency.md) (stdlib document).

---

## 15. Query DSL (expression)

`kof
User.query(db) { where age > 18; orderBy name desc; limit 10 }
`

- Special syntax recognized when the receiver is a declared `entity` and the
  method is `query` with 1 argument (ExpressionParser.parsePostfix (call)).
- Lowers to `db.query<Entity>(…)` with SQL assembled at compile-time and values
  as binds (no input concatenation). Available on JVM + JS (`DB002`/`ORM001`
  closed 18/09); `ORM001` only on Native cross riscv64/aarch64 (x86-64 real since 22/09).
- **Experimental** (ORM domain).

---

## 16. Operations that are NOT expressions

- `throw` is a statement (it does not produce a value).
- `return`, `break`, `continue` are statements.
- Assignment is not a value-expression (SEM027).
- There is no `??`, `..`, `in`, `=>`, `~` (SG-002). The elvis `a ?: b` exists since §821 (grammar §5.1).
