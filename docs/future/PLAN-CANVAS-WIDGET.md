# PLAN-CANVAS-WIDGET — Canvas 2D para kof.ui

> **Versão:** 0.1.0 · **Data:** 06/09/2026 · **Status:** Implementado
> **Branch:** beta-0.3.0

---

## 1. Problema

`kof.ui` não tem primitivas de desenho. Gráficos (pizza, barras, linhas),
animações e visualizações customizadas são impossíveis — o máximo que se faz
são retangulos coloridos (`View`+`Style`).

O futuro (`PLAN-UNIVERSAL-PLATFORM.md:422`) prevê "plot básico via kof.ui"
como visualização leve (SVG/`kof.ui` + FFI). O widget `Canvas` é a
implementação concreta dessa necessidade.

## 2. Solução

Widget `Canvas` que mapeia para `<canvas>` + `getContext("2d")` no DOM
(KofJS). API inspirada no HTML5 Canvas 2D — familiar para qualquer
desenvolvedor web.

**Alvos:**
- **JS (KofJS):** renderização real via Canvas API do browser/webview
- **JVM/Native:** handles no-ops (como todo `kof.ui`)

## 3. API Kof

### Construtor

```kof
var c = Canvas(400, 300)     // cria canvas com largura × altura
```

### Métodos de caminho

| Método | Assinatura | Descrição |
|--------|-----------|-----------|
| `beginPath()` | `→ void` | Inicia novo caminho |
| `closePath()` | `→ void` | Fecha o caminho atual |
| `moveTo(x, y)` | `(Int, Int) → void` | Move a caneta para (x, y) |
| `lineTo(x, y)` | `(Int, Int) → void` | Desenha linha até (x, y) |
| `arc(x, y, r, start, end)` | `(Int, Int, Int, Double, Double) → void` | Arco em radianos |

### Métodos de estilo

| Método | Assinatura | Descrição |
|--------|-----------|-----------|
| `setFill(color)` | `(Color) → void` | Cor de preenchimento |
| `setStroke(color)` | `(Color) → void` | Cor do traço |
| `setLineWidth(w)` | `(Int) → void` | Espessura do traço |

### Métodos de renderização

| Método | Assinatura | Descrição |
|--------|-----------|-----------|
| `fill()` | `→ void` | Preenche o caminho atual |
| `stroke()` | `→ void` | Contorna o caminho atual |
| `clearRect(x, y, w, h)` | `(Int, Int, Int, Int) → void` | Limpa retângulo |

### Lifecycle

| Método | Assinatura | Descrição |
|--------|-----------|-----------|
| `remove()` | `→ void` | Remove o canvas do DOM |

## 4. Exemplo: Gráfico de Pizza

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

## 5. Arquivos modificados

| Arquivo | Mudada |
|---------|--------|
| `KofUi.java` | tipo CANVAS + instanceMethod |
| `ExpressionStaticCallLowerer.java` | constructor lowering |
| `CompilerDriver.java` | emitUiInstance Canvas condition |
| `JsRuntimeOps.java` | registration kof_ui_canvas_* |
| `JsRuntimeUiWidgets.java` | JS DOM (13 funções) |
| `JvmRuntimeUi.java` | JVM no-ops |
| `JvmRuntimeCallDescriptors.java` | descriptors |
| `RuntimeUi.java` | native x86_64 no-ops |
| `UiE2ETest.java` | testes |

## 6. Decisões de design

1. **API idêntica ao HTML5 Canvas 2D** — familiar, padrão da indústria,
   não reinventar a roda.
2. **arc() em radianos (Double)** — padrão Canvas. Kof suporta Double como
   parâmetro de funções UI via `Type.PrimitiveType.DOUBLE`.
3. **Sem counterclockwise** — `arc(x, y, r, start, end)` sem o 6º argumento.
   Pode ser adicionado depois se necessário.
4. **Context armazenado no JS** — o canvas element e o 2D context são
   armazenados juntos em `window.__kofNodes` (id → {canvas, ctx}).
5. **Cor via Color packado** — mesmo sistema de `Label.setColor()`, com
   `kofUiColorToCss()` para conversão.

## 7. Status

- [x] Implementado (06/09/2026)
- [x] Testes UiE2ETest (JVM + Native + JS)
- [x] Doc atualizado (architecture.md, learn/35-kof-ui.md)
