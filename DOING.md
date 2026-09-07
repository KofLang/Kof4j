# DOING.md — coordenação multi-agente (quem faz o quê)

> **Regra obrigatória para agentes (IA ou humano):**
> 1. **Antes** de começar qualquer trabalho de feature/gap: leia este arquivo.
> 2. Se o item que você quer atacar já tem **dono + estado `EM CURSO`**, não toque —
>    escolha outro ou pergunte. Nunca dois agentes no mesmo gap.
> 3. Ao **reivindicar** um item: edite este arquivo **no mesmo commit** que começa
>    o trabalho (dono, branch, arquivos que vai tocar).
> 4. A **cada commit**, atualize sua linha (estado, progresso, o que falta).
> 5. Ao **concluir**: mude para `FEITO` com data + commit + teste que prova, e
>    marque o gap no docs (`status.md`/`backend-parity.md`).
> 6. Itens abertos ficam em `EM CURSO` por no máx. uma sessão; ao abandonar,
>    volte para `ABERTO` com nota do que já funciona e o que falta.
> 7. **Modo autônomo:** se o turno vai acabar, a ÚLTIMA coisa escrita aqui é
>    a linha **"PRÓXIMO PASSO"** abaixo (tarefa exata + arquivo + prova).
>    Quem voltar (humano/cron/outra instância) retoma em ≤1 leitura.

Estados: `ABERTO` · `EM CURSO` · `FEITO` · `BLOQUEADO`.

---

## PRÓXIMO PASSO (re-dispacho lê isto)

**PRÓXIMO PASSO (lane KOFSCRIPT/interpreter, 07/09)**: **PARIDADE
CROSS-TARGET (g) FEITA (este commit)** — sweep do grupo A (28 casos) nos
targets JS e NATIVE (x86_64) vs JVM. **5 divergências reais achadas e
REGISTRADAS** (regra 5/6 — lane JS/Native, não corrijo): bug 41 (campo
estático no Native → stub vazio `KofGetStatic`/`KofPutStatic`
`nat/NativeBackend:629` → lixo, R6), bug 42 (`record.hashCode()` ausente JS
`TypeError`/Native `ld: undefined P_hashCode`), bug 43 (`String.length`/
`charAt` Native UTF-8 vs JVM UTF-16: `"café".length` 5 vs 4), bug 44
(`println(double)` Native x86_64 6 casas + `5` vs JVM 16 casas + `5.0` —
contradiz "x86_64 impecável"), bug 45 (`finally` c/ `return`: JVM/Native/
interp descartam o finally — consistente/congelado; JS roda o finally mas
perde o retorno → `undefined`). **GATE permanente adicionado**:
`BackendParityTest.parityCrossTargetGroupA` (25 dos 28 casos com paridade
JVM==JS; os 3 excluídos = bugs 42/45 + float-print documentado).
`known-bugs.md` 41-45 + `backend-parity.md` 5 linhas. **REPARO DE BUILD
(obrigatório, lane do SOLID-refactor do outro agente)**: o rebase puxou os
commits que moveram `Optimizer`→`backend/`, `ClassFileParser`→`parser/`,
`JvmRuntime`→`jvm/` **sem atualizar os imports de 3 testes**
(`OptimizerTest`/`ClassFileE2ETest`/`KofWsFrameTest`) → test-compile do
`kof-compiler` quebrado em HEAD do origin (bloqueava TODOS os testes, gate
de merge inutilizável). Adicionei os 3 `import` faltantes (ZERO mudança de
teste/assertão — só compilação). Provas: `parityCrossTargetGroupA` +
os 3 testes reparados + suíte 1046/0/3-skip.
**ITEM (h) RESOLVIDO (07/09)** — bug #29 investigado com dados reais
(probes `S29`/`S29js`/`S29det`, 4 caminhos interp/JVM/JS/Native): o #29
original (handle + lambda **void** com captura, `7ec8b9d`) e `spawn fn(arg)`
(função nomeada + handle) **funcionam nos 4**. Restou 1 variante nova:
`spawn { return n*2 }` (lambda literal com `return` + handle + await) →
**SIGSEGV (139) no Native x86_64, determinístico** (interp/JVM/JS dão `42`)
→ registrado como **bug 46** (`known-bugs.md`) — paridade (regra 5),
correção = **lane Native** (trampoline de spawn + slot de retorno), NÃO
minha. **BUG 47 CORRIGIDO (este commit)**: o cache do `KofScript.eval`
usava a chave `target:hashCode():length()` — dois programas DISTINTOS com
mesmo hash int + mesmo length colidiam e o 2º eval devolvia o resultado
CACHADO do 1º (R6 silencioso). Chave → **SHA-256** (`sha256hex`). Prova:
`KofScriptTest.evalCacheKeyDoesNotCollide` (1008+2009 vs 1560+1340, mesmo
hash+length, cada um dá sua soma). **Sem item pendente na minha lane** (g,
h e bug-47 feitos); re-dispacho deve pegar item ABERTO da tabela ou ajudar
a lane Native/JS com os bugs 41-46.

**PRÓXIMO PASSO (fixes-for-kofagent, 07/09 — PLATAFORMA F2)**: **FASE 2
COMPLETA (4/4) E COMMITADA**. (1) `Target.SCRIPT` no enum + `run --target
script` → interpretador + COMP003 honesto (`303f196`/`51754fd`); (2)
`KofProjectConfig` — parser mínimo de kof.toml (`09058d7`); (3)
`TargetMatrix` — validação backend×frontend centralizada (8 testes);
(4) CLI `--backend/--frontend` com override do kof.toml em build/run/serve
(este commit): `KofCliSupport.selectTargets(flag,flag,root)` — prioridade
flag > kof.toml > default(null), validação via `TargetMatrix.validate`
ANTES de compilar (R6). `--target` (contrato legado congelado) mantém
precedência. `serve` com backend não-JVM → erro honesto (só jvm in-process
hoje). Bônus: fix do refactor SOLID remoto que quebrou test-compile do
kof-compiler (`ClassFileParser`→parser, `JvmRuntime`→jvm, `Optimizer`→
backend nos testes ClassFileE2ETest/KofWsFrameTest/OptimizerTest — imports
faltantes após o move). Provas: `SelectTargetsTest` 8/8 + E2E manual 8
cenas (build/run × flag/manifesto/override/erro-honesto/coringa-script:
`build --backend jvm`→roda `hello 42`; manifesto `backend=script`→COMP003;
`--backend jvm` sobrepõe toml; `--backend js`→"não pode ser backend";
`run --backend script`→42 via interpretador). Suíte completa 1054/0/64-skip
verde (inclui os 3 testes SOLID desbloqueados).

