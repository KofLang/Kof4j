[English](grammar.md) | [Português](grammar.pt_BR.md)

# Kof Formal Grammar

**Status:** Stable (the form) · **Evidence:** `Parser.java` (1975 lines)

## Formalism and rationale

The Kof parser is **recursive descent with precedence-climbing for binary
expressions** (ExpressionParser.parseBinary), **hand-written**, with arbitrary
lookahead over the token list (`check`/`checkNext`/scans in `looksLike*`). It
is **not** strict LL(1), **not** PEG, **not** tool-generated (there is no
`.g4`/`.y`/`.ebnf` file).

Therefore the grammar below is presented in **EBNF** as an *extractive
description* of the real parser — not as the specification the parser follows.
Where the parser uses a lookahead heuristic (e.g. `looksLikeLambdaParams`), the
grammar marks the construct as **ambiguous, resolved by lookahead**, and the
exact behavior is **Implementation-defined**.

EBNF convention used:

`text
=           definition         { x }   zero or more
,           concatenation      [ x ]   optional
( x | y )   alternative        "x"     literal terminal
(* ... *)   comment
`

`text
Source-code ──Lexer──▶ Tokens ──Parser──▶ AST ──Desugar──▶ AST ──Analyze──▶ annotated AST (side maps)
`

The **lexical grammar** is in [lexical-structure.md](lexical-structure.md).
Here is the **syntactic grammar** and the resulting **AST**.

---

## 1. Compilation unit

`ebnf
compilation-unit = [ package-declaration ] , { import-declaration } ,
                   { top-level-declaration } ;

package-declaration = "package" , qualified-identifier , [ ";" ] ;        (* `Parser.parsePackage` *)
import-declaration  = "import" , ( "*" | import-path ) , [ ";" ] ;       (* `Parser.parseImports` *)
import-path         = identifier , { "." , identifier } , [ ".*" ] ;

top-level-declaration =
      annotation-list , type-declaration
    | annotation-list , function-declaration
    | test-declaration
    | application-declaration
    | extern-declaration
    | foreign-module-declaration
    | type-declaration
    | function-declaration ;
`

**Only** type, function, `test`/`application` blocks and `extern` declarations may appear at the top. `val`/`var`
at the top → `PARSE007` (*probe*). There is no `let` (SG-001).

> **Resolution of the question "does the parser produce the AST directly?":** **no.** The
> parser produces a *raw* AST; the driver applies **desugaring** to it before
> analysis (`CompilerDriver.java`, calls to `CompilerDesugar.desugarTests`/`desugarApplication`): `desugarTests` (`test` blocks →
> harness) and `desugarApplication` (`application { onStart/onShutdown }`
> blocks → synthesized functions that wrap `main`). See
> [../compiler-architecture.md](../architecture/compiler-architecture.md).

---

## 2. Type declarations

`ebnf
type-declaration =
      class-declaration | interface-declaration | record-declaration
    | enum-declaration  | entity-declaration ;

modifiers = { "public" | "private" | "protected" | "static" | "final"
            | "abstract" | "transient" | "volatile" | "synchronized"
            | "native" | "default" | "override" } ;                 (* `TypeDeclarations.parseModifiers` *)

class-declaration = modifiers , "class" , identifier , [ type-parameters ] ,
                    [ "extends" , type-ref ] , [ implements-clause ] ,
                    ( class-body | record-header , class-body ) ;   (* `TypeDeclarations.parseClassDeclaration` *)
`

> `class X(...)` **with parentheses** is parsed as a **record** (body via
> `parseRecordBody`, TypeDeclarations.parseRecordDeclaration) — not as a class with a primary
> constructor. This is the behavior documented in `AGENTS.md`.

`ebnf
class-body = "{" , { class-member } , "}" ;
class-member = annotation-list , modifiers , ( constructor-declaration
             | method-declaration | field-declaration | type-declaration ) ;

interface-declaration = modifiers , "interface" , identifier ,
                        [ "extends" , type-ref , { "," , type-ref } ] ,
                        "{" , { class-member } , "}" ;              (* `TypeDeclarations.parseInterfaceDeclaration` *)
`

**Interfaces do not accept type-parameters** (`interface F<T>` → `PARSE007`,
*probe*).

