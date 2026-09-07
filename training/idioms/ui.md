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
