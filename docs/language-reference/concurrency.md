[English](concurrency.md) | [Português](concurrency.pt_BR.md)

# CONCURRENCY.md — Kof Concurrency Model

**Status:** Implemented on all 3 targets, real concurrency on all 3 (JVM virtual threads + JS async/await/Promise + Native pthread) — 0.2.6-beta 03/09
**Version:** 0.6.7
**Date:** September 3, 2026 (CONC003 closed — JS is no longer sequential)

---

## 0. Implemented (0.2.6-beta)

### `spawn` — statement

```kof
spawn processarFila()      // function call as a task
spawn {                    // inline block (lambda with no captures)
    println("background")
}
```

Implemented semantics:

- the task runs concurrently (JVM: virtual threads; Native: OS threads via `pthread_create`);
- the program **waits for the tasks before exiting** (implicit join — counter +
  shutdown hook on the JVM; `pthread_join` on Native);
- the function's return value is discarded (fire-and-forget);
- exceptions in the task are printed to stderr (they do not bring down the program);
- ~~**Native**: diagnostic `CONC001`~~ — ✅ closed 31/08: `pthread_create` +
  trampoline + `await`/`pthread_join` + thread-safe allocator (futex lock) +
  implicit join in main (history: "spawn: not supported on the Native
  target yet" — documented gap, never hidden);
- isolation by value: the task receives the arguments; no primitive shared
  state in the language.

### Implemented 0.1.0 → 0.2.6-beta

- `spawn` statement + `val r = spawn f()` + `await r` with typed `Handle<T>` and unboxing (`KofAwaitTest` 7/7, `KofConcurrency2Test` 10/10) — JVM; JS **real async** via GraalJS `async`/`await`/`Promise`, closed 03/09 (`CONC003`, see section 4); full Native pthread (CONC001 closed)
- non-blocking `done(h)`/`poll(h)`, `cancel(h)`/`cancelled()` (cooperative cancel) and `selectAny(h1, h2, …)` — JVM + Native (1ms polling, `KofConcurrency2Test`); JS via handle `{done,value,error,promise}` + `Promise.race` (`cancelled()` always `0` in JS — no thread-local, see section 4); Android follows `AND001`
- `awaitTimeout(r, ms)` — value if the task finishes within the deadline; otherwise throws an exception (catchable via `try/catch`) — JVM (`Future.get(ms)`) + Native (1ms polling with deadline) + JS (cooperative polling via `await Promise.resolve()`, truly fires against a slower task — `KofConcurrency2Test.awaitTimeoutSlowTaskJs`)
- `channel<T>()` — thread-safe FIFO with `c.send(v)`/`c.receive()` — JVM (`LinkedBlockingQueue`, blocking `put`/`take`) + Native (linked list + futex mutex + 1ms polling) + JS (queue of pending resolvers — `receive()` on an empty channel truly blocks until a later `send()`, `KofConcurrency2Test.channelBlocksBeforeSendJs`)
- Lambdas with capture via `BoxN` already support `spawn { println(x) }` — including capturing a mutated variable from an outer scope, on all 3 targets
- `kof.mq` publish/subscribe/queue — **3 targets** (JVM in-memory; Native asm 01/09, MQ001 closed; JS in-process); `kof.time interval/cancel` — JVM+Native+JS (TIME001 closed)

### Not exposed

No platform API (Thread/Runnable/Executor) is visible in the language.
`Thread.startVirtualThread` is an internal detail of the JVM runtime.

### Next iterations (P2)

- ~~typed producer/consumer queues (`kof.concurrent.Queue`)~~ — ✅ 31/08, channel with real blocking 03/09: `channel<T>()` with `send`/`receive` (JVM blocking `LinkedBlockingQueue` + Native futex FIFO + JS queue of pending resolvers);
- ~~`CONC003` — real async on the JS target~~ — ✅ 03/09: GraalJS `async`/`await`/`Promise`, `KofJsRunner` drains the microtask queue (`kofActiveTasks`), see section 4;
- native scheduler (threads on the Native target — depends on futex/clone);
- multiple `select` with timeout (`selectAny` already ✅ without timeout; combining it with a deadline is the next step);
- real `cancelled()` in JS — today always `0` (known limitation: no thread-local for the "current task" context in interleaved async functions in the embedded GraalJS, see section 4).

Concurrency is a capability of the **language/stdlib**, not a collection of
platform APIs.

The programmer expresses **intention**:

```text
concurrent tasks
```

and not:

```text
Thread / ExecutorService / CompletableFuture / pthread / epoll / libuv
```

The decision of how to execute (virtual thread, platform thread, event loop,
worker) belongs to the **target/runtime**.

---

## 2. Semantics (what the language promises)

### 2.1 Tasks

A task is a unit of concurrent execution with:

- explicit start (function or block);
- implicit termination (end of the body);
- optional result (observable return value);
- propagable failure (the task's exception is observable).

Conceptually:

```text
task
```

### 2.2 Isolation

The proposed Kof model is **isolation by value** (like the actor model,
without the ceremony):

- each task has its own execution context;
- communication occurs through **values exchanged explicitly** (parameters,
  returns, queues);
- **no mutable shared memory** as the primary model (eliminates data
  races by construction);
- the runtime can freely scale tasks across OS threads.

This is NOT decided yet — it is the proposed direction. Alternative considered:
shared memory with explicit synchronization (rejected as the primary model
because it reproduces the complexity of threads).

### 2.3 Communication

Exchange of values between tasks through:

- parameters and returns ("join" style);
- producer/consumer queues — implemented as `channel<T>()` (`c.send(v)`/`c.receive()`,
  blocking `take`); the original plan named it `kof.concurrent.Queue`;
- structured callbacks (not as the primary model).

### 2.4 Synchronization

- By construction (isolation);
- by values (return/queue);
- never by locks as a primary API.

---

## 3. Syntax (chosen: `spawn` — 0.2.6-beta)

**Implemented on all 3 targets, real concurrency on all of them:**

```kof
spawn task()
spawn { println("background") }
val handle = spawn tarefa()   // typed Handle<T> — 0.1.0
val result = await handle      // unboxing + clean exception — 0.1.0
```

`spawn`/`await` work on JVM (virtual threads), JS (GraalJS `async`/`await`/`Promise`, 03/09) and Native (OS threads via `pthread_create`, 31/08).

**JS-only restriction (`CONC003-JS-01`):** a regular lambda passed to
`list.map`/`filter`/`reduce` (or a UI/timer/mq handler) cannot use
`await`/`spawn expr`/`channel.receive()` — only the body of a `spawn { ... }`
can. It is a compilation error, not silently wrong behavior:
the reason is that JS's `Array.prototype.map/filter/reduce` is synchronous and does not
know how to handle a callback that returns a `Promise` — without this restriction, the
result would become an `Array<Promise<T>>` disguised as `List<T>`, corrupting
data with no error at all. JVM/Native do not have this restriction.

Rejected: `async { }` (confuses with async/await).

Pending decisions:

- how to express queues/pub-sub (`kof.concurrent.Queue` planned);
- error model (the exception already propagates via `await` with unwrap `ExecutionException` — see `KofAwaitTest`).

**Do not implement syntax before the semantics above are validated.** — validated 0.1.0.

---

## 4. Per-Target Mapping (0.2.6-beta)

The same Kof semantics uses different implementations:

| Target | Implementation | Status |
|--------|---------------|--------|
| JVM 21+ | Virtual Threads (JVM scheduler) | ✅ `await`/`Handle<T>` + `kof.mq` |
| Native x86_64 | OS threads: `pthread_create` + trampoline + `await`/`pthread_join` + `done`/`poll`/`cancel`/`cancelled`/`selectAny` + thread-safe allocator (futex) | ✅ 31/08 (`CONC001` closed) |
| Native riscv64/aarch64 | OS threads: `clone(220)` + stack per `mmap` + wait via futex on `handle->done` (`nat/NativeRiscvSpawn.java`) + helpers `kof_poll`/`kof_done`/`kof_cancel`/`kof_select_any`/`kof_await_timeout` (`nat/NativeRiscvAsmRtB48.java`) | ✅ 15/09 (`CONC001` helpers closed — `e8364c97`, cancel by real TID; proof `KofConcurrency2Test.crossNativeConcurrencyHelpersRun` under qemu, both arches) |
| JS (GraalJS) | native `async`/`await`/`Promise` — async coloring by fixpoint in the compiler (`JsBackend.computeAsyncColoring`), handle `{done,value,error,promise}`, channels with a queue of pending resolvers, `KofJsRunner` drains the microtask queue (`kofActiveTasks`) | ✅ 03/09 (`CONC003` closed) |
| KofScript | JVM via KofScriptGlobals | ✅ |

The Kof code does not change between targets; on x86_64 there is no longer a gap of
`spawn`/`await` nor of the helpers (`poll`/`done`/`cancel`/`cancelled`/
`selectAny`/`awaitTimeout` — `CONC001` closed, including the residual), nor
on JS (`CONC003` closed). On riscv64/aarch64 `spawn`/`await` exists
(`clone` 220 + futex) and the helpers too: `CONC001` was closed on 15/09 by
porting the symbols (`e8364c97` — slice `NativeRiscvAsmRtB48`, cancel by
real TID via `gettid(178)` recorded in the `clone` ctid). Historical note:
between 11/09 and 15/09 the absence was an honest R6 gate —
`ExpressionStaticCallLowerer` emitted **`CONC001`** at compile-time for
`poll`/`done`/`cancel`/`cancelled`/`selectAny`/`awaitTimeout` on those
targets (issue #91; before it there was no gate — the call fell into the
generic `sanitizeName` of `NativeRiscvCrossOps.resolveCalleeNameRiscv` and
the error appeared only at **link** time, as an undefined symbol, the same
pattern as bug 59); the gate was removed with the port. Proof:
`KofConcurrency2Test.crossNativeConcurrencyHelpersRun` and
`crossNativeCancelDuringRunningWorker` (qemu, both arches). The OTP supervisor
also runs on cross since 19/09 (§129 port — per-TID handler chain
`kof_exc_slots`; `OTP001` removed), not just the helpers (Native lane).

In JS specifically: only lambdas created directly at a `spawn` site
("task-lambdas") can become an `async function`; see restriction
`CONC003-JS-01` in section 3. `cancelled()` in JS always returns `0`
(known limitation, no equivalent thread-local for "current task" in
interleaved async functions in the embedded GraalJS).

---

## 4.5 Supervisor (kof.supervisor — issue #83, 11/09, experimental)

OTP supervision core written **in Kof** (host injected by
`import kof.supervisor` — `android-host` mechanism, zero VM change):

```kof
import kof.supervisor

supervisor("net")                       // new object per system
    .child("conn", Fabrica(), "permanent")  // permanent|transient|temporary
    .restartLimit(5)                    // maximum restarts before escalating
    .escalate(Handler)                  // KofEscalate callback (optional)
    .start()                            // starts the workers
    .stop(2000)                         // controlled shutdown (cancel + deadline)
    .stats()                            // KofSupStats(started,restarts,dropped,vivos)
```

- **Failure observation:** a `vigiar` loop per child (dedicated `spawn`) does
  `await` on the worker's handle inside `try/catch (String)` — the original cause
  reaches the supervisor (`done()`+poll is not used: polling a failed handle is
  fragile on targets without preemption).
- **Individual restart with clean state:** the `KofWorkerFactory` creates a
  **new** `KofWorker` on each restart (DD-OTP-06) — it never re-runs the object that
  failed.
- **Policies (DD-OTP-04):** `permanent` crashes→always restarts (terminating normally is
  an anomaly); `transient` terminates-normally→stops, fails→restarts; `temporary`
  never restarts (discarded).
- **Limit + escalation (DD-OTP-07/08):** `restartLimit(max)` → when exceeded, calls
  `escalate.disparou(id,motivo,reinicios)`; without a handler, it STOPS restarting and
  warns on stdout (R6 — never a silent infinite loop).
- **Controlled shutdown:** `stop(deadlineMs)` cancels cooperatively
  (`cancelled()` flag on targets with threads) and waits for the deadline; children that
  ignore the cancel are reported.
- **Parity (rule 6):** JVM ✅ · KofScript ✅ · Native x86 ✅ (since §129 closed,
  15/09: the handler chain is TLS per-thread and a `throw` in a task publishes the
  cause on the handle; `await`/`selectAny` rethrow it in the consumer —
  `selectAny` resolves the handle that completes first **in time** (wall-clock,
  `anyOf` oracle); there is no argument-order tie-break, so programs must not
  rely on which of two instant handles wins (§291)) · Native riscv64/aarch64 ✅
  since 19/09 (§129 cross port — per-TID handler chain `kof_exc_slots`; `OTP001`
  removed) · JS ✅ since 18/09 (§132 resolved:
  `time.sleep` is an await-point — cooperative async sleep driven by the `KofJsRunner`
  host pump — so the supervisor worker fires and `OTP002` was lifted; no target
  blocks at compile-time any more).

## 5. Concurrent I/O

Code like:

```kof
loadUser(id: UUID): User {
    return database.users.find(id)
}
```

does not require the user to know whether internally it used:

```text
blocking I/O
non-blocking I/O
virtual thread
epoll
event loop
worker
```

That decision belongs to the target/runtime.

---

## 6. Dependencies (0.2.6-beta)

- ✅ Lambdas with capture via `BoxN` — implemented (necessary for idiomatic `spawn { ... }`);
- ✅ producer/consumer queues — implemented as `channel<T>()` (send/receive, real blocking 03/09); `kof.mq` also provides pub/sub. The plan's `kof.concurrent.Queue` name was realized as `channel<T>()`;
- per-task exception model — ✅ unwrap `ExecutionException` on `await` (JVM);
- ✅ OS threads on Native — `pthread_create` + trampoline + futex (31/08, `CONC001` closed on x86_64); `scheduler.every/at` scheduler on Native still follows `SCHED001`.

## 7. Implementation Phases

1. Semantics validated (this document);
2. `spawn`/`async` primitive on the JVM (virtual threads when available) — ✅;
3. Native Scheduler — ✅ (pthread, `CONC001` closed);
4. Queues/pub-sub in the stdlib — ✅ (`channel<T>()`, `kof.mq`);
5. KofJS — ✅ 03/09 (`CONC003` closed, see section 4).

## 8. Non-Goals

- Exposing `Thread`, `ExecutorService`, `pthread`, `epoll` as a language API;
- locks as the primary model;
- `CompletableFuture`-style APIs leaking to the user.
