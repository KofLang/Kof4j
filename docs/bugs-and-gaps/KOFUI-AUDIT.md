[English](KOFUI-AUDIT.md) | [Português](KOFUI-AUDIT.pt_BR.md)

# KofUI — Coverage audit (Phase 4 — ex-PLATFORM-PLAN, `docs/development/DECISIONS.md` §D-PLATFORM)

> **Status:** AUDIT (07/09) — matrix of gaps `UI00x`. Source: code
> (proof, not memory). Scope: `kof.ui` (widgets/DOM). Browser Web APIs
> (fetch/WS/storage) are **Phase 5 (KofJS)** — marked here only as a boundary.
> Convention (R6): every gap has a code + diagnostic; never a silent no-op.

## 1. Compiler registry (`KofUi.java`, 539 lines — was 383 in the 07/09 audit)

> **⚠️ 07/09 snapshot — the inventory below is historical.** Recount 17/09:
> `isUiType` covers **36 types** (the text lists 24) — Color, Theme, Label,
> Button, Input, Textarea, Select, Ul, Ol, Table, Column, Row, Form, View,
> Style, Window, Link, Image, Icon, Font, Component, Event, the layout set
> (Box, Stack, Spacer, Wrap, Grid, Center, Align) and Store, Canvas, Fieldset,
> Iframe, Video, Audio, Hr. The `UI00x` matrix and R6 convention remain valid.
> **`UI001/UI002` recount (17/09):** `UI002` ✅ confirmed DONE 08/09 (the
> interpreter prints the warning once via `ui002Warned`); `UI001` **still
> open** — the 07/09 link errors are fixed (`RuntimeUi` carries the no-op
> stubs, `COMP001` gone) but Native is still a **silent** no-op (no
> compile-time diagnostic), so UI001 stands as the only silent-no-op face.

**Types (24 in the 07/09 scan):** Color, Theme, Label, Button, Input, Column, Row, View, Style,
Window, Link, Image, Icon, Font, Component, Event, Box, Stack, Spacer, Wrap,
Grid, Center, Align, Store, Canvas + namespace `Router`.

**Methods per type (summary):**
- `Color`: rgba, red/green/blue/alpha, toCss, withAlpha, isOpaque
- `Theme`: light/dark; background/surface/primary/secondary/text/error/isDark
- `Label`: text/setText, fontSize/setFontSize, bold/setBold, color/setColor, font, remove
- `Button`: text/setText, (action in ctor), remove
- `Input`: text/setText, remove
- `Window`: title, bind, show, close, size
- `Link`: text/setText, url/setUrl, remove · `Image`: src/setSrc, remove
- `Icon`: name/setName, size/setSize, remove
- `Component`: state/stateSet, view, onMount, onDispose, effect, on(type,handler), bind, remove
- `Event`: type, stopPropagation
- `Store`: get, set, subscribe, unsubscribe
- `Canvas`: beginPath, closePath, moveTo, lineTo, arc, fill, stroke, setFill, setStroke, setLineWidth, clearRect, remove
- `Router` (namespace): route, go, replace, back, forward, param, current, depth
- Layout: Box/Stack/Spacer/Wrap/Grid/Center/Align (ctors) · `Palette.<name>` (15 colors)

## 2. Implementation per target

| Target | kof.ui | State | Proof |
|---|---|---|---|
| **KofJS (browser)** | real DOM | `JsRuntimeUi*.java` + `JsRuntimeOps` — createElement + `window.__kofNodes`; real router (31/08) | `KofJsBrowserE2ETest` (Chrome headless; skips if absent), `KofUi*Test` |
| **JVM** | no-op (by design) | `JvmRuntimeUi.java` — all `kof_ui_*` empty (compiles, "runs", does not render) | `docs/backend-parity.md` ("JVM no-op"); `RouterE2ETest` |
| **Native** | **SILENT no-op** | `RuntimeUi.java` emits no-op stubs in asm (113); **07/09: 21 missing stubs (Image/Link/Icon/Font) caused link-error `undefined reference [COMP001]` — FIXED** (parity with JVM). No diagnostic for the no-op (residual R6) | manual E2E 07/09 + `UiE2ETest.mediaWidgetsLinkOnAllTargets` (JVM+Native) |
| **Script (interpreter)** | **SILENT no-op** | `kof-script/` does not know `kof.ui`; interprets and executes with no effect (R6 ❌) | manual E2E 07/09: `run --target script` → "done" rc=0 |
| **Android** | via WebView (KofJS) | `AndroidProjectWriter.java` — outputs KofJS to `assets/kof/`, renders in WebView | docs `backend-parity.md` Phase 7 |

