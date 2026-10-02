[English](stdlib.md) | [Português](stdlib.pt_BR.md)

# Kof Standard Library — Architecture

**Last updated:** September 12, 2026
**Version:** 0.5.0-beta

> Kof's Standard Library is the platform: HTTP, REST, auth, authorization,
> validation, serialization, database, messaging, observability and testing
> must be built with the platform itself — with no architectural dependency
> on external frameworks.

---

# 1. PRINCIPLE

```text
intent → Kof → result
```

The public API is simple; the internal implementation is efficient. The
compiler knows each stdlib call at compile-time and maps it to the most direct
runtime of each target (JVM bytecode, native assembly, idiomatic JS).

---

# 2. MECHANISM

Each stdlib module is a **compile-time dispatch table**:

```text
KofSecurity.java / KofWeb.java / KofIo.java / KofUi.java
        │
        ├── SemanticAnalyzer   → types of the calls (inference/checking)
        ├── CompilerDriver     → lowering to KofCall(kof_*)
        ├── JvmRuntime         → generated KofRuntime (javax.crypto, java.nio...)
        ├── NativeRuntime      → x86-64 assembly (syscalls, no libc)
        └── JsBackend          → kof-runtime.mjs (pure JS + kof_platform)
```

Target gaps produce **clear compile-time diagnostics** (SECN00x, CONC001,
JSN00x) — never silently different behavior.

> **Delivery:** the compiler includes only what the program uses (tree-shaking
> by reachability — see `docs/stdlib/stdlib-loading.md` with the numbers
> locked in `ArtifactSizeTest`).

---

# 3. MODULES

