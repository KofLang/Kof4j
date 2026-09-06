# kof.ui — Arquitetura

> **Status:** Fase 1 (inspeção) concluída; Fases 2-7 implementadas (Component
> Core + Navegação/Router — `go/replace/back/forward/param/current/depth`,
> real no JS, no-op no JVM — 30-31/08); Fases 8-11 em progresso.
> **Última atualização:** 2 de setembro de 2026
> **Versão:** 0.2.6-beta

Este documento é o mapa da arquitetura do `kof.ui`: o estado real encontrado na
inspeção, os problemas, e a fundação que a UI precisa antes de qualquer widget
novo. A regra que governa tudo: **kof.ui é uma plataforma de interface, não uma
coleção de widgets.**

---

## 1. O que é kof.ui hoje

`kof.ui` é uma **stdlib intrinsic** do compilador: os tipos de UI não existem
como classes Kof no source — são **descritos no compilador** e baixados para
funções de runtime `kof_ui_*`. A renderização é **KofJS only**: widgets viram
DOM, desenhados no webview nativo (WebKitGTK) ou no browser. Nos alvos JVM e
Native os handles são **no-ops** (documentado; a intenção compila em todos, a
realização é JS).

### Pipeline do kof.ui

```text
Kof source
  │  Window("título"), Button("+1", () -> ...), Column(listOf(...))
  ▼
SemanticAnalyzer        → reconhece os tipos kof.ui.* (KofUi.isUiType)
  ▼
CompilerDriver
  ├── construtores: match por NOME (mc.methodName == "Window" | "Label" | ...)
  │     → KofCall(kof_ui_*_new, ...)          [emitExpression, receiver==null]
  └── métodos:      match por (tipo, nome, aridade)
        → KofUi.instanceMethod → KofCall(kof_ui_*, ...)   [emitUiInstance]
  ▼
Backend
  ├── JvmBackend    → KofRuntime.java gerado, kof_ui_* no-ops (JVM/Native)
  ├── NativeBackend → assembly kof_ui_* no-ops
  └── JsBackend     → kof-runtime.mjs (CORE_RUNTIME): a implementação DOM real
```

### Os 4 pontos de implementação (arquivos reais)

| Papel | Arquivo | Responsabilidade |
|-------|---------|------------------|
| Registro de tipos/métodos | `kof-compiler/src/main/java/dev/kof/compiler/KofUi.java` | tipos `kof.ui.*`, construtores, `staticMethod`, `instanceMethod`, `paletteColor`, `themeColor` |
| Lowering | `kof-compiler/src/main/java/dev/kof/compiler/CompilerDriver.java` | construtores por nome (~2405-2519), `emitUiInstance` (~5891), `inferExprType` (~4998) |
| Runtime JVM/Native | `JvmRuntime.java` / `NativeRuntime.java` | no-ops `kof_ui_*` |
| Runtime JS (o real) | `JsBackend.java` → `CORE_RUNTIME` | DOM: `kof_ui_*New/Bind/Show/...`, tema, ícones, font |

### Widgets existentes (inventário)

| Categoria | Tipos | Notas |
|-----------|-------|-------|
| Cor/tema | `Color`, `Palette`, `Theme` | Color = Int 32-bit `(r<<24|g<<16|b<<8|a)`; Theme light/dark com cores semânticas |
| Janela | `Window` | título, bind, show/close, size, theme |
| Folha | `Label`, `Button`, `Input`, `Link`, `Image`, `Icon` | text/fontSize/bold/color; Button tem ação (lambda c/ capturas); Icon = SVG embutidos |
| Layout | `Column`, `Row`, `View`+`Style` | CSS flexbox; gap **fixo 8px**; Style(bg, fg, padding, radius) |
| Desenho | `Canvas` | 2D context: beginPath/closePath/moveTo/lineTo/arc/fill/stroke/setFill/setStroke/setLineWidth/clearRect; renderiza em `<canvas>` no KofJS |
| Fonte | `Font` | family, size, bold |

### Abstrações fundamentais que JÁ existem

- **Cor empacotada** (Int) com paleta nomeada e tema semântico.
- **Árvore de DOM** implícita via `bind` (janela/contêiner → filhos).
- **Eventos de clique** em `Button` via lambda com capturas.
- **Tema** light/dark aplicado na janela.
- **Múltiplos alvos** com gap diagnosticado (JVM/Native = no-op).

