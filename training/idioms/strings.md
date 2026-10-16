[English](strings.md) | [Português](strings.pt_BR.md)

# Idioms — Strings

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:**  0.5.0-beta (Sep 2026)

## What it is

String is a primary type of the language: concatenation with `+`, content
comparison with `==`/`!=`, and a direct method API.

## Operations (verified)

```kof
var s = "Hello World"
s.length                    // 11 (property; s.length() is also accepted)
s.charAt(1)                 // 'e' (a Char; prints as the character, `101` via `as Int`)
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
s.toCharArray()             // Char[] — UTF-16 code units (astral char = 2 elements)
var a = "x"
var b = "y"
a == b                      // CONTENT comparison (not reference)
a + "!"                     // concatenation
```

## String.valueOf + concat (fixed 01/09)

```kof
// ✅ static receiver of a builtin type works (String/Integer/Long/
// Float/Double/Boolean/Char):
var s = "n=" + String.valueOf(42)
var c = String.valueOf(104 as Char)   // "h" — codepoint→character

// ⚠️ ATTENTION: String.valueOf(int) returns DIGITS ("104"), not the character.
// For the character: String.valueOf(x as Char)
```

- Concat with mixed types (`str + Int + Long + Double + Float + char`) is
  supported; the compiler boxes and calls `valueOf` at the right point (fixes:
  COMP002 01/09; **`"str" + double` discarded the FP operand → empty output,
  fixed 02/09**).
- Do not build manual digit-by-digit conversion — use `String.valueOf`.
- **`Math` and `System` are NOT receivers** (measured 15/10 at the tip: both give
  `SEM011 Undefined variable or type`). Arithmetic helpers live in the `math`
  NAMESPACE — `math.max(3, 4)`, `math.abs(-3)` (`learn/stdlib/math.md`) — and there
  is no `System.*` surface at all (output is `println`). This line used to list both
  among the builtin static receivers — corrected 15/10 (#851); the negative is now
  locked by `BuiltinStaticReceiverSurfaceE2ETest`.

## BAD — Java's equals

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

In Kof, `==` on strings compares content. Java's `.equals()` exists because
Java cannot overload `==`. Kof does not have that limitation.
Use `==` — it is the intent.

## BAD — manual concatenation in a loop

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

Or, when the sequence is small, `listOf(...).toString()`-like or direct concat:

```kof
var saudacao = "ola " + nome + "!"
```

## WHY

`+` is already string concatenation. There is no need for a manual `StringBuilder` —
and there **is no** StringBuilder class in the language (do not invent one).

## Note per target (`STR001` — documented cross-target gap)

- On Native, `length` counts **UTF-8 bytes** of an immutable string.
- On the JVM, `length` counts UTF-16 units (standard `java.lang.String` behavior).

For strings with accents/emoji the values diverge (`"Olá".length` = 4 on Native,
3 on the JVM). It is a **known and explicit** gap (code `STR001` in
`docs/backend-parity.md`): use `length` for raw size; do not assume character
count when the target matters.

## Null safety (0.5.0-beta)

```kof
String? s = mapOf("k", "abc").get("k")   // null reaches T? via API (= null literal is SEM048)
if (s != null) {
    println(s.length)   // narrowing OK — property AND methods (s.substring(...))
}
// s.length without a check → error SEM049 (SG-005 fixed 10/09)
```

> **02/09:** narrowing of `String?` on the JVM fixed — before, `s.length`/`s.substring(...)`
> with narrowing emitted `getfield "?".length`/`"".substring` (invalid bytecode →
> `ClassFormatError`/launcher error). Now it runs on the 3 targets
> (`NullSafetyE2ETest`).

## Limitations

- `replace` is **only** `replace(Char, Char)` (numeric character codes).
- `split` returns `String[]`.

## Related anti-patterns

- `sentinel-values.md` — use `String?` instead of `""` for "not found" (0.5.0-beta)
