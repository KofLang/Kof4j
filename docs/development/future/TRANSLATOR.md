[English](TRANSLATOR.md) | [Português](TRANSLATOR.pt_BR.md)

# TRANSLATOR.md — Kof Translator (DEPRIORITIZED 15/09 → REOPENED 28/09, still in future/)

> **Owner:** 192.168.100.22 (claimed 13/09 ~10:05 — orphan: no owner with IP
> in the header; last code 6 days ago `84c48041`; maintainer's owner-without-IP=orphan
> rule).

**Status:** **REOPENED by the maintainer (`D-DEPRIORITIZED-REOPEN`, 28/09 batch)** —
still plan-only here; promotion to current work is one-at-a-time per
`D-FUTURE-PROMOTION`. Code
already in the repo stays (`Translate.java` + `TranslateLexer`/`TranslateExpr`,
`TranslateTest` 61/61) — only the *queue* stops here. Phase F
implemented: `Translate.java` + `TranslateLexer`/`TranslateExpr`;
proof: `TranslateTest` **61/61** (re-measured 15/09 on tip `7b0bfbe0`;
the header's 30/30 was the 22/08 baseline) — output compiles and runs; +do-while +switch
+try/catch/throw +arrays +cast/instanceof +throws +generics +constructor
+enum-body/multi-decl +interface-extends +bitwise/shift +parentheses
+qualified types +assert +annotations +var +interface-default 13/09).
Expanded Java subset still pending — **BUT see the maintainer directive of
> 13/09 ~21:00 (DOING.md priority table): this lane is DESPRIORIZADO
> ("lane encerrada; gaps restantes = regra 6") — the expanded subset is NOT
> current queue; pick it up only on a new maintainer decision.**)
**Date:** August 22, 2026 · status resynced 14/09 (docs lane, living record)

---

## 1. Objective

Migrate source code to Kof. First target: **Java → Kof**.

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

## 2. What it is NOT

The translator **does not work by textual substitution**:

```text
public → ...
class  → ...
```

Do NOT do this. The tool must understand the **semantic structure** of the
program: types, inheritance, overloads, flow, exceptions — and produce
idiomatic Kof, not a transliteration.

## 3. Planned Progressive Support

- classes;
- interfaces;
- inheritance;
- generics;
- overloads;
- constructors;
- exceptions;
- annotations;
- records;
- enums;
- lambdas;
- nested classes;
- anonymous classes;
- static initialization;
- access modifiers;
- Java standard library;
- calls to external libraries.

## 4. Conceptual Translation Rules

| Java Pattern | Kof Translation |
|---|---|
| Class with getters/setters | Public field |
| Immutable data class | `record` |
| Utility class with static methods | Top-level function |
| `.equals()` on strings | `==` |
| `StringBuilder` | `+` |
| Trivial static factory | Constructor |
| `Optional` | Exception (until `Option<T>` exists) |
| Service/Repository/Controller | Top-level function or direct class |

These rules are **conceptual** — the implementation must derive them from the
program's semantics, never apply them blindly.

## 5. Confidence

The translator records, per translated construct, the origin:

```text
Java construct → Kof construct
```

with a confidence level. Java constructs without a direct Kof equivalent are
marked for **manual review**, not silently altered.

## 6. Implementation Phase

Phase F of the platform roadmap (`LEGACY_MIGRATION.md`).

Before implementing: a small prototype with a subset of Java
(classes, fields, methods, if/while, strings) validated against differential
tests.

