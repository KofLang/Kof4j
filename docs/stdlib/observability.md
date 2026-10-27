[English](observability.md) | [Português](observability.pt_BR.md)

# kof.observability — Health, Metrics and Request IDs (G5)

**Last updated:** September 17, 2026
**Version:** 0.6.7 (`VERSION` 0.6.7)

> **Status:** DONE (JVM/Native/JS) — `KofObservabilityTest` 12/12 (VERSION 0.6.0, free-list Native); API confirmed in `KofObservability.java`; histograms/spans/Prometheus export closed as `OBS002` (16/09); **OpenTelemetry export (`exportSpans`) closed as `OBS003` — JVM/JS ✅, Native honest gap**
> **Module:** `kof.observability` — `observability.*`
> **Targets:** JVM ✅ · Native x86_64 ✅ (free-list) · Native riscv64 ✅ · JS ✅ — G5 closed 0.2.6-beta; OBS002 16/09; **`OBS003`: `exportSpans()` is JVM/JS only (Native refuses at compile time — never a stub)**

---

## 1. Motivation

Observing production requires three minimal primitives: **knowing whether the service is healthy** (health/readiness/liveness), **counting/measuring what happens** (metrics) and **tracing a request end-to-end** (request/correlation IDs). `kof.log` already covers structured logging (JVM/Native); `kof.observability` closes the P0 cycle by exposing these three families on the three backends with the same API.

Principle kept: *intent → Kof → stdlib → runtime/backend → platform* — no external framework, no agent, no mandatory sidecar. When the platform needs Prometheus/OpenTelemetry, it consumes the primitives of `kof.observability`.

---

## 2. API

| Call | Kof signature | Return | Description |
|---------|----------------|---------|-----------|
| `observability.health()` | `() -> String` | `"UP"` | Aggregate health — compatible with Spring Boot Actuator `/health` |
| `observability.readiness()` | `() -> Bool` | `true` | Ready to receive traffic |
| `observability.liveness()` | `() -> Bool` | `true` | Process alive (no restart needed) |
| `observability.counter(name)` | `(String) -> Int` | new value | Increments the named counter by 1 |
| `observability.increment(name, delta)` | `(String, Int) -> Int` | new value | Increments the counter by `delta` |
| `observability.gauge(name, value)` | `(String, Int) -> Void` | — | Sets the named gauge |
| `observability.histogram(name, value)` | `(String, Int) -> Void` | — | Records `value` into the named histogram (sum + count) |
| `observability.metrics()` | `() -> String` | Prometheus text | Exports counters/gauges/histograms in Prometheus text exposition format |
 | `observability.requestId()` | `() -> String` | UUID/hex | Generates a request ID (16 random bytes → 32 hex) |
 | `observability.correlationId()` | `() -> String` | UUID/hex | Alias of `requestId()` — for propagation between services |
 | `observability.traceId()` | `() -> String` | 32 hex | Trace ID (W3C Trace Context) — 16 random bytes |
 | `observability.spanId()` | `() -> String` | 16 hex | Span ID (W3C Trace Context) — 8 random bytes |
 | `observability.spanStart(name)` | `(String) -> String` | handle | Starts a timed W3C span; returns an opaque handle (`traceId+spanId`) |
 | `observability.spanEnd(handle)` | `(String) -> String` | JSON | Ends the span; returns `{traceId,spanId,parentSpanId,name,startMicros,endMicros,durationMicros}` (`startMicros`/`endMicros` are **epoch** micros) |
 | `observability.exportSpans()` | `() -> String` | OTLP/JSON | Exports the completed spans as an **OpenTelemetry OTLP/JSON** payload (`resourceSpans`) — **JVM ✅ / JS ✅**, Native `OBS003` |

All calls are **available on the three targets** (JVM/Native/JS) — `supportedOn` always returns `true`; there is no `OBS001` in normal use. Histograms, timed spans and the Prometheus text export are implemented (`OBS002` closed 16/09); any future gap will report `OBS00x`. The **only** exception is `exportSpans()`: OTLP/JSON serialization is **JVM-first** (R7) — on Native the call is rejected at compile time with **`OBS003`** (honest refusal, never a silent stub). `spanStart(name)` **keeps the span name** on JVM/JS (it used to be dropped and rendered as `"span"`).

