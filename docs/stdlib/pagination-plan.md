[English](pagination-plan.md) | [Português](pagination-plan.pt_BR.md)

# Native pagination — first-class windowing intention (implementation plan)

**Status:** CONCLUDED 29/09 — promoted 28/09/2026 from `future/` to
`docs/development/` (`D-PAGINATION`, `D-FUTURE-BATCH-2809B`, `D-FUTURE-PROMOTION`),
slices P0→P6 all landed, moved to `docs/stdlib/` (3-state rule).
**Requested by:** maintainer (23/09/2026)
**Rule 6 gate:** RESOLVED — the maintainer locked `DECISIONS.md` §`D-PAGINATION`
(surface below in §19/§20). This document now records the real state + how to
finish; it is no longer a "plan only".
**Surface locked (28/09/2026):** window type `Window<T>`; introduce the new type;
`total` via a flag on the window call; global default max; the in-memory part
starts now (rides `D-MULTIPARADIGMA-GO` Phase 1); keep `orm.page` alongside
`orm.window` (no bump); `offset` only on the windowed method (not the typed DSL);
HTTP helper `pageRequest(...)` in `kof.web`.
**How to finish:** followed §20 order P0→P6 (each slice independently proven) — COMPLETE 29/09, no further slice declared.
P1 (`slice`/`take`/`drop`) + P2 (`Window<T>` + `window(...)`) LANDED 28/09;
**P4 (`orm.window`) + P6 (docs/corpus) LANDED 29/09**, and **P5
(`kof.web.pageRequest`) LANDED 29/09** (`D-PAGINATION-P5-SHAPE`) — P0→P6 all
closed. **P3 is OUT of the locked surface** — see the correction below.
**Correction (29/09/2026):** §7.4/§10/§15/§16/§21/§22 and the former P3 planned
an `offset` token on the typed query DSL. The maintainer's locked surface
(`D-PAGINATION`, `DECISIONS.md` §D-PAGINATION) rules the opposite: `offset` lives
**only on the windowed method, never the typed DSL**. A DSL token changes the
frozen grammar (rule 6), so it is deferred to a NEW maintainer decision; the
executable sequence is P4→P5→P6. The DSL-offset mentions below are historical
design notes, not scope.
**Snapshot:** original recon on tip `63c7b15d8`; re-measure every `file:line`
before a slice lands. Active branch = `lab` (`D-BRANCH-PIPELINE`).

---

## 1. Context

Backend applications — the class of apps a team would otherwise build with
Spring Boot — repeat the same ceremony on every list endpoint: parse `page` /
`limit` / `offset`, validate, clamp, compute the offset, build `LIMIT ? OFFSET ?`,
load the rows, then compute `hasNext` / `total`. Kof's philosophy ("intention,
not mechanism") says this repetition is accidental complexity that belongs to
the platform.

The goal is a **language/stdlib abstraction for the intention "access a window
of data"**, decoupled from both HTTP and SQL:

```
Kof expression (filter -> order -> pagination)  ->  materialization
```

- over a DB connection, it should push `LIMIT`/`OFFSET` down to SQL;
- over an in-memory `List`, it should be a view/derive of the already-materialized data;
- over HTTP, it should be reachable without the core knowing about HTTP.

This plan investigates the **real** state of the repo first, then proposes an
incremental, architecture-compatible design. It does **not** implement anything.

---

## 2. Problem

1. **No first-class windowing intention.** The only pagination primitive in the
   whole codebase is `orm.page<T>(db, limit, offset)`
   (`JvmOrmRuntime.java:450-471`, `KofJsOrmBridge.java:296-302`,
   `RuntimeOrm8.java`, `NativeRiscvAsmRtB60.java`). It returns a bare `List<T>`
   with **no metadata** (`hasNext`/`total`), so every caller recomputes it.
2. **The typed query DSL has no offset.** `Entity.query(db) { where …; orderBy f;
   limit N }` — the parser accepts only `where`/`orderBy`/`limit`
   (`ExpressionParser.java:505-537`), the AST `QueryDslExpr` has `limit` and **no
   `offset`** (`QueryDslExpr.java:4-9`), and the SQL builder emits ` LIMIT n`
   only (`CompilerOrmSupport.java:170-178`). Pages beyond the first are impossible
   in the DSL.