`ebnf
record-declaration = modifiers , "record" , identifier , [ type-parameters ] ,
                     [ "extends" , type-ref ] , record-header ,
                     [ implements-clause ] , [ record-body ] ;      (* `TypeDeclarations.parseRecordDeclaration` *)
record-header      = "(" , [ record-component , { "," , record-component } ] , ")" ;
record-component   = annotation-list , modifiers , type-ref , identifier ,
                     [ "=" , expression ] ;                         (* `ClassMemberParser.parseField` *)
record-body        = "{" , { class-member } , "}" ;

enum-declaration = modifiers , "enum" , identifier ,
                   "{" , [ identifier , { "," , identifier } ] , "}" ;  (* `TypeDeclarations.parseEnumDeclaration` *)
`

**Enums are just constants** — no body, no methods, no constructors, no
fields (`enum E { A String f(){…} }` → `PARSE032`, *probe*); a clause before
the `{` (`enum Cor extends Tudo { A, B }`) is a single `PARSE034` (§687). At runtime the
value of an enum **is** the name (`String`) — see [classes.md](classes.md).

`ebnf
entity-declaration = modifiers , "entity" , identifier ,
                     "{" , { entity-field } , "}" ;                 (* `TypeDeclarations.parseEntityDeclaration` *)
entity-field       = identifier , ":" , type-ref , { "generated" | "unique" } ;
`

---

## 3. Functions and methods

`ebnf
function-declaration = annotation-list , modifiers ,
                       [ type-ref ] , identifier , [ type-parameters ] ,
                       "(" , [ parameter-list ] , ")" ,
                       [ ":" , type-ref ] ,                          (* suffixed return *)
                       function-body ;                               (* Parser.java *)

function-body = block | "=" , expression , [ ";" ] | ";" ;          (* body: block, expression, or abstract *)

method-declaration = [ type-ref ] , identifier , "(" , [ parameter-list ] , ")" ,
                     [ ":" , type-ref ] , [ throws-clause ] , function-body ;
constructor-declaration = [ "constructor" ] , "(" , [ parameter-list ] , ")" ,
                          [ throws-clause ] , block ;                (* `ClassMemberParser.parseConstructor` *)

parameter-list = parameter , { "," , parameter } ;
parameter      = annotation-list , modifiers ,
                 ( type-ref , identifier | identifier , ":" , type-ref ) ,
                 [ "=" , expression ] ;                              (* `ClassMemberParser.parseField` *)
throws-clause  = "throw" , type-ref , { "," , type-ref } ;           (* `TypeParser.parseThrows` *)
type-parameters = "<" , identifier , { "," , identifier } , ">" ;    (* `TypeParser.parseTypeParameters` *)

extern-declaration = "extern" , [ string-literal ] , identifier ,
                     [ type-parameters ] , "(" , [ parameter-list ] , ")" ,
                     [ ":" , type-ref ] , [ ";" ] ;                  (* `Parser.parseExternDeclaration` *)
(* no body: the binding is by target at runtime (JVM binds any scalar
   signature since R3 18/09; JS host runner binds scalars too (3.6.F2/F3, 18/09).
   FFI001 = non-scalar JVM/native; FFI002 = non-scalar JS; browser = runtime R7). Grammar
   ACCEPTS any arity; CompilerPipeline.isExternBound rejects non-scalar at compile time. *)

foreign-module-declaration =
      "foreign" , "module" , identifier , "{" ,
      { module-header | extern-declaration } , "}" ;          (* `Parser.parseForeignModule` *)
module-header = ( "library" , string-literal
                | "abi" , identifier
                | "ownership" , identifier ) , [ ";" ] ;
(* Connector ecosystem `D-CONNECTORS` (plan §9.16 slice A): sugar over the
   EXISTING FFI path — it desugars to plain `extern` declarations sharing the
   module `library` (no new ABI engine, rule 54). `library` is required (else
   PARSE097); `ownership` is validated against the Core vocabulary
   `owned|borrowed|shared|opaque|immutable|mutable` (else PARSE099). `foreign`
   and `module` are contextual — they stay identifiers outside this header. *)
`

The **three return forms** are valid and equivalent:

