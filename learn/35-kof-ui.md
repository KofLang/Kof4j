[English](35-kof-ui.md) | [Português](35-kof-ui.pt_BR.md)

# 35 — kof.ui — Colors, Widgets and Windows

`kof.ui` is Kof's UI platform. Rendering is **KofJS**: the same program
compiles to JVM, Native and JS, but only the JS target draws (via a native
webview with embedded WebKit, or in the browser). On the other targets the
handles are no-ops — the program runs without rendering.

## Colors, Palettes and Themes

Colors are 32-bit values (`0xRRGGBBAA`) with channels 0-255:

```kof
var red = Color(255, 0, 0)          // r, g, b (alpha = 255)
var rgba = Color.rgba(10, 20, 30, 128)
var v = Color(0xFF0000FF)           // packed value directly
red.red()        // 255
red.isOpaque()   // true
red.withAlpha(64).toCss()           // rgba(255, 0, 0, 64)
Palette.red.toCss()                 // rgb(255, 0, 0)
```

Named colors: `Palette.red/green/blue/yellow/cyan/magenta/black/white/
gray/orange/purple/pink/brown/transparent`.

Themes with semantic colors:

```kof
var dark = Theme.dark()             // or Theme.light()
dark.isDark()                       // true
dark.background().toCss()           // rgb(18, 18, 18)
dark.primary()                      // Color
```

Design-system tokens (Fase 10) — compile-time `Int` (px) constants that name
the design intent:

```kof
Spacing.md          // 16  (xs=4 sm=8 md=16 lg=24 xl=32)
Radius.lg           // 8   (none=0 sm=2 md=4 lg=8 full=9999)
Border.thin         // 2   (hairline=1 thin=2 medium=4 thick=8)
Elevation.md        // 2   (none=0 sm=1 md=2 lg=3 xl=4)
Typography.lg       // 20  (xs=12 sm=14 md=16 lg=20 xl=24 hero=32)
l.setFontSize(Typography.lg)
```

An unknown member (`Spacing.huge`) or a method call (`Spacing.of(4)`) is
`SEM079` — the tokens hold constants, never a silent 0.

## Shared and application state (Fase 8)

`Store(initial)` is shared observable state between components;
`AppState(initial)` is the **same store, one per application** — reachable
from anywhere, no handle passed around:

```kof
main() {
    var store = Store(0)             // shared: give the handle to who needs it
    store.subscribe((v: Int) -> { println("s=" + v) })
    store.set(1)                     // prints s=0 (current on subscribe), s=1
    var h = (v: Int) -> { println("t=" + v) }
    store.subscribe(h)
    store.unsubscribe(h)             // real since §279 — stops delivery
    AppState(0).set(7)               // app root: any component reads it...
    println(AppState(0).get())       // ...the same value, 7
}
```

`AppState(initial)` is create-or-get: the first call creates with `initial`,
later calls return the same handle (their `initial` is ignored). Methods are
exactly the Store's (`get`/`set`/`subscribe`/`unsubscribe`) — decision
`D-UI-APPSTATE`. The observable lives in KofJS; on JVM/Native the operations
are documented no-ops (UI is KofJS).

**Ownership is part of the lifecycle.** A store or subscription created
*during* a component's lifecycle (view render / `onMount` / `effect`) belongs
to that component and is released automatically at unmount — no `unsubscribe`
reminder (`D-UI-AUTOUNSUB` + `D-COMPLETE-FIRST` item 4). Created outside any
component it is app-scoped and manual by design; `AppState` is always
app-scoped. Three probes are the leak locks — `uiNodesLive()`,
`storesLive()`, `subscriptionsLive()` must return to 0 after mount/unmount
cycles (locked at 10k cycles in `UiLeakLockE2ETest`).

## Windows and Widgets

