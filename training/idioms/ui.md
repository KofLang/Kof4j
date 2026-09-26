[English](ui.md) | [Português](ui.pt_BR.md)

# Idioms — kof.ui (Canvas 2D)

## Canvas: pie chart

**BAD — building SVG manually with strings:**
```kof
// ❌ NO — manual SVG string manipulation
var svg = "<svg viewBox='0 0 36 36'><circle cx='18' cy='18' r='15' fill='none' stroke='blue' stroke-dasharray='45 55'/></svg>"
var img = Image("data:image/svg+xml," + svg)
```

**GOOD — use Canvas with beginPath/arc/fill:**
```kof
// ✅ IDIOMATIC — Canvas draws directly
var c = Canvas(400, 300)
var PI = 3.14159265358979
c.setFill(Palette.blue)
c.beginPath()
c.moveTo(200, 150)
c.arc(200, 150, 100, 0.0, PI)
c.closePath()
c.fill()
```

**Why:** Canvas is the platform's 2D drawing primitive. Manual SVG
is verbose and fragile; Canvas is declarative and performant.

## Canvas: colored arc

**BAD — calculating coordinates manually with sin/cos:**
```kof
// ❌ NO — calculating the arc points by hand
var x1 = cx + r * 0.707  // cos(45°)
var y1 = cy - r * 0.707  // sin(45°)
```

**GOOD — use arc() with radians:**
```kof
// ✅ IDIOMATIC — arc() calculates internally
c.arc(cx, cy, r, 0.0, 1.5708)  // 0 to π/2 (90°)
```

**Why:** `arc()` does the trigonometry internally. Do not reinvent the wheel.

## Canvas: clearing before redrawing

**BAD — creating a new Canvas every frame:**
```kof
// ❌ NO — DOM element leak
c.remove()
var c2 = Canvas(400, 300)
// ... redraw
```

**GOOD — use clearRect:**
```kof
// ✅ IDIOMATIC — clears and redraws on the same canvas
c.clearRect(0, 0, 400, 300)
// ... redraw
```

**Why:** `clearRect` is efficient and preserves the DOM element.

## Forms: input with placeholder

**BAD — without the idiom, the input is born without an input hint (the
placeholder belongs to the widget, not the application):**
```kof
// ❌ NO — input without placeholder; the usage hint stays in the code, not in the UI
var campo = Input("")
```

**GOOD — `Input.setPlaceholder`:**
```kof
// ✅ IDIOMATIC — declarative placeholder on the widget
var campo = Input("")
campo.setPlaceholder("digite aqui")
```

**Why:** `setPlaceholder` is the widget attribute (renders
`placeholder="..."` in the KofJS DOM). Using an empty string or hiding the hint
in the application is reimplementing a platform feature (R2).

## Forms: input type (password/number/email/...)

**BAD — generic text input for password/number (the type belongs to the widget,
not the application):**
```kof
// ❌ NO — password in a text input; the browser does not mask it
var senha = Input("")
```

**GOOD — `Input.setType`:**
```kof
// ✅ IDIOMATIC — declarative type on the widget (text/number/email/password/date)
var senha = Input("")
senha.setType("password")
```

**Why:** `setType` sets the `type` attribute of `<input>` (masks the password,
numeric keyboard on mobile, email validation). Using text for everything is
reimplementing a platform feature (R2).

## Forms: checkbox/radio (setChecked + checked)

**BAD — simulating checkbox state with a separate variable (the state belongs to
the widget, not the application):**
```kof
// ❌ NO — state duplicated outside the DOM
var aceite = false
var caixa = Input("")
caixa.setType("checkbox")
```

**GOOD — `setChecked`/`checked` on the widget:**
```kof
// ✅ IDIOMATIC — the checkbox state lives in the widget
var caixa = Input("")
caixa.setType("checkbox")
caixa.setChecked(true)
if (caixa.checked()) { println("aceito") }
```

**Why:** `setChecked`/`checked` read/write the real state of
`<input>` (property + `checked` attribute). Duplicating the state in a separate
variable diverges from the DOM (R1: intent, not mechanism).

## Forms: image with alt + dimensions

**BAD — `<img>` without alt/dimensions (broken accessibility + layout):**
```kof
// ❌ NO — image without alternative description or size
var logo = Image("logo.png")
```

**GOOD — `Image.setAlt/setWidth/setHeight`:**
```kof
// ✅ IDIOMATIC — alt (a11y) + declarative dimensions
var logo = Image("logo.png")
logo.setAlt("logotipo")
logo.setWidth(120)
logo.setHeight(60)
```

