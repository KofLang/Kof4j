last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kofbol-legacy-banking-interop

**Rule 6 gate:** KofBOL introduces NEW stdlib namespaces and, if any core
primitive is later deemed necessary (e.g. a decimal scalar), NEW
frozen-semantics surface. This document is **plan only, zero code**. Promotion
requires a maintainer decision (`D-KOFBOL`) plus the future-promotion flow
(`docs/development/future/README.md` §"When to move", `AGENTS.md` §"Future
promotion"). The plan is library-first by construction (`D-KOF-FIRST`): the
first version must be expressible as pure-Kof libraries over primitives that
already exist.

**Source:** maintainer request (30/09/2026).

**Related, already-decided work this plan MUST reuse (not duplicate):**
- `docs/development/kof-connector-ecosystem-plan.md` — `UNDER DEVELOPMENT`
  (`D-CONNECTORS-GO`, `DECISIONS.md:4459`); COBOL is already a declared case
  (`:438-441`, capability row `:467`, phase 7 `:520`) with the guardrail
  "a direction, not a 30-runtime demand" (rule 55, `:448`).
- `libs/interop/` (13 pure-Kof files) — `ForeignModule.kf`, `CAbiConnector.kf`,
  `ConnectorSpi.kf`, `InteropCore.kf`, … the connector scaffolding KofBOL
  plugs into.
- `AbiLayout.java` + `FfiStructLayout.java` + `docs/ffi-abi-structs.md` (D6,
  concluded 23/09) — binary struct layout/arg-class, goldens vs GCC 13.3.
- `KofBuffer.java` (`Buffer(U8)`, D-R3-BUFFER) — the byte surface.
- `KofDb.java`/`KofOrm.java` `transaction { … }` — the transaction pattern.
- `KofObservability.java` — correlation/request ids.
- `docs/development/future/LEGACY_MIGRATION.md` + `DECOMPILER.md` +
  `TRANSLATOR.md` — the (JVM-bytecode) migration cluster; COBOL is named there
  as a *possible frontend* (`LEGACY_MIGRATION.md:185,294,357`).

---

# KofBOL — plan (native legacy-banking interoperability & incremental modernization)

## 0. Purpose

> **Kof does not need to replace the legacy all at once. Kof needs to be able
> to talk to it.**

`KofBOL` is a layered **interoperability and migration** capability for banking
systems built on COBOL, mainframe runtimes and legacy data formats. It is
**not** a new COBOL, **not** a COBOL compiler, and **not** a banking framework.
It is the **bridge**, not the destination.

Final acceptance question (§30):

> Can an old banking system keep working exactly as it does today while a new
> Kof application starts replacing parts of it, one at a time?

## 1. Measured baseline (honest novelty)

Investigation (file-anchored) established exactly what exists:

| Concern | Real state | Evidence |
|---|---|---|
| `KofBOL` | **does not exist** | case-insensitive repo search → 0 hits |
| EBCDIC / code pages | **absent** | 0 hits |
| packed decimal / COMP-3 | **absent** | 0 hits |
| fixed-width / copybook | **absent** | 0 hits |
| CICS / IMS / VSAM / JCL | **absent** | 0 hits |
| MQ / dead-letter / MQ transaction | **absent** | `KofMq.java` is in-memory pub/sub only |
| DB2 driver | **absent** | `DbDrivers.java` = mariadb/mysql/postgres/sqlite |
| decimal / money type | **absent** | `Type.java:7-15` = Int/Long/Double/Float/Char/Byte/Short |
| user-facing raw socket | **absent** | `KofNet.java` is URL-parsing; sockets are internal (`runtime/RuntimeNet.java:12`) |
| COBOL-level interop | **named, unimplemented** | `kof-connector-ecosystem-plan.md:438-441,467,520`; `LEGACY_MIGRATION.md:185,294,357` |
| connector scaffolding | **exists** | `libs/interop/` (13 `.kf`), `D-CONNECTORS-GO` |
| byte surface | **exists** | `KofBuffer.java` (`Buffer(U8)`) |
| binary layout | **exists** | `AbiLayout.java`, `FfiStructLayout.java` |

**Consequence:** the mainframe data concerns (EBCDIC, COMP-3, copybooks,
fixed-width, return codes, batch) are genuinely new; the *scaffolding*
(connectors, FFI/ABI, bytes, transactions, observability) is not. KofBOL is
therefore a **library-first** effort that composes existing seams.

## 2. Non-goals (explicit)

- No new COBOL. No `PERFORM`/`MOVE`/`COMPUTE`/`PIC`/`OCCURS`/`REDEFINES` as Kof
  syntax.
- No JCL reproduction.
- No enterprise framework, no giant abstraction layers.
- Do not assume all legacy is IBM, or all mainframe, or one universal EBCDIC.
- Never convert money to `float`/`double`.
- Never hide return codes; never turn every legacy error into an exception.
- No JVM coupling in the public surface; no self-rolled crypto.

## 3. Layered architecture

```text
Kof Application
       ↓
KofBOL API                 (idiomatic Kof)
       ↓
Legacy Contract            (structured, typed, serializable)
       ↓
Encoding / Marshalling     (EBCDIC, fixed-width, COMP-3, binary, copybook)
       ↓
Transport                  (file, TCP, HTTP, MQ, batch stdio, CICS, DB2…)
       ↓
Legacy System
```

The **business API must not be coupled to the transport**. A contract is
data; a transport is a way to move bytes. They compose but never merge.

## 4. Proposed surface (intent, not final grammar)

### 4.1 Legacy contract

Reuse the language's `record` as the base (there is no need for a new
declaration kind in v1 — open decision Q1):