## 3. KofJS — what the real DOM covers today

- **Elements → tags:** Label→span, Button→button, Input→input[type=text],
  Column/Row/View→div, Link→a, Image→img, Icon→span, Style→style,
  Canvas→canvas. (Widgets: `JsRuntimeUiWidgets.java` 42/153/198/232/249/277.)
- **Events:** `Component.on(type, handler)` → `KOF_UI_EV` (15: click, dblclick,
  mousedown/up, mousemove/enter/leave, wheel, keydown/up, focus, blur, input,
  change) + fallback for arbitrary DOM type. `Button` action = click.
  `Event`: type + stopPropagation.
- **Attributes:** value, type (input), href (link), src (img).
- **Inline styles:** color, fontSize, fontWeight, fontFamily, display,
  width/height (canvas).
- **Canvas 2D:** beginPath/closePath/moveTo/lineTo/arc/fill/stroke/setFill/
  setStroke/setLineWidth/clearRect — attaches to `#kof-root` (CANVAS001 closed
  3 targets `5a9cac4`).
- **Window:** `document.title` only (no size/position).
- **Router:** complete (route/go/replace/back/forward/param/current/depth).
- **Store:** get/set/subscribe/unsubscribe (in-process observable).

## 4. Gap matrix (Phase 4)

| Gap | Description | Target | Priority |
|---|---|---|---|
| **UI001** | `kof.ui` on Native = silent no-op (binary runs with no diagnostic). **PARTIALLY FIXED 07/09**: `Image/Link/Icon/Font` **did not link** (`undefined reference [COMP001]` — 21 missing stubs in `RuntimeUi`); added (no-op parity with JVM). Remaining: diagnostic for the silent no-op of the others = design decision (rule 6) | Native | **P0 (R6)** → P2 (residual) |
| **UI002** | `kof.ui` on Script = silent no-op (interpreter executes with no effect). **DONE 08/09** (`7081551`): warning `UI002` **only once** on stderr when `KofInterpreter` resolves a `kof_ui_*` function (message points to `--target=js`); additive — no-op preserved (backward compat), no error (rule 6); test `KofScriptTest.ui002WarnsOnceOnUiCalls` (checks presence + count == 1) | Script | **P0 (R6)** → **DONE** |
| **UI003** | Elements: textarea ✅ DONE 07/09 (`Textarea`); table/tr/td ✅ DONE 07/09 (`Table(header, rows)` data-driven); select/option ✅ (`Select`); ul/ol/li ✅ (`Ul`/`Ol` data-driven); fieldset/legend ✅, iframe ✅, video/audio ✅, hr ✅ (08/09, `358ec80` — `Fieldset(children[, legend])`/`Iframe(url)`/`Video(url)`/`Audio(url)`/`Hr()` + remove; real DOM proven in headless Chrome; `kofSerialize` gained `src` + void-tags) | KofJS | P1 **DONE** |
| **UI004** | Forms: `<form>` ✅ + submit handler ✅ DONE 07/09 (`Form(children)`, `onSubmit`, `submit()` — handler runs in the browser, proof by DOM mutation); fieldset ✅ DONE 08/09 (`Fieldset(children[, legend])`, `358ec80`). `Input` types ✅ (`setType`); checkbox/radio state ✅ (`setChecked`/`checked`); select ✅ (`Select`/`setOptions`/`selected`/`setSelected`) | KofJS | P1 **DONE** |
| **UI005** | Attributes: id ✅ class ✅ disabled ✅ (DONE 07/09 — `setId`/`setClass`/`setDisabled` in DOM widgets, `kof_ui_widget_*` family); placeholder ✅ (`Input.setPlaceholder`); checked ✅; alt/width/height ✅ (`Image.*`); readonly/name ✅ DONE 07/09 (`Input`/`Textarea`.setReadonly(bool)/setName(String) — 6/6 complete points, browser proof: `name=`/`readonly` attributes in outerHTML) | KofJS | P1 **DONE** |
| **UI006** | Events: `Event.type()`/`stopPropagation()` ✅; `key()`/`value()`/`x()`/`y()` ✅ DONE 08/09 (`f0907c2` — real DOM event: `key` from KeyboardEvent, `value` from the target input, `clientX/Y`; `widget.on(type, handler)` exposed for widgets outside the Component tree; `kofUiWidgetOn` now dispatches the kofEv, before it called `fn()` without an event); `target()`/`relatedTarget()` ✅ DONE 08/09 (`3c241ae`+ — id of the origin/related node with tagName fallback; browser proof: `t=campo-main` in the final DOM) | KofJS | P2 **DONE** |
| **UI007** | declarative `style` (idiomatic CSS) — new, with its own parser (Phase 4 plan item). **DONE 17/09** (`D-UI-STYLE`, commit `f7a5ad89`): `Style("<declarations>")` parsed in the compiler, typed whitelist (`SEM076`/`SEM077`/`SEM078`), hex+CSS names+`Palette` names kept verbatim, px/`%`/`em`/`rem`, `setStyle(style)` on every DOM widget — proof: `UiStyleCssE2ETest` 10/10 + 2 real-Chrome tests | KofJS | P1 **DONE** |
| **UI008** | Window: size/position only JVM no-op; KofJS only title (browser does not control window — ok per platform) | JVM/KofJS | P3 |
| **UI009** | Canvas: fillText ✅ measureText ✅ save ✅ restore ✅ transform ✅ setGlobalAlpha ✅ (DONE 07/09 — `UiE2ETest.canvasUi009LinksOnAllTargets` + `KofJsBrowserE2ETest.canvasUi009RunsInRealBrowser`); drawImage ✅ (07/09 — Image→canvas via DOM element) | KofJS | P2 **DONE** |