3. **`orm.all` / `orm.where` / `orm.where_op` are unbounded.** They emit
   `SELECT * FROM "t"` with no window, so a large table is always fully loaded
   (`JvmOrmRuntime.java:273-315`).
4. **No HTTP helper.** `query("page")` returns `String?`
   (`KofWeb.java:242-244`, `JvmRuntimeWebDispatch.java:492-495`); every handler
   writes its own `if (p != null) { var n = p.toInt() }` with no default, no
   clamp, no max. And **JVM does not URL-decode** query values while JS does
   (`JvmWebCoreRuntime.java:139-145` vs `JsRuntimeUiWeb.java:238-246`) — a parity
   bug any helper must first reconcile.
5. **Existing surface inconsistency.** The LSP catalog documents
   `orm.page(String entity, Object offset, Object limit)` (`StdCatalog.java:235`)
   but the runtime order is `(limit, offset)` (`KofOrm.java:178-181`). A latent
   trap that must be fixed before building on top.

---

## 3. Current state found in Kof (measured)

### 3.1 Collections and pipelines

- `listOf`/`mapOf`/`setOf`; List methods dispatched in `CollectionCallLowerer.java:82-278`.
- **The only window op today is `List.subList(begin, end)`** — half-open,
  materializes a copy (`CollectionCallLowerer.java:96`; JVM `JvmOpCollections.java:184-197`).
- Higher-orders are **only** `map`/`filter`/`reduce` on `List<T>`, all **eager and
  allocating** (`CollectionCallLowerer.java:16-56`; JVM `JvmStringMiscRuntime.java:71-114`).
  No lazy sequence, no fusion.
- `sort()` is natural-order only; there is **no Comparator in Kof** by design
  (`CollectionMethodGates.java:30-31`; `training/idioms/collections.md:62-93`).
- Each collection op is mirrored in **three layers** (typer
  `MemberCallTyper`/`CollectionMethodTyper`, `MethodCallTyper`, lowerer
  `CollectionCallLowerer`) plus per-target runtime shims — a fact a new op must respect.

### 3.2 DB / ORM

- `kof.db` (`KofDb.java`) passes raw user SQL to `prepareStatement`; generated
  SQL lives only in the ORM and the query DSL.
- `orm.page` is the **only** LIMIT/OFFSET pushdown and is already real on all
  legs (JVM/JS/x86 SQLite+MySQL/riscv64+aarch64).
- `MAX_BIND = 4` (`KofDb.java:38`) caps `where + limit` binds
  (`CompilerOrmSupport.java:181-189`).
- The DB surface is declared **frozen** (`docs/stdlib/db-parity-plan.md:186-192`;
  `DECISIONS.md` §D-DB-GAPS addendum `:2356-2385`) → adding an offset or changing
  `orm.page`'s return type is **rule 6**.

### 3.3 HTTP / JSON

- No `WebRequest`/`WebResponse` user type: handlers read context functions
  (`param/query/header/body/method/path`) backed by `ThreadLocal`
  (`JvmRuntimeWebDispatch.java:174`). Response = return value + `status()`/`headerSet()`.
- **No pagination helper, no response envelope, no standard header**
  anywhere in code or docs (searched `meta`/`links`/`hasMore`/`totalPages`).
- `kof.json` auto-serializes arbitrary records/lists on JVM/JS
  (`JvmRuntimeJson.java:162-181`); on Native objects are composed at compile time
  and `List<Record>`/`Map<String,T>` are `JSN004` gaps
  (`ExpressionJsonCallLowerer.java:140-208`).

### 3.4 Constraints (see §6)

- **R1** stdlib boundary is machine-gated (`scripts/check_stdlib_boundary.sh` +
  `scripts/stdlib_boundary.txt`); the namespace `data` is **HARD-DENY**
  (`stdlib_boundary.txt:11`). Collections are **language-level**, not a namespace.