```kof
legacy Account {
    field accountNumber string fixed(10)
    field branch        string fixed(4)
    field balance       decimal(15, 2) packed
    field status        string fixed(1)
}
```

`legacy` is sugar for a `record` plus a *layout descriptor* (offsets,
encodings, COBOL metadata). It stays explicit, typed, serializable, validatable
and reusable.

### 4.2 Decode / encode

```kof
record = Account.decode(bytes)
bytes  = Account.encode(record)
```

Padding, truncation, alignment, encoding, numeric fields, optional fields and
invalid fields must each have **documented, explicit** behavior and explicit
errors (§10).

### 4.3 Copybook

```text
01 ACCOUNT-RECORD.
   05 ACCOUNT-NUMBER PIC X(10).
   05 BRANCH         PIC 9(04).
   05 BALANCE        PIC S9(13)V99 COMP-3.
   05 STATUS         PIC X(01).
```

A copybook must be **importable** into a Kof contract. Decided approach
(recommended, open decision Q2): a **standalone CLI/import tool** that parses
the copybook and **generates a Kof contract** — the parser does **not** live in
the runtime and does **not** go into the compiler front-end. This keeps the
runtime small and the generated contract reviewable.

### 4.4 Transport / session / transaction (namespaced, not a god-object)

Keep `contract`, `transport`, `session`, `transaction`, `request`, `response`
separate. No `cics_exec`-style leakage:

```kof
conn   = kofbol.connect(transport)
resp   = conn.call(AccountRequest(...))     // response.status/returnCode/message/data
conn.close()
```

### 4.5 Return codes (structured, never hidden)

```kof
response.status       // Kof-level status
response.returnCode   // Int — the legacy RC (0/4/8/12/16/…)
response.message      // String
response.data         // typed payload
```

The developer maps `returnCode` into Kof's error model **explicitly**; KofBOL
never does it silently.

### 4.6 Batch

```kof
job.processCustomerFile {
    input  customers
    output results
}
```

Represents intent (`job`/`step`/`input`/`output`/`returnCode`/`dataset`) and
integrates with the existing environment (stdio/file), never reimplements JCL.

