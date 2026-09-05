# Changelog

Todas as mudanças relevantes do Kof são registradas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/) com a convenção
de commits do projeto (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`,
`build:`, `tooling:`). A seção de cada release é gerada por
`scripts/changelog.sh` e inserida pela pipeline neste marcador:

## [0.3.0-beta] - unreleased (branch `beta-0.3.0`)

Linha de desenvolvimento 0.3.0 aberta em 04/09/2026. Semântica congelada
(0.2.6) preservada — mudanças aqui são aditivas ou com bump deliberado.

### Em desenvolvimento

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
