# Known Bugs — handoff para o próximo agente

> **Data:** 10/09/2026 · **Versão:** 0.3.1-beta. Este arquivo existe para que
> um agente (ou humano) pegue os bugs sem precisar redescobri-los. **Não são
> características** — são bugs reais com reprodução mínima.
>
> **Estado (varredura de 08/09, JVM + KofJS + interpretador):**
>
> | | |
> |---|---|
> | Abertos e atacáveis em JVM/JS | **5** — bugs 39, 45, 62, 63, 64 |
> | Abertos, só reproduzíveis no Native | **5** — bugs 46, 48, 50, 59, 61 |
> | Paridade interpretador × compilados (semântica `==` congelada — regra 6) | **1** — bug 94 (NaN/±0.0 `==` de Double no SCRIPT) |
> | Operadores relacionais NaN cross (congelados — regra 6) | **1** — bug 101 (`<`/`<=`/`>=` com NaN: riscv IEEE vs x86/JVM quirk `dcmpg`) |
> | **Corrigidos na sessão de paridade absoluta 11/09** | **6** — bugs 96 (SEM052), 98 (SEM053), 100 (SEM051+fold), 44-residual, 102 (from-idx), 103 (SEM054) — todos com o MESMO comportamento nos 5 alvos |
> | Verificados corrigidos em 08/09 | **19** — bugs 1–8, 10–17, 19, 20, 26 |
> | Não reverificados (faltou ambiente/setup) | bugs 9, 18, 21, 22, 23 |
>
> Os bugs marcados `✅ VERIFICADO CORRIGIDO 08/09` foram reproduzidos contra o
> build atual e **não** falham mais — parte virou saída correta, parte virou
> diagnóstico limpo. O Native não pôde ser reverificado nesta rodada (host
> arm64/macOS sem toolchain x86_64-linux).
>
> **Como pegar:** reproduza o snippet (`kof run --target=jvm`), fix no CÓDIGO
> (não no corpus), adicione teste E2E que falha antes/passa depois, atualize
> este arquivo (mova para "resolvidos" com o commit) e remova as notas do
> corpus.

---

## JVM / Native / JS — bugs por alvo

### 1. `throw <valor não-String>` gera bytecode inválido no JVM — ✅ VERIFICADO CORRIGIDO 08/09 (diagnostico limpo: diagnostico proprio (`throw` exige String))

- **Sintoma:** `throw 42` ou `catch (Int e)` compila, mas o `.class` falha no
  load (`ClassFormatError`, disfarçado de "JavaFX launcher error").
- **Reprodução:**
  ```kof
  main() {
      try { throw 42 } catch (String e) { print("texto") }
      println("done")
  }
  ```
- **Causa provável:** backend JVM emite o `throw` primitivo sem o wrap em
  `RuntimeException` que o `catch (String e)` espera → constant pool inválido.
- **O que deveria acontecer:** exceções são Strings — **rejeitar `throw
  <não-String>` / `catch <não-String>` em compile-time** (SEM0xx), ou suportar
  wrap/unwrap tipado.
- **Arquivos:** `CompilerDriver.java` (lowering de Throw/catch), `JvmBackend.java`.

---

### 2. Compound assignment `-=`, `/=`, `%=` produzem resultado ERRADO (JVM e Native) — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `8` / `5` / `1`)

- **Sintoma:** `x -= 2` vira **sinal invertido**; `x /= 2` vira **0**;
  `x %= 3` vira resto errado. `+=` e `*=` funcionam.
- **Reprodução:**
  ```kof
  main() {
      var a = 10; a -= 2; println(a)   // -8 (deveria 8)
      var b = 10; b /= 2; println(b)   // 0  (deveria 5)
      var c = 10; c %= 3; println(c)   // 3  (deveria 1)
  }
  ```
- **Verificado:** idêntico em JVM e Native (bug de IR, não de backend).
- **Causa provável:** lowering do `AssignmentExpr` compound no CompilerDriver
  (ordem dos operandos / mapeamento de op `-=`/`/=`/`%=` errado).
- **Arquivos:** `CompilerDriver.java` (branch `+=`/`-=`/`*=`/`/=`/`%=` no emit
  de assignment), `IRNodes.java`/backends.

---

### 3. `s += "x"` (compound de String) dentro de loop CRASHA o compilador — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `xxxxx`)

- **Sintoma:** `while (i < 100) { s += "x" }` → `RuntimeException: frame crash
  em Default/Main.main (Index 0 out of bounds for length 0)` no
  `JvmBackend.emitClass`.
- **Reprodução:**
  ```kof
  main() {
      var s = ""
      var i = 0
      while (i < 100) { s += "x"; i = i + 1 }
      println(s.length)
  }
  ```
- **Controles:** `s = s + "x"` em loop funciona; `acc += 1` (int) em loop
  funciona. O crash é específico do **compound de String** em ponto de merge
  de frames.
- **Causa provável:** mesma classe do COMP002 de call-void (push/pop
  desbalanceado na emissão do concat compound → merge de frames quebra). Ver
  `training/anti-patterns/void-call-merge-crash.md`.
- **Arquivos:** `JvmBackend.java` (emissão do concat em statement), `CompilerDriver.java`.

---

### 4. `switch` com String gera bytecode inválido no JVM — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `A`)

- **Sintoma:** `switch (s) { case "a": ... }` compila mas falha no load
  (`ClassFormatError`/JavaFX launcher error).
- **Reprodução:**
  ```kof
  main() {
      var s = "b"
      switch (s) {
          case "a": println("A"); break
          case "b": println("B"); break
          default: println("?")
      }
  }
  ```
- **Causa provável:** backend JVM não emite o dispatch de `switch` sobre
  `String` (deveria usar `hashCode`+`equals` ou cadeia de comparações).
- **Arquivos:** `JvmBackend.java` (emissão de `switch`), `CompilerDriver.java`.

---

### 5. Cast de ponto flutuante → inteiro gera bytecode inválido — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `3`)

- **Sintoma:** `3.9 as Int`, `3.9 as Long`, `2.7f as Int` compilam mas falham
  no load (JavaFX/ClassFormatError). `Long as Int` e `Int as Char` funcionam
  (01/09).
- **Reprodução:**
  ```kof
  main() {
      var d = 3.9
      var i = d as Int   // ClassFormatError
      println(i)
  }
  ```
- **Causa provável:** emissão do cast FP→Int (D2I/F2I/D2L) errada no
  `JvmBackend` (o fix de 01/09 cobriu I2C/L2I mas não FP→I/L).
- **Arquivos:** `JvmBackend.java` (emitCheckCast), `CompilerDriver.java`.

---

### 7. Argumento de tipo nullable em chamada genérica não parseia — ✅ CORRIGIDO (teste `CoreRegressionE2ETest.nullableGenericArgumentInCall`)

- **Sintoma:** `listOf<String?>()` → PARSE041 (Unexpected token `?`).
  `List<String?> l = listOf()` funciona.
- **Reprodução:**
  ```kof
  main() {
      var l = listOf<String?>()   // PARSE041
  }
  ```
- **Causa provável:** o parser de `type arguments` em method call não consome
  o `?` (o `parseTypeRef` consome, mas o caminho de type-args não).
- **Arquivos:** `Parser.java` (parseTypeArguments em method call).

---

### 8. Tipo de função em argumento genérico não parseia — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `0` (parseia))

- **Sintoma:** `listOf<(Int) -> Int>()` → PARSE (Unexpected token).
- **Reprodução:**
  ```kof
  main() {
      var fs = listOf<(Int) -> Int>()   // não parseia
  }
  ```
- **Causa provável:** o parser de type-args não aceita `(T) -> R` como
  argumento de tipo.
- **Arquivos:** `Parser.java`.

---

### 9. Captura mutável no Native: ler boxed dentro da lambda após mutação
EXTERNA produz lixo — ✅ CORRIGIDO (teste `NativeE2ETest.nativeLambdaMutableCapture` → `15\n25\n3`)

- **Sintoma:** `var f = (x) -> x + offset; offset = 20; f(5)` retorna lixo no
  Native (JVM correto). A direção "lambda escreve" funciona.
- **Reprodução:**
  ```kof
  main() {
      var offset = 10
      var f2 = (x: Int) -> x + offset
      println(f2(5))        // JVM 15 / Native lixo
      offset = 20
      println(f2(5))        // JVM 25 / Native lixo
  }
  ```
- **Causa provável:** `NativeBackend.resolveFieldOffset` resolve o layout do
  box contra a classe da lambda (fallback `HEADER_SIZE`).
- **Arquivos:** `NativeBackend.java`.

---

## Descobertos na investigação agressiva (02/09, rodada 2)

### 10. `!` (NOT lógico) como VALOR de expressão sempre retorna `true` — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `false`)

- **Sintoma:** `println(!true)` → `true`; `var x = !false` → `true`. Em
  **condição** de `if`, `!` funciona (`if (!ativo)` ok).
- **Reprodução:**
  ```kof
  main() {
      var x = !true      // true (deveria false)
      println(x)
      println(!false)    // true (deveria true — coincidentemente certo)
      println(!(1 > 2))  // true (deveria true — coincidência)
  }
  ```
- **Verificado:** JVM e Native — o valor emitido é sempre `1` (true).
- **Causa provável:** lowering do unário `!` em contexto de expressão
  (argumento/atribuição) não nega; só o caminho de condição (jump negation)
  funciona.
- **Arquivos:** `CompilerDriver.java` (UnaryExpr `!`), backends.

---

### 11. `==` em records usa igualdade de REFERÊNCIA no JVM (não `equals`) — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `true` (igualdade de conteudo))

- **Sintoma:** `Ponto(1,2) == Ponto(1,2)` → `false` (deveria `true`);
  `a.equals(b)` → `true`.
- **Reprodução:**
  ```kof
  record Ponto(Int x, Int y)
  main() {
      var a = Ponto(1, 2)
      var b = Ponto(1, 2)
      println(a == b)      // false (deveria true — equals é gerado)
  }
  ```
- **Causa provável:** `==` em tipos de referência emite `if_acmpeq`
  (referência), sem despachar para o `equals` gerado do record.
- **Arquivos:** `CompilerDriver.java`/`JvmBackend.java` (comparação `==` de
  referenciais).

---

### 12. Assignment encadeado (`var c = a = b`) crasha o compilador — ✅ VERIFICADO CORRIGIDO 08/09 (diagnostico limpo: `SEM027` (atribuicao e statement, nao expressao))

- **Sintoma:** `var c = a = b` → `Internal compiler error: frame crash
  COMP002 (Index -1 out of bounds)`.
- **Reprodução:**
  ```kof
  main() {
      var a = 1
      var b = 2
      var c = a = b      // COMP002
      println(c)
  }
  ```
- **Causa provável:** a expressão de atribuição como RHS de outra deixa a
  pilha desbalanceada no emit (AssignmentExpr dentro de AssignmentExpr).
- **Arquivos:** `CompilerDriver.java` (emit de AssignmentExpr).

---

### 13. Cast (`x as T`) usado como operando de aritmética crasha o compilador — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `4`)

- **Sintoma:** `var y = (x as Int) + 1` → `frame crash COMP002 (-1)`.
  `println(x as Int)` isolado funciona.
- **Reprodução:**
  ```kof
  main() {
      var x = 5
      var y = (x as Int) + 1    // COMP002
      println(y)
  }
  ```
- **Verificado:** não é específico de Char — `(Int as Int) + 1` também crasha.
- **Causa provável:** o cast (KofCheckCast) deixa um valor na pilha que o
  binário aritmético assume desbalanceado (push extra).
- **Arquivos:** `CompilerDriver.java`/`JvmBackend.java` (emit de `as` + binário).

---

### 14. `Map.size` (propriedade) → `NoSuchFieldError` confuso em runtime — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `1`)

- **Sintoma:** `m.size` (sem parênteses) compila mas falha em runtime com
  `NoSuchFieldError: java.util.HashMap does not have member field 'size'`.
  `m.size()` (método) funciona. `List.size` (propriedade) funciona.
- **Reprodução:**
  ```kof
  main() {
      var m = mapOf("a", 1)
      println(m.size)     // NoSuchFieldError (use m.size())
  }
  ```
- **Inconsistência:** `List` expõe `.size` (propriedade) e `Map` só `size()`
  (método) — a forma propriedade deveria funcionar (ou rejeitar em
  compile-time com diagnostic claro, não NoSuchFieldError).
- **Arquivos:** `CompilerDriver.java` (field-access dispatch de Map).

---

### 15. Primitivo não é atribuível a `Object` (sem auto-boxing) — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `42` (auto-boxing))

- **Sintoma:** `Object n = 42` → `SEM021 type mismatch: cannot assign int to
  Object`. `Object o = "kof"` funciona (String→Object).
- **Reprodução:**
  ```kof
  main() {
      Object n = 42        // SEM021 — primitivo não boxa para Object
      println("done")
  }
  ```
- **Impacto:** impede pattern matching/`instanceof` sobre primitivos via
  `Object` (só funciona com referências). É uma **limitação**, não crash —
  mas vale decisão de design (auto-boxing ou diagnostic melhor).
- **Arquivos:** `SemanticAnalyzer.java` (isAssignable primitivo → Object).

---

### 16. `List.toArray()` quebra em JVM e Native (retorno de array) — ✅ VERIFICADO CORRIGIDO 08/09 (diagnostico limpo: diagnostico com posicao (`metodo 'toArray' nao existe`))

- **Sintoma:** JVM → `ClassFormatError` (disfarçado de JavaFX launcher error);
  Native → `undefined reference to 'List_toArray'` no link.
- **Reprodução:**
  ```kof
  main() {
      var arr = listOf(1, 2, 3).toArray()
      println(arr.length)
      println(arr[1])
  }
  ```
- **Causa provável:** o retorno de tipo array (`Int[]`) de um método da
  stdlib não é tratado pelos backends (JVM emite constant-pool inválido para
  o tipo array; Native não gera o símbolo `List_toArray`).
- **Arquivos:** `JvmBackend.java`, `NativeBackend.java`, runtime nativo
  (`kof_c`/`NativeRuntime`).
- **Verificado 02/09 pós-merge riscv64** — persiste.

---

### 17. Array `.get()`/`.set()` (não existem) são aceitos e geram saída quebrada — ✅ VERIFICADO CORRIGIDO 08/09 (diagnostico limpo: `SEM028` (use `arr[i]` / `arr[i] = v`))

- **Sintoma:** a API real de array é o operador `arr[i]` / `arr[i] = v`
  (ver `training/language/arrays.md`). Porém `arr.get(0)` / `arr.set(0, 5)`
  **compilam** e produzem: JVM → `ClassFormatError: Illegal class name ""`;
  Native → `undefined reference to 'get'/'set'`.
- **Reprodução:**
  ```kof
  main() {
      var arr = new Int[3]
      arr.set(0, 5)     // JVM: ClassFormatError / Native: undefined ref 'set'
      println(arr.get(0))
  }
  ```
- **O que deveria acontecer:** rejeitar em compile-time com diagnostic claro
  ("array não tem método get()/set(); use arr[i]").
- **Causa provável:** método call sobre tipo array cai no caminho genérico de
  dispatch em vez de emitir o array load/store.
- **Arquivos:** `CompilerDriver.java`/`SemanticAnalyzer.java` (dispatch sobre
  array type), `JvmBackend.java`, `NativeBackend.java`.

---

## Investigação de usuários (02/09, rodada 3) — packages, lambda, kof-ui

### 18. kof-ui: ID de widget é reutilizado após `remove()` → colisão de nós — ✅ CORRIGIDO (teste `KofJsE2ETest.uiWidgetIdsUseMonotonicCounter`)

- **Sintoma:** `kofUiLabelNew`/`Link`/`Image`/`Icon`/`Font` geram o ID com
  `Object.keys(window.__kofNodes).length + 1`. Como `remove()` faz
  `delete __kofNodes[id]`, o length encolhe e o próximo widget **reusa um ID
  que já pertence a um nó vivo**, sobrescrevendo-o.
- **Reprodução (JS/DOM):**
  1. `var a = Label("A")` → id 1; `var b = Label("B")` → id 2
  2. `a.remove()` → `delete __kofNodes[1]` (length volta a 1)
  3. `var c = Label("C")` → id **2 de novo** → `__kofNodes[2]` agora é C; o
     handle de `b` passou a apontar para C.
- **Causa provável:** ID alocado por `length + 1` em vez de contador
  monotônico (`kofUiSeq` já existe e é monotônico — usar a mesma fonte).
- **Arquivos:** `JsBackend.java` (runtime JS `kofUiLabelNew` etc., ~linhas
  3219, 3255, 3311, 3378, 3463).

---

### 19. Lambda retornando lambda → bytecode inválido (JVM) / COMP001 (Native) — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `7`)

- **Sintoma:** `var make = (x: Int) -> ((y: Int) -> x + y); make(5)(3)` falha:
  JVM `ClassFormatError: Illegal class name ""`; Native `undefined reference`.

- **Corrigido 04/09** (`6dad633`): `collectCaptures` agora desce em lambdas
  aninhados — variáveis livres do lambda INTERNO que pertencem ao escopo do
  EXTERNO passam a ser capturadas pelo externo e repassadas via constructor.
  Antes o externo não capturava `a` e o lambda mais interno somava o ponteiro
  `this` no lugar da captura (lixo em Native, `VerifyError` em JVM). Prova:
  `LambdaE2ETest.tripleNested*` (3 níveis, `make(5)(3)(10)` = 18 nos 3 targets).
- **Arquivos:** `CompilerDriver.java` (`collectCaptures`), `LambdaE2ETest.java`.

---

### 20. Lambda armazenado em coleção e INVOCADO quebra (JVM/Native) — ✅ VERIFICADO CORRIGIDO 08/09 (saida correta: `10`)

- **Sintoma:** `listOf((x)->x*2).get(0)(4)` → JVM `ClassFormatError`; Native
  `COMP001`. Guardar sem invocar funciona (`ops.size` ok); lambda em var e
  chamar funciona; a quebra é **invocar um lambda vindo de expressão
  (call-on-expression)**.
- **Reprodução:**
  ```kof
  main() {
      var ops = listOf((x: Int) -> x * 2, (x: Int) -> x + 10)
      println(ops.get(0)(4))   // JVM ClassFormatError
  }
  ```
- **Causa provável:** call sobre o resultado de `get()` não resolve o tipo
  SAM para emitir o invoke — cai em caminho genérico.
- **Arquivos:** `CompilerDriver.java` (call-on-expression com tipo função),
  `JvmBackend.java`/`NativeBackend.java`.

---

### 21. Nomenclatura: `PKG005` rejeita mesmo nome simples em pacotes DIFERENTES — ✅ CORRIGIDO (teste `PackagesE2ETest`, casos PKG005 03/09)

- **Sintoma:** `package pkgA; class Data` + `package pkgB; class Data` →
  `duplicate type name 'Data' in packages 'pkgA' and 'pkgB' [PKG005]`. Em
  Java/JVM isso é perfeitamente legal (nomes fully-qualified distintos).

- **Corrigido 03/09**: o compilador agora aceita nomes com o mesmo nome
  simples em pacotes diferentes. Só rejeita nomes duplicados no MESMO
  pacote. O nome interno (FQ) é usado para todas as referências.
  
- **Arquivos:** `CompilerDriver.java` (PKG005) --- REMOVIDO.

---

### 22. Native: chamada de CONSTRUTOR de classe de outro pacote → undefined reference — ✅ CORRIGIDO (teste `NativeE2ETest.nativeConstructorFromImportedPackage`)

- **Sintoma:** `import a.b.C; main() { var c = C() }` no target NATIVE →
  `undefined reference to 'C_init_0'` no ld. O emit usa `sanitizeName(ct.name())`
  (nome simples "C") no call site, mas a definição usa `clazz.name()`
  (internal "a/b/C" → `a_b_C_init_0`). JVM funciona (a/b/C.class correto).
- **Reprodução:** `kof build src --target native` no projeto
  `src/Main.kf (import a.b.C)` + `src/a/b/C.kf (package a.b)`.
- **Causa provável:** `NativeBackend.java:1725` (e ~1730 para métodos) monta o
  símbolo com `ct.name()` simples; deveria usar internal name
  (`ct.packageName().replace('.','/') + "/" + ct.name()`).
- **Arquivos:** `NativeBackend.java` (mangle de CONSTRUCTOR/call).

---

### 23. ExternalClasspath: cadeia de superclasses só resolve DENTRO dos entries — ✅ CORRIGIDO (teste `AndroidInteropE2ETest.missingSuperclassOnClasspathWarns`)

- **Sintoma:** `resolveMethod`/`resolveFieldType` seguem a superclasse apenas
  se ela estiver nos entries (`classBytes`). Se uma superclasse intermediária
  (ex.: de um .jar A apontando p/ classe de um .jar B não fornecido) estiver
  fora do classpath, membros herdados NÃO são encontrados → referência perdida
  silenciosamente (descritor errado / erro de símbolo).
- **Reprodução:** classpath com `app.jar` (classe extends `LibBase` de
  `lib.jar`) sem `lib.jar` → `super.metodo()` resolve null.
- **Nota:** é limitação documentada no código (linha 135 "nos entries"), mas
  gera falha silenciosa sem aviso ao usuário. Ao menos um warning "superclasse
  X não encontrada no classpath" deveria ser emitido.
- **Arquivos:** `ExternalClasspath.java` (resolveMethod/superclassOf).

---

### 26. Valor VOID usado como valor (println(f()) / `var x = f()`) → segfault/VerifyError — ✅ CORRIGIDO 04/09 (SEM033) + variante 08/09 (SEM036)

- **Sintoma:** `println(f(5))` onde `f` é void (função `void` ou lambda com
  corpo de bloco sem `return`) compila mas quebra: Native segfault (pop de
  lixo), JVM `VerifyError`. O lambda `(a: Int) -> { var x = a + 1 }` é void
  (corpo de bloco com múltiplos statements exige `return` explícito em Kof).
