[English](entity-history-plan.md) | [Português](entity-history-plan.pt_BR.md)

# Entity History — temporal audit for `kof.orm` (specification, plan only, zero code)

**Status:** proposal, zero code — `docs/development/future/`
**Requested by:** maintainer (26/09/2026)
**Rule 6 gate:** the declaration surface and the query surface proposed here are
**design decisions**. Before any code, the maintainer locks a `DECISIONS.md`
entry (`D-ENTITY-HISTORY`) with the chosen surface. This document is a
proposal, not an authorization.
**Snapshot:** branch `beta-0.5.0`, tip `12bacc39e`. Every `file:line` below was
measured on that tip.

---

## 1. Context — what Kof has today (measured)

Kof already has a real, multi-backend persistence surface, but **nothing** about
entity history, revisions or temporal audit exists anywhere in the repository.
The relevant measured facts:

- **Entities are a keyword, not annotations.** `entity Name { field: Type
  [generated] [unique] ... }` is parsed and lowered to a **record**
  (`CompilerPipeline.java:228-229` fills `driver.entitySchemas`, then the record
  lowering runs). Typed ORM calls require a declared `entity`; a plain `record`
  yields `ORM002` (`ExpressionOrmCallLowerer.java`).
- **Primary key is implicit.** The PK is the first field marked `generated`;
  with none, the first declared field (`JvmOrmRuntime.java:89`,
  `kof_orm_pkIndex`). There is no `@Id`/primary-key keyword.
- **ORM surface** (`KofOrm.java:137`, `functions()`): `create, save, find, all,
  delete, count, deleteAll, where, saveAll, page, migrate` — plus the typed
  query DSL `Entity.query(db) { where … orderBy … limit … }`
  (`CompilerOrmSupport.lowerQueryDsl`). Target support: `orm.supportedOn`
  = JVM/ANDROID/JS, with `fnSupportedOn` extending faces to NATIVE and cross;
  unsupported combinations refuse with `ORM001` (R6, named).
- **DB surface** (`KofDb.java:80`): `connect, query, execute, close,
  transaction`. Transactions are a **block**, not a call:
  `transaction { … }` — a trailing-lambda call
  (`parser/ExpressionParser.java:149`, lowered to `kof_db_transaction`),
  with nested blocks participating in the outer transaction
  (`jvm/JvmConfigRuntime.java:466-500`; Native `runtime/RuntimeDb4.java:50-83`).
- **No timestamp type.** `time.now()` returns `Long` **epoch milliseconds**
  (`KofTime.java:13`); dates are ISO-8601 `String`s (`todayIso`, `parseDateIso`,
  `formatDateIso`). The ORM column-type map has no timestamp type — a timestamp
  today is a `Long` or a `String` column (`KofOrm.dbType`).
- **No lifecycle hooks, listeners or interceptors.** The only extension
  mechanisms are compiler-side: the `entity` schema map, the internal
  super-bridge/desugar/codegen steps, and the compile-time introspection
  intrinsic `interop.schema(R)` (`CompilerInterop.java:17-19` — explicitly no
  runtime reflection). `@Name` annotations exist but are **interop only**, never
  an ORM mechanism (`training/idioms/database.md:11`).
- **Identity/context exists but is split.** `auth.user()/claims()/hasRole(…)`
  read the current request's bearer token (`KofSecurity.java:231-255`);
  `observability.requestId()/correlationId()/traceId()` exist, but on the JVM
  `requestId()` is a **fresh random UUID per call** and `correlationId()` just
  calls it (`jvm/JvmStringObsRuntime.java:223-229`) — it is *not* bound to the
  HTTP request, contrary to the doc wording. The real per-request id
  (`KOF_LOG_REQUEST_ID`) is internal and not exposed as a Kof function.
- **Serialization:** `json.encode(value) -> String`, `json.decode<T>(json) -> T`
  (`learn/stdlib/json.md`); DB rows come back JSON-shaped. Schema versioning
  exists for **DDL** (`orm.migrate`, `kof_migrations`), never for rows.
- **Docs:** `learn/stdlib/orm.md`, `training/idioms/database.md`,
  `docs/stdlib/DATABASE_VISION.md`, `docs/development/db-parity-plan.md`,
  `DECISIONS.md` §`D-DB-GAPS`. `docs/stdlib/observability.md:134` mentions
  "future audit logging" once, with no design.

**Conclusion of the survey:** entity history would be a genuinely new contract.
Nothing in the current surface needs to be replaced — the feature must be
**additive** and must speak the existing ORM/DB language.

---

## 2. Problem

`created_at` / `updated_at` / `updated_by` answer one question — *who touched
this row last, and when* — and lose everything else. They are insufficient
because:

- **They are lossy.** Only the last state survives; the intermediate states are
  gone. "What was the price on 14/09?" is unanswerable.
- **They cannot attribute a specific change.** `updated_by` says who wrote the
  row last, not who changed *which* field.
- **They are fragile under multi-entity transactions.** A transaction touching
  five rows leaves five unrelated timestamps; there is no shared revision
  context to group them.
