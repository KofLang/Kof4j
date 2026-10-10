[English](PLAN-STDLIB-EXPANSION.md) | [Português](PLAN-STDLIB-EXPANSION.pt_BR.md)

# Plan — Universal Standard Library (STDLIB)

**Owner:** KOFSCRIPT lane (fixes-for-kofagent) · **Status:** CONCLUDED (14/09) — S0–S13 implemented and validated on 5 targets; pending decisions moved to `DECISIONS.md` §D-STDLIB · **Briefing:** maintainer 08/09 (universal stdlib, multitarget, anti-microdependency)

> **History (kept for traceability):** **S7d CLOSED 11/09**: `addDays`/`diffDays` on the 5 targets (JVM/Script S7a, JS S7b, x86 S7c `RuntimeTimeIso`, riscv/aarch **B33** — TIME002 closed; the "blocked without qemu" spec fell: toolchain+qemu present, byte-identical goldens under qemu). S1b/S1b.1 **MATH001 closed 11/09** (Double math riscv/aarch B32). Decisions/implementations ratified 13/09 (`pow`/`-lm` 7a ✅, S10c `randomBytesHex` 6a ✅, §89 alias+warning 3a ✅ `e33425b5`). S0–S6, S8–S10 DONE (audit 10/09 vs code). Items still without an algorithm in the corpus (ulid/creditCard) and the `format`/`boundaries` API shape remain open decisions in `DECISIONS.md`.

## 0. Real architecture (mapped 08/09 — DO NOT invent a parallel one)

Kof already has the exact mechanism the briefing asks for ("common API → implementation per target"):

```
Kof<Domain>.java (dev/kof/compiler root)     ← dispatch + typing (record <D>Call)
   └ MethodCallTyper.java (380-413)          ← hook: <namespace>.fn(...) by prefix
        ├ JVM:    jvm/JvmString<Domain>Runtime.java (generated source) +
        │         jvm/JvmRuntimeCallDescriptors.java (descriptors) → interpreter inherits
        │         (KofInterpreter.dispatch → JvmRuntime.hasRuntimeFn) = 2 targets out of 1
        ├ Native: runtime/Runtime<Domain>.java (x86_64 ASM) +
        │         nat/NativeRiscvAsmRtB*.java (riscv64; aarch64 = translator)
        └ JS:     js/JsRuntimeUi<Domain>.java (export kofCamelCase) — bundle kof-runtime.mjs
Test: Kof<Domain>Test.java (KofValidationTest pattern: JVM+Native+JS, 136 lines)
Gate:  ConformanceMatrixTest + docs/bugs-and-gaps/conformance-matrix.md matrix
```

Exact precedent: `validation` (G4, `KofValidation.java` 77 lines + 3 backends + test).
API idiom: **namespace-qualified** (`validation.required`, `security.hash`) — the
briefing accepts it ("adapt to the real architecture"). So: `math.clamp(...)`,
`strings.slugify(...)`, `uuid.v4()`, `encoding.base64Encode(...)`, `time.addDays(...)`
— NOT loose top-level (it would collide with the established mode).

## 1. What ALREADY EXISTS (do not duplicate)

- `json.*` (G0), `io`/`File`/`Path` (KofIo), `http.*`, `db.*`, `config.*` (includes `env`),
  `cache.*`, `log.*`, `mq.*`, `orm.*`, `web.*`, `ui.*`, `time` (now/sleep/interval/cancel),
  `crypto`/`security`/`jwt`/`secrets`/`passwords` (KofSecurity: hash/verify/needsRehash/
  constantTimeEquals/randomHex/randomInt/encryptAesGcm/csrf/session/rateLimit...),
  `validation` (required/notBlank/minLength/maxLength/lengthBetween/inRange/min/max/
  matches/isEmail/isUrl/isInt/isLong).

## 2. Real gaps (the briefing ∩ what is missing)

