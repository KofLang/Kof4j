[English](39-stdlib.md) | [Português](39-stdlib.pt_BR.md)

# 39 — Universal Standard Library

> **Every namespace now has its own chapter under [`stdlib/`](stdlib/README.md)
> — the function tables there are the 1:1 contract (measured from the real
> dispatchers). This chapter keeps the narrative, the examples and the honest
> parity table.**

| Namespace | Chapter |
|-----------|---------|
| `json` | [stdlib/json](stdlib/json.md) |
| `db` | [stdlib/db](stdlib/db.md) |
| `http` | [stdlib/http](stdlib/http.md) |
| `cache` | [stdlib/cache](stdlib/cache.md) |
| `config` | [stdlib/config](stdlib/config.md) |
| `log` | [stdlib/log](stdlib/log.md) |
| `process` | [stdlib/process](stdlib/process.md) |
| `shell` | [stdlib/shell](stdlib/shell.md) |
| `ssh` | [stdlib/ssh](stdlib/ssh.md) |
| `net` | [stdlib/net](stdlib/net.md) |
| `orm` | [stdlib/orm](stdlib/orm.md) |
| `gpu` | [stdlib/gpu](stdlib/gpu.md) |
| `mq` | [stdlib/mq](stdlib/mq.md) |
| `observability` | [stdlib/observability](stdlib/observability.md) |
| `tetris` | [stdlib/tetris](stdlib/tetris.md) |
| `media` | [stdlib/media](stdlib/media.md) |
| `buffer` | [stdlib/buffer](stdlib/buffer.md) |
| `passwords` | [stdlib/passwords](stdlib/passwords.md) |
| `crypto` | [stdlib/crypto](stdlib/crypto.md) |
| `jwt` | [stdlib/jwt](stdlib/jwt.md) |
| `secrets` | [stdlib/secrets](stdlib/secrets.md) |
| `security` | [stdlib/security](stdlib/security.md) |
| `auth` | [stdlib/auth](stdlib/auth.md) |
| `math` | [stdlib/math](stdlib/math.md) |
| `strings` | [stdlib/strings](stdlib/strings.md) |
| `encoding` | [stdlib/encoding](stdlib/encoding.md) |
| `uuid` | [stdlib/uuid](stdlib/uuid.md) |
| `time` | [stdlib/time](stdlib/time.md) |
| `random` | [stdlib/random](stdlib/random.md) |
| `rng` | [stdlib/rng](stdlib/rng.md) |
| `validation` | [stdlib/validation](stdlib/validation.md) |

## math — arithmetic that every program repeats

```kof
math.abs(-5)          // 5
math.sign(-5)         // -1
math.clamp(99, 0, 10) // 10   — clamps to the interval [min,max]
math.min(3, 7)        // 3
math.max(3, 7)        // 7
math.isEven(4)        // true
math.isOdd(4)         // false
math.isPositive(4)    // true   (0 is not positive)
math.isNegative(4)    // false
math.isZero(0)        // true
math.sqrt(16.0)       // 4.0  — Double FIRST (S1b); -1.0 => NaN
math.lerp(0.0, 10.0, 0.5)     // 5.0  — a + (b - a) * t   (S1b.1)
math.percentage(3.0, 4.0)     // 75.0 — total 0 => NaN, never throws (S1b.1)
math.isInteger(4.0)           // true;  4.5/NaN/Inf => false (S1b.1)
math.isDecimal(4.5)           // true;  !isInteger (S1b.1)
math.roundTo(3.14159, 2)      // 3.14  — half-away-from-zero to N decimals (S1b.3)
math.roundTo(2.675, 2)        // 2.68  — arithmetic scaling (see note below)
math.roundTo(1234.0, -2)      // 1200.0 — negative decimals round to tens/hundreds
math.pow(2.0, 10.0)           // 1024.0 — libm on native x86 (S1b.2)
math.parseInt("42")           // 42     — JDK contract, throws on invalid (S13a)
math.parseDouble("2.5")       // 2.5
math.parseIntOrDefault("x", 0) // 0     — never throws (S13b)
```

