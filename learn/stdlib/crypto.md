[English](crypto.md) | [Português](crypto.pt_BR.md)

# kof.security.crypto — hashes, AEAD and randomness

> **Status: JVM ✅ (audited primitives) · key-handles and raw-key forms ·
> cross faces in the parity ledger (row 14).**

| Function | Form |
|----------|------|
| `sha256` | `sha256(String s) -> String` |
| `sha256Bytes` | `sha256Bytes(Byte[] data) -> String` |
| `sha512` | `sha512(String s) -> String` |
| `hmacSha256` | `hmacSha256(String key, String msg) -> String` · `hmacSha256(KeyHandle key, String msg) -> String` |
| `hmacSha256Bytes` | `hmacSha256Bytes(Byte[] key, Byte[] msg) -> String` |
| `encryptAesGcm` | `encryptAesGcm(String plain, String keyHex64) -> String` · `(plain, KeyHandle) -> String` |
| `decryptAesGcm` | `decryptAesGcm(String cipher, String keyHex64) -> String` · `(cipher, KeyHandle) -> String` |
| `encryptChacha20` | `encryptChacha20(String plain, String keyHex) -> String` · `(plain, KeyHandle) -> String` |
| `decryptChacha20` | `decryptChacha20(String cipher, String keyHex) -> String` · `(cipher, KeyHandle) -> String` |
| `randomHex` | `randomHex(Int n) -> String` |
| `randomInt` | `randomInt(Int max) -> Int` |

```kf
var sum = crypto.sha256(payload)
var bin = crypto.sha256Bytes(payloadBytes)
var k = secrets.keyFromHex(keyHex)
var box = crypto.encryptAesGcm("segredo", k)   // AEAD: confidentiality + integrity
var out = crypto.decryptAesGcm(box, k)
```

- AEAD only (AES-GCM, ChaCha20-Poly1305) — unauthenticated modes do not
  exist on the surface.
- Prefer `KeyHandle` (from `keyFromHex`/`keyFromPem`/`keyFromKeystore`) over
  raw key strings — the handle is wipeable and never logs.

**See also:** [kof.secrets](secrets.md) — handles and redaction.
