[English](security.md) | [Português](security.pt_BR.md)

# Kof Standard Library — Security + Enterprise Capability Audit

**Last updated:** September 12, 2026
**Version:** 0.5.0-beta (free-list + riscv64; `kof.http` JVM+JS + retry/circuit)

> Permanent architectural document.
>
> Goal: map the capabilities of the Spring ecosystem, audit the current state
> of the Kof Standard Library and define the architecture of `kof.security`
> as the enterprise foundation of the platform.
>
> Fundamental rule: **do not copy Spring, do not replicate APIs, do not create
> wrappers.** Build the Kof solution for the same problems.

---

# 1. PRINCIPLE

A Kof application must build a complete enterprise application using the
Kof platform itself:

```text
HTTP, REST, auth, authorization, validation, serialization, database,
messaging, observability, testing
```

Spring remains valid as an external alternative — never as an architectural
dependency.

---

# 2. SPRING ECOSYSTEM MATRIX

Status legend for the Kof Standard Library:

```text
EXISTS           → already implemented in the Kof platform
PARTIAL          → exists, but with known gaps
MISSING          → does not exist, priority defined
NOT APPLICABLE   → does not apply to the Kof architecture
```

## 2.1 Spring Framework

| Capability | Spring | Kof | Status | Notes |
|-----------|--------|-----|--------|-------|
| Core (beans, container) | Core/Beans/Context | — | MISSING (low priority) | Kof does not need a container; functions/global state cover most cases |
| Dependency Injection | Core/Context | — | MISSING (low) | Deliberate future decision; see §3 |
| Expression Language | SpEL | — | MISSING (medium) | Can be solved with first-class functions |
| AOP | AspectJ | — | MISSING (medium) | Function composition + middleware cover common cases |
| Validation | spring-validation | `kof.validation` | **EXISTS** | 13 predicates JVM/Native/JS |
| Web | spring-web | `kof.web` (`web.app()`) | **EXISTS** | Routes, params, query, headers, body, `app.use` middleware, `status`/`headerSet` |
| Web MVC | WebMVC | `kof.web` + handlers | PARTIAL | JVM only today; embedded JS (alpha) |
| WebFlux | WebFlux | `spawn` + virtual threads | PARTIAL | Own concurrent model (JVM/Native/JS) |
| WebSocket | WebSocket | `kof.web` (`app.ws`) | **EXISTS (JVM)** | RFC 6455: handshake + masked frame codec (Native/JS `WEB004` — gate `ExpressionBuiltinInstanceCalls`, 16/09) |
| SSE | (Spring via `SseEmitter`) | `kof.web` (`app.sse`) | **EXISTS (JVM)** | `sse.send/event/close` (JVM + JS handler-scoped ✅ 16/09, `7cd69a7b`; Native `WEB003`, JS post-return push `WEB003` residual) |
| Messaging | spring-messaging | `kof.mq` (publish/subscribe/queue) | **EXISTS** | JVM+Native+JS (MQ001 closed 01/09) |
| Transactions | spring-tx | `kof.db` (`transaction {}`) | **EXISTS** | JVM (JDBC commit/rollback) + Native (SQLite) + JS (untyped 16/09) |
| Scheduling | spring-context | `kof.scheduler` (`every`/`cancel` + `spawn`) | PARTIAL | JVM (ScheduledExecutor) + JS (setInterval) + Native (`SCHED001` closed 31/08); `at(expr)` = real 5-field UTC cron **or idiomatic duration** (`30m`, `90s`, `1d&30m`, `3a&6M...`; units `ms/s/m/h/d/M/a`, `&` composition, UTC calendar clamp for `M`/`a`, D-SCHED-DURATION 19/09; JVM + JS parity, `kof_duration_next_delay_ms`); Native refuses at compile time (`CRON001`) |
| Events | ApplicationEvent | `kof.mq` pub/sub | PARTIAL | pub/sub queues in the stdlib |
| Resources | Resource | `kof.io` | EXISTS | |
| Cache | spring-cache | `kof.cache` (`get/set/set-ttl/ttl/delete/clear`) | **EXISTS** | 3 targets (native fix 30/08) |
| Conversion | ConversionService | Kof type system | NOT APPLICABLE | Static typing resolves at compile-time |
| Testing | spring-test | `kof test` + `assert` + JUnit (internal) | PARTIAL | |