Integers are above; `sqrt`/`lerp`/`percentage`/`isInteger`/`isDecimal`/
`roundTo`/`pow` are the `Double` ones in the namespace (JVM/Script/JS/x86; riscv64/aarch64
run all of these except `pow`, which is `MATH001` — libm cannot link on the static cross).
The arguments are **explicit Doubles** — `math.lerp(0, 10,
0.5)` does not compile (SEM025; no silent widening). `roundTo(value, decimals)`
takes an `Int` `decimals` and uses **deterministic decimal scaling** (not
decimal-string `BigDecimal`): `roundTo(2.675, 2)` is `2.68` because the nearest
double `2.675` scales to `267.5`.

## strings — predicates, converters and words

Predicates (all `String -> Bool`; `""`/`null` are `false`):

```kof
strings.isAlpha("ab")          // true   — only [A-Za-z] (ASCII)
strings.isNumeric("12")        // true   — only [0-9]
strings.isAlphaNumeric("a1")   // true
strings.isAscii("ab")          // true   — bytes < 128
strings.isUpperCase("AB")      // true   — has an uppercase letter; others ignored
strings.isLowerCase("ab")      // true
strings.count("aXaXa", "X")    // 2      — non-overlapping occurrences
```

Converters and padders:

```kof
strings.capitalize("abc")      // "Abc"
strings.reverse("abc")         // "cba"
strings.repeat("ab", 2)        // "abab"
strings.truncate("abcdef", 3)  // "abc"
strings.padLeft("7", 3, "0")   // "007"   — pad is String; uses the 1st char
strings.padRight("7", 3, "0")  // "700"
```

**Word** converters — the boundary is the one from the project's own
HTTP/XML parser (a camel hump of uppercase letters becomes a boundary), not `split(" ")`:

```kof
strings.toCamelCase("hello_world")  // "helloWorld"
strings.toPascalCase("hello world") // "HelloWorld"
strings.toSnakeCase("HTTPServer")   // "http_server"
strings.toKebabCase("helloWorld")   // "hello-world"
strings.slugify("Hello, World!! 42")// "hello-world-42"
```

HTML escape (5 entities — never build a page with raw interpolation):

```kof
strings.escapeHtml("a<b>&\"'c")   // "a&lt;b&gt;&amp;&quot;&#39;c"

`strings.escapeJson(s)` escapes the body of a JSON string literal
(RFC 8259): `\` becomes `\\`, `"` becomes `\"`, control chars become `\b \f \n \r \t`
or `\u00xx` (lowercase hex); other bytes (incl. UTF-8) are copied. On the 4 targets.

strings.escapeHtml("Café & ç")     // "Café &amp; ç" (>=128 is copied)
strings.unescapeHtml("a&amp;b")         // "a&b" — 5 named + &#DDD;/&#xHH; (UTF-8)
```

Whitespace (WS = tab/LF/VT/FF/CR/space; >=128 is **not** WS):

```kof
strings.removeWhitespace("  a\tb\nc  ")   // "abc"
strings.normalizeWhitespace("  a   b  ") // "a b" — trim + collapse to 1 space
```

> ⚠️ `capitalize` and the **word** converters are **ASCII** on the 4 targets
> (measured): only `a-z` → `A-Z`; any byte `>= 128` is **preserved** but
> **never capitalized** (`"café"` → `"Café"`, but `"ção"` → `"ção"`, not
> `"Ção"`), and in the word converters a byte `>= 128` acts as a **delimiter**
> (`"Ünïcödé café"` → `"n_c_d_caf"`). Real UTF-8 (capitalize/delimit
> by Unicode code point) on the natives is the gap `NAT-STR01` (open).



## encoding — bytes that every external API requires

Everything operates **by UTF-8 bytes** (same output on the 4 targets; Kof's JS is not
JavaScript — it does not depend on `TextEncoder`).

```kof
encoding.hexEncode("Hi")          // "4869"
encoding.hexDecode("4869")        // "Hi"
encoding.base64Encode("Man")      // "TWFu"
encoding.base64Decode("TWFu")     // "Man"
encoding.base64UrlEncode("a?b")   // "YT9i"        — no padding, -_ alphabet
encoding.base64UrlDecode("YT9i")  // "a?b"
encoding.urlEncode("a b")         // "a%20b"       — NOT '+' (RFC 3986)
encoding.urlDecode("a%20b")       // "a b"
```

The **decoders are tolerant by spec** (non-digits in hex become `0`
in that nibble; invalid ones in base64 are skipped; `'%'` without 2 hex digits pass
through literally). This is a decision locked in the matrix — not a "hack".

## uuid — v4 in RFC 4122 format

```kof
var id = uuid.v4()
println(id.length)              // 36
// 8-4-4-4-12, digit 13 == '4', 19th ∈ {8,9,a,b}

