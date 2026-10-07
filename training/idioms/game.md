[Português](game.pt_BR.md)

# Idioms — Games (kof.game)

**Status:** pure surface available (JVM · Script · Native x86-64/riscv64/aarch64 · JS) · **Introduced:** slice by slice, 06–07/10 (`D-GRAPHICS-GAMING`)

## What it is

Pure-Kof game building blocks in `libs/game` (`package game`, one file per
responsibility): frame clock, per-frame input snapshots, window/loop host,
2D sprites, draw list, tilemaps, offline audio, video intent. No rendering,
no window, no audio device and no decoder calls — the backend owns those.
Every module runs on every target; every semantic below is pinned by an
E2E golden (`GameClock`/`GameInput`/`GameMouse`/`GamePad`/`GameCross`/
`GameWindow`/`GameSprite`/`GameTilemap`/`GameAudio`/`GameVideoE2ETest`).

```kof
import game.Clock      import game.Keys       import game.Mouse
import game.Pad        import game.Window     import game.Sprite
import game.Draw        import game.Tilemap    import game.Audio
import game.Video       import game.Trig       import game.Wav
```

One file per import: same-package cross-file references need the explicit
`import` (measured — `import game.Draw` inside `Sprite.kf`, the
`Window.kf` → `game.Clock` precedent). There is no umbrella import.

## Clock — the frame bookkeeping, caller's timestamps

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

The caller owns time: pass monotonic microseconds to `start`/`beginFrame`
(a counter is a virtual clock — deterministic goldens). First frame reports
`dt = 0`; `stop()` ends `hasNext()`.

## Input — snapshots per frame, backend translates events

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
    println(keys.pressed("space"))   // true: down now, not before
    println(mouse.dx())              // 0 on the first frame
    println(pad.leftX())             // 0.5, raw pass-through
}
```

`down` = held; `pressed` = appeared this frame; `released` = disappeared
this frame. The backend only translates OS events into `beginFrame` calls.

## Window — the loop host with decided semantics

```kof
import game.Window

main() {
    var w = Window("Pong")
    w.clock(() -> time.now() * 1000)
    w.dtClampMillis(250)             // A1: long-frame clamp
    w.vsync(true)                    // A2: vsync is the only pacing
    w.frame { dt: Int, self: Window ->
        println("dt=" + dt)
        if (self.frames() >= 2) { self.stop() }
    }
}
```

Kof has no implicit receiver, so the body takes the window as `self` —
never capture the outer window (a capturing 1-arg body hits cross-backend
defect `known-bugs` §620; the 2-arg shape is green everywhere).
`pause()`/`resume()` skip/restore the body; `minimize()` suspends it;
`blur()` only records focus and never pauses (A3).

## Sprites — intent, transforms, animation, draw list

```kof
import game.Sprite
import game.Draw

main() {
    var hero = sprite("hero.png").at(100, 50).scale(2.0, 2.0).turn(0.0).origin(8, 8)
    hero.frames(listOf("w0.png", "w1.png"))
    hero.animate(16, 16)
    println(hero.current())           // w1.png
    println((hero.worldPointX(16, 8) * 1000.0) as Int)  // 116000, milli-units: never print raw Doubles (JS Number vs JVM/Native double formatting diverges; last-ulp trig differences must never move a golden)
    var queue = DrawList()
    queue.draw(hero)                  // invisible sprites record nothing
    println(queue.size())             // 1
}
```

`at` positions the ORIGIN point; `worldPointX/Y` map image pixels through
`pos + R·S·F·(p − origin)`; `animate(dtMs, frameMs)` advances on caller
time (`frameMs <= 0` throws); `DrawList` keeps insertion order for the
backend to consume. Rotation uses `game.Trig` (`trigSin`/`trigCos`,
Taylor, branch-free) — never `math.sin`/`math.cos`, which have no Native
symbols (`known-bugs` §621).

## Tilemaps — unbounded sparse grids

```kof
import game.Tilemap

main() {
    var level = tilemap("level.png", 16)
    level.setTile(0, 0, 1).setTile(3, 2, 5)
    println(level.tileAt(0, 0))    // 1
    println(level.tileAt(9, 9))    // -1: unset
    println(level.worldX(3))       // 48: pixel origin of column 3
    level.setTile(0, 0, 0 - 1)     // negative id clears
    println(level.count())         // 1
}
```

Negative coordinates allowed; `tileSize <= 0` throws at construction.

## Audio — offline mixer, then WAV bytes

```kof
import game.Audio

main() {
    var mix = mixer(8000)
    mix.play(Sound("a440", 440.0, 100.0, 1.0, 1), 0)
    var pcm = mix.render()
    println(pcm.length)              // 800
    println(mixerClip(300000))       // 32767: sums never wrap
}
```

Overlapping voices sum and clamp to [-32768, 32767]; each loop restarts
the phase; `mixer(rate <= 0)` throws. `encodeWav` (in `game.Wav`) turns the
PCM into a WAVE file image:

```kof
import game.Wav

main() {
    var samples = new Int[2]
    samples[0] = 0
    samples[1] = 32767
    var wav = encodeWav(samples, 8000, 1)
    println(wav.length)              // 48: 44 header + 2 per sample
    println(wavSampleAt(wav, 1))     // 32767: field-exact roundtrip
}
```

Mono/stereo only; `rate <= 0` throws. `Byte[]` indexed write plus `as Byte`
work on every target (measured).

## Video — playback state over caller time

```kof
import game.Video

main() {
    var v = video("intro.mp4", 10000, 640, 480, 30.0)
    v.play()
    v.tick(1000)
    println(v.position())    // 1000
    println(v.frameIndex())  // 30
    v.seek(99999)
    println(v.position())    // 10000: clamped
}
```

Metadata comes from the demuxing backend (meaningless values throw);
`tick` advances only while playing (end stops, or wraps on loop);
`seek` clamps; volume clamps into [0,1]. No decoder in-app.

## Test rules for game goldens

- Virtual time everywhere: counters and caller-supplied deltas, never wall
  time (except `SDL_Delay`-style pacing probes, which assert booleans).
- Milli-units, never raw `Double`s: `(v * 1000.0) as Int` (JS `Number`
  vs JVM/Native `double` formatting diverges; last-ulp trig differences
  must never move a golden — keep asserted values off milli boundaries).
- Cross legs (riscv64/aarch64 under qemu) assert the same golden as the
  JVM oracle (rule 5).