## 2.2 Spring Boot

| Capability | Spring Boot | Kof | Status | Notes |
|-----------|-------------|-----|--------|-------|
| Lifecycle | run() | `main()` + `web.app().listen()` | **EXISTS** | |
| Configuration | application.yml | `kof.config` (env + file + typed) | **EXISTS** | 3 targets; `kof config gen` |
| Profiles | profiles | `KOF_PROFILE` → `kof.<profile>.config` | **EXISTS** | |
| Dependency management | starters | — | NOT APPLICABLE | No dependencies: the stdlib IS the platform |
| Auto configuration | auto-config | — | NOT APPLICABLE | The compiler knows what the program uses |
| Embedded servers | Tomcat/Jetty | `KofHttpServer` | EXISTS | JVM only |
| Actuator | actuator | `kof.observability` | **EXISTS** | health/metrics/request IDs JVM/Native/JS |
| Health checks | health | `kof.observability.health` | **EXISTS** | JVM/Native/JS |
| Metrics | micrometer | `kof.observability` | **EXISTS** | counter/increment/gauge JVM/Native/JS |
| Observability | tracing | `kof.observability` | **EXISTS** | health/metrics/histograms/request IDs + W3C `traceId`/`spanId` and timed `spanStart`/`spanEnd` (3 targets, `OBS002`); OTel export `exportSpans()` OTLP/JSON on JVM/JS (`OBS003`, Native honest gap) |
| Logging | logback | `kof.log` + `println` | **EXISTS** | `log.debug/info/warn/error` JVM/Native |
| Graceful shutdown | shutdown | `web.close()` + spawn join | PARTIAL | |
| CLI/tooling | spring CLI | `kof` CLI (build/run/serve/test/bench/profile/inspect) | EXISTS | |

## 2.3 Spring Security

| Capability | Spring Security | Kof | Status | Priority |
|-----------|----------------|-----|--------|-----------|
| Password hashing | BCrypt | `kof.security.passwords` | **EXISTS (this stage)** | CRITICAL |
| Authentication | AuthenticationManager | `kof.security.auth` | **EXISTS (this stage)** | CRITICAL |
| Authorization (roles) | authorize | `kof.security.auth.hasRole` | **EXISTS (this stage)** | CRITICAL |
| Authorities/permissions | authorities | `kof.security.auth.hasPermission` | **EXISTS (this stage)** | HIGH |
| Sessions | session management | `kof.security` (`security.sessionCreate/sessionGet/sessionDestroy`) | **EXISTS** | JVM/Native/JS |
| Rate limiting | Bucket4j | `kof.security` (`security.rateLimit`) | **EXISTS** | JVM/Native/JS |
| API keys | ApiKeyFilter | `kof.security` (`security.apiKeyGenerate/apiKeyValid`) | **EXISTS** | JVM/Native/JS |
| Security context | SecurityContext | `kof.security.auth` (request context) | **EXISTS (this stage)** | CRITICAL |
| CSRF | CsrfFilter | `kof.security.security.csrf*` | **EXISTS (this stage)** | HIGH |
| CORS | CorsFilter | `kof.security.security.cors*` | **EXISTS (this stage)** | HIGH |
| OAuth2 client | OAuth2Client | `kof.security.oauth` (architecture) | MISSING | MEDIUM |
| OIDC | OIDC | same | MISSING | MEDIUM |
| JWT | Nimbus/JJWT | `kof.security.jwt` | **EXISTS (this stage)** | CRITICAL |
| Resource server | Bearer | `kof.security.jwt` + `auth` | **EXISTS (this stage)** | HIGH |
| Method security | @PreAuthorize | `auth.requireRole` (middleware) | **EXISTS (this stage)** | HIGH |
| Security headers | headers | `kof.security.security.*` (helpers) | **EXISTS (this stage)** | HIGH |
| Request filtering | filter chain | `app.use` (middleware) | **EXISTS** | CRITICAL |
| Remember-me | remember-me | — | MISSING | LOW |
| Logout | logout | — | MISSING | MEDIUM |
| Security events | events | — | MISSING | LOW |

