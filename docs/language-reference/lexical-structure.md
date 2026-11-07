[English](lexical-structure.md) | [Português](lexical-structure.pt_BR.md)

# Lexical Structure

**Status:** Stable (except where labeled) · **Evidence:** `Lexer.java` (477 lines), `TokenType.java` (134 lines)

The Kof lexer is **hand-written, single-pass, with lookahead of up to 3
characters** (`peek`/`peekNext`/`peekNextNext`, `Lexer.java:134-144`). It is
not tool-generated nor regex-based. It produces a flat list of `Token`
(`Token.java`: `type, value, file, line, column, offset, length`) and reports
errors with code `LEX00x`.

> **Level note:** this document describes the *lexical grammar of the language*
> (which character sequences form tokens). The fact that the lexer is
> hand-written is an implementation detail — see
> [../compiler-architecture.md](../architecture/compiler-architecture.md).

---

## 1. Source characters

- The file is read as **UTF-8** text. An initial **BOM** (`EF BB BF`) is
  ignored (`Lexer.java:95-97`, OBS-008).
- **Identifiers** start with `Character.isLetter(c)` or `_` or `$`
  (`Lexer.java:119`) and continue with `isLetterOrDigit`, `_` or `$`
  (`Lexer.java:358-360`). There is no identifier escape, nor an ASCII
  restriction: any Unicode character that `isLetter` accepts is valid.
- **Reserved words** are recognized by exact table
  (`Lexer.java:10-75`); the table is **case-sensitive** (`Class` is not a
  keyword; `class` is).

### 1.1 Keywords (exhaustive list — `Lexer.java:13-74`)

`text
class  interface  record  enum  entity
extends  implements
package  import
public  private  protected  static  final  abstract
transient  volatile  synchronized  native  default  override
void  new  this  super  return  throw
if  else  for  while  do  switch  case  break  continue
try  catch  finally
spawn  await  assert  instanceof
var  val  as
bool  byte  short  int  long  float  double  char  string
true  false  null
`

**Words that are NOT keywords** (they are `IDENTIFIER`): `let`, `in`, `type`,
`trait`, `macro`, `where`, `query`, `test`, `application`, `onStart`,
`onShutdown`, `desc`, `asc`, `sealed`, `value`, `unique`, `generated`,
`audited`. They have contextual meaning in the parser (see
[grammar.md](grammar.md)) or none.

> **#1118 + #1068 (07/11):** `unique`, `generated` and `audited` are
> **contextual**, exactly like `sealed`/`value`. They are modifiers only in
> their own position — `unique`/`generated` after an entity field's type,
> `audited` before `entity` — and ordinary identifiers everywhere else, so
> `var unique = listOf<String>()` and `var audited = 1` are plain locals.
> Before the fix the lexer reserved them and the declaration died with
> `PARSE037` plus a `PARSE041` cascade on every later use. Locked by
> `ContextualModifierNamesE2ETest` (3/3, JVM + Script).

> **§263 FIXED (17/09, compiler/nat lane):** the annotated form
> `name: Type = ...` is only valid after `var`/`val`. On the type-first path
> (`Type name = ...`) a `:` right after the name means the consumed prefix was
> never a real type. `parseVarDecl` now emits **`PARSE095`** pointing at the
> discarded prefix, instead of silently overwriting it — **only when the prefix
> differs from the annotation** (prefix == annotation discards nothing; that is
> the fix direction declared by the cataloguing lane). Before the fix,
> `let x: Int = 5` / `Klaxon x: Int = 5` / `Banana q: String = "z"` compiled
> and printed the value; without the annotation the unknown prefix already
> failed honestly as SEM011 — the hole was only with `:`. Parser-level fix:
> all 4 targets inherit the same diagnostic (rule 5). Locked by
> `ParserGarbageTypePrefixE2ETest` (9/9, JVM + JS).

**RESERVED words** (their own tokens, `IDENTIFIER` **never**): `fun`,
`fn`, `func` (SG-001, 06/09).

> **SG-002 APPLIED (12/09) + X5.1 UPDATE (21/09):** `sealed` and `permits` were
> **keywords** of the lexer (dead tokens, never accepted by the parser — `sealed
> class X {}` used to fail with `PARSE007`); with SG-002 they became **plain
> `IDENTIFIER`s**. `permits` stays a plain identifier (Kof has **no** `permits`
> clause). `sealed`, however, is now a **contextual modifier** before
> `class`/`record`/`interface` (`D-X5-SURFACE`, X5.1): `sealed class Shape` is
> the closed-subtype form, while `sealed` outside that position remains a valid
> identifier. Locked by `SealedTypeE2ETest` + `CompilerDriverTest`.
> See [specification-gaps.md](../bugs-and-gaps/specification-gaps.md).

