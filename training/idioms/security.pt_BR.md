[English](security.md) | [Português](security.pt_BR.md)

# Idioms — SEGURANÇA (passwords / crypto / jwt / secrets / session / auth)

> 8.5 (plano universal, fatia 2, 19/09). Cada forma abaixo foi compilada
> (`StdlibIdiomsCompileTest`) e o round-trip AES-GCM RODA na JVM com a golden
> travada antes desta linha entrar no corpus. Referência profunda:
> `training/language/security.md`; tutorial: `learn/36-security.md`; o exemplo que
> executa no CI: `training/examples/security.kf`.

## Senhas — nunca hashes senha à mão

```kof
// ❌ ERRADO — digest NÃO é hash de senha (rápido, sem salt)
var stored = crypto.sha256(password)

// ✅ CERTO — formato versionado, com salt, verificação constant-time
var stored = passwords.hash(password)
if (passwords.verify(input, stored)) { login(user) }
if (passwords.needsRehash(stored)) { passwords.hash(input) }  // parâmetros mudaram: re-hash no login
```

PORQUÊ: `hash` é dono do algoritmo/parâmetros/versão (R10: formato versionado);
`verify` já é constant-time para você; `needsRehash` é o caminho de upgrade — nunca
script de migração.

## Crypto — digests, HMAC e UMA regra para comparação

```kof
var digest = crypto.sha256(body)
var mac = crypto.hmacSha256(key, body)            // (key, msg) — key primeiro
if (security.constantTimeEquals(mac, received)) { accept() }   // NUNCA ==
var nonce = crypto.randomHex(16)                  // n BYTES -> 2n chars hex
```

PORQUÊ: comparar segredo com `==` é canal de timing — a linguagem tem
`constantTimeEquals` exatamente para essa comparação nunca ser artesanal.
Criptografia nunca é caseira (R11): isto binda na JCA na JVM.

REGRA: os digests levam Strings ou Ints — NUNCA um array de bytes.
`crypto.sha256(new Byte[n])` / `crypto.hmacSha256(key, byteArr)` recusa no
compile com `SECN011` em todo alvo (§563: a forma degradava por alvo — o
Script digeria a IDENTIDADE do array, a JVM morria `VerifyError` no load).
Para payloads BINÁRIOS a linguagem tem face dedicada —
`crypto.sha256Bytes(Byte[])` / `crypto.hmacSha256Bytes(Byte[] key, Byte[] msg)`
(D-KOF-DIGEST-BYTES; hoje JVM/Android/Script/x86-Native; JS e riscv64/aarch64
recusam com o gap SECN000 até o port). Fixar bytes como AAD-String em hex
continua legal onde a face não está disponível.

## Chaves de sessão — X25519 + HKDF, nunca montadas à mão

```kof
var minha = keyExchange.privateKey()                      // Secret (R8: nunca imprimível)
var minhaPub = keyExchange.publicKey(minha)               // String 64-hex — envie
var compartilhada = keyExchange.shared(minha, secrets.of(pubDoPar))
var txKey = keyExchange.hkdfSha256(compartilhada, saltHex, "0001", 32)  // AES por direção
```

D-KOF-X25519: material privado SÓ viaja dentro de `Secret` (argumento
errado = `SECN014`); o valor público é a única exportação. JVM/Android/
Script reais (RFC 7748 + RFC 5869); JS/Native/cross recusam `SECN012` até
o port. Derive chaves por direção com HKDF sobre o segredo compartilhado —
nunca reutilize um escalar cru como chave de app.

## Criptografia — AES-GCM; argumentos são `(plain, keyHex)`, chave = 32 bytes

```kof
var key = crypto.randomHex(32)                    // 32 bytes = 64 chars hex — imposto em runtime
var ct = crypto.encryptAesGcm("segredo", key)     // (plaintext, keyHex64)
var pt = crypto.decryptAesGcm(ct, key)
```

Gates honestos (medidos 19/09): AES-GCM = JVM/JS/Native x86 ✅ (golden riscv/aarch
ainda não medida); **ChaCha20 no Native = `SECN002`** — a forma existe, o target diz
não em tempo de compilação (R6, nunca fallback silencioso mais fraco).

## JWT — HS256 fixo, verify devolve as claims

```kof
var claims = "{\"sub\":\"u1\",\"iss\":\"kof\",\"aud\":\"api\"}"
var token = jwt.create(claims, secret)
var withTtl = jwt.create(claims, secret, 3600)
var checked = jwt.verify(token, secret, "kof", "api")   // sig + exp + iss + aud
var fresh = jwt.secret()                               // gera segredo forte
```

PORQUÊ: o `verify` de 4 argumentos valida emissor/audiência **dentro** da
plataforma — decodificar base64 e separar o payload à mão é o anti-pattern.

## Segredos — env com fallback, redact antes do log

