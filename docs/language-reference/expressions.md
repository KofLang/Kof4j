# Expressões

**Status:** Stable (exceto onde etiquetado) · **Evidência:** ExpressionParser, `ExpressionLowerer.java`, `TypeChecker.inferBinaryResultType`

Uma expressão produz um valor. A precedência e associatividade completas estão
em [grammar.md](grammar.md) §5. Aqui está a **semântica** de cada forma.

---

## 1. Literais

`int`/`long`/`float`/`double`/`string`/`char`/`bool`/`null` — ver
[types.md](types.md) §4. `null` tem tipo "nulo" que se adapta ao contexto de
atribuição.

---

## 2. Identificadores e acesso a membros

- `x` — variável local, parâmetro, campo (via `this` implícito), ou constante
  de enum não-qualificada.
- `this` — receiver do método/construtor corrente.
- `super` — receiver da superclasse (para `super.method()` / `super(...)`).
- `a.b` — campo ou método (`FieldAccessExpr`/`MethodCallExpr`). Type keywords
  são válidos após `.` (`config.int`).
- `a[i]` — acesso a array (`ArrayAccessExpr`). `.get()/.set()` em array →
  `SEM028`.

---

## 3. Operadores aritméticos: `+ - * / %`

- Aplicáveis a numéricos. Resultado = `commonNumericType` dos operandos
  (double > float > long > int).
- **`/` entre inteiros é divisão inteira truncante**: `7 / 2` → `3` (*probe*).
- **`%` segue o sinal do dividendo** (semântica JVM `irem`): `-7 % 3` → `-1`
  (*probe*).
- **Divisão por zero constante** (`7 / 0`) → `ARITH001` em compile-time.
  **Não-constante** (`7 / z`, `z=0`) → `ArithmeticException` em runtime
  (*probe*). **Target-specific** (Native: trap/SIGFPE; JS: `Infinity`).
- **`+` com `string`** é concatenação: `"x" + 1` → `"x1"`, `1 + "x"` → `"1x"`,
  `"x" + true` → `"xtrue"` (*probe*). Qualquer operando string → concat.
- **`+ - * / %` sobre `bool`** → `SEM002`. Sobre referência não-numérica →
  `SEM001`.

---

## 4. Operadores relacionais e de igualdade: `== != < <= > >=`

- Resultado sempre `bool`.
- **`==` tem semântica por tipo** (decidida no lowering, não no type checker):
  - `string`, `record`, `enum` → **conteúdo**
  - primitivo → **valor**
  - referência (outros) → **identidade**
  - ver [type-system.md](type-system.md) §10.
- `< <= > >=` só fazem sentido em numéricos/char (e string por ordem
  lexicográfica via `compareTo`? — **Unspecified**; o parser aceita, o lowering
  usa `if_icmp`/`lcmp`/`fcmpl`/`dcmpl` para numéricos e `if_acmp*` para
  referências, o que para `<`/`>` em referência é **não suportado**).

---

## 5. Operadores lógicos: `&& || !`

- `&&`/`||` exigem `bool` (ou primitivo inteiro — `1 && 2` compila e é
  **verdadeiro**, *probe*: tratado como não-zero). Resultado `bool`.
- **Short-circuit**: `a && b` não avalia `b` se `a` é falso. **Desligado no
  target JS** (`ExpressionLowerer.java:147-148`) — **Target-specific** (SG-006).
- `!` é negação lógica. `!5` → `0` (*probe*: aplicado a inteiro como XOR com
  -1 / `lnot` JVM que dá 0/1). **Unspecified** para não-bool.

---

## 6. Operadores bit a bit: `& | ^ << >> >>>`

- `& | ^` entre inteiros → bit a bit.
- `<<` shift esquerda; `>>` shift aritmética (sinal); `>>>` shift lógica (zero).
- **Não existe `~`** (complemento) — `~5` → `PARSE041` (*probe*). Para negar
  bits, use `x ^ -1`. **Unspecified** (SG-002).

---

## 7. Atribuição como expressão: `=` e compostos

