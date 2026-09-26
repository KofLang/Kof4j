[English](release-beta-0.5.0-prep.md) | [Português](release-beta-0.5.0-prep.pt_BR.md)

# Release 0.5.0 — preparo (branch `beta-0.5.0`)

Decisão: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09, ordem da mantenedora). Branch
ativa é `beta-0.5.0`; `beta-0.4.0` só recebe pousos em voo e preparo de
release. Este doc é a fila — fica em `docs/development/` até o corte (regra
dos três estados).

## Checklist (ordenado — número da versão e tag são decisão da mantenedora, regra 6)

1. [x] Pousar o que está em voo: §374/#553 (`.22` — WIP em
       `JvmOpCollections`), §371/#550 (CLI cross build), §378/#554 (gate
       docs). **TODOS OS TRÊS POUSARAM 20/09 — ✅ FIXED** (`§374` box-if-primitive
       no class load, `BareCollectionFieldE2ETest` 8/8; `§371` CLI embarcada cross,
       `ShippedCliCrossSmokeTest` 2/2 + `RuntimeSourceLoaderTest` 6/6; `§378`
       cruzamento EN×PT do conjunto aberto — provas no `known-bugs.md`). A lane
       docs faz ff da `beta-0.5.0` após cada pouso na 0.4.0.
