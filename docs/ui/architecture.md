[English](architecture.md) | [Português](architecture.pt_BR.md)

# kof.ui — Architecture

> **Status:** Phase 1 (inspection) completed; Phases 2-7 implemented (Component
> Core + Navigation/Router — `go/replace/back/forward/param/current/depth`,
> real in JS, no-op in the JVM — 30-31/08); Phases 8-11 in progress.
> **Last updated:** September 12, 2026
> **Version:** 0.5.0-beta

This document is the map of the `kof.ui` architecture: the real state found in
the inspection, the problems, and the foundation the UI needs before any new
widget. The rule that governs everything: **kof.ui is an interface platform, not
a collection of widgets.**

---

## 1. What kof.ui is today

`kof.ui` is a **stdlib intrinsic** of the compiler: the UI types do not exist
as Kof classes in the source — they are **described in the compiler** and lowered to
`kof_ui_*` runtime functions. Rendering is **KofJS only**: widgets become
DOM, drawn in the native webview (WebKitGTK) or in the browser. In the JVM and
Native targets the handles are **no-ops** (documented; the intention compiles in all, the
realization is JS).

### The kof.ui pipeline

```text
Kof source
  │  Window("título"), Button("+1", () -> ...), Column(listOf(...))
  ▼
SemanticAnalyzer        → recognizes the kof.ui.* types (KofUi.isUiType)
  ▼
CompilerDriver
  ├── constructors: match by NAME (mc.methodName == "Window" | "Label" | ...)
  │     → KofCall(kof_ui_*_new, ...)          [emitExpression, receiver==null]
  └── methods:      match by (type, name, arity)
        → KofUi.instanceMethod → KofCall(kof_ui_*, ...)   [emitUiInstance]
  ▼
Backend
  ├── JvmBackend    → generated KofRuntime.java, kof_ui_* no-ops (JVM/Native)
  ├── NativeBackend → kof_ui_* no-op assembly
  └── JsBackend     → kof-runtime.mjs (CORE_RUNTIME): the real DOM implementation
```

### The 4 implementation points (real files)

| Role | File | Responsibility |
|-------|---------|------------------|
| Type/method registry | `kof-compiler/src/main/java/dev/kof/compiler/KofUi.java` | `kof.ui.*` types, constructors, `staticMethod`, `instanceMethod`, `paletteColor`, `themeColor` |
| Lowering | `kof-compiler/src/main/java/dev/kof/compiler/CompilerDriver.java` | constructors by name (~2405-2519), `emitUiInstance` (~5891), `inferExprType` (~4998) |
| JVM/Native runtime | `JvmRuntime.java` / `NativeRuntime.java` | `kof_ui_*` no-ops |
| JS runtime (the real one) | `JsBackend.java` → `CORE_RUNTIME` | DOM: `kof_ui_*New/Bind/Show/...`, theme, icons, font |

### Existing widgets (inventory)

| Category | Types | Notes |
|-----------|-------|-------|
| Color/theme | `Color`, `Palette`, `Theme` | Color = 32-bit Int `(r<<24|g<<16|b<<8|a)`; Theme light/dark with semantic colors |
| Window | `Window` | title, bind, show/close, size, theme |
| Leaf | `Label`, `Button`, `Input`, `Link`, `Image`, `Icon` | text/fontSize/bold/color; Button has an action (lambda w/ captures); Icon = embedded SVG |
| Layout | `Column`, `Row`, `View`+`Style` | CSS flexbox; gap **fixed 8px**; Style(bg, fg, padding, radius) |
| Drawing | `Canvas` | 2D context: beginPath/closePath/moveTo/lineTo/arc/fill/stroke/setFill/setStroke/setLineWidth/clearRect; renders in `<canvas>` in KofJS |
| Font | `Font` | family, size, bold |

### Fundamental abstractions that ALREADY exist

- **Packed color** (Int) with named palette and semantic theme.
- **DOM tree** implicit via `bind` (window/container → children).
- **Click events** on `Button` via lambda with captures.
- **Theme** light/dark applied to the window.
- **Multiple targets** with diagnosed gap (JVM/Native = no-op).

### Fundamental abstractions that do NOT exist (the real gap)

