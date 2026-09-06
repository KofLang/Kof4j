# Statements

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Parser.java:863-1201`, `StatementLowerer.java`

Um statement executa um efeito e **não produz valor**. Semicolons são
**opcionais** em toda posição de fim de statement (ver
[lexical-structure.md](lexical-structure.md) §6).

---

## 1. Bloco

```ebnf
block = "{" , { statement } , "}"
```

Introduz um novo escopo (`StatementLowerer`/`SemanticAnalyzer` fazem
`enterScope`). Declarações dentro do bloco não vazam para fora.

---

## 2. Declaração de variável: `var` / `val` / tipo explícito

```ebnf
var-decl = ( "var" | "val" | type-ref ) , identifier , [ ":" , type-ref ] , [ "=" , expression ]
```

Quatro formas válidas:

```kof
var x = 10              // inferido, mutável
val y = 20              // inferido, "imutável" (ver abaixo)
String nome = "Mel"     // tipo explícito (type-first)
var idade: Int? = null  // tipo anotado (anotado)
String? nome2 = null    // type-first nullable
```

- **`var` sem inicializador** → tipo `UnknownType` (`:625`).
- **Tipo explícito ≠ tipo do inicializador** → `SEM021`.
- **Redeclarar no mesmo escopo** → `SEM024`.
- **`val` NÃO impede reatribuição**: `val x = 1; x = 2` **compila e roda**,
  imprimindo `2` (*probe*, confirmado isoladamente). A imutabilidade de `val`
  é **não-garantida** pelo compilador — é convenção de estilo, não regra de
  linguagem (SG-010). `val` em campo de classe → `PARSE016` (não é aceito como
  modificador de campo; use `final`).

---

## 3. `return`

```ebnf
return-stmt = "return" , [ expression ]
```

- `return;` / `return` (bare) em função `void` → ok.
- `return` com valor incompatível com o retorno declarado → `SEM010`.
- **`return` vazio em função não-void** → emite o **valor default do tipo**
  (`0`, `false`, `null`, `'\0'`): `Int f() { return }` retorna `0` (*probe*).
  **Implementation-defined** (a linguagem não exige que isso compile).
- Função não-void sem `return` no fim → comportamento **Unspecified** (o
  lowering injeta `defaultValueOp`).

---

## 4. `if` / `else` (statement)

```ebnf
if-stmt = "if" , "(" , expression , ")" , statement , [ "else" , statement ]
```

- A condição deve ser `bool` (ou primitivo inteiro — tratado como não-zero).
- O `else` é **opcional** na forma statement (só a forma expressão exige).
- **Narrowing de nullability**: `if (x != null) { … }` estreita `x` para `T`
  **apenas no then-branch** (`SemanticAnalyzer.java:666-684`). Ver
  [type-system.md](type-system.md) §5.
- Cada ramo é um statement (bloco ou statement único): `if (true) println("y")`
  funciona sem chaves (*probe*).

---

## 5. Loops

### 5.1 `while`

```ebnf
while-stmt = "while" , "(" , expression , ")" , statement
```

Condição avaliada **antes** de cada iteração.

### 5.2 `do … while`

```ebnf
do-while = "do" , statement , "while" , "(" , expression , ")"
```

Corpo executa **pelo menos uma vez**.

### 5.3 `for` clássico

```ebnf
for-stmt = "for" , "(" , [ init ] , ";" , [ cond ] , ";" , [ update ] , ")" , statement
init     = var-decl | expr-stmt
```

`for (var i = 0; i < 3; i++) { … }` (*probe*: imprime 0,1,2). As três partes
são opcionais.

### 5.4 `for-in`

```ebnf
for-in = "for" , "(" , ( "var" | "val" ) , identifier , "in" , expression , ")" , statement
```

- Itera sobre `List<T>` (índice interno `#coll`/`#idx`) ou array
  (`StatementLowerer.java:259-310`). **Sem iterator customizado.**
