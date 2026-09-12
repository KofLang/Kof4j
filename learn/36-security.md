# 36 — Segurança (kof.security)

> **Kof 0.2.6-beta — 02 set 2026 — 810 testes — completo nos 3 targets (gaps SECN00x documentados)**

`kof.security` é a camada de segurança da Standard Library: senhas, criptografia,
JWT, segredos e autenticação para aplicações web — com **secure by default**.

```kof
var hash = passwords.hash("hunter2")
println(passwords.verify("hunter2", hash))   // true
```

## Passwords

Nunca use `sha256(password)` para armazenar senha. Use `passwords`:

```kof
var hash = passwords.hash("senha")            // pbkdf2$sha256$600000$salt$hash
var ok = passwords.verify("senha", hash)      // comparação constant-time
var rehash = passwords.needsRehash(hash)      // parâmetros defasados?
```

A escolha de algoritmo/iterações/salt é automática e segura. O formato do
hash é versionado — quando os parâmetros recomendados mudarem,
`needsRehash` retorna `true` e a aplicação pode re-hashear.

## Crypto

```kof
var digest = crypto.sha256("kof")             // hex
var mac = crypto.hmacSha256(key, data)        // hex
var key = crypto.randomHex(32)                // 32 bytes seguros
var ct = crypto.encryptAesGcm("segredo", key) // aesgcm$iv$ct
var pt = crypto.decryptAesGcm(ct, key)        // falha em tamper
```

Gaps de target são erros de compilação claros (`SECN00x`), nunca
comportamento silencioso.

## JWT

```kof
var token = jwt.create("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", secret)
var claims = jwt.verify(token, secret)
var claims2 = jwt.verify(token, secret, "kof", "api")   // iss + aud
```

O algoritmo é fixo (HS256) — **nunca aceito do token** (sem confusão de
algoritmo). `exp`, `iss` e `aud` são validados. O secret padrão vem de
`KOF_JWT_SECRET` (`jwt.secret()`).

## Segredos

```kof
var apiKey = secrets.get("API_KEY")           // variável de ambiente
var apiKey = secrets.get("API_KEY", "dev")    // com fallback
var logLine = secrets.redact(token)           // nunca vaze segredos em logs
```

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

## Comparação constant-time

```kof
if (security.constantTimeEquals(a, b)) { ... }
```

Use para comparar tokens, hashes e segredos — nunca `==` em valores
sensíveis.

## Suporte por target

`kof.security` é completo nos 3 targets — Native em asm puro (sem libc),
valores idênticos ao JVM (FIPS 180-4 / RFC 2104):

| Área | JVM | Native | JS |
|------|-----|--------|----|
| passwords (PBKDF2-HMAC-SHA256, 600k) | ✅ | ✅ (asm) | ✅ (delegação ao platform) |
| sha256 / hmacSha256 | ✅ | ✅ (asm) | ✅ (JS puro) |
| sha512 | ✅ | ✅ (asm) | ✅ (JS puro) |
| aes-gcm | ✅ | ✅ (asm) | ✅ (JS puro) |
| jwt HS256 (sig/exp/iss/aud) | ✅ | ✅ (asm) | ✅ |
| secrets (env) | ✅ | ✅ (`/proc/self/environ`) | ✅ |
| constant-time / redact | ✅ | ✅ | ✅ |
| rateLimit / session / apiKey (G9) | ✅ | ✅ | ✅ |
| auth web (`auth.*`, Bearer JWT) | ✅ | ❌ | ❌ |
| csrf / cors / security headers | ✅ | ❌ | ❌ |

Gaps remanescentes (`SECN00x`, diagnóstico em compile-time): o contexto web de
auth/headers (só JVM — web server é JVM, `WEB002` no Native). AES-GCM no JS
(`SECN002`) e JWT (`SECN004`) fechados. Testes: `KofSecurityTest` (27; unit +
E2E nos 3 targets + adversariais). Referência completa: `docs/stdlib/security.md`.