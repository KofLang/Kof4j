[English](type-system.md) | [Português](type-system.pt_BR.md)

# Type System and Type Checking

**Status:** Stable (rules) · **Evidence:** `SemanticAnalyzer.java`, `Type.java`, `CompilerTypes.java`, `TypeMetrics.java`, execution probes

> This is the most important document in the reference. It avoids the vague term
> "strong typing" and describes **concrete behavior**: what is accepted, what
> is rejected, when there is inference, what is guaranteed and what is **not**.

---

## 1. System classification

Kof is **statically typed** (types resolved at compile-time), with
**local inference** (from `var`/`val` and `void` return), **nominal** for
classes (subtyping by name/inheritance, not structural) and **with erasure** for
generics (type-args erased at emit, like Java).

The term "strong typing" is **not** used here as praise. The concrete
properties — and the **guarantee failures** — are in the following sections. Where the
type checker does **not** prevent an operation, that is stated explicitly.

> **Update 09/09 (maintainer decisions on gaps B):** the compilation guarantees
> became strict at the points that were missing — instantiation of
> `abstract` (SEM041), nested type (SEM042), `implements` coverage
> (SEM043), `main` signature (SEM044), `throw` clause (SEM045) and
> `private`/`protected` visibility (SEM046) are compile-time errors.
> Lambda in a collection inherits the element type without annotation (SG-012); a nested
> function works with hoisting (SG-011); guards in pattern matching
> (`case T v if cond`) are supported (SG-014). See the error table in §13.

---

## 2. Where type checking happens (real pipeline)

`text
Source ─▶ Lexer ─▶ Tokens ─▶ Parser ─▶ AST(raw)
       ─▶ Desugar (test/application) ─▶ AST(desugared)
       ─▶ SemanticAnalyzer.analyze ─▶ AST + side maps (resolved types)
       ─▶ [aborts if there is an error] ─▶ Lowering AST→IR ─▶ Optimizer ─▶ Backend
`

`CompilerDriver.java`, method `lowerAndEmit`. **Type checking and name resolution are NOT
separate phases**: they happen interleaved inside `inferType`
(`SemanticAnalyzer`/`SemExpressionTyper.inferType`), which resolves the name and checks the type at the same
point, emitting a diagnostic inline.

### 2.1 The analyzer's 4 phases (`SemanticAnalyzer.analyze`)

| Phase | Method | What it does |
|---|---|---|
| 1 | `preDeclareType` | Creates an empty `ClassSymbol` per type; registers `knownClasses`; synthesizes `values()/valueOf()/name()` in enums |
| 2 | `defineMembers` | Fills fields/methods/constructors; record accessors; type-params |
| 3 | `analyzeDeclaration` | Analyzes bodies; **fixpoint ≤4 passes per class** (void→T return inference) |
| 4 | `resolveMethodCalls` | **Effectively a no-op** — the real resolution already happened eagerly in phase 3 (`SemanticAnalyzer.resolveMethodCalls`) |

> **Post-REFACTOR-500 F6 note:** phases 1–2 were extracted into
> `SymbolTableBuilder`; type checking (`isAssignable`, `primitiveWidth`,
> `checkArgTypes`, `inferBinaryResultType`) into `TypeChecker`; statement
> analysis (`IfStmt`/narrowing) into `StatementAnalyzer`; expression
> inference into `SemExpressionTyper`. `SemanticAnalyzer` orchestrates. The
> references below use **method**, not line (the refactor is in progress).

### 2.2 There is no "typed AST"

The AST nodes **do not carry a resolved type** — declaration types are
`String` in the AST. Resolved types live in **side maps by node
identity** (`IdentityHashMap`): `expressionTypes`, `resolvedMethods`,
`resolvedConstructors` (fields of `SemanticAnalyzer`). The lowering **re-infers**
everything via `ExpressionTyper`/`MethodCallTyper` (the analyzer's cache is cleared each
pass/class — `MethodCallTyper.java:27-34`). **Implementation-defined.**

### 2.3 When errors are reported

During analysis, immediately (`diagnostics.error`), and the driver **aborts
before lowering** if there is an error (`CompilerDriver.java`, guard `diagnostics.hasErrors()` post-`analyze`). Exception:
some `SEM0xx` codes are **deferred** to lowering/emit (SEM016/017/029/
030/031/033/034, ARITH001) — they only fire if the analysis passed.

---

