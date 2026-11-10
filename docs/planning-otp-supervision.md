[English](planning-otp-supervision.md) | [Português](planning-otp-supervision.pt_BR.md)

# planning-otp-supervision.md — OTP-style worker supervision (`one_for_one`) — CONCLUDED (ported to the 4 targets 19/09)

**Owner:** CONC lane · **Status:** core implemented and green on all targets — JVM+Script (11/09), **S2-JVM ✅ 13/09**, **S2-Native x86 ✅ 15/09** (§129 closed, DECISIONS §2 option B, TLS local-exec), **JS ✅ 18/09** (§132 resolved, `OTP002` lifted), **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table `kof_exc_slots`, `OTP001` gate removed). Concluded: promoted from `docs/development/`.
**Created:** 10/09 · **Amended:** 11/09 · **Issue:** #83 (ViniciusKoiti)

> **11/09 amendments** (verified in the `beta-0.4.0` code, marked
> inline as "⚠️ Amendment 11/09"): §Separation of responsibilities (new),
> DD-OTP-01, 02, 03, 08, 09, 11 and slice S2. The common reason for the two
> material amendments: `selectAny` **does not exist** on riscv64/aarch64, and the
> shutdown flag depends on an SG-020 rule that still has no proof.
>
> **16/09 note (doc sync against the code — both reasons changed):** the
> helpers now **do exist** on riscv64/aarch64 (`CONC001` closed 15/09 by
> `e8364c97` — slice `NativeRiscvAsmRtB48`, qemu proof on both arches), and
> the SG-020 flag has proof (`ACC_VOLATILE` fix + DD-OTP-08 implemented 15/09).
> What still blocks supervisor on cross is `OTP001` alone: raw `clone` without
> TLS for the §129 per-thread handler chain.
>
> **19/09 — port CONCLUDED (mechanism corrected after measurement):** the
> maintainer's chat decision was **real TLS via `clone`** (recorded in
> `DECISIONS.md` §129), but implementing it **proved ABI-unsafe**: overwriting
> the thread pointer (riscv `tp`=x4 / aarch64 `TPIDR_EL0`) desynchronizes the
> C library's own TLS — SIGSEGV (exit 139) in aarch64 tests calling
> `snprintf`/`strtod` through `RuntimeDtoa`, and a riscv regression in
> `crossNativeConcurrencyHelpersRun`. Root cause: our `_start` is our own, but
> a Kof program can still call libc. **Resolution (mechanism deviation,
> contract unchanged):** per-TID chain table `kof_exc_slots` (256 × 16 B
> `[tid, chain]`, key `gettid`=a7 178, linear probe, same pattern as
> `kof_cancel_slots`/CONC001) + `kof_exc_slot()` helper; the 8 chain-access
> sites now call it; the trampoline installs the per-worker handler node and
> publishes the cause on `handle->exc` (offset 48), rethrown by `await`/
> `await_timeout`/`select_any`; `kof_spawn_join_all` does not rethrow. The
> aarch64 `tp`→x4 collision becomes irrelevant (we no longer touch `tp`).
> **`OTP001` gate removed** — the supervisor core is delivered on
> riscv64/aarch64. RED baseline measured: two cross tests mirroring the x86
> §129 hang under qemu (worker `throw` longjmps the global `kof_exc_chain` of
> `main`) — now green (see the addendum below).

## Decision status (11/09)

| DD | Subject | Status |
|---|---|---|
| 01 | Shape (pure-Kof stdlib) | ⚠️ closable **with amendment** — missing the specification of stdlib packaging in `.kf` |
| 02 | Surface (API) | ⚠️ closable **with amendment** — the signature must declare the layer |
| 03 | N workers without blocking | ✅ CLOSED 19/09 — `selectAny` EXISTS on riscv/aarch (CONC001, `e8364c97`) and `OTP001` is gone (§129 cross port, per-TID chain table); the supervisor loop observes failure via `try { await } catch` on every target |
| 04 | What counts as a failure | ✅ closed |
| 05 | Plan or tree | ✅ closed |
| 06 | State on restart | ✅ closed |
| 07 | Escalation | ✅ closed (text contradiction resolved) |
| 08 | Shutdown | ✅ IMPLEMENTED 15/09 — cooperative flag live in the host (`KofSupWrap.parar` + `kofSupShouldStop`), `stop()` writes the flag before `cancel`; S4 E2E JVM+interpreter 13/13 |
| 09 | Targets | ✅ CLOSED 19/09 — the core is delivered on JVM, Script, Native x86, riscv64, aarch64 and JS |
| 10 | Injectable clock | ✅ closed |
| 11 | Success metric | ✅ closed (four gates) |
| 12 | Where the tests live | ✅ closed |
| 13 | `cancelled()` collision | ✅ closed (conditional on 08) |

