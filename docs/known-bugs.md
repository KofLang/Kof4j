# Known Bugs — handoff para o próximo agente

> **Data:** 02/09/2026 · **Status:** documentados e verificados no compilador
> (0.2.6-beta). Este arquivo existe para que um agente (ou humano) pegue os
> bugs sem precisar redescobri-los. **Não são características** — são bugs
> reais com reprodução mínima.
>
> **Como pegar:** reproduza o snippet (`kof run --target=jvm`), fix no CÓDIGO
> (não no corpus), adicione teste E2E que falha antes/passa depois, atualize
> este arquivo (mova para "resolvidos" com o commit) e remova as notas do
> corpus.

---

## JVM / Native / JS — bugs por alvo

### 1. `throw <valor não-String>` gera bytecode inválido no JVM

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

### 2. Compound assignment `-=`, `/=`, `%=` produzem resultado ERRADO (JVM e Native)

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

### 3. `s += "x"` (compound de String) dentro de loop CRASHA o compilador

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

### 4. `switch` com String gera bytecode inválido no JVM

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

### 5. Cast de ponto flutuante → inteiro gera bytecode inválido

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

### 6. Sufixo numérico MAIÚSCULO gera bytecode inválido

- **Sintoma:** `42L` / `1.5F` compilam mas falham no load. Minúsculo
  (`42l`, `1.5f`) funciona.
- **Reprodução:**
  ```kof
  main() {
      var x = 42L    // ClassFormatError
      var y = 1.5F   // idem
  }
  ```
- **Causa provável:** o lexer/lowering trata o sufixo maiúsculo como
  identificador/errado. Deveria ser alias do minúsculo (ou rejeitar com
  diagnostic claro).
- **Arquivos:** `Lexer.java`, `Parser.java` (literais numéricos), `CompilerDriver.java`.

---

### 7. Argumento de tipo nullable em chamada genérica não parseia

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

### 8. Tipo de função em argumento genérico não parseia

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
EXTERNA produz lixo

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

### 10. `!` (NOT lógico) como VALOR de expressão sempre retorna `true`

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

### 11. `==` em records usa igualdade de REFERÊNCIA no JVM (não `equals`)

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

### 12. Assignment encadeado (`var c = a = b`) crasha o compilador

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

### 13. Cast (`x as T`) usado como operando de aritmética crasha o compilador

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

### 14. `Map.size` (propriedade) → `NoSuchFieldError` confuso em runtime

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

### 15. Primitivo não é atribuível a `Object` (sem auto-boxing)

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

### 16. `List.toArray()` quebra em JVM e Native (retorno de array)

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

### 17. Array `.get()`/`.set()` (não existem) são aceitos e geram saída quebrada

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

### 18. kof-ui: ID de widget é reutilizado após `remove()` → colisão de nós

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

### 19. Lambda retornando lambda → bytecode inválido (JVM) / COMP001 (Native)

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

### 20. Lambda armazenado em coleção e INVOCADO quebra (JVM/Native)

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

### 21. Nomenclatura: `PKG005` rejeita mesmo nome simples em pacotes DIFERENTES

- **Sintoma:** `package pkgA; class Data` + `package pkgB; class Data` →
  `duplicate type name 'Data' in packages 'pkgA' and 'pkgB' [PKG005]`. Em
  Java/JVM isso é perfeitamente legal (nomes fully-qualified distintos).

- **Corrigido 03/09**: o compilador agora aceita nomes com o mesmo nome
  simples em pacotes diferentes. Só rejeita nomes duplicados no MESMO
  pacote. O nome interno (FQ) é usado para todas as referências.
  
- **Arquivos:** `CompilerDriver.java` (PKG005) --- REMOVIDO.

---

### 22. Native: chamada de CONSTRUTOR de classe de outro pacote → undefined reference

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

### 23. ExternalClasspath: cadeia de superclasses só resolve DENTRO dos entries

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

### 26. Valor VOID usado como valor (println(f()) / `var x = f()`) → segfault/VerifyError

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

---

### 27. Paridade: `String.valueOf(char)` diverge entre JS e JVM/Native

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
  (`"h"` ou `"104"`) é **de design** (semântica congelada — regra 6): o corpus
  (`common-mistakes.md`) favorece `"h"`, mas isso precisa de bump + discussão,
  não de correção silenciosa. Registrado como gap até lá.
- **Arquivos:** `JsBackend.java` (dispatch de `valueOf`), `CompilerDriver.java`
  (lowering nativo char→Int para println).
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

