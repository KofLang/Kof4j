[English](collections.md) | [Português](collections.pt_BR.md)

# Idioms — Collections

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:**  0.5.0-beta (Sep 2026) (02 Sep 2026)

## What it is

`List<T>` é a coleção ordenada da linguagem. Criação: `listOf(...)` ou `new List<T>()`.
Disponível em JVM (ArrayList), Native (implementação própria com free-list GC) e JS (Array) com a mesma API.
`Map<K,V>` e `Set<T>` existem desde 0.1.0 nos 3 targets (JVM HashMap/HashSet, Native asm próprio, JS Map/Set).

## API real (verificada no compilador — 0.5.0-beta)

```kof
var l = listOf(1, 2, 3, 4)
l.add(5)
var x = l.get(0)        // bounds check nativo via kof_list_get — sem workaround manual
l.set(0, 9)
l.size                  // propriedade, não método
l.contains(3)
l.isEmpty()
var r = l.remove(1)       // remove por ÍNDICE (Int), devolve o elemento
// NUNCA l.remove("x") (by-value do Java): SEM055 (bug 122) — para achar por
// valor use contains(x); para achar POSICAO, indexOf(x) (0.4.0, #382).
l.clear()
var vazio = listOf<Int>()

// Higher-order (3 targets)
var dobrados = l.map((x: Int) -> x * 2)
var pares = l.filter((x: Int) -> x % 2 == 0)
var soma = l.reduce((a: Int, b: Int) -> a + b, 0)   // ordem: (lambda, init)
// `reduce(0, (a, b) -> ...)` (init, lambda) também é aceito

// Map / Set
var m = mapOf("a", 1)
m.put("b", 2)
var v = m.get("a")
var n = m.getOrDefault("b", 0)   // padrao quando a chave nao existe (0.4.0, 4 alvos)
var s = setOf(1, 2, 3)
s.add(4)
s.contains(2)
// Coleções Kof são HOMOGÊNEAS: depois que o tipo PINA, add/put/set com tipo ≠
// é rejeitado em compile-time (SEM056, bug 126 — não é só o Native que quebrava:
// no JVM o add heterogêneo já dava VerifyError). Widening numérico (Int em
// List<Long>) e o PRIMEIRO add (que pina um listOf()) passam. Buscar por tipo ≠
// (m.get(5) num Map<String,Int>, s.contains("x") num Set<Int>) é MISS SEGURO
// (null/false), nunca erro — só a ESCRITA é checada.

// Como campo de classe, param de construtor e retorno de método (3 targets — 01/09)
class Bag(Set<Int> tags) {
    Set<Int> all() {
        return tags
    }
}
var b = Bag(setOf(1, 2, 3))
println(b.all().size())
```

Fix 01/09: `Set<T>`/`Map<K,V>` como campo/retorno de classe no JVM — o mapper mapeava só `List`→`ArrayList` (então `Set`/`Map` viravam `Lkof/Set;` → `NoClassDefFoundError`); agora `HashSet`/`HashMap`. Parser: método de classe com retorno genérico (`Set<Int> all(`) agora parseia (antes caía no ramo de campo). `KofMapSetTest.setMapAsFieldAndReturn`.

## Buscar, cortar e ordenar (0.4.0 — #382/#386, 4 alvos)

```kof
val l: List<Int> = listOf(3, 1, 2, 1)
var i = l.indexOf(1)          // primeira ocorrencia, -1 quando ausente
var j = l.lastIndexOf(1)      // ultima ocorrencia, -1 quando ausente
val mid = l.subList(1, 3)     // [inicio, fim) — copia; inicio==fim da vazia
val all = l.subList(0, l.size)
val head = l.take(2)          // os 2 primeiros — clampeado: take(9) = lista inteira
val tail = l.drop(1)          // tudo apos o 1o — drop(9) = vazia
val win = l.slice(1, 2)       // 2 itens a partir do offset 1 — clampeado; offset>size = vazia
var grew = mid.addAll(l)      // true quando a lista mudou (false: fonte vazia)
val ordered: List<Int> = listOf(5, 4, 3)
ordered.sort()                // ordem NATURAL, in-place (Kof nao tem Comparator)

val m: Map<String, Int> = mapOf("a", 1)
var has = m.containsValue(1)             // varredura por VALOR (containsKey e por chave)
var prev = m.putIfAbsent("a", 9)         // V? — devolve o anterior e NAO sobrescreve;
prev = m.putIfAbsent("z", 9)             // null quando a chave e nova
```

