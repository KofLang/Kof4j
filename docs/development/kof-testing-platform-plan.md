[English](kof-testing-platform-plan.md) | [Português](kof-testing-platform-plan.pt_BR.md)

# Kof Testing Platform — Unit / Integration / Frontend E2E

**Status:** UNDER DEVELOPMENT — promoted from `future/` 30/09/2026 (`D-TESTING-PLATFORM`, `D-FUTURE-BATCH-2809`/`B`, `D-FUTURE-PROMOTION`)
**Location:** `docs/development/`
**Owner:** `192.168.15.15:9092` (lane security/connectors — REASSUMED 10/10 per the maintainer's order: the prior owner lane issues/tooling `192.168.15.30:9093` last active 09/10, orphaned >1 day; claims MUST carry IP:PORTA, `D-AGENT-IDENTITY-IPPORT`)
**Nature:** implementation plan — real state + how to finish (design record kept below)
**Normative source:** `DECISIONS.md` §`D-TESTING-PLATFORM` (28/09, authorized — `D-FUTURE-BATCH-2809`/`B`); promotion to current work is one-at-a-time per `D-FUTURE-PROMOTION`
**Main dependencies:** the existing `kof test` command (`CmdTest`), the test language surface
(`test`/`assert`), the per-target harness (`ConformanceMatrixTest`), `KofJsRunner`,
`KofJsBrowserE2ETest`, the CLI, KofJS, the future KofWasm
**Companion plan:** `test-architecture-plan.md` (the **compiler's own Java suite** refactor —
L0–L5 layers, profiles, performance). This document is the **user-facing testing platform**;
the two meet at §13 (Performance) and must not duplicate each other.
**Implementation status:** slice 1 (assertion helpers) LANDED 30/09; slice 2 (`assertThrows`) LANDED 30/09 — the blocker was fixed (see §15); slice 3 (unit-core assertions) LANDED 01/10; slice 4 (Long/Double/Float numeric assertions) LANDED 01/10; slice 5 (Byte/Short/Char + `assertNotEqualBool`) LANDED 01/10; slice 6 (generic `assertEqual<T>`/`assertNotEqual<T>` pair) LANDED 02/10 — unblocked by the `known-bugs` §553 fix (`D-EQ-UNBOUNDED-T`), so §4.1 is now **complete**; §5 harness — slice 1 (temp dir + readiness poll) LANDED 06/10, slice 2 (database lifecycle `withDb` in the opt-in `kof.test.db`) LANDED 07/10, slice 3 (server lifecycle `withServer` in the opt-in `kof.test.web`, `spawn`+readiness+`finally` close) LANDED 08/10 — JVM-complete; the cross targets report the pre-existing `app.close`/`WEB001` gap honestly (no silent fallback); §6 browser-provider policy RECORDED 08/10 (docs-only — opt-in per project `C`, all targets `T1`, `kof.test` compiler/CLI `T2`; the provider slice itself stays gated by the open rule-6 provider-declaration decision); §8.5 runner duration measurement (per-file wall time + slowest file) LANDED 08/10, per-level breakdown LANDED 10/10 (the `levels:` line: `unit: N passed / M failed` — the level derived honestly from the declared tags; a pure-unit run keeps the historical output), the §6 provider stack LANDED 10/10 (the `kof-test.kofmd` manifest + the `kof test --tag browser` gate + the browser SPI + two providers: ChromeHeadless zero-dep and PlaywrightLib full-capability — **E2E REAL: Chromium 156 renders + real PNG**; the Kof-level browser API `kof.test.browser` LANDED 10/10 — **E2E REAL: `browserContent` via the virtual package, the REAL Chromium renders the dump-dom** — the §6 milestone COMPLETE at all levels); §5.1 `process`/`config`/`environment variables` faces RECORDED 08/10 as measured boundaries (handle not nameable + no list-varargs; no setenv primitive, config read-only — rule 6).

> **Slice 6 (LANDED 02/10).** The last §4.1 face: the generic pair `assertEqual<T>(T expected, T actual, String label)` / `assertNotEqual<T>(...)` in `dev/kof/test.kf`. It was deliberately deferred (not shipped broken) until `known-bugs` §553 was resolved: the maintainer's rule-6 answer `D-EQ-UNBOUNDED-T` (02/10) fixes `==` on an unbounded `T` as **structural content equality** on every target, so the helper is correct for any `T` (Int, String, record, …). The label stringifies `expected`/`actual` via `+` — no new primitive, no per-target runtime. Proof RED-first: new `GenericEqualityE2ETest` **16/16** (the generic pair green on JVM/Script/JS/Native and throwing on a real mismatch; the `==` semantics golden byte-identical to the JVM oracle on JVM + Script + JS + Native x86-64 + riscv64(qemu) + aarch64(qemu)); `KofTestingE2ETest` 7/7. §4.1 is complete; the remaining faces are rule-6/decision-gated (§4.4 parameterized, §4.6 test doubles, §5 harness). The **browser provider** (§6) is no longer gated: `D-MAINT-BATCH-0510`/`T1` decides it must serve **all targets** (JVM + JS + Native), and `/T2` decides `kof.test` stays a **compiler/CLI feature** (not a stdlib namespace) — see §12.

> **Slice 5 (LANDED 01/10).** The remaining §4.1 scalar surface: `assertEqualByte`/`assertNotEqualByte`,
> `assertEqualShort`/`assertNotEqualShort`, `assertEqualChar`/`assertNotEqualChar`, plus the missing
> `assertNotEqualBool` (the equality side already existed). Added to `dev/kof/test.kf` (same
> virtual-package mechanism). **No arithmetic on `Byte`/`Short`** — the helpers only compare (`!=`/`==`)
> and print, so they do not touch the `#720` arithmetic path (`known-bugs` §561 — **FIXED 02/10** by `D-KOF-BYTE-ARITH` = promote `Byte`/`Short`/`Char` to `Int`). Typed per
> primitive, not a generic `assertEqual<T>` (still deferred by §553). Additive, pure Kof, no new
> syntax/primitives. Proof: `KofTestingE2ETest` **7/7** across JVM + JS + Script + Native x86-64 +
> cross riscv64(qemu) + aarch64(qemu), golden-parity with the JVM oracle (RED pre-slice: 7 ×
> `SEM015 Undefined function` on the JVM leg). Next: integration/browser faces (§11 phases 2–4).

> **Slice 4 (LANDED 01/10).** The numeric assertions that §4.1 was still missing at the
> primitive level: `assertEqualLong`/`assertNotEqualLong`, `assertEqualDouble`/`assertNotEqualDouble`
> and `assertEqualFloat`/`assertNotEqualFloat`, added to `dev/kof/test.kf` (same virtual-package
> mechanism). Typed per primitive — **not** a generic `assertEqual<T>`, which stays deferred by
> `known-bugs` §553 (`==` on an unbounded `T` diverges across targets). Additive, pure Kof, no new
> syntax/primitives. Proof: `KofTestingE2ETest` **7/7** across JVM + JS + Script + Native x86-64 +
> cross riscv64(qemu) + aarch64(qemu), golden-parity with the JVM oracle (RED pre-slice 1/1 on the
> JVM leg: 12 × `SEM015 Undefined function` for the six helpers). Next: lifecycle (§4.2, needs a
> runner/desugar decision) and the integration/browser faces (§11 phases 2–4).

> **Slice 3 (LANDED 01/10).** The remaining §4.1 assertions added to the `kof.test` virtual package:
> `assertNotEqualString`, `assertEqualBool`, `assertNull<T>(T? value, String label)` and
> `assertNotNull<T>(T? value, String label)`. The null checks are **generic over `T?`** (erased
> per target) so they accept any nullable value and still reject the wrong type at compile time;
> nothing new was added to the language. Additive, pure Kof, no per-target runtime. Proof:
> `KofTestingE2ETest` **7/7** across JVM + JS + Script + Native x86-64 + cross riscv64(qemu) +
> aarch64(qemu), golden-parity with the JVM oracle (RED pre-slice 6/7 `SEM015 Undefined function`).
> §4.1 is complete **except** the generic `assertEqual`/`assertNotEqual` pair, deliberately **deferred**
> (not shipped broken): a generic equality helper cannot be correct on all targets until `known-bugs`
> §553 is decided — `==` on an unbounded `T` diverges (JS structural vs JVM/Script/Native reference),
> a frozen-operator question (rule 6). Next are lifecycle (§4.2) and the integration/browser faces (§11 phases 2–4).

> **Slice 2 (LANDED 30/09).** `assertThrows(() -> Void block, String label)` added to the `kof.test` virtual package. The blocker was `known-bugs` §549: the native `try` handler leaked on the normal exit path, so this exact helper took the catch on the no-exception path on native. §549 is now FIXED (new IR `KofExcUnlink` on the normal fall-through, x86 + riscv/aarch64 cross, parity in interpreter/JS; see CHANGELOG). Proof: `KofTestingE2ETest` **7/7** across JVM + JS + Script + Native x86-64 + cross riscv64(qemu) + aarch64(qemu), golden-parity with the JVM oracle (RED pre-fix 3/7). A residual native face remained (`return`/`break`/`continue` INSIDE a `try`, one of which crashed the next throw) and is catalogued as §551 — it did NOT gate this slice, and is now ✅ FIXED 01/10 (per-region depth + chain-relative unlink).

> **Slice 1 (LANDED 30/09).** Pure-Kof `kof.test` virtual package
> (`dev/kof/test.kf` resource + `CompilerTesting.java`, injected flat on the
> explicit `import kof.test`; same mechanism as `kof.pagination`/`kof.pairs`):
> `assertTrue`/`assertFalse`/`assertEqualInt`/`assertEqualString`/`assertNotEqualInt`/`fail`.
> Additive to the existing `test`/`assert` surface — **no new syntax**, no per-target
> runtime (only `throw` of `String`, already handled by all four targets); useful
> diagnostics (label + expected/actual) instead of the bare `assertion failed`. User-defined
> `assertTrue` disables injection (collision is signal, not silence). Proof:
> `KofTestingE2ETest` 5/5 (JVM + JS + Script + Native x86-64 parity-per-golden + collision).
> Next: `assertThrows`, lifecycle, and the integration/browser faces (§11 phases 2–4).

> **Fundamental rule.** This document describes a future architectural direction. It does
> **not** change the language, add keywords, create namespaces, or open an implementation
> track. Every syntax shown is an **intent form**; the definitive form belongs to the
> maintainer (rule 6).
>
> **KOF-first (rule 10) and "Kof is not Java" (rules 8/45).** The testing API is expressed in
> **Kof's grammar** — there is already a `test`/`assert` surface. No JavaScript/Jest, Kotlin,
> Python or Rust test syntax is imported. Playwright/Cypress are **execution providers**, never
> the public API.
>
> **Goal in one line:** test small when the problem is small, test integrated when integration
> matters, open a browser only when the application's behavior must be tested.

---

# 0. Objective and non-goals

## 0.1 Objective

Create an official, modular testing architecture for Kof at three levels:

```text
Unit Tests  →  Integration Tests  →  Frontend E2E / Browser Tests
```

Fast in daily development, deterministic, extensible, and able to grow with KofJS, KofWasm and
future targets. A developer must be able to go from an isolated function to a full Kof
application running in a real browser without leaving Kof.

## 0.2 Non-goals

* Not a giant framework: **do not build a "Jest + JUnit + Playwright + Cypress clone inside Kof"**.
* Not a copy of Playwright's or Cypress's JS API — the API represents **browser-testing concepts**;
  providers implement them.
* Not a replacement of the existing `kof test`/`assert` surface — it is **extended additively**.
* Not running every browser matrix / every target on every trivial change.
* Not retry-as-a-fix for flaky tests.
* Not mocking indiscriminately: real behavior when cheap and deterministic.

---

# 1. Architecture

```text
                    Kof Testing
                         │
          ┌──────────────┼──────────────┐
         Unit        Integration        E2E
          │              │              │
          │              │      Browser Automation
          │              │              │
          │              │        Kof Browser API
          │              │              │
          │              │      Browser Driver (SPI)
          │              │         ┌────┴────┐
          │              │     Playwright  Cypress (later)
          └──────────────┴──────────────┘
                         │
                    Test Runner
```

The public Kof API is **not** coupled to Playwright/Cypress. A browser-testing abstraction is
owned by Kof; providers/servers implement execution underneath.

---

# 2. First rule — analyze what already exists

Before implementing anything (rule 54). The inventory below is the real starting point;
**evolve it, do not replace it and do not duplicate it.**

| Existing mechanism | Real anchor | Role |
|---|---|---|
| `kof test` CLI command | `kof-cli/.../CmdTest.java:15` (`kof test <file\|dir> [--target jvm\|native\|js] [--timeout <sec>]`); dispatch `Main.java:22` | The runner entry point to extend |
| Test language surface | `test "name" { }` + `assert(cond[, msg])`; `assert` keyword `Lexer.java:68`, `TokenType.ASSERT`; desugar `CompilerDesugar.java:16,336` (`desugarTests`/`buildTestHarnessMain`) | The API the platform builds on — **not** new syntax |
| Test compilation pipeline | `CompilerPipeline.java:121-141` (`compileForTests`, `compileForTestsSources`, `discoveredTests`); `CompilerDriverState.java:208,223` | Harness plumbing |
| Per-target harness | `ConformanceMatrixTest.java:44-80` (`freshDriver`, `runJvm`, `runScript`, `runNative`) + 133 `matrix(...)` cases | The canonical compile-and-run oracle to generalize |
| KofJS execution | `KofJsE2ETest.java:19` (embedded **GraalJS** `KofJsRunner.run`); `KofJsHostlessRuntimeTest` | JS target testing (no Node required) |
| Browser E2E (embryonic) | `KofJsBrowserE2ETest.java` (real Chrome `--headless --dump-dom` `:38`; macOS `safaridriver` W3C WebDriver `:81`; `findBrowser` `:93`; skip `:139`) | The seed of the Browser Core; a raw mechanism, not an abstraction |
| Shared test helpers | `TestJdk.java:21-40`, `NativeToolchainGate.java:21`, `JsRuntimeTestSupport.java:19`, `CliProcessTree.java:23` | Reusable primitives (small, static) |
| Golden / integration scripts | `tests/run-golden.sh:28`, `tests/run-integration.sh:118` (`kof test` assert suite) | Release gates to keep |
| CI | `.github/workflows/ci.yml` (build+test, multiplatform, cross-native, structural), `release.yml` (golden+integration) | Profiles integrate here |
| Environment guards | `assumeTrue` in 121 files; `NativeToolchainGate`, qemu guards, DB env (`KOF_MYSQL_PORT`), `KofJsBrowserE2ETest:139` | The determinism rule already exists |
| Real app fixture | `examples/fullstack/`; `FullStackE2ETest.java:33-45,90`; `KofWebE2ETest`, `KofHttpServerTest`, `KofWebWsE2ETest`/`KofWebSseE2ETest` | Reference app + web integration |
| No `kof.test` namespace | `StdCatalog.java:34-59` lists no `test`; `kof.test` is a CompilerDriver/CLI feature (`ecosystem-coverage.md:74,367`) | Open question §12 |
| No WASM yet | `TargetMatrix.java:72` → `WASM001`; `TargetMatrixTest.java:55`; `wasm-wasi-plan.md` | Cross-target E2E must wait for KofWasm |
| No browser abstraction | confirmed: no Playwright/Cypress/Selenium/Puppeteer dependency anywhere | What this plan adds |
| Maven surefire runner | `pom.xml:86-105`; JUnit Jupiter `pom.xml:40`; no profiles | Internal suite (companion plan) |

**Conclusion:** the language already has `test`/`assert` and a CLI runner, but there is **no
shared harness, no levels/profiles/tags, no browser abstraction and no artifacts**. The missing
piece is the platform layer, not a new language.

---

# 3. Levels — clear separation

## 3.1 Unit

An isolated unit: lexer, parser, AST, semantic analyzer, type checker, IR, stdlib function,
utility, component, service.

Characteristics: fast, isolated, deterministic, no external process, no browser, no real
network, no real DB, no real filesystem when unnecessary. Target: **milliseconds** (seconds for
larger groups).

## 3.2 Integration

Real components working together: Kof compiler + filesystem; Kof + database; Kof + HTTP;
Kof + stdlib; Kof + native library; Kof + JVM; Kof + JS runtime; Kof + WASM runtime; Kof
application + backend. Real dependencies are allowed when they are part of the tested
behavior; do not mock the whole system.

## 3.3 Frontend E2E

A real application in a browser:

```text
Kof source → KofJS / KofWasm → web application → browser → real user interaction
```

Test navigation, click, keyboard, forms, inputs, links, routing, cookies, localStorage,
sessionStorage, fetch, WebSocket, upload, download, authentication, permissions, dialogs,
responsive behavior, frontend errors and frontend/backend integration.

---

# 4. Unit Test API

Build on the existing surface (`test "name" { }` + `assert`). The exact extension is a rule-6
decision; **no foreign syntax**. Before any API: study the grammar, functions, modules,
closures, exceptions/errors and the current runner.

## 4.1 Assertions

A small set, only where the type system/runtime make sense:

```text
assert(...)          already exists
assertEqual(...)
assertNotEqual(...)
assertTrue(...) / assertFalse(...)
assertNull(...) / assertNotNull(...)
assertThrows(...)
```

Assertions must produce useful diagnostics — `expected`, `actual`, `test`, `source` — never a
bare `test failed`.

**Status (02/10):** the primitive helpers landed incrementally — slice 1 (`assertTrue`/`assertFalse`/`assertEqualInt`/`assertEqualString`/`assertNotEqualInt`/`fail`), slice 2 (`assertThrows`), slice 3 (`assertNotEqualString`/`assertEqualBool`/`assertNull`/`assertNotNull`), slice 4 (`Long`/`Double`/`Float`), slice 5 (`Byte`/`Short`/`Char` + `assertNotEqualBool`), slice 6 (generic `assertEqual<T>`/`assertNotEqual<T>`, unblocked by the `known-bugs` §553 fix / `D-EQ-UNBOUNDED-T`). §4.1 is **complete**. Proof: `KofTestingE2ETest` 7/7 + `GenericEqualityE2ETest` 16/16 across JVM + JS + Script + Native x86-64 + riscv64/aarch64(qemu).

## 4.2 Lifecycle

**Status:** LANDED 01/10 (`CompilerDesugar` + `TestHarnessBuilder`).

Implemented with zero new syntax — uses the existing top-level zero-argument `Void` function
convention:
- `beforeAll()`: runs once before any tests run (in a `try/catch` reporting `beforeAll failed`)
- `afterAll()`: runs once after all tests finish
- `beforeEach()`: §4.2 alias of the existing `setup()` (runs before each test; a failure causes a named `SKIP`)
- `afterEach()`: §4.2 alias of the existing `teardown()` (runs via `finally` after each test)

Proof: `TestTagsE2ETest` (14/14 green). Maintains test isolation — never a global-state mechanism.

## 4.3 Isolation

**Status:** LANDED 01/10 (`TestHarnessBuilder`).

```text
test A → isolated state
test B → isolated state
```

Never `test A → global state → test B depends on A`. Temporary filesystem, ports, database and
resources have explicit lifecycle:
- If `beforeAll()` fails: all subsequent tests are skipped with a named `SKIP <name>: beforeAll failed`,
  the failure is counted, and the test runner exits with code 1. Tests never run against a broken/missing
  shared fixture.
- If `afterAll()` fails: the error is caught, reported (`afterAll failed: <e>`), the failure is counted,
  and the summary is printed with a non-zero exit code.
- If `beforeEach()` / `setup()` fails: that specific test is marked `SKIP` and does not run.
- `afterEach()` / `teardown()` always runs via `finally` for every test that setup allowed to execute.
- If `afterEach()` / `teardown()` throws, it is a **named failure, counted, and the run continues** (`teardown failed: <e>`) — never an uncaught throw that aborts the harness after the first test (measured defect, `known-bugs` §570, FIXED 02/10). Same contract as `afterAll()`.
- Declaring **both** `setup()` and `beforeEach()` (or both `teardown()` and `afterEach()`) is **refused at compile time** with the named error `TEST001` (`ambiguous test lifecycle: … they are the same hook; keep only one`). The two names are aliases of one hook, so keeping both would silently run only the last one (measured defect, `known-bugs` §577, FIXED 02/10).

Proof: `TestTagsE2ETest` (22/22 green).

## 4.4 Parameterized tests

Evaluate `input → expected` tables — very useful for parser, type checker, encoding, HTTP,
string processing and numeric operations. Do not duplicate dozens of tests only because inputs
change.

> **DECIDED 06/10 (`D-MAINT-BATCH-0610`/D):** §4.4 is the first authorized face of this plan's
> remaining rule-6 set (§4.6 test doubles and §5 harness follow). It stays additive test
> infrastructure — no language/semantics change; the concrete surface follows Kof grammar and
> is defined at implementation.

**Status: LANDED 06/10 (`D-MAINT-BATCH-0610`/D).** The surface is the additive `kof.test` helper
`testRows(rows, label, body)` — written in Kof, no new syntax/primitive (`D-KOF-FIRST` item 12),
injected flat on the explicit `import kof.test` like the other helpers. Each row is a
`List<String>` (the columns); `body` receives the row and asserts with the §4.1 helpers:

```kof
import kof.test

test "square table" {
    testRows(listOf(listOf("1", "1"), listOf("2", "4"), listOf("3", "9")), "square", (r: List<String>) -> {
        var input = r.get(0).toInt()
        if (input * input != r.get(1).toInt()) {
            throw "expected " + r.get(1) + ", got " + (input * input)
        }
    })
}
```

Rows run **in isolation** — one failing row does not abort the others — and the failures
aggregate into **one** named message (`<label>: N of M rows failed` + `row <i> <row>: <why>`),
which the harness reports as `FAIL <test>: …` (throw of String, the same path on all targets).
A uniform `List<String>` table is the honest complete increment: a generic `(T) -> Void` is
refused at compile time (`SEM085`, erasure ABI is 1.0-line), so a table per typed record would
need a helper per type — no ceremony. **Proof:** `TestRowsE2ETest` **7/7** (passing/failing
tables, row isolation, named bad row) across JVM + JS + Native x86-64 + riscv64/aarch64(qemu);
non-regression `KofTestingE2ETest` 7/7 + `StructuredTestE2ETest` 12/12 + `TestTagsE2ETest` 23/23
+ `GenericEqualityE2ETest` 16/16 + `AssertE2ETest` 5/5 + `StdCatalogTest` 11/11 = **74/74**.

## 4.5 Property-based tests (future)

Leave the architecture ready for `encode(decode(x)) == x` or `parse(print(ast)) == ast` when the
property makes sense. **Do not implement before a real need exists.**

## 4.6 Test doubles

Minimal support for fake/stub/spy/mock. Rule: if the real behavior is cheap and deterministic,
use the real behavior. Mock mainly external boundaries: HTTP service, filesystem, clock, random
source, external process, database.

> **DECIDED 06/10 (`D-MAINT-BATCH-0610B`/A):** scope = **clock/random seams only** — inject a
> deterministic clock and a reproducible random source; no mock/stub/spy framework.

**Status: LANDED 06/10 (`D-MAINT-BATCH-0610B`/A).** The surface is three additive `kof.test`
helpers, written in Kof (no new syntax/primitive, `D-KOF-FIRST` item 12):

```kof
import kof.test

// clock seam: a source of epoch millis the test controls, instead of time.now()
var clock = fixedClock(1000L)        // always 1000
var stepped = scriptedClock(listOf(10L, 20L, 30L))  // 10, 20, 30, then repeats 30

// random seam: same seed => same sequence on every backend and every run
var r = seededRandom(42)
var roll = r.next(6)
```

`fixedClock(millis)` freezes one instant; `scriptedClock(times)` returns the next reading per
call and, once exhausted, repeats the **last** (never throws, never invents a value after data
ran out; empty list = 0). `seededRandom(seed)` is a pure-Kof integer LCG (multiplier 32719,
modulus 32749, so the product fits in 32-bit `Int` with **no overflow**) — a test that depends
on randomness gets a reproducible sequence, and a failure returns identically on every run.
`rng` from the stdlib is deliberately **not** used: it has a cross gap (`RNG001`) and the whole
host file compiles on every target, so every helper must be supported on all four.

**Proof:** `TestSeamsE2ETest` **7/7** (fixed/scripted/exhaustion, seeded reproducibility across
runs) across JVM + JS + Native x86-64 + riscv64/aarch64(qemu); non-regression `KofTestingE2ETest`
7/7 + `TestRowsE2ETest` 7/7 + `StructuredTestE2ETest` 12/12 + `TestTagsE2ETest` 23/23 +
`GenericEqualityE2ETest` 16/16 + `AssertE2ETest` 5/5 + `StdCatalogTest` 11/11 = **86/86**.

---

# 5. Integration harness

> **DECIDED 06/10 (`D-MAINT-BATCH-0610B`/B):** the harness surface lives in the **Kof library**
> (`kof.test`) — temp dir / server / db lifecycle with `try/finally` cleanup, injected flat on
> the explicit `import kof.test`. No Java-only surface. AUTHORIZED; queued after §4.6.

**Status: LANDED 06/10 (first slice — temp-dir lifecycle + readiness poll); LANDED 07/10 (second
slice — database-connection lifecycle).** The surface is written in Kof (`dev/kof/test.kf`), no
new syntax/primitive (`D-KOF-FIRST` item 12):

```kof
import kof.test
import kof.test.db

// start → test → cleanup, cleanup even when the body throws
withTempDir("build/tmp", (d: String) -> {
    File(Path(d).resolve("data.txt")).writeText("hello")
    assertEqualString("hello", File(Path(d).resolve("data.txt")).readText(), "round trip")
})

// database connection: opened, used, closed — even when the body throws
withDb("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", (h: String) -> {
    db.execute(h, "create table t(id int)")
    db.execute(h, "insert into t values (?)", 7)
    assertEqualString("{\"n\":1}", db.query(h, "select count(*) as n from t").get(0), "row count")
})

// bounded readiness poll for a resource that comes up asynchronously
var up = waitUntil(() -> File("build/tmp/ready").exists(), 40, 25)
```

`withTempDir(dir, body)` creates the directory, runs the body and recursively removes the tree in
a `finally` (both paths). `removeTree(path)` is the recursive removal, pure Kof (`Directory.list()`
+ `File.delete()`): it predates the `known-bugs` §618 fix, when `Directory.delete()` only removed
an empty directory on JS — since 08/10 `delete()` itself is recursive on all four targets (§618
FIXED), and `removeTree` stays as the portable pure-Kof form that needs no backend. `waitUntil(probe, attempts, intervalMs)` probes, sleeps between attempts and returns
the last result — never throws, never invents success; `attempts <= 0` does a single probe.
`withDb(url, body)` opens `db.connect(url)`, runs the body and closes the connection in a `finally`
(both paths) — the symmetric pair of the open, so an integration test never leaves a connection
behind. The body is `(String) -> Void` (the handle is opaque, a concrete type — `(T) -> Void` is
refused with `SEM085`), so the helper is flat-injected on every target. **It lives in a separate
opt-in host `kof.test.db`, not in `kof.test`** (the `CompilerWeb`-vs-`kof.pagination` precedent):
`withDb` calls `db.connect`/`db.close`, and the cross native links libsqlite3 **by use**
(`NativeCrossLink.needsSqlite` scans the pruned asm for `call sqlite3_*`). Because `kof.test` is
flat-injected whole, a db helper living there would make **every** `import kof.test` program link
`-lsqlite3` on the cross (measured: `riscv64-linux-gnu-ld: cannot find -lsqlite3`); the separate
import means only a program that asks for `kof.test.db` pays the gap.

**Proof:** `IntegrationHarnessE2ETest` **7/7** (create/write/read, cleanup on success, cleanup on
throw, bounded poll, no-trace-on-disk) across JVM + JS + Native x86-64 + riscv64/aarch64(qemu) —
this is also the regression guard that `import kof.test` alone does **not** force sqlite on the
cross; `DbLifecycleE2ETest` **3/3** (body runs, connection closed on success and on throw — proven
on JVM + JS by the H2 in-memory database being empty after the helper, and on Native x86-64 by the
body + throw-path continuation with the data persisted). RED-first: pre-slice the probe does not
compile (`SEM015 Undefined function: 'withDb'`).

The server lifecycle helper **is** composable in pure Kof, contrary to the earlier note: `app.listen(port)`
blocks, but the helper spawns it (`var h = spawn { app.listen(port) }`), polls the port with a bounded
`http.get` probe until it accepts, runs the body and closes the app in a `finally` — `app.close()` then
`await h` — on both the success and the throw path. It lives in a **separate opt-in host `kof.test.web`**
(not `kof.test`), the `CompilerTestDb` precedent: it depends on `kof.web`/`kof.http` and the `spawn`
primitive, and `kof.test` is flat-injected whole, so a web helper living there would make every unit test
carry the web tree. `withServer(app, port, body)` takes the **already-configured** `app` (routes registered
before the helper) because `app.listen` must start after the routes exist; the body is `(String) -> Void`
(the URL). **`app.close()` (`kof_web_close`) is a JVM-only runtime symbol today**, so the helper is
JVM-complete and the cross targets report the pre-existing `WEB001` gap honestly at compile time (R6 —
never a silent fallback, never a leak); the day `kof_web_close` lands on Native/JS the pin flips
consciously. **Proof:** new `ServerLifecycleE2ETest` **3/3** — JVM lifecycle golden (body sees its own
`pong`; the port is refused after the helper on both the success and the throw path, i.e. the `finally`
ran), the cross targets pinned to the honest `WEB001`, and the opt-in guard (`withServer` is undefined
without `import kof.test.web`). RED-first: the helper was unblocked by the `known-bugs` §630 (nested
`return` lambda typing) and §632 (dotted function-typed parameter descriptor) fixes; §633 fixed the
`MEM014` false positive the helper exposed.

Infrastructure to bring up resources: HTTP server, database, filesystem, process, external
service. Each resource has `start → health check → test → cleanup`. **Never leave processes or
ports open after a test.**

**Status: temp dir + db + server LANDED; `process`, `config` and `environment variables` remain
(measured boundaries, 08/10 — lane issues/tooling `192.168.15.30:9093`).** The three landed
lifecycles (`withTempDir`/`withDb`/`withServer`) are the maintainer's `D-MAINT-BATCH-0610B`/B scope.
The remaining §5.1 resources are blocked by *missing mechanisms*, not by an undecided API — so they
are recorded as boundaries instead of a stub (Q7):

* **Process lifecycle (`withProcess(program, args, body)`) — NOT expressible in pure Kof today.**
  The intent is the symmetric pair of `withDb`: `process.spawn` a child, run the body, and
  guarantee `h.kill()` in a `finally` (both paths). Two measured blockers: (1) the spawn handle is
  an internal `java.lang.Long` registry token with **no nameable Kof type** — `(Long) -> Void` and
  `(java.lang.Long) -> Void` both fail (`SEM074`/`SEM014`; `Long` is the primitive, not the handle),
  so the handle cannot be a parameter; (2) `process.spawn(program, List<String>)` is **refused**
  (`SEM025`) — only fixed String varargs `process.spawn("prog", "a", "b")` type-check, so a helper
  cannot forward a variable argument list. The honest boundary mirrors the `app.close`/`WEB001`
  case: no helper ships until the handle is nameable (the `kof.process.Result` `D-MAINT-BATCH-0610`/C
  precedent) or spawn accepts a list.
* **Environment variables (`withEnv(name, value, body)`) — needs a set/restore primitive that does
  not exist.** The stdlib only *reads* (`config.env(key)`, `config.*`); there is no `setenv`
  equivalent to write a variable and restore it in a `finally`, so a scoped environment helper
  cannot be written in Kof. Adding one is a **new stdlib surface (rule 6)** — recorded for the
  maintainer, never invented.
* **Config (`withConfig(entries, body)`) — the same missing primitive, plus no write surface.**
  `config.get`/`config.has` read from the file named by `KOF_CONFIG` (default `kof.config`) and
  from the process environment (`RuntimeConfig1`/`RuntimeConfig2`, `NativeRiscvAsmConfig3`); a
  scoped test config therefore needs to *set* `KOF_CONFIG` (or write a file the reader will pick
  up) and *restore* it in a `finally` — i.e. the same `setenv` primitive the environment helper
  needs. `config` has no writer at all today, so a helper cannot seed a value in pure Kof; recorded
  for the maintainer with `withEnv`, never invented.

All three are additive/library-first once the mechanism exists; none blocks the landed harness.

## 5.1 Temporary environment

Official support for temporary directory, database, config, server and environment variables.
`cleanup` must happen **even when the test fails**.

## 5.2 Integration matrix

Declare which targets a test needs (`JVM`, `Native`, `JS`, `WASM`). A property common to
targets may run on several; do not run all targets for every test automatically.

## 5.3 Cross-target tests

A dedicated category proving that

```text
Kof source → JVM   ≡   Kof source → Native   ≡   Kof source → JS
```

produce equivalent behavior when the feature is supported. This generalizes
`ConformanceMatrixTest` and becomes essential for KofJS and KofWasm. Unsupported target →
honest diagnostic (`NATIVE002`/`WASM001` class), never silent.

---

# 6. Frontend Testing API

> **DECIDED 06/10 (`D-MAINT-BATCH-0610B`/C):** the browser provider (Playwright/Cypress) is
> **opt-in per project** — declared per project, the CLI does **not** bundle it (interop-first
> R9, no heavyweight default dependency). AUTHORIZED; queued after §5.

**Status: POLICY RECORDED 08/10 (docs-only, lane issues/tooling `192.168.15.30:9093`).** The §6
policy is now fixed by three decisions and recorded here: **(C)** opt-in per project — the CLI
does **not** bundle Playwright/Cypress; **(T1)** the browser provider must serve **all targets**
(JVM + JS + Native), not JVM-only; **(T2)** `kof.test` stays a **compiler/CLI feature**, not a
stdlib namespace (`StdCatalog` unchanged). **REFINED 10/10 (third chat poll, current owner lane
security/connectors `192.168.15.15:9092`):** *how* providers are declared/versioned/gated is
RESOLVED — a **project manifest file** (`kof-test.kofmd`, the repo's own format, zero external
parser) declares the browser provider + its version; the CLI reads it and gates. **The §6 provider
slice is UNLOCKED.** The seed is `KofJsBrowserE2ETest` (a raw mechanism: real Chrome
`--headless --dump-dom`, macOS `safaridriver` W3C WebDriver), not an abstraction. The provider
SPI/manifest pattern cross-references `kof-connector-ecosystem-plan.md` (§14). Capability-matrix
values stay `?` until discovered during implementation (§6.4) — never assumed.

An official browser-testing API in Kof. Conceptually:

```text
browser.launch()          page.goto(...)
browser.newPage()         page.click(...)
                          page.fill(...)
                          page.press(...)
                          page.locator(...)
                          page.text(...)
page.attribute(...)       page.screenshot(...)
                          page.evaluate(...)
```

These names are concepts only; the final API follows Kof grammar and conventions. **Do not
copy the Playwright JS API.**

## 6.1 Browser abstraction

```text
Kof Browser Testing API
        │
   Browser Driver (provider SPI)
        │
   ┌────┴────┐
Playwright  Cypress
```

Kof owns the API; Playwright and Cypress are **providers/backends**.

## 6.2 Playwright provider (first)

Start/launch browser, create context/page, navigate, locate elements, interact, collect info,
screenshots, traces, videos when supported, console, network, cookies, storage, cleanup. The
provider encapsulates Playwright; Kof tests never depend on Playwright internals.

## 6.3 Cypress provider (later)

Do not assume Cypress's execution model equals Playwright's. Expose only capabilities with
compatible semantics; a backend-specific capability that is missing yields a **clear
`capability unsupported` diagnostic** — never pretend equivalence.

## 6.4 Browser capability matrix

```text
Capability            Playwright   Cypress   Future driver
navigation               ✓            ?           ?
click / fill / keyboard  ✓            ?           ?
locator / assertion      ✓            ?           ?
screenshot / video / trace ✓         ?           ?
network interception     ✓            ?           ?
cookies / storage        ✓            ?           ?
upload / download        ✓            ?           ?
dialogs / console        ✓            ?           ?
geolocation / permissions ✓          ?           ?
multiple tabs / iframes  ✓            ?           ?
websocket                ✓            ?           ?
```

Values are **discovered during implementation**, never assumed. The system must know which
capabilities are available (values `✓`/`—`/`partial` completed per provider).

## 6.5 Locators and auto-wait

Prioritize semantic locators: `role`, `text`, `label`, `placeholder`, `test-id`, then `css`,
`xpath`. Tests must not depend on fragile CSS. The API must allow find + assert
visible/enabled/text/value/attribute.

Auto-wait is fundamental: prefer `wait until visible/enabled/text/network idle/URL/condition`
over `sleep(1000)`. **Arbitrary sleeps are not a normal synchronization strategy.**

## 6.6 Web assertions

Conceptually `expect(locator).toBeVisible()`, `toHaveText`, `toHaveValue`,
`expect(page).toHaveURL` — but idiomatic Kof, not copied JS syntax.

## 6.7 Network testing

Controlled request interception (intercept GET/POST, mock response, inspect request/response).
Keep **Frontend E2E** and **Frontend + Backend integration** separate: both are needed.

## 6.8 Fixtures

Reusable fixtures (`authenticatedPage`, `adminPage`, `loggedOutPage`, `testUser`,
`testDatabase`, `testBackend`) that do **not** hide important dependencies — the test stays
readable.

## 6.9 Full Web Integration

```text
Kof backend → HTTP/API → KofJS / KofWasm frontend → real browser
```

validating the complete system. One reference app (`examples/fullstack/` is the seed, §8.4)
must expose unit + integration + e2e tests and act as the platform's own integration test.

## 6.10 KofJS and KofWasm

The browser infrastructure must work for both:

```text
Kof source → KofJS   and   Kof source → KofWasm
```

when the application is compatible — protecting the promise "same Kof code, different target".
KofWasm is future (`WASM001`, `wasm-wasi-plan.md`); the platform must not promise it early.

## 6.11 Browser matrix and mobile

Run the same suite on Chromium/Firefox/WebKit when the backend supports it; never all browsers
on every PR by default. Profiles: `Fast Browser` and `Full Browser Matrix`. Support
desktop/tablet/mobile through viewport, device emulation, touch and orientation when supported.

## 6.12 WebSocket / SSE

Test `connect`, `message`, `disconnect`, `reconnect`, `error` — for realtime Kof applications
(seed: `KofWebWsE2ETest`, `KofWebSseE2ETest`).

## 6.13 Download / Upload

`upload file`, `download file`, verify contents/filename/MIME; integrate with `kof.file` when
available.

---

# 7. Test Runner

The runner must understand discovery, filtering, lifecycle, parallelism, timeouts, retries,
artifacts, reports and exit codes. **Do not reinvent what the current runner already has** —
integrate with `kof test` (`CmdTest`)/the compiler test pipeline.

**Discovery (LANDED 02/10, `known-bugs` §576):** a discovered `.kf` that declares neither `test`
nor a top-level `main` is an **auxiliary module** (shared helpers), not a suite. `kof test` skips it
with `SKIP <file> (no tests, no main)` and counts it as skipped — never as pass/fail. A run where
every file is an auxiliary module exits 1 (`no runnable test or program file found`); zero runnable
files is not a success. A file with `main` and no tests still runs as a program (contract preserved),
and the program's stdout is kept — printed before `PASS`, matching the JS leg (measured defect
`known-bugs` §578, FIXED 02/10). This requires the compiler to expose `CompilerDriver.hasMainEntryPoint()`,
set once per unit by the tests desugar step.