- **Corrigido 04/09**: o emit diagnostica `SEM033` ("a chamada não retorna
  valor") quando uma expressão void é usada como argumento de println/print ou
  como initializer de var. A chamada void como STATEMENT (`f(1)` sozinho)
  segue funcionando. Prova:
  `CompilerDriverTest.voidCallAsValueGivesCleanDiagnostic` +
  `voidLambdaAsValueGivesCleanDiagnostic`.
- **Arquivos:** `CompilerDriver.java` (emit de println/print e VarDeclStmt).
- **Variante corrigida 08/09 (SEM036):** função/método com tipo NÃO-void cujo corpo PODE terminar sem return/throw (`Int f() { }`, `Int f(Int x) { var y = x + 1 }`, `if` sem `else` no fim) compilava e emitia `ireturn`/`areturn` com pilha vazia → VerifyError no JVM (disfarçado de "JavaFX launcher"), `expression stack underflow` no JS, `NoSuchElementException` no interpretador. `ReturnPathAnalyzer` (novo) checa o último statement do corpo em compile-time: return/throw/block-terminal/if-com-else-ambos-saem → ok; loops/try/switch conservadores (não acusam `while(true){return}`). Abstract pulado. Prova: `CompilerDriverTest.{nonVoidFunctionWithEmptyBody,nonVoidFunctionFallingOffEnd,ifWithoutElseAtEnd}GivesCleanDiagnostic` + `allPathsReturnStillCompiles` (negativo). Suíte 1085/59 (= só bug 59) — zero regressão.

---

### 27. Paridade: `String.valueOf(char)` diverge entre JS e JVM/Native — ✅ CORRIGIDO 07/09

- **Sintoma:** `String.valueOf(104 as Char)` devolve `"h"` no JVM e no Native,
  mas `"104"` no JS. `println(char)` é numérico (`72`) nos 3 targets (congelado
  — `training/language/strings.md`), mas o `valueOf(char)` **solto** não tem
  paridade.
- **Reprodução:**
  ```kof
  main() { println(String.valueOf(104 as Char)) }
  // JVM: h   Native: h   JS: 104
  ```
- **Causa provável:** o backend JS não trata `valueOf` de `char` como conversão
  para caractere (deixa o número passar); JVM usa `String.valueOf(char)` do JDK
  (caractere) e Native usa `kof_char_to_string` (UTF-8).
- **O que deveria acontecer:** os 3 targets iguais. A decisão de qual é o certo
  (`"h"` ou `"104"`) é **de design** (  — regra 6): o corpus
  (`common-mistakes.md`) favorece `"h"`, mas isso precisa de bump + discussão,
  não de correção silenciosa. Registrado como gap até lá.
- **Arquivos:** `JsBackend.java` (dispatch de `valueOf`), `CompilerDriver.java`
  (lowering nativo char→Int para println).
- **Corrigido 07/09:** `JsCallEmitter` valueOf(char) → `String.fromCharCode` (paridade JVM/Native "h"). Prova: `CoreRegressionE2ETest.stringValueOfCharParity` (3 targets).
- **Descoberto:** 05/09 ao corrigir a regressão de `println(char)` (commit
  `94aca7a`).

---

### 28. FLAKE: `KofWebHardeningTest.ws_connection_counter_increments_and_decrements` — CORRIGIDO 05/09

- **Sintoma (histórico):** `expected: <1> but was: <0>` no contador de conexões ws.
  **Intermitente** — passava em execução isolada; falhava esporadicamente na
  suíte completa (05/09: 1× em várias rodadas; mesma assinatura em
  `ws_messages_counters_track_calls` — `2:2` vs `2:1`).
- **Causa real:** race de publicação na produção — o servidor flushava o
  `101 Switching Protocols` **antes** de `WS_CONNECTIONS_ACTIVE.incrementAndGet()`
  (`JvmRuntimeWebServer`), e `wsSend` incrementava `WS_MESSAGES_SENT` **depois**
  do `sendText` (`JvmRuntimeWebDispatch`). O cliente via o handshake/eco
  completo e consultava `/stats` antes do increment — a janela não é do teste,
  é do runtime.
- **Correção (05/09, fixes-for-kofagent):** increment do contador de conexões
  movido para **antes** do flush do 101 (com try/finally abrangendo handshake +
  frame loop, decrement no mesmo finally); increment de `WS_MESSAGES_SENT`
  movido para **antes** do `sendText`. O contador nunca mais fica atrás do
  estado observável pelo cliente. Prova: `KofWebHardeningTest` 6/6 + suíte
  completa 957/0.
- **Arquivos:** `JvmRuntimeWebServer.java`, `JvmRuntimeWebDispatch.java`.
- **Registrado:** 05/09 (suíte do port mq cross). **Corrigido:** 05/09.

---

### 29. `var h = spawn { lambda }` (handle de lambda) quebra em todos os targets — ✅ CORRIGIDO 06/09 (lane bug-fix)

- **Sintoma:** o corpo da lambda **nunca roda** ou o processo **segfaulta**:
  - x86_64 nativo: SIGSEGV (ec=139), nada imprime;
  - riscv64/aarch64 (qemu): ec=0 **sem output** (silencioso — R6);
  - `await h` nunca vê o resultado.
- **Não quebra:** `spawn { ... }` fire-and-forget com captura (funciona,
  `SpawnE2ETest.spawnLambdaCapturesOuterLocal`) e `var h = spawn fn()`
  (chamada de função nomeada — funciona nos 3 targets, `two-awaits` ok).
  Só a combinação **handle + lambda literal** está morta.
- **Reprodução:**
  ```kof
  main() {
      var n = 21
      var h = spawn { println(n * 2) }
      await h
  }
  ```
  (x86_64: segfault; riscv64: vazio; esperado: `42`).
- **Causa provável:** lowering de `SpawnStmt` com `LambdaExpr` **atribuído a
  handle** — o task object passado a `kof_spawn_result`/`pthread_create` sai
  errado (capturas/vtable da lambda void). O segfault x86_64 (que é o runtime
  "de referência") indica o bug no lowering compartilhado, não no asm riscv.
- **O que deveria acontecer:** `spawn { lambda }` com handle deve rodar a
  lambda na thread e `await` entregar o resultado (mesmo caminho do
  fire-and-forget, que já funciona).
- **Arquivos:** lowering `SpawnStmt` (`CompilerDriver.java`), `emitRiscvSpawn`
  / trampoline (`NativeBackend.java`), `NativeRuntime`/`RuntimeConcurrency`
  (x86_64).
- **Registrado:** 05/09 (sweep spawn do NATIVE002-stdlib residual —
  pré-existente, não introduzido pelos fixes cross da linha).

---

### 30. Native x86_64: `json.decode<Bool>("false")` dava `true` — ✅ CORRIGIDO

- **Sintoma:** `decode<Bool>` invertido no x86_64: `"false"`→`true`,
  `"  true"`→`false`. O JVM dava o correto (`false`/`true`); o riscv64
  (port novo) também. Só o x86_64 — o "runtime de referência" — estava
  errado, e nenhum teste cobria `false`/ws (só `"true"` sem espaço).
- **Causa:** `kof_json_decode_bool` chamava `kof_json_starts_with` passando
  o length em `%r8d`, mas a helper lê o length de `%rdx` (= pos, lixo); e a
  helper comparava a partir do offset 0, ignorando o pos após o skip de
  whitespace. Resultado: sempre "starts with true" → `false`→true, e com ws
  o byte 0 é espaço → nunca casa → `"  true"`→false.
- **Correção:** comparação inline de `"true"` a partir de `%rdx` (pos), sem
  a helper (usada só aqui). Paridade JVM/riscv64/aarch64.
- **Reprodução:** `println(json.decode<Bool>("false"))` → esperado `false`.
- **O que deveria acontecer:** `decode<Bool>` segue o JVM (true/false literais,
  ws tolerado).
- **Arquivos:** `RuntimeJsonDecode.java` (`kof_json_decode_bool`).
- **Descoberto:** 05/09 no sweep json do NATIVE002-stdlib residual (port dos
  decoders escalares riscv64 expôs a divergência). Regressão:
  `JsonE2ETest.jvmDecodeBoolFalseAndWhitespace`.

---

### 31. `process.<método-inexistente>()` compila como acesso a campo (segfault) — ✅ CORRIGIDO 06/09 (lane bug-fix)

- **Sintoma:** `process.currentDir()` (e qualquer método não-listado do
  `KofProcess`) **compila** e no cross **segfaulta** (ec=139); no x86_64
  retorna um valor lixo (`true`) — fallback silencioso.
- **Causa:** `KofProcess` só expõe `spawn` + handle methods
  (`alive/exitCode/kill/readLine/stdout/write`). Um método desconhecido não
  cai em SEM011 ("método inexistente") — cai no caminho genérico de acesso a
  campo do receiver (`pop t0; ld t0,16(t0)`), que deref um ponteiro nixo.
- **Reprodução:** `main() { println(process.currentDir().length > 0) }`
  (x86_64: `true`; riscv64/aarch64: SIGSEGV).
- **O que deveria acontecer:** diagnóstico SEM011 em compile-time (método
  não existe no namespace `process`), nunca compilar + segfault.
- **Arquivos:** lowering de receiver `process.*` (`CompilerDriver.java`,
  `KofProcess.staticCall`/`handleMethod`). É a área **F2.8
  ExpressionLowerer** do REFACTOR-500 (EM CURSO de outro agente) — não tocar
  sem combinar.
- **Registrado:** 05/09 (sweep io/process do NATIVE002-stdlib residual).

---

### 32. Type-argument de import sem package → cast/descritor quebrado (CORRIGIDO 05/09)

- **Sintoma:** `List<NodeUI>` com `import com.dev.NodeUI` gerava
  `checkcast // class NodeUI` **sem pacote** → `NoClassDefFoundError: NodeUI`.
  E o caminho qualificado `List<com.dev.NodeUI>` (que "devia" funcionar)
  quebrava de outro jeito: `ClassFormatError: ... illegal character in
  descriptor` (descritor `Lcom.dev.NodeUI;` com pontos).
- **Causa raiz:** a qualificação de tipo resolvia só o nível **externo**.
  `Type.of("List<NodeUI>")` recursa nos type-arguments mas cria
  `ClassType("", "NodeUI")` (package vazio); `qualifyViaImports`/
  `qualifiedType` dão bail em nome com `<` (só tratam o base). Assim o ARG
  nunca recebia o pacote do import. O receiver do `.get()` (analyzer) e o
  descritor do campo (driver) herdam esse arg sem pacote.
- **Correção:** qualificação **recursiva profunda**
  (`CompilerTypes.qualifyDeep` + `simpleNamePackage`): separa nome pontuado no
  campo `name`, resolve nome simples via imports (ambíguo→null, sem chute) e
  classes declaradas no módulo (SymbolTable — mesmo pacote/outro arquivo),
  recursando em type-arguments/arrays/nullable/function. Aplicada nos dois
  pontos de saída: `resolveType` (analyzer → receiver/checkcast) e
  `resolveWithTypeParams` 4-arg (driver → descritor de campo/param/retorno).
  Não altera builtin (kof.List), enum (vira String), nem nome já com pacote.
  Sem concatenação de pacote no codegen (o codegen recebe tipo já resolvido).
- **Prova:** `PackagesE2ETest` 12/12 — `genericArgViaImportResolvesPackage`
  (List import), `genericArgFullyQualifiedStillWorks` (List<com.dev.NodeUI>),
  `nestedGenericArgViaImport` (List<List<NodeUI>>), `genericArgSamePackageNoImport`,
  `genericArgCastHasPackageInBytecode` (checkcast com `com/dev/NodeUI`).
  Suíte completa 969/0.
- **Limitação (bug 33, separado):** `Map<_,Classe>`/`Set<Classe>` ainda
  quebram no emit (pré-existente, falha no baseline sem esta correção).

---

### 33. Receiver de tipo nullable **inferido** quebra o retorno de método no emit ✅ CORRIGIDO (06/09)

- **Sintoma (original, mal diagnosticado):** reportado como "Map/Set com
  type-arg de classe quebra no emit". Reprodução real:
  `var v = m.get(k)` (ou `var v = maybe()` onde `maybe(): View?`) e depois
  `v.render()` → compila, mas em runtime
  `NoSuchMethodError: 'java.lang.Object View.render()'`.
- **Causa raiz (corrigida 06/09):** NÃO é bug de Map/Set — é **member call em
  receiver de tipo nullable *inferido***. O lowering re-inferencia o tipo do
  receiver (`MethodCallTyper`); no caminho genérico de classe
  (`instanceof Type.ClassType`) o `NullableType` **não** casava → retorno do
  método saía `Unknown`/`Object`. Um local **anotado** (`var v: View?`) não
  reproduz porque o lowering descarta a nullability do local; só o **inferido**
  (`var v = m.get(k)`) preserva `NullableType` no IR. Map/Set era só *um*
  caminho que produz o local nullable (`get` retorna `V?`) — `var v = maybe()`
  reproduz **sem coleção**.
- **Fix:** `MethodCallTyper` — desempacotar `NullableType` → `inner()` antes
  do `instanceof ClassType` (espelha o unwrap já feito no ramo de handle).
- **Prova:** `KofMapSetTest.memberCallOnNullableInferredFromMapJVM` (var via
  `maybe():View?` + via `map.get`) → `v:a\nv:x`; suíte 954/0/3-skip.
- **⚠️ Sintoma separado (bug NOVO, não é este):** `Set.first()` →
  `ClassFormatError: Illegal class name ""`. `first()` **não é** método de Set
  no Kof (corpus não documenta); método desconhecido em **tipo de coleção**
  vira *no-op silencioso* no lowerer (diferente de `C.ghost()` → `SEM025`) e
  o emit gera `"".render` (descritor vazio). Violação R6 (nunca silencioso).
  Registrar como bug próprio; **não** confundir com 33.
- **Registrado:** 05/09 · **Corrigido:** 06/09.

---

### 34. Método inexistente em tipo BUILTIN (List/Map/Set/String) → no-op silencioso (R6) — ✅ CORRIGIDO 06/09 (lane bug-fix)

- **Sintoma:** `l.ghost()`, `m.ghost()`, `s.ghost()`, `"ab".ghost()` **compilam**
  e em runtime viram *no-op* que devolve o próprio receiver (JVM: `println(l.ghost())`
  → `[1, 2]`). Diferente de `C.ghost()` em classe de usuário → `SEM025`.
- **Causa raiz (diagnosticada 06/09):** em `MemberCallTyper.java:354` o gate do
  SEM025 é `!BuiltinTypes.isList(ct) && isKnownReceiver(...)`. Dois furos:
  (a) `isList` isenta **List** explicitamente; (b) o gate `isKnownReceiver`
  (`allClasses().containsKey || isExternal`) é **falso** para Map/Set/String
  (não são classes do programa nem externas) → nenhum deles chega ao SEM025.
  O lowerer (`CollectionCallLowerer`) retorna -1 para método desconhecido e o
  call cai no emit genérico com `KofCall ghost` na IR (verificado: a IR carrega
  o call). Reproduzido nos 4 tipos (probe 06/09): list/map/set/string todos
  `success=true, errs=[]`.
- **O que deveria acontecer:** SEM025 em compile-time para método fora da
  allow-list do tipo (R6: nunca silencioso). A allow-list **já existe** no
  typer (linhas 99-143: get/remove/size/contains/add/set/clear/put/keys/values/
  map/filter/reduce/...); falta usá-la como gate de erro.
- **Corrigido 06/09 (lane bug-fix):** `MemberCallTyper` e `CollectionCallLowerer` agora emitem `SEM025` para método fora da allow-list (inclui aliases `push`/`append`/`count`/`length`); `StringMethodRegistry` já cobria `String`. Prova: `TestRepro` 4/4 (`l.ghost`/`m.ghost`/`s.ghost`/`"ab".ghost` → `SEM025`), suíte 203/0. Decisão de design validada: a allow-list já estava documentada e o corpus não usa métodos fora dela.
- **Efeito colateral no interpretador (06/09):** `KofInterpreter` não tem o
  no-op — `l.ghost()` vira `NoSuchMethodError` (mais correto, R6), mas **diverge
  do JVM compilado** (que no-op). O gate de paridade (`KofInterpreterParityTest`)
  não cobre método inexistente de propósito: a paridade só vale pós-fix do 34
  (ambos os caminhos rejeitam no compile).
- **Arquivos:** `MemberCallTyper.java:354` (gate), `CollectionCallLowerer.java`
  (retorna -1), `StringMethodRegistry` (allow-list de String já existe).
- **Registrado:** 06/09 (varredura de paridade do interpretador).

---

### 35. `listOf().contains(1)` → VerifyError no compilado (int não boxea) — ✅ CORRIGIDO 06/09

- **Sintoma:** `var l = listOf(); l.contains(1)` compila; no caminho
  **compilado** o JVM rejeita o bytecode: `VerifyError: Bad type on operand
  stack — Type integer is not assignable to 'java/lang/Object'` no
  `invokevirtual ArrayList.contains`. O interpretador funciona (devolve
  `false`).
- **Causa raiz (diagnosticada 06/09):** `JvmOpCollections.emitListCall`
  (ramo `kof_list_contains`) boxeia pelo tipo do **elemento da lista**
  (`elemType`), que em `listOf()` é `UnknownType` → `emitBoxIfPrimitive` não
  emite boxing → o `int` do argumento vai cru para `contains(Object)`.
- **Fix (06/09):** boxear pelo tipo do **argumento**
  (`kc.parameterTypes().get(0)`), não do elemento. Mesma correção vale para
  `set_contains`/`map_contains` (verificado: já casavam por terem elemType
  concreto nos testes; o fix de list cobre o caso Unknown).
- **Prova:** `KofScriptTest.interpreterParitySweep` caso `empty-list` (grupo A,
  paridade byte-idêntica nos 2 caminhos: `true;0;false`); probes set/map
  contains (`sv`) idênticos. Suíte 973/0.
- **Registrado:** 06/09 (varredura de paridade do interpretador) ·
  **Corrigido:** 06/09.

### 36. `null == null` baixa `if_icmpeq` → VerifyError no compilado — ✅ CORRIGIDO 06/09

- **Sintoma:** `var a = null; var b = null; println(a == b)` compila; no
  caminho **compilado**: `VerifyError: Type null is not assignable to
  integer` (o `==` baixou comparação de inteiros). Interpretador: `true/false`
  corretos (via `numEq`→`Objects.equals`, conteúdo).
- **Causa raiz (diagnosticada 06/09):** `ExpressionBinaryLowerer:207` —
  `accType`/`rightType` de locals inicializados com `null` são
  `UnknownType`; o check de null-**literal** (linha 210) não dispara (são
  locals); `operandType` fica `Unknown` → `JvmOpEmitter.emitBinary` trata
  Unknown como NÃO-referência → `IF_ICMPEQ` sobre nulls → verifier rejeita.
- **Fix (06/09):** `UnknownType == UnknownType` → comparação de **referência**
  (`Object`/`if_acmp*`) nos 2 caminhos (valor: `ExpressionBinaryLowerer`;
  shortcut `if`: `CompilerComparisons`). Justificativa: `Unknown` só surge de
  `null`/untyped-get — **nunca** de int inferido (que dá `INT`) — então `acmp`
  é seguro e casa com o interpretador (`Objects.equals`: `null==null`→true).
  Antes disso o caso **crashava** (VerifyError), logo não há comportamento
  observável a regredir.
- **Prova:** `KofScriptTest.interpreterParitySweep` casos `null-eq` e
  `null-eq-shortcut` (grupo A, paridade byte-idêntica nos 2 caminhos:
  `true;false` e `iguais;nao-ne`).
- **Registrado:** 06/09 (varredura de paridade do interpretador) ·
  **Corrigido:** 06/09.

### 37. `case Int n` em switch → `KofInstanceOf[PrimitiveType]` → VerifyError — ✅ CORRIGIDO 07/09

- **Escopo (refinado 06/09):** pattern matching com **referências** funciona
  nos 2 caminhos (verificado: `case String s` → `str:oi`; `case Point(var x,
  var y)` destructurando record → `3,4`). O bug é só **primitivo** (`case Int
  n`), que **não está no corpus** (`training/idioms/control-flow.md` só
  documenta `case String`/`case Point`).
- **Sintoma:** `switch (o) { case Int n -> ... }` com `o: Int`: compilado →
  `VerifyError: Type integer is not assignable to 'java/lang/Object'`
  (`instanceof` sobre primitivo é ilegal no JVM); interpretador → sempre
  `false` (cai no `default`).
- **Causa raiz:** `SwitchExprLowerer` emite `KofInstanceOf` para todo
  `case Tipo x:` sem boxar o scrutinee primitivo nem rejeitar tipo primitivo.
- **Esperado (R6):** se `case Int` não é suportado, deve dar **SEM** em
  compile-time (nunca VerifyError em runtime); se for suportado, boxar o
  scrutinee e casar. **Ambos os caminhos errados** (interpretador silencia,
  compilado crasha).
- **Prova/repro:** caso `pattern-match` (sweep manual 06/09; não travado no
  teste porque o interpretador também está errado — aguarda decisão de
  lowering na lane do outro agente).
- **Corrigido 07/09:** pattern de PRIMITIVO em switch (statement e expressão) agora dá SEM035 em compile-time (instanceof de primitivo é ilegal no JVM). Prova: `CompilerDriverTest.primitivePatternInSwitchIsDiagnosed`.

### 38. Re-throw em catch de try aninhado → handler externo lê slot errado — ✅ CORRIGIDO 07/09

- **Sintoma:** `try { try { throw "inner" } catch (String e) { throw "outer" } }
  catch (String e) { println(e) }`: compilado → `VerifyError: Bad local
  variable type` no handler externo; interpretador → imprime `inner` duas
  vezes (o handler externo lê o slot do catch interno).
- **Causa raiz:** lowering de `catch (String e)` aloca o slot do excector por
  `excLocalIndex` sem considerar try aninhados — o handler externo recebe
  `localIndex` sobreposto/errado (IR: op 24 `KofCatchStart[localIndex=1]`
  mas o corpo lê `LoadLocal(2)`).
- **Esperado:** `inner` + `outer` (semântica JVM de exception table).
- **Prova/repro:** caso `nested-try` (sweep manual 06/09).
- **Corrigido 07/09 (JVM/Native):** o corpo do catch agora usa um sub-escopo de locals (`subList(0, pos-do-catch-corrente)`) — com try aninhado de catch de MESMO nome, o local do catch interno sobrescrevia o externo no findLocalVar. Prova: `CoreRegressionE2ETest.rethrowInNestedTry` (JVM). ⚠️ JS: gap SEPARADO — try aninhado com catch gera `KofCatchStart` que o KofJS não suporta (COMP002); pré-existente, registrar como gap.

### 39. `println(m.get("zz"))` (null de Map) → NPE/unbox errado nos 2 caminhos — ✅ CORRIGIDO 10/09 (SG-008/bug 87, decisão do maintainer)

- **Sintoma:** `var m = mapOf("a", 1); println(m.get("zz"))`: compilado →
  `NullPointerException` (escolheu overload `println(int)` e deu unbox de
  null); interpretador → `NoSuchMethodError: Integer.valueOf/1` (escolheu
  `valueOf(int)` para um null). Esperado: imprimir `null`.
- **Causa raiz:** seleção de overload de `println` sobre `V?` (nullable de
  genérico de coleção) resolve para o ramo primitivo.
- **Análise 07/09 (correção testada e REVERTIDA):** fazer `Map.get` devolver `V?`
  sempre corrige o println(null), MAS quebra retrocompat: `assert(m.get("a") == 1)`
  (get nullable `Int?` vs primitivo `1`) gera `if_acmpeq` sobre ref vs int →
  VerifyError. A nullability de primitivos (congelada, AGENTS.md R6) exige
  decidir o narrowing do `==` (e dos demais consumidores) antes — requer bump
  de versão + discussão, não correção silenciosa.
- **CORRIGIDO 10/09 (decisão do maintainer aplicada — ver §87):** `Map.get()`
  devolve `V?` para TODO V (4 typers/lowerers + pin `K,V` via
  `SymbolTable.updateLocalType`); `T? == x` sem NPE (desembrulho Nullable +
  primitivo boxado + guard-unbox nos 4 caminhos: interpretador/JVM/Native/JS).
  O caso `m.get(k) == 1` da reversão de 07/09 compila (o `1` é boxado,
  `if_acmpeq` — retrocompat preservada).
- **Prova:** repro §39 no MESMO programa (`println(m.get("zz"))` = `null` E
  `m.get("a") == 1` = `true`); paridade 4 targets (BackendParity +
  ConformanceMatrix + KofScript); suíte 1255/0 na época. Ver §87 para o
  registro completo.

### 40. `n += 1` em campo de instância → crash nos 2 caminhos — ✅ CORRIGIDO 07/09

- **Sintoma:** `class Box { Int n; Int inc() { n += 1; return n } }` →
  interpretador: `NoSuchElementException` (pilha vazia); compilado:
  "frame crash em Box.inc: Index -1 out of bounds for length 0". Verificado
  **pré-existente** (falha igual sem o fix de static-field de 06/09).
- **Causa raiz (parcial):** `ExpressionAssignmentLowerer` ramo não-estático
  faz `LoadLocal(0)` (this) mesmo em método sem receiver mapeado — o mesmo
  padrão do bug 35-38 (this/local desalinhado em método com `owner` mas
  lowering de `this` inconsistente).
- **Prova/repro:** probe manual 06/09 (caso `inst`).
- **Corrigido 07/09:** (1) compound via `this.n` — `KofDup` antes do getfield (o putfield precisa do receiver de novo); (2) compound via variável `b.n -= 2` — `KofLoadField` agora usa o `fieldType` REAL (era `UnknownType` → getfield de Object + aritmética inválida → VerifyError/JavaFX). Prova: `CoreRegressionE2ETest.compoundOnInstanceField` (3 targets).

### 41. Campo ESTÁTICO no Native → lixo (R6 silencioso) — ✅ CORRIGIDO 07/09 (lane Native)

- **Sintoma:** `class C { static Int count = 0; static Int bump() { count = count + 1; return count } }` + `main() { println(C.bump()) ... }`: JVM/JS/interpretador dão `1\n2\n2`; **Native imprime lixo** (`61241504\n4209948\n4211958` — memória não-inicializada, não-determinístico).
- **Causa raiz:** `nat/NativeBackend.java:629-630` tem **stub vazio** para `KofGetStatic` (`case KofGetStatic gs -> { }` — não faz nada, o valor do campo nunca é carregado) e `KofPutStatic` (`addq $8, %rsp` — corrompe a pilha, não grava). O lowering por nome simples emite GETSTATIC/PUTSTATIC desde `0ba58fc` (fix para o caminho estático); o Native **nunca implementou** esses ops.
- **Corrigido 07/09:** `ClassLayout` exclui os campos estáticos do layout de instância (não ocupam o objeto); `NativeBackend.collectStaticFields/emitStaticData/staticSymbol` emite um slot `.quad` no .data por campo estático com o initialValue (String vira OBJETO Kof: header+length@16+chars@24, não `.asciz`); `KofGetStatic`/`KofPutStatic` em `NativeMethodEmitter` (x86_64) e `NativeRiscvCrossEmit` (riscv/aarch64) acessam o slot; o receiver `System.out` do println/print é descartado (`addq $8,%rsp`/`addi sp,sp,8`) em `NativeX86Calls`/`NativeRiscvCrossOps`. Prova: `NativeE2ETest.nativeStaticFields` (Int/String/bool, `mel\ntrue\n1\n2\n2`).
- **Pré-existente, não regressão:** não há teste Native com campo estático (`NativeE2ETest` — `grep static` = 0). Antes de `0ba58fc` o Native baixava `LoadLocal(0)+LoadField` (também lixo, `this` inexistente em método estático). A suíte green (1045/0) não cobre estático×Native.
- **Prova/repro:** sweep cross-target 07/09 (casos `static-field` / `static-field-plus-eq`), Native x86_64.

### 42. `hashCode()` de record ausente no JS e no Native — ✅ CORRIGIDO (JS `1ecfb3d` + Native `buildRecordHashCodeMethod`)

- **Sintoma:** `record P(Int x, Int y)` + `a.hashCode() == b.hashCode()`: JVM/interpretador → `true`; **JS** → `TypeError: a.hashCode is not a function` (exit 1); **Native** → `ld: undefined reference to 'P_hashCode'` (fail de link, exit 1).
- **Causa raiz:** o runtime de record no JS/Native não emitia o método `hashCode` (o JVM gera `hashCode` no `JvmRecordEmitter`). `equals`/`toString` existiam nos 3; `hashCode` não.
- **Corrigido (JS, `1ecfb3d`):** `JsClassEmitter` emite `hashCode()` sintético de record + `kof_hashCode` no runtime JS.
- **Corrigido (Native):** `CompilerRecordSupport.buildRecordHashCodeMethod` sintetiza `hashCode()` acumulando `31 * h + campo` no IR para Native (`lowerRecord`), gerando o símbolo `P_hashCode`. Exclusão de `native` removida de `ConformanceMatrixTest.recordhash`.
- **Prova/repro:** `ConformanceMatrixTest.recordhash` (verde nos 4 targets: JVM, Script, JS e Native).
- **Nota:** `a == b` (igualdade de conteúdo), `println(a)` (`P[x=1, y=2]`) e `a.hashCode() == b.hashCode()` agora têm paridade nos 3 targets.

### 43. String no Native conta bytes UTF-8, JVM conta code units — CORRIGIDO x86_64 (10/09; `length` + `charAt` + `substring` + `indexOf`/`lastIndexOf` UTF-16) — decisão de design STR001: convenção code units UTF-16 (paridade JVM/JS; `café`→4, `a😀b`→4)

- **Sintoma:** `var s = "café"; println(s.length); println(s.charAt(3))`: JVM → `4` / `233` (0xE9, code unit UTF-16 de `é`); **Native** → `5` / `195` (0xC3, 1º byte de `é` em UTF-8). `println(s + "!")` casa (`café!`) — só `length`/`charAt`/`substring` divergem.
- **Causa raiz:** as ops de string do Native são **byte/UTF-8** baseadas; as do JVM são **code-unit/UTF-16** baseadas. Mesma família do `STR001` (documentado p/ JVM `"Olá 😀".length`=6), mas aqui é **divergência cross-target** (Native ≠ JVM no MESMO programa) → paridade (regra 5).
- **Prova/repro:** sweep cross-target 07/09 (caso `unicode-str`), Native x86_64.
- **Correção (lane Native, decisão de design regra 6):** alinhar `length`/`charAt`/`substring` a UMA convenção (code point ou code unit) nos 3 targets — é mudança de contrato, precisa de bump.
- **✅ CORRIGIDO 10/09 (x86_64):** `kof_string_length` UTF-16 (08/09, `NativeE2ETest.nativeStringLengthUtf16`), `kof_string_char_at` (10/09, percorre o UTF-8 e devolve a **code unit UTF-16** da posição — 1/2/3 bytes → 1 unit, astral (4 bytes) → 2 surrogates; `café.charAt(3)`→233, `a😀b.charAt(1)`→55357) **E `kof_string_substring` (10/09)**: `RuntimeStringOps.emitStringSubstring` ganha um walk interno (`.Lkof_substr_walk`, rdi=str/esi=target → eax=byteOff, edx=units, ecx=1 se caiu no meio do par) que converte start/end de code units para byte offsets; a cópia passa a ser a fatia de bytes entre as duas fronteiras (par astral sempre inteiro). `café.substring(1)`→`afé`, `substring(3)`→`é`, `a😀b.substring(1,3)`→`😀`, `.substring(3)`→`b`. Prova: `NativeE2ETest.nativeStringSubstringUtf16` + `ConformanceMatrixTest.unicode`/`unicode-astral`/`unicode-substring` (native desbloqueado, 4 targets) + `BackendParityTest.unicode-str`. **E `kof_string_index_of`/`kof_string_last_index_of` (10/09, achado por varredura de paridade):** `RuntimeStringSearch` reusa o MESMO `.Lkof_substr_walk` para varrer o haystack por **code units** e casar a needle byte-a-byte na posição convertida (needle vazio → 0/len, needle>alvo → -1, corte de par pulado — needle well-formed nunca casa numa 2ª unit). `a😀b.indexOf("c")`: era 10 (byte) → 6 (unit) = JVM/Script. Prova: `NativeE2ETest.nativeStringIndexOfUtf16` + `ConformanceMatrixTest.unicode-indexof` (4 targets) + sweep de 22 vetores (ASCII+latin1+astral+bordas) batendo JVM==Native==Script.
- **⚠️ Sub-residual (corte de par astral ao meio em `substring`):** fronteira `end`/`start` que cai na **2ª unit de um par astral** (ex.: `a😀b.substring(0,2)`, `a😀b.substring(1,2)`) produziria um **surrogate solto** na string resultante. O Native ainda não casa com o JVM aqui: produz um **diagnóstico R6** (`substring cannot split an astral code point`), nunca um byte-cru errado. A paridade plena exige o **storage ser WTF-8** (permitir surrogates soltos) + `length`/`concat` aceitarem-no — mudança maior do layout interno de string, fora do escopo desta unidade (fronteiras bem-formadas cobrem o uso real; registro p/ a próxima iteração de storage). `charAt` já casa (devolve o code unit numérico, sem storage envolvido).
- **⚠️ Residual (lane Native riscv64/aarch64):** `kof_string_length`/`kof_string_char_at`/`kof_string_substring`/`kof_string_index_of`/`kof_string_last_index_of` no RISC-V (`NativeRiscvAsmRt0`/`Rt1`) ainda são **byte-based** — as faces riscv/aarch só rodam sob qemu (ausente no worker; o commit `2b9a483b` do agente cross confirma que o **outro** ambiente tem qemu — o port pode ser validado lá). O fix x86_64 é o caminho de referência; riscv/aarch seguem quando o toolchain estiver no ambiente.

### 44. `println(double)` no Native x86_64 imprime 6 casas + `5` (JVM: 16 casas + `5.0`) — ✅ CORRIGIDO 10/09 (x86_64; faces (a) e (b) verificadas por probe pós-fix)

- **Sintoma original:** `println(1.0/3.0); println(2.5*2.0); println(7.0/2.0)`: JVM → `0.3333333333333333` / `5.0` / `3.5`; **Native** → `0.333333` / `5` / `3.5`.
- **✅ CORRIGIDO 10/09 (commit `5ae263d1`, `RuntimePrintNum`):**
  - **16 casas:** `%.16g` em `kof_print_double`/`kof_print_float` — `1.0/3.0` → `0.3333333333333333` (casa com o JVM).
  - **face (a) `5` vs `5.0`:** pós-processamento — inteiro-válido (saída sem `.`/`e`/`nan`/`inf`) ganha `.0` — `println(2.5*2.0)` → `5.0` (contrato JDK `Double.toString`).
  - **face (b) reordenação stdout:** `kof_print_double`/`kof_print_float` abandonam `printf` (buffered stdio) e emitem via o mesmo caminho `snprintf` + syscall `write` dos Int/String — misturar `println(double)` com `println(Int)`/`println(String)` NÃO reordena mais. Probe `Fp44c` 10/09 (pós-fix): `print(2.5*2.0); println(0); println(5.0); println(2.5*2.0)` → `5.0 0 5.0 5.0` (ordem preservada; o reprodutor antigo dava `50\n5\n5\n`).
  - `float` imprime como `double` (`cvtss2sd` antes de formatar) — paridade JVM.
- **Prova:** `ConformanceMatrixTest.floatprint` (native desbloqueado, JVM+Native+Script verde; KofJS segue excluído: doc "parece bug mas é esperado" — JS `String(5.0)` = `"5"`) + probes `Fp44`/`Fp44c` 10/09.
- **Nota:** a parte `5` vs `5.0` do JS fica documentada como esperado (não é bug); só o Native divergia do JVM.
- **Residual:** faces riscv64/aarch64 não re-verificadas (qemu ausente no worker) — o formato do double no RISC-V segue o path anterior até o toolchain chegar; bug 59 bloqueia a verificação cross-arch de qualquer jeito.
- **✅ RESIDUAL CORRIGIDO 11/09 (x86_64) — spelling de `inf`/`-inf`/`nan`:** o fix original (16 casas + `.0`) deixou **passar reto** o spelling do glibc `%.16g`, que escreve `inf`/`-inf`/`nan`, mas o contrato é o de `Double.toString` do JDK → `Infinity`/`-Infinity`/`NaN` (o que JVM/Script imprimem; o comentário antigo "NaN/Infinity passam retos (JVM idem)" era **erro** — o glibc não escreve `Infinity`). Paridade regra 5 quebrada de forma **silenciosa** (sem diagnóstico): `println(1.0/0.0)` → JVM/Script `Infinity`, Native `inf`; `0.0/0.0` → `NaN` vs `nan`; afetava **todos** os caminhos de conversão (println box, `print` sem box, `String.valueOf`, concat `+`). **Fix:** reescrita in-place no buffer (64 bytes, os 3 spellings cabem) nos **4** ramos — `RuntimeStringConv.emitDoubleToString`/`emitFloatToString` (o box, que é o caminho real do `println(double)` — verificado via `objdump`: `main` chama `kof_double_to_string`+`kof_println_string`, **não** `kof_print_double`) + `RuntimePrintNum.emitPrintFloat` (o `print`/unbox). Cada ramo só reescreve quando o buffer é **exatamente** o spelling do glibc (compara char a char + tamanho) — decimal/científico/`.0` têm dígito e passam. **Prova:** `ConformanceMatrixTest.infinityprint` (println + print sem box + String.valueOf + float overflow, 3 targets) + linha `infinityprint` em `conformance-matrix.md` (o `ConformanceMatrixDocTest` exige paridade test×doc). **Lição (4ª vez): build incremental com constante inlined** — a `UI_UUID_RUNTIME` do `JsRuntimeUiUuid` ficou stale no `JsArtifactWriter.class` após o merge, e `KofUuidTest.uuidV7Js` falhava com "export not provided" **sem culpa do merge nem do código**; `clean test` resolve. Sempre `clean` ao suspeitar de "export missing" em fragmento wired. **Residual:** riscv64/aarch64 não re-verificados (qemu ausente) — seguem o path anterior até o toolchain chegar.

### 45. `finally` com `return` no try: JVM/interpretador DESCARTAM o efeito colateral do finally (JS correto) — ABERTO (lane lowerers) — bug de PARIDADE desde o fix de 07/09

- **Sintoma:** `Int f() { try { return 1 } finally { println("fin") } }` + `main() { println(f()) }`:
  - **JVM / interpretador** → `1` (o `fin` **não** é impresso — o finally é descartado quando o try `return`s). Reverificado 08/09.
  - **JS** → `fin` + `1` — **correto** desde o fix de 07/09 (era `fin` + `undefined`). Reverificado 08/09.
  - **Native** → não reverificado 08/09 (host arm64/macOS sem toolchain x86_64-linux).
- **Aisla (07/09, `Fin2` probe):** finally **roda** quando o try não retorna (`in-try|fin`) e quando o try **throwa** (`fin|caught:boom`); só o caminho **return-no-try** perde o efeito colateral. Em Java/Kotlin o finally roda e o `return` ainda vale (esperado: `fin` + `1`).
- **Causa raiz (JS):** o backend JS não preserva o valor de retorno stashed quando o finally executa → vira `undefined`.
- **Causa raiz (JVM/Native/interp, consistente):** o lowering/interpretador do `return` que sai do `try` pula o bloco `finally`. Como os 3 targets CONCORDAM, o agente anterior (07/09) rotulou de "congelado por construção" (regra 6).
- **Divergência achada 08/09 (lane lowerers — NÃO corrigi, condição de parada 1):** o rótulo "congelado" CONTRADIZ o corpus. `training/idioms/errors.md:107` documenta que `finally` "roda no caminho normal, no caminho capturado e na propagação" — e `return` no try É caminho normal. Pela regra 4 (bug = alinhar ao previsto, proibido documentar em volta), o comportamento PREVISTO é `fin`+`1`; os 3 targets concordam no ERRADO. Então isto é **bug de código**, não decisão de design. **Por que não corrigi nesta sessão:** o fix exige (a) pilha de frames de finally no lowering (store do valor → jump finallyLabel → load+return no epílogo), (b) o `CompilerLambdaClass` limpar a pilha ao entrar no corpo de lambda (senão vaza), e (c) o `JsControlFlowParser.parseTryStatement` reconhecer a nova forma de IR — o JS RECONSTRÓI try/finally da IR (não emite raw), e o fix JS do c727fee já tem noção própria de return-value stash. Mudar a IR sem validar os 4 backends + o reconstructor JS é risco alto de regressão de controle-fluxo; exige vivência de design (regra 4 do repo: "discussão técnica antes de código"). **Ação:** decisão registrada em `docs/development/planning-finally-return.md` (DD-01, `PROPOSED`, bump 0.3.1); ao decidir, o fix é no lowering (propaga aos 4 via IR) + JS parser.
- **Prova/repro:** sweep cross-target 07/09 (caso `finally-return`) + probe `Fin2` (A/B/C/D). Repro 08/09: `Int f(){ try { return 1 } finally { println("fin") } }` + `main(){ println(f()) }` → JVM `1` (sem `fin`).

### 46. `spawn { return … }` (lambda que RETORNA valor + handle) → SIGSEGV no Native — ✅ CORRIGIDO 09/09 (era bug de TIPAGEM, não trampoline)

- **Sintoma:** `var n = 21; var h = spawn { return n * 2 }; var v = await h; println(v)`: interpretador/JVM/JS → `42`; **Native x86_64 → SIGSEGV (exit 139), sem output, determinístico** (3/3 runs).
- **Relação com bug 29:** o bug 29 original (handle + lambda **void** com captura, `spawn { println(n*2) }`) foi CORRIGIDO 06/09 (`7ec8b9d`) — hoje funciona nos 4 caminhos. E `spawn fn(arg)` (função nomeada + handle) funciona nos 4. Restou só a variante **lambda literal com `return`** (task que produz resultado via corpo lambda, não via chamada de função).
- **Causa raiz (provável):** o trampoline de spawn do Native trata a vtable/capturas da lambda void (sem slot de retorno); quando o corpo lambda tem `return`, o trampoline escreve o resultado em slot inexistente/mal alinhado → fault. Mesma família do bug 29 (lowering de `SpawnStmt` + `LambdaExpr` → task object) — o fix de 06/09 não cobriu o caso com retorno.
- **O que deveria acontecer:** `await` entregar `42` (igual `spawn twice(n)` — que funciona; o único delta é lambda-literal vs chamada).
- **Prova/repro:** probe `S29`/`S29det` (07/09), Native x86_64, 3/3 determinístico.
- **Teste de regressão (09/09):** `SpawnE2ETest.nativeSpawnExprAwaitLambdaReturn`
  (`var n=21; var h = spawn { return n * 2 }; println(await h)` no NATIVE) —
  **confirmado falhando com SIGSEGV (exit 139)**, pré-existente (passa no HEAD
  sem as mudanças do bug-fix lane). Uso: qualquer correção do bug 46 deve deixar
  este teste verde.
- **Teste de isolamento (09/09):** `SpawnE2ETest.nativeSpawnExprAwaitLambdaReturnNoCapture`
  (`spawn { return 42 }` SEM captura) — separa a causa: se este passa e o com
  captura falha → a CAPTURA é a causa; se ambos falham → o return-lambda é a
  causa. Rodar os dois no primeiro build com toolchain.
- **Nota (09/09):** a "causa provável" original (escrita em slot inexistente) foi
  escrita pensando no trampoline RISC-V; no **x86_64** o trampoline
  (`RuntimeConcurrency.kof_spawn_trampoline`) grava `handle->result` em
  `16(%r12)` (campo válido do handle 32B) e `await` lê o mesmo offset — então a
  causa x86_64 é OUTRA (não-confirmada; requer qemu/gdb no worker). Hipóteses a
  descartar/confirmar: GC coletando a task/stack do worker, alinhamento do
  pthread_create, ou `kof_spawn_join_all` re-joinando handle já joinado no fim
  do main.
- **Correção (lane Native, regra 6):** trampoline de spawn deve propagar o slot de retorno quando a lambda tem retorno (cf. `emitRiscvSpawn` + `kof_spawn_result`); alternativa: diagnosticar `spawn { return … }` com código de gap (R6: nunca segfault silencioso). Decidir no plano.
- **Corpus:** `training/idioms/concurrency.md` documenta `spawn f()` / `spawn { stmts }` — a forma **lambda com return** não está no corpus; como interp/JVM/JS a executam, o comportamento previsto (regra 5) é `42` nos 4 targets.

- **✅ CORRIGIDO 09/09 — causa raiz REAL (não era trampoline, era TIPAGEM):**
  o type checker de `spawn { return 42 }` devolvia `Handle<FunctionType([],Int)>`
  (embrulhando o FunctionType da lambda), enquanto o lowering devolvia `Handle<Int>`
  (usando `inferLambdaBodyType`). Com `await h` retornando `FunctionType`, o `println`
  resolvia a sobrecarga **String** → emitia `kof_println_string(42)` → deref do
  ponteiro `0x2a` (o int 42 tratado como objeto String) → SIGSEGV `si_addr=0x3a`.
  O drama nativo era o `kof_println_string` lendo o header/caracteres "do 42".
  **Fix:** `BuiltinCallTyper.__kof_spawn_expr` e `MethodCallTyper.__kof_spawn_expr`
  agora desembrulham `FunctionType.returnType()` (ou `inferLambdaBodyType` na Lambin)
  — o `Handle<T>` carrega o tipo do RETURN, espelhando o `ExpressionStaticCallLowerer`
  (bug 29). Prova: `SpawnE2ETest.nativeSpawnExprAwaitLambdaReturn` +
  `NativeSpawnExprAwaitLambdaReturnNoCapture` (ambos `42`, exit 0) — antes 139.

### 47. `KofScript.eval` cache colidia por `hashCode()+length` → resultado errado (R6) — ✅ CORRIGIDO 07/09 (lane KOFSCRIPT)

- **Sintoma:** a chave do cache do `eval` era `target:hashCode():length()`. Dois programas **distintos** com o mesmo hash `int` + mesmo `length` colidiam: o 2º `eval` devolvia o resultado **CACHADO do 1º** sem executar. Exemplo real (07/09): `println(1008 + 2009)` (→`3017`) e `println(1560 + 1340)` (→`2900`) têm `length`=`32` e `hashCode()`=`-1863778421`; após `eval(A)`, `eval(B)` dava `3017` (o certo: `2900`).
- **Causa raiz:** `hashCode()` (32 bits) + `length()` não são injetivos — colisão é inevitável (birthday: ~77k programas no espaço de 4 dígitos já garante colisão no espaço testado).
- **Impacto:** silêncio (R6) — o usuário não vê erro, só saída errada de um programa que nunca rodou.
- **Correção (07/09):** a chave do `evalCache` virou **SHA-256** do programa (`sha256hex` em `KofScript.java`). O `fileCache` continua guardado por `lastModified+size`+hash (risco negligível — a comparação de mtime/size precede o hash).
- **Prova/repro:** `KofScriptTest.evalCacheKeyDoesNotCollide` (trava a pré-condição de colisão hash+length e que cada programa dá sua soma).
- **Descoberto:** 07/09 (probe `Collide3`) durante a varredura da lane KOFSCRIPT pós-paridade cross-target.

### 48. `json.decode<List<Record>>` → interpretador ✅ CORRIGIDO 07/09 + Native → ✅ gap honesto 09/09 (JSN004; R6)

- **Sintoma:** `record P(Int x); var l = json.decode<List<P>>("[{\"x\":1},{\"x\":2}]")`: JVM → `2`/`2` ✅; KofJS → `2`/`2` ✅; **interpretador (Script) → ✅ CORRIGIDO 07/09** (`KofInterpreterRuntime` intercepta `kof_json_decode_object_list` e mapeia cada item para `KofObj`, espelhando o fix de `decode<Record>`; prova `KofScriptTest.jsonDecodeListOfRecordRunsOnInterpreter`); **Native → COMPILE-FAIL** (`decodeFunction` não gera caminho para lista de classe Kof — `JsonDispatch.decodeFunction` só trata `ClassType` no topo, não `List<ClassType>`).
- **Diferente do fix de 07/09 (bug `decode<Record>`):** `json.decode<P>` (record no topo) foi corrigido no interpretador (`KofInterpreterRuntime.decodeKofValue` espelhando `encodeKof` — o método gerado faz `Class.forName` que não existe no interpretador). A variante **lista de record** tem duas falhas independentes: (a) interpretador — o dispatch de `List` usa `kof_json_decode_list`/`_object_list` com `Class.forName`; (b) Native — `JsonDispatch.decodeFunction` não tem ramo `isList` + elemento `ClassType`.
- **Prova/repro:** probe `L2i`/`Decode` (07/09).
- **Correção:** (a) ✅ lane interpreter FEITA 07/09 — `KofInterpreterRuntime` intercepta `kof_json_decode_object_list` (e `kof_json_decode_<X>` para o record no topo) e mapeia cada item para `KofObj` (mesmo padrão do `decodeKofValue`/`encodeKof`); (b) lane Native: `JsonDispatch.decodeFunction` + runtime riscv para lista de record.
- **Descoberto:** 07/09 (lote 2 da conformance matrix).
- **Interpretador CORRIGIDO 07/09:** `kof_json_decode_object_list` (2 args) agora é tratado no interpretador (decodifica cada item da lista para KofObj da classe via className). Prova: `KofInterpreterParityTest.jsonDecodeListOfRecord`.
- **Native — gap honesto 09/09 (R6):** o runtime nativo NÃO tem `kof_json_decode_object_list` (função inexistente → link fail) e `kof_json_decode_record_list` era stub que `jmp kof_json_decode_int_list` (retornava lixo silencioso, violação R6). Correção: (a) `ExpressionJsonCallLowerer` detecta `List<ClassType>`/`List<Record>` no target Native e emite diagnostic **JSN004** ("not supported on the Native target yet; use JVM/JS/interpreted") em vez de emitir função inexistente; (b) `RuntimeJsonDecode.kof_json_decode_record_list` virou panic honesto (`kof_panic` + mensagem JSN004) em vez de retornar lixo. `JsonDispatch.decodeFunction` agora recebe o `listElementType` real (via `ExpressionJsonCallLowerer`). Decoder real de lista de records no Native = trabalho futuro.

### 49. KofJS não compila `try` aninhado — `KofJS: try expected KofTryEnd` (COMP002) — ✅ CORRIGIDO 07/09

- **Sintoma:** um `try` dentro de outro `try` no target **KofJS** dava erro de COMPILAÇÃO: `Internal compiler error: KofJS: try expected KofTryEnd [COMP002]` (`JsControlFlowParser.parseTryStatement`). JVM/Native/Script (interpretador) compilam e rodam o mesmo programa normalmente.
- **Correção (07/09, lane JS):** a causa era o label de saída (done) do try SEM-finally ficar sem consumo quando o try interno termina no meio de uma região externa: o `parseStatements` do corpo do catch interno para antes do `KofLabel(done)`, e a região externa esperava `KofTryEnd` ali. O `parseTryStatement` agora só processa finally quando o try tem catch-all `Throwable` (`hasFinally`), e consome o `KofLabel(done)` no caso sem-finally só se ele NÃO for o endLabel de um try aninhado/outer (`MethodCtx.isTryEndLabel`). Prova: `CoreRegressionE2ETest.nestedTryJs` + `ConformanceMatrixTest` caso `nestedtry` (4 targets, JS incluído). Variante re-throw em catch = bug 52 (✅ corrigido 08/09, colateral do 45).
- **Repro:**
  ```kof
  main() {
      try {
          try {
              throw "inner"
          } catch (String e) {
              println("caught-inner:" + e)
          }
      } catch (String e) {
          println("caught-outer")
      }
      println("end")
  }
  // JVM/Native/Script: caught-inner:inner / end   (exit 0)
  // KofJS: COMP002 (não compila)
  ```
- **Causa raiz:** o `JsControlFlowParser.parseTryStatement` não consumia o `KofLabel(done)` de saída do try no caso SEM-finally; num try aninhado esse label sobrava para a região externa, que esperava `KofTryEnd` ali → COMP002. (O texto antigo dizia "não re-empilha o TryEnd" — a causa real é o label de saída não consumido.)
- **Diferente do bug 38:** o 38 é re-throw lendo slot errado no EMIT (x86/JVM); este é o PARSE/LOWERING JS não aceitando a estrutura aninhada.
- **Prova/repro:** `ConformanceMatrixTest.conformanceErrors` → caso `nestedtry` (agora nos 4 targets, JS incluído). Variante re-throw em catch = bug 52 (✅ corrigido 08/09).
- **Descoberto:** 07/09 (lote 2 da conformance matrix). **Corrigido:** 07/09 (lane JS, `JsControlFlowParser.parseTryStatement`).

### 50. channel send/recv DENTRO de `spawn` → SIGSEGV no Native (139) — ✅ CORRIGIDO 09/09 (usleep clobberava %rsi=&lock)

- **Sintoma:** `val c = channel<Int>(); spawn { c.send(42) }; val v = c.receive(); println(v)`: JVM/Script/KofJS → `42`; **Native x86_64 → SIGSEGV (exit 139), sem output, determinístico** (4/4 runs).
- **Isolamento (probe `Isol`, 07/09):** canal SEM spawn (mesma thread) → `exit=0 s=11` ✅; spawn SEM canal (lambda void) → `exit=0` ✅; canal send/recv mesma thread → `exit=0 42` ✅; **só a combinação spawn + op-de-canal → 139**. Ou seja: canal e spawn isoladamente funcionam no Native; o fault é na op de canal (send/receive, futex de mutex) executada **dentro da thread do spawn** (stack/raiz do futex não válida fora da thread principal — provável).
- **Causa raiz (provável):** o mutex/futex do FIFO do canal (`kof_channel`) é criado na thread principal; quando a op roda na thread de spawn, o futex ou o ponteiro da fila está em área não válida p/ aquele stack (ou o trampoline do spawn não propaga o TCB/ambiente p/ as primitivas de sink). Família do bug 29/46 (trampoline do spawn) mas a op de sink é a variável nova.
- **Por que a suíte não puxa:** o teste `KofConcurrency2Test.channelNative` roda o canal **sem spawn** (mesma thread) — o caso com spawn nunca foi coberto. `KofMqE2ETest` cobre o MQ no cross, não channel nativo c/ spawn.
- **Prova/repro:** probe `Isol` caso C (`channel<Int>()` + `spawn { c.send(42) }` + `c.receive()`), 4/4 → 139.
- **Correção (lane Native):** futex/mutex do canal deve ser criado e usado na thread certa (thread-local TCB no trampoline do spawn), OU op de canal em thread não-principal deve usar caminho seguro (spinlock puro). Diagnosticar com `qemu`/valgrind antes de decidir.
- **Descoberto:** 07/09 (lote 3 da conformance matrix, varredura de concorrência determinística).
- **Correção candidata 09/09 (a validar com toolchain/qemu):** o lock spin de
  `kof_channel_send`/`kof_channel_receive` chamava `syscall 202` (futex WAIT)
  com `rdi=chan` (uaddr errado — nunca setado para `&lock`) e `rsi=&lock` usado
  como opcode → comportamento indefinido quando há contenção (dentro de spawn).
  Sem spawn não há contenção (o `lock cmpxchg` nunca falha, o futex nunca roda)
  — por isso o isolamento mostrava "canal sem spawn funciona". Fix: args
  alinhados ao padrão do WAKE (`rdi=&lock`, `rsi=0` FUTEX_WAIT, `rdx=1`,
  `r10=0`) + restaura `&lock` em `rsi` para o próximo cmpxchg, nos dois pontos.
  Nota adicional: o `call usleep` no receive-vazio (count==0) depende de libc —
  mesma família do bug 61 (binário `_start` cru); se o send chega antes do
  receive o caminho vazio não roda. Validar o caso `spawn { c.send(42) }` +
  `c.receive()` com qemu/valgrind.
- **Teste de validação (09/09):** `KofConcurrency2Test.channelWithSpawnNative`
  (`spawn { c.send(42) }` + `c.receive()` no NATIVE, esperado `v=42`) — o caso
  do bug 50 que a suíte não cobria. Uso: qualquer correção do bug 50 deve deixar
  este teste verde (exit 0, sem SIGSEGV 139).

- **✅ CORRIGIDO 09/09 — causa raiz REAL (não era futex args, era registrador
  clobberado):** o teste `channelWithSpawnNative` passa agora (5/5 runs, `v=42`,
  exit 0). A correção candidata anterior (futex WAIT args) estava certa MAS
  incompleta: o SIGSEGV real (`si_addr=NULL`, confirmado por strace) vinha de
  `kof_channel_receive` no caminho de fila **vazia** — `.Lchan_recv_empty` faz
  `call usleep` (chamada libc que clobbera `%rsi`, que é caller-saved e guardava
  `&lock`), e o `jmp .Lchan_recv_lock` reusava `%rsi` corrompido no
  `lock cmpxchg (%rsi)` → deref de ponteiro inválido. No canal COM spawn o
  `receive` roda ANTES do `send` (a thread ainda não enviou) → dorme no caminho
  vazio → crash. Sem spawn (mesma thread) o send precede o receive e o caminho
  vazio não roda — por isso o isolamento "canal sem spawn funciona".
  **Fix:** re-setar `%rsi` (`leaq 20(%r13), %rsi`) após o `call usleep`. O
  caminho de `kof_channel_send` não tinha o bug (usa futex, não usleep).

### 51. `CompilerDriver` reutilizado vaza classes sintéticas → 2ª compilação Native quebra (link: `undefined reference to 'calc'`) — ✅ CORRIGIDO 07/09 (compiler-core)

- **Sintoma:** UM `CompilerDriver` compilando em sequência: (1) programa com `spawn calc(...)` (gera `LambdaTask0_invoke`) → OK; (2) programa SEM spawn → **COMPILE-FAIL**: `ld: undefined reference to 'calc'` em `LambdaTask0/1/2_invoke`. Driver NOVO para o mesmo programa (2) → OK.
- **Isolamento (probe `Leak`, 07/09):** 1) c/ spawn: success=true; 2) sem spawn (MESMO driver): success=false (ld fail, LambdaTask residual); 3) sem spawn (driver novo): success=true. O `.s` do programa 2 contém os 3 lambdas sintéticos do programa 1 (nome `LambdaTask0/1/2` contínuo — `lambdaCounter` não reseta).
- **Causa raiz:** `CompilerDriverState` acumula estado entre `compile()`s e NÃO reseta: `syntheticClasses` (ArrayList), `lambdaCounter` (int), `lambdaCapturedNames`, `lambdaEnclosingOwner`, `mutatedCapturedNames`, `entitySchemas`, `pendingSuperBridges` etc. (`CompilerDriverState.java:68,110,113,123,126,128,147`). No Native, as classes sintéticas residuais entram no `.s` e fazem referência a símbolos do programa anterior.
- **Por que a suíte não puxa:** quase todos os testes E2E usam UM driver por processo (1 programa/driver) — o CLI real é 1 processo/compilação, então não manifesta. Só um teste que reutilize o driver (c/ spawn → sem spawn) pega.
- **Impacto:** quem usa a API `CompilerDriver` programaticamente (LSP, REPL, watch/reload da Fase 8, `KofScript.runFileCompiled` em loop) com programas diferentes num mesmo driver pode ter compilações contaminadas.
- **Prova/repro:** probe `Leak` (07/09).
- **Correção (lane compiler-core):** reset dos campos mutáveis do `CompilerDriverState` no início de cada `compile()` (ou factory de estado por compilação). Não tocar no `CompilerDriverState`/`NativeBackend` enquanto REFACTOR-500 F3/FASE 3 os estão editando — coordenar.
- **Descoberto:** 07/09 (lote 3 da conformance matrix).

