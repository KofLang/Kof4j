[English](UNIVERSAL-PLATFORM-VISION.md) | [Português](UNIVERSAL-PLATFORM-VISION.pt_BR.md)

# Universal Platform — Vision & Architecture

**Type:** long-term architecture — **UNDER DEVELOPMENT** since 17/09/2026
(promoted from `future/` by maintainer decision; the R12 gate is **overridden**
— see `DECISIONS.md` §D-UNIVERSAL)
**Date:** September 2, 2026
**Base:** real state **0.5.0-beta** (20/09/2026) — own frontend (lexer, parser, AST, symbol
table, semantic, type checking), backend-agnostic Kof IR, 7 targets
(jvm stable, native x86_64 stable, native.risc/native.arm toolchain+qemu,
js alpha GraalJS, kofc native-only, android Phases 1–4), stdlib as **compile-time
dispatch tables** with diagnosed gaps, real FFI (SQLite `.so`
direct, FFM Vulkan compute, Java + GraalJS interop), `mvn test` 3225
(17/09/2026 — see `AGENTS.md` §"Verification loop" for the live count).

> **Rule of this document:** this is the **vision/architecture companion**
> of [`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`](../development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md).
> Promoted from `future/` to `development/` on 17/09/2026 **by maintainer
> decision** (see `DECISIONS.md` §D-UNIVERSAL), which **overrides the R12 gate**
> ("SYSTEMS closes before any Tier 6+"). It is now **current work**: the
> executable steps (Stages 1–8) and the invariants R1–R12 — each with status,
> owner lane and proof — live in the implementation doc. This companion holds
> the philosophy, the domain map, the architectural model, the stdlib/interop
> strategy, the risks and the non-goals that justify those steps. The frozen
> core semantics and the currently-stable behavior remain 100% intact; every
> change lands additive and per target.

References (unchanged): `docs/development/roadmap.md` (vision), `docs/philosophy.md`
(intent), `docs/architecture/architecture.md` (multi-target ADR),
`docs/bugs-and-gaps/ecosystem-coverage.md` (capability matrix), `docs/stdlib/stdlib.md`
(dispatch mechanism), `docs/development/roadmap.md` §23 (ex-plan-platform-completion) (current execution).

---

## 0. The central question

> *If Kof keeps evolving for years, how do you transform it from a programming
> language into a universal platform for software, systems,
> infrastructure, automation, data, security and science — without destroying the
> simplicity and identity of the language?*

The answer, in one sentence: **the language remains one; what grows is the
depth of the stdlib and the ecosystem, and specialization happens in
libraries/APIs/tooling — never in new targets.** Kof already has, in its current
state, the three ingredients that make this possible without rewriting the core:

1. **A single frontend + a backend-agnostic IR + pluggable backends**
   (the substrate that isolates semantics from mechanism).
2. **The stdlib as compile-time dispatch tables** with diagnosed
   gaps (the mechanism by which a new domain enters as a
   *new table + new runtimes*, and not as a new target).
