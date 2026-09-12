# 02 — Primeiro Programa

> **Kof 0.3.22-beta — set 2026 — targets jvm/native/native.risc/native.arm/js/kofc**

## O construto mais básico

Em Kof, o construto mais simples que o compilador gera bytecode válido é o **record**:

```kf
record Ponto(Int x, Int y)
```

Isso cria uma classe JVM com:
- dois campos privados e finais (`x`, `y`)
- um construtor público que aceita `Int` e `Int`
- dois métodos públicos `x()` e `y()` que retornam os valores
- um método `toString()`

No Native vira struct com fields e métodos equivalentes; no JS, classe ES — a cadeia `intention->Kof->frontend->IR->backend->runtime` mantém a semântica.

## Entendendo cada parte

```
record      → palavra-chave: define um record
Ponto       → nome da classe
(           → início dos componentes
Int x       → primeiro componente: tipo Int, nome x
,           → separador
Int y       → segundo componente: tipo Int, nome y
)           → fim dos componentes
```

## Criando instâncias

Em Java, para criar uma instância você escreve `new User("Mel", ...)`. Em Kof,
a construção é `Classe(args)` **sem o `new`** (a forma idiomática); `new`
continua aceito por retrocompatibilidade, com a mesma semântica:

```kf
var p = Ponto(3, 7)      // forma idiomática (recomendada)
var old = new Ponto(3, 7) // forma explícita (retrocompatível)
```

O compilador gera o construtor e os accessors automaticamente.

## Acessando valores

Os métodos de acesso são gerados automaticamente:

```java
Ponto p = new Ponto(3, 7);
p.x()  // retorna 3
p.y()  // retorna 7
```

## Um programa completo

Agora Kof suporta `main()`. Você pode criar um programa completo:

Arquivo `main.kf`:

```kf
main() = print("Olá, mundo!")
```

Compilando e executando:

```bash
kof run main.kf                 # jvm (padrão)
kof run main.kf --target=native # ELF x86-64
kof run main.kf --target=js     # ES Module via GraalJS embarcado
```

Resultado:

```
Olá, mundo!
```

## Um programa com records

Arquivo `ponto.kf`:

```kf
record Ponto(Int x, Int y)

main() {
    var p = Ponto(3, 7)
    print(p)
}
```

Executando:

```bash
kof run ponto.kf
```

Resultado:

```
Ponto[x=3, y=7]
```

### Pattern matching com destructuring (0.2.0)

Records já desestruturam em `switch`:

```kf
record Ponto(Int x, Int y)

String descreve(Object o) {
    switch (o) {
        case Ponto(x, y): { return "ponto " + x + "," + y }
        case String s: { return "texto " + s }
        default: { return "outro" }
    }
}

main() {
    println(descreve(Ponto(3, 7)))  // ponto 3,7
    println(descreve("kof"))        // texto kof
}
```

```bash
kof run ponto.kf --target=jvm     # JVM instanceof+checkcast
kof run ponto.kf --target=native  # Native rbx→rcx fix
kof run ponto.kf --target=js      # JS typeof
```

### KofScript: `let` no topo vira global

No `kof script` / `kof repl`, `let`/`const` no nível do arquivo não são locais de `main` — viram `KofScriptGlobals`:

Arquivo `demo.ks`:

```kf
let nome = "Mel"
const pi = 3.14

main() {
    println(nome + " " + pi)
}
```

```bash
kof script demo.ks                # execução direta
kof script --repl                 # REPL incremental (digite 'exit' para sair)
kof script demo.ks --watch        # re-executa ao salvar
```

### KofC: C subset nativo-only

`kof c` não compila Kof — compila um subset de C para ELF x86-64:

```c
// hello.c
int x = 42;
void printInt(int v);

int main() {
    if (x > 0) {
        printInt(x);
    }
    while (x > 0) { x = x - 1; }
    return 0;
}
```

```bash
kof c hello.c --run               # compila via GAS+LD e executa
kof c hello.c --output ./bin      # só compila (native-only, sem --target jvm/js)
```

## Variáveis e inferência

Kof suporta inferência de tipos:

```kf
var nome = "Mel"
var idade = 26
var pi = 3.14
var apelido: String? = null   // String? básico (0.2.0): nullable com verificação em compile-time
```

O compilador entende os tipos automaticamente.

## Exercício 1

1. Crie um arquivo `coordenada.kf`
2. Defina um record com dois campos: `lat Double` e `lon Double`
3. Compile com a CLI
4. Verifique com `javap -v Coordenada.class`

## Exercício 2

1. Crie um record `Pessoa` com campos `nome String` e `idade Int`
2. Crie uma função main que crie uma pessoa e imprima seus dados
3. Execute com `kof run`

## Exercício 3 — destructuring + KofScript

1. Crie `ponto.kf` com `record Ponto(Int x, Int y)` e um `switch` com `case Ponto(x, y):`
2. Rode com `kof run --target=jvm` e `--target=js`
3. Crie `demo.ks` com `let n = 10` no topo e use `n` dentro de `main()` via `kof script demo.ks`

## Próximo passo

[Fundamentos da Linguagem →](03-language-basics.md)