```kof
var apiKey = secrets.get("API_KEY")                  // variável de ambiente
var port = secrets.get("PORT", "8080")               // com fallback
log.info("config em " + secrets.redact(apiKey))      // "sk-a********mnop" — nunca vaza
```

PORQUÊ: segredo que chega ao log já foi exposto; `redact` mantém o formato
(primeiras/últimas chars) para debug sem o valor.

## `Secret` — o valor que não vaza (D-SECRETS face 1)

```kof
var key = secrets.of("sk-live-...")          // ou secrets.secret("API_KEY") — env por nome
println(key)                                  // Secret(*** )   — redigido, sempre
log.info("key=" + key)                        // Secret(*** )   — concat também não vaza
if (key == secrets.secret("API_KEY")) { }     // igualdade de conteúdo constant-time
var raw = key.reveal()                        // o ÚNICO export cru — greppable numa auditoria
```

PORQUÊ: `secrets.get` devolve um `String` cru que flui para `println`, concat,
JSON e logs de forma invisível. `Secret` torna o caminho seguro o padrão — a
impressão é redigida por construção, e a única via para o valor cru é uma palavra
única (`reveal()`) que se pode grepar na revisão. Prefira `secrets.secret`/`of` a
`get` em código novo; `get` fica para o caminho cru legado. JVM-primeiro (R7):
JS/Native/Script/Android rejeitam em compile-time com `SECN008` (nunca stub
silencioso). NÃO faça `reveal()` para comparar nem logar — `==` já é
constant-time.

A redação é forçada no JVM (D-SECRETS P2): `json.encode(key)` devolve
`"Secret(*** )"` (nunca os campos), e `reveal()` indo direto para
`log.*`/`json.encode` levanta o aviso `SECN009` — o desmascaramento é
deliberado, então precisa ser visível. Bytes não-texto passam por
`secrets.fromBytes(u8)` (byte a byte, sem perda).

## `KeyHandle` — a chave que você nunca lê (D-SECRETS P3)

```kof
val kh = secrets.keyFromHex(hexKey)                  // ou keyFromPem(path)/keyFromKeystore(path, alias, pwd)
val tag = crypto.hmacSha256(kh, message)             // sobrecarga com chave — sem chave crua no código
val token = jwt.create(claims, kh)
val kh2 = kh.rotate()                                // handle novo; kh fica revogado
// crypto.hmacSha256(kh, msg)                        // SECN010 em runtime — handle rotacionado não pode ser reusado
```

PORQUÊ: passar o hex cru da chave adiante vaza para assinaturas, logs e diffs. Um
`KeyHandle` carrega o material mas nunca o expõe; `rotate()` invalida o handle
antigo, então uma referência roubada não assina mais. JVM-primeiro (R7): JS/
Native/Script/Android rejeitam em compile-time com `SECN008` (nunca stub
silencioso).

## Sessões, CSRF, rate-limit, headers

```kof
var sid = security.sessionCreate(user)               // payload entra, id sai
var who = security.sessionGet(sid)
security.sessionDestroy(sid)

var tok = security.csrfToken()
if (!security.csrfValid(tok)) { return "forbidden" }

if (!security.rateLimit("ip:" + addr, 10, 60)) { return "too many" }  // chave, limite, janela

var plain = security.cookieSet("sid", sid)           // string do header Set-Cookie
var hardened = security.cookieSet("sid", sid, mapOf("HttpOnly", "true"))

println(security.cspHeader() + security.hstsHeader() + security.frameHeader()
        + security.contentTypeOptionsHeader() + security.referrerHeader())
if (security.corsAllowed(origin, "https://app.kof")) { }
```

Gates honestos (medidos 19/09): sessões / rateLimit / secrets / constantTimeEquals /
`apiKeyGenerate`+`apiKeyValid` = JVM+JS+Native x86 ✅; **`csrf*` e os construtores de
header = `SECN000` em JS e Native** (tempo de compilação, R6); golden riscv/aarch não
medida. SCRIPT: o compilador não emite nada por design (`COMP003`); a face do
interpretador está medida só para `cache/net/config` — faces de segurança ⏳.

## auth.* — contexto de middleware web, não chamada solta

`auth.authenticated()`, `auth.hasRole("admin")`, `auth.user()`, `auth.claims()`,
`auth.token()`, `auth.secret(s)`, `auth.resourceServer(...)` resolvem **dentro de um
handler/`app.use` do `kof.web`** (face web JVM). `auth.x()` solto fora desse contexto é
`SEM025/SEM002` — isso está correto, não é gap: identidade vem da requisição, e uma
chamada sem requisição não tem resposta. Padrão completo: `learn/36-security.md`
§"Web auth".

## Nunca (R11)

Sem criptografia caseira, sem `==` em segredo, sem digest sem salt para senha, sem
literal de segredo no fonte, sem decodificar JWT de mão em base64. Por baixo, libs
auditadas; a superfície é pequena de propósito (6 namespaces, todos travados
comportamento-a-dispatcher em `StdCatalogSignaturesTest`).