**11 closed · 2 closable with amendment · 0 open · 0 blocked.**

> **✅ RATIFIED 13/09 (maintainer's decision):** DD-OTP-01/02/03/04/08/09/10/11/12/13 ratified in the form of the doc's proposal + 11/09 amendments. **DD-OTP-03 decided: option 1a** — S2 opens on the JVM with an identity wrapper `(id, result)` per child; riscv64/aarch64 = **declared PARTIAL** (1 worker/supervisor) until the nat lane ports the helpers (gate R6).
The 8 are independent of each other — they can be ratified without waiting for the others.
The critical path is only **DD-OTP-08**, and it resolves with a test, not
with design: the stop-flag pattern (writer + reader in a loop with deadline).

Issue #83 brings the complete and correct OTP-vs-Kof analysis. This document
**verified each of its claims against the current code** (all confirmed —
see §Facts) and turns the 13 open boxes into **numbered DDs** with a technical
recommendation per DD. The decision is the maintainer's; the document exists
so that the decision fits in one reading.

## Facts verified in the code (not memory)

| Issue claim | 10/09 verification |
|---|---|
| `spawn`/`await` desugar to `__kof_spawn_expr`/`__kof_await` (no AST node) | `parser/ExpressionParser.java:96-107` ✅ |
| implicit join (a non-daemon keeper thread waits for tasks; `process.exit` does not) | `jvm/JvmRuntimeCore.java` `KOF_ACTIVE_TASKS` + `kofTaskBegin`/`kofTaskEnd` ✅ |
| `cancelled()` native = flag per TID, 256 slots collision | `runtime/RuntimeConcurrency.java:18` (`.space 256`) ✅ |
| `awaitTimeout`/`selectAny` in native = 1ms polling | `usleep(1000)` at `:212,:341` ✅ |
| `CONC003-JS-01`: handler-lambda (mq/timer/UI) cannot `await` | `CompilerPipeline.java:78` + `docs/language-reference/concurrency.md:139` ✅ — BUT a `spawn` lambda CAN (it is a task-lambda; `spawn { await ... }` is the use covered by test) |
| no `sigaction` in Native | zero occurrences in `runtime/`+`nat/` ✅ |
| `cancelled()` JS/interpreter always 0 | `docs/backend-parity.md:84` ✅ |

Material NEW fact: the **pure-Kof** supervisor (no new runtime) runs on the
spawn-task — the `CONC003-JS-01` restriction does not reach it. And the shared
heap (the defect for a clean restart) is the lever of cross-target cooperative
shutdown: a `Bool` captured in a `Box` is visible to the worker
on ALL targets, without depending on the `cancelled()` that is broken in 2 of the 5.

## Separation of responsibilities (supervisor / runtime / worker)

> ⚠️ **11/09 Amendment** — new section. It is the "main point" declared by the
> maintainer in the #83 discussion and it was not in the document. The DDs say
> *where the code lives*; this table says *who guarantees what*. Without it, the
> feature tends to become a per-backend mechanism.

| Layer | Responsibility | State today |
|---|---|---|
| **Supervisor** | track children, detect termination, apply restart policy, control shutdown | does not exist |
| **Runtime** | provide observable termination, cancellation and time | partial — termination only via `Handle`+`await`; `spawn` as a command **swallows** the exception (`JvmRuntimeCore.java:195-205`); `cancelled()` = `0` on JS/interp; `time.now()` ok |
| **Worker** | cooperate: check the stop flag and be reconstructible by the factory | contract today implicit — must become documented |

Two boundaries that are blurred today and that this separation fixes:

1. **Policy in the runtime.** `kof_spawn` decides on its own what to do with the
   failure (prints to stderr and moves on) — that is a policy decision inside the
   mechanism layer. The supervisor only sees what goes through
   `spawn expr` + `Handle`; `spawn` as a command is invisible to it.
   **Consequence for the API:** every supervised child must be born
   as a `spawn` expression, and that is contract, not detail.
2. **Responsibility without contract in the worker.** `cancelled()` and the flag of
   DD-OTP-08 only work if the worker checks. That must be written in the
   signature of `.child(...)`, not in the text of a tutorial.

## The DDs (each: options + recommendation)

### DD-OTP-01 — Shape: where the logic lives
- **A (recommended): pure-Kof stdlib** (`kof.supervisor`, object like
  `kof.mq`/`scheduler`) implemented WITH the language: list of factories,
  loop `spawn`+`await`+`try/catch` + window with `time.now()`. Zero asm,
  zero parser/typer change, **parity by construction on the 5 targets**,
  survives a backend swap, WASM for free. The "supervisor" is a Kof
  object; whoever runs it is a `spawn` from the user (or from the stdlib itself).
- B: runtime per backend (5) with `whenComplete` on the JVM — notification without
  polling, but cost in asm ×5 and risk of divergence (rule 5). Only justified
  IF A is insufficient (measure later; probably not).
- C: new keyword — **reject**: frozen surface (rule 6, 0.2.6) +
  irreversible. Builtin in the typer (like `selectAny`) is shadowable — no.
- Complexity belongs to the platform, and the Kof platform already has
  spawn/await/try-catch: the supervisor is **Kof code**, not a VM feature.

> ⚠️ **11/09 Amendment — the packaging mechanism is missing.** The decision (A)
> is right, but the analogy *"object like `kof.mq`/`scheduler`"* does not
> hold up: `kof.mq` and `kof.scheduler` **are not pure Kof** — they are
> `KofMq.java` and `KofScheduler.java`, Java code in the compiler with runtime
> emitted per backend. **There is no stdlib module written in
> `.kf` today.** The only precedent of packaged Kof source is
> `kof-compiler/src/main/resources/dev/kof/android-host.kf` (resource).
> **Pending:** specify how a stdlib in Kof is compiled and linked to the
> user's program on each target. Until that is written, the
> "zero backend code" of slice S1 is underestimated.

### DD-OTP-02 — Surface (API)
Proposal (minimal, additive, experimental tier R5):
```kof
supervisor(name)                                  // object
  .child(id, factory, policy)                     // permanent|transient|temporary (String)
  .restartLimit(max, windowMillis)
  .clock(nowFn)                                   // DD-OTP-10 (injection for test)
  .escalate(fn)                                   // DD-OTP-07
  .start()                                        // spawn supervisor-thread
  .stop()                                         // DD-OTP-08
supervisorStats()                                 // {started, restarts, dropped} for test/E2E
```
No named monitor / tree (DD-OTP-05) — minimal stack first.

> ⚠️ **11/09 Amendment — the API must declare the layer.** The surface lists
> methods but does not say what the supervisor *promises*, what it *requires from
> the runtime* and what it *requires from the worker*. Since the separation of
> responsibilities is the central point of the proposal, that belongs in the
> signature: `.child(id, factory, policy)` must document that `factory` **creates**
> the worker (DD-OTP-06) and that the worker **must check the stop flag**
> (DD-OTP-08) for `.stop()` to have effect.

### DD-OTP-03 — N workers without blocking
**N per supervisor (recommended):** the supervisor thread runs
`while (ativos) { await handle_morto }` — but `await` blocks on only one.
Solution with what exists: the stdlib already has `selectAny` (multi-join handle,
Native polling, JVM notify) — supervisor = loop `selectAny(handles)` → the
dead one is restarted → re-registered. Cost per child: zero extra threads (1
supervisor thread). Alternative (supervisor thread per worker) is 1-thread
per child — acceptable on JVM virtual-threads, worse on x86. I recommend
`selectAny` first; measure.

> ⚠️ **11/09 Amendment — `selectAny` does not exist on two targets.**
> `nat/NativeRiscvSpawn.java` emits **only** `kof_spawn_result`,
> `kof_spawn`, `kof_await` and `kof_spawn_join_all`. There is no
> `kof_select_any`, `kof_poll`, `kof_done`, `kof_cancel` nor
> `kof_await_timeout` in any riscv/aarch64 emitter — and **there is no
> compile-time gate**, so the use falls into a link error for an undefined symbol
> (bug 59 pattern), not into an honest gap. **Amendment:** the mechanism of N
> workers needs a declared fallback — one supervisor thread per child
> where `selectAny` does not exist — or riscv/aarch are limited to **one
> worker per supervisor**, registered as PARTIAL.
>
> **✅ 16/09 sync — premise dead:** on 15/09 the symbols were ported
> (`e8364c97`, fatia `NativeRiscvAsmRtB48` — `kof_select_any`/`kof_poll`/
> `kof_done`/`kof_cancel`/`kof_await_timeout` on riscv64 + aarch64, qemu
> proof `KofConcurrency2Test.crossNativeConcurrencyHelpersRun`). The
> fallback decision no longer needs to handle "helper missing" — only the
> `OTP001` throw/TLS gap (the supervisor loop observes failure via
> `try { await } catch`, which still longjmps the global chain on cross).

### DD-OTP-04 — What counts as a failure

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> failure = uncaught exception (String) that escapes the worker's body.
> Normal termination is a failure **only** in `permanent`. Heartbeat/hang stays
> out, in its own queue (`OTP002`).
>
> **Accepted and documented limitation:** exceptions in Kof are `String`, with no
> type hierarchy, and on Native the first `catch` always catches — so
> the restart policy **cannot discriminate by error type**, only by the
> policy declared on the child. That is explicit contract, not omission.

- uncaught exception (String) → failure (the only real cross-target signal);
- normal termination → is **not** a failure in `transient`/`temporary`, is a failure in
  `permanent` (OTP semantics, translatable 1:1 with `await` that returns);
- **stuck without crash (heartbeat): OUT of the initial scope** — it is another
  feature (heartbeat+timer), and the issue is right: a connection that hangs is the expensive
  case. Own queue (`OTP002` if promoted), never half-implemented.

### DD-OTP-05 — Plan or tree

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> flat supervisor, no nesting. The response to exceeding the limit is the
> DD-OTP-07 callback, not a parent. The child abstraction is born generic enough
> to nest later without breaking the API. Tree only with real demand.

**Flat, no nesting (recommended for the 1st slice).** A tree requires
supervisor-as-child + semantic escalation between parents — it pulls in the
entire surface. Without a tree, the response to exceeding the limit is the
DD-OTP-07 callback. Re-evaluate only with real demand.

### DD-OTP-06 — State on restart

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> the restart **always** recreates the worker through the factory; it never reuses the
> state that led to the failure. Confirmed by the maintainer in the #83 discussion.
> `.child(id, factory, policy)` documents in its own signature that
> `factory` **creates** the worker. Without heap isolation it is the strongest that
> can be guaranteed — restarting over the old closure is a crash-loop until the limit.

**Require a factory that rebuilds (recommended and locked in the API):** the type of
`child(id, factory, ...)` documents `factory = () -> worker` that **creates** the
state (new records/vars per restart). There is no way to do better without
heap isolation (BEAM-heap is the pillar we do NOT import); restarting over
the old closure = crash-loop until the limit (the trap the issue
points out). Document in training: "new factory = new state".

### DD-OTP-07 — Escalation (exceeding the limit)

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> `.escalate(fn)` is **optional, with a defined default** — not mandatory.
> This resolves a contradiction in the original text, which asked for a *"mandatory
> callback in start() if there is a limit"* and on the next line described a
> *"default without `.escalate`"*: if it were mandatory, there would be no default.
>
> - **with** `.escalate(fn)`: on exceeding the limit the supervisor calls
>   `fn(id, reason, count)` on the supervisor thread and stops restarting
>   that child — the user decides what to do;
> - **without** `.escalate`: R6 diagnostic on stderr and the supervisor stops
>   restarting that child.
>
> In neither case does the supervisor kill the process, and in neither does it
> give up silently (R6).

Without a parent in the tree, the honest options: (a) kill the process — brutal, and
`process.exit` is interop (not pure-stdlib); (b) give up silently —
**forbidden (R6)**; (c) **`.escalate(fn)` callback mandatory in start() if
there is a limit (recommended):** the supervisor calls `fn(id, reason, count)`
on the supervisor thread — the user decides (log, change strategy,
exit). Default without `.escalate`: R6 diagnostic on stderr + supervisor
stops (does not restart, does not kill the process). Zero new keyword.

### DD-OTP-08 — Shutdown (`stop()`)
**Cooperative by Kof flag (recommended):** `.stop()` sets a captured `Bool`
(visible in the shared heap — works on JS/interpreter where
`cancelled()` is 0) + deadline drain: waits for children until
`stopDeadlineMillis` via `awaitTimeout`, then gives up (diagnostic,
never silent). The runtime's `cancel(handle)` is a bonus where it works (JVM/
x86), never the only lever. The implicit join is released when the supervisor's
loop ends — nothing new here, just honesty in the deadline.