### Example

```kof
main() {
    // health
    assert(observability.health() == "UP")
    assert(observability.readiness())
    assert(observability.liveness())

    // metrics
    val c1 = observability.counter("http.requests")
    val c2 = observability.counter("http.requests") // 2
    val c3 = observability.increment("http.requests", 10) // 12
    observability.gauge("cpu.load", 42)
    observability.histogram("http.latency", 12) // sum=12, count=1

    // request tracking
    val req = observability.requestId()      // "a3f1c9e2b4d64a8f9c0e1d2f3a4b5c6d"
    val corr = observability.correlationId() // another ID, propagatable in a header
    println(req + " " + corr)

    // tracing (W3C Trace Context) — pure IDs, no store, 3 targets
    val trace = observability.traceId() // 32 hex
    val span  = observability.spanId()  // 16 hex
    println(trace + "-" + span) // e.g.: header traceparent: 00-<trace>-<span>-01

    // timed span (OBS002) + Prometheus export
    val h = observability.spanStart("db.query")
    val json = observability.spanEnd(h)   // {"traceId":..,"name":"db.query",..}
    println(json)
    println(observability.metrics())      // # TYPE .. / name value

    // OpenTelemetry export (OBS003) — the OTLP/JSON payload a collector
    // consumes at /v1/traces. JVM/JS; on Native it is a compile-time OBS003.
    println(observability.exportSpans())
}
```

---

## 3. Semantics per target

### JVM

- **Health/readiness/liveness:** constants (`"UP"` / `true`) — ready for future customization (e.g., checking `kof.db`).
- **Metrics:** `ConcurrentHashMap<String, AtomicInteger>` for counters, `ConcurrentHashMap<String, Integer>` for gauges, `ConcurrentHashMap<String, long[]>` (`[sum, count]`, synchronized) for histograms — thread-safe, no persistence (process memory, like Micrometer `simple`). `metrics()` renders them in Prometheus text exposition (`name_count`/`name_sum`).
- **Spans:** `spanStart(name)` stores the name + `System.nanoTime()` (duration) + `System.currentTimeMillis()*1000` (epoch micros) under the `traceId+spanId` handle; `spanEnd` returns the span JSON with the **real name** and `durationMicros`. Each completed span is pushed into a bounded ring (max. 256).
- **OTel export (`OBS003`):** `exportSpans()` renders the ring as OTLP/JSON (`resourceSpans` → `scopeSpans` → `spans`), `kind` = 1 (INTERNAL), times in unix-epoch nanoseconds as strings, resource attribute `service.name=kof`, scope `kof.observability`. Non-destructive; names are JSON-escaped.
- **Request IDs:** `UUID.randomUUID().toString()` (36 chars with hyphens, variant 4).

### Native (asm x86-64, without libc)

- **Health:** allocates `KofString` "UP" via `kof_string_from_literal` (`.Lstr_obs_up`).
- **Readiness/liveness:** `mov $1, %eax; ret`.
- **Metrics:** `.bss` with 32 slots (`512` bytes) for counters and gauges — each slot `16` bytes (`ptr` + `int` + pad). Linear search with content comparison (`length` at `16(%rdi)` + bytes at `24(%rdi)`); `counter`/`increment` increment, `gauge` overwrites. No persistence; silent overflow after 32 distinct names (returns `0`).
- **Histograms/spans/metrics (`OBS002`):** `.bss` slots extended for histograms (sum+count) and spans (start nanos); `metrics()` builds the Prometheus text in asm.
- **OTel export:** **not implemented** — `exportSpans()` is rejected at compile time with `OBS003` (R7: JVM-first; never a stub). The Native span path itself matches the JVM golden on x86_64/riscv64/aarch64 since 18/09 (§272 faces (b)+(c) closed; the `kof_sec_random_hex` overflow found on the port was closed as §292).
- **Request IDs:** tail-call to `kof_sec_random_hex(16)` — `getrandom(2)` → `32` hex chars (without hyphens, `318` syscall), same entropy as `kof.security`.

