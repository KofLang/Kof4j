# 35 — UI e Estilização

> O visual é orientado a objetos: uma interface é uma árvore de objetos
> composta em código. A cor é um cidadão de 32 bits com paleta nomeada —
> nada de converter hex ou ANSI na mão.

## A filosofia

Em Kof, o visual não é um mundo à parte. Não há template, não há XML, não há
linguagem de marcação. Há apenas **objetos compostos por objetos**, com o
mesmo type system, o mesmo compilador e os mesmos backends do resto do
código.

```text
View
├── Style { Color background, Int padding, ... }
├── Column
│   ├── Text "Bem-vinda"  (Style { Color primary, bold })
│   └── Button "Entrar"   (action → login())
└── Style { Color surface, ... }
```

## Cor de 32 bits

Uma cor é um `Int` de 32 bits com semântica ARGB (`0xAARRGGBB`):

```kof
var primary = 0xFF6750A4   // literais hex são nativos
```

A paleta nomeada evita conversões:

```kof
class Colors {
    static Int primary    = 0xFF6750A4
    static Int background = 0xFF121212
    static Int surface    = 0xFF1E1E1E
    static Int text       = 0xFFE0E0E0
    static Int error      = 0xFFCF6679
    static Int success    = 0xFF4CAF50
    static Int warning    = 0xFFFFB74D
}
```

E o tipo sabe se apresentar:

```kof
class Color {
    Int value

    constructor(Int value) {
        this.value = value
    }

    Int red()   { return (this.value >> 16) & 0xFF }
    Int green() { return (this.value >> 8) & 0xFF }
    Int blue()  { return this.value & 0xFF }
    Int alpha() { return (this.value >> 24) & 0xFF }

    String ansi() {
        return "\u001b[38;2;" + this.red() + ";" + this.green() + ";" + this.blue() + "m"
    }
}
```

## Estilo

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

## Composição de tela

```kof
View homeView() {
    return View(
        Style(background: Colors.background, padding: 16),
        Column([
            Text("Bem-vinda", Style(color: Colors.primary, bold: true)),
            Button("Entrar", () -> login())
        ])
    )
}
```

## Por que essa abordagem

- **Um type system** — um estilo com campo errado não compila.
- **Composição** — a tela é um valor: função, lista, condição, tudo vale.
- **Multi-target** — a mesma árvore de objetos é desenhada por cada backend.
- **Zero conversão** — a paleta é a API; hex e ANSI são detalhes internos.

## Estado real (0.3.22-beta)

Esta visão está **implementada** como `kof.ui` (renderização KofJS):
`Window`, `Label` (text/fontSize/bold/color), `Button` (ação por lambda com
capturas), `Input`, `Column`/`Row`, `View`+`Style(background, foreground,
padding, radius)`, `w.theme = Theme.dark()`, e o **Router** (Fase 7, 31/08:
`Router.route/go/replace/back/forward/current/param/depth` + `Component` com
lifecycle `onMount`/`onDispose`). A execução abre o webview
nativo (WebKitGTK) e fechar a janela encerra o programa. Ver
[`learn/35-kof-ui.md`](35-kof-ui.md) e [`learn/37-kofjs.md`](37-kofjs.md).

Ver também: `docs/stdlib/COLOR.md` (paleta e semântica),
`training/idioms/composition.md` (o padrão) e
`training/idioms/collections.md`.