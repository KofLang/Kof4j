[English](known-bugs.md) | [Português](known-bugs.pt_BR.md)

# Known Bugs — handoff para o próximo agente

> **Data:** 10/09/2026 (última varredura em massa; triagens pontuais até 13/09) · **Versão:** 0.4.0-beta (pom `revision`). Este arquivo existe para que
> um agente (ou humano) pegue os bugs sem precisar redescobri-los. **Não são
> características** — são bugs reais com reprodução mínima.
>
> **Estado (varredura de 08/09, JVM + KofJS + interpretador):**
>
> | | |
> |---|---|
> | **Fila ABERTA (varredura 13/09 — seções sem resolução no próprio cabeçalho)** | **32 itens** (12 da varredura [~~§176~~ fechou 14/09] + §184/§185 ✅ CORRIGIDO 15/09 (crash interp em store Char[]/Bool[] — coerceFor agora produz o tipo REAL do slot Character/Boolean, KofInterpreterParityTest.charBoolArrayStore 26/26, lane development .18)/§186/§187/§188/§192/§193/§194/~~§195~~/§196/§197/§201✅/§202✅/§203✅/§206✅/§207✅/§208✅/§209 ✅ CORRIGIDO JVM 14/09 (`59e2403b`, `InterfaceDefaultMethodE2ETest` 5/5; face JS/Native → §248)/§210✅/§211 ✅ FECHADO 15/09 (D-ENUM207 lane .15: enum = classe real — `getstatic Dir.N:LDir;`, `Dir.class` emitida, `getClass()`→Dir, `instanceof Dir`, `==` por identidade, `name`/`ordinal`/`values`/`valueOf` reais; `enum == String` = SEM062; `EnumIdentityE2ETest` 6/6, suíte 1877/0/16-node)/§212 ✅ CORRIGIDO 15/09/§213/§214 ✅ CORRIGIDO 15/09/§215 ✅ CORRIGIDO 15/09/§216 🟡 face 1 `c.toString()` ✅ CORRIGIDA 15/09 (`69bc0f73`), face 2 `println`+concat congelada regra 6/§217✅/§218 🔴 ABERTO 15/09 (re-medido `ClassFormatError: Illegal class name ""` no tip 2fc7d6e0; ADIADO p/ 0.4.1 pela mantenedora, lane .17)/§219 🟡 (#160 ✅ 15/09; face de perda silenciosa #151 ✅ 15/09 → SEM011, feature adiada; #159 ✅ FECHADA 15/09 ~22:05 (`9e270e56` D-NARROW-WHILE — `NullSafetyE2ETest` 12/12, os 2 repros da issue verdes com jar fresco; issue fechada com prova))/§220/~~§221~~/§222 ✅ CORRIGIDO 15/09/§223 ✅ CORRIGIDO 15/09/§224 ✅ CORRIGIDO 15/09/§225 ✅ CORRIGIDO 15/09/§226 ✅ CORRIGIDO 15/09/§227 ✅ CORRIGIDO 15/09/§228/§229 ✅ CORRIGIDO 15/09/§230 ✅ CORRIGIDO 15/09/§231/§232 ✅ CORRIGIDO 15/09/§233 ✅/§234 ✅ CORRIGIDO 15/09/§235 (nova lane .18)/§236 ✅/§237 ✅/§238 ✅/§239 novo/§240 ✅ CORRIGIDO 15/09 (REGRESSÃO 8935c8a7: knows() p/ toda java.* → 39 reds de suíte, lane .22; separação knows()=entries OR JDK∖builtin, `KofBuiltinJdkSeparationE2ETest` 4/4, suíte 53→15) /§241 ✅ CORRIGIDO 15/09 (REGRESSÃO c0cf805e: Nullable(primitivo) boxed global mas meio-implementado → VerifyError no retorno Int? + 14 vermelhos de suíte; REVERTIDO porque o box foi landado em UMA face só, sem lockstep dos 4 targets — NÃO porque "§125 proíbe null boxed" (isso foi LEITURA ERRADA, corrigida 15/09 pela mantenedora — DECISIONS §7); `T?` boxed É o contrato, o trabalho é "completar o box nos 4 targets" — NÃO bloqueado pela regra 6; abrir a frente é decisão da mantenedora, lane compiler .22; suíte 15→1 fail) /§242 ✅ CORRIGIDO 15/09 (#256 residual: checagem de override abstrato casava por NOME+ARIDADE, não assinatura — mesma aridade/tipos diferentes satisfazia o abstrato; agora chaveia por `TopLevelOverload.sigTag` + `satisfiesAbstract`, `ConcreteClassMissingAbstractMethodTest` 6/6, lane compiler .22; #266 = mesma raiz, face PARÂMETRO) /§243 ✅ CORRIGIDO 15/09 (#261: classe do usuário com nome `List`/`String`/`Set`/`Map` era IGNORADA — o alias builtin vencia → IllegalAccessError/NoSuchFieldError; agora o guard de shadowing do §179 fecha todo pin de alias, `UserClassShadowsBuiltinE2ETest` 7/7 [VERMELHO 4/7 pré-fix], lane compiler .22) /§244 ✅ CORRIGIDO 15/09 (#267: `+` com dois operandos genéricos apagados (`Object`) emitia `iadd` → VerifyError; o ramo de concat agora dispara com ambos os operandos não-numéricos, `GenericOperandConcatE2ETest` 4/4 [VERMELHO 3/4 pré-fix], lane compiler .22) (§228 List ✅ corrigido 14/09, lane .15; /§245 ✅ CORRIGIDO 15/09 (só teste: `KofConcurrency2Test.stopFlagFieldWriteObservedBySpinReader` mal dimensionado — o laço espinhoso de 500M Int é fechado pelo C2 em ~83 ms, ANTES do `time.sleep(100)` do escritor → `nao-observou` determinístico; orçamento subido para Long 5B (~1,55 s = ~15× margem); Q0: 3/3 vermelho sem o `ACC_VOLATILE` SG-020, 3/3 verde com ele; lane bugs-and-gaps `192.168.100.15`; portão DOING 2→1 fail, o restante = §205) /§246 ✅ CORRIGIDO 15/09 (#268: acesso encadeado a membro em campo genérico (apagado a `Object`) emitia owner JVM inválido — campo → `"?"`, chamada → `""`; faltava `substituteTypeVariable` no caminho de acesso a campo; CORRIGIDO pela lane compiler .22 — `CompilerTypes.substituteTypeVariableIn` + checkcast/unbox no load apagado, `GenericFieldAccessE2ETest` 6/6 [VERMELHO 5/6 pré-fix]) /§247 ✅ CORRIGIDO 15/09 (#269: escrita por receptor NULÁVEL era aceita em silêncio (sem SEM049, ao contrário da leitura) e emitia `putfield Field "?".num:...` → VerifyError; CORRIGIDO pela lane compiler .22 — SEM049 na escrita + unwrap de `NullableType` no `ExpressionAssignmentLowerer`, `GenericFieldAccessE2ETest` 6/6 [VERMELHO 5/6 pré-fix]) §233/§234 renumerados 14/09 ~17:40 apos colisao §231/§228 com as lanes .18/.15; §232 = face implicit-this da familia §227, owner .18; §239 = `String.format` JS COMP002, catalogado 14/09 pela lane .15) novos; §189 ✅ corrigido 14/09; §190 teste da lane `.18` — `KofBlogE2ETest` sem `Content-Length`; §191 ✅ corrigido 14/09 (`cookieSet` secure/httpOnly string case); **§192 tinha DUPLICATA (parseOrDefault lane `.17` × `db.query<Record>` lane `.18`) — o registro do `db.query<Record>` foi renumerado para §197 em 14/09 pelo registry owner (lane bugs-and-gaps `192.168.100.15`); §192 = parseOrDefault; **NOVO §194** (for-in sobre String/não-coleção era ACEITO e quebrava por target — JVM VerifyError/Native SIGSEGV/Script crash/JS iterava; ✅ CORRIGIDO 14/09 SEM058, triagem do #145, lane bugs-and-gaps `192.168.100.15`); **NOVO §195** (`KofBlogE2ETest` vermelho no HEAD: teste usa `app.security` mas os GET não mandam sessão → 401, middleware CERTO; ⚠️ TESTE, dono lane `.18`/`.22`); **NOVO §196** (i18n EN quebrou o guard `ConcurrencyGapsDocTest` — o teste fixava o cabeçalho PT `| Construto |`; ✅ CORRIGIDO 14/09, guard aceita as duas grafias, lane bugs-and-gaps `192.168.100.15`))) (seções/sub-faces sem resolução) — ~~§177~~ ✅ CORRIGIDO 13/09 (lambda com corpo em BLOCO que retorna local declarada no bloco era tipada void/SEM033 quando o módulo tinha classe — `firstReturnValueType` não registrava os `VarDeclStmt` do corpo; lane bugs-and-gaps `192.168.100.15`, fechado na unidade do §178; repro do translator roda 4 targets = 8). **§179 ✅ CORRIGIDO 14/09** (tipo `kof.ui`/`kof.media` DECLARADO → JVM VerifyError; corrigido pela DECISIONS §4 opção A — `qualifyDeep` passo 2b + guard de shadowing `unitDeclaresType`; dono 192.168.100.18) **+ NOVO §180** (println double/float no Native x86 ≠ JDK `Double.toString`/`Float.toString` — residual/overclaim do bug 44; lane Native) **+ §181 ✅ CORRIGIDO 13/09** (cast `Double/Float as Int/Long` fora de faixa/NaN/Inf — implementado em `c90e85ee` (JS `kofD2I/kofD2L` + Native) e **regressão do fix x86/riscv corrigida pela lane bugs-and-gaps `192.168.100.15`**: bits inteiros lidos como double saturavam TODO valor positivo; labels riscv duplicados; 4 targets) **+ §182 ✅ CORRIGIDO 13/09** (parse ISO de `kof.time` com campo de SINAL: JVM/Script lenientes via `Integer.parseInt`, Native estrito, JS inconsistente — fix da lane .18 `a13665f7`, consenso ESTRITO) **+ §183 ✅ CORRIGIDO 13/09** (teste `KofTimeE2ETest.todayIso…` era flaky de relógio — `isToday(2026,9,13)` literal; fix da lane .18 `a13665f7` via partes de `todayIso()`; achado da lane bugs-and-gaps `192.168.100.15`) **+ NOVO §184** (store em `Byte[]`/`Short[]` fora de faixa NÃO trunca no JS — JVM/Native/Script `-126`/`4464` vs JS `130`/`70000`; silencioso, regra 5) **+ NOVO §185** (interpretador Script **crash** ao gravar em `Char[]` **e `Bool[]`**: `c[0]='A'`/`b[0]=true` → `argument type mismatch`; JVM/Native/JS corretos — causa raiz real é `KofInterpreterValues.coerceFor` no caminho vivo `KofInterpreter:306`, **não** o `KofInterpreterOps.arrayStore` que é código morto) **+ §189 ✅ CORRIGIDO 14/09** (record com campo de lista genérica **nullable** `List<Item>?` — `toGenericSignature` não desembrulhava `NullableType` → record component sem `Signature` → `json.decode` devolvia `LinkedHashMap` cru → `ClassCastException`; **o teste do #128 `76ca3dd4` subiu VERMELHO declarando verde**; fix = desembrulhar nullable, lane bugs-and-gaps `192.168.100.15`) **+ §187 🟡 face Native ✅ CORRIGIDA 13/09** (`Char[]` fora de faixa NÃO estreitava a 16 bits no Native — `elementTypeSize` mapeia `char`→4 e o `kof_array_set` fazia `movl` cru; **fix = máscara 0xFFFF no store** x86 `movzwl` + riscv/aarch `slli`/`srli`, stride 4 preservado; **face JS segue ABERTA** = §184; JVM/Script `4464`/`65535`; **§186 = bug distinto do colaborador Jonas Rocha**, inicializador `static` não-constante/issue #133)   — ~~§168~~ ✅ CORRIGIDO 13/09 (`3ab4c99e`; SEM025 json — `json.metodoRuim()` agora rejeitado, re-verificado no `kof check`; era pré-existente, não do WEB001-T1), ~~§166~~ ✅ CORRIGIDO 13/09 (gate tamanho hello: baseline re-medido 7.700→8.297; shim DOM #121 é préambulo `always` legítimo, mesmo processo do #104 — opção (a) do próprio registro; lane bugs-and-gaps), §165 re-verificado 13/09: NÃO reproduz em build limpo (node v22 presente, export no runtime, célula verde) — trap de inlining `static final`, ver §165; ~~§81~~ ✅ CORRIGIDO 13/09 (5b: Long=BigInt no JS, paridade 64-bit real, golden JS unificado ao JVM), §101 (relacionais de Double com NaN divergem cross — **congelado** regra 6), §104b-ii (record em coleção + storage-box asm — **lane bugfixer**, unidade GRANDE), §107 🟡 (`println(coleção)` nativo → lixo de ponteiro; **face escalar ✅ CORRIGIDA 12/09** nos 3 nativos `f3b3821c`+B39; restam record/aninhado=`?` até §104b-ii + FP-cross=FLT001), §114 ⏳ (equals de record com campo-referência no Native; sub-face do §104b-ii), §129 (throw em worker `spawn` → longjmp cross-thread no Native — **lane nat**; era referido como "§129-TLS"), §132 (KofJS: task de task não roda sem ceder o event-loop — gate OTP002; **lane alheia**), §161/NAT-STR01 (case-fold ASCII-only no Native vs Unicode no JVM/JS — **DECIDIDO 13/09**, lane nat; registrado aqui 13/09). **§165 RE-VERIFICADO 13/09 (dono 192.168.100.17, node v22 presente): NÃO reproduz em build limpo** — o export CHEGA ao `kof-runtime.mjs`, a célula verde; o sintoma era a trap de inlining `static final` (classes stale). Fecho/blindagem = dono §106/js-slices; §166 aberto). **~~§149~~ NÃO está aberto — ✅ CORRIGIDO 12/09** (a linha anterior o listava por engano; a raiz era o `JsIfThrowElse` do §147). **§156 ✅ CORRIGIDO 13/09** (lista heterogênea de lambdas mesma assinatura → elemento sem className, dispatch SAM). **§155 ✅ CORRIGIDO 13/09** (tipo-função em type-args → `ClassFormatError`; parser preserva os espaços do type-ref). **§127-JVM ✅ CORRIGIDO 13/09** (decisão 9a: `as ()->T` parseia como type-ref; checkcast p/ interface SAM sintética). **§94 ✅ CORRIGIDO 13/09** (EQ/NE de Double/Float no interpretador agora IEEE). **§125 ✅ CORRIGIDO 12/09** (return Nullable(primitivo) → default; célula `nullableprint` 4/4). **§139 ✅ CORRIGIDO 12/09** (JS fold `f()==null` → COMP002; parser JS descarta mid-expression). **§140 ✅ CORRIGIDO 12/09** (gate ≤500 virou ratchet com baseline no CI). **§90 ✅ CORRIGIDO 12/09** (lane web). **§145/§146/§147 ✅ CORRIGIDOS 12/09** (`440730c8`, issue #101). **§45 ✅ IMPLEMENTADO 13/09** (DD-01 opção 4a: FinallyFrame na IR; 4 targets, `063ed956`) e **S10c ✅ 13/09** (`random.randomBytesHex`, `317b23e7`). **§157/§158/§159/§160 ✅ CORRIGIDOS 13/09** (issue-lane: #103 caso 3 POP2 em `HashMap.put` de `Long`, kof.web `header()`/`query()` `String?`+SEM049, Native web WEB001, KofJS hostless `kof_platform`). **§172 ✅ CORRIGIDO 13/09** (compostos de SHIFT `<<=`/`>>=`/`>>>=` baixados como atribuição simples — miscompilação silenciosa; + 2ª face `Long<<=Long` VerifyError, L2I na contagem; lane development/translator, dono = 192.168.100.22). **Conclusão honesta (13/09):** dos **8 itens abertos**, a maioria pende de **decisão da mantenedora já ratificada** (fila de implementação), **congelamento regra-6** (§101 + sub-faces §114/§107 = 3), **lane alheia** (§104b-ii lane bugfixer + §129 lane nat + §132 = 3) — **§89 ✅ CORRIGIDO 13/09** (`e33425b5`, 4 alvos + warning SEM090), **§106 ✅ CORRIGIDO 13/09** (`5b939106` + residual JS `ab85cfae`, 4 alvos), **§117 ✅ CORRIGIDO 13/09** (`3734f2aa`), **§131 ✅ CORRIGIDO 13/09** (`18a64d45`, 4 backends; + **residual same-arity/tipos** `73ca2d58`, Native), **§156 ✅ CORRIGIDO 13/09**, **§81 ✅ CORRIGIDO 13/09** (Long=BigInt JS, seção própria) e **§163 ✅ CORRIGIDO 13/09** (interpretador: 2º parâmetro largo lido como `null` — paridade 4-target, achado no probe do split `NativeBackend`). **§167 ✅ CORRIGIDO 13/09** (bitwise/shift com `Long` misturado: JVM VerifyError + JS TypeError/máscara errada + overflow de Long sem wrap no JS; lane bugs-and-gaps `192.168.100.15`, 4 targets, 3 testes — seção própria). |> | **§167 ✅ CORRIGIDO 13/09 (lane bugs-and-gaps, 192.168.100.15)** | bitwise/shift com `Long` misturado: JVM VerifyError + JS TypeError/máscara errada + overflow de Long sem wrap no JS. 4 targets; achado na caça Q4 13/09. Overclaim conexo do §81 (declarava "64-bit real" cobrindo só parse/literal). Prova: `BackendParityTest.parityLongBitwiseShiftMixed` + `KofInterpreterParityTest.longBitwiseShiftMixed` + célula `bitwise` estendida 4/4. |> | **§172 ✅ CORRIGIDO 13/09 (lane development/translator, 192.168.100.22)** | compound shift `<<=`/`>>=`/`>>>=` era parseado mas baixado como atribuição SIMPLES (só o RHS gravado): `x=6; x <<= 2` dava `2` (silencioso, 4 targets). Fix: `isCompoundOp`+`compoundBinaryOp` com SHL/SHR/USHR + `emitCompoundRhsConv` (L2I no RHS largo). Prova: `CoreRegressionE2ETest.compoundShiftAssignments`. | /§248 🔴 ABERTO 15/09 (gap cross-target do §209/#213: default methods de interface descartados no JS — `TypeError` em runtime — e no Native — imprime `null`, exit 0; feature só do JVM, precisa de decisão de escopo, lane compiler .22) /§249 ✅ CORRIGIDO 15/09 (dois identificadores seguidos no início do statement eram parseados em SILÊNCIO como decl tipada `Type name` — `s length` / `x is Dog`; raiz: o tipo declarado de um `VarDeclStmt` nunca era validado, então um nome simples desconhecido virava decl morta invisível; agora SEM011, `UndeclaredVarTypeE2ETest` 10/10 [VERMELHO 3/10 pré-fix]; lane compiler .22) /§251 ✅ CORRIGIDO 15/09 (tipos DECLARADOS nunca validados: tipo indefinido em retorno/parâmetro → classe incarregável `NoClassDefFoundError` em runtime; campo/componente ignorado em silêncio; agora SEM011 em cada sítio de declaração via `DeclaredTypeChecker` dedicado + whitelist de type variable genérica; `DeclaredTypeValidationE2ETest` 14/14 [VERMELHO 7/14 pré-fix]; lane compiler .22) /§252 🔴 OPEN 15/09 (#273: Native x86_64 INTERMITTENT `spawnWorkerThrowPropagatesThroughSelectAnyNative` — `Runtime error: array index out of bounds`, 2 in 7 full-module runs, byte-identical, NOT reproducible in isolation (9/9) nor 20× binary; suspected race in the §129 per-thread handler path; owner = native lane .18/nat) /§253 🔴 ABERTO 15/09 (idiom one-shot `var id = time.interval(10, () -> { time.cancel(id) })` → SEM011 em TODOS os alvos — a var em declaração está fora do escopo da lambda; o workaround pré-declarado lê `id` dentro do job → SIGSEGV 139 só no native x86; bloqueia Toast auto-dismiss/UIW008 da kof-ui-widgets; face A lane compiler .22, face B nat .18; achado pela development .18 ao verificar UIW008/UIW020)
> | **§173 ✅ CORRIGIDO 13/09 (lane bugs-and-gaps, 192.168.100.15)** | `++`/`--`/compound em `Long`/`Double`/`Float` + incremento de ELEMENTO de array: JVM VerifyError (literal `INT 1` em binário de 2 slots, `DUP` de 1 slot, `arraystore` sem `[array,index]`), Native core dump, Script `NoSuchElementException`, JS `stack underflow`/`KofDup2`. 4 targets; caça Q4 13/09 (sobre o §167). Prova: `BackendParityTest.parityIncrementWideTypesAndArrayElement` + `KofInterpreterParityTest.incrementWideTypesAndArrayElement` + célula `increment` 4/4. |
> | **§174 ✅ CORRIGIDO 13/09 (lane bugs-and-gaps, 192.168.100.15)** | `return`/`throw` dentro de um `if` dentro do `try`: JVM/Native/Script corretos, KofJS abortava com `COMP002 unexpected KofCatchStart` (o `JsIfThrowElse.parseElse` consumia o endLabel do try envolvente ao tratar o `then` incondicional como if-else). Fix sem mudança de contrato/IR (guarda `isTryEndLabel`). Prova: `CoreRegressionE2ETest.returnInsideIfInsideTryJs`. |
> | **§176 (WEB001-T1) — ver §176 abaixo (lane JS/web, 192.168.100.17)** | o número §176 foi ocupado no remoto por WEB001-T1 antes desta lane commitar; a unidade compound-array+lambda desta lane foi renumerada para **§178** (e a face lambda-local para **§177**, colidindo com a seção já existente do translator, fechada pela mesma raiz). |
> | **§177 ✅ CORRIGIDO 13/09 (lane bugs-and-gaps, 192.168.100.15 — raiz da lane compiler)** | Lambda com corpo em BLOCO que retorna uma LOCAL declarada no próprio bloco era tipada VOID quando o módulo continha classe (`SEM033` no repro do translator): `ExpressionTyper.firstReturnValueType` não registrava os `VarDeclStmt` do corpo no escopo → `return y` = UNKNOWN → lambda void. Fix: escopo cópia mutável com os locais do corpo antes da varredura. Prova: repro exato (com `class C {}`) roda 4 targets = `8`; `CoreRegressionE2ETest.lambdaReturnLocalVar`. |
> | **§178 ✅ CORRIGIDO 13/09 (lane bugs-and-gaps, 192.168.100.15)** | (a) compound em ELEMENTO de array no JS (`a[0] += x`, `a[0] <<= 2`) → `COMP002 unexpected KofDup2`: o guard `isExpressionOp` não listava `KofDup2` (o handler já existia desde #64). (c) lambda que retorna handle `kof.ui`/`kof.media` → VerifyError no `invoke` (descritor `LLabel;` com int na pilha): `CompilerLambdaClass` preserva o handle + `JvmLiteralEmitter.returnOpcode` emite `IRETURN` (consistente com `JvmTypeMapper` = `"I"`). Prova: `CoreRegressionE2ETest.compoundOnArrayElementJs` + `ComponentCoreE2ETest` 14/14. A face (b) é o §177 (mesma raiz). |
> | **§168 ✅ CORRIGIDO 13/09 (lane development/translator, `3ab4c99e`)** | SEM025 ausente em namespace `json` para método inexistente: `json.metodoRuim()` compilava com sucesso (deveria falhar com SEM025). O handler do #126 (`61495f69`) validava aridade de `encode/decode` mas não rejeitava método desconhecido; `MemberCallNamespaces` mudou de `if (known && !valid)` para `if (!valid)` (rejeita QUALQUER método ≠ encode/decode) + `return null` no caminho válido. Re-verificado no binário (`kof check` → SEM025; `json.encode(42)` → no errors); `SemanticResolutionTest` 27/27. |
> | **§179 ✅ CORRIGIDO 14/09 (DECISIONS §4 opção A, dono 192.168.100.18; catalogado 13/09 pela lane bugs-and-gaps `192.168.100.15`, caça Q4 do §178)** | Tipo `kof.ui`/`kof.media` DECLARADO numa assinatura/var/param/campo quebra o backend JVM (VerifyError `Bad type on operand stack`): `MemberResolver.resolveType("Label")` cai em `Type.of("Label")` = `ClassType("", "Label")` — NÃO reconhece o builtin `kof.ui.Label` — então o descritor sai `LLabel;` enquanto o valor real do handle é um `int` (`kof_ui_label_new` devolve int). Menor repro `main(){ Label l = Label("x"); println(uiNodesLive()) }` → JVM VerifyError; Native/Script/JS OK. Mesma raiz: `Label make(){...}`, param `void use(Label l)`, campo `Label field`. **CORRIGIDO 14/09:** `CompilerTypes.qualifyDeep` passo 2b mapeia `ClassType("", name)` → `KofUi.typeByName`/`KofMedia.IMAGE_DATA` (via `builtinDeclaredType`), com o guard de shadowing `unitDeclaresType` (um `class Label` do usuário ainda vence); o `VarDeclStmt` do `StatementLowerer` resolve pelo analisador semântico. Prova: `ComponentCoreE2ETest.declaredUiAndMediaTypesCompileAndRun` + `userClassShadowsBuiltinUiTypeName` (JVM+Native+JS; re-verificado verde 15/09). |
> | **§181 ✅ CORRIGIDO 13/09 (catalogado pela lane bugs-and-gaps, 192.168.100.15; fix `c90e85ee` + regressão x86/riscv corrigida pela mesma lane `192.168.100.15`)** | Cast `Double/Float as Int/Long` FORA de faixa / `NaN` / `Infinity`: o contrato é o JVM (JLS 5.1.3 — satura: NaN→0, >MAX→MAX, <MIN→MIN) e **JVM+Script concordam**. **Native x86** usava `cvttsd2si` cru → "integer indefinite" `INT_MIN` (`3.0e9 as Int`→`-2147483648`, `NaN`→`INT_MIN`, `1.0e19 as Long`→`Long.MIN`). **JS** usava `Math.trunc`/`BigInt(Math.trunc)` sem 32-bit (`3.0e9 as Int`→`3000000000`, `NaN as Int`→`NaN`, `Infinity as Int`→`Infinity`; `1.0e19 as Long`→`10000000000000000000`; e **`NaN as Long` LANÇAVA `RangeError`**). Incoerente até com a aritmética Int do JS (que faz wrap 32-bit). Célula `cast` só testava valores EM FAIXA = **verde falso (Q5)**. Fix entregue: JS = helpers saturantes (`kofD2I`/`kofD2L`/`kofF2I`/`kofF2L`); Native = guard `ucomisd`+saturação (`emitSatConv`) espelhado riscv/aarch. **A célula `cast` pegou a regressão do x86** (bits inteiros lidos como double) e o `castrange` (4 targets) trava o golden. |
> | **§180 ✅ CORRIGIDO 15/09 x86_64 (lane bugs-and-gaps `192.168.100.15` catalogou, lane development `192.168.100.18` executou — residual/overclaim do bug 44)** | `println(double/float)` no Native x86_64 NÃO era JDK `Double.toString`/`Float.toString`: `%.16g` truncava o shortest-round-trip (`println(0.1+0.2)` → JVM/Script/JS `0.30000000000000004`, Native `0.3`; `100.0/3.0` → `33.333333333333336` vs `33.33333333333334`), divergia na notação científica (`1e7` → `1.0E7` vs `10000000.0`; `1e-5` → `1.0E-5` vs `1e-05`) e o `Float` imprimia a expansão double (`1.0f/3.0f` → JVM `0.33333334`, Native `0.3333333432674408`). Fix: `RuntimeDtoa` com o loop `%.*e`+`strtod` (shortest round-trip bit-exato) + reformatação ao estilo Java; só x86_64 (libc), riscv/aarch seguem `FLT001`. Célula `doubleprint` com o Native incluído (Native == JVM). |

> | Antiga "varredura 08/09" (apócrifa — corrigida 12/09) | os "abertos" 39/62/63/64/46/48/50/59/61 estão ✅ CORRIGIDO nos próprios cabeçalhos (39/62/63/64 JVM/JS; 46/50/59 Native; 48/61 gap honesto JSN004/FFI001); contagem real na linha acima. |
> | Paridade interpretador × compilados (semântica `==` congelada — regra 6) | **0** — bug 94 ✅ CORRIGIDO 13/09 (EQ/NE de Double/Float no interpretador agora IEEE; a "decisão" era alinhar ao previsto, que os 3 compilados + corpus já definiam) |
> | Paridade backend-only (regra 5, atacável na lane Native) | **2** — §107 (println coleção → lixo; **face escalar ✅ CORRIGIDA 12/09 nos 3 targets nativos** — x86 `f3b3821c` + cross B39, golden JVM byte-idêntico; restam record/aninhado=`?` honesto até §104b-ii, FP-cross=FLT001; §107-JS 11/09), §104b-ii (equals de conteúdo p/ record + box de primitivo no storage asm; **face char ✅ FECHADA 11/09** — `mapgetprim` 4/4)
> | Operadores relacionais NaN cross (congelados — regra 6) | **1** — bug 101 (`<`/`<=`/`>=` com NaN: riscv IEEE vs x86/JVM quirk `dcmpg`) |
> | **Corrigidos na sessão de paridade absoluta 11/09** | **15** — bugs 96 (SEM052), 98 (SEM053), 100 (SEM051+fold), 44-residual, 102 (from-idx), 103 (SEM054), 104a (KofObj equals/hash/toString no interpretador), 104b-i (LINK_FAIL `Object.equals` herdado no Native), 104c (membership de record por conteúdo no JS — `kofValEq`), 107-JS (`kofFormat` no JS), 109 (CRASH JVM no guard do `map.get` primitivo), 110 (`-0.0` colapsado em `+0.0` no literal emitter JVM), 111 (trailing-empties no `split` Native/JS + sentinela `substring` 0→-1; ✅ cross riscv/aarch B36/B37 11/09 — FECHADO nos 5 targets), 112 (prev de `put`/`remove` p/ primitivo: VerifyError/NPE JVM + SIGSEGV Native por pilha desequilibrada + `set.add` do interpretador + **JS fechado na mesma unidade** — `?? default` + `KofPop` preserva side-effect embrulhado; 4/4 targets), **104b-ii FACE CHAR** (SIGSEGV/`a` no `println(char-em-coleção)`; 3 buracos: desembrulhar `Nullable(CHAR)` no print-lowering JVM-coerente `unboxDescriptor` (char→`Integer`, não `Character`/`charValue`) + repair de `as Char` no `SemExpressionTyper` — `mapgetprim` 4/4) — todos com prova na matrix/suíte |
> | **Corrigidos na prova cross-arch 11/09 (MATH001/TIME002/B33)** | **3** — bugs 101→registrado (relacional NaN, ABERTO regra 6), MATH001 (Double math B32), TIME002 (ISO add/diff B33), 105 (random.int loop — renumerado de 102, colidiu c/ §102 indexOf) |
> | Verificados corrigidos em 08/09 | **19** — bugs 1–8, 10–17, 19, 20, 26 |
> | **Reverificados 13/09 (ambiente com qemu/riscv disponível)** | **5** — bugs 9, 18, 21, 22, 23: ✅ CORRIGIDO nos cabeçalhos CONFIRMADO por teste neste HEAD (`NativeE2ETest` 2/2, `KofJsE2ETest.uiWidgetIdsUseMonotonicCounter`, `PackagesE2ETest` 12/12, `AndroidInteropE2ETest.missingSuperclassOnClasspathWarns` — 16/16, 0 falha)
> | **⚠️ Colisão de numeração (13/09)** | **§127, §128 e §129 existem DUAS VEZES**, de merges independentes: (a) série *collection/decompile* (11/09) — §127 `map.get`-miss JS, §128 DecompileTest statement-switch, §129 `Set.remove` por índice; (b) série *OTP/spike #83* (11/09, renumerada p/ 127-129 na reconciliação `b114652b` e DEPOIS colidida) — §127 cast p/ tipo-função, §128 unbox de `selectAny`, §129 throw em worker `spawn`. As refs em CÓDIGO apontam para a série OTP (`JvmOpCollections` §128-JVM, `CompilerSupervisor` §129, `ExpressionBinaryLowerer` bug 127); a `conformance-matrix` aponta para a série collection (`bug 129` `setdedup`, `bug 127` `wrongkey`). **NÃO renumerar sem tocar o código** (fora da lane docs): desambigue pelo CONTEXTO (OTP vs collection). Fila futura: renumerar a série OTP p/ **§162-§164** (livres após o merge que ocupou §157-§160) junto com as refs de código (`JvmOpCollections` §128-JVM, `CompilerSupervisor` §129, `ExpressionBinaryLowerer` bug 127).
>
> Os bugs marcados `✅ VERIFICADO CORRIGIDO 08/09` foram reproduzidos contra o
> build atual e **não** falham mais — parte virou saída correta, parte virou
> diagnóstico limpo. A ressalva de 08/09 ("Native não pôde ser reverificado —
> host arm64/macOS sem toolchain x86_64-linux") está **superada**: o host
> atual é x86_64-linux com qemu riscv64/aarch64 + binutils cross, e o Native
> foi reverificado nas rodadas 11/09–13/09 (cross-arch 42+42; tabela acima).
>
> **Como pegar:** reproduza o snippet (`kof run --target=jvm`), fix no CÓDIGO
> (não no corpus), adicione teste E2E que falha antes/passa depois, atualize
> este arquivo (mova para "resolvidos" com o commit) e remova as notas do
> corpus.

---

## §168 — SEM025 ausente para método desconhecido em namespace `json` — ✅ CORRIGIDO 13/09 (lane development/translator, dono = 192.168.100.22)

> **CORRIGIDO 13/09 como efeito colateral do fix do translator (`3ab4c99e`).**
> O handler semântico do namespace `json` em `MemberCallNamespaces.inferStatic`
> passou de "só valida aridade quando o método é `encode`/`decode`" para
> "rejeita QUALQUER método não-válido" (`if (!valid)` no lugar de
> `if (known && !valid)`); o retorno de `UnknownType` só ocorre no ramo de erro
> (caminho válido devolve `null` e segue a cadeia). **Re-verificado no binário
> real:** `kof check` em `main() { println(json.metodoRuim()) }` →
> `:0:0: error: Cannot resolve method 'metodoRuim' on namespace 'json' … [SEM025]`;
> caminho feliz `json.encode(42)` → `no errors`. Célula
> `SemanticResolutionTest.wrongArityOnJsonNamespace` (J4) verde —
> `SemanticResolutionTest` 27/27.
>
> - **Sintoma original:** `main() { println(json.metodoRuim()) }` compilava com
>   sucesso (esperado `false` + SEM025 "on namespace 'json'").
> - **Causa raiz:** o handler semântico do `json` (`61495f69`, #126) validava
>   **aridade** de `encode`/`decode`, mas não tinha allow-list de métodos —
>   método inexistente no namespace resolvia no caminho genérico.
> - **Pré-existente:** provado no HEAD limpo (`553b3326`/`b17663d7`) com
>   stash da lane development — **não era regressão do WEB001-T1**.
> - **Correção aplicada:** allow-list implícita (encode/decode) no handler do
>   namespace `json`; SEM025 para o resto — via `if (!valid)`. Prova no
>   `SemanticResolutionTest` (J1–J6) + `kof check` no binário.

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

### 9. Captura mutável no Native ✅ CORRIGIDO 13/09 (triagem: `NativeE2ETest.nativeLambdaMutableCapture` verde 1/1 neste HEAD; a marca ✅ estava no corpo mas faltava no cabeçalho): ler boxed dentro da lambda após mutação
EXTERNA produzia lixo (JVM correto) — a causa era o prólogo tratando captura como arg de registrador

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

### 28. FLAKE: `KofWebHardeningTest.ws_connection_counter_increments_and_decrements` — ✅ CORRIGIDO 05/09

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

### 32. Type-argument de import sem package → cast/descritor quebrado — ✅ CORRIGIDO 05/09

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
### 44. `println(double)` no Native x86_64 imprime 6 casas + `5` (JVM: 16 casas + `5.0`) — ✅ CORRIGIDO 10/09 (x86_64; faces (a) e (b) verificadas por probe pós-fix) — ⚠️ **OVERCLAIM: residual aberto no §180** (precisão shortest-round-trip + notação científica)

- **Sintoma original:** `println(1.0/3.0); println(2.5*2.0); println(7.0/2.0)`: JVM → `0.3333333333333333` / `5.0` / `3.5`; **Native** → `0.333333` / `5` / `3.5`.
- **✅ CORRIGIDO 10/09 (commit `5ae263d1`, `RuntimePrintNum`):**
  - **16 casas:** `%.16g` em `kof_print_double`/`kof_print_float` — `1.0/3.0` → `0.3333333333333333` (casa com o JVM).
  - **face (a) `5` vs `5.0`:** pós-processamento — inteiro-válido (saída sem `.`/`e`/`nan`/`inf`) ganha `.0` — `println(2.5*2.0)` → `5.0` (contrato JDK `Double.toString`).
  - **face (b) reordenação stdout:** `kof_print_double`/`kof_print_float` abandonam `printf` (buffered stdio) e emitem via o mesmo caminho `snprintf` + syscall `write` dos Int/String — misturar `println(double)` com `println(Int)`/`println(String)` NÃO reordena mais. Probe `Fp44c` 10/09 (pós-fix): `print(2.5*2.0); println(0); println(5.0); println(2.5*2.0)` → `5.0 0 5.0 5.0` (ordem preservada; o reprodutor antigo dava `50\n5\n5\n`).
  - `float` imprime como `double` (`cvtss2sd` antes de formatar) — paridade JVM.
- **Prova:** `ConformanceMatrixTest.floatprint` (native desbloqueado, JVM+Native+Script verde; KofJS segue excluído: doc "parece bug mas é esperado" — JS `String(5.0)` = `"5"`) + probes `Fp44`/`Fp44c` 10/09.
- **Nota:** a parte `5` vs `5.0` do JS fica documentada como esperado (não é bug); só o Native divergia do JVM.
- **⚠️ OVERCLAIM (medido 13/09, ver §180):** o `%.16g` NÃO implementa `Double.toString` — a precisão shortest-round-trip (`0.1+0.2` → `0.3` vs `0.30000000000000004`) e a notação científica (`1e7` → `10000000.0` vs `1.0E7`, `1e-5` → `1e-05` vs `1.0E-5`) ainda divergem no Native x86. A célula `floatprint` só testa 3 valores que coincidem (verde falso, Q5). Residual catalogado no **§180**.
- **Residual:** faces riscv64/aarch64 não re-verificadas (qemu ausente no worker) — o formato do double no RISC-V segue o path anterior até o toolchain chegar; bug 59 bloqueia a verificação cross-arch de qualquer jeito.
- **✅ RESIDUAL CORRIGIDO 11/09 (x86_64) — spelling de `inf`/`-inf`/`nan`:** o fix original (16 casas + `.0`) deixou **passar reto** o spelling do glibc `%.16g`, que escreve `inf`/`-inf`/`nan`, mas o contrato é o de `Double.toString` do JDK → `Infinity`/`-Infinity`/`NaN` (o que JVM/Script imprimem; o comentário antigo "NaN/Infinity passam retos (JVM idem)" era **erro** — o glibc não escreve `Infinity`). Paridade regra 5 quebrada de forma **silenciosa** (sem diagnóstico): `println(1.0/0.0)` → JVM/Script `Infinity`, Native `inf`; `0.0/0.0` → `NaN` vs `nan`; afetava **todos** os caminhos de conversão (println box, `print` sem box, `String.valueOf`, concat `+`). **Fix:** reescrita in-place no buffer (64 bytes, os 3 spellings cabem) nos **4** ramos — `RuntimeStringConv.emitDoubleToString`/`emitFloatToString` (o box, que é o caminho real do `println(double)` — verificado via `objdump`: `main` chama `kof_double_to_string`+`kof_println_string`, **não** `kof_print_double`) + `RuntimePrintNum.emitPrintFloat` (o `print`/unbox). Cada ramo só reescreve quando o buffer é **exatamente** o spelling do glibc (compara char a char + tamanho) — decimal/científico/`.0` têm dígito e passam. **Prova:** `ConformanceMatrixTest.infinityprint` (println + print sem box + String.valueOf + float overflow, 3 targets) + linha `infinityprint` em `conformance-matrix.md` (o `ConformanceMatrixDocTest` exige paridade test×doc). **Lição (4ª vez): build incremental com constante inlined** — a `UI_UUID_RUNTIME` do `JsRuntimeUiUuid` ficou stale no `JsArtifactWriter.class` após o merge, e `KofUuidTest.uuidV7Js` falhava com "export not provided" **sem culpa do merge nem do código**; `clean test` resolve. Sempre `clean` ao suspeitar de "export missing" em fragmento wired. **Residual:** riscv64/aarch64 não re-verificados (qemu ausente) — seguem o path anterior até o toolchain chegar.

### 45. `finally` com `return` no try: JVM/interpretador DESCARTAM o efeito colateral do finally (JS correto) — ✅ CORRIGIDO 13/09 (DD-01 opção 4a ratificada: FinallyFrame na IR)

- **Correção (13/09):** `FinallyFrame` na IR (`CompilerDriverState`): `ReturnStmt` com frame ativo → `KofStoreLocal(#retVal)` + `KofJump(returnFinallyLabel)`; epílogo do try roda o finally e retorna (ou encadeia p/ frame externo aninhado); lambda/método/função salvam e zeram a pilha de frames; JS reconstrói try/finally nativo + epílogo só com o return (sem duplicar o finally) e `#retVal` é pre-declarado no topo do método. Prova: `CoreRegressionE2ETest.finallyReturnJvm` (try-return `fin`+`1`, catch-return `fin2`+`2`, void-return `fin3`) + `finallyReturnJs`; suíte 4-módulos **1627/0**. Todos os 4 targets agora `fin`+`1` (paridade).

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

### 65. Frontend JS/browser: `Audio`/`Video` em `Window.show()` não chegam ao DOM real (Chrome) — ✅ NÃO REPRODUZ (reverificado 12/09, issue #114/PR #115)

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
- **Impacto na gate (não se confirmou — ver reverificação abaixo):** 2 testes
  vermelhos fora do par riscv/aarch (bug 59) para qualquer agente que rode a
  suíte completa com Chrome instalado. Quem corrigir: UI lane (dono da PR #39).
- **Reverificado 12/09 (issue #114, PR #115 de @ViniAguiar1) — não reproduz em
  nenhum ponto testado, inclusive no commit que registrou esta entrada.** Os
  dois testes do sintoma rodam com Chrome real (não são pulados) e passam:
  - **macOS arm64 + Google Chrome 152:** `audioRendersInRealBrowserDom` e
    `videoRendersInRealBrowserDom` verdes na `beta-0.4.0` (`6cd36cd5`) e no
    próprio `d090ca7f`, o commit que abriu esta entrada.
  - **CI `ci.yml` (ubuntu-latest, `google-chrome` no PATH):**
    `KofJsBrowserE2ETest` com 22 testes / 0 falhas / 0 pulados em `dcf76388`
    e `e84a04cc`; já em 09/09, horas depois do registro, a classe rodava
    21 / 0 / 0 (`a6ba64d0`), e das 218 execuções da CI entre 09/09 e 11/09
    nenhuma falhou nesses dois testes.

  Não há commit que "corrigiu" o sintoma: ele não aparece nem no ponto de
  registro. A falha original veio do ambiente de quem a observou, sem causa
  identificada. Se reaparecer, reabrir com versão do Chrome, SO e a saída do
  `--dump-dom`. No macOS os testes de navegador só rodam de fato quando o
  Chrome do bundle é encontrado (issue #110).

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

### 93. JS: valor `Bool` de função stdlib é number 1/0 → `boolExpr == true` sempre `false` (paridade cross-target quebrada) — ✅ CORRIGIDO 10/09 (chokepoint `!!` na comparação cobre stdlib + instanceof + coleções)

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

### 94. Interpretador: `==` de Double via `Double.compare` → `NaN == NaN` é `true` (JVM/Native/JS compilados: `false`, IEEE) — ✅ CORRIGIDO 13/09 (interpretador agora IEEE, paridade 4/4)

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
- **Status: ✅ CORRIGIDO 13/09 (lane gate/paridade).** `==`/`!=` de
  Double/Float no interpretador passou a usar `==` primitivo (IEEE), espelhando
  `DCMPL`/`FCMPL` do JVM e `comisd`/`ucomisd` do Native. Afetava
  `KofInterpreterValues.numEq` (caminho `binary` EQ/NE),
  `KofInterpreterOps.compare` (caminho `if (a == b)`, via `cmpResult` com
  `Double.compare`) e `KofInterpreterObjects.numEq` (equals de record com
  campo Double/Float — o `JvmRecordEmitter` usa `DCMPL`+`IFEQ`, IEEE). Ordenação
  (`<`/`<=`/`>`/`>=`) segue `Double.compare` (é ordenação total, não igualdade).
  **Prova:** `KofInterpreterParityTest.doubleIeeeEquality` (NaN==NaN→false,
  NaN!=NaN→true, `if`, vars, `+0.0 == -0.0`→true) + célula `stdsqrt` sem
  exclusão (4 targets, com `+0.0 == -0.0`); `ConformanceMatrixTest` 11/11 e
  `KofInterpreterParityTest` 22/22.

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
  hex-float (`0x1p3`) lança (JVM parseia). **+ sufixo de tipo `d/D/f/F`
  (`"1.0d"`, `"1.0f"`, `"1d"`) lança no Native E no JS (JVM/Script aceitam
  → `1.0`; verificado 13/09 na caça Q4, `math.parseDouble`/`parseDoubleOrDefault`
  herdam: `"1.0d"` → JVM/Script `1.0`, Native `Invalid number`, JS `Cannot
  parse`; `"0x1.8p1"` → JVM/Script `3.0`, Native/JS caem no default).** A
  célula `stdmathparse` não cobre nem hex nem sufixo = cobertura estreita (Q5).
  Paridade bit-exata p/ casos fora
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
  qemu). Print de Double no cross **✅ FECHADO 15/09** (fatia `RtB45`:
  `kof_dtoa` via libc `snprintf`/`strtod`, link dinâmico sob demanda — ver §180);
  `Int/Long.toDouble()` boxing segue `toDouble` undefined (gap
  separado, família FLT001). Menor repro pré-fix: riscv `"2.5".toDouble()` →
  COMP001 `undefined reference to kof_string_to_double`.

### 81. KofJS: `Long` é `Number` (double 53-bit) — ✅ CORRIGIDO 13/09 (opção 5b: BigInt no JS, paridade 64-bit real)

- **Sintoma:** `println("9007199254740993".toLong())` no JS → `9007199254740992`
  (arredondado); `println("12345678901234567890".toLong())` → notação
  exponencial; e o overflow além de `Number.MAX_SAFE_INTEGER` **não** lança
  (o JVM/Native/Script lançam exceção por contrato `Long.parseLong`).
  Medido 10/09 ao escrever o `KofStringParseTest` (JS `toLong`).
- **Causa raiz:** `JsRuntimeUiStdlib` — `Number(s)` (IEEE-754 double); o
  comentário no fonte já assume "sem BigInt". É decisão do **modelo numérico JS**
  (congelado, regra 6), não do parser do bug 79.
- **Decisão (13/09, mantenedora, opção 5b):** `Long` no JS passa a **BigInt** (paridade real de 64-bit; overflow lança como nos outros alvos). Bump não necessário — semântica nova entra já sob `0.4.0-beta` (branch `beta-0.4.0`, mudança documentada aqui + migração do golden JS de `KofStringParseTest` junto, no mesmo commit).
- **✅ Correção 13/09 (Long = BigInt em todo o backend JS):**
  - **Literal** (`JsCallEmitter.literalExpr` + `JsTypeMapper.literalText` p/ field): `9007199254740993n` (sufixo `n` do JS).
  - **Binário** (`binaryExpr` roteia `isLongType(operandType)` → `longBinaryExpr`): aritmética/comparação/bitwise sobre BigInt puro; operandos Number crus envolvidos com `BigInt()` idempotente (promoção Int→Long do JVM); DIV BigInt já trunca p/ zero (LIDIV, sem `Math.trunc` — que não aceita BigInt); EQ/NE via `==`/`!=` loose (5n==5 true, `==` de Kof é conteúdo); USHR → SHR (BigInt não tem `>>>`; JVM também não tem USHR de long sem assinatura errada — SHR é o JVM-like).
  - **Conversões** (`unaryExpr`): I2L → `BigInt(x)`; L2I → `BigInt.asIntN(32, x)` (wrap signed 32-bit EXATO do JVM — `Number()` perde precisão >2^53 e daria 0 onde o JVM dá 1); D2L/F2L → `BigInt(Math.trunc(x))`; L2F/L2D pass-through (Number aceita BigInt em contexto aritmético de FP).
  - **`kof_string_to_long`** (`JsRuntimeUiStdlib`): `BigInt(s)` + range check ±2^63 — overflow LANÇA como `Long.parseLong` do JVM.
  - **Print:** `String(5n)` nativo do JS = "5" — idêntico ao JVM.
- **Prova:** JVM×JS byte-idênticos em 2 repros (literal 2^53+1 exato, mult/sub, `as Int` wrap, toLong 2^63-1, overflow lança→catch, `==` misto long/Int, array de Long, `d as Long` trunc) + `KofStringParseTest` 8/8 com golden JS **unificado** ao JVM (antes: limitado a ±2^53 com nota de divergência) + gate 4-módulos **1647 testes, 0 falhas** (2 erros ambientais GraalJS/node ausente; BackendParityTest long-div `3333333333/4` agora BigInt). riscv/aarch: não se aplicam (nativos já eram 64-bit reais).
- **Registro anterior (substituído):**BigInt no GraalJS rodaria, mas trocar o
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

### 89. Conversão numérica de primitivo `n.toDouble()`/`n.toInt()`/`n.toFloat()`/`n.toLong()` quebrava em TODOS os alvos (não só o LINK nativo) — ✅ CORRIGIDO 13/09 (opção 3a: alias do `as` + warning SEM090; 4 alvos)

- **⚠️ CORREÇÃO 13/09 (medição de EXECUÇÃO, não de compile):** a versão
  anterior dizia "**JVM e JS executam certo**" — era **verde falso**. O probe
  antigo só lia `success=true` do compilador (que mede *compile*, não
  *execução*). Medido agora com execução real (`S89.kf`:
  `main() { var n = 5; println(n.toDouble() == 5.0) }`) no HEAD:
  - **JVM**: `LinkageError`/`ClassFormatError: Illegal class name "" in class
    file Default/Main` — o lowering emite `invokevirtual "".toDouble:()Ljava/lang/Object;`
    (owner VAZIO; `Object.toDouble` não existe).
  - **Native x86/riscv/aarch64**: `undefined reference to toDouble` (link).
  - **Script (interpretador)**: `ERR: java.lang.Integer.toDouble/0`.
  - **JS**: `TypeError: n.toDouble is not a function`.
  - **Workaround `as` funciona nos 4 alvos** (`S89b.kf`: `(n as Double) == 5.0`
    → `true` em JVM/Native/Script/JS).
  Consequência: o fix da decisão 3a deve cobrir **JVM + Script + JS + os 3
  nativos**, não só o emit nativo (a premissa da decisão — "JVM/JS já
  funcionam" — era falsa).
- **Sintoma original (link nativo):** `main() { var n = 5; println(n.toDouble() == 5.0) }` falha no
  link nos 3 nativos — x86: `undefined reference to toDouble`; riscv64/
  aarch64: idem. Os outros alvos (JVM/Script/JS) também quebram — ver
  correção acima. A API **não existe/ não funciona em nenhum** alvo;
  falta o lowering correto em todos.
- **Causa raiz:** o lowering de método de instância em primitivo
  (`ExpressionInstanceCallLowerer`) não tem ramo para conversões numéricas:
  emite um `invokevirtual` genérico com owner vazio (JVM) / um `call <nome>`
  sem runtime (nativo) / reflexão que não existe (Script) / método inexistente
  (JS). Só `String.toDouble` → `kof_string_to_double` (símbolo diferente)
  funciona. O idiom que funciona em TODOS
  os targets (incluindo cross, exit 0 medido 10/09) é o **cast `as`**:
  `n as Double`, `d as Int` (AGENTS.md "Cast: x as Char / big as Int";
  `learn/04`: `Int i = d as Int`) — o `as` lower p/ o intrínseco numérico
  do backend (I2D/D2I/...) que existe nos 3 nativos.
- **✅ DECIDIDO 13/09 (mantenedora, opção 3a + emenda):** `.toDouble()`/`.toInt()`/`.toFloat()`/`.toLong()` em receiver primitivo = **alias do `as`** (truncamento; overflow → throw, paridade JVM) nos 3 nativos, COM **WARNING compile-time** (não erro) quando a conversão pode truncar (`Double→Int/Long`) apontando o `as` como forma explícita. Implementação liberada p/ lane.
- **✅ CORRIGIDO 13/09 (opção 3a implementada):** o lower de method call
  (`ExpressionInstanceCallLowerer`) recebeu ramo §89: receiver primitivo
  OU Unknown (String tem dispatch próprio ANTES — `String.toInt` intacto)
  + método toInt/toLong/toFloat/toDouble + 0 args → emite EXATAMENTE os
  mesmos KofUnary do cast `as` (emitWideningIfNeeded + emitPrimNarrow + I2C).
  `MethodCallTyper` retorna o tipo alvo (senão `var d = n.toDouble()` ficava
  Unknown e o EQ seguinte comparava Object → false). Warning SEM090 (não
  erro) quando Double/Float → Int/Long pode truncar. Prova:
  `CoreRegressionE2ETest.numericConvertMethodAliasOfAs` JVM+JS (trunc 3.7→3,
  negativo -2.5→-2, toLong, toFloat round-trip, toDouble==5.0) + medido
  NATIVO x86 idem (repro manual — sem qemu o cross fica no guard).
  **Verificação independente (lane gate, 13/09):** `S89.kf`/`S89b.kf`/`S89c.kf`
  → **4/4 alvos** (JVM/Native/Script/JS) com `n.toDouble()==5.0`, trunc
  `3.7→3`, negativo `-2.5→-2`, `toDouble` tipado (EQ com Double, não Object) e
  `"3.7".toDouble()` intacto (String dispatch antes) + warning SEM090 emitido.
  **Trava automatizada (lane gate, 13/09):** célula de matriz `numconv`
  (`ConformanceMatrixTest.conformanceCoreArithmetic`) roda o mesmo programa
  nos **4 targets em CI** (antes a prova automatizada era só JVM+JS via
  `runBoth`; o Native era repro manual) — `true/3/-2/5/2.5` 4/4.
- **Registro da decisão anterior (substituída pela de cima):** p/
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

### 106. `json.encode(Map)` quebra em 3 dos 5 alvos (JVM crasha; x86/riscv link error; só Script/JS ok) — ✅ CORRIGIDO 13/09 (opção 2b: chaves sorted; JVM/x86/Script/JS — riscv/aarch gap de porta)

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
- **✅ DECIDIDO 13/09 (mantenedora, opção 2b):** superfície = objeto JSON com chaves **SORTED** (determinismo > ordem de inserção). Correção liberada: JVM (itera `entrySet`), nativos (ramo próprio no dispatch), gate `JSN00x` compile-time no que faltar + golden de ordem nos 5 alvos. Sai de "ABERTO por decisão" para lane implementável. A correção tem 3 partes: gate honesto no compile-time até a superfície decidir (diagnóstico `JSN00x` no estilo JSN004 no dispatch de Map em nativos) + decisão de formato + ramos JVM (entries) e nativo. Registra aqui; NÃO vira edição de semântica sem decisão.
- **Pista de teste faltante (para quem fechar):** `json.encode(mapOf(...))` nos 5 alvos com golden de ordem (provavelmente insertion-order = `LinkedHashMap` semantics, mas é a decisão).
- **✅ CORRIGIDO 13/09 (opção 2b implementada):** superfície = objeto JSON com chaves **SORTED** (TreeSet/selection-sort). Call-site baixa `kof_json_encode_map(map, tagDoValor)` (tag = mesma tabela de `listTag`: 0=int, 1=string, 2=bool). JVM: `JvmRuntimeJson.kof_json_encode_map(Map,int)` (TreeSet keys, encode por tag); nativo x86: asm próprio `kof_json_encode_map` em `RuntimeJsonEncode.java` (selection-sort com `kof_string_compare_to`, builder JSON); interpretador: `KofInterpreterRuntime.encodeMapTagged`; dispatch: ramo `isMap` em `JsonDispatch.encodeFunction`. Riscv/aarch64: asm próprio pendente (gap de porta — segue como XXX00x na matriz). Prova: `JsonCompleteE2ETest.jvmEncodeMapSortedKeys` (golden `{"a":1,"b":2}
  {"x":"w","y":"z"}`) + `nativeEncodeMapSortedKeys` (golden `{"a":1,"b":2}`) verdes 13/09.
- **✅ RESIDUAL JS CORRIGIDO 13/09 (lane gate, dono 192.168.100.15):** o
  fechamento acima declarava "JVM/x86/Script/JS", mas **o JS nunca foi
  testado** (o `JsonCompleteE2ETest` só cobria JVM+Native) e devolvia `{}`
  — `json.encode(Map)` no JS caía no `JSON.stringify` genérico, e
  `JSON.stringify(new Map())` é `{}` (Map não tem own enumerable props).
  Achado pela nova célula de matriz **`jsonenc-map`** (antes inexistente —
  o "segue na matriz" do fechamento era falso). Fix: helper
  `kofJsonEncodeMap(map, tag)` em `JsRuntimeUiJsonMap` (chaves SORTED +
  `JSON.stringify` por chave/valor) + ramo `kof_json_encode_map` no
  `JsRuntimeOps`. Prova: `ConformanceMatrixTest#conformanceJson` célula
  `jsonenc-map` (4 alvos: `{"a":1,"b":2}` / `{"a":"first","z":"last"}` / `{}`).

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
  o dispatcher `NativeRiscvCrossOps` (ramos `valueOf` List/Map/Set) roteia
  coleção de Double/Float para `kof_elem_to_string` tags 4/5 →
  `kof_double_to_string`/`kof_float_to_string` — **FLT001 FECHADO 15/09** (fatia
  `RtB45`, libc via link dinâmico); antes levantava FLT001 em tempo de
  compilação (R6/R7: diagnóstico, nunca `?` silencioso nem lixo). Disciplina: **todo estado do laço vive em SLOT do frame**
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
  separador → FAIL); `nativeCollectionPrintMatchesJvmGolden` agora inclui uma
  linha `listOf(1.5, 2.0)` / `listOf(1.5f, 2.5f)` (FLT001 fechado 15/09 — fatia
  `RtB45`), o antigo `nativeCollectionPrintFloatDoubleRefusedHonest` virou
  `nativeValueOfDoubleFloatMatchesJvmGolden` (oracle riscv + aarch).
- **Faces que FICAM ABERTAS neste bug (paridade parcial, diagnosticada R6):**
  - **`?` honesto (não lixo):** elemento **record** ou **coleção aninhada**
    (tag 6) imprime `?` nos 3 targets nativos — é a recusa visível, nunca
    lixo de ponteiro. Fecha junto com o §104b-ii (equals/toString de
    conteúdo) + propagação de tag recursiva na emissão (dispatch-time hoje
    só conhece o tipo estático do elem; record/nested precisa vtable
    `toString` + sub-tag).
  - **Double/Float no cross:** **✅ FECHADO 15/09** — `println`/`valueOf`/concat
    e coleções (tags 4/5) convertem FP→string via `kof_dtoa` (fatia `RtB45`,
    libc `snprintf`/`strtod`, link dinâmico); o x86 já tinha.
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

### 117. Native x86: `cancelled()` usa tabela de 256 slots por hash de TID → colisão (cancel de um worker vaza/outro apaga) — ✅ CORRIGIDO 13/09 (opção 8a: tabela por TID real + probe linear; `3734f2aa`)  *(renumerado de §101 na reconciliação do merge beta-0.3.0→beta-0.4.0 11/09 — colidiu com a série ativa §95–§114)*

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
- **✅ CORRIGIDO 13/09 (opção 8a implementada — sem mudança de contrato,
  sem TLS da glibc):** a tabela 256 passou a ser indexada por **TID REAL
  (pthread_self) com probe linear** (`kof_cancel_slots`: 256 entries de 16B
  [tid, flag], hash phi + probe; tid=0 = vazio). O trampoline registra
  `(TID, flag=0)` ANTES da task e guarda a entry no handle novo
  (`handle->cancelEntry@32`, alloc 32→48); ao terminar, ZERA a própria entry
  (o `movb $0` cego por hash era o vazamento). `kof_cancel(h)` resolve o
  TID do handle → `kof_cancel_slot_find` → `flag=1` (só a entry DO TID do
  handle); `kof_cancelled()` resolve o TID ATUAL → flag. Colisão de hash é
  inócua: probe resolve por chave real. `RuntimeConcurrency.java` only.
  Prova: `KofConcurrency2Test.cancelDoesNotLeakAcrossWorkersNative`
  (20 iterações: worker longo cancelado + worker seguinte verifica flag
  limpa — o cenário que o hash truncado apagava) + suíte de concorrência
  34/0 (incl. cancelCooperativeJvm/Native e cancelledOutsideIsFalse).
  **Verificação independente (lane gate, 13/09):** `mvn compile` verde +
  `KofConcurrency2Test` **34/0** (1 skip de toolchain) re-executado no HEAD.

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

### 129. [collection] Native `Set.remove(x)` deletava o elemento no ÍNDICE == tag, não o encontrado — silent data corruption (pior que crash) — ✅ CORRIGIDO 11/09 (x86; riscv já estava certo; aarch traduz)

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

### 128. [decompile] kof-cli `DecompileTest#recoversStatementSwitchAndRunsIt` VERMELHO — decompilador emite statement-switch com `var` só no 1º case → SEM011 no recompile — ✅ CORRIGIDO 11/09 (consolidado no §135 — fix com hoist de locals + `static` na assinatura + teste executando o decompilado; DecompileTest 45/45)

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

### 127. [collection] JS: `map.get/remove` de MISS com VALOR primitivo devolve `null` (JVM/Native/Script devolvem o default `0`/`false`) — ✅ CORRIGIDO 12/09 (célula `wrongkey` 5/5 sem exclusões)

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

### 125. `println(<primitivo>? null)` (Int?/Bool?/... null): JVM **VerifyError** na carga + Script **NoSuchMethodError `Integer.valueOf/1`**; Native imprime `0` — ✅ CORRIGIDO 12/09 (decisão da mantenedora: opção A — alinhar ao precedente do map-miss, `0`) · **EXTENSÃO 12/09** (ramo null de if/switch em retorno/slot primitivo-nullable: mesma opção A, fold `foldNullablePrimBranches`) · **REVOGADO 15/09 (D-NULL-INTENT, e04f10ff) — N1 IMPLEMENTADO**: opção A foi substituída pelo contrato boxed real (`Nullable(primitivo)` carrega null de verdade) em JVM+Script+JS — fecha #259/#266, ver §241 e `NullablePrimitiveE2ETest`/`ConformanceMatrixTest#nullableprint`. Native (N2) segue com o comportamento antigo, fila separada.

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
- **EXTENSÃO 12/09 (mesma opção A, caçada da "reteste tudo"):** o fold do
  `ReturnStmt`/`VarDecl` só pegava o `null` LITERAL no topo. As formas em que
  o `null` mora num **RAMO** de if/switch (`Int? f() = if (c) x else null`,
  `Int f() = if (c) x else null`, `Int? f() = switch { default -> null }`,
  `Int? v = if (c) x else null`) seguiam vivas: o `branchTypeOrNullAsRef` faz
  o ramo null ser `Object` → join heterogêneo → o ramo PRIMITIVO é boxado →
  `ireturn`/`istore` sobre referência (**VerifyError JVM** + **`Integer.valueOf/1`
  no interpretador**), enquanto Native/JS imprimiam `0`. Mesma doença do §125,
  outro sítio. **Fix (contrato congelado, zero decisão nova):**
  `CompilerComparisons.foldNullablePrimBranches(e, destType)` — quando o
  destino do `ReturnStmt` OU do `VarDecl` EXPLÍCITO é primitivo ou
  `Nullable(primitivo)`, reescreve cada ramo `null` (profundo, só if/switch)
  para o default do primitivo; o join deixa de ser heterogêneo e os 4 targets
  convergem no `0`/`false` da opção A. **Não toca**: `var`/`val` INFERIDO
  (`var a = if(c) 1 else null` — §68a, decisão de contrato, segue VerifyError
  honesto) e if/switch STANDALONE (`println(if(c)1 else null)` — sem destino
  tipado, semântica atual preservada; medido V3 = sem regressão). Extração
  colateral `CapturedVarBox` manteve `StatementLowerer` em 499 (< gate 500).
  **Prova:** célula `nullableprint` ampliada (+`en(7)`/`en(-7)`, slot `Int? v
  = if(false)9 else null`, `bn(-1)` → `7/0/0/false`, 4/4 sem exclusão) +
  `KofInterpreterParityTest.{expr-body-null-branch, expr-body-switch-null-branch,
  annotated-slot-null-branch}` (3 paridades); suíte compiler 1405/0-fail (13
  err=`node` ambiente, +1 skip cross), script/kof-c/cli inalterados.
- **⚠️ REVOGADA 15/09 por DECISIONS §D-NULL-INTENT (mantenedora, em pessoa):**
  a "opção A" (o `null` dobra para o default do primitivo, `ni()==null` é
  `false`, `println(ni())` é `0`) NÃO é mais o contrato. Medido o colapso
  silencioso: `Int? maybe(){return null}` → `maybe(2)==null` dá `false` e
  imprime `0` (JVM+Native); `Int f(){return null}` → `0` SEM diagnóstico.
  Ambos são violações R6. Nullability agora é por INTENÇÃO EXPLÍCITA (a
  própria comparação `== null` é o sinal); onde a intenção existe, null é
  REAL nos 4 targets; onde não existe, null nunca chega em silêncio. A fila
  é N1→N4 do D-NULL-INTENT (roadmap §23). A célula `nullableprint` da matriz
  e as 3 paridades null-branch do `KofInterpreterParityTest` codificam a
  opção A e DEVEM virar quando N1 pousar (não tocá-las antes — regra 1: a
  suíte é o gate; mudar o oracle no MESMO commit do comportamento).

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


### 127. [OTP] JVM: cast para tipo de função (`x as () -> Int`) gera bytecode inválido (VerifyError) — ✅ CORRIGIDO 13/09 (decisão 9a: parser parseia `as ()->T` como type-ref; checkcast vai p/ interface SAM sintética)
- **Reprodução:** `var o: Object = (Object)(() -> 5)`… em Kof puro:
  `fun(Int x) { var g = x as () -> Int; return g() }` — `fun(() -> 9)` →
  **compila ok** mas ao rodar: `VerifyError: Operand stack underflow` /
  `checkcast // class "?"` (checkcast para classe INEXISTENTE — o tipo de
  função não tem erasure mapeado no cast).
- **Menor repro:** `main(){ var l = listOf(() -> 5); var g = l.get(0) as () -> Int; println(g()==5) }` (out_B107).
- **Correção (13/09, lane gate/paridade):** o RHS de `as` era parseado por
  `parsePrimary`, e `() -> Int` casa `looksLikeLambdaParams` → virava
  **LambdaExpr** (não type-ref) → `ExpressionBinaryLowerer` ficava com
  `targetType = UNKNOWN` → `JvmOpEmitter` mapeava p/ `"?"`. Fix em 3 pontos:
  (1) `ExpressionParser.parseBinary` — `as` com lookahead `(`…`)` `->` usa
  `TypeParser.parseTypeRef` (novo `looksLikeFunctionTypeRef`); (2)
  `ExpressionBinaryLowerer` — `FunctionType` no alvo do checkcast vira a
  interface SAM sintética (`CompilerLambdaClass.lambdaInterfaceType`, a mesma
  do dispatch); (3) `SemExpressionTyper` — o `IdentifierExpr` com type-ref
  `"(...) -> ..."` não dispara SEM011. Prova: `LambdaE2ETest.castToFunctionType`
  (JVM+Native, `true`/`7`); sonda B127 4/4 targets.
  **Trava automatizada (lane gate, 13/09):** célula de matriz `castfn`
  (`ConformanceMatrixTest.conformanceCoreFunctions`) roda o repro nos **4
  targets em CI** (`true` 4/4) — antes a prova automatizada era só JVM+Native;
  Script/JS eram sonda manual.
- **Impacto OTP:** DD-OTP-02 propunha `child(id, factory, ...)` com `factory`
  como tipo de função. Com este fix, a forma "Object/qualquer + `as () -> T`"
  RODA nos 4 targets; a alternativa por **interface** (abaixo) segue válida.
- **Workaround verificado (forma que RODA nos targets):** usar **interface** como
  contrato da fábrica (DD-OTP-06 "factory nova sempre"): `interface Worker { Int
  criar() }` + `class W implements Worker { criar(){...} }` — dispatch virtual de
  interface funciona nos 5 targets (spike S2/S4: supervisor puro-Kof com campo
  `Worker` + `spawn { w.criar() }` + try/await/catch = captura/limit/restart tudo
  verde no JVM). O campo tipado como `() -> Int` dá PARSE016 (parser de corpo de
  classe não aceita LPAREN como início de campo — `ClassMemberParser`).
- **Não-regressão:** a sonda B127c (`listOf` heterogêneo de lambdas com
  assinaturas DIFERENTES e dispatch por índice) já era CCE no JVM antes deste
  fix — é o bug separado do erasure de assinatura da lambda (§8/`Lambda1` vs
  `Lambda0`), NÃO regressão desta unidade. O caso homogêneo (o que este bug
  cobria) passou de COMPILE-FAIL/VerifyError para verde 4/4.


### 128. [OTP] JVM: resultado de `selectAny`/`await` de Handle<Int> atribuído a var e usado como Int → VerifyError — ✅ CORRIGIDO 12/09 (JVM; await já caía no unbox, selectAny não) (spike OTP #83 11/09)
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


### 129. [OTP] Native x86_64: `throw` dentro de worker `spawn` → unwinder faz longjmp no handler da THREAD MAIN (crash/hang cross-thread) — ✅ CORRIGIDO 15/09 (lane development `192.168.100.18`, DECISIONS §2 opção B; só x86_64)
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
- **RESOLUÇÃO (15/09, lane development `192.168.100.18`) — opção B, DECISIONS §2:**
  a chain agora é **TLS local-exec** (`.section .tbss,"awT",@nobits` +
  `%fs:kof_exc_chain@tpoff`) em vez de um `.data` global: `RuntimeGc.emitPanic`,
  `NativeMethodEmitter` (`KofTryStart`/`KofTryEnd`), `RuntimeDb4` (frames de tx) e
  `RuntimeStringParseOrDefault` (os 4 emissores da stack do root cause do §129). O
  binário x86 é dinamicamente ligado (`-lc`), então o `ld.so` inicializa o TLS da
  main e o `pthread_create` o do worker (validado experimentalmente: um worker que
  escreve na chain não toca no valor da main). O `kof_spawn_trampoline` instala um
  **frame de handler por worker** e, num `throw` sem handler interno, publica a
  causa no handle (`handle->exc`, offset 40) em vez de desenrolar para a main;
  `kof_await`/`kof_await_timeout`/`kof_select_any` a relançam no consumidor
  (paridade JVM). Dois sub-defeitos achados e corrigidos no caminho: (a) o frame do
  handler guarda o handle em `32(%rsp)` porque o worker pode clobberar o `%r12`
  callee-saved em que o código antigo confiava; (b) `kof_await` agora zera o TID já
  juntado para o `kof_spawn_join_all` implícito no fim da `main` nunca dar double
  join (SIGSEGV em `__pthread_clockjoin_ex` com TCB reciclado — reproduzido com 50
  throwers spawnados). `CompilerSupervisor` só emite `OTP001` para **riscv/aarch**
  (clone cru, sem TLS) — esses seguem gate honesto (o par
  `selectAny`/CONC001 foi fechado 15/09 por `e8364c97`; o blocker do OTP é o TLS agora). Prova:
  `KofConcurrency2Test.spawnWorkerThrow{AwaitedAndCaught,UnhandledThrowPropagates,IsolatedFromSiblings,PropagatesThroughSelectAny}Native`
  (4/4, + 10× repetição do stress de 50 workers sem SIGSEGV),
  `KofSupervisorE2ETest.supervisorNativeParityX86` (restarts=2/escaladas=2/fabrica=3)
  e `supervisorNativeS2ParityX86` (3 filhos, laço selectAny único),
  `crossGateOtp001` (riscv+aarch); `KofSupervisorE2ETest` 15/15,
  `KofConcurrency2Test` 40/0 (1 skip), `ExceptionsE2ETest` 11/0, `NativeE2ETest`
  65/0; suíte completa do `kof-compiler` 1777 testes / 1 falha = o
  `[ifexpr-heterogeneous-direct]` Native SIGSEGV pré-existente (§205, outra lane;
  idêntico no HEAD `3a0826df`).


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


### 131. Frontend/Backend: sobrecarga de método por ARIDADE na mesma classe quebra (SEM013 no JVM; colisão de símbolo no NATIVE) — ✅ CORRIGIDO 13/09 (opção 10a: 4 backends; `18a64d45`)
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
- **✅ CORRIGIDO 13/09 (opção 10a implementada, 4 backend faces):**
  (a) **symtable:** `SymbolTable.define` faz merge de `MethodSymbol` homônimo
  num `MethodSet` (espelho do `ConstructorSet`) com `select(argCount, argTypes)`
  — aridade + compatibilidade (`TypeChecker.isAssignable`); o typer
  (`MemberCallTyper`) seleciona e registra em `resolvedMethods()`.
  (b) **nativo x86/riscv:** `sigMangles` estendido — método de CLASSE leva
  sufixo de assinatura SÓ quando sobrecarregado (`classHasOverload`: 2+ defs
  do nome na própria classe); classes sem overload mantêm símbolo cru (vtable
  idêntica, zero churn). `collectVirtualMethods` dá slot PRÓPRIO por overload
  (antes o 2º def sobrescrevia o slot E os dois `.globl` colidiam);
  `findVirtualMethodIndex(owner, name, argCount)` resolve pelo símbolo
  tageado da aridade pedida.
  (c) **JS:** o mangle de assinatura (SG-011B) agora cobre TODAS as classes
  (antes só Main) — métodos sobrecarregados ganham nome JS `$`-tageado no
  `lowerFunction` E no call-site structural (`JsCallEmitter`), mesmo mangle.
  (d) **JVM:** dispatch por overload resolvido no typer (KofCall já carrega
  a assinatura certa) — o descritor JVM faz o resto.
  Prova: `CoreRegressionE2ETest.methodOverloadByArity` JVM+JS (`6\n7`:
  aridade 1 delegando `this.m(a,1)`, aridade 2 direto) + nativo x86 medido
  (`6|7`) + gate 4-módulos BUILD SUCCESS (1642, 0 falhas reais; 2 erros
  ambientais GraalJS).
  **Trava automatizada (lane gate, 13/09):** célula de matriz `methodoverload`
  (`ConformanceMatrixTest.conformanceCoreFunctions`) roda `42/7` nos **4
  targets em CI** (antes a prova automatizada era só JVM+JS via `runBoth`; o
  Native/Script era repro manual) — 4/4.
  **⚠️ RESIDUAL achado+corrigido (lane gate, 13/09):** o dispatch nativo
  resolvia a vtable só pela **ARIDADE** — overload de MESMA aridade e TIPOS
  diferentes (`twice(Int)`/`twice(String)`) caía no 1º slot: passar `String`
  p/ um parâmetro `Int` dava **SIGSEGV** (JVM/Script/JS sempre corretos —
  descritor/SAM). Repro `OV1.kf`; `methodOverloadByArity` (do fechamento) só
  cobria aridade. **Fix:** `findVirtualMethodIndex` passa a casar **nome +
  TIPOS do call site** (`NativeClassMeta.methodsForCall`, casamento exato com
  fallback p/ a 1ª assinatura da aridade quando o arg é `Unknown`); x86 e
  riscv/aarch passam `kc.parameterTypes()`. Prova: célula
  `methodoverloadtype` (4 targets, `42/abab`) + `OV1`/`CLSOV` 4/4.


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
  do §131, espelho OTP) foi **FECHADA 13/09** (`18a64d45`, decisão 10a) —
  ver §131 (symtable de classe, vtable com slot por overload, mangle JS).

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
  (lição escrita em `training/anti-patterns/asm-comment-escape.md`
  §"dupla interpretação" — ex-`plan-spring-independence.md` §"Pegadinha de text block",
  e usada em `RuntimeMemory.java:228`,
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
  pegadinha está documentada em `training/anti-patterns/asm-comment-escape.md` mas não era
  CHECKLIST obrigatório da lane Native.


### 139. JS: `<call-nullable> == null` como operando DIRETO de comparação → COMP002 "expression stack underflow" (JVM/Native/Script aceitam) — ✅ CORRIGIDO 12/09 (`39da8416`, unidade do §125 — doc sincronizada 12/09, achado na triagem pós-§139)

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
- **✅ CORRIGIDO 12/09 (`39da8416`, unidade do §125) — exatamente a rota
  "parser consome o POP":** `JsExpressionParser.parseExpressionFragment` trata
  `KofPop`/`KofPop2` como operação (não mais como FIM de fragmento): popa o
  topo e, sendo expressão com efeito colateral (`JsCall`/`JsSequence`/`JsAwait`
  ou binário contendo call), move p/ o `preambleExprs` (o valor é avaliado e
  descartado — como no statement); só `KofPop` com pilha VAZIA continua
  encerrando o fragmento. A célula `nullableprint` da matriz (sem exclusões,
  4 targets) trava os dois repros (`ni() == null`, `mapOf("a",1).get("zz")
  == null` → `false`); `ConformanceMatrixTest` 11/11 medido 12/09.
- **Prioridade:** baixa (workaround: slot `val v = ...; v == null`).

---

### 140. Gate `check_500.sh` era decorativo: nunca entrou no CI → 17 violadores da regra ≤500 se acumularam em silêncio (SemanticAnalyzer 396→535, Parser 456→513 — classes das Fases 6/7 "✅ FEITA") — ✅ CORRIGIDO 12/09 (ratchet + job no CI)

- **Sintoma (achado 12/09 na triagem da fila dev, ao procurar o próximo item do
  PLAN-SOLID-500):** `bash scripts/check_500.sh` → FALHOU com **17 classes de
  produção acima de 500 linhas** (maior: `nat/NativeBackend.java` 664). O gate
  foi escrito na Fase 9 (06/09, "gate permanente") e NENHUM workflow o chama
  (`grep -rn check_500 .github/` → vazio). Consequência medida: classes que a
  tabela de status do plano declara "✅ FEITA ≤500" voltaram a crescer sem que
  ninguém (humano, agente ou pipeline) percebesse — `SemanticAnalyzer` (Fase 6,
  "396 + 8 classes") está em 535 hoje; os commits que o incharam (`40abd0ed`
  SEM047, `b55c24c0` sobrecarga) não rodaram o gate porque só `mvn test` roda
  no CI e `mvn test` não invoca o script.
- **Causa raiz:** gate fora do pipeline é opinião, não contrato. A regra ≤500 é
  de ferro (AGENTS.md), mas o mecanismo de fiscalização vivia só na disciplina
  de cada agente — e agentes que rodam `mvn -pl` subset nunca veem o script.
- **Menor repro:** `scripts/check_500.sh` no HEAD `768b26fd` → exit 1, 17 lines.
- **Correção (12/09, esta unidade):** o gate vira **ratchet com baseline de
  dívida**: `scripts/check_500-baseline.txt` congela as 17 dívidas existentes
  (contagem por arquivo); o script falha se (a) arquivo NOVO passa de 500,
  (b) dívida do baseline CRESCE, (c) baseline some; passa com AVISO quando a
  dívida diminui (o split feito deve remover a linha — `--update-baseline`).
  Não é "consertar o teste para passar": a dívida REAL fica documentada e
  travada de crescer; o plano (Fases 2/3 + varredura) continua devendo os
  splits, agora com meta-visível no baseline que só encolhe. LIGADO no
  `build-and-test` do `.github/workflows/ci.yml` (step antes do build).
- **Prova:** casos sabotados medidos no commit — linha removida do baseline →
  "NOVA DÍVIDA" exit 1; contagem reduzida 513→450 no baseline → "DÍVIDA
  CRESCEU" exit 1; estado real → OK exit 0. YAML validado.
- **Prioridade:** processual (não afeta output do compilador). **Follow-up
  honesto:** zerar o baseline = as Fases 2/3+resíduos do PLAN-SOLID-500; as 17
  dívidas listadas são a fila real dessa frente, não a tabela de 9 fases.

### 141. Native: `"a" + <Int?-null>` (concatenação de primitivo-nullable) → LIXO de ponteiro (JVM/Script dão `a0`) — ✅ CORRIGIDO 12/09 (achado no "reteste tudo", pré-existente ao §125)

- **Menor repro (Q1, medido 12/09):** `Int? ni() { return null }` +
  `println("a" + ni())` → **Native**: `a1297530912` (lixo — ponteiro formatado
  como int); **JVM/Script**: `a0` ✅; era INDEPENDENTE do §125 (medido com
  `git stash` no HEAD: mesmo lixo sem o fold). Concatenação de `Int` não-
  -nullable (`"a" + five()`) já dava `a5` correto no Native.
- **Causa raiz:** `ExpressionBinaryLowerer` (lado-esquerdo ~154 e lado-direito
  ~161) faz DOIS passos por operando não-String: (i) `boxPrimitive(accType)`
  — que DESEMPACOTA `Nullable(Int)`→Int e, no Native, chama `kof_int_to_string`
  (deixa uma STRING na pilha); (ii) um `valueOf` externo cujo ternário de
  escolha-de-param testava `!(accType instanceof Type.PrimitiveType)` (NÃO
  desempacota) → p/ `Nullable(Int)` o teste passava e o valueOf era emitido com
  `argType=Nullable(Int)` → o dispatcher nativo (`NativeX86Calls:166` já usa o
  inner Int) rodava `kof_int_to_string` SOBRE O PONTEIRO DA STRING do passo (i)
  = lixo. O `NativeX86Calls` estava certo; o BUG era o ternário do emitir NÃO
  desempacotar, divergindo do `boxPrimitive` logo acima (assimetria
  `isPrimitiveType` vs `instanceof PrimitiveType` — a MESMA classe de causa do
  §125).
- **Fix (1 sítio ×2 lados):** `accStringified = !isString && isPrimitiveType`
  (o MESMO guard que dispara o `boxPrimitive`) → se o box/stringuificação já
  rodou, o valueOf externo recebe `UNKNOWN` (no-op). Nullable(primitivo) deixa
  de cair no caminho duplo. Comportamento inalterado p/ Int/String/record/
  coleção não-nullable (o guard é idêntico ao antigo nesses casos).
- **Prova:** célula `nullableprint` ampliada (+`println("a" + ni())`→`a0`,
  `println(ni() + "b")`→`0b`, 4/4 sem exclusão); `NativeE2ETest` 64/64
  byte-idêntico (o caminho de print não-regrediu); `ConformanceMatrixTest`
  11/11. Faces riscv/aarch: o dispatcher `NativeRiscvCrossOps` lê o INNER do
  Nullable (linhas 22/46/52/58) — mesmo valor `0` do golden; prova cross sob
  qemu no job `cross-native`.
- **Prioridade:** média-baixa (lixo ruidoso, workaround `if (x != null)`).

### 142. Native: descarte de expressão `Long`/`Double` (POP2) desbalanceava a pilha → **SIGSEGV** (`m.put(_,2L)` statement, `d==null`/`x==null`) — ✅ CORRIGIDO 12/09 (achado ao travar `mapwiden`; causa raiz NÃO era o map)

- **Menor repro (Y2d/Y2e/S_a/N2/LD, medido 12/09):**
  `var m = mapOf("a",1L); m.put("b",2L); println(m.size)` → **SIGSEGV** no
  Native x86. `m.put("b",2)` (valor Int) NÃO crasha; só com o valor `Long`.
  O `put` em si completa (um `println("ok")` logo depois sai), mas QUALQUER
  uso do mapa depois (size/get) crasha. Generaliza para qualquer expressão
  `Long`/`Double` descartada: `var d = 2.5; println(d == null)` também.
- **Causa raiz (asm disassembly, `objdump -d`):** no backend nativo **todo
  valor de pilha é 1 qword** — inclusive `Long`/`Double` (o literal `1L` vira
  `movq $1,%rax; pushq %rax`; `pushRiscv`/`pushq` sempre 8 bytes). Mas o IR
  emite `KofPop2` (semântica JVM category-2) para descartar o resultado de
  uma expressão double-width, e os dois emissores nativos mapeavam
  `KofPop2 → addq $16` (x86) / `addi sp,sp,16` (cross). Resultado: descarta
  16 bytes onde só 8 foram empilhados → `%rsp` sobe 8 além do frame → o
  primeiro `push` seguinte (o `System.out` do `println`) **pisa no slot
  local `m`** (`-0x10(%rbp)`); o `mov -0x10(%rbp),%rax` lê o `System.out`
  como se fosse o mapa; `kof_map_size`/`kof_map_get` sobre o objeto errado →
  deref de lixo → SIGSEGV. Prova no asm: S_a vs S_c diferiam em UMA instrução
  (`add $0x10` vs `add $0x8`), e a sequência pós-put mostrava o push de
  `System.out` colidindo com o local.
- **Fix (IR compartilhado; aarch64 herda via tradutor):**
  `NativeMethodEmitter` (x86) e `NativeRiscvCrossEmit` (riscv/aarch) passam a
  mapear `KofPop2 → addq $8` / `addi sp,sp,8` — descarta 1 qword, a
  convenção real do nativo. O `KofPop` (1 qword) fica igual; `KofDup2`/
  `DupX1`/`DupX2` NÃO são afetados (duplicam pares de refs/índices de 1 qword
  cada, corretos).
- **Prova:** Y2d/Y2e/S_a/N2/LD agora `rc=0` com a saída correta; célula NOVA
  `longdiscard` (`2/false/false`) 4/4 + `mapwiden` sem exclusão 4/4;
  suíte compiler 1408/0-fail (13 err node amb); `mapint`/`mapgetprim`/
  `mapmutret`/`wrongkey` intactos.
- **Prioridade:** era alta (crash em qualquer descarte Long/Double nativo).

### 143. Widening numérico abençoado (§126 "Int em Long passa") em ESCRITA de coleção pinada → JVM **VerifyError/CCE** (Native/Script acertavam) — ✅ CORRIGIDO 12/09 (B1; o §121/array-store nunca chegou nas coleções)

- **Menor repro (B1a/X2/Y2/M1/L2, medido 12/09):** `listOf(1L,2L).add(3)` →
  JVM **VerifyError** "integer not assignable to long_2nd" (o add boxeia pelo
  tipo PINADO `Long` sobre um int cru width-1 na pilha); `mapOf(_,1L).put(_,2)`
  → **ClassCastException** no `get`; `listOf(1, 2.5)` nem passava (§144).
  Native/Script já davam `[1,2,3]`/`2` — o widening era o caminho CORRETO, só
  o JVM quebrava. O §126 (opção ii) **abençoa widening numérico** ("Int em
  Long passa"), e o §121 criou o precedente (array-store `new Long[4]; c[1]=9`
  emite I2L). As coleções foram **esquecidas**: só a rejeição (§126) foi ligada,
  a conversão nunca.
- **Fix (IR compartilhado → JVM/Native/JS de uma vez):** `CompilerEmissionHelpers.coerceStoreWiden`
  — aplica `emitWideningIfNeeded` (que SÓ promove I2L/I2F/I2D/L2F/L2D/D2F,
  nunca trunca) no arg de VALOR ANTES do store. Chamado nos sítios de escrita:
  `CollectionCallLowerer` (list add arg0 / set arg1, map put arg1) e
  `ExpressionStaticCallLowerer` (literais `listOf`/`mapOf`, §144 abaixo);
  `emitArgsCoercingValue` atualiza `argTypes` p/ o tipo pinado (o box JVM é
  guiado por paramTypes). Native: I2L é no-op semântico (heap já 8-byte); JS:
  I2L já é identity (`JsCallEmitter:373`). **Rejeição (§144) e widening não
  numérico (int↔bool/char) intocados** — zero regressão (G1–G11 verdes).
- **Prova:** células NOVAS `collwiden` (List add/set widening, 4/4 `3/4/3`) e
  `mapwiden` (Map put widening, JVM/Script/JS `2/1`, native §142 excluído);
  `ConformanceMatrixTest` 11→13/13, `ConformanceMatrixDocTest` 1/1; suíte
  compiler 1407/0-fail (13 err node amb). check_500: `CollectionWrites`
  extraído (433 linhas) mantém `CollectionCallLowerer` < 500.

### 144. Narrowing numérico em escrita de coleção (Long→Int, Double→Int) NÃO rejeitado: JVM **VerifyError**, Native **TRUNCA em silêncio** (`5000000000`→`705032704`), Script preserva — ✅ CORRIGIDO 12/09 (B1b/§126 opção ii: rejeitar em compile-time SEM056) + literais `listOf`/`mapOf` cobertos (B1c)

- **Menor repro (D3/SC3/X1/Y3/G12, medido 12/09):** `listOf(1,2).add(5000000000L)`
  → JVM VerifyError, Native **`[1, 2, 705032704]`** (truncou o Long p/ Int
  sem avisar — R6 violada), Script `[1,2,5000000000]`. O `pollutesPinned` do
  §126 só pegava String↔não-String: `isString(p) == isString(a)` deixava
  Int↔Long/Double passar. No `listOf(1, 2.5)` o VALOR nem era validado (caminho
  literal em `ExpressionStaticCallLowerer` pulava o §126 inteiro).
- **Fix:** `CollectionWrites.pollutesPinned` estende p/ narrowing numérico
  (ambos `int/long/float/double`, `primWidth(arg) > primWidth(slot)`) →
  **SEM056** em compile-time (mesma decisão ii do §126 — "coleções Kof são
  homogêneas"). `int↔bool`/`char↔int` (G2–G5) ficam FORA: medidos consistentes
  nos 3, o §126 manda rejeitar SÓ o que quebra. Caminho LITERAL
  (`listOf`/`mapOf` em `ExpressionStaticCallLowerer`) passa a chamar o MESMO
  `pollutesPinned` + `coerceStoreWiden` (o buraco B1c).
- **Prova:** `listOf(1, 2.5)`/`listOf(_,Long)`/`mapOf(_,Double)` agora
  SEM056 nos 3 (sondas L1/M3/X1/SC3/D3); widening continua passando (§143);
  `SemanticResolutionTest` SEM056 (String) intacto; suíte compiler 1407/0-fail.
- **Nota de escopo:** `setOf` widening/narrowing NÃO tem `get` observável (só
  `contains`/`size`), e é coerido igual pelo `coerceStoreWiden` do add nativo
  quando aplicável; deixado como está (M2 verde nos 3).

## Issue #101 (reporte externo PublioSantos, 12/09) — 3 bugs de calculator, todos REPRODUZIDOS neste HEAD (`356f33b9`, worktree limpo; CLI da release 0.3.22-beta do reporter bate com o do repo)

### 145. `String.isEmpty()` não estava no registro de String → JVM `NoSuchMethodError` (descritor `()Ljava/lang/Object;`), Native link-fail `java_lang_String_isEmpty`, JS `t.isEmpty is not a function` — ✅ CORRIGIDO 12/09 (x86+riscv/aarch+JVM+Script+JS; issue #101)

- **Menor repro (medido 12/09 no worktree):** `main() { var s = "abc"; var t = s.trim(); if (!t.isEmpty()) { println("ok") } }` →
  JVM: `NoSuchMethodError: 'java.lang.Object java.lang.String.isEmpty()'`;
  Native: `ld: undefined reference to 'java_lang_String_isEmpty'`;
  JS: `TypeError: t.isEmpty is not a function`. JVM depende do receiver:
  `var s="abc"; s.isEmpty()` ✅ (receiver de literal/pilha cai no caminho bom
  — `javap`: `invokevirtual String.isEmpty:()Z`), mas `var t = s.trim();
  t.isEmpty()` e `s.trim().isEmpty()` ❌ (receiver de method call com tipo
  INFERIDO → `()Ljava/lang/Object;`).
- **Causa raiz:** `StringMethodRegistry.stringMethodSignature` não tem
  `isEmpty` (tem length/charAt/substring/contains/startsWith/endsWith/equals/
  indexOf/concat/trim/... — `isEmpty` ficou de fora, mas `type-system.md`
  documenta `String: isEmpty→Bool` e o `SemMethodCallTyper:98` **tipa**
  `isEmpty`→BOOL na allow-list geral de member-call). A lacuna registro↔typer
  faz o JVM emitir o invokevirtual genérico com descritor do fallback
  (Object) quando o receiver passa pelo lowerer de tipo inferido; no Native o
  nome vira `java_lang_String_isEmpty` inexistente (link); no JS não há
  runtime fn (o bloco core não define `isEmpty` para String).
- **Homologia com §34 (resolvido 06/09):** §34 era método inexistente em
  BUILTIN → SEM025 honesto. Aqui o método EXISTE na spec da linguagem mas não
  no registro do compilador — o caminho de diagnóstico correto é completar o
  registro, não SEM025.
- **Fix previsto (menor mudança):** adicionar `case "isEmpty" -> argCount == 0
  ? sig(BOOL, List.of()) : null;` ao `StringMethodRegistry`; Native: o
  intrinsic `java_lang_String_isEmpty` precisa de corpo (comparar length@16
  == 0 no header da string — o runtime já tem o layout); JS: alias
  `isEmpty` no bloco core de String (mesmo `length` path do §107-escalar).
  Gate: os 4 casos da matriz do reporter + `!t.isEmpty()` em if nos 3 targets.
- **Nota:** `kof check`/`kof test` passam nos 3 — o bug é de codegen/link em
  método TIPADO corretamente pelo checker (a verificação não confere
  registrador de assinatura vs typer: sonda futura §145-bis).
- **Fix (12/09, `718ae5cf` — lane bugfix-101):** `case "isEmpty" -> sig(BOOL)`
  no `StringMethodRegistry` + ramo `isEmpty` no `CollectionMethodTyper` +
  `case "isEmpty"` no `KofInterpreterCollections` + intrinsic x86
  (`NativeX86StringCalls`: length@16==0) + ramo riscv (`NativeRiscvCrossOps`:
  `seqz` sobre length) + alias JS (`JsCallEmitter`: `length === 0`).
  **Prova:** célula `strisempty` da matriz (`false/false/true/ok` nos 4
  targets — `ConformanceMatrixTest#conformanceCoreStrings` verde).

### 146. Native: `Double %` (variáveis) retornava o DIVIDENDO — só o fold de constantes funcionava — ✅ CORRIGIDO 12/09 (x86+riscv+aarch+JVM+Script; JS excluído bug 44; issue #101)

- **Menor repro (medido 12/09):** `println(10.0 % 3.0)` → `1.0` ✅ (fold de
  constantes no frontend); `var a=7.5; var b=2.0; println(a % b)` → **`7.5`**
  ❌ (JVM dá `1.5`); `var c=10.0; println(c % 3.0)` → **`10.0`** ❌. Int `%`
  correto; JS correto.
- **Causa raiz (exata):** `NativeX86Arith.emitBinary` — o bloco de Double
  (linha ~120-168) não tem ramo `MOD`: o `default` do switch Double (linha
  163) faz `movq %xmm0, %rax; pushq` — reempurra o PRIMEIRO operand (xmm0 =
  dividendo). O `case MOD` só existe no bloco inteiro (linha 189). Ou seja:
  `a % b` de double cai no default e devolve `a`.
- **Fix previsto (SSE2 puro, sem libm — precedente `sqrt` B36/RtB36):** sequência
  `fmod` em SSE2: `cvtsd2si` do quociente truncado + `mulsd/subsd` com
  ajuste de sinal (IEEE: `fmod(a,b)` tem sinal de `a`, |r|<|b|) — ou o loop
  de subtração com escala por `ulp2exp` como o x86 já faz em `RuntimeMath`
  (mesmo padrão que destravou MATH001). riscv: `fdiv.d+fcvt.wl.d+reconstrução`
  na B-slice seguinte; FLT001 se algo estourar. Gate: golden do oracle JVM
  medido (regra bug 44: compara Bool/Int, nunca println de double cru).
- **Nota:** passa `kof check`/`kof test` (value bug, R6 não violada — não é
  fallback silencioso, é aritmética errada em caminho que existe).
- **Fix (12/09 — lane bugfix-101):** x86 `718ae5cf` (ramo MOD Double em
  `NativeX86Arith` → `call kof_double_mod`; `RuntimeMath.kof_double_mod` em
  SSE2 puro sem libm: `a - trunc(a/b)*b`, NaN p/ 0/Inf/NaN e |q|>=2^63) +
  cross NESTE commit (fatia B40 `NativeRiscvAsmRtB40` — port 1:1 p/ riscv64:
  `fdiv.d`+`fcvt.l.d rtz`+`fmul.d`/`fsub.d`; a faixa ±2^63 por comparação FP
  porque o SAT riscv (INT64_MAX) difere do x86 (INT64_MIN); NaN canônico sem
  `lui`/`fneg` que o tradutor aarch64 não conhece; ramo MOD no dispatcher
  float/double de `NativeRiscvCrossOps` — Float promove p/ double e trunca de
  volta; aarch64 herda via tradutor, 0 UNHANDLED).
  **Prova:** célula `doublemod` da matriz (x86+JVM+Script; JS excluído bug 44)
  + `NativeRiscv64E2ETest#riscvDoubleModVariables` e
  `NativeAarch64E2ETest#aarch64DoubleModVariables` (10 vetores Bool sob qemu:
  6 finitos + 3 NaN + `c % 3.0` do reporter). Limite honesto: |q|>=2^63 dá
  NaN (igual x86); magnitudes gigantes fora do gate.

### 147. JS: if-branch terminando em `throw` ENGolia o epílogo do método — `return` final caía DENTRO do bloco `if`, statements seguintes viravam dead code; em `while` o `else{...}` do loop desaparecia (loop infinito) — ✅ CORRIGIDO 12/09 (JS+JVM+Script+Native; issue #101)

- **Menor repro (medido 12/09):** o `pick()` do reporter; output gerado:
  `else { if ((n === 3)) { ...; return x; } return 99; } }` — o `return 99`
  (epílogo do método) foi parar dentro do `else` do encadeamento, após o
  `if(n==3)`. `pick(7)` → `undefined` (nenhum caminho retorna). JVM/Script ✅
  (`10 20 30 99`). O reporter também achou o caso em `while`: `else {
  going = false }` dropado → hang.
- **Causa raiz (exata):** `JsControlFlowParser.parseIf:145-173` — com throw
  no then-branch o lowering IR não emite Jump(end) após o throw (o bloco é
  "unreachable fall-through"), e o parser de JS reconstrói a estrutura a
  partir dos labels: `parseStatements(ctx, pos, Set.of(), ...)` para o
  else-branch consome statements ALÉMDO Label(end) do if encadeado — o
  epílogo (ou o resto do corpo) é absorbido. Sem throw, o Jump(end) fecha o
  parse; com throw, o label end do aninhado some da stream e o delimitador
  vira o fim da cadeia.
- **Fix previsto:** no `parseIf`, o conjunto de stop-labels deve incluir os
  `end` labels ANCESTRAIS abertos (pilhinha de labels como fazem os parsers
  de loop com `LoopCtx`), não só `Set.of()`. Provar com os 2 repros do
  reporter (guard-chain + while-loop) + o caso do parser dele (linhas
  `s.accept(40)`) — golden JVM-vs-JS `10 20 30 99`.
- **Workaround em produção hoje (reporter confirmou):** mover o `throw` p/
  função separada. Não documentar como idiom (é bug).
- **Fix (12/09, `718ae5cf` — lane bugfix-101):** novo `JsIfThrowElse.java`
  (extraído do `JsControlFlowParser` p/ manter o ratchet ≤500): `parseElse`
  consome o else até o primeiro `KofJump`/`KofLabel` não-loop, EXCETO o
  trailing `KofReturnVoid` do corpo do método (retorno implícito — epílogo
  real, nunca statement do else); `thenEndsUnconditional` detecta
  then-throw/return/break/continue.
  **Prova:** célula `ifthrowelse` da matriz (`else/after` nos 4 targets —
  `ConformanceMatrixTest#conformanceErrors` verde).

### 148. Frontend: constante de enum qualificada (`Color.RED`) como EXPRESSÃO tipava UNKNOWN → **SEM032 falso** em switch-expr exaustivo sobre enum (`var c = Color.RED` / `switch (Color.RED)`) — ✅ CORRIGIDO 12/09 (achado no sweep de paridade 4-target)

- **Menor repro:** `enum Color { RED, GREEN, BLUE }` +
  `main() { var c = Color.BLUE; var r = switch (c) { case Color.RED -> "r" case Color.GREEN -> "g" case Color.BLUE -> "b" } }`
  → **SEM032 falso** ("switch expressão exige 'default' (ou exaustividade de
  enum)") mesmo cobrindo as 3 constantes. Idem `switch (Color.GREEN)` direto.
  Com `Color c = Color.BLUE` (tipo DECLARADO) ou `return switch (c)` com
  parâmetro `Color`, a exaustividade é reconhecida — só o caminho
  `var`/literal direto quebrava.
- **Causa raiz:** `SemExpressionTyper` (case `FieldAccessExpr`) não inferia o
  tipo da constante de enum qualificada — caía no `yield UnknownType` do fim do
  case (o receiver `Color` não é uma classe com campo `RED`). O
  `enumConstantOfExpr` já existia, mas só era usado para SUPRIMIR o SEM025 e no
  lowering; a INFERÊNCIA de tipo da expressão ficava UNKNOWN. Consequência:
  `var c = Color.RED` infere `c: Unknown` e o `switch(c)` não entra no ramo de
  exaustividade (`subjectType instanceof ClassType`) → cai no `else` genérico =
  SEM032. O mesmo `Unknown` poluía `Color.RED` como argumento/slot.
- **Efeito colateral do bug (teste que passava pelo motivo errado):**
  `KofSwitchExprE2ETest.enumNonExhaustiveFailsToCompile` (usa `var c = Color.Red`)
  passava porque QUALQUER switch-expr sobre `var` de enum caía no SEM032
  genérico — não porque a lista de constantes faltantes era detectada. Com o
  fix, a mensagem enum-específica ("não cobre: Green, Blue") aparece.
- **Fix:** `MemberResolver.enumNameOfConstant(unit, e)` (novo) devolve o NOME do
  enum dono da constante qualificada; o case `FieldAccessExpr` do
  `SemExpressionTyper` faz `yield new ClassType("", enumName, [])` quando o
  receiver é o nome do tipo enum e o campo é uma constante — espelhando o que o
  case `IdentifierExpr` já fazia p/ constante NÃO-qualificada. Sem mudança de
  contrato (só o tipo inferido, que estava UNKNOWN).
- **Prova:** `KofSwitchExprE2ETest.enumExhaustiveVarSubjectJvm` /
  `enumExhaustiveLiteralSubjectJvm` / `enumExhaustiveVarSubjectNative` (novos;
  vermelhos antes com SEM032, verdes depois) + `enumNonExhaustiveFailsToCompile`
  agora com a mensagem enum-específica + paridade medida JVM=Native=Script=JS
  (`var c = Color.GREEN` → `GREEN|GREEN|true|true|3|BLUE`; `switch` por
  constante não-qualificada `RED/GREEN/BLUE` → `g`). Suíte compiler
  **1412/0-fail** (13 err = `node` ausente, ambientais), script 31, kof-c 5,
  cli 136; ratchet ≤500 OK (SemExpressionTyper voltou a 573 < baseline 577).

### 149. JS: regressão do fix `isEmpty` (`718ae5cf`) — código gerado referencia variável não declarada (`ReferenceError: i is not defined` / `k is not defined`) + matriz de conformidade dessincronizada — ✅ CORRIGIDO 12/09 (gate de paridade; a raiz é o `JsIfThrowElse` do §147, não o `isEmpty`)

- **Repro (medido 12/09 21:30, HEAD `7a85dd93`):** `mvn -o test -pl kof-compiler -am -Dtest='KofRandomTest,ConformanceMatrixDocTest'` →
  - `KofRandomTest.randomStringJs`: `JS exit code, output:  err: ReferenceError: i is not defined` (exit 1)
  - `KofRandomTest.randomShapeJs`: `err: ReferenceError: k is not defined` (exit 1)
  - `ConformanceMatrixDocTest.matrixDocMatchesTestExclusions`: "casos na matriz ≠ casos no teste" (a matriz passou a esperar casos que o teste não tem — a célula nova de `isEmpty` não foi casada com a lista de exclusões/casos)
- **Causa raiz (a lane dona confirma):** o commit `718ae5cf` ("feat: add 'isEmpty' method support for strings and fix related issues") mexeu em `JsCallEmitter`/`JsControlFlowParser`/`JsIfThrowElse` + `CollectionMethodTyper`/`KofInterpreterCollections` — a face JS quebrou a declaração de variável de loop/compreensão no codegen JS (sintoma `i`/`k` indefinidos) e a matriz de conformidade (`docs/CONFORMANCE_MATRIX.md` ↔ `ConformanceMatrixDocTest`) não foi atualizada no MESMO commit.
- **Prova de que NÃO é regressão do split-7 (§140):** mesmo conjunto de testes no HEAD limpo (stash do split aplicado) falha IGUAL (3/3) — `ExpressionMethodCallLowerer` não toca JS nem random.

#### ✅ Correção medida (13/09, lane gate/paridade — FECHA o §149; supersede a previsão de locus abaixo)

A análise da lane dev (12/09 ~22:40, logo abaixo) diagnosticou a causa
corretamente mas **previu errado o locus do fix**: disse que o fix "NÃO pode
morar em `JsIfThrowElse`" e propôs mudança de IR (regra 6). Medido no fonte:
o IR **não é ambíguo para este caso** — o predicado que decide "label é fim
do else?" é que estava fraco. `ctx.isLoopLabel` só enxerga loops JÁ ABERTOS;
o `while` seguinte a um `assert`/if-throw ainda não está na pilha. O
`JsLabelParser.isLoopStart` (lookahead: algum jump/cond-jump posterior salta
para o label) é o predicado correto e **já era usado** no `parseStatements`.

- **Fix (sem mudança de contrato/IR):** `JsIfThrowElse.parseElse` — se o
  label for INÍCIO de loop (lookahead), parseia o loop DENTRO do else e
  continua (`flow.parseLoop`), em vez de tratá-lo como fim do else;
  `JsControlFlowParser.parseIfBody` ganha a mesma guarda nas 2 checagens de
  `Label(end)`/`Label(else-end)`. O corpo pós-if-throw fica aninhado no else
  — semanticamente equivalente, porque o then sempre lança (só o caminho
  `false` chega ao epílogo).
- **Prova:** `KofRandomTest.randomStringJs`/`randomShapeJs` **verdes** (antes
  `ReferenceError`); `KofJsE2ETest` 40/40; `ConformanceMatrixTest` 11/11 +
  `ConformanceMatrixDocTest` 1/1; gate 4-módulos **1611/0 falhas** (13 erros
  = só `node` ausente). A previsão de que a célula `assertepilogue` seria
  necessária foi cumprida de forma equivalente pelos testes do `KofRandomTest`
  (assert + `while` + epílogo) — ver bloco de prova do commit.

#### Atualização da lane development (12/09 ~22:40) — metade MATRIZ ✅ FECHADA; metade JS com causa raiz CORRIGIDA e locus do fix provado

- **(a) Metade matriz RESOLVIDA por esta sessão (`0c107eb9`):** não é
  "a célula de isEmpty" nem o caminho `docs/CONFORMANCE_MATRIX.md` (que não
  existe — o real é `docs/bugs-and-gaps/conformance-matrix.md`); são **3**
  células (`doublemod`, `strisempty`, `ifthrowelse`) adicionadas ao
  `ConformanceMatrixTest` por `718ae5cf`/`440730c8` sem linha na doc. Linhas
  espelhando-as (PARTIAL = `Set.of` do teste) postas na doc →
  `ConformanceMatrixDocTest` **verde (1/1)** + `ConformanceMatrixTest`
  `conformanceCoreArithmetic` verde no gate `gateFixed.log`. NÃO requer
  mudança na lane deles.
- **(b) Metade JS — causa raiz é outra (prova deste HEAD, `CompilerDriver`
  Target.JS dump):** NÃO é "declaração de variável de loop quebrada no
  codegen". O `assert(c)` abaixa para `if((c===0)) throw` **sem else-fonte**.
  O lowering EMITE o `Jump(end)`+`Label(end)` normais (o docstring do
  `parseIfBody:136-137` declara o padrão `[CJump, L(t), then, J(end), L(f),
  else?, L(end)]`) — mas o `backend/Optimizer` faz **"unreachable code
  elimination" + "jump-to-next elimination"** (linhas 44-45): o `J(end)` que
  vem logo APÓS um `KofThrow` é inalcançável → deletado; sem referências, o
  `L(end)` par também é podado. Resultado: o stream que chega ao parser JS
  é `CJump, L(t), throw, L(f), <statements…>` SEM end-label, enquanto um
  if-else NORMAL mantém os dois labels (o `J(end)` do then é alcançável —
  o then não termina em saída incondicional). O `JsControlFlowParser.
  parseIfBody` decide "tem else?" pela presença de `Label(end)`/`KofJump`
  após `L(false)` — sem eles, cai no ramo §147 (`JsIfThrowElse.parseElse`),
  que consome statements até o próximo `KofJump`/`KofLabel`-não-loop. Os
  statements SEGUINTES ao if (corpo do método pós-if) viram "else": `var i`
  fica preso no bloco, `while(i<8)` posterior lê `i` fora de escopo →
  `ReferenceError`. **(Retificação 13/09, pós-fix `29923a5b`):** eu aleguei
  que o reporter (`ifthrowelse`, com else-fonte) tinha IR "byte-idêntico" —
  **nunca comparei dumps de IR** (alegação de memória, violação da regra de
  não-assertar-o-não-rodado; o teste que depois passou a comparar os dois
  `while` do random-prova provou o shape distinto: o `isLoopStart` lookahead
  é justamente o que separa os casos). A parte que sobrevive ao fix: o
  parser decidir "tem else?" pela PRESENÇA de `Label(end)` — sem a
  informação, é obrigado a escolher um heurístico, e o heurístico fraco
  (`isLoopLabel` só vê loops já abertos) regressou.
- **(c) Retificada pelo fix medido (13/09):** eu afirmei que o fix "NÃO pode
  morar em `JsIfThrowElse`" e que a porta correta seria mexer no Optimizer
  (regra 6). **Errado** — o IR não precisou de mudança: `JsLabelParser.
  isLoopStart` (lookahead de jump posterior p/ o label, já usado em
  `parseStatements`) é o predicado que faltava no `parseElse`. Fix
  puramente-parser (`JsIfThrowElse` + guarda dupla no `parseIfBody`), sem
  contrato compartilhado. A "pilha de end-labels do §147" permanece não
  implementada (o lookahead a substitui neste caso); se algum shape futuro
  precisar de fronteira real, reabre-se como decisão de design.

- **(d) Estado do gate 4-módulos neste HEAD (`e1962735`, `gateFixed.log`):**
  **1602 = 1430+31+5+136, 2 falhas, 5 skip** — as 2 falhas são EXATAMENTE
  §149-JS. Lane bugfix-101 declarou #101 FEITO (21:45) mas o §149 que ELES
  mesmos abriram continua vermelho: unidade não terminada. Registrado na
  mesa + no dispatcher.

### 150. Codegen (4 targets): switch-expr EXAUSTIVO sobre enum com corpo PRIMITIVO → o fallback sintético usava o tipo do SUBJECT (referência) → boxing dos braços → **VerifyError no JVM** (`Integer` vs `int`) e **COMP002 (frame crash) em Double/Long** — ✅ CORRIGIDO 12/09 (achado no sweep de paridade 4-target do §148)

- **Menor repro:**
  ```kof
  enum Color { Red, Green, Blue }
  main() {
      var c = Color.Blue
      var r = switch (c) {
          case Color.Red -> 1
          case Color.Green -> 2
          case Color.Blue -> 3
      }
      println(r)
  }
  ```
  JVM: `VerifyError: Bad type on operand stack @66 istore_3` (`Integer` no
  slot de `int`). Variante Double/Long: `Internal compiler error: frame crash
  ... ASM COMPUTE_FRAMES AIOOBE` (COMP002). Native/Script/JS imprimiam `3`.
  Com `default` explícito sempre funcionou (`default -> 99`); só o switch
  **exaustivo de enum sem default** quebrava.
- **Causa raiz:** `SwitchExprLowerer.emitSwitchExpr` passava o tipo do SUBJECT
  (`switchType` = enum, referência) como `switchFallbackType` do
  `emitSwitchChain`. Num switch exaustivo não há default, então o valor
  sintético (`defaultValueOp(switchType)` = `null`) entra no merge; como o
  subject (referência) difere dos corpos (primitivo), `branchTypesDiffer`
  ligava e cada braço primitivo era BOXADO in-branch. Resultado: braços
  `Integer`/`Long`/`Double`/`Boolean` + fallback `null` no mesmo ponto de
  merge, mas o store do `var r` era primitivo (`istore`/`lstore`/`dstore`).
  `ExpressionTyper.boxesOwnBranches` tinha o MESMO defeito (passava o tipo do
  subject), então concordava com o lowering errado.
- **Fix:** o fallback sintético passa a usar o tipo do **RESULTADO**
  (`inferExprType(driver, se, locals)`, que já é o tipo dos corpos/do `var r`),
  não o do subject. `SwitchExprLowerer.emitSwitchExpr` calcula `resultType` e
  `fallbackType` (`Object` se os corpos divergirem — preserva o §68
  heterogêneo) e `emitSwitchChain` recebe `fallbackType`; `boxesOwnBranches`
  usa o mesmo `inferExprType(se)`. Sem mudança de contrato: só o tipo do valor
  SINTÉTICO (que nunca é observado num switch exaustivo).
- **Prova:** `KofSwitchExprE2ETest.enumExhaustiveIntBodyJvm` /
  `enumExhaustiveDoubleBodyJvm` / `enumExhaustiveLongBodyJvm` /
  `enumExhaustiveBoolBodyJvm` / `enumExhaustiveIntBodyNative` (novos; JVM
  vermelho com VerifyError/COMP002 antes). Paridade medida 4-target
  `3|3|3|3`, `3.5|3.5|3.5|3.5`, `true|true|true|true`. Sem regressão nos
  testes de switch/pattern (§68 heterogêneo e SYN001 continuam como estavam).
- **Fora de escopo (fechado em §153):** corpo de case em BLOCO
  (`case X -> { ... }`) era aceito em silêncio (lixo) — agora PARSE094.

### 151. Native: membership de coleção com constante de enum usa comparação por PONTEIRO → `contains` devolve `false` (JVM/Script/JS: `true`) — ✅ CORRIGIDO 12/09 (achado no sweep de paridade 4-target)

- **Menor repro:**
  ```kof
  enum Color { Red, Green, Blue }
  main() {
      var l = listOf(Color.Red, Color.Green)
      println(l.contains(Color.Green))   // Native: false; JVM/Script/JS: true
      var s = setOf(Color.Red, Color.Blue)
      println(s.contains(Color.Blue))    // Native: false
  }
  ```
- **Causa raiz:** a constante de enum é lowering para **String literal**
  (`ExpressionLowerer` case `FieldAccessExpr` → `KofLoadLiteral(STRING, nome)`),
  então a membership tem de comparar por CONTEÚDO. O tag do Native
  (`CollectionWrites.stringTag`) só reconhecia `String`; enum caía em `0` →
  `kof_list_contains`/`kof_set_contains` faziam `cmpq` de ponteiro entre duas
  Strings distintas → `false`. O `==` de enum já era por conteúdo
  (`CompilerComparisons:27`), por isso `l.get(0) == Color.Red` dava `true` e o
  `contains` não — divergência intra-Native.
- **Fix:** `CollectionWrites.isStringLike` trata enum como String-backed
  (`BuiltinTypes.isString(t) || CompilerTypes.isEnumType(t, unit)`) e
  `stringTag` recebe a `CompilationUnitNode`. Record/objeto NÃO entra (não é
  String em runtime; deref seria SIGSEGV) — §104b-ii segue aberto.
- **Prova:** `KofMapSetTest.listContainsEnumNative` / `listContainsEnumJvm` /
  `listContainsEnumJs` (novos) + paridade medida 4-target `true|false|true`.
  O caso record (`listOf(P(1),P(2)).contains(P(2))`) continua `false` no Native
  — é o §104b-ii (lane `nat/`), NÃO regride.

### 152. Build: `NativeRiscvAsmRtB40.java` fechava o text-block com `""");` (parêntese extra) → `';' expected`; **o `origin/beta-0.4.0` NÃO COMPILAVA** — ✅ CORRIGIDO 13/09 no remoto (`4459ff57`/`d2acf867`; achado no gate pós-rebase desta sessão)

- **Menor repro:** `mvn -o -pl kof-compiler -am compile` em `440730c8`
  (e no merge `0104f6d6`) →
  `NativeRiscvAsmRtB40.java:[102,12] ';' expected`.
- **Causa raiz:** a fatia `RISCV_RUNTIME_ASM_B_40` (port riscv64 do
  `kof_double_mod`, §146) é um campo `static final String = """…"""`; o
  fechamento foi escrito `""");` (parêntese de chamada, copiado do padrão
  `sb.append("""…""")` dos vizinhos) em vez de `""";`. Introduzido em
  `440730c8` (lane bugfix-101) — passou batido porque o gate de lá rodou só os
  testes de runtime do riscv, não o `compile` limpo do módulo.
- **Fix:** `""");` → `""";` (1 char) — já no remoto; os demais `""");` do
  pacote `nat/` são `sb.append("""…""")` legítimos (6 ocorrências, todas com
  abertura em chamada).

### 153. switch-expressão: corpo de case em BLOCO (`case X -> { ... }`) era aceito em silêncio → lixo (`Lambda0@…`/`[object Object]`/saída vazia) — ✅ CORRIGIDO 13/09 (diagnóstico PARSE094; R6)

- **Menor repro (medido 13/09):**
  ```kof
  enum E { A, B }
  main() {
      var e = E.A
      var x = switch (e) {
          case A -> { println("a"); "aa" }
          default -> "other"
      }
      println(x)
  }
  ```
  Antes: JVM/Script `Lambda0@<hash>`, Native saída **vazia**, JS
  `[object Object]` — exit 0 silencioso (violação R6: o corpus diz
  "não há escopo de bloco" no switch-expressão —
  `training/idioms/control-flow.md:145`).
- **Causa raiz:** `ExpressionParser.parseSwitchExpression` chamava
  `parseExpression` para o corpo do case; um `{` cai no ramo de lambda de
  bloco de `parsePrimary` (`case LBRACE -> new LambdaExpr(...)`), virando um
  valor de tipo-função silencioso.
- **Fix (parser, 1 guarda):** `rejectBlockCaseBody` emite `PARSE094` ("o corpo
  de cada case é uma ÚNICA expressão (sem escopo de bloco); use o
  switch-statement (`case ...:`) para múltiplos statements") quando o token
  seguinte ao `->` é `{`, tanto em `case` quanto em `default`. Semântica
  inalterada para as formas válidas (expressão única).
- **Prova:** `KofSwitchExprE2ETest.blockCaseBodyFailsWithDiagnostic` (novo;
  vermelho antes — compilava com `success=true`); os 31 testes prévios seguem
  (6 erros = só `node` ausente, ambientais).

### 154. P0 — `origin/beta-0.4.0` ficou VERMELHO por um instante: o WIP §103.1 (`69fdab59`) aterrissou na branch e quebrou o `javac` do `KofRuntime` (345 falhas) — ✅ RESOLVIDO 13/09 (fix do dono da lane §103: `751a83f2`/`3fd3c1b3`)

- **Sintoma (medido no gate pós-rebase, HEAD `7a410b6c`):** suíte
  `kof-compiler` **1445 testes, 345 falhas, 20 erros** (normal: 1439/0/13).
  As falhas eram de classe inteira (`ComponentCoreE2ETest` 13/13,
  `ConfigGenTest` 3/3, `CompilerDriverTest` 3, `BackendParityTest` 2,
  `AndroidInteropE2ETest` 2, `CodegenKitchenSinkTest` 1) e o `kof-cli`
  (`CompareTest`/`DecompileTest`/`FullStackE2ETest`/`ServePortTest`), todas
  com a MESMA raiz: `failed to compile KofRuntime helper (javac exit 1)`.
- **Causa raiz:** o commit `69fdab59` ("preserva §103.1 do outro agente em
  branch própria") foi empurrado TAMBÉM na `beta-0.4.0` — não só na branch
  `wip-103-json-map-103.1`. O conteúdo é um WIP inacabado: o
  `JvmRuntimeJson` emite `kof_json_decode_object_map` retornando
  `HashMap<Object,Object>` **sem importar `java.util.HashMap`** e um
  `kof_json_decode_map` com `new java.util.Map<Object,Object>(0){...}`
  (interface não instanciável → `anonymous class implements interface;
  cannot have arguments`). O `KofRuntime.java` gerado não compila → **toda
  compilação JVM falha**.
- **Fix (do dono da lane §103, não revert):** no rebase de 13/09 o remoto já
  trazia a implementação correta — `751a83f2` (decode real) +
  `3fd3c1b3` (`JvmRuntimeJsonMap`/`JsRuntimeUiJsonMap`, o split ≤500 que
  carrega os helpers de Map com os imports certos) + `606662f4` (§103.2
  Int→Long em campo). O meu `git revert 69fdab59` (`8d5b8e65`) ficou
  **obsoleto e foi descartado no rebase** (`git rebase --skip`): reverter
  teria destruído a implementação boa do dono. O WIP antigo segue
  preservado em `origin/wip-103-json-map-103.1` (`cf610fba`).
- **Prova:** com o fix do dono, `CompilerDriverTest` 252/0,
  `ComponentCoreE2ETest` 14/0, `ConfigGenTest` 3/0, `BackendParityTest` 16/0,
  `CodegenKitchenSinkTest` 1/0, `AndroidInteropE2ETest` 12/0 (298/0) e
  `kof-cli` `CompareTest`/`DecompileTest`/`FullStackE2ETest`/`ServePortTest`
  58/0. Lição: WIP preservado vai SÓ para branch própria; commit de WIP na
  branch de release é regressão de build (regra 1 — zero regressão).

### 155. Tipo-função como ARGUMENTO GENÉRICO declarado (`List<(Int) -> Int>`, `listOf<(Int) -> Int>()`) → `ClassFormatError` no JVM e COMPILE-FAIL/lixo nos outros 3 targets — ✅ CORRIGIDO 13/09 (achado ao validar o §127)

- **Menor repro:** `main(){ List<(Int) -> Int> l = listOf((x: Int) -> x + 1);
  println(l.get(0)(5)) }` — compila (`success=true`) mas no JVM:
  `ClassFormatError: Illegal zero length constant pool entry at 33 in class
  Default/Main`; Native `COMPILE-FAIL`; Script `ERR: Lambda0.`; JS
  `TypeError: ... is not a function`.
- **Causa raiz:** `TypeParser.parseTypeRef` monta os type-args concatenando os
  tokens CRUS (`args.append(ctx.tokens.get(ctx.pos).value())`) — sem espaços.
  O tipo-função `(Int) -> Int` virava a string `"(Int)->Int"`, e `Type.of` só
  reconhece `"(Int) -> Int"` (com espaços: `name.contains(" -> ")`). Resultado:
  `ClassType("", "(Int)->Int")` — nome de classe inválido que desce até o
  `checkcast`/`invoke` do JVM. O mesmo buraco no `parseCallTypeArguments` do
  `ExpressionParser` (guard só aceitava IDENTIFIER/primitive, não `LPAREN`).
- **Fix (parser, 2 pontos, aditivo):** `TypeParser.parseTypeRef` detecta
  `LPAREN` dentro dos type-args e delega a `parseFunctionTypeRef` (preserva os
  espaços); `ExpressionParser.parseCallTypeArguments` aceita `LPAREN` como
  início de type-arg. Sem mudança de semântica — só a string do tipo fica
  correta.
- **Prova:** `LambdaE2ETest.declaredFunctionTypeListJvm/Native` (`6`/`10`) +
  sonda C1/C2/C3 4/4 targets; `CompilerDriverTest.functionTypeSyntax`
  (o teste antigo só assertava `success=true` — o bytecode inválido passava).
- **Não-regressão:** 321/0 no subset (`LambdaE2ETest`+`CoreRegressionE2ETest`+
  `CompilerDriverTest`).
- **Trava automatizada (lane gate, 13/09):** célula de matriz `fntypegeneric`
  (`ConformanceMatrixTest.conformanceCoreFunctions`) roda o menor repro nos
  **4 targets em CI** (`6` 4/4) — antes a prova automatizada era só
  JVM+Native (`LambdaE2ETest.declaredFunctionTypeListJvm/Native`); Script/JS
  eram sonda manual.


### 156. JVM: `List` HETEROGÊNEO de lambdas com a MESMA assinatura → `ClassCastException` (`Lambda1` não é `Lambda0`) — ✅ CORRIGIDO 13/09 (infra de tipos; dono = 192.168.100.22)

- **Menor repro:** `main(){ var l = listOf((x: Int) -> x + 1, (x: Int) -> x * 2);
  println(l.get(1)(5)) }` → JVM `ClassCastException: class Lambda1 cannot be
  cast to class Lambda0`; Native/Script/JS imprimiam `10` (correto).
- **Causa raiz:** o tipo do elemento da lista era inferido do PRIMEIRO argumento
  (`listOfElementType`) e carregava o `className` da lambda concreta (`Lambda0`).
  O `JvmOpCollections` `kof_list_get` fazia `checkcast ft.className()` (Lambda0)
  e o call site invocava via `Lambda0.invoke` — mas o segundo lambda é outra
  classe (`Lambda1`) que só compartilha a **interface SAM sintética**
  (`kof/Function1_int_int`). Mesmo território do §127: a distinção
  `FunctionType.className` (lambda concreta) × interface sintética
  (`CompilerLambdaClass.lambdaInterfaceType`) precisava ser resolvida na
  inferência do elemento da coleção.
- **Fix (inferência, 2 pontos, aditivo):** quando TODOS os args do `listOf` são
  `FunctionType` da MESMA assinatura (params+retorno), o elemento desce SEM
  `className` (`CompilerTypeSupport.listOfElementType` — só unifica nesse caso;
  assinatura divergente ou lambda-vs-não-lambda mantém o primeiro, e o SEM056
  segue barrando a poluição heterogênea de verdade). Sem `className`, o
  `kof_list_get` não emite `checkcast` concreto e o call site invoca via
  `INVOKEINTERFACE` na SAM (ramo `ft.className() == null` do bug 8 em
  `ExpressionInstanceCallLowerer`/`ExpressionMethodCallLowerer`). Espelho no
  analyzer (`BuiltinCallTyper.listOf` — roda antes da síntese, `className` é
  null lá; checa só params+retorno). Lambdas guardadas em `var` e depois
  postas na lista funcionam igual (o `className` viaja no tipo do local).
- **Prova:** `LambdaE2ETest.heterogeneousLambdaListJvm/Native`
  (`10|6|11|20` — get(1), get(0) e for-in; 23/23 na classe) + sondas JVM/Native
  (get(1), get(0), for-in, single, `List<...>` declarado, `add` pós-criação,
  var-refs — 13/13); negativos inalterados (aridade/retorno divergente e
  lambda-vs-Int: `success=true` antes e depois — SEM056 não cobre lambdas,
  status quo). Suíte 4-módulos **1636/0/156-skip** (compiler 1463 + script 31
  + kof-c 5 + cli 137; skips = guards node/cross/externos; o `KofJsHostlessRuntimeTest`
  2/2 passou nesta rodada — GraalJS resolvido no reactor completo).
- **Não é regressão do §127/§155:** o caminho (`JvmOpCollections` +
  `ExpressionInstanceCallLowerer`) não foi tocado por eles; a sonda B127c já
  dava CCE antes.
- **Trava automatizada (lane gate, 13/09):** célula de matriz `lambdalisthet`
  (`ConformanceMatrixTest.conformanceCoreFunctions`) roda o menor repro nos
  **4 targets em CI** (`10/6` 4/4) — antes a prova automatizada era só
  JVM+Native (`LambdaE2ETest.heterogeneousLambdaListJvm/Native`); o "Native/
  Script/JS já imprimiam 10" era sonda manual.


### 157. JVM: `m.put(k, <Long>)` com `mapOf()` → `HashMap.put` empilha 1 Object mas o descarte da statement popa 2 (POP2) → **COMP002 "frame crash" / VerifyError** — ✅ CORRIGIDO 13/09 (issue #103 caso 3, fix da issue-lane `bee8555c` — pin-align no `CollectionCallLowerer`; merge na `beta-0.4.0` 13/09)
- **Menor repro**:
  ```kof
  main() {
      var m = mapOf()
      var agora = now()      // Long
      m.put("k", agora)      // ← frame crash
      println("guardado")
  }
  ```
  `kof run` → `frame crash em Default/Main.main` / o `javap` mostra
  `INVOKESTATIC java/lang/Long.valueOf → INVOKEVIRTUAL HashMap.put → POP2`.
- **Causa raiz (divergência de ordem, não de largura):** `mapOf()` nasce
  `Map<Unknown,Unknown>` e o **primeiro `put` pina os tipos no local** — mas os
  dois sides do pin rodavam em ordens diferentes:
  1. **Emit** (`CollectionCallLowerer`) lia `valueType` do receiver **antes** de
     mutar o local para `Map<String,Long>` → `KofCall.returnType` = Unknown →
     `emitPrevValueUnbox(Unknown)` era no-op → `HashMap.put` deixa **1 Object** na
     pilha.
  2. **Typer da statement** (`StatementLowerer` → `SemMethodCallTyper`, que roda
     o MESMO pin) via o local **depois** do pin → vê `Map<String,Long>` →
     `isDoubleWidth(Long)` → emite **POP2**.
  POP2 sobre 1 slot = underflow do frame. (`isDoubleWidth(Unknown)` é `false`,
  então o bug só aparece quando o valor pinado é primitivo de categoria-2 —
  `Long`/`Double`; `String`-value e `Int`-value não disparavam.)
- **Fix:** quando o pin dispara, alinhar `keyType`/`valueType` locais do lowering
  aos tipos pinados (`CollectionCallLowerer.java`, bloco do pin): assim o
  `retType` do `KofCall` sai `Long` e `emitPrevValueUnbox(Long)` unboxa
  Object→long (2 slots), casando com o POP2 do descarte.
- **Prova:** reproc mp/mp2/mp3 rodam; caso-onde-o-retorno-é-usado
  (`var prev = m.put(...)` → `prev=0`, e `println(m.put(...))` de Double →
  `0.0`) verde; check de poluição §126 intacto (2º `put` com valor `String` em
  mapa `Long`-pinado → `SEM056`, não auto-suprimido pelo alinhamento);
  `KofMapSetTest` 14/14, célula nova na matriz de conformidade.
  **Trava automatizada (lane gate, 13/09):** a "célula nova" declarada acima
  NÃO existia na matriz (overclaim — achado na varredura). Criada agora:
  `mapputlong` (`ConformanceMatrixTest.conformanceCoreFunctions`) roda o
  repro `mapOf()` vazio + `put` de `Long` nos **4 targets em CI**
  (`9000000001/1` 4/4).

### 158. kof.web: `header()`/`query()` declarados `String` mas devolvem `null` na ausência → deref sem narrowing passava no check e NPEava 500 silencioso — ✅ CORRIGIDO 13/09 (issue #102 item 4, comentário PublioSantos)

- **Menor repro**: rota com `return header("accept-language").split(",")[0]` —
  1º visitor sem aquele header → 500 nu, nada no log.
- **Causa:** `KofWeb.contextCall` tipava `param/query/header` todos como `STR`,
  mas o runtime (`JvmWebCoreRuntime.WebRequest.{query,header}` →
  `HashMap.get`) devolve `null` quando o campo não está no request. Mentira de
  tipo: o null-safety da linguagem não podia exigir o check.
- **Fix:** `query()`/`header()` agora retornam `Nullable(STR)` — o **SEM049**
  exige `if (c != null)` antes de deref (mesmo idioma de SG-008, `Map.get`→`V?`).
  `param()` continua `String` deliberadamente: só uma rota matchada chega ao
  handler e todo `:param` do match tem valor — o idiom canônico
  `param("id").toInt()` do corpus não pode virar erro.
- **Prova:** `kof check` rejeita `header(...).split(...)` direto (SEM049) e
  aceita a forma com narrowing; E2E novo `absentHeaderAndQueryAreNullable`
  (server JVM real: header ausente → "nada", presente → "len:3"; idem query) —
  `KofWebE2ETest` 14/14 + hardening/sse/ws/stream 27/27.

### 159. Native web: funções de contexto não-emitidas vazavam para o linker (`undefined reference to 'kof_web_param'`) em vez de WEB001 — ✅ CORRIGIDO 13/09 (issue #102 item 3)

- **Menor repro:** `kof build . --target native` de uma rota que chama
  `query("id")` → sucesso de compilação + `ld: undefined reference to
  'kof_web_query' [COMP001]`. O `--target native` falhava em 5 funções
  diferentes (`param/query/header/status/headerSet`) com `ld`-fail, não com o
  diagnóstico `WEB001` prometido nos docs.
- **Causa:** o gate de paridade de web (`ExpressionBuiltinInstanceCalls.lowerWeb`)
  cobre os métodos do objeto `app` (listen/route/etc.), mas as **funções de
  contexto** (receiver ausente) baixam por outro caminho —
  `ExpressionStaticCallLowerer` (ramo `isContextFunction`) — que não checava o
  target: emitia `KofCall` de um símbolo que o backend nativo não possui.
- **Fix:** o mesmo gate no ramo de contexto — as funções sem símbolo no asm
  nativo (só `body()` tem; o T1 é listen/route + body) dão **WEB001 em tempo de
  compilação** nos 3 targets nativos, com mensagem nomeando a função
  (R6: gap diagnosticado, nunca `ld`-fail). `KofWeb.contextNativeSupported` é a
  lista única do que o nativo emite.
- **Prova:** `build --target native` da rota com `query/header/status/
  param/headerSet` → 5 erros `WEB001` (rc=1, nenhum binário); rota T1 com
  `body()` → compila e o ELF responde (`POST /echo` → `got:olaho`);
  `KofWebNativeE2ETest` 4/4. Os demais sintomas do item 3 (return null →
  200-vazio em vez de 404; POST inconsistente) são comportamento do runtime
  nativo T1 — **documentados como limitação do gap WEB001**, não corrigidos aqui
  (a fila agora é: WEB001 no compile impede "shippar sem saber").

### 160. KofJS: blocos do `kof-runtime.mjs` referenciam `kof_platform` cru → **ReferenceError** no Node/navegador (só existe no host GraalJS) — ✅ CORRIGIDO 13/09 (issue #104)

- **Menor repro:** `kof build . --target js` de `uuid.v4().length`; rodar o
  artefato fora do Graal (Node/Chrome) → `ReferenceError: kof_platform is not
  defined` em `kof-runtime.mjs`. JVM e `kof run --target js` (Graal) → `36`.
- **Causa:** o shim `globalThis.kof_platform || Proxy` só vivia no módulo
  `kof-runtime-io.mjs` (`const` local àquele módulo). Os blocos `uuid`/`random`/
  `security`/`crypto`/`ui-web` do `kof-runtime.mjs` (outro módulo) citavam o
  nome cru; no Graal funcionava porque o `KofJsRunner` injeta o binding global.
- **Fix (decisão 1 — mecânica):** o mesmo shim passa a ser emitido no topo do
  core (`JsRuntimeCore.CORE_RUNTIME`), trocando o `ReferenceError` pelo erro
  claro do Proxy (`"is not available outside the Kof JS host"`), como o io já
  prometia. Os blocos afetados são *units* do mesmo módulo do `core`, então a
  declaração única cobre todos.
- **Decisão 2 (contrato de target — mantenedora):** se/qual capacidade tem
  implementação real no browser (Web Crypto `crypto.randomUUID`/`getRandomValues`/
  `crypto.subtle` para `uuid`/`security`) — fora do escopo deste fix mecânico.
  Efeito colateral benéfico já medido: `random.*` (que já tinha `try/catch` →
  `crypto.getRandomValues`) agora **funciona** no browser (o catch pega o erro do
  Proxy, antes era ReferenceError não-capturável).
- **Prova:** teste novo `KofJsHostlessRuntimeTest` roda o artefato JS num
  `Context` Graal **sem** injetar `kof_platform` (simula Node/browser) e asserta
  erro claro, não ReferenceError, para `uuid.v4` e `security.randomHex`; sem
  regressão com host — `KofUuidTest` 14/14, `KofRandomTest` 12/12,
  `KofSecurityTest` 28/28.

### 161. NAT-STR01 — case-fold (`toUpperCase`/`toLowerCase` de instância) e conversores de string são **ASCII-only no Native** (x86/riscv/aarch), Unicode (`Character.toUpperCase`, default locale) no JVM/interpretador; JS usa `String.prototype.toUpperCase` (Unicode) — 🟢 DECIDIDO 13/09 (abrir/implementar; ratificação da mantenedora) — lane nat

- **Menor repro (medido 13/09, harness de paridade 4-target):**
  `main(){ println("café".toUpperCase()); println("CAFÉ".toLowerCase()) }` →
  **JVM/Script/JS `CAFÉ`/`café`**; **Native x86 `CAFé`/`cafÉ`** (as letras
  ASCII são dobradas, o `é`/`É` de 2 bytes UTF-8 não é tocado). Sonda `sw2b.kf`.
- **Causa raiz:** `RuntimeStringOps.emitStringCase` (`kof_string_to_upper`/
  `kof_string_to_lower`, `:359-413`) faz `±0x20` **byte a byte** só na faixa
  `a-z`/`A-Z` (`cmpb $97`/`cmpb $122`) — nunca faz fold de acentos. Idêntico
  em riscv/aarch (o aarch traduz o x86). JVM (`JvmOpCollections:501` usa
  `Character.toUpperCase`) e o interpretador (`KofInterpreterCollections:68`
  `String.toUpperCase`) fazem case-fold Unicode. **Paridade R5 quebrada** em
  método do reference (`type-system.md:290`).
- **Irmãos do mesmo gap (todos ASCII-only no Native):** os conversores
  `strings.*` que alocam String — `capitalize`/`uncapitalize`/`reverse`/
  `toCamelCase`/`toPascalCase`/`toSnakeCase`/`toKebabCase`/`slugify`/
  `padLeft`/`padRight` (`KofStrings.java:39-69` documenta "ASCII-first no
  Native; casos não-ASCII ficam em KofStringsTest (JVM+JS)"). A matriz trava
  só ASCII de propósito (`conformance-matrix.md` §"S2b ASCII"/NAT-STR01).
- **Estado do registro (13/09):** o `docs/development/README.md:91` já listava
  NAT-STR01 como "`known-bugs.md`/conformance-matrix", mas a seção **não
  existia aqui** (só a nota na matriz) — lacuna de registro fechada nesta
  unidade.
- **Alcance honesto do fix:** Latin-1 (`é`/`É`) é factível (2 bytes UTF-8,
  comprimento preservado) com tabela de 256 entradas; scripts além de Latin-1
  (grego/cirílico/astral) exigem tabela Unicode completa (multi-sessão). O
  fix fecha a paridade R5 para Latin-1 e mantém o resto como gap honesto
  documentado — nunca silencioso. **NÃO corrigido nesta unidade** (docs-only;
  toca `nat/`, lane GC viva).



### 162. Regressão `17596ce7` (gate/pow): emissão de `kof_heap_root_start`/`kof_heap_root_end` deletada do `NativeBackend.emit` sem substituto → todo nativo x86 com GC no runtime falha o link (`undefined reference`) — ✅ CORRIGIDO 13/09

- **Menor repro:** qualquer compilado nativo x86 a partir do módulo **kof-script**
  (`KofScript.runFile(f, Target.NATIVE)`), mesmo trivial:
  ```kof
  main() { println(7) }
  ```
  → `ld: na função "kof_gc_mark": undefined reference to 'kof_heap_root_start'/'kof_heap_root_end'` (COMP001).
- **Causa raiz (retificada pós-rebase 13/09):** a lane nat (#113, `53b089fd`)
  já tinha movido a ABERTURA do intervalo de raízes (`kof_heap_root_start`)
  para o `.data` do programa E trocado o topo do intervalo pelo `_end` do
  linker (`RuntimeGc.kof_gc_mark`: `leaq _end(%rip)`) — o `kof_heap_root_end`
  explícito NÃO deve existir hoje (encolheria o intervalo → under-mark). O
  meu restore do `_end` em `.bss` foi descartado no rebase (regra 8 — lado do
  dono preservado). O que RESTOU real como bug: o **fallback de poda por
  CWD** — `RuntimeSlices.readSourceAndOrder()` resolve `NativeRuntime.java`
  relativo ao diretório corrente; compilando do módulo kof-compiler a poda
  funciona (fatia GC sai do subset); de kof-script/kof-cli o mapa cai no
  fallback completo (R6) e qualquer referência de GC não resolvida quebra o
  link. Após o pull com `53b089fd`+fixes da lane nat no HEAD, o
  `KofScriptTest` 25/0 — a superfície fica consistente.
- **Prova:** `KofScriptTest.evalNativeTarget` (25/0, falhava 1/25 antes do
  sync do HEAD); gate 4-módulos BUILD SUCCESS — 1646 testes, 0 falhas reais
  (2 reports stale de classes deletadas limpos; 2 erros ambientais node).
- **Lição:** emissão de símbolo referenciado pelo runtime é **contrato do
  backend** — remover exige verificar TODOS os callers de pruneRuntime/fallback
  (CWD-dependente), não só o caminho do módulo que os testes da lane exercitam.

### 163. Script (interpretador): 2º parâmetro largo (`Double`/`Long`) de método/função de usuário é lido como `null` → NPE — ✅ CORRIGIDO 13/09 (paridade 4-target; achado no probe do split `NativeBackend`)

- **Sintoma:** qualquer função/método/construtor de usuário com **dois ou
  mais** parâmetros largos (`Double`/`Long`) — ou um largo **não-último** —
  quebra no interpretador (KofScript):
  ```kof
  Double soma(Double a, Double b) { return a + b }
  main() { println(soma(1.5, 2.5)) }
  ```
  → `ERR: Cannot invoke "java.lang.Number.doubleValue()" because "b" is null`
  (exit 1). JVM e Native imprimem `4.0`; JS imprime `4` (bug 44 floatprint,
  alheio). Era **divergência silenciosa de paridade** entre targets.
- **Menor repro / matriz medida (`OVD.kf`, `DBL*.kf`):**
  | forma | Script antes | JVM/Native |
  |---|---|---|
  | `Double f(Double a, Double b)` | NPE (`b` null) | `4.0` |
  | `Double f(Double x)` | `7.0` ✅ | `7.0` |
  | `Double f(Double a, Int b)` | NPE (`b` null) | `3.0` |
  | `Double f(Int a, Double b)` | `3.5` ✅ | `3.5` |
  | `Long f(Long a, Long b)` | NPE (`b` null) | `9000000003` |
  Só quebra quando um parâmetro largo **não é o primeiro** (o 2º cai no
  índice errado). Mesma classe de falha do §64, mas no **interpretador**, não
  no KofJS — a correção do §64 foi no `JsMethodParser`.
- **Causa raiz:** `KofInterpreter.invokeKof` copiava o array compacto de
  argumentos direto para `f.locals` (`System.arraycopy(args, 0, f.locals, 0,
  args.length)`). O layout da IR — igual ao bytecode JVM — dá **2 slots** a
  `Double`/`Long` (`TypeMetrics.isDoubleWidth`, usado por
  `CompilerFunctionLowering`/`CompilerClassLowering`), então o 2º parâmetro
  largo vive no slot `+2`, que ficava `null` (ou era sobrescrito por um
  argumento posterior). O array de locais também podia ser **curto** para o
  layout real (parâmetro largo não lido → `ArrayIndexOutOfBounds`).
- **Fix:** posicionar os argumentos nos slots reais — `this` em 0 quando
  `hasThis`; depois cada parâmetro avança `2` (largo) ou `1`; o tamanho do
  array de locais parte do layout de parâmetros, não do nº de argumentos.
  A lógica ficou em `KofInterpreterValues.bindLocals` (colaborador de
  valores/coerções do interpretador) para manter `KofInterpreter` ≤500 linhas.
- **Prova (Q1 — teste que falharia no código velho):**
  `KofInterpreterParityTest.wideParametersOccupyTwoSlots` (novo) — função,
  método de instância, construtor, `Long`, parâmetro largo não lido e ordem
  mista `Int, Double, Double`, tudo comparado byte-a-byte com o JVM
  compilado + fork; **falhava com o interpretador antigo** (`exit code
  divergente expected <0> but was <1>`) e passa com o fix
  (`KofInterpreterParityTest` 23/23). Probes 4-target `OVD`/`OVDX`/`DBL7`:
  Script agora idêntico a JVM/Native.
- **Arquivo:** `kof-compiler/src/main/java/dev/kof/compiler/KofInterpreterValues.java`
  (`bindLocals`, chamado por `KofInterpreter.invokeKof`).
### 165. JS: bloco `ui-jsonmap` entra no `// kof:seeds` mas o `export function kofJsonEncodeMap` não vai p/ o `kof-runtime.mjs` — `json.encode(Map)` quebra SÓ com node presente

- **Menor repro (compilar p/ JS com node no PATH, v22):**
  ```kof
  import kof.json.*
  main() {
      var m = mapOf("b", 2)
      m.put("a", 1)
      println(json.encode(m))
  }
  ```
  → o `kof-runtime.mjs` gerado traz `// kof:seeds kofJsonEncodeMap,kofMapNew,kofMapPut,kofPrintln`
  mas **NÃO** traz `export function kofJsonEncodeMap`; o `Default.mjs` importa
  esse nome de `./kof-runtime.mjs` → `SyntaxError: The requested module
  './kof-runtime.mjs' does not provide an export named 'kofJsonEncodeMap'`
  (exit 1). Reproduz com UM mapa só — multi-mapa/`mapOf()` vazio NÃO são
  necessários. Medido 13/09 com `CompilerDriver.compile(..., Target.JS)`
  direto (sem CLI, sem JavaFX).
- **Como apareceu:** a célula `jsonenc-map` (`ab85cfae`, §106 residual, 13/09
  06:06) entrou no `ConformanceMatrixTest.conformanceJson` — o gate deles
  mediu **1643/0/13-node**: sem node o JS é pulado/errado-ambiental e a célula
  nunca executou o runtime. Com node presente (esta máquina), a célula
  EXECUTA e acha que o export não chega ao bundle. `ConformanceMatrixTest`
  fica 1 falha fora do baseline deles — **vermelho real, não é desta lane**.
- **Pista de causa (sem tocar JS alheio):** `JsRuntimeSlices.select()` marca o
  unit/provider do seed como live (`owner.get(seed)`) e o comentário
  `kof:seeds` é escrito a partir dos seeds pedidos, mas o TEXTO do unit que
  proveria `kofJsonEncodeMap` não entra no core — sugerido: divergência entre
  `providers` (quem declara) e o texto retido dos units do bloco `ui-jsonmap`
  (chunk/fallback — padrão §160/§162 da própria família: bloco protegido
  inteiro vs chunk granular). Dono §106/js-slices deve confirmar com
  `select(List.of("kofJsonEncodeMap", ...))` no package js.
- **Prova da atribuição:** `65d4a97e` (decompiler, esta lane) toca só
  `kof-cli/**`+docs — kof-compiler NÃO depende de kof-cli (grafo de build).
  Stash com working tree limpo no HEAD base → falha idêntica (medido 13/09).
- **⚠️ RE-VERIFICAÇÃO 13/09 (lane gate/qualidade, dono 192.168.100.15) — NÃO
  REPRODUZ em build limpo; é a armadilha da constante INLINED.** Medido no
  HEAD `d2a8a618` com `mvn -o -pl kof-compiler -am clean compile`: o
  `kof-runtime.mjs` gerado **TEM** `export function kofJsonEncodeMap`
  (linha 138) e a célula `jsonenc-map` **passa** (executada via
  `KofJsRunner`/GraalJS). Reproduzi o sintoma de propósito: com
  `JsRuntimeSlices.class` compilado antes de `JsRuntimeUiJsonMap` ganhar o
  helper (build incremental sem `clean`), o bundle sai SEM o export —
  idêntico ao §165. Causa: `JsRuntimeUiJsonMap.JSON_MAP_RUNTIME` é
  `static final String` (**constante de compilação**) e é **inlined** em
  `JsRuntimeSlices.BLOCKS`; editar o runtime sem recompilar o consumidor
  deixa classes stale (lição já documentada para `JsRuntimeCore`/slices).
  Ação: re-rodar com `clean` antes de confirmar; o dono da lane
  §106/js-slices decide fechar (provável não-bug) ou blindar contra o
  inlining. Sem `node` neste host, o caminho node fica pendente de
  confirmação — **não fechada por mim** (lane alheia).
- **RE-VERIFICAÇÃO 13/09 (dono 192.168.100.17, node v22.22.3 PRESENTE neste
  host):** em build limpo (`rm -rf target/classes` + `mvn -o -q -pl
  kof-compiler -am compile`), o `kof-runtime.mjs` gerado do meu menor repro
  traz `export function kofJsonEncodeMap` (linha 138) e o `Default.mjs` roda
  com **node** → `{"a":1}`, exit=0. `ConformanceMatrixTest` 11/11 verde
  (incluindo `conformanceJson`/`jsonenc-map`) — com node real, não GraalJS.
  Reproduzi o sintoma de novo só com classes stale (sem `clean`). Conclusão
  dual: **(a) o §165 é não-bug no código** (a pista de causa dele — export
  nunca no runtime — não se confirma); **(b) a TRAP de inlining de
  `static final String` é real e armou dois falsos-positivos seguidos**
  (§165 por mim, §81-completo pela suite run incremental). Fechar/ blindar é
  do dono §106/js-slices; a fila do cabeçalho deve tratar §165 como
  **não-reproduzível em build limpo** até lá.

### 166. JS: shim DOM (#121) estoura o gate de tamanho do bundle hello (`ArtifactSizeTest.helloJsRuntimeSizeWithinBaseline`) — 8297B > 8085B — ✅ CORRIGIDO 13/09 (opção (a): baseline re-medido; lane bugs-and-gaps `192.168.100.15`)

- **Resolução 13/09 (lane bugs-and-gaps):** opção (a) — o shim DOM é
  funcionalidade LEGÍTIMA e vive no préâmbulo `always` do core (`if (typeof
  document === "undefined")`, sem DECL de topo → o chunker o trata como
  always, exatamente como o shim `kof_platform` do #104). Re-medido o baseline
  do gate: `HELLO_JS_BYTES` **7.700 → 8.297** (mesmo processo do #104, que fez
  6.873 → 7.700), com nota no próprio `ArtifactSizeTest`. `ArtifactSizeTest`
  volta a 6/6 (a suíte 4-módulos volta a 0 falhas de código). **Follow-up T2
  (não é bug):** mover o shim p/ unit alcançável por UI faria o baseline cair
  de novo; fica registrado como meta de poda, não como falha. A opção (b)
  (on-demand) exige estender o chunker do `JsRuntimeSlices` p/ préambulos
  condicionais — desenho da lane js-slices.
- **Sintoma (medido 13/09, build limpo `rm -rf target/classes` + `mvn -o -q
  -pl kof-compiler -am compile`):** suíte completa do HEAD `c8b756c2` →
  `Tests run: 6, Failures: 1` em `ArtifactSizeTest`:
  `runtime JS inchou: 8297B > 8085B (baseline 7700B +5%)`. É a ÚNICA falha
  da suíte 4-módulos limpa (compiler 1479/1, script 31/0, c 5/0, cli 148/0).
- **Menor repro:** qualquer `mvn -o test -pl kof-compiler -Dtest='ArtifactSizeTest'`
  em build limpo no HEAD atual. O programa é o próprio `main(){println("hello")}`
  do teste — o que inchou foi o runtime, não o programa.
- **Causa raiz (atribuída por bisect de build, não opinião):** `cd8ad70b`
  (merge PR #124, fix #121, jonasrochasilva-prog, 09:29) adicionou +9 linhas
  de dataset/disabled/classList ao shim DOM dentro de `JsRuntimeCore.CORE_RUNTIME`
  — o CORE é o bloco que todo bundle JS carrega (até hello), então o bundle do
  hello cresceu 7700→8297 (+7.8% > tol de 5%). Prova A/B na MESMA árvore de
  fontes: em `b05b3906` (imediatamente antes do merge) a célula **passa**
  (1/1 verde, medido com worktree + rebuild limpo); em `cd8ad70b` falha.
  O baseline do gate (`HELLO_JS_BYTES = 7_700`) nunca foi re-medido.
- **Por que não é desta lane (registro = docs/decompiler, dono
  192.168.100.17):** minha unidade toca só `kof-cli/**`+docs e o gate falha
  no HEAD sem meu working tree (stash → mesma falha, medido 13/09).
- **Correção é da lane JS/gate:** (a) decidir se +9 linhas de shim DOM no
  CORE é aceitável (então re-medir o baseline 7_700 → novo valor com nota),
  OU (b) mover dataset/disabled/classList p/ um bloco on-demand (UI/DOM,
  `always=false` — hello não puxa DOM; o gate existe exatamente p/ capturar
  isto). Precedente do formato (a): comentário no próprio
  `ArtifactSizeTest` (linha 21): "Quando uma poda por alcançabilidade fechar,
  atualiza-se o baseline". Armadilha conexa: o gate só vê o inchaço em build
  LIMPO — incremental com `JsRuntimeCore.class` stale passa (mesma trap do
  §165, terceira vítima da família).

### 167. Bitwise/shift com `Long` misturado: JVM VerifyError + JS TypeError/máscara errada + overflow de Long no JS — ✅ CORRIGIDO 13/09 (4 targets, lane bugs-and-gaps `192.168.100.15`)

- **Contexto:** achado na varredura da lane bugs-and-gaps 13/09 (caça Q4 sobre
  bitwise/shift). Os 3 sintomas são da MESMA raiz: a promoção binária
  `int`↔`long` não era aplicada em bitwise/shift. Gatilho: só com **variáveis**
  (literais são constant-folded antes do backend) — por isso escapou das
  matrizes anteriores, que só usavam literais `Int`.
- **Menor repro / matriz medida (oracle = JVM compilado; probes `MIXB.kf`/`OVF.kf`/`L2I.kf`):**
  | expressão | JVM antes | Native/Script | JS antes |
  |---|---|---|---|
  | `var l=5L; l & 3` | **VerifyError** `land` | `1` ✅ | **TypeError** Cannot mix BigInt |
  | `var i=5; var l=5L; i & l` | **VerifyError** (`iand` c/ long; inferência INT) | `5` ✅ | **TypeError** |
  | `var i=-1; var l=4294967295L; i & l` | **VerifyError** | `4294967295` ✅ | **TypeError** |
  | `l << 2L` / `l << 70L` | **VerifyError** `lshl` (RHS long) | `20` / `320` ✅ | `20` / `320` ✅ |
  | `var i=1; i << 40L` | **VerifyError** | `256` ✅ | **TypeError** |
  | `l << 70` (RHS int) | `320` ✅ | `320` ✅ | `5902958103587056517120` ❌ (sem máscara 0x3f) |
  | `var n=-1L; n >>> 1` | `9223372036854775807` ✅ | idem ✅ | `-1` ❌ (USHR virava SHR) |
  | `Long.MAX + 1L` | `-9223372036854775808` ✅ | idem ✅ | `9223372036854775808` ❌ (BigInt ilimitado) |
  | `var l=5L; var i=l as Int; i + 1` | `6` ✅ | `6` ✅ | **TypeError** (L2I devolvia BigInt) |
- **Causa raiz (3 arquivos):**
  1. **JVM/lowering** — `ExpressionBinaryLowerer` mandava bitwise/shift para o
     branch genérico sem `emitWideningIfNeeded`; e `ExpressionTyper` inferia
     INT p/ `int & long` (o box usava `Integer.valueOf` sobre um long →
     `Bad type on operand stack`). Shift precisa do tipo do operando
     ESQUERDO (JLS 15.19) e do RHS **narrowado p/ int** (`L2I`), pois
     `lshl`/`ishl` tomam `(long,int)`/`(int,int)`.
  2. **JS** — `JsCallEmitter.longBinaryExpr`/`longOperand` só envolviam
     **literais** `JsNumber` com `BigInt()`; variáveis Int-typed chegavam como
     Number → `Cannot mix BigInt and other types`. Shift BigInt não mascarava
     (0x3f) e USHR virava SHR.
  3. **JS overflow** — BigInt é ilimitado; a aritmética não reaplicava o wrap
     de 64 bits do JVM (`asIntN(64, …)`), e `L2I` devolvia BigInt (deveria
     voltar a Number, pois Int no JS é Number).
- **Fix:** (1) `ExpressionBinaryLowerer` — novos branches p/ bitwise (common
  numeric type) e shift (resultado = tipo promovido do lado esquerdo, RHS
  `emitPrimNarrow`→int); `ExpressionTyper` promove a inferência de forma
  idêntica. (2) `JsCallEmitter` — `longOperand` envolve QUALQUER operando com
  `BigInt()` (idempotente); shift long mascara o contador com `& 63n`; USHR =
  `BigInt.asUintN(64, l) >> (r & 63n)`; aritmética long reaplica
  `asIntN(64, …)` (wrap); NEG long wrap; `L2I` = `Number(BigInt.asIntN(32, l))`;
  shift int normaliza o RHS BigInt p/ Number 32-bit.
- **Prova (Q1 — falhava no código velho):**
  - `BackendParityTest.parityLongBitwiseShiftMixed` (novo) — JVM×JS + golden
    JVM; **falhava antes** (`frame crash … longbitshift.kf:34:5`).
  - `KofInterpreterParityTest.longBitwiseShiftMixed` (novo) — interpretado×JVM
    byte-a-byte; **falhava antes** (`exit code divergente expected <-1> but was <0>`).
  - `ConformanceMatrixTest.conformanceCoreArithmetic` — caso `bitwise`
    **estendido** com o bloco Long (30 linhas, `Set.of()` — os 4 targets),
    passa 4/4.
- **Nota sobre o §81 (overclaim):** o §81 declarou "paridade 64-bit real" mas
  só cobria **parse** (`toLong`) e literais; a **aritmética** de Long no JS
  divergia (overflow sem wrap, shift sem máscara, mistura Int/Long lançando).
  Esta correção fecha a lacuna; o texto do §81 fica como registro histórico.
- **Arquivos:** `kof-compiler/src/main/java/dev/kof/compiler/ExpressionBinaryLowerer.java`,
  `.../ExpressionTyper.java`, `.../js/JsCallEmitter.java`, `.../js/JsLongEmitter.java`
  (colaborador de LONG/shift extraído p/ manter `JsCallEmitter` ≤500).

### 168. Residual #126: célula `jsondec-map` da matriz deref `Map.get()` sem narrow → SEM049 (gate vermelho real) — ✅ CORRIGIDO 13/09 (migração do programa da célula ao congelado §87)

- **Sintoma (medido, não inferido):** após `61495f69` (#126: `json` ganha
  handler em `MemberCallNamespaces.inferStatic`), `ConformanceMatrixTest.
  conformanceJson` passou a FALHAR no **JVM compile**:
  `Diagnostic[ERROR, line=5, column=28, receiver is nullable (T?); narrow
  first, code=SEM049]` em `println(m.get("1").name)`. A/B por worktree:
  pai `5a116284` → célula 1/1 VERDE; `61495f69` → 1/1 VERMELHO. Suíte
  HEAD antes deste fix: 1482 run / **1 fail** (só ConformanceMatrixTest).
- **Causa raiz (não é bug de código — é exposição de programa ilegal):**
  §126 ensinou o `SemanticAnalyzer` a conhecer `json`, então
  `json.decode<Map<String,CardText>>(…)` agora inference `m` corretamente e
  `m.get("1")` sai `CardText?` (congelado §87/SG-008: `get()` devolve `V?`
  sempre). A célula SEMPRE teve deref sem narrow — só não era pega porque o
  tipo de `m` era desconhecido antes (o lowering JVM resolvia no fim). O
  golden (`2\nMagician`) está CORRETO; o **programa** é que violava o
  congelado. Proibido "consertar" relaxando a asserção ou stubando (portão
  Q0/Q5): a migração correta é narrow no programa.
- **Menor repro:** `var m = json.decode<Map<String,CardText>>(…); println(m.get("1").name)`
  → SEM049; `var c = m.get("1"); if (c != null) println(c.name)` → compila
  (medido fora do harness com `CompilerDriver.compile(..., Target.JVM)`).
- **Correção:** narrow adicionado no programa da célula
  (`ConformanceMatrixTest:1591`, `jsondec-map`); golden e targets
  inalterados. Não mexi no handler do §126 (está certo — só tornou o erro
  visível). Suíte completa após o fix: **1682 run / 0 falhas / 0 erros /
  5 skip** (os 5 = DB externo; node presente, JS roda).
- **Arquivo:** `kof-compiler/src/test/java/dev/kof/compiler/ConformanceMatrixTest.java`
  (só a célula). Aquisição: 13/09, dono = 192.168.100.17 (achado ao rodar o
  gate para fechar §166; atribuído por A/B, não por memória).
## §170 — `json.encode`/`json.decode` não validam aridade: check passa e o bytecode sai inválido (VerifyError) — Issue #126 (PublioSantos, 13/09) ✅ CORRIGIDO 13/09 (lane issues 9094)

- **Sintoma:** `json.encode(x, 4)`, `json.encode()` e `json.decode("x")` (sem
  type-arg) passavam no `kof check` e quebravam em runtime com
  `java.lang.VerifyError: Operand stack underflow` (JVM) / undefined reference
  (Native). Os outros namespaces (`strings.escapeJson`, `time.daysInMonth`) já
  rejeitavam aridade errada no check com `SEM025`.
- **Menor repro:** `record No(String t)\nmain() { println(json.encode(No("x"), 4)) }`
  → check "no errors"; `java -cp out Default.Main` → VerifyError bci@6
  (`invokevirtual` com pilha vazia). Medido no HEAD da beta antes do fix.
- **Causa raiz:** o namespace `json` só era despachado no lowering JVM
  (`MethodCallNamespaces.inferStatic` + `ExpressionJsonCallLowerer`, guard
  `arguments().size() == 1`). O **caminho semântico** (`MemberCallNamespaces`,
  usado por `check`) não tinha nenhum `if` para `json` — a chamada caía em
  UNKNOWN sem diagnóstico. Com aridade errada, o lowerer não emitia nada e o
  `println` consumia slot inexistente.
- **Correção:** branch `json` em `MemberCallNamespaces.inferStatic` validando o
  contrato fixo (encode 1 arg; decode 1 arg + type-arg) — inválido → `SEM025`
  com a forma correta. **Caso VÁLIDO devolve `null`** (continua a cadeia
  exatamente como antes): dar tipo concreto ao `decode` mudaria o narrowing a
  jusante e quebraria `l.get(1).x` (regressão `ConformanceMatrixTest.conformanceJson`
  — `List<T>.get` é `T?` no semântico; pegamos isso ANTES do push, suíte).
- **Residual conhecido:** a primeira versão pushada deste fix (`61495f69`) deu
  tipo concreto ao `decode` no semântico e expôs o deref sem narrow da célula
  `jsondec-map` (SEM049) — registrado e CORRIGIDO no §168 (dono 192.168.100.17);
  o refin `return null` (caso válido) entrou em HEAD via `3ab4c99e` e elimina a
  raiz; o narrow da célula fica como programa-congelado-§87 correto (belt).
- **Prova:** `SemanticResolutionTest.wrongArityOnJsonNamespace` (4 casos
  rejeitados + encode/decode corretos verdes) e `ConformanceMatrixTest#conformanceJson`
  (a célula que a primeira versão minha QUEBROU, agora verde). Repr do #126
  agora: `: error: Cannot resolve method 'encode' on namespace 'json' — use
  json.encode(x) (1 arg) [SEM025]`. Suíte compiler 1483/0/156. Cross-target: o
  check é compartilhado IR, vale p/ os 4 alvos.
- **Nota de processo (honesto):** o fix entrou em HEAD pela mão da irmã 9093
  (`git add -A` em árvore compartilhada capturou meus `MemberCallNamespaces`
  +`SemanticResolutionTest` nos commits `3ab4c99e`), após minha primeira versão
  (`61495f69`) ter a regressão. Registro aqui para rastreabilidade #126.

## §171 — `synchronized` é aceito mas não chega ao bytecode (sem ACC_SYNCHRONIZED) — Issue #125 (PublioSantos, 13/09) ✅ DIAGNÓSTICO FEITO 13/09 (lane issues 9094) — warning SEM091; non-goal ratificado NÃO muda

- **Sintoma:** `synchronized Int somar(...)` (e `volatile`/`transient`/`native`)
  passava no check e rodava, mas o `.class` saía sem `ACC_SYNCHRONIZED`
  (`javap`: Kof `0x0001` vs Java `0x0021`). Contador "synchronized" compartilhado
  não tinha proteção — falsa sensação de segurança.
- **Menor repro:** `class C { synchronized Int f() { return 1 } }\nmain() { println(C().f()) }`
  → check "no errors"; `javap -v` do método: `flags: ACC_PUBLIC` (sem
  `ACC_SYNCHRONIZED`). Medido na beta.
- **Causa raiz:** `parseModifiers` (`TypeDeclarations.java:49`) aceita o token e
  guarda como string, mas `AccessFlags` **não define a constante** SYNCHRONIZED
  (0x0020 é SUPER) e `CompilerTypeSupport.computeAccess` cai em `default -> 0` —
  o modificador é **descartado em silêncio**.
- **Decisão (regra 6 — NÃO é minha):** `synchronized`/`volatile` são
  **non-goals ratificados** na superfície da linguagem
  (`concurrency-memory-model.md §5`: "Kof não expõe mecanismo — Channel é a
  abstração"; `specification-gaps.md:365`). Implementar `ACC_SYNCHRONIZED` na JVM
  seria divergir dos outros 3 targets e **violar o memory model**. Então o bug
  NÃO é "falta o flag" — é o **silêncio**.
- **Correção (R6 — nunca silencioso):** `warnMechanismModifiers`
  (`CompilerClassLowering`, chamado em lowerField+lowerMethodInner) emite
  **warning não-fatal SEM091** com a posição do membro nomeando o substituto
  (spawn/await/Channel). Não-fatal preserva retrocompatibilidade (regra 2 — o
  que compila hoje continua compilando).
- **Prova:** `SemanticResolutionTest.mechanismModifierWarnsButStaysGreen` —
  synchronized/volatile avisam, o programa compila (warning não quebra), e
  código limpo não polui (0 SEM091). CLI: `C.kf:8:42: warning: ... [SEM091]`,
  build jvm ok. Suíte compiler 1483/0.
- **Remanescente (não-alvo deste fix):** (i) o off-by-one de posição do `volatile`
  de campo (reporta a linha do próximo membro) é quirk pré-existente de
  `ctx.pos()` pós-`advance` em `ClassMemberParser` — o caso da issue (método) é
  exato; registrado, é face separada. (ii) se a mantenedora quiser que
  `synchronized` DEIXE de ser non-goal e vire ACC_SYNCHRONIZED + paridade cross
  (monitores no Native), é decisão de design com bump — planejar, não editar.

## §172 — Atribuição composta de SHIFT (`<<=`, `>>=`, `>>>=`) era parseada mas baixada como atribuição SIMPLES (só o RHS gravado) — ✅ CORRIGIDO 13/09 (lane development/translator, dono = 192.168.100.22)

- **Sintoma:** `var x = 6; x <<= 2; println(x)` imprimia `2` (só o RHS), não
  `24`; `x >>= 1` dava `1` em vez de `3`; `x >>>= 1` idem. Miscompilação
  SILENCIOSA (compila, roda, resultado errado) nos 4 targets — o pior tipo
  de bug (Q0).
- **Menor repro:** `main() { var x = 6; x <<= 2; println(x) }` → `2` (Kof JVM);
  `x = x << 2` no mesmo programa dá `24` (a forma binária sempre funcionou).
- **Causa raiz:** o parser (`Lexer`/`ExpressionParser`) reconhece
  `LESS_LESS_EQUAL`/`GREATER_GREATER_EQUAL`/`GREATER_GREATER_GREATER_EQUAL` e
  produz `AssignmentExpr(op="<<=")`, mas `ExpressionAssignmentLowerer`
  checava os compostos com uma lista literal que **não incluía os shifts**
  (`+=,-=,*=,/=,%=,&=,|=,^=`): o op caía no caminho de **atribuição simples**
  (emitia só o RHS e o `KofStoreLocal`) e o `KofBinary(SHL)` nunca era
  emitido. Gatilho: só a forma COMPOSTA (a binária `x = x << 2` tem ramo
  próprio em `ExpressionBinaryLowerer`, §167).
- **Correção:** helper único `isCompoundOp` (fonte da verdade dos compostos,
  agora com os 3 shifts) + `compoundBinaryOp` com `SHL/SHR/USHR`, aplicado
  nos 6 sítios (campo estático por nome, campo de instância, campo estático
  qualificado, campo via variável, elemento de array, box, local). **Segunda
  face achada na prova:** `Long <<= Long` dava `VerifyError: Bad type on
  operand stack @ lshl` — o JVM usa `(long,int)` e o shift count é sempre
  int; novo helper `emitCompoundRhsConv` faz `L2I` no RHS do shift (espelha
  o §167 do caminho binário) e widening normal nos demais compostos.
- **Prova:** `CoreRegressionE2ETest.compoundShiftAssignments` — JVM+JS, golden
  medido no oracle (`24/3/2147483644/2/14/7/1099511627776`); o teste falhava
  no código velho (`2` no primeiro valor) e pega a 2ª face (`VerifyError` sem
  o L2I). `TranslateTest` 33/33 (o translator que emite `<<=` agora roda o
  output — a caça Q4 que achou o bug).

### §173 — `++`/`--`/compound assignment em `Long`/`Double`/`Float` + incremento de elemento de array: JVM VerifyError, Native core dump, Script/JS sobem erro — ✅ CORRIGIDO 13/09 (4 targets, lane bugs-and-gaps `192.168.100.15`)

- **Contexto:** achado na caça Q4 sobre o §167 (mesma sessão). O `++`/`--` e o
  compound assignment em tipos de **2 slots** emitiam literal `INT 1` num
  binário do tipo do alvo (`long`/`double`/`float`) → frame corrompido. E o
  incremento de **elemento de array** não rematerializava `[array, index]` antes
  do `arraystore`.
- **Menor repro / matriz medida (oracle = JVM compilado; probes `CMP.kf`/`S168.kf`/`ARR.kf`/`ARRI.kf`/`FIELD.kf`):**
  | construto | JVM antes | Native antes | Script antes | JS antes |
  |---|---|---|---|---|
  | `var c=1L; c++` | **VerifyError** (`LADD` c/ int) | `0` ❌ | `Long.valueOf/1` ❌ | **COMP002** |
  | `var c=1L; ++c` | **VerifyError** | `0` ❌ | `Long.valueOf/1` ❌ | **COMP002** |
  | `var d=1.5; d++` | **VerifyError** | `0.0` ❌ | `Double.valueOf/1` ❌ | **COMP002** |
  | `var f=1.5f; f++` | `1.5` ✅ | `1.5` ✅ | `1.5` ✅ | `1.5` ✅ |
  | `var l=100L; l /= 3` | **VerifyError** (I2L no topo errado) | — | — | — |
  | `var a=new Long[2]; a[0]++;` | **VerifyError** | **core dump** | `NoSuchElementException` ❌ | **stack underflow** |
  | `var a=new Int[3]; a[0]++;` | **VerifyError** | **core dump** | `NoSuchElementException` ❌ | **stack underflow** |
  | `var b=Box(1L,1.5); b.n++` | **VerifyError** (`Bad local variable type`) | `2` ✅ | `2` ✅ | `2` ✅ |
  | `var max=9223372036854775807L; max++` | — | — | — | — (pós-fix: wrap p/ `-9223372036854775808`, 4/4) |
- **Causa raiz (4 arquivos, 3 sub-faces):**
  1. **Literal do incremento** (`CompilerEmissionHelpers.emitIncrementOne`): o
     `1` era sempre `INT`. Para `long`/`double`/`float` o binário `ADD`/`SUB`
     esperava os DOIS operandos no tipo do alvo → `LADD`/`DADD` sobre (largo,
     int). Fix: literal no tipo do alvo (`1L`/`1.0f`/`1.0`).
  2. **DUP de largura errada** (`CompilerEmission2.emitIncrement` local):
     `KofDup` duplica **1 slot**, mas `long`/`double` ocupam **2**. `KofDup2`
     não serve no JS (o parser o interpreta como "duplicar par array+índice").
     Fix: para tipos largos, materializa um **temp explícito** (load/store) em
     vez de dup; o temp avança **2 slots** (`TypeMetrics.isDoubleWidth`), senão
     `valTmp`/`newTmp` se sobrepõem (VerifyError `Bad local variable type`).
  3. **`arraystore` sem `[array,index]`** (`CompilerEmission2.emitIncrement`
     array): o `KofArrayStore` consome `[array, index, valor]`, mas o caminho
     emitia só o valor. Fix: materializa `#arr`/`#idx` em temps, carrega
     `[arr, idx, new]` e armazena; pós-fixado devolve o valor velho, prefixado
     o novo. Mesmo tratamento de largura nos temps.
  4. **Compound de local** (`ExpressionAssignmentLowerer`): o widening do RHS
     (`I2L` p/ `long op= Int`) saía **DEPOIS** do `KofBinary` (sobre o
     RESULTADO); movido p/ ANTES. Mesmo bug em `CompilerUiEmitter.emitFieldIncrement`
     (temps de campo largo).
- **Fix:** `CompilerEmissionHelpers.emitIncrementOne` (novo);
  `CompilerEmission2.emitIncrement` (local/array/legacy — temp explícito p/
  largos, rematerialização de array); `CompilerUiEmitter.emitFieldIncrement`
  (largura dos temps); `ExpressionAssignmentLowerer` (ordem do widening).
- **Prova (Q1 — falhava no código velho):**
  - `BackendParityTest.parityIncrementWideTypesAndArrayElement` (novo) — JVM×JS
    + golden JVM; **falhava antes** (VerifyError/COMP002).
  - `KofInterpreterParityTest.incrementWideTypesAndArrayElement` (novo) —
    interpretado×JVM byte-a-byte; **falhava antes** (JVM `FRONTEND-ERR`).
  - `ConformanceMatrixTest.conformanceCoreArithmetic` — caso `increment`
    (novo, `Set.of()` — os 4 targets), passa 4/4.
- **Arquivos:** `kof-compiler/src/main/java/dev/kof/compiler/CompilerEmissionHelpers.java`,
  `.../CompilerEmission2.java`, `.../CompilerUiEmitter.java`,
  `.../ExpressionAssignmentLowerer.java`.

### §174 — `return`/`throw` dentro de um `if` dentro do `try` → KofJS `COMP002 unexpected KofCatchStart` (paridade cross-target) — ✅ CORRIGIDO 13/09 (lane bugs-and-gaps `192.168.100.15`)

- **Contexto:** achado na caça Q4 sobre o S13a `math.parse*` (mesma sessão do
  §173). O `return` dentro de um `if` no corpo do `try` compilava e rodava nos
  targets JVM/Native/Script, mas o KofJS abortava a compilação com
  `Internal compiler error: KofJS: unexpected KofCatchStart at statement level`
  (`JsControlFlowParser.parseStatement`). Paridade regra 5 quebrada de forma
  **não-silenciosa** (COMP002), mas ainda assim um alvo que recusa programa
  válido.
- **Menor repro:** `String f(String s) { try { if (s == "x") { return "X" } return "Y" } catch (String e) { return "ERR" } }` + `println(f("x"))`.
  JVM/Native/Script → `X`; JS → **COMPILE-FAIL** (`COMP002`). O mesmo ocorre
  com `throw` no lugar do `return` e com o `if` sem `else`.
- **Causa raiz:** o `then` do `if` termina em saída incondicional (`return`/
  `throw`), então o IR **não emite** o `KofJump` de fim de `if`. O
  `JsControlFlowParser.parseIfBody` cai no ramo "else" e chama
  `JsIfThrowElse.parseElse` (§147), que percorre os statements seguintes como
  se fossem o `else` — e, ao encontrar o `KofLabel` do **endLabel do try
  envolvente**, o **consome** (linha 52). O `parseStatements` do corpo do try
  então nunca casa o endLabel e caminha até o `KofCatchStart` solto no
  statement level.
- **Fix (sem mudança de contrato/IR):** `JsIfThrowElse.parseElse` **não
  consome** um `KofLabel` que seja `isTryEndLabel` (o dono, `parseTryStatement`,
  precisa casá-lo); idem o consumo do "Label(end) — no else" e o do "end of
  else branch" em `parseIfBody` ganham a mesma guarda. A condição pré-existente
  `isTryEndLabel` (MethodCtx) já existia para o bug 49 — o §147 a tinha
  perdido no caminho do `then` incondicional.
- **Prova (Q1 — falhava no código velho):**
  `CoreRegressionE2ETest.returnInsideIfInsideTryJs` (novo) — `runBoth` compila
  JVM+JS e compara a saída byte-a-byte (`X\nY\ncaught:boom\nY`); **falhava
  antes** (JS COMP002). Golden medido no oracle JVM.
- **Bordas Q3:** `return` no `if` (com e sem statement após), `throw` no `if`,
  `if` sem `else`, catch que retorna, continuação após o `if`, e a regressão
  vizinha `nestedTryJs`/`finallyReturnJs`/`finallyReturnJvm` (55/55 no
  `CoreRegressionE2ETest`).
- **Arquivos:** `kof-compiler/src/main/java/dev/kof/compiler/js/JsControlFlowParser.java`,
  `.../js/JsIfThrowElse.java`.

### §175 — kof_string_to_double("") devolve 0.0 no Native (JVM lança) — PARIDADE — ✅ CORRIGIDO 13/09 (lane development .18, mesma sessão do S13b)
- **Sintoma:** `math.parseDoubleOrDefault("", d)` no Native x86/riscv devolve 0.0 (a comparação com d falha); JVM/JS devolvem o default `d` (o parse lança NumberFormatException, o wrapper captura). O mesmo vale para `.toDouble()` direto: `"".toDouble()` no Native = 0.0 silencioso; no JVM = exceção (R6 — viola paridade).
- **Causa raiz:** o caminho `.Lpdd_vazio` (RuntimeStringParseFp x86, linha ~67) e `.Lpd_vazio` (NativeRiscvAsmRtB31 riscv, linha ~87) devolvem `xorpd %xmm0,%xmm0` (0.0) em vez de saltar para `.Lpd*_throw`. O trim vazio é tratado como "sucesso com valor 0" — herdado do comportamento pré-S13 (não coberto pelo golden de KofStringParseTest, que não testa `"".toDouble()`).
- **Menor repro:** `main() { try { println("".toDouble()); } catch (String e) { println("T") } }` → JVM: exceção→T; Native x86/riscv: `0.0` (ou `println("".toDouble() == 0.0)` → true no Native, exceção no JVM).
- **Fix proposto:** trocar `xorpd %xmm0, %xmm0` por `jmp .Lpdd_throw` (x86) e `j .Lpd_throw` (riscv) nos dois vazio-labels; aarch herda via tradutor. Re-medir golden: `parseDoubleOrDefault("", d)` passa a devolver `d` nos 5 alvos (a célula stdmathparseord já está escrita com o valor pós-fix; o golden do KofStringParseTest NÃO muda pois "" não está lá). Impacto no golden S13a stdmathparse: nenhum ("" só aparece no parseInt T4, que lança nos 4).
- **Fix (13/09, mesmo dia do aberto):** `RuntimeStringParseFp.java` — `.Lpdd_vazio` trocado por `jmp .Lpdd_throw` (era `xorpd %xmm0,%xmm0` + pop×4 + ret); `NativeRiscvAsmRtB31.java` — `.Lpd_vazio` trocado por `j .Lpd_throw` (era `li a0,0; j .Lpd_ret`); aarch herda linha-a-linha no tradutor (j já suportado). Contrato = JDK: `Double.parseDouble("")`/`"   "` lança.
- **Prova (Q1 — falhava no código velho):** vetores T8/T9 no `FP_GOLDEN` de `KofStringParseTest` (`"".toDouble()`/`"   ".toDouble()` → T nos 4 alvos — o x86 dava `0.0` + `S8` no código velho); linhas `parseDoubleOrDefault("", 1.5) == 1.5` e `("   ", -0.25)` adicionadas ao golden PARSEORD (KofMathTest 24/24), célula `stdmathparseord` (5 vetores Double now, matriz 4/4) e `mathParseOrDefaultParity` (Script×JVM). Repro manual JVM/x86/JS: `T1/T2/true/true` byte-idêntico. Gate: suíte **1725/0/0** (158 skip = guard qemu + BD externos; toolchain cross ausente neste host — riscv/aarch usam o MESMO template B31, e o `ok-riscv/ok-aarch=true` no compilador prova a sintaxe; o golden cross continua com `assumeToolchain`).
- **Status:** FECHADO 13/09 (fix + prova no MESMO commit).

### 176. WEB001-T1: `KofWebJsE2ETest.jsWebServesRoutes` — server JS nunca abre a porta (TypeError `InetSocketAddress.create` engolido pelo teste) — ✅ CORRIGIDO 13/09 (build fresco + §176b morto)

> **Renumerado 13/09 (dono = 192.168.100.17):** a seção nasceu "§174" e
> COLIDIU com a §174 da lane bugs-and-gaps (`return`/`throw` em `if` em `try`
> → COMP002, ✅ CORRIGIDO, anterior no arquivo — vencedora do número).
> O número livre era 176 (175 = native `toDouble` já ocupado). Refs desta
> sessão nos commits (43fe2834/5c944709/6b776936) dizem "§174 KofWebJs" =
> historicamente §176.

- **Sintoma (medido 13/09, dono = 192.168.100.17):** `dev.kof.compiler.KofWebJsE2ETest.
  jsWebServesRoutes` → `server JS não abriu a porta <N> ==> expected: <true> but
  was: <false>`; DETERMÍNISTICO (3/3 runs isolados, portas efêmeras diferentes).
- **Atribuição (medição, não memória):** vermelho no HEAD `dc97d727`, em
  `554f129e`, `21d9ccf3` e com todo o trabalho da lane decompiler STASHADO —
  a suíte desta sessão viu o teste 0 vezes (`grep -c KofWebJs suite_faseC.log`
  = 0: o run antigo parou antes/coincidente; o teste já era vermelho na base).
  É WEB001-T1 (`abbde60b`, lane JS/web), não regressão minha.
- **Causa raiz (repro isolado fora do harness, `/tmp/opencode/webjs/Repro.java`):**
  o compile JS passa; o runner GraalJS morre no `kofWebListen`:
  `TypeError: invokeMember (create) on java.net.InetSocketAddress failed due
  to: Unknown identifier: create` — `JsRuntimeUiWeb.java:239` faz
  `HttpServer.create(new InetSocketAddress(port), 0)`: a CONSTRUÇÃO do host
  class falha no contexto do `KofJsRunner` (linha 66 `allowAllAccess(true)`
  ≠ `HostAccess.ALL` com `allowCreateInstances`/construtores habilitados —
  o polyfill/`Java.type` resolve o método estático `create` do
  InetSocketAddress que NÃO EXISTE; o construtor `new InetSocketAddress(int)`
  precisa de `HostAccess.Builder.allowPublicAccess`+create ou de um factory
  host-side). O teste ENGOLE o erro: roda o `KofJsRunner.run` em thread
  daemon com `catch (Exception ignored)` + stdout para `nullOutputStream`
  (§176b: o harness deveria anexar o stderr ao report — sem isto, qualquer
  morte do listener vira "porta não abriu" às cegas).
- **Menor repro:** compilar `main(){ var app = web.app() ; app.get("/hello"){return "x"} ; app.listen(45999) }` p/ JS e rodar `KofJsRunner.run(Default.mjs,...)` — stderr mostra o TypeError; a porta nunca binda.
- **Não fiz:** não consertei (lane JS/web; regra 6 — mexer em host-access do
  runner sem a lane-dona pode quebrar as outras fatias web/DOM), não relaxei
  o teste (Q5). Registro + pointer do repro.
- **✅ RESOLVIDO 13/09 (dono = 192.168.100.17) — o bug era NOSSO DE BUILD, não
  do runtime.** Causa raiz real (medida com `javap -c` + re-run do repro com
  classes frescas): a correção `new InetSocketAddress(kofWebPort)` JÁ ESTAVA na
  fonte desde `abbde60b` (13:45) e o host-access do runner NUNCA foi o problema
  (`allowAllAccess(true)` já expõe construtores). O `.mjs` gerado nascia quebrado
  porque `UI_WEB_RUNTIME` era `static final String = <text block>` → **expressão
  constante**: o javac **constant-fold** o valor inteiro no `JsRuntimeSlices.class`
  consumidor (instrução `ldc_w` com a string inteira, verificado em `javap -c`
  linha 63-64 do disassembly do `<clinit>`). Quando `abbde60b` mudou só
  `JsRuntimeUiWeb.java`, o **rebuild incremental NÃO recompilou `Slices`**
  (fonte inalterada → classe de 10:08 sobreviveu com o texto QUEBRADO antigo
  embutido), e o gerador lia o fold morto, nunca o `getstatic` vivo. Todo teste
  JS-web via `mvn` incremental reproduzia o TypeError para sempre.
- **Fix (duas partes):** (1) **higiene de build** — `mvn clean test` é o gate
  honesto; incremental com fold em `static final String` de recurso embutido
  mente. (2) **blindagem estrutural** — `UI_WEB_RUNTIME` agora inicializa via
  método (`= uiWebRuntime()`), o que **remove o status de constante**: `Slices`
  não pode mais inlinar; lê `getstatic` vivo. Mudanças futuras no slice
  sobrevivem a rebuilds incrementais. (3) **§176b MORTO** — `KofWebJsE2ETest`
  captura o `stderr` do `KofJsRunner` e o **anexa à mensagem de falha** da
  asserção "porta não abriu": qualquer morte do listener vira diagnóstico,
  nunca "expected true but was false" às cegas.
- **Prova:** `KofWebJsE2ETest` **1/1 verde** no HEAD com rebuild incremental
  pós-blindagem (sem `touch` manual — a blindagem é o teste de regressão de si
  mesma); repro isolado `rc=0`, sem TypeError; gate de release = suíte completa
  com `mvn clean` (ver fila). Lição cross-lane: **todo recurso `.mjs`/JS
  embutido como `static final String` em fatia está sujeito** — se alguém
  "consertar" um slice e o teste incremental continuar vermelho com o MESMO
  sintoma, suspeitar do fold ANTES de suspeitar do runtime.
### §177 — Lambda com corpo em BLOCO que retorna uma LOCAL declarada no próprio bloco é tipada VOID quando o módulo contém uma classe (SEM033) — ✅ CORRIGIDO 13/09 (raiz fechada pela lane bugs-and-gaps `192.168.100.15`; achado na caça Q4 do translator)

- **Sintoma:** o programa abaixo é Kof VÁLIDO (a local `y` existe), mas o
  `kof check`/compilação rejeita com
  `SEM033: println(...) recebeu um valor void — a chamada não retorna valor`.
  A lambda é tipada como void, então `f(3)` não pode ser usado como valor.
- **Menor repro (reproduz no binário, 13/09):**
  ```kof
  main() {
      var f = (n: Int) -> { var y = n + 1 return y * 2 }
      println(f(3))
  }
  class C {
  }
  ```
  → `SEM033`. **Removendo o `class C { }` o mesmo programa passa** (`checked 1
  file(s) — no errors`) — a presença da classe é o gatilho. O mesmo ocorre
  trocando o retorno por `return y` (só a local) e não ocorre retornando
  `n * 2` (expressão sobre o parâmetro).
- **Causa raiz (CONFIRMADA):** `ExpressionTyper.firstReturnValueType`
  (`ExpressionTyper.java`) varre o corpo da lambda p/ achar o primeiro
  `return` com valor, mas **não registra os `VarDeclStmt` do bloco** em
  `locals` — o `return y` não acha a local e infere `UNKNOWN` → a lambda vira
  `void`. Sem classe o caminho de resolução cai em outro ramo (por isso
  "funciona" sem ela).
- **Fix (13/09):** `firstReturnValueType` monta um escopo CÓPIA mutável e
  registra cada `VarDeclStmt` (tipo declarado ou inferido do initializer)
  antes de varrer os statements seguintes; o escopo do chamador não é
  poluído. Mesma unidade do §178(b) (mesma raiz).
- **Prova (Q1 — falhava antes):** o repro exato desta seção (com `class C {}`)
  agora compila e roda nos 4 targets = `8` (`JVM 8 / Native 8 / Script 8 /
  JS 8`); `CoreRegressionE2ETest.lambdaReturnLocalVar` cobre a mesma raiz.
- **Impacto:** o `kof translate` emite exatamente essa forma (lambda Java com
  corpo em bloco), então o output do translator era Kof válido que o
  compilador recusava; agora passa.
- **Arquivos:** `kof-compiler/src/main/java/dev/kof/compiler/ExpressionTyper.java`
  (`firstReturnValueType`/`returnValueType`).

### §178 — KofJS: compound em ELEMENTO de array (`a[0] += x`) → `COMP002 unexpected KofDup2`; e lambda que retorna handle `kof.ui`/`kof.media` → VerifyError no invoke — ✅ CORRIGIDO 13/09 (lane bugs-and-gaps `192.168.100.15`)

- **Contexto:** achados na caça Q4 sobre o §173/§174 (mesma sessão, probes
  `/tmp/opencode/d134/INC2.kf`/`INC3.kf`). A face (b) desta unidade é o §177
  (mesma raiz — registrado lá).
- **Face (a) — compound em elemento de array no JS:**
  - **Menor repro:** `var a = new Long[2]; a[0] = 10L; a[0] += 5L; println(a[0])`
    (idem `Int[]`, `Double[]`, `a[1] <<= 2`). JVM/Native/Script → `15`;
    **KofJS → COMPILE-FAIL** `COMP002 unexpected op in expression statement:
    KofDup2`.
  - **Causa raiz:** o lowering emite `KofDup2` (dup do par `[array,index]`,
    GitHub #64) para o compound em elemento, e o `consumeExpressionOp` do
    `JsExpressionParser` **já trata** `KofDup2` — mas o guard `isExpressionOp`
    (que roda antes) não o listava (`KofDup`/`KofDupX1`/`KofDupX2` estão lá,
    `KofDup2` ficou de fora no commit `c78109c5`). O guard abortava antes do
    handler.
  - **Fix:** adicionar `KofDup2` a `isExpressionOp`.
- **Face (c) — lambda que retorna handle `kof.ui`/`kof.media`:**
  - **Menor repro:** `val f = () -> { var l = Label("x"); return l }; println(uiNodesLive())`.
    JVM → **VerifyError** (`Bad type on operand stack`: o `invoke` da lambda
    saía descritor `LLabel;` com um `int` na pilha).
  - **Causa raiz:** handles UI/media são `int` em runtime, mas o round-trip
    `typeToString→toType` do `CompilerLambdaClass.lambdaClass` preservava o
    tipo e o `JvmLiteralEmitter.returnOpcode` emitia `ARETURN` — a assimetria
    com `JvmTypeMapper.toDescriptor` (que apaga o handle p/ `"I"`). Exposição
    nova: com o §177/(b) corrigido, lambdas de UI passaram a tipar o handle
    (antes tipavam void) e o defeito latente apareceu.
  - **Fix aditivo:** `lambdaClass` preserva o tipo real p/ UI/media (evita o
    round-trip que perde o pacote); `JvmLiteralEmitter.returnOpcode` emite
    `IRETURN` p/ handle — consistente com o descritor `"I"`.
- **Prova (Q1 — falhavam no código velho):**
  - `CoreRegressionE2ETest.compoundOnArrayElementJs` (novo, `runBoth` JVM+JS,
    golden `15\n12\ntrue\n15`).
  - `ComponentCoreE2ETest` 14/14 (regressão UI — `view` recebe lambda que
    retorna `Label`).
- **Bordas Q3:** array `Int`/`Long`/`Double`; `+=`/`<<=`; lambda sem/1/com
  parâmetro; `var` com tipo explícito e inferido; `String` local.
- **Arquivos:** `kof-compiler/src/main/java/dev/kof/compiler/js/JsExpressionParser.java`,
  `.../CompilerLambdaClass.java`, `.../jvm/JvmLiteralEmitter.java`.

### §179 — Tipo `kof.ui`/`kof.media` DECLARADO (assinatura/var/param/campo) quebra o backend JVM (VerifyError `Bad type on operand stack`) — ✅ CORRIGIDO 14/09 (DECISIONS §4 opção A, dono 192.168.100.18; catalogado 13/09 pela lane bugs-and-gaps `192.168.100.15`)

- **Sintoma:** qualquer tipo UI/media **declarado** (não inferido) vira
  `ClassType("", "Label")` no typer — NÃO o builtin `kof.ui.Label` — e o
  backend JVM emite descritor `LLabel;` para um valor que é `int` em runtime
  (`kof_ui_label_new` devolve int; `JvmTypeMapper.toDescriptor` só apaga UI
  quando o tipo É reconhecido como UI). Resultado: VerifyError.
- **Menor repro (4 contextos, todos VerifyError no JVM, 0 no Native/Script/JS):**
  ```kof
  // (1) var declarada
  main() { Label l = Label("x"); println(uiNodesLive()) }
  // (2) função que retorna
  Label make() { var l = Label("x"); return l }
  // (3) parâmetro
  void use(Label l) { println(uiNodesLive()) }
  main() { use(Label("x")) }
  // (4) campo de classe
  class Holder { Label field; public constructor(Label l) { this.field = l }
                 Label get() { return field } }
  ```
  JVM: `VerifyError: Bad type on operand stack` (ex.: `Main.make()LLabel; @7:
  areturn` com `Type integer ... not assignable to reference type`); Native
  `0`; Script `0` + warning UI002; JS `0`.
- **Causa raiz:** `MemberResolver.resolveType(name, scope)` → `Type.of(name)`
  → `ClassType("", name)`; `qualifyDeep` só atribui pacote via imports/classe
  declarada, então `"Label"` fica sem pacote e NÃO é `KofUi.LABEL`. O
  reconhecimento de UI só existe no caminho de CHAMADA (`KofUi.isConstructor`
  em `BuiltinCallTyper`/`ExpressionStaticCallLowerer`), não no de TIPO.
- **Fix proposto:** em `MemberResolver.resolveType`, após `qualifyDeep`, se o
  resultado for `ClassType("", name)` e `name` for builtin UI/media
  (`KofUi.isConstructor(name)` / `KofMedia`), mapear p/ o tipo canônico. O
  shadowing é preservado porque um `class Label`/`import ...Label` já faz o
  `qualifyDeep` atribuir pacote (não cai no `pkg.isEmpty()`).
- **✅ CORRIGIDO 14/09 (DECISIONS §4 opção A, dono 192.168.100.18):** o mapeamento
  vive no `CompilerTypes.qualifyDeep` passo 2b (após `simpleNamePackage`
  devolver null e nem o módulo nem a `SymbolTable` declararem o nome) via
  `builtinDeclaredType` (`KofUi.typeByName` + `KofMedia.IMAGE_DATA`), com o
  guard de shadowing `unitDeclaresType` (um `class Label` do usuário ainda
  vence); o `VarDeclStmt` do `StatementLowerer` resolve pelo analisador
  semântico. Prova: `ComponentCoreE2ETest.declaredUiAndMediaTypesCompileAndRun`
  + `userClassShadowsBuiltinUiTypeName` (JVM+Native+JS) — re-verificado verde
  15/09 pela lane bugs-and-gaps.
- **Conexão:** o §178(c) (lambda) tem a mesma família de raiz, mas o tipo do
  lambda é INFERIDO (`kof.ui.Label` via `MethodCallTyper`), por isso foi
  corrigível de forma aditiva (`CompilerLambdaClass` preserva o handle +
  `JvmLiteralEmitter.returnOpcode` emite `IRETURN`) sem tocar a resolução de
  nomes.

### §180 — `println(double/float)` no Native x86_64 NÃO é JDK `Double.toString`/`Float.toString`: `%.16g` trunca o shortest-round-trip (`0.1+0.2` → `0.3`), a notação científica diverge (`1e7` → `10000000.0`) e o `Float` imprime a expansão double (`1.0f/3.0f` → `0.3333333432674408`) — ✅ CORRIGIDO 15/09 x86_64 (residual/overclaim do bug 44; catalogado pela lane bugs-and-gaps `192.168.100.15`, executado pela lane development `192.168.100.18`; DECISIONS §6)

> **✅ CORRIGIDO 15/09 (dono 192.168.100.18, x86_64):** nova fatia de runtime
> `RuntimeDtoa` (`kof_dtoa_format`, `kof_double_to_string`,
> `kof_float_to_string`) implementa o contrato do JDK via o loop limitado
> `%.*e`+`strtod`: para precisão `0..16` (double) / `0..8` (float) formata com
> `snprintf("%.*e")` e re-parseia com `strtod`, guardando a menor precisão que
> faz round-trip **bit-exato**; depois reformata no estilo Java (científica só
> quando `|x|>=1e7` ou `<1e-3`, `E` maiúsculo, mantissa sempre com parte
> fracionária) e `Float` mantém a **própria** forma mais curta (não a expansão
> double). `RuntimePrintNum` (o `print` sem box), `RuntimeJsonEncode`
> (NaN/±Inf → `null`) e os ramos `.Lce_double`/`.Lce_float` do
> `RuntimeCollectionToString` delegam a ela; o `RuntimeStringConv` não carrega
> mais a conversão de float/double (só int/char/long/bool). As quatro faces
> (a)–(d) fechadas: `0.1+0.2` → `0.30000000000000004`, `1e7` → `1.0E7`,
> `1e-5` → `1.0E-5`, `1e-3` → `0.001`, `1e-4` → `1.0E-4`, `1.0f/3.0f` →
> `0.33333334`, `1.0e20f` → `1.0E20`, `3.4028235e38f` → `3.4028235E38`,
> `math.pow(-1.0,0.5)` → `NaN` (a face `-nan`), `-0.0` → `-0.0`. Prova:
> `ConformanceMatrixTest.doubleprint` com a exclusão do Native **removida**
> (Native == JVM byte a byte) + `KofMathTest` 29/29, `JsonE2ETest` 18/18,
> `JsonCompleteE2ETest` 9/9, `NativeRuntimeSliceRegistryTest` 7/7.
> **Cross fechado 15/09** (`RtB45`): a MESMA `RuntimeDtoa` portada ao
> riscv64/aarch64, consumindo libc `snprintf`/`strtod` pelo link dinâmico sob
> demanda (`NativeCrossLink`) — o `FLT001` acabou (`NativeRiscvDtoaTest` oracle
> JVM nos 2 arches). **Duas restrições:** (1) `snprintf`/`strtod` exigem libc →
> o binário cross liga dinamicamente só quando imprime FP; (2) o `_start` do
> runtime Kof **não** garante alinhamento de pilha de 16 bytes, então cada
> entry point do dtoa alinha a pilha antes das chamadas libc (o `movaps` da
> glibc precisa de 16B — o desalinhamento era a causa-raiz do SIGSEGV no x86;
> no cross os entry points fazem `andi sp,sp,-16`).

- **Sintoma (medido 13/09, x86_64, 4 targets):** o bug 44 foi fechado 10/09
  com a nota "16 casas + `.0` casa com o JVM", mas o `%.16g` do glibc **não**
  implementa o contrato `Double.toString` do JDK. Duas faces:
  - **(a) shortest-round-trip truncado:** `println(0.1 + 0.2)` → JVM/Script/JS
    `0.30000000000000004`; **Native x86 `0.3`** (o `%.16g` arredonda p/ 16
    dígitos significativos e perde os dígitos que distinguem o double).
    `println(100.0 / 3.0)` → JVM `33.333333333333336`, Native
    `33.33333333333334` (últimos dígitos errados). `0.1 * 3.0` idem.
  - **(b) limiar/formato científico:** JDK usa notação científica quando
    `|x| >= 1e7` ou `< 1e-3` e escreve `1.0E7`/`1.0E-5`; o glibc `%.16g`
    escreve decimal até 1e15 e `1e-05`/`1e-10` (minúsculo, expoente com zero).
    `println(1e7)` → JVM/Script `1.0E7`, Native `10000000.0`;
    `println(1e-4)` → JVM `1.0E-4`, Native `0.0001`; `1e-5` → `1.0E-5` vs
    `1e-05`; `1.23456789E8` vs `123456789.0`.
  - **(c) `Float` imprime a expansão double, não `Float.toString`:** o bug 44
    dizia "float imprime como double (`cvtss2sd` antes de formatar) — paridade
    JVM", mas o JVM usa `Float.toString` (shortest repr do float, 7-8 dígitos):
    `println(1.0f / 3.0f)` → JVM/Script/JS `0.33333334`, **Native
    `0.3333333432674408`** (o double exato de `(double)(1.0f/3.0f)`).
    `println(1.0e20f)` → `1.0E20` vs `1.000000020040877e+20`;
    `println(3.4028235e38f)` → `3.4028235E38` vs `3.402823466385289e+38`;
    `1.1754944e-38f` → `1.1754944E-38` vs `1.175494350822288e-38`.
  - **(d) `-nan` (NaN negativo) NÃO é normalizado:** o fix residual do bug 44
    (11/09) normaliza os 3 spellings do glibc `inf`/`-inf`/`nan`, mas o ramo
    de 4 chars (`sp4`) só casa `-inf` (checa `'-'` e depois `'i'`); para
    `-nan` (byte 1 = `'n'`) cai no `fin` e deixa `-nan` passar. Medido com
    `math.pow(-1.0, 0.5)` (libm devolve NaN negativo): JVM/Script/JS `NaN`,
    **Native `-nan`** — em **todos** os caminhos (`println`, `print`,
    `String.valueOf`, concat `+`). `0.0/0.0` (NaN positivo) já sai `NaN`
    correto. `RuntimeStringConv.emitDoubleToString`, ramo `.Lkof_dbl_str_sp4`
    (~l.465) — falta o caso `-nan`→`NaN`.
- **Menor repro:**
  ```kof
  main() {
      println(0.1 + 0.2)   // JVM 0.30000000000000004 | Native x86 0.3
      println(1e7)         // JVM 1.0E7               | Native x86 10000000.0
      println(1e-5)        // JVM 1.0E-5              | Native x86 1e-05
      println(1.0f / 3.0f) // JVM 0.33333334          | Native x86 0.3333333432674408
  }
  ```
  JVM/Script/JS concordam entre si; **só o Native x86 diverge** (regra 5).
- **Causa raiz:** `RuntimeStringConv.emitDoubleToString` (e o `print` sem box
  em `RuntimePrintNum`) formatam com `snprintf("%.16g")`. `%g` **não** é o
  algoritmo shortest-round-trip do JDK: usa no máximo 16 dígitos significativos
  (perde precisão) e tem o próprio limiar de expoente/spelling. O fix do 44 só
  pós-processou o `.0` e o spelling de `inf`/`nan`; a precisão e a notação
  científica ficaram.
- **Impacto:** `println`/`print`/`String.valueOf`/concat `+` de `Double` no
  Native x86 devolvem valor/forma diferentes do JVM — divergência **silenciosa**
  (sem diagnóstico), exatamente o que a regra 5 proíbe. A célula `floatprint`
  da matriz testa só `1.0/3.0`, `2.5*2.0`, `7.0/2.0` — os três **coincidem**
  por acaso, dando **verde falso** ao bug 44 (Q5).
- **Fix proposto (unidade GRANDE — lane Native):** implementar o
  shortest-round-trip (algoritmo tipo Ryu/Grisu ou o loop `%.{1..17}g` + `strtod`
  de verificação de round-trip) + normalizar a notação científica ao formato
  JDK (`E`, sem `+`, sem zero à esquerda no expoente, mantissa com `.0`).
  Toca `RuntimeStringConv` + `RuntimePrintNum` + o espelho cross (riscv/aarch,
  família FLT001). **✅ IMPLEMENTADO 15/09 x86_64 E cross** via a opção
  `%.*e`+`strtod` acima (ver a nota de fix no topo): `RuntimeDtoa` carrega
  `kof_dtoa_format`/`kof_double_to_string`/`kof_float_to_string`; o runtime
  cross porta a MESMA máquina como fatia `RtB45` (libc `snprintf`/`strtod` via
  link dinâmico sob demanda), então o `FLT001` do riscv/aarch está **fechado**.
- **Overclaim corrigido:** o cabeçalho do bug 44 e a linha `floatprint` da
  matriz diziam "DONE"; passam a apontar este residual (agora fechado no x86_64).


### §181 — `Double/Float as Int` e `as Long` FORA DE FAIXA / `NaN` / `Infinity`: JVM+Script saturam (JLS 5.1.3), Native usa `cvttsd2si` cru (`INT_MIN`) e JS usa `Math.trunc`/`BigInt` sem 32-bit (dá `3000000000`/`NaN`/`Infinity`, e `NaN as Long` lança `RangeError`) — ✅ CORRIGIDO 13/09 (catalogado pela lane bugs-and-gaps `192.168.100.15`; implementado em `c90e85ee` — JS `kofD2I/kofD2L/…` + Native `emitSatConv` + riscv/aarch — e **regressão do fix x86 corrigida + verificada** pela mesma lane `192.168.100.15`)

> **Residual medido 14/09 (run limpo da suíte de release, dono =
> 192.168.100.17, só catalogado — lane nat):** `riscv64CastSaturation` e
> `aarch64CastSaturation` continuam vermelhos — ÚNICA linha divergente é o
> `(-inf) as Int`: devolve `0`, o contrato §181/JLS 5.1.3 exige
> `-2147483648` (NaN→0 está correto, +overflow→MAX está correto; só o
> -inf/saturação-negativa ficou fora do `67db6c50`). Fix na lane nat.

- **Sintoma (medido 13/09, 4 targets):** o contrato documentado é o do JVM
  (`learn/04-variables-and-types.md:150` "Double → Int", `training/language/types.md:70`
  "trunca … como Java"): a conversão `double→int`/`double→long` do JLS 5.1.3
  **satura** (NaN→`0`; `> MAX`→`MAX`; `< MIN`→`MIN`; senão trunca p/ zero) e o
  resultado é sempre um `Int`/`Long` de 32/64 bits válido. Nenhum target além
  do JVM/Script cumpre:
  - **(a) Native x86_64 usa `cvttsd2si` cru:** fora de faixa e NaN devolvem o
    "integer indefinite" `0x80000000` (`INT_MIN`) em vez de saturar. `3.0e9 as Int`
    → JVM/Script `2147483647`, **Native `-2147483648`**; `NaN as Int` → `0` vs
    **`-2147483648`**; `Infinity as Int` → `MAX` vs **`INT_MIN`**.
    `1.0e19 as Long` → JVM `9223372036854775807`, **Native
    `-9223372036854775808`**; `NaN as Long` → `0` vs **`Long.MIN`**.
  - **(b) JS `D2I/F2I` é só `Math.trunc`** (`JsCallEmitter.unaryExpr:398`), sem
    wrap/32-bit nem saturação: `3.0e9 as Int` → **`3000000000`** (não é um Int
    válido); `1.0e300 as Int` → **`1e+300`**; `NaN as Int` → **`NaN`**;
    `Infinity as Int` → **`Infinity`**. Isso é **incoerente com a própria
    aritmética Int do JS**, que **já** faz wrap de 32 bits (`2147483647 + 1` →
    `-2147483648` nos 4 targets).
  - **(c) JS `D2L/F2L` é `BigInt(Math.trunc(x))`** sem saturação: `1.0e19 as Long`
    → **`10000000000000000000`** (BigInt > `Long.MAX`); `9.3e18 as Long` →
    **`9300000000000000000`**; `-9.3e18 as Long` → **`-9300000000000000000`**;
    e **`NaN as Long` LANÇA `RangeError: BigInt out of range`** (crash de
    runtime, não valor errado).
  - **(d) a stdlib §89 `n.toInt()`/`n.toLong()` é alias do cast** (mesmo
    lowering `D2I/F2I/D2L/F2L`) e sofre o mesmo desvio: `3.0e9.toInt()` →
    JVM/Script `2147483647`, Native `-2147483648`, JS `3000000000`;
    `1.0e19.toLong()` → `9223372036854775807` vs `Long.MIN` vs
    `10000000000000000000`; `(0.0/0.0).toLong()` → `0` vs `Long.MIN` vs
    **`RangeError`**. A célula `numconv` (§89) só testa `3.7.toInt()`/
    `(-2.5).toInt()` **em faixa** = também verde falso (Q5).
- **Menor repro:**
  ```kof
  main() {
      println(3.0e9 as Int)      // JVM/Script 2147483647 | Native -2147483648 | JS 3000000000
      println((0.0/0.0) as Int)  // JVM/Script 0          | Native -2147483648 | JS NaN
      println(1.0e19 as Long)    // JVM 9223372036854775807 | Native Long.MIN   | JS 10000000000000000000
      println((0.0/0.0) as Long) // JVM/Script 0          | Native Long.MIN     | JS RangeError (crash)
  }
  ```
  **JVM e Script concordam entre si** (oracle JLS 5.1.3); **Native e JS
  divergem** (regra 5) — divergência **silenciosa** no Native, e **crash** no
  JS na face `as Long` de NaN.
- **Causa raiz:**
  - Native: o lowering de `D2I/F2I/D2L/F2L` emite a instrução SSE de conversão
    direta (`cvttsd2si`) sem o guard de faixa/NaN que o JLS exige (o JVM insere
    a saturação no `d2i`/`d2l`). Espelha o bug 120 (direção invertida do
    `fcvt` no aarch), mas aqui é o **comportamento de borda**, não a instrução.
  - JS: `JsCallEmitter.unaryExpr` (`case D2I, F2I -> Math.trunc(operand)`;
    `case D2L, F2L -> BigInt(Math.trunc(operand))`) — falta o
    truncamento/saturação 32/64-bit. `BigInt(NaN)` lança `RangeError`.
- **Impacto:** qualquer Kof que faça cast de `Double`/`Float` fora de faixa
  (dado de entrada, `parseDouble`, cálculo que estoura) produz valores
  diferentes por target — ou **derruba** o programa no JS. A célula `cast` da
  matriz só testa `9.9 as Int`/`70000L as Int`/`66 as Char` (todos **em
  faixa**), dando **verde falso** ao problema (mesmo padrão do §180/Q5).
- **Fix proposto:**
  - **JS (lane JS, menor):** trocar `Math.trunc` por uma conversão saturante
    (`isNaN(x) ? 0 : x >= 2147483647 ? 2147483647 : x <= -2147483648 ?
    -2147483648 : Math.trunc(x)` p/ `Int`; análogo p/ `Long` com
    `BigInt.asIntN(64, …)` sobre o valor já saturado, e guard de NaN antes do
    `BigInt`). Pode virar helper de runtime (ex.: `kofD2I`/`kofD2L`).
    **⚠️ NÃO emitir a árvore condicional INLINE sobre o `operand` cru:** o
    IR do JS é uma expressão e `operand` pode ter side-effect
    (`f() as Int`) — repeti-lo nas comparações o avaliaria 2-4×, divergindo
    do JVM/Script (avalia 1×). A correção exige avaliar UMA vez: helper de
    runtime (`kofD2I(v)`) ou IIFE. Foi por isso que esta unidade não foi
    feita nesta sessão (o helper toca o runtime JS de outra lane).
  - **Native x86 (lane Native, espelho riscv/aarch):** após `cvttsd2si`,
    detectar o "integer indefinite" (`0x80000000`/`0x8000…`) **ou** comparar
    com a faixa via `ucomisd` e saturar (MAX/MIN) — NaN→`0`. Toca
    `RuntimeNumeric`/emissão de cast dos 3 nativos + o tradutor aarch/riscv
    (mesma família FLT001/MATH001).
- **Provas a adicionar:** célula de matriz `castrange` (4 targets) com os
  valores saturados do JVM + testes cross dos nativos quando qemu presente.
  **Não corrigido nesta sessão** (Native = lane Native; JS = lane JS; a unidade
  segura aqui é catalogar + travar o golden).
- **✅ CORREÇÃO (13/09, `c90e85ee` + fix da regressão pela lane bugs-and-gaps
  `192.168.100.15`):** saturação JLS 5.1.3 implementada nos 4 targets —
  JS via helpers de runtime `kofD2I`/`kofD2L`/`kofF2I`/`kofF2L` (avaliação
  única, sem repetir o `operand`), Native x86 via `NativeX86Arith.emitSatConv`,
  riscv/aarch via `NativeRiscvCrossEmit` (aarch herda no tradutor).
  - **⚠️ REGRESSÃO do `c90e85ee` (achada na caça Q4 desta lane, 13/09):** o
    `emitSatConv` x86 carregava os limites com os **bits INTEIROS**
    (`movq $2147483647, %rdx; movq %rdx, %xmm2`) — interpretados como double
    isso é um **denormal (~1e-314)**, então QUALQUER valor positivo caía no
    ramo `>= MAX` e saturava (`9.9 as Int` → `2147483647`). A célula `cast`
    (em faixa) pegou o bug — o `castrange` **não**, porque só testava
    fora-de-faixa (**verde falso Q5**). Fix: padrões de bit do double
    (`2^31 = 0x41E0000000000000`, `-2^31 = 0xC1E0000000000000`,
    `2^63 = 0x43E0000000000000`, `-2^63 = 0xC3E0000000000000`), comparando
    com `2^31`/`2^63` (não `MAX`, p/ preservar o limítrofe `2147483647.0`), e
    **promovendo Float a Double ANTES** da checagem de NaN (o `movd` cru
    deixava a checagem errada).
  - **⚠️ regressão irmã no riscv/aarch (mesma caça):** `emitCrossUnaryRiscv`
    emitia labels **FIXOS** (`.Lsat181_nan`/`_hi`/`_lo`/`_end`) → dois casts no
    MESMO método geravam **símbolo duplicado** e o GNU as falhava; e o `F2L`
    usava `fcvt.l.s`/`feq.s` sobre valor já promovido a double. Fix: sufixo
    único por emissão (`_<seq>`, como no x86) + `feq.d`/`fcvt.l.d` após a
    promoção.
  - **Prova Q1 (falhava antes, passa agora):** célula `castrange` SEM exclusões
    (4 targets) + célula `cast` (a que pegou a regressão) + novos
    `NativeRiscv64E2ETest.riscv64CastSaturation`/
    `NativeAarch64E2ETest.aarch64CastSaturation` (qemu) +
    `…CastSaturationLabelsAreUniquePerEmission` (inspeção do `.s`, roda sem
    toolchain). Golden = oracle JVM (medição real).


### §182 — `time.parseDateIso`/`addDays`/`diffDays`: parse de campo com SINAL (`"+999-01-01"`, `"2026-+1-01"`) é aceito no JVM/Script e rejeitado no Native/JS — ✅ CORRIGIDO 13/09 (catalogado pela lane bugs-and-gaps `192.168.100.15`; achado na caça Q4 do S7g; fix = lane development `.18`, `a13665f7`)

- **Sintoma (medido 13/09, 4 targets):** o parser ISO do `kof.time` usa
  `Integer.parseInt` no JVM e `parseInt` no JS, que **aceitam sinal `+`/`-`**
  num campo de 2-4 dígitos (`parseInt("+999")` = 999; `parseInt("-99")` =
  -99). O Native (asm x86) e o `kofTimeParseDateIso` do JS fazem checagem
  estrita de dígito a dígito. Resultado:
  - `time.parseDateIso("+999-01-01")` → JVM/Script `-354650`, Native/JS `0`.
  - `time.parseDateIso("2026-+1-01")` → JVM/Script `20454`, Native/JS `0`.
  - `time.parseDateIso("2026-01-+1")` → JVM/Script `20454`, Native/JS `0`.
  - `time.addDays("+999-01-01", 1)` → JVM/Script `0999-01-02`,
    Native `""` (JS acerta: `kofTimeParseIso` do addDays é **leniente** e
    aceita — ver abaixo).
  - `time.diffDays("+999-01-01", "1000-01-01")` → JVM/Script `365`,
    Native/JS `0`.
- **Menor repro:**
  ```kof
  main() {
      println(time.parseDateIso("+999-01-01"))   // JVM/Script -354650 | Native/JS 0
      println(time.parseDateIso("2026-+1-01"))   // JVM/Script 20454   | Native/JS 0
  }
  ```
- **Causa raiz (DUAS, direções opostas):**
  - **JVM/Script são LENIENTES demais:** `JvmTimeRuntime.kof_time_parseIso`
    valida `length==10` + hífens 4/7 mas usa `Integer.parseInt` nos 3 campos,
    que aceita `+999`/`+1`/`-9`. O contrato declarado é **"YYYY-MM-DD
    estrito … dígitos"** (comentário do próprio código + `KofTimeE2ETest`
    chama "estrito") → o aceite do sinal é desvio do contrato, não o contrário.
  - **JS é INCONSISTENTE entre suas próprias funções:**
    `kofTimeParseDateIso` (l.354) checa cada byte e rejeita sinal (correto),
    mas `kofTimeParseIso` (l.423, usado por `addDays`/`diffDays`) só valida
    `length==10`/hífens + `parseInt` (aceita `+/-`), espelhando o JVM. Por
    isso `addDays`/`diffDays` no JS **concordam com o JVM** e o
    `parseDateIso` do JS **não**.
  - **Native é o único estritamente correto** (`kof_time_parseDateIso` asm
    valida dígito a dígito), mas diverge do JVM/Script por regra 5.
- **Impacto:** a MESMA string é data válida num target e inválida noutro,
  **silenciosamente** (retorno `0`/`""` da política "invalid => 0"), em API
  standard recém-entregue (S7a/S7g). A célula `stdtime*` da matriz não cobre
  campo com sinal = cobertura estreita (Q5).
- **Fix proposto (decisão de correção — lane .18):** o consenso declarado é
  o parse **ESTRITO** (é o que o corpus/testes dizem e o que o Native faz).
  - JVM: trocar `Integer.parseInt` por checagem de dígito a dígito (ou
    rejeitar sinal explicitamente) em `kof_time_parseIso`.
  - JS: fazer `kofTimeParseIso` (l.423) usar a mesma validação estrita do
    `kofTimeParseDateIso` (l.354) — reusar um único helper.
  - Native: já estrito; **é a referência** — só re-verificar com o golden.
  - Alternativa (se a mantenedora preferir leniência): alinhar TODOS ao
    `Integer.parseInt` — mas isso muda o contrato declarado ("estrito") e o
    teste existente, então **exige decisão da mantenedora** (regra 6).
- **Provas a adicionar:** célula `parseisostrict` (4 targets) com os 3 campos
  com sinal + o caso válido `2026-01-01`, golden do consenso estrito.
  **Não corrigido nesta sessão** (o fix toca a unidade recém-entregue por
  outra lane — `.18`; aqui a unidade segura é catalogar + travar o golden).
- **✅ CORREÇÃO (13/09, lane .18 — mesma sessão da unidade S7e-S7h):** consenso
  ESTRITO adotado (é o que o contrato declarado e o Native já faziam — a
  alternativa leniente mudaria contrato = regra 6, desnecessário).
  - JVM: `JvmTimeRuntime.kof_time_parseIso` troca `Integer.parseInt` por
    `kof_time_digits` (dígito a dígito, rejeita `+`/`-`).
  - JS: `kofTimeParseIso` (addDays/diffDays) troca `parseInt` por
    `kofTimeDigits` — MESMO helper/contrato do `kofTimeParseDateIso`;
    inconsistência interna do JS eliminada.
  - Native: já estrito (referência) — zero mudança.
  - **Prova Q1 (falhava antes, passa agora):** célula `parseisostrict`
    SEM exclusões (4 targets, antes jvm/script/js excluídos) — golden do
    consenso estrito `0/0/0/20454//0`; suíte 1772/0/0.

### §183 — `KofTimeE2ETest.todayIsoFormatDateIsoIsToday{Jvm,Js,Native}` era FLAKY: assertava `isToday(2026,9,13)==true` LITERAL, quebrando à meia-noite UTC — ✅ CORRIGIDO 13/09 (achado pela lane bugs-and-gaps `192.168.100.15` na caça Q5; fix da lane development `.18`, `a13665f7`)

- **Sintoma:** o teste da S7e fixava o dia corrente no golden
  (`isToday(2026,9,13) == true`) — passava no dia da escrita e virava
  vermelho quando o relógio UTC cruzava a meia-noite (13→14/09). Não era
  falha de código, era **verde-falso de relógio** (Q5): o teste media o
  ambiente, não o contrato.
- **Causa raiz:** golden dependente do relógio em teste de `time.*`.
- **✅ Fix (`a13665f7`, lane `.18`):** em vez de literal, o teste extrai as
  partes da própria `time.todayIso()` (`substring` + `math.parseInt`) e
  asserta `isToday(p0,p1,p2)==true` — verdadeiro em QUALQUER dia. As datas
  fixas restantes (`isToday(2026,9,12)`, `isToday(2026,2,30)`,
  `isToday(0,1,1)`) são **passado/inválidas** → sempre `false`, estáveis.
- **Prova:** `KofTimeE2ETest` 18/18 nos 3 backends de host + cross-arch (o
  caso literal só passa no dia; o novo passa em qualquer dia).
- **Lição (Q5):** golden de `time.*`/`random.*`/host NUNCA é literal de
  relógio/ambiente — deriva do resultado do próprio runtime (paridade) ou de
  entrada controlada.

### §184 — `new Byte[n]`/`new Short[n]`: store de valor fora da faixa NÃO estreita no JS (JVM/Native/Script estreitam) — divergência cross-target SILENCIOSA — ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`; face JS — `kofArraySet` agora estreita byte→i2b/short→i2s/char→i2c)

- **Sintoma (medido 13/09, 4 targets):** arrays de tipo estreito não fazem o
  *narrowing* do valor na escrita no backend JS (o array vira `Array` JS
  comum e `kofArraySet` grava o valor cru). Os outros 3 targets estreitam
  (byte → 8 bits com sinal; short → 16 bits com sinal):
  ```kof
  main() {
      var b = new Byte[1]
      b[0] = 130
      println(b[0])       // JVM/Native/Script -126 | JS 130
      var s = new Short[1]
      s[0] = 70000
      println(s[0])       // JVM/Native/Script 4464 | JS 70000
  }
  ```
  Medição real: JVM/Native/Script `-126|4464`; JS `130|70000`. **Nenhum
  diagnóstico** — divergência silenciosa (regra 5).
- **Menor repro:** o bloco acima.
- **Causa raiz:** o JS não conhece o tipo do ELEMENTO no `KofArrayStore`:
  `JsExpressionStatementParser` (l.65-80) baixa `KofArrayStore` para
  `kofArraySet(array, index, value)` (`JsRuntimeCore.java:226`), que só faz
  bounds-check e `array[index] = value` — sem máscara 8/16 bits. `new Byte[n]`
  aloca `Array` JS puro (sem metadado de tipo). JVM usa `BASTORE`/`SASTORE`
  (que estreitam por spec JVMS §6.5); Native idem; o interpretador estreita no
  lowering do valor (`KofInterpreterOps.newArray` mapeia Byte/Short p/ `int[]`,
  mas o valor já chega estreitado).
- **Impacto:** qualquer Kof que grave em `Byte[]`/`Short[]` um valor fora de
  faixa (dado de entrada, aritmética) produz valor diferente no JS; mesma
  família do #132 (`10fd1b32`, opcodes JVM) e da KOF-SBD-001 (bounds) — o
  **tipo do elemento** não foi coberto no JS.
- **Fix proposto (lane JS):** ou (a) `kofArraySet`/`kofArrayGet` recebem o
  tipo do elemento (lowering passa uma tag `"b"`/`"s"`/`"c"`), aplicando a
  máscara no store/load; ou (b) `new Byte[n]`/`new Short[n]` viram um wrapper
  JS com `set` que estreita. (a) é menor e espelha o `kofD2I` da §181.
- **Provas a adicionar:** célula de matriz `narrowarr` (4 targets) com os
  valores fora de faixa + um teste cross; hoje só o JVM tem cobertura
  (`JvmE2ETest.execNarrowPrimitiveArrayAccess`, do #132).
- **Face Char (13/09):** o JS também não estreita `Char[]` (`c[0]=70000` →
  `70000`; `c[0]=-1` → `-1`, vs JVM/Script `4464`/`65535`) — mesma raiz.
  No Native o `char` também diverge, mas por causa própria (tamanho 4 em
  `elementTypeSize`) — ver **§187**.

### §185 — Script/interpretador: escrita em elemento de `Char[]` e `Bool[]` LANÇA `argument type mismatch` (crash) — JVM/Native/JS corretos — ✅ CORRIGIDO 15/09 (lane development `192.168.100.18`; fix de causa raiz em `KofInterpreterValues.coerceFor` + `KofInterpreterParityTest.charBoolArrayStore`)

- **Sintoma (medido 13/09, 4 targets):** o interpretador (target Script)
  **aborta** ao gravar num `Char[]` **ou num `Bool[]`**:
  ```kof
  main() {
      var c = new Char[2]
      c[0] = 'A'        // Script: exit 1, stderr "argument type mismatch"
      println(c[0])
      var b = new Bool[2]
      b[0] = true       // Script: idem
      println(b[0])
  }
  ```
  JVM/Native/JS imprimem `65`/`true`; Script `exit=1`, `stderr="argument type
  mismatch"`, sem output. A **leitura** de `Char[]`/`Bool[]` recém-alocado
  funciona (`0`/`false`); o crash é só no **store**. `Int[]`/`Long[]`/
  `Double[]`/`Float[]`/`Byte[]`/`Short[]`/`String[]` funcionam (medido).
- **Menor repro:** o bloco acima (ou `c[0] = 65 as Char`, mesmo crash).
- **Causa raiz (corrigida 13/09 — o 1º registro apontava `KofInterpreterOps`
  errado):** o caminho **vivo** do store é `KofInterpreter.java:306`
  (`Array.set(arr, idx, builtins.coerceFor(as.elementType(), v))`).
  `KofInterpreterValues.coerceFor` (l.118-129) **não coage `char` nem `bool`**:
  para ambos o valor Kof é um `Integer` e cai no `default`/`n.intValue()` do
  ramo `"int","char","bool","byte","short"` → continua `Integer`;
  `java.lang.reflect.Array.set(char[], idx, Integer)` /
  `Array.set(boolean[], idx, Integer)` lançam
  `IllegalArgumentException: argument type mismatch`. (`newArray`
  l.193-202 aloca `char[]`/`boolean[]` corretos.) **`KofInterpreterOps.
  arrayStore` (l.244-251) — que o 1º registro citava — é CÓDIGO MORTO**:
  nenhum caller fora de `KofInterpreterBuiltins` (que ninguém invoca). Não é
  ali que se conserta.
- **Impacto:** qualquer Kof com array de `Char`/`Bool` e atribuição de
  elemento roda nos 3 backends compilados e **derruba** o interpretador.
  Também quebra a paridade interpretador × JVM (gate do
  `KofScriptTest`/`KofInterpreterParityTest`).
- **Fix proposto (lane interp):** em `KofInterpreterValues.coerceFor`, nos
  ramos `"char"` e `"bool"`, converter o valor: `char` → se `String` de 1
  char → `charAt(0)`, se `Character` → ele mesmo, se `Number` →
  `(char) intValue()`; `bool` → `Boolean` ou `Number != 0`. (O
  `KofInterpreterOps.arrayStore` morto pode ser removido ou alinhado — não é
  o fix.) Alternativa mínima: tratar no ponto do `Array.set` (l.306).
- **Provas a adicionar:** `KofInterpreterParityTest.charArrayStore` +
  `boolArrayStore` (novos) + célula de matriz `chararr` (4 targets; a célula
  já cobre `Char[]` e `Bool[]`, hoje PARTIAL script).
- **Nota Q7 (código morto):** `KofInterpreterOps.arrayStore`/`arrayLoad` e o
  `KofInterpreterBuiltins` que os expõe são fachada não-invocada — catalogar
  a remoção junto do fix (não é stub de feature, é resto de refactor).
- **✅ CORRIGIDO 15/09 (lane development `192.168.100.18`):** exatamente o fix
  proposto — `KofInterpreterValues.coerceFor` agora produz o tipo REAL do slot
  nos ramos `"char"`/`"bool"` (`Character`: ele mesmo ou `(char) n.intValue()`;
  `Boolean`: ele mesmo ou `n.intValue() != 0`; byte/short permanecem `int` — os
  slots `int[]` os aceitam); repro re-medido no tip ANTES do fix
  (`argument type mismatch`, exit 1) e `65`/`true` depois. **Prova (Q1, mesma
  unidade):** `KofInterpreterParityTest.charBoolArrayStore` — interpretador × JVM
  byte-idêntico em store de literal char, número-para-Char, literal Bool e false;
  classe 25→26/26; vizinhos `ScriptTargetTest` 7/7, `EnumIdentityE2ETest` 6/6,
  `CoreRegressionE2ETest` 102/102. **Residual (Q4): a fachada morta
  `KofInterpreterOps.arrayStore`/`arrayLoad` + exposição em `KofInterpreterBuiltins`
  permanece — remoção catalogada como refactor (fora do escopo deste bug); a face
  JS de arrays de elemento estreito é §184 (registro separado).**

### §186 — JVM/KofJS: inicializador de campo `static` com expressão não-constante é descartado silenciosamente (nenhum `<clinit>` é sintetizado) — ✅ CORRIGIDO 14/09, [issue #133](https://github.com/KofLang/Kof4j/issues/133) (colaborador Jonas Rocha, varredura KOF-SBD-001-STRESS; portado do `docs/development/known-bugs.md` do PR #130)

- **Sintoma:** `static Int[] shared = new Int[3]` — `Holder.shared` é `null`
  no JVM e `undefined` no KofJS; `static Int x = compute()` imprime `0`.
  Literal constante (`static Int calls = 0`) funciona — só expressões
  não-constantes são descartadas. Silencioso: compila, roda, zero diagnóstico
  (viola R6).
- **Borda medida (Q4, lane bugs-and-gaps `192.168.100.15`, 13/09 — 4 targets):**
  o corte NÃO é "constante × não-constante" — é **literal direto × qualquer
  expressão**. Mesmo o **dobrável** cai: `static Int fold = 40 + 2` → JVM/Native/
  Script `0`, JS `undefined`; `static Int foldLit = 3 * 4` idem; e
  `static String scat = "a" + "b"` → `null`/`undefined` (o JVM tem `ConstantValue`
  p/ String concat de literais, mas o lowering não dobra). `static Int lit = 7`,
  `static Bool bt = true`, `static Double dlit = 1.5` funcionam (literais).
  ```kof
  class H {
      static Int lit = 7           // 7 nos 4
      static Int fold = 40 + 2     // JVM/Nat/Scr 0  | JS undefined
      static String scat = "a" + "b"  // null      | JS undefined
      static Int[] shared = new Int[3] // null     | JS undefined
      static Int computed = compute()  // 0        | JS undefined
      static Int compute() { return 41 }
  }
  main() { println(H.lit); println(H.fold); println(H.scat); println(H.computed) }
  ```
  `lowerField` só converte `LiteralExpr` **direto** em `initialValue`; binário/
  `new`/call — inclusive dobrados — ficam de fora.
- **Correção de escopo (13/09):** o registro original dizia "só o
  interpretador simula `<clinit>`". **Medido: o interpretador (Script) TAMBÉM
  devolve `0`/`null`** no mesmo programa — `KofInterpreterMembers.ensureInit`
  só executa um `<clinit>` que o front-end **não sintetiza** para esses
  inicializadores, e o seed de `initialValue` (l.58-63) só pega literais. Ou
  seja: os **4 targets** divergem do previsto; o `<clinit>` a sintetizar é a
  peça única que conserta JVM/Native/Script/JS.
- **Medição cross-target (lane bugs-and-gaps `192.168.100.15`, 13/09 — 4
  targets):**
  ```kof
  class H {
      static Int lit = 7          // 7 nos 4 (literal)
      static Int fold = 40 + 2    // JVM/Nat/Scr 0  | JS undefined
      static Int foldLit = 3 * 4  // 0             | undefined
      static String scat = "a" + "b"  // null      | undefined
      static Bool bt = true       // true nos 4
      static Double dlit = 1.5    // 1.5 nos 4
      static Int[] shared = new Int[3]  // null    | undefined
      static Int computed = compute()   // 0        | undefined
  }
  ```
  **Nuance nova:** até expressões **constant-foldable** (`40 + 2`, `3 * 4`,
  `"a" + "b"`) são descartadas — não só chamadas de função/`new`. O
  `lowerField` só converte `LiteralExpr` **direto** em `initialValue`;
  binário/`new`/call ficam de fora mesmo quando dobráveis. `static Int[] x`
  (`new`) idem.
- **Causa raiz:** `visitField(..., field.initialValue())` só transporta o
  atributo `ConstantValue` (primitivos/String constantes em tempo de
  compilação). Qualquer inicializador não-constante exige um método `<clinit>`
  — que NENHUM dos 3 backends compilados (JVM, Native, KofJS) sintetiza; só o
  interpretador simula `<clinit>` lazy (`KofInterpreterMembers`).
- **Escopo confirmado (13/09, medido):** JVM (array, escalar, foldável e
  `String`), **Native x86 confirmado** (mesmo `null`/`0` — o `riscv/aarch`
  compartilha o emissor de estáticos) e KofJS. **O interpretador (Script)
  TAMBÉM devolve `null`/`0`** — a hipótese anterior de que "só o
  interpretador simula `<clinit>`" **não se sustenta**: como nenhum backend
  sintetiza o `<clinit>` a partir do inicializador do campo, não há o que o
  `ensureInit` executar. Correção do registro original (era "Native não
  testado; interpretador simula").
- **Seriedade:** `static X = new X(...)`/`static X = função()` é padrão comum
  (config, tabelas, singletons, caches). Provavelmente escondido porque o
  corpus usa só literais.
- **Ação sugerida:** sintetizar `<clinit>` nos 3 backends compilados quando a
  classe tem ≥1 estático não-constante (atribuições na ordem de declaração —
  mesma semântica que o interpretador já simula). Escopo estrutural: item de
  `docs/development/`, não bugfix de 1 commit.
- **Fix PARCIAL 13/09 (lane bugs-and-gaps `192.168.100.15`, `e886bea1`):**
  (a) `FieldConstantFolder.foldConstantExpr` dobra expressões **constantes**
  (unário `-`/`+`/`~`/`!` e binário aritmético/bitwise/lógico sobre literais)
  para o `initialValue` — `-1`, `2 + 3`, `-7L`, `-1.5`, `"a" + "b"`, `!false`
  agora valem nos **4 targets** (antes: `0`/`undefined`); (b) campo `static`
  não é mais injetado no construtor como `this.x = ...` (era PUTFIELD em
  campo estático → `IncompatibleClassChangeError` no JVM, no-op no Native).
  **Prova:** célula `staticinit` (4 targets, golden
  `-1\n5\n-7\n-1.5\nab\ntrue\n7`). **Residual ABERTO:** inicializador de
  **runtime** (`static Int[] a = new Int[3]`, `static X = f()`) continua sem
  `<clinit>` nos backends compilados — o fix estrutural (que o
  `NativeMethodEmitter:58` ignora de propósito) segue como item de
  `docs/development/`. **Não fechar o §186 com o fix parcial.**
- **✅ CORRIGIDO 14/09 (fix estrutural completo, lane bugs-and-gaps
  `192.168.100.15`, fecha o residual):** `<clinit>` agora é **sintetizado no
  IR** (`CompilerClassLowering.generateStaticInitializer`) para todo campo
  estático cujo inicializador não foi dobrado a constante
  (`initialValue == null`), na **ordem de declaração** — e os backends
  passam a emití-lo: **JVM** emite `<clinit>()V` ACC_PUBLIC|ACC_STATIC
  (JVMS §2.9 — a JVM roda na inicialização da classe, lazy como manda a
  spec); **KofJS** renomeia para `_kof_clinit` e chama no topo do módulo
  antes do `main` (`JsClassEmitter`+`JsBackend`); **Native x86** emite como
  função normal e o `_start` chama cada `<clinit>` antes do `main`
  (`NativeMethodEmitter`); **riscv64/aarch64** idem
  (`NativeArchEmitter.emitClinitCallsRiscv`; aarch64 herda do tradutor
  riscv). **Interpretador (Script)**: `ensureInit` já executava `<clinit>`
  — agora há o método para executar (lazily, na primeira leitura).
  **Bug irmão descoberto e corrigido na mesma unidade:** chamada **sem
  receiver** a método `static` da MESMA classe emitia `aload_0` +
  `invokevirtual` → `IncompatibleClassChangeError` (JVM, contexto de
  instância: `D.m(){ return twice(21) }`) e `VerifyError` (contexto
  estático: dentro do `<clinit>` — `static Int y = twice(21)`). Causa raiz:
  o `MethodSymbol` nunca carregava a flag STATIC (`SymbolTableBuilder`)
  e o lowering self-method (`ExpressionMethodCallLowerer`) sempre
  empilhava `this`. Fix: flag STATIC no símbolo + `KofCallKind.STATIC` sem
  receiver. **Prova:** `CoreRegressionE2ETest.staticNonConstantFieldInitializerClinit`
  (reprodutor exato da #133, JVM+JS), `.staticClinitMixedConstantAndNonConstant`
  (ConstantValue + `<clinit>` na mesma classe, ordem de execução) e
  `.receiverlessCallToSameClassStaticMethod` (as 3 faces do bug irmão,
  JVM+JS); célula via CLI `10\n100\n42` nos 4 targets compiláveis do host
  (JVM/JS/x86; riscv64 `.s` contém `Math2_clinit` + `_start` chama antes do
  `main` — toolchain/qemu ausente no host, gate ambienta). **Issue #133
  fecha com esta unidade.**
- **Workaround:** sem inicializador não-constante em `static`; atribuir
  explicitamente num método chamado antes do primeiro uso.
- **Descoberto:** 13/09, KOF-SBD-001-STRESS (STRESS-016).

### §187 — `new Char[n]`: store/load de `Char[]` NÃO estreita a 16 bits no Native (e no JS) — JVM/Script corretos — ✅ CORRIGIDO 15/09 (face **Native CORRIGIDA 13/09**; **face JS CORRIGIDA 15/09 pela lane compiler `192.168.100.22`** — `kofArraySet` kind char→`& 0xFFFF`)

- **Sintoma (medido 13/09, 4 targets):** `Char[]` guarda 32 bits em vez de
  fazer o narrowing de 16 bits (JVM `CASTORE`/`CALOAD`):
  ```kof
  main() {
      var c = new Char[2]
      c[0] = 70000
      c[1] = -1
      println(c[0])     // JVM/Script 4464 | Native/JS 70000
      println(c[1])     // JVM/Script 65535 | Native/JS -1
  }
  ```
  O **cast escalar** `70000 as Char` está certo nos 4 (`4464`) — o desvio é
  só no **elemento de array**.
- **Menor repro:** o bloco acima.
- **Causa raiz (Native):** `NativeOpHelpers.elementTypeSize` mapeia
  `char`/`Char` → **4** (junto de `int`), então `kof_array_set` escreve
  `movl` (32 bits, sem máscara de 16) e `kof_array_get` lê `movslq`
  (32→64 com sinal). O JVM usa `CASTORE` (trunca p/ 16) e `CALOAD`
  (zero-estende). `Byte`/`Short` no Native estão certos (tamanhos 1/2 já
  truncam no `kof_array_set`); só `char` está com tamanho errado.
- **✅ Fix Native 13/09 (lane bugs-and-gaps `192.168.100.15`):** máscara
  **16-bit no store**, mantendo o stride 4 (não tocar o alocador nem os
  decoders JSON): x86 `movzwl %dx, %edx` em `NativeOpHelpers.emitArrayStore`
  quando `as.elementType()` é `char`; riscv/aarch `slli a2, a2, 48` +
  `srli a2, a2, 48` em `NativeRiscvCrossEmit` (mesma condição). O load
  `movslq`/`lw` **segue correto** porque o valor gravado fica em `[0,65535]`
  — o alerta "trocar o tamanho p/ 2 quebra o load (`movswq` sinaliza)" é
  evitado por construção. Prova: célula `charnarrow` ampliada (1-D **e**
  2-D, `Set.of("script","js")` — Native e JVM travam `4464\n65535`).
  **NÃO estende a arrays `int`**: `elementTypeSize` fica intacto (a
  ambiguidade `char`↔`int` só existe no store, que agora carrega o
  `elementType` do `KofArrayStore`).
- **Causa raiz (JS):** mesma do §184 — `Array` JS puro + `kofArraySet`/`Get`
  sem metadado de tipo; não estreita `char` (16 bits) nem `byte`/`short`.
- **Impacto:** qualquer Kof que grave/leia `Char[]` com valor fora de
  `[0,65535]` produz valor diferente no Native e no JS (silencioso, regra 5).
- **Fix proposto (lane JS):** o mesmo do §184 (tag de tipo no
  `kofArraySet`/`Get`). Face **JS segue ABERTA**.
- **Provas a adicionar:** célula de matriz `charnarrow` (JVM DONE; Native/
  Script/JS PARTIAL — Script cai no §185) + testes cross.
- **Nota de escopo:** a face JS já é o mesmo defeito do §184; registrada
  separada para o Native (que o §184 não cobre). Script = §185 (crash).

### §188 — `String as Int` compila e faz `VerifyError` no runtime JVM (R6: código errado emitido)

- **Sintoma (medido 14/09, dono = 192.168.100.17):** `var p = "2026".split("-");
  var y = p.get(0) as Int` → a compilação JVM **SUCDEDE** e o `.class` faz
  `checkcast java/lang/Integer` sem parse: `VerifyError: Bad type on operand
  stack — Type 'java/lang/String' (stack[4]) is not assignable to integer`.
  Menor repro (roda via reflection para ver o erro real, regra JavaFX):
  ```kof
  main() {
      var today = time.todayIso()
      var parts = today.split("-")
      println(today == time.formatDateIso(parts.get(0) as Int, parts.get(1) as Int, parts.get(2) as Int))
  }
  ```
- **Causa raiz (parcial — catalogado, não corrigido):** o lowering de cast
  `as Int` aceita tipo-fonte `String` (o typer não rejeita) e o backend JVM
  emite checkcast direto; deveria ser SEM0xx (cast inválido) ou um caminho de
  parse documentado. Verificação do typer/backend é **regra 6** (muda contrato
  de `as`) — não corrigi às pressas nesta unidade.
- **Estado da suíte hoje:** nenhum teste passa por este caminho (o S7e usa
  `math.parseInt` pós-§183); por isto não está vermelho — mas UM PROGRAMA DE
  USUÁRIO VERIFICA ERRADO.
- **O que a linguagem já oferece para reparse sem cast:** igualdade de strings
  com `formatDateIso` (d5) e `parseDateIso` (S7g, retorna serial Int) — a
  conversão String→Int textual CANÔNICA é parse com função stdlib, não `as`.
  Se decidir, a correção provável é SEM0xx rejeitando `String as Int`.

### §189 — Record com campo de lista genérica NULLABLE (`List<Item>?`): a assinatura genérica era omitida e o `json.decode` devolvia `LinkedHashMap` cru (`ClassCastException`) — ✅ CORRIGIDO 14/09 (lane bugs-and-gaps `192.168.100.15`; o teste do #128 subiu VERMELHO no `76ca3dd4`)

- **Sintoma (medido 14/09, HEAD `76ca3dd4`, 4 módulos):**
  `JvmE2ETest.execRecordListFieldDecode` (o teste de regressão do **#128**,
  lane `.22`) **FALHA** com
  `ClassCastException: LinkedHashMap cannot be cast to Item` no acesso
  `items.get(0).name()`. O commit `76ca3dd4` afirma "Teste VERDE na
  beta-0.4.0 atual" — **falso verde (Q5):** o teste nunca passou neste HEAD.
  ```kof
  record Item(String? name)
  record Container(String? title, List<Item>? items)
  main() {
      var c = json.decode<Container>("{\"title\":\"t\",\"items\":[{\"name\":\"a\"}]}")
      println(c.items().get(0).name())   // ClassCastException: LinkedHashMap
  }
  ```
- **Causa raiz:** `JvmTypeMapper.toGenericSignature` só tratava `ClassType`
  e `ArrayType` — **`NullableType` caía no `return null`**, então o record
  component `items` (`List<Item>?`) era emitido **sem o atributo
  `Signature`**. `RecordComponent.getGenericType()` devolvia o `ArrayList`
  cru → `JvmRuntimeJson.listElement` = `null` → os elementos da lista
  **não eram bindados** (ficavam `LinkedHashMap` cru). O controle
  NÃO-nullable (`List<Item>`) já funcionava (bug 58/#34, coberto por
  `toGenericSignature(ClassType)`); só a forma **nullable** perdeu a
  assinatura.
- **Fix (Q0 causa raiz):** `toGenericSignature` desembrulha
  `NullableType` no topo (`if (type instanceof Type.NullableType n) return
  toGenericSignature(n.inner())`). Não há `?` de null no modelo de generics
  do Java — a assinatura é a do inner. Afeta campo E record component
  (ambos passam por `toGenericSignature`).
- **Prova Q1:** `JvmE2ETest.execRecordNullableGenericListFieldDecode` (novo;
  nullable populado `x`, nullable **ausente** → `null` honesto, e o controle
  NÃO-nullable `y`) + o teste do #128 (`execRecordListFieldDecode`) agora
  passa. `JvmE2ETest` **34/34**. **Falhava antes** (`ClassCastException`
  reproduzido com o fix revertido).
- **Nota de processo:** o `76ca3dd4` violou o portão (Q0/Q1/Q5) — subiu um
  teste vermelho declarando verde. A lane `.22` deve ser avisada; a correção
  do CÓDIGO (não do teste) é esta unidade. A issue #128 **só fecha com esta
  correção** (na distro antiga o sintoma tinha a mesma raiz).

### §190 ✅ CORRIGID 14/09 (lado-teste: `a689cbd2` lane `.18` adicionou `Content-Length` aos POSTs; re-medido verde 14:35 em `b7fdcb7e`) — `KofBlogE2ETest` VERMELHO no HEAD: os POST do teste omitem `Content-Length` e o servidor (correto) lê corpo vazio → `unexpected end of JSON` — ✅ RESOLVIDO 14/09 pelo dono (lane `.18`, `8eb156f4`); era TESTE, não produto — registrado 14/09 (lane bugs-and-gaps `192.168.100.15`)

- **Sintoma (medido 14/09, HEAD `521049aa`, suíte 4 módulos limpa):**
  `KofBlogE2ETest.canonicalAppServesFrontendDbAuthValidation` **FALHA**
  deterministicamente (também no HEAD sem as minhas mudanças):
  `login deve devolver token: HTTP/1.1 500 ... {"error": "handler error:
  unexpected end of JSON"}`.
- **Causa raiz (provada, não hipótese):** os `request(...)` do teste enviam
  corpo JSON **sem o header `Content-Length`** (ex.: `POST /login` em
  `KofBlogE2ETest.java:151-153`). O `readRequest` do servidor
  (`JvmRuntimeWebDispatch.readRequest`, l.275-290) lê o corpo **por
  `Content-Length`** (correto pela RFC 7230: request sem `Content-Length`/
  `Transfer-Encoding` não tem corpo) → `body()` = `""` → `json.decode<Post>("")`
  falha `unexpected end of JSON`. **Todos os outros E2E de web do repo**
  (`KofWebE2ETest:245/253`, `KofHttpServerTest:135`, `KofWebJsE2ETest:159`,
  `KofWebNativeE2ETest:193`) **enviam `Content-Length`** — o blog E2E é o único
  que não.
- **Menor repro (standalone, servidor real + H2):**
  ```
  POST /login HTTP/1.1            → 500 {"error":"...unexpected end of JSON"}
  (sem Content-Length, corpo {"title":"mel","body":"correct-horse"})

  POST /login HTTP/1.1            → {"token":"..."}
  Content-Length: 38 ...          (mesmo corpo)
  ```
  Ambas as respostas medidas em 14/09 contra o app canônico compilado (JVM).
- **Não é bug de produto:** o servidor está spec-correct e consistente com o
  resto da suíte; o defeito é do TESTE (falta `Content-Length` nas 4 chamadas
  POST com corpo: `/login` errado, `/login` certo, `/posts` sem sessão,
  `/posts` inválido, `/posts` válido).
- **Ação (dono = lane `.18`):** adicionar `Content-Length: <n>` aos POST com
  corpo no `KofBlogE2ETest` (helper `request` pode calcular e injetar o header
  quando o raw contém corpo). **Não corrijo aqui** — arquivo `EM CURSO` de
  outra lane (regra 2/8); o registro é para destravar o dono.
- **Impacto no gate:** a suíte 4 módulos fica **vermelha** por este teste até
  o dono corrigir — bloqueia o critério "suíte verde" de release.
- **✅ Resolvido 14/09 (`8eb156f4`, dono = lane `.18`):** o dono adicionou o
  helper `post(port, path, headers, body)` com `Content-Length` **em bytes**
  (`body.getBytes(UTF_8).length`) e, no mesmo commit, corrigiu um bug REAL
  que o teste escondia: o `readRequest` do JVM contava o corpo em **chars**
  (UTF-8 multibyte `Olá` travava a conexão até o timeout) — agora conta bytes.
  `KofBlogE2ETest` verde; suíte 4 módulos 0 FAILURE. O registro cumpriu o
  papel: destravou o dono sem eu tocar arquivo de outra lane.

### §191 — `security.cookieSet` com `secure`/`httpOnly` STRING: JVM e JS divergem no case (`"FALSE"`/`"False"`/`"0"`) — divergência cross-target SILENCIOSA — ✅ CORRIGIDO 14/09 (achado na caça Q4 da lane bugs-and-gaps `192.168.100.15`; feature recém-entrada, C11)

- **Sintoma (medido 14/09, HEAD `521049aa`, JVM × JS):** com `secure`/
  `httpOnly` vindos como **string** (não boolean), o JVM usa
  `equalsIgnoreCase(s,"false") || s.equals("0")` e o JS comparava
  `s === "false"` cru. Resultado:
  ```kof
  var s1 = mapOf(); s1.put("secure", "FALSE")
  println(security.cookieSet("a","v",s1))
  // JVM: a=v; Path=/; SameSite=Lax; HttpOnly        (Secure removido)
  // JS:  a=v; Path=/; SameSite=Lax; Secure; HttpOnly (Secure mantido)
  ```
  Mesmo padrão para `"False"` em `httpOnly`. `"0"` já casava nos dois.
  `Secure`/`HttpOnly` **mantidos** onde o JVM os remove é o lado inseguro
  (o cookie fica mais restrito no JS, mas a divergência é silenciosa).
- **Causa raiz:** `JsRuntimeUiSecurity.kofSecCookieSetOpts` →
  `flag()` comparava `s === "false"` (case-sensitive); o JVM
  (`JvmStringSecurityRuntime.kof_sec_cookie_flag`) usa `equalsIgnoreCase`
  + `"0"`. Paridade de string não estava travada por teste.
- **Fix (Q0):** `flag()` no JS usa `s.toLowerCase() === "false"` — mesma
  semântica do JVM. Um token.
- **Prova Q1:** `KofSecurityTest.cookieFlagStringCaseInsensitiveCrossTarget`
  (novo; golden único JVM **e** JS com `"FALSE"`, `"False"`, `"0"` e o
  boolean `false`) — **falhava no JS antes** (reproduzido revertendo o
  `toLowerCase`). Os testes antigos (`cookieSetOptsOverridesJvm`) só usavam
  `"false"` minúsculo = **verde falso (Q5)** para a divergência de case.
  `KofSecurityTest` 41/41.
- **Lição Q5:** teste de paridade cross-target que usa só a grafia canônica
  não prova a paridade — as bordas de case/`"0"`/boolean precisam entrar.

### §192 — `math.parse*OrDefault` no cross riscv/aarch: programa TRAVA na 1ª chamada `parseDoubleOrDefault` (era §189; colisão tripla de numeração renomeada 14/09)

- **Sintoma (medido 14/09 no run limpo da suíte de release + isolado, dono =
  192.168.100.17 — só catalogado, é lane stdlib/nat):**
  `KofMathTest.parseOrDefaultCrossArch` falha no riscv: o golden exige 13
  linhas + `ec 0`; o programa imprime as **8 linhas** dos `parse{Int,Long}`
  corretamente (42/-1/7/15/3/9007199254740993/-5/8) e **TRAVA na 9ª**
  (`parseDoubleOrDefault("2.5", 0.0) == 2.5`): no gate o teste queimou
  **5737 s** em `readAllBytes` do qemu (sem exit code); no re-run isolado o
  `qemu-riscv64` do mesmo binário pendurou >5 min e precisou ser morto a
  `-9`. DETERMÍNISTICO (2/2). Não é flake de host: é loop/bloqueio no
  caminho double do S13b sob riscv.
- **Causa raiz (parcial, lida no código — `NativeRiscvAsmRtB41.java`):** o
  TEMPLATE do wrapper usa o MESMO slot duas vezes: `sd t2, 24(sp)` salva o
  chain antigo do handler, e logo `sd a1, 24(sp)` grava o DEFAULT no MESMO
  offset (o frame é de 48B com ra@40/s0@32 e handler@0..24 — não sobra slot
  pro default). Consequência mecânica: (a) no sucesso E no handler, `ld t2,
  24(sp)` restaura `kof_exc_chain` com os BITS DO DEFAULT, não o chain
  antigo — a chain global fica lixo entre calls (ex.: default `-1` → chain
  = -1); (b) QUALQUER throw fora do wrapper com chain corrompido faz o
  desempacotador (`ld handler, 0(t2)`) ler endereço inválido → trap sem
  handler → hang/`SIGSEGV` sob qemu. A face DOUBLE (9ª linha do vetor) é
  onde o programa encontra o caminho que trava (throw com chain pré-corrompido);
  o Int/Long 'sobrevive' porque cada wrapper reinstala o próprio frame antes
  de tocar a chain. JVM/Script/JS/x86 não têm este template (usa try/catch
  host-side) — daí só riscv/aarch falharem. **Fix na lane nat:** slot
  separado pro default (frame 56B ou mover ra/s0) — a causa do hang exato
  (por que a 9ª e não a 3ª) pede `gdb-multiarch`/qemu -singlestep no binário
  do menor repro; o aliasing é bug REAL independentemente dele.
- **Menor repro:** `main(){ println(math.parseDoubleOrDefault("2.5", 0.0)
  == 2.5) }` compilado p/ `NATIVE_RISCV64`, rodado sob qemu → esperar
  `true`/`ec 0`.
- **Estado da suíte:** único teste na rota cross do parseOrDefault
  (célula da matriz stdlib13 cobre os outros alvos), por isso não é
  vermelho em massa — mas um programa de usuário no riscv morre.
- **Determinística CRAVADA (re-run isolado 14/09 ~04:20, dono =
  192.168.100.17):** riscv64CastSaturation falhou em 2.2s e o
  parseOrDefault pendurou o qemu >5 min (morto a -9) — 2/2, NÃO flake.
  (O bloqueio de cache dependabot relatado antes foi RESOLVIDO:
  surefire 3.6.0 + mariadb 3.5.10 + postgresql 42.7.13 no `.m2`;
  `mvn -o` resolve de novo.)
- **MENOR REPRO de 2 linhas CRAVADO (14/09 ~05:10, harness próprio
  `dev.cli.BR` compila riscv + qemu timeout 20s, dono = 192.168.100.17 —
  só diagnóstico, lane nat conserta):**
  ```
  main() {
      println(math.parseIntOrDefault("abc", -1))          // linha 1: imprime -1
      println(math.parseDoubleOrDefault("2.5", 0.0) == 2.5)  // trava aqui
  }
  ```
  Tabela de ordem (5 casos, todos ec medidos): throw-OrDefault (int OU
  long) ANTES de qualquer `to_double` → **HANG**; `to_double` ANTES do
  throw → ok; só throws int/long (3+ calls) → ok. Regra: **qualquer
  OrDefault que lança + qualquer `parseDouble`/`Double` depois = hang**.
  O binário imprime `-1` e trava na linha 2 (stdout parcial medido).
- **PC do loop infinito CRAVADO (`qemu -d exec` + objdump do binário do
  repro):** TBs 0x10b60↔0x10b6c alternando para sempre — o loop de
  escalação de expoente dentro da região `kof_string_to_double/float`
  (`beqz s8 / fmul ft0,ft0,ft1 / addi s5,s5,-1 / bnez s5`): `ft1` vem de
  constante em `.data` (o `# 30026` é só o símbolo anterior mais próximo,
  `kof_exc_chain+0x26` — pool de dados, NÃO a chain); o contador `s5` é
  que vem de estado do parse. Hang ⇒ `s5` inicial ≈ 2⁶³ (loop de
  escalação de expoente sem convergência). Coerente com o aliasing
  acima: com a chain global corrompida pelos BITS DO DEFAULT
  (`-1` = todos os bits), um throw subsequente desempacota estado lixo e
  o double-base chega ao parser como NaN/normal absurdo → s5 garbage.
  Causa do s5 exato pede gdb-multiarch na lane nat (o host tem só gdb
  x86; attach `-g` no qemu travou na leitura — não cravei além do loop).
- **Fix para a lane nat (duas frentes, na ordem):** (1) destravar o
  slot do default (frame 56B ou mover `ra`/`s0`) — mata o aliasing;
  (2) conferir por que a região de escalação de expoente lê seed em
  `kof_exc_chain+0x26` (load não alinhado, endereço par/ímpar pelo
  bit de expoente) — mesmo pós-fix (1), um seed lido de dentro da área
  do chain é fracilo. **Prova esperada pós-fix:** `dev.cli.BR` do repro
  de 2 linhas → `ec=0 out=[-1|true]` + os 3 vermelhos da suíte verde.


### §203 ✅ CORRIGIDO 14/09 — as-cast para tipo primitivo emite `CHECKCAST "?"` inválido e omite o unboxing — `VerifyError` (issue #205)

- **Sintoma (medido 14/09 ~09:40, dono = 192.168.100.17 — só catalogado,
  lane compiler):** `o as Int` onde `o` é um valor boxed/`Object` compila
  mas quebra no class-load:
  ```kof
  main() {
      var o = "7" as Object
      var i = o as Int
      println(i + 1)
  }
  ```
  `VerifyError: Bad type on operand stack` em `istore_2` (javap do
  `Main.main` emitido): `0: ldc "7"` → `2: checkcast Object` → `6: aload_1`
  → **`7: checkcast class "?"`** → **`10: istore_2`**. Dois bugs numa
  emissão só: (a) o alvo do cast resolve para o **internal name inválido
  `"?"`** (`ClassType` de primitivo renderiza `?` em vez de ser
  descartado/traduzido); (b) nenhum unboxing (`Integer.intValue()`) entre o
  valor de referência e o `istore` — mesmo com classe válida os tipos de
  pilha não bateriam (regra R6: código que compila e não carrega).
- **Esperado (contrato, § freeze + corpus):** `o as Int` sobre
  `Number`/`String` deve OU emitir `checkcast Integer` + `intValue()`
  (estilo JLS: cast para a box e depois unbox) OU rejeitar em compile-time
  com diagnóstico (família `COMP002`) — nunca emitir bytecode quebrado.
- **Relacionado:** mesma família do §188 (`String as Int` → `VerifyError`,
  a face `checkcast`-sem-parse) e do título da issue #205.
- **Pointer (lane compiler):** emissão de `CastExpr` com alvo primitivo —
  procurar `CHECKCAST` + tratamento de primitivo na região
  `ExpressionBinaryLowerer.java:48` / lowering de `as` no bytecode
  lowerer; o nome `?` vem do `internalName` de primitivo em
  `JvmTypeMapper`/`ClassType`.
- **Prova Q1:** o repro roda no HEAD `3edaf792` com `dev.cli.BJ` (JVM):
  `ec=1` + `VerifyError` via launcher por reflection (regra JavaFX — o
  launcher direto engole atrás da mensagem falsa do JavaFX). O fix deve
  imprimir `8` + caso em `CoreRegressionE2ETest`.
- **✅ CORRIGIDO (medido 14/09 ~12:35 em `c252a983`, fix `8af810c5` "map
  primitive types to boxed classes in instanceof and checkcast"):** o javap do
  repro §203 agora emite `checkcast java/lang/Integer` VÁLIDO +
  `invokevirtual intValue()` + `istore` (sem `?`, sem istore cru). A alegação
  exata da issue está morta. Dois residuais DIFERENTES continuam abertos e NÃO
  são §203: (i) `o as Int` com `o` contendo uma `String` real → runtime
  `ClassCastException` (a face de design do §188 — regra 6, decisão parse vs
  cast da mantenedora); (ii) `i as Object` com `i` Int → NOVO §213
  (boxing ausente — `bipush` + `checkcast Object` → `VerifyError`).

### §206 ✅ CORRIGIDO 14/09 — classe com parâmetros de construtor: campos/métodos extras no corpo não resolvem (issue #215)

- **Sintoma (medido 14/09 ~11:05, dono = 192.168.100.17 — só catalogado,
  lane compiler):** uma classe estilo-record com corpo que declara um campo
  extra inicializado a partir dos parâmetros do construtor não compila,
  embora o docs/corpus apresente essa forma:
  ```kof
  class Box(Int w, Int h) {
      Int area = w * h
      Int getArea() { return area }
  }
  ```
  `SEM011: Undefined variable or type: 'area'` dentro do corpo da classe, e
  `SEM025: Cannot resolve field 'area'` no chamador. As declarações de campo
  do corpo não enxergam os parâmetros do construtor, e o campo adicionado
  não é registrado na tabela de campos da classe.
- **Relacionado:** §207 (mesma família — `extends`/`implements` em classe
  com parâmetros de construtor → PARSE007): a forma com parâmetros parece
  suportada apenas quando o corpo é exatamente o caso record.
- **Pointer (lane compiler):** desugar de `class X(params)` COM corpo
  explícito — ordem de resolução entre o escopo dos parâmetros do ctor e os
  campos do corpo; registro da tabela de campos no `ClassTyper`.
- **Estado:** ✅ CORRIGIDO 14/09 (`6f7f55bc`, "register and lower fields
  declared in constructor-param classes"); repro re-verificado verde no tip
  15/09 (`class Box(Int w, Int h) { Int area = w * h }` → `getArea()` imprime `12`).

### §207 ✅ CORRIGIDO 14/09 — `class Circle(Double r) extends Shape` → PARSE007 depois do parêntese de fechamento (issue #217)

- **Sintoma (medido 14/09 ~11:05, dono = 192.168.100.17 — só catalogado,
  lane compiler):** a forma com parâmetros de construtor só parseia SEM
  `extends`/`implements`: `class Circle(Double radius) extends Shape { ... }`
  falha com `PARSE007: Expected type declaration` na posição do `extends`
  (col 29 no repro da issue). `class X { }` simples com extends funciona. O
  caminho record-like do parser não consome as cláusulas de superclasse/
  interface depois da lista de parâmetros.
- **Pointer (lane compiler):** ramo do parser para `class NAME (` — depois
  do `)` dos parâmetros precisa aceitar `extends`/`implements` como o ramo
  de classe simples.
- **Estado:** reproduz no `1c13d982` (caso exato da issue).

### §208 ✅ CORRIGIDO 14/09 — sintaxe de tipo-função `(T) -> R` aceita como parâmetro/anotação de var mas REJEITADA como tipo de retorno/tipo de campo (issue #218)

- **Sintoma (medido 14/09 ~11:05, dono = 192.168.100.17 — só catalogado,
  lane compiler):** cobertura inconsistente da gramática do tipo-função:
  funciona em posição de parâmetro e em anotação `var`, falha no parse como
  **tipo de retorno**:
  ```kof
  (Int) -> Int makeDoubler() { return (x: Int) -> x * 2 }
  ```
  → `PARSE007: Expected type declaration` + `PARSE010: Expected function
  name` (o parser lê `(Int)` como expressão parentizada e desiste do nome da
  função). A mesma forma falha como tipo de campo segundo a issue. O
  contorno é o tipo anotado no chamador ou a forma de retorno tardio
  `makeDoubler(): (Int) -> Int`.
- **✅ CORRIGIDO (`da768386` "support function type syntax as return type and
  class field type", 12:32; re-medido 14/09 ~12:57 no `3f6ea150` com classes
  FRESCAS após `mvn -o compile`): `(Int) -> Int makeDoubler()` agora compila e
  roda `ec=0` (`42`). Issue #218 FECHADA. (Meu comentário anterior "AINDA
  REPRODUZ" das 12:40 era artefato de CLASSE OBSOLETA — o fix entrou 12:32
  mas meu target/classes era das 12:28; retificado na issue. A lição se
  re-confirma: `mvn -o compile` IMEDIATAMENTE antes de medir.)

### §209 — corpo de default method de interface descartado silenciosamente → compila como abstrato → falso positivo `SEM043` na classe implementadora (issue #213) — ✅ CORRIGIDO no JVM 14/09 (`59e2403b`, `InterfaceDefaultMethodE2ETest` 5/5); ⚠️ face JS/Native → §248

- **Sintoma (medido 14/09 ~11:20, dono = 192.168.100.17 — só catalogado,
  lane compiler):** um método de interface COM corpo (default method) é
  compilado como ABSTRATO — o corpo é descartado. A classe que implementa
  sem sobrescrevê-lo é rejeitada: `class 'SimpleGreeter' implements
  Greeter` → `SEM043` (método abstrato faltando), apesar de a interface
  fornecer implementação real:
  ```kof
  interface Greeter {
      String greet(String name)
      String greetLoud(String name) { return greet(name).toUpperCase() }
  }
  class SimpleGreeter implements Greeter {
      String greet(String name) { return "Hello " + name }
  }
  ```
  Esperado: `g.greetLoud("kof")` imprime `HELLO KOF` (default methods,
  Java/2016). Atual: `SEM043` em compile-time.
- **Pointer (lane compiler):** lowering de interface — o corpo dos default
  methods deve ser emitido (flag `default` no MethodElement); o conjunto de
  métodos abstratos usado no check `SEM043` deve EXCLUIR os que têm corpo.
- **Estado:** ✅ CORRIGIDO no JVM 14/09 (`59e2403b`) — a interface só ganha a flag `ABSTRACT` para métodos SEM corpo (`SymbolTableBuilder`), `checkInterfaceImplementation` pula os defaults (`(m.accessFlags() & ABSTRACT)==0 → continue`) e `CompilerClassLowering` emite o corpo com a flag `default`; prova `InterfaceDefaultMethodE2ETest` 5/5 (dedicado). **⚠️ As faces JS e Native seguem QUEBRADAS e silenciosas — ver §248** (medido 15/09 no tip fresco com jar self-built): o JS lança `TypeError: g.greetLoud is not a function`, o Native imprime `null` em vez de `HELLO ALICE` com exit 0. O corpus `training/` ainda não tem exemplo de default method (gap de spec também).

### §210 — chamada a método Java VARARGS (`String.format`) gera descritor errado: args não empacotados em array, retorno inferido como Object — `NoSuchMethodError` (issue #216) — ✅ CORRIGIDO 14/09 (lane bugs-and-gaps, 192.168.100.15, `042916c1`)

- **Sintoma (medido 14/09 ~11:20, dono = 192.168.100.17 — só catalogado,
  lane compiler):** interop com método varargs é emitido sem preencher o
  slot varargs (cada arg extra passado individualmente) e com o tipo de
  retorno inferido como `Object`:
  ```kof
  main() {
      var s = String.format("Hello %s, age %d", "Alice", 30)
      println(s)
  }
  ```
  → `NoSuchMethodError: 'java.lang.Object java.lang.String.format(...)'`.
  Real JVM: `format` = `(String, Object[])` retornando `String`. Dois bugs:
  (a) o rabo varargs não vira `Object[]`; (b) o retorno do varargs interop
  cai em Object. R6 (compila, nunca roda).
- **Relacionado:** #156 (`String.format` em outros corpos — mesma família);
  §166 (retorno de estático `parse*` emitido como String — face inversa).
- **Pointer (lane compiler):** lowering de `MethodCall` interop — detectar
  `isVarArgs()`, juntar os args finais em `anewarray Object` e resolver o
  descritor de retorno não-varargs.
- **Estado:** ✅ **CORRIGIDO 14/09 (lane bugs-and-gaps, `192.168.100.15`,
  `042916c1`).** Raiz: `ExternalClasspath.findDeclared`/`resolveMethod` casam
  por **name+arity apenas** (sem `ACC_VARARGS`) e `java.lang.String` não está
  nos entries, então o ramo de receptor-builtin de `ExpressionMethodCallLowerer`
  caía no descriptor fabricado. Fix: novo `StringFormatCallLowerer` empacota os
  args extras num `Object[]` (primitivos boxados) e emite o descritor REAL
  `(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;` (0-args coberto).
  Prova: `StringFormatVarargsE2ETest` 9/9, **vermelho pré-fix** provado
  (`aa78eba0`/`d6eaae0c`) com o `NoSuchMethodError` exato. Fecha #156/#216.
  **Residual JS catalogado §239** (KofJS sem lowering de `String.format`,
  `COMP002` pré-existente; lane JS).

### §211 — valores de enum compilam como `ldc <String>`, nenhuma classe enum é emitida — `Dir.N.getClass()` == `java.lang.String`, `Dir.N == "N"` é `true` (issue #207, REABERTA)

- **Sintoma (medido 14/09 ~12:30, dono = 192.168.100.17 — só catalogado,
  lane compiler):** `enum Dir { N, S, E, W }` NÃO produz `Dir.class`; toda
  referência `Dir.N` emite `ldc "N"` (constante String):
  ```kof
  main() {
      println(Dir.N.getClass())     // imprime: class java.lang.String
      println(Dir.N == "N")         // imprime: true  (um Dir É uma String?)
      var d: Dir = Dir.S
      println(d instanceof Dir)     // vira instanceof java/lang/String
  }
  ```
  javap: `0: ldc // String N` + nenhum class file de `Dir`. O crash antigo
  (`NoSuchMethodError: String.name()`) morreu só porque `.name()` na
  constante dobra em compile-time; a face SEMÂNTICA do título da issue —
  "compilados como constantes String em vez de instâncias getstatic de
  enum" — persiste inteira. Meu comentário GREEN anterior na #207 estava
  ERRADO (provou não-crash, não identidade) — retificado na issue.
- **Esperado (contrato + título da issue #207):** enum = classe com
  instâncias `static final`; `Dir.N` → `getstatic Dir.N : LDir;`;
  `getClass()` → `Dir`; `Dir.N == "N"` → erro de tipo ou false; switch sobre
  enum por identidade.
- **Pointer (lane compiler):** o caminho de lowering de enum que dobra
  `Dir.Value` em constante String (procurar `ldc` de enum no emitter de
  valores / `CompilerEnum*`; o emissor de classe para `EnumDecl` parece
  ser pulado inteiramente).
- **Estado:** reproduz no `0ab25887`; **RE-MEDIDO 15/09 ~18:30 pela lane
  bugs-and-gaps `192.168.100.15` no tip `b66edfe9`** (classes frescas,
  `Triage` JVM + `javap`): `Dir.N.getClass()` → `class java.lang.String`,
  `Dir.N == "N"` → `true`, `d instanceof Dir` → `instanceof java/lang/String`,
  e **nenhum `Dir.class` é emitido** (`javap` mostra `ldc // String N`). A
  issue **#207 foi fechada como "completed" em 15/09 04:47 apenas na face
  superficial `values()`/`name()` — REABERTA por esta lane 15/09 ~18:35 com a
  prova de bytecode** (o fix §165 `479506e1` tipou `values()`, mas nunca
  implementou a identidade de enum). Regra 6 (semântica); fix = lane compiler
  com prova E2E (getClass + == + switch + identidade de values).
- **🟡 FATIA 1 CORRIGIDA 15/09 (lane bugs-and-gaps `192.168.100.15`,
  D-ENUM207 — #207 reatribuída a esta lane pela mantenedora, `3629a13b`):**
  `enum == String` (em qualquer ordem) agora é ERRO DE TIPO em compile-time —
  `SEM062` no frontend COMPARTILHADO (`TypeChecker.inferBinaryResultType` +
  `isEnumRef`), então o mesmo diagnóstico vale nos 4 alvos (sem divergência,
  freeze regra 5; NÃO é half-landing — lição §241). `Dir.N == "N"` (antes
  `true`) é rejeitado; `Dir.N == Dir.M` e enum-vs-enum-nulável continuam
  compilando. Corpus atualizado no mesmo commit (`classes.md`, `semantics.md`,
  `syntax.md`, `expressions.md`, `type-system.md` EN+PT) para o contrato
  ratificado. Prova: `EnumIdentityE2ETest` 4/4 (VERMELHO 2/4 pré-fix: as duas
  ordens enum×String compilavam). **Segue ABERTO:** as faces de identidade —
  `Dir.N` ainda é `ldc "N"`, sem `Dir.class`, `getClass()` → `String`,
  `instanceof Dir` → `String`; `Dir.N == Dir.M` ainda é conteúdo (String) e não
  identidade `getstatic`. Isso é a fatia 2 (classe enum JVM + `getstatic` +
  `name`/`ordinal` reais, depois paridade Script/Native/JS) — o restante do
  vertical D-ENUM207.
- **✅ FATIA 2 CORRIGIDA 15/09 (lane bugs-and-gaps `192.168.100.15`,
  D-ENUM207).** O enum agora é uma **CLASSE real** emitida por
  `CompilerEnumLowering`: as constantes são campos `static final LDir;`
  criados no `<clinit>` (via `KofNewObject` + `KofPutStatic`, o caminho
  genérico de classe que os 4 alvos já suportam — sem ABI novo por target,
  freeze regra 5, NÃO é half-landing §241). Mudanças: novo
  `CompilerEnumLowering.lowerEnum`; `CompilerPipeline.lowerToIR` ganhou o
  branch `EnumDeclarationNode` (antes caía no `default`); `AccessFlags.ENUM
  (0x4000)`; `JvmTypeMapper.classDescriptor/…InternalName` parou de apagar
  enum → `Ljava/lang/String;`; `ExpressionLowerer` `Dir.N` e `N` não-qualificado
  emitem `KofGetStatic Dir.N : LDir;`; `==`/`!=` entre enums é **identidade**
  (`if_acmp` — `CompilerComparisons`/`ExpressionBinaryLowerer`), assim como o
  `switch` de enum (`SwitchStmtLowerer`/`SwitchExprLowerer`/`ExpressionPrintLowerer`);
  `name`/`ordinal`/`toString`/`compareTo` são métodos de instância reais
  emitidos na classe, `values()`/`valueOf()` estáticos reais (List<Dir>, null
  no miss); `ExpressionPrintLowerer` imprime o nome via o `toString()` da
  classe (D-PRINT preservado). `CollectionWrites.isStringLike` (§150) parou de
  marcar enum como String — a membership nativa agora é por identidade de
  ponteiro (os singletons), corrigindo o SIGSEGV que o equals-por-conteúdo no
  ponteiro do objeto causava.
  - **Prova (Q1 mesmo commit):** `EnumIdentityE2ETest` 4→6 —
    `enumHasRealIdentityOnJvm` (`getClass()`→`class Dir`, `==`/`!=` por
    identidade, `instanceof Dir`, name/ordinal/compareTo, `values()`/`valueOf()`)
    e `enumConstantCompilesToGetstatic` (`javap` mostra `getstatic
    Dir.N:LDir;`, sem `ldc // String N`; `Dir.class` emitido). `KofEnumTest`
    6/6 (4 alvos), `KofMapSetTest` 14/14, `KofEnumSwitchTest` 4/4,
    `CoreRegressionE2ETest` 102/102, `BackendParityTest` 19/19, `JvmE2ETest`
    35/35, `KofInterpreterParityTest` 25/25, `KofJsE2ETest` 40/40. Suíte
    completa do compilador **1877 run / 0 fail / 16 err** (os 16 = `node`
    ausente, ambientais) / 186 skip. Corpus atualizado no mesmo commit
    (`classes.md` EN+PT). As faces de identidade do título da issue estão
    fechadas; o #207 pode ser fechado com esta prova.

### §212 — acesso de campo `b.size` resolve para o MÉTODO `size()` quando um método divide o nome do campo — campo engolido em silêncio (issue #219) — ✅ CORRIGIDO 15/09 (issue #219 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~13:00, dono = 192.168.100.17 — só catalogado,
  lane compiler):** uma classe com um campo e um método de mesmo nome compila
  a LEITURA DO CAMPO como chamada de método. Valor silenciosamente errado
  (pior que o crash antigo — R6 + regra 4 do freeze):
  ```kof
  class Box {
      Int size = 7
      Int size() { return 99 }
  }
  main() {
      var b = new Box()
      println(b.size)     // imprime 99 — DEVE ser 7
      println(b.size())   // imprime 99 — correto
  }
  ```
- **javap (medido, o mecanismo exato):** no offset 12 de `Main.main`, a
  expressão `b.size` (SEM parênteses) emite `invokevirtual Box.size:()I` onde
  deveria emitir `getfield Box.size:I`. `Box.class` declara AMBOS `public int
  size;` e `public int size()` — a resolução escolhe o método sempre que os
  nomes colidem, em toda leitura (`b.size` usado duas vezes: ambos
  invokevirtual).
- **Esperado:** `b.size` → `getfield`; `b.size()` → `invokevirtual`. A sintaxe
  desambigua (parênteses) — o resolvedor tem que respeitar. Se a linguagem
  quiser PROIBIR a colisão (campo ≡ nome de método), o diagnóstico em
  compile-time é o caminho honesto (território de regra 6), mas HOJE a forma
  é aceita e produz lixo.
- **Pointer (lane compiler):** lowering de acesso a membro para um
  `FieldAccessExpr` não-call quando o receptor é `class` — onde decide
  getfield vs chamada-de-acessor vs method-reflection; provavelmente o caminho
  `MemberCallTyper`/`FieldAccessExpr` ou a resolução de propriedade em
  `CompilerClassLowering` preferindo `methods()` a `fields()` na colisão.
- **Estado:** ✅ CORRIGIDO 14/09 (`79ab6e0e`, "resolve field access to field
  when method has same name (#219)"); repro re-verificado verde no tip 15/09
  (`b.size` imprime `7`, `b.size()` imprime `99`).
- **Nota de triagem (mesma sessão, #220/#221/#222 → GREEN com prova javap):**
  #220 (box de campo genérico) `Integer.valueOf` antes do `putfield` = correto;
  #221 (static não-constante) roda `100|8|foobar|100` — fecha a face residual
  do §186 via `814f44da`; #222 (método estilo-ctor) emite `<init>(II)` de
  verdade. Comentários postados.

### §213 — `as Object` / cast primitivo→referência NÃO emite boxing → `bipush`+`checkcast Object` → `VerifyError` em @2: checkcast (NOVO 14/09, achado ao re-medir §203) — ✅ CORRIGIDO 15/09 (box na fonte do cast `as`; lane compiler `192.168.100.22`)

- **Sintoma (medido 14/09 ~12:40 no `c252a983`, dono = 192.168.100.17 — só
  catalogado, lane compiler):** fazer cast de uma expressão/valor primitivo
  para um tipo de referência é emitido como o primitivo cru seguido de
  `checkcast`:
  ```kof
  main() {
      var i = 7
      var o = i as Object    // VerifyError @ checkcast
      println(o)
  }
  ```
  `javap`: `0: bipush 7` → `2: checkcast java/lang/Object` (tipo na pilha
  `integer` não atribuível a referência). Igual com literal (`7 as Object`) e
  com a classe-alvo (`as String` sobre Int etc.). Nota: o caminho de
  **anotação de var** (`var o: Object = 7`) FAZ o boxing (`Integer.valueOf`) —
  só o lowering do operador `as` está quebrado. R6: compila, nunca carrega.
- **Relacionado:** mesma família de lowering de §203/§188 (operador `as`). O
  fix do §203 (`8af810c5`) mapeou primitivo→boxed para os ALVOS de
  instanceof/checkcast, mas o LADO FONTE (expressão primitiva cast para
  referência) ainda perde o `valueOf`.
- **Pointer (lane compiler):** o emissor do cast `as` — quando o tipo de
  origem é primitivo e o alvo é referência, inserir a box (`Integer.valueOf`
  etc.), espelhando o que o caminho de atribuição `var o: Object = 7` já faz.
- **Prova Q1:** os repros `e205c/e205e` acima → pós-fix devem imprimir `7`
  (sem VerifyError); `var o: Object = 7` (`e205d`) continua `ec=0`.
- **Correção (Q0, causa-raiz — lane compiler `192.168.100.22`, 15/09):** o ramo
  else do cast `as` em `ExpressionBinaryLowerer.lower` emitia o operando cru de
  `bin.left()` e depois `KofCheckCast(castTarget)`. Quando o tipo de origem é
  primitivo e o alvo é referência, agora um `kof_box`
  (`driver.emitErasureBox`) é inserido antes do checkcast — exatamente o
  caminho que o `StatementLowerer` já usava para `var o: Object = 7`.
- **Prova (Q1, teste dedicado):** `PrimitiveToReferenceCastE2ETest` 6/6 — Int
  local/literal, todas as larguras de primitivo (Int/Double/Bool/Long),
  referência→referência, primitivo→primitivo (`i as Long`/`i as Char` seguem
  numéricos) e referência→primitivo com unbox (§205). **VERMELHO pré-fix: 4/6
  falham** (as faces `as Object` morriam com `VerifyError` no checkcast, medido
  com o emissor revertido); os 2 guards (cast numérico, unbox) já estavam
  verdes. Cross-target: JVM/JS/Script imprimem o mesmo valor; Native compila.

### §214 — tipo de lambda perdido ao ler de container genérico: `List<() -> Int>.get(0)` → SEM015 "not a function" (issue #193) — ✅ CORRIGIDO 15/09 (issues #193/#236 fechadas; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~13:10 no `7ba7e48d` com classes FRESCAS (`mvn -o
  compile` antes), dono = 192.168.100.17 — só catalogado, lane compiler):** o
  tipo de elemento `() -> Int` é apagado na leitura, então o handle obtido
  não é chamável:
  ```kof
  main() {
      var fns = new List<() -> Int>()
      fns.add(() -> 42)
      var f = fns.get(0)
      println(f())     // SEM015: variable 'f' is not a function
  }
  ```
- **Família relacionada (medida hoje):** `Function<() -> Void>` como tipo de
  PARÂMETRO também falha (`runIt(f: Function<() -> Void>)` → `variable 'f' is
  not a function`, e204c 12:35) — mesma raiz: o type-argument genérico que
  carrega um tipo-função não é recuperado no ponto de uso (lista/campo/
  parâmetro).
- **2ª face — chamada inline (issue #236, medida 14/09 ~15:05 no `1f4ca5c9`
  com classes FRESCAS):** `println(fns.get(0)())` (chamada SEM a variável
  intermediária) NÃO passa pelo check SEM015 — o tipo de elemento apagado
  flui direto pro emissor de call e produz `invokevirtual ""."":()Ljava/lang/Object;`
  (owner+nome VAZIOS, entrada `#45 = Class ""` no constant pool) →
  `ClassFormatError: Illegal class name "" in class file Default/Main` no
  LOAD. Miscompile silencioso → crash (pior que o SEM015 honesto da 1ª face;
  R6). Mesma raiz, mesmo fix (recuperar o FunctionType na leitura de `.get()`)
  fecha as duas faces.
- **Pointer (lane compiler):** caminho do typer que mapeia o tipo de elemento
  de `List<T>` e os type-args de `Function<...>` de volta para `FunctionType`
  na leitura de `.get()`/parâmetro (cf. o `toGenericSignature` do §189 que
  desembrulha `NullableType` — o caso de função-argumento é o buraco irmão).
  O caminho de chamada inline precisa ALSO guardar: um callee não-resolvido
  deve diagnosticar, nunca emitir `""` como owner.
- **Estado:** ✅ CORRIGIDO (issues #193/#236 fechadas 14/09); repro re-verificado
  verde no tip 15/09 (`new List<() -> Int>().get(0)` → `f()` imprime `42`).

### §215 — padrão de vinculação com guarda (`case String s if cond -> s`) omite o store da variável vinculada → `VerifyError: Bad local variable type` na entrada do ramo (issue #199) — ✅ CORRIGIDO 15/09 (issue #199 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~13:10 no `7ba7e48d`, classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):** a variável vinculada de um
  padrão de tipo COM GUARDA nunca é `astore`ada; usá-la na guarda/ramo
  compila mas falha no load:
  ```kof
  main() {
      var obj: Object = "hello"
      var r = switch (obj) {
          case String s if s.length() > 0 -> s
          default -> "x"
      }
      println(r)
  }
  ```
  `VerifyError: Bad local variable type — Type top (current frame,
  locals[3]) is not assignable to reference type` no offset 41 (`aload_3`).
  javap: o ramo faz `aload_2; instanceof String; ifeq` direto na guarda sem
  `checkcast`/`astore 3` de `s`. A forma SEM guarda (`case String s -> s`)
  funciona — o caminho com guarda pulou o store da vinculação.
- **Esperado:** o ramo com guarda armazena o valor do cast no slot da
  vinculação ANTES de avaliar a guarda (a guarda lê `s`!).
- **Pointer (lane compiler):** lowering de switch-pattern para ramos com
  guarda — `SwitchExprLowerer`/`KofInterpreterMembers` no caminho da guarda
  sem o emit de vinculação que o caminho sem guarda faz; irmão do §199
  (slots de destructuring de record, que FOI consertado por `1bef9281`).
- **Estado:** ✅ CORRIGIDO 14/09 (`1f56ee3a`, "#199 — switch-EXPR com padrão
  guardado omitia o store da var bound (VerifyError)"; issue #199 fechada);
  repro re-verificado verde no tip 15/09 (imprime `hello`).

### §216 — `Char` é boxado como `Integer`: `println(c)` / `c.toString()` mostram o code point (`65`) em vez do caractere (`A`) (issues #168 + #153, uma raiz) — 🟡 face 1 `c.toString()` ✅ CORRIGIDA 15/09 (`69bc0f73`); face 2 `println`/concat congelada regra 6 (0.4.1)

- **Sintoma (medido 14/09 ~13:05 no `d2d025f4` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):** um valor `Char` passa
  pela box int, então qualquer exibição em string mostra o NÚMERO, em
  silêncio (R6 / freeze-4 valor-errado, não crash):
  ```kof
  main() {
      var c: Char = 'A'
      println(c)              // #168: imprime 65, esperado A
      println("char=" + c)    // #168: imprime char=65
      var a = 'A'
      println(a.toString())        // #153: imprime 65, esperado A
      println(a.toString().length())  // #153: imprime 2 (len de "65"), esperado 1
  }
  ```
  `ec=0` o tempo todo — silencioso. As duas issues são a MESMA raiz (`Char`
  → `Integer.valueOf` no caminho de boxing; §213 é a família geral de box
  faltante, esta é a box ERRADA). Relacionado: família §166/#156 (descriptor/
  box errado em primitivos).
- **Esperado:** `Char` vira box `java/lang/Character`; `c.toString()` →
  `"A"`; concatenação → o caractere.
- **Pointer (lane compiler):** o mapeador de box de primitivo para `Char`
  (o caso `char`→`Character` é descartado, caindo em `Integer`) — o caminho
  `8af810c5` "mapear tipos primitivos p/ classes boxeadas" que consertou as
  faces checkcast/instanceof provavelmente omite o sítio de concatenação
  String / boxing, e `Char.toString()` baixa para `Integer.toString`.
- **Causa raiz MEDIDA 15/09 (lane bugs-and-gaps `192.168.100.15`, classes
  FRESCAS em `212a8dbc`, javap):** `TypeEmitter.boxPrimitive` mapeia
  `char` → `new Type.ClassType("java.lang", "Integer", …)` e força o
  parâmetro do `valueOf` para `INT` (linhas 27/32). Os dois sítios de
  stringificação boxam o char como `Integer` e chamam
  `String.valueOf(Object)`:
  ```
  invokestatic java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
  invokestatic java/lang/String.valueOf:(Ljava/lang/Object;)Ljava/lang/String;
  ```
  → `"65"`. Sítios afetados: `ExpressionPrintLowerer:63` (println),
  `ExpressionBinaryLowerer:253/261` (concat), `ExpressionInstanceCallLowerer:411`
  (`toString` de primitivo).
- **Duas faces — uma é bug, outra é congelada (triagem da mantenedora na
  #153, 14/09):**
  1. **`Char.toString()` → `"A"` é bug de paridade REAL** (triagem da
     mantenedora na #153: "`println(Char)` numérico é comportamento
     documentado; `Char.toString()` não"). Nenhum documento do corpus fixa
     `Char.toString()` como numérico, então alinhá-lo ao
     `Character.toString` do Java é bugfix simples (regra 4 do Freeze), não
     mudança de semântica. O `String.valueOf(char)` standalone (overload
     `(C)`) já dá o caractere — corrigido no §27 (07/09) —, então o
     `toString` deve casar com ele. Sítio do fix:
     `ExpressionInstanceCallLowerer:411` (`toString` de primitivo).
  2. **`println(char)` / concat é contrato CONGELADO (regra 6):**
     `training/language/strings.md:25` documenta
     `println(s.charAt(0)) // 72 (H)` (numérico) e o §27 deste arquivo
     reafirma ("`println(char)` é numérico (`72`) nos 3 targets
     (congelado)"). Mudar a stringificação implícita do char para o
     caractere é **mudança de semântica** → bump + corpus + migração, exige
     ratificação da mantenedora. NÃO tocar sem ela.
  - A mantenedora adiou a correção das duas issues para a **0.4.1** (patch
    de estabilização); esta lane registra a causa raiz + escalação e não
    fura a fila na `beta-0.4.0`.
  - O storage de coleção deve continuar boxando char como `Integer`
    (`JvmOpCollections.boxedClassNameFor` default + `unboxMethodName`
    char→`intValue`) — o fix §104b-ii face-char depende disso; o fix
    estreito do `toString` não pode perturbá-lo.
- **Estado:** **face 1 (`Char.toString()`) ✅ CORRIGIDA 15/09** (`69bc0f73`,
  lane compiler `192.168.100.22`) — `toString()` num receptor `char` agora
  emite `String.valueOf(char)` com o descritor `(C)` direto no receptor (sem
  box), o mesmo overload que o `String.valueOf(c)` standalone já usa (§27);
  `CharToStringE2ETest` 5/5. **Re-verificado independentemente por esta lane no
  tip `69bc0f73`** (classes frescas, `Triage` no JVM): `'A'.toString()` → `A`,
  length `1`. **Face 2** (`println(char)` / concat → numérico `65`) reproduz —
  **congelada** regra 6 (documentado em `training/language/strings.md:25`),
  adiada pela mantenedora p/ 0.4.1; NÃO tocada. O fix preserva o boxing de
  char em coleção como `Integer` (não perturba a face `char` do §104b-ii).

### §217 — retorno de método de classe genérica não faz downcast: `Box<String>.get(): T` emite `()Object`, chamar método nele → `VerifyError` no primeiro uso (issue #161) — ✅ CORRIGIDO 14/09 (`3f742916`, lane bugs-and-gaps; `GenericMethodReturnCastE2ETest` 4/4)

- **Sintoma (medido 14/09 ~13:05 no `d2d025f4`, classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):** chamar um método no
  resultado de um método genérico cujo retorno declarado é uma variável de
  tipo produz um `Object` cru na pilha; a primeira chamada de método de
  referência nele quebra no load:
  ```kof
  class Box<T> {
      T item = null
      set(T v) { this.item = v }
      get(): T { return this.item }
  }
  main() {
      var b = new Box<String>()
      b.set("hello")
      var s = b.get()
      println(s.length())   // VerifyError: Bad type on operand stack
  }
  ```
- **javap (medido, mecanismo):** `Box.get:()Ljava/lang/Object;` →
  `invokevirtual String.length()I` no offset 23 SEM o `checkcast
  java/lang/String` que a erasure exige — `s.length()` é enviado a um
  `Object` na pilha. (A chamada `b.set` passa `Object` corretamente; só o
  caminho de-chamada-de-método-em-`T`-resultado perde o cast.)
- **Esperado:** `checkcast String` entre `get()` e `.length()` (o mesmo cast
  de erasure que o fix irmão `§203`/`8af810c5` agora faz para `as`).
- **Pointer (lane compiler):** tipagem do receptor de member-call para um
  retorno de variável genérica — onde o `checkcast` de erasure é inserido; o
  sítio de chamada tipado `T` não o dispara. Família: §161/#166/#161 gaps de
  descriptor genérico.
- **Estado:** ✅ FIXED 14/09 (`3f742916`, lane bugs-and-gaps
  `192.168.100.15`). Duas faces, uma raiz:
  1. **chamada direta** — `Box<String>.get().length()`: o call-site emitia o
     tipo SUBSTITUÍDO (`()Ljava/lang/String;`) como descriptor e, quando o
     descriptor estava certo, deixava o `Object` apagado na pilha sem o
     `checkcast` → `NoSuchMethodError` / `VerifyError`.
  2. **dentro de condição `if`** — a condição nunca era tipada (diferente de
     `while`/`for`/`assert`), então `resolvedMethods` não tinha entrada e o
     fallback inferia o tipo substituído como descriptor.
  **Fix:** novo `GenericReturnAdapter` dedicado (regra 7) adapta o retorno
  `Object` apagado ao tipo efetivo (unbox para primitivos, `checkcast` para
  referências concretas; JS/Native tratam `KofCheckCast` como no-op) +
  `StatementAnalyzer` IfStmt agora tipa a condição, espelhando
  `while`/`for`/`assert`.
  **Prova (Q0/Q1):** `GenericMethodReturnCastE2ETest` dedicado (4 casos:
  chamada direta, anotação explícita + encadeamento, paridade, condição).
  Pré-fix VERMELHO 4/4 (3× `VerifyError` + 1× `NoSuchMethodError`, via o
  harness de reflexão que contorna o launcher JavaFX); pós-fix 4/4 verde.
  Suíte 4-módulos: só o fail conhecido §205 Native (lane #183) + 13 erros de
  `node` ausente.

### §218 — `n.toHexString()` / `toBinaryString()` em Int emite `invokevirtual "".toHexString()` (classe dona vazia) → `ClassFormatError: Illegal class name ""` (issue #148)

- **Sintoma (medido 14/09 ~13:05 no `d2d025f4`, classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):** um método estilo-extensão
  de Int é emitido com classe dona VAZIA no Methodref, então a classe nunca
  carrega:
  ```kof
  main() {
      var n = 255
      var h = n.toHexString()
      println(h)
  }
  ```
  → `ClassFormatError: Illegal class name "" in class file Default/Main`.
- **javap (medido):** `invokevirtual #13 // Method "".toHexString:()Ljava/
  lang/Object;` — a entrada de classe no constant pool do receptor é `""`.
  Dois sub-bugs: (a) owner = vazio em vez de `java/lang/Integer` (o
  `Integer.toHexString` da JVM é ESTÁTICO — a chamada deveria ser
  `invokestatic` em `Integer`, não é método de instância/extensão de Int no
  JDK); (b) o tipo de retorno some para `Object` (deveria ser `String`), a
  família inversa do §166.
- **Esperado:** OU rotear `n.toHexString()` para `Integer.toHexString(n)`
  (estático, retorno `String`) OU rejeitar com diagnóstico em compile-time —
  nunca emitir `"".toHexString`.
- **Pointer (lane compiler):** a resolução de método-extensão para helpers
  de formatação numérica de Int; quando não há extensão do usuário, cai num
  owner `""` não-resolvido em vez de um estático do JDK ou um erro `SEM`.
  Irmão de §203/§213/§161 (família `as`/cast de descriptor quebrado).
- **Estado:** reproduz no `d2d025f4`; **RE-MEDIDO 15/09 ~18:40 pela lane
  bugs-and-gaps `192.168.100.15` no tip `2fc7d6e0`** (classes frescas,
  `Triage` JVM): `n.toHexString()` (n=255) → `ClassFormatError: Illegal class
  name ""` (exit 1), owner ainda vazio. **ADIADO p/ 0.4.1 pela mantenedora**
  (triagem da #148 em 14/09 09:51: "Correção entra na 0.4.1 — patch de
  estabilização"); ABERTO, não é bloqueador do portão 0.4.0. Fix = a resolução
  do helper de formatação numérica de Int (família de qualificação,
  §203/§166).

### §219 — batch (triagem 14/09): 5 issues abertas cujo código é REJEITADO por falsos positivos de diagnóstico em compile-time (formas legítimas do corpus bloqueadas — diagnóstico honesto, veredito errado, logo não R6-silencioso; cada uma precisa de fix no compiler, não de fix de crash)

Medido 14/09 ~13:05–13:20 no `d2d025f4` com classes FRESCAS (`mvn -o compile`
antes — lição da obsolescência do §206/§207), corpos-exatos das issues
(repros guardados nos comentários das issues):

| Issue | Forma | Veredito hoje | Por que está errado (esperado) |
|---|---|---|---|
| #151 | `if (d is Dog) { }` | `PARSE029: Expected ')'` @5:11 | **Rejeição CORRETA** — `is` NÃO é operador de Kof: está ausente de `Lexer.KEYWORDS` e da gramática (que documenta `instanceof`/`as`/`case Type v`); não existe operador `is` em `training/`, `learn/` nem `docs/`. Verificado `if (d instanceof Dog)` → `true`. **Mas o diagnóstico não é a história toda — o mesmo construto é SILENCIOSO fora do `if` (ver o adendo 15/09 abaixo da tabela).** Adiado p/ 0.4.1. |
| #155 | duas interfaces, `save(): Boolean` segunda | `SEM: println recebeu void` @10 | o segundo método implementado é tipado void (o título da #155 diz SEM033 ordem-dependente) — colisão de typer/`SymbolTableBuilder`, `print()` funciona, `save()` lê void |
| #159 | `String? s; while (s != null) { s.length(); s = nextVal(i) }` | ~~`SEM049 receiver is nullable` @9:27~~ | ✅ CORRIGIDO 15/09 (`9e270e56`, D-NARROW-WHILE) — narrowing agora flui pela condição do `while` e por campos de receiver; `NullSafetyE2ETest` 12/12 (issue fechada com prova 15/09 ~22:05) |
| #160 | `interface Mapper<T> { map(T input): String }` | `PARSE007` @1:17 | interface genérica NÃO parseia (CLASSE genérica parseia bem — §217/#161 compila `Box<T>`) — o ramo de declaração de interface não tem a lista de type-param |
| #141 | `var h = spawn { "ok" }; var r = await h` | `SEM: atribuição void` @3:5 | o `await` de um Handle de `spawn { block }` é tipado VOID (o resultado se perde no typing) — irmão da face §29 já fixada (`var h = spawn {lambda}`), tipagem de resultado do handle no caminho block-lambda |

- **Lição Q4 desta leva:** estas 5 NÃO são bugs silenciosos (recusam
  compilar = diagnóstico honesto) — então são **gaps de suporte / bugs de
  typer**, não crashes R6; prioridade na estabilização = abaixo de §216–§218
  (que quebram programas válidos em RUNTIME silenciosamente). Fixes
  pertencem à lane compiler (regra 6).
- **Pointer-resumo:** parser — `is` em condições (#151), type params de
  interface (#160); typer — tipagem de método multi-interface (#155),
  narrowing de null no fluxo while (#159), tipagem de resultado do Handle-
  await (#141 — perto do lowering §29 de spawn já consertado).

- **ADENDO 15/09 (lane bugs-and-gaps `192.168.100.15`, classes frescas no tip
  `d372242b`): o construto `is` é SILENCIOSO (não só rejeitado) fora do `if` —
  isto REFUTA a ressalva "estas 5 NÃO são bugs silenciosos" para a #151.**
  Medido com o `Triage` na JVM (`is` é um IDENTIFICADOR comum, então o parser
  lê `<expr> is <Type>` como dois tokens que nunca junta):
  ```kof
  main() {                       // (a) face expressão: `is Dog` descartado
      var d = new Dog()          //     r = d, sem diagnóstico, exit 0
      var r = d is Dog           //     → imprime `Dog@14dad5dc`
      println(r)
  }
  main() {                       // (b) face statement: PERDA de statement
      var x = 1                  //     `x is Dog` some E come o statement
      x is Dog                   //     SEGUINTE
      println("one")             //     → imprime só `two`, exit 0
      println("two")
  }
  ```
  - (a) `var r = d is Dog` → `r` = `d`; a cauda `is Dog` é descartada em
    silêncio (idem `var r = 5 is Dog` → `5`).
  - (b) `<ident> is Type` como statement → o statement **e o seguinte** são
    engolidos sem diagnóstico; `println("one")` nunca roda. É uma
    **miscompilação silenciosa de Kof válido**, exatamente a classe R6 que a
    ressalva dizia não ser a da #151.
  - A raiz é o **terminador de statement/expressão**, não a keyword `is`: o
    parser inicia uma declaração type-first no identificador e consome um
    statement seguinte quando não consegue formá-la. `kof check` não reporta.
  - Fronteira regra 6: adicionar um operador `is` seria **feature nova**
    (decisão da mantenedora), então a face *feature* segue adiada p/ 0.4.1; a
    **face de perda silenciosa é bug puro** (um construto que deve ser
    rejeitado) e é corrigível sem mudança de contrato. Dono = lane compiler.
  - **Entrada canônica = §249** (a raiz geral: qualquer `<ident> <ident>` no
    início do statement é aceito em silêncio como `Type name`, e um terceiro
    identificador cascateia numa nested function falsa). Este adendo guarda
    só o veredito específico da #151. O **porquê da decl falsa não ser
    rejeitada é o §251** (tipos declarados nunca são validados).

- **ATUALIZAÇÃO 15/09 ~18:00 (lane bugs-and-gaps `192.168.100.15`, classes
  frescas, `Triage` JVM no tip `69bc0f73`): a face de PERDA SILENCIOSA está
  FECHADA agora — o fix §249/§251 (`5c0da8f0`/`46d56244`) transforma o tipo
  declarado desconhecido em diagnóstico de compile-time, então AMBAS as faces
  do `is` acima passam a ser REJEITADAS no compile em vez de engolidas em
  silêncio:**
  - (a) `var r = d is Dog` → `SEM011: Undefined variable or type: 'is'`;
  - (b) `x is Dog` como statement → `SEM011`, não silencioso;
  - `if (d is Dog)` → segue `PARSE029` (inalterado).
  O bug do "construto que deve ser rejeitado" está, portanto, corrigido (não
  há mais perda silenciosa de statement); a face **feature** (`is` como
  operador real) segue decisão da mantenedora, adiada p/ 0.4.1. Issue **#151
  fechada 15/09**. Também do lote: **#160 (interface genérica
  `interface Foo<T>`) ✅ CORRIGIDA 15/09** (`fb5d0edd`, PARSE007 → parseia +
  superinterface emitido apagado); #155/#141 já fechadas; **#159 (narrowing de
  nulável em while/campo) ✅ FECHADA 15/09** (`9e270e56` D-NARROW-WHILE + `NullSafetyE2ETest`
  12/12) — lote completo.

### §220 ✅ CORRIGIDO 14/09 (`8a38faa4` lane analyzer, re-medido verde 14:38: diagnostico de compile-time substitui o AbstractMethodError de runtime — exatamente o esperado do catálogo) — método `abstract` em classe NÃO-abstrata compila → `AbstractMethodError` em runtime (issue #224)

- **Sintoma (medido 14/09 ~13:45 no `cdda27d9` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  class Broken {
      abstract Int compute()   // a classe NÃO é abstrata
  }
  main() {
      var b = new Broken()     // instanciação aceita
      println(b.compute())     // AbstractMethodError em runtime
  }
  ```
  `new Broken()` + a chamada compilam; em runtime: `AbstractMethodError:
  Receiver class Broken does not define or inherit an implementation`.
  javap: `Main` faz `new Broken` + `invokevirtual Broken.compute()I` —
  `Broken` é classe CONCRETA (sem ACC_ABSTRACT) segurando um método
  ACC_ABSTRACT.
- **Esperado (regra R6 + spec JVM):** OU um diagnóstico em compile-time
  (`SEM: class 'Broken' is not abstract but has abstract method 'compute'`
  — a JVMS diz que tal classe DEVE ser abstrata; a JVM até rejeitaria o LOAD
  em modo estrito) OU a classe fica implicitamente abstrata e `new` é
  rejeitado. Compilar e crashar na primeira chamada é R6-silencioso.
- **Pointer (lane compiler):** validação de classe no typer/sema — onde os
  implementadores de interface são checados por métodos faltantes (o caminho
  SEM043 do §209), a mesma auditoria falta para membros `abstract` de uma
  classe não-abstrata.

### §221 ✅ CORRIGID 14/09 (`769371c2`) — método de instância cujo nome colide com um built-in (`print`) é ENGLUTIDO: `log.print("test")` baixa para `System.out.print` (issue #225)

- **Sintoma (medido 14/09 ~13:45 no `cdda27d9` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):** o método do usuário
  existe na classe mas o sítio de chamada ignora o receptor:
  ```kof
  class Logger {
      void print(String msg) { println("[LOG] " + msg) }
  }
  main() { var log = new Logger(); log.print("test") }
  ```
  imprime `test` — o prefixo `[LOG] ` É PERDIDO. javap: `Logger.print(String)`
  existe na classe, mas `Main.main` emite `invokestatic String.valueOf` +
  `invokevirtual java/io/PrintStream.print` — o built-in `print` sequestrou a
  chamada de membro, receptor descartado. Comportamento silenciosamente
  errado (R6/freeze-4), mesma família do bug 116 (`Native x86: hijack of a
  user method named like a String-op`) mas no backend JVM.
- **Esperado:** `log.print(...)` → `invokevirtual Logger.print`. A tabela de
  built-ins se aplica a chamadas NUAS (`print(x)` em nível de
  expressão/stmt), NUNCA a member-call num receptor tipado que declara o nome.
- **Pointer (lane compiler):** ordem do lowering de member-call — o dispatch
  de built-in/namespace (`MemberCallNamespaces`?) é consultado ANTES da
  tabela de métodos da classe para nomes como `print`/`println`.

### §222 — if-EXPRESSION com BLOCOS de chaves (`if (flag) { "yes" } else { "no" }`) compila como LAMBDAS — o valor atribuído é `Lambda0@...` (issue #228) — ✅ CORRIGIDO 15/09 (issue #228 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~13:45 no `cdda27d9` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  main() {
      var flag = true
      var a = if (flag) { "yes" } else { "no" }
      println(a)     // imprime: Lambda0@7ad041f3 — esperado: yes
  }
  ```
  javap: `Main` faz `new Lambda0`/`new Lambda1` (os dois ramos viraram
  closures `kof.Function0_void`) e `println` recebe um Object — imprime
  `Lambda0@...`. Lixo silencioso (R6). O if-expr PARENTEZADO
  (`if (flag) "yes" else "no"`) funciona; o parser/typer confunde `{...}`
  depois da condição com corpo de lambda na posição de expressão.
  Relacionado: §200 (LCA dos ramos) e efda67b2 (block lambdas) — a
  ambiguidade gramatical `cond { ... }` é resolvida para criar lambda.
- **Decisão esperada (regra 6 p/ a mantenedora):** OU forma de if-expressão
  com chaves (avaliar o bloco como o valor) OU um diagnóstico explícito
  rejeitando-a — nunca compilar para lambdas em silêncio.
- **Pointer (lane compiler):** ramo do parser para if-EXPRESSION onde a parte
  `then` começa com `{` — hoje cai no caminho de produção de lambda.

## ### §223 — método `static` em INTERFACE mantém ACC_ABSTRACT → `ClassFormatError: illegal modifiers 0x409` (issue #230)

- **Sintoma (medido 14/09 ~14:05 no `de38f7b5` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  interface Calc { static Int add(Int a, Int b) { return a + b } }
  main() { println(Calc.add(3, 4)) }
  ```
  compila; no load: `ClassFormatError: Method add in class Calc has
  illegal modifiers: 0x409`. javap: `public static abstract int add` —
  flags ACC_PUBLIC|ACC_STATIC|**ACC_ABSTRACT** SEM Code attribute: o corpo
  emitido pelo parser foi descartado e o flag abstract-implícito dos membros
  de interface foi aplicado a um membro `static` (ilegal desde JVMS 2.9:
  método estático em interface DEVE ter Code e NÃO pode ser abstrato).
- **Esperado:** `Calc.add` → `ACC_PUBLIC|ACC_STATIC` com Code (default
  static method Java 8+); o sítio de chamada já faz `invokestatic Calc.add`
  (correto) — só a emissão da classe está errada.
- **Pointer (lane compiler):** o writer de método de interface que ORA
  ACC_ABSTRACT em todo flag de membro precisa pular membros `static` (e
  `default`) e emitir o Code deles.

### §223 — método `static` em INTERFACE mantém ACC_ABSTRACT → `ClassFormatError: illegal modifiers: 0x409` — ✅ CORRIGIDO 15/09 (issue #230 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~14:05 sobre classes FRESH de `de38f7b5`, dono =
  192.168.100.17 — catalogado, lane compiler):**
  ```kof
  interface Calc { static Int add(Int a, Int b) { return a + b } }
  main() { println(Calc.add(3, 4)) }
  ```
  compila; na carga: `ClassFormatError: Method add in class Calc has
  illegal modifiers: 0x409`. javap: `public static abstract int add` —
  flags ACC_PUBLIC|ACC_STATIC|**ACC_ABSTRACT** SEM um Code attribute: o
  corpo emitido pelo parser foi descartado e a flag implicit-abstract dos
  membros de interface foi aplicada a um membro `static` (ilegal desde
  JVMS 2.9: métodos static em interfaces DEVEM ter Code e NÃO podem ser
  abstratos).
- **Esperado:** `Calc.add` → `ACC_PUBLIC|ACC_STATIC` com o Code attribute
  (Java 8+ default static method); o sítio da chamada já faz
  `invokestatic Calc.add` (correto) — só a emissão da classe está errada.
- **Pointer (lane compiler):** o writer de método de interface que ORa
  ACC_ABSTRACT em toda flag de membro deve PULAR membros `static` (e
  `default`) e emitir o Code deles.

### §224 — descriptor de retorno de interop apagado para `Object`: `sb.append("hello")` emite `append(String)Ljava/lang/Object;` → `NoSuchMethodError` (issue #231) — ✅ CORRIGIDO 15/09 (issue #231 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~14:05 no `de38f7b5` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  main() {
      var sb = new java.lang.StringBuilder()
      sb.append("hello")
      println(sb.toString())
  }
  ```
  `NoSuchMethodError: 'java.lang.Object java.lang.StringBuilder.append(java.lang.String)'`.
  javap: `invokevirtual StringBuilder.append:(Ljava/lang/String;)Ljava/lang/Object;`
  — o método REAL do JVM retorna `StringBuilder`; o compilador INVENTOU o
  retorno `Object` porque `StringBuilder` NÃO está na tabela de assinaturas
  de interop (fallback de classe-desconhecida). A linha
  `toString:()Ljava/lang/String;` ESTÁ correta — prova de que o fallback é
  por-buraco-na-tabela, não global.
- **Família:** mesma raiz do §217/#161 (retorno genérico sem checkcast) e do
  §203 (descriptor montado da fonte errada): o caminho de emissão inventa o
  descriptor em vez de ler da classe real.
- **Esperado (interop-first, R9):** para tipos `java.*`, resolver o descriptor
  por reflexão no JDK (ou carregar a classe em compile-time — o backend JVM
  tem as classes no module path); o fallback `Object` é aceitável só para
  classe de usuário genuinamente desconhecida, e mesmo assim com diagnóstico
  honesto (R6) — `NoSuchMethodError` em runtime é silencioso.
- **Pointer (lane compiler):** o lowering de member-call de interop (o caminho
  que respondeu `toString` pela tabela) não tem `StringBuilder`/`append`;
  encher a tabela trata o sintoma — o fix real é descriptor-por-reflexão p/
  receptores `java.*`.

### §225 — método estático `java.*` com retorno desconhecido: `Double.isNaN(d)` emite `(D)Ljava/lang/String;` → `NoSuchMethodError` (issue #233) — ✅ CORRIGIDO 15/09 (issue #233 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~14:25 no `990e4d06` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  main() {
      var d: Double = 0.0 / 0.0
      println(Double.isNaN(d))   // esperado: true
  }
  ```
  `NoSuchMethodError: java.lang.String java.lang.Double.isNaN(double)`.
  javap: `invokestatic Double.isNaN:(D)Ljava/lang/String;` — o real
  `Double.isNaN` retorna `boolean`; o compilador INVENTOU `String`.
- **Família:** MESMA raiz do §224 (issue #231): um membro `java.*` fora da
  tabela de assinaturas de interop recebe descriptor de retorno fabricado —
  lá era call de instância (`StringBuilder.append` → `Object`), aqui é call
  estática (`Double.isNaN` → `String`). Um único fix (resolver descriptor por
  reflexão para `java.*`, R9/R6) fecha §224 E §225.
- **Pointer (lane compiler):** igual ao §224 — o lowering de member-call de
  interop precisa ler o descriptor do JDK, não adivinhar pelo contexto.

### §226 — `for (var n: Int in lst)` → falso diagnóstico "Undefined variable or type: 'in'" (issue #234) — ✅ CORRIGIDO 15/09 (issue #234 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~14:50 no `7f9eb015` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  main() {
      var lst = new List<Int>(); lst.add(1); lst.add(2); lst.add(3)
      for (var n: Int in lst) { println(n) }
  }
  ```
  `COMPILE FAIL: Undefined variable or type: 'in' [SEM011]` (file/line=0). O
  relator lista TODAS as variantes anotadas falhando identico (`String`,
  `Object`, `Color` sobre `Color.values()`) — sistêmico, não Int-específico. O
  parser/typer le a anotação `n: Int` e depois trata a palavra-chave `in` como
  IDENTIFICADOR expressão. A forma SEM anotacao esta VERDE no mesmo build:
  `for (var n in listOf(1,2,3))` imprime `1|2|3` (v4a, ec=0).
- **Familia:** falso-diagnóstico (mesmo batch do §219/#151-#141: mensagem de
  erro que não descreve o problema real). O for-in anotado OU parseia (a
  anotacao e redundante mas bem-definida) OU recebe PARSE/SEM preciso
  (`type annotation not allowed on for-in variable`) — nunca culpa a
  palavra-chave `in`.
- **Pointer (lane compiler):** parse do cabecalho for-in — o `: Type` consumiu
  o stream de tokens antes do `in` ser casado; o match de `in` precisa casar
  o token KEYWORD, não via resolucao de expressao.

### §227 — chamada a metodo statico SOBRECARGADO é OMITIDA do IR → `println(Fmt.of(10))` não imprime nada util / VerifyError com store em var (issue #235) — ✅ CORRIGIDO 15/09 (issue #235 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~14:50 no `7f9eb015` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  class Fmt {
      static String of(Int n) { return "int=" + n }
      static String of(Double d) { return "dbl=" + d }
  }
  main() { println(Fmt.of(10)) }        // não imprime nada util
  ```
  javap `Main.main`: SOMENTE `valueOf` + `println` — **a instrução
  `invokestatic Fmt.of` ESTA AUSENTE do IR** (titulo da #235, provado aqui);
  o slot da pilha chega null/lixo. Com store em variavel
  (`var r1 = Fmt.of(10); println(r1)`) o MESMO IR quebrado cracha no load:
  `VerifyError: Operand stack underflow` (execucao por reflexao, anti-armadilha
  JavaFX), e o corpo com as 2 chamadas morre antes em `COMPUTE_FRAMES
  (visitMaxs)`: `ArrayIndexOutOfBoundsException: Index -1 ... [COMP002]` — o
  dump IR do relator corrobora exatamente: `KofStoreLocal[type=String]` SEM
  `KofCall[of]` antes (o nó da chamada some ANTES do bytecode — bug de
  lowering/IR, não do ASM). Classes com
  overload ÚNICO funcionam — a resoluo escolhe a entrada, mas o caminho de
  EMISSAO para staticos MULTI-overload descarta o no da chamada.
- **Familia:** dispatch de sobrecarga (0.4.0 §131 p/ metodos) — o lowering de
  call statica do backend JVM para sobrecargas resolve o alvo mas NUNCA emite
  o `invokestatic` (ou emite em ramo perdido do IR). R6-silencioso + irmao do
  crash.
- **Pointer (lane compiler):** emissão de call statica onde o callee tem >1
  candidato com o mesmo nome: o resultado da resolução precisa chegar ao
  emitter de instrução (comparar com o caminho de overload unico que funciona).

### §234 — descriptor de interop montado a partir do TIPO CONCRETO do argumento + retorno fabricado: `String.join(sep, list)` → `(String,ArrayList)Object` → `NoSuchMethodError` (issue #237) — ✅ CORRIGIDO 15/09 (issue #237 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~15:15 no `01d09d7b` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  main() {
      var parts = new List<String>()
      parts.add("a"); parts.add("b"); parts.add("c")
      println(String.join(", ", parts))   // esperado: "a, b, c"
  }
  ```
  `NoSuchMethodError: java.lang.Object java.lang.String.join(String,
  ArrayList)`. javap: `invokestatic String.join:(Ljava/lang/String;
  Ljava/util/ArrayList;)Ljava/lang/Object;`. As sobrecargas REAIS são
  `join(CharSequence, Iterable<? extends CharSequence>)String` e
  `join(CharSequence, CharSequence...)String`. **DOIS defeitos independentes
  num só descriptor**: (1) o 2º PARÂMETRO é o tipo CONCRETO do argumento
  (`ArrayList`) em vez da interface que o método DECLARA (`Iterable`) — o
  compilador derivou o descriptor da classe runtime do argumento, não da
  assinatura do callee; (2) o RETORNO é fabricado como `Object` (real:
  `String`) — o mecanismo do §224/§225.
- **Por que NÃO é só uma face do §225:** §224/#231 (`StringBuilder.append`) e
  §225/#233 (`Double.isNaN`) cada um emite PARÂMETROS CORRETOS e só o retorno
  errado — lá a lista de argumentos do descriptor bate com o método real, só o
  retorno é chutado. #237 quebra o PARÂMETRO também (concreto-vs-interface),
  um buraco de lowering separado. Ambos compartilham a raiz "descriptor
  inventado do contexto em vez de lido do callee": ler a assinatura real `java.*`
  por reflexão conserta parâmetro E retorno de uma vez (R9/R6).
- **Controle (mesmo build):** `listOf("a","b")` (`List` do Kof → ainda
  `ArrayList`) reproduz idêntico → o vazamento do-tipo-concreto-do-argumento é
  sistemático, não one-off.
- **Pointer (lane compiler):** lowering de call de interop — o descriptor deve
  ser os tipos de parâmetro apagados do CALLEE (do método do JDK resolvido),
  nunca as classes do argumento do chamador; e o retorno também do callee.
  Unifica com §224/§225: um fix descriptor-do-JDK fecha #231/#233/#237.

### §229 — método `static` chamado por referência de instância emite `invokevirtual` → `IncompatibleClassChangeError` (issue #239) — ✅ CORRIGIDO 15/09 (issue #239 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~15:30 no `fa8d23f5` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  class Util {
      static Int square(Int n) { return n * n }
  }
  main() {
      var u = new Util()
      println(u.square(4))   // esperado: 16
  }
  ```
  `IncompatibleClassChangeError: Expecting non-static method Util.square(I)I`.
  javap: `invokevirtual Util.square:(I)I` — o membro É `static` (ACC_STATIC
  no class file) mas o sítio de chamada despachou pelo receiver de instância.
  Gêmeo espelhado do §223 (lá: membro `static` guarda ACC_ABSTRACT e morre no
  LOAD; aqui: o opcode da chamada está errado e morre na 1ª execução). Java
  permite `u.square(4)` (delega pra classe) — Kof deve emitir
  `invokestatic Util.square`.
- **Esperado:** lowering de member-call: quando o membro resolvido é estático,
  a expressão do receiver é avaliada (side effects) mas o opcode é
  `invokestatic` com a classe DECLARANTE como owner, não `invokevirtual` com
  a classe do receiver.
- **Pointer (lane compiler):** mesmo caminho de resolução do tratamento de
  flags do §223 — o bit static precisa escolher a família do opcode
  (`invokestatic`/`getstatic`) DEPOIS da resolução do membro, não no parse.

### §230 — campo `static` em INTERFACE irresolvível: `K.VAL` → SEM025 `Cannot resolve field 'VAL' on type 'K'` (issue #238) — ✅ CORRIGIDO 15/09 (issue #238 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~15:30 no `fa8d23f5` com classes FRESCAS, dono =
  192.168.100.17 — só catalogado, lane compiler):**
  ```kof
  interface K { static Int VAL = 42 }
  main() { println(K.VAL) }
  ```
  `COMPILE FAIL: Cannot resolve field 'VAL' on type 'K'` (SEM025,
  file/line=0). Constantes estáticas de interface (implicitamente
  `public static final`) são feature legal do Java e a sintaxe de campo
  parseia (sem erro PARSE) — a TABELA DE MEMBROS da interface no SEM nunca
  recebe os campos estáticos declarados nela. Relacionado: §223 (o mesmo
  emissor de membros de interface trata mal o MÉTODO estático — o caminho de
  FIELDS é o buraco irmão; o fix do §223 precisa cobrir FIELDS também).
- **Esperado:** `K.VAL` → `getstatic K.VAL` (campo emitido com
  ACC_PUBLIC|ACC_STATIC|ACC_FINAL + ConstantValue).
- **Pointer (lane compiler):** registro de membros de interface — campos
  estáticos declarados no corpo da interface precisam entrar na tabela de
  fields do tipo igual aos estáticos de classe.

### §233 ✅ CORRIGIDO 15/09 (estabilizacao do gate, dono = 192.168.100.17) — `NativeStringCompareCrossTest` (lane nat §111) NAO MIGRADO ao contrato `split()→String[]` → 2 reds no portão de release (erro de compilacao)

- **Sintoma (medido 14/09 ~16:40 por um agente da lane docs/development ao
  rodar o modulo kof-cli e varrer relatorios FAILURE POR MODULO — caça Q4;
  dono = 192.168.100.17, catalogado, NAO atacado — lane alheia):**
  `NativeStringCompareCrossTest.riscv64/aarch64StringCompareHashEquals`
  falham no estagio de **compilacao** (nao diff de golden):
  `array não tem método 'get()'; use o operador arr[i]`. O
  `SPLIT_PROGRAM` embutido (NativeStringCompareCrossTest.java:119/122/127/131)
  ainda escreve `a.get(0)`/`a.get(1)` sobre o resultado de `"…".split(",")`,
  mas o contrato do §202 (fechado 14/09 por `602dcbc0`) fez `split()`
  retornar `String[]` e moveu acesso a array pro SUBSCROTO `a[i]`. O
  `602dcbc0` migrou `NativeE2ETest` mas esqueceu este arquivo cross → o teste
  que estava verde 11/09 esta VERMELHO no HEAD (5º red do portao, eram 3).
- **Prova de que o fix e migracao pura de contrato (golden preservado):** o
  MESMO programa com `a[0]`/`a[1]`/`d[0]`/`f[0]`/`f[1]` roda na JVM (`ec=0`)
  produzindo EXATAMENTE o golden `SPLIT_GOLDEN = "2|a|b|1|a|0|1|[]|2|2||a"`
  (medido 16:40, `/tmp/opencode/r292/splitfull.kf`). As 4 edicoes
  `get(N)`→`[N]` mantem as assoes do teste byte-identicas — nao e relaxar
  um teste, e alcancar com o teste uma decisao ja merged.
- **Dono / acao:** o arquivo e §111 (lane nat, fechado 11/09). Dois donos
  validos: quem mergeou `602dcbc0` (a mudanca de contrato deve a migracao do
  seu blast-radius) ou a lane nat. Esta lane (docs/development) cataloga e
  NAO edita teste de outra lane. Fix = 4× `.get(N)`→`[N]` mecanicos no
  SPLIT_PROGRAM + re-rodar os 2 cross (qemu) esperando o golden existente.
  R6/anti-falso-verde: NAO baixar o assert.

### §238 ✅ CORRIGIDO 14/09 (unidade 2c, dono = 192.168.100.17) — join do if-else do decompiler (Fase C degrau 2a): local declarado DENTRO de um ramo emite `var` dentro do `if` e o le apos o join → saida recuperada NAO COMPILA (SEM000 `Undefined variable`)

- **Sintoma (medido 14/09 ~19:50 via Runner2 recompilando a saida
  decompilada de `Comp.class`, dono = 192.168.100.17 — lane
  development/DECOMPILER, o caminho de emissao de `5c944709`+`158c174b`):**
  o mesmo programa do EN acima decompila para um `if/else` REAL (sem stub),
  mas o `var v2` sobe na PRIMEIRA atribuicao, que fica DENTRO do ramo then; o
  ramo else e o `return` pos-join referenciam um `v2` nao declarado →
  recompilar da `SEM000 Undefined variable or type: 'v2'` (2x). O caminho
  `pureIfElse` da 2a emite cada ramo com sua propria visao `declared` na
  posicao errada, e `assign()` so conhece a primeira escrita — nao sabe que a
  variavel ESCAPECE do ramo pelo join.
- **Por que escapou do teste da 2a:** `recoversIfElseWithTailJoinAndRunsIt`
  (DecompileTest:926+) usa `int r = 1;` ANTES do if — o `var r` sobe certo e
  os ramos so ESCREVEM; a forma nao-compilavel so aparece quando o local e
  escritas PRIMEIRO dentro de um ramo (`int s;` sem init). Igual
  `recoversContinueAsEmptyThenJoinAndRunsIt` (`int s = 0;` pre-loop). Nenhum
  teste da 2a cobre "local escrito pela primeira vez nos dois ramos do join".
- **Esperado (lei R6, vinculante):** o decompiler nunca pode emitir saida
  nao-compilavel: ou (a) içar a declaracao para ANTES do `if` (pre-declarar
  `var v2 = 0` default-init no ponto do join, ou declarar com a honestidade
  nao-inicializada do JVMS: a regra da linguagem decide), ou (b) RECUSAR esta
  forma → stub honesto `throw \"body not recovered\"`. Stub que diz UNKNOWN e
  honesto; saida que nao recompila e o pior dos dois (parece recuperado e
  quebra no compile).
- **Repro (honesto):** `/tmp/opencode/w2b/Comp.java` → `Dump2` emite o texto
  nao-compilavel acima; `Runner2` imprime `compile=false` + os 2 SEM000.
  Fix = lane do decompiler (mesma familia de arquivos da 2a).
- **Blast-radius medido (Roi3, 18:20):** o defeito afeta a forma "join 2-succ
  cuja local fundida e escrita primeiro num ramo". NAO afeta `tern`/`shortc`
  (recuperacao diferente) e e INDEPENDENTE da questao do walker (o walker foi
  descartado — a separacao 646 invoke/453 loop vale); um fix de sipush no
  `loadValue` so faria MAIS stubs cairem NESTA forma quebrada (medido:
  `Mid.big` virou nao-compilavel) — logo a §238 e PR-REQUISITO de qualquer
  extensao de cobertura, nao um pensamento tardio.
- **✅ Fix (unidade 2c, mesma sessao):** classe NOVA `StructWalker.java`
  (84 linhas, regra 7 de nomenclatura; `BytecodeStatements.java` cresceu
  537→547 = TOLERADA, o hoist nao engorda p/ >=600) com
  `hoistEscapingLocals(thenB, elseB, ...)`: pre-varre os `xstore` dos DOIS
  ramos; slot escrita e AINDA NAO em `declared` → `var <nome> = <default>`
  ANTES do `if` + `declared.add(slot)`. Default por categoria SEMANTICA:
  istore→`0`, lstore→`0L`, dstore→`0.0`; fstore/astore → RECUSAR o shape
  (return false) p/ stub honesto — float literal drifta (licao bug 62) e o
  tipo de referencia e desconhecido no store; `null` seria inventar
  semantica. Wiring: no `struct()`, o pre-scan roda so QUANDO `pureIfElse`
  ja casou (antes do `if (`); `pureIfThen` nao toca (sem else = sem
  init-escapante javac). Slots pre-declarados (o `int r=1` do E.java) =
  zero mudanca, byte-identico. PROVA Q0 (falha no antigo): os 2 testes novos
  em 2/2 VERMELHOS com o wiring revertido (`expected true but was false`);
  VERDES com o fix. PROVA Q1/Q3: `hoistsEscapingLocalOutOfIfElseBranchesAnd
  RunsIt` — recompila E executa os 3 caminhos (oracle JVM medido `10/21/12`
  do Java original); `refLocalEscapingStaysHonestStubNotBrokenOutput` — a
  face String-escapante agora degrada p/ stub honesto (antes: saida
  quebrada — diff medido antes/depois). SUITE: DecompileTest 66/66 +
  PostDom 6/6 + modulo kof-cli COMPLETO 251/251 VERDE BUILD SUCCESS (21:30,
  relatorios limpos, sem FAILURE de node); lei do diamante VERDE; check_500
  exit=0 (nenhuma classe >=600). O caso `longLocal` fica stub (causa outra —
  return long, nao o hoist; nao regrediu — diff antes/depois so mostra
  bothWrite recuperado + refLocal honesto).
- **Face follow-up (mesma unidade, ~22:05): sipush no `loadValue`.** A
  re-medida anotou que um fix so-de-sipush seria inseguro ANTES do hoist
  (converteria stub em saida quebrada). COM o §238 no lugar, o `loadValue` foi
  espelhado ao `machineRun` (0x11→`short`): `if (a == 30000)` (const >
  bipush-range) deixa de stubar e sai em forma COMPILAVEL + executavel (face
  `Sp.big`: `int r` escapante icado por §238, teste `sipushConstantInTest-
  IsRecoveredAndRuns`, exec 1/2/2 == oracle). E o fechamento de uma
  DIVERGENCIA de passada, nao um ganho de forma nova. DecompileTest 67/67.

### §236 ✅ CORRIGIDO 14/09 (unidade da própria caça ao 5º-red) — `comparisonReturn` dobra shape AMBIGUO para Bool cru: `return a < b ? 1 : 0` num corpo `Int` virava saida NAO-compilavel (SEM010); travou `wideParamsMapToCorrectSlots` quando o typer endureceu

- **Sintoma (medido 14/09 ~20:07 na corrida FULL de `DecompileTest` apos o
  rebase; dono = 192.168.100.17, lane do decompiler):**
  `wideParamsMapToCorrectSlots` ficou vermelho: o texto decompilado passa os
  proprio pinos de string mas `result.success()` falha com `SEM010 Return type
  mismatch: expected int but got bool` em `static Int m(Long arg0, Int arg1,
  Int arg2) = arg1 < arg2`. Repro SEM o decompiler:
  `class T { static Int less(Int a, Int b) = a < b }` → mesmo SEM010 — o
  compilador esta CORRETO; quem emitiu foi o decompiler.
- **Causa raiz (ambiguidade na raiz):** o fold em
  `BytecodeDecoder.comparisonReturn` casa o shape `cmp / iconst_1 / goto /
  iconst_0 / ireturn` e devolve a condicao crua (`arg1 < arg2`, Bool). Esse
  shape e a forma do javac TANTO para `return x > 0` (metodo Bool — o fold cru
  esta certo) QUANTO para `return a < b ? 1 : 0` (metodo Int — o fold cru esta
  ERRADO: descarta o `? 1 : 0`). O descriptor e a unica testemunha — o fold
  IGNORAVA `frame.retType()`. O bug latente veio com o fold (pre-0.4.0); o
  typer mais estrito puxado hoje expôs, e o pino em DecompileTest:1213
  assertava o TEXTO ERRADO havia tempo (a linha de recompile estava la — mas o
  pino de string tambem, entao so falhou quando o SEM010 chegou).
- **Fix (raiz, nao sintoma):** `comparisonReturn` agora PORTA pelo tipo de
  retorno — `Z` → condicao crua (byte-identico ao que ja passava), `I` →
  if-expression `= if (c) 1 else 0` (mesma forma que `ifElseReturn` emite p/
  `a > b ? a : b`), outro → recusar (stub honesto, R6). Pino atualizado p/ o
  texto CORRETO `= if (arg1 < arg2) 1 else 0` com comentario marcando o pino
  como o bug. Teste focado novo
  `comparisonReturnRespectsBoolVsIntReturnType` (Bool + Int comparativo + Int
  unario numa MESMA classe + RECOMPILACAO — string sozinha era como isto se
  escondeu). Prova: DecompileTest 64/64 + PostDom 6/6 frescos 20:55; execucao
  ponta-a-ponta do `V` decompilado (slots empurrados por long) = `1|0|0` ==
  oracle JVM; compile=true.
- **Licao cross-lane (registrada, transparencia cirurgica):** um commit
  style-only de codeql (`8e4b34d3`) que se declarou "sem mudanca semantica"
  chegou com `CoreRegressionE2ETest` 1/99 vermelho, DOCUMENTADO na propria
  mensagem como "marker pre-existente da lane .22" — o red e REAL e esta
  catalogado abaixo como §237; a honestidade de declara-lo e a postura certa.

### §237 ✅ FECHADO 15/09 (medido no remoto puro `3a0826df`: 1/1 VERDE; o `8935c8a7` da lane .15 dizia "Closes #237" e `5e996312` consolidou — registro pelo catalogador, nao fix desta lane) — CoreRegressionE2ETest.stringValueOfCharParity`: `String.valueOf(char)` → `computeStack` devolve profundidade NEGATIVA → `NegativeArraySizeException: -1` no ASM COMPUTE_FRAMES (6º red do portao de release; lane .22)

- **Sintoma (medido 14/09 ~20:41 no reactor do kof-compiler, dono =
  192.168.100.22 pela confissao em `8e4b34d3` — catalogado pelo agente da
  lane do decompiler, NAO atacado):** compilar `string-valueof-char.kf`
  (caminho de coercacao Char→String de `f49ef89b`, 14/09 11:24) morre no
  backend JVM: `erro ASM: NegativeArraySizeException: -1`, fase
  `ASM COMPUTE_FRAMES (visitMaxs)` — o `computeStack` de `JvmLiteralEmitter`
  levou `depth` abaixo de zero nessa sequencia de ops, e o calculo de frames
  explode. Pre-existente desde `f49ef89b` (o teste e de 07/09 e passava la);
  visivel no HEAD pelos pulls do rebase.
- **Pointer (lane .22):** a coercacao emite um op que o modelo de pilha nao
  conta (um consumido-1/produzido-1 ou um dup-classificado-como-store); o
  modelo tem de espelhar o emitter no caminho valueOf(char). O
  `8e4b34d3` reescreveu a cadeia do `computeStack` em switch de padrao e diz
  EXPLICITAMENTE que nao tocou neste red ("semantica preservada... 1 red
  pre-existente") — a causa esta no IR que a coercacao monta, nao no rewrite.
- **Estado do portao apos esta varredura (medido 20:41-20:55):** kof-compiler
  4 arquivos-RELATORIO vermelhos — riscv64/aarch64 CastSaturation (2, residual
  §181), `NativeStringCompareCross` (2, migracao de contrato §233) e
  `stringValueOfCharParity` (1, §237 NOVO); kof-cli VERDE (64 Decompile + 6
  PostDom, e `wideParamsMapToCorrectSlots` de volta a verde pelo fix §236).

§193 — E2E blog (F12): `db.query` cru + `.get("col")`/recursos dentro de handler web derr

### §228 — `List[i] = v` (e o composto `List[i] += v`) era ACEITO mas nunca baixado: JVM `VerifyError` no `aastore`, Native SIGSEGV (exit 139), JS silencioso — ✅ CORRIGIDO 14/09 (exposto por `0ab25887`; fix = lane bugs-and-gaps `192.168.100.15`)

- **Sintoma (medido 14/09 ~13:40 em `9048a366`, classes frescas):**
  ```kof
  main() { var l2 = listOf(1); l2[0] = 9; println(l2.get(0)) }
  ```
  compila sem diagnóstico e então: **JVM** `VerifyError: Bad type on operand
  stack @21: aastore` (frame `stack={ArrayList, integer, integer}`, integer
  não atribuível a `Object`), **Native** exit 139 (SIGSEGV), **JS** exit 0
  (`9`). A face composta `l[0] += 10` quebra igual no JVM (`VerifyError …
  aaload`). Violação regra 5/6: compila e não carrega.
- **Causa raiz:** `ExpressionAssignmentLowerer` trata um alvo `ArrayAccessExpr`
  com `KofArrayStore` cru incondicionalmente (caminho `aaload`/`aastore`).
  #149/#152 (`6d7ac697`) roteou só a LEITURA `l[i]` para `kof_list_get`; a
  ESCRITA nunca foi baixada. Antes ficava mascarada porque `listOf(...)` era
  tipado `List` e caía no SEM054 (rejeitado), enquanto `new List<T>()` era
  `Unknown` e escapava; `0ab25887` (fix #214) mapeou `new List<T>()` para
  `BuiltinTypes.LIST` E isentou `List` do guard SEM054 para manter
  `new List<T>()[i]` funcionando — o que também tornou `listOf(...)[i] = v`
  alcançável, expondo a escrita quebrada.
- **Fix (raiz, `StatementAnalyzer.analyzeAssignmentStatement`):** quando o
  alvo da atribuição é um `ArrayAccessExpr` cujo receiver é uma coleção
  conhecida (String/List/Map/Set, nullable desembrulhado, `Unknown` excluído —
  SG-008), emite **SEM054** apontando para o mutador da coleção
  (`l.set(i, v)` / `m.put(k, v)`) em vez de deixar o store cru de array passar.
  Arrays intactos. É fix de bug (R6), não mudança de contrato: o corpus escreve
  listas com `l.set(0, 9)` (`training/idioms/collections.md:20`); `l[i] = v`
  nunca foi baixado.
- **Alinhamento de teste (`SemanticResolutionTest`):** o antigo
  `subscriptOnCollectionsRejected` ainda listava a LEITURA de List como falha
  esperada (obsoleto desde #149/#152); agora rejeita só as formas de fato
  quebradas (subscript de String/Map/Set + `List[i] = v`), e o novo
  `listSubscriptReadIsSupported` fixa a leitura `l[i]` suportada.
- **Prova (4 alvos):** escrita em array segue funcionando (`a[0]=7` → `8`
  JVM/JS/Native); `l.set(0,9)` → `9` nos 3; leitura `l[1]` → `20` nos 3;
  `l2[0]=9` e `l[0]+=10` → SEM054 com o hint `.set`. `SemanticResolutionTest`
  30/30.
- **Nota de dono (lane `.17`):** o fix vive no `StatementAnalyzer` (não no guard
  do `SemExpressionTyper` de `0ab25887`, para preservar a isenção da leitura).

## §193 — E2E blog (F12): `db.query` cru + `.get("col")`/recursos dentro de handler web derr

> **Renumerado de §189→§193 (14/09, dono = 192.168.100.17):** colisão tripla
> de §189 na varredura (record-nullable da lane `.15` venceu por posição;
> parseOrDefault virou §192). Registro da lane compiler; conteúdo intocado.ubam a conexão com `VerifyError`/`connection closed before headers` — catalogado 14/09 (lane development, dono = 192.168.100.18, descoberto no blog E2E D-SPRING F12)

**Causa raiz (typer × bytecode, família SEM049/SEM048):** o resultado cru de
`db.query(...)` (`List<Map<String,Object>>` no typer) chega ao bytecode como
`Object` — chamar `.get("col")` no row ou passar o resultado direto a um
runtime call (`passwords.verify`) desalinha typer e emitter:
`VerifyError: Bad type on operand stack` (`Lambda1.invoke()Ljava/lang/String;`
— `invokestatic` recebe `java/lang/Object` onde espera `String`). No handler
web, a exceção estoura no invoke e a conexão morre sem resposta
(`kof web connection error: connection closed before headers`), R6 violado —
o cliente recebe `Read timed out`, não diagnóstico.

**Menor repro (medido, `/tmp/opencode/blogrepro/B7.kf`):**
```
app.post("/login") {
    var rows = db.query(h, "select pwhash from users where usr = ?", c.user())
    var rec = rows.get(0)
    var hash = rec.get("pwhash")          // Object no bytecode, String no typer
    if (!passwords.verify(c.password(), hash)) { ... }   // VerifyError
}
```
**Correção da causa raiz (esta unidade):** tipagem real do element-type de
`List` no typer — `new List<Int>()` propaga o type-argument (antes caía em
`BuiltinTypes.LIST` sem `type-args`, e `list[0]`/`get` viravam `Unknown` no
typer mas `ArrayList.get → Object` no bytecode); `map/filter/reduce`
inferred em `MethodCallTyper`. Testes: `i149a-d` (`list[0]` no `println`/
`var`/`String.valueOf`) e `i152a-d` (`r.get(0)`), todos verdes na suíte
completa (1816/0/0).

**Fila ainda aberta (dono: lane compiler):** o `db.query` CRU continuar
expondo `Object` nos values do Map — o handler precisa de `db.query<Record>`
tipado (caminho canônico, usado pelo E2E) ou do `"" + rec.get(...)` como
workaround. O `VerifyError` no bytecode emitido é erro do COMPILER, não do
usuário — diagnostic em compile-time é a meta (regra 6).


### §194 — `for (var c in "abc")` (for-in sobre String/não-coleção) era ACEITO e quebrava de um jeito por target (JVM `VerifyError`, Native SIGSEGV, Script crash, JS iterava) — ✅ CORRIGIDO 14/09 (SEM058; triagem da fila #145 da lane bugs-and-gaps `192.168.100.15`)

- **Sintoma (medido 14/09, HEAD `e238330a`):** `for (var c in "abc")` compilava
  em silêncio e divergia de forma grosseira:
  - **JVM:** `VerifyError: Bad type on operand stack in arraylength` — a classe
    principal **nem carrega** (o emitter trata String como array e emite
    `arraylength` sobre `java/lang/String`).
  - **Native x86:** SIGSEGV (exit 139).
  - **Script:** `Argument is not an array` (erro de runtime).
  - **JS:** itera os chars (`a\nb\nc`) — a **única** que "funciona", logo
    divergência cross-target silenciosa (regra 5 + R6).
- **Corpus/contrato:** `docs/language-reference/statements.md` §5.4 já dizia que
  `for-in` itera **só `List<T>` ou array** ("Sem iterator customizado"; para
  String use `s.charAt(i)`), mas marcava o resto como "Unspecified" — que o R6
  não permite (nunca silencioso). Bug 103/SEM054 é o precedente exato
  (`[]` em coleção → rejeitar em compile-time).
- **Causa raiz:** `StatementAnalyzer` (caso `ForInStmt`) só extraía o tipo do
  elemento para `List`/`ArrayType`; qualquer outro tipo caía em
  `elemType = UNKNOWN` **sem diagnóstico** e o lowering emitia código inválido.
- **Fix (Q0):** guard no frontend semântico único dos 5 alvos
  (`StatementAnalyzer.isNonIterableForIn`) → **SEM058** ("`for-in` só itera
  sobre `List<T>` ou array em Kof; para String use `s.charAt(i)` num loop
  numérico"). Não flagados: `ArrayType` (legítimo), `UnknownType`/
  `Nullable`/`TypeVariable` (podem ser List/array em runtime — SG-008).
- **Prova Q1:** `SemanticResolutionTest.forInNonIterableRejected` (String, Map,
  Set, Int → SEM058) + `forInListAndArrayStillCompiles` (List, array, List
  vinda de função não regridem); **falhava antes** (`assertFalse(success)` deu
  `expected <false> but was <true>` com o guard desligado). Confirmado
  `SEM058` idêntico em JVM/JS/Script/Native via probe 4-target.
- **Arquivos:** `StatementAnalyzer.java` (caso `ForInStmt` + helper
  `isNonIterableForIn`); `SemanticResolutionTest.java`;
  `docs/language-reference/statements.md` §5.4 (Unspecified → SEM058).


### §195 ✅ CORRIGID 14/09 (mesmo `KofBlogE2ETest` verde re-medido 14:35 em `b7fdcb7e`: o único método cobre GETs c/ sessão e POSTs c/ Content-Length — ambas as faces fechadas) — `KofBlogE2ETest` VERMELHO (HEAD `11780dc1`): o teste passou a usar `app.security(...)` (C18) mas os `GET /posts` e `GET /posts/:id` não mandam o header de sessão → 401 (o middleware está CERTO) — ⚠️ TESTE, não produto; Q5 (commit `ab15a30f` subiu com o teste vermelho) — registrado 14/09 (lane bugs-and-gaps `192.168.100.15`; dono = lane `.18`/`.22`, `app.security`)

> **✅ RE-CONFIRMADO 14/09 (dono lane `.18`, DECISÕES §5):** a nota "reads
> públicas" do merge C18 estava desatualizada — a guarda de sessão já rejeita
> toda request fora de `publicPaths` (GET incluído). A execução do §5 ligou o
> **CSRF por default** (quando `app.security()` é configurado) e adicionou
> **`permitAll`** como alias de `publicPaths`; o `KofBlogE2ETest` agora faz o
> double-submit de CSRF nos POSTs (`KofWebE2ETest` 25/25 + blog 1/1 verde).

- **Sintoma (medido 14/09, HEAD `11780dc1`, `mvn -o -pl kof-compiler -am
  -Dtest=KofBlogE2ETest`):** o único teste do blog falha em
  `KofBlogE2ETest.java:228` (passo 6, `GET /posts`) com
  `HTTP/1.1 401 Unauthorized` / `{"error":"unauthorized"}` onde espera `200`.
  O passo 7 (`GET /posts/999`) idem (espera 404, recebe 401).
- **Causa raiz (provada):** o commit `ab15a30f` (`app.security()` C18,
  lane `.22`) adicionou ao app do teste
  `app.security(mapOf("sessionHeader","authorization","publicPaths","/register,/login"))`.
  Com `sessionHeader` configurado, o middleware exige sessão em TODO path que
  não seja público — `/posts` **não** está em `publicPaths`, logo os `GET`
  sem `authorization` são (corretamente) rejeitados com 401. Os `POST /posts`
  (passos 4/5) mandam o token e passam; só os `GET` (6/7) ficaram sem.
  **O middleware está CERTO** (security-by-default); o **teste** ficou
  desatualizado — mesmo padrão do §190 (teste × produto).
- **Prova (Q0/repro):** (a) removendo a linha `app.security(...)` do app do
  teste → **verde**; (b) adicionando `authorization: <token>` aos `GET` 6 e 7
  → **verde** (`Tests run: 1, Failures: 0`). Determinístico (3/3).
- **Menor repro (standalone, `/tmp/opencode/triage/`):** app mínimo
  `web.app` + `app.security(sessionHeader=authorization, publicPaths=/register,/login)`
  + rota `GET /posts` → request sem `authorization` = 401; com o token = 200.
- **Correção (do dono, uma linha por GET):** mandar o header de sessão nos
  `GET` 6/7 do teste **ou** incluir `/posts` em `publicPaths` (decisão de
  design do dono: leitura pública vs protegida). Não aplico por ser arquivo
  EM CURSO de outra lane (regra 2); registro para destravar o dono.
- **Impacto no gate:** a suíte 4-módulos fica **vermelha** por este teste
  até o dono corrigir — bloqueia o critério "suíte verde" de release.
  **Q5:** `ab15a30f` subiu declarando a feature implementada com o próprio
  E2E vermelho — o portão pegou (a lane bugs-and-gaps roda a suíte limpa e
  não confia no "verde" do commit).


### §196 — i18n EN quebrou o guard `ConcurrencyGapsDocTest`: o teste fixava o cabeçalho PT `| Construto |` e a doc canônica virou EN (`| Construct |`) — ✅ CORRIGIDO 14/09 (guard aceita as duas grafias; lane bugs-and-gaps `192.168.100.15`)

- **Sintoma (medido 14/09, HEAD `11780dc1`):**
  `ConcurrencyGapsDocTest.tabelaDeGapsDeclaraTodosOsConstrutosConhecidos` falha
  com `expected: <[awaitTimeout, val r = spawn expr, ...]> but was: <[]>` — a
  tabela é lida VAZIA.
- **Causa raiz:** o commit `f5a0ea41` (i18n lote 6, lane docs `.17`) trocou o
  cabeçalho da tabela de `learn/18-concurrency.md` de `| Construto |` para
  `| Construct |` (a doc canônica é EN desde a frente bilíngue). O `gapRows()`
  do guard detectava o início da tabela por `l.startsWith("| Construto |")`
  — string PT HARDCODED no teste. Sem casar, `inTable` nunca vira `true` e
  `gapRows()` devolve mapa vazio → o `assertEquals(SYMBOLS.keySet(), ...)`
  falha. É regressão cross-lane silenciosa (o i18n não roda a suíte Java).
- **Fix (Q0):** o guard aceita as DUAS grafias do rótulo (`| Construto |`
  PT **ou** `| Construct |` EN). O rótulo é prosa (traduzível); o teste não
  pode fixar um idioma — a correção certa é ser robusto aos dois.
- **Prova Q1:** `ConcurrencyGapsDocTest` 3/3 verde (era 1 falha). Suíte
  4-módulos limpa: a única vermelha restante é o §195 (outra lane).
- **Arquivo:** `kof-compiler/src/test/java/dev/kof/compiler/ConcurrencyGapsDocTest.java`
  (`gapRows`, l.80).
- **Lição Q5:** guard de doc que casa um rótulo TRADUZÍVEL precisa aceitar as
  variantes de idioma — senão a tradução (frente ativa) derruba a suíte sem
  tocar o código.

### §197 — `db.query<Record>` com componente `Int` e coluna `identity` (H2 → `Long`): `kof_json_bind` devolvia o Number CRU → `IllegalArgumentException: argument type mismatch` no read path — ✅ CORRIGIDO 14/09 (dono = lane `.18`, achado ao fechar o blog E2E F12; mesma família do CLOB do `8eb156f4`)

- **Sintoma (medido 14/09, HEAD `a689cbd2`):** o `GET /posts` do app canônico
  (`KofBlogE2ETest.blogEndToEndJvm`) devolvia
  `500 {"error": "handler error: argument type mismatch"}`. As escritas
  (`/register`, `/login`, `POST /posts`) passavam; só o read path quebrava.
- **Causa raiz:** `JvmRuntimeJson.kof_json_bind(Class,generic,Object)` tinha o
  ramo numérico `return value;` — devolvia o `Number` como o driver JDBC o
  entregou. A coluna `id identity` do H2 chega como `Long`; o record
  `Post(Int id, String title, String body)` tem construtor `(int,String,String)`
  e `getDeclaredConstructor(...).newInstance(Long)` lança
  `IllegalArgumentException: argument type mismatch` (reflexão não faz
  narrowing). Só aparecia com componente primário de largura diferente do
  que o driver devolve (o `Post(String?…)` do teste anterior não tinha esse
  campo, por isso o verde).
- **Fix:** `kof_json_bind` COERGE ao tipo do alvo em vez de devolver cru —
  `intValue()/longValue()/byteValue()/shortValue()/floatValue()/doubleValue()`
  com fallback `parse*` para `String`; `Number.class` continua passthrough.
- **Prova:** `KofBlogE2ETest` 1/1 (o `GET /posts` devolve o post criado) +
  `KofDbE2ETest` 16/0/2 + `JvmE2ETest` 35/35. Reproduzido antes do fix com
  `db.query<Post>` sobre H2 mem (o 500 só aparecia no read path).
- **Lição:** binding reflexivo de record precisa **coagir** cada componente
  ao tipo declarado; `Class` do componente + `Number` do driver não bastam.

### §198 — `==` direto em condição de `if`/`if-expr` usava `if_acmpeq` para `Record` em vez de `.equals()` — ✅ CORRIGIDO 14/09 ([issue #188](https://github.com/KofLang/Kof4j/issues/188), dono = lane `192.168.100.22`)

- **Sintoma (issue #188):**
  ```kof
  record Tag(String name)
  main() {
      var t1 = new Tag("hi")
      var t2 = new Tag("hi")
      if (t1 == t2) println("equal") else println("not equal")
  }
  ```
  Imprimia `"not equal"`, enquanto `println(t1 == t2)` imprimia `"true"`.
- **Causa raiz:** `CompilerComparisons.isComparisonShortcut` só desativava shortcut para `String` e `enum`. Para records e classes, retornava `true` no shortcut, gerando `KofConditionalJump` com `operandType` do record → `JvmOpEmitter` emitia `if_acmpeq` (igualdade referencial de ponteiro).
- **Fix:** `CompilerComparisons.isComparisonShortcut` desativa shortcut se `left` ou `right` for record type (`CompilerTypes.isRecordType(...) == true`), forçando a cair no lowering normal de `ExpressionBinaryLowerer` que emite `record.equals(other)`.
- **Prova:** `CoreRegressionE2ETest.recordEqualityInDirectIfCondition` prova `if (t1 == t2)` e `if-expression` com `Tag("hi") == Tag("hi")` avaliando para `true` e `"equal"`.

### §199 — Record destructuring com campos `Double` ou `Long` causava colisão de slots no frame JVM (VerifyError / COMP002) — ✅ CORRIGIDO 14/09 ([issue #187](https://github.com/KofLang/Kof4j/issues/187), dono = lane `192.168.100.22`)

- **Sintoma (issue #187):** Desestruturar um record com campos `Double` ou `Long` em `switch` gerava `VerifyError: Bad local variable type ... Reason: Type top is not assignable to double` ou crash `COMP002`.
- **Causa raiz:** Ao desestruturar os componentes de um record (`case Rect(var w, var h)`), `SwitchExprLowerer` e `SwitchStmtLowerer` incrementavam `localIdx` de 1 em 1 (`localIdx++`), ignorando que `Double` e `Long` são de 2 slots no frame JVM (categoria-2).
- **Fix:** Alocação de slots locais em `SwitchExprLowerer.emitPatternBinding` e `SwitchStmtLowerer` ajustada para avançar `TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1`.
- **Prova:** `CoreRegressionE2ETest.recordDestructuringDoubleAndLong` passando nos targets (JVM e JS).

### §200 — `if-expression` com ramos de tipos primitivos mistos (Int e Double) falhava com VerifyError ou COMP002 — ✅ CORRIGIDO 14/09 ([issue #183](https://github.com/KofLang/Kof4j/issues/183), dono = lane `192.168.100.22`)

- **Sintoma (issue #183):** `var result = if (flag) 1 else 2.0` causava `VerifyError: Bad type on operand stack ... Type java/lang/Number is not assignable to integer`, e `var result = if (flag) 2.0 else 1` causava crash interno de ASM frames `COMP002`.
- **Causa raiz:** O lowering de `IfExpr` e `SwitchExpr` já aplicava boxing in-branch quando `branchTypesDiffer` era verdadeiro (boxing para `Object`), mas `ExpressionTyper.inferExprType` retornava cegamente o tipo do primeiro ramo (`thenType`). Assim, `var result` recebia `Int` (ou `Double`), alocava slot/tipo primitivo e tentava fazer `istore`/`dstore` de uma referência `Object`/`Number`.
- **Fix:** `ExpressionTyper.inferExprType` para `IfExpr` e `SwitchExpr` agora retorna `Object` (`java.lang.Object`) quando `branchTypesDiffer` for verdadeiro, casando o tipo da variável receptora com os valores unificados na pilha.
- **Prova:** `CoreRegressionE2ETest.ifExpressionMixedNumericBranches` prova os dois casos (`1 else 2.5` e `3.5 else 4`) compilando e executando corretamente na JVM e JS.

### §201 — `for`/`for-in` no JS: variável de loop `_forInitVar_*`/`_forInVar` referenciada sem declaração (`ReferenceError`) — ✅ CORRIGIDO 14/09 (introduzido por `75e38d35` #182; causa raiz fixada pela lane bugs-and-gaps `192.168.100.15`)

- **Sintoma (JS):** `ReferenceError: _forInitVar_3 is not defined` /
  `_forInVar is not defined` — o programa roda no JVM mas quebra no JS.
- **Repro mínimo:** `ArrayBoundsStressTest#stress007_recoversCleanlyAfterRejectedAccess`
  e `#stress003to008and017_mixedIndexSeveralSeeds` (linha JS), `ArrayBoundsDeepStressTest#deepStress003_*`,
  `BackendParityTest#parityCrossTargetGroupA` (break-continue). 4–5 vermelhos.
- **Bisseção (provada):** `75e38d35` RED ×2 / `b3ab9858`+codemod e `75e38d35~1` GREEN.
- **Causa raiz (medida):** o fix #182 renomeia, na saída do loop, a entrada de
  `locals` para `#forInitVar`/`#forInVar` para liberar o nome original ao
  escopo externo (correto no JVM/Native/Script, que resolvem por slot/índice).
  O backend JS, porém, resolve por NOME e trata **qualquer** local cru com
  prefixo `#` como compiler-temp descartável (`JsExpressionParser.isCompilerTemp`,
  usado por `JsExpressionStatementParser`): o store da variável de loop entra
  no `preamble` e é **descartado** quando o próximo op é um `if`
  (`parseIfBody` retorna sem o preamble) → a variável é referenciada sem
  declaração. Só quebra quando o corpo do loop começa com `if`/bloco complexo
  (o caso `while` simples escapava por acaso). Mesmo mecanismo afetava
  `#scopedVar$…` do fix #203 (`aadc0176`).
- **Fix (root):** `isCompilerTemp` deixa de tratar `#forInitVar`/`#forInVar`/
  `#scopedVar$…` como temporários — são renames de variáveis de USUÁRIO com
  binding persistente e precisam ser declaradas no JS. Os temporários reais
  (`#retVal`, `#switch`, `#idx`, `#coll`, `#inc`, `#excTmp`…) seguem como antes.
- **Prova:** `CoreRegressionE2ETest.loopBodyLocalsBeforeIfAreDeclaredInJs`
  (verde com o fix; vermelho sem ele — `ReferenceError: _forInitVar_2 is not
  defined`) + `ArrayBoundsStressTest` 15/15, `ArrayBoundsDeepStressTest` 6/6,
  `BackendParityTest` 19/19, `CoreRegressionE2ETest` 75/75. JVM/Native/Script
  não tocados (só o parser JS).

### §202 — `String.split(...).get(i)` → SEM028 "array não tem método get()" — ✅ CORRIGIDO 14/09 (introduzido por `e6e5c9b8`; corpus alinhado por `602dcbc0`, lane `192.168.100.17`)

- **Sintoma:** `Compilation should succeed: [Diagnostic ... code=SEM028]` em
  `KofTimeE2ETest#todayIsoFormatDateIsoIsToday{Jvm,Js,Native}` (o programa
  usa `parts.get(0)` após `today.split("-")`), `CodegenKitchenSinkTest`
  (strings), `NativeE2ETest` (2), `KofScriptStdlibParityTest` e família em
  `ConformanceMatrixTest`. 6–9 vermelhos.
- **Bisseção (provada):** `e6e5c9b8` RED / `75e38d35` GREEN (`git worktree`
  com `mvn -o test -pl kof-compiler -Dtest=...`).
- **Mecanismo:** `e6e5c9b8` deu retorno real ao `split`/`toCharArray`/etc. no
  typer (`CollectionMethodTyper`/`StringMethodRegistry`), então o receiver de
  `.get(i)` virou `Type.ArrayType` e caiu no ramo SEM028 (diagnóstico por
  design: arrays crus não têm `get()`; o idiom é `arr[i]`). Antes o receiver
  era desconhecido e o `.get` passava — os testes antigos eram **verde-falso**
  dependendo do buraco de tipagem.
- **Resolução (decisão regra 6 — manter SEM028, alinhar o corpus):** `602dcbc0`
  alinhou os 4 programas de teste ao contrato documentado (`split` retorna
  `String[]`; acesso é `arr[i]` — `training/idioms/strings.md:132`, e o guard
  `CompilerDriverTest.arrayMethodCallGivesCleanDiagnostic` exige SEM028 em
  `arr.get()`). A alternativa "aceitar `.get` no caminho semântico" foi rejeitada
  (quebraria o guard + o contrato). **Verificado 14/09 em `79bd7ac6`:**
  `KofTimeE2ETest` 30 (0 fail, 6 skip = DB externa), `NativeE2ETest` 65/65,
  `CodegenKitchenSinkTest` 1/1, guard SEM028 1/1.
- **Residual (NÃO é §202, catalogado):** `MethodCallTyper.java:18` (lado do emit)
  ainda ACEITA `.get(i)`/`.size` em array enquanto `SemMethodCallTyper` rejeita
  `.get` com SEM028 — inconsistência latente typer/emit: quando o tipo do receiver
  só é cravado no emit (Unknown no sema), um `.get` de array ainda pode escapar e
  emitir AALOAD silenciosamente. Inofensivo hoje (`split` é cravado `String[]`),
  mas é exatamente o buraco que produziu este bug. Dono = lane de inferência.

### §204 — o ramo ELSE do `if`-statement NÃO era analisado → frames JVM inválidos (`Supervisor.lacoUnico` frame crash) — ✅ CORRIGIDO 14/09 (introduzido por `a892b3c5`, lane CodeQL; raiz corrigida pela lane development `192.168.100.18`)

- **Sintoma (JVM):** `Internal compiler error: frame crash em
  Supervisor.lacoUnico (super=java/lang/Object)` — ASM
  `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` em
  `COMPUTE_FRAMES`. 3 vermelhos em `KofSupervisorE2ETest`
  (`supervisorReiniciaWorkerQueFalhaECompleta`,
  `limiteDeReiniciosParaSemEscalarSemCallback`,
  `supervisorS2TresFilhosUmLacoSelectAny`).
- **Bisseção (provada):** `0448ef5d` (pré-merge) GREEN; `ed409ff9` GREEN;
  `752dc5df` (merge que absorve `a892b3c5`) RED; `75e38d35` RED. Worktrees de
  build limpo, `mvn -o test -pl kof-compiler -Dtest=KofSupervisorE2ETest`.
- **Causa raiz (medida):** a limpeza CodeQL `a892b3c5` removeu o binding
  `Type condType = SemExpressionTyper.inferType(...)` **junto com a linha viva**
  `if (ifStmt.elseBranch() != null) analyzeStatement(sa, ..., scope, ...)`. O
  binding era unread, mas a chamada `analyzeStatement` não era: sem ela os
  tipos das expressões do ramo ELSE nunca entram em `sa.expressionTypes()`, e o
  lowering JVM emite um join com frames inconsistentes. O rename
  `#forInitVar`/`#forInVar` (#182) só o tornou visível em `Supervisor.lacoUnico`
  (um `while` cujo corpo tem `if/else` + `spawn`).
- **Fix (raiz):** restaurar o `analyzeStatement` do ramo ELSE em
  `StatementAnalyzer` (o binding `condType` não usado continua removido, como o
  CodeQL pediu). Verificado: supervisor 8/8, mais `BackendParityTest` 19/19,
  `ArrayBoundsStressTest` 15/15, `CoreRegressionE2ETest` 79/79.
- **Teste de regressão (adicionado 14/09 pela lane bugs-and-gaps `192.168.100.15`,
  lacuna Q1):** `CompilerDriverTest.elseBranchIsAnalyzedBothBranches` — um erro
  de tipo (`Int s = "not an int"`) dentro do ramo else deve ser DIAGNOSTICADO
  (SEM021), não emitido como bytecode quebrado. Vermelho sem a análise
  restaurada, verde com ela (provado em build limpo de `origin/beta-0.4.0`). O
  fix da lane `.18` não tinha teste.
- **Nota:** JVM/Native/Script são afetados (o analyzer é target-agnóstico); o JS
  escapou porque o parser resolvia os tipos no seu próprio caminho.

### §205 — `if`-expression heterogêneo imprime `Object` no Native → SIGSEGV (exit 139) — 🟡 PARCIAL 15/09 (caso direto CORRIGIDO, fatia 1; face boxed-print = §104b-ii)

- **Sintoma (Native):** `ConformanceMatrixTest#conformanceCoreControl` caso
  `ifexpr-heterogeneous-direct` (`println(if (s == "") 1 else "s")`) sai 139 no
  Native (JVM/Script/JS ok).
- **Bisseção (provada):** `0448ef5d` GREEN / `ed409ff9` (#183) RED (worktree de
  build limpo, `mvn -o test -pl kof-compiler -Dtest=ConformanceMatrixTest#conformanceCoreControl`).
- **Mecanismo:** #183 faz `inferExprType` de um `IfExpr`/`SwitchExpr`
  heterogêneo devolver `java.lang.Object` (o join Java-like correto). O caminho
  de print em runtime então despacha `println(Object)` e o backend Native
  segfaulta (mesma família do lixo-de-ponteiro de print de coleção do §107: o
  print nativo de um valor boxeado/`Object` não está implementado). O JVM boxeia
  para o tipo do próprio ramo e imprime corretamente, então só o Native diverge.
- **✅ CORRIGIDO 15/09 — fatia 1, caso DIRETO (comportamento real, não gate):**
  a mantenedora rejeitou o rascunho de gate R6 ("gate quebra a regra 5 — mesmo
  comportamento em todos os targets"). O fix rebaixa `println`/`print` de um
  `if`/`switch` heterogêneo **direto** como **print por-ramo**
  (`ExpressionPrintLowerer.lowerHeterogeneousDirect`): a condição/subject é
  avaliada UMA vez, e cada ramo imprime seu próprio valor pelo tipo estático do
  ramo — o dispatch nativo de primitivo/String/record que já existe e é testado
  em x86, riscv64 e aarch64 (aarch64 herda via tradutor). Sem ABI novo, sem
  formato de boxed-object. Prova (medido): as 4 células SIGSEGV
  (`ifexpr-heterogeneous-direct`, `switchexpr-heterogeneous-direct`,
  `ifexpr-intlong-direct`, `ifexpr-longdouble-direct`) agora imprimem
  byte-idêntico ao JVM nas DUAS direções do ramo (medido 8/8:
  `1|s|7|d|1|d|2|d`), + 3 células novas de matriz (`-direct-else`, `-multi`,
  `-multi-default`) e linhas da matriz doc (EN+PT, gate DocTest verde). Q0:
  os MESMOS testes dão exit 139 no build pré-fix (provado por stash + rerun).
  `conformanceCoreControl` 1/1 com Native incluído; vizinhos 46/46.
- **🟡 AINDA ABERTO (fatia 2 = face print do §104b-ii):** `Object` que chega ao
  print NÃO pela sintaxe direta — `var x = if (c) 1 else "s"; println(x)`
  (SIGSEGV — o int bruto na pilha não tem tag) e `println(<primitivo> as
  Object)` (SIGSEGV `7 as Object`; `record as Object` imprime VAZIO vs JVM
  `P[x=1, y=2]`; `String as Object` imprime por sorte). Estes precisam do box
  com tag real + dispatch polimórfico `object_to_string` (a fila de ABI §104b-ii,
  D-NULL/D-NULL-INTENT N2). O fix do caso direto deliberadamente NÃO gateia
  estes: compilam hoje e continuam compilando (regra 2).
- **Contrato (regra 6):** resolvido por implementação (a opção "implementar o
  dispatch Native" do contrato original — a opção de gate foi rejeitada pela
  mantenedora em 15/09 como quebra de paridade). A célula `ifexpr` era verde
  antes do #183 porque o typer devolvia o tipo do THEN e nunca chegava ao
  caminho de print de `Object`.

### §231 — sobrecarga top-level com parâmetro default: `requiredArity` não considera defaults → chamada curta dá SEM014 em vez de selecionar o candidato com default — 🟡 catalogado 14/09 (achado na triagem CodeQL local-variable #160, dono = fila de overload/§131)

- **Sintoma:** `Int h(Int x, Int y = 10) { ... }` + `Int h(String s) { ... }`;
  `h(5)` → `SEM014: Argument 1 of 'h': expected String but got int` (escolhe o
  candidato errado). Com função ÚNICA com default, `h(5)` funciona (caminho
  candidato-único / wrappers de desugar). Medido no CLI 14/09: `o.kf` red,
  `s.kf` → 15.
- **Mecanismo:** o typer de candidatos top-level (`MethodCallTyper:399-404`,
  espelho `ExpressionMethodCallLowerer:461`, `BuiltinCallTyper:408`) calcula
  `seenDefault`/`hasDefaults` e NÃO usa: `new Candidate(fn, pt, pt.size())`
  define `requiredArity = totalArity`, então `pick` só aplica o candidato com
  default quando `nArgs == totalArity`. `TopLevelOverload` por contrato usa
  `requiredArity <= n <= totalArity` — os call-sites têm o fio pela metade.
- **Por que §231 e não um fix aqui:** completar o `requiredArity` MUDA a ordem
  de resolução (contrato §131/§205 congelado, regra 6) — é implementação da
  feature, não limpeza. O comentário em `MethodCallTyper:399` aponta esta seção.
- **Plano de fix (quando a fila de overload atacar):** `requiredArity = índice
  do primeiro default` (já existe o loop `firstDefault` em
  `CompilerClassLowering:360-366` — mesma lógica p/ função top-level), nos 3
  sítios; regressão: um caso `h(Int,Int=) + h(String)` chamando `h(5)` → 15 em
  4 alvos + SEM057 quando ambíguo de verdade.
### §232 — chamada a método de instância com `this` implícito DENTRO da própria classe baixa para `invokestatic Default/Main.<nome>` (função inexistente) → JVM `VerifyError` (face de condição `if`) — ✅ CORRIGIDO 15/09 (issue #225 fechada; repro re-verificado verde no tip 15/09)

- **Sintoma (medido 14/09 ~16:40 sobre classes frescas do rebase de
  `197ac491`; achado na caça Q4 do S3 OTP, dono = 192.168.100.18):**
  ```kof
  class P {
      Bool limite() { return true }
      Void usa() {
          if (limite()) { println("sim") }
      }
  }
  main() { var p = P(); p.usa() }
  ```
  compila (`ec=0`) e morre na carga: `VerifyError: Bad type on operand
  stack @if_icmpne`. Verdade de chão via `javap`: `usa()` emite
  `invokestatic Default/Main.limite:()Ljava/lang/Object;` — mas
  `Default/Main` NÃO tem método `limite` algum (`javap Default.Main`: só
  `main`).
- **Causa raiz:** com `receiver() == null` dentro do corpo da classe, o
  caminho de resolução usado pelo lowerer
  (`SemanticAnalyzer.getResolvedMethod`) está VAZIO desde §140 (o visitor
  `resolveMethodCalls` virou no-op; `resolvedMethods` só é preenchido pelo
  `MemberCallTyper` para chamadas COM receiver). O
  `ExpressionMethodCallLowerer` então cai no ramo de FUNÇÃO top-level, que
  emite `KofCall(mainClassType, name, FUNCTION)` com o tipo de retorno do
  TYPER apagado para `Object` — e nenhuma função top-level assim existe →
  R6 silencioso (compila verde, quebra na carga).
- **Faces:** a instrução `var b = limite()` está OK (um caminho diferente
  baixa para `invokevirtual limite:()Z` — a entender no fix); a face de
  CONDIÇÃO `if (limite())` está quebrada. Mesma família do §227 (overload
  static omitido do IR) — os dois compartilham a raiz "a resolução de
  chamada não-qualificada não está ligada do typer ao lowerer".
- **Workaround (usado no `supervisor-host.kf` S3, funciona):** qualifique
  com `this.` — `if (this.limite())` baixa corretamente (`invokevirtual`).
- **Mini-faces Q4 achadas nesta caça (mesma sessão, não seções próprias —
  repros de 3 linhas, lane compiler):** (a) acesso a array `stamp[i]`
  dentro do corpo de `for (var i in 0..n)` → `PARSE039 Expected field
  name` (interação loop de faixa + subscrito); `while` + subscrito funciona.
  (b) `fn` é palavra reservada: `var fn = ...` → `PARSE037 Expected
  variable name` (inofensivo mas não documentado no fake-idioms). (c) campo
  de tipo-função com o MESMO NOME que um método → `this.campo` resolve como
  o MÉTODO → `SEM015 not a function` na invocação (campo `clock` vs método
  `.clock()` no host S3; contorno via renomeio do campo para `clockFn`).
- **Pointer do fix (lane compiler):** popular `resolvedMethods` para
  chamadas com `receiver() == null` contra a classe envolvente na passada
  semântica (ou resolver direto no `ExpressionMethodCallLowerer` antes da
  queda ao top-level); a emissão de função-wrapper para kind FUNCTION nunca
  deve referenciar método não emitido (assert/diagnóstico).


### §235 — backend JS: chamadas estáticas de wrapper (`Double.isNaN`, `Int.parseInt`, …) emitem um identificador `java_lang_*` indefinido → `ReferenceError`

- **Sintoma (medido 14/09 ~19:20 em classes FRESCAS `cd010bf1`, dono =
  192.168.100.15 — catalogado, lane JS; PRÉ-EXISTENTE, reproduzido
  identicamente em `59359935`, antes da regressão `1e88309b`):**
  ```kof
  main() {
      var d: Double = 0.0 / 0.0
      println(Double.isNaN(d))     // JVM: true
      println(Int.parseInt("42"))  // JVM: 42
  }
  ```
  O JVM compila e roda; o **JS** compila (`success=true`) mas o `.mjs` aborta
  com `ReferenceError: java_lang_Double is not defined` (resp.
  `java_lang_Integer` para `parseInt`/`parseLong`/`parseDouble`). O emitter JS
  monta uma chamada de membro `java_lang_Double.isNaN(...)` — um identificador
  JS que o runtime nunca define.
- **Por que é um gap distinto:** `String.valueOf(...)` É baixado no JS (o
  `JsCallEmitter` tem o ramo String) e o runtime KofJS tem os helpers `parse*`
  (`JsRuntimeOps` mapeia `parseDouble` → as fns existentes do backend) — mas o
  **dispatch** estático de wrapper para `Double`/`Float`/`Int`/`Long`/`Bool`
  está ausente, então a chamada cai na emissão genérica de membro com owner
  estilo Java.
- **Esperado:** `Double.isNaN/isInfinite/isFinite` → predicado JS sobre o
  argumento (`Number.isNaN`, `!Number.isFinite`, …) e `parse*` → os helpers do
  runtime KofJS, espelhando a inferência de retorno BOOL/primitivo do
  `MethodCallTyper` do JVM (#233). Paridade cross-target (regra 5) ou
  diagnóstico explícito (R6) — nunca um `ReferenceError` silencioso em runtime.
- **Prova do gap (Q3):** `WrapperStaticCallsE2ETest.wrapperIsAndParseStatics`
  é intencionalmente **só JVM** — a face JS é esta seção, não um verde-falso.
- **Ponteiro (lane JS):** dispatch de chamada estática do `JsCallEmitter` —
  adicionar o ramo das classes wrapper (`Double`/`Float`/`Int`/`Long`/`Bool`)
  antes da emissão genérica de `JsMember`, espelhando o tratamento de
  receptor-builtin do `ExpressionInstanceCallLowerer`; o
  `JsTypeMapper.jsClassName` nunca pode vazar um identificador `java_lang_*`
  numa posição de chamada.

### §239 — backend JS: `String.format(...)` aborta a compilação com `Internal compiler error: unknown JS expression: null` (COMP002)

- **Sintoma (medido 14/09 ~21:00 em classes frescas, dono =
  192.168.100.15 — catalogado, lane JS; PRÉ-EXISTENTE, reproduzido
  identicamente no origin `aa78eba0` e em `59359935`):**
  ```kof
  main() {
      var s = String.format("Hello %s, age %d", "Alice", 30)
      println(s)
  }
  ```
  O JVM compila e roda após o fix do #156/#216; o **JS** falha na compilação
  com `Diagnostic[severity=ERROR … message=Internal compiler error: unknown JS
  expression: null, code=COMP002]`. Pilha: `JsEmitter.expr:322` ← `:281`
  (`JsMember`) ← `:271` (callee do `JsCall`) ← `emitStatement:109`.
- **Causa raiz:** o backend JS não tem lowering de `String.format`. O
  `KofCall(STATIC, owner=java.lang.String, "format")` produzido pelo lowering
  do lado JVM cai em `isStringOp` → ramo STATIC genérico do `JsCallEmitter`
  (~l.129), que monta
  `new JsMember(new JsIdentifier(JsTypeMapper.jsClassName("java/lang/String")), "format")`.
  `jsClassName` devolve **null** para uma classe do JDK ausente do mapa JS, então
  o alvo do membro é `null` → `JsEmitter.expr(null)` lança. Mesma família do
  §235 (estáticos de wrapper/JDK do JS vazando um owner não mapeado).
- **Esperado:** um helper do runtime KofJS (ex.: `kofStringFormat`) que espelhe
  o contrato JVM `(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;`, ou
  um diagnóstico explícito (R6) — nunca um internal compiler error cru em tempo
  de compilação. Paridade cross-target (regra 5).
- **Prova do gap (Q3):** `StringFormatVarargsE2ETest` é intencionalmente
  **só JVM** — a face JS é esta seção, não um verde-falso.
- **Ponteiro (lane JS):** `JsCallEmitter` — adicionar o ramo `String.format`
  antes da emissão STATIC genérica (empacotar args num array + chamar o
  formatador do runtime) e proteger o ramo STATIC para que um owner JDK não
  mapeado nunca produza um `JsMember` com alvo null.

### §240 — REGRESSÃO: `ExternalClasspath.knows()` agora devolve true para TODA classe `java.*` → builtins do Kof (`String.execute/query/close`, `catch`/`throw`, `indexOf`, `parse*`) deixam de resolver (39 falhas de suíte) — ✅ CORRIGIDO 15/09 (lane bugs-and-gaps `5e996312`; `KofBuiltinJdkSeparationE2ETest` 4/4)

- **Sintoma (medido 14/09 ~23:55 com classes FRESCAS num worktree limpo,
  dono = 192.168.100.15 — catalogado, lane `.22` compiler; NÃO é meu):** a
  bissecção aponta a raiz para `8935c8a7` ("resolve JDK method signatures with
  formal parameter and return types (#237, #231)"). A suíte 4-módulos foi de
  **verde** (`8719e304`/`9115baed`) para **39 falhas** em `kof-compiler`
  (ex.: `SemanticResolutionTest` 2, `QualifiedCatchE2ETest` 2, `KofDbE2ETest`
  10, `KofOrmE2ETest` 14, `KofWebE2ETest` 1, `KofJsE2ETest` 1,
  `KofStringParseTest` 6, `NativeE2ETest` 1, `CoreRegressionE2ETest` 1,
  `ConformanceMatrixTest` 1). Reproduzido identicamente num checkout limpo de
  `origin/beta-0.4.0` (`432cb552`, `mvn clean compile` antes) SEM nenhum dos
  meus commits:
  ```
  SemanticResolutionTest.charArgOnStringMethodRejected:
    esperava SEM051 p/ '"abc".indexOf('c')', foi: [SEM025
    Cannot resolve method 'indexOf' on type 'String']
  QualifiedCatchE2ETest.simpleAndStringCatchStillWork:
    throw exige uma String (exceções são Strings em Kof),
    recebeu RuntimeException, code=SEM026
  KofDbE2ETest.executeAndQueryRowsAsJson:
    Cannot resolve method 'execute' on type 'String' (SEM025)
  ```
- **Causa raiz:** `ExternalClasspath.knows()` ganhou
  `|| JdkReflectionResolver.isJdkClass(internalName)`, então todo tipo
  `java/*`, `javax/*`, `jdk/*` passa a ser reportado como "externo
  conhecido". Consequentemente `MemberCallTyper`/`SemanticAnalyzer.isExternal(String)`
  tomam o caminho de reflexão do JDK para `String` (e tipos de exceção),
  contornando o registro de builtins do Kof: `String.repeat` agora é ACEITO
  (deveria ser rejeitado), `indexOf(Char)` dá SEM025 em vez do contrato
  SEM051, e `throw <objeto de exceção>` / handles de db tipados `String`
  deixam de resolver seus membros builtin. É uma mudança **de contrato** na
  resolução de métodos (regra 6) — o guard `knows()` deve permanecer
  limitado às entries reais do classpath; a reflexão de JDK pertence só ao
  call-site explícito de interop (`resolveMethodWithArgs`), nunca ao
  `knows()`.
- **Esperado:** `knows()` devolve true só para classes realmente no classpath
  externo; o caminho `JdkReflectionResolver` é consultado no call-site de
  interop (`resolveMethodWithArgs`) sem trocar a classificação de "classe
  externa" da qual o registro de builtins depende. A suíte deve voltar ao
  verde (baseline `8719e304` = 1 fail conhecido §205 + 13 erros de node).
- **Ponteiro (lane `.22`, dona do `8935c8a7`):** `ExternalClasspath.knows()`
  (a cláusula `|| isJdkClass`) + os consumidores
  `MemberCallTyper`/`SemanticAnalyzer.isExternal`. O fix deve manter a
  resolução de descriptor do #237/#231 e restaurar a classificação builtin.
- **✅ CORRIGIDO 15/09 (lane bugs-and-gaps `192.168.100.15`,
  `KofBuiltinJdkSeparationE2ETest`):** a separação é `knows()` = entries reais
  **OU** (classe JDK **E NÃO** um tipo builtin do Kof). Novo
  `CompilerTypes.isKofBuiltinJavaLang(internalName)` devolve true para
  `java/lang/String` (a exceção do Kof é String) e para todo subtipo de
  `Throwable` (`RuntimeException`, `java/io/IOException`, …), com cache.
  `ExternalClasspath.knows()` acrescenta
  `&& !CompilerTypes.isKofBuiltinJavaLang(...)` à cláusula `|| isJdkClass`,
  então `SemanticAnalyzer.isExternal` deixa de classificar String/exceções
  como externas — o registro builtin volta a vencer — enquanto classes JDK
  não-builtin (`StringBuilder`, `String.join`) seguem resolvidas por reflexão
  no call-site de interop. **Prova (Q1):** novo teste DEDICADO
  `KofBuiltinJdkSeparationE2ETest` 4/4 — provado **VERMELHO 3/4 pré-fix**
  (`repeat` aceito em vez de SEM052, `indexOf(Char)`→SEM025 em vez de SEM051,
  `throw RuntimeException`→SEM026) e verde pós-fix; o 4º caso (interop
  `StringBuilder`/`String.join`) é verde nas DUAS direções, travando que a
  separação não regride #237/#231. Suíte completa do compiler: **53 fails no
  tip limpo → 15** (os 15 restantes = 14 do §241 + §205, nenhum do §240).
  `check_500` OK.
- **Estado:** ✅ CORRIGIDO — catalogado + corrigido 15/09 pela lane
  bugs-and-gaps `192.168.100.15`. O portão de release não tem mais vermelhos
  do §240.

### §241 — REGRESSÃO (meio-implementada): `c0cf805e` boxa `Nullable(primitivo)` no `JvmTypeMapper.toDescriptor` globalmente mas os CALL-SITES nunca foram atualizados → `VerifyError` no JVM no retorno `Int?` + 14 vermelhos de suíte (coleções/JSON/paridade) + 3 targets para trás (Script/JS/Native seguem unboxed) — ✅ RESOLVIDO 15/09 (lane compiler `6553ac2e` REVERT ao gap honesto; `T?` boxed NÃO é proibido pelo §125 — leitura errada corrigida 15/09 (DECISIONS §7); o revert vale porque o box landou em UMA face sem lockstep dos 4 targets)

- **Sintoma (medido 15/09 ~00:40 no TIP fresco `212a8dbc`, jar auto-compilado com `clean package`, dono = 192.168.100.15 — catalogado; o fix é frente de outra lane; NÃO corrigido aqui):** o contrato do #252 (mantenedora aprovou "aprovo", descritores boxed) entrou via `c0cf805e` (+`432cb552`), mas só o caminho de **campo** funciona. Três faces seguem quebradas:
  1. **JVM, retorno de método `Int?`** — o próprio caso de contrato da issue (`find(Int k): Int? { if (k>0) return k; return null }` e depois `var b = find(-1)`) agora **falha ao carregar**: `VerifyError: Bad type on operand stack — Location: Main.main @4: istore_1; Reason: Type 'java/lang/Integer' not assignable to integer`. O descritor devolve `Ljava/lang/Integer;` mas o STORE/slot local no call-site ainda modela `int` (forma pré-`432cb552`) — PIOR que antes (era valor errado, agora a classe nem carrega).
  2. **Suíte: 14 vermelhos atribuíveis a `c0cf805e` por bisect set-exato** (mesmo conjunto no tip; 0 deles vermelho em `c0cf805e~1`): `KofInterpreterParityTest` ×4, `KofMapSetTest.setMapAsFieldAndReturn`, `JvmE2ETest.execRecordNullablePrimitiveEquals` (**o teste do próprio fixer**), `JsonE2ETest.jvmDecodeMapOfScalars`, `CoreRegressionE2ETest` ×2, `ConformanceMatrixTest` ×3, `BackendParityTest.parityCrossTargetGroupA`, `CodegenKitchenSinkTest.allCodegenConstructsSurviveStrictVerifier`. Causa: `toDescriptor(NullableType)` é GLOBAL — todo slot de coleção / campo JSON que passava `Int?` agora recebe `Ljava/lang/Integer;` enquanto as ops de box/unbox e o interpretador NÃO foram atualizados em lockstep. (`LambdaInGenericContainerE2ETest.valueTypeContainerUnchanged` foi reparado pelo `432cb552` — prova de que o conjunto foi re-medido no tip.)
  3. **Paridade (regra 5 do freeze): Script/JS/Native nunca seguiram o contrato boxed** — mesmo `class Box2 { String? label; Int? count }`: JVM `true|null` ✅ vs Script `false|0` ❌, JS `false|0` ❌, Native `true|0` ❌ (null-check do campo certo, `println(count)` ainda imprime o default primitivo). Três targets divergem em silêncio da face JVM do MESMO programa.
- **Causa raiz:** `c0cf805e` mudou UM mapeamento (`JvmTypeMapper.toDescriptor: case NullableType(primitivo) -> "L…Boxed;"`) e remendou só os sites de leitura/escrita de campo (`ExpressionAssignmentLowerer`, `ExpressionPrintLowerer`, `CompilerEmissionHelpers`, `ExpressionBinaryLowerer`). A mudança é **descritor-global**, mas a compensação (box nos produtores, unbox nos consumidores, largura de slot em `TypeMetrics.isDoubleWidth`/modelo de frame, defaults do interpretador, mapeadores JS/Native) foi feita site a site só para CAMPOS. O call-site de retorno de método e os outros 3 backends ficaram como estavam.
- **Esperado (o contrato aprovado):** `T?` de primitivo = referência boxed no descritor E todo consumidor concorda com isso, identicamente em JVM/Script/JS/Native; campo/parâmetro/local/retorno `Int?` todos guardam `null`; `x == null` é `true` num `Int?` novo; o narrowing `if (x != null)` mantém o fast-path primitivo. A suíte volta a verde.
- **Ponteiro (lane compiler — dono de `c0cf805e`):** OU termina o contrato em todas as faces (modelo de slot do call-site de retorno + Script/JS/Native + os 14 vermelhos) OU reverte `c0cf805e` ao gap honesto (diagnóstico não-implementado, R6) — uma mudança de ABI meio-implementada que quebra o load de programas que antes funcionavam não é nenhuma das duas. Re-medir com os repros DEDICADOS: `class Box { Int? value; Boolean? flag }` + `find(Int k): Int? { if (k > 0) return k; return null }` nos 4 targets.
- **Status:** ✅ RESOLVIDO 15/09 pela lane compiler `192.168.100.22` (REVERT ao gap honesto — ver a entrada CORRIGIDO abaixo). Catalogado 15/09 pela lane bugs-and-gaps `192.168.100.15`; os 14 vermelhos do compiler + o retorno `Int?` que quebra o load eram reais no tip e foram limpos pelo revert. Independente do §240 (commit diferente, raio diferente). O sintoma do `#252`/`#259` volta como gap CONHECIDO e honesto — **corrigido 15/09 pela mantenedora (DECISIONS §7): o revert vale porque o box landou em UMA face (descritor JVM) sem lockstep dos 4 targets, NÃO porque "o box reabre o §125" — essa leitura estava errada. `T?` boxed É o contrato; o trabalho é "completar o box em JVM+Script+JS+Native" (frente §241/#266/#259) — tarefa de implementação NÃO bloqueada pela regra 6, mas abrir a frente como fila é decisão da mantenedora (D-NULL só corrige o escopo).**
- **✅ CORRIGIDO 15/09 (lane compiler, dono `192.168.100.22` — dono do `c0cf805e`): REVERT-para-gap-honesto.** O próprio registro oferecia dois caminhos ("terminar o contrato em todas as faces OU reverter ao gap honesto (R6)"); o **revert** foi escolhido como o correto, porque terminar o contrato boxed **colide de frente com o §125 CONGELADO** (decisão da mantenedora 12/09, opção A: `return null` de `Nullable(primitivo)` → default do primitivo, `0`/`0.0`/`false`, nunca `null` — `CompilerTypes.defaultValueOp` + `KofInterpreterParityTest.printNullablePrimitiveNull` + a célula `nullableprint`). Um retorno `Int?` verdadeiramente boxed teria que tornar `null` um valor de primeira classe em semântica → IR → 4 backends, desfazendo o §125 — decisão de contrato (regra 6), não patch de call-site. Meio-implementar isso (exatamente o que o §241 condena) é o que teria acontecido, então a mudança de ABI é retirada ao gap honesto em vez de embarcar um descritor que quebra o load.
  - **O que foi revertido:** `c0cf805e` (descritor boxed global de `NullableType(primitivo)` no `JvmTypeMapper.toDescriptor` + a compensação só de CAMPO em `ExpressionAssignmentLowerer`/`ExpressionBinaryLowerer`/`ExpressionPrintLowerer`/`CompilerEmissionHelpers`) e `432cb552` (normalização de var local que compensava a mesma mudança), mais o `NullablePrimitiveFieldsE2ETest` (o teste que fixava a face de campo meio-implementada). Restaura a forma pré-`c0cf805e`: campo/param/local/retorno `Int?` mantém o descritor primitivo (o sintoma do `#252` — `b.value == null` false / `b.value` 0 — volta como gap CONHECIDO, ver a issue `#252` e este registro).
  - **Prova (Q1/Q5):** suíte completa dos 4 módulos no tip antes do revert = **15 fails** (14 do §241 + §205); depois = **1 fail**, e esse é o **§205** pré-existente (ifexpr Native `conformanceCoreControl`, lane #183) — **zero vermelhos do §241**. `KofInterpreterParityTest` 25/25, `JvmE2ETest` 35/35, `JsonE2ETest` 18/18, `KofMapSetTest` 14/14, `CoreRegressionE2ETest` 102/102, `BackendParityTest` 19/19, `CodegenKitchenSinkTest` 1/1. `mvn -o -pl kof-compiler -am compile -q` verde.
  - **Residual / follow-up:** o fix real (`T?` de primitivo boxed com semântica de `null` nos 4 alvos) é um **projeto vertical** — **DECIDIDO 15/09 pela mantenedora (DECISIONS §7 D-NULL): ele NÃO reabre o §125 (null boxed `T?` nunca foi proibido; a proibição é *fabricar* o null literal num ponto sem null-safety). É uma frente de implementação NÃO bloqueada pela regra 6 (completar o box nos 4 targets em lockstep) — abrir/quando é decisão da mantenedora.** `#252`/`#259`/`#266`-primário ficam como **gap conhecido honesto**, não como meia-ABI silenciosa, até essa fila landar. A parte (c) (null literal → param primitivo não-nullable = SEM048) é ✅ CORRIGIDA (§250).
  - **✅ N1 IMPLEMENTADO 15-16/09 (lane compiler, dono 192.168.1.2):** desta vez em LOCKSTEP — descritor (`JvmTypeMapper.toDescriptor`), opcode de retorno/local (`JvmLiteralEmitter.returnOpcode/loadVarOpcode/storeVarOpcode`), largura de slot (`TypeMetrics.isDoubleWidth`/`JvmLiteralEmitter.isDoubleWidth`, Long?/Double? agora 1 slot), fold de branch null removido (`CompilerComparisons.foldNullablePrimBranches`), `defaultValueOp` (`return null` → `aconst_null` de verdade), `== null`/`!= null` por referência (`ExpressionBinaryLowerer`, `JvmOpEmitter.isRefOperand`), unbox em aritmética/condição booleana (`ExpressionBinaryLowerer`, `StatementLowerer` if/while), box em argumento de chamada (`CompilerEmission2`), print/concat sem double-box (`ExpressionPrintLowerer`, `ExpressionBinaryLowerer`). **Distinção por FORMA de chamada, não por tipo** (`CompilerComparisons.isCollectionMissSource`/`isGenuineNullablePrimitive`): `Map.get()` continua cru/default-on-miss (SG-008, intocado), só `Nullable(primitivo)` genuíno (retorno/local/param) é boxed. JVM+Script+JS confirmados; Native (N2) segue com o comportamento antigo. Prova: `NullablePrimitiveE2ETest` (novo, #259/#266 diretos) + `ConformanceMatrixTest#nullableprint` (oráculo atualizado). Fecha #259/#266.

### §242 — #256 residual: `checkAbstractClassImplementation` casa o override por NOME+ARIDADE, não por assinatura — sobrecarga de mesma aridade mas tipos diferentes satisfaz um abstrato (AbstractMethodError em runtime volta a existir) — ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`; `ConcreteClassMissingAbstractMethodTest` 6/6)

- **Sintoma (medido 15/09 no tip `08a2feab`, jar fresco; dono = 192.168.100.15 — catalogado; o fix é frente da lane compiler, de `d6101bf9`):** o #256 entrou (`d6101bf9`, `checkAbstractClassImplementation` em `MemberResolver.java`). Rejeita corretamente o exact-repro e os abstratos herdados transitivamente, MAS a comparação de implementação é por `am.name() + "/" + am.parameterTypes().size()` (linha 277) e o candidato concreto é aceito quando `lm.parameterTypes().size() == am.parameterTypes().size()` (linhas 282/289) — **aridade, não tipos**. Uma sobrecarga de mesma aridade e tipos diferentes então satisfaz o abstrato:
  ```kof
  abstract class Svc { abstract Int run(String cmd) }
  class Impl extends Svc { Int run(Int code) { return code } }   // aridade 1, tipo errado
  main() { }
  ```
  Compila limpo (esperado: SEM043 — `run(String)` nunca foi implementado). Em runtime `Svc r = new Impl(); r.run("x")` → `AbstractMethodError`/`NoSuchMethodError`, exatamente a classe de bug que o #256 combate.
- **Causa raiz:** a chave de BFS/dedup usa a QUANTIDADE de parâmetros como proxy de assinatura. O dispatch do interpretador (SG-011B) já tem a ferramenta certa — `TopLevelOverload.sigTag(parameterTypes())` — mas `checkAbstractClassImplementation` compara só `.size()`. Mesma aridade/tipos diferentes é raro (os próprios casos da issue se distinguem por aridade/nome), então é um furo residual, não regressão.
- **Esperado:** um método abstrato é satisfeito só por método concreto com tipos de parâmetro IGUAIS (elemento a elemento, ou `sigTag`-igual), não apenas aridade igual; a chave de dedup leva a assinatura.
- **Ponteiro (lane compiler):** `MemberResolver.checkAbstractClassImplementation` — trocar os guards `== .size()` + a `methodKey` por comparação de assinatura (`TopLevelOverload.sigTag` é o idioma da casa, já usado no dispatch). Adicionar um caso `wrongSignatureDoesNotSatisfy` ao `ConcreteClassMissingAbstractMethodTest` (meu `AbstractMethodImplementationTest.java` abandonado em `/tmp/opencode/my256/` já tem exatamente esse teste a aproveitar).
- **Status:** ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`). Fix: `MemberResolver.checkAbstractClassImplementation` agora chaveia o set de dedup por `am.name() + TopLevelOverload.sigTag(am.parameterTypes())` e casa os candidatos concretos por um novo helper `satisfiesAbstract(concreto, abstrato)` (não-abstrato + `sigTag` igual) — assinatura, não aridade. Prova (Q1, mesmo commit): `ConcreteClassMissingAbstractMethodTest` ganhou `wrongSignatureDoesNotSatisfyAbstractMethod` (o repro exato `Int run(Int)` vs `abstract Int run(String)` → agora SEM043) e `correctSignatureAmongSameArityOverloadsSatisfiesAbstractMethod` (aresta positiva) — **6/6 verde**, e ambos os casos novos provaram o comportamento pretendido num harness fresco de `CompilerDriver` (wrongsig/wrongarity → SEM043; ok/overloadmatch/transitive → sucesso). O #256 fica FECHADO; esta era a aresta residual mais estreita da mesma checagem.

### §243 — classe do usuário com nome de builtin Kof (`List`, `String`, `Set`, `Map`, …) é IGNORADA em todo ponto de uso — o alias builtin vence (IllegalAccessError / NoSuchFieldError) — ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`; `UserClassShadowsBuiltinE2ETest` 7/7)

- **Sintoma (medido 15/09 no tip fresco `5e996312`, jar self-built, launcher por reflexão; dono = 192.168.100.15 — catalogado; o fix é resolução de nomes, regra 6, exige decisão travada):** issue #261. `class List { Int size }` compila e o `List.class` do usuário É gerado, mas todo uso em `main()` resolve para o builtin `java.util.ArrayList`:
  ```kof
  class List { Int size }
  main() { var lst = new List(); lst.size = 5; println(lst.size) }
  ```
  → `java.lang.IllegalAccessError: class Default.Main tried to access private field java.util.ArrayList.size`. O mesmo para `class String { Int length }` → `NoSuchFieldError: Class java.lang.String does not have member field 'int length'`. `javap -c Default/Main.class` mostra `new java/util/ArrayList` + `putfield java/util/ArrayList.size:I` — a classe do usuário nunca é referenciada.
- **Causa raiz:** `CompilerTypes.toType(typeName, currentUnit)` fixa os aliases builtin **antes** de qualquer lookup de classe do usuário: `"List"/"ArrayList"/"LinkedList" → BuiltinTypes.LIST`, `"Set"/"HashSet" → SET`, `"Map"/"HashMap" → MAP`, `"Channel" → CHANNEL` (linhas 27-33), e `exceptionType` fixa `"String" → BuiltinTypes.STRING` (linha 73). Os pins foram adicionados para consertar `new List()`/`new Set()` não-qualificados (#139/#150/#214), mas fazem curto-circuito antes de `qualifyViaImports`/`simpleNamePackage` (que já sabem dar o pacote à classe do usuário). Contraste com o §179: ali o shadowing É preservado porque o builtin de UI só é consultado quando `pkg.isEmpty()` E `!unitDeclaresType` E `sa.getClass == null` — exatamente o guard que estes pins não têm.
- **Esperado:** uma classe/record/enum DECLARADA pelo usuário (mesmo arquivo ou módulo) tem prioridade sobre o alias builtin; o builtin só se aplica quando não existe tipo do usuário com aquele nome. (Coerente com o raciocínio do §179 e com o compilador emitir o `List.class` do usuário.)
- **Ponteiro (lane compiler / dono da resolução de nomes):** em `CompilerTypes.toType(String, CompilationUnitNode[, SemanticAnalyzer])`, guardar cada pin builtin com o mesmo teste de shadowing do §179 — `!unitDeclaresType(currentUnit, typeName) && (sa == null || sa.getClass(typeName) == null)` — antes de retornar o builtin. A sobrecarga de 2 args só tem `currentUnit` (shadowing no mesmo arquivo); o caso módulo-inteiro precisa da sobrecarga com `sa` que `toType(String, unit, sa)` já encadeia. Adicionar teste E2E (`UserClassShadowsBuiltinE2ETest`): `class List` + `class String` com escrita/leitura de campo no JVM.
- **Status:** ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`). Fix (Q0, causa raiz): o guard de shadowing do §179 passou a valer para todo pin de alias builtin. `CompilerTypes.toType(String, unit, external)` delega a uma sobrecarga privada de 4 args com `allowBuiltinPins = !unitDeclaresType(unit, typeName)`; a sobrecarga com `sa` calcula `userDeclaresType(unit, sa, name)` (mesmo arquivo **ou** SymbolTable do módulo) e, quando o usuário declara o nome, devolve o `ClassType` do usuário em vez de cair no `Type.of` (que mapeava `String`→`java.lang.String`); `exceptionType` guarda `String` do mesmo jeito. Os 3 pins duplicados de `NewExpr` (`ExpressionTyper`, `SemExpressionTyper`, `ExpressionLowerer`) agora chamam o novo `CompilerTypes.builtinCollectionType(name, unit, sa)` compartilhado — devolve `null` sob sombra, então a classe do usuário vence (também remove a duplicação; `SemExpressionTyper` 597→590 linhas). Prova (Q1, mesmo commit): `UserClassShadowsBuiltinE2ETest` (DEDICADO, 7 casos) — **provado VERMELHO 4/7 pré-fix** (`List`/`String`/`Set`/`Map` do usuário → `IllegalAccessError`/`NoSuchFieldError`) e verde pós-fix; os 3 restantes são a direção sem-sombra (`new List<Int>()`/`Set`/`Map` + `record Map`) que não pode regredir (#139/#150/#214). JVM+JS medidos. Suíte 4-módulos: 1 fail, o §205 pré-existente (ifexpr Native, lane #183). A #261 fica FECHADA como corrigida.


### §244 — `+` com dois operandos genéricos apagados (`Object`) compila como `iadd` → VerifyError em runtime (ambos apagados a `Object`, sem stringificação) — ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`; `GenericOperandConcatE2ETest` 4/4)

- **Sintoma (medido 15/09 no tip fresco, jar self-built, launcher por reflexão; dono = 192.168.100.15 — catalogado; o fix é da lane compiler, lowering de operador):** issue #267. Quando os dois operandos de `+` são campos de tipo-parâmetro genérico (apagados a `Object`), o backend JVM emite `iadd` em vez de concatenação de string:
  ```kof
  class Pair<A, B> { A first; B second }
  main() { var p = new Pair<String, String>(); p.first = "hello"; p.second = "world"; println(p.first + p.second) }
  ```
  → `java.lang.VerifyError: Bad type on operand stack @ iadd; Type 'java/lang/Object' not assignable to integer`. `javap` mostra `getfield first:Ljava/lang/Object; getfield second:Ljava/lang/Object; iadd`.
- **Causa raiz:** em `ExpressionBinaryLowerer.lower`, `accType`/`rightType` dos campos apagados são `TypeVariable` (ou `ClassType("java.lang","Object")`), que não são `String` nem numéricos, então o ramo aritmético `isNumeric(accType) && isNumeric(rightType)` não casa, o ramo de concat `isString(...)` também não (nenhum operando é `String`), e o controle cai no `else` final onde `operandType` fica o tipo apagado/`Object` mas `KofBinaryOp.ADD` é emitido → `JvmOpEmitter.opcodeForArithmetic` devolve o default int `IADD`. O caso com um operando `String` funciona porque toma o ramo de concat.
- **Esperado:** `+` em que um operando NÃO é numérico deve stringificar os dois lados e concatenar (contrato Kof `String + anything → String`, `training/language/types.md:151`), ou seja o caso genérico-apagado/`Object`/unknown deve tomar o caminho de concat (`String.valueOf` + `kof_string_concat`), casando com o oráculo JVM `"hello" + "world" → "helloworld"`. O `else` de fallback emitindo `ADD` sobre referências é o bug.
- **Ponteiro (lane compiler):** `ExpressionBinaryLowerer` — o `else` final não pode emitir `ADD` numérico quando o tipo do operando é referência/apagado/unknown; rotear pelo caminho de stringificação (ou recusar com diagnóstico se nenhum lado stringifica). Repro nos 4 targets (JVM VerifyError; conferir paridade Script/JS/Native). E2E: `GenericOperandConcatE2ETest`.
- **Status:** ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`). Fix (Q0, causa raiz): o ramo de concat `+` em `ExpressionBinaryLowerer` agora também dispara quando AMBOS os operandos são não-numéricos (`|| (!TypeMetrics.isNumeric(accType) && !TypeMetrics.isNumeric(rightType))`), então um par genérico-apagado/`Object`/unknown toma o caminho de stringificação (`String.valueOf` + `kof_string_concat`) em vez de cair no `else` final que emitia `KofBinaryOp.ADD` sobre referências (`IADD` default do `opcodeForArithmetic`). Prova (Q1, mesmo commit): `GenericOperandConcatE2ETest` (DEDICADO, 4 casos) — **provado VERMELHO 3/4 pré-fix** (o repro exato `Pair<String,String>` → `VerifyError @ iadd`; a face `Pair<Int,Int>`; a paridade interpretador×JVM) e verde pós-fix, com caso de não-regressão para `String+Int`/`Int+Int`/`Double` concretos. Contrato: `String + anything → String` (`training/language/types.md:151`); o interpretador já stringificava (`KofInterpreterOps`), o JVM/JS agora concordam (`helloworld`/`23`). O JS já estava correto (`+` stringifica); o Native não é afetado (caminho de concat). Suíte 4-módulos: 1 fail, o §205 pré-existente (ifexpr Native, lane #183). A #267 fechada como corrigida.

### §245 — `KofConcurrency2Test.stopFlagFieldWriteObservedBySpinReader` estava mal dimensionado: o laço de 500M iterações do leitor espinhoso termina ANTES do `time.sleep(100)` do escritor setar a flag → o teste lê a flag exatamente uma vez e sai (`nao-observou` determinístico em host rápido; flaky em host lento). NÃO é bug do compilador — o fix SG-020 `ACC_VOLATILE` funciona — ✅ CORRIGIDO 15/09 (lane bugs-and-gaps `c48006b1`, só teste; `KofConcurrency2Test` 36/36)

- **Sintoma (medido 15/09 ~06:00 no tip fresco, JDK 25, jar fresco; dono = 192.168.100.15 — catalogado E corrigido aqui, o arquivo está sem dono):** `mvn test` no tip dá **2** vermelhos no compiler, não 1: o conhecido §205 (`ConformanceMatrixTest.conformanceCoreControl`) **mais** `KofConcurrency2Test.stopFlagFieldWriteObservedBySpinReader`. Reproduzido **3/3 em isolamento** (`expected: <observou> but was: <nao-observou>`), então não é flake pontual. A nota do DOING da lane `.18` (`bad95c5e`) afirma que o portão é "1769/2 — **ambos §205**"; o segundo vermelho é, na verdade, este teste.
- **Causa raiz:** o caminho do escritor é `time.sleep(100); estado.pode = true`, enquanto o laço do leitor `while (i < 500000000)` com leitura de campo volátil roda em **~83 ms** neste host (medido com sonda Kof `elapsed=`; 500M iterações, ~0,58 s de wall mas o laço só sai cedo se a flag estiver setada). O leitor completa todo o orçamento de 500M iterações **antes** dos 100 ms do escritor, vê `pode == false` em todas as voltas e imprime `nao-observou`. Em host muito mais lento o leitor sobrevive aos 100 ms e o teste vira `observou` — uma corrida de relógio, não uma falha do modelo de memória.
- **Prova de que o fix `ACC_VOLATILE` em si está correto (a unidade SG-020 `e2291675`):** `javap -p -v Sinalizador.class` mostra `public volatile boolean pode; flags: (0x0041) ACC_PUBLIC, ACC_VOLATILE`; a lambda do leitor faz `getfield Sinalizador.pode:Z` dentro do laço (não hoistado). Com o delay do escritor reduzido para **10 ms** o mesmo programa imprime `observou` **3/3**; a variante com limite `Long` (`i < 20000000000L`, delay 100 ms) imprime `observou` **5/5**. Logo o contrato volátil se mantém — só o orçamento de tempo do teste está errado.
- **Esperado:** o leitor precisa estar comprovadamente vivo quando o escritor vira a flag. Um leitor espinhoso com orçamento fixo de iterações não consegue garantir isso contra um sleep de relógio.
- **Fix (Q0 — causa raiz, mesmo commit da prova):** o orçamento de iterações do leitor espinhoso sobe de `Int` 500M para **`Long` 5B**, para que — com a leitura volátil mantida no laço — o leitor comprovadamente sobreviva ao delay de 100 ms do escritor: medido ~**1,55 s** para 5B = margem de **~15×** (enquanto o laço `Int` de 500M é fechado pelo C2 em ~83 ms — abaixo do sleep de 100 ms). O contador `Long` também exercita o caminho de slot largo. O teste irmão de box capturado (`stopFlagCapturedBoxObservedBySpinReader`) já era robusto (laço sem limite — `while (!parar)`), intocado.
- **Prova (Q1/Q5, mesmo commit):** `stopFlagFieldWriteObservedBySpinReader` fica **3/3 vermelho** com a emissão de `ACC_VOLATILE` desabilitada (`nao-observou`) e **3/3 verde** com ela habilitada, e a classe de 2 testes roda `Tests run: 2, Failures: 0` 3/3. O repro Long-5B (`spin5.kf`) mediu `observou` 5/5 manualmente pelo harness de CLI. Suíte 4-módulos pós-fix = **1 fail**, o §205 pré-existente (lane #183) — ver a medição do portão no DOING.
- **Status:** ✅ FIXED 15/09 pela lane bugs-and-gaps `192.168.100.15` (só teste; nenhum código do compilador tocado). Catalogado + corrigido aqui porque era um portão de release vermelho sem dono `IN PROGRESS`, e o erro de dimensionamento estava no teste, não na feature.

### §246 — acesso encadeado a membro em campo de tipo-parâmetro GENÉRICO (apagado a `Object`) emite owner JVM inválido: acesso a campo → owner `"?"` (`NoClassDefFoundError: ?`), chamada de método → owner `""` (`ClassFormatError: Illegal class name ""`) — ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`; `GenericFieldAccessE2ETest` 6/6)

- **Sintoma (medido 15/09 ~10:00 no tip fresco `f3a34805`, JDK 25, jar self-built, launcher por reflexão; dono = 192.168.100.15 — catalogado; o fix é da lane compiler, substituição de tipo no caminho de acesso a campo/membro):** issue #268. Quando um campo cujo tipo declarado é um parâmetro genérico (`T wrapped` em `class Wrapper<T>`) é lido e em seguida usado como receptor de outro acesso a campo ou de uma chamada de método, o nome da classe dona no bytecode é inválido:
  ```kof
  class Wrapper<T> { T wrapped }
  class Point { Int x }
  main() { var pt = new Point(); pt.x = 42; var w = new Wrapper<Point>(); w.wrapped = pt; println(w.wrapped.x) }
  ```
  → `java.lang.NoClassDefFoundError: ?`. A face de chamada (`w.wrapped.shout()`) → `java.lang.ClassFormatError: Illegal class name "" in class file Default/Main`. Guardar o resultado num local primeiro não ajuda (mesmo owner).
- **Causa raiz:** o tipo estático de `w.wrapped` é o `TypeVariable("T")` cru — nunca substituído pelo type-argument do receptor (`Point`/`Msg`). O case `FieldAccessExpr` de `ExpressionTyper` (worktree `ExpressionTyper.java:177-241`) devolve `fs.type()` sem substituir, e `SemExpressionTyper` (`SemExpressionTyper.java:373` ff) tem o mesmo furo; o helper `CompilerTypes.substituteTypeVariable` (`CompilerTypes.java:348`) existe mas só é chamado por `MethodCallTyper` (linhas 49/466/484), nunca no acesso a campo. Logo os lowerers recebem um `TypeVariable`: `ExpressionLowerer.java:398` (leitura) não resolve para `ClassType` → `KofLoadField(TypeVariable, "x", UNKNOWN)` em `:474`; `ExpressionInstanceCallLowerer.java:221/546` → `KofCall(TypeVariable, …)`. Na emissão, `JvmOpEmitter.java:78-80` (GETFIELD) e `:180-183` (INVOKE) caem em `"?"`/`""` quando o owner não é `ClassType`, e `JvmTypeMapper.toDescriptor(TypeVariable)` → `Ljava/lang/Object;` (`JvmTypeMapper.java:21`).
- **Esperado:** o type-argument é substituído quando o receptor é um `ClassType` parametrizado (`Wrapper<Point>` → tipo do campo `Point`), de modo que o owner é `Point` e o descritor `I`; o acesso encadeado lê/chama na classe real. Mesmo contrato que o caminho de retorno de método já honra (`MethodCallTyper` substitui retornos `TypeVariable`).
- **Ponteiro (lane compiler):** `ExpressionTyper`/`SemExpressionTyper` `FieldAccessExpr` — quando `recvType instanceof ClassType ct` com type-arguments, substituir o tipo declarado do campo via `CompilerTypes.substituteTypeVariable(...)` (idioma do `MethodCallTyper`) antes de devolver; e os lowerers (`ExpressionLowerer`, `ExpressionInstanceCallLowerer`, `MemberCallTyper:122/225/291`) devem resolver um receptor `TypeVariable`/apagado para o `ClassType` substituído em vez de deixá-lo chegar ao emitter. Repro nos 4 targets; E2E: `GenericFieldChainE2ETest`.
- **Status:** ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`). Fix (Q0, causa raiz): o novo `CompilerTypes.substituteTypeVariableIn(memberType, recvType, unit)` substitui `T` pelo type-argument do receptor; `ExpressionTyper`/`SemExpressionTyper` passam a usá-lo para tipos de campo/acessador e `ExpressionLowerer` adapta o load do campo apagado (`checkcast` para referências, `kof_unbox` para primitivos). Prova (Q1, mesmo commit): `GenericFieldAccessE2ETest` (DEDICADO, 6 casos) — **VERMELHO 5/6 pré-fix** (os dois repros encadeados + a face local-depois-acesso), verde pós-fix; o `GenericFieldPrimitiveBoxE2ETest` pré-existente (boxing) segue verde. JVM+JS medidos. Suíte 4-módulos: 1 fail, o §205 pré-existente (ifexpr Native, lane #183). #268 fechada.
### §247 — escrita em campo através de receptor NULÁVEL (`a.child.num = 99`) é aceita em silêncio (sem SEM049, ao contrário da leitura) e emite `putfield Field "?".num:Ljava/lang/Object;` → VerifyError — ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`; `GenericFieldAccessE2ETest` 6/6)

- **Sintoma (medido 15/09 ~10:00 no tip fresco `f3a34805`, JDK 25, jar self-built, launcher por reflexão; dono = 192.168.100.15 — catalogado; o fix é da lane compiler, guarda semântica + unwrap nullable no caminho de escrita):** issue #269. Atribuir a um campo acessado através de um receptor nulável compila **sem diagnóstico** e produz uma classe quebrada:
  ```kof
  class A { B? child }
  class B { Int num }
  main() { var a = new A(); a.child = new B(); a.child.num = 99; println("done") }
  ```
  → `java.lang.VerifyError: Bad type on operand stack @ putfield; Reason: Type integer … not assignable to 'java/lang/Object'`. `javap` mostra `getfield A.child:LB;` e então `putfield Field "?".num:Ljava/lang/Object;` (owner `"?"`, descritor `Object` em vez de `I`). **Assimetria:** a LEITURA `println(a.child.num)` é corretamente rejeitada em compile-time com **SEM049** ("receiver is nullable (T?); narrow first"), e a face de chamada também.
- **Causa raiz (dois furos acoplados):**
  1. **Sem guarda semântica no caminho de escrita.** A checagem de deref nulável da leitura vive em `SemExpressionTyper` (`SEM049`) e a da chamada em `SemMethodCallTyper`; o tratamento de atribuição do `StatementAnalyzer` (worktree `StatementAnalyzer.java:56-77`, `else if (ae.target() instanceof FieldAccessExpr fa)`) só checa imutabilidade de record — nunca roda a checagem de receptor nulável, então `a.child.num = 99` chega ao lowering.
  2. **O lowerer de escrita não desembrulha o receptor.** `ExpressionAssignmentLowerer.java:259` computa `recvType` = `NullableType(B)`; a guarda em `:262` é `instanceof Type.ClassType` (falsa para `NullableType`), então o campo nunca é resolvido (`fieldType` fica `UNKNOWN`) e `KofStoreField(NullableType, "num", UNKNOWN)` é emitido em `:309`. Em contraste, o caminho de LEITURA **desembrulha**: `ExpressionLowerer.java:402` (`if (recvType instanceof Type.NullableType nt) recvType = nt.inner();`). Na emissão, `JvmOpEmitter.java:109-111` cai em owner `"?"` e `JvmTypeMapper.toDescriptor(UNKNOWN)` → `Ljava/lang/Object;` (`JvmTypeMapper.java:25`).
- **Esperado:** a escrita através de receptor nulável é rejeitada em compile-time com **SEM049** exatamente como a leitura (narrow first), OU (se a linguagem decidir o contrário depois) o receptor é desembrulhado e o owner/descritor corretos são emitidos — nunca uma classe quebrada silenciosa. A assimetria leitura-rejeitada/escrita-aceita é o bug.
- **Ponteiro (lane compiler):** adicionar a checagem de receptor nulável ao caminho de atribuição no `StatementAnalyzer` (espelhar o SEM049 do `SemExpressionTyper`), e/ou desembrulhar `NullableType` em `ExpressionAssignmentLowerer.java:259-262` como `ExpressionLowerer.java:402` faz. Repro nos 4 targets; E2E: `NullableReceiverFieldWriteE2ETest` (assert SEM049) — mesma família do §243/§244/§246 (receptor apagado/nulável chegando ao emitter).
- **Status:** ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`). Fix (Q0, causa raiz): os dois furos acoplados fechados — `StatementAnalyzer` agora rejeita a escrita por receptor nulável com SEM049 (espelha a leitura) e `ExpressionAssignmentLowerer` desembrulha o `NullableType` narrowed antes de resolver o campo. Prova (Q1, mesmo commit): `GenericFieldAccessE2ETest` — `writeThroughNullableReceiverIsSem049` VERMELHO pré-fix, verde pós-fix, mais a escrita por local narrowed rodando `99`; `readThroughNullableReceiverStaysSem049` fixa o contrato da leitura. Suíte 4-módulos: 1 fail, o §205 pré-existente (ifexpr Native, lane #183). #269 fechada.

### §248 — gap cross-target do §209/#213: default methods de interface são descartados em silêncio no JS (`TypeError` em runtime) e no Native (imprime `null`, exit 0) — feature só do JVM

- **Sintoma (medido 15/09 no tip fresco `7851f1d4`, jar `kof-cli` self-built; dono = 192.168.100.22 — catalogado, lane compiler):** o programa exato do §209/#213 roda corretamente no JVM mas diverge em silêncio nos outros dois alvos:
  ```kof
  interface Greeter {
      String greet(String name)
      String greetLoud(String name) { return greet(name).toUpperCase() }
  }
  class SimpleGreeter implements Greeter {
      String greet(String name) { return "Hello " + name }
  }
  main() {
      var g = new SimpleGreeter()
      println(g.greetLoud("Alice"))
      println(g.greet("Bob"))
  }
  ```
  - **JVM:** `HELLO ALICE` / `Hello Bob` ✅
  - **JS (`--target js`):** `TypeError: g.greetLoud is not a function` — compila sem diagnóstico; o corpo do default nunca é emitido porque `JsLoweringContext.skipClass` descarta toda classe IR `INTERFACE` (`JavaScript has no runtime interface`) e a classe implementadora não herda o default.
  - **Native (`--target native`):** imprime `null` / `Hello Bob` — o default method resolve para um corpo vazio (devolve o default `null` do retorno `String`) em vez de `HELLO ALICE`; exit code 0, então a divergência é **silenciosa**.
- **Causa raiz:** default methods foram implementados só no lowering JVM (§209, `59e2403b`). O backend JS não tem entidade de runtime para interface (`JsLoweringContext.skipClass` pula classes `INTERFACE`; chamadas de interface baixam para `receiver.method(...)` estrutural), então um default herdado pelo implementador não tem onde viver. O dispatch de interface/instância do Native (`NativeX86Calls:409`, `NativeRiscvCrossOps:374`) também não emite o corpo do default.
- **Esperado (regra 5 do Congelamento, paridade cross-target):** o default method roda nos 3 alvos com a mesma saída, OU o alvo não suportado falha com diagnóstico explícito (R6 `XXX00x`) — nunca divergência silenciosa. Hoje o JS quebra em runtime e o Native devolve valor errado em silêncio.
- **Ponteiro (lane compiler):** (a) JS — emitir o corpo do default numa entidade de runtime alcançável pelos implementadores (ou em cada implementador que não sobrescreve); (b) Native — emitir o corpo do default e resolver a chamada para ele; (c) se um alvo for declarado não suportado, barrar com diagnóstico em vez do caminho silencioso. É um **gap de feature**, não um fix de uma linha (o JS não tem IR de interface) — precisa de decisão de escopo antes de implementar (regra 6).
- **Estado:** 🔴 ABERTO — catalogado 15/09 pela lane compiler `192.168.100.22`. Relacionado: §209 (face JVM ✅ CORRIGIDA), issue #213 (fechada para o JVM). Nenhum teste da suíte cobre default methods no JS/Native (o `InterfaceDefaultMethodE2ETest` dedicado é só JVM), então não é vermelho do portão de release — mas é quebra real de paridade cross-target.

### §249 — dois identificadores seguidos no início do statement são SILENCIOSAMENTE parseados como declaração tipada (`Type name`); um terceiro identificador cascateia numa nested function falsa que engole a chamada seguinte — ✅ CORRIGIDO 15/09 (SEM011 no tipo declarado desconhecido; lane compiler `192.168.100.22`)

- **Sintoma (medido 15/09 no tip `f7ab115f`, classes frescas, `Triage` na JVM — dono = lane compiler; catalogado pela lane bugs-and-gaps `192.168.100.15`):**
  ```kof
  main() {                       // (a) 2 identificadores: decl falsa silenciosa
      var s = "a"                //     `s length` é aceito como `Type name`
      s length                   //     sem diagnóstico, exit 0, imprime one/two
      println("one")
      println("two")
  }
  main() {                       // (b) 3 identificadores: PERDA de statement
      var x = 1                  //     `x is Dog` -> VarDecl(x, is)
      x is Dog                   //     depois `Dog println("one")` -> nested fn
      println("one")             //     => só `two` imprime, exit 0
      println("two")
  }
  main() {                       // (c) face expressão: cauda descartada
      var d = new Dog()          //     `var r = d is Dog` -> r = d
      var r = d is Dog           //     imprime `Dog@14dad5dc`, exit 0
      println(r)
  }
  ```
  Saídas medidas: (a) `one`/`two` (a decl falsa é invisível), (b) só `two` —
  `println("one")` nunca roda, (c) `Dog@14dad5dc`. O `kof check` não reporta
  **nada** nos três.
- **Causa raiz (precisa):** `StatementParser.lookaheadTypedVarDecl`
  (`StatementParser.java:475-518`) devolve `true` para **qualquer** sequência
  `<IDENT> <IDENT>` — só checa que o token depois do tipo é `IDENTIFIER`
  (linha 517), nunca que um declarador válido segue. Então `parseVarDecl`
  (`StatementParser.java:403-429`) é entrado com tipo=`s`/`x`,
  nome=`length`/`is`, não acha `=`, e `expectSemicolon`
  (`ParseContext.java:65-69`) é **no-op** quando o próximo token não é `;` —
  a declaração falsa é aceita sem diagnóstico. Quando um terceiro
  identificador segue, o próximo `parseStatement` vê `<IDENT> <IDENT> (`
  e `lookaheadNestedFunction` (`StatementParser.java:121-124`) aceita como
  nested function (`Dog println(...)`), que consome os argumentos da chamada
  — daí a perda do statement.
- **Correção (causa-raiz, Q0 — lane compiler `192.168.100.22`):** as duas faces
  tinham UMA raiz real, uma camada acima do ponteiro do parser: um tipo
  explícito de `VarDeclStmt` que não resolve para **nenhum tipo conhecido**
  nunca era diagnosticado (`StatementAnalyzer`, case `VarDeclStmt` caía em
  `Type.of(vds.type())` = `ClassType("", name)` sem erro). Por isso o parser
  aceitava `<IDENT> <IDENT>` legitimamente — `Int x` é Kof válido — e o
  `s length`/`Foo x` falso virava uma declaração morta invisível. Fix:
  `MemberResolver.isUnresolvedSimpleType(sa, type, scope)` (espelha as isenções
  de SEM011 do case `IdentifierExpr`: builtin, `List`/`Map`/`Set`/`Channel`/
  `Handle` nu, tipo declarado `kof.ui`/`kof.media` §179, enum, classe/record/enum
  do módulo, classe externa via `--classpath`, type-param no escopo, import
  simples) agora fecha um `SEM011 "Undefined variable or type"` no
  `StatementAnalyzer`. Conservador: só nomes simples (sem `.`/`<`/`?`/`[]`/`(`)
  — as formas compostas são resolvidas por `Type.of`.
- **Prova (Q1, mesmo commit do guard):**
  `UndeclaredVarTypeE2ETest` 10/10 — as 3 faces do catálogo (a) `s length`,
  (b) `x is Dog` com perda de statement, (c) `var r = d is Dog` com cauda
  descartada + a cascata multi-ident `s length junk` são todas rejeitadas com
  `SEM011`; as 6 formas legítimas (`Int x` sem init, classe do módulo, `List`
  nu, type-param genérico, `Label` §179, com inicializador) seguem verdes.
  **VERMELHO pré-fix**: 3/10 falham com o guard do analyzer revertido (medido —
  `Foo x` compilava `success=true`).
- **Nota:** os arquivos do guard foram varridos para dentro do `74e1bed0`
  (mensagem de um commit de runtime de lane concorrente) na árvore de trabalho
  compartilhada; o conteúdo do código está correto e publicado. As 2 faces
  extras (perda de statement com `is` + cauda descartada) são commitadas pela
  lane compiler no follow-up.
- **Esperado:** um statement que não é declaração válida deve ser rejeitado
  com diagnóstico (R6) — nunca aceito em silêncio. Isto **não** é mudança de
  contrato: o corpus não tem forma de declaração `<ident> <ident>`.
- **Face adiada:** um operador `is` de verdade é feature nova → decisão da
  mantenedora (0.4.1); a face de perda silenciosa é bug puro e NÃO precisou
  dessa decisão (agora corrigida).


### §250 — `f(null)` num parâmetro primitivo NÃO-nullable compilava em silêncio e morria no load com `VerifyError` (`aconst_null` contra slot `int`) — ✅ CORRIGIDO 15/09 (SEM048 no `null`; lane bugs-and-gaps `192.168.100.15`; parte (c) da emenda DECISIONS §7)

- **Sintoma (medido 15/09 no tip fresco, jar self-built, launcher por reflexão; caso secundário da issue #266):** `class S { Int len(Int n) { return n } } main() { println(new S().len(null)) }` compilava com **ZERO diagnóstico** nos 4 targets e o JVM morria no load: `VerifyError: Bad type on operand stack — Type 'java/lang/Object' (null) not assignable to integer`. As faces construtor (`new P(null)`) e função top-level (`len(null)`) idênticas. Violação R6 (classe quebrada em silêncio).
- **Causa-raiz:** a proibição do literal `null` (SG-005/SEM048) guardava os caminhos de ATRIBUIÇÃO (`x = null`, `StatementAnalyzer`) e de RETORNO (§125) mas NUNCA o caminho de ARGUMENTO — nenhum check comparava o tipo formal do parâmetro da chamada com um argumento `null` literal, e o `aconst_null` chegava ao slot primitivo.
- **Correção (Q0, causa-raiz):** `SemanticAnalyzer.checkNullArgs` (guard compartilhado) chamado por um pós-pass sobre `resolvedMethods`/`resolvedConstructors` (faces método de instância + construtor) E diretamente no ramo top-level do `BuiltinCallTyper` (os mapas resolvidos nunca veem chamadas top-level — medido: o pós-pass sozinho deixava `len(null)` silencioso). Dispara SOMENTE quando o formal é um `Type.PrimitiveType` pelado + o argumento é o literal `null` → SEM048 com a saída ("declare the parameter 'int?'"). Um formal `NullableType` NUNCA cai aqui — `handle(Boolean? flag)` + `handle(null)` continua legítimo: pela correção da mantenedora em 15/09 (**DECISIONS §7**) o §125/SEM048 proíbe *fabricar null num ponto sem null safety*, NUNCA foi "primitivo não pode ser null" — `T?` boxed com null É o contrato e o box nos 4 targets (§241/#266/#259) NÃO é decisão congelada — abrir a fila é decisão da mantenedora.
- **Prova (Q1, mesmo commit):** `NullArgPrimitiveParamE2ETest` 7/7 — 4 rejeições (instância/construtor/top-level/`Long`) + 2 não-regressões afirmando SEM SEM048 em parâmetros `Boolean?`/`String?` + valor real passa. Arestas Q3: `println(null)` ainda imprime `null`, `null` em parâmetro referência (`String`) inalterado (aceito, o null-check de runtime funciona), `Long` dispara. Q4 cross-target: o diagnóstico é idêntico nos builds JVM/JS/Native e sob `kof run --target script` (frontend compartilhado). Suíte do compiler verde exceto os 2 vermelhos pré-existentes do tip (set-exact sem o diff).
- **Status:** ✅ CORRIGIDO 15/09 (esta unidade, commit `fix(compiler)` da mesma onda). O #266 em si segue ABERTO para a face PRIMÁRIA (parâmetros `Nullable(primitivo)` boxed nos 4 targets — a fila §241/#252).

### §251 — tipos DECLARADOS nunca são validados: um tipo indefinido em retorno/parâmetro compila em silêncio e torna a classe incarregável (`NoClassDefFoundError`); em local/campo é ignorado em silêncio — ✅ CORRIGIDO 15/09 (SEM011 em cada sítio de declaração; lane compiler `192.168.100.22`)

- **Sintoma (medido 15/09 com classes frescas, `Triage` na JVM — dono = lane compiler; catalogado pela lane bugs-and-gaps `192.168.100.15`; visto ao investigar o §249):**
  ```kof
  main() { println("ok") }        // compila SEM diagnóstico
  Foo make() { throw "e" }        // Foo é INDEFINIDO
  ```
  → runtime `NoClassDefFoundError: Foo`, exit 1 (a classe `Default/Main` não
  carrega: o descritor do método `()LFoo;` referencia um tipo inexistente).
  Idêntico para **parâmetro**: `void use(Foo f) {}` → mesmo `NoClassDefFoundError`.
  As faces **local/campo** são silenciosas (sem diagnóstico, sem crash):
  ```kof
  main() { Foo x; println("x") }  // exit 0, sem diagnóstico (local não usado)
  class C { Foo f }               // exit 0, sem diagnóstico
  ```
- **Causa raiz:** nenhuma passada valida que um NOME de tipo declarado resolve.
  O `VarDeclStmt` do `StatementAnalyzer` (`StatementAnalyzer.java:158-167`) monta
  `Type.of(vds.type())` (um `ClassType("", name)`) sem checar contra
  classes/imports/builtins conhecidos; parâmetros/retornos/campos chegam ao
  emitter pelo mesmo `Type.of` não validado. O predicado JÁ existe —
  `SemanticAnalyzer.checkThrowsClause` (`SemanticAnalyzer.java:414-425`, **SEM045**)
  valida NOMES de tipo declarados em `throw X` via
  `knownClasses`/`interfaceNames`/`MemberResolver.qualifyViaImports`; ele
  simplesmente **não é aplicado** aos tipos declarados de var/param/retorno/campo.
- **Esperado (R6):** tipo declarado indefinido vira diagnóstico de compile-time —
  nunca aceite silencioso, nunca crash cru da JVM adiado. **Não é mudança de
  contrato**: o corpus não tem forma de declaração com tipo indefinido.
- **Armadilha (por que não é one-liner):** um check ingênuo "o tipo tem que
  resolver" quebra código legal — VARIÁVEIS de tipo genérico
  (`class Box<T> { T value }`, `T id(T x)`) são tipos declarados que não são
  classes e precisam de whitelist (type params de classe/método em escopo), junto
  de primitivos, `String`, coleções builtin, `kof.ui`/`kof.media` (§179) e tipos
  de forward-ref/import/externos. O fix deve espelhar `checkThrowsClause` e
  adicionar a whitelist de type variables. Notar também que a face de perda de
  statement do §249 é uma consequência de **parser** desta mesma não-validação
  (um `<ident>` indefinido seguido de um `<ident>` é lido como `Type name`), então
  as duas seções se corrigem em camadas diferentes e nenhuma subsume a outra.
- **Estado:** ✅ CORRIGIDO 15/09 (lane compiler `192.168.100.22`).
- **Correção (Q0, causa-raiz):** novo `DeclaredTypeChecker` DEDICADO (regra 7 —
  mantém os quentes `SemanticAnalyzer`/`StatementAnalyzer` bem abaixo do gate de
  500 linhas) percorre todo sítio de declaração — retorno + parâmetros de
  função/método, parâmetros de construtor, campos, componentes de record, campos
  de entity — e chama `MemberResolver.declaredTypeUnresolved`, que decompõe tipos
  compostos (`List<Foo>`, `Foo?`, `Foo[]`, `(Int) -> Foo`) e valida cada nome
  simples pelo MESMO predicado `isUnresolvedSimpleType` do §249, com a
  **whitelist de type variables** que o catálogo avisou (`class Box<T> { T value }`,
  `T id<T>(T x)`, `Pair<K,V>`, `Node<T>?`). Builtins, coleções nuas,
  `kof.ui`/`kof.media` (§179), enums, classes/records/interfaces do módulo,
  imports e tipos externos seguem isentos; nomes qualificados (`a.b.C`) nunca
  são acusados. Dispara do `SemanticAnalyzer.analyze` após `checkNullLiteralArguments`.
- **Prova (Q1, mesmo commit):** `DeclaredTypeValidationE2ETest` 14/14 — 7 faces
  de rejeição (retorno de função, parâmetro, campo, componente de record,
  retorno de método, parâmetro de construtor, type-arg aninhado `List<Foo>`)
  todas SEM011; 7 formas legítimas verdes (classe/função genérica/`Pair<K,V>`,
  classe/interface do módulo, coleções, nullable/array, `Object`/`String`).
  **VERMELHO pré-fix**: 7/14 falham com a ligação do `DeclaredTypeChecker`
  revertida (medido — as 7 faces de rejeição compilavam `success=true`). O
  diagnóstico é idêntico nos builds JVM/JS/Native (frontend compartilhado).
- **Relacionado:** **§249** (a face de perda de statement `<ident> <ident>`,
  corrigida em camada separada — a face LOCAL `VarDeclStmt` é fechada lá);
  §179/§243 (o guard de shadowing que decide quando um nome builtin é tipo do
  usuário). Nenhum teste da suíte cobria tipos declarados indefinidos antes
  desta unidade; não é vermelho do portão.
- **Follow-up (Q4, mesma sessão):** a primeira passada rejeitava os type-params
  de INTERFACES GENÉRICAS (`interface Mapper<T> { T map(T input) }`, #160) como
  SEM011 — a armadilha exata acima — porque o `DeclaredTypeChecker` passava
  `List.of()` em vez de `i.typeParameters()` (o `TypeDeclarationNode` ganhou
  `typeParameters` com a lane #160). Corrigido para whitelistar type-params de
  interface; 2 casos de regressão adicionados (`genericInterfaceTypeVariableAccepted`,
  `genericInterfaceMultiParamAccepted`) → `DeclaredTypeValidationE2ETest` agora
  16/16 + `GenericInterfaceE2ETest` 6/6 verdes.

### §252 — Native x86_64 INTERMITENTE: `KofConcurrency2Test.spawnWorkerThrowPropagatesThroughSelectAnyNative` falha com `Runtime error: array index out of bounds` (issue #273, ~2 em 7 execuções completas do módulo)

- **Sintoma (observado 15/09, lane bugs-and-gaps `192.168.100.15`, rodando o módulo `kof-compiler` COMPLETO no tip `a28657e7`):** o teste
  (adicionado pelo fix §129 `d540957b`, lane development `.18`) espera
  `sel=boom\nok=true` mas o binário nativo imprimiu
  `Runtime error: array index out of bounds` e quebrou o assert:
  ```
  Native output ==> expected: <sel=boom
  ok=true> but was: <Runtime error: array index out of bounds>
      at dev.kof.compiler.KofConcurrency2Test.runNative(KofConcurrency2Test.java:850)
      at ...spawnWorkerThrowPropagatesThroughSelectAnyNative(KofConcurrency2Test.java:824)
  ```
  O programa é `Object falha(){throw "boom"}` + `Int rapida(){return 7}`;
  main dá spawn nos dois, `try { selectAny(a, b); ok=false } catch (String e) { println("sel="+e) }`.
- **Frequência / determinismo (medido, portão 15/09):** **2 falhas em 7 execuções completas do módulo `kof-compiler`** — ocorrência #1 na run 1 (`gate251.log`), depois 4 runs verdes (`gate251b.log`, `gate_full_2`/`a`/`b`), então ocorrência #2 no tip `7a501054` (`gate_7a50.log`, suíte completa 4 módulos: 1847 run / **2 fail** / 16 err / 175 skip — os 2 = §205 alheio + ESTE flake), 1 run verde no meio (`e97f0b08`, 1847/1/16/175, só §205). As duas ocorrências são byte-idênticas: `Native output ==> expected: <sel=boom\nok=true> but was: <Runtime error: array index out of bounds>`, o método que falha levando 0,05x s. **NÃO** reproduziu em:
  - a classe sozinha, 3 execuções seguidas: `KofConcurrency2Test` **40/0** cada;
  - os 2 métodos sozinhos sob carga de CPU (8×), 6 execuções: **2/0** cada;
  - o binário compilado rodado 20×: **20/20** `sel=boom\nok=true`.
  Logo é uma **falha nativa rara e não-determinística**, não um miscompile estável — consistente com uma corrida no caminho novo de handler per-thread do §129 (`kof_exc_chain` em `.tbss`/`%fs:tpoff`) e não com um bug determinístico.
- **Impacto:** deixa o portão da suíte **vermelho de forma intermitente** (risco de `git bisect`/CI: o mesmo commit fica verde 4/5 vezes). A assertion que falha é um **vermelho de TESTE no tip**, não um dos §205 já conhecidos/alheios.
- **Hipótese (não provada):** o caminho `selectAny` que relança um handle excepcional (`handle->exc`) interage com o unwinder per-thread sob corrida perdida — p.ex. o frame de handler do worker ou o slot de exceção do handle é lido antes de o spawner materializá-lo, e o unwind corrompido cai num caminho de bounds-check (`kof_bounds_error`). A causa raiz deve ser investigada **na lane nativa**, reproduzindo o padrão sob carga (um retry limitado no harness força a falha).
- **Issue:** **#273** (aberta 15/09 por esta lane com o repro byte-idêntico + evidência).
- **Ponteiro:** lane development `.18` / `nat` (dona de `RuntimeConcurrency.java`, `RuntimeGc.emitPanic`, `NativeMethodEmitter` do `d540957b`). Dono = **lane nativa** — catalogado aqui, NÃO tocado por esta lane.
- **Estado:** 🔴 ABERTO — catalogado 15/09 pela lane bugs-and-gaps `192.168.100.15`. Relacionado: **§129 [OTP]** (o fix que introduziu este teste/área — o bug-alvo dele está corrigido; isto é um residual intermitente). Não coberto pela exclusão do §205.

### §253 — Callback de `time.interval`/`scheduler.every` não pode se auto-referenciar pela var do handle: SEM011 em todo alvo; ler o handle capturado dentro do job = SIGSEGV no native x86

- **Face A — o analisador rejeita o idiom canônico one-shot (observado 15/09, lane development `192.168.100.18`, tip da toolchain `4af4356f`):** o padrão documentado de self-cancel
  ```
  var id = time.interval(10, () -> {
      time.cancel(id)        // :0:0: error: Undefined variable or type: 'id' [SEM011]
  })
  ```
  falha com **SEM011 em js, jvm e native** — a local que o próprio statement declara não está no escopo da lambda. Idêntico via `scheduler.every(10) { scheduler.cancel(id) }`, logo é regra de escopo do frontend, não bug de backend. O workaround de pré-declarar (`var id = ""; id = time.interval(...)`) parseia em todos os alvos — e cai na face B no native.
- **Face B — SIGSEGV no native x86 (exit 139) quando o job lê a var de handle capturada:** `var id = ""` … `id = time.interval(10, () -> { got = got + id.length })` + `time.sleep(60)` crasha no **native**, passa em js/jvm. O crash não precisa de `cancel` — ler `id.length` dentro do job basta (e a variante self-cancel em `autotest.kf` crasha igual).
- **Matriz (verde salvo indicação):** `core3` Str capturada sem self-ref → 3/3 verde; `e5` Int capturado + reassigned após a criação → verde; `e6` Str capturada + reassigned → verde; `e2` `time.cancel("literal")` dentro do job → verde; `winclose` `w.close()` numa Window capturada dentro do job → **verde nos 3** (a ação do auto-dismiss em si funciona); `r1`/`s1` self-ref → SEM011 ×3; `e1`/`autotest` leitura do handle no job → verde/verde/**SIGSEGV**.
- **Impacto:** o idiom one-shot/cancel-por-id fica inutilizável — é exatamente o blocker do **Toast auto-dismiss** da `kof-ui-widgets` (roadmap UIW008: "Toast auto-dismiss, Spinner/Skeleton animados"). O workaround `spawn { time.sleep(ms); w.close() }` NÃO é paridade (e3: fired=0 — no js o spawn daquele shape não roda sob teste).
- **Hipótese (face B, não provada):** o slot Str capturado do closure native acaba apontando para o temp do handle alocado pelo scheduler (ou o slot do spawner) cujo backing morre/é reusado antes da worker thread (`SCHED001`, thread-por-job via clone) ler — consistente com KofStr pendente dereferenciado na worker. Causa raiz é trabalho da lane nativa; a face A é da lane compiler (família de captura de lambda do §177).
- **Ponteiro:** face A → lane compiler `.22` (escopo da var do inicializador em lambdas); face B → lane development `.18`/`nat` (`RuntimeConcurrency`/`SCHED001`, `kofTimeInterval` no JS). Repros no relatório (`r1`/`s1`/`e1`/`e5`/`e6`/`e2`/`winclose`/`core3`, 10–20 linhas cada).
- **Estado:** 🔴 ABERTO — catalogado 15/09 pela lane development `192.168.100.18`, descoberto ao verificar UIW008/UIW020 contra o `beta-0.4.0` para a `kof-ui-widgets`.