## 5. COBOL type mapping (explicit semantics)

| COBOL | KofBOL representation | Notes |
|---|---|---|
| `PIC X(n)` | fixed-width string, `n` bytes | EBCDIC or ASCII per contract encoding |
| `PIC 9(n)` | unsigned fixed-width digits | zoned/display digits |
| `PIC S9(n)` | signed digits | explicit sign encoding (trailing/leading) |
| `V` (implied decimal) | scale on a decimal value | never float |
| `COMP` / `BINARY` | binary integer | big-endian vs little-endian explicit |
| `COMP-3` (packed) | packed decimal | sign nibble rules explicit |
| `DISPLAY` | character digits | code page explicit |
| `OCCURS` | fixed array (`List<E>` / `E[]`) | count bounds explicit |
| `REDEFINES` | union/overlay view | explicit, validated |
| `88-level` | named condition/boolean | explicit |

Sign, scale, precision, overflow, truncation, rounding and encoding are each a
documented decision; none is assumed.

## 6. Fixed-width / COMP-3 / EBCDIC (the priority core)

- **Fixed-width**: field = `(offset, length, encoding, numeric-kind)`. Layout is
  computed once from the contract (reuse the `AbiLayout` idea conceptually; a
  pure-Kof layout engine is the v1 mechanism).
- **Packed decimal (COMP-3)** is a priority. It must **never** go through
  `float`/`double`. The recommended v1 representation is a **pure-Kof scaled
  integer + explicit scale** (e.g. a `Decimal(unscaled: Long, scale: Int)` value
  type or digit-string), documented for precision/overflow. If a first-class
  decimal scalar is later judged necessary, that is a **core primitive** and a
  rule-6 decision (open decision Q3); v1 must not need it.
- **EBCDIC** is explicit and configurable per contract (`encoding ebcdic …`).
  "EBCDIC" is not one table: the plan requires named **code pages**
  (e.g. CP037, CP500, CP1140) with an explicit, versioned table, not a silent
  backend default. ASCII/UTF-8 conversions are explicit too.

## 7. Adapter / anti-corruption layer

The legacy details (`PIC`, `COMP-3`, EBCDIC, return codes) must **not** leak into
the application. The adapter is the boundary:

```text
Legacy Customer  →  KofBOL Adapter  →  Kof Customer
```

Contracts and adapters live at the edge; the domain model stays clean.

## 8. Incremental modernization (the point)

```text
COBOL → KofBOL Contract → Kof Adapter → Kof Service → Kof Replacement
```

A realistic path (documented as a guide, §15):

```text
1. Identify contract
2. Represent contract in KofBOL
3. Create adapter
4. Put Kof in front of the legacy
5. Migrate one operation
6. Validate behavior
7. Migrate another operation
8. Remove the old dependency
```

Central message: **do not rewrite the whole bank — extract boundaries.**

## 9. Where KofBOL enters the architecture

- **Not a compiler construct in v1.** Library-first: a namespace dispatch table
  (`KofBol.java`) + pure-Kof libraries, mirroring `KofValidation.java` /
  `KofMq.java`.
- **Contracts** are `record`s (existing type system).
- **Bytes** ride `Buffer(U8)` (`KofBuffer.java`).
- **Copybook import** is a CLI subcommand (precedent: `kof-cli/.../Main.java:27-31`
  + `DbDrivers.java` provisioning), producing Kof source — no runtime parser.
- **Connectors** (`kof-cobol-connector`) compose `libs/interop/`
  (`ForeignModule`, `CAbiConnector`) per the connector plan phase 7.
- **Registration** (when promoted) follows the existing mechanics: typer hooks
  (`MethodCallTyper`/`MemberCallTyper`/`BuiltinCallTyper`/`SemUndefinedVarGuard`),
  a lowerer, per-target runtimes (JVM / native x86-64 / native cross riscv64-aarch64
  / JS / Script), the stdlib ledger (`scripts/stdlib_boundary.txt` +
  `scripts/check_stdlib_boundary.sh`) and the R6 parity ledger
  (`docs/backend-parity.md` + `DomainGapCodesTest`).

