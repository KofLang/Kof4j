[English](security.md) | [Português](security.pt_BR.md)

# Idioms — SECURITY (passwords / crypto / jwt / secrets / session / auth)

> 8.5 (plano universal, fatia 2, 19/09). Every shape below was compiled
> (`StdlibIdiomsCompileTest`) and the AES-GCM round-trip RUNS on the JVM with the
> pinned golden before this line entered the corpus. Deep reference:
> `training/language/security.md`; tutorial: `learn/36-security.md`; the example
> that executes in CI: `training/examples/security.kf`.

## Passwords — never hash a password by hand

```kof
// ❌ BAD — a digest is NOT a password hash (fast, unsalted)
var stored = crypto.sha256(password)

// ✅ GOOD — versioned format, salted, constant-time verify
var stored = passwords.hash(password)
if (passwords.verify(input, stored)) { login(user) }
if (passwords.needsRehash(stored)) { passwords.hash(input) }  // params moved: re-hash on login
```

WHY: `hash` owns the algorithm/parameters/version (R10: format is versioned);
`verify` is constant-time for you; `needsRehash` is the upgrade path — never a
migration script.

## Crypto — digests, HMAC, and ONE rule for comparison

```kof
var digest = crypto.sha256(body)
var mac = crypto.hmacSha256(key, body)            // (key, msg) — key first
if (security.constantTimeEquals(mac, received)) { accept() }   // NEVER ==
var nonce = crypto.randomHex(16)                  // n BYTES -> 2n hex chars
```

WHY: comparing secrets with `==` is a timing channel — the language has
`constantTimeEquals` precisely so this comparison is never hand-rolled.
Crypto is never homemade (R11): these bind to JCA on the JVM.

RULE: digests take Strings or Ints — NEVER a byte array.
`crypto.sha256(new Byte[n])` / `crypto.hmacSha256(key, byteArr)` refuses
at compile time with `SECN011` on every target (§563: the form used to
degrade per target — Script digested the array IDENTITY, the JVM died
`VerifyError` at load). For BINARY payloads the language has a dedicated face —
`crypto.sha256Bytes(Byte[])` / `crypto.hmacSha256Bytes(Byte[] key, Byte[] msg)`
(D-KOF-DIGEST-BYTES; JVM/Android/Script/x86-Native today; JS and riscv64/aarch64
refuse with the SECN000 gap until ported). Pinning bytes to a deterministic
hex-String AAD stays legal where the face is unavailable.

## Session keys — X25519 + HKDF, never hand-assembled

```kof
var mine = keyExchange.privateKey()                      // Secret (R8: never printable)
var myPub = keyExchange.publicKey(mine)                  // 64-hex String — send it
var shared = keyExchange.shared(mine, secrets.of(peerPub))
var txKey = keyExchange.hkdfSha256(shared, saltHex, "0001", 32)  // AES key per direction
```

D-KOF-X25519: private material ONLY moves inside `Secret` (wrong actual =
`SECN014`); the public value is the sole export. JVM/Android/Script real
(RFC 7748 + RFC 5869); JS/Native/cross refuse `SECN012` until ported.
Derive per-direction keys with HKDF over the shared secret — never reuse
one raw scalar as an app key.

## Encryption — AES-GCM; args are `(plain, keyHex)`, key = 32 bytes

```kof
var key = crypto.randomHex(32)                    // 32 bytes = 64 hex chars — enforced at runtime
var ct = crypto.encryptAesGcm("segredo", key)     // (plaintext, keyHex64)
var pt = crypto.decryptAesGcm(ct, key)
```

Honest gates (measured 19/09): AES-GCM = JVM/JS/Native x86 ✅ (riscv/aarch golden
not measured yet); **ChaCha20 on Native = `SECN002`** — the shape exists, the
target says no at compile-time (R6, never a silent weaker fallback).

## JWT — HS256 fixed, verify returns the claims

```kof
var claims = "{\"sub\":\"u1\",\"iss\":\"kof\",\"aud\":\"api\"}"
var token = jwt.create(claims, secret)
var withTtl = jwt.create(claims, secret, 3600)
var checked = jwt.verify(token, secret, "kof", "api")   // sig + exp + iss + aud
var fresh = jwt.secret()                               // generate a strong secret
```

WHY: the 4-arg `verify` validates issuer/audience **inside** the platform —
parsing the payload by hand (split/decode base64) is the anti-pattern.

## Secrets — env with fallback, redact before logs