`kof
String a() { … }      // type before the name
b(): String { … }     // type after the parentheses (annotated form)
c() { … }             // no type → void (default)
`

**There is no function declaration keyword** (SG-001 resolved 06/09):
`fn`/`fun`/`func` as a prefix are rejected with `PARSE085`. In **any name
position** (function, variable, parameter, method, field, class, record, enum)
they are also rejected with `PARSE085` — `ParseContext.expectId` emits the
canonical diagnostic (#330, measured 17/09). They are never valid identifiers.
Parameters accept **default values**
(`parameter = expression`), which generate synthetic overloads by arity in the
lowering.

---

## 4. Types (syntactic reference)

`ebnf
type-ref = "void"
         | function-type
         | primitive-type
         | qualified-name , [ generic-args ] , { "[]" } , { "?" } ;

primitive-type = "bool" | "byte" | "short" | "int" | "long"
               | "float" | "double" | "char" | "string" ;           (* `TypeParser (primitives)` *)
qualified-name = identifier , { "." , identifier } ;
generic-args   = "<" , type-ref , { "," , type-ref } , ">" ;        (* `TypeParser (generic args)` *)
function-type  = "(" , [ type-ref , { "," , type-ref } ] , ")" , "->" , type-ref ;  (* `TypeParser.parseFunctionTypeRef` *)
`

The parser captures the type as a **raw string** (`parseTypeRef` returns
`String`), not as a structure. Resolution to `Type` happens during semantic
analysis. The `[]` (array) and `?` (nullable) suffixes are **appended to the
string** (`Int[]?` is valid). Nested generics (`Map<String, List<Int>>`) are
consumed by depth counting with a split of `>>`/`>>>`
(`splitShiftRight`).

> **`?` is a type suffix, not an expression operator.** `Int?` is nullable;
> `x?y` is not syntax.

---

## 5. Expressions

`ebnf
expression = switch-expression | assignment ;                       (* `ExpressionParser.parseExpression` *)

assignment = binary , [ assign-op , assignment ] ;                  (* right-assoc, `ExpressionParser.parseAssignment` *)
assign-op  = "=" | "+=" | "-=" | "*=" | "/=" | "%="
           | "&=" | "|=" | "^=" | "<<=" | ">>=" | ">>>=" ;

binary     = unary , { binary-op , unary } ;                        (* precedence climbing, `ExpressionParser.parseBinary` *)
binary-op  = "||" | "&&" | "|" | "^" | "&"
           | "==" | "!=" | "<" | "<=" | ">" | ">="
           | "instanceof" | "as"
           | "<<" | ">>" | ">>>"
           | "+" | "-" | "*" | "/" | "%" ;

unary      = ( "spawn" | "await" | "!" | "-" | "++" | "--" ) , unary
           | postfix ;                                              (* `ExpressionParser.parseUnary` *)

postfix    = primary , { postfix-op } ;                             (* `ExpressionParser.parsePostfix` *)
postfix-op = "." , identifier , [ call-args | trailing-lambda ]
           | "[" , expression , "]"
           | call-args , [ trailing-lambda ]
           | "<" , generic-args , ">" , call-args , [ trailing-lambda ]   (* generic call *)
           | "++" | "--" ;

primary    = literal | "this" | "super" | identifier
           | new-expression
           | "(" , lambda-params , ")" , "->" , lambda-body         (* lambda *)
           | "(" , expression , ")"                                 (* parenthesis *)
           | "if" , "(" , expression , ")" , expression , "else" , expression   (* if-expr *)
           | "{" , lambda-body , "}" ;                              (* lambda without params *)

new-expression = "new" , type-ref , [ generic-args ] ,
                 ( "[" , expression , "]" | call-args ) ;           (* `ExpressionParser.parsePostfix` *)

lambda-params = [ lambda-param , { "," , lambda-param } ] ;
lambda-param  = identifier , [ ":" , type-ref ] ;                   (* `LambdaParser` *)
lambda-body   = block | expression , [ ";" ] ;                      (* `LambdaParser` *)
`

### 5.1 Precedence and associativity (exact — ExpressionParser (precedence table))

Higher precedence at the top. **All binaries are left-associative**
(`parseBinary(prec+1)`); assignment is **right-associative**.

| Prec | Operators | Assoc |
|---|---|---|
| 8 | `* / %` | left |
| 7 | `+ -` | left |
| 6 | `<< >> >>>` | left |
| 5 | `== != < <= > >= instanceof as` | left |
| 4 | `&` | left |
| 3 | `\| ^` | left |
| 2 | `&&` | left (short-circuit) |
| 1 | `\|\|` | left (short-circuit) |
| 0 | `= +=` … | **right** |

> `instanceof` and `as` have the **same** precedence (5) and are left-assoc in
> the parser, but the lowering **breaks the chaining** on them (bug 13,
> `ExpressionLowerer.java:174-176`): `(x as Int) + 1` is not `x as (Int + 1)`.
> The behavior of pure chaining (`a as B as C`) is **Unspecified**.

### 5.2 Short-circuit

`&&` and `||` are evaluated with short-circuit on **all targets** — via labels
in JVM/Native, via native operators in JS (SG-006 ✅ FIXED 09/09).

### 5.3 Operators that do NOT exist (SG-002, verified by probe)

`~` (bitwise NOT), `?:` (elvis), `??` (null-coalesce), `..` (range), `in`
(membership in an expression), `=>`, `::`, `|>`, `...`. All produce a parse
error. For membership use `setOf(…).contains(x)` (the language idiom).

---

## 6. Statements

`ebnf
statement = block | return-stmt | if-stmt | while-stmt | do-while-stmt
          | for-stmt | throw-stmt | spawn-stmt | assert-stmt | try-stmt
          | switch-stmt | break-stmt | continue-stmt | var-decl | expr-stmt ;

block       = "{" , { statement } , "}" ;                           (* `StatementParser.parseBlock` *)
return-stmt = "return" , [ expression ] , [ ";" ] ;                 (* `StatementParser.parseReturn` *)
if-stmt     = "if" , "(" , expression , ")" , statement , [ "else" , statement ] ;
while-stmt  = "while" , "(" , expression , ")" , statement ;
do-while    = "do" , statement , "while" , "(" , expression , ")" , [ ";" ] ;
for-stmt    = "for" , "(" , ( for-init | for-in ) , ")" , statement ;
for-in      = ( "var" | "val" ) , identifier , "in" , expression ;  (* "in" is contextual *)
for-init    = [ statement ] , [ expression ] , ";" , [ expression ] ;
throw-stmt  = "throw" , expression , [ ";" ] ;
spawn-stmt  = "spawn" , expression , [ ";" ] ;
assert-stmt = "assert" , "(" , expression , [ "," , string-literal ] , ")" , [ ";" ] ;
try-stmt    = "try" , block , { catch-clause } , [ "finally" , block ] ;
catch-clause = "catch" , "(" , type-ref , identifier , ")" , block ;
break-stmt  = "break" , [ ";" ] ;                                   (* no label *)
continue-stmt = "continue" , [ ";" ] ;                              (* no label *)
var-decl    = ( "var" | "val" | type-ref ) , identifier ,
              [ ":" , type-ref ] , [ "=" , expression ] , [ ";" ] ; (* `StatementParser.parseVarDecl` *)
expr-stmt   = expression , [ ";" ] ;
`

### 6.1 Switch — two forms

`ebnf
switch-stmt = "switch" , "(" , expression , ")" , "{" , { case-stmt } ,
              [ default-stmt ] , "}" ;                              (* `StatementParser.parseSwitchStatement` *)
case-stmt   = "case" , ( pattern | expression ) , ":" , { statement } ;
default-stmt = "default" , ":" , { statement } ;

switch-expression = "switch" , "(" , expression , ")" , "{" , { case-expr } ,
                    [ default-expr ] , "}" ;                        (* `ExpressionParser.parseSwitchExpression` *)
case-expr   = "case" , ( pattern | expression ) , "->" , expression ;
default-expr = "default" , "->" , expression ;

pattern = type-name , identifier                          (* binding:  case String s *)
        | type-name , "(" , { ( "var" | "val" )? , identifier } , ")" ;  (* destructuring: case Point(var x, var y) *)
`

- **Statement** uses `:`; **expression** uses `->`. The expression form requires
  `default` (or an exhaustive enum) — `SEM032`.
- **There is no fallthrough**: each statement case jumps to the end
  (`SwitchStmtLowerer.java:174`) — *probe*: `case 1: println("a") case 2:
  println("b")` with value 1 prints only `a`.
- **There is no mandatory `break`** in the statement, but `break`/`continue` are
  valid (and necessary in nested loops).
- **There is no labeled break/continue** (`L: for …` → `PARSE041`, *probe*).

---

## 7. Annotations

`ebnf
annotation-list = { annotation } ;
annotation      = "@" , ( identifier | qualified-name ) ,
                  [ "(" , annotation-value , { "," , annotation-value } , ")" ] ;
annotation-value = ( identifier , "=" )? ,
                   ( literal | array-literal | type-ref , ".class" | enum-ref ) ;
array-literal   = "{" , [ literal , { "," , literal } ] , "}" ;
`

Annotations are **interop metadata** (emitted in the JVM bytecode as
`RuntimeVisibleAnnotations`); values must be compile-time constants
(`ANNOT001` if not). They are not macros (there is no macro in Kof — SG-003).

---

## 8. Special declarations

`ebnf
test-declaration = "test" , string-literal , block ;                (* `Parser.parseTestDeclaration` *)
application-declaration = "application" , "{" ,
                          [ "onStart" , block ] , [ "onShutdown" , block ] , "}" ;  (* `Parser.parseApplicationDeclaration` *)
`

`test` and `application` are **contextual identifiers** (recognized by
`peek().value()` in Parser.parse (dispatch)), not keywords. They are desugared before
analysis (item 1).

---

## 9. Produced AST

The AST is a set of **records** in a `sealed interface` hierarchy
(`AstNodes.java`). **There is no typed AST**: nodes store types as `String`;
resolved types live in the analyzer's side `IdentityHashMap`s (see
[type-system.md](type-system.md) §6).

