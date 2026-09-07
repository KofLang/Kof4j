# Idioms — kof.ui (Canvas 2D)

## Canvas: gráfico de pizza

**BAD — construir SVG manualmente com strings:**
```kof
// ❌ NÃO — manipulação manual de string SVG
var svg = "<svg viewBox='0 0 36 36'><circle cx='18' cy='18' r='15' fill='none' stroke='blue' stroke-dasharray='45 55'/></svg>"
var img = Image("data:image/svg+xml," + svg)
```

**GOOD — usar Canvas com beginPath/arc/fill:**
```kof
// ✅ IDIOMÁTICO — Canvas desenha diretamente
var c = Canvas(400, 300)
var PI = 3.14159265358979
c.setFill(Palette.blue)
c.beginPath()
c.moveTo(200, 150)
c.arc(200, 150, 100, 0.0, PI)
c.closePath()
c.fill()
```

**Por quê:** Canvas é a primitiva de desenho 2D da plataforma. SVG manual
é verboso e frágil; Canvas é declarativo e performático.

## Canvas: arco colorido

**BAD — calcular coordenadas manualmente com sin/cos:**
```kof
// ❌ NÃO — calcular pontos do arco na mão
var x1 = cx + r * 0.707  // cos(45°)
var y1 = cy - r * 0.707  // sin(45°)
```

**GOOD — usar arc() com radianos:**
```kof
// ✅ IDIOMÁTICO — arc() calcula internamente
c.arc(cx, cy, r, 0.0, 1.5708)  // 0 a π/2 (90°)
```

**Por quê:** `arc()` faz a trigonometria internamente. Não reinventar a roda.

## Canvas: limpando antes de redesenhar

**BAD — criar novo Canvas a cada frame:**
```kof
// ❌ NÃO — leak de elementos DOM
c.remove()
var c2 = Canvas(400, 300)
// ... redesenhar
```

**GOOD — usar clearRect:**
```kof
// ✅ IDIOMÁTICO — limpa e redesenha no mesmo canvas
c.clearRect(0, 0, 400, 300)
// ... redesenhar
```

**Por quê:** `clearRect` é eficiente e preserva o elemento DOM.

## Forms: input com placeholder

**BAD — sem o idiom, o input nasce sem dica de entrada (placeholder é do
widget, não da aplicação):**
```kof
// ❌ NÃO — input sem placeholder; a dica de uso fica no código, não na UI
var campo = Input("")
```

**GOOD — `Input.setPlaceholder`:**
```kof
// ✅ IDIOMÁTICO — placeholder declarativo no widget
var campo = Input("")
campo.setPlaceholder("digite aqui")
```

**Por quê:** `setPlaceholder` é o atributo do widget (renderiza
`placeholder="..."` no DOM do KofJS). Usar string vazia ou esconder a dica
na aplicação é reimplementar uma feature da plataforma (R2).

## Forms: tipo do input (password/number/email/...)

**BAD — input text genérico para senha/número (o tipo é do widget, não da
aplicação):**
```kof
// ❌ NÃO — senha em input text; o browser não mascara
var senha = Input("")
```

**GOOD — `Input.setType`:**
```kof
// ✅ IDIOMÁTICO — tipo declarativo no widget (text/number/email/password/date)
var senha = Input("")
senha.setType("password")
```

**Por quê:** `setType` define o atributo `type` do `<input>` (mascara senha,
teclado numérico no mobile, validação de email). Usar text para tudo é
reimplementar uma feature da plataforma (R2).

## Forms: checkbox/radio (setChecked + checked)

**BAD — simular estado de checkbox com variável à parte (o estado é do
widget, não da aplicação):**
```kof
// ❌ NÃO — estado duplicado fora do DOM
var aceite = false
var caixa = Input("")
caixa.setType("checkbox")
```

**GOOD — `setChecked`/`checked` no widget:**
```kof
// ✅ IDIOMÁTICO — estado do checkbox mora no widget
var caixa = Input("")
caixa.setType("checkbox")
caixa.setChecked(true)
if (caixa.checked()) { println("aceito") }
```

**Por quê:** `setChecked`/`checked` leem/escrevem o estado real do
`<input>` (property + atributo `checked`). Duplicar o estado em variável
à parte diverge do DOM (R1: intenção, não mecanismo).

## Forms: imagem com alt + dimensões