```kof
main() {
    var w = Window("Minha Janela")
    var label = Label("Olá, Kof!")

    w.title = "Kof App"             // title bind
    w.bind(label)                   // mounts the label in the window
    w.show()                        // serializes and displays
}
```

| Operation | Description |
|----------|-----------|
| `Window("título")` | creates a window (one per handle) |
| `w.title = v` / `w.title()` | title bind |
| `w.bind(widget)` | mounts a widget in the window |
| `w.show()` / `w.close()` | shows/closes the window (the window itself) |
| `w.size(largura, altura)` | sizes the window content |
| `w.theme = Theme.dark()` | applies the theme (background/text of the content) |

### Label

```kof
var l = Label("texto")
l.text = "novo"                     // text bind
l.fontSize = 24                     // px
l.bold = true                       // bold
l.color = Palette.red               // text color
l.text()                            // reads the text
l.remove()
```

### Button (with action)

```kof
var b = Button("Salvar", () -> salvar())
b.text = "Salvando..."
```

The second argument is a **lambda**; it can **capture** variables from the
outer scope (read-only copies):

```kof
class App {
    static Int count = 0
}

main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        App.count = App.count + 1
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

Mutable state between clicks lives in **static class fields** (the capture is
a snapshot of the value at creation time). Each click updates the label —
live, in the webview.

### Input

```kof
var i = Input("digite aqui")        // editable text field
i.text = "preenchido"               // value bind
i.text()                            // reads the current value
i.remove()
```

### Composition: Column, Row, View and Style

```kof
var col = Column(listOf(l1, l2))    // stacks vertically
var row = Row(listOf(l1, l2))       // aligns horizontally

var style = Style(Palette.black, Palette.white, 16, 8)
var view = View(style)              // box with background/padding/radius
view.bind(col)                      // composes in a tree
w.bind(view)
```

`Style(background, foreground, padding, radius)` — colors via `Color`,
`padding`/`radius` in px.

#### Declarative style (CSS-like string)

`Style("<declarations>")` takes idiomatic CSS. The compiler parses and
validates it (D-UI-STYLE/UI007): an unknown property is a compile-time
error (`SEM076`), a malformed declaration `SEM077` and an invalid value
`SEM078` — never a silent fallback.

```kof
var style = Style("background: #ff0000; padding: 8; border-radius: 4")
var view = View(style)
```

- **Colors** accept hex CSS (`#rgb`/`#rrggbb`/`#rrggbbaa`), CSS color names
  and the `Palette` names.
- **Lengths**: a bare integer means `px`; `px`/`%`/`em`/`rem` are accepted.
- The argument must be a **literal** (the parse is at compile time) — for a
  computed color use the 4-Int form.
- `setStyle(style)` applies a style to **any DOM widget**, not only `View`:
  `label.setStyle(style)`.

Real in KofJS; documented no-op on JVM/Native/Script (like the 4-Int form).

## Canvas 2D

Canvas allows free 2D drawing — graphics, visualizations. A full game
surface (frame loop, sprites, sound, video) is Kof's **own engine**, still
plan-only — `docs/development/future/graphics-gaming-plan.md`
(`DECISIONS.md` §D-GRAPHICS-GAMING + addenda); none of that surface compiles
today.
It renders into `<canvas>` in the DOM (KofJS). JVM/Native are no-ops.

```kof
var c = Canvas(400, 300)         // creates canvas

c.setFill(Palette.blue)         // fill color
c.setStroke(Palette.black)      // stroke color
c.setLineWidth(2)               // stroke width

c.beginPath()                    // path start
c.moveTo(200, 150)              // moves pen
c.lineTo(300, 200)              // draws line
c.arc(200, 150, 100, 0.0, 3.14) // arc (radians)
c.closePath()                    // closes path
c.fill()                         // fills
c.stroke()                       // outlines

c.clearRect(0, 0, 400, 300)    // clears rectangle
c.remove()                       // removes from DOM
```

### Pie chart