> ⚠️ **14/09 — UNBLOCKED (supersedes the 11/09 BLOCKED note below, kept for
> history).** The missing proof now exists: `stopFlagFieldWriteObservedBySpinReader`
> + `stopFlagCapturedBoxObservedBySpinReader` (`KofConcurrency2Test`) reproduce
> the stop-flag pattern and, pre-fix, FAIL exactly as the amendment predicted —
> the plain field let C2 hoist the `getfield` (measured: `nao-observou` 3/3 with
> the write at +100ms inside a 500M-iteration loop). Root-cause fix in the same
> unit: **every mutable (non-final) field of a Kof class is emitted
> `ACC_VOLATILE`** (`JvmBackend`; the spec's rule 5 was already law — the code
> was the deviation). Post-fix: `observou`, both paths. The volatile field the
> supervisor's stop-flag rides on is therefore guaranteed visible on the JVM;
> details in the SG-020 amendment 14/09. What remains here is **implementation
> only**: wire the captured flag into `supervisor-host.kf`'s worker contract
> (factory-reconstructible worker that checks it) + deadline drain E2E.
>
> ✅ **15/09 — IMPLEMENTED (lane development, dono 192.168.100.18).** The
> cooperative flag is live in the host: `KofSupWrap.parar` (mutable field →
> `ACC_VOLATILE` per SG-020) + `kofSupShouldStop(wrap)` query for the worker's
> loop + `stop()` writes `wrap.parar = true` on every live child **before**
> `cancel(handle)` (cancel stays the bonus lever, flag is the contract one).
> `KofSupNode.wrap` carries the current lap's wrap so `stop()` reaches the
> running worker even between laps. Proof: `KofSupervisorE2ETest` 11→13
> (`stopFlagWorkerParaNoDrenoComDeadline` JVM + `stopFlagWorkerNoInterpretador`
> — long-running worker in a loop consulting the flag exits the loop on
> `stop()`, drain finishes inside the deadline, both targets green). The
> **worker contract is now written in the host**: a cooperative worker loops on
> `kofSupShouldStop(wrap)`; a worker that never checks is still drained by the
> deadline with the R6 diagnostic (pre-existing behavior, unchanged).

