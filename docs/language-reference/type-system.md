# Sistema de Tipos e Type Checking

**Status:** Stable (regras) · **Evidência:** `SemanticAnalyzer.java`, `Type.java`, `CompilerTypes.java`, `TypeMetrics.java`, probes de execução

> Este é o documento mais importante da referência. Ele evita o termo vago
> "tipagem forte" e descreve **comportamento concreto**: o que é aceito, o que
> é rejeitado, quando há inferência, o que é garantido e o que **não** é.

---

## 1. Classificação do sistema

Kof é **estaticamente tipada** (tipos resolvidos em compile-time), com
**inferência local** (de `var`/`val` e de retorno `void`), **nominal** para
classes (subtipagem por nome/herança, não estrutural) e **com erasure** para
generics (type-args apagados no emit, como Java).

O termo "strong typing" **não** é usado aqui como elogio. As propriedades
concretas — e as **falhas de garantia** — estão nas seções seguintes. Onde o
type checker **não** impede uma operação, isso está dito explicitamente.

---

## 2. Onde o type checking acontece (pipeline real)

```text
Source ─▶ Lexer ─▶ Tokens ─▶ Parser ─▶ AST(crua)
       ─▶ Desugar (test/application) ─▶ AST(desugared)
       ─▶ SemanticAnalyzer.analyze ─▶ AST + maps laterais (tipos resolvidos)
       ─▶ [aborta se houver erro] ─▶ Lowering AST→IR ─▶ Optimizer ─▶ Backend
```

`CompilerDriver.java`, método `lowerAndEmit`. **Type checking e resolução de nomes NÃO são
fases separadas**: acontecem entrelaçados dentro de `inferType`
(`SemanticAnalyzer.java:834-1947`), que resolve o nome e checa o tipo no mesmo
ponto, emitindo diagnóstico inline.

### 2.1 As 4 fases do analisador (`SemanticAnalyzer.analyze`, :89-106)

| Fase | Método | O que faz |
|---|---|---|
| 1 | `preDeclareType` | Cria `ClassSymbol` vazio por tipo; registra `knownClasses`; sintetiza `values()/valueOf()/name()` em enums |
| 2 | `defineMembers` | Preenche campos/métodos/construtores; accessors de record; type-params |
| 3 | `analyzeDeclaration` | Analisa corpos; **fixpoint ≤4 passes por classe** (inferência de retorno void→T) |
| 4 | `resolveMethodCalls` | **No-op efetivo** — a resolução real já ocorreu eager na fase 3 (`SemanticAnalyzer.java:2223-2296`) |

### 2.2 Não há "typed AST"

Os nós da AST **não carregam tipo resolvido** — tipos de declaração são
`String` na AST. Os tipos resolvidos vivem em **maps laterais por identidade
de nó** (`IdentityHashMap`): `expressionTypes`, `resolvedMethods`,
`resolvedConstructors` (`SemanticAnalyzer.java:64-69`). O lowering **re-inferi**
tudo via `ExpressionTyper`/`MethodCallTyper` (o cache do analyzer é limpo a cada
pass/classe — `MethodCallTyper.java:27-34`). **Implementation-defined.**

### 2.3 Quando erros são reportados

Durante a análise, imediatamente (`diagnostics.error`), e o driver **aborta
antes do lowering** se houver erro (`CompilerDriver.java`, guarda `diagnostics.hasErrors()` pós-`analyze`). Exceção:
alguns códigos `SEM0xx` são **deferidos** para lowering/emit (SEM016/017/029/
030/031/033/034, ARITH001) — só disparam se a análise passou.

---

## 3. Atribuição e compatibilidade (`isAssignable`, :2166-2209)

Uma atribuição `dest = src` (e argumentos, retornos) é aceita quando:

| Regra | Aceita? | Evidência |
|---|---|---|
| `T → T` (iguais) | ✅ | :2178 |
| `T → T?` (torna nullable) | ✅ | :2174 |
| `T? → T?` (recursa no inner) | ✅ | :2170 |
| **`T? → T`** (desembrulhar nullable) | ❌ `SEM021` | :2170 — só após narrowing (§5) |
| widening numérico (`primitiveWidth(from) ≤ primitiveWidth(to)`) | ✅ | :2183 |
| `double → float` | ✅ (exceção explícita, D2F) | :2182 |
| narrowing numérico (`long→int`, `double→int`, `int→byte`) | ❌ `SEM021` | *probe*: `Long x; Int y = x` → SEM021 |
| `primitivo → Object` (auto-box) | ✅ | :2192 |
| `FunctionType → ClassType` (SAM) | ✅ (sempre; compatibilidade real adiada para emissão) | :2187 |
| `TypeVariable` em qualquer posição | ✅ | :2169 |
| **`ClassType → ClassType` (qualquer par)** | ✅ **SEMPRE** | :2205 — ⚠️ ver §7 |

