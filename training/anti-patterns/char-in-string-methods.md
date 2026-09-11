# char literal em método String com formal String (bugs 99/100)

**Name:** char literal (ou Int) como argumento de `String` method que espera
String.

**Problem:** o char literal do Kof **é** `Int` (não existe tipo char
separado). Métodos String como `indexOf`/`contains`/`lastIndexOf`/
`startsWith`/`endsWith` esperam **String** no 1º argumento — o registry
resolve por aridade, então `'c'` atravessava e cada backend quebrou de um
jeito (JVM `VerifyError`, Native SIGSEGV, JS `-1` silencioso, interpretador
`ClassCastException`). Agora é erro de compilação. A rejeição foi
generalizada (qualquer não-String — Int/Long/Double/coleção — em formal
String, nos métodos `indexOf`/`lastIndexOf`/`contains`/`startsWith`/
`endsWith`/`split`/`concat`/`equalsIgnoreCase`/`compareTo`/
`compareToIgnoreCase`) sob o código dedicado **SEM051**.

**Bad (não compila — SEM051):**
```kof
var s = "abc"
s.indexOf('c')      // ❌ SEM051: "String.indexOf não aceita Char como argumento 1"
s.contains('b')     // ❌ idem
s.lastIndexOf('c')  // ❌
s.startsWith('a')   // ❌
s.endsWith('c')     // ❌
var n = 42
s.indexOf(n)        // ❌ Int também (o tipo importa, não a forma)
```

**Preferred:**
```kof
var s = "abc"
s.indexOf("c")      // ✅ 1
s.contains("b")     // ✅ true
s.lastIndexOf("c")  // ✅ 2
s.startsWith("a")   // ✅ true
s.endsWith("c")     // ✅ true
```

**Why:** o idiom é unívoco — a API documentada (`type-system.md`) usa String;
o overload char de `java.lang.String` não é superfície do Kof. Rejeitar no
compile (R6 — nunca o "compila e quebra") em vez de converter Int→String em
silêncio: definir a semântica (byte? code unit? code point?) de um char Kof
num formal String seria mudança de contrato — decisão da mantenedora.

**Exceção (continua válida):** `replace(char, char)` — o registry tipa os 2
formais como `CHAR` quando os args são char, e o idiom `s.replace('a', 'b')`
é aceito em todos os backends.