| Security context | SecurityContext | `kof.security.auth` (contexto de request) | **EXISTS (esta etapa)** | CRÍTICA |
| CSRF | CsrfFilter | `kof.security.security.csrf*` | **EXISTS (esta etapa)** | ALTA |
| CORS | CorsFilter | `kof.security.security.cors*` | **EXISTS (esta etapa)** | ALTA |
| OAuth2 client | OAuth2Client | `kof.security.oauth` (arquitetura) | MISSING | MÉDIA |
| OIDC | OIDC | idem | MISSING | MÉDIA |
| JWT | Nimbus/JJWT | `kof.security.jwt` | **EXISTS (esta etapa)** | CRÍTICA |
| Resource server | Bearer | `kof.security.jwt` + `auth` | **EXISTS (esta etapa)** | ALTA |
| Method security | @PreAuthorize | `app.security({roles: ...})` + `auth.hasRole` | **EXISTS (C18, JVM)** | ALTA |
| Security headers | headers | `app.security()` (composto) + `security.*` (helpers) | **EXISTS (C18, JVM)** | ALTA |
| Request filtering | filter chain | `app.use` + `app.security()` (composto, ordem fixa) | **EXISTS (C18, JVM)** | CRÍTICA |
| Remember-me | remember-me | — | MISSING | BAIXA |
| Logout | logout | — | MISSING | MÉDIA |
| Security events | events | — | MISSING | BAIXA |

## 2.4 Spring Data

| Capability | Spring Data | Kof | Status | Priority |
|-----------|-------------|-----|--------|-----------|
| Repository abstraction | Commons | `kof.orm` (entity + CRUD) | **EXISTS (JVM)** | HIGH |
| JPA | JPA | — | MISSING | MEDIUM (decision: direct JDBC/ORM is more Kof) |
| JDBC | JDBC | `kof.db` (`db.execute`/`query<T>`/`transaction`) | **EXISTS** | HIGH |
| R2DBC | R2DBC | — | MISSING | MEDIUM |
| MongoDB | Mongo | `kof.orm` (official driver, E2E) | **EXISTS (JVM)** | MEDIUM |
| Redis | Redis | — | MISSING | MEDIUM |
| REST exports | Data REST | `kof.rest` (planned) | MISSING | MEDIUM |
| Migrations | Flyway/Liquibase | `kof.orm` (`orm.migrate`, `kof_migrations`) | **EXISTS** | HIGH |

## 2.5 Spring Integration / Cloud / Batch / GraphQL / Session / Kafka / AMQP / Pulsar / WS / HATEOAS / REST Docs / Modulith / Authorization Server

| Capability | Spring | Kof | Status | Priority |
|-----------|--------|-----|--------|-----------|
| Messaging channels | Integration | `kof.mq` (publish/subscribe/queue) | **EXISTS** | MEDIUM |
| Retry/error handling | Integration | `kof.http` (`http.retry`) | **EXISTS (JVM+JS)** | MEDIUM |
| Service discovery | Cloud | — | MISSING | LOW (manual config) |
| Gateway | Cloud Gateway | `kof.web` + proxy | PARTIAL | LOW |
| Circuit breakers | Resilience | `kof.http` (`http.circuit`) | **EXISTS (JVM+JS)** | LOW |
| Distributed tracing | Sleuth | `kof.observability` (`requestId`/`correlationId`, W3C `traceId`/`spanId`) | PARTIAL | LOW |
| Batch (jobs/steps/retry) | Batch | `kof.mq` queue + `kof.scheduler` | PARTIAL | MEDIUM |
| GraphQL | GraphQL | — | MISSING | LOW (REST first) |
| Distributed sessions | Session | `kof.security` (`sessionCreate/Get/Destroy`) | **EXISTS (JVM/Native/JS)** | LOW |
| Kafka producer/consumer | Kafka | `kof.messaging` (planned) | MISSING | MEDIUM |
| AMQP queues | AMQP | same | MISSING | MEDIUM |
| Pulsar | Pulsar | same | MISSING | LOW |
| SOAP/XML | WS | — | NOT APPLICABLE | REST/JSON is the Kof idiom |
| Hypermedia | HATEOAS | — | MISSING | LOW |
| API docs | REST Docs | `docs/` + training | PARTIAL | MEDIUM |
| Module boundaries | Modulith | packages + type system | PARTIAL | LOW |
| OAuth2 authorization server | Auth Server | `kof.security.oauth` (architecture) | MISSING | MEDIUM |

---

# 3. ARCHITECTURAL DECISIONS

## 3.1 DI / container

