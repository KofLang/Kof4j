[English](performance.md) | [Português](performance.pt_BR.md)

# KOF — Performance, Benchmarks, Resource Safety, Guidelines

last: none
doing: none-normative
next: none
location: docs/architecture
state: normative

**Version:** 0.5.0-beta (37 benchmarks; `kof bench` + `benchmark.yml` threshold 1.20)
Permanent principles, not suggestions: type system, IR, backends, runtime, stdlib and tooling obey them.

# 1. Core

- Remove complexity from code without moving it to runtime: high expressiveness + type safety + low overhead/consumption + performance + interop.
- Unneeded-at-runtime abstractions disappear at compile time. Rule: **generate the most semantically efficient representation per construct.**
- Superiority: idiomatic Kof ≥ idiomatic Java; **never reproduce Java overhead** out of implementation convenience (boxing/allocation/iterator/temporary/reflection/indirection/dispatch/wrapper) when provably eliminable.

# 2. Targets

- **JVM:** excellent HotSpot bytecode (concrete types, direct access, no useless boxing/allocs/reflection, efficient loops, correct StackMapTable). `for (x in values)` must seek the direct loop, never auto-Iterator+box.
- **Native:** more aggressive (startup/memory/allocs/calls/syscalls/cache/stack/lean binaries). State 0.5.0-beta: free-list `kof_free_head`, FP in XMM, dtoa via `snprintf`, `spawn` on pthreads. May surpass equivalents where control allows.
- **JS:** natural efficient ECMAScript (no wrappers/temporaries/closures/boxing/indirect-dispatch/giant runtime); direct construct mapping.
- **Script:** same frontend/type-system/IR, no second compiler; fast startup, low latency, minimal runtime. Script ≠ slow.

# 3. Zero rules

- **Overhead:** every feature answers its runtime cost; hidden overhead forbidden.
- **Boxing:** `Int/Long/Bool/Char` stay primitive (`ILOAD/ISTORE/IADD`, not alloc→unbox→op→box); type system + IR preserve the info.
- **Reflection:** only interop/frameworks/metadata/explicit APIs; `user.name` → `GETFIELD`, never reflect→lookup→invoke.
- **Dispatch:** most direct form known (`INVOKESTATIC/SPECIAL/VIRTUAL/INTERFACE`); no artificial dynamic dispatch (all targets).
- **Disappear:** loops/ranges/lambdas/closures/pipelines/matching/properties/interpolation/collections/generics/sugar must vanish unless needed ("still needed after compile?" → eliminate).
- **Type system as optimizer:** know type/subtype/mutability/escape/dispatch/nullability/specialization at compile time.
- **IR:** must allow fold/DCE/unreachable/CFG-simplify/propagation/branch/allocation/escape/inline/specialize/scalar/loop/dispatch opts (future ones must not be architecturally blocked).
- **Escape:** identify non-escaping scopes for future efficient representation (JVM: analysis→bytecode→HotSpot; Native: direct machine repr).
- **Memory/stack/resource safety:** no leaks/double-free/use-after-free/corruption/unbounded temps (esp. Native; clear ownership, no forgotten-`malloc` runtime); never artificial recursion (loops stay loops; tail analysis, no artificial frames; **no compiler-caused stack overflow**); files/fds/sockets/threads/locks/handles/buffers/processes get predictable lifecycles (normal/return/exception/break/continue); backends never introduce resource bugs (document+test+limit, never hide).

# 4. Benchmarks