- **G-01** freezes collections semantics; new surface touching them = rule 6.
- `PLAN-MULTIPARADIGMA.md` already plans `take/drop/distinct/sorted` (Phase 1,
  allowed) and `Sequence<T>`/`queryOf`/SQL backend (Phases 3–7, **gated by
  SYSTEMS/Tier 1 closing**).
- **R12**: no new front opens before SYSTEMS closes, except `D-UNIVERSAL` and
  `D-BAREMETAL-BOOT`. Pagination must ride an open front or get a decision.
- Any new contracted public surface → **MINOR + Decision ID** (`D-VERSIONING-RELEASE`).

---

## 4. Objective

Make **"access window `(limit, offset)` of a data source"** a first-class,
target-neutral intention that:

1. is the **same concept** for an in-memory `List` and a DB query;
2. **pushes down** `LIMIT`/`OFFSET` to SQL when the source is a DB (no full load);
3. never materializes before the window when the source supports it;
4. returns window metadata (`hasNext`, `hasPrevious`, optional `total`) without an
   implicit `COUNT(*)`;
5. is reachable over HTTP **without** the core depending on HTTP;
6. does not block a future **cursor/keyset** evolution.

---

## 5. Non-objectives

- **Not a framework of pagination** (no `Pageable`/`PageRequest`/`Sort` beans in
  the Spring sense, no repository abstractions).
- **Not a new heavy namespace.** `kof.data` is forbidden (R1); the DB front rides
  the existing `orm` ledger line, the in-memory front is language-level.
- **Not a generic lazy `Sequence<T>` engine** now — that is `PLAN-MULTIPARADIGMA`
  Phases 3–7, gated.
- **Not cursor/keyset** in v1 (contract prepared, implementation later).
- **Not HTTP-coupled**: the HTTP helper is sugar over the core concept, never the concept.
- **Not a `COUNT(*)`-always API**: `total` stays opt-in.

---

## 6. Relevant existing architectural decisions

| Decision / rule | Constraint on this plan |
|---|---|
| `D-KOF-FIRST` (rule 10) | external APIs (JDBC, Spring, SQL) are not oracles; the Kof contract is designed internally, then expressed. |
| Simplicity Law (rule 11) | the surface must be shorter/clearer than the boilerplate it removes; no ceremony. |
| R1 + `stdlib_boundary.txt` | new namespace ⇒ ledger line + layer, or the build fails; `data` hard-denied. Prefer **no new namespace**. |
| G-01 frozen collections | adding methods to `List` / changing its contract = rule 6. |
| `D-DB-GAPS` addendum | DB/ORM is frozen; new face inherits **4-target parity** with honest R6 diagnostics. |
| `D-VERSIONING-RELEASE` | new public surface = MINOR + Decision ID. |
| R12 / `roadmap.md` §23 | front opens only with a decision or by riding `db-parity-plan` / `PLAN-MULTIPARADIGMA` Phase 1. |
| `roadmap.md` §24 / `D-RELEASE-1.0` | a new `future/` item is an open item for the 1.0 EXIT GATE (accepted, since the maintainer requested it). |
| `PLAN-MULTIPARADIGMA.md` | in-memory windowing aligns with Phase 1; lazy/SQL backend order is normative (Phases 3–7) and must not be jumped. |

---

## 7. Design proposed

### 7.1 The central concept: a window, not a page number

```
Window<T> = { items: List<T>, offset: Int, limit: Int,
              hasPrevious: Bool, hasNext: Bool, total: Long? }
```

- **`items`** — the materialized window (possibly empty).
- **`offset`/`limit`** — the normalized request that produced it.
- **`hasPrevious`** — `offset > 0`.
- **`hasNext`** — exact when `total != null` (`offset + items.size < total`);
  otherwise **optimistic** (`items.size == limit`), documented explicitly.
- **`total`** — `null` unless the caller asked for it. **Never** triggers an
  implicit `COUNT(*)`.

Why `Window`, not `Page`: `Page` implies a page number + total (Spring-shaped);
`Window` expresses the Kof intention (an offset/limit view) and stays neutral for
a future cursor. (`Page`/`Slice` are rejected as foreign naming; rule 8/9.)

### 7.2 Where each piece lives

