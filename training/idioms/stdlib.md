[English](stdlib.md) | [Português](stdlib.pt_BR.md)

# Idioms — STDLIB (math / strings / encoding / uuid / time / process / cache / config / log / net / gpu / media)

**Status:** available · **Introduced:** 0.3.0-beta (STDLIB track, 08/09/2026) · **Updated:** 08/09/2026

## What it is

Four namespaces of pure utilities, called **without the `kof.` prefix**
(style `math.clamp(...)`, `strings.slugify(...)`, `encoding.hexEncode(...)`,
`uuid.v4()`). Same API on the targets JVM / interpreter (Script) / Native
(x86_64) / JS; cross-arch gaps are **compile-time diagnostics**, never a
silent stub (R6).

## math — Int-only (S1)

```kof
math.clamp(v, lo, hi)      // hi < lo => swap behavior NOT guaranteed: validate beforehand
math.abs(x)  math.sign(x)
math.min(a, b)  math.max(a, b)     // arithmetic; ≠ validation.min/max (size predicate)
math.isEven(x) math.isOdd(x) math.isPositive(x) math.isNegative(x) math.isZero(x)
math.sqrt(2.0)                          // Double; -1.0 => NaN (IEEE); riscv/aarch = MATH001
math.lerp(0.0, 10.0, 0.5)               // a + (b - a) * t — linear interpolation (S1b.1)
math.percentage(3.0, 4.0)               // 75.0; total == 0 => NaN (never throws) (S1b.1)
math.isInteger(4.0)                     // true; 4.5/NaN/Inf => false (S1b.1)
math.isDecimal(4.5)                     // !isInteger (S1b.1)
math.roundTo(3.14159, 2)                // 3.14 — half-away-from-zero to N decimals (S1b.3)
math.roundTo(1234.0, -2)                // 1200.0 — negative decimals: tens/hundreds (S1b.3)
```

Double: `math.sqrt(x)` (S1b) + `lerp`/`percentage`/`isInteger`/`isDecimal`
(S1b.1, 10/09 — **pure** Double scalars, without libm) + `roundTo(value, decimals)`
(S1b.3, 14/09 — half-away-from-zero by deterministic decimal scaling, no libm;
`decimals` is an `Int`, may be negative; arithmetic contract: `roundTo(2.675,2)==2.68`)
exist on JVM/Script/JS/x86; NaN at <0 = IEEE; riscv64/aarch64 = `MATH001` for
`pow` only (the rest run under qemu). The args are **explicit Doubles** —
`math.lerp(0, 10, 0.5)` (Int) **does not** compile (SEM025; no silent widening).
`pow` is implemented on x86 (libm) but `MATH001` on the cross (no libm link).

## strings — predicates and converters (S2)

```kof
strings.isAlpha("Hello")           // letters only, non-empty; "abc123" => false
strings.isNumeric("123")  strings.isAlphaNumeric("abc123")
strings.isAscii("ola")             // bytes >=128 => false (café => false on the 4 targets)
strings.isUpperCase("HELLO")       // >=1 letter and no lowercase; "123" => false
strings.isLowerCase("abc-123")     // other chars ignored
strings.count("aabaabaa", "ab")    // 2 — NON-overlapping; empty sub => 0
strings.capitalize("hello")        // "Hello" (ASCII; 1st byte a-z)
strings.uncapitalize("Hello")      // "hello" — exact mirror of capitalize (S11)
strings.reverse("abc")             // "cba" (byte-reverse on Native — see NAT-STR01)
strings.repeat("ab", 3)            // "ababab"; n<=0 => ""
strings.truncate("hello", 3)       // "hel"; n>=len => original; n<=0 => ""
strings.padLeft("7", 3, "0")       // "007" — pad is a STRING, uses the 1st char
strings.toCamelCase("hello_world") // "helloWorld"
strings.toPascalCase("hello world")// "HelloWorld"
strings.toSnakeCase("HTTPServer")  // "http_server" — boundary at uppercase+lowercase!
strings.toKebabCase("XMLParser")   // "xml-parser"
strings.slugify("Hello, World!!")  // "hello-world" (non-ASCII becomes a separator)
```

## BAD — reimplementing what the stdlib has

```kof
// ❌ Java in disguise
Bool isAlpha(String s) {
    if (s.length == 0) { return false }
    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i)
        if (!(c >= 65 && c <= 90) && !(c >= 97 && c <= 122)) { return false }
    }
    return true
}
```

