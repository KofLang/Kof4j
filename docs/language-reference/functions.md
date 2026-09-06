# Funções

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Parser.java:248-333`, `SemanticAnalyzer.java:538-556`, `CompilerDriver.java:1607-1769`

---

## 1. Declaração

```ebnf
function-declaration = [ "fn" ] , [ type-ref ] , identifier , [ type-parameters ] ,
                       "(" , [ parameter-list ] , ")" , [ ":" , type-ref ] , function-body
```

As formas canônicas (todas válidas e equivalentes):

```kof
main() { println("entry point") }            // sem tipo → void
String saudacao() { return "oi" }            // tipo antes do nome
despedida(): String { return "tchau" }       // tipo depois dos parênteses
void fazIsso() { println("x") }              // void explícito
Int dobro(Int x) { return x * 2 }
Bool positivo(Int x) = x > 0                 // expression body
```

- **Corpo**: bloco `{ … }`, ou `= expressão` (expression body — vira
  `return expressão`, `Parser.java:301-305`), ou `;` (abstrato).
- **`fn` é prefixo opcional aceito** (`Parser.java:257-259`). **`fun`/`func`
  compilam** por serem lidos como tipo de retorno implícito `void`
  (`fun main()` → função `main` com retorno `void`; *probe*). O corpus
  (`AGENTS.md`) declara que **não existem** — divergência SG-001.
- **Não há** `fun` como keyword, nem `def`, nem `lambda` keyword.

---

## 2. Parâmetros

```ebnf
parameter = ( type-ref , identifier | identifier , ":" , type-ref ) , [ "=" , expression ]
```

Duas ordens válidas:

```kof
f(Int x, String s) { }      // type-first
f(x: Int, s: String) { }    // anotado (idiomático p/ main)
```

- **Default values**: `f(Int x = 10)` — geram **overloads sintéticos por
  aridade decrescente** no lowering (`lowerFunctionDefaults`,
  `CompilerDriver.java:1730-1770`). A chamada com aridade reduzida é aceita.
- **Parâmetros são passados por valor** (referências: o valor é a referência).
- **Não há** parâmetros por referência (`ref`/`out`), nem varargs (`T...`),
  nem spread (`f(*args)`).

---

## 3. Retorno

- Tipo de retorno **antes do nome** ou **depois de `:`** após os parênteses.
- **Sem tipo declarado → `void`** (default).
- **Inferência de retorno**: uma função declarada `void` cujo corpo tem
  `return <valor>` tem o retorno **inferido** no fixpoint (≤4 passes,
  `analyzeMethodBody:470-477`). `Int f() { return 1 }` e `f() { return 1 }`
  (void declarado, inferido Int) — o segundo é **Implementation-defined**.
- `return` sem valor em função não-void → valor default do tipo (ver
  [statements.md](statements.md) §3).

---

## 4. Ponto de entrada (`main`)

Um programa Kof precisa de **exatamente um** `main` (PKG002 se 0 ou >1).
Formas aceitas (*probe*, todas compilam):

```kof
main() { }                       // sem args, sem tipo
void main() { }                  // void explícito
Int main() { return 0 }          // retorna Int (o valor NÃO vira exit code automaticamente — Unspecified)
main(args: List<String>) { }     // args como List
main(args: String[]) { }         // args como array
```

- `main` é **reconhecido por nome** (`"main".equals(func.name())` + aridade 0
  ou 1-arg-`args`, `CompilerDriver.java:1655-1660`). Não é keyword.
- O compilador **reescreve a assinatura** para `main(String[])` no emit
  (injeta `String[]`); `List<String>` é convertido no prólogo (JVM) ou vira
  lista vazia (Native/JS).
- **Não há** `@main` annotation, nem `Main` class obrigatória, nem restrição de
  visibilidade.

---

## 5. Recursão

- **Recursão direta é suportada**: `Int fact(Int n) { … return n * fact(n-1) }`
  → `120` (*probe*).
- **Recursão mútua entre funções top-level** é suportada (a resolução de
  chamada varre as declarações da unidade).
- **Não há TCO** (tail-call optimization) garantido — recursão profunda pode
  estourar a stack do target. **Implementation-defined / Target-specific.**
- Recursão em **métodos** funciona (dispatch virtual normal).

---

## 6. Funções genéricas

```ebnf
function-declaration = … , identifier , type-parameters , …
type-parameters = "<" , identifier , { "," , identifier } , ">"
```

```kof
T idf<T>(T x) { return x }
main() { println(idf<Int>(7)) }     // → 7 (probe)
```

- Type-params de **função** são declarados antes dos parênteses.
- **Não há inferência de type-args de função**: `idf(7)` sem `<Int>` —
  **Unspecified** (a substituição posicional funciona para classes; para
  funções top-level genéricas o retorno `TypeVariable` é inferido do argumento
  correspondente, `MethodCallTyper.java:416-432`).
- Bounds de type-var **não existem**.

---

## 7. Visibilidade e modificadores

- `public` (default se nenhum), `private`, `protected` — aplicados como flags
  JVM (`accessFlagsFor`, `CompilerDriver.java:3375-3391`).
- `static` — função top-level é sempre `PUBLIC|STATIC` no emit; `static` em
  método a torna chamada por nome de classe.
- `abstract` — método sem corpo (`isAbstractMethod` = `body == null`,
  `:3393`).
- `final`, `override` — aceitos como modificadores; `override` **não** é
  validado (não há checagem de que o método existe na super).
- **Não há** `internal`, `module`, `open`, `sealed` (função).

---

## 8. Onde funções vivem

- **Top-level**: compiladas para a classe `Main` (ou `<pkg>/Main`) como métodos
  `static` (`CompilerDriver.java:422-426`).
- **Membros de classe**: métodos normais.
- **Não há** funções aninhadas (função dentro de função) — `main() { f() {} }`
  não é parseado como declaração de função aninhada. **Unspecified** (SG-011).
- **Não há** funções locais nomeadas; para comportamento nomeado local, use
  lambda em `val`.

---

## 9. Sobrecarga de função

- **Não há sobrecarga de função top-level** — duas funções com o mesmo nome na
  mesma unidade colidem (o `define` sobrescreve; `resolveInHierarchy` retorna
  uma). **Unspecified** se é erro ou último-vence.
- **Construtores** sobrecarregam por aridade (ver [classes.md](classes.md)).
- **Métodos** não sobrecarregam (§11 de type-system.md).
