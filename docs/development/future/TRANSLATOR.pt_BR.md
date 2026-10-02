[English](TRANSLATOR.md) | [Português](TRANSLATOR.pt_BR.md)

# TRANSLATOR.md — Tradutor Kof (DESPRIORIZADO 15/09 → REABERTO 28/09, ainda em future/)

> **Dono:** 192.168.100.22 (reivindicado 13/09 ~10:05 — órfão: sem dono com IP
> no header; último código há 6 dias `84c48041`; regra dono-sem-IP=órfão da
> mantenedora).

**Status:** **REABERTO pela mantenedora (`D-DEPRIORITIZED-REOPEN`, lote 28/09)** —
ainda só-plano aqui; a promoção a trabalho corrente é uma-por-vez por
`D-FUTURE-PROMOTION`. O código já no repo fica (`Translate.java` + `TranslateLexer`/`TranslateExpr`,
`TranslateTest` 61/61) — só a *fila* para aqui. Fase F
implementada: `Translate.java` + `TranslateLexer`/`TranslateExpr`;
prova: `TranslateTest` **61/61** (re-medido 15/09 no tip `7b0bfbe0`;
o 30/30 do cabeçalho era a linha de base de 22/08) — output compila e roda; +do-while +switch
+try/catch/throw +arrays +cast/instanceof +throws +generics +constructor
+enum-body/multi-decl +interface-extends +bitwise/shift +parênteses
+tipos qualificados +assert +annotations +var +interface-default 13/09).
Subconjunto Java ampliado ainda pendente — **MAS ver a diretriz da
> mantenedora de 13/09 ~21:00 (tabela de prioridade no DOING.md): esta lane
> esta DESPRIORIZADA ("lane encerrada; gaps restantes = regra 6") — o
> subconjunto ampliado NAO e fila atual; retoma-lo so por nova decisao da
> mantenedora.**)
**Data:** 22 de agosto de 2026

---

## 1. Objetivo

Migrar código-fonte para Kof. Primeiro alvo: **Java → Kof**.

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

## 2. O que NÃO é

O translator **não funciona por substituição textual**:

```text
public → ...
class  → ...
```

NÃO fazer isso. A ferramenta deve compreender a **estrutura semântica** do
programa: tipos, herança, overloads, fluxo, exceções — e produzir Kof
idiomático, não uma transliteração.

## 3. Suporte Progressivo Planejado

- classes;
- interfaces;
- inheritance;
- generics;
- overloads;
- constructors;
- exceptions;
- annotations;
- records;
- enums;
- lambdas;
- nested classes;
- anonymous classes;
- static initialization;
- access modifiers;
- Java standard library;
- chamadas de bibliotecas externas.

## 4. Regras de Tradução Conceituais

| Padrão Java | Tradução Kof |
|---|---|
| Classe com getters/setters | Campo público |
| Classe de dados imutável | `record` |
| Utility class com métodos static | Função top-level |
| `.equals()` em strings | `==` |
| `StringBuilder` | `+` |
| Factory estática trivial | Construtor |
| `Optional` | Exceção (até `Option<T>` existir) |
| Service/Repository/Controller | Função top-level ou classe direta |

Estas regras são **conceituais** — a implementação deve derivá-las da
semântica do programa, nunca aplicá-las cegamente.

## 5. Confiança

O translator registra, por construção traduzida, a origem:

```text
Java constructo → Kof constructo
```

com nível de confiança. Construções Java sem equivalente Kof direto são
marcadas para **revisão manual**, não silenciosamente alteradas.

## 6. Fase de Implementação

A Fase F do roadmap da plataforma (`LEGACY_MIGRATION.md`).

Antes de implementar: protótipo pequeno com um subconjunto de Java
(classes, campos, métodos, if/while, strings) validado contra testes
diferenciais.

