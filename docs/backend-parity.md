# Backend Parity — Kof JVM × Native × KofJS

**Última atualização:** 3 de setembro de 2026
**Versão:** 0.2.6-beta

> Deltas desde 0.1.0: Targets `native.risc` (riscv64) e `native.arm` (aarch64) separados de `native` x86_64 (**core completo 02-03/09** — `NativeRiscv64E2ETest 13/13` + `NativeAarch64E2ETest 13/13` em asm puro + qemu, paridade avançada pendente; ver `docs/native-multiarch.md`); Native free-list (`kof_free_head`) + `kof_gc_collect` (mark-sweep pendente; auto-GC desativado); MySQL wire protocol em progresso (`kof_db_mysql_scramble` + `user:pass@`); pattern matching `switch case String s` + record destructuring `Point(x,y)` em JVM/Native/JS; `String?` null safety básica; `KofScript` top-level `let` → `KofScriptGlobals`; `KofCcompiler` (`kof c`) native-only C subset; `List map/filter/reduce` + `Box<T>`; Windows SIGPIPE fix.
> Deltas 30-31/08: `spawn`/`await` real no Native (pthread + trampoline + join + allocator thread-safe futex — CONC001); FP real em XMM (FLT001); JSON objetos/records + arrays FP no Native (JSN001/JSN002/JSN003); WebSocket/SSE no JVM (`app.ws`/`sse.*`, RFC 6455); `kof.http` retry/circuit JVM+JS (30s window, fail-fast); `kof.cache` 3 targets (fix de clobber de registradores); UI Fase 7 Router (JS real, JVM no-op); SQLite nativo via `.so` direto; `kof fmt` + `kof config gen`.
> Deltas 01/09: **`spawn` com lambda que captura variável externa** (`SpawnStmt` emitia a lambda com zero capturas → `VerifyError`/valor errado; agora coleta via `collectCaptures`); **`&&`/`||` booleanos short-circuitam no JS** (backend emitia `&`/`|` bitwise que avalia os dois lados; agora `&&`/`||` JS para `bool`, bitwise intacto); **`Channel<T>` como parâmetro de função** (tipo saía com package vazio e o `isChannel` exigia `kof.concurrent` → mapeado como builtin + `JvmTypeMapper` → `LinkedBlockingQueue`); **`println`/`print` antes de `spawn` no Native** e o bug pré-existente **`spawn→await→spawn`** (SIGSEGV no `pthread_create` — stack chegava desalinhada ao C call; alinhado com `andq $-16` preservando `r15` + frame); **KofJS no browser real** (`KofJsBrowserE2ETest` — Chrome headless + HTTP + DOM); **`SECN002` fechado — AES-256-GCM no KofJS** (AES-GCM puro JS: FIPS 197 key expansion + GCM CTR/GHASH, formato `aesgcm$iv$ctTag` idêntico ao JVM/Native; tamper detectado por decode base64 estrito `strict=true` — paridade cross-target JVM↔JS testada); **`OBS002` fechado — histogram/metrics no Native** (store asm 32B name+sum+count + export Prometheus via `kof_string_concat` — paridade de conteúdo com o JVM); **`TIME001` (Native) — `time.interval`/`time.cancel`** (mesmo mecanismo do `scheduler.every`/`cancel`, SCHED001: thread por job; alias `kof_time_interval`/`kof_time_cancel` → `jmp kof_scheduler_every`/`cancel`; mutação por referência da captura validada); **`TIME001` fechado no JS (02/09) — fila cooperativa de timers bombeada por `time.sleep`** (GraalJS não tem `setInterval`; browser/Node usam o nativo — `KofTimeE2ETest.jsIntervalRunsPeriodicallyUntilCancelled`); **`MQ001` fechado — kof.mq no Native** (pub/sub + filas in-process em asm: store `.bss` + nodes 40B, invoke-com-arg `rdi`=fn `rsi`=msg, unsubscribe por identidade do objeto fn — paridade de output JVM↔Native↔JS); **`Set<T>`/`Map<K,V>` como campo/retorno de classe no JVM** (o `JvmTypeMapper` mapeava só `List`→`ArrayList`, então `Set`/`Map` viravam `Lkof/Set;`/`Lkof/Map;` → `NoClassDefFoundError`; agora `HashSet`/`HashMap`; + parse de método de classe com retorno genérico `Set<Int> all(` — `KofMapSetTest.setMapAsFieldAndReturn`, 3 targets); **Query DSL tipada do ORM (nível 3, `ORM001`)** — `User.query(db){ where; orderBy; limit }` baixado para `db.query<T>` (SQL preparada em compile-time, valores como binds; `KofOrmE2ETest` 22); **source map V3 do KofJS** (mappings VLQ reais em nível de linha: cada função gerada → linha Kof via `KofDebugInfo` — antes era stub `"mappings":""`; `KofJsSourceMapTest`).
> Deltas 02/09: `Set<T>` como tipo declarado no JVM (`kof.Set` → HashSet); null-safety narrowing JVM corrigido (`s.length`/`s.substring(...)`/`if_acmp*`); `mapOf` infere o primeiro par; `String? s = null` prefixado parseia; `readText`/`readFile` → `String?` (Native devolve `null`); `size()` lança em vez de `-1`; `Map.get` → `V?` (referências); gaps `STR001`/`STR002` documentados abaixo.
> Deltas 03/09 (main): **aarch64 native core completo** (tradução riscv→aarch64, NATIVE002 13/13); **kof.deps** (package manager MVP, Maven Central); **lifecycle** `application { onStart/onShutdown }`; **observability spans W3C** (3 targets) + metrics removidos; **LOG001 JS**; **TIME001 JS**.
> Tabela reflete 0.2.6-beta (03/09) — Build `mvn test` **840** (823+8+5+4), golden 16/16, integration 9/9. DoD em `docs/plan-platform-completion.md`.

