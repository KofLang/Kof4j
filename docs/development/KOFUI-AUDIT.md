# KofUI — Auditoria de cobertura (Fase 4, PLATFORM-PLAN)

> **Status:** AUDITORIA (07/09) — matriz de gaps `UI00x`. Fonte: código
> (prova, não memória). Escopo: `kof.ui` (widgets/DOM). Web APIs de browser
> (fetch/WS/storage) são **Fase 5 (KofJS)** — marcadas aqui só como fronteira.
> Convenção (R6): todo gap tem código + diagnóstico; nunca no-op silencioso.

## 1. Registry do compilador (`KofUi.java`, 383 linhas)

**Tipos (24):** Color, Theme, Label, Button, Input, Column, Row, View, Style,
Window, Link, Image, Icon, Font, Component, Event, Box, Stack, Spacer, Wrap,
Grid, Center, Align, Store, Canvas + namespace `Router`.

**Métodos por tipo (resumo):**
- `Color`: rgba, red/green/blue/alpha, toCss, withAlpha, isOpaque
- `Theme`: light/dark; background/surface/primary/secondary/text/error/isDark
- `Label`: text/setText, fontSize/setFontSize, bold/setBold, color/setColor, font, remove
- `Button`: text/setText, (action no ctor), remove
- `Input`: text/setText, remove
- `Window`: title, bind, show, close, size
- `Link`: text/setText, url/setUrl, remove · `Image`: src/setSrc, remove
- `Icon`: name/setName, size/setSize, remove
- `Component`: state/stateSet, view, onMount, onDispose, effect, on(type,handler), bind, remove
- `Event`: type, stopPropagation
- `Store`: get, set, subscribe, unsubscribe
- `Canvas`: beginPath, closePath, moveTo, lineTo, arc, fill, stroke, setFill, setStroke, setLineWidth, clearRect, remove
- `Router` (namespace): route, go, replace, back, forward, param, current, depth
- Layout: Box/Stack/Spacer/Wrap/Grid/Center/Align (ctores) · `Palette.<name>` (15 cores)

## 2. Implementação por target

| Target | kof.ui | Estado | Prova |
|---|---|---|---|
| **KofJS (browser)** | DOM real | `JsRuntimeUi*.java` + `JsRuntimeOps` — createElement + `window.__kofNodes`; router real (31/08) | `KofJsBrowserE2ETest` (Chrome headless; pula se ausente), `KofUi*Test` |
| **JVM** | no-op (por design) | `JvmRuntimeUi.java` — todos `kof_ui_*` vazios (compila, "roda", não renderiza) | `docs/backend-parity.md` ("JVM no-op"); `RouterE2ETest` |
| **Native** | **no-op SILENCIOSO** | nenhum `kof_ui_*` em `nat/`; binário compila+roda sem diagnóstico (R6 ❌) | E2E manual 07/09: `Window/Label/w.show()` → binário x86 roda "feito" rc=0, zero diagnóstico |
| **Script (interprete)** | **no-op SILENCIOSO** | `kof-script/` não conhece `kof.ui`; interpreta e executa sem efeito (R6 ❌) | E2E manual 07/09: `run --target script` → "feito" rc=0 |
| **Android** | via WebView (KofJS) | `AndroidProjectWriter.java` — sai KofJS p/ `assets/kof/`, renderiza em WebView | docs `backend-parity.md` Fase 7 |

## 3. KofJS — o que o DOM real cobre hoje

- **Elementos → tags:** Label→span, Button→button, Input→input[type=text],
  Column/Row/View→div, Link→a, Image→img, Icon→span, Style→style,
  Canvas→canvas. (Widgets: `JsRuntimeUiWidgets.java` 42/153/198/232/249/277.)
- **Eventos:** `Component.on(type, handler)` → `KOF_UI_EV` (15: click, dblclick,
  mousedown/up, mousemove/enter/leave, wheel, keydown/up, focus, blur, input,
  change) + fallback p/ tipo DOM arbitrário. `Button` action = click.
  `Event`: type + stopPropagation.
