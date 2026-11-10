[English](18-concurrency.md) | [Português](18-concurrency.pt_BR.md)

# 18 — Concurrency

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta — `spawn`/`await` on the 3 targets**
>
> Kof does not expose `Thread`, `Runnable` nor `CompletableFuture`: the intention is
> `spawn` (run in parallel) and `await` (wait for the result). JVM uses virtual
> threads; Native runs on pthread (CONC001 closed on 31/08); JS runs over
> real `async`/`await`/`Promise` from GraalJS (CONC003 closed on 03/09,
> `spawn`/`await` really defer via microtask). The remaining
> gaps are documented, never silent. Chain:
> `intention->Kof->frontend->IR->backend->runtime`.

## spawn — fire and forget

```kf
baixar(String url) {
    // slow work...
}

main() {
    spawn baixar("https://example.com")   // runs in parallel
    println("seguindo o fluxo principal")
}
```

The body can be any expression — the compiler wraps it in a synthetic task:

```kf
spawn {
    var i = 0
    while (i < 100) {
        processar(i)
        i++
    }
}
```

## spawn + await — typed result

`spawn <expression>` returns a typed handle `Handle<T>`; `await` blocks
the calling virtual thread until the value arrives:

```kf
Int somar(a: Int, b: Int) {
    return a + b
}

main() {
    val r = spawn somar(2, 3)     // Handle<Int>
    // ...work while the sum happens...
    val total = await r           // Int — automatic unboxing
    println(total)                // 5
}
```

Primitives (`Int`, `Bool`) and references work equally:

```kf
String buscar() { return "dados" }

main() {
    val r = spawn buscar()
    println(await r)              // "dados"
}
```

## poll / done — without blocking

```kf
val r = spawn trabalho()
if (done(r)) {
    println("pronto: " + poll(r))
}
```

- `poll(r)` returns the value if ready; the **type default** (0/false) for
  primitives not ready, `null` for references. Use `done()` to
  distinguish "not ready" from a default value.
- `done(r)` → `Bool`.
- `poll`/`done` work on JVM, JS and Native x86_64 (on JS execution is
  sequential, so `poll` always has the value and `done` is `true`); on
  riscv64/aarch64 they also work since 15/09 (CONC001 closed — see the gaps
  table below).

## Exceptions cross await

The exception thrown inside the task arrives **with the original message** at
the await point — the runtime unwraps the wrapper:

```kf
Int quebra() { throw "boom" }

main() {
    val r = spawn quebra()
    try {
        await r
    } catch (String e) {
        println(e)   // "boom"
    }
}
```

## Cooperative cancellation

```kf
Int trabalho() {
    var i = 0
    while (i < 10000 && !cancelled()) {
        time.sleep(1)
        i++
    }
    return i
}

main() {
    val r = spawn trabalho()
    time.sleep(30)
    assert(cancel(r))       // marks the task
    await r                 // the task leaves the loop early
}
```

- `cancel(r)` marks the handle; **the task decides when to leave** by consulting
  `cancelled()` inside its own body.
- `cancelled()` outside a task returns `false`.
- On JS it is a marked no-op (`cancel` returns `0`, `cancelled` returns `false`) —
  execution is sequential.

## selectAny — first to arrive

```kf
val a = spawn lenta()      // 300ms
val b = spawn rapida()     // immediate
println(selectAny(a, b))   // value of the fast one
```

It blocks until **any** handle completes and returns its value. On JS it is
`Promise.race` over the handles (`js/JsRuntimeUiLayout.java:304`); on Native
x86_64 it works by 1 ms polling over the handles; on riscv64/aarch64 it also
works since 15/09 (CONC001 closed).

## Channels — typed FIFO between workers

```kf
val c = channel<Int>()     // typed channel (FIFO, blocking)
c.send(5)                  // blocks until a receiver takes it
var x = c.receive()        // blocks until a sender produces
println(x)                 // 5
```

- `channel<T>()` creates a typed channel; a bare `channel()` boxes by the
  argument type (§374).
- `send(v)` returns `void`; `receive()` returns `T` — both block (JVM:
  `LinkedBlockingQueue` put/take; Native: runtime futex queue `kof_channel_*`;
  JS: sequential queue with the same observable order).
- Valid members are exactly `send`/`receive` — anything else is SEM025.
- Channels work on ALL targets including riscv64/aarch64 (§423 closed 23/09),
  and the §485 fix (24/09) guarantees a drained queue never yields a stale
  NULL (deterministic SIGSEGV before; single-threaded repro now clean).

```kf
// producer/consumer
spawn produtor(c)
var v = c.receive()
```

## lock — mutual exclusion on a shared object

When several tasks update the same object, `lock (target) { ... }` lets only
one of them run the block at a time for that target:

```kf
class Counter {
    Int n = 0

    add() {
        lock (this) {
            n = n + 1          // read-modify-write, never interleaved
        }
    }
}
```

- The target is evaluated **once** and must be a non-null object (a class
  instance, a record, a collection). A primitive, a `String` or a nullable
  value is refused at compile time (`CONC004`): lock on the object that owns
  the shared state, usually `this`.
- The lock is released on **every** exit of the block: normal end, `return`,
  `break`/`continue`, and a `throw`.
- It is **reentrant**: the task that holds the lock may enter it again
  (`lock (c) { lock (c) { ... } }`, or a locked method calling another one).
- Exiting a `lock` on `x` happens-before the next entry into a `lock` on `x`
  (memory model §2, rule 7): writes made inside are visible to the next holder.
- Per target: JVM and Script use a reentrant lock per object (by identity),
  Native a reentrant futex lock (x86-64, riscv64, aarch64), JS and Wasm run a
  single thread, so there is nothing to exclude. On JS a function that uses
  `lock` **and** suspends (`await`, `sleep`, `channel.receive()`, or a call to
  a function that does) is refused with `CONC005`: another task would run
  while the lock is held. Move the suspending call out of that function.
- Prefer a `Channel` when tasks hand data to each other; `lock` is for a small
  critical section over shared state.

## Semantics

- JVM: each `spawn` runs on a **virtual thread** (JDK 21+) — cheap for
  thousands of tasks. Native: the task runs on a **pthread** created by the
  runtime trampoline. JS: the body runs sequentially (no parallelism).
- The program waits for the tasks before exiting (implicit join in the runtime).
- An exception inside the task is re-thrown at the `await` point.
- `await` on a handle twice returns the same value (the result is memoized by the runtime).

## Gaps per target (never silent)

Each `✅` cites the test that proves it; each `❌` means the runtime
symbol **is not emitted** for that target. `ConcurrencyGapsDocTest`
locks this table against the code — see the note at the end of the section.

| Construct | JVM | Native x86_64 | Native riscv64/aarch64 | JS |
|-----------|-----|----------------|-------------------------|----|
| `spawn stmt` | ✅ `SpawnE2ETest.spawnRunsConcurrentlyAndJoins` | ✅ pthread — `SpawnE2ETest.nativeSpawnStmtRuns` | ✅ `clone` 220 — `NativeRiscv64E2ETest.riscv64SpawnFireAndForgetJoins` | ✅ microtask — `SpawnE2ETest.jsSpawnStmtRunsSequentially` |
| `val r = spawn expr` | ✅ `KofAwaitTest.awaitJvm` | ✅ pthread — `SpawnE2ETest.nativeSpawnExprAwait` | ✅ `clone` 220 — `NativeRiscv64E2ETest.riscv64SpawnAwait` | ✅ microtask — `KofAwaitTest.awaitJs` |
| `await r` | ✅ `KofAwaitTest.awaitJvm` | ✅ `pthread_join` — `KofAwaitTest.awaitNativeRuns` | ✅ futex over `done` — `NativeAarch64E2ETest.aarch64SpawnAwait` | ✅ `KofAwaitTest.awaitJs` |
| `poll` / `done` | ✅ `KofAwaitTest.pollDoneJvm` | ✅ `KofConcurrency2Test.pollDoneNative` | ✅ `kof_poll`/`kof_done` (CONC001 closed 15/09) — `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` | ✅ `KofAwaitTest.pollDoneJs` |
| `cancel` / `cancelled` | ✅ `KofConcurrency2Test.cancelCooperativeJvm` | ✅ `KofConcurrency2Test.cancelCooperativeNative` (bug 101 → §117 FIXED 13/09, real-TID table `3734f2aa`) | ✅ `kof_cancel` real TID (CONC001 closed 15/09) — `KofConcurrency2Test.crossNativeCancelDuringRunningWorker` | ✅ no-op (`cancelled()` = `0`) — `KofConcurrency2Test.cancelJsSequential` |
| `selectAny` | ✅ `KofConcurrency2Test.selectAnyJvm` | ✅ 1 ms polling — `KofConcurrency2Test.selectAnyNative` | ✅ `kof_select_any` (CONC001 closed 15/09) — `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` | ✅ `Promise.race` — `KofConcurrency2Test.selectAnyJs` |
| `awaitTimeout` | ✅ `KofConcurrency2Test.awaitTimeoutJvm` | ✅ 1 ms polling — `KofConcurrency2Test.awaitTimeoutNative` | ✅ `kof_await_timeout` (CONC001 closed 15/09) — `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` | ✅ `KofConcurrency2Test.awaitTimeoutJs` |
| `lock (x) { }` | ✅ per-object `ReentrantLock` — `LockBlockE2ETest.lockExcludesAndReleasesOnEveryExitOnJvmScriptAndJs` | ✅ reentrant futex lock table — `LockBlockE2ETest.lockExcludesOnNativeX86` | ✅ `kof_lock_enter`/`kof_lock_exit` (G240, 06/11) — `LockBlockE2ETest.lockExcludesOnNativeCross` | ✅ no-op (one thread; CONC005 refuses lock + suspension) — `LockBlockE2ETest.lockExcludesAndReleasesOnEveryExitOnJvmScriptAndJs` |