### 3.1 `primitiveWidth` (:2211-2221)

`bool=0, char=1, {int,byte,short}=2, long=3, float=4, double=5`.

Consequência observável: `bool → int` **passa na checagem** (width 0≤2) e
**produz `1`/`0`** no emit — porque `bool` é armazenado como `int` 1/0 em Kof
(`var i: Int = true; println(i)` → `1`, *probe*). Não é um "vazio": é coerção
funcional por representação. Atribuições `bool → long/float/double` seguem a
mesma via widening. **Implementation-defined** (a representação 1/0 é detalhe
de implementação que vazou para a semântica observável).

### 3.2 Coerções em aritmética (`commonNumericType`, TypeMetrics.java:57-70)

Para `+ - * / %` com dois numéricos: `double` domina, senão `float`, senão
`long`, senão `int`. `7 / 2` → `int` = `3` (divisão inteira) (*probe*).
`1 + 1.5` → `double` = `2.5`.

---

## 4. Conversões: `as` e `instanceof`

- **`x as T`** é **cast explícito**, nunca implícito. O resultado tem tipo `T`
  (`SemanticAnalyzer.java:2062`).
  - primitivo→primitivo: widening + narrowing (`I2C`, `L2I`, `F2I`, `D2I`, …)
    — `Long x; x as Int` funciona (*probe*).
  - referência: `checkcast` JVM (pode lançar `ClassCastException` em runtime).
  - **`5 as String` NÃO faz parse de número**: emite `checkcast String` sobre
    um `Integer` boxado → falha em runtime (*probe*). String↔número é só via
    `toInt()/toLong()/toDouble()/toFloat()` (métodos de `string`).
- **`x instanceof T`** → `bool` (`:2059`). `o instanceof String` (*probe* ✅).

---

## 5. Nullability

- Representação: wrapper `NullableType(inner)`. `T?` = "T ou null".
- **Storage é o inner** — nullable é constraint de compile-time apenas
  (`StatementLowerer.java:47-51`).
- **Narrowing**: a **única** forma reconhecida é `if (x != null)` (ou `null !=
  x`) com `x` identificador de tipo `T?` → no **then-branch**, `x` passa a ter
  tipo `T` (`SemanticAnalyzer.java:666-684`). **Não há** narrowing por `&&`,
  `||`, ternário, ou `if (x == null)` no else.
- **Deref de `T?` sem narrowing NÃO é erro**: `var s: String? = "x"; s.length`
  **compila** (*probe*) — o lowering desembrulha o receiver
  (`ExpressionTyper.java:143`). A segurança null é **advisory**, não garantida
  pelo compilador (SG-005).
- **Comparação com null**: primitivo `== null` → **constante** (`false`/`true`,
  `ExpressionLowerer.java:256-268`); referência `== null` → `if_acmp`.
  `Int? == Int?` compara valor (*probe*: `5 == 5` → true). **`Int? == null`
  falha em runtime** (o unbox de um `Integer` null lança NPE — o erro do
  "JavaFX launcher" é o wrapper do runtime para exceção não tratada; *probe*).
  `String? == null` → `true` corretamente (*probe*). Comparar nullable de
  primitivo com `null` é **bug de runtime** (SG-008), não regra de linguagem.
- **Fontes de `T?`**: `Map.get(k)` para valor de referência, `readLine()`,
  `readFile()`, literais `T?`.

---

## 6. Resolução de nomes e escopo

`SymbolTable` é uma cadeia de escopos com `parent` (`SymbolTable.java:9-77`);
`resolve(name)` busca do mais interno para o mais externo. Ordem de resolução
de um identificador (`inferTypeInternal`, case `IdentifierExpr`, :861-911):

1. escopo local em cadeia (locais → params → campos da classe → raiz)
2. `args` em `main` → `String[]`
3. constante de enum não-qualificada (`Red` quando `enum Color{Red}`)
4. membro da classe corrente via `resolveInHierarchy` (BFS: classe→super→interfaces)
5. senão, se não é namespace builtin (`json`, `process`, `KofWeb`, …) nem tipo
   builtin → **`SEM011`** (indefinido)

**Shadowing**: permitido por escopo (innermost-first). Redeclarar no **mesmo**
escopo → `SEM024`. Em lambdas, params e declarações internas entram em
`shadowed` e **não** capturam a externa homônima.

