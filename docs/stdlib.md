# Kof Standard Library — Arquitetura

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes: 793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli; golden 16/16, integration 9/9)

> A Standard Library do Kof é a plataforma: HTTP, REST, auth, autorização,
> validação, serialização, database, messaging, observabilidade e testing
> devem ser construídos com a própria plataforma — sem dependência
> arquitetural de frameworks externos.

---

# 1. PRINCÍPIO

```text
intenção → Kof → resultado
```

A API pública é simples; a implementação interna é eficiente. O compilador
conhece cada chamada de stdlib em compile-time e a mapeia para o runtime
mais direto de cada target (JVM bytecode, assembly nativo, JS idiomático).

---

# 2. MECANISMO

Cada módulo da stdlib é uma **tabela de dispatch compile-time**:

```text
KofSecurity.java / KofWeb.java / KofIo.java / KofUi.java
        │
        ├── SemanticAnalyzer   → tipos das chamadas (inferência/checagem)
        ├── CompilerDriver     → lowering para KofCall(kof_*)
        ├── JvmRuntime         → KofRuntime gerado (javax.crypto, java.nio...)
        ├── NativeRuntime      → assembly x86-64 (syscalls, sem libc)
        └── JsBackend          → kof-runtime.mjs (JS puro + kof_platform)
```

Gaps de target produzem **diagnósticos claros em compile-time** (SECN00x,
CONC001, JSN00x) — nunca comportamento silenciosamente diferente.

---

# 3. MÓDULOS

