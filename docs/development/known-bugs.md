# Known Bugs — handoff para o próximo agente

> **Data:** 10/09/2026 · **Versão:** 0.3.1-beta. Este arquivo existe para que
> um agente (ou humano) pegue os bugs sem precisar redescobri-los. **Não são
> características** — são bugs reais com reprodução mínima.
>
> **Estado (varredura de 08/09, JVM + KofJS + interpretador):**
>
> | | |
> |---|---|
> | **Fila ABERTA (varredura 12/09 — todas as seções sem ✅ no próprio cabeçalho)** | **14** — §45 (finally+return: exige mudança de IR 4-backend + decisão DD-01, mantenedora), §65 (UI/Chrome — lane UI), §81 (KofJS Long>2^53 — decisão de precisão/contrato), §89 (n.toDouble()/toInt() cross — contrato de conversão), §94/§101 (congelados regra 6), §104b-ii (record-em-coleção + storage-box asm — **lane bugfixer**, unidade GRANDE), §106 (json.encode Map — decisão mantenedora), §107 🟡 (restam record/aninhado=`?` até §104b-ii + FP-cross=FLT001; face escalar ✅ 12/09 nos 3 nativos `f3b3821c`+B39), §117 (cancelled() colisão — design TLS, congelado), §127-JVM (cast p/ tipo-função → mudança de erasure no `JvmTypeMapper`, **infra de tipos**; workaround interface verificado), §129-TLS (congelado), §131 (sobrecarga de MÉTODO — semântica, mantenedora), §132 (OTP JS gate). **§125 ✅ CORRIGIDO 12/09** (decisão A: return Nullable(primitivo) apaga p/ default — 3 pontos no IR compartilhado, célula `nullableprint` 4/4 sem exclusão). **§139 ✅ CORRIGIDO 12/09** (mesma unidade: JS fold `f()==null` → COMP002 underflow; parser JS ganha o descarte mid-expression com preservação de side-effect). **§90 ✅ CORRIGIDO 12/09 (lane web, remoto).** **Conclusão honesta (12/09, atualiza a mesa do bugfixer `4d51defe`): nenhum item de código-puro-sem-decisão na lane restou nesta máquina** — os 14 abertos estão pendurados em decisão da mantenedora, congelamento regra-6, ou lane alheia (UI/bugfixer). O trabalho REAL sem-colisão-aceito da sessão é a **frente #97 tree-shaking** (S-1 ✅ `a3996600`; S-2→ em `docs/development/PLAN-TREE-SHAKING.md`). |
> | Antiga "varredura 08/09" (apócrifa — corrigida 12/09) | os "abertos" 39/62/63/64/46/48/50/59/61 estão ✅ CORRIGIDO nos próprios cabeçalhos (39/62/63/64 JVM/JS; 46/50/59 Native; 48/61 gap honesto JSN004/FFI001); contagem real na linha acima. |
> | Paridade interpretador × compilados (semântica `==` congelada — regra 6) | **1** — bug 94 (NaN/±0.0 `==` de Double no SCRIPT) |
> | Paridade backend-only (regra 5, atacável na lane Native) | **2** — §107 (println coleção → lixo; **face escalar ✅ CORRIGIDA 12/09 nos 3 targets nativos** — x86 `f3b3821c` + cross B39, golden JVM byte-idêntico; restam record/aninhado=`?` honesto até §104b-ii, FP-cross=FLT001; §107-JS 11/09), §104b-ii (equals de conteúdo p/ record + box de primitivo no storage asm; **face char ✅ FECHADA 11/09** — `mapgetprim` 4/4)
> | Operadores relacionais NaN cross (congelados — regra 6) | **1** — bug 101 (`<`/`<=`/`>=` com NaN: riscv IEEE vs x86/JVM quirk `dcmpg`) |
> | **Corrigidos na sessão de paridade absoluta 11/09** | **15** — bugs 96 (SEM052), 98 (SEM053), 100 (SEM051+fold), 44-residual, 102 (from-idx), 103 (SEM054), 104a (KofObj equals/hash/toString no interpretador), 104b-i (LINK_FAIL `Object.equals` herdado no Native), 104c (membership de record por conteúdo no JS — `kofValEq`), 107-JS (`kofFormat` no JS), 109 (CRASH JVM no guard do `map.get` primitivo), 110 (`-0.0` colapsado em `+0.0` no literal emitter JVM), 111 (trailing-empties no `split` Native/JS + sentinela `substring` 0→-1; ✅ cross riscv/aarch B36/B37 11/09 — FECHADO nos 5 targets), 112 (prev de `put`/`remove` p/ primitivo: VerifyError/NPE JVM + SIGSEGV Native por pilha desequilibrada + `set.add` do interpretador + **JS fechado na mesma unidade** — `?? default` + `KofPop` preserva side-effect embrulhado; 4/4 targets), **104b-ii FACE CHAR** (SIGSEGV/`a` no `println(char-em-coleção)`; 3 buracos: desembrulhar `Nullable(CHAR)` no print-lowering JVM-coerente `unboxDescriptor` (char→`Integer`, não `Character`/`charValue`) + repair de `as Char` no `SemExpressionTyper` — `mapgetprim` 4/4) — todos com prova na matrix/suíte |
> | **Corrigidos na prova cross-arch 11/09 (MATH001/TIME002/B33)** | **3** — bugs 101→registrado (relacional NaN, ABERTO regra 6), MATH001 (Double math B32), TIME002 (ISO add/diff B33), 105 (random.int loop — renumerado de 102, colidiu c/ §102 indexOf) |
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

### 43. String no Native conta bytes UTF-8, JVM conta code units — ✅ CORRIGIDO 5/5 faces nos 5 targets (10/09 x86 `length`/`charAt`/`substring`/`indexOf`/`lastIndexOf`; 11/09 cross B34/B35 riscv+aarch) — decisão de design STR001: convenção code units UTF-16 (paridade JVM/JS; `café`→4, `a😀b`→4)

- **Sintoma:** `var s = "café"; println(s.length); println(s.charAt(3))`: JVM → `4` / `233` (0xE9, code unit UTF-16 de `é`); **Native** → `5` / `195` (0xC3, 1º byte de `é` em UTF-8). `println(s + "!")` casa (`café!`) — só `length`/`charAt`/`substring` divergem.
- **Causa raiz:** as ops de string do Native são **byte/UTF-8** baseadas; as do JVM são **code-unit/UTF-16** baseadas. Mesma família do `STR001` (documentado p/ JVM `"Olá 😀".length`=6), mas aqui é **divergência cross-target** (Native ≠ JVM no MESMO programa) → paridade (regra 5).
- **Prova/repro:** sweep cross-target 07/09 (caso `unicode-str`), Native x86_64.
- **Correção (lane Native, decisão de design regra 6):** alinhar `length`/`charAt`/`substring` a UMA convenção (code point ou code unit) nos 3 targets — é mudança de contrato, precisa de bump.
- **✅ CORRIGIDO 10/09 (x86_64):** `kof_string_length` UTF-16 (08/09, `NativeE2ETest.nativeStringLengthUtf16`), `kof_string_char_at` (10/09, percorre o UTF-8 e devolve a **code unit UTF-16** da posição — 1/2/3 bytes → 1 unit, astral (4 bytes) → 2 surrogates; `café.charAt(3)`→233, `a😀b.charAt(1)`→55357) **E `kof_string_substring` (10/09)**: `RuntimeStringOps.emitStringSubstring` ganha um walk interno (`.Lkof_substr_walk`, rdi=str/esi=target → eax=byteOff, edx=units, ecx=1 se caiu no meio do par) que converte start/end de code units para byte offsets; a cópia passa a ser a fatia de bytes entre as duas fronteiras (par astral sempre inteiro). `café.substring(1)`→`afé`, `substring(3)`→`é`, `a😀b.substring(1,3)`→`😀`, `.substring(3)`→`b`. Prova: `NativeE2ETest.nativeStringSubstringUtf16` + `ConformanceMatrixTest.unicode`/`unicode-astral`/`unicode-substring` (native desbloqueado, 4 targets) + `BackendParityTest.unicode-str`. **E `kof_string_index_of`/`kof_string_last_index_of` (10/09, achado por varredura de paridade):** `RuntimeStringSearch` reusa o MESMO `.Lkof_substr_walk` para varrer o haystack por **code units** e casar a needle byte-a-byte na posição convertida (needle vazio → 0/len, needle>alvo → -1, corte de par pulado — needle well-formed nunca casa numa 2ª unit). `a😀b.indexOf("c")`: era 10 (byte) → 6 (unit) = JVM/Script. Prova: `NativeE2ETest.nativeStringIndexOfUtf16` + `ConformanceMatrixTest.unicode-indexof` (4 targets) + sweep de 22 vetores (ASCII+latin1+astral+bordas) batendo JVM==Native==Script.
- **⚠️ Sub-residual (corte de par astral ao meio em `substring`):** fronteira `end`/`start` que cai na **2ª unit de um par astral** (ex.: `a😀b.substring(0,2)`, `a😀b.substring(1,2)`) produziria um **surrogate solto** na string resultante. O Native ainda não casa com o JVM aqui: produz um **diagnóstico R6** (`substring cannot split an astral code point`), nunca um byte-cru errado. A paridade plena exige o **storage ser WTF-8** (permitir surrogates soltos) + `length`/`concat` aceitarem-no — mudança maior do layout interno de string, fora do escopo desta unidade (fronteiras bem-formadas cobrem o uso real; registro p/ a próxima iteração de storage). `charAt` já casa (devolve o code unit numérico, sem storage envolvido).
- **✅ Faces riscv64/aarch64 (1/3–3/3) CORRIGIDAS 11/09 (fatia B34):** `kof_string_length`/`kof_string_char_at`/`kof_string_substring` contam code units UTF-16 no riscv (port 1:1 dos refs x86; aarch via tradutor). Qemu presente no ambiente da sessão → PROVA REAL: `NativeStringUtf16CrossTest` 2/2 (golden JVM medido, 12 valores, riscv+aarch sob qemu; sabotagem do golden falhou nos 2 = não-skip) + json.decode-list-strings idêntico JVM/x86/riscv/aarch. `Mapset1` (json.decode string) passou a copiar bytes INLINE (não chama mais o substring UTF-16 — offsets do scanner são bytes; espelha o `.Lkof_jdd_copy` x86).
- **✅ Residual FECHADO 11/09 (fatia B35, `522e63e8`):** `kof_string_index_of`/`kof_string_last_index_of` no riscv contam e **devolvem** índice em code units UTF-16 (port 1:1 dos refs x86; reusa `.Lu9_walk` da B34), + variantes `_2` (`index_of2`/`last_index_of2`/`starts_with2`) com os clamps JDK do §102; CrossOps roteia por aridade como o x86. `café😀x`.indexOf("😀") → 4 (era 5). Prova: `NativeStringUtf16CrossTest.searchUtf16Cross` (16 vetores, golden JVM medido, riscv+aarch sob qemu; sabotagem → 2/2 FAIL = não-skip) + E2Es 30/30+30/30 + matrix 11/11 intactos (ASCII `lastIndexOf("na")`/`contains` dos testes não-regredem). `contains`/`startsWith` 1-arg ficam byte-based de propósito (bool de prefixo/substring em UTF-8 bem-formado == bool em units — só o VALOR de índice divergia). Sub-residual (corte de par astral ao meio em `substring`) = R6 diagnostic (`kof_panic`), paridade plena exige WTF-8 storage (§43).
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

### 90. `KofWebHardeningTest.sse_events_sent_counter_tracks_calls` é FLAKY sob carga da suíte completa (espera 3, obtém 2) — ✅ CORRIGIDO 12/09 (lane web)

- **Sintoma:** na suíte completa (`mvn test` 4 módulos, ~1281 testes rodando
  juntos) o caso `sse_events_sent_counter_tracks_calls` (KofWebHardeningTest
  linha ~395) falhava `expected: <3> but was: <2>` — o contador de eventos SSE
  enviados ficava 1 atrás no momento da asserção. Passava isolado.
- **Causa raiz:** assimetria em relação ao fix do Bug 28 (WebSocket). No
  `JvmRuntimeWebDispatch.java:218`, `kof_web_ws_send` já incrementava
  `WS_MESSAGES_SENT.incrementAndGet()` *antes* de despachar o texto para o
  socket, garantindo que um cliente que leia o frame e chame `/stats` veja o
  contador já incrementado. Em `JvmWebCoreRuntime.java` (`SseConnection.send` e
  `event`), o incremento `SSE_EVENTS_SENT.incrementAndGet()` estava sendo feito
  *após* `writeData(data)` (após o flush TCP). Sob contenção de CPU, o cliente
  lia o 3º evento, fechava o socket e consultava `/stats` antes de a thread do
  handler SSE voltar de `writeData` e executar o incremento.
- **✅ CORRIGIDO 12/09:**
  1. `JvmWebCoreRuntime.java`: `SseConnection.send` e `event` verificam
     `if (!open.get()) return;` e incrementam `SSE_EVENTS_SENT.incrementAndGet()`
     *antes* de emitir os frames para o socket (mesmo padrão do Bug 28).
  2. `KofWebHardeningTest.java`: `sse_events_sent_counter_tracks_calls` passa a
     utilizar o helper de polling `awaitStats(port, "3", 2000)` para garantir
     convergência determinística mesmo sob concorrência extrema de suíte.
- **Prova:** `KofWebHardeningTest` 6/6 verde + suíte completa web `KofWeb*Test`
  49/49 verde (`KofWebWsE2ETest`, `KofWebE2ETest`, `KofWebHardeningTest`,
  `KofWebTlsTest`, `KofWebSseE2ETest`, `KofWebNativeE2ETest`, `KofWebStreamE2ETest`).
  Check 500 linhas sem violações novas (`JvmWebCoreRuntime.java` 480 linhas,
  `KofWebHardeningTest.java` 463 linhas).


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

### 97. Native: `String.compareTo`/`String.hashCode` declarados no reference → `undefined reference` no link — ✅ x86_64 CORRIGIDO 10/09 + riscv/aarch 11/09 (B36, qemu; face JS residual)

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
  (`NativeRuntime` é x86-only; o cross tem suas fatias). **✅ CORRIGIDO 11/09
  (cross, fatia B36 `NativeRiscvAsmRtB36`)**: port 1:1 do algoritmo
  `.Lksu_next` (helper de cursor com pendência de surrogate no stack) →
  `String_equals`/`String_compareTo`/`String_hashCode` no riscv (aarch via
  tradutor); router cross roteia `equals/compareTo/hashCode` pelo bloco String
  (receiver + arg em a0/a1 — antes caíam no fallback genérico que só dava pop
  de a0). Bug pegado na prova: lead de 4-byte é `0xF0..0xF7` — testar
  `&0xF8==248` em vez de `240` fazia astral cair no raw (hashCode/hash de
  "a😀b" errado); e o round-trip da sentinela −1 pela pilha exige `sext.w` no
  aarch (o tradutor mapeia `lw`→`ldr w`, zero-extend, vs sign-extend riscv).
  Prova: `NativeStringCompareCrossTest` 2/2 (18 vetores JVM==x86==riscv==aarch
  byte-idênticos; sabotagem → 2/2 FAIL = não-skip) + substring 1-arg §111
  cross no MESMO teste (sentinela 0→−1, fix abaixo).
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
- **✅ Residual FECHADO 11/09 (riscv64/aarch64, fatia B35 `522e63e8`):** o
  roteamento cross (`NativeRiscvCrossOps`) passou a rotear `indexOf`/
  `lastIndexOf`/`startsWith` p/ os helpers `_2` por aridade (como o x86), e os
  4 helpers riscv (`kof_string_index_of`/`_of2`/`last_index_of`/`_of2`/
  `starts_with2`, arquivo novo `NativeRiscvAsmRtB35`) contam e devolvem índice
  em CODE UNITS UTF-16 com os clamps JDK — reusando `.Lu9_walk` (B34). Prova:
  `NativeStringUtf16CrossTest.searchUtf16Cross` (16 vetores, golden JVM medido,
  riscv+aarch sob qemu). Paridade absoluta JVM=JS=X86=ARM=RISC na família
  fechada nos 5 targets.
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

- **§104b-i ✅ CORRIGIDO 11/09 (Native):** (i) `Thing(5).equals(Thing(5))`
  (classe não-record, método `equals` NÃO declarado) → **LINK_FAIL** (`ld:
  undefined reference to Thing_equals` — `resolveCalleeName` mangla o dono mas
  o bare-metal não tem `java.lang.Object` herdado). Fix: síntese de
  **equals de identidade** (`this == other`, o mesmo contrato do
  `Object.equals` do JVM — oracle) em
  `CompilerRecordSupport.buildClassIdentityEqualsMethod`, ligado em
  `CompilerClassLowering.lowerClass` para os 3 targets Native quando a classe
  não declara `equals` e herda direto de Object. Prova: célula `classequals`
  (4 targets, `false|true|false|true`) — JS/Script/JVM já batiam.