Kof has no bean container. First-class functions, lambdas and
module state cover composition. A DI container would be ceremony with no
gain in intent. **Decision: NOT APPLICABLE for the core; re-evaluate only
if a pattern of need emerges (plugins/SPI).**

## 3.2 Database

`kof.database` will be SQL-first (idiomatic JDBC), with no heavy ORM. Kof
records + explicit SQL cover most applications with fewer layers.
JPA-style ORM is a future decision, not an assumption.

## 3.3 Observability

`kof.metrics`/`kof.observability` will follow the pattern of the metrics already
existing in the tooling (`kof bench`, `kof profile`): collect at runtime,
expose simply, integrate with JFR/perf/V8 when it exists.

## 3.4 Security

`kof.security` is implemented as **compiled namespaces** (same pattern
as `kof.io`/`kof.web`): the intent is expressed directly, the compiler
resolves the runtime function and each target provides the implementation.

---

# 4. KOF STANDARD LIBRARY AUDIT (real state)

| Module | Exists? | Idiomatic? | Safe? | Multi-target | Performant? | Tests | Docs |
|--------|---------|-------------|---------|--------------|---------------|--------|------|
| `kof.core` (println, arithmetic, strings, arrays) | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.collections` (List, listOf) | YES | YES | YES (bounds) | JVM/Native/JS | PARTIAL (List<Int> boxing on the JVM) | YES | YES |
| `kof.io` (File/Path/Directory, readFile) | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.time` (`now()`) | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.json` (encode/decode) | YES | YES | YES (complete FP/arrays in Native 31/08) | JVM/Native/JS | YES | YES | YES |
| `kof.http` (serve + client) | YES | YES | PARTIAL (auth via middleware) | JVM (serve); JVM+JS (client) | YES | YES | YES |
| `kof.web` (web.app, routes, ws/sse) | YES | YES | PARTIAL (auth under construction) | JVM | YES | YES (new) | YES |
| `kof.rest` | NO | — | — | — | — | — | — |
| `kof.database` (`kof.db` + `kof.orm`) | YES | YES | YES (typed bind; explicit SQL) | JVM + Native (SQLite; MySQL wire x86-64 real, F2d1–F2d7) | YES | YES | YES |
| `kof.security` | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.concurrent` (spawn/await) | YES | YES | YES | JVM/Native (pthread)/JS | YES | YES | YES |
| `kof.messaging` (`kof.mq`) | YES | YES | PARTIAL (in-memory) | JVM+JS | YES | YES | YES |
| `kof.validation` | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.serialization` | PARTIAL (json) | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.logging` | YES (`log.debug/info/warn/error`) | YES | YES | JVM/Native | YES | YES | YES |
| `kof.observability` | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.metrics` | YES (`kof.observability`) | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.config` | YES | YES | YES (separate secrets) | JVM/Native/JS | YES | YES | YES |
| `kof.cache` | YES | YES | PARTIAL (in-memory) | JVM/Native/JS | YES | YES | YES |
| `kof.test` (`kof test`, assert) | YES | YES | YES | JVM/Native/JS | YES | YES | YES |
| `kof.cli` (kof CLI) | YES | YES | YES | JVM | YES | YES | YES |
| `kof.process` | YES | YES | YES | JVM/JS (Native: `PROC001`) | YES | YES | YES |
| `kof.crypto` | NO (part of kof.security) | — | — | — | — | — | — |
| `kof.ui` (UI platform) | PARTIAL | PARTIAL | — | JS | — | PARTIAL | PARTIAL |

## 4.1 State of security on the platform today

> **Baseline (pre `kof.security` v1)** — snapshot of what was missing BEFORE
> the implementation. The current state (implemented, 3 targets) is in §7.