| Namespace | New functions (P0 first) |
|---|---|
| `math` | clamp · sign · abs · isEven/isOdd · isPositive/isNegative/isZero · lerp · percentage · roundTo · isInteger/isDecimal · parseInt/parseLong/parseDouble + OrNull/OrDefault · pow/sqrt |
| `strings` | ~~capitalize/uncapitalize~~ ✅ (uncapitalize DONE 09/09 S11, 5 targets) · toCamelCase/toPascalCase/toSnakeCase/toKebabCase (with HTTPServer/XMLParser) · slugify · truncate · repeat · reverse · count · removeWhitespace/normalizeWhitespace · padLeft/padRight · isNumeric/isInteger/isDecimal/isAlpha/isAlphaNumeric/isUpper/isLower/isAscii · escapeHtml/unescapeHtml/escapeJson · lines/words · ~~indent/dedent~~ ✅ (DONE 11/09 S3.3, 5 targets) |
| `uuid` | v4 · ~~isUuid~~ (DONE S3b-ext 09/09, 5 targets — UUID001 closed in the beta→main merge 10/09) · ~~v7~~ (DONE S3b.2 10/09, 5 targets — RFC 9562) · ulid/isUlid (P1) |
| `encoding` | base64Encode/Decode · base64UrlEncode/Decode · hexEncode/Decode · urlEncode/Decode |
| `random` | randomDouble · randomBoolean · randomChoice · randomString · randomBytes (secure split: `random.*` insecure vs `security.*` secure — already documented) |
| `validation` (ext) | ~~isCpf/formatCpf~~ (formatCpf DONE S12 09/09, 5 targets) · ~~isCnpj~~ · formatCnpj DONE S12b 09/09 (5 targets) · ~~isCep/formatCep~~ (formatCep DONE S12 09/09, 5 targets) · ~~isPis/isNis~~ · isIp/isIpv4/isIpv6/isMac/isDomain/isPort · isCreditCard (DONE S6b)/creditCardBrand/last4 (Luhn — DONE 01/10 S12c, 5 targets) · isStrongPassword/passwordScore |
 | `time` (ext) | addDays/~~addMonths~~ (DONE 01/10, 5 targets — S7a-ext: end-of-month clamp, pure-integer parity)/~~addYears~~ (DONE 01/10, 5 targets — S7a-ext2: target-year clamp, pure-integer parity) · daysBetween/hoursBetween · ~~startOf/endOf~~ (day/week/month/year) (DONE 01/10, 5 targets — S7a-ext3: composed from parity-proven primitives, pure-integer byte-identity) · isLeapYear · daysInMonth · ~~age~~ (DONE 01/10, 5 targets — S7-wedge-ext: completed years, pure integer parity) · formatDate/parseDate · isToday/~~isWeekend~~ (DONE S7-ext 09/09, 5 targets) · today |
| `net` (new, P2) | **6 scalars** `net.scheme/host/port/path/query/fragment(STR)->STR` + `queryEncode/queryDecode` — see §4 (S8 decision, 09/09) |
| `util` (P2) | debounce/throttle · retry (backoff/jitter) |

**Not** (briefing rule §48/§49 + R6): browser/DOM/storage/clipboard = KofUI lane
(kof.ui already exists); homegrown crypto = forbidden (JCA already exists); `Result`/`Option` = does not exist
in frozen semantics (null-safety + throw are the mechanism).

## 3. Committable steps (each: dispatch + 3 backends + Kof<Domain>Test + matrix + doc)

