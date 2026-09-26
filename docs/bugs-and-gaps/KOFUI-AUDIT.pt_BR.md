[English](KOFUI-AUDIT.md) | [Português](KOFUI-AUDIT.pt_BR.md)

# KofUI — Auditoria de cobertura (Fase 4 — ex-PLATFORM-PLAN, `docs/development/DECISIONS.md` §D-PLATFORM)

> **Status:** AUDITORIA (07/09) — matriz de gaps `UI00x`. Fonte: código
> (prova, não memória). Escopo: `kof.ui` (widgets/DOM). Web APIs de browser
> (fetch/WS/storage) são **Fase 5 (KofJS)** — marcadas aqui só como fronteira.
> Convenção (R6): todo gap tem código + diagnóstico; nunca no-op silencioso.

## 1. Registry do compilador (`KofUi.java`, 539 linhas — era 383 na auditoria de 07/09)

> **⚠️ Snapshot 07/09 — inventário abaixo é histórico.** Recontagem 17/09:
> `isUiType` cobre **36 tipos** (o texto lista 24) — Color, Theme, Label,
> Button, Input, Textarea, Select, Ul, Ol, Table, Column, Row, Form, View,
> Style, Window, Link, Image, Icon, Font, Component, Event, o conjunto de
> layout (Box, Stack, Spacer, Wrap, Grid, Center, Align) e Store, Canvas,
> Fieldset, Iframe, Video, Audio, Hr. A matriz `UI00x` e a convenção R6
> continuam válidas. **Recontagem `UI001/UI002` (17/09):** `UI002` ✅
> confirmado FEITO 08/09 (o interpretador imprime o warning uma vez via
> `ui002Warned`); `UI001` **segue aberto** — os erros de link de 07/09 estão
> corrigidos (`RuntimeUi` carrega os stubs no-op, `COMP001` sumiu) mas o Native
> continua um no-op **silencioso** (sem diagnóstico em compile-time), então
> UI001 permanece como a única face de no-op silencioso.

**Tipos (24 na varredura de 07/09):** Color, Theme, Label, Button, Input, Column, Row, View, Style,
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
| **UI007** | `style` declarativo (CSS idiomático) — novo, com parse próprio (item do plano Fase 4). **FEITO 17/09** (`D-UI-STYLE`, commit `f7a5ad89`): `Style("<declarações>")` com parse no compilador, whitelist tipada (`SEM076`/`SEM077`/`SEM078`), hex+nomes CSS+nomes de `Palette` verbatim, px/`%`/`em`/`rem`, `setStyle(style)` em todo widget DOM — prova: `UiStyleCssE2ETest` 10/10 + 2 testes no Chrome real | KofJS | P1 **FEITO** |
| **UI008** | Window: size/position só no-op JVM; KofJS só title (browser não controla window — ok por plataforma) | JVM/KofJS | P3 |
| **UI009** | Canvas: fillText ✅ measureText ✅ save ✅ restore ✅ transform ✅ setGlobalAlpha ✅ (FEITO 07/09 — `UiE2ETest.canvasUi009LinksOnAllTargets` + `KofJsBrowserE2ETest.canvasUi009RunsInRealBrowser`); drawImage ✅ (07/09 — Image→canvas via elemento DOM) | KofJS | P2 **FEITO** |

**Fronteira Fase 5 (KofJS Web APIs — não é kof.ui):** fetch/`WebSocket`/
`EventSource`(SSE)/`localStorage`/`sessionStorage`/`navigator`/`location`/
`history` no browser = matriz DOM/Fetch/Storage com supportedOn+gapCode do
plano Fase 5 (hoje ausentes no runtime browser; o "web" JS atual é server
GraalJS HttpServer — ws/sse residuais reportam `WEB004`/`WEB003` em
compile-time desde a fatia de honestidade 16/09).

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
3. ~~UI007 `style` declarativo (CSS idiomático, parse próprio)~~ **FEITO 17/09**
   (`f7a5ad89`): decisão `D-UI-STYLE` + fatias A/B, prova `UiStyleCssE2ETest`
   10/10 + testes no Chrome real (ver §UI007 abaixo).
