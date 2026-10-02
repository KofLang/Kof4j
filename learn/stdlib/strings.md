[English](strings.md) | [Português](strings.pt_BR.md)

# kof.strings — predicates, converters and words

> **Status: stable on JVM/Script, Native x86-64, JS; cross/corner faces tracked in the parity ledger** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|
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

> Parity: `reverse` is code-point correct on all targets (native walks UTF-8 bytes, 27/09); the regex trio (`matches`/`replaceAll`/`replaceFirst`) = `STR003` (deferred to 1.0); `toUpperCase`/`toLowerCase` are Unicode per code unit on all targets (27/09, `D-STR-UNICODE`) and `compareToIgnoreCase` matches the JVM `CASE_INSENSITIVE_ORDER` (simple fold per code unit, 28/09); `capitalize`/word-converters remain `NAT-STR01` — ledger row 11.

**See also:** [39 — Universal Standard Library](../39-stdlib.md) — the full story and the honest parity table.
