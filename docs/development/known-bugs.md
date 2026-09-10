# Known Bugs — handoff para o próximo agente

> **Data:** 08/09/2026 · **Versão:** 0.3.1-beta. Este arquivo existe para que
> um agente (ou humano) pegue os bugs sem precisar redescobri-los. **Não são
> características** — são bugs reais com reprodução mínima.
>
> **Estado (varredura de 08/09, JVM + KofJS + interpretador):**
>
> | | |
> |---|---|
> | Abertos e atacáveis em JVM/JS | **5** — bugs 39, 45, 62, 63, 64 |
> | Abertos, só reproduzíveis no Native | **7** — bugs 43, 44, 46, 48, 50, 59, 61 |
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

### 39. `println(m.get("zz"))` (null de Map) → NPE/unbox errado nos 2 caminhos — ABERTO

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
- **Prova/repro:** caso `map-null-val` (sweep manual 06/09).

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

### 43. String no Native conta bytes UTF-8, JVM conta code units — ✅ CORRIGIDO (teste `NativeE2ETest.nativeStringLengthUtf16`) — decisão de design STR001: `kof_string_length` conta code units UTF-16 (paridade JVM/JS; `café`→4, `a😀b`→4)

- **Sintoma:** `var s = "café"; println(s.length); println(s.charAt(3))`: JVM → `4` / `233` (0xE9, code unit UTF-16 de `é`); **Native** → `5` / `195` (0xC3, 1º byte de `é` em UTF-8). `println(s + "!")` casa (`café!`) — só `length`/`charAt` divergem.
- **Causa raiz:** as ops de string do Native são **byte/UTF-8** baseadas; as do JVM são **code-unit/UTF-16** baseadas. Mesma família do `STR001` (documentado p/ JVM `"Olá 😀".length`=6), mas aqui é **divergência cross-target** (Native ≠ JVM no MESMO programa) → paridade (regra 5).
- **Prova/repro:** sweep cross-target 07/09 (caso `unicode-str`), Native x86_64.
- **Correção (lane Native, decisão de design regra 6):** alinhar `length`/`charAt` a UMA convenção (code point ou code unit) nos 3 targets — é mudança de  , precisa de bump.

### 44. `println(double)` no Native x86_64 imprime 6 casas + `5` (JVM: 16 casas + `5.0`) — ✅ CORRIGIDO (teste `ConformanceMatrixTest` `0.3333333333333333\n5.0\n3.5`)

- **Sintoma:** `println(1.0/3.0); println(2.5*2.0); println(7.0/2.0)`: JVM → `0.3333333333333333` / `5.0` / `3.5`; **Native** → `0.333333` / `5` / `3.5`.
- **Causa raiz:** o printer de double do Native (`RuntimePrintNum` / `kof_print_double`) formata com **6 casas** decimais e **sem `.0`** para inteiro-valido. Contradiz `docs/backend-parity.md:89` ("x86_64/JVM/JS impecáveis" para FP→string).
- **ABERTO (lane Native).** (Nota: a linha "Corrigido 07/09" que estava aqui era copy-paste errado do bug 43 — `kof_string_length` não tem relação com print de double.)
- **Prova/repro:** sweep cross-target 07/09 (caso `float-print`), Native x86_64.
- **Nota:** a parte `5` vs `5.0` é da mesma família do formato documentado em "parecem bugs mas são esperados" (`JS println(2.0)→"2"`); a parte **6 casas** (`0.333333`) é nova e contradiz o doc.

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

### 69. KofJS: if heterogêneo → `expression stack underflow` (COMP002) — ABERTO (pré-existente, causa no backend JS)

- **Sintoma:** o MESMO programa da issue #57 (`println(if (s == "") 1 else "s")`)
  no target JS: `Internal compiler error: KofJS: expression stack underflow`
  (COMP002), em vez de JS válido.
