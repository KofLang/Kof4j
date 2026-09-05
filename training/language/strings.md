# Referência de Strings em Kof

**Versão:** 0.2.6-beta

## Criação

```kof
var s = "Hello"           // string literal
var s = ""                // string vazia
```

## Operações

### Tamanho (Length)
```kof
var s = "Hello"
println(s.length)  // 5
```

### Acesso a caracteres
```kof
var s = "Hello"
println(s.charAt(0))  // 72 (H)
```

### Substring
```kof
var s = "Hello"
println(s.substring(1, 4))  // "ell"
```

### Concatenação
```kof
var a = "Hello"
var b = " World"
println(a + b)  // "Hello World"
```

### Contém (contains)
```kof
var s = "Hello World"
println(s.contains("World"))  // true
println(s.contains("xyz"))    // false
```

### Começa com / Termina com (startsWith / endsWith)
```kof
var s = "Hello"
println(s.startsWith("He"))  // true
println(s.endsWith("llo"))    // true
```

### Igualdade
```kof
var a = "Hello"
var b = "Hello"
println(a == b)  // true (comparação de conteúdo, byte a byte)
```

## Imutabilidade

Strings são imutáveis. Operações como `concat` criam novas strings.

## Null safety (0.2.6-beta)

```kof
String? s = null
if (s != null) {
    println(s.length)   // OK — narrowing
}
```

## Codificação e tamanho — por target

- **Native**: strings são UTF-8; `length` retorna a **contagem de bytes**.
- **JVM**: strings são UTF-16 (`java.lang.String`); `length` retorna unidades de código.

```kof
println("Olá".length)  // Native: 4 (bytes UTF-8); JVM: 3 (unidades UTF-16)
```

Não assuma uma contagem específica de caracteres quando a string contém
caracteres não-ASCII e o target importa.