| Area | Current state (baseline) |
|------|--------------|
| Password hash | NONEXISTENT (the programmer would use sha256 — forbidden by design) |
| Constant time | NONEXISTENT |
| Secure random | NONEXISTENT (no random API) |
| JWT | NONEXISTENT |
| Headers/CSRF/CORS | NONEXISTENT |
| Secrets (env) | `secrets.get(name[,fallback])` (raw `String`) + `secrets.secret(name)` → `Secret` |
| Auth in HTTP | PARTIAL: manual `header("x-auth")` in the middleware |
| Secrets in logs | PROTECTED on JVM via the `Secret` type (D-SECRETS, Stage 5/3.6): prints `Secret(*** )`, raw text only through `reveal()`, `json.encode(secret)` redacted at runtime (P2) and a `SECN009` warning when `reveal()` feeds `log.*`/`json.encode`. JS/Native/Script = honest gap `SECN008` (Android runs `kof.security` since §278, 23/09) |
| Key material | `KeyHandle` (D-SECRETS P3) never exposes raw bytes: `secrets.keyFromHex/keyFromPem/keyFromKeystore(...)`, `rotate()` revokes the old handle (later use `SECN010`); JS/Native/Script = honest gap `SECN008` (Android runs `kof.security` since §278, 23/09) |

---

# 5. `kof.security` ARCHITECTURE (definition)

Intent namespaces (compiled, same pattern as `kof.io`/`kof.web`):

```text
kof.security
├── passwords        → hash/verify/needsRehash (PBKDF2-HMAC-SHA256, secure by default)
├── crypto           → sha256/sha512, sha256Bytes, hmacSha256/hmacSha256Bytes, aesGcm (encrypt/decrypt), randomHex/randomInt
├── jwt              → create/verify (HS256, exp/iss/aud, no algorithm confusion)
├── secrets          → get (env, raw String), redact, of/secret/fromBytes (→ Secret value type),
│                      keyFromHex/keyFromPem/keyFromKeystore (→ KeyHandle, raw key never exposed)
├── security         → constantTimeEquals, randomHex, redact, csrfToken/csrfValid, corsAllowed, headers helpers,
│                      rateLimit, sessionCreate/sessionGet/sessionDestroy, apiKeyGenerate/apiKeyValid (G9),
│                      cookieSet/cookieGet (C11, secure defaults)
└── auth             → web context: secret, token, authenticated, claims, user, hasRole, hasPermission,
                       resourceServer/resourceServerVerify (layer 16: JWKS RS/ES)
```

Support per target (current state — `KofSecurity.supportedOn`):

| Function | JVM | Native | JS |
|--------|-----|--------|----|
| `passwords.hash/verify/needsRehash` | YES (javax.crypto PBKDF2) | YES (asm PBKDF2-HMAC-SHA256) | YES (platform-delegated PBKDF2) |
| `crypto.sha256/sha512` | YES | YES (asm, FIPS 180-4) | YES (JS) |
| `crypto.hmacSha256` | YES | YES (asm) | YES (JS) |
| `keyExchange.privateKey/publicKey/shared/hkdfSha256` (D-KOF-X25519) | YES (JCA X25519/HKDF) | gap SECN012 (asm port queued) | gap SECN012 (host bridge carries no Secret scalars) |
| `crypto.sha256Bytes` | YES | YES (layout-compatible asm alias of sha256) | gap SECN000 (D-KOF-DIGEST-BYTES, 02/10) |
| `crypto.hmacSha256Bytes` | YES | YES (asm alias of hmacSha256) | gap SECN000 |
| `crypto.aesGcm` encrypt/decrypt | YES | YES (asm, GCM) | YES (pure JS, 01/09) |
| `crypto.randomHex/randomInt` | YES (SecureRandom) | YES (getrandom) | YES (kof_platform) |
| `jwt.create/verify/secret` | YES | YES (asm: base64url + HMAC) | YES |
| `secrets.get` | YES (env) | YES (`/proc/self/environ`) | YES (kof_platform) |
| `security.constantTimeEquals` | YES | YES (asm) | YES (JS) |
| `security.redact` | YES | YES (asm) | YES |
| `security.csrfToken/csrfValid` | YES | — | — |
| `security.corsAllowed` | YES | — | — |
| `security.cspHeader/hstsHeader/...` | YES | — | — |
| `security.rateLimit/session*/apiKey*` (G9) | YES | YES (asm) | YES (JS) |
| `security.cookieSet/cookieGet` (C11) | YES | — (SECN006) | YES (JS) |
| `auth.*` (web context) | YES (Bearer JWT + ThreadLocal) | — | — |

