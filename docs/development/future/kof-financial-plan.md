last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kof-financial-money-movement

> **EN canonical** · PT: [`kof-financial-plan.pt_BR.md`](kof-financial-plan.pt_BR.md)

**Rule 6 gate:** `kof.financial` introduces NEW stdlib namespaces. The whole
plan is designed to be **library-first** (`D-KOF-FIRST`): the v1 must be
expressible as pure-Kof libraries over primitives that already exist. If a
new core primitive is later judged necessary (a decimal scalar, an exact
rounding mode), that is a **frozen-semantics** change and a maintainer
decision. This document is **plan only, zero code**. Promotion requires a
maintainer decision (`D-FINANCIAL-GO`) plus the future-promotion flow
(`docs/development/future/README.md` §"When to move", `AGENTS.md` §"Future
promotion"). Promotion is currently FROZEN by `D-FUTURE-FREEZE`; this plan is
authored under that freeze, not promoted.

**Source:** maintainer brief (02/10/2026), summarized scope: money, currency,
FX, fees, transaction/payment/transfer/refund/reversal/settlement,
account/balance/ledger (double-entry), events/webhooks/idempotency/retries/
reconciliation, providers/adapters, compliance boundary, jurisdiction
profiles.

**Related, already-decided work this plan MUST reuse (not duplicate):**
- `docs/development/future/kofbol-plan.md` — legacy-banking interop; already
  states the same money rule (`Never convert money to float/double`) and the
  same measured absence of a decimal type. `kof.financial` is the
  **greenfield** counterpart: KofBOL talks to the old system, `kof.financial`
  models the new one.
- `KofDb.java`/`KofOrm.java` `transaction { … }` — the existing commit/rollback
  seam (`stdlib-database.md:36-38,53`); the ledger posting unit rides it.
- `KofJson.java` / `json.encode|decode<T>` — persistence and provider payloads.
- `KofHttp.java` + `KofWeb.java` — provider clients and webhook receivers.
- `KofSecurity.java` — HMAC signatures for webhooks, secure random for ids.
- `KofUuid.java` — `uuid.v4()`/`uuid.v7()` for idempotency keys.
- `KofTime.java` — `time.now()` epoch millis; ISO date strings.
- `KofObservability.java` — correlation/request ids (webhook retries, recon).
- `docs/development/future/entity-history-plan.md` — temporal audit, the same
  append-only instinct the ledger needs; `audited entity` may host ledger rows.

---

# `kof.financial` — plan (money, accounts, and movement)

## 0. Purpose

> **Kof should be able to model money and move it between accounts without
> ever losing a cent, on any target, and without inventing a language.**

`kof.financial` is a **pure-Kof library family** for the *core* money
concerns: representing amounts exactly, holding balances, posting
double-entry movements, driving a transaction lifecycle (authorize → capture →
settle → refund/reverse), charging fees, emitting events, and reconciling
against a provider. It is **not** a payment gateway, **not** a bank
integration, **not** a tax engine, and **not** a compliance system. Providers
and jurisdictions are **adapters and data**, never hard-coded policy.

Final acceptance question (§24):

> Can a Kof application represent money exactly, post a balanced ledger,
> drive a payment through its full lifecycle with idempotent retries, and
> reconcile the result against an external provider — on JVM, Native and JS —
> using only `kof.financial` and primitives that already exist?

## 1. Measured baseline (honest novelty)

Investigation (file-anchored, tip `a3912a971`) established exactly what exists.
The **scaffolding** is real; the **money domain** is entirely new.

| Concern | Real state | Evidence |
|---|---|---|
| `kof.financial` / any financial namespace | **does not exist** | repo-wide search → 0 hits |
| Decimal / `BigDecimal` / fixed-point / money type | **absent** | `Type.java:7-15`; `kofbol-plan.md:69`; `types.md:145-152` |
| Exact decimal arithmetic | **absent** | `KofMath.java` has only `roundTo(Double,Int)`; no scale type |
| `record` (immutable, structural equality) | **exists** | `classes.md:99-129`; `RecordEqualityLowerer.java` |
| `class` + `interface` + `enum` (constants) | **exists** | `classes.md:9-96,133-220` |
| Generics, `T?` narrowing, `switch`, `for-in` | **exists** | `type-system.md`; `statements.md:110-151` |
| String exceptions + named compile-time gap codes | **exists** | `statements.md:160-189`; `DomainGapCodesTest.java` |
| `db.connect` + `transaction { }` (commit/rollback) | **exists** | `KofDb.java:80,110-112`; `jvm/JvmConfigRuntime.java:535-569` |
| `entity` + ORM CRUD (`save/find/all/where/page`) | **exists** | `KofOrm.java:147`; `DATABASE_VISION.md` |
| `json.encode` / `json.decode<T>` into records | **exists** | `StdCatalog.java:401-403`; `JsonDispatch.java:78-86` |
| `http.get/post/...`, `web.app()`, webhooks receive | **exists** | `KofHttp.java:81-110`; `stdlib-web.md` |
| `crypto.hmacSha256` / `sha256` / secure random | **exists** | `KofSecurity.java:75`; `security.md` |
| `uuid.v4()` / `uuid.v7()` | **exists** | `KofUuid.java:35-49` |
| `time.now()` (epoch ms) + ISO date helpers | **exists** | `KofTime.java:128-233` |
| `String.format("%.2f", …)` (Locale.ROOT) | **exists** (JVM/JS; Native unverified) | `StringFormatCallLowerer.java:24-63` |
| `strings.padLeft/padRight` | **exists** | `KofStrings.java:82-87` |
| `spawn`/`await`/`channel`; sequentially-consistent | **exists** | `concurrency.md`; `concurrency-memory-model.md` |
| Operator overloading | **absent** | `classes.md:268` |
| Date/time/instant/duration type, timezone db | **absent** | ISO strings + epoch ms only |
| Transaction isolation / savepoints / explicit rollback | **absent** | only `setAutoCommit(false)` + exception rollback |
| Connection pooling | **absent (planned)** | `DATABASE_VISION.md:322-324` |
| Idempotency / retry / outbox / webhook ledger | **absent** | 0 hits |
| Double-entry ledger / journal / posting | **absent** | 0 hits |
| FX rate / fee schedule / reconciliation | **absent** | 0 hits |

**Consequence:** money representation, the ledger, the lifecycle, idempotency,
retries and reconciliation are genuinely new. The persistence, transport,
crypto, id and time seams already exist and must be reused, not rebuilt.

## 2. Non-goals (explicit)

- **No new money type in the language.** No `decimal`/`money` keyword, no
  operator overloading. v1 models money as a `record` over `Long` minor units
  plus an explicit currency code. A core decimal scalar, if ever needed, is a
  separate rule-6 decision (`D-FINANCIAL-DECIMAL`).
- **No floats for money, ever.** `Double`/`Float` are forbidden in any amount,
  rate, fee, or balance field; a diagnostic must enforce this at the library
  boundary.
- **No payment gateway.** No provider-specific API is hard-coded; providers
  are adapters behind an interface.
- **No tax engine.** Tax is a *fee* computed by application code; the library
  only moves amounts.
- **No compliance engine.** KYC/AML/sanctions are *structured checks with a
  decision boundary*; the library records the outcome, it does not decide law.
- **No global singleton ledger.** Accounts and ledgers are values; state lives
  in the store the application chooses.
- **No timezone/calendar engine.** v1 uses epoch millis + ISO strings; a
  real date type is a separate concern.
- **No hidden network calls.** No provider call inside a pure computation; I/O
  is always an explicit adapter call.
- **No silent rounding.** Every rounding point takes an explicit rounding mode.

## 3. Layered architecture

```text
Kof application
      ↓
kof.financial             (idiomatic Kof API: Money, Account, Journal, Tx)
      ↓
domain model              (records: Money, Currency, Rate, Fee, Entry, Event)
      ↓
engine                    (posting rules, lifecycle, rounding, idempotency)
      ↓
ports                     (LedgerStore, Provider, Clock, IdGenerator — interfaces)
      ↓
adapters                  (kof.db / kof.orm / http / file — chosen by the app)
      ↓
external world            (database, provider, webhook consumer)
```

The **domain must not depend on the adapter**. A `Journal` posts through a
`LedgerStore` interface; `kof.db` is one implementation. This is what keeps
the library testable in-memory and honest across targets.

## 4. Money representation (the core decision)

This is the decision everything else depends on, so it is stated first and
recorded as `D-FINANCIAL-MONEY`.

**v1 model — minor units + ISO-4217 code:**

```kof
// PROPOSED (pure Kof; only `record` + `Long` + `String` exist today)
record Money(Long minor, String currency)
```

- `minor` is the amount in the currency's smallest unit (cents, pence,
  satoshi-like subunits) — always an exact `Long`, never a `Double`.