**Imports**: `qualifyViaImports` só resolve **nome simples** (sem `.`/`<`/`[]`)
pelo **primeiro** import não-wildcard terminando em `.<nome>`
(`SemanticAnalyzer.java:53-63`). **Wildcards `import a.b.*` não são usados para
qualificar nomes** (:57). Type-arguments são qualificados recursivamente por
`qualifyDeep` (bug 32, `CompilerTypes.java:48-94`): nome simples via imports →
classes do módulo; **import ambíguo → não chuta** (tipo preservado).

---

## 7. Subtipagem — a maior lacuna (SG-009)

`isAssignable` aceita **`ClassType → ClassType` sempre** (`:2205-2207`). Não há
checagem de que `to` é supertype de `from`. Consequências:

- `B extends A; A a = b` funciona (*probe*) — mas por coincidência (o `checkcast`
  do lowering salva o emit), não por regra de subtipagem.
- **`A a = b_de_outra_classe` (não-relacionadas) também passa na checagem de
  tipos.** A segurança é **delegada ao `checkcast`/runtime do target**, não ao
  type checker.
- `implements I` **não** exige cobrir todos os métodos: `class C implements I {}`
  com `I` tendo `f()` abstrato **compila** (*probe*) — só falha se o método for
  chamado (runtime `AbstractMethodError`).
- `abstract class A; new A()` **compila** e falha em runtime com
  `InstantiationError` (*probe*) — não é erro de tipo.

**Garantia real do type checker:** chamada a função/método **inexistente em tipo
conhecido** é erro (`SEM015`/`SEM025`); aridade de argumentos/construtores é
checada (`SEM013`/`SEM023`); tipo de retorno incompatível é erro (`SEM010`);
`throw` só aceita `String` (`SEM026`); atribuição respeita `isAssignable`
(`SEM012`/`SEM021`); redeclaração no mesmo escopo é erro (`SEM024`); switch-
expressão exige default/exaustividade (`SEM032`); enum exaustivo em switch
(`SEM031`).

**Não garantido:** subtipagem correta; tipo de elemento em `list.add`/`map.put`
(`l.add("x")` numa `List<Int>` **não é erro** — §8); cobertura de interface;
instandabilidade de abstract; coerção `bool→numérico` (funciona por
representação 1/0, mas é implementation-defined — §3.1).

---

## 8. Generics (erasure-first)

- Type-args vivem **só** em `ClassType.typeArguments`. **Erasure**: no emit,
  `TypeVariable → Object` (`JvmTypeMapper.java:16`).
- **Substituição posicional** (`substituteTypeVariable`, `CompilerTypes.java:255`):
  dado `Box<Int>` e type-var `T` (1º type-param de `Box`), retorna `Int`. Só
  para type-params de **classe**, varrendo `currentUnit`.
- **Sem variance** (sem `extends`/`super` em type-args — §3.4 de types.md).
- **Sem bounds** de type-variable (não há `T extends X`).
- **Sem inferência de type-args de construtor**: `new Box(42)` **não** infere
  `Box<Int>` (`NewExpr` devolve type-args vazios, :1762).
- **Sem checagem de elemento em coleção**: `List<Int>.add("x")` não é detectado
  (`add` é tipado `Void` sem checagem, :1312). A falha aparece **só em runtime,
  no `get` com tipo concreto**: `m.put("b","z")` num `Map<String,Int>` →
  `ClassCastException` ao ler (*probe*); `l.add(9)` numa `List<Int>` funciona
  normalmente (*probe* — o tipo inferido era `Int` e 9 é `Int`). **Unspecified**
  como política.
- `listOf(1,2)` → `List<Int>` (tipo do 1º arg); `mapOf(k1,v1,…)` → `Map<K,V>`
  **pinned no 1º par**; `setOf(…)` → `Set<T>` (:1045-1073).

---

## 9. Boxing / unboxing

- **Auto-box** de primitivo para slot de referência no emit (`Integer`, `Long`,
  …; `JvmBackend.java:77-101`).
- **Unbox** em `list.get(i)` conforme elemType (`JvmBackend.java:913-946`):
  `listOf(1,2).get(0) + 1` → `2` (*probe*).
- **Box de erasure** (primitivo atrás de type-var/Object): `kof_box`/`kof_unbox`.
- **Captura mutável** de closure usa classe `Box<N>` sintética (ver
  [closures.md](closures.md)).

---

## 10. Comparação `==` (semântica por tipo — decidida no lowering)