4. ~~UI002 (Script no-op silencioso)~~ **FEITO 08/09** (`7081551`): decisão
   tomada como aditivo sem quebrar retrocompat — **warning** único no stderr
   (nunca erro; regra 6 + congelamento); teste `KofScriptTest.ui002WarnsOnceOnUiCalls`.

### UI007 — decisão de design + implementação (DECIDIDO e FEITO 17/09; regra 6 fechada)

O plano pede "`style` declarativo (CSS idiomático), parse próprio". A
superfície foi congelada pela mantenedora e registrada como **`D-UI-STYLE`**
em `docs/development/DECISIONS.md` (EN+PT). Forma mínima aditiva (não toca
`Style(4 Ints)` existente — retrocompat):

```kof
// forma nova: CSS idiomático como string, parse no compilador
var s = Style("background: #ff0000; padding: 8; border-radius: 4")
var v = View(s)
```

Perguntas fechadas:
- Q1: cores — **hex CSS (`#rgb`/`#rrggbb`/`#rrggbbaa`) + os nomes de cor CSS
  + os nomes de `Palette`** (a mesma tabela que o `Palette` usa).
- Q2: unidades — **inteiro nu significa `px`; `px`/`%`/`em`/`rem` aceitos**.
- Q3: propriedades — **whitelist tipada**; propriedade desconhecida é
  diagnóstico em compile-time (`SEM076`), declaração malformada `SEM077`,
  valor inválido `SEM078` — nunca repassado em silêncio ao `node.style` (R6).
- Q4: **parse no compilador** (IR de estilo; texto normalizado no lowering).
- Q5: **todo widget DOM aceita** (`setStyle(style)` com o valor `Style`, pela
  família compartilhada `kof_ui_widget_set_style` — padrão UI005), não só
  `View`.

Implementação: **FEITO 17/09** (commit `f7a5ad89`) — fatia A = parser +
`Style(String)` + lowering + runtime JS + prova; fatia B = `setStyle(style)`
em todo widget DOM. Prova: `UiStyleCssE2ETest` 10/10
(JVM/Native/Script/JS + `SEM076`/`SEM077`/`SEM078`) e `KofJsBrowserE2ETest`
+2 no DOM real do Chrome.

**Fronteira Fase 5 (KofJS Web APIs — não é kof.ui):** fetch/WS/storage.

## 7. Notas de fidelidade

- ~~`docs/development/README.md:30` diz "CANVAS001 JS pendente (anexar ao
  kof-root)"~~ — **RESOLVIDO (13/09):** o índice foi reescrito e não contém
  mais a linha; `JsRuntimeUiWidgets.java` anexa ao `#kof-root` e o CANVAS001
  está fechado `5a9cac46` (3 targets, reprovado verde 12/09 — `UiE2ETest`
  29/29 sem exclusões). Nada a corrigir.
- JVM no-op (UI008) é decisão de design documentada (backend-parity), não bug
  — mas R6 sugere diagnóstico em log (low prio).
- Native/Script no-op silencioso **não** é decisão documentada — é omissão
  (R6 exige diagnóstico): UI001/UI002.

### Fase 9 (Renderização) — poda no re-render (18/09)

A varredura das fases 8–11 do Component Core achou que o `kofUiRender`
(`JsRuntimeUiComponents.java`) reconstruía a view a cada mudança de estado
mas podava do DOM só o elemento **raiz** anterior, deixando a subárvore
descartada inteira em `window.__kofNodes` (e as ações de Button em
`window.__kofActions`) — crescimento silencioso e ilimitado. Corrigido
chamando o `kofUiRemoveSubtree` existente (DOM + registro) na troca de
raiz mais a limpeza de `__kofActions`; ver `known-bugs.md` **§300**. Prova:
`ComponentCoreE2ETest.rerenderPrunesPreviousSubtreeFromRegistry` +
`rerenderReleasesDiscardedButtonActions` (ambos VERMELHOS pré-fix). A Fase
9 continua sem reuso de nó/diffing (a metade "partial update") — esta
unidade fecha só o vazamento. A metade de reuso é questão de contrato de
identidade regra 6 — planejada com opções + evidência medida em
`DECISIONS.pt_BR.md` **D-UI-DIFF** (`BLOQUEADA` na mantenedora); sem edição
de agente sem ela.