> **Estado (13/09, dono = 192.168.100.22): do-while traduzido.** `do { ... }
> while (c)` Java → `do { ... } while (c)` Kof (idiom 1:1,
> `training/idioms/control-flow.md`). Causa: `do` era keyword do
> `TranslateLexer` mas nenhum statement a consumia → `parseExprOrDecl`
> falhava com `expected ';' but found '{'`. Fix: ramo `do` em
> `Translate.parseStatement` (corpo em bloco usa o conteúdo cru, sem chaves
> duplas). Prova: `TranslateTest.doWhileTranslates` (traduz + compila no JVM
> + roda `0/1/2`). `Translate.java` 412 ≤500; `TranslateTest` 10/10.
>
> **Estado (13/09 ~10:45, dono = 192.168.100.22): switch-statement traduzido.**
> `switch (x) { case 1: ...; break; default: ... }` Java → `switch (x) {
> case 1: ... default: ... }` Kof statement (`:`, `training/idioms/control-flow.md`).
> Causa: `switch`/`case`/`break`/`default` eram keywords sem ramo no statement
> parser → `expected ';' but found '('`. Fix: `parseSwitch` (corpo de case =
> statements até próximo `case`/`default`/`}`; `break;` dropado — em Kof é
> opcional/sem fallthrough; labels múltiplos `case "a", "b":` → cases
> separados; arrow `case 3 ->` normalizado p/ `:`). Prova:
> `TranslateTest.switchStatementTranslates` (traduz + compila JVM + roda
> `one/ab`; Int e String). `Translate.java` 476 ≤500; `TranslateTest` 11/11.
>
> **Estado (13/09 ~11:00, dono = 192.168.100.22): try/catch/finally + throw
> + bare-call traduzidos.** Gap em 3 frentes, todas do mesmo cluster "corpo de
> método de verdade":
> 1. `try { ... } catch (RuntimeException e) { ... } finally { ... }` Java →
>    `try { ... } catch (String e) { ... } finally { ... }` Kof
>    (`training/idioms/errors.md`: exceções são Strings). Causa: `try`/`catch`/
>    `finally`/`throw` eram keywords sem ramo no statement parser. Fix:
>    `parseTry` (multi-catch `catch (A | B e)` → catch único; bloco vazio →
>    `{}`).
> 2. `throw new RuntimeException(msg)` → `throw msg` (exceção-String).
> 3. **Bug latente descoberto:** chamada sem receiver (`boom("x");`) não tinha
>    ramo em `parsePostfix` → `expected ';' but found '('` — o parser só
>    tratava `recv.metodo(...)`. Corrigido (bare-call), senão *qualquer* corpo
>    de try real quebrava.
> Prova: `TranslateTest.tryCatchFinallyTranslates` (traduz + compila JVM +
> roda `caught/done/t2`). **Split feito no mesmo dia:** `Translate.java`
> 526 → **255** + `TranslateStatements.java` 287 (statements extraídos p/ o
> gate ≤500 — dívida tolerada zerada, `check_500` sem aviso de Translate);
> `TranslateTest` 12/12.
>
> **Estado (13/09 ~11:45, dono = 192.168.100.22): arrays + declarações com
> `[]`/generics.** Gap: `int[] xs = new int[3]` → `expected ']' but found
> 'xs'` (o decl-parser não pulava `[]` após o tipo). **Bug latente grave
> achado no caminho:** `new int[3]` gerava `new Int[]]` (o `parseNew`
> consumia `[` e o PRIMEIRO token da dimensão antes do `parseExpr`) → Kof
> inválido (`PARSE041`); `new int[]` local nem chegava ao compilador porque
> o decl-parser já falhava. Fix: `parseExprOrDecl` com lookahead
> `isLocalDeclAhead` (generics `List<String> xs`, `Type[] name`, `Type
> name[]`); `parseNew` corrigido (size via `parseExpr`, `new T[]{...}` →
> gap explícito R6). `array initializer {...}` → **gap honesto** (não há
> literal `{...}` em Kof — `new Int[n]` + atribuições ou `listOf`).
> Prova: `TranslateTest.arrayDeclarationTranslates` (traduz + compila JVM +
> roda `10/0/0`; C-style for preservado) +
> `TranslateTest.arrayInitializerIsHonestGap` (diagnóstico explícito, R6).
> `TranslateStatements` 326 ≤500; `TranslateTest` 14/14.
>
> **Estado (13/09 ~12:00, dono = 192.168.100.22): cast + instanceof.**
> `(String) o` → `o as String` (conversão Kof) e `o instanceof String`
> preservado (Kof tem nativo, `training/language/overview.md`). Causa:
> `instanceof` não era operador de `parseRel`; `(Type)` era lido como
> parêntese de agrupamento → `expected ';' but found 'o'` /
> `expected ')' but found 'instanceof'`. Fix: ramo `instanceof` em
> `parseRel` + `isCastAhead`/`parseCast` em `parsePrimary` (lookahead
> `(Type[...]) expr`, inclusive genéricos e arrays). Prova:
> `TranslateTest.castAndInstanceofTranslate` (traduz + compila JVM + roda
> `x/is-str/3`; cast de referência e primitivo). `TranslateExpr` 387 ≤500;
> `TranslateTest` 15/15.
>
> **Estado (13/09 ~12:15, dono = 192.168.100.22): cláusula `throws`
> descartada.** `static void f() throws IOException` → `void f()` (Kof não
> declara `throws`; exceções são Strings, sempre propagáveis). Causa: o
> parser de membro esperava `{` logo após os parâmetros →
> `expected '{' but found 'throws'`. Fix: consumir `throws` + lista até
> `{`/`;` em `parseMember`. Prova: `TranslateTest.throwsClauseIsDropped`
> (traduz + compila JVM + roda `caught`). `TranslateTest` 16/16.
>
> **Estado (13/09 ~12:30, dono = 192.168.100.22): gaps honestos varargs e
> tipo aninhado.** Dois constructos Java **sem** equivalente Kof viram
> diagnóstico explícito (R6: nunca silencioso, nunca parse error confuso):
> `T...` varargs (Kof só tem builtins variádicos — `setOf`/`listOf`; função
> de usuário não) e tipo aninhado (`class`/`interface`/`record`/`enum`
> dentro de classe — SEM042 exige top level). Causa: `parseParams` lia `...`
> como erro de sintaxe; `parseMember` caía em `expected class/...`.
> Prova: `TranslateTest.varargsAndNestedTypeAreHonestGaps`. `TranslateExpr`
> 399 ≤500; `Translate.java` 270 ≤500; `TranslateTest` 17/17.
>
> **Estado (13/09 ~13:00, dono = 192.168.100.22): generics + construtores.**
> `class Box<T>`/`record Pair<A,B>`/`<T> T id(T x)` → `Box<T>`/`Pair<A,B>`/
> `T id<T>(T x)` (Kof tem generics); `new Box<Integer>(5)` → `Box(5)` (Kof
> infere). **Bug latente grave:** construtor Java (`public User(...)`) tinha
> o nome da classe lido como tipo de retorno → o ramo de campo escaneava até
> um `;` inexistente e **travava em loop infinito** (EOF) em `kof translate`;
> e o corpo do construtor era descartado (`constructor(...) {}`). Fix:
> detectar `ClassName(` antes de `parseType` → `emitConstructor` com corpo.
> Bounds `<T extends X>` → gap honesto (R6). Prova:
> `TranslateTest.constructorTranslatesWithBody` (traduz + compila JVM + roda
> `Hello Mel/26`) + `TranslateTest.genericsTranslate` (roda `5/7`).
> `Translate.java` 316 ≤500; `TranslateTest` 19/19.
>
> **Estado (13/09 ~13:15, dono = 192.168.100.22): gaps honestos
> try-with-resources e tipo qualificado.** `try (R r = ...)` (Kof sem
> AutoCloseable — RAII é plano futuro) e `new pacote.Classe(...)` (translator
> ignora imports; mapear coleções Java→stdlib Kof é decisão de design,
> regra 6) → diagnóstico explícito com sugestão de revisão manual (R6).
> Prova: `TranslateTest.varargsAndNestedTypeAreHonestGaps` estendido.
> `TranslateExpr` 419 ≤500; `TranslateStatements` 335 ≤500;
> `TranslateTest` 19/19.
>
> **Estado (13/09 ~13:30, dono = 192.168.100.22): corpo de enum + multi-decl.**
> Corpo de enum Java (`enum Color { RED; int code(){...} }`) era pulado com
> `skipBlock` num `;` sem `{` → `expected '{' but found 'int'`; agora o corpo
> é pulado token a token até `}` (Kof enum é só constantes). Multi-declaração
> `int x = 1, y = 2;` (e sem init) → statements Kof separados
> (`var x = 1 var y = 2`); antes `expected ';' but found ','`. Prova:
> `TranslateTest.enumBodyAndMultiDeclTranslate` (traduz + compila JVM + roda
> `3`). `TranslateStatements` 354 ≤500; `TranslateTest` 20/20.
>
> **Estado (13/09 ~13:45, dono = 192.168.100.22): interface `extends` +
> gaps honestos labeled/anon.** `interface B extends A` → Kof (verificado no
> compilador); antes `expected '{' but found 'extends'`. Labeled statement
> (`outer: for ...`) e classe anônima (`new Runnable(){...}`) não têm
> equivalente Kof → diagnóstico explícito (R6). Prova:
> `TranslateTest.interfaceExtendsTranslates` (traduz + compila JVM + roda
> `g/f`) + `varargsAndNestedTypeAreHonestGaps` estendido. `Translate.java`
> 335 ≤500; `TranslateTest` 21/21.
>
> **Estado (13/09 ~14:15, dono = 192.168.100.22): 3 bugs de correção
> latentes (Q4).**
> 1. **Parênteses eram descartados** — `(1+2)*3` → `1+2*3` (=7, não 9):
>    Kof gerado compilava com **semântica errada** (o pior bug). Fix:
>    `parsePrimary` preserva `( ... )` (Kof aceita parênteses redundantes).
> 2. **`&`/`|`/`^`/`<<`/`>>`/`>>>` dropados silenciosamente** pelo lexer
>    (só `&&`/`||` emitiam token) → operandos colavam e o parser quebrava.
>    Fix: tokens `AMP`/`CARET`; shifts combinados de `LT`/`GT` no parser
>    (evita conflito com generics `List<String>`); `parseBitAnd`/
>    `parseBitOr`/`parseShift` com a precedência do parser Kof
>    (`KofFormatter.precOf`).
> 3. **Tipo qualificado** `java.util.Map<...>` → `Map<...>` (stripa pacote;
>    `Map`/`List`/`Set` são builtins Kof). `new java.util.ArrayList()` segue
>    gap honesto (mapear coleção = decisão de design).
> Prova: `TranslateTest.parenthesesPreservePrecedence` (roda `9/-3`),
> `bitwiseAndShiftTranslate` (roda `2/7/5/24/3/3/2147483644`),
> `qualifiedTypeNamesAreStripped`. `TranslateExpr` 471 ≤500;
> `TranslateTest` 24/24.
>
> **Estado (13/09 ~14:30, dono = 192.168.100.22): assert + gap de `for` com
> vírgula.** `assert cond;` / `assert cond : msg;` Java → `assert(cond)` /
> `assert(cond, msg)` Kof (primitive de teste, é função — `AssertE2ETest`);
> antes `expected ';' but found 'x'`. `for` C-style com init/incr múltiplos
> (`for (int i=0, j=3; ...; i++, j--)`) não tem equivalente Kof (for não
> aceita vírgula — PARSE041; desugar p/ while muda o fluxo do `continue`) →
> gap honesto (R6). Prova: `TranslateTest.assertTranslates` (roda `1`) +
> `forMultipleInitIncrIsHonestGap`. `TranslateStatements` 399 ≤500;
> `TranslateTest` 26/26.
>
> **Estado (13/09 ~14:45, dono = 192.168.100.22): annotations descartadas.**
> `@Override`, `@Deprecated`, `@SuppressWarnings("x")` (com args) em tipo e
> membro → descartadas (Kof ignora; verificado no compilador); antes
> `expected class/... found '@'`. Fix: `skipAnnotationsAndModifiers` no
> `parseTypeDeclaration` + ramo `@` no `parseMember`. Prova:
> `TranslateTest.annotationsAreDiscarded` (traduz + compila JVM + roda
> `x/f`). `Translate.java` 377 ≤500; `TranslateTest` 27/27.
>
> **Estado (13/09 ~15:00, dono = 192.168.100.22): `var`, `default` de
> interface, constante de interface.** (1) `var x = 1;` local Java → `var x =
> 1` Kof (`var` é reservado idêntico; antes `expected ';' but found 'x'`).
> (2) interface `default`/corpo de método — **CORRIGIDO 13/09 ~16:45:** a
> nota anterior ("Kof aceita corpo em interface") era **verificação FALSA**
> (Q5): Kof **ignora** o corpo e o implementador falha com `SEM043`
> (`kof check` no binário). Agora é **gap honesto R6** (sem default method em
> Kof). (3) constante de interface (`int X = 1;` implícito
> `static final`) → **gap honesto R6**: Kof aceita declarar mas não resolve
> (`I.X`/`C.X` → `SEM025`), sem equivalente direto. Prova:
> `TranslateTest.varLocalTranslates` (roda `1/hi`),
> `interfaceDefaultMethodIsHonestGap` (substitui o teste falso),
> `interfaceAbstractSignatureTranslates` (roda `7`),
> `interfaceConstantIsHonestGap`.
> `Translate.java` 396, `TranslateStatements` 404 ≤500; `TranslateTest` 30/30.
>
> **Estado (13/09 ~15:50, dono = 192.168.100.22): array-initializer em CAMPO
> (bug latente Q4).** `int[] xs = {1,2,3}` como **campo** de classe (não
> local) não passava pelo guard de gap honesto que o local já tinha → o
> translator emitia `Int[] xs = {` **truncado** = Kof inválido silencioso
> (viola R6; o pior tipo, output quebrado sem diagnóstico). Fix: `Translate.
> parseMember` detecta `{` após `=` e lança o mesmo `TranslateException` R6 do
> local. Prova: `arrayInitializerIsHonestGap` estendido (campo + local) —
> `TranslateTest` 33/33; probe no binário `kof translate` → diagnóstico
> explícito, sem truncamento. `Translate.java` 406 ≤500; `check_500` OK.
>
> **Estado (13/09 ~16:00, dono = 192.168.100.22): bloco de inicialização de
> instância (bug latente Q4).** `{ ... }` não-static dentro de classe rodava
> antes de todo construtor no Java; o translator dropava **silenciosamente**
> (só se documentava `static {}` skip), mudando comportamento sem diagnóstico
> (viola R6). Fix: `Translate.parseMember` distingue `static {}` (skip,
> consistente com campo estático) de instância (→ `TranslateException` R6,
> orienta mover p/ `constructor`). Prova:
> `TranslateTest.instanceInitializerBlockIsHonestGap` (instância gap + static
> skipado) — `TranslateTest` 34/34; probe no binário → diagnóstico explícito.
> `Translate.java` ~420 ≤500; `check_500` OK.
>
> **Estado (13/09 ~16:15, dono = 192.168.100.22): literais numéricos Java
> (bug latente Q4).** O lexer só consumia dígitos+ponto → `10L` virava `10`
> `L`, `1.5e3` → `1.5` `e3`, `1.5f` → `1.5` `f`, `0x1F` → `0` `x1F` — todos
> davam `expected ';' but found '…'` (parse error confuso). Fix: `scanNumber`
> consome decimal/hex/bin, `_`, ponto, expoente `e/E`/`p/P`, sufixos
> `l/L/f/F/d/D`; o texto é preservado (Kof aceita as mesmas formas — probe
> `kof check`), **exceto** `_` que o Kof rejeita (PARSE043) → removido (mesmo
> valor). Prova: `TranslateTest.javaNumericLiteralsTranslate` (traduz +
> compila JVM + roda `68088.5`, oracle javac) — `TranslateTest` 35/35; probe
> binário nos 7 literais. `check_500` OK.
>
> **Estado (13/09 ~16:30, dono = 192.168.100.22): escapes de string/char +
> `final` local/param (bugs latentes Q4).** (1) O lexer **decodificava** os
> escapes Java para chars reais e o emitter os reemitia **crus** → Kof
> inválido/semântica errada: `"say \"hi\""` virava `"say "hi""` (PARSE043) e
> `"path\\x"` virava `"path\x"` (Kof engole a barra → `pathx` ≠ Java
> `path\x`). Fix: `TranslateLexer.escapeKofString/escapeKofChar` re-escapam
> `\ " \n \t \r` no emit (helpers no lexer p/ `TranslateExpr` seguir ≤500).
> (2) `final` em local (`final int y = 2;`) e em parâmetro (`p(final int x)`)
> dava parse error — Kof não tem `final` local/param (vars mutáveis); o
> modificador é descartado. Prova:
> `TranslateTest.stringEscapesRoundTripToValidKof` (roda `say "hi"`/`path\x`)
> e `finalLocalAndParamTranslate` (roda `3hi`) — `TranslateTest` 37/37.
> `TranslateExpr` 500 (limite), `TranslateStatements` 451 ≤500; `check_500` OK.
>
> **Estado (13/09 ~16:45, dono = 192.168.100.22): `default` de interface +
> campos estáticos (bugs latentes Q4).** (1) **CORREÇÃO da unidade `3ab4c99e`:**
> a nota anterior dizia que "Kof aceita corpo em interface" — **falso**
> (re-verificado no binário: Kof ignora o corpo e o implementador falha com
> `SEM043`). Método de interface com corpo (`default`/`static`) agora é **gap
> honesto R6**; assinatura abstrata segue `Type m(): Type` (probe: `7`).
> (2) Campo `static` Java era **skipado silenciosamente** → referência virava
> `Undefined variable or type` (SEM011) = Kof inválido. Agora emite `static`
> e, nas funções **hoisted** (métodos `static`/`main` promovidos a top-level),
> qualifica a ref nua `X` → `Classe.X` via `TranslateStatics` (novo: varredura
> + qualificação segura, sem tocar strings/`.X`/parâmetro shadow). Prova:
> `interfaceDefaultMethodIsHonestGap`, `interfaceAbstractSignatureTranslates`
> (roda `7`), `staticFieldsTranslateAndQualifyInHoistedFns` (roda `5/hi/5`) —
> `TranslateTest` 39/39. `Translate.java` 443, `TranslateStatics` 134 ≤500.
>
> **Estado (13/09 ~17:30, dono = 192.168.100.22): `this(...)`, wildcard
> genérico, corpo de lambda em BLOCO + split `TranslateTypes` (bugs latentes
> Q4).** Três construtos Java que produziam Kof inválido/parse error confuso
> agora têm tratamento explícito, e o `TranslateExpr` (que estourou 500 com
> os fixes) foi aliviado por extração. (1) **Delegação de construtor
> `this(...)`** → Kof não tem (probe: `variable 'this' is not a function` =
> SEM015) → **gap honesto R6**. (2) **Wildcard genérico** `? extends/super`
> → Kof rejeita (PARSE086) → **gap honesto R6** (em vez de emitir `? extends
> Number`). (3) **Corpo de lambda em BLOCO** `() -> { ... }` → o parser só
> aceitava expressão (`expected ';' but found 'System'`); Kof **aceita** bloco
> (`() -> { counter = counter + 1 }` verificado no binário) → agora traduz
> `(params) -> { stmts }`. (4) **Split:** helpers estáticos de tipo
> (`isModifier`/`isTypekeyword`/`isPrimitiveOrType`/`isKeyword`/`kofType`)
> extraídos de `TranslateExpr` (500→487) para `TranslateTypes.java` (50).
> Prova: `constructorDelegationIsHonestGap`, `wildcardGenericIsHonestGap`,
> `lambdaBlockBodyTranslates` (roda `14` no Kof gerado) — `TranslateTest`
> **42/42**; gate 4-módulos pós-rebase **1497/0 + 33/0 + 5/0 + 194/0**, BUILD SUCCESS.
> `check_500` OK (sem aviso de `Translate*`).
>
> **Nota (achado na caça Q4, registrado §177 — ✅ CORRIGIDO pela lane
> bugs-and-gaps `192.168.100.15`):** lambda com corpo em BLOCO que retorna uma
> **local declarada no próprio bloco** era tipada VOID (`SEM033`) **quando o
> módulo continha uma classe** (`main`+`class C {}`); sem a classe o mesmo
> programa passava. Raiz: `ExpressionTyper.firstReturnValueType` não registrava
> os `VarDeclStmt` do bloco no escopo ao inferir o retorno. Fix da lane
> bugs-and-gaps: escopo cópia mutável com os locais do corpo; prova
> `CoreRegressionE2ETest.lambdaReturnLocalVar` (4 targets). O teste
> `lambdaBlockBodyTranslates` usa `return n + 1` e segue válido.
>
> **Estado (13/09 ~18:30, dono = 192.168.100.22): switch-EXPRESSÃO +
> method-ref/text-block/instanceof-pattern/import-static/`Math.` + split
> `TranslateSwitch` (bugs latentes Q4).** Oito construtos Java que davam
> parse error confuso ou **Kof inválido silencioso** agora têm tratamento:
> (1) **switch-EXPRESSÃO** `return switch (x) { case 1, 2 -> 10; default -> 0; }`
> → traduz para o switch-expr Kof (`case L -> expr`, `training/idioms/
> control-flow.md`); multi-label expande em cases separados (Kof rejeita
> lista, PARSE078) e a forma colon+`yield` vira `case L -> expr`; corpo de
> case em BLOCO é **gap honesto R6** (Kof exige UMA expressão, PARSE094) —
> antes `expected ';' but found '{'`. (2) **Method reference** `Tipo::metodo`
> → Kof só tem lambda → **gap honesto R6** (antes `expected ')' but found ':'`).
> (3) **Text block** `"""…"""` → Kof não tem → **gap honesto R6** (antes o
> lexer lia `""` vazio e reabria). (4) **`instanceof` binding pattern**
> `o instanceof String s` → **gap honesto R6** (antes `expected ')' but found
> 's'`). (5) **Tipo qualificado em EXPRESSÃO** `java.util.List.of(...)` →
> antes emitia Kof inválido (`java` undefined = SEM011) **em silêncio** →
> **gap honesto R6** (mapear Java→stdlib é decisão de design, regra 6).
> (6) **`import static` de JDK** `import static java.lang.Math.max` + `max(3,4)`
> → emitia `max(3, 4)` sem diagnóstico (SEM011) → **gap honesto R6**;
> static import de classe do próprio programa passa (o static method vira
> função top-level Kof e resolve). (7) **Literal `.5`** → normalizado para
> `0.5` no lexer (Kof exige o zero; `.5` é PARSE041). (8) **Receptor
> `Math.`** `Math.max(3,4)` / `Math.PI` → antes emitia `Math.max(...)` /
> `Math.PI` (Kof inválido = SEM011 **silencioso**); Kof expõe a stdlib em
> `math.*`, mas `math.min/max/abs` são **Int-only** (SEM025 p/ Double, sem
> widening) e o translator não tem tipos p/ escolher o overload → **gap
> honesto R6**. (9) **`~x`** (complemento bit a bit) → Kof não tem `~`
> (PARSE041), mas `~x == -x - 1` é exato em complemento de dois → emite
> `(-x - 1)`; o **lexer agora REJEITA** caractere inesperado em vez de
> dropá-lo em silêncio (era a raiz do `~` sumir). (10) **Classe LOCAL**
> (`class`/`interface`/`enum`/`record` dentro de método) → Kof não tem
> tipos aninhados (SEM042) → **gap honesto R6** (antes `expected ';' but
> found 'B'`). (11) **Instrução vazia `;`** → descartada (antes
> `expected ';' but found 'return'`). (12) **`main(String[] args)`** →
> `main(args)`: os params eram **DESCARTADOS** e o corpo podia referenciar
> `args` → Kof inválido silencioso; Kof aceita `main(String[] args)`
> (verificado no binário). **Splits:** o parser de switch-expressão (~55
> linhas) saiu para `TranslateSwitch.java` (77) e `new` para
> `TranslateNew.java` (72) — `TranslateStatements` 528→470 e
> `TranslateExpr` 554→495, ambos ≤500. Prova: `switchExpressionTranslates`
> (roda `10/0` no Kof gerado), `methodReferenceIsHonestGap`,
> `textBlockIsHonestGap`, `instanceofBindingPatternIsHonestGap`,
> `qualifiedTypeInExpressionIsHonestGap`, `jdkStaticImportIsHonestGap`,
> `ownStaticImportPassesThrough`, `mathReceiverIsHonestGap`,
> `leadingDotLiteralIsNormalized` (roda `0.5`), `bitComplementTranslates`
> (roda `-6/-1`), `emptyStatementIsSkipped` (roda `1`),
> `localClassIsHonestGap`, `mainArgsArePreserved` (roda `0`) —
> `TranslateTest` **55/55**; `check_500` OK **sem dívida de translate**.
>
> **Gap de design conhecido (regra 6, NÃO corrigido):** outros **receptores de
> classe JDK** (`Integer.parseInt`, `Long.valueOf`, `Objects.requireNonNull`,
> `Collections.sort`, `Arrays.asList`, `String.valueOf`, `StringBuilder`…) e
> `new` de tipos JDK não têm mapeamento p/ a stdlib Kof — o translator emite o
> nome como está, que falha com SEM011 (diagnóstico downstream, não
> silencioso). Mapear Java→stdlib é decisão de design (regra 6); fica
> registrado como fila futura, não como edição.
>
> **Estado (13/09 ~19:30, dono = 192.168.100.22): `~x`, classe local, `;`
> vazio, `main(args)` + split `TranslateNew` (2ª varredura de probes Q4).**
> (1) **`~x`** (complemento bit a bit): Kof não tem `~` (PARSE041); a
> identidade `~x == -x - 1` é exata em complemento de dois → emite
> `(-x - 1)`. A raiz era o **lexer dropar `~` em silêncio** → agora o
> `default` do lexer **rejeita** caractere inesperado com diagnóstico
> (antes: Kof truncado silencioso). (2) **Classe LOCAL** dentro de método →
> Kof não tem tipos aninhados (SEM042) → **gap honesto R6**. (3) **Instrução
> vazia `;`** → descartada. (4) **`main(String[] args)`** → `main(args)`: os
> params eram **descartados** e o corpo podia referenciar `args` → Kof
> inválido silencioso; Kof aceita `main(String[] args)` (verificado no
> binário). **Split:** `new` → `TranslateNew.java` (72); `TranslateExpr`
> 554→495 (≤500). Prova: `bitComplementTranslates` (roda `-6/-1`),
> `localClassIsHonestGap`, `emptyStatementIsSkipped` (roda `1`),
> `mainArgsArePreserved` (roda `0`) — `TranslateTest` **55/55**; gate
> 4-módulos **1499/0 + 33/0 + 5/0 + 207/0**, BUILD SUCCESS; `check_500` OK
> **sem dívida de translate**.

