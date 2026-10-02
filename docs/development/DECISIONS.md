[Português](DECISIONS.pt_BR.md) | [English](DECISIONS.md)

# DECISIONS — language decision record

**Last updated:** 2026-09-30

**Maintainer:** Mel Santos

**Nature:** normative and historical record of language architecture, semantics, and evolution decisions.

> This file records decisions that have already been made. It is not a backlog, an implementation diary, or a collection of open proposals.

> A decision recorded here remains the source of truth until it is formally superseded by another decision. Code, tests, and the roadmap must converge on this contract.

---

## 0. Decision index

A navigation aid, not a decision by itself. Ordered as in this file.

- **1.** Authority and change rules
- **2.** Global invariants
- **D-STDLIB** — time and calendar
- **D-SEC** — security
- **D-APP** — application model
- **D-SPRING** — framework independence
- **D-RELEASE** — patch evaluation criterion
- **D-ASM-GATE** — riscv/aarch ASM gate
- **D-BACKEND-SEMANTICS** — backend semantics
- **D-BASELINE** — toolchain baseline
- **D-NULL** — nullability and primitives
- **D-NULL-INTENT** — explicit nullability intent
- **D-PRINT** — implicit Char conversion
- **D-NARROW-WHILE** — flow narrowing
- **D-ENUM207** — enum identity
- **D-VALUE-RECORD** — value records / first-class value types
- **D-DEV-PRIORITY** — "Em desenvolvimento" is the absolute priority
- **D-DIAG-EN** — tooling/diagnostics in English; docs EN+PT
- **D-DECL-RETURN** — declared return type is law (#333)
- **D-NOT-JAVA** — Kof is not Java/Kotlin
- **D-UI-STYLE** — declarative `style` (UI007)
- **D-UI-TOKENS** — design-system tokens
- **D-UI-APPSTATE** — `AppState(initial)` root store
- **D-UI-DIFF** — node reuse
- **D-UI-AUTOUNSUB** — component-scoped subscriptions
- **D-UI-CANCELLED** — `cancelled()` in async UI actions
- **D-UI-SCOPE** — `kof.ui` rule updates
- **D-UNIVERSAL** — universal-platform promotion
- **D-TRIAGE** — philosophy check precedes the issue
- **D-POLL-19** — every pending decision resolved (poll 19/09)
- **D-TROOL** — `Bool` never nullable; `Troolean`
- **D-KOF-FIRST** — internal contract before external comparison
- **D-SCHED-DURATION** — idiomatic durations in `scheduler.at`
- **D-WORKFLOW-RUN** — `kof workflow run`
- **D-MAKEALIVE** — Kof Makealive (Stage 3)
- **D-MAKEALIVE-CLI** — 3.8 contract: `kof makealive plan|apply|destroy` (20/09)
- **D-MAKEALIVE-SYNTAX** — 3.2 `infra "prod" { }` = pure sugar over `design()` (21/09)
- **D-ARRAY-PRINT** — §388-B: `println(Int[])` is the §107 container format (21/09)
- **D-KOF-AS-CLOUD** — Kof must BE the cloud
- **D-BOOTSTRAP** — the bootstrapper (Kof in Kof)
- **D-DB-GAPS** — DB/ORM orphan gaps
- **D-BRANCH-0.5.0** — work moves to `beta-0.5.0` *(SUPERSEDED 28/09 by D-QUALITY-PIPELINE-2609 / D-BRANCH-PIPELINE)*
- **D-RELEASE-1.0** — KOF 1.0 EXIT GATE
- **D-VERSION-BUMP-0.5.0** — revision to `0.5.0-beta`
- **D-1.0-EDGES** — open edges closed
- **D-SLOT-PIN** — §383/#561 blessed-miss stored value
- **D-RELEASE-0.5.0-GATE** — 0.5.0 release gate
- **D-RELEASE-0.5.0-SCOPE** — in-flight plans allowlisted; EG-8 decoupled
- **D-RULE6-BATCH** — triage rule-6: six decisions (functions as values, SEM084-087, ABI in 1.0)
- **D-FFI-STRUCT** — FFI struct/array ABI (D6)
- **R6-SCOPE** — incremental delivery does not breach R6
- **D-R3-BUFFER** — out-buffer = nominal `Buffer(U8)`
- **D-R3-HANDLE-LIFETIME** — `Handle` memory is automatic
- **D-ARTIFACT-TRUST** — 1.0 artifact trust contract
- **D-VERSIONING-RELEASE** — consolidated versioning and release-cut policy
- **D-DEBT-SCOUT** — technical-debt scout tooling authorized, Wave 1 only, no Issue-publish capability
- **D-DEBT-SCOUT-W2** — Wave 2 authorized (evidence qualification, clustering, SARIF); still shadow, still no Issue-publish
- **D-KOF-IS-KOF** — Kof source never embeds HTML/CSS/JS (absolute rule)

---

## 1. Authority and change rules

### 1.1 Who decides

The maintainer decides matters of contract, semantics, architecture, and language direction.

Agents and contributors may:

* investigate alternatives;
* propose decisions;
* implement approved decisions;
* fix bugs and divergences from the contract;
* update execution evidence and status.

Agents and contributors **must not alter the contract of a decision through their own interpretation**.

### 1.2 How a decision enters this file

A decision is considered effective only when recorded with:

* stable identifier;
* date;
* scope;
* contract;
* decision taken;
* relationship to previous decisions, when applicable;
* implementation status, if any.

The decision does not live in chat. Chat may contain the discussion; this file contains the normative outcome.

### 1.3 How a decision is revised

An effective decision may only be changed by a new entry that:

1. identifies the previous decision;
2. explains what changes;
3. records the new decision;
4. preserves the history;
5. updates the roadmap and affected documentation.

A superseded decision is not deleted.

### 1.4 Allowed states

| State         | Meaning                                                  |
| ------------- | -------------------------------------------------------- |
| `DECIDED`     | Contract approved, not yet fully implemented             |
| `IN_PROGRESS` | Implementation in progress                               |
| `IMPLEMENTED` | Implementation completed and validated                   |
| `PARTIAL`     | Part of the contract implemented; explicit gaps remain   |
| `BLOCKED`     | Implementation depends on another decision or capability |
| `SUPERSEDED`  | Replaced by another decision                             |
| `REJECTED`    | Analyzed alternative that was rejected                   |
| `CLOSED`      | Record closed with no remaining backlog                  |

Implementation status **does not alter the contract**.

---

## 2. Global invariants

These rules remain valid regardless of the decisions below.

### G-01 — Surface freeze

The 0.2.6-beta freeze remains in effect for explicitly frozen items:

* operators;
* `==`;
* `spawn`;
* collections;
* other items covered by rule 6.

A later decision may alter a frozen contract only when it explicitly records the corresponding revision.

### G-02 — Universal vision

R1–R12 remain invariants of the language's universal vision.

### G-03 — Complexity limit

The ≤500 rule remains in effect.

### G-04 — Suite as gate

The conformance suite remains the integration gate. Code cannot be considered complete merely because it compiles locally.

### G-05 — Honest gap

When a capability does not exist on a given target, the system must:

* report the documented gap;
* use the corresponding diagnostic code;
* never produce a silently incorrect result;
* never simulate nonexistent support as if it were real support.

### G-06 — Parity

When a decision defines observable behavior, implementation must seek the same contract across all supported targets.

A difference between targets is acceptable only when:

1. it is explicitly documented;
2. it has a defined diagnostic or behavior;
3. it is represented in the conformance matrix.

### G-07 — Determinism

The same input, same contract, and same target must produce a deterministic result.

When the decision requires cross-target parity, the observable result must be equivalent, except for explicitly recorded gaps.

### G-08 — Do not reinvent the wheel

The compiler and runtime internals should primarily draw inspiration from:

1. Java — JLS, JVMS, and `java.lang`/`java.math` behavior;
2. C — ISO C and `libm`, especially for the native backend;
3. other languages, when the reference is a backend technique.

This rule does not authorize copying another language's surface. Syntax, ergonomics, and writing model remain Kof-specific decisions.

---

# 3. Current decisions

## D-STDLIB — time and calendar

**Date:** 2026-09-13

**State:** `IMPLEMENTED`

**Scope:** date, time, and calendar semantics of the stdlib.

### Contract

**D-STDLIB.1 — UTC as the default reference**

`today()` and `isToday` derive from `now()` in UTC on all targets.

The local timezone may only be obtained through an explicit API:

```kof
time.tzOffsetSeconds()
```

Native without timezone support reports `TIME003`.

There is no accidental timezone parity between targets.

**D-STDLIB.2 — Scalar calendar**

The base calendar API uses scalar values over ISO.

Example:

```kof
time.addDays("YYYY-MM-DD", n) -> String
```

The base stdlib does not introduce compound return values for calendar operations.

**D-STDLIB.3 — Hour difference**

`hoursBetween` counts complete integer hours, truncated toward zero, consistent with `daysBetween`.

It does not use float nor a 12-argument signature.

**D-STDLIB.4 — ISO formatting**

```kof
time.formatDateIso(y, m, d) -> String
```

Invalid date returns `""`.

**D-STDLIB.5 — ISO parsing**

```kof
time.parseDateIso(value) -> Int
```

Returns the `daysFromEpoch` serial.

Invalid input returns `0`.

Arbitrary patterns such as `dd/MM/yyyy` are not part of the base stdlib.

**D-STDLIB.6 — isToday**

```kof
time.isToday(y, m, d) -> Bool
```

Compares against the UTC date derived from `now()`.

### Ratified API

| Function                    | Contract                  | Targets                   |
| --------------------------- | ------------------------- | ------------------------- |
| `time.todayIso()`           | `() -> String`            | 5                         |
| `time.formatDateIso(y,m,d)` | `(Int,Int,Int) -> String` | 5                         |
| `time.isToday(y,m,d)`       | `(Int,Int,Int) -> Bool`   | 5                         |
| `time.hoursBetween(...)`    | `(Int × 8) -> Int`        | 5                         |
| `time.parseDateIso(String)` | `String -> Int`           | 5                         |
| `time.tzOffsetSeconds()`    | `() -> Int`               | JVM/JS/SCRIPT; Native gap |

### Evidence

S7e–S7h implementation completed on 2026-09-13.

* `KofTimeE2ETest`: 30/30
* `stdtime3`–`stdtime6` matrices
* Script parity
* Suite: 1772/0/0

**Implementation reference:** `docs/stdlib/time.md`

**Queue:** closed.

---

## D-SEC — security

**Date:** 2026-09-13–14

**State:** `PARTIAL`

**Scope:** cryptography, cookies, security middleware, and OAuth2/OIDC.

### Invariants

* Cryptography is not implemented from scratch.
* JVM uses JCA where applicable.
* JS uses WebCrypto where applicable.
* Native uses an audited implementation or reports a gap.
* Algorithms, token formats, and validation rules are observable contracts.
* Gaps are reported through `SECN00x`.

### D-SEC.1 — ChaCha20-Poly1305

**Decision:** add ChaCha20-Poly1305 support according to RFC 8439.

Format:

```text
chacha20$<nonceB64(12B)>$<ct+tagB64(16B tag)>
```

API:

```kof
security.chacha20Encrypt(text, keyHex) -> String
security.chacha20Decrypt(token, keyHex) -> String
```

The key must be 32 bytes represented in hexadecimal.

Nonce must never be reused with the same key.

Native without implementation reports `SECN002`.

**State:** JVM + JS implemented. Native remains a gap.

**Evidence:** RFC 8439 vector + `node:crypto` + `KofSecurityTest`.

### D-SEC.2 — Cookies

API:

```kof
security.cookieSet(name, value, opts)

security.cookieGet(header, name)
```

Defaults:

* `HttpOnly`;
* `Secure`;
* `SameSite=Lax`;
* `Path=/`.

Native without implementation reports `SECN006`.

**State:** JVM + JS implemented.

### D-SEC.3 — `app.security()` middleware

The pipeline order is fixed:

```text
rate-limit

→ CORS

→ security headers

→ cookies/session

→ CSRF

→ authentication

→ RBAC

→ route
```

The user configures policies but does not reconstruct the internal order.

Without arguments, `app.security()` applies hardening defaults.

In production, `listen`/`listenSecure` without `app.security()` emits a warning.

### D-SEC.4 — Default authentication

When `app.security()` is configured:

* authentication is required by default;
* read methods are not implicitly public;
* public paths must be declared through an allow-list;
* `permitAll` is an alias for `publicPaths`;
* an invalid token never passes silently;
* CSRF is enabled by default for state-changing methods;
* `csrf:false` explicitly disables this protection.

### D-SEC.5 — OAuth2/OIDC

Implementation follows this order:

1. resource server;
2. client authorization-code + PKCE;
3. provider: out of scope.

The resource server validates third-party JWTs through JWKS, issuer, and audience.

Allowed algorithms:

* RS256/384/512;
* ES256/384/512.

Rejected:

* `none`;
* HS*;
* algorithms outside the allow-list.

Native/JS without implementation report `SECN007`.

### D-SEC.6 — TLS

API:

```kof
app.listenSecure(port, certPem, keyPem)
```

The key must be in PKCS#8 PEM.

JVM is the first target.

Self-signed remains a development convenience, not a production configuration.

### Evidence

* `KofSecurityTest`
* `KofWebE2ETest`
* `KofBlogE2ETest`
* `KofOAuthResourceServerTest`

**Reference:** `docs/stdlib/security.md`

**Implementation:** `docs/stdlib/stdlib-web.md`

**Remaining queue:** Native crypto/TLS and documented gaps.

---

## D-APP — application model

**Date:** 2026-09-13

**State:** `PARTIAL`

**Scope:** manifest, component composition, and application execution.

### Contract

**D-APP.1 — Manifest**

The application manifest is:

```text
kof.toml
```

It is optional.

Without a manifest, current behavior must remain 1:1.

**D-APP.2 — Application unit**

An application is a directory containing a Kof module and a `main()`.

Possible components:

* backend;
* frontend;
* static.

An application is the smallest unit of `kof serve` and deployment.

**D-APP.3 — Frontend**

Frontend is another Kof module compiled to JS.

The backend does not call frontend functions directly.

Front→back communication occurs through HTTP/JSON.

**D-APP.4 — System**

System is deployment composition, not compilation composition.

`kof serve --system` remains rejected.

The approved alternative is:

```text
kof serve --list
```

**D-APP.5 — Fat jar**

The flag:

```text
kof build --fat
```

is optional.

The default remains an explicit classpath.

**D-APP.6 — Rebuild**

The frontend is rebuilt on demand by hash.

Watcher remains future.

**D-APP.7 — Targets**

Wasm enters the matrix when the target is opened.

Android is declared supported through the WebView + KofJS model, without changing the application model.

### Manifest

Sections:

```toml
[app]

[serve]

[frontend]

[static]
```

The manifest is read by the CLI, not by the compiler.

Manifest errors must produce a clear diagnostic.

### Allowed topologies

* monolith;
* modular monolith;
* microservices;
* microfrontends;
* full-stack;
* backend-only;
* frontend-only;
* distributed full-stack;
* gateway.

The model does not change between these topologies.

### Evidence

* `KofProjectConfig`
* `CmdBuildFatTest`
* `KofBlogE2ETest`
* `KofWebE2ETest`

**Reference:** `docs/backend-parity.md` (App model `APP001–003`)

**Queue:** `CmdNew` ✅ (`new` in `Main.java:37`); manifest/dependency integration ✅ (`kofdeps` + transitive lock 1.5.2 + registry pull 1.5.3-S2, 19/09); target gaps → tracked in `docs/backend-parity.md` (ledger, not this record). `APP002` ✅ FIXED 23/09 (#598, ratified in chat): `kof serve` forwards `[server] port` from `kof.toml` as `KOF_SERVER_PORT` when the env var is unset (user env wins) — proof `ServeManifestPortE2ETest` 2/2.

---

## D-SPRING — framework independence

**Date:** 2026-09-13 · **Concluded:** 2026-09-19 (audit vs code, this commit)

**State:** `CONCLUDED`

### Contract

* No stdlib component depends on Spring.
* No backend generates Java source as a mandatory step.
* Fundamental capabilities have a Kof-native API.
* Spring may be consumed as an interoperability alternative.
* The independence test has the same weight as the interoperability test.

### Phases

| Phase               | State         |
| ------------------- | ------------- |
| 1–9                 | `IMPLEMENTED` |
| 10 — native testing | `IMPLEMENTED 19/09` (`kof test` harness: `test "nome" { }` → runner sintetizado, `CmdTest.java:15,78`; CliFlagStrictness/CmdBuildAndroidAab cover the face; suite 2772/0F) |
| 11 — complete CLI   | `IMPLEMENTED 19/09` (run/build/test/serve/fmt/deps/init/check all wired in `Main.java:18-41`; deps = Maven + registry 1.5.3-S2) |
| 12 — blog E2E       | `IMPLEMENTED 19/09` (`KofBlogE2ETest` green in the full reactor suite) |

### Phase 10

`kof test` must execute project tests with Kof asserts.

JUnit is not mandatory.

Initial scope:

* unit;
* HTTP with `web.app` on an ephemeral port.

Property testing, stress, and mocks are separate increments.

### Phase 11

Consolidate:

```text
run

build

test

serve

fmt

deps

init

check

new
```

`kofdeps add/remove/list/resolve` already exists.

Dependency integration into the manifest remains.

### Phase 12

The blog E2E is the canonical validation application for the platform:

* backend;
* frontend;
* database;
* authentication;
* validation;
* manifest.

Validation must be performed per target, with honest gaps.

---

## D-RELEASE — patch evaluation criterion

**Date:** 2026-09-14

**State:** `DECIDED`

### Contract

The 0.4.0 line has already been released in #138.

Development continues normally after the release.

When beta is between 100 and 150 commits ahead of main, a patch release should be evaluated.

### Rule

```text
git rev-list --count origin/main..origin/beta-0.4.0
```

When crossing the range:

1. open a release issue;
2. run the complete suite;
3. close known issues in the bugs/parity lane;
4. evaluate the accumulated content;
5. update the version only after the evaluation.

### SemVer

If there is a new material capability that changes the contract, operator, or API surface, the evaluated version ceases to be a patch and becomes minor.

The counter does not freeze features.

Features and fixes completed with a green suite enter the package.

**Status recorded on 2026-09-14:** `main..beta = 1`.

### Relationship (added 2026-09-22 — `D-VERSIONING-RELEASE`)

The history above is preserved (§1.3). `D-VERSIONING-RELEASE` generalizes and
refines this rule without erasing it:

- the `100–150 commits` range is the **ordinary release-evaluation trigger** —
  a trigger, never an authorization to publish;
- the range is `LAST_RELEASE..ACTIVE_BRANCH`, no longer hardcoded to
  `origin/beta-0.4.0`;
- PATCH/MINOR/MAJOR classification now follows `D-VERSIONING-RELEASE`.

---

## D-ASM-GATE — riscv/aarch ASM gate

**Date:** 2026-09-14

**State:** `DECIDED`

### Contract

The specific ASM inspection gate for riscv/aarch is optional while native development is incomplete.

Main and beta must never remain with broken tests.

### Rule

The gate may be reactivated with:

```text
KOF_ASM_GATE=1
```

The default is an explicit skip.

The test body remains portable:

* if `.s` exists, inspect the text;
* if it does not exist, require the linked binary.

### Protection maintained

The duplicated-label regression remains covered by the E2Es under qemu.

### Reactivation

When the Native lane is complete and the 5/5 matrix is green, `assumeTrue` must be removed and the gate becomes mandatory again.

---

## D-BACKEND-SEMANTICS — backend semantics

**Date:** 2026-09-14–15

**State:** `IMPLEMENTED`

This section records semantic decisions that have already been ratified and implemented.

### §101 — NaN in relational comparisons

**Decision:** pure IEEE 754.

Relational comparisons with NaN return `false`.

`!=` returns `true`.

All targets must agree.

### §129 — cross-thread unwind

**Decision:** exception frame per thread.

The exception chain is thread-scoped.

A worker without an internal handler publishes the exception to the handle.

The consumer rethrows it in `await`, `await_timeout`, and `select_any`.

**Extension 19/09 — riscv64/aarch64 mechanism (maintainer's decision in chat):**
the same §129 contract is ported to the cross targets using **real TLS via
`clone`** (not a per-TID table). Each thread gets its own chain head: the main
thread in `_start` (our entry point — it does **not** go through
`__libc_start_main`, so `tp` is ours to set) and each worker via
`CLONE_SETTLS` + a per-worker TLS block (the clone flags already carry
`CLONE_SETTLS`; today `a3`/tls is passed as `0`). `kof_spawn_trampoline`
installs the per-worker handler frame and publishes the cause on `handle->exc`
(offset 48 on riscv), as the x86_64 option B does. **Known blocker to solve in
the implementation:** the aarch64 translator maps riscv `tp` → `x4`
(`NativeAarch64Helpers:74`), which collides with `a4` → `x4` (`:98`) and does
not read `TPIDR_EL0` (the real aarch64 thread pointer, via `mrs`) — the shared
access sites must be made to work on both arches before the port lands.
Evidence of the RED baseline: two cross tests mirroring the x86 §129 hang under
qemu (worker `throw` longjmps the global `kof_exc_chain` of `main`).

**Correction 19/09 — TLS-via-`tp` is ABI-unsafe; mechanism changed to a per-TID
table (measured, agent).** The "real TLS via `clone`" mechanism above was
implemented and **provably breaks libc**: overwriting the thread pointer
(riscv `tp`=x4 / aarch64 `TPIDR_EL0`) desynchronizes the C library's own TLS.
Under qemu this produced `SIGSEGV` (exit 139) in aarch64 tests that call
`snprintf`/`strtod` through `RuntimeDtoa` (B45): `nativeValueOfDoubleFloatMatchesJvmGolden`,
`aarch64NegativeFloatDoubleRuns`, `nativeCollectionPrintMatchesJvmGolden`. On
riscv the same change also regressed `crossNativeConcurrencyHelpersRun`
(`done(a)` true→false). Root cause: our `_start` is our own, but any Kof program
can still call libc (dtoa/format), so `tp` is **not** ours to repurpose.
**Resolution (deviation from the mechanism, contract unchanged):** the §129
*contract* (thread-scoped chain, worker publishes to `handle->exc`, consumer
rethrows in `await`/`await_timeout`/`select_any`) is kept exactly; only the
*mechanism* changes to a **per-TID table** `kof_exc_slots` (256 entries × 16 B
`[tid, chain]`, key `gettid`=a7 178, linear probe, same pattern as
`kof_cancel_slots`/CONC001), with a `kof_exc_slot()` helper returning
`&chain` for the current thread. This is the safer second option and does not
touch the thread pointer. Proof: the two cross tests now pass green on
riscv64/aarch64 under qemu (`KofConcurrency2Test`
`spawnWorkerThrowIsolatedFromSiblingsCrossArch` +
`spawnWorkerThrowUnhandledPropagatesCrossArch`, 138/0 in the run of 19/09).

### `roundTo`

**Decision:** arithmetic decimal rounding.

```kof
math.roundTo(value, decimals)
```

* same numeric type as the value;
* `decimals = 0` rounds to an integer;
* negative values round to tens, hundreds, etc.;
* half-away-from-zero mode;
* no locale;
* no pattern DSL;
* deterministic.

### §179 — builtin UI/media types

Declared builtin types are mapped by the resolver without breaking user shadowing.

A user class with the same name continues to win.

### `app.security()`

The mental model is inspired by Spring Security:

* fixed filter chain;
* authentication by default;
* explicit allow-list for public routes;
* CSRF by default;
* Kof-owned surface.

### §180 — float/double printing

Native must align with the observable behavior of Java's `Double.toString` and `Float.toString`:

* shortest round-trip;
* `Float` maintains its own representation;
* scientific notation according to the defined threshold;
* uppercase `E`;
* mantissa with a fractional part.

---

## D-BASELINE — toolchain baseline

**Date:** 2026-09-14

**State:** `IMPLEMENTED`

The repository build baseline moves from Java 21 to Java 25.

This changes the repository toolchain, not the minimum runtime of Kof programs.

### What changes

* `pom.xml`: `release=25`;
* CI;
* CodeQL;
* release;
* benchmark;
* Android tooling;
* embedded JDK in `package.sh`;
* build documentation.

### What does not change

* `JvmBackend` continues emitting V21 bytecode;
* Android continues with `release="21"`;
* `KofVersion.TOOLING_API=21`;
* Kof programs continue with minimum JVM runtime 21+.

---

## D-NULL — nullability and primitives

**Date:** 2026-09-15

**State:** `DECIDED`

**Revision of:** §125 / SEM048

### Historical correction

The previous decision was interpreted incorrectly.

The contract never prohibited `T?` boxed from carrying `null`.

What is prohibited is fabricating `null` at a point without null-safety.

### Contract

* `Int?`, `Boolean?`, `Double?`, and other `T?` types may carry `null`;
* `Int`, `Boolean`, `Double`, and other non-nullable types do not carry `null`;
* `null` literal at a non-nullable point is a compile-time error;
* boxed `T?` is not frozen by rule 6;
* behavior must be completed across targets in lockstep.

### State

Boxed nullable work remains in queue §241/#252/#259/#266.

The previous catalog that treated this as prohibited has been corrected.

---

## D-NULL-INTENT — explicit nullability intent

**Date:** 2026-09-15

**State:** `DECIDED`

> **Revision 19/09 (`D-TROOL`):** `Bool` left this family as a nullable
> surface — `Nullable(Bool)` is now refused with `SEM095` and the three-state
> type is `Troolean`. The rest of the contract (real boxed `T?` for
> primitives/refs, `= null` refusal, null-default of uninstantiated
> declarations) stands unchanged.

**Revision of:** option A of §125

### Contract

Null is not expected by default.

A declaration without explicit intent cannot produce or carry `null`.

Nullability intent is expressed through the comparison:

```kof
if (x == null) { ... }

if (x != null) { ... }
```

This applies to all types.

When intent exists:

* `T?` may carry real null;
* `x == null` must respond correctly;
* `println(x)` must print `null`;
* behavior must be equivalent across all targets.

The `null` literal remains prohibited at non-nullable points.

### Implementation rules

* `Int?` and other nullable primitives must use real boxed representation;
* there can be no silent folding of `null → 0`;
* there can be no silent folding of `null → false`;
* unboxing null must have defined behavior;
* uninitialized fields must have defined behavior;
* map-miss must not invent a value.

### Queue

1. JVM + Script + JS;
2. Native;
3. intent in non-nullable declarations;
4. audit and elimination of silent paths.

### Scope protection

Implementation of the intent core is under the maintainer's responsibility.

Lanes must not attack the boxed-nullable core of #266/#259 without new authorization.

**Authorization (16/09, maintainer via chat, hierarchy rule):** the maintainer
explicitly delegated the D-NULL-INTENT front to the agent lane (`192.168.100.22`,
issue-watcher/compiler) — the "new authorization" requirement above is hereby
met. The boxed-nullable core of #266/#259 is UNLOCKED for implementation by
this lane, following the D-NULL-INTENT queue (1. JVM + Script + JS; 2. Native;
3. intent in non-nullable declarations; 4. audit and elimination of silent
paths). The contract itself (explicit intent via `== null`, real boxed `T?`,
no silent folding) is unchanged. Recorded here by the lane before/with the
implementation, per the hierarchy rule (a later explicit maintainer directive
supersedes a documented restriction).

**Authorization (23/09, maintainer via chat, hierarchy rule):** the maintainer
opened queue item **2 (Native)** of this decision — the tagged-box ABI front
(`roadmap.md` §23 TIER 2.6.2, `N2`) — for implementation by the compiler lane
(`9092`). The remaining Native face is the polymorphic **`Object`-reference
print**: a record/class value typed `Object` (direct `as Object` or an
`Object`-typed local) is passed to `kof_box_to_string`, which only decodes the
MAGIC primitive box and otherwise passes the raw pointer, so `println(o)`
prints empty instead of the record's `toString` (measured 23/09: JVM
`Point[x=1, y=2]` vs Native empty — repro in `NativeObjectBoxPrintE2ETest`).
The decided contract stays: the box is `[MAGIC][tag][value]` (24 B, §3.9
`RUNTIME_ABI.md`) and no second ABI is created; the reference face dispatches
`toString` through the object's own class identity (`type_id` at offset 0, the
same discriminator `kof_instanceof` already uses). Recorded here by the lane
before/with the implementation.

Lanes may work on:

* SEM048/SEM049 audit;
* catalog of silent paths;
* fixes that do not overlap the protected implementation.

---

## D-PRINT — implicit Char conversion

**Date:** 2026-09-15

**State:** `DECIDED`

### Contract

`println` prints `Char` as a character.

```kof
println('A') // A
```

Concatenation also preserves the character:

```kof
"char: " + 'A' // char: A
```

Numeric conversion requires an explicit API.

Storing `Char` in collections remains a separate contract.

---

## D-NARROW-WHILE — flow narrowing

**Date:** 2026-09-15

**State:** `DECIDED`

Existing narrowing in `if` must be extended to:

* `while` condition;
* class-field receivers;
* scopes narrowed by null comparison.

Reassignment within the narrowed scope does not change the declaration's nullability.

The SEM012 false positive from case #159 must be eliminated.

---

## D-ENUM207 — enum identity

**Date:** 2026-09-15

**State:** `IMPLEMENTED`

Enum identity implementation was reassigned to the bugs-and-gaps lane and is **complete**: enums are real classes (`CompilerEnumLowering`), identity `==`, real `name`/`ordinal`/`values()`. The semantic change below was treated as a **contract decision** (not a local fix) and is now resolved — slice 1 made `enum == String` a **type error** (`SEM062`) on all targets; slice 2 materialized enums as real classes. Proof: `EnumIdentityE2ETest` 6/6; `known-bugs.md` §211 ✅ CLOSED 15/09; issue #207 closed.

```kof
Dir.N == "N"
```

The final semantics are recorded in this section; the behavior was changed under this decision.

---

## D-VALUE-RECORD — value records / first-class value types

**Date:** 2026-09-16

**State:** `DECIDED`

**Origin:** issue #275 (feature proposal).

### Context

Kof already has concise immutable `record` (e.g. `record Vec2(Float x, Float y)`),
but has no way to explicitly declare that a user-defined aggregate has
**value semantics and no object identity**. For small data-oriented types
(vectors, coordinates, colors, ranges, parser tokens, iterator state),
requiring a separate object allocation adds allocation pressure, GC work,
indirection and worse cache locality. Relying on JVM escape analysis does not
express intent and does not hold across the Native/JS backends.

### Decision

The suggestion is **accepted**: add an entry to the implementation queue in
`docs/development/future/` for future engineering and development of a value
form of `record` (`value record`).

### Contract

* A `value record` has the same concise immutable data-model as an existing
  Kof record, but explicitly no observable object identity.
* Equality/hash by fields (already the record contract).
* Additive and backward compatible: ordinary `record` retains its existing
  semantics; existing code keeps compiling and running (rule 2).
* Per-target ABI is an explicit scope decision before code (R7 honest scope):
  JVM → value/inline class; Native → pass-by-value (struct by value/registers);
  JS → plain frozen object.
* Boundary: core stdlib, not an official package (R1).

### Status

Planned only — **not current development**. No implementation in progress.
Lanes must not open this front without a new authorization (rule 6 / R12:
new fronts do not open before the SYSTEMS stage closes).

### Implementation

Queue entry added to `docs/development/roadmap.md` §23 (TIER 2) and this
decision recorded; engineering scheduled for the future queue.

### Relationships

* `Related:` #275 (issue)

---

## D-DEV-PRIORITY — "Em desenvolvimento" is the absolute priority of every lane

**Date:** 2026-09-16

**State:** `ACTIVE` (supersedes any per-lane preference ordering)

The maintainer's rule (16/09, chat): **total priority is completing the
`Em desenvolvimento` roadmap** — every agent, every lane, every autonomous
re-trigger picks its next task from this list, in order, before anything else
(other gaps, other queues, new fronts). Cataloguing and bug-fixing continue as
usual (the quality gate is never relaxed), but TASK SELECTION follows the
fronts below.

**The fronts (as stated by the maintainer 16/09):**

1. **Standard Library** — contracts in stabilization (the S-series:
   `PLAN-STDLIB-EXPANSION`; faces still open ride the per-item queue).
2. **GC auto-collect** — safe-points + per-frame root map.
3. **Package manager beyond MVP** — registry.
4. **Debugger beyond JVM MVP** — DAP over stdio is already on JVM; JS
   source maps line ✅; DWARF Native line ✅ partial — native
   variables/expressions and breakpoints pending + VS Code ext.
5. **KofJS — the web platform in the browser** — ES Modules via GraalJS;
   web server base ✅ (`HttpServer` + `KofJsWebQueue`); SSE handler-scoped ✅
   16/09 (`7cd69a7b`); residual per feature: ws = **WEB004**, TLS = **WEB002**,
   sse post-return push/multi-client = **WEB003**, path params/keep-alive =
   **WEB001** (canonical row: `backend-parity.md` "web on Native/JS").
6. **kof.web in Native** — residual per feature: TLS = **WEB002**, path
   params/keep-alive = **WEB001**, ws = **WEB004**, sse = **WEB003**.
7. **kof.db/orm in JS** — **DB001 CLOSED 16/09** (untyped `connect/execute/query/close/transaction` on the GraalJS-host bridge — `3e55df51`+`eb9140cb`); residual typed `db.query<T>` = `DB002` CLOSED 18/09: guest-side bind via `__kof_decode_<T>` (the host bridge has no `Class.forName` for JS classes — the wire stays untyped) + `kof.orm` = `ORM001` CLOSED 18/09: `KofJsOrmBridge` runs the same SQL as `JvmOrmRuntime` on the GraalJS host, typed records bound guest-side via `__kof_decode_<T>` (byte-parity E2E; WASM planned).

**Relation to the other rules:** this decides **order**, not **what is
acceptable** — Q0–Q7, the freeze, rule 6 and the three-states rule keep all
their force. A blocked front (rule 6, another owner `EM CURSO`, or the
`§258`-style gate) is recorded and the agent takes the NEXT front in this
list — the list is the queue, not a suggestion. `AGENTS.md` priority rule
("loose `.md` first") and the roadmap §23 tiers stay subordinate to this
decision while the `Em desenvolvimento` list has open items.

---

## D-DIAG-EN — tooling and diagnostics in English; docs stay EN+PT

**Date:** 2026-09-16

**State:** `DECIDED`

**Origin:** issue #324 (maintainer decision in the chat: "aprovo a tradução
completa para inglês"; scope confirmed 16/09: "tooling da linguagem 100% em
ingles mas as documentações precisam de ingles + pt. porem toda mensagem de
erro da linguagem precisa ser em ingles pro usuario. até pq kof é uma
plataforma universal").

### Context

The compiler emits ~144 user-visible message fragments in Portuguese across
parser, typer, lowerers and the JVM/JS/Native runtimes, and ~37 test files
assert those fragments. The internal-repr half of #324 was fixed by
`130aa213` (`Type.display`); the language half was blocked here as rule 6
until this decision.

### Contract

1. **Every user-visible message the language tooling emits is English**:
   compiler diagnostics (PARSE/SEM/… codes), runtime error strings thrown by
   the stdlib runtimes (JVM/JS/Native/interpreter), CLI/REPL/LSP output,
   build/decompile logs, and generated config templates.
2. **Documentation is a different axis and stays bilingual** — every doc
   keeps its EN + PT pair (`docs-lang.sh` invariants unchanged).
3. **Codes never change** — only the message text (SEM048 stays SEM048).
   Tests that match message *text* migrate with their unit; tests that match
   *codes* are untouched.
4. Rationale: Kof is a universal platform — the tool's language travels with
   the tool, not with the user's locale.

### Implementation

Queue opened in DOING (lane issues: translation in per-package units; files
staged/IN-PROGRESS of other agents are excluded from each unit until they
land). Docs quoting PT message texts (learn/training) sync as a follow-up
doc unit per package translated.

### Relationships

- Related: #324 (internal-repr half fixed by `130aa213`), R6 (surgical,
  never-silent diagnostics), G-01 (surface freeze — message *codes* frozen;
  *text* of this axis is set by this decision).

---

## D-DECL-RETURN — declared return type is law (#333)

**Date:** 2026-09-16

**State:** `IMPLEMENTED` (top-level/ctor) — `b1ea1718`, 19/09. See note below
for the method scope

**Origin:** issue #333 (maintainer decision in the chat, 16/09: "classe
definida como int deve obrigatoriamente retornar int"; "função definida como
int deve obrigatoriamente retornar int e o mesmo vale pras outras tipagem.
função de um tipo declarado deve retornar aquele tipo").

