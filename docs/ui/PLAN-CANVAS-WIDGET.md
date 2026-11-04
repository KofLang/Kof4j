[English](PLAN-CANVAS-WIDGET.md) | [Português](PLAN-CANVAS-WIDGET.pt_BR.md)

# PLAN-CANVAS-WIDGET — Canvas 2D for kof.ui

> **Version:** 0.1.0 · **Date:** 06/09/2026 · **Status:** Implemented · consolidated in `docs/` 12/09 (CANVAS001 CLOSED `5a9cac46`; reproved green 12/09)
> **Branch:** beta-0.3.0

---

## 1. Problem

`kof.ui` has no drawing primitives. Charts (pie, bar, line),
animations and custom visualizations are impossible — the most one does
are colored rectangles (`View`+`Style`).

The universal-platform Stages 4–7 (`docs/development/universal-platform-stages.md`, promoted 03/11) foresee "basic plot via kof.ui"
as lightweight visualization (SVG/`kof.ui` + FFI). The `Canvas` widget is the
concrete implementation of that need.

## 2. Solution

`Canvas` widget that maps to `<canvas>` + `getContext("2d")` in the DOM
(KofJS). API inspired by HTML5 Canvas 2D — familiar to any
web developer.

**Targets:**
- **JS (KofJS):** real rendering via the browser/webview Canvas API
- **JVM/Native:** no-op handles (like all of `kof.ui`)

## 3. Kof API

### Constructor

```kof
var c = Canvas(400, 300)     // creates canvas with width × height
```

### Path methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `beginPath()` | `→ void` | Starts a new path |
| `closePath()` | `→ void` | Closes the current path |
| `moveTo(x, y)` | `(Int, Int) → void` | Moves the pen to (x, y) |
| `lineTo(x, y)` | `(Int, Int) → void` | Draws a line to (x, y) |
| `arc(x, y, r, start, end)` | `(Int, Int, Int, Double, Double) → void` | Arc in radians |

### Style methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `setFill(color)` | `(Color) → void` | Fill color |
| `setStroke(color)` | `(Color) → void` | Stroke color |
| `setLineWidth(w)` | `(Int) → void` | Stroke thickness |

### Rendering methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `fill()` | `→ void` | Fills the current path |
| `stroke()` | `→ void` | Outlines the current path |
| `clearRect(x, y, w, h)` | `(Int, Int, Int, Int) → void` | Clears a rectangle |

### Lifecycle

| Method | Signature | Description |
|--------|-----------|-------------|
| `remove()` | `→ void` | Removes the canvas from the DOM |

## 4. Example: Pie Chart

```kof
import kof.ui.*

main() {
    var w = Window("Vendas por Categoria")
    w.size(500, 400)

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

    w.bind(c)
    w.show()
}
```

## 5. Modified files

| File | Changed |
|---------|--------|
| `KofUi.java` | CANVAS type + instanceMethod |
| `ExpressionStaticCallLowerer.java` | constructor lowering |
| `CompilerDriver.java` | emitUiInstance Canvas condition |
| `JsRuntimeOps.java` | registration kof_ui_canvas_* |
| `JsRuntimeUiWidgets.java` | JS DOM (13 functions) |
| `JvmRuntimeUi.java` | JVM no-ops |
| `JvmRuntimeCallDescriptors.java` | descriptors |
| `RuntimeUi.java` | native x86_64 no-ops |
| `UiE2ETest.java` | tests |

## 6. Design decisions

1. **API identical to HTML5 Canvas 2D** — familiar, industry standard,
   don't reinvent the wheel.
2. **arc() in radians (Double)** — Canvas standard. Kof supports Double as a
   parameter of UI functions via `Type.PrimitiveType.DOUBLE`.
3. **No counterclockwise** — `arc(x, y, r, start, end)` without the 6th argument.
   It can be added later if needed.
4. **Context stored in JS** — the canvas element and the 2D context are
   stored together in `window.__kofNodes` (id → {canvas, ctx}).
5. **Color via packed Color** — same system as `Label.setColor()`, with
   `kofUiColorToCss()` for conversion.

## 7. Status

- [x] Implemented (06/09/2026)
- [x] UiE2ETest tests (JVM + Native + JS)
- [x] Doc updated (architecture.md, learn/35-kof-ui.md)
