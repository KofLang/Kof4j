[English](secrets.md) | [Português](secrets.pt_BR.md)

# kof.secrets — segredos como valores que você não vaza

> **Status: JVM ✅ · tipos `Secret`/`KeyHandle` · face Android via JCA
> (`§278`) · faces cross no ledger de paridade (linha 14).**

| Função | Forma |
|--------|-------|
| `get` | `get(String key) -> String` · `get(String key, String d) -> String` |
| `redact` | `redact(String s) -> String` |
| `of` | `of(String literal) -> Secret` |
| `secret` | `secret(String name) -> Secret` |
| `fromBytes` | `fromBytes(Int[] bytes) -> Secret` |
| `keyFromHex` | `keyFromHex(String hex) -> KeyHandle` |
| `keyFromPem` | `keyFromPem(String path) -> KeyHandle` |
| `keyFromKeystore` | `keyFromKeystore(String path, String alias, String password) -> KeyHandle` |

```kf
var token = secrets.of(rawToken)          // Secret — println mostra [REDACTED]
var dbPass = secrets.get("db_password")   // env var "db_password" literal (getenv cru)
var k = secrets.keyFromPem("key.pem")     // KeyHandle para crypto.*
log.info("token=" + redact(header))      // redação explícita para strings puras
```

- Um `Secret` imprime como `[REDACTED]` e nunca vaza por `toString`/logs —
  a classe de vazamento acidental morre no tipo.
- `KeyHandle` é a forma apagável que o crypto.* prefere a strings de chave
  cruas.
- `get` e `secret` leem a variável de ambiente pelo nome **exato** — não há
  prefixo `KOF_` nem fallback de config (`secrets.get("db_password")` lê env
  `db_password`, não `KOF_DB_PASSWORD`). `get` é o `String` cru legado; use
  `secret`/`of` para o `Secret` tipado.

**Veja também:** [kof.crypto](crypto.pt_BR.md) — as cifras que recebem esses
handles.
