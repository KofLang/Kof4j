[English](game.md)

# Idiomas — Jogos (kof.game)

**Status:** superfície pura disponível (JVM · Script · Native x86-64/riscv64/aarch64 · JS) · **Introduzido:** fatia a fatia, 06–07/10 (`D-GRAPHICS-GAMING`)

## O que é

Blocos de construção de jogos em Kof puro em `libs/game` (`package game`, um
arquivo por responsabilidade): relógio de frames, snapshots de input por
frame, hospedeiro de janela/loop, sprites 2D, lista de draw, tilemaps, áudio
offline, intent de vídeo. Sem chamadas de render, janela, device de áudio ou
decoder — o backend é dono disso. Cada módulo roda em todo alvo; cada
semântica abaixo é pinada por um golden E2E (`GameClock`/`GameInput`/
`GameMouse`/`GamePad`/`GameCross`/`GameWindow`/`GameSprite`/`GameTilemap`/
`GameAudio`/`GameVideoE2ETest`).

```kof
import game.Clock      import game.Keys       import game.Mouse
import game.Pad        import game.Window     import game.Sprite
import game.Draw        import game.Tilemap    import game.Audio
import game.Video       import game.Trig       import game.Wav
```

Um arquivo por import: refs entre arquivos do mesmo pacote exigem o `import`
explícito (medido — `import game.Draw` dentro do `Sprite.kf`, precedente
`Window.kf` → `game.Clock`). Não há import guarda-chuva.

## Clock — a contabilidade de frames, timestamps do chamador

```kof
import game.Clock

main() {
    var clock = Clock()
    clock.start(1000)
    while (clock.hasNext()) {
        clock.beginFrame(2000)
        println("dt=" + clock.dtMillis())   // 1
        if (clock.frame() >= 0) { clock.stop() }
    }
}
```

O chamador é dono do tempo: passe microssegundos monotônicos para
`start`/`beginFrame` (um contador é um relógio virtual — goldens
determinísticos). O primeiro frame reporta `dt = 0`; `stop()` encerra
`hasNext()`.

## Input — snapshots por frame, backend traduz eventos

```kof
import game.Keys
import game.Mouse
import game.Pad

main() {
    var keys = Keys()
    var mouse = Mouse()
    var pad = Pad()
    keys.beginFrame(listOf("left", "space"))
    mouse.beginFrame(140, 70, listOf("left"))
    pad.beginFrame(0.5, -0.25, 0.0, 0.0, listOf("a"))
    println(keys.pressed("space"))   // true: down agora, não antes
    println(mouse.dx())              // 0 no primeiro frame
    println(pad.leftX())             // 0.5, pass-through cru
}
```

`down` = segurada; `pressed` = apareceu neste frame; `released` = sumiu
neste frame. O backend só traduz eventos do SO em chamadas `beginFrame`.

## Window — o hospedeiro do loop com semântica decidida

```kof
import game.Window

main() {
    var w = Window("Pong")
    w.clock(() -> time.now() * 1000)
    w.dtClampMillis(250)             // A1: clamp de long-frame
    w.vsync(true)                    // A2: vsync é o único pacing
    w.frame { dt: Int, self: Window ->
        println("dt=" + dt)
        if (self.frames() >= 2) { self.stop() }
    }
}
```

Kof não tem receptor implícito, então o corpo recebe a window como `self`
— nunca capture a window externa (um corpo de 1 arg com captura atinge o
defeito cross do backend `known-bugs` §620; a forma de 2 args é verde em
tudo). `pause()`/`resume()` pulam/restauram o corpo; `minimize()`
suspende; `blur()` só registra foco e nunca pausa (A3).

## Sprites — intent, transforms, animação, lista de draw

