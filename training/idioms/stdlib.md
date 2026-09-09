# Idioms — STDLIB (math / strings / encoding / uuid)

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
```

Double (lerp/roundTo/sqrt/pow) ainda não existe — é S1b (FP no asm riscv é
caro; FLT001 parcial). **Não invente** `math.sqrt` hoje: não compila.

## strings — predicados e conversores (S2)

```kof
strings.isAlpha("Hello")           // só letras, não-vazio; "abc123" => false
strings.isNumeric("123")  strings.isAlphaNumeric("abc123")
strings.isAscii("ola")             // bytes >=128 => false (café => false nos 4 targets)
strings.isUpperCase("HELLO")       // >=1 letra e nenhuma minúscula; "123" => false
strings.isLowerCase("abc-123")     // demais chars ignorados
strings.count("aabaabaa", "ab")    // 2 — NÃO-sobrepostas; sub vazio => 0
strings.capitalize("hello")        // "Hello" (ASCII; 1º byte a-z)
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
```

Não-determinístico: valide pelo **shape** (traços em 8/13/18/23, dígito 14='4',
dígito 19∈{8,9,a,b}), nunca por igualdade. v7/ulid ainda não existem.

## Nota por target (gates honestos)

| função | JVM/Script | Native x86_64 | Native riscv64/aarch64 | JS |
|---|---|---|---|---|
| math.*, strings.is*/count/capitalize/reverse/repeat/truncate/pad*, encoding.hex*/url* | ✅ | ✅ | ✅ | ✅ |
| strings.toCamel/Pascal/Snake/Kebab/slugify | ✅ | ✅ | **STRN001** (gate compile-time) | ✅ |
| encoding.base64* / base64Url* | ✅ | ✅ | **ENC002** (gate compile-time) | ✅ |
| uuid.v4 | ✅ | ✅ | **SECN000** (sem getrandom no asm puro) | ✅ |

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