- `currency` is the ISO-4217 alpha code (`"BRL"`, `"USD"`, `"EUR"`).
- `Money` is **immutable** and compares by **content** (`==` on a record is
  structural — `type-system.md:333-345`), so `Money(100,"BRL") ==
  Money(100,"BRL")` is `true`.
- A `Currency` descriptor carries the scale and metadata:

```kof
// PROPOSED
record Currency(String code, Int minorUnits, String symbol)
```

The default scale table (2 for most, 0 for JPY/KRW, 3 for BHD/KWD/JOD/TND,
etc.) is **data**, not code, and lives in a Kof file. `D-FINANCIAL-MONEY`
records: minor units + code, `Long` only, no core change.

**Why not `Double`?** `0.1 + 0.2 != 0.3` in IEEE-754; a cent lost per
transaction is a bug at scale. `types.md:36-38` documents silent wraparound
even for `Long`; overflow must therefore be checked explicitly at each
arithmetic boundary (§4.2).

**Why not a core decimal?** It would be frozen-semantics surface, would need
backend support on five targets, and `kofbol-plan.md:364` already frames the
same trade-off. The library-first path is cheaper and reversible.

### 4.1 Arithmetic contract

All `Money` arithmetic is **explicit, checked and currency-safe**:

```kof
// PROPOSED
Money add(Money a, Money b)          // throws if currencies differ; overflow-checked
Money sub(Money a, Money b)          // throws if a.minor < b.minor (unless negate)
Money negate(Money a)                // throws on Long.MIN_VALUE
Money scale(Money a, Int num, Int den, Rounding mode)   // multiply then divide
```