```kof
import game.Sprite
import game.Draw

main() {
    var hero = sprite("hero.png").at(100, 50).scale(2.0, 2.0).turn(0.0).origin(8, 8)
    hero.frames(listOf("w0.png", "w1.png"))
    hero.animate(16, 16)
    println(hero.current())           // w1.png
    println((hero.worldPointX(16, 8) * 1000.0) as Int)  // 116000, milli-units: nunca imprima Doubles crus (formatação Number do JS vs double de JVM/Native diverge; diferenças de trig de última-ulp nunca podem mover um golden)
    var queue = DrawList()
    queue.draw(hero)                  // invisível não registra nada
    println(queue.size())             // 1
}
```

`at` posiciona o ponto de ORIGEM; `worldPointX/Y` mapeiam pixels da imagem
via `pos + R·S·F·(p − origin)`; `animate(dtMs, frameMs)` avança no tempo do
chamador (`frameMs <= 0` lança); `DrawList` mantém ordem de inserção para
o backend consumir. Rotação usa `game.Trig` (`trigSin`/`trigCos`, Taylor,
sem-branch) — nunca `math.sin`/`math.cos`, que não têm símbolos Native
(`known-bugs` §621).

## Tilemaps — grades esparsas ilimitadas

```kof
import game.Tilemap

main() {
    var level = tilemap("level.png", 16)
    level.setTile(0, 0, 1).setTile(3, 2, 5)
    println(level.tileAt(0, 0))    // 1
    println(level.tileAt(9, 9))    // -1: não-setado
    println(level.worldX(3))       // 48: origem em pixel da coluna 3
    level.setTile(0, 0, 0 - 1)     // id negativo limpa
    println(level.count())         // 1
}
```

Coordenadas negativas permitidas; `tileSize <= 0` lança na construção.

## Áudio — mixer offline, depois bytes WAV

```kof
import game.Audio

main() {
    var mix = mixer(8000)
    mix.play(Sound("a440", 440.0, 100.0, 1.0, 1), 0)
    var pcm = mix.render()
    println(pcm.length)              // 800
    println(mixerClip(300000))       // 32767: somas nunca enrolam
}
```

Vozes sobrepostas somam e clampam em [-32768, 32767]; cada loop reinicia
a fase; `mixer(rate <= 0)` lança. `encodeWav` (em `game.Wav`) transforma o
PCM em imagem de arquivo WAVE:

```kof
import game.Wav

main() {
    var samples = new Int[2]
    samples[0] = 0
    samples[1] = 32767
    var wav = encodeWav(samples, 8000, 1)
    println(wav.length)              // 48: 44 header + 2 por amostra
    println(wavSampleAt(wav, 1))     // 32767: roundtrip campo-exato
}
```

Só mono/stereo; `rate <= 0` lança. Escrita indexada em `Byte[]` mais
`as Byte` funcionam em todo alvo (medido).

## Vídeo — estado de playback sobre tempo do chamador

```kof
import game.Video

main() {
    var v = video("intro.mp4", 10000, 640, 480, 30.0)
    v.play()
    v.tick(1000)
    println(v.position())    // 1000
    println(v.frameIndex())  // 30
    v.seek(99999)
    println(v.position())    // 10000: clampado
}
```

Metadados vêm do backend que demuxou (valores sem sentido lançam);
`tick` avança só tocando (fim para, ou dá wrap com loop); `seek`
clampa; volume clampa em [0,1]. Sem decoder no app.

## Regras de teste para goldens de jogo

- Tempo virtual em tudo: contadores e deltas do chamador, nunca wall
  time (exceto probes de pacing tipo `SDL_Delay`, que afirmam booleanos).
- Milli-units, nunca `Double`s crus: `(v * 1000.0) as Int` (formatação
  `Number` do JS vs `double` de JVM/Native diverge; diferenças de trig de
  última-ulp nunca podem mover um golden — mantenha valores afirmados
  longe de fronteiras de milli).
- Pernas cross (riscv64/aarch64 sob qemu) afirmam o mesmo golden que o
  oráculo JVM (regra 5).
