[English](42-games.md) | [Português](42-games.pt_BR.md)

# 42 — Games: your first game loop

Games in Kof are built from the pure `kof.game` library (`libs/game`):
a clock for frame time, input snapshots, a window/loop host, sprites and
a draw list. Nothing here touches the screen or the speakers — the backend
owns rendering and audio. That split is what makes every program below
deterministic: same inputs, same outputs, on every target.

## The pieces

- `Clock` — frame bookkeeping over timestamps *you* supply (a counter is a
  virtual clock, perfect for tests).
- `Keys`/`Mouse`/`Pad` — per-frame input snapshots: the backend translates
  OS events, your code reads `down`/`pressed`/`released`.
- `Window` — the loop host: `frame { dt -> ... }` hands your code the
  delta time of every frame.
- `Sprite`/`DrawList` — 2D intent: `sprite("hero.png").at(x, y)` plus
  transforms, collected into an ordered draw list for the backend.

## A moving sprite, end to end

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

Walk through it:

1. `Clock`, `Keys`, a `Sprite` at `(0, 100)`, an empty `DrawList` and a
   `Window` are created. Nothing runs yet.
2. `w.frame { dt, self -> ... }` starts the loop. Kof has no implicit
   receiver, so the body names the window `self` — and never captures the
   outer `w` (a capturing body hits a cross-backend defect; the 2-argument
   shape is green on every target).
3. Each frame advances the clock, polls the input snapshot, moves the ship
   while `right` is pressed, and records the sprite into the draw queue.
   After 3 frames the loop stops; the queue holds 3 commands.

## Determinism is a feature

The loop above uses fixed timestamps (`1000`), so it prints the same
thing on JVM, Script, Native and JS. Real games pass wall time
(`time.now() * 1000`) and translate real OS events — the library does not
care where the numbers come from, which is exactly why goldens use virtual
time. Test rule for game programs: milli-units, never raw `Double`s —
`(v * 1000.0) as Int` — because JS `Number` formatting differs from
JVM/Native `double`.

## Where next

- `training/idioms/game.md` — every surface (tilemaps, audio mixer, WAV,
  video intent) in canonical form, with the measured constraints.
- `docs/development/graphics-gaming-plan.md` — the full front: slices,
  decisions, gap codes, parity matrix.