**Declaration validation:** the `test` declaration refuses an empty NAME and an empty TAG with
`PARSE010` (`test name must not be empty` / `test tag must not be empty`) — an unnamed test would
run as `PASS ` with no identity (measured defect `known-bugs` §579, FIXED 03/10).

**Measured negative probes (03/10, no defect — do not re-probe):** (a) a **symlinked** `.kf` alias
(`alias.kf -> real.kf`) is discovered and run as its own file, so the same test reports twice — this
is duplicate discovery by path, not a contract violation, and `Files.walk` does not follow directory
symlinks (a `self -> .` / `up -> ..` loop does not hang). (b) **Duplicate test names** in one file
both run and are both reported (`PASS same` / `FAIL same: assertion failed`); they are distinct
declarations, not a silent overwrite. (c) An **empty test body** passes (`PASS nothing`). (d) A
`test` **nested inside a function** is rejected `SEM011` (`Undefined variable or type: 'test'`) —
test declarations are top-level only, as intended.

## 7.1 Tagging

Categorize tests: `unit`, `integration`, `e2e`, `slow`, `browser`, `network`, `database`,
`native`, `jvm`, `js`, `wasm`, `security` — enabling efficient filters.

**Status:** LANDED 26/09 (X8 fatia 3) — tags are declared in the primitive
(`test "name", "smoke" { }`) and `kof test --tag <t>` filters at COMPILE TIME (system property
`kof.test.tag`; the synthesized harness is generated once and every target runs the same filtered
catalog, rule-5 parity by construction). A filter that matches **nothing** in a file is an honest
no-op (exit 0, the harness prints `kof test: tag '<t>' (0 of N)` / `no tests with tag '<t>' (of N)`)
— a tag filter is not a gate that fails the build.

