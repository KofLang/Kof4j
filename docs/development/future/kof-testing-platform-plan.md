[English](kof-testing-platform-plan.md) | [Português](kof-testing-platform-plan.pt_BR.md)

# Kof Testing Platform — Unit / Integration / Frontend E2E

**Status:** Future plan — design only, **zero code**
**Location:** `docs/development/future/`
**Nature:** architecture, contracts, API intent, dependencies, promotion criteria
**Normative source:** pending `DECISIONS.md` §`D-TESTING-PLATFORM` (rule 6 — the maintainer decides)
**Main dependencies:** the existing `kof test` command (`CmdTest`), the test language surface
(`test`/`assert`), the per-target harness (`ConformanceMatrixTest`), `KofJsRunner`,
`KofJsBrowserE2ETest`, the CLI, KofJS, the future KofWasm
**Companion plan:** `test-architecture-plan.md` (the **compiler's own Java suite** refactor —
L0–L5 layers, profiles, performance). This document is the **user-facing testing platform**;
the two meet at §13 (Performance) and must not duplicate each other.
**Implementation status:** not started

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

## 4.2 Lifecycle

Support `beforeAll` / `afterAll` / `beforeEach` / `afterEach` when needed, but never as a
global-state mechanism. A test stays isolated.

## 4.3 Isolation

```text
test A → isolated state
test B → isolated state
```

Never `test A → global state → test B depends on A`. Temporary filesystem, ports, database and
resources have explicit lifecycle.

## 4.4 Parameterized tests

Evaluate `input → expected` tables — very useful for parser, type checker, encoding, HTTP,
string processing and numeric operations. Do not duplicate dozens of tests only because inputs
change.

## 4.5 Property-based tests (future)

Leave the architecture ready for `encode(decode(x)) == x` or `parse(print(ast)) == ast` when the
property makes sense. **Do not implement before a real need exists.**

## 4.6 Test doubles

Minimal support for fake/stub/spy/mock. Rule: if the real behavior is cheap and deterministic,
use the real behavior. Mock mainly external boundaries: HTTP service, filesystem, clock, random
source, external process, database.

---

# 5. Integration harness

Infrastructure to bring up resources: HTTP server, database, filesystem, process, external
service. Each resource has `start → health check → test → cleanup`. **Never leave processes or
ports open after a test.**

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

## 7.1 Tagging

Categorize tests: `unit`, `integration`, `e2e`, `slow`, `browser`, `network`, `database`,
`native`, `jvm`, `js`, `wasm`, `security` — enabling efficient filters.

## 7.2 Parallelism

Unit: parallel by default when isolated. Integration: controlled. E2E: per browser/context/project
when safe. Never share ports, database, filesystem, session, cookies or global state.

## 7.3 Timeouts

Every test has a reasonable timeout: separate unit, integration, e2e and browser-action
timeouts. Never let a test hang indefinitely. (Seed: `CmdTest --timeout`, `CmdTestTimeoutTest`.)

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

* **D-TESTING-PLATFORM** — opening the front and its ordered scope.
* The exact **test API syntax** (assertions, lifecycle, parameterization, locators) — additive
  to the existing `test`/`assert`; no foreign syntax.
* Whether `kof.test` becomes a **stdlib namespace** (today `StdCatalog` has none) or stays a
  compiler/CLI feature.
* **Provider policy**: Playwright/Cypress are external heavyweight dependencies — how they are
  declared, versioned and gated (interop-first, R9), and whether they ship with the CLI or are
  opt-in.
* Whether browser E2E runs on **JVM-only** first (the existing `KofJsBrowserE2ETest` is a Java
  test) or also from a standalone `kof test --e2e`.
* Promotion: `future/` → `docs/development/` when the first slice lands (three-states + R12).

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

* `docs/development/future/test-architecture-plan.md` — internal Java suite (L0–L5, profiles,
  performance). **Complementary, not duplicated.**
* `docs/development/future/wasm-wasi-plan.md` — KofWasm; the platform's cross-target/WASM E2E
  depends on it (`WASM001` until then).
* `docs/development/future/qrcode-wasm-plan.md` — another WASM front consumer.
* `docs/development/future/kof-file-plan.md` — `kof.file` for upload/download test helpers.
* `docs/development/future/kof-connector-ecosystem-plan.md` — providers (browser drivers) are a
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
