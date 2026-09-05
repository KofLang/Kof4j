# 05 — Controle de Fluxo

> **Status: implementado (JVM / Native / JS) — 0.2.6-beta**
>
> `if/else`, `while`, `for`, `for-in`, `switch`, `break/continue` funcionam nos três targets. Pattern matching (`case String s`, `Point(x,y)`) ver capítulo 15.

## Condicional

### if / else

```kf
if (idade >= 18) {
    print("maior de idade");
} else {
    print("menor de idade");
}
```

### if como expressão

```kf
String mensagem = if (ativo) "sim" else "não";
```

## Loops

### while

```kf
var i = 0;
while (i < 10) {
    print(i);
    i++;
}
```

### for

```kf
for (var i = 0; i < 10; i++) {
    print(i);
}
```

### for-in

```kf
for (var nome in nomes) {
    print(nome)
}
```

Funciona sobre `List<T>` e arrays (sintaxe `for (var x in colecao)`).

## switch

```kf
switch (dia) {
    case 1:
        println("segunda")
    case 5:
        println("sexta")
    default:
        println("meio")
}
```

> Nota: cada `case` termina sozinho (não há *fallthrough* entre casos — o
> compilador salta para o fim do `switch` ao concluir o corpo), então `break`
> **não é necessário** dentro de `switch`. Use `break`/`continue` apenas em
> loops. If-expr é a forma preferida para valores condicionais:
> `var x = if (c) a else b`. Quando o `switch` produz valor, use a forma
> **expressão** (SYN001, 0.2.6-beta): `var r = switch (dia) { case 1 -> "seg";
> default -> "outro" }` — sem `break`, sem escopo de bloco, `default`
> obrigatório. Switch com padrões (type pattern / destructuring) ver capítulo
> 15.

## break e continue

```kf
for (var i = 0; i < 100; i++) {
    if (i == 50) break;
    if (i % 2 == 0) continue;
    print(i);
}
```

## Próximo passo

[Funções →](06-functions.md)