| Pillar | State | Consequence |
|-------|--------|--------------|
| **Component** | does not exist | each widget is a loose handle; there is no UI node with children/state/identity |
| **Lifecycle** | does not exist | only `bind`/`show`/`remove`; there is no mount/unmount/dispose, nor cleanup |
| **Layout** | partial | only `Column`/`Row` (flexbox, fixed gap); there is no Stack/Box/Spacer/Scroll/Grid/Wrap/Center/Align, nor per-widget gap/padding/flex |
| **Events** | partial | only click on Button, each in its own way (scattered DOM `addEventListener`); there is no target/propagation/stopPropagation/focus/keyboard |
| **Focus** | does not exist | no global management; Tab/Shift+Tab, traversal, restoration absent |
| **Navigation** | does not exist | no Route/Router; a single window |
| **State** | ad-hoc | state in **static class fields** + lambda that updates the label by hand; there is no component state nor invalidation |
| **Rendering/invalidation** | imperative | each click does `label.text = ...` by hand; there is no re-render, diffing, scheduling |
| **Design system** | partial | Theme light/dark + `Color`/`Palette`; the tokens `Spacing`/`Radius`/`Border`/`Elevation`/`Typography` now exist (Fase 10, `D-UI-TOKENS` — compile-time px constants); the semantic theme-to-widget application remains manual |

### Problems found (diagnosis)

1. **The tree is not tracked by the framework.** The DOM is the structure, but the
   runtime does not know parent/child (only `window.__kofNodes[id]` → el). Without
   a tree there is no lifecycle, focus, event propagation or navigation.
2. **Each widget implements its own infrastructure.** `kofUiSetAction` does its
   own `addEventListener`; `View`/`Column`/`Row` do their own
   `appendChild`; color is re-converted to CSS in several places.
3. **State in statics is anti-idiomatic and does not scale.** Captures are photos;
   the example's counter uses static `App.count` + manual `label.text = ...`.
4. **Layout is fixed CSS.** `gap: 8px` hardcoded; no margin/fill/grow/shrink,
   no Stack/Scroll/Grid.
5. **No cleanup.** `remove()` only deletes the el; there is no tree unmounting,
   nor release of listeners/timers → risk of leak.

---

## 2. Proposed architecture (the foundation)

> Widgets are the visible layer built on top of **nine pillars**.
> This document defines the pillars; each becomes a phase with its own implementation,
> tests and docs. **Phase 2 (Component Core) delivers pillar 1 and the
> backbone of pillars 3/4/5/8/9** (tree + state + invalidation + lifecycle),
> which is the base on which the others rest.

### 2.1 The nine pillars

```text
   ┌───────────────────────────────────────────────────────────┐
   │                    kof.ui — platform                       │
   └───────────────────────────────────────────────────────────┘
        Widgets (visible layer, built LATER)
   ┌───────────────────────────────────────────────────────────┐
   │ 1 Component   2 Lifecycle   3 Layout    4 Events          │
   │ 5 Focus       6 Navigation  7 State     8 Rendering       │
   │ 9 Design system (Theme/Token)                             │
   └───────────────────────────────────────────────────────────┘
```

| # | Pillar | Deliverable | Phase |
|---|-------|-----------|------|
| 1 | Component model | UI node with identity, children, state, composition, render | 2 |
| 2 | Lifecycle | mount/update/unmount/dispose + automatic cleanup | 3 |
| 3 | Layout | Row/Column/Stack/Box/Spacer/Scroll/Grid/Wrap/Center/Align; gap/padding/flex | 4 |
| 4 | Events | Event/InputEvent/KeyEvent/MouseEvent; target/propagation/stop | 5 |
| 5 | Focus | global focus, Tab/Shift+Tab traversal, restoration | 6 |
| 6 | Navigation | Route/Router; go/back/forward/replace; params | 7 |
| 7 | State | local/shared/app; minimal invalidation | 8 |
| 8 | Rendering | construction, scheduling, invalidation, partial update | 9 |
| 9 | Design system | Theme + tokens (Color/Type/Spacing/Border/Radius/Elevation) | 10 |

### 2.2 The Component Core (Phase 2) — what will be implemented

**A `Component` is a node in the UI tree.** Every widget is a component. The
core delivers the backbone: tree + reactive state + invalidation + rendering
+ lifecycle + events + effects with automatic cleanup.

Model (idiomatic Kof, small API, no boilerplate):

```kof
// component reactive state + view builder + lifecycle + effects
var app = Component("App")
app.state(0)                                  // initial state (Int)
app.view { s ->                               // view: re-executed on every state change
    Column([
        Label("count: " + s),
        Button("+1", () -> { app.state(s + 1) })   // set state => invalidates => re-render
    ])
}
app.onMount { /* runs once on mount */ }
app.onDispose { /* runs once on unmount */ }
app.effect { /* registration of listener/timer/subscription; automatic cleanup on dispose */ }
window.bind(app)                              // mounts
```