```kof
var apiKey = secrets.get("API_KEY")                  // env
var port = secrets.get("PORT", "8080")               // with fallback
log.info("config at " + secrets.redact(apiKey))      // "sk-a********mnop" — never leak
```

WHY: a secret that reaches a log is already exposed; `redact` keeps the shape
(first/last chars) for debugging without the value.

## `Secret` — the value that cannot leak (D-SECRETS face 1)

```kof
var key = secrets.of("sk-live-...")          // or secrets.secret("API_KEY") — env by name
println(key)                                  // Secret(*** )   — redacted, always
log.info("key=" + key)                        // Secret(*** )   — concat cannot leak either
if (key == secrets.secret("API_KEY")) { }     // constant-time content equality
var raw = key.reveal()                        // the ONLY raw export — greppable in an audit
```

WHY: `secrets.get` returns a raw `String` that flows into `println`, concat,
JSON and logs invisibly. `Secret` makes the safe path the default — printing is
redacted by construction, and the one way to the raw value is a single word
(`reveal()`) you can grep for in code review. Prefer `secrets.secret`/`of` over
`get` for new code; `get` stays for the legacy raw path. JVM-first (R7): JS/
Native/Script/Android reject it at compile time with `SECN008` (never a silent
stub). Do NOT `reveal()` to compare or to log — `==` is already constant-time.

Redaction is enforced on JVM (D-SECRETS P2): `json.encode(key)` yields
`"Secret(*** )"` (never the fields), and `reveal()` fed straight into
`log.*`/`json.encode` raises the `SECN009` warning — the unmasking is
deliberate, so it must be visible. Non-text bytes go through
`secrets.fromBytes(u8)` (per-byte, lossless).

## `KeyHandle` — the key you never read (D-SECRETS P3)

```kof
val kh = secrets.keyFromHex(hexKey)                  // or keyFromPem(path)/keyFromKeystore(path, alias, pwd)
val tag = crypto.hmacSha256(kh, message)             // keyed overload — no raw key in code
val token = jwt.create(claims, kh)
val kh2 = kh.rotate()                                // fresh handle; kh is now revoked
// crypto.hmacSha256(kh, msg)                        // SECN010 at runtime — rotated handle cannot be reused
```

WHY: passing raw key hex around leaks it into signatures, logs and diffs. A
`KeyHandle` carries the material but never exposes it; `rotate()` invalidates the
old handle so a stolen reference can no longer sign. JVM-first (R7): JS/Native/
Script/Android reject it at compile time with `SECN008` (never a silent stub).

## Sessions, CSRF, rate-limit, headers

```kof
var sid = security.sessionCreate(user)               // payload in, id out
var who = security.sessionGet(sid)
security.sessionDestroy(sid)

var tok = security.csrfToken()
if (!security.csrfValid(tok)) { return "forbidden" }

if (!security.rateLimit("ip:" + addr, 10, 60)) { return "too many" }  // key, limit, window

var plain = security.cookieSet("sid", sid)            // Set-Cookie header string
var hardened = security.cookieSet("sid", sid, mapOf("HttpOnly", "true"))

println(security.cspHeader() + security.hstsHeader() + security.frameHeader()
        + security.contentTypeOptionsHeader() + security.referrerHeader())
if (security.corsAllowed(origin, "https://app.kof")) { }
```

Honest gates (measured 19/09): sessions / rateLimit / secrets / constantTimeEquals /
`apiKeyGenerate`+`apiKeyValid` = JVM+JS+Native x86 ✅; **`csrf*` and the header
builders = `SECN000` on JS and Native** (compile-time, R6); riscv/aarch golden not
measured. SCRIPT: the compiler emits nothing by design (`COMP003`); the interpreter
face is measured for `cache/net/config` only — security faces ⏳.

## auth.* — web middleware context, not a standalone call

`auth.authenticated()`, `auth.hasRole("admin")`, `auth.user()`, `auth.claims()`,
`auth.token()`, `auth.secret(s)`, `auth.resourceServer(...)` resolve **inside a
`kof.web` handler/`app.use`** (JVM web face). Loose `auth.x()` outside that
context is `SEM025/SEM002` — that is correct, not a gap: identity comes from the
request, and a call with no request has no answer. Full pattern:
`learn/36-security.md` §"Web auth".

## Never (R11)

No hand-rolled crypto, no `==` on secrets, no unsalted digests for passwords, no
secret literals in source, no base64-decoding a JWT by hand. Audited libraries
underneath; the surface stays small on purpose (6 namespaces, all locked
behavior-a-dispatcher in `StdCatalogSignaturesTest`).