## GOOD — the abstraction exists

```kof
// ✅
var ok = strings.isAlpha(s)
```

## WHY

The iron rule is "complexity belongs to the platform". The byte loop
above exists in 4 different backends inside the compiler — written once,
tested in the conformance matrix (`stdstrings`), parity locked. Reusing is
shorter, faster and cross-target by construction.

## encoding — hex / base64 / url (S4)

```kof
encoding.hexEncode("café")            // "636166c3a9" (UTF-8 by bytes, lowercase)
encoding.hexDecode("4869")            // "Hi"; invalid digit => 0; odd => last is HIGH nibble
encoding.base64Encode("Man")          // "TWFu" (with padding)
encoding.base64Decode("TWFu")         // TOLERANT: ignores invalid ones, stops at '='
encoding.base64UrlEncode(bytes...)    // alphabet -_, WITHOUT padding (JWT-style)
encoding.base64UrlDecode(s)           // accepts both alphabets + optional padding
encoding.urlEncode("a b")             // "a%20b" — space => %20, NOT '+'
encoding.urlDecode("caf%C3%A9")       // "café"; '%' without 2 digits passes literally
```

## uuid (S3b)

```kof
var id = uuid.v4()   // e.g.: "xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx" (RFC 4122 shape)
uuid.isUuid(id)      // true — validates the SHAPE (dashes 8/13/18/23 + rest hex); does NOT check version/variant
```

Non-deterministic: validate by **shape** (`isUuid`, or by hand: dashes at 8/13/18/23,
digit 14='4', digit 19∈{8,9,a,b}), never by equality. `uuid.v7()` (time-ordered, RFC 9562)
exists on all 5 targets (digit 14='7', digit 19∈{8,9,a,b}); ulid does not exist yet.

## random (S10a/b)

```kof
// ❌ BAD — own PRNG, internet LCG
var seed = 12345
seed = (seed * 1103515245 + 12345) % 32768
```

```kof
// ✅ GOOD — platform entropy, intent face
var roll = random.randomInt(6) + 1
var pass = random.randomString(12, "abcdefghijkmnpqrstuvwxyz23456789")
var flip = random.randomBoolean()              // coin toss
var pick = colors[random.randomInt(colors.size)]   // choice = idiom
```

**WHY:** `random.*` = draw (non-cryptographic); `security.*` = tokens
(rejection + validation). List choice **is not** a stdlib function —
`list[random.randomInt(list.size)]` is the idiom; `randomChoice` would require
an Object return in the dispatch layer (DD-STDLIB-01 — CLOSED 13/09, decision
6a: `randomBytesHex` alias of `hex` + choice=idiom; `randomBytes` reserved).

## rng — determinism you can TEST (X8 slices 1–2)

```kof
// ❌ BAD — unseeded draw inside a test (passes/fails at random, unreproducible CI)
test "sum in range" {
    var a = random.randomInt(100)
    var b = rng.int(50)
}
```

```kof
// ✅ GOOD — seeded PRNG: same seed => same sequence, any backend
test "sum commutes on random pairs" {
    rng.seed(42)
    var i = 0
    while (i < 500) {
        var a = rng.int(10000) - 5000
        var c = rng.int(10000) - 5000
        assert(a + c == c + a)
        i = i + 1
    }
}
```

**WHY:** `rng.*` = REPRODUCIBLE determinism (xorshift128 + splitmix32,
32-bit-exact — same seed, same bits on JVM and JS, `KofRngTest.jvmJsParity`);
`random.*` = OS entropy (R11). A failing property test prints its seed and the
failure reproduces. Mixing the two is the anti-pattern: seeding for security
material (R11 violation) or drawing entropy from rng (flaky tests). Slice 1 =
JVM + JS + NATIVE x86_64 (asm `RuntimeRng`, same bits by construction); cross riscv64/aarch64/ANDROID = `RNG001` honest gap at compile time.

## validation — formatting is NOT validating (S12/S12b)

```kof
// ❌ BAD — scoring by hand, and throwing when the CPF has too many digits
var out = ""
for (var i = 0; i < cpf.length; i++) {
    out = out + cpf.charAt(i)
    if (i == 2 || i == 5) { out = out + "." }
}

// ✅ GOOD — the two faces, each in its place
validation.isCpf("52998224725")     // STRICT: false if check digits do not match
validation.formatCpf("529.982.247-25") // "529.982.247-25" — LENIENT: only punctuates
```

