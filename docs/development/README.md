[English](README.md) | [Português](README.pt_BR.md)

# Development — living backlog (only work in development)

> **Base:** `0.5.0-beta` · branch `beta-0.5.0` · **updated:** 23/09/2026
> **Suite measured at this HEAD:** `3225` run (2762 kof-compiler + 50 kof-script
> + 7 kof-c-compiler + 406 kof-cli), **0 failures / 0 errors**, 221 skip (cross
> runs in the dedicated qemu job; the rest external-DB/toolchain guards + §255
> sysroot) — CI Build+Tests job of tip `404d8be6` on 20/09 ~18:14: the **first
> green on `beta-0.5.0`**, reactor `Kof 0.5.0-beta`. The §252 flake, the §181
> cross residual and §256(b) stay closed at code (`20495e48` / `c56c74a7` /
> `3a593734`). **Authoritative suite number = the CI job on the pushed SHA**
> (the gate `mvn test ... -Dmaven.test.failure.ignore=true`; check per module
> with `grep -rl FAILURE */target/surefire-reports/*.txt`), not this line — it
> rots with every commit. Refold of the `NativeRiscvAsm` concatenation to
> `<clinit>` (new anti-pattern `constant-folded-runtime-asm.md`) green in the
> `gate1585.log` gate (HEAD 54da1325).
> **3-state rule (`AGENTS.md`):** `docs/` = implemented/decided ·
> `development/` = **pending technical work** · `development/future/` =
> **plan only, zero code**. Concluded → move to a `docs/` submodule in the same
> commit; started → falls in here. The 12/09 sweep (`655afa6b`) moved 13 docs
> from `future/` to here (all with code) and 4 concluded to `docs/`.
> **Clarity refactor 13/09 (maintainer):** bugs/gaps/matrices →
> `docs/bugs-and-gaps/` (lines 2, 41, §2, §3, §4.2, §5); plans **halted by
> decision** were **ratified 13/09 and consolidated into `DECISIONS.md`** (the
> `decision-pending/` folder was extinguished — see §3). This README lists what
> **moves**; a taken decision lives in `DECISIONS.md` (rule 6: a front without a line
> there is not attacked).

**`parity/` (24/09, `D-FULL-PARITY-050`):** the full-parity blocker ledger
(`PARITY-GAPS.md`(+PT)) — every measured partial-parity row (surface ×
target × gap code × owner lane). Release condition 8 (`full_parity`): 0.5.0
does NOT cut with an open row. The ABSOLUTE rule of any plan: full parity.

**Sources of truth that are NOT here (they are not backlog):** `docs/status.md`
(what works + the suite gate), `docs/backend-parity.md` (parity
matrix with honest gaps), `docs/bugs-and-gaps/specification-gaps.md`
(SG-001–023 — maintainer queue COMPLETE, became a reference; SG-021/022 =
requests with no decision; **SG-023 ✅ DECIDED 21/09 — `D-PROPERTY`, no new
surface**).

---

## 0. What is live here (read first)