> **SG-001 RESOLVED (06/09):** `fun`/`fn`/`func` are **reserved words**
> (`FUN`/`FN`/`FUNC` tokens in the lexer) — they **do not exist** in Kof, neither
> as a declaration keyword nor as an identifier in any position (function
> name, variable, parameter, field). In declaration position the parser gives
> `PARSE085`; in any name position (function, variable, parameter, method,
> field, class, record, enum) `ParseContext.expectId` emits the same `PARSE085`
> (measured 17/09, #330 — it used to fall through to the generic `PARSE037`
> variable / `PARSE023` parameter). Aligned with the
> corpus (rule 4: bug = align with what is expected). KofScript (`.ks`) is **not**
> an exception — it is pure Kof executed directly; `fn`/`fun`/`func` there also give
> `PARSE085` (there is no dialect translation).

### 1.2 Keyword literals

`true`/`false` → `BOOLEAN_LITERAL`; `null` → `NULL_LITERAL`
(`Lexer.java:72-74`). The primitive type names (`int`, `bool`, …) are their own
keywords (`*_TYPE`), not identifiers — but they **may** appear
as a field/method name after `.` (ExpressionParser.parsePostfix, `config.int`).

---

## 2. Comments

| Form | Rule | Evidence |
|---|---|---|
| Line | `//` to the end of the line | `Lexer.java:109-110, 150-154` |
| Block | `/*` … `*/`, **not nestable**, may cross lines | `Lexer.java:111-112, 156-172` |

Unterminated block → `LEX001`. Comments are **not** preserved in the AST
(there is no doc-comment as metadata).

---

## 3. Numeric literals (`Lexer.java:265-331`)

`ebnf
hexadecimal-literal   = "0" ( "x" | "X" ) hex-digit { hex-digit } ;
decimal-literal       = digit { digit } ;
float-literal         = decimal-literal , "." , digit { digit }
                        [ exponent ] [ float-suffix ] ;
double-literal        = ( decimal-literal , "." , digit { digit } [ exponent ]
                        | decimal-literal , exponent ) [ double-suffix ] ;
long-literal          = decimal-literal , long-suffix ;
exponent              = ( "e" | "E" ) [ "+" | "-" ] digit { digit } ;
float-suffix          = "f" | "F" ;
double-suffix         = "d" | "D" ;
long-suffix           = "l" | "L" ;
`

Observable rules:

- **No digit separator.** `1_000` is read as `1` followed by the
  identifier `_000` → `SEM011` (*probe*).
- **No binary literal.** `0b1010` is read as `0` followed by the identifier
  `b1010` → `SEM011` (there is no `0b` prefix) (*probe*).
- **No octal.** `0777` is **777** decimal (the leading `0` is not a base
  prefix) (*probe*).
- **A decimal point requires digits on both sides.** `.5` → `PARSE041`;
  `1.` → `PARSE039` (*probe*).
- **Hex is always `INT_LITERAL`** (`Lexer.java:283`); `0xFF` → 255.
- **No unsigned suffix** (`u`, `UL`): `10u` is `10` + identifier `u`.
- An integer without a suffix that does not fit in `int` becomes `LONG_LITERAL`
  (`Lexer.java:325-328`). Long out of range → `PARSE084` (bug 25).
- `1.5f` is `FLOAT_LITERAL`; `1.5` is `DOUBLE_LITERAL`; `1.5d` is `DOUBLE_LITERAL`.

---

## 4. Strings and characters

### 4.1 String (`Lexer.java:174-201`)

`ebnf
string-literal = '"' { string-char | escape-sequence } '"' ;
`

- **Supported escapes** (`Lexer.java:233-243`): `\n \t \r \\ \' \" \0`
  and `\uXXXX` (4 mandatory hex digits; `LEX006`/`LEX007` if invalid).
- **Unknown escape collapses to the character itself**: `\q` → `q`
  (`Lexer.java:242`, `default -> c`). It is not an error.
- **Literal multiline is allowed**: a physical line break inside the
  quotes is part of the value and increments the line count
  (`Lexer.java:187-190`) (*probe*: `"a\nb"` with a real newline prints two
  lines).
- **There is no** triple-quoted string (`"""…"""` → **`LEX008`**), **there is
  no** interpolation (`"x${n}"` prints the literal text `x${n}` — *probe*),
  **there is no** raw string prefix (`r"…"` = identifier `r` + string).
- Unterminated string → `LEX002`.

### 4.2 Char (`Lexer.java:203-227`)

`ebnf
char-literal = "'" ( char | escape-sequence ) "'" ;
`

A single character (or escape). Empty → `LEX003`; unterminated → `LEX004`.
The value is stored as a 1-character `String` in the token.

---

## 5. Operators and delimiters (`Lexer.java:367-476`)

The lexer uses **maximal munch** with 1–3 character lookahead.

### 5.1 Complete operator token table

