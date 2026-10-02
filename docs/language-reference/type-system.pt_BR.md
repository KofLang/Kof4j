[English](type-system.md) | [Português](type-system.pt_BR.md)

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

> **Atualização 09/09 (decisões do maintainer sobre os gaps B):** as garantias
> de compilação se tornaram estritas nos pontos que faltavam — instanciação de
> `abstract` (SEM041), tipo aninhado (SEM042), cobertura de `implements`
> (SEM043), assinatura de `main` (SEM044), cláusula `throw` (SEM045) e
> visibilidade `private`/`protected` (SEM046) são erros de compile-time.
> Lambda em coleção herda o tipo do elemento sem anotação (SG-012); função
> aninhada funciona com hoisting (SG-011); guardas em pattern matching
> (`case T v if cond`) são suportadas (SG-014). Ver tabela de erros no §13.

---

## 2. Onde o type checking acontece (pipeline real)

`text
Source ─▶ Lexer ─▶ Tokens ─▶ Parser ─▶ AST(crua)
       ─▶ Desugar (test/application) ─▶ AST(desugared)
       ─▶ SemanticAnalyzer.analyze ─▶ AST + maps laterais (tipos resolvidos)
       ─▶ [aborta se houver erro] ─▶ Lowering AST→IR ─▶ Optimizer ─▶ Backend
`

`CompilerDriver.java`, método `lowerAndEmit`. **Type checking e resolução de nomes NÃO são
fases separadas**: acontecem entrelaçados dentro de `inferType`
(`SemanticAnalyzer`/`SemExpressionTyper.inferType`), que resolve o nome e checa o tipo no mesmo
ponto, emitindo diagnóstico inline.

### 2.1 As 4 fases do analisador (`SemanticAnalyzer.analyze`)

| Fase | Método | O que faz |
|---|---|---|
| 1 | `preDeclareType` | Cria `ClassSymbol` vazio por tipo; registra `knownClasses`; sintetiza `values()/valueOf()/name()` em enums |
| 2 | `defineMembers` | Preenche campos/métodos/construtores; accessors de record; type-params |
| 3 | `analyzeDeclaration` | Analisa corpos; **fixpoint ≤4 passes por classe** (inferência de retorno void→T) |
| 4 | `resolveMethodCalls` | **No-op efetivo** — a resolução real já ocorreu eager na fase 3 (`SemanticAnalyzer.resolveMethodCalls`) |

> **Nota pós-REFACTOR-500 F6:** as fases 1–2 foram extraídas para
> `SymbolTableBuilder`; a checagem de tipos (`isAssignable`, `primitiveWidth`,
> `checkArgTypes`, `inferBinaryResultType`) para `TypeChecker`; a análise de
> statements (`IfStmt`/narrowing) para `StatementAnalyzer`; a inferência de
> expressão para `SemExpressionTyper`. O `SemanticAnalyzer` orquestra. As
> referências abaixo usam **método**, não linha (o refactor está em curso).

### 2.2 Não há "typed AST"

Os nós da AST **não carregam tipo resolvido** — tipos de declaração são
`String` na AST. Os tipos resolvidos vivem em **maps laterais por identidade
de nó** (`IdentityHashMap`): `expressionTypes`, `resolvedMethods`,
`resolvedConstructors` (campos do `SemanticAnalyzer`). O lowering **re-inferi**
tudo via `ExpressionTyper`/`MethodCallTyper` (o cache do analyzer é limpo a cada
pass/classe — `MethodCallTyper.java:27-34`). **Implementation-defined.**

### 2.3 Quando erros são reportados

Durante a análise, imediatamente (`diagnostics.error`), e o driver **aborta
antes do lowering** se houver erro (`CompilerDriver.java`, guarda `diagnostics.hasErrors()` pós-`analyze`). Exceção:
alguns códigos `SEM0xx` são **deferidos** para lowering/emit (SEM016/017/029/
030/031/033/034, ARITH001) — só disparam se a análise passou.