### 30. Native x86_64: `json.decode<Bool>("false")` dava `true` (corrigido)

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

### 35. `listOf().contains(1)` → VerifyError no compilado (int não boxea) — ABERTO

- **Sintoma:** `var l = listOf(); l.contains(1)` compila; no caminho
  **compilado** o JVM rejeita o bytecode: `VerifyError: Bad type on operand
  stack — Type integer is not assignable to 'java/lang/Object'` no
  `invokevirtual ArrayList.contains`. O interpretador funciona (devolve
  `false`).
- **Causa raiz (diagnosticada 06/09):** `JvmOpCollections.emitListCall`
  (ramo `kof_list_contains`) não emite `Integer.valueOf` para argumento
  primitivo — empilha `int` direto onde `Object` é esperado.
- **Esperado:** boxing do argumento (paridade com o interpretador: `false`).
- **Prova/repro:** `KofScriptTest.interpreterCorrectWhereCompiledCrashes`
  (caso `empty-list`) trava a saída correta do interpretador.
- **Lane:** lowering/emit JVM (outro agente). Registrado pela varredura de
  paridade do interpretador 06/09.

### 36. `null == null` baixa `if_icmpeq` → VerifyError no compilado — ABERTO

- **Sintoma:** `var a = null; var b = null; println(a == b)` compila; no
  caminho **compilado**: `VerifyError: Type null is not assignable to
  integer` (o `==` baixou comparação de inteiros). Interpretador: `true/false`
  corretos.
- **Causa raiz:** o typer do `==` não classifica locals inicializados com
  `null` literal como referência → `KofConditionalJump` sai com
  `operandType` primitivo e o emitter escolhe `if_icmpeq`.
- **Esperado:** `if_acmpeq` (referências).
- **Prova/repro:** caso `null-eq` no mesmo teste.

### 37. `case Int n` em switch de primitivo → `KofInstanceOf[PrimitiveType]` — ABERTO

- **Sintoma:** `switch (o) { case Int n -> ... }` com `o: Int`: compilado →
  `VerifyError: Type integer is not assignable to 'java/lang/Object'`
  (`instanceof` sobre primitivo é ilegal no JVM); interpretador → sempre
  `false` (cai no `default`, imprime "other" em vez de "int:42").
- **Causa raiz:** `SwitchExprLowerer` emite `KofInstanceOf` para todo
  `case Tipo x:` sem boxar o scrutinee primitivo nem mapear `Int/Bool/...`
  para um teste de tipo válido.
- **Esperado:** boxar o scrutinee (ou teste direto de tipo estático) — o
  caso deve casar. **Ambos os caminhos errados** (interpretador silencia).
- **Prova/repro:** caso `pattern-match` (sweep manual 06/09; não travado no
  teste porque o interpretador também está errado — aguarda decisão de
  lowering na lane do outro agente).

### 38. Re-throw em catch de try aninhado → handler externo lê slot errado — ABERTO

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

### 39. `println(m.get("zz"))` (null de Map) → NPE/unbox errado nos 2 caminhos — ABERTO

- **Sintoma:** `var m = mapOf("a", 1); println(m.get("zz"))`: compilado →
  `NullPointerException` (escolheu overload `println(int)` e deu unbox de
  null); interpretador → `NoSuchMethodError: Integer.valueOf/1` (escolheu
  `valueOf(int)` para um null). Esperado: imprimir `null`.
- **Causa raiz:** seleção de overload de `println` sobre `V?` (nullable de
  genérico de coleção) resolve para o ramo primitivo.
- **Prova/repro:** caso `map-null-val` (sweep manual 06/09).

### 40. `n += 1` em campo de instância → crash nos 2 caminhos — ABERTO

- **Sintoma:** `class Box { Int n; Int inc() { n += 1; return n } }` →
  interpretador: `NoSuchElementException` (pilha vazia); compilado:
  "frame crash em Box.inc: Index -1 out of bounds for length 0". Verificado
  **pré-existente** (falha igual sem o fix de static-field de 06/09).
- **Causa raiz (parcial):** `ExpressionAssignmentLowerer` ramo não-estático
  faz `LoadLocal(0)` (this) mesmo em método sem receiver mapeado — o mesmo
  padrão do bug 35-38 (this/local desalinhado em método com `owner` mas
  lowering de `this` inconsistente).
- **Prova/repro:** probe manual 06/09 (caso `inst`).

---

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

- `42l`/`1.5f` minúsculos funcionam (os maiúsculos são o Bug 6).
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