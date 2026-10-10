[English](security.md) | [Português](security.pt_BR.md)

# Kof Standard Library — Security + Enterprise Capability Audit

**Última atualização:** 12 de setembro de 2026
**Versão:** 0.5.0-beta (free-list + riscv64; `kof.http` JVM+JS + retry/circuit)

> Documento arquitetural permanente.
>
> Objetivo: mapear as capacidades do ecossistema Spring, auditar o estado
> atual da Standard Library Kof e definir a arquitetura de `kof.security`
> como fundação enterprise da plataforma.
>
> Regra fundamental: **não copiar Spring, não replicar APIs, não criar
> wrappers.** Construir a solução Kof para os mesmos problemas.

---

# 1. PRINCÍPIO

Uma aplicação Kof deve construir uma aplicação enterprise completa usando a
própria plataforma Kof:

```text
HTTP, REST, auth, autorização, validação, serialização, database,
messaging, observabilidade, testing
```

Spring continua válido como alternativa externa — nunca como dependência
arquitetural.

---

# 2. MATRIZ DO ECOSSISTEMA SPRING

Legenda de status para a Standard Library Kof:

```text
EXISTS           → já implementado na plataforma Kof
PARTIAL          → existe, mas com lacunas conhecidas
MISSING          → não existe, prioridade definida
NOT APPLICABLE   → não se aplica à arquitetura Kof
```

## 2.1 Spring Framework

| Capacidade | Spring | Kof | Status | Notas |
|-----------|--------|-----|--------|-------|
| Core (beans, container) | Core/Beans/Context | — | MISSING (baixa prioridade) | Kof não necessita de container; funções/estado global cobrem a maioria dos casos |
| Dependency Injection | Core/Context | — | MISSING (baixa) | Decisão futura deliberada; ver §3 |
| Expression Language | SpEL | — | MISSING (média) | Pode ser resolvido com funções de primeira classe |
| AOP | AspectJ | — | MISSING (média) | Composição de funções + middleware cobre casos comuns |
| Validation | spring-validation | `kof.validation` | **EXISTS** | 13 predicados JVM/Native/JS |
| Web | spring-web | `kof.web` (`web.app()`) | **EXISTS** | Rotas, params, query, headers, body, middleware `app.use`, `status`/`headerSet` |
| Web MVC | WebMVC | `kof.web` + handlers | PARTIAL | Só JVM hoje; JS embarcado (alpha) |
| WebFlux | WebFlux | `spawn` + virtual threads | PARTIAL | Modelo concorrente próprio (JVM/Native/JS) |
| WebSocket | WebSocket | `kof.web` (`app.ws`) | **EXISTS (JVM)** | RFC 6455: handshake + frame codec com máscara (Native/JS `WEB004` — gate `ExpressionBuiltinInstanceCalls`, 16/09) |
| SSE | (Spring via `SseEmitter`) | `kof.web` (`app.sse`) | **EXISTS (JVM)** | `sse.send/event/close` (JVM + JS handler-scoped ✅ 16/09, `7cd69a7b`; Native `WEB003`, resíduo de push pós-return no JS `WEB003`) |
| Messaging | spring-messaging | `kof.mq` (publish/subscribe/queue) | **EXISTS** | JVM+Native+JS (MQ001 fechado 01/09) |
| Transactions | spring-tx | `kof.db` (`transaction {}`) | **EXISTS** | JVM (JDBC commit/rollback) + Native (SQLite) + JS (não-tipado 16/09) |
| Scheduling | spring-context | `kof.scheduler` (`every`/`cancel` + `spawn`) | PARCIAL | JVM (ScheduledExecutor) + JS (setInterval) + Native (`SCHED001` fechado 31/08); `at(expr)` = cron real de 5 campos em UTC **ou duração idiomática** (`30m`, `90s`, `1d&30m`, `3a&6M...`; unidades `ms/s/m/h/d/M/a`, composição com `&`, clamp civil UTC para `M`/`a`, D-SCHED-DURATION 19/09; paridade JVM + JS, `kof_duration_next_delay_ms`); Native recusa em compile-time (`CRON001`) |
| Events | ApplicationEvent | `kof.mq` pub/sub | PARTIAL | filas pub/sub na stdlib |
| Resources | Resource | `kof.io` | EXISTS | |
| Cache | spring-cache | `kof.cache` (`get/set/set-ttl/ttl/delete/clear`) | **EXISTS** | 3 targets (fix nativo 30/08) |
| Conversion | ConversionService | type system Kof | NOT APPLICABLE | Tipagem estática resolve em compile-time |
| Testing | spring-test | `kof test` + `assert` + JUnit (interno) | PARTIAL | |

