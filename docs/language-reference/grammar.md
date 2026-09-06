# Gramática Formal do Kof

**Status:** Stable (a forma) · **Evidência:** `Parser.java` (1975 linhas)

## Formalismo e justificativa

O parser do Kof é **recursive descent com precedence-climbing para expressões
binárias** (`Parser.java:1298-1307`), **hand-written**, com lookahead
arbitrário sobre a lista de tokens (`check`/`checkNext`/varreduras em
`looksLike*`). **Não** é LL(1) estrito, **não** é PEG, **não** é gerado por
ferramenta (não há arquivo `.g4`/`.y`/`.ebnf`).

Por isso a gramática abaixo é apresentada em **EBNF** como *descrição
extrativa* do parser real — não como a especificação que o parser segue. Onde
o parser usa heurística de lookahead (ex.: `looksLikeLambdaParams`), a
gramática marca a construção como **ambígua resolvida por lookahead** e o
comportamento exato é **Implementation-defined**.

Convenção EBNF usada:

```text
=           definição          { x }   zero ou mais
,           concatenação       [ x ]   opcional
( x | y )   alternativa        "x"     terminal literal
(* ... *)   comentário
```

```text
Código-fonte ──Lexer──▶ Tokens ──Parser──▶ AST ──Desugar──▶ AST ──Analyze──▶ AST anotada (maps laterais)
```

A **gramática léxica** está em [lexical-structure.md](lexical-structure.md).
Aqui está a **gramática sintática** e a **AST** resultante.

---

## 1. Unidade de compilação

```ebnf
compilation-unit = [ package-declaration ] , { import-declaration } ,
                   { top-level-declaration } ;

package-declaration = "package" , qualified-identifier , [ ";" ] ;        (* Parser.java:419 *)
import-declaration  = "import" , ( "*" | import-path ) , [ ";" ] ;       (* Parser.java:441 *)
import-path         = identifier , { "." , identifier } , [ ".*" ] ;

top-level-declaration =
      annotation-list , type-declaration
    | annotation-list , function-declaration
    | test-declaration
    | application-declaration
    | type-declaration
    | function-declaration ;
```

**Somente** declarações de tipo e de função podem aparecer no topo. `val`/`var`
no topo → `PARSE007` (*probe*). Não há `let` (SG-001).

> **Resolução da pergunta "o parser produz diretamente a AST?":** **não.** O
> parser produz uma AST *crua*; o driver aplica **desugaring** sobre ela antes
> da análise (`CompilerDriver.java`, chamadas a `CompilerDesugar.desugarTests`/`desugarApplication`): `desugarTests` (blocos `test` →
> harness) e `desugarApplication` (blocos `application { onStart/onShutdown }`
> → funções sintetizadas que envolvem o `main`). Ver
> [../compiler-architecture.md](../compiler-architecture.md).

---

## 2. Declarações de tipo

```ebnf
type-declaration =
      class-declaration | interface-declaration | record-declaration
    | enum-declaration  | entity-declaration ;

modifiers = { "public" | "private" | "protected" | "static" | "final"
            | "abstract" | "transient" | "volatile" | "synchronized"
            | "native" | "default" | "override" } ;                 (* Parser.java:491-499 *)

class-declaration = modifiers , "class" , identifier , [ type-parameters ] ,
                    [ "extends" , type-ref ] , [ implements-clause ] ,
                    ( class-body | record-header , class-body ) ;   (* Parser.java:526 *)
```

> `class X(...)` **com parênteses** é parseado como **record** (corpo via
> `parseRecordBody`, `Parser.java:538-539`) — não como classe com primary
> constructor. É o comportamento documentado em `AGENTS.md`.

```ebnf
class-body = "{" , { class-member } , "}" ;
class-member = annotation-list , modifiers , ( constructor-declaration
             | method-declaration | field-declaration | type-declaration ) ;

interface-declaration = modifiers , "interface" , identifier ,
                        [ "extends" , type-ref , { "," , type-ref } ] ,
                        "{" , { class-member } , "}" ;              (* Parser.java:557 *)
```

**Interfaces não aceitam type-parameters** (`interface F<T>` → `PARSE007`,
*probe*).