## 3. Assignment and compatibility (`TypeChecker.isAssignable`)

An assignment `dest = src` (and arguments, returns) is accepted when:

| Rule | Accepted? | Evidence |
|---|---|---|
| `T → T` (equal) | ✅ | `from.equals(to)` |
| `T → T?` (makes nullable) | ✅ | `NullableType` case in `to` |
| `T? → T?` (recurses on the inner) | ✅ | `inner()` recursion |
| **`T? → T`** (unwrap nullable) | ❌ `SEM021` | only after narrowing (§5) |
| numeric widening (`primitiveWidth(from) ≤ primitiveWidth(to)`) | ✅ | `TypeChecker.primitiveWidth` |
| `double → float` | ✅ (explicit exception, D2F) | `double→float` case |
| numeric narrowing (`long→int`, `double→int`, `int→byte`) | ❌ `SEM021` | *probe*: `Long x; Int y = x` → SEM021 |
| `primitive → Object` (auto-box) | ✅ | primitive→`java.lang.Object` case |
| `FunctionType → ClassType` (SAM) | ✅ (always; real compatibility deferred to emit) | SAM case |
| `TypeVariable` in any position | ✅ | `TypeVariable` case |
| **`ClassType → ClassType` (any pair)** | ✅ **ALWAYS** | final case `to instanceof ClassType` — ⚠️ see §7 |

### 3.1 `TypeChecker.primitiveWidth`

`bool=0, char=1, {int,byte,short}=2, long=3, float=4, double=5`.

Observable consequence: `bool → int` **passes the check** (width 0≤2) and
**produces `1`/`0`** at emit — because `bool` is stored as `int` 1/0 in Kof
(`var i: Int = true; println(i)` → `1`, *probe*). It is not a "void": it is functional
coercion by representation. Assignments `bool → long/float/double` follow the
same widening path. **Implementation-defined** (the 1/0 representation is an implementation
detail that leaked into observable semantics).

### 3.2 Coercions in arithmetic (`commonNumericType`, TypeMetrics.java:57-70)

For `+ - * / %` with two numerics: `double` dominates, else `float`, else
`long`, else `int`. `7 / 2` → `int` = `3` (integer division) (*probe*).
`1 + 1.5` → `double` = `2.5`.

---

## 4. Conversions: `as` and `instanceof`

- **`x as T`** is an **explicit cast**, never implicit. The result has type `T`
  (`TypeChecker.inferBinaryResultType`, case `as`).
  - primitive→primitive: widening + narrowing (`I2C`, `L2I`, `F2I`, `D2I`, …)
    — `Long x; x as Int` works (*probe*).
  - reference: JVM `checkcast` (may throw `ClassCastException` at runtime).
  - **`5 as String` does NOT parse a number**: it emits `checkcast String` over
    a boxed `Integer` → fails at runtime (*probe*). String↔number is only via
    `toInt()/toLong()/toDouble()/toFloat()` (methods of `string`).
- **`x instanceof T`** → `bool` (`SemExpressionTyper`, case `instanceof`). `o instanceof String` (*probe* ✅).

---

## 5. Nullability

- Representation: wrapper `NullableType(inner)`. `T?` = "T or null".
- **`NullableType` is semantic, not merely a compile-time constraint** — the
  storage is the target's inner representation, and absence is a real value
  distinct from every present value: `Absent != Present(0)`,
  `Absent != Present(false)`, `Absent != Present(0.0)`. Per target:
  `JVM` wrapper reference | `null`; `Script` host value | `null`; `JS` dynamic
  value | `null`; `Native` `RuntimeErasureBox*` | pointer `0`. The `T → T?`
  boundary is the shared `kof_box` call (`emitErasureBox`), never
  `Wrapper.valueOf` directly — see
  [RUNTIME_ABI.md §3.9](../runtime/RUNTIME_ABI.md).
- **Narrowing**: the **only** recognized form is `if (x != null)` (or `null !=
  x`) with `x` an identifier of type `T?` → in the **then-branch**, `x` now has
  type `T` (`StatementAnalyzer`, `IfStmt` narrowing). There is **no** narrowing by `&&`,
  `||`, ternary, or `if (x == null)` in the else.
- **Deref of `T?` without narrowing IS an error** (SG-005 fixed 10/09,
  `9436da12`): `var s: String? = "x"; s.length` → `error: receiver is nullable
  (T?); narrow first` [**SEM049**] (*measured 16/09, jar of tip `803eeef4*`).
  The old "advisory, not guaranteed" behavior is gone — narrowing (`if (x != null)`)
  is mandatory.