**Zero-match files are SKIPPED, never passed (measured defect `known-bugs` §587, FIXED 04/10):**
`CmdTest` used to count every file whose harness exited 0 as `passed`, so a file whose tests all
failed the tag filter printed `suite b: 1 passed, 0 failed` / `2 passed, 0 failed` — a false green
indistinguishable from a real pass. `CompilerDriver.TestInfo` now exposes the declared `tags`, and a
zero-match file is `SKIP <file> (no tests with tag '<t>')` counted in `skippedByTag`, excluded from
`passed`.

**Multi-tag (LANDED 06/10, lane issues/tooling `192.168.15.30:9093`):** the `--tag` value is a
comma-separated list and matches by **disjunction (OR)** — `kof test --tag smoke,ui` keeps every
test carrying *any* of the listed tags; a bare value (no comma) is the single-tag case and keeps the
historical contract byte for byte (rule 2). Whitespace around each tag is trimmed; an empty item is
discarded. The parse lives in `TestHarnessBuilder.matchesAnyTag` (compile-time catalog) and mirrors
in `CmdTest.hasTagMatch` (so the §587 zero-match SKIP verdict agrees with the harness). Proof
RED-first: new `TestTagsMultiE2ETest` **4/4** (union, trim, unknown-tag in the list, all-unknown
honest no-op; pre-fix **3 RED** with the old single-tag match), `TestTagsE2ETest` **23/23** unchanged
and `CmdTestTagTest` **7/7** (was 6 — the `--tag smoke,ui` CLI leg). **Rule-5 parity proof on Native
x86-64:** `TestTagsNativeE2ETest` **2/2** compiles the multi-tag harness for `Target.NATIVE` and asserts
the SAME filtered catalog (`kof test: tag 'smoke,ui' (2 of 3)`) plus the all-unknown honest no-op; the
cross native targets are not advertised test targets (`kof test --target jvm|native|js`) and the harness
main does not link `kof_process_exit` there. **Cross-target refusal (LANDED 06/10, `known-bugs` §615):**
`kof test --target native.risc`/`native.arm` was false support (it compiled and then died with a raw
`undefined reference to 'kof_process_exit' [COMP001]`); `CmdTest` now refuses both early with a named
message pointing at `--target jvm|native|js` and at `kof build --target native.<arch>` + qemu (the
compiler E2E suite is the cross runner). Proof RED-first: `CmdTestCrossTargetRefusalTest` **2/2**.
**Negation remains future work** (it
needs a syntax to distinguish "not this tag" from a tag literally named with a `!`; not decided).