- **§104b-ii ⏳ ABERTO (Native):** (ii) record em coleção:
  `kof_list_contains`/`kof_set_contains` comparam ponteiro (ou só String) —
  `setOf(p1).contains(p2)` = **false** vs JVM **true**; `println(listOf(p1))`
  imprime vazio (helper não conhece handle Kof p/ toString). Exige equals/
  hash genérico por vtable nos helpers asm (x86+riscv+aarch) — unidade
  própria, célula `objmethods` mantém Native excluído. Proibido: fallback
  silencioso.
  - **Face primitivo-em-coleção — ✅ CHAR FECHADO 11/09:** o storage da coleção
    asm guarda o valor **cru** (sem box). `println(l.get(i))` com char **SIGSEGV
    (exit=139)**: `kof_list_get` retorna `0x61` e o print dispatchava
    `kof_println_string` (objeto) sobre um codepoint → deref inválido. Prova
    (11/09): `mc2.kf` `println('a' as Char)` → `97` ok; `mc.kf`
    `println(listOf('a' as Char).get(0))` → exit=139; `mc3.kf` `l.get(0) == 'a'`
    → `true` ok. **Não era storage-box**: as outras faces primitivas (int/long/
    bool/double) já imprimiam corretamente da coleção; só o char tinha
    três buracos de DISPATCH de print, todos fechados:
    (1) **Native** `ExpressionPrintLowerer` mapeava `char→Int` (p/ o `valueOf`
    imprimir o codepoint, contrato congelado `strings.md` "72 (H)") só quando o
    tipo era `CHAR` CRU — `Nullable(CHAR)` (retorno do `map.get`) não casava e
    caía no ramo `char_to_string` do backend, que imprime o **caractere** (`a`)
    ou, com `Unknown`, nada → o raw `0x61` chegava a `kof_println_string`
    (SIGSEGV). Desembrulha o INNER agora.
    (2) **JVM** `unboxMethodName(char)→"charValue"` + descriptor
    `toDescriptor(CHAR)="C"` emitiam `Integer.charValue()C`/`Integer.intValue()C`
    — inexistentes: char é **guardado como `Integer`** (`boxedClassNameFor`
    default → `java/lang/Integer`; `emitBoxIfPrimitive` → `valueOf(I)`), a caixa
    nunca é `Character`. Unbox agora é `intValue` com `unboxDescriptor` coerente
    com a caixa (novo helper; os 4 sítios de unbox roteiam por ele).
    (3) **frontend** `x as Char` pinava `Unknown` como V do `mapOf`/elemento do
    `listOf` (não `char`): o cache do `SemanticAnalyzer`
    (`SemExpressionTyper`) não tinha o repair de cast que só o
    `ExpressionTyper:89` (lowering) fazia — e `MethodCallTyper` lê o cache, não
    o lowering. Mirror do repair no `SemExpressionTyper`.
    **Prova:** célula `mapgetprim` com a **exclusão Native REMOVIDA** → os 4
    targets byte-idênticos `true/true/8/9000000001/true/97/false`; varredura
    `int/long/bool/char/String` lidos de Map e List imprimem idênticos nos 3
    targets. O `(i)` storage-box genérico (record-em-coleção) e o
    dispatch por tag continuam ABERTOS (abaixo).
- **§104c ✅ CORRIGIDO 11/09 (JS):** record em `setOf`/`mapOf`/`listOf().contains`
  usava **identidade** (Map/HashSet JS nativos com objeto por referência):
  `listOf(p1).contains(p2)` = **false** vs JVM **true**; `setOf(p1).contains(p2)`
  = **false**; `mapOf(p1,7).get(p2)` = **null**. (A face `println(listOf(p1))`
  sem colchetes era outra coisa — fechada no **§107-JS** com `kofFormat`.)
  **Fix:** records no JS já ganham `.equals(other)` sintético por conteúdo
  (`JsClassEmitter.lowerRecordEquals`, bug 11). O runtime JS agora tem
  `kofValEq(a,b)`: primitivos/String via `===` (NaN=NaN), objeto Kof delega ao
  `.equals` sintético (1/0). Os helpers `kofListContains`/`kofSetAdd`/
  `kofSetContains`/`kofSetRemove`/`kofMapPut`/`kofMapGet`/`kofMapRemove`/
  `kofMapContains` iteram + `kofValEq` em vez de `includes`/`has`/`get`/`delete`
  (ordem de inserção preservada; primitivos/String seguem o caminho nativo).
  **Prova:** célula `objmethods` com a **exclusão JS removida** → os 4 targets
  (JVM/Script/JS; Native excluído = §104b-ii) byte-idênticos `true/true/7/
  [Point[x=1, y=2]]`. Sem wrapper de coleção/hashing: reuso do `.equals` do
  record = menor superfície.


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

### 106. `json.encode(Map)` quebra em 3 dos 5 alvos (JVM crasha; x86/riscv link error; só Script/JS ok) — ❌ ABERTO (decisão de superfície JSON = mantenedora, regra 6)

- **Menor repro (medido 11/09):**
  ```kof
  import kof.json.*
  main() { println(json.encode(mapOf("x", 1))) }
  ```
  - **JVM**: `InaccessibleObjectException: Unable to make field HashMap.table accessible` — `KofRuntime.kof_json_encode_object` reflete campos do objeto sobre um `HashMap` (Map não é objeto de campo; reflexão em java.base exige `--add-opens java.base/java.util=ALL-UNNAMED` que o runtime não pede nem aplica). CRASH em runtime.
  - **x86 e riscv64/aarch64**: o link falha — `JsonDispatch.java:31` cai no genérico `kof_json_encode` (só existe no interpretador/JVM; o nativo define apenas os tipados `int/long/bool/float/double/string/list/array`), e o `ld` não tem o símbolo → erro bruto `undefined reference to kof_json_encode` embrulhado em `COMP001 "Error reading source file"` (pior: diagnóstico misleading + R6: o gate honesto deveria ser compile-time, não link).
  - **Script (interpretador)**: funciona (`KofInterpreterRuntime.kof_json_encode` trata Map). **JS**: não medido no harness (classpath GraalJS); presumido ok pelo path de objeto nativo JS.
  - Cobertura de teste é o motivo de nunca ter aparecido: os E2E de encode cobrem Int/String/List/Array/record — NUNCA `Map`/`Map<String,*>`.
- **Causa raiz (2 camadas):** (a) dispatch: `JsonDispatch` não tem ramo `isMap` → genérico inexistente no nativo; (b) JVM: encode de objeto por reflexão não diferencia Map (deveria iterar entries, não `getDeclaredFields`). E (c) UX: failure de link não é diagnóstico R6.
- **Por que ABERTO (não corrijo silencioso):** formato de `encode(Map)` é SEMÂNTICA de superfície (ordem das chaves? insertion vs sorted? null values?) — é decisão da mantenedora (regra 6: JSON surface congelada 0.2.6-beta). A correção tem 3 partes: gate honesto no compile-time até a superfície decidir (diagnóstico `JSN00x` no estilo JSN004 no dispatch de Map em nativos) + decisão de formato + ramos JVM (entries) e nativo. Registra aqui; NÃO vira edição de semântica sem decisão.
- **Pista de teste faltante (para quem fechar):** `json.encode(mapOf(...))` nos 5 alvos com golden de ordem (provavelmente insertion-order = `LinkedHashMap` semantics, mas é a decisão).

### 108. `println(listOf(bool,...))` — interpretador (Script) imprime `[1, 0]` vs JVM `[true, false]` — ✅ CORRIGIDO (11/09, Script-only; storage boxing)

- **Menor repro (medido 11/09):** `println(listOf(true, false))` → JVM/JS
  `[true, false]`, **Script `[1, 0]`**. Descoberto junto do `collprint`
  (KofInterpreter usa `String(v)`/`valueOf` real e o ArrayList já imprime o
  objeto — mas o interpretador guarda Bool como `Integer 1/0`, perdendo o
  tipo na fronteira da coleção).
- **Causa raiz:** no backend compilado, `kof_list_add`/`set`/`contains`
  fazem `emitBoxIfPrimitive(elemType)` (JvmOpCollections:85,111,124) →
  `Boolean.valueOf` → o ArrayList guarda `Boolean` e `toString` dá `true`;
  `get` faz `emitUnboxIfPrimitive`. No `KofInterpreterCollections` o valor
  entra como `Integer 1/0` (o interpretador não distingue Bool de Int em
  storage) → o `toString` do ArrayList imprime `1`/`0`.
- **Correção (Script-only, espelhar o box do JVM, simétrico):** boxing na
  INCLUSÃO (`list add/set`, `set add`, `map put` — chave E valor) e unboxing
  na EXTRAÇÃO (`list get`, `set/map contains/remove` — senão o `==` de Bool
  `Integer 1 vs Boolean true` quebra) pelo tipo do elemento/argumento.
  Char NÃO precisa (JVM imprime `[97,98]` — já bate). Risco: o unboxing na
  extração é obrigatório e largo; exige suíte + KofInterpreterParityTest
  (novo caso bool-in-list) como gate. Unidade própria, não começada.
- **Arquivos:** `KofInterpreterCollections.java` (listOps/mapOps/setOps +
  helpers box/unbox por tipo), `KofInterpreterParityTest.java` (gate).
- **Correção aplicada (11/09, mesma lane do §112 — merged com ele):**
  `box/unbox` por tipo em `listOps`/`mapOps`/`setOps`/`channelOps`
  (inclusão box, extração unbox; `contains` de list usa o tipo do
  ARGUMENTO, espelhando o bug 35). Derivação de tipos espelha
  `JvmOpCollections` (typeArguments do ownerType + override por
  parameterTypes). O merge com o §112 mantém o `prevOrDefault` do
  `put`/`remove` (guard de prev null) E o boolean real do `set.add`, com
  unbox do prev antes do guard. Prova: célula `boolcoll`
  (`println(l)`/`println(m)`/`println(s)` + get/contains/set) FAIL sem o
  fix (`[1, 0]`/`{yes=1}`) e verde com (`[true, false]`/`{yes=true}` =
  JVM); `KofInterpreterParityTest` 19/19 + `ConformanceMatrixTest` 11/11
  (célula `mapmutret` do §112 intocada) + suíte completa 1292/0 (5 skip)
  + script/c-compiler/cli BUILD SUCCESS. Gate: `boolInCollectionsPrintsLikeJvm`.

### 107. `println(<coleção>)` no nativo imprime LIXO de ponteiro (JVM: `[1, 2, 3]`/`{k=9}`) — 🟡 PARCIAL: face escalar x86+riscv/aarch CORRIGIDA 12/09; só record/aninhado fica `?` (até §104b-ii)

- **Menor repro (medido 11/09, pós-fix §104b-i que liberou o link):**
  ```kof
  main() { println(listOf(1, 2, 3)); println(setOf(1, 2)); println(mapOf("k", 9)) }
  ```
  - **JVM (oracle)**: `[1, 2, 3]` / `[1, 2]` / `{k=9}` (medido — od -c).
  - **Script**: idêntico ao JVM (interpretador imprime `toString` real).
  - **x86_64**: bytes-lixo (`\300\224` / `\240` / \`\`` = ponteiro do objeto reinterpretado
    como KofString) + `\n`. **riscv64/aarch64**: idem (ponteiro via
    `kof_println_string`). Exit 0 (silencioso — R6 violada: nunca deveria
    imprimir lixo).
  - **JS**: ✅ CORRIGIDO (§107-JS, 11/09) — `kofFormat` espelha `ArrayList/HashMap/HashSet.toString`
    (era join sem colchetes). A face JS deste bug está fechada; o que restava
    (membership de record por conteúdo) é §104c, também fechado 11/09.
- **Causa raiz (x86_64 + riscv/aarch, 1 caminho):** `ExpressionPrintLowerer`
  baixa `println(obj)` como `valueOf(arg)` STATIC → `NativeX86Calls.java:180`
  trata `dispatchType instanceof ClassType && !String` procurando `toString`
  na **vtable** (`findVirtualMethodIndex(ct.name(), "toString")`). Record tem
  `toString` na vtable (funciona — bug 42/recordhash). **List/Map/Set NÃO
  têm vtable `toString`** (são tipos de coleção do runtime, não classes Kof)
  → `tosIdx < 0` → o ramo **não emite NADA** → o ponteiro cru fica na pilha
  e cai em `kof_println_string` = lixo. Não há `kof_list_to_string`/
  `kof_set_to_string`/`kof_map_to_string` no runtime nativo (grep = 0).
- **Correção (backend-only, minha lane Native — NÃO toca o §104b dos
  records):** emitir `toString` de coleção em asm (B34): `kof_list_to_string`
  (percorre `24(base)`, elementos `8` bytes, tag no header — reusar
  `kof_int_to_string`/`kof_long_to_string`/`kof_double_to_string`/
  `kof_bool_to_string`/`kof_string_*` conforme tag; join `", "`; wrapper
  `[`/`]`), `kof_set_to_string` (idem wrapper, ordem de inserção),
  `kof_map_to_string` (wrapper `{`/`}`, `chave=valor`, iteração `0..`
  bucket). Routing no dispatch valueOf: quando `dispatchType` é
  List/Map/Set (predicado `isList/isMap/isSet` — já existe em `KofType`?) →
  `call kof_<coll>_to_string` em vez de vtable. AArch herda via tradutor.
  **Oracle = formato JVM exato** (medir, não adivinhar — pode ser `[1, 2]`
  com vírgula+espaço; confirmar `Map` ordem). Elemento record dentro usa o
  MESMO `toString` (recursão via dispatch) — MAS só depois do §104b fechado
  (senão aninha lixo); por ora `println(listOf(int/String))` já conserta a
  maioria (record-em-list fica pro §104b-ii).
- **Escopo honesto do registro:** NÃO implementei ainda nesta passada —
  parei pra registrar (regra: unidade coesa c/ prova). Face record-em-coleção
  recursivo depende do §104b-ii (equals/toString de conteúdo — lane
  maintenedor, EM CURSO, não tocar).
- **✅ Face ESCALAR CORRIGIDA 12/09 (x86 runtime asm + dispatch; cross B39):**
  novo `RuntimeCollectionToString` (registrado em `NativeRuntime` logo após
  `RuntimeList`) emite `kof_list_to_string`/`kof_set_to_string`/
  `kof_map_to_string` + `kof_elem_to_string`. O dispatch `valueOf` em
  `NativeX86Calls` (ramos `isList/isMap/isSet`) passou a chamar esses helpers
  passando, **em tempo de compilação**, a TAG do elemento
  (`collectionTag`: 0=int/char/short/byte, 1=String, 2=Long, 3=Bool,
  4=Double, 5=Float, 6=desconhecido/record/aninhado). Nenhum mutador ou
  header de container é tocado (lição §104b-ii). O acumulador e as Strings
  temporárias vivem **ancoradas em `%rbp`** (os `pushq` dos `call` caem
  ABAIXO dos locais; `rsp`-relativo foi o primeiro bug — o retorno do
  `call` pisava o slot do acumulador, SIGSEGV).
  **Cross (riscv64 + aarch64):** fatia nova `NativeRiscvAsmRtB39`
  (0 colisões .L/.globl) com os MESMOS helpers e a MESMA semântica de tag;
  o dispatcher `NativeRiscvCrossOps` (ramos `valueOf` List/Map/Set) levanta
  **FLT001 em tempo de compilação** para coleção de Double/Float (mesma
  recusa do valueOf escalar cross — R6/R7: diagnóstico, nunca `?` silencioso
  nem lixo). Disciplina: **todo estado do laço vive em SLOT do frame**
  (registrador nenhum sobrevive aos `call` — cada helper do runtime riscv
  salva um SUBCONJUNTO INCONSISTENTE dos s-regs: `from_literal` preserva
  s0/s1/s3, `int_to_string` preserva s0/s1/s3/s4/s5, `bool_to_string` só
  ra — e os t-regs são todos free-for-all; bug capturado no qemu: ra clobber
  no `kof_elem_to_string` sem save → hang). Long: `kof_long_to_string`
  riscv é `j kof_int_to_string` e o `div` riscv é 64-bit → 100000000000L
  CORRETO no cross (idêntico x86 `divq`/JVM). aarch64 herda 100% via
  tradutor (todos os mnemônicos novos cobertos; diretivas passam verbatim).
  Provas: `NativeE2ETest#execCollectionPrintMatchesJvmGolden` +
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest#nativeCollectionPrintMatchesJvmGolden`
  (golden = oracle JVM MEDIDO, byte-idêntico nos 3 targets; sabotagem do
  separador → FAIL); `nativeCollectionPrintFloatDoubleRefusedHonest` (riscv
  + aarch) prova que a recusa FLT001 de coleção FP acontece em COMPILAÇÃO
  com a mensagem FLT001.
- **Faces que FICAM ABERTAS neste bug (paridade parcial, diagnosticada R6):**
  - **`?` honesto (não lixo):** elemento **record** ou **coleção aninhada**
    (tag 6) imprime `?` nos 3 targets nativos — é a recusa visível, nunca
    lixo de ponteiro. Fecha junto com o §104b-ii (equals/toString de
    conteúdo) + propagação de tag recursiva na emissão (dispatch-time hoje
    só conhece o tipo estático do elem; record/nested precisa vtable
    `toString` + sub-tag).
  - **Double/Float no cross:** compilam FLT001 (recusa honesta); só o x86
    tem FP (RuntimeStringConv tem kof_double_to_string x86).
  - **Map/Set multi-entry:** ordem de ARMAZENAMENTO (inserção) nos nativos
    vs hash-order do `HashMap`/`HashSet` do JVM — divergência de
    arquitetura, single-entry é idêntico. Teste multi-entry fica fora do
    golden nativo.

- **✅ Face JS CORRIGIDA 11/09 (mesma raiz — formato de contêiner ausente):**
  o JS dava `1,2` (Array.toString sem colchetes) e `[object Map]`/`[object
  Set]`. Fix: `kofFormat` em `JsRuntimeCore` espelhando
  `ArrayList/HashMap/HashSet.toString` (`[a, b]` com `", "`, `{k=v}`,
  recursivo p/ aninhados, Map/Set nativos JS; escalares passam por
  `String(x)` sem mudança — não toca bug 44) + roteamento por TIPO no
  `valueOf` (`JsCallEmitter`; o `ExpressionPrintLowerer` passa o tipo real do
  arg no JS como já fazia no Native). Célula `collprint` (JVM+Script+JS
  byte-idênticos; **Native excluído = o lixo de ponteiro deste bug**). A face
  Native (esta seção) segue ABERTA — é a mesma infraestrutura de dispatch que
  o §104b-ii precisa (equals/toString de conteúdo) para o caso record-em-lista.
- **Nota de teste faltante:** nenhum E2E nativo faz `println(coleçãoInteira)`
  (só `lista.get`/`.size` nos tests/learn) → por isso nunca apareceu.

### 109. `mapOf(k, <primitivo>).get(k)` → **CRASH no JVM** (`NoSuchMethodError: Boolean.intValue()Z`) — ✅ CORRIGIDO 11/09 (ramo primitivo no `unboxMethodName`)
- **Menor repro:** `main() { println(mapOf("t", true).get("t")) }` → JVM:
  `Exception in thread "main" java.lang.NoSuchMethodError: 'boolean
  java.lang.Boolean.intValue()'` (crash em runtime, não compile-time).
  Mesmo caminho para `listOf(true).get(0)` e `setOf(7).contains(...)` que
  passem pelo guard de `Nullable(primitivo)`.
- **Causa raiz:** o guard do `kof_map_get` (e afins) desempacota o valor com
  `JvmOpCollections.unboxMethodName(tipoInterno)` passando o tipo **primitivo**
  interno do `Nullable(Bool)` (ex.: `PrimitiveType[bool]`). `unboxMethodName`
  só tinha ramo para `Type.ClassType` (`Boolean`→`booleanValue`); primitivo
  caía no default `"intValue"` → emitia `checkcast Boolean; invokevirtual
  Boolean.intValue()Z` → link error em runtime.
