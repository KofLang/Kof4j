# Roadmap 01 — Gap Report (03/09/2026, 0.2.6-beta)

> Gerado ao final do todo NATIVE002 core (riscv64 02/09 + aarch64 03/09, 13/13 cada). Base: `docs/status.md:1`, `docs/native-multiarch.md:1`, `docs/backend-parity.md:1`, `kof-compiler/src/main/java/dev/kof/compiler/NativeBackend.java:1851`, `NativeRiscv64E2ETest.java:1`, `NativeAarch64E2ETest.java:1`.

## Sumário executivo
- **Testes:** `814` (797 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli) — `docs/status.md:11`
- **NATIVE002 core:** ✅ riscv64 13/13 (`NativeRiscv64E2ETest.java:70`) + aarch64 13/13 (`NativeAarch64E2ETest.java:70`) via `qemu-*`, runtime asm puro sem C (`NativeBackend.java:2495` RISCV_RUNTIME_ASM + `NativeBackend.java:3533` emitAarch64 via `translateRiscvToAarch64:3716`)
- **NATIVE002 paridade avançada:** ❌ `NATIVE002` segue aberto — JSON/DB/HTTP/concorrência/UI/net ainda só x86_64 (`docs/native-multiarch.md:5`, `docs/backend-parity.md:86`)
- **Discrepâncias roadmap vs status:** 3 itens listados como “Em desenvolvimento” no roadmap já estão fechados no status/docs (FLT001, JSN002, parte de CONC001/G8).

---

## 1. Concluído (roadmap) — auditoria

Todos os 38 itens de “Concluído” batem com `docs/status.md:664` e `docs/backend-parity.md:15`. Gaps honestos dentro deles:

| Item roadmap | Estado real | Onde | Observação |
|---|---|---|---|
| Compiler foundation … Native backend | ✅ | `CompilerDriver.java`, `NativeBackend.java:232` | Native x86_64 completo (18 emit*); riscv64/aarch64 só **core** (não full) — `docs/native-multiarch.md:61` |
| classes/records/inheritance/interfaces/constructors/exceptions/generics/collections/string operations/control flow | ✅ 3 targets | `BackendParityTest:10`, `KofPatternMatchingTest:10` | Herança `super.metodo()` = SUP001 no Native |
| kof build/run/serve/test/debug/bench | ✅ | `docs/status.md:13` | `kof debug` MVP JVM apenas; Native DWARF só `.debug_line` (`NativeDwarfLineInfoTest:1`) |
| kof.web rotas/middleware + WebSocket/SSE | ✅ JVM apenas | `KofWebE2ETest:10`, `KofWebWsE2ETest:11` | Native/JS gap `WEB002`/`WEB001` (`docs/backend-parity.md:78`) |
| kof.db JDBC + SQLite nativo | ✅ JVM + Native SQLite | `KofDbE2ETest:11` | Native via `.so` direto; `transaction{}` ✅ 01/09 (`NativeBackend.java:docs/status.md:132`) |
| kof.orm entity/CRUD/migrate/MongoDB | ✅ JVM | `KofOrmE2ETest:22` | Native/JS `ORM001` |
| kof.log nativo | ✅ | `NativeLogE2ETest:7` | x86_64 asm, UTC, sem JSON; riscv/aarch64 não portado |
| kof.config (3 targets) | ✅ | `KofConfigE2ETest:11`, `NativeConfigE2ETest:8` | riscv/aarch64 não portado (só x86_64 asm) |
| kof.mq pub/sub (JVM) | ✅ 3 targets | `KofMqE2ETest:4` | Native 01/09 fechado, mas só x86_64 asm; riscv/aarch64 pendente |
| cliente HTTP (JVM + JS) | ✅ JVM+JS | `KofHttpE2ETest:4` | Native `HTTP002` |
| kof.security v1 (3 targets) + G9 rateLimit/sessões/API keys (3 targets) | ✅ | `KofSecurityTest:25` |  |
| TLS/HTTPS web.listenSecure (JVM) | ✅ JVM | `KofWebTlsTest:5` | Native/JS `WEB002`/`WEB001` |
| kof.validation 13 preds + kof.observability health/metrics/request IDs (3 targets) | ✅ | `KofValidationTest:3`, `KofObservabilityTest:4` | Native observability 01/09 (`OBS002`) só x86_64 |
| kof.ui widgets | ✅ | `UiE2ETest:14` |  |
| spawn JVM + await Handle<T> | ✅ | `KofAwaitTest:7` |  |
| enum 3 targets + switch exaustivo | ✅ | `KofEnumTest:4` |  |
| Map/Set 3 targets (COL001) | ✅ | `KofMapSetTest:4` |  |
| otimizador IR + bench 37 benchmarks | ✅ | `OptimizerTest:21`, `benchmarks/` |  |
| KofScript top-level let/const | ✅ | `kof-script` 8 |  |
| KofCcompiler C subset → ELF x86_64 | ✅ | `kof-c-compiler` 5 | só x86_64 |
| kof.process + process.spawn stdin/stdout | ✅ | F10 |  |
| kof fmt | ✅ | `Fmt.java` |  |
| sobrecarga de construtores, widening, LSP, Native GC free-list, Pattern matching, Null safety, Higher-order map/filter/reduce, Módulos multi-arquivo, releases multiplataforma | ✅ | `docs/status.md:682` | GC mark-sweep pendente (memória só `munmap` — `docs/status.md:605`) |

## 2. Em desenvolvimento (roadmap) — o que falta de fato

