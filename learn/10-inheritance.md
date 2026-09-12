# 10 — Herança

> **Status: implementado (JVM / JS — Native SUP001) — 0.3.22-beta — Target separation `native.risc/arm` preserva dispatch**
>
> `extends`, virtual dispatch, sobrescrita, construtor `super(...)` e
> `super.metodo()` funcionam nos targets JVM e KofJS; no Native, herança e
> `super(...)` funcionam, mas `super.metodo()` reporta o gap `SUP001`
> (o compilador ainda não emite a chamada não-virtual sobre a vtable).

## Extends

```kf
class Animal {
    String nome
    constructor(String n) {
        nome = n
    }
    String falar() {
        return nome + " faz um barulho"
    }
}

class Cachorro extends Animal {
    constructor() {
        super("Rex")
    }
    String falar() {
        return nome + " late"
    }
}
```

## super(...) — construtor da superclasse

O construtor da subclasse chama `super(args)` como **primeira instrução** do corpo. Sem chamada explícita, o compilador insere `super()` automaticamente (quando a superclasse não é `Object`).

```kf
constructor(String n) {
    super(n)          // explícito: repassa o argumento
}
```

## super.metodo() — implementação da superclasse

Para invocar a implementação sobrescrita (não a própria), use `super.metodo(args)`:

```kf
class Cachorro extends Animal {
    String falar() {
        return super.falar() + " (latindo)"
    }
}
```

No backend JVM isso vira um `invokespecial` com owner na superclasse direta — dispatch não virtual, igual ao `javac`. Funciona também contra superclasses externas vindas do classpath (`android.view.View` etc.): o compilador lê a assinatura real do `.jar`/`.aar` para emitir o descritor exato.

## Hierarquia

```
Object
  └── Animal
        ├── Cachorro
        └── Gato
```

## Classes abstratas

```kf
abstract class Forma {
    abstract Double area()
}

class Circulo(Double raio) extends Forma {
    Double area() {
        return 3.14159 * raio * raio
    }
}

class Retangulo(Double largura, Double altura) extends Forma {
    Double area() {
        return largura * altura
    }
}
```

## sealed classes (planejado — ainda não implementado)

A palavra-chave existe no lexer, mas o parser ainda não consome `sealed ...
permits` numa declaração de classe. Exemplo ilustrativo do que se pretende:

```kf
sealed class Resultado<T> permits Sucesso<T>, Erro<T> {}

class Sucesso<T>(T valor) extends Resultado<T> {}
class Erro<T>(String mensagem) extends Resultado<T> {}
```

Isso garante que `Resultado` só pode ser implementado por `Sucesso` e `Erro`. O compilador pode verificar a completude do `switch`.

## Polimorfismo

```kf
void imprimirArea(Forma forma) {
    print(forma.area())
}

var c = new Circulo(5.0)
var r = new Retangulo(3.0, 4.0)

imprimirArea(c)   // 78.53975
imprimirArea(r)   // 12.0
```

## Próximo passo

[Generics →](11-generics.md)