## 10. Errors and return codes

- Definition/parse errors (contract/copybook): `KOFBOL-P…`.
- Semantic errors (bad layout, unknown code page, scale overflow): `KOFBOL-E1…`.
- Runtime/transport errors (timeout, disconnect, malformed payload): `KOFBOL-E2…`.
- **A non-zero legacy return code is data, not an exception** by default.
- **No match / no message / RC≠0 are values with explicit semantics**, never
  silently swallowed and never automatically thrown.

## 11. Transport abstraction

Separate the axes. Initial transports to investigate, each with an honest
capability matrix per target: **file**, **batch stdio** (`kof.process`),
**TCP** (internal `kof_net_socket_*` exists, needs a surface), **HTTP**
(`kof.http`), **MQ** (extend `kof.mq` beyond in-memory pub/sub), **CICS** (via a
real integration seam if viable), **DB2** (via a pinned driver, precedent
`DbDrivers.java`). IMS/VSAM are lower priority. Not every target must support
every transport; unsupported combinations get an explicit gap code, never a
silent fallback.

## 12. Messaging (MQ-shaped, engine-agnostic)

Design a reusable messaging abstraction (`send`/`receive` with timeout,
correlation id, message id, headers, retry, dead-letter, encoding, transaction
where supported) that IBM MQ can back later — **not** an IBM-MQ-specific API in
the business layer.

## 13. Transactions

Reuse the existing `transaction { … }` pattern. Support begin/commit/rollback/
timeout/correlation/idempotency/retry/boundaries. Do **not** invent a
distributed transaction protocol; integrate with what exists.

## 14. Security & observability

- **Security:** TLS/mTLS, certificates, secrets via `Secret`/`KeyHandle`,
  credential isolation, audit logging, message integrity, replay protection,
  correlation ids, idempotency, input/encoding validation. No self-rolled
  crypto — reuse `KofSecurity`.
- **Observability:** request/correlation/transaction id, system, operation,
  duration, return code, transport status — via `KofObservability`. **Never log
  secrets, tokens, PAN, CVV or full payloads by default**; redaction is opt-in.

## 15. Performance

Support zero-copy where possible, streaming, reusable buffers, persistent
connections, pooling, batching, backpressure. The pipeline
`EBCDIC → String → Object → String → EBCDIC` must **not** be mandatory for every
operation; an efficient byte path is allowed.

## 16. Cross-target (Universal Platform)

Common API; backends vary (`JVM adapter`, `Native adapter`, `JS adapter`,
`WASM adapter`). No Java-specific API in the public stdlib. Capabilities that
exist on only some targets are declared explicitly (gap codes). The contract
data model and the EBCDIC/COMP-3/fixed-width codecs are the **portable core**
(pure Kof) and must be cross-target from the start.

## 17. Documentation deliverables (at promotion)

`docs/` gets: what/why, architecture, legacy contracts, copybooks, fixed-width,
EBCDIC, packed decimal, transports, messaging, batch, transactions, return
codes, error handling, security, observability, migration strategy,
modernization strategy, cross-target, limitations. Plus the guide
**"Modernizing a COBOL system with Kof"** (§8 path).

## 18. Test plan (when implemented)

- **Encoding:** EBCDIC (per code page), ASCII, UTF-8.
- **Numeric:** decimal, packed decimal, COMP, signed, overflow.
- **Layout:** fixed-width, padding, truncation, nested structures, arrays,
  optional fields.
- **Copybook:** parser, conversion, errors, real cases.
- **Transport:** timeout, retry, disconnect, malformed response, correlation.
- **Transactions:** commit, rollback, timeout, failure.
- **Security:** malformed payload, invalid encoding, oversized payload, replay,
  credential leakage.
