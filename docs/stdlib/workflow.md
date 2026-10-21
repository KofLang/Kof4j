[English](workflow.md) | [Português](workflow.pt_BR.md)

# Workflows — `kof.workflow`

**Date:** September 19, 2026
**Status:** MVP implemented (universal plan Stage 2, row 2.1, slice 2.1.2) — `VERSION` 0.6.7

> **MVP scope (Q2, maintainer poll 19/09):** `job` / `dag` / `after` / `run` / `Report`.
> **2.1.3 face 1 LANDED 19/09:** `retry` (Q3 — the workflow's own additive helper;
> `kof.http` is NOT touched, its migration is a separate signed slice).
> **2.1.3 faces 2–3 LANDED 19/09 (this slice):** `deadLetter` (Q4 — the TWO faces:
> in-memory `Report.dead` always + opt-in durable sink per job) and `schedule`
> (delegates to `scheduler.at` — D-SCHED-DURATION durations or cron; Native gets
> a loud runtime `CRON001` stub, the scheduler gate itself stays compile-time).
> **2.1.3 face 4 LANDED 19/09 (3a):** `checkpoint` (store over `kof.db`/`kof.orm`;
> Native gets a loud runtime `ORM001` stub).
> **2.1.3 face 5 LANDED 19/09 (3b):** `runSupervised(dag, nome, maxReinicios)` —
> the DAG runs as supervised workers: each job is a `transient` child of a
> `kof.supervisor` (the per-child watcher IS the one_for_one — only the child that
> failed restarts). Restart policy = the supervisor's (plan §3 — the workflow never
> re-implements it); `import kof.supervisor` is NOT required (the host ships with
> the face, deduped by marker when the user also imports it).

---

## 1. What it is

`kof.workflow` is a **composition layer over plain Kof**, written in Kof itself
(resource `dev/kof/workflow-host.kf`) and injected **flat** by the compiler when you
`import kof.workflow` — the same mechanism and the same DD-OTP-01 option A as
`kof.supervisor`. Because the host is a virtual package, the surface is bare
`job(...)`/`dag(...)`; there is **no `workflow.` prefix and no `workflow` identifier**
after the import.

The MVP executes a DAG **sequentially and deterministically** (a fixpoint loop). It
crosses no runtime boundary — no threads, no `await`, no `process` — so there is **no
target gate**: the same source runs byte-identical on JVM and JS and compiles on
Native. Honest gaps (`PROC001`, `CRON001`, `ORM001`) appear only in the job *bodies*
you write, never in the layer.

## 2. Surface

```
import kof.workflow

job(String nome, () -> Bool corpo) -> KofWfJob
KofWfJob.after(KofWfJob dep) -> KofWfJob        // chainable; guards null/self-dep
dag(List<KofWfJob> jobs) -> KofWfDag            // guards empty dag / dup names / null job
KofWfDag.run() -> KofWfReport                   // sequential topological fixpoint
KofWfDag.retry(KofWfJob j, Int times, (Int) -> Int backoffMs) -> KofWfDag
KofWfDag.retryFixed(KofWfJob j, Int times) -> KofWfDag   // immediate, no sleep
KofWfDag.deadLetter(KofWfJob j, (String, String) -> Bool sink) -> KofWfDag  // opt-in durable face
exponential(Int baseMs, Int factor) -> (Int) -> Int       // 19/09: backoff(1)=base, *factor each try
schedule(KofWfDag d, String expr) -> String               // 19/09: delegates to scheduler.at, returns job id
checkpoint(KofWfDag d, String dbConn, String dagName) -> KofWfDag  // 19/09: store = kof.db/kof.orm (entity KofWfCk)
runSupervised(KofWfDag d, String supNome, Int maxReinicios) -> KofWfReport  // 19/09 (3b): DAG as one_for_one supervised workers (kof.supervisor)

Report fields: succeeded failed skipped errors retries dead  // List<String> each
Report.allOk() -> Bool                          // no failures, no skips
Report.summary() -> String                      // "ok=... failed=... skipped=..."
```

