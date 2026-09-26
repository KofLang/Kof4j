[English](roadmap.md) | [Português](roadmap.pt_BR.md)

# Kof — Long-Term Roadmap

**Last updated:** September 20, 2026 (§0 "read first" index added; active branch
corrected to `beta-0.5.0`/`D-BRANCH-0.5.0`). (older: September 15, 2026 — §23
gains 2.6 = D-NULL-INTENT queue N1→N4 [compiler lane, maintainer decision 15/09];
TIER 3–5 marked DEPRIORITIZED by the maintainer 15/09 — trio back to `future/`).
(older: plan merger: §23 = the SINGLE
implementation plan (ex-`ACTION_PLAN`+`IMPLEMENTATION_PLAN`);
migration cluster consolidated — `LEGACY_IR`+`DIFFERENTIAL_TESTING` merged into
`LEGACY_MIGRATION.md`)
**Version:** 0.5.0-beta (active branch `beta-0.5.0`)

---

## 0. Read first

This document is the **long-term narrative + the ordered plan**. What is LIVE
(act on this):

- **§23 — Consolidated Implementation Plan (Tiers 0–12)**: the single queue.
- **§24 — KOF 1.0 EXIT GATE (EG-1..EG-10)**: the release-gate queue (authority:
  `DECISIONS.md` §D-RELEASE-1.0 / §D-1.0-EDGES; detail in
  `PROPOSAL-1.0-EXIT-GATE.md`).
- **§22 — Universal Platform** (under development) and **§18 — Kof Written in
  Kof (self-hosting / NORTH STAR)**.

Sections **§1–§15 are the long-term narrative** (design direction, not a
queue); **§8/§9/§10/§11 have no owner** and are not current work. Three-states
rule: implemented/decided → `docs/`; pending → `docs/development/`; plan only →
`docs/development/future/`.

---

## Philosophy

Kof must radically simplify modern development without sacrificing power, performance, security, or interoperability.

Principles:

- abstract recurring complexity;
- keep code extremely short and readable;
- offer native language/runtime APIs;
- keep compatibility with the existing Java ecosystem;
- avoid reinventing Java libraries for aesthetics alone;
- allow Kof to offer a modern experience without forcing the user to depend on external frameworks;
- place complexity in the implementation/runtime/compiler, not in the application code;
- preserve architectural freedom;
- allow monoliths, modularization, and later microservices without rewriting the entire application.

---

## 1. Platform Targets

### KofAndroid — Android