> ⚠️ **11/09 Amendment — BLOCKED: the flag has no visibility guarantee.**### DD-OTP-09 — Targets
Pure-Kof (DD-OTP-01-A) = **JVM + Script + JS + Native x86 + riscv/aarch for
free** (everything uses only the already existing spawn/await/selectAny). Without `OTP001` —
no new gap is born from this feature (the JS-01 restriction does not reach
task-lambda; record in the parity doc). `selectAny` polling on x86 is the
regime cost, not a correctness cost.

> ⚠️ **11/09 Amendment — the declared coverage is wrong.** "riscv/aarch for
> free" is false: `selectAny`, on which DD-OTP-03 depends, does not exist
> on those targets (see the DD-OTP-03 amendment). This contradicts the principle
> stated by the maintainer — *"it makes no sense to define an API that
> works well on one backend and is merely a promise on the others"*.
> **Real coverage:** JVM + Native x86_64 + JS + Script. **riscv64/aarch64
> = declared PARTIAL**, limited to one worker per supervisor until someone
> ports the helpers (Native lane work, together with the R6 gate that
> is also missing today).
>
> **✅ 16/09 sync — the helpers were ported (15/09, `e8364c97`), but the
> coverage holds as declared for a different reason.** riscv/aarch remain
> PARTIAL because of `OTP001`: the supervisor loop observes failure via
> `try { await } catch`, and on cross a `throw` in a worker still longjmps
> the global handler chain (the §129 TLS fix is x86-only). JS is **resolved
> 18/09** (§132 closed — `OTP002` lifted; the supervisor runs on JS to parity). The
> "one worker per supervisor" limit stays until the TLS port closes on the **riscv/aarch**
> Native lane (rule 6: it is that lane's front).

### DD-OTP-10 — Injectable clock

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> `.clock(nowFn)`, default `time.now()`. Cost of one field. It is what makes the
> restart-limit gate deterministic on all targets, and what makes
> a cross test under qemu measure the supervisor instead of measuring the emulator.

**Yes (recommended):** `.clock(nowFn)` default `time.now()`. The window test
runs deterministically on all targets, without waiting for wall-clock
(qemu distorts time — the issue is right). Cost: 1 field.

### DD-OTP-11 — Success metric

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> the **four gates** of the amendment below are the closing criterion of the
> minimal core. The normal-regime budget is **measured, not promised**.
> Throughput under failure stays out (it would require a new harness — `kof bench` only
> measures wall-clock of a short process).
>
> **Explicitly recorded:** supervision **is not a performance feature**.
> In a failure-free regime, with a supervisor it is equal to or worse than without. The gain is
> availability — and the overhead benchmark exists to prove that the cost
> is small, not to show a gain.

**Boolean containment gate (recommended to close the feature):** E2E —
a worker that throws on the 1st invocation and terminates on the 2nd → the supervisor restarts,
the program completes, `supervisorStats().restarts == 1`. Normal-regime
budget: empty supervisor + 1 ok worker adds <5ms to boot
(measured, not promised). Throughput under failure: OUT (new harness —
own queue if requested).

> ⚠️ **11/09 Amendment — there are four gates, not one.** The maintainer named
> explicitly the tests that close the minimal core; the text above covers
> only the second:
>
> | Gate | Proof | Touches |
> |---|---|---|
> | Failure observation | the supervisor **knows** the child died | `spawn expr` + `Handle` |
> | Individual restart | only the dead one restarts; the others continue | `one_for_one` + `.child(...)` |
> | Restart limit | N restarts in window T stop the cycle | `.restartLimit` + `.clock` |
> | Controlled shutdown | `.stop()` actually terminates, without hanging on the implicit join | `.stop()` + DD-OTP-08 |
>
> The fourth gate depends on DD-OTP-08, today blocked.

### DD-OTP-12 — Where the tests live

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> deterministic E2E with an injected `.clock()`, **outside** the equality
> matrix — the construct is non-deterministic by nature, like `random.*`/`uuid`.
> Parity by contract asserts on the targets, a pattern already established in
> `KofRandomTest`. CI: the current target matrix is enough; the JDK axis (platform
> thread fallback) and `bench`/long-run stay in their own queue.

Deterministic E2E with injected `.clock()` (does not conflict with the matrix
exclusions: it **does not enter** the equality matrix — it is non-deterministic by
nature, like `random.*`/`uuid`; parity by contract asserts on the 5
targets, a pattern already established in `KofRandomTest`). CI: the current target matrix
is enough (do not require a new JDK axis — register the platform-thread fallback as a
separate, smaller test; `bench`/long-run are their own queue).

### DD-OTP-13 — `cancelled()` collision (256 slots)

> **✅ DECIDED — closing proposal 11/09, RATIFIED 13/09 (see top of the doc):**
> bug 101 (flag per `TID % 256`, two workers can inherit each other's
> cancel) is **independent** work of the Native lane and **does not block**
> supervision — **as long as** DD-OTP-08 uses its own flag. If DD-OTP-08
> falls back to the runtime's `cancel`, this item **becomes a blocker**.
> Dependency explicitly recorded.

Independent bug (the issue asks for a record): **bug 101 in `known-bugs.md`** —
flag per `TID % 256`, two workers can inherit each other's cancel. A possible fix
(larger table + chaining by handle pointer) belongs to the Native
lane; small and isolated. It does not block OTP (which uses its own flag).

## Proposed slice (if the maintainer approves the design)

1. **S1 (1 session):** pure-Kof `kof.supervisor` in the stdlib runtime +
   `.child/.restartLimit/.clock/.start/.stop/.escalate` + JVM/JS E2E
   (DD-OTP-11 gate) + doc/learn/training. Zero backend code.
2. **S2:** Native x86 (selectAny loop) + Script. ⚠️ **11/09 Amendment:**
   riscv/aarch do **not** come out by construction — `selectAny` does not exist there
   (DD-OTP-03/09). Either they enter with a fallback of one supervisor thread per
   child, or they stay PARTIAL with one worker per supervisor.
   *(16/09 sync: `selectAny` exists on cross since 15/09 — the blocker is now
   only `OTP001` (§129 TLS), so the "one worker per supervisor" option is the
   live one, and the fallback decision is re-opened on the maintainer's queue.)*
3. **S3:** ✅ EXECUTED 14/09 (lane development, owner 192.168.100.18): `stats()` (`started`/`restarts`/`dropped`/`vivos`) + sliding ring window (`restartLimitWindow(max, windowMs)`, ring per child) + injectable clock (`.clock(nowFn)`, DD-OTP-10) + `temporary` drop accounting (`stats().dropped`). Proof: `KofSupervisorE2ETest` 11/11 (window expiry with virtual clock = deterministic, no wall-clock; temporary drop counted on JVM+Script). Original item: `supervisorStats` + ring window + `temporary` drop + parity
   docs; promote to stable only with the complete gate matrix (R5).

## What this plan is NOT

- Tree of supervisors / named monitors / link-monitor (DD-OTP-05 postpones).
- Heartbeat against hang (DD-OTP-04 — `OTP002` queue).
- Heap isolation (it is not Kof — see R12/small core; the choice is
  a new factory, not a new heap).
- WASM/JS Worker: no SharedArrayBuffer today; the pure-Kof supervisor already runs
  single-threaded on JS (Promise) — honest, documented parity.

## Measured spike + 1st slice (11/09 — facts, not memory)

- **Shape DD-OTP-01-A (pure-Kof) CONFIRMED viable and delivered** as a virtual
  package `kof.supervisor` (host `dev/kof/supervisor-host.kf` written in Kof,
  injected only on `import kof.supervisor` — the android-host mechanism; the
  DD-OTP-01 foresaw "object like kof.mq/scheduler"; the .kf injection resolves the
  plan's distribution question without a Java backend ×5).
- **Factory = interface** (DD-OTP-06 "always a new factory"): a field/param of
  function type is broken (§127 cast `as ()->T` → VerifyError; PARSE016 on
  field `() -> Int`), and primary `class X(...)` is an immutable record. `interface
  KofWorkerFactory { KofWorker novo() }` runs on the viable targets.
- **Observation via `try{await}catch` in a `vigiar` loop PER CHILD** (dedicated
  thread), NOT polling `done`/`selectAny`: `selectAny` of a primitive breaks on the
  JVM (§128) and handle-failure polling is fragile without preemption (§132 on JS).
  This is the "supervisor thread per worker" alternative of DD-OTP-03 — the
  single-`selectAny` one was left for when §128/§132/§129 close.
- **Impediments that had to be resolved/workaround:** §130 fixed
  (false SEM024 in a re-analyzed method body — it blocked the fluent builder);
  §131 worked around (overload by arity broken → single 3-arg `child`).
- **Honest parity:** JVM + Script + **Native x86** + **JS** run the core
  (riscv/aarch were blocked at compile-time with `OTP001` until 19/09 — see the
  conclusion; NEVER a binary that hangs, rule 6). **S2-JVM of the plan
  IMPLEMENTED 13/09** (`020be966`, option 1a): `Supervisor.startAll()` + single
  selectAny loop with an identity wrapper (id/reason); S2 JVM+interpreter gates;
  `KofSupervisorE2ETest` 8/8, gate 1620/0. **S2-Native x86 ✅ IMPLEMENTED 15/09**
  (§129 closed, DECISIONS §2 option B;
  `supervisorNativeParityX86`/`supervisorNativeS2ParityX86`). **S2-JS ✅
  IMPLEMENTED 18/09** (§132 closed — cooperative async `time.sleep`; `OTP002`
  lifted; `supervisorJsParity`/`supervisorJsS2Parity`). **riscv64/aarch64 ✅
  19/09** (§129 cross port — per-TID chain table).

## Update 12/09 — REAL state of the S2 impediments (doc-vs-reality)

- **§128-unbox JVM ✅ FIXED 12/09** (`6e68cb36`, 02:22 — `JvmOpCollections`
  extends the `kof_await` unbox to `kof_select_any`; proof
  `KofConcurrency2Test#selectAnyPrimitiveJvm` + 4-target parity).
- **§127-cast function type ✅ FIXED 13/09** (decision 9a — `as ()->T` parses
  as a type-ref; the "is broken" note in the block above is historical) and
  **§131-overload ✅ DECIDED 13/09** (option 10a: implement — the workaround
  "single 3-arg `child`" can be revisited when the types lane implements it).
- **§129-longjmp Native ✅ FIXED 15/09** (DECISIONS §2 option B: TLS per-thread
  `kof_exc_chain` + per-worker handler frame in `kof_spawn_trampoline`; the worker
  publishes the cause on the handle and `await`/`selectAny` rethrow it — x86_64;
  riscv/aarch stay `OTP001`, raw `clone` without TLS) — S2-Native x86 unblocked.
  **§132 event-loop JS ✅ FIXED 18/09 (#83-JS)** — `time.sleep` is now an await-point
  (cooperative async sleep: compiler colors the reaching method async, `kofTimeSleep`
  returns a Promise, the `KofJsRunner` host pump drives it), so a worker spawned inside
  another task runs while the poller sleeps — S2-JS unblocked, `OTP002` lifted,
  `supervisorJsParity` green. (Had been mis-listed under bugs/UI; it landed on the
  KofJS/dev lane.)
- **Design hole that the §128 fix does NOT close (measured, not
  memory):** the JVM `selectAny` (`JvmRuntimeCore.kof_select_any` →
  `CompletableFuture.anyOf().get()`) returns the **value** of the first ready
  handle — **it does not return WHICH handle finished nor distinguish normal
  termination from failure** (a failure propagates as an exception without an id). For
  individual one_for_one the supervisor needs the pair `(id, reason)` of the dead one; that
  requires a wrapper per child that reports the identity (e.g. task-pair
  `(id, result)`) — **DECIDED 13/09 (option 1a)** + **IMPLEMENTED**
  (`020be966`: `Supervisor.startAll()` + single selectAny loop; 1 supervisor
  thread; `KofSupervisorE2ETest` 8/8).
- **Ratification:** ~~awaiting~~ **✅ RATIFIED 13/09** (see top of the doc). **S2-JVM IMPLEMENTED 13/09** (option 1a): `Supervisor.startAll()` + `lacoUnico()` in `supervisor-host.kf` — 1 supervisor thread, `selectAny(handles)` over the live children; **identity wrapper** `kofSupRun` returns the id (normal termination) or throws `"id: reason"` (failure → parse of the pair `(id, reason)` in the loop). New gates: `KofSupervisorE2ETest.supervisorS2TresFilhosUmLacoSelectAny` + `supervisorS2NoInterpretador` (8/8 green). **S2-Native x86 ✅ IMPLEMENTED 15/09** (§129-TLS closed — DECISIONS §2 option B).

## Conclusion — CONCLUDED 19/09 (ported to the 4 targets)

- **§129 cross port ✅ (riscv64/aarch64):** the per-TID chain-table mechanism
  `kof_exc_slots` + `kof_exc_slot()` replaced the unsafe TLS-via-`tp` approach
  (see the 19/09 note at the top and known-bugs §129 addendum). The
  `CompilerSupervisor` injected raise (`OTP001`) is **gone** — `kof.supervisor`
  now compiles and runs on the 4 native/`node` targets.
- **Gates (19/09, qemu on riscv64/aarch64):** `KofSupervisorE2ETest.crossGateOtp001`
  runs the APP on both arches and asserts `restarts=2`, `escaladas=2`,
  `fabrica=3`, `parou vivos=0` — 16/16 together with the parallel gates; the
  §129 pair `KofConcurrency2Test.spawnWorkerThrow{IsolatedFromSiblings,
  UnhandledPropagates}CrossArch` green on both arches. Suite: `KofSupervisorE2ETest`
  16/0, `KofConcurrency2Test` 48/0, riscv/aarch E2E 45/0 each (138/0 run).
- **Promotion:** this document moved to `docs/` 19/09 (planning concluded).