- **`in` é palavra contextual** (não keyword) — só válida aqui.
- O tipo da variável é `typeArguments.get(0)` da List ou o componente do array.
- **`for (var c in "ab")` NÃO itera sobre string** — o receiver `string` não é
  coleção; comportamento **Unspecified** (o probe JVM deu erro de runtime, não
  iteração). Use `s.charAt(i)` num loop numérico.

### 5.5 `break` / `continue`

- Encerram/pulam a iteração do **loop mais interno** (ou `switch`).
- **Não há labeled break/continue** (`L: for … break L` → `PARSE041`, *probe*).
- Implementados por pilhas de labels (`breakLabels`/`continueLabels`).

---

## 6. `switch` (statement)

```ebnf
switch-stmt = "switch" , "(" , expression , ")" , "{" , { case-stmt } , [ default-stmt ] , "}"
case-stmt   = "case" , ( pattern | expression ) , ":" , { statement }
```

- Cases usam `:` (a forma expressão usa `->` — ver
  [expressions.md](expressions.md) §12).
- **Sem fallthrough**: cada case termina com jump para o fim do switch
  (`SwitchStmtLowerer.java:174`). *probe*: valor 1 com `case 1: println("a")
  case 2: println("b")` imprime só `a`.
- `break` dentro do case é aceito (e redundante).
- Suporta **pattern matching** (`case String s:`) e **destructuring**
  (`case Point(var x, var y):`).
- **Enum**: switch sobre enum sem `default` exige cobertura de todas as
  constantes → senão `SEM031`.

---

## 7. `throw`

```ebnf
throw-stmt = "throw" , expression
```

- **A expressão deve ser `string`** — exceções em Kof são Strings.
  `throw 5` → `SEM026` (*probe*: "throw exige uma String").
- No **JVM**, `throw "msg"` é baixado para `new RuntimeException(msg)` +
  `athrow` (`StatementLowerer.java:311-327`). Nos outros targets é
  `KofThrow()` direto (a string é o valor lançado). **Target-specific** na
  representação, **Stable** na semântica (lança uma exceção capturável por
  `catch (String e)`).

---

## 8. `try` / `catch` / `finally`

```ebnf
try-stmt = "try" , block , { catch-clause } , [ "finally" , block ]
catch-clause = "catch" , "(" , type-ref , identifier , ")" , block
```

- `catch (String e)` captura exceções-Kof (strings). O tipo do catch é
  resolvido como `String` no JVM (porque `throw` virou `RuntimeException`).
- `catch (Int e)` **compila** (*probe*) mas o comportamento de captura é
  **Unspecified** (a exceção lançada é sempre String/RuntimeException).
- `finally` executa sempre (inclusive em `return`/`throw` do try).
- Implementado por marcadores de região na IR (`KofTryStart`/`KofCatchStart`),
  não por exception table separada.

---

## 9. `assert`

```ebnf
assert-stmt = "assert" , "(" , expression , [ "," , string-literal ] , ")"
```

- Se a condição é falsa: lança `"assertion failed"` (ou a mensagem dada).
- A mensagem deve ser **literal de string** (não expressão) — `:901`.
- Usado pelo harness de `kof test` (exit code ≠ 0).

---

## 10. `spawn` (statement)

```ebnf
spawn-stmt = "spawn" , expression
```

- Executa a expressão (chamada ou bloco) como **tarefa concorrente**.
- Fire-and-forget: o programa **aguarda as tarefas spawned antes de sair**
  (join implícito no `main`).
- `spawn { … }` (bloco) e `spawn f()` (chamada) são válidos.
- Ver [../concurrency.md](../concurrency.md).

---

## 11. Expression statement

```ebnf
expr-stmt = expression
```

Qualquer expressão usada por efeito (chamada, atribuição, incremento).
`println(x)` é um expression statement (chamada de função).

---

## 12. Statement vazio

`;` isolado → `ExpressionStmt(null)` (no-op).