Rules the core guarantees:

- **Tracked tree.** The framework knows the parent/child of each node (source of
  truth), not just the DOM.
- **Composition.** `bind`/children form the tree; one component composes others.
- **Encapsulated state.** state lives in the component; `state(...)` is the only
  mutation path (no 5 ways to store state).
- **Minimal invalidation.** `state(...)` marks **only the component** as dirty and
  schedules a re-render (scheduling), without touching the whole application.
- **Re-render by rebuild + prune (current); reconciliation (Phase 9, pending).**
  today the view builder re-runs and the fresh subtree replaces the previous
  one, pruning the old subtree from the DOM and the registry (§300). The
  **target** is reconciliation by **position + kind** — stable nodes (same
  position + kind) reuse the existing DOM and only the diff (text, props,
  handlers) is updated, without recreating the tree; key-based diffing comes
  with it.
- **Deterministic lifecycle.** mount (view + `onMount`), update (reconcile),
  unmount (`onDispose` + **effects in reverse order** + DOM removal).
- **Automatic cleanup.** listener/timer/subscription registered via `effect`
  are released on unmount — no leak, without the user having to remember.
- **Events with propagation.** `on(type, handler)` centralized; basis for
  bubbling/stopPropagation (Phase 5).

### 2.3 Relationship between components

```text
Window (root/host)
 └── Component "App"            (app root component)
      └── Column                (layout)
           ├── Label            (leaf)
           └── Button           (leaf + action)
                └── (action => state => re-render of App)
```

- **Container/Layout** (`Window`, `Column`, `Row`, `Box`, `Stack`...) have
  children; **leaf** (`Label`, `Button`, `Input`...) do not.
- **Component** (`Component`) is the node that carries **state + view +
  lifecycle + effects**; it is the unit of re-render.
- The graph is a **tree** (each node has a single parent), rooted at the window.

### 2.4 Rendering

1. **Construction:** `view { s -> ... }` executes → returns the view root (a
   layout/leaf node with the child tree).
2. **When it renders:** on mount (once) and on every `state(...)`/`text(...)`/
   `flag(...)` (scheduled invalidation, in batch — *batching* via dirty queue).
3. **How changes are detected:** the state mutation **is** the detection —
   `state(...)` itself is the invalidation point (no polling, no reflection).
4. **Invalidation:** `state(...)` marks the component dirty in the queue; a flush
   (scheduled, not synchronous) reconciles only the dirty components.
5. **Updates applied:** the view builder re-runs and the fresh subtree
   replaces the previous one — the old subtree is pruned from the DOM **and**
   from the node registry (`kofUiRemoveSubtree`, §300), so no handle leaks
   across renders. Node reuse by **position + kind** (updating only the diff)
   and key-based diffing are the **pending** half of Phase 9.

### 2.5 Events

Events live on the node and are centralized in the engine (not in the DOM of each widget).
`on(type, handler)` registers on the node; the engine wires it to the DOM. Propagation (Phase 5):
target → bubbles to the parents, with `stopPropagation`/cancellation. The core already
registers handlers per node (basis of propagation) and **clears them on unmount**.

### 2.6 State

Three scopes (Phase 8 — delivered 18/09: `D-UI-APPSTATE` + §301):

- **Component local:** `state`/`text`/`flag` on the `Component` (delivered).
- **Shared:** an observable `Store` between components (delivered;
  JS `unsubscribe` fixed §301). A store CREATED during a component's
  lifecycle is owned by it and released at unmount (`D-COMPLETE-FIRST` item 4,
  26/09); stores created at app scope — and `AppState`, app by definition —
  stay ownerless and manual.
- **Application:** `AppState(initial)` — create-or-get singleton over the
  Store machinery, reachable from anywhere (delivered, `D-UI-APPSTATE`).

A state change invalidates **only the owning component** — not the application.

### 2.7 Lifecycle (Phase 3 details it; the core already implements it)

Deterministic order:

```text
mount:   (mounts the view) -> onMount()                    [top-down after mounting]
update:  state changed   -> re-render (reconcile)
unmount: onDispose() -> effects() in REVERSE order -> remove DOM
```

Effects (listener/timer/subscription/stream/task) registered via `effect` are
**released automatically** on unmount. No leak, no manual reminder. The same
determinism covers subscriptions and component-owned stores (`D-COMPLETE-FIRST`
item 4, 26/09): the leak locks `uiNodesLive()` / `storesLive()` /
`subscriptionsLive()` all return to 0 after mount/unmount cycles (10k-cycle
proof: `UiLeakLockE2ETest`).