| Layer | Piece | Rationale |
|---|---|---|
| **language-level (collections)** | `Window<T>` record + `List` window ops (`slice`/`take`/`drop`, `window(limit, offset)`) | collections are language-level; fits `PLAN-MULTIPARADIGMA` Phase 1 |
| **platform `kof.orm`** | windowed query faces returning `Window<T>`; `offset` in the SQL DSL; `total` only via explicit `count` | rides the frameworkled `orm` ledger line; DB is platform |
| **platform `kof.web`** | `pageRequest(...)` helper reading `?page/limit/offset`, validated | sugar; keeps the core HTTP-free |
| **future** | `CursorRequest` feeding the same `Window<T>` | keyset, later decision |

No new namespace is introduced → R1 gate untouched.

### 7.3 Approaches compared

| | **A. `Window<T>` + composition (recommended)** | **B. extend signatures, no new type** | **C. lazy `Sequence<T>`** |
|---|---|---|---|
| In-memory | `l.window(limit, offset) -> Window<T>` | `l.slice(offset, limit) -> List<T>` | `seq.skip(offset).take(limit)` |
| DB | `orm.window<T>(db, limit, offset)` + DSL `offset` | add `offset` to DSL; keep `orm.page` returning `List` | lazy query spine |
| Metadata | in the `Window<T>` | caller recomputes | in the spine |
| New surface | one record (+ ops) → rule 6 | one DSL keyword + maybe no ops → smaller | large (IR middle-end) |
| Philosophy | intention explicit, reuses `subList`/`orm.page` | least surface, most boilerplate | most power, most complexity |
| Gate | OK after `D-PAGINATION` | OK after `D-PAGINATION` | blocked until SYSTEMS (Phases 3–7) |

**Recommendation: A**, with **B** as the strictly-minimal fallback if the
maintainer rejects a new type. **C** is the future successor, not v1.

### 7.4 Proposed surface (proposal — names are open questions, §19)

```kof
// in-memory (language-level)
val w = users.window(20, 40)          // Window<User>; hasNext/hasPrevious; total = null
val w2 = users.window(20, 40, true)   // total computed locally (size), opt-in

// DB (platform kof.orm) — pushes LIMIT ? OFFSET ? down
val w3 = orm.window<User>(db, 20, 40)             // no COUNT(*)
val w4 = orm.window<User>(db, 20, 40, true)       // opt-in COUNT(*) for total
// and the typed DSL gains offset:
val q = User.query(db) {
    where(active = true)
    orderBy(name) asc
    limit(20) offset(40)
}

// HTTP (platform kof.web) — reads ?page=&limit= / ?offset=&limit=, validated
val w5 = pageRequest(20)              // default limit; clamp; rejects bad input
```

The core (`Window<T>`, `List.window`) knows nothing about SQL or HTTP.

---

## 8. Contracts / semantics

| Question | Ruling (proposed) |
|---|---|
| `limit` type | `Int` (SQL OFFSET/LIMIT are 32-bit in practice); overflow documented |
| `offset` type | `Int`, `>= 0` |
| negative `limit`/`offset` | **reject** with a named error (`throw "PAGINATION: limit/offset must be >= 0"`), never silently clamp |
| `limit == 0` | valid → empty window; `hasNext` tells whether more exists; enables metadata-only reads |
| `offset > size` | empty window, `hasPrevious = true`, `hasNext = false`; **no error** |
| max limit | **not** imposed by the core/DB primitive (a hidden cap is a silent behavior); the **HTTP helper** enforces a configurable max (default constant, e.g. 1000) |
| deterministic order | pagination without `orderBy` is nondeterministic; the DB face documents it and SHOULD emit a diagnostic when windowing an unordered `all`/`where` (`ORM00x`) |
| total | only when explicitly requested (`orm.window(..., true)` / local size); never implicit `COUNT(*)` |
| `hasNext` accuracy | exact with `total`; optimistic without it (documented, testable) |

---

## 9. Integration with collections

- Reuse `List.subList` as the base (`JvmOpCollections.java:184-197`); `window`
  = normalize + `subList` + wrap in `Window<T>`.
