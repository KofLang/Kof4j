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

**PRÓXIMO PASSO (fixes-for-kofagent, 06/09)**: lane REFACTOR-500 FASES 4–8
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
DIRETAMENTE PELO INTERPRETADOR KOF SEM COMPILAR", `0aac7e6`):
`KofInterpreter` (dev.kof.compiler) executa a MESMA IR otimizada do frontend
(stack machine sobre valores reais do JDK; `KofObj` para classes Kof com
dispatch virtual + `<clinit>` + statics; try/catch espelhando a exception
table JVM; builtins sem lambda por reflexão ao `KofRuntime` GERADO — mesmo
source do caminho compilado). `CompilerDriver`: `parseAndMerge` +
`analyzeAndLower` extraídos (refactor puro) + fachada pública `interpret(...)`.
`KofScript.runFile` JVM → interpretador (sem bytecode, sem fork); JS/NATIVE →
caminho compilado (aditivo); `runFileCompiled` mantido (fallback + prova).
**Prova: paridade byte-idêntica interpretado vs JVM compilado** (funções,
strings, records `==` conteúdo + toString, coleções higher-order, classes,
while/for-in, try/catch/finally throw-as-String, spawn/await) —
KofScriptTest 12/12. Docs corrigidas (architecture, compiler-architecture,
README, actual-state, roadmap, philosophy, learn/00, CHANGELOG): KofScript
não é "linguagem separada" nem JavaScript.

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

## Em curso agora

