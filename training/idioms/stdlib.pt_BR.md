[English](stdlib.md) | [Português](stdlib.pt_BR.md)

# Idioms — STDLIB (math / strings / encoding / uuid / time / process / cache / config / log / net / gpu / media)

**Status:** available · **Introduced:** 0.3.0-beta (STDLIB track, 08/09/2026) · **Updated:** 08/09/2026

## What it is

Quatro namespaces de utilitários puros, chamados **sem prefixo `kof.`**
(estilo `math.clamp(...)`, `strings.slugify(...)`, `encoding.hexEncode(...)`,
`uuid.v4()`). Mesma API nos targets JVM / interpretador (Script) / Native
(x86_64) / JS; gaps cross-arch são **diagnóstico em compile-time**, nunca
stub silencioso (R6).

## math — Int-only (S1)

```kof
math.clamp(v, lo, hi)      // hi < lo => comportamento de swap NÃO garantido: valide antes
math.abs(x)  math.sign(x)
math.min(a, b)  math.max(a, b)     // aritmético; ≠ validation.min/max (predicado de tamanho)
math.isEven(x) math.isOdd(x) math.isPositive(x) math.isNegative(x) math.isZero(x)
math.sqrt(2.0)                          // Double; -1.0 => NaN (IEEE); riscv/aarch = MATH001
math.lerp(0.0, 10.0, 0.5)               // a + (b - a) * t — interpolação linear (S1b.1)
math.percentage(3.0, 4.0)               // 75.0; total == 0 => NaN (nunca lança) (S1b.1)
math.isInteger(4.0)                     // true; 4.5/NaN/Inf => false (S1b.1)
math.isDecimal(4.5)                     // !isInteger (S1b.1)
math.roundTo(3.14159, 2)                // 3.14 — meio-para-longe-do-zero em N casas (S1b.3)
math.roundTo(1234.0, -2)                // 1200.0 — decimals negativo: dezenas/centenas (S1b.3)
```

Double: `math.sqrt(x)` (S1b) + `lerp`/`percentage`/`isInteger`/`isDecimal`
(S1b.1, 10/09 — escalares **puros** Double, sem libm) + `roundTo(value, decimals)`
(S1b.3, 14/09 — meio-para-longe-do-zero por escala decimal determinística, sem
libm; `decimals` é `Int`, pode ser negativo; contrato aritmético:
`roundTo(2.675,2)==2.68`) existem em JVM/Script/JS/x86; NaN em <0 = IEEE;
riscv64/aarch64 = `MATH001` só no `pow` (o resto roda sob qemu). Os args são
**Double explícitos** — `math.lerp(0, 10, 0.5)` (Int) **não** compila (SEM025;
sem widening silencioso). `pow` está implementado no x86 (libm) mas é `MATH001`
no cross (sem link libm).

## strings — predicados e conversores (S2)

```kof
strings.isAlpha("Hello")           // só letras, não-vazio; "abc123" => false
strings.isNumeric("123")  strings.isAlphaNumeric("abc123")
strings.isAscii("ola")             // bytes >=128 => false (café => false nos 4 targets)
strings.isUpperCase("HELLO")       // >=1 letra e nenhuma minúscula; "123" => false
strings.isLowerCase("abc-123")     // demais chars ignorados
strings.count("aabaabaa", "ab")    // 2 — NÃO-sobrepostas; sub vazio => 0
strings.capitalize("hello")        // "Hello" (ASCII; 1º byte a-z)
strings.uncapitalize("Hello")      // "hello" — espelho exato do capitalize (S11)
strings.reverse("abc")             // "cba" (byte-reverso no Native — ver NAT-STR01)
strings.repeat("ab", 3)            // "ababab"; n<=0 => ""
strings.truncate("hello", 3)       // "hel"; n>=len => original; n<=0 => ""
strings.padLeft("7", 3, "0")       // "007" — pad é STRING, usa a 1ª char
strings.toCamelCase("hello_world") // "helloWorld"
strings.toPascalCase("hello world")// "HelloWorld"
strings.toSnakeCase("HTTPServer")  // "http_server" — boundary em maiúscula+minúscula!
strings.toKebabCase("XMLParser")   // "xml-parser"
strings.slugify("Hello, World!!")  // "hello-world" (não-ASCII vira separador)
```