- **Atributos:** value, type (input), href (link), src (img).
- **Estilos inline:** color, fontSize, fontWeight, fontFamily, display,
  width/height (canvas).
- **Canvas 2D:** beginPath/closePath/moveTo/lineTo/arc/fill/stroke/setFill/
  setStroke/setLineWidth/clearRect — anexa ao `#kof-root` (CANVAS001 fechado
  3 targets `5a9cac4`).
- **Window:** `document.title` só (sem size/position).
- **Router:** completo (route/go/replace/back/forward/param/current/depth).
- **Store:** get/set/subscribe/unsubscribe (observable in-process).

## 4. Matriz de gaps (Fase 4)

| Gap | Descrição | Target | Prioridade |
|---|---|---|---|
| **UI001** | `kof.ui` no Native = no-op silencioso (binário roda sem diagnóstico) | Native | **P0 (R6)** |
| **UI002** | `kof.ui` no Script = no-op silencioso (interprete executa sem efeito) | Script | **P0 (R6)** |
| **UI003** | Elementos faltantes no KofJS: table/tr/td, textarea, checkbox, select/option, fieldset, iframe, video/audio, hr, ul/ol/li | KofJS | P1 |
| **UI004** | Forms: sem `<form>`/submit/fieldset; `Input` só text (sem number/checkbox/select) | KofJS | P1 |
| **UI005** | Atributos faltantes: id, class custom, placeholder, disabled, checked, alt, width/height (img), readonly, name | KofJS | P1 |
| **UI006** | Eventos: `Event` expõe só type/stopPropagation (sem target, x/y, key, value, relatedTarget) | KofJS | P2 |
| **UI007** | `style` declarativo (CSS idiomático) — novo, com parse próprio (item do plano Fase 4) | KofJS | P1 |
| **UI008** | Window: size/position só no-op JVM; KofJS só title (browser não controla window — ok por plataforma) | JVM/KofJS | P3 |
| **UI009** | Canvas: fillText/measureText/drawImage/save/restore/transform/setGlobalAlpha | KofJS | P2 |

**Fronteira Fase 5 (KofJS Web APIs — não é kof.ui):** fetch/`WebSocket`/
`EventSource`(SSE)/`localStorage`/`sessionStorage`/`navigator`/`location`/
`history` no browser = matriz DOM/Fetch/Storage com supportedOn+gapCode do
plano Fase 5 (hoje ausentes no runtime browser; o "web" JS atual é server
GraalJS HttpServer — WEB001 residual ws/sse).

## 5. Próximos passos (escopos realizáveis)

1. **UI001+UI002 (P0, R6):** diagnóstico claro quando `kof.ui` é usado em
   Native/Script (gate no backend: "kof.ui não renderiza em <target>; use
   kofjs (browser) ou android [UI00x]"). Teste por target.
2. **UI003/4/5 (P1):** estender registry `KofUi` + `JsRuntimeUi*`
   (arquivo:elemento, aditivo — retrocompatível).
3. **UI007 (P1):** `style` declarativo com parse próprio.
4. Fronteira Fase 5: matriz fetch/WS/storage (outro escopo).

## 6. Notas de fidelidade

- `docs/development/README.md:30` diz "CANVAS001 JS pendente (anexar ao
  kof-root)" — **desatualizado**: `JsRuntimeUiWidgets.java` já anexa ao
  `#kof-root`; CANVAS001 fechado `5a9cac4` (3 targets). Corrigir o índice.
- JVM no-op (UI008) é decisão de design documentada (backend-parity), não bug
  — mas R6 sugere diagnóstico em log (low prio).
- Native/Script no-op silencioso **não** é decisão documentada — é omissão
  (R6 exige diagnóstico): UI001/UI002.