## 2.2 Spring Boot

| Capacidade | Spring Boot | Kof | Status | Notas |
|-----------|-------------|-----|--------|-------|
| Lifecycle | run() | `main()` + `web.app().listen()` | **EXISTS** | |
| Configuração | application.yml | `kof.config` (env + arquivo + typed) | **EXISTS** | 3 targets; `kof config gen` |
| Profiles | profiles | `KOF_PROFILE` → `kof.<profile>.config` | **EXISTS** | |
| Dependency management | starters | — | NOT APPLICABLE | Sem dependências: a stdlib É a plataforma |
| Auto configuration | auto-config | — | NOT APPLICABLE | Compilador sabe o que o programa usa |
| Embedded servers | Tomcat/Jetty | `KofHttpServer` | EXISTS | JVM apenas |
| Actuator | actuator | `kof.observability` | **EXISTS** | health/metrics/request IDs JVM/Native/JS |
| Health checks | health | `kof.observability.health` | **EXISTS** | JVM/Native/JS |
| Metrics | micrometer | `kof.observability` | **EXISTS** | counter/increment/gauge JVM/Native/JS |
| Observability | tracing | `kof.observability` | **EXISTS** | health/metrics/histogramas/request IDs + `traceId`/`spanId` W3C e `spanStart`/`spanEnd` cronometrados (3 targets, `OBS002`); export OTel `exportSpans()` OTLP/JSON no JVM/JS (`OBS003`, Native gap honesto) |
| Logging | logback | `kof.log` + `println` | **EXISTS** | `log.debug/info/warn/error` JVM/Native |
| Graceful shutdown | shutdown | `web.close()` + spawn join | PARTIAL | |
| CLI/tooling | spring CLI | `kof` CLI (build/run/serve/test/bench/profile/inspect) | EXISTS | |

## 2.3 Spring Security

| Capacidade | Spring Security | Kof | Status | Prioridade |
|-----------|----------------|-----|--------|-----------|
| Password hashing | BCrypt | `kof.security.passwords` | **EXISTS (esta etapa)** | CRÍTICA |
| Authentication | AuthenticationManager | `kof.security.auth` | **EXISTS (esta etapa)** | CRÍTICA |
| Authorization (roles) | authorize | `kof.security.auth.hasRole` | **EXISTS (esta etapa)** | CRÍTICA |
| Authorities/permissions | authorities | `kof.security.auth.hasPermission` | **EXISTS (esta etapa)** | ALTA |
| Sessions | session management | `kof.security` (`security.sessionCreate/sessionGet/sessionDestroy`) | **EXISTS** | JVM/Native/JS |
| Rate limiting | Bucket4j | `kof.security` (`security.rateLimit`) | **EXISTS** | JVM/Native/JS |
| API keys | ApiKeyFilter | `kof.security` (`security.apiKeyGenerate/apiKeyValid`) | **EXISTS** | JVM/Native/JS |
| Security context | SecurityContext | `kof.security.auth` (contexto de request) | **EXISTS (esta etapa)** | CRÍTICA |
| CSRF | CsrfFilter | `kof.security.security.csrf*` | **EXISTS (esta etapa)** | ALTA |
| CORS | CorsFilter | `kof.security.security.cors*` | **EXISTS (esta etapa)** | ALTA |
| OAuth2 client | OAuth2Client | `kof.security.oauth` (arquitetura) | MISSING | MÉDIA |
| OIDC | OIDC | idem | MISSING | MÉDIA |
| JWT | Nimbus/JJWT | `kof.security.jwt` | **EXISTS (esta etapa)** | CRÍTICA |
| Resource server | Bearer | `kof.security.jwt` + `auth` + `auth.resourceServer` (JWKS RS/ES, camada 16) | **EXISTS (C18/camada 16, JVM)** | ALTA |
| Method security | @PreAuthorize | `app.security({roles: ...})` + `auth.hasRole` | **EXISTS (C18, JVM)** | ALTA |
| Security headers | headers | `app.security()` (composto) + `security.*` (helpers) | **EXISTS (C18, JVM)** | ALTA |
| Request filtering | filter chain | `app.use` + `app.security()` (composto, ordem fixa) | **EXISTS (C18, JVM)** | CRÍTICA |
| Remember-me | remember-me | — | MISSING | BAIXA |
| Logout | logout | — | MISSING | MÉDIA |
| Security events | events | — | MISSING | BAIXA |