- New List ops (`slice`/`take`/`drop`/`window`) enter the **three mirrored
  typer/lowering layers** (`MemberCallTyper`, `CollectionMethodTyper`,
  `CollectionCallLowerer`) and each target's shim (JVM/Native x86+riscv/JS/Script).
- This is exactly `PLAN-MULTIPARADIGMA` **Phase 1** territory (eager List ops),
  which is allowed before SYSTEMS; the lazy path stays Phase 3+.

## 10. Integration with DB

- Add `offset` to the typed DSL: parser (`ExpressionParser.java:505-537`), AST
  (`QueryDslExpr`), SQL builder (`CompilerOrmSupport.java:170-178`).
- Add `orm.window<T>(db, limit, offset[, total])` returning `Window<T>`, built on
  the **existing** `kof_orm_page` shape (`SELECT * … LIMIT ? OFFSET ?`) plus
  `kof_orm_count` for the opt-in total. All four legs already have the SQL shape.
- Keep `orm.page` behavior (frozen) or supersede it via the decision; fix the
  `StdCatalog.java:235` inversion in the same unit.

## 11. Pushdown / optimization

- DB: the window is emitted as `LIMIT ? OFFSET ?` **before** materialization; the
  ORM already does this for `page` and the same generator is extended to the DSL
  and `window`.
- In-memory: data is already materialized, so pagination is a **view**; no
  pre-materialization claim is made (honest).
- Future lazy path (`PLAN-MULTIPARADIGMA` Phase 3/7) is where
  `filter → order → pagination → materialization` becomes truly deferred and
  SQL-generative; this plan does not jump that order.
- `MAX_BIND = 4` (`KofDb.java:38`): a `where` + `limit` + `offset` combination
  must fit the 4-bind cap, or the cap is revisited under the decision.

## 12. HTTP integration (optional, decoupled)

- A `kof.web` helper `pageRequest(defaultLimit[, maxLimit])` reads the query
  string, validates/clamps, and returns the **core** request value — no HTTP type
  leaks into the core.
- The handler returns `json.encode(w)`; the envelope is the plain `Window<T>`
  shape, so JVM/JS encode it for free and Native needs the field set to pass the
  JSON schema gates (`JSN002`/`JSN004` — `Window<Record>` may hit `JSN004`;
  documented and diagnosed, never silent).
- **Prerequisite: DONE 29/09** — the JVM/JS query-decoding divergence is fixed
  (`JvmWebCoreRuntime.WebRequest.query` now mirrors `decodeURIComponent`: `%XX`
  UTF-8 strict, `+` literal, malformed → error; proof
  `KofWebE2ETest#queryValueIsPercentDecoded` + `KofWebJsE2ETest`).
- Standard pagination headers (`X-Total-Count`, `Link`) are **not** mandated; if
  wanted, they are a separate `kof.web` decision.

## 13. Target compatibility

| Target | In-memory `Window<T>` | `orm.window` (SQL pushdown) | HTTP helper |
|---|---|---|---|
| JVM / Android | real | real (JDBC) | real |
| Native x86-64 | real | real (sqlite; mysql x86) | gap (`WEB001` residual) |
| Native riscv64/aarch64 | real | real (sqlite via `RtB60`) | gap |
| JS | real | real (bridge) | real |

`Window<T>` is a normal record → no new IR. `orm.window` inherits the
`D-DB-GAPS` total-parity obligation with honest R6 diagnostics while a slice lands.

## 14. Future evolution to cursor/keyset

- The carrier is generic (`Window<T>` with `items`/metadata), so keyset can reuse it.
- Add a **separate** request type later (`CursorRequest(limit, afterKey)`) and an
  `orm.after<T>(db, limit, key)` face; the result is still `Window<T>`, with the
  last item's key available to the caller.
- The initial contract must therefore **not encode "offset" into the type name**
  (reason to prefer `Window` over `OffsetPage`), and `hasNext` must be derivable
  from `items` + `limit`, which it is.
- Keyset is a **rule-6 decision of its own**; this plan only guarantees it is not blocked.

## 15. Impact on compiler / IR / backend

