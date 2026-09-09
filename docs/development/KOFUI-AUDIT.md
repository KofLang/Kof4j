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
| **Native** | **no-op SILENCIOSO** | `RuntimeUi.java` emite stubs no-op em asm (113); **07/09: 21 stubs ausentes (Image/Link/Icon/Font) causavam link-error `undefined reference [COMP001]` — CORRIGIDO** (paridade com JVM). Sem diagnóstico p/ o no-op (R6 residual) | E2E manual 07/09 + `UiE2ETest.mediaWidgetsLinkOnAllTargets` (JVM+Native) |
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
| **UI001** | `kof.ui` no Native = no-op silencioso (binário roda sem diagnóstico). **PARCIALMENTE CORRIGIDO 07/09**: `Image/Link/Icon/Font` **não linkavam** (`undefined reference [COMP001]` — 21 stubs ausentes em `RuntimeUi`); adicionados (paridade no-op com JVM). Resta: diagnóstico p/ o no-op silencioso dos demais = decisão de design (regra 6) | Native | **P0 (R6)** → P2 (residual) |
| **UI002** | `kof.ui` no Script = no-op silencioso (interprete executa sem efeito). **FEITO 08/09** (`7081551`): warning `UI002` **uma única vez** no stderr quando `KofInterpreter` resolve função `kof_ui_*` (mensagem aponta `--target=js`); aditivo — no-op preservado (retrocompat), sem erro (regra 6); teste `KofScriptTest.ui002WarnsOnceOnUiCalls` (verifica presença + contagem == 1) | Script | **P0 (R6)** → **FEITO** |
| **UI003** | Elementos: textarea ✅ FEITO 07/09 (`Textarea`); table/tr/td ✅ FEITO 07/09 (`Table(header, rows)` data-driven); select/option ✅ (`Select`); ul/ol/li ✅ (`Ul`/`Ol` data-driven); fieldset/legend ✅, iframe ✅, video/audio ✅, hr ✅ (08/09, `358ec80` — `Fieldset(children[, legend])`/`Iframe(url)`/`Video(url)`/`Audio(url)`/`Hr()` + remove; DOM real provado no Chrome headless; `kofSerialize` ganhou `src` + void-tags) | KofJS | P1 **FEITO** |
| **UI004** | Forms: `<form>` ✅ + submit handler ✅ FEITO 07/09 (`Form(children)`, `onSubmit`, `submit()` — handler roda no browser, prova por mutação de DOM); fieldset ✅ FEITO 08/09 (`Fieldset(children[, legend])`, `358ec80`). `Input` tipos ✅ (`setType`); checkbox/radio estado ✅ (`setChecked`/`checked`); select ✅ (`Select`/`setOptions`/`selected`/`setSelected`) | KofJS | P1 **FEITO** |
| **UI005** | Atributos: id ✅ class ✅ disabled ✅ (FEITO 07/09 — `setId`/`setClass`/`setDisabled` em widgets DOM, família `kof_ui_widget_*`); placeholder ✅ (`Input.setPlaceholder`); checked ✅; alt/width/height ✅ (`Image.*`); readonly/name ✅ FEITO 07/09 (`Input`/`Textarea`.setReadonly(bool)/setName(String) — 6/6 pontos completos, prova browser: atributos `name=`/`readonly` no outerHTML) | KofJS | P1 **FEITO** |
| **UI006** | Eventos: `Event.type()`/`stopPropagation()` ✅; `key()`/`value()`/`x()`/`y()` ✅ FEITO 08/09 (`f0907c2` — DOM event real: `key` do KeyboardEvent, `value` do input alvo, `clientX/Y`; `widget.on(type, handler)` exposto p/ widgets fora da árvore de Component; `kofUiWidgetOn` agora despacha o kofEv, antes chamava `fn()` sem evento); `target()`/`relatedTarget()` ✅ FEITO 08/09 (`3c241ae`+ — id do nó origem/relacionado com fallback tagName; prova browser: `t=campo-main` no DOM final) | KofJS | P2 **FEITO** |
| **UI007** | `style` declarativo (CSS idiomático) — novo, com parse próprio (item do plano Fase 4) | KofJS | P1 |
| **UI008** | Window: size/position só no-op JVM; KofJS só title (browser não controla window — ok por plataforma) | JVM/KofJS | P3 |
| **UI009** | Canvas: fillText ✅ measureText ✅ save ✅ restore ✅ transform ✅ setGlobalAlpha ✅ (FEITO 07/09 — `UiE2ETest.canvasUi009LinksOnAllTargets` + `KofJsBrowserE2ETest.canvasUi009RunsInRealBrowser`); drawImage ✅ (07/09 — Image→canvas via elemento DOM) | KofJS | P2 **FEITO** |

**Fronteira Fase 5 (KofJS Web APIs — não é kof.ui):** fetch/`WebSocket`/
`EventSource`(SSE)/`localStorage`/`sessionStorage`/`navigator`/`location`/
`history` no browser = matriz DOM/Fetch/Storage com supportedOn+gapCode do
plano Fase 5 (hoje ausentes no runtime browser; o "web" JS atual é server
GraalJS HttpServer — WEB001 residual ws/sse).