3. **Real FFI + interop (Java, GraalJS, `.so`)** (what prevents the
   reimplementation of the entire world — "Kof does not need to own everything;
   it needs to be able to integrate everything").

All the rest of this document is a consequence of these three facts.

---

# 1. Executive Vision

Kof evolves from a *general application language* to a **universal platform for
applied computing**: the same language, the same compiler, the same
semantics, the same targets — used to build applications **and** to
automate infrastructure, operate systems, analyze data, carry out scientific
research, work with security and orchestrate complex pipelines.

The ambition is not to create "KofDevOps", "KofData", "KofBio" (separate
languages). It is exactly the opposite: **a single language** whose capability grows through the
quantity and depth of the stdlib and the ecosystem:

```text
                    KOF  (one language, one compiler, one IR, targets JVM/Native/JS)
                     │
        ┌────────────┴────────────┐
        │                         │
      Core (small)         Standard Library (the platform)
                                   │
          ┌──────────┬────────────┼────────────┐
          │          │            │            │
        Systems    Data       Security     Science
          │          │            │            │
       Infra       DataFrame    Crypto       Math
       IaC         ETL          Network      Biology
       Linux       Pipelines    Forensics    Chemistry
       CI/CD       ML/AI        Identity     Simulation
       Containers               (defensive)  HPC
```

The **targets remain execution mechanisms** (JVM, Native, JS). The
specialization is done by **libraries, APIs, runtimes and tooling** — each one
expressed with the normal constructs of the language (records, classes, functions,
loops, concurrency, FFI) and, when it helps, by a **new intent syntax**
(like `entity` today, like `test "nome" { }` today) that the compiler
reduces to normal code.

The distinction that governs everything:

> **The core language remains small. The platform can be enormous.**
> **Universal capability ≠ bloated language.** (see §13 of this doc)

And the deepening follows the interoperability rule:

> **Kof does not need to own everything. Kof needs to be able to integrate everything.**
> The scientific ecosystem, the clouds, Python/R, the drivers, the existing
> tools are *consumed* by FFI/interop — not reimplemented.

---

# 2. Long-Term Philosophy

Principles that must guide evolution (all already in force in the project; here they are
reaffirmed as **invariants** against growth):

1. **One language, one semantics, several targets.** The intent is single; the
   mechanism changes per target. No domain capability creates a new target.
2. **Intent → Kof → compiler → backend.** The code expresses *what*; the
   platform decides *how*. If the programmer needs to know the mechanism
   to write the intent, the design has failed.
3. **Never silent.** When a target does not realize a capability, the
   compiler says so at compile-time with a gap code (existing pattern:
   `SECN00x`, `CONC00x`, `DB001`, `WEB002`, `HTTP002`...). Domain gaps
   follow the same pattern (`INFRA00x`, `DATA00x`, `SCI00x`, `BIO00x`).
4. **Compile-time > runtime magic.** What the compiler can know
   (schema, validation, routes, config, infrastructure graph, stubs) is
   known at compile-time. Reflection and discovery at runtime are
   *interop*, not foundation.
5. **Integrate, do not reimplement.** Kof consumes the existing ecosystem
   (Java, C/C++/Rust, Python, R, Arrow, BLAS/LAPACK, CUDA, drivers, CLIs,
   REST/gRPC) via FFI/interop. Reimplementing the entire world is risk #1.
6. **Small API per domain.** Each namespace is born with a few useful and
   well-chosen functions (current rule: ≤ ~10). It grows by composition, not
   by accumulation.
7. **Escalation by trust layers.** core → stdlib → official packages →
   ecosystem → external (interop). Each layer has different guarantees and
   responsibilities (see §5).
8. **Core stability > domain speed.** The core is slow and
   stable; the domains evolve fast. A new domain can never require
   a change in the semantics of the core.
9. **Honest multi-target.** Heavy capabilities are born where the platform is already
   strong (JVM/Native) and gain progressive parity; what is missing in JS is a
   diagnosed gap, not silences or promises.
10. **Security and scientific correctness by default.** In security: secure
    default, constant time, versioned formats (current pattern of
    `kof.security`). In science: numerical correctness and determinism are
    acceptance requirements, not "best effort".

---

# 3. Architectural Model

## 3.1 The hierarchy (mental model)

```text
Kof Core  (language + compiler + runtime + tooling — SMALL, STABLE)
   │
   ├── Language      (syntax, types, control, classes/records, generics,
   │                  lambdas, exceptions, concurrency spawn/await, basic IO)
   ├── Compiler      (single frontend → Kof IR → pluggable backends)
   ├── Runtime       (generated JVM / Native asm / JS mjs; allocator; GC; threads)
   └── Tooling       (CLI, LSP, formatter, test runner, bench, debug, packager)
          │
          ▼
     Kof Platform  (the stdlib — "the platform", grows by namespaces)
          │
   ┌──────┼──────────┬───────────┬──────────────────┐
   ▼      ▼          ▼           ▼                  ▼
 Systems  Infra     Data      Security           Science
   │      │          │           │                  │
   └──────┴──────────┴───────────┴──────────────────┘
                     │
                     ▼
        Ecosystem (official packages → community → external/interop)
```

The language remains one. The vertical arrow is **trust**: what is
above is more stable and more universal; what is below is more specific,
evolves faster and can be *turned off* (optional package, external interop).

## 3.2 How a new domain enters (the already existing mechanism)

This is the most important point of the model. Kof **does not invent anything new** to
add infrastructure, data, security or science: it reuses exactly the
mechanism that already delivers `kof.web`, `kof.security`, `kof.db` today.

```text
Kof intent  (e.g.: infra "prod" { ... }  |  df.filter { ... }  |  scan("host"))
   ↓
SemanticAnalyzer   → types of the call
   ↓
CompilerDriver     → lowering to KofCall("kof_*")   [domain dispatch table]
   ↓
   ├── JvmRuntime    (Java/JVM: FFM, JNI, Java libs, Arrow JNI, CUDA-on-JVM)
   ├── NativeRuntime (asm x86-64: syscalls, .so direct, FFI, SIMD, pthread)
   └── JsBackend     (kof-runtime.mjs + kof_platform; GraalJS interop)
   ↓
target gaps → compile-time diagnosis (INFRA00x, DATA00x, SCI00x, BIO00x)
```

Each domain is, therefore: **one (or a few) dispatch tables + the
runtime implementations per target + a documented gaps block.** It is not
a new target, it is not a new compiler, it is not a new language. It is the
same architecture that already exists — what changes is *how many* tables and runtimes
exist.

Direct consequence: **the surface of each target is always an honest and
diagnosed subset of the surface of each domain.** This is what prevents the
platform from becoming an opaque "does-everything": what is missing is always visible.

## 3.3 Where each thing lives

| Layer | What it is | Examples today | Future examples |
|--------|---------|---------------|------------------|
| **Core** (language) | Syntax, types, control, abstractions, concurrency, minimal IO | classes, records, generics, `spawn`/`await`, `try/catch`, `for-in` | (almost nothing new — see §16) |
| **Base stdlib** (always on, small) | `kof.core`, `kof.collections`, `kof.io`, `kof.time`, `kof.json` | same | same (stable) |
| **Platform** (namespaces, the "Kof Platform") | `web`, `http`, `db`, `orm`, `security`, `config`, `log`, `observability`, `concurrent`, `cache`, `mq`, `validation`, `process`, `ui`, `test` | same | + `infra`, `cloud`, `shell`, `ssh`, `data`/`dataframe`, `sci`/`math`, `bio` |
| **Official packages** (optional, managed) | Heavy/focused domains | (none yet — `kofdeps` MVP landed; registry pending ⛔) | `kof-infra-aws`, `kof-dataframe-parquet`, `kof-ml`, `kof-crypto-advanced`, `kof-bio`, `kof-hpc` |
| **Ecosystem** (community) | Third parties via registry | (planned) | any domain |
| **External / interop** (NOT Kof) | JVM libs, `.so` C/C++/Rust, Python/R, REST/gRPC, CLIs, clouds, databases | Java interop, SQLite `.so`, FFM Vulkan, GraalJS, JDBC drivers, MongoDB driver | BLAS/LAPACK, CUDA, Arrow, Terraform/cloud APIs, NGS tools, HPC libs |

## 3.4 The boundary (what enters where) — decision rule

For any candidate capability, the question **in order**:

1. **Does every program need it and is it small?** → base stdlib (rare).
2. **Is it essential to the platform and small, but optional per program?**
   → platform namespace (the current pattern: `kof.web`, `kof.db`).
3. **Is it a specific/heavy or niche domain?** → **official package**
   (optional, managed; e.g.: `kof-ml`, `kof-bio`). *Never* becomes base stdlib.
4. **Does it already exist and is better outside (Python, C, CUDA, driver, cloud API)?**
   → **interop/FFI**, not reimplementation.
5. **Should Kof not do this?** → **non-goal** (see §12).

This order is the anti-"god language" mechanism (detailed in §13).

---

# 4. Domain Expansion

Individual analysis. Each entry declares: what it is, **what Kof already has**, what
is missing, the declarative-vs-imperative verdict (when applicable), the
recommended mechanism, the interop strategy, and **what NOT to do**.

> State convention used in all sections (detailed in §14.2):
> **A** already supported · **B** supported with small extensions · **C** requires
> architectural evolution · **D** requires research · **E** probably not worth
> the effort (or is a non-goal).

## 4.1 DevOps (operations, deployment flows, systems control)

**What it is:** automating the lifecycle of a system — provision,
configure, deploy, monitor, remediate — with the same language.

**What Kof already has (real state):**
- `kof.process` (spawning external processes, live stdin/stdout) — **A**.
- `kof.web` + `kof.http` (APIs, retry/circuit, WebSocket/SSE, TLS) — **A**.
- `kof.db`/`kof.orm` (state, migrations) — **A**.
- `kof.config`/`kof.log`/`kof.observability` (typed config, logging, health/metrics/request IDs) — **A**.
- `kof.concurrent` (`spawn`/`await`, `channel`, `scheduler.every/at`, `selectAny`, `cancel`) — **A**.
- `kof.security` (auth, JWT, rate limit, sessions, API keys) — **A**.
- `kof.test` + golden + `kof bench` (continuous verification) — **A**.
- FFI/interop (JVM, `.so`, GraalJS) and distribution tooling (CLI, packager) — **A**.

**What is missing (the "DevOps" as a unified domain):** orchestrating *several*
sources of state (files, processes, cloud API, k8s) in a **graph with
plan/apply/reconcile** — today this is "B/C": the pieces exist, the *declarative
reconciliation model* does not.

**Recommended mechanism:** a `kof.infra` namespace (platform) built
*on top of* what already exists: `record`s for the desired state, functions to
"read the current state" (via `kof.http`/`kof.process`/FFI), a **dependency
graph** and a **reconciliation loop** (`spawn`/`await` already provide
concurrency; `channel` the communication). The *plan* is a diff between desired
state and current state — **computable in normal Kof** (data + functions).
The *apply* is a call to a provider (FFI/REST/CLI). **None of this requires a change
in the core** — it is library + tooling. **C** only in the sense of *new namespace
+ new tool* (`kof infra plan/apply`), not in the sense of language.

**What NOT to do:** do not copy Terraform's *provider plugins* model
as a foundation; do not create a "KofAnsible" with separate YAML inventories; do not
turn `kof` into a shell. The value of Kof here is *typing + concurrency
+ unification*, not *yet another provisioning DSL*.

## 4.2 Kof Makealive — Infrastructure as Code (the conceptual replacement for Terraform)

**Domain name:** **Kof Makealive** — the way Kof expresses
infrastructure as typed code. (The name is intentional: the code *brings to life* the
desired state of the infrastructure — the plan "wakes" the world to the declared
state.)

**What it is:** infrastructure expressed as data + typed code, with
idempotency, state, plan/diff, reconciliation.

**Declarative vs imperative verdict — the central decision:**
Kof **supports both** with the same semantics, and the recommendation is:

- **The canonical model is imperative-turned-data** (the form the prompt
  shows as an alternative): `var production = Infrastructure("production")`,
  then `production.network(...)`, `production.database(...)`. This is Kof
  **pure today** (classes, records, functions, loops, conditions, tests) —
  **A/B** — and it is where the language shines (types, abstractions, LSP, static
  analysis).
- **The declarative form** (`infra "prod" { network "main" { ... } }`) is an
  **intent syntax** (as `entity` and `test "nome" { }` are today), which the
  compiler **reduces to constructors/records** — *not* to an HCL language.
  It is **C** (new parsing block + lowering), but it *reuses* the existing
  desugaring mechanism for `entity`/`test`. It should not become a mini-DSL
  with its own semantics: it should be **sugar over typed records**.

**Why this eliminates classes of problems of infra DSLs:** because the
"desired state" becomes **first-class typed data** — you can validate
at compile-time, provide LSP, test, parametrize with functions/loops, version
with git, and diff with the same code. Traditional HCL suffers from: weak
types, no real LSP, weak tests, difficulty of abstraction. Kof solves this by
being *the language itself*.

**Pipeline (all of it is runtime/tooling, not language):**

```text
Kof source (declarative infra OR imperative over records)
   ↓ semantic analysis
   ↓ infrastructure as a graph (typed data — domain IR)
   ↓ plan (diff current vs desired state)      [normal Kof: functions + FFI]
   ↓ approval (tool: `kof infra plan --review`)
   ↓ apply (provider via FFI/REST/CLI — spawn/await)
   ↓ state (persisted — kof.db/kof.io)
   ↓ reconciliation (loop: spawn/await + channel; idempotency by construction)
```

**Mechanism:** resource `record`s + dependency `map` + **acyclic
graph detected at compile-time** (the compiler already does analysis; detecting a
resource dependency cycle is trivial in this model) + provider as an *interface*
(each cloud is an implementation; the **AWS/Azure/GCP/OpenStack is interop/FFI**,
not reimplementation).

**Idempotency/state/secrets:** idempotency by *declaration of the desired
state* (the convergent apply is idempotent by construction); state in
`kof.db` (or JSON in `kof.io`); secrets via `kof.security.secrets` (already exists:
`KOF_*`, redaction) + KMS by FFI/interop. **B** (pieces exist; the reconciliation
loop is **C**).

**What NOT to do:** do not implement HCL *inside* Kof; do not create a
repository of "KofProviders" for *all* clouds; do not promise
to replace Terraform on day one — deliver a **typed and unified subset**
that covers the team's real case, and leave the rest to interop.

## 4.3 Automation (scripts, shell, jobs, pipelines, CI/CD)

**What it is:** replacing the fragmented stack
(`Bash + Python + YAML + JSON + Terraform + Ansible + jq + sed + awk`) with a
unified and typed layer.

**What Kof already has (strong here):** `kof.process` (run commands, pipes,
stdin/stdout), `kof.io` (filesystem), `kof.http`, `kof.json`, `kof.config`,
`kof.time`/`scheduler` (cron-like `at("0 3 * * *")`), `spawn`/`channel`
(workers/queues), `kof.test`, `KofScript` (`kof script --watch`, REPL), FFI/CLI.
This covers a large part of "machine automation" — **A/B**.

**What is missing:** a declarative *job/pipeline model* and a shared
*executor* (job queue, retry, dead-letter, checkpoints) — today there is
`kof.mq` (pub/sub, **A**) and `scheduler` (**A**), but not a **batch/pipeline
framework** (see §3.8 of docs/bugs-and-gaps/`ecosystem-coverage.md`: `PLANNED`). **C** (namespace
`kof.workflow`/`kof.batch`).

**Verdict:** automation is where Kof has **the greatest natural adherence** — the
"intent paradigm" (writing `spawn`, `spawn { ... }`, `http.post`,
`Path(...).writeText` instead of `bash`/`curl`/`jq`) already is exactly the
anti-fragment of scripts. The unified layer is **B/C**: namespace + executor,
over existing primitives.

**What NOT to do:** do not reimplement bash (Kof **can** orchestrate it via
`kof.process`); do not create a separate "jobs YAML" — the jobs are **Kof
code** (data + functions), testable and typed. CI/CD: the *runner* is tooling; the
*pipeline* is Kof code (e.g.: a job that compiles, tests, publishes — all with
the same functions).

## 4.4 Cloud

**What it is:** provisioning and operating cloud resources.

**Strategy: 100% interop/FFI, zero reimplementation.** Kof does not rewrite
provider SDKs. It consumes: (a) via **JVM** — Java SDKs (AWS SDK etc.) by direct
interop; (b) via **CLI** — `kof.process` calling `aws`/`gcloud`/`az`
(robust, no dependency); (c) via **REST** — `kof.http` + request
signing (the signing layer is the only "new" and small thing). **A/B**.

**What enters Kof:** only the **typed abstraction layer** (records +
functions + `kof.infra`) that *orchestrates* the providers; the cloud
semantics stay outside. `kof.cloud`/`kof-infra-<provider>` as **official packages**
(optional) — not base stdlib. **B** (abstraction) + **A** (transport via
`kof.http`/`kof.process`).

**What NOT to do:** do not create "cloud SDKs in Kof"; do not promise parity
with all resources of all clouds; do not couple the core to a provider.

## 4.5 Data Engineering (ETL, pipelines, streaming, formats)

**What it is:** moving, transforming and persisting data at scale.

**What Kof already has:** `kof.json` (complete), `kof.io`, `kof.db`/`kof.orm`
(SQL, migrations, MongoDB), `List map/filter/reduce` (transformations),
`spawn`/`channel` (pipeline parallelism), `kof.mq` (basic pub/sub/streaming),
`kof.http` (ingestion via API). **A/B**.

**What is missing:** typed **DataFrame** (columns, lazy ops, partitioning),
columnar formats (**Parquet/Arrow**), and a **pipeline engine**
(checkpointing, replay, backpressure). **C/D**:
- DataFrame as a **namespace** (`kof.data`/`dataframe`) — **C** (new collection
  type + optimization).
- **Parquet/Arrow** — **A via interop**: Arrow has bindings (JNI on the JVM; `.so`
  on native). Kof **does not reimplement** Arrow; it **connects** to an existing
  Arrow/Parquet via FFI, exposing a typed wrapper. (This is the rule of §11.)
- **Streaming** — `spawn`/`channel` already give the skeleton; the "engine" is a
  library. **C**.

**Interop strategy (decisive):** data in Arrow (exchangeable columnar
memory) is the **exchange standard** with Python/JVM/natives. Kof defines *the typed
wrapper* and *the pipeline*; the columnar bytes are Arrow. This avoids reinventing
what the scientific ecosystem has already solved.

**What NOT to do:** do not reimplement Parquet/Arrow from scratch; do not create
your own "SQL engine" — `kof.db` is already SQL-first and the scope is to *orchestrate*
databases, not to *be* a database.

## 4.6 Data Science

**What it is:** statistical analysis, modeling, exploration, visualization.

**What Kof already has:** `List map/filter/reduce`, `Map/Set`, `kof.io`,
`kof.json`, concurrency, tests, UI (basic plot via `kof.ui`? — today no;
see gap). **A** (programming base) / **D** (science).

**Strategy: Kof *orchestrates* the scientific ecosystem, it does not replace it.**
- **Statistics/probability** — typed wrapper over existing libraries
  (JVM has stats; or FFI to C/Fortran). **C** (namespace) + **A** (FFI).
- **Visualization** — generate SVG/plot via FFI (e.g.: plotting libs) or via
  `kof.ui` (DOM/SVG) as a lightweight path; do not create a "matplotlib in Kof".
  **C/D**.
- **Notebooks/exploration** — `KofScript`/REPL already give a minimalist "notebook";
  the *rich* notebook is **E** (non-goal — it is an editor tool, not of the
  language). **B** (REPL) / **E** (rich notebook).

**What NOT to do:** do not promise parity with NumPy/Pandas/SciPy on day one;
do not reimplement linear algebra; Kof's entry into data science is
**typed pipelines + interop with the ecosystem**, not "SciPy in Kof".

## 4.7 Machine Learning

**What it is:** tensors, models, training, inference, metrics, pipelines.

**Real state:** nothing in Kof today (see docs/bugs-and-gaps/`ecosystem-coverage.md` §3.14: `PLANNED`,
stdlib-vs-external decision deferred to P3). **D** (research) — and most of it is
**A via interop**.

**Strategy (the most "interop-first" of all):**
- **Tensors / autograd / CUDA** — **A via FFI/interop**: Kof *does not* reimplement
  a deep learning framework. It consumes: (a) **JVM** — ONNX Runtime,
  TensorFlow/PyTorch via Java; (b) **Native** — `.so` (libtorch, onnxruntime,
  cuDNN/CUDA by FFI); (c) the Kof wrapper is **typed and lightweight**
  (`model.infer(x)` → FFI). The *weight* stays in the ecosystem; the *control* stays in
  Kof.
- **Inference** — **B**: input `record` + FFI call + `kof.json` for I/O.
  It is served with `kof.web` (HTTP/WebSocket/SSE already exist).
- **Training** — **D/A**: orchestrated (Kof scripts that call the trainer via
  FFI/CLI), not reimplemented. HPC/GPU is the bottleneck (§4.11).
- **Experiment tracking** — **C**: lightweight namespace over `kof.db` + `kof.io`
  (records metrics/artifacts). Small and typed.

**What NOT to do:** **do not build an ML framework in Kof** (that is the
path to the god-language). Kof enters as **the typed orchestration and
integration layer** on top of ONNX/PyTorch/libtorch/CUDA. Kof's gain:
*the same language* for the data pipeline, the deployment, the monitoring and the
service — not for autograd.

## 4.8 Cybersecurity

**What it is:** cryptography, network, security automation, forensics, defensive
security. (Offensive only in a legitimate/controlled context — see §12.)

**What Kof already has (already one of the strongest points):** `kof.security`
complete — `passwords` (PBKDF2-HMAC-SHA256 600k), `crypto` (SHA-256/512,
HMAC, AES-GCM, secure random — **on all 3 targets**, Native in pure asm),
`jwt` (fixed HS256), `secrets` (env + redaction), `constantTimeEquals`,
rate limit, sessions, API keys. **A** — and with diagnosed gaps
(`SECN00x`).

 **What is missing (expansion):**
 - **Advanced cryptography** — RSA/ECC/P-256, full X.509/TLS, ChaCha20-Poly1305.
   **B** (JVM via `javax`; Native by FFI to libs; versioned format already exists).
   **Complete strategic evolution in §4.8.1** (layers, hybrid PQC, key
   management, `SecureChannel`, threat model, roadmap by maturity).
- **Networking / protocol parsing** — sockets exist (`kof_net_*` emitted);
  packet/DNS *parsing* is **C** (namespace `kof.net`/`kof.packet` — FFI to
  libs such as `libpcap` for capture).
- **Forensics** — filesystem/binary/memory analysis: **C/D** — largely
  **A via FFI** (parsing libs) + `kof.io` + Kof pipelines. Kof is the *typed
  orchestration layer*, not the ELF/PE parser from scratch.
- **Security automation / threat intel** — **B**: `kof.http` (threat-intel
  APIs) + `kof.json` + `spawn`/`channel` (distributed scan) +
  `kof.log` (SIEM-like). Fits naturally into the automation model (§4.3).
- **Defensive** — monitoring/detection/auditing: **B** over `kof.observability`
  + `kof.log` (correlation IDs already exist) + `kof.db`.

**Domain principle:** **defense first**; offensive only in a legitimate
context (auditing, authorized pentest, research) — and always as an *orchestrated
tool*, not as "Kof is an attack framework". (Reflects the stance of
`docs/development/DECISIONS.md` §D-SEC.)

 **What NOT to do:** do not reimplement crypto stacks from scratch when there are
 audited implementations (FFI to libs); do not turn Kof into "Kali in Kof";
 do not expose offensive primitives without the control context.
 
 ## 4.8.1 Kof Security — Strategic Evolution (modern security layer + post-quantum)
 
 > **Status: planning/architecture — do NOT implement in this phase.** This
 > block is auditing + strategy, not code. The current `kof.security` remains
 > **intact** (6 namespaces, 3 targets, `KofSecurityTest` 25 + `KofSecurityG9Test`
 > 3). It follows the domain principle: **cryptography extremely complex for
 > whoever implements the platform, extremely difficult to use incorrectly for
 > whoever uses the language.**
 
 **Invariant philosophy:** *cryptographic complexity on the inside, simple API on
 the outside.* The common dev never sees nonce, IV, padding, KDF, provider, encoding,
 rotation. And — absolute rule — **Kof simplifies the *use* of cryptography,
 but NEVER reinvents cryptography**: every new primitive is **FFI to an
 audited implementation** (JCA/JCE on the JVM, `liboqs`/`Relic`/`libsodium` on
 Native, `SubtleCrypto` on JS), never its own algorithm.
 
 ### A. State at the audit (0.2.6-beta, 02/09, `KofSecurity.java`)

> **Correction 18/09:** the AES-GCM face of `SECN002` was already closed on JS
> (and on Native x86) on 01/09 — the cells/rows below originally said otherwise.
> The remaining `SECN002` face is **chacha20 on Native x86** (plus all `SECN002`
> on riscv/aarch, which is `SECN000`).
 
 6 intent namespaces, compiled by the same dispatch pattern of
 `kof.io`/`kof.web` (`KofSecurity.staticMethod` → `kof_sec_*` → 3 runtimes):
 
 | Namespace | APIs today | JVM | Native x86_64/riscv64 | JS |
 |-----------|-----------|-----|----------------------|-----|
 | `passwords` | `hash`/`verify`/`needsRehash` (PBKDF2-HMAC-SHA256, 600k) | ✅ javax.crypto | ✅ **pure asm** (getrandom, FIPS) | ✅ platform |
 | `crypto` | `sha256`/`sha512`/`hmacSha256`/`encryptAesGcm`/`decryptAesGcm`/`randomHex`/`randomInt` | ✅ JCA | ✅ **asm** (FIPS 180-4, GCM, getrandom) | ✅ pure JS sha/hmac + **AES-GCM** (AES face closed 01/09); remaining `SECN002` = chacha20 (Native x86) |
 | `jwt` | `create(claims,secret[,ttl])`/`verify(token,secret[,iss,aud])`/`secret()` — **fixed HS256** | ✅ | ✅ asm (b64url+HMAC) | ✅ |
 | `secrets` | `get(name[,fallback])`/`redact` | ✅ env | ✅ `/proc/self/environ` | ✅ platform |
 | `security` | `constantTimeEquals`/`random*`/`redact`/`csrf*`/`corsAllowed`/`csp/hsts/nosniff/frame/referrerHeader`/`rateLimit`/`session*`/`apiKey*` | ✅ | ✅ ct/redact/random/rate/session/apiKey (asm); ❌ csrf/cors/headers | ✅ ct/redact/random/rate/session/apiKey; ❌ csrf/cors/headers |
 | `auth` (web) | `secret`/`token`/`authenticated`/`claims`/`user`/`hasRole`/`hasPermission` | ✅ Bearer JWT + ThreadLocal | ❌ JVM-only | ❌ JVM-only |
 
 **Serialized formats (versioned, unambiguous):**
 `pbkdf2$sha256$<iter>$<saltB64>$<hashB64>` · `aesgcm$<ivB64>$<ctB64>` (32B key,
 IV 12B) · JWT RFC 7519 **fixed HS256** (the `alg` is **never** accepted from the token —
 locks algorithm confusion, `KofSecurityTest.jwtRejectsAlgorithmConfusionJvm`).
 
 **Real gaps** (never silent — `KofSecurity.supportedOn` + `gapCode`):
 `SECN002` (chacha20 on Native x86; the AES-GCM face closed on JS and Native x86 01/09 — see `docs/backend-parity.md`), csrf/cors/headers + `auth.*` (JVM-only).
 
 ### B. Current architecture
 
 ```text
 Kof (intent) → SemanticAnalyzer (types) → KofSecurity.staticMethod
    → kof_sec_* (runtime name) → [ supportedOn? gapCode → diagnosis ]
        → JvmRuntime     (javax.crypto / JCA / SecureRandom / MessageDigest)
        → NativeRuntime  (asm without libc: getrandom, FIPS 180-4, GCM, constant-time)
        → JsBackend      (pure JS sha/hmac/pbkdf2 + kof_platform; AES-GCM = gap)
 ```
 
 **The implicit provider layer already exists** (JVM = JCA, Native = asm, JS =
 `SubtleCrypto`/platform). What is missing is *formalizing it* and adding layers
 (keys, PQC, channel, typed) on top — see §F.
 
 ### C. Strengths (what is already well designed — DO NOT touch)
 
 - **Secure by default**: PBKDF2 600k, AES-**GCM** (authenticated, never CBC),
   constant-time in every secret comparison, fixed JWT HS256 (no alg-confusion).
 - **Versioned format** in every serialized artifact → evolution without breaking data.
 - **Honest multi-target**: primitives in asm without libc; gap becomes diagnosis, never a
   divergent stub.
 - **Zero ceremony**: intent in Kof, no annotation/container/config XML.
 - **Cross-check of vectors**: SHA-256/512/HMAC identical on the 3 targets and match
   FIPS 180-4 / RFC 2104 (`KofSecurityTest`).
 
 ### D. Gaps (what is absent — ordered by value)
 
 1. **Asymmetric** — RSA/ECC/P-256 (key, sign/verify): **does not exist** (only symmetric HMAC).
 2. **Post-quantum** — **zero** (no ML-KEM/ML-DSA/Falcon). The *harvest-now-decrypt-later* threat is real for long-lived data.
 3. **Key management** — only env `KOF_*` + `secrets.get`; no typed lifecycle (generate/derive/rotate/store/revoke).
 4. **KDF / envelope** — AES-GCM takes the key "ready" (hex); there is no HKDF, nor *key encapsulation*, nor context binding.
 5. **Secure communication (channel)** — partial TLS: `app.listenSecure` only on the JVM (self-signed via keytool); no channel abstraction (negotiation + KEM + KDF + crypto + auth in one API).
 6. **Security types** — `String` carries password, hash, key, secret: the type system **does not prevent** swapping `PasswordHash` for `Password`.
 7. **ChaCha20-Poly1305** — second AEAD (edge/devices without an AES accelerator).
 
 ### E. Risks (technical and cryptographic)
 
 - **Homemade crypto** (risk #1 of the roadmap): any "handmade" PQC/KDF/channel in Kof/asm is catastrophic. **Mitigation: FFI to audited libs always.**
 - **Arbitrary combination of primitives** ≠ secure protocol: ML-KEM + AES-GCM is only secure with KDF (HKDF) + context binding + versioning + replay. **Mitigation: *channel* abstraction with a versioned protocol, not loose primitives.**
 - **Downgrade attack** in the hybrid model (forcing the classical pair). **Mitigation: *mandatory* hybrid (classical + PQ together), versioning in the artifact header, refusal of an unknown version.**
 - **Nonce/IV reuse** (GCM = catastrophic if the IV is reused under the same key). **Mitigation: 12B IV via `randomHex` (CSPRNG) + documented per-key usage limit; never an IV derived from the counter without protection.**
 - **Secret leakage in logs/memory** — `redact` exists but is not *enforced* by type. **Mitigation: `Secret` type (non-printable) + automatic redaction in `kof.log`.**
 - **Provider/dependency compromise** (JCA backdoor, CVE in `liboqs`). **Mitigation:** §23 (audit process + pinned versions).
 
 ### F. Proposed future architecture (modular — hypothesis to validate)
 
 ```text
 kof.security
 ├── crypto          (primitives — FFI, NEVER own)
 │   ├── hash        sha256/512 (+sha3 via FFI)
 │   ├── symmetric   AES-GCM (ready) · ChaCha20-Poly1305 (FFI libsodium)
 │   ├── asymmetric  RSA/ECC/P-256 sign/verify (FFI JCA / liboqs)
 │   ├── signature   (over asymmetric)
 │   └── postquantum ML-KEM-768 (KEM) · ML-DSA-65 (sig) · hybrid  (FFI liboqs)
 ├── keys            generate · import · export · derive(HKDF) · rotate · store · revoke
 ├── secrets         get · redact · (type `Secret` non-printable)
 ├── passwords       hash/verify/needsRehash (ready; optional Argon2 migration)
 ├── jwt             create/verify (ready; JWS HS384/512 + optional RS256)
 ├── certificates    X.509/PEM/PKCS#12 (FFI; absent today)
 ├── tls             app.listenSecure(port, cert, key) + client channel (FFI/SSLSocket)
 ├── auth            web context (ready on the JVM; extend to Native/JS)
 ├── identity        RBAC/ABAC via claims (ready `hasRole/hasPermission`)
 ├── tokens          (jwt + session + apiKey — consolidate)
 └── secure          channel(...)  →  SecureChannel (KEM+KDF+AEAD+auth+replay, §J)
 ```
 
 **Assessment of the hypothesis:** it makes sense **in layers**, but do **not** create all
 sub-namespaces at once. What *protects* the language: keep the 6 current
 namespaces stable and **add** `keys`/`secure`/`postquantum` as an extension
 (pattern `entity`→`orm`), **not** reorganize what already exists. `certificates`/
 `identity`/`tokens` can start as **functions inside** existing namespaces
 before becoming a sub-tree.
 
 ### G. API Design (idiomatic — proposal, decide by security not by aesthetics)
 
 Prefer the **functional intent** form (consistent with the rest of the
 language; reject the `Security.encryption(...).build()` builder — anti-idiomatic):
 
 ```kof
 // symmetric secure by default (hides IV/alg/provider — already exists)
 var ct = crypto.encryptAesGcm(data, key)
 var pt = crypto.decryptAesGcm(ct, key)

 // asymmetric (NEW — FFI)
 var kp   = keys.generatePair("P-256")
 var sig  = kp.sign(data)
 if (kp.verify(data, sig)) { ... }

 // post-quantum (NEW — FFI liboqs)
 var ct2 = crypto.encryptHybrid(data, key)     // ML-KEM-768 + AES-256-GCM (HKDF)
 var pt2 = crypto.decryptHybrid(ct2, key)
 var sig = crypto.signPq(data, kp)             // ML-DSA-65

 // key management (NEW)
 var k   = keys.derive(masterKey, "app/2026/db")   // HKDF — never use a password as a key
 k.rotate()

 // secure channel (NEW — hides KEM/KDF/AEAD/auth/replay)
 var ch  = secure.channel(peer, profile)
 ch.send(payload)

 // passwords (ready — keep)
 var h   = passwords.hash(pw)
 if (passwords.verify(pw, h)) { ... }
 ```
 
 **Decisions to fix (not aesthetics):**
 - **Secure default**: `encrypt(...)` = AES-256-GCM; `encryptHybrid(...)` =
   ML-KEM-768 + AES-256-GCM. Never expose `Cipher("AES/CBC/...")` as default.
 - **Dangerous APIs hidden**: low-level primitives (direct `Cipher`/`Mac`)
   **do not** enter the public API — only via explicit FFI/escape hatch.
 - **Compile-time warnings**: using a key < 128 bits, short IV, ECB → `SECD00x`
   diagnosis (warning), never silent.
 - **Types** (§L/§15 of the prompt): `Secret`, `KeyHandle`, `Signature` prevent
   wrong semantics (using `Secret` where a common `String` is expected, etc.).
 
 ### H. Post-quantum strategy (analysis — NEVER implement by hand)
 
 > **Rule: there is no PQC in Kof yet. All PQC is FFI to an audited lib
 > (JVM: future provider/`liboqs`; Native: `liboqs`/`Relic`; JS: none → gap).**
 
 **What each primitive solves (and what it does NOT solve):**
 
 | Algorithm | Problem | Guarantees given | Does **NOT** give |
 |-----------|----------|-----------------|------------|
 | **ML-KEM-768** (KEM) | *key encapsulation* → shared secret | confidentiality of the secret; CCA | **does not authenticate** the peer; is not payload AEAD |
 | **HKDF** (KDF) | raw secret → derived keys | secure derivation, context binding | does not encrypt; does not authenticate by itself |
 | **AES-256-GCM** (AEAD) | encrypt the payload | confidentiality + **integrity** | IV reuse breaks everything (mitigated §E) |
 | **ML-DSA-65** (sig) | authentic signature | integrity + non-repudiation of the artifact | does not encrypt; does not give forward secrecy |
 
 **Conceptual model (what IS combined — with KDF and binding, not "just
 gluing"):**
 
 ```text
 ML-KEM-768 (encaps)  →  ephemeral secret
        ↓  HKDF (salt + info=version+context)
   keys { enc, mac }  →  AES-256-GCM (payload)  →  authenticated AEAD
   [optional] ML-DSA-65  →  envelope signature (who/which version)
 ```
 
 **Where each mandatory piece enters (do not assume that combining = secure):**
 - **KDF**: mandatory — KEM returns a raw secret; HKDF derives AEAD keys.
 - **Context binding**: the HKDF `info` carries the **protocol version** +
   party identifiers → prevents *key-confusion* between channels.
 - **Authentication**: pure ML-KEM is anonymous → require a **signature (ML-DSA) or
   pre-shared** for peer authentication; document it.
 - **Anti-replay**: on a persistent channel, counter/nonce in the envelope header.
 - **Versioning**: artifact header `hyb1$<kem>$<kemCtB64>$<ctB64>` (follows
   the `aesgcm$...` pattern); unknown version → **refuse**, do not guess.
 - **Mandatory hybrid**: always classical **and** PQ (defense in depth against
   a bug in one of the two); **downgrade** blocked (refusal of a classical-only version).
 - **Rotation/compatibility**: KEM uses ephemeral keys (channel forward secrecy);
   rotatable signature keys (public key distributed out of band).
 
 ### I. Key management (lifecycle)
 
 Abstractions to evaluate (expose the minimum): `KeyHandle` (opaque — **never** `String`
 for sensitive material), `PublicKey`/`PrivateKey`/`KeyPair`, `MasterKey`.
 **Do not expose** raw private key bytes as `String`.
 - **generate**: CSPRNG (`randomHex`/JCA) + minimum size per algorithm.
 - **derive**: HKDF from a master key (never password→key directly).
 - **store**: env `KOF_*` → file `0600` → `keychain`/platform variant (JS);
   **automatic redact** in any log.
 - **rotate/revocation**: `keys.rotate` generates a successor + dual-key windows
   (versioned format allows dual-read); revocation by `KeyHandle` allow-list.
 
 ### J. Secure communication (future abstraction — `SecureChannel`)
 
 Objective: the dev **never** implements key-exchange/KEM/KDF/encryption/auth/nonce/
 replay. Target properties: confidentiality, integrity, authentication,
 forward secrecy (via ephemeral KEM), anti-replay, algorithm **and**
 **version** negotiation, rotation, context binding. Proposal: `secure.channel(peer, profile)`
 returns a handle with `send/receive/close`; underneath = §H (hybrid) + ML-DSA
 (auth) + HKDF (derivation) + AES-256-GCM (payload) + counter (replay). **Classif.:
 D (research) / C** — depends on PQC FFI + formalization of the versioned protocol.
 
 ### K. Multi-target strategy (JVM / Native / JS)
 
 | Layer | JVM | Native | JS |
 |--------|-----|--------|-----|
 | symmetric (GCM) | JCA | asm (ready) | pure JS AES-GCM (ready 01/09) |
 | hash/HMAC | JCA | asm (ready) | pure JS (ready) |
 | asymmetric (ECC/RSA) | JCA/`KeyPair` | FFI `liboqs`/`openssl` | `SubtleCrypto` |
 | **PQC (ML-KEM/DSA)** | FFI `liboqs`/provider | FFI `liboqs` | **none** → gap `SECPQ` (diagnosis, never stub) |
 | channel/TLS | `SSLContext` (ready) | FFI `openssl` | `fetch`/`wss` (host) |
 
 **Rule:** identical Kof API; when a target does **not** have the capability, it **fails
 clearly** (`SECPQ`/`SECN00x`) — **never** a silent fallback to a weak algorithm
 (e.g.: never "PQ unavailable → uses only classical" without warning).
 
 ### L. Threat model (summary — Threat / Impact / Likelihood / Mitigation / Residual)
 
 | Threat | Impact | Likelihood | Mitigation | Residual |
 |--------|--------|-----------|-----------|---------|
 | Key leakage in logs | high | med | `Secret` type + automatic redact | low |
 | Nonce/IV reuse (GCM) | high | low | IV via CSPRNG + doc limit | low |
 | Harvest-now-decrypt-later | high (long-lived data) | med | **hybrid PQC** (§H) | med |
 | Replay on channel | med | med | counter/nonce + version | low |
 | Downgrade (classical-only) | med | med | mandatory hybrid + version refusal | low |
 | Algorithm confusion (JWT) | high | low | **fixed HS256** (ready) | low |
 | Timing attack (comparison) | med | low | `constantTimeEquals` (ready) | low |
 | Weak randomness | high | low | CSPRNG (getrandom/SecureRandom) | low |
 | Dependency/provider CVE | med | med | pinned + audit (§23) | med |
 
 ### M. Test strategy (NEVER "encrypt→decrypt→worked")
 
 - **Known-answer / official vectors**: FIPS 180-4 (ready), RFC 2104 (ready);
   for PQC: **official NIST vectors** (ML-KEM-768 / ML-DSA-65) — interop.
 - **Interop**: Kof artifact↔external lib (e.g.: `openssl`/`liboqs` decrypts what
   Kof encrypted) — essential for PQC/asymmetric.
 - **Negative/adversarial**: tamper (ready), wrong key, reused IV,
   alg-confusion (ready), unknown version, malformed token (ready).
 - **Property/fuzzing**: round-trip + integrity invariants (GCM fails on a
   bit flip) under random input.
 - **Regression**: value parity between targets (already exists for hash/HMAC).
 
 ### N. Roadmap (by maturity — **no dates**; each phase with objective/
 dependency/risk/completion criterion)
 
 ```text
 S1 SECURITY FOUNDATION   (base — almost ready)
    secure default (GCM/PBKDF2/constant-time/fixed-HS256) + vectors + redact
    → already A (AES-GCM on JS closed 01/09); to complete: adversarial vectors + the chacha20 face of SECN002 on Native x86
 S2 SAFE DEFAULTS / TYPES
    type `Secret`/`KeyHandle` + redaction in kof.log + warnings SECD00x
    → depends on S1; low risk; criterion: API without raw key exposure
 S3 KEY MANAGEMENT
    keys.generate/derive(HKDF)/rotate/store(0600)/revoke
    → depends on S2; FFI JCA/openssl; criterion: typed lifecycle + dual-key rotation
 S4 ADVANCED CRYPTO
    asymmetric (ECC/RSA sign/verify) + ChaCha20-Poly1305 + X.509/PEM
    → depends on S3 + FFI; criterion: interop with openssl + vectors
 S5 POST-QUANTUM RESEARCH
    ML-KEM-768 + ML-DSA-65 (FFI liboqs) + NIST vectors + interop
    → depends on S4 + formal FFI; high risk (only audited FFI); criterion: NIST vectors green
 S6 HYBRID CRYPTOGRAPHY
    encryptHybrid (ML-KEM + HKDF + AES-256-GCM) + versioned format + anti-downgrade
    → depends on S5; criterion: hybrid interop + downgrade blocking
 S7 SECURE COMMUNICATION
    secure.channel (KEM+KDF+AEAD+auth+replay) + full multi-target TLS
    → depends on S6; classif D/C; criterion: E2E channel + forward secrecy
 S8 MULTI-TARGET SECURITY
    honest parity (gap = diagnosis, never weak stub) + perf/streaming
    → depends on S7; criterion: target matrix without silent divergence
 ```
 
 ### O. Priorities (NOW / NEXT / LATER / RESEARCH / AVOID)
 
 **Classification (same legend A–E as §14.2):**
 
 | Item | Classif. | Justification (real state) |
 |------|----------|------------------------------|
 | ~~AES-GCM in JS (SECN002)~~ | ✅ CLOSED 01/09 | pure JS AES-GCM landed; the code `SECN002` now covers only the chacha20 face |
 | `keys.*` (generate/derive/rotate) | **B/C** | over JCA/openssl (FFI); opaque `KeyHandle` is a type extension |
 | `Secret` type/forced redaction | **B** | type-system + `kof.log`; prevents leakage |
 | asymmetric ECC/RSA (sign/verify) | **B** | FFI JCA/openssl; versioned format already exists |
 | X.509/PEM/PKCS#12 | **C** | FFI + new sub-namespace; full TLS depends on it |
 | **ML-KEM/ML-DSA (PQC)** | **D (FFI)** | **none** today; FFI `liboqs` + NIST vectors; integration research |
 | hybrid (KEM+HKDF+AEAD) | **D/C** | versioned protocol; anti-downgrade; depends on PQC |
 | `secure.channel` | **D** | KEM+KDF+AEAD+auth+replay; protocol formalization |
 | ChaCha20-Poly1305 | **B** | FFI libsodium/`SubtleCrypto` |
 | full multi-target TLS | **C** | JVM ready (self-signed); Native/JS via FFI/fetch |
 | "Kali in Kof" / offensive without context | **AVOID** | do not expose offensive primitives without control |
 | own algorithm (any) | **AVOID** | **absolute rule** — only FFI to an audited lib |
 
 **NOW** (without deep research, extends what already exists): `Secret` type +
 forced redaction · `keys.derive` (HKDF) + `keys.rotate`.
 **NEXT**: complete `keys.*` · asymmetric (ECC/RSA) · ChaCha20-Poly1305 ·
 X.509/PEM.
 **LATER**: full multi-target TLS · `secure.channel`.
 **RESEARCH** (only FFI to an audited lib + official vectors): **ML-KEM-768** ·
 **ML-DSA-65** · **hybrid** (KEM+HKDF+AEAD, anti-downgrade) · PQC interop.
 **AVOID**: inventing any algorithm · exposing `Cipher`/low-level primitives
 as default · PQC in JS without a lib (gap `SECPQ`, not a stub) · silent
 downgrade · "Kali in Kof".
 
 ### Non-goals of this block (mirrors §24)
 
 Do not invent an algorithm · do not implement all existing algorithms · do not
 expose a dangerous API as default · do not reorganize the 6 current namespaces (only
 **add** `keys`/`secure`/`postquantum`) · do not make the core depend on
 security · do not turn `kof.security` into a giant framework without modularization.
 
 ## 4.9 Scientific Computing (physics, chemistry, engineering, numerics, HPC)

**What it is:** numerical computation, simulation, SIMD/GPU, parallelism, HPC, signals,
image, audio, embedded.

**What Kof already has:** real FP arithmetic in Native (XMM — `FLT001` closed),
arrays, `List map/filter/reduce`, `spawn` (threads), FFI (**Vulkan compute via
FFM already works** — `JvmVkRuntime`, chain instance→device→pipeline validated),
`kof.io`, benchmarks. **A** (base) / **D** (HPC).

**What is missing (necessary resources, classified):**
- **SIMD / vectorization** — **D**: today there is XMM for scalar FP; data SIMD
  in the Native codegen is research. (JVM: JIT already vectorizes.)
- **GPU** — **D/A**: FFM Vulkan *is already the proof of concept* (compute). Generalizing
  (CUDA, OpenCL, or more Vulkan) is FFI + codegen research.
- **Multithreading / async** — **A** (spawn/await/channels); data-parallel is **D**.
- **Memory control** — Kof is **GC** (philosophy: the programmer does not manage
  memory). For critical HPC, the way out is **FFI to C/C++/Rust code** (escaping
  to the zone without GC), *not* ownership/borrowing in the core. **D** (advanced FFI)
  / **E** (ownership in the core — do not do it).
- **FFI / interop** — **A** (strong point: SQLite `.so`, FFM, Java, GraalJS).
  Formalizing FFI as first class is **C** and *low cost in the core*.
- **Distributed** — **D**: `spawn`/`channel` give the model; MPI/distributed
  parallelism is interop (FFI to MPI) + libraries.

**Strategy:** Kof is **the typed scientific orchestration language** (the
"glue" between numerical libraries, GPUs and pipelines) — **not** the repository
of numerical kernels. Linear algebra is **FFI to BLAS/LAPACK** (JVM has
bindings; Native via `.so`), not reimplementation.

**What NOT to do:** do not reimplement BLAS/LAPACK/NumPy; do not create ownership
in the core; do not promise "native" HPC on day one — the honest path is
**Kof orchestrates + FFI executes**.

## 4.10 Bioinformatics (biotechnology)

**What it is:** processing of DNA/RNA sequences, FASTA/FASTQ, alignment,
variants, genomic pipelines, laboratory automation.

**What Kof already has:** `List map/filter/reduce` (sequence transformation),
`kof.io`, `spawn`/`channel` (parallel pipelines), `kof.db`, `kof.test`, FFI.
**A** (base) / **D** (domain).

**Strategy (100% "official package + interop"):**
- **Formats (FASTA/FASTQ/VCF/BAM)** — **C**: an **official package**
  (`kof-bio`) with typed `record`s for reading/writing. *Small and closed*
  (stable formats) — a good candidate for a package, not for the base stdlib.
- **Alignment / variants / genomic statistics** — **A via FFI**: consumes
  existing tools (BLAST/EMBOSS/`htslib` via FFI/CLI) — Kof does **not**
  reimplement aligners.
- **Genomic pipelines** — **B/C**: exactly the automation/pipeline model
  (§4.3/§4.5): typed jobs + `spawn`/`channel` +
  checkpoints. Kof shines here (complex, typed, testable pipelines).
- **HPC** — via §4.11 (FFI + distributed orchestration).
- **Lab automation** — **B**: equipment orchestration via `kof.http`
  (REST) + `kof.process` (CLI/serial via FFI).

**What NOT to do:** do not turn Kof into a "biology language"; do not
reimplement aligners/variant callers; Kof's role is **the modern,
typed platform for building scientific pipelines** — the researcher writes
the pipeline in Kof and connects to existing scientific tools via FFI/CLI.

## 4.11 HPC (high-performance computing)

**What it is:** massive parallelism, GPU, HPC, distributed.

**Classification:** **D** (research) in everything that is "new"; **A** (interop)
in what it consumes. Kof **does not become** an HPC runtime — it **orchestrates** HPC.

**Resource by resource:**
- **SIMD** — **D** (Native codegen) / **A** (JVM JIT vectorizes).
- **GPU** — **D/A** (FFM Vulkan is the seed; CUDA/OpenCL via FFI).
- **Multithreading** — **A** (spawn/pthread/virtual threads).
- **Async** — **A** (spawn/await) / **D** (event-loop in Native; CONC003 in JS).
- **Memory control** — **E** (ownership in the core — do not do it); **D** (FFI to C/Rust
  for the non-GC zone).
- **FFI** — **A/C** (formalize FFI as first-class — the *most
  important* HPC path for Kof).
- **Vectorization** — **D**.
- **Distributed** — **D** (FFI to MPI + orchestration by Kof).

**What NOT to do:** do not compete with OpenMP/MPI/CUDA as a *runtime* — Kof is the
**typed control layer on top**; the computing weight stays in the libs.

---

# 5. Standard Library Strategy

The stdlib strategy **is** the defense against the god-language. The principle:
**the core language stays small; the platform grows; growth
happens in layers with different guarantees.**

## 5.1 The five layers (what belongs where)

| Layer | Entry criterion | Linking | Examples |
|--------|--------------------|---------|----------|
| **1. Core** (language) | essential to *almost every* program; semantics change | always (it is the language) | types, control, classes/records, `spawn`/`await`, exceptions, minimal IO |
| **2. Base stdlib** | essential to the platform; small; stable | always linked | `kof.core`, `kof.collections`, `kof.io`, `kof.time`, `kof.json` |
| **3. Platform** (namespaces) | essential to the *platform use case* (web, data, security); optional per program | linked when used (usage detection at compile-time — SQLite pattern: *link only when the program uses it*) | `web`, `http`, `db`, `orm`, `security`, `config`, `log`, `observability`, `concurrent`, `cache`, `mq`, `validation`, `process`, `ui`, `test` + future `infra`, `shell`, `ssh`, `data`, `sci`, `bio` |
| **4. Official packages** | specific/heavy domain; evolves fast; optional | managed by package manager (not linked by default) | `kof-infra-aws`, `kof-infra-az`, `kof-dataframe-parquet`, `kof-ml`, `kof-crypto-advanced`, `kof-bio`, `kof-hpc` |
| **5. Ecosystem + External** | third parties; or "it already exists and is better outside" | registry / FFI / interop | any package; JVM libs; `.so` C/C++/Rust; Python/R; Arrow; BLAS/LAPACK; CUDA; clouds; databases |

**Boundary rules (§3.4 order is the law):**
- If it goes to **layer 2**, it must be small and stable (rare).
- If it is a **heavy domain**, it goes to **layer 4** (package), **never** to
  layer 2. `ml`, `bio`, `hpc` are **official packages**, not base stdlib.
- If it **already exists outside and is better** (Arrow, BLAS, CUDA, drivers), it is
  **layer 5 (interop)** — typed wrapper in Kof, engine outside.
- **Layer 3** grows, but each namespace is **small** (≤ ~10 functions) and
  **capability-gated** (diagnosed gaps).

## 5.2 What must remain **outside** Kof

- **Engine reimplementations** of Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy —
  outside (layer 5, interop). Kof provides the *wrapper*, not the *engine*.
- **Complete cloud provider SDKs** — outside (layer 5, via JVM/CLI/REST).
- **Genomic aligners/variant callers** — outside (layer 5, FFI/CLI).
- **Deep learning frameworks** (autograd) — outside (layer 5, FFI).
- **Rich notebooks, IDE, kernel** — outside (editor tools).
- **Shell** — outside (Kof *orchestrates* the shell via `kof.process`).
- **Database** (engine) — outside (`kof.db` orchestrates; it is not an DBMS).

## 5.3 Anti-bloat mechanisms (the operational "No God Language")

(Expanded in §13; here the operational summary of the stdlib.)
1. **Stdlib modularization** — independent namespaces, without inverse
   dependency (current rule: a lower module never depends on a higher one).
2. **Official packages** — layer 4 with a package manager (`kofdeps` MVP
   landed: transitive resolution + lock; registry pending ⛔), versioned,
   optional.
3. **Capability-based APIs / optional modules** — link *only what the
   program uses* (pattern already used: SQLite/MySQL `.so` linked only when the literal
   DSN appears at compile-time). Generalize this mechanism to all
   packages.
4. **Dependency boundaries** — a package imports only what it declares; the compiler
   validates the boundary (static analysis already exists).
5. **Stable core / experimental APIs** — stability tiers: *stable*
   (compatibility guarantee) vs *experimental* (may change). Layer 4
   is born experimental.
6. **Versioning / compatibility guarantees** — version semantics per layer
   (core is strict semantics; packages follow semver). The central `VERSION` is already
   the base.

---

# 6. Interoperability Strategy

> **Kof does not need to own everything. Kof needs to be able to integrate everything.**

Interoperability is the **fence** of the universal platform — it is what prevents
reimplementing the world and what gives scale to the ecosystem. Kof *already* has real FFI
(direct SQLite `.so`; FFM Vulkan; Java interop; GraalJS; JDBC drivers;
MongoDB driver). The strategy is to **formalize and generalize** this.

## 6.1 Interop surfaces (what to consume and how)

| Target | Mechanism | State | Use in the platform |
|------|-----------|--------|-------------------|
| **Java / JVM libs** | direct interop (compatible bytecode) | **A** | clouds (AWS SDK), Arrow (JNI), ONNX, ML, HPC via JVM |
| **Native libs (C/C++)** | direct `.so` link (SQLite pattern) + FFI | **A** | BLAS/LAPACK, `libpcap`, `htslib`, parsers, CUDA |
| **Rust** | `.so` link / FFI (C ABI) | **A** | scientific libs, HPC |
| **Python** | subprocess/CLI via FFI (`kof.process`) + protocol (REPL/JSON) | **B** | scientific ecosystem (PyTorch, BioPython) as a *tool*; not as a dependency |
| **R** | subprocess/CLI via FFI | **B** | statistics/bio |
| **JavaScript** | GraalJS interop (`kof_platform`) | **A** | web/browser |
| **WebAssembly** | future target / interop | **D** | component portability |
| **REST** | `kof.http` (exists, retry/circuit) | **A** | clouds, APIs, threat-intel, lab |
| **gRPC** | planned (`app.grpc`, `.proto` codegen) | **B/C** | microservices, ML serving |
| **Databases** | JDBC (JVM) + wire protocol (Native) | **A/B** | data |
| **Cloud APIs** | REST + FFI (request signing) | **B** | infrastructure |
| **CLI tools** | `kof.process` (pipes, stdin/stdout) | **A** | automation, HPC, science (blast, samtools) |
| **OS APIs** | syscalls (Native) / FFI | **A** | systems |

## 6.2 Principles

1. **Typed wrapper, engine outside.** Kof exposes the *typed and idiomatic* API
   (`model.infer(x)`, `arrow.table(...)`, `blas.gemm(...)`); the *engine* runs in the
   ecosystem via FFI/interop.
2. **FFI as first-class.** Formalize FFI (declare signature,
   structure pointers/arrays, align ABI) as a compile-time construct —
   *reducing* the "manual asm" zone today. (Future architectural dependency,
   §7/§13.)
3. **Escape to the non-GC zone.** For critical HPC/numeric work, FFI to
   C/C++/Rust is the path (not ownership in the core). Kof provides the safe
   boundary (buffer, lifetime by GC at the boundary).
4. **Data via Arrow.** The columnar format is the **exchange standard** between Kof and
   the scientific ecosystem (JVM/native/Python).
5. **Never couple the core to an interop target.** Each target is *optional* and
   *capability-gated* (if the lib is absent, diagnosed gap).

---

# 7. Compiler Requirements

An honest analysis of *what would be needed*, why, the cost, and **whether it
is truly needed**. Rule: **nothing just because "big languages have it."**

| Capability | Needed? | Why | Cost | Verdict |
|-----------|-------------|---------|-------|----------|
| **Macros / codegen** | **Partially** | gRPC stubs, `entity` DDL, `infra` DSL (desugar over records), pipeline codegen | high if open; low if **compile-time codegen** (pattern already used: generated `KofRuntime`, synthesized test runner, `entity` DDL) | **C** — *formalize* a **compile-time codegen** layer (already exists implicitly); **reject** open macros (they break static analysis + LSP) |
| **Metaprogramming** | Not open | same as macros | high | **E** (open macros) / **C** (closed codegen) |
| **Compile-time evaluation** | **Yes (light)** | config const-folding, schema validation, cycle detection in the `infra` graph, crypto test vectors | low (the optimizer already does constant folding) | **B** — extend the *optimizer* to domain constants; not a "general TCC" |
| **More advanced generics / variance / sealed** | **Partially** | scientific collections, type-safety in pipelines, sealed for domains | medium | **B/C** — variance/sealed useful; **avoid** type-classes (see below) |
| **Type classes / protocols** | **No** | Kof has *interfaces* + *records* + FFI; type-classes add power without solving what is missing | high (new axis of the type system) | **E** — prefer **interfaces + FFI**; reconsider only if a real scientific domain requires it |
| **Reflection** | **Partially (interop target)** | ML/science (discover schemas dynamically), Java interop | medium; contradicts "compile-time > runtime magic" | **C** — reflection **restricted to interop** (no foundation); the schema known at compile-time remains the rule |
| **FFI (signature declarations)** | **Yes** | the backbone of interop/science/HPC | **low in the core** (it is lowering + runtime, not semantics) | **C** — *formalize FFI* as first-class (the highest value/cost item of all of §7) |
| **Annotations / decorators** | **No** | Kof rejects annotation-driven magic (roadmap §16); `entity`/`test` are already intention constructs without annotation | — | **E** — keep the rejection; intention constructs > annotations |
| **Package capabilities** | **Yes** | the core/platform/packages boundary (§5 layers); link only what is used | low-medium (usage detection by DSN already exists) | **C** — generalize *capability/link by use* to all packages |
| **Effect system** | **Research** | resources (files, GPU, connections), infra reconciliation, GPU | high (new axis) | **D** — *investigate*; today GC + `spawn` + `try/finally` cover it; probably **scoped resources** (light RAII-like) suffice — *not* a complete effect system |
| **Resource management (RAII/scoped)** | **Yes (light)** | handle FFI handles, files, GPU, connections without leaking | low-medium | **B/C** — a light `auto-closed`/scope (no ownership) |
| **Ownership / borrowing** | **No** | Kof is **GC** (philosophy: the programmer does not manage memory) | high; changes the identity | **E** — reject; the non-GC zone is via FFI to C/Rust, not via ownership in the core |
| **Async** | **Yes (extend)** | infra reconciliation, HPC, event-loop | medium | **B/D** — `spawn`/`await` already exist; *event-loop* in Native is research (CONC003 today in JS) |
| **Parallelism (data-parallel)** | **Research** | HPC, vectorization, data MapReduce | high | **D** — *spawn* (task-parallel) exists; data-parallel/SIMD is research + FFI |

**Summary:** the core **does not need** open macros, type-classes, annotations,
ownership, or a complete effect system. The core **needs** (low cost, high
value): **formalized FFI**, **formalized compile-time codegen**,
**package capabilities**, **light scoped resources**, **light compile-time eval**,
and (medium cost) **variance/sealed** and **interop reflection**. The rest is
research or rejection — recorded as a future architectural dependency, **not
implemented**.

---

# 8. Runtime Requirements

What **JVM**, **Native**, and **JS** would need to support. Current rule:
**JVM first, port later**; diagnosed gaps.

## 8.1 JVM (the "full interop" target)

The JVM is where the universal platform has the **greatest interop power** (access to
the entire Java ecosystem + FFM).

- **What it already has:** virtual threads (massive, cheap concurrency), FFM
  (validated Vulkan compute), full Java interop, GC, TLS, WebSocket/SSE.
- **What it would need:** (a) **formalized FFM** for BLAS/LAPACK/CUDA/Arrow
  (the seed already exists in `JvmVkRuntime`); (b) **HPC/ML via interop**
  (ONNX/libtorch/CUDA via FFM/JNI); (c) **infra** via Java/REST SDKs
  (no new runtime — `kof.http`/FFM already provide the transport).
- **Verdict:** the JVM is the **base of the universal platform** — most
  heavy capabilities (ML, HPC, clouds) arrive **first** here, via interop.
  Cost in the core: low (it is FFM/interop, not new semantics).

## 8.2 Native (the "deploy/edge/systems" target)

Native is where Kof **deploys without a JVM** — fast startup, low memory,
embedded/edge systems, local HPC, forensics.

- **What it already has:** ELF x86_64, syscalls, `spawn` (pthread), real FP (XMM),
  free-list GC, direct SQLite `.so`, MySQL wire protocol, crypto in asm.
- **What it would need (classified):** (a) complete **GC mark-sweep** (today
  free-list; auto-GC off) — **C** (necessary for long pipelines) [**updated 11/10:**
  the x86-64/native GC has mark-sweep since 03/09 and auto-collect ON since 19/09
  (`KofGcE2ETest`); the residual is the cross/native GC faces, `known-bugs` §602];
  (b) **formalized FFI** (declare `.so` without manual asm) — **C**;
  (c) **SIMD / vectorization** — **D**; (d) **event-loop/async** (today
  blocking pthread) — **D** (necessary for HPC/edge servers);
  (e) **RISC/ARM codegen** — **C** (already in progress) [**updated 11/10:**
  riscv64 + aarch64 codegen is COMPLETE since 03/09 (line-by-line translation,
  `NativeAarch64E2ETest` green under qemu); the residual is total x86 parity on
  JSON/DB/HTTP/concurrency/UI/net, `NATIVE002`]; (f) **GPU** (Vulkan via FFI; CUDA via FFI) — **D/A**.
  **(g) bare-metal / bootable** (microcontroller, legacy BIOS, UEFI) — see
  `PLAN-BAREMETAL-BOOT.md` (15/09 maintainer directive): a **platform seam
  (`kof_plat_*`) + freestanding link profile**, not a new language. Classified
  B-0…B-5; the 32-bit MCU face is research-class and depends on the collector.
- **Verdict:** Native is the target for **systems domains** (edge infra,
  forensics, automation, embedded). The HPC/numeric path is **FFI to
  C/C++/Rust** (non-GC zone), not reimplementation. Cost: medium-high
  (GC mark-sweep + event-loop are the two expensive items; bare-metal adds the
  HAL seam + 32-bit codegen per `PLAN-BAREMETAL-BOOT.md`).

## 8.3 JS (the "web/browser" target)

JS is **alpha** and, in the universal vision, is **the web target**, not the target of
heavy domains.

- **What it already has:** ES Modules (GraalJS), `kof.http` via `Java HttpClient`
  interop, UI, sequential `spawn`.
- **What it would need:** *little of the universal domains*. What is missing in JS is
  a **diagnosed gap** (pattern `DB001`, `WEB001`, `CONC003`). The
  recommendation is **not to promise JS parity** for ML/HPC/forensics — those
  are **JVM/Native**. JS enters the "universal" only for **the web/edge side of the
  application** (UI, light APIs, light data).
- **Verdict:** JS keeps the **web/edge** role; heavy domains are
  JVM/Native first. Cost: low (do not accelerate JS for HPC).

**Synthesis:** the universal platform is **JVM-first for heavy interop,
Native-first for systems/deploy, JS for web** — with honest gaps. This
preserves multi-target without false promises.

---

# 9. Tooling Requirements

Current rule (must be kept): **there is no parallel parser** — all tooling
consumes the **same frontend** as the compiler. Consequence: all new tooling
gets **domain diagnostics for free** (the LSP already publishes the same set
of errors as `kof check`).

| Tool | State | What it would need for the universal platform |
|------|--------|---------------------------------------------|
| **CLI** | 32 commands (build/run/serve/check/md/test/bench/profile/inspect/decompile/translate/compare/migrate/debug/info/lsp/install/script/repl/init/new/connector/legacy/deps/deploy/workflow/makealive/c/fmt/editor/config/version) | + **`kof infra plan/apply/destroy`** (infra orchestration — *tooling*, not language); + **`kof workflow run`** (run pipelines/jobs); + **`kof deploy`** (build + package + publish — on top of the existing packager). *All consume the frontend.* |
| **LSP** | minimal (hover/completion + diagnostics) | + **domain-sensitive** completion/diagnostics (`infra`, `entity`, `df` feature); go-to-definition in official packages; semantic tokens by domain. *Same frontend → no parallel parser.* |
| **Package manager** | MVP landed (`kof deps` + transitive resolution + lock; registry pending ⛔) | **mandatory** for layers 4/5 (official packages + ecosystem): resolution, versioning, **capability/link by use**, audit. It is what lets the platform grow without bloating the core. (Architectural dependency, §13.) |
| **Debugger** | JVM MVP (DAP + JDWP) | + Native (DWARF) + JS (source maps) — phases 4-7; **pipeline/job debugging** (see a job's state at execution). |
| **Profiler** | `kof bench`/`kof profile` (harness + baseline) | + **pipeline profiling** (time per job stage); + **HPC/FFI perf** (where time goes: Kof vs native lib). |
| **Formatter** | ✅ `kof fmt` (real parser) | stable; extend to the new intention constructs (`infra`, `entity`). |
| **Testing** | `kof.test` + golden + `kof bench` | + **property-based testing** (science: numeric invariant); + **golden diff** already covers multi-target parity; + **recon tests** (infra: idempotent plan). |
| **Deployment** | `scripts/package.sh` + release CI (2 jobs × 3 platforms) | + **multi-target deploy** (same source → JVM/Native/JS, already exists via `--target`); + *infra* artifact (the plan as a versioned artifact). |
| **Tooling observability** | `kof.observability` (health/metrics/request IDs) | + **tracing/OpenTelemetry** (spans + export landed; Native `OBS003` honest gap) — to trace pipelines end to end. |

**Principle:** tooling is the **control surface** of the universal
platform. Since it reuses the frontend, every new domain (infra, data, sci) gets
*check, LSP, debug, and test* without building parallel tools.

---

> **Moved to the implementation doc.** The stage-by-stage breakdown
> (objective, capabilities, dependencies, risks, impact, what NOT to do)
> now lives in [`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`](../development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md)
> as executable items with status/owner/proof. The staged capability order
> (FOUNDATION → SYSTEMS → AUTOMATION → INFRASTRUCTURE → DATA → SECURITY →
> SCIENTIFIC COMPUTING → BIOINFORMATICS → UNIVERSAL PLATFORM) is preserved there.

# 11. Risks

Long-term risks, classified by probability × impact, with
mitigation. (Reflects and expands the risks of the ADR and the current roadmap.)

| Risk | Why it is a risk here | Impact | Mitigation (already in the model) |
|-------|----------------------|---------|--------------------------|
| **Scope explosion** (wanting everything) | the "universal" vision invites saying "yes" to every domain | High | §3.4/§5: boundary order (core→stdlib→packages→interop); official packages for heavy domains; explicit non-goals (§12) |
| **Bloated stdlib** | every domain wants a namespace in the base stdlib | High | §5: heavy domains go to **official packages** (layer 4), never to the base stdlib; API ≤ ~10 per namespace; capability/link by use |
| **Ecosystem fragmentation** | official packages + community + interop = many places | Medium-High | §5/§9: package manager with registry + versioning + audit; layers with clear guarantees; "converge, do not duplicate" (current audit rule) |
| **Maintenance burden** | huge platform × 3 targets × gaps | **High (the greatest)** | §8: JVM-first for heavy interop, Native for systems, JS only web — *do not* promise total parity; diagnosed gaps (never "almost works"); current DoD (E2E + golden per target) |
| **Compiler complexity** | codegen, FFI, capability, interop reflection | Medium-High | §7: reject open macros/type-classes/annotations/ownership; only FFI + closed codegen + capabilities + scoped resources (low cost in the core) |
| **Runtime complexity** | GC mark-sweep, event-loop, SIMD, FFI | Medium | §8: each target has its honest scope; Native focuses on systems, JS focuses on web; FFI is the numeric path (not reimplementation) |
| **Interoperability problems** | FFI is a failure zone (ABI, lifetimes, bugs) | Medium | §6/§7: **formalized** FFI (signature declaration at compile-time); test vectors; non-GC zone via C/Rust with a safe boundary |
| **Adoption barriers** | "yet another universal language" is not an argument | Medium | §1/§2: the value is *one language* for app + infra + data + science + security — **fewer tools**, not more; measured onboarding (current rule: `kof init && kof run` < 60s) |
| **Performance** | typed wrapper over FFI may have overhead | Medium | §8/§9: *thin wrapper* (the engine is native); pipeline profiling; direct FFI (no reflection at runtime); `kof bench` with baselines |
| **Security** | security domain exposed to design errors | High | §4.8/§2: secure default, constant time, versioned formats (current pattern); defense first; offensive only in controlled context |
| **Scientific correctness** | silent numeric bug | High | §2: determinism/correctness as an **acceptance requirement** (property-based testing, golden diff); FFI to audited libs (not reimplementation) |

---

# 12. Non-Goals

What Kof **MUST NOT** try to be (explicit and permanent):

1. **It is not a god-language / "does-everything-the-same"**. It does not recreate the world; it integrates.
2. **It is not a shell** — it orchestrates the shell (`kof.process`), it does not replace it.
3. **It is not the engine** of Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy — it provides the typed
   wrapper, the engine is FFI/interop.
4. **It is not a deep learning framework** (autograd/training) — it serves and
   orchestrates models via FFI (ONNX/libtorch).
5. **It is not a DBMS** — `kof.db` orchestrates databases; it is not a SQL engine.
6. **It is not a cloud provider repository** for all clouds —
   it consumes SDKs/CLI/REST; typed abstraction only.
7. **It is not a genomic aligner/variant caller** — it consumes scientific
   tools via FFI/CLI.
8. **It is not a "Kali in Kof"** — defensive security first; offensive only in
   legitimate/controlled context; never an "attack framework".
9. **It is not a notebook/IDE/kernel** — editor tools, outside the
   language.
10. **It does not introduce ownership/borrowing in the core** — Kof is GC; the non-GC zone is
    via FFI to C/Rust.
11. **It does not use annotations/open macros/type-classes as a foundation** —
    intention constructs + interfaces + FFI + closed codegen.
12. **It does not promise JS parity for heavy domains** — JS is web/edge;
    ML/HPC/forensics are JVM/Native.
13. **It does not create a target per domain** (KofDevOps/KofData/...) — always the
    same language, same IR, same targets.
14. **It does not reimplement the scientific ecosystem immediately** — it consumes and
    orchestrates what exists (§11 rule of the statement: *Kof needs to be able to
    integrate everything, not own everything*).

---

# 13. Do not create a "God Language" — mechanisms

> *"A language capable of doing many things"* ≠ *"a bloated language
> that tries to do absolutely everything."*

The distinction that governs the universal platform: **the core language stays
small; the platform can be enormous.** Concrete mechanisms:

1. **Stdlib modularization** — independent namespaces, without inverse
   dependency (current rule); each domain is a module with a boundary.
2. **Official packages** — layer 4 (not base stdlib): a heavy/research domain
   becomes a versioned, optional, managed package. `ml`, `bio`, `hpc`,
   `infra-<cloud>` are **packages**, not stdlib.
3. **Capability-based APIs / optional modules** — link *only what the program
   uses* (pattern already used: SQLite/MySQL `.so` linked when the literal DSN
   appears at compile-time). Generalize to all packages → the final binary
   carries only what it needs.
4. **Dependency boundaries** — a package declares imports; the compiler
   validates the boundary (static analysis already exists). Prevents
   cyclic/hidden dependency between domains.
5. **Stable core** — the core is slow and stable; strict compatibility
   guarantees. The platform evolves fast; the core barely changes.
6. **Experimental APIs** — stability tiers. Layer 4 is born
   *experimental* (may change) and *promotes* to *stable* when it matures.
7. **Versioning / compatibility guarantees** — version semantics per layer
   (core strict; packages semver). The central `VERSION` is already the base.
8. **High entry cost for the core** — any change in the core requires
   justification of "every program needs it and it is small" (§3.4). The rest is
   package/interop. **This rule is the main anti-god-language mechanism.**

**Acceptance test:** at the end of the UNIVERSAL stage, the **language core must
have grown almost nothing** (FFI, codegen, capabilities, scoped resources — and
little more). If the core has bloated to "support" the platform, the design
failed.

---

# 14. Compatibility with current development (classification)

This is the section that anchors the plan **in the real state** — what Kof **already
has** that naturally allows the evolution, and how each future capability is
classified.

## 14.1 What Kof ALREADY has that naturally allows this evolution

| What already exists (state at 0.2.6-beta, 02/09) | How it enables the universal vision |
|------------------------------------------|---------------------------------|
| **Single frontend + backend-agnostic Kof IR + pluggable backends** | The substrate: a new capability = new table + runtime, **not** a new target/compiler |
| **Stdlib as dispatch tables at compile-time + diagnosed gaps** | The *mechanism* by which each domain (infra/data/sci/bio) enters without touching the core; "never silent" |
| **Real FFI** (direct SQLite `.so`; FFM Vulkan; Java interop; GraalJS; JDBC) | Prevents reimplementing the world — the backbone of interop/science/HPC |
| **Concurrency** (`spawn`/`await`, `channel`, `scheduler`, `selectAny`, `cancel`) | Pipelines, workers, infra reconciliation, distributed orchestration |
| **Abstractions** (classes, records, generics, lambdas, pattern matching) | Toolkit to model a domain (infra resources, tensors, genomic sequences) with types + LSP + tests |
| **`kof.db`/`kof.orm`** (SQL, migrations, MongoDB) | Infra *state*, experiment tracking, data |
| **`kof.security`** (crypto/JWT/secrets/auth on all 3 targets) | The core of the security domain already exists |
| **`kof.web`/`kof.http`** (routes, WS/SSE, TLS, retry/circuit) | ML serving API, threat-intel, lab automation, cloud |
| **`kof.config`/`kof.log`/`kof.observability`** | Typed config, logging, health/metrics/request IDs — the basis of every operation |
| **`kof.process`** (pipes, stdin/stdout) | Orchestrate shell/CLI/scientific tools (HPC, bio) |
| **Intention model + "never silent" + small API** | The **rules** that prevent the god-language (§13) |
| **Tooling over the same frontend** (LSP, check, fmt, test, bench, debug) | Every new domain gets diagnostics/LSP/test **for free** |

## 14.2 Classification of future capabilities

Legend: **A** already supported · **B** supported with small extensions · **C**
requires architectural evolution · **D** requires research · **E** probably not
worth it (or non-goal).

| Capability (domain) | Classif. | Justification (real state) |
|----------------------|----------|------------------------------|
| Concurrency for pipelines/workers | **A** | `spawn`/`await`/`channel`/`scheduler` ready (JVM/Native/JS) |
| Orchestrate shell/CLI/tools | **A** | `kof.process` ready |
| HTTP/REST (APIs, ML serving, threat-intel) | **A** | `kof.web` + `kof.http` (retry/circuit) ready |
| JSON/IO/config/logging/observability | **A** | `kof.json`/`kof.io`/`kof.config`/`kof.log`/`kof.observability` ready |
| Infra state / experiment tracking | **A/B** | `kof.db`/`kof.io` ready; the state *format* is an extension |
| Crypto/JWT/secrets/auth (app) | **A** | `kof.security` v1+G9+G10 ready on all 3 targets |
| Crypto: AES-GCM in JS | ✅ CLOSED 01/09 | pure JS AES-GCM landed; the code `SECN002` now covers only chacha20 (Native x86) — §4.8.1 |
| Crypto: `keys.*` (generate/derive HKDF/rotate/store) + `Secret` type | **B/C** | FFI JCA/openssl + type-system; opaque `KeyHandle` — §4.8.1 |
| Crypto: asymmetric (ECC/RSA sign/verify) + X.509/PEM | **B/C** | FFI JCA/openssl; versioned format already exists — §4.8.1 |
| Crypto: **PQC** (ML-KEM-768 KEM + ML-DSA-65 sig) | **D (FFI)** | **not available today**; FFI `liboqs` + NIST vectors; never homemade — §4.8.1 |
| Crypto: **hybrid** (ML-KEM + HKDF + AES-256-GCM, anti-downgrade) | **D/C** | versioned protocol; depends on PQC — §4.8.1 |
| Crypto: `secure.channel` (KEM+KDF+AEAD+auth+replay) | **D** | protocol formalization; depends on hybrid — §4.8.1 |
| Cloud providers (AWS/Azure/GCP) | **A/B** | via **interop** (JVM SDK / `kof.process` CLI / `kof.http` REST); typed abstraction = extension |
| FFI (`.so`, C/C++/Rust) | **A→C** | SQLite/FFM already work; **formalize** FFI (compile-time signature) = evolution |
| Java / GraalJS interop | **A** | direct existing interop |
| SSH | **B** | over `kof.process`/FFI (libssh) |
| IaC: records + graph + diff (imperative) | **A/B** | **pure** Kof today (classes/records/functions); diff is normal Kof |
| IaC: `infra "prod" { }` (declarative) | **C** | new parsing block + lowering (desugar over records; reuses `entity`/`test` desugaring) |
| IaC: plan/apply/reconciliation loop | **C** | new namespace + `kof infra` tool; does **not** change the core |
| IaC: cloud providers | **B** | **FFI/REST/CLI** (do not reimplement) |
| Batch/pipeline framework (jobs, checkpoint) | **C** | new namespace `kof.workflow`/`kof.batch` over `spawn`/`channel`/`mq` |
| Typed DataFrame (lazy, columnar) | **C** | new collection type + optimization (`data` namespace) |
| Arrow/Parquet | **A (interop)** | **FFI** to existing Arrow/Parquet; Kof provides the wrapper, not the engine |
| Statistics/probability | **B** | wrapper + FFI (JVM stats / C libs) |
| ML: inference | **B** | `record` + FFI to ONNX/libtorch + `kof.web` (serve) |
| ML: training | **D/A** | orchestrated (Kof calls the trainer via FFI/CLI); not reimplemented |
| ML: autograd/framework | **E** | **non-goal** — do not build an ML framework in Kof |
| GPU (compute) | **D/A** | FFM Vulkan **already works** (seed); generalizing CUDA/OpenCL via FFI = research |
| SIMD / vectorization (Native) | **D** | today scalar XMM; data SIMD in codegen = research |
| Data-parallel / MapReduce | **D** | task-parallel exists; data-parallel = research + FFI |
| Distributed (MPI) | **D** | FFI to MPI + Kof orchestration |
| HPC (numeric, linear algebra) | **A (interop) + C/D** | **FFI to BLAS/LAPACK** (wrapper); non-GC zone via C/Rust |
| GC mark-sweep (Native) | **C** | free-list exists; mark-sweep pending (needed for long pipelines) |
| Event-loop / real async (Native) | **D** | today pthread; event-loop = research (CONC003 is the JS case) |
| Compile-time codegen (gRPC stubs, DDL, infra) | **C** | already exists implicitly (KofRuntime, test runner, `entity` DDL); **formalize** |
| Package manager / registry / capabilities | **C** | `kofdeps` MVP landed (transitive + lock); registry pending ⛔; capability/link by use already has a seed (DSN) |
| Scoped resources (light RAII, no ownership) | **B/C** | today GC + `try/finally`; light scope for FFI/GPU/files |
| Interop reflection | **C** | restricted to interop (ML/science); not a foundation |
| Variance / sealed (type system) | **B/C** | useful for scientific collections/domains; medium cost |
| Forensics (binary/memory/fs) | **C/D (FFI)** | FFI to parse libs + Kof pipelines; do not reimplement parsers |
| Packet / network parsing | **C (FFI)** | `kof.net` + FFI to `libpcap`; sockets already emitted |
| Bio: FASTA/FASTQ/VCF/BAM formats | **C** | official package `kof-bio` with typed records |
| Bio: alignment/variants | **A (interop)** | FFI/CLI to BLAST/htslib; do not reimplement |
| Rich notebooks / IDE / kernel | **E** | **non-goal** (editor tools) |
| Ownership/borrowing in the core | **E** | **non-goal** (Kof is GC; non-GC zone via FFI) |
| Open macros / type-classes / annotations | **E** | **non-goal** (rejected — see §7) |
| DBMS / own SQL engine | **E** | **non-goal** (`kof.db` orchestrates, it is not an engine) |

**Reading:** the vast majority of universal capabilities are **A/B** (already
supported or extension) because the *substrate* (frontend+IR+dispatch+FFI+
concurrency) already exists. The **C** ones are a *new namespace/package/tool* — architectural
evolution **without** changing the core. The **D** ones (SIMD, data-parallel, distributed,
event-loop) are real research. The **E** ones are non-goals that **protect** the
language's identity.

## 14.3 Capabilities that can be added **without breaking the core**

Everything that is **A/B/C** in the table above enters **without changing the
core's semantics** — because it uses the existing mechanism (dispatch table + runtime per
target + FFI + codegen). Concretely, they can be added without breaking the
core: `infra`, `shell`, `ssh`, `workflow`/`batch`, `data`/`dataframe`,
`sci`/`math`, `bio`, `net`/`forensics`, `cloud`/`infra-<provider>` (packages),
formalized FFI, package manager, scoped resources, closed codegen.

**Only** the **D** ones (SIMD/GPU data-parallel/distributed/event-loop) and the
**rejected** ones (ownership/complete effect system/type-classes/open macros)
are also "add without breaking" — but by **research** or by **rejection**,
not by implementation in the core.

---

> **Moved to the implementation doc.** R1–R12 (each with status, owner lane
> and proof) are tracked in [`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`](../development/IMPLEMENTATION-UNIVERSAL-PLATFORM.md)
> §Invariants R1–R12.

# 16. Final mental model

```text
Kof Core  (language + compiler + runtime + tooling — SMALL, STABLE, hardly changes)
   │
   ├── Language      types · control · classes/records · generics · lambdas
   │                 exceptions · spawn/await · minimal IO
   ├── Compiler      single frontend → Kof IR → pluggable backends
   │                 (+ closed codegen · capabilities · FFI · gaps)
   ├── Runtime       JVM (full interop) · Native (systems/deploy) · JS (web)
   └── Tooling       CLI · LSP · fmt · test · bench · debug · package manager
          │
          ▼
     Kof Platform  (the stdlib — grows by small namespaces + capability-gated)
          │
   ┌──────┼──────────┬───────────┬──────────────────┐
   ▼      ▼          ▼           ▼                  ▼
 Systems  Infra     Data      Security           Science
 (web,   (IaC,     (dataframe (crypto, net,     (math, HPC,
  http,   cloud)     + Arrow)   forensics)        bio)
   │      │          │           │                  │
   └──────┴──────────┴───────────┴──────────────────┘
                     │
                     ▼
        Ecosystem  (official packages → community → external/interop)
                     (JVM · .so C/C++/Rust · Python/R · Arrow ·
                      BLAS/LAPACK · CUDA · clouds · databases · CLIs)
```

**The language remains one.** What changes from one domain to another is the
**library/API/runtime/tooling** — never the language, never the IR, never the
targets. What makes this a *universal platform* and *not* a god-language is the
**boundary** (§3.4/§5/§13): the small and stable core, the huge and
modular platform, and the external world **integrated, not owned**.

---

# 17. Conclusion — the answer to the central question

> *How to transform Kof from a language into a universal platform without destroying the
> simplicity and the identity of the language?*

**Answering with what Kof ALREADY has:**

1. **The language does not change.** One syntax, one type system, one compiler, one
   IR, the same targets (JVM/Native/JS). Evolution is 100% through **stdlib +
   packages + interop + tooling**.
2. **The expansion mechanism already exists.** The stdlib as *dispatch tables
   at compile-time* with *diagnosed gaps* is exactly the means by which
   infra, data, security and science enter **without touching the core**.
3. **FFI + interop are the coinage.** Kof **integrates** the world (Arrow, BLAS,
   CUDA, Python/R, clouds, databases, CLIs) instead of owning it. This eliminates
   risk #1 (reimplementing everything) and gives scale to the ecosystem.
4. **Identity is preserved by rules, not by luck.** "Intention, never
   silent, small API, compile-time > magic, small and stable core" —
   they are already the philosophy; here they become **invariants** with concrete
   mechanisms (boundary, layers, capabilities, stability tiers, formal FFI,
   closed codegen, non-goals).
5. **The roadmap by stages** (SYSTEMS → AUTOMATION → INFRA → DATA →
   SECURITY → SCIENTIFIC → BIO → UNIVERSAL) is by **capability/maturity**,
   without dates, and **part of the real state** — without rewriting anything, without opening
   a front now.

At the limit, the final test is simple: **at the end of the universal vision, the Kof core
should have grown almost nothing** — FFI, codegen, capabilities, scoped resources
and little more. If the core bloated to "support" the platform, the god-language
won. If the core stayed small while the platform became huge and
modular, the language kept its identity. **That is what this plan
exists for: to guarantee the second outcome.**

*Vision document. It does not alter, interrupt or replace the work in
progress. The current state of Kof remains 100% intact.*
