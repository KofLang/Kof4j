[English](36-security.md) | [Português](36-security.pt_BR.md)

# 36 — Security (kof.security)

> **Kof 0.5.0-beta — Sep 2026 — complete across the 3 targets (SECN00x gaps documented)**

`kof.security` is the Standard Library's security layer: passwords, cryptography,
JWT, secrets and authentication for web applications — with **secure by default**.

```kof
var hash = passwords.hash("hunter2")
println(passwords.verify("hunter2", hash))   // true
```

## Passwords

Never use `sha256(password)` to store a password. Use `passwords`:

```kof
var hash = passwords.hash("senha")            // pbkdf2$sha256$600000$salt$hash
var ok = passwords.verify("senha", hash)      // constant-time comparison
var rehash = passwords.needsRehash(hash)      // stale parameters?
```

The choice of algorithm/iterations/salt is automatic and secure. The hash
format is versioned — when the recommended parameters change,
`needsRehash` returns `true` and the application can re-hash.

## Crypto

```kof
var digest = crypto.sha256("kof")             // hex
var mac = crypto.hmacSha256(key, data)        // hex
var key = crypto.randomHex(32)                // secure 32 bytes
var ct = crypto.encryptAesGcm("segredo", key) // aesgcm$iv$ct
var pt = crypto.decryptAesGcm(ct, key)        // fails on tamper
```

Target gaps are clear compilation errors (`SECN00x`), never
silent behavior.

## JWT

```kof
var token = jwt.create("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", secret)
var claims = jwt.verify(token, secret)
var claims2 = jwt.verify(token, secret, "kof", "api")   // iss + aud
```

The algorithm is fixed (HS256) — **never accepted from the token** (no
algorithm confusion). `exp`, `iss` and `aud` are validated. The default secret
comes from `KOF_JWT_SECRET` (`jwt.secret()`).

## Secrets

```kof
var apiKey = secrets.get("API_KEY")           // environment variable
var apiKey = secrets.get("API_KEY", "dev")    // with fallback
var logLine = secrets.redact(token)           // never leak secrets into logs
```

For code you write today, prefer the typed `Secret` — it is redacted by
construction and only `reveal()` yields the raw text:

```kof
var key = secrets.of("sk-live-...")           // or secrets.secret("API_KEY")
println(key)                                   // Secret(*** )
var raw = key.reveal()                         // the single raw export
var fromBytes = secrets.fromBytes(payload)     // non-text bytes, per-byte
```

`json.encode(key)` is redacted at runtime (`"Secret(*** )"`), and feeding
`reveal()` straight into `log.*`/`json.encode` raises the `SECN009` warning.
`secrets.secret(name)` with an unset/blank environment variable is an explicit,
catchable error (`catch (String e)` names `SECN015`) — never a silent empty
`Secret`; the legacy `secrets.get(name)` still returns the raw `String`.
Keys are handled without ever reading them through a `KeyHandle`:

```kof
val kh = secrets.keyFromHex(hex)               // or keyFromPem(path) / keyFromKeystore(path, alias, pwd)
val tag = crypto.hmacSha256(kh, message)
val kh2 = kh.rotate()                          // kh is revoked; reusing it is SECN010
```

`Secret`/`KeyHandle` are JVM-first: other targets reject them at compile time
with `SECN008` (never a silent stub).

## Session keys — X25519 + HKDF (D-KOF-X25519)

```kof
var mine = keyExchange.privateKey()            // Secret — scalar never printable
var myPub = keyExchange.publicKey(mine)        // 64-hex String — safe to send
var shared = keyExchange.shared(mine, secrets.of(peerPub))  // Secret
var rxKey = keyExchange.hkdfSha256(shared, saltHex, "0001", 32)   // per-direction AES key (hex)
```

WHY: a session key is NEVER hand-assembled and NEVER travels as a raw
String — private material lives in `Secret` (R8), the public value is the
only export. Wrong actual on the Secret face = `SECN014`; the face runs on
JVM/Android/Script today, JS/Native/cross refuse with `SECN012` until the
port (never silent). RFC 7748 + RFC 5869 (case-1 golden in
`KeyExchangeE2ETest`).

## Web auth (middleware)

```kof
var app = web.app()
auth.secret("s3cret")
app.use {
    if (!auth.authenticated()) {
        return "{\"error\":\"unauthorized\"}"
    }
    if (!auth.hasRole("admin")) {
        return "{\"error\":\"forbidden\"}"
    }
    return null
}
app.get("/admin") { return "admin area" }
app.listen(8080)
```

## Constant-time comparison

```kof
if (security.constantTimeEquals(a, b)) { ... }
```

Use it to compare tokens, hashes and secrets — never `==` on
sensitive values.

## Support by target

`kof.security` is complete across the 3 targets — Native in pure asm (no libc),
values identical to the JVM (FIPS 180-4 / RFC 2104):

| Area | JVM | Native | JS |
|------|-----|--------|----|
| passwords (PBKDF2-HMAC-SHA256, 600k) | ✅ | ✅ (asm) | ✅ (delegation to the platform) |
| sha256 / hmacSha256 | ✅ | ✅ (asm) | ✅ (pure JS) |
| sha512 | ✅ | ✅ (asm) | ✅ (pure JS) |
| aes-gcm | ✅ | ✅ (asm) | ✅ (pure JS) |
| jwt HS256 (sig/exp/iss/aud) | ✅ | ✅ (asm) | ✅ |
| secrets (env) | ✅ | ✅ (`/proc/self/environ`) | ✅ |
| constant-time / redact | ✅ | ✅ | ✅ |
| rateLimit / session / apiKey (G9) | ✅ | ✅ | ✅ |
| auth web (`auth.*`, Bearer JWT) | ✅ | ❌ | ❌ |
| csrf / cors / security headers | ✅ | ❌ | ❌ |

Remaining gaps (`SECN00x`, compile-time diagnosis): the web context of
auth/headers (JVM only — the web server is JVM, `WEB002` on Native). AES-GCM on
JS (`SECN002`) and JWT (`SECN004`) closed. Tests: `KofSecurityTest` (27; unit +
E2E across the 3 targets + adversarial). Full reference: `docs/stdlib/security.md`.
