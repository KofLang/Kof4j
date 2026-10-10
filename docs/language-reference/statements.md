[English](statements.md) | [Português](statements.pt_BR.md)

# Statements

**Status:** Stable (except where labeled) · **Evidence:** StatementParser, `StatementLowerer.java`

A statement executes an effect and **does not produce a value**. Semicolons are
**optional** in every statement-end position (see
[lexical-structure.md](lexical-structure.md) §6).

---

## 1. Block

`ebnf
block = "{" , { statement } , "}"
`

Introduces a new scope (`StatementLowerer`/`SemanticAnalyzer` do
`enterScope`). Declarations inside the block do not leak out.

---

## 2. Variable declaration: `var` / `val` / explicit type

`ebnf
var-decl = ( "var" | "val" | type-ref ) , identifier , [ ":" , type-ref ] , [ "=" , expression ]
`

Four valid forms:

`kof
var x = 10              // inferred, mutable
val y = 20              // inferred, "immutable" (see below)
String nome = "Mel"     // explicit type (type-first)
var idade: Int? = findAge()  // annotated type (annotated)
String? nome2 = find(key)    // type-first nullable (null reaches T? via API;
                             // `= null` literal is SEM048 since 10/09)
`

- **`var` without initializer** → type `UnknownType` (`:625`).
- **Explicit type ≠ initializer type** → `SEM021`.
- **Redeclaring in the same scope** → `SEM024`.
- **`val` prevents reassignment** (SG-010 ✅ FIXED 09/09): `val x = 1; x = 2`
  (incl. compound `+=`) → `SEM037` ("cannot assign to immutable 'val'"). `val`
  in a class field → `PARSE016` (not accepted as a field modifier; use `final`).

---

## 3. `return`

`ebnf
return-stmt = "return" , [ expression ]
`

- `return;` / `return` (bare) in a `void` function → ok.
- `return` with a value incompatible with the declared return → `SEM010`.
- **Empty `return` in a non-void function** → emits the **type's default value**
  (`0`, `false`, `null`, `'\0'`): `Int f() { return }` returns `0` (*probe*).
  **Implementation-defined** (the language does not require this to compile).
- A non-void function without `return` at the end → **Unspecified** behavior (the
  lowering injects `defaultValueOp`).

---

## 4. `if` / `else` (statement)

`ebnf
if-stmt = "if" , "(" , expression , ")" , statement , [ "else" , statement ]
`

- The condition must be `bool` (or an integer primitive — treated as non-zero).
- The `else` is **optional** in the statement form (only the expression form requires it).
- **Nullability narrowing**: `if (x != null) { … }` narrows `x` to `T`
  **only in the then-branch** (`StatementAnalyzer`, `IfStmt` narrowing). See
  [type-system.md](type-system.md) §5.
- Each branch is a statement (a block or a single statement): `if (true) println("y")`
  works without braces (*probe*).

---

## 5. Loops

### 5.1 `while`

`ebnf
while-stmt = "while" , "(" , expression , ")" , statement
`

Condition evaluated **before** each iteration.

### 5.2 `do … while`

`ebnf
do-while = "do" , statement , "while" , "(" , expression , ")"
`

Body executes **at least once**.

### 5.3 Classic `for`

`ebnf
for-stmt = "for" , "(" , [ init ] , ";" , [ cond ] , ";" , [ update ] , ")" , statement
init     = var-decl | expr-stmt
`

`for (var i = 0; i < 3; i++) { … }` (*probe*: prints 0,1,2). The three parts
are optional.

### 5.4 `for-in`

`ebnf
for-in = "for" , "(" , ( "var" | "val" ) , identifier , "in" , expression , ")" , statement
`

- Iterates over `List<T>` (internal index `#coll`/`#idx`) or an array
  (`StatementLowerer.java:259-310`). **No custom iterator.**
