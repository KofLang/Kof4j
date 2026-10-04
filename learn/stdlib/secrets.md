[English](secrets.md) | [Português](secrets.pt_BR.md)

# kof.secrets — secrets as values you cannot leak

> **Status: JVM ✅ · `Secret`/`KeyHandle` types · Android face via JCA
> (`§278`) · cross faces in the parity ledger (row 14).**

| Function | Form |
|----------|------|
| `get` | `get(String key) -> String` · `get(String key, String d) -> String` |
| `redact` | `redact(String s) -> String` |
| `of` | `of(String literal) -> Secret` |
| `secret` | `secret(String name) -> Secret` |
| `fromBytes` | `fromBytes(Int[] bytes) -> Secret` |
| `keyFromHex` | `keyFromHex(String hex) -> KeyHandle` |
| `keyFromPem` | `keyFromPem(String path) -> KeyHandle` |
| `keyFromKeystore` | `keyFromKeystore(String path, String alias, String password) -> KeyHandle` |

```kf
var token = secrets.of(rawToken)          // Secret — println shows [REDACTED]
var dbPass = secrets.get("db_password")   // env var "db_password" verbatim (raw getenv)
var k = secrets.keyFromPem("key.pem")     // KeyHandle for crypto.*
log.info("token=" + redact(header))      // explicit redaction for plain strings
```

- A `Secret` prints as `[REDACTED]` and never leaks through `toString`/logs —
  the accidental-leak class dies at the type.
- `KeyHandle` is the wipeable form crypto.* prefers over raw key strings.
- `get` and `secret` read the environment variable by its **exact** name —
  there is no `KOF_` prefix and no config fallback (`secrets.get("db_password")`
  reads env `db_password`, not `KOF_DB_PASSWORD`). `get` is the legacy raw
  `String`; use `secret`/`of` for the typed `Secret`.

**See also:** [kof.crypto](crypto.md) — the ciphers that take these handles.
