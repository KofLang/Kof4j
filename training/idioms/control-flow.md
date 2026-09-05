# Idioms — Control Flow

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.2.6-beta

## What it is

Controle de fluxo sem cerimônia: `if`/`else`, `while`, `do-while`, `for`,
`for-in`, `switch`, `break`/`continue` e **if como expressão**.

## if / else

```kof
if (x > 5) {
    println("maior")
} else {
    println("menor")
}
```

## if como expressão

```kof
var status = if (ativo) "online" else "offline"
```

O if-expr produz um valor; os dois branches devem produzir valores compatíveis.

## BAD — if-expr ignorado

```kof
var status = ""
if (ativo) {
    status = "online"
} else {
    status = "offline"
}
```

## GOOD

```kof
var status = if (ativo) "online" else "offline"
```

## WHY

Declarar e depois atribuir em branches é mutação desnecessária.
A expressão-if expressa a intenção e elimina o estado intermediário.

## Loops

```kof
var i = 0
while (i < 5) {
    println(i)
    i = i + 1
}
```

```kof
for (var j = 0; j < 3; j = j + 1) {
    println(j)
}
```

```kof
do {
    println("pelo menos uma vez")
} while (falso())
```

## for-in (coleções e arrays)

```kof
var items = listOf("a", "b", "c")
for (var item in items) {
    println(item)
}

var nums = new Int[3]
nums[0] = 5
for (var n in nums) {
    println(n)
}
```

## switch (0.2.6-beta: pattern matching)

```kof
switch (x) {
    case 1:
        println("um")
        break
    case 2:
        println("dois")
        break
    default:
        println("outro")
}

// pattern matching com type + record destructuring
switch (obj) {
    case String s:
        println(s)
        break
    case Point(var x, var y):
        println(x + "," + y)
        break
    default:
        println("outro")
}
```

## When not to use

- Substituir um `for-in` por `for` com índice manual quando a ordem não importa.
- `switch` para dois casos — `if/else` é mais direto.

## switch: `break` é opcional (sem fallthrough — verificado 02/09)

Cada `case` **termina sozinho**: o compilador salta para o fim do `switch` ao
concluir o corpo — não há fallthrough (nem o bug clássico de C/Java de
esquecer o `break`). O `break` é **aceito mas não obrigatório**; escrevê-lo é
opcional (alguns preferem explícito por clareza).

```kof
switch (x) {
    case 1:
        println("um")      // sem break — ok, não cai no próximo caso
        break              // também ok (explícito)
    case 2:
        println("dois")
    default:
        println("outro")
}
```

> **Nota (02/09):** documentações anteriores afirmavam que `break` era
> obrigatório — verificado no compilador que é **opcional** (auto-termina).
> `break`/`continue` continuam obrigatórios em loops (para sair/pular).

## switch como expressão (0.2.6-beta: SYN001 — `case ... ->`)

Quando o `switch` **produz um valor**, use a forma expressão (`->`), não a
statement (`:`). Cada caso é uma única expressão; não há `break`, não há
escopo de bloco, e o `default` é obrigatório (ou exaustividade de enum —
senão `SEM032`). É o mesmo dispositivo do `if`-expressão, elevado a N casos.

```kof
// ❌ BAD — switch statement + temporário + branches atribuindo
var label = ""
switch (op) {
    case "GET":  label = "buscar"
    case "POST": label = "criar"
    default:     label = "desconhecido"
}

// ✅ GOOD — switch expressão: o valor É o switch
var label = switch (op) {
    case "GET"  -> "buscar"
    case "POST" -> "criar"
    default    -> "desconhecido"
}

// pattern matching + destructuring como expressão
var desc = switch (obj) {
    case String s            -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default                  -> "outro"
}

// aninhado / em return — funciona em qualquer posição de expressão
String nome(Int n) = switch (n) {
    case 0 -> "zero"
    case 1 -> "um"
    default -> "muitos"
}
```

**Quando usar qual:** o `switch`-expressão (`->`) quando o resultado é um
valor; o `switch`-statement (`:`) quando cada caso executa efeitos colaterais
(println, chamadas). Os dois coexistem — a escolha é por token (`->` vs `:`).

> **Verificado 03/09 (SYN001):** JVM, Native (x86_64/riscv64/aarch64) e JS.
> No JS é renderizado como ternários aninhados; em String/enum a igualdade é
> por conteúdo (nunca referência).

## Anti-patterns relacionados

- `premature-optimization.md` — loops manuais sem necessidade