# 35 — kof.ui — Cores, Widgets e Janelas

`kof.ui` é a plataforma de UI do Kof. A renderização é **KofJS**: o mesmo
programa compila para JVM, Native e JS, mas somente o alvo JS desenha (via
webview nativo com WebKit embutido, ou no browser). Nos outros alvos os
handles são no-ops — o programa executa sem renderizar.

## Cores, Paletas e Temas

Cores são valores de 32 bits (`0xRRGGBBAA`) com canais 0-255:

```kof
var red = Color(255, 0, 0)          // r, g, b (alpha = 255)
var rgba = Color.rgba(10, 20, 30, 128)
var v = Color(0xFF0000FF)           // valor empacotado direto
red.red()        // 255
red.isOpaque()   // true
red.withAlpha(64).toCss()           // rgba(255, 0, 0, 64)
Palette.red.toCss()                 // rgb(255, 0, 0)
```

Cores nomeadas: `Palette.red/green/blue/yellow/cyan/magenta/black/white/
gray/orange/purple/pink/brown/transparent`.

Temas com cores semânticas:

```kof
var dark = Theme.dark()             // ou Theme.light()
dark.isDark()                       // true
dark.background().toCss()           // rgb(18, 18, 18)
dark.primary()                      // Color
```

## Janelas e Widgets

```kof
main() {
    var w = Window("Minha Janela")
    var label = Label("Olá, Kof!")

    w.title = "Kof App"             // bind do título
    w.bind(label)                   // monta o label na janela
    w.show()                        // serializa e exibe
}
```

| Operação | Descrição |
|----------|-----------|
| `Window("título")` | cria uma janela (uma por handle) |
| `w.title = v` / `w.title()` | bind do título |
| `w.bind(widget)` | monta um widget na janela |
| `w.show()` / `w.close()` | exibe/fecha a janela (a própria) |
| `w.size(largura, altura)` | dimensiona o conteúdo da janela |
| `w.theme = Theme.dark()` | aplica o tema (fundo/texto do conteúdo) |

### Label

```kof
var l = Label("texto")
l.text = "novo"                     // bind do texto
l.fontSize = 24                     // px
l.bold = true                       // negrito
l.color = Palette.red               // cor do texto
l.text()                            // lê o texto
l.remove()
```

### Button (com ação)

```kof
var b = Button("Salvar", () -> salvar())
b.text = "Salvando..."
```

O segundo argumento é uma **lambda**; ela pode **capturar** variáveis do
escopo externo (cópias somente-leitura):

```kof
class App {
    static Int count = 0
}

main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        App.count = App.count + 1
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

Estado mutável entre cliques vive em **campos estáticos de classes** (a
captura é uma foto do valor no momento da criação). Cada clique atualiza o
label — ao vivo, no webview.

### Input

```kof
var i = Input("digite aqui")        // campo de texto editável
i.text = "preenchido"               // bind do valor
i.text()                            // lê o valor atual
i.remove()
```

### Composição: Column, Row, View e Style

```kof
var col = Column(listOf(l1, l2))    // empilha verticalmente
var row = Row(listOf(l1, l2))       // alinha horizontalmente

var style = Style(Palette.black, Palette.white, 16, 8)
var view = View(style)              // caixa com fundo/padding/raio
view.bind(col)                      // compõe em árvore
w.bind(view)
```

`Style(background, foreground, padding, radius)` — cores via `Color`,
`padding`/`radius` em px.

## Canvas 2D

Canvas permite desenho 2D livre — gráficos, visualizações, jogos.
Renderiza em `<canvas>` no DOM (KofJS). JVM/Native são no-ops.

```kof
var c = Canvas(400, 300)         // cria canvas

c.setFill(Palette.blue)         // cor de preenchimento
c.setStroke(Palette.black)      // cor do traço
c.setLineWidth(2)               // espessura do traço