**Phase 5 boundary (KofJS Web APIs — not kof.ui):** fetch/`WebSocket`/
`EventSource`(SSE)/`localStorage`/`sessionStorage`/`navigator`/`location`/
`history` in the browser = DOM/Fetch/Storage matrix with supportedOn+gapCode of
the Phase 5 plan (today absent in the browser runtime; the current JS "web" is a
GraalJS HttpServer server — residual ws/sse report `WEB004`/`WEB003` at
compile-time since the 16/09 honesty slice).

## 5. Recipe: new method in kof.ui = **6 points** (learned in practice 07/09)

Every new instance method requires the 6 points below — **missing one breaks
a target**. (It was the 6th point — `JvmRuntimeCallDescriptors` — that was
missing in 3 commits: `setPlaceholder`/`setType`/`setChecked`/`checked` compiled
on the JVM but gave `NoSuchMethodError` at runtime; only the KofJS test passed.)

1. **Registry**: `KofUi.instanceMethod()` (case in the type switch).
2. **JS whitelist**: `JsRuntimeOps.java` (list `name.equals("kof_ui_…")` —
   except families already covered by prefix: `link_`/`image_`/`icon_`/
   `canvas_`/`widget_`/`font_`).
3. **JS impl**: `JsRuntimeUi*.java` (exported function; name via
   `JsTypeMapper.capitalizeUiFn`).
4. **JVM stub (source)**: `jvm/JvmRuntimeUi.java` (no-op; Bool=int 0/1;
   String getter → `return ""`).
5. **JVM descriptor**: `jvm/JvmRuntimeCallDescriptors.java`
   (`callDescriptor`) — **without this the bytecode calls the wrong signature
   (default = `(String)Object`) → `NoSuchMethodError`**.
6. **Native stub (asm)**: `runtime/RuntimeUi.java` (void: `ret`;
   int: `xorl/movl`+`ret`; String: `leaq .Lui_empty` + `jmp
   kof_io_make_string`).

**Proof (mandatory, 2 suites)**: `UiE2ETest` (`both()` = JVM+Native) +
`KofJsBrowserE2ETest` (headless Chrome, real DOM). Testing JS only = leaving the
JVM broken (rule that failed 07/09).