### Abstrações fundamentais que NÃO existem (o gap real)

| Pilar | Estado | Consequência |
|-------|--------|--------------|
| **Componente** | não existe | cada widget é um handle solto; não há nó de UI com filhos/estado/identidade |
| **Ciclo de vida** | não existe | só `bind`/`show`/`remove`; não há mount/unmount/dispose, nem cleanup |
| **Layout** | parcial | só `Column`/`Row` (flexbox, gap fixo); não há Stack/Box/Spacer/Scroll/Grid/Wrap/Center/Align, nem gap/padding/flex por widget |
| **Eventos** | parcial | só clique no Button, cada um a sua maneira (DOM `addEventListener` espalhado); não há target/propagação/stopPropagation/foco/teclado |
| **Foco** | não existe | nenhum gerenciamento global; Tab/Shift+Tab, traversal, restoration ausentes |
| **Navegação** | não existe | sem Route/Router; uma janela só |
| **Estado** | ad-hoc | estado em **campos estáticos de classes** + lambda que atualiza label na mão; não há estado de componente nem invalidação |
| **Renderização/invalidação** | imperativo | cada clique faz `label.text = ...` à mão; não há re-render, diffing, scheduling |
| **Design system** | parcial | só Theme light/dark; não há tokens de Typography/Spacing/Border/Radius/Elevation |

### Problemas encontrados (diagnóstico)

1. **Árvore não é rastreada pelo framework.** O DOM é a estrutura, mas o
   runtime não conhece parent/child (só `window.__kofNodes[id]` → el). Sem
   árvore não há lifecycle, foco, propagação de eventos ou navegação.
2. **Cada widget implementa infraestrutura própria.** `kofUiSetAction` faz o
   próprio `addEventListener`; `View`/`Column`/`Row` fazem seu próprio
   `appendChild`; cor é re-convertida a CSS em vários pontos.
3. **Estado em estáticos é anti-idiomático e não escala.** Capturas são fotos;
   o contador do exemplo usa `App.count` estático + `label.text = ...` manual.
4. **Layout é CSS fixo.** `gap: 8px` hardcoded; sem margin/fill/grow/shrink,
   sem Stack/Scroll/Grid.
5. **Sem limpeza.** `remove()` só apaga o el; não há desmontagem de árvore,
   nem liberação de listeners/timers → risco de leak.

---

## 2. Arquitetura proposta (a fundação)

> Widgets são a camada visível construída em cima de **nove pilares**.
> Este documento define os pilares; cada um vira uma fase com implementação,
> testes e docs próprios. **A Fase 2 (Component Core) entrega o pilar 1 e a
> espinha dos pilares 3/4/5/8/9** (árvore + estado + invalidação + lifecycle),
> que é a base sobre a qual os demais se assentam.

### 2.1 Os nove pilares

```text
   ┌───────────────────────────────────────────────────────────┐
   │                    kof.ui — plataforma                     │
   └───────────────────────────────────────────────────────────┘
        Widgets (camada visível, construídos DEPOIS)
   ┌───────────────────────────────────────────────────────────┐
   │ 1 Component   2 Lifecycle   3 Layout    4 Eventos         │
   │ 5 Foco        6 Navegação   7 Estado    8 Renderização    │
   │ 9 Design system (Theme/Token)                             │
   └───────────────────────────────────────────────────────────┘
```

| # | Pilar | Entregável | Fase |
|---|-------|-----------|------|
| 1 | Component model | nó de UI com identidade, filhos, estado, composição, render | 2 |
| 2 | Lifecycle | mount/update/unmount/dispose + cleanup automático | 3 |
| 3 | Layout | Row/Column/Stack/Box/Spacer/Scroll/Grid/Wrap/Center/Align; gap/padding/flex | 4 |
| 4 | Eventos | Event/InputEvent/KeyEvent/MouseEvent; target/propagação/stop | 5 |
| 5 | Foco | foco global, traversal Tab/Shift+Tab, restoration | 6 |
| 6 | Navegação | Route/Router; go/back/forward/replace; params | 7 |
| 7 | Estado | local/compartilhado/app; invalidação mínima | 8 |
| 8 | Renderização | construção, scheduling, invalidation, partial update | 9 |
| 9 | Design system | Theme + tokens (Color/Type/Spacing/Border/Radius/Elevation) | 10 |

