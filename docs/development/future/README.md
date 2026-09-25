[English](README.md) | [Português](README.pt_BR.md)

# docs/development/future/ — future plans only (zero code)

**Rule for this folder:** here lives **only** what is **a plan for the future** —
an architecture/vision document **with no implemented code** (or with code that
is explicitly non-deliverable and outside the current scope).

> If an idea **is already being implemented** (even partially), the
> corresponding doc **does not stay here** — it lives in `docs/` and documents
> the **real state** (what already exists) + **how to finish it**. That way the
> reader knows exactly where the thing stands and what is missing.

## Recent example (09/01)

- `kof-native-risc-arm.md` **left here** for `docs/native-multiarch.md`: the
  plumbing (enum `Target.NATIVE_RISCV64/AARCH64`, CLI `native.risc/arm`,
  dispatch, cross-as/ld) is already in the code, so the item is **under development**
  and started being documented with real state + a finishing plan.

## What stays here (plan only, no code)

| Doc | Topic | Why it stays in `future/` |
|-----|------|---------------------------|
| ~~`type-system-extensions-plan.md`~~ **moved → [`../../type-system-extensions-plan.md`](../../type-system-extensions-plan.md)** | X5 variance + sealed · X6 interop reflection | plan **CONCLUDED** (X5+X6), moved to `docs/` 22/09 (three-states) |
| `PLAN-MULTIPARADIGMA.md` | multiparadigm / functional pipelines and declarative queries (`users.filter{...}.map{...}`), diagnosis at HEAD 16/09 | **design only, zero code in the doc** (§8 lists no file changed); promoted only when the first functional increment ships (SYSTEMS closed, R12) |
| `assembly-optimization-plan.md` | Native multi-ISA assembly optimization (pipeline IR→optimizer→target lowering→ISA; phases A–F; promotion criteria) | **plan only, zero code** — maintainer-requested 22/09; cross-target principle (optimize before ISA-specific assembly); rule of thumb: good IR + lowering, not infinite peephole; every optimization needs correctness test + benchmark + cross-target coverage (§22) |
| `scoped-resources-plan.md` | lightweight RAII (TIER 2.4, `using`/`resource_scope`) | pure design — zero occurrences of `resource_scope`/`kof_resource`/`using` in the lexer/parser/runtime; gated by bump |
| `value-records-plan.md` | value records / first-class value types (TIER 2.7, `value record`) — accepted 16/09 (issue #275, `DECISIONS.md` §D-VALUE-RECORD) | **zero code** — the feature does not exist in the lexer/parser/backends; design only, gated by R12 + explicit authorization to open the front |
| ~~`secrets-plan.md`~~ **promoted 21/09 → [`../../architecture/secrets-plan.md`](../../architecture/secrets-plan.md)** | `Secret`/`KeyHandle` + enforced redaction (Stage 5, tracker 3.6) | **CLOSED 21/09** — all faces landed (`04473bbe`), design record moved to `docs/architecture/` |
| `kof-file-plan.md` | `kof.file` strategic plan (core → Data → Document → Archives) | **plan only, zero code** — maintainer-requested 19/09; heavy codecs belong to official packages (R1 gate); bridges already exist (`kof.json`/`kof.db`/`kof.http`) |
| `image-vision-plan.md` | `kof.image` + `kof.vision` (pixels/filters → codecs → OCR/QR/barcode → vision pipeline → ML) | **plan only, zero code** — maintainer-requested 19/09; official packages per R1; interop-first per R9 (imageio/PDFBox/ZXing/Tess4J/OpenCV/ONNX); promotion behind R12 |
| `graphics-gaming-plan.md` | 2D/3D/game-loop/sound/video intent surface (`scene`/`frame`, sprites/tiles, mesh/camera/material, `sound.play`, `video` panel) — `DECISIONS.md` §D-GRAPHICS-GAMING + 4 addenda (20/09) | **plan only, zero code** — maintainer-requested 20/09; acceptance is FULL 4-target parity (addendum 2 — R7 does not apply here); JavaFX eradication measured 0 bindings (addendum 3); **Kof's OWN engine** for the graphics/media domain (addendum 4 — the named R9 exception; FFI only for the non-engine layer: window/GPU/audio device; codecs never homemade); fronts open only by her explicit slice promotion (R12 + rule 6) |
| `qrcode-wasm-plan.md` | `kofqrcode` (file/camera reader + writer) + `KofWasm` frontend target | **plan only, zero code** — maintainer-requested 19/09; QR = official package (R1, ZXing interop R9); `Target.WASM` does not exist (honest rejection `WASM001` in `TargetMatrix`) |
| `wasm-wasi-plan.md` | WASM+WASI technical spec: first-class direct backend, runtime/linear memory/GC, WASI, browser host, capabilities, phases 0–7, release gates | **documentation only, zero code** — maintainer-requested 19/09; deep spec for the universal-platform WASM row; every undecided point marked TBD/DECISION REQUIRED (rule 6); see §36 for the validation checklist |
| `test-architecture-plan.md` | test-suite refactoring: layers L0–L5, profiles (fast/integration/full/stress), harness, golden, determinism, measurement | **plan only, zero code** — maintainer-requested 19/09; pure test infrastructure (no compiler change); R12 — promote only after the compiler's current work closes |
| `kof-testing-platform-plan.md` | **Kof Testing Platform** (user-facing): Unit / Integration / Frontend E2E in three levels — additive to the existing `test`/`assert` surface and the `kof test` runner; browser abstraction + provider SPI (Playwright first, Cypress later), capability matrix, semantic locators/auto-wait, harness/artifacts/reports/profiles; complements `test-architecture-plan.md` (internal Java suite) | **plan only, zero code** — maintainer-requested 26/09; grounded in the real inventory (`CmdTest.java:15`, `test`/`assert` in `Lexer.java:68`/`CompilerDesugar.java:16`, `ConformanceMatrixTest.java:44`, `KofJsE2ETest`/`KofJsBrowserE2ETest`, `WASM001`); Kof owns the API, Playwright/Cypress are providers; rule 6 — promotion needs `D-TESTING-PLATFORM` |
| `http-policies-plan.md` | declarative HTTP/Web policies (authorization, rate limit, headers, rejection payloads) attachable **globally, per resource and per endpoint** — extends the existing global `app.security(opts)` | **plan only, zero code** — maintainer-requested 23/09; the global parser already exists, the gap is scoping + payloads; JVM-first, Native/JS are honest `WEB006` gaps; rule 6 — promotion needs a `D-HTTP-POLICIES` decision locked in `DECISIONS.md` |
| `pagination-plan.md` | first-class windowing intention `Window<T>` (in-memory + SQL `LIMIT/OFFSET` pushdown + optional HTTP helper), cursor/keyset-ready | **plan only, zero code** — maintainer-requested 23/09; composes existing `List.subList` + `orm.page` (`kof_orm_page`); in-memory part rides `PLAN-MULTIPARADIGMA` Phase 1, the rest is gated; rule 6 — promotion needs a `D-PAGINATION` decision |
| `kof-connector-ecosystem-plan.md` | **Kof interoperability — Connector Ecosystem**: an Interop Core (ABI/type model, ownership, strings, structs, callbacks, errors, library loading, versioning, diagnostics) + Connector SPI/manifest + catalogue (Java first, C ABI second, then JVM/Systems/managed/scripting/scientific/legacy) | **plan only, zero code** — maintainer-requested 26/09; greenfield (no "connector" concept exists); builds on the existing FFI/ABI substrate (`ffi-abi-structs.md`, `FfiSignature`/`AbiLayout`/`FfiStructLayout`, `JvmFfiRuntime`, `ExternalClasspath`, `KofProcess`) and never duplicates it (rule 54); rule 6 — promotion needs a `D-CONNECTORS` decision; scope control rule 55 (prove with few connectors, no 30-runtime waterfall) |
| `docs/shell-plan.md` (moved) | `kof.shell` — idiomatic shell over `kof.process` — **RECLASSIFIED 19/09: v1 MVP landed (`34e4344f`); the doc now lives in `docs/development/`** — residual faces (pipeline JS/Native, glob/`~`/redireção v2) tracked there | — | — |
| `docs/workflow-plan.md` (moved) | `kof.workflow` — jobs/pipelines/retry/checkpoints/dead-letter (TIER 2.1) — **RECLASSIFIED 19/09: signed off by maintainer poll (Q1–Q4) and 2.1.0 recon DONE (`WorkflowPrimitivesE2ETest` 6/6 + lambda `Result` descriptor fix `8ec07214`); the doc now lives in `docs/development/`** — **MVP 2.1.2 LANDED 19/09** (pure-Kof host, flat `job`/`dag`/`after`/`run`/`Report`, `WorkflowE2ETest` 7/7) + docs 2.1.4; residual queue = 2.1.3 add-ons |
| ~~`PLAN-BAREMETAL-BOOT.md`~~ **promoted 22/09 → [`../../PLAN-BAREMETAL-BOOT.md`](../../PLAN-BAREMETAL-BOOT.md)** | **native → bare-metal/bootable with ring0/ring1** (faces B-0…B-6: HAL seam, freestanding, UEFI, legacy BIOS, MCU, x86_64 privilege rings) | **PROMOTED 22/09** by maintainer order (`DECISIONS.md` §D-BAREMETAL-BOOT): the front is open (R12 overridden for it), ordered scope includes ring0/ring1; the plan's §7 order governs (B-0 → B-1 → boot path → B-6) |
| `PLAN-BOOTSTRAP.md` | **the Bootstrapper: Kof written in Kof (BS-1)** — the **north star** of the platform (`DECISIONS.md` §D-BOOTSTRAP, 20/09): a `kofc.kf` that compiles the whole corpus byte-identically to the Java core and then compiles itself (fixed point); "Kof as its own cloud" closes end-to-end | **design plan only, zero code** — drafted forward by the maintainer (owner lane `.18`), execution gated by the three-states rule + R12: it may not start before the 1.0 EXIT GATE closes (`roadmap.md` §24) and no stage may be skipped; every escape hatch is a rule-6 decision. Entry conditions E1–E6 (§2); phases BS-A…BS-E (§3); `roadmap.md` NORTH STAR row |
| `DECOMPILER.md` + `TRANSLATOR.md` + `LEGACY_MIGRATION.md` | legacy migration platform (decompiler/translator/IR/diff-testing) | **DEPRIORITIZED by the maintainer 15/09 — back from `docs/development/`.** Code stays in kof-cli (`DecompileTest` 67/67, `TranslateTest` 61/61); the QUEUE is paused: promotion needs her explicit decision |
| ~~`planning-stdlib-array-returns.md`~~ → `docs/stdlib/DD-STDLIB-01-array-returns.md` | DD-STDLIB-01 | **CLOSED 13/09** — decision 6a + implementation (`randomBytesHex`->String; choice=idiom), moved to docs/ |

> **`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` left `future/` on 17/09/2026** — promoted to
> current work by maintainer decision — today `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` —
> which **overrides the R12 gate** (see `DECISIONS.md` §D-UNIVERSAL).
> The vision/design is unchanged; the entry point is Stage 1 (SYSTEMS
> consolidation) and the executable recommendations R1–R12.

## Historical: what left `future/` earlier (snapshot 12/09 — NOT current state)

> **Do not read this as the current state** (updated 17/09). The migration cluster
> **returned to `future/` on 15/09** (row above), and the platform/app-model docs
> were **ratified and consolidated into `DECISIONS.md` on 13/09** (the 6 files of
> `decision-pending/` were deleted). The table is kept only as a record of the
> 12/09 fall.

| Doc | Destination / current home |
|-----|------------------|
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (the `IMPLEMENTATION_PLAN.md`+`ACTION_PLAN.md` were MERGED into `roadmap.md` §23 and `DIFFERENTIAL_TESTING.md`+`LEGACY_IR.md` into `LEGACY_MIGRATION.md`, all 13/09) | **back in `future/` 15/09** (deprioritized) — code+tests live in kof-cli (`kof inspect/decompile/translate/compare/migrate`, `Main.java`); live count in `roadmap.md` §23 TIER 3–5 |
| `PLATFORM-PLAN.md` | `DECISIONS.md` §D-PLATFORM (ratified 13/09; the file was deleted) |
| `APPLICATION_MODEL.md` | `DECISIONS.md` §D-APP (Q1–Q10 locked 13/09; the file was deleted) |
| `PLANNING-FUTURE-AUDIT.md`, `planning-future-reconcile.md` | `docs/audits/` (closed 13/09); R2→`DECISIONS.md` §D-APP/§D-PLATFORM, R5→migration cluster |
| `planning-finally-return.md` | `docs/decisions/DD-01-finally-return.md` (CLOSED 13/09; bug 45 FIXED) |
| `planning-stdlib-time-design.md` | `DECISIONS.md` §D-STDLIB (ratified 13/09; the file was deleted) — `addDays`/`diffDays` on 5 targets (TIME002 11/09) |

## When to move from `future/` to `docs/`

When the item stops being "plan only" and **there is code under development**,
even partially:

1. Move/rewrite the doc in `docs/` with **status `UNDER DEVELOPMENT`**;
2. Document **what is already done** (real files/lines) vs **what is missing**;
3. Include a **"how to finish"** section (step by step with dependencies);
4. Update `docs/backend-parity.md` / `docs/status.md` to point to the new
   path;
5. Keep the gap-code (e.g.: `NATIVE002`) until the item closes.
