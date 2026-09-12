# Benchmarks

Performance não é avaliada por sensação. Cada benchmark deve possuir:

```text
input
expected output
implementation
harness
metrics
baseline
```

## Estrutura

```text
benchmarks/
├── micro/          # arithmetic, calls, branches, loops, field/array access, allocation, boxing, strings, exceptions, lambdas, collections
├── algorithms/     # sorting, binary search, hash lookup, graph/tree traversal, matrix multiplication, parsing, serialization, hashing, compression, JSON, IO
├── collections/    # insert, lookup, remove, iteration sob volume
├── strings/        # concat, split, replace, search, parse sob volume
├── math/           # integer/long/float point, bitwise, comparisons
├── objects/        # allocation, field access, temporaries
├── inheritance/    # virtual dispatch
├── interfaces/     # interface dispatch
├── generics/       # generic code, boxing/unboxing
├── json/           # serialization/deserialization
├── io/             # open, read, write, close
├── concurrency/    # spawn, futuramente await, locks, threads
├── startup/        # tempo de inicialização por target
├── memory/         # heap, peak RSS, allocation rate, object count, GC activity, temporary allocations, file descriptors, threads
├── stress/         # testes prolongados de CPU, memory, collections, strings, concurrency, IO, exceptions, HTTP
└── applications/   # programas completos reais
```

## Regras

- Programas semanticamente equivalentes entre implementações (Java, Kof/JVM, Kof/Native, Kof/JS, Kof/Script).
- Validar `expected output` antes de coletar métricas.
- Comparar contra baseline (docs/architecture/performance.md seção 25) e sinalizar regressões.
- Stress tests medem também requests/sec, p50, p95, p99, CPU e memória (HTTP).
- Long-run tests verificam memory growth bounded, resource usage bounded, throughput e latência estáveis (docs/architecture/performance.md seção 24).

Referência arquitetural completa: docs/architecture/performance.md