### 2.2 O Component Core (Fase 2) — o que será implementado

**Um `Component` é um nó na árvore de UI.** Todo widget é um componente. O
core entrega a espinha: árvore + estado reativo + invalidação + renderização
+ lifecycle + events + efeitos com cleanup automático.

Modelo (idiomático Kof, API pequena, sem boilerplate):

```kof
// estado reativo de componente + view builder + lifecycle + effects
var app = Component("App")
app.state(0)                                  // estado inicial (Int)
app.view { s ->                               // view: re-executado a cada mudança de estado
    Column([
        Label("count: " + s),
        Button("+1", () -> { app.state(s + 1) })   // set estado => invalida => re-render
    ])
}
app.onMount { /* roda 1x ao montar */ }
app.onDispose { /* roda 1x ao desmontar */ }
app.effect { /* registro de listener/timer/subscription; cleanup automático no dispose */ }
window.bind(app)                              // monta
```

Regras que o core garante:

- **Árvore rastreada.** O framework conhece parent/child de cada nó (fonte da
  verdade), não só o DOM.
- **Composição.** `bind`/filhos formam a árvore; um componente compõe outros.
- **Estado encapsulado.** estado vive no componente; `state(...)` é o único
  caminho de mutação (sem 5 formas de guardar estado).
- **Invalidação mínima.** `state(...)` marca **só o componente** como dirty e
  agenda re-render (scheduling), sem tocar a aplicação inteira.
- **Re-render por reconciliação.** o view builder re-rodou, mas os nós
  estáveis (mesma posição + kind) **reaproveitam o DOM existente** — só o que
  mudou é atualizado (texto, props, handlers). Arquitetura preparada para
  diffing completo (Fase 9), sem recriar a árvore.
- **Lifecycle determinístico.** mount (view + `onMount`), update (reconcile),
  unmount (`onDispose` + **efeitos em ordem reversa** + remoção do DOM).
- **Cleanup automático.** listener/timer/subscription registrados via `effect`
  são liberados no unmount — nenhum vazamento, sem o usuário lembrar.
- **Eventos com propagação.** `on(type, handler)` centralizado; base para
  bubbling/stopPropagation (Fase 5).

### 2.3 Relação entre componentes

```text
Window (raiz/host)
 └── Component "App"            (componente raiz do app)
      └── Column                (layout)
           ├── Label            (folha)
           └── Button           (folha + ação)
                └── (ação => state => re-render do App)
```

- **Container/Layout** (`Window`, `Column`, `Row`, `Box`, `Stack`...) têm
  filhos; **folha** (`Label`, `Button`, `Input`...) não.
- **Componente** (`Component`) é o nó que carrega **estado + view +
  lifecycle + effects**; é a unidade de re-render.
- O grafo é uma **árvore** (cada nó tem um único parent), raiz na janela.

### 2.4 Renderização

1. **Construção:** `view { s -> ... }` executa → devolve a raiz da view (um nó
   de layout/folha com a árvore de filhos).
2. **Quando renderiza:** na montagem (1x) e a cada `state(...)`/`text(...)`/
   `flag(...)` (invalidação agendada, em lote — *batching* via fila de dirty).
3. **Como as mudanças são detectadas:** a mutação de estado **é** a detecção —
   o próprio `state(...)` é o ponto de invalidação (sem polling, sem reflexão).
4. **Invalidation:** `state(...)` marca o componente dirty na fila; um flush
   (agendado, não síncrono) reconcilia só os componentes dirty.
5. **Updates aplicados:** reconciliação por **posição + kind**, reaproveitando
   el existente e atualizando só o diff (texto/props/handlers). Preparado para
   diffing por chave (Fase 9).

### 2.5 Eventos

Eventos vivem no nó e são centralizados no engine (não no DOM de cada widget).
`on(type, handler)` registra no nó; o engine liga ao DOM. Propagação (Fase 5):
target → bubbles para os pais, com `stopPropagation`/cancelamento. O core já
registra handlers por nó (base da propagação) e os **limpa no unmount**.