| Module | Status | Notes |
|--------|--------|-------|
| `kof.core` | ✅ | println, strings, arrays, arithmetic; `enum Name { A, B }` + `values()/valueOf()/name()` + `==` by content — 3 targets (`KofEnumTest`); **0.2.0**: pattern matching `case String s` + `Point(x,y)` and basic `String?` (3 targets) |
| `kof.collections` | ✅ | `List<T>` `listOf` + `map/filter/reduce` (0.2.0, 3 targets); `Map<K,V>` (mapOf + put/get/remove/contains/size/keys/values/clear/isEmpty) and `Set<T>` (setOf + add/contains/remove/size/clear/isEmpty) — **3 targets** (Native: own asm over the List layout; Set uses a type tag for equals); `Box<T>` via `substituteTypeVariable` `CompilerTypes.java:423` |
| `kof.io` | ✅ | `File/Path/Directory`, readFile/writeFile — JVM/Native/JS |
| `kof.time` | ✅ | `now()`, `sleep`, `interval`/`cancel` (3 targets — JS via cooperative queue pumped by `time.sleep`, TIME001 closed) + civil calendar `isLeapYear/daysInMonth/dayOfWeek/daysBetween` (4 targets) + `addDays`/`diffDays` on ISO date (5 targets: JVM/Script `java.time` + JS civil algorithm + x86 asm `RuntimeTimeIso` + riscv/aarch B33 — TIME002 closed 11/09) — `KofTimeE2ETest` |
| `kof.json` | ✅ | encode/decode; objects/records JVM+Native+JS (JSN002), Float/Double + `Double[]`/`Float[]` arrays (JSN001) and `Int[]/Long[]/Bool[]/String[]` arrays (JSN003) — Native complete 31/08 |
| `kof.http` | ✅ | `kof serve` (KofHttpServer, thread pool) — JVM; `kof.http` client `http.get/post/put/delete/patch/options/status` — JVM/JS/**Native 03/09** (HTTP/1.1 asm, `NativeHttpRuntime`, IPv4 only; https→throw); retry/circuit/timeout accept the call but are only implemented on JVM/JS (`HTTP003`). **Asynchronous:** `var h = spawn http.get(url); await h` → `Handle<String>` on the 3 targets — on Node/browser it is the only real path (fetch→Promise, §133; the synchronous call there returns the raw Promise — the synchronous face does not exist in pure JS, §133/HTTP003) |
| `kof.web` | ✅ | `web.app()`, routes, `app.use` middleware, `listenSecure(port)` TLS, `status(code[, body])`/`headerSet`, `app.ws` (WebSocket RFC 6455) + `app.sse` (SSE) — JVM (Native `WEB001/002` + `WEB003`/`WEB004`; JS base + SSE handler-scoped ✅ 16/09, residual `app.ws` = `WEB004`, SSE push after-return = `WEB003` compile-time) |
| `kof.security` | ✅ (v1 + G9) | passwords, crypto, jwt, secrets, auth, security, rateLimit, sessions, apiKeys — 3 targets; Native free-list 27/08 — see `docs/stdlib/security.md` |
| `kof.concurrent` | ✅ | `spawn` (statement) + `val r = spawn f()` / `await r` (typed handle) — JVM (virtual threads) + Native (pthread, 31/08, `CONC001` closed) + event-loop JS (`CONC003` closed 03/09) |
| `kof.test` | ✅ | `kof test` (`test "name" { }` on the 3 targets) + `assert` — `StructuredTestE2ETest` 11/11; golden 16/16 |
| `kof.cli` | ✅ | `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/deps/editor/new/init/version` (26 commands — see `docs/status.md`; debug DAP, `kof script --watch` SIGPIPE fix 27/08) |
| `kof.script` | ✅ | `KofScript` top-level `var`/`val` → static fields of `KofScriptGlobals` + REPL (`kof-script` suite 38/0; the old `let` sugar was removed in `183cb048` — see §263) |
| `kof.c` | ✅ | `KofCcompiler` C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → native x86_64 (5 kof-c-compiler tests, 27/08) |
| `kof.metrics` | ✅ | `kof bench`/`kof profile` (harness + baseline, 37 benchmarks, `benchmark.yml` threshold 1.20) |
| `kof.rest` | ⏳ | planned |
| `kof.database` | ✅ | `kof.db` (JVM JDBC: H2/MySQL/MariaDB/PostgreSQL; Native SQLite via direct `.so` + MySQL wire real on x86-64 — SHA-1 auth scramble; **riscv64/aarch64 SQLite ✅ 15/09** link-by-use `libsqlite3` + slices `RtB46/RtB47`; JS **✅ 16/09** untyped via GraalJS-host bridge, typed `query<T>` **✅ 18/09** (`DB002` closed)) + `kof.orm` (entity, create/save/saveAll/find/where/count/page/delete/deleteAll/migrate; **typed column in where/count: non-field literal → `ORM003` at compile-time**; JVM + MongoDB; Native `ORM001`, JS CLOSED 18/09 (`KofJsOrmBridge`)) — see `docs/stdlib/DATABASE_VISION.md` |
| `kof.messaging` | ✅ | `kof.mq` publish/subscribe/queue — **3 targets** (JVM in-memory; Native asm 01/09; JS in-process) — `KofMqE2ETest` 4/4 |
| `kof.supervisor` | ✅ | OTP core (issue #83): `supervisor(name).child(id,factory,policy)`+`restartLimit`+`escalate`+`start`/`stop`/`stats` — host **pure-Kof** injected by `import kof.supervisor` (android-host mechanism). **JVM+Script ✅ 11/09**, **Native x86 ✅ 15/09** (§129 closed — TLS per-thread chain + per-worker handler); **Native riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table `kof_exc_slots`; `OTP001` removed), **JS ✅ 18/09** (§132 closed — cooperative async `time.sleep`; `OTP002` lifted; `supervisorJsParity`). **S3 ✅ 14/09**: `restartLimitWindow(max, windowMs)` (sliding window, ring per child), `.clock(nowFn)` (injectable, DD-OTP-10), `stats().dropped` = temporary children actually dropped. `KofSupervisorE2ETest` 16/16 |
| `kof.validation` | ✅ | `validation.required/notBlank/minLength/maxLength/lengthBetween/isEmail/isUrl/matches/isInt/isLong/inRange/min/max` — JVM/Native/JS (`KofValidationTest` 3/3) |
| `kof.logging` | ✅ | `log.debug/info/warn/error`, levels, off — JVM+Native (asm, `kof_log_*`, 27/08) — `KofLogE2ETest` + `NativeLogE2ETest` |
| `kof.observability` | ✅ | `observability.health/readiness/liveness`, `counter`/`increment`/`gauge`/`histogram`/`metrics()`, `requestId()`/`correlationId()`/`traceId()`/`spanId()`/`spanStart`/`spanEnd`, **`exportSpans()` (OTel OTLP/JSON — JVM/JS; Native `OBS003`)** — JVM/Native/JS (`KofObservabilityTest` 10/10; `OBS002` 16/09, `OBS003` 17/09) |
| `kof.cache` | ✅ | `cache.get/set/set(key,v,ttl)/ttl/delete/clear` — 3 targets (native fix 30/08) — `KofCacheE2ETest` (5) |
| `kof.scheduler` | ✅ | `scheduler.every(n, fn)`/`cancel(id)` — JVM (ScheduledExecutor) + JS (setInterval) + Native (`SCHED001` closed 31/08 — thread per job, cross 05/09). `at(cron, fn)` = real 5-field UTC cron (JVM + JS, 17/09); Native refuses at compile time (`CRON001`) |
| `kof.process` | ✅ | `process.run`/`process.spawn` (subprocess with live stdin/stdout) — **JVM ✅ (ProcessBuilder) / JS ✅ (GraalJS host); Native `PROC001` at compile-time** — `DomainGapCodesTest` |
| `kof.config` | ✅ | `config.get/env/has`, `config.str/int/long/bool(name, fallback)`, `config.required`; `${key}` interpolation; `kof config gen` generates a template; precedence `KOF_CONFIG` > env `KOF_<KEY>` > profile > `kof.config` — 3 targets (JVM/Native asm `/proc/self/environ`/JS) — `KofConfigE2ETest` (11) |
| **STDLIB** (`math`/`strings`/`encoding`/`uuid`/`validation`-ext/`time`-calendar/`net`; namespace via `KofStd`/`KofTime`/`KofValidation`) | ✅ S1–S8 (partial natives) | **plan-stdlib-expansion.** `math`: `clamp/abs/sign/min/max/isEven/isOdd/isPositive/isNegative/isZero` (Int, 4 targets) + `sqrt(DOUBLE)->Double` (S1b, 10/09: FIRST Double — JVM/Script/JS/x86 `sqrtsd` + **riscv/aarch B32 `fsqrt.d` (MATH001 closed 11/09)**; NaN on <0 = IEEE; bug 94 records the interpreter's `NaN==NaN`) + `lerp(a,b,t)`/`percentage(part,total)` (Double->Double) + `isInteger`/`isDecimal(DOUBLE)->Bool` (S1b.1, 10/09: **pure SSE2** Double scalars — no libm; 5 targets — riscv/aarch B32 11/09; golden locked to the measured JVM oracle, isolated C harness 18/18) + `roundTo(value, decimals)` (S1b.3, 14/09: half-away-from-zero by deterministic decimal scaling, no libm; 5 targets — riscv/aarch B32; `decimals` Int, may be negative; arithmetic contract `roundTo(2.675,2)==2.68`; matrix `stdmathround`). `strings`: `isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count/capitalize/uncapitalize (S11 — mirror of capitalize, 5 targets)/reverse/repeat/truncate/padLeft/padRight` + `escapeHtml`/`escapeJson` (S3.1/S3.1c) + `removeWhitespace`/`normalizeWhitespace` (S3.2) + `indent(s, n)`/`dedent(s)` (S3.3, 5 targets: JVM/Script/JS/x86/riscv64/aarch64 B37) + word-converters `toCamelCase/toPascalCase/toSnakeCase/toKebabCase/slugify` — **all on the 4 targets** (**STRN001 closed 09/09**: riscv port B15 + golden diff on qemu). `encoding`: `hexEncode/Decode` + `urlEncode/Decode` (4 targets); `base64Encode/Decode` + `base64UrlEncode/Decode` (JVM/Script/JS/x86 + **riscv/aarch — ENC002 closed 09/09**: riscv port B23 + translator, identical tolerant spec). `uuid.v4` (JVM/x86/JS + **riscv/aarch — SECN000 closed 09/09**: getrandom(2) via ecall 278 in slice B25 + translator; R11 — only the OS primitive). `uuid.isUuid` (S3b-ext — canonical shape 36/hex/fixed hyphens, **5 targets — UUID001 closed in the beta→main merge 10/09**: slice B25 + translator; does not validate version/variant). `uuid.v7` (S3b.2 10/09 — RFC 9562: unix-ts-ms 48 bits BE in b0..b5 + version='7' (char 14) + variant 10xx (char 19, mask same as v4) + rand_a/rand_b; **5 targets**: JVM/Script (SecureRandom+currentTimeMillis), JS (Date.now+randomBytesHex), x86_64 (RuntimeUuid), riscv64 B25b + aarch64 translator (getrandom+kof_time_now); ts<0=>null on riscv, ts>2^48 low-48; locked by KofUuidTest uuidV7{Jvm,Js,Native,CrossArch}). `random`: beta face `randomInt(bound)`/`randomBoolean` (S10a) + `randomString(n, alphabet)` (S10b) + main face `double()/boolean()/int(bound)/hex(n)` (S10, 10/09) — **two faces coexist in the dispatch** (additive retrocompat; `double`/`hex` riscv/aarch in slice B27); **5 targets**; entropy only from the OS (x86 alias `kof_sec_random_int`/`hex`; riscv B27/B28 + translator; JS kof_platform/crypto; JVM SecureRandom); lenient edges (`b<=0→0`, `n<=0/empty alphabet→""`, `hex n<=0→null` on JVM/JS). `validation` ext: BR `isCpf/isCnpj/isCep/isPis/isNis` (4) + `formatCpf/formatCep/formatCnpj` (S12/S12b — BR punctuation, 5 targets, lenient face: wrong number of digits => original, never throws; formatPis out — ambiguous 11-digit mask), network `isIpv4/isIpv6/isMac/isPort` (4), Luhn `isCreditCard` (4), `isDomain` (4, RFC 1123 v1 without trailing dot/IDN). `time` calendar: `isLeapYear/daysInMonth/dayOfWeek/daysBetween` (4, no gate) + `isWeekend` (S7-ext — dayOfWeek>=6 wrapper, 5 targets) + `addDays`/`diffDays` on ISO date (S7a JVM/Script via `java.time`, S7b JS civil algorithm without `Date`, S7c x86 asm `RuntimeTimeIso`, **riscv/aarch B33 — TIME002 closed 11/09**; 5 targets). `net` ext: 6 scalars `scheme/host/port/path/query/fragment(STR->STR)` + `queryEncode/queryDecode` (S8, decision §4 of the plan; RFC 3986 subset v1 — no IPv6 brackets; absent=>""; never throws) on JVM/Script/JS/x86 (RuntimeUri); riscv/aarch — **NET001 closed 09/09** (B24 + translator). **`net` socket front (D-KOF-NET, TCP+UDP) — surface bound 01/10, EVERY target refuses with `NET002` until the runtime lands** (`KofNet.supportedOn` returns false for all socket verbs; plan `docs/development/network-kofnet-plan.md` — fatia 1 shipped, fatia 2 = JVM runtime). Semantics locked in the matrices `stdmath`/`stdsqrt`/`stdmathdouble`/`stdstrings*`/`stdenc`/`stdvalidation*`/`stdluhn`/`stdipv6`/`stddomain`/`stdescape`/`stdtime`/`stdtime2`/`stduuidform` (ConformanceMatrixTest; riscv/aarch run under qemu). Tutorial: `learn/39-stdlib.md`. Open gaps: NAT-STR01 (UTF-8 in the native converters), S10c CLOSED 13/09 (decision 6a: `random.randomBytesHex` alias of `hex`; `randomBytes` reserved; choice=idiom), bug 94 (interpreter `==` NaN — frozen semantics). MATH001 CLOSED 11/09 (Double math riscv/aarch B32) · TIME002 CLOSED 11/09 (ISO add/diff B33). |

---

# 4. DESIGN RULES

1. **Intent first**: `passwords.verify(pw, hash)` — never loose primitives
   for the application to assemble security.
2. **Secure by default**: automatic secure choices (PBKDF2 600k, fixed
   HS256, random salt, constant-time comparison).
3. **No ceremony**: no container injection, no annotations, no mandatory
   XML/yml configuration.
4. **No hidden overhead**: each abstraction must answer what its runtime
   cost is (docs/architecture/performance.md §8).
5. **Clear diagnostics**: target gaps are never silent.
6. **Java/Spring remain valid** as interoperability — never as an
   architectural dependency.

---

# 5. ECOSYSTEM AUDIT

The complete coverage matrix (inventory, gaps, dependencies,
architecture, priority and strategy) lives in **`docs/bugs-and-gaps/ecosystem-coverage.md`**
— the result of auditing the stdlib against the capabilities of a modern
platform (a checklist derived from the Spring ecosystem, used as a
capability matrix, not as an API specification).

Executive summary (0.5.0-beta, re-synced 17/09):

| Category | Status |
|-----------|--------|
| core/collections/io/time/json | DONE (3 targets; 0.2.0 adds `map/filter/reduce` + pattern matching + `String?`) |
| security (crypto, jwt, secrets, web auth + G9) | DONE (JVM/Native/JS core; web auth JVM; Native free-list 27/08) |
| web server (`web.app()`) + `kof.http` client | DONE (JVM; `kof.http` JVM+JS + retry/circuit; WebSocket/SSE JVM) |
| concurrency (`spawn` + `await`) | DONE (JVM + Native pthread (31/08) + event-loop JS (CONC003 03/09)) |
| test (`assert`, `kof test` `test "name" {}`) | DONE (3 targets, 16/16 golden, 9/9 integration) |
| observability | DONE (kof.observability: health/metrics/request IDs — JVM/Native/JS) |
| `KofScript` / `KofCcompiler` / riscv64/aarch64 targets | DONE (KofScript 8, KofC 5, riscv64 toolchain stable) |
| messaging (`kof.mq` 3 targets), scheduling (`scheduler` 3 targets — SCHED001 closed 31/08), sessions, rate limiting, TLS, WebSocket/SSE (JVM), `kof.cache` (3 targets) | DONE (real gaps: `WEB002` TLS, `WEB003/004` WS/SSE) |
| GC Native mark-sweep | ✅ real sweep 03/09 (`kof_gc_mark` conservative stack+bss + `kof_gc_sweep` → free-list + manual `kof_gc_collect_now`; `KofGcE2ETest` 3/3). ✅ **auto-collect on exhaustion landed 19/09** (D1-A, §260 CLOSED: blanket-spill of the 15 GPRs + `kof_spawn_count==0` gate + one-shot flag; `a904317e`) |

# 6. NEXT STEPS (residual post-0.2.0)

1. Complete Native aarch64 codegen (placeholder today)
2. ~~Complete GC mark-sweep~~ ✅ 03/09 (KofGcE2ETest 3/3)
3. ~~Complete native MySQL/MariaDB~~ — ✅ x86-64 closed 22/09 (scramble + lenenc + handshake/query/prepared + the 13 ORM faces, F2d1–F2d7; cross riscv64/aarch64 open)
4. Typed query DSL `User.query { where age > 18 }` (level 3 DATABASE_VISION)
5. `kof fmt` (P5) + complete LSP + DWARF/JS source maps debugger
6. tracing / OpenTelemetry (WebSocket/SSE ✅ JVM and `kof.cache` ✅ 3 targets closed 30/08)

Closed history: G7 SECN004, G6 structured `kof.test`, G3 `kof.config` (JVM+Native), G2 `kof.http` (JVM+JS), G1 `kof.db`/`kof.orm` (SQLite + MySQL scramble), G4 `kof.validation`, G5 `kof.observability`, G8 `kof.time sleep/interval`, G10 security Native, G9 rateLimit/session/apiKey, G12 TLS, 0.2.0 pattern matching + `String?` + `List map/filter/reduce`.

Complete priorities and strategy: `docs/bugs-and-gaps/ecosystem-coverage.md` §7-§8.