## 6. Next steps (state 07/09, after forms + UI001-Native)

**DONE (07/09):** UI001-Native (21 stubs — `Image/Link/Icon/Font` linked
again); UI004/5 `Input.setPlaceholder`/`setType`/`setChecked`/`checked`;
UI003/5 `Image.setAlt`/`setWidth`/`setHeight`; UI004 `Form(children)` +
`onSubmit`/`submit()` (handler runs in the browser — proof by DOM mutation);
UI005 `setId`/`setClass`/`setDisabled` (+ dead code fix `acceptsFont`).

**Next (my lane, Phase 4):**
1. `<form>`/`onSubmit` (UI004 headline) — new type + ctor with lambda (pattern
   `Button(text, action)` in `ExpressionUiStaticLowerer`); browser test.
2. `id`/`class`/`disabled` attributes (UI005) + `textarea`/`select`
   elements (UI003) — same 6-point pattern.
3. ~~UI007 declarative `style` (idiomatic CSS, own parser)~~ **DONE 17/09**
   (`f7a5ad89`): decision `D-UI-STYLE` + slices A/B, proof `UiStyleCssE2ETest`
   10/10 + real-Chrome tests (see §UI007 below).
4. ~~UI002 (Script silent no-op)~~ **DONE 08/09** (`7081551`): decision
   taken as additive without breaking backward compat — single **warning** on
   stderr (never error; rule 6 + freezing); test `KofScriptTest.ui002WarnsOnceOnUiCalls`.

### UI007 — design decision + implementation (DECIDED and DONE 17/09; rule 6 closed)

The plan asks for "declarative `style` (idiomatic CSS), own parser". The
surface was frozen by the maintainer and recorded as **`D-UI-STYLE`** in
`docs/development/DECISIONS.md` (EN+PT). Minimal additive form (does not
touch the existing `Style(4 Ints)` — backward compat):

```kof
// new form: idiomatic CSS as a string, parsed in the compiler
var s = Style("background: #ff0000; padding: 8; border-radius: 4")
var v = View(s)
```

Closed questions:
- Q1: colors — **hex CSS (`#rgb`/`#rrggbb`/`#rrggbbaa`) + the CSS color
  names + the `Palette` names** (the same table `Palette` uses).
- Q2: units — **a bare integer means `px`; `px`/`%`/`em`/`rem` accepted**.
- Q3: properties — **typed whitelist**; an unknown property is a
  compile-time diagnostic (`SEM076`), malformed declaration `SEM077`,
  invalid value `SEM078` — never silently forwarded to `node.style` (R6).
- Q4: **parse in the compiler** (style IR; normalized text in the lowering).
- Q5: **every DOM widget accepts it** (`setStyle(style)` with the `Style`
  value, via the shared `kof_ui_widget_set_style` family — the UI005
  pattern), not only `View`.

Implementation: **DONE 17/09** (commit `f7a5ad89`) — slice A = parser +
`Style(String)` + lowering + JS runtime + proof; slice B = `setStyle(style)`
on every DOM widget. Proof: `UiStyleCssE2ETest` 10/10 (JVM/Native/Script/JS
+ `SEM076`/`SEM077`/`SEM078`) and `KofJsBrowserE2ETest` +2 on real Chrome DOM.

**Phase 5 boundary (KofJS Web APIs — not kof.ui):** fetch/WS/storage.

## 7. Fidelity notes

- ~~`docs/development/README.md:30` says "CANVAS001 JS pending (attach to
  kof-root)"~~ — **RESOLVED (13/09):** the index was rewritten and no longer
  contains the line; `JsRuntimeUiWidgets.java` attaches to `#kof-root` and
  CANVAS001 is closed `5a9cac46` (3 targets, re-proven green 12/09 — `UiE2ETest`
  29/29 with no exclusions). Nothing to fix.
- JVM no-op (UI008) is a documented design decision (backend-parity), not a bug
  — but R6 suggests a diagnostic in the log (low prio).
- Native/Script silent no-op is **not** a documented decision — it is an
  omission (R6 requires a diagnostic): UI001/UI002.

### Phase 9 (Rendering) — re-render prune (18/09)

