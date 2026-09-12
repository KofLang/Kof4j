# Anti-pattern — Runtime Workarounds

## Name

Tratar workarounds temporários como regras da linguagem.

## Problem

Quando uma feature ainda não existe, o código precisa de um desvio.
O desvio é legítimo — mas **não é idiom**. O corpus deve marcar
explicitamente `WORKAROUND` e `NOT IDIOMATIC`.

## Workarounds atuais (histórico 0.2.6-beta; fronteira atual em fake-idioms.md)

### 1. Null safety parcial

```kof
// ✅ String? / Int? implementado com narrowing
String? s = null
if (s != null) {
    println(s.length)   // OK — narrowing via isAssignable
}
// Option<T> genérico ainda é planned — use String? para nulabilidade simples
// WORKAROUND até Option<T>: exceção ou record Found(Bool ok, T value)
```

**Não aprenda Option<T> como idiom — use String?.**

### 2. JSON — RESOLVIDO (31/08)

```kof
// ✅ JSN001/JSN002/JSN003 fechados 31/08: json.encode/json.decode<T> de
// objetos/records e arrays (Int/Long/Bool/String/Double) funciona nos
// 3 targets — composição compile-time no Native, FP real em XMM.
var j = json.encode(Point(3, 4))
var d = json.decode<Point>(j)
var l = json.decode<Int[]>("[1,2,3]")
```

Não use mais workaround de JSON (não codificar objeto no Native, trocar
Double por Int) — a feature fechou.

### 3. Construtor com argumentos — RESOLVIDO (0.3.22-beta)

```kof
// ✅ Primary constructor é a forma idiomática desde 0.0.5
class User(String name, Int age) { }
var u = User("Mel", 30)   // sem new também OK
// Forma verbosa ainda válida mas não idiomática
```

Não use `// WORKAROUND` para construtor — é feature estável.

### 4. Captura em lambdas — RESOLVIDO (0.3.22-beta)

```kof
var offset = 10
var f = (x: Int) -> x + offset   // ✅ OK — captura mutável via box sintético BoxN
println(f(5))   // 15
```

Não marque captura como workaround — é implementado.

### 5. Imports de projeto grande — RESOLVIDO (27/08)

```kof
// ✅ CompilerDriver expandKofImports agora trata import a.b.C (arquivo) + a.b (pasta)
// Projeto largeproj com a/b/C.kf → Main.class + a/b/C.class corretos
import a.b.C
import a.b.*
```

Não é necessário workaround manual de imports.

### 6. List.get / listOf — RESOLVIDO (27/08)

```kof
var l = listOf(1, 2, 3)
var x = l.get(1)   // 2 — kof_list_get com bounds, sem handling manual
```

Não implemente bounds check manual — a stdlib já faz.

### 7. Threads no Native — RESOLVIDO (31/08)

```kof
// ✅ CONC001 fechado 31/08: spawn/await nativo via pthread
spawn work()
val r = spawn compute()
var v = await r
// FP nativo (FLT001) também fechado 31/08: float/double real em XMM
var d = 1.5 * 2.0
```

Não marque spawn/await no Native nem FP como WORKAROUND — é implementado.

### 8. GC nativo — em progresso

```kof
// Native usa free-list first-fit thread-safe (lock futex) + kof_gc_collect
// (mark-sweep conservador). Auto-GC está desligado (27/08): memória só é
// devolvida no munmap fallback — GC mark-sweep automático ainda pendente.
// Ainda não é GC completo de produção — programas longos devem evitar vazamento
```

### 9. Correções de 01/09 (compiler bugs fechados)

```kof
// ✅ Frame crash COMP002 com List.add em statement + while — RESOLVIDO 01/09
// (emit do kof_list_add popava o boolean 2×; agora o KofPop do IR cuida)
var c = listOf<Box>()
c.add(Box(7))
var i = 0
while (i < n) {
    var ent = c.get(i)   // ✅ compila e roda (era frame crash antes)
    i = i + 1
}

// ✅ Receiver estático de tipo builtin — RESOLVIDO 01/09
var s = String.valueOf(42)      // "42" (invokestatic String.valueOf)
var i2 = Integer.valueOf("17")  // idem para Integer/Long/Double/…

// ✅ Casts primitivos reais — RESOLVIDO 01/09
var ch = 104 as Char            // I2C real (era checkcast "?" → VerifyError)
var trunc = longVal as Int      // L2I real (narrowing Long→Int)
```

Não re-aplique workarounds para esses casos (ex.: evitar `List.add` antes
de `while`, ou concatenar dígitos manualmente em vez de `String.valueOf`).

## Regra

1. Todo workaround no corpus carrega o rótulo `WORKAROUND`.
2. Todo workaround cita a feature planned que o substituirá.
3. Nunca apresente workaround como exemplo de "código idiomático".

## Quando um workaround deixa de ser workaround

Quando a feature é implementada, o exemplo oficial é atualizado e o rótulo
removido. O histórico conceitual é preservado no CHANGELOG, não no corpus.
Em 0.2.6-beta foram removidos: captura lambda, imports a.b.C, List.get, primary constructor,
JSON nativo (JSN001/002/003, 31/08), threads no Native (CONC001, 31/08) e FP nativo (FLT001, 31/08).

## Exceptions

- Nenhuma: workarounds são sempre temporários por definição.