`spawn`/`await` closed `CONC001` on Native on 31/08 (pthread_create +
trampoline + `pthread_join` + thread-safe allocator via futex), and the
auxiliary constructs (`poll`/`done`/`cancel`/`cancelled`/`selectAny`/
`awaitTimeout`) **also work on x86_64** since then — runtime in
`runtime/RuntimeConcurrency.java:304`, proof in
`KofConcurrency2Test.selectAnyNative`.

On x86_64, `cancel`/`cancelled` use the table by **real TID + linear probe**
— the old `TID % 256` collision (bug 101, renumbered §117) was FIXED on
13/09 (`3734f2aa`).

On **riscv64/aarch64** the auxiliaries **exist since 15/09**: `CONC001` was
closed by porting the symbols (`e8364c97`) — the slice
`nat/NativeRiscvAsmRtB48.java` emits `kof_poll`, `kof_done`, `kof_cancel`,
`kof_select_any` and `kof_await_timeout`, with cancel by real TID
(`gettid(178)` recorded by the kernel in the `clone` ctid, no collision).
Historical note: between 11/09 and 15/09 the absence was an honest R6 gate —
`ExpressionStaticCallLowerer` emitted **`CONC001` at compile-time** (issue
#91; before it there was no gate and the error appeared only at **link**, as
an undefined symbol — same pattern as bug 59); the gate was **removed** with
the port. Proof: `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` and
`crossNativeCancelDuringRunningWorker` (qemu, both arches — they skip without
the cross toolchain). What remains open on cross is the OTP supervisor
(`OTP001` — raw `clone` without TLS for the §129 handler chain), not the
helpers.

> **This table is locked by test.** `ConcurrencyGapsDocTest` (in
> `kof-compiler/src/test/java/dev/kof/compiler/`) breaks the build if a
> support cell does not cite an existing test method, or if a
> cell marked `❌` refers to a symbol that the cross emitters
> actually emit. It is the same pattern as `ConformanceMatrixDocTest`, which locks
> `docs/bugs-and-gaps/conformance-matrix.md`. Reason: this table spent
> months saying that the auxiliaries reported `CONC001` on Native — because
> nothing compared it with the code.
>
> Guard limit, explicit: it proves that the doc points to tests that
> **exist**, not that those tests **pass**. The behavior proof
> remains the suite.
>
> **The riscv64/aarch64 column does not have the same weight of proof as the others.**
> `NativeRiscv64E2ETest` and `NativeAarch64E2ETest` are entirely
> conditioned by `Assumptions.assumeTrue(...)` on the cross toolchain
> (`riscv64-linux-gnu-as`, `riscv64-linux-gnu-ld`, `qemu-riscv64`) — without
> it, the cross proofs of those two classes (2×42 = 84 in the 16/09
> measurement; grows with each commit) **skip silently**. Execution of
> 11/09 on a machine without the toolchain: JVM, Native x86_64 and JS passed
> (`SpawnE2ETest` 10/10, `KofAwaitTest` 8/8, `KofConcurrency2Test` 29/29
> with 1 qemu skip, `ConcurrencyGapsDocTest` 3/3), and riscv/aarch
> skipped 33/33 each. Therefore the `✅` of that column means *"proven
> where the toolchain exists"*, not *"proven"*. **That gap is closed on CI:**
> the `cross-native` job (`ci.yml`) installs the cross binutils, libc and
> `qemu-user-static` and runs both suites under qemu — a green job there is
> real cross-parity proof, not a masked skip. On a local machine without the
> toolchain the `assumeTrue` guard still applies (honest skip, 42+42).

On JS the model is
single-threaded, but truly concurrent over the event loop: `spawn`
enqueues the task as a microtask (it does not run right away) and `await` really
suspends until it resolves — the program waits for all spawned tasks
before exiting, like JVM/Native (`CONC003` closed).

## Target separation (0.2.0)

The `Target` enum separates `NATIVE` (x86-64) from `NATIVE_RISCV64` and `NATIVE_AARCH64`.
`spawn`/`await` work on Native (pthread) — the separation applies to
codegen/linker (`as`/`ld` per arch), it does not change the concurrency semantics.
Native uses the free-list `kof_free_head` for `mmap` reuse.

## Next step

**[19 — Packages and Modules](19-packages-and-modules.md)**
