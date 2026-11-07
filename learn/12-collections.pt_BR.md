[English](12-collections.md) | [Português](12-collections.pt_BR.md)

# 12 — Collections

> **Status: implementado (JVM / Native / JS) — 0.5.0-beta**
>
> `List<T>`, `Map<K,V>` e `Set<T>` são coleções nativas de Kof. `List` agora tem `map/filter/reduce` idiomáticos (0.2.0) além das operações base. O tipo dos
> elementos é preservado pela pipeline inteira (inferência, for-in, `get`,
> resolução de métodos). No Native, Map e Set rodam em assembly próprio
> sobre o mesmo layout de alocação do List.

## List — a forma idiomática

```kf
var tokens = listOf(
    Token("identifier", "hello"),
    Token("string", "world")
)

for (var token in tokens) {
    println(token.kind())     // o tipo do elemento nunca degrada para Object
}

var nomes = listOf<String>()            // lista vazia tipada
nomes.add("Ana")
nomes.add("Bob")
println(nomes.get(0))
```

A inferência é mantida em toda a pipeline:

```kf
var users: List<User> = listOf()        // anotação explícita
users.add(User("Mel", 26))
println(users.get(0).name)
```

`json.decode<List<User>>(...)` também preserva o tipo dos elementos —
cada elemento é vinculado ao record, em JVM e KofJS.

## Operações de List

```kf
var l = listOf(1, 2, 3, 4)

l.size()          // 4
l.get(0)          // 1
l.contains(3)     // true
l.isEmpty()       // false
l.remove(0)       // remove por índice, devolve o elemento
l.set(0, 9)       // substitui no lugar
l.clear()         // esvazia

l.add(5)          // append (void)
l.addAll(listOf(6, 7))  // append de tudo (Bool — mudou?)
l.indexOf(3)      // primeiro índice ou -1
l.lastIndexOf(3)  // último índice ou -1
l.subList(1, 3)   // nova List — mesmo tipo de elemento (faixa [1, 3))
l.sort()          // void, ordem natural no lugar (elementos Comparable)
```

### map / filter / reduce (0.2.0)

`List<T>` expõe transformações funcionais diretas — sem expor `Stream` — via `intention->Kof->frontend->IR->backend->runtime`:

```kf
main() {
    var nums = listOf(1, 2, 3, 4, 5)

    var dobrados = nums.map((x: Int) -> x * 2)          // List<Int> [2,4,6,8,10]
    println(dobrados.get(1))                       // 4

    var pares = nums.filter((x: Int) -> x % 2 == 0)     // [2,4]
    println(pares.size())                          // 2

    var soma = nums.reduce((acc: Int, x: Int) -> acc + x, 0) // 15
    println(soma)

    // encadeando:
    var r = listOf(1, 2, 3, 4)
        .filter((x: Int) -> x > 1)
        .map((x: Int) -> x * 10)
    println(r.get(0))   // 20

    // com records:
    var users = listOf(User("Ana", 26), User("Bob", 31))
    var nomes = users.map((u: User) -> u.name)
    println(nomes.contains("Ana"))   // true
}
```

Todos os três métodos rodam em JVM, Native e JS com a mesma semântica.

## Map — pares chave/valor

`Map<K,V>` guarda pares com chave única. A API espelha a intenção, não o
mecanismo — sem `HashMap` exposto na superfície:

```kf
var idades = mapOf()
idades.put("Ana", 26)
idades.put("Bob", 31)

idades.get("Ana")         // 26
idades.containsKey("Bob") // true
idades.size()             // 2

idades.put("Ana", 27)     // sobrescreve; devolve o valor anterior
idades.remove("Bob")      // devolve o valor removido

idades.keys()             // List<String> das chaves
idades.values()           // List<Int> dos valores
idades.clear()
idades.isEmpty()
```

O tipo do valor é pinado no primeiro `put` — depois disso `get`, `remove`
e comparações têm tipagem concreta:

```kf
var estoque = mapOf()
estoque.put("parafuso", 500)
assert(estoque.get("parafuso") == 500)   // comparação numérica direta
```

> **Contrato nullable (D-NULL-INTENT):** `get`, `put`, `remove` e
> `putIfAbsent` devolvem `V?` — o valor ANTERIOR/REMOVIDO, ou `null` quando a
> chave está ausente (contrato `Map` do Java). Estreite antes de usar:
>
> ```kf
> var prev = idades.put("Ana", 28)     // Int? — 26, ou null se ausente
> if (prev != null) { println(prev) }
> var cur = idades.get("Ana")          // Int?
> var d = idades.getOrDefault("Zed", 0) // Int — fallback passado pelo chamador (#386)
> ```
>
> `containsValue(v)` é `Bool` (#386) e compara por conteúdo para records
> (veja abaixo).

## Set — valores únicos

`Set<T>` rejeita duplicatas: `add` devolve `true` só quando o elemento é
novo.

```kf
var vistos = setOf(1, 2, 2, 3)
vistos.size()          // 3 — o segundo 2 foi ignorado

vistos.contains(2)     // true
vistos.add(2)          // false (já existe)
vistos.remove(1)       // true
vistos.clear()
vistos.isEmpty()       // true
```

Strings funcionam igual:

```kf
var tags = setOf("kof", "lang")
tags.add("kof")        // false
println(tags.size())   // 1
```

## kof.http — exemplo com coleções (JVM+JS)

`kof.http` funciona em JVM e JS (Native reporta `HTTP002`):

```kf
main() {
    var url = "https://api.example.com/users"
    var resp = http.get(url)                 // resp é o corpo (String)
    if (http.status(url) == 200) {
        var users = json.decode<List<User>>(resp)
        var ativos = users.filter((u: User) -> u.age > 18)
        println(ativos.map((u: User) -> u.name).get(0))
    }
}
```

## Paridade entre targets

| Operação | JVM | Native | JS |
|----------|-----|--------|----|
| List completa | ✅ | ✅ asm | ✅ |
| List map/filter/reduce | ✅ | ✅ | ✅ |
| Map (todas as operações) | ✅ HashMap | ✅ asm próprio | ✅ JS Map |
| Set (todas as operações) | ✅ HashSet | ✅ asm sobre List | ✅ JS Set |
| kof.http (JVM+JS) | ✅ | HTTP002 | ✅ |

Igualdade é POR CONTEÚDO em todo lugar para record/classe (24/09): `contains`/`indexOf` em List e Set, dedup do `set.add`, **chaves** de `Map.get`/`containsKey`/`remove` e **valores** de `containsValue` comparam campo a campo no JVM, Native (x86-64 + riscv64 + aarch64 via `kof_obj_equals`/`kof_equals_table`) e JS — `mapOf("a", Point(1, 2)).containsValue(Point(1, 2))` é `true` em todos os alvos. O `equals` sintetizado de record recursa em campos record/classe aninhados; o `hashCode` também é por conteúdo (String, Double, record aninhado). `String` compara por conteúdo, primitivos por valor, classe sem `equals` por identidade (contrato JVM).

## Próximo passo

**[13 — Nullability](13-nullability.md)**
