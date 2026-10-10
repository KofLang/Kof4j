[English](ecosystem-coverage.md) | [Português](ecosystem-coverage.pt_BR.md)

# ECOSYSTEM-COVERAGE.md — Kof Ecosystem Coverage Matrix

> Audit of the Kof stdlib against the capability ecosystem of a modern
> platform (checklist derived from the Spring ecosystem, used as a
> **capability matrix**, not as an API specification).
>
> **Date:** September 2, 2026 · **Snapshot version:** 0.2.6-beta
> **Method:** audit of the repository (code + tests + docs) — see §2.
> **Build (on the audit date):** `mvn clean package` PASS, `mvn test` 810 (793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli), golden 16/16, integration 9/9, `scripts/package.sh` PASS, `release.yml` 2 jobs (`test-and-bump` → `package-and-release`) × 3 platforms, Windows SIGPIPE fix.
>
> **⚠️ Snapshot 09/02 — outdated numbers (09/16):** Kof is at
> **0.5.0-beta** and the suite has **3225 tests** across the 4 modules (2762 kof-compiler
> + 50 kof-script + 7 kof-c-compiler + 406 kof-cli; baseline measured 09/20 ~18:14, see
> `docs/status.md`); this document is a **capability inventory**,
> not the current gate. The status matrix (`DONE`/`PARTIAL`/
> `PLANNED`) mostly reflects 09/02; rows re-checked against the code were
> refreshed 17/09 (events/transactions/queues/DB adapters/tracing). Confirm
> against `docs/stdlib/stdlib.md` and `docs/backend-parity.md` before using a
> row as a live state.
> **Result:** no new implementation was done in this document —
> only inventory, matrix, gaps, priority and strategy. 0.2.6-beta adds `native.risc`/`native.arm` targets, free-list GC, pattern matching, `String?`, `KofScriptGlobals`, `KofCcompiler`; 08/30-31 adds Native spawn (pthread/CONC001), XMM FP (FLT001), full JSON on Native (JSN001/002/003), JVM WebSocket/SSE, `kof.cache` 3 targets, `kof.http` retry/circuit (JVM+JS), `kof fmt`/`kof config gen`, UI Phase 7 Router, native SQLite direct `.so`.

---

# 1. CLASSIFICATION

| Status | Meaning |
|--------|-------------|
| `DONE` | implemented and tested (at least JVM; see target columns) |
| `PARTIAL` | exists, with known limitations |
| `PLANNED` | designed/documented, not implemented |
| `NA` | does not apply to the platform (by design decision) |
| `EXTERNAL` | outside the stdlib (interoperability or external tool) |

Target columns: `JVM` / `Native` / `JS` = support for the capability in that
backend. `Docs` = path relative to `docs/` (or `—`). `Tests` = test file(s) in
`kof-compiler/src/test/java/dev/kof/compiler/`.

---

# 2. CURRENT KOF INVENTORY (audit summary)

## 2.1 stdlib mechanism

The stdlib is not a classic runtime library: each module is a
**compile-time dispatch table** in the compiler.

```text
Kof Code → SemanticAnalyzer (types) → CompilerDriver (lowering to KofCall "kof_*")
  → JvmRuntime   (generates dev.kof.runtime.KofRuntime.java)
  → NativeRuntime (x86-64 assembly, syscalls, no libc)
  → JsBackend    (kof-runtime.mjs + kof_platform)
```

Target gaps produce a compile-time diagnostic (SECN00x, CONC001,
JSN00x, WEB001) — never silent divergence.

## 2.2 Real surface (modules → Kof invocations)

