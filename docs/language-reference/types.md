# Tipos — Catálogo e Sintaxe

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Type.java`, `BuiltinTypes.java`, `Parser.java:1816-1903`

Este documento lista **quais tipos existem** e **como se escrevem**. As regras
de *validade* (o que pode ser atribuído a quê, quando há erro) estão em
[type-system.md](type-system.md).

---

## 1. Tipos primitivos (9)

`Type.java:8-16` define exatamente nove `PrimitiveType`:

| Tipo | `sort` | Notas |
|---|---|---|
| `void` | 0 | só tipo de retorno/ausência |
| `bool` | 1 | `true`/`false` |
| `char` | 2 | 16 bits, code unit UTF-16 |
| `byte` | 5 | 8 bits com sinal |
| `short` | 9 | 16 bits com sinal |
| `int` | 10 | 32 bits com sinal |
| `long` | 11 | 64 bits com sinal |
| `float` | 6 | IEEE-754 32 bits |
| `double` | 7 | IEEE-754 64 bits |

- **Não existe `boolean`** como nome de tipo — `bool` é o nome. `Type.of`
  normaliza `"bool"|"boolean"|"Bool"|"Boolean"` → `bool` (`Type.java:82`),
  então `Boolean` *funciona* como alias de escrita, mas o nome canônico é
  `bool`.
- **Não existe `uint`/`ulong`/tipos unsigned.**
- **Não existe `unit`/`never`/`nothing`.**
- `void` não é um tipo de valor: usá-lo como expressão → `SEM033`.
- **Overflow aritmético é silencioso e wrap-around** (semântica JVM):
  `2147483647 + 1` → `-2147483648` (*probe*). Não há checagem de overflow por
  default. **Implementation-defined** (herda do target).

### 1.1 `string` é referência, não primitivo

`string` é keyword de tipo (`TokenType.STRING_TYPE`), mas **não** é
`PrimitiveType`: resolve para `ClassType("java.lang","String")`
(`BuiltinTypes.java:11`). É o único "tipo primitivo de escrita" que é
referência. `String` (maiúsculo) também resolve para o mesmo (`Type.java:82`).

---

## 2. Tipos de referência nomeados

### 2.1 Classes do programa

`class`, `record`, `enum`, `entity`, `interface` definidos no módulo. Escritos
pelo nome, com pacote quando qualificado (`com.dev.NodeUI`).

### 2.2 Coleções builtin (`kof.*`)

| Tipo | Escrita | Nota |
|---|---|---|
| `List<T>` | `List<Int>`, `listOf(…)` | `ClassType("kof","List")` |
| `Map<K,V>` | `Map<String,Int>`, `mapOf(k,v,…)` | `ClassType("kof","Map")` |
| `Set<T>` | `Set<Int>`, `setOf(…)` | `ClassType("kof","Set")` |
| `Channel<T>` | `channel<Int>()` | `ClassType("kof.concurrent","Channel")` |

`ArrayList`/`HashMap`/`HashSet` são **aliases de escrita** que resolvem para
`List`/`Map`/`Set` (`Type.java:69-80`). Não são tipos separados.

### 2.3 Concorrência

`Handle<T>` — o tipo do valor retornado por `spawn` em posição de expressão
(`kof.concurrent.Handle`). Reconhecido por predicado, não por constante em
`BuiltinTypes` (`SemanticAnalyzer.java:828-832`).

### 2.4 `Object`

`ClassType("java.lang","Object")` (`Type.java:83`). Todo tipo de referência é
atribuível a `Object`; primitivos são auto-boxados para `Object`
(`isAssignable`, `SemanticAnalyzer.java:2192-2196`).

---

## 3. Tipos compostos

### 3.1 Array

```ebnf
array-type = type-ref , "[]" ;        (* Int[], String[][], List<Int>[] *)
```

`ArrayType(componentType)` (`Type.java:35`). **Unidimensional por sufixo**;
multidimensional é array-de-array (`Int[][]`). Não há tamanho no tipo.
Alocação: `new Int[n]`. Acesso: `a[i]` (método `.get()` em array → `SEM028`).

### 3.2 Nullable

```ebnf
nullable-type = type-ref , "?" ;      (* String?, Int?, List<Int>? *)
```

`NullableType(inner)` — **wrapper**, não sufixo atômico (`Type.java:45`).
`T?` significa "T ou null". Ver regras em [type-system.md](type-system.md) §5.

### 3.3 Function type

```ebnf
function-type = "(" , [ type-ref , { "," , type-ref } ] , ")" , "->" , type-ref ;
```

`(Int) -> Int`, `(Int, String) -> Bool`, `() -> void`
(`FunctionType(parameterTypes, returnType, className)`, `Type.java:29`).
`className` é `null` para lambda anônima e preenchido com a classe sintética
no lowering — **Implementation-defined**, não observável na linguagem.

### 3.4 Generic

```ebnf
generic-type = qualified-name , "<" , type-ref , { "," , type-ref } , ">" ;
```

`List<List<Int>>`, `Map<String, List<Int>>`. Aninhamento por contagem de
profundidade com split de `>>`/`>>>`. **Não há** wildcard `? extends T` /
`? super T` na linguagem: `List<? extends Int>` *compila* (o `?` é lido como
sufixo nullable e `extends`/`Int` viram lixo no nome) mas **quebra em
runtime** com `NoClassDefFoundError: ?extendsInt` (*probe*) — **Unspecified /
não suportado** (SG-007).

---

## 4. Literais e seus tipos

| Literal | Tipo | Exemplo |
|---|---|---|
| inteiro sem sufixo | `int` (ou `long` se não couber) | `42` |
| sufixo `l`/`L` | `long` | `9000000000L` |
| sufixo `f`/`F` | `float` | `1.5f` |
| sufixo `d`/`D` ou decimal | `double` | `1.5`, `1.5d` |
| hex | `int` | `0xFF` |
| `"…"` | `string` | `"oi"` |
| `'…'` | `char` | `'a'` |
| `true`/`false` | `bool` | |
| `null` | tipo nulo (adapta ao contexto) | |

---

## 5. O que NÃO é um tipo em Kof (SG-003)

Não existem: **type alias** (`typealias X = Int` → `PARSE011`, *probe*),
**traits** (`trait` não é keyword; vira função → `PARSE011`, *probe*),
**macros** (`macro m(){}` é parseado como função chamada `macro`, não como
macro — *probe*; não há sistema de macro), **tipos de interseção/unão**,
**tipos literais/singleton**, **`Optional<T>`/`Result<T>`** (use `T?` +
`throw`), **tipos de valor customizados** (só os 9 primitivos).

---

## 6. Recursão de tipos

Um tipo pode referir a si mesmo (via nome): `class Node { Node next … }`,
`List<List<Int>>`. Não há restrição de estratificação — a resolução é por
nome, não por expansão. **Recursive types são permitidos.**