- **They are mutable.** Nothing stops the application (or a bug) from writing
  `updated_at` to a lie. A timestamp column is not a record.
- **They do not survive DELETE.** Once the row is gone, `created_at` with it.
- **They do not describe the shape change.** When a field is removed or renamed,
  the old value is simply absent from the schema — there is no record of what it
  used to be.

The need is not "compliance logging" as a product: it is a **data-layer
capability** — the persistence layer can answer *"what did this entity look
like, when, and why"* with the same seriousness it answers *"what is this
entity now"*.

---

## 3. Goals

- Record the **lifecycle** of an audited entity: creation (`INSERT`), change
  (`UPDATE`), removal (`DELETE`).
- Give every recorded change a **revision**, a **timestamp**, an **operation**,
  an **actor** and, when available, a **correlation/request id**.
- Provide a **Kof-native query surface**: history of an entity, state at a
  revision, state at a point in time, changes between revisions, changed fields.
- Keep the capability **opt-in per entity**; audited and non-audited entities
  coexist in the same program and the same transaction.
- Be **backend-honest**: define a contract that a JDBC backend implements first,
  that Native can implement later, and that any future backend can implement —
  without the language contract depending on JDBC, JPA or Hibernate.
- Be **additive and backward compatible**: existing Kof programs, entities,
  databases and migrations keep working untouched.
- Make the audit writes **participate in the app's transactions** (atomicity is
  not optional).
- Expose **honest diagnostics** (R6) when a target/backend cannot provide
  history — never a silent no-op or an empty list pretending to be "no
  changes".

## 4. Non-goals

- **Not event sourcing.** The entity table stays the source of truth of the
  current state; history is a derived record, not the write model.
- **Not CQRS.** No read-model projection, no command/query separation.
- **Not an application log replacement.** `kof.log` and `kof.observability`
  keep their roles; entity history does not capture arbitrary events or
  free-text messages.
- **Not a compliance product.** Retention/legal-hold/erasure workflows are
  *consumers* of the capability, not part of its core contract.
- **Not Hibernate/JPA/Envers.** No `@Audited` annotations, no `AuditReader`, no
  programmatic `RevisionListener` — the Kof surface is its own.
- **Not tied to one database.** The contract is logical; the physical storage is
  a backend decision (JDBC first, others later).
- **Not bitemporal.** The spec records when the write happened (transaction
  time). "Valid time" vs "transaction time" (a full bitemporal model) is an
  explicit **future extension**, not the core.
- **Not a security feature.** Immutability/PII/retention are documented as
  limits and consumers (§14), not solved by the core.