## 2.4 Spring Data

| Capacidade | Spring Data | Kof | Status | Prioridade |
|-----------|-------------|-----|--------|-----------|
| Repository abstração | Commons | `kof.orm` (entity + CRUD) | **EXISTS (JVM)** | ALTA |
| JPA | JPA | — | MISSING | MÉDIA (decisão: JDBC/ORM direto é mais Kof) |
| JDBC | JDBC | `kof.db` (`db.execute`/`query<T>`/`transaction`) | **EXISTS** | ALTA |
| R2DBC | R2DBC | — | MISSING | MÉDIA |
| MongoDB | Mongo | `kof.orm` (driver oficial, E2E) | **EXISTS (JVM)** | MÉDIA |
| Redis | Redis | — | MISSING | MÉDIA |
| REST exports | Data REST | `kof.rest` (planejado) | MISSING | MÉDIA |
| Migrations | Flyway/Liquibase | `kof.orm` (`orm.migrate`, `kof_migrations`) | **EXISTS** | ALTA |

## 2.5 Spring Integration / Cloud / Batch / GraphQL / Session / Kafka / AMQP / Pulsar / WS / HATEOAS / REST Docs / Modulith / Authorization Server

| Capacidade | Spring | Kof | Status | Prioridade |
|-----------|--------|-----|--------|-----------|
| Messaging channels | Integration | `kof.mq` (publish/subscribe/queue) | **EXISTS** | MÉDIA |
| Retry/error handling | Integration | `kof.http` (`http.retry`) | **EXISTS (JVM+JS)** | MÉDIA |
| Service discovery | Cloud | — | MISSING | BAIXA (config manual) |
| Gateway | Cloud Gateway | `kof.web` + proxy | PARTIAL | BAIXA |
| Circuit breakers | Resilience | `kof.http` (`http.circuit`) | **EXISTS (JVM+JS)** | BAIXA |
| Distributed tracing | Sleuth | `kof.observability` (`requestId`/`correlationId`, `traceId`/`spanId` W3C) | PARTIAL | BAIXA |
| Batch (jobs/steps/retry) | Batch | `kof.mq` queue + `kof.scheduler` | PARTIAL | MÉDIA |
| GraphQL | GraphQL | — | MISSING | BAIXA (REST primeiro) |
| Distributed sessions | Session | `kof.security` (`sessionCreate/Get/Destroy`) | **EXISTS (JVM/Native/JS)** | BAIXA |
| Kafka producer/consumer | Kafka | `kof.messaging` (planejado) | MISSING | MÉDIA |
| AMQP queues | AMQP | idem | MISSING | MÉDIA |
| Pulsar | Pulsar | idem | MISSING | BAIXA |
| SOAP/XML | WS | — | NOT APPLICABLE | REST/JSON é o idioma Kof |
| Hypermedia | HATEOAS | — | MISSING | BAIXA |
| API docs | REST Docs | `docs/` + training | PARTIAL | MÉDIA |
| Module boundaries | Modulith | packages + type system | PARTIAL | BAIXA |
| OAuth2 authorization server | Auth Server | `kof.security.oauth` (arquitetura) | MISSING | MÉDIA |

