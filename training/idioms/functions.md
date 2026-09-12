# Idioms — Functions

**Status:** available · **Introduced:** 0.0.4-alpha (sem `fun`) · **Updated:** 0.3.22-beta (Sep 2026)

## What it is

Kof não possui a palavra-chave `fun`. Funções são declaradas pelo nome,
com o tipo de retorno antes do nome **ou** após os parâmetros.

## Formas válidas (todas verificadas no compilador)

```kof
main() {
    println("entry point")
}
```

```kof
String saudacao() {
    return "oi"
}
```

```kof
despedida(): String {
    return "tchau"
}
```

```kof
void fazIsso() {
    println("void explícito")
}
```

```kof
Bool positivo(Int x) = x > 0      // expression body
```

```kof
int dobro(int x) {
    return x * 2
}
```

## When to use funções top-level

- Lógica sem estado (helpers, validação, transformação).
- Utility classes de Java viram funções top-level.
- Handlers de `kof serve` (`handle(...)`) são funções top-level.

## When not to use

- Dados + comportamento → classe ou record.
- `main()` é a única função sem tipo explícito e sem retorno.

## Sobrecarga de função top-level (0.4.0-beta — oracle JVM)

Funções top-level homônimas com **assinaturas diferentes** coexistem; a
chamada resolve o candidato aplicável mais específico, como a JVM.

```kof
Int g(Int x) { return x }
Int g(Int x, Int y) { return x + y }        // ✅ aridade diferente
String twice(String s) { return s + s }
Int twice(Int n) { return n * 2 }            // ✅ tipo de parâmetro diferente

main() {
    println(g(5))          // 5   → g/Int
    println(g(5, 6))       // 11  → g/Int,Int
    println(twice("ab"))   // abab
    println(twice(21))     // 42
}
```

- **Duplicata exata é erro** (SEM047): mesmo nome + mesmos parâmetros.
- **Só trocar o retorno NÃO é sobrecarga** (SEM047, como na JVM): `Int h(Int)`
  e `String h(Int)` colidem.
- **Chamada ambígua é erro** (SEM057): quando dois candidatos aplicáveis
  empatam (ex.: argumento `Unknown` que caberia em ambos), dê um tipo ao
  argumento (cast ou variável declarada) para escolher.
- Mesma saída nos 5 targets (JVM/Script/JS/Native): a resolução é do frontend;
  cada backend referencia o candidato pela assinatura.
- **Sobrecarga de MÉTODO de classe ainda não existe** (§131, aberto) — isto é
  só função top-level.

## BAD — utility class

```kof
class StringUtils {
    static String capitalizar(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1)
    }
}
```

## GOOD — função top-level

```kof
String capitalizar(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1)
}
```

## WHY

A utility class de Java existe porque Java não tem funções fora de classes.
Kof tem funções top-level. A camada extra de classe é ruído.

## Lambdas (captura implementada)

```kof
var f = (x: Int) -> x * 2
println(f(21))          // 42

var g = (a: Int, b: Int) -> a + b
println(g(3, 4))        // 7

var h = () -> 99
println(h())            // 99

// Captura mutável — ✅ desde 0.2.6-beta via box sintético Box0
var offset = 10
var f2 = (x: Int) -> x + offset
println(f2(5))          // 15
offset = 20
println(f2(5))          // 25 — mutável

// Higher-order com List
var dobrados = listOf(1, 2, 3).map((x: Int) -> x * 2)
var pares = listOf(1, 2, 3, 4).filter((x: Int) -> x % 2 == 0)
```

- Lambdas compilam para classes sintéticas com método `invoke`.
- Captura mutável via `BoxN` — sem limitação.
- `Box<T>` erasure fix permite `Box<Int>` com primitivos.

## BAD — utility class para transformação

```kof
class ListUtils {
    static List<Int> dobrar(List<Int> l) {
        var r = listOf<Int>()
        for (var x in l) { r.add(x * 2) }
        return r
    }
}
```

## GOOD — higher-order

```kof
var dobrados = nums.map((x: Int) -> x * 2)
```

## WHY (captura)

Captura era planned antes de 0.2.6-beta; hoje está implementada — usar lambdas com parâmetros, literais e capturas livremente.

## Anti-patterns relacionados

- `java-like-code.md` — utility classes
- `unnecessary-abstraction.md` — factory/wrapper
- `fake-idioms.md` — conferir status de higher-orders