- **Comparison with null**: primitive `== null` → **constant** (`false`/`true`,
  `ExpressionLowerer.java:256-268`); reference `== null` → `if_acmp` (class/
  String narrow correctly — *measured 16/09*). **Record** used to be the
  exception (`==`/`!=` lowered to `.equals()` with no null-guard → null
  `Point?` **NPEd**); fixed 17/09 — `§262` (face (a) `07a51565` literal
  `null` → reference compare; face (b) both-null-safe content equality via
  the `Objects.equals` desugar / JS `kofRecordEq`).
- **`Nullable(primitivo)` compares by value, lifted** (*measured 19/09*,
  `NativeNullablePrimitiveContractE2ETest`, all 6 targets): a null `Int?`
  tests `== null` as **true** and prints `null`; `Int? == Int?` is value
  equality, never wrapper identity (two `10000`s from distinct calls →
  `true`); `Float?`/`Double?` follow the JVM wrapper contract, so
  `NaN == NaN` → `true` and `+0.0 == -0.0` → `false`. The old silent fold —
  a null `Int?` comparing `== null` as `false`, with `null`→`0` at the
  boundary — was the **D-NULL-INTENT / #259** bug, and is gone: the storage
  is no longer the raw inner. `String? == null` → `true`.
- **Sources of `T?`**: `Map.get(k)` for a reference value, `readLine()`,
  `readFile()`, a function declared `T?` that `return null`s. There is **no
  `T? = null` literal** — the null-literal is rejected since 10/09 (SG-008 →
  **SEM048**, *measured*: `null cannot be assigned [SEM048]`).

---

## 6. Name resolution and scope

`SymbolTable` is a chain of scopes with `parent` (`SymbolTable.java:9-77`);
`resolve(name)` searches from the innermost to the outermost. Resolution order
of an identifier (`SemExpressionTyper`, case `IdentifierExpr`):

1. local scope in the chain (locals → params → class fields → root)
2. `args` in `main` → `String[]`
3. unqualified enum constant (`Red` when `enum Color{Red}`)
4. member of the current class via `resolveInHierarchy` (BFS: class→super→interfaces)
5. otherwise, if it is not a builtin namespace (`json`, `process`, `KofWeb`, …) nor a
   builtin type → **`SEM011`** (undefined)

**Shadowing**: allowed per scope (innermost-first). Redeclaring in the **same**
scope → `SEM024`. In lambdas, params and inner declarations go into
`shadowed` and do **not** capture the homonymous outer one.

**Imports**: `qualifyViaImports` only resolves a **simple name** (without `.`/`<`/`[]`)
by the **first** non-wildcard import ending in `.<name>`
(`MemberResolver.qualifyViaImports`). **Wildcards `import a.b.*` are not used to
qualify names** (`MemberResolver.qualifyViaImports`). Type-arguments are qualified recursively by
`qualifyDeep` (bug 32, `CompilerTypes.java:48-94`): simple name via imports →
module classes; **ambiguous import → does not guess** (type preserved).

---

## 7. Subtyping (SG-009 — ✅ FIXED 10/09)

`isAssignable` performs **nominal subtyping** for reference→reference of domain
classes: it walks `superClass`/`interfaces` via BFS. An **unrelated** class is a
**compile error** (`SEM021`, *probe*: `class A`/`class B` with `A a = B()`).
Same for `implements` coverage (`SEM043`), abstract instantiation (`SEM041`) and
collection element type (`SEM056`) — all enforced at compile time:

- `B extends A; A a = b` — valid (real subtype).
- `A a = b_from_another_class` (unrelated) → `SEM021` at compile time.
- `class C implements I {}` with an abstract `f()` → `SEM043`; an `abstract
  class` may defer, the obligation is transitive to the concrete subclass
  (`#322`). `default` methods count as satisfied.
- `abstract class A; new A()`/`A()` → `SEM041` at compile time.
- `l.add("x")` on a `List<Int>` → `SEM056`.