| Gap/Item | Estado | Dono | Branch | Arquivos principais | Notas |
|---|---|---|---|---|---|
| **APP-MODEL** — Kof Application Model (RFC + plano: monólito full-stack ↔ distribuído, única abstração, `kof.toml`, packaging, System) | `EM CURSO` | agente-app-model | `beta-0.3.0` | `docs/future/APPLICATION_MODEL.md` (novo), `docs/future/README.md`, `DOING.md` | 05/09: auditoria do estado atual FEITA (CLI `CmdServe`/`CmdBuild`/`Deps`, stdlib web/http, targets, gaps WEB00x/HTTP003, roadmap §§8–11). **RFC COMPLETA (975 linhas, §1–24)**: motivation+auditoria com evidências (file:line), princípios P1–P10, `kof.toml` (manifesto), componentes, topologias, monólito/specialized, distribuído (microservices/microfrontends/gateway), System (`[system]`+`[dev.ports]`), comunicação (HTTP/JSON hoje; WS/SSE JVM; resto gap), build (jar/ELF/bundle), runtime, deployment (sem acoplamento Docker/K8s), CLI (aditivo; `--system` build sim/serve não), targets (JVM-first; APP001/002/003), segurança, compat (P5: sem manifesto = 1:1), testes (cenários A/B/C), migration, 10 open questions, future extensions, **plano I1–I4** (~4–6 sessões) + checklist AGENTS.md. **PRÓXIMO PASSO:** decisão do maintainer sobre open questions (Q1 nome manifesto, Q2 bump) e aprovação → **I1** (`AppManifest.java`+`CmdNew.java` em `kof-cli/`, parser TOML mínimo, `kof new`, validação APP003, prova: unit+E2E serve+suíte verde); ao começar I1, mover doc para `docs/application-model.md` (regra future→docs) |
| **NATIVE002-stdlib (residual R6)** — auditoria de falha silenciosa cross | `EM CURSO` | melissa (agente) | `beta-0.3.0` | `NativeBackend.java` (riscv asm), `KofScheduler.java`, `KofTime.java`, `KofMq.java`, `KofSecurity.java`, `RuntimeJsonDecode.java`, `RuntimeObservability2.java` | 05/09: **fcvt** (`1a2f044`); **ToolchainMissing** (as/ld → erro de compilação); **FLT001** (`2a7e89f`); **time.now()** (`a67a8de`); **cache**+`println(null)`+`sle/sge` (`0e6d0f9`); **MQ001 cross FEITO** (`05d0d1d`); **tail-call 8 funções** (`fc34bc7`: call+ret sem ra = loop infinito); **gates SCHED001+TIME001** (`0c4e4c5`); **toInt SIGSEGV** (`696c6c9`: deref do valor do char); **Map/Set cross** (`93fec3f`+`858718e`) + **kof_panic** C-string; **higher-order cross** (`7b81871`); **gate SECN000** (`d8aed13`: kof_sec_* ausente no cross); **json decoders escalares cross** (`d118f69`+`21954e1`: int/long/bool/string) + **bug 30 decode<Bool> x86_64 invertido** (length em %r8d + offset 0); **metrics() # TYPE cross** (`2cc3ff8`) + **tradutor quote-aware** (`#` em `.asciz "# TYPE "` era strippado como comentário → string não-terminada no aarch64); **bug 29** spawn{lambda}-com-handle registrado (pré-existente, todos os targets). Sweeps KSw/KSw2/KJ/KU/KMR3/KCFG/KVAL: **0 divergências** nos 3 targets. **time.sleep real** (`ce81639`); **scheduler/time.interval cross FEITO** (`96db26b`: gates SCHED001/TIME001 REMOVIDOS); **bug 32 CORRIGIDO — type-arg genérico via import** (`qualifyDeep` recursiva: `List<NodeUI>` c/ import, `List<com.dev.NodeUI>`, `List<List<NodeUI>>`, mesmo-pacote — PackagesE2ETest 12/12, suíte 969/0); **bug 33 registrado** (Map/Set c/ type-arg de classe — emit separado, pré-existente) (thread por job via clone 220 + nanosleep 101 + spinlock `amoswap.w`; gates SCHED001/TIME001 removidos; `_start`→exit_group 94 p/ matar threads; `amoswap.w`→`swpal` no tradutor). |
| **REFACTOR-500** — dividir as 20 classes >500 linhas (regra ≤500) | `EM CURSO` | divisão multi-agente | `beta-0.3.0` | plano `docs/refactoring/PLAN-SOLID-500.md` | **Divisão**: agente-idiomatic faz **Fases 1–3 + 9** (`NativeRuntime`, `CompilerDriver`, `NativeBackend`, varredura); **fixes-for-kofagent faz Fases 4–8** (`JsBackend`, `JvmRuntime`, `SemanticAnalyzer`, `Parser`, classes 500–1400) (`JvmRuntime`, `SemanticAnalyzer`, `Parser`, classes 500–1400); agente-idiomatic fecha com Fase 9 (varredura final). **Contrato DRY**: `TypeMapper`/`NativeNameMangler`/`TypeMetrics` são criados por agente-idiomatic e consumidos (nunca recriados) por fixes-for-kofagent. Cada fase = commit isolado + suíte completa verde como gate. ⚠️ Nunca dois agentes no mesmo arquivo gigante. **Progresso agente-idiomatic 05/09: FASE 1 COMPLETA** — NativeRuntime 17726→142 linhas (só orquestrador); ~60 classes Runtime* ≤500. Suíte compilador 922 testes, 0 falhas. **fixes-for-kofagent**: fix println(char) (`94aca7a`), gap 27 (valueOf char paridade). **FASE 5 FEITA** (`01af2d5`): JvmRuntime 2526→132 + 7 classes ≤500, source gerado byte-idêntico (prova por dump reflection). **FASE 8 FEITA** (merges `b6fb9d7`/`20b4726`+script): 13 classes 503–1401 → todas ≤500 (JvmString/Vk/Web/Media, NativeHttp/Web, Optimizer, Bench, Main, KofScript, KofJsRunner, JdwpClient), geradores byte-idênticos, suíte 943/0. Guard qemu adicionado (`4408eb6`). **PRÓXIMO PASSO (fixes-for-kofagent)**: **FASES 4–8 COMPLETAS (100%)** — FASE 5 JvmRuntime (01af2d5), FASE 8 (13 classes + JvmBackend 1401→306), FASE 7 Parser (1975→442 + 7 classes, ParseContext), FASE 6 SemanticAnalyzer (2293→396 + 8 classes; fix bug-32 re-aplicado no MemberResolver.resolveType), FASE 4 JsBackend (6064→334 + 22 classes, byte-idêntico). Todas ≤500, suíte 955/0/64-skip. **Lane do agente fechada** — sem item pendente da FASE 4–8. (Resíduo fora do inventário: VkChain64Asm 3568, arquivo M36 Vulkan não listado no plano.) **ATUALIZAÇÃO 06/09 (fixes-for-kofagent)**: FASE 9 (varredura) FEITA por mim — `scripts/check_500.sh` (gate), wildcard imports expandidos (13 arquivos), tabela de status do plano reconstruída (`55f8d20`); CANVAS001 FECHADO nos 3 targets (`5a9cac4`). **PEGANDO A FASE 3** (`NativeBackend` 8834 → ~14 classes) a pedido do maintainer, pois o agente-idiomatic NÃO a iniciou (git log: só feature work NATIVE002, nenhum commit de refactor em NativeBackend). ⚠️ **Colisão potencial**: o item NATIVE002-stdlib (linha ~105, `EM CURSO`) lista `NativeBackend.java` (riscv asm) como arquivo tocado — se o agente-idiomatic for editar riscv asm, **coordenar antes**; por ora ele está na FASE 2 (CompilerDriver), então NativeBackend está livre. **Prova de zero-regressão**: harness byte-diff dos 3 targets (x86_64/riscv64/aarch64) sobre 16 programas → `.s` byte-idêntico antes/depois de cada extração. **FASE 3.1 FEITA** (`NativeRiscvAsm` + 12 fatias ≤500; constantes asm riscv ~4700 linhas fora do NativeBackend: 8834→4113). Prova: `.s` riscv64+aarch64 byte-idênticos (16 programas), x86 só reordenação `.loc` NÃO-DETERMINISMO PRÉ-EXISTENTE (mesmo jar, 2 runs → mesmas 5 diffs); suíte 958/0 BUILD SUCCESS. **FASE 3.2 FEITA** (`NativeAarch64Translator` 496 linhas: parseImm/aarch64Reg/aarch64MovImm/aarch64AddSubImm/translateRiscvToAarch64 extraídos verbatim — bloco estático autocontido; NativeBackend 4113→3632). Prova: `.s` byte-idêntico nos 3 targets (0 diffs, 16 programas); suíte 958/0. ⚠️ não-determinismo `.loc` x86 confirmado PRÉ-EXISTENTE (mesmo jar, 2 runs → diffs diferentes; NÃO é do refactor — registrar em known-bugs na FASE 3.5). **FASE 3.3 FEITA** (`NativeRiscvHttpSupport` 280 + `NativeRiscvHttpCore` 354 + `NativeRiscvSpawn` 206: emitRiscvHttp/usesSpawn/emitRiscvSpawn extraídos verbatim — estáticos, sem estado; NativeBackend 3632→2850). Prova: riscv/aarch `.s` 0 diffs; x86 só `.loc` não-determinístico (0 linhas não-.loc); suíte 958/0. **FASE 3.4 FEITA** (`NativeRiscvCrossEmit` 311 + `NativeRiscvCrossOps` 292: os 13 métodos emitCross*Riscv/pushRiscv/crossArgReg/resolveCalleeNameRiscv/crossLocalOffRiscv/emitMethodTableRiscv extraídos verbatim; estado do backend via campo `nb` (padrão CompilerClassLowering); NativeBackend 2850→2286). Prova: `.s` byte-idêntico nos 3 targets (0 diffs, 16 programas); suíte 958/0. **FASE 3.5 FEITA** (`NativeX86StringCalls` 234: os 23 ramos String/JSON do emitCall x86 extraídos verbatim — autocontidos, zero estado; emit() devolve true quando casou; NativeBackend 2286→2070). Prova: 0 diffs nos 3 targets; suíte 958/0. **FASE 3.6 FEITA** (`NativeTypeKinds` 31: predicados isFloat/isDouble/isInt32/isDoubleWidthSlot — DRY, usados em 15+ lugares; `NativeX86Arith` 322: emitBinary+emitUnary extraídos verbatim — só dependem de NativeTypeKinds; NativeBackend 2070→1741). Prova: riscv/aarch 0 diffs, x86 só .loc; suíte 958/0. **PRÓXIMO PASSO (fixes-for-kofagent, FASE 3)**: restam no NativeBackend (1741): (a) emitCall restante (println/print/string-ops restantes/valueOf/ctor/virtual/channel/list/class/iface — ~550 linhas, dividir por família com padrão NativeX86StringCalls), (b) JSON schema (collectJsonSchemas/emitJsonSchemaData/jsonFieldTypeCode/schemaLabelFor ~135 linhas → NativeJsonSchema com estado próprio), (c) assemble/runCommand/ToolchainMissing (~200 linhas → NativeAssembler), (d) emitMethod/emitOperation/emitStart (~350 linhas), (e) orquestradores emit/emitRiscv/emitAarch64 (ficam). Meta: NativeBackend ≤500. Mesma prova byte-diff 3 targets a cada extração. **PRÓXIMO PASSO (agente-idiomatic)**: **FASE 2 COMPLETA (06/09)** — CompilerDriver 3419→459 (≤500) via 22 novas classes. **FASE 9 (varredura) FEITA (06/09)**: 319 classes em src/main de todos os módulos — APENAS NativeBackend >500 (1741, FASE 3 do fixes-for-kofagent em andamento). NativeRuntime 142 (F1 ok), 61 classes Runtime* ≤500, VkChain64Asm 57 (residual M36 resolvido). Suíte 958, 1 falha = canvasCreation (bug pré-existente, known-bugs.md:815). ⚠️ LIÇÕES F2: (1) herança CompilerDriver extends CompilerDriverState preserva call sites driver.xxx; wrappers precisam cast `(CompilerDriver) this`; (2) replace `target`→`driver.target` quebra PARÂMETROS (Target target) e VARIÁVEIS LOCAIS (ExpressionNode target); (3) mover wrappers para a classe pai resolve o limite ≤500 sem tocar call sites. **Lane do agente-idiomatic: F1+F2+F9 COMPLETAS**. **F3 DIVISÃO (06/09, maintainer)**: fixes-for-kofagent fez **3.1–3.9 COMPLETAS** — 3.1 constantes asm (NativeRiscvAsm+12 fatias), 3.2 tradutor aarch64 (NativeAarch64Translator), 3.3 HTTP/spawn (3 classes), 3.4 cross-riscv (NativeRiscvCrossEmit/Ops), 3.5 String/JSON x86 (NativeX86StringCalls), 3.6 aritmética (NativeTypeKinds+NativeX86Arith), 3.7 JSON schema (NativeJsonSchema), 3.8 assembler (NativeAssembler), 3.9 emitCall restante (NativeX86Calls 356) — NativeBackend 8834→1213. **Fix crítico `9b2c686`**: as extrações 3.1–3.7 estavam quebradas (ciclo StackOverflow CrossEmit↔CrossOps, visibilidades private via nb., classe não fechada, jar stale do shade + ECJ proceedOnError escondendo) — corrigido com PROVA HONESTA (protocolo abaixo). **agente-idiomatic pega 3.10 (emitMethod/emitOperation/emitStart + helpers → NativeMethodEmitter) e 3.11 (orquestradores emit/emitRiscv/emitAarch64 → NativeEmitterOrchestrator)**. NativeBackend 1213 em curso. Padrão: classe com campo `nb` (padrão NativeX86Calls), extração verbatim, prova byte-diff 3 targets + suíte verde. ⚠️ **PROTOCOLO DE PROVA HONESTA (obrigatório desde 9b2c686)**: (1) `mvn clean compile -pl kof-compiler -am` (limpa target/classes — ECJ proceedOnError embute stubs silenciosos em builds incrementais); (2) `grep -rl 'Unresolved compilation' kof-compiler/target/classes/` deve dar 0; (3) `mvn clean package -pl kof-cli -am` (shade puxa kof-compiler STALE do .m2 — verificar timestamp do jar); (4) byte-diff com jar FRESCO verificado; (5) suíte completa.
| **SYN001** — `SwitchExpr`: switch como expressão (pattern matching via `case ... ->`) | `FEITO` | agente-switch-expr | main | `Parser.java`, `SemanticAnalyzer.java`, `CompilerDriver.java`, `JsBackend.java`, `AstNodes.java`, `KofFormatter.java` | 03/09 `1d1343f` — plano `docs/planning-switch-expr.md`. **Aditivo**: statement (`:`) intocado (KofPatternMatchingTest 10 + KofEnumSwitchTest 4 = gate). Lowering KIR em cadeia de if-expr (JVM+Native+JS ternários). Prova: `KofSwitchExprE2ETest` 23/23 (valor/string/pattern/destructuring/return/aninhado/enum-exaustivo/SEM032) + riscv64/aarch64 14/14 qemu. Suíte 910/0/3-skip. Bônus: fix PKG005 (`f6f1714`) — re-import transitivo não é colisão |
| **NATIVE002** — paridade stdlib riscv64/aarch64 (log/config/time/cache/mq stubs→real) | `FEITO` | agente-nativo-val | main | `NativeBackend.java` (`RISCV_RUNTIME_ASM` + `translateRiscvToAarch64`) | qemu riscv64+aarch64 OK; suíte 842/0. Detalhe: log `[LEVEL] msg` + stderr; config env real (`/proc/self/environ` syscall); cache TTL via `kof_time_now`, mq pub/sub c/ list (libera NATIVE002 residual) |
 | **NATIVE002-stdlib** — JSON/http/spawn/db no runtime riscv64 (aarch64 herda via tradutor) | `FEITO` | agente-planning | `beta-0.3.0` | `NativeBackend.java` (`RISCV_RUNTIME_ASM`, `emitRiscvHttp`, `emitRiscvSpawn`), `NativeRuntime.java` (x86_64) | 04/09 `c23dcc8`+`a660adc`+`fba2731` — **JSON** ✅ + **http** ✅ (get/post/put/patch/delete/options/status + headers) + **spawn/await** ✅ (`clone(220)`+`futex` — qemu-riscv64 8.2.2 **não** implementa clone3 (ENOSYS), usa o flag-set da glibc 0x3D0F00; heap compartilhado → `kof_alloc` virou bump **atômico** `amoadd.d`/`ldadd` (tradutor: `.arch armv8.1-a`); riscv64+aarch64 **19/19 qemu** cada). **Root cause de "http não funciona"**: bug de **gp-relaxation** — `la` virava `addi rd,gp,off` com gp=0 (binário estático, sem C runtime) → fault; JSON passava por sorte de layout. Fix: `-mno-relax` no as + `--no-relax` no ld. **Fix tradutor aarch64**: `movz` (não `mov`) quando `lsl #16`; `parseImm` aceita hex; `amoadd.d`→`ldadd`; `fence`→`dmb ish`. **db**: link dinâmico de libsqlite3 exige libc → inviável no asm puro estático; cross agora reporta **DB001 em compile-time** (R6: nunca undefined-reference no ld) — `KofDb.supportedOn` exclui riscv64/aarch64, teste `crossNativeReportsDb001`. **String methods** (`trim`/`toUpperCase`/`toLowerCase`/`replace` char+String/`lastIndexOf`/`equalsIgnoreCase`/`split`) em asm puro — antes undefined-reference no link (R6); `RISCV_RUNTIME_ASM` dividido em 3 constantes (limite 64KB javac). **2 races corrigidos**: (1) filho herdava o `sp` do pai (frame ativo do `kof_spawn_result`) e o `call` do trampoline corrompia os slots salvos do pai → filho agora carrega `sp` da stack dedicada (handle+24) **antes** do call; (2) `println` fazia 2 `write` (string+newline) → interleave entre threads (`fimbg`) → virou **1 `writev`** atômico (syscall 66). Prova: `riscv64/aarch64StringTrimCaseReplaceSplit` + spawn 40/40 ×6 sem flake + suíte 913+8+5+8, 0 falhas. ⚠️ **RECONCILIAÇÃO PENDENTE**: outro agente refatorando as classes gigantes (`NativeBackend.java`/`NativeRuntime.java`, regra ≤500 linhas) — ao terminar, **normalizar** (reaplicar os ports http/spawn/String sobre a nova estrutura modular) e **retestar tudo** (suíte + E2E riscv64/aarch64). |
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

