[English](observability.md) | [Português](observability.pt_BR.md)

# kof.observability — Health, Metrics e Request IDs (G5)

**Última atualização:** 17 de setembro de 2026
**Versão:** 0.6.7 (`VERSION` 0.6.7)

> **Status:** DONE (JVM/Native/JS) — `KofObservabilityTest` 12/12 (VERSION 0.6.0, free-list Native); API confirmada em `KofObservability.java`; histogramas/spans/export Prometheus fechados como `OBS002` (16/09); **export OpenTelemetry (`exportSpans`) fechado como `OBS003` — JVM/JS ✅, gap honesto no Native**
> **Módulo:** `kof.observability` — `observability.*`
> **Targets:** JVM ✅ · Native x86_64 ✅ (free-list) · Native riscv64 ✅ · JS ✅ — G5 fechado 0.2.6-beta; OBS002 16/09; **`OBS003`: `exportSpans()` só no JVM/JS (o Native recusa em tempo de compilação — nunca stub)**

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
| `observability.histogram(name, value)` | `(String, Int) -> Void` | — | Registra `value` no histograma nomeado (soma + contagem) |
| `observability.metrics()` | `() -> String` | texto Prometheus | Exporta counters/gauges/histogramas em formato texto Prometheus |
 | `observability.requestId()` | `() -> String` | UUID/hex | Gera ID de requisição (16 bytes aleatórios → 32 hex) |
 | `observability.correlationId()` | `() -> String` | UUID/hex | Alias de `requestId()` — para propagação entre serviços |
 | `observability.traceId()` | `() -> String` | 32 hex | ID de trace (W3C Trace Context) — 16 bytes aleatórios |
 | `observability.spanId()` | `() -> String` | 16 hex | ID de span (W3C Trace Context) — 8 bytes aleatórios |
 | `observability.spanStart(name)` | `(String) -> String` | handle | Inicia um span cronometrado W3C; retorna handle opaco (`traceId+spanId`) |
 | `observability.spanEnd(handle)` | `(String) -> String` | JSON | Encerra o span; retorna `{traceId,spanId,parentSpanId,name,startMicros,endMicros,durationMicros}` (`startMicros`/`endMicros` são micros **epoch**) |
 | `observability.exportSpans()` | `() -> String` | OTLP/JSON | Exporta os spans concluídos como payload **OpenTelemetry OTLP/JSON** (`resourceSpans`) — **JVM ✅ / JS ✅**, Native `OBS003` |