**`sealed` (X5.1/X5.2 — `D-X5-SURFACE`, 21/09):** `sealed class`/`record`/`interface`
closes its subtype set at compile time — the set is the declarations of the
**same compilation unit** (file). A direct subtype (`extends`/`implements`)
declared elsewhere is **`SEM080`** (the compiler cannot know it), never a silent
open set. A `switch` **expression** over a sealed subject is **exhaustive without
`default`** when it covers every direct subtype (`case Subtype v ->`); a missing
subtype is **`SEM081`**. `sealed` is a **compile-time-only** modifier (erased in
codegen: identical bytes on JVM/Native/JS) and a **contextual** keyword — `sealed`
stays a valid identifier outside a type declaration.

**Declaration-site variance `out`/`in` (X5.3 — `D-TYPE-VARIANCE`, 21/09):**
a generic type parameter may carry a variance prefix — `class Source<out T>` is
**covariant**, `class Sink<in T>` is **contravariant**, and no prefix means
**invariant** (the default, unchanged from before). The variance governs the
compatibility of the **type arguments of the same raw type**:

```kof
record Source<out T>(T value)          // read-only component = out position
Source<Animal> up(Source<Dog> d) { return d }   // OK: Dog <: Animal (covariant)

class Sink<in T> { String consume(T v) { return "x" } }   // input position
Sink<Dog> down(Sink<Animal> w) { return w }     // OK: Animal >: Dog (contravariant)

class Box<T> { T value ... }           // invariant
Box<Animal> f(Box<Dog> d) { return d } // SEM021: rejected (§270)
```

`out`/`in` are **contextual** keywords (still valid identifiers). Erased in
codegen: descriptors and execution bytes are identical on JVM/Native/JS (the
variance lives only in the typer). **Soundness guard (`SEM082`):** `out T` is
forbidden in an input position (method/constructor parameter, writable class
field) and `in T` is forbidden in an output position (return type, any field or
record component), because a writable `out` / readable `in` would allow the
covariant/contravariant alias to store or expose a value of the wrong type.
Record components and interface fields are read-only, so `out T` is allowed
there. **Heritage guard (`SEM083`, X5.3b):** a type parameter declared `out`/
`in` may not be passed to a supertype parameter whose variance is
**incompatible** — `class Bad<out T> extends Sink<T>` is rejected when `Sink`
declares `in T` (the supertype would reintroduce `T` in an input position), and
`class Bad<in T> extends Source<T>` is rejected when `Source` declares `out T`.
Passing a variance to an **invariant** supertype parameter is also rejected
(invariant requires both read and write). The matching case
(`class Good<out T> extends Source<T>` with `Source<out T>`) is allowed.

**Use-site projection `List<out T>` / `List<in T>` (X5.4 — `D-X5-SURFACE`,
21/09):** even a type declared **invariant** accepts a projection at the use
site, exactly like Java wildcards but with Kof's `out`/`in` spelling:

```kof
List<out Animal> up(List<Dog> xs) { return xs }   // OK: covariant use
List<in Dog> down(List<Animal> xs) { return xs }  // OK: contravariant use
List<Animal> same(List<Dog> xs) { return xs }     // SEM021: invariant, rejected
```

`List<out T>` accepts any `List<S>` with `S <: T`; `List<in T>` accepts any
`List<S>` with `S >: T`. This is the same compile-time-only information as
declaration-site variance — codegen erases it (the projection becomes the
existing `WildcardType`, which all four targets already erase), so descriptors
and execution bytes are unchanged. `out`/`in` in a type-argument remain
contextual (still valid identifiers).

**Guarantee of the type checker:** a function/method **that does not exist on a
known type** is an error (`SEM015`/`SEM025`); argument/constructor arity is
checked (`SEM013`/`SEM023`); an incompatible return type is an error (`SEM010`);
`throw` only accepts `String` (`SEM026`); assignment **and call argument**
respect the nominal `isAssignable` — hierarchy + generic args (invariant by
default, `out`/`in` per declaration-site variance; #688) — (`SEM012`/`SEM014`/
`SEM021`); redeclaration in the same scope is an error (`SEM024`);
switch-expression requires default/exhaustiveness (`SEM032`); exhaustive enum in
switch (`SEM031`).

**Not a guarantee:** `bool→numeric` coercion works by 1/0 representation but is
implementation-defined (§3.1); check the §`not checked` items (`private`/
`protected` **fields**, SG-013).

---

## 8. Generics (erasure-first)

- Type-args live **only** in `ClassType.typeArguments`. **Erasure**: at emit,
  `TypeVariable → Object` (`JvmTypeMapper.java:16`).