| `passwords.hash/verify/needsRehash` | SIM (javax.crypto PBKDF2) | SIM (asm PBKDF2-HMAC-SHA256) | SIM (PBKDF2 platform-delegated) |
| `crypto.sha256/sha512` | SIM | SIM (asm, FIPS 180-4) | SIM (JS) |
| `crypto.hmacSha256` | SIM | SIM (asm) | SIM (JS) |
| `crypto.aesGcm` encrypt/decrypt | SIM | SIM (asm, GCM) | SIM (JS puro, 01/09) |
| `crypto.randomHex/randomInt` | SIM (SecureRandom) | SIM (getrandom) | SIM (kof_platform) |
| `jwt.create/verify/secret` | SIM | SIM (asm: base64url + HMAC) | SIM |
| `secrets.get` | SIM (env) | SIM (`/proc/self/environ`) | SIM (kof_platform) |
| `security.constantTimeEquals` | SIM | SIM (asm) | SIM (JS) |
| `security.redact` | SIM | SIM (asm) | SIM |
| `security.csrfToken/csrfValid` | SIM | — | — |
| `security.corsAllowed` | SIM | — | — |
| `security.cspHeader/hstsHeader/...` | SIM | — | — |
| `security.rateLimit/session*/apiKey*` (G9) | SIM | SIM (asm) | SIM (JS) |
| `security.cookieSet/cookieGet` (C11) | SIM | — (SECN006) | SIM (JS) |
| `auth.*` (contexto web) | SIM (Bearer JWT + ThreadLocal) | — | — |
| `app.security()` (C18, middleware composto) | SIM (JVM) | — (WEB006) | — (WEB006) |
| `auth.resourceServer(jwksUrl, issuer, aud)` / `resourceServerVerify(token)` (camada 16) | SIM (JVM) | — (SECN007) | — (SECN007) |

Real gaps with compile-time diagnostics: `SECN001` (passwords),
`SECN003` (sha512), `SECN005` (G9) and `SECN006` (cookies in Native) — never
silently different behavior. `SECN002` (AES-GCM in JS) and
`SECN004` (jwt) closed.

**Rule**: any gap emits a clear compile-time diagnostic (e.g.
`SECN001: passwords.hash is not yet available on the Native target`).
Never silently different behavior (§16 of the performance doc).

## 5.1 Formats (versioned, unambiguous)

```text
passwords:   pbkdf2$sha256$<iterations>$<salt-b64>$<hash-b64>
crypto:      aesgcm$<iv-b64>$<ciphertext+tag-b64>
             chacha20$<nonce-b64>$<ciphertext+tag-b64>   (14/09, D-SEC)
jwt:         RFC 7519 HS256 (alg fixed, never accepted from the token)
```

---

# 6. NEXT STEPS

1. Implement `kof.security` (KofSecurity.java + JVM/Native/JS runtimes).
2. Unit + E2E + adversarial tests (`KofSecurityTest`).
3. Benchmarks (`benchmarks/security/`).
4. Documentation (`docs/stdlib/security.md`, `learn/`, `training/`).
5. Continuous audit: `kof.config`, `kof.database`, `kof.messaging`,
   `kof.validation`, `kof.logging`, `kof.observability` (next steps).

---

# 7. IMPLEMENTATION STATE (0.5.0-beta, re-synced 17/09/2026 — `VERSION` 0.5.0-beta, 2218 tests, free-list + mark-sweep + riscv64)

## 7.1 Implemented (0.5.0-beta)