- **Currency mismatch is an error, never an implicit conversion.** Conversion
  is an explicit FX operation (§4.3).
- **Overflow is detected** with pre-checks (`a.minor > Long.MAX - b.minor`),
  not silently wrapped. Diagnostics `FIN001` (currency mismatch) and `FIN002`
  (amount overflow).
- `add`/`sub` are **top-level functions**, not methods — Kof has no operator
  overloading, and `Money` is a record.

### 4.2 Rounding

Rounding is a **first-class explicit argument**, never a default.

```kof
// PROPOSED
enum Rounding { HalfUp, HalfEven, Down, Up, Floor, Ceiling }
Money roundTo(Money a, Int scale, Rounding mode)
```

- `HalfEven` (banker's rounding) is the default recommended for settlement;
  `HalfUp` for display. The mode is always passed by the caller.
- Rounding is implemented over integer arithmetic (quotient/remainder), so it
  is exact and identical on every target. It **must not** route through
  `Double`/`math.roundTo`.
- `D-FINANCIAL-ROUNDING` records the integer algorithm and the default.

### 4.3 FX and rates

```kof
// PROPOSED
record Rate(String base, String quote, Long num, Long den, Long asOfMillis, String source)
Money convert(Money amount, Rate rate, Rounding mode)   // amount.currency == rate.base
```

- A rate is an exact rational `num/den` (e.g. `5_4321/1_0000`), **not** a
  `Double`; this preserves precision and makes conversion deterministic.
- `asOfMillis` and `source` are mandatory: a rate without provenance is a bug.
- Rate lookup is a `RateSource` interface (§12), so tests use a fixed table
  and production uses a provider.
- Cross rates (A→B→C) compose by rational multiplication, with a documented
  intermediate-rounding rule (`D-FINANCIAL-FX`).

## 5. Proposed surface (intent, not final grammar)

Namespaces (each a pure-Kof package; no core change):

| Namespace | Responsibility | Examples |
|---|---|---|
| `kof.financial` | the umbrella import | re-exports the public records |
| `kof.financial.money` | `Money`, `Currency`, arithmetic, rounding | `add`, `roundTo` |
| `kof.financial.fx` | `Rate`, conversion, rate sources | `convert` |
| `kof.financial.account` | `Account`, `Balance`, holds | `balanceOf` |
| `kof.financial.ledger` | `Entry`, `Journal`, `Posting`, double-entry | `post`, `journal` |
| `kof.financial.tx` | transaction lifecycle + state machine | `authorize`, `capture` |
| `kof.financial.fees` | fee schedules, split, rounding | `applyFees` |
| `kof.financial.events` | domain events, outbox, webhooks | `emit`, `dispatch` |
| `kof.financial.idem` | idempotency keys and records | `idempotent` |
| `kof.financial.recon` | reconciliation against statements | `reconcile` |
| `kof.financial.provider` | `Provider` interface + adapters | `FakeProvider` |
| `kof.financial.compliance` | structured checks + decisions | `CheckResult` |
| `kof.financial.jurisdiction` | jurisdiction profiles as data | `JurisdictionProfile` |

### 5.1 Core records (all `PROPOSED`, all pure Kof)

```kof
record Currency(String code, Int minorUnits, String symbol)
record Money(Long minor, String currency)
record Account(String id, String ownerRef, String currency, String kind)
record Balance(String accountId, Long postedMinor, Long pendingMinor)
record Entry(String accountId, String direction, Long minor, String currency)
record Journal(String id, String correlationId, Long postedAtMillis, List<Entry> entries)
record Event(String id, String kind, String payloadJson, Long occurredAtMillis)
```

`direction` is `"debit"`/`"credit"` (a `String` constrained by a helper, or an
`enum Direction { Debit, Credit }` once the enum has no body — which is the
case today, `classes.md:147-151`). The enum form is preferred and is
`EXISTING`-compatible.

### 5.2 Store / port interfaces

```kof
// PROPOSED
interface LedgerStore {
    Journal append(Journal j)
    List<Journal> journalsFor(String accountId)
    Balance balance(String accountId)
}

interface Clock { Long nowMillis() }
interface IdGenerator { String next(String prefix) }
interface RateSource { Rate? rate(String base, String quote) }
```

These are `EXISTING` mechanisms: `interface` with abstract methods
(`classes.md:196-220`). Implementations: `DbLedgerStore` (over `kof.db`),
`MemoryLedgerStore` (over `List`/`Map`), `OrmLedgerStore` (over `kof.orm`).

## 6. Transaction lifecycle (state machine)

The lifecycle is the *spine* of a payment. It is modelled explicitly so that
invalid transitions are impossible, not merely discouraged.

```kof
// PROPOSED
enum TxState { Created, Authorized, Captured, Settled, Refunded, Reversed, Failed, Expired }
record Transaction(String id, String idempotencyKey, Money amount, TxState state,
                   Long createdAtMillis, Long updatedAtMillis, String providerRef)
```

Transitions (each a top-level function returning a new immutable record):

| From | Event | To | Rule |
|---|---|---|---|
| `Created` | `authorize` | `Authorized` | provider approves |
| `Created` | `fail` | `Failed` | provider declines |
| `Authorized` | `capture` | `Captured` | amount ≤ authorized |
| `Authorized` | `expire` | `Expired` | past `expiresAt` |
| `Captured` | `settle` | `Settled` | funds land |
| `Captured`/`Settled` | `refund` | `Refunded` | ≤ captured, may be partial |
| any non-terminal | `reverse` | `Reversed` | void authorization/chargeback |
| `Settled` | `refund` | `Refunded` | reversal/refund distinction explicit |

- **`refund` vs `reversal`** are distinct events with distinct ledger postings:
  a refund is a new outbound movement; a reversal voids a prior authorization
  (no funds moved) or claws back a settled charge. `D-FINANCIAL-LIFECYCLE`
  records the exact semantics.
- Each transition emits an `Event` (§10) and (when settled) a balanced
  `Journal` (§8). Partial captures/refunds are first-class.
- Invalid transitions throw `"FIN003 invalid transition <from> -> <event>"`.

## 7. Fees

Fees are computed by an explicit schedule and applied as **separate ledger
entries**, never folded into the principal amount.

```kof
// PROPOSED
record FeeRule(String id, String kind, Long fixedMinor, Long bps, String currency)
record Fee(String ruleId, Money amount)
List<Fee> applyFees(Money principal, List<FeeRule> rules)
Money netOfFees(Money principal, List<Fee> fees)
```

- `kind` ∈ `"fixed"`, `"percentage"` (basis points, integer), `"tiered"`,
  `"interchange"`. All arithmetic is integer + explicit rounding.
- A fee is always posted to its own account (e.g. `fees:interchange`), so the
  journal balances and reporting is possible.
- `D-FINANCIAL-FEES` records the bps convention (1 bp = 1/10000) and the
  rounding point.

## 8. Double-entry ledger

The ledger is the **source of truth**; balances are derived.

```kof
// PROPOSED
Journal post(List<Entry> entries, String correlationId)
Bool isBalanced(Journal j)     // sum(debits) == sum(credits), same currency
```

Rules:

1. **Every journal balances**: the sum of debit minor units equals the sum of
   credit minor units, per currency. `isBalanced` is checked before append;
   an unbalanced journal throws `"FIN004 unbalanced journal"`.
2. **Entries are immutable and append-only.** There is no update or delete;
   a correction is a *reversing journal* that references the original
   `correlationId`. This is the same instinct as `entity-history-plan.md`.
3. **Signs are explicit** via `direction`, never encoded as negative amounts.
4. **Multi-currency journals** hold one balanced sub-journal per currency;
   the FX movement itself is a pair of entries in a dedicated
   `fx:position` account.
5. **Account types** follow the accounting identity: `asset`, `liability`,
   `equity`, `revenue`, `expense`. Balance sign convention is documented in
   `D-FINANCIAL-LEDGER`.

### 8.1 Balance derivation

```kof
// PROPOSED
Long postedBalance(String accountId, LedgerStore store)   // sum of posted entries
Long availableBalance(Balance b)                          // posted - pending holds
```

Balances are **computed from entries**, never stored as the primary truth;
a cached `Balance` is an optimization the store may keep, and `reconcile`
(§11) verifies the cache against the entries.

### 8.2 Holds / pending

```kof
// PROPOSED
record Hold(String accountId, Long minor, String currency, Long expiresAtMillis)
Balance applyHold(Balance b, Hold h)
```

A hold reduces `available` but not `posted`; capture converts a hold into
posted entries. This is what makes authorize/capture honest.

## 9. Accounts and balances

- An `Account` is identified by an opaque `id` plus an `ownerRef` and a
  `currency`; a multi-currency owner has one account per currency.
- **No negative-balance assumption** is baked in; whether an account may go
  negative is policy (`AccountPolicy`), not model.
- `Account.kind` ∈ `"customer"`, `"merchant"`, `"fee"`, `"fx"`, `"settlement"`,
  `"external"`.

## 10. Events, webhooks, idempotency, retries

### 10.1 Idempotency

```kof
// PROPOSED
record IdempotencyRecord(String key, String requestHash, String responseJson, Long createdAtMillis)
String idempotencyKey(String prefix)            // prefix + uuid.v7()
Transaction idempotent(String key, String requestHash, () -> Transaction body)
```

- Every mutating operation accepts an `idempotencyKey`. A replay with the
  **same key and same request hash** returns the stored response; a replay
  with the same key but a **different hash** is an error `FIN005`
  (idempotency conflict) — never a silent second charge.
- Keys use `uuid.v7()` (`KofUuid.java:35-49`) so they are time-ordered.

### 10.2 Domain events + outbox

```kof
// PROPOSED
record Event(String id, String kind, String aggregateId, String payloadJson, Long occurredAtMillis, String correlationId)
void emit(LedgerStore store, Event e)          // append to the outbox in the same transaction
List<Event> pending(LedgerStore store)
void markDispatched(LedgerStore store, String eventId)
```

- Events are written to an **outbox** in the same `transaction { … }` as the
  ledger posting (`KofDb.java:110-112`), so state and event never diverge.
- A dispatcher (application code, or `spawn`) drains the outbox.

### 10.3 Webhooks (outbound and inbound)

- **Outbound:** sign the payload with `crypto.hmacSha256(secret, body)`
  (`KofSecurity.java:75`), POST with `http.post` (`KofHttp.java`), record the
  attempt. Retries use exponential backoff with a max attempt count; every
  attempt is an `Event`.
- **Inbound:** verify the signature with `crypto.hmacSha256` and a
  **constant-time** comparison (`security.constantTimeEquals`), reject stale
  timestamps, then dedupe by event id (idempotency).
- Retry policy is data (`RetryPolicy(Int maxAttempts, Long baseDelayMillis,
  Long maxDelayMillis)`), not a constant buried in code.

## 11. Reconciliation

```kof
// PROPOSED
record StatementLine(String externalId, Long minor, String currency, Long valueDateMillis, String raw)
record Match(String journalId, String statementLineId, String kind)   // exact | partial | unmatched
List<Match> reconcile(List<Journal> ours, List<StatementLine> theirs, Int toleranceMinor)
```

- Matching is deterministic: exact amount+currency+date window first, then
  tolerance, then manual. Unmatched items are **returned**, never dropped.
- `toleranceMinor` is explicit; `0` means exact.
- The result is a report value; a reconciliation *decision* is application
  policy.

## 12. Providers / adapters

```kof
// PROPOSED
interface Provider {
    AuthorizeResult authorize(Transaction t)
    CaptureResult capture(Transaction t)
    RefundResult refund(Transaction t, Money amount)
    List<StatementLine> statement(String accountRef, String fromIso, String toIso)
}
```

- `Provider` is an interface (`classes.md:196-220`); `FakeProvider` lives in
  the library and is the test double. Real adapters live in
  `kof.financial.provider.*` and use `kof.http`/`kof.json`.
- Provider errors are mapped to a **closed** `ProviderError` record with a
  `retryable` flag; the lifecycle layer decides whether to retry.
- **No provider call happens inside `Money` arithmetic.** The port boundary is
  explicit and mockable.

## 13. Persistence and migrations

- `LedgerStore` adapters: `MemoryLedgerStore` (tests), `DbLedgerStore`
  (`db.query`/`db.execute`/`transaction`, `KofDb.java:80`), `OrmLedgerStore`
  (`entity` + ORM, `KofOrm.java:147`).
- Tables (via `entity`): `account`, `journal`, `entry`, `event`, `idempotency`,
  `hold`, `statement_line`. All append-only except caches.
- Schema evolution: `orm.migrate` exists (`KofOrm.java:147`); the plan requires
  **forward-only** migrations with a version table. This reuses ORM, no core
  change.
- Money is stored as `Long` minor + `String` currency columns; **never** a
  floating column. A DB check must reject float columns for these fields.

## 14. Compliance boundary (structure only)

The library does **not** implement law; it makes the *boundary* explicit.

```kof
// PROPOSED
enum CheckOutcome { Pass, Review, Block }
record CheckResult(CheckOutcome outcome, String code, String detail)
interface ComplianceCheck { CheckResult run(Transaction t) }
```

- Checks (KYC status, sanctions screening, velocity limits) are composed as a
  `List<ComplianceCheck>`; the app supplies them.
- The **decision** is always recorded as a `CheckResult` with a code and a
  reason, and emitted as an `Event`. No silent blocking.
- Jurisdiction profiles (§15) parameterize which checks apply.

## 15. Jurisdiction profiles

Jurisdictions are **data records**, not code branches:

```kof
// PROPOSED
record JurisdictionProfile(String code, String currency, Int minorUnits,
                           List<String> requiredChecks, Long maxAmountMinor,
                           Int settlementDays, String taxIdLabel)
```

- `code` is ISO-3166 alpha-2; profiles live in a Kof data file and are loaded
  by the app.
- A profile may set `maxAmountMinor`, required checks, settlement window and
  local currency. It **never** contains business logic.
- Profiles are versioned (effective date) so a rule change is auditable.

## 16. Cross-target matrix (§93)

`kof.financial` is pure Kof; it inherits the parity of the primitives it uses.
The honest expectation (to be measured at implementation time, RED-first):

| Capability | JVM | Native x86-64 | Native riscv64/aarch64 | JS | Script |
|---|---|---|---|---|---|
| `Long` arithmetic + overflow pre-checks | DONE | DONE | DONE | DONE | DONE |
| `record` structural equality | DONE | DONE | DONE | DONE | DONE |
| `enum` + `switch` | DONE | DONE | DONE | DONE | DONE |
| `List`/`Map` in-memory store | DONE | DONE | DONE | DONE | DONE |
| `json.encode/decode<T>` | DONE | DONE (gaps `JSN001/002/004` on some shapes) | DONE (same gaps) | DONE | DONE |
| `db.connect` + `transaction {}` | DONE | DONE (SQLite/MySQL wire) | DONE | DONE | TBD |
| `http.post` (provider calls) | DONE | DONE (https → throw) | DONE (https → throw) | DONE | TBD |
| `crypto.hmacSha256` (webhooks) | DONE | DONE | **`SECN000`** (AES-GCM/ChaCha20 cross gaps) | DONE | DONE |
| `uuid.v7` | DONE | DONE | DONE | DONE | DONE |
| `spawn`/`await` (async dispatch) | DONE | DONE | DONE | DONE | DONE |
| `time.now` + ISO helpers | DONE | DONE | `TIME003` (`tzOffsetSeconds`) | DONE | DONE |

Rules: a capability that is not honestly available on a target must raise a
**named diagnostic** at the call site (`SECN000`, `JSN00x`, `TIME003`,
`DB001`, …) — never a silent fallback. The implementation plan must re-measure
every row and replace `DONE` with the executed proof.

## 17. Requirements traceability matrix (§92)

The brief's themes are enumerated here and classified. `EXISTING` = the
primitive exists today; `PROPOSED` = pure-Kof library work (no core change);
`CORE` = requires a frozen-semantics maintainer decision; `FUTURE` = out of v1.

| # | Requirement | Class | Primitive / plan |
|---|---|---|---|
| 1 | Represent money exactly | PROPOSED | `Money(Long minor, String code)` §4 |
| 2 | Never use float for money | PROPOSED | boundary diagnostic `FIN006` §2 |
| 3 | Currency metadata + minor units | PROPOSED | `Currency` + data table §4 |
| 4 | Currency-safe arithmetic | PROPOSED | `add`/`sub` + `FIN001` §4.1 |
| 5 | Overflow detection | PROPOSED | pre-checks + `FIN002` §4.1 |
| 6 | Explicit rounding modes | PROPOSED | `Rounding` enum + `roundTo` §4.2 |
| 7 | FX conversion with exact rates | PROPOSED | `Rate` rational + `convert` §4.3 |
| 8 | Rate provenance (source/time) | PROPOSED | `Rate.asOfMillis`/`source` §4.3 |
| 9 | Accounts (per currency) | PROPOSED | `Account` §9 |
| 10 | Balances derived from entries | PROPOSED | `postedBalance` §8.1 |
| 11 | Holds / pending | PROPOSED | `Hold` + `availableBalance` §8.2 |
| 12 | Double-entry postings | PROPOSED | `Journal`/`Entry` + `isBalanced` §8 |
| 13 | Immutable append-only ledger | PROPOSED | no update/delete; reversing journal §8 |
| 14 | Multi-currency journals | PROPOSED | per-currency sub-journals §8 |
| 15 | Transaction lifecycle | PROPOSED | `TxState` + transitions §6 |
| 16 | Payment/transfer | PROPOSED | lifecycle + journal §6/§8 |
| 17 | Refund (full/partial) | PROPOSED | `refund` transition §6 |
| 18 | Reversal / chargeback | PROPOSED | `reverse` transition §6 |
| 19 | Settlement | PROPOSED | `settle` transition + settlement account §6 |
| 20 | Fee schedules | PROPOSED | `FeeRule`/`applyFees` §7 |
| 21 | Fee accounts / net amount | PROPOSED | separate entries §7 |
| 22 | Idempotency keys | PROPOSED | `uuid.v7` + `IdempotencyRecord` §10.1 |
| 23 | Idempotency conflict detection | PROPOSED | `FIN005` §10.1 |
| 24 | Domain events | PROPOSED | `Event` §10.2 |
| 25 | Outbox (atomic with posting) | PROPOSED | `transaction { }` §10.2 |
| 26 | Webhooks outbound (signed) | PROPOSED | `crypto.hmacSha256` + `http.post` §10.3 |
| 27 | Webhooks inbound (verified) | PROPOSED | HMAC + constant-time + dedupe §10.3 |
| 28 | Retries with backoff | PROPOSED | `RetryPolicy` §10.3 |
| 29 | Reconciliation | PROPOSED | `reconcile` + `Match` §11 |
| 30 | Unmatched reporting | PROPOSED | returned, never dropped §11 |
| 31 | Provider interface | PROPOSED | `Provider` §12 |
| 32 | Provider error mapping | PROPOSED | `ProviderError(retryable)` §12 |
| 33 | Fake provider for tests | PROPOSED | `FakeProvider` §12 |
| 34 | Persistence ports | PROPOSED | `LedgerStore` §5.2/§13 |
| 35 | DB/ORM adapters | PROPOSED | `KofDb`/`KofOrm` §13 |
| 36 | Forward-only migrations | PROPOSED | `orm.migrate` §13 |
| 37 | Compliance check boundary | PROPOSED | `ComplianceCheck` §14 |
| 38 | Recorded compliance decisions | PROPOSED | `CheckResult` + event §14 |
| 39 | Jurisdiction profiles as data | PROPOSED | `JurisdictionProfile` §15 |
| 40 | Profile versioning | PROPOSED | effective-date §15 |
| 41 | Correlation ids | EXISTING | `KofObservability.java` |
| 42 | Secure random / ids | EXISTING | `crypto.randomHex`/`uuid.v7` |
| 43 | Time source | EXISTING | `time.now()` |
| 44 | JSON payloads | EXISTING | `json.encode/decode<T>` |
| 45 | HTTP transport | EXISTING | `kof.http` |
| 46 | Webhook receiver | EXISTING | `kof.web` |
| 47 | Concurrency (async dispatch) | EXISTING | `spawn`/`await`/`channel` |
| 48 | Error reporting | EXISTING | String exceptions + `FIN0xx` |
| 49 | Decimal core scalar | CORE | `D-FINANCIAL-DECIMAL` (not v1) |
| 50 | Operator overloading for `Money` | CORE | forbidden, not proposed |
| 51 | Timezone-aware settlement | FUTURE | needs a date/time type |
| 52 | ISO-20022 message mapping | FUTURE | rides `kofbol-plan.md` |
| 53 | Card network connectivity | FUTURE | provider adapter, out of v1 |
| 54 | Tax calculation engine | FUTURE | app/fee concern, out of v1 |
| 55 | Full AML/KYC engine | FUTURE | compliance vendor, out of v1 |

> The brief's full 102-item enumeration was supplied as a summarized scope; this
> matrix is the honest, evidence-anchored mapping of that scope. Any item not
> listed here is **unknown** and must not be claimed. `D-FINANCIAL-TRACE`
> records the mapping and the open items.

## 18. Jurisdiction / compliance matrix (§94)

Profiles are examples of the *shape*, not legal advice; every cell is data.

| Jurisdiction | Currency | Minor units | Required checks (v1 shape) | Max amount | Settlement |
|---|---|---|---|---|---|
| BR | BRL | 2 | KYC, velocity | profile | D+1 |
| US | USD | 2 | KYC, sanctions | profile | D+1 |
| EU | EUR | 2 | KYC, SCA, sanctions | profile | D+1 |
| JP | JPY | 0 | KYC | profile | D+1 |
| BH | BHD | 3 | KYC, sanctions | profile | D+2 |

The matrix is loaded from a versioned Kof data file; adding a jurisdiction is
adding a row, never a code branch. `D-FINANCIAL-JURISDICTION` records the file
format and the versioning rule.

## 19. Errors and diagnostics

| Code | Meaning | Where |
|---|---|---|
| `FIN001` | currency mismatch | `add`/`sub`/`convert` |
| `FIN002` | amount overflow | arithmetic |
| `FIN003` | invalid lifecycle transition | `tx` |
| `FIN004` | unbalanced journal | `post` |
| `FIN005` | idempotency conflict | `idempotent` |
| `FIN006` | float used for money | boundary check |
| `FIN007` | unknown currency | `Currency` lookup |
| `FIN008` | rate missing/expired | `convert` |
| `FIN009` | reconciliation tolerance exceeded | `reconcile` |

These are **runtime** `throw "FIN00x ..."` messages (Kof's error currency is
`String` — `statements.md:166-167`). A compile-time diagnostic is only
possible if a future core feature allows it; the library must not pretend.
The codes are registered in `docs/backend-parity.md` when the plan is promoted.

## 20. Test plan (when implemented)

- **Money:** property-style tests over random `Long`/currency pairs; overflow
  boundaries (`Long.MAX`), rounding at `.5` for every mode, negative amounts.
- **Ledger:** every posting balances; reversing a journal restores the exact
  prior balance; multi-currency sub-journals balance independently.
- **Lifecycle:** every legal transition, every illegal transition throws
  `FIN003`; partial capture/refund arithmetic.
- **Idempotency:** replay same key+hash returns same response; same key
  different hash → `FIN005`; concurrency via `spawn` (sequentially-consistent
  model — `concurrency-memory-model.md`).
- **Events/outbox:** event and posting commit atomically; rollback leaves
  neither.
- **Webhooks:** signature verify accepts good/rejects tampered; stale timestamp
  rejected; inbound dedupe.
- **Recon:** exact/partial/unmatched, tolerance boundaries.
- **Cross-target:** run the suite on JVM + Native x86-64 + riscv64/aarch64
  (qemu) + JS + Script; assert named diagnostics where a primitive is absent.
- **RED-first** for every bug fix and every new capability (`Q0`/`Q1`).

## 21. Roadmap (each slice one lane, RED-first; nothing starts from this doc alone)

| Slice | Scope | Depends on | Proof |
|---|---|---|---|
| F0 | `Money`, `Currency`, arithmetic, rounding | — | money unit tests, 5 targets |
| F1 | `Account`, `Balance`, holds | F0 | balance derivation tests |
| F2 | `Journal`, `Entry`, double-entry posting | F1 | balanced-posting tests |
| F3 | lifecycle state machine | F2 | transition matrix tests |
| F4 | fees | F2 | fee split tests |
| F5 | idempotency + events + outbox | F2 | replay/atomicity tests |
| F6 | webhooks (sign/verify/retry) | F5 | signature + retry tests |
| F7 | reconciliation | F2 | match/unmatched tests |
| F8 | provider interface + fake | F3 | fake-provider E2E |
| F9 | DB/ORM adapters + migrations | F2 | persistence E2E |
| F10 | compliance + jurisdiction profiles | F3 | profile-driven check tests |

Each slice is one cohesive unit; `D-FUTURE-PROMOTION` allows one plan at a
time, and each slice is claimed in `DOING.md` with `owner = <ip>:<port>`.

## 22. Open decisions (rule 6 — maintainer)

1. `D-FINANCIAL-MONEY` — minor units + ISO code (recommended) vs a core
   decimal scalar.
2. `D-FINANCIAL-ROUNDING` — default settlement mode (`HalfEven` recommended).
3. `D-FINANCIAL-FX` — intermediate-rounding rule for composed cross rates.
4. `D-FINANCIAL-LIFECYCLE` — exact refund vs reversal semantics and partial
   rules.
5. `D-FINANCIAL-LEDGER` — balance sign convention per account type.
6. `D-FINANCIAL-FEES` — bps convention and rounding point.
7. `D-FINANCIAL-JURISDICTION` — profile file format and versioning.
8. `D-FINANCIAL-TRACE` — the requirement enumeration source (the 102-item list)
   and its canonical home.
9. `D-FINANCIAL-GO` — authorization to promote this plan at all (currently
   frozen by `D-FUTURE-FREEZE`).

## 23. Non-regression contract

- No existing language surface changes; no frozen semantics touched.
- No float in any money path; a test must prove `FIN006` fires.
- Every new namespace is registered in `scripts/stdlib_boundary.txt` when
  promoted (R1 gate).
- Every diagnostic is catalogued in `docs/backend-parity.md` (R6).
- The plan stays `planned`; nothing here is implemented until promoted.

## 24. Final review question

> Can a Kof application represent money exactly, post a balanced ledger, drive
> a payment through its full lifecycle with idempotent retries, and reconcile
> the result against an external provider — on JVM, Native and JS — using only
> `kof.financial` and primitives that already exist?

If the answer is no because a primitive is missing, the missing primitive is
named, classified `CORE` or `FUTURE`, and sent to the maintainer — never
patched around silently.