Todas as chamadas são **disponíveis nos três targets** (JVM/Native/JS) — `supportedOn` retorna `true` sempre; não há `OBS001` em uso normal. Histogramas, spans cronometrados e o export texto Prometheus estão implementados (`OBS002` fechado 16/09); qualquer gap futuro reportará `OBS00x`. A **única** exceção é `exportSpans()`: a serialização OTLP/JSON é **JVM-first** (R7) — no Native a chamada é recusada em tempo de compilação com **`OBS003`** (recusa honesta, nunca stub silencioso). `spanStart(name)` **preserva o nome do span** no JVM/JS (antes era descartado e saía sempre como `"span"`).

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
    observability.histogram("http.latency", 12) // soma=12, contagem=1

    // request tracking
    val req = observability.requestId()      // "a3f1c9e2b4d64a8f9c0e1d2f3a4b5c6d"
    val corr = observability.correlationId() // outro ID, propagável em header
    println(req + " " + corr)

    // tracing (W3C Trace Context) — IDs puros, sem store, 3 targets
    val trace = observability.traceId() // 32 hex
    val span  = observability.spanId()  // 16 hex
    println(trace + "-" + span) // ex.: header traceparent: 00-<trace>-<span>-01

    // span cronometrado (OBS002) + export Prometheus
    val h = observability.spanStart("db.query")
    val json = observability.spanEnd(h)   // {"traceId":..,"name":"db.query",..}
    println(json)
    println(observability.metrics())      // # TYPE .. / nome valor

    // export OpenTelemetry (OBS003) — payload OTLP/JSON que um coletor
    // consome em /v1/traces. JVM/JS; no Native é um OBS003 de compilação.
    println(observability.exportSpans())
}
```

---

## 3. Semântica por target

### JVM

- **Health/readiness/liveness:** constantes (`"UP"` / `true`) — prontas para customização futura (ex.: checar `kof.db`).
- **Metrics:** `ConcurrentHashMap<String, AtomicInteger>` para counters, `ConcurrentHashMap<String, Integer>` para gauges, `ConcurrentHashMap<String, long[]>` (`[soma, contagem]`, sincronizado) para histogramas — thread-safe, sem persistência (memória do processo, como Micrometer `simple`). `metrics()` renderiza em formato texto Prometheus (`name_count`/`name_sum`).
- **Spans:** `spanStart(name)` guarda o nome + `System.nanoTime()` (duração) + `System.currentTimeMillis()*1000` (micros epoch) sob o handle `traceId+spanId`; `spanEnd` retorna o JSON do span com o **nome real** e `durationMicros`. Cada span concluído entra num ring limitado (máx. 256).
- **Export OTel (`OBS003`):** `exportSpans()` renderiza o ring em OTLP/JSON (`resourceSpans` → `scopeSpans` → `spans`), `kind` = 1 (INTERNAL), tempos em nanossegundos unix-epoch como string, atributo de recurso `service.name=kof`, escopo `kof.observability`. Não destrutivo; nomes com escape JSON.
- **Request IDs:** `UUID.randomUUID().toString()` (36 chars com hífens, variante 4).

### Native (asm x86-64, sem libc)

- **Health:** aloca `KofString` "UP" via `kof_string_from_literal` (`.Lstr_obs_up`).
- **Readiness/liveness:** `mov $1, %eax; ret`.
- **Metrics:** `.bss` com 32 slots (`512` bytes) para counters e gauges — cada slot `16` bytes (`ptr` + `int` + pad). Busca linear com comparação de conteúdo (`length` em `16(%rdi)` + bytes em `24(%rdi)`); `counter`/`increment` incrementam, `gauge` sobrescreve. Sem persistência; overflow silencioso após 32 nomes distintos (retorna `0`).
- **Histogramas/spans/metrics (`OBS002`):** slots `.bss` estendidos para histogramas (soma+contagem) e spans (nanos de início); `metrics()` monta o texto Prometheus em asm.
- **Export OTel:** **não implementado** — `exportSpans()` é recusado em tempo de compilação com `OBS003` (R7: JVM-first; nunca stub). O próprio caminho de span no Native bate com o golden do JVM em x86_64/riscv64/aarch64 desde 18/09 (faces (b)+(c) do §272 fechadas; o overflow do `kof_sec_random_hex` achado no port foi fechado como §292).
- **Request IDs:** tail-call para `kof_sec_random_hex(16)` — `getrandom(2)` → `32` hex chars (sem hífens, `318` syscall), mesma entropia do `kof.security`.

### JS (kof-runtime.mjs)

- **Health/readiness/liveness:** `"UP"` / `1`.
- **Metrics:** objetos `__kofObsCounters` / `__kofObsGauges` / `__kofObsHistograms` (`{sum,count}`) em closure — `counter`/`increment`/`gauge`/`histogram` manipulam o dicionário JS; `metrics()` renderiza texto Prometheus.
- **Spans:** `Map` `__kofObsSpans` com chave no handle — `spanStart` registra o nome + `Date.now()*1000` (micros epoch), `spanEnd` retorna o JSON do span com o **nome real**. Cada span concluído entra num array limitado (máx. 256).
- **Export OTel (`OBS003`):** `exportSpans()` monta o mesmo payload OTLP/JSON do JVM via `JSON.stringify` (`resourceSpans`/`scopeSpans`/`spans`, `kind` = 1, `startTimeUnixNano`/`endTimeUnixNano` como string).
- **Request IDs:** `crypto.randomUUID()` quando disponível, fallback `Math.random` com formato `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`.

---

## 4. Testes

`kof-compiler/src/test/java/dev/kof/compiler/KofObservabilityTest.java` — 12 testes (JVM/Native x86+riscv64+aarch64/JS), 12/12:

- `observabilityJvm` / `observabilityNative` / `observabilityJs` — health/readiness/liveness, counter sequencial, gauge, `histogram` + `metrics()` (Prometheus `_count`/`_sum`), `requestId`/`correlationId`.
- `tracingJvmNativeJs` — `traceId` (32 hex) / `spanId` (16 hex) W3C nos três targets.
- `spansWithTiming` — JSON de `spanStart`/`spanEnd` com o **nome real** (`name":"op"`) no JVM/JS.
- `otlpExportJvm` — `exportSpans()` OTLP/JSON: `resourceSpans`, `service.name`, `scope`, `kind`, `startTimeUnixNano`/`endTimeUnixNano`; **edges**: ring vazio (`"spans":[]`) e nome com `"` escapado.
- `otlpExportJs` — mesmo export no JS (paridade com o payload do JVM).
- `otlpExportNativeIsHonestGap` — compilação Native **recusada com `OBS003`** (R6/R7, sem stub).
- `applicationLifecycle` / `applicationLifecycleEmptyBlocks` — hooks `application { onStart/onShutdown }`.

Todos os testes passam com `KOF_KEEP_ASM=1` preservando `Main.s` para inspeção.

---

## 5. Integração com o ecossistema

```
kof.config ──► kof.observability (config de logging/metrics)
kof.web ──► kof.observability (request IDs, metrics por rota)
kof.security ──► kof.observability (audit logging futuro)
kof.observability ──► kof.bench/profile (tooling já existente)
```

Próximos passos (fora do P0-G5): rota HTTP `/metrics` servindo `observability.metrics()`, envio do payload de `exportSpans()` a um coletor via OTLP/HTTP (um POST fino com `kof.http` — sem sidecar), fechar o `OBS003` no Native, e health customizável com checks de `kof.db`/`kof.mq`.

---

## 6. Definition of Done (G5)

- ✅ API idiomática (`observability.*`) + type safety (dispatch compile-time)
- ✅ Targets JVM/Native/JS (sem gaps, `supportedOn` = true) — **exceto** `exportSpans()` (`OBS003`, JVM/JS; gap honesto no Native)
- ✅ Testes `KofObservabilityTest` 12/12 + `KofSecurityTest` 25/25 + `KofValidationTest` 3/3 sem regressão
- ✅ Benchmark não aplicável (operações O(1) / syscall `getrandom`)
- ✅ Security review: `requestId` usa `SecureRandom` (JVM) / `getrandom` (Native) / `crypto.randomUUID` (JS) — sem vazamento
- ✅ Export OTel: payload OTLP/JSON `resourceSpans` (JVM/JS), golden medido no oráculo JVM + GraalJS; `OBS003` no Native documentado em `backend-parity.md`
- ✅ Docs: este arquivo + `docs/bugs-and-gaps/ecosystem-coverage.md` §3.9/§4/§7 + `docs/stdlib/stdlib.md` §3
- ✅ Exemplo real: snippet acima roda nos três targets (com `exportSpans()` só no JVM/JS)