---

## Tabela de Paridade

| Feature | JVM | Native | KofJS | Notas |
|---------|-----|--------|-------|-------|
| Literals (int, long, float, double, string, bool, char, null, hex) | ✅ | ✅ | ✅ | hex: `0x...` |
| Variáveis, `var`/`val`, inferência | ✅ | ✅ | ✅ | |
| Aritmética, bitwise, unário | ✅ | ✅ | ✅ | |
| if/else | ✅ | ✅ | ✅ | |
| if-expr (`var x = if (c) a else b`) | ✅ | ✅ | ✅ | |
| while, for, do-while, break/continue | ✅ | ✅ | ✅ | |
| for-in (`for (var x in coll)`) | ✅ | ✅ | ✅ | |
| switch (`case N:`) | ✅ | ✅ | ✅ | |
| Funções (todas as formas, sem `fun`) | ✅ | ✅ | ✅ | |
| Records (`record Point(Int x, Int y)`) | ✅ | ✅ | ✅ | toString/equals/hashCode |
| Classes, campos, métodos | ✅ | ✅ | ✅ | |
| `constructor(...)` e primary `class X(...)` | ✅ | ✅ | ✅ | |
| Herança, `super`, override | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| Virtual dispatch | ✅ | ✅ | ✅ | |
| Interfaces | ✅ | ✅ | ✅ | |
| Generics (erasure) — `Box<T>` `T` primitivo | ✅ | ✅ | ✅ | 25/08 `substituteTypeVariable` + `kof_int_to_string` |
| Lambdas `(x: Int) -> expr` | ✅ | ✅ | ✅ | com capturas (box `BoxN`) |
| Exceptions (throw "msg", try/catch/finally) | ✅ | ✅ | ✅ | Native: unwinding próprio |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ | |
| `spawn` stmt / `spawn f()` / `await` / `poll` / `done` / `cancel`+`cancelled` / `selectAny` / `awaitTimeout` / `channel<T>` send/receive / `scheduler.every`+`at`+`cancel` (Handle<T>, unbox, exceção limpa) | ✅ (virtual threads; canal = LinkedBlockingQueue; scheduler = ScheduledExecutor) | ✅ 31/08 (pthread_create + trampoline + pthread_join, allocator futex — CONC001; awaitTimeout = polling 1ms; canal = FIFO futex; **scheduler SCHED001** = thread por job + `usleep` ms→us + flag `active`) | ✅ 03/09 (CONC003 fechado — async/await/Promise reais; canal = fila de resolvers pendentes; `cancelled()` sempre `0`, limitação conhecida; scheduler = setInterval) | `KofAwaitTest` 8/8 · `KofConcurrency2Test` 25/25 · `SpawnE2ETest` 8/8 |
| Strings (`+`, `==`, length, charAt, substring, contains, startsWith, endsWith, indexOf, trim, case, replace, split) | ✅ | ✅ | ✅ | `length` diverge: **`STR001`** — Native conta bytes UTF-8; JVM conta unidades UTF-16 (`"Olá".length` = 4 vs 3). Gap explícito, não silencioso. |

| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| `List<T>`, `listOf`, for-in | ✅ | ✅ | ✅ | |
| `Map<K,V>` + `mapOf` (put/get/remove/contains/size/keys/values/clear/isEmpty) | ✅ HashMap | ✅ asm próprio | ✅ JS Map | `KofMapSetTest` — **campo/retorno de classe 01/09** (mapper `Map`→`HashMap`; parse de método c/ retorno genérico) |
| `Set<T>` + `setOf` (add/contains/remove/size/clear/isEmpty) | ✅ HashSet | ✅ asm sobre List | ✅ JS Set | tag de tipo no Native; **campo/retorno de classe 01/09** (mapper `Set`→`HashSet`) |
| `enum` + values/valueOf/name + switch exaustivo (`SEM031`) | ✅ | ✅ | ✅ | enum→String nos descritores; `KofEnumSwitchTest` |
| JSON encode/decode (primitivos, List) | ✅ | ✅ | ✅ | arrays `JSN003` fechado; `Double[]`/`Float[]` no Native (JSN001, 31/08) |
| JSON objetos/records (JSN002) | ✅ | ✅ 31/08 | ✅ | composição em compile-time no Native |
| `kof.io` (File, Path, Directory) | ✅ | ✅ | ✅ | `readText`/`readFile` → `String?` (`null` p/ ausência, 02/09); `size()` lança em vez de `-1` |
| `readLine` (stdin) | ✅ | ✅ | ✅ | `String?` — `null` no EOF (02/09; Native antes devolvia `""`) |
| kof.io `readText`/`size` | ✅ | ✅ | ✅ | `STR002` histórico fechado 02/09: Native `readText` devolve `null` (antes encerrava); `size()` sem sentinela `-1` |
| kof.time (`now()`, `sleep`, `interval`) | ✅ | ✅ | ✅ | `interval`/`every` JVM (ScheduledExecutor) + **Native (01/09, `time.interval` reusa o scheduler — SCHED001; mutação por referência validada)** + **JS (TIME001 fechado 02/09)** — fila cooperativa bombeada por `time.sleep` no GraalJS, `setInterval` nativo em browser/Node |
| `readLine`, `readFile`, `writeFile` | ✅ | ✅ | ✅ | |
| `kof.validation` (13 preds) | ✅ | ✅ | ✅ | `KofValidationTest` |
| `kof.observability` (health/metrics/requestId) | ✅ | ✅ | ✅ | `KofObservabilityTest` |
| `kof.http` client | ✅ | Native HTTP/1.1 asm (socket/recv/send parse; https→throw; DNS não-IP→127.0.0.1; retry knob no-op) | ✅ (GraalJS via `Java HttpClient` interop + fetch fallback) | `KofHttpE2ETest` 6/6 (JVM+JS+Native real resp); retry/circuit paridade 30/08 |
| `kof.cache` (get/set/ttl/delete/clear) | ✅ | ✅ 30/08 (fix clobber `%rax`/`%rdi`) | ✅ | `KofCacheE2ETest` 5/5 x3 targets |
| `kof.mq` (pub/sub + queue) | ✅ | ✅ (01/09, pub/sub + filas in-process, asm) | ✅ | `KofMqE2ETest` 4/4 (JVM+Native+JS) |
| `kof.config` (typed) | ✅ | ✅ (asm próprio) | CONF001 | precedência total Native (`KOF_CONFIG` > env > profile > `kof.config`); `NativeConfigE2ETest` 8 |
| `kof.log` | ✅ (JSON + correlation ID) | ✅ (asm; UTC, sem JSON) | ✅ 01/09 (console.* + nível) | `KofLogE2ETest` 11 (incl. JS) + `NativeLogE2ETest` 7 |
| `kof.security` (passwords/crypto/jwt/secrets + G9) | ✅ | ✅ | ✅ | PBKDF2/SHA512/JWT/AES-GCM (asm no Native, JS puro no KofJS — `SECN002` fechado 01/09) |
| `kof.db`/`kof.orm` | ✅ | ✅ (SQLite `.so` direto; **`transaction {}` commit/rollback 01/09** (EH asm + BEGIN/COMMIT/ROLLBACK); **MySQL wire protocol** — handshake+scramble+auth-switch+COM_QUERY+resultset 31/08 + **prepared statements binários 03/09**)/ORM001 | DB001/ORM001 | **Query DSL `User.query(db){ where; orderBy; limit }` (nível 3) 01/09** — baixa p/ `db.query` (JVM E2E H2); MySQL native `nativeMysqlWireProtocol` + `nativeMysqlPreparedBinary` (binário q/ binds); `nativeTransaction{Commits,RollsBack}` |
| `web.app()` + TLS `listenSecure` | ✅ | server HTTP/1.1 (accept/route/lambda/body) — **TLS = WEB002 residual** | ✅ 03/09 (GraalJS `HttpServer` real — `bc577aa`) | `KofWebTlsTest` (JVM) · `KofWebNativeE2ETest` 4/4 |
| `web.app()` WebSocket `app.ws` + SSE `sse.*` | ✅ 30/08 (RFC 6455 + frame codec/máscara) | **WEB002** (ws/sse não portados p/ Native) | **WEB001 residual** (ws/sse no JS) | `KofWebWsE2ETest` 11/11 · `KofWebSseE2ETest` 7/7 · `KofWsFrameTest` 7/7 · `KofWebHardeningTest` 6/6 |
| `status(code, body)` / `headerSet` | ✅ 27/08 | ✅ 03/09 (NativeWebRuntime responde status/body) | ✅ 03/09 (JS HttpServer) | `KofWebE2ETest` 9/9 |
| UI Fase 7 Router (`go/replace/back/forward/param/current/depth`) | no-op | — | ✅ 31/08 | `RouterE2ETest` |
| `switch` pattern matching `case String s` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `switch` record destructuring `Point(x,y)` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `switch` como expressão `case X -> v` (SYN001) | ✅ | ✅ (x86_64/riscv64/aarch64) | ✅ (ternários aninhados) | 03/09 — `default` obrigatório ou exaustividade enum (`SEM032`); `KofSwitchExprE2ETest` 19 + riscv64/aarch64 14/14 |
| `String?` null safety básica | ✅ | ✅ | ✅ | 0.2.6-beta (`Type?`) |
| `List map/filter/reduce` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `Box<T>` generic | ✅ | ✅ | ✅ | `substituteTypeVariable` |
| `KofScript` top-level `let` → `KofScriptGlobals` | ✅ | ✅ | ✅ | `KofScript` 0.2.0 |
| `KofCcompiler` (`kof c`) C subset | — | ✅ x86_64 native-only | — | `kof_c`, while/if/deref &/* |
| `native.risc` (riscv64) / `native.arm` (aarch64) | — | **core completo (02-03/09)**: plumbing + codegen/runtimes asm puro + qemu — `NativeRiscv64E2ETest 13/13` + `NativeAarch64E2ETest 13/13` (core: classes/arrays/List/switch/try-catch/pattern/Strings/recursão) — paridade avançada pendente — `docs/native-multiarch.md` | — | target separation 0.2.0 |
| `kof fmt` (formatter parser real, idempotente) | ✅ 31/08 | ✅ | ✅ | `KofFormatter` (2c3e794) |
| **KofJS no browser real** (`kof.ui` renderizando DOM via ES Modules) | — | — | ✅ 01/09 (`KofJsBrowserE2ETest` — Chrome headless + HTTP + captura de DOM; pula se Chrome ausente) | ESM via HTTP local (módulos não carregam via `file://`); `KofJsRunner` serve `appDir` em `127.0.0.1` |
| Android (Fase 1: `kof build --target android` → projeto Maven + APK, host Activity em Kof) | ✅ (bytecode JVM) | — | — | gaps `AND00x` em compile-time |

## Gaps documentados (não mascarados)

| Gap | Diagnostic | Status |
|-----|-----------|--------|
| spawn/await no Native | ✅ 31/08 (CONC001 fechado — pthread_create + trampoline + pthread_join + allocator thread-safe futex; join implícito) | |
| spawn/await no JS | ✅ 03/09 (CONC003 fechado — async/await/Promise real; stmt + spawn-expr + await/poll/cancel/selectAny/channel bloqueante) | `cancelled()` sempre `0` (sem thread-local pra task atual); só task-lambdas podem ficar async (`CONC003-JS-01`) |
| web no Native/JS (server, TLS, ws/sse) | ✅ server base (03/09): Native accept/route/lambda/body (`NativeWebRuntime.java`) + JS GraalJS HttpServer (`bc577aa`). Residual: TLS/ws/sse/path params/keepalive nos dois | `WEB002`/`WEB001` (residual) |
| kof.http no Native | ✅ HTTP/1.1 asm (`NativeHttpRuntime.java`, 03/09) | https + DNS real + retry ficam como gaps (`HTTP003`) |
| kof.db/orm no JS | `DB001` / `ORM001` | planned |
| JSON Float/Double | ✅ 31/08 (JSN001 fechado — XMM + parser fração/expoente) | |
| JSON objetos/records no Native | ✅ 31/08 (JSN002 fechado — composição em compile-time) | |
| GC mark-sweep no Native | ✅ | `kof_gc_sweep` real (flag bit1) + **auto-collect desligado** por exigir safe-points (`status.md` #1) — `KofGcE2ETest` 3/3 |
| MySQL nativo completo | ✅ | **wire protocol** + **prepared statements binário (03/09)** — `NativeDbPrepared.java`, `KofDbE2ETest` 12/12 |
| Native riscv64/aarch64 codegen | `NATIVE002` | **riscv64 core completo (02/09) + aarch64 core completo (03/09)**: `NativeRiscv64E2ETest 13/13` + `NativeAarch64E2ETest 13/13` — runtimes + codegen em **asm puro** (raw syscalls 64/93, **sem C**; `as`+`ld` estático; aarch64 via `translateRiscvToAarch64`) + qemu: println, var, if/else, aritmética, **classes (virtual dispatch/fields), arrays, List, switch, try/catch/throw, pattern matching (switch String s/instanceof/as), String methods, recursão**. Paridade total x86 (JSON/DB/HTTP/concorrência/UI/net) pendente nos dois. **Ver `docs/native-multiarch.md`** |

Fechados em 0.2.6-beta (01/09): **`kof.log` no JS (LOG001)** — console.* + `KOF_LOG_LEVEL`, bloco Vulkan no runtime JVM condicional ao uso de `kof.vk` (capability/link-por-uso, R2) + `--enable-preview` só para programas Vulkan (FFM preview API no JDK 21), `spawn` com lambda que captura variável externa (JVM+Native), `&&`/`||` booleanos com short-circuit no JS (bitwise intacto), `Channel<T>` como parâmetro de função (JVM/Native/JS), `println`/`print` antes de `spawn` e `spawn→await→spawn` no Native sem SIGSEGV (alinhamento de stack no `pthread_create`), KofJS `kof.ui` renderizando em browser real (Chrome headless E2E), LSP `references`+`rename`, tracing W3C `traceId`/`spanId` (3 targets), `moduleRoot` por LCA (P1-4), validação tipada de coluna no ORM (P3-10, `ORM003`), **AES-256-GCM no KofJS (`SECN002`)** — AES-GCM puro JS com paridade cross-target JVM↔JS e tamper detection por base64 estrito, **histogram/metrics no Native (`OBS002`)** — store asm + export Prometheus em paridade de conteúdo com o JVM, **`time.interval`/`time.cancel` no Native (`TIME001`)** — reusa o scheduler (SCHED001) via alias `jmp`, mutação por referência validada; **TIME001 fechado no JS (02/09)** — `kofTimeInterval`/`kofTimeCancel` agora comandam uma fila cooperativa `kofTimeJobs` bombeada dentro de `kofTimeSleep` (GraalJS não tem event loop; browser/Node caem no `setInterval` nativo) — `KofTimeE2ETest.jsIntervalRunsPeriodicallyUntilCancelled` (paridade ticks≥2 + freeze após cancel), **`transaction {}` no Native** — `kof_db_transaction` em asm: BEGIN/COMMIT/ROLLBACK via `kf_db_execute`, lambda via vtable (`rdi`=this p/ capturas) e ROLLBACK+re-throw no EH (`kf_throw_string` chega no handler com `%rdi` e a chain p/ o try externo), conexão default = última aberta — `nativeTransactionCommits`/`nativeTransactionRollsBackOnFailure`, **`kof.mq` no Native (`MQ001`)** — pub/sub + filas in-process em asm (store `.bss` + nodes 40B, invoke-com-arg, paridade de output com JVM/JS), **`Set<T>`/`Map<K,V>` como campo/retorno de classe no JVM** — mapper `Set`→`HashSet`/`Map`→`HashMap` + parse de método de classe c/ retorno genérico (`KofMapSetTest.setMapAsFieldAndReturn`, 3 targets), **source map V3 do KofJS** — mappings VLQ em nível de linha (função gerada → linha Kof; `KofJsSourceMapTest`).
Fechados em 0.2.6-beta (30-31/08): `spawn` Native (CONC001 — pthread), FP XMM (FLT001), JSON completo no Native (JSN001/JSN002/JSN003 — objetos/records + arrays incl. Double/Float), WebSocket/SSE JVM (RFC 6455), `kof.http` retry/circuit JVM+JS (30s window, fail-fast), `kof.cache` 3 targets (fix de clobber de registradores), SQLite nativo `.so` direto, UI Fase 7 Router (JS), `kof fmt` + `kof config gen`.
Fechados em 0.2.6-beta (27/08): pattern matching `switch case String s` + record `Point(x,y)` (JVM/Native/JS), `String?` null safety básica, `kof.http` no JS via `Java HttpClient`, `List map/filter/reduce`, large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, free-list GC (`kof_free_head`), Windows SIGPIPE.
Fechados em 0.1.0: Map/Set nativo (era COL001), await com unboxing, captura em lambdas (BoxN), resultado de tarefa (`await`).

## Princípio

A semântica Kof é a mesma em todos os targets. Onde um target não suporta
uma feature ainda, o compilador emite um diagnostic explícito — nunca código
que funciona de forma diferente ou quebra silenciosamente.

---

## Convenção de gaps por domínio (visão universal)

Todo gap de capacidade tem um **código de diagnóstico** documentado aqui e
emitido em compile-time. Domínios novos seguem o mesmo padrão dos existentes
(`SECN00x`, `CONC003`, `DB001`, `HTTP002`, ...):

| Prefixo | Domínio | Exemplos |
|---------|---------|----------|
| `HTTP`/`WEB`/`DB`/`ORM`/`MQ`/`SCHED`/`TIME`/`CONC`/`SECN` | Sistemas atuais | `HTTP002`, `WEB001`, `DB001`, `MQ001`, `SCHED001`, `CONC003`, `SECN002` |
| `AND` | Android | `AND001..004` |
| `NATIVE` | codegen multiarch | `NATIVE002` |
| `INFRA` | infraestrutura / IaC | `INFRA00x` |
| `DATA` | data engineering / dataframe / ML | `DATA00x` |
| `SCI` | scientific computing / HPC | `SCI00x` |
| `BIO` | bioinformática | `BIO00x` |
| `SECPQ` | criptografia pós-quântica | `SECPQ` (gap de target, nunca stub) |

Regra (R6): gap de domínio sempre tem **código + entrada nesta matriz** —
nunca stub silencioso, nunca fallback fraco, nunca "paridade parcial" sem
diagnóstico.

## Tiers de estabilidade (R5)

Cada namespace/pacote carrega um tier:

- **`stable`** — garantia de compatibilidade; promove somente com DoD completo
  (3 targets ou gap diagnosticado, E2E por target, benchmark quando plausível,
  docs + `training/` sincronizadas, suíte verde).
- **`experimental`** — pode mudar; a camada de **pacotes oficiais** nasce
  `experimental`.

Domínios pesados (`ml`, `bio`, `hpc`, `infra-<cloud>`) são **pacotes
oficiais** (camada 4), **nunca** stdlib base (R1).