---

# 3. DECISÕES ARQUITETURAIS

## 3.1 DI / container

Kof não possui container de beans. Funções de primeira classe, lambdas e
estado de módulo cobrem a composição. Um contêiner DI seria cerimônia sem
ganho de intenção. **Decisão: NOT APPLICABLE para o núcleo; reavaliar apenas
se um padrão de necessidade surgir (plugins/SPI).**

## 3.2 Database

`kof.database` será SQL-first (JDBC idiomático), sem ORM pesado. Records do
Kof + SQL explícito cobrem a maioria das aplicações com menos camadas.
JPA-style ORM é decisão futura, não pressuposto.

## 3.3 Observabilidade

`kof.metrics`/`kof.observability` seguirão o padrão das métricas já
existentes no tooling (`kof bench`, `kof profile`): coletar no runtime,
expor de forma simples, integrar com JFR/perf/V8 quando existir.

## 3.4 Security

`kof.security` é implementado como **namespaces compilados** (mesmo padrão
de `kof.io`/`kof.web`): a intenção é expressa diretamente, o compilador
resolve a função de runtime e cada target fornece a implementação.

---

# 4. AUDITORIA DA STANDARD LIBRARY KOF (estado real)

| Módulo | Existe? | Idiomático? | Seguro? | Multi-target | Performático? | Testes | Docs |
|--------|---------|-------------|---------|--------------|---------------|--------|------|
| `kof.core` (println, aritmética, strings, arrays) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.collections` (List, listOf) | SIM | SIM | SIM (bounds) | JVM/Native/JS | PARCIAL (boxing List<Int> no JVM) | SIM | SIM |
| `kof.io` (File/Path/Directory, readFile) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.time` (`now()`) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.json` (encode/decode) | SIM | SIM | SIM (FP/arrays completos no Native 31/08) | JVM/Native/JS | SIM | SIM | SIM |
| `kof.http` (serve + client) | SIM | SIM | PARCIAL (auth via middleware) | JVM (serve); JVM+JS (client) | SIM | SIM | SIM |
| `kof.web` (web.app, rotas, ws/sse) | SIM | SIM | PARCIAL (auth em construção) | JVM | SIM | SIM (novo) | SIM |
| `kof.rest` | NÃO | — | — | — | — | — | — |
| `kof.database` (`kof.db` + `kof.orm`) | SIM | SIM | SIM (bind tipado; SQL explícito) | JVM + Native (SQLite; MySQL wire x86-64 real, F2d1–F2d7) | SIM | SIM | SIM |
| `kof.security` | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.concurrent` (spawn/await) | SIM | SIM | SIM | JVM/Native (pthread)/JS | SIM | SIM | SIM |
| `kof.messaging` (`kof.mq`) | SIM | SIM | PARCIAL (in-memory) | JVM+JS | SIM | SIM | SIM |
| `kof.validation` | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.serialization` | PARCIAL (json) | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.logging` | SIM (`log.debug/info/warn/error`) | SIM | SIM | JVM/Native | SIM | SIM | SIM |
| `kof.observability` | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.metrics` | SIM (`kof.observability`) | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.config` | SIM | SIM | SIM (secrets separados) | JVM/Native/JS | SIM | SIM | SIM |
| `kof.cache` | SIM | SIM | PARCIAL (in-memory) | JVM/Native/JS | SIM | SIM | SIM |
| `kof.test` (`kof test`, assert) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.cli` (CLI kof) | SIM | SIM | SIM | JVM | SIM | SIM | SIM |
| `kof.process` | SIM | SIM | SIM | JVM/JS (Native: `PROC001`) | SIM | SIM | SIM |
| `kof.crypto` | NÃO (faz parte de kof.security) | — | — | — | — | — | — |
| `kof.ui` (plataforma de UI) | PARCIAL | PARCIAL | — | JS | — | PARCIAL | PARCIAL |

## 4.1 Estado da segurança na plataforma hoje

> **Linha de base (pré `kof.security` v1)** — snapshot do que faltava ANTES
> da implementação. O estado atual (implementado, 3 targets) está em §7.

| Área | Estado atual (baseline) |
|------|--------------|
| Hash de senha | INEXISTENTE (programador usaria sha256 — proibido por design) |
| Constante de tempo | INEXISTENTE |
| Random seguro | INEXISTENTE (sem API de random) |
| JWT | INEXISTENTE |
| Headers/CSRF/CORS | INEXISTENTE |
| Segredos (env) | `secrets.get(name[,fallback])` (`String` cru) + `secrets.secret(name)` → `Secret` |
| Auth em HTTP | PARCIAL: `header("x-auth")` manual no middleware |
| Secrets em logs | PROTEGIDO no JVM pelo tipo `Secret` (D-SECRETS, Estágio 5/3.6): imprime `Secret(*** )`, o texto cru só por `reveal()`, `json.encode(secret)` redigido em runtime (P2) e aviso `SECN009` quando `reveal()` alimenta `log.*`/`json.encode`. JS/Native/Script = gap honesto `SECN008` (Android roda `kof.security` desde §278, 23/09) |
| Material de chave | `KeyHandle` (D-SECRETS P3) nunca expõe bytes crus: `secrets.keyFromHex/keyFromPem/keyFromKeystore(...)`, `rotate()` revoga o handle antigo (uso posterior `SECN010`); JS/Native/Script = gap honesto `SECN008` (Android roda `kof.security` desde §278, 23/09) |

---

# 5. ARQUITETURA `kof.security` (definição)

Namespaces de intenção (compilados, mesmo padrão de `kof.io`/`kof.web`):

```text
kof.security
├── passwords        → hash/verify/needsRehash (PBKDF2-HMAC-SHA256, secure by default)
├── crypto           → sha256/sha512, sha256Bytes, hmacSha256/hmacSha256Bytes, aesGcm (encrypt/decrypt), randomHex/randomInt
├── jwt              → create/verify (HS256, exp/iss/aud, sem confusão de algoritmo)
├── secrets          → get (env, String cru), redact, of/secret/fromBytes (→ tipo valor Secret),
│                      keyFromHex/keyFromPem/keyFromKeystore (→ KeyHandle, chave crua nunca exposta)
├── security         → constantTimeEquals, randomHex, redact, csrfToken/csrfValid, corsAllowed, headers helpers,
│                      rateLimit, sessionCreate/sessionGet/sessionDestroy, apiKeyGenerate/apiKeyValid (G9),
│                      cookieSet/cookieGet (C11, defaults seguros)
└── auth             → contexto web: secret, token, authenticated, claims, user, hasRole, hasPermission,
                       resourceServer/resourceServerVerify (camada 16: JWKS RS/ES)