### 2.6 Estado

Três escopos (Fase 8 define o modelo oficial; o core entrega o **local**):

- **Local de componente:** `state`/`text`/`flag` no `Component` (entregue).
- **Compartilhado:** um `Store` observável entre componentes (Fase 8).
- **Aplicação:** raiz/`AppState` (Fase 8).

Mudança de estado invalida **apenas o componente dono** — não a aplicação.

### 2.7 Ciclo de vida (Fase 3 detalha; o core já implementa)

Ordem determinística:

```text
mount:   (monta a view) -> onMount()                      [top-down depois de montar]
update:  state mudou   -> re-render (reconcile)
unmount: onDispose() -> effects() em ordem REVERSA -> remove DOM
```

Efeitos (listener/timer/subscription/stream/task) registrados via `effect` são
**liberados automaticamente** no unmount. Sem vazamento, sem lembrete manual.

### 2.8 Layout (Fase 4 detalha; o core já traz primitivas)

O core adiciona as primitivas estruturais faltando, todas com
**gap/padding/alignment/flex** via CSS (sem o widget calcular posição):
`Box`, `Stack`, `Spacer`, `Wrap`, `Grid`, `Center`, `Align` (além do `Row`/
`Column`/`View` existentes). `Scroll` entra com a camada de layout.

### 2.9 Navegação (Fase 7) — implementada

`Router` namespace: `route(name, component)`, `go(name[, param])`,
`replace(name[, param])`, `back()`, `forward()`, `param()`, `current()`,
`depth()`. Navegar = **trocar o componente raiz**: o engine desmonta o antigo
(lifecycle correto + cleanup) e monta o novo. A espinha de componente do core é
o que torna isso possível (árvore + lifecycle + cleanup).

Detalhes de implementação (alvo JS):

- `kofUiRouterShow` desmonta **qualquer rota montada** que não seja o destino
  (cobre o bind inicial de `Window.bind`, que monta um componente raiz sem
  registrar `current`). Padrão suportado: configurar o component (`view`,
  `onMount`, ...) **antes** de `win.bind`/`Router.go`.
- `Router.go`/`replace` aceitam 1 ou 2 argumentos (com ou sem param).
- `back()`/`forward()` usam pilhas de histórico; `forwardStack` é limpo ao
  navegar para frente.
- Testes: `RouterE2ETest` (go com lifecycle, back/forward, rota unknown).

### 2.10 Estrutura de módulos (Fase 11)

```text
kof-ui/  (conceitual — hoje vive no compilador; o motor é o CORE_RUNTIME JS)
  core/       component · state · lifecycle · render · events · input · focus
  layout/     row · column · stack · box · scroll · grid · wrap · spacer
  navigation/ router · route · navigation
  theme/      theme · color · typography · spacing · border · radius · elevation
  widgets/    input · buttons · selection · feedback · navigation · data · overlays · advanced
```

---

## 3. Decisões e limites honestos

- **Renderização é KofJS.** JVM/Native continuam no-op para UI (gap
  documentado, como hoje). O core roda e é testado no alvo JS (GraalJS).
- **API pode evoluir.** o `view { ... }` + `state(...)` é a forma atual; o
  formato final do "componente declarativo" pode mudar, mas a **arquitetura**
  (árvore, composição, estado, invalidação, lifecycle) é estável.
- **Sem mágica de reatividade.** Kof não tem observer de propriedade; a
  detecção de mudança de estado **é** a chamada a `state(...)`. Isso é
  explícito mas mínimo (um método), encapsulado e automático (sem
  invalidate manual, sem cleanup manual).
- **Não reescrever o que funciona.** `Color`/`Palette`/`Theme`, os widgets
  existentes e seus testes são preservados; o core os **estende** (árvore,
  estado, lifecycle) sem quebrar o comportamento atual.

## 4. Planos de teste (Fase 2)

- **Componentes:** mount, update, unmount.
- **Lifecycle:** ordem correta (mount→update→unmount), cleanup, dispose.
- **Eventos:** propagação, cancelamento, foco (base).
- **Memória:** desmontar libera listeners; effects rodam 1x; **stress 10.000
  mount/unmount** + **10.000 event dispatches** sem leak.
