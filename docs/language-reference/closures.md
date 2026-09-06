# Closures e Lambdas

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Parser.java:1517-1667`, `CompilerDriver.java:900-1243`, `BoxClassFactory.java`

---

## 1. Formas de lambda

```ebnf
lambda = "(" , [ lambda-param , { "," , lambda-param } ] , ")" , "->" , lambda-body
       | "{" , lambda-body , "}" ;
lambda-param = identifier , [ ":" , type-ref ] ;
lambda-body  = block | expression ;
```

Exemplos válidos:

```kof
(x: Int) -> x * 2                  // params tipados, corpo-expressão
(a: Int, b: Int) -> { return a + b }  // corpo-bloco
() -> println("oi")                // sem params
{ println("bloco") }               // bloco-lambda (0 params)
(x: Int) -> (y: Int) -> x + y      // currying (lambda retornando lambda)
```

- **Corpo-expressão** tem retorno implícito (`parseLambdaBody:1660-1667` vira
  `ReturnStmt`).
- **Corpo-bloco** exige `return` explícito para produzir valor.

---

## 2. Parâmetros e tipos

- Parâmetros **podem** ser anotados (`x: Int`).
- **Sem anotação**, o parâmetro assume `Object` (`parseLambdaParameter:1652`).
  Usá-lo em aritmética → `SEM001` com dica *"declare o tipo do parâmetro,
  ex.: `(x: Int) -> …`"* (*probe*: `l.map((x) -> x + 1)` → SEM001).
- **Não há inferência de tipo de parâmetro de lambda** a partir do contexto de
  chamada (a tabela de `map` sabe que é `Int`, mas o parser não propaga para o
  corpo). **Unspecified** como política (SG-012).

---

## 3. Function types

Uma lambda tem tipo `FunctionType(parameterTypes, returnType, className)`
(`Type.java:29`). Function types são **valores de primeira classe**:

```kof
var f: (Int) -> Int = (x: Int) -> x * 2
println(f(5))                       // chamada de valor-função
var g = listOf(1,2).map((x: Int) -> x + 1)   // passada como argumento
```

- Sintaxe do tipo: `(Int, String) -> Bool` (`parseFunctionTypeRef`).
- Chamar uma variável com `FunctionType` → `ft.returnType()` + checagem de
  args (`SemanticAnalyzer.java:1517-1522`).
- Chamar variável **sem** FunctionType → `SEM015`.
- **Não há** `fun`-type com nome, nem type alias de função.

---

## 4. Captura de variáveis (closure)

Uma lambda captura variáveis do escopo externo.

### 4.1 Captura read-only (snapshot)

```kof
main() {
    var n = 10
    var f = () -> n + 1
    println(f())        // 11 (probe)
}
```

- Capturas são **campos `private final`** da classe sintética `Lambda<N>`
  (`lambdaClass`, `CompilerDriver.java:933-939`), copiadas no call site.
- A captura é um **snapshot do valor no momento da criação da lambda**
  (comentário `:897-898`).
- `collectCaptures` (`:1072-1209`) varre o corpo; params e declarações internas
  entram em `shadowed` e **não** capturam a externa homônima.

### 4.2 Captura mutável (Box)

```kof
main() {
    var n = 0
    var inc = () -> { n = n + 1 }
    inc(); inc()
    println(n)          // 2 (probe)
}
```

- Se uma variável capturada é **atribuída dentro da lambda**, o lowering a
  converte em **box mutável**: classe sintética `Box<N>` com campo `value`
  (`BoxClassFactory.createBoxClass`). Leituras/escritas viram
  `KofLoadField/KofStoreField "value"`.
- **Consequência observável**: a mutação via box é **visível fora da lambda**
  (o `n` externo muda). Isso é **Stable** (comportamento documentado e
  testado).
- Captura de `this` (`() -> this.v`) funciona (*probe*).

---

## 5. Representação de implementação (não-normativa)

Para explicar o comportamento observável: cada lambda vira uma **classe
sintética** `Lambda<N>` (ou `LambdaTask<N>` para corpo de `spawn`) que
implementa uma **interface de função sintética** por assinatura
(`kof/Function<N>_<mangled>`). O call site faz `new Lambda<N>(captures)` +
`invoke(args)`. **Implementation-defined** — outro compilador Kof pode usar
closures de outra forma, desde que preserve a semântica de captura (§4).

---

## 6. Trailing lambda

```ebnf
call-args , trailing-lambda = "(" , [ args ] , ")" , block
```

```kof
transaction { println("dentro") }
list.forEach((x: Int) -> println(x))
map.map { s: String -> s.length }        // trailing com params tipados
```

- `f { … }`: o bloco é o **último argumento** (`Parser.java:1436`).
- `f { x: Int -> … }`: trailing lambda com parâmetros (`looksLikeLambdaBlockParams`,
  heurística de lookahead ≤8 tokens — **Implementation-defined**).
- `f { … }` sem `->` é uma **lambda de 0 params** cujo corpo é o bloco.

---

## 7. Limites

- **Lambda não pode declarar função nomeada** dentro do corpo.
- **Lambda não é genérica** (sem `<T>` próprio).
- **Lambda não pode ter `return` de tipo incompatível** com o corpo (SEM010).
- **Não há** SAM-conversion implícita de lambda para interface do usuário de
  forma garantida: `FunctionType → ClassType` passa em `isAssignable` sempre
  (:2187), mas a compatibilidade real é validada na emissão. **Unspecified.**
- **Não há** `it`/`$0` como parâmetro implícito (Kotlin-style). Use
  `(x: T) -> …`.