- **Fix (mesma tabela de `boxedClassNameFor`):** `unboxMethodName` trata agora
  `Type.PrimitiveType`: `bool→booleanValue`, `char→charValue`, `byte→byteValue`,
  `short→shortValue`, `int→intValue`, `long→longValue`, `float→floatValue`,
  `double→doubleValue` — default `intValue` só permanece para tipos
  genuinamente desconhecidos.
- **Prova:** célula de matriz `mapgetprim` — desde 11/09 **sem exclusões**
  (4/4 targets byte-idênticos; a face char do §104b-ii foi fechada na mesma
  data, ver §104b-ii); esperado
  `true\ntrue\n8\n9000000001\ntrue\n97\nfalse` cobre Bool/Int/Long/Double/Char/miss
  pelo mesmo caminho de guard.
- **Faces PRÉ-EXISTENTES descobertas pela célula (não são do §109):**
  - **Native:** `println(l.get(i))` com char-em-coleção → **SIGSEGV (exit=139)**
    — ✅ **FECHADO 11/09** como face char do **§104b-ii** (não era storage-box:
    eram 3 buracos de dispatch/digitação do char, detalhados lá; a célula
    `mapgetprim` não exclui mais Native).
  - **JS:** `println(d * 2)` para `Double` imprime `5` vs `5.0` do JVM — é o
    **floatprint (bug 44)** já registrado (`String(5.0)==="5"`), não regressão.
    A célula usa predicado (`d > 1.0`) para exercitar storage Double sem
    colidir com o §44.
- **Nota de teste faltante:** nenhum teste cobria `println(map.get(k))` com
  valor primitivo não-Int/Double — Bool e Char eram os gatilhos do default
  silencioso do `unboxMethodName`.
- **Nota (face JVM do char, corrigida junto com §104b-ii 11/09):** o próprio
  ramo primitivo que FECHOU o §109 trazia um segundo crash latente para Char:
  `char→charValue` + descriptor `toDescriptor(CHAR)="C"` emitiam
  `Integer.charValue()C`, inexistente — char é GUARDADO como `Integer`
  (`boxedClassNameFor` default), a caixa nunca é `Character`. `mapOf("c",'a')
  .get("c")` crashava no JVM com `NoSuchMethodError` até o fix de coerência
  caixa↔unbox (novo `unboxDescriptor`, `char→intValue/()I`).

### 112. `println(m.put(k,v))` → **VerifyError**; `println(m.remove(chave-ausente))` → **NPE** (JVM) / **SIGSEGV** (Native); `s.add(já-existente)` → `true` no interpretador — ✅ CORRIGIDO 11/09 (3 targets; JS registrado como face restante)
- **Menor repro (JVM):** `var m = mapOf("a",1); println(m.put("a",2))` →
  `java.lang.VerifyError: Bad type on operand stack ... Type 'java/lang/Object'
  (stack[1]) is not assignable to integer` (crash no verifier, NÃO output
  errado). `println(m.remove("zz"))` → `NullPointerException: Cannot invoke
  Integer.intValue()` (NPE não é exceção-as-String do contrato Kof).
- **Menor repro (Native x86):** `var m = mapOf("a",1); println(m.remove("zz"))`
  → **SIGSEGV (exit=139)**.
- **Menor repro (interpretador/Script):** `var s = setOf(1,2); println(s.add(1))`
  → **`true`** (devia `false` — já continha 1); `println(m.remove("zz"))` → exit=1
  com `java.lang.Integer.valueOf/1` no stderr (NPE simétrico do JVM).
