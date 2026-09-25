[English](PARITY-GAPS.md) | [Português](PARITY-GAPS.pt_BR.md)

# Ledger de paridade total — impeditivo da 0.5.0 (mantenedora 24/09)

> **MANDATO (mantenedora, 24/09): paridade total da plataforma é
> INDISPENSÁVEL para a release 0.5.0.** Um código de gap honesto (`PROC001`,
> `MEDIA001`, ...) é o jeito sancionado de RASTREAR uma face ausente — nunca o
> estado final sancionado. Cada linha abaixo é uma funcionalidade que hoje
> funciona em ALGUNS alvos e é gap nomeado/diagnosticado em outros. **A
> `0.5.0` não corta enquanto este ledger tiver QUALQUER linha aberta.**
>
> Regra do ledger (três estados, verificado por máquina via
> `check_release_050_gate.sh` → `full_parity`):
> - uma linha sai SOMENTE quando a funcionalidade compila E roda com paridade
>   byte/golden em TODOS os alvos (teste de prova nomeado, runner registrado);
> - fechos parciais movem as células de alvo da linha (nunca marcar uma linha
>   DONE parcialmente);
> - o ledger VAZIO (sem linhas) é o estado GREEN — `docs/development/parity/`
>   permanece (o contrato + a história), o corpo do arquivo encolhe para
>   "0 linhas abertas".
>
> "⏳ golden" = a face existe mas o golden cross (diff byte riscv64/aarch64 vs
> oráculo JVM) nunca foi medido — não medido NÃO é verde (Q5: sem falso verde).

## Linhas abertas (medidas 24/09 de `DomainGapCodesTest`, códigos de gap dos `Kof*.java`, tabela de paridade do `training/idioms/stdlib.md`, `docs/bugs-and-gaps/known-bugs.md`)

