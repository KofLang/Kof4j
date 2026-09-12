# Composição visual por objetos

**Updated:** 0.3.22-beta (Sep 2026) (02 Sep 2026)

> O visual é um grafo de objetos. Nada de templates, XML ou strings mágicas:
> a interface é código Kof tipado, compilado e verificado como qualquer outro.

## A ideia

Uma interface é uma **árvore de objetos** composta em código. Cada elemento
visual é um objeto; cada objeto carrega seu próprio estilo; o estilo é feito
de `Color`, `Int` e `String` — tipos da linguagem, nunca textos soltos.

```kof
class Style {
    Color background
    Color foreground
    Int padding
    Int radius

    constructor(Color background = Colors.surface,
                Color foreground = Colors.text,
                Int padding = 12,
                Int radius = 8) {
        this.background = background
        this.foreground = foreground
        this.padding = padding
        this.radius = radius
    }
}
```

## Composição

```kof
class View {
    Style style
    List<Node> children

    constructor(Style style, List<Node> children) {
        this.style = style
        this.children = children
    }
}

class Text {
    String content
    Style style

    constructor(String content, Style style) {
        this.content = content
        this.style = style
    }
}
```

## A paleta

Cores de 32 bits (`0xAARRGGBB`) com nomes, não códigos:

```kof
class Colors {
    static Int primary    = 0xFF6750A4
    static Int background = 0xFF121212
    static Int text       = 0xFFE0E0E0
    static Int error      = 0xFFCF6679
}
```

## Montando uma tela

```kof
View homeView() {
    return View(
        Style(background: Colors.background, padding: 16),
        [
            Text("Bem-vinda", Style(color: Colors.primary, bold: true)),
            Button("Entrar", () => login())
        ]
    )
}
```

## Por que objetos

- **Tipado**: um estilo com campo errado não compila.
- **Composável**: a tela é um valor como qualquer outro — pode vir de função,
  de lista, de condição.
- **Multi-target**: a mesma árvore de objetos vira o que cada backend souber
  desenhar (terminal, web, nativo).

Ver também: `docs/stdlib/COLOR.md` e `learn/35-ui-and-styling.md`.