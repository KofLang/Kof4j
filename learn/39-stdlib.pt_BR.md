[English](39-stdlib.md) | [Português](39-stdlib.pt_BR.md)

# 39 — Standard Library universal (math, strings, encoding, uuid, validation, time)

> **Todo namespace agora tem capítulo próprio em [`stdlib/`](stdlib/README.pt_BR.md)
> — as tabelas de funções lá são o contrato 1:1 (medidas dos dispatchers
> reais). Este capítulo mantém a narrativa, os exemplos e a tabela honesta de
> paridade.**

| Namespace | Capítulo |
|-----------|----------|
| `json` | [stdlib/json](stdlib/json.pt_BR.md) |
| `db` | [stdlib/db](stdlib/db.pt_BR.md) |
| `http` | [stdlib/http](stdlib/http.pt_BR.md) |
| `cache` | [stdlib/cache](stdlib/cache.pt_BR.md) |
| `config` | [stdlib/config](stdlib/config.pt_BR.md) |
| `log` | [stdlib/log](stdlib/log.pt_BR.md) |
| `process` | [stdlib/process](stdlib/process.pt_BR.md) |
| `shell` | [stdlib/shell](stdlib/shell.pt_BR.md) |
| `ssh` | [stdlib/ssh](stdlib/ssh.pt_BR.md) |
| `net` | [stdlib/net](stdlib/net.pt_BR.md) |
| `orm` | [stdlib/orm](stdlib/orm.pt_BR.md) |
| `gpu` | [stdlib/gpu](stdlib/gpu.pt_BR.md) |
| `mq` | [stdlib/mq](stdlib/mq.pt_BR.md) |
| `observability` | [stdlib/observability](stdlib/observability.pt_BR.md) |
| `tetris` | [stdlib/tetris](stdlib/tetris.pt_BR.md) |
| `media` | [stdlib/media](stdlib/media.pt_BR.md) |
| `buffer` | [stdlib/buffer](stdlib/buffer.pt_BR.md) |
| `passwords` | [stdlib/passwords](stdlib/passwords.pt_BR.md) |
| `crypto` | [stdlib/crypto](stdlib/crypto.pt_BR.md) |
| `jwt` | [stdlib/jwt](stdlib/jwt.pt_BR.md) |
| `secrets` | [stdlib/secrets](stdlib/secrets.pt_BR.md) |
| `security` | [stdlib/security](stdlib/security.pt_BR.md) |
| `auth` | [stdlib/auth](stdlib/auth.pt_BR.md) |
| `math` | [stdlib/math](stdlib/math.pt_BR.md) |
| `strings` | [stdlib/strings](stdlib/strings.pt_BR.md) |
| `encoding` | [stdlib/encoding](stdlib/encoding.pt_BR.md) |
| `uuid` | [stdlib/uuid](stdlib/uuid.pt_BR.md) |
| `time` | [stdlib/time](stdlib/time.pt_BR.md) |
| `random` | [stdlib/random](stdlib/random.pt_BR.md) |
| `rng` | [stdlib/rng](stdlib/rng.pt_BR.md) |
| `validation` | [stdlib/validation](stdlib/validation.pt_BR.md) |

## math — aritmética que todo programa repete

```kof
math.abs(-5)          // 5
math.sign(-5)         // -1
math.clamp(99, 0, 10) // 10   — limita ao intervalo [min,max]
math.min(3, 7)        // 3
math.max(3, 7)        // 7
math.isEven(4)        // true
math.isOdd(4)         // false
math.isPositive(4)    // true   (0 não é positivo)
math.isNegative(4)    // false
math.isZero(0)        // true
math.sqrt(16.0)       // 4.0  — PRIMEIRO Double (S1b); -1.0 => NaN
math.lerp(0.0, 10.0, 0.5)     // 5.0  — a + (b - a) * t   (S1b.1)
math.percentage(3.0, 4.0)     // 75.0 — total 0 => NaN, nunca lança (S1b.1)
math.isInteger(4.0)           // true;  4.5/NaN/Inf => false (S1b.1)
math.isDecimal(4.5)           // true;  !isInteger (S1b.1)
math.roundTo(3.14159, 2)      // 3.14  — meio-para-longe-do-zero em N casas (S1b.3)
math.roundTo(2.675, 2)        // 2.68  — escala aritmética (ver nota abaixo)
math.roundTo(1234.0, -2)      // 1200.0 — decimals negativo arredonda p/ dezenas/centenas
math.pow(2.0, 10.0)           // 1024.0 — libm no native x86 (S1b.2)
math.parseInt("42")           // 42     — contrato JDK, lança em inválido (S13a)
math.parseDouble("2.5")       // 2.5
math.parseIntOrDefault("x", 0) // 0     — nunca lança (S13b)
```