---

## 3. Atribuição e compatibilidade (`TypeChecker.isAssignable`)

Uma atribuição `dest = src` (e argumentos, retornos) é aceita quando:

| Regra | Aceita? | Evidência |
|---|---|---|
| `T → T` (iguais) | ✅ | `from.equals(to)` |
| `T → T?` (torna nullable) | ✅ | caso `NullableType` em `to` |
| `T? → T?` (recursa no inner) | ✅ | recursão `inner()` |
| **`T? → T`** (desembrulhar nullable) | ❌ `SEM021` | só após narrowing (§5) |
| widening numérico (`primitiveWidth(from) ≤ primitiveWidth(to)`) | ✅ | `TypeChecker.primitiveWidth` |
| `double → float` | ✅ (exceção explícita, D2F) | caso `double→float` |
| narrowing numérico (`long→int`, `double→int`, `int→byte`) | ❌ `SEM021` | *probe*: `Long x; Int y = x` → SEM021 |
| `primitivo → Object` (auto-box) | ✅ | caso primitivo→`java.lang.Object` |
| `FunctionType → ClassType` (SAM) | ✅ (sempre; compatibilidade real adiada para emissão) | caso SAM |
| `TypeVariable` em qualquer posição | ✅ | caso `TypeVariable` |
| **`ClassType → ClassType` (qualquer par)** | ✅ **SEMPRE** | caso final `to instanceof ClassType` — ⚠️ ver §7 |

