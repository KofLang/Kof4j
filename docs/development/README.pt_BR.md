[English](README.md) | [Português](README.pt_BR.md)

# Development — backlog vivo (só trabalho em desenvolvimento)

> **Base:** `0.5.0-beta` · branch `beta-0.5.0` · **atualizado:** 23/09/2026
> **Suíte medida neste HEAD:** `3225` run (2762 kof-compiler + 50 kof-script
> + 7 kof-c-compiler + 406 kof-cli), **0 falhas / 0 erros**, 221 skip (cross
> roda no job dedicado com qemu; o resto são guardas de toolchain/DB externo +
> sysroot §255) — job CI Build+Tests do tip `404d8be6` em 20/09 ~18:14: o
> **primeiro verde na `beta-0.5.0`**, reator `Kof 0.5.0-beta`. O flake §252, o
> residual cross §181 e o §256(b) seguem fechados no código (`20495e48` /
> `c56c74a7` / `3a593734`). **Nº autoritativo da suíte = o job CI no SHA
> pushado** (o gate `mvn test ... -Dmaven.test.failure.ignore=true`; conferir
> por módulo com `grep -rl FAILURE */target/surefire-reports/*.txt`), não esta
> linha — ela apodrece a cada commit. Refold da concatenação
> `NativeRiscvAsm` para `<clinit>` (novo anti-pattern
> `constant-folded-runtime-asm.md`) verde no gate `gate1585.log` (HEAD 54da1325).
> **Regra dos 3 estados (`AGENTS.md`):** `docs/` = implementado/decidido ·
> `development/` = **trabalho técnico pendente** · `development/future/` =
> **só plano, zero código**. Concluído → move para um submódulo de `docs/` no
> mesmo commit; iniciado → cai aqui. A varredura de 12/09 (`655afa6b`) moveu 13
> docs de `future/` para cá (todos com código) e 4 concluídos para `docs/`.
> **Refactor de clareza 13/09 (mantenedora):** bugs/gaps/matrizes →
> `docs/bugs-and-gaps/` (linhas 2, 41, §2, §3, §4.2, §5); planos **parados por
> decisão** foram **ratificados 13/09 e consolidados em `DECISIONS.md`** (a
> pasta `decision-pending/` foi extinta — ver §3). Este README lista o que
> **anda**; uma decisão tomada vive em `DECISIONS.md` (regra 6: uma frente sem
> linha ali não é atacada).

**`parity/` (24/09, `D-FULL-PARITY-050`):** o ledger impeditivo de paridade
total (`PARITY-GAPS.pt_BR.md`(+EN)) — cada linha de paridade parcial medida
(superfície × alvo × código de gap × lane dona). Condição 8 da release
(`full_parity`): a 0.5.0 NÃO corta com linha aberta. A regra ABSOLUTA de
qualquer plano: paridade total.

**Fontes de verdade que NÃO estão aqui (não são backlog):** `docs/status.md`
(o que funciona + gate da suíte), `docs/backend-parity.md` (matriz de
paridade com gaps honestos), `docs/bugs-and-gaps/specification-gaps.md`
(SG-001–023 — fila do maintainer COMPLETA, virou referência; SG-021/022 =
pedidos sem decisão; **SG-023 ✅ DECIDIDO 21/09 — `D-PROPERTY`, sem superfície
nova**).

---

## 0. O que está vivo aqui (leia primeiro)

- **Pendentes (condição 3 do gate de release):** nenhum — os planos em voo com
  dono ainda soltos (`db-parity-plan`,
  `PLAN-BAREMETAL-BOOT` e, desde 26/09, `interop-engine-plan` — X2, sob a lane
  compilador pelo item 2 da `D-COMPLETE-FIRST`, mesmo tratamento de
  `memory-safety-plan`) estão no **allowlist** por
  `D-RELEASE-0.5.0-SCOPE` (mantenedora 21/09/2026) + `D-BAREMETAL-BOOT`
  (mantenedora 22/09/2026) + o registro de posse da `D-COMPLETE-FIRST`:
  mantêm dono + fila na §1 e concluem nas próprias frentes; não barram o
  corte 0.5.0.
  `IMPLEMENTATION-UNIVERSAL-PLATFORM`, `makealive-plan` e `secrets-plan`
  concluíram e foram movidos para `docs/architecture/` (21/09); o plano de
  tipos (X5+X6) concluiu e foi movido para `docs/` (22/09); `kof-c-cross`
  (C1–C4 + C3-residual) concluiu e foi movido para `docs/` (23/09).
  Autoridade: `scripts/check_release_050_gate.sh` (`loose_docs`).
- **Registros vivos aqui (não são backlog):** `DECISIONS.md`, `roadmap.md`,
  `release-beta-0.5.0-prep.md`.
  24/09: os dois PROPOSALs ratificados saíram de development/ — exit-gate →
  `docs/`, registro de versioning → `docs/distribution/` (a regra operante é o
  `VERSIONING.md`); o ledger `tech-debt` + a ferramenta `debt-scout` foram
  MORTOS pela mantenedora (dívida medida zerada).
- **§1 é a fila; §4.1/§4.2 são TRILHA DE AUDITORIA** (o que já saiu, com
  prova) — não leia como trabalho. Como agir: §6.

---

## 1. Ordem de execução dos planos (fila oficial da lane development)

> Critério: (1) frente designada pela mantenedora > (2) saúde do gate >
> (3) trabalho de código-puro sem decisão > (4) itens bloqueados = NÃO atacar
> (regra 6). Itens de registro vivo (matrizes/auditorias) não têm "fim" —
> atualizam-se a cada gap fechado, não puxam prioridade.