- **Pending (the release gate's condition 3):** none — the two in-flight
  OWNED plans still loose (`db-parity-plan`,
  `PLAN-BAREMETAL-BOOT`) are **allowlisted** by
  `D-RELEASE-0.5.0-SCOPE` (maintainer 21/09/2026) + `D-BAREMETAL-BOOT`
  (maintainer 22/09/2026): they keep owner + queue in §1 and conclude on their
  own fronts; they do not gate the 0.5.0 cut.
  `IMPLEMENTATION-UNIVERSAL-PLATFORM`, `makealive-plan` and `secrets-plan`
  concluded and moved to `docs/architecture/` (21/09); the type-system plan
  (X5+X6) concluded and moved to `docs/` (22/09); `kof-c-cross` (C1–C4 +
  C3-residual) concluded and moved to `docs/` (23/09).
  Authority: `scripts/check_release_050_gate.sh` (`loose_docs`).
- **Living records here (not backlog):** `DECISIONS.md`, `roadmap.md`,
  `release-beta-0.5.0-prep.md`.
  24/09: the two ratified PROPOSALs left development/ — exit-gate → `docs/`,
  versioning record → `docs/distribution/` (the operative rule is `VERSIONING.md`);
  the `tech-debt` ledger + `debt-scout` tooling were KILLED by the maintainer
  (debt measured zeroed).
- **§1 is the queue; §4.1/§4.2 are an AUDIT TRAIL** (what already left, with
  proof) — do not read them as work. How to act: §6.

---

## 1. Plan execution order (official queue of the development lane)

> Criterion: (1) front designated by the maintainer > (2) gate health >
> (3) pure-code work with no decision > (4) blocked items = DO NOT attack
> (rule 6). Living-record items (matrices/audits) have no "end" —
> they update with every closed gap, they do not pull priority.

| # | Plan | State | Why in this position | Concrete next step |
|---|---|---|---|---|
| 1 | `stdlib/PLAN-TREE-SHAKING.md` (#97) | ✅ **CONCLUDED 13/09** — S-1..S-6.1 ✅ (S-6.1 merged `0104f6d6` PR #106) + S-7 ✅ (consolidated into `docs/stdlib/stdlib-loading.md`, moved to `docs/stdlib/`) | front designated 11/09, closed; S-5-x86 remains in the bugfix queue (`root_end`, outside this plan) |
| 2 | ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **DONE 13/09 — F3 closed** (NativeBackend **498** ≤500 measured: `NativeSymbolMangling` 92 + `NativeStaticData` 116 + `emitMethodTable`→NativeClassMeta; the "GC lane in `nat/`" blocker expired — the refs no longer exist in the repo, dead-owner rule) — **PLAN CLOSED and MOVED 13/09** (F1–F9 all ✅; 3-state rule) | the ≤500 gate became a **ratchet locked in CI** (2652aa45, §140): debt does not grow and only shrinks; the authoritative count is `wc -l scripts/check_500-baseline.txt` (update by POINTING to the file, not by writing a number that rots with every split) | — (doc in `docs/architecture/`; if a new >500 residue appears, reopen as its own item) |
| 3 | ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ **CONCLUDED + PROMOTED 19/09** — §5 step-8: faces (1)–(5) all closed (GC G-0..G-6(a); DB001+CONC001 cross; FLT001; §107 record/nested on ALL 3 arches; per-arch parity columns; cross CI) — NATIVE002 CLOSED; remaining per-domain refusals (SECN000/OTP001/JSN004/RNG001/UI) are honest gap codes in `known-bugs.md` + `backend-parity.md`, not pending work of this doc | moved to `docs/` (3-state rule) | — |
| 4 | ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` (#83) | ✅ **CONCLUDED 19/09** — 1st slice ✅ 11/09 (core+`restartLimit`+`stop`) + **S2-JVM ✅ 13/09** + **S2-Native x86 ✅ 15/09** (§129 closed — TLS per-thread chain) + **S2-JS ✅ 18/09** (§132 closed; `OTP002` lifted) + **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table `kof_exc_slots`; `OTP001` gate removed; `crossGateOtp001` runs the APP on both arches) | moved to `docs/` (3-state rule) | — (DD-OTP RATIFIED 13/09) |
| 5 | ~~`plan-editor-integration.md`~~ → `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | ✅ **CONCLUDED 14/09** — degrees 0–13 implemented and proven (`EditorIntegrationTest` 23/23; `kof editor` complete across 7 editors; release gate §19 green) | moved to `docs/tooling/` (3-state rule) | — |
| 6 | ~~`plan-stdlib-expansion.md`~~ → `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ **CONCLUDED 14/09** — S0–S13 implemented and validated on 5 targets; pending decisions consolidated in `DECISIONS.md` §D-STDLIB | moved to `docs/stdlib/` (3-state rule) | — |
| 7 | newly opened queue of `DECISIONS.md` (13/09): ~~`time.todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds` (D-STDLIB)~~ **✅ EXECUTED 13/09** (S7e-S7h, stdtime3-6 matrix, suite 1772/0/0; TIME003 = general queue) · ~~`CmdNew` (D-APP I1)~~ **✅ DONE 14/09** (`kof new --type mono\|backend\|frontend\|full-stack`, compilable skeletons, honest APP003, `CmdNewTest` 8/8, APP matrix in `backend-parity.md`) · ~~`chacha20Encrypt/Decrypt` (D-SEC)~~ **✅ DONE 14/09** · ~~`security.cookies`~~ **✅ DONE 14/09** · ~~`app.security()` (C18)~~ **✅ DONE 14/09** (composite middleware, fixed order, JVM; `KofWebE2ETest` 22/22 + `appSecurityPipelineE2E`; Native/JS `WEB006`; unified superset .18×.22) · ~~`--fat` (D-APP I3)~~ **✅ DONE 14/09** (`kof build --fat` → `kof-app.jar` executable with classes+runtime+deps; `CmdBuildFatTest` 4/4, `java -jar` proof; non-JVM honest refusal R6) · ~~blog E2E (D-SPRING F12)~~ **✅ DONE 14/09** (`KofBlogE2ETest` green; exposed+fixed 2 JVM bugs: `readRequest` counted body in chars vs `Content-Length` in bytes — hung a multibyte UTF-8 connection; raw JDBC CLOB on the read path) | `RATIFIED` (decision locked 13/09) | — | ~~TLS own cert (D-SEC)~~ **✅ DONE 14/09** (`app.listenSecure(port, certPem, keyPem)`, PKCS#8 PEM, JVM; `KofWebTlsTest` 7/7; Native/JS `WEB002`) · ~~OAuth resource-server (D-SEC layer 16)~~ **✅ DONE 14/09** (`auth.resourceServer(jwksUrl,issuer,aud)` + `resourceServerVerify`; RS/ES via JWKS, no alg confusion; integrates with `auth.authenticated`/`app.security`; `KofOAuthResourceServerTest` 4/4; Native/JS `SECN007`) — **§7 QUEUE EMPTY**; each line = unit-test-commit |
| 8 | **`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (MOVED TO `docs/architecture/`, Stages 1–3 + R + Stage 8)** (+ vision companion `docs/architecture/UNIVERSAL-PLATFORM-VISION.md`) | `IN PROGRESS` — **promoted from `future/` 17/09** (`DECISIONS.md` §D-UNIVERSAL, R12 overridden); split 17/09 into executable steps + vision companion | maintainer directive 17/09: promote and implement | **Stages 1–8 + R1–R12 as executable items** (status ✅/🟡/🔵/⛔ + owner lane + proof) — live state: **R1 ✅ DONE** (`5f1422c6` boundary gate+ledger+CI); **R6 ✅ machine gate** (`DomainGapCodesTest…` `19a740f2` + ledger sweep `c5897cd5`); **R5 ✅ machine gate 21/09** (tier in `scripts/stdlib_boundary.txt` + `check_stdlib_boundary.sh`, D4-A); **X8 ✅ 21/09** (property idiom `test`+`rng`+`assert`, `D-PROPERTY`); **1.5 ✅ OTel export landed** (`435b7013`; Native `OBS003`); 1.1 MEDIA = `MEDIA001/003` documented, queued behind the `.22` HTTP facades; 1.2 GC x86 = ✅ G-6(a) auto-collect landed 19/09 (`a904317e`, §260 CLOSED, D1-A); 1.4 registry = **✅ MVP 19/09** (D2-A: publish + pull 1.5.3-S2). Claim in `DOING.md` before code |
| 9 | ~~`workflow-plan.md`~~ + ~~`shell-plan.md`~~ (+PT) → `docs/workflow-plan.md` / `docs/shell-plan.md` | ✅ **CONCLUDED 19/09** — workflow: all five faces landed (`WorkflowE2ETest` 20/20, byte-parity JVM==JS, Native real); shell: 2.2.0–2.2.4 landed (`ShellE2ETest` 15/15; only residual = JS live-pipe `pipeline`, a platform item on tracker row 2.2, not a plan slice) | moved to `docs/` (3-state rule — a concluded plan may not stay in `development/`) | — |
| 10 | `D-WORKFLOW-RUN` (Stage 2 rows 2.5/2.6) — `kof workflow run` full runner + CI/CD pipeline example | ✅ **LANDED 19/09** (owner platform lane, sessão 19/09-3/9093): convention `pipeline(): KofWfDag`; `list`/`run --job`/`--dry-run`/`--json`; host `order()`/`runJob()` + `CmdWorkflow`; `examples/ci/ci-pipeline.kf` E2E golden (`CmdWorkflowTest` 9/9) | decision locked in `DECISIONS.md` §D-WORKFLOW-RUN; implemented directly (tooling slices, X9 `kof deploy` precedent) | residual: JS/Native runner faces are honest follow-up slices (R7) |
| 11 | ~~`makealive-plan.md` (+PT)~~ → `docs/architecture/makealive-plan.md` — D-MAKEALIVE: infrastructure as typed code — **all rows 3.1–3.8 LANDED** (MK-1 core 20/09; 3.3 reconcile; 3.2 `966c86a4` `D-MAKEALIVE-SYNTAX`; 3.7 closed runtime-only; 3.8 `D-MAKEALIVE-CLI`; **3.6 secrets landed 21/09 `32285136`**) | ✅ **CONCLUDED + MOVED 21/09** | — (3-state rule) |
| 12 | ~~`secrets-plan.md` (+PT)~~ → `docs/architecture/secrets-plan.md` — `D-SECRETS`, Stage 5/tracker 3.6 | ✅ **CONCLUDED + MOVED 21/09** — all faces LANDED `04473bbe` (`Secret` `32285136`; P1-remainder `fromBytes`/identity `hashCode`; P2 runtime+lint redaction; P3 `KeyHandle`/`rotate` `SECN010`); `SecretE2ETest` 7/7 + `KeyHandleE2ETest` 5/5 | — (3-state rule) |
| — | ~~`ffi-abi-structs.md` (+PT)~~ → `docs/ffi-abi-structs.md` — FFI struct/array ABI (D6) | ✅ **CONCLUDED + MOVED 23/09** — all slices landed (3.8a `AbiLayout`; 3.8b JVM param+return+array+buffer; JS param+return+array+buffer; 3.7 x86-64 param+return+sret+`T[]` copy-in + cross INTEGER struct return+param); proof FFI battery **60/60 green 23/09** | moved to `docs/` (3-state rule) | — |
| — | `memory-safety-plan.md` (+PT) — `D-MEMORY-SAFETY` 25/09 | `IN DEVELOPMENT` — maintainer 25/09: memory-safety front (ownership/lifetime/borrowing/aliasing/FFI) **opened and owned by the parity lane**; Phases 0–1 (investigation + spec) are current work, compiler/core edits wait for the current queue (brief constraint); Kof-first: no copied borrow checker, null safety untouchable, rule 11 Simplicity Law | **parity lane** (D-MEMORY-SAFETY) · Phase 0 = sweep the 20 §1 questions + `known-bugs.md` reference/aliasing/lifetime bugs, produce `memory-safety-investigation.md` (EN+PT) — ZERO core edits | Fase 1 spec `docs/spec/memory-safety.md`; Fases 2–6 gated per the plan table |
| — | `db-parity-plan.md` (+PT) — `D-DB-GAPS` addendum 21/09 | `IN DEVELOPMENT` — maintainer 21/09: **total DB parity** (every target accepts mariadb/mysql/sqlite/mongodb); measured matrix + slices S0–S4 | **`gaps-db` lane** (handed over 21/09 by order of the maintainer; docs/plataforma keeps the record) · **S0 ✅ DONE 21/09 (session 9092: `DB001` named refusal + link-by-use)** (`D-DB-PARITY-OWNER`) | S1 `mariadb://` = mysql-wire alias (Native); S2 JDBC scheme parity JVM/JS/Android; S3 `mongodb://` interop-first (R9); S4 oracle |
| — | ~~`codegen-step-2.2.3-assessment.md`~~ → `docs/architecture/codegen-step-2.2.3-assessment.md` (+PT) — roadmap 2.2.3 | ✅ **CONCLUDED + MOVED 21/09** — option B (`D-DESUGAR-STEP`) **implemented** (`85779f20`: `DesugarStepPipeline` + `DesugarSteps.defaults()` with the four desugars; `CompilerPipeline:303`) | measured 21/09: **phase mismatch** (hook = optimized IR; DDL = lowering; runner = AST desugar) → the DDL stays in lowering | — (doc in `docs/architecture/`; 3-state rule) |
| — | ~~`type-system-extensions-plan.md` (+PT)~~ → `docs/type-system-extensions-plan.md` — X5 variance+sealed / X6 interop reflection | ✅ **CONCLUDED + MOVED 22/09** — X5.0–X5.5 + X6.0–X6.3 all landed (X5.5 cells `sealedswitch`/`variance`/`useproj`; X6.3 cell `interopschema` + Arrow/Parquet binding E2E, `InteropSchemaE2ETest` 18/18); 3-state rule | — (doc in `docs/`) | — |
| — | ~~`kof-c-cross.md` (+PT)~~ → `docs/kof-c-cross.md` — `kof-c-compiler` cross targets (C1–C4) | ✅ **CONCLUDED + MOVED 23/09** — C1+C2+C3+C4+C3-residual all landed (in-repo C subset compiler emits riscv64/aarch64 via per-ISA emitters; `kof c --target`/`-c`/`.o`; struct multi-eightbyte param ≤48 B + struct return ≤16 B); proof `KofCCrossCompilerTest`/`KofCParamsCompilerTest`/`KofCStructCompilerTest` 14/14 + `KofCObjectCompilerTest` 5/5 under qemu (x86_64 oracle) | moved to `docs/` (three-states rule) | — |
| — | ~~`PLAN-BAREMETAL-BOOT.md` (+PT)~~ → `docs/PLAN-BAREMETAL-BOOT.md` — bare-metal/bootable with ring0/ring1 (faces B-0…B-6) | ✅ **CONCLUDED + MOVED 25/09** — **promoted from `future/` 22/09** (`D-BAREMETAL-BOOT`, maintainer order) · **B-0..B-3 + B-6 LANDED** (`kof_plat_*` seam on x86+cross; freestanding link + configurable heap/stack + `_end`; libc-free Schubfach dtoa on x86+cross (B-1c, §448); UEFI profile; **legacy BIOS boot runs the REAL Kof `main` bare** — B-3a+B-3b, `KO-BIOS OK`/`LM64 OK`/`PAYLOAD` under SeaBIOS, `BiosBootE2ETest` **5/0F**; ring0/ring1 with `#GP` + GDT-sabotage proof) | moved to `docs/` (3-state rule) | — (**B-4 MCU landed on riscv32**: 32-bit codegen + collector port B4-GC-1..4 `NativeMcuGcTest` 8/0 + MCU time B4-TIME `NativeMcuTimeTest` 2/0; closure per `D-BAREMETAL-MCU-GC` "and/or"; follow-ups tracked: Cortex-M3 mirror + emitter IR integration, q.v. `roadmap.md` §23) |
| — | living records: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (in `docs/bugs-and-gaps/`); `roadmap.md` (here); `roadmap-audit.md`/`complexity-audit.md` (in `docs/audits/`) | `LIVE` | **they are not backlog** — matrix/audit/queue that update together with each closure | update the cell/section in the SAME commit that closes the gap |

**R12 rule (AGENTS.md):** nothing from `future/` (RAII, package-compiler,
bare-metal) opens before SYSTEMS closes (parity + GC + stability).
**Exception, maintainer decision 17/09** (`DECISIONS.md` §D-UNIVERSAL):
**`IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (MOVED TO `docs/architecture/`) was **promoted to current work** with the R12 gate
**overridden** — its entry point is Stage 1 (SYSTEMS consolidation) + R1–R12,
so it attacks exactly the SYSTEMS items this rule requires closing.

---

## 2. Open bugs (queue in `docs/bugs-and-gaps/known-bugs.md`) — triage 13/09,
resynced 14/09 ~22:15, **live count resynced 21/09** (docs lane — living
record, the rule of §1 of the three-states table). The **authority** for the
live set is `scripts/check_known_bugs_status.sh` (EN×PT consistent), never a
number written by hand.

**The CHANGELOG cannot lie about the ledger**: `scripts/check_changelog_ledger.sh`
cross-checks every `§NNN ✅ FIXED` claim against that live set (same classifier, the
open list the gate prints) and REDs the silent-revert case that actually happened on
21/09 — a stale-base rebase flipped §388 `✅→🟡` while the CHANGELOG kept claiming the
flip, with zero conflict to warn anyone. Historical quotes of a half-closed item may
be waived only by a named line in `scripts/changelog-ledger-waivers.txt`, never by
editing the gate.

**Ledger links must actually land**: `scripts/check_ledger_anchors.sh` recomputes the
GitHub slug of every section heading and compares it — by exact string — to the
`pt-switch`/`en-switch` href of the opposite language (diacritics folded, punctuation
deleted, `_` kept). Measured 21/09: 11 of 26 hrefs were hand-abbreviations pointing at
nothing (including two this lane shipped the same morning). All regenerated to zero, and
`--selftest` plants a truncated slug so the class cannot silently return. Both gates sit
in the CI agent suite (`run-agent-tests.sh`) and fire per-change via `agent-verify.sh`.

**Living counts must match the authority**: `scripts/check_live_records.sh` extracts every
`N items`/`N live` declaration from this lane's two READMEs and requires it to equal the
classifier's live count. The class drifted twice on 21/09 — a phrase resync left a table row at
`18`, and the next resync missed the same row again, caught by the sister lane in `5a80625c`. A
number hard-coded in two places is a promise to drift; a *missing* declaration is a failure, not
a free pass (anti-neutering: a wording change must update the gate too). The hook wiring is
proven functionally — `agent-verify-wiring-test.sh` executes the real block skeleton, catching a
correctly-written regex trapped in a mis-nested `if` (a bug this lane planted and then fixed the
same hour).

The same gate also enforces EN↔PT parity of the `DECISIONS.md` decision IDs and of the section
numbering/level, and — added 21/09 — that the **`Pending (condition 3)` list in §0 equals the
loose set the release gate actually flags** (`ls docs/development/*.md` minus its `ALLOWLIST`).
The human registry may not disagree with the measurement: neither listing less nor more (a
planted extra loose doc was caught in both languages). It also checks the roadmap's **EG table** —
the source of release condition 6, which the gate reads in EN only — has the same EG-N rows and
the same closed/open state in EN and PT, by the gate's own `DONE|FEITO` rule (a planted PT
divergence is named). Finally, the section numbering/level parity that was checked for
`DECISIONS.md` now covers **every EN↔PT doc pair** in this directory — a `## 1.` in EN matched to
a `# 1.` in PT is named (a planted level slip in the PT README was caught).

**3 items in the open queue** (**26/09: lane compiler 9092 catalogued §500** (static method/field on an imported external class name that does not resolve emits empty-owner `invokevirtual "".bogus` — even the valid varargs `Arrays.asList` is affected, `ExternalClasspath` lacks `ACC_VARARGS`; needs a dedicated unit) — 2→3; **26/09: lane compiler 9092 FIXED §499** (unknown static method on a builtin type name — now `SEM074`; RED→GREEN `BuiltinUnknownMethodGuardTest` 14/14; suite 4016 0F/0E) — 3→2; **26/09: lane compiler 9092 catalogued §499** (unknown static method on a builtin type name — `String.bogus()`/`Int.bogus()` compile clean and emit `invokestatic <Owner>.bogus` → `NoSuchMethodError`; needs a curated interop whitelist shared by typer+lowerer, rule 6/11) — 2→3; **26/09: lane native-cross CLOSED §497** (native `kof.io` row 13 complete: recursive `delete` + `modifiedTime`/`isSymlink`/`moveTo`/`copyTo` on x86-64 + riscv64/aarch64; `NAT006` closed) — 4→3; **25/09: lane compiler 9092 FIXED §496** (unknown FIELD on ANY builtin namespace — now SEM102; RED→GREEN `BuiltinUnknownFieldGuardTest` 19/19) — 3→2; (**24/09: lane native-cross catalogued §494** (JVM vs Native `kof.io` `size()` error message — JVM `file not found: ` vs x86/cross `size: file not found: `; contract decision, same family as §493) — 1→2; **24/09: lane gaps-db catalogued §493** (JVM vs Native diverge on the `orm.delete`/`deleteAll` MySQL error path — JVM throws a SQLException string, Native x86-64/cross return `true`; contract decision) — 0→1; **24/09: lane compiler 9092 FIXED §488** (x86 `RuntimeDb5`: NULL → literal `null`, `len==0` → `kof_json_encode_string`; `KofDbE2ETest#nativeMysqlNullAndEmptyStringJson` JVM-oracle byte-parity, RED pre-fix `{"n":,}`) — live **1→0**; **24/09: lane gaps-db catalogued §488** — the x86 MySQL text path (`RuntimeDb5 .Ldb_mysql_null`) emits NULL as a raw EMPTY string → invalid JSON `{"n":,`, and an EMPTY string cell takes the digits-only path as a raw number (also invalid when empty); root-cause fix pending — live **0→1**) (**24/09: lane 9092 FIXED §278 face gpu** — Android compiles `kof.gpu` like the JVM, byte-identical `Main.class`; runtime = `JvmVkStubRuntime` sem FFM no ART — live **1→0**) (resynced 23/09 — **23/09: lane 9092 FIXED §485** (channel receive: draining the queue left `tail` stale → the next `send` appended with `head=0`/`count>0` → NULL deref; `channelDrainThenSendNative` x86+riscv64+aarch64) — 2→1; **23/09: lane compiler 9092 FIXED §486 face (b) `43f2833a` (return-suffixed bridge mangling) + §487 `374b2b4bb` (#610 default diamond/arity)** — 3→2; **23/09: lane compiler 9092 FIXED §486 face (a)** (reference-return covariant bridge on Native skipped — register pass-through; `NativeGenericIfaceBridgeE2ETest` 3/3 on x86+riscv64+aarch64) **and catalogued §486 face (b)** (primitive-return bridge collides on one asm symbol; needs return-type-aware native mangling; files owned by the baremetal lane) — 2→3; **23/09: lane compiler 9092 FIXED §205** (Object-typed print on Native — a record/class reference reaching `println` via `as Object` or an `Object` local now dispatches its own `toString` through `kof_tostring_table[type_id]`; §205 slice 2 = N2/tagged-box ABI; `NativeObjectBoxPrintE2ETest` 3/3 on x86+riscv64+aarch64) — 2→1; **23/09: session 9092 FIXED §483 + §271 + §248** (generic-interface dispatch on Native + interface default methods on Native/JS — §356 bridge on Native + vtable slot aligned, and the inherited default materialized on JS implementors; `GenericInterfaceAssignabilityTest` 10/10, `InterfaceDefaultMethodE2ETest` 7/7 on 4 targets) — 5→2; **23/09: lane 9093 FIXED §476 + §478 (#587 break-in-case) — 5→4**; **23/09: lane 9092 baremetal catalogued §476** (mixed pattern+value `case` list with an empty `default:` compiles clean and dies at JVM load with `VerifyError: Bad type on operand stack`) — 4→5; **23/09: lane 9092 baremetal FIXED §423** (cross channels ported — `kof_channel_*` in slice `RtB61`; NAT005 gate removed; qemu parity on both arches) — 5→4; **23/09: lane 9092 baremetal FIXED §448** (Schubfach dtoa on the cross riscv64/aarch64 for `Double`+`Float`) — 6→5; **23/09: lane 9093 nat FIXED §444** (TypeVariable branch in the x86 `valueOf` dispatcher) — 7→6; resynced 22/09 — **22/09: session 9093 (typer lane) FIXED §280** (53/91 `error("",0,0,0)` sites now report the real source position; `DiagnosticSourceLocationTest` 5/5; dead-turn WIP finished via the dead-owner rule — §442 split conflict resolved, 3 `NewExpr` sites re-applied in `SemNewExprTyper`) — 8→7; **22/09: lane 9093 FIXED §442** (split `NewExpr` → `SemNewExprTyper`; `check_500` rc=0, suite 3590 0F/0E @ `fd5119f69`) — 9→8; **10→9 when lane 9093 FIXED §268** (22/09, `D-RULE6-BATCH` option (A): cached `java.lang` probe + SEM087, raw-super extends/implements face; `JavaLangHeritageTest` 9/9) and **§288** (`D-RULE6-BATCH` option (b): parse-time `TypeVariable` single source + interim SEM085 rejection for owner-T function types; `FnTypeVarSignatureE2ETest` 9/9) and **§444** catalogued (pre-existing native gap — the set moved §288 out / §444 in, net 9); the merged-tip line **11→10** already covers **§302** closed (CLOSEALL) + **§442** catalogued (check_500 `SemExpressionTyper` 603); 21/09 — **14→10 when the maintainer's CLOSEALL batch closed §334** (`kof_box_equals` NaN boxed-erasure) **and §400** (named top-level function passed as a value → SEM011) 21/09, and lane nat/native-debug closed **§418** (riscv64 harness: bounded wait + destroy/kill, `hangingChildIsKilledByTheBoundedWait`) 21/09; before it, **15→14 when §438** (the `kof debug` session leaked its debuggee JVM + `/tmp/kof-debug-*` dir, exhausting the tmpfs) was FIXED ✅ 21/09 by lane `.18` (`KofDebugJvmSession` shutdown hook + tree-kill teardown; RED-first `CliDebugProcessLeakTest`); before it, **16→15 when §437** (check_500 red: `JvmOpCollections` 604) was FIXED ✅ 21/09 by lane `.18` (the duplicate §432 fix had left dead code in `emitMapCall`; removing it took the file to 594, `check_500` rc=0 — no split needed), after **14→16 when §437** and **§438** were catalogued 21/09 by other lanes; **15→14 when §424** (five accepted `String` methods were silently broken on JS and link-fail on Native with no gap code) was FIXED ✅ 21/09 by lane `.18` (honest `STR003` gate on JS + Native; JVM real); **16→15 when §427** (riscv64/aarch64 lowered `kof.io`/web-T1 with no cross runtime → loud `ld` undefined-reference) was FIXED ✅ 21/09 by lane `.18` (honest `NAT006` io + `NAT007` web-T1 gates); **17→16 when §426** (`time.collect()` compiled clean on JS but the runtime never exported `kofGcCollectNow`, so the artifact failed at load) was FIXED ✅ 21/09 by lane `.18` (JS gated with `TIME004`); **18→17 when §425** (riscv64/aarch64 `kof.config` was a silent default-echo stub with `supportedOn` wrongly true) was FIXED ✅ 21/09 by lane `.18` (`supportedOn` false for the cross → live `CONF001`; javadoc/matrix corrected); **19→18 when §428** (the JVM/Native DAP `default` answered every unimplemented request `success:true` + empty body, a silent façade Q7) was FIXED ✅ 21/09 by lane `.18` (both defaults now `fail`/`fail2` with `unsupported request: <cmd>`; `restart` documented as a limit); **20→19 when §432** (JVM `Map<_,Object>.getOrDefault(k,<primitivo>)` VerifyError, catalogued by the nat lane in the §352/Q4 hardening) was FIXED ✅ 21/09 by session 9093 (`.18`; written-arg type separated from the slot V in `JvmOpCollections.emitMapCall`); **21→20 when §435** (check_500 `LspServer` 601, CLI/plataforma lane) was FIXED ✅ 21/09 by lane `.18` (split into `LspJsonRpc`; `LspServer` 601→582; `check_500` rc=0); it had been catalogued 21/09; **20→22 when the gaps-db F2d1 re-push catalogued §433/§434** (the two remote-tip reds of the FFI/JS lane, `2995d0f8`: JS hello bundle +5% budget, Buffer gate audit missing JS); 23→22 when **§429** (LSP unknown request now answers `-32601`, lane docs/plataforma) was FIXED ✅ 21/09, 22→21 when **§431** (CLI tooling drift, lane `.18`) was FIXED ✅ 21/09; 20→19 when
**§380** (JS nested-`if`/`throw` codegen) was formalized ✅ `9f383bcf`,
re-measured 16/0F at the tip; 19→18 when **§381** (entity-field keyword
OOM in the parser) was fixed ✅ `576a1dcb`; 18→17 when **§394** (test harness
leaks the served app) was fixed ✅ `d0464385` and **§353** (`io` method result
inside a lambda body — SEM014) was fixed ✅ 21/09 by the compiler lane; 17→18 when **§418** (riscv64 single-step debug
harness) was RE-PUBLISHED by the native lane the same day with fresh grounding after the
tree loss its §419 retraction records — count went down (fixes) and up (a real gap
re-surfaced) in one day, which is exactly why the script, not the prose, is the
authority; 18→19 when the db/orm lane OPENED §421 (native `db.connect` accepts any
scheme silently, refusal only at `kof_orm_*`) as its own honest catalog in F2c3 — counts
moving UP because lanes keep cataloguing against themselves is the ledger working, not
rotting; 19→18 when **§396** (println of a RECORD null on Native x86-64) was fixed ✅ `461a07e2` by the native lane the same day; 18→19 when the §396 lane OPENED **§422** (an `extern` unsupported signature "compiles clean") and 19→18 the SAME day when the FFI lane RESOLVED it — **NOT a bug, a stale test**: `Int[]` binds by design since D6-2, so the assertion was repointed to genuinely-unsupported signatures (`String[]`/`List<Int>` → `FFI001`, `Buffer(Int)` → `SEM096`), rejection intact (`CompilerDriverTest` 259/0F); 18→16 when the 21/09 nat orphan sweep closed **§192** (parseOrDefault cross hang — already fixed by `5d4d59b9`, the ledger had never been flipped; `KofMathTest.parseOrDefaultCrossArch` 1/1) and **§358** (`toString` on unbounded `T` native → honest `NAT004`; `NativeGenericDispatchGapE2ETest` 2/2); 16→15 when **§258** (the CodeQL umbrella, #775+#776) was FIXED ✅ 21/09 in-file by lane `.18` (`TestJdk.javaBin()` for #775 + the exhaustive `Target` `switch` for #776); 15→16 when the SAME sweep catalogued **§423** (channels on riscv64/aarch64 were never ported — cryptic `undefined reference` link-fail now honest `NAT005`; `BareCollectionPrimitiveArgE2ETest` 12/12), the counter moving UP because the lane found and declared a real pre-existing gap; 16→24 when the review front's pass 5 catalogued **§424–§431** (semantic findings + per-target ratchets, batches 1–3); 24→23 when **§421** (native `db.connect` accepted any scheme — S0) was FIXED ✅ `b1a5373b` by the DB/db-parity lane; 23→22 when **§431** (tooling drift: dead `serveStatic`, non-fatal `Compare` unknown-option, unreachable DAP branch) was FIXED ✅ by lane `.18`; by
`scripts/check_known_bugs_status.sh`; the number is a dated snapshot — the
script is the authority). The **32** counted on 14/09 and the 13/09 list
below are the HISTORICAL snapshot, preserved for the record (taken BEFORE the
§220–§239 wave). The conclusion holds WITH
correction: the items still open are owner/blocked/rule-6 — but the "ZERO
pure-code item" was REFUTED by the 14/09 wave itself: §236 (comparisonReturn
Bool×Int) and §238 (hoist of escaping local + sipush) were pure-code items of
the decompiler and **were fixed in the development lane** (unidades 2c,
`8719e304`+`f2371212`), while §233/§234 (test migration `split()->String[]` —
compiler lane) and §237 (`computeStack` — lane .22) catalogued as owned. The
rest of the 13/09 wave (§220–§232, §235, §239) belongs to lanes .15/.18/.22
or rule 6. Items from the 13/09 list that changed since: §129-[collection]
✅ 11/09 (`3645` — the OPEN §129 is the OTP one, number collision), the rest
continue as described. Closed 13/09: §89, §106 (+JS `ab85cfae`), §117, §131 (+residual
`73ca2d58`), §127-JVM, §155, §94, §156, §81 (BigInt), §163 (interpreter
2nd wide parameter); §157-160 and §65 closed/DOES-NOT-REPRODUCE.
All hanging on:

| Group | Bugs | Who unblocks |
|---|---|---|
| Ratified decision 13/09 — pending implementation | §161/NAT-STR01 (§89 ✅ `e33425b5`, §106 ✅ `5b939106`+JS `ab85cfae`, §117 ✅ `3734f2aa`, §131 ✅ `18a64d45`, §81 ✅ `839bd73f`, §163 ✅ `d2a8a618`; §45/DD-01 CLOSED 13/09 — see `docs/decisions/DD-01-finally-return.md`) | ratified queue / executor lanes |
| Rule-6 frozen | ~~§101~~ ✅ FIXED 14/09 (DECISIONS §1 option A — pure IEEE 754 on all targets) | nobody (contract) |
| Someone else's lane | §104b-ii + §107 remaining + §114 (bugfixer — record storage-box), §132 (OTP-JS) ✅ CLOSED 18/09 (#83-JS — landed on the dev/KofJS lane, not a foreign lane), §165 (js-slices — re-verified 13/09: does NOT reproduce in a clean build, probable non-bug) — §129 ✅ FIXED 15/09 (lane development `192.168.100.18`) | lane owners |

Fixed 13/09: **§89** (numeric conversion in a primitive = alias of `as` +
warning SEM090; 4 targets — `CoreRegressionE2ETest.numericConvertMethodAliasOfAs`),
**§106** (`json.encode(Map)` keys SORTED on the 4 targets — `JsonCompleteE2ETest`
+ `jsonenc-map` cell of the matrix; JS residual `ab85cfae`),
**§117** (cancel by real TID + linear probe on Native x86 — `KofConcurrency2Test`
34/0), **§131** (method overload by signature on the 4 backends —
`CoreRegressionE2ETest.methodOverloadByArity` + harness 4/4), **§94** (EQ/NE of Double/Float in the interpreter now IEEE —
`stdsqrt` cell 4/4 without exclusion), **§127-JVM** (cast to function type →
synthetic SAM interface; `LambdaE2ETest.castToFunctionTypeJvm/Native`),
**§155** (function type as type-arg → parser preserves the spaces of the type-ref;
`LambdaE2ETest.declaredFunctionTypeListJvm/Native`), **§156** (heterogeneous
list of lambdas with the same signature → element without className, SAM
dispatch; `LambdaE2ETest.heterogeneousLambdaListJvm/Native`), **§81** (Long=BigInt
in JS, real 64-bit parity — `839bd73f`) and **§163** (interpreter: 2nd
wide parameter `Long`/`Double` read as `null` — `KofInterpreterParityTest.
wideParametersOccupyTwoSlots` + `wideparams` cell 4/4; `d2a8a618`).
Fixed 12/09: §90 (web, #98), §125,
§139, §140 (gate→ratchet), §107-face
scalar, §108, §138, MATH001, TIME002, **§145/§146/§147 (issue #101,
`440730c8` — qemu proof 42+42)**.

---

## 3. Maintainer decisions (record: `DECISIONS.md`)

> Nothing here is "halted waiting" — the fronts that awaited a decision were
> **ratified 13/09** and live in `DECISIONS.md` (D-STDLIB/D-SEC/D-APP/
> D-SPRING/D-PLAT/D-PLATFORM) with the execution queue open. The rule
> remains: **a front without a line in `DECISIONS.md` is not attacked** (rule 6);
> a chat decision is locked there in the same commit. `known-bugs.md` =
> `docs/bugs-and-gaps/known-bugs.md`.

| Item | Where | What it awaits |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `docs/stdlib/DD-STDLIB-01-array-returns.md` (CLOSED 13/09, moved to docs/) | ✅ IMPLEMENTED 13/09 (option 6a: `randomBytesHex` alias of `hex` + choice=idiom; S10c CLOSED) |
| DD-STDLIB-02 — `time.format`/`boundaries` | `DECISIONS.md` §D-STDLIB | ✅ RATIFIED 13/09 (UTC-only, ISO scalars, zero pattern-DSL) — **queue released** (todayIso/formatDateIso/isToday/hoursBetween/parseDateIso/tzOffsetSeconds) |
| DD-01 — `finally` on the `return` path | `docs/decisions/DD-01-finally-return.md` (CLOSED 13/09, moved to docs/) | ✅ IMPLEMENTED 13/09 (option 4a: FinallyFrame in the IR + finallyReturnJvm/Js gates; suite 1627/0; bug 45 CLOSED) |
| DD-OTP (concluded) | `docs/planning-otp-supervision.md` (CLOSED 19/09, moved to docs/) | ✅ RATIFIED 13/09 (option 1a: wrapper `(id, result)`) — **S2-JVM ✅ 13/09** (`Supervisor.startAll`/single selectAny loop) + **S2-Native x86 ✅ 15/09** (§129 closed, DECISIONS §2 option B) + **S2-JS ✅ 18/09** (§132 closed, `OTP002` lifted) + **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table; `OTP001` gate removed) |
| `pow`/`-lm`, `roundTo`-mode | `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ `pow` **DONE 13/09** (7a: `-lm`; 5 targets MATH001 cross; `stdmathpow` matrix + `powCrossArchRefused` `d736e36e`) · `roundTo` **DONE 14/09** (DECISIONS §3 ratified; S1b.3: `math.roundTo(Double,Int)`, half-away-from-zero by deterministic decimal scaling, no libm; 5 targets — riscv/aarch B32; `stdmathround` matrix + `roundToCrossArch` under qemu + `KofScriptStdlibParityTest.mathRoundToParity`) |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 / conformance-matrix | ✅ OPEN BY DECISION 13/09 — implement UTF-8 astral in the natives |
| §129 (cross-thread unwind via TLS) | `known-bugs.md` | ✅ FIXED 15/09 (DECISIONS §2 option B: TLS per-thread chain + per-worker handler in the trampoline; x86_64; riscv/aarch remain `OTP001`) |
| json §106 | `known-bugs.md` | ✅ FIXED 13/09 (option 2b: sorted keys) — JVM/x86/Script/JS (`5b939106` + JS residual `ab85cfae`); riscv/aarch port gap tracked separately |

---

## 4. Index of what is IN DEVELOPMENT here

### 4.1 Platforms & migration (fell from `future/` 12/09 — code started; the `~~struck-through~~` ones were **ratified 13/09 and consolidated into `DECISIONS.md`** — the 6 files of `decision-pending/` were deleted)

| File | Real state | What remains to close |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `DECISIONS.md` §D-PLATFORM (dead) | F1–3/8/9 with code (`ProjectLocator`, `KofProjectConfig`, `Target.SCRIPT`, PKG006/007, conformance 11 tests) | F1 resolved by the manifest; F4/F5→KOFUI-AUDIT/stdlib-web; F6 wasm/F7 android→D-APP Q7/Q10 table; F9→conformance-matrix |
| ~~`APPLICATION_MODEL.md`~~ → `DECISIONS.md` §D-APP (Q1–Q10 locked) | `application { onStart/onShutdown }` ✅ E2E 3 targets; I2 (full-stack) ✅ `FullStackE2ETest` | `CmdNew` (I1), I3 (`--fat`), System/distributed — queue |
| ~~`LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md`~~ → **`future/` (DEPRIORITIZED by the maintainer 15/09)** — umbrella §4 IR/Confidence, §8 diff-testing; ~~+ `DIFFERENTIAL_TESTING.md` + `LEGACY_IR.md`~~ (MERGED into the umbrella 13/09) | code stays in the repo: `inspect/decompile/translate/compare/migrate` (`Main.java:25-29`) + `Confidence`/`Type.fromJvmSignature`; **NOT current work — promotion needs her explicit decision**; **live count = `roadmap.md` §23 TIER 3–5** (do not duplicate the number here) | coverage: opaque switch/athrow, `inspect --java` (R5 of the audit), IR non-JVM |
| ~~`IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md`~~ → `roadmap.md` §23 | **MERGED 13/09** (redundancy ~85% between them; status over-claimed vs code — e.g.: `CodegenStep` ✅ nonexistent, Native FFI was FFI001) | §23 is the single plan; tiers 6–12 = `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (promoted 17/09, R12 overridden) |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparison branch `planning-future`×beta **closed 13/09** — no open code of their own lives in them: R2 lives in `DECISIONS.md` §D-APP/§D-PLATFORM; R5 in the migration cluster (`DECOMPILER.md`/`LEGACY_MIGRATION.md` §4 Phase C) | — (outside `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | CLOSED 13/09 (FinallyFrame IR + gates; bug 45 FIXED, suite 1627/0) | — (outside `development/`) |
| ~~`planning-stdlib-time-design.md`~~ → `DECISIONS.md` §D-STDLIB | `addDays`/`diffDays` on the 5 targets | ✅ RATIFIED 13/09 — queue released |

### 4.2 Living plans & audits

| File | Real state | Note |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUDED 13/09 (S-1..S-6.1 + S-7; consolidated into `docs/stdlib/stdlib-loading.md`) | S-5-x86 = bugfix queue (`root_end`), outside the plan |
| `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | S0–S6, S8–S12 ✅ (MATH001/TIME002 closed 11/09) | only pending decisions (§3) |
| ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` | ✅ CONCLUDED 19/09 — 1st slice ✅ JVM+Script; **S2-JVM ✅ 13/09**; **S2-Native x86 ✅ 15/09**; **S2-JS ✅ 18/09**; **riscv64/aarch64 ✅ 19/09** (§129 cross port — per-TID chain table; `OTP001` removed) | moved to `docs/` (3-state rule) |
| `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | CLI/DAP/LSP/stdout-json ✅ | IntelliJ plugin |
| ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ PROMOTED 19/09 (faces (1)–(5) closed; NATIVE002 CLOSED) | per-domain refusals live in known-bugs/backend-parity |
| ~~`type-system-extensions-plan.md`~~ → `docs/type-system-extensions-plan.md` | ✅ CONCLUDED + MOVED 22/09 (X5.0–X5.5 + X6.0–X6.3; cells `sealedswitch`/`variance`/`useproj`/`interopschema`) | moved to `docs/` (3-state rule) |
| ~~`security-plan.md`~~ → `DECISIONS.md` §D-SEC | A ✅; B/C ✅; C11 cookies + C18 middleware + D16 OAuth + D17 TLS-cert **ratified 13/09** (runs with I2 of the app model) | ChaCha20 = queue; OAuth: resource-server→client, provider=NEVER |
| ~~`plan-platform-completion.md`~~ → `DECISIONS.md` §D-PLAT (dead) | P0–P3 ✅; P4 (health/tracing/metrics) ❌; P5: `kof fmt` ✅ 31/08, LSP/VS Code ❌ | P4/P5 already have a home (§23/backend-parity); blog E2E = D-SPRING F12 |
| ~~`plan-spring-independence.md`~~ → `DECISIONS.md` §D-SPRING | F1–9 ✅; F10–F12 ratified 13/09 — **all IMPLEMENTED; §D-SPRING `CONCLUDED` 19/09** (audit vs code) | no open front in this record; follow-ups live in the trackers |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | Feature×4 targets matrix locked by `ConformanceMatrixTest` (11) + doc-gate | live: updates with every gap |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 with `PARTIAL`/`PLANNED` (events, batch, AI) | coverage reference |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monolith→micro) | long term |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matrix 06/09 + queue P0→P5 (P0 CLOSED 09/09) | re-audit when something closes |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (R6 face: silent no-op) OPEN | UI lane |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | **3 live** (live count — **26/09: lane compiler 9092 catalogued §500** (static method/field on an imported external class name that does not resolve emits empty-owner `invokevirtual "".bogus` — even the valid varargs `Arrays.asList` is affected, `ExternalClasspath` lacks `ACC_VARARGS`; needs a dedicated unit) — 2→3; **26/09: lane compiler 9092 FIXED §499** (unknown static method on a builtin type name — now `SEM074`; RED→GREEN `BuiltinUnknownMethodGuardTest` 14/14; suite 4016 0F/0E) — 3→2; **26/09: lane compiler 9092 catalogued §499** (unknown static method on a builtin type name — `String.bogus()`/`Int.bogus()` compile clean and emit `invokestatic <Owner>.bogus` → `NoSuchMethodError`; needs a curated interop whitelist shared by typer+lowerer, rule 6/11) — 2→3; **26/09: lane native-cross FIXED §497** (all native `kof.io` faces ported; `NAT006` closed) — 4→3; **26/09: lane native-cross catalogued §497** (native `kof.io` gaps: non-recursive `Directory.delete` on x86-64 + no native `copyTo`/`moveTo`/`modifiedTime`/`isSymlink`) — 3→4; **25/09: lane compiler 9092 FIXED §496** (unknown FIELD on ANY builtin namespace — now SEM102; RED→GREEN `BuiltinUnknownFieldGuardTest` 19/19) — 3→2; (**24/09: lane gaps-db catalogued §493** (JVM vs Native diverge on the `orm.delete`/`deleteAll` MySQL error path — JVM throws a SQLException string, Native x86-64/cross return `true`; contract decision) — 0→1; **24/09: lane compiler 9092 FIXED §488** (x86 `RuntimeDb5`: NULL → literal `null`, `len==0` → `kof_json_encode_string`; E2E `nativeMysqlNullAndEmptyStringJson` JVM-oracle byte-parity) — 1→0; **24/09: lane gaps-db catalogued §488** (x86 MySQL text path emits NULL as a raw empty string → invalid JSON, and an empty string cell as a raw number; `RuntimeDb5 .Ldb_mysql_null`; root-cause fix pending) — 0→1; **24/09: §278 gpu FIXED** (Android=JVM, FFM-free stub) — 1→0; **23/09: lane 9092 FIXED §485** (channel drain-then-send NULL deref in `kof_channel_receive` — stale `tail`; `channelDrainThenSendNative` x86+riscv64+aarch64) — 2→1; authority is `scripts/check_known_bugs_status.sh`; **23/09: lane compiler 9092 FIXED §486 face (b) `43f2833a` (return-suffixed bridge mangling) + §487 `374b2b4bb` (#610 default diamond/arity)** — 3→2; **23/09: lane compiler 9092 FIXED §486 face (a)** (reference-return covariant bridge on Native skipped — register pass-through; `NativeGenericIfaceBridgeE2ETest` 3/3 x86+riscv64+aarch64) **and catalogued §486 face (b)** (primitive-return bridge collides on one asm symbol; needs return-type-aware native mangling; files owned by the baremetal lane) — 2→3; **23/09: lane compiler 9092 FIXED §205** (Object-typed print on Native — record/class reference now dispatches `toString` via `kof_tostring_table[type_id]`; §205 slice 2 = N2/tagged-box ABI; `NativeObjectBoxPrintE2ETest` 3/3 x86+riscv64+aarch64) — 2→1; **23/09: session 9092 FIXED §483 + §271 + §248** (Native generic dispatch + interface defaults on Native/JS; `GenericInterfaceAssignabilityTest` 10/10, `InterfaceDefaultMethodE2ETest` 7/7) — 5→2; **23/09: lane 9093 FIXED §476 + §478 (#587 break-in-case) — 5→4**; **23/09: lane 9092 baremetal catalogued §476** (mixed pattern+value `case` list with an empty `default:` compiles clean and dies at JVM load with `VerifyError: Bad type on operand stack`) — 4→5; **23/09: lane 9092 baremetal FIXED §423** (cross channels ported — `kof_channel_*` in slice `RtB61`; NAT005 gate removed; qemu parity on both arches) — 5→4; **23/09: lane 9092 baremetal FIXED §448** (Schubfach dtoa on the cross riscv64/aarch64 for `Double`+`Float`) — 6→5; **23/09: lane 9093 nat FIXED §444** — 7→6; **22/09: session 9093 (typer lane) FIXED §280** (53/91 sites com posição real; `DiagnosticSourceLocationTest` 5/5) — 8→7; **22/09: lane 9093 FIXED §442** (split `NewExpr` → `SemNewExprTyper`; `check_500` rc=0, suite 3590 0F/0E @ `fd5119f69`) — 9→8; **22/09: lane 9093 FIXED §268** (`D-RULE6-BATCH` (A): `java.lang` probe + SEM087; `JavaLangHeritageTest` 9/9) and **§288** (`D-RULE6-BATCH` (b): parse-time `TypeVariable` single source + interim **SEM085** rejection; `FnTypeVarSignatureE2ETest` 9/9) and catalogued **§444** (pre-existing: generic class with a T-arg ctor runs silent on Native, measured on clean origin) — 10→9 on §268, then §288 out + §444 in keeps it at **9**; **11→10 when re-measured at the merged tip — §302 closed (CLOSEALL) and §442 catalogued (check_500 `SemExpressionTyper` 603)**; **14→12 when the maintainer's CLOSEALL batch closed §334/§400 21/09**; **re-measured at the merged tip** after §424 fixed by lane `.18` 21/09 (five `String` methods now honest-refuse `STR003` on JS/Native), §437 closed (the check_500 red was already gone: `JvmOpCollections` 594 < 600 after the §432 dead-code cleanup) and §438 fixed (the `kof debug` session now registers a shutdown hook that kills the debuggee and reclaims `/tmp/kof-debug-*`); snapshot after §421 S0 fix + §432 fixed by session 9093 (JVM `getOrDefault` VerifyError, written-arg type split from the slot V) + §423–§431 catalogued + §433/§434 (FFI/JS remote reds) by the gaps-db re-push + §429 fixed (LSP `-32601`) and §431 closed by lane `.18` + §435 (check_500 red catalogued by the gaps-db lane) closed by lane `.18` + §436 (`StdCatalog` signatures for `secrets.of`/`secrets.secret`, D-SECRETS lane) fixed by lane `.18`/9093 21/09 + §437 (check_500 red: `JvmOpCollections` 604, JVM lane) catalogued 21/09 (19->20) + §438 (test-infra: cli debug/serve tests leak suspended JVMs + `/tmp` scratch dirs -> tmpfs exhausted; host cleaned 6.3G->621M) catalogued 21/09 (20->21); §435 was closed by lane `.18` (check_500 `LspServer` 601→582, split into `LspJsonRpc`); 18→16 when §192 and §358 were closed by the nat orphan sweep 21/09; 16→15 when §258 was closed 21/09 (#775 `NumericFormatterE2ETest` + #776 `KofHttp.supportedOn`); 15→16 when §423 (cross channels, NAT005) was catalogued 21/09; was 20 — §380 `9f383bcf`, §381 `576a1dcb`, §394 `d0464385` and §353 21/09 closed; §400/§418/§421 catalogued/re-published; the §2 live set is the authority; the historical 14/09 count was 32; §81/§163/§127-JVM, §155, §94, §157-160 and §65 closed/DOES-NOT-REPRODUCE 13/09; §425–§428 were closed by lane `.18` 21/09 (CHANGELOG entries: cross `NAT006`/`NAT007`, JS `TIME004`, config `CONF001`, §428 fix)) | live queue |
| ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ **DONE + MOVED 13/09** (F1–F9 all closed — F3: NativeBackend 498 ≤500 measured, GC lane blocker expired/dead-owner rule); ratchet `check_500-baseline.txt` (debts locked — authoritative number = `wc -l` of the file) in CI | plan CLOSED (3-state rule) |
| `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (MOVED TO `docs/architecture/`, **CONCLUDED** — Stages 1–3 + R + Stage 8 implemented, future stages split to `development/future/`) | promoted from `future/` 17/09, R12 overridden (`DECISIONS.md` §D-UNIVERSAL); future stages split to `development/future/` per 3-state rule | architecture for Tiers 6–12; vision/design frozen, state claims synced to code |
| `docs/PROPOSAL-1.0-EXIT-GATE.md` (+PT; saiu de development/ 24/09) | **KOF 1.0 EXIT GATE — RATIFIED 20/09/2026** by the maintainer (`DECISIONS.md` §D-RELEASE-1.0); promoted from `future/`: the gate (§8) + the queue (§23) are the binding stabilization meta — **Kof RC 1.0 / release 1.0 exist only when every item matches and no edge is open** | order of execution = the PROPOSAL's own §23, tracked in `roadmap.md` §24 (EG-1..EG-10); **all seven `[? MEL]` edges CLOSED 20/09 by `DECISIONS.md` §D-1.0-EDGES** — KofC + Android inside the 8-target Stable 1.0 with their own gates (EG-9/EG-10), the nine §35 reinforcement candidates are mandatory gates, the 1.0 line opens after the 0.5.0 cut + EG-1..EG-7; the only remaining edge is the maintainer's RC-opening declaration (EG-8) |

### 4.3 `future/` — plan only, zero code (not current work)

> The **full, authoritative index** of this folder (every plan + its trigger)
> is `future/README.md` — the rows below are the ones that most often gate
> current work; when in doubt, read that index, not this table.

| File | Trigger to fall in here |
|---|---|
| `PLAN-MULTIPARADIGMA.md` (multiparadigm / functional pipelines + declarative queries; 16/09, design only) | first functional increment begins (SYSTEMS closed, R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump with `using`/`resource_scope` decided |
| ~~`PLAN-BAREMETAL-BOOT.md`~~ → **promoted 22/09 to the development index, CONCLUDED + moved to [`../PLAN-BAREMETAL-BOOT.md`](../PLAN-BAREMETAL-BOOT.md) 25/09** | **CLOSED 25/09** (`D-BAREMETAL-BOOT` promoted 22/09; `D-BAREMETAL-MCU-GC` "and/or" closed B-4 on riscv32) — bare-metal front complete end-to-end (BIOS/UEFI/MCU riscv32); follow-ups (Cortex-M3 mirror, emitter IR integration) tracked in §23 |
| `PLAN-BOOTSTRAP.md` (the Bootstrapper: Kof written in Kof — **north star**, `DECISIONS.md` §D-BOOTSTRAP, 20/09) | 1.0 EXIT GATE closed + entry conditions E1–E6 (`roadmap.md` §24) |
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (legacy migration platform) | **back here 15/09 — DEPRIORITIZED by the maintainer**; promotion needs her explicit decision |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` **left `future/` 13/09** — decision 6a ratified, implemented and moved to `docs/stdlib/DD-STDLIB-01-array-returns.md`.)*

*(historical moves of 12/09: 13 docs fell from `future/` to here —
evidence in each line of §4.1; SG snapshot 08/09 → `docs/history/`)*

---

## 5. What is NO longer here (consolidated 12/09, with proof)

| Left for | Doc | Proof |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–023 + E1–E3 | maintainer queue COMPLETE (summary of the doc itself); old snapshot → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | levels 0–4 | query DSL 01/09 (`KofOrmE2ETest` 32; JS parity 18/09), MySQL prepared (`nativeMysqlPreparedBinary`), pooling ✅; DB001/DB002/ORM001 (native) live in the parity matrix |
| `docs/audits/complexity-audit.md` | snapshot 02/09 | pre-SOLID-500 numbers; live gate = `scripts/check_500.sh` (ratchet) |
| `docs/history/roadmap-gap-2026-09-03.md` | dated gap report | pending items live in roadmap-audit/known-bugs |
| `docs/decisions/` | `planning-switch-expr`, `planning-mutability` | SYN001, DD-02/SEM037/SEM038 applied |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | CANVAS001 | `UiE2ETest` 29/29 without exclusions |

---

## 6. How to use (autonomous agent)

```
1. READ docs/status.md + docs/backend-parity.md            → what works (gate)
2. READ the §1 queue of this README + DOING.md (owners)    → what is missing, without collision
3. BUGS: known-bugs.md §Aberto only with an owner at the table; decision → §3, do not edit
4. EXECUTE a scope → test (suite with -Dmaven.test.failure.ignore=true)
   → commit with DOING.md updated → move doc to docs/ if CLOSED
5. RE-DISPATCH: no item in the §1 queue without an owner AND suite green → REFUSE
   (AGENTS.md stability condition)
```

**Synchronization:** `git fetch && git pull --rebase --autostash` before EVERY
commit; re-read this README after the pull (another agent may have closed an
item of the queue). `DOING.md` marks owner/state; this README is the **queue**.

**Do not confuse:** `training/` + `learn/` + `docs/` = stable corpus.
`development/` = work that is not yet expected behavior. A frozen-contract
change never passes through here without bump + decision (rule 6).