- **Causas raiz (três distintas, mesma família "retorno primitivo de put/remove/
  add"):**
  1. **JVM `kof_map_put`/`kof_map_remove` (`JvmOpCollections`):** `HashMap.put`/
     `remove` devolvem `Object` (o valor anterior, que pode ser null). O typer
     (`CollectionMethodTyper`/`MemberCallTyper`) declara o retorno como `V` —
     quando `V` é primitivo, o `Object` entrava direto em uso primitivo: no
     `put` o `println` emitia `String.valueOf(int)` sobre `Object` → **VerifyError**;
     no `remove` o unbox cru `checkcast Integer; intValue` sobre null → **NPE**.
     (O `get` já tinha o guard null→default do §87/§109; put/remove não.)
  2. **Native `kof_map_remove` rota de MISS (`RuntimeMap.java`):** desequilíbrio
     de pilha — a rotina faz **5 pushq** (rbx/r12/r13/r14/r15) mas o caminho
     `.LKMR_miss` fazia só **3 popq** → `ret` saltava para lixo na pilha →
     **SIGSEGV** em qualquer `m.remove(chave-ausente)`.
  3. **Interpretador (`KofInterpreterCollections`):** `set.add` fazia
     `s.add(x); s.contains(x)` → sempre `true`; e `map.put`/`remove` não
     guardavam o `prev` null para retorno primitivo (NPE/exit=1).
- **Correção:**
  - JVM: novo helper `emitPrevValueUnbox(mv, declared)` — `DUP; IFNONNULL; POP;
    default; …checkcast boxed; unboxMethodName` (guard null→default, **mesmo**
    caminho do guard do `get`, reaproveitando `unboxMethodName` corrigido no
    §109); ligado no `kof_map_put` (quando o retorno NÃO é void) e no
    `kof_map_remove` (ramo primitivo). Valor de referência: cast no declared.
  - Native: 5 pops simétricos na `.LKMR_miss`.
  - Interpretador: `set.add` usa o `boolean` real de `HashSet.add`; helper
    `prevOrDefault(prev, declared)` (null→`KofInterpreterMembers.defaultValue`)
    no `kof_map_put`/`kof_map_remove`, espelhando o guard do `get` (SG-008).
- **Prova:** célula `mapmutret` na matriz (JVM+Native+Script byte-idênticos
  `false/true/3/true/false/1/2/2/0/0` — cobre add existente/novo, size, remove
  hit/miss, put over existente, get, remove-miss com value Int, size final) +
  probes `mmr.kf`/`ad.kf`/`t[A-D].kf`.
- **✅ §112-JS CORRIGIDO 11/09 (face JS, na mesma unidade):** no JS o `put`/`remove` de
  prev AUSENTE imprimia `null` em vez do default do primitivo (`0`), porque o
  typer declara o retorno como `V` **não-nullable** (get é `V?`, e por isso o
  miss do get já é coerçado) e o emitter JS não aplicava default a `null` nesse
  caminho. Célula `mapmutret` hoje cobre JS (Set.of() vazio).
  **Fix (2 partes — o wrap sozinho NÃO bastava, ver armadilha abaixo):**
  `JsCollectionOps.handleMapOp` envolve put/remove primitivo com
  `?? defaultForType(V)` (padrão exato do `kof_poll`), E
  `JsExpressionStatementParser` estende o `KofPop` p/ preservar
  `JsBinary` cujo operando é `JsCall` (side-effect). Prova: célula
  `mapmutret` 4 targets idênticos + célula `map` (put-statement) verde.
  **Armadilha documentada (11/09, 1ª tentativa revertida — causou o wrap):**
  só envolver o call com `?? defaultForType(V)` (padrão exato do
  `kof_poll`/`JsRuntimeOps:339`) **quebrou a célula `map`**: ali `m.put("b",2)`
  é STATEMENT — o retorno é descartado por `KofPop` no parser de statement JS,
  que só sobrevivia a `JsCall`/`JsSequence`/`JsAwait`; a forma embrulhada
  `(call ?? 0)` era `JsBinary` e caiu no descarte silencioso → **o side-effect
  do put se PERDIA** (put não rodava; `map` deu `1\n1` em vez de `1\n2`).
  **Fix real:** o `KofPop` do `JsExpressionStatementParser` foi estendido p/
  preservar `JsBinary` com operando `JsCall` (expressão com chamada é sempre
  side-effecting em Kof — sem short-circuit de efeitos colaterais). Mesma
  vizinhança do bug 79 (KofPop width-blind).

### 113. Native: `new Int[a][b]` não aloca NADA (op IR sumido) → SIGSEGV em `m[0][0]`/`m.length` — ✅ CORRIGIDO 11/09 (x86 `97d54a60`; faces riscv/aarch ✅ fatia B37 + roteio cross, golden JVM sob qemu — 5/5 targets) [renumerado da fila pós-merge]

- **Menor repro (medido 11/09):** `main() { var m = new Int[2][2]; println(m.length) }`
  → JVM `2`, Script `2`, **Native exit=139 / saída vazia**. Idem
  `m[0][1] = 7; println(m[0][1])` (vazio/crash). `new Int[2]` (1 dim) funciona.
- **Causa raiz (parcial — o asm prova):** o lowering frontend EMITE
  `KofNewMultiArray(baseType, dims)` (ExpressionLowerer:234) para ≥2 dimensões —
  JVM (`JvmOpEmitter:220` MULTIANEWARRAY), interpretador
  (`KofInterpreter:308` `newMultiArray`) e JS (`JsExpressionParser:240`) tratam.
  **O backend Native não tem o caso**: `NativeMethodEmitter` casa
  `case KofNewArray` mas o `KofNewMultiArray` cai no `default -> { }` — os N
  `pushq` dos tamanhos ficam na pilha e o assignment pop **um só** (lixo): o
  slot guarda um endereçamento dos pushes remanescentes → deref → SIGSEGV. O
  default silencioso é ainda R6-violation (superfície documentada
  `learn/04-…:84` arrays multi-dim deveria diagnosticar se não implementável).
- **Fix (não feito — exige qemu p/ riscv/aarch? NÃO: x86 validável aqui, mas o
  desenho é multi-arch):** alocar recursively n-veis de `kof_array_alloc` com
  stride 8 (ponteiro) nas dimensões externas e o stride do `baseType` na
  última, preenchendo cada slot com o sub-array alocado; um helper
  `kof_multi_array_alloc(nDims, baseSize)` com loop interno (allocs aninhados
  na ordem de pilha dos tamanhos) nos 3 targets (x86 direto; riscv/aarch via
  as faces cross ficam skipadas NESTA sessão — port 1:1 quando houver qemu,
  precedente B35–B37). Alternativa R6-honesta imediata: diagnóstico no typer
  quando `driver.target.isNative() && dims>=2` (SEMxxx) até o port — mas
  QUEBRA código multi-target válido; decisão da lane Native com a mantenedora.
- **✅ CORRIGIDO 11/09 (x86):** helper recursivo `kof_multi_alloc`
  (`RuntimeArray.emitMultiArrayAlloc`): nível i lê `d_i` do stack do chamador
  (`rsp + 56i + 8n` — frame 64B/nível), aloca via `kof_array_alloc` com
  elemSize 8 (nó interno = ponteiros) ou `rbx`=stride da folha, e preenche os
  slots recursivamente; folhas `rep stosb` (zero — paridade MULTIANEWARRAY).
  Chamador (`NativeBackend.emitNewMultiArray`) passa n/stride/i=1, coleta as
  dims da pilha e pusha o nó. O `default -> {}` do `NativeMethodEmitter`
  virou **throw** (R6: op sem lowering nunca mais some em silêncio).
- **Port riscv/aarch NÃO feito nesta sessão:** sem toolchain no host
  (nota DOING) + records já bloqueiam cross (§104) e a pilha de frame do
  riscv exige re-derivação dos offsets 56i+8n; fica p/ sessão c/ qemu
  (precedente B32–B37), com a célula da matrix já travando o oracle.
- **Achado colateral (menor repro no probe, 11/09):** guardar `Int` em
  `Long[]` crasha o **JVM** (`frame crash / NegativeArraySizeException` na
  COMPUTE_FRAMES) — ver §121. A célula usa Int p/ não pendurar na lane alheia.
- **Prova:** célula `array2d` AGORA multidimensional de verdade
  (`new Int[2][3]` + 3-D `new Int[2][2][2]` store/load + zero-fill) 4/4
  sem exclusões + `NativeE2ETest#nativeMultiDimArray` (repro menor do §113
  → `2/7`); suíte completa pós-clean verde. Faces riscv/aarch: port pendente.

### 114. Native: `equals`/`==` de record com campo de REFERÊNCIA (String ou record aninhado) compara PONTEIRO → `false` — ⏳ PARCIAL 11/09 (face String ✅; record-aninhado/hash/coleção = §104b-ii) (sub-face do §104b-ii (i), backend-only)

- **Menor repro (medido 11/09):**
  `record S(String t)` + `println(S("ab") == S("ab"))` → JVM/Script/JS `true`,
  **Native `false`**. Idem aninhado: `record W(Pt p, String t)` +
  `W(Pt(1,2),"z") == W(Pt(1,2),"z")` → Native `false`. `Pt(Int,Int)` só com
  primitivos funciona (mesmo programa, `true` nos 4).
- **Causa raiz:** `CompilerRecordSupport.buildRecordEqualsMethod` (síntese
  Native, gateada a `Target.NATIVE*` — JVM usa `JvmRecordEmitter` com
  `Objects.equals`, JS usa `lowerRecordEquals`) compara CADA campo com
  `KofBinary(EQ, f.type())`. Para primitivo o lowering casa (valores crus);
  para `ClassType` o caminho é `cmpq`/`==` de **ponteiro** — nunca o equals de
  conteúdo (String: `kof_string_equals`; record: o próprio equals sintético).
  Top-level `s == t` de String funciona porque passa pelo caminho de
  comparação de String do typer (`Objects.equals`/`kof_string_equals`), não
  por aqui.
- **✅ FACE STRING CORRIGIDA 11/09:** campo String → `KofCall
  kof_string_equals(STRING,STRING)` como FUNCTION — o MESMO lowering do
  top-level `s == t` (ExpressionBinaryLowerer:214), já roteado nos 3 backends
  nativos (x86 direto; riscv `NativeRiscvCrossOps:253`; aarch via tradutor),
  null-safe medido: `S(null)==S(null)` → true (era true-acidental por
  ponteiro-null; continua true por conteúdo). Prova: célula `recordstrfield`
  4/4 sem exclusão (`S("ab")==S("ab")` true, mismatch false, campo misto
  Int+String) + suíte completa 4 módulos verde (1337+30+5+127).
- **Fix (não feito — faces restantes):** mesma infra do §104b-ii (i) — nos campos de referência
  do equals sintetizado, emitir o compare de conteúdo: String →
  `call kof_string_equals` (helper já existe); record aninhado → dispatch vtable
  `equals` pelo slot do tipo do campo (`findVirtualMethodIndex(f.type(),
  "equals")`, o mesmo mecanismo da célula `classequals`/§104b-i). Precisa do
  hash de conteúdo junto (hoje `buildRecordHashCodeMethod` soma campos — para
  referência usaria `hashCode()` vtable), e o mesmo port nas 3 faces
  (riscv/aarch sob qemu — bloqueadas NESTA sessão). NÃO é de superfície:
  `.equals`/`==` de String é contrato congelado e o oracle JVM é claro
  (conteúdo), por isso pode ser atacado direto na lane Native.
- **Prova esperada:** estender célula `objmethods` (hoje Native excluído =
  §104b-ii) com as variantes `S("ab")==S("ab")` e `W(Pt(1,2),"z")==W(Pt(1,2),"z")`
  → 4/4 sem exclusões.
- **RE-DIMENSIONADO 12/09 (medido, sem edição de código):** `record Outer(Inner
  i, String s)` aninhado → JVM `true`, **Native x86 `false`** (confirmado hoje,
  `NEST.kf`). O dispatch-vtable de `equals` **JÁ EXISTE e funciona** (o
  top-level `a==b` no x86 devolve `true` via `ExpressionBinaryLowerer:191`
  → `KofCall INSTANCE equals` → `NativeX86Calls:322` `findVirtualMethodIndex`).
  O delta do campo aninhado é só: (a) trocar o `KofBinary(EQ, f.type())` do
  record-não-String por `KofCall(f.type(),"equals",[Object],BOOL,INSTANCE)`,
  (b) **null-guard** (field pode ser null → `Objects.equals` = `a==b || (a!=null
  && a.equals(b))` — exige `KofConditionalJump`/`KofLabel` dentro do equals
  sintetizado, que hoje é um bloco linear sem jumps), (c) o MESMO port no riscv
  (`NativeRiscvCrossOps`) + aarch tradutor + reconstructor JS (o JS já dá true
  por `lowerRecordEquals` nativo do objeto — mas o reconstructor lê a IR do
  Native, precisa aceitar os jumps novos), (d) hash de conteúdo coerente. NÃO é
  trivial-x86-só: sem qemu aqui, (c)+(d) ficam sem prova → meia-unidade.
  **Recomendação:** agrupar §114-nested + §107-x86 + §104b-ii(i) numa só frente
  NATIVE-storage/vtable (mesma infra de "conhecer o tipo do conteúdo em
  runtime"), planejada em `docs/development/` e executada numa sessão COM toolchain
  riscv/aarch (valida as 3 faces de uma vez). Rodar fora disso = regressão
  silenciosa cross-target (proibido).

### 111. `split` não removia vazios TRAILING (Native x86 + JS) e `substring(a,0)` devolvia a string toda (Native x86) — ✅ CORRIGIDO 11/09 (x86_64 + JS; residual riscv/aarch)
- **Menor repro split:** `println("a,".split(",").length)` → JVM/Script **1**,
  x86 **2**, JS **2**. `",".split(",")` → JVM **0** (todos trailing), x86 **2**.
  Oracle = Java `String.split(regex)` que é `split(regex,0)`: remove vazios
  TRAILING, EXCETO input `""` → `[""]` (tamanho 1).
- **Menor repro substring:** `"hello".substring(0,0).length` → JVM **0**, x86
  **5**. O call-site 1-arg (`NativeX86StringCalls`) passava `end=0` como
  sentinela "até o fim"; o helper `kof_string_substring` tratava `end==0` como
  toend — mas `0` é um **end legítimo** da forma 2-arg → colapsava.
- **Causa raiz split:** o loop `.Lkof_split_done` (`RuntimeStringEdit`) contava
  pieces SEM o trim Java. **JS:** `JsCallEmitter` mapeava `split` →
  `String.prototype.split` direto, que PRESERVA trailing (semântica JS ≠ Java).
- **Fix x86:** pós-processamento no `.Lkof_split_done` — scan do fim do array
  removendo pieces com `nBytes==0` (offset 16), sobrescreve o `length` do array
  (offset 16); caso input vazio força `[""]`. **Fix JS:** helper `kofSplit`
  (`JsRuntimeCore`) faz `split` + trim de trailing + o caso `""`. **Fix
  substring x86:** sentinela do 1-arg `0` → `-1` (call-site + helper
  `cmpl $-1`), liberando `end=0` como valor real.
- **Prova:** célula `strsplit` (4 targets byte-idênticos
  `1/0/2/1/3/0/llo/0`) + os probes `sp2.kf`/`sp3.kf` (conteúdo dos pieces,
  trailing, bordas `substring(5)`/unicode `café`).
- **✅ Face `substring` CORRIGIDA 11/09 (cross, B36 `f6831e31`, qemu presente):**
  call-site 1-arg virou `li a2, -1` (CrossOps) + guard `bltz s2` (B34) —
  `hello.substring(0,0)` → `[]` (era a string toda), `substring(2)` → `[llo]`;
  prova `NativeStringCompareCrossTest` (7 vetores, riscv+aarch == JVM == x86).
- **✅ Face `split` CORRIGIDA 11/09 (cross, B37 `e960c9fd`, qemu):** port 1:1
  do `.Lkof_split_done` x86 no riscv (`NativeRiscvAsmStrn1`): scan do fim
  podando vazios (s7 = peças MATERIALIZADAS, não o count da alocação),
  sobrescreve length do array, e input vazio força `[""]` (nunca garbage do
  bump). Prova: `NativeStringCompareCrossTest` split (9 linhas, 6 faces
  `a,b,`→2/`a,`→1/`,`→0/`""`→1`[]`/`a,b`→2/`,a`→2) riscv+aarch == JVM == x86
  byte-idênticos sob qemu. §111 ✅ FECHADO nos 5 targets (paridade absoluta).

### 110. Literal/fold `-0.0` vira `+0.0` no JVM (perde o zero com sinal) — ✅ CORRIGIDO 11/09 (guard de raw bits no literal emitter)
- **Menor repro:** `main() { println(-0.0) }` → JVM **`0.0`**, Native/Script **`-0.0`**.
  Idem para fold: `println(-1.0 * 0.0)` e negação `val z = 0.0; println(-z)`.
- **Causa raiz (JVM-only, `JvmLiteralEmitter`):** os atalhos de instrução
  testavam `value == 0.0` / `value == 0f` — e **em IEEE `-0.0 == 0.0` é true**,
  então o literal (ou resultado de fold em `OptimizerConstantFold`, que é
  correto: `av * bv` dá `-0.0`) era emitido como `DCONST_0`/`FCONST_0`, que
  são sempre **+zero** no bytecode. O run-time estava certo (`a * b` com
  variáveis → `-0.0`); só o **caminho do literal** colapsava.
- **Fix:** guard por bits crus — `value == 0.0 && Double.doubleToRawLongBits(value) == 0L`
  (idem `Float.floatToRawIntBits` para float); `-0.0` cai no `visitLdcInsn`
  (LDC de double preserva bits). Mesmo bug simétrico em `emitLoadFloat`,
  corrigido junto (`val f = -0.0 as Float`).
- **Contrato preservado (não é §94):** `0.0 == -0.0` continua **`true`** —
  a célula NÃO mexe em comparação de signed zero (congelado), só no SPELLING
  do literal. Oracle: comportamento de run-time do próprio JVM (que já dava
  `-0.0` corretamente em `a*b`).
- **Prova:** célula `negzero` (JVM+Native+Script byte-idênticos; **JS excluído**
  = `String(-0.0)` → `"0"` sem `.0`, já é o floatprint §44 documentado, não
  regressão). Face riscv/aarch: sem qemu no ambiente — o fix é JVM-only
  (emissor de bytecode), as faces nativas nunca tiveram o colapso.

### 105. `random.int(bound)`/`randomInt(bound)` em riscv64/aarch64 entra em LOOP INFINITO para qualquer bound > 1 — ✅ CORRIGIDO 11/09 (aritmética de rejection sampling) [renumerado de 102 — o número foi tomado pelo §102 indexOf(String,from) no remoto na mesma data]

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
- **Arquivos:** `NativeRiscvAsmRtB27.java` (`kof_random_int`). Também destrava `random.randomString` (B28 chama `kof_random_int` p/ índice do alfabeto). *(Confirmação independente na ponta da 0.4.0, `2266f323` — mesma fórmula, merged sem conflito.)*

### 115. Constant pool: Float/Double armazenados como bits crus (parser de migração) — ✅ CORRIGIDO 08/09  *(renumerado de §62 na reconciliação do merge beta-0.3.0→beta-0.4.0 11/09 — colidiu com a série ativa §95–§114)*

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

### CANVAS001 — ClassFormatError com arc() (Double params) — ✅ FECHADO (JVM 06/09; JS 06/09 `5a9cac46` — reprovado verde 12/09: `UiE2ETest` 29/29 sem exclusões)

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
- **Face JS — FECHADA 06/09 (`5a9cac46`):** shim `getContext` +
  `attach` ao `kof-root` + snapshot em ops de renderização (Canvas serializa
  sem `Window.show()`). **Reprovado 12/09:** `UiE2ETest` completo 29/29 sem
  exclusões (incluindo `canvasCreation`) — a flag `!UiE2ETest#canvasCreation`
  do loop de verificação do AGENTS.md era obsoleta e foi removida.
- **Arquivos:** `MethodCallTyper.java`, `BuiltinCallTyper.java`,
  `JvmRuntimeCallDescriptors.java` (JVM); `JsRuntimeCore.java`,
  `JsRuntimeUiWidgets.java` (JS). Plano consolidado: `docs/ui/PLAN-CANVAS-WIDGET.md`.
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

### 116. Native x86: hijack de método de usuário com nome de String-op (`p.trim()` → LIXO silencioso) — ✅ CORRIGIDO 10/09  *(renumerado de §100 na reconciliação do merge beta-0.3.0→beta-0.4.0 11/09 — colidiu com a série ativa §95–§114)*

- **Sintoma:** `class P { Int trim() { 42 } }` + `p.trim()` no x86: JVM e JS
  dão `42`, o **Native dá lixo** (medido: `-103849952` — o receiver da classe
  foi dereferenciado como ponteiro `KofString` e a soma de bytes do heap
  virou "comprimento"). Silencioso (exit 0) — pior classe: paridade quebrada
  SEM diagnóstico (regra 5). Idem `indexOf`/`split`/`toUpperCase`/… em classe
  do usuário.
- **Causa:** dos 16 ramos `INSTANCE` de `NativeX86StringCalls.emit`, só
  `length` e `equals` checavam `BuiltinTypes.isString(kc.ownerType())`; os
  outros 14 (`charAt`…`split`) casavam **só por nome** e rodavam ANTES do
  dispatch virtual genérico (vtable) de `NativeX86Calls.emitCall` → qualquer
  método de usuário com nome colidente era sequestrado pelo intrínseco.
- **Corpus:** nada autoriza o hijack — o dispatch de método é por classe
  (JVM `INVOKEVIRTUAL <owner>`, riscv `isString` no `NativeRiscvCrossOps`,
  JS `isStringOp` = `ownerType==String`). O x86 era o outlier.
- **Fix (preserva semântica — regra 3):** guard `isString(ownerType)` nos
  14 ramos. Os ramos FUNCTION (`kof_string_to_*`, `kof_json_decode_*`) não
  ganham guard: nome prefixado é inatingível por método de usuário.
- **Prova:** `NativeE2ETest.nativeUserClassMethodsNotHijackedByStringOps`
  (classe `P` com `trim`/`indexOf`/`split`/`toUpperCase` de usuário + as
  MESMAS 4 ops em String real no mesmo programa — golden JVM medido
  `42 2 s9 7 x| 2 b AB`, x86 idêntico pós-fix; antes o primeiro valor era
  lixo). Regressão coberta: `recordhash` (matrix, DONE nativo — record.hashCode
  cai no vtable, intacto) + `nativeStringEqualsVsRecordEquals` (bug 97,
  lado a lado). Suíte completa **1307+30+5+127 / 0 falhas / 94-skip**.
- **Nota (riscv/aarch):** o gate já existia em `NativeRiscvCrossOps` —
  residual da família §97 cross é SÓ a AUSÊNCIA dos 3 símbolos
  (equals/compareTo/hashCode), não o hijack.

### 117. Native x86: `cancelled()` usa tabela de 256 slots por hash de TID → colisão (cancel de um worker vaza/outro apaga) — ABERTO (registrado da issue #83)  *(renumerado de §101 na reconciliação do merge beta-0.3.0→beta-0.4.0 11/09 — colidiu com a série ativa §95–§114)*

- **Sintoma (potencial, não reproduzido):** o trampoline de spawn
  (`RuntimeConcurrency.java:20-38`) resolve o slot como
  `(TID * 0x9E3779B97F4A7C15) >> 56` (0..255) e **limpa o slot na partida**
  (`movb $0`), assumindo que é "do worker anterior reutilizado". Dois workers
  VIVOS com colisão no slot: (a) o segundo a largar limpa o flag que o
  primeiro setou via `cancel` → cancelamento perdido; (b) `cancel(h)` seta o
  slot do hash → pode cancelar worker alheio. JS/interpretador: `cancelled()`
  sempre 0 (registrado em `backend-parity.md:84`); riscv/aarch: `CONC001`.
- **Causa:** tabela fixa 256 indexada por hash sem chave de identidade
  (pthread_t não é o índice — só o hash truncado).
- **Fix sugerido (lane Native, pequeno):** slot por **handle** (bloco já
  tem campo livre) em vez de TID: `cancel(h)` seta `h+24`-adjacente (flag
  própria do handle), trampoline lê o SEU handle — zero colisão, tabela
  nem precisa mais. Ou: chave dupla `(slot, pthread_self)` com CAS. Qualquer
  um: E2E com 2 workers longos + cancel do 2º (harness de 50+ iterações p/
  pegar colisão). NÃO bloqueia `planning-otp-supervision` (que usa flag
  própria de stdlib — DD-OTP-08).
- **SONDA 11/09 (tentativa de atacar — voltou a ABERTO, com plano travado):**
  repro probabilística medida: worker longo cooperativo + worker curto
  cancelado, 5 execuções x86 nativas — 5/5 sem colisão (TIDs de pthread
  tendem a ser sequenciais; o hash `0x9E3779B9…>>56` só colide com TIDs
  distantes ~2^56 — raro na prática, mas REAL após wrap de TID / threads
  encerradas: o `movb $0` do trampoline no slot alheio continua silencioso).
  **Por que NAO implementei sem decisão (regra 6):** o fix correto exige TLS
  (`__thread kof_current_handle`, setado na entrada do trampoline — ~6 linhas
  x86 + chamada `tls_get_addr` ou FS-base via `movq %fs:0`) OU crescer o bloco
  do trampolim p/ carregar o handle (já carregado! `8(%rdi)` é o handle no
  bloco — o trampoline PODE setar um field `cancelled` no próprio handle ANTES
  da task, mas `cancelled()` não recebe handle — é função global; trocar a
  assinatura `cancelled()`→leitura TLS = mudança de CONTRATO da API
  congelada spawn/await/cancel). O caminho sem mudança de contrato é o **TLS
  implícito**: `kof_cancel(h)` marca `h.canceled` (field novo 32 no handle,
  alloc 32→40) E o trampoline publica `tls = h` no start; `cancelled()` lê o
  TLS e responde `h.canceled` (fora de worker → 0, paridade com hoje). Zero
  colisão (flag é do handle), tabela 256 some. **Decisão pedida à
  mantenedora:** aprovar TLS+field `canceled@32` (plano acima, ~30 linhas em
  `RuntimeConcurrency.java` + `KofInterpreterConcurrency` paridade + teste de
  colisão forçada via 2 workers com cancel encadeado em loop de 1000 spawns)
  OU aceitar o risco documentado (colisão exige TID-wrap; 5/5 limpo na sonda).
  riscv/aarch não têm `kof_cancel` exportado (gate CONC001 só no x86) — o
  fix é x86-only, sem efeito paridade nos outros nativos (já diagnosticado).

### 118. kof.ui: chamadas de instância em `Column`/`Row` eram DROPADAS silenciosamente (compila e não faz nada) — ✅ CORRIGIDO 11/09  *(renumerado de §102 na reconciliação do merge beta-0.3.0→beta-0.4.0 11/09 — colidiu com a série ativa §95–§114)*

- **Sintoma:** `col.setId("x")`, `col.setClass("c")`, `col.setBorder(...)` etc.
  compilavam com sucesso nos 4 alvos e **não emitiam call nenhuma** — o `.mjs`
  simplesmente não tinha a chamada (repro: `Column(listOf(b)).setId/setClass/
  setBorder` → `Default.mjs` só com `kofUiColumnNew`).
- **Causa raiz:** o typer resolve métodos UI por `KofUi.isUiType` (inclui
  Column/Row, e `KofUi.instanceMethod` aceita os `kof_ui_widget_*` para todo
  `isDomWidget`, que inclui Column/Row), mas o `emitUiInstance` do lowerer
  roteava por uma LISTA HARDCODED de tipos que omitia Column/Row → branch
  `default → return` sem call e sem diagnóstico (anti-R6).
- **Fix:** gate do bloco/widget em `CompilerUiEmitter.emitUiInstance` passa a
  `KofUi.isDomWidget(recvType) || KofUi.isWindow(recvType) || KofUi.isCanvas
  (recvType)` — o MESMO predicado que o registry usa para resolver; a lista
  não pode voltar a divergir do `isDomWidget`.
- **Prova:** `UiE2ETest.widgetVisualPrimitivesLinkOnAllTargets` (JVM+Native
  link+run das 5 primitivas novas em Column) +
  `KofJsBrowserE2ETest.widgetVisualPrimitivesRenderInRealBrowserDom` (Chrome
  headless real: border/box-shadow/linear-gradient/flex/max-width no DOM — o
  gradiente num Column é exatamente o caso que sumia); emit verificado no
  `.mjs` (`kofUiWidgetSetId/SetClass/SetBorder` aparecem).
- **Resíduo honesto:** outros receivers de `isUiType` que não widget DOM
  (Style/Font/Event/Box/Stack/...) continuam sem métodos próprios no
  registry — se um dia `instanceMethod` os aceitar, o gate precisa cobri-los
  (mesma lição: registry e lowerer compartilham predicado, não lista).

### 119. Tradutor riscv→aarch64: `lw` traduzido como `ldr w` (zero-extend) onde riscv é sign-extend — ✅ CORRIGIDO 11/09 (raiz; `ldrsw`)  *(renumerado de §103 na reconciliação do merge beta-0.3.0→beta-0.4.0 11/09 — colidiu com a série ativa §95–§114)*

- **Sintoma (histórico, pegado na prova do §97 cross em 11/09):** programa com
  sentinela `-1` carregada da pilha (`lw t0, 16(sp)`-like) dava `-1` no riscv e
  `0xFFFFFFFF` no aarch sob qemu (diff de prefixo `compareTo` virava `-100`).
- **Causa raiz:** `NativeAarch64Translator` mapeava `lw` → `ldr w, [..]`, que
  no AArch64 **zera** os 32 bits altos; o `lw` riscv faz **sign-extend** do
  word para 64 bits. Os irmãos já estavam certos (`lb`→`ldrsb`, `lh`→`ldrsh`,
  e `lbu`→`ldrb` que de fato é zero-extend no riscv) — `lw` era o único
  mnemonic com extensão errada. Latente em TODO `lw` de valor possivelmente
  negativo (o codegen de locals usa `ld`, e os `lw` do runtime são metadados
  não-negativos — byteLen/tam/flags — por isso nunca aparecera fora do §97).
- **Menor repro (na época):** o caso prefixo do §97 (`"ab"` vs `"abc"` →
  `compareTo` = `-1`, lida da pilha e somada) → riscv `-1`, aarch `-100`.
- **Workaround da época (mantido):** `sext.w` explícito após o `lw` na fatia
  B32 (no-op no riscv; corrigia o aarch). Após a correção-raiz o `sext.w` fica
  redundante mas INOCUO (sinal-extend de um valor já sinal-extendido) —
  removê-lo seria churn sem ganho; fica como double-proteção documentada.
- **Fix (raiz):** `lw` → `ldrsw x<rd>, [base]` (load signed word, o match
  exato do `lw` riscv). Para valores não-negativos (todos os usos atuais do
  runtime: contagens, offsets, headers, flags) `ldrsw` é bit-idêntico a
  `ldr w` → risco controlado pela suíte. O caso `rdRaw=sp` ajustado
  conjuntamente (`lw sp` inexistente no corpus, 0 refs — mas o ramo ficaria
  inconsistente; temp passa a `x17` 64-bit).
- **Prova:** gate de EXECUÇÃO sob qemu (sem golden textual do tradutor no
  repo): `NativeRiscv64E2ETest` 33/0 + `NativeAarch64E2ETest` 33/0 (inclui os
  3 testes UTF-16 que cobrem as sentinela `-1` do §97/§43 nos dois alvos) +
  suítes cross-ativas 13 classes ~194/0 sob qemu (concorrência/math/net/
  security/time/uuid/validation/string/encoding/mq/random/parse/matrix) +
  `NativeE2ETest` x86 61/0 + suíte completa baseline 0-falhas.

### 121. JVM: guardar `Int` em array `Long` (`new Long[4]; c[1] = 9`) crasha o backend — ✅ CORRIGIDO 11/09 (achado pela prova do §113)

- **Menor repro (probe 11/09):** `new Long[4]; c[1] = 9` →
  **compile JVM falha**: `frame crash ... COMPUTE_FRAMES (visitMaxs)
  NegativeArraySizeException: -1`. Idem `new Long[2][2]; c[1][0] = 9` (o
  `c[1][0]` resolve p/ `long` primitivo). Int→Int e String→String ok; o
  bug é o widening store `Int`→slot `Long`.
- **Causa raiz provável:** `ExpressionAssignmentLowerer:321-329` — o bloco
  que DEVERIA emitir a conversão (`I2L`/`L2I`) quando o primitivo do valor ≠
  o tipo do slot é um `if {}` VAZIO (só o comentário explica o motivo: sem
  ela, `aastore/lastore` com tipo errado → verifier rejeita = o frame crash
  documentado como COMP002). O comentário diz "converter no IR"; o código não
  converte. `newMultiArray` do §113 expôs isto ao varrer os alvos da célula.
- **✅ CORRIGIDO 11/09 (lane JVM):** o bloco vazio recebeu a MESMA linha
  que o caminho compound usa 20 linhas acima:
  `if (isPrimitiveType(value) && isPrimitiveType(elem))
   driver.emitWideningIfNeeded(ops, aaValueType, aaElemType)` antes do
  `KofArrayStore` (`I2L` etc. no IR — cada backend já trata `KofUnary(I2L)`).
- **Prova:** célula `arrlongstore` (Int→Long 1-D e 2-D + zero-fill, 4/4 sem
  exclusão); suíte completa pós-clean verde (1499 run; 12 err=node, 136 skip=
  cross-sem-toolchain). JS/Native/Script já aceitavam; só o codegen JVM
  estava quebrado — nenhuma mudança de contrato (widening Int→Long já é
  documentado em `learn/`).

### 122. List `get`/`set`/`remove` com índice NÃO-Int (String/record/array) aceito em silêncio → JVM VerifyError na carga, Native pointer-as-index → ⏳→✅ (opção B, família SEM051-054) [SEM055 11/09]

- **Menor repro (medido 11/09, probes RM3/IX/IX2):**
  `var s = listOf("a","b"); println(s.remove("a"))` → **JVM**:
  `VerifyError: Bad type on operand stack ... not assignable to integer` na
  **carga da classe** (todo o programa morre, não só o statement);
  **Native**: o ponteiro da String vira índice → `array index out of bounds`
  (exit≠0). `get("x")`/`set("k",v)` idem. Int no índice funciona (`IXC`).
- **Causa raiz:** `CollectionCallLowerer` (lowering único dos 5 targets)
  despacha `kof_list_get/set/remove` sem checar o tipo do argumento — o
  contrato (learn/12, training/idioms/collections) é **índice Int**
  (`remove(0)` devolve o elemento); Java tem overloads remove(int)/remove(Object),
  Kof não — o by-value era fake idiom aceito.
- **✅ CORRIGIDO 11/09 (opção B — decisão da mantenedora §100, mesma
  família SEM051/052/053/054):** guarda por posição no lowering:
  `get/set/remove` de List com tipo de **referência** (ClassType/record/
  ArrayType/TypeVariable, desconhecidos-nullable NUNCA flagados — SG-008 pode
  chegar Int em runtime) no índice → **SEM055** ("List.remove pega ÍNDICE
  Int; String não é índice (para buscar por valor use contains)"), nos 5
  backends pelo frontend único. Programas que funcionavam: nenhum (todos
  crashavam ou imprimiam lixo) → rejeição aditiva, retrocompatível.
- **Prova:** `SemanticResolutionTest.listIndexNonIntRejected` (4 vetores,
  String/record-aninhado/array) + `listIndexIntAndUnknownStillCompiles`
  (regra 1 — Int não regride); suíte completa 4 módulos verde.
- **Corpus:** `training/idioms/collections.md` (comentário no remove) +
  `fake-idioms.md` (linha nova).

### 123. Native: `Map<Int,*>` SIGSEGVa em qualquer get/put — `kof_map_find` hardcoded `kof_string_equals` (chave Int vira PONTEIRO) — ✅ CORRIGIDO 11/09 (x86+riscv; aarch por tradução)

- **Menor repro (medido 11/09, probes C1/D1):** `var m = mapOf(1, 2);
  println(m.get(1))` → **Native ec=139** (SIGSEGV), JVM `2`. Não é o caso
  de tipo-errado (§122): com os tipos CERTOS (Int key, Int/String val) o
  programa morria. `m.put(3,4)` idem; `m.size` passava (não chama find).
- **Causa raiz:** o Map nativo foi escrito para a fase P1 como
  **`Map<String,V>`** (literal no header do arquivo: "kof.collections:
  Map<String,V> nativo (P1)") — `kof_map_find` chama `kof_string_equals`
  SEMPRE; com chave Int o inteiro cru é interpretado como ponteiro →
  leitura em endereço inválido. O Set já tinha tag de tipo (1=String →
  `kof_string_equals`, 0 → `cmpq`); o Map nunca recebeu.
- **✅ Fix (mesma tag, no HEADER do map — off 40, dentro dos 64B já
  alocados; sem mudar assinatura nem IR):** `kof_map_new` inicializa
  tag=1 (String = o caso histórico → **zero regressão p/ Unknown**);
  `kof_map_find` lê a tag do struct (String → equals; senão `cmpq` — chave
  0 é legítima no modo raw, por isso o skip de null só vale p/ String);
  o EMITTER (x86 `NativeX86Calls` + riscv `NativeRiscvCrossOps`; aarch
  traduz o x86) escreve a tag a partir do tipo do 1º arg do put/get/
  remove/contains (Unknown NÃO toca). O `KofCall` original do mapOf já
  carregava key Int como INT no slot (mapOf(1,2) funcionava em size) —
  o modo raw compara exatamente esses words.
- **Prova:** célula `mapint` 4/4 sem exclusão (put/get/size/containsKey/
  remove Int-key String-val) + probes D1/C1/C2/D4/A1/A3/MP2 nativos
  ec=0; suíte completa 4 módulos verde (1501/0/12err-node/136skip).
- **Prova CROSS dedicada (12/09, qemu real):** `riscv64MapKeyTagCross`
  (NativeRiscv64E2ETest) + `aarch64MapKeyTagCross` (NativeAarch64E2ETest)
  rodam `mapOf(1,"a",2,"b").get(1)` (chave Int → tag=0 raw-cmp, o caso que
  SIGSEGVava), `.get("x")`/`.contains` tipo-errado (miss seguro, família
  §126) — golden = oracle JVM medido (`a\nnull\ntrue\nnull\nfalse\ntrue\n0\n7`),
  Skipped=0 (toolchain presente: qemu-riscv64/qemu-aarch64 + binutils cruzados).
  Confirma o emissor `NativeRiscvCrossOps.java:270-294` (`li t0,<tag>;
  sw t0,40(a0)`) e sua tradução aarch (off 40 cabe no immediato `str`).
- **Fora daqui (registrados):** chave do TIPO ERRADO (Int em Map<String,V>
  e vizinhos) → família §122 (SEM05x, rejeitar em compile-time) — A2
  ainda SIGSEGVa até a guarda de Map/Set; §124 novo (abaixo) foi achado
  pela célula.

### 124. Script/interpretador: `println` de `String?` null → NPE "Cannot read the array length because \"value\" is null" (JVM/Native/JS imprimem `null`) — ✅ CORRIGIDO 11/09

- **Menor repro (medido 11/09, MI5):** `String? nd() { return null }` +
  `println(nd())` → **Script ec=1** com a mensagem em stderr; **JVM/Native
  imprimem `null`**. Confirmado PRÉ-EXISTENTE (roda igual com a árvore do
  §123 em stash) — não é regressão da tag do map. `map.get` de MISS com
  valor String no interpretador cai no mesmo caminho (MI1: out=[um] então
  ec=1) e `m.remove` de chave inexistente idem (MI6/MI3).
- **Causa raiz (stack via `-Dkof.interp.trace=1`):** o `println(arg)`
  não-escalar baixa `String.valueOf(arg)` como chamada EXTERNA
  (ExpressionPrintLowerer: `List.of(Unknown)` nos targets não-nativos). No
  interpretador, `KofInterpreterRuntime.invokeExternal` pontua os overloads
  com `signatureScore`: arg null nunca passa em `p.isInstance(args[i])`,
  e `valueOf(char[])`/`valueOf(Object)` empatam em score → a ordem de
  `c.getMethods()` escolhia o ARRAY → o JDK NPE ("Cannot read the array
  length because "value" is null" = `new String(char[])` null).
- **✅ Fix:** no scorer, arg `null` desqualifica parâmetro ARRAY (score
  −1) a menos que o IR declare `ArrayType` de verdade (guarda por IR, não
  por sorteio de reflection). `valueOf(Object)` vence → `String.valueOf(
  null)` = "null", como JVM/Native.
- **Prova:** `KofInterpreterParityTest.printNullableStringNull` (2 paridades
  interp≡JVM: `println(nd())` e map-get-miss) + célula `mapint` da matriz
  4/4 imprimindo `null` no get-miss (antes contornava a terra-minada);
  suíte completa 4 módulos.
- **Achas irmãs (registradas, NÃO corrigidas aqui):** `println(Int? null)`
  → JVM **VerifyError** e Script `Integer.valueOf/1` (NoSuchMethod) —
  §125. `println(char)` congelado numérico (strings.md) continua intocado.

### 129. Native `Set.remove(x)` deletava o elemento no ÍNDICE == tag, não o encontrado — silent data corruption (pior que crash) — ✅ CORRIGIDO 11/09 (x86; riscv já estava certo; aarch traduz)

- **Menor repro (SR1, medido 11/09):** `setOf("a","b","c")`:
  `s.remove("a")` → retornava `true` mas removia **"b"**. `s.contains("b")`
  = false, `s.contains("a")` = true (JVM: o inverso). Corrupção SILENCIOSA
  — remove() dizia sucesso e apagava o vizinho errado.
- **Causa raiz (x86 `RuntimeSet.kof_set_remove`):** no `.LKSR_found` o
  caminho fazia `movq %r13, %rsi` para o `kof_list_remove` — **r13 é a
  TAG** (1=String), não o índice. O índice do loop (achado) vivia em
  **r14**, nunca usado no remove. `remove("a")` (hit no índice 0, tag 1)
  passava 1 → apagava o índice 1. Só escapava quando tag==índice.
- **✅ Fix:** `movq %r14, %rsi` (r13/r14 são callee-saved → sobrevivem ao
  `call kof_string_equals` no meio do loop). riscv (`Mapset0.Lksr_found:
  mv a1, s3`) já passava o índice correto — bug era x86-only; aarch64
  traduz o x86 → coberto.
- **Por que não foi pego:** a célula `setdedup` nunca chamava `remove`;
  os probes §126 só mediam `add`/`contains`. Estendida agora (remove
  Int-hit, remove-miss, remove String no índice 0) → `setdedup` 4/4.
- **Prova:** SR1/SR2/SR3 nativos = JVM byte-a-byte; célula `setdedup`
  expandida (10 linhas de saída); suíte completa abaixo.

### 128. kof-cli `DecompileTest#recoversStatementSwitchAndRunsIt` VERMELHO — decompilador emite statement-switch com `var` só no 1º case → SEM011 no recompile — ✅ CORRIGIDO 11/09 (consolidado no §135 — fix com hoist de locals + `static` na assinatura + teste executando o decompilado; DecompileTest 45/45)

- **Reprodução (exata, medida 11/09):** em `94c4a1fb` LIMPO (working tree
  sem nenhuma edição da lane collections): `mvn -o -pl kof-cli -am test
  -Dtest='DecompileTest#recoversStatementSwitchAndRunsIt'` → falha.
  Reproduz também na suíte completa da lane (§126) com os edits em stash.
  **Comportamento ordem-dependente**: na suíte completa de 11/09 mais
  cedo (1501–1502, HEADs anteriores) passava; isolado, falha — método
  isolado não herda o warm-up/state compartilhado dos 44 vizinhos.
- **Sintoma:** o esqueleto decompilado de um `switch` statement com
  variável local compartilhada declara `var v1` APENAS no `case 1` e o
  referencia nos cases seguintes + no `return` → o recompile do output
  cospe SEM011 "Undefined variable or type: 'v1'" (3×).
- **Causa provável:** recuperação de bodies do decompiler (Fase E) não
  hoista as declarações de locals usados além do case em que aparecem;
  o recovery por bytecode precisa declarar a variável ANTES do switch
  (o slot existe desde o primeiro write, mas o escopo Kof é textual).
- **Não é da lane §123/§126:** nenhum arquivo tocado (kof-cli/decompiler
  × kof-compiler/collections); a prova é o reproduz-no-HEAD-limpo.
- **Ação:** lane do decompilador (KOFSCRIPT/legacy) precisa hoistar a
  declaração; até lá, quem rodar a suíte completa pode ver 1 fail além do
  node-trio — não "corrigir" o teste (regra: gate quebrado = registrar).
- **Prioridade:** média (vermelho em teste de gate; sem produto afetado
  além do round-trip switch-statement).

### 127. JS: `map.get/remove` de MISS com VALOR primitivo devolve `null` (JVM/Native/Script devolvem o default `0`/`false`) — ✅ CORRIGIDO 12/09 (célula `wrongkey` 5/5 sem exclusões)

- **Menor repro (medido 11/09):** `mapOf("a", 1).get("zz")` → **JS `null`**,
  JVM/Native/Script **`0`**. Célula `wrongkey` divergiu só na última linha
  (as três anteriores — String-value null, contains false — casam).
- **Causa:** `kofMapGet`/`kofMapRemove` (JsRuntimeUiLayout:184-197)
  devolvem `null` fixo no miss; o SG-008/bug-87 (default do primitivo em
  `Nullable(primitive)` no uso) foi implementado no JVM
  (`JvmOpCollections.emitPrevValueUnbox`) e no interpretador
  (`KofInterpreterCollections` guard §112) mas **nunca no JS** — o guard
  precisa do tipo de retorno do call, que só existe no lowering
  (`retType` do `CollectionCallLowerer`), não no runtime export.
- **Fix provável:** no lowerer JS (ou no wrapper da chamada), quando
  `retType` é primitivo/`Nullable(primitivo)`, `get`/`remove` de miss →
  default (`0`/`0.0`/`false`/`\0`) — espelhando o padrão JVM/Script; a
  célula `mapgetprim` (valores primitivos String-key HIT) não cobre o MISS.
- **Prova esperada:** `mapOf("a",1).get("zz")` = `0` nos 4 (extensão da
  célula `wrongkey` tirando a exclusão JS) + paridade `map-miss-primitive`
  (Int/Long/Double/Bool → `0`/`0`/`0.0`/`false`, String → `null`).
- **✅ CORRIGIDO 12/09 (célula `wrongkey` sem exclusão JS — 5/5).** Causa
  dupla na lowering JS (`JsCollectionOps.handleMapOp`): (i) o ramo do §112-JS
  que embrulha `?? default` só casava `put`/`remove` com `returnType`
  `PrimitiveType` PURO — mas `kof_map_get` declara `Nullable(V)`, então o
  get-miss **nunca** era coercitado e o `null` do runtime vazava; (ii)
  `defaultForType(Bool)` devolvia `JsNumber("0")` (não `false`) e NEM
  desempacotava `Nullable` (a célula `wrongkey` nunca exercitou Bool-miss, e
  `mapgetprim` é só HIT — por isso o bug de Bool passava invisível). Fix:
  `get` adicionado ao ramo; o guard agora aceita `Nullable(Primitivo)`;
  `defaultForType` desempacota `Nullable` e devolve `false` p/ Bool (correto
  p/ os 3 consumidores: field-default de Bool, put/remove, poll). Prova
  medida JS (KofJsRunner/GraalJS): `0|0|0.0|false|null` = oracle JVM/Native/
  Script (Int/Long/Double/Bool-miss + String-miss). **Residual NÃO-§127:**
  o DOUBLE-miss imprime `0` (não `0.0`) no JS — é a divergência de
  IMPRESSÃO de Number do JS (`String(5.0)="5"`, célula `floatprint`, §44/
  família), o VALOR armazenado está correto; fica fora daqui (decisão de
  formatação). `remove`/`put`-first de chave-ausente com valor primitivo já
  cobertos (mesmo ramo, agora com `get`).
  no harness JS.

### 125. `println(<primitivo>? null)` (Int?/Bool?/... null): JVM **VerifyError** na carga + Script **NoSuchMethodError `Integer.valueOf/1`**; Native imprime `0` — ✅ CORRIGIDO 12/09 (decisão da mantenedora: opção A — alinhar ao precedente do map-miss, `0`)

- **Menor repro (PN2, medido 11/09):** `Int? ni() { return null }` +
  `println(ni())` → **JVM**: VerifyError na inicialização da classe
  (operand stack — boxing do null); **Script**: `java.lang.Integer.valueOf/1`
  NoSuchMethodError (o MESMO `invokeExternal` do §124 — agora escolhendo o
  overload certo, mas o boxing do null não resolve p/ `Integer.valueOf/1`);
  **Native**: imprime `0` (default do primitivo, SG-008). Os 3 divergem.
- **Por que é DIVERSO do §124:** lá o arg null do `String?` batia no
  overload errado (char[] vs Object); aqui `println(Nullable(INT))` faz
  `boxPrimitive` → `valueOf(INT)` e o caminho do null no boxing/choice
  falha em outros pontos (JVM bytecode inválido + reflect sem alvo).
- **Oracle (regra 4) — o PRECEDENTE CONGELADO já responde, mas há
  inconsistência entre caminhos do MESMO tipo (aqui está o bug):**
  (a) `println(mapOf("a",1).get("zz"))` — `Nullable(INT)` vindo do
  MAP-miss — imprime **`0` nos 4 targets** (guard SG-008/bug-87:
  "uso espera primitivo → null vira default do primitivo"; PN3 medido
  11/09). (b) `println(String? null)` imprime **`null`** (§124).
  (c) `println(ni())` com `Int? ni() { return null }` — MESMO tipo
  `Nullable(INT)` de (a) — crasha JVM/Script e dá `0` no Native.
  O comportamento de fato congelado é (a): **`0`** — escolher `"null"`
  aqui CONTRADIZIRIA o map-miss. §125 = alinhar o caminho (c) ao
  precedente (a): o guard `Nullable(primitivo)→default` vive só na
  lowering de coleções (`JvmOpCollections`/`prevOrDefault`), nunca no
  return de FUNÇÃO de usuário; o println(boxed) do null → boxing
  `valueOf/1` inexistente (Script) e stack int-vs-ref inválida (JVM).
  Correção provável: o guard no `boxPrimitive` do print-lowering
  (Nullable(primitive) com null → default ANTES do box), um ponto só,
  e a célula fecha 4/4 = `0`. Se a mantenedora preferir `"null"` como
  oracle universal de nullable-print, (a) tem de mudar junto — bump +
  migração; registrado nas duas leituras, execução aguarda (condição 1).
- **Prova esperada:** PN2 com o valor do oracle nos 4 targets (célula na
  matriz 4/4) + `KofInterpreterParityTest`; hoje crash/crash/0.
- **Estado medido 12/09 (re-avaliação da mesa de decisões, sem edição de
  código):** (a) `var x: Int? = null` (literal null DIRETO) é REJEITADO pelo
  **SEM048** ("null safety works by narrowing") — logo os ÚNICOS caminhos que
  produzem `Int?`==null observável são: **(b)** `println(mapOf("a",1).get("zz"))`
  → `0` nos 4 targets (caminho runtime, sem descritor de função), e **(c)**
  `Int? ni() { return null }` + `println(ni())` → crash JVM/Script (descritor
  `int` com null → VerifyError — a CAUSA RAIZ já pinada 11/09). (d) `String?`
  null (não-primitivo) → `null` (§124, intocado). O fix do **CRASH (c)** é
  independente do oracle (crash nunca é oracle: boxar o retorno `Int?` em
  `Integer` no JVM = descritor + unbox-guard nos callers; Native/Script
  idem) — mas o VALOR que o println imprime depois (`0` de (b) OU `null`
  estilo (d)) É a decisão da mantenedora que trava a unidade: (A) alinha (c)
  ao precedente (b) = `0` (sem bump, guard de null→default no use-site —
  mesmo `prevOrDefault` do map-miss, estendido ao retorno de função);
  (B) oracle `null` universal de nullable-print → muda (b) de `0` p/ `null`
  (bump + migração + corpus). Com o SEM048, a leitura (A) fica MAIS natural
  (a linguagem já obriga narrowing; o `0` do map-miss é o "default do
  primitivo" documentado SG-008). Registrado aguardando (condição 1).
- **Prioridade:** média-baixa (crash ruidoso; workaround `if (x != null)`
  ou `println(x == null ? "null" : x)`).
- **RESOLUÇÃO 12/09 (opção A da mantenedora — alinhar (c) ao precedente (b)):**
  três pontos no IR compartilhado (todos os 4 targets baixam por aqui — mesma
  forma do §124/§127, um ponto por alvo-alavanca):
  1. `JvmLiteralEmitter.returnOpcode` — desempacota `Nullable(primitivo)`
     para o primitivo. O descritor já o fazia (`JvmTypeMapper.toDescriptor`
     caso `NullableType → inner`); a ASSIMETRIA (descritor `I` + opcode
     `ARETURN`) era o VerifyError de qualquer `Int? f(){...}` — inclusive com
     corpo não-nulo.
  2. `StatementLowerer` caso `ReturnStmt` — `return null` em função
     `Nullable(primitivo)` emite `defaultValueOp` direto (o default do
     primitivo), nunca `aconst_null` + unbox-de-Unknown (o `Object.intValue`
     ilegal que o `emitWideningIfNeeded`/erasure-unbox produzia; e o
     `Integer.valueOf/1` reflectido do interpretador).
  3. `CompilerTypes.defaultValueOp` — default de `Nullable(primitivo)` é o
     default do primitivo (SG-008/bug-87: "null se perde em transitos de
     primitivo"), NUNCA null; `Nullable(ref)` mantém null (§124 intocado).
  4. `TypeMetrics.isDoubleWidth` — desempacota `Nullable` (mesma assimetria
     do (1)): `Long? f()`/`Double? f()` retorna categoria-2 (2 slots) na
     signatura apagada, então descartar seu valor (o fold `f() == null`, ou
     statement `f()`) exige **POP2**, não POP — o `isDoubleWidth(Nullable)`
     cru devolvia false → POP sobre long → VerifyError "long_2nd" (SG-020/
     bug-79 reaparecendo só no wrapper nullable). Prova: `nullableprint`
     estendida com `println(nl()==null)`/`println(nd()==null)` (width 2).
  `null` não-observável em primitivo é o CONTRATO da opção A:
  `ni() == null` é `false` (medido JVM/Native/Script/JS — o fold do
  primitivo-vs-null, mesma regra 44-47 do `==` de primitivo).
  - **Prova:** célula nova `nullableprint` na matriz 4/4 SEM exclusão
    (`0/false/0/6` + `a == null → false` na forma-slot);
    `KofInterpreterParityTest#printNullablePrimitiveNull` (4 paridades);
    suíte completa no HEAD: compiler 1394/0-fail (13 err = `node` ausente,
    ambiente), script 31/0, kof-c 5/0, cli 136/0.
  - **ACHADO COLATERAL (pré-existente, NÃO-§125, registrado como §139):**
    `println(mapOf("a",1).get("zz") == null)` com a chamada DIRETA como
    operando do `==` (sem slot) quebra o parser JS com COMP002 "expression
    stack underflow" no HEAD (medido com `git stash` — bug antigo, a célula
    `wrongkey` usa a forma-slot e por isso nunca pegou). JVM/Native/Script
    aceitam e imprimem `false`. Causa: o fold `KofCall; KofPop;
    KofLoadLiteral` dentro de expressão — o parser JS
    (`JsExpressionParser.parseExpressionFragment:311`) quebra no `KofPop`
    sem nunca ter empilhado nada daquela sub-expressão.

### 126. Chave do TIPO ERRADO em Map/Set/`contains`-de-List pinados → Native SIGSEGV (JVM tolera com miss/false) — ✅ CORRIGIDO 11/09 (decisão da mantenedora: opção ii — SEM056 em compile-time)

- **Matriz medida 11/09 (probes A1/A2/MP2/ST1/ST2/E1):**
  | programa | JVM | Native (hoje) |
  |---|---|---|
  | `mapOf("a",1).get(5)` (Int em String-map) | `0` | ✅ `0` (tag §123 resolve: raw cmpq) |
  | `mapOf(1,"a").get("x")` (String em Int-map) | `null` | ❌ **SIGSEGV** (A2) |
  | `setOf("a","b").add(5)` / `.contains(5)` / `.remove(5)` | `true/false` | ❌ **SIGSEGV** (ST1/ST2) |
  | `listOf("a","b").contains(5)` | `false` | ❌ **SIGSEGV** (E1) |
  | `mapOf(1,"um").put/get(String)` | — | ❌ SIGSEGV (A2 path) |
- **Causa:** onde existe COMPARAÇÃO por conteúdo o tag 0/1 decide String↔raw,
  mas o tag vem do tipo do ARG/elemento, não do conteúdo PINADO: em
  `set.add(5)` num set String o tag sai 1 (String-elem) e o Int cru vira
  ponteiro no `kof_string_equals`; no Map, o caso simétrico. JVM usa
  HashMap/HashSet reais (equals por classe → miss silencioso).
- **Fix (duas metades, mesma família SEM055):**
  (a) **Map:** o tag do native deve vir do **keyType do receptor**
      (pinning já garante concreto), não do arg → A2 vira `raw cmpq` e
      dá `null` como o JVM (seguro, sem rejeição). O emitter já lê
      `kc.parameterTypes().get(0)` — mudar para o keyType do `ownerType`
      no lowering (o call carrega Map<K,V> no ownerType).
  (b) **Set/List-contains:** String-pinned + arg conhecido NÃO-String →
      **SEM056** (rejeitar; análogo §122; Unknown passa). Int-pinned + arg
      String: raw-cmp é SEGURO (sem deref) → pode devolver false/null
      como o JVM sem rejeição. Não rejeitar o que é apenas "miss".
- **Prova esperada:** A1/A2/MP2/ST1/ST2/E1 viram células 4/4 (com SEM056
  onde rejeição; null/false onde miss) + `listIndexNonIntRejected`-style
  no SemanticResolutionTest.
- **◐ PARCIAL (11/09, lado ARG corrigido):** o tag do Native (map: header
  off 40 escrito pelo emitter x86+riscv; set/list: literal no lowering) é
  agora **CONJUNÇÃO elem-receptor × tipo-do-arg**: `1` (kof_string_equals)
  só quando AMBOS conhecidos String; qualquer outro par → `0` raw cmpq,
  que nunca deref e produz exatamente o miss do JVM. Provas: E2 e célula
  `wrongkey` 4/4 (String-arg em Int-map → `null`; Int-arg em String-set →
  `false`; Int-arg em String-list-contains → `false`; Int em String-map →
  `0`, como o JVM); A1/A2/MP2/ST1/ST2/E1 nativos ec=0; zero regressão
  (String-String continua no equals de conteúdo — mapa/células `map`/
  `mapint`/`set` verdes). **Prova CROSS dedicada 12/09 (qemu real, golden
  JVM):** `riscv64MapKeyTagCross` + `aarch64MapKeyTagCross` exercitam
  justamente o miss-seguro do lado-arg no riscv/aarch (String-arg em
  `mapOf(1,..)` → `null`; Int-arg em `setOf("a","b")` → `false`; Int-arg em
  `mapOf("k",7)` → `0`) — Skipped=0, os 2 E2E rodam de fato. SEM rejeição: o reject em alvo único seria
  fallback silencioso proibido, e em todos os-alvos mudaria comportamento
  que roda hoje no JVM (regra 2).
- **✅ LADO CANDIDATO CORRIGIDO (11/09, decisão da mantenedora = opção ii,
  "Kof estático"):** SEM056 em compile-time rejeitando a ESCRITA que polui o
  container pinado (família SEM055/§122). Medido antes de decidir: `List.add`
  heterogêneo JÁ crasha no JVM (VerifyError na carga); `List.set` com valor
  errado idem; `Map.put` com valor errado → ClassCastException no get/unbox;
  só Set.add/Map.put-chave são tolerados pelo JVM — mas SIGSEGVam no Native.
  A rejeição é universal (erro de tipo é erro de tipo em todo alvo — rejeitar
  só no Native seria o "fallback silencioso por alvo" proibido, e aceitar só
  no JVM manteria o SIGSEGV). `pollutesPinned` (CollectionCallLowerer) é
  CIRÚRGICO: rejeita só quando AMBOS os lados são conhecidos, concretos e
  divergem na família String↔não-String (é exatamente o par que vira tag=1
  sobre um Int). Passam: widening numérico (Int→Long, §121), Unknown/
  TypeVariable (SG-008), query-side (get/contains/remove-procura → miss
  seguro do lado ARG), o add que PINA um List<Unknown> (define o tipo, não
  polui), e Map.put com VALUE em mapa de valor Unknown. Sítios: `List.add/
  set(valor)`, `Set.add`, `Map.put(chave|valor)`. **Prova:**
  `SemanticResolutionTest.heterogeneousWriteToPinnedCollectionRejected`
  (7 programas, todos SEM056 com dica "coleções Kof são homogêneas") +
  `querySideAndWideningNotRejected` (o que NÃO deve regredir) + Conformance
  Matrix intacta 11/11 (wrongkey/mapint/set continuam 4/4 — o query-side não
  mudou). Suíte: 1363/0 na lane (+2 testes novos; as 2 falhas + 12 errors
  são as pré-existentes de sempre — node ausente, §128, SEM047).
  **MUDANÇA DE CONTRATO deliberada (regra 1, única exceção):** código que
  HOJE roda no JVM (`setOf("a").add(5)` → size 2) passa a NÃO compilar.
  Justificativa: a alternativa era manter SIGSEGV no Native (imprestável) ou
  guard de arena no asm dos 3 targets + port riscv (complexidade alta,
  comportamento "comporta lixo"); a mantenedora escolheu a linha estática.
  Bump de versão: o 0.4.0 ainda é beta — a breaking-change entra na própria
  linha 0.4.0 (não exige release anterior estável).


### 120. Tradutor riscv→aarch64: `fcvt.w/l.{s,d}` (FP→INT) traduzido como `scvtf` (direção INVERTIDA) — ✅ CORRIGIDO 11/09 (`fcvtzs`)  *(renumerado de §104 na reconciliação do merge 11/09 — colidiu com o record-equals §104 da série ativa)*

- **Sintoma (achado 11/09 ao portar MATH001):** `var e = 2.5; println((e * 2.0) as Int)`
  dava `0` no aarch64 (riscv/x86/JVM = `5`). Qualquer `Double as Int`/`as Long`
  no aarch dava lixo (o D2I do cross-emit emite exatamente `fcvt.w.d t0, f0, rtz`).
- **Causa raiz:** o ramo `fcvt.{w,l}.{s,d}` do `NativeAarch64Translator` emitia
  `scvtf` (INT→FP, a direção OPOSTA), lendo o registrador FP como se fosse
  inteiro — ex.: `fcvt.w.d t0, f0` → `scvtf d0, f0` (src FP inválido; `as
  Int` virava 0/sujeira). O caso int→float correto vive no ramo irmão
  (`parts[1]=d`, `fcvt.d.l/w` — bug 82) e permanece.
- **Fix (mínimo, impeditivo MATH001 — `isInteger` precisa de trunc FP→int):**
  `fcvt.w/l.s/d` → `fcvtzs w/x, s/d` (truncate toward zero == `rtz` do riscv
  == `cvttsd2si` do x86; paridade preservada). `fsqrt.d` ganhou ramo próprio
  (`fsqrt d/s`) — era UNHANDLED pass-through (montava as riscv mas travava o
  aarch; impeditivo p/ `math.sqrt` cross).
- **Prova:** E2E cross `nativeMathDoubleSeries` (riscv/aarch, golden JVM
  medido) + sonda `Double as Int` aarch (0→`5`) + suíte completa.


### 127. JVM: cast para tipo de função (`x as () -> Int`) gera bytecode inválido (VerifyError) — 🔴 ABERTO (achado no spike OTP #83 11/09)
- **Reprodução:** `var o: Object = (Object)(() -> 5)`… em Kof puro:
  `fun(Int x) { var g = x as () -> Int; return g() }` — `fun(() -> 9)` →
  **compila ok** mas ao rodar: `VerifyError: Operand stack underflow` /
  `checkcast // class "?"` (checkcast para classe INEXISTENTE — o tipo de
  função não tem erasure mapeado no cast).
- **Menor repro:** `main(){ var l = listOf(() -> 5); var g = l.get(0) as () -> Int; println(g()==5) }` (out_B107).
- **Impacto OTP:** DD-OTP-02 propunha `child(id, factory, ...)` com `factory`
  como tipo de função. O cast `as () -> T` está quebrado no JVM, então a forma
  "Object/qualquer + cast p/ função" NÃO é utilizável hoje.
- **Workaround verificado (forma que RODA nos targets):** usar **interface** como
  contrato da fábrica (DD-OTP-06 "factory nova sempre"): `interface Worker { Int
  criar() }` + `class W implements Worker { criar(){...} }` — dispatch virtual de
  interface funciona nos 5 targets (spike S2/S4: supervisor puro-Kof com campo
  `Worker` + `spawn { w.criar() }` + try/await/catch = captura/limit/restart tudo
  verde no JVM). O campo tipado como `() -> Int` dá PARSE016 (parser de corpo de
  classe não aceita LPAREN como início de campo — `ClassMemberParser`), e como
  `Object`+cast dá este §127.
- **Por que NÃO corrigi agora:** consertar o erasure do cast de tipo-função no
  `JvmTypeMapper` é mudança de infraestrutura de tipos (afeta `mapOf<String,
  ()->T>` etc.) — fora do escopo OTP (a interface resolve o caso de uso do
  supervisor). Registrado como pré-requisito se um dia a API quiser closure como
  tipo-valor declarado.


### 128. JVM: resultado de `selectAny`/`await` de Handle<Int> atribuído a var e usado como Int → VerifyError — ✅ CORRIGIDO 12/09 (JVM; await já caía no unbox, selectAny não) (spike OTP #83 11/09)
- **Correção (12/09, lane bugfix):** em `JvmOpCollections.emitRuntimeCall`, o
  ramo que chama `emitUnboxIfPrimitive` para `kof_await`/`kof_await_timeout` com
  retorno primitivo foi estendido a `kof_select_any` (mesmo destino `Object` do
  runtime, mesma assimetria). Uma condição. Typer já devolvia o inner `Int`
  (`BuiltinCallTyper:304`), então `isPrimitiveType(kc.returnType())` casa e o
  `Integer.intValue()` entra antes do `istore` — o VerifyError desaparece.
  **Prova:** `KofConcurrency2Test#selectAnyPrimitiveJvm` (spawn de `Int`,
  `selectAny(a,b)+1` → `8`) — vermelho antes (VerifyError), verde depois;
  `selectAnyJvm` (String) e `await`+aritmética intactos (sem regressão);
  classe 33/0 (1 skip). Paridade medida: JVM `8`==Native `8`==JS `8`. Script
  é outro caminho (interp `KofInterpreterConcurrency:111`, sem descritor).
- **Menor repro:** `Int um(){return 1}; Int dois(){return 2}; main(){ var a=spawn
  um(); var b=spawn dois(); var v=selectAny(a,b); println(v==1||v==2) }` →
  compila ok, roda: `VerifyError: Bad type on operand stack … Type
  'java/lang/Object' is not assignable to integer` no `istore` do resultado de
  `kof_select_any` (que retorna `Object`).
- **Causa:** o typer sabe o elemento (`selectAny`→typeArg do Handle,
  `BuiltinCallTyper:304`), mas o lowerer emite `kof_select_any`→`Object` e **não
  insere o unbox** (`Integer.intValue`) quando o destino é primitivo. `await h`
  de Handle<Int> seguido de uso Int (`var v=await h; v==1`) NÃO trava no JVM
  (B108/A1 verdinhos), mas `selectAny` sim. Divergência entre os dois builtins no
  mesmo lowerer.
- **Impacto OTP:** DD-OTP-03 propunha `selectAny(handles)` como coração do
  supervisor N-workers. No JVM o resultado primitivo é inutilizável hoje.
  Contorno do spike: supervisor JVM usa `poll`/`done` por filho no laço (verificados
  — `KofConcurrency2Test`) OU um `await` por filho com a thread supervisora por
  worker; a decisão final depende de fechar este §128 ou fixar o unbox.
- **Fix mínimo provável (NÃO aplicado — fora do escopo da unidade, para a lane
  CONC/native):** no `ExpressionStaticCallLowerer` ramo `selectAny`, inserir o
  mesmo unbox que `await` já faz quando o tipo-destino é primitivo. Reproduzível
  e pequeno; mas como `await h` de Int já funciona, a assimetria é só de
  `selectAny` → deixo para o dono da lane CONC (regra dos bugs não-impeditivos: o
  supervisor consegue viver sem selectAny no núcleo 1ª fatia).


### 129. Native x86_64: `throw` dentro de worker `spawn` → unwinder faz longjmp no handler da THREAD MAIN (crash/hang cross-thread) — 🔴 ABERTO (impeditivo do OTP no Native; spike #83 11/09, evidência GDB)
- **Reprodução (M2, deterministicamente travado/crashado no native):**
  `main(){ var i=0; while(i<3){ var h=spawn { throw "x" }; try { await h }
  catch(String e){println("cap")} i=i+1 } println("fim3") }` → imprime
  `cap 0 cap 1 cap 2 fim3` e o **processo nunca sai** (exit 124 no timeout).
  Sem try no worker (V1/V3: `spawn { 5 }` + await, sem throw) → sai limpo.
- **Evidência (GDB, não hipótese):** sob timing variável o processo dá SIGSEGV
  com `rip=0x0` na main **e** a thread-3 (worker) aparece com stack frames de
  `kof_spawn_handle_new` nos endereços de STACK DA MAIN (`0x7fffffffd580`,
  `0x7fffffffd5a0` = locals do `main`). I.e.: o `throw` do worker não
  encapsula a falha na thread do worker — o **handler chain global
  (`kof_exc_chain`, bss compartilhada entre threads — `NativeMethodEmitter:238`)
  faz o longjmp do worker saltar para o handler `try` registrado pela main**
  (o `try { await h }` da main), corrompendo o stack da main / deixando a
  task sem join → o epílogo `kof_spawn_join_all` pendura ou a main já se foi.
- **Comportamento PREVISTO (JVM/interpretador/JS):** `spawn { throw }` faz a
  task completar excepcionalmente; `await` na main re-lança a causa no
  CONSUMIDOR (JvmRuntimeCore:202 / KofInterpreterConcurrency:172). A task que
  falha NUNCA executa código na thread da main. O Native diverge (regra 5 —
  paridade cross-target).
- **Impacto OTP (por que é impeditivo, não "bug alheio adiado"):** o núcleo do
  supervisor #83 É "worker falha → supervisor observa → reinicia". Na forma
  puro-Kof (spawn+await+try/catch, que é a recomendada no DD-OTP-01-A), isso
  cai exatamente no caminho do §129 → no Native o supervisor crasha/hanga ao
  reiniciar UM worker que lança. Sem resolver §129, o gate de paridade da
  feature (E2E nos nativos) é inalcançável — seria `OTP001` no Native (gap
  honesto R6), não implementação.
- **Não corrigi nesta sessão (motivo):** o fix exige dar ao unwinder do Native
  **isolamento por thread** de `kof_exc_chain` (chain thread-local, como o
  `cancelled()` por TID já é) + garantir que um `throw` sem handler no worker
  marque o handle como excepcionalmente-completo em vez de longjmpar para fora
  da thread. É mudança do MECANISMO de exceção no Native (não um bug pontual) —
  superfície congelada-adjacente (regra 6/§). Precisa de decisão da mantenedora
  sobre a convenção de unwind no Native (chain TLS por TID vs frame por thread).
  Escopo grande (afeta RuntimeDb4/Gc que compartilham o chain). **Ação:**
  registrar §129 + na 1ª fatia OTP, o gate Native é `OTP001` honesto (R6) até
  §129 fechado; JVM+Script+JS entregam o núcleo.


### 130. Frontend: re-análise do corpo de método no mesmo escopo → SEM024 falso ("variable already defined") — ✅ CORRIGIDO 11/09 (impeditivo do host OTP puro-Kof)
- **Sintoma:** classe cujo método **sem tipo de retorno declarado** termina em
  `return <expr>` (ou **chama outro método da mesma classe** que faz isso) →
  `SEM024: variable 'q' is already defined in this scope` apontando para um `var`
  que aparece UMA só vez no corpo. Bypassava todo construtor/builder encadeado
  (`child(id,f)` → `return child(id,f,pol)`), forma canônica de API fluente.
- **Menor repro (título `S18`):** `class C { a(Int x) { var q = x; return q } }`
  → SEM024 em `q`. Com `Int a(Int x)` (tipo declarado) → compila. Duas funções
  de topo com o mesmo `var q` → compila (não é colisão entre unidades).
- **Causa raiz (`SemanticAnalyzer`):** o corpo de método/constructor passa por um
  **laço de 4 passes** para inferir return-type (bug 26). No pass 1 o
  `analyzeBody` `define` cada `var` no `methodScope` (IdentityHashMap criado em
  `SymbolTableBuilder.defineMethodSymbol`). Quando um `return <expr>` num método
  void dispara a reinferência (`ms.setReturnType`, l.257-263), `changed=true` e o
  laço **re-executa `analyzeBody` no MESMO `methodScope`** → o SC5
  (`scope.hasLocal`, `StatementAnalyzer:136`) reclama de cada `var` do pass
  anterior. Por isso a suíte estava verde: todo o corpus usa tipos declarados.
- **Fix (mínimo, sem tocar semântica):** cada análise de corpo ganha um
  **escopo-filho** (`methodScope.enterScope()` / `ctorScope.enterScope()`) em
  `analyzeMethodBody`/`analyzeConstructorBody`. `resolve()` anda pai-acima, então
  params/`this`/campos continuam visíveis; apenas os `var`s deixam de vazar entre
  passes (o pinning de tipo SG-008 é por-pass e o codegen lê `expressionTypes`,
  não estes escopos — verificado: nenhum leitor de `methodScopes()`/`ctorScopes()`
  fora do próprio builder). Redeclaração genuína **no mesmo corpo** continua
  SEM024 (coberto pelo teste).
- **Prova:** `SemanticResolutionTest#redeclarationFalsePositiveEmMetodoDeClasse`
  (repro + run com valor encadeado correto `total==3`) e
  `#redeclaracaoMesmoCorpoAindaErro` (borda SC5); os 5 mini-repro do spike
  (T15/T16/T18/T19/T21) viram `ok=true`; bloco `*Class*,*Method*,*Semantic*,*E2E*`
  = 679/0.
- **Por que corrigi (regra dos bugs impeditivos):** o núcleo OTP #83 na forma
  **puro-Kof** (DD-OTP-01-A, recomendada no plano) exige API fluente com método
  sem tipo declarado encadeando `return`; §130 travava a compilação do host no
  passo ZERO. É impeditivo direto da unidade assumida, não auditoria geral.


### 131. Frontend/Backend: sobrecarga de método por ARIDADE na mesma classe quebra (SEM013 no JVM; colisão de símbolo no NATIVE) — 🔴 ABERTO (achado no spike OTP #83 11/09; contornado)
- **Reprodução:** `class B { Int m(Int a){ return this.m(a,1) } Int m(Int a, Int
  b){ return a+b } }` → no JVM: `SEM013: Wrong number of arguments for 'm':
  expected 2 but got 1` na chamada `b.m(5)` — a seleção de overload ignora o
  1º método e só "vê" o último definido. Default de parâmetro (`m(Int a, String
  p = "def")`) também não existe no parser. No NATIVE: quando a resolução passa,
  a emissão colide (`as: symbol 'Supervisor_child' is already defined`).
- **Causa provável (JVM):** `defineMethodSymbol` faz `classScope.define(methodSym)`
  sobreescrevendo o `Symbol` homônimo (um slot por NOME, não por assinatura) →
  só a última aridade sobrevive no mapa de membros. NÃO investigado a fundo
  (fora do escopo da unidade — workaround adotado).
- **Contorno no host OTP:** API usa **assinatura única** `child(id, fabrica,
  politica)` (sem overload `child/2`). Zero impacto no caso de uso.
- **Por que NÃO corrigi:** seleção de overload é mudança na **resolução de
  membros** (afeta toda dispatch, testada por centenas de casos). Impeditivo?
  NÃO — contornado com 1 assinatura. Deixado para a lane de tipos/overload com
  este menor repro.
- **Nota (12/09, §136):** a parte **top-level** da sobrecarga foi fechada na
  unidade SG-011B (símbolo sufixado por assinatura no Native). Isto NÃO resolve
  sobrecarga de **método de classe** (o `class B { m/1; m/2 }` acima): a causa
  é outra (`defineMethodSymbol` sobreescreve o Symbol homônimo na symtable da
  classe + vtable por índice), e o `as: symbol 'Supervisor_child' is already
  defined` ali é `<init>`/método, não função top-level. Continua ABERTO.


### 132. KofJS: task spawnada DE DENTRO de outra task nunca roda sem ceder o event-loop (worker do supervisor nunca dispara) — 🔴 ABERTO (impeditivo JS do OTP #83; gate OTP002 aplicado)
- **Reprodução (host_u1, target JS, node v20):** `main(){ var h = spawn { 42 }; var t=0; while(t<50 && !done(h)){ time.sleep(10); t=t+1 }; println(done(h)) }` → `done=false` sempre; e no supervisor: `spawn { self.vigiar(n) }` (thread supervisora) faz `n.h = spawn { w.run() }` de DENTRO da task `vigiar` — a fábrica NUNCA é chamada (`fabrica=0`; no JVM a mesma saída é `fabrica=3`).
- **Causa:** o backend JS é single-thread (modelo de event-loop; `kofSpawnResult` cria Promise). Um `await`/loop numa task agenda continuations como micro/macro-tasks — mas o `time.sleep` do backend JS é síncrono/busy-wait (fila cooperativa de timers, TIME001) e **cede o loop só para timers, não para as promises pendentes da task-mãe** no ponto do `while(!done)` — e o `spawn` filho dentro de uma task-filha pode nunca ser agendado enquanto a mãe segura o loop. `done(h)` num handle rejeitado também reportou `false` no probe J1 (reject marca `done=true` no `.catch` do runtime, mas a visibilidade ao laço spin depende de ceder — mesmo sintoma raiz).
- **Por que NÃO corrigi:** consertar = redesenhar `time.sleep` JS para ceder o loop (await-style) OU exigir CPS no lowering — mudança de **contrato de execução do backend JS** (concorrência single-thread é decisão documentada, regra 6). Não é mineira nem impeditiva para a UNIDADE: a issue #83 pediu "o menor núcleo funcional"; o gate honesto (OTP002 em compile-time, R6) está aplicado e testado (`KofSupervisorE2ETest#jsGateOtp002`).
- **Prova do contorno:** mesma semântica verde em JVM e no interpretador (KofScript) via o MESMO frontend + MESMO host .kf — paridade por construção nos dois alvos; os demais bloqueados com diagnóstico, nunca silêncio.

### 133. KofJS (Node/browser): `http.*` sem interop Java devolvia `""` silencioso; fetch era stub — ✅ CORRIGIDO 11/09
- **Menor repro (node v20):** `main(){ println(http.get("http://127.0.0.1:P/x")) }`
  → imprimia linha VAZIA (sem erro); `spawn http.get(...)` + `await h` idem → o
  supervisor/consumidor lia `""` como "resposta vazia" legítima (R6-violation:
  silêncio onde deveria haver transporte ou erro).
- **Causa:** `JsRuntimeUiLayout.kofHttpRequest` só implementava o caminho
  síncrono `Java.type('java.net.http.HttpClient')` (funciona no GraalJS
  embutido — `KofJsRunner`); no Node/browser o fallback tinha o comentário
  literal "synchronous fallback not possible … return empty" e `return ""`.
  O fetch real nunca existiu. `kofHttpStatus` idem (catch → `return 0`).
- **Por que a face era "impossível":** HTTP é inherently async em JS; a API
  `http.get(...)` é síncrona por contrato nos outros targets. Resolver
  async→sync no thread principal não existe (Atomics.wait trava o próprio loop).
- **Fix:** fallback Node/browser agora usa `fetch` REAL e devolve **Promise**
  (headers `\n`-split, `AbortController` com `http.timeout(sec)`, ≥500 →
  falha + circuit-record, como o ramo Java). `kofSpawnResult` já roda
  `task.invoke()` dentro de `Promise.resolve().then(...)` — a Promise do fetch
  encadeia NATURALMENTE e `await h` resolve o corpo: **`spawn http.get(url)`
  + `await` vira a forma assíncrona idiomática no JS** (zero mudança de AST,
  a máquina Handle<T> existente é o carrier). `await http.get(...)` de valor
  já-resolvido continua ok; o `await` do `main` JS precisa ceder o loop (§132 —
  task-de-TASK segue gateada; aqui não há task-de-task: o fetch é o proprio
  worker do handle). Face SÍNCRONA no Node fica honesta: `http.get(...)` sem
  spawn/await devolve o Promise cru (`[object Promise]` no println) — NÃO
  retrói para `""`; registro HTTP004 na matriz de paridade como borda do
  backend (sync http não existe em JS puro; a linguagem entrega async).
  `kofHttpStatus` ganhou fallback HEAD via fetch (Promise→status).
  Caminho GraalJS (KofJsRunner) INTACTO — `jsHttpGet`/`jsHttpServerRoundtrip`
  continuam síncronos e verdes.
- **Prova:** `KofHttpE2ETest.jsNodeSpawnAwaitHttpResolvesBody` (node v20;
  `spawn get` + `spawn post` + awaits → `"Hello from Kof|got:xyz"` byte-a-byte;
  sabotagem do fallback → fail) + harness medindo `spawn/await/selectAny` no
  Node (`ola-async|ola-async`, `any=true`) e status `200` via `spawn`+`await`.

### 134. External classpath quebrado: `--classpath`/`--deps` com pacote FORA da whitelist (gson, postgres, lib interna) → import virava PKG006; chamada estática externa (jar legítimo) era rejeitada — ✅ CORRIGIDO 11/09 (bugbugfix 0.3.1→0.4.x)

- **Relato:** "a 0.3.7 quebrou external classpath". A data do relato está
  errada; a regressão é ANTIGA: o commit `e7005c69` (Fase 1 — PKG006,
  07/09) já é ancestral da tag `kof-0.3.1-beta`, então TODO release de
  0.3.1 em diante (incluindo a 0.3.7) quebra o caso. O `git log` da 0.3.7
  inteira (2 commits: #69 if/switch-underflow) NÃO toca classpath — a
  0.3.7 não quebrou nada; ela apenas HERDOU a quebra da Fase 1.
- **Menor repro (medido 11/09):** `jar` com `ext/Greeter.class` (pacote
  FORA da whitelist de prefixos) + `--classpath` apontando pra ele +
  `import ext.Greeter; Greeter.hello("mel")` → `:0:0: error: import
  'ext.Greeter' não encontrado no módulo [...] [PKG006]`,
  `success=false`. Antes da Fase 1: import era SILENCIOSAMENTE ignorado
  (`// import externo (android.* etc.) — ignora`) e a chamada resolve via
  lowering; com o commit, o import é REJEITADO antes de qualquer resolução.
- **Causa raiz (camada 1 — PKG006):** `CompilerImports.expandKofImports`
  (linha ~152) decide `!isExternalImport(imp)` → PKG006. `isExternalImport`
  (linha ~244) é uma lista FIXA de prefixos (`kof./android./androidx./java./
  jakarta./javax./kotlin./scala.`) que NÃO consulta `ExternalClasspath`.
  Um jar `--classpath` com `com.google.gson`/`org.postgresql`/`ext.*` (qualquer
  pacote fora da lista) apanha PKG006 mesmo com o `ExternalClasspath` tendo
  carregado a classe. Prova: `ExternalClasspath.knows("ext/Greeter")=true`
  + `resolveMethod("ext/Greeter","hello",1)` retornando assinatura — a única
  barreira era o gate do import, não a resolução.
- **Causa raiz (camada 2 — SEM011 no receiver externo):** com o gate
  afrouxado, `Greeter.hello("mel")` (identifier como receiver) ainda
  morria em `SemExpressionTyper.inferTypeInternal` (~linha 175): o bloco
  de "undefined variable/type" só isenta names builtins + namespaces stdlib;
  o receiver de uma classe externa importada (identifier) não passa por
  `ClassInstanceExpr` nem por `FieldAccessExpr` (que têm `knows(ct)`) — ele
  chega aqui como `IdentifierExpr` puro. `ExpressionMethodCallLowerer`
  resolve correctly (camada 3, que já estava pronta), MAS a análise semântica
  rodava antes e bloqueava. Prova: `java.lang.Integer.toString(5)` (mesmo
  whitelisted!) → `SEM011 Undefined variable or type: 'Integer'` no HEAD
  (a forma `new ArrayList<String>()`/`lista.get(0)` do learn/21 é FieldAccess
  e passa; o caminho estático-via-import é o que quebrava).
- **Correção (mínima, target-aware):** (i) `ExternalClasspath` ganha
  `knowsImport(String dotted, boolean wildcard)` que consulta os entries
  carregados (prefixo do dir interno p/ `ext.*`, chave exata p/ `ext.Greeter`).
  (ii) `CompilerImports.expandKofImports` recebe o `ExternalClasspath`
  (sobrecarga nova; a antiga passa `null` p/ compat) e o PKG006 agora é
  `!isExternalImport(imp) && (extCp == null || !extCp.knowsImport(imp,
  wildcard))` — o import de dependência real deixa de ser rejeitado.
  (iii) `SemExpressionTyper` isenta SEM011 quando o identifier é uma
  classe externa importada presente nos entries (`isExternalImportedClass`
  → `qualifyViaImports` + `externalTypes().knows(internalName)`), espelhando
  o `knows(ct)` que FieldAccess/`new`/assignment já têm. (iv) **Target-aware:**
  o gate de entries só vale em `Target.JVM`/`ANDROID` (interop .class JVM só
  existe nos targets JVM-family). Em NATIVE/JS, passa `null` → PKG006
  honesto (R6): sem isso eu introduziria uma NOVA regressão — JS emitia
  `ext_Greeter` pendurado com `success=true` (NoClassDefFound em runtime) e
  NATIVE só falhava no LINK (`undefined reference to 'ext_Greeter_hello'`),
  não em compile-time. `driver.externalClasspath` só chega ao import gate
  nos targets onde faz sentido.
- **Escopo honesto:** ~~NÃO resolvo wildcard `import ext.*`~~ — **RESOLVIDO
  12/09 (esta linha, JVM/ANDROID):** o wildcard agora qualifica o nome simples
  quando a classe EXISTE num entry carregado — `MemberResolver.qualifyViaImports`
  e o espelho `CompilerTypes.qualifyViaImports`/`toType` ganharam overload com
  `ExternalClasspath`; os sítios com contexto (receiver estático
  `MemberCallTyper:53`, identificador `SemExpressionTyper.isExternalImportedClass`,
  `new` `SemExpressionTyper:329`+`ExpressionLowerer:139`, type-anotação
  `StatementAnalyzer:128`) passam `sa.externalTypes()`/`driver.externalClasspath`.
  Aditivo e target-aware: sem cp (NATIVE/JS) o wildcard segue PKG006/SEM011
  (não vaza); nome que NÃO existe no jar continua SEM011 (R6, testado). Edge
  NÃO coberto (raro, fica aberto): `extends` de classe externa POR WILDCARD
  (`SymbolTableBuilder:22` não plumbado) e anotação `@` com wildcard
  (`CompilerAnnotations:38`) — ambos funcionam com import pontual. A whitelist
  de prefixos continua cobrindo `java.*`/`android.*` sem precisar de jar. Não
  conserto o `Integer.toString` sem-import (caminho `java.lang` implícito é
  outro gap — documentado em §134-adjacente como "qualquer classe java.*
  precisa do import"). NÃO toco `--classpath` (funciona) nem `--deps` (mesmo
  caminho, coberto pelo fix).
- **Prova:** `ExternalClasspathE2ETest` (6/6): (1) chamada ESTÁTICA em
  classe externa (o caso do bug) → compila + roda `hi mel`; (2) caminho
  `new`/instância continua verde; (3) import fora do classpath → PKG006
  (R6: não virou silêncio); (4) NATIVE e JS com o MESMO jar → PKG006
  (paridade honesta cross-target); (5) WILDCARD `import ext.*` estática →
  roda `hi mel`; (6) WILDCARD nome inexistente → SEM011 (não-bypass).
  Medido fora da suíte: var anotada + construtor + método de instância via
  wildcard (`var p: Point2 = new Point2(42); p.getX()` → `42`). Suíte: 1361 run / 0 falha na lane (12
  err = node ausente neste host = trio pre-existente; 1 fail =
  `CompilerDriverTest#duplicateTopLevelFunctionFails` SEM047, pré-existente
  no HEAD `a95ffa49` — verificado com stash, NÃO é desta unidade).


### 135. CONFLITO DE CONTRATO (não é bug de código — é regra 6): `duplicateTopLevelFunctionFails` SEM047 vs SG-011B sobrecarga — ✅ RESOLVIDO 12/09 (opção 1 RATIFICADA pela mantenedora na sessão de 11/09)

**Medido 12/09** (suíte do HEAD `eae16c46`, toolchain cross ativa): 1367+31+5+136 testes,
**2 falhas**, uma delas esta. As duas são PRÉ-EXISTENTES às merges desta sessão
(gate CONC001 `0bf899a3` + indent/dedent `c14c1808` — reproduzidas no base sem
os patches).

**O choque, nos fatos:**

- `40abd0ed` (09/09, **decisão explícita da mantenedora**): *"top-level sem
  overload"* — duas funções homônimas = SEM047 em compile-time. Prova: o teste
  `CompilerDriverTest.duplicateTopLevelFunctionFails` ("overload top-level deve
  falhar"). Suíte da época: 1238/0.
- `b55c24c0` (11/09, "implement top-level function overloading resolution",
  SG-011B): **inverte o contrato** — seleção por assinatura via
  `TopLevelOverload.pick` nos 4 frontends (typer/analyzer/lowerer). O teste de
  09/09 NÃO foi atualizado nem removido, o doc SEM047/specification-gaps não
  menciona a mudança, e não houve bump de versão. Consequência: o teste que
  era a PROVA da decisão da mantenedora agora FALHA — a suíte está vermelha no
  `beta-0.4.0` (e estava antes desta sessão).

**Menor repro (JVM, 2 decls):**

```kof
Int f(Int x) { return x }
Int f(String x) { return 0 }
main() { println(f(1)) }
```

09/09 esperava: erro SEM047 nomeando o conflito. Hoje: compila e roda
(`1`). O teste do repo (`duplicateTopLevelFunctionFails`) ainda espera o erro.

**Por que não corrijo (nem do lado A nem do B):** mudança de contrato
congelado é decisão da mantenedora (regra 6). Os dois lados têm prova e
intenção documentada. O que PRECISA acontecer, qualquer que seja o lado:

1. **Se sobrecarga vence (SG-011B ratificado):** atualizar/remover
   `duplicateTopLevelFunctionFails` (deixa de ser contrato), registrar a
   inversão em `specification-gaps.md`/`AGENTS.md` (SEM047 → semântica nova),
   bump de versão (contrato de 09/09 era explícito: "erro de compilação"),
   e fechar o gap §131 correlato (aridade na MESMA classe ainda quebra).
2. **Se SEM047 vence (decisão de 09/09 restaurada):** reverter o
   `TopLevelOverload` do frontend (b55c24c0) a erro, e mover a feature p/
   `planning-*` — o OTP (§131) não depende dela.

**RESOLUÇÃO (12/09, commit `84794127` — opção 1):** a mantenedora ratificou a
sobrecarga nesta sessão com a diretiva do **oráculo JVM** ("assuma o padrão JVM e
replique nos outros 4; a semântica tem que ser a MESMA nos 5") — a JVM permite
sobrecarga top-level, então o contrato de 09/09 ("não existe overload") foi
emitido antes dessa diretriz e está **substituído** por ela. Checklist da opção 1
fechado: (1) `duplicateTopLevelFunctionFails` substituído pelos 3 testes do
contrato novo (`distinctSignatureOverloadCompiles` + `duplicateExactSignatureFails`
+ `returnOnlyCollisionFails`); (2) inversão registrada em `specification-gaps.md`
(SG-011B → APLICADO) e em `AGENTS.md` (lista congelada + tabela SEM047); (3) bump
NÃO se aplica — o contrato de 09/09 viveu só nesta branch dev, nunca foi
liberado em tag de release (a última é `kof-0.3.1-beta`); a 0.4.0-beta nasce com
a semântica nova e documentada; (4) §131 (método de classe) fica ABERTO com
nota de alcance na §136. Paridade 6/6 provada por `TopLevelOverloadE2ETest`
(JVM/Script/JS/x86/riscv64/aarch64 sob qemu + especificidade por subtipagem).

Terceira falha correlata no MESMO HEAD, também pré-existente e de lane
alheia: `DecompileTest.recoversStatementSwitchAndRunsIt` (nascida em
`487287fb` "switch recovery" — o decompiler do CLI não recovery-ou o
statement-switch na mesma taxa). Reprodução no próprio teste (kof-cli).
**FECHADA (§137, abaixo)** — corrigida na lane migração/bugfix (`982f53f0`) antes da unidade dev tocar; confirmada verde no gate 12/09.
### 136. Native/JS/Script: sobrecarga top-level e wrapper de default colidiam no símbolo único (as: `symbol is already defined`; JS: `SyntaxError: Identifier already declared`) — ✅ CORRIGIDO 11/09 (unidade SG-011B)
- **Menor repro (nativo x86_64, medido 11/09):** `Int d(Int x, Int y = 2) {
  return x + y }` → o lowering de default gera o wrapper `d/1` com o MESMO
  símbolo asm do canônico `d/2` (`Default_Main_d`) → `as` falha
  (`symbol Default_Main_d is already defined`). ANTES da SG-011B a colisão só
  aparecia com defaults; com a sobrecarga liberada no frontend (assinaturas
  diferentes coexistem), dois candidatos de mesma aridade (`twice(String)` /
  `twice(Int)`) colidiam também no JS (node: `SyntaxError: Identifier 'twice'
  has already been declared` — compilava e MORRIA em runtime, R6-violation) e
  no interpretador (dispatch por nome+aridade escolhia o candidato errado em
  silêncio — `twice(21)` chamava a versão String).
- **Causa raiz (comum aos 3):** os backends nomeiam símbolos por NOME (+
  aridade p/ `<init>`), nunca por ASSINATURA — inofensivo enquanto o frontend
  garantia 1 candidato por nome (SEM047 antigo); liberada a sobrecarga, o
  nome deixa de ser chave única.
- **Correção (oracle JVM em todos):** chave de símbolo = assinatura, calculada
  no MESMO lugar da seleção (frontend) e replicada por tag determinística
  (`TopLevelOverload.sigTag`): Native = `.globl Default_Main_d_I_I` /
  `..._g_I` (definição, pré-registro de forward-ref, call-site
  `resolveCalleeName` x86+riscv, vtable do recipiente Main — aarch herda do
  tradutor); JS = nome `twice$I`/`twice$Ljava_lang_String` (só quando ≥2
  assinaturas sob o nome; `fnArityNames` intacto p/ defaults; chave de
  coloração async ganha a tag — sem isso duas sobrecargas de mesma aridade
  herdavam `async` uma da outra e vazavam Promise p/ valor = divergência
  silenciosa); interpretador = `findKofMethod` tenta igualdade EXATA de
  assinatura (tag) antes do fallback nome+aridade. Programa de candidato
  único por nome: byte-idêntico ao antes nos 5 targets (invariante).
- **Prova:** `TopLevelOverloadE2ETest` 5/5 — `ov.kf` (g/1+g/2, twice(String)
  +twice(Int)) e `defarg.kf` rodam `5 11 abab 42` / `7 11` byte-idênticos em
  JVM+Script+JS+x86 e riscv64/aarch64 sob qemu; `CompilerDriverTest`
  `distinctSignatureOverloadCompiles` (compila) /
  `duplicateExactSignatureFails`+`returnOnlyCollisionFails` (SEM047). O teste
  antigo `duplicateTopLevelFunctionFails` (que ALEGAVA contrato "sobrecarga
  não existe" e pendurou a lane §134) foi substituído pelo contrato novo.
- **Alcance honesto:** isto fecha a sobrecarga de **função TOP-LEVEL**
  (SG-011B). Sobrecarga de MÉTODO de classe (SEM013/colisão `Supervisor_child`
  do §131, espelho OTP) permanece ABERTA — outra máquina (symtable de classe,
  dispatch virtual, vtable real), repro e workaround lá documentados.

### 137. `kof decompile`: `switch`-statement recuperava código INCOMPILÁVEL (locals sem hoist) + `static` perdido na assinatura — ✅ CORRIGIDO 11/09 (achado pelo gate do HEAD; lane migração-legado, sem dono)

- **Menor repro (medido 11/09 — veio vermelho no gate do HEAD, NÃO é do
  §113/port: já falhava no próprio commit do autor `487287fb`):**
  ```java
  class S { static String grade(int x){ String r;
    switch(x){ case 1: r="one"; break; case 2: r="two"; break; default: r="other"; }
    return r; }
    static void main(String[] a){ System.out.println(grade(1)); } }
  ```
  `kof decompile S.class` → o `var r` (`v1`) era emitido DENTRO de `case 1:` e
  usado em `case 2:`/`return` → **SEM011 'Undefined variable or type: v1'** (o
  `switch` Kof tem cases como blocos EXCLUDENTES — probe 11/09: `var` de um
  case não alcança irmãos; idêntico ao if/else, que também recusa SEM011).
  2º bug (latente, revelado pelo 1º teste que EXECUTA saída decompilada):
  o decompiler calculava `isStatic` só p/ o frame e **nunca emitia o
  modificador** → `S.grade(1)` baixava como método de instância →
  `ClassNotFoundException`/`NoSuchMethodError` em runtime. 3º: o teste do
  autor rodava `Default.Main`, mas a classe decompilada sem pacote é `S`.
- **Causa raiz:** `BytecodeSwitch.recoverSwitchStmt` compartilhava um `declared`
  entre cases+epílogo assumindo escopo de método; `Decompile.decompile` só
  emitia `static p/ campos` (skip de `static`) e nunca na assinatura de método.
- **Fix (backend-only, 3 arquivos):** (a) **hoist** — todo slot NÃO-parâmetro
  storeado nas regiões dos braços/epílogo é pré-declarado ANTES do `switch`
  como `var` sem init (probe: `var v` sem init compila, roda e aceita
  atribuição de tipos diferentes; `Int v`/`String v`/`Object v` idem — escolhi
  `var` por não precisar mapear descriptor→tipo e aceitar qualquer valor);
  helper `localSlotsStoredIn` (decodificação de opcode de store = JVMS 6.5,
  espelha `BytecodeStatements.storeSlot`) + `BytecodeFrame.isNamedSlot`
  (nunca hoistar parâmetro/`this`); (b) `isStatic` movido p/ o topo do laço e
  prefixado nas 4 assinaturas (`stat = isStatic ? "static " : ""`); (c) alvo
  de execução do teste → `S` (a classe decompilada). **Prova:**
  `DecompileTest.recoversStatementSwitchAndRunsIt` — não só compila de volta:
  EXECUTA os 3 caminhos (`one/two/other` sob JVM); Decompilar S.class real →
  `var v1` antes do `switch`, `static` nas duas assinaturas; `java S` →
  `one\ntwo\nother`. DecompileTest **45/45** (era 45/1). NÃO regride: os
  asserts `.contains("Int add")` etc. são de substring — `static Int add` passa.
- **Consolida o §128** (mesmo teste/sintoma/raiz, registrado ABERTO pela lane
  collections; minha triagem acrescentou a 2ª causa que ele não tinha medido —
  o `static` ausente na assinatura, que só aparece quando o teste EXECUTA o
  decompilado, não só recompila).
- **Alcance honesto:** o hoist cobre o statement-switch do §128/§137 (região por
  braço = straight-line sem merge, onde `simDepth` já valida); o if/else
  (`struct`) compartilha `declared` entre branches DA MESMA forma — mesma
  família, NÃO testado por execução aqui (fica registrado: se algum dia um
  teste executar if/else com local 1º-storeado num ramo e lido fora, o mesmo
   hoist se aplica). O teste que faltava ("nenhum E2E executa decompilado")
   agora existe — é o que pegou o bug.

### 138. `RuntimeJsonDecode` panic-msg com `\n` cru em text block → `.asciz` quebrado que só montava por equilíbrio acidental de aspas — ✅ CORRIGIDO 12/09 (revelado pelo §107-x86)

- **Menor repro (medido 12/09):** compilar QUALQUER programa nativo cujo
  runtime assembly passa pela área `Ljson_rec_list_msg` (isto é, todo build
  Native — a string é emitida incondicionalmente) **com linhas asm adicionadas
  antes dela**: `as: linha não terminada` + `missing closing '"'` +
  `invalid character (0xa) in mnemonic`. Sem as linhas extras, monta
  silenciosamente. A armadilha é esta: o output do backend é byte-idêntico
  na região em causa nos dois casos — só o que VEM ANTES muda.
- **Causa raiz:** em `RuntimeJsonDecode.java:219` a mensagem de panic do gap
  JSN004 foi escrita como `.asciz "...(JSN004)\n"` **dentro de um text block
  Java** — num text block, `\n` é NOVA LINHA REAL, não escape: o `as` recebe
  `.asciz "...(JSN004)` + quebra de linha + `"`. A string fica ABERTA na
  linha 1 e "fecha" na aspas da linha 2 (`"`), com o texto do meio
  (vazio) virando lixo engolido pelo lexer — a armação só não explodiu no
  HEAD porque o equilíbrio par/ímpar de aspas ACADÊNTICO do resto do arquivo
  (todos os `#` comentários fora de string) fazia o `as` re-entrar em estado
  normal justo antes da região crítica. Qualquer adição de linhas com aspas
  (o §107-x86 emitiu `.ascii "["`/`"?"/...`) desloca o deslocamento e a
  montagem falha — em código NON-MINE. Convenção correta do próprio repo
  (lição já escrita em `docs/development/plan-spring-independence.md`
  §"Pegadinha de text block", e usada em `RuntimeMemory.java:228`,
  `RuntimeObservability3.java:35-42`, `RuntimeValidation.java:356-359`):
  para emitir `\n` no asm a partir de um text block, escrever `\\n`.
- **Fix (1 byte, backend-only):** `\\n` na linha 219 (o resto da string fica
  em linha única). A mensagem em runtime não muda (o `as` continua
  interpretando `\n` → newline). Prova estrutural: a suíte inteira (1553
  testes com qemu rodando os cross-arch) passa SEM linhas removidas; e o
  §107-x86 (que EMITE novas strings) agora monta verde.
- **Nota (regra 3, transparência cirúrgica):** o bug existia desde
  `954cca89` (bug 48, 07/09); 5 dias de CI verde o cobriram porque NENHUM
  build com linhas com aspas antes da região existia. Não é regressão — é
  latente que o §107 revelou. Lição: text blocks que EMITEM asm/string têm
  que ser revistos contra a pegadinha do escape (double backslash), e a
  pegadinha está documentada em `plan-spring-independence.md` mas não era
  CHECKLIST obrigatório da lane Native.


### 139. JS: `<call-nullable> == null` como operando DIRETO de comparação → COMP002 "expression stack underflow" (JVM/Native/Script aceitam) — ⏳ ABERTO (achado 12/09 na unidade do §125; pré-existente ao §125, medido no HEAD sem as edições)

- **Menor repro (medido 12/09):** `main() { println(mapOf("a",1).get("zz")
  == null) }` → JS: `COMP002: Internal compiler error: KofJS: expression
  stack underflow`; com `Int? ni(){return null}` + `println(ni() == null)`
  idem. A forma-SLOT (`val v = ...; println(v == null)`) funciona nos 4
  targets — é o que a matriz cobre (`wrongkey`, `nulleq`).
- **Causa (pinada no dump do próprio erro):** `== null` com lado primitivo
  dobra em `KofPop; KofLoadLiteral(BOOL)` (ExpressionBinaryLowerer:184 —
  "primitivo nunca é null"); o lado oposto (a call nullable) é emitido ANTES
  e descartado. O parser JS de fragmentos de expressão
  (`JsExpressionParser.parseExpressionFragment`) sai no `KofPop` (linha 311
  trata `KofPop` como FIM de fragmento, não como operação a consumir) e o
  `JsReturn`/`println` externo encontra a pilha vazia → underflow. O JVM
  emite tudo reto (pop+push é bytecode válido); o JS não tem stack — a
  lowering de "descartar operando" não tem contrapartida no parser.
- **Oracle (já congelado, sem decisão pendente):** `false` nos 4 targets
  (`f()==null` com `f` primitivo/Nullable(primitivo) — o fold do
  primitivo; `get`-miss é Unknown→comparação de referência boxed → `null
  == Integer(0)`... medido: JVM/Script imprimem `false` também). A célula
  `nulleqshortcut` cobre o caminho dos dois lados; falta o JS aceitar o
  fold/pop.
- **Correção provável:** no parser JS, `KofPop`/`KofPop2` DENTRO de
  fragmento de expressão deve consumir o topo (equivalente funcional do
  discard), não encerrar o fragmento — ou o lowering de `==`-com-null não
  emitir `KofPop` para o JS (materializar em temp). Unidade pequena,
  multi-arquivo-por-alvo (só JS), sem decisão de contrato. Sem dono.
- **Prioridade:** baixa (workaround: slot `val v = ...; v == null`).