> **Estado (13/09 ~20:00, dono = 192.168.100.22): escapes unicode/char, lambda
> 1-param, enum/record/init/abstract (3ª varredura de probes Q4).**
> (1) **Escapes de string/char**: o lexer dropava a barra de `\uXXXX` e emitia
> o texto cru (Kof inválido); `\b`/`\f` e octais iam crus; `char '\n'`/`'\''`/
> `'\uXXXX'` caíam no fallthrough e eram **dropados**. Agora `decodeEscape` +
> `Esc(ch,len)` decodificam `\n \t \r \b \f \" \' \\`, `\uXXXX` e octal, em
> string E char; o emit re-escapa controle `<0x20`/`0x7F` como `\uXXXX` (Kof
> suporta unicode escape). (2) **Lambda de 1 parâmetro SEM parênteses**
> (`x -> x + 1`): Kof exige parênteses (PARSE041) → emite `(x) -> x + 1`
> (antes: `expected ';' but found '->'`). (3) **Enum com construtor/corpo de
> constante** (`A(1)`, `A { … }`) e **record com corpo** (construtor compacto/
> accessors) → **gap honesto R6** (antes: pulados em SILÊNCIO = validação
> sumia). (4) **`static {}` e bloco de instância** → gap R6; **método
> `abstract`/`native` sem corpo** em classe → gap R6 (antes: dropado silencioso
> → chamada virava SEM011). Prova: `unicodeAndControlEscapesRoundTrip` (roda
> `A/true/3/true/true/true/true`), `singleParamLambdaWithoutParensTranslates`,
> `enumBodyIsHonestGap`, `recordBodyIsHonestGap`, `abstractMethodIsHonestGap`,
> `instanceInitializerBlockIsHonestGap` (static incluso) — `TranslateTest`
> **60/60**; gate 4-módulos **1499/0 + 33/0 + 5/0 + 212/0**, BUILD SUCCESS;
> `check_500` OK (`TranslateExpr` 506 = dívida tolerada ≤599; split planejado).