- **Collections ops** (`slice`/`take`/`drop`/`window`): typer + lowering + 4 target
  shims (no IR op change; they lower to existing `kof_list_*` / `subList`).
- **`Window<T>` record**: declarative (`record`), no new IR, but JSON on Native
  needs the schema table (`NativeJsonSchema.java`) to cover the fields.
- **DB DSL `offset`**: parser + AST + SQL lowerer (compiler).
- **`orm.window`**: `KofOrm` table + JVM/JS/x86/riscv runtime faces (compiler +
  runtime slices), mirroring `kof_orm_page`.
- **HTTP helper**: `KofWeb` context/whitelist + per-target gates + runtimes.
- So this is **mostly compiler/backend work, not pure stdlib**; only the value
  semantics of `Window<T>` are "stdlib-like".

## 16. Tests required

- Unit/E2E per op: `slice`/`take`/`drop` edge cases (0, exactly-size, offset==size,
  offset>size, negative) on all 4 targets.
- `Window<T>` metadata: `hasNext`/`hasPrevious` in both regimes (with/without total).
- DB: `orm.window` roundtrip on JVM (H2/SQLite) + Native (sqlite under qemu) +
  JS; verify `LIMIT ? OFFSET ?` is actually in the executed SQL (spy/assert).
- DSL: `limit(N) offset(M)` compiles and returns the right rows; `ORM00x` on bad input.
- HTTP: `pageRequest` parses `?page=3&limit=20`, clamps, rejects bad input;
  JVM URL-decode parity test.
- Cross-target parity: same program → same observable output on JVM/Native/JS, or
  a declared gap.
- No implicit `COUNT(*)`: assert no count query is issued unless requested.

## 17. Compatibility / regression

- **Additive** where possible; if `orm.page`'s return type changes, it is a
  deliberate bump + migration per the freeze.
- The fix of `StdCatalog.java:235` (offset/limit inversion) is a backward-compatible
  doc correction.
- Zero regression on the existing web/DB/collections suites is the merge gate.

## 18. Risks

1. **Scope creep into a framework** — mitigated by the `Window` contract and the
   explicit non-goals.
2. **Nondeterministic pagination** without ordering — needs the diagnostic.
3. **Bind-cap pressure** (`MAX_BIND=4`) — may force a decision.
4. **Native JSON gaps** for `Window<Record>` (`JSN004`) — must be diagnosed.
5. **HTTP/native parity** (JVM no URL-decode) — must be fixed first.
6. **R12/1.0 gate** — a new front/open item; needs the maintainer's decision.
7. **`orm.page` freeze** — changing it is rule 6; approach B avoids the break.

## 19. Open questions (for the maintainer)

1. **Name**: `Window<T>` (recommended) vs `Slice<T>` vs `Page<T>`?
2. New type at all, or Approach B (extend signatures, no type)?
3. `total`: separate `orm.window(..., true)` flag, or always require an explicit
   `orm.count` call?
4. Max limit default + is it global or per-endpoint?
5. Is the in-memory part allowed now under `PLAN-MULTIPARADIGMA` Phase 1, or does
   the whole front wait for SYSTEMS?
6. Does `orm.page` get superseded (bump) or kept alongside `orm.window`?
7. Is `offset` added to the typed DSL, or only the windowed method?
8. HTTP helper name/shape (`pageRequest(default[, max])`) and whether it belongs
   in `kof.web` or a `kof.pagination` context function. **RESOLVED 29/09** —
   `D-PAGINATION-P5-SHAPE`: `kof.web.pageRequest(default[, max]): PageRequest`
   (core record, ambient `query`), separate `kof.web` host.

## 20. Implementation in phases/slices

Each slice is independently provable; no slice ships without a test and docs.

- **P0 — Recon + decision (this document).** Fix `StdCatalog.java:235` (DONE 28/09:
  catalog corrected to `(entity, limit, offset)` + regression lock
  `StdCatalogSignaturesTest#ormPageCatalogDocumentsLimitBeforeOffset`); lock
  `D-PAGINATION`.