`text
AstNode (sealed) { SourcePosition position() }
├── AnnotationNode, AnnotationPair, CompilationUnitNode
├── FunctionDeclarationNode, TestDeclarationNode, ApplicationDeclarationNode
├── TypeDeclarationNode (sealed)
│   ├── ClassDeclarationNode, EnumDeclarationNode, InterfaceDeclarationNode
│   ├── RecordDeclarationNode, RecordComponentNode
│   └── EntityDeclarationNode, EntityFieldNode
├── MemberNode (sealed): FieldDeclarationNode, MethodDeclarationNode,
│                         ConstructorDeclarationNode, FormalParameterNode
├── ExpressionNode (sealed)
│   ├── IdentifierExpr, PatternExpr, LiteralExpr
│   ├── BinaryExpr, UnaryExpr, AssignmentExpr
│   ├── MethodCallExpr, NewExpr, NewArrayExpr, ArrayAccessExpr, FieldAccessExpr
│   ├── IfExpr, SwitchExpr, SwitchExprCase, LambdaExpr, QueryDslExpr
└── StatementNode (sealed)
    ├── ExpressionStmt, ReturnStmt, BlockStmt, IfStmt, WhileStmt, DoWhileStmt
    ├── ForStmt, ForInStmt, VarDeclStmt, ThrowStmt, SpawnStmt, AssertStmt
    ├── BreakStmt, ContinueStmt, SwitchStmt, SwitchCase, TryStmt, CatchClause
`

`SourcePosition = record(file, line, column, offset, length)`
(`SourcePosition.java`). Literals carry a `LiteralKind` (`ConcreteLiteralKind`:
`INT LONG FLOAT DOUBLE STRING CHAR BOOLEAN NULL`) and value as `String`.

**Total: 50 AST nodes** (53 records in `AstNodes.java`; 3 do not implement
`AstNode` and are value helpers: `AnnotationPair`, `AnnotationClassRef`,
`AnnotationEnumRef`). The rest — including `SwitchExprCase`, `SwitchCase`,
`CatchClause`, `RecordComponentNode`, `EntityFieldNode`, `FormalParameterNode`
— implement `AstNode` (directly or via `ExpressionNode`/`StatementNode`/
`MemberNode`/`TypeDeclarationNode`).