**Why:** `alt` is accessibility (screen readers); `width`/`height`
avoid layout-shift. They are widget attributes (rendered in the KofJS DOM),
not application ones (R2).

## Forms: grouping fields in <form>

**BAD — loose fields without grouping (no form boundary):**
```kof
// ❌ NO — inputs and button outside a <form>
var nome = Input("")
var enviar = Button("enviar")
var col = Column(listOf(nome, enviar))
```

**GOOD — `Form(children)`:**
```kof
// ✅ IDIOMATIC — fields grouped in <form>
var nome = Input("")
var enviar = Button("enviar")
var f = Form(listOf(nome, enviar))
```

**Why:** `Form` renders `<form>` (renders in the KofJS DOM) and
groups the fields — a semantic form boundary. Loose fields
lose the submission/accessibility semantics (R1: intent).

## Widgets: id, class and disabled

**BAD — creating wrappers only to give a widget an id/class:**
```kof
// ❌ NO — View wrapping the input only to "carry" an id
var wrapper = View(campo)
```

**GOOD — `setId`/`setClass`/`setDisabled` on the widget itself:**
```kof
// ✅ IDIOMATIC — attributes belong to the widget (shared family)
var campo = Input("")
campo.setId("nome")
campo.setClass("destaque")
campo.setDisabled(true)
```

**Why:** id/class/disabled are attributes of the DOM element; the
`widget_*` family of kof.ui exposes them on any widget (Label/Button/Input/View/
Link/Image/Icon/Form/Column/Row). Wrapping to work around it is mechanism,
not intent (R1).

## Forms: onSubmit (submission handler)

**BAD — standalone button that calls the logic (the form has no owner of the submission):**
```kof
// ❌ NO — submit loose on a Button, without a Form
var enviar = Button("enviar", () -> salvar())
```

**GOOD — `Form.onSubmit` + `submit()`:**
```kof
// ✅ IDIOMATIC — the form owns the submission
var f = Form(listOf(nome, email))
f.onSubmit(() -> salvar())
f.submit()   // or the user presses Enter in the browser
```

**Why:** `onSubmit` registers the handler on the `<form>` (runs on the
submit event, with `preventDefault` — without reloading the page); `submit()`
submits programmatically. The form semantics stay in the form (R1).

## Forms: multiline text (Textarea)

**BAD — Input with type=text for long text (no line breaks):**
```kof
// ❌ NO — single-line input for a multiline description
var obs = Input("")
```

**GOOD — `Textarea(text)`:**
```kof
// ✅ IDIOMATIC — first-class widget for multiline text
var obs = Textarea("descreva aqui")
obs.setPlaceholder("máx. 500 caracteres")
println(obs.text())
```

**Why:** `Textarea` renders `<textarea>` (multiline, resizable);
`Input` is single-line. Using the right widget is intent, not mechanism (R1).

## Forms: option choice (Select)

**BAD — chaining Inputs/checkboxes for a single choice:**
```kof
// ❌ NO — 3 checkboxes to choose 1 color
var c1 = Input(""); c1.setType("checkbox")
var c2 = Input(""); c2.setType("checkbox")
```

**GOOD — `Select(options)`:**
```kof
// ✅ IDIOMATIC — the list IS the widget
var cor = Select(listOf("vermelho", "verde", "azul"))
cor.setSelected(1)
println(cor.selected())   // index of the active option
```

**Why:** `Select` renders `<select>`/`<option>` (single choice out of N);
`setOptions` swaps the list, `selected`/`setSelected` read/write the index.
Representing the domain (set of options) with the language collection, not
N manual widgets (R3).

## Canvas: drawing and text state (UI009)

**BAD — redrawing without preserving/clearing the context state:**
```kof
// ❌ NO — alpha/transform leak into the next drawings
c.setGlobalAlpha(0.3)
c.fillText("rótulo", 10, 20)
c.setFill(Palette.blue)   // still with alpha 0.3!
```

**GOOD — `save()`/`restore()` around the temporary state:**
```kof
// ✅ IDIOMATIC — the saved block is discarded
c.save()
c.setGlobalAlpha(0.3)
c.transform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
c.fillText("rótulo", 10, 20)
c.restore()
var w = c.measureText("rótulo")   // Double — real text width
```

**Why:** `save`/`restore` stack the context state (alpha, transform,
colors) — without them, an adjustment leaks into all the following drawing.
`measureText` returns a `Double` (width in px) for text layout.

## Canvas: composing images (drawImage)

**GOOD — `drawImage(img, x, y)`:**
```kof
// ✅ IDIOMATIC — the Image is the <img> element of the DOM itself
var logo = Image("data:image/svg+xml,...")
c.drawImage(logo, 5, 5)
```

