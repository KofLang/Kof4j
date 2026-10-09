[English](42-games.md) | [Português](42-games.pt_BR.md)

# 42 — Jogos: seu primeiro game loop

Jogos em Kof são construídos da biblioteca pura `kof.game` (`libs/game`):
um relógio para tempo de frame, snapshots de input, um hospedeiro de
janela/loop, sprites e uma lista de draw. Nada aqui toca a tela ou os
alto-falantes — o backend é dono de render e áudio. Essa divisão é o que
torna cada programa abaixo determinístico: mesmas entradas, mesmas saídas,
em todo alvo.

## As peças

- `Clock` — contabilidade de frames sobre timestamps que *você* fornece
  (um contador é um relógio virtual, perfeito para testes).
- `Keys`/`Mouse`/`Pad` — snapshots de input por frame: o backend traduz
  eventos do SO, seu código lê `down`/`pressed`/`released`.
- `Window` — o hospedeiro do loop: `frame { dt -> ... }` entrega ao seu
  código o delta time de cada frame.
- `Sprite`/`DrawList` — intent 2D: `sprite("hero.png").at(x, y)` mais
  transforms, coletados numa lista ordenada de draw para o backend.

## Um sprite em movimento, de ponta a ponta

```kf
import game.Clock
import game.Keys
import game.Sprite
import game.Draw
import game.Window

main() {
    var clock = Clock()
    var keys = Keys()
    var ship = sprite("ship.png").at(0, 100)
    var queue = DrawList()
    var w = Window("First game")
    w.frame { dt: Int, self: Window ->
        clock.beginFrame(1000)
        if (keys.pressed("right")) {
            ship.at(ship.x() + 4, ship.y())
        }
        queue.draw(ship)
        if (self.frames() >= 3) { self.stop() }
    }
    println("drawn=" + queue.size())
}
```

Passo a passo:

1. `Clock`, `Keys`, um `Sprite` em `(0, 100)`, uma `DrawList` vazia e uma
   `Window` são criados. Nada roda ainda.
2. `w.frame { dt, self -> ... }` inicia o loop. Kof não tem receptor
   implícito, então o corpo nomeia a window `self` — e nunca captura o `w`
   externo (um corpo com captura atinge um defeito cross do backend; a
   forma de 2 args é verde em todo alvo).
3. Cada frame avança o relógio, lê o snapshot de input, move a nave
   enquanto `right` está pressionado e registra o sprite na fila de draw.
   Após 3 frames o loop para; a fila tem 3 comandos.

## Determinismo é feature

O loop acima usa timestamps fixos (`1000`), então imprime a mesma coisa
em JVM, Script, Native e JS. Jogos reais passam wall time
(`time.now() * 1000`) e traduzem eventos reais do SO — a biblioteca não se
importa de onde vêm os números, que é exatamente por que goldens usam
tempo virtual. Regra de teste para programas de jogo: milli-units, nunca
`Double`s crus — `(v * 1000.0) as Int` — porque a formatação `Number` do
JS difere do `double` de JVM/Native.

## Para onde ir

- `training/idioms/game.md` — toda superfície (tilemaps, mixer de áudio,
  WAV, intent de vídeo) em forma canônica, com as restrições medidas.
- `docs/development/graphics-gaming-plan.md` — a frente completa: fatias,
  decisões, códigos de gap, matriz de paridade.