**WHY:** `formatCpf`/`formatCep`/`formatCnpj` **form, they do not validate**: they remove
existing punctuation and reapply the mask; if the number of digits does not match
(or is `null`), they return the **original input** — never throw, never truncate.
Whoever decides whether the document is *valid* is the strict face (`isCpf`/`isCnpj`/
`isCep`). Separating the two is the "represent the intent" rule: formatting
presentation is one thing, checking legitimacy is another. The same applies to
`time.isWeekend(y,m,d)` (calendar only, no clock — invalid date => `false`
because `dayOfWeek` gives 0).

## shell — commands without a string (2.2 v1, 18/09)

BAD: `process.run("sh -c \"echo a | wc\"")` — a command string = injection class +
broken cross-target parity.

GOOD:

```kof
import kof.shell
var r = shell.run("git", listOf("status", "--short"))
if (shell.ok(r)) { println(r.stdout) }
var n = shell.pipeline(listOf(listOf("echo", "one two"), listOf("wc", "-w"))).stdout
```

WHY: argv is a **list**, never a string — the golden
`argvIsNeverConcatenatedIntoShellString` pins that `"a b|c && d"` survives as ONE
element; `Result` is the SAME type as `kof.process`'s (one shape, never a fork);
non-zero exit is data, not an exception. Honest faces: `pipeline` = JVM-real,
JS/Native = `PROC001` at compile-time (R6); full doc in `docs/stdlib/shell.md`.

## time — calendar, ISO, clock (8.5, 19/09)

```kof
val iso  = time.todayIso()                       // "2026-09-19"
val week = time.addDays(iso, 7)                  // ISO in, ISO out
val gap  = time.diffDays("2026-01-01", "2026-09-19")
val dim  = time.daysInMonth(2026, 2)             // 28
val dow  = time.dayOfWeek(2026, 9, 19)
val hrs  = time.hoursBetween(2026, 9, 19, 0, 2026, 9, 19, 12)
val leap = time.isLeapYear(2026)                 // own calendar math: BAD, 18 members exist
time.sleep(50); val t = time.now()               // epoch millis
val id = time.interval(1000, () -> println("tick"))
time.cancel(id)
```

WHY: the calendar face is **pure String/Int functions** — no date object to import,
no corner-case math of your own. `parseDateIso`/`formatDateIso` move between the
ISO string and parts. LSP hover/signatureHelp lists all 18 with real arities.
Interpreter parity for the new faces: `KofScriptStdlibParityTest`.

## process — external commands, argv as varargs (8.5, 19/09)

```kof
// ❌ BAD — a List here (that is shell's shape; process's varargs reject it, SEM025)
val r = process.run("git", listOf("status"))
// ✅ direct command: varargs of String
val r = process.run("git", "status", "--short")
if (r.exitCode == 0) { println(r.stdout) }       // non-zero exit is DATA, never an exception
process.exit(1)                                  // terminate with code — taught in learn/23-testing.md
```

WHY: `kof.process` = **one-shot command with args you already have as values**;
`kof.shell` (section below) = the list-shaped/dynamic argv and pipeline idiom.
Same `Result` shape in both (`stdout`/`stderr`/`exitCode`). Honest gates (measured
19/09 + pinned in `DomainGapCodesTest`): `run`/`spawn`/`exit` = `PROC001` at
**compile-time on Native**; `process.spawn`/`pipeline` are **REAL on JS under the
Kof JS host** (`KofJsRunner`, 19-20/09, byte-parity pinned); on a bare `node` they raise the
honest `kof_platform.*: not available outside the Kof JS host` diagnostic. Never a silent fallback.

## cache — String KV with TTL (8.5, 19/09)

```kof
cache.set("k", "v")                    // no expiry: cache.ttl("k") gives -1
cache.set("session", tok, 300)         // ttl in seconds
val v = cache.get("k")
val left = cache.ttl("session")
cache.delete("k")
cache.clear()
```

WHY: deliberately tiny — String→String in-process KV. No serialization ceremony;
if you need persistence that is `kof.orm`, not a cache flag.

## config — key + default, no sentinel strings (8.5, 19/09)

```kof
val port = config.get("server.port")            // 1 arg: value as stored
val name = config.str("app.name", "demo")       // str/int/long/bool take key + default
val url  = config.required("db.url")            // missing key is an ERROR, never a silent ""
val has  = config.has("app.name")
val home = config.env("HOME")                   // raw environment
```