### Contract

1. **The declared return type is the law** in every backend: a function or
   method declared `T` must return a value assignable to `T`; a function
   declared `void` must NOT `return <value>` — that is a compile-time
   diagnostic (never silent re-typing).
2. **The silent re-inference `void→T` is removed from both paths** that
   today disagree (root cause measured in the #333 thread):
   `SemanticAnalyzer.analyzeMethodBody` (re-types the class symbol) and
   `CompilerFunctionLowering.lowerFunctionInner` (re-types only the
   definition descriptor — call-sites keep resolving `()V` → the
   `NoSuchMethodError`). Every call-site resolves against the **declared**
   type.
3. **Inference only where no type is declared** (`main()`, and unannotated
   top-level forms as today).
4. This is a **deliberate contract change** (freeze rule 1): code that
   today compiles by silently re-typing a declared `void` starts failing
   with the diagnostic above — approved by the maintainer with this record;
   CHANGELOG entry lands with the implementation commit.

### Implementation

Owner: compiler lane (issue sweep claimed in DOING, 17/09). Diagnostic
wording must follow D-DIAG-EN (English).

**As landed (`b1ea1718`, 19/09, `SEM093`) — measured on the 0.4.6 tip jar:**

- **Item 1 (void declared):** enforced for **top-level functions and
  constructors**; a **class method** declared `void` with `return <value>`
  still compiles via the §130/bug-26 both-sides re-inference (the maintainer's
  commit states it deliberately: "Metodos ficam de fora"). Face b of the #333
  thread (`b.m()` printing through the re-typed slot) therefore stays
  accepted-by-design unless the maintainer later narrows §130.
- **Item 2 (silent re-typing):** the `FunctionLowering` descriptor-only
  re-typing (the NoSuchMethodError half) is **gone for top-level**; the
  `analyzeMethodBody` symbol re-typing survives for methods with the call-sites
  resolving against the retyped symbol (consistent pair — no link crash).
- **Item 3 (no declared type):** for **top-level** the "as today" behavior was
  deliberately tightened — `f() { return 5 }` (unannotated) is now `SEM093`
  (proof: `VoidReturnValueE2ETest#untypedTopLevelWithReturnRejected`),
  because the untyped top-level was exactly the silent-NSME face. Untyped
  **methods** keep inferring (§130). The record here governs; a future
  relaxation is the maintainer's call (rule 6).
- **Item 4:** CHANGELOG entry lands in this same commit (EN+PT).

### Relationships

- Closes the rule-6 block on #333 (the fix was catalogued, waiting for
  exactly this decision).
- Related: D-NULL-INTENT (declared-vs-inferred contract family), bug 62.

---

## D-NOT-JAVA — Kof is not Java/Kotlin: a foreign-language feature request is NOT a Kof bug

**Date:** 2026-09-18

**State:** `DECIDED`

**Origin:** maintainer directive, 18/09 (issue sweep): "ele ta abrindo issue de
java no kof. kof não é java. não tem string builder no kof. responde todas e as
que não forem relativas a kof ou que ele usou treinamento errado devem ser
ignoradas e fechadas. adiciona isso como regra absoluta."

### Contract

1. A request for a construct that does not exist in Kof because it is
   **translated Java/Kotlin/C#** is **not a bug**: the compiler rejecting it is
   correct behavior. Examples measured in the sweep: `StringBuilder`,
   top-level `val`/`var`, `fun`/`val` keywords, `v is Car`, `"""triple
   quotes"""`, `Pair`, `it` implicit lambda parameter, `mutableListOf`, Elvis
   `?:`, `?.`, `0..n` ranges, `!!`, named arguments, Kotlin-style primary
   constructor with body, `catch (e: Type)`, `object`, `open`/`override`.
2. Handling: **answer once with the Kof idiom that replaces it** (the idiom
   table of `AGENTS.md` §"Idiom table") and **close the issue** as
   not-valid. Do not implement the foreign feature; do not "improve the
   diagnostic" of a correct rejection.
3. Exception (real work): Kof *claims* the construct in `training/`/`learn/`/
   docs and the compiler disagrees with its own documentation — that is a
   bug (freeze rule 4), and *how* the feature would exist is a design
   decision reserved to the maintainer (rule 6).
4. The rule is recorded as iron rule **§8 of AGENTS.md** (EN+PT) — absolute.

### Evidence

- Sweep 18/09: closed under this rule: #407 (top-level val/var), #417
  (`mutableListOf`), #418 (`Pair`), #422 (`it` + `any/all/none` chain), #424
  (`StringBuilder`), #425 (`object`), #406 (`v is Car`), #410 (`0..n`), #414
  (Elvis `?:`), #416 (`!!`), #411 + #412 (`: Type` syntax family), #419
  (destructured `for ((k,v) in map)`), #420 (`open`/`override`), #421
  (primary-ctor-with-body), #404 (`catch (e: Type)` form — real issue tracked
  as #427 where applicable), #364 (`"""triple quotes"""`), #367 (named args),
  #415 (`s[0]` on String — decided: strings are not indexable in Kof; use
  `charAt`).
- Corpus authority: `AGENTS.md` §"Fake idioms — DO NOT EXIST in Kof",
  `training/anti-patterns/fake-idioms.md`, `learn/15` (`is`/binding not
  supported → `switch`/`as`).

### Relationships

- Generalizes the precedent of the `let/const` sugar cluster (§263) and
  `Int.MAX_VALUE` (bug 99 / SEM050).