Kof compiled for Android applications (APK/AAB). Full design in
[docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

Objectives:
- same code, same intention: `Window(...)` opens a real app;
- reuse of the JVM backend (bytecode → dex) — there is no alternative codegen;
- `kof.ui` via a synthesized WebView host (same KofJS layer as desktop);
- direct interop with `android.*` via ExternalClasspath
  (`extends Activity`, `super.onCreate`, androidx annotations);
- honest gaps at compile-time (`AND001..003`).

Current state: 🟡 Phases 1-4 implemented — `kof build --target android`
generates a Maven project (zero Java/Kotlin/Gradle) with an Activity host IN KOF
(`android-host.kf`) compiled by the frontend itself; d8/aapt2/apksigner
pipeline via pom without dependencies. Phase 2: the manifest carries
label/permissions; `--apk`/`--keystore` build the artifact directly. Phase 3:
responsive WebView (`<meta viewport>` device-width + narrow-screen CSS +
`setUseWideViewPort`/`setLoadWithOverviewMode` in the host). Phase 4:
`--min-sdk`/`--target-sdk` thread to `<uses-sdk>`, the platform jar and
`d8 --min-api` (defaults 24/34). `kof.web` is an enforced compile-time gap
(`AND002`). Pending (Phases 5+, no owner): `--aab` (needs `bundletool`, refused
honestly today), declarative icon metadata (decision-pending). Details in
[docs/targets/KOFANDROID.md](../targets/KOFANDROID.md).

### Kof4J — JVM

Kof compiled to JVM/bytecode.

Objectives:
- maximum compatibility with Java;
- access to Java libraries;
- compatibility with Maven/the existing ecosystem;
- execution as a JAR;
- possibility of using legacy frameworks such as Spring, Hibernate, etc.;
- primary backend during the initial consolidation.

Current state: ✅ stable (JVM V21, ASM, virtual threads, 819 tests 02/09;
native web stack with WebSocket/SSE, limits/counters and `kof.http`
retry/circuit — 30/08-04/09)

### KofNative — Native Binary

Kof compiled directly to native/binary code.

Objectives:
- ELF/PE/Mach-O depending on the platform;
- low consumption;
- extremely fast startup;
- possibility of servers without a JVM;
- native Kof runtime;
- reuse of the same language semantics;
- same application being compilable to JVM or Native.

Current state: ✅ stable x86_64 (free-list `kof_free_head` with mmap reuse; GC
mark-sweep implemented 03/09 + auto-collect on exhaustion ✅ 19/09 (D1-A, §260 CLOSED), memory returned on the
`munmap` fallback; `spawn`/`await` via `pthread_create` + trampoline +
`pthread_join` with thread-safe allocator (futex) — 31/08; real FP in XMM —
FLT001; JSON objects/records + FP arrays — JSN001/002/003; native SQLite `.so`
direct; MySQL wire x86-64 real (13 ORM faces, F2d1–F2d7 22/09)) + `native.risc` (riscv64: real codegen 02/09 — pure asm + qemu, NATIVE002
partial) + `native.arm` (aarch64: inherits from riscv via translator — `NativeArchEmitter.emitAarch64`, 39/39 E2E under qemu) *(synced 12/09: the line "codegen still placeholder" rotted — `NativeAarch64E2ETest` runs under qemu where there is a toolchain; honest guard skips on a host without cross)*

### KofJS — Web

Kof running on the server side/compiling to web applications.

KofJS must NOT be treated simply as "Kof that turns into JavaScript".

The vision is to generate modern frontend in a declarative and minimalist way:

```kof
page Home {
    column {
        text("Olá")
        button("Entrar") {
            login()
        }
    }
}
```

The intention is similar to Flutter's philosophy:
- declarative UI;
- components;
- composition;
- state;
- events;
- layouts;
- little verbosity;
- optimized HTML/CSS/JS generation.

Current state: 🟡 alpha — pipeline `.kf → Kof IR → KofJS → .mjs` functional with
execution on Kof's own embedded JS engine (no Node.js). Classes,
inheritance, List `map/filter/reduce`, String API, JSON, exceptions, pattern matching
`case String s` + `Point(x,y)`, basic `String?`, `kof.time`/`kof.io`/`kof.http` (via `Java HttpClient` interop + fetch fallback; retry/circuit at parity with the JVM — 30/08; scheduler via `setInterval` — 27/08; `spawn`/`await`/`channel<T>()` with real concurrency via async/await/Promise — CONC003 closed 03/09) and `kof run
--target=js` work. The web platform (HTML/CSS/JS, browser) is the next
phase. See: `docs/targets/KOFJS.md`.

### KofScript — Direct Execution

Runtime to execute Kof code directly.

Planned command:

```
kof run arquivo.kf
```

The internal implementation may evolve into interpretation, incremental compilation, JIT or hybrid execution, but the decision will be made later based on benchmarks.

Current state: ✅ implemented: `kof script app.kf [--watch]` + `kof repl` (top-level statements → `main()`, top-level `var`/`val` → `KofScriptGlobals`; Windows SIGPIPE fix). **0.3.0-beta: direct execution by interpretation** — `KofInterpreter` runs the SAME optimized frontend IR (without emitting bytecode, without a JVM fork; parity by construction with the JVM backend, proven in test). `KofCcompiler` (`kof c`) compiles a C subset → native x86_64 ELF (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`).

---

## 2. Multi-Target Principle

The language must have a single semantics:

```
Source
  ↓
Lexer
  ↓
Parser
  ↓
AST
  ↓
Type System
  ↓
Symbol Resolution
  ↓
Semantic Model
  ↓
Kof IR
  ├── Kof4J Backend
  ├── KofNative Backend
  ├── KofJS Backend
  └── KofScript Runtime
```

The Kof IR must remain independent of JVM, ASM, JavaScript or native code.

Backends are responsible for transforming the semantic representation into their platform.

Current state: ✅ architecture defined and partially implemented

---

## 3. Kof as a Backend Platform

The long-term vision is to allow building modern backends without Spring.

Do not reimplement Spring. Instead, transform recurring capabilities into Kof Runtime primitives.

Future objectives:
- HTTP / REST / WebSocket / SSE (WebSocket/SSE + JVM hardening completed 04/09; JS/Native follow-up);
- HTTP client;
- JSON;
- RPC;
- events / queues / pub/sub;
- concurrency / async;
- cache;
- configuration;
- observability / logging / metrics / tracing;
- health checks / graceful shutdown;
- validation / serialization / scheduling.

Conceptual example:

```kof
api "/users" {
    get "/{id}" {
        return User.find(id)
    }
    post "/" {
        return User.create(input())
    }
}
```

Current state: 🟡 partial — HTTP/routes (`kof.web` + TLS `listenSecure` +
**WebSocket `app.ws`** + **SSE `sse.*`** — 30/08, JVM; hardening
`app.configure`/`app.stats` — 04/09), JSON (complete in the 3
targets, 31/08), configuration (`kof.config` Native asm), logging (`kof.log`
Native asm), security (`kof.security` + G9), **cache (`kof.cache`, 3
targets — 30/08)**, **`kof.http` retry/circuit breaker (JVM+JS — 30/08)**,
concurrency (`spawn` + `await`/`Handle<T>` — JVM virtual threads, Native
pthread 31/08, JS sequential), `List map/filter/reduce`, `Box<T>`, pattern
matching and `String?` implemented (0.2.6-beta).
Missing (synced 12/09 against `backend-parity.md` — the list below was the
31/08 one; HTTP002/MySQL/cross-codegen have CLOSED since):
~~HTTP client in Native (HTTP002)~~ ✅ closed (Native HTTP/1.1 asm —
`backend-parity.md` §kof.http; https→throw declared), RPC (gRPC — see
below), tracing (OpenTelemetry), residual web in Native/JS
(~~WEB002/WEB001~~ **real base in both**: Native server `KofWebNativeE2ETest`
4/4 + GraalJS HttpServer `bc577aa`; residual TLS/ws/sse/path-params),
~~complete native MySQL~~ ✅ wire protocol + binary prepared statements 03/09
(`KofDbE2E` `nativeMysqlWireProtocol`/`nativeMysqlPreparedBinary`),
~~RISC/ARM codegen~~ ✅ complete core (real riscv64 02/09; aarch64 inherits via
translator; 39+39 E2E under qemu — advanced parity faces = NATIVE002),
GC mark-sweep (G-0 riscv ✅ `356f33b9`; G-1..G-5 decomposition in
`native-multiarch.md`).
(kof.mq pub/sub + queue = 3 targets — MQ001 closed 01/09)
See `docs/development/DECISIONS.md` §D-SPRING (Phases 5-14).

**gRPC in `kof.web` (new, 31/08 — planned)**: gRPC communication as
first-class in the web platform — `app.grpc { service ... }` with stubs
generated from `.proto`, server streaming + unary over HTTP/2 on the JVM
(`io.grpc` via `kof.web`), and client `grpc.call(endpoint, method, msg)`.
Scope: web Phase (same family as `app.ws`/`sse.*`); `.proto` → Kof IR
codegen; JVM parity first, Native/JS later.

### Concurrency — residual queue (updated 13/09 — was "0.2.6-beta, 31/08")

State 13/09: real concurrency **JVM** (virtual threads) + **Native**
(pthread, CONC001 closed 31/08: spawn/await + `done`/`poll`/`cancel`/
`cancelled`/`selectAny` — cooperative cancel by TID, selectAny by 1ms polling)
+ **JS** ✅ 03/09 (CONC003 closed — stmt/expr/cancel/selectAny with real
async/await/Promise) + **OTP supervision** (`kof.supervisor`: 1st slice
11/09 JVM+Script core, **S2-JVM 13/09** `startAll`/`lacoUnico` — see
`docs/planning-otp-supervision.md`; **Native x86 ✅ 15/09** — §129 closed via DECISIONS
§2 option B; **riscv64/aarch64 ✅ 19/09** — §129 cross port (per-TID chain table
`kof_exc_slots`, `OTP001` gate removed), so `kof.supervisor` runs on all Native
targets. **JS ✅ 18/09 — §132 resolved:** `time.sleep` became a cooperative
async await-point (compiler colors the reaching method async, `kofTimeSleep` returns a
Promise, `KofJsRunner` host pump drives it), so a spawned worker fires from inside
another task and `OTP002` was lifted — `kof.supervisor` now runs on JS to parity. The
earlier `spawn→await→spawn` SIGSEGV (misaligned stack at
the `pthread_create` call site) was fixed 01/09 by `andq $-16` in
`kof_spawn_handle_new`. A related latent defect surfaced and was fixed 15/09 in the
same §129 unit: `kof_await` did not clear the handle's TID after joining, so the
implicit `kof_spawn_join_all` at the end of `main` **double-joined** every awaited
handle — a SIGSEGV in `__pthread_clockjoin_ex` once the TCB was recycled
(reproduced at HEAD with 50 spawns + 50 awaits, 3/3 crash; clean after the fix).

| Item | Description | Priority |
|------|-----------|------------|
| ~~`ExecutionException` unwrap~~ | ✅ 31/08 — `kof_await` re-throws the original cause (JVM) | — |
| ~~`await` with timeout~~ | ✅ 31/08 — `awaitTimeout(r, ms)`: value on time, exception catchable via `try/catch` on timeout (JVM `Future.get(ms)` + Native 1ms polling with deadline; JS deadline poll `kofAwaitTimeout` — CONC003) | — |
| ~~Cancellation~~ | ✅ 31/08 — `cancel(r)`/`cancelled()` cooperative via flag on the handle (JVM + Native by TID) | — |
| ~~Multiple wait~~ | ✅ 31/08 — `selectAny(h1, h2, ...)` → first ready handle (JVM + Native + JS) | — |
| ~~`done`/`poll`~~ | ✅ 31/08 — non-blocking over the handle (JVM + Native) | — |
| ~~Native Port~~ | ✅ 31/08 — `pthread_create` + trampoline + `pthread_join` + thread-safe allocator (futex); implicit join (CONC001 closed) | — |
| ~~JS Port~~ | ✅ 03/09 — spawn over Promise, native await via microtask (CONC003 closed) | — |
| ~~Scheduler~~ | ✅ 31/08 — `every`/`cancel` JVM (`ScheduledExecutor`) + JS (`setInterval`) + **Native SCHED001** (thread per job, `usleep` ms→us + `active` flag, cooperative `cancel(id)`); `at(cron)` real 17/09 (5-field UTC parser, JVM + JS) | `at(cron)` on Native → **CRON001** (compile-time refusal — no asm parser) |
| ~~Typed channels~~ | ✅ 31/08, real blocking in JS 03/09 — `channel<Int>()` with `send`/`receive` (JVM blocking `LinkedBlockingQueue` + Native FIFO futex + JS queue of pending resolvers) | — |

Criterion for "100%": the three targets running the same concurrent programs
with an empty golden diff (same pattern as metric 1 of the plan).

### Language — residual queue (P1/P2, updated 13/09)

| Item | Status | Plan |
|------|--------|-------|
| pattern matching | ✅ 0.2.6-beta — `switch (x) { case String s: ... }` + `case Point(x,y)` in JVM/Native/JS | guards and nested destructuring pending |
| null safety | ✅ 0.2.6-beta — basic `String?` with compile-time `?`-check; **no Option in the core** | advanced checks pending |
| higher-order in collections | ✅ 0.2.6-beta — `List map/filter/reduce` in JVM/Native/JS | `Map/Set` already ✅ 0.1.0 |
| multi-file modules | ✅ 0.2.6-beta — `import a.b.C` file handling fix (`CompilerDriver.java:243`) for large projects (`a/b/C.kf`) | residual unified visibility/import semantics |

---

## 4. Native Security

Kof's own security layer, inspired by needs solved by Spring Security, but NOT as a copy.

Objectives:
- authentication / authorization;
- JWT / OAuth/OIDC;
- sessions / roles / permissions;
- security policies / CSRF / CORS;
- secure headers / rate limiting;
- input validation / password hashing;
- audit logging / API security.

The philosophy must be declarative and secure by default:

```kof
security {
    auth jwt
    route "/admin" requires role("admin")
    route "/users" requires auth
    rate "/login" 10/minute
}
```

Current state: ✅ implemented (v1, docs/stdlib/security.md)

**Implemented (0.2.6-beta, includes 0.0.5):**
- `kof.security` with idiomatic API: `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` + G9 (`rateLimit`, `sessionCreate`, `apiKeyGenerate`).
- PBKDF2-HMAC-SHA256 password hashing (600k iterations, salt, constant-time,
  versioned format).
- Crypto: SHA-256/512, HMAC, AES-GCM, secure random — JVM, Native (asm, `kof_db_mysql_scramble` for MySQL) and JS.
- JWT HS256 (fixed alg — no algorithm confusion), exp/iss/aud.
- Secrets via env + redaction for logs; constant-time comparison; Native free-list.
- Web auth middleware (`auth.authenticated()`, `auth.hasRole(...)`).
- Target gaps with clear diagnostics (SECN001/002/003/004, HTTP002).

**Pending:**
- OAuth2/OIDC client (architecture prepared in docs/stdlib/security.md §2.3);
- audit logging;
- database integration (planned).

*(sessions, rate limiting and API keys closed in G9 — 3 targets;
JWT/passwords/SHA-512/AES-GCM in Native closed in asm — G10.)*

---

## 5. Data / ORM / Hibernate

The vision is not to replace Hibernate by force. Kof must keep Java interoperability and allow `import org.hibernate.Session`.

But a native data layer must exist in the future:

- SQL / NoSQL / transactions / connection pools;
- migrations / repositories / query APIs;
- PostgreSQL / MySQL / SQLite / MongoDB.

Conceptual experience:

```kof
entity User {
    id: Long
    name: String
    email: String
}

User.find(id)
User.findAll()
User.save(user)

User.query {
    where age > 18
    orderBy name
}

transaction {
    user.save()
    account.update()
}

sql """
    SELECT * FROM users WHERE active = true
"""
```

Principle: "Abstraction when it helps, SQL when needed."

Current state: 🟡 partial — **levels 0-2 and 4 implemented** (`kof.db` +
`kof.orm`, see `docs/stdlib/DATABASE_VISION.md`): idiomatic connection
(JDBC on the JVM; native SQLite via `.so`; MySQL handshake `kof_db_mysql_scramble` 27/08), SQL with prepared
statements, transactions, declarative `entity` at compile-time, CRUD
(`create/save/find/all/where/delete/count`), `orm.where` by field + operators, `saveAll` batch, `page`/`count`/`deleteAll`,
versioned migrations (`kof_migrations`) and MongoDB (official driver).
Missing: typed query DSL (`User.query { where age > 18 }`), connection
pooling, complete MySQL (query/prepared), `kof.db`/`kof.orm` outside the JVM (**JS `DB001` CLOSED 16/09** — untyped on the GraalJS host; typed `query<T>` = `DB002` CLOSED 18/09; `kof.orm` = `ORM001` CLOSED 18/09 on JS, residual Native `ORM001`), NoSQL beyond MongoDB.

---

## 6. Dependency Management

The user should not need to edit `pom.xml` directly.

Future commands:

```
kof init
kof install lombok
kof remove lombok
kof update
```

The language's own file (`kofdeps`). For Kof4J, the system may generate a temporary `pom.xml` in memory during the build and use Maven for resolution/download.

Current state: 🟡 MVP 01/09 — `kof deps init/add/remove/list/resolve` (file
`kofdeps`, Maven Central resolution → `~/.kof/deps`, classpath via
`kof build|run --deps`); **POM transitive dependencies ✅ 16/09** (resolved by
delegating to Maven via a temporary `pom.xml` + `dependency:build-classpath`
(R9: the Maven graph resolver already exists — never reimplemented), the closure
is written to a portable `kofdeps.lock` GAV list that `resolve`/`build`/`run
--deps` consume; `KofDb`-style honest degradation when `mvn` is absent — explicit
warning, never a silent truncated classpath; proof `DepsTransitiveTest` 10/10
including a real-Maven E2E `jgrapht-core:1.4.0 → org.jheaps:jheaps:0.11`);
**registry ✅ 19/09 (D2-A, `DECISIONS.md` §D-POLL-19)**: GitHub Releases as the official host — publish (`kof deploy --publish`: Release + `<name>-<v>.tar.gz` + `SHA256SUMS`) + pull (`owner/repo[@ver]` in `kofdeps`, checksum verified BEFORE install, cache `~/.kof/deps/kof/`, REG001–004 honest).

---

## 7. Java Interoperability

Java compatibility is a strategic requirement. Kof must be able to use Java classes, methods, interfaces, libraries, annotations, Maven artifacts and legacy frameworks.

The existence of Kof native APIs must NOT break that capability.

Rule: "Legacy keeps working. Kof offers a better experience on top."

Current state: ✅ functional (records, classes, constructors, methods, fields)

---

## 8. Frontend

Declarative UI API conceptually inspired by Flutter.

Objectives:
- components / composition / layout;
- state / events / routing;
- forms / validation;
- responsive design / accessibility;
- animation / theming.

```kof
column {
    text("Hello")
    button("Click") { action() }
}
button.color = red
button.alignment = center
button.size = 10
```

KofJS must generate:

```
output/
├── index.html
├── assets/
├── app.js
└── app.css
```

Current state: ❌ not implemented

---

## 9. Frontend + Backend in the Same Project

A single Kof project can contain backend and frontend. The compiler must understand the contexts through the project's structure/declarations.

Shared models/types may in the future be used on both sides.

Current state: ❌ not implemented

---

## 10. Application Architecture

Kof must not impose MVC, Clean Architecture or Hexagonal Architecture. It must allow all of them.

```kof
// Simple
main() {
    get "/users" { return User.all() }
}

// Modular
app/
├── domain/
├── application/
├── infrastructure/
└── api/
```

Principle: "The language provides primitives; the architecture is the developer's choice."

Current state: ❌ not implemented

---

## 11. Monolith → Microservices

The goal is to allow evolution without rewriting:

```
monolith → modular monolith → services → microservices
```

`kof build` compilation can generate `app.jar` or a native `app`. Later the same project can be partitioned.

Current state: ❌ not implemented

---

## 12. Performance

Kof must allow implementing fast, efficient, scalable applications, with low consumption and fast startup.

Rules:
- compile-time > runtime magic;
- type information > reflection;
- generated code > runtime discovery;
- explicit semantics > hidden framework behavior.

Current state: ✅ JVM functional, Native functional

---

## 13. Observability

Native APIs for log, metric, trace, health, audit. Integration with OpenTelemetry.

Current state: 🟡 partial — `kof.log` with levels (JVM: structured JSON +
correlation ID; Native: asm, UTC — JS `console.*` 01/09) and `kof.observability`
(health/readiness/liveness, counter/increment/gauge, requestId/
correlationId — 3 targets). Missing: histogram + `/metrics` endpoint
(Prometheus), tracing/OpenTelemetry and `app.health("/health")`.

---

## 14. Standard Library / Runtime

Progressively:

```
kof-runtime / kof-http / kof-json / kof-data /
kof-security / kof-concurrency / kof-io / kof-ui
```

But do NOT create dozens of modules prematurely. First define contracts, types and architecture.

Current state: 🟡 in progress — already exist as stdlib namespaces (0.2.6-beta):
`kof.web` (JVM, `kof.http` JVM+JS), `kof.io`, `kof.time`, `kof.config` (JVM+Native free-list), `kof.log` (JVM+Native),
`kof.security` (3 targets, G9), `kof.db` + `kof.orm` (JVM; native SQLite + `kof_db_mysql_scramble`), `kof.validation`/`kof.observability`/`kof.mq` (3 targets),
`kof.process`, `kof.ui` + `KofScript`/`KofCcompiler`. The organization into separate modules will come after the
contracts stabilize.

---

## 15. Roadmap by Phases

### Phase 0 — Current Consolidation ✅

- parser;
- type system;
- symbol resolution;
- semantic model;
- Kof IR;
- JVM backend;
- Native backend (completed).

### Phase F — Runtime + Object Model ✅

- audit of the current runtime ✅
- Kof Runtime ABI defined ✅
- Object Model defined ✅
- ClassLayout / FieldLayout centralized ✅
- NativeRuntime (kof_alloc, kof_panic, etc.) ✅
- NativeBackend refactored (heap alloc, constructors, KofDup) ✅
- **Phase F.1 — String Model:** ✅
  - BuiltinTypes.STRING centralized ✅
  - KofString layout (type_id, flags, length, UTF-8 data) ✅
  - kof_string_from_literal ✅
  - kof_string_length ✅
  - kof_string_concat ✅
  - kof_string_equals ✅
  - kof_print_string / kof_println_string ✅
  - NativeBackend uses KofString for literals ✅
  - STRING_MODEL.md documented ✅
- **Phase F.2 — Array Model:** ✅
  - ArrayType in the Type System ✅
  - NewArrayExpr + ArrayAccessExpr in the AST ✅
  - Parser: new Type[size], expr[expr], expr.length ✅
  - SemanticAnalyzer: array type checking ✅
  - CompilerDriver: lowering to KofNewArray/KofArrayLoad/KofArrayStore/KofArrayLength ✅
  - NativeRuntime: kof_array_alloc, kof_array_length, kof_array_get, kof_array_set ✅
  - NativeBackend: complete lowering of array operations ✅
  - JVM Backend: NEWARRAY/IALOAD/IASTORE/ARRAYLENGTH ✅
  - ARRAY_MODEL.md documented ✅
  - 25 new tests (creation, access, length, long, string, loop, argument, return, empty) ✅
- **Phase F.3 — Inheritance:** ✅
  - SemanticAnalyzer: resolveInHierarchy() walks the superclass chain ✅
  - ClassLayout: buildWithSuper() includes inherited fields ✅
  - NativeBackend: allClassesMap to resolve superclasses ✅
  - CompilerDriver: super(args) with arguments, findSuperClass() ✅
  - Constructor chaining with explicit super(args) ✅
  - Access to inherited fields and methods ✅
  - 3-level inheritance ✅
  - INHERITANCE_MODEL.md documented ✅
  - 20 new tests (subclass, inherited fields, inherited methods, constructor chaining, 3 levels) ✅
- **Phase F.4 — Virtual Dispatch:** ✅
  - Object header extended: 8 → 16 bytes (type_id + flags + method_table_ptr) ✅
  - Method tables generated per class ✅
  - kof_init_object to initialize the header ✅
  - Virtual dispatch via vtable in NativeBackend ✅
  - JVM uses native INVOKEVIRTUAL ✅
  - Parser: support for `ClassName varName = value` ✅
  - CompilerDriver: NewExpr in inferExprType ✅
  - VIRTUAL_DISPATCH.md documented ✅
  - 11 new tests (override, polymorphism, 3 levels, slots) ✅
- **Phase F.5 — Interfaces:** ✅
  - KofCallKind.INTERFACE in the IR ✅
  - Parser: interface declaration + implements ✅
  - SemanticAnalyzer: isInterfaceType(), resolveInHierarchy() walks interfaces ✅
  - CompilerDriver: defines KofCallKind.INTERFACE for calls through an interface ✅
  - JvmBackend: INVOKEINTERFACE ✅
  - NativeBackend: dispatch via vtable for interfaces ✅
  - INTERFACES_MODEL.md documented ✅
  - 13 new tests ✅
- **Phase F.6 — Exceptions/Runtime Errors:** ✅
  - AST: ThrowStmt, TryStmt, CatchClause ✅
  - Parser: try/catch/finally ✅
  - IR: KofThrow ✅
  - JvmBackend: ATHROW ✅
  - NativeBackend: kof_panic for throw ✅
  - Runtime errors: kof_null_error, kof_bounds_error ✅
  - EXCEPTIONS_MODEL.md documented ✅
  - 14 new tests ✅
- **Phase F.7 — Memory Management:** ✅
  - kof_alloc with allocation tracking ✅
  - kof_free (no-op, documented) ✅
  - kof_memstats for debug ✅
  - MEMORY_MODEL.md documented ✅
> **Updated (0.2.6-beta):** interfaces (F.5), real exceptions (F.6, JVM +
> Native unwinding) and memory management (free-list `kof_free_head` + `kof_gc_collect` 27/08; `mmap` + reuse) are implemented.

### Phase 1 — Core

- runtime (consolidation);
- standard types;
- collections;
- IO;
- errors/exceptions;
- concurrency;
- serialization.

### Phase 2 — Developer Experience

- `kof init` / `kofdeps` / `kof install` / `kof remove`;
- `kof update` / `kof check` / `kof fmt` / `kof test` / `kof clean`;
- REPL / LSP.

### Phase 3 — Web Platform (`kof serve`)

- network syscalls in NativeRuntime (socket, bind, listen, accept, read, write, close) ✅;
- `kof serve` command in the CLI ✅;
- KofHttpServer (thread pool, Content-Length, query, headers, 404/500) ✅;
- `kof serve` with top-level handlers (`handle(...)`) ✅;
- JSON serialization (`json.encode`/`json.decode`) ✅;
- 8 in-process E2E tests (real sockets) ✅;
- Documentation (`docs/stdlib/http.md`) ✅;
- Path parameters (`:id`), query, headers, middleware `app.use` ✅
  (stack `web.app()` — Phase 1 of the Spring independence plan);
- WebSocket/SSE + hardening (`app.configure`/`app.stats`, connection cap,
  deadlines) ✅ JVM (30/08-04/09); JS/Native follow-up.

### Phase 4 — Security

- auth / authorization / JWT / OAuth/OIDC;
- sessions / policies / rate limiting;
- security defaults / audit.

> Ecosystem audit: the coverage matrix, gaps (G1-G12),
> priorities and strategy live in `docs/bugs-and-gaps/ecosystem-coverage.md`.
> P0 implementation order: target diagnostics (G7) → structured `kof.test`
> (G6) → `kof.config` (G3) → `kof.http` client (G2) →
> `kof.database` (G1) → validation (G4) → observability (G5) →
> scheduling (G8) → Native security (G10) → web security (G9, G12).

### Phase 5 — KofJS

- frontend / declarative UI / components;
- state / routing / forms / SSR;
- HTML/CSS/JS generation.

### Phase 6 — KofScript

- direct execution / fast startup;
- REPL / incremental execution / scripting APIs.

### Phase 7 — Complete Native

- full language support / native runtime;
- networking / database / security;
- production server support.

### Phase 8 — Platform Maturity

- distributed systems / service discovery;
- messaging / RPC;
- observability / deployment / cloud integrations.

### Phase 9 — Internal Refactor: 500-lines-per-class rule

> **Recorded 02/09/2026.** Architecture rule: no class may exceed
> **500 lines**. Current violations force a general refactor:

- `NativeRuntime.java` (~17,300 — embedded assembly) → modules by domain
  (`native/asm/*.s` or `NativeRuntime*` classes per area);
- `CompilerDriver.java` (~8,200) → extract helpers per area;
- `JsBackend.java` (~5,400) → separate emitter from embedded runtime;
- `Parser.java` / `SemanticAnalyzer.java` / `JvmBackend.java` → sub-parsers.

Acceptance criterion: `cloc`/`wc -l` per class — none above 500.
Details and size table: `docs/audits/complexity-audit.md` → "Architecture
rule — 500-lines-per-class limit".

---

## 16. Do Not

- do not copy Spring;
- do not copy Hibernate;
- do not create a giant monolithic framework;
- do not add annotations for everything;
- do not depend on reflection when compile-time is enough;
- do not couple the core to the JVM;
- do not create backend-specific APIs inside the language;
- do not sacrifice Java interoperability;
- do not implement giant features before consolidating the core;
- do not turn every problem into a new module;
- do not add complexity just because other languages do it that way.

---

## 17. Distribution and Tooling (0.2.6-beta)

Kof is a distributable platform, not just a JAR:

- self-contained distribution (compiler, CLI, runtime, stdlib, tooling, editor support, embedded JDK 25);
- OpenJDK embedded in the official package (Temurin 25, tooling API level 21);
- centralized versioning (`VERSION` 0.5.0-beta → pom/properties via `scripts/bump-version.sh`);
- releases by 2 jobs (`release.yml`: `test-and-bump` exports `bump_sha` → `package-and-release` checks the bump commit + version sanity check) on push to `main`, per platform linux-x86_64 / macos-arm64 / windows-x86_64 (tests 819 → bump → package 3 platforms → GitHub Release);
- `scripts/package.sh` PASS (dist layout + tar.gz/zip + SHA256SUMS + jars), golden 16/16, integration 9/9;
- official editor support: TextMate grammar + LSP (hover/completion + real diagnostics) + `kof editor install` (VS Code/Neovim/Vim/Emacs/Geany/Nano + honest step-10 IntelliJ 13/09: filetype XML + External Tools + LSP4IJ README, no plugin — issue #1);
- `kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/deps/editor/init/new/version` PASS (26 commands; `fmt` and `config gen` 31/08).

References: `docs/distribution/`, `docs/tooling/`.

---

## 18. Kof Written in Kof (self-hosting)

Planned from now as a real architectural evolution, not a demonstration.

Prerequisites before migration:

- generics; collections; exceptions;
- stdlib; filesystem; strings; concurrency; HTTP;
- tooling; sufficient expressiveness of the language.

The current compiler remains architecturally prepared for the migration
(single frontend feeding compiler, LSP, formatter and diagnostics), but the
migration must **not** be attempted prematurely.

---

## 19. Kof + LLM

Kof is *Human First, LLM Friendly by Consequence*:

- less ceremony; fewer files; fewer artificial abstractions;
- less configuration; more intention.

The consistency of the design makes humans and LLMs understand the same
language the same way. The `training/` directory is an official part of that
strategy.

---

## 19.5 Kof Debugger (official tooling component)

First-class debugging: the programmer debugs **Kof code**,
regardless of target. Phases 1-3 implemented: DebugInfo in the IR with
source location per op, JVM LineNumberTable/SourceFile/LocalVariableTable
generated and **functional `kof debug` MVP** (DAP over stdio + raw JDWP: launch,
breakpoints by Kof line, `stopped`, stack trace with Kof functions/lines,
continue, disconnect). Phase 4 (Kof Editor) planned; Phase 5 (Native DWARF) x86-64 LANDED — `.debug_line`+`.debug_info`+`.debug_abbrev` on by default, `--release` strips, locked by `NativeDwarfLineInfoTest`/`NativeDwarfSubprogramTest`; cross line table LANDED 19/09 (`.file`/`.loc` in riscv64, passed verbatim to the aarch64 translation — `NativeDwarfCrossTest`); fatia 2 landed 19/09 ~23:5x — CU/subprogram DIEs in the cross too (`NativeDwarf.Arch` frame_base: regx-x27 riscv / reg29 aarch; slots real from `NativeDwarfCrossRegister`; ELF locked by `NativeDwarfCrossTest`+objdump in CI); residual narrowed 20/09: gdb-Native face ✅ (`kof debug --target native` — ELF+DWARF, gdb com `directory` da fonte, `KOF_GDB` override; `--break <line>` adds a scriptable BATCH session (stop on the Kof LINE + backtrace, CI-friendly) and `--output <dir>` keeps the ELF; `KofDebugNativeTest` 7/7 — real-gdb batch + stub-gdb construction; js recusado honesto — o engine embutido nao tem inspector); DAP<->gdb ✅ 20/09 (`kof debug --dap --target native`, `KofDebugNativeDapTest` 3/3 stub-MI — o editor fala DAP com o Native e ve o .kf); attach REAL 20/09 (X7-5, `81401629`): `--dap --attach <pid>` no JVM (cliente JDWP cru reconstruido sobre oraculos medidos byte-a-byte na JDK 25.0.4.1 — §376: IDSizes lê 5 nao 6, `VM.Classes` substitui `ClassesBySignature` morto, `FrameCount` clamp, forma real de `VariableTable`, guarda NATIVE_METHOD, envelope COMPOSITE; disconnect NAO mata o debuggee — `KofDebugAttachTest`) e no Native (gdb `-p` via `KofGdbMi.attach`; launch/configurationDone no-op quando attached); o que resta = face JS (engine embutido sem inspector = gap honesto); Phase 6 (JS source maps) V3 landed 01/09 (`KofJsSourceMapTest`); Phase 7 (advanced) planned. See: `docs/debugging/debugger-architecture.md`,
`docs/debugging/debugging.md`, `docs/debugging/debug-adapter.md`.

## 20. Design Principles

1. Simplicity first.
2. Readability first.
3. Compile-time whenever possible.
4. Small and predictable runtime.
5. Secure by default.
6. Measurable performance.
7. Uncompromising interoperability.
8. Native abstractions for recurring problems.
9. Escape hatches always available.
10. One language, multiple targets.
11. Monolith and microservices must be architectural choices, not limitations of the language.
12. Code must express intention, not infrastructure.
13. Kof must hide complexity without hiding power.
14. Legacy compatibility is a feature.
15. No future decision may break the language's agnostic core.

---

## 21. Legacy Migration Platform (future plan)

Long-term initiative to analyze, recover, translate and modernize
legacy systems into Kof — **outside the 0.0.x scope**.

- Central document: `future/LEGACY_MIGRATION.md` (§4 = Legacy Semantic
  IR/Confidence; §8 = differential test + migration report) — **DEPRIORITIZED
  by the maintainer 15/09: the trio + work-logs went back to `future/`; code in
  kof-cli stays, promotion needs her explicit decision**
- Planned components: `kof inspect`, `kof decompile`, `kof translate`,
  `kof migrate`, `kof compare`
- Architecture: `Legacy Input → Legacy Semantic IR → Kof AST → Kof IR → Backend`
- Java is a supported source, never a mandatory intermediate representation
- Related documents: `future/DECOMPILER.md`, `future/TRANSLATOR.md` (the old
  `LEGACY_IR.md` and `DIFFERENTIAL_TESTING.md` were merged into the central one 13/09;
  `IMPLEMENTATION_PLAN.md`/`ACTION_PLAN.md` became §23 of this roadmap)

**Do not implement anything from this section before consolidating the language,
compiler, runtime, stdlib and tooling.**

---

## 22. Universal Platform (under development — R12 overridden)

Long-term vision — Kof as a universal platform (one language for
applications **and** systems, infrastructure, automation, data, security and
science) **without** destroying the language's simplicity.

- Central document: `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  (architecture — **UNDER DEVELOPMENT** since 17/09/2026; promoted from
  `future/` by maintainer decision, `DECISIONS.md` §D-UNIVERSAL)
- Stages by capability/maturity: `FOUNDATION ✅` → `SYSTEMS` (in
  progress) → `AUTOMATION` → `INFRASTRUCTURE` → `DATA` → `SECURITY` →
  `SCIENTIFIC` → `BIO` → `UNIVERSAL`
- Expansion mechanism: **stdlib as compile-time dispatch tables** +
  FFI/interop + official packages — never a new target, never a new language
- Invariants (R1–R12): core/platform boundary, interop-first, honest scope
  per target (JVM-first/Native/JS-web), never silent per domain
  (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`), stability tiers
  (`stable`/`experimental`), small and stable core, security defense first,
  correct/deterministic in science
- Permanent non-goals: no open macros/type-classes/annotations/ownership/
  effect system; no homemade crypto; no reimplementing Arrow/BLAS/ML/
  aligners; no "Kali in Kof"; no per-domain target; no own SQL engine

> **Status 17/09:** R1 ✅ DONE (`5f1422c6` — gate `scripts/check_stdlib_boundary.sh` + ledger na CI, AGENTS invariante 1). R2–R12: fila aberta por D-UNIVERSAL; unidades de código seguem a ordem de valor do §23.
>
> **R12 gate overridden 17/09/2026** (`DECISIONS.md` §D-UNIVERSAL): the
> maintainer authorized this front to open **with SYSTEMS still in progress**.
> The entry point is Stage 1 (SYSTEMS consolidation) + R1–R12; Tier 6+
> (AUTOMATION/INFRA/DATA/…) keeps its order in §23. For every **other** front,
> R12 remains the default: do not open `infra`/`data`/`sci` before SYSTEMS
> closes (gap parity, GC mark-sweep, basic package manager — §23 P0–P5).

---

## 23. Consolidated Implementation Plan (Tiers 0–12)

> **This is the ONLY ordered implementation plan in the repo.** It merges
> `ACTION_PLAN.md` and `IMPLEMENTATION_PLAN.md` (deleted 13/09 — ~85% of the
> content was the SAME phases/tiers table in both, and the two
> diverged from the code). Every phase here moves the corresponding doc from
> `future/`→`docs/` when it gains code. Difficulty: `E` easy · `M`
> medium · `H` high · `R` research.
>
> **Cross-cutting rule (R12):** no future plan item is an action on the
> current state; new fronts (AUTOMATION/DATA/SCI/BIO) do not open before the
> SYSTEMS stage (§21/§22) closes. Non-goals (§16/§22): no open macros,
> type-classes, ownership, effect system, homemade crypto, reimplementing
> Arrow/BLAS/ML; no "Kali in Kof"; no own SQL engine.

### TIER 0 — Guardrails and processes (E, ≈ zero) ✅ 01/09

R1/R5/R6/R7/R9–R12 as invariants (AGENTS.md + §22); gap convention per
domain (`INFRA00x`/`DATA00x`/`SCI00x`/`BIO00x`/`SECPQ`) + parity matrix;
`stable`/`experimental` tiers (`docs/backend-parity.md`).

### TIER 1 — Closing the SYSTEMS stage (M–H, prerequisite for Tiers 6+)

| # | Item | Measured state (13/09) |
|---|------|----------------------|
| 1.1 | Parity gaps (`HTTP002`, WEB residual `WEB002`/`WEB003`/`WEB004`, ~~`CONC003`~~ ✅ 03/09, ~~`LOG001`~~ ✅ 01/09, ~~`MQ001`~~ ✅ 01/09, ~~`SCHED001`~~ ✅ 31/08, ~~`TIME001`~~ ✅ 02–05/09, ~~`SECN002`~~ ✅ 01/09, ~~`OBS002`~~ ✅ 01/09, `MEDIA`) | 🟡 in progress — JS web server base ✅ 16/09 (WEB001 closed; DB001 closed); residual per `backend-parity.md` (HTTP002 https/TLS native, ws/sse gap codes, MEDIA) |
| 1.2 | Automatic GC mark-sweep in Native | 🟡 riscv `356f33b9` ✅; x86 decomposed G-1..G-5 (`native-multiarch.md`) |
| 1.3 | Typed query DSL (`User.query {}`) | ✅ 01/09 (`KofOrmE2ETest`) |
| 1.4 | Package manager MVP (`kofdeps`) | ✅ `kof deps` + Maven Central resolution; **transitive ✅ 16/09** (Maven delegation + `kofdeps.lock`, `DepsTransitiveTest` 10/10 incl. real-Maven E2E); **registry ✅ 19/09** (D2-A publish + 1.5.3-S2 pull, `DepsRegistryTest` 6/6) |
| 1.5 | Tracing/OpenTelemetry + `application{}` lifecycle | 🟡 W3C spans + lifecycle ✅ 3 targets; **OTel export ✅ JVM/JS (`exportSpans()` → OTLP/JSON, `OBS003`); Native gap honesto `OBS003`** |
| 1.6 | **Native → bare-metal/bootable with ring0/ring1** (microcontroller, legacy BIOS, UEFI, x86_64 privilege rings) — 15/09 maintainer directive | 🟢 **DONE — CLOSED 25/09 (promoted from `future/` 22/09)** (`D-BAREMETAL-BOOT`, maintainer order; R12 overridden for this front): HAL seam `kof_plat_*` + freestanding profile, faces B-0…B-6 in `docs/PLAN-BAREMETAL-BOOT.md`. **LANDED: B-0..B-3 + B-6** (legacy BIOS runs the real Kof `main` end-to-end under SeaBIOS; ring0/ring1 with `#GP` + GDT-sabotage proof). **Remaining authorized (`D-BAREMETAL-BODIES`, 24/09): B-5 platform bodies** — BIOS/UEFI `kof_plat_time` (RTC/TSC), `sleep`, `mono`, `random` **LANDED 24/09**. **B-4 (MCU):** the 32-bit codegen **LANDED** (RV32I + Cortex-M3/Thumb-2 emitters; hello over UART + vector-table reset path asserted — `NativeMcuE2ETest` 7/7, `NativeMcuArmE2ETest` 6/0), **B-4 CLOSED 25/09 on riscv32** (`D-BAREMETAL-MCU-GC` "and/or"): the collector `native-multiarch.md` **G-4/G-5 was ported to the 32-bit MCU** (B4-GC-1..4: alloc + conservative mark + sweep + a long-running recycle proof under qemu; `NativeMcuGcRiscv32`/`NativeMcuGcRiscv32Sweep`, `NativeMcuGcTest` 8/0) and the MCU `kof_plat_time` bodies landed (B4-TIME: named wall refusal + `time`-counter monotonic; `NativeMcuTimeRiscv32`, `NativeMcuTimeTest` 2/0). The plan `docs/PLAN-BAREMETAL-BOOT.md` moved to `docs/` (3-state rule). **Tracked follow-ups:** (a) **DONE 25/09** — the collector/time were **mirrored to Cortex-M3/Thumb-2** (`NativeMcuArmGc`/`NativeMcuArmGcSweep`/`NativeMcuArmTime`; `NativeMcuArmGcTest` 8/0 + `NativeMcuArmTimeTest` 2/0 under `qemu-system-arm -M mps2-an385`; the mono counter accumulates SysTick deltas so it never goes backwards on the 2^24 wrap; `NativeMcuArmTime` is wired into `NativeMcuArm.renderAsm`); (b) **LANDED 25/09 (fatia F — maintainer "minimal spike")**: `NativeMcuRiscv32.lowerMain` now runs real eval-stack codegen — `listOf(1,2,3)` via `kof_list_new`/`kof_list_add` (stable-header growable list), `.size` via `kof_list_size`, `println` of runtime Int via `kof_string_of_int` (real RV32I decimal — binary long-division u32/10, no M-extension), locals (`KofLoad/KofStoreLocal` + `.Lmcu_locals`) and `KofDup`; the tip's E2E reds were rooted in the MCU emitter itself (literal `println` counted `+1` PAST its rodata bytes → garbage; `renderAsm` never emitted `.rodata` nor the `.Lkof_heap_root_*` anchors → link failed yet was swallowed as `success=true` with no artifact — now a loud `NATIVE002` refusal); `mapOf`/loops stay honestly refused. Proof: `NativeMcuE2ETest` **8/0** (new `mcuSpikeListOfPrintlnSizeAndIntOverUart` — UART byte-exact `3\n42` under qemu virt rv32) + `NativeMcuListTest` 4/0 + `NativeMcu*` suite 38/0; split for ≤500: `NativeMcuListStringRiscv32` (222 lines). Remaining follow-up: mirror the slice to the Cortex-M3 emitter. |

### TIER 2 — Compiler foundations (M) — **status corrected against the code**

> The old version marked 2.1.5 and 2.2.2 as "✅"; **they are not** (see below —
> audited at HEAD 13/09, not from memory).

| # | Item | REAL measured state |
|---|------|--------------------|
| 2.1.1–2.1.3 | `extern` syntax + type-check + gaps `FFI001`/`FFI002` (never silent drop) | ✅ `Parser.java:192` (PARSE090), `ExternalFunctionNode`, `FfiE2ETest` |
| 2.1.4 | **JVM** binding (FFM `java.lang.foreign`) | ✅ **generalized 18/09 (`.18`, R3):** any scalar signature, arbitrary arity, `void`/`String` returns — measured `fmod`→1.5, `ldexp`→12.0, `strncmp`→-1, `puts(void)`, `getenv`→String (`syntax.md`) |
| 2.1.5 | **Native** binding | ✅ **20/09 (#431 slices 1–2, §369)** — the raw binary binds scalars **direct** (`call sym@PLT`, link-by-use), which **supersedes `dlopen`/`dlsym`**; §61 closed. The old `dlopen` segfault was the reason for the switch, not an open gap |
| 2.1.6 | struct/array marshalling | 🟡 **JVM ✅ 3.8b (20–21/09)**: `record` by value as arg/return + scalar `T[]`→`ptr` (`FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5); **D6-3 out-buffer ✅ landed 21/09** (`Buffer`/`Buffer(U8,INOUT)`, `BufferE2ETest` 4/4 + `BufferFfiE2ETest` 4/4); **remaining** = JS struct bridge, Native sret (3.7). D6 ✅ decided 20/09 |
| 2.1.7 | JS: gap `FFI002` | ✅ honest gap + **scalar parity CLOSED 18/09 (`d3598c2d`, slices 3.6.F1–F3):** host runner binds via `KofJsFfiBridge`, `FfiE2ETest` 16/16 byte-for-byte JVM↔JS; browser = runtime R7; non-scalar keeps `FFI002` |
| 2.2.1 | Inventory of implicit codegen (4 points: runtime `.source()`, `desugarTests`, `desugarApplication`, entity→record+schema) | ✅ the 4 exist (`CompilerPipeline:295-296`) |
| 2.2.2 | **Formal `CodegenStep` hook** | ✅ **LANDED 21/09 (R4, `D-CODEGEN-STEP`)** — `CodegenStep`/`CodegenStepPipeline` (additive; empty registry = identity, zero behavior change; `CodegenStepPipelineTest` 6/6). The old `d1c56bad` "✅" was an over-claim from the `planning-future` branch; R4 is the real landing |
| 2.2.3 | Migrate DDL/runner to the formal hook | ✅ **IMPLEMENTED 21/09 (`85779f20`, `D-DESUGAR-STEP`)** — measured 21/09 (`docs/architecture/codegen-step-2.2.3-assessment.md` +PT, moved per the 3-state rule): **phase mismatch** (DDL = lowering at `ExpressionOrmCallLowerer:70`; runner = AST desugar; the hook runs on the OPTIMIZED IR, `CompilerPipeline:332`). Option B = a **`DesugarStep`/`DesugarStepPipeline` registry at the AST phase** (mirroring `CodegenStep`); the four desugars are registered in `DesugarSteps.defaults()` and run from `CompilerPipeline:303` — behavior-free (rule 3; `DesugarStepPipelineTest` 7/7). The ORM DDL stays in lowering (not a registry candidate). |
| 2.2.4 | `infra "prod" {}` base (codegen over records) | ✅ **LANDED 21/09 (`D-MAKEALIVE-SYNTAX`, `966c86a4`)**: pure sugar over `design()` (no HCL; `infra` = IDENTIFIER, lowered to `design(): Infrastructure`) — proof `InfraSyntaxE2ETest`; R4 ✅ was the hook |
| 2.3.1 | Constant-folding of domain constants | ✅ `"a"+"b"→"ab"` (`OptimizerConstantFold:100`) |
| 2.3.2 | Cycle detection in the `infra` graph at compile-time | ✅ **CLOSED 21/09 as runtime-only** (`D-MAKEALIVE-SYNTAX` addendum, `5759b9bd`): 2.2.4 is pure sugar, so the compiler sees only generic calls — a static graph would give the block its own semantics (§7/rule 11); the 3.1 runtime refusal names the cycle members |
| 2.4.1 | Scoped resources (lightweight RAII over `try/finally`) | 🟡 design only (`future/scoped-resources-plan.md`); `using` syntax gated by bump |

| 2.5 | Variance / sealed | ⏫ **SUPERSEDED 21/09 by §2.8.4** (`D-TYPE-VARIANCE`): `sealed` + variance opened as the **X5** slices (X5.1–X5.4 ✅ DONE 21/09); the old "postpone" is void |

#### 2.6 — Nullability by EXPLICIT INTENT (queue N1→N4 of DECISIONS §D-NULL-INTENT, 15/09)

**Decided by the maintainer in person 15/09** (record: `DECISIONS.md`
§D-NULL-INTENT, EN+PT — the §125 "option A" silent `null→0` fold is REVOKED).
Lane: **compiler** (contract on the 4 backends — not the docs lane).

| # | Step | Scope (one line) | Depends on |
|---|------|------------------|------------|
| 2.6.1 | **N1** — JVM+Script+JS: `Nullable(primitive)` carries REAL null | boxed `T?` return/field/slot on the 3 targets that have boxed types; flip `nullableprint` cell + the 3 `KofInterpreterParityTest` null-branch parities in the SAME commit as the behavior (rule 1) | — |
| 2.6.2 | **N2** — Native: real null via the tagged-box ABI §104b-ii — **✅ DONE 23/09 (authorized Native face = `Object`-reference print)** | **`RuntimeErasureBox`** (`[MAGIC][tag][value]`, 24 B) + `kof_box_*` / `kof_unbox_*` strict+soft dispatch; x86 hand-written + riscv hand-written + aarch64 via translator. The older `typeId=3` box sketch is **superseded — do not create it as a second ABI**; see `docs/runtime/RUNTIME_ABI.md` §3.9. **LANDED 23/09:** the authorized Native face (per the `D-NULL-INTENT` Authorization 23/09) is the polymorphic `Object`-reference print — `kof_box_to_string` now decodes the 4-byte `type_id` (offset 0, the `kof_instanceof` discriminator), `type_id==1` (`String`) passes raw and any other reference tail-calls `kof_tostring_table[type_id]` (dense `.quad` emitted with the classes, reused from the vtables; `0` = old passthrough). No second ABI. Proof: `NativeObjectBoxPrintE2ETest` 3/3 (in-test JVM oracle, x86+riscv64+aarch64; closes §205 slice 2). | §104b-ii / §205 slice 2 share this ABI |
| 2.6.3 | **N3** — `== null` on a NON-nullable: legal, constant-foldable, NEVER a diagnostic | intent reads the comparison itself; rule 2 (backward compat): existing code that compares keeps compiling | N1 |
| 2.6.4 | **N4** — audit the remaining silent-null faces | map-miss `0` (SG-008), uninitialized field `0`, unbox-of-null `0` — each gets a decision or an honest diagnostic (R6) | N1–N3 |
| 2.6.5 | **D-TROOL-1 (front-end) ✅ LANDED 19/09** (`916b9fb7` core + `d61836eb` migration/law; `TrooleanLawE2ETest` 13/13, gate dirigido 8/8, goldens medidos JVM=Script=JS=Native-x86) — register `Troolean` (3 states); `Nullable(Bool)` written by the user → `SEM095` ("`Bool` has exactly two values — for true/false/unknown use `Troolean`"); uninstantiated `Troolean` = unknown; Kleene `!`/`&&`/`||` + `== true/false`/`== null` + `println` + condition sugar `if (t)`≡`if (t == true)` — JVM+Script+JS via `runAll3`; migrate the 4 `Bool?` test files (same assertions) | DECISIONS §D-TROOL; proof `TrooleanLawE2ETest` + migrated §306 faces; closes #462/#486 | — |
| 2.6.6 | **D-TROOL-2 (Native) ✅ 19/09 (medido)** | three-state face on the native backend — measure `Nullable(Bool)` behavior there first (PR #465 front is the boxed-`T?` lane); ship work or the honest `NAT-TROOL001` diagnostic, never a silent fallback — MEASURED: Kleene tables IDENTICAL Native-x86-64 (boxed slot §295/§306 reused, zero backend edits; no NAT-TROOL001 needed; cross under qemu guard, CI green) | D-TROOL-1, family 2.6.2 |
| 2.6.7 | **D-TROOL-3 (corpus+migration) ✅ 19/09** (`5f0757e8`) | `training/idioms` + `fake-idioms.md` (row `Bool?` → Troolean), `docs/language-reference/types.md`, CHANGELOG migration note (freeze rule 1 — the #401 precedent class), `backend-parity.md` cell | D-TROOL-1 |

**Queue status (18/09 ~13:40):** PR **#438** (external fork, `fix/278-d-null-intent-atomic`) **MERGED by the maintainer at `250f6207`** (18/09 12:53) — N1 (JVM+Script+JS) landed: `Map.get` now returns `V?` for reference values and the absent-key primitive repro runs clean (measured `9`, fresh jars). The PRESENT-key `!= null` face on primitive values is STILL broken post-merge (Int/Long/Double/Boolean → `NoSuchMethodError Object.valueOf(boxed)`; Float → CCE `Double→Float` at the map site) — catalogued as §294 re-procedure 2a, owner `.22` erasure/boxing cluster, NOT a reopen of #438. N2 (Native) and I4/i1 remain out of scope/queue; lanes must not open a parallel front on the same contract (rule 6 / one contract, one PR). It also declares `Map.get/put/remove` on absent keys (I7) — subsuming the parked `#376`/`#409` map-absent face stamped by the maintainer 18/09 — do NOT open a separate N4 front for those cells while #438 is under review.

#### 2.7 — Value records / first-class value types (queue of `D-VALUE-RECORD`, 16/09) — **FRONT OPENED 19/09 (D7-A)**

**Decided by the maintainer 16/09** (record: `DECISIONS.md` §D-VALUE-RECORD;
origin issue #275). Additive, backward compatible. **Planned only — not
current work** (R12: new fronts do not open before the SYSTEMS stage closes;
lanes must not attack without new authorization).

**Design plan:** [`future/value-records-plan.md`](future/value-records-plan.md) (zero code).

| # | Step | Scope (one line) | Depends on |
|---|------|------------------|------------|
| 2.7.1 | **front-end `value` keyword** | `value record Name(fields)` parses and types like `record` + `value` modifier; no identity semantics yet | — |
| 2.7.2 | **JVM ABI** | value/inline class mapping (`invokevirtual` value semantics, no `Object` identity) | 2.7.1 |
| 2.7.3 | **Native ABI** | pass-by-value (struct by value / registers) | 2.7.1 |
| 2.7.4 | **JS ABI** | plain frozen object (no identity) | 2.7.1 |
| 2.7.5 | **parity + docs** | conformance cells `valuerecord` + parity matrix + `training/` + `learn/` | 2.7.1–2.7.4 |

#### 2.8 — Queue opened 21/09 (`DECISIONS.md` §D-CODEGEN-STEP/§D-R3-3.3/§D-R3-3.5/§D-TYPE-VARIANCE/§D-INTEROP-REFLECT)

**Decided by the maintainer 21/09** (multiple-choice). D8–D12 of
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`. Additive; nothing lands without proof.
The two core type-system fronts are **spec-first** (plan reviewed before code,
rule 6).

| # | Step | Scope (one line) | Depends on |
|---|------|------------------|------------|
| 2.8.1 | **R4 `CodegenStep`** (`D-CODEGEN-STEP` = A) — ✅ **landed 21/09** | compiler-INTERNAL codegen hook, no user syntax; unblocks `infra "prod" {}` (3.2) + DDL/runner migration | — |
| 2.8.2 | **R3-3.3 handles/out-buffers** (`D-R3-3.3` = A) | nominal opaque `Handle` (non-arithmetic) + `Buffer(U8, INOUT)` (== D6-3); prerequisite of Stages 4–7 | R3 (2.1) |
| 2.8.3 | **R3-3.5 variadics** (`D-R3-3.5` = A) | NO general variadics — caller passes `List`/`Array`/`Buffer`; documented gap (R6/R7) | R3 (2.1) |
| 2.8.4 | **X5 variance + sealed** (`D-TYPE-VARIANCE` = C) | ✅ **DONE 21–22/09** — `sealed`+`SEM080`; exhaustive `switch`+`SEM081`; declaration-site `out`/`in`+`SEM082`; use-site projection `List<out T>`/`List<in T>`; X5.5 parity cells (`sealedswitch`/`variance`/`useproj`) + docs/`training`; **plan CONCLUDED + moved to `docs/` 22/09** | ✅ |
| 2.8.5 | **X6 interop reflection** (`D-INTEROP-REFLECT` = **A, compile-time intrinsic**) | ✅ **DONE 21–22/09** — surface frozen: `interop.schema(R)` → immutable `List<Field>`, zero runtime reflection, all targets, boundary-only; X6.0–X6.3 (intrinsic+fold, diagnostics `INTEROP001`/`INTEROP002`, `interopschema` cell + Arrow/Parquet binding E2E `InteropSchemaE2ETest` 18/18); **plan CONCLUDED + moved to `docs/` 22/09** | ✅ |
| 2.8.6 | **X2 interop engine — Python/R** (`D-COMPLETE-FIRST` item 2) | 🔨 **UNDER DEVELOPMENT — fatia 1 LANDED 26/09** (lane compiler 9092): `KofPy` host in Kof over `process.spawn`+`json.decode<T>` — `var py = KofPy(src)` + `py.callInt/callDouble/callBool/callString(fn, listOf(...))`, JVM≡x86≡JS≡SCRIPT goldens measured, `INTEROP004`/`INTEROP006`/`INTEROP005` named (§514 blocks cross); fatia 2 = records↔JSON fold + R engine (fatia 3 cross with §514) — plan `interop-engine-plan.md` (+PT) landing log | fatias 2–5 |

**Spec plan (X5 + X6):** [`type-system-extensions-plan.md`](../type-system-extensions-plan.md) — **APPROVED 21/09 (D-TYPE-VARIANCE/INTEROP-REFLECT)**; **CONCLUDED + MOVED to `docs/` 22/09** (all slices X5.0–X5.5, X6.0–X6.3 landed with proof).

### TIER 3–5 — Legacy migration platform (Phases A–H) ✅ code+tests

`kof inspect/decompile/translate/compare/migrate` in the CLI (`Main.java`);
Legacy Semantic IR with Confidence Model (5 levels) + "never invent". Proof
measured 15/09 at HEAD (`7b0bfbe0`, fresh classes): **Decompile 67 + PostDom 6,
Translate 61, Compare 7, Migrate 3** — all green (the 13/09 numbers 57/33/6/3
were stale; the former "1 red cell" `qualifiedLocalTypeTranslates` is GREEN
since the `.22` lane closed it). Method body recovery still partial
(structural joins = Phase C; re-measured 15/09 with an instrumented probe:
1793 stubs, the biggest family is if-test prefixes with computation/invokes —
519/566 TRAPs needing the post-dominator walker; the old "2452" StoreCat
number is stale, the pure if-then join sub-case already recovered). The
detailed technical history lives in `future/LEGACY_MIGRATION.md` +
`future/DECOMPILER.md` (§7) — **do not duplicate here**; this table only gives
the order. **DEPRIORITIZED 15/09 (maintainer): TIER 3–5 is not current work.**

### TIER 6–12 — Universal platform (architecture **UNDER DEVELOPMENT** 17/09 — R12 overridden; governed by `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`)

| Tier | Stage | Scope (one line) |
|------|---------|--------------------|
| 6 | AUTOMATION | `kof.workflow`/`batch`/`shell`/`ssh` — jobs as Kof code, never YAML/bash |
| 7 | INFRASTRUCTURE | `infra "prod" {}` (codegen, not HCL) + reconciliation loop — deps 1.4, 2.2 |
| 8 | DATA | typed `dataframe` + Arrow/Parquet/statistics **via FFI** (wrapper, never reimplement) — deps 2.1, pkg manager |
| 9 | SECURITY | S2 `Secret`/`KeyHandle` · S3 `keys.*` · S4 asymmetric · S5 **PQC** (`liboqs`, NIST) · S6 hybrid · S7 `secure.channel`; only FFI to an audited lib |
| 10 | SCIENTIFIC | BLAS/LAPACK/GPU/MPI **via FFI**; Native SIMD (research); deps 2.1, 2.4, 1.2 |
| 11 | BIO | `kof-bio` (official package): FASTA/FASTQ/VCF + alignment via FFI/CLI — deps 6, 8, 10 |
| 12 | UNIVERSAL | total integration + mature pkg manager + LSP/debug/profiler per domain; **final test: the language core barely grew** |
| — | **NORTH STAR (post-12) — BOOTSTRAPPER** | the Kof compiler written in Kof; design-plan draft `future/PLAN-BOOTSTRAP.md` (BS-1, `DECISIONS.md` §D-BOOTSTRAP, DECIDED 20/09, draft owner `.18`); execution gated by the plan’s E1–E6 and by the 1.0 exit gate — R12 governs, no stage is skipped for it |

### Critical path (what blocks what)

`Legacy-Class-File-Parser` → all Tiers 3–5 · `Decompiler-Structural` →
`Diff-Framework` → `Migration-Reports` · `2.1 FFI` → Tiers 8/9/10 (everything via
FFI) · `2.2 codegen hook` → `infra`/gRPC stubs · **TIER 1 (SYSTEMS) closes
before ANY Tier 6+ (R12).**

### TIER 13 — Tech-debt ledger queue (OPEN 23/09, `D-TECHDEBT-23/09`; **LEDGER KILLED 24/09** — debt measured zeroed)

Ordered queue from the maintainer's multiple-choice rulings 23/09. **24/09,
maintainer order: the `tech-debt.md` ledger is KILLED** — every live §NNN it
tracked was re-measured ✅ in `known-bugs.md` (§205/§248/§271/§283/§423 23/09;
§278 24/09) and the `check_500` size gate is green; the ledger, the
`technical-debt/` scout contract and the `debt-scout` tooling were removed.
13.6 continues below as its own front (it was never ledger debt — it is the
FFI struct spec). Claim in `DOING.md` before code; one item = one owner
+ one proof.

| # | Item | Owner lane | Proof |
|---|---|---|---|
| 13.1 | §248 — interface default methods on JS + Native (parity) | compiler (**9092, ✅ DONE 23/09**) | Native vtable slot + JS materialization; `InterfaceDefaultMethodE2ETest` 7/7 on 4 targets |
| 13.2 | §271 — bridge methods on generic-interface impls (erasure ABI) | compiler (**9092, ✅ DONE 23/09**) | JVM face ✅ (§356 `c8d55a10`); Native face ✅ (§483) — `NoSuchMethodError` closed, suite green |
| 13.3 | §278 — port `kof.security` (`kof.security` ✅ **DONE 23/09**, byte-parity Android=JVM; `kof.gpu` residual — FFM absent on Android, needs a design decision) | gaps-db | security byte-parity proven; gpu face pending |
| 13.4 | §423 — port `kof_channel_*` runtime to riscv64/aarch64 | nat (**✅ DONE 23/09, baremetal 9092**) | `kof_channel_*` in slice `RtB61`, `NAT005` gate removed; qemu parity on both arches (`BareCollectionPrimitiveArgE2ETest` 12/12; ledger §423 ✅ FIXED) |
| 13.5 | Split batch: `NativeBackend` 603 + `CompilerPipeline` 588 + `RuntimeOrm7` 585 (behavior-preserving, precedent §442/§446) | 9092 (**✅ DONE 23/09**) | `CompilerPipeline` ✅ 475; `RuntimeOrm7` ✅ split (`RuntimeOrm7` 34 + `RuntimeOrm7Setup` 265 + `RuntimeOrm7Fetch` 329); `NativeBackend` ✅ 547 (<600, tolerated band; baseline 571→547 refreshed) — `check_500.sh` rc=0, suite green |
| 13.6 | D6-1=B — mutable `struct` by-ref front (spec-first, rule 11) | FFI | design §4/§6 reviewed, then parser/typer diff + tests |

## 24. KOF 1.0 EXIT GATE — contract stabilization (RATIFIED 09/20/2026, `DECISIONS.md` §D-RELEASE-1.0; edges closed by `D-1.0-EDGES`)

Development meta until the first RC: **no bug ships, no edge stays open.** The
normative text is `docs/PROPOSAL-1.0-EXIT-GATE.md` (+`.pt_BR.md`)
§§8–20; the order of execution is its §23 queue. Done here (ratification pass):
steps 1–4 — active branch `beta-0.5.0` confirmed, `DECISIONS.md`/`AGENTS.md`
re-read, `D-RELEASE-1.0` recorded, EN/PT synchronized (doc promoted from
`future/`, approval block filled as record of the maintainer's chat order).

Open queue (every lane obeys; owner claims in `DOING.md`):

| # | Item (doc ref) | Acceptance proof |
|---|---|---|
| EG-1 | Define `release-blocker` **mechanically** (§11; Q3) | every OPEN issue classified in exactly one of BLOCKS 1.0 / OUTSIDE 1.0 SURFACE / POST-1.0 / NOT A BUG / TRACKING (`tracking/contract`) via label+ledger; script lists violations; RED test first. **DONE** `ea5d4dfe` + 5th-category complement |
| EG-2 | Implement the machine gate (§10 trust criteria; steps 6–7) | gate fails on planted false-green/false-red fixtures; verdict bound to the analyzed SHA; stale analysis cannot decide a new commit; `CODEQL_GATE_SKIP` usage becomes an exception with recorded cause, then dies. **DONE 20/09** (proposal §10): `0d2a019d` closed 6/8; the stability lane closed SHA binding + empty≠unavailable in `scripts/codeql-gate.sh`, RED-first (`scripts/tests/codeql-gate-test.sh` 10 scenarios, registered) |
| EG-3 | Real package tested **outside the repo** (§12; step 9) | published-layout artifact runs the corpus E2E on a clean dir (the #550 lesson, pinned). **DONE 20/09** `67c503db` (`scripts/test-package-outside-repo.sh`, PASS measured) |
| EG-4 | BEFORE/AFTER validation of the gate (step 8) | same SHA measured before/after; no regression in existing lanes' pushes. **DONE** — evidenced by EG-2 (gate verdicts bound to the analyzed SHA, deterministic; live gate measured before/after with no new false-red in the lanes) |
| EG-5 | Final target matrix (§13–§14; step 10) | JVM / x86-64 / riscv64 / aarch64 / JS / Script **/ KofC / Android** green on the SAME candidate + golden byte parity where the contract requires; KofC and Android each carry their own gate (EG-9/EG-10). **Harness DONE 20/09**: `scripts/target-matrix.sh` (one command; core 6 targets byte-parity vs JVM oracle, cross executes under qemu when present, honest SKIP→INCOMPLETE; kofc/android = DELEGATED EG-9/EG-10), RED-first `scripts/tests/target-matrix-test.sh` registered; measured PASS on the six core targets. **Artifact-freshness guard 20/09** (`jar_stale`): without `--dist`, a tree `lib/kof.jar` older than the sources is refused with a named cause (rc=3) instead of measuring a phantom binary — the stale-jar false `PARITY: 0%` on the cross targets can no longer be read as a divergence. The RC-day run on the candidate remains |
| EG-6 | Close the open EDGES the maintainer owns (§21 Q2/Q7, §35 candidates) | **DONE 20/09/2026 (`D-1.0-EDGES`)**: Q1 (1.0 line opens after the 0.5.0 release + EG-1..EG-7), Q2/Q7 (KofC + Android inside Stable 1.0, own gates), §35 (all nine reinforcements mandatory) |
| EG-7 | VERSION / docs / metadata sync (§16, §13 note, §35 site line) | VERSION, pom `revision`, package version.properties, CHANGELOG, AGENTS header, release docs, public site, support matrix — one consistent statement. **Repo side DONE** (bump `404d8be6`; release docs synced). **Site side deferred — maintainer-owned** (she updates `koflang.github.io` shortly; chat 20/09) |
| EG-8 | First 1.0 RC candidate (step 11) — ONLY when EG-1…EG-7 all close | the §8 checklist green with reproducible evidence on the candidate SHA + Mel's explicit "the 1.0 line is open" declaration (Q1) |
| EG-9 | KofC gate (own gate — `D-1.0-EDGES`) | KofC green on the candidate with its own gate evidence. **Mechanism DONE 20/09**: `scripts/test-kofc-gate.sh` — preflight names the missing toolchain (`as`+`ld`/`gcc`, JDK ≥ 25), compiles+runs the supported corpus (5 cases, real ELF executed, stdout asserted), and rejects malformed input without emitting a binary (the #485 class, R6/Q7); verdict bound to the SHA (`KOFC-GATE: PASS sha=…`). RED-first offline: `scripts/tests/test-kofc-gate-test.sh` (5 scenarios, registered). PASS measured on `8fa39ff9` |
| EG-10 | Android gate (own gate — `D-1.0-EDGES`) | Android green on the candidate with its own gate evidence (CI runs the APK). **Mechanism DONE 20/09**: `scripts/test-android-gate.sh` — honest preflight (JDK ≥ 25 + `jar`; `ANDROID_HOME` + complete build-tools ≥ 35 + a platform `android-N/android.jar`); absent SDK = honest SKIP exit 3 naming what is missing (never a false green, R6), and the CI `android.yml` runs the SAME gate. With the SDK it runs `kof build --target android --apk` (the standalone aapt2→d8→zip→zipalign→apksigner pipeline) and proves the artifact is a real zip carrying `AndroidManifest.xml` + `classes.dex`; verdict bound to the SHA (`ANDROID-GATE: PASS sha=…`). RED-first offline: `scripts/tests/test-android-gate-test.sh` (6 scenarios, registered) |

Rules binding every item: the §8 gate is AND — one unmet item blocks the RC
regardless of the others; per `D-1.0-STABILITY-100` (20/09) no 1.0.0 ships
while ANY item remains open in `docs/development/`, `docs/development/future/`
or `docs/bugs-and-gaps/` — 100% resolved, with the cross-target parity proven
by measurement; gaps stay only per §15 (OUTSIDE 1.0 + honest +
documented); the freeze (§17) starts at the first RC and freezes the surface,
not the stabilization; RC→Stable adds no regression (§19). TIER 12's "final
test" remains compatible: stabilization touches structure/diagnostics, never the
core surface.

**0.5.0 release gate (`D-RELEASE-0.5.0-GATE`, 09/20/2026):** the 0.5.0 release
(precondition for opening the 1.0 line, Q1) is cut only when the maintainer's
seven conditions hold, each measured — 100% target parity; no pending decision;
all loose `docs/development/*.md` concluded and moved out; total stability;
0 open bug issues; all edges closed; nothing pending in bugs-and-gaps. Queue +
current state: `release-beta-0.5.0-prep.md` §"Release gate". Mechanized by
`scripts/check_release_050_gate.sh`.

### TIER 14 — Quality-pipeline migration (DECIDED 26/09 `D-QUALITY-PIPELINE-2609`; execution gated POST-0.5.0)

The `lab → testing → prerelease → stable → release/x.y.z → tag` esteira is
design-closed. **NO unit may start before the 0.5.0 cycle closes** — today's
work keeps landing on `beta-0.5.0` (`D-BRANCH-0.5.0` in force). When the
maintainer opens the front, the units are:

| # | Unit | Gate/proof |
|---|---|---|
| 14.1 | Pilot: `release/0.5.0` temporary branch (version bump, changelog, artifacts, checksums) cut from stable-candidate; publish; end branch | GitHub Release + tag `v0.5.0`; nothing new added mid-pilot |
| 14.2 | Atomic cutover (ONE change): create `lab`/`testing`/`prerelease`/`stable`; CI workflows (`codeql.yml` branches+schedule, gates, cross jobs) re-pointed; `scripts/sync-push.sh` + §NNN-tip gate + heartbeat/watcher crons re-pointed; `AGENTS.md` (`D-BRANCH` superseded), `DOING.md`, `DECISIONS.md` updated | build after cutover: every automation resolves the same stage; zero agent left pushing to a retired branch |
| 14.3 | Promotion tooling: `lab→testing` runs the full suite as first formal gate; promotion checks encode 80% (testing→prerelease) / 100% (prerelease→stable + CLOSEALL/docs) mechanically (extend `check_release_050_gate.sh` per stage) | scripted proof per promotion, not opinion |
| 14.4 | Branch protections: no force-push + required checks on `testing`/`prerelease`/`stable`; hotfix path (PR + backport + revalidation) documented in `AGENTS.md` | GitHub settings + docs mirror |
| 14.5 | Open point to settle with the maintainer in this plan: does `lab` keep the zero-regression floor (rule 8) without per-push CI? | recorded in `D-QUALITY-PIPELINE-2609` §OPEN POINT |