## 7.2 Parallelism

Unit: parallel by default when isolated. Integration: controlled. E2E: per browser/context/project
when safe. Never share ports, database, filesystem, session, cookies or global state.

## 7.3 Timeouts

Every test has a reasonable timeout: separate unit, integration, e2e and browser-action
timeouts. Never let a test hang indefinitely. (Seed: `CmdTest --timeout`, `CmdTestTimeoutTest`.)

**Status:** a single global `--timeout <sec>` LANDED (JVM/Native via `Process.waitFor` + `destroyForcibly`;
JS best-effort in-process `Thread.join`). Separate **per-level** timeouts remain open — they need the
level concept, not yet in the runner. The complementary **duration measurement** (per-file wall time +
slowest file) LANDED 08/10 and is recorded in §8.5.

## 7.4 Retry and flaky detection

Retries used with great care, **never to hide flakiness**. Record first execution, retries,
duration, environment, browser, target, result. A test alternating pass/fail is not healthy
merely because a retry passed → **Flaky Test Detection** report.

## 7.5 Debug mode

`test --debug`: visible browser, optional slow motion, detailed logs, screenshots, trace.

---

# 8. Artifacts, reports, data and server lifecycle

## 8.1 Artifacts

On E2E failure, automatically collect when possible: screenshot, video, trace, console, network,
HTML, logs — so `CI failed → open artifact → see exactly what happened`.