| Operando | `==` compara | Evidência |
|---|---|---|
| `string` | **conteúdo** (`kof_string_equals`) | *probe*: `"ab" == "a"+"b"` → true |
| `record` | **conteúdo** (equals gerado campo a campo) | *probe*: `P(1,2)==P(1,2)` → true |
| `enum` | **conteúdo** (é String em runtime) | `ExpressionLowerer.java:285` |
| primitivo | **valor** | `if_icmp`/`lcmp`/`fcmpl`/`dcmpl` |
| referência (não-string/record/enum) | **identidade** (`if_acmp`) | *probe*: `C(1)==C(1)` → false |

`a.equals(b)` **funciona** em string (*probe*) mas é anti-pattern — use `==`.

---

## 11. Overload e resolução de método

- **Construtores**: sobrecarga **por aridade** (`ConstructorSet`,
  `SymbolTable.java:47-58`). Aridade errada → `SEM023`.
- **Métodos**: **NÃO há sobrecarga real** — `define` sobrescreve homônimos;
  `resolveInHierarchy` retorna o único `MethodSymbol`. `checkArgTypes` valida
  aridade/tipos do escolhido mas **não seleciona entre candidatos**.
- **Default parameters** geram overloads sintéticos por aridade decrescente no
  lowering (`lowerFunctionDefaults`).
- **Dispatch**: `KofCallKind {INSTANCE, STATIC, CONSTRUCTOR, FUNCTION,
  INTERFACE, SUPER}` → opcode JVM (`INVOKEVIRTUAL`/`STATIC`/`SPECIAL`/
  `INTERFACE`). **Dispatch virtual polimórfico é delegado ao runtime** — o
  compilador só escolhe o opcode; não há vtable própria. *probe*: `A a = B();
  a.f()` → `2` (override real).

---

## 12. Métodos de tipos builtin

Não há `SymbolTable` para `List`/`Map`/`Set`/`String`/`Channel` — são **tabelas
de assinatura hard-coded** em três camadas espelhadas (análise, lowering de
tipagem, lowering de emissão). Exemplos de retorno:

- `List`: `get/remove`→elemType; `size/length/count`→Int; `contains/isEmpty`→
  Bool; `add/push/append/set/clear`→Void; `map/filter/reduce`→higher-order.
- `Map`: `get`→`V?` (referência); `put/remove`→V; `keys`→`List<K>`; `values`→
  `List<V>`.
- `String`: `indexOf/length/compareTo/hashCode`→Int; `isEmpty`→Bool;
  `substring/split/replace/trim/toUpperCase/toLowerCase`→String/String[];
  `toInt/toLong/toDouble/toFloat`→número (funções do **runtime**, não de
  `java.lang.String`).

`map((x:Int)->…)` → `List<R>`; `filter` → mesmo tipo do receiver; `reduce` →
retorno do lambda (*probe*: map/filter/reduce corretos).

---

## 13. Tabela de erros de tipo (SEM0xx)

| Código | Detecta | Evidência |
|---|---|---|
| `SEM001` | operador aritmético em String/não-numérico | :2072, 2119 |
| `SEM002` | aritmética sobre `bool` | :2097 |
| `SEM010` | `return` com tipo incompatível | :651 |
| `SEM011` | variável/tipo indefinido | :884 |
| `SEM012` | atribuição incompatível (statement) | :569 |
| `SEM013` | nº de argumentos ≠ parâmetros | :2147 |
| `SEM014` | argumento com tipo incompatível | :2155 |
| `SEM015` | função indefinida / não-função chamada | :1579 |
| `SEM020` | atribuição a variável nunca declarada | :926 |
| `SEM021` | tipo explícito ≠ tipo do inicializador | :635 |
| `SEM023` | construtor com aridade errada | :1773 |
| `SEM024` | redeclaração no mesmo escopo | :628 |
| `SEM025` | método inexistente em tipo conhecido | :1550 |
| `SEM026` | `throw` de valor não-String | :802 |
| `SEM027` | atribuição usada como expressão | :912 |
| `SEM028` | `.get()/.set()` em array | :993 |
| `SEM029` | `toArray()` em List/Set | driver:4052 |
| `SEM030` | enum sem a constante acessada | driver:4859 |
| `SEM031` | switch-statement sobre enum não exaustivo | SwitchStmtLowerer:32 |
| `SEM032` | switch-expressão sem default | :1919 |
| `SEM033` | valor `void` usado como expressão | driver:2675 |
| `SEM034` | `sublist()`/`subSet()` | driver:4067 |
| `ARITH001` | divisão/resto por zero **constante** | ExpressionLowerer:198 |

Divisão por zero **não-constante** (`7 / z` com `z=0`) → erro de **runtime**
(`ArithmeticException` no JVM; *probe*), não compile-time.
