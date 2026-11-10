[English](concurrency-memory-model.md) | [Português](concurrency-memory-model.pt_BR.md)

# Kof Concurrency Memory Model (SG-020)

> **State:** SPEC ADOPTED (maintainer decision 11, 09/09) — implemented
> and VALIDATED 10/09: the 5 HB edges of §2 have proof in
> `KofConcurrency2Test`/`SpawnE2ETest` (green in the suite). SG-020 CLOSED — the
> document left `docs/development/` (there is no pending work here).
> **Origin:** maintainer decision 11 on the spec gaps ("implement
> concurrent memory model with green threads").
> **Date:** 09/09/2026 · lane spec-gaps · moved to docs/ 11/09.

## 1. What is specified

Kof's concurrency model is **sequentially consistent (SC)** on all
targets. Every observation of a concurrent write by another flow of
execution obeys **total** happens-before — with no observable reordering
within the same flow.

Primitives (all from the language, never mechanism):

| Primitive | Semantics |
|---|---|
| `spawn f()` / `spawn { ... }` | creates a flow of execution (green thread); fire-and-forget when the value is discarded |
| `val h = spawn f()` → `await h` | Handle<T>; `await` **blocks** the caller until the return and establishes HB |
| `time.interval(ms, fn)` / `scheduler.every(ms) { }` | recurring job; cancellation by id |
| `Channel<T>` | FIFO; `send` blocks if full, `receive` blocks if empty; each value delivered to exactly 1 receiver |
| `lock (target) { ... }` | mutual exclusion on `target` (a non-null object) for the block; released on every exit (end, `return`, `break`/`continue`, throw); reentrant (G240, 06/11) |

## 2. Happens-before (normative rules)

1. **spawn:** everything the parent wrote before the `spawn` is visible to the
   child (HB = spawn edge).
2. **await:** everything the task wrote before returning is visible to the
   awaiter after the `await` (HB = await edge).
3. **Channel:** `send(v)` HB the `receive()` that delivers `v`.
4. **Cancellation** (`cancel(h)`/`h.cancelled()`): the cancellation is
   HB-observable by the target task at its next scheduling point; there
   is no synchronous preemption.
5. **Locals are private** to the flow; **statics and fields of shared
   objects** follow SC (atomic writes per field; no observable reordering
   tornadoes).
6. **Data race** (same variable without the edges above): the behavior is
   defined as **reading the most recent value in synchronization
   order** (SC) — never a "torn" value for one-word types
   (Int/Bool/Char/refs). `Long`/`Double` on 32-bit native targets do not
   exist (targets are 64-bit) — no word-tearing on any target.
7. **lock:** leaving a `lock (x) { }` block HB the next entry into a
   `lock (x) { }` block on the same object `x`, by any flow (HB = lock
   edge). Two flows never run blocks locked on the same `x` at the same time;
   the flow that holds the lock may enter it again (reentrant).

## 3. Per-target mapping (how the spec is honored)

| Target | Flow (green thread) | Synchronization primitive | HB guaranteed by |
|---|---|---|---|
| **JVM** | virtual thread (`Thread.startVirtualThread`, 21+; platform on ART) | JMM | `CompletableFuture` (spawn/await), `LinkedBlockingQueue` (Channel), `ReentrantLock` per object (`lock`) — all guarantee HB per JMM §17.4 |
| **Native x86_64** | `clone(220)` with shared heap | x86-TSO (strong hardware) | futex (`SYS_futex` 202) WAIT/WAKE on spawn/await/channel; lock `lock cmpxchg`; `lock (x)`: a reentrant table of held locks under a futex guard (`RuntimeLock`) |
| **Native riscv64/aarch64** | inherits x86 via translator | weak — needs an **explicit fence** | futex + `fence rw,rw` / `dmb ish` at the HB points (already emitted by the runtime: `amoswap` acq/rel, `fence`→`dmb ish` in the translator) |
| **Interp/Script** | virtual thread (same JVM runtime) | same as JVM | same as JVM |

**Parity rule (R5/R6):** any new target must prove the 5 HB edges
of §2 with the E2Es of §4 before leaving `experimental`.

## 4. Conformance proofs (DoD)

1. `spawnWriteBeforeAwaitIsVisible` — parent writes `x=41`, spawn reads and adds,
   await returns 42 (edge 1+2).
2. `channelSendHappensBeforeReceive` — send of a constructed value, receive
   observes the constructor's side effect (edge 3).
3. `staticsAreSequentiallyConsistent` — N spawns incrementing the SAME static
   N×1000 times; exact final result N×1000 (edge 5, atomicity per
   field).
4. `noWordTearingOnLong` — Long static written by 1 flow, read by another
   in a loop, never observes a partial value (edge 6).
5. Cancellation: `cancel` followed by `cancelled()` observable in the task
   (edge 4) — covered by the existing handle tests.

> **Current state of the proofs (validated 10/09):** (1)(2)(5) already covered by
> `SpawnE2ETest`/`KofConcurrency2Test` (channel JVM+Native, spawn/await
> cross-target). **(3) `staticsAreSequentiallyConsistent` (4×sum 0..999 =
> 1998000, HB of await) and (4) `noWordTearingOnLong` (reader never observes a
> partial value) IMPLEMENTED** — `KofConcurrency2Test:699/:737`, green in the
> suite (29/0/1-skip qemu). All 5 HB edges of §2 have proof.

> **Current state of the proofs (validated 10/09):** (1)(2)(5) already covered by
> `SpawnE2ETest`/`KofConcurrency2Test` (channel JVM+Native, spawn/await
> cross-target). **(3) `staticsAreSequentiallyConsistent` (4×sum 0..999 =
> 1998000, HB of await) and (4) `noWordTearingOnLong` (reader never observes a
> partial value) IMPLEMENTED** — `KofConcurrency2Test`, green in the
> suite. All 5 HB edges of §2 have proof.
>
> **⚠️ Amendment 14/09 — rule 5 now has a proof and a backend fix.**
> `stopFlagFieldWriteObservedBySpinReader` +
> `stopFlagCapturedBoxObservedBySpinReader` (`KofConcurrency2Test`) are the
> stop-flag pattern of DD-OTP-08: instance field (and captured-Box closure
> field) written by one flow, spun on by another, with the writer's write at
> +100ms inside a 500M-iteration loop. **Pre-fix (measured, 3/3):** the field
> was emitted plain (`BoxClassFactory` `AccessFlags.PUBLIC`; no `ACC_VOLATILE`
> anywhere in the compiler) and C2 hoisted the `getfield` out of the loop —
> the reader output `nao-observou`, i.e. `.stop()` would never be observed by
> a long-lived worker. **Fix (root cause, same unit):** every **mutable**
> (non-`final`) field of a Kof class is now emitted with `ACC_VOLATILE`
> (`JvmBackend` field emission; `AccessFlags.VOLATILE = 0x0040`, the §4.7
> field-flag bit that shares the `BRIDGE` method-flag value). `final`
> fields (records, closure refs) keep their semantics. This makes the CODE
> conform to rule 5 of §2 (ratified 09/09) — the spec was already law, the
> code was the deviation. Post-fix, `observou` proves the edge on the JVM.
> JS/interpreter ride the JVM runtime (§3); x86-TSO needed no fence for this
> pattern (rule 5's SC); riscv/aarch inherit via the translator (§3).
> **DD-OTP-08 (supervisor `.stop()`) is UNBLOCKED**: the flag the supervisor
> sets is now guaranteed visible to the worker without `volatile` as language
> surface (still a §5 non-goal; the abstraction carries the proof).

## 5. What is NOT specified (non-goals)

- Explicit `volatile`/`synchronized` **modifiers** on the language surface —
  Kof does not expose mechanism (rule 1: intention, not mechanism); they stay
  a warning (SEM091). The correct abstractions are Channel (messages, not
  memory) and, since 06/11, the `lock (target) { }` block (maintainer decision,
  G240): a scoped critical section over one object, with the HB edge of §2
  rule 7, released on every exit. Unlike a method modifier it names the
  object it excludes on and cannot leak the lock past the block.
- Preemption with a quantum — green threads are cooperative at scheduling
  points (await/channel/time), with no guaranteed time slice.
- Relaxed/acquire-release model exposed to the user — SC-only is the spec;
  relaxing it would be a contract break (bump + discussion).