c.beginPath()                    // início de caminho
c.moveTo(200, 150)              // move caneta
c.lineTo(300, 200)              // desenha linha
c.arc(200, 150, 100, 0.0, 3.14) // arco (radianos)
c.closePath()                    // fecha caminho
c.fill()                         // preenche
c.stroke()                       // contorna

c.clearRect(0, 0, 400, 300)    // limpa retângulo
c.remove()                       // remove do DOM
```

### Gráfico de pizza

```kof
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
```

| Operação | Descrição |
|----------|-----------|
| `Canvas(largura, altura)` | cria canvas 2D |
| `c.beginPath()` / `c.closePath()` | gerencia caminho |
| `c.moveTo(x, y)` / `c.lineTo(x, y)` | desenha com a caneta |
| `c.arc(x, y, r, inicio, fim)` | arco em radianos |
| `c.fill()` / `c.stroke()` | preenche/contorna caminho |
| `c.setFill(cor)` / `c.setStroke(cor)` | define cores |
| `c.setLineWidth(largura)` | espessura do traço |
| `c.clearRect(x, y, w, h)` | limpa retângulo |
| `c.remove()` | remove do DOM |

## Router (Fase 7, 31/08)

Navegação por troca de componente raiz (unmount do antigo + mount do novo):

```kf
var home = Component(0)
var detail = Component(0)
home.view((s: Int) -> { return Label("home") })
detail.view((s: Int) -> { return Label("detail:" + Router.param()) })

var w = Window("App")
w.bind(home)
Router.route("home", home)
Router.route("detail", detail)
Router.go("detail", "42")   // navega com parâmetro
```

| Operação | Descrição |
|----------|-----------|
| `Router.route("nome", component)` | registra a rota |
| `Router.go("nome")` / `Router.go("nome", "param")` | navega (`false` se a rota não existe) |
| `Router.replace("nome"[, "param"])` | navega sem empilhar no histórico |
| `Router.back()` / `Router.forward()` | histórico (stacks) |
| `Router.current()` | rota ativa |
| `Router.param()` | parâmetro da navegação atual |
| `Router.depth()` | profundidade do histórico |

`Component` (`.view`, `.onMount`, `.onDispose`) é a unidade montável —
o router desmonta o componente antigo e monta o novo. JS real; nos alvos
JVM/Native o router é no-op (como o resto do `kof.ui`).

## Execução

`kof run --target=js`:

1. compila o programa para `Default.mjs` + `kof-runtime.mjs`;
2. executa no runner embarcado (GraalJS) — validação e snapshot;
3. escreve o **app interativo** (`index.html` + módulos) e abre no webview
   nativo (`bin/kof-webview`, WebKitGTK embutido) — a página roda o programa
   de verdade: DOM real, eventos de clique, edição de input;
4. **fecha a janela = encerra o programa** (o runner aguarda o webview).

Sem o webview nativo, cai no browser do sistema (`xdg-open`/`open`/
`rundll32`). No JVM e Native os handles são no-ops (nada é renderizado).

## Representação

- `Color`, `Theme` e todos os handles de widget são `Int` — sem objetos.
- Canais de cor são manipulação de bits no compilador — zero custo.
- `toCss()` é o único ponto com runtime (idêntico nos três alvos).
- JVM: handles de kof.ui são empacotados/desempacotados em slots de objeto
  (ex.: `List<Label>`) via `Integer`.
- Lambdas com capturas: campos privados finais + construtor na classe
  sintética; `invoke()` copia os campos para locals (foto somente-leitura).

## Referências

- `kof-compiler/src/main/java/dev/kof/compiler/KofUi.java` (registry)
- `kof-compiler/src/main/java/dev/kof/compiler/JsBackend.java` (runtime JS)
- `native/webview/kof-webview.c` (shell WebKitGTK sem headers)
- Testes: `kof-compiler/src/test/java/dev/kof/compiler/UiE2ETest.java`,
  `WindowE2ETest.java`