## 8.2 Screenshots and visual regression

Allow page/element/failure screenshots. Evaluate screenshot comparison as a **future**
capability (`baseline → new screenshot → pixel comparison → difference report`) for components,
layouts, dashboards and critical pages. It must be deterministic; visual regression is **not**
mandatory for all tests.

## 8.3 Accessibility (evaluate)

Integration with accessibility tooling for roles, labels, keyboard navigation, contrast when
supported, accessible names and semantic structure. Never replaces manual accessibility testing.

## 8.4 Test data and server lifecycle

Mechanisms to seed DB, create users/fixtures and reset state; avoid permanent global fixtures —
each run cleans its own state. For E2E: `build app → start server → wait ready → run browser →
collect artifacts → shutdown`; the developer must not manually start five processes.

## 8.5 Unified report

```text
Kof Test Report
Unit         1203 passed / 0 failed   4.2s
Integration   430 passed / 2 failed   38s
E2E            87 passed / 1 failed   2m14s
```

with duration and slow-test identification. Design goal: `docs/testing/TEST-PERFORMANCE.md`
(the companion plan proposes it; §13).

**Status: FIRST SLICE LANDED 08/10 (lane issues/tooling `192.168.15.30:9093`).** The runner now
measures each file's wall time and prints one additive line after the summary —
`time: <total>ms total, slowest <file> (<ms>ms)` — the base of **slow-test identification**. It is
printed on the success **and** failure paths (before the non-zero exit), so a slow/failing file is
diagnosable from the run output alone; no new flag, no level concept, no change to the historical
summary/suite lines (byte-compatible — the existing assertions stay green). The per-**level**
breakdown (Unit/Integration/E2E) and the machine-readable report remain open: they need the level
concept the runner does not have yet (the `--tag` axis is the only current classifier). Proof:
`CmdTestSuiteTest` **10/10** — new `runnerReportsTotalTimeAndSlowestFile` + `timingIsPrintedBeforeAFailingExit`,
RED-first (both fail pre-slice, no `time:` line).