| API | JVM | Native x86_64 (+ riscv64) | JS | Format |
|-----|-----|---------------------------|----|---------|
| `passwords.hash(password)` | ✅ PBKDF2-HMAC-SHA256 600k | ✅ (asm: internal HMAC + b64 + getrandom, free-list 27/08) | ✅ PBKDF2 (platform-delegated) | `pbkdf2$sha256$600000$salt$hash` |
| `passwords.verify(password, hash)` | ✅ constant-time | ✅ (asm, constant-time) | ✅ | |
| `passwords.needsRehash(hash)` | ✅ | ✅ | ✅ | |
| `crypto.sha256(data)` | ✅ | ✅ (asm FIPS 180-4, riscv64 `li a7`) | ✅ (pure JS) | hex |
| `crypto.sha512(data)` | ✅ | ✅ (asm FIPS 180-4, FIPS vectors tested) | ✅ (pure JS) | hex |
| `crypto.hmacSha256(key, data)` | ✅ | ✅ (asm) | ✅ (pure JS) | hex |
| `crypto.encryptAesGcm(plain, keyHex)` | ✅ AES/GCM/NoPadding | ✅ (asm GCM, round-trip E2E) | ✅ (pure JS, round-trip E2E) | `aesgcm$iv$ct` |
| `crypto.decryptAesGcm(ct, keyHex)` | ✅ (fails on tamper) | ✅ (asm, fails on tamper) | ✅ (pure JS, fails on tamper) | |
| `crypto.encryptChacha20(plain, keyHex)` | ✅ (RFC 8439, **interop with the JDK** validated 14/09) | honest SECN002 (130-bit asm in the queue) | ✅ (pure JS, interop with the JDK validated) | `chacha20$nonce$ct` |
| `crypto.decryptChacha20(ct, keyHex)` | ✅ (constant-time tag, fails on tamper) | honest SECN002 | ✅ (pure JS, constant-time tag) | |
| `crypto.randomHex(n)` | ✅ SecureRandom | ✅ getrandom (`li a7 318` x86_64 / `214` riscv64) | ✅ platform | hex |
| `crypto.randomInt(bound)` | ✅ | ✅ getrandom + rejection | ✅ platform | |
| `jwt.create(claims, secret[, ttl])` | ✅ HS256 + iat/exp | ✅ (asm: base64url + HMAC + kof_now) | ✅ | RFC 7519 HS256 |
| `jwt.verify(token, secret[, iss, aud])` | ✅ (sig, exp, iss, aud) | ✅ (asm, constant-time + exp/iss/aud) | ✅ | alg fixed HS256 |
| `jwt.secret()` | ✅ env `KOF_JWT_SECRET` or generated | ✅ (`/proc/self/environ`) | ✅ | 32 bytes hex |
| `secrets.get(name[, fallback])` | ✅ env | ✅ `/proc/self/environ` | ✅ platform | |
| `secrets.redact(value)` | ✅ | ✅ (asm) | ✅ | `abcd********wxyz` |
| `secrets.of(text)` / `secrets.secret(name)` | ✅ (→ `Secret`; unset env → `SECN015` error) | ❌ `SECN008` | ❌ `SECN008` | D-SECRETS face 1 |
| `secrets.fromBytes(bytes)` | ✅ (→ `Secret`, per-byte Latin-1) | ❌ `SECN008` | ❌ `SECN008` | lossless for non-text bytes |
| `Secret.reveal()` / `.redacted()` | ✅ | ❌ `SECN008` | ❌ `SECN008` | prints `Secret(*** )`; `reveal()` is the only raw export |
| `secrets.keyFromHex/keyFromPem/keyFromKeystore(...)` | ✅ (→ `KeyHandle`) | ❌ `SECN008` | ❌ `SECN008` | P3; raw key bytes never exposed |
| `KeyHandle.rotate()` | ✅ | ❌ `SECN008` | ❌ `SECN008` | revokes the old handle; later use fails `SECN010` |
| `crypto.hmacSha256(KeyHandle, msg)` / `aesGcm` / `chacha20` with `KeyHandle` | ✅ | ❌ `SECN008` | ❌ `SECN008` | P3 overloads |
| `jwt.create/verify(..., KeyHandle)` | ✅ | ❌ `SECN008` | ❌ `SECN008` | P3 overloads |
| `json.encode(Secret)` | ✅ redacted `"Secret(*** )"` | n/a | n/a | P2: never field-dumps a secret |
| `security.constantTimeEquals(a, b)` | ✅ `MessageDigest.isEqual` | ✅ (asm) | ✅ | |
| `security.randomHex` / `randomInt` | ✅ | ✅ | ✅ | |
| `security.csrfToken/csrfValid` | ✅ (session-scoped) | ❌ | ❌ | |
| `security.corsAllowed(origin, allowed)` | ✅ | ❌ | ❌ | |
| `security.cspHeader/hstsHeader/contentTypeOptionsHeader/frameHeader/referrerHeader` | ✅ (ready-made values) | ❌ | ❌ | |
| `auth.secret/token/authenticated/claims/user/hasRole/hasPermission` | ✅ (web context, Bearer JWT) | ❌ | ❌ | |
| `security.rateLimit(key, limit, window)` | ✅ fixed-window per-key | ✅ (asm, per-key counter, free-list) | ✅ (JS, Date.now) | per-key count |
| `security.sessionCreate(data)` / `sessionGet` / `sessionDestroy` | ✅ (ConcurrentHashMap) | ✅ (asm, 32 slots) | ✅ (JS object) | randomHex(16) id |
| `security.apiKeyGenerate` / `apiKeyValid` | ✅ (ConcurrentHashMap) | ✅ (asm, 32 slots) | ✅ (JS object) | randomHex(32) |