- `x = v`, `x += v`, `x -= v`, `x *= v`, `x /= v`, `x %= v`, `x &= v`,
  `x |= v`, `x ^= v`, `x <<= v`, `x >>= v`, `x >>>= v`.
- **Right-associativa**: `a = b = c` atribui `c` a `b` e `b` a `a`.
- **Atribuição usada como valor** (`var c = (a = b)`) → `SEM027` (*probe* —
  atribuição é statement, não expressão, exceto na própria forma de atribuição).
- Atribuir a variável nunca declarada → `SEM020`.

---

## 8. Incremento/decremento: `++ --`

- Prefixo (`++x`) e sufixo (`x++`) ambos válidos (ExpressionParser.parseUnary (inc/dec),
  `1457-1466`).
- Aplicável a local, campo, elemento de array.
- `i++` como statement incrementa; como expressão produz o valor antigo
  (semântica padrão). **Implementation-defined** para o valor de retorno em
  posição de expressão (não testado por probe dedicado).

---

## 9. `instanceof` e `as`

- `x instanceof T` → `bool`.
- `x as T` → valor convertido/castado. Ver [type-system.md](type-system.md) §4.

---

## 10. Chamadas

- `f(args)` — função/método. `f<T>(args)` — com type-args explícitos.
- `f { … }` — trailing lambda (o bloco é o último argumento).
- `f { x -> … }` / `f { x: Int -> … }` — trailing lambda com parâmetros.
- `obj.m(args)` — método de instância. `Klass.m(args)` — método estático.
- `new T(args)` — construtor. `new T[n]` — array.
- Aridade errada → `SEM013`; tipo de argumento errado → `SEM014`.

---

## 11. `if` como expressão

`kof
var status = if (ativo) "online" else "offline"
`

- **`else` é obrigatório** na forma expressão: `if (c) x` sem `else` em
  posição de expressão → `PARSE044` (*probe*).
- Os dois braços devem ter tipo comum (senão `SEM012`/`SEM021`).
- Não há operador ternário `c ? a : b` — `?` só é sufixo de tipo (*probe*).

---

## 12. `switch` como expressão

`kof
var desc = switch (o) {
    case String s -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}
`

- Cada case usa `->` e produz **uma expressão** (o valor).
- **`default` obrigatório** (ou enum exaustivo) → senão `SEM032`.
- **Sem fallthrough** — cada ramo é um valor.
- Suporta **pattern matching**: `case Type var` (binding) e `case Type(a, b)`
  (destructuring de record). Ver [classes.md](classes.md) §6.

---

## 13. Lambdas (expressão)

`kof
(x: Int) -> x * 2
(a: Int, b: Int) -> { return a + b }
() -> println("oi")
{ println("bloco") }
`

- Parâmetros **exigem anotação de tipo** para uso aritmético: `(x) -> x + 1` →
  `SEM001` com dica "declare o tipo do parâmetro" (*probe*).
- Corpo: expressão única (retorno implícito) ou bloco `{ … }`.
- Ver [closures.md](closures.md).

---

## 14. `spawn` e `await` em posição de expressão

- `spawn f()` em expressão → `Handle<T>` (baixado como `__kof_spawn_expr`).
- `await h` → valor `T` (baixado como `__kof_await`).
- `awaitTimeout(h, ms)` → valor `T` ou lança exceção (função, **não** sintaxe
  `await h withTimeout` — isso dá `PARSE043`, *probe*).
- Ver [../concurrency.md](../concurrency.md) (documento de stdlib).

---

## 15. Query DSL (expressão)

`kof
User.query(db) { where age > 18; orderBy name desc; limit 10 }
`

- Sintaxe especial reconhecida quando o receiver é uma `entity` declarada e o
  método é `query` com 1 argumento (ExpressionParser.parsePostfix (call)).
- Baixa para `db.query<Entity>(…)` com SQL montado em compile-time e valores
  como binds (sem concat de entrada). ORM001.
- **Experimental** (domínio ORM).

---

## 16. Operações que NÃO são expressões

- `throw` é statement (não produz valor).
- `return`, `break`, `continue` são statements.
- Atribuição não é expressão-valor (SEM027).
- Não há `?:`, `??`, `..`, `in`, `=>`, `~` (SG-002).