WHY: literal keys are discovered **at compile-time** (`CompilerDriver.discoveredConfigKeys()`
— deploy/tooling read them before the program runs; a computed key degrades to the
runtime path, pinned in `ConfigGenTest`). Default belongs in the call, not in an
`if (s == "")`.

## log — four levels (8.5, 19/09)

```kof
log.debug("payload " + n)
log.info("boot")
log.warn("deprecated path")
log.error("boom: " + err)
```

WHY: `println` is program **output**; `log` is program **observation** — mixing them
loses the level. Four members, `String` in, `void` out, no format ceremony
(concatenation is already efficient).

## net — URL apart, no regex (8.5, 19/09)

```kof
val u = "https://api.x.io:8443/v1/items?page=2#top"
net.scheme(u); net.host(u); net.port(u); net.path(u); net.query(u); net.fragment(u)
val q = net.queryEncode("a b&c")
val back = net.queryDecode(q)
```

WHY: hand-rolled `split("/")`/regex over URLs breaks on port, query and fragment —
each piece is a real function, all targets (interpreter parity pinned 19/09 in
`KofScriptStdlibParityTest`).

## gpu — probe first, kernels honest (8.5 fatia 3, 19/09)

```kof
if (gpu.available()) {
    var a = new Int[4]
    var c = new Int[4]
    val rc = gpu.dispatchMatmul(a, a, c, 2, 2, 2)   // rc != 0 = kernel said no, never a silent wrong answer
} else {
    println(gpu.failReason())                        // WHY the host has no face (R6)
}
```

WHY: heavy compute is an **official package** domain (R1) — `kof.gpu` exposes only
what the platform already runs (fixed-shape kernels + the `mv*` int8/long faces for
the on-device path); ML frameworks stay interop (R9). Honest gates (measured 19/09):
JVM + Native x86 ✅; riscv64/aarch64 ✅ and JS/Script ✅ since 26/09 (row 6 closed,
`D-FULL-PARITY-050`) — all degrade through the honest CPU fallback
(`available=false`, dispatch `-1`), never `GPU001`.

## media — Image/Audio/Video/Mic are namespaces, not UI widgets (8.5 fatia 3, 19/09)

```kof
val img = Image.open("photo.png")        // ImageData (pixels + width/height)
val wav = Audio.openWav("bell.wav")      // Audio
val clip = Video.open("intro.mp4")       // Video
val take = Mic.record(1)                 // Audio — 1 second from the default device
val inputs = Mic.list()                  // available capture devices
```

WHY: `Image` in `kof.ui` is a **view widget**; `Image.open` here is **media I/O**
(same name, different intent — do not confuse them). Everything the platform
decodes stays in the backend; user code never touches buffers or codecs. Honest
gates (measured 19/09): JVM ✅; **JS and Native = `MEDIA001`** at compile-time (R6);
riscv/aarch ⏳. Scope note: this is today's **data face** of `kof.media`. The
future graphics/gaming/media surface is **Kof's own engine** with FULL 4-target
parity as its acceptance criterion (`DECISIONS.md` §D-GRAPHICS-GAMING addenda 2+4;
plan `docs/development/future/graphics-gaming-plan.md`) — `MEDIA001` is honest for
the legacy face, not the model for what gets promoted.

## Note per target (honest gates)