```ebnf
record-declaration = modifiers , "record" , identifier , [ type-parameters ] ,
                     [ "extends" , type-ref ] , [ implements-clause ] ,
                     record-header , [ record-body ] ;              (* Parser.java:619 *)
record-header      = "(" , [ record-component , { "," , record-component } ] , ")" ;
record-component   = annotation-list , modifiers , type-ref , identifier ,
                     [ "=" , expression ] ;                         (* Parser.java:673 *)
record-body        = "{" , { class-member } , "}" ;

enum-declaration = modifiers , "enum" , identifier ,
                   "{" , [ identifier , { "," , identifier } ] , "}" ;  (* Parser.java:502 *)
```

**Enums são apenas constantes** — sem corpo, sem métodos, sem construtores, sem
campos (`enum E { A String f(){…} }` → `PARSE032`, *probe*). Em runtime o
valor de um enum **é** o nome (`String`) — ver [classes.md](classes.md).

```ebnf
entity-declaration = modifiers , "entity" , identifier ,
                     "{" , { entity-field } , "}" ;                 (* Parser.java:591 *)
entity-field       = identifier , ":" , type-ref , { "generated" | "unique" } ;
```

---

## 3. Funções e métodos

```ebnf
function-declaration = annotation-list , modifiers ,
                       [ type-ref ] , identifier , [ type-parameters ] ,
                       "(" , [ parameter-list ] , ")" ,
                       [ ":" , type-ref ] ,                          (* retorno sufixado *)
                       function-body ;                               (* Parser.java *)

function-body = block | "=" , expression , [ ";" ] | ";" ;          (* corpo: bloco, expressão, ou abstrato *)

method-declaration = [ type-ref ] , identifier , "(" , [ parameter-list ] , ")" ,
                     [ ":" , type-ref ] , [ throws-clause ] , function-body ;
constructor-declaration = [ "constructor" ] , "(" , [ parameter-list ] , ")" ,
                          [ throws-clause ] , block ;                (* Parser.java:781 *)

parameter-list = parameter , { "," , parameter } ;
parameter      = annotation-list , modifiers ,
                 ( type-ref , identifier | identifier , ":" , type-ref ) ,
                 [ "=" , expression ] ;                              (* Parser.java:815 *)
throws-clause  = "throw" , type-ref , { "," , type-ref } ;           (* Parser.java:840 *)
type-parameters = "<" , identifier , { "," , identifier } , ">" ;    (* Parser.java:402 *)
```

As **três formas de retorno** são válidas e equivalentes:

```kof
String a() { … }      // tipo antes do nome
b(): String { … }     // tipo depois dos parênteses (forma anotada)
c() { … }             // sem tipo → void (default)
```

**Não existe keyword de declaração de função** (SG-001 resolvido 06/09):
`fn`/`fun`/`func` como prefixo são rejeitados com `PARSE085`. Como *nome* de
função continuam identificadores válidos. Parâmetros aceitam **default values**
(`parameter = expression`), que geram overloads sintéticos por aridade no
lowering.

---

## 4. Tipos (referência sintática)

```ebnf
type-ref = "void"
         | function-type
         | primitive-type
         | qualified-name , [ generic-args ] , { "[]" } , { "?" } ;

primitive-type = "bool" | "byte" | "short" | "int" | "long"
               | "float" | "double" | "char" | "string" ;           (* Parser.java:1868 *)
qualified-name = identifier , { "." , identifier } ;
generic-args   = "<" , type-ref , { "," , type-ref } , ">" ;        (* Parser.java:1839 *)
function-type  = "(" , [ type-ref , { "," , type-ref } ] , ")" , "->" , type-ref ;  (* Parser.java:1878 *)
```

O parser captura o tipo como **string bruta** (`parseTypeRef` devolve
`String`), não como estrutura. A resolução para `Type` acontece na análise
semântica. Sufixos `[]` (array) e `?` (nullable) são **anexados à string**
(`Int[]?` é válido). Genéricos aninhados (`Map<String, List<Int>>`) são
consumidos por contagem de profundidade com split de `>>`/`>>>`
(`splitShiftRight`).

> **`?` é sufixo de tipo, não operador de expressão.** `Int?` é nullable;
> `x?y` não é sintaxe.

---

## 5. Expressões

