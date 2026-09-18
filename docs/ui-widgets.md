# kof.ui — RawView (low-level escape hatch)

`RawView` is a `kof.ui` widget for cases where the declarative widget set
(`Label`, `Button`, `Column`, `View`, ...) is not enough and the program needs
direct control over an HTML tag, its class, inline CSS and inner HTML.

```kof
var rv = RawView("section", "hero", "color: red;", "<b>oi</b>")
rv.setCss("color: blue;")
rv.setHtml("<i>tchau</i>")
```

## Constructor

```kof
RawView(String tag, String className, String cssText, String innerHtml)
```

| Argument | Meaning |
|---|---|
| `tag` | HTML tag name rendered on the JS target (e.g. `"div"`, `"section"`) |
| `className` | CSS class applied to the node |
| `cssText` | inline CSS (`style.cssText`) |
| `innerHtml` | initial `innerHTML` content |

## Methods

| Method | Effect |
|---|---|
| `bind(container)` | attaches the RawView to a container widget, same as `View.bind` |
| `setCss(String cssText)` | replaces the node's inline CSS |
| `setHtml(String innerHtml)` | replaces the node's `innerHTML` |

## Cross-target behavior

- **JS (KofJS):** renders a real DOM node with the given tag/class/css/html —
  see `rawViewRendersInRealBrowserDom` in `KofJsBrowserE2ETest`.
- **JVM/Native:** no-op (same parity pattern as `View`/`Fieldset`/`Iframe`) —
  see `rawViewLinksOnAllTargets` in `UiE2ETest`.

## Tests

- `tests/golden/raw_view/` — golden test (JVM + Native) via `tests/run-golden.sh raw_view`.
- `kof-compiler/src/test/java/dev/kof/compiler/UiE2ETest.java` — JVM/Native no-op parity.
- `kof-compiler/src/test/java/dev/kof/compiler/KofJsBrowserE2ETest.java` — real DOM assertion in a browser.