Rules:

- A body returns `Bool` (`true` = success). `false` **or a thrown string** fails the
  job; the thrown reason is recorded in `errors` as `"nome: motivo"` (a `false` is
  recorded as `"nome: false"`).
- A failed or skipped job **poisons its transitive dependents** — they are `skipped`
  and never run. Independent branches keep running.
- A **cycle is rejected at run time** with `workflow: ciclo detectado entre: ...`
  (an actionable message — never a silent hang, per the plan §2 invariant).
- `retry(job, times, backoff)`: the body re-runs after `sleep(backoff(attempt))` up to
  `times` extra attempts (throw and `false` both retry); `Report.retries` records
  `"nome: tentativas=N"`, and `errors` keeps the LAST reason if it still fails.
  `retryFixed` is the same with zero wait. Only jobs that are members of the dag can
  be configured (guard message says so).
- `Report.dead` (deadLetter, in-memory face — ALWAYS present): every job that
  exhausted retries lands as `"nome: motivo"` (same reason text as `errors`);
  successful jobs never enter.
- `deadLetter(job, sink)`: the durable face is USER code — the sink
  `(nome, motivo) -> Bool` receives every final failure (persist wherever you
  want, e.g. `kof.orm` in YOUR body; the workflow stays pure and target-neutral,
  never depending on `kof.orm` itself). `false` or a throw from the sink fails
  LOUD with the job name (R6 — a refused dead letter must not vanish). One sink
  per job (re-registering throws).
- `schedule(expr, dag)`: DELEGATES to `scheduler.at` (idiomatic durations
  `30m`/`1d&30m` or 5-field cron — D-SCHED-DURATION) and returns the scheduler
  job id. Each fire runs the WHOLE dag inside `spawn` (JVM = one thread per
  fire; JS = the cooperative pump — the form CONC003 allows inside timer
  callbacks, since `run()` may `time.sleep` on retry backoff). A failing fire
  never kills the scheduler (isolated in the spawn); per-fire persistence goes
  through `deadLetter`, which runs inside `run()`. On NATIVE the slice is a
  stub that fails LOUD at runtime citing `CRON001` (the scheduler gate is
- `checkpoint(d, dbConn, dagName)`: the store REUSES `kof.db`/`kof.orm`
  (entity `KofWfCk`, key `dagName/jobName`, `CREATE TABLE IF NOT EXISTS` —
  idempotent). Restored jobs re-enter as `succeeded` WITHOUT re-running their
  bodies; a save happens once per job after success (the unique key keeps one
  row per job); a refused/throwing save fails LOUD with the job name (R6).
  The connection lives in the dag's closures (no auto-close; for H2 mem use
  `DB_CLOSE_DELAY=-1`). On NATIVE the slice is a stub that fails LOUD at
  runtime citing `ORM001` — your DIRECT `kof.db`/`kof.orm` calls keep their
  own honest gap. PARSER EDGE (measured 19/09): a function-type FIELD after a
  `List<...>` field does not parse (`PARSE023` "Expected parameter name") —
  the ck hooks follow a plain `String dagNome = null` field and take
  `(dagName, jobName)`; grammar change is rule-6 territory.
