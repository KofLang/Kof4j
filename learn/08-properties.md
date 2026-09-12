# 08 — Campos e Acesso a Dados ("Propriedades")

> **Status: implementado — acesso direto a campo, sem getters/setters (0.3.22-beta, exemplos verificados no compilador)**
>
> Kof **não** tem JavaBeans: não há getter/setter de convenção nem reflexão de
> framework. Um campo é acessado direto: `u.name` (leitura) e `u.name = "Mel"`
> (escrita). Isto é uma decisão de filosofia, não uma lacuna — ver
> `docs/philosophy.md` e `training/anti-patterns/java-like-code.md`.

## O problema do Java

Em Java, expor um campo exige cerimônia:

```java
private String name;

public String getName() { return name; }
public void setName(String name) { this.name = name; }
```

Isso existe por causa de JavaBeans, serialização e frameworks de reflexão.
**Kof não tem nenhuma dessas convenções** — então a cerimônia some.

## Dois modelos de "dado com parâmetros"

Kof tem **dois** modelos, e é essencial distingui-los:

| Declaração | Runtime | Campos | Acesso | Mutável? |
|-----------|---------|--------|--------|----------|
| `record Point(Int x, Int y)` | record | privados `final` | `p.x()` (accessor) | não |
| `class User(String name, Int age)` | **record** (idem) | privados `final` | `u.name` (→ accessor) | não |
| `class Conta { String titular; constructor(...) }` | classe | públicos | `c.titular` | sim |

> `class X(...)` é **alias de `record X(...)`** — compila para um
> `java.lang.Record` (imutável). Para **estado mutável** com parâmetros, use
> campos explícitos + `constructor(...)`.

## 1. Dados imutáveis → record (e `class X(...)`)

```kf
record Point(Int x, Int y)
// class Point(Int x, Int y) — idêntico

main() {
    var p = Point(10, 20)
    println(p.x())        // 10 — accessor do record
    println(p)            // Point[x=10, y=20] (JVM)
    // p.x = 99           // ERRO de compilação SEM038: records são imutáveis
}
```

`u.name` (sem parênteses) em um record **também lê** — o compilador baixa para
o accessor. Mas a **escrita** (`u.name = ...`) é inválida (campo final).

## 2. Estado mutável → classe com `constructor(...)`

```kf
class Conta {
    String titular
    Double saldo

    public constructor(String titular, Double saldo) {
        this.titular = titular
        this.saldo = saldo
    }

    depositar(Double valor) {
        saldo = saldo + valor
    }
}

main() {
    var c = Conta("Mel", 100.0)
    println(c.titular)          // "Mel" — campo público, leitura direta
    c.saldo = 200.0             // escrita direta
    c.depositar(50.0)
    println(c.saldo)            // 250.0
}
```

Aqui os campos são **públicos** e **mutáveis** — leitura e escrita diretas,
sem getters/setters.

## Campos sem construtor

Uma classe sem construtor explícito tem um construtor padrão sem argumentos:

```kf
class Usuario {
    String nome
    Int idade
}

main() {
    var u = Usuario()
    u.nome = "Mel"
    println(u.nome)
}
```

## Regras de acesso

- Campos são públicos por padrão (`private`/`protected` existem para quando
  você realmente precisa encapsular).
- Não escreva `getName()`/`setName()` por reflexo — é cerimônia sem semântica.
- Métodos dentro da classe acessam os campos direto (`saldo = saldo + valor`).

## Anti-padrão (o que NÃO fazer)

```kf
// ❌ Java traduzido — getters/setters sem razão de existir
class User {
    private String name
    public getName(): String { return name }
    public setName(String name) { this.name = name }
}

// ✅ Kof — o campo é o dado
class User {
    String name
}
```

## Exercícios

1. Crie `class Conta(String titular, Double saldo)` e tente
   `c.saldo = 300.0`. O que acontece? Explique por quê (compare com o
   `record`).
2. Escreva a mesma `Conta` como **classe mutável** (campos + `constructor`) e
   implemente `depositar`/`sacar`. Valide com `kof run`.
3. Converta o modelo de dados de um app simples (ex.: `User`, `Produto`)
   para `record` quando imutável e classe quando mutável — decida caso a caso.

## Próximo passo

[Interfaces →](09-interfaces.md)