| Token | Text | Token | Text |
|---|---|---|---|
| `PLUS` | `+` | `PLUS_PLUS` | `++` |
| `MINUS` | `-` | `MINUS_MINUS` | `--` |
| `STAR` | `*` | `STAR_EQUAL` | `*=` |
| `SLASH` | `/` | `SLASH_EQUAL` | `/=` |
| `PERCENT` | `%` | `PERCENT_EQUAL` | `%=` |
| `BANG` | `!` | `BANG_EQUAL` | `!=` |
| `EQUAL` | `=` | `EQUAL_EQUAL` | `==` |
| `LESS` | `<` | `LESS_EQUAL` | `<=` |
| `GREATER` | `>` | `GREATER_EQUAL` | `>=` |
| `LESS_LESS` | `<<` | `LESS_LESS_EQUAL` | `<<=` |
| `GREATER_GREATER` | `>>` | `GREATER_GREATER_EQUAL` | `>>=` |
| `GREATER_GREATER_GREATER` | `>>>` | `GREATER_GREATER_GREATER_EQUAL` | `>>>=` |
| `AMP` | `&` | `AMP_AMP` | `&&` |
| `PIPE` | `\|` | `PIPE_PIPE` | `\|\|` |
| `CARET` | `^` | `CARET_EQUAL` | `^=` |
| `AMP_EQUAL` | `&=` | `PIPE_EQUAL` | `\|=` |
| `PLUS_EQUAL` | `+=` | `MINUS_EQUAL` | `-=` |
| `ARROW` | `->` | `TILDE` | `~` |

### 5.2 Delimiters

`( ) { } [ ] ; , . :` → `LPAREN RPAREN LBRACE RBRACE LBRACKET RBRACKET
SEMICOLON COMMA DOT COLON`. In addition `::` (`COLON_COLON`), `?` (`QUESTION`),
`@` (`AT`), `_` (`UNDERSCORE`), `...` (`ELLIPSIS`), `=>` (`DOUBLE_ARROW`),
`|>` (`PIPE_LINE`).

### 5.3 Lexical tokens that the syntax does not use (SG-002)

`TILDE`, `COLON_COLON`, `ELLIPSIS`, `DOUBLE_ARROW`, `PIPE_LINE`, `UNDERSCORE`
are produced by the lexer but **do not appear in any parser production**
(grep: 0 occurrences in `Parser.java` besides the `QUESTION` used in
nullable types). Observable consequences:

- `~5` → `PARSE041` (*probe*) — there **is no** bitwise complement.
- `a => b` → `PARSE041` — only `->` exists.
- `x ?? y` → `PARSE041` (*probe*) — there **is no** `??`; the elvis `x ?: y`
  is the token `?:` (§821).
- `1..3` → `PARSE039` (*probe*) — there **is no** range operator.
- `1 in s` → `PARSE029` (*probe*) — there **is no** `in` operator in an expression
  (`in` is only a contextual word inside `for (var x in coll)`).
- `_` as a variable name: `_` is `UNDERSCORE` outside an identifier, but
  `_x` is `IDENTIFIER` (`Lexer.java:119`).

Unexpected character → `LEX005`.

---

## 6. Semicolons and line breaks

**The semicolon is optional in every statement-end position.** The parser
consumes `;` only *if present* (`expectSemicolon`, ParseContext.expectSemicolon).
Line breaks are **not** tokens and have **no** syntactic meaning for statement
termination (automatic semicolon insertion does not exist). Consequence: `var a = 1 var b =
2` on the same line is parsed as two declarations.

**Single exception — `return` (#343).** The value of a `return` only counts
when it is on the **same line** as the keyword. A line break after a bare
`return` makes it a void return (`StatementParser.parseReturn`), so
`if (x < 0) return` followed by `println(x)` on the next line parses as a
void `return` + a separate statement — not as `return println(x)`. A value
on the following line is not supported (use braces or keep the value on the
same line).

---

## 7. Lexical error table

| Code | Message | Cause | Evidence |
|---|---|---|---|
| `LEX001` | Unterminated block comment | `/*` without `*/` | `Lexer.java:171` |
| `LEX002` | Unterminated string literal | `"` without closing | `Lexer.java:196` |
| `LEX003` | Empty character literal | `''` | `Lexer.java:209` |
| `LEX004` | Unterminated character literal | `'a` | `Lexer.java:224` |
| `LEX005` | Unexpected character | character without a production | `Lexer.java:470` |
| `LEX006` | Incomplete unicode escape | `\u` with <4 digits | `Lexer.java:248` |
| `LEX007` | Invalid unicode escape | invalid hex in `\uXXXX` | `Lexer.java:256` |
| `LEX008` | No triple-quoted string | `"""…"""` (Kof has no raw/multiline literal — #364) | `Lexer.java:188` |