### 2.8 Layout (Phase 4 details it; the core already brings primitives)

The core adds the missing structural primitives, all with
**gap/padding/alignment/flex** via CSS (without the widget calculating position):
`Box`, `Stack`, `Spacer`, `Wrap`, `Grid`, `Center`, `Align` (in addition to the existing
`Row`/`Column`/`View`). `Scroll` comes with the layout layer.

### 2.9 Navigation (Phase 7) — implemented

`Router` namespace: `route(name, component)`, `go(name[, param])`,
`replace(name[, param])`, `back()`, `forward()`, `param()`, `current()`,
`depth()`. Navigating = **swapping the root component**: the engine unmounts the old
one (correct lifecycle + cleanup) and mounts the new one. The component backbone of the core is
what makes this possible (tree + lifecycle + cleanup).

Implementation details (JS target):

- `kofUiRouterShow` unmounts **any mounted route** that is not the destination
  (covers the initial bind of `Window.bind`, which mounts a root component without
  registering `current`). Supported pattern: configure the component (`view`,
  `onMount`, ...) **before** `win.bind`/`Router.go`.
- `Router.go`/`replace` accept 1 or 2 arguments (with or without param).
- `back()`/`forward()` use history stacks; `forwardStack` is cleared when
  navigating forward.
- Tests: `RouterE2ETest` (go with lifecycle, back/forward, unknown route).

### 2.10 Module structure (Phase 11)

**AUDITED 18/09 — delivered as the conceptual structure it declares.** The
engine lives in the compiler (not a standalone `kof-ui/` package), and the
physical files already mirror the map by responsibility:

| conceptual module | where it lives today |
|---|---|
| `core/` (component · state · lifecycle · render · events · input) | `js/JsRuntimeUiComponents.java`, `js/JsRuntimeUiEvents.java`, `js/JsRuntimeUiForms.java`, `js/JsRuntimeUiValidation.java` |
| `layout/` (row · column · stack · box · grid · wrap · spacer) | `js/JsRuntimeUiLayout.java` |
| `navigation/` (router · route) | `js/JsRuntimeUiEvents.java` (Router block, Fase 7) |
| `theme/` (theme · color · typography · spacing · border · radius · elevation) | `KofStyleParser.java` + `KofUiTokens.java` + `Palette` (compiler-side, all four targets) |
| `widgets/` (input · buttons · selection · feedback · data) | `js/JsRuntimeUiWidgets.java` |
| honest no-ops (UI is KofJS) | `jvm/JvmRuntimeUi.java`, `runtime/RuntimeUi.java` (native asm) |

A physical split into `kof-ui/*` packages would move files without changing
behavior or the JS `CORE_RUNTIME` exports — no gain, all risk (rule: small
and stable core); the table above is the module boundary.

```text
kof-ui/  (conceptual — today it lives in the compiler; the engine is the JS CORE_RUNTIME)
  core/       component · state · lifecycle · render · events · input · focus
  layout/     row · column · stack · box · scroll · grid · wrap · spacer
  navigation/ router · route · navigation
  theme/      theme · color · typography · spacing · border · radius · elevation
  widgets/    input · buttons · selection · feedback · navigation · data · overlays · advanced
```

---

## 3. Decisions and honest limits

- **Rendering is KofJS.** JVM/Native remain no-op for UI (documented
  gap, as today). The core runs and is tested on the JS target (GraalJS).
- **API may evolve.** `view { ... }` + `state(...)` is the current form; the
  final format of the "declarative component" may change, but the **architecture**
  (tree, composition, state, invalidation, lifecycle) is stable.
- **No reactivity magic.** Kof has no property observer; the
  state change detection **is** the call to `state(...)`. This is
  explicit but minimal (one method), encapsulated and automatic (no
  manual invalidate, no manual cleanup).
- **Don't rewrite what works.** `Color`/`Palette`/`Theme`, the existing
  widgets and their tests are preserved; the core **extends** them (tree,
  state, lifecycle) without breaking the current behavior.

## 4. Test plans (Phase 2)

- **Components:** mount, update, unmount.
- **Lifecycle:** correct order (mount→update→unmount), cleanup, dispose.
- **Events:** propagation, cancellation, focus (basis).
- **Memory:** unmounting releases listeners; effects run once; **10,000
  mount/unmount stress** + **10,000 event dispatches** without leak.