- **Positional substitution** (`substituteTypeVariable`, `CompilerTypes.java:255`):
  given `Box<Int>` and type-var `T` (1st type-param of `Box`), returns `Int`. Only
  for **class** type-params, scanning `currentUnit`.
- **No variance** (no `extends`/`super` in type-args — §3.4 of types.md).
- **No bounds** of type-variable (there is no `T extends X`).
- **No constructor type-arg inference**: `new Box(42)` does **not** infer
  `Box<Int>` (`SemExpressionTyper`, case `NewExpr` returns empty type-args).
- **No element checking in collections**: `List<Int>.add("x")` is not detected
  (`MemberCallTyper`/`CollectionMethodTyper`: `add` is typed `Void` without checking). The failure appears **only at runtime,
  in `get` with a concrete type**: `m.put("b","z")` on a `Map<String,Int>` →
  `ClassCastException` when reading (*probe*); `l.add(9)` on a `List<Int>` works
  normally (*probe* — the inferred type was `Int` and 9 is `Int`). **Unspecified**
  as policy.
- `listOf(1,2)` → `List<Int>` (type of the 1st arg); `mapOf(k1,v1,…)` → `Map<K,V>`
  **pinned to the 1st pair**; `setOf(…)` → `Set<T>` (`SemExpressionTyper`, case `setOf`).

---

## 9. Boxing / unboxing

- **Auto-box** from primitive to reference slot at emit (`Integer`, `Long`,
  …; `JvmBackend.java:77-101`).
- **Unbox** in `list.get(i)` according to elemType (`JvmOpCollections.java:95-112`):
  `listOf(1,2).get(0) + 1` → `2` (*probe*).
- **Erasure box** (primitive behind type-var/Object): `kof_box`/`kof_unbox`.
- **Mutable capture** of a closure uses a synthetic `Box<N>` class (see
  [closures.md](closures.md)).

---

## 10. `==` comparison (per-type semantics — decided in lowering)

| Operand | `==` compares | Evidence |
|---|---|---|
| `string` | **content** (`kof_string_equals`) | *probe*: `"ab" == "a"+"b"` → true |
| `record` | **content** (equals generated field by field) | *probe*: `P(1,2)==P(1,2)` → true |
| `enum` | **identity** between two enum values (each constant is a singleton instance) | `CompilerEnumLowering` |
| primitive | **value** | `if_icmp`/`lcmp`/`fcmpl`/`dcmpl` |
| reference (non-string/record/enum) | **identity** (`if_acmp`) | *probe*: `C(1)==C(1)` → false |