### 3.1 `TypeChecker.primitiveWidth`

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
  (`TypeChecker.inferBinaryResultType`, caso `as").
  - primitivo→primitivo: widening + narrowing (`I2C`, `L2I`, `F2I`, `D2I`, …)
    — `Long x; x as Int` funciona (*probe*).
  - referência: `checkcast` JVM (pode lançar `ClassCastException` em runtime).
  - **`5 as String` NÃO faz parse de número**: emite `checkcast String` sobre
    um `Integer` boxado → falha em runtime (*probe*). String↔número é só via
    `toInt()/toLong()/toDouble()/toFloat()` (métodos de `string`).
- **`x instanceof T`** → `bool` (`SemExpressionTyper`, caso `instanceof`). `o instanceof String` (*probe* ✅).

---

## 5. Nullability

- Representação: wrapper `NullableType(inner)`. `T?` = "T ou null".
- **`NullableType` é semântico, não apenas constraint de compile-time** — o
  storage é a representação interna do target, e a ausência é um valor real
  distinto de todo valor presente: `Absent != Present(0)`,
  `Absent != Present(false)`, `Absent != Present(0.0)`. Por target:
  `JVM` referência de wrapper | `null`; `Script` valor do host | `null`;
  `JS` valor dinâmico | `null`; `Native` `RuntimeErasureBox*` | ponteiro `0`.
  A fronteira `T → T?` é a chamada compartilhada `kof_box` (`emitErasureBox`),
  nunca `Wrapper.valueOf` direto — ver
  [RUNTIME_ABI.md §3.9](../runtime/RUNTIME_ABI.md).
- **Narrowing**: a **única** forma reconhecida é `if (x != null)` (ou `null !=
  x`) com `x` identificador de tipo `T?` → no **then-branch**, `x` passa a ter
  tipo `T` (`StatementAnalyzer`, narrowing de `IfStmt`). **Não há** narrowing por `&&`,
  `||`, ternário, ou `if (x == null)` no else.
- **Deref de `T?` sem narrowing É erro** (SG-005 corrigido 10/09, `9436da12`):
  `var s: String? = "x"; s.length` → `error: receiver is nullable (T?);
  narrow first` [**SEM049**] (*medido 16/09, jar do tip `803eeef4*`).
  O comportamento antigo de "advisory, não garantida" acabou — o narrowing
  (`if (x != null)`) é obrigatório.
- **Comparação com null**: primitivo `== null` → **constante** (`false`/`true`,
  `ExpressionLowerer.java:256-268`); referência `== null` → `if_acmp` (class/
  String fazem narrowing corretamente — *medido 16/09*). **Record** era a
  exceção (`==`/`!=` num record baixava para `.equals()` sem guarda de null →
  um `Point?` null comparado com `null` dava **NPE**); consertado 17/09 —
  `§262` (face (a) `07a51565` literal `null` → comparação de referência;
  face (b) igualdade de conteúdo null-safe via desugar `Objects.equals` /
  helper JS `kofRecordEq`).
- **`Nullable(primitivo)` compara por valor, lifted** (*medido 19/09*,
  `NativeNullablePrimitiveContractE2ETest`, nos 6 targets): um `Int?` null
  testa `== null` como **true** e imprime `null`; `Int? == Int?` é igualdade
  de valor, nunca identidade de wrapper (dois `10000` de chamadas distintas →
  `true`); `Float?`/`Double?` seguem o contrato do wrapper JVM, então
  `NaN == NaN` → `true` e `+0.0 == -0.0` → `false`. A dobra silenciosa antiga
  — um `Int?` null comparando `== null` como `false`, com `null`→`0` na
  fronteira — era o bug **D-NULL-INTENT / #259**, e acabou: o storage não é
  mais o interno cru. `String? == null` → `true`.
- **Fontes de `T?`**: `Map.get(k)` para valor de referência, `readLine()`,
  `readFile()`, função declarada `T?` que faz `return null`. **Não existe
  literal `T? = null`** — o null-literal é rejeitado desde 10/09 (SG-008 →
  **SEM048**, *medido*: `null cannot be assigned [SEM048]`).

---

## 6. Resolução de nomes e escopo

`SymbolTable` é uma cadeia de escopos com `parent` (`SymbolTable.java:9-77`);
`resolve(name)` busca do mais interno para o mais externo. Ordem de resolução
de um identificador (`SemExpressionTyper`, case `IdentifierExpr`):

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
(`MemberResolver.qualifyViaImports`). **Wildcards `import a.b.*` não são usados para
qualificar nomes** (`MemberResolver.qualifyViaImports`). Type-arguments são qualificados recursivamente por
`qualifyDeep` (bug 32, `CompilerTypes.java:48-94`): nome simples via imports →
classes do módulo; **import ambíguo → não chuta** (tipo preservado).

---

## 7. Subtipagem (SG-009 — ✅ CORRIGIDO 10/09)

`isAssignable` faz **subtipagem nominal** para reference→reference de classes de
domínio: percorre `superClass`/`interfaces` por BFS. Classe **não relacionada** é
**erro de compilação** (`SEM021`, *probe*: `class A`/`class B` com `A a = B()`).
Idem cobertura de `implements` (`SEM043`), instanciação de abstrata (`SEM041`) e
tipo de elemento de coleção (`SEM056`) — todos impostos em compile-time:

- `B extends A; A a = b` — válido (subtipo real).
- `A a = b_de_outra_classe` (não relacionadas) → `SEM021` em compile-time.
- `class C implements I {}` com `f()` abstrato → `SEM043`; uma `abstract class`
  pode adiar, a obrigação é transitiva para a subclasse concreta (`#322`).
  Métodos `default` contam como satisfeitos.
- `abstract class A; new A()`/`A()` → `SEM041` em compile-time.
- `l.add("x")` numa `List<Int>` → `SEM056`.

**`sealed` (X5.1/X5.2 — `D-X5-SURFACE`, 21/09):** `sealed class`/`record`/`interface`
fecha o conjunto de subtipos em compile-time — o conjunto é o das declarações da
**mesma unidade de compilação** (arquivo). Um subtipo direto (`extends`/
`implements`) declarado fora dela é **`SEM080`** (o compilador não o conhece),
nunca um conjunto aberto silencioso. Um `switch` **expressão** sobre sujeito
selado é **exaustivo sem `default`** quando cobre todo subtipo direto
(`case Subtype v ->`); faltando um subtipo é **`SEM081`**. `sealed` é modificador
**só de compile-time** (apagado na emissão: bytes idênticos em JVM/Native/JS) e
**keyword contextual** — `sealed` segue identificador válido fora de uma
declaração de tipo.

**Variância no sítio de declaração `out`/`in` (X5.3 — `D-TYPE-VARIANCE`, 21/09):**
um type-param genérico pode carregar um prefixo de variância — `class Source<out T>`
é **covariante**, `class Sink<in T>` é **contravariante**, e sem prefixo é
**invariante** (o padrão, inalterado). A variância governa a compatibilidade dos
**type-args do mesmo raw**:

```kof
record Source<out T>(T value)          // componente somente-leitura = posição de saída
Source<Animal> up(Source<Dog> d) { return d }   // OK: Dog <: Animal (covariante)

class Sink<in T> { String consume(T v) { return "x" } }   // posição de entrada
Sink<Dog> down(Sink<Animal> w) { return w }     // OK: Animal >: Dog (contravariante)

class Box<T> { T value ... }           // invariante
Box<Animal> f(Box<Dog> d) { return d } // SEM021: rejeitado (§270)
```

`out`/`in` são **keywords contextuais** (seguem identificadores válidos). Apagados
na emissão: descritores e bytes de execução idênticos em JVM/Native/JS (a variância
vive só no typer). **Guarda de solidez (`SEM082`):** `out T` é proibido em posição
de entrada (parâmetro de método/construtor, campo gravável de classe) e `in T` é
proibido em posição de saída (retorno, qualquer campo ou componente de record),
porque um `out` gravável / `in` legível deixaria o alias covariante/contravariante
gravar ou expor um valor do tipo errado. Componentes de record e campos de
interface são somente-leitura, então `out T` é permitido neles. **Guarda de
herança (`SEM083`, X5.3b):** um type-param declarado `out`/`in` não pode ser
passado a um parâmetro do supertipo com variância **incompatível** —
`class Bad<out T> extends Sink<T>` é rejeitado quando `Sink` declara `in T` (o
supertipo reintroduziria `T` numa posição de entrada), e `class Bad<in T>
extends Source<T>` é rejeitado quando `Source` declara `out T`. Passar uma
variância para um parâmetro **invariante** do supertipo também é rejeitado (o
invariante exige leitura e escrita). O caso coerente (`class Good<out T> extends
Source<T>` com `Source<out T>`) é permitido.

**Projeção no sítio de uso `List<out T>` / `List<in T>` (X5.4 — `D-X5-SURFACE`,
21/09):** mesmo um tipo declarado **invariante** aceita uma projeção no sítio de
uso, exatamente como wildcards de Java, mas com a grafia `out`/`in` de Kof:

```kof
List<out Animal> up(List<Dog> xs) { return xs }   // OK: uso covariante
List<in Dog> down(List<Animal> xs) { return xs }  // OK: uso contravariante
List<Animal> same(List<Dog> xs) { return xs }     // SEM021: invariante, rejeitado
```

`List<out T>` aceita qualquer `List<S>` com `S <: T`; `List<in T>` aceita
qualquer `List<S>` com `S >: T`. É a mesma informação só de compile-time da
variância no sítio de declaração — a emissão a apaga (a projeção vira o
`WildcardType` já existente, que os quatro alvos já apagam), então descritores e
bytes de execução não mudam. `out`/`in` num type-argument continuam contextuais
(seguem identificadores válidos).

**Garantia do type checker:** chamada a função/método **inexistente em tipo
conhecido** é erro (`SEM015`/`SEM025`); aridade de argumentos/construtores é
checada (`SEM013`/`SEM023`); tipo de retorno incompatível é erro (`SEM010`);
`throw` só aceita `String` (`SEM026`); atribuição **e argumento de chamada**
respeitam o `isAssignable` nominal — hierarquia + args de genérico (invariante
por padrão, `out`/`in` por variância declaration-site; #688) — (`SEM012`/`SEM014`/
`SEM021`); redeclaração no mesmo escopo é erro (`SEM024`); switch-
expressão exige default/exaustividade (`SEM032`); enum exaustivo em switch
(`SEM031`).

**Não é garantia:** coerção `bool→numérico` funciona por representação 1/0 mas é
implementation-defined (§3.1); ver os itens `não checado` (`private`/`protected`
em **campos**, SG-013).

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
  `Box<Int>` (`SemExpressionTyper`, caso `NewExpr` devolve type-args vazios).
- **Sem checagem de elemento em coleção**: `List<Int>.add("x")` não é detectado
  (`MemberCallTyper`/`CollectionMethodTyper`: `add` é tipado `Void` sem checagem). A falha aparece **só em runtime,
  no `get` com tipo concreto**: `m.put("b","z")` num `Map<String,Int>` →
  `ClassCastException` ao ler (*probe*); `l.add(9)` numa `List<Int>` funciona
  normalmente (*probe* — o tipo inferido era `Int` e 9 é `Int`). **Unspecified**
  como política.
- `listOf(1,2)` → `List<Int>` (tipo do 1º arg); `mapOf(k1,v1,…)` → `Map<K,V>`
  **pinned no 1º par**; `setOf(…)` → `Set<T>` (`SemExpressionTyper`, caso `setOf`).

---

## 9. Boxing / unboxing

- **Auto-box** de primitivo para slot de referência no emit (`Integer`, `Long`,
  …; `JvmBackend.java:77-101`).
- **Unbox** em `list.get(i)` conforme elemType (`JvmOpCollections.java:95-112`):
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
| `enum` | **identidade** entre dois valores de enum (cada constante é uma instância singleton) | `CompilerEnumLowering` |
| referência (não-string/record/enum) | **identidade** (`if_acmp`) | *probe*: `C(1)==C(1)` → false |

Um valor de enum **não** é uma String: `Dir.N == "N"` é rejeitado em
compile-time com `SEM062` (D-ENUM207 / issue #207). Compare dois valores de
enum, ou chame `.name()` explicitamente para obter o nome.


`a.equals(b)` **funciona** em string (*probe*) mas é anti-pattern — use `==`.

---

## 11. Overload e resolução de método

- **Construtores**: sobrecarga **por aridade** (`ConstructorSet`,
  `SymbolTable.java:47-58`). Aridade errada → `SEM023`.
- **Métodos**: **sobrecarga real por assinatura** (§131 fechado 13/09,
  `18a64d45`): homônimos com aridade/tipos diferentes coexistem via
  `MethodSet` (merge no `define`, `select(argCount, argTypes)`); o typer
  (`MemberCallTyper`) escolhe o candidato e registra em `resolvedMethods()`.
  Nos backends: descritor JVM por assinatura, símbolo/slot próprio por
  overload no Native, mangle de assinatura no JS. Sem candidato compatível →
  `SEM013`/`SEM057`.
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
| `SEM001` | operador aritmético em String/não-numérico | `TypeChecker.inferBinaryResultType` |
| `SEM002` | aritmética sobre `bool` | `TypeChecker.inferBinaryResultType` |
| `SEM010` | `return` com tipo incompatível | `StatementAnalyzer` (case `ReturnStmt`) |
| `SEM011` | variável/tipo indefinido | `SemExpressionTyper` (case `IdentifierExpr`) |
| `SEM012` | atribuição incompatível (statement) | `StatementAnalyzer` (case `AssignStmt`) |
| `SEM013` | nº de argumentos ≠ parâmetros | `TypeChecker.checkArgTypes` |
| `SEM014` | argumento com tipo incompatível — hierarquia nominal + args de genérico (#688) | `TypeChecker.checkArgTypes` |
| `SEM015` | função indefinida / não-função chamada | `BuiltinCallTyper` |
| `SEM020` | atribuição a variável nunca declarada | `SemExpressionTyper` (case `AssignExpr`) |
| `SEM021` | tipo explícito ≠ tipo do inicializador | `StatementAnalyzer` (case `VarDeclStmt`) |
| `SEM023` | construtor com aridade errada | `SemExpressionTyper` (case `NewExpr`) |
| `SEM024` | redeclaração no mesmo escopo | `StatementAnalyzer` (case `VarDeclStmt`) |
| `SEM025` | método inexistente em tipo conhecido | `MemberCallTyper` |
| `SEM026` | `throw` de valor não-String | `StatementAnalyzer` (case `ThrowStmt`) |
| `SEM027` | atribuição usada como expressão | `SemExpressionTyper` (case `AssignExpr`) |
| `SEM028` | `.get()/.set()` em array | `SemMethodCallTyper` |
| `SEM029` | `toArray()` em List/Set | driver:4052 |
| `SEM030` | enum sem a constante acessada | driver:4859 |
| `SEM031` | switch-statement sobre enum não exaustivo | SwitchStmtLowerer:32 |
| `SEM032` | switch `Bool`/enum não exaustivo (expression ou **statement**, §686) sem default | `MemberResolver` / `SemExpressionTyper` |
| `SEM033` | valor `void` usado como expressão | driver:2675 |
| `SEM034` | `sublist()`/`subSet()` | driver:4067 |
| `SEM037` | reatribuição de `val` | parser (`type="val"`) + `StatementAnalyzer` |
| `SEM038` | escrita em componente de record | `StatementAnalyzer` (DD-02) |
| `SEM041` | instanciação de classe `abstract` (`new A()` e `A()`) | `SemExpressionTyper`/`BuiltinCallTyper` (SG-017) |
| `SEM042` | tipo aninhado (class dentro de class) | `ClassMemberParser.parseClassMember` (SG-016) |
| `SEM043` | `implements` sem cobrir método da interface / aridade errada | `ImplementationChecker.checkInterfaceImplementation` (SG-015) |
| `SEM044` | `main()` com tipo de retorno declarado (`Int main()`) | `SemanticAnalyzer.analyzeFunction` (SG-018) |
| `SEM045` | cláusula `throw X` com tipo desconhecido | `SemanticAnalyzer.checkThrowsClause` (SG-019) |
| `SEM046` | acesso `private`/`protected` (método ou campo) fora do permitido | `MemberCallTyper.checkMemberAccess`/`checkFieldAccess` (SG-013) |
| `SEM017` | nenhum construtor `super`/delegante com essa aridade na classe base | `ExpressionBareCallLowerer.lower` (deferido ao lowering) |
| `SEM059` | tipo de retorno do override incompatível com o do método sobrescrito | `ImplementationChecker.checkOverrideReturnCompatibility` (#326) |
| `SEM060` | chamar método de instância pelo nome da classe sem receptor (`Calc.add(1)`) | `ExpressionMethodCallLowerer.lower` (#258) |
| `SEM061` | mesmo descritor JVM redeclarado (o overload exige parâmetro/retorno DIFERENTE) | `SymbolTableBuilder.checkMethodRedeclaration` |
| `SEM064` | `interface J extends Base` onde `Base` é uma classe (interfaces só podem estender interfaces) | `SemanticAnalyzer.analyzeInterface` (#321) |
| `SEM065` | escrita em campo `final` fora do construtor da classe | `MemberCallTyper.checkFinalFieldWrite` (#331/#327; era SEM063, renumerado 17/09 — colidiu com §193) |
| `SEM066` | acessor de coleção (`get`/`put`/`size`...) chamado num receptor String (linha crua do `db.query`) | `StringReceiverGuards` (§193) |
| `SEM067` | tipo de `catch` é um primitivo Kof (primitivos não são throwable) | `CatchTypeCheck.check` (#332/#328) |
| `SEM068` | tipo de `catch` é classe de usuário que não é subclasse de `Throwable` | `CatchTypeCheck.check` (#332/#328) |
| `SEM069` | `final abstract class X` (modificadores contraditórios — sem instância nem subclasse possível) | `ClassShapeChecks.checkClassDeclaration` (#341) |
| `SEM070` | `class D extends F` onde `F` é declarada `final` | `ClassShapeChecks.checkClassDeclaration` (#339) |
| `SEM071` | instanciação de `interface` (`new I()` e `I()`) | `ClassShapeChecks.checkInstantiable` (#340) |
| `SEM072` | `add`/`push`/`append` de List com aridade errada — ex. `l.add(i, v)` (não existe inserção posicional; use `set(i, v)`) | `MemberCallTyper` (#336, 4 alvos) |
| `SEM073` | `reduce` de List com aridade errada — `reduce((a,b)->…)` sem seed (o reduce do Kof sempre recebe a lambda E uma seed, em qualquer ordem; a forma sem seed morria no `Frame.merge` da ASM) | `MemberCallTyper` (#361, 4 alvos) |
| `SEM074` | método de instância em primitivo — ex. `n.abs()`, `n.equals(o)`, `n.toChar()` (primitivos só têm `toString()` e as conversões `toInt()`/`toLong()`/`toFloat()`/`toDouble()`; comparação é `a == b`, matemática é função top-level como `math.abs(x)`; a chamada fora da lista compilava e morria no load da classe) | `SemMethodCallTyper` (#362, 4 alvos) |
| `SEM075` | campo de instância referido nu dentro de método `static` (não existe `this` implícito; o backend JVM emitia `aload_0` → `VerifyError` no load) — use uma instância, ou declare o campo `static` | `SemExpressionTyper` (#345, 4 alvos) |
| `SEM076` | `Style("<declarações>")` com propriedade fora da whitelist do kof.ui | `KofStyleParser` (D-UI-STYLE/UI007) |
| `SEM077` | `Style("<declarações>")` com declaração malformada, ou argumento não-literal | `KofStyleParser` (D-UI-STYLE/UI007) |
| `SEM078` | `Style("<declarações>")` com valor inválido para propriedade conhecida | `KofStyleParser` (D-UI-STYLE/UI007) |
| `SEM079` | uso errado de token do design system: membro inexistente de `Spacing`/`Radius`/`Border`/`Elevation`/`Typography`, ou chamada de método num namespace de token | `KofUiTokens` (Fase 10) |
| `SEM080` | subtipo (`extends`/`implements`) de tipo `sealed` declarado fora de sua unidade de compilação (o conjunto de subtipos selado é fechado) | `SealedTypeChecks` (X5.1/D-X5-SURFACE) |
| `SEM081` | `switch` (expressão ou **statement**, §686) sobre sujeito `sealed` sem um caso de subtipo direto (sem `default`) | `MemberResolver` (X5.2/D-X5-SURFACE) |
| `SEM082` | type-param `out` usado em posição de entrada (parâmetro/campo gravável) ou type-param `in` usado em posição de saída (retorno/campo/componente de record) — solidez da variância declaration-site | `VarianceChecks` (X5.3/D-TYPE-VARIANCE) |
| `SEM083` | type-param `out`/`in` passado a um parâmetro de supertipo com variância incompatível (ou invariante) em `extends`/`implements` — solidez da variância em posição de herança | `VarianceChecks` (X5.3b/D-TYPE-VARIANCE) |
| `ARITH001` | divisão/resto por zero **constante** | `ExpressionBinaryLowerer` (guarda de zero constante) |

Divisão por zero **não-constante** (`7 / z` com `z=0`) → erro de **runtime**
(`ArithmeticException` no JVM; *probe*), não compile-time.