| Module | Kof invocations | Source file | Tests |
|--------|----------------|-------------------|-------|
| `kof.core`/`kof.collections` | `println/print`, `String` (concat, length, indexOf, split...), `List<T>`, `listOf`, `map/filter/reduce` (0.2.0), pattern matching `case String s` + `Point(x,y)` (0.2.0), `String?` (0.2.0) | JvmRuntime/NativeRuntime/JsBackend | KofHigherOrderTest (5) + JvmE2ETest, NativeE2ETest, KofJsE2ETest |
| `kof.io` | `File/Path/Directory` (+methods), `readFile/writeFile/readLine` | KofIo.java | IoE2ETest (15) |
| `kof.time` | `now()`, `sleep`, `interval`/`cancel` (JVM/Native/JS — TIME001 closed) | KofTime.java | KofTimeE2ETest |
| `kof.json` | `json.encode/decode<T>` | JvmRuntime/NativeRuntime/JsBackend | JsonE2ETest (14) |
| `kof.security` | `passwords.*`, `crypto.*`, `jwt.*`, `secrets.*`, `security.*`, `auth.*` | KofSecurity.java | KofSecurityTest (22) |
| `kof.web` | `web.app()`, `app.get/post/.../use/listen/port/close`, `param/query/header/body/method/path`, `status(code, body)`, `headerSet`, `app.ws("/chat") { }` (WebSocket, 08/30), `sse.send/event/close` (SSE, 08/30), `app.configure`/`app.stats` (hardening, 09/04) | KofWeb.java + JvmWebRuntime.java + KofHttpServer.java | KofWebE2ETest (9), KofHttpServerTest (8), KofWebWsE2ETest (11), KofWsFrameTest (7), KofWebSseE2ETest (7), KofWebHardeningTest (6) |
| `kof.http` (client) | `http.get/post/put/delete/patch/options/status/timeout` (headers, JSON) + `retry`/`circuit` (08/30, 30s window, fail-fast) — JVM+JS (JS via `Java HttpClient` interop + fetch fallback) | KofHttp.java + JvmWebRuntime.java + KofJsRunner | KofHttpE2ETest (4, JVM+JS), KofHttpResilienceE2ETest (3, JVM+JS) |
| `kof.cache` | `cache.get/set/set(key,v,ttl)/ttl/delete/clear` (Map + TTL) — JVM/Native/JS (08/30) | JvmCacheRuntime.java / NativeRuntime (asm) | KofCacheE2ETest (5, x3 targets) |
| `kof.mq` | `mq.publish/subscribe/unsubscribe`, `queue/push/pop/size` (JVM + Native + JS; MQ001 closed 09/01) | KofMq.java + NativeRuntime (asm) | KofMqE2ETest (4, x3 targets) |
| `kof.concurrent` | `spawn expr` / `spawn { }` (implicit join) | JvmRuntime | SpawnE2ETest (3) |
| `kof.test` | `assert(cond[, msg])`, `test "name" { }` (synthesized runner), `kof test` | CompilerDriver/CLI | AssertE2ETest (5), StructuredTestE2ETest (11) |
| `kof.ui` | `Color/Theme/Palette`, `Window/Label/Button/Input`, `Column/Row/View/Style`, events by lambda with captures, native webview | KofUi.java, JsBackend (runtime), kof-webview.c | UiE2ETest (14), WindowE2ETest (3) |
| `kof.process` | `process.run(cmd)`/`run(cmd, args)`/`exit(code)`, `process.spawn` (live stdin/stdout) → `Result` | JvmRuntimeCore / KofProcess | ProcessE2ETest, KofProcess* — JVM/JS real; Native `PROC001` |
| `kof.shell` | `shell.cmd(program, args)`/`shell.run`/`shell.ok(r)`/`shell.pipeline(stages)` — argv-as-list, never `sh -c` | KofShell.java (sugar over `kof.process`; `kof_shell_pipeline` JVM helper) | ShellE2ETest (11) — JVM+JS real (`run`/`ok`/`cmd`), pipeline JVM real, JS/Native honest `PROC001` (18/09, `34e4344f`, plan `docs/shell-plan.md`) |
| `kof.interop` (engines, X2 fatias 1–3) | `KofPy(src)`/`KofR(src)` + `callInt/callDouble/callBool/callString/callJson(fn, listOf(...))` + `timeout(ms)`/`cancel()` — deadline runs in the CHILD, failures NAMED `INTEROP004`–`INTEROP008` (never exit-code guessing); JVM/x86/JS/Script CI-certified 26–27/09 — `experimental` until the cross encoders land (§514, R5); targets without a proven process runtime refuse at compile-time `INTEROP005` keeping the face (ANDROID/MCU/RISCV32/cross) | resources `interop-py-host.kf`/`interop-r-host.kf`/`interop-py-refusal.kf` — pure Kof over `kof.process`+`kof.json` (rule 12: the core supplies only the process mechanism) | `InteropPy*E2ETest` (8) · `InteropR*E2ETest` (12 — CI 10/10 skipped-0 with real R) · `InteropTimeout*E2ETest` (5) · refusal `InteropIdiomsCompileTest` (3) |
| `kof.config` | `config.get/env/has`, `config.str/int/long/bool(name, fallback)` — JVM/Native (file+profiles+env) + JS (env) | KofConfig.java | KofConfigE2ETest (8) |
| `kof.log` | `log.debug/info/warn/error`, levels (default INFO), `off`, warn→stderr | KofLog.java | KofLogE2ETest (7), NativeLogE2ETest (17) |
| `kof.cli` | 26 commands: `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/deps/editor/init/new/version` (`fmt` + `config gen` 08/31) | kof-cli | Bench, KofDebug E2E |

## 2.3 kof.security — inventory (6 namespaces, compile-time dispatch)

| Namespace | Call | JVM | Native | JS |
|-----------|---------|-----|--------|----|
| `passwords` | `hash/verify/needsRehash` (PBKDF2-HMAC-SHA256 600k) | ✅ | ✅ asm (G10, 08/25) | ✅ |
| `crypto` | `sha256`, `sha512`, `hmacSha256`, `encryptAesGcm/decryptAesGcm`, `randomHex`, `randomInt` | ✅ (sha512/AES-GCM) | ✅ asm: sha256/sha512/hmac/AES-GCM/random (G10, 08/25) | ✅ |
| `jwt` | `create(claims, secret[, ttl])`, `verify(token, secret[, iss, aud])`, `secret()` (fixed HS256, iat/exp) | ✅ | ✅ asm (G10, 08/25) | ✅ |
| `secrets` | `get(name)`, `get(name, fallback)` (env `KOF_*`), `redact(value)` | ✅ | ✅ (`/proc/self/environ`) | ✅ |
| `security` | `constantTimeEquals`, `csrfToken/csrfValid`, `corsAllowed`, headers (CSP/HSTS/nosniff/Frame/Referrer), `randomHex/randomInt`, `redact` | ✅ | ✅ constant-time/redact; ❌ csrf/cors/headers | ✅ constant-time/redact; ❌ csrf/cors/headers |
| `auth` (web) | `secret(token)`, `token()`, `authenticated()`, `claims()`, `user()`, `hasRole(r)`, `hasPermission(p)` (Bearer JWT + ThreadLocal per request) | ✅ | ❌ | ❌ |

Hash format: `pbkdf2$sha256$<iter>$<saltB64>$<hashB64>`;
AES-GCM: `aesgcm$<ivB64>$<ctB64>` (key 32B, IV 12B).
Documentation: `docs/stdlib/security.md`; tests: `KofSecurityTest` (22).

## 2.4 kof.web — inventory

- `web.app()` → routes `app.get/post/put/delete/patch/options(path) { }`,
  middleware `app.use { }`, `app.listen(port)` (blocking, virtual
  threads), `app.port()`, `app.close()`; `status(code, body)`/`headerSet`
  (08/27).
- **WebSocket** `app.ws("/chat") { }` — RFC 6455 handshake + frame codec with
  masking (08/30); **SSE** `sse.send/event/close` (08/30) — both JVM;
  **hardening/observability** `app.configure` (`maxConnections`,
  `maxFrameBytes`, `maxMessageBytes`, `idleMs`) + `app.stats` (09/04) — JVM.
- Context: `param/query/header/body/method/path` (ThreadLocal per request).
- Path params `:id`; query and headers case-insensitive; automatic
  Content-Type (JSON if `{`/`[`); 404/500; chained middlewares.
- Engine: `WebRoute/WebRequest` generated in KofRuntime; `KofHttpServer`
  (legacy `kof serve`, `ReflectiveHandler`).