Os inteiros ficam acima; `sqrt`/`lerp`/`percentage`/`isInteger`/`isDecimal`/
`roundTo`/`pow` são os `Double` da namespace (JVM/Script/JS/x86; riscv64/aarch64
rodam todos exceto `pow`, que é `MATH001` — libm não linka no cross estático).
Os argumentos são **Double explícitos** — `math.lerp(0, 10,
0.5)` não compila (SEM025; sem widening silencioso). `roundTo(value, decimals)`
recebe um `Int` `decimals` e usa **escala decimal determinística** (não o
`BigDecimal` decimal-string): `roundTo(2.675, 2)` é `2.68` porque o double mais
próximo de `2.675` escala para `267.5`.

## strings — predicados, conversores e palavras

Predicados (todos `String -> Bool`; `""`/`null` são `false`):

```kof
strings.isAlpha("ab")          // true   — só [A-Za-z] (ASCII)
strings.isNumeric("12")        // true   — só [0-9]
strings.isAlphaNumeric("a1")   // true
strings.isAscii("ab")          // true   — bytes < 128
strings.isUpperCase("AB")      // true   — tem letra maiúscula; demais ignoradas
strings.isLowerCase("ab")      // true
strings.count("aXaXa", "X")    // 2      — ocorrências não-sobrepostas
```

Conversores e preenchedores:

```kof
strings.capitalize("abc")      // "Abc"
strings.reverse("abc")         // "cba"
strings.repeat("ab", 2)        // "abab"
strings.truncate("abcdef", 3)  // "abc"
strings.padLeft("7", 3, "0")   // "007"   — pad é String; usa a 1ª char
strings.padRight("7", 3, "0")  // "700"
```

Conversores de **palavra** — boundary é o do parser HTTP/XML do próprio
projeto (cavalo-de-pau de maiúsculas vira fronteira), não `split(" ")`:

```kof
strings.toCamelCase("hello_world")  // "helloWorld"
strings.toPascalCase("hello world") // "HelloWorld"
strings.toSnakeCase("HTTPServer")   // "http_server"
strings.toKebabCase("helloWorld")   // "hello-world"
strings.slugify("Hello, World!! 42")// "hello-world-42"
```

Escape HTML (5 entidades — nunca montar página com interpolação crua):

```kof
strings.escapeHtml("a<b>&\"'c")   // "a&lt;b&gt;&amp;&quot;&#39;c"

`strings.escapeJson(s)` escapa o corpo de uma string literal JSON
(RFC 8259): `\` vira `\\`, `"` vira `\"`, os de controle viram `\b \f \n \r \t`
ou `\u00xx` (hex minúsculo); demais bytes (incl. UTF-8) são copiados. Nos 4 targets.

strings.escapeHtml("Café & ç")     // "Café &amp; ç" (>=128 é copiado)
strings.unescapeHtml("a&amp;b")         // "a&b" — 5 nomeadas + &#DDD;/&#xHH; (UTF-8)
```

Whitespace (WS = tab/LF/VT/FF/CR/espaço; >=128 **não** é WS):

```kof
strings.removeWhitespace("  a\tb\nc  ")   // "abc"
strings.normalizeWhitespace("  a   b  ") // "a b" — trim + colapso p/ 1 espaço
```

> ⚠️ `capitalize` e os conversores de **palavra** são **ASCII** nos 4 targets
> (medido): só `a-z` → `A-Z`; qualquer byte `>= 128` é **preservado** mas
> **nunca capitalizado** (`"café"` → `"Café"`, mas `"ção"` → `"ção"`, não
> `"Ção"`), e nos conversores de palavra byte `>= 128` age como **delimitador**
> (`"Ünïcödé café"` → `"n_c_d_caf"`). UTF-8 de verdade (capitalizar/delimitar
> por código Unicode) nos nativos é o gap `NAT-STR01` (aberto).



## encoding — bytes que toda API externa exige

Tudo opera **por bytes UTF-8** (mesma saída nos 4 targets; o JS do Kof não é
JavaScript — não depende de `TextEncoder`).

```kof
encoding.hexEncode("Hi")          // "4869"
encoding.hexDecode("4869")        // "Hi"
encoding.base64Encode("Man")      // "TWFu"
encoding.base64Decode("TWFu")     // "Man"
encoding.base64UrlEncode("a?b")   // "YT9i"        — sem padding, alfabeto -_
encoding.base64UrlDecode("YT9i")  // "a?b"
encoding.urlEncode("a b")         // "a%20b"       — NÃO '+' (RFC 3986)
encoding.urlDecode("a%20b")       // "a b"
```

Os **decoders são tolerantes por spec** (não-dígitos em hex viram `0`
naquele nibble; inválidos em base64 são pulados; `'%'` sem 2 hex passam
literais). Isso é decisão travada na matriz — não "jeitinho".

## uuid — v4 no formato RFC 4122

```kof
var id = uuid.v4()
println(id.length)              // 36
// 8-4-4-4-12, dígito 13 == '4', 19º ∈ {8,9,a,b}