- Part of the architecture (never feeling): tree `benchmarks/{micro,algorithms,collections,pipelines,strings,math,objects,inheritance,interfaces,generics,json,io,concurrency,startup,memory,stress,applications}`; each has input/expected/implementation/harness/metrics/baseline.
- Micro: arithmetic/bitwise/compare/branch/loop/calls/virtual/interface/field/array/alloc/generics/boxing/strings/exceptions/lambda/closure/collections. Algorithms: sort/search/hash/graph/tree/matrix/parse/serialize/hash/compress/JSON/IO (semantically equivalent programs).
- Memory: heap/RSS/alloc-rate/objects/GC/temps/fds/threads (time alone never decides; 10% faster × 5x memory is not better).
- Stress (`benchmarks/stress/`): CPU prolonged, millions of allocs, collection/string volumes (insert/lookup/remove/iterate, concat/split/replace/search/parse), `spawn` storms (+`await` future), IO volumes, throw/catch/finally storms, HTTP req/s+p50/p95/p99+CPU/RAM.
- Long-run: bounded memory/resources, stable throughput/latency (leak/degrade detection).
- Regression: per-version baseline (0.2.6-beta golden: sort 42ms/json 17ms/startup 38ms/12MB/37 benches); CI flags `PERFORMANCE REGRESSION` past thresholds (statistics, no false positives; significant regressions investigated).
- CI pipeline: compile→run→validate→collect→compare→report (`benchmark.yml`; informative on PRs, blocking past significant limit).
- Multi-target: JVM/Native/JS/Script share frontend/type-system/IR; benchmarks compare Java vs Kof-per-target asking where/why (never marketing); every relevant regression becomes an investigation.
- Java→Kof migration must not preserve incidental overhead: use type/control-flow/ownership/semantic info for same semantics + less code/abstractions/allocs/better repr. Equivalence is **semantics**, never internal implementation (no Iterator preserved for structural equivalence).

# 5. Debug / profile

- Profiles: debug (mapping/lines/locals/metadata/traces/observability) vs release (optimized, metadata only if needed); debugging never forces overhead on the final program.
- Runtime debugging (planned): Editor → Debug Protocol → Adapter → per-target interface (JVM existing mechanisms; Native own metadata/protocol; JS DevTools/Node); compiled program, breakpoints/step/locals/threads/watch/attach.
- `kof bench`/`profile`/`inspect`: startup/throughput/latency/CPU/memory/allocs/GC. JVM: JFR `kof profile --methods` with Kof lines ✅. Native: honest gap (needs `perf_event`, refused naming the sysctl when gated) ✅. JS: Node `--cpu-prof` + `.mjs.map` to Kof lines ✅.

# 6. Stdlib / IO / concurrency

- Stdlib (`core/collections/io/time/json/concurrent`) designed for allocs/cache/syscalls/boxing/dispatch/memory/throughput/latency; simple API, efficient inside.
- IO: consistent cross-platform APIs (backend picks impl, never a second API per technology).
- Concurrency: low overhead, safe, predictable, no leaks/abandoned threads; `spawn processarFila()` over Thread/Runnable/Executor ceremony — hide ceremony, never absurd cost.
- Exceptions under load (latency/alloc/stack/nested/finally/propagation), no state corruption; Native unwinding preserves correctness/cleanup/stack/resources.

# 7. Feature gate (15 questions)

New feature answers: simpler code? statically verifiable? disappears at compile? runtime cost? allocs? boxing? reflection? indirect dispatch? memory? leak/stack risk? benchmark? stress test? applicable targets? debugger-visible? Bad answer → review the architecture.
DoD: Parser→Script support + unit/E2E/docs/benchmark/stress/memory/resource tests + debug metadata, preserving correctness+performance+resource/stack-safety+debuggability.

# 8. Optimization rule

`intent → semantics → static analysis → eliminate abstractions → minimum representation → efficient code` (never intent→boilerplate→objects→wrappers→reflection→giant-runtime). Final: complexity removed from BOTH sides (programmer: less code/ceremony + safety/intent; machine: less overhead/alloc/boxing/dispatch/memory). Philosophy is never "fast enough" but **"if we can do better, we do better"** — JVM seeks better-than-Java, Native exploits full control, JS idiomatic, Script minimal, all targets correctness-first with zero accepted unnecessary overhead. Performance, memory/resource/stack safety, observability and debuggability ARE quality, not finishing touches.