- Targets: JVM ✅ (incl. ws/sse); Native ✅ base (Native web server in asm:
  `kof_web_*` — `NativeWebCore`/`Listen`/`Responses`/`Runtime`; proof
  `KofWebNativeE2ETest` 4/4 in the 09/12 gate; the tail remains per-feature
  gaps — TLS `WEB002`, ws `WEB004`, sse `WEB003`); JS ✅ WEB001-T1
  (09/13: hostless GraalJS server — `kofWeb*` in `JsRuntimeUiWeb` + Java queue
  `KofJsWebQueue` (Context thread-confined → event-loop in `kofWebListen`);
  handler return = 200 body same as JVM; pure JS UTF-8 encoder (host
  instance interop does not expose methods in this build); proof `KofWebJsE2ETest`
  (routes exact/:param/query/method/path/POST-body over real sockets).
- Tests: `KofWebE2ETest` (9, real sockets), `KofHttpServerTest` (8),
  `KofWebWsE2ETest` (11), `KofWsFrameTest` (7), `KofWebSseE2ETest` (7).
- Docs: `docs/stdlib/stdlib-web.md`.

## 2.5 Runtimes

| Runtime | Location | Contents |
|---------|-------|----------|
| JVM | generated at compile (`dev.kof.runtime.KofRuntime`) | json, io, time, spawn, web, security, ui |
| Native | `NativeRuntime.java` (x86-64 asm, no libc) | strings, lists, json, io, sec (partial), net (symbols), time, print |
| JS | `JsBackend` generates `kof-runtime.mjs` + `kof-runtime-io.mjs`; `kof-runtime` module = `KofJsRunner` (embedded GraalJS) | language, io via `kof_platform`, sec, ui (DOM/webview) |

## 2.6 Tests (810 JUnit: 793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli) — per module (08/27)

Security (22) · CompilerDriver (190) · Native E2E (50) · KofJS E2E (35) ·
JVM E2E (29) · Optimizer (21) · Io (15) · Json (14 + complete 7) · CoreRegression (14) ·
BackendParity (10) · Exceptions (9) · Web E2E (9) · HttpServer (8) ·
**KofConfig (8 + Native 8)** · **KofLog (10 + Native 7)** · Idiomatic (7+6) · Ui (14) · Assert (5) ·
FunctionSyntax (4) · Lambda (4) · **KofTime (5)** · **KofMq (4)** ·
**KofHttp (4, JVM+JS) + Resilience (3, JVM+JS 08/30)** · TuringComplete (3) · **KofOrm (12+, E2E MariaDB/Postgres + MongoDB + SQLite native)** ·
**KofDb (8, + SQLite `.so` + MySQL wire x86-64 real)** · Spawn (3) · Window (3) · IRStatistics (2) · DebugInfo (2) ·
NativeDebug (5) · StructuredTest (11) · AndroidInterop (11) · **KofScript (8)** · **KofCcompiler (5)** ·
**KofWs (11) + KofWsFrame (7) + KofSse (7) + KofCache (5, x3 targets) + Router (E2E)** (08/30-31).
Golden: `tests/golden/` 16/16 (8 cases × jvm+native). Integration: `tests/run-integration.sh` 9/9. `mvn test` 810.

## 2.7 Benchmarks (37, in 17 categories, `kof bench` PASS)

micro, algorithms, collections, strings, math, objects, inheritance,
interfaces, generics, json, io, concurrency, startup, memory, stress,
applications + `benchmarks/security/` (password-hash, jwt, hash-speed,
aes-gcm). Tooling: `kof bench` (median + RSS + baseline), `kof profile`.

---

# 3. COVERAGE MATRIX

Legend in the target columns: `y` = supported, `~` = partial, `–` = no.
`Docs`: `security.md` = `docs/stdlib/security.md`; `stdlib.md` = `docs/stdlib/stdlib.md`;
`web` = `docs/stdlib/stdlib-web.md`; `concurrency` = `docs/language-reference/concurrency.md`.

## 3.1 Core / Application

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| application lifecycle | `main()`/`args` | y | y (empty args) | y | UiE2ETest | history/language-state.md |
| configuration model | `config.get/str/int/long/bool/has` (file + env + profiles) | y | y | y (CONF001 closed 16/09) | KofConfigE2ETest | stdlib/stdlib.md |
| dependency injection | `NA` (no container; direct resolution) | — | — | — | — | philosophy.md |
| events | ✅ event bus via `kof.mq` (publish/subscribe/unsubscribe) — see §3.4 | y | ✅ | ✅ | KofMqE2ETest | stdlib/stdlib.md |
| validation | ✅ `kof.validation` (required/notBlank/minLength/maxLength/lengthBetween/isEmail/isUrl/matches/isInt/isLong/inRange/min/max) — JVM/Native/JS | y | y | y | KofValidationTest | stdlib/stdlib.md |
| scheduling | ✅ `kof.time` now/sleep + interval/cancel (3 targets — TIME001 closed) | y | y | y | KofTimeE2ETest | stdlib/stdlib.md |
| caching | ✅ `kof.cache` (get/set/ttl/delete/clear; 08/30) | y | y (asm) | y | KofCacheE2ETest (5, x3) | development/roadmap.md |
| transactions | ✅ `transaction {}` (JVM; real commit/rollback) | y | y (asm 01/09) | ✅ 16/09 (JS bridge) | KofDbE2ETest | stdlib/DATABASE_VISION.md |
| resource management | `PARTIAL` (real try/finally) | y | y | — | ExceptionsE2ETest | history/language-state.md |
| profiles/environments | `PARTIAL` (profile file + env; the rest in kof.config) | y | y | – CONF001 | KofConfigE2ETest | — |

## 3.2 Web / HTTP / REST

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| HTTP server | `web.app()` | y | ✅ base 03/09 | ✅ T1 (09/13) | KofWebE2ETest, KofWebJsE2ETest | stdlib/stdlib-web.md |
| routing (path params, query, headers) | `app.get("/users/:id")` | y | – | ✅ T1 (09/13) | KofWebE2ETest, KofWebJsE2ETest | stdlib/stdlib-web.md |
| REST verbs | get/post/put/delete/patch/options | y | – | ✅ get/post (T1) | KofWebE2ETest, KofWebJsE2ETest | stdlib/stdlib-web.md |
| JSON body | automatic (Content-Type) | y | – | – | KofWebE2ETest | stdlib/stdlib-web.md |
| middleware | `app.use` | y | – | – | KofWebE2ETest | stdlib/stdlib-web.md |
| HTTP client | ✅ `kof.http` (get/post/put/delete/patch/options/status; 3 targets — Native via HTTP/1.1 asm, https/retry) | y | y (asm `NativeHttpRuntime`) | y (GraalJS `Java HttpClient` + fetch) | KofHttpE2ETest (6) + KofHttpResilienceE2ETest (3, JVM+JS) | stdlib/http.md |
| typed path/query/body | `PLANNED` (today strings) | — | — | — | — | stdlib/stdlib-web.md |
| custom status codes | ✅ `status(201, body)` (08/27) | y | – WEB001 | ✅ 16/09 (§265) | KofWebE2ETest, KofWebJsE2ETest `jsWebStatusAndHeaderReachTheWire` | stdlib/stdlib-web.md |
| custom response headers | ✅ `headerSet("X","y")` (08/27) | y | – WEB001 | ✅ 16/09 (§265) | KofWebE2ETest, KofWebJsE2ETest `jsWebStatusAndHeaderReachTheWire` | stdlib/stdlib-web.md |
| cookies | `PLANNED` | — | — | — | — | development/roadmap.md |
| multipart | `PLANNED` | — | — | — | — | — |
| content negotiation | `PLANNED` | — | — | — | — | — |
| error handling | 404/500 + message | y | – | ✅ 03/09 (JS 404/500) | KofWebE2ETest | stdlib/stdlib-web.md |
| WebSocket | ✅ `app.ws("/chat") { }` (JVM, 08/30 — RFC 6455 handshake + frame codec/masking) | y | – WEB004 | – WEB004 (compile-time gate 16/09; was silent no-op) | KofWebWsE2ETest (11) + KofWsFrameTest (7) | stdlib/stdlib-web.md |
| SSE | ✅ `sse.send/event/close` (JVM, 08/30) | y | – WEB003 | ✅ handler-scoped 16/09 (`app.sse` + `send/event/close/isOpen` + `sse()`, framing/headers same as JVM; push after-return/multi-client = WEB003 residual) | KofWebSseE2ETest (7) · KofWebJsE2ETest sse (3) | stdlib/stdlib-web.md |
| web limits/observability | ✅ `app.configure`/`app.stats` (JVM, 09/04) | y | – | – | KofWebHardeningTest (6) | stdlib/stdlib-web.md |
| gRPC / GraphQL / SOAP | `EXTERNAL`/`PLANNED` (interop) | — | — | — | — | development/roadmap.md |
| REST documentation (OpenAPI) | `PLANNED` | — | — | — | — | development/roadmap.md |
| HATEOAS | `NA` (no heavy framework) | — | — | — | — | — |

## 3.3 Data / Database

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| SQL / JDBC | ✅ `kof.db` (SQL-first) + native SQLite via direct `.so` + MySQL wire protocol (handshake+scramble+auth-switch+COM_QUERY+resultset, 08/31) | y | y (SQLite + MySQL wire) | ✅ 16/09 (JDBC via GraalJS host) | KofDbE2ETest | stdlib/DATABASE_VISION.md |
| `db.connect/query/transaction` | ✅ (+ typed `query<T>`) | y | y | ✅ untyped 16/09; typed `query<T>` `DB002` CLOSED 18/09 | KofDbE2ETest | stdlib/DATABASE_VISION.md |
| prepared statements | ✅ (`?` binds) | y | y | ✅ 16/09 (binds via bridge) | KofDbE2ETest | — |
| connection pools | `PLANNED` | — | — | — | — | — |
| migrations | ✅ versioned `orm.migrate` (`kof_migrations`) | y | x86-64 ✅ 22/09 (F2d1–F2d7); cross = ORM001 | ✅ 18/09 | KofOrmE2ETest | stdlib/DATABASE_VISION.md |
| repositories/ORM | ✅ `kof.orm`: `entity` + create/save/find/all/where/delete/count | y | x86-64 ✅ 22/09 (F2d1–F2d7); cross = ORM001 | ✅ 18/09 | KofOrmE2ETest | stdlib/DATABASE_VISION.md |
| NoSQL (MongoDB) | ✅ official driver via compatible reflection | y | — | — | KofOrmE2ETest (E2E, conditional skip) | stdlib/DATABASE_VISION.md |
| mapping | ✅ entity → row/document by compile-time schema | y | – | y | JsonE2ETest, KofOrmE2ETest | — |
| typed query DSL (`User.query { where ... }`) | ✅ (level 3, 01/09 — lowers to `db.query<T>`; JVM H2 E2E) | ✅ | — | — | KofOrmE2ETest | stdlib/DATABASE_VISION.md |
| pagination | ✅ `orm.page(page, size[, where])` | y | – | – | KofOrmE2ETest | stdlib/DATABASE_VISION.md |
| PostgreSQL / MySQL / SQLite / MongoDB / Redis | ✅ SQLite (native `.so`) / MySQL (wire protocol) / MongoDB (driver) — see rows above; PostgreSQL + Redis `PLANNED` | y | y (SQLite) | — | KofDbE2ETest, KofOrmE2ETest | stdlib/DATABASE_VISION.md |
| transactions | ✅ `transaction {}` (JVM; commit/rollback; JS bridge 16/09) — see §3.1 | y | y (asm 01/09) | ✅ 16/09 | KofDbE2ETest | stdlib/DATABASE_VISION.md |
| optimistic/pessimistic locking | `PLANNED` | — | — | — | — | — |

## 3.4 Messaging

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| event bus / pub-sub | ✅ `kof.mq` (publish/subscribe/unsubscribe + queue/push/pop) — JVM + Native + JS (MQ001 closed 01/09) | y | ✅ | ✅ | KofMqE2ETest (4, x3 targets) | concurrency |
| queues | ✅ producer/consumer realized as `channel<T>()` (`send`/`receive`, blocking `take`) — the plan's name was `kof.concurrent.Queue` | y | y | y | ConformanceMatrixTest, AndroidInteropE2ETest | language-reference/concurrency.md |
| Kafka / AMQP / Pulsar | `PLANNED` (external adapters) | — | — | — | — | development/roadmap.md |
| retry / dead-letter / backpressure | `PLANNED` | — | — | — | — | — |
| consumer groups | `PLANNED` | — | — | — | — | — |

## 3.5 Security (kof.security)

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| password hashing (PBKDF2 600k) | `DONE` | y | y (asm, G10) | y | KofSecurityTest | stdlib/security.md |
| SHA-256 / SHA-512 / HMAC | `DONE` | y | y (asm, G10) | y | KofSecurityTest | stdlib/security.md |
| AES-GCM | `DONE` | y | y (asm, G10) | ✅ (01/09, SECN002 closed — pure JS) | KofSecurityTest | stdlib/security.md |
| SecureRandom | `DONE` | y | y (getrandom) | y | KofSecurityTest | stdlib/security.md |
| JWT (HS256, exp/iss/aud) | `DONE` | y | y (asm, G10) | y | KofSecurityTest | stdlib/security.md |
| secrets (`secrets.get`, env) | `DONE` | y | y | y | KofSecurityTest | stdlib/security.md |
| constant-time comparison | `DONE` | y | y | y | KofSecurityTest | stdlib/security.md |
| redaction | `DONE` | y | y | y | KofSecurityTest | stdlib/security.md |
| CSRF | `DONE` (JVM) | y | – | – | — | stdlib/security.md |
| CORS | `DONE` (JVM) | y | – | – | — | stdlib/security.md |
| security headers (CSP/HSTS/nosniff/Frame/Referrer) | `DONE` (JVM) | y | – | – | — | stdlib/security.md |
| web auth (Bearer JWT + roles/permissions) | `DONE` (JVM) | y | – | – | — | stdlib/security.md |
| RBAC / ABAC | `PARTIAL` (auth.hasRole/hasPermission JVM) | y | – | – | — | stdlib/security.md |
| API keys | `PLANNED` | — | — | — | — | stdlib/security.md |
| rate limiting | ✅ `security.rateLimit(key, limit, window)` — JVM/Native/JS | y | y | y | KofSecurityG9Test | stdlib/security.md |
| sessions | ✅ `security.sessionCreate/sessionGet/sessionDestroy` — JVM/Native/JS | y | y | y | KofSecurityG9Test | stdlib/security.md |
| API keys | ✅ `security.apiKeyGenerate/apiKeyValid` — JVM/Native/JS | y | y | y | KofSecurityG9Test | stdlib/security.md |
| OAuth2 / OIDC (client, resource server, provider) | `PLANNED` | — | — | — | — | stdlib/security.md |
| TLS / certificates / HTTPS | ✅ `web.listenSecure(port)` + `kof.http` HTTPS — JVM (self-signed via keytool) | y | — WEB002 | — WEB002 | KofWebTlsTest | stdlib/http.md |
| secure cookies | `PLANNED` | — | — | — | — | — |
| token rotation / replay protection | `PLANNED` | — | — | — | — | — |
| audit logging | `PLANNED` | — | — | — | — | — |
| request signing | `PLANNED` | — | — | — | — | — |
| service-to-service auth | `PLANNED` | — | — | — | — | — |
| key management | `PLANNED` (today: env `KOF_*`) | y | y | y | — | stdlib/security.md |

## 3.6 Identity

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| OAuth2 client / resource server / authorization server | `PLANNED` | — | — | — | — | stdlib/security.md |
| OIDC provider | `PLANNED` | — | — | — | — | — |
| session management | `PLANNED` | — | — | — | — | — |
| LDAP / Kerberos | `EXTERNAL` | — | — | — | — | — |
| machine-to-machine auth | `PLANNED` | — | — | — | — | — |

## 3.7 Integration / Resilience

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| HTTP integrations | ✅ `kof.http` client (3 targets — Native asm HTTP/1.1) | y | y | y | KofHttpE2ETest | stdlib/http.md |
| file adapters | `DONE` (kof.io) | y | y | y | IoE2ETest | stdlib/IO.md |
| retry / timeout / circuit | ✅ `kof.http` `retry`/`timeout`/`circuit` (JVM+JS, 08/30; **Native x86_64+riscv64+aarch64 17/09 — §259 CLOSED: real nonblock+ppoll-deadline+SO_RCVTIMEO / N+1 attempts / 30s-window fail-fast, cross-parity messages** | y | ✅ §259 | y | KofHttpResilienceE2ETest | stdlib/http.md |
| circuit breaker / bulkhead | ✅ circuit breaker `kof.http` (08/30, 30s window, fail-fast; **§259 CLOSED — real on JVM/JS and the 4 native targets**); bulkhead `PLANNED` | y | y | y | KofHttpResilienceE2ETest | stdlib/http.md |
| idempotency | `PLANNED` | — | — | — | — | — |

## 3.8 Batch

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| jobs/steps/pipelines/checkpoints | ✅ `kof.workflow` — host puro-Kof `job`/`dag`/`after`/`run`/`Report` (2.1.2) + `checkpoint` (`ee63dc80`) | y | — (recon; compile ✅ `StdlibIdiomsCompileTest`) | y | WorkflowE2ETest 23, WorkflowPrimitivesE2ETest 6 | stdlib/workflow.md |
| retries / resumability / parallel | ✅ `flow.retry`/`retryFixed`/`exponential` (`7db91735`) + dead-letter (`95f81747`, `Report.dead` + fs) + DAG topo-order | y | — (recon; checkpoint-over-ORM = `ORM001`) | y (byte parity §387) | WorkflowE2ETest | stdlib/workflow.md |
| scheduling | ✅ `schedule(expr, dag)` → `scheduler.at` (`95f81747`/`d9adeb03`) | y | — (`CRON001` honesto) | y (§274 5-field UTC) | WorkflowE2ETest, StdlibIdiomsCompileTest | stdlib/workflow.md |

## 3.9 Observability

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| metrics (runtime API) | ✅ `kof.observability.counter/increment/gauge/histogram/metrics()` — JVM/Native/JS | y | y | y | KofObservabilityTest | stdlib/observability.md |
| health checks / readiness / liveness | ✅ `kof.observability.health/readiness/liveness` — JVM/Native/JS | y | y | y | KofObservabilityTest | stdlib/observability.md |
| tracing / OpenTelemetry | `DONE` (JVM/JS) — W3C `traceId`/`spanId` + timed `spanStart`/`spanEnd`/`metrics()` (3 targets, `OBS002`) and OTel **export** (`exportSpans()` → OTLP/JSON `resourceSpans`) on JVM/JS (`OBS003`); Native export = honest compile-time gap (`OBS003`) | y | y | y | KofObservabilityTest 10/10 | stdlib/observability.md |
| structured logging | `log.debug/info/warn/error` (levels, stderr) | y | y (asm, UTC) | y (console.*, 09/01) | KofLogE2ETest, NativeLogE2ETest | — |
| correlation IDs / request IDs | ✅ `kof.observability.requestId/correlationId` — JVM/Native/JS | y | y | y | KofObservabilityTest | stdlib/observability.md |
| request IDs | ✅ `kof.observability.requestId` — JVM/Native/JS | y | y | y | KofObservabilityTest | stdlib/observability.md |
| profiling / runtime diagnostics | `PARTIAL` (`kof profile`) | y | y | – | — | architecture/performance.md |
| resource monitoring | `PARTIAL` (native memstats, RSS in bench) | – | y | – | Bench | architecture/performance.md |

## 3.10 Configuration

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| environment variables | `DONE` (`secrets.get`, `KOF_*`, `config.env`) | y | y | y | KofSecurityTest, KofConfigE2ETest | stdlib/security.md |
| command-line arguments | `DONE` (`main(args)`) | y | y (empty) | y (empty) | UiE2ETest | history/language-state.md |
| config files / profiles / precedence | `DONE` (JVM/Native: explicit file > env > profile > default; JS: env) | y | y | y | KofConfigE2ETest | stdlib/stdlib.md |
| typed configuration | `DONE` (`config.str/int/long/bool`) | y | y | y | KofConfigE2ETest | — |
| hot reload | `PLANNED`/`NA` | — | — | — | — | — |

## 3.11 Testing

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| `assert(cond[, msg])` | `DONE` | y | y (free-list) | y | AssertE2ETest | history/language-state.md |
| `kof test` (per-file, exit code) | `DONE` | y | y | y | — | development/roadmap.md |
| structured suite `test "name" { }` | `DONE` (`test "name" { }` + `kof test` on the 3 targets, `CompilerDriver.java:1`) | y | y | y | StructuredTestE2ETest | development/roadmap.md |
| HTTP testing | `DONE` (E2E with sockets) | y | — | — | KofWebE2ETest | stdlib/stdlib-web.md |
| mocks / fixtures | `PLANNED` | — | — | — | — | — |
| property testing / stress | `PARTIAL` (stress benchmarks) | y | y | – | Bench | architecture/performance.md |
| test containers | `NA`/`EXTERNAL` | — | — | — | — | — |
| golden tests | `DONE` | y | y | — | tests/golden | — |

## 3.12 CLI / Shell

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| full `kof` CLI | `DONE` (26 commands: build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/deps/editor/init/new/version — `fmt` + `config gen` 08/31) | y (native.risc/native.arm) | y (free-list + pthread) | y (GraalJS) | — | tooling/ |
| `kof script` / `kof repl` | `DONE` (top-level `var`/`val` → `KofScriptGlobals`, `--watch`, SIGPIPE fix) | y | y | y | KofScript | stdlib/stdlib.md |
| `kof c` (KofCcompiler) | `DONE` (C subset `while/if/deref &/*` → ELF x86_64) | — | y x86_64 native-only | — | KofCCompilerTest | architecture/architecture.md |
| command parsing (in Kof) | `PLANNED` (`kof.cli` as lib) | — | — | — | — | development/roadmap.md |
| interactive CLI / prompts / progress | `PLANNED` | — | — | — | — | — |

## 3.13 Modular Architecture

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| multi-file modules | `PLANNED` | — | — | — | — | development/roadmap.md |
| domain modules / boundaries | `PLANNED` | — | — | — | — | — |
| modules as a native construct (`service UserService { }`) | `PLANNED` | — | — | — | — | — |
| architecture tests | `PLANNED` | — | — | — | — | — |

## 3.14 AI (investigation)

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| model clients / embeddings / RAG / tool calling | `PLANNED` (external module or future stdlib — pending decision) | — | — | — | — | — |

## 3.15 Interoperability

| Capability | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| call Java | `DONE` (direct interop) | y | – | – | CompilerDriverTest | architecture/architecture.md |
| Spring | `EXTERNAL` (`kof spring starter` planned — start.spring.io) | — | — | — | — | DECISIONS.md §D-SPRING |
| JS (Node/browser) | `PARTIAL` (embedded GraalJS, `kof_platform`) | — | — | y | KofJsE2ETest | targets/KOFJS.md |
| libc | `NA` (native without libc) | — | — | — | — | architecture/architecture.md |

---

# 4. CRITICAL GAPS (P0 priority)

| # | Gap | Impact | Proposed location |
|---|-----|---------|----------------|
| G1 | ~~**Database/SQL** nonexistent~~ — ✅ **level 0 implemented**: `kof.db` (JDBC JVM, native SQLite, MySQL wire x86-64 real) + `kof.orm` (entity, CRUD, where, migrate, MongoDB) | real apps with persistence on JVM/Native-SQLite | ✅ typed query DSL (01/09) + kof.db on JS (16/09) + `kof.orm` on JS (18/09, `ORM001` closed); remaining: pools; ORM on **Native** x86-64 ✅ 22/09 (13/13 faces, F2d1–F2d7), `ORM001` only on riscv64/aarch64 |
| G2 | ~~**HTTP client** nonexistent~~ — ✅ **implemented**: `kof.http` client (get/post/put/delete/patch/options/status/timeout + retry/circuit 08/30, headers; Native HTTP/1.1 asm, §259 resilience real on the 4 natives) | integrations, tests, frontend | ✅ closed — `KofHttpE2ETest` (4, JVM+JS) + `KofHttpResilienceE2ETest` (3) |
| G3 | ~~Configuration~~ — ✅ `kof.config` implemented (file > env > profile > default, typed `str/int/long/bool`); **native CONF001 closed** (asm `/proc/self/environ`); JS: CONF001 closed 16/09 | — | — |
| G4 | ~~**Validation** nonexistent~~ — ✅ **implemented**: `kof.validation` (13 predicates on the 3 targets) | — | `KofValidationTest` (3/3) |
| G5 | ~~**Partial runtime observability**~~ — ✅ **implemented**: `kof.observability` (health/readiness/liveness, counter/increment/gauge, requestId/correlationId — JVM/Native/JS; `KofObservabilityTest` 10/10 incl. OBS003) | — | `KofObservabilityTest` |
| G6 | ~~**Structured kof.test** nonexistent~~ — ✅ **implemented**: `test "name" { }` on the 3 targets; runner synthesized at compile-time; PASS/FAIL by name + exit code (`StructuredTestE2ETest`) | tests as first-class citizens | timeouts ✅ 19/09 (`kof test --timeout <sec>` — the harness process is killed and the FAIL is reported, R6; `CmdTestTimeoutTest` 3/3); **named suites by directory ✅ 21/09** (`kof test <dir>` recurses and reports one named suite per directory with a per-suite summary; `CmdTestSuiteTest` 2/2); next: **`SG-023` ✅ DECIDED 21/09 (`D-PROPERTY`)** — no new surface: property = `test`+`kof.rng`+`assert` idiom, fixtures = `close()`+`try/finally` (D5-B); proof `PropertyTestIdiomE2ETest` 7/7 (JVM==JS==Native checksum) |
| G7 | ~~**Incomplete target diagnostics in security/web**~~ — ✅ **closed**: `jwt.*` with explicit input (SECN004 on Native); `csrf/cors/auth/headers` already covered; WEB001 emitted for web.app() and app methods outside the JVM | violates "never silent" | keep: every new function enters `supportedOn` in the same PR |
| G8 | ~~**Scheduling** nonexistent~~ — ✅ `kof.time.sleep` + `interval`/`cancel` 3 targets (`KofTimeE2ETest` 39/0/7skip; Native reuses scheduler, JS cooperative queue — TIME001 closed 09/02); **cron ✅ 17/09**: `scheduler.at(cron, fn)` = real 5-field UTC parser on JVM/JS (lists/ranges/steps, DOM∨DOW classic rule; invalid → loud throw), Native honest compile-time gap `CRON001` (§274) | periodic jobs | next: no pending item (cron closed) |
| G9 | ~~**Rate limiting / sessions / API keys** nonexistent~~ — ✅ **implemented**: `security.rateLimit`/`sessionCreate`/`sessionGet`/`sessionDestroy`/`apiKeyGenerate`/`apiKeyValid` — JVM/Native/JS (`KofSecurityG9Test` 3/3) | — | `KofSecurityG9Test` |
| G10 | ~~kof.security on Native~~ — ✅ **closed**: PBKDF2, SHA-512, JWT HS256 and AES-GCM in asm (SECN001-004) | — | keep the test vectors (FIPS 197, NIST SP 800-38D, RFC 7519) |
| G11 | ~~**Lambdas with capture**~~ — ✅ **capture implemented** (mutable via synthetic box `BoxN` + capture by value; `Lambda0`/`Box0` generated); **Map/Set** (COL001) and **await/join** (with unboxing) both closed in 0.1.0 — no open face | expressiveness | compiler (documented in backend-parity.md) |
| G12 | ~~**TLS/HTTPS** on the web server~~ — ✅ **implemented**: `web.listenSecure(port)` (JVM, `SSLServerSocket` + `keytool` self-signed, `SAN=IP:127.0.0.1,DNS:localhost`; `kof.http` trust-all) — Native/JS report `WEB002` | — | `KofWebTlsTest` 7/7 (incl. own PKCS#8 PEM cert) |

---

# 5. DEPENDENCIES BETWEEN MODULES

```text
kof.config ──────────────► kof.database (DSN, credentials)
   │                        │
   ├──► kof.validation ────┤  (schema, payloads)
   │                        │
   ├──► kof.security ──────┘  (secrets, field encryption)
   │
   ├──► kof.observability     (logging/metrics config)
   │
   └──► kof.web               (profiles, secrets)

kof.http (client) ──► kof.web (server) ──► kof.security (web auth)
      │                    │
      │                    └──► kof.observability (request IDs, metrics)
      │
      └──► kof.test (HTTP E2E)

kof.database ──► kof.concurrent (pools, async) ──► kof.time (timeouts)

kof.security ──► kof.io (files/certs) ──► kof.json (JWT claims, config)
```

Rule: low-level modules (`kof.core`, `kof.io`, `kof.json`,
`kof.time`) never depend on high-level modules. `kof.security` is
critical infrastructure: nothing depends on it to exist, but everything
exposed to the world depends on it to be secure.

---

# 6. PROPOSED ARCHITECTURE

```text
kof.core / kof.collections / kof.io / kof.time / kof.json
      │
      ├── kof.security          (crypto, secrets, auth, web security)
      ├── kof.config            (env + files + profiles, typed)
      ├── kof.concurrent        (spawn, queues, async, await)
      │
      ├── kof.http              (server + client, REST, middleware)
      ├── kof.validation
      ├── kof.database          (SQL-first, transactions, migrations)
      │
      ├── kof.observability     (logging, metrics, health, tracing)
      ├── kof.test              (structured suites)
      ├── kof.messaging         (event bus, queues, adapters)
      └── kof.cli               (arg parsing in Kof)
```

Principles maintained:

1. **Intention → Kof → stdlib → runtime/backend → platform** — never
   "disguised Java API", never "framework + annotations + reflection +
   container" when the compiler solves it.
2. **No ceremony**: no `@Service/@Repository/@Autowired` as a paradigm;
   native constructs (`service UserService { }` planned).
3. **Secure by default**: TLS when applicable, secure cookies,
   constant-time, redaction, leak-free errors, timeouts, limits.
4. **Honest multiplatform**: JVM/Native/JS; when a capability does not
   exist on a target: clear diagnostic (SECN00x/CONC001/JSN00x), never
   silent divergence.
5. **Performance**: Kof → IR → direct code; no unnecessary
   reflection/indirection at runtime.
6. **`new` is not required**: `User(...)` is the idiomatic form (already
   in force in the guidelines — `User("Mel")`); docs/learn/examples prefer
   the form without `new`.
7. **Spring = optional interoperability**: `kof spring starter`
   (planned) queries start.spring.io and generates a compatible project —
   the stdlib does not copy Spring's model.

---

# 7. PRIORITY

## P0 — base platform (now)

1. ~~G7~~ — ✅ complete target diagnostic in security/web.
2. ~~G6~~ — ✅ structured `kof.test` (`test "name" { }` on the 3 targets,
   runner synthesized at compile-time, `process.exit(code)`).
3. ~~G3~~ — ✅ `kof.config` (JVM + **Native** + JS); CONF001 closed 16/09.
4. ~~G2~~ — ✅ `kof.http` client.
5. ~~G1~~ — ✅ complete level 0 `kof.db` + `kof.orm` (idiomatic JDBC, native
   SQLite, transactions, entity, migrations, **where with operators**,
   **saveAll batch**, **page/count/deleteAll**, **real MariaDB/PostgreSQL**,
   MongoDB); next: pools (ORM on Native x86-64 closed 22/09, F2d1–F2d7). Typed query DSL ✅ 01/09; `kof.db` on JS ✅ 16/09 (DB001 closed), `kof.orm` on JS ✅ 18/09 (ORM001 closed).
6. ~~G4~~ — ✅ `kof.validation` (13 predicates on the 3 targets; `KofValidationTest` 3/3).
7. ~~G5~~ — ✅ `kof.observability` (health/readiness/liveness, counter/increment/gauge, requestId/correlationId — JVM/Native/JS; `KofObservabilityTest` 10/10).
8. ~~G8~~ — ✅ `kof.time.sleep` + `interval`/`cancel` 3 targets (JS: cooperative queue — TIME001 closed 09/02) + **cron 17/09** (JVM/JS real, Native `CRON001`; §274).
9. ~~G10~~ — ✅ security on Native (PBKDF2, SHA-512, JWT HS256, AES-GCM in asm — native E2E `KofSecurityTest`) + config/log (asm).
10. ~~G9~~ — ✅ rate limiting, sessions, API keys (`security.rateLimit`, `sessionCreate`/`sessionGet`/`sessionDestroy`, `apiKeyGenerate`/`apiKeyValid` — JVM/Native/JS; `KofSecurityG9Test` 3/3).
11. ~~G12~~ — ✅ TLS/HTTPS (`web.listenSecure(port)` — JVM, `SSLServerSocket` + self-signed; `kof.http` HTTPS trust-all; `KofWebTlsTest` 7/7; Native/JS `WEB002`).

## P1

messaging (`kof.concurrent.Queue`, event bus, Kafka/AMQP adapters),
~~caching~~ — ✅ `kof.cache` (08/30, 3 targets),
~~resilience (retry/timeout/circuit breaker)~~ — ✅ `kof.http` (08/30, JVM+JS; **§259 CLOSED 17/09 — real on the 4 native targets**),
~~WebSocket/SSE~~ — ✅ JVM (08/30; `KofWebWsE2ETest`/`KofWebSseE2ETest`); hardening/limits/observability `app.configure`/`app.stats` (09/04),
GraphQL/gRPC (interop), HTTP/2.

## P2

batch, LDAP, complete OAuth2/OIDC, advanced sessions, OpenAPI,
mail/SMTP, CLI argument parsing in Kof, modular architecture
(`service UserService { }`, multi-file modules, boundaries).

## P3

AI (model clients, embeddings, RAG, tool calling — decide stdlib vs
external module), cloud integrations, provider adapters.

---

# 8. IMPLEMENTATION STRATEGY

1. **Converge, don't duplicate**: every new capability goes through the flow
   `SEARCH → EXISTS? → AUDIT/TEST → GAP → DESIGN → IMPLEMENT → TEST →
   DOCUMENT` (§17 of the statement). Never create `KofSecurity2`/`KofWeb2`.
2. **Reach P0 in layers** (each layer delivers value on its own):
   a. target diagnostic (G7) + structured test suite (G6);
   b. config (G3) + http client (G2) — enable tests and integrations;
   c. database (G1) — the biggest engine for real applications;
   d. validation (G4) + observability (G5);
   e. web security (G9, G12) — close the "production" cycle.
3. **Each module is only "DONE" with**: idiomatic API + type safety +
   applicable targets + unit/E2E tests + stress + benchmark + security
   review + docs + learn + training + real example (Definition of Done).
4. **JVM first** for modules with a heavy backend (database, TLS);
   Native and JS follow with the existing primitives (asm, kof_platform);
   gaps with diagnostics, never silent stubs.
5. **Continuous documentation**: update `docs/stdlib/stdlib.md` and this document
   with each delivered module; create `docs/database.md`, `docs/messaging.md`,
   `docs/stdlib/observability.md`, `docs/configuration.md`, `docs/testing.md`,
   `docs/platform.md` as each module takes shape.

---

# 9. RECORDED DECISIONS

- **No DI/container**: direct resolution and native constructs
  (`service`/`component` planned) — see `docs/philosophy.md`,
  `docs/stdlib/security.md` §2.
- **SQL-first Database**: `db.query` + prepared statements as the base;
  ORM optional, never mandatory — `docs/stdlib/DATABASE_VISION.md`.
- **Fixed JWT HS256** in v1 (no algorithm confusion); rotation and
  flexible signing remain for the P2 identity layer.
- **`new` accepted for backward compatibility**, not recommended.
- **AI**: stdlib vs external module decision postponed until P3.
- **Observability**: tooling REAL 20/09 — `kof bench` + `kof profile --methods` method-level sampling (JVM: own JFR `jdk.ExecutionSample`, report on the `.kf` line via LineNumberTable; JS: Node `--cpu-prof` + `.mjs.map` back to the Kof line; native face pending) + `kof.log`
  (levels, stderr — JVM); health/metrics/request IDs enter in P0-G5.
- **Configuration**: `kof.config` (JVM) follows the precedence
  explicit file > env > profile > default; typed via
  `config.str/int/long/bool`; CONF001 closed 16/09.
