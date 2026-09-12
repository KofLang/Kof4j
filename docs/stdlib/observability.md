# kof.observability — Health, Metrics e Request IDs (G5)

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; `VERSION` 0.2.6-beta)

> **Status:** DONE (JVM/Native/JS) — `KofObservabilityTest` 3/3 (0.2.6-beta, free-list Native); API confirmada em `KofObservability.java`
> **Módulo:** `kof.observability` — `observability.*`
> **Targets:** JVM ✅ · Native x86_64 ✅ (free-list) · Native riscv64 ✅ · JS ✅ — sem gaps (G5 fechado, 0.2.6-beta)

---

## 1. Motivação

Observar produção exige três primitivas mínimas: **saber se o serviço está saudável** (health/readiness/liveness), **contar/medir o que acontece** (metrics) e **rastrear uma requisição fim-a-fim** (request/correlation IDs). O `kof.log` já cobre logging estruturado (JVM/Native); o `kof.observability` fecha o ciclo P0 ao expor essas três famílias nos três backends com a mesma API.

Princípio mantido: *intenção → Kof → stdlib → runtime/backend → plataforma* — sem framework externo, sem agente, sem sidecar obrigatório. Quando a plataforma precisa de Prometheus/OpenTelemetry, ela consome as primitivas do `kof.observability`.

---

## 2. API

| Chamada | Assinatura Kof | Retorno | Descrição |
|---------|----------------|---------|-----------|
| `observability.health()` | `() -> String` | `"UP"` | Health agregado — compatível com Spring Boot Actuator `/health` |
| `observability.readiness()` | `() -> Bool` | `true` | Pronto para receber tráfego |
| `observability.liveness()` | `() -> Bool` | `true` | Processo vivo (não precisa restart) |
| `observability.counter(name)` | `(String) -> Int` | novo valor | Incrementa contador nomeado em 1 |
| `observability.increment(name, delta)` | `(String, Int) -> Int` | novo valor | Incrementa contador em `delta` |
| `observability.gauge(name, value)` | `(String, Int) -> Void` | — | Define gauge nomeado |
 | `observability.requestId()` | `() -> String` | UUID/hex | Gera ID de requisição (16 bytes aleatórios → 32 hex) |
 | `observability.correlationId()` | `() -> String` | UUID/hex | Alias de `requestId()` — para propagação entre serviços |
 | `observability.traceId()` | `() -> String` | 32 hex | ID de trace (W3C Trace Context) — 16 bytes aleatórios |
 | `observability.spanId()` | `() -> String` | 16 hex | ID de span (W3C Trace Context) — 8 bytes aleatórios |

Todas as chamadas são **disponíveis nos três targets** (JVM/Native/JS) — `supportedOn` retorna `true` sempre; não há `OBS001` em uso normal. Gaps futuros (ex.: export Prometheus) reportarão `OBS00x`.

### Exemplo

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

    // request tracking
    val req = observability.requestId()      // "a3f1c9e2b4d64a8f9c0e1d2f3a4b5c6d"
    val corr = observability.correlationId() // outro ID, propagável em header
    println(req + " " + corr)

    // tracing (W3C Trace Context) — IDs puros, sem store, 3 targets
    val trace = observability.traceId() // 32 hex
    val span  = observability.spanId()  // 16 hex
    println(trace + "-" + span) // ex.: header traceparent: 00-<trace>-<span>-01
}
```

---

## 3. Semântica por target

### JVM

- **Health/readiness/liveness:** constantes (`"UP"` / `true`) — prontas para customização futura (ex.: checar `kof.db`).
- **Metrics:** `ConcurrentHashMap<String, AtomicInteger>` para counters, `ConcurrentHashMap<String, Integer>` para gauges — thread-safe, sem persistência (memória do processo, como Micrometer `simple`).
- **Request IDs:** `UUID.randomUUID().toString()` (36 chars com hífens, variante 4).

### Native (asm x86-64, sem libc)

- **Health:** aloca `KofString` "UP" via `kof_string_from_literal` (`.Lstr_obs_up`).
- **Readiness/liveness:** `mov $1, %eax; ret`.
- **Metrics:** `.bss` com 32 slots (`512` bytes) para counters e gauges — cada slot `16` bytes (`ptr` + `int` + pad). Busca linear com comparação de conteúdo (`length` em `16(%rdi)` + bytes em `24(%rdi)`); `counter`/`increment` incrementam, `gauge` sobrescreve. Sem persistência; overflow silencioso após 32 nomes distintos (retorna `0`).
- **Request IDs:** tail-call para `kof_sec_random_hex(16)` — `getrandom(2)` → `32` hex chars (sem hífens, `318` syscall), mesma entropia do `kof.security`.

### JS (kof-runtime.mjs)

- **Health/readiness/liveness:** `"UP"` / `1`.
- **Metrics:** objetos `__kofObsCounters` / `__kofObsGauges` em closure — `counter`/`increment`/`gauge` manipulam o dicionário JS.
- **Request IDs:** `crypto.randomUUID()` quando disponível, fallback `Math.random` com formato `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`.

---

## 4. Testes

`kof-compiler/src/test/java/dev/kof/compiler/KofObservabilityTest.java` — 3 testes (JVM/Native/JS):

- `observabilityJvm` — health/readiness/liveness, counter sequencial (`1→2→5`), gauge, `requestId`/`correlationId` não vazios e distintos.
- `observabilityNative` — mesmo cenário em assembly (verifica `health() == "UP"` e incremento `1→2→7`).
- `observabilityJs` — health `"UP"`, readiness/liveness `true`, counter `1→2→12`, requestIds com `length > 0`.

Todos os testes passam com `KOF_KEEP_ASM=1` preservando `Main.s` para inspeção.

---

## 5. Integração com o ecossistema

```
kof.config ──► kof.observability (config de logging/metrics)
kof.web ──► kof.observability (request IDs, metrics por rota)
kof.security ──► kof.observability (audit logging futuro)
kof.observability ──► kof.bench/profile (tooling já existente)
```

Próximos passos (fora do P0-G5): export Prometheus (`/metrics`), `tracing`/`OpenTelemetry` (spans), `kof.observability.metrics()` dump JSON, health customizável com checks de `kof.db`/`kof.mq`.

---

## 6. Definition of Done (G5)

- ✅ API idiomática (`observability.*`) + type safety (dispatch compile-time)
- ✅ Targets JVM/Native/JS (sem gaps, `supportedOn` = true)
- ✅ Testes `KofObservabilityTest` 3/3 + `KofSecurityTest` 25/25 + `KofValidationTest` 3/3 sem regressão
- ✅ Benchmark não aplicável (operações O(1) / syscall `getrandom`)
- ✅ Security review: `requestId` usa `SecureRandom` (JVM) / `getrandom` (Native) / `crypto.randomUUID` (JS) — sem vazamento
- ✅ Docs: este arquivo + `docs/ecosystem-coverage.md` §3.9/§4/§7 + `docs/stdlib/stdlib.md` §3
- ✅ Exemplo real: snippet acima roda nos três targets
