[English](classes.md) | [Português](classes.pt_BR.md)

# Classes, Records, Enums, Interfaces, Entities

**Status:** Stable (exceto onde etiquetado) · **Evidência:** TypeDeclarations.parseTypeDeclaration, `SymbolTableBuilder`/`SemanticAnalyzer` (defineMembers), `SymbolTable.java`

---

## 1. Classes (estado mutável)

`ebnf
class-declaration = modifiers , "class" , identifier , [ type-parameters ] ,
                    [ "extends" , type-ref ] , [ implements-clause ] , class-body
class-body = "{" , { field | method | constructor | nested-type } , "}"
`

`kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    String greeting() { return "Hello " + name }
}
var u = User("Mel", 26)     // sem `new` (new também é aceito)
u.age = 27                  // campo direto — mutável
`

- **Campos são públicos por default** (sem `private`); escrita direta.
- **Construtor**: `constructor(...)` ou bloco `{ ... }` (`parseConstructor`).
  Se nenhum construtor é declarado, um **default 0-args** é sintetizado
  (`defineClassMembers:145-147`).
- **`new` é opcional**: `User(...)` e `new User(...)` ambos válidos.
- **Instanciação sem `new` de classe com construtor de args**: `User("Mel",26)`
  resolve via *construction implícita* (`:1083-1084`).
- **Sem getters/setters** — campo direto (idiom da linguagem).
- **Sem `val` em campo** (`val x = 1` em corpo de classe → `PARSE016`,
  *probe*); use `final`.

### 1.1 `class X(...)` é record, não classe

`class User(String name, Int age) { }` **não** é classe com primary
constructor — o parser roteia para `parseRecordBody` (TypeDeclarations.parseRecordDeclaration) e
produz um **record** (imutável, accessors `u.name()`). Escrita `u.name = "x"`
**não** funciona. Para dados imutáveis, a forma canônica é `record`.
**Stable** (documentado em `AGENTS.md`, verificado).

---

## 2. Herança

`ebnf
implements-clause = "implements" , type-ref , { "," , type-ref }
`

- `extends` = **classe única** (sem múltipla herança de classe).
- `implements` = lista de interfaces.
- **Superclasse default**: `Object` (classes), `"Record"` sintético (records/
  entities).
- **Resolução de membros na hierarquia**: BFS classe→super→interfaces→
  super-super (`resolveInHierarchy:243-266`), primeiro encontrado vence.
- **`super.method()`** e **`super(args)`** (construtor) funcionam (*probe*:
  `B.g()` chamando `super.f()` → 1).
- **Override**: método na subclasse com mesmo nome **substitui** (dispatch
  virtual real no runtime: `A a = B(); a.f()` → 2, *probe*).
- **`override` modifier** é aceito mas **não validado** (não há checagem de que
  o método existe na super).
- **Subtipagem é checada** (nominal, SG-009 ✅ CORRIGIDO 10/09): classe não
  relacionada em declaração/atribuição tipada → `SEM021` — ver
  [type-system.md](type-system.md) §7.

---

## 3. Visibilidade

| Modificador | Efeito |
|---|---|
| `public` (default) | visível em todo lugar |
| `private` | visível só na classe |
| `protected` | visível no pacote/subclasse (semântica JVM) |