### 52. `throw` DENTRO de `catch` (re-throw) → KofJS não compila (`unexpected KofCatchStart`) — ✅ CORRIGIDO 08/09 (efeito colateral do bug 45)

- **Sintoma:** `try { try { throw "x" } catch (String e) { throw "re:" + e } } catch (String e) { println("outer:" + e) }`: JVM/Script/Native → `outer:re:x` / `end` ✅; **KofJS → COMPILE-FAIL** `KofJS: unexpected KofCatchStart at statement level` (`JsControlFlowParser.parseStatement:145`).
- **Diferente do bug 49:** o 49 era try aninhado SEM re-throw (corrigido 07/09 — o parser consome o label de saída no caso sem-finally). Este é o catch que **lança de novo** (`throw` dentro do corpo do catch): o corpo do catch interno termina em `KofThrow` (não em `KofJump` para o done), e o `KofCatchStart` do externo aparece "solto" no statement level — o `parseStatements` do catch interno não trata a saída por throw. Pré-existente (falha igual antes e depois do fix do 49).
- **Prova/repro:** probe `ReThrow` (07/09); caso `catchrethrow` na `ConformanceMatrixTest` (agora 4 targets).
- **Causa raiz (IR dumpada 07/09, probe `ReThrowIr`):** o corpo do catch interno termina em `KofThrow` SEM `KofJump` de saída (o throw propaga direto pro handler externo). O `parseStatements` do corpo do catch consome o throw e para no `KofTryEnd`, mas o `KofLabel(endLabel do try externo)` que segue é tratado como "unmatched label" e a região externa perde o alinhamento → `KofCatchStart` chega solto no `parseStatement` (linha 145). Diferente do 49: lá a saída era por jump; aqui é por throw (não há jump p/ consumir).
- **Correção (lane JS):** `parseTryStatement`/`parseStatements` deve tratar corpo de catch que termina em `KofThrow` (a saída do try é o handler externo, não um jump) — consumir o `KofCatchStart`/`KofTryEnd` do externo corretamente. Mesma família do 49 (parser de try JS), arquivo `JsControlFlowParser`. ⚠️ O outro agente editou este parser hoje (`5d6e68a`) — coordenar antes de tocar.
- **Descoberto:** 07/09 (fix do bug 49, varredura de variantes de try).
- **Correção (08/09):** fix **colateral** do bug 45 (`c727fee` — finally com
  return no try): o parser JS (`JsControlFlowParser`) passou a tratar o corpo
  de catch que termina em `KofThrow` pela região externa — o re-throw propaga
  pro handler externo sem deixar o `KofCatchStart` solto. Verificado: JS
  compila e roda `outer:re:x\nend` (idêntico a JVM/Native/Script). Prova:
  `ConformanceMatrixTest.catchrethrow` agora **4 targets** (exclusão `js`
  removida); `conformance-matrix.md` célula → DONE.

### 53. Handler de rota que termina em `return null` responde 404 mesmo quando retorna valor (GitHub #28) — ✅ CORRIGIDO 07/09 (lane compiler/typer)

