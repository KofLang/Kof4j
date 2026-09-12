# Idioms — Strings

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.3.22-beta (Sep 2026)

## What it is

String é um tipo primário da linguagem: concatenação com `+`, comparação de
conteúdo com `==`/`!=`, e uma API de métodos direta.

## Operações (verificadas)

```kof
var s = "Hello World"
s.length                    // 11 (propriedade; s.length() também é aceito)
s.charAt(1)                 // 'e' como valor numérico (101)
s.substring(6)              // "World"
s.substring(0, 5)           // "Hello"
s.contains("World")
s.startsWith("Hello")
s.endsWith("orld")
s.indexOf("W")              // 6
s.toUpperCase()
s.toLowerCase()
s.trim()
s.equalsIgnoreCase("hello world")
s.split(" ")                // String[]
var a = "x"
var b = "y"
a == b                      // comparação de CONTEÚDO (não referência)
a + "!"                     // concatenação
```

## String.valueOf + concat (corrigido 01/09)

```kof
// ✅ receiver estático de tipo builtin funciona (String/Integer/Long/
// Float/Double/Boolean/Char/Math/System):
var s = "n=" + String.valueOf(42)
var c = String.valueOf(104 as Char)   // "h" — codepoint→caractere

// ⚠️ ATENÇÃO: String.valueOf(int) retorna DÍGITOS ("104"), não o caractere.
// Para o caractere: String.valueOf(x as Char)
```

- Concat com mistura de tipos (`str + Int + Long + Double + Float + char`) é
  suportado; o compilador boxa e chama `valueOf` no ponto certo (fixes:
  COMP002 01/09; **`"str" + double` descartava o operando FP → saída vazia,
  corrigido 02/09**).
- Não construa conversão manual dígito-a-dígito — use `String.valueOf`.

## BAD — equals de Java

```kof
if (nome.equals("Mel")) {
    ...
}
```

## GOOD

```kof
if (nome == "Mel") {
    ...
}
```

## WHY

Em Kof, `==` em strings compara conteúdo. O `.equals()` de Java existe porque
Java não pode sobrecarregar `==`. Kof não tem essa limitação.
Use `==` — é a intenção.

## BAD — concatenação manual em loop

```kof
var result = ""
for (var item in items) {
    result = result + item + ","
}
```

## GOOD

```kof
var result = ""
for (var item in items) {
    result += item + ","
}
```

Ou, quando a sequência é pequena, `listOf(...).toString()`-like ou concat direto:

```kof
var saudacao = "ola " + nome + "!"
```

## WHY

`+` já é concatenação de strings. Não há necessidade de `StringBuilder` manual —
e **não existe** uma classe StringBuilder na linguagem (não invente uma).

## Nota por target (`STR001` — gap cross-target documentado)

- No Native, `length` conta **bytes UTF-8** de uma string imutável.
- No JVM, `length` conta unidades UTF-16 (comportamento padrão do `java.lang.String`).

Para strings com acentos/emoji os valores divergem (`"Olá".length` = 4 no Native,
3 no JVM). É um gap **conhecido e explícito** (código `STR001` em
`docs/backend-parity.md`): use `length` para tamanho bruto; não assuma contagem
de caracteres quando o target importa.

## Null safety (0.3.22-beta)

```kof
String? s = null
if (s != null) {
    println(s.length)   // narrowing OK — propriedade E métodos (s.substring(...))
}
// s.length sem check → erro SEM014
```

> **02/09:** narrowing de `String?` no JVM corrigido — antes `s.length`/`s.substring(...)`
> com narrowing emitiam `getfield "?".length`/`"".substring` (bytecode inválido →
> `ClassFormatError`/erro de launcher). Agora roda nos 3 targets
> (`NullSafetyE2ETest`).

## Limitações

- `replace` é **somente** `replace(Char, Char)` (códigos numéricos de caractere).
- `split` retorna `String[]`.

## Anti-patterns relacionados

- `sentinel-values.md` — use `String?` em vez de `""` para "não encontrado" (0.3.22-beta)