> **State (13/09, owner = 192.168.100.22): do-while translated.** `do { ... }
> while (c)` Java → `do { ... } while (c)` Kof (1:1 idiom,
> `training/idioms/control-flow.md`). Cause: `do` was a keyword of
> `TranslateLexer` but no statement consumed it → `parseExprOrDecl`
> failed with `expected ';' but found '{'`. Fix: `do` branch in
> `Translate.parseStatement` (block body uses the raw content, without double
> braces). Proof: `TranslateTest.doWhileTranslates` (translates + compiles on the JVM
> + runs `0/1/2`). `Translate.java` 412 ≤500; `TranslateTest` 10/10.
>
> **State (13/09 ~10:45, owner = 192.168.100.22): switch-statement translated.**
> `switch (x) { case 1: ...; break; default: ... }` Java → `switch (x) {
> case 1: ... default: ... }` Kof statement (`:`, `training/idioms/control-flow.md`).
> Cause: `switch`/`case`/`break`/`default` were keywords without a branch in the statement
> parser → `expected ';' but found '('`. Fix: `parseSwitch` (case body =
> statements until the next `case`/`default`/`}`; `break;` dropped — in Kof it is
> optional/no fallthrough; multiple labels `case "a", "b":` → separate
> cases; arrow `case 3 ->` normalized to `:`). Proof:
> `TranslateTest.switchStatementTranslates` (translates + compiles JVM + runs
> `one/ab`; Int and String). `Translate.java` 476 ≤500; `TranslateTest` 11/11.
>
> **State (13/09 ~11:00, owner = 192.168.100.22): try/catch/finally + throw
> + bare-call translated.** Gap on 3 fronts, all from the same "real method
> body" cluster:
> 1. `try { ... } catch (RuntimeException e) { ... } finally { ... }` Java →
>    `try { ... } catch (String e) { ... } finally { ... }` Kof
>    (`training/idioms/errors.md`: exceptions are Strings). Cause: `try`/`catch`/
>    `finally`/`throw` were keywords without a branch in the statement parser. Fix:
>    `parseTry` (multi-catch `catch (A | B e)` → single catch; empty block →
>    `{}`).
> 2. `throw new RuntimeException(msg)` → `throw msg` (String exception).
> 3. **Latent bug discovered:** call without a receiver (`boom("x");`) had no
>    branch in `parsePostfix` → `expected ';' but found '('` — the parser only
>    handled `recv.metodo(...)`. Fixed (bare-call), otherwise *any* real try
>    body broke.
> Proof: `TranslateTest.tryCatchFinallyTranslates` (translates + compiles JVM +
> runs `caught/done/t2`). **Split done the same day:** `Translate.java`
> 526 → **255** + `TranslateStatements.java` 287 (statements extracted to the
> ≤500 gate — tolerated debt zeroed, `check_500` with no Translate warning);
> `TranslateTest` 12/12.
>
> **State (13/09 ~11:45, owner = 192.168.100.22): arrays + declarations with
> `[]`/generics.** Gap: `int[] xs = new int[3]` → `expected ']' but found
> 'xs'` (the decl-parser did not skip `[]` after the type). **Serious latent bug
> found along the way:** `new int[3]` generated `new Int[]]` (the `parseNew`
> consumed `[` and the FIRST token of the dimension before `parseExpr`) → invalid
> Kof (`PARSE041`); a local `new int[]` did not even reach the compiler because
> the decl-parser already failed. Fix: `parseExprOrDecl` with lookahead
> `isLocalDeclAhead` (generics `List<String> xs`, `Type[] name`, `Type
> name[]`); `parseNew` fixed (size via `parseExpr`, `new T[]{...}` →
> explicit gap R6). `array initializer {...}` → **honest gap** (there is no
> `{...}` literal in Kof — `new Int[n]` + assignments or `listOf`).
> Proof: `TranslateTest.arrayDeclarationTranslates` (translates + compiles JVM +
> runs `10/0/0`; C-style for preserved) +
> `TranslateTest.arrayInitializerIsHonestGap` (explicit diagnostic, R6).
> `TranslateStatements` 326 ≤500; `TranslateTest` 14/14.
>
> **State (13/09 ~12:00, owner = 192.168.100.22): cast + instanceof.**
> `(String) o` → `o as String` (Kof conversion) and `o instanceof String`
> preserved (Kof has it natively, `training/language/overview.md`). Cause:
> `instanceof` was not an operator of `parseRel`; `(Type)` was read as a
> grouping parenthesis → `expected ';' but found 'o'` /
> `expected ')' but found 'instanceof'`. Fix: `instanceof` branch in
> `parseRel` + `isCastAhead`/`parseCast` in `parsePrimary` (lookahead
> `(Type[...]) expr`, including generics and arrays). Proof:
> `TranslateTest.castAndInstanceofTranslate` (translates + compiles JVM + runs
> `x/is-str/3`; reference and primitive cast). `TranslateExpr` 387 ≤500;
> `TranslateTest` 15/15.
>
> **State (13/09 ~12:15, owner = 192.168.100.22): `throws` clause
> dropped.** `static void f() throws IOException` → `void f()` (Kof does not
> declare `throws`; exceptions are Strings, always propagable). Cause: the
> member parser expected `{` right after the parameters →
> `expected '{' but found 'throws'`. Fix: consume `throws` + list until
> `{`/`;` in `parseMember`. Proof: `TranslateTest.throwsClauseIsDropped`
> (translates + compiles JVM + runs `caught`). `TranslateTest` 16/16.
>
> **State (13/09 ~12:30, owner = 192.168.100.22): honest gaps varargs and
> nested type.** Two Java constructs **without** a Kof equivalent become an
> explicit diagnostic (R6: never silent, never a confusing parse error):
> `T...` varargs (Kof only has variadic builtins — `setOf`/`listOf`; a user
> function does not) and nested type (`class`/`interface`/`record`/`enum`
> inside a class — SEM042 requires top level). Cause: `parseParams` read `...`
> as a syntax error; `parseMember` fell into `expected class/...`.
> Proof: `TranslateTest.varargsAndNestedTypeAreHonestGaps`. `TranslateExpr`
> 399 ≤500; `Translate.java` 270 ≤500; `TranslateTest` 17/17.
>
> **State (13/09 ~13:00, owner = 192.168.100.22): generics + constructors.**
> `class Box<T>`/`record Pair<A,B>`/`<T> T id(T x)` → `Box<T>`/`Pair<A,B>`/
> `T id<T>(T x)` (Kof has generics); `new Box<Integer>(5)` → `Box(5)` (Kof
> infers). **Serious latent bug:** a Java constructor (`public User(...)`) had
> the class name read as the return type → the field branch scanned up to
> a nonexistent `;` and **hung in an infinite loop** (EOF) in `kof translate`;
> and the constructor body was discarded (`constructor(...) {}`). Fix:
> detect `ClassName(` before `parseType` → `emitConstructor` with body.
> Bounds `<T extends X>` → honest gap (R6). Proof:
> `TranslateTest.constructorTranslatesWithBody` (translates + compiles JVM + runs
> `Hello Mel/26`) + `TranslateTest.genericsTranslate` (runs `5/7`).
> `Translate.java` 316 ≤500; `TranslateTest` 19/19.
>
> **State (13/09 ~13:15, owner = 192.168.100.22): honest gaps
> try-with-resources and qualified type.** `try (R r = ...)` (Kof without
> AutoCloseable — RAII is a future plan) and `new package.Class(...)` (translator
> ignores imports; mapping Java collections→Kof stdlib is a design decision,
> rule 6) → explicit diagnostic with a manual-review suggestion (R6).
> Proof: `TranslateTest.varargsAndNestedTypeAreHonestGaps` extended.
> `TranslateExpr` 419 ≤500; `TranslateStatements` 335 ≤500;
> `TranslateTest` 19/19.
>
> **State (13/09 ~13:30, owner = 192.168.100.22): enum body + multi-decl.**
> Java enum body (`enum Color { RED; int code(){...} }`) was skipped with
> `skipBlock` on a `;` without `{` → `expected '{' but found 'int'`; now the body
> is skipped token by token until `}` (Kof enum is only constants). Multi-declaration
> `int x = 1, y = 2;` (and without init) → separate Kof statements
> (`var x = 1 var y = 2`); before `expected ';' but found ','`. Proof:
> `TranslateTest.enumBodyAndMultiDeclTranslate` (translates + compiles JVM + runs
> `3`). `TranslateStatements` 354 ≤500; `TranslateTest` 20/20.
>
> **State (13/09 ~13:45, owner = 192.168.100.22): interface `extends` +
> honest gaps labeled/anon.** `interface B extends A` → Kof (verified in the
> compiler); before `expected '{' but found 'extends'`. Labeled statement
> (`outer: for ...`) and anonymous class (`new Runnable(){...}`) have no
> Kof equivalent → explicit diagnostic (R6). Proof:
> `TranslateTest.interfaceExtendsTranslates` (translates + compiles JVM + runs
> `g/f`) + `varargsAndNestedTypeAreHonestGaps` extended. `Translate.java`
> 335 ≤500; `TranslateTest` 21/21.
>
> **State (13/09 ~14:15, owner = 192.168.100.22): 3 latent correctness
> bugs (Q4).**
> 1. **Parentheses were discarded** — `(1+2)*3` → `1+2*3` (=7, not 9):
>    the generated Kof compiled with **wrong semantics** (the worst bug). Fix:
>    `parsePrimary` preserves `( ... )` (Kof accepts redundant parentheses).
> 2. **`&`/`|`/`^`/`<<`/`>>`/`>>>` silently dropped** by the lexer
>    (only `&&`/`||` emitted a token) → operands glued together and the parser broke.
>    Fix: `AMP`/`CARET` tokens; shifts combined from `LT`/`GT` in the parser
>    (avoids conflict with generics `List<String>`); `parseBitAnd`/
>    `parseBitOr`/`parseShift` with the Kof parser's precedence
>    (`KofFormatter.precOf`).
> 3. **Qualified type** `java.util.Map<...>` → `Map<...>` (strips package;
>    `Map`/`List`/`Set` are Kof builtins). `new java.util.ArrayList()` remains
>    an honest gap (mapping a collection = design decision).
> Proof: `TranslateTest.parenthesesPreservePrecedence` (runs `9/-3`),
> `bitwiseAndShiftTranslate` (runs `2/7/5/24/3/3/2147483644`),
> `qualifiedTypeNamesAreStripped`. `TranslateExpr` 471 ≤500;
> `TranslateTest` 24/24.
>
> **State (13/09 ~14:30, owner = 192.168.100.22): assert + `for` gap with
> comma.** `assert cond;` / `assert cond : msg;` Java → `assert(cond)` /
> `assert(cond, msg)` Kof (test primitive, it is a function — `AssertE2ETest`);
> before `expected ';' but found 'x'`. C-style `for` with multiple init/incr
> (`for (int i=0, j=3; ...; i++, j--)`) has no Kof equivalent (for does not
> accept a comma — PARSE041; desugaring to while changes the `continue` flow) →
> honest gap (R6). Proof: `TranslateTest.assertTranslates` (runs `1`) +
> `forMultipleInitIncrIsHonestGap`. `TranslateStatements` 399 ≤500;
> `TranslateTest` 26/26.
>
> **State (13/09 ~14:45, owner = 192.168.100.22): annotations discarded.**
> `@Override`, `@Deprecated`, `@SuppressWarnings("x")` (with args) on type and
> member → discarded (Kof ignores them; verified in the compiler); before
> `expected class/... found '@'`. Fix: `skipAnnotationsAndModifiers` in
> `parseTypeDeclaration` + `@` branch in `parseMember`. Proof:
> `TranslateTest.annotationsAreDiscarded` (translates + compiles JVM + runs
> `x/f`). `Translate.java` 377 ≤500; `TranslateTest` 27/27.
>
> **State (13/09 ~15:00, owner = 192.168.100.22): `var`, interface
> `default`, interface constant.** (1) `var x = 1;` Java local → `var x =
> 1` Kof (`var` is reserved identically; before `expected ';' but found 'x'`).
> (2) interface `default`/method body — **FIXED 13/09 ~16:45:** the
> previous note ("Kof accepts a body in an interface") was a **FALSE
> verification** (Q5): Kof **ignores** the body and the implementer fails with `SEM043`
> (`kof check` on the binary). It is now an **honest gap R6** (no default method in
> Kof). (3) interface constant (`int X = 1;` implicitly
> `static final`) → **honest gap R6**: Kof accepts the declaration but does not resolve it
> (`I.X`/`C.X` → `SEM025`), with no direct equivalent. Proof:
> `TranslateTest.varLocalTranslates` (runs `1/hi`),
> `interfaceDefaultMethodIsHonestGap` (replaces the false test),
> `interfaceAbstractSignatureTranslates` (runs `7`),
> `interfaceConstantIsHonestGap`.
> `Translate.java` 396, `TranslateStatements` 404 ≤500; `TranslateTest` 30/30.
>
> **State (13/09 ~15:50, owner = 192.168.100.22): array-initializer in a FIELD
> (latent bug Q4).** `int[] xs = {1,2,3}` as a class **field** (not
> local) did not go through the honest-gap guard that the local one already had → the
> translator emitted `Int[] xs = {` **truncated** = silent invalid Kof
> (violates R6; the worst kind, broken output without a diagnostic). Fix: `Translate.
> parseMember` detects `{` after `=` and throws the same R6 `TranslateException` as the
> local one. Proof: `arrayInitializerIsHonestGap` extended (field + local) —
> `TranslateTest` 33/33; probe on the binary `kof translate` → explicit
> diagnostic, without truncation. `Translate.java` 406 ≤500; `check_500` OK.
>
> **State (13/09 ~16:00, owner = 192.168.100.22): instance initialization
> block (latent bug Q4).** Non-static `{ ... }` inside a class ran
> before every constructor in Java; the translator dropped it **silently**
> (only `static {}` skip was documented), changing behavior without a diagnostic
> (violates R6). Fix: `Translate.parseMember` distinguishes `static {}` (skip,
> consistent with static field) from instance (→ R6 `TranslateException`,
> guides moving to `constructor`). Proof:
> `TranslateTest.instanceInitializerBlockIsHonestGap` (instance gap + static
> skipped) — `TranslateTest` 34/34; probe on the binary → explicit diagnostic.
> `Translate.java` ~420 ≤500; `check_500` OK.
>
> **State (13/09 ~16:15, owner = 192.168.100.22): Java numeric literals
> (latent bug Q4).** The lexer only consumed digits+dot → `10L` became `10`
> `L`, `1.5e3` → `1.5` `e3`, `1.5f` → `1.5` `f`, `0x1F` → `0` `x1F` — all
> gave `expected ';' but found '…'` (confusing parse error). Fix: `scanNumber`
> consumes decimal/hex/bin, `_`, dot, exponent `e/E`/`p/P`, suffixes
> `l/L/f/F/d/D`; the text is preserved (Kof accepts the same forms — probe
> `kof check`), **except** `_` which Kof rejects (PARSE043) → removed (same
> value). Proof: `TranslateTest.javaNumericLiteralsTranslate` (translates +
> compiles JVM + runs `68088.5`, javac oracle) — `TranslateTest` 35/35; binary
> probe on the 7 literals. `check_500` OK.
>
> **State (13/09 ~16:30, owner = 192.168.100.22): string/char escapes +
> local/param `final` (latent bugs Q4).** (1) The lexer **decoded** the
> Java escapes to real chars and the emitter re-emitted them **raw** → invalid
> Kof/wrong semantics: `"say \"hi\""` became `"say "hi""` (PARSE043) and
> `"path\\x"` became `"path\x"` (Kof swallows the backslash → `pathx` ≠ Java
> `path\x`). Fix: `TranslateLexer.escapeKofString/escapeKofChar` re-escape
> `\ " \n \t \r` on emit (helpers in the lexer so `TranslateExpr` stays ≤500).
> (2) `final` on a local (`final int y = 2;`) and on a parameter (`p(final int x)`)
> gave a parse error — Kof has no local/param `final` (mutable vars); the
> modifier is discarded. Proof:
> `TranslateTest.stringEscapesRoundTripToValidKof` (runs `say "hi"`/`path\x`)
> and `finalLocalAndParamTranslate` (runs `3hi`) — `TranslateTest` 37/37.
> `TranslateExpr` 500 (limit), `TranslateStatements` 451 ≤500; `check_500` OK.
>
> **State (13/09 ~16:45, owner = 192.168.100.22): interface `default` +
> static fields (latent bugs Q4).** (1) **CORRECTION of unit `3ab4c99e`:**
> the previous note said that "Kof accepts a body in an interface" — **false**
> (re-verified on the binary: Kof ignores the body and the implementer fails with
> `SEM043`). An interface method with a body (`default`/`static`) is now an **honest
> gap R6**; an abstract signature remains `Type m(): Type` (probe: `7`).
> (2) A Java `static` field was **silently skipped** → the reference became
> `Undefined variable or type` (SEM011) = invalid Kof. It now emits `static`
> and, in **hoisted** functions (`static`/`main` methods promoted to top-level),
> qualifies the bare ref `X` → `Class.X` via `TranslateStatics` (new: scan
> + safe qualification, without touching strings/`.X`/shadowing parameter). Proof:
> `interfaceDefaultMethodIsHonestGap`, `interfaceAbstractSignatureTranslates`
> (runs `7`), `staticFieldsTranslateAndQualifyInHoistedFns` (runs `5/hi/5`) —
> `TranslateTest` 39/39. `Translate.java` 443, `TranslateStatics` 134 ≤500.
>
> **State (13/09 ~17:30, owner = 192.168.100.22): `this(...)`, generic
> wildcard, BLOCK lambda body + `TranslateTypes` split (latent bugs
> Q4).** Three Java constructs that produced invalid Kof/confusing parse errors
> now have explicit handling, and `TranslateExpr` (which blew past 500 with
> the fixes) was relieved by extraction. (1) **Constructor delegation
> `this(...)`** → Kof does not have it (probe: `variable 'this' is not a function` =
> SEM015) → **honest gap R6**. (2) **Generic wildcard** `? extends/super`
> → Kof rejects it (PARSE086) → **honest gap R6** (instead of emitting `? extends
> Number`). (3) **BLOCK lambda body** `() -> { ... }` → the parser only
> accepted an expression (`expected ';' but found 'System'`); Kof **accepts** a block
> (`() -> { counter = counter + 1 }` verified on the binary) → now translates
> `(params) -> { stmts }`. (4) **Split:** static type helpers
> (`isModifier`/`isTypekeyword`/`isPrimitiveOrType`/`isKeyword`/`kofType`)
> extracted from `TranslateExpr` (500→487) to `TranslateTypes.java` (50).
> Proof: `constructorDelegationIsHonestGap`, `wildcardGenericIsHonestGap`,
> `lambdaBlockBodyTranslates` (runs `14` in the generated Kof) — `TranslateTest`
> **42/42**; post-rebase 4-module gate **1497/0 + 33/0 + 5/0 + 194/0**, BUILD SUCCESS.
> `check_500` OK (no `Translate*` warning).
>
> **Note (found in the Q4 hunt, recorded §177 — ✅ FIXED by the
> bugs-and-gaps lane `192.168.100.15`):** a lambda with a BLOCK body that returns a
> **local declared in the block itself** was typed VOID (`SEM033`) **when the
> module contained a class** (`main`+`class C {}`); without the class the same
> program passed. Root: `ExpressionTyper.firstReturnValueType` did not register
> the block's `VarDeclStmt` in the scope when inferring the return. Fix from the
> bugs-and-gaps lane: mutable copy scope with the body locals; proof
> `CoreRegressionE2ETest.lambdaReturnLocalVar` (4 targets). The test
> `lambdaBlockBodyTranslates` uses `return n + 1` and remains valid.
>
> **State (13/09 ~18:30, owner = 192.168.100.22): switch-EXPRESSION +
> method-ref/text-block/instanceof-pattern/import-static/`Math.` + split
> `TranslateSwitch` (latent bugs Q4).** Eight Java constructs that gave a
> confusing parse error or **silent invalid Kof** now have handling:
> (1) **switch-EXPRESSION** `return switch (x) { case 1, 2 -> 10; default -> 0; }`
> → translates to the Kof switch-expr (`case L -> expr`, `training/idioms/
> control-flow.md`); multi-label expands into separate cases (Kof rejects the
> list, PARSE078) and the colon+`yield` form becomes `case L -> expr`; a case
> body in a BLOCK is an **honest gap R6** (Kof requires ONE expression, PARSE094) —
> before `expected ';' but found '{'`. (2) **Method reference** `Type::method`
> → Kof only has lambda → **honest gap R6** (before `expected ')' but found ':'`).
> (3) **Text block** `"""…"""` → Kof does not have it → **honest gap R6** (before the
> lexer read an empty `""` and reopened). (4) **`instanceof` binding pattern**
> `o instanceof String s` → **honest gap R6** (before `expected ')' but found
> 's'`). (5) **Qualified type in an EXPRESSION** `java.util.List.of(...)` →
> before it emitted invalid Kof (`java` undefined = SEM011) **silently** →
> **honest gap R6** (mapping Java→stdlib is a design decision, rule 6).
> (6) **JDK `import static`** `import static java.lang.Math.max` + `max(3,4)`
> → emitted `max(3, 4)` without a diagnostic (SEM011) → **honest gap R6**;
> a static import of the program's own class passes (the static method becomes
> a top-level Kof function and resolves). (7) **Literal `.5`** → normalized to
> `0.5` in the lexer (Kof requires the zero; `.5` is PARSE041). (8) **Receiver
> `Math.`** `Math.max(3,4)` / `Math.PI` → before it emitted `Math.max(...)` /
> `Math.PI` (invalid Kof = **silent** SEM011); Kof exposes the stdlib in
> `math.*`, but `math.min/max/abs` are **Int-only** (SEM025 for Double, without
> widening) and the translator has no types to choose the overload → **honest
> gap R6**. (9) **`~x`** (bitwise complement) → Kof does not have `~`
> (PARSE041), but `~x == -x - 1` is exact in two's complement → emits
> `(-x - 1)`; the **lexer now REJECTS** an unexpected character instead of
> silently dropping it (it was the root of `~` disappearing). (10) **LOCAL class**
> (`class`/`interface`/`enum`/`record` inside a method) → Kof does not have
> nested types (SEM042) → **honest gap R6** (before `expected ';' but
> found 'B'`). (11) **Empty statement `;`** → discarded (before
> `expected ';' but found 'return'`). (12) **`main(String[] args)`** →
> `main(args)`: the params were **DISCARDED** and the body could reference
> `args` → silent invalid Kof; Kof accepts `main(String[] args)`
> (verified on the binary). **Splits:** the switch-expression parser (~55
> lines) went to `TranslateSwitch.java` (77) and `new` to
> `TranslateNew.java` (72) — `TranslateStatements` 528→470 and
> `TranslateExpr` 554→495, both ≤500. Proof: `switchExpressionTranslates`
> (runs `10/0` in the generated Kof), `methodReferenceIsHonestGap`,
> `textBlockIsHonestGap`, `instanceofBindingPatternIsHonestGap`,
> `qualifiedTypeInExpressionIsHonestGap`, `jdkStaticImportIsHonestGap`,
> `ownStaticImportPassesThrough`, `mathReceiverIsHonestGap`,
> `leadingDotLiteralIsNormalized` (runs `0.5`), `bitComplementTranslates`
> (runs `-6/-1`), `emptyStatementIsSkipped` (runs `1`),
> `localClassIsHonestGap`, `mainArgsArePreserved` (runs `0`) —
> `TranslateTest` **55/55**; `check_500` OK **with no translate debt**.
>
> **Known design gap (rule 6, NOT fixed):** other **JDK class
> receivers** (`Integer.parseInt`, `Long.valueOf`, `Objects.requireNonNull`,
> `Collections.sort`, `Arrays.asList`, `String.valueOf`, `StringBuilder`…) and
> `new` of JDK types have no mapping to the Kof stdlib — the translator emits the
> name as is, which fails with SEM011 (downstream diagnostic, not
> silent). Mapping Java→stdlib is a design decision (rule 6); it stays
> recorded as a future queue, not as an edit.
>
> **State (13/09 ~19:30, owner = 192.168.100.22): `~x`, local class, empty
> `;`, `main(args)` + `TranslateNew` split (2nd Q4 probe sweep).**
> (1) **`~x`** (bitwise complement): Kof does not have `~` (PARSE041); the
> identity `~x == -x - 1` is exact in two's complement → emits
> `(-x - 1)`. The root was the **lexer silently dropping `~`** → now the
> lexer `default` **rejects** an unexpected character with a diagnostic
> (before: silent truncated Kof). (2) **LOCAL class** inside a method →
> Kof does not have nested types (SEM042) → **honest gap R6**. (3) **Empty
> statement `;`** → discarded. (4) **`main(String[] args)`** → `main(args)`: the
> params were **discarded** and the body could reference `args` → silent
> invalid Kof; Kof accepts `main(String[] args)` (verified on the
> binary). **Split:** `new` → `TranslateNew.java` (72); `TranslateExpr`
> 554→495 (≤500). Proof: `bitComplementTranslates` (runs `-6/-1`),
> `localClassIsHonestGap`, `emptyStatementIsSkipped` (runs `1`),
> `mainArgsArePreserved` (runs `0`) — `TranslateTest` **55/55**; 4-module
> gate **1499/0 + 33/0 + 5/0 + 207/0**, BUILD SUCCESS; `check_500` OK
> **with no translate debt**.