**Why:** `Image` already materializes an `<img>` at runtime; `drawImage` composes
it into the canvas bitmap without a round-trip through a URL — the platform takes
care of the loading (R2).

## Item lists (Ul/Ol)

**BAD — building `<li>` by hand with Column + Labels:**
```kof
// ❌ NO — reimplementing the list with loose widgets
var col = Column(listOf(Label("maçã"), Label("uva")))
```

**GOOD — `Ul(items)` / `Ol(items)`:**
```kof
// ✅ IDIOMATIC — the language collection BECOMES the HTML list
var frutas = Ul(listOf("maçã", "uva"))
frutas.setItems(listOf("manga"))
var passos = Ol(listOf("primeiro", "segundo"))
```

**Why:** `Ul`/`Ol` take a `List<String>` and materialize `<ul>/<ol>` with
one `<li>` per item — representing the domain (ordered/unordered list)
with the language collection, not N manual widgets (R3).

## Data tables (Table)

**BAD — Column of Rows of Labels for tabular data:**
```kof
// ❌ NO — manual grid
var linha1 = Row(listOf(Label("mel"), Label("26")))
```

**GOOD — `Table(header, rows)`:**
```kof
// ✅ IDIOMATIC — the nested collection BECOMES the table
var t = Table(listOf("nome", "idade"),
              listOf(listOf("mel", "26"), listOf("ana", "30")))
t.setRows(listOf(listOf("bob", "41")))
```

**Why:** `Table` takes a `List<String>` (header) + `List<List<String>>`
(rows) and materializes `<thead>/<tbody><tr><td>` — tabular data with the
language collection, not N manual widgets (R3).

## Grouping and media (Fieldset/Iframe/Video/Audio/Hr)

**BAD — div with a manual border and a title Label to group:**
```kof
// ❌ NO — fake grouping
var g = Column(listOf(Label("credenciais"), user, pass))
```

**GOOD — `Fieldset(children, legend)`:**
```kof
// ✅ IDIOMATIC — the semantic grouping widget
var fs = Fieldset(listOf(user, pass), "credenciais")
```

**Why:** `Fieldset` materializes `<fieldset>` + `<legend>` — semantic
form grouping with a title, not an invented bordered div (R3).

**Media and separators:** `Iframe(url)` → `<iframe src>`, `Video(url)`/
`Audio(url)` → `<video|audio controls src>`, `Hr()` → `<hr>` — each one is
a first-class widget (with `.remove()`), not manual markup.

```kof
var fr = Iframe("https://example.org")
var v = Video("clip.mp4")
var a = Audio("som.mp3")
var h = Hr()
```

## Events with payload (Event.key/value/x/y/target/relatedTarget)

**BAD — global handler without payload, manual mutation:**
```kof
// ❌ NO — event without data, guessed global state
campo.on("keydown", () -> { processar("") })
```

**GOOD — the handler reads the payload of the real DOM event:**
```kof
// ✅ IDIOMATIC — the event carries key/value/position/target
campo.on("keydown", (e: Event) -> { campo.setPlaceholder("tecla: " + e.key()) })
campo.on("input",   (e: Event) -> { filtro.set(e.value()) })
campo.on("click",   (e: Event) -> { println(e.x()) })
campo.on("click",   (e: Event) -> { println(e.target()) })        // id of the origin node
campo.on("focus",   (e: Event) -> { println(e.relatedTarget()) }) // node it came from
```

**Why:** `Event.key()/value()/x()/y()/target()/relatedTarget()` read the
real DOM event (KeyboardEvent.key, target.value, clientX/Y, target.id,
relatedTarget.id) — the payload comes from the browser, not from manual global
state (R3). `target()` returns the **id** of the node that originated the event
(fallback to the lowercase `tagName` when the node has no `setId`; `""` outside
the browser). `relatedTarget()` likewise for the related node (focus/mouse).
Works on any DOM widget via `.on(type, handler)`.

## Declarative style (CSS-like string)

**BAD — manual per-property setters, Java-like ceremony:**
```kof
// ❌ NO — one call per property, and only View had a style
var v = View(Style(Palette.white, Palette.black, 8, 4))
```

**GOOD — one idiomatic CSS string, parsed and validated in the compiler:**
```kof
// ✅ IDIOMATIC — parse/validate at compile time (SEM076/076/077, never silent)
var card = Style("background: #ffffff; padding: 8; border-radius: 4")
var v = View(card)
var l = Label("titulo")
l.setStyle(card)                       // any DOM widget, not only View
```