- Does NOT cover: bugs where the reproducer is valid Kof (e.g. #403, #336,
  #313 — those stay and got fixed) nor the nullable-primitive cluster
  (D-NULL-INTENT) nor unqualified-JDK-type resolution (§268/D-DECL family).
## D-UI-STYLE — declarative `style` (UI007)

**Date:** 2026-09-17

**State:** `DECIDED`

**Origin:** maintainer decision in the chat, answering the five open
questions of `KOFUI-AUDIT.md` §UI007 ("UI007 — design proposal"). The item
was `BLOQUEADO` by rule 6 (API surface); this record unblocks it.

### Context

UI007 asks for "declarative `style` (idiomatic CSS), own parser". The exact
surface is an API freeze, so it could not be implemented on the agent's
judgement. The existing `Style(Int, Int, Int, Int)` (background, foreground,
padding, radius — `kof_ui_style_new`) is already shipped on all four targets
(real only on KofJS; documented no-op elsewhere).

### Contract

1. **Surface.** `Style("<declarations>")` — one String literal argument,
   `prop: value;` pairs separated by `;`. It produces a `kof.ui.Style`
   value, consumed by `View(style)` exactly like the 4-Int form. The
   existing `Style(4 Ints)` is **untouched** (additive, backward compatible).
2. **Parse in the compiler (Q4).** The declarations are parsed and validated
   at compile time; the lowering carries the **normalized** CSS text. A
   non-literal argument is a diagnostic (no runtime parse).
3. **Colors (Q1).** Accept hex CSS (`#rgb`, `#rrggbb`, `#rrggbbaa`), the CSS
   color names and the `Palette` names (`red`, `cyan`, …) — the same table
   `Palette` uses. The compiler validates the value and keeps it in the
   normalized CSS (the browser resolves the name). The 4-Int `Style` form
   remains the way to pass a computed `Color` value (the String form takes a
   literal only).
4. **Units (Q2).** A bare integer means `px`; the suffixes `px`, `%`, `em`
   and `rem` are accepted.
5. **Properties (Q3).** A **typed whitelist**. A property outside the
   whitelist is a compile-time diagnostic (`SEM076`) — never silently
   forwarded to `node.style` (R6). A malformed declaration is `SEM077`; an
   invalid value for a known property is `SEM078`.
6. **Scope (Q5).** `setStyle(style)` — taking the `Style` value, exactly like
   `View(style)` — is available on **every DOM widget** (`KofUi.isDomWidget`),
   not only `View`, through the shared `kof_ui_widget_set_style` family (the
   UI005 pattern, same shape as `setFont(font)`).

### Invariants

- **No silent regression on the 4-Int form**: `Style(Int, Int, Int, Int)`
  keeps its exact semantics on all targets.
- **Honest per-target parity**: the declarative style is real on KofJS and
  a **no-op** on JVM/Native/Script, matching the existing `Style` gap
  (UI001). The no-op stays documented; it is not turned into a silent
  fallback.
- **Diagnostics follow D-DIAG-EN** (English message text; codes stable).
- The new codes are **additive** and do not renumber anything.

### Rejected alternatives

- **Parse at runtime** (Q4 option): rejected — "own parser" plus compile-time
  validation (Q3) requires the compiler; a runtime parse would also make the
  unknown-property error a runtime failure instead of a diagnostic.
- **`setStyle` only on `View`** (Q5 option): rejected by the maintainer in
  favour of the wider surface (every DOM widget).

### Implementation

Claimed in `DOING.md` (UI007, lane UI/style); roadmap §8 Frontend. Slice A =
compiler parser + `Style(String)` + lowering + JS runtime + tests; slice B =
`setStyle` on every DOM widget.

### Evidence

`UiStyleCssE2ETest` 10/10 (JVM + Native + Script + JS: happy path, CSS names,
units, `setStyle` on a Label, `SEM076`/`SEM077`/`SEM078`, non-literal,
4-Int non-regression) + `KofJsBrowserE2ETest` (headless Chrome, real DOM:
`declarativeStyleRendersInRealBrowserDom`,
`setStyleRendersOnAnyDomWidgetInRealBrowser`); full 4-module suite green
outside the pre-existing §252 flake and §181/§256 cross reds; `docs-lang.sh
check` 0/0/0.

### Relationships

- Closes the rule-6 block on UI007 (`KOFUI-AUDIT.md` §UI007).
 - Related: UI001 (silent no-op family), UI005 (shared `kof_ui_widget_*`
   family), D-DIAG-EN.

---


## D-UI-TOKENS — design-system tokens (Fase 10, pillar 9)

**Date:** 2026-09-18

**State:** `DECIDED`

**Origin:** maintainer scope authorisation (chat, 18/09 — the "all" answer
for the Component Core phases 8–11). The exact surface (namespace names,
member names, px values) is an API freeze (rule 6); it is locked here,
following the **D-UI-STYLE Q2** convention (a bare Int is pixels) and the
8px grid (Material/Tailwind consensus) so the tokens are predictable and
idiomatic. The *shape* (five constant namespaces) is the contract; the
specific scale values may be amended by the maintainer without changing the
API shape.

### Context

`KOFUI-AUDIT.md`/`architecture.md` §2.1 pillar 9 ("Design system — Theme +
tokens") lists the tokens `Color/Type/Spacing/Border/Radius/Elevation`.
Today only `Color` (via `Palette`/`Color`) and `Theme` exist; there are no
Spacing/Border/Radius/Elevation/Typography tokens, so layouts hard-code
literals (`padding: 16`, `border-radius: 4`) instead of naming the design
intent.

### Contract

1. **Surface.** Five constant namespaces, each a compile-time fold to a bare
   `Int` (px):

   | Namespace | Members (→ px) |
   |-----------|----------------|
   | `Spacing` | `xs`=4 `sm`=8 `md`=16 `lg`=24 `xl`=32 |
   | `Radius` | `none`=0 `sm`=2 `md`=4 `lg`=8 `full`=9999 |
   | `Border` | `hairline`=1 `thin`=2 `medium`=4 `thick`=8 |
   | `Elevation` | `none`=0 `sm`=1 `md`=2 `lg`=3 `xl`=4 |
   | `Typography` | `xs`=12 `sm`=14 `md`=16 `lg`=20 `xl`=24 `hero`=32 |

2. **Fold in the compiler (shared frontend).** A `FieldAccessExpr`
   `Namespace.member` is folded to a `KofLoadLiteral(Int)` by the same
   idiom as `Palette`. Because the fold lives in the shared frontend, all
   four targets (JVM/Native/Script/JS) carry the same constant —
   **cross-target parity by construction**.

3. **R6 — no silent 0.** An unknown member (`Spacing.huge`) and a method
   call on a namespace (`Spacing.of(4)`) are a compile-time diagnostic
   **`SEM079`** (English message, lists the valid members). The pre-existing
   `Palette.nope` silent hole is a separate, catalogued gap (it is the
   compiler lane's surface; tokens deliberately do not replicate it).

4. **Additive & backward compatible.** No existing identifier is shadowed
   (`Spacing`/`Radius`/`Border`/`Elevation`/`Typography` were unused).
   Tokens compose with the existing primitives (`Label.setFontSize(
   Typography.lg)`, `Style("padding: " + Spacing.md + …)` is the *style*
   literal form — the tokens carry the same px values).

### Invariants

- The five namespaces are **constants only** (no methods, no `var` form).
- Values are plain `Int` px (D-UI-STYLE Q2); `full`=9999 is the CSS
  "pill" idiom for fully rounded.
- Diagnostics follow D-DIAG-EN. **Amendment (18/09, cross-lane collision):**
  the codes in this decision and in D-UI-STYLE were renumbered — the compiler
  lane had already landed `SEM073` (reduce-arity, `MemberCallTyper`) and
  `SEM074` (primitive-method, `SemMethodCallTyper`) on `beta-0.4.0`. Style:
  Style: `SEM073/74/75` → **`SEM076/77/78`**; tokens: `SEM076` → **`SEM079`** (second renumber, 18/09 merge: `beta-0.4.0` took `SEM073/74` for reduce-arity/primitive-method and `SEM075` for the static-field diagnostic — this lane shifted again to stay unique).
  Only the labels moved; no semantics changed (the decisions stand as decided).
- No runtime surface is added: the fold is compile-time, so there is no
  per-target no-op to document (unlike UI001) — the value is in the IR.

### Rejected alternatives

- **A `Tokens` namespace with nested members** (`Tokens.Spacing.md`):
  rejected — an extra level of ceremony for no gain; the flat
  `Spacing.md` matches `Palette.red` and the idiom table.
- **Runtime objects / `var` tokens**: rejected — tokens are compile-time
  constants; a runtime form would add a no-op per target (UI001 family)
  for zero benefit.

### Implementation

Claimed in `DOING.md` (Fases 8–11, lane UI/style). `KofUiTokens.java`
(fold table + messages) + the six `Palette` touch points
(`SemExpressionTyper`×2, `ExpressionTyper`, `ExpressionLowerer`,
`ExpressionMethodCallLowerer`, `MemberCallTyper`).

### Evidence

`UiTokensE2ETest` 7/7 — golden table on JVM + Native + Script (same shared
fold → identical output), JS DOM (value lands in rendered text),
`SEM079` unknown member, `SEM079` method call, composition with
`Style`/widget; full 4-module suite green outside the pre-existing §252
flake and §181/§256 cross reds; `docs-lang.sh check` 0/0/0.

### Relationships

- Delivers pillar 9 of `architecture.md` §2.1 (Fase 10).
- Related: D-UI-STYLE (Q2 px convention), `Palette` (fold idiom), UI001,
  D-DIAG-EN.

---

## D-UI-APPSTATE — Fase 8: `AppState(initial)` is the application-scoped root store

**Date:** 2026-09-18

**State:** `DECIDED`

**Context:** `docs/ui/architecture.md` §2.6 defines three state scopes. The
local one (`state`/`text`/`flag` on `Component`) and the shared `Store`
(get/set/subscribe/unsubscribe) already worked; the **application root** was
the missing scope (Fase 8). While wiring it, §301 was measured and fixed
first: JS `Store.unsubscribe` was a silent no-op (wrapper-vs-raw identity),
so the "cleanup" leg of §2.6 had no working primitive — see `known-bugs.md`
§301.

**Decision (minimal contract):**
- `AppState(initial)` — one argument, returns the **app-scoped store**: a
  **create-or-get singleton** over the Store machinery. The first call
  creates with `initial`; later calls return the same handle and **ignore**
  their `initial` (documented; the value lives in the runtime, one slot per
  process).
- The returned handle is a `Store` — methods are exactly `get`/`set`/
  `subscribe`/`unsubscribe`; no new surface, no `State`/`Signal` machinery.
- Reachability is the point: components call `AppState(0)` anywhere instead
  of prop-drilling a handle.
- `storesLive()` counts the app-state slot (leak probe unchanged).
- Subscription cleanup on unmount stays **manual** (`unsubscribe(h)` — now
  real per §301): auto-attributing subscriptions to components is a bigger
  contract (which component is "current" during a subscribe?) — rule 6, not
  decided here.
- JVM/Native keep the documented Store no-ops (UI is KofJS — backend-parity);
  the JVM singleton still counts once in `storesLive()`.

**Amendable without breaking code:** the initial-value-ignored semantics and
any later extension (e.g., auto-unsub) are recorded here first; the call
shape itself is frozen.

**Evidence:** `ComponentCoreE2ETest.appStateIsCreateOrGetSingleton` +
`appStateDrivesComponentsWithoutPropDrilling` (RED pre-feature — SEM015
"Undefined function: 'AppState'"; green post-wiring); golden measured per
target (JS `10,10,x=10,x=42,,1`; JVM `0,0,"",1`; Native `0,0,"",0`);
ComponentCore 24/24 + UiE2E 29 + browser 28 + Router 4 + style/tokens 17 +
CoreRegression 102 + CompilerDriver 256 green.

- Related: D-UI-STYLE, D-UI-TOKENS, §301, D-BACKEND-SEMANTICS (no-op stores).

---

## D-UI-DIFF — Fase 9 (partial update / node reuse): **DECIDED (B) — root-kind reuse**

**Date:** 2026-09-18 · **Decision date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — option **(B) root-kind reuse patch**. Queue open in `DOING.md` (owner .17, lane kof-ui).

**Context:** `architecture.md` Phase 9 wants "partial update": reuse the DOM
node when the view re-renders the same widget at the same position. Today
re-render is rebuild+prune: §300 removed the leak, but identity is still
re-created — **measured 18/09 (embedded host, scratch probe):** a
`view (s) -> Label("v="+s)` component's root label handle is `3` after 1
state write and `7` after 5 (one fresh handle per render; old subtree
pruned, correct but new). Consequences: any reference a user kept to a
widget from a previous render is stale, and real-DOM state (input focus,
caret, scroll, CSS transitions) is lost on every state write.

**Options (not decided here — rule 6, §2.7 lifecycle/identity is frozen):**
- **(A) Full positional reconciler** (VDOM-lite): builders emit descriptors,
  a diff-by-(position, kind) patches properties in place. Biggest gain,
  biggest risk: needs a property-copy table per widget family, and makes old
  handles stay live — an identity contract change.
- **(B) Root-kind reuse patch** (first slice): when old and new root are the
  same kind, copy the value-bearing properties onto the OLD DOM node and
  discard the new node — one widget at a time, measurable, but still a
  handle-identity change at the root (aliasing decision required).
- **(C) Keep rebuild; no reuse.** Honest, simple; focus/caret loss stays a
  documented limitation (current state).

**Recommendation (lane UI/style):** (B) behind an explicit identity note —
smallest cohesive unit that fixes the user-visible pain (focus loss on the
common single-root-widget case) without a VDOM layer. The decision (which
option + the handle-continuity contract) is the maintainer's.

**Decision (maintainer, 18/09) — the identity contract of (B):** when the
previous and next render of a `view`-component produce the **same root
kind**, the OLD DOM node is kept and the value-bearing properties are copied
from the fresh node onto it; the old root handle **stays live** (identity
continuity — this is the aliasing call option B requires). Different root
kind → rebuild + prune exactly as today (§300). No VDOM layer, no keying,
no positional diff of children in this slice. Proof expected: probe shows
the same handle across state writes when kind is stable; focus-bearing
element survives the write; kind change still prunes (no regression of
§300).

**Related:** §300 (prune), §301 (unsubscribe), Fase 9 audit lines,
D-UI-APPSTATE (manual-unsub stance — now superseded by D-UI-AUTOUNSUB),
D-UI-CANCELLED.

---

## D-UI-AUTOUNSUB — Store subscriptions are component-scoped automatically: **DECIDED (A)**

**Date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — option **(A) automatic per-component scope**.

**Context:** since §301 `unsubscribe(h)` is a real primitive, but cleanup
is manual — a component that `subscribe`s on mount leaks the callback (and
its captured closure) after the component is pruned (§300's registry knows
exactly when). D-UI-APPSTATE recorded "cleanup stays manual" as the
*pre-decision* stance.

**Decision:** a `subscribe` performed **while a component is the current
render target** is bound to that component; when the component leaves the
tree (subtree prune, §300), the runtime unsubscribes it automatically.
Subscribes **outside** a component context (application-scoped, e.g. an
`AppState` observer created in `main`) keep manual semantics — the primitive
from §301 continues to work for them. Backward compatible: nothing that
compiles today changes behavior except leaked subscriptions dying with
their component.

**Related:** §300 (subtree registry), §301 (unsubscribe), D-UI-APPSTATE
(stance superseded), D-UI-DIFF (same lifecycle plumbing).

---

## D-UI-CANCELLED — `cancelled()` in async UI actions: **DECIDED (A) — origin-component scope**

**Date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — option **(A) true when the originating component
left the tree**.

**Context:** `cancelled()` inside an async action callback currently is
conservative (almost always `false`) — a late `spawn`/`http` response can
still write into a DOM subtree that §300 already pruned. Option B
(state-version based) was rejected as over-aggressive (kills legitimate
updates, heavy contract change); option C (manual handles) rejected as
ceremony.

**Decision:** the action remembers the component instance it was created
in; `cancelled()` returns `true` once that instance's node is no longer in
the live tree (same registry as §300). Async callbacks should guard the DOM
touch with `cancelled()` — when true, the response is dropped. Non-DOM side
effects are the programmer's business (unchanged).

**Related:** §300, D-UI-AUTOUNSUB (same lifecycle substrate), Fase 8
(`view`/actions), D-BACKEND-SEMANTICS (`spawn`/`await` frozen — this is UI
observability, not a concurrency contract change).

---

## D-UI-SCOPE — `kof.ui` rule updates: **DECIDED — the sole named exception to rule 5**

**Date:** 2026-09-18 (maintainer, multi-choice poll in session)

**State:** `DECIDED` — exception ratified; this record converts the verbal directive into a contract.

**Origin:** maintainer directive in chat, 18/09/2026: "regra do ui so vale pra js e
webasm" — kof.ui rule updates only apply to KofJS and Kof WebASM, not JVM/Native.
The statement arrived without a decision id (rule 6: contracts change only by
recorded decision). This record ratifies it as the maintainer's chosen form
(single named rule, recommended wording for the WASM condition).

### Context

AGENTS rule 5 makes absolute parity law ("same output on every target"). kof.ui
rule work has always been JS-only in practice: `kof_dom_patch` (§257/§300), diff
caching (D-UI-DIFF), `cancelled()` (D-UI-CANCELLED), the Router (Phase 7) — the
JVM/Native sides are no-op or degrade by design. Filing those as "parity gaps"
was miscoding intent as debt. The maintainer's statement turns practice into
law; this decision makes it a **named** exception so ledgers, the parity matrix,
and CI queries stop treating it as a bug.

### Contract

1. **`kof.ui` rule updates** — changes to the UI-language semantics (rendering
   rules, state-driven re-render, signal propagation, `when`/`each`, diffing,
   cancellation/`cancelled()`, the §300 lifecycle substrate) — are the **sole
   named exception** to rule 5. The parity duty binds **KofJS** (today) and
   **Kof WebASM** *the moment `Target.WASM` exists* (the enum is
   `JVM/NATIVE/ANDROID/JS` — measured 18/09; there is no WASM surface to break
   yet). It does **not** bind JVM/Native, ever: absence of UI rules there is the
   design, not a gap.
2. **Not** inside the exception: the rest of `kof.ui` (API signatures and what
   the parity matrix already measures row-by-row — unchanged), everything
   outside `kof.ui`, and rule 5 for core semantics and stdlib outputs. The
   exception is **prospective**: it prevents *new* parity obligations on
   JVM/Native for rule updates; it does not rewrite existing matrix rows.
3. The exception is **named and enumerable** (exactly this surface, exactly
   this target set). It is not a template: any future exception needs a new
   `D-` section ratified by the maintainer (rule 6).

### Consequences

- `docs/backend-parity.md` gets the carve-out under the Principle section, EN+PT,
  same commit as this decision.
- A UI-rule change is landed with JS (+ WebASM when it exists) tests only; do
  **not** add JVM/Native parity assertions for rule behavior, and do not open
  `known-bugs` items for their absence.
- Existing records D-UI-STYLE / D-UI-TOKENS / D-UI-DIFF / D-UI-AUTOUNSUB /
  D-UI-CANCELLED are all consistent with this carve-out (they shipped on KofJS
  only, by fact).

### Related

- AGENTS rules 5, 6, 10; `docs/backend-parity.md` §Principle.
- D-UI-DIFF, D-UI-AUTOUNSUB, D-UI-CANCELLED (the substrate this governs).
- `IMPLEMENTATION-UNIVERSAL-PLATFORM` R7 (browser honest degrade) — related but
  distinct: R7 is a runtime degrade with a diagnostic, this is a scope exclusion.

---

## D-UNIVERSAL — promotion of `IMPLEMENTATION-UNIVERSAL-PLATFORM` to current work (R12 overridden)

**Date:** 2026-09-17

**State:** `DECIDED`

**Origin:** maintainer directive in chat, 17/09/2026: "se acabaram os docs
preciso que voce assuma a frente
docs/development/future/PLAN-UNIVERSAL-PLATFORM.pt_BR.md" (original name;
renamed to `IMPLEMENTATION-UNIVERSAL-PLATFORM` in the same promotion) → answered
"Promover p/ development/ e implementar".

### Contract

1. `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` + `.pt_BR.md` **leave `future/`** and become
   current work in `docs/development/`, status **UNDER DEVELOPMENT**.
2. The promotion gate of `docs/development/README.md` §4.3 ("decision +
   SYSTEMS closed (R12)") is **overridden by this decision**: the maintainer
   authorizes the front to open with SYSTEMS still in progress.
3. The document stops being "vision only": its own header rule ("implements
   nothing, does not move files, does not open a new front") is **revoked and
   rewritten** in the same commit as the move.
4. The **first entry point is Stage 1 (SYSTEMS consolidation, §10 Estágio 1)**
   and the executable recommendations **R1–R12 (§15)** — not Tier 6+
   (AUTOMATION/INFRA/DATA/…), which keep their order in `roadmap.md` §23.
5. The vision/design of the document is **not** edited by agents: only state
   claims are synced to the real code (three-state rule), and each
   implementation unit follows Q0–Q7 like any other change.
6. Frozen core semantics remain frozen; every change is additive and per
   target (R6/R7 apply).

### Invariants

- The plan does **not** become a license to break the freeze (rule 6 of
  `AGENTS.md` still holds for operators/precedence/order of evaluation).
- `roadmap.md` §23 remains the **single ordered plan**; this document supplies
  the architecture for Tiers 6–12.
- No heavy domain (`ml`/`bio`/`hpc`) enters the base stdlib (R1).

### Rejected alternatives

- **Respect R12 and close SYSTEMS first** (do the TIER 1 items before
  promoting) — rejected by the maintainer, who chose to promote now.
- **Promote only the document without opening implementation** — rejected:
  the directive is "promover **e implementar**".

### Implementation

- Files moved: `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md` →
  `docs/development/PLAN-UNIVERSAL-PLATFORM.md` (and the `.pt_BR.md` pair;
  promotion of 17/09), then **renamed** to
  `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` when split into the executable
  tracker + the vision companion
  `docs/architecture/UNIVERSAL-PLATFORM-VISION.md`.
- Queue: `roadmap.md` §23 TIER 6–12 now points at the new path and records the
  R12 override; the first executable unit is chosen from Stage 1 / R1–R12.
- Tracking: `DOING.md` + `DOING.pt_BR.md`.

### Evidence

- Move + header rewrite + reference sync in the same commit; `docs-lang.sh
  check` 0/0/0; `check_500.sh` rc=0.

### Relationships

- `Overrides: R12` (the meta-rule "do not interrupt the present").
- `Related:` `roadmap.md` §23 (Tiers 0–12), §22 (Universal Platform),
  `AGENTS.md` §"Platform invariants".

---


## D-TRIAGE — philosophy check precedes the issue (docs-first gate)

**Date:** 2026-09-18

**State:** `DECIDED`

### Contract

A request that imports a **foreign stack** into Kof (HTML tags/CSS/`innerHTML`
into `kof.ui`, framework layers, template engines — case #449 "RawView") is
NOT a missing feature and NOT a bug: it is a philosophy violation by an author
who has not read the docs. The FIRST reply must send the author to the reading
(`docs/philosophy.md` "What Kof Is NOT", `training/idioms/<area>.md`,
`training/anti-patterns/`) with one line of WHY and the Kof idiom that covers
the real need. The issue stays open only if the need survives the reading AND
no Kof abstraction covers it — resolution then is a maintainer decision
(D-UI-* family), never the imported syntax. Rule 9 of `AGENTS.md` (generalizing
rule 8 "Kof is not Java"); the issue templates carry a required checkbox that
makes the human sign the same gate.

### Evidence

#449 closed by maintainer 18/09 (RawView with `setCss`/`setHtml`); rule in
`AGENTS.md`/`AGENTS.pt_BR.md` §"Iron rules" item 9; philosophy bullet "It is
not markup in disguise" EN+PT; `feature_request.yml`/`bug_report.yml`
checkboxes. Governance-only change (no code).

---

# 4. Rejected or superseded decisions

This section is historical. It does not define current behavior.

| ID                  | Previous decision                  | State        | Superseded by                    |
| ------------------- | ---------------------------------- | ------------ | -------------------------------- |
| §125                | `null` for primitive folds to zero | `SUPERSEDED` | D-NULL                           |
| §125                | boxed `T?` would be prohibited     | `SUPERSEDED` | D-NULL                           |
| §125                | silent `null` in return/assignment | `SUPERSEDED` | D-NULL-INTENT                    |
| C18 interim         | public reads by default            | `SUPERSEDED` | D-SEC.4                          |
| D-PLATFORM          | separate platform plan             | `CLOSED`     | D-APP + roadmap §23              |
| D-PLAT              | separate completion plan           | `CLOSED`     | roadmap §23 + Definition of Done |
| D-ASM-GATE previous | ASM inspection always mandatory    | `SUPERSEDED` | current D-ASM-GATE               |
| D-BRANCH-0.5.0      | work moves to `beta-0.5.0`         | `SUPERSEDED` | D-QUALITY-PIPELINE-2609 / D-BRANCH-PIPELINE |

---

# 5. Relationship with other documents

This file defines **the contract**.

The other documents define:

| Document                 | Responsibility                             |
| ------------------------ | ------------------------------------------ |
| `roadmap.md`             | What will be implemented and in what order |
| `DOING.md`               | What is being executed now                 |
| `AGENTS.md`              | Operational rules for agents               |
| `docs/architecture/`     | Detailed architecture                      |
| `docs/stdlib/`           | Stdlib contracts and APIs                  |
| `docs/backend-parity.md` | Parity matrix and gaps                     |
| `docs/bugs-and-gaps/`    | Bugs, gaps, and regressions                |
| `training/`              | Learning material and corpus               |
| `CHANGELOG`              | Release history                            |

### Precedence rule

In case of conflict:

1. current decision in this file;
2. specific normative documentation;
3. conformance tests;
4. current implementation;
5. chat history.

Code and tests that contradict a current decision indicate a divergence to be corrected, not an automatic new decision.

---

# 6. How to record a new decision

Use this format:

```markdown
**## D-XXXX — title**

**Date:** YYYY-MM-DD

**State:** DECIDED | IN_PROGRESS | IMPLEMENTED | PARTIAL |
BLOCKED | SUPERSEDED | REJECTED | CLOSED

**Scope:** ...

**### Context**

Why the decision was necessary.

**### Decision**

Approved contract, without unnecessary implementation details.

**### Invariants**

What must not be broken.

**### Rejected alternatives**

Only when necessary to preserve the reasoning.

**### Implementation**

Reference to the roadmap or DOING.

**### Evidence**

Tests, commits, matrix, or documentation.

**### Relationships**

- `Supersedes: ...`

- `Depends on: ...`

- `Related: ...`
```

---

# 7. Final rule

**Decisions are permanent until superseded. Implementations are revisable.**

Code may change.

Tests may change.

The roadmap may change.

The contract changes only through a recorded decision.

---

## D-POLL-19 — every pending decision resolved (multiple-choice poll, maintainer 19/09)

**Date:** 2026-09-19 · **State:** `DECIDED` (batch) · **Answers (maintainer):**
D1-A · D2-A · D3-A · D4-A · D5-B · D6-A · D7-A · #401 = "BUG REAL — `List<Int>` is
different from `List<String>`" (= option A, compile-time rejection) · X8-A · LSP-A
(rename cross-file) · LSP-A (hover signatures via `StdCatalog`).

| # | Decision (option) | Unblocks / queue |
|---|---|---|
| D1 (A) | GC x86 auto-collect re-baseline **approved now** | 1.2.2/1.2.3 proceed; Stage 6 gate open — execution = native lane |
| D2 (A) | registry MVP = **local + GitHub Releases as official host** (publish = Release with artifact + SHA256SUMS) | 1.5.3 ⛔→open; `kof deploy --publish` face (docs→platform lane); 8.2 package manager |
| D3 (A) | bare-metal/bootable **design plan authorized** | 1.7: plan doc in `docs/development/` (native lane drafts, maintainer reviews) |
| D4 (A) | **conservative default**: every namespace is born `experimental`; promotion per-namespace with the R5 DoD | R5; `docs/backend-parity.md` §Stability (default line added 19/09); **machine-gated 21/09** — tier in `scripts/stdlib_boundary.txt` + `scripts/check_stdlib_boundary.sh` (denies missing/invalid tier and unpinned `stable`) |
| D5 (B) | **no new syntax** — scoped resources = `close()` + `try/finally`; `using` is OFF | 6.5 ships as pattern, not grammar; `future/scoped-resources` stays design-only |
| D6 (A) | R3 struct/array ABI: **written spec first, review, then code** | spec doc `docs/ffi-abi-structs.md` (drafted by docs→platform lane 19/09, design-only); implementation = compiler lane |
| D7 (A) | value records (TIER 2.7) **front opened now** | roadmap §23 2.7.1+ queue active — compiler lane (needs coordination with Cluster A) |
| #401 | **real bug**: `List<Int>` assigned as `List<String>` must be REJECTED at compile time | §270/§271 Cluster A (`.22`) executes; freeze rule 1 satisfied — code that compiles today fails at runtime, so tightening matches the documented contract |
| X8 (A) | `kof.test` runner implemented **exactly as roadmap §G6 specifies** | X8 slice 3 — docs→platform lane |
| LSP-A (rename) | **cross-file rename via WorkspaceEdit** on the same textual convention as `references` ("first hit" honesty documented) | X10-continuation — docs→platform lane |
| LSP-A (signatures) | hover signatures: **`StdCatalog` extended with signatures extracted from the typer** (single source) | X10-continuation — docs→platform lane |

**Evidence:** maintainer's message 19/09 ~03:5x (-03), one-line multi-choice
answers; ratification commit updates the D-table in
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (+PT), `roadmap.md` §23 (D7),
`backend-parity.md` (D4) and `known-bugs.md` §270 (#401).

---

## D-TROOL — `Bool` is never nullable; the three-valued type is `Troolean` (maintainer 19/09)

**Date:** 2026-09-19 · **State:** `DECIDED` · **Revision of:** the `Nullable(Bool)`
face of D-NULL-INTENT (the boxed-nullable machinery stays; `Bool` stops using it
as surface syntax) · **Queue:** new front under §23 Tier 2.6 (null-intent family).

### Contract (maintainer's words, 19/09 ~12:0x -03)

1. A nullable variable declared **without instantiation** already has `null` as
   its value — by default, no ceremony. *(already the measured behavior of
   `String? s`, `Int? q`, `Bool? b`: JVM/Script/JS print `null` / `== null` is
   true — this clause CONFIRMS the current D-NULL-INTENT default and freezes it.)*
2. Assigning `null` to a nullable through code (`x = null`) **remains refused**
   (SEM048 unchanged). `null` reaches a variable only through (a) the default of
   the non-instantiated declaration or (b) an API/function returning `null`.
3. **Primitive values are not interfered with**: `Int n` keeps its `0` default —
   only `T?` declarations carry null.
4. **`Bool` cannot be nullable**: it has exactly two values. `Bool?` (and every
   `Nullable(Bool)` written by the user) becomes a **compile-time refusal,
   `SEM095`** with the message pointing at the replacement. (SEM094 is reserved
   for the switch-return gate shipped by the bot's PR #481 — if that one ever
   re-lands first, the codes swap and this entry updates.)
5. For `true / false / null` the language gains **`Troolean`** — a 3-state type
   with its logic (Kleene): `!`, `&&`, `||` follow the truth tables
   (`NOT U=U`; `AND`: F dominates, U second; `OR`: T dominates, U second),
   comparison against `true`/`false` and the intent-check `== null` work,
   un-declared instance = `null`(unknown), functions may `return null` into a
   `Troolean`. `println` shows `true`/`false`/`null` (no 0/1 — §306(b) canonical
   already does this for the boxed Boolean).

### Rationale and scope notes

- The clause pair (1)(2) makes `Bool? b = null` illegal but `Bool? b` (uninstantiated)
  legal — for `Bool` specifically, clause (4) removes the whole surface: there is
  no way to hold the unknown state in a `Bool?` anymore, which is exactly the
  confused family behind #462 (value-context VerifyError) and #486 (JVM
  VerifyError + JS `null` leak on `&&`/`||` over `Bool?`). Both issues are
  CLOSED by this decision: the reproducer becomes a `SEM095` diagnostic and the
  idiom is `Troolean` (rule 8 close: the foreign construct's replacement is now
  IN the language).
- Internal representation decision (lane, no new runtime class needed on
  JVM/Script/JS): `Troolean` is a **nominal type in the front end** (type name
  registered; `Type.PrimitiveType` "troolean" sort) that lowers per backend using
  the machinery the box already has — JVM/Script/JS reuse the boxed `Boolean`
  slot of §295/§306 (the writer/reader cluster already fixed them), the
  three-valued operators desugar in the front end into Kleene tables over the
  existing comparison machinery; **Native** maps to a 3-state `byte` (0=F,1=T,2=U) —
  if a Native path cannot honor a face, the honest R6 diagnostic `NAT-TROOL001`
  replaces any silent fallback (freeze rule 5: parity or diagnosed gap).
- Frozen-semantics audit (rule 1/6): this TIGHTENS (a construct that compiled
  now gets a diagnostic — same class of change the maintainer approved for #401:
  code that compiles today dies at runtime, so refusing it at compile time
  matches the documented contract). `Bool` non-nullability is consistent with
  §306's own truth (the JVM reader faces of `Bool?` were crash-faces; #462/#486).
  Version note + migration entry go in CHANGELOG (0.4.0 line).

### Queue (roadmap §23 Tier 2.6, D-TROOL)

1. Front end: register `Troolean`; refuse `Bool?`/`Nullable(Bool)` written by
   the user with `SEM095` (message: "`Bool` has two values; for
   true/false/unknown use `Troolean`"); tests: `TrooleanLawE2ETest`
   (SEM095 face + declaration default + `return null` narrowing).
2. Operators: Kleene `!`, `&&`, `||`, `==` on `Troolean`, `runAll3` (JVM+Script+JS),
   edges: all nine AND/OR combinations + NOT, nested chains, condition position.
3. Native: 3-state byte + `NAT-TROOL001` honest gap where a face cannot land.
4. Migration of the 4 test files that write `Bool?` (ConformanceMatrix,
   NullablePrimitiveContract, NullableBoolTruthiness, KofInterpreterParity) +
   corpus: `training/language/types.md`, nullability idiom docs,
   `fake-idioms.md` (add the `Bool?` row → Troolean), DECISIONS D-NULL-INTENT
   revision note, CHANGELOG migration entry, `backend-parity.md` matrix cell.

**Evidence:** maintainer's message 19/09 ~12:0x (-03), two clauses (the
definition + "implement and close the related issues"). Lane picks: the
implementation is front-end-centric and the boxed-nullable machinery is already
built (`.22` shipped §295/§306 18–19/09); coordination claimed in `DOING.md`
same commit (the `SemExpressionTyper`/`ExpressionLowerer` files are the same
`.22` touched today — they hold NO other unclaimed `Bool?`-face work after
#462/#486 are closed here).

---

## D-KOF-FIRST — internal contract before external comparison (`KOF-first, external-second`)

**Date:** 2026-09-19 · **State:** `DECIDED` (ratified 19/09/2026 — flip from `PROPOSED` executed in the PR-EXTERNA lane session, 19/09; the rule text is unchanged from the maintainer's proposal `KOF_FIRST_CONTRACT_RULE.md`. From here it is a ratified contract, no longer only a working rule) · **Scope:** issue/PR triage, bug hunting, gap classification, use of external references · **Related:** `D-NOT-JAVA` (rule 8), `D-TRIAGE` (rule 9), the precedence rule of §5

### Context

The risk is not a wrong issue; it is the language evolving by accident. A
foreign expectation enters as a "bug", gets a plausible patch, a test freezes
the new behavior, the documentation starts teaching it — and the Kof surface
has grown without a decision. The repository already carries the pieces of the
answer (rule 8 "Kof is not Java", rule 9 "philosophy check precedes the
issue", `D-NOT-JAVA`, `D-TRIAGE`, and the precedence rule that puts
`DECISIONS.md` above implementation) and, at the same time, the measured cases
that motivated this rule: #410 (`0..n` as a range), #416 (`!!`), #407
(top-level `val`/`var`), #424 (`StringBuilder`), #415 (`String[i]`), #449
(`RawView`), #483 (`name() -> Type`), #492/PR #496 (`(Int x) -> x * x`).

### Contract

1. **No external result is an oracle.** A language, specification, forum,
   benchmark, paper or runtime does not, by itself, define Kof's expected
   behavior.
2. **The reproducer must be valid Kof.** Before opening or validating an
   issue, prove the snippet uses grammar and syntax Kof recognizes.
3. **Kof's contract comes before the implementation.** Identify the decision,
   normative documentation, conformance test or applicable rule *before*
   classifying the observed behavior.
4. **The Kof idiom is searched before the foreign feature.** If the need is
   already met by an existing Kof abstraction, rejecting foreign syntax is not
   a bug.
5. **Internal divergence precedes external comparison.** A bug is demonstrated
   as a divergence between Kof and its own contract, or between targets
   governed by the same contract.
6. **A gap must be proved.** There is a gap only when the legitimate need
   remains with no satisfactory solution inside current Kof.
7. **External research begins only after the gap.** Once the internal problem
   is proved, other languages and the literature may be studied.
8. **External references supply principles, not surface.** Extract
   invariants, techniques, formal models, known failures, trade-offs.
9. **Every external solution is translated back into Kof.** Name, syntax, API,
   semantics and ergonomics are evaluated against Kof's philosophy, decisions,
   targets and abstractions.
10. **A contract change is a decision, not a bugfix.** A proposal that changes
    grammar, semantics, operators, the type model or a frozen API requires an
    explicit maintainer decision (rule 6).

### Classification (Gate 4 — nothing gets a production patch without one)

| Category | Exists when |
|---|---|
| `BUG REAL` | valid Kof program + Kof contract defines the behavior + implementation differs |
| `TARGET DIVERGENCE` | the same valid Kof construct behaves differently across targets with no honest documented gap |
| `GAP REAL` | legitimate need + no adequate Kof syntax/idiom/stdlib/composition + no decision rejecting it |
| `DESIGN REQUEST` | intent is to change, extend or replace a surface/semantics decision |
| `NOT-VALID` | the reproducer depends on a construct that is not Kof and a Kof idiom covers the intent |
| `CONTRACT AMBIGUITY` | docs, decisions, tests and implementation do not settle which behavior is normative → evidence + alternatives + maintainer decision, never an automatic fix |

### Gates (the pipeline, in order)

- **Gate 0 — is the reproducer Kof?** Check `docs/language-reference/`
  (grammar, syntax, types), the feature's own doc, `training/`, `learn/`,
  `training/anti-patterns/fake-idioms.md`, this file. Not Kof → no bug is
  demonstrated; go to Gate 1.
- **Gate 1 — intent and idiom.** Never stop at "this syntax does not exist":
  name the real intent and the Kof idiom that expresses it. Idiom resolves →
  `NOT-VALID`.
- **Gate 2 — governing contract.** `DECISIONS.md` → normative docs →
  conformance/golden → parity matrix → implementation; chat history only as
  auxiliary evidence. Record `contract source` / `contract statement` /
  `expected Kof behavior`.
- **Gate 3 — internal measurement.** Run the **valid Kof** reproducer on the
  relevant targets (JVM / Script / JS / Native x86 / Native riscv64-aarch64
  when applicable).
- **Gate 4 — classification.** One of the six categories above.
- **Gate 5 — proof of the gap.** For `GAP REAL`, answer *no* to all: valid
  Kof syntax exists? documented idiom exists? stdlib/API exists? composition
  of Kof resources solves it reasonably? a decision consciously rejects that
  surface? already catalogued gap?
- **Gate 6 — external research.** Now, and only now.
- **Gate 7 — translation back to Kof** (what internal problem it solves,
  which principle is reusable, what is specific to the source language,
  conflict with any Kof decision, new syntax/API, accidental complexity,
  parity, honest gap on some target, expressible with existing mechanisms).
- **Gate 8 — decision.** Contract change → comparative proposal, trade-offs,
  migration and per-target impact, technical recommendation **without
  self-ratification**, maintainer's decision.
- **Gate 9 — implementation and proof.** RED reproducing the contract → root
  cause fix → GREEN → cross-target conformance → golden/migration → docs and
  CHANGELOG.

### Blocked without a decision

An automatic production PR is appropriate **only** for a confirmed `BUG REAL`,
a confirmed `TARGET DIVERGENCE`, or the implementation of an already ratified
decision. It is blocked while the issue is `CONTRACT AMBIGUITY`,
`DESIGN REQUEST` or an unratified `GAP`. Any parser/lexer diff that introduces
a newly accepted form must answer *which decision authorizes this new
surface* — with no decision, `STOP`.

### Evidence block (issues and PRs)

Issues and bug-hunter reports carry `KOF VALIDITY` (grammar source,
syntax/documentation source, reproducer validated as Kof), `CONTRACT`
(decision/source, expected behavior), `MEASUREMENT` (targets, actual
behavior), `CLASSIFICATION` and `DUPLICATE CHECK`. If `KOF VALIDITY` cannot be
proved, no issue is opened automatically. PRs carry the contract source, the
valid Kof reproducer, the RED before the production change, the root cause,
the fix, why it does or does not change the Kof contract, the regression
proof and the cross-target impact.

### Evidence

Maintainer-facing proposal `KOF_FIRST_CONTRACT_RULE.md` (19/09); rules 8 and 9
of `AGENTS.md`/`AGENTS.pt_BR.md`; `D-NOT-JAVA`, `D-TRIAGE`, precedence rule of
§5; measured cases #407, #410, #415, #416, #424, #449, #483, #492/#496.
Governance-only change: no code, no semantics, no surface touched.
## D-SCHED-DURATION — idiomatic duration expressions in `scheduler.at`

**Date:** 2026-09-19 · **State:** `DECIDED` (maintainer directive in chat:
"coloca pra aceitar expressões idiomáticas também. scheduler.at(30m) por
exemplo, pode ter s, m, h, d, M, a" + "e aceitar expressões compostas
(1d&30m) por exemplo")

**Decision (additive, freeze rule 2):** the first argument of
`scheduler.at(expr, fn)` accepts, BESIDES the 5-field UTC cron (unchanged),
an **idiomatic duration expression**:

* `term := digits unit`, `unit ∈ { s, ms, m, h, d, M, a }` — `s` seconds, `ms` milliseconds,
  `m` minutes, `h` hours, `d` days (fixed, in ms); `M` months and `a` years
  advance the UTC CALENDAR (month/year boundary, clamped to the target
  month's last day — `2024-01-31` + `1M` = `2024-02-29`);
* composition with **`&`** (e.g. `1d&30m`, `1M&15m`): the fixed terms (s/m/h/d)
  sum in ms and are applied as an OFFSET after the calendar advance;
* semantics: first fire after the interval counted from now, then repeatedly
  (fixed interval, or the calendar-advanced next instant for M/a — anchor
  advances from the previous fire, never from `now`, no drift);
* a string that is NOT a duration keeps the cron path (same 5-field parser,
  same errors); a MALFORMED duration (unknown unit, zero/negative term,
  empty term) throws with a clear message — never silent (R6);
* targets: JVM + JS (same algorithm, byte-parity golden via probe); Native
  keeps the existing honest compile-time `CRON001` refusal (the gap already
  covers the whole `at` surface).

**Evidence:** maintainer's messages 19/09 (this session, lane .18). First
consumer: `flow.schedule(cron)` of the `kof.workflow` 2.1.3 bundle (same
honest gap on Native).

## D-WORKFLOW-RUN — `kof workflow run` is a full introspection runner over `pipeline()`

**Date:** 2026-09-19 · **State:** `DECIDED` (maintainer, chat poll this
session: chose **full runner (introspection)** for row 2.6 and **real
pipeline example + E2E proof** for row 2.5)

**Decision (additive, freeze rule 2):** row 2.6 of
`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` ships as a **full runner**, not an
alias over `kof run`:

* a **pipeline file** is a `.kf` module that imports `kof.workflow` and
  defines a top-level `pipeline(): KofWfDag`; it carries no `main()` (the
  runner synthesizes the entry). This is the only new convention; nothing
  that exists today changes (`kof run` keeps running any `.kf` unchanged).
* `kof workflow list <file.kf>` — lists jobs and their dependencies (the
  DAG); `--json` for a machine-readable form;
* `kof workflow run <file.kf>` — runs the dag; `--job <name>` restricts to
  the named job **and its transitive dependencies**; `--dry-run` prints the
  topological order without executing any body; `--json` emits the
  structured `Report`;
* exit code: `0` iff `Report.allOk()`, else `1` (same honesty as
  `kof test`);
* targets: JVM first (R7); JS and the remaining targets are follow-up
  slices with the same honest gap when a job body needs a primitive the
  target lacks;
* the runner is **tooling**, the pipeline is **Kof code** (VISION §4.3);
  `kof workflow run` consumes the same frontend — no parallel parser.

**Evidence:** maintainer poll this session (options: thin alias / minimal
`pipeline()` convention / **full introspection** / plan-only). VISION
`UNIVERSAL-PLATFORM-VISION.md:1137`; `workflow-plan.md` (2.1 signed 19/09);
X9 `kof deploy` precedent for tooling slices.

## D-MAKEALIVE — Kof Makealive (Stage 3): namespace, providers, state, surface

**Date:** 2026-09-20 · **State:** `DECIDED` (maintainer, chat poll this
session — answers to the Q1–Q4 of `makealive-plan.md` §6)

**Decision (additive, freeze rule 2):**

* **Q1 — namespace = `kof.makealive`** (option A). The tracker literal
  `kof.infra` is HARD-DENY by the R1 machine gate (measured:
  `check_stdlib_boundary.sh` rc=1, "official package only"; plan §2.1) — the
  name is the decided domain (VISION §4.2). Ships as a pure-Kof host of a
  virtual namespace (workflow/supervisor pattern, DD-OTP-01 A) + a
  `platform` line in `scripts/stdlib_boundary.txt`.
* **Q2 — v1 providers = the COMPLETE generic surface**: local-FS (idempotency
  measurable end-to-end with zero cloud credentials) + REST via `kof.http` +
  CLI via `kof.shell` — all as interop (R9). Concrete clouds stay official
  packages (`infra-<cloud>`, R1 — never a compiler literal).
* **Q3 — state = `kof.db` from day one** (maintainer chose the
  non-recommended option over JSON/kof.io). Consequence: wherever kof.db is
  gated, the Makealive state is gated with it (Native = the honest
  `DB001`/`ORM001` gaps until the DB-gap front closes them — see
  D-KOF-AS-CLOUD: closing those gaps is on the Kof-as-cloud path, not a
  permanent degrade).
* **Q4 — flat injected host, English surface**: `resource`/`requires`/
  `plan`/`apply`/`destroy` (no `makealive.` prefix; parity with
  `job`/`dag`/`run`). The 3.1 golden freezes these names.

**Evidence:** maintainer chat poll 19/09–20/09 (answers: A / "completo" /
"kof.db desde o dia 1" / "confirmar flat + inglês"). R1 collision measured
19/09 (plan §2.1). **Unblocks tracker 3.1** (owner .18): next = 3.1 core
host + `MakealiveE2ETest`; 3.2/3.7 were ⛔ R4 — **R4 ✅ landed 21/09** (3.2 remains
a rule-6 surface decision; 3.7 depends on it); 3.8 (the `kof infra` CLI
contract) stays an open question (rule 6). **UPDATE 21/09:** resolved by
**`D-MAKEALIVE-SYNTAX`** (below) — **3.2 DECIDED** (pure sugar over `design()`),
which **unblocks 3.7**; **3.8 reaffirmed** (`kof makealive` only, `kof infra` not added).

## D-KOF-AS-CLOUD — Kof must be ready to BE the cloud itself

**Date:** 2026-09-20 · **State:** `DECIDED` (strategic direction — not a
work order)

**Decision:** the endgame of the universal platform is Kof **hosting Kof**:
the language provisions the infra it runs on (Makealive, Stage 3), runs on
it (Native bare-metal/bootable, 1.7 + D3), and self-hosts its own packages
(registry 1.5.3 + D2). Concrete consequence for the queue: cross-target
DB/ORM parity (the `DB001`/`ORM001` faces — Native and Android) is no
longer "honest gap, forever" — it is a **path item** for Kof-as-cloud: a
platform that runs on its own provisioned infra needs its own state layer on
every target it provisions. This does NOT override the stage order (R12),
the freeze, or the quality gate — it re-prioritizes the DB-gap front within
the existing lanes.

**Evidence:** maintainer message 20/09 ("kof tem que estar pronto pra ser a
própria nuvem depois"), immediately after choosing kof.db-from-day-1 for the
Makealive state (D-MAKEALIVE Q3).

## D-BOOTSTRAP — final objective: the bootstrapper — Kof written in Kof

**Date:** 2026-09-20 · **State:** `DECIDED` (maintainer — the FINAL
OBJECTIVE of the project; reached as the last stage of the platform;
extends D-KOF-AS-CLOUD)

**Decision:** the **final objective** (north star) of Kof is the
**bootstrapper**: the Kof compiler **written in Kof** (`kof feito em kof`).
The Java implementation is the bootstrap that produces the self-hosting
implementation; after it, the toolchain runs on the language itself, and the
"Kof as its own cloud" direction closes end-to-end: the compiler compiles
itself, provisions its infra (Makealive), runs on that infra (Native/
bootable) and self-hosts its packages (registry).

Consequences for the queue (scheduling only — R12 still governs execution):

* a **design plan** is authorized (D3 bare-metal precedent: plan drafted,
  maintainer reviews, execution waits the stage order) — **BS-1 (poll
  20/09): DECIDED**, drafting owner = lane `.18`;
* this decision does NOT open the bootstrapper front before the existing
  stages close — only the *planning* is pulled forward (the D-UNIVERSAL
  override pattern);
* the Java core stays the reference (frozen semantics); the bootstrap
  compiler is proven **byte-for-byte against the same golden E2E corpus**
  (the corpus is the oracle — Q0–Q7 apply to the bootstrap too, zero
  hallucination);
* it is a **platform** goal, not a domain: no language feature is justified
  "for the bootstrapper"; any escape hatch the bootstrap needs is a design
  decision (rule 6), never a silent addition.

**Evidence:** maintainer message 20/09 ("o final stage de tudo é
bootstrapper. kof feito em kof").

## D-DB-GAPS — the DB/ORM orphan gaps: route per target (DECIDED)

**Date:** 2026-09-20 · **State:** `DECIDED` (maintainer poll 20/09; owner
lane `.18` — the previous owner died without a successor: "não tem ninguém
nas gaps de db. agente morto")

**Decision (three gaps, one route):**

* **DB-1 — `ORM001` on Native (row 1.1.9):** option **A**. The ORM lands as
  real `kof_orm_*` asm **over the existing native `kof_db_*` surface** (the
  same stack the JVM uses: `JvmOrmRuntime` → JDBC → `kof_db_*`); JS keeps
  `KofJsOrmBridge`, JVM keeps the host-side runtime — Native closes last,
  per R7. No JVM-embedding shortcut, no silent fallback (R6).
* **DB-2 — Android refuses `kof.db` (§278, row 1.1.10):** **implement
  correctly — Android IS the JVM**, so it must have the **same behavior as
  the JVM**. **IMPLEMENTED 20/09**: `KofDb`/`KofOrm` `supportedOn` include
  `ANDROID`; pinned by `KofDbE2ETest.androidDbEmitsTheSameBytecodeAsJvm`
  (byte-identical `Main.class`) and the flipped
  `DomainGapCodesTest.androidCompilesDbLikeJvmAndRefusesCryptoWithTheDocumentedCode`;
  §278 is now PARTIAL (SECN/GPU open). The `DB001` refusal on the Android target is lifted; the
  `DomainGapCodesTest.androidRefusesDbAndCryptoWithTheDocumentedCodes` pin
  flips to parity for the DB face. `SECN00x`/`GPU001` refusals stay honest
  until those stacks themselves run on Android (different lanes; same
  principle already recorded here — rule 6 signature: maintainer 20/09,
  "implementa corretamente. android é jvm, logo androids tem que ter o
  mesmo comportamento que jvm").
* **DB-3 — MySQL on riscv64/aarch64:** option **B** — **extend** the MySQL
  surface to the cross targets (no degradation allowed; the x86 reference
  defines the contract).

Sequencing note (MK-1, same poll): makealive **fatia 3.1 is "completo" in
one slice** — core + local-FS/REST/CLI providers + `kof.db` state surface,
not a core-only fragment (the `D-MAKEALIVE` Q3 store decision applies from
the first apply). The DB gaps are the **prerequisite front** of that full
3.1 on Native; the slice stays honest on JVM/JS meanwhile (those targets
already have real `kof.db`).

**Evidence:** maintainer answers 20/09 — DB-1 "A) kof_orm_* em asm sobre
os kof_db_* existentes", DB-2 "implementa corretamente… android é jvm",
DB-3 "B) estender MySQL p/ riscv/aarch", MK-1 "B) completo de uma vez".

### D-DB-GAPS addendum (09/21/2026, maintainer) — TOTAL DB parity across all targets

Poll (chat 21/09, during the §421 triage): asked which native gap-code to use
for the silent acceptance of unsupported schemes, the maintainer answered
**full parity — every target must ACCEPT `mariadb`, `mysql`, `sqlite`,
`mongodb`, … (no gap-code endpoint)**. This generalizes DB-3: the DB surface
reaches the *same scheme set* on JVM/Android/JS/Native, each scheme **real**
(R6). An unsupported scheme is a **declared interim gap** only while its slice
lands — never a permanent refusal, never a silent accept.

**Measured state (21/09, this lane — measurement, not memory):**
- **JVM/Android/JS:** JDBC via the host — any JDBC URL whose driver is on the
  classpath (h2, sqlite-jdbc, mysql, mariadb, postgres); the JS delegate IS the
  host's JDBC. MongoDB is **not** JDBC (separate protocol).
- **Native (x86-64/riscv64/aarch64):** `sqlite:` (libsqlite3, link-by-use) +
  `mysql://` (wire protocol in `RuntimeDb2.java`) are real; `mariadb://`
  (mysql-wire compatible) and `mongodb://`/`oracle://` are **not** parsed —
  `kof_db_connect` registers a type-0 handle and the failure surfaces late at
  `.Lorm_conn` (**§421**).
- `kof_db_type` already reserves **1=sqlite 2=mysql 3=oracle 4=mongo** → the
  type model anticipates this front.

**Slices (queue opened in `docs/stdlib/db-parity-plan.md`):** S0 interim
honest diagnostic (clears §421's silent accept while the schemes land); S1
`mariadb://` = mysql-wire alias (Native, 3 arches); S2 JDBC scheme parity
JVM/JS/Android (per-driver measured, honest missing-driver diagnostic); S3
`mongodb://` interop-first (driver/wire — never a home-grown server, R9); S4
oracle (same). Ownership: DB/ORM front (owner to be named) + this lane for the
plan/records. **Not a frozen-surface change** — it widens accepted URLs; the
`kof.db`/`kof.orm` API is unchanged.

## D-BRANCH-0.5.0 — work moves to `beta-0.5.0`; `beta-0.4.0` stays for in-flight landings + release prep (09/20/2026, maintainer order)

**State:** `SUPERSEDED` (28/09/2026) by `D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE` — the branch pipeline `lab → testing → prerelease → stable → release/x.y.z → tag` replaced "work moves to `beta-0.5.0`" at the 0.5.0 cutover; `beta-*` is frozen. History preserved below (never deleted, §1.3).

**Order (chat 09/20/2026):** "avise os outros agentes, vamos mover todo trabalho
pra branch beta-0.5.0 e começar a preparar a nova release".

**Decided:**
- New active branch: `beta-0.5.0`, cut from the tip of `beta-0.4.0`. All new
  commits (code and docs, every lane) land there.
- `beta-0.4.0` still receives work already in flight (e.g. §374/#553 WIP of
  `.22`); every landing there is fast-forwarded into `beta-0.5.0` by the docs
  lane, so the two branches never diverge in content.
- Version bump (`pom.xml` `<revision>0.4.7-beta</revision>` → new release
  number), CHANGELOG cut and tag are **release-prep items** — the maintainer
  confirms the number at the cut; no unilateral bump by an agent.
- Release-prep queue lived in
  `docs/distribution/release-beta-0.5.0.md` (+ `.pt_BR.md`) — CLOSED 28/09 (moved out of `development/`).

**Evidence:** maintainer order 09/20/2026 (chat); `AGENTS.md`/`AGENTS.pt_BR.md`
active-branch lines and this record land in the same pass; open issues
#550/#553/#554/#555 notified by comment; `DOING.md` banner for all lanes.

## D-RELEASE-1.0 — KOF 1.0 EXIT GATE: contract stabilization is the development meta; no RC/release 1.0 with any item unmet or any edge open (09/20/2026, maintainer ratification)

**Order (chat 09/20/2026):** "decisão de `docs/development/future/PROPOSAL-1.0-EXIT-GATE.md`
ratificada. concordo com o planejamento. setar como meta de desenvolvimento a
estabilização dos contratos seguindo o planejamento existente nessa issue. kof
RC 1.0.0 e kof release 1.0.0 só existem QUANDO todos os pontos estiverem
correspondentes e não houver nenhuma aresta aberta".

**Decided:**
- The proposal becomes the normative contract, recorded here as
  `D-RELEASE-1.0`. The document was promoted from `future/` to
  `docs/PROPOSAL-1.0-EXIT-GATE.md` (+`.pt_BR.md`), its §22
  approval block filled as the mechanical record of this chat approval (her
  words quoted as evidence), and the header status switched to RATIFIED.
- **The EXIT GATE (§8 + the D-BRANCH-0.5.0 complement) is binding**: a build
  may be declared Kof RC 1.0.0 only with every mandatory item satisfied by
  reproducible evidence on the same candidate, and RC may become Stable 1.0.0
  only with the gate still green and no RC→Stable regression. No cut, tag or
  publication of "1.0" exists while ANY item is unmet or ANY edge is open —
  this is the definition of "all points matching and no open edge", and it is
  the responsibility of every lane, not of a release-day ceremony.
- **Development meta (immediate)**: contract stabilization per the §23 queue —
  define `release-blocker` mechanically (§11 four-category classification of
  every open issue), implement the machine gate with RED tests written before
  any gate logic (§10 trust criteria: same-SHA verdict, no stale analysis, no
  known false-green, end of routine `CODEQL_GATE_SKIP`), validate
  BEFORE/AFTER, test the real package outside the repo, run the final target
  matrix. Only then may the first RC candidate exist. Queue opened in
  `docs/development/roadmap.md` §23 and claimed in `DOING.md`.
- **Q1 (when the 1.0 line starts)**: as proposed — when Mel explicitly declares
  the 1.0 line/candidate open (gate complement item); nothing before that.
- **Q2/Q7 (surface — STILL OPEN, these are the first "arestas" to close)**: the
  ratification approves the contract and the plan; it does not fabricate
  answers she did not give. KofC and Android inside the Stable 1.0 surface, and
  the §35 `[? MEL]` reinforcement candidates, remain maintainer decisions that
  block only the first RC — a question not answered is an edge not closed.
  Meanwhile the site/README MUST stop implying a decision that does not exist
  (site marks KofC "Disponível" today — docs/site sync is a queue item).
- **Q3**: mechanism = the §11 classification is normative (every open issue in
  exactly one of BLOCKS 1.0 / OUTSIDE 1.0 SURFACE / POST-1.0 / NOT A BUG), made
  mechanical by labels + ledger + the machine gate — never by absence of label.
- **Q4**: freeze as proposed — the public surface freezes from the first
  Mel-approved RC; stabilization, fixes, tests, docs and CI keep moving.
- **Q5**: gaps may remain only explicitly OUTSIDE the 1.0 surface with known
  target, honest diagnostic, docs updated and the scope decision recorded.
- **Q6**: gate trust criteria (§10) are binding acceptance, not aspiration; the
  Quality-Gate technical solution stays its own front (§23 steps 5–8).

**Non-goals of this record:** it does NOT authorize a 1.0 cut, does NOT bump
VERSION (0.4.7-beta stays until release-prep per D-BRANCH-0.5.0), and does NOT
close #560 — the issue stays as the tracking thread until the queue it opened
is executed.

**Evidence:** maintainer ratification 09/20/2026 (chat, quoted verbatim in the
§22 block of the doc and in this record); doc EN+PT updated + promoted the same
pass; `roadmap.md` §23 queue; `DOING.md` claim; #560 cross-notified.


## D-VERSION-BUMP-0.5.0 — the revision moves to `0.5.0-beta` on the active branch (09/20/2026, maintainer order)

**Decision (maintainer, chat 09/20/2026):** "faz o bump de versão em tudo no repo
pra beta 0.5.0" — the product version is bumped `0.4.7-beta → 0.5.0-beta` on the
active branch `beta-0.5.0` (`D-BRANCH-0.5.0`). This satisfies release-prep
checklist item 3 (`docs/distribution/release-beta-0.5.0.md`) and supersedes
the "VERSION stays 0.4.7-beta" clause of `D-RELEASE-1.0` only in the sense the
release-prep phase it reserved has now begun by order.

**Mechanics:** single source `VERSION` → `scripts/bump-version.sh` syncs
`pom.xml` `<revision>` and `dev/kof/version.properties`
(`kof.version=0.5.0-beta`; compiler/runtime/stdlib `0.5.0`; tooling API 21
unchanged). Docs carrying a **current-version** stamp (README, `docs/status`,
`docs/backend-parity` header, `AGENTS.md` header, distribution/install outputs,
training/learn headers, idiom "Updated:" stamps, artifact-name examples) were
bumped EN+PT in the same commit. **Historical** mentions (CHANGELOG sections,
`known-bugs.md` measurements taken on `0.4.7-beta` jars, "Introduced:",
feature stamps like `D-TROOL 19/09`) were left intact — history is not
rewritten (freeze rule 4).

**Verification before bumping (prep item 3):** no test or script pins the
artifact version (only a historical javadoc comment in `TrooleanLawE2ETest`).


## D-1.0-EDGES — the open edges are closed: 5th category, #564/#565 as `1.0-blocks`, KofC + Android inside the 1.0 surface, all nine §35 reinforcements mandatory, and the 1.0 line opens after the 0.5.0 release + EG-1..EG-7 (09/20/2026, maintainer answers to the seven open questions)

**Context:** the `D-RELEASE-1.0` ratification left seven edges open (the
`[? MEL]` questions of `PROPOSAL-1.0-EXIT-GATE.md` §35, the #560/#564/#565
classification and Q1). The maintainer answered all seven (question poll,
09/20/2026). This record locks the answers; the `D-RELEASE-1.0` non-goals (no
1.0 cut, no tag, #560 stays open) still hold.

**Decided (all seven):**

1. **#560 — 5th category.** The tracking umbrella of the gate is not a defect
   and fits none of the four §11 categories; §11 now has **five**. The label
   was created as `release-tracking` and renamed to **`tracking/contract`**
   ("Tracks an already-ratified contract; valid in stabilization, must close
   before RC"); #560 carries it. The machine gate
   (`scripts/check_release_blockers.sh`) recognizes it; an issue in this
   category is valid during stabilization but must still close before the RC.
2. **#564 — `1.0-blocks`.** `kof deps resolve <owner>/<repo>@<ver>` always
   fails REG002 against real GitHub Releases (pickTarball cuts the asset object
   at the nested "uploader") — the package/deps contract is broken end-to-end.
3. **#565 — `1.0-blocks`.** Every JVM fat jar built by `kof deploy` embeds a
   truncated copy of itself as entry `kof-app.jar` (invalid zip) — deploy
   artifact integrity.
4. **Q1 — when the 1.0 line opens.** After the **0.5.0 release is cut** and
   **EG-1..EG-7 are closed**; only then does she declare it and the first RC
   candidate is cut (EG-8).
5. **KofC — inside Stable 1.0, with its own gate.** The site's "Disponível" is
   now consistent; KofC is a full 1.0 surface target with its own gate, not an
   outside/optional item.
6. **Android — inside 1.0, with its own gate** (the full option, not the
   recommended partial one; CI already runs the APK). Android is a full 1.0
   surface target with its own gate.
7. **§35 — all nine candidates become mandatory gates** (not only the four
   recommended): real app with the package; baseline/no-regression; failure/
   flake policy without false-green; Stable Surface snapshot at RC1; artifact
   identity (SHA256/provenance); per-target evidence manifest; compatibility
   corpus; formal waiver (+ the remaining doc items).

**Consequence — Stable 1.0 surface = 8 targets:** JVM, Native x86-64, riscv64,
aarch64, JS, Script, **KofC**, **Android**, each with its own gate where
applicable.

**Non-goals:** does NOT authorize the 1.0 cut (that is EG-8, gated on
EG-1..EG-7 + the 0.5.0 release), does NOT change VERSION, does NOT close #560.

**Evidence:** maintainer answers 09/20/2026 (question poll); label
`tracking/contract` on #560; #564/#565 labeled `1.0-blocks`; ledger
`scripts/release-blockers.tsv`; gate `scripts/check_release_blockers.sh`
(five categories; `--rc-gate` RED with 4 open `1.0-blocks`).
## D-SLOT-PIN — §383/#561 blessed-miss stored value: the pinned slot wins; JS coerces at store (option (a)) (09/20/2026, maintainer dispatch)

**Date:** 2026-09-20 · **State:** `DECIDED` (maintainer dispatch of the
#561 unit, 20/09 — "the slot's pinned type wins, value rewritten at store;
JS MUST match the 3-target majority")

**Decision:** the §383 dossier (three measured options) is resolved with
**option (a) — coerce JS to the slot**. Grounds, all pre-existing law (the
question is CLOSED by the contract, not re-opened): freeze rule 5 (JVM/Native/JS
divergence on the same program is a parity bug — never a silent divergence),
the "golden = JVM oracle" measurement law, and the §126 blessed-miss contract
as IMPLEMENTED (box-by-slot at store — `listOf(1).add(true)` stores `1` on
JVM/Script/Native). Consequences landed the same commit:

- **JS** (`JsCollectionOps.slotStoreCoerce`): at the pinned List add/set store,
  numeric-slot + Bool arg → `v ? 1 : 0`; Bool-slot + numeric/char arg →
  truthiness — the SAME coercion point the other targets use (the store).
- **JVM** (`CompilerEmissionHelpers.coerceStoreWiden`, List sites only): the
  blessed bool→Long miss now emits `I2L` before the slot box — the face used
  to die in `Long.valueOf(J)` over `ICONST_1` (COMP002 frame crash, measured
  20/09); Script/Native already stored `1` and keep byte-identical results.
- **Category-cross pairs are NOT blessed misses** (`CollectionWrites.breaksPinnedList`,
  List add/set + `listOf` literal sites): primitive into a pinned REFERENCE
  slot (`listOf(listOf(1)).add(true)`) and the mirror (object into a pinned
  primitive slot) break on BOTH compiled targets (JVM VerifyError at load,
  Native SIGSEGV/garbage) and diverge on the two tolerated ones — §126's own
  doctrine ("reject only what truly breaks") makes them SEM056 on all four.
- **Map/Set stay untouched**: there the neighboring-category heterogeneity is
  tolerated by the 3/4 consensus (faces S2/M1 measured 20/09 — coercing would
  move JS to the Native-minority side).

**Evidence:** `HeterogeneousSlotPinE2ETest` 16/16 (JVM≡Script≡JS≡Native
byte-a-byte, goldens from JVM runs 20/09); §383 flipped in
`known-bugs.md` EN+PT; #561 answered with the measured matrix.

## D-RELEASE-0.5.0-GATE — the 0.5.0 release gate: seven conditions, every one measured, no open edge (09/20/2026, maintainer directive)

**Context:** the 0.5.0 release is the precondition the maintainer set for
opening the 1.0 line (`D-1.0-EDGES` Q1: the 0.5.0 release cut + EG-1..EG-7
closed). This record locks the **release gate for 0.5.0** — the seven
conditions she stated as the priority for "releasing the 0.5.0 gate to all
agents". It refines (never replaces) the release-prep checklist and the §8
gate: the seven are the **acceptance**; the queue that satisfies them is
`roadmap.md` §24 (EG) + `release-beta-0.5.0-prep.md` + the per-item lanes.

**Decided — the seven conditions (maintainer's list, 09/20/2026):**

1. **100% parity between targets** — the same program yields the same
   observable result on every target of the 0.5.0 surface; a divergence is a
   bug (freeze rule 5) or a diagnosed `XXX00x` gap, never silent.
2. **No pending decision** — `DECISIONS.md` carries no open question that
   changes the surface (no unresolved `[? MEL]` in the PROPOSAL and no
   `State: OPEN`); nothing waits on a decision. A `State: OPEN — spec/plan
   first` entry is direction-decided but plan-pending, so the gate reports
   `NEEDS-REVIEW`, never a silent green.
3. **All loose `.md` in `docs/development/` concluded and moved out** — the
   three-states rule: `docs/development/` keeps only work with pending
   implementation; a concluded doc moves to `docs/`.
4. **Total stability** — full suite green (0F/0E outside documented
   environmental guards) + the 5/5 conformance matrix measured on the candidate.
5. **0 open issues that are a bug** — no OPEN GitHub issue that is a bug.
   #566 enters as a blocker (maintainer 09/20/2026).
6. **All edges closed** — every open edge closed with proof: the **full EG
   queue (EG-1 through EG-10)** and the open `1.0-blocks` issues. The 0.5.0
   release waits until every owner closes and moves their own work (confirmed
   by the maintainer 09/20/2026 — the gate measures, it does not take over
   another lane's item).
7. **Nothing pending in bugs-and-gaps** — `docs/bugs-and-gaps/known-bugs.md`
   and `specification-gaps.md` with no live/OPEN entry.

**Proposed execution order (agent reading — the maintainer may reorder):**
first the correctness edges that are already `1.0-blocks` and the live
known-bugs (conditions 1/5/6/7 — they share the same root causes and unblock
the rest), then the docs/decision hygiene (2/3), with stability (4) measured
last on the frozen candidate. The gate script reports each condition as
GREEN / RED / NEEDS-MEASURE so the worklist is exact, never by eye.

**Non-goals:** does NOT cut the 0.5.0 release (that is the maintainer's call at
the cut, rule 6), does NOT bump `VERSION`, does NOT open the 1.0 line, does NOT
authorize a 1.0 RC (EG-8 stays gated on the 0.5.0 cut).

**Evidence:** maintainer directive 09/20/2026 (chat); measured starting state
(09/20/2026): `scripts/check_known_bugs_status.sh` reports 19 live known-bugs
(EN×PT consistent); 4 OPEN `bug`-labeled issues (#561/#563/#564/#566);
`scripts/check_release_blockers.sh --rc-gate` RED with 4 open `1.0-blocks`
(#561/#563/#564/#566); `specification-gaps.md` 0 open.

### Addendum (09/20/2026) — version string, `main` freeze, and the package consumption model

Three maintainer answers (chat, 09/20/2026), same release scope:

1. **The 0.5.0 release ships as `0.5.0-beta`** (keeps the beta suffix; no
   codename — that is reserved for 1.0, `release-naming.md`). The lanes may
   draft the CHANGELOG and pre-stage the tag; the cut itself still waits on the
   seven conditions.
2. **`main` stays frozen until the 0.5.0 release.** The 12 pre-fix CodeQL
   alerts on `main` are not ported now; the port is a release-day action. The
   gate's conditions 5/6 are measured on the active branch `beta-0.5.0`.
3. **A package published by `kof deploy --publish` is consumed as a SOURCE
   module — option (b) of #566.** The published artifact must carry the
   sources; consumption is via the source module (`import regsmoke.Greeter`
   resolves against the installed sources), **not** via the compiled jar.
   Consequences opened for the cli/deps lane: `kof deploy --publish` must
   package the library's public source surface (today it packages only classes
   reachable from `main`), and the registry install (`kof deps resolve`) must
   place the source module where the import gate finds it. The three concrete
   defects split out of #566 (#567 `--classpath` silent no-op — R6, #568 SEM015
   false positive, #569 `build` emits on error) remain defects and proceed
   independently of the model.

**Non-goals:** the model decision does NOT change Kof syntax/semantics; it
settles the Registry consumption contract only. It does NOT cut 0.5.0 nor open
1.0.

---

## D-FFI-STRUCT — FFI struct/array ABI: the D6 decisions (maintainer 09/20/2026)

**Date:** 2026-09-20 · **State:** `DECIDED` · **Revision (09/20/2026):** the
maintainer's multiple-choice answer set **D6-1 = A+B** (lane `.14`/`.22` had
recorded option A) and confirmed D6-2..D6-5. This is the canonical record; the
earlier option-A text is preserved under *Superseded* (conflict policy: both
sides kept). Issues **#572/#573** (slice 3.8b) must align to **B** — `D-FFI-STRUCT-B`
(21/09) supersedes the A+B reading of D6-1: `record` stays by-value read-only;
the delta is the mutable by-ref `struct` (`Buffer(U8)` covers the out-buffer).

**Scope:** closes the `D6-1..D6-5` questions of
`docs/ffi-abi-structs.md` (§4) — the spec that gates the FFI
struct/array ABI (tracker 3.8a/3.8b/3.7). 3.8a (`AbiLayout`) landed 20/09.

### Decision

- **D6-1 = A+B** — a Kof `record` maps to a C struct **by value** (read-only,
  scalar fields, already zero-ceremony) **and** a new mutable `struct`
  declaration maps **by reference** (the shape that enables in/out buffers).
  The `struct` keyword is new language surface: it enters only through the
  Simplicity Law gate (rule 11) before landing.
- **D6-2 = only `new T[n]` binds to `ptr`** with **no implicit length** (the C
  API takes the length explicitly); `List<T>` stays `FFI001` until a
  boxed-unboxing benchmark proves the copy is worth it. (Own slice, after 3.8b.)
- **D6-3 = out-buffers are their own ABI kind** — `new Byte[n]` crossing as
  `Buffer(U8, INOUT)` (copy-in / call / copy-back), **never the `S` token**
  (`S` = NUL-terminated UTF-8 `char*`, read-only). Length stays an explicit C
  argument; no new syntax (rule 11).
- **D6-4 = implement the full sret** — the JVM FFM `Linker` hides it; the
  **native asm** backend implements it per ABI (SysV hidden pointer / AAPCS64
  hidden `x8` / LP64 reference) — slice 3.7, native lane.
- **D6-5 = confined arena per downcall** (`Arena.ofConfined()`, closed after
  the call); a returned `char*` is copied then never owned (Kof `String` is
  immutable). The measured wart (§1: `Arena.global()` on the FFI path) is fixed
  in this front, not left to drift.

**Encoding:** the `FfiSignature` token grammar gains a struct token
`@<fieldchars>` (e.g. `div(Int,Int):Div` → `@ii`), reusing the scalar chars
`i j f d b`; arrays/out-buffers get their tokens in their own slice. Anything
outside the decided set stays `FFI001`/`FFI002` (R6), never silent.

### Invariants

- Zero regression on the scalar/callback FFI (`FfiE2ETest`,
  `JvmFfiCallbackE2ETest` stay green).
- Native/JS keep `FFI001`/`FFI002` for struct until 3.7/JS land — never a
  silent partial binding (R6).
- Only scalar fields bind in v1; a record with a non-scalar field is `FFI001`
  on JVM (honest). `CString`, `Buffer`, `Pointer`, `OpaqueHandle` and `Struct`
  remain distinct ABI types even when all lower to an address in a register.

### Superseded (preserved — the earlier option-A record, lane `.14`/`.22`, 20/09)

> The lane had recorded **D6-1 = option A** (a `record` only, by value,
> read-only; no new `struct`) and treated D6-2/3/4 as deferred with owner. The
> maintainer's 09/20 answer (A+B; all five decided) supersedes it. Kept for
> traceability.

### Implementation

`docs/ffi-abi-structs.md` §6: 3.8a ✅ (landed); **3.8b = JVM
binding (this front)** — compile lane (#572/#573, align to A+B); 3.7 = native
asm (native lane, includes sret); JS boundary = its own decision. The doc is
promoted to `docs/` only when 3.8 lands (three-states rule).

### Evidence

- Spec + measured layout: `docs/ffi-abi-structs.md` §1–§3;
  `AbiLayoutTest` (14 shapes × 3 ABIs, GCC 13.3 golden).
- E2E proof for 3.8b: `FfiStructE2ETest` (JVM: record arg + record return via a
  real C `.so`, byte-for-byte vs the C oracle; Native/JS pinned `FFI001`/
  `FFI002`).

### Relationships

- `Supersedes: the 20/09 option-A D6-1 record (same heading, lane .14/.22)`
- `Depends on: D-POLL-19 (spec-first), AbiLayout 3.8a, D-KOF-FIRST`
- `Related: R3 (FFI), R6 (never silent), R9 (interop-first), rule 11 (struct keyword gate), D-1.0-EDGES`


## D-ARTIFACT-TRUST — 1.0 trust contract for release artifacts: integrity + exact-artifact + neutral build provenance, attested by the official workflow; verification mandatory at the release gate and in `kof deps resolve` for official packages (09/20/2026, maintainer answers to #571)

Decided via the issue-lane multiple-choice (20/09). **(1) Mandatory properties:** `SHA256SUMS` integrity (standalone jars join the cycle) + the `§32.6` exact-artifact invariant with MECHANICAL enforcement in `--rc-gate` (tested digest == published digest) + build provenance attestation verifiable online and offline. **(2) Identity/vendor:** the official workflow under Actions is the attesting identity; the contract states NEUTRAL PROPERTIES (vendor name non-normative — `D-KOF-FIRST`/R11: no home-grown crypto, no vendor as oracle). **(3) Object:** for a library the verified artifact is the SOURCES tarball (confirms #571-Q12); each target binary carries its own digest. **(4) Where mandatory / failure policy:** release gate BLOCKS without valid evidence; `kof deps resolve`/Registry HARD-BLOCKS official packages without valid evidence; community packages = honest warning (R6, never silent). **(5) Hardening (separate queue, not part of the contract text):** SHA-pin all 59 action refs, job-level least privilege (kill workflow-level `contents:write`/direct-to-main flow), rulesets on `main`+active branch; signed commits/SBOM = post-1.0. Queue: (a) rc-gate digest enforcement + jar-in-SUMS (tooling lane), (b) attest+verify in the release workflow (CI lane), (c) resolve-side evidence check with official/community policy (cli/deps lane).

## D-1.0-STABILITY-100 — the total-stability criterion for closing 1.0.0: EVERY item in `docs/development/`, `docs/development/future/` and `docs/bugs-and-gaps/` is 100% resolved, with full cross-target parity proven (09/20/2026, maintainer rule)

Rule (ABSOLUTE, refines `D-RELEASE-1.0`): no KOF 1.0.0 release while ANY work item remains open/undelivered in the three ledgers — `docs/development/` (plans with pending implementation), `docs/development/future/` (promoted features must be DEVELOPED, not deferred past 1.0), `docs/bugs-and-gaps/` (bugs, spec gaps, parity matrices) — and parity means the measured cross-target matrix (rule 5 of the freeze), proven by tests/goldens, never by claim. "Stable" is a STATE TO VERIFY (AGENTS §Stability), and 1.0.0 is the formalization of that state; the EG queue, `release-blockers.tsv` and this rule must agree — a closed issue that leaves work pending does NOT discharge the blocker: only the landed proof does.

---

## D-R3-3.3 — FFI handles and out-buffers (multiple-choice, maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` · **Option chosen:** **A** (of A/B/C).

`void*` / `T*` / out-buffers are represented by a **nominal opaque `Handle`**
(non-arithmetic, never an integer) plus **`Buffer(U8, INOUT)`** for
by-reference byte buffers — consistent with **D6-3** (`Buffer(U8, INOUT)`, no
new buffer syntax). No pointer arithmetic. `Pointer`/`OpaqueHandle`/`Buffer`/
`Struct` stay distinct ABI types even when a register carries an address (R6:
never silent).

- **Unblocks:** R3-3.3 → open; the out-buffer/buffer slice of R3 (prerequisite
  of Stages 4–7, all behind R3).
- **Evidence:** D6-3; `docs/ffi-abi-structs.md`.
- **Relationships:** `Depends on: D-POLL-19/D6 · Related: R3, R6, R9`.

---

## D-R3-3.5 — FFI variadics (multiple-choice, maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` · **Option chosen:** **A**.

**No general Kof variadics.** An FFI caller passes a `List`/`Array`/`Buffer`
instead; `printf`-style calls are covered by fixed-arity overloads. Rationale:
the JVM FFM `Linker` has **no variadic downcall**, so a `...` marker would
diverge per target — a silent lie (R6/R7). A variadic libc call with no
fixed-arity form stays an explicit documented gap.

- **Unblocks:** R3-3.5 closed as "no variadics" (documented).
- **Relationships:** `Depends on: D-POLL-19/D6 · Related: R3, R6, R7`.

---

## D-TYPE-VARIANCE — variance + sealed types (multiple-choice, maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `IMPLEMENTED` (plan approved; X5.1–X5.5 landed 21/09) · **Option chosen:** **C** (variance + sealed).

The maintainer **opens** variance + sealed types as a core type-system front
(scientific collections + exhaustive `switch`). It is a **frozen-core change
(rule 6)** and follows the **spec-first** discipline of D6: a written design
plan is drafted and reviewed **before any parser/typer diff** — nothing lands
silently. Type-classes remain rejected (permanent non-goal).

- **Unblocks:** X5 → landed (spec-first: plan reviewed, then slices with proof).
- **Landed:** X5.1–X5.5 (21/09) — `sealed` + exhaustive `switch` (`SEM080`/`SEM081`),
  declaration-site and use-site variance (`SEM082`); proof `SealedTypeE2ETest`,
  `TypeVarianceE2ETest`, `UseSiteVarianceE2ETest` (45 tests green).
- **Relationships:** `Related: rule 6, rule 11, R10, permanent non-goals, D-KOF-FIRST`.

---

## D-INTEROP-REFLECT — interop reflection (multiple-choice, maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `IMPLEMENTED` (X6.0–X6.3 landed 22/09) · **Option chosen:** **A — compile-time intrinsic**.

The maintainer authorized starting X6 (21/09). Surface frozen:

- **`interop.schema(R)`** — a **compile-time intrinsic** in the `interop`
  namespace, where `R` is a `record` type declared in the module. It resolves
  to an **immutable** `List<Field>`, where
  **`record Field(String name, String type)`** is a compiler-provided record
  whose entries are the record's components in declaration order.
- **Zero runtime reflection**: the compiler already knows the record's
  structure, so the intrinsic is folded at compile time — no
  `java.lang.reflect`, no runtime metaprogramming, no `Class.forName`.
- **Same output on every target** (JVM/Native/Script/KofJS): the fold is
  frontend-level, so no `REF001` gap is needed (the JVM-first posture is
  satisfied trivially).
- **Boundary-only**: the namespace is `interop`; it is not a language
  foundation and must not grow into general reflection (fence documented).

Reflection is authorized **only at the interop boundary** (never a language
foundation). The incremental plan was drafted first (slices with proof per
slice) — the same spec-first gate as D6/X5.

- **Unblocks:** X6 → X6.1 (JVM slice) in progress.
- **Next deliverable:** X6.1 implementation + golden test.
- **Relationships:** `Related: rule 6, rule 11, R9, X5, D-KOF-FIRST`.

---

## D-CODEGEN-STEP — compile-time codegen hook (multiple-choice, maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` · **Option chosen:** **A**.

Implement **`CodegenStep`** as a **compiler-internal hook** (no user syntax) —
R4 (`🔵`). It is the declared blocker of Stage 3 (`infra "prod" {}` desugar,
3.2) and of the DDL/runner migration. It adds **no language surface**; any
user-facing form (3.2) is its own later decision (rule 11 gate).

- **Unblocks:** R4 → in progress; 3.2 unblocked **behind R4**.
- **Evidence:** `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` R4 + critical path.
- **Relationships:** `Related: R4, R8 (same frontend), 3.2, rule 11`.

## D-GRAPHICS-GAMING — graphics beyond forms: a future plan for the 2D/3D/game surface is MANDATORY (09/20/2026, maintainer request)

Maintainer asks how Kof handles 2D, 3D and non-web graphics ("how does one make a game in Kof?") and directs: open the plan in `docs/development/future/`. Current ground truth: `kof.ui` is a form/intent surface (JVM=JavaFX, JS=DOM, Android=APK); the corpus has NO game abstraction (frame loop, sprites, meshes, input-per-frame, audio, GPU) — games today would be interop, not idiom (rule-8/11 boundary: a foreign API shape is not the answer; the plan must define the KOF INTIMATION the platform lowers, per-target honest gaps R6/XXX001-style, interop-first R9 for engines/libs — never a home-grown renderer, and KofC/wasm are future). PLAN-DOC: `docs/development/graphics-gaming-plan.md` — skeleton next session; design questions it must answer: game-loop primitive (a `scene`/`frame` idiom?), 2D sprite/tileface surface, 3D scope (mesh/camera/material as intent vs. FFI to native GPU libs), audio, input model, per-target honesty (JVM/Native/JS/web + KofC later), and the non-goals guard (no HTML5-canvas leakage into user code). Priority: future/ — does NOT compete with 1.0 (R12 + D-1.0-STABILITY-100: it enters the 1.0 surface only by her explicit promotion).

### D-GRAPHICS-GAMING addendum (09/20/2026, maintainer, same session) — the media surface is IN SCOPE of the plan: a sound pipeline AND video support

KOF also needs a **sound pipeline** (playback, streams, volume/mix, the game-audio case: low-latency SFX) and **video support** (a `video`/`VideoView` intent surface in `kof.ui`-land: file/stream playback, the player chrome belonging to the platform, never to user code). The plan doc MUST treat media as first-class: the KOF idiom (e.g. `sound.play("x.ogg")`, `video` panel component) + per-target lowering JVM (JavaFX Media/`javax.sound` exist TODAY on JVM — measure before promising), JS (browser `<video>`/WebAudio — the platform renders), Native (interop-first R9: SDL_mixer/miniaudio/OpenAL/ffmpeg — never a home-grown codec; honest `XXX001` gaps where absent, e.g. audio on riscv cross), + the non-goals guard (no HTML5 `<audio>` tags leaking into Kof code; codecs are the platform's problem). Pipeline of sound (mixing/graph) gets its OWN section in the plan answering: latency contract, formats supported per target, device enumeration, and whether `kof.sound` is core-stdlib or a stdlib package (R1 boundary).

### D-GRAPHICS-GAMING addendum 2 (09/20/2026, maintainer) — NO JavaFX; the graphics/media surface requires FULL PARITY

"No JavaFX. It must have full parity." Consequences recorded: (1) the graphics/media plan may NOT use JavaFX (nor any single-target toolkit) as the rendering/media backend of the KOF surface — JVM must reach the same idiom through the same portable stack as the other targets (interop-first R9: the shape the plan evaluates is an SDL/GL-class portable layer lowered to per-target bindings, not platform chrome); (2) FULL PARITY is the acceptance criterion for this surface — unlike R7's "honest scope per target", a graphics/media feature is only in the language surface when EVERY target runs the SAME program with the SAME behavior (or the feature is not promoted at all); (3) EXISTING kof.ui-JVM (JavaFX-based) keeps working unchanged (backward compatibility, freeze rule 2) but is the legacy face of the area, not the future one — its migration/retirement is a DESIGN QUESTION the plan must answer (rule 6), never an agent decision; (4) the JavaFX-removal work item lands in `docs/development/graphics-gaming-plan.md` §parity as its own section (measure today: which kof.ui classes bind javafx.* on the JVM target).

### D-GRAPHICS-GAMING addendum 3 (09/20/2026, maintainer — CORRECTION to addendum 2) — Kof never used and never will use JavaFX; every "JavaFX" message in Kof is a bug in disguise

The maintainer revokes the framing "kof.ui-JVM is JavaFX-based legacy that keeps working": **Kof has NEVER used JavaFX and NEVER will** — consistent with the house rule of 09/12 (`AGENTS.md`, "JavaFX rule"): the message "componentes de runtime do JavaFX não foram encontrados" is NEVER benign, it is the launcher swallowing a real `VerifyError`/`ExceptionInInitializer` — a disguised bug, always root-caused, never accommodated. Therefore: (a) addendum 2 item (3) reads: any `javafx.*` binding found in the Kof tree is NOT a legacy face — it is a DEFECT to be removed by a normal bug pipeline (freeze rule 4: fix the code to reach the documented behavior, never document around it); (b) `kof.ui` on the JVM was, is, and will be served by the portable parity stack from the start — the "migration" section of the plan becomes an ERADICATION section: measure every `javafx` reference in src/docs/std-lib (`grep -rn "javafx" kof-*/src` etc.), classify each (bug of the disguised-exception kind vs. wrong-doc claim) and file as items with reproduction; (c) backward compatibility does NOT protect a JavaFX path — user Kof code never named JavaFX, so removing it cannot break any valid Kof program (the compat promise is to Kof programs, not to internals).
## D-MAKEALIVE-CLI — 3.8 contract: `kof makealive plan|apply|destroy` (tooling over the host)

Decided 20/09 by maintainer delegation to `.18` ("propose the contract + implement") —
row 3.8's rule-6 required a decision on the command contract. **(1) Verb:**
`kof makealive <plan|apply|destroy> <file.kf>` — NOT `kof infra`: D-MAKEALIVE Q1 decided
the namespace IS the name (`kof.makealive`) and the literal `infra` stays HARD-DENY in the
stdlib ledger (R1); the CLI follows the decided name. **(2) Program convention (the
D-WORKFLOW-RUN posture of 2.6):** the file is pure Kof — `import kof.makealive`,
`design(): Infrastructure`, `provider(): Provider`, no `main()`; the tool synthesizes a
main() over the host's own faces (`plan`/`apply`/`destroy`/`mkLoadState`/`mkSaveState`/
`mkMaxGen`) and communicates through the marked line `@@KOF_MAKEALIVE@@ {json}`; human/JSON
formatting lives in the CLI, never in the host. **(3) State:** h2 file via `--state PATH`
(default `<file>.makealive`); the "the runner brings the JDBC driver" contract is unchanged
(KofJsDbBridge); apply/destroy ALWAYS save `gen = max+1` — and destroy persists an
empty-state marker (`res ""`) so the empty generation is visible to `mkMaxGen`/`mkLoadState`
(bug found by this decision's own E2E; golden `emptyGenerationIsVisibleAndLoadsEmpty`).
**(4) Targets:** JVM+JS byte parity (R7); script/native honest refusal (same as 2.6) — the
Native host stub keeps `ORM001` at the call site. **(5) rc:** the marked line decides
(`allOk`); a throw (provider refused set/delete, argument guards) = no marked line, the raw
output IS the diagnosis, rc 1. Proof: `CmdMakealiveTest` 7/7 + `MakealiveMaxGenE2ETest` 4/4
+ Makealive battery 14/14.

## D-MAKEALIVE-SYNTAX — 3.2: `infra "prod" { ... }` is PURE SUGAR over `design()` (21/09/2026, maintainer)

**Date:** 2026-09-21 · **State:** `DECIDED` · **Decides:** `makealive-plan.md` §5 row **3.2** (and unblocks **3.7**; reaffirms **3.8**) · **Supersedes:** nothing.

**Context:** the three remaining makealive rows (3.2/3.7/3.8) were polled to the maintainer.
Row **3.2** ("syntax `infra "prod" {}`") needed a rule-6 decision because it is **new
user-facing parse surface**; the earlier codegen-hook blocker had already been removed by
**R4** (`CodegenStep` hook, landed 21/09).

**Decision (rule 11 — Simplicity Law):** ADD the block, as **pure syntactic sugar** — it
desugars over the host's already-decided records/builder and gets **no semantics of its own**
(plan §7, "no HCL inside Kof").

- **(1) Form:** a top-level declaration `infra "prod" { <calls> }`. `infra` stays an
  **IDENTIFIER** (dispatched exactly like `test`/`application`), **not** a reserved word and
  **not** a new token — so `LanguageCoreSurfaceTest` (8.6) stays green **by construction**.
- **(2) Desugaring:** the block lowers to `design(): Infrastructure` — a synthesized local
  `__infra = Infrastructure("prod")`, each statement `name(args)` becomes `__infra.name(args)`
  (the host faces `resource`/`prop`/`requires`), then `return __infra`. Output is identical to
  the hand-written imperative `design()`; the CLI contract (`D-MAKEALIVE-CLI`) is unchanged.
- **(3) No HCL, no nesting:** the body is plain Kof call statements — no `key = value`, no
  `resource` sub-block, no new type, no new runtime.
- **3.7** (compile-time cycle detection) — **CLOSED as runtime-only** (maintainer 21/09,
  addendum): with the block as pure sugar the compiler sees only generic calls, so a
  compile-time graph would give `infra` its **own semantics** (against §7 / rule 11);
  the **runtime refusal** shipped in 3.1 already names the cycle members — that IS the
  contract. No static check is added.
- **3.8** — reaffirmed: `kof makealive plan|apply|destroy` is the **only** verb
  (`D-MAKEALIVE-CLI`); `kof infra` is **not** added.

**Proof (measured at landing):** `InfraSyntaxE2ETest` — an `infra "prod" { ... }` file and its
hand-written `design()` twin produce byte-identical `plan`/`apply` output (JVM==JS), plus a
syntax golden (`infra` is not reserved; a body kept in the host faces). Documented in
`docs/stdlib/makealive.md` + `learn/`.

## D-GRAPHICS-GAMING addendum 4 (09/20/2026, maintainer) — Kof WILL HAVE ITS OWN graphics engine for games

Order: Kof needs its own graphics engine for games — the plan's interop-first recommendation (R9) is REVOKED for this domain (D-UNIVERSAL-style precedent). The engine is Kof's (Kof/platform code, house-driven), exposed in zero-boilerplate idiom (rule 11: idiomatic, easy, no accidental complexity); FFI bindings stay limited to the non-engine layer (window/GPU/audio device). Consequence: the plan §§3–4 + Q1/Q5/Q7 in `future/graphics-gaming-plan.md` + README/learn/training/UI-media docs need REWRITE in this direction; R9 gains a named exception in DECISIONS. Detail choices (engine name, first slice, formats) stay rule 6 via the plan open Qs.

## D-PROPERTY — property-based testing: NO new syntax — the surface is the existing `test` + `kof.rng` + `assert` idiom (21/09/2026, maintainer-delegated)

**Date:** 2026-09-21 · **State:** `DECIDED` · **Decides:** `SG-023` (option **C** + option **iii**) · **Closes:** the X8 remainder (G6 "next").

**Context:** the X8 front landed `rng` (slices 1–2), `kof test --timeout` and named suites by directory. The two remaining faces — a property runner and suite fixtures — were registered as **`SG-023`** ("REQUESTED, no decision") because both *suggested* new user-facing surface (rule 6). Asked to decide, the maintainer delegated the choice ("Decide SG-023 surface").

**Decision (rule 11, Simplicity Law): NO new syntax.** The mechanism already exists and is documented:
- **property** = **option C** — a `test "name" { }` whose body seeds `rng` and loops, using `assert(cond, msg)`. **REJECTED** option A (`property`/`forAll` keyword) and option B (`kof test --props` implicit mode): ceremony over a mechanism the language already has.
- **fixtures** = **option iii** — nothing new; the `D5-B` pattern (`close()` + `try/finally`) already expresses per-test setup/teardown. **REJECTED** option i (`setup`/`teardown` blocks) and option ii (`_suite.kf` convention file).

**Why:** "Kof must be simpler than any alternative" — `rng.seed(42)` + loop + `assert` is shorter and more intent-revealing than a keyword + generator inference + shrinking machinery; the compiler stays smaller and the idiom works on every `rng` target. It respects `D-KOF-FIRST` (no QuickCheck/Hypothesis borrowing before a Kof contract).

**Proof:** `PropertyTestIdiomE2ETest` **7/7** (kof-compiler) — a seeded 200-iteration property PASSes and is reproducible across runs, its `checksum` is bit-identical **JVM==JS** and **JVM==Native-x86** (`rng` parity), a falsifiable property FAILs deterministically with the seed-derived message and exit 1 (JVM+JS), and a zero-iteration property PASSes vacuously. Documented in `training/idioms/stdlib.md` + `learn/23-testing.md`.

**Not a language change:** no parser/typer/codegen touched; the `kof test` surface is unchanged.

## D-ARRAY-PRINT — §388-B: printing a whole `Int[]` is container format (21/09, maintainer)

The §388 entry logged the reverse parity of the bytes faces: JVM/Script printed
`[I@65629ac6` (raw Java toString of `int[]`) while KofJS printed `65,66,67` —
no corpus line declared how a primitive array PRINTS (the container-format row of
the matrix covered List/Map/nested only). The maintainer settled it in chat on
21/09 (rule 6): the **container format wins** — i.e. the §107 grammar already in
place for collections (oracle = `ArrayList.toString`, `[65, 66]` with brackets
and `, ` separators; JS mirrors via `kofFormat`, the three native targets via
`kof_array_to_string`).

Calibration note: the vote option was phrased "65,66,67" (the then-current JS
face), but what was voted against is the IDENTITY form; the house container
format — already declared for collections since §107 and pinned in
`conformance-matrix.md` — is `[65, 66]`. The implementation follows §107, not
the literal text of the option.

Scope: `println(new Int[n])`, `println(readBytes())`, flat/nested/empty, records
and Strings by the element's own content toString. Passing a `List` to a bytes
face stays a compile error (`SEM099`, §388-A) — untouched by this decision.
Tests: `arrayprint` cell in `ConformanceMatrixTest` (JVM/Script/JS/native),
`ArrayPrintFormatE2ETest`, riscv64/aarch64 goldens in `Native*E2ETest` (CI/qemu).

## R6-SCOPE — incremental delivery does NOT breach R6 (maintainer, 21/09/2026)

**State:** `DECIDED` · ABSOLUTE clarification of R6 (never silent).

R6 forbids **silence**, not **partial scope**. A delivery that is a **complete
vertical slice for its declared scope**, with the not-yet-supported paths
failing through **honest diagnostics** (`FFI001`/`FFI002`/`XXX00x` — which *is*
R6), does **not** breach R6. R6 is violated only when a gap is **hidden**: a
silent stub, a weak fallback, a divergence the user cannot see.

Consequence: any undelivered capability is built **incrementally** (e.g.
JVM-first, with Native/JS as *declared, diagnosed* gaps — R7) and each slice
lands whole for its scope. "Can't do it all at once" is no reason to defer the
slice; "hide the missing part" is the only forbidden move.

## D-R3-BUFFER — out-buffer is the nominal `Buffer(U8)` type (maintainer, 21/09/2026)

**State:** `DECIDED` · **Option chosen:** nominal type (of reuse-`Byte[]` /
nominal `Buffer(U8)` / split).

D6-3/D-R3-3.3 fixed that out-buffers exist as their own ABI kind
(`Buffer(U8, INOUT)`, copy-in / call / copy-back, **never `S`**). This decision
fixes the **spelling the user writes**: a **nominal `Buffer(U8)`** type in the
`extern` signature — *not* a reuse of `Byte[]` (scalar `T[]` stays the
read-only `ptr` of fatia 3/D6-2). `Buffer` stays a distinct ABI type even when
a register carries an address (R6).

**Creation/lifetime (answered 21/09):** the programmer obtains a buffer with
**`buffer.alloc(Int n) : Buffer(U8)`** (stdlib) and its lifetime is
**automatic** — the compiler releases it at the end of the enclosing scope; the
programmer never allocates or frees. `Buffer(U8)` as an `extern` parameter is
`Buffer(U8, INOUT)`: copy-in, call, copy-back; the length is an explicit C
argument (D6-3). Inspect with `Buffer.bytes()`. Incremental slice: JVM first;
Native/JS keep honest `FFI001`/`FFI002` (R6-SCOPE).

## D-R3-HANDLE-LIFETIME — `Handle` memory is automatic (maintainer, 21/09/2026)

**State:** `DECIDED` · **Direction chosen:** automatic (of single `Handle<T>`, or
defer).

D-R3-3.3 chose a nominal opaque `Handle` (never an integer, no pointer
arithmetic). This decision fixes its **lifetime**: allocation/deallocation must
be **automatic — the programmer never manages memory** (no manual `malloc`/
`free`). `Handle` therefore does not land as an isolated FFI type now; it is
delivered together with the language-managed resource/lifetime mechanism
(scoped-resources / RAII front, `docs/scoped-resources-plan.md`),
which is the owner of the allocation strategy. Until then `Handle`-typed externs
stay honest `FFI001`/`FFI002` (R6).

---

## D-DESUGAR-STEP — 2.2.3 resolved: AST-phase desugar registry (option B) (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` · **Option chosen:** **B**.

The maintainer chose **option B** of `docs/architecture/codegen-step-2.2.3-assessment.md` (+PT) — **implemented 21/09 (`85779f20`)**:
add a **`DesugarStep` registry at the AST phase** mirroring
`CodegenStep`/`CodegenStepPipeline`, and migrate the four source desugars
(`desugarTests`/`desugarApplication`/`desugarInfra`/`desugarNestedFunctions`,
today in `CompilerDesugar`, `CompilerPipeline.java:301-304`) into registered
steps. **Compiler-internal, zero language surface** (rule 11: nothing reaches
user code). **Behavior-free** (freeze rule 3): same suite + golden E2E per
target, byte-identical output. The ORM DDL stays in lowering (not a candidate).
Queue: `roadmap.md` §23 `2.2.3` + tracker R4.

- **Unblocks:** 2.2.3 (`⛔` → open, slices).
- **Relationships:** `Related: D-CODEGEN-STEP, freeze rule 3, rule 6, rule 11`.

## D-TYPE-VARIANCE / D-INTEROP-REFLECT — plan APPROVED, slices authorized (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `IMPLEMENTED` (plan approved; X5+X6 surface landed 22/09).

`future/type-system-extensions-plan.md` (+PT) was reviewed and **APPROVED**. X5
(variance+sealed, option C) and X6 (interop reflection) started **incremental
slices, each with its own proof**. The surface has landed (X5.5 + X6.3, 22/09),
so gate condition 2 no longer needs review. The plan is promoted
to `docs/development/` (three-states). Queue: `roadmap.md` §2.8.4/§2.8.5.

## D-SECRETS — Stage 5 / 3.6 promoted; face 1 authorized (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` · **P1+P2+P3 LANDED.**

`future/secrets-plan.md` (+PT) is **promoted to `docs/development/`**; **face 1
(`Secret` type)** is authorized as a rule-6-voted surface, incremental with proof;
`KeyHandle`/redaction follow face by face. Last residual of `makealive-plan`
(3.6). Queue: tracker 3.6.

**Face 1 LANDED 21/09 (`32285136`):** `Secret` value type — `secrets.of(text)` /
`secrets.secret(name)` constructors, `reveal()` (the only raw export),
`redacted()`, redacted printing (`Secret(*** )`), constant-time `==`. JVM-first
(R7); JS/Native/Script/Android = honest gap `SECN008` (R6). Proof `SecretE2ETest`
4/4.

**P1 remainder + P2 + P3 AUTHORIZED (maintainer 21/09, direct order "implementa
tudo o que falta do secrets plan … precisa fechar"):** the whole
`secrets-plan.md` is to be implemented and the plan closed. Chosen options (the
plan's own alternatives): `secrets.get` stays the legacy raw `String` (frozen
0.2.6) and `secrets.secret`/`secrets.of` are the typed path — **non-breaking**;
`P3 KeyHandle` is pulled **forward from "after 1.0"** by the same order. Each face
stays incremental with proof and its own honest per-target gap. Queue: tracker
3.6 / `secrets-plan.md` §2.

**ALL FACES LANDED 21/09 (`04473bbe`):** P1 remainder (`secrets.fromBytes(Int[])`,
identity `hashCode`); **P2** enforced redaction (runtime `json.encode(Secret)` →
`"Secret(*** )"`; compile-time lint `SECN009` when `reveal()` feeds
`log.*`/`json.encode`); **P3 `KeyHandle`** (`secrets.keyFromHex/keyFromPem/
keyFromKeystore`, `rotate()` revoking the old handle → later use `SECN010`,
`KeyHandle` overloads on `crypto.hmacSha256/aesgcm/chacha20` and
`jwt.create/verify`; raw key never exposed). JVM-first (R7), `SECN008` elsewhere
(R6). Proof: `SecretE2ETest` 7/7 + `KeyHandleE2ETest` 5/5. The plan is closed and
moved to `docs/architecture/secrets-plan.md` (design record).

## D-FFI-STRUCT-B — D6-1 option B (`struct` mutable): approved spec-first (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` · design-first (no code yet).

The maintainer **approved D6-1 B** (new mutable `struct` declaration, by-ref, for
in/out buffers) **spec-first**: the surface is designed/measured in
`ffi-abi-structs.md` and reviewed **before any parser/typer diff** (rule 11).
Records stay by-value read-only; `Buffer(U8)` already covers the out-buffer case
landed. Queue: tracker 3.8 (`ffi-abi-structs.md` §6).

## D-DB-PARITY-OWNER — db-parity owner named; S0/S1 authorized (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED`.

`db-parity-plan.md` (+PT) gets an owner (docs/plataforma lane, the D-DB-GAPS
author) and starts **S0** (honest interim §421 diagnosis) + **S1**
(`mariadb://` = mysql-wire alias), each with proof; S2–S4 follow per slice.
Addendum to `D-DB-GAPS`.

## D-RELEASE-0.5.0-GATE condition 2 — stays `NEEDS-REVIEW` while a surface front is in flight (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` (confirmation).

Condition 2 reports `NEEDS-REVIEW` — **not RED** — while an approved `State:
OPEN` front has not landed; it does not block the 0.5.0 cut by itself. An
`OPEN` entry is never "nothing waits".

## D-X5-SURFACE — X5 surface freeze (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED`.

Maintainer answers to the plan's open questions a–d:
- **(a) variance keyword = `out`/`in`** — declaration-site, single-char (passes rule 11).
- **(b) `sealed` applies to `class`/`record` + `interface`.**
- **(c) use-site projection (`List<out T>`) IS in v1** (overrides the plan's "deferred" default; X5.4 becomes a v1 slice).
- **(d) diagnostics stay in the existing `SEM0xx` family** (no new family).

Surface: `sealed class/record/interface`; subtypes outside the set = diagnostic;
exhaustive `switch` over a sealed subject; `out`/`in` on generic params with an
assignment-soundness check. Compiler/frontend only; no runtime surface.
Queue: `roadmap.md` §2.8.4; slices X5.0→X5.5 (X5.4 now in v1).

- **Relationships:** `Related: D-TYPE-VARIANCE, rule 6, rule 11, R10`.

## D-RELEASE-0.5.0-SCOPE — the in-flight owned plans still loose are allowlisted in condition 3 and EG-8 is decoupled from condition 6 (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` (maintainer answers in the chat).

Two scope rulings for the 0.5.0 gate (`D-RELEASE-0.5.0-GATE`), so the release
does not wait on other lines' open fronts:

- **(a) Condition 3 — allowlist of the in-flight OWNED plans still loose.**
  `db-parity-plan` (owner gaps-db lane) and `ffi-abi-structs` (owner jonas) stay
  in `docs/development/` **without turning condition 3 RED**: each has an owner,
  a live queue and declared pending implementation; they conclude on their own
  fronts (three-states rule), not as a precondition of the 0.5.0 cut. The gate
  ALLOWLIST carries them; the README queue keeps tracking them. A doc without
  an owner/queue is still RED. (`IMPLEMENTATION-UNIVERSAL-PLATFORM` was on the
  ruling's original list and **concluded 21/09** — moved to
  `docs/architecture/`; `type-system-extensions-plan` (owner compiler/X5)
  **concluded 22/09** — X5+X6 landed with proof, moved to `docs/`; hence
  neither is loose/allowlisted.)
- **(b) Condition 6 — EG-8 decoupled.** EG-8 is the first 1.0 RC candidate plus
  the maintainer's explicit "the 1.0 line is open" declaration, and it only
  opens after EG-1..EG-7 close — it belongs to the 1.0 line, not to the 0.5.0
  gate. Condition 6 now measures **EG-1..EG-7 + the open `1.0-blocks` issues**;
  an open EG-8 never turns it RED.
- **(c) Condition 7 — batch triage.** The maintainer receives the 14 live
  known-bugs with a per-item recommendation (close / post-1.0 / not-a-bug) and
  classifies them in batch; until then the condition stays RED (measured).

**Evidence:** `scripts/check_release_050_gate.sh` (ALLOWLIST + `c_edges`,
`--selftest` with both planted cases), `release-beta-0.5.0-prep.md` conditions
3/6, README condition-3 pending list.

- **Relationships:** `Related: D-RELEASE-0.5.0-GATE, D-RELEASE-1.0, rule 6, rule 3`.

## D-CLOSEALL-BATCH — batch dos 14 known-bugs: a mantenedora ordenou fechar tudo com evidências; os 3 forks rule-6 foram votados (mantenedora, 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` (mantenedora, interativo — diretiva
"você assume bugs-and-gaps e fecha todos os bugs que existem e apresenta
evidências para todos"; sub-votos respondidos no chat com as opções recomendadas).

- **§334 (`kof_box_equals` NaN) → CLOSED:** divergência documentada e
  INALCANÇAVEL do código-fonte Kof (nenhum produtor de NaN hoje — divisão por
  zero é diagnóstico de compile-time OBS-009); downgrade para informativo,
  como a própria entrada prevê.
- **§188 (`"2026" as Int` → VerifyError) → (A) REJEITAR no compile-time** —
  novo SEM0xx nomeando o idiom canônico `math.parseInt`.
- **§400 (função nomeada passada como VALOR, SEM011 falso) → (A) MANTER a
  rejeição, novo diagnóstico nomeia a regra real e aponta o idiom lambda
  (`probe` → `() -> probe()`, byte-paridade já medida).**
- **§283 (native aarch64: processo nunca encerra com `time.interval` vivo)
  → (B) WORKER COMO THREAD DAEMON — paridade com `java.util.Timer` (daemon
  por padrão na JVM).**
- **§418 (harness riscv debug sem destroy) + §423 (channels riscv/aarch sem
  runtime): HANDOFF p/ lane nat/native-debug (9093, ativa no período — fechou
  §424/425/427); assumo se ela parar (regra dead-task).**
- O resto do lote (§302/§280/§268/§248/§278/§205) = trabalho normal da lane;
  §271/§288 = MESMA raiz river (caminho único de resolução de tipo).

**Relationships:** rule 6, §271/§288 river, §334/OBS-009, DECISIONS.md (NaN policy se precisar).

## D-RULE6-BATCH — rule-6 review of the triage: six decisions (maintainer 21/09/2026)

**Date:** 2026-09-21 · **State:** `DECIDED` (maintainer answers in the chat, multiple-choice).

The maintainer reviewed the six rule-6 items of the 14-live triage and decided:

- **§400 — (B) named functions become VALUES — supersedes the batch's (A).**
  The maintainer had voted **(A) keep the rejection + fix the message** in
  `D-CLOSEALL-BATCH` and the batch lane landed it (`cd04246c`, new SEM011
  naming the idiom); in this review she answered **(B)**: a named top-level
  function in argument position converts to the expected `FunctionType`
  (overloads included). **Operative decision: (B)** — the landed (A)
  diagnostic stays only until the conversion lands (it is strictly better than
  the old message and disappears with the surface); surface expansion → rule 11
  gate + queue in the type front, never a drive-by edit. Pending her
  confirmation of the supersession (flagged in the chat 21/09).
- **§188 — (A) reject `String as Int` with a diagnostic (`SEM084`).** `as` is
  numeric conversion, not textual parsing; the canonical path stays
  `math.parseInt`. Kill the false-accept (check-clean → runtime CCE).
- **§288 — (b) parse-time `TypeVariable` + interim rejection (`SEM085`).**
  Annotate type-params as `TypeVariable` at parse inside the generic owner
  (single source of truth); until the pipeline fix lands, reject the composite
  form instead of converting a loud error into a load crash.
- **§302 — (A+B) fix the pin guard + diagnose what remains (`SEM086`).**
  `Type.of`/`toType` parity across locals × fields × params × records (all four
  backends); a bare collection type that stays ambiguous gets an honest
  diagnostic.
- **§268 — (A) implicit `java.lang` via cached probe + honest diagnostic
  (`SEM087`).** `Class.forName("java.lang."+n)` cached; unresolved simple name
  in `extends`/`implements` → diagnostic (no raw super); `Object` →
  `java/lang/Object`.
- **§271 — (B) interim honest diagnostic now; the COMPLETE erasure ABI in the
  1.0 line.** Bridge/descriptor emission across the four backends is a **1.0
  deliverable, not post-1.0** (maintainer 21/09): the release ladder may
  continue through further minor betas (up to 0.9.x if needed) before 1.0; what
  must not ship is the silent `NoSuchMethodError`. Interim: refuse the
  unsupported generic-interface dispatch with a code (mirroring `NAT005`).

**Queue:** DOING (lane 9093 claim); each fix carries its own proof (RED→GREEN)
and updates its ledger section in the same commit.

- **Relationships:** `Related: D-RELEASE-0.5.0-SCOPE, D-RELEASE-1.0, rule 6, rule 11, R6`.

## D-BAREMETAL-BOOT — bare-metal front promoted: `PLAN-BAREMETAL-BOOT` leaves `future/`, R12 overridden, ordered scope = bare-metal with ring0/ring1 (maintainer 22/09/2026)

**Date:** 2026-09-22 · **State:** `DECIDED` (maintainer order in the chat —
autonomous session) · **Overrides:** the R12 gate (SYSTEMS before everything)
**for this front only** — same pattern as §D-UNIVERSAL.

**Decision (maintainer's words):** "você vai assumir PLAN-BAREMETAL-BOOT de
future e vai trazer pra desenvolvimento. quero que desenvolva pra baremetal com
suporte a ring0 e ring1".

1. **Promotion (three-states):** `PLAN-BAREMETAL-BOOT.md` (+`.pt_BR`) moves
   from `future/` to `docs/development/`, status **IN DEVELOPMENT**; the
   `future/README` row is reclassified as moved; `roadmap.md` 1.6 and tracker
   1.7 follow in the same commit.
2. **R12 override** for this front (maintainer's order), recorded here — the
   front opens now; the plan's own §7 order governs (B-0 → B-1 → boot path →
   rings B-6 → …).
3. **Ordered scope:** bare-metal **with ring0/ring1** (x86_64 privilege levels,
   face B-6) — boot at CPL0 + ring1 domains with a falsifiable `#GP` proof. The
   **Kof-level surface** to target a ring1 domain is **NOT** decided here: it is
   a rule-6 decision (rule 11 / Simplicity Law applies) and lands only with the
   maintainer's review.
4. **Queue:** DOING (lane claim, session 9092); the 0.5.0 gate's condition 3
   keeps the plan allowlisted as an in-flight owned plan
   (`D-RELEASE-0.5.0-SCOPE` pattern) — the 0.5.0 cut does not wait for it.

**Evidence:** maintainer message 22/09/2026 (chat, this session); plan file
promoted in the same commit; `check_release_050_gate.sh` ALLOWLIST updated.

- **Relationships:** `Related: D-POLL-19 (D3-A), D-UNIVERSAL (R12 override pattern), D-BOOTSTRAP (north star), rule 6, rule 11, R12`.

## D-VERSIONING-RELEASE — consolidated versioning and release-cut policy: PATCH = no contracted public-surface diff, MINOR mandatory for any public-surface diff pre-1.0, strict SemVer post-1.0, trigger ≠ cut (maintainer approval 22/09/2026)

**Date:** 2026-09-22 · **State:** `DECIDED` (maintainer approval — PR #582
merged 22/09/2026) · **Partially supersedes:** the classification and trigger
rules of `D-RELEASE` — the historical decision is preserved (§1.3); only its
scope is refined. · **Incorporates by reference, without relaxing:**
`D-RELEASE-1.0`, `D-1.0-EDGES`, `D-RELEASE-0.5.0-GATE`,
`D-RELEASE-0.5.0-SCOPE`.

**Scope:** how a change is classified (PATCH/MINOR/MAJOR) and when a release
candidate is evaluated; it does not cut a release by itself.

**Contract:**

1. **Classification is separate from the cut.** A change being a PATCH does not
   authorize publishing a PATCH; classification → evaluation → candidate → gate
   → cut is a pipeline, and a trigger only opens the evaluation.
2. **Pre-1.0 PATCH:** reserved for changes with **no contracted public-surface
   diff** — bugfix, security/regression fix, parity fix to satisfy an existing
   contract, performance/internal refactor, CI/tooling/packaging/docs, or a
   diagnostic improvement that does not change the contract.
   `PUBLIC_CONTRACT_SURFACE_DIFF = 0 → PATCH admissible`.
3. **Pre-1.0 MINOR (mandatory):** any **new or altered contracted public
   surface** — new syntax/operator/observable semantics, relevant public API or
   namespace, public command/flag, public stdlib capability, package/registry/
   interop contract, promoting a target to Supported/Stable, or a deliberate
   approved pre-1.0 breaking change.
   `PUBLIC_CONTRACT_SURFACE_DIFF > 0 → PATCH forbidden, MINOR minimum,
   Decision ID mandatory`.
4. **Pre-1.0 breaking change:** MINOR + recorded decision + impact/migration
   note + proof. Never hidden in a patch because the project is below 1.0.
5. **Post-1.0:** strict SemVer — PATCH backward-compatible fixes, MINOR
   backward-compatible public functionality, MAJOR incompatible contract
   change; compatibility is evaluated across **source**, **artifact/binary**,
   **behavioral** and **cross-target parity** dimensions.
6. **First `1.0.0`:** only when `D-RELEASE-1.0` (EXIT GATE) is fully GREEN on
   the same candidate and no `D-1.0-EDGES` edge is open — never by commit
   count, feature count, project age, or reaching `0.9.9`.
7. **Ordinary trigger (generalized):** `LAST_RELEASE..ACTIVE_BRANCH` (not
   hardcoded to a historical branch) crossing **100–150 commits** opens a
   RELEASE EVALUATION — never an automatic publication.
8. **Extraordinary trigger:** a relevant security fix, a critical regression,
   an urgent distribution/package fix, or an explicit maintainer decision opens
   the evaluation immediately.
9. **Common eligibility-to-cut baseline** (line-specific gates still prevail):
   candidate SHA identified; VERSION/pom/version resource consistent;
   CHANGELOG/release metadata coherent; required suite GREEN on the candidate;
   PATCH/MINOR/MAJOR classification proven; necessary decisions recorded;
   applicable blockers resolved; real package validated when applicable;
   artifact trust/provenance per `D-ARTIFACT-TRUST`; EN/PT docs synced.
10. **Future mechanical gate (backlog, not this decision):**
    `release-surface-gate` comparing the last release against the candidate
    across grammar, language-reference, operators, typing rules, Stable stdlib
    catalog, contractual CLI commands/flags, package/registry contract and
    Stable Target Surface; `PUBLIC_SURFACE_DIFF > 0` rejects PATCH.

**Materialized text:** `docs/distribution/VERSIONING.md` (+PT) describes the
current state and points here; `D-RELEASE` keeps its history with a
relationship note; `D-RELEASE-0.5.0-GATE` and `D-RELEASE-1.0` are unchanged.

**Evidence:** `docs/distribution/PROPOSAL-VERSIONING-RELEASE.md` (+PT) — the
KOF-first evidence block and external grounding; maintainer approval (PR #582
merged 22/09/2026; closed-issue record).

- **Relationships:** `Related: D-RELEASE (partially superseded), D-RELEASE-1.0, D-1.0-EDGES, D-RELEASE-0.5.0-GATE, D-RELEASE-0.5.0-SCOPE, D-ARTIFACT-TRUST, D-BRANCH-0.5.0, D-VERSION-BUMP-0.5.0, rule 6`.

## D-TECHDEBT-23/09 — tech-debt ledger rulings (maintainer 23/09/2026, multiple-choice)

**Date:** 2026-09-23 · **State:** `DECIDED` (maintainer answers in chat,
multiple-choice — "chama no pente") · **Source:** `docs/development/tech-debt.md`
§5 (6 open questions) → answers: §248 = port JS+Native · §271 = emit bridges ·
§278 = port the stacks · §423 = schedule the port · split = all-in-batch ·
D6-1=B = open now.

1. **§248 — interface default methods: PORT JS + NATIVE** (not JVM-only).
   Queue: compiler lane — emit defaults on JS + Native with parity proof.
2. **§271 — generic interface dispatch: EMIT BRIDGES** (erasure ABI line
   decided now). Queue: compiler lane — bridge methods on generic-interface
   impls.
3. **§278 — Android: PORT THE STACKS** (`kof.security`/`kof.gpu` run on
   Android; `kof.db`/`kof.orm` already fixed via DB-2). Queue: gaps-db lane.
4. **§423 — channels cross: SCHEDULE THE PORT** (riscv64/aarch64
   `kof_channel_*` runtime + qemu proof). Queue: nat lane.
5. **Split order: ALL IN BATCH** — `NativeBackend` 603 (RED) +
   `CompilerPipeline` 588 + `RuntimeOrm7` 585 in one batch (precedent
   §442/§446, behavior-preserving).
6. **D6-1=B — OPEN NOW** (mutable `struct` by-ref front opens under
   spec-first + Simplicity Law, rule 11). Queue: FFI lane — design §4/§6
   for review, then parser/typer diff.

**Evidence:** maintainer multiple-choice answers 23/09 (this session);
`docs/development/tech-debt.md` §5.

- **Relationships:** `Related: tech-debt.md §5, rule 6, rule 11, R6, D-FFI-STRUCT-B, D-RELEASE-0.5.0-GATE (cond. 2/7).`

## D-DEBT-SCOUT — KOF Technical Debt Scout front opens: Wave 1 (deterministic, shadow-only) only, no Issue-publish capability (user-directed, 23/09/2026)

> **24/09/2026 — KILLED by the maintainer:** the debt was measured zeroed
> (every live §NNN the ledger tracked is ✅ in `known-bugs.md`; size gate
> green) and the `tech-debt`/`technical-debt`/`debt-scout` tooling, workflow
> and tests were removed by her order. This decision stays as history.

**Date:** 2026-09-23 · **State:** `DECIDED` (scope, not implementation
detail) · **Source:** two research documents supplied by the user in this
session (`KOF_TECHNICAL_DEBT_SCOUT_AGENT_V1_BACKUP.md`,
`KOF_TECHNICAL_DEBT_SCOUT_AGENT_V2.md`) — V2 supersedes V1 per its own
§0. Condensed operating contract landed at
`docs/development/technical-debt/DEBT_SCOUT_CONTRACT.md` in the same
commit as this entry.

**Decision:** a new tooling front — automated discovery/documentation of
historical technical debt — is authorized, scoped strictly to **Wave 1**
of the V2 design: deterministic detectors only (no LLM in the loop), a
stable candidate schema + two fingerprints (finding/debt), branch/ref
discovery that never hardcodes a version string, and a scan orchestrator.
**No script and no workflow in this landing may call the GitHub Issues
write API, and no workflow grants this system `issues: write`.** The
Scout is a feed into human triage (today: `docs/development/tech-debt.md`,
the maintainer-owned ledger) — it is never a second writer of that
ledger, and it does not decide language contract, does not implement
fixes, does not close Issues, does not merge PRs (rule 6 applies to
anything the Scout surfaces that would require a contract change).

**Why a decision record for tooling, not just an edit:** this front can,
in later waves the source documents describe, gain the ability to open
GitHub Issues autonomously. That capability is explicitly **not**
authorized by this entry — moving past Wave 1 (SARIF upload, the Debt
Inbox, and especially any workflow permission change toward
`issues: write` for this system) requires its own `DECISIONS.md` entry
recording the maintainer's phase authorization (`DEBT_SCOUT_CONTRACT.md`
§7, phases S1/S2/S3), the same way `D-ARTIFACT-TRUST` gated
`scripts/agent-close-issue.sh`'s write path.

**Rejected for this landing:** copying either 104-section source document
verbatim into the repo (violates the "small parts" lesson, `AGENTS.md`
§"Lesson learned (09/04)"); a parallel risk/evidence/dispatch framework
(V2 §46 already mandates reusing `scripts/agent-*.sh`); any auto-publish
path before a trust-rollout phase is explicitly authorized.

**Evidence:** the two source documents (session-supplied, 2026-09-23);
existing agent infrastructure (`scripts/agent-common.sh`,
`agent-dispatch-gate.sh`, `agent-state-fingerprint.sh`, `agent-risk.sh`,
`agent-evidence.sh`, `agent-verify.sh`) confirmed present and reusable;
`docs/development/tech-debt.md` (opened 23/09) confirmed as the existing
manual ledger this tooling feeds rather than duplicates; no
`technical-debt` GitHub label exists yet (`gh label list`), so any future
labeling stays in the candidate body per the contract, not a new
taxonomy invented on the fly.

- **Relationships:** `Related: AGENTS.md rule 6/8/9/10/11, R6,
  D-ARTIFACT-TRUST, D-KOF-FIRST, tech-debt.md.`

## D-DEBT-SCOUT-W2 — Debt Scout Wave 2 authorized: evidence qualification, root-cause clustering, principal/interest/lock-in, C2/C3 classifier, Debt Inbox, SARIF — still shadow, still zero Issue-publish (user-directed, 23/09/2026)

**Date:** 2026-09-23 · **State:** `DECIDED` · **Source:** user said "segue
para wave2" after reviewing the Wave 1 landing (contract `DEBT_SCOUT_
CONTRACT.md` §11, `KOF_TECHNICAL_DEBT_SCOUT_AGENT_V2.md` §94).

**Decision:** Wave 2 of the V2 design is authorized: a deterministic
KOF-first context builder, root-cause clustering (by `debt_fingerprint`),
the principal/interest/lock-in vector (never a single score, contract
§11/§12), a C2/C3 confidence classifier that only promotes a cluster
past `C1` when the mandatory-evidence checklist (contract §7, C3
requirements) is actually satisfied — never by many weak signals — a
Debt Inbox for C2 findings without a code location, and a SARIF writer
for C0/C1/C2 findings that DO have a location (contract §37: "prefer
SARIF/code scanning over opening an Issue" for exactly that case).

**What is explicitly still NOT authorized by this entry:** the C3
publisher, any `issues: write` grant anywhere, any push/schedule
trigger. Wave 2 stays `mode: shadow` end to end — `issues_created`
remains a structural `0`, proven the same way Wave 1 proved it (the
report/workflow asserts it, not merely intends it). Advancing to Wave 3
(§95 of the source spec: the canary C3 publisher) needs its own
`DECISIONS.md` entry with the maintainer's phase-S1 authorization
(`DEBT_SCOUT_CONTRACT.md` §7), exactly as `D-DEBT-SCOUT` already said.

**New privilege this wave introduces, scoped tightly:** the workflow's
`scan` job gains `security-events: write` (SARIF upload only,
`github/codeql-action/upload-sarif`) — still zero `issues: write`
anywhere. This is the same least-privilege discipline
`D-ARTIFACT-TRUST` already applies to every other workflow in this repo.

**Evidence:** Wave 1's own real run (`https://github.com/KofLang/Kof4j/
actions/runs/35839064175`) measured that of 33 real candidates (32 `C0`
SATD, 1 `C1` branch-drift), **zero** qualified for an Issue under the
contract's own gates when triaged by hand — that triage is exactly what
Wave 2's classifier now does mechanically, so the next run's result is
checkable without manual re-triage every time.

- **Relationships:** `Related: D-DEBT-SCOUT, DEBT_SCOUT_CONTRACT.md §7/§11/§37,
  D-ARTIFACT-TRUST, rule 6.`

## D-BAREMETAL-RING1-SURFACE — ring1 domain surface = a built-in `ring1(fn)` marker (no new syntax); compiler lowers to the CPL0→CPL1 transition (maintainer 23/09/2026)

**Date:** 2026-09-23 · **State:** `DECIDED` (maintainer answered the
multiple-choice in the chat, option "Built-in marker function") · **Source:**
B-6.1 landed (Kof-owned GDT/TSS/IDT under OVMF); B-6.2 (CPL1 entry) was blocked
on this rule-6 decision (`D-BAREMETAL-BOOT` §3 left the Kof-level surface open).

**Decision:** the way Kof code targets a **ring1 domain** is a **built-in
marker function `ring1(fn)`** — a call whose callee name is reserved and lowered
by the compiler; **no new grammar, keyword, block, annotation or modifier**.
`ring1(fn)` runs the given Kof function value at **CPL1** and returns its result
to CPL0: the runtime performs the `iretq` transition (`CS=0x18` RPL=1,
`SS=0x20`, TSS `rsp0` backing the trap back), calls the function, and the
privileged-instruction fault path returns through a ring0 gate.

**Rationale (Simplicity Law, rule 11):** Kof declares the **intent**
(`ring1(fn)`), the platform does the mechanism. A built-in call is the smallest
possible surface — it parses as an ordinary call, exists in zero grammar
productions, and reads exactly as a human would write it. A keyword/block was
rejected as a larger, less Kof-y surface.

**Constraints (frozen semantics untouched):**
1. The builtin is meaningful **only** on the x86_64 bare-metal/UEFI ring profile
   (`NativeProfile.UEFI_RING`/its boot path). On every other target/backend it
   must fail with a **named diagnostic** (`NATIVE003`), never a silent no-op
   (R6) — a CPL1 domain does not exist on JVM/JS/riscv/aarch64 today (R7
   honest scope).
2. **Additive**: code that does not call `ring1` is unaffected; no change to
   operators, precedence, evaluation order or any existing API.
3. `ring1` takes a **function value** (a Kof function/lambda); it is not a
   statement keyword, so it composes as an expression returning the function's
   result.

**Queue:** B-6.2 (`PLAN-BAREMETAL-BOOT`), lane baremetal, session 9092; B-6.3
(`#GP` proof in the ring1 domain + GDT-descriptor sabotage) follows once B-6.2
is proven. The 0.5.0 gate's condition 3 keeps the plan allowlisted (in-flight
owned plan, `D-BAREMETAL-BOOT` pattern) — the cut does not wait for it.

**Evidence:** maintainer multiple-choice answer in the chat, 23/09/2026 (this
session), option "Built-in marker function (Recommended)"; recorded here before
any B-6.2 code (rule 6: a front is not attacked without a locked decision).

- **Relationships:** `Related: D-BAREMETAL-BOOT, D-UNIVERSAL, D-BOOTSTRAP, rule 6, rule 11, R6, R7`.

## D-BAREMETAL-BODIES — B-5 platform bodies authorized (BIOS time via RTC first) and B-4 (MCU) authorized with its collector prerequisite (maintainer 24/09/2026)

**Date:** 2026-09-24 · **State:** `DECIDED` (maintainer answer in the chat,
this session: "autorizo 1 e 2") · **Extends:** `D-BAREMETAL-BOOT` (the front's
plan `PLAN-BAREMETAL-BOOT` §B-4 / §B-5)

**Decision (maintainer's words):** *"autorizo 1 e 2"* —

1. **B-5 (platform bodies)** may be implemented, starting with the **BIOS**
   face: `kof_plat_time` filled from the CMOS **RTC** (I/O ports `0x70`/`0x71`),
   returning the epoch time the existing ABI already expects (`ts[0]=tv_sec`,
   `ts[1]=tv_nsec`), so a Kof `time.now()` runs bare under SeaBIOS. Capabilities
   that still have no body stay **named refusals** (R6), never a silent stub;
   a BIOS refusal must print a **readable ASCII** diagnostic (not the UEFI
   UTF-16 form, which COM1 renders as interleaved-NUL garbage).
2. **B-4 (MCU)** is authorized as a front; it remains **blocked by its hard
   prerequisite** — the GC collector `native-multiarch.md` **G-4/G-5**
   (KB-scale RAM) — which is developed **first**, in slices.

**Not relaxed:** nothing. The **Kof surface/semantics are untouched** — only the
`kof_plat_*` HAL bodies behind the existing ABI change (no grammar, operator,
type-model or frozen-contract change); every still-missing path keeps a **named
diagnostic** (R6/R7); the B-6 ring `#GP`/CPL semantics are unchanged.

**Queue:** `roadmap.md` §23 (baremetal front) + a DOING claim in the same
commit; **B-5 BIOS time = first slice** (proof: `BiosBootE2ETest` boots a Kof
`time.now()` under SeaBIOS); **B-4 follows the collector G-4/G-5**.

**Evidence:** maintainer message 24/09/2026 (chat, this session); recorded here
**before** any B-5/B-4 code (rule 6: a front is not attacked without a locked
decision).

- **Relationships:** `Related: D-BAREMETAL-BOOT, D-UNIVERSAL, D-BOOTSTRAP, rule 6, rule 11, R6, R7, R12`.

## D-BAREMETAL-MCU-GC — B-4 does NOT close before the collector G-4/G-5 is ported to 32-bit; MCU `kof_plat_time` = named wall refusal + SysTick monotonic (maintainer 24/09/2026)

**Date:** 2026-09-24 · **State:** `DECIDED` (maintainer answers in the chat,
this session, 24/09) · **Extends:** `D-BAREMETAL-BODIES` (its item 2 left B-4
blocked by the collector; this locks the closure criterion and the MCU time
semantics)

**Decision (maintainer's answers, in order):**

1. **B-4 does NOT close yet.** The minimal print-only MCU slice (hello +
   vector-table reset path asserted on both riscv32 and Cortex-M3, everything
   else honestly refused with `NATIVE002`/`CONC003`) satisfies the literal
   §B-4 acceptance, but it is **not** the closing criterion: the collector
   `native-multiarch.md` **G-4/G-5** must be **ported to the 32-bit MCU**
   (allocation + mark/sweep + a long-running proof that recycles under
   `qemu-system-riscv32 -M virt` and/or `qemu-system-arm -M mps2-an385`) before
   `PLAN-BAREMETAL-BOOT` leaves `docs/development/`. The heap is sized by the
   linker script (KB-scale), not the fixed 262 144 B `.bss` arena.
2. **MCU `kof_plat_time` semantics** (an RTC-less MCU): the **wall** clock
   (`time.now()`) is a **named refusal** (R6/R7) — never a fake epoch; the
   **monotonic** path (`kof_plat_time_mono`, and `time.sleep` where it applies)
   is provided by the **SysTick** counter with `boot = 0`.

**Not relaxed:** nothing. The **Kof surface/semantics are untouched** — only
32-bit runtime internals and the `kof_plat_*` HAL bodies behind the existing
ABI; every still-missing path keeps a **named diagnostic** (R6/R7).

**Queue:** `roadmap.md` §23 (baremetal front) + a DOING claim in the same
commit; **first slice = the 32-bit allocator + collector port** (proof under
qemu), then the MCU time bodies.

**Evidence:** maintainer answers 24/09/2026 (chat, this session), to the two
options presented after the B-4.3 sl.1 landing; recorded here **before** any
collector/MCU-time code (rule 6).

- **Relationships:** `Related: D-BAREMETAL-BOOT, D-BAREMETAL-BODIES, D-UNIVERSAL, D-BOOTSTRAP, rule 6, rule 11, R6, R7`.

## D-FULL-PARITY-050 — Full platform parity is the ABSOLUTE rule of every plan and an 0.5.0 BLOCKER: the release does not cut while `docs/development/parity/PARITY-GAPS.md` has an open row (maintainer 24/09/2026)

**Date:** 2026-09-24 · **State:** `DECIDED` (maintainer messages in the chat,
this session, 24/09) · **Extends:** `D-UNIVERSAL`, `D-RELEASE-0.5.0-SCOPE`,
platform invariant R7 ("honest scope per target")

**Decision (maintainer's words, in order):** *"A PLATAFORMA UNIVERSAL TA
IMPLEMENTADA SÓ PRA JVM? ISSO É INACEITAVEL. TUDO TEM QUE TER PARIDADE TOTAL.
DOCUMENTE ISSO COMO IMPEDITIVO PARA 0.5.0"*; *"A REGRA ABSOLUTA PRA QUALQUER
PLANO É A PARIDADE TOTAL"*; *"APROVEITA E PESQUISA TUDO QUE TA COM PARIDADE
PARCIAL E BOTA EM docs/development/parity PARIDADE TOTAL É INDISPENSAVEL"*.

**What was decided:**

1. **Full parity (JVM/Script ≡ Native x86-64 ≡ Native riscv64/aarch64 ≡ JS,
   byte/golden vs the JVM oracle) is the ABSOLUTE rule for every plan** — a
   new front that lands JVM-first MUST carry its parity plan in the same
   queue item; "declared gap" is a tracking state, never an acceptance state.
2. **0.5.0 release condition 8 (full_parity) is a BLOCKER:** the ledger
   `docs/development/parity/PARITY-GAPS.md`(+PT) must have **0 open rows** at
   cut. The ledger was created measured (24/09) with the 16 open rows below
   (gap codes from `DomainGapCodesTest`, `Kof*.java`, the stdlib parity table
   and `known-bugs.md`): process/shell (`PROC001`), ssh (no code yet —
   catalog), media (`MEDIA001`/`MEDIA003`), mq (`MQ001`), gpu JS + cross
   golden (`GPU001`), observability cross golden (`OBS003`), time cross
   (`TIME002`/`TIME004`), cache/config/log cross golden + interpreter log
   (`CONF001`), `math.pow` cross (`MATH001`), `strings.reverse` non-ASCII +
   five String methods (`NAT-STR01`/`STR003`), web T1 native (`WEB00x`),
   `kof.io` cross (`NAT006`/`NAT007`), security cross (`SECN001/003/004/005`),
   `orm.*` native (`ORM001`), `db.*` native query/prepared (`DB001`).
3. **Existing release condition 1 (100% parity) measured on the CONFORMANCE
   MATRIX stays** — the ledger ADDS the long tail the matrix never covered
   (unmeasured goldens count as OPEN, Q5: no false green).
4. **Definition of done per row:** face compiles + golden/E2E byte parity +
   docs tables updated in the SAME commit + row removed in the SAME commit.
5. **Every FUTURE plan document inherits the rule:** a plan without a parity
   section (targets × proof) is misclassified (three-states rule) — agents
   must add it or route the gap to this ledger.

**Evidence:** maintainer messages 24/09/2026 (chat, this session); ledger
created with the full measured state in the same commit; machine gate wired
in `scripts/check_release_050_gate.sh` (`full_parity`).

- **Relationships:** `Related: D-UNIVERSAL, D-RELEASE-0.5.0-SCOPE, D-DB-GAPS,
  D-GRAFICOS-GAMING, R6, R7, Q5, rule 6`.

## D-MEMORY-SAFETY — Memory safety front (ownership/lifetime/borrowing/aliasing/FFI) opened in `docs/development/`, owned by the parity lane; investigation first, core untouched until the current queue closes (maintainer 25/09/2026)

**Decision (maintainer, 25/09/2026, chat):** the memory-safety brief
(ownership, lifetime, borrowing, mutable/shared aliasing, use-after-free,
double-free, dangling references, escape analysis, closure capture, move
semantics, resource destruction, data races, native pointers, FFI/C-C++/Rust
boundaries, JVM/Native/JS/WASM differences) enters `docs/development/` as a
LIVE front — not `future/` — and the parity lane **owns** it
("pode botar em docs/development e ja assumir essa frente").

**Constraints locked by the brief (verbatim constraints, not agent
paraphrase):**

1. **Kof already HAS null safety** — values are non-null by default and
   `null` exists only where the type/semantics allow it. The investigation
   must NOT reinvent, replace, or duplicate that system; it only studies the
   nullability × ownership × lifetime × borrowing interaction.
2. **Investigate Kof-first** — never assume the compiler works like Rust,
   C++, Java, Kotlin, Swift or Zig; no borrowing/copied solution without
   verifying it fits Kof's semantic model (rule 10: KOF-first,
   external-second — external sources contribute principles, never syntax).
3. **Architecture before code** — Phase 0 produces
   `docs/spec/memory-safety-investigation.md` (current state, risks,
   implicit lifetime model, fragile points, proposal, alternatives, backend
   impact, compatibility impact, incremental plan) BEFORE any compiler edit.
   Phase 1 produces the formal spec `docs/spec/memory-safety.md`
   (Ownership, Lifetime, Borrowing, Aliasing, Mutability, Move, Copy, Clone,
   Drop/Destruction, Escape, Closure Capture, Concurrency, FFI, Unsafe
   Boundaries). The semantics exist before the implementation.
4. **The implementation phase only starts after the current queue closes**
   (the brief's final line: "Esse trabalho só começa depois que a fila atual
   estiver concluída") — until then: investigation, spec drafts and
   compiler-internal infrastructure studies only, ZERO premature core edits.
5. **No accidental complexity on the language surface** (rule 11): the model
   must be strong enough to make whole bug classes impossible without
   turning Kof into an endless chain of lifetime annotations; success =
   the compiler can say "this program cannot produce this class of error"
   (use-after-free, double-free, dangling reference, invalid lifetime
   escape, unsafe mutable aliasing, unexpected null, accidental data race)
   with an explicit boundary where a proof is impossible.
6. **Cross-target by construction** — JVM, Native, JS and the planned WASM
   backend must express the SAME Kof semantics (GC on JVM/JS never excuses
   aliasing/mutability/lifetime divergence); FFI boundaries must define
   owner/keeper/free-writer/guardian for every crossing kind.
7. **Diagnostics are part of the feature** — every rule lands with
   valid/invalid/expected-diagnostic/regression/per-backend tests (small
   suites per domain, adapted to the real test tree, no giant suite).
8. **Forbidden:** copying Rust's borrow checker, inventing syntax (`let`,
   `const`, foreign move markers), a null-safety rewrite, a big-bang
   compiler refactor, single-backend ownership, hiding ownership problems
   in the runtime.

**Phase plan (from the brief, machine-checkable order):** Fase 0
investigation doc → Fase 1 spec (`docs/spec/memory-safety.md`) → Fase 2
compiler-internal structures (ownership/lifetime/borrow/alias/mutability/
escape/resource-state representations) → Fase 3 first guarantees
(use-after-move, dangling refs, invalid escapes, mutable aliasing, double
ownership/destruction) → Fase 4 closures/async → Fase 5 Native+FFI →
Fase 6 JVM/JS/WASM parity of the semantics. Each phase gates the next; a
phase without its proof tests does not close.

**Evidence:** maintainer messages 25/09/2026 (chat, this session): the full
brief (sections 1–27) + "pode botar em docs/development e ja assumir essa
frente"; plan doc created in the same commit
(`docs/development/memory-safety-plan.md` EN+PT).

- **Relationships:** `Related: D-KOF-FIRST, D-FULL-PARITY-050 (the memory
  front runs AFTER the parity blocker unless the maintainer says otherwise),
  rule 6 (frozen semantics — ownership semantics that change evaluation
  order/operator contracts go through the maintainer), rule 8 (Kof is not
  Java/Rust), rule 11 (Simplicity Law), SG/D-KOF-AS-CLOUD, R6, R7`.

### Update 26/09 — Fase 1 CLOSED, Fase 2 UNLOCKED (maintainer, chat)

**Phase 1 CLOSED 25/09** (option A — spec accepted; mirrors landed at
`8d5634216`/`0670f2312`). At 25/09 the maintainer chose option `J` (the
front queues behind the current queue); on 26/09 she unlocked it — her
words: **"fase 2 destravada"**. **Phase 2 (compiler infrastructure) = IN
DEVELOPMENT**, owner = parity lane (this lane's claim in `DOING.md` EN+PT,
same commit). Scope locked: compiler-internal representations in package
`dev.kof.compiler.memory` — ownership/lifetime/borrowing/aliasing/
mutability/escape/resource-state + the `MEMxxx` diagnostic-code enum from
the spec §1–§10 — structures and tests ONLY; **zero behavior change (suite
byte-green)**; emission/wiring of diagnostics belongs to Phase 3. Forbidden
by the brief still holds (no borrow-checker copy, no new syntax, no
null-safety rewrite).

**Evidence:** maintainer message 26/09/2026 (chat, autonomous session);
plan+spec flips EN+PT in the same commit.

**See also:** `D-DECISION-BATCH-2609` call (4) — the same maintainer decision
recorded in batch by the watcher lane; this block is the scope-lock for the
implementation, not a second decision.


## D-DECISION-BATCH-2609 — maintainer's four-call batch: §493 Native throws like JVM; #619 merge HELD; #624 merge GO; memory-safety Fase 2 UNLOCKED (maintainer 26/09/2026)

Four decisions taken in one pass via the session's multiple-choice prompt
(26/09/2026, ~02:40):

1. **§493 — the law is the JVM behavior**: a failed `orm.delete`/`deleteAll`
   over MySQL (dead/invalid connection) must THROW the error string on every
   target. Native x86-64 and the cross (riscv64/aarch64) currently return
   `true` — that is the bug to fix (RuntimeDb5/`RtB75`/`RtB54` error paths).
   Consistent with "exceptions are Strings", R6 (never silent) and the
   db-parity-plan's Acceptance (no silent divergence). **Queue:** close §493
   in the ledger with RED→GREEN cross-target proof (server-down fixture),
   then the plan's conclusion rule moves `db-parity-plan` to `docs/stdlib/`.
   The lane for the code is gaps-db/native-runtime; this entry is the gate.
2. **PR #619 (beta-0.5.0 → main) — merge HELD.** The branch keeps receiving
   work; the release act stays with the maintainer (rule 10). No action for
   any agent beyond keeping `beta-0.5.0` green.
3. **PR #624 (#623, MP4 extended-size box) — merge GO.** Verified before
   landing: single commit `d7f0745fc` from `PublioSantos/Kof4j`,
   APPROVED; its two red checks were stale-base artifacts (branch at
   25/09 10:42, before §506/§507/§508 fixes). Merge simulated on a throwaway
   branch over tip `c298406e2`: `run-agent-tests.sh` VERDE + media battery
   17/0F/0E. Merged as a merge commit preserving the author's SHA.
4. **memory-safety Fase 2 — AUTHORIZED now.** The "opção J" hold (zero
   premature core edits) is lifted by the maintainer: the parity lane (owner
   of the front per `D-MEMORY-SAFETY`) proceeds to Fase 2 — compiler-internal
   structures for ownership/lifetime/borrow/alias/mutability/escape/
   resource-state. Fase 1 gates stay satisfied (`docs/spec/memory-safety.md`
   landed 25/09); Fase 3+ remain gated by Fase 2's proof tests.

## D-QUALITY-PIPELINE-2609 — branch pipeline = quality pipeline (lab → testing → prerelease → stable → release/x.y.z → tag); lab sem CI por push; gates de promoção 80%/100%/100%+CLOSEALL; migração ATÔMICA pós-0.5.0 com `release/0.5.0` de piloto (maintainer 26/09/2026)

**Evidence:** issue #626 (proposal by the maintainer, 26/09 04:52Z) +
maintainer's reply 26/09 05:53Z accepting the technical review of the
parity/quality lane ("desenho fechado conceitualmente como uma quality
pipeline").

(The sister-lane comment on #626 recorded the same design under the name
`D-QUALITY-PIPELINE`; it is the SAME decision and this dated entry is the
single record — one representation per claim. `794aa4721` had committed the
stash-pop conflict markers; `e29ba47b3` merged both sides into this entry.)
**Decision (option: staged quality pipeline, not per-environment branches):**

| Stage | Role | CI | Break? | Publishable? |
|---|---|---|---|---|
| `lab` | experimentation | **NO CI per push** (heavy validation moves to the promotion) | contract may change (see open point below) | no |
| `testing` | integration/QA | full suite at `lab→testing` promotion (first formal gate) | ideally no | potentially |
| `prerelease` | public candidate | **≥80% green to enter from `testing`; 100% green to advance** | no features | yes |
| `stable` | closed contract | 100% + version closing criteria (CLOSEALL+docs) | no | yes |
| `release/x.y.z` | packaging only (temporary, from `stable`) | final validations | no | yes |
| tag | the public contract | — | — | — |

- **Hotfix on `stable`:** PR + explicit backport + re-validation before it
  returns to `stable` — never a side door for development.
- **Promotion proof is objective:** the `release-beta-0.5.0-prep.md`
  checklist (cond.7 = `check_known_bugs_status.sh` live-empty + conformance
  matrix) becomes the definition-of-promotion.
- **Timing (hard):** migration happens ONLY after the 0.5.0 cycle closes on
  the current branch model; `release/0.5.0` serves as the pilot of the last
  stage. The cutover itself is ONE atomic change: branches + CI + scripts +
  `AGENTS.md` + `DOING.md` + `DECISIONS.md` + automations (partial migration
  = agents pushing to the wrong place — maintainer's words).
- **OPEN POINT (not decided):** whether `lab` keeps the zero-regression
  floor (rule 8) even without per-push CI — raised by the lane review
  ("no quebrável", precedents `7f174a6f`); the maintainer's reply did not
  touch it; settle it in the cutover plan, do not assume either way.
- **Until then NOTHING changes (historical — this bullet predates the
  cutover and is superseded by the CUTOVER EXECUTED below / `D-BRANCH-PIPELINE`):**
  `beta-0.5.0` was the active branch (`D-BRANCH-0.5.0`, `SUPERSEDED` 28/09);
  agents kept pushing to it; #619 stays HELD (rule 10). Queue: roadmap §23 `TIER 14`.
- **OPEN POINT #2 (the ≥80% denominator):** branch protection measures
  checks as booleans — a percentage cannot be enforced by protection; it must
  live in a promotion script over a FIXED, enumerable list of checks
  (Build+Tests, Native cross, Structural, kof.io x3, CodeQL Gate, bots), and
  an "80% that tolerates reds" must classify WHICH reds: functional (always
  block — rule 8) vs documented environment/toolchain (named whitelist).
  Proposed formula for the migration commit: `testing -> prerelease` = zero
  functional reds + at most N named environment-reds in the whitelist (the
  measurable ≈80%); `prerelease -> stable` = 100% on the same enumeration.
  Natural generalization of `check_release_050_gate.sh` (already this shape
  for the release). Answered on the issue (lane comment).
- **CUTOVER EXECUTED (28/09/2026, maintainer: "0.5.0 acabou de ser mergeada na
  main, pode começar"):** `lab`/`testing`/`prerelease`/`stable` created from
  `origin/main` (`317d9f6b1`); `beta-*` frozen; CI re-pointed (`codeql.yml`,
  `kof-*-bot*.yml`, `pr-base-guard.yml`, `dependabot.yml`, `scripts/codeql-gate.sh`);
  broadcast + migration instructions in issue #647. Maintainer answers:
  (a) the cutover happens NOW (0.5.0 closed); (b) `lab` KEEPS the zero-regression
  floor (rule 8) even without per-push CI — **OPEN POINT resolved**; (c) the
  `≥80%` denominator is DROPPED: every promotion is 100%. `AGENTS.md`/`.pt_BR.md`
  now declare `D-BRANCH-PIPELINE: active branch = `lab``. Promotion automation is
  `TIER 14.3` (roadmap).

## D-COMPLETE-FIRST — choice rule for automatic decisions: the solution that is idiomatic AND complete (no stub, no giving up, no accepting a gap, full parity) is THE option the lanes follow; thin/stub/gap-accept alternatives are not options (maintainer 26/09/2026)

**Date:** 2026-09-26 · **State:** `DECIDED` (maintainer, chat, this session)

**Decision (maintainer's words, in order):** "me da soluções idiomaticas,
nada de desistir ou assumir gap" → "muito menos stub" → "percebe que depois
das minhas reclamações vc me deu só uma opção? **é ela q vc segue**".

**The rule, operative:** when a rule-6 front is triaged, the **complete
idiomatic form** — the one that would be presented as the real contract
(analysis pass, not a lone diagnostic; the full engine, not an `eval`-returns-
String facade; the runner wired end to end, not a half flag; deterministic
lifecycle release, not a GC hope) — **is the decision the lane follows**,
without sending it back to a vote. What is NOT a choice: giving up,
"accepting a gap" as the answer to a legitimate need, stubs/facades/thin
APIs, or a slice that pretends the rest exists. Legitimate target scope
(R7: JVM-first with a NAMED diagnostic on the impossible-in-this-target
path) is not a gap — it is complete delivery of its declared scope. When
two or more genuinely complete options exist (different full contracts),
the maintainer still votes among them; the default when exactly one is
complete is to follow it immediately.

**Operative consequences — the four open rule-6 questions of 26/09 resolve
as the full option of each** (each lands as a complete package, never a
stub, with proof per target before closing):

1. **O-02 × N-02 (memory-safety Fase 3):** resolved **without null
   literal** — the ownership/lifetime analysis pass in the compiler pipeline
   with MEM001/MEM002/MEM005 emission and interaction cases across the 4
   targets. The plan's DECISION REQUEST is CLOSED by this rule; Fase 3 is
   UNLOCKED.
2. **X2 (Python/R interop):** the full `interop` official package — typed
   bidirectional marshalling (Int/Double/Bool/String/List/Map/record↔JSON),
   real process management (spawn, stdin/stdout, timeout, exit, cancel),
   session state, named `INTEROP00x` errors, E2E per target, corpus
   (`training/idioms/interop.md` + `learn/` + parity matrix) synchronized.
   Born `experimental` per R5.
   **Progress 26/09 — fatia 1 LANDED (item still open, fatias 2+ pending):**
   the Python engine `KofPy` shipped as a Kof-written host (`interop-py-host.kf`)
   over `process.spawn` + typed `json.decode<T>` — surface `var py = KofPy(src)`
   + `py.callInt/callDouble/callBool/callString(fn, listOf(...))`, goldens
   JVM≡x86≡JS≡SCRIPT measured byte-identical, named `INTEROP004`/`INTEROP006`,
   `INTEROP005` refusal where the face is unproven (cross blocked by §514,
   OPEN — lane native; ANDROID/MCU/RISCV32 by R7). The session-is-the-source
   replay contract replaces long-lived `python -` (measured impossible without
   EOF); a live-session surface needs a named handle type = rule 6 future slice.
   The engine's Double args exposed and FIXED §513 (json element tag collapse)
   at the root with a JVM-oracle regression. Remaining: records↔JSON + R engine
   (fatia 2), timeout/cancel (fatia 3), cross re-entry with §514 (fatia 4),
   corpus DoD + promotion (fatia 5).
3. **X8 fatia 3 (named suites):** the `test` primitive's optional tag
   flowing parser→typer→IR→runner catalog (single source), `kof test --tag`
   with real filtering, conditional setup/teardown as functions (failing
   setup skips its tests, named), E2E CLI goldens, honest refusal where the
   runner does not exist.
   **LANDED (26/09):** the tag = extra string literals in the declaration
   (`test "n", "smoke" { }`) — zero new syntax (rule 11; SG-023 iii surface
   kept); the filter is decided at COMPILE time in `TestHarnessBuilder`, so all
   four targets execute the identical filtered catalog (rule 5 by construction);
   `setup`/`teardown` are ordinary zero-arg functions (setup that throws = named
   SKIP; teardown runs via `finally` even on failing tests). Evidence:
   `TestTagsE2ETest` 10/10 + `CmdTestTagTest` 4/4 + legacy `StructuredTestE2ETest`
   unchanged + corpus (`learn/23-testing` EN+PT, `training/tooling/cli` EN+PT).
4. **`kof.ui` auto-unsubscribe:** deterministic release at component
   **unmount** (the path that already walks the tree), leak locks
   (`subscriptionsLive()`/`storesLive()` = 0 after mount/unmount × N),
   subscription outside a component stays ownerless and manual by design,
   JVM/Native keep the documented UI=KofJS no-op parity.
   **LANDED (26/09):** the subscription half had already shipped as
   `D-UI-AUTOUNSUB` (A); this item completed the lifecycle — a **Store created
   during a component's lifecycle is owned by it and dies at unmount** (entry
   deleted: value + carried subscriptions go together; `AppState` never
   attributed, app by definition), and the probe trio `uiNodesLive()` /
   `storesLive()` / **`subscriptionsLive()`** (new — 7 wiring points, JVM face
   honestly 0, Native asm 0, Script inherits UI002) locks it. Evidence:
   `UiLeakLockE2ETest` 6/6 (component-owned store + sub die measured
   `1,1→0,0`; app-scope control survives `1,1`; AppState survives; manual
   unsubscribe counts exactly `2→1→0`; 10k-cycle stress `0\n0\n0` on
   JVM/Native/JS) + existing UI battery 83/83 untouched (rule 2). Corpus:
   `training/idioms/ui`(+PT) stale §279 claim fixed + leak-lock idiom,
   `learn/35-kof-ui`(+PT), `docs/ui/architecture`(+PT) §2.6/§2.7,
   `KOFUI-AUDIT`(+PT) rule-6 line closed as shipped.

**How to apply:** present rule-6 questions as multiple choice ONLY when the
choices are genuinely complete alternatives; when the lane knows the one
complete idiomatic form, it implements it under this rule and records the
evidence here — it does not stall the loop asking "which variant?". The
rule never overrides rule 6 on contracts that change frozen semantics:
changing existing behavior still goes through an explicit maintainer vote
(this record IS the explicit authorization for items 1–4).

**Evidence:** maintainer messages 26/09/2026 (chat, autonomous session):
"vamo destravar rule 6, me da as duvidas" / "multipla escolha" / "não gostei
das opções" / "me explica MELHOR e me da soluções idiomaticas, nada de
desistir ou assumir gap" / "muito menos stub" / "percebe que depois das
minhas reclamações vc me deu só uma opção? é ela q vc segue" / "defina isso
como regra de escolha pra decisões automaticas. idiomatico, de acordo com a
filosofia kof, não assumir gap mas sempre desenvolver por completo, paridade
total e nunca stub".

- **Relationships:** `Related: rule 6, rule 8, rule 10 (D-KOF-FIRST), rule 11
  (Simplicity Law), Q7 (no stubs), R1/R5 (interop = official package,
  experimental), R7 (honest target scope ≠ gap), D-MEMORY-SAFETY (Fase 3
  unblocked here), D-FULL-PARITY-050`.

## D-KOFMD — Kofmd (typed, intent-oriented Markdown) is INDISPENSABLE for 0.5.0: full spec (37 sections + intent addendum) is the contract; implementation starts from the real infrastructure (maintainer 27/09/2026, explicit decision)

**Evidence:** maintainer messages 27/09/2026 (chat, autonomous session):
full Kofmd specification (37 sections: objective, existing-Kof investigation,
definition, principles, typing, data/text, block semantics, AI-first,
communication, short answers, idioms, schema, docs/AI-guide/rules,
interoperability, preservation, parser, AST/IR, type checking, LSP, CLI,
formatting, canonical form, AI read/write tests, machine readability,
non-JSON, security, tests, goldens, corpus, style, AI principles,
incremental phases 1–10, success criteria, final rule) + intent addendum
(13 sections: intent-before-presentation, no-inference, intent≠type,
small idiomatic intents, composability, determinism, write-by-intent,
probability reduction, prose, normal Markdown, design principle, golden
rule, Kof philosophy) → "kofmd vai precisar entrar agora devido ao tamanho
e a verbosidade das documentações" → "é uma decisão explicita da
mantenedora. kofmd indispensavel para 0.5.0".

**Decision (explicit maintainer override of the scope-freeze for this
front only):** Kofmd enters 0.5.0 as a first-class front. The 37+13 spec
above IS the contract (intent-oriented, typed, Markdown-degradable, never
verbose, never XML/YAML-disguised, never a metadata language). Rule 6 is
satisfied by this entry: new surface, explicit vote, recorded here.

**Scope lock (rule 11 + D-COMPLETE-FIRST apply):**
1. **Fase 1 first — investigate the real infrastructure** (lexer,
   `parser/` incl. `Lexer.java`/`Parser.java`/`AnnotationParser.java`,
   AST nodes, type system, `record` types, annotations
   (`CompilerAnnotations`), modules, stdlib, `kof.json` serialization,
   `kof.file` future plan, CLI (`kof-cli`), LSP (`LspServer`), existing
   docs/tooling/AI integration). No isolated architecture — Kofmd lives
   where the ecosystem already lives.
2. **Spec before syntax** — `docs/kofmd-plan.md`(+PT) records
   the measured findings + the frozen surface; no syntax is implemented
   before the plan lands.
3. **Incremental slices per the spec's own §35** (investigation → spec →
   minimal parser → types → Markdown interop → formatter/canonical →
   schemas → CLI/LSP → AI tooling/corpus → gradual doc migration), each a
   complete vertical cut with proof (Q0–Q7), never a stub.
4. **Fake-idiom guard** — the spec's conceptual examples (`intent: task`,
   `@decision`, `type TestResult {...}`, `kof md check`, `let`) are NOT
   adopted automatically: every surface form must compile against the real
   Kof grammar or be refused honestly (R6). `Option<T>`/`Result<T,E>` in
   the spec's type list do not exist as Kof surface types — the plan names
   the real Kof spelling (`T?` + narrowing, `throw "msg"`) or records a
   rule-6 gap.
5. **Queue:** roadmap §23 opens the Kofmd line in the same commit (rule 6:
   deciding without recording = invisible; recording without queue = dead).

**Closure (27/09/2026):** all slices 3.1→3.9 landed — pure-Kof lib
`libs/kofmd/` (8 responsibility files ≤500), CLI `kof md check|format`
(`CmdMd`), LSP hook (`LspKofmd`, `MDxxx` + hover), golden corpus
(`libs/kofmd/corpus/`, 12 files) and the hot-doc state-header migration
(convention in `docs/kofmd-plan.md` §5). Proof: the `Kofmd*E2ETest` cluster
13/13 + `CmdMdTest` 5/5 + `LspServerTest`. The plan was **promoted out of
`docs/development/`** to `docs/kofmd-plan.md` (three-states rule) — the 0.5.0
`loose_docs` condition 3 is satisfied for this front.

- **Relationships:** `Related: rule 6, rule 11 (Simplicity Law),
  D-COMPLETE-FIRST, D-KOF-FIRST, Q7 (no stubs), R1/R5, R6, kof-file-plan
  (future), roadmap §23, D-RELEASE-0.5.0-GATE (scope note)`.

## D-KOF-FIRST-IMPL — Post-0.5.0 feature architecture: Kof-first implementation, library-first; the core only grows to supply the smallest missing mechanism (maintainer 27/09/2026, explicit decision; registered as AGENTS.md rule 12)

**Evidence:** maintainer message 27/09/2026 (chat): the "Kof Post-0.5.0
Feature Architecture Guideline" (26 sections — objective; decision rule; new
feature ≠ new compiler code; library as unit of evolution; small core;
incremental self-hosting; no core rewrite; when the core may grow; core as
mechanism / Kof as policy; FFI is not failure; library first, backend after;
avoid backend-specific features; stdlib as bridge; progressive migration;
core-necessity test; design test; anti-accidental-growth; stronger
justification for new syntax; library as part of the language; self-hosting
metric; compiler as future target; self-hosting coexists with all backends;
acceptance criteria; evolution principle; summary rule; long-term principle).

**Decision:** From 0.5.0 on, every new feature is implemented **as a Kof
library first** whenever the language can already express it: *"If a feature
can be written in Kof, it MUST be written in Kof."* A new feature is **not** a
reason to grow the compiler/runtime/backend/IR/CLI/tooling. Rule 6 is
satisfied by this entry (new direction, explicit vote, recorded here).

**Decision procedure (normative):** `feature → can Kof do it? → yes → Kof
library`; no → `which fundamental capability is missing? → add the smallest
primitive → implement the feature in Kof`. The core supplies **mechanisms**;
Kof libraries carry **policy and abstraction** (`runtime: socket`;
`Kof library: HTTP`). FFI/JVM/Native/JS/WASM are the boundary, not a failure.

**Core-necessity test (every PR that touches the core for a feature):**
1. Why can it not be implemented in Kof?
2. Which fundamental capability is missing?
3. The smallest necessary core change.
4. Does that change unlock other Kof libraries?
5. Which Kof implementation can later replace part of it?
No clear answer → the feature is re-evaluated.

**Design test (before accepting an external implementation):** are we adding
a **fundamental capability** or only a **feature Kof could implement**?
Feature → library; capability → core; the distinction is explicit.

**Anti-accidental-growth:** never `if feature == X`, a special runtime API for
X, or new syntax, when X can be a library. New syntax needs a stronger
justification — exhaust `library + types + functions + modules + stdlib`
first (rule 11); the language grows by **capability**, never by local
convenience or backend-specific special cases.

**Migration:** existing core code may stay; the process is incremental
substitution (`API → Kof implementation → parity tests → migration → future
removal`), never a rewrite. Official Kof libraries are part of the language,
not "external code".

**Acceptance:** `Kof source + Kof library + tests + documentation`; if
external code is unavoidable, `Kof source + minimal primitive + backend
implementation + Kof library + tests + documentation`.

**Long-term goal:** progressive self-hosting — Kof implementing stdlib,
libraries, tooling and, eventually, parts of its own compiler — reached
library by library, not by a big rewrite. Track `% stdlib / libraries /
tooling implemented in Kof` as a direction, never as an artificial target.

- **Relationships:** `Related: rule 6, rule 11 (Simplicity Law), rule 12,
  D-KOF-FIRST (external behavior), D-BOOTSTRAP, D-MAKEALIVE, D-DB-GAPS, R1,
  R9, Q7`.

## D-DECISION-BATCH-2709B — maintainer's three answers 27/09: #639 qualified `pkg.Type` (BUG); JS media deferred post-0.5.0; `math.pow` cross = link libm (maintainer 27/09/2026, explicit decision)

**Evidence:** maintainer messages 27/09/2026 (chat, this session), answering the
parity lane's three blocking questions.

**1. #639 is a BUG — Kof differentiates by package path.** Same simple name in
different packages must be distinguishable. The consumer surface is the
**qualified path** `pkg.Type` — in expressions (`p1.Item(1)`) and in type
annotations (`var a: p1.Item`, `List<p1.Item>`); no new keyword (rule 11). The
refusal contract of `Sem010PackageQualifiedTypesE2ETest` changes accordingly.
Face 1 is an independent bug: inside `p1/Item.kf`, a bare `Item` must bind to
**its own** `p1.Item`, never another package's (`p2.Item`) — the measured
last-write-wins.

**2. JS media is deferred POST-0.5.0.** `kof.media` (`Image`/`Audio`/`Video`/
`Mic`) on the JS target is NOT a 0.5.0 blocker; parity ledger row 4's JS cell
becomes a declared post-0.5.0 gap. The "full parity" absolute of
D-GRAPHICS-GAMING stands for the media-engine front, whose plan is `future/`;
the JS engine decision is what stays open (rule 6).

**3. `math.pow` cross = link libm.** The cross sysroot links libm so
`kof_math_pow` (call `pow@PLT`) runs on riscv64/aarch64 like x86-64. This
revises decision 7a for `pow` only (the rest of the cross runtime stays
static); ledger row 10 closes with the byte-parity golden vs the JVM oracle.
Linking is **by-use** (`usesPow`), so programs that never call `pow` stay
libm-free.

- **Relationships:** `Related: D-FULL-PARITY-050, D-KOF-FIRST (rule 10), rule 11, rule 6, D-GRAPHICS-GAMING, issue #639`.

## D-DB-NORMALIZE — bare schemes normalize to `jdbc:` on JVM/JS (maintainer 27/09/2026, voted)

The open design question of `db-parity-plan.md` ("should JVM/Android/JS
normalize a bare scheme?") is DECIDED: **normalize**. `mysql://`→`jdbc:mariadb://`
(the mariadb-java-client driver only accepts the `mariadb:` sub-scheme —
measured 27/09), `mariadb://`→`jdbc:mariadb://`, `postgres://`→`jdbc:postgresql://`
(userinfo becomes `?user=`/`&password=`, merged with an existing query without
overriding params already there), `sqlite:<rest>`→`jdbc:sqlite:<rest>`.
`mongodb://` never normalizes (own branch); `oracle://` stays DB001 (S4
declared); unparseable input falls through to the named DB001. Implemented in
`JvmConfigRuntime` + `KofJsDbBridge` (duplicated per the S2 precedent);
`connect2` swaps the scheme only (explicit creds stay authoritative). Proof:
`KofDbE2ETest#jvmBareMysqlNormalizesToJdbc` + `#jvmBareSqliteNormalizesToJdbc` +
`#jvmBarePostgresNormalizesWithoutServer` + `#jvmBareMysqlConnect2ExplicitCreds` +
`#jsBareMysqlNormalizesToJdbc` (RED-first 5/5).

- **Relationships:** `Related: D-DB-GAPS, db-parity-plan.md, rule 11`.

## D-DB-ZERODRIVER — no manual JDBC driver downloads, ever (maintainer 27/09/2026, voted option C)

Challenge from the maintainer: requiring a separately downloaded library to
program goes against Kof (rule 11 — the platform absorbs ceremony). DECIDED,
two tracks: **(a) tooling auto-provision** — `kof run`/`kof build` resolve JDBC
drivers on first use from the registry/cache (no manual download; offline →
honest diagnostic, never silent); **(b) pure-Java MySQL wire** in the runtime as
the next db slice (`mysql_native_password` first — the protocol is already
owned: proven on 3 native targets in S5.x; JVM wire = same state machine over
`java.net.Socket`). SQLite-engine/Mongo-driver reimplementation is a never
(provisioned drivers, R9); H2 (pure Java, tiny) is the embedded-default
candidate later. D-DB-NORMALIZE stands as the URL canon under every track.

- **Relationships:** `Related: D-DB-GAPS, D-DB-NORMALIZE, D-KOF-FIRST-IMPL (rule 12), rule 11, R9`.

## D-DOC-SLIM — doc-wide Kofmd compression: every `.md` except `learn/`/`training/` is migrated to terse Kofmd; corpus and CHANGELOG body excluded (maintainer 27/09/2026, explicit order)

Broadens `D-KOFMD` slice 3.9 (which migrated only the hot working docs): the
maintainer ordered **all** Markdown compressed to terse, intent-oriented Kofmd
— "até mesmo o AGENTS.md, a regra é absoluta" — with `learn/`/`training/`
excepted. Mechanism is **agent migration**: the tool `kof md` only `check`s and
canonicalizes via `format` (measured byte-identical on prose) and `convert` is a
declared non-goal, so each EN+PT pair is a commit with the doc gates green. Two
technical exclusions, confirmed with the maintainer: `libs/kofmd/corpus/*.md`
(golden fixtures asserted byte-for-byte by `KofmdCorpusE2ETest`) and the
`CHANGELOG` body (generated by `scripts/changelog.sh`; only its header is
hand-edited). Supersedes the "doc-wide migration" non-goal of `kofmd-plan.md`
§4/§5.

- **Relationships:** `Related: D-KOFMD, D-KOF-FIRST, D-KOF-FIRST-IMPL (rule 12), rule 11`.

## D-IO-SIZE-JVM-LAW — §494: the JVM is the law for the `kof.io` `size()` error message (maintainer 27/09/2026, voted option A)

The JVM's `file not found: <path>` is the contract; the Native x86-64 and cross
`size: ` prefix is the divergence to remove. Same family as §493 (JVM is law):
the fix drops `.Lstr_io_size_prefix` from `RuntimeIo2.kof_io_file_size` and
`NativeRiscvAsmIoSize`, and the `NativeIoSizeCrossTest` pins collapse to the JVM
message. The success path (`st_size`) stays byte-identical. Aligns the thrown
String to the JVM; no other frozen semantics change.

- **Relationships:** `Related: §493, D-FULL-PARITY-050, D-COMPLETE-FIRST`.

## D-MEMORY-CLEAR — O-03/`MEM003`: `clear()` nulls every slot before shrinking; `MEM003` is a runtime guarantee, never a compile face (maintainer 27/09/2026, voted option a)

The spec §3 O-03 row had no decidable user-program shape, so no `MEM003` was
emitted (honest silence). The maintainer chose the CONTRACT: `clear()` MUST null
every slot before shrinking, so the guarantee is proven by a per-target runtime
test, not by a compile diagnostic. No `MEM003` compile face is created. Resolves
the decision request in `docs/development/memory-safety-plan.md`; the test lands
under the memory-safety E2E suite on all four targets.

- **Relationships:** `Related: D-MEMORY-SAFETY, D-COMPLETE-FIRST`.

## D-KOFMD-ON-EDIT — every document an agent edits is Kofmd-compressed in the same commit; the rule is absolute (maintainer 27/09/2026, explicit order)

Divides the doc-wide compression work in `D-DOC-SLIM` across agents by making
compression an **obligation of any edit**: from now on, whoever edits a document
also compresses it. It is no longer a separate front one lane owns — each agent
migrates the documents it touches. Absolute rule ("a regra é absoluta"):

- an agent that edits any eligible document MUST bring it to canonical Kofmd in
  the **same commit** — canonical state block, typed fields, prose only for what
  fields cannot express, zero field duplication;
- it is a `before_commit` / Final-self-check item, not an optional follow-up;
- the exclusions of `D-DOC-SLIM` still hold: `learn/`, `training/`,
  `libs/kofmd/corpus/*.md` and the generated `CHANGELOG` body are never
  compressed.

Consequence: the "unowned compression queue" stops being a single lane's queue
and becomes part of every change's definition of done; a doc edited without its
Kofmd pass is an incomplete unit and must not be pushed.

- **Relationships:** `Related: D-KOFMD, D-DOC-SLIM, rule 5, rule 6`.

## D-PARITY-050-SCOPE — the 0.5.0 full-parity ledger covers the SIX release targets; MCU/riscv32 + four faces are deferred to 1.0 (maintainer 27/09/2026, voted batch)

Amends `D-FULL-PARITY-050`: the 0.5.0 release ledger is measured over the SIX
release targets — JVM, Script, JS, Native x86-64, Native riscv64, Native
aarch64. MCU/riscv32 stays OUT of the 0.5.0 ledger (no process layer). Votes:

- rows 10 (`math.pow` cross) and 13 (`kof.io` cross) — **CLOSED** (already proved; bookkeeping only);
- rows 1 (`process`) and 3 (`ssh`) — **closed for the 6 targets**; the MCU/riscv32 `PROC001` residual leaves the 0.5.0 ledger;
- row 4 media `Image`/`Mic`, row 12 web T1 on native/cross, row 14 security cross/JS — **DEFERRED to 1.0** (declared gaps `MEDIA00x`/`WEB00x`/`SECN00x`; never an acceptance state, `D-COMPLETE-FIRST`);
- row 11 strings (`NAT-STR01`/`STR003`) — **IMPLEMENT now** (native+JS Unicode parity): the only remaining 0.5.0 open row.

- **Relationships:** `Related: D-FULL-PARITY-050, D-COMPLETE-FIRST, D-RELEASE-0.5.0-GATE`.

## D-BUGS-050-QUARANTINE — §524 and §533 are harness/environment conditions, quarantined by deterministic isolation (maintainer 27/09/2026, voted)

Both are green in isolation and red only under full-suite load (host scheduler);
they are NOT code regressions. §524 = the qemu aarch64 harnesses
(`NativeRiscvGc*`/`Dtoa`/`DbWire`) SIGSEGV 139 under load; §533 =
`InteropTimeout.cancelFromAnotherTask…008` (the host-python `never` registration
race). Contract: make them deterministic by ISOLATION (run the harnesses/tests
outside the load, pinned), keep the entries honest — the isolation must be
proved, never a hidden failure / false green (Q5).

- **Relationships:** `Related: D-RELEASE-0.5.0-GATE, zero-regression`.

## D-534-JS-DEFER — §534 (`kof run --target js` ignores provisioned JDBC drivers) deferred to 1.0 (maintainer 27/09/2026, voted)

The JS-run provisioning (TCCL `URLClassLoader` or child re-exec) is a real
feature with no 0.5.0 surface; it is declared and deferred, and `DB001` stays the
honest diagnostic on JS.

- **Relationships:** `Related: D-DB-ZERODRIVER, D-FULL-PARITY-050`.

## D-X2-LANDED — interop engine complete (5/5 slices with evidence, 27/09)

Item 2 (`D-COMPLETE-FIRST`) CLOSES: fatia 1 (Py engine), 2 (R engine), 3
(timeout/cancel/reuse + §527), 4 (cross faces — timeout007/deadline-reuse/
idle-cancel + R-happy E2E riscv64≡aarch64≡JVM under qemu, R-gated; 008 stays
JVM-only by design), 5 (corpus/DoD). Session-state stays a declared cut-out
(rule 6); ANDROID/MCU/RISCV32 stay R7; `kof.interop` stays `experimental`
(R5 per-namespace promotion is a separate act). Proof: `InteropTimeoutE2ETest`
7/7 + `InteropRE2ETest` cross (R-gated) + neighbors 27/0F/8skip; plan promoted
`development/` → `docs/` (three-states).

- **Relationships:** `Related: D-COMPLETE-FIRST, D-KOF-FIRST (rule 12), rule 11, rule 6, issue #639 (untouched), PR #619 (rule 10)`.

## D-STR-UNICODE — row 11 for 0.5.0 = the Unicode faces, JVM-exact per UTF-16 code unit; the regex engine is deferred to 1.0 (maintainer 27/09/2026, voted)

Row 11 is split by capability, not by target:

- **Implement now (native + JS), JVM-exact per UTF-16 code unit:** `String.toUpperCase`/`toLowerCase` (NAT-STR01), `String.compareToIgnoreCase`, and `strings.reverse` non-ASCII (JVM `StringBuilder.reverse` semantics — surrogate PAIRS stay together). Native uses a compact embedded Unicode case table; JS binds its Unicode-correct primitives and pins parity against the JVM oracle. Only a per-code-unit fold is in scope (no locale-sensitive full case mapping).
- **Deferred to 1.0:** the three regex members (`matches`/`replaceAll`/`replaceFirst`). A regex engine on the freestanding native targets is a HEAVY DOMAIN and a new fundamental capability — per the platform boundary it belongs to an official package/library, not an ad-hoc runtime splice; JS `RegExp` vs JVM `Pattern` parity rides the same engine decision. `STR003` stays the honest gate for exactly these three until then; `compareToIgnoreCase` leaves the gate.

Consequence: after the Unicode faces land, row 11's only remaining cells are the deferred regex faces, and `full_parity` reaches 0 open rows for 0.5.0.

- **Relationships:** `Related: D-PARITY-050-SCOPE, D-FULL-PARITY-050, D-KOF-FIRST (rule 12), NAT-STR01, §424`.

---

## D-KOFMD-OPERATING-STANDARD — Kofmd is the mandatory operating standard for every agent: think, reason, respond, execute and document in Kofmd, uniformly (maintainer 27/09/2026, explicit order)

**State:** DECIDED (normative; binding on every agent, no per-agent variant)

Kofmd stops being only a file format and becomes the operating standard of the agent itself. Every agent — human-directed or autonomous, any lane — thinks, reasons, responds, executes and documents in Kofmd. The standard is uniform: no agent keeps a private variant.

Contract:

- **Intention over narrative.** Represent work as `intent`, `state`, `evidence`, `decision`, `result`, `next`; do not expand a structured problem into prose.
- **Evidence before inference.** Distinguish `fact`, `decision`, `inference`, `unknown`. Never fabricate api, syntax, behavior, decision, requirement, result, compatibility, target support or implementation state. When evidence is missing, record `unknown` or the gap — never a plausible guess.
- **Verified results only.** `implemented` != `verified`; a result is claimed only with executed proof (compiler, tests, golden). A plan is not an implementation; an expectation is not proof.
- **Minimal sufficient response.** The smallest representation that preserves intent, state, evidence, decision, result, next. Prose only where structure cannot carry the information.
- **State, not history.** `last` = the immediately relevant prior state; `next` = the next known intention, not a backlog; `location` = where the intent belongs; `constraint`/`decision` are explicit.
- **Semantic compression.** Any document an agent edits is compressed in the same commit (`D-KOFMD-ON-EDIT`).

Coordination (uniform across agents):

- claim before work; claim and first change share a commit;
- on a lane collision, wait for the owner or stop — never race the shared worktree;
- never end a turn with an uncommitted unit;
- push only through `scripts/sync-push.sh`.

- **Relationships:** `Related: D-KOFMD, D-KOFMD-ON-EDIT, D-DOC-SLIM, D-BRANCH-PIPELINE, D-QUALITY-PIPELINE-2609, rule 5, rule 6`.

## D-FUTURE-BATCH-2809 — all `future/` plans authorized as 1.0.0 scope (maintainer 28/09/2026, voted batch)

Every plan in `docs/development/future/` is authorized — all must conclude
before 1.0.0. Promotion stays one-at-a-time per `D-FUTURE-PROMOTION` (easiest
first; never the most interesting, never frozen-semantics). Individual locks:

- **D-SCOPED-RESOURCES-GO** — `using` syntax authorized (lightweight RAII,
  mapped desugar, no ownership).
- **D-VALUE-RECORDS-GO** — value-record front open; ABI scope + open questions
  (class? generics? diagnostics? JS repr?) decided during implementation.
- **D-KOF-FILE-GO** — `kof.file` promotion authorized.
- **D-BUFFER-INOUT-NATIVE** — the Native face of `Buffer(U8, INOUT)` is authorized
  (x86-64 + cross riscv64/aarch64); native/FFI lane.
- **D-TEST-ARCHITECTURE-GO** — test-architecture promotion authorized
  (profiling → integration).
- **D-HTTP-POLICIES** — authorized; surface locked during implementation.
- **D-PAGINATION** — authorized; surface locked during implementation.
- **D-ENTITY-HISTORY** — authorized; surfaces locked during implementation.
- **D-TESTING-PLATFORM** — authorized (extends `kof test` additively).
- **D-CONNECTORS** — connector ecosystem authorized.
- **D-DEPRIORITIZED-REOPEN** — DECOMPILER, LEGACY_MIGRATION and TRANSLATOR
  reopened.
- **D-GRAPHICS-SPIKE** — graphics-gaming spike 3.0 authorized (measurement and
  stack only, no API).
- **D-ASSEMBLY-OPT-GO** — assembly-optimization authorized (from phase A,
  no semantics change).
- **D-IMAGE-VISION-GO** — image-vision authorized.
- **D-MULTIPARADIGMA-GO** — Tier 2.x authorized.
- **D-BOOTSTRAP-GO** — Kof-in-Kof authorized.
- **D-WASM-GO** — new wasm/wasi target authorized.
- **D-UNIVERSAL-STAGES-GO** — stages 4–7 (DATA/SECURITY/SCIENTIFIC/BIO)
  authorized.
- **Open design questions resolved** in `D-FUTURE-BATCH-2809B` (below).

- **Relationships:** `Related: D-FUTURE-PROMOTION, D-KOF-FIRST (rule 12), rule 11, rule 6`.

## D-FUTURE-BATCH-2809B — open design questions of the `future/` plans resolved (maintainer 28/09/2026, interactive batch)

**State:** DECIDED (maintainer) — resolves the "Open questions / DECISION REQUIRED / TBD" left by `D-FUTURE-BATCH-2809`. Promotion remains one-at-a-time (`D-FUTURE-PROMOTION`); the plans' own recommendations were chosen except where noted.

- **D-PAGINATION (surface locked):** window type `Window<T>`; introduce the new type (not signature-only); `total` via a flag on the window call; max limit = global default; the in-memory part starts now (rides `D-MULTIPARADIGMA-GO` Phase 1); keep `orm.page` alongside `orm.window` (no bump); `offset` only on the windowed method, not the typed DSL; HTTP helper `pageRequest(...)` lives in `kof.web`.
- **D-VALUE-RECORDS-GO (resolved):** `value` applies only to `record`; collections hold them boxed; use where identity is required = compile-time error; JS representation is a frozen object.
- **D-ENTITY-HISTORY (Q1–Q15):** `audited entity`; query entity-static (`User.history(db, id)`); return known `Revision`/`FieldChange` records; one revision per `save`; per-entity sequence; only ORM paths audited (raw SQL out of contract); `atTime` = at-or-before; relationships = FK values only; a removed entity keeps history queryable; PII reconciliation = crypto-shredding + masking (contract); fix `observability.correlationId()` to be request-bound; no core actor-override sugar in v1; snapshot-per-revision baseline (diff optional per backend); `not audited` exclusion in the core; assign the `HIST0xx` block now (+ parity matrix).
- **D-MULTIPARADIGMA-GO (TBDs):** `take(-1)`/`drop(-1)` mirror `slice` (0/size); `zip` returns a `record Pair`.
- **D-GRAPHICS-SPIKE:** namespace `kof.game`; scene is call-based (no new syntax); 3D only after 2D parity; golden hash contracts required; stack decided by the 3.0 spike (measured, never by familiarity); input = per-frame snapshot; WASM auto-entry; current media kept (no rebase).
- **D-TESTING-PLATFORM:** extend the existing `test`/`assert` (no foreign syntax); `kof.test` becomes a stdlib namespace; Playwright/Cypress opt-in, not shipped with the CLI; browser E2E JVM-only first, standalone `kof test --e2e` later.
- **D-CONNECTORS:** ownership vocabulary stays internal (no language surface); interop error is a language type; **`foreign module` enters the grammar now** (maintainer's explicit choice, against the recommended defer); ABI tiers + first stable version defined now; official second connector = C ABI.
- **D-KOF-FILE-GO:** base stdlib keeps light I/O/streaming/text; heavy codecs (PDF/images/archives) belong to official packages (R1).
- **D-IMAGE-VISION-GO:** official package, interop-first (imageio/PDFBox/ZXing/Tess4J/OpenCV/ONNX) — never reimplement.
- **D-BOOTSTRAP-GO:** keep entry conditions E1–E6; does not start before the 1.0 EXIT GATE (R12).
- **D-WASM-GO (D-WASM-01..09):** direct backend (not a transpile chain); `Int` = i64 (match JVM/Native golden); exception = thrown-string global + `br` unwinding; objects = handles + handle table; GC reuses the native design (mark-sweep); closure env explicit parameter; WASI preview1; dev runtime = wasmtime first; concurrency = cooperative/diagnostic-only in v1.

- **Relationships:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-PAGINATION, D-VALUE-RECORDS-GO, D-ENTITY-HISTORY, D-MULTIPARADIGMA-GO, D-GRAPHICS-SPIKE, D-TESTING-PLATFORM, D-CONNECTORS, D-KOF-FILE-GO, D-IMAGE-VISION-GO, D-BOOTSTRAP-GO, D-WASM-GO, rule 6`.

## D-PAGINATION-P4-LOWERING — `orm.window` desugars in the ORM lowerer into the Kof `window(...)` helper (maintainer 29/09/2026, "P4 via (b)")

**State:** DECIDED (maintainer) — unblocks pagination plan P4 under `D-PAGINATION`.

- **Problem:** `orm.window<T>(db, limit, offset[, true])` must return `Window<T>`, but `Window<T>` is a Kof `record` compiled per-program: a per-target runtime `kof_orm_window` cannot construct it (no reflection/codegen). A design decision was required (AGENTS rule 6).
- **Chosen (b) — ORM-lowerer desugar (library-first):** `ExpressionOrmCallLowerer` lowers `orm.window` into the existing injected Kof `window(...)` helper over the already-existing `orm.page`/`orm.count` faces. No new runtime symbol per target, no language-surface change, no change to `Window<T>`.
- **Semantics:** `orm.page<T>(db, limit, offset)` supplies the rows; the helper supplies `Window<T>` metadata exactly as the in-memory P2 face (`hasPrevious = offset > 0`; `hasNext` optimistic `page.size == limit` without total). The 4-argument form is the explicit opt-in total: it runs `orm.count<T>(db)` **only on that branch** (lazy), so the 3-argument form never issues a `COUNT(*)`. Argument expressions are evaluated once (lowerer temps).
- **Support:** rides the `orm.page`/`orm.count` support sets (JVM/Android/JS + Native x86-64 + riscv64/aarch64 cross), honest `ORM001` wherever the underlying face is absent.
- **Relationships:** `Related: D-PAGINATION, D-KOF-FIRST, D-KOF-FIRST-IMPL, D-DB-GAPS, rule 6, rule 12`.

## D-PAGINATION-P5-SHAPE — `kof.web.pageRequest` reads the ambient request and returns a core `PageRequest` (maintainer 29/09/2026, option "explicit `PageRequest`")

**State:** DECIDED (maintainer) — unblocks P5 under `D-PAGINATION`.

- **Problem:** P5 must read `?page/limit/offset` from the current HTTP request and hand the handler a pagination value, without leaking an HTTP type into the core (plan §12). `kof.web` exposes the request only through ambient context accessors (`query(name): String?`), never a `Request` value; §19 Q8 asked the name/shape/home.
- **Chosen:** `pageRequest(defaultLimit: Int[, maxLimit: Int]): PageRequest` in a virtual host **`kof.web`** (injected on `import kof.web`), returning the CORE record `PageRequest(Int page, Int limit, Int offset)` and reading the request via the existing ambient `query("page"/"limit"/"offset")`. "Explicit" = an explicit core return value (no hidden `Window`, no HTTP type); a literal `Request` parameter is not representable because `kof.web` has no `Request` type — adding one would be a separate core-primitive decision (not taken).
- **Semantics:** `page` is 1-based, default 1 (`offset = (page-1)*limit`); `limit` defaults to `defaultLimit` and is clamped down to `maxLimit` when `maxLimit > 0`; an explicit `?offset=` wins over the page calculation; non-integer values, `page < 1`, `limit < 1`, `offset < 0`, and Int overflow of `(page-1)*limit` throw the named `PAGINATION:` error. The handler maps it to `400` with `catch (String e) { return status(400, e) }` — no runtime error→status change.
- **Home rationale:** a SEPARATE host from `kof.pagination` so Native programs that only `import kof.pagination` never pay the web gap: `pageRequest` needs `query(...)`, absent on Native (`WEB001`, plan §13). The `web` namespace is already registered in the R1 ledger.
- **Related:** `Related: D-PAGINATION, D-PAGINATION-P4-LOWERING, D-KOF-FIRST, rule 6, rule 12`.

## D-HTTP-POLICIES — declarative HTTP/Web policies: global (existing), per-resource prefix and per-endpoint, with declarative rejection payloads (maintainer 28/09/2026, "pode assumir")

**State:** DECIDED (maintainer) — promoted front under `D-FUTURE-PROMOTION` (`docs/stdlib/http-policies-plan.md`, concluded 28/09: slices F0–F6 landed, `KofHttpPoliciesE2ETest` 10/10); part of the `D-FUTURE-BATCH-2809` authorization.

- **Scope:** additive extension of the existing **global** `app.security(opts)` (`D-SEC` C18). No new grammar, no keyword, no new user type beyond the `Map` opts already used. The fixed pipeline order (`D-SEC`) is untouched.
- **Surface v1 (locked, plan §3):** `app.security(opts)` (global, unchanged); `app.policy(prefix, opts)` (resource scope); `app.get/post/... (path, opts) { }` (endpoint policy); new opt key `responses` (`Map`) for declarative 401/403/429 bodies. Scalar keys: deepest scope wins; list keys (`publicPaths`, `roles`): union (allow-lists only accumulate). Prefix-only matching, longest prefix wins; no globs/regex in v1.
- **Backward compatible:** omitted keys keep today's behavior/bodies. Errors are raised at app build (before `listen`), never a silent no-op.
- **Targets:** JVM full; Native/JS `WEB006` at compile time (R6 — never a silent policy drop).
- **Merge law / slice order (F0…F6):** owned by the plan (§4, §12); this entry locks the decision and the surface.
- **Relationships:** `Related: D-SEC, D-SPRING, D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, rule 6, rule 12`.

## D-KOF-FILE-GO — `kof.file` promoted, re-scoped to Streaming (library-first) (maintainer 28/09/2026, batch `D-FUTURE-BATCH-2809` + direction "re-escopar kof-file e implementar Streaming")

**State:** DECIDED (maintainer) — promoted front under `D-FUTURE-PROMOTION` (`docs/stdlib/kof-file-plan.md`); the batch authorized the promotion, this entry locks the re-scope and the surface.

- **Re-scope (measured, 28/09):** Phase 1 (File/Path/Text/Binary) is already implemented as `kof.io` (`docs/stdlib/IO.md`); the only open Phase-1 face is **Streaming**. The plan's "zero code" claim was true for the module name, not for the capability.
- **Slice 1 (LANDED):** pure-Kof library `libs/file/` — `FileStream(path[, chunkSize])` with `readChunk() : Int[]?` (null at EOF), `done()`, `position()`, plus `copyStream(source, destination, chunkSize) : Long` (constant-memory copy). Built exclusively on the existing `kof.io.readRange`; **no new grammar, no compiler change** (`D-KOF-FIRST-IMPL`, rule 12). Proof `FileLibraryE2ETest` 2/2 (JVM).
- **Slice 2 (LANDED):** the library is measured on every target against one golden (`FileLibraryE2ETest` 7/7) — JVM, Native x86-64, riscv64/aarch64 (qemu) and **Script** all run the real `readRange`. **JS is an honest compile-time gap `IOJS001`**: the GraalJS runtime (`kof-runtime-io.mjs`) exports no binding for `readRange`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink`, so emitting the call died at RUNTIME with `SyntaxError: ... does not provide an export named 'kofIoReadRange'` (R6). `ExpressionBuiltinInstanceCalls.lowerIo` now refuses via `JS_MISSING_IO`; the host has no partial-read primitive, so a real JS binding needs a host addition — never a silent whole-file fallback. Pinned by `DomainGapCodesTest.ioReadRangeOnJsIsIojs001`/`ioCopyOnJsIsIojs001` and the parity matrix row.
- **Targets:** JVM, Native (x86-64/riscv64/aarch64) and Script proven; JS refuses `readRange`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` at compile time with `IOJS001` (never a silent whole-file fallback).
- **Boundary (R1):** base stdlib keeps light I/O/streaming/text; heavy codecs (PDF/images/archives) belong to official packages (R9 interop-first).
- **Slice order (how to finish):** plan §"How to finish" (Streaming slice 2 → Native measurement + JS/Script gap code → Phase 2 structured data → Phase 3+ configuration/documents/containers).
- **Relationships:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, D-KOF-FIRST-IMPL, D-IO-SIZE-JVM-LAW, rule 6, rule 12, R1, R9`.

## D-RELEASE-0.5.0-CLOSED — the 0.5.0 cut is done; the prep tracker and its gate are retired (maintainer 28/09/2026, "a release ja aconteceu")

**State:** DECIDED (maintainer) · **Evidence:** `origin/main` merged `#619` (`beta-0.5.0 → main`) and the artifacts are tagged `kof-0.5.0-beta*` (2026.09.25).

- The acceptance record `release-beta-0.5.0-prep.md`(+PT) moved out of `development/` (three-states rule) to [`docs/distribution/release-beta-0.5.0.md`](../distribution/release-beta-0.5.0.md) (+PT) with `state: done`; it is frozen history.
- `scripts/check_release_050_gate.sh` and `scripts/tests/check-release-050-gate-test.sh` are **retired** (removed from `run-agent-tests.sh`); the 0.5.0-specific conditions (parts G/H of `check_live_records.sh` + the README §0/§1 loose-set authority) stop running now that their inputs are gone.
- Release promotion is governed from now on by [`quality-pipeline.md`](quality-pipeline.md) (`D-QUALITY-PIPELINE-2609`): `lab → testing → prerelease → stable → release/x.y.z → tag`.
- **Relationships:** `Related: D-BRANCH-0.5.0, D-RELEASE-0.5.0-GATE, D-RELEASE-0.5.0-SCOPE, D-FULL-PARITY-050, D-QUALITY-PIPELINE-2609, D-BRANCH-PIPELINE`.
## D-SCOPED-RESOURCES-GO — `using (x = init, closer) { body }`: lightweight RAII as a mapped pre-lowering desugar, no ownership (maintainer 28/09/2026, batch `D-FUTURE-BATCH-2809`)

**State:** DECIDED (maintainer) — promoted front under `D-FUTURE-PROMOTION` (`docs/scoped-resources-plan.md`); the batch one-liner is the authorization, this entry locks the decision and the surface.

- **Scope:** one contextual statement + one `DesugarStep` (`desugarUsing`, FIRST in `defaults()`), zero typer/lowerer/codegen change on any target. New syntax is justified (not library-first): only the parser can introduce a binding with a guaranteed closer and a parse-time missing-closer error; the lowering reuses the existing `try/finally` on every target.
- **Surface v1 (locked, plan §2/§3):** `using (x = init, closer) { body }` → `{ var x = init; try { body } finally { closer } }`. The closer is EXPLICIT — `x.close()` is false for `db` (String handle, `db.close(handle)` law); `conn.close()`/`sse.close()` stay writable as the closer. Missing closer = parse error (R6). Block-scoped binding (no escape by construction); escape-after-close stays with memory-safety.
- **Backward compatible:** `using` is contextual (`using` + `(` only); zero `.kf`/test-source identifier usage measured, so no existing program changes meaning. Programs without `using` return the unit untouched (freeze rule 3).
- **Targets:** all by construction (pre-lowering desugar); slice-1 proof is JVM/Script/JS parity + JVM/Script/Native-x86 exception goldens; JS nested-throw stays a loud COMP002 (pre-existing backend gap, §174 family, JS lane's front).
- **Merge law / slice order:** owned by the plan (§6); slice 1 = parser + `UsingStmt` + `desugarUsing` + `UsingDesugarE2ETest` 7/7.
- **Closure 28/09:** slices 1–6 landed (`UsingDesugarE2ETest` 18/18); plan moved to `docs/scoped-resources-plan.md`; cross-`db` explicitly out (db lane's matrix).
- **Relationships:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, D-DESUGAR-STEP, rule 6, rule 11, rule 12`.
## D-MEM021-SCALAR — `MEM021` covers the ESCALAR capture too: parent re-assignment of a captured local after `spawn` without join is a compile-time ERROR (maintainer 28/09/2026, voted option A + ERROR)

**State:** DECIDED (maintainer) — resolves the #660 decision request; implementation = `OwnershipPass` + `SpawnCaptureScanner` (front `docs/development/memory-safety-plan.md` slice 3.2b).

- **Question (#660):** `SpawnCaptureScanner.MUTATORS` listed only OBJECT mutators (`add`/`remove`/`clear`/`addAll`); the parent re-assigning the SAME captured SCALAR local after `spawn`, with no `await`/`join_all` between, compiled with NO diagnostic on all targets and raced (measured JVM/JS/Script `202` then `101`, exit 0). Spec B-04 spoke of a "mutable object", so the scalar case was neither forbidden nor registered.
- **Decision:** option **A — diagnose**, severity **ERROR** (not WARNING): extend `MEM021` to the scalar path. Rationale: a worker that WRITES the captured binding forces the representation box (`CompilerCaptureScanner` → `mutatedCapturedNames` → `CapturedVarBox`), so parent and worker SHARE the slot; a parent write with no `await` between is the clear data race of B-04/C-03, same class as the existing object `MEM021` (ERROR on the clear race).
- **Surface:** the worker write set gains re-assignment (`n = ...`) and `++`/`--` of a captured binding; the parent write set gains re-assignment and `++`/`--` of the same binding. Pure READ-only capture is NOT a race (read-only capture lowers by VALUE, no box) — stays silent.
- **Zero false positive by construction:** `await` of any handle clears the pending set (sub-report, never over-report); names SHADOWED by worker-local declarations or lambda parameters are excluded; conditional/interprocedural spawn faces remain named (silent).
- **Proof:** `MemorySafetyE2ETest` 46/46 (6 new: 3 RED before the fix — measured 3/3 FAIL on the old scanner — and 3 zero-FP green), JVM/Native/JS + Script diagnostic.
- **Relationships:** `Related: D-MEMORY-SAFETY, D-MEMORY-CLEAR, D-FUTURE-BATCH-2809, rule 6, rule 11`; tracker #660.

## D-BUFFER-INOUT-NATIVE — the Native face of `Buffer(U8, INOUT)` is authorized and routed to the native/FFI lane, cross scope (maintainer 28/09/2026, direction "a lane FFI corrija" + recorte "x86-64 + cross riscv64/aarch64")

**State:** DECIDED (maintainer) — resolves the #651 target/order decision request; **execution belongs to the native/FFI lane** (fase-5 front), not the issues/tooling lane.

- **Question (#651):** `Buffer(U8, INOUT)` binds on JVM/JS (`FfiSignature` token `B`, `BufferFfiE2ETest` 4/4) but the Native face stays `FFI001` (honest per-target gap, R6). The issue asked whether to complete it on Native and in which target order.
- **Decision:** authorized — implement the Native face under the existing `D-R3-BUFFER`/`D6-3` handle/out-buffer contract; **no new syntax, no generic pointer**. Recorte = **Native Linux x86-64 AND cross riscv64/aarch64** (maintainer's scope choice); JS/non-bindable faces keep their honest gap (`FFI001`/`FFI002`).
- **Contract kept:** accept only the already-defined Buffer type/capacity; native access confined to the FFI call; validate length/capacity/bounds before the call; publish bytes per the established `INOUT` semantics; refuse honestly retained callbacks, variadics, generic pointers and out-of-contract ownership; `FFI001` stays for everything outside the contract (never a stub, never a silent fallback).
- **Proof to require (per issue):** a small deterministic C fixture + a Kof program observing the result via `Buffer.bytes()`, byte-for-byte JVM↔Native parity, and negative capacity/bounds cases; a benchmark may measure cost but no performance claim without measurement.
- **Routing:** NOT implemented by the issues/tooling lane; the native/FFI lane owns execution (coordinate with the in-progress fase-5 front / pin unit #666 before touching; do not collide).
- **Relationships:** `Related: D-R3-BUFFER, D6-3, D-KOF-FIRST, D-FULL-PARITY-050, rule 6, rule 11, rule 12`; tracker #651.

## D-SCRIPT-EXTERN-REFUSE — Script × `extern` is refused at compile time with an honest gap code + a `backend-parity` FFI×Script row (maintainer 28/09/2026, voted option A)

**State:** DECIDED (maintainer) — resolves the #667 decision request; **implementation belongs to the memory-safety/Script lane** (front `docs/development/memory-safety-plan.md` phase-5 unit 2), not the issues/tooling lane.

- **Question (#667):** `extern "libc.so.6" abs(Int x): Int` + `main() { println(abs(-7)) }` compiles CLEAN on Script and dies at runtime with the raw JVM error `KofRuntime.kof_ffi/4` (exit 1), with or without `spawn`; no gap code and no FFI×Script row in `docs/backend-parity.md`. `kof_ffi` exists only in the JVM runtime (`JvmRuntimeCallDescriptors`), not in `KofInterpreterRuntime`.
- **Decision:** option **A — compile-time refusal**. The Script target has no FFI runtime, so an `extern` declaration is refused at the declaration line with an honest, named gap code and the `backend-parity` FFI×Script row is added. No silent fallback, no raw runtime death (precedent #510/`INTEROP003`: static field of an external class on a non-JVM target = compile-time refusal).
- **Gap code (locked sub-choice):** reuse **`FFI001`** — the same "extern is not bindable on this target" class Native already uses for its honest declaration-line refusal (`CompilerFfiBinding`); a new `FFI003` would multiply the code table without a distinct class. Everything outside the contract keeps `FFI001`.
- **Not chosen:** (B) implementing the `kof_ffi` bridge in the interpreter (larger surface/contract — reopen with a new decision if a real Script FFI consumer appears); (C) other.
- **Proof to require:** a Script RED-first E2E (the exact `abs(-7)` reproducer refused at compile with `FFI001`, zero raw runtime crash) + the `backend-parity` FFI×Script row; no Script face is pinned as correct.
- **Relationships:** `Related: D-FFI-STRUCT, D-R3-BUFFER, D-FULL-PARITY-050, D-MEMORY-SAFETY, rule 6, rule 7`; tracker #667.

## D-MEM020-COMPILE — `MEM020` (B-03) gets a COMPILE face over the existing `OwnershipPass`: two concurrent `extern` writes to the same `Buffer(U8)` across `spawn` without join are a compile-time ERROR (maintainer 28/09/2026, voted option A)

**State:** DECIDED (maintainer) — resolves the #668 decision request; **implementation belongs to the memory-safety lane** (front `docs/development/memory-safety-plan.md` phase-5 unit 2), not the issues/tooling lane.

- **Question (#668):** two `spawn`ed `extern` calls writing the same `Buffer(U8)` without sync compile CLEAN (diag=[]) on all targets — the B-03 line of the spec ("Passing same `Buffer` to two concurrent FFI calls without sync" → `MEM020`, `MemRule.java:36` `COMPILE_AND_RUNTIME`). The captured Buffer is by reference and FFI-write is not in `SpawnCaptureScanner.MUTATORS`, so object `MEM021` does not fire and `D-MEM021-SCALAR` does not cover object aliasing via FFI. No scanner, no runtime guard, no E2E.
- **Decision:** option **A — compile face**. `MEM020` ERROR when a `Buffer` is written by an `extern` (INOUT param) from two `spawn`s without `await`/`join_all` between — implemented over the existing `OwnershipPass` (same shape as slices 3.2/3.2b). The spec already forbids B-03; this gives the existing rule a tooth.
- **Not chosen:** (B) runtime writable-borrow guard on `Buffer` = a NEW core primitive → larger scope decision, deferred; (C) accept as a documented race (rejected — the spec already forbids it).
- **Proof to require:** the exact #668 reproducer RED-first as `MEM020` ERROR on all targets with zero false positives (a single writer or an intervening `await` stays silent); `MemorySafetyE2ETest` growth.
- **Relationships:** `Related: D-MEMORY-SAFETY, D-MEM021-SCALAR, D-R3-BUFFER, D-FFI-STRUCT, rule 6, rule 12`; tracker #668.

## D-MULTIPARADIGMA-PHASE1A — `any`/`all`/`none` on `List`: eager short-circuit quantifiers reusing the `kof_list_*` pattern (maintainer 28/09/2026, batch `D-MULTIPARADIGMA-GO` + `D-FUTURE-PROMOTION`)

**State:** DECIDED (maintainer) — promoted front under `D-FUTURE-PROMOTION` (`docs/stdlib/PLAN-MULTIPARADIGMA.md`); the batch one-liner authorizes Tier 2.x, this entry locks Phase-1a scope.

- **Scope:** three additive `List` methods, no grammar/keyword/type change. Truthiness reuses the `filter` rule (`Boolean.TRUE` or `Integer 1`); short-circuit per the plan §4 table (vacuous: `all`/`none` true, `any` false on empty — `none` ≡ ¬`any`; the plan's draft line read `any`/`none` false and the maintainer corrected it 28/09). Zero new typer/lowerer machinery beyond the map/filter path (`contextualLambda` generalization to the new set).
- **Surface v1 (locked, plan §4):** `List<T>.any((T)->Bool): Bool`, `all`, `none` — same signatures on every target; `take`/`drop`/`slice` are the pagination lane's P1 ride and are NOT in this slice; `find`/`forEach`/`flatMap`/`count(pred)`/rest are later slices.
- **Backward compatible:** new method names only (zero `.any(`/`.all(`/`.none(` in corpus, no keywords); unknown-method diagnostic lists them (never silent).
- **Targets:** all by the established pattern (JVM statics + Native-x86 asm + cross new piece + JS prelude + Script); slice proof is E2E parity per op.
- **Merge law / slice order:** owned by the plan (§3: one commit per op-pair); slice 1a = the quantifier trio + `ListQuantifiersE2ETest`.
- **Relationships:** `Related: D-MULTIPARADIGMA-GO, D-FUTURE-BATCH-2809B, D-FUTURE-PROMOTION, D-KOF-FIRST, rule 6, rule 11, rule 12`.
## D-IMAGE-VISION-GO — `kof.image`/`kof.vision` promoted as official package; first slice = pure-Kof metadata (maintainer 29/09/2026, batch `D-FUTURE-BATCH-2809` + `D-FUTURE-PROMOTION`)

**State:** DECIDED (maintainer) / IN DEVELOPMENT — promoted front under `D-FUTURE-PROMOTION` (`docs/development/image-vision-plan.md`).

- **Scope:** image manipulation (`kof.image`) and computer vision (`kof.vision`), delivered as an **official package** (R1; born `experimental`), interop-first per R9 (imageio/turbojpeg/OpenCV/ONNX behind the Kof API; codecs never reimplemented).
- **Slice 1 LANDED 29/09 (library-first, `D-KOF-FIRST-IMPL`):** pure-Kof `libs/image/` reads **format + pixel dimensions** from the leading bytes (PNG/GIF/BMP info+core/JPEG SOF/WEBP VP8·VP8L·VP8X) over a bounded 4 KiB `kof.io.readRange` prefix — no codec, no pixels, no new syntax. `ImageMetadataE2ETest` 7/7 on JVM + Native x86-64 + riscv64 (qemu) + Script; JS gap `IOJS001`.
- **Next:** pixel decode + `Image` data, then interop `resize`/`crop`/`rotate`, Phase 2 processing, Phase 3 `kof.vision`.
- **Measured finding (native lane):** cross natives fail a single `new Int[65536]` (256 KiB) allocation — catalogued `known-bugs` **§540**.

- **Relationships:** `Related: D-FUTURE-BATCH-2809, D-FUTURE-BATCH-2809B, D-FUTURE-PROMOTION, D-KOF-FIRST, D-KOF-FIRST-IMPL, R1, R9, rule 6, rule 12`.

## D-STDOUT-ENCODING — one stdout-encoding rule for everything the CLI runs: console keeps its code page, file/pipe gets UTF-8 (maintainer 29/09/2026, multiple-choice "Aprovar forma (c) + merge")

**State:** DECIDED (maintainer) — candidate shape (c) of §539; closes #676 and §539 when PR #677 lands on `lab`.

- **Rule:** a stream attached to a Windows console is emitted in the console's code page (what the console can display — the JVM's own behavior there, no regression); a file or pipe gets UTF-8 (parity with Native/KofJS and `semantics.md` §7). Linux/macOS unchanged (all values already UTF-8).
- **Scope:** CLI process wiring only (`KofStdio` + the 9 child-launch sites + KofJS `fromUtf8`); no syntax, stdlib or compiler change. The `kof build` JVM-artifact residual (rule in the generated program's startup) is a separate follow-up, not this decision.
- **Relationships:** `Related: #676, #677, §539, rule 5, rule 6`.

## D-MULTIPARADIGMA-SORTED — `sorted` ships natural order PLUS the `(A,A)->Int` comparator in this slice, on all 4 targets (maintainer 29/09/2026, multiple-choice "Com comparador agora")

**State:** DECIDED (maintainer) — unblocks the `sorted` remainder of `PLAN-MULTIPARADIGMA.md`.

- **Scope:** `List<T>.sorted(): List<T>` (natural order via `compareTo` for String/numbers; naive-for-others stays an honest gap) AND `List<T>.sorted((T,T)->Int): List<T>` now (not later); stable, copies and sorts, eager like the rest of Phase 1.
- **Relationships:** `Related: D-MULTIPARADIGMA-PHASE1A, D-MULTIPARADIGMA-GO, rule 6`.

## D-MULTIPARADIGMA-ZIP — `zip` truncates at `min` size and yields a named record `Pair` (maintainer 29/09/2026, multiple-choice "Record Pair")

**State:** DECIDED (maintainer) — unblocks the `zip` remainder of `PLAN-MULTIPARADIGMA.md`, closing the plan §231 TBD.

- **Scope:** `List<T>.zip(List<U>)` truncates silently at `min(sizeA,sizeB)` (not an error); each element is a named record `Pair` (generic parameters as the record system allows — proven by the slice E2E, never assumed).
- **CONFLICT RESOLVED (option A):** `Pair` is listed as a fake/foreign construct in the `D-NOT-JAVA` iron rule (§8) — `Pair` was among the symbols closed as "does not exist in Kof" in the 18/09 sweep (issue #418). The maintainer's multiple-choice answer **"Record Pair"** picks option **(A)**: this scope-limited `D-*` introduces the stdlib `Pair<A,B>(A first, B second)` and supersedes the fake-idiom entry for this type only; `zip` yields `List<Pair<T,U>>`. Recorded values: home = stdlib prelude (`dev/kof/pairs.kf`), field names `first`/`second`, accessors `p.first()`/`p.second()`. **IMPLEMENTED 30/09** (`80dd32b50`: `CompilerPairs`/`CollectionZipLowerer` rewrite, `ListZipE2ETest` JVM/Script/JS green; native leg blocked by an independent backend gap — bare type-variable `List.get` corruption, `§271` erasure ABI). Registered in `docs/development/README.md` §3.
- **Relationships:** `Related: D-MULTIPARADIGMA-PHASE1A, D-MULTIPARADIGMA-GO, D-NOT-JAVA, rule 6`.

## D-MULTIPARADIGMA-ZIP-NATIVE — `zip` is refused at compile time on the native targets when either list element type is a primitive (maintainer 30/09/2026, multiple-choice "B — honest NAT008")

**State:** DECIDED (maintainer) — supersedes the native-BLOCKED cell of `D-MULTIPARADIGMA-ZIP` (slice 1i); the managed surface (JVM/Script/JS) is unaffected.

- **Scope:** `List<T>.zip(List<U>)` keeps `Pair` + `min` truncation on JVM/Script/JS. On every native target (`NATIVE`, `NATIVE_RISCV64`, `NATIVE_AARCH64`, plus the MCU targets), a `zip` whose receiver/argument element type is a `PrimitiveType` (or still `Unknown` at lowering) is refused at compile time with the honest gap code **`NAT008`** — never a `SIGSEGV` (R6).
- **Root cause (measured 30/09):** native lists of a concrete primitive element store the value **raw** (`kof_list_get` returns the raw qword); when the same list is viewed through a bare type-variable (`zipPairs<A,B>` reads `xs.get(i)`), the erasure contract says "reference" and the call-site emits `kof_unbox_*`, which dereferences the raw integer as a pointer → `SIGSEGV` (rc=139). Proven without zip/injection by `firstOf<T>(List<T>): T { return xs.get(0) }` → rc=139 on native, `1` on JVM. Reference-element zip works natively (measured), so only primitive elements are refused.
- **Deferred (honest gap):** the representation fix (box at the generic erasure boundary, cross-target) is a backend front; when it lands, `NAT008` is deleted and native `zip` is re-enabled for every element type. Recorded in `docs/backend-parity.md` (Documented Gaps) like `NAT006`/`NAT007` — a deliberate, named limitation, not a bug in the open queue.
- **Relationships:** `Related: D-MULTIPARADIGMA-ZIP, D-MULTIPARADIGMA-PHASE1A, D-KOF-FIRST-IMPL, rule 6`.

## D-MULTIPARADIGMA-GROUPBY — `groupBy` as specified in plan §230 (maintainer 29/09/2026, multiple-choice "Aprovar especificado")

**State:** DECIDED (maintainer) — unblocks the `groupBy` remainder of `PLAN-MULTIPARADIGMA.md`.

- **Scope:** `List<T>.groupBy((T)->K): Map<K,List<T>>` exactly as §230 (insertion-order map of groups; keys with the boxed map equality); eager, additive.
- **Relationships:** `Related: D-MULTIPARADIGMA-PHASE1A, D-MULTIPARADIGMA-GO, rule 6`.

## D-SCRIPT-WARN-SURFACE — the Script target surfaces frontend WARNING diagnostics like JVM/JS/Native (maintainer 29/09/2026, multiple-choice "A" on #678)

**State:** DECIDED (maintainer) / IMPLEMENTED 29/09 — resolves the #678 decision request (diagnostics-parity divergence of phase-6, `docs/development/memory-safety-plan.md`).

- **Question (#678):** a terminating `for-in` + `list.remove(0)` emits `MEM022` on JVM/Native/JS, but `driver.interpret` returned `exit=0, stderr=[]` — `CompilerPipeline.prepareForInterpretation` built a local `DiagnosticCollector` and discarded it (only ERRORS escaped via `KofInterpretException`), so every WARNING (`MEM022`, `MEM014`) was invisible on Script, contradicting the plan DoD ("same sources, same diagnostics").
- **Decision:** option **A — surface the warnings**. The interpreter exposes the frontend WARNINGs through `KofInterpreter.Result.warnings()` (additive record component with a compat 3-arg constructor); the CLI/`KofScript` print them to stderr exactly like the compile path. No language-semantics change — diagnostics only.
- **Not chosen:** (B) leave Script warning-free by documented scope (rejected: the signal is the contract, and `list.add` during iteration is a runaway loop where the warning is the only signal).
- **Proof to require:** the terminating reproducer yields `MEM022` in `Result.warnings()` on Script (RED before, measured 29/09), plus no regression on the interpreter battery.
- **Relationships:** `Related: D-MEMORY-SAFETY, D-SCRIPT-EXTERN-REFUSE, rule 5, rule 6, rule 7`; tracker #678.

## D-CONNECTORS-GO — the Kof Connector Ecosystem plan is promoted to current work (maintainer 29/09/2026, multiple-choice "connectors" + `D-FUTURE-PROMOTION`)

**State:** DECIDED (maintainer) — `docs/development/kof-connector-ecosystem-plan.md` moves to `docs/development/` with status UNDER DEVELOPMENT; one front at a time.

- **Scope:** Interop Core + Connector SPI/manifest + catalogue, building on the existing FFI/ABI substrate (never duplicating it, rule 54); scope control rule 55 (prove with few connectors first — Java first, no 30-runtime waterfall); official-packages layering (R1) and interop-first (R9) apply.
- **Relationships:** `Related: D-FUTURE-PROMOTION, D-FUTURE-BATCH-2809B, rule 6, rule 54, rule 55, R1, R9`.
## D-IMAGE-SURFACE — `kof.image` value surface reuses `Raster`; codecs are pure Kof when feasible, JVM imageio only where infeasible (maintainer 29/09/2026, chat decision)

**State:** DECIDED (maintainer) — closes the rule-6 decision request opened with the image-vision promotion.

- **Surface:** no new `Image`/`Pixel`/`Color` types; the value is the existing `Raster(format, width, height, channels, samples)`. `decode(path): Raster` covers every supported format.
- **Codecs:** implement in **pure Kof** whenever feasible — full cross-target parity, no gap (`PNM`, `farbfeld`, `BMP`, `QOI` today). Use JVM **imageio** interop only where a pure-Kof decoder is technically infeasible (JPEG, and GIF/WebP/AVIF unless prioritized), with an honest compile-time gap on the other targets — never a silent fallback, and never a gap added "just to add one".
- **Plan:** `docs/development/image-vision-plan.md` §34 (TODO) lists the missing decoders, ordered by cost (PNG pure-Kof via zlib inflate = high value highest; JPEG = imageio).
- **Progress:** slice 2f LANDED — pure-Kof QOI decode on all targets; `RasterDecodeE2ETest` 7/7 (JVM + Native x86-64/riscv64 + Script).

- **Relationships:** `Related: D-IMAGE-VISION-GO, D-FUTURE-BATCH-2809, D-FUTURE-PROMOTION, D-KOF-FIRST, D-KOF-FIRST-IMPL, R1, R9, rule 6, rule 11`.

## D-WEBP-LOSSY-PURE-KOF — WebP lossy (`VP8 `) and AVIF decode as a pure-Kof decoder on all targets, no third-party imageio plugin (maintainer 30/09/2026, multiple-choice "decoder VP8 lossy em Kof puro")

**State:** DECIDED (maintainer) — closes the last image codec gap; supersedes the "interop/gap" note in `image-vision-plan.md` §34 PENDING.

- **Question:** the last image decoders — WebP lossy `VP8 ` and AVIF — cannot ride the JVM `imageio` escape hatch: OpenJDK 25 `javax.imageio` has **no** WebP or AVIF reader (measured 30/09; `ImageIO.getImageReadersByFormatName("webp"/"avif")` empty), so `image.decode` would need a third-party plugin (TwelveMonkeys / an AVIF lib) — a dependency decision. The maintainer chose to keep interop-first for JVM-only formats but to **reject** a new dependency for this.
- **Decision (option C):** implement the **VP8 lossy decoder in pure Kof**, on all targets (JVM + Native x86-64/riscv64/aarch64 + JS + Script), library-first, no compiler change — the same shape as the VP8L slices. AVIF follows the same route (its intra codec is a later, separate increment).
- **Why pure Kof over imageio:** `D-IMAGE-SURFACE` says pure Kof whenever feasible; a full third-party imageio plugin set would break the offline (`mvn -o`) build and the "no reimplement, no gratuitous dependency" balance. VP8 lossy is large but bounded and fully described by RFC 6386.
- **Slices (each one complete unit, tested):** (1) RIFF/`VP8 ` chunk parser + frame-header + the boolean **range decoder** (RFC 6386 §7); (2) the per-macroblock mode/segment header + coefficient probability tables; (3) intra prediction (`VP8 ` keyframes are all-intra) + the inverse DCT/WHT + reconstruction; (4) the in-loop deblocking filter; (5) the adaptive (non-keyframe) path — if in scope.
- **Honest boundary:** the pure-Kof key-frame chain is complete (slices 1–7, 30/09–01/10) and `decodeRaster` routes a lossy WebP through it (`libs/image/Vp8Raster.kf`); the adaptive (non-keyframe) path and multi-partition token streams remain explicit `IMAGE:` refusals (no half decode, no stub, Q7).
- **Relationships:** `Related: D-IMAGE-SURFACE, D-IMAGE-VISION-GO, D-KOF-FIRST, D-KOF-FIRST-IMPL, R1, R9, rule 6, rule 11`; plan `docs/development/image-vision-plan.md` §34.

## D-KOF-IS-KOF — Kof source never embeds HTML, CSS or JavaScript (maintainer directive 29/09/2026: "NÃO ENFIAR HTML NEM JS DENTRO DE CÓDIGO KOF. KOF É KOF")

**State:** DECIDED (maintainer) — absolute rule.

- **Rule:** a Kof program expresses intent with Kof primitives and idioms only. HTML tags, CSS (classes/inline styles) and JavaScript must never be pasted into Kof source — including as string/text-block payloads that build a UI, wire behavior or inject script (e.g. `"""<div onclick=...>"""`).
- **Why:** Kof is not markup in disguise (`docs/philosophy.md` §"It is not markup in disguise"); importing a foreign stack's syntax into `.kf` breaks the language surface (`AGENTS.md` rule 11), the domain separation (rule 3) and cross-target honesty (rule 5). `kof.ui`/`kof.web` declare intent and each target's backend renders it; heavy web concerns are platform/official-package responsibility (boundary rule).
- **What to do instead:** if Kof cannot express the intent, the missing piece is a Kof abstraction (library-first, `D-KOF-FIRST`) or a maintainer decision — never foreign syntax or a foreign-code payload. Interop, when truly needed, goes through the sanctioned FFI/official-package path, not embedded markup/script.
- **Scope:** all Kof targets and all official libraries; applies to source, test fixtures and documentation examples alike.
- **Relationships:** `Related: D-KOF-FIRST, D-KOF-FIRST-IMPL, D-GRAPHICS-GAMING, AGENTS.md rules 3/5/11, docs/philosophy.md`; anti-pattern: `training/anti-patterns/embedded-html-js.md`.

## D-MEM030-BORROW-RUNTIME — B-03 gets its RUNTIME half: a writable-borrow state on `Buffer(U8)`, with total cross-target proof (maintainer 30/09/2026, multiple-choice "Borrow-state + prova cross total")

**State:** DECIDED (maintainer) — extends `D-MEM020-COMPILE`; the maintainer rejected accepting the runtime half as a gap. Implementation belongs to the memory-safety lane (front `docs/development/memory-safety-plan.md`, phase-5 unit 4).

- **Question:** `MEM020` (B-03, `MemRule.java:36` `COMPILE_AND_RUNTIME`) shipped only the compile face over `OwnershipPass` (`D-MEM020-COMPILE`); the runtime half ("Passing same `Buffer` to two concurrent FFI calls without sync") was left unshaped because option B needed a new core primitive. The maintainer decided 30/09 that no gap is accepted — the runtime half must be developed.
- **Decision:** implement **option B — runtime writable-borrow state on the `Buffer` object**: a borrow flag (and owning task identity) held by the native/JVM/JS `Buffer` runtime; an `extern` INOUT write attempts to acquire an exclusive writable borrow, and a second concurrent writable borrow (two `spawn`s, or worker×parent without `await`) raises a runtime `MEM020`. Single writers and an intervening `await` stay clean (same shape as the compile face).
- **Total cross-target proof required:** the primitive and the E2E must be byte-identical on **all six** reachable faces — JVM, Script, JS, Native x86-64, Native riscv64, Native aarch64 — with a negative case (concurrent writers → `MEM020`) and a positive control (single writer / awaited → clean).
- **Not chosen:** leaving the runtime half unshaped as a documented scope cut (rejected by the maintainer, 30/09); compile-only enforcement was already the previous decision.
- **Relationships:** `Related: D-MEMORY-SAFETY, D-MEM020-COMPILE, D-MEM021-SCALAR, D-BUFFER-INOUT-NATIVE, D-R3-BUFFER, rule 6, rule 12`; tracker `#668`.
- **Addendum (maintainer 30/09/2026, follow-up multiple-choice "B-03 negative case on JS/Script" — chosen "Primitive on all 6; negative by structure on JS/Script"):** the primitive lands on **all six** faces; the **negative** case is proven by execution where preemption exists (JVM virtual threads, native pthreads x86-64/riscv64/aarch64) and by documented **structural unreachability** on JS (its `spawn` lowers to `async`/`await` — cooperative, single-threaded) and Script (`extern` is refused at compile time with `FFI001`; there is no `Buffer` surface at all). Nothing is accepted as a code gap.
- **State 30/09 (this lane):** JVM, JS, Native x86-64 and cross riscv64/aarch64 primitives **LANDED**. The cross **negative** race **was BLOCKED** by `known-bugs §545` (any cross `spawn` worker calling an `extern` SIGSEGVs: the raw `clone` starts the worker with `tls=0` → `tp` invalid; pre-existing native/cross defect, not this front) — **§545 FIXED 30/09**, so the race now runs on cross too (`BufferRuntimeBorrowE2ETest` **8 run / 0F / 0 skip**, the cross negative previously `@Disabled`; the x86-64 negative raises exactly one `MEM020`). Script is **structural N/A** (no Buffer/extern surface).
- **Addendum 30/09 (§545 unblock):** `known-bugs §545` was fixed by giving the raw-clone worker a real TLS pointer — `NativeRiscvSpawn` calls the loader's `_dl_allocate_tls(NULL)` and passes the block as the `clone` `tls` argument (the flag set already carries `CLONE_SETTLS`), and `NativeArchEmitter` forces the dynamic link whenever `usesSpawn` (the symbol lives in `ld.so`; a weak reference would be relaxed to null under `--gc-sections`). This removes the last blocker on the total cross proof of this decision; aarch64 inherits via the translator.
- **Addendum 30/09 (cross release correction, found while fixing `known-bugs §546`):** the cross writable-borrow **release** re-read the `Buffer(U8)` object from the argument block, which the C callee may overwrite (measured with glibc `memset` overwriting the block), so the flag leaked and a later sequential writer would raise a spurious `MEM020`. `NativeFfiCallRiscv` now saves the object in a reserved frame scratch slot (the x86-64 scheme) and the release reads it there. Proof: `BufferRuntimeBorrowE2ETest#sequentialWritersReleaseBorrowCross` (RED-first SIGSEGV → GREEN); the full class is 8 run / 0F / 1 skip.

## D-MEM-PHASE6-4BACKENDS — phase 6 parity is the FOUR real backends (JVM/Native/JS/Script); WASM leaves the spec contract until a backend exists (maintainer 30/09/2026, multiple-choice "Reescrever a spec para os 4 backends reais")

**State:** DECIDED (maintainer) — rewrites the phase-6 scope in `docs/spec/memory-safety.md` (§12 roadmap + Appendix) and the plan DoD.

- **Question:** the spec named "JVM / JS / WASM" as the phase-6 parity set, but the tree has **no WASM backend** (measured 28/09, #671; `docs/backend-parity.md` = JVM × Native × KofJS — no WASM column). The DoD also referenced a dead "§27 questions" anchor that does not exist in the spec.
- **Decision:** phase 6 parity is defined over the **four backends that exist** — **JVM, Native, JS, Script** (plus the Native cross ISAs riscv64/aarch64 used for the memory tests). WASM is **not** an open gap of this front: it re-enters the contract only when a real WASM backend lands (never before). The dead "§27" reference is corrected to the spec's twelve sections.
- **Relationships:** `Related: D-MEMORY-SAFETY, D-WASM-01, D-KOF-IS-KOF, D-KOF-FIRST, rule 5, rule 6`; tracker `#671`.

## D-MEM-FFI-CROSS-FULL — cross FFI parity is total before the front closes: `String[]`, memory-path structs, callbacks and out-buffer land on riscv64/aarch64 too (maintainer 30/09/2026, multiple-choice "Paridade total cross antes de fechar")

**State:** DECIDED (maintainer) — extends `D-BUFFER-INOUT-NATIVE`; the maintainer rejected accepting the remaining cross refusals as permanent gaps.

- **Question:** Native x86-64 binds scalar externs, scalar `T[]`→`ptr`, struct by-value and `Buffer(U8)` INOUT, while cross riscv64/aarch64 still refuse `String[]`, memory-path structs, callbacks and out-buffer with `FFI001`/`FFI002`.
- **Decision:** implement those four faces on the cross runtimes (`NativeFfiCallRiscv` + the shared FFI binding/ABI) with the same ABI as x86-64, and require **byte-identical** proof JVM ≡ riscv64 ≡ aarch64 for each face before memory-safety is declared done. Nothing is accepted as a permanent gap.
- **Relationships:** `Related: D-MEMORY-SAFETY, D-BUFFER-INOUT-NATIVE, D-MEM020-COMPILE, D-R3-BUFFER, D-FFI-STRUCT, rule 6, rule 12`; tracker `#651`.
- **State 30/09 (this lane) — faces 1–3 of 4 LANDED:** face 1 = cross **scalar `T[]`→`ptr`**: gate `CompilerFfiBinding` no longer restricts arrays to x86-64, `FfiStructLayout.crossBindable` counts an array-ptr as one INTEGER ordinal, `NativeFfiCallRiscv` packs per call via the new `kof_ffi_pack_array` riscv helper (per-program, `NativeArchEmitter`); proof `FfiNativeArrayE2ETest#scalarArrayCrossBindsAndMatchesJvm` (JVM == riscv64 == aarch64 byte-identical through a real cross `.so`; 5 element widths). Face 2 = `String[]`→`char**`: a separate marker (`FfiSignature.isStringArray` + `pS` token, JS stays `FFI002`) packs one NUL-terminated cstring per element (String payload at offset 24, `null`→0) via `kof_ffi_pack_str_array` (riscv + x86) and `kof_ffi_copy_in_strings` (JVM FFM); proof `FfiNativeStringArrayE2ETest` 2/2 (JVM == x86-64 == riscv64 == aarch64 byte-identical) and the former `String[]`→`FFI001` pins became bind assertions. The x86 asm helpers were extracted to `NativeFfiAsmHelpers` (`NativeFfiCall` 467 < 600). Out-buffer (`Buffer(U8)`) was already cross-bound by `#651` fatia B. Face 3 = **memory-path structs (sret)**: the gate accepts a `byMemory()` struct return (`FfiStructLayout.crossMemoryReturn`), `crossBindable(List,int)` reserves 1 INTEGER register (the RISC-V `a0` sret pointer; AAPCS64 `x8` does not consume one, reserved conservatively), and `NativeFfiCallRiscv` allocates the C buffer before the call, keeps its pointer in a reserved frame scratch slot across the call, and reconstructs each field from its C offset; the pointer register is **arch-aware** (`a0` riscv64 / `a7`→`x8` aarch64, matching the measured divergence — the first real argument shifts to `a1` only on riscv64). The **>16 B by-value PARAM** (also `byMemory`/BYREF) is covered in the same unit: measured 30/09 with cross-gcc, both riscv64 and aarch64 pass it as a pointer in `a0`/`x0`, so `NativeFfiCallRiscv` passes the object payload `obj+16` as one INTEGER (`FfiStructLayout.crossByMemory`; `crossBindable` counts one pointer). Proof `FfiNativeStructReturnE2ETest` 2/2: a real cross-compiled `.so` `Big{long,long,long}` return, golden byte-identical **JVM (FFM SegmentAllocator oracle) == x86-64 == riscv64(qemu) == aarch64(qemu)**; `FfiCrossStructParamE2ETest` 8/8 (`bigsum(Big,long)`, `142` under qemu both archs). **Remaining face (callbacks) — STOP rule 6/12, measured 30/09:** callbacks do not bind on **any** native target today (x86-64 included): the JVM/JS build a C function pointer via `Linker.upcallStub`, which has no bare-metal equivalent, so a cross callback needs a new MECHANISM — a native trampoline that turns a Kof function value (a heap object with an `invoke`) into an executable stub that marshals the C ABI and `j`s into the closure. There is no such mechanism in the corpus and the maintainer controls architecture (rule 6), so this face is **not invented here**; it is recorded as a decision request (the mechanism also unblocks x86-64 callbacks).

## D-CLI-SOURCE-ROOTS — two Kof source roots (app × test) are declared in `kof.toml [sources]`; the CLI honors them without copies (maintainer 30/09/2026, multi-choice)

**State:** DECIDED (maintainer) + IMPLEMENTED (30/09) — tracker `#708` (case `renanfranca`/SiFuture #2).

- **Question:** how (and whether) the CLI exposes separate application and test source roots (`src/main/kof` × `src/test/kof`), so a project can build the real sources and run suites that import them without copying into a temp tree.
- **Decision:** declare the roots in the manifest — `[sources] app = "src/main/kof"`, `[sources] test = "src/test/kof"` (D1); a declared root is discovered **recursively** (subdirectory = package), the positional `kof build <dir>` keeps the historical one-directory-one-package discovery (D2); the test root reuses the existing `dependencySourceRoots` source-path to resolve `import` against the app root (D3); the acceptance covers **all real test targets** (jvm/native/js; D4). `kof build`/`kof test` with no positional argument use the declared roots; without a manifest or a declared root they fail explicitly (R6), never a silent no-op.
- **Not authorized:** language/syntax change, a new core primitive, or automatic two-root inference from directory names alone (the roots are explicit in `kof.toml`).
- **Relationships:** `Related: D-APP, D-APP.REF, D-KOF-FIRST, D-MEM-FFI-CROSS-FULL, rule 6, R6`; the three interface-independent defects were fixed earlier in the same issue (commit `687570a64`).

## D-SIZE-BUDGET — open the KOF distribution-size front; **Phase 1 = measurement only** (maintainer 01/10/2026, "Aprovado" on the #704 rule-6 request — the recommended option A)

**State:** DECIDED (maintainer) — tracker `#704` (case `jonasrochanasajon`). Phase 1 NOT yet implemented (next promotion candidate per `D-FUTURE-PROMOTION`).

- **Question:** the distribution has no size contract. `kof-cli` is shaded (`maven-shade-plugin`), so every new runtime/compiler dependency is paid by every user; PDFBox (`#629`) is the first concrete case.
- **Decision (approved = option A):** open the front with **Phase 1 = observability only** — measure the toolchain (module jars, compressed and installed distribution), attribute bytes per dependency (direct, transitive, top 20), measure a `hello-world` per target (JVM, Native x86-64/riscv64/aarch64, JS, Script), and generate a `size diff` between two commits. **No behaviour, dependency, packaging or safety change; no CI block in Phase 1.**
- **Not authorized (each needs its own later decision):** any reduction, removing functionality/target/test/diagnostic/security for bytes, removing GraalJS, enabling `minimizeJar`, changing the default packaging, defining MB limits before a baseline exists, or putting PDFBox in the core.
- **Acceptance (Phase 1):** a reproducible baseline (commit + environment + sizes) versioned under `docs/audits/`, modules and distribution measured, dependencies attributed, `hello-world` per target, working inter-commit diff — with zero dependency or behaviour change.
- **Consolidating rule (target):** an optional capability has an optional cost (an app that does not use PDF pays 0 for PDF).
- **Relationships:** `Related: D-KOF-FIRST-IMPL, D-APP (--fat optional), D-KOF-FILE-GO, R1, R9`; independent of `#629` (where PDFBox lives is a separate decision that may follow this rule). Ledger `post-1.0`.

---

## D-AGENT-IDENTITY-IPPORT — every agent claim in DOING carries `<local-ipv4>:<opencode-port>`, absolute and mandatory (maintainer 01/10/2026, chat directive "DEIXA A REGRA ABSOLUTA PARA TODOS OS AGENTES. SEMPRE MARCAR IP E PORTA NO DOING. VIROU BAGUNÇA MESMO COM ESSA REGRA, PRECISO QUE REFORCE")

**State:** DECIDED (maintainer) — codified 01/10 in `AGENTS.md`/`AGENTS.pt_BR.md` (§Authority identity + §Operating-loop claim step + §Multi-agent state claim block + §Final self-check) with enforcement in `scripts/check_owner_identity.sh`.

- **Question:** a claim with only an IPv4 (or only "this session") is ambiguous: routers/DHCP change the IPv4 and multiple sessions can run on the same host, so the claim cannot be verified as the same owner later.
- **Decision (absolute):** every `IN PROGRESS` / `DONE` / `FIXED` / `STOP` claim in `DOING.md` (EN) / `DOING.pt_BR.md` (PT) MUST carry `owner = <local-ipv4>:<opencode-port>` (EN) or `dona = <local-ipv4>:<opencode-port>` (PT). A bare IPv4 or a "this session"/"esta sessão" is INVALID and the gate `scripts/check_owner_identity.sh` rejects it rc=1.
- **Enforcement:** `scripts/check_owner_identity.sh` scans `DOING.md` + `DOING.pt_BR.md`, polices only claims dated ≥ `01/10` (no retroactive enforcement), and fails on: (a) an `owner`/`dona` value that lacks `:<port>`, (b) a bare "this session"/"esta sessão" string. `--selftest` fixtures `ok.md`/`bad.md`/`ptbad.md` prove accept/reject have teeth.
- **Port source:** the opencode server port the session attaches to — read from `ss -tln | grep opencode` (the running `opencode -s ... --port <N>`) or from the `--attach http://127.0.0.1:<N>` argument of the current `opencode run`.
- **Lane identity confirmation (extended):** an agent never acts on another owner's lane on IP alone — the confirmation requires session + lane + commit SHA + **IP:PORT** together (a router/DHCP may change both, so IP alone is stale).
- **Not authorized:** shipping a claim without `:<port>` (the gate blocks it), editing another lane's claim, retroactively rewriting historical claims with dates < `01/10` (the rule is not enforced backward; historical claims stay as-is as evidence).
- **Relationships:** replaces the 21/09 "confirm by SHA/IP" phrasing with the stronger **IP:PORT**; orthogonal to `D-KOFMD-OPERATING-STANDARD`; complements `check_release_blockers.sh` (ledger hygiene) and `check_live_records.sh` (live-doc truth).

---

## D-UDP — UDP / datagram (connectionless) network is an authorized queue front; the surface awaits definition (maintainer 01/10/2026, directive "kof nao tem suporte a UDP adiciona na fila pra por em network, isso é crucial")

**State:** ANSWERED 01/10 — surface decided and SUBSUMED by `D-KOF-NET` (unified `kof.net`, TCP+UDP, blocking+spawn, `Byte[]`, "host:port" addressing, 64 KiB bound, unicast-only v1); the plan is `docs/development/future/network-udp-plan.md` (+PT).
- **Directive (maintainer):** Kof must support UDP; it is queued **under the network front** (`roadmap.md` §3). The directive authorizes opening the front; it does **not** yet fix the surface.
- **Measured absence (01/10/2026):** a tree sweep finds **0** hits for `udp`/`datagram`/`SOCK_DGRAM` in `kof-compiler/src/main` (only a TCP/UDP port-range comment); `KofNet.java` is **URI parsing only** (the `net` stdlib S8 extension); the real transport is TCP (`runtime/RuntimeNet.java` + `KofWeb` + native raw syscalls). No `backend-parity.md` cell exists.
- **Approach (library-first, `D-KOF-FIRST-IMPL`):** implement over the **existing socket seam** per target (JVM `DatagramSocket`, Native `SOCK_DGRAM` + `sendto`/`recvfrom`, JS node `dgram`; browser = honest gap; Script inherits JVM or an explicit refusal). No lexer/parser change; each unsupported target gets an honest gap code (R6/R7).
- **Awaiting definition (7 open questions, plan §5):** (1) namespace `kof.udp` vs an extension of `kof.net`; (2) message type `String` vs `Buffer(U8)` bytes vs a `Datagram` record; (3) peer/address representation; (4) blocking vs timeout vs callback (`udp.listen`) receive; (5) maximum datagram size (bound + refuse, never truncate); (6) broadcast/multicast in v1 or later; (7) whether UDP obeys the `app.security`/policy model.
- **Promotion:** when the maintainer answers the questions, the plan is rewritten `UNDER DEVELOPMENT`, moved out of `future/` (+PT) and promoted one-at-a-time per `D-FUTURE-PROMOTION`; the JVM slice lands first (RED-first E2E over a real loopback socket), then the native and JS faces with their gaps.
- **Relationships:** `Related: D-KOF-FIRST-IMPL, D-SPRING, D-FUTURE-PROMOTION, D-KOF-FILE-GO (heavy codecs R1), rule 6, rule 12`; queue row in `docs/development/README.md` §3 (+PT).
## D-KOF-MATH-TRIG — DECISION REQUEST: does the `math` namespace gain trigonometry (`sin`/`cos`/`tan`/`asin`/`acos`/`atan`/`atan2`/`toRadians`/`toDegrees`) and constants (`pi`/`e`/`tau`)? (OPEN — rule 6, maintainer owns architecture; tracker `#717`)
**State:** DECISION REQUEST (OPEN) — external contributor `#717` (01/10/2026). Filed for the maintainer; **nothing implemented** (a new public stdlib surface + a native link-policy change are architecture, rule 6).
- **Question:** open the `math` trigonometric surface (`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2(y,x)`, `toRadians`, `toDegrees`) and the zero-arg mathematical constants (`pi()`/`e()`/`tau()`), and with what target contract?
- **Measured today (01/10):** `KofMath.functions()` = `abs/sign/clamp/min/max/isEven/isOdd/isPositive/isNegative/isZero/sqrt/lerp/percentage/isInteger/isDecimal/roundTo/pow/parse*`; **zero** hits for `sin|cos|tan|asin|acos|atan|atan2|toRadians|toDegrees|pi|tau` across the tree. The `PLAN-STDLIB-EXPANSION` §2 `math` gap row does NOT list them, so this is a genuinely new surface, not a queued face.
- **Faces the decision must fix (each a frozen-contract or architecture choice):**
  1. **Surface/shape** — name, arity (`atan2(y,x)` vs `atan2(x,y)`), type (`sin(Double)->Double`; is an `Int` argument silently widened or refused, like the `sqrt`/`lerp` `SEM025` type guard?), `Float` handling, and whether `pi`/`e`/`tau` are **functions** (`pi()`, the `uuid.v4()` zero-arg precedent) or **constants** (no such surface exists today).
  2. **Cross targets** — `sqrt` closed `MATH001` on riscv64/aarch64 with a **libm-free** `fsqrt.d`; `pow` closed cross **by linking libm by-use** (`D-DECISION-BATCH-2709B` #3; `NativeCrossLink.needsLibm` scans for `call pow`). Trig has **no libm-free primitive**, so the honest cross face is the `pow` precedent (link libm by use) — a link-policy decision, not an agent edit.
  3. **Determinism** — the JVM oracle would be `java.lang.Math`; byte parity across JVM/JS/Native x86-64/riscv64/aarch64 requires the same underlying implementation or an accepted ULP tolerance (the corpus rule is byte-identical golden, rule 5).
- **Not authorized here:** adding any symbol, touching the native link policy, or shipping `MATH001`/a new gap code as the end state without a decision.
- **Classification:** `post-1.0` per the #717 labels (new stdlib surface, not a 1.0 blocker). The issue author's claim that the work already exists ("12 symbols in `KofMath.functions()`", "`KofMathTest` 29 run") is **NOT** in the tree — measured above.
- **Relationships:** `Related: D-DECISION-BATCH-2709B (#3 pow = libm), D-FULL-PARITY-050, D-KOF-FIRST, rule 5, rule 6, rule 11`; plan `docs/stdlib/PLAN-STDLIB-EXPANSION.md`.

---

## D-KOFSHARE-100KOF — KofShare is a 100% Kof application (server + client); interop is NOT a product route; the §559 capability decision is (a) stdlib front (maintainer 01/10/2026, directive "o kofshare é 100% feito em kof" / "kofshare é um aplicativo, um servidor e um cliente")

**State:** DECIDED — closes the §559 rule-6 choice; opens the `kof.net` socket-front queue front

- **Directive (maintainer):** KofShare — the P2P file-share product (server + client, repo `kof-share`) — is written **entirely in Kof**. JVM interop over `java.net.ServerSocket`/`KeyAgreement`/`Signature` is FORBIDDEN as a product route.
- **What interop was:** the §559 probe evidence — the fastest way to MEASURE the missing capabilities (transport, key agreement, signing) and to derive the transfer protocol semantics (X25519 + Ed25519 + AES-GCM + HMAC framing, green 01/10). The probes stay as evidence; the route dies.
- **Decision consequence (§559):** option **(a)** — the data-plane surface becomes an official stdlib front: connection-oriented sockets in the network namespace (the smallest primitive per `D-KOF-FIRST`), and a key-agreement/key-exchange face in `kof.security` (`SECN005` family). Until the front ships, **KofShare is BLOCKED on stdlib capability, not on product code** — an honest block, never a silent interop fallback (`no-silent-fallback`).
- **Architecture home:** TCP/listen/accept/connect joins the network front already authorized by `D-UDP` (same family, same namespace question — the UDP plan's open question (1) `kof.udp` vs extension of `kof.net` is now ALSO the TCP naming question; answer it once, for both).
- **What this does NOT authorize:** inventing the socket surface without the maintainer's answer to the namespace/blocking/type questions (rule 6); shipping KofShare on interop anyway; a private per-product C shim.
- **Relationships:** `Depends on: D-KOF-FIRST, D-KOF-FIRST-IMPL, D-UDP (naming question), §559 (catalogued home)`

---

## D-KOF-NET — unified network front `kof.net` (TCP + UDP), blocking verbs + `spawn`, `Byte[]` payload, UDP addressed by `"host:port"`, 64 KiB bound, unicast-only v1 (maintainer 01/10/2026, rule-6 votes in chat)

**State:** DECIDED — the surface contract of the stdlib network front; unblocks the KofShare block (`D-KOFSHARE-100KOF`/§559-a); implementation goes through the promotion flow

- **Namespace:** ONE `kof.net` for both transport families, alongside the existing URI accessors (`net.scheme/host/port/path/query/fragment/queryEncode/Decode` — measured: all take a URL String, no verb collision). TCP verbs and UDP verbs live in the same namespace.
- **Scope decision:** TCP and UDP in the SAME front/v1 (maintainer chose "TCP + UDP together now", rejecting the TCP-first path) — the queue item ships as one coherent network front.
- **Blocking model:** BLOCKING verbs (`listen/connect/accept/send/receive` block the calling worker); parallelism is the existing Kof concurrency — `spawn` one worker per connection/endpoint. No async/await I/O machinery is introduced (none exists measured for I/O; no-silent-fallback).
- **Payload type:** `Byte[]` flows both ways on both transports (stream bytes for TCP, one datagram per `send` for UDP). No `Message`/`Datagram` record, no String convenience overload in v1 (one way to do it; `encoding` namespaces convert).
- **UDP addressing:** endpoint/peer = `String "host:port"` (e.g. `"127.0.0.1:9000"`); `receive` yields the bytes AND the source address in that form. No new `Addr` type in v1.
- **Datagram bound:** 64 KiB practical IPv4 limit — `bind`/`send` REFUSE larger with a `NET00x` diagnostic; no transparent fragmentation, per-target behavior identical.
- **Broadcast/multicast:** NOT in v1 (unicast only). A later face needs its own rule-6 decision.
- **Security/policy:** network endpoints obey the existing `app.security`/policy model — no new policy face is invented here. The key-exchange face (`SECN005`, `D-KOFSHARE-100KOF`'s other requirement) remains a separate surface decision.
- **Surface (contract, to be compile-validated during slice 1):** `net.listen(port) -> Listener`, `listener.accept() -> Conn`, `net.connect(host, port) -> Conn`, `conn.send(Byte[]) -> Int`, `conn.receive(maxBytes) -> Byte[]`, `conn.close()`, `listener.close()`; `net.bind(port) -> Endpoint`, `endpoint.send(addr, Byte[])`, `endpoint.receive(maxBytes) -> (Byte[], String)`-shape, `endpoint.close()`. Names/types are frozen by this decision; the exact return-shape for receive-with-source is the first design question to compile-probe (tuple absence in Kof ⇒ likely a `record Datagram(Byte[] bytes, String from)` — the maintainer's `Byte[]`/no-new-record preference is honored on the SEND side; the RECEIVE side may need the source carrier: decide via RED-first probe, keep the decision note updated).
- **Relationships:** `Depends on: D-KOF-FIRST-IMPL (library-first), D-UDP (subsumed here), §559 (catalogued gap this closes), D-KOFSHARE-100KOF (product blocked on this)`; `Resolves-questions: D-UDP plan §5 (1,2,3,4,5,6 — historical numbering, plan now a pointer)`