> **State (13/09 ~20:00, owner = 192.168.100.22): unicode/char escapes, 1-param
> lambda, enum/record/init/abstract (3rd Q4 probe sweep).**
> (1) **String/char escapes**: the lexer dropped the backslash of `\uXXXX` and emitted
> the raw text (invalid Kof); `\b`/`\f` and octals went raw; `char '\n'`/`'\''`/
> `'\uXXXX'` fell into the fallthrough and were **dropped**. Now `decodeEscape` +
> `Esc(ch,len)` decode `\n \t \r \b \f \" \' \\`, `\uXXXX` and octal, in
> string AND char; the emit re-escapes control `<0x20`/`0x7F` as `\uXXXX` (Kof
> supports unicode escape). (2) **1-parameter lambda WITHOUT parentheses**
> (`x -> x + 1`): Kof requires parentheses (PARSE041) → emits `(x) -> x + 1`
> (before: `expected ';' but found '->'`). (3) **Enum with constructor/constant
> body** (`A(1)`, `A { … }`) and **record with body** (compact constructor/
> accessors) → **honest gap R6** (before: SILENTLY skipped = validation
> vanished). (4) **`static {}` and instance block** → gap R6; **`abstract`/`native`
> method without a body** in a class → gap R6 (before: silently dropped
> → the call became SEM011). Proof: `unicodeAndControlEscapesRoundTrip` (runs
> `A/true/3/true/true/true/true`), `singleParamLambdaWithoutParensTranslates`,
> `enumBodyIsHonestGap`, `recordBodyIsHonestGap`, `abstractMethodIsHonestGap`,
> `instanceInitializerBlockIsHonestGap` (static included) — `TranslateTest`
> **60/60**; 4-module gate **1499/0 + 33/0 + 5/0 + 212/0**, BUILD SUCCESS;
> `check_500` OK (`TranslateExpr` 506 = tolerated debt ≤599; split planned).