```ebnf
expression = switch-expression | assignment ;                       (* Parser.java:1277 *)

assignment = binary , [ assign-op , assignment ] ;                  (* right-assoc, Parser.java:1284 *)
assign-op  = "=" | "+=" | "-=" | "*=" | "/=" | "%="
           | "&=" | "|=" | "^=" | "<<=" | ">>=" | ">>>=" ;

binary     = unary , { binary-op , unary } ;                        (* precedence climbing, Parser.java:1298 *)
binary-op  = "||" | "&&" | "|" | "^" | "&"
           | "==" | "!=" | "<" | "<=" | ">" | ">="
           | "instanceof" | "as"
           | "<<" | ">>" | ">>>"
           | "+" | "-" | "*" | "/" | "%" ;

unary      = ( "spawn" | "await" | "!" | "-" | "++" | "--" ) , unary
           | postfix ;                                              (* Parser.java:1334 *)

postfix    = primary , { postfix-op } ;                             (* Parser.java:1376 *)
postfix-op = "." , identifier , [ call-args | trailing-lambda ]
           | "[" , expression , "]"
           | call-args , [ trailing-lambda ]
           | "<" , generic-args , ">" , call-args , [ trailing-lambda ]   (* generic call *)
           | "++" | "--" ;

primary    = literal | "this" | "super" | identifier
           | new-expression
           | "(" , lambda-params , ")" , "->" , lambda-body         (* lambda *)
           | "(" , expression , ")"                                 (* parêntese *)
           | "if" , "(" , expression , ")" , expression , "else" , expression   (* if-expr *)
           | "{" , lambda-body , "}" ;                              (* lambda sem params *)

new-expression = "new" , type-ref , [ generic-args ] ,
                 ( "[" , expression , "]" | call-args ) ;           (* Parser.java:1669 *)

lambda-params = [ lambda-param , { "," , lambda-param } ] ;
lambda-param  = identifier , [ ":" , type-ref ] ;                   (* Parser.java:1649 *)
lambda-body   = block | expression , [ ";" ] ;                      (* Parser.java:1660 *)
```

### 5.1 Precedência e associatividade (exata — `Parser.java:1309-1321`)

Maior precedência no topo. **Todos os binários são left-associativos**
(`parseBinary(prec+1)`); atribuição é **right-associativa**.

| Prec | Operadores | Assoc |
|---|---|---|
| 8 | `*` `/` `%` | left |
| 7 | `+` `-` | left |
| 6 | `<<` `>>` `>>>` | left |
| 5 | `==` `!=` `<` `<=` `>` `>=` `instanceof` `as` | left |
| 4 | `&` | left |
| 3 | `\|` `^` | left |
| 2 | `&&` | left (short-circuit) |
| 1 | `\|\|` | left (short-circuit) |
| 0 | `=` `+=` … | **right** |

> `instanceof` e `as` têm a **mesma** precedência (5) e são left-assoc no
> parser, mas o lowering **interrompe o encadeamento** neles (bug 13,
> `ExpressionLowerer.java:174-176`): `(x as Int) + 1` não é `x as (Int + 1)`.
> O comportamento de encadeamento puro (`a as B as C`) é **Unspecified**.

### 5.2 Short-circuit

`&&` e `||` são avaliados com short-circuit por labels em **JVM e Native**;
no **target JS o short-circuit é desligado** (`ExpressionLowerer.java:147-148`)
— **Target-specific** (SG-006).

### 5.3 Operadores que NÃO existem (SG-002, verificado por probe)

`~` (bitwise NOT), `?:` (elvis), `??` (null-coalesce), `..` (range), `in`
(membership em expressão), `=>`, `::`, `|>`, `...`. Todos produzem erro de
parse. Para membership use `setOf(…).contains(x)` (idiom da linguagem).

---

## 6. Statements

```ebnf
statement = block | return-stmt | if-stmt | while-stmt | do-while-stmt
          | for-stmt | throw-stmt | spawn-stmt | assert-stmt | try-stmt
          | switch-stmt | break-stmt | continue-stmt | var-decl | expr-stmt ;

block       = "{" , { statement } , "}" ;                           (* Parser.java:853 *)
return-stmt = "return" , [ expression ] , [ ";" ] ;                 (* Parser.java:946 *)
if-stmt     = "if" , "(" , expression , ")" , statement , [ "else" , statement ] ;
while-stmt  = "while" , "(" , expression , ")" , statement ;
do-while    = "do" , statement , "while" , "(" , expression , ")" , [ ";" ] ;
for-stmt    = "for" , "(" , ( for-init | for-in ) , ")" , statement ;
for-in      = ( "var" | "val" ) , identifier , "in" , expression ;  (* "in" é contextual *)
for-init    = [ statement ] , [ expression ] , ";" , [ expression ] ;
throw-stmt  = "throw" , expression , [ ";" ] ;
spawn-stmt  = "spawn" , expression , [ ";" ] ;
assert-stmt = "assert" , "(" , expression , [ "," , string-literal ] , ")" , [ ";" ] ;
try-stmt    = "try" , block , { catch-clause } , [ "finally" , block ] ;
catch-clause = "catch" , "(" , type-ref , identifier , ")" , block ;
break-stmt  = "break" , [ ";" ] ;                                   (* sem label *)
continue-stmt = "continue" , [ ";" ] ;                              (* sem label *)
var-decl    = ( "var" | "val" | type-ref ) , identifier ,
              [ ":" , type-ref ] , [ "=" , expression ] , [ ";" ] ; (* Parser.java:1203 *)
expr-stmt   = expression , [ ";" ] ;
```