- **`in` is a contextual word** (not a keyword) — only valid here.
- The variable type is `typeArguments.get(0)` of the List or the array component.
- **`for (var c in "ab")` does NOT iterate over a string** — the `string`
  receiver is not a collection. Since 14/09 it is **rejected at compile time
  with `SEM058`** on all 5 targets (previously it was silently accepted and
  broke differently per target — JVM `VerifyError` `arraylength` on String,
  Native SIGSEGV, Script "Argument is not an array", JS iterated chars). Use
  `s.charAt(i)` in a numeric loop. The same applies to `Map`/`Set`/primitives/
  record/class receivers (only `List<T>` and arrays are iterable).

### 5.5 `break` / `continue`

- End/skip the iteration of the **innermost loop** (or `switch`).
- **There is no labeled break/continue** (`L: for … break L` → `PARSE041`, *probe*).
- Implemented by label stacks (`breakLabels`/`continueLabels`).

---

## 6. `switch` (statement)

`ebnf
switch-stmt = "switch" , "(" , expression , ")" , "{" , { case-stmt } , [ default-stmt ] , "}"
case-stmt   = "case" , ( pattern | expression ) , ":" , { statement }
`

- Cases use `:` (the expression form uses `->` — see
  [expressions.md](expressions.md) §12).
- **No fallthrough**: each case ends with a jump to the end of the switch
  (`SwitchStmtLowerer.java:174`). *probe*: value 1 with `case 1: println("a")
  case 2: println("b")` prints only `a`.
- `break` inside the case is accepted (and redundant).
- Supports **pattern matching** (`case String s:`) and **destructuring**
  (`case Point(var x, var y):`).
- **Enum**: a switch over an enum without `default` requires coverage of all
  constants → otherwise `SEM031`.
- **Sealed / Bool**: the *statement* form is exhaustive too — a `sealed`
  subject missing a direct subtype case → `SEM081`; a `Bool` subject not
  covering `true` and `false` → `SEM032` (same contract as the expression
  form; `default:` of any body count, §686).

---

## 7. `throw`

`ebnf
throw-stmt = "throw" , expression
`

- **The expression must be `string`** — exceptions in Kof are Strings.
  `throw 5` → `SEM026` (*probe*: "throw requires a String").
- On the **JVM**, `throw "msg"` is lowered to `new RuntimeException(msg)` +
  `athrow` (`StatementLowerer.java:311-327`). On the other targets it is
  `KofThrow()` directly (the string is the thrown value). **Target-specific** in
  representation, **Stable** in semantics (throws an exception catchable by
  `catch (String e)`).

---

## 8. `try` / `catch` / `finally`

`ebnf
try-stmt = "try" , block , { catch-clause } , [ "finally" , block ]
catch-clause = "catch" , "(" , type-ref , identifier , ")" , block
`

- `catch (String e)` catches Kof exceptions (strings). The catch type is
  resolved as `String` on the JVM (because `throw` became `RuntimeException`).
- `catch (Int e)` **compiles** (*probe*) but the catching behavior is
  **Unspecified** (the thrown exception is always String/RuntimeException).
- `finally` always executes (including on `return`/`throw` of the try).
- Implemented by region markers in the IR (`KofTryStart`/`KofCatchStart`),
  not by a separate exception table.

---

## 9. `assert`

`ebnf
assert-stmt = "assert" , "(" , expression , [ "," , string-literal ] , ")"
`

- If the condition is false: throws `"assertion failed"` (or the given message).
- The message must be a **string literal** (not an expression) — `:901`.
- Used by the `kof test` harness (exit code ≠ 0).

---

## 10. `spawn` (statement)

`ebnf
spawn-stmt = "spawn" , expression
`

- Executes the expression (call or block) as a **concurrent task**.
- Fire-and-forget: the program **awaits the spawned tasks before exiting**
  (implicit join in `main`).
- `spawn { … }` (block) and `spawn f()` (call) are valid.
- See [../concurrency.md](concurrency.md).

---

## 11. Expression statement

`ebnf
expr-stmt = expression
`

Any expression used for effect (call, assignment, increment).
`println(x)` is an expression statement (a function call).

---

## 12. Empty statement

An isolated `;` → `ExpressionStmt(null)` (no-op).