uuid.isUuid(id)                 // true  — validates the SHAPE
uuid.isUuid("550e8400-e29b-41d4-a716-446655440000")  // true
uuid.isUuid("não-é-uuid")       // false
```

`v4()` is nondeterministic by nature: the tests lock **shape**, not
equality. `isUuid` is the inverse: a pure shape predicate (36 chars, dashes
at 8/13/18/23, lowercase or uppercase hex) — it does not check version/variant. It runs
on JVM/Script/JS and on Native x86/riscv64/aarch64 (`UUID001` closed 09/09 — slice B +
translator; `getrandom(2)` under qemu, `KofUuidTest.uuidV4CrossArch`/`isUuidCrossArch`).

## uuid — v7 (S3b.2, RFC 9562)

```kof
var id = uuid.v7()          // time-sortable: 48 bits of unix-ts-ms at the start
println(id.charAt(14))      // '7' (version)
```

Same 8-4-4-4-12 shape as v4, but the first 12 hex digits encode the clock
(epoch-ms big-endian), so v7's generated in sequence are sortable.
Variant 10xx (19th ∈ {8,9,a,b}) as in v4. Entropy only from the OS (SecureRandom /
getrandom / crypto — R11).

## uuid — isUuid (S3b-ext)

```kof
uuid.isUuid("550e8400-e29b-41d4-a716-446655440000")   // true
uuid.isUuid("550e8400e29b41d4a716446655440000")        // false (no dashes)
uuid.isUuid(uuid.v4())                                  // true (parity)
```

Validates the RFC 4122 **shape**: 36 chars, fixed hyphens at 8/13/18/23, the rest
hex (uppercase or lowercase). It does **not** check version/variant — any
canonical v1..v5 is `true`; entropy is `v4()`'s job.

## time — isWeekend (S7-ext)

```kof
time.isWeekend(2026, 9, 12)   // true  — Saturday
time.isWeekend(2026, 9, 9)    // false — Wednesday
time.isWeekend(2026, 2, 30)   // false — invalid date (dayOfWeek => 0)
```

`dayOfWeek(y,m,d) >= 6` (ISO 1=Monday..7=Sunday). A pure wrapper on the 5 targets —
it reuses the calendar machine; an invalid date falls into `false` automatically.

## validation — formatCpf / formatCep (S12)

```kof
validation.formatCpf("52998224725")    // "529.982.247-25"
validation.formatCpf("123")            // "123" (does not match => original)
validation.formatCep("01310100")       // "01310-100"
validation.formatCnpj("34546401000163") // "34.546.401/0001-63"
```

BR punctuation: 11 digits => `DDD.DDD.DDD-DD` (CPF); 8 => `DDDDD-DDDD` (CEP);
14 => `NN.NNN.NNN/NNNN-NN` (CNPJ). Outside that
(wrong number of digits, null) => **original** — a lenient face, never throws.
Formats **without validating** (any digits; validating is the job of `isCpf`/
`isCep`). 5 targets; reuses the same `brDigits` as the predicates.

## strings — uncapitalize (S11)

```kof
strings.uncapitalize("Hello World")   // "hello World"
strings.uncapitalize("HELLO")         // "hELLO"
strings.uncapitalize("1abc")          // "1abc" (1st byte outside A-Z => original)
```

Mirror of `capitalize`: only the 1st byte; `A-Z` -> `a-z`; null/`""`/outside-A-Z
=> original. ASCII on the 5 targets (parity with the S2b rule of capitalize).

## random — draw with OS entropy (S10a/b)

```kof
var n = random.randomInt(100)          // 0..99 (bound<=0 -> 0, lenient face)
var coin = random.randomBoolean()      // 0 or 1
var token = random.randomString(8, "0123456789abcdef")  // 8 chars from the alphabet
var salt = random.randomBytesHex(16)   // 32 lowercase hex (alias of random.hex — DD-STDLIB-01)
// list choice = idiom, not a function:
var l = listOf("a", "b", "c")
var pick = l[random.randomInt(l.size)]
```

Entropy ALWAYS comes from the OS primitive (getrandom / SecureRandom /
crypto) — no homemade PRNG. For security tokens use `security.*`
(randomHex/randomInt with strict validation); `random.*` is the
draw/shuffle/test face. Nondeterministic: the tests lock the **contract**
(range + edges), not equality.

## rng — seedable deterministic PRNG (X8 slices 1–2, property-based testing)

```kof
rng.seed(42)                           // reset: same seed => same sequence, ANY backend
var v = rng.int(1000)                  // [0,1000); bound<=0 -> 0 (lenient)
var b = rng.boolean()                  // true/false
var d = rng.double()                   // [0,1), 52-bit mantissa, never 1.0
var s = rng.string(8, "abc")           // 8 chars from the alphabet; n<=0/empty -> ""
// property-based test idiom (test harness):
test "addition commutes" {
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

Same seed => SAME sequence on JVM and JS (xorshift128 + splitmix32,
32-bit-exact math — parity proven byte-for-byte by `KofRngTest.jvmJsParity`).
NEVER for keys/tokens/secret material: that is `random`/`security`
(OS entropy, R11). Slice 1 = JVM + JS; NATIVE/ANDROID fail at compile time
with the honest gap `RNG001` (x86_64 asm landed in slice 2 — byte-identical to JVM). int(bound) uses modulo
(tiny documented bias) — the contract is deterministic parity, not
cryptographic uniformity.


## validation — BR documents, network and card

```kof
validation.isCpf("52996581504")          // true  — digits extracted
validation.isCpf("529.982.247-25")       // true  — punctuation ignored
validation.isCnpj("34546401000163")      // true  // CPF/CNPJ: mod-11 with
validation.isCep("01310-100")            // true  //   check digit
validation.isPis("12345678900")          // true
validation.isNis("12056412278")          // true — NIS reuses the PIS mod-11

validation.isIpv4("192.168.0.1")         // true  — dotted-quad
validation.isIpv4("01.2.3.4")            // false — leading zero is not allowed
validation.isMac("00:1A:2B:3C:4D:5E")    // true  — ':' or '-', consistent
validation.isPort(443)                   // true  — 1..65535
validation.isCreditCard("4532 0151 1283 0366") // true — Luhn, 12..19 digits
validation.isIpv6("fe80::1")                 // true  — RFC 5952 subset
validation.isIpv6("::ffff:192.168.0.1")      // false — mixed form not in v1
validation.isDomain("sub.example.co.uk")     // true  — RFC 1123 labels
validation.isDomain("localhost")             // false — requires >=2 labels
validation.isDomain("xn--mnchen-3ya.de")     // true  — punycode (it is ASCII)
```

> What is **not** content validation: `validation.min/max` (already existed,
> G4) compare numbers; the predicates above check format + checksum.

## time — civil calendar (no clock, no timezone)

```kof
time.isLeapYear(2024)                 // true   — Gregorian (%4, %100, %400)
time.daysInMonth(2024, 2)             // 29
time.dayOfWeek(2026, 9, 9)            // 3      — ISO: 1=Monday..7=Sunday
time.daysBetween(2024, 1, 1, 2024, 3, 1) // 60  — can be negative
time.addDays("2024-02-28", 1)         // "2024-02-29" — ISO date (String)
time.diffDays("2024-01-01", "2024-03-01") // 60 — can be negative
```

Domain **1..9999** (the civil serial fits in Int; outside that or a nonexistent
date → `isLeapYear=false` / `0`). The clock functions `now`/`sleep`/`interval` are
from `time` since before — the calendar above is the pure, deterministic part.

`addDays`/`diffDays` accept an **ISO date in String** (`YYYY-MM-DD`); invalid
→ `""` (add) / `0` (diff). Available on **JVM, Script** (via `java.time`) and
**JS** (same civil calendar algorithm, no `Date` => byte-identical parity);
on native **riscv64/aarch64** it is an honest gap `TIME002` (clear error at compile, never — x86 CLOSED S7c: RuntimeTimeIso asm)
silent fallback) — the port to asm is the next step (same order as the
native port of `net`, `NET001`).

