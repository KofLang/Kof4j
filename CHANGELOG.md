# Changelog

Todas as mudanças relevantes do Kof são registradas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/) com a convenção
de commits do projeto (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`,
`build:`, `tooling:`). A seção de cada release é gerada por
`scripts/changelog.sh` e inserida pela pipeline neste marcador:

## [0.3.0-beta] - unreleased (branch `beta-0.3.0`)

(0.2.6) preservada — mudanças aqui são aditivas ou com bump deliberado.

### Em desenvolvimento

  - **KofScript virou target de execução direta com interpretador da IR
    (06/09)** — `KofInterpreter` executa a MESMA IR otimizada que o backend
    JVM consome (mesmo frontend: parse → merge → imports → desugar → análise
    → lowering → otimização), sem emitir bytecode e sem fork de JVM.
    Paridade por construção, provada em teste (saída byte-idêntica ao JVM
    compilado: funções, strings, records com `==` de conteúdo + toString,
    coleções com higher-order, classes mutáveis, while/for-in,
    try/catch/finally com throw-as-String, spawn/await). `CompilerDriver`
    ganhou `interpret(...)` (fachada pública) e os passos extraídos
    `parseAndMerge`/`analyzeAndLower` (refactor puro, zero-regressão).
    JS/Native continuam no caminho compilado; `runFileCompiled` mantido como
    fallback e prova de paridade. Docs corrigidas: KofScript NÃO é linguagem
    separada nem JavaScript — é Kof puro no mesmo frontend.
  - **`fn`/`fun`/`func` viraram palavras reservadas no Kof (06/09, SG-001)** —
    a documentação sempre disse que "não existe `fun` nem `func`" (AGENTS.md,
    fake-idioms.md), mas o compilador aceitava `fn` como prefixo e `fun`/`func`
    como tipo de retorno implícito. Agora as três são **palavras reservadas no
    lexer** (tokens `FUN`/`FN`/`FUNC`, mesmo mecanismo de `sealed`/`permits`):
    **não existem** no Kof em nenhuma posição — nem como keyword de declaração,
    nem como nome de função/variável/parâmetro/campo. Em posição de declaração
    dá **`PARSE085`** (diagnóstico claro: "declare como `Tipo nome(...) { }` ou
    `nome(...): Tipo { }`"); em outra posição o `expectId` de cada parser já
    falha (`PARSE037` variável, `PARSE023` parâmetro). Alinhamento
    código↔corpus (regra 4). **KofScript (`.ks`) não é exceção** — é Kof puro
    (ver entrada KofScript abaixo); `fn`/`fun`/`func` lá também dão
    `PARSE085`. Breaking change deliberado e
    documentado: código `.kf` que usava `fun`/`fn`/`func` (mesmo como
    identificador) agora precisa renomear. Prova: `FunctionSyntaxTest` (12
    casos) + KofScriptTest 8/8 + suíte completa 957/0.

  - **KofScript é Kof puro — sugar JavaScript removido (06/09, correção de
    design do maintainer)** — o pipeline carregava açúcar de outra língua:
    `let`→`var`, `const`→`val`, `async fn`→`fn`, e um `fn` "próprio" traduzido
    na fronteira `.ks`→`.kf`. **KofScript não é JavaScript**: é o target onde
    o código Kof roda direto, sem compilação separada. Removido `preprocess`,
    `normalizeVoidFns`, `toKofSyntax` e as 3 cópias da lógica de wrap
    (KofScript/CmdScript/LspServer) — substituídas por um único `wrapPureKof`
    que só faz o **modelo de execução de script**: statements de topo viram
    `main()`, `var`/`val` de topo viram `KofScriptGlobals`. `let`/`const`/
    `async`/`fn` agora dão o diagnóstico normal do parser Kof em `.ks` também
    (R6: nunca silencioso). Breaking change deliberado: `.ks` que usava sugar
    precisa da forma Kof. Prova: `KofScriptTest` 9/9 (inclui `jsSugarIsRejected`
    — `fn`/`let` em `.ks` → `PARSE085`) + suíte completa.

  - **NATIVE002-stdlib residual (05/09)** — auditoria R6 + paridade cross:
    **fcvt riscv64** (os 10 mnemonics de conversão numérica saíam com
    rd/rs invertidos — `as Int`/`as Double` quebravam no `as`),
    **ToolchainMissing** (falha de `as`/`ld` nos cross agora propaga como
    erro de compilação — antes era "success=true sem binário" silencioso),
    **FLT001** (`println(double)`/`valueOf(double)` no cross vira
    diagnóstico em compile-time: runtime asm puro sem libc não tem `%g` —
    antes segfault silencioso; aritmética/conversão FP funciona),
    **time.now()** real (`clock_gettime` 113 — era stub `li a0,0` que
    quebrava o TTL do cache), **cache riscv64/aarch64** (scan loops usavam
    t2/t3 clobberados pelo `kof_string_equals` → segfault; + `sle/sge`
    inexistentes na ISA riscv → `<=`/`>=` quebravam; + `println(null)`→"null"),
    **mq riscv64/aarch64** (port completo: queue por handle, pop via
    `kof_list_remove`, queue_size, unsubscribe por identidade, invoke dos
    handlers via vtable — antes infuncional: gate MQ001). Prova:
    `KofMqE2ETest` 5/5 (incl. cross qemu c/ paridade de output),
    `riscv64/aarch64Cache`, `riscv64/aarch64TimeNow`.
    **tail-call em 8 funções riscv** (`call`+`ret` sem salvar `ra` = loop
    infinito — `observability.health`/ids/`time_interval` hangavam),
    **gates SCHED001/TIME001/SECN000** (scheduler/time.interval/kof_sec_*
    ausentes no runtime cross → diagnóstico limpo em vez de undefined-reference
    no link ou no-op silencioso), **`"42".toInt()`** (deref do VALOR do char
    como endereço → SIGSEGV), **Map/Set + higher-order** no cross
    (`RISCV_MAPSET_ASM` linear-scan; closure ABI do mq), **`kof_panic`**
    imprime C-string (mensagem de bounds-check), **json decoders escalares**
    (int/long/bool/string), **bug 30** (`decode<Bool>("false")`→true no
    x86_64: length em registrador errado + offset ignorado), **metrics()
    `# TYPE`** no cross + **tradutor quote-aware** (`.asciz "# TYPE "` era
    strippado como comentário → string não-terminada no aarch64). Sweeps de
    paridade (KSw/KSw2/KJ/KU/KMR3/KCFG/KVAL): **0 divergências** nos 3
    targets. Bugs registrados fora da lane: #29 (`spawn { lambda }` com
    handle), #31 (`process.<inexistente>` compila como acesso a campo),
    #28 (flake ws). Suíte completa 962/0/3-skip.

  - NATIVE002 paridade avançada riscv64/aarch64: stdlib real no runtime asm —
    **JSON** (`kof_json_quote`/builder, encode/decode record+listas), **HTTP**
    (`get/post/put/patch/delete/options/status` + headers, asm puro: socket+
    connect+write/read/close, syscalls asm-generic) e **spawn/await** (`clone`
    + `futex` — qemu-riscv64 8.2.2 não implementa clone3; heap compartilhado
    entre main e workers → `kof_alloc` virou bump atômico `amoadd.d`) fechados
    (19/19 qemu cada target); aarch64 herda via `translateRiscvToAarch64`.
    Fix de codegen: `--no-relax` no as/ld riscv64 (gp-relaxation faultava com
    gp=0 no binário estático). Fixes do tradutor aarch64: `movz` (não `mov`)
    para imediatos com `lsl #16`; imediatos hex no `li`/`addi`/`andi`;
    `amoadd.d`→`ldadd` + `.arch armv8.1-a`; `fence`→`dmb ish`. **db**: o link
    dinâmico de libsqlite3 exige libc — inviável no asm puro estático; os cross
    agora reportam **DB001 em compile-time** (R6: nunca undefined-reference no
    ld), travado por `crossNativeReportsDb001`. **String methods riscv64/aarch64**
    (`trim`/`toUpperCase`/`toLowerCase`/`replace` char+String/`lastIndexOf`/
    `equalsIgnoreCase`/`split`) implementados em asm puro — antes quebravam no
    link com undefined reference silencioso (R6); `RISCV_RUNTIME_ASM` dividido
    em 3 constantes (limite de 64KB do javac). Prova: `riscv64/aarch64StringTrimCaseReplaceSplit`
    + suíte 913+8+5+8.
  - GC auto-collect (safe-points + mapa de raízes por frame).
  - Package manager MVP (`kof init`/`kofdeps`/registry).

## [0.2.8-beta] - 2026-09-04

### Documentation

  - seção 0.2.7-beta movida para o topo

## [0.3.0-beta] - 2026-09-05

### Features

  - String methods riscv64/aarch64 + 2 fixes de race no spawn
  - spawn/await riscv64+aarch64 (NATIVE002-stdlib)
  - http.get/post/status riscv64+aarch64 (NATIVE002-stdlib)
  - add support for nested lambdas capturing outer variables
  - JSON completo no riscv64/aarch64 (encode+decode de listas)
  - kof_json_quote no riscv64/aarch64 + corrige escape x86_64
  - PR6 hardening limits and observability (upstream rebase)

### Bugfixes

  - time.now() riscv64/aarch64 real (clock_gettime=113, paridade x86)
  - println(char) numérico (72) preservado; String.valueOf(char) → caractere UTF-8 (h)
  - FLT001 — println/valueOf(double) no cross vira diagnóstico, não segfault
  - fcvt riscv64 com direção invertida (FP conversions quebradas)
  - kof_mv64_matvec preserva rc do vk64_submit no readback de y (memcpy clobbera %eax)
  - update stack handling and syscall for string printing
  - readback y <- ymap apos submit em mv1 (rsi clobberado pelo submit)
  - void-as-value SEM033, sublist SEM034, interface dispatch para tipo de função declarado
  - cross riscv64/aarch64 reporta DB001 em compile-time (R6)
  - aritmética sobre param de lambda sem anotação → SEM001, não bytecode quebrado
  - base 1000x1e6 + recomposicao exata por divId
  - vkMakeSet recebia PipelineLayout em vez do DSL
  - unwrap InvocationTargetException in handler error catch
  - map AudioSystem.isLineSupported throw to MEDIA003 message

### Documentation

  - FLT001-cross na matriz de gaps + Bug 27 (println(char) diverge — pré-existente remoto)
  - REFACTOR-500 — divisão confirmada (fixes-for-kofagent faz Fases 4-8)
  - gap 27 — paridade String.valueOf(char) JS vs JVM/Native (R6)
  - reconcile note for planning-future <-> beta-0.3.0 (merge state, pre-existing charAt failure, normalization checklist)
  - update AGENTS.md with autonomous mode guidelines and conditions for stopping
  - add REFACTOR-500 entry for class division guidelines
  - lição aprendida 04/09 — trabalhe sempre em partes pequenas
  - add PLAN-SOLID-500 for class restructuring guidelines
  - known-bugs.md — Bug 19 atualizado (triple-nested resolvido 04/09)
  - NATIVE002-stdlib FEITO (JSON+http+spawn riscv64/aarch64; db→DB001)
  - regra todowrite obrigatório (status visível a cada etapa)
  - break TIER 2 into measurable subtasks (2.1.1–2.5.1)

### Refactoring

  - optimize descriptor set binding logic in assembly

### Tests

  - channelBlocksBeforeSendJvm não pinifica corrida de agendamento

### Build

  - abre linha 0.3.0-beta na branch beta-0.3.0

## [0.3.1-beta] - 2026-09-08

### Features

  - implement extern function support and enhance FFI diagnostics
  - Input/Textarea setName + setReadonly — UI005 (atributos)
  - add JvmFrameDiagnostics for enhanced error reporting on frame crashes
  - add setName and setReadonly methods for Input and Textarea components
  - Table(header, rows) — UI003 data-driven (fecha table/tr/td)
  - Ul/Ol data-driven — UI003 (List<String> vira <ul>/<ol><li>)
  - Canvas.drawImage — fecha UI009 (Image composto no bitmap)
  - Canvas UI009 — save/restore/setGlobalAlpha/fillText/measureText/transform
  - Select(options) — UI003/UI004 (escolha de opção, 9 pontos + 2 provas)
  - Textarea — widget multilinha (UI003, Fase 4)
  - Form.onSubmit + Form.submit() (UI004 headline, Fase 4)
  - setId/setClass/setDisabled (UI005) + fix código morto acceptsFont
  - Form(children) — container <form> (UI004, Fase 4)
  - Input.setChecked/checked (UI004 forms, Fase 4) — checkbox/radio state
  - Input.setType (UI003/4 forms, Fase 4) — text/number/email/password/date
  - Input.setPlaceholder (UI004/5 forms, Fase 4) — KofJS DOM real
  - F3 fechada — APP001 + examples/fullstack + FullStackE2ETest (I2 DoD)
  - F3-degrau-2c — kof run full-stack (env-pass ao processo filho)
  - F3-degrau-2b — kof serve full-stack (I2: app serve o bundle via env)
  - F3-degrau-2a — serveStatic (estáticos p/ full-stack, R6 traversal)
  - F3-degrau-1 — kof build full-stack (backend+frontend+estáticos)
  - F2-parte-4 — CLI --backend/--frontend c/ override do kof.toml
  - add security plan and implementation layers documentation
  - change interfaces to sealed for LiteralKind and Type
  - F2-parte-2 — KofProjectConfig (parser mínimo de kof.toml)
  - runFile aceita Target.SCRIPT (execução direta, fase 2 plataforma)
  - F2-parte-1 — Target.SCRIPT (coringa) + COMP003 honesto + run --target script
  - add KofScript target and module resolution for project roots
  - Fase 1 — module resolution cross-directory via kof.toml + PKG006/PKG007 (plataforma, docs/future/PLATFORM-PLAN.md)
  - add interpreter parity sweep test for edge cases
  - add auto-loop script for autonomous opencode mode with cron integration
  - implement x86_64 method emission in NativeMethodEmitter
  - implement x86_64 call emission in NativeX86Calls class
  - add Kof module interpretation without bytecode emission
  - KofInterpreter — IR stack machine; KofScript roda sem compilar
  - improve imports and add check script for class size limit
  - Canvas 2D widget — desenho 2D via <canvas> (KofJS)
  - scheduler/time.interval riscv64/aarch64 (SCHED001+TIME001 FEITO)
  - time.sleep real riscv64/aarch64 (nanosleep 101)
  - metrics() com # TYPE no riscv64/aarch64 + tradutor quote-aware
  - json.decode<Int> escalar riscv64/aarch64
  - gate SECN000 no cross (kof_sec_* ausente no runtime riscv64)
  - higher-order (map/filter/reduce) riscv64/aarch64
  - Map/Set riscv64/aarch64 + kof_panic imprime C-string (bounds msg)
  - gates SCHED001 + TIME001 no cross (scheduler/time.interval stubs)
  - MQ001 cross FEITO — port completo kof.mq riscv64/aarch64
  - MQ001 no cross — gate honesto (padrao DB001)

