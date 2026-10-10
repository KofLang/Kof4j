[English](strings.md) | [Português](strings.pt_BR.md)

# kof.strings — predicados, conversores e palavras

> **Status: estável em JVM/Script, Native x86-64, JS; cross/corner faces rastreada no ledger de paridade** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|

| Função | Forma |
|--------|-------|
| `isAlpha` | `isAlpha(String s) -> Bool` |
| `isNumeric` | `isNumeric(String s) -> Bool` |
| `isAlphaNumeric` | `isAlphaNumeric(String s) -> Bool` |
| `isAscii` | `isAscii(String s) -> Bool` |
| `isUpperCase` | `isUpperCase(String s) -> Bool` |
| `isLowerCase` | `isLowerCase(String s) -> Bool` |
| `count` | `count(String s, String needle) -> Int` |
| `capitalize` | `capitalize(String s) -> String` |
| `uncapitalize` | `uncapitalize(String s) -> String` |
| `reverse` | `reverse(String s) -> String` |
| `toCamelCase` | `toCamelCase(String s) -> String` |
| `toPascalCase` | `toPascalCase(String s) -> String` |
| `toSnakeCase` | `toSnakeCase(String s) -> String` |
| `toKebabCase` | `toKebabCase(String s) -> String` |
| `slugify` | `slugify(String s) -> String` |
| `escapeHtml` | `escapeHtml(String s) -> String` |
| `unescapeHtml` | `unescapeHtml(String s) -> String` |
| `escapeJson` | `escapeJson(String s) -> String` |
| `removeWhitespace` | `removeWhitespace(String s) -> String` |
| `normalizeWhitespace` | `normalizeWhitespace(String s) -> String` |
| `dedent` | `dedent(String s) -> String` |
| `repeat` | `repeat(String s, Int n) -> String` |
| `truncate` | `truncate(String s, Int n) -> String` |
| `indent` | `indent(String s, Int n) -> String` |
| `padLeft` | `padLeft(String s, Int n, String pad) -> String` |
| `padRight` | `padRight(String s, Int n, String pad) -> String` |
```
```kf
strings.capitalize("kof")   // Kof
strings.toSnakeCase("toCamelCase")  // to_camel_case
strings.slugify("Hello World!")     // hello-world
strings.truncate("abcdef", 4)       // abcd…
```
```

> Paridade: `reverse` é correto por code point em todos os targets (o Native percorre os bytes UTF-8, 27/09); o trio de regex (`matches`/`replaceAll`/`replaceFirst`) = `STR003` (adiado p/ 1.0); `toUpperCase`/`toLowerCase` são Unicode por code unit em todos os targets (27/09, `D-STR-UNICODE`) e `compareToIgnoreCase` casa com o `CASE_INSENSITIVE_ORDER` do JVM (fold SIMPLES por code unit, 28/09); `capitalize`/conversores de palavra seguem `NAT-STR01` — ledger linha 11.

**Veja também:** [39 — Standard Library universal](../39-stdlib.pt_BR.md) — a história completa e a tabela honesta de paridade.