## 5. Receita: método novo em kof.ui = **6 pontos** (aprendida na prática 07/09)

Cada método de instância novo exige os 6 pontos abaixo — **faltar um quebra
um target**. (Foi o 6º ponto — `JvmRuntimeCallDescriptors` — que faltou em
3 commits: `setPlaceholder`/`setType`/`setChecked`/`checked` compilavam no
JVM mas davam `NoSuchMethodError` em runtime; só o teste KofJS passava.)

1. **Registry**: `KofUi.instanceMethod()` (case no switch do tipo).
2. **Whitelist JS**: `JsRuntimeOps.java` (lista `name.equals("kof_ui_…")` —
   exceto famílias já cobertas por prefixo: `link_`/`image_`/`icon_`/
   `canvas_`/`widget_`/`font_`).
3. **Impl JS**: `JsRuntimeUi*.java` (função exportada; nome via
   `JsTypeMapper.capitalizeUiFn`).
4. **Stub JVM (source)**: `jvm/JvmRuntimeUi.java` (no-op; Bool=int 0/1;
   String getter → `return ""`).
5. **Descriptor JVM**: `jvm/JvmRuntimeCallDescriptors.java`
   (`callDescriptor`) — **sem isso o bytecode chama assinatura errada
   (default = `(String)Object`) → `NoSuchMethodError`**.
6. **Stub Native (asm)**: `runtime/RuntimeUi.java` (void: `ret`;
   int: `xorl/movl`+`ret`; String: `leaq .Lui_empty` + `jmp
   kof_io_make_string`).

**Prova (obrigatória, 2 suítes)**: `UiE2ETest` (`both()` = JVM+Native) +
`KofJsBrowserE2ETest` (Chrome headless, DOM real). Só testar JS = deixar o
JVM quebrado (regra que falhou 07/09).

## 6. Próximos passos (estado 07/09, após forms + UI001-Native)

**FEITOS (07/09):** UI001-Native (21 stubs — `Image/Link/Icon/Font` linkavam
de novo); UI004/5 `Input.setPlaceholder`/`setType`/`setChecked`/`checked`;
UI003/5 `Image.setAlt`/`setWidth`/`setHeight`; UI004 `Form(children)` +
`onSubmit`/`submit()` (handler roda no browser — prova por mutação de DOM);
UI005 `setId`/`setClass`/`setDisabled` (+ fix do código morto `acceptsFont`).

**Próximos (minha lane, Fase 4):**
1. `<form>`/`onSubmit` (UI004 headline) — novo tipo + ctor c/ lambda (padrão
   `Button(text, action)` em `ExpressionUiStaticLowerer`); teste browser.
2. Atributos `id`/`class`/`disabled` (UI005) + elementos `textarea`/`select`
   (UI003) — mesmo padrão de 6 pontos.
3. UI007 `style` declarativo (CSS idiomático, parse próprio) — o item maior.
4. ~~UI002 (Script no-op silencioso)~~ **FEITO 08/09** (`7081551`): decisão
   tomada como aditivo sem quebrar retrocompat — **warning** único no stderr
   (nunca erro; regra 6 + congelamento); teste `KofScriptTest.ui002WarnsOnceOnUiCalls`.

### UI007 — proposta de design (aguarda maintainer; regra 6)

O plano pede "`style` declarativo (CSS idiomático), parse próprio". A
superfície exata é decisão de design (congelamento de API). Proposta
mínima aditiva (não toca `Style(4 Ints)` existente — retrocompat):

```kof
// forma nova: CSS idiomático como string, parse no compilador
var s = Style("background: #ff0000; padding: 8; border-radius: 4")
var v = View(s)
```

Open questions (não decidíveis sem maintainer):
- Q1: cores — aceitar `#rrggbb`/nomes CSS, ou só o `Color`/`Palette` da
  linguagem (conversão `toCss` já existe)?
- Q2: unidades — `8` = px? aceitar `em`/`%`/`rem`?
- Q3: propriedades — whitelist (background/padding/margin/radius/
  border/font) ou qualquer `prop: valor` passado ao `node.style`?
- Q4: parse no compilador (IR de estilo) ou no runtime (string → CSSStyle
  declaration)? "parse próprio" sugere compilador.
- Q5: `Style` é só para `View` ou todo widget DOM aceita (via
  `setStyle`)?

Implementação aguarda decisão; o parse em si (lexer de `prop: valor;`) é
mecânico quando a superfície fechar.

**Fronteira Fase 5 (KofJS Web APIs — não é kof.ui):** fetch/WS/storage.

## 7. Notas de fidelidade

- `docs/development/README.md:30` diz "CANVAS001 JS pendente (anexar ao
  kof-root)" — **desatualizado**: `JsRuntimeUiWidgets.java` já anexa ao
  `#kof-root`; CANVAS001 fechado `5a9cac4` (3 targets). Corrigir o índice.
- JVM no-op (UI008) é decisão de design documentada (backend-parity), não bug
  — mas R6 sugere diagnóstico em log (low prio).
- Native/Script no-op silencioso **não** é decisão documentada — é omissão
  (R6 exige diagnóstico): UI001/UI002.
