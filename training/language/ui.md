# kof.ui — Color, Palette e Theme

Fatos sobre a fundação de UI do Kof. Use para responder perguntas sobre
cores, paletas e temas.

## Color

- `Color(r, g, b)` — canais 0-255, alpha 255.
- `Color.rgba(r, g, b, a)` — com alpha.
- `Color(valorEmpacotado)` — o valor cru `0xRRGGBBAA`.
- Layout: `(r << 24) | (g << 16) | (b << 8) | a`.
- `red()`, `green()`, `blue()`, `alpha()` — canais 0-255.
- `isOpaque()` — Bool (alpha == 255).
- `withAlpha(a)` — nova Color com alpha trocado.
- `toCss()` — `rgb(r, g, b)` quando opaca, `rgba(r, g, b, a)` quando não.

Exemplos:

```kof
Color(255, 0, 0).toCss()            // rgb(255, 0, 0)
Color.rgba(10, 20, 30, 128).toCss() // rgba(10, 20, 30, 128)
Color(0xFF0000FF).red()             // 255
```

## Palette

Constantes nomeadas: `Palette.red`, `green`, `blue`, `yellow`, `cyan`,
`magenta`, `black`, `white`, `gray`/`grey`, `orange`, `purple`, `pink`,
`brown`, `transparent`.

```kof
Palette.green.toCss()   // rgb(0, 255, 0)
Palette.transparent.alpha()   // 0
```

## Theme

- `Theme.light()` / `Theme.dark()` — o tema (tag 0/1).
- `isDark()` — Bool.
- Cores semânticas: `background()`, `surface()`, `primary()`,
  `secondary()`, `text()`, `error()` — Color.

Dark: background `rgb(18, 18, 18)`, text `rgb(255, 255, 255)`.
Light: background `rgb(255, 255, 255)`, text `rgb(0, 0, 0)`.

```kof
var dark = Theme.dark()
dark.background().toCss()   // rgb(18, 18, 18)
```

## Semântica entre targets

- Color/Theme são valores Int de 32 bits — o compilador manipula os canais
  com bitwise; `toCss()` usa helpers de runtime idênticos em JVM, Native e
  JS.
- A renderização (widgets → DOM) é **KofJS only** e está implementada:
  `Window`/`Label`/`Button`/`Input`, `Column`/`Row`, `View`+`Style`,
  eventos por lambda com capturas, webview nativo (`bin/kof-webview`,
  WebKitGTK). JVM/Native: handles no-ops.
- Não há JavaFX, AWT ou dependência de GUI em nenhum backend.

## Router (Fase 7)

Navegação por troca de componente raiz: `Router` é namespace (não tipo).

- `Router.route(name, component)` — registra rota.
- `Router.go(name)` / `Router.go(name, param)` — navega (unmount do antigo + mount do novo).
- `Router.replace(name[, param])` — navega sem empilhar no histórico.
- `Router.back()` / `Router.forward()` — histórico (pilhas; `forwardStack` limpo ao ir para frente).
- `Router.param()` — `String` do param da rota atual.
- `Router.current()` — `String` da rota atual.
- `Depth`: `Router.depth()` — `Int`.
- Real no target JS (KofJS); JVM/Native: no-op handles.
- Ver `docs/ui/architecture.md` §2.9 e `RouterE2ETest`.

## Idioma KofScript (0.3.22-beta)

```kof
let x = 5
var app = Window("Hi", Label("Olá"))
```

## Referência

- `learn/35-kof-ui.md`
- `kof-compiler/src/test/java/dev/kof/compiler/UiE2ETest.java` (29 testes)