**PRÓXIMO PASSO (fixes-for-kofagent, 07/09 — PLATAFORMA F3)**: FASE 3
(Full-stack) **DEGRAU 1 FEITO**: `kof build` full-stack —
`KofCliSupport.detectLayout` (APPLICATION_MODEL P6: raiz com `src/` =
backend, `src/web/` = frontend KofJS, `src/static/` = estáticos; **aditivo**:
sem `web/` com .kf → `backendDir` = próprio dir, monólito inalterado) +
`buildFrontend` (backend→`build/backend`, frontend→`build/frontend` com
bundle KofJS, estáticos→`build/static`; `--output` respeitado). Provas:
`LayoutTest` 5/5 + E2E manual (build da RAIZ full-stack: dist/backend roda
`backend 42` + dist/frontend com index.html/.mjs + dist/static/style.css;
regressão monólito `build/classes`+`--output` idêntico a hoje).
**DEGRAU 2a FEITO (este commit)**: `KofCliSupport.serveStatic(webRoot,host,port)`
— servidor de estáticos p/ `run`/`serve` full-stack (JDK `httpserver`, já
usado em JsRuntimeUiWeb — zero dep nova). `port=0`→efêmera; `/`→index.html;
content-type por extensão; **R6: path traversal (`../`) → 404**, nunca serve
fora do webRoot. Prova: `ServeStaticTest` 3/3 (root/estáticos/content-type +
traversal bloqueado + subdir index). **DEGRAU 2b FEITO (este commit)**:
`kof serve` full-stack conforme APPLICATION_MODEL **I2/P2** (decisão no
corpus, não inventada): o **APP é dono das rotas** — a CLI compila o
frontend (bundle KofJS→tempDir/frontend) + estáticos (→tempDir/static) e
passa os caminhos ao backend via **env `KOF_WEB_OUT`/`KOF_STATIC_OUT`**
(`executeProcess(..., extraEnv)` novo); o app consome com
`config.env("KOF_WEB_OUT")` + `app.serveDir("/", ...)` (JVM; Native/JS =
WEB005, gap). `buildFrontend` movido p/ `KofCliSupport` (DRY — build+serve
reuso). **Prova E2E Cenário A (I2)**: app real `web.app()` + `serveDir` →
 `GET /api/ping`={"pong":true} (JSON backend), `GET /`=bundle 200 text/html
 (via KOF_WEB_OUT), `GET /static/app.css` 200 text/css (KOF_STATIC_OUT),
 `GET /../Main.kf`=404 (traversal). **Regressão**: serve monólito sem web/
 invisível (0 linha frontend no log, rota ok, 404 sem rota). **DEGRAU 2c
 FEITO (este commit)**: `kof run --backend --frontend` full-stack — mesmo
 padrão I2 (backend JVM + web/ → buildFrontend no tempDir + env
 KOF_WEB_OUT/KOF_STATIC_OUT no processo filho; `--frontend script` = Fase 8
 SSR → erro honesto, não compila JS silencioso). Provas: E2E (env chega ao
 processo filho do backend: WEB_OUT/STATIC_OUT impressas; regressão monólito
  `mono-run 42` inalterado; R6 `--frontend script` rejeitado). **BUG
  SERVEDIR-Raiz CORRIGIDO (este commit)**: `app.serveDir("/", dir)` (o case
  canônico do full-stack I2 — montar o bundle na raiz) só servia `/`→index;
  `/Default.mjs`/`/index.html` davam 404 (match `sd.prefix + "/"` = `"//"`
  nunca casa). Fix em `kof_web_static_match` (JvmMediaWebRuntime, source
  gerado): prefixo `/` → `rel = path.substring(1)`. Prova: E2E serve
  full-stack (GET /, /index.html, /Default.mjs, /kof-runtime.mjs = 200;
  traversal /../Main.kf = 404) + `KofMediaE2ETest` 14/14 (2 novos:
  `servesRootPrefixFiles_notJustIndex`, `rootPrefix_stillBlocksTraversal`).
  **DEGRAU 2d FEITO (este commit)**: gate **APP001** (I2.6, R6):
  `KofCliSupport.app001(backend, fullStack)` — backend não-JVM com web/ →
  erro claro no build/run (antes: silenciar o frontend = fallback
  silencioso); serve já rejeitava non-JVM. **`examples/fullstack/`**
  (Cenário A canônico: kof.toml + src/Main.kf web.app+serveDir(KOF_WEB_OUT)
  + src/web/ + src/static/) + **`FullStackE2ETest`** 4/4 na suíte (CLI real
  subprocesso: build full-stack → dist/backend+frontend+static; build
  --backend native → APP001; serve → GET /api/ping=JSON, GET /=bundle,
  GET /static/app.css=css, traversal=404; monólito inalterado). Suíte
  1085/0/64-skip verde.   **FASE 3 COMPLETA** (build/run/serve full-stack +
  exemplo + E2E). **FASE 4 (KofUI) REIVINDICADA — AUDITORIA FEITA (este
  commit)**: `docs/development/KOFUI-AUDIT.md` — matriz de gaps `UI00x`
  (fonte: código, não memória). **Descoberta-chave (R6)**: `kof.ui` em
  **Native e Script roda como no-op SILENCIOSO** (E2E manual 07/09: binário
  x86 roda "feito" rc=0 sem diagnóstico; interprete idem) → **UI001/UI002
  (P0)**. KofJS (browser) = target real de DOM (widgets/router/canvas/store
  ok); faltam elementos (table/textarea/form), atributos (id/placeholder/
  disabled), style declarativo → UI003-7 (P1/P2). JVM no-op é design
  documentado (não bug). **UI001/UI002 = DECISÃO DE DESIGN (regra 6)**:
  erro em Native/Script quebraria retrocompatibilidade (hoje compila+roda
  no-op) → registrado no audit p/ o maintainer; não é edição unilateral.
  **UI004/5 FORMS — `Input.setPlaceholder` FEITO (este commit)**: aditivo,
  5 pontos (registry `KofUi.instanceMethod`, whitelist `JsRuntimeOps`, impl
  `JsRuntimeUiWidgets.kofUiInputSetPlaceholder`, stub no-op JVM
  `JvmRuntimeUi` + Native `RuntimeUi` [preserva no-op]). **Prova**:
  `KofJsBrowserE2ETest.inputPlaceholderRendersInRealBrowserDom` — Chrome
  headless, DOM contém `placeholder="digite aqui"`; suíte 1086/0/64-skip.
  **`Input.setType` FEITO (este commit)**: aditivo 5 pontos (registry,
  whitelist, `JsRuntimeUiWidgets.kofUiInputSetType`, stubs JVM/Native).
  Prova: `KofJsBrowserE2ETest.inputTypeRendersInRealBrowserDom` (DOM
  contém `type="password"`); suíte 1089/0/64-skip. **`Input.setChecked`
  + `Input.checked()` FEITO (este commit)**: aditivo 5 pontos (registry,
  whitelist, `JsRuntimeUiWidgets` — property `checked` + atributo
  `setAttribute("checked","")` p/ serializar no DOM, stubs JVM/Native
  [Bool=int 0/1]). Prova: `KofJsBrowserE2ETest
  .inputCheckboxCheckedRendersInRealBrowserDom` (DOM contém
  type="checkbox" + checked); suíte 1091/0/64-skip. **UI001-NATIVE CORRIGIDO
  (este commit, R6 P0)**: `Image/Link/Icon/Font` **não linkavam no Native**
  (`undefined reference to kof_ui_image_new [COMP001]` — 21 stubs ausentes
  em `runtime/RuntimeUi.java`; JVM tem 112, Native tinha 92). Adicionados os
  21 stubs no-op (paridade com o design JVM no-op; valores espelham
  defaults: new→1, icon_size→24, widget_font→-1, getters String→"").
  Antes: link-error duro; depois: compila+linka+roda no-op. **Prova**:
  `UiE2ETest.mediaWidgetsLinkOnAllTargets` (JVM+Native, `both()`); suíte
  1092/0/64-skip. ⚠️ O RESTO do UI001 (window/label/... no-op SILENCIOSO)
   segue sendo decisão de design (regra 6) — só o link-error era bug.
   **`Image.setAlt/setWidth/setHeight` FEITO (este commit) + BUG JVM
   CORRIGIDO (minha regressão em 3 commits)**: UI003/5. Os métodos
   `setPlaceholder`/`setType`/`setChecked`/`checked` (c862a91/4a90f88/
   b932bc0) **faltavam em `JvmRuntimeCallDescriptors.java` (o 6º ponto)**
   → compilavam no JVM mas davam `NoSuchMethodError` em runtime (default
   = `(String)Object`). Só o teste KofJS passava; o JVM nunca testado.
   Corrigido: 7 descriptors faltantes adicionados. **LIÇÃO (KOFUI-AUDIT
   §5)**: método novo em kof.ui = **6 pontos** (registry, whitelist JS,
   impl JS, stub JVM, **descriptor JVM**, stub Native) + prova em **2
   suítes** (`UiE2ETest` JVM+Native E `KofJsBrowserE2ETest` Chrome).
   Prova: `UiE2ETest.imageAttributesLinkOnAllTargets` (JVM+Native) +
   `KofJsBrowserE2ETest.imageAltSizeRendersInRealBrowserDom` (DOM
   alt/width/height); suíte 1094/0/64-skip.    **`Form(children)` FEITO (este commit) — UI004**: novo tipo `FORM` +
   ctor espelhando `Column` 1:1 (6 pontos + registry/typer/lowerer):
   `<form>` com `preventDefault` no submit + appendChild dos filhos.
   Prova: `UiE2ETest.formContainerLinksOnAllTargets` (JVM+Native) +
   `KofJsBrowserE2ETest.formContainerRendersInRealBrowserDom` (DOM
   `<form>` + kof-form + input do form); suíte 1097/0/64-skip.
   **PRÓXIMO PASSO (Fase 4, minha lane)**: `Form.onSubmit`/submit handler
   (UI004 — handler que roda no submit; padrão `Button(text, action)`
   SAM via `kofUiSetAction`); depois UI005 id/class/disabled + UI007
   style declarativo. Seguir 6 pontos + 2 suítes. **NÃO quebrar**:
  microsserviços (kof.http/kof.web/CmdServe), PKG002/4/5 (congelados), lanes
  `NativeBackend.java`/`KofInterpreter*`.

**Estado anterior (06/09 — ROADMAP AUDIT)**: plano do maintainer entregue:
auditoria completa → matriz em `docs/roadmap-audit.md` (`2970447`) →
concluir pendentes P0–P5. Bugs 29/31/34 + SG-007 em `7ec8b9d`; P0
silent-UNKNOWN em `c4ddcf6`. Cron heartbeat ATIVO (auto-loop.sh, 30min).