### JS (kof-runtime.mjs)

- **Health/readiness/liveness:** `"UP"` / `1`.
- **Metrics:** objects `__kofObsCounters` / `__kofObsGauges` / `__kofObsHistograms` (`{sum,count}`) in a closure — `counter`/`increment`/`gauge`/`histogram` manipulate the JS dictionary; `metrics()` renders Prometheus text.
- **Spans:** `__kofObsSpans` `Map` keyed by handle — `spanStart` records the name + `Date.now()*1000` (epoch micros), `spanEnd` returns the span JSON with the **real name**. Each completed span is pushed into a bounded array (max. 256).
- **OTel export (`OBS003`):** `exportSpans()` builds the same OTLP/JSON payload as the JVM via `JSON.stringify` (`resourceSpans`/`scopeSpans`/`spans`, `kind` = 1, `startTimeUnixNano`/`endTimeUnixNano` as strings).
- **Request IDs:** `crypto.randomUUID()` when available, fallback `Math.random` with the format `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`.

---

## 4. Tests

`kof-compiler/src/test/java/dev/kof/compiler/KofObservabilityTest.java` — 12 tests (JVM/Native x86+riscv64+aarch64/JS), 12/12:

- `observabilityJvm` / `observabilityNative` / `observabilityJs` — health/readiness/liveness, sequential counter, gauge, `histogram` + `metrics()` (Prometheus `_count`/`_sum`), `requestId`/`correlationId`.
- `tracingJvmNativeJs` — W3C `traceId` (32 hex) / `spanId` (16 hex) on the three targets.
- `spansWithTiming` — `spanStart`/`spanEnd` JSON with the **real name** (`name":"op"`) on JVM/JS.
- `otlpExportJvm` — `exportSpans()` OTLP/JSON: `resourceSpans`, `service.name`, `scope`, `kind`, `startTimeUnixNano`/`endTimeUnixNano`; **edges**: empty ring (`"spans":[]`) and a name with `"` escaped.
- `otlpExportJs` — same export on JS (parity with the JVM payload).
- `otlpExportNativeIsHonestGap` — Native compile is **rejected with `OBS003`** (R6/R7, no stub).
- `applicationLifecycle` / `applicationLifecycleEmptyBlocks` — `application { onStart/onShutdown }` hooks.

All tests pass with `KOF_KEEP_ASM=1` preserving `Main.s` for inspection.

---

## 5. Integration with the ecosystem

```
kof.config ──► kof.observability (logging/metrics config)
kof.web ──► kof.observability (request IDs, per-route metrics)
kof.security ──► kof.observability (future audit logging)
kof.observability ──► kof.bench/profile (existing tooling)
```

Next steps (outside P0-G5): an HTTP `/metrics` route that serves `observability.metrics()`, sending the `exportSpans()` payload to a collector over OTLP/HTTP (a thin `kof.http` POST — no sidecar), closing `OBS003` on Native, and customizable health with `kof.db`/`kof.mq` checks.

---

## 6. Definition of Done (G5)

- ✅ Idiomatic API (`observability.*`) + type safety (compile-time dispatch)
- ✅ JVM/Native/JS targets (no gaps, `supportedOn` = true) — **except** `exportSpans()` (`OBS003`, JVM/JS; Native honest gap)
- ✅ Tests `KofObservabilityTest` 12/12 + `KofSecurityTest` 25/25 + `KofValidationTest` 3/3 without regression
- ✅ Benchmark not applicable (O(1) operations / `getrandom` syscall)
- ✅ Security review: `requestId` uses `SecureRandom` (JVM) / `getrandom` (Native) / `crypto.randomUUID` (JS) — no leakage
- ✅ OTel export: OTLP/JSON `resourceSpans` payload (JVM/JS), golden measured on the JVM oracle + GraalJS; Native `OBS003` documented in `backend-parity.md`
- ✅ Docs: this file + `docs/bugs-and-gaps/ecosystem-coverage.md` §3.9/§4/§7 + `docs/stdlib/stdlib.md` §3
- ✅ Real example: snippet above runs on the three targets (with `exportSpans()` only on JVM/JS)
