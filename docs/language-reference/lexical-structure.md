# Estrutura Léxica

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Lexer.java` (477 linhas), `TokenType.java` (134 linhas)

O lexer do Kof é **hand-written, single-pass, com lookahead de até 3
caracteres** (`peek`/`peekNext`/`peekNextNext`, `Lexer.java:134-144`). Não é
gerado por ferramenta nem baseado em regex. Produz uma lista plana de `Token`
(`Token.java`: `type, value, file, line, column, offset, length`) e reporta
erros com código `LEX00x`.

> **Nota de nível:** este documento descreve a *gramática léxica da linguagem*
> (quais sequências de caracteres formam tokens). O fato de o lexer ser
> hand-written é detalhe de implementação — ver
> [../compiler-architecture.md](../compiler-architecture.md).

---

## 1. Caracteres de origem

- O arquivo é lido como texto **UTF-8**. Um **BOM** inicial (`EF BB BF`) é
  ignorado (`Lexer.java:95-97`, OBS-008).
- **Identificadores** começam com `Character.isLetter(c)` ou `_` ou `$`
  (`Lexer.java:119`) e continuam com `isLetterOrDigit`, `_` ou `$`
  (`Lexer.java:358-360`). Não há escape de identificador, nem restrição a
  ASCII: qualquer caractere Unicode que `isLetter` aceite é válido.
- **Palavras reservadas** são reconhecidas por tabela exata
  (`Lexer.java:10-75`); a tabela é **sensível a maiúsculas** (`Class` não é
  keyword; `class` é).

### 1.1 Keywords (lista exaustiva — `Lexer.java:13-74`)

`text
class  interface  record  enum  entity  generated  unique
extends  implements  sealed  permits
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

**Palavras que NÃO são keywords** (são `IDENTIFIER`): `let`, `in`, `type`,
`trait`, `macro`, `where`, `query`, `test`, `application`, `onStart`,
`onShutdown`, `desc`, `asc`. Têm significado contextual no parser (ver
[grammar.md](grammar.md)) ou nenhum.

**Palavras RESERVADAS** (tokens próprios, `IDENTIFIER` **nunca**): `fun`,
`fn`, `func` (SG-001, 06/09) — mesmas da `sealed`/`permits` (tokens dedicados
que o parser não aceita como identificador em **nenhuma** posição).

> **Divergência documentada (SG-002):** `sealed` e `permits` são keywords do
> lexer mas **não são aceitas em lugar nenhum do parser** — `sealed class X {}`
> falha com `PARSE007`. São tokens mortos. Ver
> [../specification-gaps.md](../specification-gaps.md).

> **SG-001 RESOLVIDO (06/09):** `fun`/`fn`/`func` são **palavras reservadas**
> (tokens `FUN`/`FN`/`FUNC` no lexer) — **não existem** no Kof, nem como
> keyword de declaração nem como identificador em nenhuma posição (nome de
> função, variável, parâmetro, campo). Em posição de declaração o parser dá
> `PARSE085`; em outra posição, o `expectId` de cada parser já falha com
> diagnóstico (`PARSE037` variável, `PARSE023` parâmetro, …). Alinhado ao
> corpus (regra 4: bug = alinhar ao previsto). KofScript (`.ks`) **não** é
> exceção — é Kof puro executado direto; `fn`/`fun`/`func` lá também dão
> `PARSE085` (não há tradução de dialeto).

### 1.2 Literais de palavra-chave

`true`/`false` → `BOOLEAN_LITERAL`; `null` → `NULL_LITERAL`
(`Lexer.java:72-74`). Os nomes de tipos primitivos (`int`, `bool`, …) são
keywords próprias (`*_TYPE`), não identificadores — mas **podem** aparecer
como nome de campo/método após `.` (ExpressionParser.parsePostfix, `config.int`).

---

## 2. Comentários

| Forma | Regra | Evidência |
|---|---|---|
| Linha | `//` até o fim da linha | `Lexer.java:109-110, 150-154` |
| Bloco | `/*` … `*/`, **não aninhável**, pode cruzar linhas | `Lexer.java:111-112, 156-172` |

Bloco não terminado → `LEX001`. Comentários **não** são preservados na AST
(não há doc-comment como metadado).

---

## 3. Literais numéricos (`Lexer.java:265-331`)

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

Regras observáveis:

- **Sem separador de dígitos.** `1_000` é lido como `1` seguido do
  identificador `_000` → `SEM011` (*probe*).
- **Sem octal.** `0777` vale **777** decimal (o `0` inicial não é prefixo de
  base) (*probe*).
- **Ponto decimal exige dígitos dos dois lados.** `.5` → `PARSE041`;
  `1.` → `PARSE039` (*probe*).