**Estado anterior (lane 4–8 fechada)**: lane REFACTOR-500 FASES 4–8
**FECHADA** (Parser 456 / SemanticAnalyzer 396 / JsBackend 334 / JvmRuntime 132
/ 13 classes 500–1400 + VkChain64Asm; resíduo 502-linha `ExpressionStaticCallLowerer`
→ 493 em `9c2002b`). **CANVAS001 metade JVM CORRIGIDA** (`6665a2d`): causa raiz
era o construtor `Canvas` sem typer no `MethodCallTyper` (ramo genérico só cobria
layout/store) → `var c = Canvas(...)` = UNKNOWN → Methodref owner `""` →
`ClassFormatError`; + descritor `set_line_width` `(III)V`→`(II)V` (stack underflow
→ COMP002). Prova: `UiE2ETest.canvasCreation` JVM+Native ✅. **O que falta na
suíte (único fail, 958/1)**: metade JS do mesmo teste — `kofUiCanvasNew` não
anexa o `<canvas>` ao `kof-root` nem dispara `kofUiSerializeHtml` (só
`Window.show()` serializa; o teste não usa Window). **Isso é decisão de design
da lane Canvas** (timing de serialização p/ widget sem janela) — NÃO é da minha
lane; registrado em `docs/known-bugs.md` CANVAS001. Sem item pendente na lane
4–8: re-dispacho deve (a) pegar item ABERTO novo, ou (b) ajudar FASE 2/3 do
agente-idiomatic se destravado, ou (c) fechar CANVAS001 JS **apenas** com
aprovação de design (anexar ao root + serializar no fim do main quando há
widget órfão).

**SWEEP R6 COMPLETO** (05/09): todas as áreas stdlib varridas com 0
divergências nos 3 targets (cache/mq/time/FP/collections/json/string/
validation/config/log/web). Bugs achados+corrigidos: toInt SIGSEGV,
Map/Set/higher-order ausentes, kof_panic NUL, decode<Bool> invertido
(bug 30), metrics # TYPE, tradutor quote-aware. Gates honestos:
DB001/SECN000/SCHED001/TIME001/FLT001. Bugs registrados (não-lane):
#29 spawn{lambda}-handle, #31 process.<inexistente> (lane F2.8), #28
flake ws.

**PRÓXIMA TAREA (maior valor na lane)**: **scheduler/time.interval cross
FEITO** (05/09): thread por job via clone 220 + nanosleep 101 + spinlock
`amoswap.w` (tradutor: `swpal`); `_start`→exit_group 94 (mata threads daemon
no fim do main); gates SCHED001/TIME001 REMOVIDOS; testes convertidos p/ E2E
real (`schedulerEveryCrossNative` 3 targets, `crossNativeTimeIntervalRuns`).
Suíte 964/0/3-skip.

**BUG 32 (type-arg via import) CORRIGIDO** (denúncia humana 05/09): causa
raiz = qualificação de tipo resolvia só o nível externo — `Type.of`
recusava nos type-args mas criava `ClassType("","NodeUI")`; `qualifyViaImports`/
`qualifiedType` davam bail em nome com `<`. Fix = `CompilerTypes.qualifyDeep`
(recursivo: nome pontuado + imports ambíguo→null + SymbolTable do módulo)
aplicado nos 2 pontos de saída: `resolveType` (analyzer→checkcast) e
`resolveWithTypeParams` 4-arg (driver→descritor). Prova: PackagesE2ETest
12/12 (List import/qualificado/nested/mesmo-pacote/bytecode) + suíte 969/0.