### Fase 10 (tokens do design system) — FEITA (18/09)

A `D-UI-TOKENS` entregou o pilar 9 de `architecture.md` §2.1: os namespaces
`Spacing`/`Radius`/`Border`/`Elevation`/`Typography` são constantes `Int`
(px) em compile-time, folding pelo mesmo idiom que o `Palette` (frontend
compartilhado → paridade cross-target por construção). Membros inexistentes
e chamadas de método num namespace dão `SEM079` (R6 — nunca 0 silencioso).
Prova: `UiTokensE2ETest` 7/7 (tabela golden em JVM/Native/Script + DOM JS +
as duas arestas `SEM079` + composição com `Style`/widget). A linha
"Design system" da matriz agora lê: Theme + `Color`/`Palette` + os cinco
namespaces de token (a aplicação semântica theme→widget continua manual).

### Fase 8 (Estado da aplicação) — CONCLUÍDA (18/09)

A `architecture.md` §2.6 foi fechada por duas unidades. §301 (bug): o
`Store.unsubscribe` do KofJS era no-op silencioso — o subscribe guardava o
wrapper `fn.invoke.bind(fn)`, o unsubscribe buscava o handle raw, e callbacks
desinscritos seguiam recebendo todo `set()` para sempre; as inscrições agora
são pares `{raw,f}` removidos por identidade do raw (fail-first:
`storeUnsubscribeStopsDelivery`, VERMELHO `n=1,n=2,n=3,` pré-fix). Feature:
`AppState(initial)` — o store-raiz do escopo da aplicação, um singleton
create-or-get sobre a máquina do Store, alcançável de qualquer lugar sem
prop-drilling (`D-UI-APPSTATE`). Prova: `appStateIsCreateOrGetSingleton` +
`appStateDrivesComponentsWithoutPropDrilling` (golden medido por target;
JVM/Native mantêm os no-ops documentados do Store; o singleton JVM ainda conta
em `storesLive()`), `ComponentCoreE2ETest` 24/24. ATUALIZAÇÃO (26/09): a questão
regra-6 FECHADA como entregue — a opção (A) da `D-UI-AUTOUNSUB` (18/09) prende
as subscriptions feitas no ciclo de vida do componente e as solta no unmount;
o item 4 da `D-COMPLETE-FIRST` (26/09) completou o ciclo com os stores do
componente (liberados no unmount; `AppState` isento — app por definição) e as
sondas de trava de leak `uiNodesLive()/storesLive()/subscriptionsLive()`
(`UiLeakLockE2ETest`, 10k ciclos). O `unsubscribe` manual segue o primitivo de
escopo app.

### Fase 11 (estrutura de módulos) — AUDITADA/CONCLUÍDA (18/09, sem código)

O mapa de módulos do §2.10 se declara **conceitual** ("hoje vive no
compilador; o motor é o CORE_RUNTIME do JS"). Medido contra a árvore: os
arquivos físicos já espelham cada módulo conceitual por responsabilidade
(`Components`/`Events`/`Forms`/`Validation` = core; `Layout` = layout; o
bloco Router de `Events` = navigation; `KofStyleParser`+`KofUiTokens`+
`Palette` = theme; `Widgets` = widgets; `JvmRuntimeUi`/`RuntimeUi` nativo =
os no-ops honestos). Um empacotamento físico `kof-ui/*` moveria arquivos sem
mudar comportamento nem as exports do `CORE_RUNTIME` — rejeitado por "núcleo
pequeno e estável" (ganho zero, risco todo); o §2.10 agora carrega a tabela
de mapeamento (EN+PT) como fronteira de módulos. Regra dos três estados:
isto é auditoria fechando um estado documentado, não trabalho inventado.