---

# 9. Security, CI and profiles

## 9.1 Security tests

Verify XSS, CSRF, authentication, authorization, cookie behavior, CORS, invalid input,
injection and session expiration. Never replace specialized security tooling.

## 9.2 Profiles

```text
test unit          → seconds
test integration   → minutes
test e2e           → browser automation
test all           → unit + integration + e2e
test e2e --browser chromium|firefox|webkit
```

Final syntax follows the existing CLI. These profiles mirror the companion plan's Maven
`fast`/`integration`/`full`/`stress` proposal (`test-architecture-plan.md:167`), operating at the
platform level instead of the internal suite level.

## 9.3 CI integration

```text
Pull Request  → Unit + relevant Integration
Merge         → Unit + Integration + E2E
Release       → Full + Browser Matrix + Cross Target
```

Do not run the full browser matrix on every trivial change. Reuse the existing workflows
(`.github/workflows/ci.yml`, `release.yml`) and release gates (`tests/run-golden.sh`,
`tests/run-integration.sh`).

---

# 10. Testing the framework itself

The framework must test itself: unit/integration/driver/browser/failure/timeout/parallel/artifact
tests — especially `test passes`, `test fails`, `test throws`, `test timeout`, `test cleanup`,
`test retry`, `test browser crash`, `test application crash`.

