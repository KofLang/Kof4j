# Anti-pattern — Java-like Code

## Name

Código Java traduzido literalmente para Kof.

## Problem

Transportar convenções de Java (getters/setters, builders, factories, utility
classes, DTO ceremony, `equals`, `StringBuilder`, sentinelas) para Kof sem
reavaliar se a convenção tem razão de existir na nova linguagem.

## Bad example

```kof
class User {
    private String name
    private Int age
    public getName(): String {
        return name
    }
    public setName(String name) {
        this.name = name
    }
    public getAge(): Int {
        return age
    }
    public setAge(Int age) {
        this.age = age
    }
}
```

## Why it is bad

Java exige getters/setters por causa de JavaBeans, serialização, frameworks de
reflection e convenções de ferramentas. Kof não possui nenhuma dessas
convenções. O código duplica o estado com cerimônia sem semântica.

## Preferred approach

```kof
class User {
    String name
    Int age
}
```

Acesso direto: `u.name`, `u.age = 30`.

## Java pattern → decisão em Kof

| Padrão Java | Por que existe em Java | Kof precisa? | Alternativa idiomática |
|---|---|---|---|
| Getter/setter | JavaBeans, frameworks, reflection | Não | Campo público |
| Builder | Construtores com muitos args opcionais | Não (por enquanto) | Construtor com args ou record |
| Factory estática | Construtores não podem ter nomes | Não | Chamar o construtor |
| Utility class com static | Java não tem funções top-level | Não | Função top-level |
| Service/Repository/Controller | Injeção de dependência, ciclos de vida | Não | Função top-level ou classe direta |
| `StringBuilder` | `+` em loop era ineficiente | Não | `+` concatena |
| `.equals()` | `==` não pode ser sobrecarregado | Não | `==` compara conteúdo |
| DTO + mapper | Serialização exige no-arg + setters | Não | Record + json.encode |
| Optional | `null` onipresente | Parcial (0.3.22-beta) | `String?` + `if (x != null)` narrowing; `Option<T>` ainda planned |
| `instanceof` + cast | Type narrowing | Sim (0.3.22-beta) | `instanceof` + `as` e pattern `case String s:` / `case Point(x,y)` |
| Loop manual para map | Java sem higher-order até streams | Não | `list.map/filter/reduce` (0.3.22-beta) |
| `import java.util.*` | Java collections | Não | `listOf`/`mapOf`/`setOf` + `import a.b.C` file-specific (fix 27/08) |

## Exceptions

Padrões que são exceções legítimas:
- Interoperabilidade com bibliotecas Java (quando existir a camada de interop).
- Convenções impostas por API externa.