uuid.isUuid(id)                 // true  — valida a FORMA
uuid.isUuid("550e8400-e29b-41d4-a716-446655440000")  // true
uuid.isUuid("não-é-uuid")       // false
```

`v4()` é não-determinístico por natureza: os testes travam **forma**, não
igualdade. `isUuid` é o inverso: predicado puro de forma (36 chars, traços
em 8/13/18/23, hex min ou maiúsculo) — não verifica version/variant. Roda em
JVM/Script/JS e em Native x86/riscv64/aarch64 (`UUID001` fechado 09/09 — fatia B +
tradutor; `getrandom(2)` sob qemu, `KofUuidTest.uuidV4CrossArch`/`isUuidCrossArch`).

## uuid — v7 (S3b.2, RFC 9562)

```kof
var id = uuid.v7()          // ordenável por tempo: 48 bits de unix-ts-ms no início
println(id.charAt(14))      // '7' (versão)
```

Mesmo shape 8-4-4-4-12 do v4, mas os 12 primeiros hex codificam o relógio
(epoch-ms big-endian), então v7's gerados em sequência são ordenáveis.
Variante 10xx (19º ∈ {8,9,a,b}) como no v4. Entropia só do SO (SecureRandom /
getrandom / crypto — R11).

## uuid — isUuid (S3b-ext)

```kof
uuid.isUuid("550e8400-e29b-41d4-a716-446655440000")   // true
uuid.isUuid("550e8400e29b41d4a716446655440000")        // false (sem traços)
uuid.isUuid(uuid.v4())                                  // true (paridade)
```

Valida o **shape** RFC 4122: 36 chars, hífens fixos em 8/13/18/23, o resto
hex (maiúsculas ou minúsculas). **Não** checa versão/variante — qualquer
v1..v5 canônico é `true`; a entropia é papel do `v4()`.

## time — isWeekend (S7-ext)

```kof
time.isWeekend(2026, 9, 12)   // true  — sábado
time.isWeekend(2026, 9, 9)    // false — quarta
time.isWeekend(2026, 2, 30)   // false — data inválida (dayOfWeek => 0)
```

`dayOfWeek(y,m,d) >= 6` (ISO 1=segunda..7=domingo). Wrapper puro nos 5 alvos —
reusa a máquina de calendário; data inválida cai em `false` automaticamente.

## validation — formatCpf / formatCep (S12)

```kof
validation.formatCpf("52998224725")    // "529.982.247-25"
validation.formatCpf("123")            // "123" (não confere => original)
validation.formatCep("01310100")       // "01310-100"
validation.formatCnpj("34546401000163") // "34.546.401/0001-63"
```

Pontuação BR: 11 dígitos => `DDD.DDD.DDD-DD` (CPF); 8 => `DDDDD-DDDD` (CEP);
14 => `NN.NNN.NNN/NNNN-NN` (CNPJ). Fora disso
(nº errado de dígitos, null) => **original** — face leniente, nunca lança.
Formata **sem validar** (dígitos quaisquer; validar é papel de `isCpf`/
`isCep`). 5 alvos; reusa o mesmo `brDigits` dos predicadores.

## strings — uncapitalize (S11)

```kof
strings.uncapitalize("Hello World")   // "hello World"
strings.uncapitalize("HELLO")         // "hELLO"
strings.uncapitalize("1abc")          // "1abc" (1º byte fora de A-Z => original)
```

Espelho do `capitalize`: só o 1º byte; `A-Z` -> `a-z`; null/`""`/fora-de-A-Z
=> original. ASCII nos 5 alvos (paridade com a regra S2b do capitalize).

## random — sorteio com entropia do SO (S10a/b)

```kof
var n = random.randomInt(100)          // 0..99 (bound<=0 -> 0, face leniente)
var coin = random.randomBoolean()      // 0 ou 1
var token = random.randomString(8, "0123456789abcdef")  // 8 chars do alfabeto
var salt = random.randomBytesHex(16)   // 32 hex minúsculo (alias de random.hex — DD-STDLIB-01)
// escolha de lista = idiom, não função:
var l = listOf("a", "b", "c")
var pick = l[random.randomInt(l.size)]
```

A entropia vem SEMPRE da primitiva do SO (getrandom / SecureRandom /
crypto) — sem PRNG caseiro. Para tokens de segurança use `security.*`
(randomHex/randomInt com validação estrita); `random.*` é a face
sorteio/shuffle/teste. Não-determinístico: os testes travam **contrato**
(faixa + bordas), não igualdade.

## rng — PRNG determinístico semeável (X8 fatias 1–2, property-based testing)

```kof
rng.seed(42)                           // reset: mesma seed => mesma sequência, QUALQUER backend
var v = rng.int(1000)                  // [0,1000); bound<=0 -> 0 (leniente)
var b = rng.boolean()                  // true/false
var d = rng.double()                   // [0,1), mantissa de 52 bits, nunca 1.0
var s = rng.string(8, "abc")           // 8 chars do alfabeto; n<=0/vazio -> ""
// idiom de property-based test (harness de teste):
test "adição comuta" {
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

Mesma seed => MESMA sequência na JVM e no JS (xorshift128 + splitmix32,
aritmética 32-bit exata — paridade provada byte-a-byte pelo
`KofRngTest.jvmJsParity`). NUNCA para chaves/tokens/segredo: isso é
`random`/`security` (entropia do SO, R11). Fatia 1 = JVM + JS; NATIVE/ANDROID
falham em compile com o gap honesto `RNG001` (asm x86_64 caiu na fatia 2 — byte-idêntico ao JVM).
int(bound) usa módulo (viés pequeno documentado) — o contrato é paridade
determinística, não uniformidade criptográfica.


## validation — documentos BR, rede e cartão

```kof
validation.isCpf("52996581504")          // true  — dígitos extraídos
validation.isCpf("529.982.247-25")       // true  — pontuação ignorada
validation.isCnpj("34546401000163")      // true  // CPF/CNPJ: mod-11 com
validation.isCep("01310-100")            // true  //   dígito verificador
validation.isPis("12345678900")          // true
validation.isNis("12056412278")          // true — NIS reusa o mod-11 do PIS

validation.isIpv4("192.168.0.1")         // true  — dotted-quad
validation.isIpv4("01.2.3.4")            // false — zero à esquerda não vale
validation.isMac("00:1A:2B:3C:4D:5E")    // true  — ':' ou '-', consistente
validation.isPort(443)                   // true  — 1..65535
validation.isCreditCard("4532 0151 1283 0366") // true — Luhn, 12..19 dígitos
validation.isIpv6("fe80::1")                 // true  — subconjunto RFC 5952
validation.isIpv6("::ffff:192.168.0.1")      // false — forma mista não na v1
validation.isDomain("sub.example.co.uk")     // true  — RFC 1123 labels
validation.isDomain("localhost")             // false — exige >=2 labels
validation.isDomain("xn--mnchen-3ya.de")     // true  — punycode (é ASCII)
```

> O que **não** é validação de conteúdo: `validation.min/max` (já existiam,
> G4) comparam números; os predicados acima checam formato + checksum.

## time — calendário civil (sem relógio, sem timezone)

```kof
time.isLeapYear(2024)                 // true   — Gregório (%4, %100, %400)
time.daysInMonth(2024, 2)             // 29
time.dayOfWeek(2026, 9, 9)            // 3      — ISO: 1=segunda..7=domingo
time.daysBetween(2024, 1, 1, 2024, 3, 1) // 60  — pode ser negativo
time.addDays("2024-02-28", 1)         // "2024-02-29" — data ISO (String)
time.diffDays("2024-01-01", "2024-03-01") // 60 — pode ser negativo
```

Domínio **1..9999** (serial civil cabe em Int; fora disso ou data inexistente
→ `isLeapYear=false` / `0`). A função `now`/`sleep`/`interval` de relógio é
de `time` desde antes — o calendário acima é a parte pura, determinística.

`addDays`/`diffDays` aceitam data **ISO em String** (`YYYY-MM-DD`); inválido
→ `""` (add) / `0` (diff). Disponível em **JVM, Script** (via `java.time`) e
**JS** (mesmo algoritmo civil do calendário, sem `Date` => paridade byte-idêntica);
no native **riscv64/aarch64** é gap honesto `TIME002` (erro claro no compile, nunca — x86 FECHADO S7c: RuntimeTimeIso asm)
fallback silencioso) — o port para asm é o próximo degrau (mesma ordem do
port nativo do `net`, `NET001`).