| # | Superfície | JVM/Script | Native x86-64 | Native riscv64/aarch64 | JS | Código | Fila / lane dona |
|---|------------|------------|----------------|--------------------------|----|--------|------------------|
| 1 | `process.run`/`spawn`/`exit` | ✅ | ✅ x86 `run`/`exit`/`spawn` 26/09 (whole-record `println(r)`/`"x"+r` = `PROC001`, acesso `.stdout`/`.stderr`/`.exitCode`; handle: `readLine`/`write`/`exitCode`/`kill`/`alive`) | ✅ cross `run` 26/09 (`spawn` = `PROC001`) | ✅ (KofJsRunner) | `PROC001` (cross `spawn` + whole-record print) | lane native-cross (`run` x86 ✅ 25/09, cross ✅ 26/09; `spawn` x86 ✅ 26/09) |
| 3 | `ssh.cmd`/`run`/`ok` | ✅ | ✅ x86 + riscv64/aarch64 26/09 | ❌ | ❌ | `PROC001` (MCU/riscv32; JS sem dispatch) | lane native-cross (nativo ✅ 26/09) |
| 4 | media: `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record`/`list` | ✅ | ❌ | ❌ | ❌ | `MEDIA001`/`MEDIA003` | frente media |
| 10 | `math.pow` cross (estático, sem libc) | ✅ | ✅ (libm `-lm`) | ❌ `MATH001` | ✅ | `MATH001` | lane native cross |
| 11 | `strings.reverse` não-ASCII (UTF-16 vs byte) + `String.matches`/`replaceAll`/`replaceFirst`/`compareToIgnoreCase` | ✅ | ❌ `NAT-STR01`/`STR003` | ❌ `STR003` | ❌ `STR003` | `NAT-STR01`/`STR003` | lanes native/js |
| 12 | web T1 (faces do `kof.http.server`) no native/cross | ✅ | ⏳ | ❌ `WEB002`–`WEB006` | ✅ | `WEB00x` | lane web |
| 13 | faces de arquivo do `kof.io` no cross | ✅ | ✅ x86 | 🟡 parcial — estat+texto+fs (`exists`/`isFile`/`isDirectory`/`readText`/`writeText`/`appendText`/`delete`/`create`/`createDirectories`/`mkdirs`/`size`/`readBytes`/`writeBytes`/`appendBytes`/`list`/`readRange`/`name`/`path_fileName`/`path_parent`/`path_extension`/`path_isAbsolute`/`path_resolve`/`path_normalize`/`path_toAbsolute`) ✅ (`NativeRiscvAsmIoStat`/`IoText`/`IoFs`/`IoMkdirs`/`IoSize`/`IoBytes`/`IoDirList`/`IoReadRange`/`IoPath`/`IoResolve`/`IoNormalize`/`IoToAbsolute`/`IoDirDelete` — `delete` recursivo ✅ cross **e x86-64** 26/09; `modifiedTime`+`isSymlink` ✅ cross+x86-64 via `NativeRiscvAsmIoMeta`/`RuntimeIoMeta`; `moveTo` ✅ todos os nativos via `RuntimeIoMove`/`NativeRiscvAsmIoMove` (`renameat2`); `copyTo` ✅ todos os nativos via `RuntimeIoCopy`/`NativeRiscvAsmIoCopy` → **linha 13 COMPLETA, sem face `NAT006`** (§497 FIXADA); `size()` msg JVM×x86 divergem (§494); msg de `size()` JVM×x86 diverge (§494) | ✅ | `NAT006`/`NAT007` | lane native cross |
| 14 | família security no cross/native (faces bcrypt/argon2/keystore) | ✅ | parcial | ❌ `SECN001`/`003`/`004`/`005` | ⏳ | `SECN00x` | lane security |

> **As linhas 15 (`orm.*` nativo) e 16 (`db.*` nativo) foram FECHADAS em 24/09
> pela lane gaps-db (S5.5)** — o cross (riscv64/aarch64) eram as últimas células
> abertas; as provas estão na seção Fechadas.

## Já em paridade total (verificado — a revisão do que está FEITO, 24/09)

Medido 4/4 (JVM/Script ≡ Native x86-64 ≡ Native riscv64/aarch64 ≡ JS),
prova = casos std* do `ConformanceMatrixTest` + o E2E nomeado por linha:

- **Núcleo da linguagem:** operadores/precedência, `==` por conteúdo,
  exceções como String, estreitamento null-safe, expressões `if`/`switch`,
  pattern matching, closures, `spawn`/`await`/`poll`/`done`/`cancel`/
  `selectAny`/`awaitTimeout`, canais incl. riscv64/aarch64 (§423, §485),
  métodos default de interface (§248), interfaces genéricas + bridges
  covariante/primitiva (§355–357, §486), igualdade/hashCode de record por
  conteúdo em todo lugar (§104b-ii/§114), diagnósticos SEM.
- **Núcleo da stdlib (linhas da tabela de paridade com ✅ nas 4 colunas):**
  `math` (abs/sign/clamp/min/max/is*/sqrt/lerp/percentage/roundTo/parse*) —
  EXCETO `pow` (linha 10); `strings` (is*, count, capitalize/uncapitalize,
  reverse ASCII, toCamel/Pascal/Snake/Kebab, slugify, escapeHtml/Json,
  família de whitespace, dedent, repeat, truncate, indent, pad*) — EXCETO
  `reverse` não-ASCII (linha 11); `encoding` (hex, base64, base64Url, url);
  `net` (todos os 8); `uuid` (v4, v7, isUuid); `random` (todas as faces);
  `time` núcleo de calendário civil (isLeapYear, daysInMonth, dayOfWeek,
  daysBetween, isWeekend, isToday) — EXCETO o golden cross das faces novas
  (linhas 8/9); `validation` (docs BR, rede, cartão — todas as faces).
- **Coleções** (`List`/`Set`/`Map` API completa incl. `getOrDefault`,
  `putIfAbsent`, sort/indexOf/subList) — 4 alvos.
- **`json.encode`/`decode<T>`** — 4 alvos (o Native compõe em compile-time).
- **`kof.ui`** — cores/widgets/janelas nos 4 alvos (regra: a plataforma
  renderiza).
- **`String.toCharArray`** (linha 11, portado 24/09) — 4 alvos; array de code
  units UTF-16 (astral = high/low surrogate), paridade byte-a-byte
  JVM/x86/cross. Prova: `KofStringsTest#toCharArrayJvmJsNative` (JVM/JS/x86) +
  `NativeStringToCharArrayCrossTest` (riscv64/aarch64 sob qemu).

Uma entrada aqui só SAI quando a prova é nomeada; o runner
`ConformanceMatrixTest` re-executa a cada passagem do gate de release
(condição 1), então uma regressão REABRE a linha (zero regressão, regra 1
do freeze).

## Fechados (prova registrada aqui quando a linha esvazia)

- **Linha 2 — `shell.cmd`/`run`/`runWith`/`pipeline`/`ok` (as 5 faces, os 4 alvos)** —
  fechada 25/09 (lane paridade + lane native-cross). x86-64: fatia A pousou
  `run`/`cmd`/`ok` (25/09), fatia B pousou `runWith` (contrato pleno: split do
  argv + chdir + `setenv` aditivo no hook do filho; contexto na pilha do
  wrapper — o fork copia, o scan de pilha do GC enraíza) e `pipeline`
  (`kof_shell_pipeline`: encadeamento de pipes do kernel, stdin `/dev/null` no
  estágio 0, captura só do ÚLTIMO estágio, exitCode = último; oráculo JVM
  `JvmRuntimeCore:446`). Cross: `run` (linha 1 fatia C), `cmd`/`ok` (B2),
  `runWith` (B1: cwd/env herdados byte-parity, não-vazio = `Result` honesto),
  `pipeline` (B2 cadeia de kernel) — tudo da lane native-cross 26/09.
  Micro-divergências declaradas (R7, no cabeçalho do runtime + fixadas aqui):
  o `pipeline` nativo limita a 64 estágios com `Result` honesto (o JVM não tem
  cap) e o stderr intermediário vai a `/dev/null` (o JVM pipa-e-nunca-lê, o que
  deadlocka acima de 64 KiB — intestável por contrato). A fatia runWith x86
  também corrigiu o §503 (`.quad` desalinhado em `.data` = raiz estática
  invisível ao GC → sweep liberava o buffer de dreno de 1 MiB vivo →
  OUT==ERR==CHUNK; `.balign 8` agora é obrigatório em todo global de runtime
  que guarda ponteiro de heap). Prova: `ShellE2ETest`
  `pipelineOnNativeMatchesJvmGolden`/`pipelineHonestFailuresOnNative`/
  `runWithOnNativeMatchesJvmGolden`/`runWithHonestFailuresOnNative` (JVM ==
  nativo byte a byte), `ShellCrossE2ETest` 7/7, `ProcessRun*E2ETest` 6/6+6/6,
  bateria T1–T14; nenhuma face do shell segue gateada nos alvos do ledger.
- **Linha 6 — `gpu.*` (face JS) + golden cross** — fechada 26/09 (lane parity).
  A linha escondia um gap REAL, não um golden stale: `KofGpu.supportedOn` já
  devolvia true para os alvos nativos, mas o caminho de emissão riscv64/aarch64
  nunca emitia runtime `kof_vk_*`/`kof_mv64_*`, então `gpu.available()` falhava no
  **link** (`ld: undefined reference to 'kof_vk_available'`). O novo
  `NativeRiscvAsmGpu` define as 13 entry points com o contrato de fallback
  honesto do `JvmVkStubRuntime` (available=false, dispatch −1, as faces mv
  `32`/`sp` −6, `failReason` um KofString real), e JS/Script deixaram de ser
  gateados por `GPU001` (lacuna declarada é estado de rastreio, nunca de
  aceitação, `D-FULL-PARITY-050`): `KofGpu.supportedOn` agora devolve true em
  todo alvo, o JS ganha `JsRuntimeGpuSupport` (exports de fallback camelCase
  ligados ao slicer do runtime) e o Script degrada pelo runtime FFM real do
  interpretador (available=false/dispatch −1 sem `libvkchain.so`). Prova:
  `KofGpuCrossTest` **4/4** (riscv64 + aarch64 sob qemu, runner JS embutido,
  interpretador Script) + `ConformanceMatrixTest` 14/14 + `KofJsE2ETest` 40/40;
  `StdParityGapAuditTest#gpuUngatedOnAllTargets` trava a remoção do `GPU001`.
- **Linha 5 — `mq.*` golden cross + JS (riscv64/aarch64)** — fechada 26/09
  (lane parity). A linha estava STALE: `KofMq.supportedOn` devolve `true` para
  todo alvo (o código `MQ001` é registro retido, não gate vivo —
  `StdParityGapAuditTest` afirma que o conjunto de não-suportados é vazio) e o
  golden cross já executava (`KofMqE2ETest#crossNativeMqQueueAndPubsub`:
  queue/push/pop/queueSize + pub/sub + unsubscribe em riscv64 **e** aarch64 sob
  qemu, byte-paridade com x86-64). A única face sem prova nomeada era a
  **fila no JS** (o teste existente cobria só pub/sub JS), agora adicionada:
  `KofMqE2ETest#jsQueuePushPopAndSize` afirma `2\njob-1\njob-2\nnull` no runner
  GraalJS embutido. Prova: `KofMqE2ETest` **6/6, 0 skipped**
  (JVM/x86/riscv64/aarch64/JS).
- **Linha 7 — `observability.*` golden cross + `OBS003`** — fechada 26/09
  (lane parity). As células cross estavam STALE/parcialmente medidas: o golden
  de span/trace/request-id já executava sob qemu
  (`KofObservabilityTest#spansCrossArchRiscv64`/`#spansCrossArchAarch64`), e a
  última célula aberta era o **golden de métricas/health no cross**. Estendido
  o `runCross` (`KofObservabilityTest`) com o contrato dos 4 alvos:
  `health()=="UP"`, `readiness()`/`liveness()`, `counter`/`increment`
  (`1`/`2`/`7`), `gauge 99`, `histogram` (`_count 2`/`_sum 25`) — medido
  byte a byte em riscv64 **e** aarch64 sob qemu (2/2, `x-ok`). `OBS003`
  (`observability.exportSpans` no native) segue um gap **declarado, intencional**
  R7 JVM-first — travado por
  `StdParityGapAuditTest#observabilityExportSpansGatedOnNative` +
  `DomainGapCodesTest` (OBS003), nunca um stub silencioso. Prova:
  `KofObservabilityTest` (golden JVM/x86/JS/cross) + `KofJsE2ETest`.
- **Linha 9 — `cache.*`/`config.*`/`log.*` cross (riscv64/aarch64)** — fechada
  26/09 (lane parity). Três faces cross estavam abertas: **`cache`** —
  `cache.ttl` devolvia 0 para chave ausente/sem-TTL/expirada onde o oracle
  JVM/x86 devolve -1 (`NativeRiscvAsmRtB2` `.Lct_miss`; §501), corrigido com
  `KofCacheCrossTest` (4/4); **`log`** — o cross não tinha o interpretador de
  nível nem o contrato de linha JVM (fatia 2a: `.Llog_parse_level` lê
  `KOF_LOG_LEVEL`, threshold lazy, rótulos JVM `DEBUG`/`INFO`/`WARN`/`ERROR`) e
  nem o timestamp UTC `yyyy-MM-dd HH:mm:ss.SSS` (fatia 2b: `.Llog_format_ts`
  porta a conversão civil Hinnant + `kof_time_now()`), com `NativeLogCrossTest`
  (7/7); **`config`** — a última face, recusada em compile-time `CONF001`: agora
  é runtime cross real (`NativeRiscvAsmConfig1/2/3` — `kof_env_getc`
  /`/proc/self/environ`, find de arquivo `chave=valor`, lookup `KOF_CONFIG` →
  env `KOF_<KEY>` → perfil `kof.<KOF_PROFILE>.config`/`kof.config`, interpolação
  `${key}`, wrappers tipados `get`/`env`/`has`/`str`/`int`/`long`/`bool`/
  `required`), com `KofConfigCrossTest` (3/3, env+arquivo+perfil+interpolação+
  panic) e o gate `CONF001` removido (`KofConfig.supportedOn` true;
  `DomainGapCodesTest` `configOnCrossHasNoGap`). Prova: os três testes cross
  14/14 + as suítes E2E dos 4 alvos
  (`NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 110/110, 2 skips de ambiente) +
  `KofCacheE2ETest`/`KofLogE2ETest`/`KofConfigE2ETest`/`NativeConfigE2ETest`.
- **Linha 8 — `time.*` (faces novas + `addDays`/`diffDays` + `collect`)** —
  fechada 25/09 (lane parity). A célula cross estava OBSOLETA:
  `addDays`/`diffDays` já tinham sido portados para riscv64/aarch64 em 11/09
  (`NativeRiscvAsmRtB33`, fatia B33, reusando `kdv_valid`/`kdv_epoch`;
  `aarch64` via tradutor), e as "faces novas"
  (`todayIso`/`formatDateIso`/`isToday`/`hoursBetween`/`parseDateIso`)
  também. **Medido no tip:** `KofTimeE2ETest` **44/44 com 0 skip** sob a
  toolchain cross (os goldens `...CrossArch` existentes EXECUTAM, não pulam) +
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` stdtime2. A única célula real
  aberta era o `time.collect` no JS (`TIME004`) — e o gate da §426 era um
  fallback, não o estado final (`D-FULL-PARITY-050`: um gap honesto nunca é o
  estado final sancionado). Agora é uma face **real**:
  `kof_gc_collect_now` é reconhecido pelo `JsRuntimeOps.isRuntimeOp` (a raiz
  de fato do antigo `ReferenceError` — o emitter produzia uma chamada crua
  porque o mapeamento nunca era alcançado), o `JsRuntimeTime` exporta
  `kofGcCollectNow`, e o `KofJsRunner` expõe `kof_platform.gcCollect` →
  `System.gc()` (semântica exata do JVM/SCRIPT). Prova:
  `KofTimeE2ETest#collectJsRunsOnHost` (compila E roda) +
  `DomainGapCodesTest.collectOnJsHasNoGap`/`collectOnJvmAndX86HasNoGap` (com as
  arches cross) 25/25 + `KofJsE2ETest` 40/40.
- **Linha 15 — `orm.*` nativo (`kof_orm_*`)** — fechada 24/09 (lane gaps-db,
  S5.5). As 13 faces (`create`/`migrate`/`count`/`count_where`/`save`/
  `saveAll`/`find`/`all`/`where`/`where_op`/`page`/`delete`/`deleteAll`) rodam
  byte-idênticas em JVM + Native x86-64 + riscv64 + aarch64 sobre **os dois**
  SQLite e wire MySQL (os últimos gaps do cross — MySQL `save`/`saveAll`/
  `find`/`all`/`where`/`where_op`/`page` — landaram na S5.5, `RtB76`–`RtB81`,
  helpers `RtB78Helpers`/`RtB80Helpers`/`RtB81Helpers`); JS fechado 18/09
  (`KofJsOrmBridge`, mesmo SQL do `JvmOrmRuntime`). Prova: `KofOrmE2ETest`
  82/0F — incl. `crossNativeMariadb{Save,SaveAll,Find,All,Where,Page}MatchesOracles`
  (goldens byte JVM == x86-64 == riscv64 == aarch64) — + `NativeRiscvDbWireTest`
  41/0F + `NativeRiscvRuntimeSliceRegistryTest` 9/9.
- **Linha 16 — `db.*` paridade nativa query/prepared** — fechada 24/09 (lane
  gaps-db). O cross agora carrega o wire untyped completo (connect/handshake/
  COM_QUERY/execute/query/scalar, SQLite + MySQL, binds preparados — `RtB62`–
  `RtB73` + `RtB47b`); x86-64 real desde F1–F2; JS fechado 16/09 (DB001,
  `KofJsDbBridge`). Prova: `KofDbE2ETest` 40/0F + `NativeRiscvDbWireTest` 41/0F.

## Definition of done para TODA linha

1. A face compila no alvo (nenhum código de gap emitido).
2. Prova golden/E2E: saída byte-idêntica vs o oráculo JVM (cross sob qemu
   onde aplicável), teste nomeado no commit.
3. Tabela de paridade em `learn/39-stdlib.md`, `training/idioms/stdlib.md` e o
   capítulo do namespace sob `learn/stdlib/` atualizados no MESMO commit.
4. Linha removida deste ledger no MESMO commit + `full_parity` re-executado.
