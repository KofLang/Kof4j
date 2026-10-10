[English](README.md) | [Português](README.pt_BR.md)

# Benchmarks

Performance is not evaluated by feeling. Every benchmark must have:

```text
input
expected output
implementation
harness
metrics
baseline
```

## Structure

```text
benchmarks/
├── micro/          # arithmetic, calls, branches, loops, field/array access, allocation, boxing, strings, exceptions, lambdas, collections
├── algorithms/     # sorting, binary search, hash lookup, graph/tree traversal, matrix multiplication, parsing, serialization, hashing, compression, JSON, IO
├── collections/    # insert, lookup, remove, iteration under volume
├── pipelines/      # functional List pipelines (map/filter/combined/with-take/distinct/sorted) — eager cost yardstick
├── strings/        # concat, split, replace, search, parse under volume
├── math/           # integer/long/float point, bitwise, comparisons
├── objects/        # allocation, field access, temporaries
├── inheritance/    # virtual dispatch
├── interfaces/     # interface dispatch
├── generics/       # generic code, boxing/unboxing
├── json/           # serialization/deserialization
├── io/             # open, read, write, close
├── concurrency/    # spawn, future await, locks, threads
├── startup/        # startup time per target
├── memory/         # heap, peak RSS, allocation rate, object count, GC activity, temporary allocations, file descriptors, threads
├── stress/         # extended tests of CPU, memory, collections, strings, concurrency, IO, exceptions, HTTP
└── applications/   # real complete programs
```

## Rules

- Programs semantically equivalent across implementations (Java, Kof/JVM, Kof/Native, Kof/JS, Kof/Script).
- Validate `expected output` before collecting metrics.
- Compare against baseline (docs/architecture/performance.md section 25) and flag regressions.
- Stress tests also measure requests/sec, p50, p95, p99, CPU and memory (HTTP).
- Long-run tests verify bounded memory growth, bounded resource usage, stable throughput and latency (docs/architecture/performance.md section 24).

Full architectural reference: docs/architecture/performance.md