- **Hex é sempre `INT_LITERAL`** (`Lexer.java:283`); `0xFF` → 255.
- **Sem sufixo unsigned** (`u`, `UL`): `10u` é `10` + identificador `u`.
- Inteiro sem sufixo que não cabe em `int` vira `LONG_LITERAL`
  (`Lexer.java:325-328`). Long fora do range → `PARSE084` (bug 25).
- `1.5f` é `FLOAT_LITERAL`; `1.5` é `DOUBLE_LITERAL`; `1.5d` é `DOUBLE_LITERAL`.

---

## 4. Strings e caracteres

### 4.1 String (`Lexer.java:174-201`)

`ebnf
string-literal = '"' { string-char | escape-sequence } '"' ;
`

- **Escapes suportados** (`Lexer.java:233-243`): `\n \t \r \\ \' \" \0`
  e `\uXXXX` (4 hex dígitos obrigatórios; `LEX006`/`LEX007` se inválido).
- **Escape desconhecido colapsa para o próprio caractere**: `\q` → `q`
  (`Lexer.java:242`, `default -> c`). Não é erro.
- **Multilinha literal é permitida**: uma quebra de linha física dentro das
  aspas faz parte do valor e incrementa a contagem de linha
  (`Lexer.java:187-190`) (*probe*: `"a\nb"` com newline real imprime duas
  linhas).
- **Não existe** string com aspas triplas (`"""…"""` → `PARSE043`), **não
  existe** interpolação (`"x${n}"` imprime o texto literal `x${n}` — *probe*),
  **não existe** prefixo de raw string (`r"…"` = identificador `r` + string).
- String não terminada → `LEX002`.

### 4.2 Char (`Lexer.java:203-227`)

`ebnf
char-literal = "'" ( char | escape-sequence ) "'" ;
`

Um único caractere (ou escape). Vazio → `LEX003`; não terminado → `LEX004`.
O valor é armazenado como `String` de 1 caractere no token.

---

## 5. Operadores e delimitadores (`Lexer.java:367-476`)

O lexer usa **maximal munch** com lookahead de 1–3 caracteres.

### 5.1 Tabela completa de tokens de operador

| Token | Texto | Token | Texto |
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

### 5.2 Delimitadores

`( ) { } [ ] ; , . :` → `LPAREN RPAREN LBRACE RBRACE LBRACKET RBRACKET
SEMICOLON COMMA DOT COLON`. Além disso `::` (`COLON_COLON`), `?` (`QUESTION`),
`@` (`AT`), `_` (`UNDERSCORE`), `...` (`ELLIPSIS`), `=>` (`DOUBLE_ARROW`),
`|>` (`PIPE_LINE`).

### 5.3 Tokens léxicos que a sintaxe não usa (SG-002)

`TILDE`, `COLON_COLON`, `ELLIPSIS`, `DOUBLE_ARROW`, `PIPE_LINE`, `UNDERSCORE`
são produzidos pelo lexer mas **não aparecem em nenhuma produção do parser**
(grep: 0 ocorrências em `Parser.java` além do `QUESTION` usado em tipos
nullable). Consequências observáveis:

- `~5` → `PARSE041` (*probe*) — **não existe** complemento bit a bit.
- `a => b` → `PARSE041` — só `->` existe.
- `x ?? y`, `x ?: y` → `PARSE041` (*probe*) — **não existe** null-coalescing
  nem elvis.
- `1..3` → `PARSE039` (*probe*) — **não existe** operador de range.
- `1 in s` → `PARSE029` (*probe*) — **não existe** operador `in` em expressão
  (`in` só é palavra contextual dentro de `for (var x in coll)`).
- `_` como nome de variável: `_` é `UNDERSCORE` fora de identificador, mas
  `_x` é `IDENTIFIER` (`Lexer.java:119`).

Caractere inesperado → `LEX005`.

---

## 6. Semicolons e quebras de linha

**O ponto-e-vírgulo é opcional em toda posição de fim de statement.** O parser
consome `;` apenas *se presente* (`expectSemicolon`, ParseContext.expectSemicolon).
Quebras de linha **não** são tokens e **não** têm significado sintático
(inserção automática de semicolon não existe). Consequência: `var a = 1 var b =
2` na mesma linha é parseado como duas declarações.

---

## 7. Tabela de erros léxicos

| Código | Mensagem | Causa | Evidência |
|---|---|---|---|
| `LEX001` | Unterminated block comment | `/*` sem `*/` | `Lexer.java:171` |
| `LEX002` | Unterminated string literal | `"` sem fechamento | `Lexer.java:196` |
| `LEX003` | Empty character literal | `''` | `Lexer.java:209` |
| `LEX004` | Unterminated character literal | `'a` | `Lexer.java:224` |
| `LEX005` | Unexpected character | caractere sem produção | `Lexer.java:470` |
| `LEX006` | Incomplete unicode escape | `\u` com <4 dígitos | `Lexer.java:248` |
| `LEX007` | Invalid unicode escape | hex inválido em `\uXXXX` | `Lexer.java:256` |