```

Suporte por target (estado atual — `KofSecurity.supportedOn`):

| Função | JVM | Native | JS |
|--------|-----|--------|----|
| `passwords.hash/verify/needsRehash` | SIM (javax.crypto PBKDF2) | SIM (asm PBKDF2-HMAC-SHA256) | SIM (PBKDF2 platform-delegated) |
| `crypto.sha256/sha512` | SIM | SIM (asm, FIPS 180-4) | SIM (JS) |
| `crypto.hmacSha256` | SIM | SIM (asm) | SIM (JS) |
| `keyExchange.privateKey/publicKey/shared/hkdfSha256` (D-KOF-X25519) | SIM (JCA X25519/HKDF) | gap SECN012 (port asm na fila) | gap SECN012 (a ponte de host não carrega escalares Secret) |
| `crypto.sha256Bytes` | SIM | SIM (alias asm de layout-compatível de sha256) | gap SECN000 (D-KOF-DIGEST-BYTES, 02/10) |
| `crypto.hmacSha256Bytes` | SIM | SIM (alias asm de hmacSha256) | gap SECN000 |
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
| `auth.resourceServer/resourceServerVerify` (camada 16) | SIM (JVM) | — (SECN007) | — (SECN007) |

Gaps reais com diagnóstico em compile-time: `SECN001` (passwords),
`SECN003` (sha512), `SECN005` (G9) e `SECN006` (cookies no Native) — nunca
comportamento silenciosamente diferente. `SECN002` (AES-GCM no JS) e
`SECN004` (jwt) fechados.

**Regra**: qualquer gap emite diagnóstico claro em compile-time (ex.
`SECN001: passwords.hash não está disponível no target Native ainda`).
Nunca comportamento silenciosamente diferente (§16 do doc de performance).

## 5.1 Formatos (versionados, sem ambigüidade)

```text
passwords:   pbkdf2$sha256$<iterations>$<salt-b64>$<hash-b64>
crypto:      aesgcm$<iv-b64>$<ciphertext+tag-b64>
             chacha20$<nonce-b64>$<ciphertext+tag-b64>   (14/09, D-SEC)