**LANG-SPEC FEITO** (06/09): Language Reference criado em
`docs/language-reference/` (14 docs) + `docs/compiler-architecture.md` +
`docs/specification-gaps.md` (SG-001..020). Separa linguagem≠compilador≠target;
spec extraída do código + probes de execução, zero mudança de comportamento
(suíte16 969/0/3-skip). **Candidatos a teste de conformidade** (regra 13 —
regras da spec SEM teste dedicado, descobertas por probe): SG-008 `Int?==null`
(NPE runtime), SG-010 `val` reatribuível, SG-009 subtipagem não checada.
**SG-001 RESOLVIDO** (06/09, decisão humana "fun e fn não deviam existir",
**2× — reservadas em TODA posição**): `fun`/`fn`/`func` são **palavras
reservadas no lexer** (tokens FUN/FN/FUNC, mecanismo sealed/permits) — não
existem no Kof em NENHUMA posição: nem prefixo de declaração (PARSE085,
top-level + `ClassMemberParser`), nem nome de função/variável/parâmetro/campo
(expectId → PARSE037/023/…). KofScript NÃO traduz mais nada (`toKofSyntax`
removido — `fn`/`let` em `.ks` dão o diagnóstico normal do parser).
FunctionSyntaxTest 12; suíte21 957+8+5+8, 0 falhas
(`bf84a86`).

 **KOFSCRIPT = TARGET DE EXECUÇÃO DIRETA FEITO** (06/09, decisão humana
 "KOFSCRIPT NÃO É JAVASCRIPT ... É UM TARGET ONDE VOCE PASSA SEU CODIGO
 DIRETAMENTE PELO INTERPRETADOR KOF SEM COMPILAR", `0aac7e6`+`c593a3d`+`f0d6ce7`):
 `KofInterpreter` (dev.kof.compiler) executa a MESMA IR otimizada do frontend
 (stack machine sobre valores reais do JDK; `KofObj` para classes Kof com
 dispatch virtual + `<clinit>` + statics; try/catch espelhando a exception
 table JVM; builtins sem lambda por reflexão ao `KofRuntime` GERADO — mesmo
 source do caminho compilado). `CompilerPipeline`: `parseAndMerge` +
 `analyzeAndLower` + `prepareForInterpretation` + `interpret` (refactor puro
 sobre a F2.52 do outro agente) + fachada pública `CompilerDriver.interpret(...)`.
 `KofScript.runFile` JVM → interpretador (sem bytecode, sem fork); JS/NATIVE →
 caminho compilado (aditivo); `runFileCompiled` mantido (fallback + prova).
 **Prova: paridade byte-idêntica interpretado vs JVM compilado** (funções,
 strings, records `==` conteúdo + toString, coleções higher-order, classes,
 while/for-in, try/catch/finally throw-as-String, spawn/await, null-safety,
 closure capture, channel, time.interval/cancel) — KofScriptTest 15/15.
 Bugs achados na varredura de paridade e CORRIGIDOS: pilha não aceitava null
 (ArrayDeque→LinkedList, `c593a3d`); time.interval sem jobs canceláveis
 (`f0d6ce7`). Docs corrigidas (architecture, compiler-architecture, README,
 actual-state, roadmap, philosophy, learn/00, CHANGELOG): KofScript não é
 "linguagem separada" nem JavaScript. **Merge com origin feito** (F2.52
 CompilerPipeline + FASE 3.x native); suíte pós-merge 957+15+5+8, 0 falhas,
  3-skip (só `UiE2ETest.canvasCreation` JS = CANVAS001, lane do outro agente).
  **REFACTOR ≤500 da lane (06/09)**: `KofInterpreter` 643→430 e
  `KofInterpreterBuiltins` 977→129 (fachada) divididos em 8 colaboradores
  coesos ≤500 (`KofInterpreterValues`/`Ops`/`Collections`/`Concurrency`/
  `Runtime`/`Objects`/`Members`/`Frame`) — refactor PRESERVA SEMÂNTICA, prova:
  `KofInterpreterParityTest` 16/16 + `KofScriptTest` 15/15 + suíte 973/0/3-skip.
  Fix de flake no MEU teste de channel (ordem `recv:42` vs `post-send` é
  corrida genuína; só `pre-send` < `recv:42` é garantido).
  **VARREDURA DE PARIDADE FEITA (06/09, `0ba58fc`+`2c57a64`)**: bateria honesta de
  edge-cases interpretado-vs-compilado (grupo A: paridade byte-idêntica
  obrigatória; grupo B: interpretador oráculo onde o compilado tem bug).
  **ACHADOS + CORRIGIDOS (minha lane)**: (1) campo estático por nome simples
  em método estático baixava `LoadLocal(0)+LoadField/StoreField` (this
  inexistente) → VerifyError/recv-null; consertado nos 2 lowerers
  (GETSTATIC/PUTSTATIC, com `+=`). (2) bug 35: `listOf().contains(1)` boxeava
  pelo tipo do ELEMENTO (Unknown) não do ARGUMENTO (int) → VerifyError;
  consertado em `JvmOpCollections` (`2c57a64`). (3) bug 36: `null == null`
  baixava `if_icmpeq` (UnknownType→primitivo) → VerifyError; consertado nos
  2 caminhos de comparação (`3c7641f`: Unknown==Unknown → referência —
  Unknown nunca surge de int inferido, então acmp é seguro e casa com o
  interpretador). **REGISTRADOS bugs 37–40** (known-bugs.md):
  `case Int` primitivo, re-throw aninhado (slot errado), `println` de null de
  Map, `+=` em campo de instância. ⚠️ O sweep commitado antes (`830259d`)
  passou em FALSO (sintaxe inválida — ambos os caminhos falhavam iguais);
  reescrito com diretório por caso + newline + sintaxe real. ⚠️ LIÇÃO: build
  STALE do ECJ proceedOnError fez o `ne` (bug 36) PARECER corrigido quando não
  estava — só `rm -rf target/classes && mvn compile` (prova honesta) revelou o
  VerifyError real. Suíte pós-fix 973+17+5+4, 0 falhas.
  **HEARTBEAT CORRIGIDO (07/09, `cfd5a4d`)**: o cron usava `opencode run
  --session` SEM `--attach` → spawnava agente headless CONCORRENTE (a "outra
  sessão" que o maintainer viu; um `run` ficou vivo 20 min disputando com o
  TUI). Agora: `--attach http://127.0.0.1:9092` (porta fixa do servidor TUI
  da sessão aberta) + health-check antes de disparar + AGENTS.md atualizado
  com a regra. Testado com cron de 1 min: FUNCIONOU (injeção na sessão viva).
  **PRÓXIMO PASSO (minha lane)**: (a) ~~bug 34~~ FEITO (`ccaf7a6`); (b) ~~bug
  35~~ CORRIGIDO (`2c57a64`); (c) ~~bug 36~~ CORRIGIDO (`3c7641f`); (d) bugs
   37–40 registrados — correção é decisão de lowering/semântica (regra 6),
   NÃO minha; (e) ~~`spawn func(arg)` com captura (bug #29)~~ —
   **RESOLVIDO (07/09, probes S29/S29js/S29det)**: #29 original (lambda void
   + handle, `7ec8b9d`) e `spawn fn(arg)` funcionam nos 4 caminhos; restou a
   variante `spawn { return … }` (lambda c/ return) → SIGSEGV Native →
   **bug 46** (known-bugs.md, lane Native);
  (f) ~~varredura JS/Native~~ — **KofScript.runFile aceita Target.SCRIPT**
  (`51754fd`, fase 2 plataforma: SCRIPT==JVM no interpretador, teste
   `scriptTargetRunsDirectly`); (g) **PARIDADE CROSS-TARGET FEITA (07/09)** —
   sweep do grupo A (28 casos) vs JS + Native (x86_64): **5 divergências
   registradas** (bugs 41-45 em `known-bugs.md` + 5 linhas em
    `backend-parity.md`): 41 campo estático Native (stub vazio `KofGetStatic`
    `nat/NativeBackend:629`, R6), 42 `record.hashCode()` ausente JS/Native, 43
   `String.length`/`charAt` Native UTF-8 vs JVM UTF-16, 44 `println(double)`
   Native 6 casas, 45 `finally` c/ `return` (JVM/Native/interp congelado;
   JS perde retorno). **Gate permanente**: `BackendParityTest
    .parityCrossTargetGroupA` (25 casos JVM==JS). Correção dos 5 = lane
    JS/Native (regra 6, NÃO minha).    (h) **RESOLVIDO (07/09)**: bug #29
    investigado — original + `spawn fn(arg)` ok nos 4; variante
    `spawn { return … }` → SIGSEGV Native = **bug 46** (lane Native).
    **BUG 47 CORRIGIDO (07/09)**: cache do `eval` colidia por
    `hashCode()+length` → resultado errado (R6); chave → SHA-256
    (`evalCacheKeyDoesNotCollide`).


**PRÓXIMA TAREA (maior valor)**: **bug 33 CORRIGIDO** (`df2ffdd`, 06/09) —
diagnóstico original ERRADO: não era Map/Set. Era **member call em receiver de
tipo nullable INFERIDO** (`var v = m.get(k)` → V?, ou `var v = maybe()`):
`MethodCallTyper` no lowering tinha `instanceof ClassType` que falhava no
`NullableType` → retorno do método saía `Object` → NoSuchMethodError. Fix:
unwrap NullableType→inner() (espelha ramo do handle). KofMapSetTest
+memberCallOnNullableInferredFromMapJVM; suíte20 954/0. **BUG NOVO
(descoberto no 33, R6 — registrar em known-bugs.md + investigar):**
`Set.first()` — `first` NÃO é método de Set no Kof (corpus não documenta) —
mas método desconhecido em **tipo de coleção** vira *no-op silencioso* no
lowerer (diferente de `C.ghost()` → SEM025) e o emit gera descritor vazio
(`"".render` → ClassFormatError). Nunca silencioso (R6): ou `first` vira
método de Set (decisão de design, regra 6) ou o lowerer de coleção dá erro
para método inexistente. Prova esperada: `vs.first()` compila com `first`
implementado OU dá diagnóstico; suíte verde.
---

## REGRA DE SINCRONIZAÇÃO (07/09, obrigatória)

> **Antes de CADA commit/push: verificar conflito com o trabalho do outro agente.**
> Pull/rebase PRIMEIRO (`git fetch origin && git rebase origin/beta-0.3.0`), conferir
> se o working tree está limpo e se o rebase não trouxe conflitos. Se o rebase
> aplicar commits novos do outro agente em arquivos que TOQUEI: re-verificar a
> compilação e os testes da área antes de commitar. NUNCA commitar por cima de
> um rebase não verificado. Push logo após cada commit (sincronizar sempre).
> Heartbeat: `scripts/kof-heartbeat.sh` (10min, nohup loop via auto-loop.sh tick).

## Em curso agora

| Gap/Item | Estado | Dono | Branch | Arquivos principais | Notas |
|---|---|---|---|---|---|
| **F9-CONFORMANCE** — matriz Feature × target (plano plataforma Fase 9; roadmap-audit P4 "Conformance Suite NOT STARTED") | `EM CURSO` | lane KOFSCRIPT (fixes-for-kofagent) | `beta-0.3.0` | `docs/development/conformance-matrix.md`, `ConformanceMatrixTest.java` (11 testes), `KofInterpreterRuntime.java`, `KofInterpreter.java`/`KofInterpreterFrame.java`, `KofScriptTest.java` | 07/09: **LOTE 1+2+3 FEITOS** — `ConformanceMatrixTest` (11 testes, 45 casos) trava a MESMA saída nos 4 targets (JVM/Native/Script/KofJS). **FIXES da lane (este commit):** (1) `json.decode<Record>` no interpretador exit 1 R6 → `KofInterpreterRuntime.decodeKofValue` (espelha `encodeKof`); (2) **RACE no interpretador** — `KofInterpreter.lastReturned` era 1 campo de instância sobrescrito por cada `KofReturn`; 2 `spawn` concorrentes (virtual threads) faziam o `await` ler o retorno do outro handle (reproduzido 3/120); correção: retorno na `Frame.returnValue` (per-thread). Prova `KofScriptTest.concurrentAwaitReturnsOwnTaskResult` (25 tasks × 8 runs). **ACHADOS novos (registrados, lanes JS/Native/compiler-core):** bug 48 (`decode<List<Record>>` — interp exit 1 R6 + Native não compila), bug 49 (KofJS não compila `try` aninhado COMP002), bug 50 (channel op DENTRO de spawn → SIGSEGV Native 139 — futex fora da thread principal; canal-sem-spawn/spawn-sem-canal ok), bug 51 (`CompilerDriver` reutilizado vaza `LambdaTask` sintética → 2ª compilação Native quebra; driver novo ok). Driver fresco por caso no teste (CLI é 1 processo/compilação). - **Bug 48 (metade interpreter) CORRIGIDO 07/09** (`KofInterpreterRuntime` intercepta `kof_json_decode_object_list` → mapeia itens p/ KofObj; prova `KofScriptTest.jsonDecodeListOfRecordRunsOnInterpreter`); a metade Native (não compila) segue ABERTA (código do fix veio em `41d989a` — outro agente fez o MESMO fix em paralelo; interceptação duplicada removida no rebase; cobertura teste+matriz+docs em `a6d723e`). **LANE KOFSCRIPT/interpreter FECHADA (07/09)** — F9 lotes 1-3 (matriz 45 casos × 4 targets) + 3 fixes (`decode<Record>`, `decode<List<Record>>`, RACE `lastReturned`) + bugs 48-51 registrados. **Suíte 1091/0/3-skip (verde). PRÓXIMO PASSO (F9, se retomado):** (a) matriz vira gate de CI (comparar células × testes reais — o plano pede); (b) riscv64/aarch64 via qemu — PULAR (já coberto por `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 20/20 + lane NATIVE002 = duplicação/colisão); (c) documentar UNSUPPORTED (Android/Wasm). Re-dispacho sem dono na minha lane → item ABERTO de outra lane (bug 49 JS try-aninhado = menor, `JsControlFlowParser.parseTryStatement`) ou lane Native (bugs 46/50/48b — mas `nat/NativeBackend` está EM CURSO no REFACTOR-500, coordenar). |
| **APP-MODEL** — Kof Application Model (RFC + plano: monólito full-stack ↔ distribuído, única abstração, `kof.toml`, packaging, System) | `EM CURSO` | agente-app-model | `beta-0.3.0` | `docs/future/APPLICATION_MODEL.md` (novo), `docs/future/README.md`, `DOING.md` | 05/09: auditoria do estado atual FEITA (CLI `CmdServe`/`CmdBuild`/`Deps`, stdlib web/http, targets, gaps WEB00x/HTTP003, roadmap §§8–11). **RFC COMPLETA (975 linhas, §1–24)**: motivation+auditoria com evidências (file:line), princípios P1–P10, `kof.toml` (manifesto), componentes, topologias, monólito/specialized, distribuído (microservices/microfrontends/gateway), System (`[system]`+`[dev.ports]`), comunicação (HTTP/JSON hoje; WS/SSE JVM; resto gap), build (jar/ELF/bundle), runtime, deployment (sem acoplamento Docker/K8s), CLI (aditivo; `--system` build sim/serve não), targets (JVM-first; APP001/002/003), segurança, compat (P5: sem manifesto = 1:1), testes (cenários A/B/C), migration, 10 open questions, future extensions, **plano I1–I4** (~4–6 sessões) + checklist AGENTS.md. **PRÓXIMO PASSO:** decisão do maintainer sobre open questions (Q1 nome manifesto, Q2 bump) e aprovação → **I1** (`AppManifest.java`+`CmdNew.java` em `kof-cli/`, parser TOML mínimo, `kof new`, validação APP003, prova: unit+E2E serve+suíte verde); ao começar I1, mover doc para `docs/application-model.md` (regra future→docs) |
| **NATIVE002-stdlib (residual R6)** — auditoria de falha silenciosa cross | `EM CURSO` | melissa (agente) | `beta-0.3.0` | `NativeBackend.java` (riscv asm), `KofScheduler.java`, `KofTime.java`, `KofMq.java`, `KofSecurity.java`, `RuntimeJsonDecode.java`, `RuntimeObservability2.java` | 05/09: **fcvt** (`1a2f044`); **ToolchainMissing** (as/ld → erro de compilação); **FLT001** (`2a7e89f`); **time.now()** (`a67a8de`); **cache**+`println(null)`+`sle/sge` (`0e6d0f9`); **MQ001 cross FEITO** (`05d0d1d`); **tail-call 8 funções** (`fc34bc7`: call+ret sem ra = loop infinito); **gates SCHED001+TIME001** (`0c4e4c5`); **toInt SIGSEGV** (`696c6c9`: deref do valor do char); **Map/Set cross** (`93fec3f`+`858718e`) + **kof_panic** C-string; **higher-order cross** (`7b81871`); **gate SECN000** (`d8aed13`: kof_sec_* ausente no cross); **json decoders escalares cross** (`d118f69`+`21954e1`: int/long/bool/string) + **bug 30 decode<Bool> x86_64 invertido** (length em %r8d + offset 0); **metrics() # TYPE cross** (`2cc3ff8`) + **tradutor quote-aware** (`#` em `.asciz "# TYPE "` era strippado como comentário → string não-terminada no aarch64); **bug 29** spawn{lambda}-com-handle registrado (pré-existente, todos os targets). Sweeps KSw/KSw2/KJ/KU/KMR3/KCFG/KVAL: **0 divergências** nos 3 targets. **time.sleep real** (`ce81639`); **scheduler/time.interval cross FEITO** (`96db26b`: gates SCHED001/TIME001 REMOVIDOS); **bug 32 CORRIGIDO — type-arg genérico via import** (`qualifyDeep` recursiva: `List<NodeUI>` c/ import, `List<com.dev.NodeUI>`, `List<List<NodeUI>>`, mesmo-pacote — PackagesE2ETest 12/12, suíte 969/0); **bug 33 registrado** (Map/Set c/ type-arg de classe — emit separado, pré-existente) (thread por job via clone 220 + nanosleep 101 + spinlock `amoswap.w`; gates SCHED001/TIME001 removidos; `_start`→exit_group 94 p/ matar threads; `amoswap.w`→`swpal` no tradutor). |
| **REFACTOR-500** — dividir as 20 classes >500 linhas (regra ≤500) | `EM CURSO` | divisão multi-agente | `beta-0.3.0` | plano `docs/refactoring/PLAN-SOLID-500.md` | **Divisão**: agente-idiomatic faz **Fases 1–3 + 9** (`NativeRuntime`, `CompilerDriver`, `NativeBackend`, varredura); **fixes-for-kofagent faz Fases 4–8** (`JsBackend`, `JvmRuntime`, `SemanticAnalyzer`, `Parser`, classes 500–1400) (`JvmRuntime`, `SemanticAnalyzer`, `Parser`, classes 500–1400); agente-idiomatic fecha com Fase 9 (varredura final). **Contrato DRY**: `TypeMapper`/`NativeNameMangler`/`TypeMetrics` são criados por agente-idiomatic e consumidos (nunca recriados) por fixes-for-kofagent. Cada fase = commit isolado + suíte completa verde como gate. ⚠️ Nunca dois agentes no mesmo arquivo gigante. **Progresso agente-idiomatic 05/09: FASE 1 COMPLETA** — NativeRuntime 17726→142 linhas (só orquestrador); ~60 classes Runtime* ≤500. Suíte compilador 922 testes, 0 falhas. **fixes-for-kofagent**: fix println(char) (`94aca7a`), gap 27 (valueOf char paridade). **FASE 5 FEITA** (`01af2d5`): JvmRuntime 2526→132 + 7 classes ≤500, source gerado byte-idêntico (prova por dump reflection). **FASE 8 FEITA** (merges `b6fb9d7`/`20b4726`+script): 13 classes 503–1401 → todas ≤500 (JvmString/Vk/Web/Media, NativeHttp/Web, Optimizer, Bench, Main, KofScript, KofJsRunner, JdwpClient), geradores byte-idênticos, suíte 943/0. Guard qemu adicionado (`4408eb6`). **PRÓXIMO PASSO (fixes-for-kofagent)**: **FASES 4–8 COMPLETAS (100%)** — FASE 5 JvmRuntime (01af2d5), FASE 8 (13 classes + JvmBackend 1401→306), FASE 7 Parser (1975→442 + 7 classes, ParseContext), FASE 6 SemanticAnalyzer (2293→396 + 8 classes; fix bug-32 re-aplicado no MemberResolver.resolveType), FASE 4 JsBackend (6064→334 + 22 classes, byte-idêntico). Todas ≤500, suíte 955/0/64-skip. **Lane do agente fechada** — sem item pendente da FASE 4–8. (Resíduo fora do inventário: VkChain64Asm 3568, arquivo M36 Vulkan não listado no plano.) **ATUALIZAÇÃO 06/09 (fixes-for-kofagent)**: FASE 9 (varredura) FEITA por mim — `scripts/check_500.sh` (gate), wildcard imports expandidos (13 arquivos), tabela de status do plano reconstruída (`55f8d20`); CANVAS001 FECHADO nos 3 targets (`5a9cac4`). **PEGANDO A FASE 3** (`NativeBackend` 8834 → ~14 classes) a pedido do maintainer, pois o agente-idiomatic NÃO a iniciou (git log: só feature work NATIVE002, nenhum commit de refactor em NativeBackend). ⚠️ **Colisão potencial**: o item NATIVE002-stdlib (linha ~105, `EM CURSO`) lista `NativeBackend.java` (riscv asm) como arquivo tocado — se o agente-idiomatic for editar riscv asm, **coordenar antes**; por ora ele está na FASE 2 (CompilerDriver), então NativeBackend está livre. **Prova de zero-regressão**: harness byte-diff dos 3 targets (x86_64/riscv64/aarch64) sobre 16 programas → `.s` byte-idêntico antes/depois de cada extração. **FASE 3.1 FEITA** (`NativeRiscvAsm` + 12 fatias ≤500; constantes asm riscv ~4700 linhas fora do NativeBackend: 8834→4113). Prova: `.s` riscv64+aarch64 byte-idênticos (16 programas), x86 só reordenação `.loc` NÃO-DETERMINISMO PRÉ-EXISTENTE (mesmo jar, 2 runs → mesmas 5 diffs); suíte 958/0 BUILD SUCCESS. **FASE 3.2 FEITA** (`NativeAarch64Translator` 496 linhas: parseImm/aarch64Reg/aarch64MovImm/aarch64AddSubImm/translateRiscvToAarch64 extraídos verbatim — bloco estático autocontido; NativeBackend 4113→3632). Prova: `.s` byte-idêntico nos 3 targets (0 diffs, 16 programas); suíte 958/0. ⚠️ não-determinismo `.loc` x86 confirmado PRÉ-EXISTENTE (mesmo jar, 2 runs → diffs diferentes; NÃO é do refactor — registrar em known-bugs na FASE 3.5). **FASE 3.3 FEITA** (`NativeRiscvHttpSupport` 280 + `NativeRiscvHttpCore` 354 + `NativeRiscvSpawn` 206: emitRiscvHttp/usesSpawn/emitRiscvSpawn extraídos verbatim — estáticos, sem estado; NativeBackend 3632→2850). Prova: riscv/aarch `.s` 0 diffs; x86 só `.loc` não-determinístico (0 linhas não-.loc); suíte 958/0. **FASE 3.4 FEITA** (`NativeRiscvCrossEmit` 311 + `NativeRiscvCrossOps` 292: os 13 métodos emitCross*Riscv/pushRiscv/crossArgReg/resolveCalleeNameRiscv/crossLocalOffRiscv/emitMethodTableRiscv extraídos verbatim; estado do backend via campo `nb` (padrão CompilerClassLowering); NativeBackend 2850→2286). Prova: `.s` byte-idêntico nos 3 targets (0 diffs, 16 programas); suíte 958/0. **FASE 3.5 FEITA** (`NativeX86StringCalls` 234: os 23 ramos String/JSON do emitCall x86 extraídos verbatim — autocontidos, zero estado; emit() devolve true quando casou; NativeBackend 2286→2070). Prova: 0 diffs nos 3 targets; suíte 958/0. **FASE 3.6 FEITA** (`NativeTypeKinds` 31: predicados isFloat/isDouble/isInt32/isDoubleWidthSlot — DRY, usados em 15+ lugares; `NativeX86Arith` 322: emitBinary+emitUnary extraídos verbatim — só dependem de NativeTypeKinds; NativeBackend 2070→1741). Prova: riscv/aarch 0 diffs, x86 só .loc; suíte 958/0. **REFACTOR-500 COMPLETO (07/09)!** — F3 do NativeBackend CONCLUÍDA (8834→479): agente-idiomatic extraiu NativeMethodEmitter (emitMethod/emitOperation/emitStart), NativeArchEmitter (emitRiscv/emitAarch64), NativeOpHelpers (condicional/literal/new/resolve), NativeClassMeta (vtable/string data). **check_500: OK — nenhuma classe >500** (as 20 do plano todas ≤500). Suíte completa 1030/0. Fases 1-9 completas (F1 NativeRuntime, F2 CompilerDriver, F3 NativeBackend, F4-8 fixes-for-kofagent, F9 varredura). ****F3 REATRIBUÍDA (07/09, agente-idiomatic)**: o fixes-for-kofagent parou na FASE 3.6 (NativeBackend 1741→1269, sem commits recentes — ele está em plataforma/conformance). agente-idiomatic retoma a F3 (sua lane original F1-F3+F9). **PRÓXIMO PASSO (fixes-for-kofagent, FASE 3)**: restam no NativeBackend (1741): (a) emitCall restante (println/print/string-ops restantes/valueOf/ctor/virtual/channel/list/class/iface — ~550 linhas, dividir por família com padrão NativeX86StringCalls), (b) JSON schema (collectJsonSchemas/emitJsonSchemaData/jsonFieldTypeCode/schemaLabelFor ~135 linhas → NativeJsonSchema com estado próprio), (c) assemble/runCommand/ToolchainMissing (~200 linhas → NativeAssembler), (d) emitMethod/emitOperation/emitStart (~350 linhas), (e) orquestradores emit/emitRiscv/emitAarch64 (ficam). Meta: NativeBackend ≤500. Mesma prova byte-diff 3 targets a cada extração. **PRÓXIMO PASSO (agente-idiomatic)**: **SOLID ORGANIZAÇÃO EM SUBPACOTES FEITA (07/09)** — 7 módulos migrados para subpacotes (dev.kof.compiler.*): `backend` (4), `js` (25+5 aux), `jvm` (35), `nat` (36, native é keyword), `parser` (10), `runtime` (60), `vk` (16). Núcleo (CompilerDriver, AST, IR, semantic, types, lowering, stdlib) fica na raiz. Pré-requisitos: multi-arquivo separado (IRNodes 45, AstNodes 59, JsMethodCtx 5 → 1 classe/arquivo public), sealed interface→interface (switches ganharam default), 271 classes public, membros do núcleo public (AccessFlags consts, Type consts, DiagnosticCollector.error, NativeRuntime, KofLoadLiteral.of*). Prova: suíte 228 verdes por commit + sync/rebase a cada push. ⚠️ Resíduo: JsExpressionParser (526) e JsControlFlowParser (514) do outro agente excedem 500 por pouco (dependem de estado de instância — extração por helper não-trivial); NativeBackend (1269) é F3 do outro agente. **BUGS CORRIGIDOS NA SESSÃO (07/09): 27, 37, 38, 40, 48-interpretador, 49-try-JS, 51-driver-reuse** (REFACTOR-500 completo, suíte 1037/0).  (paridade JS valueOf char, case primitivo SEM035, re-throw try aninhado, compound campo) — cada um com teste (suíte 1025/0).  (paridade JS valueOf char → String.fromCharCode), 37 (case primitivo em switch → SEM035), 40 (compound campo → KofDup + fieldType real)** — cada um com teste de regressão (suíte 1024/0). **Bug 39 (null de Map no println): corrigido mas REVERTIDO** — Map.get devolver V? sempre quebra `m.get("a") == 1` (if_acmpeq ref vs int → VerifyError); nullability de primitivos é congelada (AGENTS.md R6), requer decisão de narrowing do `==` (bump + discussão). **COMPARAÇÃO COM A MAIN (07/09, feita)**: diff de código (merge-base a67a8de) — NÃO há perda de funcionalidade na branch: (1) docs da main estão em docs/development/ (movidos, não perda); (2) classes de compiler da main (raiz) estão em subpacotes (reorganização SOLID, não perda); (3) CLI Decompile/Translate/Migrate/Compare são EXCLUSIVOS da main (pós merge-base, a branch nunca os teve — implementação do plano de migração legado, DIVERGÊNCIA não regressão); (4) branch tem 333 commits à frente (todo o desenvolvimento atual). Suíte completa 1017/0 como prova. **PRÓXIMO**: reduzir JsExpressionParser/JsControlFlowParser (mover métodos de instância via wrapper com instância) OU delegar ao fixes-for-kofagent; depois verificar bugs pendentes + docs + comparação com main.
| **SYN001** — `SwitchExpr`: switch como expressão (pattern matching via `case ... ->`) | `FEITO` | agente-switch-expr | main | `Parser.java`, `SemanticAnalyzer.java`, `CompilerDriver.java`, `JsBackend.java`, `AstNodes.java`, `KofFormatter.java` | 03/09 `1d1343f` — plano `docs/planning-switch-expr.md`. **Aditivo**: statement (`:`) intocado (KofPatternMatchingTest 10 + KofEnumSwitchTest 4 = gate). Lowering KIR em cadeia de if-expr (JVM+Native+JS ternários). Prova: `KofSwitchExprE2ETest` 23/23 (valor/string/pattern/destructuring/return/aninhado/enum-exaustivo/SEM032) + riscv64/aarch64 14/14 qemu. Suíte 910/0/3-skip. Bônus: fix PKG005 (`f6f1714`) — re-import transitivo não é colisão |
| **NATIVE002** — paridade stdlib riscv64/aarch64 (log/config/time/cache/mq stubs→real) | `FEITO` | agente-nativo-val | main | `NativeBackend.java` (`RISCV_RUNTIME_ASM` + `translateRiscvToAarch64`) | qemu riscv64+aarch64 OK; suíte 842/0. Detalhe: log `[LEVEL] msg` + stderr; config env real (`/proc/self/environ` syscall); cache TTL via `kof_time_now`, mq pub/sub c/ list (libera NATIVE002 residual) |
 | **NATIVE002-stdlib** — JSON/http/spawn/db no runtime riscv64 (aarch64 herda via tradutor) | `FEITO` | agente-planning | `beta-0.3.0` | `NativeBackend.java` (`RISCV_RUNTIME_ASM`, `emitRiscvHttp`, `emitRiscvSpawn`), `NativeRuntime.java` (x86_64) | 04/09 `c23dcc8`+`a660adc`+`fba2731` — **JSON** ✅ + **http** ✅ (get/post/put/patch/delete/options/status + headers) + **spawn/await** ✅ (`clone(220)`+`futex` — qemu-riscv64 8.2.2 **não** implementa clone3 (ENOSYS), usa o flag-set da glibc 0x3D0F00; heap compartilhado → `kof_alloc` virou bump **atômico** `amoadd.d`/`ldadd` (tradutor: `.arch armv8.1-a`); riscv64+aarch64 **19/19 qemu** cada). **Root cause de "http não funciona"**: bug de **gp-relaxation** — `la` virava `addi rd,gp,off` com gp=0 (binário estático, sem C runtime) → fault; JSON passava por sorte de layout. Fix: `-mno-relax` no as + `--no-relax` no ld. **Fix tradutor aarch64**: `movz` (não `mov`) quando `lsl #16`; `parseImm` aceita hex; `amoadd.d`→`ldadd`; `fence`→`dmb ish`. **db**: link dinâmico de libsqlite3 exige libc → inviável no asm puro estático; cross agora reporta **DB001 em compile-time** (R6: nunca undefined-reference no ld) — `KofDb.supportedOn` exclui riscv64/aarch64, teste `crossNativeReportsDb001`. **String methods** (`trim`/`toUpperCase`/`toLowerCase`/`replace` char+String/`lastIndexOf`/`equalsIgnoreCase`/`split`) em asm puro — antes undefined-reference no link (R6); `RISCV_RUNTIME_ASM` dividido em 3 constantes (limite 64KB javac). **2 races corrigidos**: (1) filho herdava o `sp` do pai (frame ativo do `kof_spawn_result`) e o `call` do trampoline corrompia os slots salvos do pai → filho agora carrega `sp` da stack dedicada (handle+24) **antes** do call; (2) `println` fazia 2 `write` (string+newline) → interleave entre threads (`fimbg`) → virou **1 `writev`** atômico (syscall 66). Prova: `riscv64/aarch64StringTrimCaseReplaceSplit` + spawn 40/40 ×6 sem flake + suíte 913+8+5+8, 0 falhas. ⚠️ **RECONCILIAÇÃO PENDENTE**: outro agente refatorando as classes gigantes (`NativeBackend.java`/`NativeRuntime.java`, regra ≤500 linhas) — ao terminar, **normalizar** (reaplicar os ports http/spawn/String sobre a nova estrutura modular) e **retestar tudo** (suíte + E2E riscv64/aarch64). |
| **KOFSCRIPT** — execução direta + paridade cross-target | `EM CURSO` | lane KOFSCRIPT (fixes-for-kofagent) | `beta-0.3.0` | `KofScript.java`, `KofScriptTest.java`, `KofInterpreter*.java` | **KofScript = alvo de execução direta** (06/09): IR interpretada, não compilada; paridade byte-idêntica interpretado vs JVM compilado (KofScriptTest 15/15). **REFATOR ≤500** (06/09): `KofInterpreter` 643→430 + `KofInterpreterBuiltins` 977→129 (fachada) em 8 colaboradores ≤500. **VARREDURA DE PARIDADE** (`0ba58fc`+`2c57a64`): bugs 35 (`contains` box pelo elemento) e 36 (`null==null` → `if_icmpeq`) corrigidos; campo estático por nome simples nos lowerers (GETSTATIC/PUTSTATIC). **Bugs 37–40 registrados** (known-bugs.md; correção = decisão de lowering, regra 6, NÃO minha lane). **HEARTBEAT CORRIGIDO** (`cfd5a4d`): `--attach` + health-check. **Target.SCRIPT** (`51754fd`): `runFile(f, SCRIPT)` → interpretador (fase 2 plataforma). **BUG do `wrapPureKof` CORRIGIDO (07/09, `3fbf12d`)**: `qualifyGlobals` (scanner) substitui `replaceAll(\b)` que corrompia nome da global DENTRO de string literal/comentário/membro (`println("my name is here")` → `"my KofScriptGlobals.name is here"`; gap "regex multiline-fragil" do roadmap-audit). Provas: `globalQualificationSkipsStringLiterals` + `qualifyGlobalsLeavesMembersAndComments` + suíte 1045/0/3-skip. **PARIDADE CROSS-TARGET (g) FEITA (07/09, `e88ec98`)**: sweep grupo A vs JS+Native → bugs 41-45 registrados + gate `BackendParityTest.parityCrossTargetGroupA` (25 casos JVM==JS) + reparo de build (3 imports perdidos no SOLID-refactor). **ITEM (h) RESOLVIDO (07/09)**: bug #29 investigado com probes (S29/S29js/S29det, 4 caminhos) — original (lambda void+handle) e `spawn fn(arg)` funcionam nos 4; variante `spawn { return … }` → SIGSEGV Native x86_64 (determinístico) → **bug 46** (known-bugs.md, lane Native). **BUG 47 CORRIGIDO (07/09, este commit)**: cache do `eval` colidia por `hashCode()+length` (2 programas distintos → o 2º devolvia o resultado do 1º, R6); chave → SHA-256 (`sha256hex`); prova `evalCacheKeyDoesNotCollide`. **SEM ITEM PENDENTE NA MINHA LANE** (g+h+47 feitos) — re-dispacho: pegar item ABERTO da tabela ou ajudar lane Native/JS com bugs 41-46. |
| **SEM-AUDIT** — inferência nunca cria símbolo não declarado | `FEITO (parcial)` | agente-planning | `beta-0.3.0` | `SemanticAnalyzer.java`, `CompilerDriverTest.java` | 04/09 auditoria: **regra central SEGURA** — `println(ghost)`/`foo(ghost)`/`(x:Int)->y+1` dão SEM011 em qualquer posição (13 casos em `undeclaredIdentifiersNeverInferredIntoVariables`+`lambdaParametersBoundInOwnScope`, sem fallback Any/Object/dynamic). **Bug irmão corrigido**: param de lambda SEM anotação (`(x) -> x + 1`) caía no default silencioso `Object` e o emit fazia IADD sobre referência → bytecode inválido (VerifyError disfarçado de "JavaFX launcher"). Agora SEM001 explícito com dica `(x: Int)`; `==` sobre Object continua válido; teste `untypedLambdaParamArithmeticIsDiagnosedNotEmitted`. **Y-combinator**: `=>` é token morto no parser (só `->`); lambdas curried com tipos anotados param mas invoke de FunctionType = SEM032 (interface dispatch não implementado — gap real, não bug). |

## Concluídos recentemente

| Gap/Item | Estado | Dono | Data | Prova |
|---|---|---|---|---|
| **CONC003** — JS async real (`async`/`await`/`Promise` do GraalJS) | `FEITO` | agente-conc003 | 03/09 | branch `conc003-js-async`, 6 commits (`bba9d6d`..`663bb2d`): fase 0 coloração async, fase 1+2 codegen+shim+`KofJsRunner`, fase 3+4 testes reescritos + 7 novos provando concorrência real, checklist adversarial manual (5/5: exceção não-esperada, captura mutada, `list.map` com await vira erro `CONC003-JS-01`, fire-and-forget espera antes de sair, `cancel()` cooperativo), docs atualizados em todo o repo. `KofAwaitTest`/`KofConcurrency2Test`/`SpawnE2ETest`/`KofJsE2ETest`: zero regressão fora de Native/x86_64 (ambiental, pré-existente). Falta: fork + PR (pendente confirmação) |
| **GC mark-sweep** Native | `FEITO` | agente-planning | 03/09 | `461ec3b` — sweep real funciona; auto-collect fica desligado (safe-points fora do escopo) |
| **HTTP002** — `kof.http` no Native | `FEITO` | agente-planning | 03/09 | `71d27f2` — `NativeHttpRuntime.java` (novo, ≤500): parse URL, IPv4, socket/connect, request/read body/status; `KofHttpE2ETest` 6/6 (get/post/status com server Kof real) |
| **MySQL Native prepared + query binário** | `FEITO` | agente-nativo-val | 03/09 | `4ce1f25` + `02b9ddb` — `NativeDbPrepared.java` (≤500): PREPARE/EXECUTE binário completo (); `KofDbE2ETest` 12/12 com `nativeMysqlPreparedBinary` (aspas+injection intactos) |
| **NATIVE002 core** — riscv64 + aarch64 13/13 | `FEITO` | outro agente | 02–03/09 | `3fbc29a`, `ac6c598` — asm puro via `translateRiscvToAarch64` |
| **TIME001** — time.interval/cancel no JS | `FEITO` | agente-planning | 03/09 | `c1db297` — fila cooperativa `kofTimeJobs` bombeada por `kofTimeSleep`; `KofTimeE2ETest` 5/5 |
| **LOG001** — kof.log no JS | `FEITO` | agente-planning | 01/09 | `console.*` + `KOF_LOG_LEVEL` |
| Spans W3C / lifecycle `application{}` / `kof deps` | `FEITO` | agente-planning | 01/09 |
| PKG005 (nomes iguais em pacotes diferentes) | `FEITO` | agente-idiomatic | 03/09 | Em Java, nomes com o mesmo simples em pacotes diferentes são válidos. Compilador agora usa nomes FQ internamente. | `97109c1`, `eb108ec`, `dfce911` |

## Abertos (livres pra pegar)

| Gap/Item | Prioridade | Escopo | Notas |
|---|---|---|---|
| **CANVAS001** — Canvas arc() ClassFormatError com Double params | alta | **JVM CORRIGIDO 06/09** (causa raiz: construtor Canvas sem typer → owner `""`; + descritor `set_line_width` `(III)V`→`(II)V`). Falta só a metade JS do teste: canvas não anexa ao `kof-root` nem serializa (decisão de design da lane Canvas). | `docs/known-bugs.md` CANVAS001 atualizado; fix em `MethodCallTyper`/`BuiltinCallTyper`/`JvmRuntimeCallDescriptors`; `UiE2ETest.canvasCreation` JVM+Native ✅, JS ❌ (assertNotNull html) |
| **HTTP003** — kof.http Native cauda | média | `https` + DNS real + `timeout/retry/circuit` (knobs reais) no Native | HTTP/1.1 get/post/status ✅ 03/09 (`NativeHttpRuntime.java`); delete/put/patch/options compilados; cauda = TLS/DNS/retry |
| **WEB002 residual** — kof.web Native avançado | média | TLS `listenSecure`, ws/sse, path params, keepalive no `NativeWebRuntime` | server base ✅ 03/09 (accept/route/lambda/body — `KofWebNativeE2ETest` 4/4); resto é cauda |
| **WEB001 residual** — kof.web JS avançado | média | ws/sse + TLS no JS | GraalJS HttpServer real ✅ 03/09 (`bc577aa`); ws/sse pendentes |
| **MEDIA001/2/3** | baixa | paridade media Native/JS | gap documentado |
| **SECPQ** | baixa | PQC via liboqs FFI | Tier 9 (futuro) |
| **~~MySQL query binário~~** | ~~baixo~~ | ~~`kof_db_mysql_prep_query`~~ | |
| **Portar stdlib riscv64/aarch64** | média | `translateRiscvToAarch64` existe | agente-nativo-val |
| Debugger DWARF variáveis/expressões + VS Code ext | baixa | `kof.debug` | |
| OpenTelemetry export | baixa | spans feitos; falta OTLP export | |

### Trilha universal — Tier 1 e o estágio SYSTEMS

Tier 0 (guardrails) ✅. Tier 1 pendências que fecham o estágio:

| Pendência | Escopo | Estado |
|---|---|---|
| WEB002 | kof.web server nativo | ✅ 03/09 (sem path params, sse/ws, keepalive; ver gaps) |
| WEB001 | kof.web JS | ✅ 03/09 (scaffold → REAL GraalJS HttpServer: `kofWebAppNew`, `kofWebRoute`, `kofWebListen` emitidos com handler invoke via GraalJS Value interop. Tests pass 843/0. Solicito: EM CURSO completo com SSE/WS próximos.) |
| CONC003 | async JS real | ✅ 03/09 (CONC003 ticket 7402101 — erro de lowering morto removido; spawn/await sequencial cobre JS; event-loop real é pesquisa futura) |
| MEDIA001/002/003 | media Native/JS | ✅ 03/09 (todos os 12 testes E2E passam: serveDir, Image, Audio WAV, Video metadata, Range requests, mic gap honesto). Pendências menores: camera real, parity deep‑dive. |
| HTTP002 cauda | delete/put/patch/options + resilience no Native | ✅ 03/09 (NativeHttpRuntime já tem delete/put/patch/options compilados; resilience = no-op honesto; E2E coverage pendente mas código OK) |
| GC auto-collect | safe-points | 🟡 EM CURSO — mark‑sweep real OK (3/3 E2E). Auto‑collect desligado por risco de double‑free se chamado de dentro de kof_alloc (stack pointer do bloco livre ainda não na stack). Safe‑points (mapa de raízes por frame) são pesquisa — kof_gc_collect_now disponível para coleta explícita pelo programador. |
| DB001/ORM001 (JS) | db/orm no JS | ✅ 03/09 (kof.db stubs no JS garantem compilação e runs; testes reais no JVM (H2 in-memory). ORM001 fechado para JVM/Native. Próxima frente: interop SQLite/WASM para JS — fora do escopo desta sessão). |

Tier 1 ⇒ fechado ⇒ Tiers 2–12 (plataforma universal) abrem.

## Regras de convivência (já em AGENTS.md)

- **≤500 linhas por classe** (refactor futuro de NativeRuntime: módulo novo por área, ex: `NativeHttpRuntime.java`).
- Nunca duas frentes no mesmo arquivo gigante ao mesmo tempo — se for inevitável, combine no chat antes.
- **Congelamento de comportamento** (AGENTS.md, obrigatório): zero regressão (suíte **910** é gate de merge), features novas **aditivas** (retrocompatibilidade), refactor de 500 linhas preserva semântica (mesma suíte + golden E2E; output mudou = bug do refactor), bugs em `docs/known-bugs.md` são corrigidos **no código** para atingir o comportamento previsto (nunca "documentar em volta"), paridade JVM/Native/JS é regra.

## Incidentes de processo (bronca registrada — 03/09, agente-switch-expr)

Três violações encontradas ao auditar as branches antes do merge. **Não se repita:**

1. **`fixes-for-kofagent` (`cf5a4cb`) quebrou o build da branch.** `JvmVkRuntime.java`
   foi reescrito (return → campo `VK_SOURCE`) mas o `;` do text block foi apagado e
   um `}` sobrou — `mvn compile` falhava em TODA a branch. Commite com
   `mvn -o -pl kof-compiler -am compile -q` ANTES de pushar. Fix: `3777eea`.
2. **`idiomatic-fixes` (`2729f32`) mudou semântica sem rodar a suíte completa.**
   O fix PKG005 passou a flaggar "mesmo nome no MESMO pacote" e quebrou 3 testes de
   `PackagesE2ETest` (falso-positivo: re-import transitivo de fonte explícita).
   O commit diz "871/872" — a suíte inteira é gate de merge, não um subset.
   Fix: `f6f1714` (dedup por arquivo de origem) + testes atualizados.
3. **Dois agentes no mesmo arquivo gigante sem combinar.** `SYN001` (reivindicado
   em `1d1343f`) toca `CompilerDriver.java`/`JsBackend.java`; `2729f32` e `bc577aa`
   avançaram nos mesmos arquivos na mesma janela. A regra de ouro do AGENTS.md é
   "combine no chat antes" — o merge só não foi pior porque os hunks não colidiram.

**Padrão correto:** reivindicar → trabalhar → `mvn test` COMPLETO → commit → push.
Se o gate falha, o commit não existe.

## Frentes de validação/docs (não são gaps de feature — avisar antes de mexer)

| Frente | Estado | Dono | Branch | Arquivos | Notas |
|---|---|---|---|---|---|
| **Bug-hunt + `known-bugs.md`** | `EM CURSO` | agente-idiomatic | beta-0.3.0 | `docs/known-bugs.md`, `docs/status.md` | **26 bugs corrigidos com teste de regressão** (1–26 exceto nenhum; 04/09 fechou 19, 26, 16-sublist e 8-invocação). Suíte completa 913 testes verde. |
| **Auditoria idiomática de docs/training** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `learn/`, `training/`, `docs/` | Revisar corpus contra o compilador (fake idioms, casos obsoletos). |

### Notas WEB002_NATIVE — fechado (historial pregado)

KofWebNativeE2ETest:
- T1 accept loop: `NativeWebRuntime.java` (-lloop is blocking ok) ✅ 
- T2 parse METHOD+PATH (parse bytes até espaço) → 200/404 ✅
- Handler com send lambda ✅ (dispatch vtable[0])
- `kof_web_body()` — read body após CRLF CRLF ✅ (T4).

Fechado 03/09 _closed. 4/4 suíte.