- **Not automatic for every entity.** Opt-in only (§20).
- **Not an ORM `@Version`/**optimistic-locking replacement.** Row versioning for
  concurrency control is a separate concern; this spec does not define it.

## 5. Motivation

The Kof philosophy is *intention, not mechanism*, and *complexity belongs to the
platform*. Today a Kof developer who needs history must either denormalize audit
columns by hand, add triggers in the database (mechanism, not intention), or
pull in a Java framework — all of which break the multi-backend promise. A
first-class *history intention* on the entity lets the compiler and the backend
implement the mechanism once, so application code writes what it means
(`User.history(db, id)`), not how to reconstruct it.

It also closes a real architectural gap: Kof already versions its **schema**
(`orm.migrate`) with no row-level counterpart, and already has the building
blocks (entities as compile-time metadata, a transaction block, `array`
serialization, actor/correlation surfaces) — the feature is an integration of
existing pieces, not a new universe.

## 6. Concepts

| Concept | Definition |
|---|---|
| **Audited entity** | A declared `entity` carrying the history capability (opt-in). Non-audited entities have no history and no overhead. |
| **Revision** | A monotonic, per-entity integer ordering the recorded states of one entity (`1, 2, 3, …`). Revision `0`/absent means "no history". |
| **Audit record** | One stored fact: `(entity, entityPk, revision, operation, at, actor, correlationId, payload)`. |
| **Operation** | One of `INSERT`, `UPDATE`, `DELETE` (closed set in the core; extension is an Open Question). |
| **Actor** | Who caused the change: an authenticated user, a service, a job, a migration, an internal process, or the system. Represented as a `String` id with a kind (see §11). |
| **Timestamp (`at`)** | Instant of the record, `Long` epoch milliseconds (matching `time.now()`), with ISO-8601 accepted as query input. |
| **Historical state** | The entity's mapped field values as of a revision — a value of the entity's own record shape (or `null` if it did not exist at that point). |
| **Field change** | `(revision, field, previous, current)` — one mapped field that changed in a revision. Values are represented portably (see §10). |
| **Revision context** | The grouping of all audit records written by **one transaction**, identified by a group id and carrying the transaction's actor/correlation id. |
| **Correlation id** | An application-supplied or context-derived id tying a change to a request/workflow. Optional; may be absent. |

## 7. Conceptual model

### 7.1 Operations

- `INSERT` — the entity is created. The record captures the state **after**
  insertion (including any generated PK). Revision = 1 for that entity.
- `UPDATE` — an existing entity is persisted with changed mapped fields.
  Revision = previous + 1. An update that changes nothing produces **no**
  revision (no-op writes are not history).
- `DELETE` — the entity is removed. Revision = previous + 1, and the record
  captures the **last known state** so that the entity's history and its final
  shape remain queryable after deletion.

Restoration is **not** a core operation: restoring is "read a historical state,
then `save` it as a new revision". It needs no new API (§28 discusses an
optional sugar).

### 7.2 State representation strategies

| Strategy | What is stored per revision | Reconstruction |
|---|---|---|
| **Full snapshot** | all mapped field values of the entity | O(1) — read the record |
| **Diff only** | only fields that changed | O(revisions) — replay from revision 1 |
| **Snapshot + diff** | the changed fields **and** a materialized full snapshot | O(1) read, O(1) diff query |

**Recommended: snapshot-per-revision (full snapshot of the audited entity) with
diffs computed by the contract at read time.** Justification:

- **Reconstruction is O(1)** and cannot be corrupted by a lost intermediate
  record — the state is self-contained.
- **Schema evolution is honest** (§16): an old snapshot holds exactly the fields
  that existed then; removed fields remain visible, added fields are absent.
- **Diff is a read concern**, not a write concern: `changes(id)` can compare
  consecutive snapshots without a second storage format.
- **Write cost is bounded** by entity width — acceptable for the entity sizes
  Kof targets; huge entities are addressed in §15 (per-entity opt-out of fields
  is a future extension).
- A backend **may** additionally materialize diffs or deltas as an optimization,
  as long as the contract's semantics (below) hold; the physical choice is left
  to the backend (§17).

Tombstones: `DELETE` stores the final snapshot plus the `DELETE` operation, so
"state at revision N after deletion" is answerable and "existed at time T"
is `false` only for T after the delete.

### 7.3 Revision identity

Two ids appear conceptually:

- **Entity revision** (`1, 2, 3, …` per entity): the user-facing ordering used
  by `at(id, revision)` and `changes(id, from, to)`. **Recommended** as the
  contract's revision semantics — per-entity monotonic and deterministic, which
  makes it portable across backends with different global-sequence abilities.
- **Revision context id** (one per transaction): groups all records written by
  the same transaction; not the user-facing "revision". Its shape (global
  counter, UUID, `(at, group)`) is a backend decision.

## 8. Architecture

The contract is layered so that the language never depends on a driver:

```
                 Kof language (compiler frontend)
                         |  entities + history intention (metadata)
                 Kof ORM contract  ("what history means")
                         |
        +----------------+----------------+
        |                |                |
     JDBC backend    Native backend   Future backend
     (first)         (later)          (WASM/hosted/…)
```

- **Language/frontend** owns: the opt-in declaration (metadata on the entity),
  the query surface's type rules, and compile-time diagnostics. It knows nothing
  about SQL or tables.
- **ORM contract** owns: the semantics of `INSERT/UPDATE/DELETE` recording,
  revision ordering, the shape of the query results, atomicity coupling to
  transactions, and the error/capability model. It is expressed as compiler
  metadata + runtime entry points, exactly like the existing `kof_orm_*` faces.
- **Runtime** owns: reading `time`, resolving the actor/correlation context,
  and the write/read orchestration.
- **Backend** owns: the physical schema, indexes, SQL/asm, and any
  optimization — hidden behind the contract.
- **Database** owns: durability, the actual types, constraints and storage.

This preserves *"Kof does not need to own everything; Kof needs to be able to
integrate everything"*: history is an **integration contract**, not a bundled
framework.

## 9. Language / API

> Everything in this section is a **proposal**, not an existing API. The current
> corpus uses entity-static calls for typed ORM (`Entity.query(db)`) and
> namespace functions (`orm.*`), so both options below are evaluated against
> that convention.

### 9.1 Declaration (opt-in)

**Primary proposal — a declaration modifier, mirroring `generated`/`unique`
(no annotations):**

```kof
audited entity User {
    id: Long generated
    name: String
    email: String
}
```

Alternatives evaluated (both weaker):

- `audit User` as a standalone declaration — separates capability from the
  entity, invites drift, and reads as a verb with no subject.
- `@Audited` annotation — violates `training/idioms/database.md:11`
  ("never annotations" for the ORM) and is invisible to Native by design.

The modifier is compiled into the entity's metadata (an `audited` flag beside
`generated`/`unique`), so the compiler can reject history calls on non-audited
entities at compile time (`HIST002`, §20).

### 9.2 Query surface

**Primary proposal — entity-static calls, consistent with `Entity.query(db)`:**

```kof
var rs: List<Revision>        = User.history(db, id)
var r:  Revision              = User.history(db, id, 3)          // one revision
var u:  User?                 = User.at(db, id, 3)               // state at revision
var u2: User?                 = User.atTime(db, id, "2026-09-26T12:00:00Z")
var cs: List<FieldChange>     = User.changes(db, id)             // all changes
var cs2: List<FieldChange>    = User.changes(db, id, 2, 5)       // between revisions
var rs2: List<Revision>       = User.history(db, id, offset, limit) // pagination
```

Semantics and result types:

| Call | Returns | Notes |
|---|---|---|
| `E.history(db, id)` | `List<Revision>` | oldest → newest; empty when no history |
| `E.history(db, id, revision)` | `Revision?` | one revision; `null` when absent |
| `E.history(db, id, offset, limit)` | `List<Revision>` | pagination mirroring `orm.page<T>(db, offset, limit)` |
| `E.at(db, id, revision)` | `E?` | mapped state at that revision; `null` if the revision does not exist |
| `E.atTime(db, id, iso)` | `E?` | state as of the last revision at or before `iso`; `null` before existence |
| `E.changes(db, id)` | `List<FieldChange>` | field-level changes across the whole history |
| `E.changes(db, id, from, to)` | `List<FieldChange>` | inclusive range |

**Rejected alternative:** a global `audit.*` namespace (`audit.User.history(id)`).
It introduces a second naming universe for something the entity already names,
and duplicates the entity-static convention. It is documented here only because
the brief raised it.

**Return types** (compiler-known records, proposed):
`Revision(revision: Long, at: Long, operation: String, actor: String?, correlationId: String?)` and
`FieldChange(revision: Long, field: String, previous: String?, current: String?)`.
Values in `FieldChange` are **portable strings** (canonical JSON fragments for
composites) so the contract survives type changes; typed accessors are a future
extension (§28). `E.at`/`E.atTime` return the entity's own record type (`E?`).

### 9.3 Semantics of edge cases

- **Entity does not exist today, but had history:** `history`/`changes` still
  return it (the history outlives the row); `at` returns the state at that time.
- **After `DELETE`:** the `DELETE` revision is the last one; `at(last)`,
  `atTime(iso after delete)` returns `null` (the entity did not exist), while
  `at(revision before delete)` returns the old state.
- **Entity without history (audited but never written, or revision 0):**
  `history`/`changes` return empty lists; `at`/`atTime` return `null`.
- **Non-audited entity:** compile error `HIST002` — never a silent empty list.
- **Invalid argument** (`revision <= 0`, `from > to`, malformed ISO timestamp,
  negative `offset`/`limit`): programmer error `HIST005` at compile time when
  constant, otherwise a named runtime error (§20).
- **Ordering guarantee:** records are returned in ascending revision order.

## 10. Data model (logical persistence contract)

The contract requires two logical structures; **names are illustrative** — a
backend may choose any physical names, types and indexes:

```
audit_revision(
    revision_id      -- revision-context id (per transaction)
    entity           -- audited entity name (string)
    entity_pk        -- primary-key value of the affected entity (portable string)
    revision         -- per-entity monotonic revision (1..n)
    operation        -- INSERT | UPDATE | DELETE
    at               -- epoch millis
    actor            -- actor id (nullable)
    actor_kind       -- user | service | job | migration | internal | system
    correlation_id   -- nullable
    payload          -- full snapshot of the mapped fields (JSON text)
)
```

Minimum information needed to reconstruct history: entity name, PK value,
per-entity revision, operation, timestamp, actor(+kind), correlation id, and the
field snapshot. A backend needs a uniqueness guarantee on
`(entity, entity_pk, revision)` and an index supporting
`(entity, entity_pk, revision)` and `(entity, entity_pk, at)` lookups.

Nothing in the contract requires a specific column type, a specific PK strategy,
or a specific JSON encoding — only that the *reader* can produce the document
shapes in §9.

## 11. Semantics of context (actor, timestamp, correlation)

Per audit record:

- **Timestamp** is taken by the **runtime at the moment of the transaction's
  audit write** (not by the application), unless a backend must derive it. All
  records written by one transaction share the same timestamp and revision
  context — this is what makes a multi-entity transaction coherent.
- **Actor** resolution order (proposal):
  1. explicit override in scope (a future sugar, e.g. a block — §27);
  2. `auth.user()` when the request is authenticated
     (`KofSecurity.java:231-255`), `actor_kind = user`;
  3. an application-declared default for the scope (job/migration/service),
     `actor_kind` accordingly;
  4. otherwise `actor_kind = system`, `actor = null` — **never** a lie.
- **Correlation id**: from the runtime context when one exists; otherwise absent.
  **Measured caveat:** today `observability.correlationId()` on the JVM is a
  fresh random UUID per call (`jvm/JvmStringObsRuntime.java:223-229`) and is not
  request-bound — wiring it (or the internal `KOF_LOG_REQUEST_ID`) to a real
  request context is a **prerequisite** and an Open Question (§27), not an
  assumption of this spec.
- The contract must represent changes performed by users, services, jobs,
  migrations, internal processes and the system — hence `actor_kind` is part of
  the logical model, not an afterthought.

## 12. Transactions

- **Atomicity is mandatory.** Audit records are written **inside the same
  database transaction** as the entity change; a rollback rolls back the audit
  record with it. The existing `transaction { … }` block
  (`parser/ExpressionParser.java:149`, nested-participating on JVM and Native)
  is the natural carrier — no new transaction primitive is proposed.
- **Multiple entities in one transaction:** each affected entity gets its own
  per-entity revision; all records share one **revision context id**, one
  timestamp and the same actor/correlation id.
- **The same entity changed several times in one transaction:** the proposal is
  to record **one revision per persisted state change** in order, or — a valid
  alternative — collapse to the last state with a single revision for the
  transaction. This is an **Open Question** (§27/Q4); the recommendation is
  "one revision per `save`", because collapsing loses intermediate states the
  feature exists to preserve.
- **A transaction that fails after writing the entity (before commit):** the
  audit record is not visible after rollback; the revision counter is not
  consumed in a way that leaves holes observable to readers (holes are
  acceptable structurally but readers must order by revision, not count them).

## 13. Concurrency

- **Two transactions changing the same entity:** each writes inside its own DB
  transaction; the revision is assigned at write time. The uniqueness guarantee
  on `(entity, entity_pk, revision)` forces serialization (one transaction
  retries or fails deterministically) — this must be specified by the backend,
  never left to a silent lost revision.
- **Ordering:** revisions order the states; **equal timestamps are allowed**
  (two transactions can share a millisecond) and must not be used for ordering.
- **Isolation:** the contract inherits the database's isolation; the spec
  requires only that **a committed revision is never later mutated** and that
  concurrent writers cannot interleave two states under one revision.
- **Revision id generation:** per-entity, assigned atomically with the write
  (sequence/`MAX(revision)+1` under a unique constraint, or backend-native).
  A global revision-context id generation strategy is backend-specific.
- **Reads** must be consistent: a history query never sees a half-written
  transaction (it is inside/outside the DB transaction, per isolation).

## 14. Security and privacy (limits, not a full feature)

- **Who reads:** history can expose deleted data and old PII — read access must
  be a **separate authorization decision** from reading the current entity
  (proposal: the backend/application guards the history call; the core does not
  invent a policy language).
- **Who writes:** audit writes are performed by the ORM/runtime; there is **no
  public write/update/delete API for audit records** in the contract — that is
  the primary tamper-resistance measure at the language level.
- **Tampering:** the contract forbids updating history; true tamper-evidence
  (hash chains, signatures) is a **future extension**, not core.
- **Sensitive data:** an audited entity may contain secrets/PII. The spec's
  requirement is **field-level exclusion** (`not audited` marker — a future
  extension, §28) and the honest statement that by default the full mapped
  snapshot is recorded; **do not** audit credential material. Masking,
  encryption-at-rest and redaction are consumers/database concerns.
- **Retention/erasure:** documented in §15 as a contract option, not an
  automatic policy. GDPR-style erasure conflicts with immutable history and is
  an explicit Open Question (§27/Q10).

## 15. Performance and retention

- **Write overhead:** one extra row per persisted state change of an audited
  entity (snapshot strategy), written in the same transaction. Non-audited
  entities pay **zero**. Expected overhead is proportional to audited entity
  width, not to table size.
- **Read overhead:** `history`/`changes` are indexed range scans on
  `(entity, entity_pk, revision)`; `at`/`atTime` are single-row lookups;
  `atTime` additionally uses the `(entity, entity_pk, at)` index.
- **Pagination is required** in the API (`offset/limit`) and must be pushed down
  by the backend where possible (no full-history materialization for a page).
- **High-frequency entities:** the snapshot strategy multiplies storage; the
  documented mitigations are (a) field exclusion, (b) backend delta
  materialization, (c) retention/archival — the first two are future
  extensions, the third is a backend policy.
- **Retention contract (optional, deliberate):** the core exposes **no**
  automatic expiry. If a backend supports retention, it must be **explicit**
  (a migration/declaration), and history reads must state honestly when
  revisions were expired (`HIST004`, §20) rather than silently returning a
  shorter history. Archival/partitioning are backend concerns (§17).

## 16. Schema evolution (mandatory analysis)

Example: `User { name }` → `User { name, email }`.

Because each revision stores a **self-describing snapshot** keyed by field
name, old revisions remain interpretable:

- **Field added:** old snapshots simply lack it; `at(oldRevision)` returns the
  entity with the field absent/defaulted per the record's rules, and
  `changes` reports it as appearing when it was first written (previous = absent).
- **Field removed:** old snapshots still carry it; the mapped current entity no
  longer has the component, so the removed field is visible only through the raw
  payload (a future `payload`/raw accessor, §28) or through `changes`.
- **Field renamed:** without a mapping declaration, this reads as
  remove+add — honest, if noisy. An explicit rename mapping in the migration is
  a **future extension**; the spec requires the contract to *allow* such a
  mapping without changing the stored payloads.
- **Type changed:** `FieldChange` carries portable `String` representations, so
  a value `"42"` (Int) vs `"42"` (String) is distinguishable only by the raw
  payload; typed historical accessors are a future extension. The contract must
  not crash on type changes — it degrades to the portable representation.
- **Entity removed:** the history tables are independent of the entity table, so
  the history of a deleted entity **remains queryable by entity name**. Whether
  removing the declaration should purge history is **explicitly not automatic**
  (Open Question §27/Q9).
- **Relationship changed:** the core records **mapped scalar fields** of the
  audited entity. Full relationship-graph snapshots (following references) are
  **out of scope** for the core (Open Question §27/Q8) — the honest boundary is:
  audit the foreign-key value, not the referenced entity's own history.

## 17. Backend portability — who owns what

| Layer | Owns |
|---|---|
| Language contract | the declaration modifier, the query surface, compile-time diagnostics, result shapes |
| Kof ORM | the semantics of recording, revision ordering, transaction coupling, capability/errors |
| Runtime | clock, actor/correlation resolution, orchestration |
| Backend | physical schema, SQL/asm, indexes, concurrency primitives, optimizations |
| Database | durability, types, constraints, isolation |

The language **must not** name tables, SQL, JPA or JDBC. Consequence: an
entity-history call compiles once and is resolved per target; where a target has
no implementation, the compiler/runtime **refuses by name** (`HIST001`), exactly
like `ORM001`/`DB001` today (R6). The first implementation target is **JDBC**
(JVM, where transactions and the ORM already exist), but that ordering is an
implementation decision, never part of the contract.

## 18. Interoperability

- **JDBC (JVM):** natural first backend; SQL against the same connection as the
  entity writes, joining `transaction { … }`.
- **Native (x86-64, riscv64/aarch64):** the DB/ORM faces already exist
  (`kof_db_*`, `kof_orm_*`, SQLite/MySQL wire); history is another ORM face, so
  the native route is "emit the same contract in asm/runtime", not a redesign.
- **Cross targets without DB (JS/WASM/MCU):** honest refusal (`HIST001`), with
  capability documented per target.
- **Hybrid apps:** because history is data-layer, a Kof service and a Java
  service sharing the same database can both consume the audit tables as long
  as they agree on the physical schema (a compatibility note, not a contract
  obligation).

## 19. Observability

- Entity history **does not replace** `kof.observability`. The relationship is
  complementary: history stores *entity* lineage in the database; observability
  stores *operation* lineage in traces/metrics/logs.
- The **correlation id is the join key**: when tracing and history share a
  correlation id, a trace can point at the revisions it produced, and a
  revision can point at its trace.
- **Do not duplicate responsibilities:** the core records no log messages, no
  span data and no metrics. The one shared primitive is the correlation id —
  and today that primitive is not request-bound (§11), which is a prerequisite
  to fix at the observability layer, not here.

## 20. Error semantics and capability model

New named codes (proposal; exact numbering to be assigned with the maintainer):

| Code | Class | Meaning |
|---|---|---|
| `HIST001` | capability | The target/backend has no entity-history implementation (named refusal, R6) |
| `HIST002` | compile | History query on a non-audited entity |
| `HIST003` | runtime | Audit write failed (e.g. uniqueness/serialization conflict not retried) |
| `HIST004` | runtime | The requested revision was expired/archived by an explicit retention policy |
| `HIST005` | compile/runtime | Invalid argument: `revision <= 0`, `from > to`, malformed ISO, negative window |

Not-found is **not** an error: `E.at`/`E.atTime` return `null`, `history`/
`changes` return empty — consistent with `orm.find<T>` returning `T?`.

**Capability model (proposal):**

- **Opt-in per entity** (`audited entity`). Default **off** — compatibility.
- **Capability of the target/backend:** required to be *declared*; absence is a
  named refusal, never a silent no-op.
- **Capability of the ORM contract:** always present (what history means is not
  optional once an entity is audited).
- **Not mandatory globally:** there is no "audit everything" switch in the core
  (a future extension could add a project-wide default).

## 21. Compatibility

- **Existing Kof code:** unaffected — the modifier and the calls are new syntax;
  programs that do not use them compile and run unchanged.
- **Existing entities:** unaudited; zero overhead; no migration required.
- **Existing databases:** no change until an entity is marked audited and the
  history structure is created (an explicit `orm.migrate`-style step, not an
  implicit DDL at runtime).
- **Migrations:** additive DDL (new structures); existing migrations untouched.
- **Future versions:** the declaration and query surface are additive; the
  physical schema is behind the contract, so a backend may change its storage
  without changing Kof code.

## 22. Examples (illustrative; syntax follows current Kof)

> These examples are proposals. They follow the real surface where it exists
> (`entity`, `orm.*`, `db.connect`, `transaction { }`, `auth.*`) and the proposed
> history surface where it does not.

**1 — audited entity + creation**

```kof
audited entity User {
    id: Long generated
    name: String
    email: String
}

main() {
    var db = db.connect("sqlite:app.db")
    orm.migrate(db, "create-users", "create table User(id integer primary key autoincrement, name text, email text)")
    var u = User(0, "Mel", "mel@example.com")
    orm.save(db, u)                    // revision 1: INSERT
}
```

**2 — update**

```kof
transaction {
    var u = orm.find<User>(db, id)
    var changed = User(u.id, "Mel Santos", u.email)
    orm.save(db, changed)              // revision 2: UPDATE (name)
}
```

**3 — full history**

```kof
var rs = User.history(db, id)
for (var r in rs) {
    println(r.revision + " " + r.operation + " " + r.actor)
}
```

**4 — state at a revision**

```kof
var at2: User? = User.at(db, id, 2)
if (at2 != null) {
    println(at2.name)
}
```

**5 — state at a point in time**

```kof
var then: User? = User.atTime(db, id, "2026-09-26T12:00:00Z")
```

**6 — changes between revisions**

```kof
var cs = User.changes(db, id, 2, 5)
for (var c in cs) {
    println(c.field + ": " + c.previous + " -> " + c.current)
}
```

**7 — who changed which field**

```kof
for (var c in User.changes(db, id)) {
    println("rev " + c.revision + " " + c.field)
}
```

**8 — delete**

```kof
orm.delete<User>(db, id)               // new revision: DELETE (final snapshot kept)
```

**9 — history after delete**

```kof
var before = User.at(db, id, 2)        // still available
var now = User.at(db, id, User.history(db, id).size)   // null (deleted state)
```

**10 — actor / correlation context**

```kof
// inside a web route, auth.user() resolves the actor automatically
app.post("/users") {
    transaction {
        var u = User(0, body(), "x@example.com")
        orm.save(db, u)                // actor = auth.user(); correlation = context
    }
}

// a migration/job declares its own actor (proposed sugar, §28)
audit.as("migration-2026-09") {
    transaction { /* writes */ }
}
```

## 23. Testing strategy (when implemented)

- **Recording:** INSERT/UPDATE/DELETE produce exactly one revision each; no-op
  update produces none; the final snapshot on DELETE is preserved.
- **Queries:** `history` ordering and pagination; `at` by revision; `atTime`
  boundaries (before existence, between revisions, equal timestamp, after
  delete); `changes` ranges and field pairs.
- **Transactions:** commit writes records; rollback writes none; multi-entity
  transaction shares the revision context; same entity twice (per the Q4
  decision); nested `transaction { }` participates.
- **Concurrency:** two writers on the same entity serialize; no lost revision;
  equal timestamps still order by revision.
- **Context:** authenticated actor, anonymous/system actor, job/migration
  override, correlation id present/absent.
- **Schema evolution:** field added/removed/renamed/type-changed; entity removed;
  old snapshot still readable.
- **Capability:** `HIST001` on unsupported target; `HIST002` on a non-audited
  entity; `HIST004` where retention is explicit.
- **Cross-backend:** the same program yields the same history semantics on JDBC
  and (later) Native; divergent cases fail by name, never silently.
- **Compatibility:** existing ORM suites stay green; non-audited entities pay
  nothing; migrations remain additive.
- **Golden/per-target:** byte-stable query results and named diagnostics per
  target (house pattern: E2E with golden output + backend parity matrices).

## 24. Rollout (incremental; not a commitment)

- **Phase 1 — Contract + metadata (this spec).** Lock the declaration and query
  surface in `DECISIONS.md`; define the logical model and codes.
- **Phase 2 — Metadata + JDBC backend.** `audited entity` metadata, the history
  tables, recording on `INSERT/UPDATE/DELETE`, writes inside
  `transaction { … }`.
- **Phase 3 — Historical queries.** `history`/`at`/`atTime`/`changes` +
  pagination on JDBC, with E2E goldens.
- **Phase 4 — Context.** Real actor resolution and a request-bound correlation
  id (depends on an observability fix).
- **Phase 5 — Native backend.** The same contract in the native ORM runtime.
- **Phase 6 — Advanced.** Retention/archival, typed historical accessors, field
  exclusion, tamper-evidence — each with its own decision.

## 25. Comparison with Hibernate Envers (conceptual reference only)

Envers solves the same *problem* — auditing entity changes with revisions — and
this spec borrows the **questions**, not the answers:

| Aspect | Envers | This proposal (Kof) |
|---|---|---|
| Declaration | `@Audited` annotations | `audited entity` (keyword, opt-in) |
| Revision model | global revision entity with a listener | per-entity revision + transaction revision context |
| Read API | `AuditReader` (imperative Java API) | entity-static Kof calls returning Kof records |
| Storage | generated `_AUD` tables per entity | backend-chosen structures behind one contract |
| Coupling | JPA/Hibernate lifecycle | ORM contract + `transaction { }`, driver-independent |
| Configuration | annotations, properties, listeners | language surface + capability model (R6) |

**Envers is not the API of Kof.** The concept (append-only revisions with
state queries) is; the mechanism belongs to the backend.

## 26. Name evaluation

| Candidate | Assessment |
|---|---|
| Temporal Entity Audit | accurate but long; "temporal" suggests bitemporal semantics the core does not have |
| ORM Audit | ties the concept to one subsystem; history is a data-layer concept |
| Entity Revision | describes an artifact, not the capability; "revision" is one concept inside it |
| Entity History ✅ | **chosen** — says the intention ("the history of this entity") with no borrowed framework baggage; matches Kof's intent-first naming |
| Audit (alone) | overloaded in this repo: `docs/audits/`, security audit logging — ambiguous |

**Decision:** the concept is **Entity History**; the surface keyword is
`audited`; the query prefix is the entity itself (`User.history(db, id)`); the
document is `entity-history-plan.md`. "Revision", "audit record" and
"correlation id" remain internal concept names.

## 27. Open questions (decisions the maintainer must make)

- **Q1. Declaration surface:** `audited entity` vs `history entity` vs a clause?
  (Recommendation: `audited entity`.)
- **Q2. Query surface:** entity-static (`User.history(db, id)`) vs an `audit.*`
  namespace? (Recommendation: entity-static, mirroring `User.query(db)`.)
- **Q3. Return shapes:** are `Revision`/`FieldChange` compiler-known records, or
  plain `Map`/JSON? (Recommendation: known records; typed historical accessors
  later.)
- **Q4. Same entity changed N times in one transaction:** one revision per
  `save`, or one collapsed revision per transaction? (Recommendation: per
  `save`.)
- **Q5. Revision semantics:** per-entity sequence (recommended) vs global
  revision id exposed to users?
- **Q6. Record `INSERT` even when the entity is created outside the ORM
  (`orm.save` only)?** What about raw `db.execute` touching an audited table?
  (Proposal: only ORM paths are audited; raw SQL is out of contract.)
- **Q7. `atTime` semantics:** last revision at-or-before the instant
  (recommended) vs first after?
- **Q8. Relationships:** audit FK values only (recommended) vs graph snapshots?
- **Q9. Entity removal:** keep history queryable (recommended) vs purge on
  declaration removal?
- **Q10. Erasure/retention:** how does the contract reconcile immutable history
  with PII erasure (masking, crypto-shredding, exemption)? Contract option only.
- **Q11. Correlation id prerequisite:** fix `observability.correlationId()` to be
  request-bound, or expose `KOF_LOG_REQUEST_ID`? Which layer owns it?
- **Q12. Actor override sugar:** needed in the core, or is the app-level default
  enough for v1?
- **Q13. Storage strategy:** is snapshot-per-revision acceptable as the required
  baseline, with diff materialization optional per backend? (Recommendation: yes.)
- **Q14. Field exclusion:** `not audited` marker in the entity — core or
  extension?
- **Q15. `HIST0xx` code numbering:** assign the block with the maintainer and add
  it to the parity matrix.

## 28. Future extensions

- Field exclusion (`not audited`), typed historical accessors, raw payload
  access, rename mappings for schema evolution.
- Retention/archival/partitioning declarations; soft-delete vs hard-delete
  policy.
- Bitemporal model (valid time vs transaction time).
- Tamper-evidence (hash chain/signature) and crypto-shredding for erasure.
- Relationship-graph snapshots and "as-of" joins across entities.
- Correlation with `kof.observability` spans/metrics as a first-class join.
- A project-wide audit default (opt-out instead of opt-in).

## 29. Measured evidence (file:line on tip `12bacc39e`)

- `kof-compiler/src/main/java/dev/kof/compiler/KofOrm.java:137` — ORM functions.
- `kof-compiler/src/main/java/dev/kof/compiler/KofOrm.java:47` — `orm` namespace.
- `kof-compiler/src/main/java/dev/kof/compiler/CompilerPipeline.java:228-229` —
  entity schemas registered from `EntityDeclarationNode`.
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmOrmRuntime.java:89` —
  `kof_orm_pkIndex` (first `generated`, else first field).
- `kof-compiler/src/main/java/dev/kof/compiler/KofDb.java:80,110` — DB functions
  incl. `transaction`.
- `kof-compiler/src/main/java/dev/kof/compiler/parser/ExpressionParser.java:149`
  — `transaction { … }` trailing-lambda call.
- `kof-compiler/src/main/java/dev/kof/compiler/KofTime.java:13` — `time.now()`
  epoch millis.
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmStringObsRuntime.java:223-229`
  — `requestId()` random per call; `correlationId()` delegates to it.
- `kof-compiler/src/main/java/dev/kof/compiler/CompilerInterop.java:17-19` —
  `interop.schema(R)` compile-time, no runtime reflection.
- `docs/stdlib/observability.md:134` — the only "future audit logging" mention.
- Related plans: `docs/development/pagination-plan` (windowing),
  `docs/development/memory-safety-plan.md`, `docs/development/db-parity-plan.md`,
  `docs/stdlib/DATABASE_VISION.md`, `DECISIONS.md` §`D-DB-GAPS`.

**No implementation was performed.** This document is the entire change.
