# 39 — Standard Library universal (math, strings, encoding, uuid, validation, time)

> **Kof 0.3.0-beta — `intention->Kof->frontend->IR->backend->runtime`**

A Standard Library do Kof existe para uma coisa: **você nunca reimplementar o
óbvio**. Matemática de uso comum, predicados e conversores de string,
codificação (hex/base64/url), UUID, validação (documentos BR, rede, cartão)
e calendário civil — tudo chamado por **intenção**, com o mesmo resultado no
JVM, no interpretador KofScript, no JS e no binário nativo (x86_64, riscv64,
aarch64).

```kof
var phone = "1234"
var gateway = "192.168.0.1"
if (strings.isNumeric(phone)) {          // intenção, não loop de char
    println(validation.isIpv4(gateway))  // mesma API em todo target
}
```


> Regra do projeto: se parece Java traduzido, está errado. Loop de bytes
> manual para validar CPF é o anti-pattern; `validation.isCpf(...)` é o
> idiom. O "por quê" completo (BAD/GOOD/WHY) está em
> `training/idioms/stdlib.md`.

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
```

Todos `Int`/`Bool` inteiros — sem ponto flutuante aqui (`math.lerp`/
`roundTo` ficam em degrau próprio, com as mesmas garantias).

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
```

Não-determinístico por natureza: os testes travam **forma**, não igualdade.

## validation — documentos BR, rede e cartão

```kof
validation.isCpf("52996581504")          // true  — dígitos extraídos
validation.isCpf("529.982.247-25")       // true  — pontuação ignorada
validation.isCnpj("34546401000163")      // true  // CPF/CNPJ: mod-11 com
validation.isCep("01310-100")            // true  //   dígito verificador
validation.isPis("12345678900")          // true

validation.isIpv4("192.168.0.1")         // true  — dotted-quad
validation.isIpv4("01.2.3.4")            // false — zero à esquerda não vale
validation.isMac("00:1A:2B:3C:4D:5E")    // true  — ':' ou '-', consistente
validation.isPort(443)                   // true  — 1..65535
validation.isCreditCard("4532 0151 1283 0366") // true — Luhn, 12..19 dígitos
validation.isIpv6("fe80::1")                 // true  — subconjunto RFC 5952
validation.isIpv6("::ffff:192.168.0.1")      // false — forma mista não na v1
```

> O que **não** é validação de conteúdo: `validation.min/max` (já existiam,
> G4) comparam números; os predicados acima checam formato + checksum.

## time — calendário civil (sem relógio, sem timezone)

```kof
time.isLeapYear(2024)                 // true   — Gregório (%4, %100, %400)
time.daysInMonth(2024, 2)             // 29
time.dayOfWeek(2026, 9, 9)            // 3      — ISO: 1=segunda..7=domingo
time.daysBetween(2024, 1, 1, 2024, 3, 1) // 60  — pode ser negativo
```

Domínio **1..9999** (serial civil cabe em Int; fora disso ou data inexistente
→ `isLeapYear=false` / `0`). A função `now`/`sleep`/`interval` de relógio é
de `time` desde antes — o calendário acima é a parte pura, determinística.

## Paridade por target (tabela honesta)

| API | JVM / Script | Native x86_64 | Native riscv64 / aarch64 | JS |
|---|---|---|---|---|
| `math.*`, `strings.is*/count/capitalize/reverse/repeat/truncate/pad*`, `toCamel/Pascal/Snake/Kebab/slugify`, `encoding.hex*/url*`, `time.isLeapYear/daysInMonth/dayOfWeek/daysBetween`, `validation.isCpf/isCnpj/isCep/isPis/isIpv4/isIpv6/isMac/isPort/isCreditCard` | ✅ | ✅ | ✅ | ✅ |
| `encoding.base64*` / `base64Url*` | ✅ | ✅ | **ENC002** (gate de compilação) | ✅ |
| `uuid.v4` | ✅ | ✅ | **SECN000** (entropia; gate de compilação) | ✅ |

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
- `docs/stdlib.md` §3 — a matriz de referência com gates.
- `docs/development/plan-stdlib-expansion.md` — o que falta: `random` (P0),
  `isDomain` (design aberto — regra de rótulo é decisão, não codar unilateral), `net`/`url` (S8).
