[English](IMPLEMENTATION-UNIVERSAL-PLATFORM.md) | [Português](IMPLEMENTATION-UNIVERSAL-PLATFORM.pt_BR.md)

# Implementação — Kof como Plataforma Universal

**Tipo:** rastreamento de implementação — **EM DESENVOLVIMENTO** desde 17/09/2026
(promovido de `future/` por decisão da mantenedora; o portão R12 está
**sobreposto** — ver `DECISIONS.md` §D-UNIVERSAL)
**Companion (visão/arquitetura):** [`docs/architecture/UNIVERSAL-PLATFORM-VISION.pt_BR.md`](../architecture/UNIVERSAL-PLATFORM-VISION.pt_BR.md)
— filosofia, mapa de domínios, modelo arquitetural, estratégia de
stdlib/interop, riscos e não-objetivos que justificam estes passos.
**Base:** estado real 0.5.0-beta — 7 targets (jvm estável, native x86_64 estável,
native.risc/native.arm toolchain+qemu, js alpha GraalJS, kofc native-only,
android Fases 1–4), stdlib como **tabelas de dispatch em compile-time** com
gaps diagnosticados, FFI real (SQLite `.so`, FFM Vulkan compute, interop Java +
GraalJS).

> **Regra deste documento:** esta é a face **executável** da plataforma
> universal. Todo item abaixo é uma unidade de trabalho com status, lane dona e
> (quando landado) uma prova. A ordem de capacidades é fixa
> (FOUNDATION → SYSTEMS → AUTOMATION → INFRASTRUCTURE → DATA → SECURITY →
> SCIENTIFIC COMPUTING → BIOINFORMATICS → UNIVERSAL PLATFORM) e **o Estágio 1
> fecha antes de qualquer estágio posterior abrir** (R12, sobreposto apenas para
> o *agendamento* deste plano — nunca para as regras de freeze/qualidade). Cada
> unidade landa como qualquer outra mudança: Q0–Q7, aditiva, zero regressão,
> prova no mesmo commit. A semântica congelada do core fica 100% intacta.

---

## Legenda de status

| Marca | Significado |
|-------|-------------|
| ✅ | **FEITO** — landado com prova (data + commit + teste) |
| 🟡 | **PARCIAL / EM CURSO** — parcialmente landado ou com dono trabalhando |
| 🔵 | **FALTA** — executável, sem bloqueio, sem dono ainda |
| ⛔ | **DEPENDE DE DECISÃO** — decisão da mantenedora (regra 6), nunca edição de agente |
| 🔒 | **BLOQUEADO** — depende de outro item landar antes |

As lanes donas seguem a convenção de IP local do `DOING.md`
(`.15` = bugs-and-gaps/docs, `.17`/`.18` = native/development, `.22` = compiler).
Reivindique um item no `DOING.md` **no mesmo commit** que inicia o trabalho.

## Resumo

| Estágio | Nome | Status | Bloqueio |
|---------|------|--------|----------|
| 1 | SYSTEMS (consolidação) | 🟡 em curso | gaps de paridade web/native **1.1.3–1.1.9** (donos: lanes web/native) — **D1–D3 ✅ decididos 19/09** (GC x86 ✅ D1-A; registry ✅ 19/09) |
| 2 | AUTOMATION | 🟡 **majoritariamente pousado** — 2.1 `kof.workflow`/`kof.batch`, 2.2 `kof.shell`, 2.3 `kof.ssh`, 2.5 CI/CD como código Kof, 2.6 `kof workflow run` todos ✅ (paridade byte JVM==JS); 2.4 cron maduro 🟡 (`at(cron)` real JVM/JS, Nativo gap honesto `CRON001`) | residual 2.4 (`CRON001` Nativo); linhas do Estágio 2 de resto fechadas |
| 3 | INFRASTRUCTURE (Kof Makealive) | ✅ **todas as linhas 3.1–3.8 pousadas** (3.6 secrets pousou 21/09 `32285136`, fechado 21/09 `04473bbe`) — `makealive-plan.md` movido para `docs/architecture/` | Estágio 2, R3 (FFI), R4 (hook de codegen — **✅ pousou 21/09**); **colisão de nome R1 ✅ resolvida (`kof.makealive`, plano §2.1/Q1)** |
| 4 | DATA (engineering / science / ML) | 🔵 não iniciado | Estágio 3, R3 (FFI) |
| 5 | SECURITY (expansão) | 🔵 não iniciado | Estágio 3, R3 (FFI) |
| 6 | SCIENTIFIC COMPUTING | 🔵 não iniciado | Estágio 4, R3, GC (1.2) |
| 7 | BIOINFORMATICS | 🔵 não iniciado | Estágios 2/4/6 |
| 8 | UNIVERSAL PLATFORM | 🔵 não iniciado | todos os anteriores | — norte `DECISIONS.md` §D-BOOTSTRAP (20/09): o compilador escrito em Kof fecha este estágio de ponta a ponta (rascunho do plano BS-1 = lane `.18`) |

Invariantes: **R1 ✅ · R2 ✅ 20/09 · R6 ✅ · R7 ✅ · R8 ✅ · R12 ✅ (sobreposto)** ·
**R3 🟡 · R4 ✅ · R5 ✅ 21/09 (gate de máquina dos tiers; pacotes oficiais seguem no Estágio 7) · R9 🟡 · R10 🔵 · R11 🟡**

Fila transversal (não é estágio): **X1–X10** — gRPC, Python/R, WASM,
avaliação em compile-time, variance/sealed, reflexão de interop, debugger
DWARF/source map, testes de propriedade, `kof deploy`, LSP de domínio. Não-objetivos
permanentes (VISION §12) no fim. A **auditoria de gaps 18/09** fechou o drift
VISION×tracker (esses itens não tinham entrada executável).

---

# Estágio 1 — SYSTEMS (consolidação do que já é "sistemas")

**Objetivo:** fechar os gaps de paridade de *systems* que já existem — não abrir
domínio novo. **Este estágio fecha antes de qualquer Tier 6+ (R12).**

### 1.1 Gaps de paridade (web / HTTP / mídia)

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.1.1 | `WEB001` — base do servidor web no JS | ✅ 16/09 | lane web | `WEB001` fechado; JS GraalJS HttpServer |
| 1.1.2 | `WEB005` — código de gap de `app.serveDir` em não-JVM | ✅ 17/09 | `.15` | `b4957c06`; `KofMediaE2ETest.serveDirOnNonJvmEmitsWeb005NotWeb001` (JS + 3 alvos nativos); emitia o fantasma `WEB001` (§275) |
| 1.1.3 | `WEB002` — TLS no JS/Native | 🔵 | lane web | gap honesto em compile-time; pinado por `DomainGapCodesTest` |
| 1.1.4 | `WEB003` — SSE no Native (JS handler-scoped ✅ 16/09) | 🔵 | lane web | gap honesto; pinado |
| 1.1.5 | `WEB004` — WebSocket no Native | 🔵 | lane web | gap honesto; pinado |
| 1.1.6 | `WEB006` — middleware de segurança no JS/Native | 🔵 | lane web | gap honesto; pinado |
| 1.1.7 | `HTTP002` — https + DNS real no Native | 🔵 | lane native | HTTP/1.1 asm landado 03/09; `timeout`/`retry`/`circuit` REAIS nos 4 alvos nativos desde 17/09 (§259 FECHADO). `HTTP002` é código **reservado** (ramo morto — `KofHttp.supportedOn` sempre true) |
| 1.1.8 | `MEDIA001`/`MEDIA003` — handles de mídia / mic em não-JVM | 🔵 | trabalho de regra 6 adiado para 1.0 (`PARITY-GAPS` linha 4) | `Video`/`Audio` ✅ 26/09; `Image.open`/`Mic.record` = `MEDIA001` (não-JVM) / `MEDIA003` (JS); gap honesto em compile-time; documentado na matriz |
| 1.1.9 | `ORM001` — `kof.orm` no Native | 🟡 | `.18` (D-DB-GAPS DB-1 `DECIDIDO` 20/09) | JVM + JS fechados (JS 18/09, `KofJsOrmBridge`); rota no Native = `kof_orm_*` em asm **sobre a superfície `kof_db_*` nativa existente** (x86 primeiro, depois cross; sem atalho de embutir JVM, sem fallback silencioso). **F1 completo ✅ 20/09** — `delete_all`/`create`/`migrate`/`count` + **F3a `count` com bind (`count_where`) ✅** (gate por-função `fnSupportedOn`; provas: DDL byte-idêntico ao host medido no `sql` salvo em `sqlite_master` (`PRIMARY KEY AUTOINCREMENT` + `UNIQUE`), binds via `prepare_v2`/`bind_*`, `KofOrmE2ETest` 41/0F); Android `kof.orm` = paridade JVM (DB-2 ✅ 20/09); **F2a `save` row-object ✅ 20/09** (INSERT-gen/UPDATE/miss→INSERT-all nas 3 saídas do host, JVM==Native byte, `RuntimeOrm4`+`RuntimeOrmSchema`/`RuntimeOrmBind`; bug medido por gdb: literal `.ascii` sem NUL no `SELECT last_insert_rowid()`); **F2c `all`→List ✅ 21/09** (`RuntimeOrm6`, loop do find + `kof_list_add`; lista vazia≠null; oracle medido antes da asm); **F2c2 `where`/`where_op` ✅ 21/09** (`RuntimeOrm7`, arg7 na stack S7f, whitelist op + throw medido, GC-safe); **F2c3 `page`/`delete`/`saveAll` FECHADO 21/09 — row-object x86-64 completo** (`RuntimeOrm8/9/10`: page = `LIMIT ? OFFSET ?` bindados com trunc int32 = `intValue()` do host, KofString do coerce = atoi superset honesto documentado; delete = PK do schema via `parse_schema` + bind do key (classificador do Orm7), `true` sempre no DONE como `execute1 >= 0` do host — miss deleta 0 linhas e retorna true, medido no oráculo; saveAll = loop `kof_list_size`/`kof_list_get` → `kof_orm_save`, instância patchada descartada como no host); prova: `KofOrmE2ETest` 48/0F `pageDeleteSaveAllNativeEndToEndMatchesJvm` + `pageEdgesDeleteMissSaveAllEmptyNativeMatchesJvm` byte JVM==Native (batch, offset além, miss, lista vazia), pin honesto migrou p/ cross riscv64; **F2d1–F2d7 runtime-MySQL x86-64 FECHADO 22/09 — 13/13 faces `kof_orm_*` sobre o wire** (delete_all/count/delete/find/all/where/where_op/page/saveAll/save/count_where/create/migrate; prova byte JVM==Native por face contra o MariaDB 12.3.2 do host; CLIENT_FOUND_ROWS + dialeto backtick); restam `ORM001`: SÓ os espelhos cross riscv64/aarch64 (compile-time); **F2b `find` row-object ✅ 20/09** (`RuntimeOrm5`+resolver `kof_orm_ctors`: miss→null como o host, record construído no runtime, colunas por NOME, bool por parseBoolean do oráculo; key chega KofString pelo coerce do call-site — medido por gdb; bug novo §396 catalogado: println cru de record null = SEGV no Native, narrowing ok); **runtime-MySQL x86-64 ✅ 22/09 (F2d1–F2d7: 13/13 faces sobre o wire — prova byte JVM==Native por face contra o MariaDB 12.3.2 do host, `scripts/safe-suite.sh` 3602/0F @ `ded4ff01`)**; espelhos cross riscv/aarch ainda `ORM001` honesto |
| 1.1.10 | §278 — Android reusa `JvmBackend` mas recusa `kof.db`/`kof.security`/`kof.gpu` (`DB001`/`SECN00x`/`GPU001`) | 🟢 db/orm (20/09, DB-2; §278 PARCIAL) | `.18` na face DB (D-DB-GAPS DB-2 `DECIDIDO` 20/09) | **mantenedora: "Android É JVM" → recusa `DB001` levantada, paridade com o JVM** (pin `DomainGapCodesTest` vira paridade p/ db); `SECN00x`/`GPU001` seguem honestas até aquelas pilhas rodarem no Android (lanes próprias; mesmo princípio); medido com `CompilerDriver(Target.ANDROID)`; catalogado §278 |