| # | Plano | Estado | Por que nesta posição | Próximo passo concreto |
|---|---|---|---|---|
| 1 | `stdlib/PLAN-TREE-SHAKING.md` (#97) | ✅ **CONCLUÍDO 13/09** — S-1..S-6.1 ✅ (S-6.1 mergeado `0104f6d6` PR #106) + S-7 ✅ (consolidado em `docs/stdlib/stdlib-loading.md`, movido p/ `docs/stdlib/`) | frente designada 11/09, fechada; S-5-x86 segue fila bugfix (`root_end`, fora deste plano) |
| 2 | ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **FEITO 13/09 — F3 fechada** (NativeBackend **498** ≤500 medido: `NativeSymbolMangling` 92 + `NativeStaticData` 116 + `emitMethodTable`→NativeClassMeta; o bloqueio "lane GC em `nat/`" caducou — refs não existem mais no repo, regra do dono-morto) — **PLANO FECHADO e MOVIDO 13/09** (F1–F9 todas ✅; regra dos 3 estados) | gate ≤500 virou **ratchet travado no CI** (2652aa45, §140): dívida não cresce e só encolhe; a contagem autoritativa é `wc -l scripts/check_500-baseline.txt` (atualize APONTANDO p/ o arquivo, não gravando nº que apodrece a cada split) | — (doc em `docs/architecture/`; se resíduo >500 novo aparecer, reabre como item próprio) |
| 3 | ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ **CONCLUÍDO + PROMOVIDO 19/09** — §5 passo-8: faces (1)–(5) todas fechadas (GC G-0..G-6(a); DB001+CONC001 cross; FLT001; §107 record/aninhado nas 3 arcos; colunas por-arch; CI cross) — NATIVE002 FECHADO; recusas restantes por domínio (SECN000/OTP001/JSN004/RNG001/UI) são códigos de gap honestos no `known-bugs.md` + `backend-parity.md`, não trabalho pendente deste doc | movido p/ `docs/` (regra de 3 estados) | — |
| 4 | ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` (#83) | ✅ **CONCLUÍDO 19/09** — 1ª fatia ✅ 11/09 (núcleo+`restartLimit`+`stop`) + **S2-JVM ✅ 13/09** + **S2-Native x86 ✅ 15/09** (§129 fechado — chain TLS por thread) + **S2-JS ✅ 18/09** (§132 fechado; `OTP002` levantado) + **riscv64/aarch64 ✅ 19/09** (§129 port cross — tabela de cadeia por-TID `kof_exc_slots`; gate `OTP001` removido; `crossGateOtp001` roda o APP nas 2 arches) | movido p/ `docs/` (regra dos 3 estados) | — (DD-OTP RATIFICADAS 13/09) |
| 5 | ~~`plan-editor-integration.md`~~ → `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | ✅ **CONCLUÍDO 14/09** — degraus 0–13 implementados e provados (`EditorIntegrationTest` 23/23; `kof editor` completo nos 7 editores; release gate §19 verde) | movido para `docs/tooling/` (regra dos 3 estados) | — |
| 6 | ~~`plan-stdlib-expansion.md`~~ → `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ **CONCLUÍDO 14/09** — S0–S13 implementados e validados nos 5 alvos; pendências de decisão consolidadas em `DECISIONS.md` §D-STDLIB | movido para `docs/stdlib/` (regra dos 3 estados) | — |
| 7 | fila recém-aberta de `DECISIONS.md` (13/09): ~~`time.todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds` (D-STDLIB)~~ **✅ EXECUTADA 13/09** (S7e-S7h, matriz stdtime3-6, suíte 1772/0/0; TIME003 = fila geral) · ~~`CmdNew` (D-APP I1)~~ **✅ FEITO 14/09** (`kof new --type mono\|backend\|frontend\|full-stack`, esqueletos compiláveis, APP003 honesto, `CmdNewTest` 8/8, matriz APP em `backend-parity.md`) · ~~`chacha20Encrypt/Decrypt` (D-SEC)~~ **✅ FEITO 14/09** · ~~`security.cookies`~~ **✅ FEITO 14/09** · ~~`app.security()` (C18)~~ **✅ FEITO 14/09** (middleware composto, ordem fixa, JVM; `KofWebE2ETest` 22/22 + `appSecurityPipelineE2E`; Native/JS `WEB006`; superconjunto unificado .18×.22) · ~~`--fat` (D-APP I3)~~ **✅ FEITO 14/09** (`kof build --fat` → `kof-app.jar` executável com classes+runtime+deps; `CmdBuildFatTest` 4/4, prova `java -jar`; não-JVM recusa honesto R6) · ~~blog E2E (D-SPRING F12)~~ **✅ FEITO 14/09** (`KofBlogE2ETest` verde; expôs+corrigiu 2 bugs JVM: `readRequest` contava body em chars vs `Content-Length` em bytes — travava conexão UTF-8 multibyte; CLOB cru do JDBC no read path) | `RATIFICADO` (decisão travada 13/09) | — | ~~OAuth resource-server (D-SEC camada 16)~~ **✅ FEITO 14/09** (`auth.resourceServer(jwksUrl,issuer,aud)` + `resourceServerVerify`; RS/ES via JWKS, sem confusão de algoritmo; integra com `auth.authenticated`/`app.security`; `KofOAuthResourceServerTest` 4/4; Native/JS `SECN007`) — **FILA §7 VAZIA**; cada linha = unidade-teste-commit |
| 8 | `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (+ companion de visão `docs/architecture/UNIVERSAL-PLATFORM-VISION.pt_BR.md`) | `EM CURSO` — **promovido de `future/` 17/09** (`DECISIONS.md` §D-UNIVERSAL, R12 sobreposto); dividido 17/09 em passos executáveis + companion de visão | diretriz da mantenedora 17/09: promover e implementar | **Estágios 1–8 + R1–R12 como itens executáveis** (status ✅/🟡/🔵/⛔ + lane dona + prova) — estado vivo: **R1 ✅ FEITO** (`5f1422c6` gate+ledger+CI da fronteira); **R6 ✅ gate de máquina** (`DomainGapCodesTest…` `19a740f2` + varredura do ledger `c5897cd5`); **R5 ✅ gate de máquina 21/09** (tier em `scripts/stdlib_boundary.txt` + `check_stdlib_boundary.sh`, D4-A); **X8 ✅ 21/09** (idioma property `test`+`rng`+`assert`, `D-PROPERTY`); **1.5 ✅ export OTel landado** (`435b7013`; Native `OBS003`); 1.1 MEDIA = `MEDIA001/003` documentados, na fila atrás das facades HTTP da `.22`; 1.2 GC x86 = ✅ G-6(a) auto-collect landado 19/09 (`a904317e`, §260 FECHADO, D1-A); 1.4 registry = **✅ MVP 19/09** (D2-A: publish + pull 1.5.3-S2). Reivindicar em `DOING.md` antes do código |
| 9 | ~~`workflow-plan.md`~~ + ~~`shell-plan.md`~~ (+PT) → `docs/workflow-plan.md` / `docs/shell-plan.md` | ✅ **CONCLUÍDOS 19/09** — workflow: as cinco faces landaram (`WorkflowE2ETest` 20/20, paridade byte JVM==JS, Native real); shell: 2.2.0–2.2.4 landados (`ShellE2ETest` 15/15; único residual = `pipeline` JS com pipes vivos, item de plataforma na linha 2.2 do tracker, não fatia do plano) | movidos para `docs/` (regra dos 3 estados — plano concluído não pode ficar em `development/`) | — |
| 10 | `D-WORKFLOW-RUN` (Stage 2 linhas 2.5/2.6) — runner completo `kof workflow run` + exemplo de pipeline de CI/CD | ✅ **ATERROU 19/09** (dono lane plataforma, sessão 19/09-3/9093): convenção `pipeline(): KofWfDag`; `list`/`run --job`/`--dry-run`/`--json`; host `order()`/`runJob()` + `CmdWorkflow`; `examples/ci/ci-pipeline.kf` golden E2E (`CmdWorkflowTest` 9/9) | decisão travada em `DECISIONS.md` §D-WORKFLOW-RUN; implementado direto (fatias de tooling, precedente X9 `kof deploy`) | residual: faces JS/Native do runner são fatias seguintes honestas (R7) |
| 11 | ~~`makealive-plan.md` (+PT)~~ → `docs/architecture/makealive-plan.md` — D-MAKEALIVE: infraestrutura como código tipado — **todas as linhas 3.1–3.8 POUSADAS** (core MK-1 20/09; 3.3 reconcile; 3.2 `966c86a4` `D-MAKEALIVE-SYNTAX`; 3.7 fechada runtime-only; 3.8 `D-MAKEALIVE-CLI`; **3.6 secrets pousou 21/09 `32285136`**) | ✅ **CONCLUÍDO + MOVIDO 21/09** | — (regra dos três estados) |
| 12 | ~~`secrets-plan.md` (+PT)~~ → `docs/architecture/secrets-plan.md` — `D-SECRETS`, Estágio 5/tracker 3.6 | ✅ **CONCLUÍDO + MOVIDO 21/09** — todas as faces POUSADAS `04473bbe` (`Secret` `32285136`; resto-P1 `fromBytes`/`hashCode` de identidade; P2 redação runtime+lint; P3 `KeyHandle`/`rotate` `SECN010`); `SecretE2ETest` 7/7 + `KeyHandleE2ETest` 5/5 | — (regra dos três estados) |
| — | ~~`ffi-abi-structs.md` (+PT)~~ → `docs/ffi-abi-structs.md` — ABI de struct/array da FFI (D6) | ✅ **CONCLUÍDO + MOVIDO 23/09** — todas as fatias pousadas (3.8a `AbiLayout`; 3.8b JVM param+retorno+array+buffer; JS param+retorno+array+buffer; 3.7 x86-64 param+retorno+sret+copy-in `T[]` + return+param de struct INTEGER no cross); prova bateria FFI **60/60 verde 23/09** | movido para `docs/` (regra dos três estados) | — |
| — | `memory-safety-investigation.md` (+PT) — entregável da Fase 0 | `ENTREGUE 25/09 — aguardando revisão da mantenedora` — varredura de 14 pontos com evidência file:line (repr de variável, cópia/compartilhamento, zero escape analysis, val=só-ligação, captura-por-valor+box-na-mutação, raízes do GC conservador, pilhas-de-worker-nunca-raízes, regras de cópia da FFI, divergência de duas representações da nullability, aliasing não rastreado, recursos só-close-explícito, spawn compartilha refs), família de bugs de memória §503/§260/§292/§252 mapeada, 8 pontos frágeis, respostas preliminares às perguntas centrais da frente | lane paridade (gate da Fase 0 = revisão da mantenedora) | Fase 1: `docs/spec/memory-safety.md` |
| — | `interop-engine-plan.md` (+PT) — `D-COMPLETE-FIRST` item 2, 26/09 | `EM DESENVOLVIMENTO` — X2 motor Python/R como PACOTE COMPLETO sobre o HOST_IMPORT `kof.interop` JÁ EXISTENTE (zero namespaces novos; ledger de fronteira inalterado); motor escrito em Kof (`interop-py-host.kf`) sobre `kof.process`+`kof.json`; diagnósticos nomeados a partir de `INTEROP004` (001–003 medidos tomados); regra 11: superfície do usuário = intenção (`py.call`), nunca mecanismo | **lane compiler 9092** (claim 26/09) · fatia 1 = motor Python no JVM — RECON: medir primeiro a superfície de escrita no stdin do `kof.process`; E2E com guarda python3 (presente no host; `Rscript` ausente → fatia 2 `assumeTrue`) | fatias 1–5 pela tabela do plano; fechamento = nota LANDED nos DECISIONS + linha 2.8.6 vira ✅ + doc vai para `docs/` |
| — | `memory-safety-plan.md` (+PT) — `D-MEMORY-SAFETY` 25/09 | `EM DESENVOLVIMENTO` — mantenedora 25/09: frente de memory safety (ownership/lifetime/borrowing/aliasing/FFI) **aberta e sob a lane paridade**; Fases 0–1 (investigação + spec) são trabalho corrente, edições no compilador/core esperam a fila atual (restrição do brief); Kof-first: nada de borrow checker copiado, null safety intocável, rule 11 Lei da Simplicidade | **lane paridade** (D-MEMORY-SAFETY) · Fase 0 = varrer as 20 perguntas do §1 + bugs de referência/aliasing/lifetime do `known-bugs.md`, produzir `memory-safety-investigation.md` (EN+PT) — ZERO edições no core | Fase 1 spec `docs/spec/memory-safety.md`; Fases 2–6 gateadas pela tabela do plano |
| — | `db-parity-plan.md` (+PT) — adendo `D-DB-GAPS` 21/09 | `EM DESENVOLVIMENTO` — mantenedora 21/09: **paridade total de DB** (todo alvo aceita mariadb/mysql/sqlite/mongodb); matriz medida + fatias S0–S4 | **lane `gaps-db`** (repassada 21/09 por ordem da mantenedora; a lane docs mantém o registro) · **S0 ✅ FEITO 21/09 (sessão 9092: recusa nomeada `DB001` + link-by-use)** (`D-DB-PARITY-OWNER`) | S1 `mariadb://` = alias mysql-wire (Native); S2 paridade JDBC JVM/JS/Android; S3 `mongodb://` interop-first (R9); S4 oracle |
| — | ~~`codegen-step-2.2.3-assessment.md`~~ → `docs/architecture/codegen-step-2.2.3-assessment.md` (+PT) — roadmap 2.2.3 | ✅ **CONCLUÍDO + MOVIDO 21/09** — opção B (`D-DESUGAR-STEP`) **implementada** (`85779f20`: `DesugarStepPipeline` + `DesugarSteps.defaults()` com os quatro desugars; `CompilerPipeline:303`) | medido 21/09: **descompasso de fase** (hook = IR otimizada; DDL = lowering; runner = desugar de AST) → o DDL fica no lowering | — (doc em `docs/architecture/`; regra dos 3 estados) |
| — | ~~`type-system-extensions-plan.md` (+PT)~~ → `docs/type-system-extensions-plan.md` — X5 variância+sealed / X6 reflexão de interop | ✅ **CONCLUÍDO + MOVIDO 22/09** — X5.0–X5.5 + X6.0–X6.3 todos landados (X5.5 células `sealedswitch`/`variance`/`useproj`; X6.3 célula `interopschema` + E2E binding Arrow/Parquet, `InteropSchemaE2ETest` 18/18); regra dos três-estados | — (doc em `docs/`) | — |
| — | ~~`kof-c-cross.md` (+PT)~~ → `docs/kof-c-cross.md` — alvos cross do `kof-c-compiler` (C1–C4) | ✅ **CONCLUÍDO + MOVIDO 23/09** — C1+C2+C3+C4+C3-residual todos landados (o compilador C do repositório emite riscv64/aarch64 via emissores por ISA; `kof c --target`/`-c`/`.o`; struct multi-eightbyte param ≤48 B + return ≤16 B); prova `KofCCrossCompilerTest`/`KofCParamsCompilerTest`/`KofCStructCompilerTest` 14/14 + `KofCObjectCompilerTest` 5/5 sob qemu (oráculo x86_64) | movido p/ `docs/` (regra dos 3 estados) | — |
| — | ~~`PLAN-BAREMETAL-BOOT.md` (+PT)~~ → `docs/PLAN-BAREMETAL-BOOT.md` — bare-metal/bootável com ring0/ring1 (faces B-0…B-6) | ✅ **CONCLUÍDO + MOVIDO 25/09** — **promovido de `future/` 22/09** (`D-BAREMETAL-BOOT`, ordem da mantenedora) · **B-0..B-3 + B-6 LANDADOS** (costura `kof_plat_*` no x86+cross; link freestanding + heap/stack configuráveis + `_end`; dtoa Schubfach libc-free no x86+cross (B-1c, §448); perfil UEFI; **boot BIOS legado roda o `main` Kof REAL bare** — B-3a+B-3b, `KO-BIOS OK`/`LM64 OK`/`PAYLOAD` sob SeaBIOS, `BiosBootE2ETest` **5/0F**; ring0/ring1 com prova de `#GP` + sabotagem da GDT) | movido p/ `docs/` (regra dos 3 estados) | — (**B-4 MCU landou no riscv32**: codegen 32-bit + port do coletor B4-GC-1..4 `NativeMcuGcTest` 8/0 + tempo B4-TIME `NativeMcuTimeTest` 2/0; fechamento por `D-BAREMETAL-MCU-GC` "and/or"; follow-ups rastreados: espelho Cortex-M3 + integração no emissor, q.v. `roadmap.md` §23) |
| — | registros vivos: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (em `docs/bugs-and-gaps/`); `roadmap.md` (aqui); `roadmap-audit.md`/`complexity-audit.md` (em `docs/audits/`) | `VIVA` | **não são backlog** — matriz/auditoria/fila que se atualizam junto com cada fechamento | atualizar célula/seção no MESMO commit que fecha o gap |

**Regra R12 (AGENTS.md):** nada de `future/` (RAII, package-compiler,
bare-metal) abre antes de SYSTEMS fechar (paridade + GC + estabilidade).
**Exceção, decisão da mantenedora 17/09** (`DECISIONS.md` §D-UNIVERSAL):
**`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (MOVIDO PARA `docs/architecture/`)** foi **promovido a trabalho corrente** com o portão
R12 **sobreposto** — seu ponto de entrada é o Estágio 1 (consolidação SYSTEMS)
+ R1–R12, então ele ataca justamente os itens SYSTEMS que esta regra manda
fechar.

---

## 2. Bugs abertos (fila em `docs/bugs-and-gaps/known-bugs.md`) — triagem
13/09, ressincronizada 14/09 ~22:15, **contagem viva ressincronizada 21/09**
(lane docs — registro vivo, regra §1 da tabela três-estados). A **autoridade**
da fila viva é `scripts/check_known_bugs_status.sh` (EN×PT consistentes),
nunca um número escrito à mão.

**O CHANGELOG não pode mentir sobre o ledger**: `scripts/check_changelog_ledger.sh`
conferiu cada afirmação `§NNN ✅ FIXED` contra essa fila viva (mesmo classificador, a
lista de abertos que o gate imprime) e VERMELHA o caso de retrocesso silencioso que
aconteceu de verdade em 21/09 — um rebase de base velha virou o §388 de `✅→🟡` enquanto
o CHANGELOG seguia alegando o flip, sem um único conflito para avisar ninguém. Citação
histórica de meia-face fechada só se isenta por linha nomeada em
`scripts/changelog-ledger-waivers.txt`, nunca editando o gate.

**Link do ledger tem que chegar**: `scripts/check_ledger_anchors.sh` recalcula o slug GitHub
de cada heading de seção e compara — por igualdade exata — com o href `pt-switch`/
`en-switch` da outra língua (diacríticos dobrados, pontuação removida, `_` preservado).
Medido 21/09: 11 de 26 hrefs eram abreviações à mão apontando para lugar nenhum
(incluindo dois que esta lane pousou na própria manhã). Todos regenerados até zero, e o
`--selftest` planta um slug truncado para a classe nunca voltar calada. Os dois gates
moram na suíte de agentes da CI (`run-agent-tests.sh`) e disparam por mudança via
`agent-verify.sh`.

**Contagem viva tem de bater com a autoridade**: `scripts/check_live_records.sh` extrai toda
declaração `N itens`/`N vivos` dos dois READMEs desta lane e exige que ela seja igual à contagem
viva do classificador. A classe driftou duas vezes em 21/09 — um resync de frase deixou uma linha
de tabela em `18`, e o resync seguinte esqueceu a mesma linha de novo, achada pela lane irmã em
`5a80625c`. Número cravado em dois lugares é promessa de drift; declaração *ausente* é falha, não
passe livre (anti-neutering: mudar a prosa obriga a atualizar o gate junto). O fio do hook é
provado funcionalmente — `agent-verify-wiring-test.sh` executa o esqueleto real dos blocos e pega
um regex certo preso num `if` mal aninhado (bug que esta lane plantou e corrigiu na mesma hora).

O mesmo gate também exige paridade EN↔PT dos IDs de decisão do `DECISIONS.md` e da numeração/
nível das seções, e — acrescentado 21/09 — que a **lista `Pendentes (condição 3)` da §0 seja
igual ao conjunto loose que o gate de release de fato marca** (`ls docs/development/*.md` menos
o `ALLOWLIST` dele). O registro humano não pode discordar da medição: nem listar menos, nem
listar a mais (um loose extra plantado foi pego nos dois idiomas). Também confere a **tabela EG** do roadmap —
fonte da condição 6 do release, que o gate lê só no EN —: mesmas linhas EG-N e mesmo estado
fechado/aberto em EN e PT, pela regra `DONE|FEITO` do próprio gate (divergência plantada no PT
é nomeada). Por fim, a paridade de numeração/nível de seções que era checada no `DECISIONS.md`
agora cobre **todos os pares EN↔PT** deste diretório — um `## 1.` no EN casado com um `# 1.` no PT
é nomeado (um deslize de nível plantado no README PT foi pego).

**4 itens na fila aberta** (**26/09: lane compiler 9092 CORREGIU o §513** (`json.encode` colapsava slots crus Double/Long em `encode_int` no x86 e o cast tag-2 do `List<Bool>` do JVM estourava CCE — `listTag` ganhou tags 3/5, dispatch de lista+map x86 corrigidos, cast JVM corrigido; `JsonNativeEncodeFpE2ETest` 1/1 oráculo-JVM + JVM≡x86, achado pelo motor da X2) **e catalogou o §514** (riscv64/aarch64 nunca receberam `kof_json_encode_double`/`encode_long`; alvos do motor restritos a {JVM,NATIVE,JS} com recusa `INTEROP005` até o port) — 3→4; **26/09: lane compiler 9092 catalogou §511** (a flake de boot OVMF do `RingPrivilegeE2ETest` sob carga da suíte completa — re-incidência ≥2 com evidência controlada por stash; dona = lane baremetal) — 2→3; **26/09: lane compiler 9092 FECHOU §500 (fatia B — campos estáticos via `KofGetStatic` real; RED→GREEN `ExternalStaticFieldE2ETest` 8/8, goldens medidos na JVM nua; a face da escrita é `SEM025`, não COMP002; reactor 4099 0F/0E)** — 3→2; **26/09: §508 — catalogado pelo heartbeat, CORRIGIDO no mesmo dia pela dona (lane baremetal)**; o heartbeat havia catalogado o §508 (ERRO do CodeQL só-do-modo-PR em `RuntimeDtoaSchubfach.java:90` — FP provável: o comprimento de `gTable()` (1234) é par, o passo 2 nunca toca `length`; os gates da branch leem alertas do modo push e não são afetados; ação dismiss-ou-guard registrada para a dona, lane baremetal) — 3→4; **26/09: lane compiler 9092 catalogou §500** (método/campo estático em nome de classe externa importada que não resolve emite `invokevirtual "".bogus` — até o válido varargs `Arrays.asList` é afetado, `ExternalClasspath` sem `ACC_VARARGS`; exige unidade dedicada) — 2→3; **26/09: lane compiler 9092 CORRIGIU §499** (método estático desconhecido em nome de tipo builtin — agora `SEM074`; RED→GREEN `BuiltinUnknownMethodGuardTest` 14/14; suíte 4016 0F/0E) — 3→2; **26/09: lane compiler 9092 catalogou §499** (método estático desconhecido em nome de tipo builtin — `String.bogus()`/`Int.bogus()` compilam limpo e emitem `invokestatic <Owner>.bogus` → `NoSuchMethodError`; exige whitelist de interop curada compartilhada typer+lowerer, regra 6/11) — 2→3; **26/09: lane native-cross FECHOU §497** (linha 13 nativa do `kof.io` completa: `delete` recursivo + `modifiedTime`/`isSymlink`/`moveTo`/`copyTo` no x86-64 + riscv64/aarch64; `NAT006` fechado) — 4→3; **25/09: lane compiler 9092 CORRIGIU §496** (campo desconhecido em QUALQUER namespace builtin — agora SEM102; RED→GREEN `BuiltinUnknownFieldGuardTest` 19/19) — 3→2; (**24/09: lane native-cross catalogou §494** (mensagem de erro do `kof.io` `size()` JVM vs Native — JVM `file not found: ` vs x86/cross `size: file not found: `; decisão de contrato, mesma família da §493) — 1→2; **24/09: lane gaps-db catalogou §493** (JVM e Native divergem no caminho de erro do `orm.delete`/`deleteAll` MySQL — a JVM lança string de SQLException, o Native x86-64/cross devolvem `true`; decisão de contrato) — 0→1; **24/09: lane compiler 9092 CORRIGIU §488** (x86 `RuntimeDb5`: NULL → literal `null`, `len==0` → `kof_json_encode_string`; `KofDbE2ETest#nativeMysqlNullAndEmptyStringJson` paridade com o oráculo JVM, RED pré-fix `{"n":,}`) — viva **1→0**; **24/09: lane gaps-db catalogou §488** — o caminho de texto MySQL do x86 (`RuntimeDb5 .Ldb_mysql_null`) emite NULL como string VAZIA crua → JSON inválido `{"n":,`, e uma célula de string VAZIA segue o caminho só-dígitos como número cru (também inválido quando vazia); fix de raiz pendente — viva **0→1**) (**24/09: lane 9092 CORRIGIU §278 face gpu** — Android compila `kof.gpu` como o JVM, `Main.class` byte-a-byte; runtime = `JvmVkStubRuntime` sem FFM no ART — viva **1→0**) (ressincronizado 23/09 — **23/09: lane 9092 CORRIGIU §485** (receive do canal: drenar a fila deixava `tail` obsoleto → o `send` seguinte anexava com `head=0`/`count>0` → deref NULL; `channelDrainThenSendNative` x86+riscv64+aarch64) — 2→1; **23/09: lane compiler 9092 CORRIGIU §486 face (b) `43f2833a` (mangling do bridge com sufixo do retorno) + §487 `374b2b4bb` (#610 diamante de default/aridade)** — 3→2; **23/09: lane compiler 9092 CORRIGIU §486 face (a)** (bridge de retorno referência no Native pulado — pass-through de registrador; `NativeGenericIfaceBridgeE2ETest` 3/3 em x86+riscv64+aarch64) **e catalogou §486 face (b)** (bridge de retorno primitivo colide num único símbolo asm; exige mangling ciente do retorno; arquivos da lane baremetal) — 2→3; **23/09: lane compiler 9092 CORRIGIU §205** (print de valor tipado `Object` no Native — record/classe que chega ao `println` por `as Object` ou local `Object` agora despacha o próprio `toString` via `kof_tostring_table[type_id]`; §205 fatia 2 = N2/ABI de caixa com tag; `NativeObjectBoxPrintE2ETest` 3/3 em x86+riscv64+aarch64) — 2→1; **23/09: sessão 9092 CORRIGIU §483 + §271 + §248** (dispatch de interface genérica no Native + default methods de interface no Native/JS — bridge da §356 no Native + slot de vtable alinhados, e o default herdado materializado nos implementadores JS; `GenericInterfaceAssignabilityTest` 10/10, `InterfaceDefaultMethodE2ETest` 7/7 nos 4 alvos) — 5→2; **23/09: lane 9093 CORRIGIU §476 + §478 (#587 break-in-case) — 5→4**; **23/09: lane 9092 baremetal catalogou §476** (lista mista de `case` padrão+valor com `default:` vazio compila limpo e morre no load da JVM com `VerifyError: Bad type on operand stack`) — 4→5; **23/09: lane 9092 baremetal CORRIGIU §423** (canais cross portados — `kof_channel_*` na fatia `RtB61`; gate NAT005 removido; paridade qemu nas duas arches) — 5→4; **23/09: lane 9092 baremetal CORRIGIU §448** (dtoa Schubfach no cross riscv64/aarch64 para `Double`+`Float`) — 6→5; **23/09: lane 9093 nat CORRIGIU §444** (ramo TypeVariable no dispatcher `valueOf` x86) — 7→6; ressincronizado 22/09 — **22/09: sessão 9093 (lane typer) FECHOU §280** (53/91 sites `error("",0,0,0)` agora com posição real; `DiagnosticSourceLocationTest` 5/5; WIP do turno morto retomado pela regra do dono-sumido — conflito do split §442 resolvido, 3 sites do `NewExpr` re-aplicados em `SemNewExprTyper`) — 8→7; **22/09: lane 9093 corrigiu o §442** (split `NewExpr` → `SemNewExprTyper`; `check_500` rc=0, suíte 3590 0F/0E @ `fd5119f69`) — 9→8; **10→9 quando a lane 9093 corrigiu o §268** (22/09, `D-RULE6-BATCH` opção (A): probe de `java.lang` com cache + SEM087, face super crua de extends/implements; `JavaLangHeritageTest` 9/9) e o **§288** (`D-RULE6-BATCH` opção (b): `TypeVariable` em parse-time, fonte única + rejeição interina SEM085 para tipos-função com T do dono; `FnTypeVarSignatureE2ETest` 9/9) e catalogou o **§444** (gap nativo pré-existente — o conjunto moviu §288 para fora / §444 para dentro, líquido 9); a linha do tip mesclado **11→10** já cobre o **§302** fechado (CLOSEALL) + **§442** catalogado (check_500 `SemExpressionTyper` 603); 21/09 — **14→10 quando o lote CLOSEALL da mantenedora fechou o §334** (caixa NaN do `kof_box_equals` no erasure) **e o §400** (função top-level nomeada passada como valor → SEM011) 21/09, e a lane nat/native-debug fechou o **§418** (harness riscv64: wait bounded + destroy/kill, `hangingChildIsKilledByTheBoundedWait`) 21/09; antes, **15→14 quando o §438** (a sessão `kof debug` vazava a JVM debuggee + o dir `/tmp/kof-debug-*`, esgotando o tmpfs) foi CORRIGIDO ✅ 21/09 pela lane `.18` (shutdown hook do `KofDebugJvmSession` + teardown que mata a árvore; RED-first `CliDebugProcessLeakTest`); antes, **16→15 quando o §437** (check_500 vermelho: `JvmOpCollections` 604) foi CORRIGIDO ✅ 21/09 pela lane `.18` (o fix §432 duplicado deixara código morto em `emitMapCall`; removê-lo levou o arquivo a 594, `check_500` rc=0 — sem split), depois de **14→16 quando o §437** e o **§438** foram catalogados 21/09 por outras lanes; **15→14 quando o §424** (cinco métodos `String` aceitos ficavam silenciosamente quebrados no JS e em link-fail no Native sem gap code) foi CORRIGIDO ✅ 21/09 pela lane `.18` (gate honesto `STR003` no JS + Native; JVM real); **16→15 quando o §427** (riscv64/aarch64 baixavam `kof.io`/web-T1 sem runtime no cross → `ld` undefined-reference alto) foi CORRIGIDO ✅ 21/09 pela lane `.18` (gates honestos `NAT006` io + `NAT007` web-T1); **17→16 quando o §426** (`time.collect()` compilava limpo no JS mas o runtime nunca exportava `kofGcCollectNow`, então o artefato falhava no load) foi CORRIGIDO ✅ 21/09 pela lane `.18` (JS gateado com `TIME004`); **18→17 quando o §425** (`kof.config` no riscv64/aarch64 era um stub de default-echo silencioso com `supportedOn` erradamente true) foi CORRIGIDO ✅ 21/09 pela lane `.18` (`supportedOn` false no cross → `CONF001` vivo; javadoc/matriz corrigidos); **19→18 quando o §428** (o `default` do DAP JVM/Native respondia toda requisição não implementada com `success:true` + corpo vazio, fachada silenciosa Q7) foi CORRIGIDO ✅ 21/09 pela lane `.18` (os dois defaults agora `fail`/`fail2` com `unsupported request: <cmd>`; `restart` documentado como limite); **20→19 quando o §432** (VerifyError do JVM em `Map<_,Object>.getOrDefault(k,<primitivo>)`, catalogado pela lane nat no endurecimento §352/Q4) foi CORRIGIDO ✅ 21/09 pela sessão 9093 (`.18`; tipo do arg escrito separado do V do slot em `JvmOpCollections.emitMapCall`); **21→20 quando o §435** (check_500 `LspServer` 601, lane CLI/plataforma) foi CORRIGIDO ✅ 21/09 pela lane `.18` (split em `LspJsonRpc`; `LspServer` 601→582; `check_500` rc=0); havia sido catalogado 21/09; **20→22 quando o re-pouso F2d1 da gaps-db catalogou §433/§434** (os dois vermelhos do tip remoto da lane FFI/JS, `2995d0f8`: bundle JS hello +5% do orçamento, auditoria de gate do Buffer sem JS); 23→22 quando **§429** (request desconhecida no LSP agora responde `-32601`, lane docs/plataforma) foi CORRIGIDO ✅ 21/09, 22→21 quando **§431** (tooling drift da CLI, lane `.18`) foi CORRIGIDO ✅ 21/09; 20→19
quando **§380** (codegen JS de `if` aninhado com `throw`) foi formalizado ✅
`9f383bcf`, re-medido 16/0F no tip; 19→18 quando **§381** (OOM do keyword
entity-field no parser) foi corrigido ✅ `576a1dcb`; 18→17 quando **§394**
(harness de teste vaza o app servido) foi corrigido ✅ `d0464385` e **§353**
(resultado de método `io` dentro de lambda — SEM014) foi corrigido ✅ 21/09
pela lane do compilador; 17→18 quando **§418** (harness de single-step do debug riscv64) foi RE-PUBLICADO pela lane nativa no mesmo dia, com nova prova, após a perda de árvore que a retração §419 dela registra — a contagem desceu (correções) e subiu (um gap real reapareceu) num só dia, exatamente por isso a autoridade é o script, não a prosa; 18→19 quando a própria lane db/orm ABRIU a
§421 (`db.connect` nativo aceita qualquer scheme em silêncio; a recusa só aparece no
`kof_orm_*`) como catálogo honesto do seu F2c3 — contagem subir porque lanes continuam
catalogando contra si mesmas é o ledger funcionando, não apodrecendo; 19→18 quando **§396** (println de RECORD null no Native x86-64) foi corrigido ✅ `461a07e2` pela lane nativa no mesmo dia; 18→19 quando a lane do §396 ABRIU o **§422** (um `extern` de assinatura não-suportada "compila limpo") e 19→18 no MESMO dia quando a lane FFI o RESOLVEU — **NÃO é bug, é teste stale**: `Int[]` binda por desenho desde o D6-2, então a asserção foi reapontada para assinaturas genuinamente não-suportadas (`String[]`/`List<Int>` → `FFI001`, `Buffer(Int)` → `SEM096`), rejeição intacta (`CompilerDriverTest` 259/0F); 18→16 quando a varredura de órfãos da lane nat, em 21/09, fechou o **§192** (hang do parseOrDefault no cross — já corrigido por `5d4d59b9`, o ledger nunca tinha sido virado; `KofMathTest.parseOrDefaultCrossArch` 1/1) e o **§358** (`toString` em `T` sem bound no nativo → recusa honesta `NAT004`; `NativeGenericDispatchGapE2ETest` 2/2); 16→15 quando o **§258** (o guarda-chuva CodeQL, #775+#776) foi CORRIGIDO ✅ 21/09 no próprio arquivo pela lane `.18` (`TestJdk.javaBin()` para o #775 + o `switch` exaustivo sobre `Target` para o #776); 15→16 quando a MESMA varredura catalogou o **§423** (canais em riscv64/aarch64 nunca foram portados — link-fail críptico `undefined reference` agora `NAT005` honesto; `BareCollectionPrimitiveArgE2ETest` 12/12), a contagem subindo porque a lane achou e declarou um gap real pré-existente; 16→24 quando a passada 5 da frente de revisão catalogou **§424–§431** (achados semânticos + ratchets por alvo, lotes 1–3); 24→23 quando o **§421** (`db.connect` nativo aceitava qualquer scheme — S0) foi CORRIGIDO ✅ `b1a5373b` pela lane DB/db-parity; 23→22 quando o **§431** (drift de tooling: `serveStatic` morto, `Compare` não-fatal em opção desconhecida, ramo inalcançável do DAP) foi CORRIGIDO ✅ pela lane `.18`; por
`scripts/check_known_bugs_status.sh`; o número é um snapshot datado — a
autoridade é o script). Os **32** contados em 14/09 e a lista de 13/09
abaixo são o snapshot HISTÓRICO, preservado para o registro (tirado ANTES da
onda §220–§239). A conclusão permanece COM
correção: os itens ainda abertos têm dono/bloqueio/regra-6 — mas o "ZERO item
código-puro" foi REFUTADO pela própria onda de 14/09: §236 (comparisonReturn
Bool×Int) e §238 (hoist de local escapante + sipush) eram itens código-puro do
decompiler e **foram consertados na lane de desenvolvimento** (unidades 2c,
`8719e304`+`f2371212`), enquanto §233/§234 (migração de teste
`split()->String[]` — lane compiler) e §237 (`computeStack` — lane .22) foram
catalogados com dono. O resto da onda de 14/09 (§220–§232, §235, §239) pertence
às lanes .15/.18/.22 ou regra 6. Itens da lista de 13/09 que mudaram desde
então: §129-[coleção] ✅ 11/09 (`3645` — o §129 ABERTO é o do OTP, colisão de
número), os demais seguem como descrito. Fechados 13/09: §89, §106 (+JS `ab85cfae`), §117, §131 (+residual
`73ca2d58`), §127-JVM, §155, §94, §156, §81 (BigInt), §163 (interpretador
2º parâmetro largo); §157-160 e §65 fechados/NÃO-REPRODUZ.
Todos pendurados em:

| Grupo | Bugs | Quem destrava |
|---|---|---|
| Decisão ratificada 13/09 — implementação pendente | §161/NAT-STR01 (§89 ✅ `e33425b5`, §106 ✅ `5b939106`+JS `ab85cfae`, §117 ✅ `3734f2aa`, §131 ✅ `18a64d45`, §81 ✅ `839bd73f`, §163 ✅ `d2a8a618`; §45/DD-01 FECHADO 13/09 — ver `docs/decisions/DD-01-finally-return.md`) | fila ratificada / lanes executoras |
| Congelado regra-6 | ~~§101~~ ✅ CORRIGIDO 14/09 (DECISIONS §1 opção A — IEEE 754 puro em todos os alvos) | ninguém (contrato) |
| Lane alheia | §104b-ii + §107 restante + §114 (bugfixer — storage-box de record), §132 (OTP-JS) ✅ FECHADO 18/09 (#83-JS — caiu na lane dev/KofJS, não alheia), §165 (js-slices — re-verificado 13/09: NÃO reproduz em clean build, provável não-bug) — §129 ✅ CORRIGIDO 15/09 (lane development `192.168.100.18`) | donos das lanes |

Corrigidos 13/09: **§89** (conversão numérica em primitivo = alias do `as` +
warning SEM090; 4 alvos — `CoreRegressionE2ETest.numericConvertMethodAliasOfAs`),
**§106** (`json.encode(Map)` chaves SORTED nos 4 alvos — `JsonCompleteE2ETest`
+ célula `jsonenc-map` da matriz; residual JS `ab85cfae`),
**§117** (cancel por TID real + probe linear no Native x86 — `KofConcurrency2Test`
34/0), **§131** (sobrecarga de método por assinatura nos 4 backends —
`CoreRegressionE2ETest.methodOverloadByArity` + harness 4/4), **§94** (EQ/NE de Double/Float no interpretador agora IEEE —
célula `stdsqrt` 4/4 sem exclusão), **§127-JVM** (cast p/ tipo-função →
interface SAM sintética; `LambdaE2ETest.castToFunctionTypeJvm/Native`),
**§155** (tipo-função como type-arg → parser preserva os espaços do type-ref;
`LambdaE2ETest.declaredFunctionTypeListJvm/Native`), **§156** (lista
heterogênea de lambdas mesma assinatura → elemento sem className, dispatch
SAM; `LambdaE2ETest.heterogeneousLambdaListJvm/Native`), **§81** (Long=BigInt
no JS, paridade 64-bit real — `839bd73f`) e **§163** (interpretador: 2º
parâmetro largo `Long`/`Double` lido como `null` — `KofInterpreterParityTest.
wideParametersOccupyTwoSlots` + célula `wideparams` 4/4; `d2a8a618`).
Corrigidos 12/09: §90 (web, #98), §125,
§139, §140 (gate→ratchet), §107-face
escalar, §108, §138, MATH001, TIME002, **§145/§146/§147 (issue #101,
`440730c8` — prova qemu 42+42)**.

---

## 3. Decisões da mantenedora (registro: `DECISIONS.md`)

> Nada aqui está "parado esperando" — as frentes que esperavam decisão foram
> **ratificadas 13/09** e vivem em `DECISIONS.md` (D-STDLIB/D-SEC/D-APP/
> D-SPRING/D-PLAT/D-PLATFORM) com a fila de execução aberta. A regra
> permanece: **frente sem linha em `DECISIONS.md` não é atacada** (regra 6);
> decisão do chat trava lá no mesmo commit. `known-bugs.md` =
> `docs/bugs-and-gaps/known-bugs.md`.

| Item | Onde | O que espera |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `docs/stdlib/DD-STDLIB-01-array-returns.md` (FECHADO 13/09, movido p/ docs/) | ✅ IMPLEMENTADO 13/09 (opção 6a: `randomBytesHex` alias de `hex` + choice=idiom; S10c FECHADO) |
| DD-STDLIB-02 — `time.format`/`boundaries` | `DECISIONS.md` §D-STDLIB | ✅ RATIFICADO 13/09 (UTC-only, escalares ISO, zero pattern-DSL) — **fila liberada** (todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds) |
| DD-01 — `finally` no caminho de `return` | `docs/decisions/DD-01-finally-return.md` (FECHADO 13/09, movido p/ docs/) | ✅ IMPLEMENTADO 13/09 (opção 4a: FinallyFrame na IR + gates finallyReturnJvm/Js; suíte 1627/0; bug 45 FECHADO) |
| DD-OTP (concluída) | `docs/planning-otp-supervision.md` (FECHADO 19/09, movido p/ docs/) | ✅ RATIFICADAS 13/09 (opção 1a: wrapper `(id, resultado)`) — **S2-JVM ✅ 13/09** (`Supervisor.startAll`/laço selectAny único) + **S2-Native x86 ✅ 15/09** (§129 fechado, DECISIONS §2 opção B) + **S2-JS ✅ 18/09** (§132 fechado, `OTP002` elevado) + **riscv64/aarch64 ✅ 19/09** (§129 port cross — tabela por-TID; gate `OTP001` removido) |
| `pow`/`-lm`, `roundTo`-mode | `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ `pow` **FEITO 13/09** (7a: `-lm`; 5 alvos MATH001 cross; `stdmathpow` matriz + `powCrossArchRefused` `d736e36e`) · `roundTo` **NÃO aprovado pela 7a** (ratificação = só pow; "+roundTo" era nota de agente no plano — superfície/assinatura indefinida = regra 6, aguarda decisão da mantenedora) |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 / conformance-matrix | ✅ ABERTO POR DECISÃO 13/09 — implementar UTF-8 astral nos nativos |
| §129 (unwind cross-thread via TLS) | `known-bugs.md` | ✅ CORRIGIDO 15/09 (DECISIONS §2 opção B: chain TLS por thread + handler por worker no trampolim; x86_64; riscv/aarch seguem `OTP001`) |
| json §106 | `known-bugs.md` | ✅ CORRIGIDO 13/09 (opção 2b: chaves sorted) — JVM/x86/Script/JS (`5b939106` + residual JS `ab85cfae`); gap de porte riscv/aarch rastreado à parte |

---

## 4. Índice do que está EM DESENVOLVIMENTO aqui

### 4.1 Plataformas & migração (caíram de `future/` 12/09 — código iniciado; os `~~riscados~~` foram **ratificados 13/09 e consolidados em `DECISIONS.md`** — os 6 arquivos de `decision-pending/` foram apagados)

| Arquivo | Estado real | O que falta p/ fechar |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `DECISIONS.md` §D-PLATFORM (morto) | F1–3/8/9 com código (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 testes) | F1 resolvido pelo manifesto; F4/F5→KOFUI-AUDIT/stdlib-web; F6 wasm/F7 android→tabela D-APP Q7/Q10; F9→conformance-matrix |
| ~~`APPLICATION_MODEL.md`~~ → `DECISIONS.md` §D-APP (Q1–Q10 travados) | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | `CmdNew` (I1), I3 (`--fat`), System/distribuído — fila |
| ~~`LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md`~~ → **`future/` (DESPRIORIZADO pela mantenedora 15/09)** — umbrella §4 IR/Confidence, §8 diff-testing; ~~+ `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md`~~ (FUNDIDAS no umbrella 13/09) | código fica no repo: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`) + `Confidence`/`Type.fromJvmSignature`; **NÃO é trabalho atual — promoção exige decisão explícita dela**; **contagem viva = `roadmap.md` §23 TIER 3–5** (não duplicar número aqui) | cobertura: switch/athrow opacos, `inspect --java` (R5 do audit), IR non-JVM |
| ~~`IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md`~~ → `roadmap.md` §23 | **FUNDIDOS 13/09** (redundância ~85% entre si; status sobre-claimed vs código — ex.: `CodegenStep` ✅ inexistente, FFI Native era FFI001) | §23 é o plano único; tiers 6–12 = `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (promovido 17/09, R12 sobreposto) |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparação branch `planning-future`×beta **encerrada 13/09** — nada de código aberto próprio mora nelas: R2 vive em `DECISIONS.md` §D-APP/§D-PLATFORM; R5 no cluster migração (`DECOMPILER.md`/`LEGACY_MIGRATION.md` §4 Fase C) | — (fora de `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | FECHADO 13/09 (FinallyFrame IR + gates; bug 45 CORRIGIDO, suíte 1627/0) | — (fora de `development/`) |
| ~~`planning-stdlib-time-design.md`~~ → `DECISIONS.md` §D-STDLIB | `addDays`/`diffDays` nos 5 alvos | ✅ RATIFICADO 13/09 — fila liberada |

### 4.2 Plans & auditorias vivas

| Arquivo | Estado real | Nota |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUÍDO 13/09 (S-1..S-6.1 + S-7; consolidado em `docs/stdlib/stdlib-loading.md`) | S-5-x86 = fila bugfix (`root_end`), fora do plano |
| `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 fechados 11/09) | só decisões pendentes (§3) |
| ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` | ✅ CONCLUÍDO 19/09 — 1ª fatia ✅ JVM+Script; **S2-JVM ✅ 13/09**; **S2-Native x86 ✅ 15/09**; **S2-JS ✅ 18/09**; **riscv64/aarch64 ✅ 19/09** (§129 port cross — tabela por-TID; `OTP001` removido) | movido p/ `docs/` (regra dos 3 estados) |
| `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | CLI/DAP/LSP/stdout-json ✅ | plugin IntelliJ |
| ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ PROMOVIDO 19/09 (faces (1)–(5) fechadas; NATIVE002 FECHADO) | recusas por domínio vivem em known-bugs/backend-parity |
| ~~`type-system-extensions-plan.md`~~ → `docs/type-system-extensions-plan.md` | ✅ CONCLUÍDO + MOVIDO 22/09 (X5.0–X5.5 + X6.0–X6.3; células `sealedswitch`/`variance`/`useproj`/`interopschema`) | movido p/ `docs/` (regra de 3 estados) |
| ~~`security-plan.md`~~ → `DECISIONS.md` §D-SEC | A ✅; B/C ✅; C11 cookies + C18 middleware + D16 OAuth + D17 TLS-cert **ratificados 13/09** (executa com I2 do app model) | ChaCha20 = fila; OAuth: resource-server→client, provider=NUNCA |
| ~~`plan-platform-completion.md`~~ → `DECISIONS.md` §D-PLAT (morto) | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | P4/P5 já têm casa (§23/backend-parity); blog E2E = D-SPRING F12 |
| ~~`plan-spring-independence.md`~~ → `DECISIONS.md` §D-SPRING | F1–9 ✅; F10–F12 ratificadas 13/09 — **tudo IMPLEMENTADO; §D-SPRING `CONCLUÍDA` 19/09** (auditoria vs código) | sem frente aberta neste registro; seguimentos vivem nos trackers |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | matriz Feature×4 targets travada por `ConformanceMatrixTest` (11) + doc-gate | viva: atualiza com cada gap |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 com `PARTIAL`/`PLANNED` (events, batch, AI) | referência de cobertura |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monólito→micro) | longo prazo |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matriz 06/09 + fila P0→P5 (P0 FECHADO 09/09) | re-audit quando algo fecha |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (face R6: no-op silencioso) ABERTO | lane UI |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | **4 vivos** (contagem viva — **26/09: lane compiler 9092 CORREGIU o §513** (`json.encode` colapsava slots crus Double/Long em `encode_int` no x86 e o cast tag-2 do `List<Bool>` do JVM estourava CCE — `listTag` ganhou tags 3/5, dispatch de lista+map x86 corrigidos, cast JVM corrigido; `JsonNativeEncodeFpE2ETest` 1/1 oráculo-JVM + JVM≡x86, achado pelo motor da X2) **e catalogou o §514** (riscv64/aarch64 nunca receberam `kof_json_encode_double`/`encode_long`; alvos do motor restritos a {JVM,NATIVE,JS} com recusa `INTEROP005` até o port) — 3→4; **26/09: lane compiler 9092 catalogou §511** (a flake de boot OVMF do `RingPrivilegeE2ETest` sob carga da suíte completa — re-incidência ≥2 com evidência controlada por stash; dona = lane baremetal) — 2→3; **26/09: lane compiler 9092 FECHOU §500 (fatia B — campos estáticos via `KofGetStatic` real; RED→GREEN `ExternalStaticFieldE2ETest` 8/8, goldens medidos na JVM nua; a face da escrita é `SEM025`, não COMP002; reactor 4099 0F/0E)** — 3→2; **26/09: §508 CORRIGIDO pela dona (lane baremetal)** — o guard do ERROR CodeQL modo-PR pousou; 4→3; **26/09: heartbeat (lane paridade/qualidade) catalogou o §508** (ERRO do CodeQL só-do-modo-PR em `RuntimeDtoaSchubfach.java:90` — FP provável, `gTable()` tem comprimento par; gates da branch não afetados; ação registrada para a dona baremetal) — 3→4; **26/09: lane compiler 9092 catalogou §500** (método/campo estático em nome de classe externa importada que não resolve emite `invokevirtual "".bogus` — até o válido varargs `Arrays.asList` é afetado, `ExternalClasspath` sem `ACC_VARARGS`; exige unidade dedicada) — 2→3; **26/09: lane compiler 9092 CORRIGIU §499** (método estático desconhecido em nome de tipo builtin — agora `SEM074`; RED→GREEN `BuiltinUnknownMethodGuardTest` 14/14; suíte 4016 0F/0E) — 3→2; **26/09: lane compiler 9092 catalogou §499** (método estático desconhecido em nome de tipo builtin — `String.bogus()`/`Int.bogus()` compilam limpo e emitem `invokestatic <Owner>.bogus` → `NoSuchMethodError`; exige whitelist de interop curada compartilhada typer+lowerer, regra 6/11) — 2→3; **26/09: lane native-cross FECHOU §497** (todas as faces nativas do `kof.io`; `NAT006` fechado) — 4→3; **26/09: lane native-cross catalogou §497** (gaps nativos do `kof.io`: `Directory.delete` não recursivo no x86-64 + sem `copyTo`/`moveTo`/`modifiedTime`/`isSymlink` nativo) — 3→4; **25/09: lane compiler 9092 CORRIGIU §496** (campo desconhecido em QUALQUER namespace builtin — agora SEM102; RED→GREEN `BuiltinUnknownFieldGuardTest` 19/19) — 3→2; (**24/09: lane gaps-db catalogou §493** (JVM e Native divergem no caminho de erro do `orm.delete`/`deleteAll` MySQL — a JVM lança string de SQLException, o Native x86-64/cross devolvem `true`; decisão de contrato) — 0→1; **24/09: lane compiler 9092 CORRIGIU §488** (x86 `RuntimeDb5`: NULL → literal `null`, `len==0` → `kof_json_encode_string`; E2E `nativeMysqlNullAndEmptyStringJson` paridade byte a byte com o oráculo JVM) — 1→0; **24/09: lane gaps-db catalogou §488** (caminho de texto MySQL do x86 emite NULL como string vazia crua → JSON inválido, e string vazia como número cru; `RuntimeDb5 .Ldb_mysql_null`; fix de raiz pendente) — 0→1; **24/09: §278 gpu CORRIGIDA** (Android=JVM, stub sem FFM) — 1→0; **23/09: lane 9092 CORRIGIU §485** (deref NULL no drena-e-envia do canal em `kof_channel_receive` — `tail` obsoleto; `channelDrainThenSendNative` x86+riscv64+aarch64) — 2→1; autoridade é `scripts/check_known_bugs_status.sh`; **23/09: lane compiler 9092 CORRIGIU §486 face (b) `43f2833a` (mangling do bridge com sufixo do retorno) + §487 `374b2b4bb` (#610 diamante de default/aridade)** — 3→2; **23/09: lane compiler 9092 CORRIGIU §486 face (a)** (bridge de retorno referência no Native pulado — pass-through de registrador; `NativeGenericIfaceBridgeE2ETest` 3/3 x86+riscv64+aarch64) **e catalogou §486 face (b)** (bridge de retorno primitivo colide num único símbolo asm; exige mangling ciente do retorno; arquivos da lane baremetal) — 2→3; **23/09: lane compiler 9092 CORRIGIU §205** (print de valor tipado `Object` no Native — record/classe agora despacha `toString` via `kof_tostring_table[type_id]`; §205 fatia 2 = N2/ABI de caixa com tag; `NativeObjectBoxPrintE2ETest` 3/3 x86+riscv64+aarch64) — 2→1; **23/09: sessão 9092 CORRIGIU §483 + §271 + §248** (dispatch genérico no Native + defaults de interface no Native/JS; `GenericInterfaceAssignabilityTest` 10/10, `InterfaceDefaultMethodE2ETest` 7/7) — 5→2; **23/09: lane 9093 CORRIGIU §476 + §478 (#587 break-in-case) — 5→4**; **23/09: lane 9092 baremetal catalogou §476** (lista mista de `case` padrão+valor com `default:` vazio compila limpo e morre no load da JVM com `VerifyError: Bad type on operand stack`) — 4→5; **23/09: lane 9092 baremetal CORRIGIU §423** (canais cross portados — `kof_channel_*` na fatia `RtB61`; gate NAT005 removido; paridade qemu nas duas arches) — 5→4; **23/09: lane 9092 baremetal CORRIGIU §448** (dtoa Schubfach no cross riscv64/aarch64 para `Double`+`Float`) — 6→5; **23/09: lane 9093 nat CORRIGIU §444** — 7→6; **22/09: sessão 9093 (lane typer) FECHOU §280** (53/91 sites com posição real; `DiagnosticSourceLocationTest` 5/5) — 8→7; **22/09: lane 9093 corrigiu o §442** (split `NewExpr` → `SemNewExprTyper`; `check_500` rc=0, suíte 3590 0F/0E @ `fd5119f69`) — 9→8; **22/09: lane 9093 corrigiu §268** (`D-RULE6-BATCH` (A): probe `java.lang` + SEM087; `JavaLangHeritageTest` 9/9) e **§288** (`D-RULE6-BATCH` (b): `TypeVariable` em parse-time, fonte única + rejeição interina **SEM085**; `FnTypeVarSignatureE2ETest` 9/9) e catalogou **§444** (pré-existente: classe genérica com constructor de ARG T roda em silêncio no Native, medido em origem limpa) — 10→9 no §268, depois §288 saiu + §444 entrou e a conta ficou **9**; **11→10 ao re-medir no tip mesclado — §302 fechado (CLOSEALL) e §442 catalogado (check_500 `SemExpressionTyper` 603)**; **14→12 quando o lote CLOSEALL da mantenedora fechou §334/§400 21/09**; **re-medido no tip mesclado** após o §424 corrigido pela lane `.18` 21/09 (cinco métodos `String` agora recusam honestamente `STR003` no JS/Native), o §437 fechado (o check_500 vermelho já não existia: `JvmOpCollections` 594 < 600 após o cleanup de código morto do §432) e o §438 corrigido (a sessão `kof debug` agora registra um shutdown hook que mata o debuggee e reclama o `/tmp/kof-debug-*`); snapshot após o fix S0 do §421 + §432 corrigido pela sessão 9093 (VerifyError do `getOrDefault` JVM, tipo do arg escrito separado do V do slot) + §423–§431 catalogados + §433/§434 (vermelhos remotos FFI/JS) pelo re-pouso da gaps-db + §429 corrigido (LSP `-32601`) e §431 fechado pela lane `.18` + §435 (check_500 vermelho catalogado pela lane gaps-db) fechado pela lane `.18` + §436 (assinaturas de `secrets.of`/`secrets.secret` no `StdCatalog`, lane D-SECRETS) corrigido pela lane `.18`/9093 21/09 + §437 (check_500 vermelho: `JvmOpCollections` 604, lane JVM) catalogado 21/09 (19->20) + §438 (test-infra: testes debug/serve do cli vazam JVMs suspensas + dirs scratch em `/tmp` -> tmpfs esgotado; host limpo 6,3G->621M) catalogado 21/09 (20->21); §435 foi fechado pela lane `.18` (check_500 `LspServer` 601→582, split em `LspJsonRpc`); 18→16 quando §192 e §358 foram fechados pela varredura de órfãos da lane nat em 21/09; 16→15 quando o §258 foi fechado 21/09 (#775 `NumericFormatterE2ETest` + #776 `KofHttp.supportedOn`); 15→16 quando o §423 (canais no cross, NAT005) foi catalogado em 21/09; era 20 — §380 `9f383bcf`, §381 `576a1dcb`, §394 `d0464385` e §353 21/09 fechados; §400/§418/§421 catalogados/re-publicados; a fila viva do §2 é a autoridade; a contagem histórica de 14/09 era 32; §81/§163/§127-JVM, §155, §94, §157-160 e §65 fechados/NÃO-REPRODUZ 13/09; §425–§428 foram fechados pela lane `.18` 21/09 (entradas do CHANGELOG: cross `NAT006`/`NAT007`, JS `TIME004`, config `CONF001`, fix do §428)) | fila viva |
| ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **FEITO + MOVIDO 13/09** (F1–F9 todas fechadas — F3: NativeBackend 498 ≤500 medido, bloqueio da lane GC caducou/regra do dono-morto); ratchet `check_500-baseline.txt` (dívidas travadas — nº autoritativo = `wc -l` do arquivo) no CI | plano FECHADO (regra dos 3 estados) |
| `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (MOVIDO PARA `docs/architecture/`, **CONCLUÍDO** — Estágios 1–3 + R + Estágio 8 implementados, fases futuras separadas para `development/future/`) | promovido de `future/` 17/09, R12 sobreposto (`DECISIONS.md` §D-UNIVERSAL); fases futuras separadas para `development/future/` pela regra 3-estados | arquitetura dos Tiers 6–12; visão/design congelados, claims de estado sincronizadas com o código |
| `docs/PROPOSAL-1.0-EXIT-GATE.md` (+PT; saiu de development/ 24/09) | **KOF 1.0 EXIT GATE — RATIFICADO 20/09/2026** pela mantenedora (`DECISIONS.md` §D-RELEASE-1.0); promovido de `future/`: o gate (§8) + a fila (§23) são a meta vinculante de estabilização — **Kof RC 1.0 / release 1.0 só existem quando todos os pontos corresponderem e nenhuma aresta estiver aberta** | ordem de execução = o §23 do próprio PROPOSAL, rastreada no `roadmap.md` §24 (EG-1..EG-10); **as sete arestas `[? MEL]` FECHADAS 20/09 por `DECISIONS.md` §D-1.0-EDGES** — KofC + Android dentro da Stable 1.0 de 8 alvos com gates próprios (EG-9/EG-10), os nove candidatos de reforço da §35 são gates obrigatórios, a linha 1.0 abre após o corte 0.5.0 + EG-1..EG-7; a única aresta restante é a declaração da mantenedora que abre o RC (EG-8) |

### 4.3 `future/` — só plano, zero código (não é trabalho atual)

> O índice **completo e autoritativo** desta pasta (todo plano + seu gatilho)
> é `future/README.md` — as linhas abaixo são as que mais costumam barrar o
> trabalho atual; em dúvida, leia aquele índice, não esta tabela.

| Arquivo | Gatilho p/ cair p/ cá |
|---|---|
| `PLAN-MULTIPARADIGMA.md` (multiparadigma / pipelines funcionais + queries declarativas; 16/09, só design) | primeiro incremento funcional começa (SYSTEMS fechado, R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump com `using`/`resource_scope` decidido |
| ~~`PLAN-BAREMETAL-BOOT.md`~~ → **promovido 22/09 ao índice de desenvolvimento, CONCLUÍDO + movido para [`../PLAN-BAREMETAL-BOOT.md`](../PLAN-BAREMETAL-BOOT.md) 25/09** | **FECHADO 25/09** (`D-BAREMETAL-BOOT` promovido 22/09; `D-BAREMETAL-MCU-GC` "and/or" fechou o B-4 no riscv32) — frente bare-metal completa end-to-end (BIOS/UEFI/MCU riscv32); follow-ups (espelho Cortex-M3, integração no emissor) rastreados na §23 |
| `PLAN-BOOTSTRAP.md` (o Bootstrapper: Kof escrito em Kof — **estrela-guia**, `DECISIONS.md` §D-BOOTSTRAP, 20/09) | EXIT GATE 1.0 fechado + condições de entrada E1–E6 (`roadmap.md` §24) |
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (plataforma de migração legado) | **de volta p/ cá 15/09 — DESPRIORIZADO pela mantenedora**; promoção exige decisão explícita dela |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` **saiu de `future/` 13/09** — decisão 6a ratificada, implementado e movido p/ `docs/stdlib/DD-STDLIB-01-array-returns.md`.)*

*(movimentos históricos de 12/09: 13 docs caíram de `future/` p/ cá —
evidência em cada linha de §4.1; snapshot SG 08/09 → `docs/history/`)*

---

## 5. O que NÃO está mais aqui (consolidado 12/09, com prova)

| Saiu p/ | Doc | Prova |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–023 + E1–E3 | fila do maintainer COMPLETA (resumo do próprio doc); snapshot antigo → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | níveis 0–4 | query DSL 01/09 (`KofOrmE2ETest` 32; paridade JS 18/09), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/DB002/ORM001 (native) vivem na matriz de paridade |
| `docs/audits/complexity-audit.md` | snapshot 02/09 | números pré-SOLID-500; gate vivo = `scripts/check_500.sh` (ratchet) |
| `docs/history/roadmap-gap-2026-09-03.md` | gap report datado | pendências vivem em roadmap-audit/known-bugs |
| `docs/decisions/` | `planning-switch-expr`, `planning-mutability` | SYN001, DD-02/SEM037/SEM038 aplicados |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | CANVAS001 | `UiE2ETest` 29/29 sem exclusões |

---

## 6. Como usar (agente autônomo)

```
1. LEIA docs/status.md + docs/backend-parity.md            → o que funciona (gate)
2. LEIA a fila §1 deste README + DOING.md (donos)          → o que falta, sem colisão
3. BUGS: known-bugs.md §Aberto só com dono na mesa; decisão → §3, não editar
4. EXECUTE um escopo → teste (suíte com -Dmaven.test.failure.ignore=true)
   → commit com DOING.md atualizado → mova doc p/ docs/ se FECHOU
5. RE-DISPARO: sem item na fila §1 sem dono E suíte verde → RECUSE
   (condição de estabilidade AGENTS.md)
```

**Sincronização:** `git fetch && git pull --rebase --autostash` antes de TODO
commit; releia este README depois do pull (outro agente pode ter fechado um
item da fila). `DOING.md` marca dono/estado; este README é a **fila**.

**Não confundir:** `training/` + `learn/` + `docs/` = corpus estável.
`development/` = trabalho que ainda não é comportamento previsto. Mudança de
contrato congelado nunca passa por aqui sem bump + decisão (regra 6).