## BAD — reimplementar o que a stdlib tem

```kof
// ❌ Java disfarçado
Bool isAlpha(String s) {
    if (s.length == 0) { return false }
    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i)
        if (!(c >= 65 && c <= 90) && !(c >= 97 && c <= 122)) { return false }
    }
    return true
}
```

## GOOD — a abstração existe

```kof
// ✅
var ok = strings.isAlpha(s)
```

## WHY

A regra de ferro é "complexidade pertence à plataforma". O loop de bytes
acima existe em 4 backends diferentes dentro do compilador — escrito uma vez,
testado na matriz de conformidade (`stdstrings`), paridade travada. Reusar é
mais curto, mais rápido e cross-target por construção.

## encoding — hex / base64 / url (S4)

```kof
encoding.hexEncode("café")            // "636166c3a9" (UTF-8 por bytes, minúsculo)
encoding.hexDecode("4869")            // "Hi"; dígito inválido => 0; ímpar => último é nibble ALTO
encoding.base64Encode("Man")          // "TWFu" (com padding)
encoding.base64Decode("TWFu")         // TOLERANTE: ignora inválidos, para em '='
encoding.base64UrlEncode(bytes...)    // alfabeto -_, SEM padding (JWT-style)
encoding.base64UrlDecode(s)           // aceita os 2 alfabetos + padding opcional
encoding.urlEncode("a b")             // "a%20b" — espaço => %20, NÃO '+'
encoding.urlDecode("caf%C3%A9")       // "café"; '%' sem 2 dígitos passa literal
```

## uuid (S3b)

```kof
var id = uuid.v4()   // ex.: "xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx" (shape RFC 4122)
uuid.isUuid(id)      // true — valida o SHAPE (traços 8/13/18/23 + resto hex); NÃO checa versão/variante
```

Não-determinístico: valide pelo **shape** (`isUuid`, ou à mão: traços em 8/13/18/23,
dígito 14='4', dígito 19∈{8,9,a,b}), nunca por igualdade. `uuid.v7()` (ordem temporal,
RFC 9562) existe nos 5 targets (dígito 14='7', dígito 19∈{8,9,a,b}); ulid ainda não existe.

## random (S10a/b)

```kof
// ❌ BAD — PRNG próprio, LCG de internet
var seed = 12345
seed = (seed * 1103515245 + 12345) % 32768
```

```kof
// ✅ GOOD — entropia da plataforma, face de intenção
var roll = random.randomInt(6) + 1
var pass = random.randomString(12, "abcdefghijkmnpqrstuvwxyz23456789")
var flip = random.randomBoolean()              // sorteio de moeda
var pick = colors[random.randomInt(colors.size)]   // choice = idiom
```

**WHY:** `random.*` = sorteio (não-críptográfico); `security.*` = tokens
(rejeição + validação). A escolha de lista **não** é função da stdlib —
`list[random.randomInt(list.size)]` é o idiom; `randomChoice` exigiria
retorno Object na camada de dispatch (DD-STDLIB-01 — FECHADO 13/09, decisão
6a: `randomBytesHex` alias de `hex` + choice=idiom; `randomBytes` reservado).

## rng — determinismo TESTÁVEL (X8 fatias 1–2)

```kof
// ❌ BAD — sorteio sem seed dentro de teste (passa/falha ao acaso, CI irreproduzível)
test "soma no intervalo" {
    var a = random.randomInt(100)
    var b = rng.int(50)
}
```