- `sort()` aceita elementos de ordem natural (Int/Long/Double/String…);
  record ou outro sem ordem → `SEM097` (gate de compile-time compartilhado).
  `Float` no Native → `NAT001` com diagnostico honesto (§349) — nunca ordem
  errada silenciosa.
- `indexOf`/`lastIndexOf` buscam por valor com o tipo pinado; buscar por
  tipo ≠ e MISS SEGURO (`-1`), mesma regra do `contains` (SEM056 so checa a
  ESCRITA).
- `subList` fora dos limites morre no bounds check (medido:
  `IndexOutOfBoundsException: toIndex = N` no JVM; `kof_bounds_error` no
  Native) — mesma familia do `get(i)`.
- `take(n)`/`drop(n)`/`slice(offset, limit)` devolvem copia materializada
  (nunca view viva) e clampeiam honestamente: `n`/`offset` alem do fim dao a
  lista inteira / vazia, nunca erro. `n`/`offset`/`limit` negativo e erro
  nomeado em runtime — `"PAGINATION: count must be >= 0"` (`take`/`drop`) ou
  `"PAGINATION: limit/offset must be >= 0"` (`slice`). Nos 4 alvos
  (D-PAGINATION, pagination P1).
- `putIfAbsent` devolve `V?` (D-NULL-INTENT): estreite com `if (prev != null)`.
- O idiom Java `Collections.sort(l, comparator)` nao existe em Kof: `sort()`
  e ordem natural, ponto. Para achar posicao, `indexOf(x)` (nao loop manual
  com `get(i)`).

## Janela `Window<T>` (D-PAGINATION P2 — `import kof.pagination`)

```kof
import kof.pagination

val l: List<Int> = listOf(10, 20, 30, 40, 50)
val w = window(l, 2, 1)          // 2 itens a partir do offset 1 -> Window<Int>
val w2 = window(l, 2, 1, true)   // idem, total calculado localmente (opt-in)
w.items()        // List<Int> — a janela materializada (possivelmente vazia)
w.offset()       // Int
w.limit()        // Int
w.hasPrevious()  // Bool — offset > 0
w.hasNext()      // Bool — exato com total; otimista (items.size == limit) sem ele
w.total()        // Long? — null salvo quando pedido
```

- `Window<T>` e `window(items, limit, offset[, withTotal])` sao escritos em Kof
  (`D-KOF-FIRST-IMPL`) e injetados flat num `import kof.pagination` explicito —
  sem runtime por backend; nos 4 alvos (JVM/Native/JS/Script). Um
  `Window`/`window` declarado pelo usuario colide e pula a injecao.
- `limit`/`offset` sao `Int >= 0`; valor negativo e erro nomeado
  (`"PAGINATION: limit/offset must be >= 0"`), nunca clamp silencioso.
- `offset > size` → janela vazia com `hasPrevious = true` (sem erro);
  `limit == 0` → janela vazia. `hasNext` e exato quando `total` esta presente,
  otimista caso contrario. `total` e opt-in e nunca dispara um `COUNT`.
- O core nao sabe de SQL nem de HTTP (`orm.window`/`pageRequest` sao faces de
  plataforma de fatias posteriores).

## Quantificadores `any`/`all`/`none` (D-MULTIPARADIGMA-PHASE1A fatia 1a, todos os alvos)

```kof
var xs = listOf(1, 2, 3)
var hasBig = xs.any((x) -> x > 2)     // true — para no primeiro match
var allPos = xs.all((x) -> x > 0)     // true — vácuo no vazio
var noBig = xs.none((x) -> x > 9)     // true — vácuo no vazio
```