- **Sintoma (GitHub issue #28, reportado por domfelipe, macos-arm64):** um handler com a forma idiomática `if (x != null) { return valor } return null` responde **404 em TODAS as requests de sucesso** — o valor retornado pelo caminho de sucesso se perde. Silencioso: compila sem aviso, só se manifesta em runtime. Efeitos colaterais do handler persistem (ex.: `app.patch` alternava o estado no store a cada request enquanto respondia 404).
- **Reproduzido no repo (07/09, probe `Iss28`, JVM):**
  - `if (id == 1) { return "one" } return null` → **404** (errado; esperado 200 `one`)
  - `if (id != 1) { return null } return "one"` → 200 `one` ✅ (controle do issue)
  - `return "one"` (sem null) → 200 ✅; `return null` único → 404 ✅ (documentado)
- **Causa raiz (IR dumpada, probe `LamIr`):** o `invoke()` da lambda-handler sai com `ret=void` mesmo tendo `return "one"`. `ExpressionTyper` (caso `LambdaExpr`, ~linhas 216-229) só varre `ReturnStmt` **no topo do corpo** (`for (StatementNode s : le.body())` — não desce em `if`); o `return "one"` está aninhado no `if` e é ignorado; o `return null` do topo tem tipo UNKNOWN → o fallback marca VOID. Com `invoke` void, o valor nunca chega ao dispatch (`JvmRuntimeWebDispatch:91` `kof_web_invoke` → null → 404). Explica exatamente o padrão do issue: funciona quando o último return top-level é o de valor.
- **Correção (lane compiler/typer):** inferir o tipo de retorno da lambda varrendo **todos** os `ReturnStmt` do corpo (recursivo em if/switch/blocos), não só o primeiro top-level; `return null` deve contribuir UNKNOWN (não forçar VOID quando há outro return com valor). Mesma família do bug 29 (retorno de lambda). **FEITO 07/09:** `ExpressionTyper.firstReturnValueType`/`returnValueType` (varredura recursiva em if/switch/try/loops/blocos, sem descer em lambdas aninhadas) — usado tanto pelo caso `LambdaExpr` quanto por `inferLambdaBodyType`. Prova: `KofWebE2ETest.handlerReturningNullAsLastPathStillRespondsValue` (200 `one` no hit, 404 no miss — `return null` continua documentado).
- **Workaround (documentar no corpus):** `return status(404, "...")` na perna de ausência, ou inverter a forma (`if (x == null) { return null }` com o sucesso por último).
- **Descoberto:** 07/09 (GitHub #28; reproduzido + causa raiz no repo).

### 54. Qualquer `app.delete(...)` crasha o compilador JVM (COMP002 "frame crash") (GitHub #29) — ✅ CORRIGIDO 07/09 (lane compiler)

- **Sintoma (GitHub issue #29, reportado por domfelipe, macos-arm64):** registrar QUALQUER rota com `app.delete(...)` faz o backend JVM crashar em compile-time: `RuntimeException: frame crash em Default/Main.main: Index -1 out of bounds for length 0` (ASM `Frame.merge`), reportado como COMP002. Blocker para APIs REST com DELETE. Bissecção do autor: gatilho é `app.delete` em si (path/corpo/captura/try irrelevantes); `get/post/patch` passam.
- **Reproduzido no repo (07/09, probe `Repro2`/`Repro3`, JVM):** `get/post/put/patch` → success=true; `delete` → crash `Index -1 out of bounds` (e a exceção escapa como RuntimeException, não como diagnostic — R6). `options` → success=true.
- **Causa raiz (confirmada, probes `WebDelIr`/`IoCheck`):** o `case "delete"` de `KofIo.instanceMethod` (`KofIo.java:61-63`) **não valida `argCount == 0`** — retorna `IoCall(kof_io_delete, BOOL)` para QUALQUER arity, inclusive o `app.delete("/x") {…}` (2 args). Em `CompilerComparisons.hasReturnValueInner`, o check de Io (`KofIo.instanceMethod(UNKNOWN, name, size) != null`, ~linha 158) roda **antes** do check de web → `delete` é tratado como `File.delete()` (retorna Bool) → `StatementLowerer:41` emite `KofPop` extra → mas o lowering de web (`ExpressionInstanceCallLowerer:176`) emite `kof_web_route` (VOID, nada empilha) → underflow no merge de frames. IR dumpado: só o DELETE tem um `KofPop` órfão (índice 9) que o GET não tem.
- **Correção (lane compiler):** (a) `KofIo.instanceMethod` deve exigir `argCount == 0` no `case "delete"` (como fazem `size`/`name`); (b) `hasReturnValueInner` deve checar rota web (`KofWeb.isAppType` do receiver) **antes** do fallback Io genérico (o comentário em `CompilerComparisons:188` já alerta que "delete também é rota do web" — mas a ordem dos checks não respeita); (c) o crash deve virar diagnostic COMP002 com posição, não RuntimeException (R6). ⚠️ `CompilerComparisons`/`KofIo`/`ExpressionInstanceCallLowerer` são núcleo — verificar dono no DOING antes de tocar.
- **Workaround (documentar no corpus):** `app.post("/tasks/:id/delete")` ou outro verbo.
- **Descoberto:** 07/09 (GitHub #29; reproduzido + causa raiz no repo).

### 55. `ConcurrentModificationException` no interpretador com `spawn` concorrente (statics/`<clinit>` em HashMap compartilhado) — ✅ CORRIGIDO 07/09 (lane interpreter)

- **Sintoma:** rodar um programa KofScript com muitos `spawn` concorrentes (ex.: 25 `spawn calc(t)` + `await`) falhava intermitentemente (~1/120 runs) com `java.util.ConcurrentModificationException` — stack: `HashMap.computeIfAbsent` → `KofInterpreterMembers.kofStatics:159` → `ensureInit` → `dispatch`. Pré-existente (não é regressão do fix 53 — o teste `concurrentAwaitReturnsOwnTaskResult` do RACE/bug 47 o pegou na suíte).
- **Causa raiz:** `staticFields` e `initialized` eram `HashMap` comuns mutados por virtual threads do `spawn` (cada task chama `ensureInit`/`kofStatics` na mesma classe). Além do CME, `initialized.putIfAbsent` tinha corrida **semântica**: o thread perdedor do claim retornava com os statics ainda não-semeados (o `<clinit>` do vencedor não tinha terminado) — a JVM garante que `<clinit>` roda exatamente uma vez e os outros **esperam**.
- **Correção (lane interpreter):** `staticFields`/`initialized`/`initLocks`/`claimed` → `ConcurrentHashMap`; `ensureInit` com lock por classe (`synchronized (initLocks[name])`): fast-path `initialized` (done), `claimed` só detecta reentrância do mesmo thread (clinit→método da própria classe), perdedores bloqueiam até o vencedor terminar. Cadeia de super é árvore → ordem de lock sub→super consistente → sem deadlock. Prova: probe `CmeFull` 120/120 runs ok (antes: falha em ~1/120) + `KofScriptTest.concurrentAwaitReturnsOwnTaskResult` (25 tasks × 8 runs) verde na suíte.
- **Descoberto:** 07/09 (validação do fix 53 na suíte completa).

### 56. `String.split` (qualquer array + `.get`/`.size`) → `ClassFormatError: Illegal class name ""` (GitHub #30) — ✅ CORRIGIDO 07/09 (lane codegen JVM)

- **Sintoma (GitHub issue #30, reportado por domfelipe):** `"abc".split(...)` em seguida `parts.get(0)`/`parts.size` gerava `ClassFormatError: Illegal class name "" in class file Default/Main` em tempo de carga.
- **Reproduzido no repo (07/09, probes `Iss3031`/`SplitIr`/`RefRun`):** a constant pool da classe gerada continha `#41 = Class ""` + `#45 = Methodref "".get:(I)Ljava/lang/Object;` — o `parts.get(0)` sobre `String[]` foi baixado como chamada de método com owner vazio; `parts.size` (property) virava `getfield "?".size`.
- **Causa estrutural (não era o split — era ARRAY):** `MethodCallExpr`/`FieldAccessExpr` com receiver `ArrayType` caía no fallback genérico (`KofCall(owner=ArrayType)`/`KofLoadField(owner=ArrayType)`); `JvmTypeMapper.toInternalName` não tem mapeamento para `ArrayType` e produzia internalName vazio → `Methodref ""` → `ClassFormatError`. O mesmo valeria para `new Int[n].get(i)` etc.
- **Correção (07/09, `c9ecc36`):** `ExpressionInstanceCallLowerer`: `.get(i)` em array → `KofArrayLoad` (AALOAD); `.size`/`.length`/`.count` → `KofArrayLength` (ARRAYLENGTH); demais métodos → SEM025 (R6, sem bytecode inválido). `ExpressionLowerer` (property): `arr.size`/`arr.count` → arraylength. `MethodCallTyper`/`ExpressionTyper`: `.get(i)` tipa como componente, `.size/.length` como `Int` — sem isto o emit gerava `String.valueOf(Object)` sobre stack `int` → `VerifyError` (segunda face do mesmo bug, achada no `-Xverify:all`).
- **Prova:** `CoreRegressionE2ETest.stringSplitArrayAccess` (JVM+JS: `3`/`a`/`3`); probe: antes `ClassFormatError`, após `3`/`a`.
- **Descoberto:** 07/09 (GitHub #30; reproduzido + IR dumpado).

### 57. `await` sobre handle em parâmetro/campo `Object` → VerifyError; `Handle<T>` declarado → classe inexistente (GitHub #31) — ✅ CORRIGIDO 07/09 (lane codegen JVM)

- **Sintoma (GitHub issue #31, reportado por domfelipe):** `val h = spawn f()` / `spawn { lambda }` geravam bytecode inválido (`VerifyError`). No estado atual da beta as formas básicas já passavam (bug 29 corrigido em `7ec8b9d`), mas a varredura de formas achou 2 faces vivas: (a) `Int take(Object h) { return await h }` → `VerifyError: Type 'java/lang/Object' not assignable to integer` no `ireturn`; (b) `Handle<Int>` como tipo declarado (parâmetro/campo/retorno) → `ClassNotFoundException: Handle` (descriptor `LHandle;` — pacote vazio, classe inexistente).
- **Reproduzido no repo (07/09, probes `HandleHunt`/`SpawnVerify` com `-Xverify:all`):** 6 formas de handle mapeadas; `objHandle`/`handleField` VerifyError, `typedHandle`/`handleList` ClassNotFoundException.
- **Causa raiz:** `Type.of`/`JvmTypeMapper` mapeavam `Channel`→`kof.concurrent`/`LinkedBlockingQueue` mas **não** `Handle` — `Handle<Int>` declarado virava `ClassType("", "Handle")` → descriptor de classe inexistente; e o typer do `await` (exige pacote `kof.concurrent`) divergia do lowering (não checa) → double unbox. Na face Object: `kof_await` devolve `Object` boxed e `emitWideningIfNeeded` só fazia widening primitivo→primitivo — sem unbox quando o valor é apagado (Unknown/Object) e o slot declarado é primitivo.
- **Correção (07/09):** (1) `Type.of`: `Handle<T>` → `ClassType("kof.concurrent","Handle",args)`; (2) `JvmTypeMapper`: `Handle`→`java/util/concurrent/CompletableFuture` (o runtime de spawn É um) no descriptor + internalName; (3) `JvmOpCollections.emitKofRuntimeCall`: checkcast também p/ `kof.concurrent` (skip obsoleto, escrito quando Handle mapeava p/ classe inexistente — Channel não regride: `kof_channel_new` tem descriptor específico); (4) `CompilerEmissionHelpers.emitWideningIfNeeded`: valor apagado (Unknown/Object/TypeVariable) → slot primitivo = unbox (`kof_unbox` com CHECKCAST).
- **Prova:** `CoreRegressionE2ETest.awaitOnHandleThroughObjectParam` + `awaitOnTypedHandleParam` (JVM, 42); probes HandleHunt/SpawnVerify/ChanTest3 15/15 com `-Xverify:all`; suíte completa verde.
- **Descoberto:** 07/09 (GitHub #31; formas básicas já vivas pelo bug 29, faces Object/declarado novas).

### 58. `json.decode` de record com campo `List<Record>` → elementos chegam como mapas crus (GitHub #34) — ✅ CORRIGIDO 07/09 (lane codegen JVM + runtime)

- **Sintoma (GitHub issue #34, reportado por domfelipe):** `json.decode` de uma coleção de records não tipa os elementos — eles chegam como mapas crus (`LinkedHashMap`), e o acesso posterior causa `ClassCastException`.
- **Reproduzido no repo (07/09, probes `Json34`/`Json34b`):** o caso canônico é um record com campo coleção de records: `record User(String name, List<Addr> addrs)` + `json.decode<User>(…)` → `u.addrs().get(0)` é `LinkedHashMap`, não `Addr` → `ClassCastException: LinkedHashMap cannot be cast to Addr`. (O caso `List<User>` plano já funcionava — bug 48 corrigido 07/09.)
- **Causa raiz:** o record é emitido com o campo `addrs:Ljava/util/ArrayList;` **sem** o atributo `Signature` do class file (genérico apagado). O runtime `kof_json_bind` só recebe `Class<?>` (erasure: `List`), então o ramo de coleção retornava `new ArrayList(l)` cru, sem bindar os elementos p/ `Addr`.
- **Correção (07/09):** (1) `JvmTypeMapper.toGenericSignature` — assinatura genérica (type-args embrulhados) espelhando `toDescriptor`; emitida em `visitField`/`visitRecordComponent` (guard: tipos UI/media apagados p/ `I` ficam sem signature). (2) `kof_json_bind` ganha overload com `java.lang.reflect.Type generic` (via `RecordComponent.getGenericType`/`Field.getGenericType`) e binda recursivamente os elementos da lista (`listElement`/`bindByType`). (3) Variante `List<List<T>>` (elemento é coleção builtin → `Class.forName("kof.List")` CNFE silencioso) agora dá **JSN004** (gap honesto, R6) no lugar do crash.
- **Prova:** `CoreRegressionE2ETest.jsonDecodeRecordWithListOfRecords` (JVM: `x`/`y`); probes Json34/Json34b: `userWithListAddr`/`listUserNested` OK, `nestedList` → JSN004; suíte completa verde.
- **Descoberto:** 07/09 (GitHub #34; caso canônico = campo `List<Record>`, não só `List<Record>` no topo).

### 59. REGRESSÃO Native riscv64/aarch64: `println` → `undefined reference to kof_static_java_lang_System_out` no link (59 testes vermelhos) — ✅ CORRIGIDO 09/09 (lane Native)

- **Sintoma:** desde `62423bf` (fix bug 41, "campo estático dava lixo", lane Native), os 59 testes `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` falham no link: `aarch64-linux-gnu-ld: undefined reference to 'kof_static_java_lang_System_out'`. Qualquer programa com `println`/`print` quebra nos 2 archs. **x86_64 (`NativeE2ETest`) passa** — o autor provou só x86.
- **Bissect:** verde em `4a073ff`, vermelho em `62423bf` (e `756e7b3` não conserta). Reproduzido standalone (probe `RiscvS`): o `.s` riscv64 gerado tem `la t0, kof_static_java_lang_System_out` (referência) mas **nenhuma definição** `kof_static_java_lang_System_out:` no `.data`.
- **Causa (diagnóstico read-only — não corrigi, lane Native + `nat/` EM CURSO no REFACTOR-500):** `NativeBackend.emitStaticData` (`nat/NativeBackend.java:185`, chamado em `:281`) itera `staticFieldSymbols` e emite os `.quad`. No caminho riscv/aarch o símbolo referenciado por `NativeRiscvCrossEmit:128` (`KofGetStatic` → `nb.staticSymbol(...)`) não chega ao mapa de dados emitido — ou o `collectStaticFields` não coleta o `System.out` do receiver quando o lowering riscv o trata como builtin de print (o fix 62423bf "descarta o receiver System.out" em `NativeX86Calls`/`NativeRiscvCrossOps`), ou o `emitStaticData` não roda no emit riscv/aarch. O x86 define o símbolo; riscv/aarch referenciam sem definir.
- **Correção (lane Native):** garantir que o símbolo estático referenciado por `KofGetStatic` no caminho riscv/aarch seja emitido no `.data` (mesmo `emitStaticData` do x86), OU que o `KofGetStatic` de `System.out` seja descartado no riscv/aarch como no x86 (não referenciado). Prova esperada: `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 26/26 + gate `check_500`.
- **CORRIGIDO 09/09:** causa confirmada = `emitRiscv`/`emitAarch64` em `NativeArchEmitter` não chamavam `collectStaticFields()`/`emitStaticData(sb)`, então os símbolos estáticos (ex: `kof_static_java_lang_System_out`) referenciados por `KofGetStatic` no `NativeRiscvCrossEmit` nunca eram definidos no `.data` do riscv/aarch (o x86_64 passava porque o `emit()` do `NativeBackend` os emite). Fix: adicionado `nb.collectStaticFields()` + `nb.emitStaticData(sb)` nos dois caminhos (`emitRiscv` e `emitAarch64`), logo após a emissão dos string literals (`.quad`/`.asciz` são direções ELF universais). Prova esperada: `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` de volta ao verde.
- **Impacto no gate:** suíte completa vermelha (59 falhas) desde `62423bf` — não é regressão da lane KOFSCRIPT (nenhum arquivo `nat/` tocado por mim; bissect prova).
- **Descoberto:** 07/09 (validação do fix #35.2 na suíte completa).

### 60. `http.<verb>` com 2+ headers como argumentos separados → crash/SEM025 (GitHub #32) — ✅ CORRIGIDO 07/09 (lane compiler/http)

- **Sintoma (GitHub issue #32, reportado por domfelipe):** `http.post(url, body, "Content-Type: …", "X-Test: abc")` (4 args) crashava o compilador (COMP002 frame crash no ASM). Com 1 header funcionava.
- **Estado na beta ao reproduzir (07/09, probes `Http32`/`Http32b`/`Http32Run`):** o crash já tinha sido contido pelo trabalho SEM025 (`65e2dc0`) — 4+ args caíam em `staticCall` null → SEM025 honesto. Mas o pedido real do plano (BUG 5) é **múltiplos headers funcionarem**, não só diagnosticarem.
- **Causa:** a API modela headers como UMA String `\n`-separada (`kof_http_<verb>_headers` com 2/3 parâmetros); `staticCall` só casava aridade exata 1-3 → 4+ args sem correspondência.
- **Correção (07/09):** headers variádicos — `KofHttp.staticCall` aceita `>=2`/`>=3` args (get/delete/options e post/put/patch); `ExpressionHttpCallLowerer` emite url/body fixos e mescla os headers extras em um slot com `concat(acc, "\n", h)` (o runtime já splita por linha). Aditivo: 1 header (forma antiga) não muda de caminho.
- **Prova:** `KofHttpE2ETest.multipleHeadersAsVariadicArgs` (3 headers → servidor ecoa `A=1 B=2 C=3`); probe post-4args/get-3args/post-5args compilam; E2E runtime com servidor Kof recebendo os 3 headers individualmente.
- **Descoberto:** 07/09 (GitHub #32; corpo do issue obtido via API).

### 61. FFI nativo: `dlopen`/`dlsym` segfaultam no binário nativo (sem init do glibc) — ✅ gap honesto FFI001 implementado (08/09); correção real (glibc init) = trabalho futuro

- **Sintoma:** um `extern` compilado para NATIVE gera um binário que **segfaulta
  (exit 139)** ao chamar `dlopen`. Na main, `FfiE2ETest.libcAbsEndToEndNative`
  falhava 3/5 (o teste "verde no papel" nunca rodou de verdade — reconciliação
  R1 já havia sinalizado o FFI da planning-future como não-executado).
- **Reprodução mínima (fora do Kof):** programa asm com `_start` cru +
  `call dlopen@PLT`, link `ld -o t t.o -dynamic-linker /lib64/ld-linux-x86-64.so.2
  -lc` (o MESMO link command do `NativeAssembler`) → SIGSEGV dentro de
  `dl_open_worker` (glibc). O MESMO `_start` chamando `strlen@PLT`/`abs@PLT`
  direto → funciona. gcc normal (crt1.o + `__libc_start_main`) → funciona.
- **Causa raiz:** o binário nativo da Kof usa `_start` próprio (syscalls
  diretos, sem `__libc_start_main`); o `dlopen` do glibc exige TLS/estado
  inicializado pelo loader do libc, que nunca roda. `dlopen` é o ÚNICO caminho
  do `NativeFfiRuntime` (main) — por isso crasha.
- **Comportamento previsto (R6):** `extern` em NATIVE emite **gap honesto
  FFI001** em compile-time (nunca binário que segfaulta). Implementado na beta
  (merge main→beta, 08/09): `CompilerPipeline.isExternBound` retorna false p/
  NATIVE; `NativeFfiRuntime` (asm morto) removido.
- **Correção (lane Native):** inicializar o runtime do glibc no `_start`
  (chamar `__libc_start_main` / usar crt1) OU resolver símbolos via
  `dlsym`-free (link direto `-l<lib>` + `call sym@PLT`, que funciona — provado
  acima). Prova esperada: `FfiE2ETest` nativo verde + gate `check_500`.
- **Descoberto:** 08/09 (porte do FFI da main para a beta; probes `dltest.s`/
  `pltest.s` com o link command real do backend).

---

### 62. Frontend não valida mutabilidade: `val` é decorativo e escrita em componente de record diverge nos 3 caminhos (GitHub #42) — ✅ CORRIGIDO 09/09 (sintomas a/b/c)

- **Sintoma (a):** `main() { val x = 1; x = 2; println(x) }` → `kof check` "no
  errors" e imprime **`2`** no JVM, KofJS e interpretador. `val` não é imutável.
  Idem com compound (`val x = 1; x += 5` → `6`) e referência (`val s = "a";
  s = "b"` → `b`).
- **Sintoma (b):** `record P(Int x)` + `p.x = 9` passa no `kof check` e dá **3
  comportamentos**: JVM `IllegalAccessError: tried to access private field P.x`;
  KofJS `TypeError: p.x is not a function` (a atribuição cria propriedade que
  sombreia o accessor); interpretador **imprime `9`** (muta o record em
  silêncio). Native não verificado (host arm64 sem toolchain x86_64-linux).
  Vale igual para `class P(Int x) { }` — o parser trata como record (`javap`:
  `final class P extends java.lang.Record`, campos `private final`).
- **Sintoma (c):** `record P(Int x) { bump() { this.x = 99 } }` → JVM
  `IllegalAccessError: Update to non-static final field P.x attempted from a
  different method (bump)`; KofJS imprime `99` (mutação silenciosa).
- **Causa raiz:** `StatementAnalyzer.analyzeAssignmentStatement` é o checkpoint
  de toda atribuição-statement e valida **apenas** compatibilidade de tipo
  (SEM012) — nunca pergunta se o alvo é atribuível. Em todo o `kof-compiler` as
  únicas mensagens "cannot assign" são SEM012/SEM021, ambas de type mismatch:
  não existe checagem de mutabilidade. Sem diagnóstico no frontend o lowering
  emite o store cegamente (`putfield P.x:I` em campo `private final` de outra
  classe no JVM — confirmado por `javap -c`).
- **Regras violadas:** R6 (nunca silencioso), paridade cross-target, semântica
  congelada 0.2.6-beta (`val` documentado como imutável).
- **Correção proposta:** checagem de mutabilidade em
  `analyzeAssignmentStatement` + diagnóstico novo (`SEM0xx: cannot assign to
  immutable <nome>`) para (a) símbolo `val` e (b) componente de record.
- **Sintoma (a) CORRIGIDO 09/09:** `parser/StatementParser.parseVarDecl` agora
  carrega `type="val"` para `val` (antes sempre "var" → o flag nunca chegava ao
  analisador); `SymbolTable.LocalVariableSymbol` ganhou campo `isVal` (construtor
  compacto de 3 args preserva os call sites de catch/loop/pattern); `StatementAnalyzer.
  analyzeAssignmentStatement` emite **SEM037** ("cannot assign to immutable 'val'
  variable") para reatribuição (incluindo compound `+=`) de símbolo val;
  `StatementAnalyzer`/`StatementLowerer`/`CompilerFunctionLowering` tratam "val"
  como keyword (como "var") na inferência de tipo. Testes:
  `CompilerDriverTest.{assignmentToValGivesCleanDiagnostic,compoundAssignmentToValGivesCleanDiagnostic,varRemainsMutable}`.
- **Sintomas (b)+(c) CORRIGIDOS 09/09 (`cd0da824` + este commit, checkpoint
  único `StatementAnalyzer.analyzeAssignmentStatement`):** alvo `FieldAccessExpr`
  agora resolve o tipo do receiver (ou `currentClassName()` p/ `this`) e, se
  `CompilerTypes.isRecordType`, emite **SEM038** ("cannot assign to 'x': record
  is immutable"). (b) `p.x = 9` e (c) `this.x = 99` em MÉTODO de record viram
  erro de compilação nos 4 caminhos (JVM `IllegalAccessError`, JS `TypeError` e
  interp silencioso tornam-se inalcançáveis — paridade cross-target). A escrita
  `this.x =` só é exempta DENTRO DE CONSTRUTOR (init do campo final, JVMS 4.4):
  flag `inConstructor` salvo-restaurado em `analyzeConstructorBody`. Furo
  adicional fechado no mesmo checkpoint: o `update` do `for` tinha atalho que só
  inferia tipos (sem checagem de atribuição) → `for (val i = 0; i < 2; i = i + 1)`
  era silencioso; agora usa `analyzeAssignmentStatement` (SEM012/037/038 de
  graça). Prova CLI: check nos 4 cenários (b/c erro; ctor de record ok; classe
  mutável ok) + suíte 1154/0-falhas-minhas.
- **Arquivos:** `StatementAnalyzer.java` (`analyzeAssignmentStatement`),
  `SemanticAnalyzer.java`.
- **Cobertura:** nenhum teste da suíte cobre imutabilidade (busca por
  `immutab|reassign|cannot assign to` em `kof-compiler/src/test/java` → zero).
- **Descoberto:** 08/09 (probe manual; JVM + KofJS + interpretador).

---

### 63. KofJS: atribuição a PARÂMETRO emite `let` redeclarado → SyntaxError derruba o módulo inteiro (GitHub #43) — ✅ CORRIGIDO (testes `CoreRegressionE2ETest.{compoundAssignmentToParameterDoesNotRedeclareInJs,classMethodParameterReassignmentDoesNotRedeclareInJs,lambdaParameterReassignmentDoesNotRedeclareInJs}`)

- **Sintoma:** `Int f(Int a) { a = 99; return a }` → JVM e interpretador dão
  `99`; KofJS falha no *parse* com
  `SyntaxError: Variable "a" has already been declared`. JS gerado:
  `function f(a) { let a = 99; return a; }`. Por ser erro de parse, derruba o
  **módulo inteiro**, não só a função.
- **Alcance (todos confirmados; JVM e interpretador corretos em todos):** função
  top-level, duas atribuições ao mesmo parâmetro, compound `a += 1`, método de
  classe, e lambda `(n: Int) -> { n = 3; return n }`.
- **Causa raiz:** `JsExpressionParser.storeLocalStatement` decide declaração vs
  atribuição por "primeiro store no slot" (`if (ctx.declared.add(sl.index()))`
  → `JsVarDecl`). Correto para locais, errado para parâmetros — que já estão
  ligados pela assinatura da função JS. O construtor de `MethodCtx` popula
  `localNames`/`rawLocalNames`/`captureSlots` mas **nunca semeia `declared` com
  os slots dos parâmetros**. O cálculo da faixa já existe em
  `JsMethodParser.parseMethodBody` (`paramStart`/`paramEnd`), mas roda DEPOIS de
  `flow.parseStatements(...)` — o `let` já foi emitido.
- **Correção proposta:** semear `ctx.declared` com os slots `paramStart..paramEnd`
  ANTES de parsear o corpo (construtor de `MethodCtx` ou topo de
  `parseMethodBody`), reusando o cálculo existente.
- **Arquivos:** `js/JsExpressionParser.java` (`storeLocalStatement`),
  `js/MethodCtx.java` (construtor), `js/JsMethodParser.java` (`parseMethodBody`).
- **Native:** não verificado (host arm64/macOS sem toolchain x86_64-linux).
- **Descoberto:** 08/09 (probe manual da matriz de mutabilidade).

---

### 64. KofJS: parâmetro após um `Long`/`Double` é descartado da assinatura e lê `undefined` (GitHub #47) — ✅ CORRIGIDO (testes `CoreRegressionE2ETest.{parameterAfterALongIsNotDroppedFromTheJsSignature,parameterAfterADoubleIsNotDroppedFromTheJsSignature}`)

- **Sintoma:** `Int after(Long a, Int b) { return b }` + `main() { println(after(1L, 42)) }`
  → `kof check` "no errors"; JVM imprime `42`; **KofJS imprime `undefined`**.
  Sem erro, sem diagnóstico — resposta errada em silêncio. O JS emitido é
  `function after(a) { return b; }`: o parâmetro `b` some da assinatura.
- **Matriz verificada (08/09):** `after(Long a, Int b)` → `undefined`;
  `after(Double a, Int b)` → `undefined`; `before(Int b, Long a)` → `42` ✅;
  `onlyWide(Long a)` → `42` ✅. Só quebra quando o parâmetro largo **não** é o
  último — com ele por último o truncamento não descarta nada, e foi por isso
  que passou despercebido.
- **Efeito colateral:** o parâmetro perdido reaparece como local pré-declarado.
  `Int wide(Long a, Int b, Double c)` emite `function wide(a, b) { let c; ... }`.
- **Causa raiz:** `Long`/`Double` ocupam DOIS slots, mas a montagem da
  assinatura assume um slot por parâmetro. O laço de `parameterSlots` percorre
  `0..localNames.size()`, enquanto os índices são ESPARSOS com parâmetro largo:
  em `f(Long a, Int b)` o mapa é `{0:a, 2:b}` e `size()` é 2, então o laço vai
  até `i = 1` e para antes do slot 2. Correção: percorrer as chaves reais de
  slot em ordem crescente.
- **Arquivos:** `js/JsMethodParser.java` (`parameterSlots`/`parameterNames`).
- **Não tem relação com o bug 63** (GitHub #43, `let` redeclarado): reproduzido
  antes e depois daquela correção, com saída idêntica nos dois estados.
- **Prova/repro:** `CoreRegressionE2ETest` +4 casos no PR #48 (JVM e KofJS com
  saída idêntica exigida); falhavam com `expected: <42> but was: <undefined>`.
- **Native:** não verificado (host arm64/macOS sem toolchain x86_64-linux).
- **Descoberto:** 08/09, durante a correção do bug 63.

---

### 65. Frontend JS/browser: `Audio`/`Video` em `Window.show()` não chegam ao DOM real (Chrome) — ABERTO (lane UI)

- **Introduzido por:** merge da PR #39 (`kof-ui-media-widgets`) em beta-0.3.0
  (08/09) — não é regressão de outra lane (confirmado: falha no HEAD limpo,
  sem o WIP da stdlib).
- **Sintoma:** `KofJsBrowserE2ETest.audioRendersInRealBrowserDom` e
  `videoRendersInRealBrowserDom` falham — `<audio>`/`<video>` ausentes no DOM
  dumpado pelo Chrome. A compilação JS passa; só a renderização no browser não
  monta o widget.
- **Menor repro:**
  ```kof
  main() {
      var audio = Audio("song.mp3")
      audio.setControls(true)
      var col = Column(listOf(audio))
      var w = Window("AudioTest")
      w.bind(col)
      w.show()
  }
  ```
  `kof build --target js` → abrir no Chrome → o DOM não contém `<audio>` nem
  a classe `kof-audio`. (`Video("clip.mp4")` idem.)
- **Causa raiz (provável, não confirmada — é da lane UI):** serialização dos
  widgets de mídia não integrada no mesmo caminho de `kofUiSerializeHtml`
  que `Window.show()` usa. Ver CANVAS001 (linha 1306) — problema análogo de
  timing de serialização de widget sem janela/sem ganchos.
- **Verificado 09/09 (por leitura de código — descarta hipóteses):** o pipeline
  JS de mídia está CORRETO em todos os pontos, então a causa não é o mapeamento
  de nome nem a ordem de concatenação:
  - lowering: `Audio("x")`/`Video("x")` → `kof_ui_audio_new`/`kof_ui_video_new`
    (`ExpressionUiStaticLowerer`);
  - whitelist: `kof_ui_audio_*`/`kof_ui_video_*` em `JsRuntimeOps`;
  - nome JS: `capitalizeUiFn("kof_ui_audio_new")` = `kofUiAudioNew` (define
    exportada em `JsRuntimeUiForms`);
  - ordem do bundle (`JsArtifactWriter`): Core → Components (declara
    `kofNodeSeq`) → Widgets (`kofUiCreateNode`) → Forms — `kofNodeSeq` no escopo;
  - montagem: `kofUiCreateNode` registra no `__kofNodes`; `Column` faz
    appendChild; `WindowNew` → `root.appendChild(winEl)`; `WindowBind` →
    appendChild do column; `kofSerialize` serializa `<video src>`/`<audio src>`.
  → A falha é de TEMPO DE RUNTIME/ordem de montagem no browser (requer depurar
  com Chrome devtools), não de codegen. O teste `dumpDom` usa `--dump-dom
  --virtual-time-budget=8000`.
- **Impacto na gate:** 2 testes vermelhos fora do par riscv/aarch (bug 59)
  para qualquer agente que rode a suíte completa com Chrome instalado.
  Quem corrigir: UI lane (dono da PR #39).

### 66. `record` com construtor explícito canônico → `<init>` duplicado (ClassFormatError no JVM) — ✅ CORRIGIDO 09/09

- **Sintoma (issue #53):** `record P(Int x) { constructor(Int x) { this.x = x } }` → o
  record gera **dois** `<init>`: o automático (`CompilerRecordSupport.generateRecordConstructor`)
  SEMPRE adicionado em `CompilerClassLowering.lowerRecord` + o explícito do usuário
  (`lowerConstructor`) → `ClassFormatError: <init> duplicado` no load JVM.
- **Correção (09/09):** `lowerRecord` agora verifica se o record declara um construtor
  explícito com a MESMA aridade do canônico (número de componentes) e, nesse caso,
  NÃO gera o automático (o explícito é lowered e substitui). Construtores não-canônicos
  (aridade diferente) continuam somando (canônico + overload). Prova:
  `CompilerDriverTest.recordWithExplicitCanonicalConstructorCompilesToJvm`.

### 67. Interpretador: `super(v)` explícito em classe de domínio → StackOverflowError (issue #54) — ✅ CORRIGIDO 09/09
### 68. If/switch-expression com branches heterogêneos primitivo-vs-referência → VerifyError no JVM (issue #57) — ✅ CORRIGIDO 09/09 (posições de expressão; slots primitivos seguem ABERTOS)

- **Sintoma (issue #57):** `println(if (s == "") 1 else "s")` → check aprova,
  JVM rejeita: `VerifyError: Bad type on operand stack @25 invokestatic`
  (`Integer.valueOf` recebendo String). Variante `var x = ...` e `switch`
  heterogêneo como var-init → VerifyError no store. Nota de ambiente: no
  JDK 25 (Temurin) o mesmo .class inválido aborta no launcher com a mensagem
  "JavaFX runtime" em vez de VerifyError (disfarce já catalogado no bug da
  variante SEM036, §461) — ground truth no JDK 21.
- **Causa raiz:** o typer devolve o thenType (primeiro case no switch) e
  IGNORA o else; os 5 sites de box pós-expressão (`ExpressionPrintLowerer`,
  `CompilerEmission2` args, `ExpressionAssignmentLowerer`, `StatementLowerer`,
  `CollectionCallLowerer`) aplicavam `kof_box(thenType)` DEPOIS do join →
  `Integer.valueOf` sobre o valor do ramo String.
- **Correção (09/09, lane issues+migração — só codegen, check inalterado):**
  predicado `ExpressionTyper.{ifNeedsInnerBox,switchNeedsInnerBox,
  boxesOwnBranches}` (heterogêneo = exatamente um lado primitivo);
  `ExpressionLowerer`/`SwitchExprLowerer` boxeiam o ramo primitivo IN-branch
  (`emitErasureBox`, JVM-only); os 5 callers pulam o pós-box p/ esses nós.
  Prova: `ConformanceMatrixTest` casos `ifexpr-heterogeneous-direct` +
  `switchexpr-heterogeneous-direct` (JVM+Native+Script verdes; JS excluído —
  ver 69) + probes `objdecl`/`objassign` (slot Object) imprimindo `1`.
- **ABERTO (mesma issue, status quo — nunca rodou, sem regressão):**
  (a) `var x = if (c) 1 else "s"` (slot inferido Int) e `Int x = ...`
  explícito → VerifyError no store; alargar o slot p/ Object mudaria o tipo
  visível de `x` (`x+1` hoje é check-error com `Object`, provado por probe
  `objplus`) → decisão de contrato, não fix silencioso;
  (b) heterogêneo primitivo-vs-primitivo distinto (`1 else 2L`) → crash do
  backend (`frame crash ... COMPUTE_FRAMES AIOOBE`, causa distinta:
  slot-size 1 vs 2 no join) — ver 70.

### 69. KofJS: if heterogêneo → `expression stack underflow` (COMP002) (issue #69) — ✅ CORRIGIDO 09/09

- **Sintoma:** o MESMO programa da issue #57 (`println(if (s == "") 1 else "s")`)
  no target JS: `Internal compiler error: KofJS: expression stack underflow`
  (COMP002), em vez de JS válido.
- **Causa raiz:** Em `JsExpressionStatementParser.java`, ao processar `KofConditionalJump`,
  o compilador drenava incondicionalmente toda a pilha de operandos acumulada até então
  (`while (!stack.isEmpty())`) para dentro da `condition` antes de determinar se o salto
  era um `if-expression` ou um `if-statement`. Quando a expressão ocorria como argumento
  de uma chamada de função (ex.: `println(...)`), o receiver `$kofOut` que já estava na pilha
  era descartado prematuramente. Ao terminar de emitir o `ifExpr`, apenas o resultado da
  expressão ficava na pilha, fazendo com que a chamada de função subsequente falhasse com
  `expression stack underflow`.
- **Correção:** O empacotamento de preâmbulo na condição só é executado se `tryParseIfExpr(...)`
  retornar `null` (ou seja, quando for comprovadamente um `if-statement`). Em `if-expression`,
  os operandos prévios na pilha permanecem intactos.
- **Provas:** Todos os 5 testes da `ConformanceMatrixTest` com branches heterogêneos
  (`ifexpr-heterogeneous-direct`, `switchexpr-heterogeneous-direct`, `ifexpr-intlong-direct`,
  `ifexpr-longdouble-direct`, `ifexpr-intnull-direct`) foram reabilitados para o target JS
  (remoção de `Set.of("js")`), passando com sucesso no KofJS.

### 71. JVM: array multidimensional `new Int[2][3]` compila e dá VerifyError — ✅ CORRIGIDO 09/09

- **Sintoma:** `var arr = new Int[2][3]` + `println(arr.length)` → check aprova,
  JVM rejeita no load: `VerifyError: Bad type on operand stack`.
- **Causa raiz (bytecode):** o lowering emitia `iconst_2; newarray int` (só a
  1ª dimensão) e tratava o `[3]` como INDEX (`iaload 3`) + `getfield length`
  sobre int → inválido. Repro provado: bytecode `05bc 0a06 2e3c ...`.
- **Correção (09/09, lane issues+migração — assumida da nota abaixo):** 3 camadas:
  (1) parser consome dims adicionais → `NewArrayExpr.moreDims` (record estendido,
  ctor 1-dim preservado — retrocompat; `new T[2][]` vazio segue PARSE046);
  (2) novo op IR `KofNewMultiArray(baseType, dims)` no `ExpressionLowerer`
  (+ typers renderizam ArrayType aninhado; formatter/capturas varrem moreDims);
  (3) emitters: JVM `MULTIANEWARRAY` (desc via `arrayTypeOf`+`toDescriptor`),
  interpretador `Array.newInstance(comp, lens)`, JS `JsNestedArray` + runtime
  `kofMultiArray(sizes, dims, baseFill)` (semântica JVM: dims-1 preenchidas com
  arrays vazios, NÃO recursivo) + import registrado + whitelist `isExpressionOp`.
  `computeStack` conta o op (`depth -= dims-1`).
- **Provas:** repro JVM `exit=0 out=2` (antes VerifyError), interpretador
  `stdout=2`, JS real (node) `2` com import gerado no Default.mjs;
  teste `multidimensionalArrayAllocatesAllDims` (JVM+JS: `2/3/0/2/4/0`,
  Int[2][3]+Long[2][3][4]); suíte completa 1200+25+5+126 = 1356/0/78-skip.
- **Nota de história:** forma `new T[a][b]` existia na sintaxe sem semântica
  (decisão da mantenedora pendente) — resolvido implementando o lowering
  aditivo (comportamento previsível, sem mudar a forma 1-dim congelada).

### 70. JVM: heterogêneo primitivo-vs-primitivo como arg → crash do backend (`COMPUTE_FRAMES AIOOBE`) — ✅ CORRIGIDO 09/09 (posições de expressão; slots primitivos seguem ABERTOS)

- **Sintoma:** `println(if (s == "") 1 else 2L)` → check aprova, mas o COMPILADOR
  crasha (`frame crash ... ASM COMPUTE_FRAMES ArrayIndexOutOfBounds`) em vez
  de emitir diagnóstico ou bytecode válido.
- **Causa (distinta do §68):** int ocupa 1 slot, long/double 2 — o join tem
  tamanhos de pilha diferentes; o backend não normaliza. Não é o box (que é
  por tipo, não por tamanho).
- **Correção (09/09, lane issues+migração — generaliza o §68):** sem widening
  (que mudaria valor impresso: `2L`→`2.0`, divergindo do interpretador):
  cada ramo primitivo é boxeado p/ SEU PRÓPRIO boxed (`Integer`/`Long`/
  `Double`), join só de referências. Mecanismo único: `branchTypesDiffer`
  (só tipos concretos; `Unknown`/`TypeVariable`/lambda → status quo) +
  `boxPrimitiveBranch` + `boxesOwnBranches` nos 5 callers (predicado alargado
  de "prim-vs-ref" p/ "tipos distintos"; comportamento idêntico p/ #57).
  `null` literal conta como referência. Prova: probes JVM==script em
  intlong/longdouble/strlong/boolint-JVM/intnull (`2` imprime `2`, não `2.0`);
  matriz `ifexpr-{intlong,longdouble,intnull}-direct` (JVM+Native+Script;
  JS excluído — §69). Slots primitivos (`var x`/`Int x`) seguem §68(a).
- **Observado fora de escopo:** `bool` no interpretador imprime `1`

### 72. JVM: signature genérica de type-arg primitivo usa descriptor cru → GenericSignatureFormatError (GitHub #62) — ✅ CORRIGIDO 09/09

- **Sintoma:** `record Checkpoint(List<Double> params, Int step)` +
  `json.decode<Checkpoint>(j)` → `GenericSignatureFormatError:
  Remaining input: D>` no LOAD da classe (a classe nem carrega). O compile
  passa e o `json.encode` funciona.
- **Causa raiz:** `JvmTypeMapper.toGenericSignature` retorna `null` para
  type primitivo (assinaturas só aceitam referências), e o fallback no
  loop de type-arguments era `toDescriptor(arg)` — que para `Double` é `D`
  (válido em descriptor, INVÁLIDO dentro de `<...>` de signature, que só
  aceita `L...;`/`[`/`T`). Resultado: `Lkof/.../Checkpoint;<Lkof/...List;<D>;>...`
- **Correção (09/09):** helper `signatureTypeArg(Type)` no `JvmTypeMapper`:
  primitivo em posição de type-arg vira o BOXED (`Ljava/lang/Double;`,
  `Ljava/lang/Integer;`, ...), nullable unwrapa, resto cae no
  `toGenericSignature`→`toDescriptor` como antes. Mapeamento boxed via
  `boxedInternalName` (case dos nomes Kof e JVM). Campos `Double`/`Int`
  NUS (não em `<>`) não mudam — descriptor `D`/`I` continua correto lá.
- **Prova:** `CoreRegressionE2ETest.jsonDecodeRecordWithListOfDoubles`
  (encode→decode→acesso a `params().get(0/1)` + `step()` = `1.0/2.0/3`,
  ao lado do modelo `jsonDecodeRecordWithListOfRecords` #34); suíte
  CoreRegressionE2ETest 45/0. Repro J62 standalone: antes
  `GenericSignatureFormatError`, depois `exit=0 out={"params":[1.0,2.0],"step":3}`.

### 73. JVM: 2 labels de debug consecutivos → LNT com entries no mesmo pc → ClassFormatError no load (GitHub #63) — ✅ CORRIGIDO 09/09

- **Sintoma:** arquivo `.kf` grande (280 linhas, denso de `if`/`try`/`catch`/
  `finally`/`while` — repro real `lab.kof.old` de ThiagoLange) compila
  (`kof check` OK) mas o `kof run` falha no LOAD:
  `ClassFormatError: Invalid pc in LineNumberTable in class file Default/Main`.
  Regressão 0.3.2 (0.1.3 OK); arquivo compacto (15 linhas) passa.
- **Causa raiz (LNT, não lowering):** `JvmBackend.emitMethod` visitava um
  `visitLabel`+`visitLineNumber` ANTES do emit de cada op com debug-position
  de line nova. Statements seguidos cujos primeiros ops são `KofLabel` de IR
  (que NÃO é instrução real — `visitLabel` não avança o pc) geravam 2 labels
  de debug CONSECUTIVOS resolvendo para o MESMO `start_pc` → 2 entries de
  LineNumberTable no mesmo pc. Probes ASM (`Mk3`/`Mk5`): hotspot rejeita
  dup-pc MESMO com lines diferentes — e também fora de ordem/pc além do
  código. Arquivos densos: o epílogo de `while` (label end com pos da line
  do while) seguido do statement seguinte (line nova, zero instrução entre)
  é o padrão mais frequente (6 sites no repro).
- **Correção (09/09):** o label de debug é RETIDO (`pendingDebugLabel`) e só
  é visitado junto com a LNT quando uma instrução real for emitida — `KofLabel`
  de IR nunca limpa o pending nem dispara a visitação. Um novo debug-pos com
  pending retido SUBSTITUI (a line anterior descrevia zero insns); pending
  já visitado + nenhuma instrução real desde → novo label é skipado (as
  próximas instruções seguem descrevendo a line anterior — debug impreciso
  em vez de classe inválida, nunca falha de load).
- **Prova:** repro real (`lab.kof.old`, 280 linhas) compilada pelo driver:
  antes `exit=1 ClassFormatError Invalid pc` (JDK 21 + JDK 25), depois
  `exit=0` com output correto do dispatcher; `javap` LNT validada por
  parser (0 dup-pc/0 não-monotônico/0 overflow nos 13 métodos); scan
  `-Xverify:all` nas 26 classes do output = 0 falha (JDK 21). Teste
  `CoreRegressionE2ETest.largeDenseFileLoadsOnJvm` (60 blocos try/for/
  while/finally aninhados, ~420 linhas geradas → 2188). Suíte
  CoreRegressionE2ETest 46/0.

### 74. JVM: `+=` em elemento de array e campo estático qualificado sobrescreve o valor (GitHub #64) — ✅ CORRIGIDO 09/09

- **Sintoma:** o MESMO `+=` produzia resultado diferente por destino: local
  `10 += 5` = `15` (correto), mas `values[0] += 5` e `Counter.total += 5`
  com valor inicial `10` davam `5` (SOBRESCREVEU — não somou). Compila e
  roda sem erro.
- **Causa raiz:** os ramos `ArrayAccessExpr` e `FieldAccessExpr`-estático
  (`Class.field`) do `ExpressionAssignmentLowerer` ignoravam `ae.operator()`:
  emitiam receptor+índice+RHS+store direto — o mecanismo de compound só era
  alcançado pelo campo estático POR NOME SIMPLES, campo de instância (bug 40)
  e box local.
- **Correção (09/09):** campo estático qualificado: `GETSTATIC` + RHS +
  `KofBinary` + `PUTSTATIC` (sem receiver — estático não consome `this`).
  Elemento de array: `DUP2` (duplica o par [receiver, index]) + `AALOAD` +
  RHS + `KofBinary` + `AASTORE`. Novo op `KofDup2` emitido nos 4 backends
  (JVM DUP2, interpretador, Native x86_64, riscv cross, JS via temps).
  `+=` com String (elemento ou campo): mesmo mecanismo da concatenação
  (`boxPrimitive`+`valueOf`+`kof_string_concat`) — `names[0] += 9` = `ab9`.
  Widening do RHS p/ o tipo do destino (`Double *= 2`: int→double antes do
  DMUL — o literal int na pilha de DMUL dava frame inválido). No compound,
  o `emitPrimWidenNarrow` final NÃO re-aplica (o KofBinary já produziu o
  tipo do elemento — a conversão extra dava I2L sobre long → VerifyError).
  `computeStack` agora conta width real de `KofLoadLiteral`/`KofGetStatic`
  de long/double (getstatic Double é 2 slots no JVM real).
- **Prova:** repro da issue `15/15/15` (antes `5/5/15`, JDK 21+25, JVM run
  limpo com `-Xverify:all`); bordas: `Int[]` (`15/17/60`), `Long[]`
  (`15`/`1` — int em Long[] ok), `String[]` (`ab9`), `Double` estático
  (`5.0`); teste `compoundAssignmentOnArrayElementAndQualifiedStatic`;
  CoreRegressionE2ETest 47/0.

### 75. JVM: LineNumberTable aponta o statement SEGUINTE (linha do statement ausente, `}` herdando) (GitHub #66) — ✅ CORRIGIDO 09/09

- **Sintoma:** repro 6 linhas (`record P`, 2 prints): LNT = `3/5/6/5` em vez
  de `3/4/5` — a linha do statement `println(p.x())` (4) não existia na
  tabela; o `}` de fechamento (6) herdava entries; stacks traces apontavam
  a linha errada.
- **Causa raiz (2 defeitos independentes, ambos confirmados por instrumentação
  `kof.trace.debug`):**
  1. **Parser:** `new ExpressionStmt(ctx.pos(), expr)` capturava a posição
     DEPOIS do `expectSemicolon()` — o peek era o PRIMEIRO TOKEN DO
     STATEMENT SEGUINTE (ou o `}`). O statement herdava a linha do seguinte
     (+1). O mesmo padrão em `finishMethod`/`parseField` (ClassMemberParser),
     expression-body de função (Parser) e lambda-body (LambdaParser).
  2. **Cópia do KofDebugInfo:** `new HashMap<>(IdentityHashMap)` — ops são
     RECORDS e duas com o MESMO valor (2 `KofGetStatic` do `System.out` em
     prints diferentes) colidem por equals/hashCode: 1 entry, o último put
     vencia para AMBAS — a posição do print seguinte sobrescrevia a do
     anterior na LNT.
- **Correção (09/09):** posição capturada ANTES do parse em todos os 5 sites
  (ExpressionStmt/finishMethod/parseField/func-expression-body/lambda-body);
  a cópia do KofDebugInfo é `IdentityHashMap` (por identidade — instâncias
  iguais mantêm entradas próprias). Flag diagnóstico `kof.trace.debug` (dumpa
  os puts de posição por statement) fica como ferramenta permanente.
- **Prova:** IR pós-fix: ops do statement 4 todas @4 e do 5 todas @5 (antes:
  misto @4/@5 por colisão); LNT final = `3/4/5` (uma entrada por statement);
  teste `lineNumberTableMatchesSourceLines`; CoreRegressionE2ETest 48/0.

### 76. CLI: `kof build` ignora `.kof` (só varre `.kf`) e responde "no .kf files found" (GitHub #67) — ✅ CORRIGIDO 09/09

- **Sintoma:** `.kof` é extensão oficial (editor/kof.tmLanguage.json declara
  `fileTypes: [kf, kof]`). `run/check/test/fmt` aceitavam `.kof`; só o
  `build` não — e respondia `no .kf files found` para diretório E arquivo
  avulso, sugerindo diretório vazio.
- **Causa raiz:** o filtro de descoberta era `endsWith(".kf")` sem
  contemplar `.kof` — `KofCliSupport.collect`/`collectShallow` e o `Fmt`
  (diretório).
- **Correção (09/09):** filtro único `KofCliSupport.isKofSource(Path)`
  (case-insensitive: `.kf` OU `.kof`), usado pelos 3 sites; mensagem de
  diretório vazio atualizada p/ `no .kf/.kof files found` (4 sites:
  CmdBuild/CmdTest/Main×2).
- **Prova:** teste `KofSourceDiscoveryTest` 3/3 (collect aceita .kof+.kf e
  ignora .txt; collectShallow aceita .kof; extensão maiúscula .KOF); probe
  reflexão `collect` = 2 files (.kf+.kof no mesmo dir); kof-cli build/test
  verde.

### 77. JVM: `transaction` aninhado comita o escopo externo — rollback posterior não desfaz (GitHub #65) — ✅ CORRIGIDO 09/09 (JVM)

- **Sintoma:** bloco `transaction` dentro de outro executa `commit()` na
  MESMA conexão antes do externo terminar; o `throw` do externo depois
  disso deixa as linhas confirmadas no banco (`{"n":2}` com rollback
  seguinte). Controle sem o bloco interno: `{"n":0}` (rollback simples ok).
- **Causa raiz:** `JvmConfigRuntime.kof_db_transaction` obtém a conexão,
  desativa o autocommit e comita ao terminar — SEM consultar o
  `ThreadLocal KOF_DB_TX`: `prevAuto` já era `false` no bloco interno, mas
  o commit rodava igual, confirmando as linhas da transação externa.
- **Correção (09/09, JVM):** `nested = c.equals(KOF_DB_TX.get())` — bloco
  interno NESTA mesma conexão NÃO comita, não rollbacka, não restaura o
  autocommit nem remove o `ThreadLocal` (participa da transação externa:
  qualquer erro propaga p/ o bloco externo decidir — sem savepoints, que é
  decisão da mantenedora). Bloco em OUTRA conexão mantém transação própria
  (comportamento anterior). Política de savepoints/aninhamento explícito:
  decisão da mantenedora (gap registrado aqui, não implementado).
- **Gap honesto (R6):** o Native (`RuntimeDb4.kof_db_transaction`, asm
  x86_64) tem o MESMO furo (BEGIN/COMMIT em transação externa comita o
  escopo externo no sqlite/MySQL) — NÃO corrigido nesta lane (assembly
  Native, sem ThreadLocal equivalente); lane Native deve espelhar a
  semântica JVM (flag de transação ativa p/ o handle). O JS não implementa
  `kof_db_transaction` (gap JS pré-existente, JSN00x).
- **Prova:** repro EXATO da issue `caught {"n":0}` (antes `{"n":2}`, H2
  in-memory); teste `nestedTransactionDoesNotCommitOuterScope`; classe
  KofDbE2ETest 15/0 (2 skips Native pré-existentes).

### 78. Native: `transaction` aninhado comita o escopo externo (irmão asm do §77) — ✅ CORRIGIDO 10/09 (x86_64, espelhando o §77)

- **Sintoma:** MESMO programa do §77 em target Native (x86_64, sqlite): o
  bloco `transaction` interno comita (COMMIT no handle) enquanto o externo
  ainda está em transação; rollback do externo não desfaz as linhas
  confirmadas pelo interno. Paridade quebrada JVM vs Native (regra 5).
- **Causa:** `runtime/RuntimeDb4.kof_db_transaction` (asm) fazia
  BEGIN/COMMIT/ROLLBACK pelo handle SEM flag de transação ativa — não havia
  equivalente do `ThreadLocal KOF_DB_TX` JVM; cada bloco aninhado repetia
  BEGIN (no-op dentro de tx no sqlite, mas o COMMIT interno efetivava as
  linhas antes do rollback do externo).
- **✅ CORRIGIDO 10/09 (x86_64):** espelhou a semântica do §77 no asm —
  (1) novo slot BSS `.Ldb_tx_handle` (`RuntimeDb1`, 0 = sem tx) é o
  equivalente do `ThreadLocal KOF_DB_TX`; (2) o flag `nested =
  (tx_handle != 0 && tx_handle == default_handle)` é calculado na entrada e
  salvo no **slot 32 do frame de try** (frame crescido p/ 48B — 0/8/16/24
  seguem do layout do unwinder de `KofTryStart`); (3) BEGIN/COMMIT/ROLLBACK e
  o `KOF_DB_TX.set/remove` só rodam quando `!nested` — o bloco interno
  **participa** da transação externa e propaga o erro p/ o externo decidir.
  A flag lida do FRAME (não de reg) porque a lambda chamada pode clobberar
  callee-saved; o handler `.Ltx_rollback` lê-a de `32(%rsp)` (o unwinder
  deixa `%rsp` = base do frame de try) ANTES do `addq $48` que o desfaz.
  Sem savepoints (mesma decisão da mantenedora, §77). riscv/aarch64: o db
  reporta DB001 em compile-time no cross (asm puro, sem lib) — não há
  `kof_db_transaction` lá para espelhar (mesma restrição do §77 cross).
- **Prova:** `KofDbE2ETest.nativeNestedTransactionDoesNotCommitOuterScope`
  (sqlite x86_64; MESMO programa do §77 no binário — antes `caught {"n":2}`,
  agora `caught {"n":0}`, paridade JVM). Regressões: `nativeTransactionCommits`
  + `nativeTransactionRollsBackOnFailure` + `nativeSqliteRoundtrip` intactas
  (caso não-aninhado não regrediu); `KofDbE2ETest` 16/0.

### 92. Native (x86/riscv/aarch): `random.double()` retorna valores em [0,2) — constante 2^53 codificada como 2^52 — ✅ CORRIGIDO 10/09

- **Sintoma:** `KofRandomTest.randomShapeNative` flaky em main (`845284e5`):
  `assert(d < 1.0)` falha em ~50% das execuções do MESMO binário
  (31/60 no harness; com 6 asserts de double no programa, falha ~100%).
- **Menor repro:** `main() { var d = random.double(); assert(d < 1.0) }` →
  `kof run --target native`, ~1 em 2 rods → `assertion failed`.
- **Causa raiz:** `.Lrnd_two53` tem `.quad 0x4330000000000000`, que é
  **2^52** (4503599627370496.0), não 2^53 (9007199254740992.0 =
  `0x4340000000000000`). O asm divide `v ∈ [0,2^53)` (mantissa >> 11) por
  2^52 → resultado em [0,2). Bit 52 do exponent field: `0x433` vs `0x434`.
  Mesmo valor copiado no runtime x86 (`RuntimeRandom.java`) e no bloco
  riscv/aarch (`NativeRiscvAsmRtB27.java`) — bug único, dois sites +
  translator aarch64 (mesma const).
- **Correção (10/09):** `.quad 0x4340000000000000` nos 3 sites (x86 RuntimeRandom + riscv B27 + `.Lrnd_two53` aarch em `NativeAarch64Translator`). PROVA:
  harness isolado chamando `kof_random_double` 200k×: `ge1=0`, max < 1.0;
  binário real do teste: **0/200** falhas (antes 31/60);
  `KofRandomTest` 4/4 (1 skip cross-arch sem toolchain).
 - **Lição:** golden de valor é impossível p/ random (por design), mas
   CONSTANTE DE FP em asm merece teste de decode no harness — o comentário
   dizia "= 2^53" e o bit não era (confiança no texto, não na máquina).

### 93. JS: valor `Bool` de função stdlib é number 1/0 → `boolExpr == true` sempre `false` (paridade cross-target quebrada) — CORRIGIDO 10/09 (chokepoint `!!` na comparação cobre stdlib + instanceof + coleções)

- **Sintoma:** `var b = random.boolean()` (ou `var e = math.isEven(2)`) no
  target JS: `println(e)` mostra `true`, MAS `e == true` e `e == false` são
  AMBOS `false`, e `assert(b == true || b == false)` FALHA. No JVM e no
  Native (x86/riscv/aarch) o mesmo programa é `true`/`false` corretos
  (paridade regra 5 quebrada). Menor repro (`kof run --target js`):
  ```
  main() {
      var e = math.isEven(2)
      if (e == true) { println("E-TRUE") } else { println("E-NOTTRUE") }
      println(e)                 // -> "true"  (parece ok!)
      var b = random.boolean()
      assert(b == true || b == false)   // falha no JS, passa no JVM
  }
  ```
  Saída JS observada (harness KofJsRunner, 10/09): `E-NOTTRUE` + println
  `true`; somatório de `(b==true)+(b==false)` sobre 60 amostras = **0**
  (esperado 60).
- **Causa raiz (EVIDÊNCIA decisiva — `.mjs` gerado, 10/09):**
  ```js
  let e = kofMathIsEven(2);                 // função retorna NUMBER 1
  kofPrintln(String((e ? true : false)));   // PRINT injeta coerção → "true"
  if ((e === true)) { ... }                 // == baixa p/ === STRICTO → 1===true=false
  ```
  O backend JS **não é simétrico**: o emissor de `println` envolve o operando
  Bool num `(x ? true : false)` (por isso imprimir `true` engana), mas o
  emissor de `==` emite o operando CRU `===` (`JsCallEmitter.java:268
  case EQ -> JsBinary(left,"===",right)`; idem `JsControlFlowParser:230`) e os
  literais Kof `true/false` baixam p/ boolean JS. Como as funções stdlib
  Bool-returning entregam **number `1/0`** (`JsRuntimeUiStdlib:
  kofMathIsEven/IsOdd/IsPositive/IsNegative/IsZero` linhas 19-23,
  `kofRandomBoolean` ~467, predicados `strings.is*` 27-53, `validation.is*`
  ~331-403, `security.constantTime*` ~230-341), `boolExpr == true` é sempre
  falso. No JVM/Native o valor é primitivo `Z` real e a comparação casa.
- **Fix (implementado 10/09 — opção B no SÍNTESE, o chokepoint da comparação):**
  em vez de reescrever os ~48 sites `? 1 : 0` (opção A — INCOMPLETA: os guards
  `return 0` das famílias validation/security ficariam `0===false`, e NÃO cobria
  `instanceof` nem predicados de coleção), os emissores de `==`/`!=` do backend
  JS agora **normalizam ambos os operandos com `!!`** (ToBoolean) quando o lado
  é Bool — cobrindo uniformemente 1/0 de stdlib, `instanceof` e `contains`/
  `isEmpty`. Disparo por TIPO (`JsTypeMapper.isBoolOperand`) **ou** por LITERAL
  (`JsTypeMapper.isBoolLiteral` — `true`/`false`), porque `if (boolExpr == true)`
  colapsa `operandType` p/ `INT` no lowerer compartilhado (`comparisonOperandType`,
  CompilerComparisons.java) — o tipo não é sinal suficiente no caminho de
  condição. LT/LE/GT/GE ficam intocados (Kof proíbe ordenar Bool). Sites:
  `JsCallEmitter.binaryExpr` (caso valor, `KofBinary`) + `boolEq` helper novo;
  `JsControlFlowParser.comparisonExpr` (caminho de condição — agora recebe
  `operandType` do `KofConditionalJump`; 3 call-sites atualizados em
  JsControlFlowParser/JsExpressionStatementParser/JsExpressionParser).
  A opção A parcial (math.is*/random.boolean → boolean JS real) FICOU nos
  commits anteriores e é compatível com o chokepoint (defesa em profundidade).
- **Prova (real execução + paridade):** `node` roda o `.mjs` gerado e imprime
  as 10 saídas corretas (era o bug: `cond`/`instanceof==true`/`isEmpty==false`
  todos `false`); `CoreRegressionE2ETest.boolEqualityContentParityJvmJs`
  (`runBoth`) trava **JVM == JS byte-idênticos** no caminho de VALOR
  (`var x = a == true`) E de CONDIÇÃO (`if (a == true)`), para stdlib
  (strings/math), `instanceof` e coleção (contains/isEmpty/list/map). Suíte
  completa **1365/0** (compiler 1208 + script 25 + kof-c 5 + cli 127; 80 skip
  = riscv/aarch sem qemu). Matriz `stdmath`/`stdstrings`/`stdvalidation` e
  `KofRandomTest.randomShapeJs` verdes (sem regressão).
- **Status (10/09): CORRIGIDO** — chokepoint de comparação cobre TODAS as
  famílias (math/strings/validation/security + instanceof + coleções), não só
  as já convertidas. `randomShapeJs` mantém o assert `b==true||b==false`.
- **Por que passou despercebido (lição §92 de novo):** `randomShapeJs`
  (`KofRandomTest:74`) **omite** as linhas `var b = random.boolean();
  assert(b == true || b == false)` que `randomShapeNative`/`randomShapeCrossArch`
  têm — o shape JS nunca exercita Bool de função. E a matriz `stdmath` só faz
  `println(isEven(...))` (caminho impresso, coercente), nunca `== true`.
- **Prova de aceite esperada:** estender `randomShapeJs` com as 2 linhas de
  boolean (espelhando native) + caso `math.isEven(2) == true` na matriz; deve
  dar exit 0 nos 3 targets com saída idêntica.
- **Arquivo:** `js/JsRuntimeUiStdlib.java` (linhas 19,23,~467); verificar
  também `JsCallEmitter`/`JsValueEmitter` p/ outros retornos Bool numericados.
  Registrado 10/09 (sessão S7c; achado ao tentar FECHAR uma "carry JS bool"
  que na verdade NÃO era false alarm — a matriz `stdmath` só provava o print,
  não a comparação).














  (`println(if (c) true else 5)` → script `1` vs JVM `true`); lado JVM
  inalterado pela mudança (mesmo `Boolean.valueOf` antes e depois) —
  divergência do backend script, lane KOFSCRIPT se quiser.


- **Sintoma:** `class Base { ... }` + `class Derived extends Base { constructor(v) { super(v) ... } }`
  → JVM/JS ok (`42`); **interpretador → StackOverflowError** (recursão no ctor).
- **Causa raiz:** `KofInterpreter.dispatch` resolvia o owner de TODO `KofCall`
  pelo runtime-class do receiver (dispatch virtual — correto p/ método).
  `super(v)` de um construtor é baixado como `KofCall(ownerType=superclasse,
  "<init>", kind=CONSTRUCTOR)` (`ExpressionMethodCallLowerer:414`), MAS o
  dispatch ignorava o ownerType estático e usava a classe do objeto (`Derived`)
  → `findKofMethod` pegava `Derived.<init>` de novo → recursão.
  ⚠️ A 1ª correção da lane bug-fix (`8968c883`, bump `KofCallKind.SUPER`) NÃO
  bastava: provado por experimento — revertida a fusão, o PRÓPRIO teste
  `interpretExplicitSuperConstructor` falha com StackOverflow (o `super(v)` do
  ctor é kind CONSTRUCTOR, não SUPER; o bump só pega método `super.m()`).
- **Correção (09/09, `d92f413a` — fusão das 2 lanes):** (1) `<init>` resolve o
  owner pelo `kc.ownerType()` ESTÁTICO do IR (construtor não é virtual no JVM);
  (2) bump `SUPER→superclasse` preservado com guard `!<init>` (cobre método
  não-virtual); (3) `super()` p/ base externa não-Kof (Record/Object, IR do
  #53) = no-op. Prova: `ScriptTargetTest` 7/7 (interpretExplicitSuperConstructor
  + explicitSuperConstructorDoesNotRecurse + recordWithExplicitConstructorRunsOnInterpreter).

### 94. Interpretador: `==` de Double via `Double.compare` → `NaN == NaN` é `true` (JVM/Native/JS compilados: `false`, IEEE) — ABERTO (paridade regra 5, semântica `==` congelada = regra 6)

- **Sintoma:** `math.sqrt(-1.0) != math.sqrt(-1.0)` (ou qualquer `NaN != NaN`):
  JVM/Native-x86/KofJS → `true` (IEEE 754: NaN nunca é igual a si mesmo);
  interpretador (SCRIPT) → `false`. Menor repro — precisa de uma origem de
  NaN sem literal (literal `nan` não existe em Kof; `sqrt(-1.0)` é a que a
  stdlib S1b expôs):
  ```kof
  main() {
      println(math.sqrt(-1.0) != math.sqrt(-1.0))
  }
  ```
  `kof run` (script) → `false`; `--target jvm|native|js` → `true`.
- **Causa raiz (verificada 10/09 ao escrever o wedge S1b):**
  `KofInterpreterOps.binary` rota EQ/NE primitivos por
  `KofInterpreterValues.numEq`, que para Double usa
  `Double.compare(x, y) == 0` — e `Double.compare(NaN, NaN)` retorna **0**
  (ordenação total de `Comparable`, NÃO igualdade IEEE). O caminho compilado
  é `DCMPL`/`===`/`comisd`+push, todos IEEE (`NaN != NaN`). O mesmo `numEq`
  também inverte `+0.0 == -0.0` (JVM compilado: `true`; `Double.compare`:
  `false` — mesmo buraco, não reproduzido ainda).
- **Por que NÃO foi corrigido na hora (regra 6):** `==` é
  **congelado (0.2.6-beta)** — mudar a semântica do interpretador afeta todo
  código Kof existente que compare Doubles (ordenação vs igualdade em mapas,
  `contains` de lista sobre Object cai em outro ramo). É decisão de design →
  discussão + bump, nunca correção silenciosa. O correto provável é EQ/NE
  usarem `x == y` nativo (IEEE) e `compareRefs`/ordenação manterem
  `Double.compare` — mas quem decide é a mantenedora.
- **Mitigação atual (R6 honesto):** a matriz `stdsqrt` marca a célula script
  como **PARTIAL-bug 94** e o `Set.of("script")` exclui da asserção — o teste
  continua provando os 3 targets compilados; o caso NaN vive inteiro em
  `KofMathTest.sqrtJvm/sqrtNative/sqrtJs`.
- **Prova de aceite esperada:** `stdsqrt` sem exclusão (4 targets idênticos
  no `NaN != NaN`); + vetor `+0.0 == -0.0`.
- **Arquivos:** `KofInterpreterValues.numEq` (linha ~91),
  `KofInterpreterOps.binary` (EQ/NE). Descoberto 10/09 (sessão stdlib S1b).
- **Status: ABERTO** (semântica congelada — aguarda decisão de design).

## Comportamentos que PAREcem bugs mas são esperados (não corrigir)

| Cenário | Comportamento | Por quê |
|---------|---------------|---------|
| `l.get(5)` em lista de 3 | `IndexOutOfBoundsException` | bounds check (verificado 27/08) |
| `json.decode<Int>("abc")` | `NumberFormatException` | parse inválido |
| `json.decode<Point>("{\"x\":5}")` sem `y` | NPE/IllegalArgumentException | campo ausente — erro pouco claro (gap de mensagem, não bug de semântica) |
| `"abc".toInt()` | `NumberFormatException` | parse inválido |
| `10 / 0` (variáveis) | `ArithmeticException` runtime | ARITH001 só pega constantes |
| `"Olá 😀".length` | 6 (JVM UTF-16) | gap `STR001` documentado |
| `Map<String, Int>.get(ausente)` | NPE no unboxing | primitivos não representam null (limitação documentada) |
| JS `println(2.0)` | imprime `2` (JVM imprime `2.0`) | JS `String(2.0)` = `"2"` — formato padrão JS; gap de formatação de println cross-target (paridade) |

## Resolvidos nesta branch (referência)

- `42l`/`1.5f` minúsculos funcionam (maiúsculos também — ver Bug 6 abaixo).
- `Long as Int` funciona (fix 01/09) — o FP→Int é o Bug 5.
- Null-safety narrowing JVM (`s.length` pós-guard) — corrigido 02/09.
- Concat `"str" + double` — corrigido 02/09.
- Captura mutável JVM (mutação externa) — corrigido 02/09.
- **Bug 2** (compound `-=`/`/=`/`%=` resultado errado) — **corrigido 03/09**:
  a ordem dos operandos estava invertida (`a -= 2` virava `2 - a`; `+=`/`*=` só
  funcionavam por serem comutativos). Agora o LHS é empurrado antes do RHS.
  Prova: `CoreRegressionE2ETest.compoundAssignmentOrderAndStringInLoop`
  (JVM+JS+Native).
- **Bug 3** (crash do compilador com `s += "x"` em loop) — **corrigido 03/09**:
  mesma raiz do Bug 2 — o caminho de compound empurrava o RHS duas vezes
  (stack extra que quebrava o merge de frames no loop). Prova: mesmo teste
  acima.
- **Bug 10** (`!` NOT como valor de expressão sempre retorna `true`) —
  **corrigido 03/09**: constant folding usava `~i` (bitwise) em vez de `i == 0
  ? 1 : 0` (lógico) em `Optimizer.foldUnary`. Prova:
  `CoreRegressionE2ETest.logicalNotAsExpressionValue` (JVM+JS+Native).
- **Bug 5** (cast FP→Int/Long gera bytecode inválido) — **corrigido 03/09**:
  faltavam os ops de conversão `D2I`/`F2I`/`D2L`/`F2L` no IR e nos backends
  (JVM/Native/JS/riscv). Cast agora trunca para zero (`3.9 as Int` → `3`).
  Prova: `CoreRegressionE2ETest.fpToIntAndDoubleToFloatConversions`.
- **Bug 24** (Double→Float narrowing gera bytecode inválido) — **corrigido
  03/09**: `Float f = 3.4` e `d as Float` não emitiam `D2F` (o caso especial
  só cobria argumentos de função). `emitWideningIfNeeded` agora cobre
  Double→Float e o caso redundante em `emitArgumentsWithFormalTypes` foi
  removido. Prova: mesmo teste do Bug 5.
- **Bug 25** (literal Long fora do range crasha o compilador com
  `NumberFormatException` crua) — **corrigido 03/09**: `Parser.parsePrimary`
  valida a faixa do literal e emite `PARSE084: numeric literal out of range`.
  Prova: `CompilerDriverTest.outOfRangeLongLiteralGivesCleanDiagnostic`.
- **Bug 6** (sufixo numérico MAIÚSCULO `42L`/`1.5F` gera bytecode inválido) —
  **corrigido 03/09**: o `Lexer.readNumber` só consumia sufixos minúsculos;
  `42L` virava `INT_LITERAL(42) IDENTIFIER(L)`. Agora aceita
  `f/F`/`d/D`/`l/L` como alias. Prova:
  `CoreRegressionE2ETest.uppercaseNumericSuffixes` (JVM+JS+Native).
- **Bug 14** (`Map.size`/`Set.size` propriedade → `NoSuchFieldError` em
  runtime) — **corrigido 03/09**: o field-access de `m.size` caía no caminho
  genérico (getfield em `HashMap`) e o tipo inferia UNKNOWN (boxing errado em
  `println`). Agora `m.size`/`s.size` despacham para `kof_map_size`/
  `kof_set_size` (como `List.size`). Prova:
  `CoreRegressionE2ETest.mapAndSetSizeProperty` (JVM+JS+Native).
- **Bug 1** (`throw <não-String>` gera bytecode inválido no JVM) —
  **corrigido 03/09**: `SemanticAnalyzer` rejeita `throw <não-String>` com
  `SEM026` ("exceções são Strings em Kof"). De quebra, corpos de try/catch/
  finally agora passam pela análise semântica (antes eram ignorados — `throw
  42` dentro de try escapava). Prova:
  `CompilerDriverTest.throwNonStringGivesCleanDiagnostic`.
- **Bug 7** (`listOf<String?>()` não parseia — PARSE041) — **corrigido 03/09**:
  `Parser.looksLikeGenericCall` rejeitava o token `?` no lookahead de call
  genérico → `<` virava comparação. `QUESTION` agora é aceito. Prova:
  `CoreRegressionE2ETest.nullableGenericArgumentInCall`.
- **Bug 22** (Native: construtor de classe de outro pacote → `undefined
  reference`) — **corrigido 03/09**: o mangle do call site usava o nome
  simples (`C_init_0`) mas a definição usa o internal name (`com_acme_C_init_0`).
  `NativeBackend.resolveCalleeName` agora usa `classTypeManglePrefix`
  (package + nome). Prova:
  `NativeE2ETest.nativeConstructorFromImportedPackage`.
- **Bug 4** (`switch` com String gera bytecode inválido no JVM) —
  **corrigido 03/09**: o lowering do switch não-enum usava `SUB`
  (`switchValue - caseValue == 0`) para testar igualdade → `String - String`
  invalidava o bytecode. String agora usa `kof_string_equals` (por conteúdo),
  como enums. JS backend atualizado para o novo padrão (switch JS já compara
  strings por valor). Prova: `CoreRegressionE2ETest.stringSwitchOnJvm`.
- **Bug 13** (cast `x as T` usado em aritmética crasha o compilador) —
  **corrigido 03/09**: o flattening de cadeia esquerda-associativa tratava
  `(x as Int) + 1` como cadeia `[+, as]` — o `as` caía no `default -> ADD`.
  `as`/`instanceof` agora param o flattening. Prova:
  `CoreRegressionE2ETest.castInArithmetic` (JVM+JS+Native).
- **Bug 17** (array `.get()`/`.set()` — não existem, mas compilavam e geravam
  saída quebrada) — **corrigido 03/09**: o SemanticAnalyzer rejeita method
  call sobre tipo array com `SEM028` ("use o operador arr[i]"). Prova:
  `CompilerDriverTest.arrayMethodCallGivesCleanDiagnostic`.
- **Bug 18** (kof-ui: ID de widget reutilizado após `remove()` → colisão) —
  **corrigido 03/09**: os 5 factories (Label/Link/Image/Icon/Font) usavam
  `Object.keys(__kofNodes).length + 1`; após `remove()` o length encolhia e o
  próximo widget reusava o ID de um nó vivo. Agora contador monotônico
  `kofNodeSeq`. Prova: `KofJsE2ETest.uiWidgetIdsUseMonotonicCounter`.
- **Bug 12** (assignment encadeado `var c = a = b` gerava bytecode inválido) —
  **corrigido 03/09**: assignment usado como VALOR é rejeitado com `SEM027`
  ("atribuição é um statement, não uma expressão"). Statements (`a = b`,
  `i = i + 1` no for) seguem passando com o check de assignability intacto
  (SEM012). Prova: `CompilerDriverTest.chainedAssignmentRejectedAsExpression`.
- **Bug 16** (`List.toArray()` quebrava JVM/Native) — **corrigido 03/09**:
  `toArray` não é suportado/documentado e caía no caminho genérico → bytecode
  inválido. Agora `SEM029` limpo ("use um loop com new T[n]"). Interop Java
  (`stream()`) segue funcionando. Prova:
  `CompilerDriverTest.toArrayOnCollectionGivesCleanDiagnostic`. Relacionado:
  `sublist()`/`subSet()` (retorno de coleção) também geravam bytecode inválido —
  **corrigido 04/09** com `SEM034` limpo (prova:
  `CompilerDriverTest.sublistOnCollectionGivesCleanDiagnostic`).
- **Bug 11** (`==` em records usa igualdade de REFERÊNCIA) — **corrigido
  03/09 (JVM+JS+Native)**: `==`/`!=`/`equals` em records despacham para o
  `equals` gerado (comparação de conteúdo). JVM já gerava equals; JS gera
  `equals()` por componente (retorna Kof bool 0/1); Native agora gera e
  dispatcha `equals` via vtable. O `println(record)` também funciona em
  Native (usa o toString gerado). Prova:
  `CoreRegressionE2ETest.recordEqualityByContent` (JVM+JS+Native).
- **Bug 23** (ExternalClasspath: superclasse fora dos entries perdia
  referência SILENCIOSAMENTE) — **corrigido 03/09**: `resolveMethod`/
  `resolveFieldType` emitem warning quando a cadeia de superclasses encontra
  uma classe ausente do classpath ("may not resolve"). Prova:
  `AndroidInteropE2ETest.missingSuperclassOnClasspathWarns`.
- **Bug 20** (lambda em coleção invocado: `ops.get(0)(4)`/`f(4)` de elemento de
  lista) — **corrigido 03/09 (3 targets)**: três causas encadeadas —
  (1) a inferência de métodos de List/Map/Set no SemanticAnalyzer devolvia
  Unknown (a lista de lambdas perdia o tipo do elemento); (2) o tipo cacheado
  da análise semântica tinha a FunctionType SEM className (a síntese da lambda
  é pós-análise) → agora `containsLambdaFunctionType` força re-inferência;
  (3) o JVM `kof_list_get` não fazia CHECKCAST para a classe sintética da
  lambda (verifier: Object onde Lambda0). Prova:
  `CoreRegressionE2ETest.lambdaStoredInCollectionAndInvoked`.
- **Bug 19** (lambda retornando lambda) — **corrigido 04/09**: `collectCaptures`
  desce em lambdas aninhados (o externo captura e repassa variáveis livres do
  interno) — triple-nested `make(5)(3)(10)` funciona nos 3 targets. Prova:
  `LambdaE2ETest.tripleNested*` (JVM+Native).
- **Bug 8** (tipo de função `(Int) -> Int` não parseava como tipo) —
  **corrigido 03/09 (parse) + 04/09 (invocação)**: `Parser.parseTypeRef` agora
  aceita `(params) -> ret`; `Type.of` converte para `FunctionType`;
  `looksLikeLambdaParams` reconhece `(s: (Int) -> Int) -> ...`.
  `listOf<(Int) -> Int>()` funciona. **Invocar valor de tipo de função
  DECLARADO** (`s(1)` com `s: (Int) -> Int`, inclusive params de função) —
  **corrigido 04/09**: toda lambda implementa uma interface sintética por
  assinatura (`kof/FunctionN_<types>`) e o call site despacha via
  INVOKEINTERFACE (antes SEM032). Prova:
  `CompilerDriverTest.functionTypeSyntax` +
  `LambdaE2ETest.declaredFunctionType*` (var e param, JVM+Native).
- **Bug 9** (captura mutável no Native → lixo) — **corrigido 03/09**: o
  prologue nativo iterava os locals na ORDEM DE INSERÇÃO [this, capture, param]
  e consumia rsi/rdx para a CAPTURA (que na verdade é carregada dos campos do
  objeto via ops). O param real ficava com rdx (lixo). Agora o prologue salva
  registros apenas nos slots de PARAMS (1..soma das larguras), ordenando os
  locals por índice; capturas (slots acima) são preenchidas pelas ops. Prova:
  `NativeE2ETest.nativeLambdaMutableCapture`.
- **Bug 15** (primitivo não atribuível a Object — sem auto-boxing) —
  **corrigido 03/09**: `isAssignable` aceita primitivo→`java.lang.Object` e o
  emit boxa (`emitErasureBox` no JVM; JS/Native já são untyped) no var-decl e
  na atribuição. De quebra, declaração SEM inicializador (`Int x`, `Object o`)
  agora recebe default (0/null) — antes crashava o frame. `Int → String`
  continua rejeitado (SEM021). Prova:
  `CompilerDriverTest.primitiveAssignableToObject`.

- **Bug 34** (método inexistente em tipo BUILTIN List/Map/Set/String → no-op silencioso, R6) — **corrigido 06/09 (lane bug-fix)**: `MemberCallTyper` e `CollectionCallLowerer` agora diagnosticam `SEM025` para método fora da allow-list (`List: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce`, `Map: put/get/...`, `Set: add/...`, `String: via registro`). O lowerer retorna `localIdx` sem cair no emit genérico com owner `""` → `ClassFormatError`. Prova: `TestRepro` 3/3 (`s.first()`/`l.first()`/`m.first()` → `SEM025`), suíte `CompilerDriverTest` 203/0.
- **Bug 29** (`var h = spawn { lambda }` handle) — **melhoria 06/09 (lane bug-fix)**: o `Handle<T>` da task com lambda void agora carrega `T=void` (não `FunctionType`). `ExpressionStaticCallLowerer` usa `inferLambdaBodyType` para lambdas (corpo sem `return` → `void`), e `ExpressionTyper.inferLambdaBodyType` preserva `FunctionType` só para lambdas que retornam lambda (bug 19). Antes `spawn { println(n*2) }` gerava `Handle<FunctionType>` → `invoke():Object` com areturn em pilha vazia (VerifyError/segfault). Prova: `TestRepro` spawn handle compila, `SpawnE2ETest` 8/8.
- **Bug 31** (`process.<inexistente>()` → segfault) — **corrigido 06/09 (lane bug-fix)**: `MemberCallTyper` (SEM025) e `ExpressionProcessCallLowerer` (SEM025) agora rejeitam método fora de `run/spawn/exit` com lista válida, nunca caindo no load de campo genérico. Prova: `TestRepro` `process.currentDir()` → `SEM025`, `process.spawn` válido continua ok.
- **SG-007** (wildcard `List<? extends Int>` → `NoClassDefFoundError: ?extendsInt`) — **corrigido 06/09 (lane bug-fix)**: `TypeParser.parseTypeRef` rejeita `?` wildcard dentro de `<>` com `PARSE086` ("Wildcard types '? extends/super' are not supported; use concrete type or nullable 'T?'"). `List<String?>` (nullable) continua válido. Prova: `TestRepro2` wildcard → `PARSE086`, `TestWild` `String?` → ok.

---

## Aberto (gap Canvas — 06/09)

### 79. Native: `String.toInt/toLong` divergem do contrato JVM em entrada inválida — R6 silencioso nos 3 nativos — ✅ CORRIGIDO 10/09 (as 3 faces; varredura da lane STDLIB — header fechado na auditoria cross)

- **Contrato previsto** (congelado, tabela "PAREcem bugs mas são esperados" deste
  arquivo + teste `KofJsE2ETest.execStringToNumberConversion`): `"abc".toInt()` →
  `NumberFormatException` (exceção=String em Kof); `"12a34".toInt()` → throw
  (JVM `Integer.parseInt` valida dígito a dígito); `" -42 "` → `-42` (JVM aceita
  espaços); `"999999999999".toInt()` → throw (overflow).
- **Comportamento medido 10/09 (qemu/proc real, harness GenU):**

| entrada | JVM/JS (previsto) | x86_64 | riscv64 | aarch64 |
|---|---|---|---|---|
| `"abc".toInt()` | throw | **5451** | **0** | **0** |
| `"12a34".toInt()` | throw | **16934** | **1234** | **1234** |
| `" -42 ".toInt()` | `-42` | **-162596** (espaço→16*10+(32-48)=-270; só o `-` do meio é parseado como sinal) | **42** (pula não-dígitos inclusive o `-` fora do índice 0 — sinal perdido) | **42** (tradução idêntica) |
| `"999999999999".toInt()` | throw | **wraparound** (-727379969) | **999999999999** (retorna LONG num site Int — lixo de 64 bits) | idem riscv |

- **Três implementações divergentes entre si**, todas violando o contrato:
  (a) **x86** (`RuntimeStringParse.emitStringToInt/Long`): loop `acc*10+(c-48)`
  sem validação de dígito, sem trim, sem throw — "abc"→5451;
  (b) **riscv** (`NativeRiscvAsmRtB0` `.Lsti_*`): tem **trim** e **pula
  não-dígitos** (`bgt 9,.Lsti_skip`) — "abc"→0, "12a34"→1234 (silencioso, pior:
  parece que funciona); aarch64 é tradução linha-a-linha (mesmo comportamento);
  (c) overflow: ninguém checa 32-bit; o riscv propaga 64 bits para um site Int.
- **Por que ninguém viu:** os testes cross-arch (`NativeRiscv64E2ETest:443`,
  `NativeAarch64E2ETest:171`) só exercitam **entradas válidas** ("42", "-7", "0"
  — o fix `696c6c9` do deref). A suíte nunca passou entrada inválida nos
  nativos. O JS ganhou `kofParseChecked` (regex + throw) no #51; o nativo nunca
  foi alinhado.
- **Correção (x86) FEITA 10/09:** `RuntimeStringParse.emitStringToInt/Long`
  reescritos no contrato exato do JDK — trim (byte<=32 nas duas pontas), sinal
  `+/-`, dígito-a-dígito, acumulação NEGATIVA (`acc<=0`, `limit=MIN` p/
  negativos / `-MAX` p/ positivos, overflow detectado por-dígito antes do
  `*10` e antes da subtração), e falha → `kof_string_from_literal` +
  `kof_throw_string` (exceção String capturável; sem try outer = panic com
  código — nunca número silencioso). Prova medida: os 14 vetores da matriz
  acima (T1..T7 + válidas, incl. `+7`, `-2147483648`, `-9223372036854775808`)
  saem BYTE-IDÊNTICOS ao JVM no x86; suíte kof-compiler verde (aarch64 28/28
  roda o mesmo asm-x86? não — aarch64 traduz riscv; ver pendência).
  LIÇÃO do port: o imediato `$-9223372036854775808` não cabe em cmp
  sign-extended do gas — o bloco final de comparação com MIN é redundante
  quando o guard por-dígito usa `limit=MIN` (removido).
- **Correção (riscv/aarch) FEITA 10/09 (U3):** `NativeRiscvAsmRtB0` perdeu o
  `kof_string_to_int` silencioso; os dois parseadores agora vivem numa fatia
  NOVA (`NativeRiscvAsmRtB30`, montada via template String.format) com o MESMO
  algoritmo do x86 (trim, +/-, dígito-a-dígito, acumulação negativa,
  kof_string_from_literal+kof_throw_string). aarch64 herda por tradução.
  `toLong` riscv **criado** (antes: link quebrava — undefined reference).
  PROVA: 16 vetores golden idênticos JVM==x86==riscv-qemu==aarch-qemu
  (KofStringParseTest + riscv64/aarch64StringToInt estendidos). B30 abre a
  seção com `.section .text` (armadilha conhecida) e fecha `.section .data`
  (msg) — verificado com `riscv64-linux-gnu-as` na fatia isolada.
- **Divergência irmã descoberta e travada:** `println(Long.MIN_VALUE)` no
  riscv/aarch imprime lixo → registrado como **bug 80** (printer, não parse —
  o parse retorna MIN exato, provado por `(w - v) == 1`). E `toLong` no JS é
  `Number` (double 53-bit): overflow ±2^53 não lança → **bug 81** (design do
  modelo numérico, congelado — golden do teste JS limita-se a ±2^53).
  Menor repro: `main() { try { println("abc".toInt()) } catch (String e) { println("THREW") } }` —
  JVM/JS/x86 imprimem `THREW`; riscv/aarch imprimem `0`.

### 80. riscv64/aarch64: `println(Long.MIN_VALUE)` imprime lixo (Int.MIN ok) — ✅ CORRIGIDO 10/09 (varredura STDLIB; NATIVE002 órfão reatribuído)

- **Sintoma:** `var m = -(9223372036854775807 + 1); println(m)`: JVM/x86 →
  `-9223372036854775808`; riscv64 e aarch64 → `-'..--).0-*(+,))+(0(` (bytes
  fora de ASCII). `println(-2147483648)` (Int.MIN) e `println` de qualquer
  outro long (inclusive MIN+1, MAX) estão corretos nos 2.
- **Causa raiz:** `NativeRiscvAsmRt0` define
  `kof_long_to_string: j kof_int_to_string` (alias). O `kof_int_to_string` é
  RV64 e faz `neg s0, s0` p/ magnitude. Para `Int.MIN` (= -2^31, sign-extended
  a 64 bits) o `neg` dá +2^31 — ok. Para `Long.MIN` (= -2^63) o `neg` é
  **auto-referente** (magnitude continua negativa) → o laço de contagem e o
  `rem`/`div` **signed** produzem restos negativos; `addi t3,48` cai abaixo de
  '0' → bytes de lixo. (É a técnica de Int.MIN funcionar "por acaso" só em 64
  bits.)
- **Por que ninguém viu:** o único exercício de Long nos cross-arch é
  `42/0/-7` (válidos, magnitude positiva). Long.MIN não é testado em riscv/aarch.
- **Prova/repro:** harness GenU, arquivo `prn.kf` (`main(){ var big
  = 9223372036854775807; var m = -(big+1); println(m) }`) → riscv-qemu e
  aarch-qemu imprimem lixo; JVM e x86 nativo imprimem o valor. Isolado do bug
  79: `"-9223372036854775808".toLong()` **retorna** MIN exato nos 3 (provado
  via `("-9223372036854775807".toLong() - v) == 1` → true nos 3); só a
  IMPRESSÃO falha.
- **Correção FEITA 10/09 (varredura da STDLIB; NATIVE002 reatribuído — linha
  órfã desde 05/09, regra do DOING):** magnitude mantida na forma **NEGATIVA**
  (`s5 = -|v|`, técnica de acumulação-negativa do JDK): `rem(v≤0, 10)∈[-9,0]`
  e `digit = -rem`; todos os 64 bits cabem em [-2^63, 0] sem overflow — o `neg`
  auto-referente nunca acontece. Opções usadas (rem/div/neg/bgtz/bltz) já
  suportadas no tradutor aarch (divu/remu NÃO existem lá — primeira tentativa
  com eles falhou exatamente por isso; a versão negativa é a que passa).
  Prova: riscv-qemu e aarch-qemu imprimem `-9223372036854775808`; diff JVM
  ==x86==riscv==aarch no vetor 0/±42/±10/±MAX/Int.MIN/Long.MIN/10^6. Trava:
  `v` impresso adicionado aos golden cross-arch dos testes do 79
  (riscv64StringToInt/aarch64StringToInt) + `KofStringParseTest` (3 alvos).

### 82. Native: `String.toDouble/toFloat` fora do contrato JVM — parser x86 silencioso-e-errado; riscv/aarch nem definem os símbolos — ✅ CORRIGIDO 10/09 (as duas faces; irmão FP do bug 79, varredura STDLIB)

- **Contrato previsto** (congelado, tabela "PAREcem bugs" deste arquivo —
  `Double/Float.parseFloat(s.trim())`, exceção=String em inválido — e o teste
  `KofJsE2ETest.execStringToNumberConversion` cobre `"abc".toDouble()`→throw).
- **Medido 10/09 (harness GenU):**

| entrada | JVM/JS (previsto) | x86 atual | riscv64/aarch64 |
|---|---|---|---|
| `"2.5".toDouble()` | 2.5 ✅ | ✅ | **link quebra** (undefined ref `kof_string_to_double`) |
| `"1e3".toDouble()` | 1000.0 | **lixo** (xmm0 nunca inicializado no caminho int→exp) | link quebra |
| `" 3.0 "` | 3.0 | **-157** (espaço vira dígito -16) | link quebra |
| `"abc"` | throw | **5451** (silencioso!) | link quebra |
| `"1.2.3"` | throw | **1.2** (para no 2º '.', aceita) | link quebra |
| `"NaN".toDouble() == "NaN".toDouble()` | false (IEEE NaN≠NaN) | **true** (virou número 3493…) | link quebra |
| `"7".toDouble()` | 7.0 | ✅ (int path com '.' ausente) | link quebra |

- **Causa raiz x86** (`RuntimeStringParse.emitStringToDouble/Float`): parser
  ad-hoc sem trim, sem validação (qualquer byte não-dígito vira `c-48`), sem
  throw, sem literais NaN/Infinity, e o ramo expoente-por-inteiro pula o
  `vcvtsi2sd` (só o caminho fracionário cria xmm0) → garbage multiplicado.
- **Causa raiz cross:** `kof_string_to_double/float` só existem no asm x86
  (`NativeRuntime`); a cadeia `NativeRiscvAsm*` nunca definiu (FLT001 é o gap
  de aritmética FP cross — mas aqui quebra até o LINK de `String.toDouble()`).
- **Correção face x86 FEITA 10/09:** parser reescrito no contrato: trim,
  `+/-` inicial, dígitos-a-dígitos, um único `.` (com dígitos antes OU
  depois), expoente `e/E` só com dígitos, literais `NaN/Infinity/-Infinity`
  (case-sensitive, idem JDK; NaN é o qNaN estático — `NaN==NaN` dá false
  como no JVM), falha → `kof_throw_string` (nunca número). Mantissa em
  int64 + UMA divisão por 10^nfrac (rounding único). `toDouble`/`toFloat`
  partilham a máquina (`cvtsd2ss` no fim) — split de arquivo novo
  `runtime/RuntimeStringParseFp.java` (gate ≤500; `RuntimeStringParse`
  ficou só com Int/Long). Prova: oracle booleano de 24 vetores medidos no
  JVM == x86 == JS byte-a-byte (`KofStringParseTest` 6/6: toInt/toLong +
  toDouble/toFloat em JVM/x86/JS).
- **LIMITE travado (documentado no parser e nos testes):** mantissa com
  >19 dígitos LANÇA no x86 (JVM/JS parseiam com arredondamento — a máquina
  usa int64 + UMA divisão por 10^nfrac, o que dá round-trip correto p/
  ≤19 dígitos: `"0.3"==0.3`, `"0.1"+"0.2"==0.30000000000000004` batem);
  hex-float (`0x1p3`) lança (JVM parseia). Paridade bit-exata p/ casos fora
  disso exige o algoritmo big-int shortest-round-trip do JDK → família
  FLT001. exp |e|>320 satura a 0/Infinity (JVM idem).
- **Correção face cross FEITA 10/09:** `NativeRiscvAsmRtB31` novo (parser
  riscv espelho do x86 — trim/sinal/digito/um-ponto/expoente/NaN-Infinity/
  throw; mantissa int64 + 1 divisão por 10^nfrac; retorno cross = Double em
  bits-raw `a0`, Float em low32). Duas admissões corrigidas no tradutor aarch:
  `fcvt.d.l` **faltava** (int64→double; o L2D do backend riscv a usa — nunca
  exercitado por causa do gate FLT001) e `fcvt.s.d`/`fcvt.d.s` estavam com
  **dst/src invertidos** (todo F2D/D2F do aarch corromperia) + operadores
  `fdiv.`/`fmul.` com ponto extra. Prova: oracle de 25 vetores **JVM==x86==
  riscv==aarch==JS** byte-a-byte (`KofStringParseTest` 8/8, os 2 cross via
  qemu). Print de Double no cross segue FLT001 (double→string exige
  snprintf); `Int/Long.toDouble()` boxing segue `toDouble` undefined (gap
  separado, família FLT001). Menor repro pré-fix: riscv `"2.5".toDouble()` →
  COMP001 `undefined reference to kof_string_to_double`.

### 81. KofJS: `Long` é `Number` (double 53-bit) — `"...".toLong()` acima de ±2^53 perde precisão e NÃO lança overflow — ABERTO (paridade R5 cross-target)

- **Sintoma:** `println("9007199254740993".toLong())` no JS → `9007199254740992`
  (arredondado); `println("12345678901234567890".toLong())` → notação
  exponencial; e o overflow além de `Number.MAX_SAFE_INTEGER` **não** lança
  (o JVM/Native/Script lançam exceção por contrato `Long.parseLong`).
  Medido 10/09 ao escrever o `KofStringParseTest` (JS `toLong`).
- **Causa raiz:** `JsRuntimeUiStdlib:314` — `Number(s)` (IEEE-754 double); o
  comentário no fonte já assume "sem BigInt". É decisão do **modelo numérico JS**
  (congelado, regra 6), não do parser do bug 79.
- **Não-corrigível silenciosamente:**BigInt no GraalJS rodaria, mas trocar o
  tipo de `Long` em JS é mudança de contrato (narrowing/`==`/println) → nota de
  design, não edição. Por ora a matriz `stdparse` (linha bug 79) cobre `toInt`
  nos 5 e `toLong` com golden JVM/Native; o teste JS limita-se a ±2^53
  (documentado no próprio `KofStringParseTest`).

### 87. `T?` de primitivo NPE no `== null`; literal `null` fabricável; `Map.get()` primitivo sem null — ✅ CORRIGIDO 10/09 (SG-008 + SEM048, decisão do maintainer)

- **Sintoma (3 faces do mesmo gap de null safety):**
  1. `Int? a = mapOf("k",1).get("zz"); a == null` → **NPE** no compilado (unbox
     de `Integer` null) — o get de primitivo nem devolvia `V?` (só referência).
  2. `Int? x = null` / `x = null` compilavam — o programador fabricava null,
     source de NPEs que a nullability deveria prevenir.
  3. `println(m.get("zz"))` (bug 39, revertido 07/09 por retrocompat) — o
     corrigir só o println quebrava `m.get(k) == 1` (VerifyError `if_acmpeq`
     sobre ref vs int).
- **Decisão do maintainer (09/09, "o próprio nome já diz")**: `null` NUNCA é
  fabricável (ban total do literal, nem a `T?`); `null` só chega de API
  (`mapOf().get(missing)`); `T? == null` é comparação de referência — sem
  unbox, sem NPE. Regra 6 SUSPENSA para breaking (testes migrados junto).
- **Correção 10/09** (detalhada em `specification-gaps.md` §SG-008): SEM048
  ban do literal (`StatementAnalyzer`); `get()` → `V?` sempre (4 typers,
  fechando a janela do bug 39 com o `==` corrigido); pin `K,V` no primeiro
  `put()` via `SymbolTable.updateLocalType`; `==` com lado nullable →
  referência com primitivo boxado (`CompilerComparisons` +
  `ExpressionBinaryLowerer`, box na ordem certa); interpretador
  `eqAllowsNull` + unbox com guard. Retrocompat preservada: `m.get(k) == 1`
  compila (o `1` é boxado, `if_acmpeq` — o caso que REVERTIA o fix do bug 39).
- **Provas:** `CompilerDriverTest.nullInVarDeclFails`/`nullInAssignmentFails`/
  `nullFromApiStaysGreen` (241/241); paridade 4 targets `BackendParityTest`
  (16) + `ConformanceMatrixTest` (11) + `KofScriptTest`; repro do bug 39
  (`println(m.get("zz"))` imprime `null`) e `m.get("a") == 1` → `true` no
  mesmo programa. Suíte compiler 1255/0-falhas-de-código (14 errors = ambiente:
  node/javac/javap ausentes).
- **Lição de regressão (registrada):** a 1ª tentativa adicionou um path
  `isComparisonShortcut` no `case AssertStmt` (StatementLowerer) que quebrou
  `assert(cancel(r) == 0)` no JS (`Bool == Int` → `comparisonOperandType`
  retorna BOOL → código errado). Revertido ao path genérico — o `assert`
  NÃO usa shortcut; testes verdes depois.

### 88. riscv64/aarch64: `Map.get()` imprime `0`/segv — `String.valueOf(T?)` no cross não emite (SG-008/87 parcial) — ✅ CORRIGIDO 10/09 (regra zero-regressão; achado na varredura STDLIB ao bisectar o gate da suíte)

- **Sintoma:** após `9436da12` (SG-008, `Map.get()` devolve `V?`), o
  `riscv64MapSet`/`aarch64MapSet` (qemu) passaram a dar **SIGSEGV** e o
  `println(m.get(k))` imprimia `0` — green→red. x86 e JVM estavam corretos
  (o MESMO commit corrigiu o `valueOf` x86 e o `println` instance).
- **Causa raiz:** `NativeRiscvCrossOps` — o branch `String.valueOf` (STATIC)
  despachava sobre `argType` sem unwrappar `Nullable`. Com `V?`, o
  `valueOf(m.get(k))` recebia `Nullable(Int)`, não casava
  `instanceof PrimitiveType` e **não emitia nada** (sem `pop`, sem conversão):
  o raw `Int` ficava na pilha e o `println` seguinte tratava-o como
  ponteiro de string → segv (ou `0` quando o Int era 0). O próprio commit
  do bug 87 já tinha aplicado o `dispatchType` (unwrap) ao `println`
  instance (linha ~116) e ao `NativeX86Calls` (x86), mas **esqueceu esse
  branch cross** — a mesma classe de defeito, alvo diferente.
- **Correção:** mesmo unwrap já presente no `println` cross e no x86:
  `Type vArgType = argType instanceof Type.NullableType nt ? nt.inner() : argType`.
  (A decisão `Map.get()`→`V?` é do maintainer/SG-008 — este fix é alinhar o
  código cross ao comportamento já decidido, não é escolha de design:
  "bug = alinhar ao previsto, nunca o contrário".)
- **Prova:** `riscv64MapSet`/`aarch64MapSet` 28/28 verdes de novo;
  `println(m.get(k))` cross == x86 == JVM nos vetores 0/1/2; `m.get("a")==1`,
  `m.get("zz")==null`, `println(m.get("zz"))` cross == JVM (exit 0).
  Nota: a saída `0` vs `null` para `mapOf()` **sem tipo** é semântica do
  JVM (KofMap.put sem tipo-erasure; `get` ausente → 0), idêntica entre os
  alvos — **não** é divergência cross; registrar como gap de semântica
  `mapOf()`-vazio (se a mantenedora quiser `null` ali, é decisão SG-00x).

### 89. Native (x86 + cross): conversão numérica de primitivo `n.toDouble()`/`n.toInt()`/`n.toFloat()`/`n.toLong()` quebra o LINK — o idiom documentado é `as` — ABERTO (decisão de design, regra 6; achado 10/09 varredura STDLIB)

- **Sintoma:** `main() { var n = 5; println(n.toDouble() == 5.0) }` falha no
  link nos 3 nativos — x86: `undefined reference to toDouble`; riscv64/
  aarch64: idem. **JVM e JS executam certo** (interpretador implementa o
  método em primitivo — probe `box2.kf`: JVM success=true). A API **existe**
  e funciona em 2 dos 3 targets; falta só o emit nativo.
- **Causa raiz:** o backend nativo (`NativeX86Calls.emitCall` /
  `NativeRiscvCrossOps`) não tem intrínseco p/ conversão numérica de
  primitivo — o call genérico cai em `call <nome>` sem que NENHUMA runtime
  defina `toDouble`/`toInt`/`toFloat`/`toLong` (só `String.toDouble` →
  `kof_string_to_double`, símbolo diferente). O idiom que funciona em TODOS
  os targets (incluindo cross, exit 0 medido 10/09) é o **cast `as`**:
  `n as Double`, `d as Int` (AGENTS.md "Cast: x as Char / big as Int";
  `learn/04`: `Int i = d as Int`) — o `as` lower p/ o intrínseco numérico
  do backend (I2D/D2I/...) que existe nos 3 nativos.
- **Por que NÃO é "só implementar" (regra 6 — decisão de design):** p/
  adicionar o emit nativo de `.toDouble()`/`.toInt()` em primitivo falta a
  **semântica congelada** da conversão — `3.7.toInt()` deve truncar?
  arredondar? overflow → throw? — e **nenhum teste e nenhum doc do corpus**
  pinam o valor em primitivo (só o da `String`, outro contrato: §79/§82).
  Implementar = inventar API + semântica de arredondamento; o caminho
  idiomático já existe (`as`). Opções p/ a mantenedora: (a) `.toDouble()`
  em primitivo vira alias do `as` (definir trunc/round + overflow → throw?)
  e entra no emit dos 3 nativos; (b) o typer **rejeita** `.toDouble()`/
  `.toInt()`/`.toFloat()`/`.toLong()` em receiver primitivo com diagnóstico
  apontando p/ `as` (superfície = corpus); (c) deixar como está (JVM/JS
  funcionam, nativo quebra no link — divergência R5 honesta, sem gate).
- **Evidência:** `box2.kf` (`5.toInt()`) — JVM success=true; NATIVE/
  NATIVE_RISCV64 `undefined reference to toInt`; **pre-existing** (reproduzido
  em worktree de `9436da12`, anterior ao trabalho da varredura STDLIB — que
  não tocou X86Calls/typer numérico). `cast.kf` (`as`) — JVM + 3 nativos
  exit 0 com valores corretos.
- **Menor repro:** `main() { var n = 5; println(n.toDouble() == 5.0) }` →
  x86/riscv/aarch `undefined reference to toDouble`; JVM/JS `true`.
- **Custo da opção (a):** baixo — o emit é o MESMO intrínseco do `as`
  (I2D/L2D/I2F já existem no backend; `fcvt.d.w`/`fcvt.d.l` funcionando nos
  3 nativos após o §82); o trabalho é só definir a semântica (trunc vs
  round vs throw) e rotear o call no `emitCall`. **Custo da (b):** uma
  rejeição no `MethodCallTyper`/`SemMethodCallTyper` com mensagem apontando
  p/ o cast `as` — e quebra-retro? (JVM/JS aceitam hoje; rejeitar no typer
  atinge TODOS os targets — código de usuário que usa `5.toDouble()` no JVM
  pararia de compilar → é mudança de contrato, bump).

### 90. `KofWebHardeningTest.sse_events_sent_counter_tracks_calls` é FLAKY sob carga da suíte completa (espera 3, obtém 2) — ABERTO (lane web; não-correlacionado com a varredura STDLIB, achado 10/09)

- **Sintoma:** na suíte completa (`mvn test` 4 módulos, ~1281 testes rodando
  juntos) o caso `sse_events_sent_counter_tracks_calls` (KofWebHardeningTest
  linha ~395) falha `expected: <3> but was: <2>` — o contador de eventos SSE
  enviados fica 1 atrás no momento da asserção. **Passa 3/3 quando rodado
  isolado** (`-Dtest='KofWebHardeningTest#sse_events_sent_counter_tracks_calls'`,
  8–9s cada) — medido 10/09.
- **Diagnóstico:** não-correlacionado com a varredura STDLIB (uuid/parse
  string/asm cross — nenhum toca web/SSE). O padrão "espera 3 obtém 2, verde
  isolado, vermelho sob carga" é corrida de despacho/contagem do servidor SSE
  no teste: a asserção lê o contador antes do último write ter sido
  contabilizado sob contenção de CPU da suíte paralela. (Investigação da causa
  raiz exata = lane web.)
- **Menor repro:** roda a suíte completa com qemu (carga alta) → o caso cai
  com 2; roda isolado → verde. Não consegui reproduzir isolado em 3 tentativas
  (10/09).
- **Não corrigido aqui (regra 3):** não "conserto o teste para passar" (não
  é minha lane nem tenho a causa raiz do despacho SSE); registrado com
  evidência p/ a lane web. Suspeito de asserção sem sincronização — provável
  fix = esperar o contador convergir (poll com timeout) em vez de ler 1x, OU
  eliminar a corrida no contador do handler. Se a causa raiz for um undercount
  real (evento perdido), aí é bug de funcionalidade da lane web.


### 95. Native: 2+ `String.split` no mesmo programa → assembler "already defined" (COMP001) — ✅ CORRIGIDO 10/09 (x86_64; varredura de paridade String)

- **Sintoma:** `var a = "x,y".split(",").length; var b = "p,q".split(",").length`
  falha no Native x86_64: `ld: symbol '.Lkof_split_empty_sep' is already
  defined` → COMP001 (erro de montagem). QUALQUER programa com 2+ splits
  (parsear 2 linhas CSV, query-string + header) era **incompilável** no Native;
  JVM/Script rodam normal. Paridade quebrada (regra 5) de forma barulhenta.
- **Causa raiz:** o ramo inline do `split` (`NativeX86StringCalls.emit`,
  extraído verbatim do `NativeBackend.emitCall` na FASE 3 do REFACTOR-500)
  emitia DUAS labels com nome FIXO (`.Lkof_split_empty_sep` / `.Lkof_split_call`)
  dentro do corpo de cada call site. Um segundo `split` no MESMO arquivo `.s`
  redefinia o símbolo → erro do assembler. Os demais ramos inline usam labels
  via `resolveLabel`/contador; o `split` foi o único que ficou com nome estático.
- **✅ CORRIGIDO 10/09 (x86_64):** `NativeBackend` ganha `inlineSeq` (resetado
  por programa, junto de `stringCounter` — output determinístico); o ramo do
  `split` sequencia as labels (`.Lkof_split_empty_sep<N>`/`.Lkof_split_call<N>`).
  `NativeX86StringCalls.emit` recebe o `nb` (única mudança de assinatura; o
  `emit` já é estático e o único caller é `NativeX86Calls.emitCall:78`).
- **Prova:** `NativeE2ETest.nativeTwoSplitsInOneProgram` (2 splits + get: `5\nn`);
  oracle JVM==Native==Script no mesmo programa. Suíte 0 falhas.
- **Nota (riscv/aarch):** o backend cross não tem o mesmo ramo inline de split
  com labels fixas (o `kof_string_split` é chamado direto) — não reproduz.

### 96. `String.repeat`/`padStart`/`padEnd` como MÉTODO DE INSTÂNCIA — aceito e quebrado em 3/4 backends — ✅ CORRIGIDO 11/09 (SEM052: rejeitar e apontar p/ o idiom `strings.*`)

- **Sintoma (paridade absoluta JVM=JS=X86=ARM=RISC quebrada):** `"ab".repeat(3)`,
  `"ab".padStart(5,"-")`, `"ab".padEnd(5,"-")`, `"abcdef".truncate(3)`,
  `"7".padLeft(3,"0")`, `"ab".reverse()`, `"abc".count("a")`, `"a".isAlpha()`,
  `"a".toCamelCase()`, `"a".escapeHtml()`, `"a".slugify()` (toda a superfície
  `strings.*` chamada como método) eram ACEITOS pelo typer e cada target fazia
  UMA COISA DIFERENTE:
  | alvo | `"ab".repeat(3)` | `"ab".padStart(5,"-")` |
  |---|---|---|
  | JVM | `NoSuchMethodError String.repeat(I)` (descritor sai Object) | `NoSuchMethodError` idem |
  | Native x86 | `undefined reference java_lang_String_repeat` (link-fail) | idem (link-fail) |
  | JS | roda o `.repeat` NATIVO do JavaScript (paridade por acaso) | roda `.padStart` do JS |
  | Script | `ababab` (reflexão JDK) | vazio + `exit=1` |
- **Causa raiz:** nenhuma parte do typer lowering conhece esses nomes como
  métodos de String (a `StringMethodRegistry` não os lista), mas nenhum ponto
  os REJEITA — o `KofCall` sai com owner `String` e o backend cada um faz o que
  dá (`JvmTypeMapper` monta descritor Object; Native emite call p/ símbolo que
  ninguém define; JsCallEmitter cai no método nativo do JS; o interpretador
  resolve por `Method.invoke` no JDK). O corpus (`training/idioms/stdlib.md:47-50`,
  `learn/39-stdlib.md:71-74`) só documenta a forma FUNÇÃO:
  `strings.repeat("ab", 3)` / `strings.padLeft("7", 3, "0")` / `strings.truncate`.
- **Decisão (opção B da mantenedora, 11/09 — paridade absoluta é a regra):**
  REJEITAR em compile-time com **SEM052** apontando para o idiom real
  (`Kof não tem método "repeat" de String; use a função da stdlib:
  strings.repeat(...` — com `padStart→padLeft`/`padEnd→padRight` no hint).
  Guard por NOME (`NOT_INSTANCE_METHODS` = toda a superfície de
  `KofStrings.staticMethod`) no branch `isString(recvType)` do
  `ExpressionInstanceCallLowerer` — mesmo ponto dos guards §100/SEM051:
  lowering é o frontend ÚNICO dos 5 alvos, um erro igual em todos por
  construção. NÃO flaguemos `toUpperCase`/`toLowerCase`/`trim`/`split`/
  `replace`/`substring`/`equals`/`indexOf`... (SÃO métodos na registry).
  Verificado: JVM/NATIVE/JS dão SEM052 idêntico; `interpret()` (Script) LANÇA
  com a mesma mensagem (antes: `ababab`/vazio por reflexão).
- **Prova:** `SemanticResolutionTest.stringsFunctionsAsInstanceMethodsRejected`
  (14 formas × SEM052) + `stringsFunctionsAndRealStringMethodsStillCompile`
  (funções `strings.*` + métodos reais de String não regridem).
- **Descoberto:** 10/09; **corrigido 11/09** sob a diretriz "paridade entre os
  targets em primeiro lugar; opção B = rejeitar em tempo de compilação".

### 97. Native: `String.compareTo`/`String.hashCode` declarados no reference → `undefined reference` no link — ✅ x86_64 CORRIGIDO 10/09 (varredura String parte 2; faces JS + riscv/aarch residuais)

- **Sintoma:** `a.compareTo("abd")` e `a.hashCode()` falham no link Native
  x86_64: `undefined reference to java_lang_String_compareTo` / `_hashCode`
  (COMP001). riscv/aarch idem (mesmo `emitCall` genérico → símbolo `java_lang_String_*`
  nunca definido no runtime). **JVM e interpretador rodam** (o typer aceita —
  `BuiltinCallTyper.java:420` tipa os dois como `String→Int`; o interpretador
  trata `hashCode` em `KofInterpreterObjects:32`/`KofInterpreterCollections:74`).
- **Contradição com o corpus (por que é paridade, não design):** o
  `docs/language-reference/type-system.md:289` DECLARA a API — "`String`:
  indexOf/length/**compareTo/hashCode**→Int". O typer honra a declaração; os 3
  nativos não. Paridade cross-target quebrada (regra 5) em método *documentado*
  — família do §96 (corr. 11/09 com SEM052), mas lá o método NÃO está no
  corpus (rejeitado); aqui ESTÁ (implementado no x86).
- **Causa:** nenhum dos 3 backends nativos emite os intrínsecos
  `java_lang_String_compareTo`/`_hashCode`. `NativeX86StringCalls.emit` roteia
  length/charAt/substring/indexOf/… mas não estes dois → caem no `emitCall`
  genérico que chama o símbolo que ninguém define (mesma raiz do §96/§89).
- **A armadilha que o fix NÃO pode repetir (lição bug 43):** uma implementação
  byte-a-byte (`memcmp` no UTF-8, soma de bytes no `hashCode`) DIVERGE do JVM
  em strings astrais/multi-byte: o `String.compareTo` do JVM compara **code
  units UTF-16** (`a😀b` vs `a�b` — o 😀 é 2 surrogados), o `hashCode` é
  `31*…` sobre UTF-16. Exatamente o que o §43 pegou em charAt/substring/indexOf.
  O fix correto reusa `.Lkof_substr_walk` (decoder UTF-8→code-unit) nos 2.
- **✅ CORRIGIDO 10/09 (face x86_64):** arquivo novo `runtime/RuntimeStringCompare`
  encadeado em `NativeRuntime.emitRuntime`; o helper `.Lksu_next` decodifica o
  UTF-8 interno em **sequência de code units UTF-16** (par astral → high, depois
  low pendurado no cursor) — NÃO memcmp/byte-sum; `kof_string_compare_to`
  (primeira unit diferente → `A−B`, como o JVM; prefixo → diferença de
  contagem de units) + `kof_string_hash_code` (`h=31*h+unit`). Routing em
  `NativeX86StringCalls.emit` (caller pop → rdi/rsi; convenção dos demais
  `kof_string_*`). Bugs pegos na prova: (a) a validação de continuação
  (`and 0xC0/cmp 0x80`) DESTRUÍA o registrador do byte antes do `and 0x3F` →
  é(233) virava 192 — reler/re-usar scratch (`r8d/r10d/r11d`); (b) em `.Lksn4`
  o bookkeeping das posições lia b2 como b3 (astral hash 131791936 vs 1772899);
  (c) o `.Lct_diff` comparava além do fim da string curta (prefixo `ab`/`abc`
  dava −99) — agora unit 0 (fim) cai na contagem de units.
- **Prova:** `NativeE2ETest.nativeStringCompareToAndHashCodeUtf16` — 11 vetores
  com astral/BMP/prefixo/vazio, golden JVM==Native==Script idênticos
  (`10 1 -1 -1 55260 -10176 10176 96354 3240 1772899 0`). Suíte da área verde
  (NativeE2ETest 59, BackendParity 16, ConformanceMatrix 11, doc-gate).
- **Residuais (honestos, NÃO regredidos):** **JS** — `JsCallEmitter` não trata os
  dois no switch; caem no `default` → `texto.compareTo(o)`/`texto.hashCode()`
  que NÃO existem em `String.prototype` → `TypeError` em runtime (bug-irmão do
  `equals`, que é tratado). Como node está AUSENTE aqui, não travar por teste —
  face da lane JS. **riscv64/aarch64** — os símbolos vivem só no `.s` x86
  (`NativeRuntime` é x86-only; o cross tem suas fatias). Ferramenta de cross
  ausente neste ambiente → portar no env da lane cross (com qemu) reusando o
  MESMO algoritmo de code-unit. Ver matriz `backend-parity.md`.
- **Continuação 10/09 (mesma varredura): `String.equals` link-fail** →
  `undefined reference java_lang_String_equals`. O `==` de String JÁ baixava p/
  `kof_string_equals` (conteúdo, null-safe); o MÉTODO `.equals` não era roteado
  (caía no caminho genérico). Fix: routing em `NativeX86StringCalls.emit` p/ o
  MESMO `kof_string_equals` (type-system.md:258 documenta ".equals funciona
  (probe) mas é anti-pattern — use `==`"). **Guard `isString(ownerType)` é
  essencial:** `record.equals` (gerado campo-a-campo, `ExpressionBinaryLowerer:196`)
  NUNCA pode ser hijackado — provado lado a lado no mesmo programa.
- **Resíduo NEW (não-meu escopo, registrar): `Object.equals`** — `var o = s as
  Object; o.equals("café")` dá `undefined reference java_lang_Object_equals` no
  link (JVM/Script rodam). Diferente do caso String: exige **dispatch virtual**
  (vtable) num receiver tipado como referência — não é "só chamar o intrínseco",
  é o mecanismo de `invokevirtual` genérico do Native. Decidir com a lane Native
  (dispatch) — NÃO silencioso: gap aberto, menor repro `/tmp/oq.kf`.
- **Descoberto:** 10/09 na varredura de paridade String (batch `swB.kf`/`swF.kf`/`sw2b.kf`).

### 98. String `<`/`>`: três backends divergem e TODOS dão lixo — ✅ CORRIGIDO 11/09 (SEM053: rejeitar, opção B da mantenedora)

- **Sintoma (medido 10/09, 3 targets no MESMO programa `swE.kf`,
  `"abc"` vs `"abd"`):** `a<b | a>b | b<a | b>a | a==b` —
  **JVM** `false|false|false|false|false` (tudo false: `if_acmp` em referência
  é sempre-falso p/ `<`/`>`); **Native x86_64** `false|true|true|false|false`
  (compara o **ponteiro** — ordem de alocação, não conteúdo); **interpretador**
  `true|false|false|true|false` (lexicográfico, **invertido** p/ `<` vs `>` do
  Native). `a==b` bate (`false`) nos 3 (conteúdo, congelado — §regra 6).
- **Revalidado 11/09** sob a diretriz nova da mantenedora ("paridade entre os
  targets em primeiro lugar; regra absoluta JVM=JS=X86=ARM=RISC; opção B =
  rejeitar em tempo de compilação"): os 3 alvos reproduzem os 3 resultados
  DIFERENTES acima — é exatamente o que a regra proíbe (mesmo código,
  comportamento distinto). Não é mais caso de "decisão de design aberta": a
  decisão É rejeitar (a mesma família da opção B dos §96/§100).
- **✅ CORRIGIDO 11/09 — SEM053 no typer de resultado binário**
  (`TypeChecker.inferBinaryResultType`): `<`/`<=`/`>`/`>=` com um lado
  String (ou `Nullable(String)`) agora é **erro em compile-time** apontando
  para o idiom: "use `s.compareTo(t) < 0`" (com o relacional correto por
  operador). Operando **mistos** (`"abc" < 'b'`, `"abc" < 1`) caem na MESMA
  SEM053 (o guard é `isMaybeString(left) || isMaybeString(right)`). Como o
  `inferBinaryResultType` é o frontend ÚNICO (alimenta o shortcut de condição
  `if`/`while`/`for` **e** o valor `KofBinary` de `println(a<b)`), a rejeição
  é idêntica nos 5 alvos — verificado: JVM/NATIVE/JS dão SEM053 igual no
  `compile` e o `interpret()` (Script) LANÇA a mesma mensagem.
- **Escopo cirúrgico (não regridir — regra 1):** `==`/`!=` de String (conteúdo,
  congelado §regra 6) e `+` (concat) NÃO passam pelo guard; toda comparação
  NUMÉRICA (int/long/float/double, `while (n < 10)`) e Char-vs-Char seguem
  válidas. O caminho de referência não-String (`if_acmp*` p/ Object) não é
  tocado.
- **Prova:** `SemanticResolutionTest.stringOrderingOperatorsRejected` (6 formas
  × SEM053) + `stringEqualityAndNumericOrderingStillCompile` (==/!= numérico/
  compareTo idiom não regridem). Suíte completa pós-clean abaixo.
- **O que um dia reabriria discussão:** (b) definir ordem lexicográfica UTF-16
  como CONTRATO (exigiria os 5 backends + bump + migração — hoje o `compareTo`
  cobre 100% dos usos reais e o reference mantém `Unspecified`). Não é a
  decisão desta sessão.
- **Descoberto:** 10/09; **corrigido 11/09** (SEM053, opção B).

### 100. Char como argumento de método String aceito em silêncio → quebra de um jeito DIFERENTE em cada target (paridade absoluta + R6) — ✅ CORRIGIDO 11/09 (SEM051, opção B: rejeitar em compile-time)

- **Sintoma:** `"abc".indexOf('c')` (Char, não String) é **aceito** pelo compilador
  e roda de um jeito **diferente em cada target** — paridade absoluta (JVM=JS=x86=arm=risc)
  quebrada em silêncio (R6):
  | método | JVM | Native x86 | Script |
  |---|---|---|---|
  | `indexOf('c')`/`lastIndexOf`/`startsWith`/`endsWith`/`split(',')` | `VerifyError` (getfield/invokevirtual com int p/ slot String) | saída VAZIA (SIGSEGV silencioso) | saída VAZIA |
  | `contains('b')` | `IncompatibleClassChangeError` (Integer→CharSequence) | vazio | `false` (lixo) |
  | `compareTo('a')` | `NoSuchMethodError String.compareTo(int)` | vazio | vazio |
  | `concat('x')` | `VerifyError` | vazio | vazio |
  (`replace('b','x')` **funciona** nos 3 — Java tem `replace(char,char)` e a
  `StringMethodRegistry` resolve o overload pelo tipo do arg.)
- **Causa raiz:** os métodos String com parâmetro String/CharSequence
  (`indexOf`/`lastIndexOf`/`contains`/`startsWith`/`endsWith`/`split`/`concat`/
  `compareTo`/...) NÃO têm checagem de tipo de argumento no typer — o bloco de
  String do `BuiltinCallTyper` e o `CollectionMethodTyper` devolvem o tipo de
  RETORNO sem validar os args, e o `MemberCallTyper:415-420` pula a checagem
  para String (comentário: "resolvidos via lowering direto"). O Char
  (representado como Int em Kof) desce pro lowering e cada backend o trata
  diferente: JVM faz `invokevirtual indexOf(I)` (não existe → VerifyError),
  Native chama `kof_string_index_of` esperando ponteiro-String e dereferencia
  o Int 99 → SIGSEGV, Script unbox-falha e engole.
- **✅ CORRIGIDO 11/09 (estendido na mesma sessão) — opção B (decisão da
  mantenedora): REJEITAR em tempo de compilação**, o MESMO diagnóstico em todos
  os backends. Guard no `ExpressionInstanceCallLowerer` (branch
  `isString(recvType)`), por NOME do método (`STRING_ARG_METHODS` —
  `compareTo`/`compareToIgnoreCase` não estão na `StringMethodRegistry`
  (sig=null), então um guard por assinatura ESCAPAIRIA deles), **por POSIÇÃO
  contra a formal da registry**: qualquer argumento cujo formal é
  String/CharSequence e cujo tipo NÃO é String (Char **ou Int/Long/Double/
  array/classe** — `equalsIgnoreCase(5)` dava JVM `ExceptionInInitializerError`,
  Native vazio, Script `false`) vira **SEM051** ("String.X não aceita Int como
  argumento 1 (o parâmetro é String); use o literal String"). A checagem por
  posição preserva `indexOf("a", 2)` (formal String,Int → só a pos-1 é
  stringy). Como o lowering é o frontend ÚNICO que alimenta JVM/Native/JS/Script
  (paridade por construção), o MESMO erro sai nos 5 alvos. Verificado:
  JVM/NATIVE/JS dão o SEM051 idêntico no `compile`; `interpret()` (Script)
  LANÇA `KofInterpretException: SEM051` em vez de rodar lixo.
- **✅ `String.equals(não-String)` CORRIGIDO 11/09 (constante-fold, paridade):**
  `equals` é formal `Object` (não-String é um uso LEGÍTIMO — Java devolve
  `false`), mas o Native CRASHAVA: `kof_string_equals` lia o Int-boxado como
  ponteiro-String (SIGSEGV/saída vazia) enquanto JVM/Script/JS davam `false`.
  O lowering (único p/ os 5) faz constant-fold quando o arg é **provavelmente
  não-String** (primitivo, array, ou classe Kof — String é final e java.lang
  fica de fora por poder PORTAR String em runtime): roda o efeito do arg,
  descarta receiver+arg (`KofPop`), empilha `false`. `equals(String)` segue
  pelo runtime (`kof_string_equals`/`Objects.equals`/`===` no JS). Prova
  runtime nos 4: matriz `equalsfold` (true/false/false/false — JVM+NATIVE+
  SCRIPT+JS(Graal) idênticos).
- **Escopo cirúrgico (não regridir — regra 1):** NÃO flaguemos `replace('b','x')`
  (overload char,char legal, `charAt('1')`/`substring(1)` (formal numérico — Char
  é Int, widening do usuário; erro de índice é runtime legítimo), nem `equals`
  (formal Object). `indexOf("c")`, `compareTo("a")`, `contains("b")` permanecem.
- **Prova:** `SemanticResolutionTest.charArgOnStringMethodRejected` (12 formas
  × SEM051, incluindo Int-arg `equalsIgnoreCase(5)`/`concat(5)`) +
  `stringMethodsWithStringOrCharArgsStillCompile` (não regridir
  `replace(char,char)`/`charAt`/literal-String) + matriz `equalsfold` (runtime
  nos 4 targets). Suíte completa pós-clean abaixo.
- **NOTA de design (por que rejeitar e não implementar):** Kof NÃO tem overload
  `(char)` p/ esses métodos no corpus (`type-system.md:289` lista `indexOf`→Int
  sem dizer o tipo do arg; o idiom real é `"c"`). A alternativa (implementar a
  sobrecarga char em 5 backends) seria mudar o contrato de forma aditiva — é
  opção de design, mas a mantenedora optou pela REJEIÇÃO (opção B) porque
  mantém a linguagem simples e o custo de 5 backends não se justifica p/ um
  caso que o literal String cobre (`"c"` em vez de `'c'`).
- **Arquivos:** `ExpressionInstanceCallLowerer.java` (guard SEM051 +
  `STRING_ARG_METHODS`); testes em `SemanticResolutionTest.java`.


### 102. Native/JS: `indexOf(String, from)`/`lastIndexOf(String, from)`/`startsWith(String, from)` ignoravam/am o índice inicial — ✅ CORRIGIDO 11/09 (x86_64 + JS; paridade absoluta)

- **Sintoma (medido 11/09):** `"aXb".indexOf("X", 2)` → **JVM `-1`** (correto),
  **Native `1`** (o helper `kof_string_index_of` lê só `%rdi/%rsi` e IGNORA o
  `%rdx` que o roteador já empilha — aridade 2 vira 1 em silêncio). No **JS**,
  o `String.prototype` respeita o `from` mas **diverge do JDK no clamp**:
  `lastIndexOf("a",-1)` JS `0` vs JDK `-1`; `startsWith("",4)` JS `true` vs
  JDK `false`; vazio+from idem. Paridade absoluta JVM=JS=X86=ARM=RISC quebrada
  de 2 formas diferentes (R6). Mesma família para `lastIndexOf(s,from)` e
  `startsWith(s,from)`.
- **✅ CORRIGIDO 11/09 (x86_64):** helpers novos `kof_string_index_of2` /
  `kof_string_last_index_of2` / `kof_string_starts_with2` (arquivo novo
  `runtime/RuntimeStringSearchFrom.java` — regra ≤500; registrados em
  `NativeRuntime`) com as clampagens do **JDK 21 travadas em oracle**
  (`Jdk.java`/`Jdk3.java`: from<0→0 no indexOf e −1 no lastIndexOf e false no
  startsWith; from>totalH→totalH (indexOf/lastIndexOf) / false (startsWith);
  needle vazia→start (indexOf) / min(from,totalH) (lastIndexOf) / true se
  within (startsWith); corte de par astral no início → pulado/false, como o
  scan do bug 43). Reusam `.Lkof_substr_walk` (UTF-16 code units) e o from vive
  em registrador callee-saved antes dos walks clobberarem. Roteamento em
  `NativeX86StringCalls` por **aridade** (`parameterTypes().size() >= 2` →
  helper `_2`; 1-arg intacto).
- **✅ CORRIGIDO 11/09 (JS):** helpers `kof_string_index_of2`/
  `_last_index_of2`/`_starts_with2` no runtime `STDLIB_RUNTIME` com os clamps
  do JDK (JS é UTF-16 nativo — o scan por `startsWith` no laço é exato);
  `JsCallEmitter.handleStringOp` roteia por aridade (2+ args → helper; 1-arg
  segue o protótipo, que bate o JDK).
- **Prova:** oracle JDK 21 gravado em `Jdk/Jdk3`; `NativeE2ETest.
  nativeStringSearchFromIndex` (31 vetores — 18 ASCII edge + 13 astrais UTF-16:
  cut de par, needle astral, from sobre surrogate) golden JVM==Native; matriz
  `searchfrom` (JVM=NATIVE=SCRIPT=**JS(Graal)** idênticos nos 6 vetores de
  clamp — a célula que PEGOU a divergência do JS); DocTest par célula.
  Suíte completa pós-clean **1471 run / 0 falhas** (12 err = node ausente).
- **Residual honesto:** faces riscv64/aarch64 — o roteamento cross
  (`NativeRiscvCrossOps`) mapeia `indexOf/lastIndexOf` p/ o helper de 1 arg
  byte-based do port (o MESMO bug, com o sub-problema UTF-8 byte-vs-unit do
  §43 cross) — port só com qemu/toolchain (bug 59, lane cross `2b9a483b`).
- **Descoberto:** 11/09 (sweep §100); **corrigido 11/09** (x86_64 + JS).


### 104. Métodos de objeto (equals/hashCode/toString) de record divergem por target — §104a ✅ CORRIGIDO 11/09 (Script), §104b em aberto (Native)

- **Superfície:** `record Point(Int x, Int y)` — `p1.equals(p2)`, `==`, e
  record **dentro de coleção** (`listOf(p1).contains(p2)`, `setOf(p1).contains`,
  `mapOf(p1,7).get(p2)`, `println(listOf(p1))`). Oracle = **JVM** (registro
  real gera equals/hashCode/toString por **conteúdo**; classe não-record =
  **identidade** — `Thing(5).equals(Thing(5))` é `false`). Matriz (`/tmp/om.kf`):

  | alvo | `listOf(p1).contains(p2)` | `setOf.contains` | `map.get` | `println(list)` | `Thing.equals` |
  |---|---|---|---|---|---|
  | JVM | true | true | 7 | `[Point[x=1, y=2]]` | false |
  | Script (antes) | **false** | **false** | **0** | `KofObj@...` | **true** ❌ |
  | Script (depois) | true | true | 7 | `[Point[x=1, y=2]]` | false |
  | Native | **LINK_FAIL** (`Thing.equals`) | — | — | — | — |

- **§104a ✅ CORRIGIDO 11/09 (Script) — opção (a) backend erra:** `KofObj`
  (o objeto do interpretador) **não sobrescrevia** `equals`/`hashCode`/
  `toString`. Os métodos sintéticos só existiam no dispatch `KofInterpreter`
  (chamada virtual Kof `.equals()`), mas o **JDK** chama `Object.*` por dentro
  de `ArrayList.contains/indexOf`, `HashMap`/`HashSet` e `List.toString` → usava
  **identidade**. Fix: override real em `KofObj` delegando em helpers estáticos
  `KofInterpreterObjects.objectEquals/objectHash/objectToString`; conteúdo SÓ
  para `isRecord()`, classe não-record mantém identidade (e o `.equals()` de
  classe, que antes dava `true` errado, agora dá `false` = JVM).
  - **Prova:** `KofInterpreterParityTest.recordsInCollectionsUseContentEquals`
    (gate interpretador≡JVM com record em list/set/map/println). Suíte completa
    pós-clean **1474 run / 0 falhas** (12 err = node ausente).
  - **Arquivos:** `KofInterpreter.java` (override em `KofObj`),
    `KofInterpreterObjects.java` (helpers estáticos + equals de classe→identidade).
  | JS | **false** | **false** | **null** | `Point[x=1, y=2]` (sem `[]`!) | — |

- **§104b ⏳ ABERTO (Native):** (i) `Thing(5).equals(Thing(5))` (classe
  não-record, método `equals` NÃO declarado) → **LINK_FAIL** (`ld:
  undefined reference to Thing_equals` — `resolveCalleeName` mangla o dono mas
  ninguém emite o `Object.equals` herdado). (ii) record em coleção:
  `kof_list_contains`/set/map comparam `cmpl %r12,%rax` (ponteiro) ou só
  string — nunca o equals de conteúdo. Reprodução mínima: célula `objmethods`
  (Native excluído) + `/tmp/om.kf` / `/tmp/req3.kf`. Proibido: fallback
  silencioso.
- **§104c ⏳ ABERTO (JS):** record em `setOf`/`mapOf`/`listOf().contains`
  usa **identidade** (Map/HashSet JS nativos com objeto por referência) e
  `println(listOf(p1))` imprime `Point[x=1, y=2]` **sem os colchetes**
  (join sem wrapper `[...]`). Prova: célula `objmethods` (JS excluída, medido
  11/09: `false|false|null|Point[x=1, y=2]`). Fix exige wrapper de coleção
  com equals/hashing por conteúdo no emitter JS — unidade própria.


### 103. Subscript `x[i]` em String/List/Map/Set aceito em silêncio → quebra os 3 targets (VerifyError/vazio) — ✅ CORRIGIDO 11/09 (SEM054, opção B)

- **Sintoma:** `"abc"[0]` e `listOf(10,20)[1]` (e escrita `l[0] = 9`) eram
  ACEITOS pelo parser/typer e quebravam de um jeito em cada target (R6 +
  paridade absoluta): **JVM** `VerifyError: Bad type on operand stack in
  aaload` (o receiver é Object/`kof.List`, não array — a classe nem
  inicializa), **Native**/Script **saída vazia** (exit=1 silencioso).
  Achado no sweep de coleções/records (11/09, `/tmp/col1.kf`→`/tmp/rec1.kf`):
  o programa inteiro morria no JVM pelo último statement.
- **Corpus:** o `[]` SÓ existe para **array** (`learn/04-variables-and-types.md:84`
  `numeros[0]`, `training/idioms/control-flow.md:81` `nums[0] = 5` — ambos
  `new Int[n]`). Coleção tem accessor (`get(i)`, `charAt(i)`, `substring`);
  `[]` em coleção NUNCA foi documentado nem funciona em algum backend.
- **✅ CORRIGIDO 11/09 — opção B:** guard no `SemExpressionTyper` (caso
  `ArrayAccessExpr` — o frontend SEMÂNTICO único dos 5 alvos, casa do
  §99/SEM050): receiver com tipo **Kof-collection** (String/List/Map/Set,
  desembrulhando Nullable) → **SEM054** ("`[]` só pega em array em Kof; para
  esta coleção use charAt(i) / substring(i) | get(i) | get(k)"). Cobertura de
  LEITURA E ESCRITA (`l[0] = 9` cai no mesmo caso). NÃO flagados:
  `ArrayType` (legítimo), `UnknownType`/`Nullable(Unknown)` (pode ser array em
  runtime via get sem pin — SG-008; flagar regridiria código válido).
- **Prova:** `SemanticResolutionTest.subscriptOnCollectionsRejected` (5 formas
  × SEM054) + `subscriptOnArraysStillCompiles` (array simples/2D/não regridir);
  verificado SEM054 idêntico em JVM/NATIVE/JS + `interpret()` lança a mesma
  mensagem. Suíte completa pós-clean **1473 run / 0 falhas** (12 err=node).
- **Arquivos:** `SemExpressionTyper.java` (caso ArrayAccessExpr + helpers
  `isKofCollectionType`/`collectionIndexHint`); `SemanticResolutionTest.java`.


### 99. `Int.MAX_VALUE`/`<primitivo>.<campo>` passa SEM diagnóstico → lixo nos 3 targets + CRASH do compilador — ✅ CORRIGIDO 10/09 (R6; varredura numeric/estático)

- **Sintoma:** `Int.MAX_VALUE` (e qualquer `Int/Long/Double/Float/Char/Byte/
  Short/Bool.<campo>`) é **fake idiom** (não existe em Kof — corpus só usa
  literal ou `as`). Mas o compilador ACEITAVA:
  - `println(Int.MAX_VALUE)` → `ok=true` + `getfield "?".MAX_VALUE` no bytecode
    → **JVM** `NoClassDefFoundError: ?`, **Native** SIGSEGV, **Script** `null`.
  - `var x = Int.MAX_VALUE` (assignment) → **crash do próprio compilador**:
    `RuntimeException: frame crash ... NegativeArraySizeException: -1` (ASM
    COMPUTE_FRAMES) — um programa do usuário derruba o `mvn`/driver.
  - `Int.foo` / `Int.SIZE` → idem, aceito silenciosamente.
- **Causa raiz:** em `SemExpressionTyper`, o receiver `Int` é um
  `IdentifierExpr` com nome em `isBuiltinTypeName` → a isenção (linha ~173,
  que existe p/ **posição de tipo**: `var x: Int`, `x as Int`) suprime o SEM011
  e `inferType` cai em `yield UNKNOWN`. No caso `FieldAccessExpr`, o único
  guard de campo inexistente (SEM025, linha ~383) dispara só em
  `recvType instanceof ClassType` — `UNKNOWN` não é ClassType, logo o campo
  escapa SEM diagnóstico. O lowering (`ExpressionLowerer`/`ExpressionTyper`
  `case FieldAccessExpr`) então emite um acesso a campo num dono primitivo →
  `JvmOpEmitter:104` faz o fallback `sf.ownerType() instanceof ClassType ct ?
  ct.name() : "?"` → o dono literal `"?"` cai no constant pool.
- **✅ CORRIGIDO 10/09 (R6, nunca silencioso — NÃO é mudança de contrato, é
  rejeitar código que nunca funcionou):** novo guard no caso `FieldAccessExpr`
  do `SemExpressionTyper` — se `recvType` é `UNKNOWN` **e** o receiver é um
  `IdentifierExpr` cujo nome é `isBuiltinTypeName` → **SEM050** ("'<T>' é um
  tipo primitivo, não tem campo estático '<f>' (use o literal...)"), antes do
  lowering. Como `analyze()` → `hasErrors()` → `return null` ocorre ANTES do
  `visitMaxs`, o crash do compilador e o bytecode lixo morrem no typer, nos 3
  targets (JVM/Native/interpretador compartilham o frontend — paridade por
  construção).
- **Escopo cirúrgico (não regridir legítimo):** `String.valueOf(42)`/
  `String.format(...)`/`Int.parseInt(...)` são **MethodCallExpr** (têm
  parênteses/args), caminho diferente (`BuiltinCallTyper`) — NÃO afetados
  (probe: `String.valueOf(42)`→"42" nos 3). `s.length` (instância, receiver
  String-typed) e anotação/cast `x: Int`/`x as Int` permanecem válidos (testado).
- **Prova:** `SemanticResolutionTest.staticFieldOnPrimitiveTypeRejected`
  (8 tipos × 4 campos, incl. a forma assignment que crashava) +
  `primitiveAsTypeAndLiteralStillCompile` (anotação/cast/length-instância/literal
  compilam). Suíte completa **1464 run / 0 falhas** (12 err = node ausente).
- **Arquivos:** `SemExpressionTyper.java` (guard SEM050); testes em
  `SemanticResolutionTest.java`.


### 101. Operadores relacionais de Double com NaN: x86/JVM/Script divergem do riscv/aarch (e entre si em `<=`/`>=`) — ABERTO (regra 6 — operadores congelados, decisão da mantenedora)

- **Sintoma (medido 11/09 na prova do MATH001 — menor repro, mesma entrada
  nos 5 alvos):** `var n = 0.0/0.0` e comparações relacionais com NaN:

  | programa | JVM | x86_64 | riscv64/aarch64 |
  |---|---|---|---|
  | `1.0 < n` | true | true | **false** |
  | `1.0 > n` | false | false | false |
  | `1.0 <= n` | true | **false** | **false** |
  | `1.0 >= n` | false | false | false |
  | `n < 1.0` | true | true | **false** |

  (IEEE 754 puro: TODO relational com NaN é false — o riscv segue IEEE; o
  JVM/JS seguem a semântica Java de `dcmpg`/`<` que troca o resultado p/
  `true` no NaN — é o mesmo quirk do bug 94, que congela `NaN == NaN` =
  `false` nos compilados mas `true` no interpretador via `Double.compare`.)
- **Causa:** no cross, `NativeRiscvCrossOps.emitCrossBinaryRiscv` usa
  `flt.d`/`fle.d`/`fgt`/`fge` p/ os operadores relacionais (IEEE — NaN
  sempre false), enquanto o x86 usa `cmpsd`+`seta/setae`-style com swap
  (quirk `dcmpg`) idêntico ao `DCMPL/DCMPG` do JVM. Os dois caminhos são
  "defensáveis"; a spec da linguagem NÃO fixa a semântica NaN de `<`/`>`
  (expressions.md: relacional de float segue o backend). Escolher UMA das
  duas = mudança de contrato sobre operadores congelados (regra 6:
  bump + discussão com a mantenedora), então **NADA foi alterado aqui**.
- **Notas do registro:** (a) a face `!=`/`==` JÁ estava congelada correta-
  mente — o achado do MATH001 foi só no NE riscv (`fle+snez` dizia
  `NaN != NaN` = false, divergia de TODOS os outros = true IEEE) —
  corrigido p/ `feq+seqz` em 11/09 (alinha o cross com o contrato IEEE
  já travado na matriz `stdsqrt`/golden KofMathTest, NÃO é escolha nova);
  (b) o parâmetro do `<`/`<=`/etc. fica como está nos 3 caminhos atuais.
- **Próximo passo honesto:** decisão DD no planning-* (qual família: IEEE
  pura nos 5 ou quirk-JVM nos 5) — a matriz não cobre relacionais com NaN
  (só Bool-equality, padrão bug 44), então nada está silencioso.
- **Arquivos:** `NativeRiscvCrossOps.emitCrossBinaryRiscv` (cross),
  `NativeX86Calls`/`RuntimeFp` (x86), `JvmOpEmitter` (JVM DCMPL/G).

### 102. `random.int(bound)`/`randomInt(bound)` em riscv64/aarch64 entra em LOOP INFINITO para qualquer bound > 1 — ✅ CORRIGIDO 11/09 (aritmética de rejection sampling)

- **Sintoma:** `KofRandomTest.randomIntCrossArch`/`randomShapeCrossArch`
  travavam (0 output, CPU 53% indefinidamente) sob qemu-riscv64 com
  `random.randomInt(1000)`. Reprodução mínima: os 500 loops do próprio
  shape-test (qualquer bound>1 basta; bound=2 trava 100% — range=0).
- **Causa raiz:** na fatia B27 (S10, merge 10/09), o rejection sampling
  computava `range = (floor((2^64-1)/bound) + 1) * bound` (= floor(2^64/bound)
  * bound). O `+1` faz o produto **sempre** > 2^64 para bound>1 → wraps em
  64 bits: bound=1000 → range=384; bound=2 → range=0 (`bgeu t0, s1` rejeita
  tudo = loop). A referência x86 (`kof_sec_random_int`, RuntimeSecurity11)
  usa `(0xffffffff/bound)*bound` em 32 bits — a fórmula correta é
  `q*bound` sem o `+1` (q*b ≤ 2^64-1 sempre).
- **Por que nunca foi pego:** B27 entrou no merge 10/09 em ambiente SEM
  qemu/toolchain → `randomIntCrossArch`/`randomShapeCrossArch` passaram
  SKIPPED pelo guard honesto (4408eb6). Com qemu presente (11/09, prova do
  MATH001/TIME002 na mesma cadeia de fatias), o skip virou EXECUÇÃO — e o
  loop apareceu no primeiro teste com bound>1. A lição do DOING de 08/09
  ("suíte sem flag/ambiente != suíte rodando") confirmada em mais um item.
- **✅ CORRIGIDO 11/09:** B27 `.Lrnd_i_*`: `divu t1,-1,bound; mul s1,t1,bound`
  (sem `addi +1`) — range = q*bound ≤ 2^64-1 sempre; rejeição x<range +
  remu uniforme (mesma distribuição do contrato: uniforme [0,bound)).
  bound=1: q=2^64-1, range=2^64-1 (rejeita só x=2^64-1 — desprezível,
  remu=0 trivial).
- **Prova:** `KofRandomTest` 12/12 (incl. os 2 cross-arch com os 500 loops
  de bound=1000/2 + asserts de borda 1/0/-5/1e6) sob qemu-riscv64 +
  qemu-aarch64; probe isolado OK nos 2 alvos.
- **Arquivos:** `NativeRiscvAsmRtB27.java` (`kof_random_int`).

### 62. Constant pool: Float/Double armazenados como bits crus (parser de migração) — ✅ CORRIGIDO 08/09

- **Sintoma:** `kof inspect`/`kof decompile` de um `.class` com constante
  float (`3.5f`) exibiam/emitiam `1079574528` (os bits IEEE-754 como inteiro);
  `ldc 3.5` nunca recuperava o valor real. Sem crash — perda silenciosa de
  informação (R6).
- **Causa raiz:** `ClassFileParser` tratava tag 4 (Float) no mesmo ramo da
  tag 3 (Integer) com `getInt()`, e tag 6 (Double) no ramo da tag 5 (Long)
  com `getLong()` — sem `intBitsToFloat`/`longBitsToDouble`.
- **Correção:** tags separados (4 → `Float.intBitsToFloat(getInt())`,
  6 → `Double.longBitsToDouble(getLong())`). Para o recovery não driftar
  tipo (Kof não tem literal float inline; "3.5" tipa como Double → SEM010 no
  corpo de método Float), `BytecodeDecoder.ldc` recusa literais float → o
  corpo degrada p/ stub UNKNOWN honesto (igual a Double/Long via ldc2_w).
- **Prova:** `DecompileTest.floatConstantsDegradeNotDrift` (ldc int recupera
  `Int i() = 42`; `Float f()`/`Double d()` → stub, sem "= 3.5" vazando);
  DecompileTest 21/21; suíte 1226/0/64-skip.

### CANVAS001 — ClassFormatError com arc() (Double params) — JVM CORRIGIDO 06/09

- **Sintoma (original):** `Canvas(400,300)` + `c.arc(200,150,100,0.0,3.14)` compila, mas
  o JVM lança `ClassFormatError: Illegal class name "" in class file`.
- **Reprodução:**
  ```kof
  main() {
      var c = Canvas(400, 300)
      c.arc(200, 150, 100, 0.0, 3.14)
  }
  ```
- **Causa raiz (verificada 06/09 — diferente da hipótese original):** o
  construtor `Canvas` não era tipado no `MethodCallTyper` (lado driver) nem no
  `BuiltinCallTyper` (lado semântico) — o ramo genérico de construtores UI só
  cobria `isLayoutType || isStore`. `var c = Canvas(...)` era inferido UNKNOWN,
  o receiver não era reconhecido como UI-type no `ExpressionInstanceCallLowerer`,
  e a chamada caía no dispatch genérico de instância → owner `""` no
  Methodref → `ClassFormatError`. O `arc` só expunha o bug porque os widgets
  Int-only sem receiver tipado falhavam igual (qualquer método Canvas).
- **Correção JVM (06/09):**
  1. `MethodCallTyper`: ramo genérico de construtores UI passa a aceitar todo
     `KofUi.isUiType(ct)` (cobre Canvas/Image/Icon/Link/Font/Component sem
     branch explícito).
  2. `BuiltinCallTyper`: branch explícito `Canvas(Int,Int) → KofUi.CANVAS`
     (paridade com o lado driver).
  3. `JvmRuntimeCallDescriptors`: `kof_ui_canvas_set_line_width` estava
     agrupado com `move_to/line_to` como `(III)V` mas recebe
     `(canvas,width)` = `(II)V` → stack underflow → `COMP002 frame crash`
     quando `setLineWidth` era seguido de outro call.
  O descriptor de `arc` `(IIIIDD)V` já estava correto (receiver INT é
  prepended pelo caminho UI-call). Prova: `Main.class` agora emite
  `invokestatic KofRuntime.kof_ui_canvas_arc:(IIIIDD)V`; programa completo do
  `UiE2ETest.canvasCreation` roda limpo no JVM e no Native.
- **O que falta (metade JS do teste):** `canvasCreation` ainda falha em
  `assertNotNull(html)` — o canvas nunca é anexado ao `kof-root` nem dispara
  `kofUiSerializeHtml` (só `Window.show()` serializa; o plano
  `docs/future/PLAN-CANVAS-WIDGET.md` desenha Canvas montado dentro de uma
  `Window`, mas o teste não usa Window). Timing de serialização para widgets
  sem janela é decisão de design do autor do recurso (lane Canvas).
- **Arquivos:** `MethodCallTyper.java`, `BuiltinCallTyper.java`,
  `JvmRuntimeCallDescriptors.java` (corrigidos); `JsRuntimeUiWidgets.java`,
  `UiE2ETest.java` (pendentes, lane Canvas).
---

## Bug 79 — `await` de `Handle<Long>` como statement emite POP de 1 slot → VerifyError

- **Status:** CORRIGIDO (09/09, lane spec-gaps — descoberto pelos testes do modelo de memória SG-020)
- **Sintoma:** `var w = spawn escreveLong()` + `await w` (statement, valor descartado) → `java.lang.VerifyError: Bad type on operand stack ... long_2nd ... pop` no `main`.
- **Causa raiz:** `StatementLowerer.emitStatementInner` case `ExpressionStmt` emite `KofPop` incondicional para descartar o valor da expressão; Long/Double são categoria-2 (2 slots) e exigem POP2. `KofPop` virava POP (1 slot) → o 2º slot do long ficava na pilha → verificador rejeita.
- **Fix:** novo op IR `KofPop2` (POP2 JVM, `addq $16,%rsp` x86, `addi sp,sp,16` riscv); o statement escolhe `KofPop2` quando `TypeMetrics.isDoubleWidth(tipo)`. Interpretador trata `KofPop2` como pop; JvmLiteralEmitter conta depth−1 igual (modelo de 1 slot do emitter). (Repro: `KofConcurrency2Test.noWordTearingOnLong` antes do fix.)
- **Arquivos:** `KofPop2.java` (novo), `StatementLowerer.java`, `JvmOpEmitter.java`, `JvmLiteralEmitter.java`, `NativeMethodEmitter.java`, `NativeRiscvCrossEmit.java`, `KofInterpreter.java`.

---

### 91. Varredura KofPop width-blind — 2 sítios além do statement_expression ainda emitiam POP de 1 slot (VerifyError `long_2nd`) — ✅ CORRIGIDO 10/09 (merge main→beta-0.3.0; irmãos do `KofPop2` acima)

O caso canônico (statement-expression, `await w` de `Handle<Long>`) foi fechado
pelo `KofPop2` (linha acima, "Bug 79" da lane SG-020). A varredura dos demais
`new KofPop()` restantes achou 2 sítios com o MESMO furo width-blind, corrigidos
nesta merge (mesma técnica: `TypeMetrics.isDoubleWidth` → `KofPop2`):

- **`StatementLowerer.java` (corpo de atualização do `for`)** — descarta o valor
  da expressão do update (ex.: `for (...) { } ... random.double()` / método que
  devolve Long/Double chamado por efeito). Antes: `KofPop()` unconditional →
  `VerifyError: Bad type on operand stack ... long_2nd` no load. Prova: `for`
  com update double-wide compila e o bytecode traz `pop2`.
- **`ExpressionBinaryLowerer.java` (comparação `primitivo == null`)** — o caminho
  que valida um valor primitivo contra `null` empurra o valor e descarta. Antes
  do fix, o tipo descartado era tratado como 1-slot; agora usa `accType`/`rightType`
  do operando e emite `KofPop2` quando o valor é categoria-2 (Long/Double).
  Repro: `random.double() == null` compilava e rodava sem crash (antes: o
  mesmo `long_2nd`).
- **`ExpressionInstanceCallLowerer.java:51` (args de call em array)** — BENIGNO:
  só é alcançado depois de diagnóstico SEM025 (caminho de erro que retorna
  `INT 0`); o programa já falhou a compilação, o POP nunca roda em bytecode
  válido. Deixado como está.

**Regra travada:** todo descarte de valor de expressão usa o TIPO real
(`isDoubleWidth` → `KofPop2`), nunca `KofPop()` unconditional. Os demais
`new KofPop()` do repo estão em contexto de 1-slot (String/ref/prim de 32 bits,
int de índice) — verificados na varredura.
