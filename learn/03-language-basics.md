# 03 — Fundamentos da Linguagem

> **Kof 0.3.22-beta — `intention->Kof->frontend->IR->backend->runtime`**

## O que você vai aprender

Neste capítulo você vai entender como o código Kof é estruturado: statements, expressões, literais, comentários e a estrutura básica de um programa.

## Statements

Um statement é uma instrução. Em Kof, o ponto e vírgula é **opcional** na maioria dos contextos:

```kf
record User(String name)
```

```kf
var nome = "Mel"
println(nome)
```

O compilador aceita tanto com quanto sem ponto e vírgula. Escolha um estilo e seja consistente.

## Comentários

Comentários de linha:

```kf
// isso é um comentário
```

Comentários de bloco:

```kf
/* isso é um
   comentário de
   várias linhas */
```

O compilador ignora comentários completamente.

## Literais (inclui `String?` para nullable — ver cap. 13)

### Strings

```kf
"olá mundo"
"com Escape\n"
"com \"aspas\""
```

### Números

```kf
42          // Int
42L         // Long
3.14        // Double
3.14f       // Float
```

### Booleanos

```kf
true
false
```

### Null

```kf
null
```

## Tipos primitivos

Kof usa os mesmos tipos primitivos da JVM:

| Tipo | Descrição | Tamanho |
|------|-----------|---------|
| `Bool` | booleano | 1 bit |
| `Byte` | byte inteiro | 8 bits |
| `Short` | inteiro curto | 16 bits |
| `Int` | inteiro | 32 bits |
| `Long` | inteiro longo | 64 bits |
| `Float` | ponto flutuante | 32 bits |
| `Double` | ponto flutuante duplo | 64 bits |
| `Char` | caractere | 16 bits |
| `Void` | sem valor | — |

## Strings

Strings são objetos. São imutáveis (como em Java):

```kf
String nome = "Kof"
```

Concatenação funciona com `+`:

```kf
String saudacao = "Olá, " + nome
```

## Operadores

### Aritméticos

```kf
a + b     // soma
a - b     // subtração
a * b     // multiplicação
a / b     // divisão
a % b     // módulo
```

### Comparação

```kf
a == b    // igual
a != b    // diferente
a < b     // menor
a <= b    // menor ou igual
a > b     // maior
a >= b    // maior ou igual
```

### Lógicos

```kf
a && b    // e lógico
a || b    // ou lógico
!a        // negação
```

### Atribuição

```kf
x = 5
x += 3    // x = x + 3
x -= 2    // x = x - 2
x *= 4    // x = x * 4
x /= 2    // x = x / 2
x %= 3    // x = x % 3
```

## Estrutura de um arquivo Kof

Um arquivo `.kf` contém:

1. Declaração de package (opcional)
2. Imports (opcional)
3. Declarações de tipo (classes, records, interfaces)
4. Funções

Exemplo:

```kf
package com.example

record Point(Int x, Int y)

main() {
    var p = Point(3, 7)
    print(p)
}
```

## Status atual

✅ Lexer reconhece todos os literais e operadores
✅ Ponto e vírgula opcional
✅ Package e imports funcionam
✅ Records funcionam
✅ Funções com `main()` funcionam

## Próximo passo

[Variáveis e Tipos →](04-variables-and-types.md)