The survey for the Component Core phases 8–11 found that
`kofUiRender` (`JsRuntimeUiComponents.java`) rebuilt the view on every
state change but pruned only the previous **root** element from the DOM,
leaving the whole discarded subtree in `window.__kofNodes` (and its Button
actions in `window.__kofActions`) — unbounded silent growth. Fixed by
calling the existing `kofUiRemoveSubtree` (DOM + registry) on root change
plus the `__kofActions` cleanup; see `known-bugs.md` **§300**. Proof:
`ComponentCoreE2ETest.rerenderPrunesPreviousSubtreeFromRegistry` +
`rerenderReleasesDiscardedButtonActions` (both RED pre-fix). Phase 9 still
lacks node reuse/diffing (the "partial update" half) — this unit closes
the leak only. The reuse half is a rule-6 identity contract question —
planned with options + measured evidence in `DECISIONS.md` **D-UI-DIFF**
(`BLOCKED` on the maintainer); no agent edit without it.

### Phase 10 (Design system tokens) — DONE (18/09)

`D-UI-TOKENS` delivered pillar 9 of `architecture.md` §2.1: the
`Spacing`/`Radius`/`Border`/`Elevation`/`Typography` namespaces are
compile-time `Int` (px) constants, folded by the same idiom as `Palette`
(shared frontend → cross-target parity by construction). Unknown members and
method calls on a namespace are `SEM079` (R6 — never a silent 0). Proof:
`UiTokensE2ETest` 7/7 (golden table on JVM/Native/Script + JS DOM + both
`SEM079` edges + composition with `Style`/widget). The audit matrix
"Design system" row now reads: Theme + `Color`/`Palette` + the five token
namespaces (the semantic theme-to-widget application remains manual).

### Phase 8 (Application state) — DONE (18/09)

`architecture.md` §2.6 closed by two units. §301 (bug): KofJS
`Store.unsubscribe` was a silent no-op — subscribe stored the
`fn.invoke.bind(fn)` wrapper, unsubscribe searched the raw handle, so
unsubscribed callbacks kept receiving every `set()` forever; subs are now
`{raw,f}` pairs removed by raw identity (fail-first:
`storeUnsubscribeStopsDelivery`, RED `n=1,n=2,n=3,` pre-fix). Feature:
`AppState(initial)` — the application-scoped root store, a create-or-get
singleton over the Store machinery reachable from anywhere without
prop-drilling (`D-UI-APPSTATE`). Proof: `appStateIsCreateOrGetSingleton` +
`appStateDrivesComponentsWithoutPropDrilling` (golden measured per target;
JVM/Native keep the documented Store no-ops; JVM singleton still counts in
`storesLive()`), `ComponentCoreE2ETest` 24/24. UPDATE (26/09): the rule-6
question CLOSED as shipped — `D-UI-AUTOUNSUB` option (A) (18/09) binds
subscriptions made during a component's lifecycle and drops them at unmount;
`D-COMPLETE-FIRST` item 4 (26/09) completed the lifecycle with component-owned
stores (released at unmount; `AppState` exempt — app by definition) and the
leak-lock probes `uiNodesLive()/storesLive()/subscriptionsLive()`
(`UiLeakLockE2ETest`, 10k cycles). Manual `unsubscribe` remains the app-scope
primitive.

### Phase 11 (module structure) — AUDITED DONE (18/09, no code)

The §2.10 module map declares itself **conceptual** ("today it lives in the
compiler; the engine is the JS CORE_RUNTIME"). Measured against the tree:
the physical files already mirror every conceptual module by responsibility
(`Components`/`Events`/`Forms`/`Validation` = core; `Layout` = layout; the
Router block of `Events` = navigation; `KofStyleParser`+`KofUiTokens`+
`Palette` = theme; `Widgets` = widgets; `JvmRuntimeUi`/native `RuntimeUi` =
the honest no-ops). A physical `kof-ui/*` packaging would move files without
changing behavior or the `CORE_RUNTIME` exports — rejected by "small and
stable core" (no gain, all risk); §2.10 now carries the mapping table (EN+PT)
as the module boundary. Three-states rule: this is an audit closing a
documented state, not invented work.