```kof
var c = Canvas(400, 300)
var PI = 3.14159265358979
var cx = 200
var cy = 150
var r = 100

var dados = listOf(45, 25, 20, 10)
var cores = listOf(Palette.blue, Palette.red, Palette.green, Palette.orange)
var total = 100

var inicio = 0.0
for (var i in dados) {
    var fim = inicio + (i * 2.0 * PI) / total
    c.setFill(cores[i])
    c.beginPath()
    c.moveTo(cx, cy)
    c.arc(cx, cy, r, inicio, fim)
    c.closePath()
    c.fill()
    inicio = fim
}
```

| Operation | Description |
|----------|-----------|
| `Canvas(largura, altura)` | creates 2D canvas |
| `c.beginPath()` / `c.closePath()` | manages path |
| `c.moveTo(x, y)` / `c.lineTo(x, y)` | draws with the pen |
| `c.arc(x, y, r, inicio, fim)` | arc in radians |
| `c.fill()` / `c.stroke()` | fills/outlines path |
| `c.setFill(cor)` / `c.setStroke(cor)` | sets colors |
| `c.setLineWidth(largura)` | stroke width |
| `c.clearRect(x, y, w, h)` | clears rectangle |
| `c.remove()` | removes from DOM |

## Router (Phase 7, 31/08)

Navigation by swapping the root component (unmount the old + mount the new):

```kf
var home = Component(0)
var detail = Component(0)
home.view((s: Int) -> { return Label("home") })
detail.view((s: Int) -> { return Label("detail:" + Router.param()) })

var w = Window("App")
w.bind(home)
Router.route("home", home)
Router.route("detail", detail)
Router.go("detail", "42")   // navigates with a parameter
```

| Operation | Description |
|----------|-----------|
| `Router.route("nome", component)` | registers the route |
| `Router.go("nome")` / `Router.go("nome", "param")` | navigates (`false` if the route does not exist) |
| `Router.replace("nome"[, "param"])` | navigates without pushing onto history |
| `Router.back()` / `Router.forward()` | history (stacks) |
| `Router.current()` | active route |
| `Router.param()` | parameter of the current navigation |
| `Router.depth()` | history depth |

`Component` (`.view`, `.onMount`, `.onDispose`) is the mountable unit —
the router unmounts the old component and mounts the new one. Real JS; on the
JVM/Native targets the router is a no-op (like the rest of `kof.ui`).

## Execution

`kof run --target=js`:

1. compiles the program to `Default.mjs` + `kof-runtime.mjs`;
2. runs it in the embedded runner (GraalJS) — validation and snapshot;
3. writes the **interactive app** (`index.html` + modules) and opens it in the
   native webview (`bin/kof-webview`, embedded WebKitGTK) — the page runs the
   program for real: real DOM, click events, input editing;
4. **closing the window = terminating the program** (the runner waits for the webview).

Without the native webview, it falls back to the system browser (`xdg-open`/
`open`/`rundll32`). On JVM and Native the handles are no-ops (nothing is
rendered).

## Representation

- `Color`, `Theme` and all widget handles are `Int` — no objects.
- Color channels are bit manipulation in the compiler — zero cost.
- `toCss()` is the only point with runtime (identical across the three targets).
- JVM: kof.ui handles are boxed/unboxed into object slots
  (e.g.: `List<Label>`) via `Integer`.
- Lambdas with captures: private final fields + constructor in the synthetic
  class; `invoke()` copies the fields into locals (read-only snapshot).

## References

- `kof-compiler/src/main/java/dev/kof/compiler/KofUi.java` (registry)
- `kof-compiler/src/main/java/dev/kof/compiler/JsBackend.java` (runtime JS)
- `native/webview/kof-webview.c` (headerless WebKitGTK shell)
- Tests: `kof-compiler/src/test/java/dev/kof/compiler/UiE2ETest.java`,
  `WindowE2ETest.java`