## net — URL components (S8)

`net` decomposes a URL into 6 fields, one per function — the same String goes in, one
piece comes out:

```kof
val url = "https://user:pw@koflang.dev:8443/docs/intro?page=2#sintaxe"
net.scheme(url)     // "https"
net.host(url)       // "koflang.dev"   (userinfo and port removed)
net.port(url)       // "8443"          (String — parsing is left to the caller)
net.path(url)       // "/docs/intro"
net.query(url)      // "page=2"
net.fragment(url)   // "sintaxe"
net.queryEncode("a b&c=1")          // "a%20b%26c%3D1"
net.queryDecode("a%20b%26c%3D1")    // "a b&c=1"
```

An absent field returns `""` (never throws, never a surprise `null` — `null` only
arrives if the input is `null`). It is the v1 subset of RFC 3986: IPv6 with brackets
(`http://[::1]:8080`) is not yet recognized (the host comes out truncated) — port and
mixed form are left for v2, documented. On the natives (x86/riscv/aarch) the
namespace runs on the 4 targets (riscv/aarch port closed 09/09 — same span
machine on the 3 natives).

## Parity by target (honest table)

| API | JVM / Script | Native x86_64 | Native riscv64 / aarch64 | JS |
|---|---|---|---|---|
| `math.*`, `strings.is*/count/capitalize/reverse/repeat/truncate/pad*/escapeHtml`, `toCamel/Pascal/Snake/Kebab/slugify`, `encoding.hex*/url*`, `time.isLeapYear/daysInMonth/dayOfWeek/daysBetween`, `validation.isCpf/isCnpj/isCep/isPis/isNis/isIpv4/isIpv6/isMac/isPort/isCreditCard/isDomain` | ✅ | ✅ | ✅ | ✅ |
| `encoding.base64*` / `base64Url*` | ✅ | ✅ | ✅ | ✅ |
| `net.*` (S8) | ✅ | ✅ | ✅ | ✅ |
| `uuid.v4` / `uuid.v7` | ✅ | ✅ | ✅ | ✅ |
| `random.randomInt/randomBoolean/randomString` (beta face S10a/b) | ✅ | ✅ | ✅ | ✅ |
| `random.double/boolean/int/hex` (main face S10) | ✅ | ✅ | ✅ (B27) | ✅ |
| `uuid.isUuid` (S3b.1, shape predicate) | ✅ | ✅ | ✅ (B25) | ✅ |
| `time.addDays` / `time.diffDays` (S7a/b/c, ISO date) | ✅ | ✅ | `TIME002` | ✅ |

Gate = compilation error **with a code** (R6 — never a silent stub):
`strings.toCamelCase` and the word converters reached the 4 targets only
on 09/09 (gap `STRN001` closed with the riscv port + byte-by-byte diff on
qemu). While a target is gated, the compiler says **what** and **which
gap**, and the program does not become wrong behavior.

How to check it yourself: each row of the table mirrors a program in
`ConformanceMatrixTest` (cases `stdmath`, `stdstrings*`, `stdenc`,
`stdvalidation*`, `stdluhn`, `stdtime`) — same expected output on the 4 targets,
actually executed (riscv/aarch64 under qemu).

## Next step

- `training/idioms/stdlib.md` — BAD/GOOD/WHY for each namespace.
- `docs/stdlib/stdlib.md` §3 — the reference matrix with gates.
- `docs/stdlib/PLAN-STDLIB-EXPANSION.md` — what is missing: `random` (P0),
  `last4`/`creditCardBrand` (brand table = trademark — evaluate
  first) and `math` Double (FLT).