### 1.2 GC mark-sweep no Native

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.2.1 | GC no riscv64 | ✅ | lane native | `356f33b9` |
| 1.2.2 | GC no x86_64 — decomposto G-1..G-5 + G-6 | ✅ | lane native; exec pousado por `.18` sob ordem da mantenedora | `docs/native-multiarch.md`; **auto-collect LIGADO (G-6(a) 19/09, opção D1-A)**: o gatilho de free-list exausta chama `collect_now` 1x por programa (blanket-spill 15 GPRs; gate `spawn_count==0` com `incq` movido p/ a entrada de `kof_spawn_handle_new`); §260 FECHADO; cap-test no repo `gcAutoCollectFitsUnderMemoryCap`; `KofGcE2ETest` 4/4; face MT worker-stack catalogada; hello 44→84 syms re-baselined com causa (bytes dentro do gate +5%) |
| 1.2.3 | Sign-off de re-baseline do auto-collect x86 | ✅ | mantenedora | D1-A DECIDIDO 19/09; EXECUTADO 19/09 (`.18`, ordem da mantenedora): hello 44→84 syms re-baselined COM causa no comentário, bytes dentro do gate +5%; §260 FECHADO; gatilho ligado (G-6(a)) com MT = comportamento antigo |

### 1.3 Event-loop / async real no JS

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.3.1 | `CONC003` — async/await/Promise reais no JS | ✅ 03/09 | lane JS | residual `CONC003-JS-01` (só task-lambdas podem ser async) |
| 1.3.2 | §132 — escalonamento cooperativo no KofJS (supervisor) | ✅ 18/09 | `.18` | entregue como **`time.sleep` async cooperativo** (`06d8b322`) — NÃO o rascunho generators+relógio lógico: ponto de await via `computeAsyncColoring` + Promise `kofTimeSleep` + bomba do host `KofJsRunner`; `OTP002` levantado. Prova: `AsyncSleepJsE2ETest` + `KofSupervisorE2ETest#supervisorJsParity`/`#supervisorJsS2Parity` (JS -> `restarts=2 fabrica=3`) |

### 1.4 DSL de query tipada

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.4.1 | `User.query { where ... }` | ✅ 01/09 | `.18` | `KofOrmE2ETest`; `0112bf32` (§193) |