jwt:         RFC 7519 HS256 (alg fixado, nunca aceito do token)
```

---

# 6. PRÓXIMAS ETAPAS

1. Implementar `kof.security` (KofSecurity.java + runtimes JVM/Native/JS).
2. Testes unitários + E2E + adversariais (`KofSecurityTest`).
3. Benchmarks (`benchmarks/security/`).
4. Documentação (`docs/stdlib/security.md`, `learn/`, `training/`).
5. Auditoria contínua: `kof.config`, `kof.database`, `kof.messaging`,
   `kof.validation`, `kof.logging`, `kof.observability` (próximas etapas).

---

# 7. ESTADO DA IMPLEMENTAÇÃO (0.5.0-beta, re-synced 17/09/2026 — `VERSION` 0.5.0-beta, 2218 testes, free-list + mark-sweep + riscv64)

## 7.1 Implementado (0.5.0-beta)

| API | JVM | Native x86_64 (+ riscv64) | JS | Formato |
|-----|-----|---------------------------|----|---------|
| `passwords.hash(password)` | ✅ PBKDF2-HMAC-SHA256 600k | ✅ (asm: HMAC interno + b64 + getrandom, free-list 27/08) | ✅ PBKDF2 (platform-delegated) | `pbkdf2$sha256$600000$salt$hash` |
| `passwords.verify(password, hash)` | ✅ constant-time | ✅ (asm, constant-time) | ✅ | |
| `passwords.needsRehash(hash)` | ✅ | ✅ | ✅ | |
| `crypto.sha256(data)` | ✅ | ✅ (asm FIPS 180-4, riscv64 `li a7`) | ✅ (JS puro) | hex |
| `crypto.sha512(data)` | ✅ | ✅ (asm FIPS 180-4, vetores FIPS testados) | ✅ (JS puro) | hex |
| `crypto.hmacSha256(key, data)` | ✅ | ✅ (asm) | ✅ (JS puro) | hex |
| `crypto.encryptAesGcm(plain, keyHex)` | ✅ AES/GCM/NoPadding | ✅ (asm GCM, round-trip E2E) | ✅ (JS puro, round-trip E2E) | `aesgcm$iv$ct` |
| `crypto.decryptAesGcm(ct, keyHex)` | ✅ (falha em tamper) | ✅ (asm, falha em tamper) | ✅ (JS puro, falha em tamper) | |
| `crypto.encryptChacha20(plain, keyHex)` | ✅ (RFC 8439, **interop com o JDK** validado 14/09) | SECN002 honesto (asm 130-bit na fila) | ✅ (JS puro, interop com o JDK validado) | `chacha20$nonce$ct` |
| `crypto.decryptChacha20(ct, keyHex)` | ✅ (tag constant-time, falha em tamper) | SECN002 honesto | ✅ (JS puro, tag constant-time) | |
| `crypto.randomHex(n)` | ✅ SecureRandom | ✅ getrandom (`li a7 318` x86_64 / `214` riscv64) | ✅ platform | hex |
| `crypto.randomInt(bound)` | ✅ | ✅ getrandom + rejection | ✅ platform | |
| `jwt.create(claims, secret[, ttl])` | ✅ HS256 + iat/exp | ✅ (asm: base64url + HMAC + kof_now) | ✅ | RFC 7519 HS256 |
| `jwt.verify(token, secret[, iss, aud])` | ✅ (sig, exp, iss, aud) | ✅ (asm, constant-time + exp/iss/aud) | ✅ | alg fixado HS256 |
| `jwt.secret()` | ✅ env `KOF_JWT_SECRET` ou gerado | ✅ (`/proc/self/environ`) | ✅ | 32 bytes hex |
| `secrets.get(name[, fallback])` | ✅ env | ✅ `/proc/self/environ` | ✅ platform | |
| `secrets.redact(value)` | ✅ | ✅ (asm) | ✅ | `abcd********wxyz` |
| `secrets.of(text)` / `secrets.secret(name)` | ✅ (→ `Secret`; env não definida → erro `SECN015`) | ❌ `SECN008` | ❌ `SECN008` | D-SECRETS face 1 |
| `secrets.fromBytes(bytes)` | ✅ (→ `Secret`, byte a byte Latin-1) | ❌ `SECN008` | ❌ `SECN008` | sem perda para bytes não-texto |
| `Secret.reveal()` / `.redacted()` | ✅ | ❌ `SECN008` | ❌ `SECN008` | imprime `Secret(*** )`; `reveal()` é o único export cru |
| `secrets.keyFromHex/keyFromPem/keyFromKeystore(...)` | ✅ (→ `KeyHandle`) | ❌ `SECN008` | ❌ `SECN008` | P3; bytes crus da chave nunca expostos |
| `KeyHandle.rotate()` | ✅ | ❌ `SECN008` | ❌ `SECN008` | revoga o handle antigo; uso posterior falha `SECN010` |
| `crypto.hmacSha256(KeyHandle, msg)` / `aesGcm` / `chacha20` com `KeyHandle` | ✅ | ❌ `SECN008` | ❌ `SECN008` | sobrecargas P3 |
| `jwt.create/verify(..., KeyHandle)` | ✅ | ❌ `SECN008` | ❌ `SECN008` | sobrecargas P3 |
| `json.encode(Secret)` | ✅ redigido `"Secret(*** )"` | n/a | n/a | P2: nunca despeja os campos de um segredo |
| `security.constantTimeEquals(a, b)` | ✅ `MessageDigest.isEqual` | ✅ (asm) | ✅ | |
| `security.randomHex` / `randomInt` | ✅ | ✅ | ✅ | |
| `security.csrfToken/csrfValid` | ✅ (session-scoped) | ❌ | ❌ | |
| `security.corsAllowed(origin, allowed)` | ✅ | ❌ | ❌ | |
| `security.cspHeader/hstsHeader/contentTypeOptionsHeader/frameHeader/referrerHeader` | ✅ (valores prontos) | ❌ | ❌ | |
| `auth.secret/token/authenticated/claims/user/hasRole/hasPermission` | ✅ (contexto web, Bearer JWT) | ❌ | ❌ | |
| `security.rateLimit(key, limit, window)` | ✅ fixed-window per-key | ✅ (asm, per-key counter, free-list) | ✅ (JS, Date.now) | per-key count |
| `security.sessionCreate(data)` / `sessionGet` / `sessionDestroy` | ✅ (ConcurrentHashMap) | ✅ (asm, 32 slots) | ✅ (JS object) | randomHex(16) id |
| `security.apiKeyGenerate` / `apiKeyValid` | ✅ (ConcurrentHashMap) | ✅ (asm, 32 slots) | ✅ (JS object) | randomHex(32) |

## 7.2 Verificação cruzada

Os valores de SHA-256/HMAC são idênticos entre JVM, Native e JS e batem com
vetores de referência (FIPS 180-4, RFC 2104) — verificado por
`KofSecurityTest` nos três targets.

## 7.3 Testes

`KofSecurityTest` (25 testes): hashing/verificação, senha errada, rehash,
SHA-256/512 vetores, HMAC, constant-time, random, JWT (assinatura, expiração,
issuer/audience, token malformado, confusão de algoritmo, claims não-objeto),
AES-GCM (round trip JVM+Native, tamper, chave errada), secrets/redact, e
diagnostics de target gap (SECN001/002/003). Casos adversariais incluídos (§18).

## 7.4 Benchmarks

`benchmarks/security/`: `password-hash`, `jwt`, `hash-speed`, `aes-gcm`
(jvm/js conforme suporte).

## 7.5 Gaps documentados

- ~~JWT no Native~~ — ✅ fechado: `kof_sec_jwt_*` em asm (base64url + HMAC
  + iat/exp + exp/iss/aud + constant-time + exceções via try/catch).
- ~~`passwords.*` no Native~~ — ✅ fechado: PBKDF2-HMAC-SHA256 em asm
  (`kof_sec_password_*`, HMAC interno + getrandom).
- ~~SHA-512 asm~~ — ✅ fechado: `kof_sec_sha512` em asm (FIPS 180-4, vetores
  FIPS testados; `sha512NativeVectors`).
- ~~AES-GCM no Native~~ — ✅ fechado: `kof_sec_aesgcm_encrypt/decrypt` em asm
  (GCM; round-trip E2E `aesGcmNativeRoundTrip`). Restante: AES-GCM no JS
  (`SECN002`).
- `== null` com String no Native: `kof_string_equals` não trata null
  (limitação pré-existente do backend).
- ~~Diagnósticos de target incompletos (G7)~~ — ✅ fechado: `jwt.*` ganhou
  entrada explícita em `KofSecurity.supportedOn` (Native/JS reportam
  `SECN004` em compile-time em vez de link silencioso); `auth.*`/`csrf`/`cors`/
  headers agora são restritos a `Target.JVM` em `supportedOn`.
- `SECN008` — `Secret`/`KeyHandle` são JVM-primeiro (D-SECRETS): todo outro
  target recusa em compile-time, nunca fallback silencioso (R6/R7).
- `SECN009` — **aviso** (não erro) quando um resultado de `reveal()` flui direto
  para `log.*` ou `json.encode`; a redação é forçada em runtime, o aviso só
  expõe o desmascaramento deliberado. Fluxos indiretos (atribuir a uma variável
  e depois logar) são limitação declarada do lint.
- `SECN010` — usar um `KeyHandle` após `rotate()` falha em runtime nomeando a
  revogação (`IllegalStateException`), então uma chave rotacionada nunca é reusada.

- `SECN011` — argumento não-String/Int nos nomes de digest simples (§563; a
  forma degradava por alvo antes do guard).
- `SECN012` — a face `keyExchange` (X25519/HKDF, D-KOF-X25519) em alvos sem
  runtime (JS/Native/cross) — recusa no compile até o port.
- `SECN013` — argumento não-`Byte[]` nas faces de digest binário
  (`sha256Bytes`/`hmacSha256Bytes`, D-KOF-DIGEST-BYTES).
- `SECN014` — argumento não-`Secret` na face de chave de sessão (D-KOF-X25519;
  material privado é tipado, nunca String crua).
- `SECN015` — `secrets.secret(name)` com variável de ambiente não definida/em
  branco lança erro explícito e catchável (`catch (String e)`) em vez de devolver
  um `Secret` vazio em silêncio (credencial em branco é falha de segurança). O
  legado `secrets.get(name)` segue devolvendo o `String` cru/fallback
  (SEC1, `D-MAINT-BATCH-0510`, issue #758).

## 7.6 Correções de bugs descobertas durante a implementação

| Bug | Correção |
|-----|----------|
| `while (longExpr < intLiteral)` gerava `LCMP` sobre [long, int] (stack underflow → Frame.merge crash) | shortcut de comparação agora faz widening dos operandos (`emitComparisonShortcut`) |
| `crypto.randomInt` nativo retornava o quociente da divisão em vez do resto | `movl %edx, %eax` após `divl` |