- **S0** this plan + DOING claim
- **S1a** `JvmRuntimeCallDescriptors` 504→≤500 (split by domain — gate prerequisite) — **DONE 08/09** (`ea0046c4`: 504→354, `JvmRuntimeReturnDescriptors` extracted; measured 13/09: **414 ≤500**, outside the `check_500-baseline.txt` baseline)
- **S1** `math` (P0-a) — 4 targets — **DONE 08/09** (math clamp/abs/sign/min/max/isEven/isOdd/isPositive/isNegative/isZero `d0b829a1`; Double continues in S1b/S1b.1/S1b.2; `KofMathTest` 15 tests)
- **S13a** `math.parseInt/parseLong/parseDouble` (P0, §2+line 41) — **DONE 13/09** (development lane .18): namespace facade over the EXISTING runtime fns `kof_string_to_int/long/double` on the 4 backends (rule 2, zero new runtime; riscv/aarch = B30/B31, byte-identical golden). JDK contract with trim; invalid/overflow THROWS (Or* = S13b). Proof: `KofMathTest.parse{Jvm,Native,Js,CrossArch,TypeGuardRefused}` (SEM025 guard in the typer) + `stdmathparse` matrix cell 4 targets + `KofScriptStdlibParityTest.mathParseParity`. JS route fix in `JsRuntimeOps.handleRuntimeOp` (FUNCTION args vs METHOD receiver — emit identical to the .toInt() case of JsCallEmitter). Suite 1698/0/0.
- **S13c `math.parse{Int,Long,Double}OrNull`** — **BLOCKED (rule 6, maintainer's decision)**: §125 froze primitive nullable (`Int?` folds to `()I`, `== null` = `iconst_0` — verified by javap on the 4 targets 13/09). OrNull would be identical to `parseOrDefault(s, 0)` = dead code (Q7). Requires real primitive-nullable (contract change). **S7e time (D-STDLIB): `todayIso/formatDateIso/isToday` DONE 13/09** (5 targets; proof `KofTimeE2ETest` S7e 18/18 + `stdtime3` matrix; suite 1733/0/0). **S7f `hoursBetween` DONE 13/09** (D3 symmetric floor, 5 targets; root-cause fix in the generic x86 emit 7+ args; proof `KofTimeE2ETest.hoursBetween*` 23/23 + `stdtime4` matrix + Script parity; suite 1758/0/0). **S7g `parseDateIso` DONE 13/09** (D4 serial daysFromEpoch, invalid ⇒ 0, 5 targets; serial closes with hoursBetween/daysBetween; proof `KofTimeE2ETest.parseDateIso*` 28/28 + `stdtime5` matrix + Script parity; suite 1764/0/0). **S7h `tzOffsetSeconds` DONE 13/09** (D1 host timezone; JVM/JS/SCRIPT parity by JVM oracle; **Native = honest gap TIME003** — refuses with diagnostic; proof `KofTimeE2ETest.tzOffset*` 30/30 + `stdtime6` matrix PARTIAL native + Script parity; suite 1767/0/0). **D-STDLIB TIME QUEUE CLOSED 13/09 — 6/6 items executed.**
- **S13b** `math.parse{Int,Long,Double}OrDefault` (P0, briefing §43) — **DONE 13/09** (development lane .18): parse failure RETURNS the default (never throws). Backends: JVM try/catch (`JvmStringCoreRuntime` + I/J/D descriptors), JS wrapper (`JsRuntimeUiStdlib`), x86 wrapper with local handler in `kof_exc_chain` (`RuntimeStringParseOrDefault` NEW — Kof try/catch mechanism), riscv **B41** (`NativeRiscvAsmRtB41` NEW; aarch inherits via translator; B34–B40 = other lanes — rule 8). ROOT-CAUSE fix in `KofStd`/`ExpressionMethodCallLowerer`: genuine widening (I2L/I2F/I2D/...) in std call args — Int literal in a Long param crashed COMPUTE_FRAMES (NegativeArraySize) on the JVM. JS route fix: the `kof_string_to_*` cases (S13a/S13b) were INSIDE the `kof_web_` block (inert) — moved to the main flow. Proof: `KofMathTest.parseOrDefault{Jvm,Native,Js,CrossArch}` (24 tests) + `stdmathparseord` cell + `mathParseOrDefaultParity`. Suite 1720/0/0. Residual: §175 (empty Double = 0.0 on Native — BASE parse parity, own queue).
- **S2** `strings` (part 1: cases/slug/pad/reverse/count) — 4 targets — **DONE 08/09** (predicates isAlpha/isNumeric/isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count + converters capitalize/reverse/repeat/truncate; `KofStringsTest` 16 tests)
- **S3** `strings` (part 2: escapes/lines/words/indent/isX) — **DONE** (escapeJson `aef9cf23` + indent/dedent on the 5 targets `ca1e3d36`; `KofStringsIndentDedentTest` 4 tests)
- **S4** `encoding` (hex/url/base64/base64Url) **DONE 08/09** — `stdenc` matrix
  4 targets; base64* on the **4 targets — ENC002 closed 09/09** (riscv port B23; single tolerant spec). ⚠️ Note:
  the project's JS runner (embedded GraalJS) does NOT have `TextEncoder/TextDecoder` —
  UTF-8 encoded by hand in `JsRuntimeUiStdlib`. `uuid` (v4/v7/ulid) continues in S3b.
- **S5** `random` new namespace + `validation` BR ext (CPF/CNPJ/CEP/PIS/NIS with
  reusable internal checksum — §18 briefing)
  - **kof-script PARITY DONE 10/09:** `KofScriptStdlibParityTest` (5
    tests) proves interpreter (Target.SCRIPT) × compiled JVM for the whole
    new stdlib of the session — uncapitalize, formatCpf/formatCep/formatCnpj,
    isUuid (+v4), isWeekend, random facade (contract/range, never the
    drawn value). No GAP: the interpreter resolves kof_* by reflection on the SAME
    generated KofRuntime (parity by construction, R5); the test is the proof, not
    memory. Runs in the kof-script gate (25 -> 30).
  - **S12b DONE 09/09:** `validation.formatCnpj` on the 5 targets — 14 digits
    => NN.NNN.NNN/NNNN-NN (single IBGE canonical). NEW files (gates
    were overflowing): x86 RuntimeValidationFmtBr (Br 455/500; emit after Br in
    NativeRuntime — uses its kof_br_digits) + riscv B29 (B12 484/500; append
    NativeRiscvAsm). S12 LESSONS respected (frame -48, len@16/20=0,
    movl not leal). KofValidationTest formatCnpj* 5/5 (class 34/34).
    **formatPis does NOT enter:** 11-digit mask without a single IBGE form
    (3.5.2.1 vs 3.4.3.1) = design decision — note, not code (rule 6).
  - **S3b-ext DONE 09/09:** `uuid.isUuid(STR->BOOL)` on the 5 targets — RFC 4122
    shape (36; hyphens at 8/13/18/23; the rest hex upper/lower). Does not
    validate version/variant. JVM JvmUuidRuntime + JS JsRuntimeUiUuid
    (new fragments — gates ≤500); x86 RuntimeUuid; riscv B25 (LESSON:
    band upper-bound with bltu is EXCLUSIVE — 58/71/103, not 57/70/102;
    'e'/'9' were rejected — isolated in the x86-ok/riscv-fail trace). KofUuidTest
    isUuid* (JVM/JS golden + cross assert v4()-parity).
  - **S7-ext DONE 09/09:** `time.isWeekend(y,m,d)` on the 5 targets — wrapper
    `dayOfWeek >= 6` (ISO 1=Mon..7=Sun; invalid date => dayOfWeek 0 => false,
    automatic gating). JVM JvmTimeRuntime + descriptor (III)Z (not I —
    real boolean; NoSuchMethodError discovered in the E2E); JS kofTimeIsWeekend
    (wrapper in JsRuntimeUiWeb); x86 wrapper `call kof_time_dayOfWeek` +
    cmpl $6; riscv B14 wrapper — LESSON: riscv wrapper ALWAYS saves `ra`
    (the call's jalr clobbers ra → ret returns to its own body = infinite loop;
    isolated via qemu -d in_asm); aarch translates. KofTimeE2ETest calendar*
    extended (JVM/JS/x86 println + cross assert).
  - **S12 DONE 09/09:** `validation.formatCpf/formatCep` on the 5 targets —
    BR punctuation (11 digits => DDD.DDD.DDD-DD; 8 => DDDDD-DDDD; otherwise
    original, never throws — lenient face; reuses the already ported kof_br_digits).
    x86 RuntimeValidationBr (movl $34/$39, not leal — gas); riscv B12
    (frame -48: -40 misaligns PS; len at 16, 20=0); aarch translates; JVM
    JvmStringValidationRuntime; JS JsRuntimeUiValidation (new fragment,
    Crypto 489/500 no room). KofValidationTest formatBr* (5 targets).
  - **S11 DONE 09/09:** `strings.uncapitalize` on the 5 targets — byte-for-
    byte mirror of capitalize (single dispatch KofStrings; JVM JvmStringWsRuntime, JS
    kofStringsUncapitalize, x86 RuntimeStringsConv derived, riscv B7, aarch
    translated; KofStringsTest#uncapitalizeAllTargets golden 3 + qemu assert 2).
  - **S10a/b DONE 09/09:** `randomInt(bound)`/`randomBoolean`/`randomString(n,
    alphabet)` on the 5 targets (entropy from the OS only — getrandom/SecureRandom/crypto;
    x86 alias `kof_sec_random_int`, riscv B27/B28 + aarch translator, JS
    kof_platform+crypto fallback, JVM SecureRandom). `randomChoice` does NOT enter:
    idiom `l[randomInt(l.size)]` (the rule — complexity to whoever uses it).
    binary `randomBytes`/`randomChoice` = DD-STDLIB-01
    (`docs/stdlib/DD-STDLIB-01-array-returns.md`, **DECIDED 13/09 (option 6a) +
    IMPLEMENTED in this unit**: `random.randomBytesHex(n)->String` as an
    additive alias of `random.hex`, same runtime fn `kof_random_hex`, 5 targets;
    binary `randomBytes` RESERVED; choice = idiom) — Array return
    in the dispatch layer is a design decision, not an edit.
  - **S10 face main (845284e5 + fix §92, beta→main merge 10/09):**
    `random.double/boolean/int/hex` — the TWO faces coexist in the dispatch
    (`KofRandom.staticMethod` accepts `randomInt` AND `int`, etc.; same runtime
    fn, additive retrocompat). The `double` closed FLT001 for the random family
    on riscv/aarch (B27: fcvt.d.l/fdiv + ucvtf/fld translator), after the fix of the
    divisor 2^52→2^53 (§92). Documented edge: `hex(n<=0)` → null on JVM/JS,
    `""` on x86/riscv (pre-existing kof_sec_random_hex callee — divergence
    recorded in the matrix, not silent). KofRandomTest 12/12.
- **S6** `validation` network ext (IPv4/IPv6/mac/domain/port) + Luhn — **DONE** (S6a/S6b, `KofValidation.java` isIpv4/isIpv6/isMac/isPort/isDomain/isCreditCard + RuntimeValidationNet; stdvalidation*/stdluhn/stdipv6/stddomain matrices).
- **S7** `time` ext (add/diff/boundaries/format) — **PARTIAL (open step):**
   - **DONE** calendar `isLeapYear/daysInMonth/dayOfWeek/daysBetween` (4 targets;
     stdtime matrix) + `isWeekend` (S7-ext, 5 targets). **S7a** `addDays`/`diffDays`
     on ISO date (String) JVM+Script via `java.time` (10/09 — `JvmTimeRuntime`
     reuse `kof_time_validDate`/civil epoch). **S7b** JS (10/09 —
     `JsRuntimeUiWeb`, SAME civil algorithm as the wedge, WITHOUT `Date` => byte-identical
     parity). **S7c** native **x86** (10/09 — `runtime/RuntimeTimeIso.java`:
     `.Lka_parse2` + `.Lka_civil` (EXHAUSTIVE round-trip 1..9999) + String allocation
     in asm; C harness 200k fuzz 0 fails). `stdtime2` matrix (JVM+Script+JS+x86;
     riscv/aarch=TIME002) + `KofTimeE2ETest...Time002Gate`.
   - **S7d DONE 11/09 — TIME002 CLOSED** (`addDays`/`diffDays` on riscv64/
     aarch64, slice **B33**): 1:1 port of the x86 spec (`RuntimeTimeIso`) —
     `.Lu8_parse2` (format + digits + kdv_valid) / `.Lu8_civil` (Hinnant
     inverse) / `.Lu8_put4`/`.Lu8_put2`, reusing `kdv_valid`/`kdv_epoch` from
     B14; `divl`→`divu/remu` (z≥0 by the -719162..2932896 guard); String alloc =
     riscv kof_alloc pattern; aarch inherits via translator. Gate `KofTime.supportedOn`
     removed (5 targets). **riscv LESSONS from the port (recorded in the code):**
     (1) `call` on riscv is `jalr ra` — overwrites the caller's `ra` (it is not the
     x86 stack); a helper ending in `call h; ret` needs a **tail-jmp**
     `j h` otherwise the `ret` returns to its own body = infinite loop (caught in the
     qemu trace of parse2). (2) `kdv_valid` does `call daysInMonth` and **clobbers
     s0** — no live value in `s0` between calls (everything in a stack slot,
     lesson B14). (3) `blt`/`bge` SIGNED in the epoch bounds (difference from
     unsigned `bltu`). Proof: byte-identical golden of 9 lines (measured JVM oracle)
     under qemu-riscv64 + qemu-aarch64 (`KofTimeE2ETest` inverting the old
     `Time002Gate`); KofTimeE2ETest 11/11, stdtime2 matrix + riscv/aarch E2E.
   - **OPEN**: `format`/`boundaries` (API shape — `format(date, "yyyy-MM-dd")`
     vs scalar functions `yearOf`/`monthOf`… — maintainer's surface
     decision, like the `net`/`validation` family).
- **S8** `net` url/query parse/encode — **DONE** (S8 decision §4; KofNet 6 scalars + queryEncode/Decode, RuntimeUri, stdnet, NET001 riscv closed B24).
- **S3b-wedge (uuid.v4) + S4 COMPLETE DONE 08/09:** uuid shape-verified 3 targets (SECN000 cross-arch closed 09/09 — B25 getrandom ecall); encoding hex/url/base64/base64url (stdenc matrix 11 fields × 4; gates ENC002 base64* and SECN000 uuid in the cross). JVM-runtime LESSON: never checked exceptions in the generated KofRuntime (SecureRandom new, not getInstanceStrong).
- **S1b.1 DONE (10/09):** `math.lerp(a,b,t)`/`percentage(part,total)` (Double->Double) + `math.isInteger/isDecimal(DOUBLE)->Bool` — **pure** Double scalars (SSE2 `subsd/mulsd/addsd/divsd` + `cvttsd2si/ucomisd`; 0x7ff exp = NaN/Inf, exp>=0x433 = |v|>=2^52). JVM (`JvmStringMathRuntime`) + SCRIPT (reflection) + JS (`kofMathLerp/Percentage/IsInteger/IsDecimal` — Bool=1/0, chokepoint §93) + x86 (`RuntimeMath`; arg/ret **raw bits via rax** = rides the generic path, zero change in NativeX86Calls — unlike sqrt which needed xmm). Type guard: only Double (Int does NOT widen silently — SEM025). **`pow`/`roundTo` POSTPONED**: `pow` requires libm (the native link is `-lc` only — changing the link = maintainer's contract decision — I do NOT alter the NativeAssembler without a decision). **✅ DECIDED 13/09 (option 7a): `-lm` link APPROVED — implement `pow`.** Note: the maintainer's ratification covers ONLY `pow`; the "+ `roundTo` via floor asm" was an agent annotation in this plan — `roundTo` was NOT approved (undefined surface/signature = rule 6, awaits decision).. **S1b.2 DONE 13/09 (another agent, `7f174a6f` — dispatch+shim, no E2E/matrix yet):** `math.pow(DOUBLE,DOUBLE)->Double` in `KofMath` + `RuntimeMath.kof_math_pow` (x86 `pow@PLT`, `-lm` always linked; JVM/JS `Math.pow`; riscv/aarch MATH001). PROOF: isolated C harness 18/18 (golden = measured JVM oracle, never memory — 2.675-style is left out) + `KofMathTest` doubleOpsJvm/Native/Js + **MATH001 CLOSED 11/09: `sqrtCrossArch`/`doubleOpsCrossArch` run the SAME byte-identical goldens under qemu-riscv64 + qemu-aarch64** (slice B32: fsqrt.d/fadd/fsub/fmul/fdiv/fcvt.l.d/fcvt.d.l/feq.d; aarch inherits via translator — `fsqrt` separate + `fcvt.w/l→fcvtzs` fixed here); `stdmathdouble` matrix (15 outputs) + doc-gate. KofMathTest 11/11. **Sibling FINDING (bug 101):** the cross riscv Double `NE` was `fle+snez` (it said `NaN!=NaN`=false, diverged from x86/JVM/JS=IEEE true) — fixed to `feq+seqz` in the same proof (necessary: the DBL_SRC golden uses `NaN!=NaN`). RELATIONAL divergence (`<`/`<=`/`>=` with NaN: x86=JVM via dcmpg quirk, riscv=IEEE flt/fle) is PRE-EXISTING, frozen operators (rule 6) → recorded bug 101, NOT changed (number 100 was taken by the Char-method-String bug on the same date).
 - **S1b.3 DONE 14/09 (DECISIONS §3, owner 192.168.100.18):** `math.roundTo(value: Double, decimals: Int) -> Double` — half-away-from-zero (C `round()` anchor) by deterministic decimal scaling (Java `BigDecimal.setScale` anchor, but **arithmetic**, not decimal-string): `p=10^|d|` by repeated multiply (each step 1 correctly-rounded IEEE op → byte-identical 5 targets); `d>=0`: `roundHalfAway(v*p)/p`, `d<0`: `roundHalfAway(v/p)*p` (negative decimals round to tens/hundreds); `|d|` saturates at 308; `v*p` overflow → returns `v`. No libm. Backends: JVM (`JvmStringMathRuntime`, reflection for SCRIPT), JS (`kofMathRoundTo`), x86 (`RuntimeMath`), riscv (slice B32; aarch via translator). Type guard: `decimals` must be `Int` (SEM025). Locked contract: `roundTo(2.675,2)==2.68`. Proof: `KofMathTest.roundTo{Jvm,Native,Js,CrossArch}` + `roundToTypeGuardRefused` + `ConformanceMatrixTest.stdmathround` (4 targets, doc-gate) + `KofScriptStdlibParityTest.mathRoundToParity`.
 - **S1b-wedge DONE (10/09):** `math.sqrt(DOUBLE)->Double` — FIRST Double of the `math` namespace (opens the path for lerp/percentage/roundTo/parse*/pow). JVM (`Math.sqrt`) + SCRIPT (reflection) + JS (`Math.sqrt`) + x86 (`sqrtsd %xmm0`, arg/ret by the bits convention `popq %rax; movq %rax, %xmm0; call; movq %xmm0, %rax; pushq %rax` — precedents `kof_json_encode_double`/`kof_random_double`). NaN on <0 = IEEE (parity measured on the 3). **MATH001 CLOSED 11/09:** riscv64/aarch64 — slice B32 `fsqrt.d` (+ aarch translator `fsqrt` separate); proof `KofMathTest.sqrtCrossArch` (8 byte-identical golden lines under qemu, inverting the old `sqrtGatedOnCrossArch`). **FINDING (bug 94):** the interpreter does Double `==` via `numEq`→`Double.compare` → `NaN == NaN` = `true` (divergence from the 3 compiled ones, IEEE) — `==` semantics frozen (rule 6), recorded in known-bugs + PARTIAL cell in the matrix; the wedge does NOT touch the interpreter. ⚠️ Bug 44: matrix/tests use ONLY Bool comparisons (`sqrt(9.0)==3.0`), never `println` of raw double. Proof: `KofMathTest.sqrtJvm/sqrtNative/sqrtJs` (8 byte-identical lines) + `sqrtGatedOnCrossArch` (MATH001 × 2) + `ConformanceMatrixTest.stdsqrt` (6 outputs; jvm/native/js + doc-gate) + isolated C harness (8 vectors, 0 fails — BEFORE the suite).
- **S3b.1 DONE (10/09):** `uuid.isUuid(STR)->Bool` — **shape** predicate 8-4-4-4-12 (36 chars, dashes at 8/13/18/23, hex lower/upper; version/variant NOT checked). JVM+SCRIPT+JS+x86 without gate (plain byte-scan; x86 validated in the isolated C harness — 12 vectors + null, 0 fails — BEFORE the suite, lesson S7c). **UUID001 gate (R6):** riscv64/aarch64 = own B slice pending (same stop condition as S7c-1 — no cross-assembler/qemu in the lane; x86 spec ready in `RuntimeUuid`; do NOT write asm without assembling/running). Proof: `ConformanceMatrixTest.stduuidform` (7 outputs × 4 targets, doc-gate) + `KofUuidTest.isUuidShapeJvmJsNative` (JVM==JS==x86 byte-identical; last line `isUuid(uuid.v4())` — parity with the generator itself) + `isUuidGatedOnCrossArch` (UUID001 on the 2 targets).
- **S3b.2 DONE (10/09):** `uuid.v7()->String` — RFC 9562 time-ordered UUID (48 bits unix ms timestamp big-endian + version 7 + variant 10xx + cryptographic entropy). JVM (`JvmUuidRuntime`) + JS (`JsRuntimeUiUuid`) + Native x86_64 (`RuntimeUuid` via `kof_now` and `kof_sec_random_hex`). **UUID002 gate (R6):** riscv64/aarch64 honestly rejected in the compiler until a dedicated port. Proof: `KofUuidTest` (`uuidV7Jvm`, `uuidV7Native`, `uuidV7Js`, `uuidV7MonotonicOrderJvm`, `uuidV7GatedOnCrossArch`, `isUuidShapeJvmJsNative`).
- **S1–S2b.2 DONE 08/09:** math(9) · strings predicates(8: isAlpha/isNumeric/
  isAlphaNumeric/isAscii/isUpperCase/isLowerCase/count) · strings converters(4:
  capitalize/reverse/repeat/truncate — 1st String allocation case in the runtime,
  riscv slices B7/B8; gap NAT-STR01 for reverse UTF-8 on Native). API note:
  `validation.min/max` (binary, G4) ≠ `math.min/max` (arithmetic) — distinct
  namespaces, no collision; document in learn.
- **S9** stdlib matrix in docs/stdlib/stdlib.md + learn/39-stdlib + training/idioms (math/
  strings/validation) + minimal benchmarks (clamp/slugify) if applicable — **DONE** (`std*` matrix in ConformanceMatrixTest + `docs/stdlib/stdlib.md` §STDLIB + `learn/39-stdlib.md` + `training/idioms/stdlib.md`/`strings.md`).

Each S = full suite green (with `-Dmaven.test.failure.ignore=true`) + DOING updated.

## 4. API decisions (stable before coding)

- Names: `is<Cpf>`, `format<Cpf>`, `normalize*` (§42 briefing) — already the repo's pattern.
- Parse failure = `OrNull`/`OrDefault` (briefing §43 ≡ Kof's `String?`).
- Natives: purely arithmetic functions in JS/Java/ASM without regex when trivial
  (clamp/sign/abs inlinable? NO — keep a single runtime fn for byte-identical parity
  between targets, which is what the matrix proves).
- riscv64/aarch64: while bug 59 is open, do new symbols follow the SECN000 pattern?
  NO — validation already has real riscv (B3); copy the B3 pattern (pure asm, no libc).
  Parity gate = ConformanceMatrixTest (riscv only runs via qemu in the NATIVE002 CI).

## 4. S8 decision — `net` shape (09/09)

**Chosen: 6 scalar functions** (`net.scheme(s)` … `net.fragment(s)`, all
`STR->STR`), **NOT** a `Uri(...)` record returned by `net.urlParse`. Technical
reason, not stylistic: **no asm runtime function returns a structured object
today** (KofHttp precedent: "the body is returned as a String";
records are compiler-generated classes, not allocatable by the x86/riscv asm).
A `urlParse` that returns a record would be the FIRST object allocated at runtime in
Native — its own scope, multi-session, and requires separate design (allocatable
record IR). The scalar form replicates EXACTLY the precedent of the
`validation` family (`isIpv4(STR)->Bool`, …): N functions over the same String, each
pure byte-scan, portable to the 4 targets without a new mechanism. It is **fully
additive** (new namespace → nothing frozen at stake; rule 6 satisfied).

**v1 semantics (RFC 3986 subset, honest scope locked in the matrix, R6):**
- `scheme`: `[A-Za-z][A-Za-z0-9+.-]*` before the first `:`; otherwise `""`.
- authority only after `//` ; `userinfo@` ignored (host = after the last `@`).
- `host`: up to the first `:` of the authority or its end; `port`: after that `:`
  (String, not Int — tolerant/no parse, same as the validation policy).
- **v1 WITHOUT an IPv6 literal in brackets** (`[::1]` falls into the host as is) —
  documented, like isIpv6/isDomain (no zone, no mixed form).
- `path` up to `?`/`#`; `query` after the 1st `?` up to `#`; `fragment` after the 1st `#`.
- absent field ⇒ `""` (natural "no substring"); `null` ⇒ `null` (parity
  with all the stdlib STR->STR). Malformed input ⇒ best effort per
  field, NEVER throws (validation family).
- `queryEncode`/`queryDecode`: key/value percent-encoding reuses EXACTLY
  `encoding.urlEncode/urlDecode` (do not re-implement — rule 2); `net` is an
  intent facade (`net.queryEncode` == `encoding.urlEncode`).
