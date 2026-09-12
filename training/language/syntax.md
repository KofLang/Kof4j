# Kof Syntax Reference

**Version:** 0.3.22-beta (Sep 2026)

## Declarations

### Package
```kof
package com.example
```

### Import (fix 0.2.6-beta: file-specific)

```kof
import a.b.C          // arquivo a/b/C.kf — fix 27/08 CompilerDriver expandKofImports
import a.b.*          // diretório a/b
import kof.http
```

Projetos grandes com `import a.b.C` agora geram `Main.class` + `a/b/C.class` corretamente. Evitar `import java.util.List` — use `listOf`/`List<T>` da stdlib.

### Function
```kof
add(Int a, Int b): Int {
    return a + b
}
main() {
    println("oi")
}
String saudacao() { return "oi" }
despedida(): String { return "tchau" }
Bool positivo(Int x) = x > 0
```

Sem `fun` keyword.

### Class
```kof
class User(String name, Int age) {
    greeting(): String { return "Hello " + name }
}
// verboso ainda válido
class User2 {
    String name
    public constructor(String name) {
        this.name = name
    }
}
```

### Record
```kof
record Point(Int x, Int y)
var p = Point(10, 20)
```

### Enum

```kof
enum Name { A, B, C }
```

`values()` / `valueOf("A")` / `name()` + exhaustive switch SEM031. See Types.

### Interface
```kof
interface Speaker {
    speak(): String
}
```

### Variable
```kof
var x = 10
val y = 20
String name = "Mel"
String? maybe = null        // nullable
Box<Int> b = Box(42)        // generics com primitivo
```

### Nullable (0.3.22-beta)

```kof
String? s = null
if (s != null) {
    println(s.length)   // narrowing
}
String t = s            // erro SEM014 se sem check
```

### KofScript top-level let (0.3.22-beta)
```kof
let x = 5
const y: Int = 10
// → KofScriptGlobals static fields + rewriting
```

### Pattern matching (0.3.22-beta)

```kof
switch (obj) {
    case String s:
        println(s)
        break
    case Point(var x, var y):
        println(x + "," + y)
        break
    default:
        println("outro")
}
if (p instanceof Point) {
    var q = p as Point
}
```

### Spawn / Await

```kof
spawn expr();            // fire-and-forget (JVM virtual thread / Native pthread / JS sequencial)
val r = spawn expr();    // Handle<T> typed handle
val v = await r;         // blocks; T (primitives unboxed)
```

3 targets: JVM virtual threads, Native pthread (CONC001 fechado 31/08), JS sequencial (CONC003 parcial). Android: AND001.

### kof.http (0.3.22-beta)

```kof
var html = http.get("https://example.com")
var resp = http.post(api, json.encode(body), "Content-Type: application/json")
if (http.status(url) == 404) { }
http.timeout(30)         // timeout global (ms)
http.retry(3)            // repete em exceção + HTTP 5xx
http.circuit(5)          // abre circuito após N falhas por 30s; circuit(0) recupera
```

Métodos: `get/post/put/delete/patch/options/status` + resiliência `timeout/retry/circuit`. JVM + JS (Java HttpClient interop); Native HTTP002.

## Statements

### If/Else
```kof
if (x > 0) {
    println("positive")
} else {
    println("non-positive")
}
var status = if (ativo) "online" else "offline"
```

### While / Do-While / For
```kof
while (i < 10) { println(i); i = i + 1 }
do { println(i); i = i + 1 } while (i < 10)
for (var i = 0; i < 10; i++) { println(i) }
for (var x in listOf(1,2,3)) { println(x) }
```

### Try/Catch
```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} finally {
    println("done")
}
```

## Expressions

### Arithmetic / Comparison / Logical
```kof
a + b; a - b; a * b; a / b; a % b
a == b; a != b; a < b; a > b; a <= b; a >= b
a && b; a || b; !a
```

### String Concatenation
```kof
"Hello" + " World"
```

### Array Access
```kof
var arr = new Int[5]
arr[0] = 10
println(arr[0])
println(arr.length)
```

### Collections higher-order

```kof
var dobrados = listOf(1,2,3).map((x: Int) -> x * 2)
var pares = listOf(1,2,3).filter((x: Int) -> x % 2 == 0)
var soma = listOf(1,2,3).reduce((a: Int, b: Int) -> a + b, 0)
```

### Method Call
```kof
var s = "Hello"
println(s.length)
println(s.charAt(0))
println(s.substring(1, 3))
```
