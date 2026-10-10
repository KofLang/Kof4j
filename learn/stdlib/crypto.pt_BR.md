[English](crypto.md) | [Português](crypto.pt_BR.md)

# kof.security.crypto — hashes, AEAD e aleatoriedade

> **Status: JVM ✅ (primitivas auditadas) · formas com key-handle e chave
> crua · faces cross no ledger de paridade (linha 14).**

| Função | Forma |
|--------|-------|
| `sha256` | `sha256(String s) -> String` |
| `sha256Bytes` | `sha256Bytes(Byte[] dados) -> String` |
| `sha512` | `sha512(String s) -> String` |
| `hmacSha256` | `hmacSha256(String key, String msg) -> String` · `hmacSha256(KeyHandle key, String msg) -> String` |
| `hmacSha256Bytes` | `hmacSha256Bytes(Byte[] chave, Byte[] msg) -> String` |
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
var box = crypto.encryptAesGcm("segredo", k)   // AEAD: confidencialidade + integridade
var out = crypto.decryptAesGcm(box, k)
```

- Só AEAD (AES-GCM, ChaCha20-Poly1305) — modos não autenticados não existem
  na superfície.
- Prefira `KeyHandle` (de `keyFromHex`/`keyFromPem`/`keyFromKeystore`) a
  strings de chave cruas — o handle é apagável e nunca vai para log.

**Veja também:** [kof.secrets](secrets.pt_BR.md) — handles e redação.