| Item roadmap | Status real (03/09) | Falta |
|---|---|---|
| **Standard Library (contratos em estabilização)** | 🟡 Parcial | Muitos módulos com gaps por target (web, db/orm, http, cache, media, etc.). Contratos não congelados. |
| **Async** | 🟡 | `Async` como conceito ainda não tem spec; `spawn`/`await` já cobre parte. Falta `async`/`await` JS-style (não existe em Kof — `fake-idioms.md`) |
| **Concurrency — spawn no Native (CONC001), spawn-expr/await no JS (CONC003), AND001 no Android** | ✅ CONC001 fechado 31/08 (`docs/status.md:606`), AND001 31/08, CONC003 JS sequencial (event-loop real pendente) — `docs/backend-parity.md:78` | JS async real sobre Promises/event-loop (`CONC003` futuro) |
| **KofAndroid — Fase 1: kof build --target android** | 🟡 Fase 1 gera projeto Maven + APK (host em Kof) — `docs/status.md:679` Fase 2 31/08 já com `--apk` standalone, mas gaps `AND00x` em compile-time | Fase 2 completa + paridade (sem virtual threads no ART → platform threads) |
| **MySQL/MariaDB nativo — handshake (27/08; query/prepared pendentes)** | 🟡 Wire protocol ✅ 31/08 (`kof_db_mysql_scramble` + COM_QUERY + binds `?` client-side — `KofDbE2ETest.java:nativeMysqlWireProtocol`) — `docs/status.md:137` | **Prepared statements** binários COM_STMT_PREPARE/EXECUTE (tentativa 01/09 revertida — packet malformado, hang). Pendente. |
| **Ponto flutuante SSE no Native (FLT001)** | ✅ **Fechado 31/08** — XMM real (`vcvtsi2sd`, `mulsd`, `snprintf` dtoa) — `docs/status.md:620` vs roadmap diz “Em desenvolvimento” → **discrepância: já feito** | Nada (só paridade riscv/aarch64 do FP — hoje só int no core) |
| **JSON de objetos no Native (JSN002)** | ✅ **Fechado 31/08** — composição compile-time — `docs/status.md:608` vs roadmap “Em desenvolvimento” → **já feito** (só x86_64) | Portar p/ riscv/aarch64 |
| **native.risc toolchain estável + native.arm placeholder** | 🟡 **Atualizado 03/09** — riscv64 + aarch64 **core completo 13/13** (`docs/native-multiarch.md:3`), ELF via `cross-as/ld` + qemu, **sem C** (bump allocator, raw syscalls) — `NativeBackend.java:1851/3533` | Paridade avançada (JSON/DB/HTTP/concorrência/scheduler/mq/UI/net/cache/log/config/time) nos dois. `NATIVE002` segue como diagnóstico `ops desconhecidos → comentário`. |
| **Debugger — além do MVP JVM** | 🟡 MVP JVM DAP sobre stdio ✅ (`docs/status.md:583`) + JS source maps V3 linha ✅ (`KofJsSourceMapTest:1`) + Native DWARF `.debug_line` ✅ parcial (`NativeDwarfLineInfoTest:1`) | Locals por frame, stepping, exception breakpoints, avaliação; Native variáveis/expressões; DAP breakpoints no Nativo; `debugger-architecture.md` |
| **KofJS — plataforma web no browser (ES Modules via GraalJS já em alpha)** | 🟡 Alpha + **browser real 01/09** (`KofJsBrowserE2ETest:1` — Chrome headless + HTTP + DOM) — `KofJsRunner` serve `appDir` | Módulos via `file://` (ESM exige HTTP), persistência, performance, API completa |
| **Concorrência 0.2.x residual: await com timeout, cancelamento, select, canais tipados, scheduler/cron (G8)** | ✅ **Fechado** — `awaitTimeout`, `cancel`/`cancelled`, `selectAny`, `channel<T>` send/receive, `scheduler.every/at/cancel` em JVM+Native+JS (`KofConcurrency2Test:18`, `SpawnE2ETest:8`, `docs/status.md:677`) vs roadmap “Em desenvolvimento” → **já feito (exceto JS async real)** | JS async real (event-loop) |

## 3. Pendências não listadas no roadmap 01 (mas em docs)

- **GC Native** — free-list `kof_free_head` ✅, `kof_gc_collect` mark-sweep emitido mas **auto-GC desligado** (hang) — `docs/status.md:605`
- **HTTP/2, gRPC** (`app.grpc`) — planejado (`docs/status.md:706`)
- **Package manager** (`kofdeps`, registry), **language spec** completa, **conformance suite**, **auto-hospedagem** — planejado (`docs/status.md:701`)
- **Web completa** (frontend declarativo, SSR) — planejado
- **Media** — câmera `MEDIA002`, paridade Native/JS `MEDIA001`, mic sem hardware `MEDIA003`
- **CI cross** — `aarch64`/`riscv64` não entram no pipeline (`docs/native-multiarch.md:68`)
- **Toolchains** — `qemu-user` + `binutils-*-linux-gnu` instalados localmente, não no CI

## 4. Próximos passos para fechar NATIVE002 totalmente

1. Portar bindings `?` MySQL + prepared statements (quando re-tentado) p/ riscv/aarch64 (mesma tradução).
2. Portar `kof.http`/`kof.db`/`kof.mq`/`kof.cache`/`kof.log`/`kof.config`/`kof.time`/`kof.observability`/`scheduler` p/ riscv/aarch64 (hoje só x86_64 asm).
3. Extrair `NativeBase` (layout/`kof_alloc`/mangle comum) para evitar divergência (`docs/native-multiarch.md:66`).
4. Adicionar CI com `assume` (não quebrar quando toolchain ausente) + `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` no `ci.yml`.

---
*Em 03/09, o “todo” NATIVE002 core está 100% verde (26 testes cross, binários estáticos qemu). O roadmap 01 tem 3 itens com discrepância (já fechados no status) e 7 com falta real (paridade avançada + debugger + KofJS + Android).*