- **`private`/`protected` são checados em compile-time para MÉTODOS e CAMPOS**
  (`SEM046`, SG-013): chamar `c.f()` ou ler/escrever `c.x` de fora de um membro
  `private` (ou fora da hierarquia, no caso de `protected`) é erro de
  compilação; de dentro, funciona. `this.x`/`x` nu na classe declarante passa
  (owner == caller). A face de CAMPOS foi fechada 17/09 (#331/#327) — o
  `FieldSymbol` perdia os modificadores.
- **Escrita em campo `final` é checada** (`SEM065`): atribuir um campo `final`
  fora do construtor da classe declarante é erro de compilação (JVMS 4.4,
  restrição do `putfield`); o inicializador sintético `<clinit>` ainda passa.
  `FieldAccessControlTest` 7/7.
- Sem modificador → `public` (`SymbolTableBuilder` deriva as flags dos modificadores da declaração — private/protected/static/final).
- `static` campo/método: acesso por nome de classe (`S.k`, `S.k()` — *probe*).

---

## 4. Records (dados imutáveis)

`ebnf
record-declaration = modifiers , "record" , identifier , [ type-parameters ] ,
                     [ "extends" , type-ref ] , record-header ,
                     [ implements-clause ] , [ record-body ]
record-header = "(" , [ record-component , { "," , record-component } ] , ")"
record-component = [ modifiers ] , type-ref , identifier , [ "=" , expression ]
`

`kof
record Point(Int x, Int y)
var p = Point(10, 20)
println(p.x())          // accessor por método (probe)
println(p.x)            // leitura direta também funciona (probe)
println(p)              // JVM: Point[x=10, y=20]
`

- Cada componente gera: **field privado**, **accessor `name()`** (método
  0-arg), e o **construtor canônico** (`defineRecordMembers:150-182`).
- **`equals`/`hashCode`/`toString` são gerados** — `equals` compara campo a
  campo (primitivos por valor, refs por `Objects.equals`), `JvmBackend:303-397`.
- **Records são imutáveis**: não há setter; atribuição a `p.x` é **não
  suportada** (o accessor é método).
- **Record com métodos**: `record P(Int x, Int y) { Int sum() { return x+y } }`
  funciona (*probe*).
- **Record genérico**: `record Box<T>(T v)` funciona (*probe*).
- **Default em componente**: `record C(Int x = 0)` gera overloads por aridade.
- **Record implementando interfaces**: a ordem canônica do Kof é componentes
  primeiro — `record Point(Int x, Int y) implements Describable { }`; a ordem
  Java (`implements` antes da lista de componentes) também é aceita (#325).

---

## 5. Enums

`ebnf
enum-declaration = modifiers , "enum" , identifier ,
                   "{" , [ identifier , { "," , identifier } ] , "}"
`

`kof
enum Color { Red, Blue }
println(Color.Red)        // "Red" (probe)
var c = Color.Red
println(c.name())         // "Red" (probe)
`

- **Só constantes** — sem métodos, campos, construtores, corpo (`enum E { A
  String f(){…} }` → `PARSE032`, *probe*).
- **Nenhuma cláusula entre o nome e `{`** — `enum Cor extends Tudo { A, B }`
  (ou `implements …`) não é Kof válido e produz **um** `PARSE034`; a lista de
  constantes nunca é descartada em silêncio (§687).
- **Um valor de enum é uma INSTÂNCIA real (D-ENUM207, issue #207)** — o
  compilador emite uma classe enum real (`Dir.class`) com as constantes como
  instâncias `static final` criadas no `<clinit>`; `Dir.N` compila para
  `getstatic Dir.N : LDir;` (não `ldc "N"`). `getClass()` devolve `Dir` e
  `instanceof Dir` é uma checagem real. `==`/`!=` entre dois valores de enum
  compara **identidade** (`if_acmp`) — correto porque as constantes são
  singletons. Um valor de enum **não** é uma String: `Color.Red == "Red"` é
  rejeitado em compile-time com `SEM062` — compare dois valores de enum ou
  chame `.name()`.
- Métodos sintéticos: `values() → List<Dir>` (static), `valueOf(String) →
  Dir` (static, null no miss), `name() → String`, `ordinal() → Int`,
  `toString() → String` (o nome), `compareTo(Dir) → Int` (instância).
- **Switch sobre enum**: sem `default` exige cobertura total → senão `SEM031`.
- Constante não-qualificada (`Red` dentro do contexto do enum) resolve
  (`:869-876`).

---

## 6. Pattern matching (em switch)

`ebnf
pattern = type-name , identifier                          (* binding *)
        | type-name , "(" , { ( "var" | "val" )? , identifier } , ")"   (* destructuring *)
`

`kof
switch (obj) {
    case String s: println(s); break
    case Point(var x, var y): println(x + "," + y); break
    default: println("outro")
}
`

- **Binding**: `case Type var` — testa `instanceof` e vincula `var`.
- **Destructuring**: `case Point(var x, var y)` — testa tipo + extrai campos
  (records). `var`/`val` nos sub-bindings são opcionais (ExpressionParser (pattern)).
- Implementado por `KofInstanceOf` + `KofCheckCast` + `KofLoadField` por
  componente (`SwitchStmtLowerer.java:48-133`).
- **Funciona nos 3 targets** (JVM/Native/JS — testado em
  `KofPatternMatchingTest`, `KofSwitchExprE2ETest`).
- **Não há** pattern em `if`/`while`, nem `when`, nem guardas (`case P(x) if
  x>0`), nem patterns aninhados (`case List(P(a,b))`). **Unspecified** (SG-014).

---

## 7. Interfaces

`ebnf
interface-declaration = modifiers , "interface" , identifier ,
                        [ "extends" , type-ref , { "," , type-ref } ] ,
                        "{" , { method } , "}"
`

`kof
interface I { Int f() }
class C implements I { Int f() { return 1 } }
`

- Métodos sem corpo → **abstratos** (`isAbstractMethod` = body null).
- **`default Int f() { … }`** → método com corpo em interface funciona
  (*probe*).
- **Não aceita type-parameters** (`interface F<T>` → `PARSE007`, *probe*).
- **A implementação completa é checada em compile-time** (`SEM043`, *probe*):
  `class C implements I {}` sem `f()` é **erro de compilação**, não
  `AbstractMethodError` em runtime. Uma **`abstract class` pode adiar** os
  métodos da interface (`abstract class A implements I {}` compila); a obrigação é
  **transitiva** para a subclasse concreta — `class C extends A {}` sem `f()`
  falha com `SEM043` nomeando classe + método + "inherited via". Métodos `default`
  com corpo contam como satisfeitos (#213). SG-015 resolvido.
- **Não há** trait, nem interface com estado (campos), nem companion object.

---

## 8. Entities (ORM)

`ebnf
entity-declaration = modifiers , "entity" , identifier ,
                     "{" , { entity-field } , "}"
entity-field = identifier , ":" , type-ref , { "generated" | "unique" }
`

`kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}
`

- **É um record gerado + schema para `kof.orm`** (`EntityDeclarationNode.java` / `KofOrm.java`).
- Constraints `generated`/`unique` são metadados de schema (compile-time, sem
  reflection).
- Habilita o **Query DSL**: `User.query(db) { where age > 18; … }`.
- **Experimental** (domínio ORM). Ver [../stdlib-database.md](../stdlib/stdlib-database.md).

---

## 9. Classes aninhadas

`class A { class B { } }` é **erro de parse** (`SEM042`, *probe*): tipos
aninhados não existem no Kof — `ClassMemberParser.parseClassMember` rejeita uma
`type-declaration` usada como membro com "declare at top level"; o mesmo branch
cobre interface/record/entity aninhados. Prova: `nestedClassGivesCleanDiagnostic` + `topLevelClassStaysGreen` (SG-016 resolvido).

---

## 10. O que NÃO existe em classes Kof

| Ausente | Nota |
|---|---|
| `sealed`/`permits` | não são palavras-chave — removidas do lexer (SG-002, 12/09): `sealed class S {}` → `PARSE010`; feature adiada (roadmap §2.5) |
| ~~`abstract class` não-instanciável em compile-time~~ **existe** | `new A()`/`A()` em classe abstrata → `SEM041` (*probe*, SG-017 resolvido) |
| `companion object` | não existe |
| `object` (singleton) | não existe keyword `object` |
| `data class` | use `record` |
| `value class`/`inline class` | não existe |
| `operator fun` (sobrecarga de operador) | **não há** sobrecarga de operador customizada |
| `init` block | inicialização via construtor |
| `get()/set()` customizados | campo direto |
| `lateinit` | não existe |
| `open` (herança) | classes são abertas por default (sem `final` implícito) |
| construtor primário Kotlin-style | `class X(...)` = record |
| `super()` sem args implícito | construtor default chama `super()`? **Unspecified** |
