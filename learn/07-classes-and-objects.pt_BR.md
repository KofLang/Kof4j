[English](07-classes-and-objects.md) | [Português](07-classes-and-objects.pt_BR.md)

# 07 — Classes e Objetos

> **Kof 0.5.0-beta — exemplos verificados no compilador (02/09)**
>
> Kof tem **dois** modelos de "dado com parâmetros": `record`/`class X(...)`
> (imutável, accessors) e classe com campos + `constructor(...)` (mutável,
> campos diretos). **Sem getters/setters** — o campo é o dado.

## 1. Dados imutáveis → record

A forma canônica para dados imutáveis:

```kf
record User(String name, String email)

main() {
    var u = User("Mel", "mel@kof.dev")
    println(u.name())          // accessor
    println(u.email())         // mel@kof.dev
}
```

O compilador gera: construtor canônico, accessors (`name()`), e
`toString`/`equals`/`hashCode` em todos os alvos. Igualdade e hash são POR
CONTEÚDO em todo lugar (§104b-ii/§114, 24/09): dois records com os mesmos
valores de campo são `==`, têm o mesmo hash, dedup em `Set`, batem como chave
de `Map` e como valor de `containsValue` — no JVM, Native (x86-64, riscv64,
aarch64) e JS igualmente. O `equals` sintetizado recursa em campos record/classe
aninhados; o `hashCode` é por conteúdo para String, Double e records aninhados.

### `value record` — mesmo modelo de dados, sem identidade (TIER 2.7, 14/10)

```kf
value record Vec2(Float x, Float y)
```

Um `value record` comporta-se **exatamente como `record`** (campos, `==`
estrutural, `toString` — verificado nos quatro alvos) mas declara **sem
identidade de objeto** (`D-VALUE-RECORD`; `value` aplica-se só a `record`,
coleções guardam boxado). Por alvo: JVM = a classe record comum, Native = a
representação de heap atual, JS = o objeto é **congelado**. `value` é uma
keyword *contextual*: só antes de `record` — em qualquer outro lugar `value`
continua identificador comum.

Records podem ter métodos:

```kf
record Token(String kind, String text) {
    label(): String {
        return kind + "(" + text + ")"
    }
}
```

## 2. `class X(...)` = record (mesma coisa — verificado 02/09)

`class User(String name, String email)` é **alias de `record`** — o parser o
trata como record body (imutável, `extends java.lang.Record` no JVM):

```kf
class User(String name, String email) {
    greeting(): String {
        return "Hello " + name
    }
}

main() {
    var u = User("Mel", "mel@kof.dev")
    println(u.greeting())      // Hello Mel
    println(u.name)            // leitura ok (vira o accessor)
    // u.name = "Ana"          // ERRO de compilação SEM038: record é imutável
}
```

> Prefira `record` (a intenção é explícita). `class X(...)` é retrocompatível.

## 3. Estado mutável → classe com campos + `constructor(...)`

Para **mutar**, use campos públicos explícitos:

```kf
class Conta {
    String titular
    Double saldo

    public constructor(String titular, Double saldo) {
        this.titular = titular
        this.saldo = saldo
    }

    depositar(Double valor) {
        saldo = saldo + valor     // acesso direto ao campo
    }
}

main() {
    var c = Conta("Mel", 100.0)
    c.saldo = 50.0                // escrita direta — sem setter
    c.depositar(25.0)
    println(c.saldo)              // 75.0 — leitura direta, sem getter
}
```

**Sem getters/setters**: `c.saldo` lê, `c.saldo = x` escreve. `getSaldo()`/
`setSaldo()` são cerimônia Java sem razão em Kof (ver cap. 08).

## Construtor padrão

Sem `constructor(...)`, um construtor vazio é gerado:

```kf
class Config {
    String host
    Int porta
}

main() {
    var config = Config()
    config.host = "localhost"
    println(config.host)
}
```

`new Config()` também é aceito (retrocompatível).

## Campos com inicializador

```kf
class User {
    String name
    Bool active = true
}
```

Inicializadores rodam em todos os construtores (JVM, Native, JS).

## Modificadores de acesso

`private` existe para encapsulamento real — mas **não crie getter para
expor**; ou o campo é público, ou o método tem semântica:

```kf
class Conta {
    private Double saldo

    public constructor(Double saldo) { this.saldo = saldo }

    // método com SEMÂNTICA, não getter
    Double totalComJuros(Double taxa) {
        return saldo * (1 + taxa)
    }
}
```

## Funções utilitárias → top-level (não classe static)

```kf
// ❌ utility class com static (Java)
class StringUtils {
    static String repetir(String texto, Int vezes) { ... }
}

// ✅ função top-level (Kof)
String repetir(String texto, Int vezes) {
    var resultado = ""
    for (var i = 0; i < vezes; i++) {
        resultado += texto
    }
    return resultado
}
```

## this e super

```kf
class Animal {
    String nome

    public constructor(String nome) {
        this.nome = nome
    }
}

class Cachorro extends Animal {
    String raca

    public constructor(String nome, String raca) {
        super(nome)          // super(args) é a 1ª instrução
        this.raca = raca
    }
}
```

Override é implícito (mesmo nome de método); dispatch é virtual.

## Sobrecarga de métodos (0.5.0-beta, §131)

Métodos de mesmo nome coexistem na classe quando as assinaturas diferem:

```kf
class Calc {
    Int add(Int a, Int b) { return a + b }
    Int add(Int a, Int b, Int c) { return a + b + c }
}
main() {
    var c = Calc()
    println(c.add(1, 2))       // 3
    println(c.add(1, 2, 3))    // 6
}
```

As regras são as mesmas das funções (SEM047 duplicata exata, SEM057
ambiguidade) — veja cap. 06 e `training/idioms/classes.md`.

## Status atual

- ✅ `record` / `class X(...)` — dados imutáveis, accessors (3 targets)
- ✅ Classe mutável — campos públicos + `constructor(...)`
- ✅ Campos com inicializador (JVM, Native, JS)
- ✅ Herança, virtual dispatch, interfaces
- ✅ Sem getters/setters — campo direto

## Exercício 1

Crie `class ContaBancaria` com campos `titular` e `saldo`, construtor e
métodos `depositar`/`sacar` — **sem** `getSaldo()`, acesse `c.saldo`
diretamente. Valide com `kof run`.

## Exercício 2

Crie `record Retangulo(Double largura, Double altura)` com um método
`area()`. Teste. Depois tente `r.largura = 5.0` — o que acontece e por quê?

## Próximo passo

[Campos e Acesso a Dados →](08-properties.md)