| Módulo | Estado | Notas |
|--------|--------|-------|
| `kof.core` | ✅ | println, strings, arrays, aritmética; `enum Name { A, B }` + `values()/valueOf()/name()` + `==` por conteúdo — 3 targets (`KofEnumTest`); **0.2.0**: pattern matching `case String s` + `Point(x,y)` e `String?` básica (3 targets) |
| `kof.collections` | ✅ | `List<T>` `listOf` + `map/filter/reduce` (0.2.0, 3 targets); `Map<K,V>` (mapOf + put/get/remove/contains/size/keys/values/clear/isEmpty) e `Set<T>` (setOf + add/contains/remove/size/clear/isEmpty) — **3 targets** (Native: asm próprio sobre layout List; Set usa tag de tipo p/ equals); `Box<T>` via `substituteTypeVariable` `CompilerDriver.java:3972` |
| `kof.io` | ✅ | `File/Path/Directory`, readFile/writeFile — JVM/Native/JS |
| `kof.time` | ✅ | `now()`, `sleep`, `interval`/`cancel` (3 targets — JS via fila cooperativa bombeada por `time.sleep`, TIME001 fechado) — `KofTimeE2ETest` |
| `kof.json` | ✅ | encode/decode; objetos/records JVM+Native+JS (JSN002), Float/Double + arrays `Double[]`/`Float[]` (JSN001) e arrays `Int[]/Long[]/Bool[]/String[]` (JSN003) — Native completo 31/08 |
| `kof.http` | ✅ | `kof serve` (KofHttpServer, thread pool) — JVM; `kof.http` client `http.get/post/put/delete/patch/options/status` — JVM/JS/**Native 03/09** (HTTP/1.1 asm, `NativeHttpRuntime`, IPv4 só; https→throw); retry/circuit/timeout aceitam chamada mas só implementados em JVM/JS (`HTTP003`) |
| `kof.web` | ✅ | `web.app()`, rotas, middleware `app.use`, `listenSecure(port)` TLS, `status(code[, body])`/`headerSet`, `app.ws` (WebSocket RFC 6455) + `app.sse` (SSE) — JVM (Native `WEB001/002`, JS `WEB001`/`WEB003`/`WEB004`) |
| `kof.security` | ✅ (v1 + G9) | passwords, crypto, jwt, secrets, auth, security, rateLimit, sessions, apiKeys — 3 targets; free-list Native 27/08 — ver `docs/security.md` |
| `kof.concurrent` | ✅ | `spawn` (statement) + `val r = spawn f()` / `await r` (handle tipado) — JVM (virtual threads) + Native (pthread, 31/08, `CONC001` fechado) + JS sequencial |
| `kof.test` | ✅ | `kof test` (`test "nome" { }` nos 3 targets) + `assert` — `StructuredTestE2ETest` 11/11; golden 16/16 |
| `kof.cli` | ✅ | `kof build/run/serve/check/test/bench/debug/info/lsp/install/script/repl/c` (debug DAP, `kof script --watch` SIGPIPE fix 27/08) |
| `kof.script` | ✅ | `KofScript` top-level `let` → `KofScriptGlobals` + REPL (8 testes kof-script, 27/08) |
| `kof.c` | ✅ | `KofCcompiler` C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → native x86_64 (5 testes kof-c-compiler, 27/08) |
| `kof.metrics` | ✅ | `kof bench`/`kof profile` (harness + baseline, 37 benchmarks, `benchmark.yml` threshold 1.20) |
| `kof.rest` | ⏳ | planejado |
| `kof.database` | ✅ | `kof.db` (JVM JDBC: H2/MySQL/MariaDB/PostgreSQL; Native SQLite via `.so` direto + MySQL wire protocol WIP — auth scramble SHA-1; JS `DB001`) + `kof.orm` (entity, create/save/saveAll/find/where/count/page/delete/deleteAll/migrate; **coluna tipada em where/count: literal não-campo → `ORM003` em compile-time**; JVM + MongoDB; Native/JS `ORM001`) — ver `docs/DATABASE_VISION.md` |
| `kof.messaging` | ✅ | `kof.mq` publish/subscribe/queue — **3 targets** (JVM in-memory; Native asm 01/09; JS in-process) — `KofMqE2ETest` 4/4 |
| `kof.validation` | ✅ | `validation.required/notBlank/minLength/maxLength/lengthBetween/isEmail/isUrl/matches/isInt/isLong/inRange/min/max` — JVM/Native/JS (`KofValidationTest` 3/3) |
| `kof.logging` | ✅ | `log.debug/info/warn/error`, níveis, off — JVM+Native (asm, `kof_log_*`, 27/08) — `KofLogE2ETest` + `NativeLogE2ETest` |
| `kof.observability` | ✅ | `observability.health/readiness/liveness`, `counter`/`increment`/`gauge`, `requestId()`/`correlationId()` — JVM/Native/JS (`KofObservabilityTest` 3/3) |
| `kof.cache` | ✅ | `cache.get/set/set(key,v,ttl)/ttl/delete/clear` — 3 targets (fix nativo 30/08) — `KofCacheE2ETest` (5) |
| `kof.scheduler` | ✅ | `scheduler.every(n, fn)`/`at(cron, fn)`/`cancel(id)` — JVM (ScheduledExecutor) + JS (setInterval) — Native `SCHED001` |
| `kof.process` | ✅ | `kof.process` (spawn de processos) — ver `docs/status.md` |
| `kof.config` | ✅ | `config.get/env/has`, `config.str/int/long/bool(name, fallback)`, `config.required`; interpolação `${key}`; `kof config gen` gera template; precedência `KOF_CONFIG` > env `KOF_<KEY>` > profile > `kof.config` — 3 targets (JVM/Native asm `/proc/self/environ`/JS) — `KofConfigE2ETest` (11) |
| **STDLIB** (`math`/`strings`/`encoding`/`uuid`/`validation`-ext/`time`-calendário/`net`; namespace via `KofStd`/`KofTime`/`KofValidation`) | ✅ S1–S8 (parcial nativos) | **plan-stdlib-expansion.** `math`: `clamp/abs/sign/min/max/isEven/isOdd/isPositive/isNegative/isZero` (Int, 4 alvos). `strings`: `isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count/capitalize/reverse/repeat/truncate/padLeft/padRight + `escapeHtml` (S3.1) + `removeWhitespace`/`normalizeWhitespace` (S3.2) + word-converters `toCamelCase/toPascalCase/toSnakeCase/toKebabCase/slugify` — **todos nos 4 alvos** (**STRN001 fechado 09/09**: port riscv B15 + diff golden no qemu). `encoding`: `hexEncode/Decode` + `urlEncode/Decode` (4 alvos); `base64Encode/Decode` + `base64UrlEncode/Decode` (JVM/Script/JS/x86 + **riscv/aarch — ENC002 fechado 09/09**: port riscv B23 + translator, spec tolerante idêntica). `uuid.v4` (JVM/x86/JS; riscv/aarch **SECN000** — entropia). `validation` ext: BR `isCpf/isCnpj/isCep/isPis` (4), rede `isIpv4/isIpv6/isMac/isPort` (4), Luhn `isCreditCard` (4), `isDomain` (4, RFC 1123 v1 sem ponto final/IDN). `time` calendário: `isLeapYear/daysInMonth/dayOfWeek/daysBetween` (4, sem gate). Semântica travada nas matrizes `stdmath`/`stdstrings*`/`stdenc`/`stdvalidation*`/`stdluhn`/`stdipv6`/`stddomain`/`stdescape`/`stdtime` (ConformanceMatrixTest; riscv/aarch executam sob qemu). Tutorial: `learn/39-stdlib.md`. `net` ext: 6 escalares `scheme/host/port/path/query/fragment(STR->STR)` + `queryEncode/queryDecode` (S8, decisão §4 do plano; RFC 3986 subset v1 — sem colchetes IPv6; ausente=>""; nunca lança) em JVM/Script/JS; nativos **NET001** gated. Semântica travada nas matrizes `stdmath`/`stdstrings*`/`stdenc`/`stdvalidation*`/`stdunescape`/`stdws`/`stdescape`/`stdnet`/`stdtime` (ConformanceMatrixTest; riscv/aarch executam sob qemu). Tutorial: `learn/39-stdlib.md`. Gaps abertos: NAT-STR01 (UTF-8 nos conversores nativos), FLT (`math` Double = S1b), SECN000 (uuid no riscv/aarch), NET001 (net nos nativos). |

---

# 4. REGRAS DE DESIGN

1. **Intenção primeiro**: `passwords.verify(pw, hash)` — nunca primitivas
   soltas para a aplicação montar segurança.
2. **Secure by default**: escolhas seguras automáticas (PBKDF2 600k, HS256
   fixo, salt aleatório, comparação constant-time).
3. **Sem cerimônia**: sem injeção de container, sem annotations, sem
   configuração XML/yml obrigatória.
4. **Sem overhead escondido**: cada abstração precisa responder qual é seu
   custo em runtime (docs/performance.md §8).
5. **Diagnósticos claros**: gaps de target nunca silenciosos.
6. **Java/Spring continuam válidos** como interoperabilidade — nunca como
   dependência arquitetural.

---

# 5. AUDITORIA DO ECOSSISTEMA

A matriz completa de cobertura (inventário, gaps, dependências,
arquitetura, prioridade e estratégia) vive em **`docs/ecosystem-coverage.md`**
— resultado da auditoria da stdlib contra as capacidades de uma
plataforma moderna (checklist derivado do ecossistema Spring, usado como
matriz de capacidades, não como especificação de API).

Resumo executivo (0.2.6-beta, 31/08):

| Categoria | Estado |
|-----------|--------|
| core/collections/io/time/json | DONE (3 targets; 0.2.0 acrescenta `map/filter/reduce` + pattern matching + `String?`) |
| security (crypto, jwt, secrets, auth web + G9) | DONE (JVM/Native/JS core; web auth JVM; free-list Native 27/08) |
| web server (`web.app()`) + `kof.http` client | DONE (JVM; `kof.http` JVM+JS + retry/circuit; WebSocket/SSE JVM) |
| concurrency (`spawn` + `await`) | DONE (JVM + Native pthread (31/08) + JS sequencial) |
| test (`assert`, `kof test` `test "nome" {}`) | DONE (3 targets, 16/16 golden, 9/9 integration) |
| observability | DONE (kof.observability: health/metrics/request IDs — JVM/Native/JS) |
| `KofScript` / `KofCcompiler` / targets riscv64/aarch64 | DONE (KofScript 8, KofC 5, riscv64 toolchain estável) |
| messaging (`kof.mq` 3 targets), scheduling (`scheduler` JVM+JS), sessions, rate limiting, TLS, WebSocket/SSE (JVM), `kof.cache` (3 targets) | DONE (gaps reais: `SCHED001` Native, `WEB002` TLS, `WEB003/004` WS/SSE) |
| GC Native mark-sweep | DONE (03/09 `kof_gc_mark` + `kof_gc_sweep` + auto-collect on exhaustion; `KofGcE2ETest` 3/3) |

# 6. PRÓXIMAS ETAPAS (residual pós-0.2.0)

1. Native aarch64 codegen completo (placeholder hoje)
2. ~~GC mark-sweep completo~~ ✅ 03/09 (KofGcE2ETest 3/3)
3. MySQL/MariaDB native completo (auth scramble SHA-1 + lenenc done; handshake/query/prepared pendentes — WIP)
4. Query DSL tipada `User.query { where age > 18 }` (nível 3 DATABASE_VISION)
5. `kof fmt` (P5) + LSP completo + Debugger DWARF/JS source maps
6. tracing / OpenTelemetry (WebSocket/SSE ✅ JVM e `kof.cache` ✅ 3 targets fechados 30/08)

Histórico fechado: G7 SECN004, G6 `kof.test` estruturado, G3 `kof.config` (JVM+Native), G2 `kof.http` (JVM+JS), G1 `kof.db`/`kof.orm` (SQLite + MySQL scramble), G4 `kof.validation`, G5 `kof.observability`, G8 `kof.time sleep/interval`, G10 security Native, G9 rateLimit/session/apiKey, G12 TLS, 0.2.0 pattern matching + `String?` + `List map/filter/reduce`.

Prioridades e estratégia completas: `docs/ecosystem-coverage.md` §7-§8.