```kof
// ✅ GOOD — PRNG semeado: mesma seed => mesma sequência, qualquer backend
test "soma comuta em pares aleatórios" {
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

**WHY:** `rng.*` = determinismo REPRODUZÍVEL (xorshift128 + splitmix32,
32-bit exato — mesma seed, mesmos bits na JVM e no JS,
`KofRngTest.jvmJsParity`); `random.*` = entropia do SO (R11). Um property test
que falha imprime a seed e a falha se reproduz. Misturar os dois é o
anti-padrão: semear material de segurança (violação da R11) ou sortear
entropia do rng (testes flaky). Fatia 1 = JVM + JS; NATIVE/ANDROID = gap
honesto `RNG001` em compile (asm x86_64 — mesmos bits por construção — caiu na fatia 2).

## validation — formatar NÃO é validar (S12/S12b)

```kof
// ❌ BAD — pontuar à mão, e lançar quando o CPF tem dígitos demais
var out = ""
for (var i = 0; i < cpf.length; i++) {
    out = out + cpf.charAt(i)
    if (i == 2 || i == 5) { out = out + "." }
}

// ✅ GOOD — as duas faces, cada uma no seu lugar
validation.isCpf("52998224725")     // STRICTA: false se dígitos verificação não batem
validation.formatCpf("529.982.247-25") // "529.982.247-25" — LENIENTE: só pontua
```

**WHY:** `formatCpf`/`formatCep`/`formatCnpj` **formam, não validam**: tiram
pontuação existente e reimponhem a máscara; se o número de dígitos não bate
(ou é `null`), devolvem a **entrada original** — nunca lançam, nunca truncam.
Quem decide se o documento é *válido* é a face stricta (`isCpf`/`isCnpj`/
`isCep`). Separar as duas é a regra "represente a intenção": formatar
apresentação é uma coisa, checar legitimidade é outra. O mesmo vale p/
`time.isWeekend(y,m,d)` (só calendário, sem relógio — data inválida => `false`
porque `dayOfWeek` dá 0).

## shell — comandos sem string (2.2 v1, 18/09)

RUIM: `process.run("sh -c \"echo a | wc\"")` — string de comando = classe de
injeção + paridade entre alvos quebrada.

BOM:

```kof
import kof.shell
var r = shell.run("git", listOf("status", "--short"))
if (shell.ok(r)) { println(r.stdout) }
var n = shell.pipeline(listOf(listOf("echo", "um dois"), listOf("wc", "-w"))).stdout
```

POR QUÊ: argv é **lista**, nunca string — o golden
`argvIsNeverConcatenatedIntoShellString` pinha que `"a b|c && d"` sobrevive como UM
elemento; `Result` é o MESMO tipo de `kof.process` (uma forma, nunca um fork);
exit≠0 é dado, não exceção. Faces honestas: `pipeline` = JVM-real,
JS/Native = `PROC001` em tempo de compilação (R6); doc completa em
`docs/stdlib/shell.pt_BR.md`.

## time — calendário, ISO, relógio (8.5, 19/09)

```kof
val iso  = time.todayIso()                       // "2026-09-19"
val week = time.addDays(iso, 7)                  // ISO entra, ISO sai
val gap  = time.diffDays("2026-01-01", "2026-09-19")
val dim  = time.daysInMonth(2026, 2)             // 28
val dow  = time.dayOfWeek(2026, 9, 19)
val hrs  = time.hoursBetween(2026, 9, 19, 0, 2026, 9, 19, 12)
val leap = time.isLeapYear(2026)                 // calcular LeapYear à mão: ERRADO, 18 membros existem
time.sleep(50); val t = time.now()               // epoch millis
val id = time.interval(1000, () -> println("tick"))
time.cancel(id)
```

PORQUÊ: a cara de calendário são **funções puras de String/Int** — sem objeto de data
para importar, sem matemática de esquina sua. `parseDateIso`/`formatDateIso` convertem
entre a string ISO e as partes. O hover/signatureHelp do LSP lista os 18 com aridades
reais. Paridade do interpretador nas faces novas: `KofScriptStdlibParityTest`.

## process — comandos externos, argv em varargs (8.5, 19/09)

```kof
// ❌ ERRADO — uma List aqui (essa é a forma do shell; os varargs do process recusam, SEM025)
val r = process.run("git", listOf("status"))
// ✅ comando direto: varargs de String
val r = process.run("git", "status", "--short")
if (r.exitCode == 0) { println(r.stdout) }       // exit != 0 é DADO, nunca exceção
process.exit(1)                                  // encerra com código — ensinado em learn/23-testing.md
```

PORQUÊ: `kof.process` = **comando único com argumentos que você já tem como valores**;
`kof.shell` (seção abaixo) = argv dinâmico em lista e pipeline. Mesmo `Result` nos dois
(`stdout`/`stderr`/`exitCode`). Gates honestos (medidos 19/09 + travados em
`DomainGapCodesTest`): `run`/`spawn`/`exit` = `PROC001` em **tempo de compilação no
Native**; `process.spawn`/`pipeline` sao **REAIS no JS sob o host Kof** (`KofJsRunner`, 19-20/09,
paridade byte travada); num `node` puro lancam o diagnostico honesto `kof_platform.*: not
available outside the Kof JS host`. Nunca fallback silencioso.

## cache — KV de String com TTL (8.5, 19/09)

```kof
cache.set("k", "v")                    // sem expiração: cache.ttl("k") dá -1
cache.set("session", tok, 300)         // ttl em segundos
val v = cache.get("k")
val left = cache.ttl("session")
cache.delete("k")
cache.clear()
```

PORQUÊ: deliberadamente minúsculo — KV String→String em processo. Sem cerimônia de
serialização; persistência é `kof.orm`, não flag de cache.

## config — chave + default, sem string-sentinela (8.5, 19/09)

```kof
val port = config.get("server.port")            // 1 arg: o valor como está
val name = config.str("app.name", "demo")       // str/int/long/bool levam chave + default
val url  = config.required("db.url")            // chave ausente é ERRO, nunca "" silencioso
val has  = config.has("app.name")
val home = config.env("HOME")                   // ambiente cru
```

PORQUÊ: chaves literais são descobertas **em tempo de compilação**
(`CompilerDriver.discoveredConfigKeys()` — deploy/tooling leem antes do programa rodar;
chave computada degrada para o caminho de runtime, travado em `ConfigGenTest`). O
default fica na chamada, não num `if (s == "")`.

## log — quatro níveis (8.5, 19/09)

```kof
log.debug("payload " + n)
log.info("boot")
log.warn("deprecated path")
log.error("boom: " + err)
```

PORQUÊ: `println` é **saída** do programa; `log` é **observação** — misturá-los perde o
nível. Quatro membros, `String` entra, `void` sai, sem cerimônia de formato
(concatenação já é eficiente).

## net — URL decomposta, sem regex (8.5, 19/09)

```kof
val u = "https://api.x.io:8443/v1/items?page=2#top"
net.scheme(u); net.host(u); net.port(u); net.path(u); net.query(u); net.fragment(u)
val q = net.queryEncode("a b&c")
val back = net.queryDecode(q)
```

PORQUÊ: `split("/")`/regex feito à mão em URL quebra em porta, query e fragmento —
cada peça é função real, em todos os targets (paridade do interpretador travada 19/09
em `KofScriptStdlibParityTest`).

## gpu — probe primeiro, kernels honestos (8.5 fatia 3, 19/09)

```kof
if (gpu.available()) {
    var a = new Int[4]
    var c = new Int[4]
    val rc = gpu.dispatchMatmul(a, a, c, 2, 2, 2)   // rc != 0 = o kernel disse nao, nunca resposta errada silenciosa
} else {
    println(gpu.failReason())                        // POR QUE o host nao tem face (R6)
}
```

PORQUE: computacao pesada e dominio de **pacote oficial** (R1) — `kof.gpu` expoe so o que a
plataforma ja roda (kernels de forma fixa + as faces `mv*` int8/long do caminho on-device);
frameworks de ML ficam em interop (R9). Gates honestos (medidos 19/09): JVM + Native x86 ✅;
riscv64/aarch64 ✅ e JS/Script ✅ desde 26/09 (linha 6 fechada, `D-FULL-PARITY-050`) — todos
degradam pelo fallback CPU honesto (`available=false`, dispatch `-1`), nunca `GPU001`.

## media — Image/Audio/Video/Mic sao namespaces, nao widgets de UI (8.5 fatia 3, 19/09)

```kof
val img = Image.open("photo.png")        // ImageData (pixels + largura/altura)
val wav = Audio.openWav("bell.wav")      // Audio
val clip = Video.open("intro.mp4")       // Video
val take = Mic.record(1)                 // Audio — 1 segundo do dispositivo padrao
val inputs = Mic.list()                  // dispositivos de captura disponiveis
```

PORQUE: `Image` em `kof.ui` e um **widget de visao**; `Image.open` aqui e **I/O de midia**
(mesmo nome, intencao diferente — nao confundir). Tudo que a plataforma decodifica fica no
backend; o codigo do usuario nunca toca buffer nem codec. Gates honestos (medidos 19/09):
JVM ✅; **JS e Native = `MEDIA001`** em tempo de compilacao (R6); riscv/aarch ⏳.
Nota de escopo: esta e a **face de dados** atual de `kof.media`. A superficie futura
de graficos/jogos/midia e **engine propria da Kof**, com paridade TOTAL nos 4 alvos
como criterio de aceite (`DECISIONS.md` §D-GRAPHICS-GAMING adendos 2+4; plano
`docs/development/future/graphics-gaming-plan.md`) — `MEDIA001` e honesto para a face
legada, nao o modelo do que sera promovido.

## Nota por target (gates honestos)

| função | JVM/Script | Native x86_64 | Native riscv64/aarch64 | JS |
|---|---|---|---|---|
| math.*, strings.is*/count/capitalize/uncapitalize/reverse/repeat/truncate/pad*, encoding.hex*/url*, time.isLeapYear/daysInMonth/dayOfWeek/daysBetween/isWeekend, validation.isCpf/isCnpj/isCep/isPis/isIpv4/isIpv6/isMac/isPort/isCreditCard/isDomain/formatCpf/formatCep/formatCnpj | ✅ | ✅ | ✅ | ✅ |
| strings.toCamel/Pascal/Snake/Kebab/slugify | ✅ | ✅ | ✅ (STRN001 fechado 09/09 — B15, diff golden qemu) | ✅ |
| strings.escapeHtml/escapeJson (5 entidades; >=128 cópia) | ✅ | ✅ | ✅ (B20, diff golden qemu) | ✅ |
| strings.removeWhitespace/normalizeWhitespace | ✅ | ✅ | ✅ (B21) | ✅ |
| encoding.base64* / base64Url* | ✅ | ✅ | ✅ (ENC002 fechado 09/09) | ✅ |
| net.scheme/host/port/path/query/fragment + queryEncode/Decode | ✅ | ✅ | ✅ (NET001 fechado 09/09) | ✅ |
| uuid.v4 | ✅ | ✅ | ✅ (SECN000 fechado 09/09) | ✅ |
| uuid.isUuid (forma 8-4-4-4-12; version/variant não verificadas) | ✅ | ✅ | ✅ (B25, UUID001 fechado no merge beta→main 10/09) | ✅ |
| math.sqrt (S1b — primeiro Double; NaN em <0 = IEEE) | ✅ | ✅ | ✅ (B32 `fsqrt.d`; MATH001 fechado 11/09) | ✅ |
| math.lerp/percentage/isInteger/isDecimal/roundTo (S1b.1/S1b.3 — SSE2 puro, sem libm) | ✅ | ✅ | ✅ (B32; MATH001 fechado 11/09) | ✅ |
| math.pow (S1b.2 — libm `pow@PLT` + `-lm` no x86) | ✅ | ✅ | ❌ `MATH001` (cross estático sem libc) | ✅ |
| random.randomInt/randomBoolean/randomString (face beta S10a/b) | ✅ | ✅ | ✅ (B27/B28, getrandom/lemire) | ✅ |
| random.double/boolean/int/hex (face main S10) | ✅ | ✅ | ✅ (B27) | ✅ |
| faces novas de `time.*` (todayIso/addDays/diffDays/hoursBetween/iso parse-format/sleep/now/interval) | ✅ JVM (medido 19/09, `StdlibIdiomsCompileTest`); interpretador: datas ✅ (paridade X8), relógio ⏳ | ✅ x86 (medido 19/09) | ✅ cross (B33; linha 8 fechada 25/09 — `KofTimeE2ETest` 44/44) | ✅ (medido 19/09) |
| `cache.*` / `config.*` / `log.*` (8.5) | ✅ JVM (medido 19/09); cache+config ✅ paridade no interpretador 19/09 (`KofScriptStdlibParityTest`); log ⏳ interpretador | ✅ x86 (medido 19/09) | ✅ cross (linha 9 fechada 26/09 — `KofCacheCrossTest`/`KofConfigCrossTest`/`NativeLogCrossTest`) | ✅ (medido 19/09) |
| `process.run`/`exit` (varargs) | ✅ | ❌ `PROC001` (tempo de compilação, travado em `DomainGapCodesTest`) | ❌ `PROC001` | ✅ |
| `process.spawn` | ✅ | ❌ `PROC001` | ❌ `PROC001` | ✅ host Kof JS (`KofJsRunner`); node puro = diagnostico honesto |
| `observability.*` (spans 01/09 + metrics/health 8.5 19/09) | ✅ (medido 19/09) | ✅ x86 (medido 19/09) | ✅ cross (linha 7 fechada 26/09 — `KofObservabilityTest` 12/12) | ✅ (medido 19/09) |
| `gpu.available`/`failReason`/`dispatchMatmul(Int)` | ✅ | ✅ (medido 19/09) | ✅ cross (linha 6 fechada 26/09 — fallback CPU honesto) | ✅ fallback JS/Script (linha 6; `GPU001` aposentado) |
| `Image.open`/`Audio.openWav`/`Video.open`/`Mic.record/list` | ✅ (medido 19/09) | `Video`+`Audio` ✅ (26/09 fatia 1 — `MediaNativeE2ETest` byte-for-byte vs JVM); `Image`/`Mic` ❌ `MEDIA001` | `Video` ✅ (26/09 fatia 2A — `MediaCrossE2ETest` riscv64/aarch64 byte-for-byte sob qemu); `Audio`/`Image`/`Mic` ❌ `MEDIA001` | ❌ `MEDIA001` |
| shell.cmd/run/ok (v1) | ✅ | ❌ `PROC001` (tempo de compilação) | ❌ `PROC001` | ✅ paridade byte |
| shell.pipeline (v1) | ✅ | ❌ `PROC001` | ❌ `PROC001` | ✅ host Kof JS (chain + pump, 20/09 `081a48f8`; node puro = diagnostico honesto) |

`strings.reverse` em não-ASCII: byte-reverso no Native vs UTF-16 no JVM/JS —
gap **NAT-STR01** (paridade só travada em ASCII na matriz).

## Limitações

- `charAt(i)` devolve o **código** do char (Int), não um char literal — por
  isso `padLeft` recebe pad como String.
- Predicados `strings.*` são **ASCII**: acentos => false (decisão travada na
  matriz `stdstrings`, não bug).
- `encoding.hexDecode`/`base64Decode` são **tolerantes por especificação**
  (mesmo comportamento nos 4 backends); se você precisa rejeitar entrada
  inválida, valide antes (`strings.isNumeric`/`isAlphaNumeric`).
