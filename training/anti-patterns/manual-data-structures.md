# Anti-pattern — Manual Data Structures

## Name

Implementar estruturas de dados que a linguagem já fornece.

## Problem

Linked lists manuais (`Node` + `next`), arrays dinâmicos manuais, hashmaps
manuais, string builders manuais — quando `List<T>`, `Map<K,V>`, `Set<T>` e `+`
já resolvem o problema (3 targets).

## Bad example

```kof
class Node {
    String value
    Node next
}
class Registry {
    Node root
    Int count
    Bool hasNext() {
        return root != null && root.next != null
    }
}
```

Workaround histórico (removido 27/08): `List.get` manual com bounds check — agora `l.get(i)` já faz via `kof_list_get`.

Outros workarounds manuais que existiam por gaps de target e hoje estão obsoletos:
parser JSON manual no Native (JSN001/002/003 fechados 31/08 — `json.encode`/`json.decode<T>` de objetos/records/arrays nos 3 targets) e aritmética/formatação FP manual no Native (FLT001 fechado 31/08 — float/double real em XMM).

## Why it is bad

A implementação manual carrega: alocação, encadeamento, contagem, bounds,
iteração — tudo que o programador teria que manter e testar. O domínio é
"uma coleção", não "nós encadeados".

## Preferred approach (0.3.22-beta)

```kof
class Registry {
    List<String> entries

    constructor() {
        entries = listOf()
    }
}

// Associações — Map existe (0.1.0)
var m = mapOf("kof", "kf")
m.put("json", "json")
var v = m.get("kof")

// Conjuntos
var s = setOf(1, 2, 3)
s.add(4)

// Transformação — sem loop manual
var nomes = users.map((u: User) -> u.name)
var pares = nums.filter((x: Int) -> x % 2 == 0)
```

## Estruturas manuais comuns → alternativa (0.3.22-beta)

| Manual | Alternativa Kof |
|---|---|
| Linked list (`Node.next`) | `List<T>` |
| Dynamic array + `count` | `List<T>` (size) |
| Hashmap manual | `Map<K,V>` + `mapOf` |
| Set manual | `Set<T>` + `setOf` |
| String builder | `+` concatenação |
| Collection wrapper | A coleção diretamente |
| Registry com `get`/`has` manual | `List<T>` + `contains` / `Map.get` |
| Loop manual para map/filter | `list.map` / `filter` / `reduce` |
| `Box<T>` manual para primitivos | `Box<T>` nativo (erasure fix 25/08) |

## Exceptions

- Implementar uma estrutura para APRENDER a linguagem (didático).
- Estrutura com requisitos de performance comprovados que a stdlib não cobre.