- **Compatibility:** fixtures representative of real COBOL records (byte-exact
  goldens).
- **Cross-target:** byte-identical decode/encode on JVM + Native + JS + Script.

## 19. Open decisions (rule 6 — maintainer)

1. **Q1 New `legacy` declaration vs `record` + descriptor** — sugar or record?
2. **Q2 Copybook parser home** — standalone CLI/import generator (recommended)
   vs stdlib vs compiler front-end.
3. **Q3 Decimal representation** — pure-Kof scaled-integer value type
   (recommended, no core change) vs a new core decimal primitive (rule-6).
4. **Q4 Scope of v1** — confirm the priority set in §27.
5. **Q5 Which EBCDIC code pages** ship first (CP037/CP500/CP1140…).
6. **Q6 Is COBOL a `kof-cobol-connector` inside `D-CONNECTORS`** (existing
   interop framing) or a standalone KofBOL front? This determines whether a new
   `D-KOFBOL` is needed at all or the work rides `D-CONNECTORS-GO`.
7. **Q7 Transport surface** — expose a raw socket namespace now, or keep
   transports internal and start with file/stdio/HTTP/MQ?
8. **Q8 MQ backend** — pure-Kof messaging abstraction first; which real MQ
   binding(s) later.
9. **Q9 Relationship to `LEGACY_MIGRATION.md`** — is a COBOL→Kof *data contract*
   path part of KofBOL, or strictly interop (existing system consumes/feeds Kof)?
10. **Q10 Batch execution** — pure orchestration vs executing real JCL on the
    host.

## 20. Roadmap (each slice one lane, RED-first; nothing starts from this doc alone)

- **Spike-0 — inventory & contract model.** Lock the `legacy`/descriptor model
  and the layout engine; parser-free.
- **Fatia 1 — fixed-width encode/decode** for `PIC X`/`PIC 9` (ASCII only),
  byte-exact goldens, JVM + Script.
- **Fatia 2 — EBCDIC codecs** (one code page first) with explicit tables.
- **Fatia 3 — packed decimal (COMP-3)** with full sign/scale/overflow tests.
- **Fatia 4 — copybook import CLI** generating Kof contracts.
- **Fatia 5 — `legacy` contract + `decode`/`encode` + `REDEFINES`/`OCCURS`/88-level.**
- **Fatia 6 — adapter + anti-corruption layer + return-code model.**
- **Fatia 7 — transport abstraction** (file/stdio/HTTP first) + capability matrix.
- **Fatia 8 — Native x86-64 + riscv64/aarch64 + JS parity** for the portable core.
- **Fatia 9 — messaging (MQ-shaped) + batch abstraction.**
- **Fatia 10 — transactions + CICS/DB2/IMS/VSAM connectors** (as viable).
- **Fatia 11 — docs + guide + conformance-matrix + fixtures.**

## 21. Deliverables (full implementation, when promoted)

Complete KofBOL implementation; public APIs; contracts; encoding; fixed-width;
packed decimal; copybook support; adapter architecture; tests; fixtures;
documentation; real examples; changelog; and a short technical report
(architecture, decisions, limitations, supported/future targets, risks, next
steps). **No critical TODO; no pseudocode; no spec-only delivery.**

## 22. Non-regression contract

- Additive only; no change to frozen surfaces (operators, precedence,
  evaluation order, null safety, content `==`, String exceptions, spawn/await,
  List/Map/Set).
- No core type/grammar change in v1 (library-first).
- Every slice carries tests in the same commit (`AGENTS.md` Q0–Q7).

## 23. Final review question

> **KofBOL is the bridge, not the destination.**

Answer technically, before calling it done: can the legacy keep running exactly
as today while Kof replaces its parts gradually — and can it finally be
**encapsulated, integrated, gradually replaced and then removed when it makes
sense**, with no big-bang rewrite? If yes, KofBOL is fulfilling its purpose.