## net — componentes de URL (S8)

`net` decompõe uma URL em 6 campos, um por função — a mesma String entra, um
pedaço sai:

```kof
val url = "https://user:pw@koflang.dev:8443/docs/intro?page=2#sintaxe"
net.scheme(url)     // "https"
net.host(url)       // "koflang.dev"   (userinfo e porta removidos)
net.port(url)       // "8443"          (String — parse fica com quem usa)
net.path(url)       // "/docs/intro"
net.query(url)      // "page=2"
net.fragment(url)   // "sintaxe"
net.queryEncode("a b&c=1")          // "a%20b%26c%3D1"
net.queryDecode("a%20b%26c%3D1")    // "a b&c=1"
```

Campo ausente devolve `""` (nunca lança, nunca `null` de surpresa — o `null` só
chega se a entrada for `null`). É subconjunto v1 do RFC 3986: IPv6 com colchetes
(`http://[::1]:8080`) ainda não é reconhecido (o host sai truncado) — port e
forma mista ficam para a v2, documentado. Nos nativos (x86/riscv/aarch) o
namespace roda nos 4 alvos (port riscv/aarch fechado 09/09 — mesma máquina de
spans nos 3 nativos).

## Paridade por target (tabela honesta)

| API | JVM / Script | Native x86_64 | Native riscv64 / aarch64 | JS |
|---|---|---|---|---|
| `math.*`, `strings.is*/count/capitalize/reverse/repeat/truncate/pad*/escapeHtml`, `toCamel/Pascal/Snake/Kebab/slugify`, `encoding.hex*/url*`, `time.isLeapYear/daysInMonth/dayOfWeek/daysBetween`, `validation.isCpf/isCnpj/isCep/isPis/isNis/isIpv4/isIpv6/isMac/isPort/isCreditCard/isDomain` | ✅ | ✅ | ✅ | ✅ |
| `encoding.base64*` / `base64Url*` | ✅ | ✅ | ✅ | ✅ |
| `net.*` (S8) | ✅ | ✅ | ✅ | ✅ |
| `uuid.v4` / `uuid.v7` | ✅ | ✅ | ✅ | ✅ |
| `random.randomInt/randomBoolean/randomString` (face beta S10a/b) | ✅ | ✅ | ✅ | ✅ |
| `random.double/boolean/int/hex` (face main S10) | ✅ | ✅ | ✅ (B27) | ✅ |
| `uuid.isUuid` (S3b.1, predicado de forma) | ✅ | ✅ | ✅ (B25) | ✅ |
| `time.addDays` / `time.diffDays` (S7a/b/c, data ISO) | ✅ | ✅ | `TIME002` | ✅ |

Gate = erro de compilação **com código** (R6 — nunca stub silencioso):
`strings.toCamelCase` e os conversores de palavra chegaram aos 4 targets só
em 09/09 (gap `STRN001` fechado com o port riscv + diff byte-a-byte no
qemu). Enquanto um alvo está gated, o compilador diz **o quê** e **qual
gap**, e o programa não vira comportamento errado.

Como conferir você mesmo: cada linha da tabela espelha um programa em
`ConformanceMatrixTest` (casos `stdmath`, `stdstrings*`, `stdenc`,
`stdvalidation*`, `stdluhn`, `stdtime`) — mesma saída esperada nos 4 targets,
executados de verdade (riscv/aarch64 sob qemu).

## Próximo passo

- `training/idioms/stdlib.md` — BAD/GOOD/WHY de cada namespace.
- `docs/stdlib/stdlib.md` §3 — a matriz de referência com gates.
- `docs/stdlib/PLAN-STDLIB-EXPANSION.pt_BR.md` — o que falta: `random` (P0),
  `last4`/`creditCardBrand` (tabela de bandeira = marca registrada — avaliar
  antes) e `math` Double (FLT).