An enum value is **not** a String: `Dir.N == "N"` is rejected at compile time
with `SEM062` (D-ENUM207 / issue #207). Compare two enum values, or call
`.name()` explicitly to get the name.

`a.equals(b)` **works** on string (*probe*) but is an anti-pattern — use `==`.

---

## 11. Overload and method resolution

- **Constructors**: overloading **by arity** (`ConstructorSet`,
  `SymbolTable.java:47-58`). Wrong arity → `SEM023`.
- **Methods**: **real overloading by signature** (§131 closed 13/09,
  `18a64d45`): homonyms with different arity/types coexist via
  `MethodSet` (merge in `define`, `select(argCount, argTypes)`); the typer
  (`MemberCallTyper`) picks the candidate and records it in `resolvedMethods()`.
  In the backends: JVM descriptor by signature, own symbol/slot per
  overload in Native, signature mangle in JS. No compatible candidate →
  `SEM013`/`SEM057`.
- **Default parameters** generate synthetic overloads by decreasing arity in the
  lowering (`lowerFunctionDefaults`).
- **Dispatch**: `KofCallKind {INSTANCE, STATIC, CONSTRUCTOR, FUNCTION,
  INTERFACE, SUPER}` → JVM opcode (`INVOKEVIRTUAL`/`STATIC`/`SPECIAL`/
  `INTERFACE`). **Polymorphic virtual dispatch is delegated to the runtime** — the
  compiler only picks the opcode; there is no own vtable. *probe*: `A a = B();
  a.f()` → `2` (real override).

---

## 12. Builtin type methods

There is no `SymbolTable` for `List`/`Map`/`Set`/`String`/`Channel` — they are
**hard-coded signature tables** in three mirrored layers (analysis, typing
lowering, emit lowering). Return examples:

- `List`: `get/remove`→elemType; `size/length/count`→Int; `contains/isEmpty`→
  Bool; `add/push/append/set/clear`→Void; `map/filter/reduce`→higher-order.
- `Map`: `get`→`V?` (reference); `put/remove`→V; `keys`→`List<K>`; `values`→
  `List<V>`.
- `String`: `indexOf/length/compareTo/hashCode`→Int; `isEmpty`→Bool;
  `substring/split/replace/trim/toUpperCase/toLowerCase`→String/String[];
  `toInt/toLong/toDouble/toFloat`→number (functions of the **runtime**, not of
  `java.lang.String`).

`map((x:Int)->…)` → `List<R>`; `filter` → same type as the receiver; `reduce` →
the lambda's return (*probe*: map/filter/reduce correct).

---

## 13. Type error table (SEM0xx)

| Code | Detects | Evidence |
|---|---|---|
| `SEM001` | arithmetic operator on String/non-numeric | `TypeChecker.inferBinaryResultType` |
| `SEM002` | arithmetic on `bool` | `TypeChecker.inferBinaryResultType` |
| `SEM010` | `return` with incompatible type | `StatementAnalyzer` (case `ReturnStmt`) |
| `SEM011` | undefined variable/type | `SemExpressionTyper` (case `IdentifierExpr`) |
| `SEM012` | incompatible assignment (statement) | `StatementAnalyzer` (case `AssignStmt`) |
| `SEM013` | number of arguments ≠ parameters | `TypeChecker.checkArgTypes` |
| `SEM014` | argument with incompatible type — nominal hierarchy + generic args (#688) | `TypeChecker.checkArgTypes` |
| `SEM015` | undefined function / non-function called | `BuiltinCallTyper` |
| `SEM020` | assignment to a never-declared variable | `SemExpressionTyper` (case `AssignExpr`) |
| `SEM021` | explicit type ≠ initializer type | `StatementAnalyzer` (case `VarDeclStmt`) |
| `SEM023` | constructor with wrong arity | `SemExpressionTyper` (case `NewExpr`) |
| `SEM024` | redeclaration in the same scope | `StatementAnalyzer` (case `VarDeclStmt`) |
| `SEM025` | nonexistent method on a known type | `MemberCallTyper` |
| `SEM026` | `throw` of a non-String value | `StatementAnalyzer` (case `ThrowStmt`) |
| `SEM027` | assignment used as an expression | `SemExpressionTyper` (case `AssignExpr`) |
| `SEM028` | `.get()/.set()` on array | `SemMethodCallTyper` |
| `SEM029` | `toArray()` on List/Set | driver:4052 |
| `SEM030` | enum without the accessed constant | driver:4859 |
| `SEM031` | non-exhaustive switch-statement over enum | SwitchStmtLowerer:32 |
| `SEM032` | non-exhaustive `Bool`/enum switch (expression or **statement**, §686) without default | `MemberResolver` / `SemExpressionTyper` |
| `SEM033` | `void` value used as an expression | driver:2675 |
| `SEM034` | `sublist()`/`subSet()` | driver:4067 |
| `SEM037` | reassignment of `val` | parser (`type="val"`) + `StatementAnalyzer` |
| `SEM038` | write to a record component | `StatementAnalyzer` (DD-02) |
| `SEM041` | instantiation of an `abstract` class (`new A()` and `A()`) | `SemExpressionTyper`/`BuiltinCallTyper` (SG-017) |
| `SEM042` | nested type (class inside class) | `ClassMemberParser.parseClassMember` (SG-016) |
| `SEM043` | `implements` without covering the interface method / wrong arity | `ImplementationChecker.checkInterfaceImplementation` (SG-015) |
| `SEM044` | `main()` with a declared return type (`Int main()`) | `SemanticAnalyzer.analyzeFunction` (SG-018) |
| `SEM045` | `throw X` clause with unknown type | `SemanticAnalyzer.checkThrowsClause` (SG-019) |
| `SEM046` | `private`/`protected` access (method or field) outside what is allowed | `MemberCallTyper.checkMemberAccess`/`checkFieldAccess` (SG-013) |
| `SEM017` | no `super`/delegating constructor with that arity in the base class | `ExpressionBareCallLowerer.lower` (deferred to lowering) |
| `SEM059` | overriding method's return type not compatible with the overridden one | `ImplementationChecker.checkOverrideReturnCompatibility` (#326) |
| `SEM060` | calling an instance method via the class name without a receiver (`Calc.add(1)`) | `ExpressionMethodCallLowerer.lower` (#258) |
| `SEM061` | same JVM descriptor redeclared (overload needs a different parameter/return type) | `SymbolTableBuilder.checkMethodRedeclaration` |
| `SEM064` | `interface J extends Base` where `Base` is a class (interfaces may only extend interfaces) | `SemanticAnalyzer.analyzeInterface` (#321) |
| `SEM065` | write to a `final` field outside its class constructor | `MemberCallTyper.checkFinalFieldWrite` (#331/#327; era SEM063, renumerado 17/09 — colidiu com §193) |
| `SEM066` | collection accessor (`get`/`put`/`size`...) called on a String receiver (raw `db.query` row) | `StringReceiverGuards` (§193) |
| `SEM067` | `catch` type is a Kof primitive (primitives are not throwable) | `CatchTypeCheck.check` (#332/#328) |
| `SEM068` | `catch` type is a user class that is not a `Throwable` subclass | `CatchTypeCheck.check` (#332/#328) |
| `SEM069` | `final abstract class X` (contradictory modifiers — no possible instance nor subclass) | `ClassShapeChecks.checkClassDeclaration` (#341) |
| `SEM070` | `class D extends F` where `F` is declared `final` | `ClassShapeChecks.checkClassDeclaration` (#339) |
| `SEM071` | instantiation of an `interface` (`new I()` and `I()`) | `ClassShapeChecks.checkInstantiable` (#340) |
| `SEM072` | wrong-arity `add`/`push`/`append` on a List — e.g. `l.add(i, v)` (there is no positional insert; use `set(i, v)`) | `MemberCallTyper` (#336, all 4 targets) |
| `SEM073` | wrong-arity `reduce` on a List — seedless `reduce((a,b)->…)` (Kof's reduce always takes the lambda AND a seed, either order; the seedless form died in ASM `Frame.merge`) | `MemberCallTyper` (#361, all 4 targets) |
| `SEM074` | instance method on a primitive — e.g. `n.abs()`, `n.equals(o)`, `n.toChar()` (primitives have only `toString()` and the `toInt()`/`toLong()`/`toFloat()`/`toDouble()` conversions; comparison is `a == b`, math is top-level functions like `math.abs(x)`; the unlisted call used to compile and die at class load) | `SemMethodCallTyper` (#362, all 4 targets) |
| `SEM075` | instance field referenced bare inside a `static` method (no implicit `this` exists; the JVM backend used to emit `aload_0` → `VerifyError` at load) — use an instance, or declare the field `static` | `SemExpressionTyper` (#345, all 4 targets) |
| `SEM076` | `Style("<declarations>")` with a property outside the kof.ui whitelist | `KofStyleParser` (D-UI-STYLE/UI007) |
| `SEM077` | `Style("<declarations>")` malformed declaration, or a non-literal argument | `KofStyleParser` (D-UI-STYLE/UI007) |
| `SEM078` | `Style("<declarations>")` with an invalid value for a known property | `KofStyleParser` (D-UI-STYLE/UI007) |
| `SEM079` | design-system token misuse: unknown member of `Spacing`/`Radius`/`Border`/`Elevation`/`Typography`, or a method call on a token namespace | `KofUiTokens` (Fase 10) |
| `SEM080` | subtype (`extends`/`implements`) of a `sealed` type declared outside its compilation unit (the sealed subtype set is closed) | `SealedTypeChecks` (X5.1/D-X5-SURFACE) |
| `SEM081` | `switch` (expression or **statement**, §686) over a `sealed` subject missing a direct subtype case (no `default`) | `MemberResolver` (X5.2/D-X5-SURFACE) |
| `SEM082` | `out` type parameter used in an input position (parameter/writable field) or `in` type parameter used in an output position (return/field/record component) — declaration-site variance soundness | `VarianceChecks` (X5.3/D-TYPE-VARIANCE) |
| `SEM083` | `out`/`in` type parameter passed to a supertype parameter with incompatible variance (or to an invariant one) in `extends`/`implements` — variance soundness in heritage position | `VarianceChecks` (X5.3b/D-TYPE-VARIANCE) |
| `ARITH001` | division/remainder by a **constant** zero | `ExpressionBinaryLowerer` (constant-zero guard) |

Division by a **non-constant** zero (`7 / z` with `z=0`) → **runtime**
error (`ArithmeticException` on the JVM; *probe*), not compile-time.