### Bugfixes

  - source do runtime FFI casa com o JDK que o compila (CI 21 vs local 25)
  - #35.3 banner reporta a porta REAL (R6, nunca mentir)
  - headers variádicos — 2+ headers como args separados (bug 60, GitHub #32)
  - serveDir com barra final serve index.html (GitHub #35.2) + regressão bug 59
  - record com campo List<Record> decodifica tipado (bug 58, GitHub #34)
  - String.length contava bytes UTF-8 (bug 43)
  - campo estático dava lixo (bug 41)
  - await sobre handle não quebra mais o bytecode (bug 57, GitHub #31)
  - array.get(i)/.size não geram mais ClassFormatError (GitHub #30)
  - lambda return aninhado (bug 53, #28) + CME no spawn (bug 55)
  - handle return null in lambdas to avoid 404 responses (bug 53)
  - finally com return no try perdia o retorno (bug 45)
  - app.delete não colide mais com File.delete (bug 54, GitHub #29)
  - record hashCode() ausente no KofJS (bug 42)
  - try aninhado compila no KofJS (bug 49) + registra bug 52 (re-throw em catch)
  - CompilerDriver reutilizado vazava classes sintéticas (bug 51)
  - Image.setAlt/setWidth/setHeight (UI003/5) + FIX JVM descriptors (6º ponto)
  - try aninhado no KofJS (bug 49)
  - Native — 21 stubs UI ausentes quebravam link (UI001, R6 P0)
  - json.decode<List<Record>> — trata kof_json_decode_object_list (bug 48)
  - corrige RACE no spawn/await (lastReturned) + lote 3 da matriz (concorrência determinística)
  - serveDir('/') serve o bundle completo, não só o index (F3 full-stack)
  - decode<Record> no interpretador + lote 2 da matriz (erros/null/JSON)
  - re-throw em try aninhado — corpo do catch usa sub-escopo (bug 38)
  - REVERTER bug 39 (get nullable) — quebra m.get==1 (retrocompat); registrar design pendente
  - Map.get devolve V? sempre — println(null) não dá NPE (bug 39)
  - chave do cache do eval vira SHA-256 — colisão hashCode+length dava resultado errado (bug 47)
  - case de primitivo em switch → SEM035 (bug 37)
  - compound em campo de instância — KofDup + fieldType real (bug 40)
  - watchdog teto 120→240min (turno ativo legítimo passa de ~2h)
  - importa classes movidas no refactor SOLID (test-compile quebrado)
  - paridade String.valueOf(char) — JS usa String.fromCharCode (bug 27)
  - watchdog mata run pendurado (lock stale >120min)
  - qualifyGlobals substitui replaceAll(\b) — nome de global não corrompe string literal/comentário/membro
  - switches não-exaustivos após sealed->interface — adiciona default
  - SEM025 para resolução falha em símbolo conhecido — namespaces builtin, super e campos (R6, P0 roadmap-audit)
  - update cron job to require --attach for session injection and clarify server health check
  - resolve VerifyError for null comparisons by using reference equality for UnknownType
  - contains boxeia pelo tipo do argumento (bug 35)
  - campo estático por nome simples baixa GETSTATIC/PUTSTATIC
  - clarify autonomous mode cron behavior in AGENTS.md
  - diagnose builtin unknown methods, wildcard and spawn void handle (bugs 29/31/34, SG-007)
  - semear staticFields com initialValue de campos estáticos
  - add error handling for unresolved collection and process method calls
  - time.interval/scheduler com jobs canceláveis (paridade com runtime gerado)
  - pilha aceita null (LinkedList); +3 testes de paridade
  - FASE 3.1–3.7 corrigidas — ciclo StackOverflow, visibilidades, imports + prova honesta (jar fresco)
  - Canvas renderiza no KofJS — shim getContext + attach ao root + snapshot em ops de renderização (CANVAS001 FECHADO)
  - Canvas sem owner "" no JVM — construtores UI tipados no driver-side typer (CANVAS001 metade JVM)
  - contadores WS publicados ANTES do estado observável — fecha bug 28 (flake gate)
  - fun/fn/func viram palavras reservadas — não existem em nenhuma posição (SG-001)
  - member call em receiver nullable inferido (bug 33) — MethodCallTyper unwrap
  - PARSE085 também em membros de classe + âncoras da spec pós-F6/F7
  - rejeita fn/fun/func como keyword de função (PARSE085) — SG-001
  - resolveType do MemberResolver recebe o qualifyDeep do bug-32 (a extração FASE 6 duplicou o método sem o fix)
  - type-argument genérico via import (bug 32) — qualificação recursiva
  - JdwpPacket — codec no corpo da classe (0abb880 deixou aninhado como Packet, quebrando JdwpClient)
  - JdwpPacket — codec no corpo da classe, não em aninhada morta (REFACTOR-500)
  - metrics() x86_64 nao emite mais bytes NUL (comprimentos errados)
  - decoders json escalares cross + decode<Bool> x86_64 invertido
  - "42".toInt() riscv64/aarch64 — deref do VALOR do char = SIGSEGV
  - tail-call em 8 funcoes riscv64 (call+ret sem salvar ra = loop infinito)
  - F1.9 extrai RuntimeJson* — restaura label .Lkof_json_true perdido na divisão
  - println(char) imprime código (paridade JVM) + F1.1 extrai RuntimePrint
  - cache riscv64/aarch64 real + println(null) + sle/sge invalidos

### Documentation

  - fecha lane — issues #28–#35 fechadas no GitHub (0.3.1)
  - fecha metade JS do bug 42 (recordhash) + corrige doc do 44
  - especificação Editor Integration (plano, implementação depois)
  - P0 fallbacks semânticos FECHADO (verificado no código)
  - UI007 proposta de design (regra 6) + DOING atualizado (drawImage/Ul/Ol feitos)
  - bug 56 (GitHub #30 split→ClassFormatError) + linha GITHUB-P0 no DOING
  - auditoria planning-future — R1 marcado FEITO
  - idiom web — contrato de retorno de handler (bug 53/54)
  - auditoria planning-future — agente morto, lote 3 duplicata+flaky
  - corrige causa-raiz da auditoria planning-future
  - auditoria planning-future × docs/development/future
  - bugs 53/54 (GitHub #28/#29) — reproduzidos + causa raiz por IR
  - bug 49 descreve o código mesclado (5d6e68a), não minha versão descartada
  - bugs 48/49 corrigidos (json list interpreter, try aninhado JS); suíte 1037/0
  - F9 lotes 1-3 + 3 fixes da lane interpreter; suíte 1091/0/3-skip
  - FASE 4 KofUI — auditoria de cobertura + matriz de gaps UI00x
  - REFACTOR-500 COMPLETO — F3 NativeBackend 8834→479, check_500 OK, todas ≤500
  - F3 reatribuída ao agente-idiomatic (fixes parou na 3.6; NativeBackend 1269)
  - bugs 27/37/38/40 corrigidos (suíte 1025/0); 39 = design pendente
  - bugs 27/37/40 corrigidos; 39 revertido (design); 38 pendente
  - reivindica F9 — matriz Feature×Target com estado real do sweep cross-target
  - atualiza caminhos de arquivos para os novos subpacotes SOLID (jvm/, nat/)
  - comparação com a main — sem perda de funcionalidade
  - bug 46 — spawn { return … } SIGSEGV no Native (variante do #29) + lane KOFSCRIPT g+h fechada
  - SOLID organização em subpacotes — 7 módulos migrados (backend/js/jvm/nat/parser/runtime/vk)
  - move pendentes para development/ e referencia no AGENTS.md
  - regra de sincronização — verificar conflito antes de cada commit/push
  - runFile SCRIPT feito (51754fd); proximo = paridade cross-target (regra 5)
  - bug 36 CORRIGIDO (3c7641f) + heartbeat corrigido (--attach 9092, testado 1min)
  - Fase 1 plataforma FEITA (6caf84d) — PRÓXIMO PASSO: F2 Target Architecture
  - F0 auditoria real + plano técnico por fases (module system, targets, full-stack, KofUI/JS/Wasm/Android, conformance)
  - roadmap-audit.md — matriz de estado real (12 itens + 12 fallbacks UNKNOWN P0)
  - claim ROADMAP AUDIT lane (fase 1 auditoria em curso)
  - KofScript = execução direta via KofInterpreter (status, backend-parity, bug 37 refinado)
  - varredura FEITA (fix static-field + bug 35), bugs 36-40 registrados, licao do build stale ECJ
  - varredura de paridade FEITA + bugs 35-40 registrados
  - bug 34 registrado — método inexistente em builtin → no-op silencioso (R6)
  - KOFSCRIPT pós-merge — paridade 15/15, bugs null+interval corrigidos, PRÓXIMO PASSO (bug 29/34 + varredura JS/Native)
  - F3 3.1–3.9 completas (fixes-for-kofagent) + protocolo de prova honesta documentado
  - REFACTOR-500 — FASE 9 (varredura) FEITA, só NativeBackend >500 (F3 do outro agente)
  - KofScript = target de execução direta (interpretador da IR)
  - REFACTOR-500 — FASE 2 COMPLETA (CompilerDriver ≤500)
  - FASE 3 reivindicada (NativeBackend) — maintainer pediu, agente-idiomatic não iniciou; aviso de colisão NATIVE002 + plano byte-diff 3 targets
  - PRÓXIMO PASSO — lane 4–8 fechada, CANVAS001 metade JVM corrigida, falta só design JS (lane Canvas)
  - REFACTOR-500 — F2.33-F2.44 + lições (this->driver, campos intercalados)
  - REFACTOR-500 — FASES 4-8 COMPLETAS (fixes-for-kofagent); suíte 955/0
  - SG-001 palavras reservadas (bf84a86) + bug 33 corrigido + bug novo coleção
  - REFACTOR-500 — F2.24-F2.32 + lição do bloco de instância
  - SG-001 inclui membros de classe (7e6f9e3, suíte19)
  - SG-001 resolvido (fn/fun/func → PARSE085, suíte18 verde)
  - âncoras do CompilerDriver por método (não linha) + contagem AST 50 nós
  - LANG-SPEC FEITO — suíte16 969/0/3-skip (zero regressão, docs puros)
  - README + architecture.md — separa linguagem≠compilador≠target, corrige pipeline
  - syntax (formas concretas) + compiler-architecture (implementação)
  - modules + semantics + specification-status + specification-gaps
  - functions + closures + classes
  - expressions + statements — semântica de cada forma
  - types + type-system — catálogo de tipos e regras concretas de validade
  - grammar — gramática EBNF extrativa + AST (39 nós) + precedência exata
  - lexical-structure — gramática léxica completa (tokens, keywords, literais, operadores, erros LEX00x)
  - Language Reference — índice + separação linguagem≠compilador≠target (LANG-SPEC)
  - REFACTOR-500 — lição da divisão do ExpressionMethodCallLowerer (cadeia if/else)
  - REFACTOR-500 — F2.21-F2.23 + suíte 948 verde
  - RFC completa §3-24 + plano I1-I4 — topologia, kof.toml, System, build/deploy, targets, segurança, testes, open questions
  - REFACTOR-500 — F2.12-F2.20 (CompilerDriver 3419)
  - RFC §3-7 — principles, application, manifesto kof.toml, componentes, topologia, monólito
  - RFC APPLICATION_MODEL §1-2 — motivation + auditoria do estado (CLI/stdlib/targets/gaps)
  - bug 28 — nota de recorrência 05/09 (suíte 969 pós bug-32, flake confirmada)
  - REFACTOR-500 — FASE 5+8 FEITAS (fixes-for-kofagent); PRÓXIMO FASE 7 Parser
  - REFACTOR-500 — limpa PRÓXIMO PASSO (F2.12 LoweringContext)
  - REFACTOR-500 — F2.11 + PRÓXIMO PASSO (LoweringContext)
  - REFACTOR-500 — F2.10 CompilerImports
  - Bug 28 — flaky WS/SSE connection counter (JVM) registrado
  - REFACTOR-500 — F2.9 CompilerDesugar
  - time.sleep real no cross (ce81639) — fecha unidade
  - regra 7 — unidade em progresso = turno em progresso
  - REFACTOR-500 — F2.8 ModuleRoots
  - estado real do NATIVE002-stdlib (sweep completo 05/09)
  - REFACTOR-500 — F2.5-F2.7
  - bug 31 (process.<inexistente> segfault) + gap formato log cross
  - linha melissa — sweep R6 completo (SECN000, json decoders, metrics # TYPE, tradutor quote-aware); suíte 962/0
  - REFACTOR-500 — F2.5 BoxClassFactory
  - #29 spawn { lambda } com handle quebra em todos os targets
  - re-dispacho nao e conversa — regra 6 do turno autonomo
  - linha melissa atualizada — Map/Set/higher-order FEITO, sweep 0 divergencias
  - PRÓXIMO PASSO — sweep R6 crypto/process-spawn-edge/json-edge/string-utf8
  - REFACTOR-500 — PRÓXIMO PASSO atualizado (F2.5 BoxClassFactory/CompilerImports)
  - REFACTOR-500 — F2.4 CompilerTypes
  - REFACTOR-500 — F2.3 TypeEmitter + PRÓXIMO PASSO
  - REFACTOR-500 — F2.1/F2.2 CompilerDriver (TypeMetrics, StringMethodRegistry)
  - restaura PRÓXIMO PASSO + linha melissa (merge remoto sobrescreveu)
  - REFACTOR-500 — divisão confirmada (idiomatic F1-3+9, fixes-for-kofagent F4-8) + FASE 1 completa
  - REFACTOR-500 — FASE 1 COMPLETA (NativeRuntime)
  - REFACTOR-500 — F1.15 divide métodos gigantes restantes
  - REFACTOR-500 — F1.14 dedup
  - REFACTOR-500 — F1.13 security/validation/observability
  - REFACTOR-500 — F1.12 printnum/net/vk/misc
  - REFACTOR-500 — F1.11 concurrency
  - REFACTOR-500 — F1.10 RuntimeMemory/RuntimeGc
  - REFACTOR-500 — progresso F1.1-F1.9 (agente-idiomatic) e regressão de paridade do merge
  - Bug 28 (flake ws counter) + PRÓXIMO PASSO (auditoria R6 observability/scheduler)
  - modo autonomo — regra do turno + PRÓXIMO PASSO

### Refactoring

  - dividir lowerer UI + JsRuntimeUiWidgets + JvmRuntimeUi (gate <=500)
  - F3 — extrai NativeClassMeta (vtable/string data, 118 linhas)
  - F3 — extrai NativeOpHelpers (ops de emissão, 178 linhas)
  - F3 — extrai NativeArchEmitter (emitRiscv/emitAarch64, 262 linhas)
  - F3 — extrai NativeMethodEmitter (emitMethod/emitOperation/emitStart, 251 linhas)
  - SOLID — JsExpressionParser 526→383 (JsExpressionStatementParser helper)
  - SOLID — JsControlFlowParser 514→499 (JsLabelParser helper)
  - SOLID — ExpressionParser 519→475 (ExpressionNewParser helper)
  - SOLID — migra parser para dev.kof.compiler.parser (10 classes)
  - SOLID — migra backend/orquestração para dev.kof.compiler.backend (4 classes)
  - SOLID — migra backend JVM para dev.kof.compiler.jvm (35 classes)
  - SOLID — migra backend nativo para dev.kof.compiler.nat (36 classes)
  - SOLID — migra runtime nativo para dev.kof.compiler.runtime (60 classes)
  - SOLID — migra backend JS para dev.kof.compiler.js (30 classes)
  - SOLID — 271 classes public + extrai RecordDeclarationNode/KofOperation
  - SOLID — separa JsMethodCtx.java (5 classes) em arquivos próprios
  - SOLID — separa AstNodes.java (59 decls) em arquivos próprios
  - SOLID — separa IRNodes.java (45 records) em arquivos próprios public
  - SOLID subpackages — grupo vk public (corrige acesso cross-package)
  - divide KofInterpreter/KofInterpreterBuiltins em 8 colaboradores <=500
  - update progress and next steps for REFACTOR-500 phases
  - change resolveCalleeName method visibility to public
  - extract assembly logic to NativeAssembler class
  - F2.53 extrai CompilerUiEmitter (UI instance, packed color, SAM)
  - F2.52 extrai lowerAndEmit para CompilerPipeline
  - F2.51 move setters/local-scope/isAbstract para CompilerDriverState
  - F2.50 move 35 wrappers/setters para CompilerDriverState (herança)
  - F2.49 extrai CompilerEmission2 (super-bridge, args, increment)
  - F2.48 extrai CompilerDriverState (33 campos via herança)
  - F2.47 extrai CompilerEmissionHelpers + CompilerConfigSupport
  - F2.46 extrai CompilerTypeSupport (type helpers)
  - F2.45 extrai CompilerPipeline (orquestração)
  - FASE 3.6 — aritmética x86_64 + predicados de tipo extraídos (NativeBackend 2070→1741, REFACTOR-500)
  - FASE 3.5 — calls String/JSON x86_64 extraídos p/ NativeX86StringCalls (2286→2070, REFACTOR-500)
  - FASE 3.4 — emissores cross riscv64 extraídos (2 classes ≤500, NativeBackend 2850→2286, REFACTOR-500)
  - FASE 3.3 — HTTP/spawn riscv64 extraídos (3 classes ≤500, NativeBackend 3632→2850, REFACTOR-500)
  - FASE 3.2 — tradutor riscv→aarch64 extraído p/ NativeAarch64Translator (4113→3632, REFACTOR-500)
  - FASE 3.1 — constantes asm riscv64 fora do NativeBackend (8834→4113, REFACTOR-500)
  - ExpressionStaticCallLowerer 502→493 — construtor Canvas move p/ ExpressionUiStaticLowerer (REFACTOR-500 varredura)
  - F2.44 extrai CompilerOrmSupport (ORM + super-bridges)
  - F2.43 extrai CompilerFunctionLowering (funções top-level)
  - F2.42 extrai CompilerRecordSupport (métodos sintéticos de record)
  - F2.41 extrai CompilerClassLowering (lowering de classes)
  - F2.40 extrai CompilerComparisons (comparação + retorno)
  - F2.39 extrai CompilerLambdaClass (geração de classes lambda)
  - F2.38 extrai CompilerAnnotations (lowering de anotações)
  - F2.37 extrai CompilerCaptureScanner (análise de capturas)
  - F2.36 extrai CompilerCaptures (coleta de capturas de lambda)
  - F2.35 extrai ExpressionOrmCallLowerer (ORM estático)
  - F2.34 extrai ExpressionJsonCallLowerer (json.encode/decode)
  - F2.33 extrai ExpressionInstanceCallLowerer (dispatch de instância)
  - VkChain64Asm 3568 -> 57 (wrapper source() concatena 15 classes por dominio, REFACTOR-500)
  - VkChain64Dispatch — kof_vk_dispatch64 (REFACTOR-500)
  - VkChain64WSp — wputsp + wrunsp (REFACTOR-500)
  - VkChain64W32 — wput32 + wrun32 (REFACTOR-500)
  - VkChain64W64 — wput + wrun (REFACTOR-500)
  - VkChain64Matvec — load_w + matvec (REFACTOR-500)
  - VkChain64Shape — set_shape + shape_xy (REFACTOR-500)
  - VkChain64Submit — submit + write_desc (REFACTOR-500)
  - VkChain64Helpers — helpers fail/trace (REFACTOR-500)
  - VkChain64Loader — dlopen/dlsym libvulkan (REFACTOR-500)
  - VkChain64Data — .data/.bss/.rodata (REFACTOR-500)
  - VkChain64Alloc — vk64_alloc_buffer (REFACTOR-500)
  - VkChain64Init — init_common parte A (REFACTOR-500)
  - VkChain64InitSpv — init parte B: spv/shader/pipelines (REFACTOR-500)
  - VkChain64Init2 — pipe32+split opcionais (REFACTOR-500)
  - VkChain64InitPools — init pools (pipe32/split/pools de VkChain64Asm, REFACTOR-500)
  - F8.6 JvmBackend sem resíduos (REFACTOR-500)
  - F8.5 extrai JvmOpEmitter de JvmBackend (REFACTOR-500)
  - F8.4 extrai JvmOpCollections de JvmBackend (REFACTOR-500)
  - F8.3 extrai JvmLiteralEmitter de JvmBackend (REFACTOR-500)
  - F8.2 extrai JvmRecordEmitter de JvmBackend (REFACTOR-500)
  - F8.1 extrai JvmAnnotations de JvmBackend (REFACTOR-500)
  - F2.32 extrai ExpressionUiMediaCallLowerer (Ui/Media/Io estáticos)
  - F2.31 extrai 5 lowerers de namespace (http/time/mq/config/cache)
  - F2.30 extrai ExpressionLogCallLowerer (log.*)
  - F2.29 extrai ExpressionSchedulerCallLowerer (scheduler.*)
  - F2.28 extrai ExpressionProcessCallLowerer (namespace process.*)
  - F2.27 extrai ExpressionDbCallLowerer (namespace db.*)
  - F2.26 extrai ExpressionPrintLowerer (print/println)
  - F2.25 extrai ExpressionUiStaticLowerer (Icon/Font/Button/Component/Store)
  - F2.24 extrai ExpressionStaticCallLowerer (branches receiver-null)
  - FASE 6 — renomeia ExpressionTyper/MethodCallTyper → Sem* (colisão de nome com F2.15/F2.16 do CompilerDriver)
  - FASE 4.6 — remove classe placeholder vazia JsMethodCtx (REFACTOR-500)
  - FASE 4.5 — divide parsing/lowering em 8 classes coesas ≤500 (REFACTOR-500)
  - FASE 4.4 — extrai MethodCtx/LoopCtx/NewPending/DupMarker/StatementEnd + helpers estáticos (REFACTOR-500)
  - FASE 4.3 — extrai JsLoweringContext (estado compartilhado do lowering) (REFACTOR-500)
  - FASE 4.2 — extrai JsTypeMapper (helpers puros de nome/tipo) (REFACTOR-500)
  - FASE 4.1 — extrai runtime constants + JsArtifactWriter (REFACTOR-500)
  - F1.16 divide RuntimeJsonDecode em 2 (517→292)
  - F2.21 extrai CollectionCallLowerer (branches List/Channel/Map/Set)
  - REFACTOR-500 F6 (6/6) — remove SSE_CONNECTION_TYPE órfã do SemanticAnalyzer
  - FASE 7 — divide Parser em StatementParser/ExpressionParser/LambdaParser/TypeParser/AnnotationParser/ClassMemberParser (REFACTOR-500)
  - FASE 7 — cria ParseContext (estado compartilhado do parsing, REFACTOR-500)
  - REFACTOR-500 F6 (5/6) — extrai ExpressionTyper + StatementAnalyzer + 3 typer de MethodCallExpr
  - REFACTOR-500 F6 (4/6) — extrai SymbolTableBuilder
  - REFACTOR-500 F6 (3/6) — extrai TypeChecker
  - REFACTOR-500 F6 (2/6) — extrai MemberResolver
  - F2.20 extrai ExpressionAssignmentLowerer + ExpressionBinaryLowerer
  - REFACTOR-500 F6 (1/6) — SemanticAnalyzer expõe estado via accessors
  - F2.19 extrai ExpressionMethodCallLowerer (case MethodCallExpr, 2205 linhas)
  - F2.18 extrai ExpressionLowerer (emitExpression, 3169 linhas)
  - F2.17 extrai CollectionMethodTyper (List/Map/Set/String)
  - F2.16 extrai MethodCallTyper (case MethodCallExpr do inferExprType)
  - F2.15 extrai ExpressionTyper (inferExprType)
  - F2.14 divide StatementLowerer — SwitchStmtLowerer + SwitchExprLowerer
  - F2.13 extrai StatementLowerer (emitStatementInner + switch-expr)
  - F2.12 adiciona resolveWithTypeParams/substituteTypeVariable/defaultValueOp ao CompilerTypes
  - FASE 8 — Main 1229 → 375 (dispatcher + comandos pequenos) (REFACTOR-500)
  - FASE 8 — extrair CmdBuild/CmdRun/CmdTest/CmdScript/CmdServe + KofCliSupport de Main (1229) (REFACTOR-500)
  - F2.11 adiciona type/enum/record helpers ao CompilerTypes
  - F2.10 extrai CompilerImports (expandKofImports + declarationName)
  - FASE 8 — KofJsRunner 568 → 419 + KofJsWebview (162) (REFACTOR-500)
  - F2.9 extrai CompilerDesugar (desugarTests/desugarApplication/buildTestHarnessMain)
  - FASE 8 — Bench 630 → 286 (orquestração só) (REFACTOR-500)
  - FASE 8 — extrair BenchRunners (149) de Bench (630) (REFACTOR-500)
  - FASE 8 — KofScript 608 → 479 + KofScriptExecutor (164) (REFACTOR-500)
  - FASE 8 — extrair BenchBaseline (162) de Bench (630) (REFACTOR-500)
  - F2.8 extrai ModuleRoots (moduleRootFor/commonAncestor/derivedPackageOf)
  - FASE 8 — extrair BenchDiscovery (100) de Bench (630) (REFACTOR-500)
  - F2.7 extrai JsonDispatch (encode/decode/listTag/sanitize)
  - F2.6 extrai HierarchyResolver (5 métodos de hierarquia)
  - F2.5 extrai BoxClassFactory (criação de box mutável)
  - F2.4 extrai CompilerTypes (toType/qualifyViaImports/ownerTypeFromInternal/mainClassType)
  - F2.3 extrai TypeEmitter.boxPrimitive
  - FASE 8 — JvmVkRuntime 995 → wrapper + 3 partes ≤500 (REFACTOR-500)
  - FASE 8 — JvmMediaRuntime 673 → wrapper + 2 partes ≤500 (REFACTOR-500)
  - FASE 8 — JvmWebRuntime 716 → wrapper + 2 partes ≤500 (REFACTOR-500)
  - FASE 8 — JvmStringRuntime 983 → wrapper 15 + 5 partes ≤500 (REFACTOR-500)
  - F2.2 extrai StringMethodRegistry (assinaturas de String/Object)
  - F2.1 extrai TypeMetrics (11 helpers de tipo puros)
  - F1 final — RuntimeSecurityData extrai rodata; todas as Runtime* ≤500
  - F1 COMPLETA — NativeRuntime 17726→141 linhas, orquestrador puro
  - F1.15 divide JsonArrayDecode/Log/Config/Io/Db — NativeRuntime 5564→267 linhas
  - F1.14 dedup — RuntimeJsonUtils/Cache/Time/Ui ligados ao NativeRuntime
  - F1.13 extrai security/validation/observability/map/set/enum
  - F1.12 extrai RuntimePrintNum/RuntimeNet/RuntimeVk/RuntimeMisc
  - F1.11 extrai RuntimeConcurrency/Channel/Scheduler/Mq
  - F1.10 extrai RuntimeMemory + RuntimeGc (alloc/free/gc/exit/panic/errors)
  - F1.8 extrai RuntimeArray (alloc/length/get/set)
  - F1.7 extrai RuntimeList (kof_list_new/grow/add/get/set/...)
  - F1.6 extrai RuntimeStringBase (from_literal/memcpy/length/concat/equals/print_string)
  - F1.5 extrai RuntimeStringOps + RuntimeStringEdit
  - F1.4 extrai RuntimeStringSearch (contains/startsWith/endsWith/indexOf/lastIndexOf)
  - F1.3 extrai RuntimeStringParse (toInt/toLong/toDouble/toFloat)
  - F1.2 extrai RuntimeStringConv (int/char/long/bool/float/double→string)
  - FASE 8 — Optimizer 611 → 240 (REFACTOR-500)
  - FASE 8 — OptimizerConstantFold (388) — passe de constant folding (REFACTOR-500)
  - FASE 8 — NativeWebRuntime 603 → 25 (REFACTOR-500)
  - FASE 8 — NativeWebResponses (122) — helpers de resposta (REFACTOR-500)
  - FASE 8 — NativeWebListen (355) — listen + handle_client (REFACTOR-500)
  - FASE 8 — NativeWebCore (156) — dados + primitivas web (REFACTOR-500)
  - FASE 8 — NativeHttpRuntime 652 → 30 (REFACTOR-500)
  - FASE 8 — NativeHttpCore (392) — request core + wrappers (REFACTOR-500)
  - FASE 8 — NativeHttpParseUrl (172) — parse URL + erro https (REFACTOR-500)
  - FASE 8 — NativeHttpPrimitives (113) — data + buffer helpers (REFACTOR-500)
  - FASE 8 — JdwpClient 503 → 415 + JdwpPacket (100) (REFACTOR-500)
  - FASE 5 — JvmRuntime 2526 → 132 + 7 classes ≤500 (REFACTOR-500)

### Tests

  - fecha bug 52 — re-throw em catch já tinha paridade JS (colateral do 45)
  - kitchen-sink com ORACLE strict-verifier (P0 parte 4)
  - gate de CI da matriz — doc × exclusões do teste (Fase 9)
  - comentário do freshDriver atualizado (bug 51 corrigido em b7afc5a)
  - bug 48 — regressão decode<List<Record>> no interpretador + matriz
  - ConformanceMatrixTest lote 1 — linguagem core nos 4 targets
  - cross-target sweep JS/Native (regra 5) — bugs 41-45 registrados + gate JVM×JS
  - casos dos bugs 35/36/static-field travados em JVM×JS
  - gate de paridade permanente interpretado vs JVM (15 casos)
  - guard Assumptions.assumeTrue (qemu ausente) em time/scheduler cross — convenção NATIVE002
  - E2E riscv64/aarch64 Map/Set (paridade exata com x86_64)

## [0.3.2-beta] - 2026-09-09

### Features

  - add Fieldset, Iframe, Video, Audio, Hr widgets - UI003

### Bugfixes

  - parâmetro após um `Long`/`Double` deixa de sumir da assinatura (GitHub #47)
  - atribuicao parametro let

### Documentation

  - corrigir links internos para docs/development (#49)
  - registra o bug 64 (KofJS descarta parâmetro após Long/Double)
  - reverifica o inventário contra o build 0.3.1-beta e corrige a entrada 45
  - registra bugs 62 e 63 (mutabilidade não validada; let redeclarado em parâmetro no KofJS)
  - bug 6 — remove seção duplicada/desatualizada em known-bugs.md

## [0.3.3-beta] - 2026-09-09

### Bugfixes

  - sintetizar hashCode() para records no backend nativo (#55)

## [0.3.4-beta] - 2026-09-09

### Documentation

  - add GitHub issue form templates

## [0.3.5-beta] - 2026-09-09

### Features

  - S3.1c — strings.escapeJson nos 5 backends (RFC 8259, oracle Python)
  - add escapeJson function for JSON string escaping
  - add support for multidimensional arrays and related operations
  - SECN000 fechado — uuid.v4 riscv/aarch (B25, getrandom ecall 278) + paridade variant x86 (máscara 10xx)
  - S8-C riscv/aarch — net.* portado (B24) — NET001 FECHADO
  - Fase E — arrays (anewarray + acessos) no statements-path
  - S8-B x86 — net.* URI-parse nos nativos x86 (RuntimeUri, NET001 só riscv/aarch)
  - Fase E — new/dup/init em statement-bodies
  - §7 degrau 4 — tipos de assinatura registram import
  - S8-wedge net nos 6 alvos não-nativos — decisão de shape + NET001 gate
  - §7 degrau 3 — imports cross-package com regra de não-ambiguidade
  - ENC002 fechado — base64/base64Url riscv B23 + aarch64 (port)
  - §7 degrau 2 — índice same-package resolve instanceof/cast de domínio
  - S3.1b — strings.unescapeHtml nos 4 targets
  - #58 — issue forms (bug_report + feature_request + config)
  - S3.2 — strings.removeWhitespace/normalizeWhitespace nos 4 targets
  - §7 degrau 1 — kof decompile <dir> com package + fix pop sem operando
  - S3.1 — strings.escapeHtml nos 4 targets, sem gate
  - Fase E — pop/instanceof/checkcast com whitelist R6-segura
  - S6c — validation.isDomain nos 4 targets, sem gate (RFC 1123 v1)
  - blockerSink — medidor da fila Fase E (custo zero) + ROI medido
  - S6b.3 — validation.isIpv6 nos 4 targets, sem gate
  - S6b — validation.isCreditCard (Luhn) nos 4 targets, sem gate
  - S6a — validation.isIpv4/isMac/isPort nos 4 targets, sem gate
  - STRN001 FECHADO — word-converters (joinWords) portados p/ riscv64 B15 + aarch64
  - i2c → as Char (único narrowing fiel); i2b/i2s ficam stub honesto
  - S7.2 — time.dayOfWeek/daysBetween nos 4 targets, sem gate
  - bug 62(b)/(c) — escrita em componente de RECORD é imutável (SEM037)
  - S7-wedge — time.isLeapYear/daysInMonth nos 4 targets, sem gate
  - bug 62 (a) — val é imutável (SEM037) + parser carrega type=val
  - S5 — validation.isCpf/isCnpj/isCep/isPis nos 4 targets, sem gate
  - S3b-wedge — uuid.v4 (RFC 4122) nos 3 targets testáveis + SECN000 gate cross-arch
  - decompile trata ldc_w (maior gap real do corpus) e aconst_null
  - S4.2c — encoding.base64UrlEncode/Decode (RFC 4648 §5) + split RuntimeEncoding ≤500
  - JSON runtime improvements and self-check update
  - S4.2b — encoding.urlEncode/urlDecode (RFC 3986) nos 4 targets, sem gate
  - decompile recupera aritmética/casts long+double com guarda de tipo
  - S4.2a — encoding.base64Encode/Decode (reuso dos internals x86 + gate ENC002 cross-arch)
  - S4-hex — namespace encoding com hexEncode/hexDecode nos 4 targets
  - S2b.4 — word-converters (toCamel/Pascal/Snake/Kebab/slugify) + split ≤500 (RuntimeStrings, JsCrypto)
  - decompile recupera String concat via invokedynamic (J9+ BootstrapMethods)
  - S2b.3 — strings.padLeft/padRight nos 4 targets + fix do limite de 64KB na cadeia riscv
  - S2b.2 — strings.repeat/truncate nos 4 targets
  - S2b-wedge — strings.capitalize/reverse nos 4 targets (1º conversor que alocou String)
  - Fase C/E — ldc2_w (const Long/Double) no kof decompile
  - S2a.3+S2a.4 — strings.count (não-sobrepostas) + isUpperCase/isLowerCase nos 4 targets
  - Fase C/E — lconst/dconst no kof decompile (lesson bug 62 aplicada)
  - S2a.2 — strings.isAlphaNumeric/isAscii nos 4 targets (paridade ASCII travada)
  - S2a — hook KofStd unificado + kof.strings isAlpha/isNumeric nos 4 targets
  - S1 kof.math Int-only nos 4 targets (clamp/abs/sign/min/max/isEven/isOdd/isPositive/isNegative/isZero)
  - Fase C — do-while (bottom-tested loop) no kof decompile
  - UI006 residual FEITO — Event.target()/relatedTarget() com prova browser corrigida
  - add target and relatedTarget event accessors for UI006
  - codeAction source.format (EDI001 §15 — último bullet)
  - UI002 — warning único quando kof.ui roda no interpretador (R6)
  - UI006 — Event key/value/x/y + widget.on() (DOM real em KofJS)
  - documentSymbol (outline) + LspHover extraído (EDI001 §15)
  - UI003 — Fieldset/Iframe/Video/Audio/Hr (DOM real em KofJS)
  - Fase D — Type Recovery (Signature JVM com genéricos)
  - F9(c) — Android/Wasm documentados + --target=wasm honesto (R6)
  - textDocument/formatting — delega ao KofFormatter (EDI001 §15)
  - EDI001 §18 — kof.target flui para build/run/test da extensão
  - EDI001 — extensão VS Code completa (extension.js + snippets, §3/§19)
  - textDocument/definition — go-to-definition same-file (EDI001 §15)
  - EDI001 degrau 11 — hook pós-instalador oferece integrações (§13)
  - EDI001 degraus 3+4-10 — install/uninstall/setup com consentimento + conteúdo idiomático por editor
  - EDI001 degraus 1-2 — infra EditorIntegration + kof editor (read-only)

### Bugfixes

  - bug 71 — new T[a][b] cria TODAS as dims (KofNewMultiArray: MULTIANEWARRAY JVM / Array.newInstance interp / kofMultiArray JS) — restaura gate quebrado no remote (53264c9f era meio-de-grau)
  - #65 transaction aninhado comita o escopo externo — rollback não desfaz
  - #67 kof build ignora .kof (só varre .kf) e responde no .kf files found
  - #66 LNT apontava o statement seguinte (pos pós-ponto-e-vírgula + cópia HashMap colidindo records iguais)
  - #64 += em elemento de array e campo estático qualificado sobrescreve em vez de somar
  - #63 2 labels de debug consecutivos no mesmo pc → LNT inválida (ClassFormatError no load)
  - #62 signature genérica de type-arg primitivo usava descriptor cru (D) → GenericSignatureFormatError
  - resolve SIGSEGV in spawn expressions by correcting type inference for lambda returns
  - #60 — handle de conexão nunca reutilizado (contador monotônico)
  - §70 — join heterogêneo primitivo-vs-primitivo (Int/Long/Double/null) sem crash e sem widening
  - #57 — if/switch heterogêneo primitivo-vs-referência não gera mais VerifyError no JVM
  - sintetizar hashCode() para records no backend nativo (#55)
  - ldc escapa constantes de string — DRIFT 69→5 no corpus (09/09)
  - 3 bugs de parsing/length que travavam bytecode REAL (601 classes)
  - #54 — <init> NÃO é virtual no interpretador (super(v) recursiva)
  - bug #54 — super(v) explícito despacha p/ <init> da SUPERCLASSE (era recursão no ctor → StackOverflowError)
  - #53 metades JS/Native/script — super(Record.<init>) só no JVM
  - bug 66 (#53) — record com ctor explícito canônico não gera <init> duplicado
  - #42(c) — SEM038 em this.x= de MÉTODO de record (exempt só construtor) + testes
  - #42 — SEM038 escrita em componente de record + SEM037 no update do for
  - bug 50 — futex WAIT do canal x86_64 com args corretos (uaddr=&lock, op, val)
  - #52 — reconstruir agrupamento por precedência/associatividade
  - String.toInt/toLong/toDouble/toFloat no runtime (GitHub #51)
  - bug 48 json.decode<List<Record>> gap honesto JSN004 + bug 59 regressão riscv/aarch estáticos
  - B5-B9 emitiam código em .rodata (herdado do B4) — crash em runtime
  - decompile nunca emite owner java/jdk sem mapeamento (R6)
  - slot de xstore_0..3 wide era (op-0x3f) sem %4 (bug da unit anterior)
  - decompile mapeia slots wide (R6 — Long/Double = 2 slots)
  - bug 62 — CP Float/Double como bits crus; ldc recusa float p/ não driftar
  - Fase C — R6: nunca emitir código errado p/ join compartilhado
  - status reflete o marker mesmo com editor ausente do PATH
  - SEM036 — função não-void que pode terminar sem return (bug 26 variante)

### Documentation

  - STDLIB — registra gate quebrado no remote (53264c9f multidim/bug71 WIP: ArrayFiller nunca criado + import KofNewMultiArray ausente em JsExpressionParser) — build limpo falha; escapeJson S3.1c FEITO (d57f8e5c) suíte verde antes do rebase; lane pausa por colisão (bug 71 do autor)
  - #62 docs de Kof (docs de Kof) — docs
  - #61 respondida (design da mantenedora) — sessão issues 62-67
  - switch-Fase-C em espera (stash WIP); #62 assumida; #61 não (design)
  - bugs 46/50 verificados pós-fix órfão (a617d840) — testes verdes, linha bug-fix atualizada
  - junta linha da lane STDLIB rachada por newline (`\\n` literal dentro do replace — contagem de pipes 7 restaurada)
  - #60 fechada c/ evidência; convenção koftmp; próxima fila
  - gotcha LEX004 — lexer pré-processa \\uXXXX antes do token
  - #55/#57/#58 fechadas c/ evidência; próximo = degrau 2 multi-classe ou §§69-70
  - #55 FECHADA (cherry-pick main def86a5a→c57855fd, prova recordhash 4 targets)
  - Fase E — pop/instanceof/cast feitos (3ca20067); próximo degrau = multi-classe §7
  - fila Fase E CORRIGIDA pela prova de drift — pop/instanceof/cast bloqueados por multi-classe, não por eles
  - migração — 601/601 robustez (ff2369f6); fila Fase E = 1812 stubs/3306
  - 39-stdlib — tutorial da standard library universal (S9.2)
  - #54/bug 67 — corrigido registro da causa raiz (o 8968c883 sozinho NÃO fechava) + DOING com evidência
  - SG-006 resolvido — short-circuit && paridade travada por teste (suíte verde)
  - bug 46 — registra teste de isolamento (sem captura)
  - registrar que #54 (bug 67) foi corrigido pelo lane bug-fix — agente migração não deve refazer
  - bug 67 (#54) super(v) interp corrigido + known-bugs + DOING
  - bug 50 — registra teste de validação channelWithSpawnNative
  - #53 FECHADA (203096e4) + #54 aberta (interp super(v), sem dono) — próximo passo do loop
  - SG-010 resolvido — val imutável (SEM037, bug 62a)
  - 'ERRO de runtime: record é imutável' → 'ERRO de compilação SEM038' (training/language/classes + learn/07)
  - learn/08 — p.x=99 é erro de COMPILAÇÃO SEM038 (não runtime)
  - records — documenta imutabilidade (SEM038, bug 62)
  - concurrency — documenta spawn { return ... } lambda literal + Handle (bug 46 Native gap)
  - bug 65 — registra verificação do pipeline JS (descarta codegen; causa = timing runtime no browser)
  - corrigir \\n literal que mesclou as linhas STDLIB e EDI001
  - SG-006 — análise: JS emite &&/|| nativos com short-circuit; recomendar teste de paridade
  - bug 46 — registra teste de regressão + confirmação SIGSEGV + nota sobre causa x86_64
  - remover pipe extra órfão na linha STDLIB (herança do commit S5)
  - bug 66 corrigido; estado atualizado
  - #42 FECHADA (a/b/c, ed0475c8) — linha da sessão atualizada
  - DD-02 → APLICADO (erro direto) + link p/ #53 (record+ctor JVM, aberto); issue #53 criada com repro mínimo
  - bug 62 completo (a val SEM037 + b/c record SEM038); restam 5 exigem ambiente/regra 6
  - bug 62 (mutabilidade) completo — (a) val SEM037, (b)/(c) record SEM038
  - #42 62(b)/(c) FEITO (72e79b9f) — não retocar; mantém bloco 9364b973 da lane bug-fix
  - consertar linha da lane STDLIB — S7-wedge dentro da linha + PRÓXIMO PASSO reordenado
  - bug 50 correção candidata (futex WAIT args) + DOING estado real
  - estado real dos bugs apos a sessao (48,59,62a corrigidos; doc atualizado)
  - marcar bugs 43,44,63,64 como corrigidos (fixes já no código com testes)
  - marcar bugs 7,9,18,21,22,23,30 como corrigidos (doc desatualizado)
  - DD-02 — design dos validadores de mutabilidade (val/record, #42)
  - normas de desenvolvimento colaborativo da pré-beta 0.3.0 no código de conduta
  - training/idioms/stdlib.md — os 4 namespaces STDLIB com BAD/GOOD/WHY
  - S4 completo — linha STDLIB na matriz de módulos + nota do plano
  - contagem da suíte pós-merge (1249/0/64-skip)
  - update PRÓXIMO PASSO and remove legacy heartbeat (auto-loop.sh ativo)
  - add specification-gaps.md with compiler gaps (R6, HW001, CONC001, etc.)
  - registra ldc2_w no DOING + IMPLEMENTATION_PLAN (follow-up d953d92)
  - STDLIB S2a.2 FEITO (257b9b0) — linha de PRÓXIMO PASSO corrigida (fica em S2a.3)
  - PRÓXIMO PASSO — estender emitLinear p/ long/casts c/ guard de tipo (lesson bug 62)
  - sweeps R6 do decompiler (control-flow + numérico) — tudo degrada honesto
  - STDLIB S1+S1a FEITO (d0b829a) — registro da lição do inline de constant JS + PRÓXIMO (S1b math Double, S2 strings)
  - sincroniza tabela de testes (DoD-docs) — 1218 total, UiE2ETest 27, browser 16
  - UI005 readonly/name FEITO na matriz + índice README desatualizado corrigido
  - RETRATA 2 falsas falhas UI (build stale meu pós-rebase; suíte fresca 1091/59bug59 + 126 demais = verde) + EDI001 codeAction FEITO (4329898)
  - UI002 FEITO (warning único no interpretador) — matriz + DOING
  - codeAction feito + registro das 2 falhas UI003/UI006 (lane UI, regra 3)
  - esclarece contagem da suíte (qemu presente vs ausente)
  - suíte completa exige -Dmaven.test.failure.ignore=true (lição 08/09)
  - PRÓXIMO PASSO — fila UI (UI002 warning, UI006 residual, UI007 bloqueado)
  - UI006 FEITO (key/value/x/y + widget.on) — matriz + corpus
  - UI003 FEITO (fieldset/iframe/video/audio/hr) — matriz + corpus
  - Fase D (Type Recovery) marcada completa — provas 367d6c4
  - DD-01 — finally no caminho return (bug 45) vira plano de design + limpeza
  - 38-editors — tutorial kof editor (§27 pede corpus passo-a-passo)
  - PRÓXIMO PASSO — varredura de exclusões obsoletas da matriz de conformância
  - degrau 12 — docs/editors/* + corpus (training/cli, EDITOR_SUPPORT)
  - DOING — degrau 11 feito (hook pós-instalador, e7e3564)

### Refactoring

  - S1a stdlib — JvmRuntimeCallDescriptors 504→354 (callReturnDescriptor → JvmRuntimeReturnDescriptors, gate ≤500 limpo na lane) + docs/development/plan-stdlib-expansion.md (mapeamento da arquitetura real da stdlib: Kof<Domain>.java→typer→3 backends, existente vs lacunas P0-P2, degraus S1-S9) + claim STDLIB no DOING

### Tests

  - reabilita native p/ channel-spawn (bug 50) e adiciona spawnexpr-return (bug 46)
  - json.decode<List<Record>> Native dá JSN004 (gap honesto, não link fail)
  - isolamento spawn { return 42 } sem captura — separa causa captura vs return lambda
  - bug 50 — channel+spawn no Native (valida fix candidata futex WAIT quando build disponível)
  - SG-006 — short-circuit && no JS/JVM (String? null && length > 0 não NPE)
  - spawn-expr lambda literal com return + handle no Native; docs bug 61 gap honesto FFI001
  - Fase C — trava degradação honesta de do-while com corpo ramificado
  - Fase C — trava laço aninhado + corpo não-linear (verificação com probes)
  - staticfield/staticpluseq nos 4 targets (bug 41 já corrigido no Native)

## [0.3.6-beta] - 2026-09-09

### Features

  - introduce kof.random namespace with various random generation functions and runtime support

## [0.3.7-beta] - 2026-09-10

### Bugfixes

  - preservar operandos da pilha em if/switch-expressions (#69)

## [0.3.8-beta] - 2026-09-10

### Features

  - implement kof.random namespace with double, boolean, int, and hex functions; add tests for cross-platform compatibility

### Bugfixes

  - random.double() usava 2^52 como divisor (constante .Lrnd_two53 errada) — corrigir para 2^53 nos runtimes x86 e riscv/aarch; docs: gravar regra de organizacao de documentacao no AGENTS.md + known-bugs SS79 + DOING.md

## [0.3.9-beta] - 2026-09-10

### Documentation

  - consolidar classificacao docs/development pela nova regra - mover planning-switch-expr + planning-mutability (FEITOS) p/ docs/, planning-finally-return (PROPOSED/zero codigo) p/ future/; classificar plan-stdlib-expansion por degrau (S7 unico aberto); atualizar README do indice + DOING.md

## [0.3.10-beta] - 2026-09-10

### Features

  - time.addDays/diffDays em data ISO (JVM/Script via java.time) + gate TIME002 em JS/Native; matriz stdtime2 + KofTimeE2ETest + docs (stdlib/learn/matrix/plan); DOING.md

## [0.3.11-beta] - 2026-09-10

### Features

  - time.addDays/diffDays em JS (algoritmo civil sem Date, paridade byte-idêntica); TIME002 resta só Native; matriz stdtime2 JS DONE + gate KofTimeE2ETest + docs (stdlib/learn/matrix/plan); DOING.md

## [0.3.12-beta] - 2026-09-10

### Bugfixes

  - pad4 de ano em time.addDays JS (parity byte-idêntica com JVM %04d; ano<1000 divergia) + stdtime2 trava 0999/0001/1700-02-28; DOING.md com design fechado do S7c x86

## [0.3.13-beta] - 2026-09-10

### Features

  - time.addDays/diffDays no native x86 (RuntimeTimeIso — parse ISO + inversa civil Hinnant asm; round-trip exaustivo 1..9999 + fuzz C 200k) + gate TIME002 afunilado p/ riscv/aarch (precedente NET001) + matriz stdtime2 roda x86 local + docs; DOING.md

## [0.3.14-beta] - 2026-09-10

### Bugfixes

  - transaction aninhado no native x86 não comita o escopo externo — .Ldb_tx_handle dono + flag-owner no record do try (paridade §77 JVM) + teste E2E sqlite; known-bugs/DOING

### Documentation

  - carry JS bool = false alarm (matriz stdmath prova println true/false no JS; isBoolOperand converte 1/0) — fechar após ler randomShapeJs
  - PRÓXIMO PASSO refina S7c-1 (bloqueio qemu/toolchain documentado) + tarefa (B) paridade JS bool como próxima executável sem qemu

## [0.3.15-beta] - 2026-09-10

### Bugfixes

  - math.is*/random.boolean retornam boolean JS real — boolExpr==true funciona no JS (paridade JVM/Native); randomShapeJs ganha o assert que native já tinha; FALTAM strings/validation/security (guards)

### Documentation

  - §80 math/random feito nesta sessão; PRÓXIMO PASSO = strings/validation/security (guards)
  - plan-stdlib-expansion + stdlib.md + learn/39 atualizados — addDays/diffDays x86 FECHADO (TIME002 residual só riscv/aarch)
  - status PARCIAL — math/random corrigidos; strings/validation/security documentados c/ nota dos guards return 0
  - bug paridade JS Bool — boolExpr==true sempre false (funções stdlib retornam 1/0; print coerce mas == usa === cru; evidência no .mjs gerado) + PRÓXIMO PASSO com fix (opção A, todos os sites incl guards validation)

## [0.3.16-beta] - 2026-09-10

### Bugfixes

  - Bool==true no JS normalizado no chokepoint da comparação — paridade JVM/Native

### Documentation

  - S7c Native x86 addDays/diffDays FEITO (cd622c47) — header estava stale

### Tests

  - stdstrings ganha caso `isAlpha("Hello") == true` — 4 targets
  - matriz stdmath ganha os casos `==true`/`==false` — 4 targets travam a comparação

## [0.3.17-beta] - 2026-09-10

### Tests

  - stdvalidation ganha `isCpf==true`/`isCpf==false` — fecha a família flagged como FALTAM

## [0.3.18-beta] - 2026-09-10

### Documentation

  - §80 fechado + endurecido (matrizes stdmath/stdstrings/stdvalidation); PRÓXIMO PASSO = S1b math Double (ou uuid.isUuid p/ risco menor)

## [0.3.19-beta] - 2026-09-10

### Features

  - uuid.isUuid(STR)->Bool — 4 targets, gate UUID001 honesto riscv/aarch

## [0.3.20-beta] - 2026-09-10

### Refactoring

  - JvmStringMathRuntime 504→465 + JvmUuidRuntime novo (58) — split do próprio 79d2668a

## [0.3.21-beta] - 2026-09-11

### Features

  - implementar uuid.v7() ordenado no tempo conforme RFC 9562

<!-- NEXT-RELEASE -->

## [0.2.7-beta] - 2026-09-04

### Features

  - log níveis + cache real + mq push/pop real — suíte 842/0
  - WEB002 T1 — accept loop HTTP/1.1 no Native (kof_web_listen+handle_client; respondendo 200/hello fixo a qualquer request — valida listen→accept→read→write→close; routing+dispatch é T2/T3). Módulo novo NativeWebRuntime.java (≤500 linhas); CompilerDriver libera web.app/listen para NATIVE_*. Suíte 840/0
  - prepared statements com QUERY binário — parse de binary-rows (COM_STMT_EXECUTE)
  - GC mark-sweep real (sweep funcional, auto-collect desligado)
  - close HTTP002 — kof.http no Native (asm HTTP/1.1)
  - COM_STMT_PREPARE/EXECUTE binário — kof_db_mysql_prepare + kof_db_mysql_exec (NativeDbPrepared.java, novo módulo ≤500 linhas)
  - validation 13/13 + observability real (counter/gauge/histogram/metrics) em asm puro
  - stubs NATIVE002.1 — kof.log/config/time/observability/cache/mq em asm puro
  - add roadmap gap report for NATIVE002 core completion
  - CORE COMPLETO em asm puro via tradução riscv→aarch64 (NATIVE002) — 13/13 E2E qemu
  - close TIME001 — time.interval/cancel no KofJS via fila cooperativa
  - CORE COMPLETO em asm puro (NATIVE002 parcial) — 13/13 E2E qemu
  - package manager MVP — kof deps (kofdeps, Maven Central, --deps)
  - application { onStart/onShutdown } — construcao de intencao
  - spans W3C com timing (spanStart/spanEnd) nos 3 targets
  - close LOG001 (kof.log on JS) + runtime fixes
  - add platform invariants and gap conventions to AGENTS.md and backend-parity.md; introduce ACTION_PLAN.md for future implementation roadmap
  - riscv64 real (NATIVE002 parcial) — kof_main em asm + runtime C via gcc cruzado + qemu (NativeRiscv64E2ETest 4/4)
  - captura mutável de lambda — mutação fora da lambda refletida
  - DWARF line table real no ELF x86-64 (.file/.loc GAS — Fase 5 parcial do debugger)
  - source map V3 real (mappings VLQ em nível de linha — função gerada → linha Kof)
  - Enhance array creation handling in JvmBackend for reference types
  - Query DSL tipada nível 3 — User.query(db){ where; orderBy; limit } (ORM001)
  - MQ001 — kof.mq no Native (pub/sub + filas in-process em asm, paridade JVM/JS)
  - readLine → String? (null no EOF) + docs exemplos desatualizados
  - transaction {} — commit/rollback real (BEGIN/COMMIT/ROLLBACK + EH)
  - TIME001 — time.interval/time.cancel no Native (reusa o scheduler)
  - OBS002 — histogram/metrics no Native (store asm + export Prometheus)
  - add primitive widening and narrowing for array store operations to prevent verifier errors
  - enhance AES-GCM support for JS target and add cross-target parity tests
  - add support for Channel type in various components and tests
  - add maven-surefire-plugin configuration to include specific test files
  - add tracing for 'add' method calls to enhance debugging
  - add tracing for return value of 'add' method calls to aid debugging
  - add tracing for resolved owner class in KofPop to aid debugging
  - add L2I unary operation support and enhance type casting for primitives
  - implement local HTTP server for serving appDir and open in system browser
  - add support for I2C conversion in constant folding
  - add support for I2C unary operation and enhance type casting for primitives
  - enhance handling of built-in types as static receivers to prevent frame crashes
  - enhance method call handling for built-in types to prevent ClassFormatError
  - update kof_io_read_range and kof_io_read_range_path to use long for length parameter; enhance file reading with offset support
  - File.readRange(offset, len) e File.readRangePath(path, offset, len) — leitura com offset p/ arquivos grandes (GGUF de LLM) sem carregar o inteiro; JVM via RandomAccessFile (kof_io_read_range/_path)
  - add traceId and spanId functions; enhance LSP server capabilities
  - add built-in health check endpoint and update related tests
  - implement implicit join for main function tasks to prevent orphaned threads
  - add video handling support with metadata extraction and streaming capabilities
  - build.sh da libvkchain (compila + instala)
  - M32.3 — dispatch Vulkan compute REAL nos 2 backends
  - implement non-blocking done/poll methods and cooperative cancellation in native backend
  - String.lastIndexOf — kof_string_last_index_of (varredura reversa do fim p/ inicio, needle vazia retorna length, nao-achado -1) + handler INSTANCE lastIndexOf no emitCall; fecha N11 (repro regressions/N11 rc=0 no kof-agent)
  - CONC001 fechado — spawn/await no Native via pthread
  - JSN001 fechado — Float/Double no Native (encode, decode, arrays)
  - Fase 7 Router — go/replace com param, unmount de rotas não-registradas
  - kof config gen — template de deploy a partir do código (P3)
  - FFI Vulkan compute (FFM) — JvmVkRuntime com cadeia instance→device→pipeline validada (RADV+lvp rc=0), stage inline no ComputePipelineCreateInfo, structs validados (DeviceCreate 72B, WriteDesc 64B, SubmitInfo 72B, MemoryAlloc 32B); degradação silenciosa p/ CPU (bug RADV/lvp 25.2.8 no dispatch — reproduzido em C puro dlsym)
  - interpolação ${key} no kof.config — P2 nos 3 targets
  - FFI Vulkan compute (FFM) — JvmVkRuntime com cadeia instance→device→pipeline validada (RADV+lvp rc=0), stage inline no ComputePipelineCreateInfo, structs validados (DeviceCreate 72B, WriteDesc 64B, SubmitInfo 72B, MemoryAlloc 32B); degradação silenciosa p/ CPU (bug RADV/lvp 25.2.8 no dispatch — reproduzido em C puro dlsym)
  - add configuration interpolation and HTTP circuit breaker functionality

### Bugfixes

  - 500 desempacota InvocationTargetException do handler lambda
  - envolve TODO o mic record no gap MEDIA003
  - mic captura qualquer exceção de hardware ausente como MEDIA003
  - dedup por arquivo de origem — re-import transitivo não é colisão
  - JvmVkRuntime.java — restaura ';' e remove '}' extra (build quebrado no merge fixes-for-kofagent)
  - PKG005 permite nomes iguais em pacotes diferentes (como em Java)
  - bug 11 native — record ==/equals/!/toString/concat (já testado), concat valueOf fix, digest valueOf Object->toString
  - remove duplicate WS/SSE runtime definitions
  - bug 15 — primitivo → Object (auto-boxing) + default em var sem init
  - bug 9 — captura mutável no Native (prologue de lambda)
  - bug 8 — tipo de função (Int) -> Int parseia como tipo
  - bugs 19/20 (lambda em coleção/retornando lambda) + validação símbolos
  - --enable-preview só no JDK 21 — FFM é final no 22+ (JDK 25 quebrava COMP001)
  - bug 23 — warning quando superclasse externa está fora do classpath
  - bug 11 — == em records por conteúdo (JVM+JS)
  - bug 16 — List.toArray() rejeitado com SEM029
  - bug 12 — assignment como valor rejeitado (SEM027)
  - bug 18 — kof-ui widget id monotônico (sem reuso após remove)
  - bug 17 — array .get()/.set() rejeitados com SEM028
  - bug 13 — cast em aritmética crasha o compilador
  - bug 4 — switch de String no JVM
  - bug 22 — Native: construtor de classe importada (undefined reference)
  - bug 7 — listOf<String?>() agora parseia
  - bug 1 — throw não-String vira SEM026; try/catch agora é analisado
  - bug 14 — Map/Set .size como propriedade
  - bug 6 — sufixos numéricos maiúsculos (42L/1.5F/2.0D)
  - bugs 5, 24, 25 — conversões numéricas + literal fora de faixa
  - bugs 2, 3, 10 — compound assignment + NOT lógico
  - concat 'str' + double/float descartava o operando FP
  - Set<T>/Map<K,V> como campo/retorno de classe (mapper HashSet/HashMap + parse de método c/ retorno genérico)
  - idiomatic-philosophy — kof.Set JVM, Map.get V?, readText String?, size() sem sentinela
  - alinhar stack no call pthread_create (println antes de spawn desalinhava → segfault glibc)
  - surefire include pega NativeDebugTest2-5; docs/status atualiza 01/09 + regressão dc849f6
  - clarify test summary in project status documentation
  - update last updated date in project status
  - prevent stack underflow by avoiding unnecessary KofPop for collection methods
  - 'fn' keyword de declaração — o parser tratava 'fn main()' como retorno 'fn' e o JvmBackend emitia main([String;)Lfn; → JVM rejeita e tenta o launcher JavaFX ('componentes de runtime do JavaFX não encontrados'). kof run --target jvm volta a funcionar (validado: pure.kf + date + llama smoke)
  - remove unsupported JS spawn error handling; add sequential execution test
  - dedupe helloRoute in KofWebE2ETest; docs for app.health + observability (760 tests)
  - N12 — ordem dos stack args >=6 invertida nos call sites (SysV: arg6 deve ficar no topo → 16(%rbp)); INSTANCE/INTERFACE vtable salvavam stack args em r10 único (quebra com >1 stack arg) — agora slots de frame; repro N12 (9 campos, x.i=9) verde, J4/N10 re-validados
  - N23 — constructor com >=6 args: cleanup dos stack args apos call (callee caller-clean); pop do consumidor volta a desempilhar o push duplicado do receptor (rip=0x1 via vtable corrompida); repro R3-R7 + N23 verdes, 16/16 suites
  - COMP002 travava lambda WS com if/String — descritores ws faltavam
  - fechamento da classe KofRuntime no runtime concatenado (COMP001)
  - fechamento da classe KofRuntime após concatenação do JvmVkRuntime (COMP001 compact source file)
  - cache.delete statement sem Pop extra — KofIo.instanceMethod(Unknown,'delete') interceptava hasReturnValue antes do caso cache (frame merge NegativeArraySize) — fecha KofCacheE2ETest (661 testes verdes); trace IR via -Dkof.trace.ir
  - inferência de aritmética promove int→long, corpo vazio em classe concreta emite return (ClassFormatError), tipo de retorno de função top-level registrado (NoSuchMethodError em receiver); fix(native/io): kof_io_dir_delete recursivo no JVM + retorna 0 (não -1) em falha — fecha suítes ws do kof-agent (16/16) e IoE2ETest.directoryDelete
  - fechamento da classe KofRuntime no runtime concatenado (COMP001)
  - fechamento da classe KofRuntime após concatenação do JvmVkRuntime (COMP001 compact source file)
  - update future release codename in documentation; modify test to accept closed URL as argument
  - correct spelling of "Diplomat" to "Diplomata" in release notes; add end-to-end tests for HTTP resilience and circuit breaker functionality
  - restaura descritores JVM + no-ops UI/Store perdidos no rebase
  - cache.delete statement sem Pop extra — KofIo.instanceMethod(Unknown,'delete') interceptava hasReturnValue antes do caso cache (frame merge NegativeArraySize) — fecha KofCacheE2ETest (661 testes verdes); trace IR via -Dkof.trace.ir
  - remove unnecessary stack adjustment in method call
  - inferência de aritmética promove int→long, corpo vazio em classe concreta emite return (ClassFormatError), tipo de retorno de função top-level registrado (NoSuchMethodError em receiver); fix(native/io): kof_io_dir_delete recursivo no JVM + retorna 0 (não -1) em falha — fecha suítes ws do kof-agent (16/16) e IoE2ETest.directoryDelete

### Documentation

  - WEB001/WEB002/HTTP002 atualizados ao estado real (03/09)
  - SYN001 23/23 (enum exaustivo) + suíte 910
  - bronca formal — 3 incidentes de processo (03/09)
  - corpus do switch-expressão + status 906 testes
  - atualiza known-bugs.md e DOING.md com status 03/09
  - NATIVE002 paridade stubs→real FEITO — log config cache mq interval scheduler com cli via kof_time; aquitetura confirmada
  - CONC003 fechado - async real no JS documentado em todo o repo
  - trilha universal — Tier 0 fechado; Tier 1 (SYSTEMS) pendências mapeadas; WEB002 reivindicado por agente-planning
  - MySQL prepared binário FEITO (02b9ddb) — status/parity/DOING atualizados
  - limpa duplicatas; umico Em curso + Abertos
  - WEB002 devolvido a ABERTO (escopo muito grande pra sessao; proximo passo: server bloqueante com accept+request-line+match de rota literal) — NAO pega outro enquanto GC fechado
  - marca GC sweep+flag FEITO (dono agente-planning); reivindico WEB002 (NativeWebRuntime.java novo; handler com trampolim)
  - ajustes de escopo apos HTTP002/GC — WEB002 e maior (closure trampolines); GC sweep fechado, auto-collect pendente safe-points
  - status 854 + CHANGELOG noite 03/09 — 14 bugs corrigidos
  - DOING.md — 13/25 bugs corrigidos no known-bugs (03/09)
  - DOING.md — MySQL prepared FEITO (4ce1f25), NATIVE002 valid/observability FEITO (b20aa49), aberto: query binaria + GC
  - update AGENTS.md and DOING.md with behavior freezing guidelines; revise test counts in status and stdlib-logging documentation
  - DOING.md — coordenacao multi-agente (dono por gap, estado, arquivos)
  - rodada 3 (usuários) — 6 bugs novos (total 23)
  - bateria pós-merge — bugs 16-17 + contagens reais 819 (merge riscv64)
  - update known bugs and status with new test results and bug descriptions
  - resolve conflitos do merge de main (riscv64 13/13, counts reais)
  - rodada agressiva — 6 bugs novos em known-bugs.md (total 15)
  - bug-hunt 02/09 — 9 bugs documentados em known-bugs.md p/ próximo agente
  - esclarece class X(...) = record em TODOS os md; reduce padronizado
  - learn/19 esclarece java.util.* (interop, não idiomático para coleções)
  - auditoria final — switch break opcional, learn/04 arrays, learn/12 notas, contagens 810
  - merge backend-parity deltas (LOG001 JS + Vulkan conditional + main fixes)
  - regra de arquitetura — máximo 500 linhas por classe (refactor futuro)
  - captura mutável, concat FP, riscv64 — registros 02/09
  - riscv64 real (02/09) no corpus — targets.md, overview, roadmap, actual-state
  - learn/21 honesto — interop Java parcial verificada; contagem 810
  - riscv64 runtime em asm puro (sem C) — status/parity alinhados
  - honestidade verificada — exceptions String-only, class X(...) = record
  - gotchas do koflama — ANEWARRAY ref types, Map descriptor jar, unboxing NPE, Int overflow em acumuladores micro, UTF-8 vs latin-1
  - recalibra contagem de testes para 805 (788+8+5+4) pós-merge
  - disclaimer da marca no README + NATIVE002 — toolchain cruzada + runtime C via gcc validado (02/09)
  - consistência geral — versão 0.2.6-beta, contagem de testes 788, datas 02/09
  - SECN002 fechado (AES-256-GCM no KofJS) + contagem 780 (763+8+5+4)
  - deltas 01/09 (spawn captura, short-circuit JS, Channel param, pthread_create alinhamento, KofJS browser) + contagem 778 + MySQL wire protocol
  - contagem 778 + bug #2 spawn→await→spawn resolvido (mesmo fix de alinhamento pthread_create)
  - chained-OR membership caveat — Set<T> declarado quebra no JVM; só setOf local nos 3 targets
  - casts primitivos as Char/as Int, Long[], String.valueOf builtin + fixes 01/09 (frame List.add, I2C, L2I)
  - future/ fica só com planos; risc/arm (em desenvolvimento) -> docs/native-multiarch.md com estado real + como finalizar
  - §4.8.1 Kof Security — evolução estratégica (auditoria + PQC híbrido ML-KEM/ML-DSA + SecureChannel + threat model + roadmap por maturidade)
  - status 769 tests (752 kof-compiler +8 script +5 c-compiler +4 cli); integrate upstream io/fn-parser fixes
  - status 768 tests; LSP references/rename, W3C traceId/spanId, P1-4 LCA moduleRoot, P3-10 ORM003 typed column
  - update project status and test counts; add multimedia handling details for Kof
  - alinha contagem de testes (736 = 723+8+5) e pipeline de release
  - sweep profundo — todos os MDs sincronizados com o estado 0.2.6-beta
  - guias de instalação por SO (sem versão hardcoded) + sweep 0.2.6-beta
  - gap COMP002 do config fechado — causa era descritor ws faltando no JVM
  - CONC001 fechado — spawn/await nos 3 targets (parity de concorrência)
  - sync
  - CONC003 no JS já cobre spawn stmt + spawn-expr — gap restante é async real
  - JSN001/FLT001 fechados — parity JSON Float/Double no Native; CONC003 parcial
  - Fase 7 Router marcada como implementada com detalhes

### Refactoring

  - remove observability metrics implementation
  - runtime em assembly PURO (sem C) — Kof é Kof
  - streamline return value handling and remove debug tracing for 'add' method
  - update variable declaration examples for clarity and consistency
  - simplify argument handling in KofIo method calls to prevent frame bugs

### Tests

  - mic gap aceita as duas formulações do MEDIA003

## [0.2.6-beta] - 2026-09-02

### Feature — switch como expressão (SYN001)

- **`case ... ->` produzindo valor** (`feat`): `var r = switch (x) { case 1 ->
  "um"; default -> "outro" }` — pattern matching via expressão, no espírito do
  switch expression do Java 14. Cada caso é uma única expressão (sem `break`,
  sem escopo de bloco, sem fallthrough); `default` obrigatório ou exaustividade
  de enum (senão `SEM032`). Funciona nos 3 targets + riscv64/aarch64 (JS
  renderiza como ternários aninhados). **Aditivo**: a forma statement
  (`case X:`) está intocada. Prova: `KofSwitchExprE2ETest` 19/19 +
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 14/14. Plano em
  `docs/planning-switch-expr.md`.
- **PKG005: re-import transitivo não é colisão** (`fix`): `compileSources` com
  fonte explícita + `import` da mesma declaração disparava falso-positivo de
  "duplicate type name"; agora só colide quando os **arquivos** diferem
  (`PackagesE2ETest` 7/7).

### Fix — filosofia idiomática (revisão do corpus)

- **`Set<T>` como tipo declarado no JVM** (`feat`): descriptor `kof.Set`
  materializado como `java/util/HashSet` (`JvmTypeMapper`). `Set<T>` em campo,
  retorno e parâmetro agora funciona nos 3 targets — antes `NoClassDefFoundError:
  kof/Set` no JVM (`KofMapSetTest`).
- **Parser: membros de classe com retorno genérico** (`fix`): `Set<Int> foo()`,
  `List<String> bar()` em classe não parseavam (lookahead de 1 token).
  Refatorado para parse-then-decide (`Parser.parseClassMember`).
- **Null-safety narrowing no JVM corrigido** (`fix`): `if (s != null) {
  s.length }` emitia `getfield "?".length` e `s.substring(...)` emitia
  `"".substring` (bytecode inválido → erro de launcher/`ClassFormatError`).
  Agora desempacota `NullableType` no dispatch de field-access e method-call
  (`NullSafetyE2ETest`). `if (x != null)` usa `if_acmp*` (era `if_icmp*`).
- **`mapOf(k1, v1, ...)` infere o tipo do primeiro par** (`fix`): antes
  `Map<Unknown,Unknown>` vazava para `var m = mapOf(...)` e `get()` devolvia
  Unknown (SemanticAnalyzer + CompilerDriver).
- **Parser: forma prefixada nullable** (`feat`): `String? s = null` e retorno
  `String? f()` agora parseiam em statements, funções e classes — simétrica a
  `String s`; a forma anotada `var s: String? = null` também é válida.

### Fix — stdlib exemplifica os idioms que ensina

- **`File.readText()`/`readFile()` → `String?`**: ausência = `null` (JVM e
  Native — o Native antes encerrava o programa).
- **`File.size()` sem sentinela `-1`**: lança exceção recuperável
  (`catch (String e)`) quando o arquivo não existe (JVM + Native asm via
  `kof_throw_string`).
- **`Map.get` devolve `V?`** para valores de referência (ausência = `null`,
  narrowing via `if (x != null)`); primitivos seguem `V` (modelo atual não
  representa ausência).
- **`readLine()` → `String?`**: `null` no EOF em JVM e Native (o Native antes
  devolvia `""`).
- **Captura mutável de lambda — mutação fora da lambda** (`fix`): a detecção
  só marcava mutações DENTRO da lambda (`inLambda`); `var f = (x) -> x +
  offset; offset = 20` capturava por valor (15 em vez de 25). Agora
  `collectMutatedCaptures` computa as capturas REAIS (via `collectCaptures`)
  e boxa qualquer variável capturada + mutada em qualquer lugar (JVM
  verificado; `LambdaE2ETest`). Native: a direção "lambda escreve" funciona;
  "lê boxed após mutação externa" é bug conhecido.
- **Concat `"str" + double/float` descartava o operando FP** (`fix`): o guard
  de concat FP fazia `yield` incondicional (ignorava `fpSupportedOnNative`,
  que é true desde o FLT001) → `"a=" + 1.5` compilava só como `"a="` (saída
  vazia silenciosa). Agora só pula quando o target não suporta FP
  (`BackendParityTest.parityStringDoubleConcat`).
- **riscv64 codegen real (merge da main, 02/09)**: stack machine riscv64 +
  runtime em asm puro (sem C), `NativeRiscv64E2ETest 4/4` via qemu
  (`NATIVE002` parcial); aarch64 segue placeholder.

- **Bug-hunt 02/09 — 9 bugs documentados para o próximo agente** em
  `docs/known-bugs.md` (reprodução + causa provável + arquivos): compound
  assignment `-=`/`/=`/`%=` (resultado errado, JVM+Native), `s += "x"` em loop
  (crash do compilador), `switch` de String (bytecode inválido), cast FP→Int,
  sufixo numérico maiúsculo `42L`/`1.5F`, `listOf<String?>()` (não parseia),
  tipo de função em generic (`listOf<(Int) -> Int>()`), `throw` não-String,
  captura mutável Native.

- **Noite 03/09 — 14 bugs corrigidos** (todos com teste de regressão que
  falhava antes/passa depois; `known-bugs.md` atualizado a cada fix):
  1. `throw <não-String>` → SEM026 (try/catch agora passa por análise
     semântica — antes corpos de try eram ignorados)
  2. compound `-=`/`/=`/`%=` (ordem dos operandos invertida)
  3. `s += "x"` em loop (RHS empurrado duas vezes → crash de frame)
  4. `switch` de String no JVM (usava SUB em vez de igualdade de conteúdo)
  5. cast FP→Int/Long (novos ops D2I/F2I/D2L/F2L nos 3 backends)
  6. sufixos numéricos maiúsculos `42L`/`1.5F` (lexer)
  7. `listOf<String?>()` não parseia (lookahead de call genérico)
  10. `!` NOT como valor (fold usava `~i` bitwise)
  13. `(x as Int) + 1` crashava (flattening de cadeia incluía `as`)
  14. `Map.size`/`Set.size` propriedade (NoSuchFieldError)
  17. array `.get()/.set()` → SEM028 (API é `arr[i]`)
  22. Native: construtor de classe importada (mangle com package)
  24. `Float f = 3.4` (D2F no widening)
  25. literal Long fora do range (PARSE084 em vez de crash)
  Suíte subiu de 840 → **854**; zero regressão (Congelamento de comportamento).

### Corpus / docs

- `training/datasets/kof-idioms.json` atualizado para 0.2.6-beta (17 → 20
  entradas; `;` estilo Java removido; kof-004 separa ausência vs erro).
- `AGENTS.md` corrigido: forma nullable padrão `String? s = null`; `spawn`
  fire-and-forget sozinho é válido.
- `docs/philosophy.md`: propostas futuras (`config {}`, `name: required`)
  marcadas como tal; `route GET` substituído pela API implementada.
- `docs/backend-parity.md`: gap `STR001` (length UTF-8 vs UTF-16) e
  `STR002` (io) documentados; `docs/stdlib/IO.md` e `training/language/io.md`
  refletem o novo contrato.

## [0.1.0] - 2026-08-25

Primeira release estável da plataforma base — P0 (ecossistema) e P1
(linguagem) fechados.

### Features

#### P0 — ecossistema
- **G5 `kof.observability`**: `health/readiness/liveness`,
  `counter/increment/gauge`, `requestId/correlationId` — JVM/Native/JS
  (`KofObservabilityTest` 3/3; asm com contadores em .bss no Native)
- **G9 web security**: `security.rateLimit(key, limit, window)`,
  `sessionCreate/sessionGet/sessionDestroy`, `apiKeyGenerate/apiKeyValid`
  — JVM/Native/JS (`KofSecurityG9Test` 3/3)
- **G12 TLS/HTTPS**: `web.listenSecure(port)` (SSLServerSocket + keytool,
  SAN localhost) + `kof.http` HTTPS (`KofWebTlsTest` 5/5); Native/JS
  reportam WEB002

#### P1 — linguagem
- **Enums**: declaração `enum Color { Red }`; `values()/valueOf()/name()`;
  `==` por conteúdo; constante inválida → SEM030; **switch exaustivo**
  com SEM031 listando casos faltantes; mapeado a String nos descritores
  JVM (`KofEnumTest` + `KofEnumSwitchTest`)
- **Map<K,V> / Set<T>**: `mapOf/setOf` + API completa — JVM (HashMap/
  HashSet), Native (**asm próprio**, keys+vals com crescimento 2x, tag de
  tipo p/ equals) e JS (Map/Set nativos) (`KofMapSetTest` 3/3)
- **spawn/await**: `val r = spawn f()` devolve `Handle<T>` tipado;
  `await r` bloqueia em virtual thread com unboxing de primitivos;
  gaps CONC001 (Native) / CONC003 (JS) / AND001 explícitos
  (`KofAwaitTest` 4/4)
- **kof.validation** (G4): 13 predicados nos 3 targets (`SEM` VAL001)

### Fixes

- decode<List<Int>> no Native caía no ramo JSN002 → link quebrado
  (`List_vtable`) — List/Map excluídos do ramo de objeto composto
- spawn statement no JS falhava em runtime silenciosamente → CONC003
- lambda não-void de expressão única emitia POP antes do areturn
  (VerifyError em todo spawn/await com retorno)
- unbox pós-await restrito ao await (descritor default Object
  englobava kof_ui_* → VerifyError mascarado de "JavaFX" pelo launcher)
- `kof test` volta a ser per-file (PKG002 com 2 main() no mesmo diretório)
- boxing de Map.put/get/remove/contains e Set.* via parameterTypes do
  call-site (mapOf nasce Unknown; pinning no primeiro put)

### Docs
- docs/observability.md novo; ecosystem-coverage G5/G9/G12 DONE;
  security.md atualizada; learn/12-collections reescrito (Map/Set);
  learn/18-concurrency reescrito (spawn/await); enum em learn/04 e
  training/language/{types,syntax}; overview do corpus para 0.1.0

## [0.1.0-beta] - 2026-08-25

### Features

- kof.security no Native (asm x86-64, sem libc): PBKDF2-HMAC-SHA256 600k
  (hash/verify/needsRehash), SHA-512 (FIPS 180-4), JWT HS256
  (create/verify + iat/exp/iss/aud + exceções via try/catch) — fecham
  SECN001/SECN003/SECN004 do G10
- lambdas com captura mutável (box sintético) — kof.time.interval real
- kof.http client + kof.mq + kof.time (scheduler) + kof.config nativo
- ORM completo (where com operadores, saveAll, page, count, deleteAll,
  MariaDB/PostgreSQL reais, MongoDB)
- auditoria + matriz de cobertura + plano kof.security (docs)
- split do JvmRuntime em runtimes separados (fix constant pool 65535)

### Fixes

- success=false do compile (gaps de target falhavam o build)
- kof_json_find_value reescrito (ponteiro/offset + limite do scan)
- hmac_internal com data >64 (opad sobreposto)
- .Ljf_mkstr (kof_alloc clobbered len)
- JDT autobuild do VS Code desativado (corrompia o target/ com ECJ)

## [0.0.5-alpha] - 2026-08-22

### Features

  - KofJS backend (alpha) — same Kof IR lowered to ECMAScript 2022+ ESM modules
  - embedded JS engine (GraalJS) — `kof run --target=js` executes without Node.js
  - KofJS runtime layers — kof-runtime.mjs (core) + kof-runtime-io.mjs (platform via kof_platform)
  - KofJS classes, records, inheritance, interfaces (type-level), generics erasure
  - KofJS List, String API, arrays, JSON (encode/decode with class binding)
  - KofJS exceptions (try/catch/finally), lambdas, if-expressions, source maps
  - record-style class syntax — `class User(String name)` same semantics as record
  - generic return types in function declarations (e.g. `List<Int> ints()`)
  - KofJsE2ETest suite — .kf → .mjs → embedded engine → stdout/exit code
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.6-alpha] - 2026-08-22

### Features

  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.7-alpha] - 2026-08-23

### Features

  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - update local build instructions with lib/kof.jar workaround
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.8-alpha] - 2026-08-23

### Features

  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.9-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.10-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.11-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.12-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.13-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - extração do zip do JDK (Windows) — mover o subdiretório jdk-* com verificação, sem engolir falha
  - verificação explícita do JDK embarcado após a extração (Windows)
  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.12-alpha [skip ci]
  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.14-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - Windows — converter paths MSYS para Windows antes do extractall do Python
  - extração do zip do JDK (Windows) — mover o subdiretório jdk-* com verificação, sem engolir falha
  - verificação explícita do JDK embarcado após a extração (Windows)
  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.13-alpha [skip ci]
  - bump version to 0.0.12-alpha [skip ci]
  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.1.1-alpha] - 2026-08-26

### Features

  - add fake SDK jar for AndroidInterop testing
  - release version 0.1.0
  - switch exaustivo sobre enum — SEM031 + comparação por conteúdo
  - Map/Set nativo em asm — fecha COL001
  - add support for spawn and await expressions with error handling
  - enum P1 — declaração, values/valueOf/name, == por conteúdo (3 targets)
  - implement TLS/HTTPS G12 — web.listenSecure + kof.http HTTPS
  - complete G9 Native + docs/test — rate limiting/sessions/API keys
  - implement rate limiting, session management, and API key handling
  - implement kof.observability G5 — health/metrics/request IDs on JVM/Native/JS
  - implement kof.validation functions and integrate with compiler
  - AES-GCM nativo em asm — fecha SECN002 (G10 completo)
  - JWT HS256 nativo em asm — fecha SECN004 (G10)
  - PBKDF2 + SHA-512 nativos em asm — fecha SECN001/SECN003 (G10 parcial)
  - add in-memory messaging system with publish/subscribe and queues
  - extend KofUnaryOp with D2F and update backends
  - add support for Link, Image, Icon, and Font UI components
  - add CI workflow for Android target with APK assembly
  - decode de arrays no Native — fecha o gap JSN003
  - Implement constructor overloading and add JvmConfigRuntime and JvmStringRuntime
  - kof.config no target Native — fecha o gap CONF001
  - kof.http client + kof.mq (messageria em memória) — G2/G3 fechados
  - enhance Android target support with embedded host Activity and external classpath resolution
  - add Android target support with project generation and configuration
  - add KofAndroid target with initial design and objectives
  - implement qualified type resolution and enhance inheritance support
  - kof.log no target Native — fecha o gap LOG001
  - kof.orm validado em bancos reais — MariaDB 11 e PostgreSQL 16 + fixes do WIP das annotations
  - add support for native target execution in Main class
  - update CLI documentation and add structured test example
  - kof.orm — count com filtro e deleteAll completam o CRUD
  - kof.orm completo — operadores no where, saveAll (batch) e page (paginação)
  - add test declaration support and compile-time test harness
  - implement string to numeric conversions and enhance MongoDB method handling
  - add MongoDB and SQLite support to kof.orm with new runtime methods
  - orm.where (query por campo) + orm.migrate (migrations versionadas)
  - kof.orm — o ORM da própria linguagem (entity + orm.*)

### Bugfixes

  - unbox pós-kof_await restrito ao await — UI voltou a verificar
  - enhance spawn expression handling for primitive return types
  - List/Map fora do ramo JSN002 (ld List_vtable) + spawn stmt JS CONC003
  - lambda não-void single-expr vira return + gaps CONC003 p/ spawn-expr/await no JS
  - add debug logging for MemoryLayer entries field type resolution
  - Map/Set boxing e construção — corrige VerifyError JVM e stack underflow JS
  - fwd-ref multi-file, Int[] negativos nativo, Frame.merge Map
  - SEM025 não reportar Object methods (hashCode/equals/toString) — corrige JvmE2ETest.execRecordValueMethods
  - N3 args vazio + N9 box String += com concat
  - exclude String/Int/Long/Bool from SEM025 — avoid false-positive for JDK methods (contains/split)
  - dedupe kof.validation block — single copy, fix Native ld duplicate symbols
  - enhance JSON value retrieval in emitJsonFindValue function
  - add string conversion functions and update NativeRuntime with new assembly generation
  - alinha serve/check/test com o modelo de módulo multi-arquivo
  - simplify JSON string handling in NativeRuntime and CompilerDriver
  - kof_sec_secret_get nativo reescrito — bug #13 resolvido
  - db.close quebrado pelo WIP do isLocalVarName + surefire -Xshare:off
  - update AndroidInteropE2ETest to use a temporary SDK JAR for external classpath
  - update Android project instructions to reflect Maven usage
  - update comments for clarity in NativeRuntime and modify AndroidProjectWriter to use Maven
  - FLT001 — operações de ponto flutuante viram diagnóstico em compile-time
  - feedback do kof-calculator-lab — calculator interativo destravado + bugs reais
  - remove debug logging for MongoDB method accessibility
  - enhance integer arithmetic checks and improve MongoDB query handling
  - feedback real do kof-calculator-lab (OBS-004 a OBS-010)

### Documentation

  - stdlib — await/join de spawn (P1), CONC003 no JS
  - stdlib P1 — Map/Set (JVM/JS, COL001 Native) e enum (3 targets)
  - sync 0.1.0-beta 25/08 — generics Box<T> + SEM025 Object fix + test counts
  - bugs #13/#14 resolvidos, plano P0 atualizado
  - JSN003 encerrado na documentacao
  - bug #13 (secret_get nativo) encerrado
  - CONFIG001 nativo concluido (8/8 testes E2E)
  - estado do CONFIG001 nativo (WIP ~90%) e contagem de testes
  - bugs 13-14 na lista (secret_get nativo segfault; FP sem SSE no Native) + progresso do plano
  - kof.log nativo na documentação (LOG001 só no JS)
  - kof.orm completo na documentação (saveAll, page, operadores no where, deleteAll, count filtrado, MariaDB/PostgreSQL)
  - package.sh no Windows — Git Bash + descoberta do Python (OBS-005/006)
  - kof.orm na tabela de features (status.md + README)

### Tests

  - update passwordsNative test to validate successful hash on Native target
  - prova de Turing-completude — Ackermann + loop de 1M nos 3 targets

## [0.1.2-beta] - 2026-08-26

### Features

  - LSP hover/completion · kof init/fmt/script · collect não-recursivo
  - spawn/await no JS (fecha CONC003) + kof script; versão 0.1.1-beta

## [0.1.3-beta] - 2026-08-26

### Features

  - poll/done + exceção limpa no await — itens 'alta' da fila

## [0.1.4-beta] - 2026-08-27

### Features

  - enhance native target support for RISC-V and ARM architectures
  - add native C subset compiler
  - JIT in-memory + top-level let + kof test isolado + LSP .ks (3 gaps restantes)
  - CLI kof script --target + repl + classpath jar + diagnostics
  - MVP KofScript direct execution (Fase 6)
  - switch case String s + instanceof + checkcast em JVM/Native/JS
  - cache in-process, WebSocket/SSE, scheduler every/at + pattern matching record destructuring + nullability String? + kof.time/config/mq para Native/JS
  - kof.time now/sleep, kof.config/mq for JS, fix native rbx clobber
  - implement higher-order functions for List — map, filter, reduce
  - cancel cooperativo + selectAny — itens 'média' da fila

### Bugfixes

  - imports file-specific + native free-list GC + docs 27/08

### Documentation

  - move DATABASE_VISION e KOF_VS_SPRING de future para docs; future fica só com planejados + kof-native risc/arm

## [0.1.5-beta] - 2026-08-27

### Features

  - automatic GC on alloc + kof_gc_collect coalesce

### Bugfixes

  - lib/kof.jar inside tar.gz + always upload artifacts

## [0.1.6-beta] - 2026-08-27

### Features

  - automatic GC on alloc + kof_gc_collect coalesce

### Bugfixes

  - windows SIGPIPE 141 head pipefail
  - lib/kof.jar inside tar.gz + always upload artifacts

### Build

  - bump version to 0.1.5-beta [skip ci]

## [0.1.7-beta] - 2026-08-27

### Bugfixes

  - duplicate if-no-files-found + pipefail head
  - windows pipefail + lib/kof.jar check + jar upload

## [0.1.8-beta] - 2026-08-27

### Bugfixes

  - remove duplicate if-no-files-found
  - ensure kof-cli jars + lib/kof.jar in dist for 0.1.7

## [0.1.9-beta] - 2026-08-27

### Bugfixes

  - build kof-cli+dist in same job as release (no artifact loss)
  - ensure kof-cli jar in same-step as release (artifact fallback)
  - remove duplicate if-no-files-found again
  - re-add kof-cli jars to upload for 0.1.8

## [0.1.10-beta] - 2026-08-27

### Bugfixes

  - package+release uma coisa só + JDK 21 no release job
  - build kof-cli+dist in same job as release (no artifact loss)
  - ensure kof-cli jar in same-step as release (artifact fallback)
  - remove duplicate if-no-files-found again
  - re-add kof-cli jars to upload for 0.1.8

### Build

  - bump version to 0.1.9-beta [skip ci]

## [0.1.11-beta] - 2026-08-27

### Bugfixes

  - package+release uma coisa só (single job, no artifact loss)

## [0.2.1-beta] - 2026-08-28

### Features

  - GC mark-sweep + MySQL handshake + RISC-V placeholder (code it all)

### Documentation

  - update all md to 0.2.0-beta 27 Aug 2026 (658 tests, KofC, KofScript, kof.http JS, imports fix)
  - update all md to 0.2.0-beta 27 Aug 2026 (658 tests, KofC, KofScript, kof.http JS, imports fix)

### Build

  - bump version to 0.2.0-beta [skip ci]

## [0.2.2-beta] - 2026-08-28

### Bugfixes

  - shell bash for changelog + remove duplicate release-artifacts step (windows pwsh fix)

## [0.2.3-beta] - 2026-08-29

### Features

  - resposta rica status/header + scheduler every/at (JVM+JS)

### Bugfixes

  - string concat null -> anull, if null, List and subclass parity (4 bugs)

## [0.2.4-beta] - 2026-08-30

### Features

  - F10 spawn com stdin vivo + fix(native): forward reference

### Bugfixes

  - PROC001 explícito para process.spawn no Native
  - marcação transitiva + sweep no-op conservador + cdq em idivl Int — fecha N22 e SIGFPE do scheduler
  - String API no SemanticAnalyzer/inferExprType + lastIndexOf + null-compare no KofBinary
  - aritmetica Int trunca 32 bits — fecha N21/N10-family
  - comparisonOperandType refinado — null-literal decide ref; Unknown volta a ser int
  - R2 mapOf(k,v) pares + R3 null-safety narrowing + R4 Box<T> genérico na JVM

## [0.2.5-beta] - 2026-08-30

### Features

  - kof fmt via parser real (KofFormatter) + idempotente

### Bugfixes

  - kof.cache ttl/get clobber de registradores + println(null) segfault; feat: KofCacheE2ETest (5 casos x3 targets)
  - String.length via toString type inference + remove auto-GC hang (alloc path)

### Documentation

  - status 0.2.3-beta + kof.cache fechado (fix nativo + E2E)

## [0.2.6-beta] - 2026-08-30

### Features

  - RFC 6455 WebSocket frame codec
  - RFC 6455 WebSocket handshake
  - JVM native SSE + trailing-block sugar
  - persistent connection + route kinds + diagnostics (WEB003/WEB004)

## Versionamento

O Kof usa `MAJOR.MINOR.PATCH` (ver [docs/distribution/VERSIONING.md](docs/distribution/VERSIONING.md)).

- `0.0.x-alpha` — estágio inicial (Alpha), cada commit na `main` gera a próxima versão.
- O `PATCH` é o *pontinho da vergonha*: bugfixes, correções, regressões e pequenos ajustes.
- Nada é chamado de stable enquanto estiver em Alpha.

## [0.0.4-alpha] - 2026-08-22

### Infraestrutura de distribuição

- Versionamento centralizado: `VERSION` como fonte única, `<revision>` no Maven,
  `kof/version.properties` empacotado, `scripts/bump-version.sh`.
- `kof info` — relatório do ambiente (versão, Tooling API, target, JVM, install).
- `kof check` — type-check sem emissão de código.
- `kof lsp` — Language Server sobre stdio consumindo o frontend real do compilador.
- Launcher `bin/kof` (Unix) e `bin/kof.bat` (Windows) com suporte a JDK embutido.
- `scripts/package.sh` — pacote oficial (`kof-<versão>-<os>-<arch>` + SHA256SUMS),
  com JDK embutido opcional (`--jdk`, Temurin 21).
- GitHub Actions: `ci.yml` (PR) e `release.yml` (push na `main` → teste, bump,
  empacotamento multiplataforma, changelog e GitHub Release).
- Suporte a editores: grammar TextMate oficial em `editor/kof.tmLanguage.json`
  e documentação de consumo em `docs/tooling/`.

### Features

- JSON parity JVM + Native — encode/decode de objetos e records (JVM),
  `long`, arrays e inferência de campos.
- List rich API — `contains`, `isEmpty`, `remove`, `clear`, `listOf` (JVM + Native parity).
- Native string API parity — `indexOf`, `trim`, `toUpperCase`/`toLowerCase`,
  `replace`, `equalsIgnoreCase`, `split`.
- Backend JVM: generics com erasure, boxing, records, interfaces, bitwise,
  aritmética de `long`.

### Tooling

- Build Maven estável sob JDK 25 (reuso de compilador desabilitado no reactor).

## [0.0.3] - 2026-08-21

Estado anterior do projeto — veja `git log` e `docs/status.md` para o histórico completo.

## Formato da convenção de commits

```text
feat:      nova capacidade
fix:       correção de bug
docs:      documentação
refactor:  mudança interna sem mudança de comportamento
test:      testes
build:     build/CI/empacotamento
tooling:   ferramentas e editor support
```

A pipeline gera a seção do changelog a partir desses prefixos
(`scripts/changelog.sh`).