| function | JVM/Script | Native x86_64 | Native riscv64/aarch64 | JS |
|---|---|---|---|---|
| math.*, strings.is*/count/capitalize/uncapitalize/reverse/repeat/truncate/pad*, encoding.hex*/url*, time.isLeapYear/daysInMonth/dayOfWeek/daysBetween/isWeekend, validation.isCpf/isCnpj/isCep/isPis/isIpv4/isIpv6/isMac/isPort/isCreditCard/isDomain/formatCpf/formatCep/formatCnpj | ✅ | ✅ | ✅ | ✅ |
| strings.toCamel/Pascal/Snake/Kebab/slugify | ✅ | ✅ | ✅ (STRN001 closed 09/09 — B15, golden diff qemu) | ✅ |
| strings.escapeHtml/escapeJson (5 entities; >=128 copy) | ✅ | ✅ | ✅ (B20, golden diff qemu) | ✅ |
| strings.removeWhitespace/normalizeWhitespace | ✅ | ✅ | ✅ (B21) | ✅ |
| encoding.base64* / base64Url* | ✅ | ✅ | ✅ (ENC002 closed 09/09) | ✅ |
| net.scheme/host/port/path/query/fragment + queryEncode/Decode | ✅ | ✅ | ✅ (NET001 closed 09/09) | ✅ |
| uuid.v4 | ✅ | ✅ | ✅ (SECN000 closed 09/09) | ✅ |
| uuid.isUuid (form 8-4-4-4-12; version/variant not checked) | ✅ | ✅ | ✅ (B25, UUID001 closed in the beta→main merge 10/09) | ✅ |
| math.sqrt (S1b — first Double; NaN at <0 = IEEE) | ✅ | ✅ | ✅ (B32 `fsqrt.d`; MATH001 closed 11/09) | ✅ |
| math.lerp/percentage/isInteger/isDecimal/roundTo (S1b.1/S1b.3 — pure SSE2, without libm) | ✅ | ✅ | ✅ (B32; MATH001 closed 11/09) | ✅ |
| math.pow (S1b.2 — libm `pow@PLT` + `-lm` on x86) | ✅ | ✅ | ❌ `MATH001` (static cross without libc) | ✅ |
| random.randomInt/randomBoolean/randomString (beta face S10a/b) | ✅ | ✅ | ✅ (B27/B28, getrandom/lemire) | ✅ |
| random.double/boolean/int/hex (main face S10) | ✅ | ✅ | ✅ (B27) | ✅ |
| `time.*` new faces (todayIso/addDays/diffDays/hoursBetween/iso parse-format/sleep/now/interval) | ✅ JVM (measured 19/09, `StdlibIdiomsCompileTest`); interpreter: dates ✅ (X8 parity), clock ⏳ | ✅ x86 (measured 19/09) | ✅ cross (B33; row 8 closed 25/09 — `KofTimeE2ETest` 44/44) | ✅ (measured 19/09) |
| `cache.*` / `config.*` / `log.*` (8.5) | ✅ JVM (measured 19/09); cache+config ✅ interpreter parity 19/09 (`KofScriptStdlibParityTest`); log ⏳ interpreter | ✅ x86 (measured 19/09) | ✅ cross (row 9 closed 26/09 — `KofCacheCrossTest`/`KofConfigCrossTest`/`NativeLogCrossTest`) | ✅ (measured 19/09) |
| `process.run`/`exit` (varargs) | ✅ | ❌ `PROC001` (compile-time, pinned `DomainGapCodesTest`) | ❌ `PROC001` | ✅ |
| `process.spawn` | ✅ | ❌ `PROC001` | ❌ `PROC001` | ✅ Kof JS host (`KofJsRunner`); bare node = honest diagnostic |
| `observability.*` (spans 01/09 + metrics/health 8.5 19/09) | ✅ (measured 19/09) | ✅ x86 (measured 19/09) | ✅ cross (row 7 closed 26/09 — `KofObservabilityTest` 12/12) | ✅ (measured 19/09) |
| `gpu.available`/`failReason`/`dispatchMatmul(Int)` | ✅ | ✅ (measured 19/09) | ✅ cross (row 6 closed 26/09 — honest CPU fallback) | ✅ JS/Script fallback (row 6; `GPU001` retired) |
| `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record/list` | ✅ (measured 19/09) | ❌ `MEDIA001` (compile-time) | ❌ `MEDIA001` | ❌ `MEDIA001` |
| shell.cmd/run/ok (v1) | ✅ | ❌ `PROC001` (compile-time) | ❌ `PROC001` | ✅ byte-parity |
| shell.pipeline (v1) | ✅ | ❌ `PROC001` | ❌ `PROC001` | ✅ Kof JS host (chain + pump, 20/09 `081a48f8`; bare node = honest diagnostic) |

`strings.reverse` on non-ASCII: byte-reverse on Native vs UTF-16 on JVM/JS —
gap **NAT-STR01** (parity only locked on ASCII in the matrix).

## Limitations

- `charAt(i)` returns the **code** of the char (Int), not a char literal — that is
  why `padLeft` receives pad as a String.
- `strings.*` predicates are **ASCII**: accents => false (decision locked in the
  `stdstrings` matrix, not a bug).
- `encoding.hexDecode`/`base64Decode` are **tolerant by specification**
  (same behavior on the 4 backends); if you need to reject invalid input,
  validate beforehand (`strings.isNumeric`/`isAlphaNumeric`).