### 1.5 Package manager MVP (`kofdeps`)

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.5.1 | `kof deps` + resolução Maven Central | ✅ | lane tooling | — |
| 1.5.2 | Resolução transitiva + `kofdeps.lock` | ✅ 16/09 | lane tooling | `DepsTransitiveTest` 10/10 (incl. E2E com Maven real) |
| 1.5.3 | Registry MVP | ✅ 19/09 | lane docs→plataforma | D2-A: **publish ✅** (era `b1ea1718`) + **pull ✅ S2 19/09** — `DepsRegistry`: `owner/repo[@ver]` → asset `<repo>-<ver>.tar.gz` da API GitHub Releases, `SHA256SUMS` verificado ANTES de instalar, cache `~/.kof/deps/kof/`, `latest` pinna a versão no `kofdeps`; REG001–004 honestos (R6); `DepsRegistryTest` 13/13 (shape REAL do GitHub + contrato HTTP; #564) + vizinhos `Deps*` 14/14; build/run consomem via `Deps.classpath()`; **round-trip live no GitHub ✅ 20/09** (release pública real, sem token, HOME limpo: pull por versão + `latest` + 2º resolve idempotente + REG001; era VERMELHO por #564/#565, corrigidas — evidência `docs/audits/registry-live-roundtrip-2026-09-20.pt_BR.md`); **consumo por código KOF = MÓDULO-FONTE (opção (b), decisão da mantenedora 20/09, #566) ✅ implementado:** o `kof deploy` empacota as fontes (`src/…`, cobertas pelo `SHA256SUMS`; biblioteca sem `.kf` no topo é aceita e publica só as fontes), `kof deps resolve` instala as fontes VERIFICADAS (REG002/REG004) e `kof run/build --deps` as entregam ao compilador — o `import` resolve contra elas (módulo local › stdlib oficial › deps; todos os alvos; `Classe()` sem `new` funciona); `DependencySourceRootE2ETest` 7/7 + `DepsSourceModuleTest` 8/8 + `CmdDeploySourcesTest` 5/5 (ciclo completo deploy → registry → `run --deps`); pacote legado (só jar) segue pelo classpath (idiom de interop `--deps` + `new Classe()`); **`CmdDeployTest` mudou de propósito** (o tar deixou de ter 3 entradas) |

### 1.6 Tracing / OpenTelemetry + ciclo de vida `application{}`

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.6.1 | Spans W3C + ciclo de vida `application{}` (3 alvos) | ✅ | lane platform | `KofObservabilityTest` 10/10 |
| 1.6.2 | Export OTel `exportSpans()` → OTLP/JSON (JVM/JS) | ✅ 17/09 | lane platform | `435b7013` |
| 1.6.3 | `OBS003` — export OTel no Native | 🔵 | lane native | gap honesto em compile-time (R7 JVM-first); pinado por `DomainGapCodesTest` |

### 1.7 Native → bare-metal / bootável

| # | Item | Status | Dono | Prova / nota |
|---|------|--------|------|--------------|
| 1.7.1 | Seam HAL `kof_plat_*` + perfil freestanding (faces B-0…B-6) | 🟡 | lane baremetal (sessão 9092) | `docs/PLAN-BAREMETAL-BOOT.md`; **EM DESENVOLVIMENTO — promovido 22/09** (`D-BAREMETAL-BOOT`); B-0 é a primeira fatia executável; MCU depende de 1.2 |
| 1.7.2 | Agendamento das faces bare-metal | ✅ | — | **`D3-A` 19/09 + `D-BAREMETAL-BOOT` 22/09: frente ABERTA, R12 sobreposto**; escopo ordenado = bare-metal com ring0/ring1 (B-6); a ordem do §7 do plano governa |

---

# Estágio 2 — AUTOMATION (camada unificada)

**Objetivo:** Kof como camada de automação *unificada* (substituir
Bash+Python+YAML+jq+sed+awk **numa única linguagem tipada**).
**Dependências:** Estágio 1 (concorrência, scheduler, mq prontos).
**NÃO fazer:** não reimplementar bash; jobs são **código Kof**, não YAML.

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 2.1 | `kof.workflow` / `kof.batch` — jobs, pipelines, retry, checkpoints, dead-letter | ✅ | `.18` | **FECHADA 20/09 (re-medido no tip):** toda a superfície do Stage 1 entregue — MVP 2.1.2 host puro-Kof `workflow-host.kf` injetado flat (`import kof.workflow` → `job`/`dag`/`after`/`run`/`Report`, paridade de bytes JVM==JS); bundle 2.1.3 completo — retry `7db91735`, deadLetter `95f81747`, checkpoint `ee63dc80`, schedule `d9adeb03`; **`kof.batch` é o nome da visão PARA ESTA MESMA superfície** (linha C, “new namespace kof.workflow/kof.batch”) — módulo à parte não existe no plano assinado (`docs/workflow-plan.md` CONCLUÍDA 19/09, enquete Q1–Q4 do maintainer). Prova medida no tip: `WorkflowPrimitivesE2ETest` 6/6 + `WorkflowE2ETest` 23/23 (paridade JS §387) + `StdlibIdiomsCompileTest` 20/20 (4 alvos) + `CmdWorkflowTest` 12/12 (CLI `--target js` `e26dc549`). Native: gaps honestos `CRON001`/`PROC001`/`ORM001` mantêm suas células na matriz. |
| 2.2 | `kof.shell` — shell idiomático sobre `kof.process` | ✅ | `.18` | **FECHADO 20/09** (Estágio 1): as cinco faces `cmd`/`run`/`runWith`/`ok`/`pipeline` são REAIS em JVM+JS — plano `docs/shell-plan.md` SIGNED-OFF 18/09 (enquete Q1–Q3) + 2.2.3 runWith ✅ 19/09 (ambiente aditivo, falhas honestas `-1`) + face JS de `process.spawn` ✅ 19/09 (bug de roteamento §355 morto na raiz — as ops de handle nunca haviam rodado em NENHUM alvo) + cadeia JS do `pipeline` ✅ 20/09 (threads de pump em `KofJsProcessBridge`; pin multi-pump de 3 estágios). Prova: `ShellE2ETest` 16/16 + `ProcessSpawnE2ETest` 4/4, paridade byte JVM==JS em tudo. Native = `PROC001` honesto esperando o `process.run`/spawn da lane nativa em asm (R7 — a matriz mantém a célula); glob/`~`/redir fora do v1 por decisão. |
| 2.3 | `kof.ssh` — exec remoto | ✅ | lane 9092 | **FECHADO 21/09** (Estágio 2, MVP): `ssh.cmd(host, command) -> List<String>` é o construtor de argv (`["ssh","-o","BatchMode=yes","-o","ConnectTimeout=5",host,command]`) — host e command ficam UM elemento cada, então a classe de injeção do `sh -c` fica fora por construção; `ssh.run(host, command) -> Result` rebaixa para a camada de processo já provada (mesma forma de Result do `kof.process`; falha de spawn/conexão é Result honesto, nunca throw/hang — BatchMode + ConnectTimeout) e `ssh.ok(result)` é puro sobre esse Result compartilhado. **Real em JVM+JS com paridade byte; Native = `PROC001` honesto (R7 — mesma célula de process/shell).** `libssh`/FFI não é necessário para o MVP: a VISION §6.1 opção B diz "sobre kof.process/FFI" e este é o caminho de processo. Prova: `SshE2ETest` 8/8. Linha no ledger R1 adicionada (36 namespaces). |
| 2.4 | Cron/scheduler maduro | 🟡 | lane concurrency | `at(cron)` cron real de 5 campos UTC no JVM/JS desde 17/09 (§274); Native `CRON001` gap honesto |
| 2.5 | Pipelines de CI/CD como **código Kof** | ✅ | plataforma lane (.15) | **19/09 (2.1.3 landou completo nos commits irmas: retry+deadLetter+checkpoint+schedule)**: o pipeline CI e codigo Kof tipado e o runner funciona HOJE — medido: pipeline verde `kof run` = exit 0, job falho + `throw` = exit 1 com `pipeline red: <summary>` nomeando o job (`StdlibIdiomsCompileTest#greenPipelineExitsZeroRedPipelineExitsNonZero`, golden de saida MEDIDO). Corpus: `training/idioms/automation.md`(+PT) — todas as formas (`job/dag/after/retry/exponential/retryFixed/deadLetter/checkpoint/schedule`) travadas compilandolas nos 4 alvos + matriz honesta (Native: `CRON001`/`ORM001` em runtime; SCRIPT: `COMP003` por design do alvo). **D-WORKFLOW-RUN (DECISIONS.md)**: entregue — `examples/ci/ci-pipeline.kf` (checkout→build→test→package, artefatos reais no fs, retry + dead-letter) + golden `CmdWorkflowTest.realCiPipelineExampleRunsEndToEnd`. |
| 2.6 | Tooling: `kof workflow run` | ✅ | lane plataforma (sessão 19/09-3, 9093) | **FEITO 19/09 (D-WORKFLOW-RUN)**: `CmdWorkflow` `list`/`run`/`--job`/`--dry-run`/`--json` sobre `pipeline(): KofWfDag`; host `order()`/`runJob()` (`2372f6d4`), CLI (`7c9f9e59`), `CmdWorkflowTest` 9/9; JVM-first — JS/Native são fatias seguintes honestas (R7) — contrato: DECISIONS.md §D-WORKFLOW-RUN; face runner (exit 0/!=0) medida na linha 2.5; **20/09 `.18`: `--target js` FEITO (in-process via `KofJsRunner`, paridade de bytes com JVM, `CmdWorkflowTest` 12/12); `native`/`script` seguem follow-up** |

---

# Estágio 3 — INFRASTRUCTURE (IaC + cloud) — **Kof Makealive**

**Objetivo:** infraestrutura como **código Kof tipado** com
plan/apply/state/reconciliation.
**Dependências:** Estágios 1–2; **FFI formalizada** (R3, dependência
arquitetural); capacidades de pacote (1.5).
**NÃO fazer:** HCL dentro do Kof; um repositório de provider para *tudo*;
acoplar o core a um provider.
**Plano (19/09, diretiva da mantenedora, lane `.18`):** [`makealive-plan.pt_BR.md`](../architecture/makealive-plan.pt_BR.md).
**Colisão R1 MEDIDA 19/09:** o literal do tracker `kof.infra` é HARD-DENY em
`scripts/check_stdlib_boundary.sh` (rc=1; plano §2.1) — o namespace é a
pergunta da mantenedora **Q1** (plano §6); nenhuma superfície embarca antes.
A forma imperativa-transformada-em-dados (VISÃO §4.2 "A/B — Kof puro hoje")
NÃO precisa de **R4**; R4 barra só as linhas declarativas (3.2, 3.7).

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 3.1 | `kof.makealive` (Q1 `DECIDIDO` 20/09 — era o literal `kof.infra` vetado pela R1) — records de recurso + grafo de dependência + diff | ✅ 20/09 | `.18` (plano + recon 3.0; núcleo em voo) | **Q1–Q4 RESPONDIDAS 20/09 (`DECISIONS.md` §D-MAKEALIVE)**; fatia = COMPLETA por MK-1 (núcleo + providers REST/CLI + estado kof.db, 3.4/3.5 dobrados); face imperativa não precisa de R4 (plano §5 3.1); recon 3.0.1 ✅ + 3.0.2 ✅ + sonda de forma do provider 3.1.0 ✅ (`MakealivePrimitivesE2ETest` 6/6) | **3.1 CORE POUSOU 20/09 (`9e8be985`+`f5256f8f`):** `makealive-host.kf` (faces Q4: `Infrastructure`/`Provider`/`plan`/`apply`/`destroy`) + injetor `CompilerMakealive` + ledger R1 + `MakealiveE2ETest` 4/4 (paridade byte JVM==JS, Native compila; gate lambda-FT §379 (renumerado 3x no tip) renumerado no rebase; §380 ABERTO JS if-aninhado-com-throw — workaround guards de nivel unico na face que falha). MK-1: face de estado kof.db POUSOU 20/09 (`makealive-db-host.kf` + stub `.native.kf` ORM001 + `MakealiveDbHostE2ETest` 3/3, JVM==JS byte, update=max-gen, linha MARCA p/ recurso sem props; §381 catalogado no caminho); **providers fs/CLI/REST POUSARAM 20/09 (`4ee3a5c9`+`f62206e0`+`13b44c6c`):** fs golden (`MakealiveFsProviderE2ETest` 1/1), CLI golden (mundo via `shell.run`+`test -f`, `exitCode -1` nunca throw) e REST golden (`http.get`=body String, 404 vazio sem throw, sonda `http.status(url)`) rodam JVM->JS contra o MESMO mundo e comparam byte a byte; docs stdlib `docs/stdlib/makealive.md` EN+PT landadas; MK-1 FEITO (3.4/3.5 dobradas e viram ✅)**; a mesma pilha carregou o fix que virou ✅ FIXED no §372 da lane docs (ArrayType) |
| 3.2 | `infra "prod" { ... }` — desugar sobre records (codegen em compile-time) | ✅ | `.18`/9093 | **POUSOU 21/09 (`D-MAKEALIVE-SYNTAX` + commit `966c86a4`)** — puro açúcar sintático sobre `design()`: `infra` segue IDENTIFICADOR (despachado como `test`/`application`, não palavra reservada), o bloco rebaixa para `design(): Infrastructure` e não ganha semântica própria (sem HCL). **Prova: `InfraSyntaxE2ETest`** (gêmeo `design()` escrito à mão → plan/apply byte-idênticos, JVM==JS). R4 ✅ (hook `CodegenStep`) removeu o bloqueio de codegen. |
| 3.3 | Loop de reconciliação (spawn/await + channel) | ✅ 20/09 | `.18` | Estágio 1 (2.1); pousou como `reconcile(design, provider, intervalMs)` sobre `scheduler.every` (tick = `apply` dentro de um `spawn`, forma CONC003-JS-01; o "channel" = o jobId do scheduler — pare com `scheduler.cancel(id)`, sem faces novas). SEM stub no Native (medido 20/09: o CRON001 gateia `scheduler.at`, nunca o `every` — SCHED001 fechado cross 05/09); `MakealiveReconcileE2ETest` 1/1 x3 serial, JVM==JS byte, pin de compilação Native |
| 3.4 | Estado em `kof.db` | ✅ 20/09 | `.18` | **dobrado no 3.1 pelo MK-1 (20/09)**; item de verificação por target mantido; estado no Native gated pelo §D-DB-GAPS |
| 3.5 | Providers via FFI/REST/CLI (AWS/Azure/GCP — interop) | ✅ 20/09 | — | R3; v1 = os providers genericos medidos e verdes (goldens fs/CLI/REST 20/09, JVM==JS); nuvens concretas sao PACOTES OFICIAIS (`infra-<cloud>`, D-MAKEALIVE Q2), nao superficie do compilador |
| 3.6 | Segredos via `kof.security` | ✅ 21/09 | lane security | `D-SECRETS` **COMPLETO 21/09** (`32285136` face 1 + `04473bbe` resto-P1/P2/P3): tipo valor `Secret` — `secrets.of`/`secrets.secret`/`fromBytes`, `reveal()` (único export cru), impressão redigida (`Secret(*** )`), `==` constant-time; **P2** redação forçada (runtime `json.encode(Secret)` → `"Secret(*** )"`, lint compile-time `SECN009`); **P3 `KeyHandle`** (`secrets.keyFromHex/keyFromPem/keyFromKeystore`, `rotate()` → handle antigo revogado `SECN010`, sobrecargas `KeyHandle` de `crypto`/`jwt`, chave crua nunca exposta); JVM-primeiro (R7), JS/Native/Script/Android = `SECN008` (R6). Prova `SecretE2ETest` 7/7 + `KeyHandleE2ETest` 5/5. `secrets-plan.md` movido para `docs/architecture/`. |
| 3.7 | Detecção de ciclo no grafo `infra` em compile-time | ✅ | `.18`/9093 | **FECHADA 21/09 como runtime-only** (adendo ao `D-MAKEALIVE-SYNTAX`): com a 3.2 como açúcar puro o compilador só vê chamadas genéricas, então um grafo estático daria semântica própria ao bloco (§7/regra 11); a recusa em runtime (3.1) nomeia os membros do ciclo — esse é o contrato. |
| 3.8 | Tooling: `kof makealive plan/apply/destroy` | ✅ 20/09 | `.18` | **FEITO (D-MAKEALIVE-CLI, contrato decidido 20/09 por delegação do maintainer)**: o verbo segue o nome decidido no Q1 (`makealive`, não `infra`); convenção `design()`+`provider()` à la D-WORKFLOW-RUN; MARK `@@KOF_MAKEALIVE@@ `; estado h2 `--state` com gen=max+1 + MARCA de estado vazio no destroy (bug de geração invisível achado pelo E2E; `mkMaxGen` landed); paridade de bytes JVM==JS, recusas script/native (R7). Prova: `CmdMakealiveTest` 7/7 + `MakealiveMaxGenE2ETest` 4/4 + bateria Makealive 14/14. |
---

# Estágio 8 — UNIVERSAL PLATFORM (integração)

**Objetivo:** uma aplicação **+** sua infra **+** seu deploy **+** seu pipeline
de dados **+** sua segurança **+** sua pesquisa — **na mesma linguagem**, com a
mesma experiência de desenvolvimento.
**Dependências:** todas as anteriores; package manager; FFI.
**NÃO fazer:** deixar o core crescer para "suportar" a plataforma — o core
**não deve mudar** (ou mudar quase nada) até aqui.

| # | Item | Status | Dono | Depende de |
|---|------|--------|------|------------|
| 8.1 | Integração total dos Estágios 1–7 | 🔵 | — | todos |
| 8.2 | Package manager maduro | ✅ | plataforma lane (.15) | **19/09 (auditoria medida contra o DoD do roadmap 1.4)**: subcomandos `init/add/remove/list/resolve` (`Deps.java:48`); Maven Central GAV exato + transitivo via delegação Maven + `kofdeps.lock` (16/09, `DepsTransitiveTest` 10/10); **checksum sha256 verificado no pull** (`Deps.verifyChecksum`→`CmdDeploy.sha256Hex`); registry GitHub Releases: publish D2-A + pull 1.5.3-S2 (`DepsRegistryTest` 6/6). Modelo = GAV exato + lock reprodutível; **não medido** range/semver solving nem registry privado além de `owner/repo` — reabrir só com necessidade concreta (KOF-first: nenhum pedido hoje). |
| 8.3 | LSP/debug/profiler por domínio | 🟡 | lane tooling/docs (.15) p/ LSP | **LSP domain-aware ✅ 19/09**: completion + hover leem o `StdCatalog` (namespace lista membros; membro no contexto `ns.` nomeia a face; solto = null honesto). **rename cross-file ✅ 19/09 (LSP-A, `LspRename`)** + fix de higiene: o scan de irmãos nunca trata /tmp ou a raiz do FS como projeto (profundidade 1 lá; 485 `.kf` alheios medidos em /tmp). **8.3-B FEITO 19/09 (`.18`)**: `workspace/symbol`/hover/definition/references cobrem deps fora do pai do arquivo via `rootUri` do initialize (`LspProject.siblings(self,root)`, dedup+ordenado; sem rootUri = comportamento antigo exato). **Pendente (estado corrigido vs o código, medido 19/09 ~20:3x):** o MVP `kof debug` JVM (DAP+JDWP) existe (roadmap §19.5 fases 1-3); `kof profile` EXISTE como profiler real de processo (wall/RSS/pausas-GC/faults/ctx via /usr/bin/time -v + gc.log, com ponteiros honestos p/ JFR/perf/DevTools no nível de método) — **profiler de AMOSTRAGEM interno (nível de método) ✅ 20/09 ~05:1x**: `kof profile --methods` grava `jdk.ExecutionSample` com o JFR do próprio JVM (`jdk.jfr`, sem ferramenta externa), agrega os métodos quentes e mostra a **linha da fonte Kof** (o LineNumberTable mapeia o bytecode de volta ao `.kf`; `ProfileMethodsTest` 4/4 — função quente real + recusas honestas). **Nível de método no JS ✅ 20/09 ~07:2x**: `kof profile --methods --target js` roda o módulo emitido sob o `--cpu-prof` do próprio Node (parte do Node, sem ferramenta externa) e o `.mjs.map` emitido mapeia a linha JS amostrada de volta à **linha da fonte Kof** — o equivalente JS do LineNumberTable; internos do Node filtrados, Node ausente = falha honesta (`ProfileMethodsTest` 5/5: `spin` quente com `(line …)`, falha por Node ausente, recusa Native). **Native = recusa honesta nomeando perf + o `perf_event_paranoid` medido** (R6/R7 — sem substituto interno onde o sysctl proíbe); front-ends de debug para Native (gdb/DAP) ✅ X7-3/X7-4, JS (node inspector) = gap honesto (engine embutido); profiler por domínio = resíduo + X7; signatureHelp por domínio — **assinaturas no hover ✅ 19/09 (LSP-A fatias 1–6: 31/32 ns, 263 membros/280 formas; tabela = artefato de `scripts/gen_signatures.py`, NUNCA edicao manual)**: `StdCatalog.signaturesOf` (tabela no `StdCatalog`, fonte única) alimenta o hover de `db`/`http` com um overload por linha, travada COMPORTAMENTO-A-DISPATCHER (`StdCatalogSignaturesTest`: a aridade gravada binda no `staticCall` real, a proibida não). No caminho, **DOIS bugs reais do catálogo X10 achados pela fatia**: o `KofDb.functions()` e `KofProcess.functions()` só tinham os `case`/primeiro roteamento (connect/close/transaction) — `query`/`execute` viviam nas famílias `isQuery/isExecute` e sumiam do completion/hover (query/execute 3→5; run/spawn/exit 1→3) + o lock passou a somar os literais das famílias (fonte-a-fonte). **Restante:** NENHUM — **32/32 ✅ 19/09 ~22h (fechamento X10)**: `json` entrou com `encode(value) -> String` / `decode<T>(jsonString) -> T`, travados COMPORTAMENTALMENTE na aridade real do typer (o dispatcher por aridade SEMPRE existiu — `MemberCallNamespaces` cobra 1 arg + `<T>` do decode com SEM025; o que não existia era a tabela; o dispatch POR TIPO segue no lowerer/`JsonDispatch`, imutável — nenhuma semântica de linguagem mudou); a fatia 3 trocou o lock de case-literals por um que segue vírgulas e **achou 4 drifts de catálogo ainda maiores** (net 1→8, encoding 2→8, math 19→23, strings 7→26 — 38 membros bindando no dispatcher, invisíveis no completion desde a fatia 1; todos corrigidos com trava comportamento-a-dispatcher) (fatia a fatia, sem fingir cobertura — membro sem tabela mantém hover simples, R6) **REQUEST `textDocument/signatureHelp` ✅ 19/09 (LSP-A, `LspSignatureHelp`)**: a mesma tabela do hover — dentro de `ns.member(` com tabela devolve as formas + `parameters` + `activeParameter` (vírgulas de top-level, ciente de string/escape/parênteses/colchetes; clamp na forma mais larga); fora de chamada, membro sem tabela, ambíguo ou desconhecido = null honesto (32/32 desde 19/09 ~22h — `json` na tabela, forma bindada ao SEM025 do typer). Capability `signatureHelpProvider` anunciada; travada no round-trip do servidor (`LspServerTest`) + 7 casos de borda (`LspSignatureHelpTest`) | |
| 8.4 | Deploy multi-target (mesma fonte → JVM/Native/JS) | ✅ | lane tooling/docs (.15) | **núcleo ✅ 19/09 (X9 fatia 4)**: `--target jvm,native,js`/`all` = mesma fonte, uma release por face + `.deploy-manifest.json` (SUCCESS/FAIL honesto por alvo, R6/R7, exit 1 se alguma falhar); faces cross ✅ 20/09 (X9 fatia 6: empacotam como o x86; toolchain ausente = falha honesta nomeando a ferramenta) + publish (D2-A ✅ 19/09) — **D2-A ✅ 19/09**: `--publish` deixou de ser ⛔ (face GitHub Releases na fila) |
| 8.5 | Documentação/corpus (`training/`) dos domínios | ✅ | lane docs | **FEITO 19/09 (3 fatias)**: `stdlib.md` (+time/process/net→cache/config/log/net + gpu/media + matriz por-target MEDIDA), `security.md` NOVO (+PT; 14 probes por-membro; no caminho 3 labels errados da tabela corrigidos NA RAIZ no gerador + trava de label + round-trip golden), observability metrics/health em `architecture.md`, `orm.saveAll` em `database.md`. Guardas: `StdlibIdiomsCompileTest` 18/18 + `KofScriptStdlibParityTest` (goldens interpretador==JVM medidos). Residual honesto: golden cross riscv/aarch das faces novas (guarda qemu — lane nat). |
| 8.6 | **Teste final:** o core da linguagem quase não cresceu | ✅ | plataforma lane (.15) | **19/09: `LanguageCoreSurfaceTest` 6/6** — trava por golden MEDIDO no tip (jshell, nunca memória): 64 palavras reservadas, 17 binários, 17 unários, 6 comparações, 119 tokens (ordem incluída), 8 variantes seladas de `Type` + `isSealed`. Qualquer crescimento do núcleo (gramática/operators/type-model) vira falha vermelha = rule 6 com bump+doc+migração no mesmo commit; feature de plataforma não passa mais por dentro do núcleo sem decisão explícita. |

---

# Fila transversal (VISION §6.1 interop + §7 compilador + §9 tooling)

> Auditoria de gaps 18/09: estas capacidades estavam descritas no companion VISION
> (superfícies de interop, requisitos de compilador, tooling) mas **não tinham
> item executável** nas tabelas de Estágios 1–8. São transversais, não um
> estágio de domínio. Cada uma entra como unidade própria quando seu estágio abrir;
> nenhuma muda o core congelado. `⛔` = exige decisão da mantenedora (regra 6)
> antes de qualquer edição.

| # | Item | Estado | Dono | Fonte / nota |
|---|------|--------|------|--------------|
| X1 | gRPC no `kof.web` (`app.grpc { }` + `.proto` → codegen IR + `grpc.call`) | 🔵 | lane web | VISION §6.1 "B/C"; `roadmap.md` §19 (31/08) — paridade JVM primeiro, Native/JS depois |
| X2 | Interop Python/R (CLI/`kof.process` + protocolo JSON) | ✅ COMPLETO 27/09 | — | VISION §6.1 "B"; o ecossistema científico como *ferramenta*, não dependência (Estágio 4). Fatias 1–5 landadas (`D-COMPLETE-FIRST` item 2): faces tipadas `KofPy`/`KofR` + `callJson`/timeout; cross riscv64≡aarch64≡JVM; prova `InteropTimeoutE2ETest`+`InteropRE2ETest` (plano movido p/ `docs/interop-engine-plan.md`; `roadmap.md` §2.8.6) |
| X3 | Alvo/interop WebAssembly | 🔵 | — | VISION §6.1 "D" (futuro); portabilidade de componentes — pesquisa, não agendado |
| X4 | Avaliação leve em compile-time (const-folding de domínio, validação de schema/ciclo) | 🔵 | lane compilador | VISION §7 "B" — estende o otimizador; NÃO é um TCC geral; distinto do R4 (codegen) |
| X5 | Tipos variance / sealed | ✅ FEITO 21–22/09 | lane compilador | `D-TYPE-VARIANCE`=C: `sealed`+`SEM080`, `switch` exaustivo+`SEM081`, `out`/`in` no sítio de declaração+`SEM082`, projeção no sítio de uso `List<out T>`/`List<in T>`; X5.0–X5.5 landadas; plano CONCLUÍDO + movido p/ `docs/type-system-extensions-plan.md` 22/09 |
| X6 | Reflexão de interop (restrita ao interop) | ✅ FEITO 21–22/09 | lane compilador | `D-INTEROP-REFLECT`=A (intrínseco de compile-time): `interop.schema(R)` → `List<Field>` imutável, zero reflexão em runtime, todos os alvos, só fronteira; `INTEROP001`/`INTEROP002`; X6.0–X6.3 (`InteropSchemaE2ETest` 18/18); plano CONCLUÍDO + movido p/ `docs/` 22/09 |
| X7 | Debugger Native DWARF + source maps JS | 🟡 | lane tooling | VISION §9; `roadmap.md` §19.5 — source map V3 do JS ✅ 01/09 (`KofJsSourceMapTest`); DWARF Native x86-64 ✅ REAL (`NativeDwarf.java`: `.debug_line`+`.debug_info`+`.debug_abbrev`, LIGADO por default, `--release` remove; travado em `NativeDwarfLineInfoTest`+`NativeDwarfSubprogramTest` — medido no ELF do tip: as 3 seções presentes); **fatia 1 do cross ✅ 19/09**: a line table `.file`/`.loc` agora TAMBÉM nos cross — o riscv emite as diretivas, o tradutor aarch64 as repassa verbatim (diretivas `.` não são traduzidas); prova `NativeDwarfCrossTest` (nível `.s` no host; ELF `.debug_line` via objdump roda na CI com toolchain). **fatia 2 do cross ✅ 19/09 ~23:5x**: DIEs CU/subprogram TAMBÉM nos cross — o `NativeDwarf` ganhou `Arch` (frame_base: rbp x86 / regx-x27 riscv / reg29 aarch) e o pipeline riscv registra `.Lfe_`+Fn com os slots reais da moldura (`NativeDwarfCrossRegister`; o aarch herda via tradução verbatim); prova `NativeDwarfCrossTest` (`.debug_info`/`.debug_abbrev`/`.asciz "main"`/byte do frame_base por ABI, + ELF `objdump --dwarf=info` na CI com toolchain). **fase 6 NATIVE ✅ 20/09 ~00:5x**: `kof debug --target native` = build do ELF com DWARF + gdb dirigido com o `directory` da FONTE Kof (o usuário escreve `break Main.kf:2`, nunca o mangle; `KOF_GDB` = override de teste/ambiente; prova `KofDebugNativeTest` 4/4 via stub-gdb — host sem gdb medido, gdb real na CI; js = recusado honesto: engine embutido, sem inspector para anexar). **fase 6 fatia 2 ✅ 20/09 ~03:0x**: `--break <linha>` = sessao BATCH scriptavel (para na LINHA Kof + `bt`, amigavel a CI — o gdb real deste host mediu `main (x=41) at Main.kf:4`) e `--output <dir>` preserva o ELF; ambos honestos no JVM (`only apply to --target native`); prova `KofDebugNativeTest` 7/7 (batch com gdb real + stub-gdb). **fase 7 DAP<->gdb ✅ 20/09 ~03:2x**: `kof debug --dap --target native` = ponte DAP<->GDB/MI para o editor (Kof NÃO reinventa um debugger — só traduz; launch/-break-insert/-exec-run/-stack-list-frames/-stack-list-variables/-data-evaluate; o editor sempre vê o .kf). Prova `KofDebugNativeDapTest` 3/3 com stub-gdb MI (conversa completa do editor + erro honesto de gdb ausente + evaluate sem símbolo = `success:false`, nunca valor inventado); gdb real na CI. **attach (X7-5) ✅ 20/09**: JVM `--dap --attach <pid>` (JDWP cru numa VM viva — o debuggee SOBREVIVE ao disconnect) + Native `--dap --attach <pid>` (gdb `-p`, o MI nunca dá `kill` num processo alheio); no caminho o cliente JDWP do JVM foi reconstruído contra o wire do JDK 25 medido (§376: IDSizes=5, (1,2) morto→(1,3), FrameCount, VariableTable real, eventos) e o launch/attach ganharam SEUS PRIMEIROS E2E (`KofDebugJvmTest` + `KofDebugAttachTest` 3/3) — o "JVM DAP ok" anterior era FALSE-GREEN sem teste nenhum na árvore (§377). **step/evaluate do DAP JVM ✅ 20/09**: `next`/`stepIn`/`stepOut` (JDWP `SingleStep` kind 1 + modificador Step kind 10) e `evaluate` (nome de local do frame; o JDWP nao tem avaliador de expressao = recusa honesta), paridade com o Native — prova `KofDebugJvmStepTest` 3/3 (break -> evaluate local -> next -> stepIn em `add` -> stepOut); no caminho o `LocalVariableTable` Start=0 do backend JVM foi medido e catalogado (§385) e o `JdwpValues.locals` ganhou fallback por-slot (omite o ilegivel, nunca inventa). **pause + setExceptionBreakpoints ✅ 20/09**: `pause` suspende as threads de USUARIO (nunca as do proprio agente JDWP — medido: suspende-las congela o protocolo) e `setExceptionBreakpoints` arma o evento Exception (kind 4 + ExceptionOnly 8; caught/uncaught) — prova `KofDebugJvmExceptionTest` 2/2; no Native `pause` = `-exec-interrupt --all` e exception = breakpoint em `kof_throw_string` (caught/uncaught = `verified:false` honesto, a cadeia de throw Kof nao e C++). No caminho, um frame sem debug info (nativo, ex. `Thread.sleep`) abortava o `stackTrace` inteiro com `NATIVE_METHOD` (511) — agora esse frame vira `?`/-1 e os frames Kof sobrevivem. **Residual real: face JS do DAP** = gap honesto (engine embutido sem inspector) — **bloqueio medido 20/09 (dois, não um):** (a) o runtime JS do Kof é o **GraalJS embutido** (`KofJsRunner`, in-process) — `node` NÃO é o runtime de produção (só o profiler/testes o usam), então o inspector do node é irrelevante e ligar o inspector do GraalJS é decisão de engine (rule 6); (b) mesmo com inspector, o source map emitido é **por FUNÇÃO** (`JsIr.JsFunctionLine` = 1 mapeamento por declaração), então breakpoint por LINHA Kof exigiria mapeamentos por statement no emissor (lane compiler) — um breakpoint de entrada de função que se apresenta como a linha L seria fachada (Q7), nunca entregue (locals do debug JÁ existem nos 3 nativos: `DW_TAG_variable`+`DW_OP_fbreg`+`DW_AT_type` — medido com objdump 20/09 depois que um tick desta lane catalogou o contrário POR MEMÓRIA; o gap aberto naquela hora era FALSO e foi corrigido no mesmo dia — shapes measured, never assumed) |
| X8 | Testes property-based | ✅ | lane docs→plataforma (192.168.100.15) | fatias 1–2 ✅ 18/09: namespace `rng` (xorshift128+splitmix32 semeável) em JVM+JS+**NATIVE x86_64** — `KofRngTest` 11/11 incl. paridades byte JVM==JS e JVM==NATIVE (asm `RuntimeRng`, bits por construção) + `RNG001` honesto em cross/ANDROID (`a71f761c`,`1ff54c6e`,`367af29d`); fatia 3 = runner property no `kof.test`; port cross pede qemu (lane nat) — **X8-A ✅ 19/09**: kof.test = spec exata do roadmap §G6; **fatia 3 em curso** — **timeouts ✅ 19/09** (`kof test --timeout <seg>` mata o filho JVM/Native no prazo, FAIL honesto R6, JS best-effort declarado; `CmdTestTimeoutTest` 3/3); **suítes nomeadas por diretório ✅ 21/09** (`kof test <dir>` recursivo: uma suíte nomeada por diretório + resumo; `CmdTestSuiteTest` 2/2); restante G6-next ✅ **DECIDIDO 21/09 (`D-PROPERTY`): sem superfície nova** — property = idioma `test`+`kof.rng`+`assert`, fixtures = `close()`+`try/finally` (D5-B); prova `PropertyTestIdiomE2ETest` 7/7 (checksum JVM==JS==Native) |
| X9 | `kof deploy` (build + pacote + publish) | ✅ | lane tooling/docs (192.168.100.15) | fatias 1–3 ✅ 18/09: JVM (fat jar) + NATIVE (ELF 0755) + JS (.mjs) + ANDROID (APK via pipeline --apk do build) — release = artefato + RELEASE.md + SHA256SUMS + tar.gz (`CmdDeployTest` 9/9+1-skip, módulo 322/322; `154ea1a4`, `bfdd452a`, fatia 3); cross riscv/arm = `DEP001` honesto (suprimido pela fatia 6); `--publish`/registry = ⛔ D2; **fatia 4 ✅ 19/09 (linha 8.4)**: multi-target da MESMA fonte — `--target jvm,native,js`/`all`, uma release por face (subdir `-jvm/-native/-kofjs`) + `DEPLOY-MANIFEST` (`.deploy-manifest.json`), FAIL por alvo não derruba os demais (R6), exit 1 com falha; `CmdDeployTest` 13 (11+2-skip), cli 339/0F — **fatia 5 ✅ 19/09 (D2-A)**: face `--publish` = GitHub Releases (tar.gz+manifesto, 422=reuso, sem token=falha honesta); multi-target da linha 8.4 landado `28b004c4` — **fatia 6 ✅ 20/09 (pacote cross)**: `--target native.riscv64|native.aarch64` empacota o ELF cross pelo mesmo pipeline do x86 (0755 + RELEASE.md + SHA256SUMS + tar.gz); a recusa preventiva `DEP001` saiu do deploy (R6: ele tenta de verdade; sem toolchain = falha honesta nomeando a ferramenta); `KOF_CROSS_PREFIX` é o override de toolchain da casa (padrão KOF_GDB); prova no host = stubs `as`/`ld` (`CmdDeployTest#crossReleasePackagesWithStubToolchain` + `crossDeployWithoutToolchainFailsHonestly` + falha parcial determinística), toolchain real/qemu = job cross da CI — **X9 CONCLUÍDA 20/09: faces JVM/Native/JS/Android/cross + multi-target + publish (D2-A) + pull (1.5.3-S2) todas landadas; prova total na linha e em `backend-parity`** |
| X10 | LSP domain-aware (completion + ir-para-definição em pacotes) | ✅ | lane docs→plataforma (192.168.100.15) | fatias 1–3 ✅ 18/09: `StdCatalog` = **31 namespaces** (**medido 21/09: 34** — o `StdCatalogTest` trava 34; buffer/shell/ssh pousaram após o fecho do X10) completados por membros REAIS do typer (7 KofStd + time/http/db/cache/process + segurança×6 + json/log/orm/config/gpu/mq/validation/observability/tetris + Image/Audio/Video/Mic) — fonte-única travada contra a fonte (`StdCatalogTest` 10/10; **drift do db corrigido 19/09** (`functions()` 3→5: `query`/`execute` moravam nas familias `isQuery/isExecute` fora do lock de `case`; a trava agora soma os literais das familias), `LspServerTest` 25/25; `48633d98`, `e79a3ea0`, `9e4d1728`); web/app-DSL + ui + ffi ficam de fora (R6 honesto); fatia 4 ✅ 18/09: ir-para-definição **cruza arquivos do projeto** (`crossFileDefinition`, walk ≤6 + primeiro hit, convenção única `LspSymbols`; `null` honesto) — `0a4497c7`, `LspServerTest` 27/27; fatia 5 ✅ 18/09: **referências também cruzam arquivos** (somente-leitura; varredura extraída p/ `LspProject` no split ≤600) — `f5df2362`, `LspServerTest` 28/28; fatia 6 ✅ 18/09: **`workspace/symbol`** indexa buffers + .kf irmãos (filtro/ordenação LSP) — `c04e16a4`, `LspServerTest` 29/29; fatia 7 ✅ 18/09: **hover de símbolos do projeto** (buffer+cross-file, linha completa; bug de framing byte-vs-char no teste-mate) — `848b7df1`, `LspServerTest` 30/30. **X10 CONCLUÍDA** (rename cross-file e assinaturas de membros = perguntas de superfície rule 6 no DOING) — **pós-X10 (LSP-A ✅ 19/09)**: rename cross-file + assinatura de hover (StdCatalog) aprovados ; **lock de hover ✅ 19/09 (LSP-A)**: `LspServerTest.hoverCoversSliceThreeNamespacesFromSingleSource` (36/36) trava hover+completion sobre os 31 namespaces pela fonte-única do catálogo — zero código novo de tooling; assinatura de hover segue regra 6 (metadado embutido nos 24 typers `Kof*` — registrado no 8.3) |

---

# Não-objetivos permanentes (VISION §12)

> Explícitos e permanentes: **não** são itens de trabalho e não devem ser abertos
> como gaps. Protegem a identidade da linguagem (a cerca anti-god-language).

Kof **não é**: uma god-language · um shell · o motor Arrow/Parquet/BLAS/CUDA ·
um framework de ML · um DBMS · um repositório de provedores de nuvem · um aligner
genômico · "Kali em Kof" · um notebook/IDE/kernel · ownership/borrowing ·
anotações/macros abertas/type-classes como fundação · paridade JS para domínios
pesados · um alvo por domínio · uma reimplementação do ecossistema científico.

---

# Invariantes R1–R12

| # | Invariante | Status | Prova / nota |
|---|-----------|--------|--------------|
| R1 | Travar a fronteira core/plataforma (ordem §3.4 como regra invariante) | ✅ 17/09 | `5f1422c6` — `scripts/check_stdlib_boundary.sh` + ledger (31 namespaces em `5f1422c6`; crescido para **35 registrados + 15 `excluded`** em 21/09) + CI + `--selftest`; invariante 1 do AGENTS |
| R2 | Generalizar "capability/link by use" para todos os pacotes/domínios | ✅ 20/09 | **fatia 1 (math)**: o x86 não liga mais `libm` incondicionalmente — o shim `call pow` do monolito virou FRACO (`.weak pow`, RuntimeMath) e `-lm` só entra quando a fonte realmente chama `kof_math_pow` (o único caminho ao shim, escaneado como `usesDb`/`usesMysql`). A matriz inteira agora é by-use, MEDIDA por face: x86 `LinkByUseTest` (readelf em ELF real: plain liga SÓ libc; sqlite/pthread/libm só quando usados; binário pow bate byte a byte com o oráculo JVM), cross scan `needsSqlite` + `ffiLinkArg` (#431), JS delegação ao host (require lazy), JVM class-loading lazy. Ternário morto no NativeAssembler (sqlite igual nos dois ramos) removido junto. |
| R3 | Formalizar FFI como first-class | 🟡 | **ABI escalar da JVM + `void` 18/09 (`.18`)**: `kof_ffi`/`kof_ffi_void` casam aridade arbitrária sobre {Int,Long,Float,Double,Boolean,String} entrada/saída, `String` lê `char*`, `void` é descartado como statement. `FfiE2ETest` cobre `pow`/`strstr`/`srand`/`atol→labs` (Long) + `FfiSignatureTest` trava o mapeamento escalar→layout completo. **Paridade JS FECHADA 18/09 (3.6 F1+F2+F3, `.18`)**: a mesma ABI escalar agora binda no target JS via bridge FFM no host `KofJsFfiBridge` (`extern`→`kofFfi`→`ProxyExecutable` `kof_platform.ffi`), provada byte-a-byte JVM↔JS (`FfiE2ETest` +7 `assertJvmJsParity`); o browser não tem host → degrade honesto em runtime (R7, como `kof.io`). Ver §R3-fatias para a decomposição completa. **Callbacks/upcalls (3.4) paridade JVM+JS FECHADA 18/09 (C1→C3.4)**: `extern` com parâmetro de tipo-função binda tanto na JVM quanto no host runner JS — um valor de função Kof entregue a C como ponteiro de função real (`Linker.upcallStub`), provado byte-a-byte JVM↔JS (`42/42/6.0/7.5` em ABIs Int/Long/Double/mistas; `5/104/2026` em ABIs com `String` como argumento — `char*`->`String` na fronteira do upcall); a ponte JS chama o método `invoke` do objeto `Lambda` compilado (um valor de função Kof é um objeto, não uma arrow nativa — descoberto na C3.2); síncrono/não-escapante; ABI do callback = primitivos + `String` como arg; **retorno** `String` segue não-bindável (`FFI001`/`FFI002`); o browser degrada honesto (R7). Restam: handles opacos (out-buffers **landaram 21/09** na JVM como 3.8b fatia 4; a metade `Handle` de **3.3 ✅ decidido 21/09 `D-R3-3.3`** é a frente RAII), variadics (**3.5 ✅ decidido 21/09 `D-R3-3.5` = nenhum**), ABI struct/array D6 (**3.8b fatias 1–4 + D6-5 landed 20–21/09** — record by-value in/out, `T[]`→`ptr` copy-in, `Buffer(U8)` out-buffer INOUT, tudo JVM), callbacks Native (sem mecanismo). **ABI escalar Native FECHADA 20/09 (#431 fatias 1–2, `6794ca21`+`cc12f4d0`, §369)**: `extern` com `library()` binda DIRETO em x86-64/riscv64/aarch64 (link-by-use + `call sym@PLT`, sem `dlopen` — o escape do §61 é a rota de produção; `FfiNativeE2ETest` 16/16 + `FfiNativeCrossE2ETest` 6/6 sob qemu); o bug nessa superfície, §370/#549 (`Double`/`Int` cru num slot `Float`/`Double` = bit-garbage silencioso no Native), CORRIGIDO 20/09 pelo `ExternArgumentCoercion` (`FfiExternTypeConversionTest` 11/11). Gaps honestos por target que restam (R7): assinaturas não-escalares + callbacks Native (`FFI001`) e não-escalar no JS (`FFI002`). — **D6-A ✅ 19/09**: struct/array = spec-first (ver 3.8) |
| R4 | Formalizar o codegen em compile-time (`CodegenStep`) | ✅ pousou 21/09 | `CodegenStep`/`CodegenStepPipeline` (aditivo; registro vazio = identidade, zero mudança de comportamento; `CodegenStepPipelineTest` 6/6); destrava `infra "prod" {}` (3.2, superfície ainda ⛔ regra 6); a **migração DDL/runner (2.2.3) avaliada 21/09 = descompasso de fase → LANDADO 21/09 opção B (`D-DESUGAR-STEP`): registry `DesugarStep` na fase de AST (`85779f20`, `DesugarStepPipelineTest` 7/7); o DDL permanece no lowering** |
| R5 | Tiers de estabilidade + pacotes oficiais | ✅ 21/09 (aplicado como gate de máquina) | tiers definidos em `backend-parity.md` §Stability tiers; **`D4-A` decidido** (todo namespace nasce `experimental`; promoção por-namespace com o DoD do R5) — **aplicado**: cada linha de `scripts/stdlib_boundary.txt` agora carrega seu tier, e `scripts/check_stdlib_boundary.sh` recusa tier ausente/inválido e `stable` sem pin no `STABLE_ALLOWLIST` (zero promoções; D4-A) — o selftest prova que morde; a aplicação era a parte pendente, não uma decisão |
| R6 | Manter o "nunca silencioso" para domínios novos | ✅ 17/09 | gate de máquina `DomainGapParityMatrixTest.everyPinnedGapIsDocumentedInTheParityMatrix` (`19a740f2`) + varredura completa do ledger (`c5897cd5`, achou §278) |
| R7 | Escopo honesto por alvo (JVM-first / Native systems / JS web) | ✅ | estratégia adotada; imposta pelos gaps documentados (`OBS003`, `GPU001`, `PROC001`, `SECN00x`, `MEDIA00x`) |
| R8 | Manter o tooling no MESMO frontend | ✅ | regra atual (LSP, `kof deps`, CLI consomem o frontend do compilador; sem parser paralelo) |
| R9 | Interop-first como padrão dos domínios | 🟡 | adotado; formalização de FFI pendente (R3) |
| R10 | Correto e determinístico por padrão (ciência) | 🔵 | aplica-se a partir dos Estágios 4/6 (property-based + golden) |
| R11 | Segurança: defesa primeiro | 🟡 | adotado (nunca cripto caseira; FFI para libs auditadas); PQC pendente no Estágio 5 |
| R12 | Não interromper o presente (meta-regra) | ✅ sobreposto 17/09 | `DECISIONS.md` §D-UNIVERSAL — sobrepõe o portão de *agendamento*, nunca o freeze/qualidade; segue default para os outros planos de `future/` |


## R3 fatias — decomposição do FFI até paridade total

Fatias incrementais da R3 rumo à "paridade total no FFI" (diretriz da
mantenedora 18/09). ⛔ = decisão de design da mantenedora (regra 6); 🔵 = ainda
em aberto; ✅ = landado.

| # | Fatia | Estado | Dono | Pré-requisito |
|---|-------|--------|------|---------------|
| 3.1 | JVM: ABI escalar geral — aridade arbitrária, {Int,Long,Float,Double,Boolean,String} entrada/saída, String lê de volta char* | ✅ 18/09 (.18) | dev .18 | — |
| 3.2 | JVM: retorno void (kof_ffi_void, descritor V; resultado descartado como statement) | ✅ 18/09 (.18) | .18 | — |
| 3.3 | JVM: handles opacos / out-buffers (void*, T*, Array<Byte> como buffer) — ponteiro opaco / buffer de bytes, NÃO o ABI struct completo do D6 | 🟡 decidido 21/09 | mantenedora | **`D-R3-3.3` = opção A**: `Handle` opaco nominal (não-aritmético) + `Buffer(U8, INOUT)` (== D6-3); **metade Buffer landou 21/09** (3.8b fatia 4, JVM; o namespace `kof.buffer` — `alloc`/`bytes()` — e o token INOUT `B` também pousaram no JS 21/09); `Handle` aguarda o mecanismo de recurso/tempo-de-vida gerido pela linguagem (`D-R3-HANDLE-LIFETIME`) — a frente RAII landou o `using` 28/09 (`docs/scoped-resources-plan.md`, CONCLUÍDO), mas o `Handle` em si segue honesto `FFI001`/`FFI002` até lá |
| 3.4 | JVM+JS: callbacks / upcalls (Linker.upcallStub) — função Kof entregue a C como ponteiro de função | ✅ **C1→C3.4 landados 18/09 (JVM+JS bindam callbacks primitivos E com argumento `String`, paridade byte-a-byte)** | .18 | semântica de closure + GC rooting (R12/1.2); só síncrono/não-escapante; ABI do callback = primitivos + `String` como arg (char*->String); **retorno** `String`/struct/pointer segue gated; a ponte JS chama o `invoke` do objeto `Lambda`; ver §R3-3.4 |
| 3.5 | JVM: variadics (printf, execlp) — como representar `...` numa assinatura Kof | ✅ decidido 21/09 | mantenedora | **`D-R3-3.5` = opção A: SEM variadics gerais** — caller passa `List`/`Array`/`Buffer`; fechado como gap documentado (R6/R7) |
| 3.6 | JS: paridade via bridge no host (o runner GraalJS/node É uma JVM com java.lang.foreign no host) — browser segue degrade honesto em runtime (R7: sem host `kof_platform.ffi`) | ✅ 18/09 (.18) | .18 | ABI 3.1/3.2 |
| 3.6.F1 | Bridge FFI no host `KofJsFfiBridge` + `KofJsFfiBridgeTest` (8/8) — mesmo downcall do `kof_ffi`, provado no host; gate do compilador FECHADO (zero risco ao backend) | ✅ 18/09 (.18) | .18 | — |
| 3.6.F2 | Roteamento JS no compilador: ramo JS no `isExternBound` + baixar `extern`→`kofFfi`/`kofFfiVoid`→`kof_platform.ffi` (rotear em `JsRuntimeOps` + helper em `JsRuntimeIo` + `ProxyExecutable` no `KofJsRunner`) — abre o gate escalar do JS | ✅ 18/09 (.18) | .18 | F1 |
| 3.6.F3 | Paridade E2E byte-a-byte JVM↔JS — `FfiE2ETest` +7 `assertJvmJsParity` (abs/atoi/sqrt/pow/atol→labs Long/strstr/srand void): mesmo `.kf`, saída idêntica nos dois alvos | ✅ 18/09 (.18) | .18 | F2 |
| 3.7 | Native: ABI escalar direta (`call sym@PLT`, link-by-use — substitui dlopen/dlsym; §61 fechado) | ✅ 20/09 (#431 fatias 1–2, §369) | lane nat | §61 |
| 3.8 | ABI struct/array completo (D6) | 🟡 | lane compilador (pós-spec) | **D6-A ✅ 19/09**: spec **rascunho escrita 19/09** (`ffi-abi-structs.md`+PT: ABIs medidas, 3 exemplos-resolução como golden, D6-1..D6-5 = decisões da mantenedora antes de 3.8, verruga `Arena.global` §1 catalogada). **D6 DECIDIDO 20/09** (`DECISIONS.md §D-FFI-STRUCT`, **D6-1 = A+B**; D6-5 = arena confinada). **D6-1 B APROVADA spec-first 21/09 (`D-FFI-STRUCT-B`)**. **3.8a ✅ 20/09 (fatia sem decisão)**: `AbiLayout.java` + `AbiLayoutTest` — engine puro de layout/classificação (size/align/offsets + classes por ABI), golden MEDIDO com GCC 13.3 nos 3 alvos e reprovado ao vivo por `_Static_assert` contra gcc/aarch64-linux-gnu-gcc/riscv64-linux-gnu-gcc; corrigiu a prosa riscv do rascunho (LP64D faz *flatten* de structs ≤2 campos — `Time(Long,Double)`→`a0`+`fa0`). **3.8b ✅ 20–21/09 (JVM)**: fatia 1 `record` por valor como struct C **argumento** (`e79ea4e6`), fatia 2 `record` **retorno** por valor (registrador + sret, reconstrução pelo construtor canônico; `20aa73d7`), fatia 3 **`T[]` escalar→`ptr`** com **copy-in por chamada** (token `p<elem>`; `7c6413d4`), + fix D6-5 (arena confinada por chamada nos helpers escalares), + **fatia 4 out-buffer D6-3 `Buffer(U8)` INOUT** (`buffer.alloc`/`Buffer.bytes()` + `extern` copy-in/chamada/copy-back, token `B`; `6f0a7e8d`+`debd39ca`). Provas: `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, `BufferE2ETest` 4/4, `BufferFfiE2ETest` 4/4, `FfiE2ETest` 17/17, bateria FFI 100/0F/0E. **Restam:** só o Native (3.7: struct/array/sret) — cross-lane; o FFI do JS fechou 21/09 (param: struct `structParamByValueJsParity`, array `arrayParamByValueJsParity`, buffer `bufferInoutCopyInCopyBackJsParity`; **retorno de struct** `structReturnByValueJsParity` via `__kof_ffi_from`). |
| 3.9 | Meta-paridade: mesma fonte extern com o mesmo comportamento em todo alvo CAPAZ (R7 honest-scope nos incapazes) | meta | — | 3.1–3.8 |

3.1+3.2 landados 18/09 → a JVM tem a ABI escalar completa + void, **prova
reforçada** (`Int`/`Long`/`Double`/`String`/`void` e2e contra libc/libm incl. `Long`
via `atol`→`labs`; conjunto inteiro travado por mapeamento em `FfiSignatureTest`).
**3.6 (paridade JS) FECHADA 18/09** em F1 (bridge no host, gate fechado) → F2
(roteamento JS no compilador abre o gate escalar) → F3 (paridade E2E byte-a-byte
JVM↔JS, +7). No target JS a **ABI escalar agora binda no host runner GraalJS/node**
(FFM no host, sem bytecode no guest); o browser não tem host `kof_platform.ffi` e
lança erro honesto em runtime (R7, mesmo degrade do `kof.io`); assinaturas não-
escalares (array/struct/pointer) seguem `FFI002` em compilação (3.3/3.5 **decididos 21/09**; 3.8 em curso — D6 decidido, 3.8b fatias 1–4 + D6-5 landed 20–21/09; restam bridge JS/sret Native).
**Callback/upcall (3.4): TOTALMENTE LANDADO
18/09 (C1→C3.4) — a JVM *e* o host runner JS agora bindam callbacks primitivos **E com
argumento `String`****
(`extern` com parâmetro de tipo-função → `Linker.upcallStub` sobre o valor de função
Kof; um `.kf` real computa `42/42/6.0/7.5` em ABIs Int/Long/Double/mistas e `5/104/2026`
em args `String`, byte-a-byte JVM↔JS em `JvmFfiCallbackE2ETest`; a ponte JS chama o método `invoke` do objeto `Lambda`
compilado, lendo o `char*` de um arg callback a `String` Kof na fronteira — design completo + a descoberta objeto-vs-arrow da C3.2 em §R3-3.4 abaixo).
De resto, o próximo trabalho da R3 é **implementar o 3.3/3.5 decididos** (`D-R3-3.3`/`D-R3-3.5`, 21/09) mais a 3.8 em curso (D6 decidido 20/09; 3.8b fatias 1–4 + D6-5 + struct param no JS landed 20–21/09; restam retorno de struct/array/buffer no JS + sret Native); a ABI escalar Native (3.7) landou 20/09 e o §370/#549 (conversão de argumento de extern) fechou no mesmo dia.

### §R3-3.4 — callbacks / upcalls (função Kof entregue a C)

**Objetivo.** `extern` aceita um valor de função Kof como parâmetro *callback*: C
recebe um ponteiro de função real que, ao ser invocado, roda o closure Kof e devolve
seu resultado — o espelho de **upcall** FFM do downcall da 3.1.

**Superfície.** Um parâmetro `extern` de tipo-função, ex.
`extern "lib.so" each(Int n, (Int, Int) -> Int cb): Int`; o closure é baixado no
`Object[]` de args como o valor de função Kof (um `FunctionValue` implementando uma
interface sintética especializada, ex. `int invoke(int,int)` — medido). **Os parâmetros
do callback são o conjunto bindável {Int, Long, Float, Double, Boolean} mais `String`
(fatia 3.4-C3.4)** — um parâmetro `String` do callback chega como um `char*` do C que o
runtime lê num `String` Kof (o espelho do upcall para o `getString` do downcall); o
retorno do callback é **só** primitivo-ou-void: devolver `String` entregaria ao C um
`char*` cujo dono da memória não é observável sob o contrato síncrono, então um **retorno**
`String` segue `FFI001`/`FFI002` honesto (nunca stub silencioso, R6). Os carriers
primitivos já vêm unboxed da interface especializada, sem adaptador de boxing.

**Codificação da assinatura.** `FfiSignature.signature` codifica um parâmetro de
callback como um **token de parêntese aninhado `(<retchar><paramchars>)`** (assim fica
1:1 com o argumento — ex. `each(Int n, (Int,Int)->Int cb): Int` → `ii(iii)`: retorno
`i`, parâmetro `i`, token de callback `(iii)`; um arg `String` carrega no mesmo token —
`f(Int n, (String)->Int cb): Int` → `i(iS)`); layout nativo = `ADDRESS` (ponteiro de
função). O `kof_ffi` parseia com cursor (um `(` consome seu descritor aninhado até o
`)` correspondente).

**Runtime (`kof_ffi` gerado).** Num arg `(` (callback), o objeto de função Kof recebido
vira um stub achando o `invoke` por reflexão (por nome + aridade do callback),
unreflectindo-o e fixando o tipo de carrier:
`Linker.upcallStub(lookup.unreflect(invoke).bindTo(closure).asType(tipoCarrierUnboxed),
innerFnDesc, arena)` → um `MemorySegment` usado como arg `ADDRESS` no spreader. Como a
interface do Kof é **especializada** (`int invoke(int,int)`), o `.asType(...)` é no-op —
os carriers já batem com os `ValueLayout`s do FFM; o `.asType` fica como a ponte geral
de boxing/unboxing (**medido** na C1, onde um closure apagado
`Object invoke(Object,Object)->Object` bridged do mesmo jeito devolveu `42` através de
um upcall C real). **Um arg `String` do callback (3.4-C3.4)**: o `char*` que o lado C passa
tem carrier nativo `ADDRESS`, então o tipo de método do stub recebe um `MemorySegment`
naquela posição; `MethodHandles.filterArguments` insere um `kof_ffi_cstr`
(`reinterpret(MAX).getString(0)`, NULL→null) que o vira `String` **antes** do `invoke` Kof
rodar, de modo que o closure vê um string Kof real (conteúdo incluído — provado por
`atol`-dentro-do-callback). Rooting: o stub é alocado no `Arena.ofConfined()` da chamada (≈ a
arena confined que o `kof_ffi` já usa) e fica vivo exatamente enquanto o C o segura.
**Espelho JS (`KofJsRunner`)**: o parse por cursor mora no `KofJsFfiBridge.call` (um slot
`(` → `ADDRESS`, o stub pré-montado passa direto), e `jsCallbackStub` monta o `upcallStub`
cuja ponte é um static de aridade fixa `executeJsX` alcançado via
`MethodHandles.asVarargsCollector` (NÃO `asSpreader`, que o JDK rejeita num handle varargs)
chamando `fn.getMember("invoke").execute(...)` — porque no JS o valor de função é um objeto
`Lambda`, não chamável; um arg `String` do callback chega como o carrier `MemorySegment` e
o `executeJsX` o lê via o mesmo `getString` (→ host String → JS string) antes da chamada;
a arena confined do stub é aberta pelo `ProxyExecutable` e fechada
após o downcall síncrono retornar.

**Restrição honesta (escopo da fatia, R6/R7).** **O contrato do callback `extern` é
síncrono, não-escapante** — o stub vive exatamente pela duração da chamada (arena
`confined`), então é válido enquanto o C chama de volta *antes de retornar* (comparadores
estilo `qsort`, `each`, `foreach`). Isso espelha a própria regra do C de não liberar um
callback que o chamador ainda segura: passar um callback Kof a uma API que o **guarda**
após o retorno (`atexit`, `signal`, async) é **fora do contrato** e seria use-after-free.
Como o compilador não observa a retenção do C, isto é um **contrato documentado**, não um
stub silencioso; um **binding explícito de callback persistente** (uma raiz GC real,
R12) é uma fatia futura separada. O que o gate PEGA em **compilação** (R6,
`FFI001`/`FFI002` honestos) são ABIs não-bindáveis: um arg struct/pointer, um **retorno**
`String` (um **arg** `String` do callback binda desde 3.4-C3.4), ou callback-como-retorno.

**Postura por target.** **JVM**: binda (upcall FFM no host, igual ao downcall; um arg
`String` cruza como `MemorySegment` lido a `String` Kof pelo `kof_ffi_cstr`).
**JS**: **binda (C3.2/C3.3 landados 18/09; arg `String` na 3.4-C3.4)** — o runner GraalJS/node é uma JVM, então o
`ProxyExecutable` do `KofJsRunner` ganha o mesmo caminho de upcall; um arg de callback é
marshalled montando um `Linker.upcallStub` sobre o valor de função Kof. **Descoberta
(C3.2)**: um valor de função Kof compilado NÃO é uma arrow JS nativa — é um **objeto**
`Lambda…` com um método `invoke`, então a ponte do stub chama `fn.getMember("invoke").execute(...)`
(o pin C3.1 exercitava um `Value.execute` numa arrow nativa e foi corrigido para o enquadramento
objeto-`invoke`); os carriers primitivos passam por `asInt`/`asLong`/`asFloat`/`asDouble`/
`asBoolean` do Graal; um arg `String` chega como `MemorySegment` e o `executeJs*` o lê via
`getString` antes do `Value.execute` (3.4-C3.4). O cenário reentrante (JS → `ProxyExecutable` no host → downcall nativo →
`Linker.upcallStub` → de volta ao `Value.invoke`) roda na mesma thread; provado byte-a-byte
JVM↔JS (`jvmAndJsCallbacksMatchByteForByte`: `42/42/6.0/7.5`; `stringCallbackArgsBindAndMatchJvmJs`: `5/104/2026`). O browser não tem host → degrade
honesto em runtime (R7); um callback não-bindável (ex. **retorno** `String`) ainda falha em
compilação no JS (`FFI002`). **Native**: §61 (3.7).

**Fatias (espelham a disciplina F1→F3 da 3.6).** **C1** = pin do mecanismo no nível do
host (`JvmFfiCallbackTest`: upcallStub + ponte de closure `.asType` + rooting por
`Arena` + spreader `ADDRESS`, contra um `.so` temp compilado por gcc), gate do
compilador **fechado**, zero risco ao backend. **C2** = token de callback em
`FfiSignature` + parse por cursor no `JvmFfiRuntime.kof_ffi` + ponte
`Linker.upcallStub`/`.asType` + ramo JVM do `isExternBound` → abre o gate de callback
da JVM, provado por `JvmFfiCallbackE2ETest` (um `.kf` real computa `42/42/6.0/7.5` em
ABIs Int/Long/Double/mistas; callback no JS segue `FFI002`; um callback com **retorno**
`String` segue `FFI001`). **C3** = paridade JS de callback, dividida como F1→F3:
**C3.1** = pin de reentrância no host (`KofJsFfiCallbackBridgeTest` — JS→nat→upcall→
callback reentrante, Int `42`/Long `42L`/loop `46`; gate ainda fechado) ✅;
**C3.2** = caminho de upcall no `KofJsFfiBridge` + marshalling do `Value` de callback no
`ProxyExecutable` do `KofJsRunner` + abrir o ramo JS do `isExternBound` para callbacks
bindáveis ✅; **C3.3** = E2E de paridade byte-a-byte JVM↔JS de callback + degrade honesto
do browser (R7) ✅; **C3.4** = `String` como **argumento** do callback (o `char*` que o C
passa é lido num `String` Kof na fronteira do upcall — JVM `filterArguments`+`kof_ffi_cstr`,
JS `executeJs*` `getString`; **retorno** `String` segue gated) ✅ 18/09.
Status: **C1→C3.4 todos landados 18/09** — a JVM e o host runner JS bindam callbacks
primitivos **E com argumento `String`** (paridade byte-a-byte: `42/42/6.0/7.5` escalares,
`5/104/2026` args `String`); o enquadramento arrow/`Value.execute` do pin C3.1 foi
corrigido para a convenção real de objeto-`invoke` do `Lambda` na C3.2. As únicas formas
de callback não-bindáveis que restam são um **retorno** `String`/struct/pointer ou
callback-como-retorno aninhado — `FFI001`/`FFI002` honestos.

---

# Decisões — D1–D7 resolvidas 19/09 (D-POLL-19) + D8–D12 resolvidas 21/09 (ver `DECISIONS.md`)

| # | Decisão | Estado | Destrava |
|---|---|---|---|
| D1 | Sign-off de re-baseline do auto-collect do GC x86 (§260 G-6(a)) | 1.2.2/1.2.3, Estágio 6 | `DECIDIDO (A) 19/09` |
| D2 | Registry de pacotes MVP — escopo/hospedagem | 1.5.3, Estágio 3+, 8.2 | `DECIDIDO (A) 19/09` |
| D3 | Agendamento do bare-metal/bootável | 1.7 | `DECIDIDO (A) 19/09` |
| D4 | Tiers de estabilidade por-namespace do R5 (quais são `stable` vs `experimental`) | R5, Estágio 7 (pacote oficial `kof-bio`) | `DECIDIDO (A) 19/09` |
| D5 | Sintaxe `using` de scoped resources (barrada por bump) | 6.5 | `DECIDIDO (B) 19/09` |
| D6 | Design do ABI de struct/array do R3 (nível de assinatura) | 3.5, 4.2, 5.4, 6.1 | `DECIDIDO (A) 19/09` |
| D7 | Value records / tipos-valor first-class (fila §2.7 do `roadmap.md` §23) | TIER 2.7 — planejado, precisa de autorização para abrir | `DECIDIDO (A) 19/09 — front ABERTA` |
| D8 | R3-3.3 handles/out-buffers da FFI | 3.3, Estágios 4–7 | `DECIDIDO (A) 21/09 — Handle + Buffer(U8,INOUT)` |
| D9 | R3-3.5 variadics da FFI | 3.5 | `DECIDIDO (A) 21/09 — sem variadics gerais (gap documentado)` |
| D10 | X5 variance + sealed types | núcleo do type-system | `ABERTO (C) 21/09 — spec-first, plano exigido` |
| D11 | X6 reflexão de interop | fronteira de interop | `ABERTO 21/09 — spec-first, plano incremental exigido` |
| D12 | Hook `CodegenStep` (R4) | Estágio 3, 3.2 | `DECIDIDO (A) 21/09 — hook interno (sem sintaxe de usuário)` |

---

# Caminho crítico

`Estágio 1 (SYSTEMS) fecha` → Estágios 2/3 → Estágios 4/5 → Estágio 6 →
Estágio 7 → Estágio 8.

Transversal: **R3 (FFI formalizada)** é a espinha dorsal dos Estágios 3–7 e
**R4 (hook de codegen)** — **✅ pousou 21/09 (`CodegenStep`/`CodegenStepPipeline`, registro vazio = identidade)** — barra o Estágio 3 (`infra`). Dentro do Estágio 1, os
itens restantes sem decisão são os gaps de paridade das lanes web/native
(1.1.3–1.1.10) e o redesign de escalonamento JS do §132 (1.3.2, `.18`) — **FECHADO 18/09** (`06d8b322`); o Stage 1 agora só aguarda os gaps de paridade web/native + as decisões da mantenedora D1–D3.

Veja o companion [`UNIVERSAL-PLATFORM-VISION.pt_BR.md`](../architecture/UNIVERSAL-PLATFORM-VISION.pt_BR.md)
para o *porquê* por trás de cada item acima.

---

# Fases futuras (apenas plano)

As **Fases 4–7** (DATA, SECURITY expansão, SCIENTIFIC COMPUTING, BIOINFORMATICS) são **apenas plano, sem código** e estão documentadas em [`development/future/IMPLEMENTATION-UNIVERSAL-PLATFORM-future.pt_BR.md`](../future/IMPLEMENTATION-UNIVERSAL-PLATFORM-future.pt_BR.md). Pela regra dos três estados (AGENTS.md), elas pertencem a `development/future/` e não são trabalho de desenvolvimento atual.