---

# 11. Incremental implementation

| Phase | Scope |
|---|---|
| 1 · Unit Core | discovery, assertions, lifecycle, isolation, reporting, filtering, timeout |
| 2 · Integration Core | fixtures, temporary environment, process lifecycle, HTTP, database, cleanup |
| 3 · Browser Core | browser abstraction, page, locator, navigation, interaction, browser assertions |
| 4 · Playwright | first complete provider; prove `Kof test → Playwright → Chromium → real app` |
| 5 · Cypress | second provider only after the abstraction is stable; map capabilities |
| 6 · KofJS / KofWasm | E2E for both, same suite when possible |
| 7 · Cross-browser | Chromium, Firefox, WebKit |
| 8 · Advanced | visual regression, accessibility, network mocking, WebSocket, upload/download, mobile emulation, traces, videos, advanced diagnostics |

The companion plan (`test-architecture-plan.md`) may advance the **internal suite** axis in
parallel; neither blocks the other, and both share §13.

---

# 12. Open decisions (rule 6 — the maintainer decides)

**Resolved 05/10 by `D-MAINT-BATCH-0510`** (the maintainer's chat poll):

* **T2 — `kof.test` stays a compiler/CLI feature**, NOT a stdlib namespace; `StdCatalog` is
  unchanged. (Was an open decision below; now decided.)
* **T1 — the browser E2E provider must serve ALL targets** (JVM + JS + Native), not JVM-only.
  The Playwright/Cypress dependency declaration/versioning still lands with the provider slice.
  (Was an open decision below; now decided.)

Still open (rule 6):

* **Provider policy**: **RESOLVED 10/10 by the third chat poll (refining `D-MAINT-BATCH-0610B`/C):**
  the declaration lives in a **project manifest file** (`kof-test.kofmd` — the repo's own
  compressed-doc format, zero external parser dependency) declaring the browser provider + its
  version; the CLI reads it and gates (explicit and versioned, no command flags). The §6 provider
  slice is UNLOCKED. **The Playwright JAVA lib lands as a NORMAL kof-cli dependency (the fourth
  chat poll, 10/10 — the maintainer's call supersedes the 'does not bundle' half for the lib jar;
  browsers stay per-project via `npx playwright install`).**
* **All other items RESOLVED by execution (10/10, owner lane security/connectors):**
  `D-TESTING-PLATFORM` opened the front (the plan landed slices 1-6 + §5 harness + the §6 stack);
  the test API syntax is FIXED by the landed slices (additive, no foreign syntax — the four chat
  polls confirmed it); promotion to `docs/development/` happened 30/09 (the first-slice rule).
  **No open decisions remain — the plan advances by the confirmed §11 order.**

---

# 13. Performance (meets the companion plan)

Unit tests must not depend on browser infrastructure; integration tests must not start a browser
unnecessarily. Hierarchy: `Unit (cheap) → Integration (moderate) → E2E (expensive)`. The more
expensive the level, the fewer the tests. This is the same principle as
`test-architecture-plan.md` (§"Feedback cost"); the platform exposes the **profiles**, the
companion plan measures/refactors the **internal Java suite**. Do not duplicate the profiling
work — reference it.

---

# 14. Relation to other plans

* `docs/development/test-architecture-plan.md` — internal Java suite (L0–L5, profiles,
  performance). **Complementary, not duplicated.**
* `docs/wasm-wasi-plan.md` — KofWasm; the platform's cross-target/WASM E2E
  depends on it (`WASM001` until then).
* `docs/development/future/qrcode-wasm-plan.md` — another WASM front consumer.
* `docs/stdlib/kof-file-plan.md` — `kof.file` for upload/download test helpers.
* `docs/stdlib/kof-connector-ecosystem-plan.md` — providers (browser drivers) are a
  natural connector-style SPI; cross-reference for the SPI/manifest pattern.

---

# 15. Success criteria

Functional when it is possible to:

* **Unit** — create and run isolated tests quickly.
* **Integration** — run real components together without running the whole suite.
* **E2E** — open a real application in a browser and perform real actions.
* **Cross-target** — run the same Kof application through KofJS (and KofWasm when supported).
* **Cross-browser** — run the same suite on multiple browsers.
* **Diagnostics** — an E2E failure generates enough evidence to diagnose without manual
  reproduction.

---

# 16. Final rule

Do **not** build "a clone of Jest + JUnit + Playwright + Cypress inside Kof". Build a **Kof
testing infrastructure** that understands the language's architecture and uses mature ecosystem
tools when they are the best implementation underneath.

> **Kof provides the testing experience; providers provide execution.**

Testing Kof must be as natural as writing Kof:

```text
Kof → Kof Test → Unit / Integration / Browser → result
```

No workaround, no abandoning Kof to write tests, and no turning every test into a two-hour
compilation.