- **Prova de pré-existência (09/09):** revertido o fix JVM da lane (stash dos
  8 arquivos do §68, teste mantido) → o JS falha IDÊNTICO; o backend JS ignora
  `kof_box` (no-op), logo o underflow vem do tratamento de if-expr do próprio
  backend JS, não do box. Casos excluídos com `Set.of("js")` até o dono do JS
  corrigir.

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

### 78. Native: `transaction` aninhado comita o escopo externo (irmão asm do §77) — ABERTO (lane Native)

- **Sintoma:** MESMO programa do §77 em target Native (x86_64, sqlite): o
  bloco `transaction` interno comita (COMMIT no handle) enquanto o externo
  ainda está em transação; rollback do externo não desfaz as linhas
  confirmadas pelo interno. Paridade quebrada JVM vs Native (regra 5).
- **Causa:** `runtime/RuntimeDb4.kof_db_transaction` (asm) faz
  BEGIN/COMMIT/ROLLBACK pelo handle SEM flag de transação ativa — não há
  equivalente do `ThreadLocal KOF_DB_TX` JVM; cada bloco aninhado repete
  BEGIN (que no sqlite é no-op dentro de tx, mas o COMMIT interno efetiva).
- **Correção esperada (lane Native):** espelhar a semântica JVM fixada em
  `JvmConfigRuntime.kof_db_transaction` (`nested = mesma conexão/handle →
  não comita, não rollbacka, não re-BEGIN; erro propaga p/ o externo
  decidir`) — flag de transação ativa por handle no asm (x86_64 primeiro,
  riscv/aarch64 quando a área db existir lá). Sem savepoints (decisão da
  mantenedora, §77).
- **Prova de repro:** o mesmo programa KofDbE2ETest da issue #65 rodando
  no binário x86_64 (`caught {"n":2}` esperado antes do fix). Lane issues
  (09/09) NÃO implementou — asm fora da lane; registrado p/ o dono Native
  com a semântica alvo já definida no §77.














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

### 79. Native: `String.toInt/toLong` divergem do contrato JVM em entrada inválida — R6 silencioso nos 3 nativos — ABERTO (achado 10/09, varredura da lane STDLIB)

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

### 82. Native: `String.toDouble/toFloat` fora do contrato JVM — parser x86 silencioso-e-errado; riscv/aarch nem definem os símbolos — PARCIALMENTE CORRIGIDO 10/09 (irmão FP do bug 79; mesma família, varredura STDLIB)

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
- **Correção face x86 FEITA 10/09 (parcial, ver limite abaixo):** parser
  reescrito no contrato: trim, `+/-` inicial, dígitos-a-dígitos, um único
  `.`, expoente opcional só com dígitos, literais `NaN/Infinity/-Infinity`
  (case-sensitive, idem JDK; NaN via comparação-consigo-mesma que força qNaN
  real em SSE), falha → `kof_throw_string`. Mantissa acumulada em int64 +
  UMA divisão por 10^ndigitos (evita o erro acumulado do parser antigo:
  `"0.3".toDouble() == 0.3` agora true como no JDK).
- **LIMITE travado (documentado, não corrigível em asm puro):** paridade
  bit-exata p/ decimais que não cabem em int64 e expoentes >22 exige o
  algoritmo correcto do JDK (big-int shortest-round-trip) → fica na família
  FLT001/paridade-FP; vetores curtos (≤15 dígitos, exp |e|≤22) batem.
- **PENDENTE (face cross):** riscv/aarch precisam de `to_double/to_float`
  reais — depende de FP RV64 (`fadd.d`/`fmul.d`/`fcvt.*`) no conjunto do
  tradutor aarch (parcialmente presente: `NativeRiscvCrossOps` usa fcvt).
  Não é link-fail silencioso (COMP001 honesto), mas programa válido não
  linka. Registrado p/ lane NATIVE002/FLT001 com este §82 como spec.
  Menor repro: `main() { println("2.5".toDouble()) }` em riscv → COMP001
  undefined reference; em x86 pré-fix: `"abc".toDouble()` → 5451.

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