- `runSupervised(d, supNome, maxReinicios)` (2.1.3b — supervision delegates to
  `kof.supervisor`, plan §3): every job becomes a `transient` child of one
  `Supervisor`; the per-child watcher IS the one_for_one (only the failed child
  restarts, neighbors are untouched). The body runs ONCE per watcher visit — a
  `false`/throw makes the worker THROW so the supervisor applies `restartLimit`;
  after `maxReinicios` restarts the job is dropped (final failure) and its
  dependents `skip` transitively. Dependencies are a cooperative wait on
  per-job status flags (mutable `Bool` field = `ACC_VOLATILE`, the DD-OTP-08
  mechanism). The `Report` is assembled in DECLARATION order — deterministic on
  every target (the 4 targets run this face; no stub: the supervisor core ships
  everywhere, §129/OTP001 gone, JS since §132). Guards, all LOUD (R6):
  `maxReinicios < 1` is refused (silent unbounded restart = thread storm — the
  lesson measured when a host fell 19/09); a job carrying `retry()` on the same
  dag is refused (ONE restart policy per face — workflow `retry` lives in
  `run()`); empty supervisor name refused; wait and settle budgets (30 s) fail
  with the job name, never hang. `checkpoint` and `deadLetter` registered on
  the dag are HONORED here (restored job = succeeded without re-running; sink
  receives the final failure). `kof.supervisor` does NOT need its own import —
  `CompilerWorkflow` injects the supervisor host flat with the face and dedups
  by its marker when the user also imports `kof.supervisor`.
  static — referencing `scheduler.at` in the host would reject the whole host
  at compile time; your DIRECT `scheduler.at` calls keep the compile-time
  refusal).

## 3. Idiom

```
import kof.workflow

main() {
    var acc = listOf("start")
    var build = job("build", () -> { acc.add("built"); return true })
    var image = job("image", () -> { acc.add("imaged"); return true }).after(build)
    var ship  = job("ship",  () -> true).after(image)
    var lint  = job("lint",  () -> true)

    var rep = dag(listOf(ship, image, build, lint)).run()   // order = deps, not input
    println(rep.summary())   // ok=build,lint,image,ship failed= skipped=
    println(rep.allOk())     // true
    println(acc.get(1))      // built — closure state proves real sequencing
}
```

A job that shells out uses the `kof.process` Result (its own gap table applies):

```
var compile = job("compile", () -> process.run("make", listOf("-j4")).exitCode == 0)
```

`listOf(...)` everywhere — Kof has no `[]` list literal and no `{}` map literal
(measured; pinned negative by `WorkflowPrimitivesE2ETest`).

## 4. Failure semantics at a glance

| body outcome | Report | dependents |
|---|---|---|
| body `true` | `succeeded` | run when all deps succeeded |
| `false` (after retries exhausted) | `failed` + `errors` `"nome: false"` | `skipped` (transitively) |
| `throw "why"` (after retries) | `failed` + `errors` `"nome: why"` | `skipped` (transitively) |
| cycle in deps | `run()` throws `workflow: ciclo detectado entre: ...` | — |
| retried job | `retries` `"nome: tentativas=N"` | — |

Construction guards (`dag`/`job`/`after` throw immediately): empty name, null body,
null dependency, self-dependency, duplicate names in one dag, empty dag.

## 5. Known Kof edges (hit while building the host)

- A field **without an initializer** directly above a lambda-typed field breaks the
  parser's class-body lookahead — initialize (`String nome = null`). Grammar is
  rule-6 territory; worked around in the host.
- A block lambda whose **only exit is `throw`** types as `Void` — keep a trailing
  `return` so the body types as `() -> Bool`.
- A function-typed local **inferred with `var`** from a field loses its type —
  annotate: `var f: () -> Bool = job.corpo`.

## 6. Proof

`WorkflowE2ETest` 24/24 (exact stdout goldens, JVM==JS byte-parity): linear order,
failure cascade, throw-with-reason, cycle message, guard set, real bodies through a
captured list, retry (recover-on-3rd + exhaust-with-reason + exponential), schedule
fire-count, checkpoint restore across runs, supervised happy path, one_for_one
restart (flaky child restarts, neighbor untouched — counters prove both sides of
"one"), limit-exceeded drop + transitive skip, the face's R6 guards, and the
double-import dedup; Native compile-pinned. The shape layer is pinned by
`WorkflowPrimitivesE2ETest` (6/6, incl. the negative syntax pins). Plan:
`docs/workflow-plan.md` §5.
