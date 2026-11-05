[English](closures.md) | [Português](closures.pt_BR.md)

# Closures and Lambdas

**Status:** Stable (except where labeled) · **Evidence:** LambdaParser, `CompilerDriver.java` (`lambdaClass`/`collectCaptures`), `BoxClassFactory.java`

---

## 1. Lambda forms

`ebnf
lambda = "(" , [ lambda-param , { "," , lambda-param } ] , ")" , "->" , lambda-body
       | "{" , lambda-body , "}" ;
lambda-param = identifier , [ ":" , type-ref ] ;
lambda-body  = block | expression ;
`

Valid examples:

`kof
(x: Int) -> x * 2                  // typed params, expression-body
(a: Int, b: Int) -> { return a + b }  // block-body
() -> println("oi")                // no params
{ println("bloco") }               // block-lambda (0 params)
(x: Int) -> (y: Int) -> x + y      // currying (lambda returning lambda)
`

- **Expression-body** has an implicit return (`parseLambdaBody:1660-1667` becomes
  `ReturnStmt`).
- **Block-body** requires an explicit `return` to produce a value.

---

## 2. Parameters and types

- Parameters **may** be annotated (`x: Int`).
- **Without annotation**, the parameter assumes `Object` (`parseLambdaParameter:1652`).
  Using it in arithmetic → `SEM001` with the hint *"declare the parameter type,
  e.g. `(x: Int) -> …`"* (*probe*: `l.map((x) -> x + 1)` → SEM001).
- **Lambda parameter type inference WORKS** from the call context (SG-012,
  since 09/09): in `List` `map`/`filter`/`reduce`, a param without annotation
  inherits the **element type** (`nums.map((x) -> x * 2)` compiles). Without
  context, `Object` remains and arithmetic is `SEM001` (never a silent Object).

---

## 3. Function types

A lambda has type `FunctionType(parameterTypes, returnType, className)`
(`Type.java:29`). Function types are **first-class values**:

`kof
var f: (Int) -> Int = (x: Int) -> x * 2
println(f(5))                       // function-value call
var g = listOf(1,2).map((x: Int) -> x + 1)   // passed as argument
`

- Type syntax: `(Int, String) -> Bool` (`parseFunctionTypeRef`).
- Calling a variable with `FunctionType` → `ft.returnType()` + arg checking
  (`SemExpressionTyper`, `FunctionType` case).
- Calling a variable **without** FunctionType → `SEM015`.
- A **field** of function type is called like a method (§820):
  `h.run()` invokes the value of `() -> Int run` when the class has no
  method named `run` in its hierarchy; a method with the same name wins.
  The argument count is checked (`SEM013`). Each field declaration ends at
  its line, so `() -> Int run` followed by `(Int) -> String show` on the
  next line declares two fields.
- **There is no** named `fun`-type, nor function type alias.

---

## 4. Variable capture (closure)

A lambda captures variables from the outer scope.

### 4.1 Read-only capture (snapshot)

`kof
main() {
    var n = 10
    var f = () -> n + 1
    println(f())        // 11 (probe)
}
`

- Captures are **`private final` fields** of the synthetic class `Lambda<N>`
  (`lambdaClass`, `CompilerDriver.java`), copied at the call site.
- The capture is a **snapshot of the value at the moment the lambda is created**
  (comment `:897-898`).
- `collectCaptures` (`:1072-1209`) scans the body; params and inner declarations
  go into `shadowed` and do **not** capture the homonymous outer one.

### 4.2 Mutable capture (Box)

`kof
main() {
    var n = 0
    var inc = () -> { n = n + 1 }
    inc(); inc()
    println(n)          // 2 (probe)
}
`

- If a captured variable is **assigned inside the lambda**, the lowering
  converts it into a **mutable box**: synthetic class `Box<N>` with field `value`
  (`BoxClassFactory.createBoxClass`). Reads/writes become
  `KofLoadField/KofStoreField "value"`.
- **Observable consequence**: the mutation via box is **visible outside the
  lambda** (the outer `n` changes). This is **Stable** (documented and tested
  behavior).
- Capturing `this` (`() -> this.v`) works (*probe*).

---

## 5. Implementation representation (non-normative)

To explain the observable behavior: each lambda becomes a **synthetic class**
`Lambda<N>` (or `LambdaTask<N>` for a `spawn` body) that implements a
**synthetic function interface** per signature
(`kof/Function<N>_<mangled>`). The call site does `new Lambda<N>(captures)` +
`invoke(args)`. **Implementation-defined** — another Kof compiler may use
closures differently, as long as it preserves the capture semantics (§4).

---

## 6. Trailing lambda

`ebnf
call-args , trailing-lambda = "(" , [ args ] , ")" , block
`

`kof
transaction { println("dentro") }
list.forEach((x: Int) -> println(x))
map.map { s: String -> s.length }        // trailing with typed params
`

- `f { … }`: the block is the **last argument** (ExpressionParser.parsePostfix (trailing lambda)).
- `f { x: Int -> … }`: trailing lambda with parameters (`looksLikeLambdaBlockParams`,
  lookahead heuristic ≤8 tokens — **Implementation-defined**).
- `f { … }` without `->` is a **0-param lambda** whose body is the block.

---

## 7. Limits

- **A lambda cannot declare a named function** inside its body.
- **A lambda is not generic** (no `<T>` of its own).
- **A lambda cannot have a `return` of a type incompatible** with the body (SEM010).
- **There is no** implicit SAM-conversion from lambda to a user interface in a
  guaranteed way: `FunctionType → ClassType` always passes in `isAssignable`
  (SAM case of `TypeChecker.isAssignable`), but the real compatibility is
  validated at emission. **Unspecified.**
- **There is no** `it`/`$0` as an implicit parameter (Kotlin-style). Use
  `(x: T) -> …`.