Loops eager com short-circuit: o predicado roda até a resposta ser conhecida e
para (um `throw` após o ponto de decisão nunca dispara). Vácuos: `all`/`none`
true no vazio, `any` false (`none` ≡ ¬`any`, decisão da mantenedora 28/09).
Predicados usam a regra de veracidade do `filter`. Params nus `(x)` herdam o
tipo do elemento (SG-012, generalizado); sem `take`/`drop`/`slice` aqui — são
da P1 pagination.

## `find` + `count(pred)` (D-MULTIPARADIGMA-PHASE1A fatia 1b, todos os alvos)

```kof
var xs = listOf(1, 2, 3)
var f = xs.find((x) -> x > 1)   // 2 — primeiro match ou null (como Map.get-ausente)
var m = xs.find((x) -> x > 9)   // null — teste com `== null` (idioma V?)
var n = xs.count((x) -> x > 1)  // 2 — count() nu segue significando size
```

No Native, a primitiva encontrada leva box por baixo (slots crus vs
consumidores `T?` boxed); ausência segue null/0 por alvo. Lambdas de bloco só
produzem valor com `return` explícito (SEM033).

## `forEach` (D-MULTIPARADIGMA-PHASE1A fatia 1c, todos os alvos)

```kof
var xs = listOf(1, 2, 3)
xs.forEach((x) -> println(x * 10))   // 10, 20, 30 — só efeito, sem alocação
listOf().forEach((x) -> println(x))  // vácuo: não imprime nada
```

## `flatMap` (D-MULTIPARADIGMA-PHASE1A fatia 1d, todos os alvos)

```kof
var xs = listOf(1, 2, 3)
var ys = xs.flatMap((x) -> listOf(x, x * 10))   // [1, 10, 2, 20, 3, 30]
var e = listOf().flatMap((x) -> listOf(x))      // vazio entra, vazio sai
```

## `distinct` (D-MULTIPARADIGMA-PHASE1A fatia 1e, todos os alvos)

```kof
var xs = listOf(3, 1, 2, 1, 3)
var d = xs.distinct()   // [3, 1, 2] — primeiras ocorrências em ordem
```
Igualdade é a regra do `contains` por alvo (String por conteúdo, resto como
`contains`); vazio entra, vazio sai.

## `sorted` (D-MULTIPARADIGMA-PHASE1A fatia 1g, todos os alvos)

```kof
var xs = listOf(3, 1, 2)
var s = xs.sorted()   // [1, 2, 3] — cópia fresca, xs intacto
var d = xs.sorted((a: Int, b: Int) -> b - a)   // [3, 2, 1] — comparador: negativo/zero/positivo
```
Ordem natural exige domínio natural (Int/Long/Double/Float/Bool/Char/String — SEM097 senão); com comparador a lambda define a ordem (records bem-vindos). Estável para comparadores puros; vazio/unitário entra, igual sai.

## `groupBy` (D-MULTIPARADIGMA-PHASE1A fatia 1h, todos os alvos)

```kof
var xs = listOf(1, 2, 3, 4)
var g = xs.groupBy((n: Int) -> n % 2)   // {1=[1, 3], 0=[2, 4]} — Map<K,List<E>>
```
Chaves usam a igualdade boxed do mapa (mesma taxonomia do `mapOf`); valores são listas frescas em ordem de encontro; chaves ausentes leem `null` (use `getOrDefault` ou estreite com `if`).

## `listOf` com subtipos relacionados infere o ancestral comum (0.5.0-beta, §285)

```kof
interface Animal { String sound() }
class Dog implements Animal { String sound() { return "woof" } }
class Cat implements Animal { String sound() { return "meow" } }
var animals = listOf(new Dog(), new Cat())   // inferido List<Animal>, nao List<Dog>
animals.get(1).sound()                       // "meow" — sem ClassCastException
```

Elementos que COMPELHAM um supertipo (classe ou interface) sao homogeneos no
nivel do ancestral: a inferencia alarga para o supertipo comum. Elementos
nao relacionados (`listOf(new Dog(), 42)`) mantem a rejeicao de homogeneidade
SEM056. Ate 0.3.x o tipo vinha so do PRIMEIRO argumento — o fix caminha por
superclasses E interfaces (familia do §156). Medido 18/09 no tip:
`woof`/`meow`.