## 7.2 Cross verification

The SHA-256/HMAC values are identical across JVM, Native and JS and match
reference vectors (FIPS 180-4, RFC 2104) — verified by
`KofSecurityTest` on the three targets.

## 7.3 Tests

`KofSecurityTest` (25 tests): hashing/verification, wrong password, rehash,
SHA-256/512 vectors, HMAC, constant-time, random, JWT (signature, expiration,
issuer/audience, malformed token, algorithm confusion, non-object claims),
AES-GCM (JVM+Native round trip, tamper, wrong key), secrets/redact, and
target gap diagnostics (SECN001/002/003). Adversarial cases included (§18).

## 7.4 Benchmarks

`benchmarks/security/`: `password-hash`, `jwt`, `hash-speed`, `aes-gcm`
(jvm/js as supported).

## 7.5 Documented gaps

- ~~JWT in Native~~ — ✅ closed: `kof_sec_jwt_*` in asm (base64url + HMAC
  + iat/exp + exp/iss/aud + constant-time + exceptions via try/catch).
- ~~`passwords.*` in Native~~ — ✅ closed: PBKDF2-HMAC-SHA256 in asm
  (`kof_sec_password_*`, internal HMAC + getrandom).
- ~~SHA-512 asm~~ — ✅ closed: `kof_sec_sha512` in asm (FIPS 180-4, FIPS
  vectors tested; `sha512NativeVectors`).
- ~~AES-GCM in Native~~ — ✅ closed: `kof_sec_aesgcm_encrypt/decrypt` in asm
  (GCM; E2E round-trip `aesGcmNativeRoundTrip`). Remaining: AES-GCM in JS
  (`SECN002`).
- `== null` with String in Native: `kof_string_equals` does not handle null
  (pre-existing backend limitation).
- ~~Incomplete target diagnostics (G7)~~ — ✅ closed: `jwt.*` got an
  explicit entry in `KofSecurity.supportedOn` (Native/JS report
  `SECN004` at compile-time instead of a silent link); `auth.*`/`csrf`/`cors`/
  headers are now restricted to `Target.JVM` in `supportedOn`.
- `SECN008` — `Secret`/`KeyHandle` are JVM-first (D-SECRETS): every other
  target refuses at compile time, never a silent fallback (R6/R7).
- `SECN009` — **warning** (not an error) when a `reveal()` result flows directly
  into `log.*` or `json.encode`; the redaction is enforced at runtime, the
  warning just surfaces the deliberate unmasking. Indirect flows
  (assign to a variable, then log) are a declared limitation of the lint.
- `SECN010` — using a `KeyHandle` after `rotate()` fails at runtime naming the
  revocation (`IllegalStateException`), so a rotated key can never be reused.

- `SECN011` — non-String/Int actual on the plain `crypto` digest names (§563;
  the form degraded per target before the guard).
- `SECN012` — the `keyExchange` face (X25519/HKDF, D-KOF-X25519) on targets
  without the runtime (JS/Native/cross) — compile-time refusal until ported.
- `SECN013` — non-`Byte[]` actual on the binary digest faces
  (`sha256Bytes`/`hmacSha256Bytes`, D-KOF-DIGEST-BYTES).
- `SECN014` — non-`Secret` actual on the session-key face (D-KOF-X25519;
  private material is typed, never raw String).
- `SECN015` — `secrets.secret(name)` with an unset/blank environment variable
  throws an explicit, catchable error (`catch (String e)`) instead of silently
  returning an empty `Secret` (a blank credential is a security failure). The
  legacy `secrets.get(name)` keeps returning the raw `String`/fallback
  (SEC1, `D-MAINT-BATCH-0510`, issue #758).

## 7.6 Bug fixes discovered during the implementation

| Bug | Fix |
|-----|----------|
| `while (longExpr < intLiteral)` generated `LCMP` over [long, int] (stack underflow → Frame.merge crash) | comparison shortcut now widens the operands (`emitComparisonShortcut`) |
| native `crypto.randomInt` returned the division quotient instead of the remainder | `movl %edx, %eax` after `divl` |