**BAD — `<img>` sem alt/dimensões (acessibilidade + layout quebrados):**
```kof
// ❌ NÃO — imagem sem descrição alternativa nem tamanho
var logo = Image("logo.png")
```

**GOOD — `Image.setAlt/setWidth/setHeight`:**
```kof
// ✅ IDIOMÁTICO — alt (a11y) + dimensões declarativas
var logo = Image("logo.png")
logo.setAlt("logotipo")
logo.setWidth(120)
logo.setHeight(60)
```

**Por quê:** `alt` é acessibilidade (screen readers); `width`/`height`
evitam layout-shift. São atributos do widget (renderizam no DOM do
KofJS), não da aplicação (R2).

## Forms: agrupar campos em <form>

**BAD — campos soltos sem agrupamento (sem fronteira de formulário):**
```kof
// ❌ NÃO — inputs e botão fora de um <form>
var nome = Input("")
var enviar = Button("enviar")
var col = Column(listOf(nome, enviar))
```

**GOOD — `Form(children)`:**
```kof
// ✅ IDIOMÁTICO — campos agrupados em <form>
var nome = Input("")
var enviar = Button("enviar")
var f = Form(listOf(nome, enviar))
```

**Por quê:** `Form` renderiza `<form>` (renderiza no DOM do KofJS) e
agrupa os campos — fronteira semântica de formulário. Campos soltos
perdem a semântica de submissão/acessibilidade (R1: intenção).

## Widgets: id, class e disabled

**BAD — criar wrappers só para dar id/classe a um widget:**
```kof
// ❌ NÃO — View envolvendo o input só para "carregar" um id
var wrapper = View(campo)
```

**GOOD — `setId`/`setClass`/`setDisabled` no próprio widget:**
```kof
// ✅ IDIOMÁTICO — atributos são do widget (família compartilhada)
var campo = Input("")
campo.setId("nome")
campo.setClass("destaque")
campo.setDisabled(true)
```

**Por quê:** id/class/disabled são atributos do elemento DOM; a família
`widget_*` do kof.ui os expõe em qualquer widget (Label/Button/Input/View/
Link/Image/Icon/Form/Column/Row). Envelopar para contornar é mecanismo,
não intenção (R1).

## Forms: onSubmit (handler de submissão)

**BAD — botão avulso que chama a lógica (o form não tem dono da submissão):**
```kof
// ❌ NÃO — submit solto num Button, sem Form
var enviar = Button("enviar", () -> salvar())
```

**GOOD — `Form.onSubmit` + `submit()`:**
```kof
// ✅ IDIOMÁTICO — o form é dono da submissão
var f = Form(listOf(nome, email))
f.onSubmit(() -> salvar())
f.submit()   // ou o usuário aperta Enter no browser
```

**Por quê:** `onSubmit` registra o handler no `<form>` (roda no evento
submit, com `preventDefault` — sem recarregar a página); `submit()`
submete programaticamente. A semântica de formulário fica no form (R1).

## Forms: texto multilinha (Textarea)

**BAD — Input com type=text para texto longo (sem quebras de linha):**
```kof
// ❌ NÃO — input de linha única para descrição multilinha
var obs = Input("")
```

**GOOD — `Textarea(text)`:**
```kof
// ✅ IDIOMÁTICO — widget de primeira classe para texto multilinha
var obs = Textarea("descreva aqui")
obs.setPlaceholder("máx. 500 caracteres")
println(obs.text())
```

**Por quê:** `Textarea` renderiza `<textarea>` (multilinha, redimensionável);
`Input` é linha única. Usar o widget certo é intenção, não mecanismo (R1).

## Forms: escolha de opção (Select)

**BAD — encadear Inputs/checkboxes para uma escolha única:**
```kof
// ❌ NÃO — 3 checkboxes para escolher 1 cor
var c1 = Input(""); c1.setType("checkbox")
var c2 = Input(""); c2.setType("checkbox")
```

**GOOD — `Select(opções)`:**
```kof
// ✅ IDIOMÁTICO — a lista É o widget
var cor = Select(listOf("vermelho", "verde", "azul"))
cor.setSelected(1)
println(cor.selected())   // índice da opção ativa
```

**Por quê:** `Select` renderiza `<select>`/`<option>` (escolha única de N);
`setOptions` troca a lista, `selected`/`setSelected` leem/escrevem o índice.
Representar o domínio (conjunto de opções) com a coleção da linguagem, não
N widgets manuais (R3).