## `Map.get` devolve `V?` para valores de referência (02/09)

`m.get(chave)` retorna `V?` quando o valor é um tipo de referência
(`Map<String, String>`, `Map<String, User>`): ausência = `null`, use
`if (v != null)` para estreitar. Para valores **primitivos** (`Map<String, Int>`)
o tipo agora TAMBÉM é `V?` (desde o merge N1 do D-NULL-INTENT, #438 `250f6207`,
18/09: `Int z = m.get("a")` falha com type-mismatch em `NullableType[int]` —
ausência é representável). **Cuidado enquanto o §294 estiver aberto:** no JVM,
o `if (v != null)` com chave PRESENTE em mapa de valor primitivo ainda morre em
runtime (`NoSuchMethodError Object.valueOf(boxed)`); até o §294 fechar, prefira
`m.getOrDefault(chave, fallback)` (landado `62bd455e`, medido funcionando) ou
checagens `contains`/`containsKey` em mapas de valor primitivo. Valores de
referência não são afetados.

Fix 27/08: `listOf(...).get(n)` e `size` em projetos grandes com `import a.b.C` agora resolvem corretamente (CompilerDriver file-specific imports). Não é necessário workaround manual de índice.

## `List`/`Set`/`Map` bare em posicao declarada (0.4.0 — §373/#443)

Um nome de colecao SEM argumentos de tipo em campo, parametro ou retorno e a
**colecao builtin** — exatamente o que a forma local sempre significou (#139/#150/#214):

```kof
class Box {
    List items                      // bare = List builtin (antes do §373: quebrava no class load)
    public constructor() { items = listOf(1, 2) }
}
count(xs: List): Int { return xs.size }     // bare em parametro
```

Prefira o tipo de elemento explicito (`List<Int> items`) quando conhecido — o compilador
checa mais. Uma classe do usuario com o mesmo nome (`class List { ... }` no seu pacote)
continua vencendo a builtin (guarda de shadow do §243, provado pelos controles de
`BareCollectionFieldE2ETest` 8/8, o mesmo print nos 4 alvos).

## When to use

Qualquer problema que requer uma sequência de elementos:
coleções, registros, filas simples, agrupamentos, acumuladores.
`Map`/`Set` para associações e conjuntos. `map`/`filter`/`reduce` para transformação sem loop manual.

## When not to use

- Não reimplementar `map`/`filter`/`reduce` com loop quando a higher-order expressa a intenção.
- Não usar `List<record>` com busca linear quando `Map<K,V>` resolve (quando há chave).

## BAD — estrutura manual

```kof
class Node {
    Node next
    Int value
}
class Registry {
    Node root
    Int count
}
```

## GOOD — coleção da linguagem

```kof
class Registry {
    List<LanguageEntry> entries

    constructor() {
        entries = listOf(
            LanguageEntry("Kof", "kf", "kof"),
            LanguageEntry("JSON", "json", "json")
        )
    }
}
```

## GOOD — transformação declarativa (0.5.0-beta)

```kof
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
```

## GOOD — Box<T> com primitivos (0.1.0 fix)

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b = Box<Int>(42)
println(b.get())   // 42 — substituteTypeVariable corrige T → Int no Native
```

## WHY

`Node`/`next`/`count` é implementação acidental. O domínio é "uma sequência de entradas".
Kof possui a abstração. Represente o domínio, não a implementação.
Higher-orders e `Box<T>` eliminam loops e wrappers manuais.

## Iteração

```kof
var items = listOf("a", "b", "c")
for (var item in items) {
    println(item)
}
```

`for-in` funciona sobre `List<T>` e arrays (`new Int[5]`).

## Tipos de elementos

```kof
var ids = listOf<Int>()          // lista vazia de Int
var nomes = listOf("Ana", "Mel")
var users = listOf<User>()       // lista de objetos (erasure)
var boxed: Box<Int> = Box(5)
```

## Anti-patterns relacionados

- Linked list manual → `training/anti-patterns/manual-data-structures.md`
- Array como substituto de coleção dinâmica → usar `List<T>`
- Loop manual para map/filter → usar higher-order