2. [ ] Dívida CodeQL (#555): **TRIAGEM FECHADA 20/09 (unidade I, §385)** —
       os 40 da janela: 13 fixados no código com teste alvo, 26 descartados
       com motivo (25 harness `src/test` + FP JEP 443 #876), #938 da tooling
       lane. O portão agora é por BASELINE (`scripts/codeql-baseline.txt`:
       só alerta NOVO bloqueia; skip exige motivo e deixa rastro; ignorado
       em CI) — `scripts/codeql-gate.sh --fast` já mede VERDE sem skip
       (rc=0). Resta para marcar [x]: ff da `q555` + primeiro re-scan fechar
       os 14 `open` tolerados por id no baseline (podar as linhas então) e
       #563 (família src/test no CI) seguir na fila própria.
3. [ ] Bump de versão: **DECIDIDO 20/09/2026 — o release sai como
       `0.5.0-beta`** (sufixo beta mantido, sem codename; adendo do
       `D-RELEASE-0.5.0-GATE`). `VERSION`/`pom.xml` já estão em `0.5.0-beta`;
       restam só o CHANGELOG/tag. Conferir referências à versão codificadas
       (javadoc/testes citam a versão do artefato) ANTES do bump; nunca edição
       unilateral. **Auditado 21/09 (9093): limpo** — os hits `0.4.0`
       restantes são comentários de procedência (quando um port pousou) e
       `beta-0.4.0` usado como *nome de branch* pelo `codeql-gate.sh`
       (monitora as duas) e por fixtures de teste; nenhum codifica a versão do
       artefato.
4. [~] Corte do CHANGELOG (EN+PT): seção `0.5.0` reunindo os bullets não
       lançados; cabeçalho `Version:` do `AGENTS.md`(+PT) atualizado no
       mesmo commit. (As lanes podem redigir já; o corte segue esperando as
       sete condições.) **DRAFT LANDADO 22/09 (sessão 9092):** o cabeçalho da
       seção unreleased agora é `## [0.5.0-beta] - unreleased (branch
       beta-0.5.0)` (EN+PT — `VERSION` já é `0.5.0-beta`; o cabeçalho do
       `AGENTS.md`(+PT) já foi bumpado pelo `D-VERSION-BUMP-0.5.0`). O CORTE
       em si (datar a seção + tag) segue esperando as sete condições.
5. [ ] Tally: `'Current build: **N**'` em `docs/backend-parity.md`(+PT) a
       partir da primeira CI Build+Tests hospedada VERDE no tip da release
       (contagem do log do job, nunca memória).
6. [ ] Prova de estabilidade: suíte completa 0F/0E + matriz de conformidade
       5/5 MEDIDOS no candidato à tag (AGENTS §Estabilidade — tag só com
       verde).
7. [ ] Tag + release notes (EN+PT); declarar `beta-0.4.0` fechada exceto
       pela lista de fixes residuais. **O `main` fica congelado até este
       release** (mantenedora 20/09/2026): os 12 alertas CodeQL pré-fix do
       `main` são portados no dia do release, não antes; o gate mede a
       `beta-0.5.0`.

## Issues abertas que viajam para `beta-0.5.0`

#555 (guarda-chuva CodeQL — o único ainda aberto; **#550/#553/#554 pousaram
20/09** com suas seções ✅ FIXED). Avisadas em cada issue e pelo banner no
`DOING.md`(+PT).

## Gate de release (`D-RELEASE-0.5.0-GATE`, 20/09/2026, diretiva da mantenedora)

O release 0.5.0 só é cortado quando **todas as sete condições** valerem, cada
uma **medida** (nunca a olho). Este gate refina o checklist acima: o checklist
é a fila tática, estas sete são a aceitação. A diretiva da mantenedora é a
prioridade para "liberar o gate 0.5.0 para todos os agentes".

| # | Condição | Como é medida | Estado 21/09 (medido — nunca a olho) |
|---|---|---|---|
| 1 | Paridade 100% entre os alvos | matriz por alvo + paridade byte dos goldens onde o contrato exige; divergência = bug ou gap `XXX00x`. **Medida automaticamente** pelo `check_release_050_gate.sh` (roda `scripts/target-matrix.sh`, EG-5, e lê a linha `PARITY: 100%`) | GREEN (medido 21/09: `PARITY: 100%` em jvm/x86-64/riscv64/aarch64/JS/Script vs o oráculo JVM; o jar da árvore foi reconstruído por `scripts/build-kof-jar.sh`, que também o estampa — um rebase não finge mais "velho"; num host sem root o toolchain cross (binutils/qemu/libc) é montado por `scripts/setup-cross-toolchain.sh`, que extrai os `.deb`s para um prefixo local e aponta `KOF_CROSS_SYSROOT` para ele). **Re-medido 21/09 no tip `c8d62388`: DE VOLTA A GREEN** — `scripts/build-kof-jar.sh` (reconstrói + estampa o `lib/kof.jar` pelo conteúdo da fonte) e então `scripts/target-matrix.sh` sob o env do `scripts/setup-cross-toolchain.sh --export` (`PATH`/`LD_LIBRARY_PATH`/`KOF_CROSS_SYSROOT`) → `PARITY: 100%` (jvm/x86_64/riscv64/aarch64/js/script byte-a-byte vs o oráculo JVM; kofc=EG-9, android=EG-10 delegados). O NEEDS-MEASURE anterior no mesmo dia (pousos makealive 3.2 `966c86a4`, §422 `9d97b268`, …) é a regra da própria condição, re-instaurada no corte pela condição 6 — **não uma regressão de paridade**. Re-medido 21/09 em `29198ea8` (após mudar `KofBuffer.gapCode`, jar reconstruído): `PARITY: 100%` de novo |
| 2 | Nenhuma decisão pendente | `DECISIONS.md` sem pergunta aberta que mude a superfície | NEEDS-REVIEW (**não RED**) — **2** frentes aprovadas com `State: OPEN` em curso (`D-TYPE-VARIANCE`/X5, `D-INTEROP-REFLECT`/X6), sincronizado 22/09 com o gate (`decisions` nomeia exatamente estas duas); `D-SECRETS` agora é `DECIDED` (todas as faces landadas 21/09, `04473bbe`) e saiu desta linha. Pela condição 2 de `D-RELEASE-0.5.0-GATE`, frente aprovada ABERTA não bloqueia o corte e nunca é "nada espera" |
| 3 | Todos os `docs/development/*.md` soltos concluídos e movidos | regra dos três estados; só fica trabalho com implementação pendente | **GREEN (21/09, `D-RELEASE-0.5.0-SCOPE`)** — os planos em voo com dono ainda soltos entram no allowlist e não barram o corte 0.5.0: `ffi-abi-structs` [jonas], `db-parity-plan` [lane gaps-db]; cada um mantém dono/fila (README sec.1). `makealive-plan`, `secrets-plan` e `IMPLEMENTATION-UNIVERSAL-PLATFORM` MOVIDOS 21/09 — concluídos; `type-system-extensions-plan` (X5+X6) MOVIDO 22/09 — concluído. `loose_docs GREEN` medido |
| 4 | Estabilidade total | suíte completa 0F/0E + matriz 5/5 na candidata. **Medida automaticamente** a partir de um log real da suíte via `scripts/stability-report.sh` (`KOF_SUITE_LOG=…`); o GREEN exige o `TOTAL` 0F/0E **e** o log **estampado** (`SUITE-SHA` == tip, `SUITE-DIRTY=0`) — log de outro commit ou de árvore suja é `unknown`, nunca verde falso | GREEN (medido 21/09 em `67b087f9`): corrida completa nova do `safe-suite.sh` — `TOTAL: tests=3440 failures=0 errors=0 skipped=225`, `SUITE-SHA`==tip, `SUITE-DIRTY=0`, e o `stability-report.sh` devolveu GREEN a partir do log estampado. O RED anterior em `96af9b63` (`tests=3413 failures=1`) era o **teste stale do §422** `CompilerDriverTest#externProducesHonestGapNotSilentDrop`, que ainda exigia `FFI001` para um extern `Int[]` que o `7c6413d4` (D6-2) tornou bindável no JVM de propósito — **RESOLVIDO 21/09 pela frente FFI**: `7c89f531` reapontou a asserção para assinaturas realmente não-ligadas (`String[]`/`List<Int>`→`FFI001`, `Buffer(Int)`→`SEM096`) e `9d97b268` fechou o §422 (`CompilerDriverTest` 259/0F/0E), então o vermelho stale sumiu. A baseline `8f459b8e` (`tests=3355`, primeiro log autocertificável — o `safe-suite.sh` antigo imprimia o `TOTAL` só no console e nunca o anexava, então NENHUMA corrida estampada podia ser certificada até o conserto do mecanismo) segue como história. **Re-medido 21/09 em `29198ea8` (tip): `TOTAL: tests=3479 failures=0 errors=0 skipped=13`, `SUITE-SHA==tip`, `stability-report.sh` GREEN** — as 3 falhas vistas no candidato §431 (`3472/3F`) eram ratchets stale da FFI JS agora reconciliados (`KofBuffer.gapCode` gap JS, `InteropIdiomsCompileTest` bridge JS, `ArtifactSizeTest` `HELLO_JS_BYTES` 13.007→13.834 KB), então aquele vermelho sumiu. O candidato ainda exige re-medição no tip final limpo na hora do corte — isso é a regra da condição 6, não dúvida desta linha |
| 5 | 0 issues abertas que sejam bug | issues OPEN do GitHub com label `bug` = 0 | GREEN (0 issues de bug abertas; a condição lê o **rc da consulta** — API fora = `UNKNOWN`, nunca GREEN; num host sem `gh`, `scripts/fetch-open-issues.sh` fornece o `R050_OPEN_ISSUES_TSV` pela API pública — medido 21/09: 0 issues de bug abertas, a #580 é documentation/enhancement) |
| 6 | Todas as arestas fechadas | EG-1..EG-7 fechadas + `1.0-blocks` abertos = 0; **EG-8 desacoplado** (`D-RELEASE-0.5.0-SCOPE`) — é o corte do RC 1.0 + a declaração da mantenedora, só depois de EG-1..EG-7 | **GREEN (21/09, `R050_OPEN_BLOCKS=0` medido via `gh` autenticado: os únicos 2 itens abertos são PRs do dependabot, 0 issues)** — tabela EG ilegível = `UNKNOWN`, nunca GREEN; enumeração de `1.0-blocks` que falha deixa essa parte `UNKNOWN`, nunca um 0 implícito |
| 7 | Nada pendente em bugs-and-gaps | conjunto live do `check_known_bugs_status.sh` vazio + `specification-gaps.md` 0 abertos | RED (3 live no tip — **26/09: lane compiler 9092 catalogou §511** (a flake de boot OVMF do `RingPrivilegeE2ETest` sob carga da suíte completa — re-incidência ≥2 com evidência controlada por stash; dona = lane baremetal) — 2→3; **26/09: lane compiler 9092 FECHOU §500 (fatia B — campos estáticos via `KofGetStatic` real; RED→GREEN `ExternalStaticFieldE2ETest` 8/8, goldens medidos na JVM nua; a face da escrita é `SEM025`, não COMP002; reactor 4099 0F/0E)** — 3→2; **26/09: §508 CORRIGIDO no mesmo dia pela dona (lane baremetal)**; o heartbeat havia catalogado o §508 (ERRO do CodeQL só-do-modo-PR em `RuntimeDtoaSchubfach.java:90` — FP provável: o comprimento de `gTable()` (1234) é par, o passo 2 nunca toca `length`; os gates da branch leem os alertas do modo push e não são afetados; a ação dismiss-ou-guard foi registrada para a dona, lane baremetal) — 3→4; **26/09: lane compiler 9092 catalogou §500** (método/campo estático em nome de classe externa importada que não resolve emite `invokevirtual "".bogus` — até o válido varargs `Arrays.asList` é afetado, `ExternalClasspath` sem `ACC_VARARGS`; exige unidade dedicada) — 2→3; **26/09: lane compiler 9092 CORRIGIU §499** (método estático desconhecido em nome de tipo builtin — agora `SEM074`; RED→GREEN `BuiltinUnknownMethodGuardTest` 14/14; suíte 4016 0F/0E) — 3→2; **26/09: lane compiler 9092 catalogou §499** (método estático desconhecido em nome de tipo builtin — `String.bogus()`/`Int.bogus()` compilam limpo e emitem `invokestatic <Owner>.bogus` → `NoSuchMethodError`; exige whitelist de interop curada compartilhada typer+lowerer, regra 6/11) — 2→3; **26/09: lane native-cross FECHOU §497** (`delete` recursivo, `modifiedTime`, `isSymlink`, `moveTo`, `copyTo` no x86-64 + riscv64/aarch64; `NAT006` fechado) — 4→3; **26/09: lane native-cross catalogou §497** (gaps nativos do `kof.io`: `Directory.delete` não recursivo no x86-64 + sem `copyTo`/`moveTo`/`modifiedTime`/`isSymlink` nativo) — 3→4; **25/09: lane compiler 9092 CORRIGIU §496** (campo desconhecido em qualquer namespace builtin agora SEM102) — 3→2; (**24/09: lane gaps-db catalogou §493** (JVM e Native divergem no caminho de erro do `orm.delete`/`deleteAll` MySQL — a JVM lança string de SQLException, o Native x86-64/cross devolvem `true`; decisão de contrato) — 0→1; **24/09: lane compiler 9092 CORRIGIU §488** (x86 `RuntimeDb5`: NULL → literal `null`, `len==0` → `kof_json_encode_string`; E2E `nativeMysqlNullAndEmptyStringJson` paridade com o oráculo JVM, RED pré-fix `{"n":,}`) — 1→0; **24/09: lane gaps-db catalogou §488** (caminho de texto MySQL do x86 emite NULL como string vazia crua → JSON inválido `{"n":,`, e string vazia como número cru; `RuntimeDb5 .Ldb_mysql_null`; fix de raiz pendente) — 0→1; **24/09: lane compiler 9092 CORRIGIU §278 face gpu** (Android compila `kof.gpu` como o JVM — `Main.class` byte-a-byte; runtime = `JvmVkStubRuntime`, sem FFM no ART) — 1→0; **23/09: lane 9092 CORRIGIU §485** (deref NULL no drena-e-envia do canal — `tail` obsoleto; `channelDrainThenSendNative` x86+riscv64+aarch64) — 2→1; **23/09: lane compiler 9092 CORRIGIU §486 face (b) `43f2833a`** (mangling do bridge com sufixo do retorno — bridge×concreto não colidem mais; `NativeGenericIfaceBridgeE2ETest` agora prova as DUAS faces 3/3 x86+riscv64+aarch64) **e §487 `374b2b4bb`** (issue #610 — diamante de default agora `SEM101` no compile; overload por aridade entre interfaces corrigido) — 3→2; **23/09: lane compiler 9092 CORRIGIU §486 face (a)** (bridge de retorno referência no Native pulado — pass-through de registrador; `NativeGenericIfaceBridgeE2ETest` 3/3 x86+riscv64+aarch64) **e catalogou §486 face (b)** (bridge de retorno primitivo colide num único símbolo asm; exige mangling ciente do retorno; arquivos da lane baremetal) — 2→3; **23/09: lane compiler 9092 CORRIGIU §205** (print de valor tipado `Object` no Native — record/classe agora despacha `toString` via `kof_tostring_table[type_id]`; §205 fatia 2 = N2/ABI de caixa com tag; `NativeObjectBoxPrintE2ETest` 3/3 x86+riscv64+aarch64) — 2→1; **23/09: sessão 9092 CORRIGIU §483 + §271 + §248** (dispatch de interface genérica no Native + defaults de interface no Native/JS; `GenericInterfaceAssignabilityTest` 10/10, `InterfaceDefaultMethodE2ETest` 7/7 nos 4 alvos) — 5→2; **23/09: lane 9093 CORRIGIU §476 + §478 (#587 break-in-case) — 5→4**; **23/09: lane 9092 baremetal catalogou §476** (lista mista de `case` padrão+valor + `default:` vazio → `VerifyError` na JVM) — 4→5; **23/09: lane 9092 baremetal CORRIGIU §423** (canais cross portados — `kof_channel_*` na fatia `RtB61`; gate NAT005 removido; paridade qemu nas duas arches) — 5→4; **23/09: lane 9092 baremetal CORRIGIU §448** (dtoa Schubfach cross riscv64/aarch64 `Double`+`Float`) — 6→5; **23/09: lane 9093 nat CORRIGIU §444** (ramo TypeVariable no dispatcher `valueOf` x86) — 7→6; **22/09: sessão 9093 (lane typer) FECHOU §280** (53/91 sites com posição real; `DiagnosticSourceLocationTest` 5/5) — 8→7; **22/09: lane 9093 corrigiu o §442** (split `NewExpr` → `SemNewExprTyper`; `check_500` rc=0, suíte 3590 0F/0E @ `fd5119f69`) — 9→8; 22/09: lane 9093 corrigiu o **§268** (`D-RULE6-BATCH` (A): probe de `java.lang` com cache + SEM087) + o **§288** (`D-RULE6-BATCH` (b): `TypeVariable` em parse-time, fonte única + rejeição interina SEM085 para tipos-função com T do dono, `FnTypeVarSignatureE2ETest` 9/9) e catalogou o **§444** (gap nativo pré-existente — conjunto moveu §288 para fora / §444 para dentro, líquido 9); o tip mesclado 11→10 cobre o **§302** fechado (CLOSEALL) + **§442** catalogado; 21/09: (14→12 após o lote CLOSEALL da mantenedora fechar §334/§400) após o fix S0 do §421 + §423–§431 catalogados, §431 fechado pela lane `.18`, §429 corrigido (LSP `-32601`), §435 fechado pela lane `.18` (check_500 `LspServer` 601→582, split em `LspJsonRpc`), §432 corrigido (VerifyError do `getOrDefault` JVM), §428 corrigido (requests não implementadas do DAP agora falham honestamente), §425 corrigido (`kof.config` no riscv64/aarch64 agora `CONF001` honesto), §426 corrigido (`time.collect()` no JS agora `TIME004`), §427 corrigido (`kof.io`/web-T1 no riscv64/aarch64 agora `NAT006`/`NAT007`) e §424 corrigido (cinco métodos `String` no JS/Native agora `STR003` honesto), §437 fechado (o check_500 vermelho já não existia — `JvmOpCollections` 594 < 600 após o cleanup de código morto do §432, sem split) e §438 corrigido (shutdown hook do `KofDebugJvmSession` + teardown dos testes que mata a árvore, para nenhum debuggee/`/tmp/kof-debug-*` sobreviver a uma sessão); o script é a autoridade; ledger ilegível = `UNKNOWN`, nunca GREEN) |

| 8 | **Paridade total da plataforma (IMPEDITIVA, `D-FULL-PARITY-050` 24/09)** | `docs/development/parity/PARITY-GAPS.pt_BR.md`(+EN) com **0 linhas abertas** — verificação por máquina: `check_release_050_gate.sh` → `full_parity` (ledger ausente/ilegível = UNKNOWN, nunca GREEN); toda face de namespace compila E roda byte/golden vs o oráculo JVM em JVM/Script, Native x86-64, Native riscv64/aarch64 e JS | **RED (24/09, criado medido)** — 16 linhas abertas: process/shell `PROC001`; ssh (código a catalogar); media `MEDIA001/003`; mq `MQ001`; gpu JS+golden `GPU001`; observability golden `OBS003`; time cross `TIME002/004`; cache/config/log golden cross + log interpretador `CONF001`; `math.pow` cross `MATH001`; strings `NAT-STR01`/`STR003`; web T1 native `WEB00x`; kof.io cross `NAT006/007`; security cross `SECN001/003/004/005`; orm nativo `ORM001`; db nativo query/prepared `DB001`. A condição 1 (matriz de conformidade) permanece; esta é a cauda longa que a matriz nunca cobriu — golden não medido = ABERTO (Q5) |

Mecanizado por `scripts/check_release_050_gate.sh` (reporta cada condição como
GREEN / RED / NEEDS-MEASURE / UNKNOWN; teste RED-first
`scripts/tests/check-release-050-gate-test.sh`). Toda condição data-driven
**recusa GREEN quando a fonte não é legível** — jar velho, log da suíte de outro
commit ou de árvore suja, consulta ao GitHub que falha, tabela EG impossível de
parsear, ledger de bugs ilegível, fonte de decisão ou lista de docs soltos
ausente: as sete condições ficam inconclusivas, nunca verde falso.
RED é esperado até a fila fechar — o gate é o motor, não um bloqueio a contornar.

### Recuperação — limpar as condições auto-medidas

```bash
eval "$(scripts/setup-cross-toolchain.sh --export)"     # cond. 1: binutils/qemu/libc cross (host sem root; uma vez)
scripts/build-kof-jar.sh                                # cond. 1: rebuilda + estampa o jar da árvore (após o último commit do compiler)
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 alvos core)
scripts/fetch-open-issues.sh > /tmp/open-issues.tsv     # cond. 5: quando o `gh` não existe (API pública)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: rodar em árvore LIMPA
R050_OPEN_ISSUES_TSV=/tmp/open-issues.tsv \
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