### 6.1 Switch — duas formas

```ebnf
switch-stmt = "switch" , "(" , expression , ")" , "{" , { case-stmt } ,
              [ default-stmt ] , "}" ;                              (* Parser.java:1071 *)
case-stmt   = "case" , ( pattern | expression ) , ":" , { statement } ;
default-stmt = "default" , ":" , { statement } ;

switch-expression = "switch" , "(" , expression , ")" , "{" , { case-expr } ,
                    [ default-expr ] , "}" ;                        (* Parser.java:1172 *)
case-expr   = "case" , ( pattern | expression ) , "->" , expression ;
default-expr = "default" , "->" , expression ;

pattern = type-name , identifier                          (* binding:  case String s *)
        | type-name , "(" , { ( "var" | "val" )? , identifier } , ")" ;  (* destructuring: case Point(var x, var y) *)
```

- **Statement** usa `:`; **expressão** usa `->`. A forma expressão exige
  `default` (ou enum exaustivo) — `SEM032`.
- **Não há fallthrough**: cada case do statement salta para o fim
  (`SwitchStmtLowerer.java:174`) — *probe*: `case 1: println("a") case 2:
  println("b")` com valor 1 imprime só `a`.
- **Não há `break` obrigatório** no statement, mas `break`/`continue` são
  válidos (e necessários em loops aninhados).
- **Não há labeled break/continue** (`L: for …` → `PARSE041`, *probe*).

---

## 7. Annotations

```ebnf
annotation-list = { annotation } ;
annotation      = "@" , ( identifier | qualified-name ) ,
                  [ "(" , annotation-value , { "," , annotation-value } , ")" ] ;
annotation-value = ( identifier , "=" )? ,
                   ( literal | array-literal | type-ref , ".class" | enum-ref ) ;
array-literal   = "{" , [ literal , { "," , literal } ] , "}" ;
```

Annotations são **metadados de interop** (emitidas no bytecode JVM como
`RuntimeVisibleAnnotations`); valores devem ser constantes em compile-time
(`ANNOT001` se não). Não são macros (não existe macro em Kof — SG-003).

---

## 8. Declarações especiais

```ebnf
test-declaration = "test" , string-literal , block ;                (* Parser.java:205 *)
application-declaration = "application" , "{" ,
                          [ "onStart" , block ] , [ "onShutdown" , block ] , "}" ;  (* Parser.java:219 *)
```

`test` e `application` são **identificadores contextuais** (reconhecidos por
`peek().value()` em `Parser.java:35-39`), não keywords. São desugared antes da
análise (item 1).

---

## 9. AST produzida

A AST é um conjunto de **records** numa hierarquia `sealed interface`
(`AstNodes.java`). **Não há AST tipada**: os nós guardam tipos como `String`;
os tipos resolvidos vivem em `IdentityHashMap` laterais do analisador (ver
[type-system.md](type-system.md) §6).

```text
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
```

`SourcePosition = record(file, line, column, offset, length)`
(`SourcePosition.java`). Literais carregam `LiteralKind` (`ConcreteLiteralKind`:
`INT LONG FLOAT DOUBLE STRING CHAR BOOLEAN NULL`) e valor como `String`.

**Total: 50 nós de AST** (53 records em `AstNodes.java`; 3 não implementam
`AstNode` e são auxiliares de valor: `AnnotationPair`, `AnnotationClassRef`,
`AnnotationEnumRef`). Os demais — incluindo `SwitchExprCase`, `SwitchCase`,
`CatchClause`, `RecordComponentNode`, `EntityFieldNode`, `FormalParameterNode`
— implementam `AstNode` (direta ou via `ExpressionNode`/`StatementNode`/
`MemberNode`/`TypeDeclarationNode`).
