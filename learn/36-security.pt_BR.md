[English](36-security.md) | [Português](36-security.pt_BR.md)

# 36 — Segurança (kof.security)

> **Kof 0.5.0-beta — set 2026 — completo nos 3 targets (gaps SECN00x documentados)**

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

Para código novo, prefira o `Secret` tipado — é redigido por construção e só
`reveal()` devolve o texto cru:

```kof
var key = secrets.of("sk-live-...")           // ou secrets.secret("API_KEY")
println(key)                                   // Secret(*** )
var raw = key.reveal()                         // o único export cru
var fromBytes = secrets.fromBytes(payload)     // bytes não-texto, byte a byte
```

`json.encode(key)` é redigido em runtime (`"Secret(*** )"`), e alimentar
`reveal()` direto em `log.*`/`json.encode` levanta o aviso `SECN009`.
`secrets.secret(name)` com variável de ambiente não definida/em branco é erro
explícito e catchável (`catch (String e)` nomeia `SECN015`) — nunca um `Secret`
vazio silencioso; o legado `secrets.get(name)` segue devolvendo o `String` cru.
Chaves são manipuladas sem nunca lê-las, via `KeyHandle`:

```kof
val kh = secrets.keyFromHex(hex)               // ou keyFromPem(path) / keyFromKeystore(path, alias, pwd)
val tag = crypto.hmacSha256(kh, message)
val kh2 = kh.rotate()                          // kh fica revogado; reusá-lo é SECN010
```

`Secret`/`KeyHandle` são JVM-primeiro: os demais alvos rejeitam em compile-time
com `SECN008` (nunca stub silencioso).

## Chaves de sessão — X25519 + HKDF (D-KOF-X25519)

```kof
var minha = keyExchange.privateKey()           // Secret — o escalar nunca é imprimível
var minhaPub = keyExchange.publicKey(minha)    // String 64-hex — segura para enviar
var compartilhada = keyExchange.shared(minha, secrets.of(pubDoPar))  // Secret
var rxKey = keyExchange.hkdfSha256(compartilhada, saltHex, "0001", 32)  // chave AES por direção (hex)
```

PORQUÊ: uma chave de sessão NUNCA é montada à mão e NUNCA viaja como
String crua — o material privado vive em `Secret` (R8), e o valor público é
a única exportação. Argumento errado na face Secret = `SECN014`; a face roda
em JVM/Android/Script hoje, JS/Native/cross recusam com `SECN012` até o
port (nunca silencioso). RFC 7748 + RFC 5869 (golden case-1 em
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