**Why:** the compiler owns the parse (D-UI-STYLE/UI007): hex/name/`Palette`
colors, bare Int = px, `px`/`%`/`em`/`rem`, and a typed whitelist — an unknown
property is `SEM076`, a malformed declaration `SEM077`, an invalid value
`SEM078`. Never a silent fallback to `node.style` (R6). Real in KofJS;
documented no-op on JVM/Native/Script, like the 4-Int `Style`.

## Design-system tokens (Spacing/Radius/Border/Elevation/Typography)

**BAD — hard-coded magic numbers (the design intent is unnamed):**
```kof
// ❌ NO — 16 and 4 are literals; nobody knows what they mean
var card = Style("background: #ffffff; padding: 16; border-radius: 4")
l.setFontSize(20)
```

**GOOD — name the design intent with the token scales (Fase 10, D-UI-TOKENS):**
```kof
// ✅ IDIOMATIC — Spacing/Radius/Border/Elevation/Typography.<name> = Int px
var card = Style("background: #ffffff; padding: 16; border-radius: 4")  // literal still required by Style
l.setFontSize(Typography.lg)        // 20
var pad = Spacing.md                 // 16
var rad = Radius.md                  // 4
```

**Why:** tokens are compile-time constants (Int px — D-UI-STYLE Q2), folded
by the same idiom as `Palette`; because the fold is in the shared frontend,
all four targets carry the same value. An unknown member (`Spacing.huge`)
or a method call on a namespace (`Spacing.of(4)`) is `SEM079` (R6 — never a
silent 0). Scales (8px grid): `Spacing` xs/sm/md/lg/xl = 4/8/16/24/32 ·
`Radius` none/sm/md/lg/full = 0/2/4/8/9999 · `Border` hairline/thin/medium/
thick = 1/2/4/8 · `Elevation` none/sm/md/lg/xl = 0/1/2/3/4 · `Typography`
xs/sm/md/lg/xl/hero = 12/14/16/20/24/32.

## Application state without prop-drilling (Fase 8)

**BAD (prop-drilling):**

```kof
main() {
    var theme = Store(0)
    buildHeader(theme)              // thread the handle down...
}
Header buildHeader(Store theme) {   // ...through every layer
    return buildLogo(theme)
}
```

**PREFERRED:**

```kof
main() {
    AppState(0).set(1)              // root store: one per application
}
Label buildLogo() {
    var l = Label("")
    AppState(0).subscribe((v: Int) -> { l.text = "mode " + v })   // reach it directly
    return l
}
```

**Why:** `AppState(initial)` is the application-scoped root store —
create-or-get singleton over the `Store` machinery (`D-UI-APPSTATE`): the
first call creates with `initial`, later calls return the same handle.
Components read it where they need it instead of carrying handles through
layers. Methods are exactly the Store's; `unsubscribe` is real since §279,
and lifecycle ownership shipped: subscriptions (`D-UI-AUTOUNSUB`, 18/09) and
component-owned stores (`D-COMPLETE-FIRST` item 4, 26/09) are released
automatically at unmount — `AppState` is never attributed (app by design). The
observable lives in KofJS; JVM/Native are documented no-ops.

## Let the component own its subscriptions and stores (leak locks)

**BAD (manual reminder — the leak that never shows in one cycle):**

```kof
main() {
    var win = Window("App")
    var store = Store(0)
    var i = 0
    while (i < 1000) {
        var page = Component(i)
        page.view((s: Int) -> {
            store.subscribe((v: Int) -> {})   // accumulates every re-render;
            return Label("x")                 // nobody ever unsubscribes
        })
        win.bind(page)
        page.remove()
        i = i + 1
    }
}
```

**PREFERRED (create in the lifecycle, and the lifecycle releases):**

```kof
main() {
    var win = Window("App")
    var page = Component(0)
    page.view((s: Int) -> {
        var inner = Store(s)                   // owned by THIS component
        inner.subscribe((v: Int) -> {})        // bound to THIS lifecycle
        return Label("x")
    })
    win.bind(page)
    page.remove()                              // store + subscription die here
}
```

**Why:** a store or subscription created **during a component's lifecycle**
(view render / `onMount` / `effect`) is bound to that component and released
deterministically at unmount — no `unsubscribe` reminder, no GC hope
(`D-UI-AUTOUNSUB` + `D-COMPLETE-FIRST` item 4). Created **outside** any
component it is app-scoped and stays manual by design; `AppState` is always
app-scoped. Three probes lock it: `uiNodesLive()`, `storesLive()`,
`subscriptionsLive()` must return to 0 after mount/unmount × N
(`UiLeakLockE2ETest`, 10k cycles). Real on KofJS; JVM/Native keep the
documented no-op faces.