- **P1 — In-memory windowing (language-level).** `slice`/`take`/`drop` on `List`
  (three typer/lowering layers + 4 shims). DONE 28/09: `kof_list_take/drop/slice`
  on JVM/JS/interpreter + native x86-64/riscv64/aarch64 (clamping honesto,
  negativo = erro nomeado `PAGINATION`); proof `PaginationSliceE2ETest` 7/7
  (cross riscv/aarch ran) + `KofScriptStdlibParityTest#paginationSliceParity`.
  *(_Rides `PLAN-MULTIPARADIGMA` Phase 1; no new type yet._)
- **P2 — `Window<T>` value + `window(limit, offset[, total])`.** DONE 28/09:
  `record Window<T>` + `window<T>(List<T>, Int, Int): Window<T>` and
  `window<T>(List<T>, Int, Int, Bool): Window<T>` written in Kof
  (`dev/kof/pagination.kf`, library-first per `D-KOF-FIRST-IMPL`), injected FLAT
  on explicit `import kof.pagination` (`CompilerPagination.java`, same mechanism
  as `kof.supervisor`/`kof.workflow`/`kof.interop` — user-defined `Window`/`window`
  collides and skips injection). Nested type-arg inference landed alongside
  (recursive `substituteTypeVariableIn` in `CompilerTypes` +
  argument-driven binding in `MethodCallTyper#tloFns` — fixes `Box<Int>.items()`
  and `window(l,2,1)` emitting `List<T>`/`Window<T>` on Native, which boxed a raw
  `int` in `println` and SIGSEGV'd via `kof_box_to_string`). Proof:
  `PaginationWindowE2ETest` 7/7 (JVM/JS/Script/native x86-64 + cross
  riscv64/aarch64, 0 skips) + neighbors 150/150. P4 next (not started).
- **P3 — DB offset in the typed DSL: OUT OF SCOPE.** The locked surface
  (`D-PAGINATION`) keeps `offset` off the typed DSL (a grammar token is a
  frozen-surface change, rule 6). Deferred to a NEW maintainer decision; do not
  implement from this plan. The windowed DB offset is P4 below.
- **P4 — `orm.window<T>(db, limit, offset[, true])`** on all four legs, reusing
  `kof_orm_page`/`kof_orm_count`. **LANDED (29/09)** per
  `D-PAGINATION-P4-LOWERING`: the ORM lowerer desugars `orm.window` into the
  injected Kof `windowPage(...)` helper over `orm.page`/`orm.count` (library-
  first; no per-target runtime for the `Window` record; `windowPage` does not
  re-slice — the SQL already paginated; `windowBounds` validates before the SQL;
  the 4-argument form's `orm.count` is lazy/opt-in). Proof: `PaginationOrmWindowE2ETest`
  5/5 — JVM(H2) + Native x86(sqlite) + JS parity + negative named error + import
  required.
- **P5 — HTTP helper `pageRequest(...)`** + query-decode parity fix. **LANDED
  (29/09)** per `D-PAGINATION-P5-SHAPE`: the pure-Kof virtual host `kof.web`
  (injected on `import kof.web`) provides `pageRequest(defaultLimit[, maxLimit]):
  PageRequest`, returning the core `PageRequest(Int page, Int limit, Int offset)`
  and reading the ambient `query("page"/"limit"/"offset")`; 1-based page, clamp
  to `maxLimit`, explicit `?offset=` wins, named `PAGINATION:` error on bad input
  (handler maps to 400 via `status(400, e)`). Proof: `PaginationPageRequestE2ETest`
  8/8 (page/limit→offset, defaults, clamp, explicit offset, page=0, non-integer,
  negative offset, import required) + `KofWebE2ETest` 28/28 (no regression).
- **P6 — Docs/corpus** (`training/idioms/database.md`, `collections.md`,
  `docs/stdlib/stdlib-database.md`, `DATABASE_VISION.md`), synchronize the
  `orm.page` signature everywhere. **LANDED (29/09)** — `training/idioms/
  collections.md` + `database.md` (EN+PT) carry `Window<T>`/`window`/`orm.window`;
  `docs/stdlib/stdlib-database.md` + `DATABASE_VISION.md` (EN+PT) document
  `orm.window<T>(db, limit, offset[, total])`; the `orm.page` signature was
  already `(db, limit, offset)` everywhere.
- **Future (separate decision) — cursor/keyset** and the lazy
  `PLAN-MULTIPARADIGMA` spine.

> **Decision resolved (29/09 — `D-PAGINATION-P4-LOWERING`):** the maintainer
> chose **(b)** — the ORM lowerer desugars `orm.window` into the injected Kof
> `windowPage(...)` helper over `orm.page`/`orm.count`, so no per-target runtime
> constructs the `Window<T>` record.
>
> **Decision resolved (29/09 — `D-PAGINATION-P5-SHAPE`):** the maintainer chose
> the explicit-core-return shape — the `kof.web`-owned `pageRequest(default[, max]):
> PageRequest` reads the ambient `query(...)` and returns the core `PageRequest`
> record; §19 Q8 is CLOSED, so P5 has no open design point.

## 21. Acceptance criteria per phase

- **P1:** `slice/take/drop` correct for `0`, exactly-size, `offset==size`,
  `offset>size`, negative→named error, on JVM/Native/JS/Script; suite green.
- **P2:** `Window<T>` metadata exact with `total`, optimistic without; JSON
  encode/decode on JVM/JS and Native (or declared `JSN004`); no `COUNT(*)`.
- **P3:** DSL `offset` produces correct rows; SQL contains `LIMIT ? OFFSET ?`;
  unordered window emits `ORM00x`, never silence.
- **P4:** `orm.window` parity JVM/Native/JS (same rows, same metadata); opt-in
  total issues exactly one `COUNT(*)`; no full-table load in the windowed path.
- **P5:** `?page=3&limit=20` → offset 40 without handler code; bad values → 400
  with a named message; JVM/JS query decoding identical.
- **P6:** docs and `StdCatalog` consistent; `check_stdlib_boundary.sh` green (no
  new namespace); zero regression.

## 22. Final answers (as requested)

1. **Compatible approach:** **A — a `Window<T>` intention value composed from the
   existing `subList` (in-memory) and `kof_orm_page` `LIMIT/OFFSET` (DB)**, with
   the HTTP helper isolated in `kof.web`. Fallback **B** (no new type) if the
   maintainer rejects a new surface; **C** (lazy sequence) is the gated future.
2. **Likely files/modules:**
   - collections: `MemberCallTyper.java`, `CollectionMethodTyper.java`,
     `MethodCallTyper.java`, `CollectionCallLowerer.java`, `jvm/JvmOpCollections.java`,
     `runtime/RuntimeList*.java`, `js/JsRuntimeCollections.java`,
     `KofInterpreterCollections.java`, riscv `NativeRiscvAsmMapset1/RtB*`.
   - DB: `parser/ExpressionParser.java`, `QueryDslExpr.java`,
     `CompilerOrmSupport.java`, `KofOrm.java`, `jvm/JvmOrmRuntime.java`,
     `KofJsOrmBridge.java`, `runtime/RuntimeOrm8.java`, `nat/NativeRiscvAsmRtB60.java`,
     `StdCatalog.java`.
   - HTTP: `KofWeb.java`, `jvm/JvmRuntimeWebDispatch.java`, `jvm/JvmWebCoreRuntime.java`,
     `js/JsRuntimeUiWeb.java`.
   - JSON (Native): `nat/NativeJsonSchema.java`, `ExpressionJsonCallLowerer.java`.
3. **New contracts:** `Window<T>` (value + metadata semantics),
   `orm.window<T>(db, limit, offset[, total])`, `kof.web.pageRequest(...)`. (The
   `offset` DSL token shown in the design sections is NOT in the locked surface —
   see the correction in the header.) All require `D-PAGINATION`.
4. **Implement first:** P1 (in-memory `slice/take/drop`, no new type) — smallest,
   rides `PLAN-MULTIPARADIGMA` Phase 1, immediately useful.
5. **Explicitly future:** cursor/keyset pagination; the lazy `Sequence<T>` spine
   and SQL-generative `queryOf` (`PLAN-MULTIPARADIGMA` Phases 3–7, gated by SYSTEMS).
